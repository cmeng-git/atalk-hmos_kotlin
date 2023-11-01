/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import org.atalk.service.neomedia.event.VolumeChangeListener

/**
 * Control for volume level in (neo)media service.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
interface VolumeControl {
    /**
     * Adds a `VolumeChangeListener` to be informed about changes in the volume level of this instance.
     *
     * @param listener the `VolumeChangeListener` to be informed about changes in the volume level of
     * this instance
     */
    fun addVolumeChangeListener(listener: VolumeChangeListener)

    /**
     * Returns the maximum allowed volume value/level.
     *
     * @return the maximum allowed volume value/level
     */
    val maxValue: Float

    /**
     * Returns the minimum allowed volume value/level.
     *
     * @return the minimum allowed volume value/level
     */
    val minValue: Float

    /**
     * Gets the current volume value/level.
     */
    var volumeLevel: Float

    /**
     * Get mute state of sound playback.
     *
     * @return mute state of sound playback.
     */
    /**
     * Mutes current sound playback.
     *
     * @param mute mutes/unmutes playback.
     */
    fun getMute(): Boolean

    /**
     * Removes a `VolumeChangeListener` to no longer be notified about changes in the volume
     * level of this instance.
     *
     * @param listener the `VolumeChangeListener` to no longer be notified about changes in the volume
     * level of this instance
     */
    fun removeVolumeChangeListener(listener: VolumeChangeListener)

    /**
     * Sets the current volume value/level.
     *
     * @param value the volume value/level to set on this instance
     * @return the actual/current volume value/level set on this instance
     */
    fun setVolume(value: Float): Float

    companion object {
        /**
         * The name of the configuration property which specifies the volume level of audio input.
         */
        const val CAPTURE_VOLUME_LEVEL_PROPERTY_NAME = "media.CAPTURE_VOLUME_LEVEL"

        /**
         * The name of the configuration property which specifies the volume level of audio output.
         */
        const val PLAYBACK_VOLUME_LEVEL_PROPERTY_NAME = "media.PLAYBACK_VOLUME_LEVEL"
    }
}