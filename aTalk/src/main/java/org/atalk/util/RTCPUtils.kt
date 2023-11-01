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

import org.atalk.service.neomedia.RawPacket

/**
 * Utility class that contains static methods for RTCP header manipulation.
 *
 * // @deprecated
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
object RTCPUtils {
    /**
     * Gets the RTCP packet type.
     *
     * @param buf the byte buffer that contains the RTCP header.
     * @param off the offset in the byte buffer where the RTCP header starts.
     * @param len the number of bytes in buffer which constitute the actual data.
     * @return the unsigned RTCP packet type, or -1 in case of an error.
     */
    fun getPacketType(buf: ByteArray, off: Int, len: Int): Int {
        return if (!RawPacket.isRtpRtcp(buf, off, len)) {
            -1
        } else buf[off + 1].toInt() and 0xff
    }

    /**
     * Gets the RTCP packet type.
     *
     * @param baf the [ByteArrayBuffer] that contains the RTCP header.
     * @return the unsigned RTCP packet type, or -1 in case of an error.
     */
    fun getPacketType(baf: ByteArrayBuffer?): Int {
        return if (baf == null) {
            -1
        } else getPacketType(baf.buffer, baf.offset, baf.length)
    }

    /**
     * Gets the RTCP packet length in bytes as specified by the length field
     * of the RTCP packet (does not verify that the buffer is actually large enough).
     *
     * @param buf the byte buffer that contains the RTCP header.
     * @param off the offset in the byte buffer where the RTCP header starts.
     * @param len the number of bytes in buffer which constitute the actual data.
     * @return the RTCP packet length in bytes, or -1 in case of an error.
     */
    fun getLength(buf: ByteArray?, off: Int, len: Int): Int {
        // XXX Do not check with isRtpRtcp.
        if (buf == null || buf.size < off + len || len < 4) {
            return -1
        }
        val lengthInWords = buf[off + 2].toInt() and 0xff shl 8 or (buf[off + 3].toInt() and 0xff)
        return (lengthInWords + 1) * 4
    }

    /**
     * Gets the report count field of the RTCP packet specified in the
     * [ByteArrayBuffer] that is passed in as a parameter.
     *
     * @param baf the [ByteArrayBuffer] that contains the RTCP header.
     * @return the report count field of the RTCP packet specified in the
     * [ByteArrayBuffer] that is passed in as a parameter, or -1 in case of an error.
     */
    fun getReportCount(baf: ByteArrayBuffer?): Int {
        return if (baf == null) {
            -1
        } else getReportCount(baf.buffer, baf.offset, baf.length)
    }

    /**
     * Gets the report count field of the RTCP packet specified in the
     * [ByteArrayBuffer] that is passed in as a parameter.
     *
     * @param buf the byte buffer that contains the RTCP header.
     * @param off the offset in the byte buffer where the RTCP header starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     * @return the report count field of the RTCP packet specified in the
     * byte buffer that is passed in as a parameter, or -1 in case of an error.
     */
    private fun getReportCount(buf: ByteArray?, off: Int, len: Int): Int {
        return if (buf == null || buf.size < off + len || len < 1) {
            -1
        } else buf[off].toInt() and 0x1F
    }

    /**
     * Gets the RTCP packet length in bytes.
     *
     * @param baf the [ByteArrayBuffer] that contains the RTCP header.
     * @return the RTCP packet length in bytes, or -1 in case of an error.
     */
    fun getLength(baf: ByteArrayBuffer?): Int {
        return if (baf == null) {
            -1
        } else getLength(baf.buffer, baf.offset, baf.length)
    }

    /**
     * Checks whether the buffer described by the parameters looks like an
     * RTCP packet. It only checks the Version and Packet Type fields, as
     * well as a minimum length.
     * This method returning `true` does not necessarily mean that the
     * given packet is a valid RTCP packet, but it should be parsed as RTCP
     * (as opposed to as e.g. RTP or STUN).
     *
     * @param buf
     * @param off
     * @param len
     * @return `true` if the described packet looks like RTCP.
     */
    fun isRtcp(buf: ByteArray, off: Int, len: Int): Boolean {
        if (!RawPacket.isRtpRtcp(buf, off, len)) {
            return false
        }
        val pt = getPacketType(buf, off, len)

        // Other packet types are used for RTP.
        return pt in 200..211
    }
}