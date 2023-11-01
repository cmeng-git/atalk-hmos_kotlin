/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.util

/**
 * RTP-related static utility methods.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
object RTPUtils {
    /**
     * Hex characters for converting bytes to readable hex strings
     */
    private val HEXES = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7', '8',
            '9', 'A', 'B', 'C', 'D', 'E', 'F'
    )

    /**
     * Returns the delta between two RTP sequence numbers, taking into account
     * rollover.  This will return the 'shortest' delta between the two
     * sequence numbers in the form of the number you'd add to b to get a. e.g.:
     * getSequenceNumberDelta(1, 10) -> -9 (10 + -9 = 1)
     * getSequenceNumberDelta(1, 65530) -> 7 (65530 + 7 = 1)
     *
     * @return the delta between two RTP sequence numbers (modulo 2^16).
     */
    fun getSequenceNumberDelta(a: Int, b: Int): Int {
        var diff = a - b
        if (diff < -(1 shl 15)) {
            diff += 1 shl 16
        } else if (diff > 1 shl 15) {
            diff -= 1 shl 16
        }
        return diff
    }

    /**
     * Returns whether or not seqNumOne is 'older' than seqNumTwo, taking rollover into account
     *
     * @param seqNumOne
     * @param seqNumTwo
     * @return true if seqNumOne is 'older' than seqNumTwo
     */
    fun isOlderSequenceNumberThan(seqNumOne: Int, seqNumTwo: Int): Boolean {
        return getSequenceNumberDelta(seqNumOne, seqNumTwo) < 0
    }

    /**
     * Returns result of the subtraction of one RTP sequence number from another (modulo 2^16).
     *
     * @return result of the subtraction of one RTP sequence number from another (modulo 2^16).
     */
    fun subtractNumber(a: Int, b: Int): Int {
        return as16Bits(a - b)
    }

    /**
     * Apply a delta to a given sequence number and return the result (taking rollover into account)
     *
     * @param startingSequenceNumber the starting sequence number
     * @param delta the delta to be applied
     * @return the sequence number result from doing startingSequenceNumber + delta
     */
    fun applySequenceNumberDelta(startingSequenceNumber: Int, delta: Int): Int {
        return startingSequenceNumber + delta and 0xFFFF
    }

    /**
     * Set an integer at specified offset in network order.
     *
     * @param ofs Offset into the buffer
     * @param data The integer to store in the packet
     */
    fun writeInt(buf: ByteArray?, ofs: Int, data: Int): Int {
        var offset = ofs
        if (buf == null || buf.size < offset + 4) {
            return -1
        }
        buf[offset++] = (data shr 24).toByte()
        buf[offset++] = (data shr 16).toByte()
        buf[offset++] = (data shr 8).toByte()
        buf[offset] = data.toByte()
        return 4
    }

    /**
     * Writes the least significant 24 bits from the given integer into the given byte array at the given offset.
     *
     * @param buf the buffer into which to write.
     * @param ofs the offset at which to write.
     * @param data the integer to write.
     * @return 3
     */
    fun writeUint24(buf: ByteArray?, ofs: Int, data: Int): Int {
        var offset = ofs
        if (buf == null || buf.size < offset + 3) {
            return -1
        }
        buf[offset++] = (data shr 16).toByte()
        buf[offset++] = (data shr 8).toByte()
        buf[offset] = data.toByte()
        return 3
    }

    /**
     * Set an integer at specified offset in network order.
     *
     * @param ofs Offset into the buffer
     * @param data The integer to store in the packet
     */
    fun writeShort(buf: ByteArray, ofs: Int, data: Short): Int {
        var offset = ofs
        buf[offset++] = (data.toInt() shr 8).toByte()
        buf[offset] = data.toByte()
        return 2
    }

    /**
     * Read a integer from a buffer at a specified offset.
     *
     * @param buffer the buffer.
     * @param ofs start offset of the integer to be read.
     */
    fun readInt(buffer: ByteArray, ofs: Int): Int {
        var offset = ofs
        return (buffer[offset++].toInt() and 0xFF shl 24
                or (buffer[offset++].toInt() and 0xFF shl 16)
                or (buffer[offset++].toInt() and 0xFF shl 8)
                or (buffer[offset].toInt() and 0xFF))
    }

    /**
     * Reads a 32-bit unsigned integer from the given buffer at the given
     * offset and returns its Long representation.
     *
     * @param buffer the buffer.
     * @param offset start offset of the integer to be read.
     */
    fun readUint32AsLong(buffer: ByteArray, offset: Int): Long {
        return readInt(buffer, offset).toLong() and 0xFFFFFFFFL
    }

    /**
     * Read an unsigned short at a specified offset as an int.
     *
     * @param buffer the buffer from which to read.
     * @param offset start offset of the unsigned short
     * @return the int value of the unsigned short at offset
     */
    fun readUint16AsInt(buffer: ByteArray, offset: Int): Int {
        val b1 = 0xFF and buffer[offset].toInt()
        val b2 = 0xFF and buffer[offset + 1].toInt()
        return b1 shl 8 or b2
    }

    /**
     * Read a signed short at a specified offset as an int.
     *
     * @param buffer the buffer from which to read.
     * @param offset start offset of the unsigned short
     * @return the int value of the unsigned short at offset
     */
    fun readInt16AsInt(buffer: ByteArray, offset: Int): Int {
        var ret = (0xFF and buffer[offset].toInt() shl 8
                or (0xFF and buffer[offset + 1].toInt()))
        if (ret and 0x8000 != 0) {
            ret = ret or -0x10000
        }
        return ret
    }

    /**
     * Read an unsigned short at specified offset as a int
     *
     * @param buffer byte array buffer
     * @param offset start offset of the unsigned short
     * @return the int value of the unsigned short at offset
     */
    fun readUint24AsInt(buffer: ByteArray, offset: Int): Int {
        val b1 = 0xFF and buffer[offset].toInt()
        val b2 = 0xFF and buffer[offset + 1].toInt()
        val b3 = 0xFF and buffer[offset + 2].toInt()
        return b1 shl 16 or (b2 shl 8) or b3
    }

    /**
     * Returns the given integer masked to 16 bits
     *
     * @param value the integer to mask
     * @return the value, masked to only keep the lower 16 bits
     */
    fun as16Bits(value: Int): Int {
        return value and 0xFFFF
    }

    /**
     * Returns the given integer masked to 32 bits
     *
     * @param value the integer to mask
     * @return the value, masked to only keep the lower 32 bits
     */
    fun as32Bits(value: Long): Long {
        return value and 0xFFFFFFFFL
    }

    /**
     * A [Comparator] implementation for unsigned 16-bit [Integer]s.
     * Compares `a` and `b` inside the [0, 2^16] ring;
     * `a` is considered smaller than `b` if it takes a smaller
     * number to reach from `a` to `b` than the other way round.
     *
     * IMPORTANT: This is a valid [Comparator] implementation only when
     * used for subsets of [0, 2^16) which don't span more than 2^15 elements.
     *
     * E.g. it works for: [0, 2^15-1] and ([50000, 2^16) u [0, 10000])
     * Doesn't work for: [0, 2^15] and ([0, 2^15-1] u {2^16-1}) and [0, 2^16)
     */
    val sequenceNumberComparator = Comparator<Int> { a, b ->
        if (a == b) {
            0
        } else if (a > b) {
            if (a - b < 0x10000) 1 else -1
        } else  //a < b
        {
            if (b - a < 0x10000) -1 else 1
        }
    }

    /**
     * Returns the difference between two RTP timestamps.
     *
     * @return the difference between two RTP timestamps.
     */
    fun rtpTimestampDiff(a: Long, b: Long): Long {
        var diff = a - b
        if (diff < -(1L shl 31)) {
            diff += 1L shl 32
        } else if (diff > 1L shl 31) {
            diff -= 1L shl 32
        }
        return diff
    }

    /**
     * Returns whether or not the first given timestamp is newer than the second
     *
     * @param a
     * @param b
     * @return true if a is newer than b, false otherwise
     */
    fun isNewerTimestampThan(a: Long, b: Long): Boolean {
        return rtpTimestampDiff(a, b) > 0
    }

    /**
     * Return a string containing the hex string version of the given byte
     *
     * @param b byte value
     * @return a string containing the hex string version of the given byte
     */
    private fun toHexString(b: Byte): String {
        val hexStringBuilder = StringBuilder(2)
        hexStringBuilder.append(HEXES[b.toInt() and 0xF0 shr 4])
        hexStringBuilder.append(HEXES[b.toInt() and 0x0F])
        return hexStringBuilder.toString()
    }
    /**
     * Return a string containing the hex string version of the given byte
     *
     * @param bytes byte arrays
     * @param offset offset in the array
     * @param len length of array
     * @param format the boolean indicates whether to format the hex
     * @return a string containing the hex string version of the given byte
     */
    @JvmOverloads
    fun toHexString(bytes: ByteArray?, offset: Int = 0, len: Int = bytes!!.size, format: Boolean = true): String? {
        return if (bytes == null) {
            null
        } else {
            val hexStringBuilder = StringBuilder(2 * bytes.size)
            for (i in 0 until len) {
                if (format) {
                    if (i % 16 == 0) {
                        hexStringBuilder.append("\n").append(toHexString(i.toByte())).append("  ")
                    } else if (i % 8 == 0) {
                        hexStringBuilder.append(" ")
                    }
                }
                val b = bytes[offset + i]
                hexStringBuilder.append(toHexString(b))
                hexStringBuilder.append(" ")
            }
            hexStringBuilder.toString()
        }
    }
}