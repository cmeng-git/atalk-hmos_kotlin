/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification

/**
 * Base class for actions of a notification.
 *
 * @author Ingo Bauersachs
 */
abstract class NotificationAction
/**
 * Creates a new instance of this class.
 *
 * @param actionType
 * The action type name.
 */ protected constructor(
        /**
         * The action type name.
         */
        val actionType: String) {
    /**
     * Returns TRUE if this notification action handler is enabled and FALSE otherwise. While the notification handler
     * for the sound action type is disabled no sounds will be played when the `fireNotification` method is
     * called.
     *
     * @return TRUE if this notification action handler is enabled and FALSE otherwise
     */
    /**
     * Enables or disables this notification handler. While the notification handler for the sound action type is
     * disabled no sounds will be played when the `fireNotification` method is called.
     *
     * @param isEnabled
     * TRUE to enable this notification handler, FALSE to disable it.
     */
    /**
     * Indicates if this handler is enabled.
     */
    var isEnabled = true

    /**
     * Return the action type name.
     *
     * @return the action type name.
     */

    companion object {
        /**
         * The sound action type indicates that a sound would be played, when a notification is fired.
         */
        const val ACTION_SOUND = "SoundAction"

        /**
         * The popup message action type indicates that a window (or a systray popup), containing the corresponding
         * notification message would be poped up, when a notification is fired.
         */
        const val ACTION_POPUP_MESSAGE = "PopupMessageAction"

        /**
         * The log message action type indicates that a message would be logged, when a notification is fired.
         */
        const val ACTION_LOG_MESSAGE = "LogMessageAction"

        /**
         * The command action type indicates that a command would be executed, when a notification is fired.
         */
        const val ACTION_COMMAND = "CommandAction"

        /**
         * The vibrate action type indicates that the device will vibrate, when a notification is fired.
         */
        const val ACTION_VIBRATE = "VibrateAction"
    }
}