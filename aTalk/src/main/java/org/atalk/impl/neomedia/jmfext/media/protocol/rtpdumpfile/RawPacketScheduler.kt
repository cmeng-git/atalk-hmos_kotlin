/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.rtpdumpfile

import org.atalk.impl.neomedia.RTPPacketPredicate
import org.atalk.service.neomedia.RawPacket
import org.atalk.util.ByteArrayBuffer
import org.atalk.util.RTPUtils.rtpTimestampDiff

/**
 * Suggests a schedule method that puts the current thread to sleep for X milis, where X is such
 * that RTP timestamps and a given clock are respected.
 *
 * @author George Politis
 */
class RawPacketScheduler
/**
 * Ctor.
 *
 * @param clockRate
 */(
        /**
         * The RTP clock rate, used to interpret the RTP timestamps read from the file.
         */
        private val clockRate: Long) {
    /**
     * The timestamp of the last rtp packet (the timestamp change only when a marked packet has been sent).
     */
    private var lastRtpTimestamp = -1L

    /**
     * puts the current thread to sleep for X milis, where X is such that RTP timestamps and a given
     * clock are respected.
     *
     * @param rtpPacket
     * the `RTPPacket` to schedule.
     */
    @Throws(InterruptedException::class)
    fun schedule(rtpPacket:  RawPacket) {
        if (!RTPPacketPredicate.INSTANCE.test(rtpPacket as ByteArrayBuffer)) {
            return
        }

        if (lastRtpTimestamp == -1L) {
            lastRtpTimestamp = rtpPacket.timestamp
            return
        }

        val previous = lastRtpTimestamp
        lastRtpTimestamp = rtpPacket.timestamp
        val rtpDiff = rtpTimestampDiff(lastRtpTimestamp, previous)
        val nanos = rtpDiff * 1000 * 1000 * 1000 / clockRate
        if (nanos > 0) {
            Thread.sleep(nanos / 1000000, (nanos % 1000000).toInt())
        }
    }
}