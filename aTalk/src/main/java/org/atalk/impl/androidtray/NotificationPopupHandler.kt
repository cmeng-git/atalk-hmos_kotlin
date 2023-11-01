/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidtray

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.impl.protocol.jabber.ChatRoomJabberImpl
import net.java.sip.communicator.plugin.notificationwiring.NotificationManager
import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.systray.AbstractPopupMessageHandler
import net.java.sip.communicator.service.systray.PopupMessage
import net.java.sip.communicator.service.systray.SystrayService
import net.java.sip.communicator.service.systray.event.SystrayPopupMessageEvent
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.call.CallManager
import org.atalk.hmos.gui.call.JingleMessageCallActivity
import org.atalk.hmos.gui.call.JingleMessageSessionImpl
import org.atalk.hmos.gui.call.ReceivedCallActivity
import org.atalk.hmos.gui.chat.ChatPanel
import org.atalk.hmos.gui.chat.ChatSessionManager
import org.atalk.hmos.gui.chat.ChatSessionManager.CurrentChatListener
import org.atalk.hmos.gui.chatroomslist.ChatRoomListFragment
import org.atalk.hmos.gui.contactlist.ContactListFragment
import org.atalk.hmos.gui.util.AndroidUtils
import org.atalk.impl.androidnotification.AndroidNotifications
import org.atalk.service.osgi.OSGiService
import timber.log.Timber

