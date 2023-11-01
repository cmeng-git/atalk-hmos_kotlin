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
package org.atalk.service.neomedia.stats

/**
 * Basic statistics for a single "stream". A stream can be defined either as
 * the packets with a particular SSRC, or all packets of a
 * [org.atalk.service.neomedia.MediaStream], or something else.
 *
 * This class does not make a distinction between packets sent or received.
 *
 * @author Damian Minkov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
interface TrackStats {
    /**
     * @return the SSRC associated with this [TrackStats].
     */
    val sSRC: Long

    /**
     * @return the jitter in milliseconds.
     */
    val jitter: Double

    /**
     * @return the total number of bytes.
     */
    val bytes: Long

    /**
     * @return the total number of packets.
     */
    val packets: Long

    /**
     * @return the round trip time in milliseconds.
     */
    val rtt: Long

    /**
     * @return the current bitrate in bits per second.
     */
    val bitrate: Long

    /**
     * @return the current packet rate in packets per second.
     */
    val packetRate: Long

    /**
     * @return the number of bytes in the last interval.
     */
    val currentBytes: Long

    /**
     * @return the number of packets in the last interval.
     */
    val currentPackets: Long

    /**
     * @return the interval length in milliseconds over which
     * [.getCurrentBytes] and [.getCurrentPackets] operate.
     */
    val interval: Long

    /**
     * @return an estimate for the recent loss rate.
     */
    val lossRate: Double

    /**
     * Gets the number of packets for which retransmission was requested,
     * but they were missing from the cache.
     * @return the number of packets for which retransmission was requested,
     * but they were missing from the cache.
     */
    val packetsMissingFromCache: Long

    /**
     * Gets the number of bytes retransmitted.
     *
     * @return the number of bytes retransmitted.
     */
    val bytesRetransmitted: Long

    /**
     * Gets the number of bytes for packets which were requested and found
     * in the cache, but were intentionally not retransmitted.
     *
     * @return the number of bytes for packets which were requested and
     * found in the cache, but were intentionally not retransmitted.
     */
    val bytesNotRetransmitted: Long

    /**
     * Gets the number of packets retransmitted.
     *
     * @return the number of packets retransmitted.
     */
    val packetsRetransmitted: Long

    /**
     * Gets the number of packets which were requested and found in the
     * cache, but were intentionally not retransmitted.
     *
     * @return the number of packets which were requested and found in the
     * cache, but were intentionally not retransmitted.
     */
    val packetsNotRetransmitted: Long

    companion object {
        /**
         * The value that indicates that no values has been set for the jitter
         * field.
         */
        const val JITTER_UNSET = Double.MIN_VALUE
    }
}