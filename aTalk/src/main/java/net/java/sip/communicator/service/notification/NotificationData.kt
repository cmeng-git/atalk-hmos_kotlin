/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification

import java.util.*

/**
 * Object to cache fired notifications before all handler implementations are ready registered.
 *
 * @author Ingo Bauersachs
 */
class NotificationData
/**
 * Creates a new instance of this class.
 *
 * @param eventType the type of the event that we'd like to fire a notification for.
 * @param msgType the notification sub-category message type
 * @param title the title of the given message
 * @param message the message to use if and where appropriate (e.g. with systray or log notification.)
 * @param icon the icon to show in the notification if and where appropriate
 * @param extras additional/extra [NotificationHandler]-specific data to be provided
 * by the new instance to the various `NotificationHandler`
 */ internal constructor(
        /**
         * The type of the event that we'd like to fire a notification for.
         * @see net.java.sip.communicator.plugin.notificationwiring.NotificationManager
         */
        val eventType: String,
        /**
         * The sub-category of the event type.
         * @see net.java.sip.communicator.service.systray.SystrayService
         */
        val messageType: Int,
        /**
         * Gets the title of the given message.
         *
         * @return the title
         */
        val title: String,
        /**
         * Gets the message to use if and where appropriate (e.g. with systray or log notification).
         *
         * @return the message
         */
        val message: String,
        /**
         * Gets the icon to show in the notification if and where appropriate.
         *
         * @return the icon
         */
        val icon: ByteArray?,
        /**
         * The [NotificationHandler]-specific extras provided to this instance. The keys are among the
         * `XXX_EXTRA` constants defined by the `NotificationData` class.
         */
        private val extras: Map<String, Any>?) {
    /**
     * Gets the type of the event that we'd like to fire a notification for
     *
     * @return the eventType
     */
    /**
     * Gets the msgType of the event that we'd like to fire a notification for
     *
     * @return the msgType
     */

    /**
     * Gets the [NotificationHandler]-specific extras provided to this instance.
     *
     * @return the `NotificationHandler`-specific extras provided to this instance. The keys are among the
     * `XXX_EXTRA` constants defined by the `NotificationData` class
     */
    fun getExtras(): Map<String, Any> {
        return Collections.unmodifiableMap(extras!!)
    }

    /**
     * Gets the [NotificationHandler]-specific extra provided to this instance associated with a specific key.
     *
     * @param key the key whose associated `NotificationHandler`-specific extra is to be returned. Well known keys
     * are defined by the `NotificationData` class as the `XXX_EXTRA` constants.
     * @return the `NotificationHandler`-specific extra provided to this instance associated with the specified
     * `key`
     */
    fun getExtra(key: String): Any? {
        return extras?.get(key)
    }

    companion object {
        /**
         * The name/key of the `NotificationData` extra which is provided to
         * [CommandNotificationHandler.execute] i.e. a
         * `Map<String,String>` which is known by the (argument) name `cmdargs`.
         */
        const val COMMAND_NOTIFICATION_HANDLER_CMDARGS_EXTRA = "CommandNotificationHandler.cmdargs"

        /**
         * The name/key of the `NotificationData` extra which is provided to
         * [PopupMessageNotificationHandler.popupMessage]
         * i.e. an `Object` which is known by the (argument) name `tag`.
         */
        const val POPUP_MESSAGE_HANDLER_TAG_EXTRA = "PopupMessageNotificationHandler.tag"

        /**
         * The name/key of the `NotificationData` extra which is provided to [SoundNotificationHandler] i.e. a
         * `Callable<Boolean>` which is known as the condition which determines whether looping sounds are to
         * continue playing.
         */
        const val SOUND_NOTIFICATION_HANDLER_LOOP_CONDITION_EXTRA = "SoundNotificationHandler.loopCondition"
    }
}