/**
 * Displays popup messages as Android status bar notifications.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class NotificationPopupHandler : AbstractPopupMessageHandler(), CurrentChatListener {
    private val mContext = aTalkApp.globalContext

    /**
     * Creates new instance of `NotificationPopupHandler`. Registers as active chat listener.
     */
    init {
        ChatSessionManager.addCurrentChatListener(this)
    }

    /**
     * {@inheritDoc}
     */
    override fun showPopupMessage(popupMessage: PopupMessage) {
        var newPopup: AndroidPopup? = null
        // Check for existing notifications and create mergePopUp else create new
        for (popup in notificationMap.values) {
            val merge = popup!!.tryMerge(popupMessage)
            if (merge != null) {
                newPopup = merge
                break
            }
        }
        if (newPopup == null) {
            newPopup = AndroidPopup.createNew(this, popupMessage)
        }

        // Create the notification base view
        val nId = newPopup.id
        val mBuilder = newPopup.buildNotification(nId)

        // Create and register the content intent for click action
        mBuilder!!.setContentIntent(newPopup.createContentIntent())

        // Register delete intent
        mBuilder.setDeleteIntent(createDeleteIntent(nId))
        mBuilder.setWhen(0)

        // Must setFullScreenIntent to wake android from sleep and for heads-up to stay on
        // heads-up notification is for both the Jingle Message propose and Jingle incoming call
        // Do no tie this to Note-10 Edge-light, else call UI is not shown
        when (popupMessage.group) {
            AndroidNotifications.CALL_GROUP ->                 // if (!aTalkApp.isForeground && NotificationManager.INCOMING_CALL.equals(popupMessage.getEventType())) {
                if (NotificationManager.INCOMING_CALL == popupMessage.eventType) {
                    val tag = popupMessage.tag ?: return
                    val mSid = tag as String
                    callNotificationMap[mSid] = nId

                    // Note: Heads-up prompt is not shown under android locked screen, it auto launches activity.
                    // So disable auto-answer (JMC) in this case; hence allow user choice to cancel/accept incoming call
                    // For jingleMessage propose => JingleMessageCallActivity;
                    val fullScreenIntent: Intent
                    val msgType = popupMessage.messageType
                    Timber.d("Pop up message type: %s; mSid: %s; nId: %s", msgType, mSid, nId)
                    fullScreenIntent = if (SystrayService.JINGLE_MESSAGE_PROPOSE == msgType) {
                        Intent(mContext, JingleMessageCallActivity::class.java)
                                .putExtra(CallManager.CALL_SID, mSid)
                                .putExtra(CallManager.AUTO_ACCEPT, !aTalkApp.isDeviceLocked)
                                .putExtra(CallManager.CALL_EVENT, NotificationManager.INCOMING_CALL)
                    } else {
                        Intent(mContext, ReceivedCallActivity::class.java)
                                .putExtra(CallManager.CALL_SID, mSid)
                                .putExtra(CallManager.AUTO_ACCEPT, SystrayService.HEADS_UP_INCOMING_CALL == msgType)
                    }
                    val fullScreenPendingIntent = PendingIntent.getActivity(aTalkApp.globalContext,
                            0, fullScreenIntent, getPendingIntentFlag(isMutable = false, isUpdate = true))
                    mBuilder.setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setCategory(Notification.CATEGORY_CALL)
                            .setFullScreenIntent(fullScreenPendingIntent, true)
                            .setOngoing(true)
                            .setAutoCancel(false) // must not allow user to cancel, else no UI to take call

                    // Build end call action
                    val dismissAction = NotificationCompat.Action.Builder(
                            R.drawable.ic_call_end_light,
                            aTalkApp.getResString(R.string.service_gui_DISMISS),
                            createDismissIntent(nId)).build()
                    mBuilder.addAction(dismissAction)

                    // Build answer call action
                    val answerAction = NotificationCompat.Action.Builder(
                            R.drawable.ic_call_light,
                            aTalkApp.getResString(R.string.service_gui_ANSWER),
                            fullScreenPendingIntent).build()
                    mBuilder.addAction(answerAction)
                }
            AndroidNotifications.MESSAGE_GROUP -> if (!aTalkApp.isForeground && !newPopup.isSnooze(nId) && newPopup.isHeadUpNotificationAllow) {
                mBuilder.priority = NotificationCompat.PRIORITY_HIGH

                // Build Mark as read action
                val markReadAction = NotificationCompat.Action.Builder(
                        R.drawable.ic_read_dark,
                        aTalkApp.getResString(R.string.service_gui_MAS),
                        createReadPendingIntent(nId))
                        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                        .setShowsUserInterface(false)
                        .build()
                mBuilder.addAction(markReadAction)

                // Build Reply action for OS >= android-N
                val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                        .setLabel("Quick reply")
                        .build()
                val replyAction = NotificationCompat.Action.Builder(
                        R.drawable.ic_send_text_dark,
                        aTalkApp.getResString(R.string.service_gui_REPLY),
                        createReplyIntent(nId))
                        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                        .setShowsUserInterface(false)
                        .addRemoteInput(remoteInput)
                        .build()
                mBuilder.addAction(replyAction)

                // Build Snooze action if more than the specific limit has been reached
                if (newPopup is AndroidMergedPopup) {
                    if (newPopup.displaySnoozeAction()) {
                        val snoozeAction = NotificationCompat.Action.Builder(
                                R.drawable.ic_notifications_paused_dark,
                                aTalkApp.getResString(R.string.service_gui_SNOOZE),
                                createSnoozeIntent(nId)).build()
                        mBuilder.addAction(snoozeAction)
                    }
                }
            }
        }

        // caches the notification until clicked or cleared
        notificationMap[nId] = newPopup

        // post the notification
        aTalkApp.notificationManager.notify(nId, mBuilder.build())
        newPopup.onPost()
    }

    /**
     * Create a pending intent onDelete
     *
     *id Must be unique for each, so use notification id as request code
     * @return Delete PendingIntent
     */
    private fun createDeleteIntent(id: Int): PendingIntent {
        val intent = PopupClickReceiver.createDeleteIntent(id)
        return PendingIntent.getBroadcast(mContext, id, intent, getPendingIntentFlag(isMutable = false, isUpdate = true))
    }

    /**
     * Create a pending intent onReply
     *
     *id Must be unique for each, so use notification id as request code
     * @return Delete PendingIntent
     */
    private fun createReplyIntent(id: Int): PendingIntent {
        val intent = PopupClickReceiver.createReplyIntent(id)
        return PendingIntent.getBroadcast(mContext, id, intent, getPendingIntentFlag(isMutable = true, isUpdate = false))
    }

    /**
     * Create a pending intent on message readPending
     *
     *id Must be unique for each, so use notification id as request code
     * @return Delete PendingIntent
     */
    private fun createReadPendingIntent(id: Int): PendingIntent {
        val intent = PopupClickReceiver.createMarkAsReadIntent(id)
        return PendingIntent.getBroadcast(mContext, id, intent, getPendingIntentFlag(isMutable = true, isUpdate = true))
    }

    /**
     * Create a pending intent onSnooze
     *
     *id Must be unique for each, so use notification id as request code
     * @return Delete PendingIntent
     */
    private fun createSnoozeIntent(id: Int): PendingIntent {
        val intent = PopupClickReceiver.createSnoozeIntent(id)
        return PendingIntent.getBroadcast(mContext, id, intent, getPendingIntentFlag(isMutable = true, isUpdate = true))
    }

    /**
     * Create a pending intent onDismiss call
     *
     *id Must be unique for each, so use notification id as request code
     * @return Delete PendingIntent
     */
    private fun createDismissIntent(id: Int): PendingIntent {
        val intent = PopupClickReceiver.createCallDismiss(id)
        return PendingIntent.getBroadcast(mContext, id, intent, getPendingIntentFlag(isMutable = false, isUpdate = true))
    }

    /**
     * Fires `SystrayPopupMessageEvent` for clicked notification.
     *
     *notificationId the id of clicked notification.
     */
    fun fireNotificationClicked(notificationId: Int) {
        val popup = notificationMap[notificationId]
        if (popup == null) {
            Timber.e("No valid notification exists for %s", notificationId)
            return
        }
        val msg = popup.popupMessage
        if (msg == null) {
            Timber.e("No popup message found for %s", notificationId)
            return
        }
        firePopupMessageClicked(SystrayPopupMessageEvent(msg, msg.tag))
        removeNotification(notificationId)
    }

    /**
     * Fires `SystrayPopupMessageEvent` for clicked notification.
     *
     *notificationId the id of clicked notification.
     */
    fun fireNotificationClicked(notificationId: Int, intent: Intent?) {
        val popup = notificationMap[notificationId]
        if (popup == null) {
            Timber.e("No valid notification exists for %s", notificationId)
            return
        }
        val message = popup.popupMessage
        val group = message?.group
        val remoteInput = RemoteInput.getResultsFromIntent(intent!!)
        var replyText: CharSequence? = null
        if (remoteInput != null) {
            replyText = remoteInput.getCharSequence(KEY_TEXT_REPLY)
        }
        val repliedNotification: Notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            repliedNotification = Notification.Builder(mContext, group)
                    .setSmallIcon(popup.popupIcon)
                    .setContentText(replyText)
                    .build()

            // Issue the new notification to acknowledge
            aTalkApp.notificationManager.notify(notificationId, repliedNotification)
        }
        if (!TextUtils.isEmpty(replyText) && AndroidNotifications.MESSAGE_GROUP == group) {
            var chatPanel: ChatPanel? = null
            val tag = message.tag
            if (tag is Contact) {
                val metaContact = AndroidGUIActivator.contactListService.findMetaContactByContact(tag)
                if (metaContact != null) {
                    chatPanel = ChatSessionManager.getActiveChat(metaContact.getMetaUID())
                }
            } else if (tag is ChatRoomJabberImpl) {
                val chatRoomWrapper = MUCActivator.mucService.getChatRoomWrapperByChatRoom(tag, false)
                if (chatRoomWrapper != null) {
                    chatPanel = ChatSessionManager.getActiveChat(chatRoomWrapper.chatRoomID)
                }
            }
            if (chatPanel != null) {
                Timber.d("Popup action reply message to: %s %s", tag, replyText)
                chatPanel.sendMessage(replyText.toString(), IMessage.ENCODE_PLAIN)
            }
        }

        // Clear systray notification and reset unread message counter;
        fireNotificationClicked(notificationId, PopupClickReceiver.ACTION_MARK_AS_READ)
    }

    /**
     * Fires `SystrayPopupMessageEvent` for clicked notification with the specified action.
     *
     *notificationId the id of clicked notification.
     *action the action to be perform of clicked notification.
     */
    fun fireNotificationClicked(notificationId: Int, action: String) {
        val popup = notificationMap[notificationId]
        if (popup == null) {
            Timber.e("No valid notification exists for %s", notificationId)
            return
        }

        // Remove the notification for all actions except ACTION_SNOOZE.
        if (PopupClickReceiver.ACTION_SNOOZE != action) removeNotification(notificationId)

        // Retrieve the popup tag to process
        val message = popup.popupMessage
        val tag = message!!.tag
        val jinglePropose = SystrayService.JINGLE_MESSAGE_PROPOSE == message.messageType
        when (action) {
            PopupClickReceiver.ACTION_POPUP_CLEAR -> {}
            PopupClickReceiver.ACTION_MARK_AS_READ -> if (tag is Contact) {
                val metaContact = AndroidGUIActivator.contactListService.findMetaContactByContact(tag)
                metaContact?.setUnreadCount(0)

                val clf = aTalk.getFragment(aTalk.CL_FRAGMENT)
                if (clf is ContactListFragment) {
                    clf.updateUnreadCount(metaContact)
                }
            } else if (tag is ChatRoomJabberImpl) {
                val chatRoomWrapper = MUCActivator.mucService.getChatRoomWrapperByChatRoom(tag, false)
                chatRoomWrapper!!.unreadCount = 0
                val crlf = aTalk.getFragment(aTalk.CRL_FRAGMENT)
                if (crlf is ChatRoomListFragment) {
                    crlf.updateUnreadCount(chatRoomWrapper)
                }
            }
            PopupClickReceiver.ACTION_SNOOZE -> popup.setSnooze(notificationId)
            PopupClickReceiver.ACTION_CALL_DISMISS -> {
                val sid = tag as String
                callNotificationMap.remove(sid)
                if (jinglePropose) {
                    JingleMessageSessionImpl.sendJingleMessageReject(sid)
                } else {
                    val call = CallManager.getActiveCall(sid)
                    if (call != null) {
                        CallManager.hangupCall(call)
                    }
                }
            }
            else -> Timber.w("Unsupported action: %s", action)
        }
        val msg = popup.popupMessage
        if (msg == null) {
            Timber.e("No popup message found for %s", notificationId)
            return
        }
        firePopupMessageClicked(SystrayPopupMessageEvent(msg, msg.tag))
    }

    /**
     * Removes all currently registered notifications from the status bar.
     */
    fun dispose() {
        // Removes active chat listener
        ChatSessionManager.removeCurrentChatListener(this)
        for ((key, value) in notificationMap) {
            value!!.removeNotification(key)
        }
        notificationMap.clear()
    }

    /**
     * {@inheritDoc} <br></br>
     * This implementations scores 3: <br></br>
     * +1 detecting clicks <br></br>
     * +1 being able to match a click to a message <br></br>
     * +1 using a native popup mechanism <br></br>
     */
    override val preferenceIndex = 3

    override fun toString(): String {
        // return aTalkApp.getResString(R.string.impl_popup_status_bar);
        return javaClass.name
    }

    /**
     * Method called by `AndroidPopup` to signal the timeout.
     *
     *popup `AndroidPopup` on which timeout event has occurred.
     */
    fun onTimeout(popup: AndroidPopup) {
        removeNotification(popup.id)
    }

    /**
     * {@inheritDoc}
     */
    override fun onCurrentChatChanged(chatId: String) {
        // Clears chat notification related to currently opened chat for incomingMessage & incomingFile
        val openChat = ChatSessionManager.getActiveChat(chatId) ?: return
        val chatPopups = ArrayList<AndroidPopup?>()
        for (popup in notificationMap.values) {
            if (popup!!.isChatRelated(openChat)) {
                chatPopups.add(popup)
                break
            }
        }
        for (chatPopup in chatPopups) {
            removeNotification(chatPopup!!.id)
        }
    }

    companion object {
        private const val KEY_TEXT_REPLY = "key_text_reply"

        /**
         * Map of currently displayed `AndroidPopup`s. Value is removed when
         * corresponding notification is clicked or discarded.
         */
        private val notificationMap = HashMap<Int, AndroidPopup?>()

        /**
         * Map of call sid to notificationId, for remote removing of heads-up notification
         */
        private val callNotificationMap = HashMap<String, Int>()

        /**
         * [Behavior changes: Apps targeting Android 12](https://developer.android.com/about/versions/12/behavior-changes-12#pending-intent-mutability)
         * Android 12 must specify the mutability of each PendingIntent object that your app creates.
         *
         * @return Pending Intent Flag based on API
         */
        fun getPendingIntentFlag(isMutable: Boolean, isUpdate: Boolean): Int {
            var flag = if (isUpdate) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_CANCEL_CURRENT
            if (isMutable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flag = flag or PendingIntent.FLAG_MUTABLE
            } else if (!isMutable) {
                flag = flag or PendingIntent.FLAG_IMMUTABLE
            }
            return flag
        }

        /**
         * Removes notification for given `notificationId` and performs necessary cleanup.
         *
         *notificationId the id of notification to remove.
         */
        private fun removeNotification(notificationId: Int) {
            if (notificationId == OSGiService.generalNotificationId) {
                AndroidUtils.clearGeneralNotification(aTalkApp.globalContext)
            }
            val popup = notificationMap[notificationId]
            if (popup == null) {
                Timber.w("Notification for id: %s already removed", notificationId)
                return
            }
            Timber.d("Removing notification popup: %s", notificationId)
            popup.removeNotification(notificationId)
            notificationMap.remove(notificationId)
        }

        /**
         * Clear the entry in the callNotificationMap for the specified call Id.
         * The callNotificationMap entry for the callId must be cleared, so the Ring tone will stop
         *
         *callId call Id / Jingle Sid
         * @see JingleMessageSessionImpl.onCallProposed
         * @see .getCallNotificationId
         */
        fun removeCallNotification(callId: String) {
            val notificationId = callNotificationMap[callId]
            Timber.d("Removing notification for callId: %s => %s", callId, notificationId)
            if (notificationId != null) {
                removeNotification(notificationId)
                callNotificationMap.remove(callId)
            }
        }

        /**
         * Use by phone ring Tone to check if the call notification has been dismissed, hence to stop the ring tone
         *
         *callId call Id / Jingle Sid
         * @return the notificationId for the specified callId
         */
        fun getCallNotificationId(callId: String): Int? {
            return callNotificationMap[callId]
        }
    }
}