/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtcp

import net.sf.fmj.media.rtp.RTCPCompoundPacket
import org.atalk.service.neomedia.RawPacket
import org.atalk.util.ByteArrayBuffer
import org.atalk.util.RTCPUtils.getReportCount
import java.util.*

/**
 * A class which represents an RTCP Generic NACK feedback message, as defined
 * in RFC4585 Section 6.2.1.
 *
 * The RTCP packet structure is:
 *
 * <pre>`0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P| FMT=1   |   PT=205      |             length            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                  SSRC of packet sender                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                  SSRC of media source                         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           FCI                                 |
 * |                          [...]                                |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * The Feedback Control Information (FCI) field consists of one or more
 * 32-bit words, each with the following structure:
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |             PID               |             BLP               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
`</pre> *
 *
 * @author Boris Grozev
 */
class NACKPacket : RTCPFBPacket {
    /**
     * The set of sequence numbers described by this packet.
     */
    private var lostPackets: Collection<Int>? = null

    /**
     * Initializes a new `NACKPacket` instance.
     *
     * @param base
     */
    constructor(base: RTCPCompoundPacket?) : super(base)

    /**
     * Initializes a new `NACKPacket` instance with specific "packet sender SSRC" and
     * "media source SSRC" values and which describes a specific set of sequence numbers.
     *
     * @param senderSSRC the value to use for the "packet sender SSRC" field.
     * @param sourceSSRC the value to use for the "media source SSRC" field.
     * @param lostPackets the set of RTP sequence numbers which this NACK packet is to describe.
     *
     *
     * Note that this implementation is not optimized and might not always use the minimal
     * possible number of bytes to describe a given set of packets. Specifically, it does
     * take into account that sequence numbers wrap at 2^16 and fails to pack numbers close
     * to 2^16 with those close to 0.
     */
    constructor(senderSSRC: Long, sourceSSRC: Long, lostPackets: Collection<Int>) : super(FMT, RTPFB, senderSSRC, sourceSSRC) {
        val sorted = LinkedList(lostPackets)
        sorted.sort()
        val nackList = LinkedList<ByteArray>()
        var currentPid = -1
        var currentNack: ByteArray? = null
        for (seq in sorted) {
            if (currentPid == -1 || currentPid + 16 <= seq) {
                currentPid = seq
                currentNack = ByteArray(4)
                currentNack[0] = (seq and 0xff00 shr 8).toByte()
                currentNack[1] = (seq and 0x00ff).toByte()
                currentNack[2] = 0
                currentNack[3] = 0
                nackList.add(currentNack)
                continue
            }
            // Add seq to the current fci
            val diff = seq - currentPid
            if (diff <= 8) currentNack!![3] = (currentNack[3].toInt() or (1 shl diff - 1).toByte().toInt()).toByte() else currentNack!![2] = (currentNack[2].toInt() or (1 shl diff - 8 - 1).toByte().toInt()).toByte()
        }
        // Set the fci field, which is used when assembling
        fci = ByteArray(nackList.size * 4)
        for (i in nackList.indices) {
            System.arraycopy(nackList[i], 0, fci, i * 4, 4)
        }
        this.lostPackets = sorted
    }

    /**
     * Gets the set of sequence numbers reported lost in this NACK packet.
     *
     * @return
     */
    @Synchronized
    fun getLostPackets(): Collection<Int>? {
        if (lostPackets == null) {
            // parse this.fci as containing NACK entries and initialize this.lostPackets
            lostPackets = getLostPacketsFci(RawPacket(fci, 0, fci.size))
        }
        return lostPackets
    }

    override fun toString(): String {
        return ("RTCP NACK packet; packet sender: " + senderSSRC
                + "; media sources: " + sourceSSRC
                + "; NACK entries: " + (if (fci == null) "none" else fci.size / 4)
                + "; lost packets: "
                + if (lostPackets == null) "none" else lostPackets!!.toTypedArray().contentToString())
    }

    companion object {
        /**
         * Gets a boolean indicating whether or not the RTCP packet specified in the
         * [ByteArrayBuffer] that is passed as an argument is a NACK packet or not.
         *
         * @param baf the [ByteArrayBuffer]
         * @return true if the byte array buffer holds a NACK packet, otherwise false.
         */
        fun isNACKPacket(baf: ByteArrayBuffer?): Boolean {
            val rc = getReportCount(baf)
            return rc == FMT && isRTPFBPacket(baf)
        }

        /**
         * @param baf the NACK packet.
         * @return the set of sequence numbers reported lost in a NACK packet
         * represented by a [ByteArrayBuffer].
         */
        fun getLostPackets(baf: ByteArrayBuffer): MutableCollection<Int> {
            return getLostPacketsFci(getFCI(baf))
        }

        /**
         * @param fciBuffer the [ByteArrayBuffer] which represents the FCI field of a NACK packet.
         * @return the set of sequence numbers reported lost in the FCI field of a
         * NACK packet represented by a [ByteArrayBuffer].
         */
        fun getLostPacketsFci(fciBuffer: ByteArrayBuffer?): MutableCollection<Int> {
            val lostPackets = LinkedList<Int>()
            if (fciBuffer == null) {
                return lostPackets
            }
            val fci = fciBuffer.buffer
            val off = fciBuffer.offset
            val len = fciBuffer.length
            for (i in 0 until len / 4) {
                val pid = 0xFF and fci[off + i * 4 + 0].toInt() shl 8 or (0xFF and fci[off + i * 4 + 1].toInt())
                lostPackets.add(pid)

                // First byte of the BLP
                for (j in 0..7) {
                    if (0 != fci[off + i * 4 + 2].toInt() and (1 shl j)) {
                        lostPackets.add((pid + 1 + 8 + j) % (1 shl 16))
                    }
                }

                // Second byte of the BLP
                for (j in 0..7) {
                    if (0 != fci[off + i * 4 + 3].toInt() and (1 shl j)) {
                        lostPackets.add((pid + 1 + j) % (1 shl 16))
                    }
                }
            }
            return lostPackets
        }

        /*
     * The value of the "fmt" field for a NACK packet.
     */
        const val FMT = 1
    }
}