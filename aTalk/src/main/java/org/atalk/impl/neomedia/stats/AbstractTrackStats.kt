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

import org.atalk.service.neomedia.stats.TrackStats
import org.ice4j.util.RateStatistics
import java.util.concurrent.atomic.AtomicLong

/**
 * Media stream statistics per send or receive SSRC.
 *
 * @author Damian Minkov
 * @author Boris Grozev
 */
abstract class AbstractTrackStats internal constructor(
        /**
         * The length of the interval over which the average bitrate, packet rate
         * and packet loss rate are computed.
         */
        final override val interval: Long,
        /**
         * The SSRC, if any, associated with this instance.
         */
        override val sSRC: Long) : TrackStats {

    /**
     * The total number of bytes.
     */
    private val aBytes = AtomicLong()

    /**
     * The total number of RTP packets. This excludes RTCP packets, because
     * the value is used to calculate the number of lost RTP packets.
     */
    private val aPackets = AtomicLong()

    /**
     * Number of bytes retransmitted.
     */
    private val aBytesRetransmitted = AtomicLong()

    /**
     * Number of bytes for packets which were requested and found in the
     * cache, but were intentionally not retransmitted.
     */
    private val aBytesNotRetransmitted = AtomicLong()

    /**
     * Number of packets retransmitted.
     */
    private val aPacketsRetransmitted = AtomicLong()

    /**
     * Number of packets which were requested and found in the cache, but
     * were intentionally not retransmitted.
     */
    private val aPacketsNotRetransmitted = AtomicLong()

    /**
     * The number of packets for which retransmission was requested, but
     * they were missing from the cache.
     */
    private val aPacketsMissingFromCache = AtomicLong()

    /**
     * The bitrate.
     */
    private var mBitrate = RateStatistics(interval.toInt())

    /**
     * The packet rate.
     */
    var mPacketRate = RateStatistics(interval.toInt(), 1000f)

    /**
     * Notifies this instance that a packet with a given length was processed (i.e. sent or received).
     *
     * @param length the length of the packet.
     * @param now the time at which the packet was processed (passed in order
     * to avoid calling [System.currentTimeMillis]).
     * @param rtp whether the packet is an RTP or RTCP packet.
     */
    protected open fun packetProcessed(length: Int, now: Long, rtp: Boolean) {
        aBytes.addAndGet(length.toLong())
        mBitrate.update(length, now)

        // Don't count RTCP packets towards the packet rate since it is used to
        // calculate the number of lost packets.
        if (rtp) {
            aPackets.addAndGet(1)
            mPacketRate.update(1, now)
        }
    }

    /**
     * The last jitter (in milliseconds).
     */
    override var jitter = TrackStats.JITTER_UNSET

    /**
     * The total number of bytes.
     */
    override val bytes: Long
        get() = aBytes.get()

    /**
     * The total number of RTP packets. This excludes RTCP packets, because
     * the value is used to calculate the number of lost RTP packets.
     */
    override val packets: Long
        get() = aPackets.get()

    /**
     * The RTT computed with the RTCP feedback (cf. RFC3550, section 6.4.1,
     * subsection "delay since last SR (DLSR): 32 bits"). `-1` if the RTT
     * has not been computed yet. Otherwise, the RTT in milliseconds.
     */
    override var rtt = -1L

    /**
     * The bitrate.
     */
    override val bitrate: Long
        get() = mBitrate.getRate(System.currentTimeMillis())

    /**
     * The packet rate.
     */
    override val packetRate: Long
        get() = aPackets.get()

    /**
     * {@inheritDoc}
     */
    override val currentBytes: Long
        get() = mBitrate.accumulatedCount

    /**
     * {@inheritDoc}
     */
    override val currentPackets: Long
        get() = mPacketRate.accumulatedCount

    /**
     * The number of packets for which retransmission was requested, but
     * they were missing from the cache.
     */
    override val packetsMissingFromCache: Long
        get() = aPacketsMissingFromCache.get()

    /**
     * Number of bytes retransmitted.
     */
    override val bytesRetransmitted: Long
        get() = aBytesRetransmitted.get()

    /**
     * Number of bytes for packets which were requested and found in the
     * cache, but were intentionally not retransmitted.
     */
    override val bytesNotRetransmitted: Long
        get() = aBytesNotRetransmitted.get()

    /**
     * Number of packets retransmitted.
     */
    override val packetsRetransmitted: Long
        get() = aPacketsRetransmitted.get()

    /**
     * Number of packets which were requested and found in the cache, but
     * were intentionally not retransmitted.
     */
    override val packetsNotRetransmitted: Long
        get() = aPacketsNotRetransmitted.get()

    /**
     * Notifies this instance that an RTP packet with a given length was not
     * retransmitted (that is, the remote endpoint requested it,
     * and it was found in the local cache, but it was not retransmitted).
     *
     * @param length the length in bytes of the packet.
     */
    fun rtpPacketRetransmitted(length: Long) {
        aPacketsRetransmitted.incrementAndGet()
        aBytesRetransmitted.addAndGet(length)
    }

    /**
     * Notifies this instance that an RTP packet with a given length was retransmitted.
     *
     * @param length the length in bytes of the packet.
     */
    fun rtpPacketNotRetransmitted(length: Long) {
        aPacketsNotRetransmitted.incrementAndGet()
        aBytesNotRetransmitted.addAndGet(length)
    }

    /**
     * Notifies this instance that the remote endpoint requested retransmission
     * of a packet, and it was not found in the local cache.
     */
    fun rtpPacketCacheMiss() {
        aPacketsMissingFromCache.incrementAndGet()
    }
}