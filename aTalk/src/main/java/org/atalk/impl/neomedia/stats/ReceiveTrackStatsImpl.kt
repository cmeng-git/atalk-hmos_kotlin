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
package org.atalk.impl.neomedia.stats

import org.atalk.service.neomedia.stats.ReceiveTrackStats
import org.atalk.util.RTPUtils.getSequenceNumberDelta
import org.ice4j.util.RateStatistics
import java.util.concurrent.atomic.AtomicLong

/**
 * Media stream statistics implementation per received SSRC.
 *
 * @author Damian Minkov
 * @author Boris Grozev
 */
class ReceiveTrackStatsImpl internal constructor(interval: Int, ssrc: Long) : AbstractTrackStats(interval.toLong(), ssrc), ReceiveTrackStats {
    /**
     * The highest received sequence number.
     */
    private var highestSeq = -1

    /**
     * The packet loss rate.
     */
    private val mPacketLossRate: RateStatistics

    /**
     * The total number of lost packets.
     */
    private val aPacketsLost = AtomicLong()

    /**
     * Initializes a new instance.
     * @param interval the interval in milliseconds over which average bit- and
     * packet-rates will be computed.
     */
    init {
        mPacketLossRate = RateStatistics(interval, 1000f)
    }

    /**
     * Notifies this instance that an RTP packet with a given length and
     * sequence number was received.
     * @param seq the RTP sequence number of the packet.
     * @param length the length in bytes of the packet.
     */
    fun rtpPacketReceived(seq: Int, length: Int) {
        val now = System.currentTimeMillis()

        // update the bit- and packet-rate
        super.packetProcessed(length, now, true)
        if (highestSeq == -1) {
            highestSeq = seq
            return
        }

        // Now check for lost packets.
        val diff = getSequenceNumberDelta(seq, highestSeq)
        if (diff <= 0) {
            // RFC3550 says that all packets should be counted as received.
            // However, we want to *not* count retransmitted packets as received,
            // otherwise the calculated loss rate will be close to zero as long
            // as all missing packets are requested and retransmitted.
            // Here we differentiate between packets received out of order and
            // those that were retransmitted.
            // Note that this can be avoided if retransmissions always use the
            // RTX format and "de-RTX-ed" packets are not fed to this instance.
            if (diff > -10) {
                aPacketsLost.addAndGet(-1)
                mPacketLossRate.update(-1, now)
            }
        } else {
            // A newer packet.
            highestSeq = seq

            // diff = 1 is the "normal" case (i.e. we received the very next
            // packet).
            if (diff > 1) {
                aPacketsLost.addAndGet((diff - 1).toLong())
                mPacketLossRate.update(diff - 1, now)
            }
        }
    }

    override val packetsLost: Long
        get() = aPacketsLost.get()

    /**
     * {@inheritDoc}
     */
    override val currentPacketsLost: Long
        get() = mPacketLossRate.accumulatedCount

    /**
     * Notifies this instance that an RTCP packet with a specific length was
     * received.
     * @param length the length in bytes.
     */
    fun rtcpPacketReceived(length: Int) {
        super.packetProcessed(length, System.currentTimeMillis(), false)
    }
    // This is not thread safe and the counters might change between the
    // two function calls below, but the result would be just a wrong
    // value for the packet loss rate, and likely just off by a little.
    /**
     * {@inheritDoc}
     *
     * @return the loss rate in the last interval.
     */
    override val lossRate: Double
        get() {
            // This is not thread safe and the counters might change between the
            // two function calls below, but the result would be just a wrong
            // value for the packet loss rate, and likely just off by a little.
            val lost = currentPacketsLost
            val expected = lost + currentPackets
            return if (expected == 0L) 0.0 else (lost / expected).toDouble()
        }
}