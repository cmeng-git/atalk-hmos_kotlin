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
package org.atalk.impl.neomedia.transform

import org.atalk.impl.neomedia.MediaStreamImpl
import org.atalk.impl.neomedia.RTCPPacketPredicate
import org.atalk.impl.neomedia.RTPPacketPredicate
import org.atalk.impl.neomedia.rtcp.NACKPacket
import org.atalk.impl.neomedia.rtcp.RTCPFBPacket
import org.atalk.impl.neomedia.rtcp.RTCPIterator
import org.atalk.impl.neomedia.rtp.MediaStreamTrackReceiver
import org.atalk.impl.neomedia.rtp.RawPacketCache
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.RawPacket
import org.atalk.service.neomedia.TransmissionFailedException
import org.atalk.service.neomedia.codec.Constants
import org.atalk.util.logging.Logger
import timber.log.Timber
import java.util.*
import kotlin.math.min

/**
 * Intercepts RTX (RFC-4588) packets coming from an [MediaStream], and removes their RTX encapsulation.
 *
 * Intercepts NACKs and retransmits packets to a mediaStream (using the RTX
 * format if the destination supports it).
 *
 * @author Boris Grozev
 * @author George Politis
 * @author Eng Chong Meng
 */
class RtxTransformer(mediaStream: MediaStreamImpl) : TransformEngine {
    /**
     * The `MediaStream` for the transformer.
     */
    private val mediaStream: MediaStreamImpl

    /**
     * Maps an RTX SSRC to the last RTP sequence number sent with that SSRC.
     */
    private val rtxSequenceNumbers = HashMap<Long, Int>()

    /**
     * The payload type number configured for RTX (RFC-4588), mapped by the media payload type number.
     */
    private var apt2rtx = HashMap<Byte, Byte>()

    /**
     * The "associated payload type" number for RTX, mapped by the RTX payload type number.
     */
    private var rtx2apt = HashMap<Byte, Byte>()

    /**
     * The transformer that decapsulates RTX.
     */
    override val rtpTransformer = RTPTransformer()

    /**
     * The transformer that handles NACKs.
     */
    override val rtcpTransformer = RTCPTransformer()

    /**
     * Initializes a new `RtxTransformer` with a specific `MediaStreamImpl`.
     *
     * mediaStream the `MediaStreamImpl` for the transformer.
     */
    init {
        this.mediaStream = mediaStream
        val cfg = LibJitsi.configurationService
        val enableNackTermination = !cfg.getBoolean(DISABLE_NACK_TERMINATION_PNAME, false)

        if (enableNackTermination) {
            val cache = mediaStream.cachingTransformer
            if (cache != null) {
                cache.setEnabled(true)
            } else {
                Timber.w("NACK termination is enabled, but we don't have a packet cache.")
            }
        }
    }

    /**
     * Returns a boolean that indicates whether or not the destination endpoint supports RTX.
     *
     * @return true if the destination endpoint supports RTX, otherwise false.
     */
    fun destinationSupportsRtx(): Boolean {
        return apt2rtx.isNotEmpty()
    }

    /**
     * Returns the sequence number to use for a specific RTX packet, which
     * is based on the packet's original sequence number.
     *
     * Because we terminate the RTX format, and with simulcast we might
     * translate RTX packets from multiple SSRCs into the same SSRC, we keep
     * count of the RTX packets (and their sequence numbers) which we sent for each SSRC.
     *
     * @param ssrc the SSRC of the RTX stream for the packet.
     * @return the sequence number which should be used for the next RTX packet sent using SSRC `ssrc`.
     */
    private fun getNextRtxSequenceNumber(ssrc: Long): Int {
        var seq: Int?
        synchronized(rtxSequenceNumbers) {
            seq = rtxSequenceNumbers[ssrc]
            seq = if (seq == null)
                Random().nextInt(0xffff)
            else seq!! + 1
            rtxSequenceNumbers.put(ssrc, seq!!)
        }
        return seq!!
    }

