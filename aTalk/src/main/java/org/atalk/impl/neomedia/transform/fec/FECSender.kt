/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.fec

import net.sf.fmj.media.rtp.RTPHeader
import org.atalk.impl.neomedia.transform.PacketTransformer
import org.atalk.service.neomedia.RawPacket
import timber.log.Timber

/**
 * `PacketTransformer` which adds ulpfec packets. Works for a specific SSRC.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
internal class FECSender(
        /**
         * The single SSRC with which this `FECSender` works.
         */
        private val ssrc: Long,
        /**
         * An ulpfec packet will be generated for every `fecRate` media packets. If set to 0, no
         * ulpfec packets will be generated.
         */
        private var fecRate: Int,
        /**
         * The ulpfec payload type.
         */
        private var ulpfecPT: Byte,
) : PacketTransformer {
    /**
     * A counter of packets. Incremented for every media packet.
     */
    private var counter = 0

    /**
     * Number of ulpfec packets added.
     */
    private var nbFec = 0

    /**
     * A fec packet, which will be sent once enough (that is `fecRate`) media packets have
     * passed, and have been "added" to the fec packet. Should be always non-null.
     */
    private var fecPacket: FECPacket?

    /**
     * Creates a new `FECSender` instance.
     *
     * ssrc the SSRC with which this `FECSender` will work.
     * fecRate the rate at which to add ulpfec packets.
     * ulpfecPT the payload to use for ulpfec packets.
     */
    init {
        fecPacket = FECPacket(ssrc, ulpfecPT)
    }

    /**
     * {@inheritDoc}
     */
    override fun reverseTransform(pkts: Array<RawPacket?>): Array<RawPacket?> {
        return pkts
    }

    /**
     * {@inheritDoc}
     */
    @Synchronized
    override fun transform(pkts: Array<RawPacket?>): Array<RawPacket?> {
        var pkt: RawPacket? = null
        for (p in pkts) {
            if (p != null && p.version == RTPHeader.VERSION) {
                pkt = p
                break
            }
        }
        return pkt?.let { transformSingle(it, pkts) } ?: pkts
    }

    /**
     * Processes `pkt` and, if `fecRate` packets have passed, creates a fec packet
     * protecting the last `fecRate` media packets and adds this fec packet to `pkts`.
     *
     * @param pkt media packet to process.
     * @param pkts array to try to use for output.
     * @return an array that contains `pkt` (after processing) and possible an ulpfec packet
     * if one was added.
     */
    private fun transformSingle(pkt: RawPacket, pkts: Array<RawPacket?>?): Array<RawPacket?>? {
        // TODO due to the overhead introduced by adding any redundant data it
        // is usually a good idea to activate it only when the network
        // conditions require it.
        var packets = pkts
        counter++
        pkt.sequenceNumber = pkt.sequenceNumber + nbFec
        if (fecRate != 0) fecPacket!!.addMedia(pkt)
        if (fecRate != 0 && counter % fecRate == 0) {
            fecPacket!!.finish()
            var found = false
            for (i in packets!!.indices) {
                if (packets[i] == null) {
                    found = true
                    packets[i] = fecPacket
                    break
                }
            }
            if (!found) {
                val pkts2 = arrayOfNulls<RawPacket>(packets.size + 1)
                System.arraycopy(packets, 0, pkts2, 0, packets.size)
                pkts2[packets.size] = fecPacket
                packets = pkts2
            }
            fecPacket = FECPacket(ssrc, ulpfecPT)
            nbFec++
        }
        return packets
    }

    /**
     * {@inheritDoc}
     */
    override fun close() {
        Timber.i("Closing FEC-Sender for ssrc: %d. Added %d ulpfec packets.", ssrc, nbFec)
    }

    /**
     * Sets the ulpfec payload type.
     *
     * @param ulpfecPT the payload type.
     */
    fun setUlpfecPT(ulpfecPT: Byte) {
        this.ulpfecPT = ulpfecPT
        if (fecPacket != null) fecPacket!!.payloadType = ulpfecPT
    }

    /**
     * Updates the `fecRate` property. Re-allocates buffers, if needed.
     *
     * @param newFecRate the new rate to set.
     */
    fun setFecRate(newFecRate: Int) {
        if (fecRate != newFecRate) {
            fecPacket = FECPacket(ssrc, ulpfecPT) // reset it
            fecRate = newFecRate
            counter = 0
        }
    }

    /**
     * A `RawPacket` extension which represents an ulpfec packet. Allows
     * for a media packet to be protected to be added via the `addMedia()`
     * method.
     *
     * The format of this packet (see RFC3350 and RFC5109) is as follows:
     *
     * 12 byte RTP header (no CSRC or extensions):
     * 0                   1                   2                   3
     * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |V=2|P|X|  CC   |M|     PT      |       sequence number         |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                           timestamp                           |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |           synchronization source (SSRC) identifier            |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *
     * 10 byte FEC Header:
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |E|L|P|X|  CC   |M| PT recovery |            SN base            |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                          TS recovery                          |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |        length recovery        |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *
     * 4 byte FEC Level 0 Header (the short mask is always used):
     * 0                   1                   2                   3
     * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |       Protection Length       |             mask              |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *
     * Followed by 'Protection Length' bytes of 'FEC Level 0 Payload'.
     */
    private class FECPacket(
            ssrc: Long,
            /**
             * The payload type for this packet.
             */
            override var payloadType: Byte,
    ) : RawPacket(ByteArray(FECTransformEngine.INITIAL_BUFFER_SIZE), 0,
            FECTransformEngine.INITIAL_BUFFER_SIZE) {

        /**
         * SN base. The sequence number of the first media packet added.
         */
        var base = -1

        /**
         * Number of media packets added.
         */
        var numPackets = 0

        /**
         * The biggest payload (in the sense of RFC5109) of the media packets added.
         */
        var protectionLength = -1

        /**
         * The sequence of the last media packet added.
         */
        var lastAddedSeq = -1

        /**
         * This `RawPacket`'s buffer.
         */
        private var buf: ByteArray

        /**
         * The SSRC of this packet.
         */
        private val ssrc: Long

        /**
         * The RTP timestamp of the last added media packet.
         */
        private var lastAddedTS: Long = -1

        /**
         * Creates a new instance, initialized with a buffer obtained using `new`.
         */
        init {
            buf = buffer
            this.ssrc = ssrc
            super.payloadType = payloadType
        }

        /**
         * Adds a media packet to be protected by this `FECPacket`.
         *
         * @param media the media packet to add.
         */
        fun addMedia(media: RawPacket) {
            val mediaBuf = media.buffer
            val mediaOff = media.offset
            // payload length in the sense of RFC5109
            val mediaPayloadLen = media.length - 12

            // make sure that the buffer is big enough
            if (buf.size < mediaPayloadLen + RTP_HDR_LEN + FEC_HDR_LEN) {
                val newBuff = ByteArray(mediaPayloadLen + RTP_HDR_LEN + FEC_HDR_LEN)
                System.arraycopy(buf, 0, newBuff, 0, buf.size)
                for (i in buf.size until newBuff.size) newBuff[i] = 0.toByte()
                buf = newBuff
                buffer = buf
            }
            if (base == -1) {
                // first packet, make a copy and not XOR
                base = media.sequenceNumber

                // 8 bytes from media's RTP header --> the FEC Header
                System.arraycopy(mediaBuf, mediaOff, buf, RTP_HDR_LEN, 8)
                // set the 'length recovery' field
                buf[RTP_HDR_LEN + 8] = (mediaPayloadLen shr 8 and 0xff).toByte()
                buf[RTP_HDR_LEN + 9] = (mediaPayloadLen and 0xff).toByte()

                // copy the payload
                System.arraycopy(mediaBuf, mediaOff + RTP_HDR_LEN, buf, RTP_HDR_LEN + FEC_HDR_LEN,
                        mediaPayloadLen)
            } else {
                // not the first packet, do XOR

                // 8 bytes from media's RTP header --> the FEC Header
                for (i in 0..7) buf[RTP_HDR_LEN + i] = (buf[RTP_HDR_LEN + i].toInt() xor mediaBuf[mediaOff + i].toInt()).toByte()

                // 'length recovery'
                buf[RTP_HDR_LEN + 8] = (buf[RTP_HDR_LEN + 8].toInt() xor (mediaPayloadLen shr 8 and 0xff).toByte().toInt()).toByte()
                buf[RTP_HDR_LEN + 9] = (buf[RTP_HDR_LEN + 9].toInt() xor (mediaPayloadLen and 0xff).toByte().toInt()).toByte()

                // payload
                for (i in 0 until mediaPayloadLen) {
                    buf[RTP_HDR_LEN + FEC_HDR_LEN + i] = (buf[RTP_HDR_LEN + FEC_HDR_LEN + i].toInt() xor mediaBuf[mediaOff + RTP_HDR_LEN + i].toInt()).toByte()
                }
            }
            lastAddedSeq = media.sequenceNumber
            lastAddedTS = media.timestamp
            if (mediaPayloadLen > protectionLength) protectionLength = mediaPayloadLen
            numPackets++
        }

        /**
         * Fill in the required header fields and prepare this packet to be sent.
         *
         * @return the finished packet.
         */
        fun finish(): RawPacket {
            // RTP header fields
            buf[0] = 0x80.toByte() // no Padding, no Extension, no CSRCs
            super.payloadType = payloadType
            sequenceNumber = lastAddedSeq + 1
            // setSSRC(ssrc.toInt())
            timestamp = lastAddedTS // TODO: check 5109 -- which TS should be used?

            // FEC Header
            buf[RTP_HDR_LEN + 2] = (base shr 8 and 0xff).toByte()
            buf[RTP_HDR_LEN + 3] = (base and 0xff).toByte()

            // FEC Level 0 header
            buf[RTP_HDR_LEN + 10] = (protectionLength shr 8 and 0xff).toByte()
            buf[RTP_HDR_LEN + 11] = (protectionLength and 0xff).toByte()

            // assume all packets from base to lastAddedSeq were added
            val mask = (1 shl numPackets) - 1 shl 16 - numPackets
            buf[RTP_HDR_LEN + 12] = (mask shr 8 and 0xff).toByte()
            buf[RTP_HDR_LEN + 13] = (mask and 0xff).toByte()
            length = RTP_HDR_LEN + FEC_HDR_LEN + protectionLength
            return this
        }

        companion object {
            /**
             * Length of the RTP header of this packet.
             */
            private const val RTP_HDR_LEN = 12

            /**
             * Length of the additional headers added to this packet (in bytes): 10 bytes FEC Header + 4
             * bytes FEC Level 0 Header (short mask)
             */
            private const val FEC_HDR_LEN = 14
        }
    }
}