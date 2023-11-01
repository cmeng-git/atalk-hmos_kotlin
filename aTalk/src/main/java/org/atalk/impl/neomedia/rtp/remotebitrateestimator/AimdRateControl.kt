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
package org.atalk.impl.neomedia.rtp.remotebitrateestimator

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.service.neomedia.rtp.RemoteBitrateEstimator
import org.atalk.util.logging.DiagnosticContext
import timber.log.Timber

/**
 * A rate control implementation based on additive increases of bitrate when no over-use is detected
 * and multiplicative decreases when over-uses are detected. When we think the available bandwidth
 * has changes or is unknown, we will switch to a "slow-start mode" where we increase multiplicatively.
 *
 *
 * webrtc/modules/remote_bitrate_estimator/aimd_rate_control.cc
 * webrtc/modules/remote_bitrate_estimator/aimd_rate_control.h
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
internal class AimdRateControl(diagnosticContext: DiagnosticContext) {
    private val diagnosticContext: DiagnosticContext
    private var avgMaxBitrateKbps = 0f
    private var beta = 0f

    /**
     * Returns `true` if there is a valid estimate of the incoming bitrate, `false` otherwise.
     *
     * @return
     */
    var isValidEstimate = false
        private set
    var latestEstimate = 0L
        private set

    private val currentInput = RateControlInput(BandwidthUsage.kBwNormal, 0L, 0.0)
    private var inExperiment = false
    private var minConfiguredBitrateBps = 0L
    private var rateControlRegion: RateControlRegion? = null
    private var rateControlState: RateControlState? = null
    private var rtt = 0L
    private var timeFirstIncomingEstimate = 0L
    private var timeLastBitrateChange = 0L
    private var timeOfLastLog = 0L
    private var updated = false
    private var varMaxBitrateKbps = 0f

    init {
        reset()
        this.diagnosticContext = diagnosticContext
    }

    private fun additiveRateIncrease(nowMs: Long, lastMs: Long, responseTimeMs: Long): Long {
        require(responseTimeMs > 0) { "responseTimeMs" }
        var beta = 0.0
        if (lastMs > 0) {
            beta = Math.min((nowMs - lastMs) / responseTimeMs.toDouble(), 1.0)
            if (inExperiment) beta /= 2.0
        }
        val bitsPerFrame = latestEstimate.toDouble() / 30.0
        val packetsPerFrame = Math.ceil(bitsPerFrame / (8.0 * 1200.0))
        val avgPacketSizeBits = bitsPerFrame / packetsPerFrame
        return Math.max(1000.0, beta * avgPacketSizeBits).toLong()
    }

    private fun changeBitrate(currentBitrateBps: Long, incomingBitrateBps: Long, nowMs: Long): Long {
        var currentBitrateBps = currentBitrateBps
        if (!updated) return latestEstimate
        // An over-use should always trigger us to reduce the bitrate, even though
        // we have not yet established our first estimate. By acting on the over-use,
        // we will end up with a valid estimate.
        if (!isValidEstimate && currentInput.bwState != BandwidthUsage.kBwOverusing) {
            return latestEstimate
        }
        updated = false
        changeState(currentInput, nowMs)

        // Calculated here because it's used in multiple places.
        val incomingBitrateKbps = incomingBitrateBps / 1000.0f
        // Calculate the max bit rate std dev given the normalized variance and
        // the current incoming bit rate.
        val stdMaxBitRate = Math.sqrt((varMaxBitrateKbps * avgMaxBitrateKbps).toDouble()).toFloat()
        when (rateControlState) {
            RateControlState.kRcHold -> {}
            RateControlState.kRcIncrease -> {
                if (avgMaxBitrateKbps >= 0f
                        && incomingBitrateKbps > avgMaxBitrateKbps + 3f * stdMaxBitRate) {
                    changeRegion(RateControlRegion.kRcMaxUnknown, nowMs)
                    avgMaxBitrateKbps = -1f
                }
                currentBitrateBps += if (rateControlRegion == RateControlRegion.kRcNearMax) {
                    // Approximate the over-use estimator delay to 100 ms.
                    val responseTime = rtt + 100
                    val additiveIncreaseBps = additiveRateIncrease(nowMs, timeLastBitrateChange, responseTime)
                    additiveIncreaseBps
                } else { // kRcMaxUnknown || kRcAboveMax
                    val multiplicativeIncreaseBps = multiplicativeRateIncrease(nowMs, timeLastBitrateChange, currentBitrateBps)
                    multiplicativeIncreaseBps
                }
                timeLastBitrateChange = nowMs
            }
            RateControlState.kRcDecrease -> {
                isValidEstimate = true
                if (incomingBitrateBps < minConfiguredBitrateBps) {
                    currentBitrateBps = minConfiguredBitrateBps
                } else {
                    // Set bit rate to something slightly lower than max to get rid
                    // of any self-induced delay.
                    currentBitrateBps = (beta * incomingBitrateBps + 0.5).toLong()
                    if (currentBitrateBps > latestEstimate) {
                        // Avoid increasing the rate when over-using.
                        if (rateControlRegion != RateControlRegion.kRcMaxUnknown) {
                            currentBitrateBps = (beta * avgMaxBitrateKbps * 1000f + 0.5f).toLong()
                        }
                        currentBitrateBps = Math.min(currentBitrateBps, latestEstimate)
                    }
                    changeRegion(RateControlRegion.kRcNearMax, nowMs)
                    if (incomingBitrateKbps < avgMaxBitrateKbps - 3f * stdMaxBitRate) {
                        avgMaxBitrateKbps = -1f
                    }
                    updateMaxBitRateEstimate(incomingBitrateKbps)
                }
                // Stay on hold until the pipes are cleared.
                changeState(RateControlState.kRcHold, nowMs)
                timeLastBitrateChange = nowMs
            }
            else -> throw IllegalStateException("rateControlState")
        }
        if ((incomingBitrateBps > 100000L || currentBitrateBps > 150000L)
                && currentBitrateBps > 1.5 * incomingBitrateBps) {
            // Allow changing the bit rate if we are operating at very low rates
            // Don't change the bit rate if the send side is too far off
            currentBitrateBps = latestEstimate
            timeLastBitrateChange = nowMs
        }
        return currentBitrateBps
    }

    private fun changeRegion(region: RateControlRegion, nowMs: Long) {
        if (rateControlRegion == region) {
            return
        }
        rateControlRegion = region
        Timber.log(TimberLog.FINER, "%s", diagnosticContext
                .makeTimeSeriesPoint("aimd_region", nowMs)
                .addField("aimd_id", hashCode())
                .addField("region", region))
    }

    private fun changeState(input: RateControlInput, nowMs: Long) {
        when (currentInput.bwState) {
            BandwidthUsage.kBwNormal -> if (rateControlState == RateControlState.kRcHold) {
                timeLastBitrateChange = nowMs
                changeState(RateControlState.kRcIncrease, nowMs)
            }
            BandwidthUsage.kBwOverusing -> if (rateControlState != RateControlState.kRcDecrease) {
                changeState(RateControlState.kRcDecrease, nowMs)
            }
            BandwidthUsage.kBwUnderusing -> changeState(RateControlState.kRcHold, nowMs)
            else -> throw IllegalStateException("currentInput.bwState")
        }
    }

    private fun changeState(newState: RateControlState, nowMs: Long) {
        if (rateControlState == newState) {
            return
        }
        rateControlState = newState
        Timber.log(TimberLog.FINER, "%s", diagnosticContext
                .makeTimeSeriesPoint("aimd_state", nowMs)
                .addField("aimd_id", hashCode())
                .addField("state", rateControlState))
    }

    // Estimate how often we can send RTCP if we allocate up to 5% of
    // bandwidth to feedback.
    val feedBackInterval: Long
        get() {
            // Estimate how often we can send RTCP if we allocate up to 5% of
            // bandwidth to feedback.
            val interval = (kRtcpSize * 8.0 * 1000.0 / (0.05 * latestEstimate) + 0.5).toLong()
            return Math.min(Math.max(interval, kMinFeedbackIntervalMs), kMaxFeedbackIntervalMs)
        }

    /**
     * Returns `true` if the bitrate estimate hasn't been changed for more than an RTT, or if
     * the `incomingBitrate` is more than 5% above the current estimate. Should be used to
     * decide if we should reduce the rate further when over-using.
     *
     * @param timeNow
     * @param incomingBitrateBps
     * @return
     */
    fun isTimeToReduceFurther(timeNow: Long, incomingBitrateBps: Long): Boolean {
        val bitrateReductionInterval = Math.max(Math.min(rtt, 200L), 10L)
        if (timeNow - timeLastBitrateChange >= bitrateReductionInterval) return true
        if (isValidEstimate) {
            val threshold = (kWithinIncomingBitrateHysteresis * incomingBitrateBps).toLong()
            val bitrateDifference = latestEstimate - incomingBitrateBps
            return bitrateDifference > threshold
        }
        return false
    }

    private fun multiplicativeRateIncrease(nowMs: Long, lastMs: Long, currentBitrateBps: Long): Long {
        var alpha = 1.08
        if (lastMs > -1) {
            val timeSinceLastUpdateMs = Math.min(nowMs - lastMs, 1000)
            alpha = Math.pow(alpha, timeSinceLastUpdateMs / 1000.0)
        }
        return Math.max(currentBitrateBps * (alpha - 1.0), 1000.0).toLong()
    }

    fun reset() {
        reset(RemoteBitrateEstimator.kDefaultMinBitrateBps.toLong())
    }

    private fun reset(minBitrateBps: Long) {
        minConfiguredBitrateBps = minBitrateBps
        latestEstimate =  /* maxConfiguredBitrateBps */30000000L
        avgMaxBitrateKbps = -1f
        varMaxBitrateKbps = 0.4f
        rateControlState = RateControlState.kRcHold
        rateControlRegion = RateControlRegion.kRcMaxUnknown
        timeLastBitrateChange = -1L
        currentInput.bwState = BandwidthUsage.kBwNormal
        currentInput.incomingBitRate = 0L
        currentInput.noiseVar = 1.0
        updated = false
        timeFirstIncomingEstimate = -1L
        isValidEstimate = false
        beta = 0.85f
        rtt = kDefaultRttMs.toLong()
        timeOfLastLog = -1L
        inExperiment = false
    }

    fun setEstimate(bitrateBps: Long, nowMs: Long) {
        updated = true
        isValidEstimate = true
        latestEstimate = changeBitrate(bitrateBps, bitrateBps, nowMs)
    }

    fun setMinBitrate(minBitrateBps: Long) {
        minConfiguredBitrateBps = minBitrateBps
        latestEstimate = Math.max(minBitrateBps, latestEstimate)
    }

    fun setRtt(rtt: Long) {
        Timber.log(TimberLog.FINER, "%s", diagnosticContext
                .makeTimeSeriesPoint("aimd_rtt", System.currentTimeMillis())
                .addField("aimd_id", hashCode())
                .addField("rtt", rtt))
        this.rtt = rtt
    }

    fun update(input: RateControlInput?, nowMs: Long) {
        if (input == null) throw NullPointerException("input")

        // Set the initial bit rate value to what we're receiving the first half
        // second.
        if (!isValidEstimate) {
            if (timeFirstIncomingEstimate < 0L) {
                if (input.incomingBitRate > 0L) timeFirstIncomingEstimate = nowMs
            } else if (nowMs - timeFirstIncomingEstimate > kInitializationTimeMs
                    && input.incomingBitRate > 0L) {
                latestEstimate = input.incomingBitRate
                isValidEstimate = true
            }
        }
        if (updated && currentInput.bwState == BandwidthUsage.kBwOverusing) {
            // Only update delay factor and incoming bit rate. We always want to
            // react on an over-use.
            currentInput.noiseVar = input.noiseVar
            currentInput.incomingBitRate = input.incomingBitRate
        } else {
            updated = true
            currentInput.copy(input)
        }
    }

    fun updateBandwidthEstimate(nowMs: Long): Long {
        latestEstimate = changeBitrate(latestEstimate, currentInput.incomingBitRate, nowMs)
        if (isValidEstimate) {
            Timber.log(TimberLog.FINER, "%s", diagnosticContext
                    .makeTimeSeriesPoint("aimd_estimate", nowMs)
                    .addField("aimd_id", hashCode())
                    .addField("estimate_bps", latestEstimate)
                    .addField("incoming_bps", currentInput.incomingBitRate))
        }
        if (nowMs - timeOfLastLog > kLogIntervalMs) timeOfLastLog = nowMs
        return latestEstimate
    }

    private fun updateMaxBitRateEstimate(incomingBitrateKbps: Float) {
        val alpha = 0.05f
        avgMaxBitrateKbps = if (avgMaxBitrateKbps == -1f) {
            incomingBitrateKbps
        } else {
            (1 - alpha) * avgMaxBitrateKbps + alpha * incomingBitrateKbps
        }

        // Estimate the max bit rate variance and normalize the variance with
        // the average max bit rate.
        val norm = Math.max(avgMaxBitrateKbps, 1f)
        varMaxBitrateKbps = (1 - alpha) * varMaxBitrateKbps + (alpha
                * (avgMaxBitrateKbps - incomingBitrateKbps)
                * (avgMaxBitrateKbps - incomingBitrateKbps)) / norm
        // 0.4 ~= 14 kbit/s at 500 kbit/s
        if (varMaxBitrateKbps < 0.4f) varMaxBitrateKbps = 0.4f
        // 2.5f ~= 35 kbit/s at 500 kbit/s
        if (varMaxBitrateKbps > 2.5f) varMaxBitrateKbps = 2.5f
    }

    companion object {
        private const val kDefaultRttMs = 200
        private const val kInitializationTimeMs = 5000L
        private const val kLogIntervalMs = 1000L
        private const val kMaxFeedbackIntervalMs = 1000L
        private const val kMinFeedbackIntervalMs = 200L
        private const val kRtcpSize = 80
        private const val kWithinIncomingBitrateHysteresis = 1.05
    }
}