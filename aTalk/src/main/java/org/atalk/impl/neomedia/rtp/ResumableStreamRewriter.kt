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
package org.atalk.impl.neomedia.rtp

import org.atalk.impl.neomedia.rtcp.RTCPSenderInfoUtils.getTimestamp
import org.atalk.impl.neomedia.rtcp.RTCPSenderInfoUtils.setTimestamp
import org.atalk.service.neomedia.RawPacket
import org.atalk.util.RTPUtils.getSequenceNumberDelta
import org.atalk.util.RTPUtils.rtpTimestampDiff
import org.atalk.util.RTPUtils.subtractNumber

/**
 * Rewrites sequence numbers for RTP streams by hiding any gaps caused by
 * dropped packets. Rewriters are not thread-safe. If multiple threads access a
 * rewriter concurrently, it must be synchronized externally.
 *
 * @author Maryam Daneshi
 * @author George Politis
 * @author Eng Chong Meng
 */
class ResumableStreamRewriter {
    /**
     * The sequence number delta between what's been accepted and what's been
     * received, mod 2^16.
     */
    var seqnumDelta = 0

    /**
     * The timestamp delta between what's been accepted and what's been
     * received, mod 2^32.
     */
    private var timestampDelta = 0L

    /**
     * The highest sequence number that got accepted, mod 2^16.
     */
    var highestSequenceNumberSent = -1

    /**
     * The highest timestamp that got accepted, mod 2^32.
     */
    private var highestTimestampSent = -1L

    /**
     * Rewrites the sequence number of the RTP packet in the byte buffer,
     * hiding any gaps caused by drops.
     *
     * @param accept true if the packet is accepted, false otherwise
     * @param buf the byte buffer that contains the RTP packet
     * @param off the offset in the byte buffer where the RTP packet starts
     * @param len the length of the RTP packet in the byte buffer
     * @return true if the packet was altered, false otherwise
     */
    fun rewriteRTP(accept: Boolean, buf: ByteArray?, off: Int, len: Int): Boolean {
        if (buf == null || buf.size < off + len) {
            return false
        }
        val sequenceNumber = RawPacket.getSequenceNumber(buf, off, len)
        val newSequenceNumber = rewriteSequenceNumber(accept, sequenceNumber)
        val timestamp = RawPacket.getTimestamp(buf, off, len)
        val newTimestamp = rewriteTimestamp(accept, timestamp)
        var modified = false
        if (sequenceNumber != newSequenceNumber) {
            RawPacket.setSequenceNumber(buf, off, newSequenceNumber)
            modified = true
        }
        if (timestamp != newTimestamp) {
            RawPacket.setTimestamp(buf, off, len, newTimestamp)
            modified = true
        }
        return modified
    }

    /**
     * Restores the RTP timestamp of the RTCP SR packet in the buffer.
     *
     * @param buf the byte buffer that contains the RTCP packet.
     * @param off the offset in the byte buffer where the RTCP packet starts.
     * @param len the number of bytes in buffer which constitute the actual
     * data.
     * @return true if the SR is modified, false otherwise.
     */
    fun processRTCP(rewrite: Boolean, buf: ByteArray?, off: Int, len: Int): Boolean {
        if (timestampDelta == 0L) {
            return false
        }
        val ts = getTimestamp(buf, off, len)
        if (ts == -1L) {
            return false
        }
        val newTs = if (rewrite) ts - timestampDelta and 0xffffffffL else ts + timestampDelta and 0xffffffffL
        val ret = setTimestamp(buf, off, len, newTs.toInt())
        return ret > 0
    }

    /**
     * Rewrites the sequence number passed as a parameter, hiding any gaps
     * caused by drops.
     *
     * @param accept true if the packet is accepted, false otherwise
     * @param sequenceNumber the sequence number to rewrite
     * @return a rewritten sequence number that hides any gaps caused by drops.
     */
    fun rewriteSequenceNumber(accept: Boolean, sequenceNumber: Int): Int {
        return if (accept) {
            // overwrite the sequence number (if needed)
            val newSequenceNumber = subtractNumber(sequenceNumber, seqnumDelta)

            // init or update the highest sent sequence number (if needed)
            if (highestSequenceNumberSent == -1 || getSequenceNumberDelta(
                            newSequenceNumber, highestSequenceNumberSent) > 0) {
                highestSequenceNumberSent = newSequenceNumber
            }
            newSequenceNumber
        } else {
            // update the sequence number delta (if needed)
            if (highestSequenceNumberSent != -1) {
                val newDelta = subtractNumber(
                        sequenceNumber, highestSequenceNumberSent)
                if (getSequenceNumberDelta(newDelta, seqnumDelta) > 0) {
                    seqnumDelta = newDelta
                }
            }
            sequenceNumber
        }
    }

    /**
     * Rewrites the timestamp passed as a parameter, hiding any gaps caused by
     * drops.
     *
     * @param accept true if the packet is accepted, false otherwise
     * @param timestamp the timestamp to rewrite
     * @return a rewritten timestamp that hides any gaps caused by drops.
     */
    private fun rewriteTimestamp(accept: Boolean, timestamp: Long): Long {
        return if (accept) {
            // overwrite the timestamp (if needed)
            val newTimestamp = timestamp - timestampDelta and 0xffffffffL

            // init or update the highest sent timestamp (if needed)
            if (highestTimestampSent == -1L ||
                    rtpTimestampDiff(newTimestamp, highestTimestampSent) > 0) {
                highestTimestampSent = newTimestamp
            }
            newTimestamp
        } else {
            // update the timestamp delta (if needed)
            if (highestTimestampSent != -1L) {
                val newDelta = timestamp - highestTimestampSent and 0xffffffffL
                if (rtpTimestampDiff(newDelta, timestampDelta) > 0) {
                    timestampDelta = newDelta
                }
            }
            timestamp
        }
    }
}