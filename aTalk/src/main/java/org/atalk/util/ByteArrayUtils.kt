/*
 * Copyright @ 2019 - Present 8x8, Inc
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
 * Contains basic methods for reading/writing values to/from a `byte[]`s
 * and [ByteArrayBuffer]s.
 */
object ByteArrayUtils {
    /**
     * Read a unsigned 16-bit value from a byte array buffer at a specified offset as an int.
     *
     * @param bab the buffer from which to read.
     * @param off start offset of the unsigned short
     * @return the int value of the unsigned short at offset
     */
    fun readUint16(bab: ByteArrayBuffer, off: Int): Int {
        return readUint16(bab.buffer, off + bab.offset)
    }

    /**
     * Read a unsigned 16-bit value from a byte array at a specified offset as an int.
     *
     * @param buf the buffer from which to read.
     * @param off start offset of the unsigned short
     * @return the int value of the unsigned short at offset
     */
    fun readUint16(buf: ByteArray?, off: Int): Int {
        var off = off
        val b1 = 0xFF and buf!![off++].toInt()
        val b2 = 0xFF and buf[off].toInt()
        return b1 shl 8 or b2
    }

    /**
     * Writes a 16-bit unsigned integer into a byte array buffer at a specified offset.
     *
     * @param bab the byte array to write to.
     * @param value the value to write.
     */
    fun writeUint16(bab: ByteArrayBuffer, off: Int, value: Int) {
        writeUint16(bab.buffer, off + bab.offset, value)
    }

    /**
     * Writes a 16-bit unsigned integer into a byte array at a specified offset.
     *
     * @param buf the byte array to write to.
     * @param value the value to write.
     */
    fun writeUint16(buf: ByteArray?, off: Int, value: Int) {
        var off = off
        buf!![off++] = (value shr 8 and 0xFF).toByte()
        buf[off] = (value and 0xFF).toByte()
    }

    /**
     * Read a unsigned 24-bit value from a byte array buffer at a specified offset as an int.
     *
     * @param bab the buffer from which to read.
     * @param off start offset of the unsigned short
     * @return the int value of the unsigned short at offset
     */
    fun readUint24(bab: ByteArrayBuffer, off: Int): Int {
        return readUint24(bab.buffer, off + bab.offset)
    }

    /**
     * Read a unsigned 24-bit value from a byte array at a specified offset as an int.
     *
     * @param buf the buffer from which to read.
     * @param off start offset of the unsigned short
     * @return the int value of the unsigned short at offset
     */
    fun readUint24(buf: ByteArray?, off: Int): Int {
        var off = off
        val b1 = 0xFF and buf!![off++].toInt()
        val b2 = 0xFF and buf[off++].toInt()
        val b3 = 0xFF and buf[off].toInt()
        return b1 shl 16 or (b2 shl 8) or b3
    }

    /**
     * Writes a 24-bit unsigned integer into a byte array buffer at a specified offset.
     *
     * @param bab the byte array to write to.
     * @param value the value to write.
     */
    fun writeUint24(bab: ByteArrayBuffer, off: Int, value: Int) {
        writeUint24(bab.buffer, off + bab.offset, value)
    }

    /**
     * Writes a 24-bit unsigned integer into a byte array at a specified offset.
     *
     * @param buf the byte array to write to.
     * @param value the value to write.
     */
    fun writeUint24(buf: ByteArray?, off: Int, value: Int) {
        var off = off
        buf!![off++] = (value shr 16 and 0xFF).toByte()
        buf[off++] = (value shr 8 and 0xFF).toByte()
        buf[off] = (value and 0xFF).toByte()
    }

    /**
     * Read a unsigned 32-bit value from a byte array buffer at a specified offset as a `long`.
     *
     * @param bab the buffer from which to read.
     * @param off start offset of the unsigned short
     * @return the int value of the unsigned short at offset
     */
    fun readUint32(bab: ByteArrayBuffer, off: Int): Long {
        return readUint32(bab.buffer, off + bab.offset)
    }

    /**
     * Read a unsigned 32-bit value from a byte array at a specified offset as a `long`.
     *
     * @param buf the buffer from which to read.
     * @param off start offset of the unsigned short
     * @return the int value of the unsigned short at offset
     */
    fun readUint32(buf: ByteArray?, off: Int): Long {
        return readInt(buf, off).toLong() and 0xFFFFFFFFL
    }

    /**
     * Read a 32-bit integer from a byte array buffer at a specified offset.
     *
     * @param bab the byte array from which to read.
     * @param off start offset in the buffer of the integer to be read.
     */
    fun readInt(bab: ByteArrayBuffer, off: Int): Int {
        return readInt(bab.buffer, off + bab.offset)
    }

    /**
     * Read a 32-bit integer from a byte array at a specified offset.
     *
     * @param buf the byte array from which to read.
     * @param off start offset in the buffer of the integer to be read.
     */
    fun readInt(buf: ByteArray?, off: Int): Int {
        var off = off
        return (buf!![off++].toInt() and 0xFF shl 24
                or (buf[off++].toInt() and 0xFF shl 16)
                or (buf[off++].toInt() and 0xFF shl 8)
                or (buf[off].toInt() and 0xFF))
    }

    /**
     * Writes an `int` into a byte array buffer at a specified offset.
     *
     * @param bab the byte array to write to.
     * @param value the value to write.
     */
    fun writeInt(bab: ByteArrayBuffer, off: Int, value: Int) {
        writeInt(bab.buffer, off + bab.offset, value)
    }

    /**
     * Writes an `int` into a byte array at a specified offset.
     *
     * @param buf the byte array to write to.
     * @param value the value to write.
     */
    fun writeInt(buf: ByteArray?, off: Int, value: Int) {
        var off = off
        buf!![off++] = (value shr 24 and 0xFF).toByte()
        buf[off++] = (value shr 16 and 0xFF).toByte()
        buf[off++] = (value shr 8 and 0xFF).toByte()
        buf[off] = (value and 0xFF).toByte()
    }

    /**
     * Read a 16-bit signed integer from a byte array buffer at a specified offset into a `short`.
     *
     * @param bab the byte array from which to read.
     * @param off start offset in the buffer of the integer to be read.
     */
    fun readShort(bab: ByteArrayBuffer, off: Int): Short {
        return readShort(bab.buffer, off + bab.offset)
    }

    /**
     * Read a 16-bit signed integer from a byte array at a specified offset into a `short`.
     *
     * @param buf the byte array from which to read.
     * @param off start offset in the buffer of the integer to be read.
     */
    fun readShort(buf: ByteArray?, off: Int): Short {
        var off = off
        return (buf!![off++].toInt() and 0xFF shl 8 or (buf[off].toInt() and 0xFF)).toShort()
    }

    /**
     * Writes a `short` into a byte array buffer at a specified offset.
     *
     * @param bab the byte array to write to.
     * @param value the value to write.
     */
    fun writeShort(bab: ByteArrayBuffer, off: Int, value: Short) {
        writeShort(bab.buffer, off + bab.offset, value)
    }

    /**
     * Writes a `short` into a byte array at a specified offset.
     *
     * @param buf the byte array to write to.
     * @param value the value to write.
     */
    fun writeShort(buf: ByteArray?, off: Int, value: Short) {
        var off = off
        buf!![off++] = (value.toInt() shr 8 and 0xFF).toByte()
        buf[off] = (value.toInt() and 0xFF).toByte()
    }
}