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
package net.java.sip.communicator.impl.msghistory

import android.content.ContentValues
import android.database.Cursor
import android.text.TextUtils
import net.java.sip.communicator.impl.protocol.jabber.ChatRoomJabberImpl
import net.java.sip.communicator.impl.protocol.jabber.ChatRoomMemberJabberImpl
import net.java.sip.communicator.impl.protocol.jabber.MessageJabberImpl
import net.java.sip.communicator.impl.protocol.jabber.OperationSetPersistentPresenceJabberImpl
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.filehistory.FileRecord
import net.java.sip.communicator.service.history.HistoryReader
import net.java.sip.communicator.service.history.HistoryService
import net.java.sip.communicator.service.history.event.HistorySearchProgressListener
import net.java.sip.communicator.service.history.event.ProgressEvent
import net.java.sip.communicator.service.msghistory.MessageHistoryAdvancedService
import net.java.sip.communicator.service.msghistory.MessageHistoryService
import net.java.sip.communicator.service.msghistory.event.MessageHistorySearchProgressListener
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.protocol.AbstractMessage
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.AdHocChatRoom
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.ChatRoomMember
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.IMessage
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging
import net.java.sip.communicator.service.protocol.OperationSetContactCapabilities
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresence
import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.OperationSetSmsMessaging
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageDeliveryFailedEvent
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageListener
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageReceivedEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveryFailedEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageListener
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageReceivedEvent
import net.java.sip.communicator.service.protocol.event.LocalUserAdHocChatRoomPresenceChangeEvent
import net.java.sip.communicator.service.protocol.event.LocalUserAdHocChatRoomPresenceListener
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceChangeEvent
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceListener
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.MessageDeliveryFailedEvent
import net.java.sip.communicator.service.protocol.event.MessageListener
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent
import net.java.sip.communicator.util.UtilActivator
import net.java.sip.communicator.util.account.AccountUtils
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.ChatFragment
import org.atalk.hmos.gui.chat.ChatMessage
import org.atalk.hmos.gui.chat.ChatMessageImpl
import org.atalk.hmos.gui.chat.ChatPanel
import org.atalk.hmos.gui.chat.ChatSession
import org.atalk.hmos.gui.chat.chatsession.ChatSessionFragment
import org.atalk.hmos.gui.chat.chatsession.ChatSessionRecord
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.persistance.DatabaseBackend
import org.atalk.service.configuration.ConfigurationService
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smackx.forward.packet.Forwarded
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.element.OmemoElement
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException
import org.jivesoftware.smackx.omemo.exceptions.CryptoFailedException
import org.jivesoftware.smackx.omemo.exceptions.NoRawSessionException
import org.jivesoftware.smackx.omemo.util.OmemoConstants
import org.jivesoftware.smackx.receipts.ReceiptReceivedListener
import org.jivesoftware.smackx.sid.element.StanzaIdElement
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart
import org.jxmpp.stringprep.XmppStringprepException
import org.jxmpp.util.XmppStringUtils
import org.osgi.framework.BundleContext
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceRegistration
import timber.log.Timber
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.math.abs


/**
 * The Message History Service stores messages exchanged through the various protocols Logs
 * messages for all protocol providers that support basic instant messaging (i.e. those that
 * implement OperationSetBasicInstantMessaging).
 *
 * @author Alexander Pelov
 * @author Damian Minkov
 * @author Lubomir Marinov
 * @author Valentin Martinet
 * @author Eng Chong Meng
 */
class MessageHistoryServiceImpl : MessageHistoryService, MessageHistoryAdvancedService, MessageListener, ChatRoomMessageListener, AdHocChatRoomMessageListener, ServiceListener, LocalUserChatRoomPresenceListener, LocalUserAdHocChatRoomPresenceListener, ReceiptReceivedListener {
    /**
     * The BundleContext that we got from the OSGI bus.
     */
    private var bundleContext: BundleContext? = null
    private var historyService: HistoryService? = null
    private val syncRootHistoryService = Any()
    private val progressListeners = Hashtable<MessageHistorySearchProgressListener?, HistorySearchProgressListener>()
    private var configService: ConfigurationService? = null
    private var msgHistoryPropListener: MessageHistoryPropertyChangeListener? = null

    /**
     * The message source service, can be null if not enabled.
     */
    private var messageSourceService: MessageSourceService? = null

    /**
     * The message source service registration.
     */
    private var messageSourceServiceReg: ServiceRegistration<*>? = null
    private val mDB = DatabaseBackend.writableDB
    private val contentValues = ContentValues()

    /**
     * Starts the service. Check the current registered protocol providers which supports
     * BasicIM and adds message listener to them
     *
     * bc BundleContext
     */
    fun start(bc: BundleContext?) {
        bundleContext = bc
        val refConfig = bundleContext!!.getServiceReference(ConfigurationService::class.java.name)
        configService = bundleContext!!.getService(refConfig) as ConfigurationService

        // Check if the message history is enabled in the configuration service;
        // if not, then do not register the service.
        val isMessageHistoryEnabled = configService!!.getBoolean(
                MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED,
                java.lang.Boolean.parseBoolean(MessageHistoryActivator.resources!!.getSettingsString(
                        MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED)))

        // Load the "IS_MESSAGE_HISTORY_ENABLED" property.
        Companion.isHistoryLoggingEnabled = configService!!.getBoolean(
                MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED,
                java.lang.Boolean.parseBoolean(UtilActivator.resources.getSettingsString(
                        MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED)))

        // We are adding a property change listener in order to
        // listen for modifications of the isMessageHistoryEnabled property.
        msgHistoryPropListener = MessageHistoryPropertyChangeListener()
        configService!!.addPropertyChangeListener(
                MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED, msgHistoryPropListener!!)
        if (isMessageHistoryEnabled) {
            Timber.d("Starting the msg history implementation.")
            loadMessageHistoryService()
        }
    }

    /**
     * Stops the service.
     *
     * bc BundleContext
     */
    fun stop(bc: BundleContext?) {
        if (configService != null) configService!!.removePropertyChangeListener(msgHistoryPropListener!!)
        stopMessageHistoryService()
    }

    /**
     * When new protocol provider is registered or removed; check and add/remove the listener if
     * has BasicIM support
     *
     * serviceEvent ServiceEvent received
     */
    override fun serviceChanged(serviceEvent: ServiceEvent) {
        val sService = bundleContext!!.getService(serviceEvent.serviceReference)
        Timber.log(TimberLog.FINER, "Received a service event for: %s", sService.javaClass.name)

        // we don't care if the source service is not a protocol provider
        if (sService !is ProtocolProviderService) {
            return
        }
        if (serviceEvent.type == ServiceEvent.REGISTERED) {
            Timber.d("Handling registration of a new Protocol Provider.")
            handleProviderAdded(sService)
        } else if (serviceEvent.type == ServiceEvent.UNREGISTERING) {
            handleProviderRemoved(sService)
        }
    }

    /**
     * Used to attach the Message History Service to existing or just registered protocol
     * provider. Checks if the provider has implementation of OperationSetBasicInstantMessaging
     *
     * provider ProtocolProviderService
     */
    private fun handleProviderAdded(provider: ProtocolProviderService) {
        Timber.d("Adding protocol provider %s", provider.protocolDisplayName)

        // check whether the provider has a basic im operation set
        val opSetIm = provider.getOperationSet(OperationSetBasicInstantMessaging::class.java)
        if (opSetIm != null) {
            opSetIm.addMessageListener(this)
            if (messageSourceService != null) opSetIm.addMessageListener(messageSourceService!!)
        } else {
            Timber.log(TimberLog.FINER, "Service did not have OperationSet BasicInstantMessaging.")
        }
        val opSetSMS = provider.getOperationSet(OperationSetSmsMessaging::class.java)
        if (opSetSMS != null) {
            opSetSMS.addMessageListener(this)
            if (messageSourceService != null) opSetSMS.addMessageListener(messageSourceService!!)
        } else {
            Timber.log(TimberLog.FINER, "Service did not have OperationSet SmsMessaging.")
        }
        val opSetMultiUChat = provider.getOperationSet(OperationSetMultiUserChat::class.java)
        if (opSetMultiUChat != null) {
            for (room in opSetMultiUChat.getCurrentlyJoinedChatRooms()!!) {
                room!!.addMessageListener(this)
            }
            opSetMultiUChat.addPresenceListener(this)
            if (messageSourceService != null) opSetMultiUChat.addPresenceListener(messageSourceService!!)
        } else {
            Timber.log(TimberLog.FINER, "Service did not have OperationSet MultiUserChat.")
        }
        if (messageSourceService != null) {
            val opSetPresence = provider.getOperationSet(OperationSetPresence::class.java)
            if (opSetPresence != null) {
                opSetPresence.addContactPresenceStatusListener(messageSourceService!!)
                opSetPresence.addProviderPresenceStatusListener(messageSourceService!!)
                opSetPresence.addSubscriptionListener(messageSourceService!!)
            }

            /* cmeng - too earlier to trigger and not ready??? messageSourceService has its own
             * ProviderPresenceStatusListener#providerStatusChanged listener to take care.
             * Need to be registered and connected for retrieving muc recent messages
             */
            // messageSourceService.handleProviderAdded(provider, false);
            provider.getOperationSet(OperationSetContactCapabilities::class.java)?.addContactCapabilitiesListener(messageSourceService)
        }
    }

    /**
     * Removes the specified provider from the list of currently known providers and ignores all
     * the messages exchanged by it
     *
     * provider the ProtocolProviderService that has been unregistered.
     */
    private fun handleProviderRemoved(provider: ProtocolProviderService) {
        val opSetIm = provider.getOperationSet(OperationSetBasicInstantMessaging::class.java)
        if (opSetIm != null) {
            opSetIm.removeMessageListener(this)
            if (messageSourceService != null) opSetIm.removeMessageListener(messageSourceService!!)
        }
        val opSetSMS = provider.getOperationSet(OperationSetSmsMessaging::class.java)
        if (opSetSMS != null) {
            opSetSMS.removeMessageListener(this)
            if (messageSourceService != null) opSetSMS.removeMessageListener(messageSourceService!!)
        }
        val opSetMultiUChat = provider.getOperationSet(OperationSetMultiUserChat::class.java)
        if (opSetMultiUChat != null) {
            for (room in opSetMultiUChat.getCurrentlyJoinedChatRooms()!!) {
                room!!.removeMessageListener(this)
            }
            opSetMultiUChat.removePresenceListener(this)
            if (messageSourceService != null) opSetMultiUChat.removePresenceListener(messageSourceService!!)
        }
        if (messageSourceService != null) {
            val opSetPresence = provider.getOperationSet(OperationSetPresence::class.java)
            if (opSetPresence != null) {
                opSetPresence.removeContactPresenceStatusListener(messageSourceService!!)
                opSetPresence.removeProviderPresenceStatusListener(messageSourceService!!)
                opSetPresence.removeSubscriptionListener(messageSourceService!!)
            }
            messageSourceService!!.handleProviderRemoved(provider)
            provider.getOperationSet(OperationSetContactCapabilities::class.java)?.removeContactCapabilitiesListener(messageSourceService)
        }
    }

