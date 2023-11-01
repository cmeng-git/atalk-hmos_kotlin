/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions
 * and limitations under the License.
 */
package org.atalk.hmos.gui.chat.conference

import net.java.sip.communicator.service.protocol.AdHocChatRoom
import net.java.sip.communicator.service.protocol.ChatRoomConfigurationForm
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomParticipantPresenceChangeEvent
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomParticipantPresenceListener
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum
import net.java.sip.communicator.util.ConfigurationUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.chat.ChatPanel
import org.atalk.hmos.gui.chat.ChatSession
import org.atalk.hmos.gui.chat.ChatTransport
import java.util.*

/**
 * An implementation of `ChatSession` for ad-hoc conference chatting.
 *
 * @author Valentin Martinet
 * @author Eng Chong Meng
 */
class AdHocConferenceChatSession(sessionRenderer: ChatPanel, chatRoomWrapper: AdHocChatRoomWrapper) : ChatSession(), AdHocChatRoomParticipantPresenceListener {

    override var currentChatTransport: ChatTransport? = null
    private val chatRoomWrapper: AdHocChatRoomWrapper
    private val sessionRenderer: ChatPanel

    /**
     * Creates an instance of `AdHocConferenceChatSession`, by specifying the
     * sessionRenderer to be used for communication with the UI and the ad-hoc chat room
     * corresponding to this conference session.
     *
     * sessionRenderer the renderer to be used for communication with the UI.
     * chatRoomWrapper the ad-hoc chat room corresponding to this conference session.
     */
    init {
        this.sessionRenderer = sessionRenderer
        this.chatRoomWrapper = chatRoomWrapper
        currentChatTransport = AdHocConferenceChatTransport(this, chatRoomWrapper.adHocChatRoom)
        chatTransports.add(currentChatTransport!!)
        initChatParticipants()
        val chatRoom = chatRoomWrapper.adHocChatRoom
        chatRoom!!.addParticipantPresenceListener(this)
    }

    /**
     * Returns the descriptor of this chat session.
     *
     * @return the descriptor of this chat session.
     */
    override val descriptor: Any
        get() = chatRoomWrapper

    /**
     * Returns the chat identifier.
     *
     * @return the chat identifier
     */
    override val chatId: String
        get() = chatRoomWrapper.adHocChatRoomID!!

    /**
     * Disposes this chat session.
     */
    override fun dispose() {
        val chatRoom = chatRoomWrapper.adHocChatRoom
        chatRoom!!.removeParticipantPresenceListener(this)
    }

    /**
     * Returns the entityJid of the ad-hoc chat room.
     *
     * @return the entityJid of the ad-hoc chat room.
     */
    override val chatEntity: String
        get() = chatRoomWrapper.adHocChatRoomName!!

    /**
     * Returns the configuration form corresponding to the chat room.
     *
     * @return the configuration form corresponding to the chat room.
     * @throws OperationFailedException if no configuration form is available for the chat room.
     */
    @get:Throws(OperationFailedException::class)
    val chatConfigurationForm: ChatRoomConfigurationForm?
        get() = null

    /**
     * Returns the default mobile number used to send sms-es in this session. In the case of
     * conference this is for now null.
     *
     * @return the default mobile number used to send sms-es in this session.
     */
    /**
     * Sets the default mobile number used to send sms-es in this session.
     *
     * smsPhoneNumber The default mobile number used to send sms-es in this session.
     */
    override var defaultSmsNumber: String?
        get() = null
        set(smsPhoneNumber) {}

    /**
     * Returns a collection of the last N number of messages given by count.
     *
     * @param count The number of messages from history to return.
     * @return a collection of the last N number of messages given by count.
     */
    override fun getHistory(count: Int): Collection<Any>? {
        val metaHistory = AndroidGUIActivator.metaHistoryService
                ?: return null

        // If the MetaHistoryService is not registered we have nothing to do here. The history
        // could be "disabled" from the user through one of the configuration forms.
        return metaHistory.findLast(chatHistoryFilter as Array<String>, chatRoomWrapper.adHocChatRoom!!,
                ConfigurationUtils.getChatHistorySize())
    }

