/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.chat.conference

import android.content.Intent
import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.impl.protocol.jabber.ChatRoomJabberImpl
import net.java.sip.communicator.plugin.notificationwiring.NotificationManager
import net.java.sip.communicator.service.muc.ChatRoomListChangeEvent
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.muc.MUCService
import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.protocol.event.*
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum
import net.java.sip.communicator.util.ConfigurationUtils
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.LauncherActivity
import org.atalk.hmos.gui.chat.ChatMessage
import org.atalk.hmos.gui.chat.ChatPanel
import org.atalk.hmos.gui.chat.ChatSessionManager
import org.atalk.hmos.gui.chatroomslist.AdHocChatRoomList
import org.atalk.hmos.gui.chatroomslist.AdHocChatRoomListChangeEvent
import org.atalk.hmos.gui.chatroomslist.AdHocChatRoomListChangeListener
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.osgi.framework.Bundle
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import timber.log.Timber
import java.util.*
import java.util.concurrent.ExecutionException

/**
 * The `ConferenceChatManager` is the one that manages both chat room and ad-hoc chat rooms invitations.
 *
 * @author Yana Stamcheva
 * @author Lubomir Marinov
 * @author Valentin Martinet
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class ConferenceChatManager : ChatRoomMessageListener, ChatRoomInvitationListener, AdHocChatRoomMessageListener, AdHocChatRoomInvitationListener, LocalUserChatRoomPresenceListener, LocalUserAdHocChatRoomPresenceListener, ServiceListener, ChatRoomLocalUserRoleListener {
    /**
     * The list of ad-hoc chat rooms.
     */
    private val adHocChatRoomList = AdHocChatRoomList()
    private val multiUserChatManager = this

    /**
     * A list of all `AdHocChatRoomListChangeListener`-s.
     */
    private val adHoclistChangeListeners = Vector<AdHocChatRoomListChangeListener>()

    /**
     * Creates an instance of `ConferenceChatManager`.
     */
    init {
        // Loads the chat rooms list in a separate thread.
        object : Thread() {
            override fun run() {
                adHocChatRoomList.loadList()
            }
        }.start()
        AndroidGUIActivator.bundleContext!!.addServiceListener(this)
    }

    /**
     * Returns all chat room providers currently contained in the ad-hoc chat room list.
     *
     * @return all chat room providers currently contained in the ad-hoc chat room list.
     */
    fun getAdHocChatRoomList(): AdHocChatRoomList {
        return adHocChatRoomList
    }

    override fun invitationReceived(evt: ChatRoomInvitationReceivedEvent) {
        val multiUserChatOpSet = evt.getSourceOperationSet()
        val invitation = evt.getInvitation()

        // Wake aTalk to show invitation dialog
        if (!aTalkApp.isForeground) {
            val context = aTalkApp.globalContext
            val i = Intent(context, LauncherActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            Timber.d("Receive invitation while aTalk is in background")
            NotificationManager.fireNotification(NotificationManager.INCOMING_INVITATION)
        }

        // Event thread - Must execute in UiThread for dialog
        Handler(Looper.getMainLooper()).post {
            val activity = aTalkApp.waitForFocus()
            if (activity != null) {
                val dialog = InvitationReceivedDialog(activity, multiUserChatManager, multiUserChatOpSet, invitation)
                dialog.show()
            } else {
                // cmeng - auto accept and join room.
                // Set setCurrentChatId to null after joined, so incomingMessage pop-message is active
                try {
                    Timber.d("Receive invitation with waitForFocus failed, so auto-joined!")
                    val chatRoom = invitation.getTargetChatRoom() as ChatRoomJabberImpl
                    chatRoom.join()
                    ChatSessionManager.setCurrentChatId(null)
                    chatRoom.addMessage(aTalkApp.getResString(R.string.service_gui_JOIN_AUTOMATICALLY),
                            ChatMessage.MESSAGE_SYSTEM)
                } catch (e: OperationFailedException) {
                    Timber.w("Auto join group chat failed!")
                }
            }
        }
    }

    /**
     * Implements the `ChatRoomMessageListener.messageDelivered` method. <br></br>
     * Shows the message in the conversation area and clears the write message area.
     *
     * @param evt the `ChatRoomMessageDeliveredEvent` that notified us that the message was
     * delivered to its destination
     */
    override fun messageDelivered(evt: ChatRoomMessageDeliveredEvent) {
        val sourceChatRoom = evt.getSourceChatRoom()
        Timber.log(TimberLog.FINER, "MESSAGE DELIVERED to chat room: %s", sourceChatRoom.getName())
        val chatPanel = ChatSessionManager.getMultiChat(sourceChatRoom, false)
        if (chatPanel != null) {
            val message = evt.getMessage()!!
            // just return if the delivered message is for remote client consumption only
            if (message.isRemoteOnly()) return
            val messageType = evt.getEventType()
            chatPanel.addMessage(sourceChatRoom.getUserNickname().toString(), "",
                    evt.getTimestamp(), messageType, message, null)
        }
    }

    /**
     * Implements the `ChatRoomMessageListener.messageReceived` method. <br></br>
     * Obtains the corresponding `ChatPanel` and process the message there.
     *
     * @param evt the `ChatRoomMessageReceivedEvent` that notified us that a message has been received
     */
    override fun messageReceived(evt: ChatRoomMessageReceivedEvent) {
        val sourceChatRoom = evt.getSourceChatRoom()
        val sourceMember = evt.getSourceChatRoomMember()
        val messageType = evt.getEventType()
        val message = evt.getMessage()
        val chatPanel: ChatPanel?
        var createWindow = false
        var autoOpenConfig = MUCService.getChatRoomAutoOpenOption(sourceChatRoom.getParentProvider(), sourceChatRoom.getName())
        if (autoOpenConfig == null) autoOpenConfig = MUCService.DEFAULT_AUTO_OPEN_BEHAVIOUR
        if (autoOpenConfig == MUCService.OPEN_ON_ACTIVITY || autoOpenConfig == MUCService.OPEN_ON_MESSAGE && !evt.isHistoryMessage()
                || evt.isImportantMessage()) createWindow = true

        chatPanel = if (sourceChatRoom.isSystem()) {
            val serverWrapper = MUCActivator.mucService.findServerWrapperFromProvider(sourceChatRoom.getParentProvider())!!
            ChatSessionManager.getMultiChat(serverWrapper.systemRoomWrapper!!, createWindow)
        }
        else {
            ChatSessionManager.getMultiChat(sourceChatRoom, createWindow, message.getMessageUID())
        }
        
        if (chatPanel == null) return
        val messageContent = message.getContent()
        if (evt.isHistoryMessage()) { // cmeng: need to check since it always start with new ?????
            val timeStamp = Date()
            // chatPanel.getChatConversationPanel().getLastIncomingMsgTimestamp();
            val c = chatPanel.chatSession!!.getHistoryBeforeDate(
                    Date(if (timeStamp == Date(0)) System.currentTimeMillis() - 10000 else timeStamp.time), 20)!!
            var hasMatch = false
            for (o in c) {
                // cmeng: never match and should be implemented in ChatRoomMessageDeliveredEvent
                if (o is ChatRoomMessageDeliveredEvent) {
                    if (evt.getTimestamp() != null && evt.getTimestamp().equals(o.getTimestamp())) {
                        hasMatch = true
                        break
                    }
                }
                else if (o is ChatRoomMessageReceivedEvent) {
                    if (evt.getTimestamp() != null && evt.getTimestamp().equals(o.getTimestamp())) {
                        hasMatch = true
                        break
                    }
                }
                val m2 = evt.getMessage()
                if (m2 != null && m2.getContent() == messageContent) {
                    hasMatch = true
                    break
                }
            }
            // skip if the message is an old history previously received
            if (hasMatch) return
        }

        // contact may be null if message received with nickName only or when contact reject invitation
        // Contact contact = sourceMember.getContact();
        // String jabberID = (contact == null) ? displayName : contact.getAddress();
        val jabberID = sourceMember.getContactAddress()
        val displayName = jabberID.replace("(\\w+)/.*".toRegex(), "$1")
        chatPanel.addMessage(jabberID, displayName, evt.getTimestamp(), messageType, message, null)
    }

    /**
     * Implements the `ChatRoomMessageListener.messageDeliveryFailed` method. <br></br>
     * In the conversation area shows an error message, explaining the problem.
     *
     * @param evt the `ChatRoomMessageDeliveryFailedEvent` that notified us of a delivery failure
     */
    override fun messageDeliveryFailed(evt: ChatRoomMessageDeliveryFailedEvent) {
        /*
         * FIXME ChatRoomMessageDeliveryFailedEvent#getSource() is not a IMessage instance at the
         * time of this writing and the attempt "(IMessage) evt.getSource()" seems to be to
         * get the message which failed to be delivered. I'm not sure it's
         * ChatRoomMessageDeliveryFailedEvent#getMessage() but since it's the only message I can
         * get out of ChatRoomMessageDeliveryFailedEvent, I'm using it.
         */

        // Just show the pass in error message if false
        var mergeMessage = true
        var resendLastMessage = true
        var errorMsg: String?
        val reason = evt.getReason()
        val sourceChatRoom = evt.getSourceChatRoom()
        val srcMessage = evt.getMessage()
        val destMember = evt.getDestinationChatRoomMember()
        when (evt.getErrorCode()) {
            MessageDeliveryFailedEvent.OFFLINE_MESSAGES_NOT_SUPPORTED -> errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_DELIVERY_NOT_SUPPORTED, destMember!!.getNickName())
            MessageDeliveryFailedEvent.NETWORK_FAILURE -> errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_NOT_DELIVERED)
            MessageDeliveryFailedEvent.PROVIDER_NOT_REGISTERED -> errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_SEND_CONNECTION_PROBLEM)
            MessageDeliveryFailedEvent.INTERNAL_ERROR -> errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_DELIVERY_INTERNAL_ERROR)
            MessageDeliveryFailedEvent.FORBIDDEN -> errorMsg = aTalkApp.getResString(R.string.service_gui_CHATROOM_SEND_MSG_FORBIDDEN)
            MessageDeliveryFailedEvent.UNSUPPORTED_OPERATION -> errorMsg = aTalkApp.getResString(R.string.service_gui_UNSUPPORTED_OPERATION)
            MessageDeliveryFailedEvent.OMEMO_SEND_ERROR, MessageDeliveryFailedEvent.NOT_ACCEPTABLE -> {
                errorMsg = evt.getReason()
                mergeMessage = false
            }
            MessageDeliveryFailedEvent.SYSTEM_ERROR_MESSAGE -> {
                resendLastMessage = false
                if (TextUtils.isEmpty(reason)) errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_DELIVERY_UNKNOWN_ERROR)
                else {
                    errorMsg = reason
                    mergeMessage = false
                }
            }
            else -> if (TextUtils.isEmpty(reason)) errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_DELIVERY_UNKNOWN_ERROR)
            else {
                errorMsg = reason
                mergeMessage = false
            }
        }
        if (!TextUtils.isEmpty(reason) && mergeMessage) errorMsg += " " + aTalkApp.getResString(R.string.service_gui_ERROR_WAS, reason)

        // Error message sent from conference has no nickName i.e. contains ""
        val sender = if (destMember == null || TextUtils.isEmpty(destMember.getNickName())) sourceChatRoom.getName() else destMember.getNickName()!!
        val chatPanel = ChatSessionManager.getMultiChat(sourceChatRoom, true)!!
        if (resendLastMessage) {
            chatPanel.addMessage(sender, Date(), ChatMessage.MESSAGE_OUT, srcMessage.getMimeType(), srcMessage.getContent()!!)
        }
        chatPanel.addMessage(sender, Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN, errorMsg)
        ChatSessionManager.setCurrentChatId(chatPanel.chatSession!!.chatId)
    }

    /**
     * Implements the `LocalUserAdHocChatRoomPresenceListener.localUserPresenceChanged` method
     *
     * @param evt the `LocalUserAdHocChatRoomPresenceChangeEvent` that notified us of a presence change
     */
    override fun localUserAdHocPresenceChanged(evt: LocalUserAdHocChatRoomPresenceChangeEvent) {
        val sourceAdHocChatRoom = evt.getAdHocChatRoom()!!
        val adHocChatRoomWrapper = adHocChatRoomList.findChatRoomWrapperFromAdHocChatRoom(sourceAdHocChatRoom)
        val eventType = evt.getEventType()
        if (LocalUserAdHocChatRoomPresenceChangeEvent.LOCAL_USER_JOINED == eventType) {
            if (adHocChatRoomWrapper != null) {
                fireAdHocChatRoomListChangedEvent(adHocChatRoomWrapper, AdHocChatRoomListChangeEvent.AD_HOC_CHATROOM_CHANGED)

                // Check if we have already opened a chat window for this chat wrapper and load
                // the real chat room corresponding to the wrapper.
                val chatPanel = ChatSessionManager.getMultiChat(adHocChatRoomWrapper, true)!!
                // cmeng - below check is not necessary since above will do them all ???
                if (chatPanel.isChatFocused) {
                    (chatPanel.chatSession as AdHocConferenceChatSession).loadChatRoom(sourceAdHocChatRoom)
                }
                else {
                    // ChatSessionManager.openChat(chatPanel, true);
                    ChatSessionManager.setCurrentChatId(chatPanel.chatSession!!.chatId)
                }
            }
            sourceAdHocChatRoom.addMessageListener(this)
        }
        else if (evt.getEventType() == LocalUserAdHocChatRoomPresenceChangeEvent.LOCAL_USER_JOIN_FAILED) {
            DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_ERROR,
                    R.string.service_gui_CHATROOM_JOIN_FAILED_REASON, sourceAdHocChatRoom.getName(), evt.getReason())
        }
        else if (LocalUserAdHocChatRoomPresenceChangeEvent.LOCAL_USER_LEFT == eventType || LocalUserAdHocChatRoomPresenceChangeEvent.LOCAL_USER_DROPPED == eventType) {
            closeAdHocChatRoom(adHocChatRoomWrapper)

            // Need to refresh the chat room's list in order to change the state of the chat room to offline.
            fireAdHocChatRoomListChangedEvent(adHocChatRoomWrapper, AdHocChatRoomListChangeEvent.AD_HOC_CHATROOM_CHANGED)
            sourceAdHocChatRoom.removeMessageListener(this)
        }
    }

    /**
     * Implements the `LocalUserChatRoomPresenceListener.localUserPresenceChanged` method.
     *
     * @param evt the `LocalUserChatRoomPresenceChangeEvent` that notified us
     */
    override fun localUserPresenceChanged(evt: LocalUserChatRoomPresenceChangeEvent) {
        val sourceChatRoom = evt.getChatRoom()
        val chatRoomWrapper = MUCActivator.mucService.findChatRoomWrapperFromChatRoom(sourceChatRoom)
        val eventType = evt.getEventType()
        if (LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_JOINED == eventType) {
            if (chatRoomWrapper != null) {
                MUCActivator.mucService.fireChatRoomListChangedEvent(chatRoomWrapper,
                        ChatRoomListChangeEvent.CHAT_ROOM_CHANGED)
                var createWindow = false
                val autoOpenConfig = MUCService.getChatRoomAutoOpenOption(sourceChatRoom.getParentProvider(), sourceChatRoom.getName())
                if (autoOpenConfig != null && autoOpenConfig == MUCService.OPEN_ON_ACTIVITY) createWindow = true
                val chatPanel = ChatSessionManager.getMultiChat(chatRoomWrapper, createWindow)
                // cmeng - below code may not be required since above code will create and setActive
                if (chatPanel != null) {
                    // chatPanel.setChatIcon(chatPanel.getChatStatusIcon());

                    // Check if we have already opened a chat window for this chat wrapper and
                    // load the real chat room corresponding to the wrapper.
                    if (chatPanel.isChatFocused) {
                        (chatPanel.chatSession as ConferenceChatSession).loadChatRoom(sourceChatRoom)
                    }
                    else {
                        // ChatSessionManager.openChat(chatPanel, true);
                        ChatSessionManager.setCurrentChatId(chatPanel.chatSession!!.chatId)
                    }
                }
            }
            if (sourceChatRoom.isSystem()) {
                val serverWrapper = MUCActivator.mucService.findServerWrapperFromProvider(sourceChatRoom.getParentProvider())!!
                serverWrapper.setSystemRoom(sourceChatRoom)
            }
            sourceChatRoom.addMessageListener(this)
            sourceChatRoom.addLocalUserRoleListener(this)
        }
        else if (LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_JOIN_FAILED == eventType) {
            DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_ERROR,
                    R.string.service_gui_CHATROOM_JOIN_FAILED_REASON, sourceChatRoom.getName(), evt.getReason())
        }
        else if (LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_LEFT == eventType || LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_KICKED == eventType || LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_DROPPED == eventType) {
            if (chatRoomWrapper != null) {
                if (StringUtils.isEmpty(evt.getReason())) {
                    AndroidGUIActivator.uIService.closeChatRoomWindow(chatRoomWrapper)
                }
                else {
                    // send some system messages informing for the reason of leaving
                    val chatPanel = ChatSessionManager.getMultiChat(sourceChatRoom, false)
                    if (chatPanel != null) {
                        chatPanel.addMessage(sourceChatRoom.getName(), Date(), ChatMessage.MESSAGE_SYSTEM,
                                IMessage.ENCODE_PLAIN, evt.getReason()!!)

                        // print and the alternate address
                        if (StringUtils.isNotEmpty(evt.getAlternateAddress())) {
                            chatPanel.addMessage(sourceChatRoom.getName(), Date(),
                                    ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_PLAIN,
                                    aTalkApp.getResString(R.string.service_gui_CHATROOM_ALTERNATE_ADDRESS,
                                            evt.getAlternateAddress()))
                        }
                    }
                }
                // Need to refresh the chat room's list in order to change the state of the chat room to offline.
                MUCActivator.mucService.fireChatRoomListChangedEvent(chatRoomWrapper,
                        ChatRoomListChangeEvent.CHAT_ROOM_CHANGED)
            }
            sourceChatRoom.removeMessageListener(this)
            sourceChatRoom.removeLocalUserRoleListener(this)
        }
    }

    /**
     * Called to accept an incoming invitation. Adds the invitation chat room to the list of chat
     * rooms and joins it.
     *
     * @param invitation the invitation to accept
     * @param multiUserChatOpSet the operation set for chat conferencing
     * @throws OperationFailedException if the accept fails
     */
    @Throws(OperationFailedException::class)
    fun acceptInvitation(invitation: AdHocChatRoomInvitation, multiUserChatOpSet: OperationSetAdHocMultiUserChat?) {
        val chatRoom = invitation.getTargetAdHocChatRoom()
        chatRoom.join()
    }

    /**
     * Rejects the given invitation with the specified reason.
     *
     * @param multiUserChatAdHocOpSet the operation set to use for rejecting the invitation
     * @param invitation the invitation to reject
     * @param reason the reason for the rejection
     */
    fun rejectInvitation(multiUserChatAdHocOpSet: OperationSetAdHocMultiUserChat,
            invitation: AdHocChatRoomInvitation?, reason: String?) {
        multiUserChatAdHocOpSet.rejectInvitation(invitation, reason)
    }

    /**
     * Creates an ad-hoc chat room, by specifying the ad-hoc chat room name, the parent protocol
     * provider and eventually, the contacts invited to participate in this ad-hoc chat room.
     *
     * @param protocolProvider the parent protocol provider.
     * @param contacts the contacts invited when creating the chat room.
     * @param reason the reason for this invitation
     * @return the `AdHocChatRoomWrapper` corresponding to the created ad hoc chat room
     */
    fun createAdHocChatRoom(protocolProvider: ProtocolProviderService,
            contacts: Collection<String>, reason: String?): AdHocChatRoomWrapper? {
        var chatRoomWrapper: AdHocChatRoomWrapper? = null
        val groupChatOpSet = protocolProvider.getOperationSet(OperationSetAdHocMultiUserChat::class.java)
                ?: return null

        // If there's no group chat operation set we have nothing to do here.
        var chatRoom: AdHocChatRoom? = null
        try {
            val members: List<String> = LinkedList(contacts)
            chatRoom = groupChatOpSet.createAdHocChatRoom("chatroom-" + Date().time, members, reason)
        } catch (ex: OperationFailedException) {
            DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_ERROR,
                    R.string.service_gui_CHATROOM_CREATE_ERROR, protocolProvider.protocolDisplayName)
        } catch (ex: OperationNotSupportedException) {
            DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_ERROR,
                    R.string.service_gui_CHATROOM_CREATE_ERROR, protocolProvider.protocolDisplayName)
        }
        if (chatRoom != null) {
            val parentProvider = adHocChatRoomList.findServerWrapperFromProvider(protocolProvider)!!
            chatRoomWrapper = AdHocChatRoomWrapper(parentProvider, chatRoom)
            parentProvider.addAdHocChatRoom(chatRoomWrapper)
            adHocChatRoomList.addAdHocChatRoom(chatRoomWrapper)
            fireAdHocChatRoomListChangedEvent(chatRoomWrapper,
                    AdHocChatRoomListChangeEvent.AD_HOC_CHATROOM_ADDED)
        }
        return chatRoomWrapper
    }

    /**
     * Joins the given ad-hoc chat room
     *
     * @param chatRoomWrapper chatRoom Wrapper
     */
    fun joinChatRoom(chatRoomWrapper: AdHocChatRoomWrapper) {
        val chatRoom = chatRoomWrapper.adHocChatRoom
        if (chatRoom == null) {
            DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_WARNING,
                    R.string.service_gui_CHATROOM_NOT_CONNECTED, chatRoomWrapper.adHocChatRoomName)
            return
        }
        JoinAdHocChatRoomTask(chatRoomWrapper).execute()
    }

    /**
     * Removes the given chat room from the UI.
     *
     * @param chatRoomWrapper the chat room to remove.
     */
    fun removeChatRoom(chatRoomWrapper: ChatRoomWrapper) {
        val chatRoom = chatRoomWrapper.chatRoom
        if (chatRoom != null) leaveChatRoom(chatRoomWrapper)
        AndroidGUIActivator.uIService!!.closeChatRoomWindow(chatRoomWrapper)
        MUCActivator.mucService.removeChatRoom(chatRoomWrapper)
    }

    /**
     * Joins the given chat room and manages all the exceptions that could occur during the join process.
     *
     * @param chatRoom the chat room to join
     */
    fun joinChatRoom(chatRoom: AdHocChatRoom) {
        var chatRoomWrapper = adHocChatRoomList.findChatRoomWrapperFromAdHocChatRoom(chatRoom)
        if (chatRoomWrapper == null) {
            val parentProvider = adHocChatRoomList.findServerWrapperFromProvider(chatRoom.getParentProvider())!!
            chatRoomWrapper = AdHocChatRoomWrapper(parentProvider, chatRoom)
            adHocChatRoomList.addAdHocChatRoom(chatRoomWrapper)
            fireAdHocChatRoomListChangedEvent(chatRoomWrapper,
                    AdHocChatRoomListChangeEvent.AD_HOC_CHATROOM_ADDED)
        }
        this.joinChatRoom(chatRoomWrapper)
        // ChatSessionManager.openChat(chatWindowManager.getMultiChat(chatRoomWrapper, true), true);
        ChatSessionManager.getMultiChat(chatRoomWrapper, true)
    }

    /**
     * Leaves the given `ChatRoom`.
     *
     * @param chatRoomWrapper the `ChatRoom` to leave.
     */
    fun leaveChatRoom(chatRoomWrapper: ChatRoomWrapper?) {
        val leavedRoomWrapped = MUCActivator.mucService.leaveChatRoom(chatRoomWrapper!!)
        if (leavedRoomWrapped != null) {
            // AndroidGUIActivator.getUIService().closeChatRoomWindow(leavedRoomWrapped);
        }
    }

    /**
     * Leaves the given `ChatRoom`.
     *
     * @param chatRoomWrapper the `ChatRoom` to leave.
     */
    fun leaveChatRoom(chatRoomWrapper: AdHocChatRoomWrapper) {
        val chatRoom = chatRoomWrapper.adHocChatRoom
        if (chatRoom != null) {
            chatRoom.leave()
        }
        else {
            DialogActivity.showDialog(aTalkApp.globalContext,
                    R.string.service_gui_WARNING, R.string.service_gui_CHATROOM_LEAVE_NOT_CONNECTED)
        }
    }

    /**
     * Adds the given `AdHocChatRoomListChangeListener` that will listen for all changes of
     * the chat room list data model.
     *
     * @param l the listener to add.
     */
    fun addAdHocChatRoomListChangeListener(l: AdHocChatRoomListChangeListener) {
        synchronized(adHoclistChangeListeners) { adHoclistChangeListeners.add(l) }
    }

    /**
     * Removes the given `AdHocChatRoomListChangeListener`.
     *
     * @param l the listener to remove.
     */
    fun removeAdHocChatRoomListChangeListener(l: AdHocChatRoomListChangeListener) {
        synchronized(adHoclistChangeListeners) { adHoclistChangeListeners.remove(l) }
    }

    /**
     * Notifies all interested listeners that a change in the chat room list model has occurred.
     *
     * @param adHocChatRoomWrapper the chat room wrapper that identifies the chat room
     * @param eventID the identifier of the event
     */
    private fun fireAdHocChatRoomListChangedEvent(adHocChatRoomWrapper: AdHocChatRoomWrapper?, eventID: Int) {
        val evt = AdHocChatRoomListChangeEvent(adHocChatRoomWrapper, eventID)
        for (l in adHoclistChangeListeners) {
            l.contentChanged(evt)
        }
    }

    /**
     * Closes the chat corresponding to the given ad-hoc chat room wrapper, if such exists.
     *
     * @param chatRoomWrapper the ad-hoc chat room wrapper for which we search a chat to close.
     */
    private fun closeAdHocChatRoom(chatRoomWrapper: AdHocChatRoomWrapper?) {
        val chatPanel = ChatSessionManager.getMultiChat(chatRoomWrapper!!, false)
        if (chatPanel != null) {
            // ChatSessionManager.closeChat(chatPanel);
        }
    }

    /**
     * Handles `ServiceEvent`s triggered by adding or removing a ProtocolProviderService.
     * Updates the list of available chat rooms
     * and chat room servers.
     *
     * @param event The event to handle.
     */
    override fun serviceChanged(event: ServiceEvent) {
        // if the event is caused by a bundle being stopped, we don't want to know
        val eType = event.serviceReference.bundle.state
        if (eType == Bundle.STOPPING) return

        // AndroidGUIActivator.bundleContext can be null on application exit
        if (AndroidGUIActivator.bundleContext != null) {
            val service = AndroidGUIActivator.bundleContext!!.getService(event.serviceReference)

            // we don't care if the source service is not a protocol provider
            if (service is ProtocolProviderService) {
                val adHocMultiUserChatOpSet = service.getOperationSet(OperationSetAdHocMultiUserChat::class.java)
                if (adHocMultiUserChatOpSet != null) {
                    if (event.type == ServiceEvent.REGISTERED) {
                        adHocChatRoomList.addChatProvider(service)
                    }
                    else if (event.type == ServiceEvent.UNREGISTERING) {
                        adHocChatRoomList.removeChatProvider(service)
                    }
                }
            }
        }
    }

    /**
     * Joins an ad-hoc chat room in an asynchronous way.
     */
    private open class JoinAdHocChatRoomTask(private val adHocChatRoomWrapper: AdHocChatRoomWrapper) : AsyncTask<Void?, Void?, String?>() {
        /**
         * @return SUCCESS if success, otherwise the error code
         * [#doInBackground(Void... params)][AsyncTask] to perform all asynchronous tasks.
         */
        override fun doInBackground(vararg params: Void?): String? {
            val chatRoom = adHocChatRoomWrapper.adHocChatRoom!!
            return try {
                chatRoom.join()
                SUCCESS
            } catch (e: OperationFailedException) {
                Timber.log(TimberLog.FINER, e, "Failed to join ad-hoc chat room: %s", chatRoom.getName())
                when (e.getErrorCode()) {
                    OperationFailedException.AUTHENTICATION_FAILED -> AUTHENTICATION_FAILED
                    OperationFailedException.REGISTRATION_REQUIRED -> REGISTRATION_REQUIRED
                    OperationFailedException.PROVIDER_NOT_REGISTERED -> PROVIDER_NOT_REGISTERED
                    OperationFailedException.SUBSCRIPTION_ALREADY_EXISTS -> SUBSCRIPTION_ALREADY_EXISTS
                    else -> UNKNOWN_ERROR
                }
            }
        }

        /**
         * [#onPostExecute(String)][AsyncTask] onPostExecute()} to perform
         * UI changes after the ad-hoc chat room join task has finished.
         */
        override fun onPostExecute(result: String?) {
            var returnCode: String? = null
            try {
                returnCode = get()
            } catch (ignore: InterruptedException) {
            } catch (ignore: ExecutionException) {
            }
            ConfigurationUtils.updateChatRoomStatus(adHocChatRoomWrapper.protocolProvider,
                    adHocChatRoomWrapper.adHocChatRoomID, GlobalStatusEnum.ONLINE_STATUS)
            val errorMessage = if (PROVIDER_NOT_REGISTERED == returnCode) {
                aTalkApp.getResString(R.string.service_gui_CHATROOM_NOT_CONNECTED, adHocChatRoomWrapper.adHocChatRoomName)
            }
            else if (SUBSCRIPTION_ALREADY_EXISTS == returnCode) {
                aTalkApp.getResString(R.string.service_gui_CHATROOM_ALREADY_JOINED, adHocChatRoomWrapper.adHocChatRoomName)
            }
            else {
                aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_FAILED_REASON,
                        adHocChatRoomWrapper.adHocChatRoomName, result)
            }
            if (SUCCESS != returnCode && AUTHENTICATION_FAILED != returnCode) {
                DialogActivity.showDialog(aTalkApp.globalContext,
                        aTalkApp.getResString(R.string.service_gui_ERROR), errorMessage)
            }
        }

        companion object {
            private const val SUCCESS = "Success"
            private const val AUTHENTICATION_FAILED = "AuthenticationFailed"
            private const val REGISTRATION_REQUIRED = "RegistrationRequired"
            private const val PROVIDER_NOT_REGISTERED = "ProviderNotRegistered"
            private const val SUBSCRIPTION_ALREADY_EXISTS = "SubscriptionAlreadyExists"
            private const val UNKNOWN_ERROR = "UnknownError"
        }
    }

    /**
     * Indicates that an invitation has been received and opens the invitation dialog to notify the user.
     *
     * @param evt the `AdHocChatRoomInvitationReceivedEvent` that notified us
     */
    override fun invitationReceived(evt: AdHocChatRoomInvitationReceivedEvent) {
        // Timber.i("Invitation received: %s", evt.toString());
        val multiUserChatOpSet = evt.getSourceOperationSet()
        val invitationAdHoc = evt.getInvitation()

        // Event thread - Must execute in UiThread for dialog
        Handler(Looper.getMainLooper()).post(Runnable {
            val activity = aTalkApp.waitForFocus()
            if (activity != null) {
                val dialog = InvitationReceivedDialog(activity, multiUserChatManager, multiUserChatOpSet, invitationAdHoc)
                dialog.show()
            }
        })
    }

    /**
     * Implements the `AdHocChatRoomMessageListener.messageDelivered` method. <br></br>
     * Shows the message in the conversation area and clears the write message area.
     *
     * @param evt the `AdHocChatRoomMessageDeliveredEvent` that notified us
     */
    override fun messageDelivered(evt: AdHocChatRoomMessageDeliveredEvent) {
        val sourceChatRoom = evt.getSource() as AdHocChatRoom
        // Timber.i("Message delivered to ad-hoc chat room: %s", sourceChatRoom.getName());
        val chatPanel = ChatSessionManager.getMultiChat(sourceChatRoom, false)
        if (chatPanel != null) {
            val message = evt.getMessage()

            // just return if the delivered message is for remote client consumption only
            if (message.isRemoteOnly()) return
            val messageType = evt.getEventType()
            val accountId = sourceChatRoom.getParentProvider().accountID
            chatPanel.addMessage(accountId.mUserID!!, accountId.displayName!!,
                    evt.getTimestamp(), messageType, message, null)
        }
        else {
            Timber.e("chat panel is null, message NOT DELIVERED !")
        }
    }

    /**
     * Implements `AdHocChatRoomMessageListener.messageDeliveryFailed` method. <br></br>
     * In the conversation area shows an error message, explaining the problem.
     *
     * @param evt the `AdHocChatRoomMessageDeliveryFailedEvent` that notified us
     */
    override fun messageDeliveryFailed(evt: AdHocChatRoomMessageDeliveryFailedEvent) {
        val sourceChatRoom = evt.sourceChatRoom
        val sourceMessage = evt.message!!
        val destParticipant = evt.destinationParticipant!!
        val errorMsg = when (evt.errorCode) {
            MessageDeliveryFailedEvent.OFFLINE_MESSAGES_NOT_SUPPORTED -> aTalkApp.getResString(R.string.service_gui_MSG_DELIVERY_NOT_SUPPORTED,
                    destParticipant.displayName)

            MessageDeliveryFailedEvent.NETWORK_FAILURE -> aTalkApp.getResString(R.string.service_gui_MSG_NOT_DELIVERED)

            MessageDeliveryFailedEvent.PROVIDER_NOT_REGISTERED -> aTalkApp.getResString(R.string.service_gui_MSG_SEND_CONNECTION_PROBLEM)

            MessageDeliveryFailedEvent.INTERNAL_ERROR -> aTalkApp.getResString(R.string.service_gui_MSG_DELIVERY_INTERNAL_ERROR)

            MessageDeliveryFailedEvent.UNSUPPORTED_OPERATION -> aTalkApp.getResString(R.string.service_gui_UNSUPPORTED_OPERATION)

            else -> aTalkApp.getResString(R.string.service_gui_MSG_DELIVERY_UNKNOWN_ERROR)
        }
        val sender = destParticipant.displayName
        val chatPanel = ChatSessionManager.getMultiChat(sourceChatRoom, true)!!
        chatPanel.addMessage(sender, Date(), ChatMessage.MESSAGE_OUT,
                sourceMessage.getMimeType(), sourceMessage.getContent()!!)
        chatPanel.addMessage(sender, Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN, errorMsg)
        ChatSessionManager.setCurrentChatId(chatPanel.chatSession!!.chatId)
    }

    /**
     * Implements the `AdHocChatRoomMessageListener.messageReceived` method. <br></br>
     * Obtains the corresponding `ChatPanel` and process the message there.
     *
     * @param evt the `AdHocChatRoomMessageReceivedEvent` that notified us
     */
    override fun messageReceived(evt: AdHocChatRoomMessageReceivedEvent) {
        val sourceChatRoom = evt.getSourceChatRoom()
        val sourceParticipant = evt.getSourceChatRoomParticipant().address
        val messageType = evt.getEventType()
        Timber.i("Message received from contact: %s", sourceParticipant)
        val message = evt.getMessage()
        val chatPanel = ChatSessionManager.getMultiChat(sourceChatRoom, true, message.getMessageUID())!!
        chatPanel.addMessage(sourceParticipant, sourceParticipant, evt.getTimestamp(),
                messageType, message, null)
        ChatSessionManager.setCurrentChatId(chatPanel.chatSession!!.chatId)
    }

    override fun localUserRoleChanged(evt: ChatRoomLocalUserRoleChangeEvent) {
        if (evt.isInitial()) return
        val sourceChatRoom = evt.getSourceChatRoom()
        val chatRoomWrapper = MUCActivator.mucService.findChatRoomWrapperFromChatRoom(sourceChatRoom)!!
        val chatPanel = ChatSessionManager.getMultiChat(chatRoomWrapper, true)!!
        ChatSessionManager.setCurrentChatId(chatPanel.chatSession!!.chatId)
    }
}