/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.event

import org.atalk.service.neomedia.VolumeControl
import java.util.*

/**
 * Represents the event fired when playback volume value has changed.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class VolumeChangeEvent
/**
 * Initializes a new `VolumeChangeEvent` which is to notify about a specific volume level
 * and its mute state.
 *
 * @param source
 * the `VolumeControl` which is the source of the change
 * @param level
 * the volume level
 * @param mute
 * `true` if the volume is muted; otherwise, `false`
 * @throws IllegalArgumentException
 * if source is `null`
 */
(source: VolumeControl?,
        /**
         * The volume level.
         */
        val level: Float,
        /**
         * The indicator which determines whether the volume is muted.
         */
        val mute: Boolean) : EventObject(source) {
    /**
     * Gets the volume level notified about by this `VolumeChangeEvent`.
     *
     * @return the volume level notified about by this `VolumeChangeEvent`
     */
    /**
     * Gets the indicator which determines whether the volume is muted.
     *
     * @return `true` if the volume is muted; otherwise, `false`
     */

    /**
     * Gets the `VolumeControl` which is the source of the change notified about by this
     * `VolumeChangeEvent`.
     *
     * @return the `VolumeControl` which is the source of the change notified about by this
     * `VolumeChangeEvent`
     */
    val sourceVolumeControl: VolumeControl
        get() = getSource() as VolumeControl

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}