/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.recording

import org.atalk.impl.neomedia.transform.PacketTransformer
import org.atalk.impl.neomedia.transform.TransformEngine
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.RawPacket
import java.util.*

/**
 * A `TransformEngine` and `PacketTransformer` which implement a fixed-size buffer.
 * The class is specific to video recording. Buffered are only VP8 RTP packets, and they are places
 * in different buffers according to their SSRC.
 *
 * @author Boris Grozev
 */
class PacketBuffer : TransformEngine, PacketTransformer {
    /**
     * The map of actual `Buffer` instances, one for each SSRC that this
     * `PacketBuffer` buffers in each instant.
     */
    private val buffers = HashMap<Long, Buffer>()

    /**
     * Implements [PacketTransformer.close].
     */
    override fun close() {}

    /**
     * Implements
     * [PacketTransformer.reverseTransform].
     *
     * Replaces each packet in the input with a packet (or null) from the
     * `Buffer` instance for the packet's SSRC.
     *
     * @param pkts the transformed packets to be restored.
     * @return
     */
    override fun reverseTransform(pkts: Array<RawPacket?>): Array<RawPacket?> {
        for (i in pkts!!.indices) {
            var pkt = pkts[i]

            // Drop padding packets. We assume that any packets with padding
            // are no-payload probing packets.
            if (pkt != null && pkt.paddingSize != 0) pkts[i] = null
            pkt = pkts[i]
            if (willBuffer(pkt)) {
                val buffer = getBuffer(pkt!!.getSSRCAsLong())
                pkts[i] = buffer.insert(pkt)
            }
        }
        return pkts
    }

    /**
     * Implements [PacketTransformer.transform].
     */
    override fun transform(pkts: Array<RawPacket?>): Array<RawPacket?> {
        return pkts
    }

    /**
     * Implements TransformEngine.getRTPTransformer.
     */
    override val rtpTransformer: PacketTransformer
        get() = this

    /**
     * Implements TransformEngine.getRTCPTransformer.
     */
    override val rtcpTransformer: PacketTransformer?
        get() = null

    /**
     * Checks whether a particular `RawPacket` will be buffered or not by this instance.
     * Currently we only buffer VP8 packets, recognized by their payload type number.
     *
     * @param pkt
     * the packet for which to check.
     * @return
     */
    private fun willBuffer(pkt: RawPacket?): Boolean {
        return pkt != null && pkt.payloadType.toInt() == VP8_PAYLOAD_TYPE
    }

    /**
     * Disables the `Buffer` for a specific SSRC.
     *
     * @param ssrc
     */
    fun disable(ssrc: Long) {
        getBuffer(ssrc).disabled = true
    }

    /**
     * Resets the buffer for a particular SSRC (effectively re-enabling it if
     * it was disabled).
     * @param ssrc
     */
    fun reset(ssrc: Long) {
        synchronized(buffers) { buffers.remove(ssrc) }
    }

    /**
     * Gets the `Buffer` instance responsible for buffering packets with
     * SSRC `ssrc`. Creates it if necessary, always returns non-null.
     * @param ssrc the SSRC for which go get a `Buffer`.
     * @return the `Buffer` instance responsible for buffering packets with
     * SSRC `ssrc`. Creates it if necessary, always returns non-null.
     */
    private fun getBuffer(ssrc: Long): Buffer {
        synchronized(buffers) {
            var buffer = buffers[ssrc]
            if (buffer == null) {
                buffer = Buffer(SIZE, ssrc)
                buffers[ssrc] = buffer
            }
            return buffer
        }
    }

    /**
     * Empties the `Buffer` for a specific SSRC, and returns its contents as an ordered (by
     * RTP sequence number) array.
     *
     * @param ssrc
     * the SSRC for which to empty the `Buffer`.
     * @return the contents of the `Buffer` for SSRC, or an empty array, if there is no
     * buffer for SSRC.
     */
    fun emptyBuffer(ssrc: Long): Array<RawPacket?> {
        var buffer: Buffer?
        synchronized(buffers) { buffer = buffers[ssrc] }
        return if (buffer != null) {
            buffer!!.empty()
        } else arrayOfNulls(0)
    }

    /**
     * Represents a buffer for `RawPacket`s.
     */
    private class Buffer(capacity: Int, ssrc: Long) {
        /**
         * The actual contents of this `Buffer`.
         */
        private val buffer: SortedSet<RawPacket?>

        /**
         * The maximum capacity of this `Buffer`.
         */
        private val capacity: Int

        /**
         * The SSRC that this `Buffer` is associated with.
         */
        private val ssrc: Long

        /**
         * Whether this buffer is disabled or not. If disabled, it will drop incoming packets, and
         * output 'null'.
         */
        var disabled = false

        /**
         * Constructs a `Buffer` with the given capacity and SSRC.
         */
        init {
            buffer = TreeSet(seqNumComparator)
            this.capacity = capacity
            this.ssrc = ssrc
        }

        /**
         * Inserts a specific `RawPacket` in this `Buffer`. If, after the insertion,
         * the number of elements stored in the buffer is more than `this.capacity`, removes
         * from the buffer and returns the 'first' packet in the buffer. Otherwise, return null.
         *
         * @param pkt
         * the packet to insert.
         * @return Either the 'first' packet in the buffer, or null, according to whether the buffer
         * capacity has been reached after the insertion of `pkt`.
         */
        fun insert(pkt: RawPacket?): RawPacket? {
            if (disabled) return null
            var ret: RawPacket? = null
            synchronized(buffer) {
                buffer.add(pkt)
                if (buffer.size > capacity) {
                    ret = buffer.first()
                    buffer.remove(ret)
                }
            }
            return ret
        }

        /**
         * Empties this `Buffer`, returning all its contents.
         *
         * @return the contents of this `Buffer`.
         */
        fun empty(): Array<RawPacket?> {
            synchronized(buffer) {
                val ret = buffer.toTypedArray()
                buffer.clear()
                return ret
            }
        }
    }

    companion object {
        /**
         * A `Comparator` implementation for RTP sequence numbers. Compares the sequence numbers
         * `a` and `b` of `pkt1` and `pkt2`, taking into account the wrap at
         * 2^16.
         *
         * IMPORTANT: This is a valid `Comparator` implementation only if used for subsets of [0,
         * 2^16) which don't span more than 2^15 elements.
         *
         * E.g. it works for: [0, 2^15-1] and ([50000, 2^16) u [0, 10000]) Doesn't work for: [0, 2^15]
         * and ([0, 2^15-1] u {2^16-1}) and [0, 2^16)
         */
        private val seqNumComparator: Comparator<in RawPacket?> = Comparator { pkt1, pkt2 ->
            val a = pkt1!!.sequenceNumber.toLong()
            val b = pkt2!!.sequenceNumber.toLong()
            if (a == b) 0 else if (a > b) {
                if (a - b < 32768) 1 else -1
            } else  // a < b
            {
                if (b - a < 32768) -1 else 1
            }
        }

        /**
         * The `ConfigurationService` used to load buffering configuration.
         */
        private val cfg = LibJitsi.configurationService

        /**
         * The payload type for VP8. TODO: make this configurable.
         */
        private const val VP8_PAYLOAD_TYPE = 100

        /**
         * The parameter name for the packet buffer size
         */
        private val PACKET_BUFFER_SIZE_PNAME = PacketBuffer::class.java.canonicalName + ".SIZE"

        /**
         * The size of the buffer for each SSRC.
         */
        private val SIZE = cfg.getInt(PACKET_BUFFER_SIZE_PNAME, 300)
    }
}