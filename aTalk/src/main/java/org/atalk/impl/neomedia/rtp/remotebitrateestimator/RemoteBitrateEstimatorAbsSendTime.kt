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
package org.atalk.impl.neomedia.rtp.remotebitrateestimator

import org.atalk.service.neomedia.rtp.RemoteBitrateEstimator
import org.atalk.util.logging.DiagnosticContext
import org.atalk.util.logging.TimeSeriesLogger
import org.ice4j.util.RateStatistics
import java.util.*

/**
 * webrtc.org abs_send_time implementation as of June 26, 2017.
 * commit ID: 23fbd2aa2c81d065b84d17b09b747e75672e1159
 *
 * @author Julian Chukwu
 * @author George Politis
 * @author Eng Chong Meng
 */
class RemoteBitrateEstimatorAbsSendTime(
        /**
         * The observer to notify on bitrate estimation changes.
         */
        private val observer: RemoteBitrateObserver?,
        /**
         * The [DiagnosticContext] of this instance.
         */
        private val diagnosticContext: DiagnosticContext) : RemoteBitrateEstimator {
    /**
     * Reduces the effects of allocations and garbage collection of the method `incomingPacket`.
     */
    private val deltas = LongArray(3)

    /**
     * Reduces the effects of allocations and garbage collection of the method
     * [.incomingPacketInfo]} by promoting the
     * `RateControlInput` instance from a local variable to a field and
     * reusing the same instance across method invocations. (Consequently, the
     * default values used to initialize the field are of no importance because
     * they will be overwritten before they are actually used.)
     */
    private val input = RateControlInput(BandwidthUsage.kBwNormal, 0L, 0.0)
    /**
     * {@inheritDoc}
     */
    /**
     * The set of synchronization source identifiers (SSRCs) currently being
     * received. Represents an unmodifiable copy/snapshot of the current keys of
     * [.ssrcsMap] suitable for public access and introduced for
     * the purposes of reducing the number of allocations and the effects of garbage collection.
     */
    override var ssrcs: Collection<Long>? = Collections.unmodifiableList(emptyList<Long>())
        private set

    /**
     * A map of SSRCs -> time first seen (in millis).
     */
    private val ssrcsMap: MutableMap<Long, Long> = TreeMap()

    /**
     * The time (in millis) when we saw the first packet. Useful to determine the probing period.
     */
    private var firstPacketTimeMs: Long

    /**
     * Keeps track of the last time (in millis) that we updated the bitrate estimate.
     */
    private var lastUpdateMs: Long

    /**
     * The rate control implementation based on additive increases of bitrate
     * when no over-use is detected and multiplicative decreases when over-uses are detected.
     */
    private val remoteRate = AimdRateControl(diagnosticContext)

    /**
     * Holds the [InterArrival], [OveruseEstimator] and [OveruseDetector] instances of this RBE.
     */
    private var detector: Detector? = null

    /**
     * Keeps track of how much data we're receiving.
     */
    private var incomingBitrate: RateStatistics

    /**
     * Determines whether or not the incoming bitrate is initialized or not.
     */
    private var incomingBitrateInitialized: Boolean

    init {
        incomingBitrate = RateStatistics(RemoteBitrateEstimator.kBitrateWindowMs, RemoteBitrateEstimator.kBitrateScale.toFloat())
        incomingBitrateInitialized = false
        firstPacketTimeMs = -1
        lastUpdateMs = -1
    }

    /**
     * Notifies this instance of an incoming packet.
     *
     * @param arrivalTimeMs the arrival time of the packet in millis.
     * @param timestamp the send time of the packet in AST format (24 bits, 6.18 fixed point).
     * @param payloadSize the payload size of the packet.
     * @param ssrc the SSRC of the packet.
     */
    override fun incomingPacketInfo(arrivalTimeMs: Long, timestamp: Long, payloadSize: Int, ssrc: Long) {
        // Shift up send time to use the full 32 bits that inter_arrival works with, so wrapping works properly.
        val timestamp_ = timestamp shl kAbsSendTimeInterArrivalUpshift

        // Convert the expanded AST (32 bits, 6.26 fixed point) to millis.
        val sendTimeMs = (timestamp_ * kTimestampToMs).toLong()

        // XXX The arrival time should be the earliest we've seen this packet,
        // not now. In our code however, we don't have access to the arrival time.
        val nowMs = System.currentTimeMillis()
        if (timeSeriesLogger.isTraceEnabled) {
            timeSeriesLogger.trace(diagnosticContext
                    .makeTimeSeriesPoint("in_pkt", nowMs)
                    .addField("rbe_id", hashCode())
                    .addField("recv_ts_ms", arrivalTimeMs)
                    .addField("send_ts_ms", sendTimeMs)
                    .addField("pkt_sz_bytes", payloadSize)
                    .addField("ssrc", ssrc))
        }

        // should be broken out from  here.
        // Check if incoming bitrate estimate is valid, and if it needs to be reset.
        val incomingBitrate_ = incomingBitrate.getRate(arrivalTimeMs)
        if (incomingBitrate_ != 0L) {
            incomingBitrateInitialized = true
        } else if (incomingBitrateInitialized) {
            // Incoming bitrate had a previous valid value, but now not
            // enough data point are left within the current window.
            // Reset incoming bitrate estimator so that the window
            // size will only contain new data points.
            incomingBitrate = RateStatistics(RemoteBitrateEstimator.kBitrateWindowMs, RemoteBitrateEstimator.kBitrateScale.toFloat())
            incomingBitrateInitialized = false
        }
        incomingBitrate.update(payloadSize, arrivalTimeMs)
        if (firstPacketTimeMs == -1L) {
            firstPacketTimeMs = nowMs
        }
        var updateEstimate = false
        var targetBitrateBps = 0L

        synchronized(this) {
            timeoutStreams(nowMs)
            ssrcsMap[ssrc] = nowMs
            if (!ssrcs!!.contains(ssrc)) {
                ssrcs = Collections.unmodifiableList(ArrayList(ssrcsMap.keys))
            }
            val deltas = deltas

            /* long timestampDelta */
            deltas[0] = 0
            /* long timeDelta */
            deltas[1] = 0
            /* int sizeDelta */
            deltas[2] = 0
            if (detector == null) {
                detector = Detector(OverUseDetectorOptions(), true)
            }
            if (detector!!.interArrival.computeDeltas(timestamp_, arrivalTimeMs, payloadSize, deltas, nowMs)) {
                val tsDeltaMs = deltas[0] * kTimestampToMs
                detector!!.estimator.update( /* timeDelta */
                        deltas[1],  /* timestampDelta */
                        tsDeltaMs, deltas[2].toInt(),
                        detector!!.detector.state, nowMs)
                detector!!.detector.detect(
                        detector!!.estimator.offset, tsDeltaMs,
                        detector!!.estimator.numOfDeltas, arrivalTimeMs)
            }

            // Check if it's time for a periodic update or if we
            // should update because of an over-use.
            if (lastUpdateMs == -1L
                    || nowMs - lastUpdateMs > remoteRate.feedBackInterval) {
                updateEstimate = true
            } else if (detector!!.detector.state == BandwidthUsage.kBwOverusing) {
                val incomingRate_ = incomingBitrate.getRate(arrivalTimeMs)
                if (incomingRate_ > 0 && remoteRate.isTimeToReduceFurther(nowMs, incomingBitrate_)) {
                    updateEstimate = true
                }
            }
            if (updateEstimate) {
                // The first overuse should immediately trigger a new estimate.
                // We also have to update the estimate immediately if we are
                // overusing and the target bitrate is too high compared to
                // what we are receiving.
                input.bwState = detector!!.detector.state
                input.incomingBitRate = incomingBitrate.getRate(arrivalTimeMs)
                input.noiseVar = detector!!.estimator.varNoise
                remoteRate.update(input, nowMs)
                targetBitrateBps = remoteRate.updateBandwidthEstimate(nowMs)
                updateEstimate = remoteRate.isValidEstimate
            }
        }
        if (updateEstimate) {
            lastUpdateMs = nowMs
            observer?.onReceiveBitrateChanged(ssrcs!!, targetBitrateBps)
        }
    }

    /**
     * Timeouts SSRCs that have not received any data for kTimestampGroupLengthMs millis.
     *
     * @param nowMs the current time in millis.
     */
    @Synchronized
    private fun timeoutStreams(nowMs: Long) {
        var removed = false
        val itr = ssrcsMap.entries.iterator()
        while (itr.hasNext()) {
            val (_, value) = itr.next()
            if (nowMs - value > RemoteBitrateEstimator.kStreamTimeOutMs) {
                removed = true
                itr.remove()
            }
        }
        if (removed) {
            ssrcs = Collections.unmodifiableCollection(ssrcsMap.keys)
        }
        if (detector != null && ssrcsMap.isEmpty()) {
            // We can't update the estimate if we don't have any active streams.
            detector = null
            // We deliberately don't reset the first_packet_time_ms_
            // here for now since we only probe for bandwidth in the beginning of a call right now.
        }
    }

    /**
     * {@inheritDoc}
     */
    @Synchronized
    override fun onRttUpdate(avgRttMs: Long, maxRttMs: Long) {
        remoteRate.setRtt(avgRttMs)
    }

    /**
     * {@inheritDoc}
     */
    @get:Synchronized
    override val latestEstimate: Long
        get() {
            if (!remoteRate.isValidEstimate) {
                return -1
            }
            val bitrateBps = if (ssrcsMap.isEmpty()) {
                0L
            } else {
                remoteRate.latestEstimate
            }
            return bitrateBps
        }

    /**
     * {@inheritDoc}
     */
    @Synchronized
    override fun removeStream(ssrc: Long) {
        if (ssrcsMap.remove(ssrc) != null) {
            ssrcs = Collections.unmodifiableCollection(ssrcsMap.keys)
        }
    }

    /**
     * {@inheritDoc}
     */
    @Synchronized
    override fun setMinBitrate(minBitrateBps: Int) {
        // Called from both the configuration thread and the network thread.
        // Shouldn't be called from the network thread in the future.
        remoteRate.setMinBitrate(minBitrateBps.toLong())
    }

    /**
     * Holds the [InterArrival], [OveruseEstimator] and
     * [OveruseDetector] instances that estimate the remote bitrate of a stream.
     */
    private inner class Detector(options: OverUseDetectorOptions, enableBurstGrouping: Boolean) {
        /**
         * Computes the send-time and recv-time deltas to feed to the estimator.
         */
        val interArrival: InterArrival

        /**
         * The Kalman filter implementation that estimates the jitter.
         */
        val estimator: OveruseEstimator

        /**
         * The overuse detector that compares the jitter to an adaptive threshold.
         */
        val detector: OveruseDetector

        /**
         * Ctor.
         *
         * options the over-use detector options.
         * enableBurstGrouping true to activate burst detection, false otherwise
         */
        init {
            interArrival = InterArrival(
                    kTimestampGroupLengthTicks, kTimestampToMs, enableBurstGrouping, diagnosticContext)
            estimator = OveruseEstimator(options, diagnosticContext)
            this.detector = OveruseDetector(options, diagnosticContext)
        }
    }

    companion object {
        /**
         * The [TimeSeriesLogger] to be used by this instance to print time series.
         */
        private val timeSeriesLogger = TimeSeriesLogger.getTimeSeriesLogger(RemoteBitrateEstimatorAbsSendTime::class.java)

        /**
         * Defines the number of digits in the AST representation (24 bits, 6.18 fixed point) after the radix.
         */
        private const val kAbsSendTimeFraction = 18

        /**
         * Defines the upshift (left bit-shift) to apply to AST (24 bits, 6.18 fixed
         * point) to make it inter-arrival compatible (expanded AST, 32 bits, 6.26 fixed point).
         */
        private const val kAbsSendTimeInterArrivalUpshift = 8

        /**
         * This is used in the [InterArrival] computations. In this estimator
         * a timestamp group is defined as all packets with a timestamp which are at
         * most 5ms older than the first timestamp in that group.
         */
        private const val kTimestampGroupLengthMs = 5

        /**
         * Defines the number of digits in the expanded AST representation (32 bits, 6.26 fixed point) after the radix.
         */
        private const val kInterArrivalShift = kAbsSendTimeFraction + kAbsSendTimeInterArrivalUpshift

        /**
         * Converts the [.kTimestampGroupLengthMs] into "ticks" for use with the [InterArrival].
         */
        private const val kTimestampGroupLengthTicks = ((kTimestampGroupLengthMs shl kInterArrivalShift) / 1000).toLong()

        /**
         * Defines the expanded AST (32 bits) to millis conversion rate. Units are ms per timestamp
         */
        private const val kTimestampToMs = 1000.0 / (1 shl kInterArrivalShift)

        /**
         * Converts rtp timestamps to 24bit timestamp equivalence
         *
         * @param timeMs is the RTP timestamp e.g System.currentTimeMillis().
         * @return time stamp representation in 24 bit representation.
         */
        fun convertMsTo24Bits(timeMs: Long): Long {
            return ((timeMs shl kAbsSendTimeFraction) + 500) / 1000 and 0x00FFFFFFL
        }
    }
}