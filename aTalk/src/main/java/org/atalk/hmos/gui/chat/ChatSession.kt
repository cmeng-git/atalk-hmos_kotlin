/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.hmos.gui.chat

import net.java.sip.communicator.service.msghistory.MessageHistoryService
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.OperationSet
import org.jivesoftware.smackx.muc.MultiUserChat
import org.json.JSONObject
import java.util.*

/**
 * @author Yana Stamcheva
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
abstract class ChatSession {
    // The last access date to the server mam records
    var mamDate: Date? = null
    private val attributes = JSONObject()
    val messages = ArrayList<ChatMessageImpl>()
    private val accountId: AccountID? = null
    private val nextMessage: String? = null

    @Transient
    private val mucOptions: MultiUserChat? = null
    /**
     * Returns the persistable address of the contact from the session.
     *
     * @return the persistable address.
     */
    /**
     * The persistable address of the contact from the session.
     */
    var persistableAddress: String? = null
        protected set

    /**
     * The list of `ChatContact`s contained in this chat session.
     */
    protected val chatParticipants = ArrayList<ChatContact<*>>()

    /**
     * The list of `ChatTransport`s available in this session.
     */
    protected val chatTransports = LinkedList<ChatTransport>()

    /**
     * The list of all `ChatSessionChangeListener` registered to listen for transport modifications.
     */
    private val chatTransportChangeListeners = ArrayList<ChatSessionChangeListener>()

    /**
     * Returns the descriptor of this chat session.
     *
     * @return the descriptor of this chat session i.e. MetaContact or ChatRoomWrapper
     */
    abstract val descriptor: Any?

    /**
     * Returns the chat identifier i.e. SessionUuid in DB; uniquely identify this chat session.
     * The mSessionUuid is linked to all the chatMessages of this chatSession in the database
     *
     * @return the chat identifier i.e. SessionUuid of the chat
     */
    abstract val chatId: String

    /**
     * Returns `true` if this chat session descriptor is persistent, otherwise returns `false`.
     *
     * @return `true` if this chat session descriptor is persistent, otherwise returns `false`.
     */
    abstract val isDescriptorPersistent: Boolean

    /**
     * Returns an iterator to the list of all participants contained in this chat session.
     *
     * @return an iterator to the list of all participants contained in this chat session.
     */
    val participants: Iterator<Any>
        get() = chatParticipants.iterator()

    /**
     * Returns all available chat transports for this chat session. Each chat transport is
     * corresponding to a protocol provider.
     *
     * @return all available chat transports for this chat session.
     */
    fun getChatTransports(): Iterator<ChatTransport> {
        return chatTransports.iterator()
    }
    /**
     * Returns the currently used transport for all operation within this chat session.
     *
     * @return the currently used transport for all operation within this chat session.
     */
    /**
     * Sets the transport that will be used for all operations within this chat session.
     *
     * chatTransport The transport to set as a default transport for this session.
     */
    abstract var currentChatTransport: ChatTransport?

    /**
     * Returns a list of all `ChatTransport`s contained in this session supporting the given `opSetClass`.
     *
     * opSetClass the `OperationSet` class we're looking for
     * @return a list of all `ChatTransport`s contained in this session supporting the given `opSetClass`
     */
    fun getTransportsForOperationSet(opSetClass: Class<out OperationSet?>): List<ChatTransport> {
        val opSetTransports = LinkedList<ChatTransport>()
        for (transport in chatTransports) {
            if (transport.protocolProvider.getOperationSet(opSetClass) != null) opSetTransports.add(transport)
        }
        return opSetTransports
    }

    /**
     * Returns the `ChatPanel` that provides the connection between this chat session and its UI.
     *
     * @return The `ChatSessionRenderer`.
     */
    abstract val chatSessionRenderer: ChatPanel

    /**
     * Returns the entityBareJid of the chat. If this chat panel corresponds to a single
     * chat it will return the entityBareJid of the `MetaContact`, otherwise it
     * will return the entityBareJid of the chat room.
     *
     * @return the entityBareJid of the chat
     */
    abstract val chatEntity: String

    /**
     * Returns a collection of the last N number of history messages given by count.
     *
     * count The number of messages from history to return.
     * @return a collection of the last N number of messages given by count.
     */
    abstract fun getHistory(count: Int): Collection<Any>?

    /**
     * Returns a collection of the last N number of history messages given by count before the given date.
     *
     * date The date up to which we're looking for messages.
     * count The number of messages from history to return.
     * @return a collection of the last N number of messages given by count.
     */
    abstract fun getHistoryBeforeDate(date: Date, count: Int): Collection<Any>?

    /**
     * Returns a collection of the last N number of history messages given by count after the given date.
     *
     * date The date from which we're looking for messages.
     * count The number of messages from history to return.
     * @return a collection of the last N number of messages given by count.
     */
    abstract fun getHistoryAfterDate(date: Date, count: Int): Collection<Any?>?

    /**
     * Returns the start date of the history of this chat session.
     *
     * @return the start date of the history of this chat session.
     */
    abstract val historyStartDate: Date

    /**
     * Returns the end date of the history of this chat session.
     *
     * @return the end date of the history of this chat session.
     */
    abstract val historyEndDate: Date?
    /**
     * Returns the default mobile number used to send sms-es in this session.
     *
     * @return the default mobile number used to send sms-es in this session.
     */
    /**
     * Sets the default mobile number used to send sms-es in this session.
     *
     * smsPhoneNumber The default mobile number used to send sms-es in this session.
     */
    abstract var defaultSmsNumber: String?

    /**
     * Disposes this chat session.
     */
    abstract fun dispose()

    /**
     * Returns the ChatTransport corresponding to the given descriptor.
     *
     * descriptor The descriptor of the chat transport we're looking for.
     * resourceName The entityBareJid of the resource if any, null otherwise
     * @return The ChatTransport corresponding to the given descriptor.
     */
    fun findChatTransportForDescriptor(descriptor: Any, resourceName: String?): ChatTransport? {
        for (chatTransport in chatTransports) {
            val transportResName = chatTransport.resourceName
            if (chatTransport.descriptor == descriptor && (resourceName == null || transportResName != null && transportResName == resourceName)) return chatTransport
        }
        return null
    }

    /**
     * Returns the status icon of this chat session.
     *
     * @return the status icon of this chat session.
     */
    abstract val chatStatusIcon: ByteArray?

    /**
     * Returns the avatar icon of this chat session.
     *
     * @return the avatar icon of this chat session.
     */
    abstract val chatAvatar: ByteArray?

    /**
     * Gets the indicator which determines whether a contact list of (multiple) participants is
     * supported by this `ChatSession`. For example, UI implementations may use the
     * indicator to determine whether UI elements should be created for the user to represent the
     * contact list of the participants in this `ChatSession`.
     *
     * @return `true` if this `ChatSession` supports a contact list of
     * (multiple) participants; otherwise, `false`
     */
    abstract val isContactListSupported: Boolean

    /**
     * Adds the given [ChatSessionChangeListener] to this `ChatSession`.
     *
     * l the `ChatSessionChangeListener` to add
     */
    fun addChatTransportChangeListener(l: ChatSessionChangeListener) {
        synchronized(chatTransportChangeListeners) { if (!chatTransportChangeListeners.contains(l)) chatTransportChangeListeners.add(l) }
    }

    /**
     * Removes the given [ChatSessionChangeListener] to this `ChatSession`.
     *
     * l the `ChatSessionChangeListener` to add
     */
    fun removeChatTransportChangeListener(l: ChatSessionChangeListener) {
        synchronized(chatTransportChangeListeners) { chatTransportChangeListeners.remove(l) }
    }

    /**
     * Fires a event that current ChatTransport has changed.
     */
    fun fireCurrentChatTransportChange() {
        var listeners: List<ChatSessionChangeListener>
        synchronized(chatTransportChangeListeners) { listeners = ArrayList(chatTransportChangeListeners) }
        for (l in listeners) l.currentChatTransportChanged(this)
    }

    /**
     * Fires a event that current ChatTransport has been updated.
     */
    fun fireCurrentChatTransportUpdated(eventID: Int) {
        var listeners: List<ChatSessionChangeListener>
        synchronized(chatTransportChangeListeners) { listeners = ArrayList(chatTransportChangeListeners) }
        for (l in listeners) l.currentChatTransportUpdated(eventID)
    }

    companion object {
        const val TABLE_NAME = "chatSessions" // chat session
        const val SESSION_UUID = "sessionUuid" // ChatSession Unique Id
        const val ACCOUNT_UUID = "accountUuid"
        const val ACCOUNT_UID = "accountUid" // AccountUID
        const val ENTITY_JID = "entityJid" // entityJid for contact or chatRoom
        const val CREATED = "created" // time stamp
        const val STATUS = "status" // see ChatFragment#chatType (MSGTYPE_)
        const val MODE = "mode" // muc = 1
        const val MAM_DATE = "mamDate" // mam last access date
        const val ATTRIBUTES = "attributes" // see below ATTR_*
        const val ATTR_NEXT_ENCRYPTION = "next_encryption"
        const val ATTR_MUC_PASSWORD = "muc_password"
        const val ATTR_MUTED_TILL = "muted_till"
        const val ATTR_ALWAYS_NOTIFY = "always_notify"
        const val ATTR_CRYPTO_TARGETS = "crypto_targets"
        const val ATTR_LAST_CLEAR_HISTORY = "last_clear_history"
        const val ATTR_AUTO_JOIN = "autoJoin"
        const val ATTR_AUTO_OPEN = "autoOpen" // on-activity

        // public static final String ATTR_STATUS = "lastStatus";
        const val STATUS_AVAILABLE = 0
        const val STATUS_ONLINE = 1
        const val STATUS_ARCHIVED = 2
        const val STATUS_DELETED = 3
        const val MODE_SINGLE = 0
        const val MODE_MULTI = 1
        const val MODE_NPE = 2 // non-persistent entity
        private val chatSession: ChatSession? = null

        /**
         * The chat history filter for retrieving history messages.
         * MessageHistoryService in aTalk includes both the message and file history
         * Note: FileHistoryService.class is now handle within the MessageHistoryService.class
         */
        val chatHistoryFilter = arrayOf(MessageHistoryService::class.java.name)
    }
}