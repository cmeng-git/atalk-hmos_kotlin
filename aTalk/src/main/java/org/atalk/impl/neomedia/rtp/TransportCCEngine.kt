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
package org.atalk.impl.neomedia.rtp

import org.atalk.impl.neomedia.RTPPacketPredicate
import org.atalk.impl.neomedia.rtcp.RTCPTCCPacket
import org.atalk.impl.neomedia.rtcp.RTCPTCCPacket.PacketMap
import org.atalk.impl.neomedia.rtp.remotebitrateestimator.RemoteBitrateEstimatorAbsSendTime
import org.atalk.impl.neomedia.rtp.remotebitrateestimator.RemoteBitrateObserver
import org.atalk.impl.neomedia.transform.PacketTransformer
import org.atalk.impl.neomedia.transform.SinglePacketTransformerAdapter
import org.atalk.impl.neomedia.transform.TransformEngine
import org.atalk.service.neomedia.ByteArrayBufferImpl
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.RawPacket
import org.atalk.service.neomedia.TransmissionFailedException
import org.atalk.service.neomedia.VideoMediaStream
import org.atalk.service.neomedia.rtp.CallStatsObserver
import org.atalk.util.LRUCache
import org.atalk.util.RTPUtils
import org.atalk.util.logging.DiagnosticContext
import org.atalk.util.logging.TimeSeriesLogger
import timber.log.Timber
import java.io.IOException
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Implements transport-cc functionality as a [TransformEngine]. The
 * intention is to have the same instance shared between all media streams of
 * a transport channel, so we expect it will be accessed by multiple threads.
 * See https://tools.ietf.org/html/draft-holmer-rmcat-transport-wide-cc-extensions-01
 *
 * @author Boris Grozev
 * @author Julian Chukwu
 * @author George Politis
 * @author Eng Chong Meng
 */
class TransportCCEngine(diagnosticContext: DiagnosticContext) : RTCPPacketListenerAdapter(), RemoteBitrateObserver, CallStatsObserver {
    /**
     * Gets the engine which handles incoming RTP packets for this instance.
     */
    /**
     * The engine which handles incoming RTP packets for this instance. It
     * reads transport-wide sequence numbers and registers arrival times.
     */
    val ingressEngine = IngressEngine()

    /**
     * The engine which handles outgoing RTP packets for this instance. It
     * adds transport-wide sequence numbers to outgoing RTP packets.
     */
    private val egressEngine = EgressEngine()

    /**
     * The ID of the transport-cc RTP header extension, or -1 if one is not
     * configured.
     */
    private var extensionId = -1

    /**
     * The next sequence number to use for outgoing data packets.
     */
    private val outgoingSeq = AtomicInteger(1)

    /**
     * The running index of sent RTCP transport-cc feedback packets.
     */
    private val outgoingFbPacketCount = AtomicInteger()

    /**
     * The list of [MediaStream] that are using this
     * [TransportCCEngine].
     */
    private val mediaStreams: MutableList<MediaStream> = LinkedList<MediaStream>()

    /**
     * Some [VideoMediaStream] that utilizes this instance. We use it to
     * get the sender/media SSRC of the outgoing RTCP TCC packets.
     */
    private var anyVideoMediaStream: VideoMediaStream? = null

    /**
     * Incoming transport-wide sequence numbers mapped to the timestamp of their
     * reception (in milliseconds since the epoch).
     */
    private var incomingPackets: PacketMap? = null

    /**
     * Used to synchronize access to [.incomingPackets].
     */
    private val incomingPacketsSyncRoot = Any()

    /**
     * Used to synchronize access to [.sentPacketDetails].
     */
    private val sentPacketsSyncRoot = Any()

    /**
     * The [DiagnosticContext] to be used by this instance when printing diagnostic information.
     */
    private val diagnosticContext: DiagnosticContext

    /**
     * The time (in milliseconds since the epoch) at which the first received
     * packet in [.incomingPackets] was received (or -1 if the map is empty).
     * Kept here for quicker access, because the map is ordered by sequence number.
     */
    private var firstIncomingTs = -1L

    /**
     * The reference time of the remote clock. This is used to rebase the
     * arrival times in the TCC packets to a meaningful time base (that of the
     * sender). This is technically not necessary and it's done for convenience.
     */
    private var remoteReferenceTimeMs = -1L

    /**
     * Local time to map to the reference time of the remote clock. This is used
     * to rebase the arrival times in the TCC packets to a meaningful time base
     * (that of the sender). This is technically not necessary and it's done for convenience.
     */
    private var localReferenceTimeMs = -1L

    /**
     * Holds a key value pair of the packet sequence number and an object made
     * up of the packet send time and the packet size.
     */
    private val sentPacketDetails: MutableMap<Int, PacketDetail> = LRUCache(MAX_OUTGOING_PACKETS_HISTORY)

