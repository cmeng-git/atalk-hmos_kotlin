/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.remotebitrateestimator

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.util.logging.DiagnosticContext
import timber.log.Timber

/**
 * webrtc/modules/remote_bitrate_estimator/overuse_detector.cc
 * webrtc/modules/remote_bitrate_estimator/overuse_detector.h
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
internal class OveruseDetector(options: OverUseDetectorOptions?, diagnosticContext: DiagnosticContext) {
    /**
     * Returns the current detector state.
     *
     * @return
     */
    var state = BandwidthUsage.kBwNormal
        private set
    private val inExperiment = false // AdaptiveThresholdExperimentIsEnabled()
    private var kDown = 0.00018
    private var kUp = 0.01
    private var lastUpdateMs = -1L
    private var overuseCounter = 0
    private var overusingTimeThreshold = 100.0
    private var prevOffset = 0.0
    private var threshold = 12.5
    private var timeOverUsing = -1.0
    private val diagnosticContext: DiagnosticContext

    init {
        if (options == null) throw NullPointerException("options")
        threshold = options.initialThreshold
        this.diagnosticContext = diagnosticContext
        if (inExperiment) initializeExperiment()
    }

    /**
     * Update the detection state based on the estimated inter-arrival time delta offset.
     * `timestampDelta` is the delta between the last timestamp which the estimated offset is
     * based on and the last timestamp on which the last offset was based on, representing the time
     * between detector updates. `numOfDeltas` is the number of deltas the offset estimate is
     * based on. Returns the state after the detection update.
     *
     * @param offset
     * @param tsDelta
     * @param numOfDeltas
     * @param nowMs
     * @return
     */
    fun detect(offset: Double, tsDelta: Double, numOfDeltas: Int, nowMs: Long): BandwidthUsage {
        if (numOfDeltas < 2) return BandwidthUsage.kBwNormal
        val prev_offset = prevOffset
        prevOffset = offset
        val T = Math.min(numOfDeltas, 60) * offset
        var newHypothesis = false
        if (T > threshold) {
            if (timeOverUsing == -1.0) {
                // Initialize the timer. Assume that we've been
                // over-using half of the time since the previous
                // sample.
                timeOverUsing = tsDelta / 2
            } else {
                // Increment timer
                timeOverUsing += tsDelta
            }
            overuseCounter++
            if (timeOverUsing > overusingTimeThreshold && overuseCounter > 1) {
                if (offset >= prev_offset) {
                    timeOverUsing = 0.0
                    overuseCounter = 0
                    state = BandwidthUsage.kBwOverusing
                    newHypothesis = true
                }
            }
        } else if (T < -threshold) {
            timeOverUsing = -1.0
            overuseCounter = 0
            state = BandwidthUsage.kBwUnderusing
            newHypothesis = true
        } else {
            timeOverUsing = -1.0
            overuseCounter = 0
            state = BandwidthUsage.kBwNormal
            newHypothesis = true
        }
        if (newHypothesis) {
            Timber.log(TimberLog.FINER, "%s", diagnosticContext
                    .makeTimeSeriesPoint("utilization_hypothesis", nowMs)
                    .addField("detector", hashCode())
                    .addField("offset", offset)
                    .addField("prev_offset", prev_offset)
                    .addField("T", T)
                    .addField("threshold", threshold)
                    .addField("hypothesis", state.value))
        }
        updateThreshold(T, nowMs)
        return state
    }

    private fun initializeExperiment() {
        val kUp = 0.0
        val kDown = 0.0
        overusingTimeThreshold = kOverUsingTimeThreshold.toDouble()
        // if (readExperimentConstants(kUp, kDown))
        run {
            this.kUp = kUp
            this.kDown = kDown
        }
    }

    private fun updateThreshold(modifiedOffset: Double, nowMs: Long) {
        if (!inExperiment) return
        if (lastUpdateMs == -1L) lastUpdateMs = nowMs
        if (Math.abs(modifiedOffset) > threshold + kMaxAdaptOffsetMs) {
            // Avoid adapting the threshold to big latency spikes, caused e.g., by a sudden capacity drop.
            lastUpdateMs = nowMs
            return
        }
        val k = if (Math.abs(modifiedOffset) < threshold) kDown else kUp
        threshold += k * (Math.abs(modifiedOffset) - threshold) * (nowMs - lastUpdateMs)
        val kMinThreshold = 6.0
        val kMaxThreshold = 600.0
        threshold = Math.min(Math.max(threshold, kMinThreshold), kMaxThreshold)
        lastUpdateMs = nowMs
    }

    companion object {
        private const val kMaxAdaptOffsetMs = 15.0
        private const val kOverUsingTimeThreshold = 100
    }
}