    /**
     * Set the configuration service.
     *
     * historyService HistoryService
     */
    fun setHistoryService(historyService: HistoryService?) {
        synchronized(syncRootHistoryService) {
            this.historyService = historyService
            Timber.d("New history service registered.")
        }
    }

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact
     * on and after the given date
     *
     * metaContact MetaContact
     * startDate Date the start date of the conversations
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findByStartDate(metaContact: MetaContact, startDate: Date): Collection<EventObject?> {
        val result = HashSet<EventObject?>()
        val startTimeStamp = startDate.time.toString()
        val contact = metaContact.getDefaultContact()!!
        val sessionUuid = getSessionUuidByJid(contact)
        val cursor: Cursor
        val args = arrayOf(sessionUuid, startTimeStamp)
        cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + ">=?",
                args, null, null, ORDER_ASC)
        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, contact))
        }
        return result
    }

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact before
     * the given date
     *
     * metaContact MetaContact
     * endDate Date the end date of the conversations
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findByEndDate(metaContact: MetaContact, endDate: Date): Collection<EventObject?> {
        val result = HashSet<EventObject?>()
        val endTimeStamp = endDate.time.toString()
        val contact = metaContact.getDefaultContact()!!
        val sessionUuid = getSessionUuidByJid(contact)
        val cursor: Cursor
        val args = arrayOf(sessionUuid, endTimeStamp)
        cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + "<?",
                args, null, null, ORDER_ASC)
        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, contact))
        }
        return result
    }

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact between
     * the given dates inclusive of startDate
     *
     * metaContact MetaContact
     * startDate Date the start date of the conversations
     * endDate Date the end date of the conversations
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findByPeriod(metaContact: MetaContact, startDate: Date, endDate: Date): Collection<EventObject?> {
        val result = HashSet<EventObject?>()
        val startTimeStamp = startDate.time.toString()
        val endTimeStamp = endDate.time.toString()
        val contact = metaContact.getDefaultContact()!!
        val sessionUuid = getSessionUuidByJid(contact)
        val cursor: Cursor
        val args = arrayOf(sessionUuid, startTimeStamp, endTimeStamp)
        cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + ">=? AND "
                        + ChatMessage.TIME_STAMP + "<?", args, null, null, ORDER_ASC)
        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, contact))
        }
        return result
    }

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact between
     * the given dates inclusive of startDate and having the given keywords
     *
     * metaContact MetaContact
     * startDate Date the start date of the conversations
     * endDate Date the end date of the conversations
     * keywords array of keywords
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findByPeriod(metaContact: MetaContact, startDate: Date, endDate: Date, keywords: Array<String>): Collection<EventObject?> {
        return findByPeriod(metaContact, startDate, endDate, keywords, false)
    }

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact having
     * the given keyword
     *
     * metaContact MetaContact
     * keyword keyword
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findByKeyword(metaContact: MetaContact, keyword: String): Collection<EventObject?> {
        return findByKeyword(metaContact, keyword, false)
    }

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact having
     * the given keywords
     *
     * metaContact MetaContact
     * keywords keyword
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findByKeywords(metaContact: MetaContact, keywords: Array<String>): Collection<EventObject?> {
        return findByKeywords(metaContact, keywords, false)
    }

    /**
     * Returns the supplied number of recent messages exchanged by all the contacts in the
     * supplied metaContact
     *
     * metaContact MetaContact
     * count messages count
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findLast(metaContact: MetaContact, count: Int): Collection<EventObject?> {
        val result = LinkedList<EventObject?>()
        val contacts = metaContact.getContacts()
        while (contacts.hasNext()) {
            val contact = contacts.next()!!
            val sessionUuid = getSessionUuidByJid(contact)
            var cursor: Cursor
            val args = arrayOf(sessionUuid)
            cursor = mDB.query(ChatMessage.TABLE_NAME, null, ChatMessage.SESSION_UUID
                    + "=?", args, null, null, ORDER_DESC, count.toString())
            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToMessageEvent(cursor, contact))
            }
        }
        Collections.sort(result, MessageEventComparator())
        return result
    }

    /**
     * Returns the supplied number of recent messages on or after the given date exchanged by all
     * the contacts in the supplied metaContact
     *
     * metaContact MetaContact
     * startDate messages after date
     * count messages count
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findFirstMessagesAfter(metaContact: MetaContact, startDate: Date, count: Int): Collection<EventObject?> {
        val result = LinkedList<EventObject?>()
        val startTimeStamp = startDate.time.toString()
        val contacts = metaContact.getContacts()
        while (contacts.hasNext()) {
            val contact = contacts.next()!!
            val sessionUuid = getSessionUuidByJid(contact)
            val args = arrayOf(sessionUuid, startTimeStamp)
            val cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                    ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + ">=?",
                    args, null, null, ORDER_ASC, count.toString())
            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToMessageEvent(cursor, contact))
            }
        }
        Collections.sort(result, MessageEventComparator())
        return result
    }

    /**
     * Returns the supplied number of recent messages before the given date exchanged by all the
     * contacts in the supplied metaContact
     *
     * metaContact MetaContact
     * endDate messages before date
     * count messages count
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findLastMessagesBefore(metaContact: MetaContact, endDate: Date, count: Int): Collection<EventObject?> {
        val result = LinkedList<EventObject?>()
        val endTimeStamp = endDate.time.toString()

        // cmeng - metaUid is also the sessionUid for metaChatSession
        // String sessionUuid = metaContact.getMetaUID();
        val contacts = metaContact.getContacts()
        while (contacts.hasNext()) {
            val contact = contacts.next()!!
            val sessionUuid = getSessionUuidByJid(contact)
            var cursor: Cursor
            val args = arrayOf(sessionUuid, endTimeStamp)
            cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                    ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + "<?",
                    args, null, null, ORDER_DESC, count.toString())
            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToMessageEvent(cursor, contact))
            }
        }
        Collections.sort(result, MessageEventComparator())
        return result
    }
    // ============== ChatSessionFragment utilities ======================
    /**
     * Returns all the chat session record created by the supplied accountUid before the given date
     *
     * accountUid Account Uid
     * endDate end date for the session creation
     *
     * @return Collection of ChatSessionRecord
     */
    override fun findSessionByEndDate(accountUid: String, endDate: Date): Collection<ChatSessionRecord> {
        val result = ArrayList<ChatSessionRecord>()
        val endTimeStamp = endDate.time.toString()
        val args = arrayOf(accountUid, endTimeStamp)
        val cursor = mDB.query(ChatSession.TABLE_NAME, null,
                ChatSession.ACCOUNT_UID + "=? AND " + ChatSession.CREATED + "<?",
                args, null, null, ChatSession.MODE + ", " + ChatSession.ENTITY_JID + " ASC")
        while (cursor.moveToNext()) {
            val csRecord = convertToSessionRecord(cursor)
            if (csRecord != null) {
                result.add(csRecord)
            }
        }
        return result
    }

    /**
     * convert ChatSession Table rows to ChatSessionRecord for UI display
     * bit-7 of ChatSession.STATUS if set, then remove the record from UI; i.e. just return null
     *
     * cursor HistoryRecord in cursor
     *
     * @return Object ChatSessionRecord
     */
    private fun convertToSessionRecord(cursor: Cursor): ChatSessionRecord? {
        val mProperties = Hashtable<String, String>()
        for (i in 0 until cursor.columnCount) {
            val value = if (cursor.getString(i) == null) "" else cursor.getString(i)
            mProperties[cursor.getColumnName(i)] = value
        }
        return try {
            val entityJBareid = JidCreate.entityBareFrom(mProperties[ChatSession.ENTITY_JID])
            val chatType = Objects.requireNonNull<String?>(mProperties[ChatSession.STATUS]).toInt()
            if (chatType and ChatSessionFragment.SESSION_HIDDEN != 0) return null
            val chatMode = Objects.requireNonNull<String?>(mProperties[ChatSession.MODE]).toInt()
            val date = Date(Objects.requireNonNull<String?>(mProperties[ChatSession.CREATED]).toLong())
            val mamDate = Date(Objects.requireNonNull<String?>(mProperties[ChatSession.MAM_DATE]).toLong())
            ChatSessionRecord(
                    mProperties[ChatSession.SESSION_UUID]!!,
                    mProperties[ChatSession.ACCOUNT_UID]!!,
                    entityJBareid, chatMode, chatType, date, mamDate)
        } catch (e: XmppStringprepException) {
            null
        }
    }

    /**
     * Get and return the last message for the specified sessionUuid
     *
     * sessionUuid the chatSessionUuid in ChatMessage.TABLE_NAME
     *
     * @return last message for the specified sessionUuid
     */
    fun getLastMessageForSessionUuid(sessionUuid: String?): String? {
        var msgBody: String? = null
        val endTimeStamp = Date().time.toString()
        if (!TextUtils.isEmpty(sessionUuid)) {
            val columns = arrayOf(ChatMessage.MSG_BODY)
            val args = arrayOf(sessionUuid, endTimeStamp)
            val cursor = mDB.query(ChatMessage.TABLE_NAME, columns, ChatMessage.SESSION_UUID + "=? AND "
                    + ChatMessage.TIME_STAMP + "<?", args, null, null, ORDER_DESC, "1")
            while (cursor.moveToNext()) {
                msgBody = cursor.getString(0)
            }
            cursor.close()
        }
        return msgBody
    }

    /**
     * Get and return the last message Timestamp for the specified sessionUuid
     *
     * sessionUuid the chatSessionUuid in ChatMessage.TABLE_NAME
     *
     * @return last message Date (TimeStamp) for the specified sessionUuid
     */
    fun getLastMessageDateForSessionUuid(sessionUuid: String): Date? {
        val endTimeStamp = Date().time.toString()
        val columns = arrayOf(ChatMessage.TIME_STAMP)
        val args = arrayOf(sessionUuid, endTimeStamp)
        val cursor = mDB.query(ChatMessage.TABLE_NAME, columns, ChatMessage.SESSION_UUID + "=? AND "
                + ChatMessage.TIME_STAMP + "<?", args, null, null, ORDER_DESC, "1")
        var mamDate = "-1"
        while (cursor.moveToNext()) {
            mamDate = cursor.getString(0)
        }
        cursor.close()
        return if (mamDate == "-1") null else Date(mamDate.toLong())
    }

    /**
     * Set the chatSession chatType mainly used to set session hidden bit
     *
     * sessionUuid the chatSession Uuid
     *
     * @return the chatSession chatType
     */
    fun setSessionChatType(sessionUuid: String, chatType: Int): Int {
        if (StringUtils.isEmpty(sessionUuid)) return 0
        val args = arrayOf(sessionUuid)
        contentValues.clear()
        contentValues.put(ChatSession.STATUS, chatType)

        // From field crash on java.lang.IllegalArgumentException? HWKSA-M, Android 9
        return try {
            mDB.update(ChatSession.TABLE_NAME, contentValues, ChatSession.SESSION_UUID + "=?", args)
        } catch (e: IllegalArgumentException) {
            Timber.w("Exception in setting Session ChatType for: %s; %s", sessionUuid, e.message)
            -1
        }
    }
    // ============== End ChatSessionFragment utilities ======================
    // ============== Start mam Message utilities ======================
    /**
     * Get the last server mam record access date
     *
     * sessionUuid Chat session Uuid
     *
     * @return the last mamDate updated
     */
    fun getMamDate(sessionUuid: String): Date? {
        val columns = arrayOf(ChatSession.MAM_DATE)
        val args = arrayOf(sessionUuid)
        val cursor = mDB.query(ChatSession.TABLE_NAME, columns,
                ChatSession.SESSION_UUID + "=?", args, null, null, null, null)
        var mamDate: Date? = null
        while (cursor.moveToNext()) {
            mamDate = Date(cursor.getString(0).toLong())
        }
        cursor.close()
        return mamDate
    }

    /**
     * Update the last mam retrieval message timeStamp for the specified sessionUuid.
     * Always advance timeStamp by 10ms to avoid last message being included in future mam fetching.
     *
     * sessionUuid the chat sessions record id to which to save the timestamp
     * date last mam message timestamp
     */
    fun setMamDate(sessionUuid: String, date: Date) {
        contentValues.clear()
        contentValues.put(ChatSession.MAM_DATE, (date.time + 10).toString())
        val args = arrayOf(sessionUuid)
        mDB.update(ChatSession.TABLE_NAME, contentValues, ChatSession.SESSION_UUID + "=?", args)
    }

    fun saveMamIfNotExit(omemoManager: OmemoManager, chatPanel: ChatPanel,
            forwardedList: List<Forwarded<Message>>) {
        val userJid = chatPanel.protocolProvider.connection!!.user
        val chatId = chatPanel.chatSession!!.chatId
        var timeStamp = Date()
        for (forwarded in forwardedList) {
            val msg = forwarded.forwardedStanza

            // Skip all messages that are being sent by own self
            val sender = msg!!.from
            // Timber.d("userJid = %s; sender = %s", userJid, sender);
            if (userJid.equals(sender)) {
                continue
            }

            // Some received/DomainBareJid message does not have msgId. So use stanzaId from StanzaIdElement if found.
            var msgId = msg.stanzaId
            if (TextUtils.isEmpty(msgId)) {
                val stanzaIdElement = msg.getExtension(StanzaIdElement.QNAME)
                if (stanzaIdElement is StanzaIdElement) {
                    msgId = stanzaIdElement.id
                }
            }
            if (TextUtils.isEmpty(msgId)) {
                continue
            }
            timeStamp = forwarded.delayInformation.stamp
            val args = arrayOf(msgId, chatId)
            val cursor = mDB.query(ChatMessage.TABLE_NAME, null, ChatMessage.UUID
                    + "=? AND " + ChatMessage.SESSION_UUID + "=?", args, null, null, null)
            val msgCount = cursor.count
            cursor.close()
            if (msgCount == 0) {
                var iMessage: IMessage? = null
                if (msg.hasExtension(OmemoElement.NAME_ENCRYPTED, OmemoConstants.OMEMO_NAMESPACE_V_AXOLOTL)) {
                    val omemoElement = msg.getExtensionElement(OmemoElement.NAME_ENCRYPTED, OmemoConstants.OMEMO_NAMESPACE_V_AXOLOTL) as OmemoElement
                    try {
                        val oReceive = omemoManager.decrypt(sender.asBareJid(), omemoElement)
                        iMessage = MessageJabberImpl(oReceive.body, IMessage.ENCRYPTION_OMEMO, null, msgId)
                    } catch (e: SmackException.NotLoggedInException) {
                        Timber.e("Omemo decrypt message (%s): %s", msgId, e.message)
                    } catch (e: CorruptedOmemoKeyException) {
                        Timber.e("Omemo decrypt message (%s): %s", msgId, e.message)
                    } catch (e: NoRawSessionException) {
                        Timber.e("Omemo decrypt message (%s): %s", msgId, e.message)
                    } catch (e: CryptoFailedException) {
                        Timber.e("Omemo decrypt message (%s): %s", msgId, e.message)
                    } catch (e: IOException) {
                        Timber.e("Omemo decrypt message (%s): %s", msgId, e.message)
                    }
                } else {
                    iMessage = MessageJabberImpl(msg.body, IMessage.ENCRYPTION_NONE, null, msgId)
                }
                if (iMessage != null) {
                    val direction = if (userJid.asBareJid().isParentOf(sender)) ChatMessage.DIR_OUT else ChatMessage.DIR_IN
                    val msgType = if (Message.Type.groupchat == msg.type) ChatMessage.MESSAGE_ACTION else ChatMessage.MESSAGE_IN
                    if (this.isHistoryLoggingEnabled) {
                        writeMessage(chatId, direction, sender, iMessage, timeStamp, msgType)
                    } else {
                        val fromJid = sender.toString()
                        chatPanel.cacheNextMsg(ChatMessageImpl(fromJid, fromJid, timeStamp,
                                msgType, iMessage, null, direction))
                    }
                    // Timber.d("Message body# %s: (%s) %s => %s", sender, msgId, timeStamp, iMessage.getContent());
                }
            }
        }
        // Save the last mam retrieval timeStamp
        setMamDate(chatId, timeStamp)
    }
    // ============== End mam Message utilities ======================
    /**
     * Return the messages for the recently contacted `count` contacts.
     *
     * count contacts count
     * providerToFilter can be filtered by provider e.g. Jabber:abc123@atalk.org,
     * or `null` to search for all providers
     * contactToFilter can be filtered by contact e.g. xyx123@atalk.org, or `null`
     * to search for all contacts
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findRecentMessagesPerContact(count: Int, providerToFilter: String,
            contactToFilter: String?, isSMSEnabled: Boolean): Collection<EventObject?> {
        var sessionUuid: String
        var accountUuid: String
        var entityJid: String
        var whereCondition = ""
        var args = arrayOf<String?>()
        var cursorMsg: Cursor
        var descriptor: Any?
        val result = LinkedList<EventObject?>()

        // Timber.i("Find recent message for: " + providerToFilter + " -> " + contactToFilter);
        val argList = ArrayList<String?>()
        if (StringUtils.isNotEmpty(providerToFilter)) {
            whereCondition = ChatSession.ACCOUNT_UID + "=?"
            argList.add(providerToFilter)
        }
        if (StringUtils.isNotEmpty(contactToFilter)) {
            if (StringUtils.isNotEmpty(whereCondition)) whereCondition += " AND "
            whereCondition += ChatSession.ENTITY_JID + "=?"
            argList.add(contactToFilter)
        }
        if (argList.size > 0) args = argList.toTypedArray()
        val cursor = mDB.query(ChatSession.TABLE_NAME, null, whereCondition, args, null,
                null, null)

        // Iterate over all the found sessionsUuid for the given accountUuid
        while (cursor.moveToNext()) {
            if (result.size >= count) break
            sessionUuid = cursor.getString(cursor.getColumnIndexOrThrow(ChatSession.SESSION_UUID))
            // skip for null sessionUuid i.e. message from non-persistent contact e.g server announcement.
            if (StringUtils.isEmpty(sessionUuid)) continue
            accountUuid = cursor.getString(cursor.getColumnIndexOrThrow(ChatSession.ACCOUNT_UUID))
            entityJid = cursor.getString(cursor.getColumnIndexOrThrow(ChatSession.ENTITY_JID))

            // find contact or chatRoom for given contactJid; skip if not found contacts,
            // disabled accounts and hidden one
            descriptor = getContactOrRoomByID(accountUuid, entityJid, isSMSEnabled)
            if (descriptor == null) continue
            whereCondition = ChatMessage.SESSION_UUID + "=?"
            argList.clear()
            argList.add(sessionUuid)
            if (isSMSEnabled) {
                whereCondition += " AND (" + ChatMessage.MSG_TYPE + "=? OR " + ChatMessage.MSG_TYPE + "=?)"
                argList.add(ChatMessage.MESSAGE_SMS_IN.toString())
                argList.add(ChatMessage.MESSAGE_SMS_OUT.toString())
            }
            args = argList.toTypedArray()
            cursorMsg = mDB.query(ChatMessage.TABLE_NAME, null, whereCondition, args,
                    null, null, ORDER_DESC, count.toString())
            while (cursorMsg.moveToNext()) {
                if (descriptor is Contact) {
                    val o = convertHistoryRecordToMessageEvent(cursorMsg, descriptor)
                    result.add(o)
                }
                if (descriptor is ChatRoom) {
                    val o = convertHistoryRecordToMessageEvent(cursorMsg, descriptor)
                    result.add(o)
                }
            }
            cursorMsg.close()
        }
        cursor.close()
        Collections.sort(result, MessageEventComparator())
        return result
    }

    /**
     * Get and return the count of messages for the specified accountUuid
     *
     * editedAccUID the current edited account
     *
     * @return count of messages for the specified accountUuid
     */
    fun getMessageCountForAccountUuid(editedAccUID: String): Int {
        var msgCount = 0
        var sessionUuid: String
        val sessionUuids = ArrayList<String>()
        val columns = arrayOf(ChatSession.SESSION_UUID)
        val args = arrayOf(editedAccUID)
        val cursor = mDB.query(ChatSession.TABLE_NAME, columns, ChatSession.ACCOUNT_UID + "=?", args,
                null, null, null)
        while (cursor.moveToNext()) {
            sessionUuid = cursor.getString(0)
            if (!TextUtils.isEmpty(sessionUuid)) sessionUuids.add(sessionUuid)
        }
        cursor.close()
        if (sessionUuids.isNotEmpty()) {
            for (sessionId in sessionUuids) {
                msgCount += getMessageCountForSessionUuid(sessionId)
            }
        }
        return msgCount
    }

    /**
     * Get and return the count of messages for the specified sessionUuid
     *
     * sessionUuid the chatSessionUuid in ChatMessage.TABLE_NAME
     *
     * @return count of messages for the specified sessionUuid
     */
    fun getMessageCountForSessionUuid(sessionUuid: String?): Int {
        var msgCount = 0
        if (!sessionUuid.isNullOrEmpty()) {
            val args = arrayOf(sessionUuid)
            val cursor = mDB.query(ChatMessage.TABLE_NAME, null, ChatMessage.SESSION_UUID + "=?", args,
                    null, null, null)
            msgCount = cursor.count
            cursor.close()
        }
        return msgCount
    }

    /**
     * Find the Contact or ChatRoom corresponding to this contactId. First Checks the account
     * and then searches for the contact or chatRoom. Will skip hidden and disabled accounts.
     *
     * accountUuid the account Uuid.
     * contactId the entityBareJid for Contact or ChatRoom in String.
     * isSMSEnabled get contact from SmsMessage if true
     *
     * @return Contact or ChatRoom object.
     */
    private fun getContactOrRoomByID(accountUuid: String, contactId: String, isSMSEnabled: Boolean): Any? {
        // skip for system virtual server e.g. atalk.org without "@"
        if (StringUtils.isEmpty(contactId) || contactId.indexOf("@") <= 0) return null
        var accountID: AccountID? = null
        for (acc in AccountUtils.storedAccounts) {
            if (!acc!!.isHidden && acc.isEnabled && accountUuid == acc.accountUuid) {
                accountID = acc
                break
            }
        }
        if (accountID == null) return null

        /*
         * Check to ensure account is online (required to fetch room) before proceed. This is to take care in case
         * user has exited while muc chatRoom messages fetching is still on going
         */
        val pps = accountID.protocolProvider
        if (pps == null || !pps.isRegistered) return null
        val opSetPresence = pps.getOperationSet(OperationSetPersistentPresence::class.java) ?: return null
        val contact = opSetPresence.findContactByID(contactId)
        if (contact != null) return contact
        if (isSMSEnabled) {
            // we will check only for sms contacts
            return pps.getOperationSet(OperationSetSmsMessaging::class.java)?.getContact(contactId)
        }
        val opSetMuc = pps.getOperationSet(OperationSetMultiUserChat::class.java)
                ?: return null
        return try {
            // will remove the server part - cmeng: not required in new implementation
            // id = id.substring(0, id.lastIndexOf('@'));
            opSetMuc.findRoom(contactId)
        } catch (e: Exception) {
            Timber.e(e, "Cannot find room for: %s", contactId)
            null
        }
    }

    /**
     * Returns the sessionUuid by specified Contact
     * Non-Persistent Entity will use ChatSession.Mode_MULTI to generate sessionUuid
     *
     * contact The chat Contact
     *
     * @return sessionUuid - created if not exist
     */
    override fun getSessionUuidByJid(contact: Contact): String {
        val accountID = contact.protocolProvider.accountID
        val entityJid = contact.address
        return if (contact.isPersistent) getSessionUuid(accountID, entityJid, ChatSession.MODE_SINGLE) else getSessionUuid(accountID, entityJid, ChatSession.MODE_NPE)
    }

    /**
     * Returns the sessionUuid by specified ChatRoom
     *
     * room The chatRoom
     *
     * @return sessionUuid - created if not exist
     */
    override fun getSessionUuidByJid(chatRoom: ChatRoom): String {
        val accountID = chatRoom.getParentProvider().accountID
        val entityJid = chatRoom.getName()
        return getSessionUuid(accountID, entityJid, ChatSession.MODE_MULTI)
    }

    /**
     * Returns the sessionUuid by specified AccountID and chatRoomID
     *
     * accountID The AccountID
     * entityJid The chatRoomID
     *
     * @return sessionUuid - created if not exist
     */
    override fun getSessionUuidByJid(accountID: AccountID, entityJid: String): String {
        return getSessionUuid(accountID, entityJid, ChatSession.MODE_MULTI)
    }

    /**
     * Returns the sessionUuid by the given AdHocChatRoom
     *
     * room The adHocChatRoom
     *
     * @return sessionUuid - created if not exist
     */
    private fun getSessionUuidByJid(room: AdHocChatRoom): String {
        val accountID = room.getParentProvider().accountID
        val entityJid = room.getName()
        return getSessionUuid(accountID, entityJid, ChatSession.MODE_MULTI)
    }

    /**
     * Get sessionUuid for the unique pair (accountUuid + nick) OR generate new if none found
     *
     * accountID AccountID
     * entityJid Contact or ChatRoom
     * mode indicate if it is ChatSession.MODE_SINGLE or ChatSession.MODE_MUC, dictate the method
     * use to generate new sessionUid
     *
     * @return sessionUuid - created if not exist
     */
    private fun getSessionUuid(accountID: AccountID, entityJid: String, mode: Int): String {
        val accountUuid = accountID.accountUuid
        val accountUid = accountID.accountUniqueID
        var columns = arrayOf(ChatSession.SESSION_UUID)
        val args = arrayOf(accountUuid, entityJid)
        var cursor = mDB.query(ChatSession.TABLE_NAME, columns, ChatSession.ACCOUNT_UUID
                + "=? AND " + ChatSession.ENTITY_JID + "=?", args, null, null, null)
        var sessionUuid: String? = null
        while (cursor.moveToNext()) {
            sessionUuid = cursor.getString(0)
        }
        cursor.close()
        if (StringUtils.isNotEmpty(sessionUuid)) return sessionUuid!!

        // Create new chatSession entry if one does not exist
        val timeStamp = System.currentTimeMillis().toString()
        // Use metaContactUid if it is a metaContact chatSession
        if (mode == ChatSession.MODE_SINGLE) {
            columns = arrayOf(MetaContactGroup.MC_UID)
            cursor = mDB.query(MetaContactGroup.TBL_CHILD_CONTACTS, columns,
                    MetaContactGroup.ACCOUNT_UUID + "=? AND " + MetaContactGroup.CONTACT_JID + "=?",
                    args, null, null, null)
            while (cursor.moveToNext()) {
                sessionUuid = cursor.getString(0)
            }
            cursor.close()
        }

        // generate new sessionUuid for non-persistent contact or ChatSession.MODE_MULTI
        if (StringUtils.isEmpty(sessionUuid)) {
            sessionUuid = timeStamp + abs(entityJid.hashCode())
        }
        contentValues.clear()
        contentValues.put(ChatSession.SESSION_UUID, sessionUuid)
        contentValues.put(ChatSession.ACCOUNT_UUID, accountUuid)
        contentValues.put(ChatSession.ACCOUNT_UID, accountUid)
        contentValues.put(ChatSession.ENTITY_JID, entityJid)
        contentValues.put(ChatSession.CREATED, timeStamp)
        contentValues.put(ChatSession.STATUS, ChatFragment.MSGTYPE_OMEMO)
        contentValues.put(ChatSession.MODE, mode)
        mDB.insert(ChatSession.TABLE_NAME, null, contentValues)
        return sessionUuid!!
    }

    /**
     * Get the chatSession chatType
     *
     * chatSession the chatSession for single or multi-chat
     *
     * @return the chatSession chatType
     */
    override fun getSessionChatType(chatSession: ChatSession): Int {
        var chatType = ChatFragment.MSGTYPE_OMEMO
        val entityJid = chatSession.chatEntity
        val accountUid = chatSession.currentChatTransport!!.protocolProvider.accountID
        if (StringUtils.isEmpty(entityJid)) return chatType

        val accountUuid = accountUid.accountUuid
        val columns = arrayOf(ChatSession.STATUS)
        val args = arrayOf(accountUuid, entityJid)
        val cursor = mDB.query(ChatSession.TABLE_NAME, columns, ChatSession.ACCOUNT_UUID
                + "=? AND " + ChatSession.ENTITY_JID + "=?", args, null, null, null)
        while (cursor.moveToNext()) {
            chatType = cursor.getInt(0)
        }
        cursor.close()

        // clear the session record hidden bit if set; to make visible to ChatSessionFragment UI
        if (chatType and ChatFragment.MSGTYPE_MASK != 0) {
            chatType = chatType and ChatFragment.MSGTYPE_MASK
            setSessionChatType(chatSession, chatType)
        }
        if (ChatFragment.MSGTYPE_UNKNOWN == chatType) chatType = ChatFragment.MSGTYPE_OMEMO
        return chatType
    }

    /**
     * Set the chatSession chatType
     *
     * chatSession the chatSession for single or multi-chat
     *
     * @return the chatSession chatType
     */
    override fun setSessionChatType(chatSession: ChatSession, chatType: Int): Int {
        val entityJid = chatSession.chatEntity
        val accountUid = chatSession.currentChatTransport!!.protocolProvider.accountID
        if (StringUtils.isEmpty(entityJid) || entityJid == aTalkApp.getResString(R.string.service_gui_UNKNOWN)) return 0
        val accountUuid = accountUid.accountUuid
        val args = arrayOf(accountUuid, entityJid)
        contentValues.clear()
        contentValues.put(ChatSession.STATUS, chatType)

        // From field crash on java.lang.IllegalArgumentException? HWKSA-M, Android 9
        return try {
            mDB.update(ChatSession.TABLE_NAME, contentValues, ChatSession.ACCOUNT_UUID
                    + "=? AND " + ChatSession.ENTITY_JID + "=?", args)
        } catch (e: IllegalArgumentException) {
            Timber.w("Exception setSessionChatType for EntityJid: %s and AccountUid: %s; %s",
                    entityJid, accountUid, e.message)
            -1
        }
    }

    /**
     * Use to convert HistoryRecord to MessageDeliveredEvent or MessageReceivedEvent or
     * FileRecord which are returned in cursor by the finder Methods
     *
     * cursor HistoryRecord in cursor
     * contact always the metaContact.getDefaultContact().
     *
     * @return Object
     */
    private fun convertHistoryRecordToMessageEvent(cursor: Cursor, contact: Contact): EventObject {
        val mProperties = Hashtable<String, String>()
        for (i in 0 until cursor.columnCount) {
            val value = if (cursor.getString(i) == null) "" else cursor.getString(i)
            mProperties[cursor.getColumnName(i)] = value
        }

        // Return FileRecord if it is of file transfer message type, but excluding MESSAGE_HTTP_FILE_LINK
        val msgType = Objects.requireNonNull<String?>(mProperties[ChatMessage.MSG_TYPE]).toInt()
        if (msgType == ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY || msgType == ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE || msgType == ChatMessage.MESSAGE_FILE_TRANSFER_SEND || msgType == ChatMessage.MESSAGE_STICKER_SEND) {
            return createFileRecordFromProperties(mProperties, contact)
        }

        // else proceed to process normal chat message
        val msg = createMessageFromProperties(mProperties)
        val timestamp = Date(Objects.requireNonNull<String?>(mProperties[ChatMessage.TIME_STAMP]).toLong())
        val sender = mProperties[ChatMessage.JID]!!
        return if (msg.isOutgoing) {
            val evt = MessageDeliveredEvent(msg, contact, null, sender, timestamp)
            if (ChatMessage.MESSAGE_SMS_OUT == msg.msgSubType) {
                evt.setSmsMessage(true)
            }
            evt
        } else {
            // ContactResource has no meaning for the given contact, so set it to null
            MessageReceivedEvent(msg, contact, null, sender, timestamp, null)
        }
    }

    /**
     * Use to convert HistoryRecord in ChatRoomMessageDeliveredEvent or
     * ChatRoomMessageReceivedEvent which are returned in cursor by the finder methods
     *
     * cursor HistoryRecord in cursor
     * chatRoom the chat room
     *
     * @return EventObject
     */
    private fun convertHistoryRecordToMessageEvent(cursor: Cursor, chatRoom: ChatRoom): EventObject {
        val mProperties = Hashtable<String, String>()
        for (i in 0 until cursor.columnCount) {
            val value = if (cursor.getString(i) == null) "" else cursor.getString(i)
            mProperties[cursor.getColumnName(i)] = value
        }

        // jabberID should contain user bareJid if muc msg in; else contact fullJid if muc msg out
        // EntityBareJid if from chatRoom itself (should not have stored in DB)
        val jabberID = XmppStringUtils.parseBareJid(Objects.requireNonNull<String?>(mProperties[ChatMessage.JID]))
        val pps = chatRoom.getParentProvider()
        val presenceOpSet = pps.getOperationSet(OperationSetPersistentPresence::class.java) as OperationSetPersistentPresenceJabberImpl
        val contact = presenceOpSet.findContactByID(jabberID)

        // Do not include ChatMessage.MESSAGE_HTTP_FILE_LINK
        val msgType = Objects.requireNonNull<String?>(mProperties[ChatMessage.MSG_TYPE]).toInt()
        if (msgType == ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY || msgType == ChatMessage.MESSAGE_FILE_TRANSFER_SEND || msgType == ChatMessage.MESSAGE_STICKER_SEND) {
            return if (contact != null) {
                createFileRecordFromProperties(mProperties, contact)
            } else  // send from me
                createFileRecordFromProperties(mProperties, chatRoom)
        }
        val msg = createMessageFromProperties(mProperties)
        val timestamp = Date(Objects.requireNonNull<String?>(mProperties[ChatMessage.TIME_STAMP]).toLong())
        return if (msg.isOutgoing) {
            ChatRoomMessageDeliveredEvent(chatRoom, timestamp, msg, ChatMessage.MESSAGE_MUC_OUT)
        } else {
            // muc incoming message can be MESSAGE_HTTP_FILE_LINK
            // Incoming muc message contact should not be null unless the sender is not one of user's contacts
            val userJid = contact?.contactJid

            // Incoming muc message Entity_Jid is the nick name of the sender; null if from chatRoom
            val nickName = mProperties[ChatMessage.ENTITY_JID]
            var nick: Resourcepart? = null
            try {
                nick = Resourcepart.from(Objects.requireNonNull<String?>(nickName))
            } catch (e: XmppStringprepException) {
                Timber.w("History record to message conversion with null nick")
            }
            val from = ChatRoomMemberJabberImpl(chatRoom as ChatRoomJabberImpl, nick, userJid)
            ChatRoomMessageReceivedEvent(chatRoom, from, timestamp, msg, msgType)
        }
    }

    /**
     * Create from the retrieved database mProperties to chatMessages
     *
     * mProperties message properties converted from cursor
     *
     * @return MessageImpl
     */
    private fun createMessageFromProperties(mProperties: Map<String, String>): MessageImpl {
        val messageUID = mProperties[ChatMessage.UUID]
        // val messageReceivedDate = Date(Objects.requireNonNull<String?>(mProperties[ChatMessage.TIME_STAMP]).toLong())
        val msgBody = mProperties[ChatMessage.MSG_BODY]!!
        val encType = Objects.requireNonNull<String?>(mProperties[ChatMessage.ENC_TYPE]).toInt()
        val xferStatus = Objects.requireNonNull<String?>(mProperties[ChatMessage.STATUS]).toInt()
        val receiptStatus = Objects.requireNonNull<String?>(mProperties[ChatMessage.READ]).toInt()
        val serverMsgId = mProperties[ChatMessage.SERVER_MSG_ID]
        val remoteMsgId = mProperties[ChatMessage.REMOTE_MSG_ID]
        val isOutgoing = ChatMessage.DIR_OUT == mProperties[ChatMessage.DIRECTION]
        var msgSubType = -1
        val msgType = Objects.requireNonNull<String?>(mProperties[ChatMessage.MSG_TYPE]).toInt()
        if (msgType == ChatMessage.MESSAGE_SMS_OUT || msgType == ChatMessage.MESSAGE_SMS_IN) msgSubType = msgType
        return MessageImpl(msgBody, encType, "", messageUID, xferStatus, receiptStatus,
                serverMsgId, remoteMsgId, isOutgoing, msgSubType)
    }

    /**
     * Create from the retrieved database mProperties to FileRecord
     *
     * mProperties message properties converted from cursor
     * entityJid an instance of Contact or ChatRoom of the history message
     *
     * @return FileRecord
     */
    private fun createFileRecordFromProperties(mProperties: Map<String, String>, entityJid: Any): FileRecord {
        val uuid = mProperties[ChatMessage.UUID]
        val dir = mProperties[ChatMessage.DIRECTION]
        val date = Date(Objects.requireNonNull<String?>(mProperties[ChatMessage.TIME_STAMP]).toLong())
        val file = Objects.requireNonNull<String?>(mProperties[ChatMessage.FILE_PATH])
        val encType = Objects.requireNonNull<String?>(mProperties[ChatMessage.ENC_TYPE]).toInt()
        val status = Objects.requireNonNull<String?>(mProperties[ChatMessage.STATUS]).toInt()
        return FileRecord(uuid!!, entityJid, dir!!, date, File(file), encType, status)
    }

    /**
     * Loads and registers the contact source service.
     */
    private fun loadRecentMessages() {
        messageSourceService = MessageSourceService(this)
        messageSourceServiceReg = bundleContext!!.registerService(
                ContactSourceService::class.java.name, messageSourceService, null)
        MessageHistoryActivator.contactListService!!.addMetaContactListListener(messageSourceService)
    }

    /**
     * Unloads the contact source service.
     */
    private fun stopRecentMessages() {
        if (messageSourceServiceReg != null) {
            MessageHistoryActivator.contactListService!!.removeMetaContactListListener(messageSourceService)
            messageSourceServiceReg!!.unregister()
            messageSourceServiceReg = null
            messageSourceService = null
        }
    }

    // //////////////////////////////////////////////////////////////////////////
    // ChatMessageListener implementation methods for chatMessage
    // cmeng (20210816): Actually when historyLog is disabled, MessageListener is not registered with
    // OperationSetBasicInstantMessaging; hence HttpFileDownload messages are not saved in DB.
    // @see start#isMessageHistoryEnabled
    override fun messageReceived(evt: MessageReceivedEvent) {
        val contact = evt.getSourceContact()
        val metaContact = MessageHistoryActivator.contactListService!!.findMetaContactByContact(contact)

        // return if logging is switched off for this particular contact OR not a Http File Transfer message
        val msgType = evt.getEventType()
        if (metaContact != null && !isHistoryLoggingEnabled(metaContact.getMetaUID()) && ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD != msgType) {
            return
        }

        // Replace last message in DB with the new received correction message content only - no new message record
        val message = evt.getSourceMessage()
        val msgCorrectionId = evt.getCorrectedMessageUID()
        if (!TextUtils.isEmpty(msgCorrectionId)) message.setMessageUID(msgCorrectionId!!)
        val sessionUuid = getSessionUuidByJid(contact)
        writeMessage(sessionUuid, ChatMessage.DIR_IN, contact, evt.getSender(), message, evt.getTimestamp(), msgType)
    }

    override fun messageDelivered(evt: MessageDeliveredEvent) {
        val message = evt.getSourceMessage()
        val contact = evt.getContact()
        val metaContact = MessageHistoryActivator.contactListService!!.findMetaContactByContact(contact)

        // return if logging is switched off for this particular contact
        // and do store if message is for remote only e.g HTTP file upload message
        if (metaContact != null && !isHistoryLoggingEnabled(metaContact.getMetaUID())
                || message.isRemoteOnly()) {
            return
        }

        // Replace last message in DB with the new delivered correction message content only - no new message record
        val msgCorrectionId = evt.getCorrectedMessageUID()
        if (!TextUtils.isEmpty(msgCorrectionId)) message.setMessageUID(msgCorrectionId!!)
        val sessionUuid = getSessionUuidByJid(contact)
        writeMessage(sessionUuid, ChatMessage.DIR_OUT, contact, evt.getSender()!!, message, evt.getTimestamp(), evt.getEventType())
    }

    override fun messageDeliveryFailed(evt: MessageDeliveryFailedEvent) {
        // nothing to do for the history service when delivery failed
    }

    override fun onReceiptReceived(fromJid: Jid, toJid: Jid, receiptId: String, receipt: Stanza) {
        val args = arrayOf(receiptId)
        contentValues.clear()
        contentValues.put(ChatMessage.READ, ChatMessage.MESSAGE_DELIVERY_RECEIPT)
        mDB.update(ChatMessage.TABLE_NAME, contentValues, ChatMessage.SERVER_MSG_ID + "=?", args)
    }

    // //////////////////////////////////////////////////////////////////////////
    // ChatRoomMessageListener implementation methods for chatRoom
    override fun messageReceived(evt: ChatRoomMessageReceivedEvent) {
        val msgType = evt.getEventType()

        // return if logging is switched off for this particular chatRoom or not a Http File Transfer message
        if (!isHistoryLoggingEnabled(evt.getSourceChatRoom().getName())
                && ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD != msgType) {
            return
        }

        // if this is chat room message history on every room enter, we can receive the same
        // latest history messages and this will just fill the history on every join
        if (evt.isHistoryMessage()) {
            val c = findFirstMessagesAfter(evt.getSourceChatRoom(),
                    Date(evt.getTimestamp().time - 10000), 20)
            var hasMatch = false
            for (e in c) {
                if (e is ChatRoomMessageReceivedEvent) {
                    val cev = e
                    val entityJid = evt.getSourceChatRoomMember().getContactAddress()
                    if (entityJid == cev.getSourceChatRoomMember().getContactAddress() && evt.getTimestamp() == cev.getTimestamp()) {
                        hasMatch = true
                        break
                    }
                    // also check and message content
                    val m1 = cev.getMessage()
                    val m2 = evt.getMessage()
                    if (m1.getContent() == m2.getContent()) {
                        hasMatch = true
                        break
                    }
                }
            }
            // ignore if message is already saved
            if (hasMatch) return
        }
        val sessionUuid = getSessionUuidByJid(evt.getSourceChatRoom())
        writeMessage(sessionUuid, ChatMessage.DIR_IN, evt.getSourceChatRoomMember(), evt.getMessage(),
                evt.getTimestamp(), msgType)
    }

    override fun messageDelivered(evt: ChatRoomMessageDeliveredEvent) {
        // return if logging is switched off for this particular chat room
        val room = evt.getSourceChatRoom()
        val message = evt.getMessage()
        if (!isHistoryLoggingEnabled(room.getName()) || message!!.isRemoteOnly()) {
            return
        }

        // if this is chat room message history on every room enter, we can receive the same
        // latest history messages and this will just fill the history on every join
        if (evt.isHistoryMessage()) {
            val c = findFirstMessagesAfter(room,
                    Date(evt.getTimestamp().time - 10000), 20)
            var hasMatch = false
            for (e in c) if (e is ChatRoomMessageDeliveredEvent) {
                val cev = e
                if (evt.getTimestamp() == cev.getTimestamp()) {
                    hasMatch = true
                    break
                }

                // also check and message content
                val m1 = cev.getMessage()
                val m2 = evt.getMessage()
                if (m1 != null && m2 != null && m1.getContent() == m2.getContent()) {
                    hasMatch = true
                    break
                }
            }
            // ignore if message is already saved
            if (hasMatch) return
        }
        val sessionUuid = getSessionUuidByJid(room)
        writeMessage(sessionUuid, ChatMessage.DIR_OUT, room, message, evt.getTimestamp(), ChatMessage.MESSAGE_MUC_OUT)
    }

    override fun messageDeliveryFailed(evt: ChatRoomMessageDeliveryFailedEvent) {
        // nothing to do for the history service when delivery failed
    }

    // //////////////////////////////////////////////////////////////////////////
    // ChatRoomMessageListener implementation methods for AdHocChatRoom (for icq)
    override fun messageReceived(evt: AdHocChatRoomMessageReceivedEvent) {
        val msgType = evt.getEventType()

        // return if logging is switched off for this particular chatRoom or not a Http File Transfer message
        // Must save Http File Transfer message record for transfer status
        val room = evt.getSourceChatRoom()
        if (!isHistoryLoggingEnabled(room.getName()) && ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD != msgType) {
            return
        }
        val sessionUuid = getSessionUuidByJid(evt.getSourceChatRoom())
        writeMessage(sessionUuid, ChatMessage.DIR_IN, room, evt.getMessage(), evt.getTimestamp(), msgType)
    }

    override fun messageDelivered(evt: AdHocChatRoomMessageDeliveredEvent) {
        // return if logging is switched off for this particular chat room
        val room = evt.getSourceChatRoom()
        val message = evt.getMessage()
        if (!isHistoryLoggingEnabled(room.getName()) || message.isRemoteOnly()) {
            return
        }
        val sessionUuid = getSessionUuidByJid(evt.getSourceChatRoom())
        writeMessage(sessionUuid, ChatMessage.DIR_OUT, room, evt.getMessage(), evt.getTimestamp(), evt.getEventType())
    }

    override fun messageDeliveryFailed(evt: AdHocChatRoomMessageDeliveryFailedEvent) {
        // nothing to do for the history service when delivery failed
    }

    // ============== Store message to database ======================
    /**
     * Writes message to the history for MAM, ChatRoom out and AdHocChatRoom in/out
     *
     * chatId The entry with sessionUuid to which it will store the message
     * direction ChatMessage.DIR_IN or DIR_OUT
     * sender can either be EntityBareJid, ChatRoom or AdHocChatRoom (icq implementation)
     * message IMessage
     * msgTimestamp the timestamp when was message received that came from the protocol provider
     * msgType ChatMessage#Type
     */
    private fun writeMessage(chatId: String, direction: String, from: Any?,
            message: IMessage, msgTimestamp: Date, msgType: Int) {
        if (from == null) return
        var jid = ""
        when (from) {
            is ChatRoom -> { // ChatRoomJabberImpl
                val chatRoom = from
                val accountId = chatRoom.getParentProvider().accountID
                jid = accountId.accountJid
            }
            is AdHocChatRoom -> {
                val chatRoom = from
                val accountId = chatRoom.getParentProvider().accountID
                jid = accountId.accountJid
            }
            is ChatRoomMember -> {
                // String entityJid = from.getChatRoom().getName() + "/" + nick;
                jid = from.getContactAddress() // contact entityFullJid
            }
            is Jid -> {
                jid = from.toString()
            }
        }

        // Strip off the resourcePart
        val entityJid = jid.replace("(\\w+)/.*".toRegex(), "$1")
        contentValues.clear()
        contentValues.put(ChatMessage.SESSION_UUID, chatId)
        contentValues.put(ChatMessage.TIME_STAMP, msgTimestamp.time)
        contentValues.put(ChatMessage.ENTITY_JID, entityJid)
        contentValues.put(ChatMessage.JID, jid)
        writeMessageToDB(message, direction, msgType)
    }