    /**
     * Tries to find an SSRC paired with `ssrc` in an FID group in one
     * of the mediaStreams from [.mediaStream]'s `Content`. Returns -1 on failure.
     *
     * @param pkt the `RawPacket` that holds the RTP packet for which to find a paired SSRC.
     * @return An SSRC paired with `ssrc` in an FID group, or -1.
     */
    private fun getRtxSsrc(pkt: RawPacket): Long {
        val receiveRTPManager = mediaStream.rtpTranslator!!.findStreamRTPManagerByReceiveSSRC(pkt.getSSRC())

        var receiver: MediaStreamTrackReceiver? = null
        if (receiveRTPManager != null) {
            val receiveStream = receiveRTPManager.mediaStream
            if (receiveStream != null) {
                receiver = receiveStream.mediaStreamTrackReceiver
            }
        }
        if (receiver == null) {
            return -1
        }
        val encoding = receiver.findRTPEncodingDesc(pkt)
        if (encoding == null) {
            Timber.w("Encoding not found, stream_hash = %s; ssrc = %s", mediaStream.hashCode(), pkt.getSSRCAsLong())
            return -1
        }
        return encoding.getSecondarySsrc(Constants.RTX)
    }

    /**
     * Retransmits a packet to [.mediaStream]. If the destination supports the RTX format,
     * the packet will be encapsulated in RTX, otherwise, the packet will be retransmitted as-is.
     *
     * @param pkt the packet to retransmit.
     * @param rtxPt the RTX payload type to use for the re-transmitted packet.
     * @param after the `TransformEngine` in the chain of `TransformEngine`s of the
     * associated `MediaStream` after which the injection of `pkt` is to begin
     * @return `true` if the packet was successfully retransmitted, `false` otherwise.
     */
    private fun retransmit(pkt: RawPacket, rtxPt: Byte?, after: TransformEngine): Boolean {
        val destinationSupportsRtx = rtxPt != null
        val retransmitPlain = if (destinationSupportsRtx) {
            val rtxSsrc = getRtxSsrc(pkt)
            if (rtxSsrc == -1L) {
                Timber.w("Cannot find SSRC for RTX, retransmitting plain. SSRC = %s", pkt.getSSRCAsLong())
                true
            } else {
                !encapsulateInRtxAndTransmit(pkt, rtxSsrc, rtxPt!!, after)
            }
        } else {
            true
        }

        if (retransmitPlain) {
            if (mediaStream != null) {
                try {
                    mediaStream.injectPacket(pkt,  /* data */true, after)
                } catch (tfe: TransmissionFailedException) {
                    Timber.w("Failed to retransmit a packet.")
                    return false
                }
            }
        }
        return true
    }

    /**
     * Notifies this instance that the dynamic payload types of the associated [MediaStream] have changed.
     */
    fun onDynamicPayloadTypesChanged() {
        val apt2rtx = HashMap<Byte, Byte>()
        val rtx2apt = HashMap<Byte, Byte>()
        val mediaFormatMap = mediaStream.getDynamicRTPPayloadTypes()
        val it = mediaFormatMap.entries.iterator()
        while (it.hasNext()) {
            val (pt, format) = it.next()
            if (!Constants.RTX.equals(format.encoding, ignoreCase = true)) {
                continue
            }

            val aptString = format.formatParameters["apt"]
            val apt = try {
                aptString!!.toByte()
            } catch (nfe: NumberFormatException) {
                Timber.e("Failed to parse apt: %s", aptString)
                continue
            }

            apt2rtx[apt] = pt
            rtx2apt[pt] = apt
        }
        this.rtx2apt = rtx2apt
        this.apt2rtx = apt2rtx
    }

    /**
     * Encapsulates `pkt` in the RTX format, using `rtxSsrc` as its SSRC, and
     * transmits it to [.mediaStream] by injecting it in the `MediaStream`.
     *
     * @param pkt the packet to transmit.
     * @param rtxSsrc the SSRC for the RTX stream.
     * @param after the `TransformEngine` in the chain of `TransformEngine`s of the associated
     * `MediaStream` after which the injection of `pkt` is to begin
     * @return `true` if the packet was successfully retransmitted, `false` otherwise.
     */
    private fun encapsulateInRtxAndTransmit(
            pkt: RawPacket, rtxSsrc: Long, rtxPt: Byte, after: TransformEngine): Boolean {
        val buf = pkt.buffer
        val len = pkt.length
        val off = pkt.offset

        val newBuf = ByteArray(len + 2)
        val rtxPkt = RawPacket(newBuf, 0, len + 2)

        val osn = pkt.sequenceNumber
        val headerLength = pkt.headerLength
        val payloadLength = pkt.payloadLength

        // Copy the header.
        System.arraycopy(buf, off, newBuf, 0, headerLength)

        // Set the OSN field.
        newBuf[headerLength] = (osn shr 8 and 0xff).toByte()
        newBuf[headerLength + 1] = (osn and 0xff).toByte()

        // Copy the payload.
        System.arraycopy(buf, off + headerLength, newBuf, headerLength + 2, payloadLength)
        if (mediaStream != null) {
            rtxPkt.setSSRC(rtxSsrc.toInt())
            rtxPkt.payloadType = rtxPt
            // Only call getNextRtxSequenceNumber() when we're sure we're going
            // to transmit a packet, because it consumes a sequence number.
            rtxPkt.sequenceNumber = getNextRtxSequenceNumber(rtxSsrc)
            try {
                mediaStream.injectPacket(rtxPkt,  /* data */true, after)
            } catch (tfe: TransmissionFailedException) {
                Timber.w("Failed to transmit an RTX packet.")
                return false
            }
        }
        return true
    }

