/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.util.dsi

/**
 * Represents an algorithm for the detection/identification of the active/dominant
 * speaker/participant/endpoint/stream in a multipoint conference.
 *
 *
 * Implementations of `ActiveSpeakerDetector` get notified about the (current) audio levels
 * of multiple audio streams (identified by their synchronization source identifiers/SSRCs) via
 * calls to [.levelChanged] and determine/identify which stream is dominant/active
 * (in terms of speech). When the active stream changes, listeners registered via
 * [.addActiveSpeakerChangedListener] are notified.
 *
 *
 * @author Boris Grozev
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface ActiveSpeakerDetector {
    /**
     * Adds a listener to be notified by this active speaker detector when the active stream
     * changes.
     *
     * @param listener the listener to register with this instance for notifications about changes of the
     * active speaker
     */
    fun addActiveSpeakerChangedListener(listener: ActiveSpeakerChangedListener?)

    /**
     * Notifies this `ActiveSpeakerDetector` about the latest/current audio level of a
     * stream/speaker identified by a specific synchronization source identifier/SSRC.
     *
     * @param ssrc the SSRC of the stream/speaker
     * @param level the latest/current audio level of the stream/speaker with the specified `ssrc`
     */
    fun levelChanged(ssrc: Long, level: Int)

    /**
     * Removes a listener to no longer be notified by this active speaker detector when the active
     * stream changes.
     *
     * @param listener the listener to unregister with this instance for notifications about changes of the
     * active speaker
     */
    fun removeActiveSpeakerChangedListener(listener: ActiveSpeakerChangedListener?)
}