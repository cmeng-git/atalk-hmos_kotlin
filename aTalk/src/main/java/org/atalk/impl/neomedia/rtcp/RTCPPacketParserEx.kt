/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtcp

import net.sf.fmj.media.rtp.RTCPCompoundPacket
import net.sf.fmj.media.rtp.RTCPPacket
import net.sf.fmj.media.rtp.RTCPPacketParser
import net.sf.fmj.media.rtp.util.BadFormatException
import net.sf.fmj.media.rtp.util.UDPPacket
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.service.neomedia.RawPacket
import org.atalk.service.neomedia.event.RTCPFeedbackMessageEvent
import org.atalk.service.neomedia.rtp.RTCPExtendedReport
import org.atalk.util.RTPUtils.readInt
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Extends [RTCPPacketParser] to allow the parsing of additional RTCP packet types such as
 * REMB, NACK and XR.
 *
 * @author George Politis
 * @author Boris Grozev
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class RTCPPacketParserEx : RTCPPacketParser() {
    @Throws(BadFormatException::class)
    fun parse(data: ByteArray?, offset: Int, length: Int): RTCPPacket {
        val udp = UDPPacket()
        udp.data = data
        udp.length = length
        udp.offset = offset
        udp.received = false
        return parse(udp)
    }

    /**
     * @param base
     * @param firstbyte the first byte of the RTCP packet
     * @param type the packet type of the RTCP packet
     * @param length the length in bytes of the RTCP packet, including all
     * headers and excluding padding.
     * @param ins` the binary representation from which the new
     * instance is to be initialized, excluding the first 4 bytes.
     * @return
     * @throws BadFormatException
     * @throws IOException
     */
    @Throws(BadFormatException::class, IOException::class)
    override fun parse(base: RTCPCompoundPacket, firstbyte: Int, type: Int,
            length: Int, ins: DataInputStream): RTCPPacket? {
        return if (type == RTCPFBPacket.RTPFB || type == RTCPFBPacket.PSFB) {
/*
	0                   1                   2                   3
    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |V=2|P|   FMT   |       PT      |          length               |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                  SSRC of packet sender                        |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                  SSRC of media source                         |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   :            Feedback Control Information (FCI)                 :
   :                                                               :
*/
            val senderSSRC = ins.readInt().toLong() and 0xffffffffL
            val sourceSSRC = ins.readInt().toLong() and 0xffffffffL

            if (type == RTCPFBPacket.RTPFB) {
                parseRTCPFBPacket(base, firstbyte, RTCPFBPacket.RTPFB, length, ins, senderSSRC, sourceSSRC)
            } else {
                when (firstbyte and 0x1f) {
                    RTCPREMBPacket.FMT -> {
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
                        val remb = RTCPREMBPacket(base)
                        remb.senderSSRC = senderSSRC
                        remb.sourceSSRC = sourceSSRC

                        // Unique identifier 'R' 'E' 'M' 'B'
                        ins.readInt()
                        val destlen = ins.readUnsignedByte()
                        val buf = ByteArray(3)
                        ins.read(buf)
                        remb.exp = buf[0].toInt() and 0xFC shr 2
                        remb.mantissa = buf[0].toInt() and 0x3 shl 16 and 0xFF0000 or (buf[1].toInt() shl 8 and 0x00FF00
                                ) or (buf[2].toInt() and 0x0000FF)
                        remb.dest = LongArray(destlen)
                        var i = 0
                        while (i < remb.dest!!.size) {
                            remb.dest!![i] = ins.readInt().toLong() and 0xffffffffL
                            i++
                        }
                        remb
                    }
                    else -> parseRTCPFBPacket(base, firstbyte, RTCPFBPacket.PSFB, length, ins,
                            senderSSRC, sourceSSRC)
                }
            }
        } else if (type == RTCPExtendedReport.XR) {
            RTCPExtendedReport(firstbyte, type, length, ins)
        } else {
            null
        }
    }

    /**
     * Creates a new [RTCPFBPacket] instance.
     *
     * @param base
     * @param firstbyte the first byte of the RTCP packet.
     * @param type the packet type.
     * @param length the length in bytes.
     * @param ins
     * @param senderSSRC
     * @param sourceSSRC
     * @return
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun parseRTCPFBPacket(base: RTCPCompoundPacket, firstbyte: Int, type: Int,
            length: Int, ins: DataInputStream, senderSSRC: Long, sourceSSRC: Long): RTCPFBPacket {
        val fb: RTCPFBPacket
        val fmt = firstbyte and 0x1f
        fb = if (type == RTCPFBPacket.RTPFB && fmt == NACKPacket.FMT) {
            NACKPacket(base)
        } else if (type == RTCPFBPacket.RTPFB && fmt == RTCPTCCPacket.FMT) {
            RTCPTCCPacket(base)
        } else {
            RTCPFBPacket(base)
        }
        fb.fmt = fmt
        fb.type = type
        fb.senderSSRC = senderSSRC
        fb.sourceSSRC = sourceSSRC
        val fcilen = length - 12 // header + ssrc + ssrc = 14
        if (fcilen != 0) {
            fb.fci = ByteArray(fcilen)
            ins.read(fb.fci)
        }
        if (TimberLog.isTraceEnable) {
            val ptStr: String // Payload type (PT)
            var fmtStr: String? = null // Feedback message type (FMT)
            var detailStr: String? = null
            when (fb.type) {
                RTCPFBPacket.PSFB -> {
                    ptStr = "PSFB"
                    when (fb.fmt) {
                        RTCPFeedbackMessageEvent.FMT_FIR -> fmtStr = "FIR"
                        RTCPFeedbackMessageEvent.FMT_PLI -> fmtStr = "PLI"
                        RTCPREMBPacket.FMT -> fmtStr = "REMB"
                    }
                }
                RTCPFBPacket.RTPFB -> {
                    ptStr = "RTPFB"
                    when (fb.fmt) {
                        1 -> fmtStr = "Generic NACK"
                        3 -> fmtStr = "TMMBR"
                        4 -> {
                            fmtStr = "TMMBN"

                            // Log the TMMBN FCI entries.
                            val tmmbnFciEntryStr = StringBuilder()
                            var i = 0
                            val end = fcilen - 8
                            while (i < end) {
                                val ssrc = readInt(fb.fci!!, i)
                                val b4 = fb.fci!![i + 4]
                                val mxTbrExp /* 6 bits */ = b4.toInt() and 0xFC ushr 2
                                val b6 = fb.fci!![i + 6]
                                val mxTbrMantissa /* 17 bits */ = (b4.toInt() and 0x1 shl 16 and 0xFF0000
                                        or (fb.fci!![i + 5].toInt() shl 8 and 0x00FF00)
                                        or (b6.toInt() and 0x0000FF))
                                val measuredOverhead /* 9 bits */ = (b6.toInt() and 0x1 shl 8 and 0xFF00
                                        or (fb.fci!![i + 7].toInt() and 0x00FF))
                                tmmbnFciEntryStr.append(", SSRC 0x")
                                tmmbnFciEntryStr.append(java.lang.Long.toHexString(ssrc.toLong() and 0xFFFFFFFFL))
                                tmmbnFciEntryStr.append(", MxTBR Exp ")
                                tmmbnFciEntryStr.append(mxTbrExp)
                                tmmbnFciEntryStr.append(", MxTBR Mantissa ")
                                tmmbnFciEntryStr.append(mxTbrMantissa)
                                tmmbnFciEntryStr.append(", Measured Overhead ")
                                tmmbnFciEntryStr.append(measuredOverhead)
                                i += 8
                            }
                            detailStr = tmmbnFciEntryStr.toString()
                        }
                    }
                }
                else -> ptStr = fb.type.toString()
            }
            if (fmtStr == null) fmtStr = fb.fmt.toString()
            if (detailStr == null) detailStr = ""
            Timber.log(TimberLog.FINER, "SSRC of packet sender: 0x8x (%s), SSRC of media source: 0x8x (%s), Payload type (PT): %s, Feedback message type (FMT): %s%s",
                    senderSSRC, senderSSRC, sourceSSRC, sourceSSRC, ptStr, fmtStr, detailStr)
        }
        return fb
    }

    companion object {
        /**
         * Initializes a new `RawPacket` instance from a specific `RTCPPacket`.
         *
         * @param rtcp the `RTCPPacket` to represent as a `RawPacket`
         * @return a new `RawPacket` instance which represents the specified `rtcp`
         * @throws IOException if an input/output error occurs during the serialization/writing of the binary
         * representation of the specified `rtcp`
         */
        @Throws(IOException::class)
        fun toRawPacket(rtcp: RTCPPacket): RawPacket {
            val byteArrayOutputStream = ByteArrayOutputStream()
            val dataOutputStream = DataOutputStream(byteArrayOutputStream)
            rtcp.assemble(dataOutputStream)
            val buf = byteArrayOutputStream.toByteArray()
            return RawPacket(buf, 0, buf.size)
        }
    }
}