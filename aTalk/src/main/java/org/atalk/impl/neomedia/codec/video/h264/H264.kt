package org.atalk.impl.neomedia.codec.video.h264

/**
 * Common utilities and constants for H264.
 */
object H264 {
    /**
     * The bytes to prefix any NAL unit to be output by this
     * `DePacketizer` and given to a H.264 decoder. Includes
     * start_code_prefix_one_3bytes. According to "B.1 Byte stream NAL unit
     * syntax and semantics" of "ITU-T Rec. H.264 Advanced video coding for
     * generic audiovisual services", zero_byte "shall be present" when "the
     * nal_unit_type within the nal_unit() is equal to 7 (sequence parameter
     * set) or 8 (picture parameter set)" or "the byte stream NAL unit syntax
     * structure contains the first NAL unit of an access unit in decoding
     * order".
     */
    val NAL_PREFIX = byteArrayOf(0, 0, 0, 1)

    /**
     * Constants used to detect H264 keyframes in rtp packet
     */
    const val kTypeMask: Byte = 0x1F // Nalu
    const val kIdr: Byte = 5
    const val kSei: Byte = 6
    const val kSps: Byte = 7
    const val kPps: Byte = 8
    const val kStapA: Byte = 24
    const val kFuA: Byte = 28 // Header sizes
    const val kNalHeaderSize = 1
    const val kFuAHeaderSize = 2
    const val kLengthFieldSize = 2
    const val kStapAHeaderSize = kNalHeaderSize + kLengthFieldSize
    const val kNalUSize = 2

    /**
     * Check if Single-Time Aggregation Packet (STAP-A) NAL unit is correctly formed.
     *
     * @param data            STAP-A payload
     * @param offset          Starting position of NAL unit
     * @param lengthRemaining Bytes left in STAP-A
     * @return True if STAP-A NAL Unit is correct
     */
    fun verifyStapANaluLengths(data: ByteArray, offset: Int, lengthRemaining: Int): Boolean {
        var offset = offset
        var lengthRemaining = lengthRemaining
        if (data.size < offset + lengthRemaining) {
            return false
        }
        while (lengthRemaining != 0) {
            if (lengthRemaining < kNalUSize) {
                return false
            }
            val naluSize = kNalUSize + getUint16(data, offset)
            offset += naluSize
            lengthRemaining -= naluSize
        }
        return true
    }

    fun getUint16(data: ByteArray, offset: Int): Int {
        return data[offset].toInt() and 0xff shl 8 or (data[offset + 1].toInt() and 0xff)
    }
}