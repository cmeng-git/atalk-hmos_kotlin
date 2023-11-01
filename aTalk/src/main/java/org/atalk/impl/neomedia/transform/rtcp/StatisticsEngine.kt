/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.transform.rtcp

import net.sf.fmj.media.rtp.*
import net.sf.fmj.media.rtp.util.BadFormatException
import net.sf.fmj.utility.ByteBufferOutputStream
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.MediaStreamImpl
import org.atalk.impl.neomedia.MediaStreamStatsImpl
import org.atalk.impl.neomedia.RTCPPacketPredicate
import org.atalk.impl.neomedia.RTPPacketPredicate
import org.atalk.impl.neomedia.rtcp.*
import org.atalk.impl.neomedia.stats.MediaStreamStats2Impl
import org.atalk.impl.neomedia.transform.*
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.RawPacket
import org.atalk.service.neomedia.codec.Constants
import org.atalk.service.neomedia.control.FECDecoderControl
import org.atalk.service.neomedia.rtp.RTCPExtendedReport
import org.atalk.service.neomedia.rtp.RTCPExtendedReport.VoIPMetricsReportBlock
import org.atalk.util.MediaType
import org.atalk.util.RTCPUtils
import org.atalk.util.RTPUtils
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.*
import javax.media.rtp.ReceiveStream