    /**
     * Returns a collection of the last N number of messages given by count.
     *
     * @param date The date up to which we're looking for messages.
     * @param count The number of messages from history to return.
     * @return a collection of the last N number of messages given by count.
     */
    override fun getHistoryBeforeDate(date: Date, count: Int): Collection<Any>? {
        val metaHistory = AndroidGUIActivator.metaHistoryService
                ?: return null

        // If the MetaHistoryService is not registered we have nothing to do here. The history
        // could be "disabled" from the user through one of the configuration forms.
        return metaHistory.findLastMessagesBefore(chatHistoryFilter,
                chatRoomWrapper.adHocChatRoom!!, date, ConfigurationUtils.getChatHistorySize())
    }

    /**
     * Returns a collection of the last N number of messages given by count.
     *
     * @param date The date from which we're looking for messages.
     * @param count The number of messages from history to return.
     * @return a collection of the last N number of messages given by count.
     */
    override fun getHistoryAfterDate(date: Date, count: Int): Collection<Any?>? {
        val metaHistory = AndroidGUIActivator.metaHistoryService
                ?: return null

        // If the MetaHistoryService is not registered we have nothing to do here. The history
        // could be "disabled" from the user through one of the configuration forms.
        return metaHistory.findFirstMessagesAfter(chatHistoryFilter,
                chatRoomWrapper.adHocChatRoom!!, date, ConfigurationUtils.getChatHistorySize())
    }// If the MetaHistoryService is not registered we have nothing to do here. The history
    // could be "disabled" from the user through one of the configuration forms.
    /**
     * Returns the start date of the history of this chat session.
     *
     * @return the start date of the history of this chat session.
     */
    override val historyStartDate: Date
        get() {
            val metaHistory = AndroidGUIActivator.metaHistoryService
                    ?: return Date(0)

            // If the MetaHistoryService is not registered we have nothing to do here. The history
            // could be "disabled" from the user through one of the configuration forms.
            var startHistoryDate = Date(0)
            val firstMessage = metaHistory.findFirstMessagesAfter(chatHistoryFilter,
                    chatRoomWrapper.adHocChatRoom!!, Date(0), 1)
            if (firstMessage.isNotEmpty()) {
                val fms = firstMessage.iterator()
                val evt = fms.next()
                if (evt is MessageDeliveredEvent) {
                    startHistoryDate = evt.getTimestamp()
                } else if (evt is MessageReceivedEvent) {
                    startHistoryDate = evt.getTimestamp()
                }
            }
            return startHistoryDate
        }// If the MetaHistoryService is not registered we have nothing to do here. The history
    // could be "disabled" from the user through one of the configuration forms.
    /**
     * Returns the end date of the history of this chat session.
     *
     * @return the end date of the history of this chat session.
     */
    override val historyEndDate: Date
        get() {
            val metaHistory = AndroidGUIActivator.metaHistoryService
                    ?: return Date(0)

            // If the MetaHistoryService is not registered we have nothing to do here. The history
            // could be "disabled" from the user through one of the configuration forms.
            var endHistoryDate = Date(0)
            val lastMessage = metaHistory.findLastMessagesBefore(chatHistoryFilter,
                    chatRoomWrapper.adHocChatRoom!!, Date(Long.MAX_VALUE), 1)
            if (lastMessage.isNotEmpty()) {
                val fms = lastMessage.iterator()
                val evt = fms.next()
                if (evt is MessageDeliveredEvent) {
                    endHistoryDate = evt.getTimestamp()
                } else if (evt is MessageReceivedEvent) {
                    endHistoryDate = evt.getTimestamp()
                }
            }
            return endHistoryDate
        }

    /**
     * Returns the `ChatSessionRenderer` that provides the connection between this chat
     * session and its UI.
     *
     * @return The `ChatSessionRenderer`.
     */
    override val chatSessionRenderer: ChatPanel
        get() = sessionRenderer

    /**
     * Returns `true` if this contact is persistent, otherwise returns
     * `false`.
     *
     * @return `true` if this contact is persistent, otherwise returns
     * `false`.
     */
    override val isDescriptorPersistent: Boolean
        get() = false

