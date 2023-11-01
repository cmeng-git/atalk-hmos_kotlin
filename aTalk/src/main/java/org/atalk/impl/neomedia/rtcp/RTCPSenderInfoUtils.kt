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
package org.atalk.impl.neomedia.rtcp

import net.sf.fmj.media.rtp.RTCPSenderInfo
import org.atalk.util.ByteArrayBuffer
import org.atalk.util.RTPUtils.readUint32AsLong
import org.atalk.util.RTPUtils.writeInt

/**
 * Utility class that contains static methods for RTCP sender info manipulation.
 *
 *
 * TODO maybe merge into the RTCPSenderInfo class.
 *
 * @author George Politis
 */
object RTCPSenderInfoUtils {
    /**
     * Gets the RTP timestamp from an SR.
     *
     * @param buf the byte buffer that contains the RTCP sender report.
     * @param off the offset in the byte buffer where the RTCP sender report starts.
     * @param len the number of bytes in buffer which constitute the actual data.
     * @return the RTP timestamp, or -1 in case of an error.
     */
    fun getTimestamp(buf: ByteArray?, off: Int, len: Int): Long {
        return if (!isValid(buf, off, len)) {
            -1
        } else readUint32AsLong(buf!!, off + 16)
    }

    /**
     * Sets the RTP timestamp in an SR.
     *
     * @param buf the byte buffer that contains the RTCP sender report.
     * @param off the offset in the byte buffer where the RTCP sender report starts.
     * @param len the number of bytes in buffer which constitute the actual data.
     * @param ts the new timestamp to be set.
     * @return the number of bytes written.
     */
    fun setTimestamp(buf: ByteArray?, off: Int, len: Int, ts: Int): Int {
        return if (!isValid(buf, off, len)) {
            -1
        } else writeInt(buf, off + 16, ts)
    }

    /**
     * @param buf the byte buffer that contains the RTCP sender info.
     * @param off the offset in the byte buffer where the RTCP sender info starts.
     * @param len the number of bytes in buffer which constitute the actual data.
     * @return true if the RTCP sender info is valid, false otherwise.
     */
    fun isValid(buf: ByteArray?, off: Int, len: Int): Boolean {
        return if (buf == null || buf.size < off + len || len < RTCPSenderInfo.SIZE) {
            false
        } else true
    }

    /**
     * Sets the RTP timestamp in an SR.
     *
     * @param baf the [ByteArrayBuffer] that holds the SR.
     * @param ts the new timestamp to be set.
     * @return the number of bytes written.
     */
    fun setTimestamp(baf: ByteArrayBuffer?, ts: Int): Int {
        return if (baf == null) {
            -1
        } else setTimestamp(baf.buffer, baf.offset, baf.length, ts)
    }

    /**
     * Gets the RTP timestamp from an SR.
     *
     * @param baf the [ByteArrayBuffer] that holds the SR.
     * @return the RTP timestamp, or -1 in case of an error.
     */
    fun getTimestamp(baf: ByteArrayBuffer?): Long {
        return if (baf == null) {
            -1
        } else getTimestamp(baf.buffer, baf.offset, baf.length)
    }

    /**
     * Sets the octet count in the SR that is specified in the arguments.
     *
     * @param baf the [ByteArrayBuffer] that holds the SR.
     * @param octetCount the octet count ot set.
     * @return the number of bytes that were written, otherwise -1.
     */
    fun setOctetCount(baf: ByteArrayBuffer?, octetCount: Int): Int {
        return if (baf == null) {
            -1
        } else setOctetCount(baf.buffer, baf.offset, baf.length, octetCount)
    }

    /**
     * Sets the packet count in the SR that is specified in the arguments.
     *
     * @param packetCount the packet count to set.
     * @param baf the [ByteArrayBuffer] that holds the SR.
     * @return the number of bytes that were written, otherwise -1.
     */
    fun setPacketCount(baf: ByteArrayBuffer?, packetCount: Int): Int {
        return if (baf == null) {
            -1
        } else setPacketCount(baf.buffer, baf.offset, baf.length, packetCount)
    }

    /**
     * Sets the packet count in the SR that is specified in the arguments.
     *
     * @param packetCount the packet count to set.
     * @param buf the byte buffer that holds the SR.
     * @param off the offset where the data starts
     * @param len the length of the data.
     * @return the number of bytes that were written, otherwise -1.
     */
    private fun setPacketCount(buf: ByteArray?, off: Int, len: Int, packetCount: Int): Int {
        return if (!isValid(buf, off, len)) {
            -1
        } else writeInt(buf, off + 20, packetCount)
    }

    /**
     * Sets the octet count in the SR that is specified in the arguments.
     *
     * @param buf the byte buffer that holds the SR.
     * @param off the offset where the data starts
     * @param len the length of the data.
     * @param octetCount the octet count ot set.
     * @return the number of bytes that were written, otherwise -1.
     */
    private fun setOctetCount(buf: ByteArray?, off: Int, len: Int, octetCount: Int): Int {
        return if (!isValid(buf, off, len)) {
            -1
        } else writeInt(buf, off + 24, octetCount)
    }
}