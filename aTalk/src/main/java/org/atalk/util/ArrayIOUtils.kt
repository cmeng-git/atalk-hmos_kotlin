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
 * Implements functionality aiding the reading and writing in little endian of
 * `byte` arrays and primitive types such as `short`.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
object ArrayIOUtils {
    /**
     * Reads an integer from a specific series of bytes starting the reading at
     * a specific offset in it.
     *
     * @param in the series of bytes to read an integer from
     * @param inOffset the offset in `in` at which the reading of the
     * integer is to start
     *
     * @return an integer read from the specified series of bytes starting at
     * the specified offset in it
     */
    fun readInt(`in`: ByteArray, inOffset: Int): Int {
        return (`in`[inOffset + 3].toInt() shl 24
                or (`in`[inOffset + 2].toInt() and 0xFF shl 16)
                or (`in`[inOffset + 1].toInt() and 0xFF shl 8)
                or (`in`[inOffset].toInt() and 0xFF))
    }

    /**
     * Reads a short integer from a specific series of bytes starting the
     * reading at a specific offset in it. The difference with
     * [.readShort] is that the read short integer is an
     * `int` which has been formed by reading two bytes, not a `short`.
     *
     * @param in the series of bytes to read the short integer from
     * @param inOffset the offset in `in` at which the reading of the short integer is to start
     *
     * @return a short integer in the form of `int` read from the
     * specified series of bytes starting at the specified offset in it
     */
    fun readInt16(`in`: ByteArray, inOffset: Int): Int {
        return `in`[inOffset + 1].toInt() shl 8 or (`in`[inOffset].toInt() and 0xFF)
    }

    /**
     * Reads a short integer from a specific series of bytes starting the
     * reading at a specific offset in it.
     *
     * @param in the series of bytes to read the short integer from
     * @param inOffset the offset in `in` at which the reading of the
     * short integer is to start
     *
     * @return a short integer in the form of `short` read from the
     * specified series of bytes starting at the specified offset in it
     */
    fun readShort(`in`: ByteArray, inOffset: Int): Short {
        return readInt16(`in`, inOffset).toShort()
    }

    /**
     * Converts an integer to a series of bytes and writes the result into a specific output
     * array of bytes starting the writing at a specific offset in it.
     *
     * @param in the integer to be written out as a series of bytes
     * @param out the output to receive the conversion of the specified integer to a series of bytes
     * @param outOffset the offset in `out` at which the writing of the
     * result of the conversion is to be started
     */
    fun writeInt(`in`: Int, out: ByteArray, outOffset: Int) {
        out[outOffset] = (`in` and 0xFF).toByte()
        out[outOffset + 1] = (`in` ushr 8 and 0xFF).toByte()
        out[outOffset + 2] = (`in` ushr 16 and 0xFF).toByte()
        out[outOffset + 3] = (`in` shr 24).toByte()
    }

    /**
     * Converts a short integer to a series of bytes and writes the result into
     * a specific output array of bytes starting the writing at a specific
     * offset in it. The difference with [.writeShort]
     * is that the input is an `int` and just two bytes of it are written.
     *
     * @param in the short integer to be written out as a series of bytes specified as an integer
     * i.e. the value to be converted is contained in only two of the four bytes made available by the integer
     * @param out the output to receive the conversion of the specified short integer to a series of bytes
     * @param outOffset the offset in `out` at which the writing of the
     * result of the conversion is to be started
     */
    fun writeInt16(`in`: Int, out: ByteArray, outOffset: Int) {
        out[outOffset] = (`in` and 0xFF).toByte()
        out[outOffset + 1] = (`in` shr 8).toByte()
    }

    /**
     * Converts a short integer to a series of bytes and writes the result into
     * a specific output array of bytes starting the writing at a specific offset in it.
     *
     * @param in the short integer to be written out as a series of bytes specified as `short`
     * @param out the output to receive the conversion of the specified short integer to a series of bytes
     * @param outOffset the offset in `out` at which the writing of the result of the conversion is to be started
     */
    fun writeShort(`in`: Short, out: ByteArray, outOffset: Int) {
        writeInt16(`in`.toInt(), out, outOffset)
    }
}