/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidtray

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Builder
import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.impl.protocol.jabber.ChatRoomJabberImpl
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.systray.PopupMessage
import net.java.sip.communicator.service.systray.SystrayService
import net.java.sip.communicator.util.ConfigurationUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.chat.ChatPanel
import org.atalk.hmos.gui.chat.ChatSessionManager
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.util.AndroidImageUtil
import org.atalk.impl.androidnotification.AndroidNotifications
import timber.log.Timber
import java.util.*

/**
 * Class manages displayed notification for given `PopupMessage`.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class AndroidPopup(
        /**
         * Parent notifications handler
         */
        val handler: NotificationPopupHandler?,
        /**
         * Displayed `PopupMessage`.
         */
        var popupMessage: PopupMessage?) {

    /**
     * Returns displayed `PopupMessage`.
     *
     * @return displayed `PopupMessage`.
     */
    /**
     * Timeout handler.
     */
    private var timeoutHandler: Timer? = null
    /**
     * Returns notification id.
     *
     * @return notification id.
     */
    /**
     * Notification id.
     */
    var id: Int

    /**
     * Optional chatTransport descriptor if supplied by `PopupMessage`.
     */
    private val mDescriptor: Any?

    /*
     * Notification channel group
     */
    private val group = popupMessage!!.group.toString()

    /**
     * Small icon used for this notification.
     */
    var popupIcon = 0
    private val mContext = aTalkApp.globalContext
    private var muteEndTime: Long? = null

    /**
     * Creates new instance of `AndroidPopup`.
     *
     * handler parent notifications handler that manages displayed notifications.
     * popupMessage the popup message that will be displayed by this instance.
     */
    init {
        id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        when (group) {
            AndroidNotifications.MESSAGE_GROUP -> popupIcon = R.drawable.incoming_message
            AndroidNotifications.SILENT_GROUP -> popupIcon = R.drawable.incoming_message
            AndroidNotifications.FILE_GROUP -> popupIcon = R.drawable.ic_attach_dark
            AndroidNotifications.CALL_GROUP -> popupIcon = when (popupMessage!!.messageType) {
                SystrayService.WARNING_MESSAGE_TYPE -> R.drawable.ic_alert_dark
                SystrayService.JINGLE_INCOMING_CALL, SystrayService.JINGLE_MESSAGE_PROPOSE -> R.drawable.call_incoming
                SystrayService.MISSED_CALL_MESSAGE_TYPE -> R.drawable.call_incoming_missed
                else -> R.drawable.ic_info_dark
            }
            AndroidNotifications.DEFAULT_GROUP -> {
                id = SystrayServiceImpl.generalNotificationId
                popupIcon = R.drawable.ic_notification
            }
            else -> {
                id = SystrayServiceImpl.generalNotificationId
                popupIcon = R.drawable.ic_notification
            }
        }
        // Extract contained chat descriptor if any
        mDescriptor = popupMessage!!.tag
    }

    /**
     * Removes this notification.
     */
    fun removeNotification(id: Int) {
        cancelTimeout()
        snoozeEndTimes.remove(id)
        val notifyManager = aTalkApp.notificationManager
        notifyManager.cancel(this.id)
    }

    /**
     * Returns `true` if this popup is related to given `ChatPanel`.
     *
     * @param chatPanel the `ChatPanel` to check.
     * @return `true` if this popup is related to given `ChatPanel`.
     */
    fun isChatRelated(chatPanel: ChatPanel?): Boolean {
        return if (chatPanel != null) {
            val descriptor = chatPanel.chatSession!!.currentChatTransport!!.descriptor
            descriptor != null && descriptor == mDescriptor && (AndroidNotifications.MESSAGE_GROUP == group || AndroidNotifications.FILE_GROUP == group)
        } else {
            false
        }
    }

    /**
     * Tries to merge given `PopupMessage` with this instance. Will return merged
     * `AndroidPopup` or `null` otherwise.
     *
     * @param popupMessage the `PopupMessage` to merge.
     * @return merged `AndroidPopup` with given `PopupMessage` or `null` otherwise.
     */
    fun tryMerge(popupMessage: PopupMessage): AndroidPopup? {
        return if (isGroupTheSame(popupMessage) && isSenderTheSame(popupMessage)) {
            mergePopup(popupMessage)
        } else {
            null
        }
    }

    /**
     * Merges this instance with given `PopupMessage`.
     *
     * @param popupMessage the `PopupMessage` to merge.
     * @return merge result for this `AndroidPopup` and given `PopupMessage`.
     */
    protected open fun mergePopup(popupMessage: PopupMessage?): AndroidPopup? {
        // Timeout notifications are replaced
        /*
         * if(this.timeoutHandler != null) { cancelTimeout(); this.popupMessage = popupMessage;
         * return this; } else {
         */
        val merge = AndroidMergedPopup(this)
        merge.mergePopup(popupMessage)
        return merge
        // }
    }

    /**
     * Checks whether `Contact` of this instance matches with given `PopupMessage`.
     *
     * @param popupMessage the `PopupMessage` to check.
     * @return `true` if `Contact`s for this instance and given `PopupMessage` are the same.
     */
    private fun isSenderTheSame(popupMessage: PopupMessage): Boolean {
        return mDescriptor != null && mDescriptor == popupMessage.tag
    }

    /**
     * Checks whether group of this instance matches with given `PopupMessage`.
     *
     * @param popupMessage the `PopupMessage` to check.
     * @return `true` if group of this instance and given `PopupMessage` are the same.
     */
    private fun isGroupTheSame(popupMessage: PopupMessage): Boolean {
        return if (this.popupMessage!!.group == null) {
            popupMessage.group == null
        } else {
            this.popupMessage!!.group == popupMessage.group
        }
    }

    /**
     * Returns message string that will displayed in single line notification.
     *
     * @return message string that will displayed in single line notification.
     */
    open val message: String?
        get() = popupMessage!!.message

    /**
     * Builds notification and returns the builder object which can be used to extend the notification.
     *
     * @return builder object describing current notification.
     */
    open fun buildNotification(nId: Int): Builder? {
        // Do not show heads-up notification when user has put the id notification in snooze
        val builder = if (isSnooze(nId) || !ConfigurationUtils.isHeadsUpEnable) {
            Builder(mContext, AndroidNotifications.SILENT_GROUP)
        } else {
            Builder(mContext, group)
        }
        builder.setSmallIcon(popupIcon)
                .setContentTitle(popupMessage!!.messageTitle)
                .setContentText(message)
                .setAutoCancel(true) // will be cancelled once clicked
                .setVibrate(longArrayOf()) // no vibration
                .setSound(null) // no sound
        val res = aTalkApp.appResources
        // Preferred size
        val prefWidth = res.getDimension(android.R.dimen.notification_large_icon_width).toInt()
        val prefHeight = res.getDimension(android.R.dimen.notification_large_icon_height).toInt()

        // Use popup icon if provided
        var iconBmp: Bitmap? = null
        val icon = popupMessage!!.icon
        if (icon != null) {
            iconBmp = AndroidImageUtil.scaledBitmapFromBytes(icon, prefWidth, prefHeight)
        }

        // Set default avatar if none provided
        if (iconBmp == null && mDescriptor != null) {
            iconBmp = if (mDescriptor is ChatRoom) AndroidImageUtil.scaledBitmapFromResource(res, R.drawable.ic_chatroom, prefWidth, prefHeight) else AndroidImageUtil.scaledBitmapFromResource(res, R.drawable.contact_avatar, prefWidth, prefHeight)
        }
        if (iconBmp != null) {
            if (iconBmp.width > prefWidth || iconBmp.height > prefHeight) {
                iconBmp = Bitmap.createScaledBitmap(iconBmp, prefWidth, prefHeight, true)
            }
            builder.setLargeIcon(iconBmp)
        }

        // Build inbox style
        val inboxStyle = NotificationCompat.InboxStyle()
        onBuildInboxStyle(inboxStyle)
        builder.setStyle(inboxStyle)
        return builder
    }

    /**
     * Returns the `PendingIntent` that should be trigger when user clicks the notification.
     *
     * @return the `PendingIntent` that should be trigger by notification
     */
    fun createContentIntent(): PendingIntent? {
        var targetIntent: Intent? = null
        val message = popupMessage
        val group = message?.group
        if (AndroidNotifications.MESSAGE_GROUP == group || AndroidNotifications.FILE_GROUP == group) {
            val tag = message.tag
            if (tag is Contact) {
                val metaContact = AndroidGUIActivator.contactListService.findMetaContactByContact(tag)
                if (metaContact == null) {
                    Timber.e("Meta contact not found for %s", tag)
                } else {
                    targetIntent = ChatSessionManager.getChatIntent(metaContact)
                }
            } else if (tag is ChatRoomJabberImpl) {
                val chatRoomWrapper = MUCActivator.mucService.getChatRoomWrapperByChatRoom(tag, true)
                if (chatRoomWrapper == null) {
                    Timber.e("ChatRoomWrapper not found for %s", tag.getIdentifier())
                } else {
                    targetIntent = ChatSessionManager.getChatIntent(chatRoomWrapper)
                }
            }
        }
        // Displays popup message details when the notification is clicked when targetIntent is null
        if (message != null && targetIntent == null) {
            targetIntent = DialogActivity.getDialogIntent(aTalkApp.globalContext,
                    message.messageTitle, message.message)
        }

        // Must be unique for each, so use the notification id as the request code
        return if (targetIntent == null) null else PendingIntent.getActivity(aTalkApp.globalContext, id, targetIntent,
                NotificationPopupHandler.getPendingIntentFlag(false, true))
    }

    /**
     * Method fired when large notification view using `InboxStyle` is being built.
     *
     * @param inboxStyle the inbox style instance used for building large notification view.
     */
    protected open fun onBuildInboxStyle(inboxStyle: NotificationCompat.InboxStyle) {
        inboxStyle.addLine(message)
        // Summary
        if (mDescriptor is Contact) {
            val pps = mDescriptor.protocolProvider
            if (pps != null) {
                inboxStyle.setSummaryText(pps.accountID.displayName)
            }
        }
    }

    /**
     * Cancels the timeout if it exists.
     */
    private fun cancelTimeout() {
        // Remove timeout handler
        if (timeoutHandler != null) {
            Timber.d("Removing timeout from notification: %s", id)
            // FFR: NPE: 2.1.5 AndroidPopup.cancelTimeout (AndroidPopup.java:379) ?
            timeoutHandler!!.cancel()
            timeoutHandler = null
        }
    }

    /**
     * Enable snooze for the next 30 minutes
     */
    fun setSnooze(nId: Int) {
        muteEndTime = System.currentTimeMillis() + 30 * 60 * 1000 // 30 minutes
        snoozeEndTimes[nId] = muteEndTime
    }

    /**
     * Check if the given notification ID is still in snooze period
     *
     * @param nId Notification id
     * @return true if it is still in snooze
     */
    fun isSnooze(nId: Int): Boolean {
        muteEndTime = snoozeEndTimes[nId]
        return muteEndTime != null && System.currentTimeMillis() < muteEndTime!!
    }

    /**
     * Check if the android heads-up notification allowed
     *
     * @return true if the group is MESSAGE_GROUP
     */
    val isHeadUpNotificationAllow: Boolean
        get() = ConfigurationUtils.isHeadsUpEnable && (AndroidNotifications.MESSAGE_GROUP == group || AndroidNotifications.CALL_GROUP == group)

    /**
     * Method called by notification manger when the notification is posted to the tray.
     */
    fun onPost() {
        cancelTimeout()
        val timeout = popupMessage!!.timeout
        if (timeout > 0) {
            Timber.d("Setting timeout %d; on notification: %d", timeout, id)
            timeoutHandler = Timer()
            timeoutHandler!!.schedule(object : TimerTask() {
                override fun run() {
                    handler!!.onTimeout(this@AndroidPopup)
                }
            }, timeout)
        }
    }

    companion object {
        /**
         * Stores all the endMuteTime for each notification Id.
         */
        private val snoozeEndTimes = Hashtable<Int, Long>()

        /**
         * Creates new `AndroidPopup` for given parameters.
         *
         * @param handler notifications manager.
         * @param popupMessage the popup message that will be displayed by returned `AndroidPopup`
         * @return new `AndroidPopup` for given parameters.
         */
        fun createNew(handler: NotificationPopupHandler?, popupMessage: PopupMessage?): AndroidPopup {
            return AndroidPopup(handler, popupMessage)
        }
    }
}