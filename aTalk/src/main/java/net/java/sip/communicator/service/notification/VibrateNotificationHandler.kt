/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification

/**
 * The `VibrateNotificationHandler` interface is meant to be implemented by the
 * notification bundle in order to provide handling of vibrate actions.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
interface VibrateNotificationHandler : NotificationHandler {
    /**
     * Perform vibration patter defined in given `vibrateAction`.
     *
     * @param vibrateAction the `VibrateNotificationAction` containing vibration pattern details.
     */
    fun vibrate(vibrateAction: VibrateNotificationAction?)

    /**
     * Turn the vibrator off.
     */
    fun cancel()
}