    /**
     * Used for estimating the bitrate from RTCP TCC feedback packets
     */
    private val bitrateEstimatorAbsSendTime: RemoteBitrateEstimatorAbsSendTime

    /**
     * Ctor.
     *
     * diagnosticContext the DiagnosticContext of this instance.
     */
    init {
        this.diagnosticContext = diagnosticContext
        bitrateEstimatorAbsSendTime = RemoteBitrateEstimatorAbsSendTime(this, diagnosticContext)
    }

    /**
     * Notifies this instance that a data packet with a specific transport-wide
     * sequence number was received on this transport channel.
     *
     * @param seq the transport-wide sequence number of the packet.
     * @param marked whether the RTP packet had the "marked" bit set.
     */
    private fun packetReceived(seq: Int, pt: Int, marked: Boolean) {
        val now = System.currentTimeMillis()
        synchronized(incomingPacketsSyncRoot) {
            if (incomingPackets == null) {
                incomingPackets = PacketMap()
            }
            if (incomingPackets!!.size >= MAX_INCOMING_PACKETS_HISTORY) {
                val iter = incomingPackets!!.entries.iterator()
                if (iter.hasNext()) {
                    iter.next()
                    iter.remove()
                }

                // This shouldn't happen, because we will send feedback often.
                Timber.i("Reached max size, removing an entry.")
            }
            if (incomingPackets!!.isEmpty()) {
                firstIncomingTs = now
            }
            incomingPackets!!.put(seq, now)
        }
        if (timeSeriesLogger.isTraceEnabled) {
            timeSeriesLogger.trace(diagnosticContext
                    .makeTimeSeriesPoint("ingress_tcc_pkt", now)
                    .addField("seq", seq)
                    .addField("pt", pt))
        }
        maybeSendRtcp(marked, now)
    }

    /**
     * Gets the source SSRC to use for the outgoing RTCP TCC packets.
     *
     * @return the source SSRC to use for the outgoing RTCP TCC packets.
     */
    private val sourceSSRC: Long
        get() {
            val stream = anyVideoMediaStream ?: return -1
            val receiver = stream.mediaStreamTrackReceiver ?: return -1
            val tracks = receiver.mediaStreamTracks
            if (tracks == null || tracks.isEmpty()) {
                return -1
            }
            val encodings = tracks[0]!!.rtpEncodings
            return if (encodings == null || encodings.isEmpty()) {
                -1
            } else encodings[0]!!.primarySSRC
        }

