/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.remotebitrateestimator

/**
 * webrtc/modules/remote_bitrate_estimator/include/bwe_defines.h
 *
 * @author Lyubomir Marinov
 */
class RateControlInput(var bwState: BandwidthUsage, var incomingBitRate: Long, var noiseVar: Double) {
    /**
     * Assigns the values of the fields of `source` to the respective fields of this
     * `RateControlInput`.
     *
     * @param source
     * the `RateControlInput` the values of the fields of which are to be assigned to
     * the respective fields of this `RateControlInput`
     */
    fun copy(source: RateControlInput) {
        bwState = source.bwState
        incomingBitRate = source.incomingBitRate
        noiseVar = source.noiseVar
    }
}