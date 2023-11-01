/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.util.dsi

/**
 * Implementing classes can be notified about changes to the 'active' stream (identified by its
 * SSRC) using [.activeSpeakerChanged].
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
interface ActiveSpeakerChangedListener {
    /**
     * Notifies this listener that the active/dominant stream/speaker has been changed to one
     * identified by a specific synchronization source identifier/SSRC.
     *
     * @param ssrc the SSRC of the latest/current active/dominant stream/speaker
     */
    fun activeSpeakerChanged(ssrc: Long)
}