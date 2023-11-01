/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.rtp

/**
 * webrtc/modules/remote_bitrate_estimator/include/remote_bitrate_estimator.cc
 * webrtc/modules/remote_bitrate_estimator/include/remote_bitrate_estimator.h
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface RemoteBitrateEstimator : CallStatsObserver {
    /**
     * Returns the estimated payload bitrate in bits per second if a valid estimate exists;
     * otherwise, `-1`.
     *
     * @return the estimated payload bitrate in bits per seconds if a valid estimate exists;
     * otherwise, `-1`
     */
    val latestEstimate: Long

    /**
     * Returns the estimated payload bitrate in bits per second if a valid
     * estimate exists; otherwise, `-1`.
     *
     * @return the estimated payload bitrate in bits per seconds if a valid
     * estimate exists; otherwise, `-1`
     */
    val ssrcs: Collection<Long>?

    /**
     * Removes all data for `ssrc`.
     *
     * @param ssrc
     */
    fun removeStream(ssrc: Long)

    /**
     * Sets the minimum bitrate for this instance.
     *
     * @param minBitrateBps the minimum bitrate in bps.
     */
    fun setMinBitrate(minBitrateBps: Int)

    /**
     * Notifies this instance of an incoming packet.
     *
     * @param arrivalTimeMs the arrival time of the packet in millis.
     * @param timestamp the 32bit send timestamp of the packet. Note that the
     * specific format depends on the specific implementation.
     * @param payloadSize the payload size of the packet.
     * @param ssrc the SSRC of the packet.
     */
    fun incomingPacketInfo(arrivalTimeMs: Long, timestamp: Long, payloadSize: Int, ssrc: Long)

    companion object {
        /**
         * webrtc/modules/remote_bitrate_estimator/include/bwe_defines.h
         */
        const val kBitrateWindowMs = 1000
        const val kBitrateScale = 8000
        const val kDefaultMinBitrateBps = 30000
        const val kProcessIntervalMs = 500
        const val kStreamTimeOutMs = 2000
        const val kTimestampGroupLengthMs = 5
    }
}