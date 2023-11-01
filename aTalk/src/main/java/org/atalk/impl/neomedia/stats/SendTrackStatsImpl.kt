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

import org.atalk.service.neomedia.stats.SendTrackStats
import org.atalk.util.RTPUtils.getSequenceNumberDelta
import org.ice4j.util.RateStatistics

/**
 * Media stream statistics implementation per send SSRC.
 *
 * @author Damian Minkov
 * @author Boris Grozev
 */
class SendTrackStatsImpl
/**
 * Initializes a new instance.
 * @param interval the interval in milliseconds over which average bit- and
 * packet-rates will be computed.
 */
internal constructor(interval: Int, ssrc: Long) : AbstractTrackStats(interval.toLong(), ssrc), SendTrackStats {
    /**
     * {@inheritDoc}
     */
    /**
     * The highest sent sequence number.
     */
    override var highestSent = -1
        private set

    /**
     * Rate of packet that we did not send (i.e. were lost on their way to us)
     */
    private var mPacketsNotSentRate = RateStatistics(1000, 1000f)

    /**
     * The fraction lost reported in the most recently received RTCP Receiver Report.
     */
    private var fractionLost = -1.0

    /**
     * The time at which [.fractionLost] was last updated.
     */
    private var fractionLostLastUpdate = -1L

    /**
     * Notifies this instance that an RTP packet with a particular sequence
     * number was sent (or is about to be sent).
     * @param seq the RTP sequence number.
     * @param length the length in bytes.
     */
    fun rtpPacketSent(seq: Int, length: Int) {
        val now = System.currentTimeMillis()

        // update the bit- and packet-rate
        super.packetProcessed(length, now, true)
        if (highestSent == -1) {
            highestSent = seq
            return
        }

        // We monitor the sequence numbers of sent packets in order to
        // calculate the actual number of lost packets.
        // If we are forwarding the stream (as opposed to generating it
        // locally), as is the case in jitsi-videobridge, packets may be lost
        // between the sender and us, and we need to take this into account
        // when calculating packet loss to the receiver.
        val diff = getSequenceNumberDelta(seq, highestSent)
        if (diff <= 0) {
            // An old packet, already counted as not send. Un-not-send it ;)
            mPacketsNotSentRate.update(-1, now)
        } else {
            // A newer packet.
            highestSent = seq

            // diff = 1 is the "normal" case (i.e. we received the very next
            // packet).
            if (diff > 1) {
                mPacketsNotSentRate.update(diff - 1, now)
            }
        }
    }
    // We haven't received a RR recently, so assume no loss.

    // Take into account packets that we did not send
    /**
     * {@inheritDoc}
     *
     * Returns an estimation of the loss rate based on the most recent RTCP
     * Receiver Report that we received, and the rate of "non-sent" packets
     * (i.e. in the case of jitsi-videobridge the loss rate from the sender to the bridge).
     */
    override val lossRate: Double
        get() {
            val now = System.currentTimeMillis()
            if (fractionLostLastUpdate == -1L || now - fractionLostLastUpdate > 8000) {
                // We haven't received a RR recently, so assume no loss.
                return 0.0
            }

            // Take into account packets that we did not send
            val packetsNotSent = mPacketsNotSentRate.getAccumulatedCount(now)
            val packetsSent = mPacketRate.getAccumulatedCount(now)
            val fractionNotSent = if (packetsSent + packetsNotSent > 0) (packetsNotSent / (packetsNotSent + packetsSent)).toDouble() else 0.toDouble()
            return 0.0.coerceAtLeast(fractionLost - fractionNotSent)
        }

    /**
     * Notifies this instance that an RTCP packet with a given length in bytes
     * was sent (or is about to be sent).
     * @param length
     */
    fun rtcpPacketSent(length: Int) {
        super.packetProcessed(length, System.currentTimeMillis(), false)
    }

    /**
     * Notifies this instance that an RTCP Receiver Report with a given value
     * for the "fraction lost" field was received.
     * @param fractionLost the value of the "fraction lost" field from an RTCP
     * Receiver Report as an unsigned integer.
     */
    fun rtcpReceiverReportReceived(fractionLost: Int) {
        this.fractionLost = fractionLost / 256.0
        fractionLostLastUpdate = System.currentTimeMillis()
    }
}