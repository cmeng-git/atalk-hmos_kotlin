/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidtray

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import org.atalk.hmos.aTalkApp
import timber.log.Timber

/**
 * This `BroadcastReceiver` listens for `PendingIntent` coming from popup messages notifications. There
 * are two actions handled:<br></br>
 * - `POPUP_CLICK_ACTION` fired when notification is clicked<br></br>
 * - `POPUP_CLEAR_ACTION` fired when notification is cleared<br></br>
 * Those events are passed to `NotificationPopupHandler` to take appropriate decisions.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class PopupClickReceiver
/**
 * Creates new instance of `PopupClickReceiver` bound to given `notificationHandler`.
 *
 * @param notificationHandler the `NotificationPopupHandler` that manages the popups.
 */
(
        /**
         * `NotificationPopupHandler` that manages the popups.
         */
        private val notificationHandler: NotificationPopupHandler) : BroadcastReceiver() {
    /**
     * Registers this `BroadcastReceiver`.
     */
    fun registerReceiver() {
        val filter = IntentFilter()
        filter.addAction(ACTION_POPUP_CLICK)
        filter.addAction(ACTION_POPUP_CLEAR)
        filter.addAction(ACTION_REPLY_TO)
        filter.addAction(ACTION_MARK_AS_READ)
        filter.addAction(ACTION_SNOOZE)
        filter.addAction(ACTION_CALL_ANSWER)
        filter.addAction(ACTION_CALL_DISMISS)
        aTalkApp.globalContext.registerReceiver(this, filter)
    }

    /**
     * Unregisters this `BroadcastReceiver`.
     */
    fun unregisterReceiver() {
        aTalkApp.globalContext.unregisterReceiver(this)
    }

    /**
     * {@inheritDoc}
     */
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId == -1) {
            Timber.w("Invalid notification id = -1")
            return
        }
        val action = intent.action
        Timber.d("Popup action: %s", action)
        if (action == null) return
        when (action) {
            ACTION_POPUP_CLICK -> notificationHandler.fireNotificationClicked(notificationId)
            ACTION_REPLY_TO -> notificationHandler.fireNotificationClicked(notificationId, intent)
            ACTION_POPUP_CLEAR, ACTION_MARK_AS_READ, ACTION_SNOOZE, ACTION_CALL_ANSWER, ACTION_CALL_DISMISS -> notificationHandler.fireNotificationClicked(notificationId, action)
            else -> Timber.w("Unsupported action: %s", action)
        }
    }

    companion object {
        /**
         * Popup clicked action name used for `Intent` handling by this `BroadcastReceiver`.
         */
        const val ACTION_POPUP_CLICK = "org.atalk.ui.popup_click"

        /**
         * Popup cleared action name used for `Intent` handling by this `BroadcastReceiver`
         */
        const val ACTION_POPUP_CLEAR = "org.atalk.ui.popup_discard"

        /**
         * Android Notification Actions
         */
        const val ACTION_MARK_AS_READ = "mark_as_read"
        const val ACTION_SNOOZE = "snooze"
        private const val ACTION_REPLY_TO = "reply_to"

        // private static final String ACTION_CLEAR_NOTIFICATION = "clear_notification";
        // private static final String ACTION_DISMISS_ERROR_NOTIFICATIONS = "dismiss_error";
        const val ACTION_CALL_DISMISS = "call_dismiss"
        const val ACTION_CALL_ANSWER = "call_answer"

        /**
         * `Intent` extra key that provides the notification id.
         */
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        /**
         * Creates "on click" `Intent` for notification popup identified by given `notificationId`.
         *
         * @param notificationId the id of popup message notification.
         * @return new "on click" `Intent` for given `notificationId`.
         */
        fun createIntent(notificationId: Int): Intent {
            val intent = Intent()
            intent.action = ACTION_POPUP_CLICK
            intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            return intent
        }

        /**
         * Creates "on deleted" `Intent` for notification popup identified by given `notificationId`.
         *
         * @param notificationId the id of popup message notification.
         * @return new "on deleted" `Intent` for given `notificationId`.
         */
        fun createDeleteIntent(notificationId: Int): Intent {
            val intent = Intent()
            intent.action = ACTION_POPUP_CLEAR
            intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            return intent
        }

        /**
         * Creates "on deleted" `Intent` for notification popup identified by given `notificationId`.
         *
         * @param notificationId the id of popup message notification.
         * @return new "on deleted" `Intent` for given `notificationId`.
         */
        fun createReplyIntent(notificationId: Int): Intent {
            val intent = Intent()
            intent.action = ACTION_REPLY_TO
            intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            return intent
        }

        /**
         * Creates "on deleted" `Intent` for notification popup identified by given `notificationId`.
         *
         * @param notificationId the id of popup message notification.
         * @return new "on deleted" `Intent` for given `notificationId`.
         */
        fun createMarkAsReadIntent(notificationId: Int): Intent {
            val intent = Intent()
            intent.action = ACTION_MARK_AS_READ
            intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            return intent
        }

        /**
         * Creates "on deleted" `Intent` for notification popup identified by given `notificationId`.
         *
         * @param notificationId the id of popup message notification.
         * @return new "on deleted" `Intent` for given `notificationId`.
         */
        fun createSnoozeIntent(notificationId: Int): Intent {
            val intent = Intent()
            intent.action = ACTION_SNOOZE
            intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            return intent
        }

        /**
         * Creates call dismiss `Intent` for notification popup identified by given `notificationId`.
         *
         * @param notificationId the id of popup message notification.
         * @return new dismiss `Intent` for given `notificationId`.
         */
        fun createCallDismiss(notificationId: Int): Intent {
            val intent = Intent()
            intent.action = ACTION_CALL_DISMISS
            intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            return intent
        }
    }
}