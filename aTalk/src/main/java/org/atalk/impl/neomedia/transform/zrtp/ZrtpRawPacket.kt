/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.zrtp

import gnu.java.zrtp.packets.ZrtpPacketBase
import gnu.java.zrtp.utils.ZrtpCrc32
import org.atalk.service.neomedia.RawPacket

/**
 * ZRTP packet representation.
 *
 * This class extends the RawPacket class and adds some methods required by the ZRTP transformer.
 *
 * @author Werner Dittmann <Werner.Dittmann></Werner.Dittmann>@t-online.de>
 */
class ZrtpRawPacket : RawPacket {
    /**
     * Construct an input ZrtpRawPacket using a received RTP raw packet.
     *
     * @param pkt a raw RTP packet as received
     */
    constructor(pkt: RawPacket?) : super(pkt!!.buffer, pkt.offset, pkt.length) {}

    /**
     * Construct an output ZrtpRawPacket using specified value.
     *
     * Initialize this packet and set the ZRTP magic value to mark it as a ZRTP packet.
     *
     * @param buf Byte array holding the content of this Packet
     * @param off Start offset of packet content inside buffer
     * @param len Length of the packet's data
     */
    constructor(buf: ByteArray, off: Int, len: Int) : super(buf, off, len) {
        writeByte(0, 0x10.toByte())
        writeByte(1, 0.toByte())

        var at = 4
        writeByte(at++, ZRTP_MAGIC[0])
        writeByte(at++, ZRTP_MAGIC[1])
        writeByte(at++, ZRTP_MAGIC[2])
        writeByte(at, ZRTP_MAGIC[3])
    }

    /**
     * Check if it could be a ZRTP packet.
     *
     * The method checks if the first byte of the received data matches the defined ZRTP pattern 0x10
     *
     * @return true if could be a ZRTP packet, false otherwise.
     */
     private val isZrtpPacket: Boolean
        get() = isZrtpData(this)

    /**
     * Check if it is really a ZRTP packet.
     *
     * The method checks if the packet contains the ZRTP magic number.
     *
     * @return true if packet contains the magic number, false otherwise.
     */
    fun hasMagic(): Boolean {
        return readByte(4) == ZRTP_MAGIC[0] && readByte(5) == ZRTP_MAGIC[1] && readByte(6) == ZRTP_MAGIC[2] && readByte(7) == ZRTP_MAGIC[3]
    }

    /**
     * Set the sequence number in this packet.
     *
     * @param seq sequence number
     */
    fun setSeqNum(seq: Short) {
        var at = 2
        writeByte(at++, (seq.toInt() shr 8).toByte())
        writeByte(at, seq.toByte())
    }

    /**
     * Check if the CRC of this packet is ok.
     *
     * @return true if the CRC is valid, false otherwise
     */
    fun checkCrc(): Boolean {
        val crc = readInt(length - ZrtpPacketBase.CRC_SIZE)
        return ZrtpCrc32.zrtpCheckCksum(buffer, offset, length - ZrtpPacketBase.CRC_SIZE, crc)
    }

    /**
     * Set ZRTP CRC in this packet
     */
    fun setCrc() {
        var crc = ZrtpCrc32.zrtpGenerateCksum(buffer, offset, length - ZrtpPacketBase.CRC_SIZE)
        // convert and store CRC in crc field of ZRTP packet.
        crc = ZrtpCrc32.zrtpEndCksum(crc)
        writeInt(length - ZrtpPacketBase.CRC_SIZE, crc)
    }

    companion object {
        /**
         * Each ZRTP packet contains this magic number/cookie.
         */
        val ZRTP_MAGIC = byteArrayOf(0x5a, 0x52, 0x54, 0x50)

        /**
         * Checks whether extension bit is set and if so is the extension header an zrtp one.
         *
         * @param pkt the packet to check.
         * @return `true` if data is zrtp packet.
         */
        fun isZrtpData(pkt: RawPacket?): Boolean {
            return pkt!!.extensionBit && pkt.headerExtensionType == 0x505a
        }
    }
}