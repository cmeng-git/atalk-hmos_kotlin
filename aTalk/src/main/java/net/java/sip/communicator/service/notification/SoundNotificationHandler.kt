/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification

/**
 * The `SoundNotificationHandler` interface is meant to be implemented by the notification bundle
 * in order to provide handling of sound actions.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface SoundNotificationHandler : NotificationHandler {
    /**
     * Start playing the sound pointed by `getDescriotor`. This method should check the
     * loopInterval value to distinguish whether to play a simple sound or to play it in loop.
     *
     * @param action the action to act upon
     * @param data Additional data for the event.
     */
    fun start(action: SoundNotificationAction, data: NotificationData?)

    /**
     * Stops playing the sound pointing by `getDescriptor`. This method is meant to be used
     * to stop sounds that are played in loop.
     *
     * @param data Additional data for the event.
     */
    fun stop(data: NotificationData?)

    /**
     * Specifies if currently the sound is off.
     *
     * @return TRUE if currently the sound is off, FALSE otherwise
     */
    var isMute: Boolean

    /**
     * Tells if the given notification sound is currently played.
     *
     * @param data Additional data for the event.
     */
    fun isPlaying(data: NotificationData?): Boolean
}