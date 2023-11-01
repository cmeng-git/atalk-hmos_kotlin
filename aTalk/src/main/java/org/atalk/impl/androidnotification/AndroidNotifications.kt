/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidnotification

import net.java.sip.communicator.plugin.notificationwiring.NotificationManager
import net.java.sip.communicator.plugin.notificationwiring.SoundProperties
import net.java.sip.communicator.service.notification.NotificationService
import net.java.sip.communicator.service.notification.PopupMessageNotificationAction
import net.java.sip.communicator.service.notification.SoundNotificationAction
import net.java.sip.communicator.service.notification.VibrateNotificationAction
import net.java.sip.communicator.util.ServiceUtils.getService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import java.util.*

/**
 * Android notifications wiring which overrides some default notifications and adds vibrate actions.
 *
 *
 * All pop-message events must be assigned to any one of the android xxx_GROUP, otherwise it will be blocked.
 * Each of these xxx_GROUP's will appear in android Notifications setting and user may disable it.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AndroidNotifications : BundleActivator {
    /**
     * Overrides SIP default notifications to suit Android devices available resources
     *
     * {@inheritDoc}
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        val notificationService = getService(bundleContext, NotificationService::class.java)
                ?: return

        // Incoming call: modified default incoming call notification to be played only on notification stream.
        val inCallSoundHandler = SoundNotificationAction(SoundProperties.INCOMING_CALL, 2000, true, false, false)
        notificationService.registerDefaultNotificationForEvent(NotificationManager.INCOMING_CALL, inCallSoundHandler)

        // Incoming call: Adds basic vibrate notification for incoming call
        val inCallVibrate = VibrateNotificationAction(NotificationManager.INCOMING_CALL, longArrayOf(1800, 1000), 0)
        notificationService.registerDefaultNotificationForEvent(NotificationManager.INCOMING_CALL, inCallVibrate)

        //  cmeng 20200525: added back for JingleMessage support
        // Incoming call Popup Message: replace with aTalk INCOMING_CALL popup message;
        // notificationService.removeEventNotificationAction(NotificationManager.INCOMING_CALL,
        //        NotificationAction.ACTION_POPUP_MESSAGE);

        // Incoming call : new(No default message, Notification hide timeout, displayed on incoming call icon)
        notificationService.registerDefaultNotificationForEvent(NotificationManager.INCOMING_CALL,
                PopupMessageNotificationAction(null, -1, CALL_GROUP))

        // Missed call : new(No default message, Notification hide timeout, displayed on missed call icon)
        notificationService.registerDefaultNotificationForEvent(NotificationManager.MISSED_CALL,
                PopupMessageNotificationAction(null, -1, CALL_GROUP))
        notificationService.registerDefaultNotificationForEvent(NotificationManager.CALL_SECURITY_ERROR,
                PopupMessageNotificationAction(null, -1, CALL_GROUP))
        notificationService.registerDefaultNotificationForEvent(NotificationManager.SECURITY_MESSAGE,
                PopupMessageNotificationAction(null, -1, CALL_GROUP))

        // Incoming message: new(No default message, Notification hide timeout, displayed on aTalk icon)
        notificationService.registerDefaultNotificationForEvent(NotificationManager.INCOMING_MESSAGE,
                PopupMessageNotificationAction(null, -1, MESSAGE_GROUP))

        // Incoming file: new(No default message, Notification hide timeout, displayed on aTalk icon)
        notificationService.registerDefaultNotificationForEvent(NotificationManager.INCOMING_FILE,
                PopupMessageNotificationAction(null, -1, FILE_GROUP))

        // Proactive notifications: new(No default message, Notification hide timeout, displayed on aTalk icon)
        notificationService.registerDefaultNotificationForEvent(NotificationManager.PROACTIVE_NOTIFICATION,
                PopupMessageNotificationAction(null, 7000, SILENT_GROUP))
        notificationService.registerDefaultNotificationForEvent(NotificationManager.INCOMING_INVITATION,
                SoundNotificationAction(SoundProperties.INCOMING_INVITATION, -1, true, false, false))

        // Remove obsoleted/unused events
        notificationService.removeEventNotification(NotificationManager.CALL_SAVED)
    }

    /**
     * {@inheritDoc}
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
    }

    companion object {
        /**
         * Calls notification group.
         */
        const val CALL_GROUP = "call"

        /**
         * Message notifications group.
         */
        const val MESSAGE_GROUP = "message"

        /**
         * Calls notification group.
         */
        const val FILE_GROUP = "file"

        /**
         * Default group that uses aTalk icon for notifications
         */
        const val DEFAULT_GROUP = "default"

        /**
         * Missed call event.
         */
        const val SILENT_GROUP = "silent"
        var notificationIds = Arrays.asList(CALL_GROUP, MESSAGE_GROUP, FILE_GROUP, DEFAULT_GROUP, SILENT_GROUP)
    }
}