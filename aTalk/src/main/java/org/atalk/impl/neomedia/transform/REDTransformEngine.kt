/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform

import net.sf.fmj.media.rtp.RTPHeader
import org.atalk.service.neomedia.RawPacket
import timber.log.Timber

/**
 * Implements a [PacketTransformer] and
 * [TransformEngine] for RED (RFC2198).
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class REDTransformEngine @JvmOverloads constructor(
        incomingPT: Byte = -1,
        outgoingPT: Byte = -1,
) : TransformEngine, PacketTransformer {
    /**
     * The RED payload type for incoming packets. Only RTP packets with this payload type will be
     * reverse-transformed by this `PacketTransformer`.
     *
     * The special value "-1" is used to effectively disable reverse-transforming packets by this `PacketTransformer`.
     */
    private var incomingPT: Byte = 0

    /**
     * The payload type to set when constructing RED packets (e.g. for outgoing) packets.
     *
     * The special value "-1" is used to effectively disable transforming packets by this `PacketTransformer`.
     */
    private var outgoingPT: Byte = 0

    /**
     * Initializes a new `REDTransformEngine` instance.
     *
     * @param incomingPT the RED payload type number for incoming packets.
     * @param outgoingPT the RED payload type number for outgoing packets.
     */
    init {
        setIncomingPT(incomingPT)
        setOutgoingPT(outgoingPT)
    }

    /**
     * Sets the RED payload type for incoming red packets.
     *
     * @param incomingPT the payload type to set.
     */
    fun setIncomingPT(incomingPT: Byte) {
        this.incomingPT = incomingPT
        Timber.i("Set incoming payload type %s", incomingPT)
    }

    /**
     * Sets the RED payload type for outgoing red packets.
     *
     * @param outgoingPT the payload type to set.
     */
    fun setOutgoingPT(outgoingPT: Byte) {
        this.outgoingPT = outgoingPT
        Timber.i("Set outgoing payload type %s", outgoingPT)
    }

    /**
     * {@inheritDoc}
     */
    override fun close() {}

    /**
     * {@inheritDoc}
     *
     * Reverse-transform a RED (RFC2198) packet.
     */
    override fun reverseTransform(pkts: Array<RawPacket?>): Array<RawPacket?> {
        if (incomingPT.toInt() == -1)
            return pkts

        // XXX: in the general case we should transform each packet in pkts and
        // then merge all the results somehow. However, for performance(*) and
        // simplicity, we assume that there is at most a single packet in pkts,
        // and the rest is null. This is a valid assumption with the currently
        // available PacketTransformers in libjitsi.
        //
        // (*) in the majority of packets there will be a single packet as a
        // result, and thus we get to reuse both pkts[0] and pkts itself.
        if (pkts.isNotEmpty()) {
            if (pkts[0] != null && pkts[0]!!.payloadType == incomingPT)
                return reverseTransformSingle(pkts[0], pkts)
        }
        return pkts
    }

    /**
     * {@inheritDoc}
     *
     * Encapsulates the packets in `pkts` with RED (RFC2198).
     *
     * Effectively inserts the following 1-byte RED header right after the
     * RTP header (where "Block PT" is the payload type of the original packet)
     * and changes the payload type of the packet to `outgoingPT`
     *
     * 0 1 2 3 4 5 6 7
     * +-+-+-+-+-+-+-+-+
     * |0|   Block PT  |
     * +-+-+-+-+-+-+-+-+
     */
    override fun transform(pkts: Array<RawPacket?>): Array<RawPacket?> {
        if (outgoingPT.toInt() == -1)
            return pkts

        for (pkt in pkts) {
            if (pkt != null && pkt.version == RTPHeader.VERSION) {
                val buf = pkt.buffer
                val len = pkt.length
                val off = pkt.offset
                val hdrLen = pkt.headerLength

                var newBuf = buf // try to reuse
                if (newBuf.size < len + 1) {
                    newBuf = ByteArray(len + 1)
                }

                System.arraycopy(buf, off, newBuf, 0, hdrLen)
                System.arraycopy(buf, off + hdrLen, newBuf, hdrLen + 1, len - hdrLen)
                newBuf[hdrLen] = pkt.payloadType

                pkt.buffer = newBuf
                pkt.offset = 0
                pkt.length = len + 1
                pkt.payloadType = outgoingPT
            }
        }
        return pkts
    }

    /**
     * Transforms the RFC2198 packet `pkt` into an array of RTP packets.
     */
    private fun reverseTransformSingle(pkt: RawPacket?, pkts: Array<RawPacket?>): Array<RawPacket?> {
        var pkts = pkts
        val buf = pkt!!.buffer
        val off = pkt.offset

        val hdrLen = pkt.headerLength
        var idx = off + hdrLen // beginning of RTP payload
        var pktCount = 1 // number of packets inside RED

        // 0 1 2 3
        // 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        // |F| block PT | timestamp offset | block length |
        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        while (buf[idx].toInt() and 0x80 != 0) {
            pktCount++
            idx += 4
        }
        idx = off + hdrLen // back to beginning of RTP payload

        if (pkts.size < pktCount)
            pkts = arrayOfNulls(pktCount)
        if (pktCount != 1)
            Timber.i("Received a RED packet with more than one packet inside")

        var payloadOffset = idx + (pktCount - 1) * 4 + 1 /* RED headers */

        // write non-primary packets, keep pkts[0] for the primary
        for (i in 1 until pktCount) {
            val blockLen = (buf[idx + 2].toInt() and 0x03) shl 8 or (buf[idx + 3].toInt() and 0xFF)

            // XXX: we might need to optimize
            val newBuf = ByteArray(hdrLen + blockLen)
            // XXX: might be wrong but this doesn't look right -- do we really
            // want to copy the RTP header from inside the payload?
            System.arraycopy(buf, payloadOffset, newBuf, 0, hdrLen + blockLen)

            // XXX: we might need to optimize
            if (pkts[i] == null)
                pkts[i] = RawPacket()

            pkts[i]!!.buffer = newBuf
            pkts[i]!!.offset = 0
            pkts[i]!!.length = hdrLen + blockLen
            pkts[i]!!.payloadType = (buf[idx].toInt() and 0xf7).toByte()
            // TODO: update timestamp

            idx += 4 // next RED header
            payloadOffset += blockLen
        }

        // idx is now at the "primary encoding block header":
        // 0 1 2 3 4 5 6 7
        // +-+-+-+-+-+-+-+-+
        // |0| Block PT |
        // +-+-+-+-+-+-+-+-+

        // write primary packet: reuse pkt
        pkt.payloadType = (buf[idx].toInt() and 0x7f).toByte()

        // reuse the buffer, move the payload "left". Moving the payload
        // right doesn't work because apparently we've got an offset
        // bug somewhere in libjitsi (in the SRTP transformer?) that
        // corrupts the authentication tag of outgoing RTP packets
        // resulting in RTP packets being discarded at the clients (both
        // FF and Chrome) because the RTP packet authentication fails.
        System.arraycopy(buf, off, buf, off + payloadOffset - hdrLen, hdrLen)
        pkt.offset = off + payloadOffset - hdrLen
        pkt.length = pkt.length - (payloadOffset - hdrLen)

        pkts[0] = pkt
        return pkts
    }

    /**
     * {@inheritDoc}
     *
     * Return the single `PacketTransformer` for this `TransformEngine`
     */
    override val rtpTransformer: PacketTransformer
        get() = this

    /**
     * {@inheritDoc}
     *
     * We don't touch RTCP
     */
    override val rtcpTransformer: PacketTransformer?
        get() = null
}