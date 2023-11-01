/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification

/**
 * An implementation of the `SoundNotificationHandlerImpl` interface.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class SoundNotificationAction
/**
 * Creates an instance of `SoundNotification` by specifying the sound file descriptor and the loop interval.
 * By default is enabling simulation output to notification and playback device.
 *
 * @param soundDescriptor the sound file descriptor
 * @param loopInterval the loop interval
 */ @JvmOverloads constructor(
        /**
         * the descriptor pointing to the sound to be played.
         */
        var descriptor: String?,
        /**
         * Interval of milliseconds to wait before repeating the sound. -1 means no repetition.
         */
        var loopInterval: Int,
        /**
         * The boolean telling if this sound is to be played on notification device.
         */
        var isSoundNotificationEnabled: Boolean = false,
        /**
         * Is sound to be played on playback device.
         */
        var isSoundPlaybackEnabled: Boolean = false,
        /**
         * Is sound to be played on pc speaker device.
         */
        var isSoundPCSpeakerEnabled: Boolean = false) : NotificationAction(ACTION_SOUND) {
    /**
     * Returns the loop interval. This is the interval of milliseconds to wait before repeating the sound,
     * when playing a sound in loop. By default this method returns -1.
     *
     * @return the loop interval
     */
    /**
     * Changes the loop interval. This is the interval of milliseconds to wait before repeating the sound,
     * when playing a sound in loop.
     *
     * @param loopInterval the loop interval
     */
    /**
     * Returns the descriptor pointing to the sound to be played.
     *
     * @return the descriptor pointing to the sound to be played.
     */
    /**
     * update the descriptor pointing to the sound to be played
     *
     * @param soundFileDescriptor the descriptor pointing to the sound to be played
     */
    /**
     * Returns if this sound is to be played on notification device.
     *
     * @return True if this sound is played on notification device. False Otherwise.
     */
    /**
     * Enables or disables this sound for notification device.
     *
     * @param isSoundEnabled True if this sound is played on notification device. False Otherwise.
     */
    /**
     * Returns if this sound is to be played on playback device.
     *
     * @return True if this sound is played on playback device. False Otherwise.
     */
    /**
     * Enables or disables this sound for playback device.
     *
     * @param isSoundEnabled True if this sound is played on playback device. False Otherwise.
     */
    /**
     * Returns if this sound is to be played on pc speaker device.
     *
     * @return True if this sound is played on pc speaker device. False Otherwise.
     */
    /**
     * Enables or disables this sound for pc speaker device.
     *
     * @param isSoundEnabled True if this sound is played on speaker device. False Otherwise.
     */
    /**
     * Creates an instance of `SoundNotification` by specifying the sound file descriptor and the loop interval.
     *
     * @param descriptor the sound file descriptor
     * @param loopInterval the loop interval
     * @param isSoundNotificationEnabled True if this sound is activated. False Otherwise.
     * @param isSoundPlaybackEnabled True if this sound is activated. False Otherwise.
     * @param isSoundPCSpeakerEnabled True if this sound is activated. False Otherwise.
     */
}