/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.chat.conference

import android.app.Activity
import android.os.Handler
import android.os.Looper
import net.java.sip.communicator.service.metahistory.MetaHistoryService
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.muc.MUCService
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.ChatRoomConfigurationForm
import net.java.sip.communicator.service.protocol.ChatRoomMember
import net.java.sip.communicator.service.protocol.ConferenceDescription
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.event.ChatRoomConferencePublishedEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomConferencePublishedListener
import net.java.sip.communicator.service.protocol.event.ChatRoomLocalUserRoleListener
import net.java.sip.communicator.service.protocol.event.ChatRoomMemberPresenceChangeEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMemberPresenceListener
import net.java.sip.communicator.service.protocol.event.ChatRoomMemberRoleListener
import net.java.sip.communicator.service.protocol.event.ChatRoomPropertyChangeEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomPropertyChangeFailedEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomPropertyChangeListener
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum
import net.java.sip.communicator.util.ConfigurationUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.chat.ChatContact
import org.atalk.hmos.gui.chat.ChatPanel
import org.atalk.hmos.gui.chat.ChatSession
import org.atalk.hmos.gui.chat.ChatTransport
import java.util.*

/**
 * An implementation of `ChatSession` for conference chatting.
 *
 * @author Yana Stamcheva
 * @author Lubomir Marinov
 * @author Valentin Martinet
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class ConferenceChatSession(chatPanel: ChatPanel, chatRoomWrapper: ChatRoomWrapper) : ChatSession(), ChatRoomMemberPresenceListener, ChatRoomPropertyChangeListener, ChatRoomConferencePublishedListener {
    /**
     * The current chat transport used for messaging.
     */
    override var currentChatTransport: ChatTransport? = null
        /**
         * Sets the transport that will be used for all operations within this chat session.
         */
        set(chatTransport) {
            field = chatTransport
            fireCurrentChatTransportChange()

        }

    /**
     * The chat room wrapper, which is the descriptor of this chat session.
     */
    private val chatRoomWrapper: ChatRoomWrapper

    /**
     * The object used for rendering.
     */
    private val sessionRenderer: ChatPanel

    /**
     * Creates an instance of `ConferenceChatSession`, by specifying the sessionRenderer to
     * be used for communication with the UI and the chatRoom corresponding to this
     * conference session.
     *
     * chatPanel the renderer to be used for communication with the UI.
     * chatRoomWrapper the chat room corresponding to this conference session.
     */
    init {
        sessionRenderer = chatPanel
        this.chatRoomWrapper = chatRoomWrapper
        currentChatTransport = ConferenceChatTransport(this, chatRoomWrapper.chatRoom!!)
        chatTransports.add(currentChatTransport!!)
        synchronized(this.chatParticipants) { initChatParticipants() }
        val chatRoom = chatRoomWrapper.chatRoom!!
        chatRoom.addMemberPresenceListener(this)
        chatRoom.addPropertyChangeListener(this)
        chatRoom.addConferencePublishedListener(this)
    }

    /**
     * Returns the descriptor of this chat session.
     *
     * @return the descriptor i.e. ChatRoomWrapper of this chat session.
     */
    override val descriptor: Any
        get() = chatRoomWrapper

    /**
     * Returns the chat identifier.
     *
     * @return the chat identifier
     */
    override val chatId: String
        get() = chatRoomWrapper.chatRoomID!!

    /**
     * Disposes this chat session.
     */
    override fun dispose() {
        val chatRoom = chatRoomWrapper.chatRoom!!
        chatRoom.removeMemberPresenceListener(this)
        chatRoom.removePropertyChangeListener(this)
        chatRoom.removeConferencePublishedListener(this)
        if (ConfigurationUtils.isLeaveChatRoomOnWindowCloseEnabled) {
            chatRoom.leave()
        }
    }

    /**
     * Returns the entityJid of the chat room.
     *
     * @return the entityJid of the chat room.
     */
    override val chatEntity: String
        get() = chatRoomWrapper.chatRoomName!!

    /**
     * Returns the subject of the chat room.
     *
     * @return the subject of the chat room.
     */
    val chatSubject: String
        get() = chatRoomWrapper.chatRoom!!.getSubject()!!

    /**
     * Returns the configuration form corresponding to the chat room.
     *
     * @return the configuration form corresponding to the chat room.
     * @throws OperationFailedException if no configuration form is available for the chat room.
     */
    @get:Throws(OperationFailedException::class, InterruptedException::class)
    val chatConfigurationForm: ChatRoomConfigurationForm
        get() = chatRoomWrapper.chatRoom!!.getConfigurationForm()!!

    /**
     * Returns the default mobile number used to send sms-es in this session. In the case of
     * conference this is for now null.
     *
     * @return the default mobile number used to send sms-es in this session.
     */
    /**
     * Sets the default mobile number used to send sms-es in this session.
     *
     *smsPhoneNumber The default mobile number used to send sms-es in this session.
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
        return metaHistory.findLast(chatHistoryFilter, chatRoomWrapper.chatRoom!!,
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
        return metaHistory.findLastMessagesBefore(chatHistoryFilter, chatRoomWrapper.chatRoom!!, date, count)
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
        return metaHistory.findFirstMessagesAfter(chatHistoryFilter, chatRoomWrapper.chatRoom!!,
                date, ConfigurationUtils.getChatHistorySize())
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
                    chatRoomWrapper.chatRoom!!, Date(0), 1)
            if (firstMessage.isNotEmpty()) {
                val i = firstMessage.iterator()
                val evt = i.next()
                if (evt is MessageDeliveredEvent) {
                    startHistoryDate = evt.getTimestamp()
                } else if (evt is MessageReceivedEvent) {
                    startHistoryDate = evt.getTimestamp()
                }
            }
            return startHistoryDate
        }

    // If the MetaHistoryService is not registered we have nothing to do here. The history
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
                    chatRoomWrapper.chatRoom!!, Date(Long.MAX_VALUE), 1)
            if (lastMessage.isNotEmpty()) {
                val i1 = lastMessage.iterator()
                val evt = i1.next()
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
     * Invoked when `ChatRoomMemberPresenceChangeEvent` is received. When a new
     * `ChatRoomMember` has joined the chat, adds it to the list of chat participants.
     * When a `ChatRoomMember` has left or quit, or has being kicked it's removed from the
     * chat window.
     *
     * @param evt the `ChatRoomMemberPresenceChangeEvent` that notified us
     */
    override fun memberPresenceChanged(evt: ChatRoomMemberPresenceChangeEvent) {
        Handler(Looper.getMainLooper()).post(Runnable {
            val sourceChatRoom = evt.getChatRoom()
            val room = chatRoomWrapper.chatRoom
            // return if this is not for this chatRoomWrapper session
            if (sourceChatRoom != room) return@Runnable

            val eventType = evt.getEventType()
            val chatRoomMember = evt.getChatRoomMember()
            // Timber.d("ChatRoom member: %s (%s)", chatRoomMember.getContactAddress(), chatRoomMember.getNickName());
            var statusMessage: String? = null
            if (eventType == ChatRoomMemberPresenceChangeEvent.MEMBER_JOINED) {
                val chatContact = ConferenceChatContact(chatRoomMember)

                // Check if not ever present in the chat room. In some cases, the considered
                // chatRoom member may appear twice in the chat contact list panel.
                synchronized(chatParticipants) { if (!chatParticipants.contains(chatContact)) chatParticipants.add(chatContact) }
                // cmeng: seems to do nothing for private contact presence in chatRoom
                room.updatePrivateContactPresenceStatus(chatRoomMember)

                /*
                 * When the whole list of members of a given chat room is reported, it doesn't
                 * make sense to see "ChatContact has joined #ChatRoom" for all of them one
                 * after the other. Such an event occurs not because the ChatContact has
                 * joined after us but rather she was there before us.
                 */
                if (!evt.isReasonUserList()) {
                    statusMessage = aTalkApp.getResString(
                            R.string.service_gui_CHATROOM_USER_JOINED, sourceChatRoom.getName())
                    sessionRenderer.updateChatContactStatus(chatContact, statusMessage)
                }
            } else if (eventType == ChatRoomMemberPresenceChangeEvent.MEMBER_LEFT || eventType == ChatRoomMemberPresenceChangeEvent.MEMBER_KICKED || eventType == ChatRoomMemberPresenceChangeEvent.MEMBER_QUIT) {
                when (eventType) {
                    ChatRoomMemberPresenceChangeEvent.MEMBER_LEFT -> statusMessage = aTalkApp.getResString(
                            R.string.service_gui_CHATROOM_USER_LEFT, sourceChatRoom.getName())
                    ChatRoomMemberPresenceChangeEvent.MEMBER_KICKED -> statusMessage = aTalkApp.getResString(
                            R.string.service_gui_CHATROOM_USER_KICKED, sourceChatRoom.getName())
                    ChatRoomMemberPresenceChangeEvent.MEMBER_QUIT -> statusMessage = aTalkApp.getResString(
                            R.string.service_gui_CHATROOM_USER_QUIT, sourceChatRoom.getName())
                }
                var contact: ChatContact<*>? = null
                for (chatContact in chatParticipants) {
                    if (chatContact.descriptor!! == chatRoomMember) {
                        room.updatePrivateContactPresenceStatus(chatRoomMember)
                        contact = chatContact
                        break
                    }
                }

                // If contact found, remove from chat participants.
                if (contact != null) {
                    synchronized(chatParticipants) {
                        chatParticipants.remove(contact)
                        sessionRenderer.updateChatContactStatus(contact, statusMessage!!)
                    }
                }
            }
        })
    }

    override fun chatRoomPropertyChangeFailed(event: ChatRoomPropertyChangeFailedEvent) {}

    /**
     * Updates the chat panel when a property of the chat room has been modified.
     *
     * @param event the event containing information about the property change
     */
    override fun chatRoomPropertyChanged(event: ChatRoomPropertyChangeEvent) {
        if (event.propertyName.equals(ChatRoomPropertyChangeEvent.CHAT_ROOM_SUBJECT)) {
            sessionRenderer.setChatSubject(event.newValue as String, event.oldValue as String)
        }
    }

    /**
     * Returns `true` if this contact is persistent, otherwise returns
     * `false`.
     *
     * @return `true` if this contact is persistent, otherwise returns
     * `false`.
     */
    override val isDescriptorPersistent: Boolean
        get() = true

    /**
     * Loads the given chat room in the this chat conference panel. Loads all members and adds all
     * corresponding listeners.
     *
     * @param chatRoom the `ChatRoom` to load
     */
    fun loadChatRoom(chatRoom: ChatRoom) {
        // Re-init the chat transport, as we have a new chat room object.
        currentChatTransport = ConferenceChatTransport(this, chatRoomWrapper.chatRoom!!)
        chatTransports.clear()
        chatTransports.add(currentChatTransport!!)
        synchronized(this.chatParticipants) {

            // Remove all existing contacts.
            this.chatParticipants.clear()
            // Add the new list of members.
            for (member in chatRoom.getMembers()) {
                val contact = ConferenceChatContact(member)
                chatParticipants.add(contact)
            }
        }
        // Add all listeners to the new chat room.
        chatRoom.addPropertyChangeListener(this)
        chatRoom.addMemberPresenceListener(this)

        // Load the subject of the chat room.
        sessionRenderer.setChatSubject(chatRoom.getSubject(), null)
    }

    /**
     * Implements the `ChatPanel.getChatStatusIcon` method.
     *
     * @return the status icon corresponding to this chat room
     */
    override val chatStatusIcon: ByteArray
        get() {
            var status = GlobalStatusEnum.OFFLINE
            if (chatRoomWrapper.chatRoom != null && chatRoomWrapper.chatRoom!!.isJoined()) status = GlobalStatusEnum.ONLINE
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
     * Initializes the list of participants.(It is assumed that `chatParticipants` is
     * locked.)
     */
    private fun initChatParticipants() {
        val chatRoom = chatRoomWrapper.chatRoom
        if (chatRoom != null && chatRoom.isJoined()) {
            for (member in chatRoom.getMembers()) chatParticipants.add(ConferenceChatContact(member))
        }
    }

    /**
     * Indicates if the contact list is supported by this session. The contact list would be
     * supported for all non system and non private sessions.
     *
     * @return `true` to indicate that the contact list is supported, `false` otherwise.
     */
    override val isContactListSupported: Boolean
        get() {
            val chatRoom = chatRoomWrapper.chatRoom!!
            return !chatRoom.isSystem() && !MUCService.isPrivate(chatRoom)
        }

    /**
     * Adds the given `ChatRoomMemberRoleListener` to the contained chat room role listeners.
     *
     * @param l the listener to add
     */
    fun addMemberRoleListener(l: ChatRoomMemberRoleListener) {
        chatRoomWrapper.chatRoom!!.addMemberRoleListener(l)
    }

    /**
     * Adds the given `ChatRoomLocalUserRoleListener` to the contained chat room role listeners.
     *
     * @param l the listener to add
     */
    fun addLocalUserRoleListener(l: ChatRoomLocalUserRoleListener) {
        chatRoomWrapper.chatRoom!!.addLocalUserRoleListener(l)
    }

    /**
     * Removes the given `ChatRoomMemberRoleListener` from the contained chat room role listeners.
     *
     * @param l the listener to remove
     */
    fun removeMemberRoleListener(l: ChatRoomMemberRoleListener) {
        chatRoomWrapper.chatRoom!!.removeMemberRoleListener(l)
    }

    /**
     * Removes the given `ChatRoomLocalUserRoleListener` from the contained chat room role listeners.
     *
     * @param l the listener to remove
     */
    fun removeLocalUserRoleListener(l: ChatRoomLocalUserRoleListener) {
        chatRoomWrapper.chatRoom!!.removeLocalUserRoleListener(l)
    }

    /**
     * Acts upon a `ChatRoomConferencePublishedEvent`, dispatched when a member of a chat
     * room publishes a `ConferenceDescription`.
     *
     * @param evt the event received, which contains the `ChatRoom`, `ChatRoomMember` and
     * `ConferenceDescription` involved.
     */
    override fun conferencePublished(evt: ChatRoomConferencePublishedEvent) {
        Activity().runOnUiThread(Runnable {
            val room = evt.getChatRoom()
            if (room != chatRoomWrapper.chatRoom) return@Runnable
            val cd = evt.getConferenceDescription()
            if (evt.getType() == ChatRoomConferencePublishedEvent.CONFERENCE_DESCRIPTION_SENT) {
                // sessionRenderer.chatConferenceDescriptionSent(cd);
            } else if (evt.getType() == ChatRoomConferencePublishedEvent.CONFERENCE_DESCRIPTION_RECEIVED) {
                updateChatConferences(room, evt.getMember(), cd, room.getCachedConferenceDescriptionSize())
            }
        })
    }

    /**
     * Adds/Removes the announced conference to the interface.
     *
     * @param chatRoom the chat room where the conference is announced.
     * @param chatRoomMember the chat room member who announced the conference.
     * @param cd the `ConferenceDescription` instance which represents the conference.
     */
    private fun updateChatConferences(
            chatRoom: ChatRoom, chatRoomMember: ChatRoomMember,
            cd: ConferenceDescription, activeConferencesCount: Int,
    ) {
        val isAvailable = cd.isAvailable()
        for (chatContact in chatParticipants) {
            if (chatContact.descriptor!!.equals(chatRoomMember)) {
                /*
                 * TODO: we want more things to happen, e.g. the ConferenceDescription being added to a list in the GUI
                 * TODO: i13ze the string, if we decide to keep it at all
                 */
                sessionRenderer.updateChatContactStatus(chatContact,
                        (if (isAvailable) "published" else "removed") + " a conference " + cd)
                break
            }
        }
        if (isAvailable) {
            // sessionRenderer.addChatConferenceCall(cd);
            if (activeConferencesCount == 1) {
                // sessionRenderer.setConferencesPanelVisible(true);
            }
        } else {
            // sessionRenderer.removeChatConferenceCall(cd);
            if (activeConferencesCount == 0) {
                // sessionRenderer.setConferencesPanelVisible(false);
            }
        }
    }
}