/**
 * Implements a `TransformEngine` monitors the incoming and outgoing RTCP
 * packets, logs and stores statistical data about an associated `MediaStream`.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class StatisticsEngine(
        /**
         * The stream created us.
         */
        private val mediaStream: MediaStreamImpl) : SinglePacketTransformer(RTCPPacketPredicate.INSTANCE), TransformEngine {

    /**
     * The minimum inter arrival jitter value we have reported.
     *
     * @return minimum inter arrival jitter value we have reported.
     */
    /**
     * The minimum inter arrival jitter value we have reported, in RTP timestamp units.
     */
    var maxInterArrivalJitter = 0L
        private set

    /**
     * The `MediaType` of [.mediaStream]. Cached for the purposes of performance.
     */
    private val mediaType = mediaStream.mediaType
    /**
     * The maximum inter arrival jitter value we have reported.
     *
     * @return maximum inter arrival jitter value we have reported.
     */
    /**
     * The minimum inter arrival jitter value we have reported, in RTP timestamp units.
     */
    var minInterArrivalJitter = -1L
        private set

    /**
     * The number of RTCP sender reports (SR) and/or receiver reports (RR) sent. Mapped per ssrc.
     */
    private val numberOfRTCPReportsMap = HashMap<Long, Long>()

    /**
     * The sum of the jitter values we have reported in RTCP reports, in RTP timestamp units.
     */
    private val jitterSumMap = HashMap<Long, Long>()

    /**
     * The [RTCPPacketParserEx] which this instance will use to parse RTCP packets.
     */
    private val parser = RTCPPacketParserEx()

    /**
     * The [MediaStreamStats2Impl] of the [MediaStream].
     */
    private val mediaStreamStats = mediaStream.mediaStreamStats

    /**
     * Adds a specific RTCP XR packet into `pkt`.
     *
     * @param pkt the `RawPacket` into which `extendedReport` is to be added
     * @param extendedReport the RTCP XR packet to add into `pkt`
     * @return `true` if `extendedReport` was added into `pkt`; otherwise,
     * `false`
     */
    private fun addRTCPExtendedReport(pkt: RawPacket?, extendedReport: RTCPExtendedReport): Boolean {
        // Find an offset within pkt at which the specified RTCP XR packet is to be added.
        // According to RFC 3550, it should not follow an RTCP BYE packet with matching SSRC.
        var buf = pkt!!.buffer
        var off = pkt.offset
        var end = off + pkt.length
        while (off < end) {
            val rtcpPktLen = getLengthIfRTCP(buf, off, end - off)
            if (rtcpPktLen <= 0) // Not an RTCP packet.
                break
            val pt = 0xff and buf[off + 1].toInt() // payload type (PT)
            var before = false
            if (pt == RTCPPacket.BYE) {
                val sc = 0x1f and buf[off].toInt() // source count
                if (sc < 0 || rtcpPktLen < 1 + sc * 4) {
                    // If the packet is not really an RTCP BYE, then we should better add the
                    // RTCP XR before a chunk of bytes that we do not fully understand.
                    before = true
                } else {
                    var i = 0
                    var ssrcOff = off + 4
                    while (i < sc) {
                        if (RTPUtils.readInt(buf, ssrcOff) == extendedReport.ssrc) {
                            before = true
                            break
                        }
                        ++i
                        ssrcOff += 4
                    }
                }
            }
            off += if (before) break else rtcpPktLen
        }
        var added = false
        if (off <= end) {
            // Make room within pkt for extendedReport.
            val extendedReportLen = extendedReport.calcLength()
            val oldOff = pkt.offset
            pkt.grow(extendedReportLen)
            val newOff = pkt.offset
            buf = pkt.buffer
            off = off - oldOff + newOff
            end = newOff + pkt.length
            if (off < end) {
                System.arraycopy(buf, off, buf, off + extendedReportLen, end - off)
            }

            // Write extendedReport into pkt with auto resource close
            val bbos = ByteBufferOutputStream(buf, off, extendedReportLen)
            try {
                DataOutputStream(bbos).use { dos ->
                    extendedReport.assemble(dos)
                    added = dos.size() == extendedReportLen
                }
            } catch (ignore: IOException) {
            }
            if (added) {
                pkt.length = pkt.length + extendedReportLen
            } else if (off < end) {
                // Reclaim the room within pkt for extendedReport.
                System.arraycopy(buf, off + extendedReportLen, buf, off, end - off)
            }
        }
        return added
    }

    /**
     * Adds RTP Control Protocol Extended Report (RTCP XR) packets to
     * `pkt` if `pkt` contains RTCP SR or RR packets.
     *
     * @param pkt the `RawPacket` to which RTCP XR packets are to be added
     * @param sdpParams
     * @return a list of `RTCPExtendedReport` packets added to `pkt` or
     * `null` or an empty list if no RTCP XR packets were added to `pkt`
     */
    private fun addRTCPExtendedReports(pkt: RawPacket?, sdpParams: String): List<RTCPExtendedReport>? {
        // Create an RTCP XR packet for each RTCP SR or RR packet. Afterwards,
        // add the newly created RTCP XR packets into pkt.
        val buf = pkt!!.buffer
        var off = pkt.offset
        var rtcpXRs: MutableList<RTCPExtendedReport>? = null
        val end = off + pkt.length
        while (off < end) {
            val rtcpPktLen = getLengthIfRTCP(buf, off, end - off)
            if (rtcpPktLen <= 0) // Not an RTCP packet.
                break
            val pt = 0xff and buf[off + 1].toInt() // payload type (PT)
            if (pt == RTCPPacket.RR || pt == RTCPPacket.SR) {
                val rc = 0x1f and buf[off].toInt() // reception report count
                if (rc >= 0) {
                    // Does the packet still look like an RTCP packet of the advertised packet
                    // type (PT)?
                    var minRTCPPktLen = (2 + rc * 6) * 4
                    var receptionReportBlockOff = off + 2 * 4
                    if (pt == RTCPPacket.SR) {
                        minRTCPPktLen += 5 * 4
                        receptionReportBlockOff += 5 * 4
                    }
                    if (rtcpPktLen < minRTCPPktLen) {
                        rtcpXRs = null // Abort, not an RTCP RR or SR packet.
                        break
                    }
                    val senderSSRC = RTPUtils.readInt(buf, off + 4)
                    // Collect the SSRCs of the RTP data packet sources being
                    // reported upon by the RTCP RR/SR packet because they may
                    // be of concern to the RTCP XR packet (e.g. VoIP Metrics Report Block).
                    val sourceSSRCs = IntArray(rc)
                    for (i in 0 until rc) {
                        sourceSSRCs[i] = RTPUtils.readInt(buf, receptionReportBlockOff)
                        receptionReportBlockOff += 6 * 4
                    }

                    // Initialize an RTCP XR packet.
                    val rtcpXR = createRTCPExtendedReport(senderSSRC, sourceSSRCs, sdpParams)
                    if (rtcpXR != null) {
                        if (rtcpXRs == null) rtcpXRs = LinkedList()
                        rtcpXRs.add(rtcpXR)
                    }
                } else {
                    rtcpXRs = null // Abort, not an RTCP RR or SR packet.
                    break
                }
            }
            off += rtcpPktLen
        }

        // Add the newly created RTCP XR packets into pkt.
        if (rtcpXRs != null && rtcpXRs.isNotEmpty()) {
            for (rtcpXR in rtcpXRs) addRTCPExtendedReport(pkt, rtcpXR)
        }
        return rtcpXRs
    }

    /**
     * Initializes a new RTP Control Protocol Extended Report (RTCP XR) packet.
     *
     * @param senderSSRC the synchronization source identifier (SSRC) of the originator of the new RTCP XR
     * packet
     * @param sourceSSRCs the SSRCs of the RTP data packet sources to be reported upon by the new RTCP XR packet
     * @param sdpParams
     * @return a new RTCP XR packet with originator `senderSSRC` and reporting upon `sourceSSRCs`
     */
    private fun createRTCPExtendedReport(senderSSRC: Int, sourceSSRCs: IntArray?, sdpParams: String?): RTCPExtendedReport? {
        var xr: RTCPExtendedReport? = null
        if (sourceSSRCs != null && sourceSSRCs.isNotEmpty() && sdpParams != null && sdpParams.contains(VoIPMetricsReportBlock.SDP_PARAMETER)) {
            xr = RTCPExtendedReport()
            for (sourceSSRC in sourceSSRCs) {
                val reportBlock = createVoIPMetricsReportBlock(senderSSRC, sourceSSRC)
                if (reportBlock != null) xr.addReportBlock(reportBlock)
            }
            if (xr.reportBlockCount > 0) {
                xr.ssrc = senderSSRC
            } else {
                // An RTCP XR packet with zero report blocks is fine, generally,
                // but we see no reason to send such a packet.
                xr = null
            }
        }
        return xr
    }

    /**
     * Initializes a new RTCP XR &quot;VoIP Metrics Report Block&quot; as defined by RFC 3611.
     *
     * @param senderSSRC
     * @param sourceSSRC the synchronization source identifier (SSRC) of the RTP
     * data packet source to be reported upon by the new instance
     * @return a new `RTCPExtendedReport.VoIPMetricsReportBlock` instance
     * reporting upon `sourceSSRC`
     */
    private fun createVoIPMetricsReportBlock(senderSSRC: Int, sourceSSRC: Int): VoIPMetricsReportBlock? {
        var voipMetrics: VoIPMetricsReportBlock? = null
        if (MediaType.AUDIO == mediaType) {
            val receiveStream = mediaStream.getReceiveStream(sourceSSRC)
            if (receiveStream != null) {
                voipMetrics = createVoIPMetricsReportBlock(senderSSRC, receiveStream)
            }
        }
        return voipMetrics
    }

    /**
     * Initializes a new RTCP XR &quot;VoIP Metrics Report Block&quot; as defined by RFC 3611.
     *
     * @param senderSSRC
     * @param receiveStream the `ReceiveStream` to be reported upon by the new instance
     * @return a new `RTCPExtendedReport.VoIPMetricsReportBlock` instance
     * reporting upon `receiveStream`
     */
    private fun createVoIPMetricsReportBlock(senderSSRC: Int, receiveStream: ReceiveStream): VoIPMetricsReportBlock {
        val voipMetrics = VoIPMetricsReportBlock()
        voipMetrics.sourceSSRC = receiveStream.ssrc.toInt()

        // loss rate
        var expectedPacketCount: Long = 0
        val receptionStats = receiveStream.sourceReceptionStats
        var lossRate: Double
        if (receiveStream is SSRCInfo) {
            val ssrcInfo = receiveStream as SSRCInfo
            expectedPacketCount = ssrcInfo.expectedPacketCount
            if (expectedPacketCount > 0) {
                var lostPacketCount = receptionStats.pdUlost.toLong()
                if (lostPacketCount in 1..expectedPacketCount) {
                    // RFC 3611 mentions that the total number of packets lost takes into account
                    // "the effects of applying any error protection such as FEC".
                    val fecDecodedPacketCount = getFECDecodedPacketCount(receiveStream)
                    if (fecDecodedPacketCount in 1..lostPacketCount) {
                        lostPacketCount -= fecDecodedPacketCount
                    }
                    lossRate = lostPacketCount / expectedPacketCount.toDouble() * 256
                    if (lossRate > 255) lossRate = 255.0
                    voipMetrics.lossRate = lossRate.toInt().toShort()
                }
            }
        }

        // round trip delay
        val rtt = mediaStream.mediaStreamStats.rttMs.toInt()
        if (rtt >= 0) {
            voipMetrics.roundTripDelay = rtt
        }

        // end system delay
        /*
         * Defined as the sum of the total sample accumulation and encoding
         * delay associated with the sending direction and the jitter buffer,
         * decoding, and playout buffer delay associated with the receiving
         * direction. Collectively, these cover the whole path from the network
         * to the very playback of the audio. We cannot guarantee latency pretty
         * much anywhere along the path and, consequently, the metric will be "extremely variable".
         */

        // signal level
        // noise level
        /*
         * The computation of noise level requires the notion of silent period
         * which we do not have (because, for example, we do not voice activity detection).
         */

        // residual echo return loss (RERL)
        /*
         * WebRTC, which is available and default on OS X, appears to be able to
         * provide the residual echo return loss. Speex, which is available and
         * not default on the supported operating systems, and WASAPI, which is
         * available and default on Windows, do not seem to be able to provide
         * the metric. Taking into account the availability of the metric and
         * the distribution of the users according to operating system, the
         * support for the metric is estimated to be insufficiently useful.
         * Moreover, RFC 3611 states that RERL for "PC softphone or
         * speakerphone" is "extremely variable, consider reporting "undefined" (127)".
         */

        // R factor
        /*
         * TODO Requires notions such as noise and noise sources, simultaneous
         * impairments, and others that we do not know how to define at the time of this writing.
         */
        // ext. R factor
        /*
         * The external R factor is a voice quality metric describing the
         * segment of the call that is carried over a network segment external
         * to the RTP segment, for example a cellular network. We implement the
         * RTP segment only and we do not have a notion of a network segment
         * external to the RTP segment.
         */
        // MOS-LQ
        /*
         * TODO It is unclear at the time of this writing from RFC 3611 how
         * MOS-LQ is to be calculated.
         */
        // MOS-CQ
        /*
         * The metric may be calculated by converting an R factor determined
         * according to ITU-T G.107 or ETSI TS 101 329-5 into an estimated MOS
         * using the equation specified in G.107. However, we do not have R factor.
         */

        // receiver configuration byte (RX config)
        // packet loss concealment (PLC)
        /*
         * We insert silence in place of lost packets by default and we have FEC
         * and/or PLC for OPUS and SILK.
         */
        var packetLossConcealment = VoIPMetricsReportBlock.DISABLED_PACKET_LOSS_CONCEALMENT
        val mediaFormat = mediaStream.format
        if (mediaFormat != null) {
            var encoding = mediaFormat.encoding
            if (encoding != null) {
                encoding = encoding.lowercase(Locale.getDefault())
                if (Constants.OPUS_RTP.lowercase(Locale.getDefault()).contains(encoding)
                        || Constants.SILK_RTP.lowercase(Locale.getDefault()).contains(encoding)) {
                    packetLossConcealment = VoIPMetricsReportBlock.STANDARD_PACKET_LOSS_CONCEALMENT
                }
            }
        }
        voipMetrics.packetLossConcealment = packetLossConcealment

        // jitter buffer adaptive (JBA)
        val jbc = MediaStreamStatsImpl.getJitterBufferControl(receiveStream)
        var discardRate: Double
        if (jbc == null) {
            voipMetrics.jitterBufferAdaptive = VoIPMetricsReportBlock.UNKNOWN_JITTER_BUFFER_ADAPTIVE
        } else {
            // discard rate
            if (expectedPacketCount > 0) {
                val discardedPacketCount = jbc.discarded
                if (discardedPacketCount in 1..expectedPacketCount) {
                    discardRate = discardedPacketCount / expectedPacketCount.toDouble() * 256
                    if (discardRate > 255) discardRate = 255.0
                    voipMetrics.discardRate = discardRate.toInt().toShort()
                }
            }

            // jitter buffer nominal delay (JB nominal)
            // jitter buffer maximum delay (JB maximum)
            // jitter buffer absolute maximum delay (JB abs max)
            val maximumDelay = jbc.maximumDelay
            voipMetrics.jitterBufferMaximumDelay = maximumDelay
            voipMetrics.jitterBufferNominalDelay = jbc.nominalDelay
            if (jbc.isAdaptiveBufferEnabled) {
                voipMetrics.jitterBufferAdaptive = VoIPMetricsReportBlock.ADAPTIVE_JITTER_BUFFER_ADAPTIVE
                voipMetrics.jitterBufferAbsoluteMaximumDelay = jbc.absoluteMaximumDelay
            } else {
                voipMetrics.jitterBufferAdaptive = VoIPMetricsReportBlock.NON_ADAPTIVE_JITTER_BUFFER_ADAPTIVE
                // Jitter buffer absolute maximum delay (JB abs max) MUST be set
                // to jitter buffer maximum delay (JB maximum) for fixed jitter
                // buffer implementations.
                voipMetrics.jitterBufferAbsoluteMaximumDelay = maximumDelay
            }

            // jitter buffer rate (JB rate)
            /*
             * TODO We do not know how to calculate it at the time of this
             * writing. It very likely is to be calculated in
             * JitterBufferBehaviour because JitterBufferBehaviour implements
             * the adaptiveness of a jitter buffer implementation.
             */
        }
        if (receptionStats is RTPStats) {
            // burst density
            // gap density
            // burst duration
            // gap duration
            val burstMetrics = receptionStats.burstMetrics
            var l = burstMetrics.burstMetrics
            val gapDuration: Int = (l and 0xFFFFL).toInt()
            l = l shr 16
            val burstDuration: Int = (l and 0xFFFFL).toInt()
            l = l shr 16
            val gapDensity: Short = (l and 0xFFL).toShort()
            l = l shr 8
            val burstDensity: Short = (l and 0xFFL).toShort()
            l = l shr 8
            val sDiscardRate = (l and 0xFFL).toShort()
            l = l shr 8
            val sLossRate = (l and 0xFFL).toShort()
            voipMetrics.burstDensity = burstDensity
            voipMetrics.gapDensity = gapDensity
            voipMetrics.burstDuration = burstDuration
            voipMetrics.gapDuration = gapDuration
            voipMetrics.discardRate = sDiscardRate
            voipMetrics.lossRate = sLossRate

            // Gmin
            voipMetrics.gMin = burstMetrics.gMin
        }
        return voipMetrics
    }

    /**
     * Gets the number of packets in a `ReceiveStream` which have been decoded by means of FEC.
     *
     * @param receiveStream the `ReceiveStream` of which the number of packets decoded by means
     * of FEC is to be returned
     * @return the number of packets in `receiveStream` which have been decoded by means of FEC
     */
    private fun getFECDecodedPacketCount(receiveStream: ReceiveStream): Long {
        val devSession = mediaStream.getDeviceSession()
        var fecDecodedPacketCount: Long = 0
        if (devSession != null) {
            val decoderControls: Iterable<FECDecoderControl> = devSession.getDecoderControls(receiveStream, FECDecoderControl::class.java)
            for (decoderControl in decoderControls) fecDecodedPacketCount += decoderControl.fecPacketsDecoded().toLong()
        }
        return fecDecodedPacketCount
    }

    /**
     * Gets the average value of the jitter reported in RTCP packets, in RTP timestamp units.
     */
    val avgInterArrivalJitter: Double
        get() {
            val numberOfRTCPReports = getCumulativeValue(numberOfRTCPReportsMap)
            val jitterSum = getCumulativeValue(jitterSumMap)
            return if (numberOfRTCPReports == 0L) 0.0 else jitterSum.toDouble() / numberOfRTCPReports
        }

    /**
     * Returns a reference to this class since it is performing RTP transformations in here.
     *
     * @return a reference to `this` instance of the `StatisticsEngine`.
     */
    override val rtcpTransformer: PacketTransformer
        get() = this

    /**
     * Always returns `null` since this engine does not require any RTP transformations.
     *
     * @return `null` since this engine does not require any RTP transformations.
     */
    override val rtpTransformer: PacketTransformer = RTPPacketTransformer()

    /**
     * Initializes a new SR or RR `RTCPReport` instance from a specific byte array.
     *
     * @param type the type of the RTCP packet (RR or SR).
     * @param buf the byte array from which the RR or SR instance will be initialized.
     * @param off the offset into `buf`.
     * @param len the length of the data in `buf`.
     * @return a new SR or RR `RTCPReport` instance initialized from the specified byte array.
     * @throws IOException if an I/O error occurs while parsing the specified
     * `pkt` into a new SR or RR `RTCPReport` instance.
     */
    @Throws(IOException::class)
    private fun parseRTCPReport(type: Int, buf: ByteArray, off: Int, len: Int): RTCPReport? {
        return when (type) {
            RTCPPacket.RR -> RTCPReceiverReport(buf, off, len)
            RTCPPacket.SR -> RTCPSenderReport(buf, off, len)
            else -> null
        }
    }

    /**
     * Initializes a new SR or RR `RTCPReport` instance from a specific `RawPacket`.
     *
     * @param pkt the `RawPacket` to parse into a new SR or RR `RTCPReport` instance
     * @return a new SR or RR `RTCPReport` instance initialized from the specified `pkt`
     * @throws IOException if an I/O error occurs while parsing the specified
     * `pkt` into a new SR or RR `RTCPReport` instance
     */
    @Throws(IOException::class)
    private fun parseRTCPReport(pkt: RawPacket): RTCPReport? {
        return parseRTCPReport(pkt.rtcpPacketType, pkt.buffer, pkt.offset, pkt.length)
    }

    /**
     * Parses incoming RTCP packets and notifies the MediaStreamStats of this instance
     * about the reception of packets with known types (currently these are RR, SR, XR, REMB, NACK).
     *
     * @param pkt the packet to reverse-transform
     * @return the packet which is the result of the reverse-transform
     */
    override fun reverseTransform(pkt: RawPacket): RawPacket? {
        // SRTP may send non-RTCP packets.
        if (RTCPUtils.isRtcp(pkt!!.buffer, pkt.offset, pkt.length)) {
            mediaStreamStats.rtcpPacketReceived(pkt.rtcpSSRC, pkt.length)
            var compound: RTCPCompoundPacket?
            var ex: Exception?
            try {
                compound = parser.parse(pkt.buffer, pkt.offset, pkt.length) as RTCPCompoundPacket
                ex = null
            } catch (e: BadFormatException) {
                // In some parsing failures, FMJ swallows the original
                // IOException and throws a runtime IllegalStateException.
                // Handle it as if parsing failed.
                compound = null
                ex = e
            } catch (e: IllegalStateException) {
                compound = null
                ex = e
            }
            if (compound?.packets == null || compound.packets.isEmpty()) {
                Timber.i("Failed to parse an incoming RTCP packet: %s", if (ex == null) "null" else ex.message)

                // Either this is an empty packet, or parsing failed. In any
                // case, drop the packet to make sure we're not forwarding
                // broken RTCP (we've observed Chrome 49 sending SRs with an
                // incorrect 'rc' field).
                return null
            }
            try {
                updateReceivedMediaStreamStats(compound.packets)
            } catch (t: Throwable) {
                if (t is ThreadDeath) {
                    throw t
                } else {
                    Timber.e(t, "Failed to analyze an incoming RTCP packet for the purposes of statistics.")
                }
            }
        }
        return pkt
    }

    /**
     * Processes the [RTCPPacket]s from `in` as received RTCP
     * packets and updates the MediaStreamStats. Adds to `out` the
     * ones which were not consumed and should be output from this instance.
     *
     * @param in the input packets
     */
    private fun updateReceivedMediaStreamStats(`in`: Array<RTCPPacket>) {
        val streamStats = mediaStream.mediaStreamStats
        for (rtcp in `in`) {
            when (rtcp.type) {
                RTCPFBPacket.PSFB -> if (rtcp is RTCPREMBPacket) {
                    Timber.log(TimberLog.FINER, "remb_received,stream = %s bps = %s, dest = %s",
                            mediaStream.hashCode(), rtcp.bitrate, Arrays.toString(rtcp.dest))
                    streamStats.rembReceived(rtcp)
                }

                RTCPPacket.SR -> {
                    if (rtcp is RTCPSRPacket) {
                        streamStats.srReceived(rtcp)
                    }
                    val report: RTCPReport? = try {
                        val baos = ByteArrayOutputStream()
                        rtcp.assemble(DataOutputStream(baos))
                        val buf = baos.toByteArray()
                        parseRTCPReport(rtcp.type, buf, 0, buf.size)
                    } catch (ioe: IOException) {
                        Timber.e(ioe, "Failed to assemble an RTCP report.")
                        null
                    }
                    if (report != null) {
                        streamStats.rtcpReports.rtcpReportReceived(report)
                    }
                }

                RTCPPacket.RR -> {
                    val report: RTCPReport? = try {
                        val baos = ByteArrayOutputStream()
                        rtcp.assemble(DataOutputStream(baos))
                        val buf = baos.toByteArray()
                        parseRTCPReport(rtcp.type, buf, 0, buf.size)
                    } catch (ioe: IOException) {
                        Timber.e(ioe, "Failed to assemble an RTCP report.")
                        null
                    }
                    if (report != null) {
                        streamStats.rtcpReports.rtcpReportReceived(report)
                    }
                }
                RTCPFBPacket.RTPFB -> if (rtcp is NACKPacket) {
                    // NACKs are currently handled in RtxTransformer and do not
                    // reach the StatisticsEngine.
                    streamStats.nackReceived(rtcp)
                } else if (rtcp is RTCPTCCPacket) {
                    /*
                     * Intuition: Packet is RTCP, wakeup RTCPPacketListeners which may include BWE workers
                     */
                    streamStats.tccPacketReceived(rtcp)
                }
                RTCPExtendedReport.XR -> if (rtcp is RTCPExtendedReport) {
                    streamStats.rtcpReports.rtcpExtendedReportReceived(rtcp)
                }
                else -> {}
            }
        }
    }

    /**
     * Transfers RTCP sender report feedback as new information about the
     * download stream for the MediaStreamStats. Finds the info needed for
     * statistics in the packet and stores it, then returns the same packet as
     * `StatisticsEngine` is not modifying it.
     *
     * @param pkt the packet to transform
     * @return the packet which is the result of the transform
     */
    override fun transform(pkt: RawPacket): RawPacket {
        // SRTP may send non-RTCP packets.
        if (RTCPUtils.isRtcp(pkt.buffer, pkt.offset, pkt.length)) {
            mediaStreamStats.rtcpPacketSent(pkt.rtcpSSRC, pkt.length)
            try {
                updateSentMediaStreamStats(pkt)
            } catch (t: Throwable) {
                when (t) {
                    is InterruptedException -> {
                        Thread.currentThread().interrupt()
                    }
                    is ThreadDeath -> {
                        throw t
                    }
                    else -> {
                        Timber.e(t, "Failed to analyze an outgoing RTCP packet for the purposes of statistics.")
                    }
                }
            }

            // RTCP XR
            // We support RTCP XR VoIP Metrics Report Block only at the time of
            // this writing. While the methods addRTCPExtendedReports(RawPacket)
            // and createVoIPMetricsReportBlock(int) are aware of the fact, it
            // does not make sense to even go there for the sake of performance.
            if (MediaType.AUDIO == mediaType) {
                // We will send RTCP XR only if the SDP attribute rtcp-xr is
                // present and we will send only XR blocks indicated by SDP parameters.
                val o = mediaStream.getProperty(RTCPExtendedReport.SDP_ATTRIBUTE)
                if (o != null) {
                    val sdpParams = o.toString()
                    if (sdpParams.isNotEmpty()) {
                        val xrs = addRTCPExtendedReports(pkt, sdpParams)
                        if (xrs != null) {
                            val rtcpReports = mediaStream.mediaStreamStats.rtcpReports
                            for (xr in xrs) rtcpReports.rtcpExtendedReportSent(xr)
                        }
                    }
                }
            }
        }
        return pkt
    }

    /**
     * Transfers RTCP sender/receiver report feedback as new information about
     * the download stream for the `MediaStreamStats`.
     *
     * @param pkt the sent RTCP packet
     */
    @Throws(Exception::class)
    private fun updateSentMediaStreamStats(pkt: RawPacket) {
        val r = parseRTCPReport(pkt)
        if (r != null) {
            mediaStream.mediaStreamStats.rtcpReports.rtcpReportSent(r)
            val feedbackReports: List<*> = r.feedbackReports
            if (feedbackReports.isNotEmpty()) {
                val feedback = feedbackReports[0] as RTCPFeedback
                val ssrc = feedback.ssrc
                val jitter = feedback.jitter
                incrementSSRCCounter(numberOfRTCPReportsMap, ssrc, 1)
                if (jitter < minInterArrivalJitter
                        || minInterArrivalJitter == -1L) {
                    minInterArrivalJitter = jitter
                }
                if (maxInterArrivalJitter < jitter) maxInterArrivalJitter = jitter
                incrementSSRCCounter(jitterSumMap, ssrc, jitter)
                if (TimberLog.isTraceEnable) {
                    val numberOfRTCPReports = getMapValue(numberOfRTCPReportsMap, ssrc)
                    // As sender reports are sent on every 5 seconds, print
                    // every 4th packet, on every 20 seconds.
                    if (numberOfRTCPReports % 4 == 1L) {
                        val buff = StringBuilder(RTP_STAT_PREFIX)
                        val mediaTypeStr = mediaType?.toString() ?: ""
                        buff.append("Sending a report for ")
                                .append(mediaTypeStr).append(" stream SSRC:")
                                .append(ssrc).append(" [")
                        // SR includes sender info, RR does not.
                        if (r is RTCPSenderReport) {
                            buff.append("packet count:")
                                    .append(r.senderPacketCount)
                                    .append(", bytes:")
                                    .append(r.senderByteCount).append(", ")
                        }
                        buff.append("inter-arrival jitter:").append(jitter)
                                .append(", lost packets:")
                                .append(feedback.numLost)
                                .append(", time since previous report:")
                                .append((feedback.dlsr / 65.536).toInt())
                                .append("ms]")
                        Timber.log(TimberLog.FINER, "%s", buff)
                    }
                }
            }
        }
    }

    private inner class RTPPacketTransformer : SinglePacketTransformerAdapter(RTPPacketPredicate.INSTANCE) {
        override fun transform(pkt: RawPacket): RawPacket {
            mediaStreamStats.rtpPacketSent(pkt.getSSRCAsLong(), pkt.sequenceNumber, pkt.length, pkt.isSkipStats)
            return pkt
        }

        override fun reverseTransform(pkt: RawPacket): RawPacket {
            mediaStreamStats.rtpPacketReceived(pkt.getSSRCAsLong(), pkt.sequenceNumber, pkt.length)
            return pkt
        }
    }

    companion object {
        /**
         * The RTP statistics prefix we use for every log.
         * Simplifies parsing and searching for statistics info in log files.
         */
        const val RTP_STAT_PREFIX = "rtpstat:"

        /**
         * Determines whether `buf` appears to contain an RTCP packet
         * starting at `off` and spanning at most `len` bytes. Returns
         * the length in bytes of the RTCP packet if it was determined that there
         * indeed appears to be such an RTCP packet; otherwise, `-1`.
         *
         * @param buf
         * @param off
         * @param len
         * @return the length in bytes of the RTCP packet in `buf` starting
         * at `off` and spanning at most `len` bytes if it was determined that there
         * indeed appears to be such an RTCP packet; otherwise, `-1`
         */
        private fun getLengthIfRTCP(buf: ByteArray, off: Int, len: Int): Int {
            if (off >= 0 && RTCPUtils.isRtcp(buf, off, len)) {
                val bytes = RTCPUtils.getLength(buf, off, len)
                if (bytes <= len) {
                    return bytes
                }
            }
            return -1
        }

        /**
         * Computes the sum of the values of a specific `Map` with `Long` values.
         *
         * @param map the `Map` with `Long` values to sum up. Note that
         * we synchronize on this object!
         * @return the sum of the values of the specified `map`
         */
        private fun getCumulativeValue(map: Map<*, Long?>): Long {
            var cumulativeValue: Long = 0
            synchronized(map) {
                for (value in map.values) {
                    if (value == null) continue
                    cumulativeValue += value
                }
            }
            return cumulativeValue
        }

        /**
         * Utility method to return a value from a map and perform unboxing only if
         * the result value is not null.
         *
         * @param map the map to get the value. Note that we synchronize on that object!
         * @param ssrc the key
         * @return the result value or 0 if nothing is found.
         */
        private fun getMapValue(map: Map<*, Long>, ssrc: Long): Long {
            synchronized(map) {

                // there can be no entry, or the value can be null
                val res = map[ssrc]
                return res ?: 0
            }
        }

        /**
         * Utility method to increment map value with specified step. If entry is missing add it.
         *
         * @param map the map holding the values. Note that we synchronize on that
         * object!
         * @param ssrc the key of the value to increment
         * @param step increment step value
         */
        private fun incrementSSRCCounter(map: MutableMap<Long, Long>, ssrc: Long, step: Long) {
            synchronized(map) {
                val count = map[ssrc]
                map.put(ssrc, if (count == null) step else count + step)
            }
        }
    }
}