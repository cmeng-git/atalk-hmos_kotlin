/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.notificationwiring

import android.text.Html
import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.service.gui.Chat
import net.java.sip.communicator.service.notification.CommandNotificationHandler
import net.java.sip.communicator.service.notification.NotificationAction
import net.java.sip.communicator.service.notification.NotificationData
import net.java.sip.communicator.service.notification.SoundNotificationAction
import net.java.sip.communicator.service.protocol.Call
import net.java.sip.communicator.service.protocol.CallPeer
import net.java.sip.communicator.service.protocol.CallPeerState
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.IMessage
import net.java.sip.communicator.service.protocol.OperationSetAdHocMultiUserChat
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony
import net.java.sip.communicator.service.protocol.OperationSetChatStateNotifications
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import net.java.sip.communicator.service.protocol.OperationSetSmsMessaging
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageDeliveryFailedEvent
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageListener
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageReceivedEvent
import net.java.sip.communicator.service.protocol.event.CallChangeEvent
import net.java.sip.communicator.service.protocol.event.CallChangeListener
import net.java.sip.communicator.service.protocol.event.CallEvent
import net.java.sip.communicator.service.protocol.event.CallListener
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent
import net.java.sip.communicator.service.protocol.event.CallPeerConferenceEvent
import net.java.sip.communicator.service.protocol.event.CallPeerConferenceListener
import net.java.sip.communicator.service.protocol.event.CallPeerEvent
import net.java.sip.communicator.service.protocol.event.CallPeerListener
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityListener
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityMessageEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityNegotiationStartedEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOffEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOnEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityTimeoutEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveryFailedEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageListener
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageReceivedEvent
import net.java.sip.communicator.service.protocol.event.ChatStateNotificationEvent
import net.java.sip.communicator.service.protocol.event.ChatStateNotificationsListener
import net.java.sip.communicator.service.protocol.event.FileTransferCreatedEvent
import net.java.sip.communicator.service.protocol.event.FileTransferRequestEvent
import net.java.sip.communicator.service.protocol.event.LocalUserAdHocChatRoomPresenceChangeEvent
import net.java.sip.communicator.service.protocol.event.LocalUserAdHocChatRoomPresenceListener
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceChangeEvent
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceListener
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.MessageDeliveryFailedEvent
import net.java.sip.communicator.service.protocol.event.MessageListener
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent
import net.java.sip.communicator.service.protocol.event.ScFileTransferListener
import net.java.sip.communicator.service.systray.SystrayService
import org.apache.commons.text.StringEscapeUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.chat.ChatMessage
import org.atalk.hmos.gui.chat.ChatPanel
import org.atalk.hmos.gui.chatroomslist.ChatRoomListFragment
import org.atalk.hmos.gui.contactlist.ContactListFragment
import org.atalk.hmos.gui.util.AndroidImageUtil
import org.atalk.service.neomedia.event.SrtpListener
import org.atalk.service.neomedia.recording.Recorder
import org.jivesoftware.smackx.chatstates.ChatState
import org.osgi.framework.Bundle
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.Callable

/**
 * Listens to various events which are related to the display and/or playback of notifications
 * and shows/starts or hides/stops the notifications in question.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 * @see .registerDefaultNotifications
 * This is where all the events actions e.g. notification popup, vibrate and alert are defined for each notification
 */
class NotificationManager : CallChangeListener, CallListener, CallPeerConferenceListener, CallPeerListener, CallPeerSecurityListener, ChatRoomMessageListener, LocalUserChatRoomPresenceListener, AdHocChatRoomMessageListener, LocalUserAdHocChatRoomPresenceListener, ScFileTransferListener, MessageListener, Recorder.Listener, ServiceListener, ChatStateNotificationsListener {
    /**
     * Stores notification references to stop them if a notification has expired (e.g. to stop the dialing sound).
     */
    private val callNotifications = WeakHashMap<Call<*>, NotificationData>()

    /**
     * The pseudo timer which is used to delay multiple composing notifications before receiving the message.
     */
    private val proactiveTimer = HashMap<Any, Long>()

    /**
     * Implements CallListener.callEnded. Stops sounds that are playing at the moment if there are any.
     *
     * @param event the `CallEvent`
     */
    override fun callEnded(event: CallEvent) {
        try {
            // Stop all telephony related sounds.
            // stopAllTelephonySounds()mChatActivity
            val notification = callNotifications[event.sourceCall]
            if (notification != null) stopSound(notification)

            // Play the hangup sound - Let peerStateChanged() fire HANG_UPmChatActivity else double firing
            // fireNotification(HANG_UP)mChatActivity
        } catch (t: Throwable) {
            if (t is ThreadDeath) throw t
            else {
                Timber.e(t, "An error occurred while trying to notify about the end of a call.")
            }
        }
    }

    /**
     * Implements the `CallChangeListener.callPeerAdded` method.
     *
     * @param evt the `CallPeerEvent` that notifies us for the change
     */
    override fun callPeerAdded(evt: CallPeerEvent) {
        val peer = evt.getSourceCallPeer()
        peer.addCallPeerListener(this)
        peer.addCallPeerSecurityListener(this)
        peer.addCallPeerConferenceListener(this)
    }

