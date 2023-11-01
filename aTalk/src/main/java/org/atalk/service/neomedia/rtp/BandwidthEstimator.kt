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
package org.atalk.service.neomedia.rtp

/**
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
interface BandwidthEstimator {
    /**
     * Adds a listener to be notified about changes to the bandwidth estimation.
     * @param listener
     */
    fun addListener(listener: Listener?)

    /**
     * Removes a listener.
     * @param listener
     */
    fun removeListener(listener: Listener?)

    /**
     * @return the latest estimate.
     */
    val latestEstimate: Long

    /**
     * @return the latest values of the Receiver Estimated Maximum Bandwidth.
     */
    val latestREMB: Long

    /**
     * @return the [Statistics] specific to this bandwidth estimator.
     */
    val statistics: Statistics?

    /**
     * void SendSideBandwidthEstimation::UpdateReceiverEstimate
     * This is the entry/update point for the estimated bitrate in the
     * REMBPacket or a Delay Based Controller estimated bitrate when the
     * Delay based controller and the loss based controller lives on the
     * send side. see internet draft on "Congestion Control for RTCWEB"
     */
    fun updateReceiverEstimate(bandwidth: Long)

    /**
     * @return the latest effective fraction loss calculated by this
     * [BandwidthEstimator]. The value is between 0 and 256 (corresponding
     * to 0% and 100% respectively).
     */
    val latestFractionLoss: Int

    interface Listener {
        fun bandwidthEstimationChanged(newValueBps: Long)
    }

    /**
     * Holds stats specific to the bandwidth estimator.
     */
    interface Statistics {
        /**
         * @return the number of millis spent in the loss-degraded state.
         */
        val lossDegradedMs: Long

        /**
         * @return the number of millis spent in the loss-limited state.
         */
        val lossLimitedMs: Long

        /**
         * @return the number of millis spent in the loss-free state.
         */
        val lossFreeMs: Long
        fun update(nowMs: Long)
    }
}