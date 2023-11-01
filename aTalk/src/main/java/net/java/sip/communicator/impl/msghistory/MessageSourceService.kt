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
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.contactlist.event.MetaContactListAdapter
import net.java.sip.communicator.service.contactlist.event.MetaContactRenamedEvent
import net.java.sip.communicator.service.contactsource.ContactQuery
import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.filehistory.FileRecord
import net.java.sip.communicator.service.msghistory.MessageSourceContactPresenceStatus
import net.java.sip.communicator.service.muc.ChatRoomPresenceStatus
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageDeliveryFailedEvent
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageListener
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageReceivedEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveryFailedEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageListener
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageReceivedEvent
import net.java.sip.communicator.service.protocol.event.ContactCapabilitiesEvent
import net.java.sip.communicator.service.protocol.event.ContactCapabilitiesListener
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener
import net.java.sip.communicator.service.protocol.event.ContactPropertyChangeEvent
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceChangeEvent
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceListener
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.MessageDeliveryFailedEvent
import net.java.sip.communicator.service.protocol.event.MessageListener
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent
import net.java.sip.communicator.service.protocol.event.ProviderPresenceStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.ProviderPresenceStatusListener
import net.java.sip.communicator.service.protocol.event.SubscriptionEvent
import net.java.sip.communicator.service.protocol.event.SubscriptionListener
import net.java.sip.communicator.service.protocol.event.SubscriptionMovedEvent
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.ChatMessage
import org.atalk.persistance.DatabaseBackend
import timber.log.Timber
import java.beans.PropertyChangeEvent
import java.io.IOException
import java.util.*
import kotlin.math.abs

