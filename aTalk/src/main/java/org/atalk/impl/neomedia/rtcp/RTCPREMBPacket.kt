/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtcp

import net.sf.fmj.media.rtp.RTCPCompoundPacket
import org.atalk.impl.neomedia.RTCPFeedbackMessagePacket
import org.atalk.util.ByteArrayBuffer
import org.atalk.util.RTCPUtils.getReportCount
import java.io.DataOutputStream
import java.io.IOException
import java.util.*
import kotlin.math.pow

/**
 * Created by gp on 6/24/14.
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P| FMT=15  |   PT=206      |             length            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                  SSRC of packet sender                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                  SSRC of media source                         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  Unique identifier 'R' 'E' 'M' 'B'                            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  Num SSRC     | BR Exp    |  BR Mantissa                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |   SSRC feedback                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  ...                                                          |
 */
class RTCPREMBPacket : RTCPFBPacket {
    /**
     * The exponential scaling of the mantissa for the maximum total media bit rate value, ignoring
     * all packet overhead.
     */
    var exp = 0

    /**
     * The mantissa of the maximum total media bit rate (ignoring all packet overhead) that the
     * sender of the REMB estimates. The BR is the estimate of the traveled path for the SSRCs
     * reported in this message.
     */
    var mantissa = 0

    /**
     * one or more SSRC entries which this feedback message applies to.
     */
    var dest: LongArray? = null

    constructor(senderSSRC: Long, mediaSSRC: Long, exp: Int, mantissa: Int, dest: LongArray?) : super(FMT, PSFB, senderSSRC, mediaSSRC) {
        this.exp = exp
        this.mantissa = mantissa
        this.dest = dest
    }

    constructor(senderSSRC: Long, mediaSSRC: Long, bitrate: Long, dest: LongArray?) : super(FMT, PSFB, senderSSRC, mediaSSRC) {

        // 6 bit Exp
        // 18 bit mantissa
        exp = 0
        for (i in 0..63) {
            if (bitrate <= 0x3ffff shl i) {
                exp = i
                break
            }
        }

        /* type of bitrate is an unsigned int (32 bits) */
        mantissa = bitrate.toInt() shr exp
        this.dest = dest
    }

    constructor(base: RTCPCompoundPacket?) : super(base) {
        super.fmt = FMT
        super.type = PSFB
    }

    /*
        0                   1                   2                   3
        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |V=2|P| FMT=15  |   PT=206      |             length            |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                  SSRC of packet sender                        |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                  SSRC of media source                         |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |  Unique identifier 'R' 'E' 'M' 'B'                            |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |  Num SSRC     | BR Exp    |  BR Mantissa                      |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |   SSRC feedback                                               |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |  ...                                                          |

     */
    @Throws(IOException::class)
    override fun assemble(dataoutputstream: DataOutputStream) {
        val len = calcLength()
        val buf = ByteArray(len)
        var off = 0

        /*
         * version (V): (2 bits):   This field identifies the RTP version. The
         *     current version is 2.
         * padding (P) (1 bit):   If set, the padding bit indicates that the
         *     packet contains additional padding octets at the end that
         *     are not part of the control information but are included
         *     in the length field.  Always 0.
         * Feedback message type (FMT) (5 bits):  This field identifies the type
         *     of the FB message and is interpreted relative to the type
         *     (transport layer, payload- specific, or application layer
         *     feedback).  Always 15, application layer feedback message.
         */
        buf[off++] = 0x8F.toByte()

        /*
         * Payload type (PT) (8 bits):   This is the RTCP packet type that
         *     identifies the packet as being an RTCP FB message.
         *     Always PSFB (206), Payload-specific FB message.
         */
        buf[off++] = 0xCE.toByte()

        // Length (16 bits): The length of this packet in 32-bit words minus one, including the
        // header and any padding. This is in line with the definition of the length field used
        // in RTCP sender and receiver reports
        val rtcpPacketLength = len / 4 - 1
        buf[off++] = (rtcpPacketLength and 0xFF00 shr 8).toByte()
        buf[off++] = (rtcpPacketLength and 0x00FF).toByte()

        // SSRC of packet sender: 32 bits
        RTCPFeedbackMessagePacket.writeSSRC(senderSSRC, buf, off)
        off += 4

        // SSRC of media source (32 bits): Always 0;
        RTCPFeedbackMessagePacket.writeSSRC(0L, buf, off)
        off += 4

        // Unique identifier (32 bits): Always 'R' 'E' 'M' 'B' (4 ASCII characters).
        buf[off++] = 'R'.code.toByte()
        buf[off++] = 'E'.code.toByte()
        buf[off++] = 'M'.code.toByte()
        buf[off++] = 'B'.code.toByte()

        // Num SSRC (8 bits): Number of SSRCs in this message.
        buf[off++] = (if (dest != null && dest!!.isNotEmpty()) dest!!.size else 0).toByte()

        // BR Exp (6 bits): The exponential scaling of the mantissa for the
        // maximum total media bit rate value, ignoring all packet overhead.

        // BR Mantissa (18 bits): The mantissa of the maximum total media bit
        // rate (ignoring all packet overhead) that the sender of the REMB estimates.
        buf[off++] = (exp and 0x3f shl 2 or (mantissa and 0x30000 shr 16)).toByte()
        buf[off++] = (mantissa and 0xff00 shr 8).toByte()
        buf[off++] = (mantissa and 0xff).toByte()

        // SSRC feedback (32 bits) Consists of one or more SSRC entries which
        // this feedback message applies to.
        if (dest != null && dest!!.isNotEmpty()) {
            for (d in dest!!) {
                RTCPFeedbackMessagePacket.writeSSRC(d, buf, off)
                off += 4
            }
        }
        dataoutputstream.write(buf, 0, len)
    }

    override fun calcLength(): Int {
        var len = 20 // 20 bytes header + standard data
        if (dest != null) len += dest!!.size * 4
        return len
    }

    override fun toString(): String {
        return "\tRTCP REMB packet from sync source $senderSSRC" +
                "\n\t\tfor sync sources: ${Arrays.toString(dest)}" +
                "\n\t\tBR Exp: $exp" +
                "\n\t\tBR Mantissa: $mantissa"
    }

    /**
     * Gets the bitrate described in this packet in bits per second.
     *
     * @return the bitrate described in this packet in bits per second.
     */
    val bitrate: Long
        get() = (mantissa * 2.0.pow(exp.toDouble())).toLong()

    companion object {
        const val FMT = 15

        /**
         * Gets a boolean that indicates whether or not the packet specified in the
         * [ByteArrayBuffer] that is passed in the first argument is an RTCP REMB packet.
         *
         * @param baf the [ByteArrayBuffer] that holds the RTCP packet.
         * @return true if the packet specified in the [ByteArrayBuffer] that
         * is passed in the first argument is an RTCP REMB packet, otherwise false.
         */
        fun isREMBPacket(baf: ByteArrayBuffer?): Boolean {
            val rc = getReportCount(baf)
            return isPSFBPacket(baf) && rc == FMT
        }
    }
}