    /**
     * Examines the list of received packets for which we have not yet sent
     * feedback and determines whether we should send feedback at this point.
     * If so, sends the feedback.
     *
     * @param marked whether the last received RTP packet had the "marked" bit
     * set.
     * @param now the current time.
     */
    private fun maybeSendRtcp(marked: Boolean, now: Long) {
        var packets: PacketMap? = null
        var delta: Long
        synchronized(incomingPacketsSyncRoot) {
            if (incomingPackets == null || incomingPackets!!.isEmpty()) {
                // No packets with unsent feedback.
                return
            }
            delta = if (firstIncomingTs == -1L) 0 else now - firstIncomingTs

            // The number of packets represented in incomingPackets (including
            // the missing ones), i.e. the number of entries that the RTCP TCC
            // packet would include.
            val packetCount = 1 + RTPUtils.subtractNumber(
                    incomingPackets!!.lastKey(),
                    incomingPackets!!.firstKey())

            // This condition controls when we send feedback:
            // 1. If 100ms have passed,
            // 2. If we see the end of a frame, and 20ms have passed, or
            // 3. If we have at least 100 packets.
            // 4. We are approaching the maximum number of packets we can
            // report on in one RTCP packet.
            // The exact values and logic here are to be improved.
            if (delta > 100 || delta > 20 && marked || incomingPackets!!.size > 100 || packetCount >= RTCPTCCPacket.MAX_PACKET_COUNT - 20) {
                packets = incomingPackets
                incomingPackets = null
                firstIncomingTs = -1
            }
        }
        if (packets != null) {
            val stream = mediaStream
            if (stream == null) {
                Timber.w("No media stream, can't send RTCP.")
                return
            }
            try {
                val senderSSRC = anyVideoMediaStream!!.streamRTPManager!!.localSSRC
                if (senderSSRC == -1L) {
                    Timber.w("No sender SSRC, can't send RTCP.")
                    return
                }
                val sourceSSRC = sourceSSRC
                if (sourceSSRC == -1L) {
                    Timber.w("No source SSRC, can't send RTCP.")
                    return
                }
                val rtcpPacket = RTCPTCCPacket(
                        senderSSRC, sourceSSRC,
                        packets!!, (outgoingFbPacketCount.getAndIncrement() and 0xff).toByte(), diagnosticContext)

                // Inject the TCC packet *after* this engine. We don't want
                // RTCP termination -which runs before this engine in the 
                // egress- to drop the packet we just sent.
                stream.injectPacket(rtcpPacket.toRawPacket(), false /* rtcp */, egressEngine)
            } catch (iae: IllegalArgumentException) {
                // This comes from the RTCPTCCPacket constructor when the
                // list of packets contains a delta which cannot be expressed
                // in a single packet (more than 8192 milliseconds), or the
                // number of packets to report (including the ones lost) is
                // too big for one RTCP TCC packet. In this case we would have
                // to split the feedback in two or more RTCP TCC packets.
                // We currently don't do this, because it only happens if the
                // receiver stops sending packets for over 8s or there is a
                // significant gap in the received sequence numbers. In this
                // case we will fail to send one feedback message.
                Timber.w("Not sending transport-cc feedback, delta or packet count too big.")
            } catch (e: IOException) {
                Timber.e(e, "Failed to send transport feedback RTCP")
            } catch (e: TransmissionFailedException) {
                Timber.e(e, "Failed to send transport feedback RTCP")
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onRttUpdate(avgRttMs: Long, maxRttMs: Long) {
        bitrateEstimatorAbsSendTime.onRttUpdate(avgRttMs, maxRttMs)
    }

    /**
     * Sets the ID of the transport-cc RTP extension. Set to -1 to effectively disable.
     *
     * @param id the ID to set.
     */
    fun setExtensionID(id: Int) {
        extensionId = id
    }

    /**
     * Called when a receive channel group has a new bitrate estimate for the incoming streams.
     *
     * @param ssrcs
     * @param bitrate
     */

    override fun onReceiveBitrateChanged(ssrcs: Collection<Long>, bitrate: Long) {
        val videoStream: VideoMediaStream
        for (stream in mediaStreams) {
            if (stream is VideoMediaStream) {
                videoStream = stream
                videoStream.orCreateBandwidthEstimator!!.updateReceiverEstimate(bitrate)
                break
            }
        }
    }

    /**
     * Handles an incoming RTCP transport-cc feedback packet.
     *
     * @param tccPacket the received TCC packet.
     */
    override fun tccReceived(tccPacket: RTCPTCCPacket?) {
        val packetMap = tccPacket!!.packets
        var previousArrivalTimeMs = -1L
        for ((key, arrivalTime250Us) in packetMap!!.entries) {
            if (arrivalTime250Us == -1L) {
                continue
            }
            if (remoteReferenceTimeMs == -1L) {
                remoteReferenceTimeMs = RTCPTCCPacket.getReferenceTime250us(
                        ByteArrayBufferImpl(tccPacket.fci, 0, tccPacket.fci.size)) / 4
                localReferenceTimeMs = System.currentTimeMillis()
            }
            var packetDetail: PacketDetail?
            synchronized(sentPacketsSyncRoot) { packetDetail = sentPacketDetails.remove(key) }
            if (packetDetail == null) {
                continue
            }
            val arrivalTimeMs = arrivalTime250Us / 4 - remoteReferenceTimeMs + localReferenceTimeMs
            if (timeSeriesLogger.isTraceEnabled) {
                if (previousArrivalTimeMs != -1L) {
                    val diff_ms = arrivalTimeMs - previousArrivalTimeMs
                    timeSeriesLogger.trace(diagnosticContext
                            .makeTimeSeriesPoint("ingress_tcc_ack")
                            .addField("seq", key)
                            .addField("arrival_time_ms", arrivalTimeMs)
                            .addField("diff_ms", diff_ms))
                } else {
                    timeSeriesLogger.trace(diagnosticContext
                            .makeTimeSeriesPoint("ingress_tcc_ack")
                            .addField("seq", key)
                            .addField("arrival_time_ms", arrivalTimeMs))
                }
            }
            previousArrivalTimeMs = arrivalTimeMs
            val sendTime24bits = RemoteBitrateEstimatorAbsSendTime.convertMsTo24Bits(packetDetail!!.packetSendTimeMs)
            bitrateEstimatorAbsSendTime.incomingPacketInfo(
                    arrivalTimeMs, sendTime24bits, packetDetail!!.packetLength, tccPacket.sourceSSRC)
        }
    }

    /**
     * Gets the engine which handles outgoing RTP packets for this instance.
     */
    fun getEgressEngine(): TransformEngine {
        return egressEngine
    }

    /**
     * Adds a [MediaStream] to the list of [MediaStream]s which
     * use this [TransportCCEngine].
     *
     * @param mediaStream the stream to add.
     */
    fun addMediaStream(mediaStream: MediaStream) {
        synchronized(mediaStreams) {
            mediaStreams.add(mediaStream)

            // Hook us up to receive TCCs.
            val stats = mediaStream.mediaStreamStats
            stats.addRTCPPacketListener(this)
            if (mediaStream is VideoMediaStream) {
                anyVideoMediaStream = mediaStream
                diagnosticContext["video_stream"] = mediaStream.hashCode()
            }
        }
    }

    /**
     * Removes a [MediaStream] from the list of [MediaStream]s which
     * use this [TransportCCEngine].
     *
     * @param mediaStream the stream to remove.
     */
    fun removeMediaStream(mediaStream: MediaStream) {
        synchronized(mediaStreams) {
            while (mediaStreams.remove(mediaStream)) {
                // we loop in order to remove all instances.
            }
            // Hook us up to receive TCCs.
            val stats = mediaStream.mediaStreamStats
            stats.removeRTCPPacketListener(this)
            if (mediaStream === anyVideoMediaStream) {
                anyVideoMediaStream = null
            }
        }
    }

    /**
     * @return one of the [MediaStream] instances which use this [TransportCCEngine], or null.
     */
    private val mediaStream: MediaStream?
        get() {
            synchronized(mediaStreams) { return if (mediaStreams.isEmpty()) null else mediaStreams[0] }
        }

    /**
     * [PacketDetail] is an object that holds the
     * length(size) of the packet in [.packetLength]
     * and the time stamps of the outgoing packet
     * in [.packetSendTimeMs]
     */
    private inner class PacketDetail(var packetLength: Int, var packetSendTimeMs: Long)

    /**
     * Handles outgoing RTP packets for this [TransportCCEngine].
     */
    inner class EgressEngine
    /**
     * Ctor.
     */
        : SinglePacketTransformerAdapter(RTPPacketPredicate), TransformEngine {
        /**
         * {@inheritDoc}
         *
         *
         * If the transport-cc extension is configured, update the
         * transport-wide sequence number (adding a new extension if one doesn't exist already).
         */
        override fun transform(pkt: RawPacket): RawPacket {
            if (extensionId != -1) {
                var ext = pkt.getHeaderExtension(extensionId.toByte())
                if (ext == null) {
                    ext = pkt.addExtension(extensionId.toByte(), 2)
                }
                val seq = outgoingSeq.getAndIncrement() and 0xffff
                RTPUtils.writeShort(ext.buffer, ext.offset + 1, seq.toShort())
                if (timeSeriesLogger.isTraceEnabled) {
                    timeSeriesLogger.trace(diagnosticContext
                            .makeTimeSeriesPoint("egress_tcc_pkt")
                            .addField("rtp_seq", pkt.sequenceNumber)
                            .addField("pt", RawPacket.getPayloadType(pkt))
                            .addField("tcc_seq", seq))
                }
                synchronized(sentPacketsSyncRoot) { sentPacketDetails.put(seq, PacketDetail(pkt.length, System.currentTimeMillis())) }
            }
            return pkt
        }

        /**
         * {@inheritDoc}
         */
        override val rtpTransformer: PacketTransformer
            get() = this

        /**
         * {@inheritDoc}
         */
        override val rtcpTransformer: PacketTransformer?
            get() = null
    }

    /**
     * Handles incoming RTP packets for this [TransportCCEngine].
     */
    inner class IngressEngine
    /**
     * Ctor.
     */
        : SinglePacketTransformerAdapter(RTPPacketPredicate.INSTANCE), TransformEngine {
        /**
         * {@inheritDoc}
         */
        override fun reverseTransform(pkt: RawPacket): RawPacket? {
            if (extensionId != -1) {
                val he = pkt!!.getHeaderExtension(extensionId.toByte())
                if (he != null && he.extLength == 2) {
                    val seq = RTPUtils.readUint16AsInt(he.buffer, he.offset + 1)
                    packetReceived(seq, RawPacket.getPayloadType(pkt), pkt.isPacketMarked)
                }
            }
            return pkt
        }

        /**
         * {@inheritDoc}
         */
        override val rtpTransformer: PacketTransformer
            get() = this

        /**
         * {@inheritDoc}
         */
        override val rtcpTransformer: PacketTransformer?
            get() = null
    }

    companion object {
        /**
         * The maximum number of received packets and their timestamps to save.
         */
        private const val MAX_INCOMING_PACKETS_HISTORY = 200

        /**
         * The maximum number of received packets and their timestamps to save.
         *
         * XXX this is an uninformed value.
         */
        private const val MAX_OUTGOING_PACKETS_HISTORY = 1000

        /**
         * The [TimeSeriesLogger] to be used by this instance to print time series.
         */
        private val timeSeriesLogger = TimeSeriesLogger.getTimeSeriesLogger(TransportCCEngine::class.java)
    }
}