    /**
     * Implements the `CallChangeListener.callPeerRemoved` method.
     *
     * @param evt the `CallPeerEvent` that has been triggered
     */
    override fun callPeerRemoved(evt: CallPeerEvent) {
        val peer = evt.getSourceCallPeer()
        peer.removeCallPeerListener(this)
        peer.removeCallPeerSecurityListener(this)
        peer.addCallPeerConferenceListener(this)
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun callStateChanged(evt: CallChangeEvent) {}

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun conferenceFocusChanged(conferenceEvent: CallPeerConferenceEvent) {}

    /**
     * Indicates that the given conference member has been added to the given peer.
     *
     * @param conferenceEvent the event
     */
    override fun conferenceMemberAdded(conferenceEvent: CallPeerConferenceEvent) {
        try {
            val peer = conferenceEvent.getConferenceMember()!!.getConferenceFocusCallPeer()
            if (peer.getConferenceMemberCount() > 0) {
                val securityEvent = peer.getCurrentSecuritySettings()
                if (securityEvent is CallPeerSecurityOnEvent) fireNotification(CALL_SECURITY_ON)
            }
        } catch (t: Throwable) {
            if (t is ThreadDeath) throw t else Timber.e(t, "Error notifying for secured call member")
        }
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun conferenceMemberErrorReceived(conferenceEvent: CallPeerConferenceEvent) {}

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun conferenceMemberRemoved(conferenceEvent: CallPeerConferenceEvent) {}

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun fileTransferCreated(event: FileTransferCreatedEvent?) {}

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun fileTransferRequestCanceled(event: FileTransferRequestEvent) {}

    /**
     * When a request has been received we show a notification.
     *
     * @param event `FileTransferRequestEvent`
     *
     * @see ScFileTransferListener.fileTransferRequestReceived
     */
    override fun fileTransferRequestReceived(event: FileTransferRequestEvent) {
        try {
            val request = event.getRequest()
            val message = request.getFileName() + "  (size: " + request.getFileSize() + ")"
            val sourceContact = request.getSender()!!

            // Fire notification
            val title = aTalkApp.getResString(R.string.xFile_FILE_RECEIVING_FROM,
                sourceContact.displayName)
            fireChatNotification(sourceContact, INCOMING_FILE, title, message, request.getID())
        } catch (t: Throwable) {
            Timber.e(t, "Error notifying for file transfer request received")
        }
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun fileTransferRequestRejected(event: FileTransferRequestEvent) {}

    /**
     * Adds all listeners related to the given protocol provider.
     *
     * @param protocolProvider the `ProtocolProviderService`
     */
    private fun handleProviderAdded(protocolProvider: ProtocolProviderService) {
        if (!protocolProvider.accountID.isEnabled) return
        val supportedOperationSets = protocolProvider.getSupportedOperationSets()

        // Obtain the basic instant messaging operation set.
        val imOpSetClassName = OperationSetBasicInstantMessaging::class.java.name
        if (supportedOperationSets.containsKey(imOpSetClassName)) {
            val im = supportedOperationSets[imOpSetClassName] as OperationSetBasicInstantMessaging?

            // Add to all instant messaging operation sets the Message listener which handles all
            // received messages.
            im!!.addMessageListener(this)
        }

        // Obtain the sms messaging operation set.
        val smsOpSetClassName = OperationSetSmsMessaging::class.java.name
        if (supportedOperationSets.containsKey(smsOpSetClassName)) {
            val sms = supportedOperationSets[smsOpSetClassName] as OperationSetSmsMessaging?
            sms!!.addMessageListener(this)
        }

        // Obtain the chat state notifications operation set.
        val tnOpSetClassName = OperationSetChatStateNotifications::class.java.name
        if (supportedOperationSets.containsKey(tnOpSetClassName)) {
            val tn = supportedOperationSets[tnOpSetClassName] as OperationSetChatStateNotifications?

            // Add to all chat state notification operation sets the Message listener implemented in
            // the ContactListPanel, which handles all received messages.
            tn!!.addChatStateNotificationsListener(this)
        }

        // Obtain file transfer operation set.
        protocolProvider.getOperationSet(OperationSetFileTransfer::class.java)?.addFileTransferListener(this)

        // Obtain the multi user chat operation set & Manager.
        val uiService = AndroidGUIActivator.uIService

        val conferenceChatManager = uiService.conferenceChatManager
        val multiChatOpSet = protocolProvider.getOperationSet(OperationSetMultiUserChat::class.java)
        if (multiChatOpSet != null) {
            multiChatOpSet.addPresenceListener(this)
            multiChatOpSet.addInvitationListener(conferenceChatManager)
            multiChatOpSet.addPresenceListener(conferenceChatManager)
        }

        // Obtain the ad-hoc multi user chat operation set.
        val adHocMultiChatOpSet = protocolProvider.getOperationSet(OperationSetAdHocMultiUserChat::class.java)
        if (adHocMultiChatOpSet != null) {
            adHocMultiChatOpSet.addPresenceListener(this)
            adHocMultiChatOpSet.addInvitationListener(conferenceChatManager)
            adHocMultiChatOpSet.addPresenceListener(conferenceChatManager)
        }

        // Obtain the basic telephony operation set.
        protocolProvider.getOperationSet(OperationSetBasicTelephony::class.java)?.addCallListener(this)
    }

    /**
     * Removes all listeners related to the given protocol provider.
     *
     * @param protocolProvider the `ProtocolProviderService`
     */
    private fun handleProviderRemoved(protocolProvider: ProtocolProviderService) {
        val supportedOperationSets = protocolProvider.getSupportedOperationSets()

        // Obtain the basic instant messaging operation set.
        val imOpSetClassName = OperationSetBasicInstantMessaging::class.java.name
        if (supportedOperationSets.containsKey(imOpSetClassName)) {
            val im = supportedOperationSets[imOpSetClassName] as OperationSetBasicInstantMessaging

            // Add to all instant messaging operation sets the Message listener which handles all
            // received messages.
            im.removeMessageListener(this)
        }

        // Obtain the chat state notifications operation set.
        val tnOpSetClassName = OperationSetChatStateNotifications::class.java.name
        if (supportedOperationSets.containsKey(tnOpSetClassName)) {
            val tn = supportedOperationSets[tnOpSetClassName] as OperationSetChatStateNotifications?

            // Add to all chat state notification operation sets the Message listener implemented in
            // the ContactListPanel, which handles all received messages.
            tn!!.removeChatStateNotificationsListener(this)
        }

        // Obtain file transfer operation set.
        protocolProvider.getOperationSet(OperationSetFileTransfer::class.java)?.removeFileTransferListener(this)

        // Obtain the multi user chat operation set & Manager.
        val uiService = AndroidGUIActivator.uIService
        val conferenceChatManager = uiService.conferenceChatManager
        val multiChatOpSet = protocolProvider.getOperationSet(OperationSetMultiUserChat::class.java)
        if (multiChatOpSet != null) {
            multiChatOpSet.removePresenceListener(this)
            multiChatOpSet.removeInvitationListener(conferenceChatManager)
            multiChatOpSet.removePresenceListener(conferenceChatManager)
        }
        val multiAdHocChatOpSet = protocolProvider.getOperationSet(OperationSetAdHocMultiUserChat::class.java)
        if (multiAdHocChatOpSet != null) {
            multiAdHocChatOpSet.removePresenceListener(this)
            multiAdHocChatOpSet.removeInvitationListener(conferenceChatManager)
            multiAdHocChatOpSet.removePresenceListener(conferenceChatManager)
        }

        // Obtain the basic telephony operation set.
        protocolProvider.getOperationSet(OperationSetBasicTelephony::class.java)?.removeCallListener(this)
    }

    /**
     * Implements CallListener.incomingCallReceived. Upon received a call, plays the phone ring tone to the user
     * and gathers caller information that may be used by a user-specified command (incomingCall event trigger).
     *
     * @param event the `CallEvent`
     */
    override fun incomingCallReceived(event: CallEvent) {
        try {
            val peerInfo = HashMap<String, String>()
            val call = event.sourceCall
            val peer = call.getCallPeers().next()
            val peerName = peer.getDisplayName()
            peerInfo["caller.uri"] = peer.getURI()!!
            peerInfo["caller.address"] = peer.getAddress()
            peerInfo["caller.name"] = peerName!!
            peerInfo["caller.id"] = peer.getPeerID()

            /*
             * The loopCondition will stay with the notification sound until the latter is stopped.
             * If by any chance the sound fails to stop by the time the call is no longer referenced, do try
             * to stop it then. That's why the loopCondition will weakly reference the call.
             */
            val weakCall = WeakReference(call)
            val notification = fireNotification(INCOMING_CALL, "",
                aTalkApp.getResString(R.string.service_gui_CALL_INCOMING, peerName), peerInfo) {
                val call1 = weakCall.get() ?: return@fireNotification false

                /*
                         * INCOMING_CALL should be played for a Call only while there is a
                         * CallPeer in the INCOMING_CALL state.
                         */
                val peerIter = call1.getCallPeers()
                var loop = false
                while (peerIter.hasNext()) {
                    val peer1 = peerIter.next()
                    if (CallPeerState.INCOMING_CALL == peer1.getState()) {
                        loop = true
                        break
                    }
                }
                loop
            }
            if (notification != null) callNotifications[call] = notification
            call.addCallChangeListener(this)
            peer.addCallPeerListener(this)
            peer.addCallPeerSecurityListener(this)
            peer.addCallPeerConferenceListener(this)
        } catch (t: Throwable) {
            if (t is ThreadDeath) throw t
            else {
                Timber.e(t, "An error occurred while trying to notify about an incoming call")
            }
        }
    }

    /**
     * Initialize, register default notifications and start listening for new protocols or removed
     * one and find any that are already registered.
     */
    fun init() {
        registerDefaultNotifications()
        // listens for new protocols
        NotificationWiringActivator.bundleContext!!.addServiceListener(this)

        // enumerate currently registered protocols
        for (pp in protocolProviders) {
            handleProviderAdded(pp)
        }
        val mediaServiceImpl = NotificationWiringActivator.mediaService
        if (mediaServiceImpl == null) {
            Timber.w("Media Service record listener init failed - jnlibffmpeg failed to load?")
        }
        else mediaServiceImpl.addRecorderListener(this)
    }

    /**
     * Checks if the contained call is a conference call.
     *
     * @param call the call to check
     *
     * @return `true` if the contained `Call` is a conference call, otherwise returns `false`.
     */
    private fun isConference(call: Call<*>): Boolean {
        // If we're the focus of the conference.
        if (call.isConferenceFocus) return true

        // If one of our peers is a conference focus, we're in a conference call.
        val callPeers = call.getCallPeers()
        while (callPeers.hasNext()) {
            val callPeer = callPeers.next()
            if (callPeer.isConferenceFocus()) return true
        }

        // the call can have two peers at the same time and there is no one is conference focus.
        // This is situation when some one has made an attended transfer and has transferred us. We
        // have one call with two peers the one we are talking to and the one we have been
        // transferred to. And the first one is been hangup and so the call passes through
        // conference call for a moment and than go again to one to one call.
        return call.callPeerCount > 1
    }

    /**
     * Implements the `LocalUserAdHocChatRoomPresenceListener.localUserPresenceChanged` method
     *
     * @param evt the `LocalUserAdHocChatRoomPresenceChangeEvent` that notified us of a presence change
     */
    override fun localUserAdHocPresenceChanged(evt: LocalUserAdHocChatRoomPresenceChangeEvent) {
        val eventType = evt.getEventType()
        if (LocalUserAdHocChatRoomPresenceChangeEvent.LOCAL_USER_JOINED == eventType) {
            evt.getAdHocChatRoom()!!.addMessageListener(this)
        }
        else if (LocalUserAdHocChatRoomPresenceChangeEvent.LOCAL_USER_LEFT == eventType || LocalUserAdHocChatRoomPresenceChangeEvent.LOCAL_USER_DROPPED == eventType) {
            evt.getAdHocChatRoom()!!.removeMessageListener(this)
        }
    }

    /**
     * cmeng: should remove this from here
     * Implements the `LocalUserChatRoomPresenceListener.localUserPresenceChanged` method.
     *
     * @param evt the `LocalUserChatRoomPresenceChangeEvent` that notified us
     */
    override fun localUserPresenceChanged(evt: LocalUserChatRoomPresenceChangeEvent) {
        val sourceChatRoom = evt.getChatRoom()
        val eventType = evt.getEventType()
        if (LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_JOINED == eventType) {
            sourceChatRoom.addMessageListener(this)
        }
        else if (LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_LEFT == eventType || LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_KICKED == eventType || LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_DROPPED == eventType) {
            sourceChatRoom.removeMessageListener(this)
        }
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun messageDelivered(evt: AdHocChatRoomMessageDeliveredEvent) {}

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun messageDelivered(evt: ChatRoomMessageDeliveredEvent) {}

    /**
     * {@inheritDoc}
     *
     * Not used
     */
    override fun messageDelivered(evt: MessageDeliveredEvent) {}

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun messageDeliveryFailed(evt: AdHocChatRoomMessageDeliveryFailedEvent) {}

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun messageDeliveryFailed(evt: ChatRoomMessageDeliveryFailedEvent) {}

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun messageDeliveryFailed(evt: MessageDeliveryFailedEvent) {}

    /**
     * Implements the `AdHocChatRoomMessageListener.messageReceived` method. <br></br>
     *
     * @param evt the `AdHocChatRoomMessageReceivedEvent` that notified us
     */
    override fun messageReceived(evt: AdHocChatRoomMessageReceivedEvent) {
        // Fire notification as INCOMING_FILE is found
        val chatRoom = evt.getSourceChatRoom()
        val sourceParticipant = evt.getSourceChatRoomParticipant().displayName
        val message = evt.getMessage()
        var msgBody = message.getContent()!!
        val msgUid = message.getMessageUID()
        if (ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD == evt.getEventType()) {
            val filePath = msgBody.split("#")[0]
            val fileName = filePath.substring(filePath.lastIndexOf('/') + 1)
            val title = aTalkApp.getResString(R.string.xFile_FILE_RECEIVING_FROM, sourceParticipant)
            fireChatNotification(chatRoom, INCOMING_FILE, title, fileName, msgUid)
        }
        else {
            val fireChatNotification: Boolean
            val nickname = chatRoom.getName()
            fireChatNotification = msgBody.lowercase(Locale.getDefault()).contains(nickname.lowercase(Locale.getDefault()))
            if (fireChatNotification) {
                val title = aTalkApp.getResString(R.string.service_gui_MSG_RECEIVED, sourceParticipant)
                if (IMessage.ENCODE_HTML != evt.getMessage().getMimeType()) {
                    msgBody = StringEscapeUtils.escapeHtml4(msgBody)
                }
                fireChatNotification(chatRoom, INCOMING_MESSAGE, title, msgBody, evt.getMessage().getMessageUID())
            }
        }
    }

    /**
     * Implements the `ChatRoomMessageListener.messageReceived` method. <br></br>
     * Obtains the corresponding `ChatPanel` and process the message there.
     *
     * @param evt the `ChatRoomMessageReceivedEvent` that notified us that a message has been received
     */
    override fun messageReceived(evt: ChatRoomMessageReceivedEvent) {
        // Fire notification as INCOMING_FILE is found
        val chatRoom = evt.getSourceChatRoom()
        val nickName = evt.getSourceChatRoomMember().getNickName() // sender
        val message = evt.getMessage()
        var msgBody = message.getContent()!!
        val msgUid = message.getMessageUID()
        if (ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD == evt.getEventType()) {
            val filePath = msgBody.split("#")[0]
            val fileName = filePath.substring(filePath.lastIndexOf('/') + 1)
            val title = aTalkApp.getResString(R.string.xFile_FILE_RECEIVING_FROM, nickName)
            fireChatNotification(chatRoom, INCOMING_FILE, title, fileName, msgUid)
        }
        else {
            val fireChatNotification: Boolean

            /*
             * It is uncommon for IRC clients to display popup notifications for messages which
             * are sent to public channels and which do not mention the nickname of the local user.
             */
            if (chatRoom.isSystem() || isPrivate(chatRoom)) fireChatNotification = true
            else {
                fireChatNotification = chatRoom.getUserNickname() != null // recipient
            }
            if (fireChatNotification) {
                // Block notification event if isHistoryMessage() and from autoJoined chatRoom
                if (!(evt.isHistoryMessage() && evt.isAutoJoin())) {
                    val title = aTalkApp.getResString(R.string.service_gui_MSG_RECEIVED, nickName)
                    // cmeng - extract only the msg body for notification display
                    if (IMessage.ENCODE_HTML != message.getMimeType()) {
                        msgBody = StringEscapeUtils.escapeHtml4(msgBody)
                    }
                    fireChatNotification(chatRoom, INCOMING_MESSAGE, title, msgBody, msgUid)
                }

                // update unread count for fired notification
                // Must pre-stored in ChatRoomWrapper attribute as crlf is null when aTalk is closed
                val mucService = MUCActivator.mucService
                val crWrapper = mucService.getChatRoomWrapperByChatRoom(chatRoom, false)
                if (crWrapper != null) {
                    val unreadCount = crWrapper.unreadCount + 1
                    crWrapper.unreadCount = unreadCount
                    val crlf = aTalk.getFragment(aTalk.CRL_FRAGMENT)
                    if (crlf is ChatRoomListFragment) {
                        crlf.updateUnreadCount(crWrapper)
                    }
                }
            }
        }
    }

    /**
     * Fired on new messages.
     *
     * @param evt the `MessageReceivedEvent` containing details on the received message
     */
    override fun messageReceived(evt: MessageReceivedEvent) {
        // Fire notification as INCOMING_FILE is found
        val contact = evt.getSourceContact()
        val message = evt.getSourceMessage()
        var msgBody = message.getContent()!!
        val msgUid = message.getMessageUID()
        if (ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD == evt.getEventType()) {
            val filePath = msgBody.split("#")[0]
            val fileName = filePath.substring(filePath.lastIndexOf('/') + 1)
            val title = aTalkApp.getResString(R.string.xFile_FILE_RECEIVING_FROM, contact.address)
            fireChatNotification(contact, INCOMING_FILE, title, fileName, msgUid)
        }
        else {
            // Fire as message notification
            val title = aTalkApp.getResString(R.string.service_gui_MSG_RECEIVED, contact.address)

            // cmeng - extract only the msg body for notification display
            if (IMessage.ENCODE_HTML != message.getMimeType()) {
                msgBody = StringEscapeUtils.escapeHtml4(message.getContent())
            }
            fireChatNotification(contact, INCOMING_MESSAGE, title, msgBody, msgUid)

            // update unread count for fired notification.
            updateMessageCount(contact)
        }
    }

    /**
     * Do nothing. Implements CallListener.outGoingCallCreated.
     *
     * @param event the `CallEvent`
     */
    override fun outgoingCallCreated(event: CallEvent) {
        val call = event.sourceCall
        call.addCallChangeListener(this)
        if (call.getCallPeers().hasNext()) {
            val peer = call.getCallPeers().next()
            peer.addCallPeerListener(this)
            peer.addCallPeerSecurityListener(this)
            peer.addCallPeerConferenceListener(this)
        }
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun peerAddressChanged(evt: CallPeerChangeEvent) {}

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun peerDisplayNameChanged(evt: CallPeerChangeEvent) {}

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun peerImageChanged(evt: CallPeerChangeEvent) {}

    /**
     * Fired when peer's state has changed
     *
     * @param evt fired CallPeerEvent
     */
    override fun peerStateChanged(evt: CallPeerChangeEvent) {
        try {
            val peer = evt.getSourceCallPeer()
            val call = peer.getCall()

            val newState = evt.newValue as CallPeerState
            val oldState = evt.oldValue as CallPeerState
            Timber.d("Peer State Changed to %s", newState)

            // Play the dialing audio when in connecting and initiating call state.
            // Stop the dialing audio when we enter any other state.
            if (newState == CallPeerState.INITIATING_CALL || newState == CallPeerState.CONNECTING) {
                /*
                 * The loopCondition will stay with the notification sound until the latter is being stopped.
                 * If by any chance the sound fails to stop by the time the peer is no longer referenced,
                 * do try to stop it then. That's why the loopCondition will weakly reference the peer.
                 */
                val weakPeer = WeakReference(peer)

                /* We want to play the dialing once for multiple CallPeers. */
                if (shouldPlayDialingSound(weakPeer)) {
                    val notification = fireNotification(DIALING) {
                        shouldPlayDialingSound(weakPeer)
                    }
                    if (notification != null) callNotifications[call] = notification
                }
            }
            else {
                val notification = callNotifications[call]
                if (notification != null) stopSound(notification)
            }

            // If we were already in state of CONNECTING_WITH_EARLY_MEDIA, then the server has already
            // taking care of playing the notification, so we don't need to fire a notification here.
            if (newState == CallPeerState.ALERTING_REMOTE_SIDE
                    && oldState != CallPeerState.CONNECTING_WITH_EARLY_MEDIA) {
                val weakPeer = WeakReference(peer)
                val notification = fireNotification(OUTGOING_CALL) {
                    val peer1 = weakPeer.get()
                    peer1 != null && CallPeerState.ALERTING_REMOTE_SIDE == peer1.getState()
                }
                if (notification != null) callNotifications[call] = notification
            }
            else if (newState == CallPeerState.BUSY) {
                // We start the busy sound only if we're in a simple call.
                if (!isConference(call!!)) {
                    val weakPeer = WeakReference(peer)
                    val notification = fireNotification(BUSY_CALL) {
                        val peer12 = weakPeer.get()
                        peer12 != null && CallPeerState.BUSY == peer12.getState()
                    }
                    if (notification != null) callNotifications[call] = notification
                }
            }
            else if (newState == CallPeerState.DISCONNECTED || newState == CallPeerState.FAILED) {
                fireNotification(HANG_UP)
            }
        } catch (t: Throwable) {
            if (t is ThreadDeath) throw t
            else {
                Timber.e(t, "An error occurred while trying to notify about a change in the state of a call peer.")
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun peerTransportAddressChanged(evt: CallPeerChangeEvent) {}

    /**
     * Notifies that a specific `Recorder` has stopped recording the media associated with
     * it.
     *
     * @param recorder the `Recorder` which has stopped recording its associated media
     */
    override fun recorderStopped(recorder: Recorder) {
        try {
            fireNotification(CALL_SAVED, SystrayService.NONE_MESSAGE_TYPE,
                aTalkApp.getResString(R.string.plugin_callrecordingconfig_CALL_SAVED),
                aTalkApp.getResString(R.string.plugin_callrecordingconfig_CALL_SAVED_TO, recorder.filename))
        } catch (t: Throwable) {
            if (t is ThreadDeath) throw t
            else {
                Timber.e(t, "An error occurred while trying to notify that the recording of a call has stopped.")
            }
        }
    }

    /**
     * Register all default notifications.
     */
    private fun registerDefaultNotifications() {
        val notificationService = NotificationWiringActivator.getNotificationService()

        // Register incoming message notifications.
        notificationService.registerDefaultNotificationForEvent(INCOMING_MESSAGE,
            NotificationAction.ACTION_POPUP_MESSAGE, null, null)
        notificationService.registerDefaultNotificationForEvent(INCOMING_MESSAGE,
            SoundNotificationAction(SoundProperties.INCOMING_MESSAGE, -1,
                isSoundNotificationEnabled = true, isSoundPlaybackEnabled = false, isSoundPCSpeakerEnabled = false))

        // Register incoming call notifications.
        notificationService.registerDefaultNotificationForEvent(INCOMING_CALL,
            NotificationAction.ACTION_POPUP_MESSAGE, null, null)
        val inCallSoundHandler = SoundNotificationAction(
            SoundProperties.INCOMING_CALL, 2000, isSoundNotificationEnabled = true, isSoundPlaybackEnabled = true, isSoundPCSpeakerEnabled = true)
        notificationService.run {
            registerDefaultNotificationForEvent(INCOMING_CALL, inCallSoundHandler)

            // Register outgoing call notifications.
            registerDefaultNotificationForEvent(OUTGOING_CALL,
                SoundNotificationAction(SoundProperties.OUTGOING_CALL, 3_000, isSoundNotificationEnabled = false, isSoundPlaybackEnabled = true, isSoundPCSpeakerEnabled = false))

            // Register busy call notifications.
            registerDefaultNotificationForEvent(BUSY_CALL,
                SoundNotificationAction(SoundProperties.BUSY, 1, isSoundNotificationEnabled = false, isSoundPlaybackEnabled = true, isSoundPCSpeakerEnabled = false))

            // Register dial notifications.
            registerDefaultNotificationForEvent(DIALING,
                SoundNotificationAction(SoundProperties.DIALING, -1, isSoundNotificationEnabled = false, isSoundPlaybackEnabled = true, isSoundPCSpeakerEnabled = false))

            // Register the hangup sound notification.
            registerDefaultNotificationForEvent(HANG_UP,
                SoundNotificationAction(SoundProperties.HANG_UP, -1, isSoundNotificationEnabled = false, isSoundPlaybackEnabled = true, isSoundPCSpeakerEnabled = false))

            // Register proactive notifications.
            registerDefaultNotificationForEvent(PROACTIVE_NOTIFICATION,
                NotificationAction.ACTION_POPUP_MESSAGE, null, null)

            // Register warning message notifications.
            registerDefaultNotificationForEvent(SECURITY_MESSAGE,
                NotificationAction.ACTION_POPUP_MESSAGE, null, null)

            // Register sound notification for security state off during a call.
            registerDefaultNotificationForEvent(CALL_SECURITY_ERROR,
                NotificationAction.ACTION_POPUP_MESSAGE, null, null)
            registerDefaultNotificationForEvent(CALL_SECURITY_ERROR,
                SoundNotificationAction(SoundProperties.CALL_SECURITY_ERROR, -1,
                    false, isSoundPlaybackEnabled = true, isSoundPCSpeakerEnabled = false))

            // Register sound notification for security state on during a call.
            registerDefaultNotificationForEvent(CALL_SECURITY_ON,
                SoundNotificationAction(SoundProperties.CALL_SECURITY_ON, -1,
                    isSoundNotificationEnabled = false, isSoundPlaybackEnabled = true, isSoundPCSpeakerEnabled = false))

            // Register sound notification for incoming files.
            registerDefaultNotificationForEvent(INCOMING_FILE,
                NotificationAction.ACTION_POPUP_MESSAGE, null, null)
            registerDefaultNotificationForEvent(INCOMING_FILE,
                SoundNotificationAction(SoundProperties.INCOMING_FILE, -1,
                    isSoundNotificationEnabled = true, isSoundPlaybackEnabled = false, isSoundPCSpeakerEnabled = false))

            // Register notification for saved calls.
            registerDefaultNotificationForEvent(CALL_SAVED,
                NotificationAction.ACTION_POPUP_MESSAGE, null, null)
        }
    }

    /**
     * Processes the received security message and security errors.
     *
     * @param event the event we received
     */
    override fun securityMessageReceived(event: CallPeerSecurityMessageEvent) {
        try {
            val messageTitleKey: String?
            // Android notification cannot support html tags
            val message = Html.fromHtml(event.getI18nMessage()).toString()
            when (event.getEventSeverity()) {
                SrtpListener.INFORMATION -> {
                    messageTitleKey = "service.gui.SECURITY_INFO"
                    aTalkApp.showToastMessage("$message: $messageTitleKey")
                    return
                }

                SrtpListener.WARNING -> messageTitleKey = "service.gui.SECURITY_WARNING"
                SrtpListener.SEVERE, SrtpListener.ERROR -> {
                    messageTitleKey = "service.gui.SECURITY_ERROR"
                    fireNotification(CALL_SECURITY_ERROR, SystrayService.WARNING_MESSAGE_TYPE,
                        NotificationWiringActivator.resources.getI18NString(messageTitleKey)!!, message)
                    return
                }
                // Whatever other severity there is or will be, we do not know how to react to it yet.
                else ->
                    messageTitleKey = null
            }
            if (messageTitleKey != null) {
                fireNotification(SECURITY_MESSAGE, SystrayService.INFORMATION_MESSAGE_TYPE,
                    NotificationWiringActivator.resources.getI18NString(messageTitleKey)!!, message)
            }
        } catch (t: Throwable) {
            if (t is ThreadDeath) throw t
            else {
                Timber.e(t, "An error occurred while trying to notify about a security message")
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun securityNegotiationStarted(securityStartedEvent: CallPeerSecurityNegotiationStartedEvent) {}

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun securityOff(securityEvent: CallPeerSecurityOffEvent) {}

    /**
     * When a `securityOnEvent` is received.
     *
     * @param securityEvent the event we received
     */
    override fun securityOn(securityEvent: CallPeerSecurityOnEvent) {
        try {
            val securityController = securityEvent.getSecurityController()
            val peer = securityEvent.source as CallPeer
            if (!securityController.requiresSecureSignalingTransport()
                    || peer.getProtocolProvider().isSignalingTransportSecure) {
                fireNotification(CALL_SECURITY_ON)
            }
        } catch (t: Throwable) {
            if (t is ThreadDeath) throw t
            else {
                Timber.e(t, "An error occurred while trying to notify about a security-related event")
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun securityTimeout(securityTimeoutEvent: CallPeerSecurityTimeoutEvent) {
        Timber.w("Notification security timeout: %s", securityTimeoutEvent.getSessionType())
    }

    /**
     * Implements the `ServiceListener` method. Verifies whether the passed event concerns
     * a `ProtocolProviderService` and adds the corresponding listeners.
     *
     * @param event The `ServiceEvent` object.
     */
    override fun serviceChanged(event: ServiceEvent) {
        val serviceRef = event.serviceReference

        // if the event is caused by a bundle being stopped, we don't want to know
        if (serviceRef.bundle.state == Bundle.STOPPING) return
        val service = NotificationWiringActivator.bundleContext!!.getService(serviceRef as ServiceReference<Any>)

        // we don't care if the source service is not a protocol provider
        if (service is ProtocolProviderService) {
            when (event.type) {
                ServiceEvent.REGISTERED -> handleProviderAdded(service)
                ServiceEvent.UNREGISTERING -> handleProviderRemoved(service)
            }
        }
    }

    /**
     * Stops all sounds for the given event type.
     *
     * @param data the event type for which we should stop sounds. One of the static event types defined in this class.
     */
    private fun stopSound(data: NotificationData?) {
        if (data == null) return
        try {
            val notificationService = NotificationWiringActivator.getNotificationService()
            if (notificationService != null) notificationService.stopNotification(data)
        } finally {
            /*
             * The field callNotifications associates a Call with a NotificationData for the
             * purposes of the stopSound method so the stopSound method should dissociate them
             * upon stopping a specific NotificationData.
             */
            val i = callNotifications.entries.iterator()
            while (i.hasNext()) {
                val (_, value) = i.next()
                if (data == value) i.remove()
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    override fun chatStateNotificationDeliveryFailed(event: ChatStateNotificationEvent) {}

    /**
     * Informs the user what is the chat state of his chat contacts.
     *
     * @param event the event containing details on the chat state notification
     */
    override fun chatStateNotificationReceived(event: ChatStateNotificationEvent) {
        try {
            // we don't care for proactive notifications, different than chat state sometimes after
            // closing chat we can see someone is composing, usually it's just server sanding that the
            // chat is inactive (ChatState.inactive)
            if (event.getChatState() != ChatState.composing) {
                return
            }

            // check whether the current chat window is showing the chat we received
            // a chat state info formChatActivity in such case don't show notifications
            val chatDescriptor = event.getChatDescriptor()
            val uiService = NotificationWiringActivator.uIService

            if (uiService != null) {
                val chat = uiService.currentChat
                if (chat != null && chat.isChatFocused) {
                    val chatTransport = (chat as ChatPanel).chatSession!!.currentChatTransport
                    if (chatDescriptor == chatTransport!!.descriptor) return

//                    ChatSession chatSession = ((ChatPanel) chat).getChatSession()mChatActivity
//                    if (chatSession instanceof MetaContactChatSession) {
//                        MetaContact metaContact = uiService.getChatContact(chat)mChatActivity
//                        if ((metaContact != null) && metaContact.containsContact((Contact) chatDescriptor)) {
//                            returnmChatActivity
//                        }
//                    }
//                    if (chatSession instanceof ConferenceChatSession) {
//                      if (((ChatRoomWrapper) chatSession.getDescriptor()).getChatRoom().equals(chatDescriptor))
//                            returnmChatActivity
//                    }
                }
            }

            val currentTime = System.currentTimeMillis()
            val fromJid = event.getMessage().from.toString()

            if (proactiveTimer.isNotEmpty()) {
                // first remove chatDescriptors that have been here longer than the timeout to avoid memory leaks
                val entries = proactiveTimer.entries.iterator()
                while (entries.hasNext()) {
                    val (_, lastNotificationDate) = entries.next()

                    // The entry is outdated
                    if (lastNotificationDate + 30000 < currentTime) {
                        entries.remove()
                    }
                }

                // Now, check if the chatDescriptor is still in the mapmChatActivity We already notified the others about this
                if (proactiveTimer.containsKey(chatDescriptor)) {
                    return
                }
            }
            proactiveTimer[chatDescriptor] = currentTime
            fireChatNotification(chatDescriptor, PROACTIVE_NOTIFICATION, fromJid,
                aTalkApp.getResString(R.string.service_gui_PROACTIVE_NOTIFICATION), null)
        } catch (t: Throwable) {
            if (t is ThreadDeath) throw t
            else {
                Timber.e(t, "An error occurred while handling a chat state notification.")
            }
        }
    }

    companion object {
        /**
         * Default event type for a busy call.
         */
        const val BUSY_CALL = "BusyCall"

        /**
         * Default event type for call been saved using a recorder.
         */
        const val CALL_SAVED = "CallSaved"

        /**
         * Default event type for security error on a call.
         */
        const val CALL_SECURITY_ERROR = "CallSecurityError"

        /**
         * Default event type for activated security on a call.
         */
        const val CALL_SECURITY_ON = "CallSecurityOn"

        /**
         * Default event type for dialing.
         */
        const val DIALING = "Dialing"

        /**
         * Default event type for hanging up calls.
         */
        const val HANG_UP = "HangUp"

        /**
         * Default event type for receiving calls (incoming calls).
         */
        const val INCOMING_CALL = "IncomingCall"

        /**
         * Default event type for incoming file transfers.
         */
        const val INCOMING_FILE = "IncomingFile"

        /**
         * Default event type for incoming invitation received.
         */
        const val INCOMING_INVITATION = "IncomingInvitation"

        /**
         * Default event type for receiving messages.
         */
        const val INCOMING_MESSAGE = "IncomingMessage"

        /**
         * Default event type for missed call.
         */
        const val MISSED_CALL = "MissedCall"

        /**
         * Default event type for outgoing calls.
         */
        const val OUTGOING_CALL = "OutgoingCall"

        /**
         * Default event type for proactive notifications (chat state notifications when chatting).
         */
        const val PROACTIVE_NOTIFICATION = "ProactiveNotification"

        /**
         * Default event type when a secure message received.
         */
        const val SECURITY_MESSAGE = "SecurityMessage"

        /**
         * Fires a chat message notification for the given event type through the `NotificationService`.
         *
         * @param chatDescriptor the chat contact to which the chat message correspondsChatActivity the chat contact could be a
         * Contact or a ChatRoom.
         * @param eventType the event type for which we fire a notification
         * @param messageTitle the title of the message
         * @param message the content of the message
         * @param messageUID the message UID
         */
        fun fireChatNotification(
                chatDescriptor: Any, eventType: String, messageTitle: String,
                message: String, messageUID: String?,
        ) {
            val notificationService = NotificationWiringActivator.getNotificationService()
                    ?: return
            var popupActionHandler: NotificationAction? = null
            val uiService = NotificationWiringActivator.uIService
            var chatPanel: Chat? = null
            var contactIcon: ByteArray? = null
            if (chatDescriptor is Contact) {
                if (uiService != null) chatPanel = uiService.getChat(chatDescriptor)
                contactIcon = chatDescriptor.image
                if (contactIcon == null) {
                    contactIcon = AndroidImageUtil.getImageBytes(aTalkApp.globalContext, R.drawable.person_photo)
                }
            }
            else if (chatDescriptor is ChatRoom) {
                // For system rooms we don't want to send notification events.
                if (chatDescriptor.isSystem()) return
                if (uiService != null) {
                    chatPanel = uiService.getChat(chatDescriptor)
                }
            }

            // Do not popup notification if the chatPanel is focused and for INCOMING_MESSAGE or INCOMING_FILE
            if (chatPanel != null && chatPanel.isChatFocused && (eventType == INCOMING_MESSAGE || eventType == INCOMING_FILE)) {
                popupActionHandler = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_POPUP_MESSAGE)
                popupActionHandler!!.isEnabled = false
            }
            val extras = HashMap<String, Any>()
            extras[NotificationData.POPUP_MESSAGE_HANDLER_TAG_EXTRA] = chatDescriptor
            notificationService.fireNotification(eventType, SystrayService.INFORMATION_MESSAGE_TYPE,
                messageTitle, message, contactIcon, extras)

            // Reset the popupActionHandler to enable for ACTION_POPUP_MESSAGE for incomingMessage if it was disabled
            if (popupActionHandler != null) popupActionHandler.isEnabled = true
        }

        /**
         * Fires a notification for the given event type through the `NotificationService`. The
         * event type is one of the static constants defined in the `NotificationManager` class.
         *
         * **Note**: The uses of the method at the time of this writing do not take measures to stop looping sounds
         * if the respective notifications use them i.e. there is implicit agreement that the notifications fired through
         * the method do not loop sounds. Consequently, the method passes arguments to `NotificationService`
         * so that sounds are played once only.
         *
         * @param eventType the event type for which we want to fire a notification
         */
        fun fireNotification(eventType: String) {
            val notificationService = NotificationWiringActivator.getNotificationService()
            if (notificationService != null) notificationService.fireNotification(eventType)
        }

        /**
         * Fires a notification for the given event type through the `NotificationService`. The
         * event type is one of the static constants defined in the `NotificationManager` class.
         *
         * @param eventType the event type for which we want to fire a notification
         * @param loopCondition the method which will determine whether any sounds played as part of the specified
         * notification will continue looping
         *
         * @return a reference to the fired notification to stop it.
         */
        private fun fireNotification(eventType: String, loopCondition: Callable<Boolean>): NotificationData? {
            return fireNotification(eventType, "", "", null, loopCondition)
        }

        /**
         * Fires a notification through the `NotificationService` with a specific event type, a
         * specific message title and a specific message.
         *
         * **Note**: The uses of the method at the time of this writing do not take measures to
         * stop looping sounds if the respective notifications use them i.e. there is implicit
         * agreement that the notifications fired through the method do not loop sounds. Consequently,
         * the method passes arguments to `NotificationService` so that sounds are played once only.
         *
         * @param eventType the event type of the notification to be fired
         * @param msgType the notification sub-category message type
         * @param messageTitle the title of the message to be displayed by the notification to be fired if such a
         * display is supported
         * @param message the message to be displayed by the notification to be fired if such a display is supported
         */
        private fun fireNotification(eventType: String, msgType: Int, messageTitle: String, message: String) {
            val notificationService = NotificationWiringActivator.getNotificationService()
            if (notificationService != null) {
                notificationService.fireNotification(eventType, msgType, messageTitle, message, null)
            }
        }

        /**
         * Fires a message notification for the given event type through the `NotificationService`.
         *
         * @param eventType the event type for which we fire a notification
         * @param messageTitle the title of the message
         * @param message the content of the message
         * @param cmdargs the value to be provided to
         * [CommandNotificationHandler.execute] as the `cmdargs` argument
         * @param loopCondition the method which will determine whether any sounds played as part of the specified
         * notification will continue looping
         *
         * @return a reference to the fired notification to stop it.
         */
        private fun fireNotification(
                eventType: String, messageTitle: String,
                message: String, cmdargs: Map<String, String>?, loopCondition: Callable<Boolean>?,
        ): NotificationData? {
            val notificationService = NotificationWiringActivator.getNotificationService()
            return if (notificationService == null)
                null
            else {
                val extras = HashMap<String, Any>()
                if (cmdargs != null) {
                    extras[NotificationData.COMMAND_NOTIFICATION_HANDLER_CMDARGS_EXTRA] = cmdargs
                }
                if (loopCondition != null) {
                    extras[NotificationData.SOUND_NOTIFICATION_HANDLER_LOOP_CONDITION_EXTRA] = loopCondition
                }
                notificationService.fireNotification(eventType,
                    SystrayService.INFORMATION_MESSAGE_TYPE, messageTitle, message, null, extras)
            }
        }// get all registered provider factories

        /**
         * Returns all `ProtocolProviderFactory`s obtained from the bundle context.
         *
         * @return all `ProtocolProviderFactory`s obtained from the bundle context
         */
        val protocolProviderFactories: Map<Any, Any>
            get() {
                var serRefs: Array<ServiceReference<*>?>? = null
                try {
                    // get all registered provider factories
                    serRefs = NotificationWiringActivator.bundleContext!!
                        .getServiceReferences(ProtocolProviderFactory::class.java.name, null)
                } catch (e: InvalidSyntaxException) {
                    Timber.e("NotificationManager : %s", e.message)
                }
                val providerFactoriesMap = Hashtable<Any, ProtocolProviderFactory>()
                if (serRefs != null) {
                    for (serRef in serRefs) {
                        val providerFactory = NotificationWiringActivator.bundleContext!!.getService(serRef as ServiceReference<Any>) as ProtocolProviderFactory
                        providerFactoriesMap[serRef.getProperty(ProtocolProviderFactory.PROTOCOL)] = providerFactory
                    }
                }
                return providerFactoriesMap
            }// get all registered provider factories

        /**
         * Returns all protocol providers currently registered.
         *
         * @return all protocol providers currently registered.
         */
        val protocolProviders: List<ProtocolProviderService>
            get() {
                var serRefs: Array<ServiceReference<*>?>? = null
                try {
                    // get all registered provider factories
                    serRefs = NotificationWiringActivator.bundleContext!!
                        .getServiceReferences(ProtocolProviderService::class.java.name, null)
                } catch (e: InvalidSyntaxException) {
                    Timber.e("NotificationManager : %s", e.message)
                }
                val providersList = ArrayList<ProtocolProviderService>()
                if (serRefs != null) {
                    for (serRef in serRefs) {
                        val pp = NotificationWiringActivator.bundleContext!!.getService(serRef as ServiceReference<Any>) as ProtocolProviderService
                        providersList.add(pp)
                    }
                }
                return providersList
            }

        /**
         * Determines whether a specific `ChatRoom` is private i.e. represents a one-to-one
         * conversation which is not a channel. Since the interface [ChatRoom] does not expose
         * the private property, an heuristic is used as a workaround: (1) a system
         * `ChatRoom` is obviously not private and (2) a `ChatRoom` is private
         * if it has only one `ChatRoomMember` who is not the local user.
         *
         * @param chatRoom the `ChatRoom` to be determined as private or not
         *
         * @return `true` if the specified `ChatRoom` is privateChatActivity otherwise, `false`
         */
        private fun isPrivate(chatRoom: ChatRoom): Boolean {
            if (!chatRoom.isSystem() && chatRoom.isJoined() && chatRoom.getMembersCount() == 1) {
                val nickname = chatRoom.getUserNickname().toString()
                for (member in chatRoom.getMembers()) {
                    if (nickname == member.getNickName()) return false
                }
                return true
            }
            return false
        }

        /**
         * Determines whether the `DIALING` sound notification should be played for a specific `CallPeer`.
         *
         * @param weakPeer the `CallPeer` for which it is to be determined whether the `DIALING`
         * sound notification is to be played
         *
         * @return `true` if the `DIALING` sound notification should be played for the
         * specified `callPeer`mChatActivity otherwise, code>false
         */
        private fun shouldPlayDialingSound(weakPeer: WeakReference<CallPeer>): Boolean {
            val peer = weakPeer.get() ?: return false
            val call = peer.getCall() ?: return false
            val conference = call.getConference() ?: return false
            var play = false
            for (aCall in conference.calls) {
                val peerIter = aCall.getCallPeers()
                while (peerIter.hasNext()) {
                    val aPeer = peerIter.next()

                    // The peer is still in a call/telephony conference so the DIALING sound may need to be played.
                    if (peer == aPeer) play = true
                    val state = peer.getState()
                    if (CallPeerState.INITIATING_CALL == state || CallPeerState.CONNECTING == state) {
                        // The DIALING sound should be played for the first CallPeer only.
                        if (peer != aPeer)
                            return false
                    }
                    else {
                        /*
                         * The DIALING sound should not be played if there is a CallPeer which does
                         * not require the DIALING sound to be played.
                         */
                        return false
                    }
                }
            }
            return play
        }

        /**
         * Update message unread count for the actual recipient (contact). The value must only
         * pre-store in metaContact unreadCount attribute, as clf is null when aTalk is closed
         * Note: Carbon copy message does not trigger messageReceived().
         *
         * @param contact the message recipient to which the unread count is to be updated
         */
        fun updateMessageCount(contact: Contact?) {
            val metaContact = AndroidGUIActivator.contactListService.findMetaContactByContact(contact)
            if (metaContact != null) {
                val unreadCount = metaContact.getUnreadCount() + 1
                metaContact.setUnreadCount(unreadCount)
                val clf = aTalk.getFragment(aTalk.CL_FRAGMENT)
                if (clf is ContactListFragment) {
                    clf.updateUnreadCount(metaContact)
                }
            }
        }
    }
}