    /**
     * Returns the SSRC paired with `ssrc` in an FID source-group, if any. If none is found, returns -1.
     *
     * @return the SSRC paired with `ssrc` in an FID source-group, if any. If none is found, returns -1.
     */
    private fun getPrimarySsrc(rtxSSRC: Long): Long {
        val receiver = mediaStream.mediaStreamTrackReceiver
        if (receiver == null) {
            Timber.d("Dropping an incoming RTX packet from an unknown source.")
            return -1
        }

        val encoding = receiver.findRTPEncodingDesc(rtxSSRC)
        if (encoding == null) {
            Timber.d("Dropping an incoming RTX packet from an unknown source.")
            return -1
        }
        return encoding.primarySSRC
    }

    private val cache: RawPacketCache?
        get() {
            val stream = mediaStream
            return stream.cachingTransformer?.outgoingRawPacketCache
        }

    /**
     * @param mediaSSRC
     * @param lostPackets
     */
    private fun nackReceived(mediaSSRC: Long, lostPackets: MutableCollection<Int>) {
        Timber.d("%s nack_received,stream = %d; ssrc = %s; lost_packets = %s",
                Logger.Category.STATISTICS, mediaStream.hashCode(), mediaSSRC, lostPackets)

        val cache = cache
        if (cache != null) {
            // Retransmitted packets need to be inserted:
            // * after SSRC-rewriting (the external transform engine)
            // * after statistics (we update them explicitly)
            // * before abs-send-time
            // * before SRTP
            // We use 'this', because the RtxTransformer happens to be in the
            // correct place in the chain. See
            // MediaStreamImpl#createTransformEngineChain.
            // The position of the packet cache does not matter, because we update it explicitly.
            val after = this
            val rtt = mediaStream.mediaStreamStats.sendStats.rtt
            val now = System.currentTimeMillis()
            val i = lostPackets.iterator()

            while (i.hasNext()) {
                val seq = i.next()
                val container = cache.getContainer(mediaSSRC, seq)
                val stats = mediaStream.mediaStreamStats
                if (container != null) {
                    // Cache hit.
                    val delay = now - container.timeAdded
                    val send = rtt == -1L || delay >= min(rtt * 0.9, (rtt - 5).toDouble())
                    Timber.d("%s retransmitting stream = %d, ssrc = %s,seq = %d,send = %s",
                            Logger.Category.STATISTICS, mediaStream.hashCode(), mediaSSRC, seq, send)

                    val rtxPt = apt2rtx[container.pkt!!.payloadType]
                    if (send && retransmit(container.pkt!!, rtxPt, after)) {
                        stats.rtpPacketRetransmitted(mediaSSRC, container.pkt!!.length.toLong())

                        // We just retransmitted the packet. Update its
                        // timestamp in the cache so that we use the new
                        // timestamp when we handle subsequent NACKs.
                        cache.updateTimestamp(mediaSSRC, seq, now)
                        i.remove()
                    }
                    if (!send) {
                        stats.rtpPacketNotRetransmitted(mediaSSRC, container.pkt!!.length.toLong())
                        i.remove()
                    }
                } else {
                    stats.rtpPacketCacheMiss(mediaSSRC)
                }
            }
        }

        if (lostPackets.isNotEmpty()) {
            // If retransmission requests are enabled, videobridge assumes
            // the responsibility of requesting missing packets.
            Timber.d("Packets missing from the cache.")
        }
    }

