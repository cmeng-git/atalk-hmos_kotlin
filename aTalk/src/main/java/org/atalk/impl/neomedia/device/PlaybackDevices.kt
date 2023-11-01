/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

/**
 * Manages the list of active (currently plugged-in) playback devices and manages user preferences
 * between all known devices (previously and actually plugged-in).
 *
 * @author Vincent Lucas
 * @author Eng Chong Meng
 */
open class PlaybackDevices
/**
 * Initializes the playback device list management.
 *
 * @param audioSystem The audio system managing this playback device list.
 */
(audioSystem: AudioSystem) : Devices(audioSystem) {
    /**
     * Returns the property of the capture devices.
     */
    override val propDevice: String
        get() = PROP_DEVICE

    companion object {
        const val PROP_DEVICE = "playbackDevice"
    }
}