    /**
     * Loads the given chat room in the this chat conference panel. Loads all members and adds all
     * corresponding listeners.
     *
     * @param chatRoom the `ChatRoom` to load
     */
    fun loadChatRoom(chatRoom: AdHocChatRoom) {
        for (contact in chatRoom.getParticipants()!!)  // sessionRenderer.addChatContact(new AdHocConferenceChatContact(contact));
            chatRoom.addParticipantPresenceListener(this)
    }

    /**
     * Implements the `ChatPanel.getChatStatusIcon` method.
     *
     * @return the status icon corresponding to this ad-hoc chat room
     */
    override val chatStatusIcon: ByteArray
        get() {
            var status = GlobalStatusEnum.OFFLINE
            if (chatRoomWrapper.adHocChatRoom != null) status = GlobalStatusEnum.ONLINE
            return status.statusIcon!!
        }

    /**
     * Returns the avatar icon of this chat session.
     *
     * @return the avatar icon of this chat session.
     */
    override val chatAvatar: ByteArray?
        get() = null

    /**
     * Initializes the list of participants.
     */
    private fun initChatParticipants() {
        val chatRoom = chatRoomWrapper.adHocChatRoom
        if (chatRoom != null) {
            for (contact in chatRoom.getParticipants()!!) {
                chatParticipants.add(AdHocConferenceChatContact(contact))
            }
        }
    }

    /* Implements ChatSession#isContactListSupported(). */
    override val isContactListSupported: Boolean
        get() = true

    /**
     * Invoked when `AdHocChatRoomParticipantPresenceChangeEvent` are received. When a new
     * participant (`Contact`) has joined the chat adds it to the list of chat
     * participants on the right of the chat window. When a participant has left or quit it's
     * removed from the chat window.
     */
    override fun participantPresenceChanged(evt: AdHocChatRoomParticipantPresenceChangeEvent) {
        val sourceChatRoom = evt.getAdHocChatRoom()
        if (sourceChatRoom != chatRoomWrapper.adHocChatRoom) return
        val eventType = evt.getEventType()
        val participant = evt.getParticipant()
        var statusMessage: String? = null
        if (eventType == AdHocChatRoomParticipantPresenceChangeEvent.CONTACT_JOINED) {
            val chatContact = AdHocConferenceChatContact(participant)
            chatParticipants.add(chatContact)

            /*
             * When the whole list of members of a given chat room is reported, it doesn't make
             * sense to see "ChatContact has joined #ChatRoom" for all of them one after the
             * other. Such an event occurs not because the ChatContact has joined after us but
             * rather she was there before us.
             */
            if (!evt.isReasonUserList()) {
                statusMessage = aTalkApp.getResString(
                        R.string.service_gui_CHATROOM_USER_JOINED, sourceChatRoom.getName())
                sessionRenderer.updateChatContactStatus(chatContact, statusMessage)
            }
        } else if (eventType == AdHocChatRoomParticipantPresenceChangeEvent.CONTACT_LEFT || eventType == AdHocChatRoomParticipantPresenceChangeEvent.CONTACT_QUIT) {
            if (eventType == AdHocChatRoomParticipantPresenceChangeEvent.CONTACT_LEFT) {
                statusMessage = aTalkApp.getResString(
                        R.string.service_gui_CHATROOM_USER_LEFT, sourceChatRoom.getName())
            } else if (eventType == AdHocChatRoomParticipantPresenceChangeEvent.CONTACT_QUIT) {
                statusMessage = aTalkApp.getResString(
                        R.string.service_gui_CHATROOM_USER_QUIT, sourceChatRoom.getName())
            }
            for (chatContact in chatParticipants) {
                if (chatContact.descriptor!! == participant) {
                    sessionRenderer.updateChatContactStatus(chatContact, statusMessage!!)
                    break
                }
            }
        }
    }

//    /**
//     * {@inheritDoc}
//     *
//     *
//     * Not implemented.
//     */
//    override fun addChatTransportChangeListener(l: ChatSessionChangeListener) {}
//
//    /**
//     * {@inheritDoc}
//     *
//     *
//     * Not implemented.
//     */
//    override fun removeChatTransportChangeListener(l: ChatSessionChangeListener) {}
}