    /**
     * Sends padding packets with the RTX SSRC associated to the media SSRC that
     * is passed as a parameter. It implements packet triplication.
     *
     * @param ssrc the media SSRC to protect.
     * @param bytes the amount of padding to send in bytes.
     * @return the remaining padding bytes budget.
     */
    fun sendPadding(ssrc: Long, bytes: Int): Int {
        var bytes = bytes
        val receiveRTPManager = mediaStream.rtpTranslator!!.findStreamRTPManagerByReceiveSSRC(ssrc.toInt())
        if (receiveRTPManager == null) {
            Timber.w("rtp_manager_not_found, stream_hash = %s; ssrc = %s", mediaStream.hashCode(), ssrc)
            return bytes
        }

        val receiveStream = receiveRTPManager.mediaStream
        if (receiveStream == null) {
            Timber.w("stream_not_found, stream_hash = %s;  ssrc = %s", mediaStream.hashCode(), ssrc)
            return bytes
        }

        val receiver = receiveStream.mediaStreamTrackReceiver
        if (receiver == null) {
            Timber.w("receiver_not_found, stream_hash = %s; ssrc = %s", mediaStream.hashCode(), ssrc)
            return bytes
        }

        val encoding = receiver.findRTPEncodingDesc(ssrc)
        if (encoding == null) {
            Timber.w("encoding_not_found, stream_hash = %s; ssrc = %s", mediaStream.hashCode(), ssrc)
            return bytes
        }

        val cache = cache ?: return bytes
        val lastNPackets = cache.getMany(ssrc, bytes)
        if (lastNPackets == null || lastNPackets.isEmpty()) {
            return bytes
        }

        // XXX this constant is not great, however the final place of the stream
        // protection strategy is not clear at this point so I expect the code
        // will change before taking its final form.
        for (i in 0..1) {
            val it = lastNPackets.iterator()
            while (it.hasNext()) {
                val container = it.next()
                val pkt = container.pkt
                // Containers are recycled/reused, so we must check if the packet is still there.
                if (pkt != null) {
                    val len = container.pkt!!.length
                    val apt = rtx2apt[container.pkt!!.payloadType]

                    // XXX if the client doesn't support RTX, then we can not
                    // effectively ramp-up bwe using duplicates because they
                    // would be dropped too early in the SRTP layer. So we are
                    // forced to use the bridge's SSRC and thus increase the
                    // probability of losses.
                    if (bytes - len > 0 && apt != null) {
                        retransmit(container.pkt!!, apt, this)
                        bytes -= len
                    } else {
                        // Don't break as we might be able to squeeze in the next packet.
                    }
                }
            }
        }
        return bytes
    }

    /**
     * The transformer that decapsulates RTX.
     */
    inner class RTPTransformer
    /**
     * Ctor.
     */
    internal constructor() : SinglePacketTransformerAdapter(RTPPacketPredicate.INSTANCE) {
        /**
         * Implements [PacketTransformer.transform].
         * {@inheritDoc}
         */
        override fun reverseTransform(pkt: RawPacket): RawPacket? {
            val apt = rtx2apt[pkt.payloadType]
                    ?: return pkt

            if (pkt.payloadLength - pkt.paddingSize < 2) {
                // We need at least 2 bytes to read the OSN field.
                Timber.d("Dropping an incoming RTX packet with padding only: %s", pkt)
                return null
            }

            var success = false
            val mediaSsrc = getPrimarySsrc(pkt.getSSRCAsLong())
            if (mediaSsrc != -1L) {
                val osn = pkt.originalSequenceNumber
                // Remove the RTX header by moving the RTP header two bytes right.
                val buf = pkt.buffer
                val off = pkt.offset
                System.arraycopy(buf, off, buf, off + 2, pkt.headerLength)

                pkt.offset = off + 2
                pkt.length = pkt.length - 2
                pkt.setSSRC(mediaSsrc.toInt())
                pkt.sequenceNumber = osn
                pkt.payloadType = apt
                success = true
            }
            // If we failed to handle the RTX packet, drop it.
            return if (success) pkt
            else null
        }
    }

    /**
     * The transformer that handles NACKs.
     */
    inner class RTCPTransformer
    internal constructor() : SinglePacketTransformerAdapter(RTCPPacketPredicate.INSTANCE) {
        /**
         * Implements [PacketTransformer.transform].
         * {@inheritDoc}
         */
        override fun reverseTransform(pkt: RawPacket): RawPacket {
            val it = RTCPIterator(pkt)
            while (it.hasNext()) {
                val next = it.next()
                if (NACKPacket.isNACKPacket(next)) {
                    val lostPackets = NACKPacket.getLostPackets(next)
                    val mediaSSRC = RTCPFBPacket.getSourceSSRC(next)
                    nackReceived(mediaSSRC, lostPackets)
                    it.remove()
                }
            }
            return pkt
        }
    }

    companion object {
        /**
         * The name of the property used to disable NACK termination.
         */
        const val DISABLE_NACK_TERMINATION_PNAME = "neomedia.rtcp.DISABLE_NACK_TERMINATION"
    }
}