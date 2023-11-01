/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtcp

import net.sf.fmj.media.rtp.RTCPCompoundPacket
import net.sf.fmj.media.rtp.RTCPPacket
import org.atalk.service.neomedia.RawPacket
import org.atalk.util.ByteArrayBuffer
import org.atalk.util.RTCPUtils.getLength
import org.atalk.util.RTCPUtils.getPacketType
import org.atalk.util.RTPUtils.readUint32AsLong
import java.io.DataOutputStream
import java.io.IOException

/**
 * Created by gp on 6/27/14.
 *
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P|   FMT   |       PT      |          length               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                  SSRC of packet sender                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                  SSRC of media source                         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * :            Feedback Control Information (FCI)                 :
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
open class RTCPFBPacket : RTCPPacket {
    lateinit var fci: ByteArray

    /**
     * Feedback message type (FMT).
     */
    var fmt = 0
    /**
     * @return the `Sender SSRC` field of this `RTCP` feedback packet.
     */
    /**
     * SSRC of packet sender.
     */
    var senderSSRC = 0L
    /**
     * @return the `Source SSRC` field of this `RTCP` feedback packet.
     */
    /**
     * SSRC of media source.
     */
    var sourceSSRC = 0L

    constructor(fmt: Int, type: Int, senderSSRC: Long, sourceSSRC: Long) {
        super.type = type
        this.fmt = fmt
        this.senderSSRC = senderSSRC
        this.sourceSSRC = sourceSSRC
    }

    constructor(base: RTCPCompoundPacket?) : super(base)

    @Throws(IOException::class)
    override fun assemble(dataoutputstream: DataOutputStream) {
        dataoutputstream.writeByte((0x80 /* version */ or fmt).toByte().toInt())
        dataoutputstream.writeByte(type.toByte().toInt()) // packet type, 205 or 206

        // Full length in bytes, including padding.
        val len = calcLength()
        dataoutputstream.writeShort(len / 4 - 1)
        dataoutputstream.writeInt(senderSSRC.toInt())
        dataoutputstream.writeInt(sourceSSRC.toInt())
        dataoutputstream.write(fci)

        // Pad with zeros. Since the above fields fill in exactly 3 words, the number of padding
        // bytes will only depend on the length of the fci field.
        var i = fci.size
        while (i % 4 != 0) {

            // pad to a word.
            dataoutputstream.writeByte(0)
            i++
        }
    }

    override fun calcLength(): Int {
        // Length (16 bits): The length of this packet in 32-bit words minus one, including the
        // header and any padding.
        var len = 12 // header+ssrc+ssrc
        if (fci.isNotEmpty()) len += fci.size

        // Pad to a word.
        if (len % 4 != 0) {
            len += 4 - len % 4
        }
        return len
    }

    override fun toString(): String {
        return "\tRTCP FB packet from sync source $senderSSRC"
    }

    /**
     * @return a [RawPacket] representation of this [RTCPFBPacket].
     * @throws IOException
     */
    @Throws(IOException::class)
    fun toRawPacket(): RawPacket {
        return RTCPPacketParserEx.toRawPacket(this)
    }

    companion object {
        const val RTPFB = 205
        const val PSFB = 206

        /**
         * Gets a boolean that indicates whether or not the packet specified in the
         * [ByteArrayBuffer] that is passed in the first argument is an RTCP
         * RTPFB or PSFB packet.
         *
         * @param baf the [ByteArrayBuffer] that holds the RTCP packet.
         * @return true if the packet specified in the [ByteArrayBuffer] that is passed in the
         * first argument is an RTCP RTPFB or PSFB packet, otherwise false.
         */
        private fun isRTCPFBPacket(baf: ByteArrayBuffer?): Boolean {
            return isRTPFBPacket(baf) || isPSFBPacket(baf)
        }

        /**
         * Gets a boolean that indicates whether or not the packet specified in the
         * [ByteArrayBuffer] passed in as an argument is an RTP FB packet.
         *
         * @param baf the [ByteArrayBuffer] that holds the packet
         * @return true if the packet specified in the [ByteArrayBuffer]
         * passed in as an argument is an RTP FB packet, otherwise false.
         */
        fun isRTPFBPacket(baf: ByteArrayBuffer?): Boolean {
            val pt = getPacketType(baf)
            return pt == RTPFB
        }

        /**
         * Gets a boolean that indicates whether or not the packet specified in the
         * [ByteArrayBuffer] passed in as an argument is an RTP FB packet.
         *
         * @param baf the [ByteArrayBuffer] that holds the packet
         * @return true if the packet specified in the [ByteArrayBuffer]
         * passed in as an argument is an RTP FB packet, otherwise false.
         */
        fun isPSFBPacket(baf: ByteArrayBuffer?): Boolean {
            val pt = getPacketType(baf)
            return pt == PSFB
        }

        /**
         * Gets the SSRC of the media source of the packet specified in the
         * [ByteArrayBuffer] passed in as an argument.
         *
         * @param baf the [ByteArrayBuffer] that holds the packet
         * @return the SSRC of the media source of the packet specified in the
         * [ByteArrayBuffer] passed in as an argument, or -1 in case of an error.
         */
        fun getSourceSSRC(baf: ByteArrayBuffer?): Long {
            return when {
                baf == null || baf.isInvalid -> {
                    -1
                }
                else -> readUint32AsLong(baf.buffer, baf.offset + 8)
            }
        }

        /**
         * Gets the Feedback Control Information (FCI) field of an RTCP FB message.
         *
         * @param baf the [ByteArrayBuffer] that contains the RTCP message.
         * @return the Feedback Control Information (FCI) field of an RTCP FB message.
         */
        fun getFCI(baf: ByteArrayBuffer): ByteArrayBuffer? {
            if (!isRTCPFBPacket(baf)) {
                return null
            }
            val length = getLength(baf)
            return if (length < 0) {
                null
            } else RawPacket(baf.buffer, baf.offset + 12, length - 12)
        }
    }
}