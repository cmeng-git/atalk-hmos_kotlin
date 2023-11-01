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
package org.atalk.impl.neomedia.rtp.sendsidebandwidthestimation

import org.atalk.impl.neomedia.MediaStreamImpl
import org.atalk.impl.neomedia.rtcp.RTCPREMBPacket
import org.atalk.impl.neomedia.rtp.RTCPPacketListenerAdapter
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.rtp.BandwidthEstimator
import org.atalk.util.IntSummaryStatistics
import org.atalk.util.LongSummaryStatistics
import org.atalk.util.logging.DiagnosticContext
import org.atalk.util.logging.TimeSeriesLogger
import timber.log.Timber
import java.util.*
import kotlin.math.max

/**
 * Implements the send-side bandwidth estimation described in
 * https://tools.ietf.org/html/draft-ietf-rmcat-gcc-01 Heavily based on code from webrtc.org
 * (send_side_bandwidth_estimation.cc, commit ID 7ad9e661f8a035d49d049ccdb87c77ae8ecdfa35).
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class SendSideBandwidthEstimation(stream: MediaStreamImpl, startBitrate: Long) : RTCPPacketListenerAdapter(), BandwidthEstimator {
    /**
     * send_side_bandwidth_estimation.h
     */
    private var low_loss_threshold_ = 0f

    /**
     * send_side_bandwidth_estimation.h
     */
    private var high_loss_threshold_ = 0f

    /**
     * send_side_bandwidth_estimation.h
     */
    private var bitrate_threshold_bps_ = 0

    /**
     * send_side_bandwidth_estimation.h
     */
    private var first_report_time_ms_ = -1L

    /**
     * send_side_bandwidth_estimation.h
     */
    private var lost_packets_since_last_loss_update_Q8_ = 0

    /**
     * send_side_bandwidth_estimation.h
     */
    private var expected_packets_since_last_loss_update_ = 0

    /**
     * send_side_bandwidth_estimation.h
     */
    private var has_decreased_since_last_fraction_loss_ = false
    /**
     * {@inheritDoc}
     */
    /**
     * send_side_bandwidth_estimation.h
     *
     * uint8_t last_fraction_loss_;
     */
    override var latestFractionLoss = 0
        private set

    /**
     * send_side_bandwidth_estimation.h
     */
    private var last_feedback_ms_ = -1L

    /**
     * send_side_bandwidth_estimation.h
     */
    private var last_packet_report_ms_ = -1L

    /**
     * send_side_bandwidth_estimation.h
     */
    private var last_timeout_ms_ = -1L

    /**
     * send_side_bandwidth_estimation.h
     */
    private val in_timeout_experiment_: Boolean

    /**
     * send_side_bandwidth_estimation.h
     */
    private var min_bitrate_configured_ = kDefaultMinBitrateBps

    /**
     * send_side_bandwidth_estimation.h
     */
    private var max_bitrate_configured_ = kDefaultMaxBitrateBps

    /**
     * send_side_bandwidth_estimation.h
     */
    private var time_last_decrease_ms_ = 0L
    /**
     * {@inheritDoc}
     */
    /**
     * send_side_bandwidth_estimation.h
     */
    override var latestREMB = 0L
        private set
    /**
     * {@inheritDoc}
     */
    /**
     * send_side_bandwidth_estimation.h
     */
    override var latestEstimate = 0L
        private set

    /**
     * send_side_bandwidth_estimation.h
     */
    private val min_bitrate_history_: Deque<Pair<Long>> = LinkedList()

    /**
     * The [DiagnosticContext] of this instance.
     */
    private val diagnosticContext: DiagnosticContext
    private val listeners: MutableList<BandwidthEstimator.Listener?> = LinkedList()

    /**
     * The [MediaStream] for this [SendSideBandwidthEstimation].
     */
    private val mediaStream: MediaStream

    /**
     * The instance that holds stats for this instance.
     */
    override val statistics = StatisticsImpl()

    init {
        mediaStream = stream
        diagnosticContext = stream.getDiagnosticContext()
        val lossExperimentProbability = cfg.getDouble(
                LOSS_EXPERIMENT_PROBABILITY_PNAME, kDefaultLossExperimentProbability.toDouble()).toFloat()
        if (kRandom.nextFloat() < lossExperimentProbability) {
            low_loss_threshold_ = cfg.getDouble(
                    LOW_LOSS_THRESHOLD_PNAME, kDefaultLowLossThreshold.toDouble()).toFloat()
            high_loss_threshold_ = cfg.getDouble(
                    HIGH_LOSS_THRESHOLD_PNAME, kDefaultHighLossThreshold.toDouble()).toFloat()
            bitrate_threshold_bps_ = 1000 * cfg.getInt(
                    BITRATE_THRESHOLD_KBPS_PNAME, kDefaultBitrateThresholdKbps)
        } else {
            low_loss_threshold_ = kDefaultLowLossThreshold
            high_loss_threshold_ = kDefaultHighLossThreshold
            bitrate_threshold_bps_ = 1000 * kDefaultBitrateThresholdKbps
        }
        val timeoutExperimentProbability = cfg.getDouble(
                TIMEOUT_EXPERIMENT_PROBABILITY_PNAME, kDefaultTimeoutExperimentProbability.toDouble()).toFloat()
        in_timeout_experiment_ = kRandom.nextFloat() < timeoutExperimentProbability
        setBitrate(startBitrate)
    }

    /**
     * bool SendSideBandwidthEstimation::IsInStartPhase(int64_t now_ms)
     */
    @Synchronized
    private fun isInStartPhase(now: Long): Boolean {
        return first_report_time_ms_ == -1L || now - first_report_time_ms_ < kStartPhaseMs
    }

    /**
     * int SendSideBandwidthEstimation::CapBitrateToThresholds
     */
    @Synchronized
    private fun capBitrateToThresholds(bRate: Long): Long {
        var bitrate = bRate
        if (latestREMB in 1 until bitrate) {
            bitrate = latestREMB
        }
        if (bitrate > max_bitrate_configured_) {
            bitrate = max_bitrate_configured_.toLong()
        }
        if (bitrate < min_bitrate_configured_) {
            bitrate = min_bitrate_configured_.toLong()
        }
        return bitrate
    }

    /**
     * void SendSideBandwidthEstimation::UpdateEstimate(int64_t now_ms)
     */
    @Synchronized
    fun updateEstimate(now: Long) {
        var bitrate = latestEstimate

        // We trust the REMB during the first 2 seconds if we haven't had any
        // packet loss reported, to allow startup bitrate probing.
        if (latestFractionLoss == 0 && isInStartPhase(now) && latestREMB > bitrate) {
            setBitrate(capBitrateToThresholds(latestREMB))
            min_bitrate_history_.clear()
            min_bitrate_history_.addLast(Pair(now, bitrate))
            return
        }
        updateMinHistory(now)
        if (last_packet_report_ms_ == -1L) {
            // No feedback received.
            latestEstimate = capBitrateToThresholds(latestEstimate)
            return
        }
        val time_since_packet_report_ms = now - last_packet_report_ms_
        val time_since_feedback_ms = now - last_feedback_ms_
        if (time_since_packet_report_ms < kPacketReportTimeoutIntervals * kFeedbackIntervalMs) {
            // We only care about loss above a given bitrate threshold.
            val loss = latestFractionLoss / 256.0f
            // We only make decisions based on loss when the bitrate is above a
            // threshold. This is a crude way of handling loss which is
            // uncorrelated to congestion.
            if (latestEstimate < bitrate_threshold_bps_ || loss <= low_loss_threshold_) {
                // Loss < 2%: Increase rate by 8% of the min bitrate in the last
                // kBweIncreaseIntervalMs.
                // Note that by remembering the bitrate over the last second one can
                // rampup up one second faster than if only allowed to start ramping
                // at 8% per second rate now. E.g.:
                // If sending a constant 100kbps it can rampup immediately to 108kbps
                // whenever a receiver report is received with lower packet loss.
                // If instead one would do: bitrate_ *= 1.08^(delta time), it would
                // take over one second since the lower packet loss to achieve 108kbps.
                bitrate = (min_bitrate_history_.first.second * 1.08 + 0.5).toLong()

                // Add 1 kbps extra, just to make sure that we do not get stuck
                // (gives a little extra increase at low rates, negligible at higher rates).
                bitrate += 1000
                statistics.update(now, false, LossRegion.LossFree)
            } else if (latestEstimate > bitrate_threshold_bps_) {
                if (loss <= high_loss_threshold_) {
                    // Loss between 2% - 10%: Do nothing.
                    statistics.update(now, false, LossRegion.LossLimited)
                } else {
                    // Loss > 10%: Limit the rate decreases to once a kBweDecreaseIntervalMs + rtt.
                    if (!has_decreased_since_last_fraction_loss_
                            && now - time_last_decrease_ms_ >= kBweDecreaseIntervalMs + rtt) {
                        time_last_decrease_ms_ = now

                        // Reduce rate:
                        //   newRate = rate * (1 - 0.5*lossRate);
                        //   where packetLoss = 256*lossRate;
                        bitrate = (bitrate * (512 - latestFractionLoss) / 512.0).toLong()
                        has_decreased_since_last_fraction_loss_ = true
                        statistics.update(now, false, LossRegion.LossDegraded)
                    }
                }
            }
        } else {
            statistics.update(now, true, null)
            if (time_since_feedback_ms >
                    kFeedbackTimeoutIntervals * kFeedbackIntervalMs
                    && (last_timeout_ms_ == -1L
                            || now - last_timeout_ms_ > kTimeoutIntervalMs)) {
                if (in_timeout_experiment_) {
                    latestEstimate *= 0.8.toLong()
                    // Reset accumulators since we've already acted on missing
                    // feedback and shouldn't to act again on these old lost
                    // packets.
                    lost_packets_since_last_loss_update_Q8_ = 0
                    expected_packets_since_last_loss_update_ = 0
                    last_timeout_ms_ = now
                }
            }
        }
        setBitrate(capBitrateToThresholds(bitrate))
    }

    /**
     * void SendSideBandwidthEstimation::UpdateReceiverBlock
     */
    @Synchronized
    fun updateReceiverBlock(fraction_lost: Long, number_of_packets: Long, now: Long) {
        last_feedback_ms_ = now
        if (first_report_time_ms_ == -1L) {
            first_report_time_ms_ = now
        }

        // Check sequence number diff and weight loss report
        if (number_of_packets > 0) {
            // Calculate number of lost packets.
            val num_lost_packets_Q8 = fraction_lost * number_of_packets
            // Accumulate reports.
            lost_packets_since_last_loss_update_Q8_ += num_lost_packets_Q8.toInt()
            expected_packets_since_last_loss_update_ += number_of_packets.toInt()

            // Don't generate a loss rate until it can be based on enough packets.
            if (expected_packets_since_last_loss_update_ < kLimitNumPackets) return
            has_decreased_since_last_fraction_loss_ = false
            latestFractionLoss = lost_packets_since_last_loss_update_Q8_ / expected_packets_since_last_loss_update_

            // Reset accumulators.
            lost_packets_since_last_loss_update_Q8_ = 0
            expected_packets_since_last_loss_update_ = 0
            last_packet_report_ms_ = now
            updateEstimate(now)
        }
    }

    /**
     * void SendSideBandwidthEstimation::UpdateMinHistory(int64_t now_ms)
     */
    @Synchronized
    private fun updateMinHistory(now_ms: Long) {
        // Remove old data points from history.
        // Since history precision is in ms, add one so it is able to increase
        // bitrate if it is off by as little as 0.5ms.
        while (!min_bitrate_history_.isEmpty()
                && now_ms - min_bitrate_history_.first.first + 1 > kBweIncreaseIntervalMs) {
            min_bitrate_history_.removeFirst()
        }

        // Typical minimum sliding-window algorithm: Pop values higher than current
        // bitrate before pushing it.
        while (!min_bitrate_history_.isEmpty()
                && latestEstimate <= min_bitrate_history_.last.second) {
            min_bitrate_history_.removeLast()
        }
        min_bitrate_history_.addLast(Pair(now_ms, latestEstimate))
    }

    /**
     * void SendSideBandwidthEstimation::UpdateReceiverEstimate
     */
    @Synchronized
    override fun updateReceiverEstimate(bandwidth: Long) {
        latestREMB = bandwidth
        setBitrate(capBitrateToThresholds(latestEstimate))
    }

    /**
     * void SendSideBandwidthEstimation::SetMinMaxBitrate
     */
    @Synchronized
    fun setMinMaxBitrate(min_bitrate: Int, max_bitrate: Int) {
        min_bitrate_configured_ = max(min_bitrate, kDefaultMinBitrateBps)
        max_bitrate_configured_ = if (max_bitrate > 0) {
            max(min_bitrate_configured_, max_bitrate)
        } else {
            kDefaultMaxBitrateBps
        }
    }

    /**
     * Sets the value of [.bitrate_].
     *
     * @param newValue the value to set
     */
    @Synchronized
    private fun setBitrate(newValue: Long) {
        val oldValue = latestEstimate
        latestEstimate = newValue
        if (oldValue != latestEstimate) {
            fireBandwidthEstimationChanged(oldValue, newValue)
        }
    }

    /**
     * {@inheritDoc}
     */
    @Synchronized
    override fun addListener(listener: BandwidthEstimator.Listener?) {
        listeners.add(listener)
    }

    /**
     * {@inheritDoc}
     */
    @Synchronized
    override fun removeListener(listener: BandwidthEstimator.Listener?) {
        listeners.remove(listener)
    }

    /**
     * {@inheritDoc}
     */
    override fun rembReceived(rembPacket: RTCPREMBPacket?) {
        updateReceiverEstimate(rembPacket!!.bitrate)
    }

    /**
     * Returns the last calculated RTT to the endpoint.
     *
     * @return the last calculated RTT to the endpoint.
     */
    @get:Synchronized
    private val rtt: Long
        get() {
            var rtt = mediaStream.mediaStreamStats.sendStats.rtt
            if (rtt < 0 || rtt > 1000) {
                Timber.w("RTT not calculated, or has a suspiciously high value (%d). Using the default of 100ms.", rtt)
                rtt = 100
            }
            return rtt
        }

    /**
     * Notifies registered listeners that the estimation of the available bandwidth has changed.
     *
     * @param oldValue the old value (in bps).
     * @param newValue the new value (in bps).
     */
    @Synchronized
    private fun fireBandwidthEstimationChanged(oldValue: Long, newValue: Long) {
        for (listener in listeners) {
            listener!!.bandwidthEstimationChanged(newValue)
        }
    }

    private inner class Pair<T>(var first: T, var second: T)

    /**
     * This class records statistics information about how much time we spend
     * in different loss-states (loss-free, loss-limited and loss-degraded).
     */
    inner class StatisticsImpl : BandwidthEstimator.Statistics {
        /**
         * The current state [LossRegion].
         */
        private var currentState: LossRegion? = null

        /**
         * Keeps the time (in millis) of the last transition (including a loop).
         */
        private var lastTransitionTimestampMs = -1L

        /**
         * The cumulative duration (in millis) of the current state
         * [.currentState] after having looped
         * [.currentStateConsecutiveVisits] times.
         */
        private var currentStateCumulativeDurationMs = 0L

        /**
         * The number of loops over the current state [.currentState].
         */
        private var currentStateConsecutiveVisits = 0

        /**
         * The bitrate when we entered the current state [.currentState].
         */
        private var currentStateStartBitrateBps = 0L

        /**
         * Computes the min/max/avg/sd of the bitrate while in
         * [.currentState].
         */
        private var currentStateBitrateStatistics = LongSummaryStatistics()

        /**
         * Computes the min/max/avg/sd of the loss while in
         * [.currentState].
         */
        private var currentStateLossStatistics = IntSummaryStatistics()

        /**
         * True when the fields of this class have changed from their default
         * value. The purpose is to avoid creating new IntSummaryStatistics and
         * LongSummaryStatistics when it's not needed.
         */
        private var isDirty = false

        /**
         * Computes the sum of the duration of the different states.
         */
        private val lossFreeMsStats = LongSummaryStatistics()
        private val lossDegradedMsStats = LongSummaryStatistics()
        private val lossLimitedMsStats = LongSummaryStatistics()
        override fun update(nowMs: Long) {
            synchronized(this@SendSideBandwidthEstimation) {
                val time_since_packet_report_ms = nowMs - last_packet_report_ms_
                val currentStateHasTimedOut = (time_since_packet_report_ms
                        < kPacketReportTimeoutIntervals * kFeedbackIntervalMs)
                update(nowMs, currentStateHasTimedOut, null)
            }
        }

        /**
         * Records a state transition and updates the statistics information.
         *
         * @param nowMs the time (in millis) of the transition.
         * @param currentStateHasTimedOut true if the current state has timed
         * out, i.e. we haven't received receiver reports "in a while".
         * @param nextState the that the bwe is transitioning to.
         */
        fun update(
                nowMs: Long, currentStateHasTimedOut: Boolean, nextState: LossRegion?) {
            synchronized(this@SendSideBandwidthEstimation) {
                if (lastTransitionTimestampMs > -1 && !currentStateHasTimedOut) {
                    isDirty = true
                    currentStateCumulativeDurationMs += nowMs - lastTransitionTimestampMs
                }
                lastTransitionTimestampMs = nowMs
                if (!currentStateHasTimedOut) {
                    isDirty = true
                    // If the current state has not timed out, then update the stats that we gather.
                    currentStateLossStatistics.accept(latestFractionLoss)
                    currentStateConsecutiveVisits++ // we start counting from 0.
                    if (currentState == nextState) {
                        currentStateBitrateStatistics.accept(latestEstimate)
                        return
                    }
                }
                if (currentState != null) {
                    // This is not a loop, we're transitioning to another state.
                    // Record how much time we've spent on this state, how many
                    // times we've looped through it and what was the impact on the bitrate.
                    when (currentState) {
                        LossRegion.LossDegraded -> lossDegradedMsStats.accept(
                                currentStateCumulativeDurationMs)
                        LossRegion.LossFree -> lossFreeMsStats.accept(currentStateCumulativeDurationMs)
                        LossRegion.LossLimited -> lossLimitedMsStats.accept(
                                currentStateCumulativeDurationMs)
                        else -> {}
                    }
                    if (timeSeriesLogger.isTraceEnabled) {
                        timeSeriesLogger.trace(diagnosticContext
                                .makeTimeSeriesPoint("loss_estimate")
                                .addField("state", currentState!!.name)
                                .addField("max_loss",
                                        currentStateLossStatistics.max / 256.0f)
                                .addField("min_loss",
                                        currentStateLossStatistics.min / 256.0f)
                                .addField("avg_loss",
                                        currentStateLossStatistics.average / 256.0f)
                                .addField("max_bps",
                                        currentStateBitrateStatistics.max)
                                .addField("min_bps",
                                        currentStateBitrateStatistics.min)
                                .addField("avg_bps",
                                        currentStateBitrateStatistics.average)
                                .addField("duration_ms",
                                        currentStateCumulativeDurationMs)
                                .addField("consecutive_visits",
                                        currentStateConsecutiveVisits)
                                .addField("bitrate_threshold",
                                        bitrate_threshold_bps_)
                                .addField("low_loss_threshold",
                                        low_loss_threshold_)
                                .addField("high_loss_threshold",
                                        high_loss_threshold_)
                                .addField("delta_bps",
                                        latestEstimate - currentStateStartBitrateBps))
                    }
                }
                currentState = nextState
                currentStateStartBitrateBps = latestEstimate
                if (isDirty) {
                    currentStateLossStatistics = IntSummaryStatistics()
                    currentStateBitrateStatistics = LongSummaryStatistics()
                    currentStateConsecutiveVisits = 0
                    currentStateCumulativeDurationMs = 0
                    isDirty = false
                }
                currentStateBitrateStatistics.accept(latestEstimate)
            }
        }

        override val lossLimitedMs: Long
            get() {
                synchronized(this@SendSideBandwidthEstimation) { return lossLimitedMsStats.sum }
            }
        override val lossDegradedMs: Long
            get() {
                synchronized(this@SendSideBandwidthEstimation) { return lossDegradedMsStats.sum }
            }
        override val lossFreeMs: Long
            get() {
                synchronized(this@SendSideBandwidthEstimation) { return lossFreeMsStats.sum }
            }
    }

    /**
     * Represents the loss-based controller states.
     */
    enum class LossRegion {
        /**
         * Loss is between 2% and 10%.
         */
        LossLimited,

        /**
         * Loss is above 10%.
         */
        LossDegraded,

        /**
         * Loss is bellow 2%.
         */
        LossFree
    }

    companion object {
        /**
         * The name of the property that specifies the low-loss threshold
         * (expressed as a proportion of lost packets).
         * See [.low_loss_threshold_].
         */
        val LOW_LOSS_THRESHOLD_PNAME = SendSideBandwidthEstimation::class.java.name + ".lowLossThreshold"

        /**
         * The name of the property that specifies the high-loss threshold
         * (expressed as a proportion of lost packets).
         * See [.high_loss_threshold_].
         */
        val HIGH_LOSS_THRESHOLD_PNAME = SendSideBandwidthEstimation::class.java.name + ".highLossThreshold"

        /**
         * The name of the property that specifies the bitrate threshold (in kbps).
         * See [.bitrate_threshold_bps_].
         */
        val BITRATE_THRESHOLD_KBPS_PNAME = SendSideBandwidthEstimation::class.java.name + ".bitrateThresholdKbps"

        /**
         * The name of the property that specifies the probability of enabling the loss-based experiment.
         */
        val LOSS_EXPERIMENT_PROBABILITY_PNAME = SendSideBandwidthEstimation::class.java.name + ".lossExperimentProbability"

        /**
         * The name of the property that specifies the probability of enabling the
         * timeout experiment.
         */
        val TIMEOUT_EXPERIMENT_PROBABILITY_PNAME = SendSideBandwidthEstimation::class.java.name + ".timeoutExperimentProbability"

        /**
         * The ConfigurationService to get config values from.
         */
        private val cfg = LibJitsi.configurationService

        /**
         * send_side_bandwidth_estimation.cc
         */
        private const val kBweIncreaseIntervalMs = 1000

        /**
         * send_side_bandwidth_estimation.cc
         */
        private const val kBweDecreaseIntervalMs: Long = 300

        /**
         * send_side_bandwidth_estimation.cc
         */
        private const val kDefaultMinBitrateBps = 10000

        /**
         * send_side_bandwidth_estimation.cc
         */
        private const val kDefaultMaxBitrateBps = 1000000000

        /**
         * send_side_bandwidth_estimation.cc
         */
        private const val kStartPhaseMs = 2000

        /**
         * send_side_bandwidth_estimation.cc
         */
        private const val kLimitNumPackets = 20

        /**
         * send_side_bandwidth_estimation.cc
         *
         * Expecting that RTCP feedback is sent uniformly within [0.5, 1.5]s
         * intervals.
         */
        private const val kFeedbackIntervalMs: Long = 1500

        /**
         * send_side_bandwidth_estimation.cc
         */
        private const val kPacketReportTimeoutIntervals = 1.2

        /**
         * send_side_bandwidth_estimation.cc
         */
        private const val kFeedbackTimeoutIntervals: Long = 3

        /**
         * send_side_bandwidth_estimation.cc
         */
        private const val kTimeoutIntervalMs: Long = 1000

        /**
         * send_side_bandwidth_estimation.cc
         */
        private const val kDefaultLowLossThreshold = 0.02f

        /**
         * send_side_bandwidth_estimation.cc
         */
        private const val kDefaultHighLossThreshold = 0.1f

        /**
         * send_side_bandwidth_estimation.cc
         */
        private const val kDefaultBitrateThresholdKbps = 0

        /**
         * Disable the loss experiment by default.
         */
        private const val kDefaultLossExperimentProbability = 0f

        /**
         * Disable the timeout experiment by default.
         */
        private const val kDefaultTimeoutExperimentProbability = 0f

        /**
         * The random number generator for all instances of this class.
         */
        private val kRandom = Random()

        /**
         * The [TimeSeriesLogger] to be used by this instance to print time series.
         */
        private val timeSeriesLogger = TimeSeriesLogger.getTimeSeriesLogger(SendSideBandwidthEstimation::class.java)
    }
}