//    /**
//     * Writes the message to the history for ChatRoom incoming message
//     *
//     * chatId The entry with chatSessionUuid to which it will store the message
//     * direction the direction of the message.
//     * from coming from
//     * message IMessage
//     * msgTimestamp the timestamp when was message received that came from the protocol provider
//     * msgType ChatMessage#Type
//     */
//    private fun writeMessage(chatId: String, direction: String, from: ChatRoomMember,
//            message: IMessage, msgTimestamp: Date, msgType: Int) {
//        // missing from, strange messages, most probably a history coming from server and probably already written
//        if (from == null) return
//
//        // String entityJid = from.getChatRoom().getName() + "/" + nick;
//        val jid = from.getContactAddress() // contact entityFullJid
//        val entityJid = jid.replace("(\\w+)/.*".toRegex(), "$1")
//        contentValues.clear()
//        contentValues.put(ChatMessage.SESSION_UUID, chatId)
//        contentValues.put(ChatMessage.TIME_STAMP, msgTimestamp.time)
//        contentValues.put(ChatMessage.ENTITY_JID, entityJid)
//        contentValues.put(ChatMessage.JID, jid)
//        writeMessageToDB(message, direction, msgType)
//    }

    /**
     * Writes a message to the history for chatMessage in/out.
     *
     * chatId The entry with chatSessionUuid to which it will store the message
     * direction the direction of the message.
     * contact the communicator contact for this chat
     * sender message sender
     * message IMessage
     * msgTimestamp the timestamp when was message received that came from the protocol provider
     * msgType ChatMessage#Type
     */
    private fun writeMessage(chatId: String, direction: String, contact: Contact, sender: String,
            message: IMessage, msgTimestamp: Date, msgType: Int) {
        contentValues.clear()
        contentValues.put(ChatMessage.SESSION_UUID, chatId)
        contentValues.put(ChatMessage.TIME_STAMP, msgTimestamp.time)
        contentValues.put(ChatMessage.ENTITY_JID, contact.address)
        // JID is not stored for chatMessage or incoming message
        contentValues.put(ChatMessage.JID, sender)
        writeMessageToDB(message, direction, msgType)
    }

    /**
     * Inserts message to the history. Allows to update the already saved message.
     *
     * direction String direction of the message in or out.
     * source The source Contact
     * destination The destination Contact
     * message IMessage message to be written
     * msgTimestamp the timestamp when was message received that came from the protocol provider
     * isSmsSubtype whether message to write is an sms
     */
    override fun insertMessage(direction: String, source: Contact, destination: Contact,
            message: IMessage, messageTimestamp: Date, isSmsSubtype: Boolean) {
        // return if logging is switched off for this particular contact
        val metaContact = MessageHistoryActivator.contactListService!!
                .findMetaContactByContact(destination)
        if (metaContact != null && !isHistoryLoggingEnabled(metaContact.getMetaUID())) {
            return
        }
        val sessionUuid = getSessionUuidByJid(destination)
        val msgType = if (isSmsSubtype) ChatMessage.MESSAGE_SMS_OUT else ChatMessage.MESSAGE_OUT
        contentValues.clear()
        contentValues.put(ChatMessage.SESSION_UUID, sessionUuid)
        contentValues.put(ChatMessage.TIME_STAMP, messageTimestamp.time)
        contentValues.put(ChatMessage.ENTITY_JID, source.address)
        contentValues.put(ChatMessage.JID, destination.address)
        writeMessageToDB(message, direction, msgType)
    }

    /**
     * Update the reset of the message content and write to the dataBase
     *
     * message IMessage message to be written
     * direction ChatMessage.DIR_IN or DIR_OUT
     * msgType ChatMessage#Type
     */
    private fun writeMessageToDB(message: IMessage, direction: String, msgType: Int) {
        contentValues.put(ChatMessage.UUID, message.getMessageUID())
        contentValues.put(ChatMessage.MSG_BODY, message.getContent())
        contentValues.put(ChatMessage.ENC_TYPE, message.getEncType())
        contentValues.put(ChatMessage.CARBON, if (message.isCarbon()) 1 else 0)
        contentValues.put(ChatMessage.DIRECTION, direction)
        contentValues.put(ChatMessage.MSG_TYPE, msgType)
        if (ChatMessage.DIR_OUT == direction) {
            contentValues.put(ChatMessage.STATUS, ChatMessage.MESSAGE_OUT)
            contentValues.put(ChatMessage.SERVER_MSG_ID, message.getServerMsgId())
            contentValues.put(ChatMessage.REMOTE_MSG_ID, message.getRemoteMsgId())
            contentValues.put(ChatMessage.READ, ChatMessage.MESSAGE_DELIVERY_CLIENT_SENT)
        } else {
            contentValues.put(ChatMessage.STATUS, if (ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD == msgType) FileRecord.STATUS_UNKNOWN else ChatMessage.MESSAGE_IN)
            contentValues.put(ChatMessage.REMOTE_MSG_ID, message.getMessageUID())
        }
        mDB.insert(ChatMessage.TABLE_NAME, null, contentValues)
    }

    //============ service change events handler ================//
    /**
     * Remove a configuration service.
     *
     * historyService HistoryService
     */
    fun unsetHistoryService(historyService: HistoryService) {
        synchronized(syncRootHistoryService) {
            if (this.historyService === historyService) {
                this.historyService = null
                Timber.d("History service unregistered.")
            }
        }
    }

    /**
     * Called to notify interested parties that a change in our presence in a chat room has
     * occurred. Changes may include us being kicked, join, left.
     *
     * evt the `LocalUserChatRoomPresenceChangeEvent` instance containing the chat
     * room and the type, and reason of the change
     */
    override fun localUserPresenceChanged(evt: LocalUserChatRoomPresenceChangeEvent) {
        if (LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_JOINED == evt.getEventType()) {
            if (!evt.getChatRoom().isSystem()) {
                evt.getChatRoom().addMessageListener(this)
                if (messageSourceService != null) evt.getChatRoom().addMessageListener(messageSourceService!!)
            }
        } else {
            evt.getChatRoom().removeMessageListener(this)
            if (messageSourceService != null) evt.getChatRoom().removeMessageListener(messageSourceService!!)
        }
    }

    /**
     * Adding progress listener for monitoring progress of search process
     *
     * listener HistorySearchProgressListener
     */
    override fun addSearchProgressListener(listener: MessageHistorySearchProgressListener) {
        synchronized(progressListeners) {
            val wrapperListener = SearchProgressWrapper(listener)
            progressListeners.put(listener, wrapperListener)
        }
    }

    /**
     * Removing progress listener
     *
     * listener HistorySearchProgressListener
     */
    override fun removeSearchProgressListener(listener: MessageHistorySearchProgressListener) {
        synchronized(progressListeners) { progressListeners.remove(listener) }
    }
    // =========== find messages for metaContact and chatRoom with keywords ==================
    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact between
     * the given dates including startDate and having the given keywords
     *
     * metaContact MetaContact
     * startDate Date the start date of the conversations
     * endDate Date the end date of the conversations
     * keywords array of keywords
     * caseSensitive is keywords search case sensitive
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findByPeriod(metaContact: MetaContact, startDate: Date,
            endDate: Date, keywords: Array<String>, caseSensitive: Boolean): Collection<EventObject?> {
        val result = HashSet<EventObject?>()
        val startTimeStamp = startDate.time.toString()
        val endTimeStamp = endDate.time.toString()
        var filterLike = StringBuilder("( ")
        for (word in keywords) {
            filterLike.append(ChatMessage.MSG_BODY + " LIKE '%").append(word).append("%' OR ")
        }
        filterLike = StringBuilder(filterLike.substring(0, filterLike.length - 4) + " )")
        val contacts = metaContact.getContacts()
        while (contacts.hasNext()) {
            val contact = contacts.next()!!
            val sessionUuid = getSessionUuidByJid(contact)
            val args = arrayOf(sessionUuid, startTimeStamp, endTimeStamp)
            val cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                    ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + ">=? AND "
                            + ChatMessage.TIME_STAMP + "<? AND " + filterLike, args, null, null, ORDER_ASC)
            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToMessageEvent(cursor, contact))
            }
        }
        return result
    }

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact
     * having the given keyword
     *
     * metaContact MetaContact
     * keyword keyword
     * caseSensitive is keywords search case sensitive
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findByKeyword(metaContact: MetaContact, keyword: String,
            caseSensitive: Boolean): Collection<EventObject?> {
        val result = HashSet<EventObject?>()
        val filterLike = "( " + ChatMessage.MSG_BODY + " LIKE '%" + keyword + "%' )"
        val contacts = metaContact.getContacts()
        while (contacts.hasNext()) {
            val contact = contacts.next()!!
            val sessionUuid = getSessionUuidByJid(contact)
            val args = arrayOf(sessionUuid)
            val cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                    ChatMessage.SESSION_UUID + "=? AND " + filterLike, args, null, null, ORDER_ASC)
            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToMessageEvent(cursor, contact))
            }
        }
        return result
    }

    /**
     * Returns all the messages exchanged by all the contacts in the supplied metaContact
     * having the given keywords
     *
     * metaContact MetaContact
     * keywords keyword
     * caseSensitive is keywords search case sensitive
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findByKeywords(metaContact: MetaContact,
            keywords: Array<String>, caseSensitive: Boolean): Collection<EventObject?> {
        val result = HashSet<EventObject?>()
        var filterLike = StringBuilder("( ")
        for (word in keywords) {
            filterLike.append(ChatMessage.MSG_BODY + " LIKE '%").append(word).append("%' OR ")
        }
        filterLike = StringBuilder(filterLike.substring(0, filterLike.length - 4) + " )")
        val contacts = metaContact.getContacts()
        while (contacts.hasNext()) {
            val contact = contacts.next()!!
            val sessionUuid = getSessionUuidByJid(contact)
            val args = arrayOf(sessionUuid)
            val cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                    ChatMessage.SESSION_UUID + "=? AND " + filterLike, args, null, null, ORDER_ASC)
            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToMessageEvent(cursor, contact))
            }
        }
        return result
    }

    /**
     * Returns all the messages exchanged in the supplied chat room on and after the given date
     *
     * room The chat room
     * startDate Date the start date of the conversations
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findByStartDate(room: ChatRoom, startDate: Date): Collection<EventObject?> {
        val result = HashSet<EventObject?>()
        val startTimeStamp = startDate.time.toString()
        val sessionUuid = getSessionUuidByJid(room)
        val args = arrayOf(sessionUuid, startTimeStamp)
        val cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + ">=?",
                args, null, null, ORDER_ASC)
        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, room))
        }
        return result
    }

    /**
     * Returns all the messages exchanged in the supplied chat room before the given date
     *
     * room The chat room
     * endDate Date the end date of the conversations
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findByEndDate(room: ChatRoom, endDate: Date): Collection<EventObject?> {
        val result = HashSet<EventObject?>()
        val endTimeStamp = endDate.time.toString()
        val sessionUuid = getSessionUuidByJid(room)
        val args = arrayOf(sessionUuid, endTimeStamp)
        val cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + "<?",
                args, null, null, ORDER_ASC)
        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, room))
        }
        return result
    }

    /**
     * Returns all the messages exchanged in the supplied chat room between the given dates
     *
     * room The chat room
     * startDate Date the start date of the conversations
     * endDate Date the end date of the conversations
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findByPeriod(room: ChatRoom, startDate: Date, endDate: Date): Collection<EventObject?> {
        val result = HashSet<EventObject?>()
        val startTimeStamp = startDate.time.toString()
        val endTimeStamp = endDate.time.toString()
        val sessionUuid = getSessionUuidByJid(room)
        val args = arrayOf(sessionUuid, startTimeStamp, endTimeStamp)
        val cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + ">=? AND "
                        + ChatMessage.TIME_STAMP + "<?", args, null, null, ORDER_ASC)
        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, room))
        }
        return result
    }

    /**
     * Returns all the messages exchanged in the supplied chat room between the given
     * dates and having the given keywords
     *
     * room The chat room
     * startDate Date the start date of the conversations
     * endDate Date the end date of the conversations
     * keywords array of keywords
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findByPeriod(room: ChatRoom, startDate: Date, endDate: Date,
            keywords: Array<String>): Collection<EventObject?> {
        return findByPeriod(room, startDate, endDate, keywords, false)
    }

    /**
     * Returns all the messages exchanged in the supplied chat room between the given
     * dates and having the given keywords
     *
     * room The chat room
     * startDate Date the start date of the conversations
     * endDate Date the end date of the conversations
     * keywords array of keywords
     * caseSensitive is keywords search case sensitive
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findByPeriod(room: ChatRoom, startDate: Date, endDate: Date, keywords: Array<String>, caseSensitive: Boolean): Collection<EventObject?> {
        val result = HashSet<EventObject?>()
        val startTimeStamp = startDate.time.toString()
        val endTimeStamp = endDate.time.toString()
        val sessionUuid = getSessionUuidByJid(room)
        val args = arrayOf(sessionUuid, startTimeStamp, endTimeStamp)
        var filterLike = StringBuilder("( ")
        for (word in keywords) {
            filterLike.append(ChatMessage.MSG_BODY + " LIKE '%").append(word).append("%' OR ")
        }
        filterLike = StringBuilder(filterLike.substring(0, filterLike.length - 4) + " )")
        val cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + ">=? AND "
                        + ChatMessage.TIME_STAMP + "<? AND " + filterLike, args, null, null, ORDER_ASC)
        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, room))
        }
        return result
    }

    /**
     * Returns all the messages exchanged in the supplied room having the given keyword
     *
     * room The Chat room
     * keyword keyword
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findByKeyword(room: ChatRoom, keyword: String): Collection<EventObject?> {
        return findByKeyword(room, keyword, false)
    }

    /**
     * Returns all the messages exchanged in the supplied chat room having the given
     * keyword
     *
     * room The chat room
     * keyword keyword
     * caseSensitive is keywords search case sensitive
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findByKeyword(room: ChatRoom, keyword: String,
            caseSensitive: Boolean): Collection<EventObject?> {
        val result = HashSet<EventObject?>()
        val sessionUuid = getSessionUuidByJid(room)
        val args = arrayOf(sessionUuid)
        val filterLike = "( " + ChatMessage.MSG_BODY + " LIKE '%" + keyword + "%' )"
        val cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + filterLike, args, null, null, ORDER_ASC)
        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, room))
        }
        return result
    }

    /**
     * Returns all the messages exchanged in the supplied chat room having the given
     * keywords
     *
     * room The chat room
     * keywords keyword
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findByKeywords(room: ChatRoom, keywords: Array<String>): Collection<EventObject?> {
        return findByKeywords(room, keywords, false)
    }

    /**
     * Returns all the messages exchanged in the supplied chat room having the given
     * keywords
     *
     * room The chat room
     * keywords keyword
     * caseSensitive is keywords search case sensitive
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findByKeywords(room: ChatRoom, keywords: Array<String>,
            caseSensitive: Boolean): Collection<EventObject?> {
        val result = HashSet<EventObject?>()
        val sessionUuid = getSessionUuidByJid(room)
        val args = arrayOf(sessionUuid)
        var filterLike = StringBuilder("( ")
        for (word in keywords) {
            filterLike.append(ChatMessage.MSG_BODY + " LIKE '%").append(word).append("%' OR ")
        }
        filterLike = StringBuilder(filterLike.substring(0, filterLike.length - 4) + " )")
        val cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + filterLike, args, null, null, ORDER_ASC)
        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, room))
        }
        return result
    }

    /**
     * Returns the supplied number of recent messages exchanged in the supplied chat room
     *
     * room The chat room
     * count messages count
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findLast(room: ChatRoom, count: Int): Collection<EventObject?> {
        val result = LinkedList<EventObject?>()
        val sessionUuid = getSessionUuidByJid(room)
        val args = arrayOf(sessionUuid)
        val cursor = mDB.query(ChatMessage.TABLE_NAME, null, ChatMessage.SESSION_UUID
                + "=?", args, null, null, ORDER_DESC, count.toString())
        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, room))
        }
        Collections.sort(result, MessageEventComparator())
        return result
    }

    /**
     * Returns the supplied number of recent messages on and after the given startDate exchanged
     * in the supplied chat room
     *
     * room The chat room
     * startDate messages on and after date
     * count messages count
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findFirstMessagesAfter(room: ChatRoom, startDate: Date, count: Int): Collection<EventObject?> {
        val result = LinkedList<EventObject?>()
        val startTimeStamp = startDate.time.toString()
        val sessionUuid = getSessionUuidByJid(room)
        val args = arrayOf(sessionUuid, startTimeStamp)
        val cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + ">=?",
                args, null, null, ORDER_DESC, count.toString())
        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, room))
        }
        Collections.sort(result, ChatRoomMessageEventComparator())
        return result
    }

    /**
     * Returns the supplied number of recent messages before the given endDate exchanged in
     * the supplied chat room
     *
     * room The chat room
     * endDate messages before date
     * count messages count
     *
     * @return Collection of MessageReceivedEvents or MessageDeliveredEvents
     */
    override fun findLastMessagesBefore(room: ChatRoom, endDate: Date, count: Int): Collection<EventObject?> {
        val result = LinkedList<EventObject?>()
        val endTimeStamp = endDate.time.toString()
        val sessionUuid = getSessionUuidByJid(room)
        val args = arrayOf(sessionUuid, endTimeStamp)
        val cursor = mDB.query(ChatMessage.TABLE_NAME, null,
                ChatMessage.SESSION_UUID + "=? AND " + ChatMessage.TIME_STAMP + "<?",
                args, null, null, ORDER_DESC, count.toString())
        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToMessageEvent(cursor, room))
        }
        Collections.sort(result, ChatRoomMessageEventComparator())
        return result
    }

    /**
     * Loads the History and MessageHistoryService. Registers the service in the bundle context.
     */
    private fun loadMessageHistoryService() {
        configService!!.addPropertyChangeListener(
                MessageHistoryService.PNAME_IS_RECENT_MESSAGES_DISABLED, msgHistoryPropListener!!)
        val isRecentMessagesDisabled = configService!!.getBoolean(
                MessageHistoryService.PNAME_IS_RECENT_MESSAGES_DISABLED, false)
        if (!isRecentMessagesDisabled) loadRecentMessages()

        // start listening for newly register or removed protocol providers
        bundleContext!!.addServiceListener(this)
        for (pps in currentlyAvailableProviders) {
            handleProviderAdded(pps)
        }
    }// this shouldn't happen since we're providing no parameter string but let's log just
    // in case.

    // in case we found any
    /**
     * Returns currently registered in osgi ProtocolProviderServices.
     *
     * @return currently registered in osgi ProtocolProviderServices.
     */
    val currentlyAvailableProviders: List<ProtocolProviderService>
        get() {
            val res = ArrayList<ProtocolProviderService>()
            val protocolProviderRefs = try {
                bundleContext!!.getServiceReferences(ProtocolProviderService::class.java.name, null)
            } catch (ex: InvalidSyntaxException) {
                // this shouldn't happen since we're providing no parameter string but let's log just
                // in case.
                Timber.e(ex, "Error while retrieving service refs")
                return res
            }

            // in case we found any
            if (protocolProviderRefs != null) {
                Timber.d("Found %s already installed providers.", protocolProviderRefs.size)
                for (protocolProviderRef in protocolProviderRefs) {
                    val provider = bundleContext!!.getService(protocolProviderRef) as ProtocolProviderService
                    res.add(provider)
                }
            }
            return res
        }

    /**
     * Stops the MessageHistoryService.
     */
    private fun stopMessageHistoryService() {
        // start listening for newly register or removed protocol providers
        bundleContext!!.removeServiceListener(this)
        val protocolProviderRefs = try {
            bundleContext!!.getServiceReferences(ProtocolProviderService::class.java.name, null)
        } catch (ex: InvalidSyntaxException) {
            // this shouldn't happen since we're providing no parameter string but let's log just
            // in case.
            Timber.e(ex, "Error while retrieving service refs")
            return
        }

        // in case we found any
        if (protocolProviderRefs != null) {
            for (protocolProviderRef in protocolProviderRefs) {
                val provider = bundleContext!!.getService(protocolProviderRef) as ProtocolProviderService
                handleProviderRemoved(provider)
            }
        }
    }

    /**
     * Called to notify interested parties that a change in our presence in an ad-hoc chat room
     * has occurred. Changes may include us being join, left.
     *
     * evt the `LocalUserAdHocChatRoomPresenceChangeEvent` instance containing the ad-hoc
     * chat room and the type, and reason of the change
     */
    override fun localUserAdHocPresenceChanged(evt: LocalUserAdHocChatRoomPresenceChangeEvent) {
        if (LocalUserAdHocChatRoomPresenceChangeEvent.LOCAL_USER_JOINED == evt.getEventType()) {
            evt.getAdHocChatRoom()!!.addMessageListener(this)
        } else {
            evt.getAdHocChatRoom()!!.removeMessageListener(this)
        }
    }
    // =============== Erase chat history for entities for given message Uuids =====================
    /**
     * Permanently removes all locally stored message history.
     * - Remove only chatMessages for metaContacts i.e. ChatSession.MODE_SINGLE
     * - Remove both chatMessage and chatSession info (no currently) for muc i.e. ChatSession.MODE_MULTI
     */
    override fun eraseLocallyStoredChatHistory(chatMode: Int) {
        val args = arrayOf(chatMode.toString())
        val columns = arrayOf(ChatSession.SESSION_UUID)
        val sessionUuids = ArrayList<String>()
        val cursor = mDB.query(ChatSession.TABLE_NAME, columns, ChatSession.MODE + "=?",
                args, null, null, null)
        while (cursor.moveToNext()) {
            sessionUuids.add(cursor.getString(0))
        }
        cursor.close()
        purgeLocallyStoredHistory(sessionUuids, ChatSession.MODE_SINGLE != chatMode)
    }

    /**
     * Permanently removes locally stored message history as listed in msgUUIDs;
     * Or all the chat message for the metaContact if msgUUIDs is null.
     *
     * metaContact metaContact
     * msgUUIDs Purge all the chat messages listed in the msgUUIDs.
     */
    override fun eraseLocallyStoredChatHistory(metaContact: MetaContact, messageUUIDs: List<String>?) {
        if (messageUUIDs == null) {
            val contacts = metaContact.getContacts()
            while (contacts.hasNext()) {
                val contact = contacts.next()!!
                purgeLocallyStoredHistory(listOf(getSessionUuidByJid(contact)), false)
            }
        } else {
            purgeLocallyStoredHistory(messageUUIDs)
        }
    }

    /**
     * Permanently removes locally stored message history as listed in msgUUIDs;
     * Or all the chat message for the specified room if msgUUIDs is null.
     *
     * room ChatRoom
     * msgUUIDs Purge all the chat messages listed in the msgUUIDs.
     */
    override fun eraseLocallyStoredChatHistory(room: ChatRoom, messageUUIDs: List<String>?) {
        if (messageUUIDs == null) {
            purgeLocallyStoredHistory(listOf(getSessionUuidByJid(room)), true)
        } else {
            purgeLocallyStoredHistory(messageUUIDs)
        }
    }

    /**
     * Permanently removes locally stored message history as specified in msgUUIDs.
     *
     * msgUUIDs list of message Uuid to be erase
     */
    private fun purgeLocallyStoredHistory(msgUUIDs: List<String?>) {
        for (uuid in msgUUIDs) {
            val args = arrayOf(uuid)
            mDB.delete(ChatMessage.TABLE_NAME, ChatMessage.UUID + "=?", args)
        }
    }

    /**
     * Permanently removes locally stored message history for each sessionUuid listed in sessionUuids.
     * - Remove only chatMessages for metaContacts
     * - Remove both chatSessions and chatMessages for muc
     *
     * sessionUuids list of sessionUuids to be erased.
     * eraseSid erase also the item in ChatSession Table if true.
     */
    fun purgeLocallyStoredHistory(sessionUuids: List<String>, eraseSid: Boolean) {
        for (uuid in sessionUuids) {
            val args = arrayOf(uuid)
            // purged all messages with the same sessionUuid
            mDB.delete(ChatMessage.TABLE_NAME, ChatMessage.SESSION_UUID + "=?", args)

            // Purge the sessionUuid in the ChatSession if true
            if (eraseSid) {
                mDB.delete(ChatSession.TABLE_NAME, ChatSession.SESSION_UUID + "=?", args)
            }
        }
    }
    // =============== End Erase chat history for entities for given message Uuids =====================
    /**
     * Retrieve all locally stored media file paths for the specified descriptor
     *
     * descriptor MetaContact or ChatRoomWrapper
     *
     * @return List of media file Paths
     */
    override fun getLocallyStoredFilePath(descriptor: Any): List<String> {
        val msgFilePathDel = ArrayList<String>()
        var filePath: String
        lateinit var sessionUuid: String
        if (descriptor is MetaContact) {
            val contacts = descriptor.getContacts()
            val contact = contacts.next()
            if (contact != null) sessionUuid = getSessionUuidByJid(contact)
        } else {
            val chatRoom = (descriptor as ChatRoomWrapper).chatRoom
            if (chatRoom != null) sessionUuid = getSessionUuidByJid(chatRoom)
        }
        if (sessionUuid != null) {
            val args = arrayOf(sessionUuid)
            val columns = arrayOf(ChatMessage.FILE_PATH)
            val cursor = mDB.query(ChatMessage.TABLE_NAME, columns, ChatMessage.SESSION_UUID + "=?",
                    args, null, null, null)
            while (cursor.moveToNext()) {
                filePath = cursor.getString(0)
                if (!TextUtils.isEmpty(filePath)) {
                    msgFilePathDel.add(filePath)
                }
            }
            cursor.close()
        }
        return msgFilePathDel
    }

    /**
     * Retrieve all locally stored media file paths for all the received messages
     *
     * @return List of media file Paths
     */
    override val locallyStoredFilePath: List<String>
        get() {
            val msgFilePathDel = ArrayList<String>()
            var filePath: String
            val columns = arrayOf(ChatMessage.FILE_PATH)
            val cursor = mDB.query(ChatMessage.TABLE_NAME, columns, ChatMessage.FILE_PATH + " IS NOT NULL",
                    null, null, null, null)
            while (cursor.moveToNext()) {
                filePath = cursor.getString(0)
                if (!TextUtils.isEmpty(filePath)) {
                    msgFilePathDel.add(filePath)
                }
            }
            cursor.close()
            return msgFilePathDel
        }
    /**
     * Returns `true` if the "PNAME_IS_MESSAGE_HISTORY_ENABLED" property is true,
     * otherwise - returns `false`. Indicates to the user interface whether the
     * history logging is enabled.
     *
     * @return `true` if the "IS_MESSAGE_HISTORY_ENABLED" property is true,
     * otherwise - returns `false`.
     */
    /**
     * Updates the "isHistoryLoggingEnabled" property through the `ConfigurationService`.
     *
     * isEnabled indicates if the history logging is enabled.
     */
    override var isHistoryLoggingEnabled: Boolean
        get() = Companion.isHistoryLoggingEnabled
        set(isEnabled) {
            Companion.isHistoryLoggingEnabled = isEnabled
            configService!!.setProperty(MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED,
                    java.lang.Boolean.toString(Companion.isHistoryLoggingEnabled))
        }

    /**
     * Returns `true` if the "PNAME_IS_MESSAGE_HISTORY_PER_CONTACT_ENABLED_PREFIX.id"
     * property is true for the `id`, otherwise - returns `false`.
     * The Global isHistoryLoggingEnabled must be enabled for this to take effect.
     * Indicates to the user interface whether the history logging is enabled for the
     * supplied id (id for metaContact or for chat room).
     *
     * @return `true` if the "PNAME_IS_MESSAGE_HISTORY_PER_CONTACT_ENABLED_PREFIX"
     * property for the `id` AND isHistoryLoggingEnabled are true,
     * otherwise - returns `false`.
     */
    override fun isHistoryLoggingEnabled(id: String): Boolean {
        return Companion.isHistoryLoggingEnabled && configService!!.getBoolean(
                MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_PER_CONTACT_ENABLED_PREFIX
                        + "." + id, true)
    }

    /**
     * Updates the "isHistoryLoggingEnabled" property through the `ConfigurationService`
     * for the contact.
     *
     * isEnabled indicates if the history logging is enabled for the contact.
     */
    override fun setHistoryLoggingEnabled(id: String, isEnabled: Boolean) {
        configService!!.setProperty(
                MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_PER_CONTACT_ENABLED_PREFIX
                        + "." + id, if (isEnabled) null else false)
    }

    /**
     * Simple message implementation.
     */
    private class MessageImpl(content: String, encType: Int, subject: String?, messageUID: String?, xferStatus: Int, receiptStatus: Int,
            serverMsgId: String?, remoteMsgId: String?, val isOutgoing: Boolean, val msgSubType: Int) : AbstractMessage(content, encType, subject!!, messageUID, xferStatus, receiptStatus, serverMsgId, remoteMsgId)

    /**
     * Used to compare MessageDeliveredEvent or MessageReceivedEvent and to be ordered in TreeSet
     * according their timestamp
     */
    private class MessageEventComparator<T> @JvmOverloads constructor(private val reverseOrder: Boolean = false) : Comparator<T> {
        override fun compare(o1: T, o2: T): Int {
            val date1 = if (o1 is MessageDeliveredEvent) (o1 as MessageDeliveredEvent).getTimestamp() else if (o1 is MessageReceivedEvent) (o1 as MessageReceivedEvent).getTimestamp() else if (o1 is ChatRoomMessageDeliveredEvent) (o1 as ChatRoomMessageDeliveredEvent).getTimestamp() else if (o1 is ChatRoomMessageReceivedEvent) (o1 as ChatRoomMessageReceivedEvent).getTimestamp() else return 0
            val date2 = if (o2 is MessageDeliveredEvent) (o2 as MessageDeliveredEvent).getTimestamp() else if (o2 is MessageReceivedEvent) (o2 as MessageReceivedEvent).getTimestamp() else if (o2 is ChatRoomMessageDeliveredEvent) (o2 as ChatRoomMessageDeliveredEvent).getTimestamp() else if (o2 is ChatRoomMessageReceivedEvent) (o2 as ChatRoomMessageReceivedEvent).getTimestamp() else return 0
            return if (reverseOrder) date2.compareTo(date1) else date1.compareTo(date2)
        }
    }

    /**
     * Used to compare ChatRoomMessageDeliveredEvent or ChatRoomMessageReceivedEvent and to be
     * ordered in TreeSet according their timestamp
     */
    private class ChatRoomMessageEventComparator<T> : Comparator<T> {
        override fun compare(o1: T, o2: T): Int {
            val date1 = if (o1 is ChatRoomMessageDeliveredEvent) (o1 as ChatRoomMessageDeliveredEvent).getTimestamp() else if (o1 is ChatRoomMessageReceivedEvent) (o1 as ChatRoomMessageReceivedEvent).getTimestamp() else return 0
            val date2 = if (o2 is ChatRoomMessageDeliveredEvent) (o2 as ChatRoomMessageDeliveredEvent).getTimestamp() else if (o2 is ChatRoomMessageReceivedEvent) (o2 as ChatRoomMessageReceivedEvent).getTimestamp() else return 0
            return date1.compareTo(date2)
        }
    }

    /**
     * A wrapper around HistorySearchProgressListener that fires events for
     * MessageHistorySearchProgressListener
     */
    private inner class SearchProgressWrapper(listener: MessageHistorySearchProgressListener) : HistorySearchProgressListener {
        var currentReaderProgressRatio = 0.0
        var accumulatedRatio = 0.0
        var currentProgress = 0.0
        var lastHistoryProgress = 0.0

        // used for more precise calculations with double values
        var raiser = 1000
        private val listener: MessageHistorySearchProgressListener

        init {
            this.listener = listener
        }

        private fun setCurrentValues(currentReader: HistoryReader, allRecords: Int) {
            currentReaderProgressRatio = currentReader.countRecords().toDouble() / allRecords * raiser
            accumulatedRatio += currentReaderProgressRatio
        }

        override fun progressChanged(evt: ProgressEvent?) {
            val progress = getProgressMapping(evt)
            currentProgress = progress.toDouble()
            listener.progressChanged(net.java.sip.communicator.service.msghistory.event.ProgressEvent(this@MessageHistoryServiceImpl, evt!!, progress / raiser))
        }

        /**
         * Calculates the progress according the count of the records we will search
         *
         * evt the progress event
         *
         * @return int
         */
        private fun getProgressMapping(evt: ProgressEvent?): Int {
            val tmpHistoryProgress = currentReaderProgressRatio * evt!!.progress
            currentProgress += tmpHistoryProgress - lastHistoryProgress
            if (evt.progress == HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE) {
                lastHistoryProgress = 0.0

                // this is the last one and the last event fire the max there will be looses in
                // currentProgress due to the deviation
                if (accumulatedRatio.toInt() == raiser) currentProgress = raiser * MessageHistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE.toDouble()
            } else lastHistoryProgress = tmpHistoryProgress
            return currentProgress.toInt()
        }

        /**
         * clear the values
         */
        fun clear() {
            currentProgress = 0.0
            lastHistoryProgress = 0.0
        }
    }

    /**
     * Handles `PropertyChangeEvent` triggered from the modification of the isMessageHistoryEnabled property.
     */
    private inner class MessageHistoryPropertyChangeListener : PropertyChangeListener {
        override fun propertyChange(evt: PropertyChangeEvent) {
            if (evt.propertyName == MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED) {
                val newPropertyValue = evt.newValue as String
                Companion.isHistoryLoggingEnabled = java.lang.Boolean.parseBoolean(newPropertyValue)

                // If the message history is not enabled we stop here.
                if (Companion.isHistoryLoggingEnabled) loadMessageHistoryService() else stop(bundleContext)
            } else if (evt.propertyName == MessageHistoryService.PNAME_IS_RECENT_MESSAGES_DISABLED) {
                val newPropertyValue = evt.newValue as String
                val isDisabled = java.lang.Boolean.parseBoolean(newPropertyValue)
                if (isDisabled) {
                    stopRecentMessages()
                } else if (Companion.isHistoryLoggingEnabled) {
                    loadRecentMessages()
                }
            }
        }
    }

    companion object {
        /**
         * Sort database message records by TimeStamp in ASC or DESC
         */
        private const val ORDER_ASC = ChatMessage.TIME_STAMP + " ASC"
        private const val ORDER_DESC = ChatMessage.TIME_STAMP + " DESC"

        /**
         * Indicates if history logging is enabled.
         */
        private var isHistoryLoggingEnabled = true
    }
}