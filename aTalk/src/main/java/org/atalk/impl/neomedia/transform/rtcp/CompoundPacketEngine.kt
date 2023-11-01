/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.rtcp

import net.sf.fmj.media.rtp.RTCPHeader
import org.atalk.impl.neomedia.transform.PacketTransformer
import org.atalk.impl.neomedia.transform.TransformEngine
import org.atalk.service.neomedia.RawPacket
import java.util.*

/**
 * Implements a `TransformEngine` which splits incoming RTCP compound packets into individual
 * packets.
 *
 * @author Boris Grozev
 */
class CompoundPacketEngine : TransformEngine, PacketTransformer {
    /**
     * Used in `reverseTransform`, declared here to avoid recreation.
     */
    private val counts = IntArray(MAX_INDIVIDUAL)

    /**
     * Closes the transformer and underlying transform engine.
     *
     * Nothing to do here.
     */
    override fun close() {}

    /**
     * Returns a reference to this class since it is performing RTCP transformations in here.
     *
     * @return a reference to `this` instance of the `CompoundPacketEngine`.
     */
    override val rtcpTransformer: PacketTransformer
        get() = this

    /**
     * Always returns `null` since this engine does not require any RTP transformations.
     *
     * @return `null` since this engine does not require any RTP transformations.
     */
    override val rtpTransformer: PacketTransformer?
        get() = null

    override fun reverseTransform(pkts: Array<RawPacket?>): Array<RawPacket?> {
        var packets = pkts
        var needed = 0 // total number of individual packets in pkts
        var needToSplit = false
        Arrays.fill(counts, 0)

        // calculate needed and fill in this.counts
        for (i in packets!!.indices) {
            val pkt = packets[i]
            var individual = 0 // number of individual packets in pkt
            if (pkt != null) {
                val buf = pkt.buffer
                var off = pkt.offset
                var len = pkt.length
                var l: Int
                while (getLengthInBytes(buf, off, len).also { l = it } >= 0) {
                    individual++
                    off += l
                    len -= l
                }
                if (individual == 0) packets[i] = null // invalid RTCP packet. drop it.
                if (individual > 1) needToSplit = true
                needed += individual
                counts[i] = individual
            }
        }
        if (!needToSplit) return packets
        if (needed > MAX_INDIVIDUAL) return packets // something went wrong. let the original packet(s) go.

        // allocate a new larger array, if necessary
        if (needed > packets.size) {
            val newPkts = arrayOfNulls<RawPacket>(needed)
            System.arraycopy(packets, 0, newPkts, 0, packets.size)
            packets = newPkts
        }

        // do the actual splitting
        var i = 0
        while (i < packets.size) {
            if (counts[i] > 1) // need to split
            {
                var j: Int = 0 // empty spot always exists, because needed<=pkts.length
                while (j < packets.size) {
                    if (packets[j] == null) break
                    j++
                }

                // total off/len
                val oldBuf = packets[i]!!.buffer
                val oldOff = packets[i]!!.offset
                val oldLen = packets[i]!!.length

                // length of the first packet
                val len = getLengthInBytes(oldBuf, oldOff, oldLen)
                val buf = ByteArray(len)
                System.arraycopy(oldBuf, oldOff, buf, 0, len)
                packets[j] = RawPacket(buf, 0, len)
                counts[j]++
                packets[i]!!.offset = oldOff + len
                packets[i]!!.length = oldLen - len
                counts[i]--
                i-- // try that packet once again
            }
            i++
        }
        return packets
    }

    /**
     * {@inheritDoc}
     *
     * The implementation of `CompoundPacketEngine` does not transform when sending RTCP
     * packets because the only purpose is to split received compound RTCP packets.
     */
    override fun transform(pkts: Array<RawPacket?>): Array<RawPacket?> {
        return pkts
    }

    companion object {
        /**
         * The maximum number of individual RTCP packets contained in an RTCP compound packet. If an
         * input packet (seems to) contain more than this, it will remain unchanged.
         */
        private const val MAX_INDIVIDUAL = 20

        /**
         * Returns the length in bytes of the RTCP packet contained in `buf` at offset
         * `off`. Assumes that `buf` is valid at least until index `off`+3.
         *
         * @return the length in bytes of the RTCP packet contained in `buf` at offset
         * `off`.
         */
        private fun getLengthInBytes(buf: ByteArray, off: Int, len: Int): Int {
            if (len < 4) return -1
            val v = buf[off].toInt() and 0xc0 ushr 6
            if (RTCPHeader.VERSION != v) return -1
            val lengthInWords = buf[off + 2].toInt() and 0xFF shl 8 or (buf[off + 3].toInt() and 0xFF)
            val lengthInBytes = (lengthInWords + 1) * 4
            return if (len < lengthInBytes) -1 else lengthInBytes
        }
    }
}