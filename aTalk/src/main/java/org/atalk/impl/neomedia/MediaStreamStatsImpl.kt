/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import net.sf.fmj.media.rtp.RTCPFeedback
import net.sf.fmj.media.rtp.RTCPReport
import net.sf.fmj.media.rtp.RTCPSRPacket
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.device.MediaDeviceSession
import org.atalk.impl.neomedia.device.VideoMediaDeviceSession
import org.atalk.impl.neomedia.rtcp.NACKPacket
import org.atalk.impl.neomedia.rtcp.RTCPREMBPacket
import org.atalk.impl.neomedia.rtcp.RTCPTCCPacket
import org.atalk.impl.neomedia.stats.MediaStreamStats2Impl
import org.atalk.impl.neomedia.transform.rtcp.StatisticsEngine
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.MediaStreamStats
import org.atalk.service.neomedia.MediaStreamTarget
import org.atalk.service.neomedia.RTPTranslator
import org.atalk.service.neomedia.VideoMediaStream
import org.atalk.service.neomedia.control.FECDecoderControl
import org.atalk.service.neomedia.rtp.RTCPPacketListener
import org.atalk.service.neomedia.rtp.RTCPReportAdapter
import org.atalk.service.neomedia.rtp.RTCPReportListener
import org.atalk.service.neomedia.rtp.RTCPReports
import org.atalk.service.neomedia.stats.TrackStats
import org.atalk.util.LRUCache
import org.atalk.util.MediaType
import org.atalk.util.TimeUtils
import timber.log.Timber
import java.awt.Dimension
import java.io.IOException
import java.util.*
import javax.media.control.JitterBufferControl
import javax.media.format.VideoFormat
import javax.media.protocol.DataSource
import javax.media.protocol.PushBufferDataSource
import javax.media.rtp.ReceiveStream

