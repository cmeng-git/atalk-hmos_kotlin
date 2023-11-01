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
import org.atalk.util.logging.DiagnosticContext
import timber.log.Timber
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * webrtc/modules/remote_bitrate_estimator/overuse_estimator.cc
 * webrtc/modules/remote_bitrate_estimator/overuse_estimator.h
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
internal class OveruseEstimator(options: OverUseDetectorOptions, private val diagnosticContext: DiagnosticContext) {
    private var avgNoise: Double
    private val E: Array<DoubleArray?>

    /**
     * Reduces the effects of allocations and garbage collection of the method `update`.
     */
    private val Eh = DoubleArray(2)

    /**
     * Reduces the effects of allocations and garbage collection of the method `update`.
     */
    private val h = DoubleArray(2)

    /**
     * Reduces the effects of allocations and garbage collection of the method `update`.
     */
    private val IKh = arrayOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(0.0, 0.0))

    /**
     * Reduces the effects of allocations and garbage collection of the method `update`.
     */
    private val K = DoubleArray(2)

    /**
     * Returns the number of deltas which the current over-use estimator state is based on.
     *
     * @return
     */
    var numOfDeltas = 0
        private set

    /**
     * Returns the estimated inter-arrival time delta offset in ms.
     *
     * @return
     */
    var offset: Double
        private set
    private var prevOffset: Double
    private val processNoise: DoubleArray
    private var slope: Double

    /**
     * Store the tsDelta history into a `double[]` used as a circular buffer
     * Original c++ code uses std::list<double> but in Java this translate
     * to `List<Double>` which causes a lot of autoboxing
    </double> */
    private val tsDeltaHist = DoubleArray(kMinFramePeriodHistoryLength)

    /**
     * Index to insert next value into [.tsDeltaHist]
     */
    private var tsDeltaHistInsIdx = 0

    /**
     * Returns the estimated noise/jitter variance in ms^2.
     *
     * @return
     */
    var varNoise: Double
        private set

    init {
        slope = options.initialSlope
        offset = options.initialOffset
        prevOffset = offset
        avgNoise = options.initialAvgNoise
        varNoise = options.initialVarNoise
        E = clone(options.initialE)
        processNoise = options.initialProcessNoise.clone()
        /*
         * Initialize {@link tsDeltaHist} with {@code Double.MAX_VALUE}
         * to simplify {@link updateMinFramePeriod}
         */
        Arrays.fill(tsDeltaHist, Double.MAX_VALUE)
    }

    /**
     * Update the estimator with a new sample. The deltas should represent deltas between timestamp
     * groups as defined by the InterArrival class. `currentHypothesis` should be the
     * hypothesis of the over-use detector at this time.
     *
     * @param tDelta
     * @param tsDelta
     * @param sizeDelta
     * @param currentHypothesis
     */
    fun update(tDelta: Long, tsDelta: Double, sizeDelta: Int, currentHypothesis: BandwidthUsage?, systemTimeMs: Long) {
        val minFramePeriod = updateMinFramePeriod(tsDelta)
        val tTsDelta = tDelta - tsDelta
        ++numOfDeltas
        if (numOfDeltas > kDeltaCounterMax) numOfDeltas = kDeltaCounterMax

        // Update the Kalman filter
        E[0]!![0] += processNoise[0]
        E[1]!![1] += processNoise[1]
        if ((currentHypothesis == BandwidthUsage.kBwOverusing && offset < prevOffset || currentHypothesis == BandwidthUsage.kBwUnderusing) && offset > prevOffset) {
            E[1]!![1] += 10.0 * processNoise[1]
        }
        val h = h
        val Eh = Eh
        h[0] = sizeDelta.toDouble()
        h[1] = 1.0
        Eh[0] = E[0]!![0] * h[0] + E[0]!![1] * h[1]
        Eh[1] = E[1]!![0] * h[0] + E[1]!![1] * h[1]
        val residual = tTsDelta - slope * h[0] - offset
        val inStableState = currentHypothesis == BandwidthUsage.kBwNormal

        // We try to filter out very late frames. For instance periodic key
        // frames doesn't fit the Gaussian model well.
        val maxResidual = 3.0 * sqrt(varNoise)
        val residualForUpdateNoiseEstimate = if (abs(residual) < maxResidual) residual else if (residual < 0) -maxResidual else maxResidual
        updateNoiseEstimate(residualForUpdateNoiseEstimate, minFramePeriod, inStableState)
        val denom = varNoise + h[0] * Eh[0] + h[1] * Eh[1]
        val K = K
        val IKh = IKh
        K[0] = Eh[0] / denom
        K[1] = Eh[1] / denom
        IKh[0][0] = 1.0 - K[0] * h[0]
        IKh[0][1] = -K[0] * h[1]
        IKh[1][0] = -K[1] * h[0]
        IKh[1][1] = 1.0 - K[1] * h[1]
        val e00 = E[0]!![0]
        val e01 = E[0]!![1]

        // Update state.
        E[0]!![0] = e00 * IKh[0][0] + E[1]!![0] * IKh[0][1]
        E[0]!![1] = e01 * IKh[0][0] + E[1]!![1] * IKh[0][1]
        E[1]!![0] = e00 * IKh[1][0] + E[1]!![0] * IKh[1][1]
        E[1]!![1] = e01 * IKh[1][0] + E[1]!![1] * IKh[1][1]

        // Covariance matrix, must be positive semi-definite.
        val positiveSemiDefinite = E[0]!![0] + E[1]!![1] >= 0 && E[0]!![0] * E[1]!![1] - E[0]!![1] * E[1]!![0] >= 0 && E[0]!![0] >= 0
        check(positiveSemiDefinite) { "positiveSemiDefinite" }
        slope += K[0] * residual
        this.prevOffset = offset
        offset += K[1] * residual
        Timber.log(TimberLog.FINER, "%s", diagnosticContext
                .makeTimeSeriesPoint("delay_variation_estimation", systemTimeMs)
                .addField("estimator", hashCode())
                .addField("time_delta", tDelta)
                .addField("ts_delta", tsDelta)
                .addField("tts_delta", tTsDelta)
                .addField("offset", offset)
                .addField("hypothesis", currentHypothesis!!.value))
    }

    private fun updateMinFramePeriod(tsDelta: Double): Double {
        /*
         * Change from C++ version:
         * We use {@link tsDeltaHist} as a circular buffer initialized
         * with {@code Double.MAX_VALUE}, so we insert new {@link tsDelta}
         * value at {@link tsDeltaHistInsIdx} and we search for the
         * minimum value in {@link tsDeltaHist}
         */
        tsDeltaHist[tsDeltaHistInsIdx] = tsDelta
        tsDeltaHistInsIdx = (tsDeltaHistInsIdx + 1) % tsDeltaHist.size
        var min = tsDelta
        for (d in tsDeltaHist) {
            if (d < min) min = d
        }
        return min
    }

    private fun updateNoiseEstimate(residual: Double, tsDelta: Double, stableState: Boolean) {
        if (!stableState) return

        // Faster filter during startup to faster adapt to the jitter level of
        // the network alpha is tuned for 30 frames per second, but is scaled
        // according to tsDelta.
        var alpha = 0.01
        if (numOfDeltas > 10 * 30) alpha = 0.002

        // Only update the noise estimate if we're not over-using beta is a
        // function of alpha and the time delta since the previous update.
        val beta = Math.pow(1 - alpha, tsDelta * 30.0 / 1000.0)
        avgNoise = beta * avgNoise + (1 - beta) * residual
        varNoise = beta * varNoise + (1 - beta) * (avgNoise - residual) * (avgNoise - residual)
        if (varNoise < 1) varNoise = 1.0
    }

    companion object {
        private const val kDeltaCounterMax = 1000
        private const val kMinFramePeriodHistoryLength = 60

        /**
         * Creates and returns a deep copy of a `double` two-dimensional matrix.
         *
         * @param matrix the `double` two-dimensional matrix to create and return a deep copy of
         * @return a deep copy of `matrix`
         */
        private fun clone(matrix: Array<DoubleArray>): Array<DoubleArray?> {
            val length = matrix.size
            val clone: Array<DoubleArray?> = arrayOfNulls(length)
            for (i in 0 until length) clone[i] = matrix[i].clone()
            return clone
        }
    }
}