/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.recording

/**
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
interface Synchronizer {
    /**
     * Sets the clock rate of the RTP clock for a specific SSRC.
     *
     * @param ssrc
     * the SSRC for which to set the RTP clock rate.
     * @param clockRate
     * the clock rate.
     */
    fun setRtpClockRate(ssrc: Long, clockRate: Long)

    /**
     * Sets the endpoint identifier for a specific SSRC.
     *
     * @param ssrc
     * the SSRC for which to set the endpoint identifier.
     * @param endpointId
     * the endpoint identifier to set.
     */
    fun setEndpoint(ssrc: Long, endpointId: String?)

    /**
     * Notifies this `Synchronizer` that the RTP timestamp `rtpTime` (for SSRC
     * `ssrc`) corresponds to the NTP timestamp `ntpTime`.
     *
     * @param ssrc
     * the SSRC.
     * @param rtpTime
     * the RTP timestamp which corresponds to `ntpTime`.
     * @param ntpTime
     * the NTP timestamp which corresponds to `rtpTime`.
     */
    fun mapRtpToNtp(ssrc: Long, rtpTime: Long, ntpTime: Double)

    /**
     * Notifies this `Synchronizer` that the local timestamp `localTime` corresponds
     * to the NTP timestamp `ntpTime` (for SSRC `ssrc`).
     *
     * @param ssrc
     * the SSRC.
     * @param localTime
     * the local timestamp which corresponds to `ntpTime`.
     * @param ntpTime
     * the NTP timestamp which corresponds to `localTime`.
     */
    fun mapLocalToNtp(ssrc: Long, localTime: Long, ntpTime: Double)

    /**
     * Tries to find the local time (as returned by `System.currentTimeMillis()`) that
     * corresponds to the RTP timestamp `rtpTime` for the SSRC `ssrc`.
     *
     * Returns -1 if the local time cannot be found (for example because not enough information for
     * the SSRC has been previously provided to the `Synchronizer`).
     *
     * @param ssrc
     * the SSRC with which `rtpTime` is associated.
     * @param rtpTime
     * the RTP timestamp
     * @return the local time corresponding to `rtpTime` for SSRC `ssrc` if it can be
     * calculated, and -1 otherwise.
     */
    fun getLocalTime(ssrc: Long, rtpTime: Long): Long
}