/**
 * Class used to compute stats concerning a MediaStream.
 *
 *
 * Note: please do not add more code here. New code should be added to [MediaStreamStats2Impl]
 * instead, where we can manage the complexity and consistency better.
 *
 * @author Vincent Lucas
 * @author Boris Grozev
 * @author Lyubomir Marinov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
open class MediaStreamStatsImpl(
    /**
     * The source data stream to analyze in order to compute the stats.
     */
    private val mediaStreamImpl: MediaStreamImpl) : MediaStreamStats {

    /**
     * Enumeration of the direction (DOWNLOAD or UPLOAD) used for the stats.
     */
    enum class StreamDirection {
        DOWNLOAD, UPLOAD
    }

    /**
     * Keeps track of when a given Network Time Protocol (NTP) time (found in an SR) has been
     * received. This is used to compute the correct Round-Trip-Time (RTT) in the translator case.
     */
    private val emission2reception = Collections.synchronizedMap<Long, Long>(LRUCache(100))

    /**
     * The last jitter received/sent in a RTCP feedback (in RTP timestamp units).
     */
    private val jitterRTPTimestampUnits = doubleArrayOf(0.0, 0.0)

    /**
     * The last number of received/sent Bytes.
     */
    private val nbByte = longArrayOf(0, 0)

    /**
     * The total number of discarded packets
     */
    private  var newNbDiscarded = 0L

    /**
     * The last number of download/upload lost packets.
     */
    private val nbLost = longArrayOf(0, 0)

    /**
     * The last number of received/sent packets.
     */
    private val mnbPackets = longArrayOf(0, 0)

    /**
     * The last download/upload loss rate computed (in %).
     */
    private val percentLoss = doubleArrayOf(0.0, 0.0)

    /**
     * The last used bandwidth computed in download/upload (in Kbit/s).
     */
    private val rateKiloBitPerSec = doubleArrayOf(0.0, 0.0)

    /**
     * The number of packets lost, as reported by the remote side in the last received RTCP RR.
     */
    private var nbPacketsLostUpload = 0L

    /**
     * The `RTCPReportListener` which listens to [.rtcpReports]
     * about the sending and the receiving of RTCP sender/receiver reports and
     * updates this `MediaStreamStats` with their feedback reports.
     */
    private val rtcpReportListener = object : RTCPReportAdapter() {
        /**
         * {@inheritDoc}
         *
         * Updates this `MediaStreamStats` with the received feedback (report).
         */
        override fun rtcpReportReceived(report: RTCPReport) {
            this@MediaStreamStatsImpl.rtcpReportReceived(report)
        }

        /**
         * {@inheritDoc}
         *
         * Updates this `MediaStreamStats` with the sent feedback (report).
         */
        override fun rtcpReportSent(report: RTCPReport) {
            val feedbackReports = report.feedbackReports
            if (feedbackReports.isNotEmpty()) {
                updateNewSentFeedback(feedbackReports[0] as RTCPFeedback)
            }
        }
    }

    /**
     * The last time these stats have been updated.
     */
    private var updateTimeMs: Long

    /**
     * The last number of sent packets when the last feedback has been received. This counter is
     * used to compute the upload loss rate.
     */
    private var uploadFeedbackNbPackets = 0L

    /**
     * The maximum inter arrival jitter value the other party has reported, in RTP time units.
     */
    private var minRemoteInterArrivalJitter = -1L

    /**
     * The minimum inter arrival jitter value the other party has reported, in RTP time units.
     */
    private var maxRemoteInterArrivalJitter = 0L

    /**
     * The sum of all RTP jitter values reported by the remote side, in RTP time units.
     */
    private var remoteJitterSum = 0L

    /**
     * The number of remote RTP jitter reports received.
     */
    private var remoteJitterCount = 0

    /**
     * The list of listeners to be notified when RTCP packets are received.
     */
    private val rtcpPacketListeners = Collections.synchronizedList<RTCPPacketListener>(LinkedList())

    /**
     * The detailed statistics about the RTCP reports sent and received by the associated local peer.
     */
    final override val rtcpReports = RTCPReports()

    /**
     * Creates a new instance of stats concerning a MediaStream.
     */
    init {
        updateTimeMs = System.currentTimeMillis()
        rtcpReports.addRTCPReportListener(rtcpReportListener)
    }

    /**
     * Computes the RTT with the data (Last Sender Report - LSR and DLSR) contained in the last
     * RTCP Sender Report (RTCP feedback). This RTT computation is based on RFC3550, section
     * 6.4.1, subsection "delay since last SR (DLSR): 32 bits".
     * LSR: The middle 32 bits out of 64 in the NTP timestamp
     * DLSR: The delay, expressed in units of 1/65536 seconds, between receiving the last SR packet from
     * source SSRC_n and sending this reception report block
     *
     * @param feedback The last RTCP feedback received by the MediaStream.
     * @return The RTT in milliseconds, or -1 if the RTT is not computable.
     */
    private fun computeRTTInMs(feedback: RTCPFeedback): Int {
        var lsr = feedback.lsr
        val dlsr = feedback.dlsr
        var rtt = -1

        // The RTCPFeedback may represents a Sender Report without any report blocks (and so without LSR and DLSR)
        if (lsr > 0 && dlsr > 0) {
            val arrivalMs = System.currentTimeMillis()

            // If we are translating, the NTP timestamps we include in outgoing
            // SRs are based on the actual sender's clock.
            val translator = mediaStreamImpl.rtpTranslator
            if (translator != null) {
                val receiveRTPManager = translator.findStreamRTPManagerByReceiveSSRC(feedback.ssrc.toInt())
                lsr = if (receiveRTPManager != null) {
                    val receiveStream = receiveRTPManager.mediaStream
                    val stats = receiveStream.mediaStreamStats as MediaStreamStatsImpl
                    val lsrReceipt = stats.emission2reception[lsr] ?: return -1
                    lsrReceipt
                } else {
                    // feedback.getSSRC() might refer to the RTX SSRC but the translator doesn't
                    // know about the RTX SSRC because of the de-RTXification step. In the
                    // translator case if we can't map an emission time to a receipt time, we're
                    // bound to compute the wrong RTT, so here we return -1.
                    Timber.d("invalid_rtt: stream = %s, ssrc = %s,now = %s,lsr = %s,dlsr = %s",
                            mediaStreamImpl.hashCode(), feedback.ssrc, arrivalMs, lsr, dlsr)
                    return -1
                }
            }

            // Get the 64-bit NTP time (cmeng: use the toNtpShort instead)
            //            long arrivalNtp = TimeUtils.toNtpTime(arrivalMs);
            //            // Get the middle 32-Bit of NTP time for RTT computation
            //            long arrival = TimeUtils.toNtpShortFormat(arrivalNtp);
            val arrival = TimeUtils.toNtpShort(arrivalMs)
            val ntprtd = arrival - lsr - dlsr
            val rttLong = TimeUtils.ntpShortToMs(ntprtd)

            //            Timber.e("Calculated RTT (ms) = " + rttLong
            //                    + "\narrival=" + Long.toHexString(arrival)
            //                    + ",lsr=" + Long.toHexString(lsr)
            //                    + ",dlsr=" + Long.toHexString(dlsr));

            // Values over 3s are suspicious and likely indicate a bug.
            rtt = if (rttLong < 0 || rttLong >= 3000) {
                Timber.w("invalid_rtt: stream=%s ssrc=%s, rtt(ms)=%s, now=%s, lsr=%s, dlsr=%s",
                        mediaStreamImpl.hashCode(), feedback.ssrc, rttLong,
                        java.lang.Long.toHexString(arrival), java.lang.Long.toHexString(lsr), java.lang.Long.toHexString(dlsr))
                -1
            } else {
                Timber.log(TimberLog.FINER, "rtt: stream=%s ssrc=%s, rtt(ms)=%s, now=%s, lsr=%s, dlsr=%s",
                        mediaStreamImpl.hashCode(), feedback.ssrc, rttLong,
                        java.lang.Long.toHexString(arrival), java.lang.Long.toHexString(lsr), java.lang.Long.toHexString(dlsr))
                rttLong.toInt()
            }
        }
        return rtt
    }

    /**
     * Returns the jitter average of this download stream.
     *
     * @return the last jitter average computed (in ms).
     */
    override val downloadJitterMs: Double
        get() = getJitterMs(StreamDirection.DOWNLOAD)

    /**
     * Returns the number of lost packets for the receive streams.
     *
     * @return the number of lost packets for the receive streams.
     */
    override val downloadNbPacketLost: Long
        get() {
            var downloadLost = 0L
            for (stream in mediaStreamImpl.receiveStreams) {
                downloadLost += stream!!.sourceReceptionStats.pdUlost.toLong()
            }
            return downloadLost
        }

    /**
     * Returns the total number of sent packets lost.
     *
     * @return the total number of sent packets lost.
     */
    override val uploadNbPacketLost: Long
        get() {
            return nbPacketsLostUpload
        }

    /**
     * Returns the number of Protocol Data Units (PDU) lost in download since the beginning of the session.
     *
     * @return the number of packets lost for this stream.
     */
    private val downloadNbPDULost: Long
        get() {
            val devSession = mediaStreamImpl.getDeviceSession()
            var nbLost = 0
            if (devSession != null) {
                for (receiveStream in devSession.receiveStreams) nbLost += receiveStream.sourceReceptionStats.pdUlost
            }
            return nbLost.toLong()
        }

    /**
     * Returns the percent loss of the download stream.
     *
     * @return the last loss rate computed (in %).
     */
    override val downloadPercentLoss: Double
        get() {
            return percentLoss[StreamDirection.DOWNLOAD.ordinal]
        }

    /**
     * Returns the bandwidth used by this download stream.
     *
     * @return the last used download bandwidth computed (in Kbit/s).
     */
    override val downloadRateKiloBitPerSec: Double
        get() {
            return rateKiloBitPerSec[StreamDirection.DOWNLOAD.ordinal]
        }

    /**
     * Returns the download video format if this stream downloads a video, or null if not.
     *
     * @return the download video format if this stream downloads a video, or null if not.
     */
    private val downloadVideoFormat: VideoFormat?
        get() {
            val deviceSession = mediaStreamImpl.getDeviceSession()
            return if (deviceSession is VideoMediaDeviceSession)
                deviceSession.receivedVideoFormat else null
        }

    /**
     * Returns the download video size if this stream downloads a video, or null if not.
     *
     * @return the download video size if this stream downloads a video, or null if not.
     */
    override val downloadVideoSize: Dimension?
        get() {
            return downloadVideoFormat?.size
        }

    /**
     * Returns the MediaStream enconding.
     *
     * @return the encoding used by the stream.
     */
    override val encoding: String?
        get() {
            val format = mediaStreamImpl.format
            return format?.encoding
        }

    /**
     * Returns the MediaStream enconding rate (in Hz)..
     *
     * @return the encoding rate used by the stream.
     */
    override val encodingClockRate: String?
        get() {
            val format = mediaStreamImpl.format
            return format?.realUsedClockRateString
        }

    /**
     * Returns the set of `PacketQueueControls` found for all the `DataSource`s of
     * all the `ReceiveStream`s. The set contains only non-null elements.
     *
     * @return the set of `PacketQueueControls` found for all the `DataSource`s of
     * all the `ReceiveStream`s. The set contains only non-null elements.
     */
    private val jitterBufferControls: Set<JitterBufferControl>
        get() {
            val set = HashSet<JitterBufferControl>()
            if (mediaStreamImpl.isStarted) {
                val devSession = mediaStreamImpl.getDeviceSession()
                if (devSession != null) {
                    for (receiveStream in devSession.receiveStreams) {
                        val pqc = getJitterBufferControl(receiveStream)
                        if (pqc != null) set.add(pqc)
                    }
                }
            }
            return set
        }

    /**
     * Returns the delay in milliseconds introduced by the jitter buffer. Since there might be
     * multiple `ReceiveStreams`, returns the biggest delay found in any of them.
     *
     * @return the delay in milliseconds introduces by the jitter buffer
     */
    override val jitterBufferDelayMs: Int
        get() {
            var delay = 0
            for (pqc in jitterBufferControls) if (pqc.currentDelayMs > delay) delay = pqc.currentDelayMs
            return delay
        }

    /**
     * Returns the delay in number of packets introduced by the jitter buffer. Since there might be
     * multiple `ReceiveStreams`, returns the biggest delay found in any of them.
     *
     * @return the delay in number of packets introduced by the jitter buffer
     */
    override val jitterBufferDelayPackets: Int
        get() {
            var delay = 0
            for (pqc in jitterBufferControls) if (pqc.currentDelayPackets > delay) delay = pqc.currentDelayPackets
            return delay
        }

    /**
     * Returns the jitter average of this upload/download stream.
     *
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the stream from which this function
     * retrieve the jitter.
     * @return the last jitter average computed (in ms).
     */
    private fun getJitterMs(streamDirection: StreamDirection): Double {
        // RFC3550 says that concerning the RTP timestamp unit (cf. section 5.1
        // RTP Fixed Header Fields, subsection timestamp: 32 bits):
        // As an example, for fixed-rate audio the timestamp clock would likely
        // increment by one for each sampling period.
        //
        // Thus we take the jitter in RTP timestamp units, convert it to seconds
        // (/ clockRate) and finally converts it to milliseconds (* 1000).
        return rtpTimeToMs(jitterRTPTimestampUnits[streamDirection.ordinal])
    }

    /**
     * Gets the RTP clock rate associated with the `MediaStream`.
     *
     * @return the RTP clock rate associated with the `MediaStream`.
     */
    private val rtpClockRate: Double
        get() {
            val format = mediaStreamImpl.format
            val clockRate: Double
            if (format == null) {
                val mediaType = mediaStreamImpl.mediaType
                clockRate = if (MediaType.VIDEO == mediaType) 90000.0 else 48000.0
            } else
                clockRate = format.clockRate

            return clockRate
        }

    /**
     * Converts from RTP time units (using the assumed RTP clock rate of the media stream) to
     * milliseconds. Returns -1D if an appropriate RTP clock rate cannot be found.
     *
     * @param rtpTime the RTP time units to convert.
     * @return the milliseconds corresponding to `rtpTime` RTP units.
     */
    private fun rtpTimeToMs(rtpTime: Double): Double {
        val rtpClockRate = rtpClockRate
        return if (rtpClockRate <= 0) -1.0 else rtpTime / rtpClockRate * 1000
    }

    /**
     * {@inheritDoc}
     */
    override val minDownloadJitterMs: Double
        get() {
            val statisticsEngine = mediaStreamImpl.statisticsEngine
            return if (statisticsEngine != null) {
                rtpTimeToMs(statisticsEngine.minInterArrivalJitter.toDouble())
            } else (-1).toDouble()
        }

    /**
     * {@inheritDoc}
     */
    override val maxDownloadJitterMs: Double
        get() {
            val statisticsEngine = mediaStreamImpl.statisticsEngine
            return if (statisticsEngine != null) {
                rtpTimeToMs(statisticsEngine.maxInterArrivalJitter.toDouble())
            } else -1.0
        }

    /**
     * {@inheritDoc}
     */
    override val minUploadJitterMs: Double
        get() {
            return rtpTimeToMs(minRemoteInterArrivalJitter.toDouble())
        }

    /**
     * {@inheritDoc}
     */
    override val maxUploadJitterMs: Double
        get() {
            return rtpTimeToMs(maxRemoteInterArrivalJitter.toDouble())
        }

    /**
     * {@inheritDoc}
     */
    override val avgDownloadJitterMs: Double
        get() {
            val statisticsEngine = mediaStreamImpl.statisticsEngine
            return if (statisticsEngine != null) {
                rtpTimeToMs(statisticsEngine.avgInterArrivalJitter)
            } else (-1).toDouble()
        }

    /**
     * {@inheritDoc}
     */
    override val avgUploadJitterMs: Double
        get() {
            val count = remoteJitterCount
            return if (count == 0) (-1).toDouble() else rtpTimeToMs(remoteJitterSum.toDouble() / count)
        }

    /**
     * Notifies this instance that an RTCP report with the given value for RTP jitter was received.
     *
     * @param remoteJitter the jitter received, in RTP time units.
     */
    fun updateRemoteJitter(remoteJitter: Long) {
        if (remoteJitter < minRemoteInterArrivalJitter || minRemoteInterArrivalJitter == -1L) minRemoteInterArrivalJitter = remoteJitter
        if (maxRemoteInterArrivalJitter < remoteJitter) maxRemoteInterArrivalJitter = remoteJitter
        remoteJitterSum += remoteJitter
        remoteJitterCount++
    }

    /**
     * Returns the local IP address of the MediaStream.
     *
     * @return the local IP address of the stream.
     */
    override val localIPAddress: String?
        get() {
            val mediaStreamLocalDataAddress = mediaStreamImpl.localDataAddress
            return mediaStreamLocalDataAddress?.address?.hostAddress
        }

    /**
     * Returns the local port of the MediaStream.
     *
     * @return the local port of the stream.
     */
    override val localPort: Int
        get() {
            val mediaStreamLocalDataAddress = mediaStreamImpl.localDataAddress
            return mediaStreamLocalDataAddress?.port ?: -1
        }

    /**
     * Returns the number of sent/received bytes since the beginning of the session.
     *
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the stream from which this function
     * retrieve the number of sent/received bytes.
     * @return the number of sent/received bytes for this stream.
     */
    private fun getNbBytes(streamDirection: StreamDirection): Long {
        return getTrackStats(streamDirection).bytes
    }

    /**
     * @param streamDirection the direction.
     * @return the aggregate track stats for a given direction.
     */
    private fun getTrackStats(streamDirection: StreamDirection): TrackStats {
        val extended = extended
        return if (streamDirection == StreamDirection.DOWNLOAD) extended.receiveStats else extended.sendStats
    }

    /**
     * The total number of Protocol Data Units (PDU) discarded by the FMJ packet queue since the
     * beginning of the session. It's the sum over all `ReceiveStream`s of the `MediaStream`
     */
    override val nbDiscarded: Long
        get() {
            var nbDiscarded = 0
            for (pqc in jitterBufferControls) nbDiscarded = +pqc.discarded
            return nbDiscarded.toLong()
        }

    /**
     * Returns the number of Protocol Data Units (PDU) discarded by the FMJ packet queue since the
     * beginning of the session because it was full. It's the sum over all `ReceiveStream`s
     * of the `MediaStream`
     *
     * @return the number of discarded packets because it was full.
     */
    override val nbDiscardedFull: Int
        get() {
            var nbDiscardedFull = 0
            for (pqc in jitterBufferControls) nbDiscardedFull = +pqc.discardedFull
            return nbDiscardedFull
        }

    /**
     * Returns the number of Protocol Data Units (PDU) discarded by the FMJ packet queue since the
     * beginning of the session because they were late. It's the sum over all
     * `ReceiveStream`s of the `MediaStream`
     *
     * @return the number of discarded packets because they were late.
     */
    override val nbDiscardedLate: Int
        get() {
            var nbDiscardedLate = 0
            for (pqc in jitterBufferControls) nbDiscardedLate = +pqc.discardedLate
            return nbDiscardedLate
        }

    /**
     * Returns the number of Protocol Data Units (PDU) discarded by the FMJ packet queue since the
     * beginning of the session during resets. It's the sum over all `ReceiveStream`s of the
     * `MediaStream`
     *
     * @return the number of discarded packets during resets.
     */
    override val nbDiscardedReset: Int
        get() {
            var nbDiscardedReset = 0
            for (pqc in jitterBufferControls) nbDiscardedReset = +pqc.discardedReset
            return nbDiscardedReset
        }

    /**
     * Returns the number of Protocol Data Units (PDU) discarded by the FMJ packet queue since the
     * beginning of the session due to shrinking. It's the sum over all `ReceiveStream`s of
     * the `MediaStream`
     *
     * @return the number of discarded packets due to shrinking.
     */
    override val nbDiscardedShrink: Int
        get() {
            var nbDiscardedShrink = 0
            for (pqc in jitterBufferControls) nbDiscardedShrink = +pqc.discardedShrink
            return nbDiscardedShrink
        }

    /**
     * The number of packets for which Forward Error Correction (FEC) data was decoded.
     */
    /**
     * Returns the number of packets for which FEC data was decoded. Currently this is cumulative
     * over all `ReceiveStream`s.
     *
     * @return the number of packets for which FEC data was decoded. Currently this is cumulative
     * over all `ReceiveStream`s.
     * @see MediaStreamStatsImpl.updateNbFec
     */
    override var nbFec = 0L

    /**
     * Returns the total number of packets that are send or receive for this stream since the stream is created.
     *
     * @return the total number of packets.
     */
    override val nbPackets: Long
        get() {
            return getNbPDU(StreamDirection.DOWNLOAD) + downloadNbPacketLost + uploadFeedbackNbPackets
        }

    /**
     * Returns the number of lost packets for that stream.
     *
     * @return the number of lost packets.
     */
    override val nbPacketsLost: Long
        get() {
            return nbLost[StreamDirection.UPLOAD.ordinal] + downloadNbPacketLost
        }

    /**
     * {@inheritDoc}
     */
    override val nbPacketsSent: Long
        get() {
            return getNbPDU(StreamDirection.UPLOAD)
        }

    /**
     * {@inheritDoc}
     */
    override val nbPacketsReceived: Long
        get() {
            return getNbPDU(StreamDirection.DOWNLOAD)
        }

    /**
     * Returns the number of Protocol Data Units (PDU) sent/received since the beginning of the session.
     *
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the stream from which this function
     * retrieve the number of sent/received packets.
     * @return the number of packets sent/received for this stream.
     */
    private fun getNbPDU(streamDirection: StreamDirection): Long {
        return getTrackStats(streamDirection).packets
    }

    override val nbReceivedBytes: Long
        get() {
            val connector = mediaStreamImpl.rtpConnector
            if (connector != null) {
                val stream = try {
                    connector.dataInputStream
                } catch (ex: IOException) {
                    // We should not enter here because we are not creating stream.
                    null
                }
                if (stream != null) return stream.numberOfReceivedBytes
            }
            return 0
        }

    override val nbSentBytes: Long
        get() {
            val connector = mediaStreamImpl.rtpConnector ?: return 0
            var stream: RTPConnectorOutputStream? = null
            try {
                stream = connector.getDataOutputStream(false)
            } catch (e: IOException) {
                // We should not enter here because we are not creating output stream
            }
            return stream?.numberOfBytesSent ?: 0
        }

    /**
     * Returns the number of packets in the first `JitterBufferControl` found via `getJitterBufferControls`.
     *
     * @return the number of packets in the first `JitterBufferControl` found via `getJitterBufferControls`.
     */
    override val packetQueueCountPackets: Int
        get() {
            for (pqc in jitterBufferControls) return pqc.currentPacketCount
            return 0
        }

    /**
     * Returns the size of the first `JitterBufferControl` found via `getJitterBufferControls`.
     *
     * @return the size of the first `JitterBufferControl` found via `getJitterBufferControls`.
     */
    override val packetQueueSize: Int
        get() {
            for (pqc in jitterBufferControls) return pqc.currentSizePackets
            return 0
        }

    /**
     * The last percent of discarded packets
     */
    override var percentDiscarded = 0.0

    /**
     * Returns the remote IP address of the MediaStream.
     *
     * @return the remote IP address of the stream.
     */
    override val remoteIPAddress: String?
        get() {
            val mediaStreamTarget = mediaStreamImpl.target

            // Gets this stream IP address endpoint. Stops if the endpoint is disconnected.
            return if (mediaStreamTarget?.dataAddress == null) {
                null
            } else mediaStreamTarget.dataAddress.address.hostAddress
        }

    /**
     * Returns the remote port of the MediaStream.
     *
     * @return the remote port of the stream.
     */
    override val remotePort: Int
        get() {
            val mediaStreamTarget = mediaStreamImpl.target

            // Gets this stream port endpoint. Stops if the endpoint is disconnected.
            return mediaStreamTarget?.dataAddress?.port ?: -1
        }

    /**
     * The RTT computed with the RTCP feedback (cf. RFC3550, section 6.4.1, subsection
     * "Delay since Last Sender Record (DLSR): 32 bits"). -1 if the RTT has not been computed yet.
     * Otherwise the RTT in ms.
     */
    final override var rttMs = -1L
        /**
         * Sets a specific value on [.rttMs]. If there is an actual difference between the old
         * and the new values, notifies the (known) `CallStatsObserver`s.
         */
        private set(rtt) {
            if (field != rtt) {
                field = rtt

                // Notify the CallStatsObservers.
                if (field >= 0) {
                    // RemoteBitrateEstimator is a CallStatsObserver and
                    // VideoMediaStream has a RemoteBitrateEstimator.
                    val mediaStream = mediaStreamImpl
                    if (mediaStream is VideoMediaStream) {
                        val remoteBitrateEstimator = mediaStream.remoteBitrateEstimator
                        remoteBitrateEstimator!!.onRttUpdate(
                                /* avgRttMs */ rtt,
                                /* maxRttMs*/ rtt)
                        mediaStream.mTransportCCEngine?.onRttUpdate(
                                /* avgRttMs */ rtt,
                                /* maxRttMs*/ rtt)
                    }
                }
            }
        }

    /**
     * Returns the jitter average of this upload stream (in ms).
     */
    override val uploadJitterMs: Double
        get() {
            return getJitterMs(StreamDirection.UPLOAD)
        }

    /**
     * Returns the percent loss of the upload stream (in %)..
     */
    override val uploadPercentLoss: Double
        get() {
            return percentLoss[StreamDirection.UPLOAD.ordinal]
        }

    /**
     * Returns the bandwidth used by this download stream.
     *
     * @return the last used upload bandwidth computed (in Kbit/s).
     */
    override val uploadRateKiloBitPerSec: Double
        get() {
            return rateKiloBitPerSec[StreamDirection.UPLOAD.ordinal]
        }

    /**
     * Returns the upload video format if this stream uploads a video, or null if not.
     *
     * @return the upload video format if this stream uploads a video, or null if not.
     */
    private val uploadVideoFormat: VideoFormat?
        get() {
            val deviceSession = mediaStreamImpl.getDeviceSession()
            return if (deviceSession is VideoMediaDeviceSession) deviceSession.sentVideoFormat else null
        }

    /**
     * Returns the upload video size if this stream uploads a video, or null if not.
     *
     * @return the upload video size if this stream uploads a video, or null if not.
     */
    override val uploadVideoSize: Dimension?
        get() {
            return uploadVideoFormat?.size
        }

    override val isAdaptiveBufferEnabled: Boolean
        get() {
            for (pcq in jitterBufferControls) if (pcq.isAdaptiveBufferEnabled) return true
            return false
        }

    /**
     * Updates the jitter stream stats with the new feedback sent.
     *
     * @param feedback The last RTCP feedback sent by the MediaStream.
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the
     * stream from which this function retrieve the jitter.
     */
    private fun updateJitterRTPTimestampUnits(
            feedback: RTCPFeedback, streamDirection: StreamDirection) {
        // Updates the download jitter in RTP timestamp units. There is no need
        // to compute a jitter average, since (cf. RFC3550, section 6.4.1 SR:
        // Sender Report RTCP Packet, subsection inter-arrival jitter: 32 bits)
        // the value contained in the RTCP sender report packet contains a mean
        // deviation of the jitter.
        jitterRTPTimestampUnits[streamDirection.ordinal] = feedback.jitter.toDouble()
        val extended = extended
        extended.updateJitter(
                feedback.ssrc,
                streamDirection,
                rtpTimeToMs(feedback.jitter.toDouble()))
    }

    /**
     * Updates the number of discarded packets.
     *
     * @param newNbDiscarded The last update of the number of lost.
     * @param nbSteps The number of elapsed steps since the last number of loss update.
     */
    private fun updateNbDiscarded(newNbDiscarded: Long, nbSteps: Long) {
        val newPercentDiscarded = computePercentLoss(nbSteps, newNbDiscarded)
        percentDiscarded = computeEWMA(nbSteps, percentDiscarded, newPercentDiscarded)
        // Saves the last update number download lost value.
        this.newNbDiscarded += newNbDiscarded
    }

    /**
     * Updates the `nbFec` field with the sum of FEC-decoded packets over the different
     * `ReceiveStream`s
     */
    private fun updateNbFec() {
        val devSession = mediaStreamImpl.getDeviceSession()
        var nbFec = 0
        if (devSession != null) {
            for (receiveStream in devSession.receiveStreams) {
                for (fecDecoderControl in devSession.getDecoderControls(receiveStream, FECDecoderControl::class.java)) {
                    nbFec += fecDecoderControl.fecPacketsDecoded()
                }
            }
        }
        this.nbFec = nbFec.toLong()
    }

    /**
     * Updates the number of loss for a given stream.
     *
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the stream from which this function
     * updates the stats.
     * @param newNbLost The last update of the number of lost.
     * @param nbSteps The number of elapsed steps since the last number of loss update.
     */
    private fun updateNbLoss(streamDirection: StreamDirection, newNbLost: Long, nbSteps: Long) {
        val streamDirectionIndex = streamDirection.ordinal
        val newPercentLoss = computePercentLoss(nbSteps, newNbLost)
        percentLoss[streamDirectionIndex] = computeEWMA(nbSteps,
                percentLoss[streamDirectionIndex], newPercentLoss)
        // Saves the last update number download lost value.
        nbLost[streamDirectionIndex] += newNbLost
    }

    /**
     * Updates this stream stats with the new feedback received.
     *
     * @param feedback The last RTCP feedback received by the MediaStream.
     */
    private fun updateNewReceivedFeedback(feedback: RTCPFeedback) {
        val streamDirection = StreamDirection.UPLOAD
        updateJitterRTPTimestampUnits(feedback, streamDirection)

        // Updates the loss rate with the RTCP sender report feedback, since
        // this is the only information source available for the upload stream.
        val uploadNewNbRecv = feedback.xtndSeqNum
        nbPacketsLostUpload = feedback.numLost
        val newNbLost = nbPacketsLostUpload - nbLost[streamDirection.ordinal]
        val nbSteps = uploadNewNbRecv - uploadFeedbackNbPackets
        updateNbLoss(streamDirection, newNbLost, nbSteps)

        // Updates the upload loss counters.
        uploadFeedbackNbPackets = uploadNewNbRecv

        // Computes RTT.
        val rtt = computeRTTInMs(feedback).toLong()
        // If a new RTT could not be computed based on this feedback, keep the old one.
        if (rtt >= 0) {
            rttMs = rtt
            val extended = extended
            extended.updateRtt(feedback.ssrc, rtt)
        }
    }

    /**
     * Updates this stream stats with the new feedback sent.
     *
     * @param feedback The last RTCP feedback sent by the MediaStream.
     */
    private fun updateNewSentFeedback(feedback: RTCPFeedback) {
        updateJitterRTPTimestampUnits(feedback, StreamDirection.DOWNLOAD)

        // No need to update the download loss as we have a more accurate value
        // in the global reception stats, which are updated for each new packet received.
    }

    /**
     * Computes and updates information for a specific stream.
     */
    override fun updateStats() {
        // Gets the current time.
        val currentTimeMs = System.currentTimeMillis()

        // Updates stats for the download stream.
        updateStreamDirectionStats(StreamDirection.DOWNLOAD, currentTimeMs)
        // Updates stats for the upload stream.
        updateStreamDirectionStats(StreamDirection.UPLOAD, currentTimeMs)
        // Saves the last update values.
        updateTimeMs = currentTimeMs
    }

    /**
     * Computes and updates information for a specific stream.
     *
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the stream from which this function
     * updates the stats.
     * @param currentTimeMs The current time in ms.
     */
    private fun updateStreamDirectionStats(streamDirection: StreamDirection, currentTimeMs: Long) {
        val streamDirectionIndex = streamDirection.ordinal

        // Gets the current number of packets correctly received since the beginning of this stream.
        val newNbRecv = getNbPDU(streamDirection)
        // Gets the number of byte received/sent since the beginning of this stream.
        val newNbByte = getNbBytes(streamDirection)

        // Computes the number of update steps which has not been done since last update.
        var nbSteps = newNbRecv - mnbPackets[streamDirectionIndex]
        // Even if the remote peer does not send any packets (i.e. is microphone is muted), Jitsi
        // must updates it stats. Thus, Jitsi computes a number of steps equivalent as if Jitsi
        // receives a packet each 20ms (default value).
        if (nbSteps == 0L) nbSteps = (currentTimeMs - updateTimeMs) / 20

        // The upload percentLoss is only computed when a new RTCP feedback is received. This is
        // not the case for the download percentLoss which is updated for each new RTP packet
        // received. Computes the loss rate for this stream.
        if (streamDirection == StreamDirection.DOWNLOAD) {
            // Gets the current number of losses in download since the beginning
            // of this stream.
            val newNbLost = downloadNbPDULost - nbLost[streamDirectionIndex]
            updateNbLoss(streamDirection, newNbLost, nbSteps + newNbLost)
            val newNbDiscarded = nbDiscarded - newNbDiscarded
            updateNbDiscarded(newNbDiscarded, nbSteps + newNbDiscarded)
        }

        // Computes the bandwidth used by this stream.
        val newRateKiloBitPerSec = computeRateKiloBitPerSec(newNbByte - nbByte[streamDirectionIndex],
                currentTimeMs - updateTimeMs)
        rateKiloBitPerSec[streamDirectionIndex] = computeEWMA(nbSteps,
                rateKiloBitPerSec[streamDirectionIndex], newRateKiloBitPerSec)

        // Saves the last update values.
        mnbPackets[streamDirectionIndex] = newNbRecv
        nbByte[streamDirectionIndex] = newNbByte
        updateNbFec()
    }

    /**
     * Notifies this instance that an RTCP REMB packet was received.
     *
     * @param remb the packet.
     */
    fun rembReceived(remb: RTCPREMBPacket?) {
        if (remb != null) {
            synchronized(rtcpPacketListeners) {
                for (listener in rtcpPacketListeners) {
                    listener.rembReceived(remb)
                }
            }
        }
    }

    /**
     * Notifies this instance that an RTCP NACK packet was received.
     *
     * @param nack the packet.
     */
    fun nackReceived(nack: NACKPacket?) {
        if (nack != null) {
            synchronized(rtcpPacketListeners) {
                for (listener in rtcpPacketListeners) {
                    listener.nackReceived(nack)
                }
            }
        }
    }

    /**
     * Notifies this instance that an RTCP SR packet was received.
     *
     * @param sr the packet.
     */
    fun srReceived(sr: RTCPSRPacket?) {
        if (sr != null) {
            val emissionTime = TimeUtils.toNtpShortFormat(TimeUtils.constructNtp(sr.ntptimestampmsw, sr.ntptimestamplsw))
            val arrivalTime = TimeUtils.toNtpShortFormat(TimeUtils.toNtpTime(System.currentTimeMillis()))
            emission2reception[emissionTime] = arrivalTime
            synchronized(rtcpPacketListeners) {
                for (listener in rtcpPacketListeners) {
                    listener.srReceived(sr)
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun addRTCPPacketListener(listener: RTCPPacketListener) {
        rtcpPacketListeners.add(listener)
    }

    /**
     * {@inheritDoc}
     */
    override fun removeRTCPPacketListener(listener: RTCPPacketListener) {
        rtcpPacketListeners.remove(listener)
    }

    /**
     * Notifies this instance that a specific RTCP RR or SR report was received by [.rtcpReports].
     *
     * @param report the received RTCP RR or SR report
     */
    private fun rtcpReportReceived(report: RTCPReport) {
        // reception report blocks
        val feedbackReports = report.feedbackReports
        if (feedbackReports.isNotEmpty()) {
            val extended = extended
            for (rtcpFeedback in feedbackReports) {
                updateNewReceivedFeedback(rtcpFeedback)
                extended.rtcpReceiverReportReceived(
                        rtcpFeedback.ssrc, rtcpFeedback.fractionLost)
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     *
     * This method is different from [.getUploadRateKiloBitPerSec] in that: 1. It is not
     * necessary for [.updateStats] to be called periodically by the user of libjitsi in
     * order for it to return correct values. 2. The returned value is based on the average bitrate
     * over a fixed window, as opposed to an EWMA. 3. The measurement is performed after the
     * [MediaStream]'s transformations, notably after simulcast layers are dropped (i.e.
     * closer to the network interface).
     *
     *
     * The return value includes RTP payload and RTP headers, as well as RTCP.
     */
    override val sendingBitrate: Long
        get() {
            var sbr = -1L
            val rtpConnector = mediaStreamImpl.rtpConnector
            if (rtpConnector != null) {
                try {
                    val rtpStream = rtpConnector.getDataOutputStream(false)
                    if (rtpStream != null) {
                        val now = System.currentTimeMillis()
                        sbr = rtpStream.getOutputBitrate(now)
                        val rtcpStream = rtpConnector.getControlOutputStream(false)
                        if (rtcpStream != null) {
                            sbr += rtcpStream.getOutputBitrate(now)
                        }
                    }
                } catch (ioe: IOException) {
                    Timber.w(ioe, "Failed to get sending bitrate.")
                }
            }
            return sbr
        }

    /**
     * @return this instance as a [MediaStreamStats2Impl].
     */
    private val extended: MediaStreamStats2Impl
        get() {
            return mediaStreamImpl.mediaStreamStats
        }

    /**
     * Notifies listeners that a transport-wide-cc packet was received.
     * Listeners may include Remote Bitrate Estimators or Bandwidth Estimators
     * {@param tccPacket}
     */
    fun tccPacketReceived(tccPacket: RTCPTCCPacket?) {
        if (tccPacket != null) {
            synchronized(rtcpPacketListeners) {
                for (listener in rtcpPacketListeners) {
                    listener.tccReceived(tccPacket)
                }
            }
        }
    }

    companion object {
        /**
         * Computes an Exponentially Weighted Moving Average (EWMA). Thus, the most recent history
         * has a more preponderant importance in the average computed.
         *
         * @param nbStepSinceLastUpdate The number of step which has not been computed since last update.
         * In our case the number of packets received since the last computation.
         * @param lastValue The value computed during the last update.
         * @param newValue The value newly computed.
         * @return The EWMA average computed.
         */
        private fun computeEWMA(nbStepSinceLastUpdate: Long, lastValue: Double, newValue: Double): Double {
            // For each new packet received the EWMA moves by a 0.1 coefficient.
            var EWMACoeff = 0.01 * nbStepSinceLastUpdate
            // EWMA must be <= 1.
            if (EWMACoeff > 1) EWMACoeff = 1.0
            return lastValue * (1.0 - EWMACoeff) + newValue * EWMACoeff
        }

        /**
         * Computes the loss rate.
         *
         * @param nbLostAndRecv The number of lost and received packets.
         * @param nbLost The number of lost packets.
         * @return The loss rate in percent.
         */
        private fun computePercentLoss(nbLostAndRecv: Long, nbLost: Long): Double {
            return if (nbLostAndRecv == 0L) 0.0 else 100.0 * nbLost / nbLostAndRecv
        }

        /**
         * Computes the bitrate in kbps.
         *
         * @param nbBytes The number of bytes received.
         * @param intervalMs The number of milliseconds during which `nbBytes` bytes were sent or received.
         * @return the bitrate computed in kbps (1000 bits per second)
         */
        private fun computeRateKiloBitPerSec(nbBytes: Long, intervalMs: Long): Double {
            return if (intervalMs == 0L) 0.0 else nbBytes * 8.0 / intervalMs
        }

        /**
         * Gets the `JitterBufferControl` of a `ReceiveStream`.
         *
         * @param receiveStream the `ReceiveStream` to get the `JitterBufferControl` of
         * @return the `JitterBufferControl` of `receiveStream`.
         */
        fun getJitterBufferControl(receiveStream: ReceiveStream): JitterBufferControl? {
            val ds = receiveStream.dataSource
            if (ds is PushBufferDataSource) {
                for (pbs in ds.streams) {
                    val pqc = pbs.getControl(JitterBufferControl::class.java.name) as JitterBufferControl?
                    if (pqc != null) return pqc
                }
            }
            return null
        }
    }
}