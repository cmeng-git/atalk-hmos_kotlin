/*
 * Copyright @ 2015 - present 8x8, Inc
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
package org.atalk.impl.neomedia.transform.srtp.utils

import org.atalk.util.ByteArrayBuffer
import org.atalk.util.ByteArrayUtils
import java.util.*

/**
 * SrtpPacket is the low-level utilities to get the data fields needed by SRTP.
 */
object SrtpPacketUtils {
    /**
     * The size of the fixed part of the RTP header as defined by RFC 3550.
     */
    private const val FIXED_HEADER_SIZE = 12

    /**
     * The size of the fixed part of the extension header as defined by RFC 3550.
     */
    private const val EXT_HEADER_SIZE = 4

    /**
     * Returns `true` if the extension bit of an SRTP packet has been set
     * and `false` otherwise.
     *
     * @param buf The SRTP packet.
     * @return `true` if the extension bit of this packet has been set
     * and `false` otherwise.
     */
    private fun getExtensionBit(buf: ByteArrayBuffer?): Boolean {
        val buffer = buf!!.buffer
        val offset = buf.offset
        return buffer[offset].toInt() and 0x10 == 0x10
    }

    /**
     * Returns the number of CSRC identifiers included in an SRTP packet.
     *
     * Note: this does not verify that the packet is indeed long enough for the claimed number of CSRCs.
     *
     * @param buf The SRTP packet.
     * @return the CSRC count for this packet.
     */
    private fun getCsrcCount(buf: ByteArrayBuffer?): Int {
        val buffer = buf!!.buffer
        val offset = buf.offset
        return buffer[offset].toInt() and 0x0f
    }

    /**
     * Returns the length of the variable-length part of the header extensions present in an SRTP packet.
     *
     * Note: this does not verify that the header extension bit is indeed set, nor that the packet is long
     * enough for the header extension specified.
     *
     * @param buf The SRTP packet.
     * @return the length of the extensions present in this packet.
     */
    private fun getExtensionLength(buf: ByteArrayBuffer): Int {
        val cc = getCsrcCount(buf)

        // The extension length comes after the RTP header, the CSRC list, and
        // two bytes in the extension header called "defined by profile".
        val extLenIndex = FIXED_HEADER_SIZE + cc * 4 + 2
        return ByteArrayUtils.readUint16(buf, extLenIndex) * 4
    }

    /**
     * Reads the sequence number of an SRTP packet.
     *
     * @param buf The buffer holding the SRTP packet.
     */
    fun getSequenceNumber(buf: ByteArrayBuffer): Int {
        return ByteArrayUtils.readUint16(buf, 2)
    }

    /**
     * Reads the SSRC of an SRTP packet.
     *
     * @param buf The buffer holding the SRTP packet.
     */
    fun getSsrc(buf: ByteArrayBuffer): Int {
        return ByteArrayUtils.readInt(buf, 8)
    }

    /**
     * Validate that the contents of a ByteArrayBuffer could contain a valid SRTP packet.
     *
     * This validates that the packet is long enough to be a valid packet, i.e. attempts to read
     * fields of the packet will not fail.
     *
     * @param buf The buffer holding the SRTP packet.
     * @param authTagLen The length of the packet's authentication tag.
     * @return true if the packet is syntactically valid (i.e., long enough); false if not.
     */
    fun validatePacketLength(buf: ByteArrayBuffer, authTagLen: Int): Boolean {
        val length = buf.length
        var neededLength = FIXED_HEADER_SIZE + authTagLen
        if (length < neededLength) {
            return false
        }
        val cc = getCsrcCount(buf)
        neededLength += cc * 4
        if (length < neededLength) {
            return false
        }
        if (getExtensionBit(buf)) {
            neededLength += EXT_HEADER_SIZE
            if (length < neededLength) {
                return false
            }
            val extLen = getExtensionLength(buf)
            neededLength += extLen
            if (length < neededLength) {
                return false
            }
        }
        return true
    }

    /**
     * Gets the total header length of an SRTP packet.
     *
     * @param buf The buffer holding the SRTP packet.
     */
    fun getTotalHeaderLength(buf: ByteArrayBuffer): Int {
        var length = FIXED_HEADER_SIZE + getCsrcCount(buf) * 4
        if (getExtensionBit(buf)) {
            length += EXT_HEADER_SIZE + getExtensionLength(buf)
        }
        return length
    }

    /**
     * Formats the current state of an SRTP/SRTCP replay window, for debugging purposes.
     */
    fun formatReplayWindow(maxIdx: Long, replayWindow: Long, replayWindowSize: Long): String {
        val out = StringBuilder()
        val formatter = Formatter(out)
        formatter.format("maxIdx=%d, window=0x%016x: [", maxIdx, replayWindow)
        var printedSomething = false
        for (i in replayWindowSize - 1 downTo 0) {
            if (replayWindow shr i.toInt() and 0x1L != 0L) {
                if (printedSomething) {
                    out.append(", ")
                }
                printedSomething = true
                out.append(maxIdx - i)
            }
        }
        out.append("]")
        return out.toString()
    }
}