/**
 * The source contact service. This will show most recent messages.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class MessageSourceService(
        /**
         * Message history service that has created us.
         */
        private val messageHistoryService: MessageHistoryServiceImpl) : MetaContactListAdapter(), ContactSourceService, ContactPresenceStatusListener, ContactCapabilitiesListener, ProviderPresenceStatusListener, SubscriptionListener, LocalUserChatRoomPresenceListener, MessageListener, ChatRoomMessageListener, AdHocChatRoomMessageListener {
    /**
     * List of recent messages.
     */
    private val recentMessages = LinkedList<ComparableEvtObj>()
    /**
     * Returns default type to indicate that this contact source can be queried by default filters.
     *
     * @return the type of this contact source
     */
    /**
     * The type of the source service, the place to be shown in the ui.
     */
    override var type = ContactSourceService.CONTACT_LIST_TYPE

    /**
     * Number of messages to show.
     */
    private var numberOfMessages = 0

    /**
     * Date of the oldest shown message.
     */
    private var oldestRecentMessage: Date? = null

    /**
     * The last query created.
     */
    private var recentQuery: MessageSourceContactQuery? = null
    /**
     * Access for source contacts impl.
     *
     * @return isSMSEnabled
     */
    /**
     * The message subtype if any.
     */
    var isSMSEnabled = false

    // SQLite database variables
    private val mDB = DatabaseBackend.writableDB
    private val contentValues = ContentValues()

    /**
     * Constructs MessageSourceService.
     */
    init {
        val conf = MessageHistoryActivator.configurationService
        if (conf!!.getBoolean(IN_HISTORY_PROPERTY, false)) {
            type = ContactSourceService.HISTORY_TYPE
        }
        numberOfMessages = conf.getInt(NUMBER_OF_RECENT_MSGS_PROP, numberOfMessages)
        isSMSEnabled = conf.getBoolean(IS_MESSAGE_SUBTYPE_SMS_PROP, isSMSEnabled)
        RECENT_MSGS_VER = conf.getString(VER_OF_RECENT_MSGS_PROP, RECENT_MSGS_VER)
        MessageSourceContactPresenceStatus.MSG_SRC_CONTACT_ONLINE.statusIcon = MessageHistoryActivator.resources!!.getImageInBytes("sms_status_icon")
    }

    override fun providerStatusChanged(evt: ProviderPresenceStatusChangeEvent) {
        if (!evt.getNewStatus().isOnline || evt.getOldStatus().isOnline) return
        handleProviderAdded(evt.getProvider(), true)
    }

    override fun providerStatusMessageChanged(evt: PropertyChangeEvent) {}

    /**
     * When a provider is added, do not block and start executing in new thread.
     *
     * @param provider ProtocolProviderService
     */
    fun handleProviderAdded(provider: ProtocolProviderService, isStatusChanged: Boolean) {
        Timber.d("Handle new provider added and status changed to online: %s", provider.accountID.mUserID)
        Thread { handleProviderAddedInSeparateThread(provider, isStatusChanged) }.start()
    }

    /**
     * When a provider is added. As searching can be slow especially when handling special type of
     * messages (with subType) this need to be run in new Thread.
     * cmeng - may not be true for SQLite database implementation
     *
     * @param provider ProtocolProviderService
     */
    private fun handleProviderAddedInSeparateThread(provider: ProtocolProviderService, isStatusChanged: Boolean) {
        // lets check if we have cached recent messages for this provider, and fire events if found and are newer
        synchronized(recentMessages) {
            val cachedRecentMessages = getCachedRecentMessages(provider, isStatusChanged)
            if (cachedRecentMessages.isEmpty()) {
                // there is no cached history for this, let's check and load it not from cache, but do a local search
                val res = messageHistoryService.findRecentMessagesPerContact(numberOfMessages,
                        provider.accountID.accountUniqueID!!, null, isSMSEnabled)
                val newMsc = ArrayList<ComparableEvtObj>()
                processEventObjects(res, newMsc, isStatusChanged)
                addNewRecentMessages(newMsc)
                for (msc in newMsc) {
                    saveRecentMessageToHistory(msc)
                }
            } else {
                addNewRecentMessages(cachedRecentMessages)
            }
        }
    }

    /**
     * A provider has been removed.
     *
     * @param provider the ProtocolProviderService that has been unregistered.
     */
    fun handleProviderRemoved(provider: ProtocolProviderService?) {
        // Remove the recent messages for this provider, and update with recent messages for the available providers
        synchronized(recentMessages) {
            if (provider != null) {
                val removedItems = ArrayList<ComparableEvtObj>()
                for (msc in recentMessages) {
                    if (msc.protocolProviderService == provider) removedItems.add(msc)
                }
                recentMessages.removeAll(removedItems)
                oldestRecentMessage = if (recentMessages.isNotEmpty()) recentMessages[recentMessages.size - 1].timestamp else null
                if (recentQuery != null) {
                    for (msc in removedItems) {
                        recentQuery!!.fireContactRemoved(msc)
                    }
                }
            }

            // handleProviderRemoved can be invoked due to stopped history service, if this is the
            // case we do not want to update messages
            if (!messageHistoryService.isHistoryLoggingEnabled) return

            // lets do the same as we enable provider for all registered providers and finally fire events
            val contactsToAdd = ArrayList<ComparableEvtObj>()
            for (pps in messageHistoryService.currentlyAvailableProviders) {
                contactsToAdd.addAll(getCachedRecentMessages(pps, true))
            }
            addNewRecentMessages(contactsToAdd)
        }
    }

    /**
     * Updates contact source contacts with status.
     *
     * @param evt the ContactPresenceStatusChangeEvent describing the status
     */
    override fun contactPresenceStatusChanged(evt: ContactPresenceStatusChangeEvent) {
        if (recentQuery == null) return
        synchronized(recentMessages) {
            for (msg in recentMessages) {
                if (msg.contact != null && msg.contact == evt.getSourceContact()) {
                    recentQuery!!.updateContactStatus(msg, evt.getNewStatus())
                }
            }
        }
    }

    override fun localUserPresenceChanged(evt: LocalUserChatRoomPresenceChangeEvent) {
        if (recentQuery == null) return
        var srcContact: ComparableEvtObj? = null
        synchronized(recentMessages) {
            for (msg in recentMessages) {
                if (msg.room != null && msg.room == evt.getChatRoom()) {
                    srcContact = msg
                    break
                }
            }
        }
        if (srcContact == null) return
        val eventType = evt.getEventType()
        if (LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_JOINED == eventType) {
            recentQuery!!.updateContactStatus(srcContact!!, ChatRoomPresenceStatus.CHAT_ROOM_ONLINE)
        } else if (LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_LEFT == eventType || LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_KICKED == eventType || LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_DROPPED == eventType) {
            recentQuery!!.updateContactStatus(srcContact!!, ChatRoomPresenceStatus.CHAT_ROOM_OFFLINE)
        }
    }

    /**
     * Updates the contact sources in the recent query if any. Done here in order to sync with
     * recentMessages instance, and to check for already existing instances of contact sources.
     * Normally called from the query.
     */
    fun updateRecentMessages() {
        if (recentQuery == null) return
        synchronized(recentMessages) {
            val currentContactsInQuery = recentQuery!!.queryResults
            for (evtObj in recentMessages) {
                // the contains will use the correct equals method of the object evtObj
                if (!currentContactsInQuery.contains<Any>(evtObj)) {
                    val newSourceContact = MessageSourceContact(evtObj.eventObject, this@MessageSourceService)
                    newSourceContact.initDetails(evtObj.eventObject)
                    recentQuery!!.addQueryResult(newSourceContact)
                }
            }
        }
    }

    /**
     * Searches for entries in cached recent messages in history.
     *
     * @param provider the provider which contact messages we will search e.g Jabber:abc123@atalk.org
     * @param isStatusChanged is the search because of status changed
     * @return entries in cached recent messages in history.
     */
    private fun getCachedRecentMessages(provider: ProtocolProviderService, isStatusChanged: Boolean): List<ComparableEvtObj> {
        var res: Collection<EventObject?>?
        val accountId = provider.accountID.accountUniqueID
        val recentMessagesContactIDs = getRecentContactIDs(accountId!!,
                if (recentMessages.size < numberOfMessages) null else oldestRecentMessage)
        val cachedRecentMessages = ArrayList<ComparableEvtObj>()
        for (contactId in recentMessagesContactIDs) {
            try {
                res = messageHistoryService.findRecentMessagesPerContact(numberOfMessages,
                        accountId, contactId, isSMSEnabled)
                processEventObjects(res, cachedRecentMessages, isStatusChanged)
            } catch (e: Exception) { // IndexOutOfBound
                Timber.w("Get cache recent message exception for: %s => %s", contactId, e.message)
            }
        }
        return cachedRecentMessages
    }

    /**
     * Process list of event objects. Checks whether message source contact already exist for this
     * event object, if yes just update it with the new values (not sure whether we should do
     * this, as it may bring old messages) and if status of provider is changed, init its
     * details, updates its capabilities. It still adds the found messages source contact to the
     * list of the new contacts, as later we will detect this and fire update event. If nothing
     * found a new contact is created.
     *
     * @param res list of lately fetched event
     * @param cachedRecentMessages list of newly created source contacts or already existed but updated with
     * corresponding event object
     * @param isStatusChanged whether provider status changed and we are processing
     */
    private fun processEventObjects(res: Collection<EventObject?>?,
            cachedRecentMessages: MutableList<ComparableEvtObj>, isStatusChanged: Boolean) {
        for (eventObject in res!!) {
            // skip process any non-message FileRecord object
            if (eventObject is FileRecord) continue
            var oldMsg = findRecentMessage(eventObject, recentMessages)
            if (oldMsg != null) {
                oldMsg.update(eventObject) // update
                if (isStatusChanged && recentQuery != null) recentQuery!!.updateCapabilities(oldMsg, eventObject)

                // we still add it to cachedRecentMessages later we will find it is duplicate and
                // will fire update event
                if (!cachedRecentMessages.contains(oldMsg)) cachedRecentMessages.add(oldMsg)
                continue
            }
            oldMsg = findRecentMessage(eventObject, cachedRecentMessages)
            if (oldMsg == null) {
                oldMsg = ComparableEvtObj(eventObject)
                if (isStatusChanged && recentQuery != null) recentQuery!!.updateCapabilities(oldMsg, eventObject)
                cachedRecentMessages.add(oldMsg)
            }
        }
    }

    /**
     * Add the ComparableEvtObj, newly added will fire new, for existing fire update and when
     * trimming the list to desired length fire remove for those that were removed
     *
     * @param contactsToAdd List of contacts to add
     */
    private fun addNewRecentMessages(contactsToAdd: List<ComparableEvtObj>) {
        // now find object to fire new, and object to fire remove let us find duplicates and fire update
        val duplicates = ArrayList<ComparableEvtObj>()
        for (msgToAdd in contactsToAdd) {
            if (recentMessages.contains(msgToAdd)) {
                duplicates.add(msgToAdd)
                // save update
                updateRecentMessageToHistory(msgToAdd)
            }
        }
        recentMessages.removeAll(duplicates)
        // now contacts to add has no duplicates, add them all
        val changed = recentMessages.addAll(contactsToAdd)
        if (changed) {
            recentMessages.sort()
            if (recentQuery != null) {
                for (obj in duplicates) recentQuery!!.updateContact(obj, obj.eventObject)
            }
        }
        if (recentMessages.isNotEmpty()) oldestRecentMessage = recentMessages[recentMessages.size - 1].timestamp

        // trim
        var removedItems: List<ComparableEvtObj>? = null
        if (recentMessages.size > numberOfMessages) {
            removedItems = ArrayList(recentMessages.subList(numberOfMessages, recentMessages.size))
            recentMessages.removeAll(removedItems)
        }
        if (recentQuery != null) {
            // now fire, removed for all that were in the list and now are removed after trim
            if (removedItems != null) {
                for (msc in removedItems) {
                    if (!contactsToAdd.contains(msc)) recentQuery!!.fireContactRemoved(msc)
                }
            }
            // fire new for all that were added, and not removed after trim
            for (msc in contactsToAdd) {
                if ((removedItems == null || !removedItems.contains(msc))
                        && !duplicates.contains(msc)) {
                    val newSourceContact = MessageSourceContact(msc.eventObject, this@MessageSourceService)
                    newSourceContact.initDetails(msc.eventObject)
                    recentQuery!!.addQueryResult(newSourceContact)
                }
            }
            // if recent messages were changed, indexes have change lets fire event for the last
            // element which will reorder the whole group if needed.
            if (changed) recentQuery!!.fireContactChanged(recentMessages[recentMessages.size - 1])
        }
    }

    /**
     * Searches for contact ids in history of recent messages on and after the startDate
     *
     * @param accountUid Account Uid
     * @param startDate start date to search; can be null if not applicable
     * @return List of found entityJid
     */
    private fun getRecentContactIDs(accountUid: String, startDate: Date?): List<String> {
        val contacts = ArrayList<String>()
        val argList = ArrayList<String>()
        val columns = arrayOf(ENTITY_JID)
        var whereCondition = "$ACCOUNT_UID=?"
        argList.add(accountUid)

        // add startDate if not null as additional search condition
        if (startDate != null) {
            whereCondition += " AND $TIME_STAMP>=?"
            argList.add(startDate.time.toString())
        }
        val args = argList.toTypedArray()

        // Retrieve all the entityJid for the given accountUid and startDate
        val cursor = mDB.query(TABLE_NAME, columns,
                whereCondition, args, null, null, null)
        while (cursor.moveToNext()) {
            contacts.add(cursor.getString(0))
        }
        cursor.close()
        return contacts
    }

    /**
     * Adds recent message in history database;
     * Remove excess of old records (+10) each time if db exceed NUMBER_OF_MSGS_IN_HISTORY.
     */
    private fun saveRecentMessageToHistory(msc: ComparableEvtObj) {
        // Keep the record size to within the specified NUMBER_OF_MSGS_IN_HISTORY
        val cursor = mDB.query(TABLE_NAME, null, null, null,
                null, null, ORDER_ASC)
        val excess = cursor.count - NUMBER_OF_MSGS_IN_HISTORY
        if (excess > 0) {
            cursor.move(excess + 12)
            val args = arrayOf(cursor.getString(cursor.getColumnIndexOrThrow(TIME_STAMP)))
            val count = mDB.delete(TABLE_NAME, "$TIME_STAMP<?", args)
            Timber.d("No of recent old messages deleted : %s", count)
        }
        cursor.close()
        val date = Date()
        val uuid = date.time.toString() + abs(date.hashCode())
        val accountUid = msc.protocolProviderService!!.accountID.accountUniqueID
        contentValues.clear()
        contentValues.put(UUID, uuid)
        contentValues.put(ACCOUNT_UID, accountUid)
        contentValues.put(ENTITY_JID, msc.contactAddress)
        contentValues.put(TIME_STAMP, msc.timestamp!!.time)
        contentValues.put(VERSION, RECENT_MSGS_VER)
        mDB.insert(TABLE_NAME, null, contentValues)
    }

    /**
     * Updates recent message in history.
     */
    private fun updateRecentMessageToHistory(msg: ComparableEvtObj) {
        contentValues.clear()
        contentValues.put(TIME_STAMP, msg.timestamp!!.time)
        contentValues.put(VERSION, RECENT_MSGS_VER)
        val accountUid = msg.protocolProviderService!!.accountID.accountUniqueID
        val entityJid = msg.contactAddress
        val args = arrayOf(accountUid, entityJid)
        mDB.update(TABLE_NAME, contentValues, "$ACCOUNT_UID=? AND $ENTITY_JID=?", args)
    }

    // ================ Message events handlers =======================
    override fun messageReceived(evt: MessageReceivedEvent) {
        if (isSMSEnabled && evt.getEventType() != ChatMessage.MESSAGE_SMS_IN) {
            return
        }
        handle(evt, evt.getSourceContact().protocolProvider, evt.getSourceContact().address)
    }

    override fun messageDelivered(evt: MessageDeliveredEvent) {
        if (isSMSEnabled && !evt.isSmsMessage()) return
        handle(evt, evt.getContact().protocolProvider, evt.getContact().address)
    }

    /**
     * @param evt the `MessageFailedEvent`
     */
    override fun messageDeliveryFailed(evt: MessageDeliveryFailedEvent) {}
    override fun messageReceived(evt: ChatRoomMessageReceivedEvent) {
        if (isSMSEnabled) return

        // ignore non conversation messages
        if (evt.getEventType() != ChatMessage.MESSAGE_IN) return
        handle(evt, evt.getSourceChatRoom().getParentProvider(), evt.getSourceChatRoom().getName())
    }

    override fun messageDelivered(evt: ChatRoomMessageDeliveredEvent) {
        if (isSMSEnabled) return
        handle(evt, evt.getSourceChatRoom().getParentProvider(), evt.getSourceChatRoom().getName())
    }

    /**
     * @param evt the `ChatRoomMessageDeliveryFailedEvent`
     */
    override fun messageDeliveryFailed(evt: ChatRoomMessageDeliveryFailedEvent) {}
    override fun messageReceived(evt: AdHocChatRoomMessageReceivedEvent) {
        // TODO
    }

    override fun messageDelivered(evt: AdHocChatRoomMessageDeliveredEvent) {
        // TODO
    }

    /**
     * @param evt the `AdHocChatRoomMessageDeliveryFailedEvent`
     */
    override fun messageDeliveryFailed(evt: AdHocChatRoomMessageDeliveryFailedEvent) {}
    override fun subscriptionCreated(evt: SubscriptionEvent?) {}
    override fun subscriptionFailed(evt: SubscriptionEvent?) {}
    override fun subscriptionRemoved(evt: SubscriptionEvent?) {}
    override fun subscriptionMoved(evt: SubscriptionMovedEvent?) {}
    override fun subscriptionResolved(evt: SubscriptionEvent?) {}

    /**
     * Handles new events.
     *
     * @param obj the event object
     * @param provider the provider
     * @param id the id of the source of the event
     */
    private fun handle(obj: EventObject?, provider: ProtocolProviderService?, id: String?) {
        // check if provider - contact exist update message content
        synchronized(recentMessages) {
            var existingMsc: ComparableEvtObj? = null
            for (msc in recentMessages) {
                if (msc.protocolProviderService == provider && msc.contactAddress == id) {
                    msc.update(obj)
                    updateRecentMessageToHistory(msc)
                    existingMsc = msc
                }
            }
            if (existingMsc != null) {
                recentMessages.sort()
                oldestRecentMessage = recentMessages[recentMessages.size - 1].timestamp
                if (recentQuery != null) {
                    recentQuery!!.updateContact(existingMsc, existingMsc.eventObject)
                    recentQuery!!.fireContactChanged(existingMsc)
                }
                return
            }

            // if missing create source contact and update recent messages, trim and sort
            val newSourceContact = MessageSourceContact(obj, this@MessageSourceService)
            newSourceContact.initDetails(obj)
            // we have already checked for duplicate
            val newMsg = ComparableEvtObj(obj)
            recentMessages.add(newMsg)
            recentMessages.sort()
            oldestRecentMessage = recentMessages[recentMessages.size - 1].timestamp

            // trim
            var removedItems: List<ComparableEvtObj>? = null
            if (recentMessages.size > numberOfMessages) {
                removedItems = ArrayList(recentMessages.subList(numberOfMessages, recentMessages.size))
                recentMessages.removeAll(removedItems)
            }
            // save
            saveRecentMessageToHistory(newMsg)

            // no query nothing to fire
            if (recentQuery == null) return

            // now fire
            if (removedItems != null) {
                for (msc in removedItems) {
                    recentQuery!!.fireContactRemoved(msc)
                }
            }
            recentQuery!!.addQueryResult(newSourceContact)
        }
    }

    /**
     * If a contact is renamed update the locally stored message if any.
     *
     * @param evt the `ContactPropertyChangeEvent` containing the source
     */
    override fun contactModified(evt: ContactPropertyChangeEvent?) {
        if (evt!!.propertyName != ContactPropertyChangeEvent.PROPERTY_DISPLAY_NAME) return
        val contact = evt.getSourceContact()
        for (msc in recentMessages) {
            if (contact == msc.contact) {
                if (recentQuery != null) recentQuery!!.updateContactDisplayName(msc, contact.displayName)
                return
            }
        }
    }

    /**
     * Indicates that a MetaContact has been modified.
     *
     * @param evt the MetaContactListEvent containing the corresponding contact
     */
    override fun metaContactRenamed(evt: MetaContactRenamedEvent) {
        for (msc in recentMessages) {
            if (evt.getSourceMetaContact().containsContact(msc.contact)) {
                if (recentQuery != null) recentQuery!!.updateContactDisplayName(msc, evt.getNewDisplayName())
            }
        }
    }

    override fun supportedOperationSetsChanged(event: ContactCapabilitiesEvent?) {
        val contact = event!!.getSourceContact()
        for (msc in recentMessages) {
            if (contact == msc.contact) {
                if (recentQuery != null) recentQuery!!.updateCapabilities(msc, contact)
                return
            }
        }
    }

    /**
     * Returns the display name of this contact source.
     *
     * @return the display name of this contact source
     */
    override val displayName: String
        get() = aTalkApp.getResString(R.string.service_gui_RECENT_MESSAGES)

    /**
     * Returns the index of the contact source in the result list.
     *
     * @return the index of the contact source in the result list
     */
    override val index: Int
        get() = 0

    /**
     * Returns the index of the source contact, in the list of recent messages.
     *
     * @param messageSourceContact search item
     * @return index of recentMessages containing the messageSourceContact
     */
    fun getIndex(messageSourceContact: MessageSourceContact): Int {
        synchronized(recentMessages) {
            for (i in recentMessages.indices) if (recentMessages[i].contact == messageSourceContact.contact) return i
            return -1
        }
    }

    /**
     * Creates query for the given `searchString`.
     *
     * @param queryString the string to search for
     * @return the created query
     */
    override fun createContactQuery(queryString: String): ContactQuery? {
        recentQuery = createContactQuery(queryString, numberOfMessages) as MessageSourceContactQuery?
        return recentQuery
    }

    /**
     * Creates query for the given `searchString`.
     *
     * @param queryString the string to search for
     * @param contactCount the maximum count of result contacts
     * @return the created query
     */
    override fun createContactQuery(queryString: String, contactCount: Int): ContactQuery? {
        if (StringUtils.isNotEmpty(queryString)) return null
        recentQuery = MessageSourceContactQuery(this@MessageSourceService)
        return recentQuery
    }

    /**
     * Object used to cache recent messages.
     */
    private inner class ComparableEvtObj(source: EventObject?) : Comparable<ComparableEvtObj> {
        /**
         * The event object.
         *
         * @return the event object.
         */
        var eventObject: EventObject? = null
            private set
        /**
         * The protocol provider.
         *
         * @return the protocol provider.
         */
        /**
         * The protocol provider.
         */
        var protocolProviderService: ProtocolProviderService? = null
            private set
        /**
         * The address.
         *
         * @return the address.
         */
        /**
         * The address.
         */
        var contactAddress: String? = null
            private set
        /**
         * The timestamp of the message.
         *
         * @return the timestamp of the message.
         */
        /**
         * The timestamp.
         */
        var timestamp: Date? = null
            private set
        /**
         * The contact.
         *
         * @return the contact.
         */
        /**
         * The contact instance.
         */
        var contact: Contact? = null
            private set
        /**
         * The room.
         *
         * @return the room.
         */
        /**
         * The room instance.
         */
        var room: ChatRoom? = null
            private set

        /**
         * Constructs.
         *
         * source used to extract initial values.
         */
        init {
            update(source)
        }

        /**
         * Extract values from `EventObject`.
         *
         * @param source the eventObject to retrieve information
         */
        fun update(source: EventObject?) {
            eventObject = source
            when (source) {
                is MessageDeliveredEvent -> {
                    contact = source.getContact()
                    contactAddress = contact!!.address
                    protocolProviderService = contact!!.protocolProvider
                    timestamp = source.getTimestamp()
                }
                is MessageReceivedEvent -> {
                    contact = source.getSourceContact()
                    contactAddress = contact!!.address
                    protocolProviderService = contact!!.protocolProvider
                    timestamp = source.getTimestamp()
                }
                is ChatRoomMessageDeliveredEvent -> {
                    room = source.getSourceChatRoom()
                    contactAddress = room!!.getName()
                    protocolProviderService = room!!.getParentProvider()
                    timestamp = source.getTimestamp()
                }
                is ChatRoomMessageReceivedEvent -> {
                    room = source.getSourceChatRoom()
                    contactAddress = room!!.getName()
                    protocolProviderService = room!!.getParentProvider()
                    timestamp = source.getTimestamp()
                }
            }
        }

        override fun toString(): String {
            return "ComparableEvtObj{address='$contactAddress', ppService=$protocolProviderService}"
        }

        /**
         * Compares two ComparableEvtObj.
         *
         * @param other the object to compare with
         * @return 0, less than zero, greater than zero, if equals, less or greater.
         */
        override fun compareTo(other: ComparableEvtObj): Int {
            return if (other.timestamp == null) 1 else other.timestamp!!.compareTo(timestamp)
        }

        /**
         * Checks if equals, and if this event object is used to create a MessageSourceContact, if
         * the supplied `Object` is instance of MessageSourceContact.
         *
         * @param other the object to check.
         * @return `true` if equals.
         */
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || other !is MessageSourceContact && javaClass != other.javaClass) return false
            return when (other) {
                is ComparableEvtObj -> {
                    contactAddress == other.contactAddress && protocolProviderService == other.protocolProviderService
                }
                is MessageSourceContact -> {
                    contactAddress == other.contactAddress && protocolProviderService == other.protocolProviderService
                }
                else -> false
            }
        }

        override fun hashCode(): Int {
            var result = contactAddress.hashCode()
            result = 31 * result + protocolProviderService.hashCode()
            return result
        }
    }

    /**
     * Permanently removes all locally stored message history, remove recent contacts.
     */
    @Throws(IOException::class)
    fun eraseLocallyStoredHistory() {
        var toRemove: List<ComparableEvtObj>
        synchronized(recentMessages) {
            toRemove = ArrayList(recentMessages)
            recentMessages.clear()
        }
        if (recentQuery != null) {
            for (msc in toRemove) {
                recentQuery!!.fireContactRemoved(msc)
            }
        }
    }

    /**
     * Permanently removes locally stored message history for the metaContact, remove any recent contacts if any.
     */
    @Throws(IOException::class)
    fun eraseLocallyStoredHistory(metaContact: MetaContact, mhsTimeStamp: List<Date?>?) {
        var toRemove: MutableList<ComparableEvtObj>
        synchronized(recentMessages) {
            toRemove = ArrayList()
            val contacts = metaContact.getContacts()
            while (contacts.hasNext()) {
                val contact = contacts.next()
                val id = contact!!.address
                val provider = contact.protocolProvider
                if (mhsTimeStamp == null) {
                    for (msc in recentMessages) {
                        if (msc.protocolProviderService == provider && msc.contactAddress == id) {
                            toRemove.add(msc)
                        }
                    }
                } else {
                    for (msc in recentMessages) {
                        if (msc.protocolProviderService == provider && msc.contactAddress == id) {
                            toRemove.add(msc)
                        }
                    }
                }
            }
            recentMessages.removeAll(toRemove)
        }
        if (recentQuery != null) {
            for (msc in toRemove) {
                recentQuery!!.fireContactRemoved(msc)
            }
        }
    }

    /**
     * Permanently removes locally stored message history for the chatRoom, remove any recent contacts if any.
     */
    fun eraseLocallyStoredHistory(room: ChatRoom) {
        var toRemove: ComparableEvtObj? = null
        synchronized(recentMessages) {
            for (msg in recentMessages) {
                if (msg.room != null && msg.room == room) {
                    toRemove = msg
                    break
                }
            }
            if (toRemove == null) return
            recentMessages.remove(toRemove)
        }
        if (recentQuery != null) recentQuery!!.fireContactRemoved(toRemove)
    }

    companion object {
        /* DB database column fields for call history */
        const val TABLE_NAME = "recentMessages"
        const val UUID = "uuid" // unique identification for the recent message
        const val ACCOUNT_UID = "accountUid" // account uid
        const val ENTITY_JID = "entityJid" // contact Jid
        const val TIME_STAMP = "timeStamp" // callEnd TimeStamp
        const val VERSION = "version" // version

        /**
         * Whether to show recent messages in history or in contactList. By default we show it in contactList.
         */
        private const val IN_HISTORY_PROPERTY = "msghistory.contactsrc.IN_HISTORY"

        /**
         * Property to control number of recent messages.
         */
        private const val NUMBER_OF_RECENT_MSGS_PROP = "msghistory.contactsrc.MSG_NUMBER"

        /**
         * Property to control version of recent messages.
         */
        private const val VER_OF_RECENT_MSGS_PROP = "msghistory.contactsrc.MSG_VER"

        /**
         * Property to control messages type. Can query for message sub type.
         */
        private const val IS_MESSAGE_SUBTYPE_SMS_PROP = "msghistory.contactsrc.IS_SMS_ENABLED"

        /**
         * Sort database message records by TimeStamp in ASC
         */
        private const val ORDER_ASC = "$TIME_STAMP ASC"

        /**
         * The maximum number of recent messages to store in the history, but will retrieve just `numberOfMessages`
         */
        private const val NUMBER_OF_MSGS_IN_HISTORY = 100

        /**
         * The current version of recent messages. When changed the recent messages are recreated.
         */
        private var RECENT_MSGS_VER : String? = "2"

        /**
         * Tries to match the event object to already existing ComparableEvtObj in the supplied list.
         *
         * @param obj the object that we will try to match.
         * @param list the list we will search in.
         * @return the found ComparableEvtObj
         */
        private fun findRecentMessage(obj: EventObject?,
                list: List<ComparableEvtObj>): ComparableEvtObj? {
            var contact: Contact? = null
            var chatRoom: ChatRoom? = null
            when (obj) {
                is MessageDeliveredEvent -> {
                    contact = obj.getContact()
                }
                is MessageReceivedEvent -> {
                    contact = obj.getSourceContact()
                }
                is ChatRoomMessageDeliveredEvent -> {
                    chatRoom = obj.getSourceChatRoom()
                }
                is ChatRoomMessageReceivedEvent -> {
                    chatRoom = obj.getSourceChatRoom()
                }
            }
            for (evt in list) {
                if ((contact != null && contact == evt.contact || chatRoom != null && chatRoom == evt.room)) return evt
            }
            return null
        }
    }
}