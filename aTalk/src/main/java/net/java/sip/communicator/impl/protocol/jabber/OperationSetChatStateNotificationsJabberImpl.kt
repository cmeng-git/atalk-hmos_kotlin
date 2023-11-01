/**
 * aTalk (jitsi-android fork), the OpenSource Java VoIP and Instant Messaging client.
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.protocol.event.ChatStateNotificationEvent
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum
import org.atalk.hmos.gui.chat.MetaContactChatTransport
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.chat2.Chat
import org.jivesoftware.smack.chat2.ChatManager
import org.jivesoftware.smack.packet.*
import org.jivesoftware.smackx.chatstates.ChatState
import org.jivesoftware.smackx.chatstates.ChatStateListener
import org.jivesoftware.smackx.chatstates.ChatStateManager
import org.jivesoftware.smackx.muc.MultiUserChatManager
import org.jivesoftware.smackx.xevent.MessageEventManager
import org.jivesoftware.smackx.xevent.MessageEventNotificationListener
import org.jivesoftware.smackx.xevent.MessageEventRequestListener
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.Jid
import timber.log.Timber

/**
 * OperationSet that handle chat state notifications
 *
 * @author Damian Minkov
 * @author Emil Ivov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class OperationSetChatStateNotificationsJabberImpl internal constructor(provider: ProtocolProviderServiceJabberImpl) : AbstractOperationSetChatStateNotifications<ProtocolProviderServiceJabberImpl?>(provider), ChatStateListener {
    /**
     * An active instance of the opSetPeersPresence operation set. We're using it to map incoming
     * events to contacts in our contact list.
     */
    private var opSetPeersPresence: OperationSetPersistentPresenceJabberImpl? = null

    /**
     * An active instance of the opSetBasicIM operation set.
     */
    private var opSetBasicIM: OperationSetBasicInstantMessagingJabberImpl? = null

    /**
     * An instant of the smack ChatManager for the current PPS
     */
    private var mChatManager: ChatManager? = null

    /**
     * An instant of the smack MultiUserChatManager for the current PPS
     */
    private var multiUserChatManager: MultiUserChatManager? = null

    /**
     * The manger which send us the chat state info and through which we send info
     */
    private var messageEventManager: MessageEventManager? = null
    private var chatStateManager: ChatStateManager? = null

    /**
     * provider a ref to the `ProtocolProviderServiceImpl` that created us and that we'll use
     * for retrieving the underlying aim connection.
     */
    init {
        // We use this listener to seize the moment when the protocol provider has been successfully registered.
        provider.addRegistrationStateChangeListener(ProviderRegListener())
    }

    /**
     * Sends a chat state notification to the chatDescriptor that chat state we are in
     * XEP-0085 chat state sending
     *
     * @param chatDescriptor the `chatDescriptor` i.e. Contact or ChatRoom to notify
     * @param chatState the chat state that we have entered.
     *
     * @throws java.lang.IllegalStateException if the underlying stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException if `notifiedContact` is not
     * an instance belonging to the underlying implementation.
     */
    @Throws(IllegalStateException::class, IllegalArgumentException::class, NotConnectedException::class, InterruptedException::class)
    override fun sendChatStateNotification(chatDescriptor: Any?, chatState: ChatState?) {
        if (mChatManager == null) return
        val entityBareJid: EntityBareJid?
        when (chatDescriptor) {
            is VolatileContactJabberImpl -> {
                Timber.d("send ChatState Notification not available for: %s", chatDescriptor)
            }
            is Contact -> {
                entityBareJid = chatDescriptor.contactJid as EntityBareJid
                if (chatState == ChatState.gone) opSetBasicIM!!.purgeGoneJidThreads(entityBareJid)
                val chat = mChatManager!!.chatWith(entityBareJid)
                chatStateManager!!.setCurrentState(chatState, chat)
            }
            is ChatRoom -> {
                // XEP-0085: A client SHOULD NOT generate <gone/> notifications in group chat.
                if (ChatState.gone == chatState) return
                entityBareJid = chatDescriptor.getIdentifier()
                val mucChat = multiUserChatManager!!.getMultiUserChat(entityBareJid)
                chatStateManager!!.setCurrentState(chatState, mucChat)
            }
            else -> {
                throw IllegalArgumentException("The specified chatDescriptor is not valid.$chatDescriptor")
            }
        }
    }

    /**
     * Utility method throwing an exception if the stack is not properly initialized.
     *
     * @throws java.lang.IllegalStateException if the underlying stack is not registered and initialized.
     */
    @Throws(IllegalStateException::class)
    override fun assertConnected() {
        if (parentProvider != null && !parentProvider.isRegistered
                && opSetPeersPresence!!.getPresenceStatus()!!.isOnline) {
            // if we are not registered but the current status is online; change the current status
            opSetPeersPresence!!.fireProviderStatusChangeEvent(opSetPeersPresence!!.getPresenceStatus(),
                    parentProvider.jabberStatusEnum!!.getStatus(JabberStatusEnum.OFFLINE))
        }
        super.assertConnected()
    }

    /**
     * Our listener that will tell us when we're registered and ready to accept us as a listener.
     */
    private inner class ProviderRegListener : RegistrationStateChangeListener {
        /**
         * The method is called by a ProtocolProvider implementation whenever a change in the
         * registration state of the corresponding provider had occurred.
         *
         * @param evt ProviderStatusChangeEvent the event describing the status change.
         */
        override fun registrationStateChanged(evt: RegistrationStateChangeEvent) {
            if (evt.getNewState() === RegistrationState.REGISTERED) {
                /* XMPPTCPConnection connection for chat session. */
                val connection = parentProvider!!.connection
                opSetPeersPresence = parentProvider
                        .getOperationSet(OperationSetPersistentPresence::class.java) as OperationSetPersistentPresenceJabberImpl?
                opSetBasicIM = parentProvider
                        .getOperationSet(OperationSetBasicInstantMessaging::class.java) as OperationSetBasicInstantMessagingJabberImpl?
                messageEventManager = MessageEventManager.getInstanceFor(parentProvider.connection)
                messageEventManager!!.addMessageEventRequestListener(JabberMessageEventRequestListener())
                messageEventManager!!.addMessageEventNotificationListener(IncomingMessageEventsListener())

                // smack ChatStatManager#ChatStateListener 4.4.3 does not support group chat state notifications (enhaced to support)
                mChatManager = ChatManager.getInstanceFor(connection)
                multiUserChatManager = MultiUserChatManager.getInstanceFor(connection)
                chatStateManager = ChatStateManager.getInstance(connection)
                chatStateManager!!.addChatStateListener(this@OperationSetChatStateNotificationsJabberImpl)

                // if (smackChatStateListener == null) {
                //     smackChatStateListener = new SmackChatStateListener();
                //     mConnection.addAsyncStanzaListener(smackChatStateListener, CHATSTATE);
                // }
            } else if (evt.getNewState() === RegistrationState.UNREGISTERED || evt.getNewState() === RegistrationState.AUTHENTICATION_FAILED || evt.getNewState() === RegistrationState.CONNECTION_FAILED) {
                // if (parentProvider.connection != null) {
                //       parentProvider.connection.removeAsyncStanzaListener(smackChatStateListener);
                //  }
                //  smackChatStateListener = null;
                mChatManager = null
                multiUserChatManager = null
                if (chatStateManager != null) {
                    chatStateManager!!.removeChatStateListener(this@OperationSetChatStateNotificationsJabberImpl)
                    chatStateManager = null
                }
                if (messageEventManager != null) {
                    messageEventManager = null
                }
            }
        }
    }

    /**
     * Listens for incoming request for chat state info
     */
    private inner class JabberMessageEventRequestListener : MessageEventRequestListener {
        override fun deliveredNotificationRequested(from: Jid, packetID: String, messageEventManager: MessageEventManager) {
            try {
                messageEventManager.sendDeliveredNotification(from, packetID)
            } catch (e: NotConnectedException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        override fun displayedNotificationRequested(from: Jid, packetID: String,
                messageEventManager: MessageEventManager) {
            try {
                messageEventManager.sendDisplayedNotification(from, packetID)
            } catch (e: NotConnectedException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        override fun composingNotificationRequested(from: Jid, packetID: String,
                messageEventManager: MessageEventManager) {
            try {
                messageEventManager.sendComposingNotification(from, packetID)
            } catch (e: NotConnectedException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        override fun offlineNotificationRequested(from: Jid, packetID: String,
                messageEventManager: MessageEventManager) {
            try {
                messageEventManager.sendCancelledNotification(from, packetID)
            } catch (e: NotConnectedException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Receives incoming chat state info, Jid from is always a buddy (currently not implemented in aTalk)
     * #TODO - to use for message delivery
     *
     * @see [XEP-22: Message Events](http://xmpp.org/extensions/xep-0022.html)
     * Note: This specification has been obsoleted in favor of XEP-0085 and XEP-0184.
     */
    private inner class IncomingMessageEventsListener : MessageEventNotificationListener {
        /**
         * Called when a notification of message delivered is received.
         *
         * @param from the user that sent the notification.
         * @param packetID the id of the message that was sent.
         */
        override fun deliveredNotification(from: Jid, packetID: String) {}

        /**
         * Called when a notification of message displayed is received.
         *
         * @param from the user that sent the notification.
         * @param packetID the id of the message that was sent.
         */
        override fun displayedNotification(from: Jid, packetID: String) {}

        /**
         * Called when a notification that the receiver of the message is composing a reply is received.
         *
         * @param from the user that sent the notification.
         * @param packetID the id of the message that was sent.
         */
        override fun composingNotification(from: Jid, packetID: String) {
            val bareFrom = from.asBareJid()
            var sourceContact = opSetPeersPresence!!.findContactByJid(bareFrom)

            // create the volatile contact if not found
            if (sourceContact == null) {
                sourceContact = opSetPeersPresence!!.createVolatileContact(bareFrom)
            }
            val event = ChatStateNotificationEvent(sourceContact, ChatState.composing, null)
            fireChatStateNotificationsEvent(event)
        }

        /**
         * Called when a notification that the receiver of the message is offline is received.
         *
         * @param from the user that sent the notification.
         * @param packetID the id of the message that was sent.
         */
        override fun offlineNotification(from: Jid, packetID: String) {}

        /**
         * Called when a notification that the receiver of the message canceled the reply is received.
         *
         * @param from the user that sent the notification.
         * @param packetID the id of the message that was sent.
         */
        override fun cancelledNotification(from: Jid, packetID: String) {
            val bareFrom = from.asBareJid()
            var sourceContact = opSetPeersPresence!!.findContactByJid(bareFrom)

            // create the volatile contact if not found
            if (sourceContact == null) {
                sourceContact = opSetPeersPresence!!.createVolatileContact(bareFrom)
            }
            val event = ChatStateNotificationEvent(sourceContact, ChatState.inactive, null)
            fireChatStateNotificationsEvent(event)
        }
    }
    //    private Object getChatDescriptor(BareJid bareJid) {
    //        Object chatDescriptor = null;
    //
    //        OperationSetMultiUserChat mucOpSet = parentProvider.getOperationSet(OperationSetMultiUserChat.class);
    //        if (mucOpSet != null) {
    //            List<ChatRoom> chatRooms = mucOpSet.getCurrentlyJoinedChatRooms();
    //            for (ChatRoom chatRoom : chatRooms) {
    //                if (chatRoom.getIdentifier().equals(bareJid)) {
    //                    chatDescriptor = chatRoom;
    //                    break;
    //                }
    //            }
    //        }
    //        if (chatDescriptor == null) {
    //            chatDescriptor = opSetPeersPresence.findContactByJid(bareJid);
    //        }
    //        return chatDescriptor;
    //    }
    /**
     * The listener that we use to track chat state notifications according to XEP-0085.
     * Called by smack when the state of a chat changes.
     * Fired when the state of a chat with another user changes.
     *
     * @param state the new state of the participant.
     * @param message the message carrying the chat state.
     */
    override fun stateChanged(chat: Chat, state: ChatState, message: Message) {
        val fromJid = message.from
        val bareJid = fromJid.asBareJid()
        Timber.d("ChatState Event: %s is in '%s'", fromJid, state.name)
        var isPrivateMessagingAddress = false
        val mucOpSet = parentProvider!!.getOperationSet(OperationSetMultiUserChat::class.java)
        if (mucOpSet != null) {
            val chatRooms = mucOpSet.getCurrentlyJoinedChatRooms()
            for (chatRoom in chatRooms!!) {
                if (chatRoom!!.getName() == bareJid.toString()) {
                    isPrivateMessagingAddress = true
                    break
                }
            }
        }

        // Object chatDescriptor = getChatDescriptor(bareJid);
        var chatDescriptor: Any? = opSetPeersPresence!!.findContactByJid(if (isPrivateMessagingAddress) message.from else bareJid)
        if (chatDescriptor == null) {
            // in private messaging we can receive some errors when we left room (isPrivateMessagingAddress == false)
            // and we try to send some message
            if (message.error != null) {
                chatDescriptor = opSetPeersPresence!!.findContactByJid(bareJid)
            }
            if (chatDescriptor == null) {
                // create the volatile contact from new source contact
                if (message.type != Message.Type.groupchat) {
                    chatDescriptor = opSetPeersPresence!!.createVolatileContact(bareJid, isPrivateMessagingAddress)
                }
            }

            // Must not pass in a null descriptor to ChatStateNotificationEvent() => IllegalArgumentException (FFR)
            if (chatDescriptor == null) {
                chatDescriptor = bareJid
            }
        }
        val event = ChatStateNotificationEvent(chatDescriptor, state, message)
        if (message.error != null) fireChatStateNotificationsDeliveryFailedEvent(event) else {
            // Invalid the last thread associated with the contact when he is gone
            if (state == ChatState.gone) opSetBasicIM!!.purgeGoneJidThreads(bareJid) else if (state == ChatState.active) MetaContactChatTransport.setChatStateSupport(true)
            fireChatStateNotificationsEvent(event)
        }
    }
}