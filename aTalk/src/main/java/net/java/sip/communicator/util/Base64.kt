/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 *
 * The contents of this file has been copied from the Base64 and Base64Encoder
 * classes of the Bouncy Castle libraries and included the following license.
 *
 * Copyright (c) 2000 - 2006 The Legion Of The Bouncy Castle
 * (http://www.bouncycastle.org)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.java.sip.communicator.util

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

object Base64 {
    private val encoder = Base64Encoder()

    /**
     * encode the input data producing a base 64 encoded byte array.
     *
     * @param data the byte array to encode
     * @return a byte array containing the base 64 encoded data.
     */
    fun encode(
            data: ByteArray): ByteArray {
        val bOut = ByteArrayOutputStream()
        try {
            encoder.encode(data, 0, data.size, bOut)
        } catch (e: IOException) {
            throw RuntimeException("exception encoding base64 string: $e")
        }
        return bOut.toByteArray()
    }

    /**
     * Encode the byte data to base 64 writing it to the given output stream.
     *
     * @param data the byte array to encode
     * @param out the output stream where the result is to be written.
     * @return the number of bytes produced.
     * @throws IOException if the output stream throws one
     */
    @Throws(IOException::class)
    fun encode(data: ByteArray, out: OutputStream): Int {
        return encoder.encode(data, 0, data.size, out)
    }

    /**
     * Encode the byte data to base 64 writing it to the given output stream.
     *
     * @param data the byte array to encode
     * @param off offset
     * @param length length
     * @param out OutputStream
     * @return the number of bytes produced.
     * @throws IOException
     */
    @Throws(IOException::class)
    fun encode(data: ByteArray, off: Int, length: Int, out: OutputStream): Int {
        return encoder.encode(data, off, length, out)
    }

    /**
     * decode the base 64 encoded input data. It is assumed the input data is valid.
     *
     * @param data the byte array to encode
     * @return a byte array representing the decoded data.
     */
    fun decode(data: ByteArray): ByteArray {
        val bOut = ByteArrayOutputStream()
        try {
            encoder.decode(data, 0, data.size, bOut)
        } catch (e: IOException) {
            throw RuntimeException("exception decoding base64 string: $e")
        }
        return bOut.toByteArray()
    }

    /**
     * decode the base 64 encoded String data - whitespace will be ignored.
     *
     * @param data the byte array to encode
     * @return a byte array representing the decoded data.
     */
    fun decode(data: String): ByteArray {
        val bOut = ByteArrayOutputStream()
        try {
            encoder.decode(data, bOut)
        } catch (e: IOException) {
            throw RuntimeException("exception decoding base64 string: $e")
        }
        return bOut.toByteArray()
    }

    /**
     * decode the base 64 encoded String data writing it to the given output stream,
     * whitespace characters will be ignored.
     *
     * @param data the data to decode
     * @param out OutputStream
     * @return the number of bytes produced.
     * @throws IOException if an exception occurs while writing to the specified output stream
     */
    @Throws(IOException::class)
    fun decode(
            data: String,
            out: OutputStream): Int {
        return encoder.decode(data, out)
    }

    class Base64Encoder {
        protected val encodingTable = byteArrayOf('A'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte(), 'D'.code.toByte(), 'E'.code.toByte(), 'F'.code.toByte(), 'G'.code.toByte(), 'H'.code.toByte(), 'I'.code.toByte(), 'J'.code.toByte(), 'K'.code.toByte(), 'L'.code.toByte(), 'M'.code.toByte(), 'N'.code.toByte(), 'O'.code.toByte(), 'P'.code.toByte(), 'Q'.code.toByte(), 'R'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte(), 'U'.code.toByte(), 'V'.code.toByte(), 'W'.code.toByte(), 'X'.code.toByte(), 'Y'.code.toByte(), 'Z'.code.toByte(), 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 'd'.code.toByte(), 'e'.code.toByte(), 'f'.code.toByte(), 'g'.code.toByte(), 'h'.code.toByte(), 'i'.code.toByte(), 'j'.code.toByte(), 'k'.code.toByte(), 'l'.code.toByte(), 'm'.code.toByte(), 'n'.code.toByte(), 'o'.code.toByte(), 'p'.code.toByte(), 'q'.code.toByte(), 'r'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(), 'u'.code.toByte(), 'v'.code.toByte(), 'w'.code.toByte(), 'x'.code.toByte(), 'y'.code.toByte(), 'z'.code.toByte(), '0'.code.toByte(), '1'.code.toByte(), '2'.code.toByte(), '3'.code.toByte(), '4'.code.toByte(), '5'.code.toByte(), '6'.code.toByte(), '7'.code.toByte(), '8'.code.toByte(), '9'.code.toByte(), '+'.code.toByte(), '/'.code.toByte())
        protected var padding = '='.code.toByte()

        /*
         * set up the decoding table.
         */
        protected val decodingTable = ByteArray(128)
        protected fun initialiseDecodingTable() {
            for (i in encodingTable.indices) {
                decodingTable[encodingTable[i].toInt()] = i.toByte()
            }
        }

        init {
            initialiseDecodingTable()
        }

        /**
         * encode the input data producing a base 64 output stream.
         *
         * @param data the byte array to encode
         * @param off offset
         * @param length length
         * @param out OutputStream
         * @return the number of bytes produced.
         * @throws IOException if an exception occurs while writing to the stream.
         */
        @Throws(IOException::class)
        fun encode(data: ByteArray, off: Int, length: Int, out: OutputStream): Int {
            val modulus = length % 3
            val dataLength = length - modulus
            var a1: Int
            var a2: Int
            var a3: Int
            var i = off
            while (i < off + dataLength) {
                a1 = data[i].toInt() and 0xff
                a2 = data[i + 1].toInt() and 0xff
                a3 = data[i + 2].toInt() and 0xff
                out.write(encodingTable[a1 ushr 2 and 0x3f].toInt())
                out.write(encodingTable[a1 shl 4 or (a2 ushr 4) and 0x3f].toInt())
                out.write(encodingTable[a2 shl 2 or (a3 ushr 6) and 0x3f].toInt())
                out.write(encodingTable[a3 and 0x3f].toInt())
                i += 3
            }

            /*
             * process the tail end.
             */
            val b1: Int
            val b2: Int
            val b3: Int
            val d1: Int
            val d2: Int
            when (modulus) {
                0 -> {}
                1 -> {
                    d1 = data[off + dataLength].toInt() and 0xff
                    b1 = d1 ushr 2 and 0x3f
                    b2 = d1 shl 4 and 0x3f
                    out.write(encodingTable[b1].toInt())
                    out.write(encodingTable[b2].toInt())
                    out.write(padding.toInt())
                    out.write(padding.toInt())
                }
                2 -> {
                    d1 = data[off + dataLength].toInt() and 0xff
                    d2 = data[off + dataLength + 1].toInt() and 0xff
                    b1 = d1 ushr 2 and 0x3f
                    b2 = d1 shl 4 or (d2 ushr 4) and 0x3f
                    b3 = d2 shl 2 and 0x3f
                    out.write(encodingTable[b1].toInt())
                    out.write(encodingTable[b2].toInt())
                    out.write(encodingTable[b3].toInt())
                    out.write(padding.toInt())
                }
            }
            return dataLength / 3 * 4 + if (modulus == 0) 0 else 4
        }

        private fun ignore(c: Char): Boolean {
            return c == '\n' || c == '\r' || c == '\t' || c == ' '
        }

        /**
         * decode the base 64 encoded byte data writing it to the given output
         * stream, whitespace characters will be ignored.
         *
         * @param data the byte array to encode
         * @param off offset
         * @param length length
         * @param out OutputStream
         * @return the number of bytes produced.
         * @throws IOException if an exception occurs while wrinting to the
         * stream.
         */
        @Throws(IOException::class)
        fun decode(data: ByteArray, off: Int, length: Int, out: OutputStream): Int {
            var b1: Byte
            var b2: Byte
            var b3: Byte
            var b4: Byte
            var outLen = 0
            var end = off + length
            while (end > off) {
                if (!ignore(Char(data[end - 1].toUShort()))) {
                    break
                }
                end--
            }
            var i = off
            val finish = end - 4
            i = nextI(data, i, finish)
            while (i < finish) {
                b1 = decodingTable[data[i++].toInt()]
                i = nextI(data, i, finish)
                b2 = decodingTable[data[i++].toInt()]
                i = nextI(data, i, finish)
                b3 = decodingTable[data[i++].toInt()]
                i = nextI(data, i, finish)
                b4 = decodingTable[data[i++].toInt()]
                out.write(b1.toInt() shl 2 or (b2.toInt() shr 4))
                out.write(b2.toInt() shl 4 or (b3.toInt() shr 2))
                out.write(b3.toInt() shl 6 or b4.toInt())
                outLen += 3
                i = nextI(data, i, finish)
            }
            outLen += decodeLastBlock(out, Char(data[end - 4].toUShort()), Char(data[end - 3].toUShort()), Char(data[end - 2].toUShort()), Char(data[end - 1].toUShort()))
            return outLen
        }

        private fun nextI(data: ByteArray, i: Int, finish: Int): Int {
            var i = i
            while (i < finish && ignore(Char(data[i].toUShort()))) {
                i++
            }
            return i
        }

        /**
         * decode the base 64 encoded String data writing it to the given output
         * stream, whitespace characters will be ignored.
         *
         * @param data the byte array to encode
         * @param out OutputStream
         * @return the number of bytes produced.
         * @throws IOException if an exception occurs while writing to the stream
         */
        @Throws(IOException::class)
        fun decode(data: String, out: OutputStream): Int {
            var b1: Byte
            var b2: Byte
            var b3: Byte
            var b4: Byte
            var length = 0
            var end = data.length
            while (end > 0) {
                if (!ignore(data[end - 1])) {
                    break
                }
                end--
            }
            var i = 0
            val finish = end - 4
            i = nextI(data, i, finish)
            while (i < finish) {
                b1 = decodingTable[data[i++].code]
                i = nextI(data, i, finish)
                b2 = decodingTable[data[i++].code]
                i = nextI(data, i, finish)
                b3 = decodingTable[data[i++].code]
                i = nextI(data, i, finish)
                b4 = decodingTable[data[i++].code]
                out.write(b1.toInt() shl 2 or (b2.toInt() shr 4))
                out.write(b2.toInt() shl 4 or (b3.toInt() shr 2))
                out.write(b3.toInt() shl 6 or b4.toInt())
                length += 3
                i = nextI(data, i, finish)
            }
            length += decodeLastBlock(out, data[end - 4], data[end - 3],
                    data[end - 2], data[end - 1])
            return length
        }

        @Throws(IOException::class)
        private fun decodeLastBlock(out: OutputStream, c1: Char, c2: Char, c3: Char, c4: Char): Int {
            val b1: Byte
            val b2: Byte
            val b3: Byte
            val b4: Byte
            return if (c3.code.toByte() == padding) {
                b1 = decodingTable[c1.code]
                b2 = decodingTable[c2.code]
                out.write(b1.toInt() shl 2 or (b2.toInt() shr 4))
                1
            } else if (c4.code.toByte() == padding) {
                b1 = decodingTable[c1.code]
                b2 = decodingTable[c2.code]
                b3 = decodingTable[c3.code]
                out.write(b1.toInt() shl 2 or (b2.toInt() shr 4))
                out.write(b2.toInt() shl 4 or (b3.toInt() shr 2))
                2
            } else {
                b1 = decodingTable[c1.code]
                b2 = decodingTable[c2.code]
                b3 = decodingTable[c3.code]
                b4 = decodingTable[c4.code]
                out.write(b1.toInt() shl 2 or (b2.toInt() shr 4))
                out.write(b2.toInt() shl 4 or (b3.toInt() shr 2))
                out.write(b3.toInt() shl 6 or b4.toInt())
                3
            }
        }

        private fun nextI(data: String, i: Int, finish: Int): Int {
            var i = i
            while (i < finish && ignore(data[i])) {
                i++
            }
            return i
        }
    }
}