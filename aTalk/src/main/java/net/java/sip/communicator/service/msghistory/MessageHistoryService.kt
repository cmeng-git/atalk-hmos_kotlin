/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.msghistory

import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.msghistory.event.MessageHistorySearchProgressListener
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.Contact
import org.atalk.hmos.gui.chat.ChatSession
import org.atalk.hmos.gui.chat.chatsession.ChatSessionRecord
import java.util.*

/**
 * The Message History Service stores messages exchanged through the various protocols
 *
 * @author Alexander Pelov
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
interface MessageHistoryService {
    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact after the given date
     *
     * @param metaContact MetaContact
     * @param startDate Date the start date of the conversations
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findByStartDate(metaContact: MetaContact, startDate: Date): Collection<EventObject?>

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact before the given date
     *
     * @param metaContact MetaContact
     * @param endDate Date the end date of the conversations
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findByEndDate(metaContact: MetaContact, endDate: Date): Collection<EventObject?>

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact between the given dates
     *
     * @param metaContact MetaContact
     * @param startDate Date the start date of the conversations
     * @param endDate Date the end date of the conversations
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findByPeriod(metaContact: MetaContact, startDate: Date, endDate: Date): Collection<EventObject?>

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact between
     * the given dates and having the given keywords
     *
     * @param metaContact MetaContact
     * @param startDate Date the start date of the conversations
     * @param endDate Date the end date of the conversations
     * @param keywords array of keywords
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findByPeriod(metaContact: MetaContact, startDate: Date, endDate: Date, keywords: Array<String>): Collection<EventObject?>

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact between
     * the given dates and having the given keywords
     *
     * @param metaContact MetaContact
     * @param startDate Date the start date of the conversations
     * @param endDate Date the end date of the conversations
     * @param keywords array of keywords
     * @param caseSensitive is keywords search case sensitive
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findByPeriod(metaContact: MetaContact, startDate: Date, endDate: Date,
            keywords: Array<String>, caseSensitive: Boolean): Collection<EventObject?>

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact having the given keyword
     *
     * @param metaContact MetaContact
     * @param keyword keyword
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findByKeyword(metaContact: MetaContact, keyword: String): Collection<EventObject?>

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact having the given keyword
     *
     * @param metaContact MetaContact
     * @param keyword keyword
     * @param caseSensitive is keywords search case sensitive
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findByKeyword(metaContact: MetaContact, keyword: String, caseSensitive: Boolean): Collection<EventObject?>

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact having the given keywords
     *
     * @param metaContact MetaContact
     * @param keywords keyword
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findByKeywords(metaContact: MetaContact, keywords: Array<String>): Collection<EventObject?>

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact having the given keywords
     *
     * @param metaContact MetaContact
     * @param keywords keyword
     * @param caseSensitive is keywords search case sensitive
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findByKeywords(metaContact: MetaContact, keywords: Array<String>, caseSensitive: Boolean): Collection<EventObject?>

    /**
     * Returns the supplied number of recent messages exchanged by all the contacts in the supplied metaContact
     *
     * @param metaContact MetaContact
     * @param count messages count
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findLast(metaContact: MetaContact, count: Int): Collection<EventObject?>

    /**
     * Returns the supplied number of recent messages after the given date exchanged by all the
     * contacts in the supplied metaContact
     *
     * @param metaContact MetaContact
     * @param startDate messages after date
     * @param count messages count
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findFirstMessagesAfter(metaContact: MetaContact, startDate: Date, count: Int): Collection<EventObject?>

    /**
     * Returns the supplied number of recent messages before the given date exchanged by all the
     * contacts in the supplied metaContact
     *
     * @param metaContact MetaContact
     * @param endDate messages before date
     * @param count messages count
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findLastMessagesBefore(metaContact: MetaContact, endDate: Date, count: Int): Collection<EventObject?>

    /**
     * Returns all the chat session record created by the supplied accountUid before the given date
     *
     * @param accountUid Account Uid
     * @param endDate end date for the session creation
     * @return Collection of ChatSessionRecord
     */
    fun findSessionByEndDate(accountUid: String, endDate: Date): Collection<ChatSessionRecord?>

    /**
     * Returns the messages for the recently contacted `count` contacts.
     *
     * @param count contacts count
     * @param providerToFilter can be filtered by provider e.g. Jabber:abc123@atalk.org,
     * or `null` to search for all  providers
     * @param contactToFilter can be filtered by contact e.g. xyx123@atalk.org,
     * or `null` to search for all contacts
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findRecentMessagesPerContact(count: Int, providerToFilter: String,
            contactToFilter: String?, isSMSEnabled: Boolean): Collection<EventObject?>

    /**
     * Adding progress listener for monitoring progress of search process
     *
     * @param listener HistorySearchProgressListener
     */
    fun addSearchProgressListener(listener: MessageHistorySearchProgressListener)

    /**
     * Removing progress listener
     *
     * @param listener HistorySearchProgressListener
     */
    fun removeSearchProgressListener(listener: MessageHistorySearchProgressListener)

    /**
     * Returns all the messages exchanged in the supplied chat room after the given date
     *
     * @param room The chat room
     * @param startDate Date the start date of the conversations
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findByStartDate(room: ChatRoom, startDate: Date): Collection<EventObject?>

    /**
     * Returns all the messages exchanged in the supplied chat room before the given date
     *
     * @param room The chat room
     * @param endDate Date the end date of the conversations
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findByEndDate(room: ChatRoom, endDate: Date): Collection<EventObject?>

    /**
     * Returns all the messages exchanged in the supplied chat room between the given dates
     *
     * @param room The chat room
     * @param startDate Date the start date of the conversations
     * @param endDate Date the end date of the conversations
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findByPeriod(room: ChatRoom, startDate: Date, endDate: Date): Collection<EventObject?>

    /**
     * Returns all the messages exchanged in the supplied chat room between the given dates and
     * having the given keywords
     *
     * @param room The chat room
     * @param startDate Date the start date of the conversations
     * @param endDate Date the end date of the conversations
     * @param keywords array of keywords
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findByPeriod(room: ChatRoom, startDate: Date, endDate: Date, keywords: Array<String>): Collection<EventObject?>

    /**
     * Returns all the messages exchanged in the supplied chat room between the given dates and
     * having the given keywords
     *
     * @param room The chat room
     * @param startDate Date the start date of the conversations
     * @param endDate Date the end date of the conversations
     * @param keywords array of keywords
     * @param caseSensitive is keywords search case sensitive
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findByPeriod(room: ChatRoom, startDate: Date, endDate: Date,
            keywords: Array<String>, caseSensitive: Boolean): Collection<EventObject?>

    /**
     * Returns all the messages exchanged in the supplied room having the given keyword
     *
     * @param room The Chat room
     * @param keyword keyword
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findByKeyword(room: ChatRoom, keyword: String): Collection<EventObject?>

    /**
     * Returns all the messages exchanged in the supplied chat room having the given keyword
     *
     * @param room The chat room
     * @param keyword keyword
     * @param caseSensitive is keywords search case sensitive
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findByKeyword(room: ChatRoom, keyword: String, caseSensitive: Boolean): Collection<EventObject?>

    /**
     * Returns all the messages exchanged in the supplied chat room having the given keywords
     *
     * @param room The chat room
     * @param keywords keyword
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findByKeywords(room: ChatRoom, keywords: Array<String>): Collection<EventObject?>

    /**
     * Returns all the messages exchanged in the supplied chat room having the given keywords
     *
     * @param room The chat room
     * @param keywords keyword
     * @param caseSensitive is keywords search case sensitive
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findByKeywords(room: ChatRoom, keywords: Array<String>, caseSensitive: Boolean): Collection<EventObject?>

    /**
     * Returns the supplied number of recent messages exchanged in the supplied chat room
     *
     * @param room The chat room
     * @param count messages count
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findLast(room: ChatRoom, count: Int): Collection<EventObject?>

    /**
     * Returns the supplied number of recent messages after the given date exchanged in the supplied chat room
     *
     * @param room The chat room
     * @param startDate messages after date
     * @param count messages count
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findFirstMessagesAfter(room: ChatRoom, startDate: Date, count: Int): Collection<EventObject?>

    /**
     * Returns the supplied number of recent messages before the given date exchanged in the supplied chat room
     *
     * @param room The chat room
     * @param endDate messages before date
     * @param count messages count
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    fun findLastMessagesBefore(room: ChatRoom, endDate: Date, count: Int): Collection<EventObject?>

    /**
     * Permanently removes all locally stored message history for the specified chatMode.
     * @param chatMode i.e. ChatSession.MODE_SINGLE or ChatSession.MODE_MULTI
     */
    fun eraseLocallyStoredChatHistory(chatMode: Int)

    /**
     * Permanently removes locally stored message history for the metaContact.
     */
    fun eraseLocallyStoredChatHistory(metaContact: MetaContact, messageUUIDs: List<String>?)

    /**
     * Permanently removes locally stored message history for the chatRoom.
     */
    fun eraseLocallyStoredChatHistory(room: ChatRoom, messageUUIDs: List<String>?)

    /**
     * Fetch the attached file paths for all the messages of the specified descriptor.
     */
    fun getLocallyStoredFilePath(descriptor: Any): List<String>

    /**
     * Fetch the attached file paths for all locally saved messages.
     */
    val locallyStoredFilePath: List<String>

    /**
     * Returns `true` if the "IS_MESSAGE_HISTORY_ENABLED" property is true, otherwise -
     * returns `false`. Indicates to the user interface whether the history logging is enabled.
     *
     * @return `true` if the "IS_MESSAGE_HISTORY_ENABLED" property is true, otherwise -
     * returns `false`.
     */
    val isHistoryLoggingEnabled: Boolean

    /**
     * Updates the "isHistoryLoggingEnabled" property through the `ConfigurationService`.
     *
     * @param isEnabled indicates if the history logging is enabled.
     */

    /**
     * Returns `true` if the "IS_MESSAGE_HISTORY_ENABLED" property is true for the
     * `id`, otherwise - returns `false`. Indicates to the user interface
     * whether the history logging is enabled for the supplied id (id for metaContact or for chat room).
     *
     * @return `true` if the "IS_MESSAGE_HISTORY_ENABLED" property is true for the
     * `id`, otherwise - returns `false`.
     */
    fun isHistoryLoggingEnabled(id: String): Boolean

    /**
     * Updates the "isHistoryLoggingEnabled" property through the `ConfigurationService` for the contact.
     *
     * @param isEnabled indicates if the history logging is enabled for the contact.
     */
    fun setHistoryLoggingEnabled(id: String, isEnabled: Boolean)

    /**
     * Returns the sessionUuid by specified Object
     *
     * @param contact The chat Contact
     * @return sessionUuid - created if not exist
     */
    fun getSessionUuidByJid(contact: Contact): String
    fun getSessionUuidByJid(chatRoom: ChatRoom): String
    fun getSessionUuidByJid(accountID: AccountID, entityJid: String): String

    /**
     * Get the chatSession persistent chatType
     *
     * @param chatSession the chat session for which to fetch from
     * @return the chatType
     */
    fun getSessionChatType(chatSession: ChatSession): Int

    /**
     * Store the chatSession to user selected chatType
     *
     * @param chatSession the chat session for which to apply to
     * @param chatType the chatType to store
     * @return number of columns affected
     */
    fun setSessionChatType(chatSession: ChatSession, chatType: Int): Int

    companion object {
        /**
         * Name of the property that indicates whether the logging of messages is enabled.
         */
        const val PNAME_IS_MESSAGE_HISTORY_ENABLED = "msghistory.IS_MESSAGE_HISTORY_ENABLED"

        /**
         * Name of the property that indicates whether the recent messages is enabled.
         */
        const val PNAME_IS_RECENT_MESSAGES_DISABLED = "msghistory.IS_RECENT_MESSAGES_DISABLED"

        /**
         * Name of the property that indicates whether the logging of messages is enabled.
         */
        const val PNAME_IS_MESSAGE_HISTORY_PER_CONTACT_ENABLED_PREFIX = "msghistory.contact"
    }
}