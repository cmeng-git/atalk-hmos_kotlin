package org.atalk.impl.neomedia.stats

import org.atalk.impl.neomedia.MediaStreamImpl
import org.atalk.impl.neomedia.MediaStreamStatsImpl
import org.atalk.service.neomedia.stats.MediaStreamStats2
import org.atalk.service.neomedia.stats.ReceiveTrackStats
import org.atalk.service.neomedia.stats.SendTrackStats
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class MediaStreamStats2Impl
/**
 * Initializes a new [MediaStreamStats2Impl] instance.
 */
(mediaStream: MediaStreamImpl?) : MediaStreamStatsImpl(mediaStream!!), MediaStreamStats2 {
    /**
     * Hold per-SSRC statistics for received streams.
     */
    private val receiveSsrcStats = ConcurrentHashMap<Long, ReceiveTrackStatsImpl?>()

    /**
     * Hold per-SSRC statistics for sent streams.
     */
    private val sendSsrcStats = ConcurrentHashMap<Long, SendTrackStatsImpl?>()

    /**
     * Hold per-SSRC time after which we can clean them.
     */
    private val sendSsrcStatsToClean = ConcurrentHashMap<Long, Long>()

    /**
     * Global (aggregated) statistics for received streams.
     */
    override val receiveStats = AggregateReceiveTrackStats(INTERVAL, receiveSsrcStats)

    /**
     * Global (aggregated) statistics for sent streams.
     */
    override val sendStats = AggregateSendTrackStats(INTERVAL, sendSsrcStats)

    /**
     * Notifies this instance that an RTP packet with a particular SSRC, sequence number and length was received.
     *
     * @param ssrc the SSRC of the packet.
     * @param seq the RTP sequence number of the packet.
     * @param length the length in bytes of the packet.
     */
    fun rtpPacketReceived(ssrc: Long, seq: Int, length: Int) {
        synchronized(receiveStats) {
            getReceiveStats(ssrc)!!.rtpPacketReceived(seq, length)
            receiveStats.packetProcessed(length, System.currentTimeMillis(), true)
        }
    }

    /**
     * Notifies this instance that an RTP packet with a given SSRC and a given length was retransmitted.
     *
     * @param ssrc the SSRC of the packet.
     * @param length the length in bytes of the packet.
     */
    fun rtpPacketRetransmitted(ssrc: Long, length: Long) {
        getSendStats(ssrc).rtpPacketRetransmitted(length)
        sendStats.rtpPacketRetransmitted(length)
    }

    /**
     * Notifies this instance that an RTP packet with a given SSRC and a given
     * length was not retransmitted (that is, the remote endpoint requested it,
     * and it was found in the local cache, but it was not retransmitted).
     *
     * @param ssrc the SSRC of the packet.
     * @param length the length in bytes of the packet.
     */
    fun rtpPacketNotRetransmitted(ssrc: Long, length: Long) {
        getSendStats(ssrc).rtpPacketNotRetransmitted(length)
        sendStats.rtpPacketNotRetransmitted(length)
    }

    /**
     * Notifies this instance that the remote endpoint requested retransmission
     * of a packet with a given SSRC, and it was not found in the local cache.
     *
     * @param ssrc the SSRC of the requested packet.
     */
    fun rtpPacketCacheMiss(ssrc: Long) {
        getSendStats(ssrc).rtpPacketCacheMiss()
        sendStats.rtpPacketCacheMiss()
    }

    /**
     * Notifies this instance that an RTP packet with a particular SSRC,
     * sequence number and length was sent (or is about to be sent).
     *
     * @param ssrc the SSRC of the packet.
     * @param seq the RTP sequence number of the packet.
     * @param length the length in bytes of the packet.
     * @param skipStats whether to skip this packet.
     */
    fun rtpPacketSent(ssrc: Long, seq: Int, length: Int, skipStats: Boolean) {
        if (skipStats) {
            return
        }
        synchronized(sendStats) {
            getSendStats(ssrc).rtpPacketSent(seq, length)
            sendStats.packetProcessed(length, System.currentTimeMillis(), true)
        }
    }

    /**
     * Notifies this instance that an RTCP Receiver Report packet with a
     * particular SSRC and the given values for total number of lost packets
     * and extended highest sequence number was received.
     *
     * @param ssrc the SSRC of the packet.
     * @param fractionLost the value of the "fraction lost" field.
     */
    fun rtcpReceiverReportReceived(ssrc: Long, fractionLost: Int) {
        synchronized(sendStats) { getSendStats(ssrc).rtcpReceiverReportReceived(fractionLost) }
        cleanSendStatsOld()
    }

    /**
     * Clean old send stats.
     */
    private fun cleanSendStatsOld() {
        if (sendSsrcStatsToClean.isEmpty()) {
            return
        }
        val now = System.currentTimeMillis()
        sendSsrcStatsToClean.entries.stream().forEach { (key, value): Map.Entry<Long, Long> ->
            if (value > now) {
                sendSsrcStats.remove(key)
                sendSsrcStatsToClean.remove(key)
            }
        }
    }

    /**
     * Notifies this instance that an RTCP packet with a particular SSRC and particular length was received.
     *
     * @param ssrc the SSRC of the packet.
     * @param length the length in bytes of the packet.
     */
    fun rtcpPacketReceived(ssrc: Long, length: Int) {
        synchronized(receiveStats) {
            getReceiveStats(ssrc)!!.rtcpPacketReceived(length)
            receiveStats.packetProcessed(length, System.currentTimeMillis(), false)
        }
    }

    /**
     * Notifies this instance that an RTCP packet with a particular SSRC and
     * particular length was sent (or is about to be sent).
     *
     * @param ssrc the SSRC of the packet.
     * @param length the length in bytes of the packet.
     */
    fun rtcpPacketSent(ssrc: Long, length: Int) {
        synchronized(sendStats) {
            getSendStats(ssrc).rtcpPacketSent(length)
            sendStats.packetProcessed(length, System.currentTimeMillis(), false)
        }
    }

    /**
     * Notifies this instance of a new value for the RTP jitter of the stream in a particular direction.
     *
     * @param ssrc the SSRC of the stream for which the jitter changed.
     * @param direction whether the jitter is for a received or sent stream.
     * @param jitter the new jitter value in milliseconds.
     */
    fun updateJitter(ssrc: Long, direction: StreamDirection, jitter: Double) {
        // Maintain a jitter value for the entire MediaStream, and for
        // the individual SSRCs(if available)
        if (direction === StreamDirection.DOWNLOAD) {
            receiveStats.jitter = jitter

            // update jitter for known stats
            val receiveSsrcStat = receiveSsrcStats[ssrc]
            if (receiveSsrcStat != null) {
                receiveSsrcStat.jitter = jitter
            }
        } else if (direction === StreamDirection.UPLOAD) {
            sendStats.jitter = jitter

            // update jitter for known stats
            val sendSsrcStat = sendSsrcStats[ssrc]
            if (sendSsrcStat != null) {
                sendSsrcStat.jitter = jitter
            }
        }
    }

    /**
     * Notifies this instance of a new value for the round trip time measured for the associated stream.
     *
     * @param ssrc the SSRC of the stream for which the jitter changed.
     * @param rtt the new measured RTT in milliseconds.
     */
    fun updateRtt(ssrc: Long, rtt: Long) {
        // RTT value for the entire MediaStream
        receiveStats.rtt = rtt
        sendStats.rtt = rtt

        // RTT value for individual SSRCs
        // skip invalid ssrc
        if (ssrc < 0) return

        // directly get the receive/send stats to avoid creating unnecessary
        // stats
        val receiveSsrcStat = receiveSsrcStats[ssrc]
        if (receiveSsrcStat != null) receiveSsrcStat.rtt = rtt
        val sendSsrcStat = sendSsrcStats[ssrc]
        if (sendSsrcStat != null) sendSsrcStat.rtt = rtt
    }

    /**
     * {@inheritDoc}
     */
    override fun getReceiveStats(ssrc: Long): ReceiveTrackStatsImpl? {
        var ssrc_ = ssrc
        if (ssrc_ < 0) {
            Timber.e("No received stats for an invalid SSRC: %s", ssrc_)
            // We don't want to lose the data (and trigger an NPE), but at
            // least we collect all invalid SSRC under the value of -1;
            ssrc_ = -1
        }
        var stats = receiveSsrcStats[ssrc_]
        if (stats == null) {
            synchronized(receiveSsrcStats) {
                stats = receiveSsrcStats[ssrc_]
                if (stats == null) {
                    stats = ReceiveTrackStatsImpl(INTERVAL, ssrc_)
                    receiveSsrcStats[ssrc_] = stats
                }
            }
        }
        return stats
    }

    /**
     * {@inheritDoc}
     */
    override fun getSendStats(ssrc: Long): SendTrackStatsImpl {
        var ssrc_ = ssrc
        if (ssrc_ < 0) {
            Timber.e("No send stats for an invalid SSRC: %s", ssrc_)
            // We don't want to lose the data (and trigger an NPE), but at
            // least we collect all invalid SSRC under the value of -1;
            ssrc_ = -1
        }
        var stats = sendSsrcStats[ssrc_]
        if (stats == null) {
            synchronized(sendSsrcStats) {
                stats = sendSsrcStats[ssrc_]
                if (stats == null) {
                    stats = SendTrackStatsImpl(INTERVAL, ssrc_)
                    sendSsrcStats[ssrc_] = stats
                }
            }
        }
        return stats!!
    }

    /**
     * {@inheritDoc}
     */
    override val allSendStats: Collection<SendTrackStats?>
        get() = sendSsrcStats.values

    /**
     * {@inheritDoc}
     */
    override val allReceiveStats: Collection<ReceiveTrackStats?>
        get() = receiveSsrcStats.values

    /**
     * Clears ssrc from receiver stats.
     *
     * @param ssrc the ssrc to process.
     */
    fun removeReceiveSsrc(ssrc: Long) {
        receiveSsrcStats.remove(ssrc)
    }

    /**
     * Schedules ssrc for clear from the send stats per ssrc.
     *
     * @param ssrc the ssrc to clear.
     */
    override fun clearSendSsrc(ssrc: Long) {
        sendSsrcStatsToClean[ssrc] = System.currentTimeMillis() + INTERVAL
    }

    /**
     * An TrackStats implementation which aggregates values for a collection of TrackStats instances.
     */
    abstract inner class AggregateTrackStats<T>
    /**
     * Initializes a new [AggregateTrackStats] instance.
     *
     * @param interval the interval in milliseconds over which average values will be calculated.
     * @param children a reference to the map which holds the statistics to aggregate.
     */

    (interval: Int,
        /**
         * The collection of TrackStats for which this instance aggregates.
         */
        protected val children: Map<Long, T?>) : AbstractTrackStats(interval.toLong(), -1) {

        /**
         * {@inheritDoc}
         */
        public override fun packetProcessed(length: Int, now: Long, rtp: Boolean) {
            // A hack to make RTCP packets count towards the aggregate packet rate.
            super.packetProcessed(length, now, true)
        }
    }

    /**
     * An [SendTrackStats] implementation which aggregates values for
     * a collection of [SendTrackStats] instances.
     */
    inner class AggregateSendTrackStats
    /**
     * Initializes a new [AggregateTrackStats] instance.
     *
     * @param interval the interval in milliseconds over which average values will be calculated.
     * @param children a reference to the map which holds the statistics to aggregate.
     */
    (interval: Int, children: Map<Long, SendTrackStats?>) : AggregateTrackStats<SendTrackStats?>(interval, children), SendTrackStats {
        /**
         * {@inheritDoc}
         */
        override val lossRate: Double
            get() {
                var sum = 0.0
                var count = 0
                for (child in children.values) {
                    val fractionLoss = child!!.lossRate
                    if (fractionLoss >= 0) {
                        sum += fractionLoss
                        count++
                    }
                }
                return if (count != 0) (sum / count) else 0.0
            }

        /**
         * {@inheritDoc}
         */
        override val highestSent: Int
            get() = -1
    }

    /**
     * An [ReceiveTrackStats] implementation which aggregates values
     * for a collection of [ReceiveTrackStats] instances.
     */
    inner class AggregateReceiveTrackStats
    /**
     * Initializes a new [AggregateTrackStats] instance.
     *
     * @param interval the interval in milliseconds over which average values will be calculated.
     * @param children a reference to the map which holds the statistics to
     */
    (interval: Int, children: Map<Long, ReceiveTrackStats?>) : AggregateTrackStats<ReceiveTrackStats?>(interval, children), ReceiveTrackStats {
        /**
         * {@inheritDoc}
         */
        override val packetsLost: Long
            get() {
                var lost = 0L
                for (child in children.values) {
                    lost += child!!.packetsLost
                }
                return lost
            }

        /**
         * {@inheritDoc}
         */
        override val currentPackets: Long
            get() {
                var packets = 0L
                for (child in children.values) {
                    packets += child!!.currentPackets
                }
                return packets
            }

        /**
         * {@inheritDoc}
         */
        override val currentPacketsLost: Long
            get() {
                var packetsLost = 0L
                for (child in children.values) {
                    packetsLost += child!!.currentPacketsLost
                }
                return packetsLost
            }

        // This is not thread safe and the counters might change
        // between the two function calls below, but the result would
        // be just a wrong value for the packet loss rate, and likely
        // just off by a little bit.
        /**
         * {@inheritDoc}
         *
         * @return the loss rate in the last interval.
         */
        override val lossRate: Double
            get() {
                var lost = 0L
                var expected = 0L
                for (child in children.values) {
                    // This is not thread safe and the counters might change
                    // between the two function calls below, but the result would
                    // be just a wrong value for the packet loss rate, and likely
                    // just off by a little bit.
                    val childLost = child!!.currentPacketsLost
                    expected += childLost + child.currentPackets
                    lost += childLost
                }
                return if (expected == 0L) 0.0 else (lost / expected).toDouble()
            }
    }

    companion object {
        /**
         * Window over which rates will be computed.
         */
        private const val INTERVAL = 1000
    }
}