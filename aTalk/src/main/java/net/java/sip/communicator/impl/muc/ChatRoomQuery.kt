/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.impl.muc

import net.java.sip.communicator.service.contactsource.AsyncContactQuery
import net.java.sip.communicator.service.contactsource.ContactQuery
import net.java.sip.communicator.service.contactsource.ContactQueryListener
import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.muc.ChatRoomListChangeEvent
import net.java.sip.communicator.service.muc.ChatRoomListChangeListener
import net.java.sip.communicator.service.muc.ChatRoomPresenceStatus
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapperListener
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceChangeEvent
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceListener
import org.osgi.framework.Bundle
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceReference
import java.util.*
import java.util.regex.Pattern

/**
 * The `ChatRoomQuery` is a query over the `ChatRoomContactSourceService`.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class ChatRoomQuery(
        /**
         * The query string.
         */
        override val queryString: String, contactSource: ChatRoomContactSourceService?) : AsyncContactQuery<ContactSourceService?>(contactSource!!, Pattern.compile(queryString, Pattern.CASE_INSENSITIVE or Pattern.LITERAL), true), LocalUserChatRoomPresenceListener, ChatRoomListChangeListener, ChatRoomProviderWrapperListener {
    /**
     * List with the current results for the query.
     */
    private val contactResults = TreeSet<ChatRoomSourceContact?>()

    /**
     * MUC service.
     */
    private val mucService = MUCActivator.mucService

    /**
     * The number of contact query listeners.
     */
    private var contactQueryListenersCount = 0

    /**
     * The protocol provider registration listener.
     */
    private var protolProviderRegistrationListener: ServiceListener? = null

    /**
     * Adds listeners for the query
     */
    private fun initListeners() {
        for (pps in MUCActivator.getChatRoomProviders()!!) {
            addQueryToProviderPresenceListeners(pps)
        }
        mucService.addChatRoomListChangeListener(this)
        mucService.addChatRoomProviderWrapperListener(this)
        protolProviderRegistrationListener = ProtocolProviderRegListener()
        MUCActivator.bundleContext!!.addServiceListener(protolProviderRegistrationListener)
    }

    /**
     * Adds the query as presence listener to protocol provider service.
     *
     * @param pps the protocol provider service.
     */
    fun addQueryToProviderPresenceListeners(pps: ProtocolProviderService) {
        val opSetMUC = pps.getOperationSet(OperationSetMultiUserChat::class.java)
        opSetMUC?.addPresenceListener(this)
    }

    /**
     * Removes the query from protocol provider service presence listeners.
     *
     * @param pps the protocol provider service.
     */
    fun removeQueryFromProviderPresenceListeners(pps: ProtocolProviderService) {
        val opSetMUC = pps.getOperationSet(OperationSetMultiUserChat::class.java)
        opSetMUC?.removePresenceListener(this)
    }

    override fun run() {
        val chatRoomProviders = mucService.chatRoomProviders
        for (provider in chatRoomProviders) {
            providerAdded(provider, true)
        }
        if (status != ContactQuery.QUERY_CANCELED) status = ContactQuery.QUERY_COMPLETED
    }

    /**
     * Handles adding a chat room provider.
     *
     * @param provider the provider.
     * @param addQueryResult indicates whether we should add the chat room to the query results or fire an event
     * without adding it to the results.
     */
    private fun providerAdded(provider: ChatRoomProviderWrapper?, addQueryResult: Boolean) {
        for (i in 0 until provider!!.countChatRooms()) {
            val chatRoom = provider.getChatRoom(i)
            addChatRoom(provider.protocolProvider, chatRoom!!.chatRoomName,
                    chatRoom.chatRoomID, addQueryResult, chatRoom.isAutoJoin)
        }
    }

    /**
     * Handles chat room presence status updates.
     *
     * @param evt the `LocalUserChatRoomPresenceChangeEvent` instance containing the chat room
     * and the type, and reason of the change
     */
    override fun localUserPresenceChanged(evt: LocalUserChatRoomPresenceChangeEvent) {
        val sourceChatRoom = evt.getChatRoom()
        val eventType = evt.getEventType()
        var existingContact = false
        var foundContact: ChatRoomSourceContact? = null
        synchronized(contactResults) {
            for (contact in contactResults) {
                if (contactEqualsChatRoom(contact!!, sourceChatRoom)) {
                    existingContact = true
                    foundContact = contact
                    contactResults.remove(contact)
                    break
                }
            }
        }
        if (LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_JOINED == eventType) {
            if (existingContact) {
                foundContact!!.presenceStatus = ChatRoomPresenceStatus.CHAT_ROOM_ONLINE
                synchronized(contactResults) { contactResults.add(foundContact) }
                fireContactChanged(foundContact)
            } else {
                val chatRoom = mucService.findChatRoomWrapperFromChatRoom(sourceChatRoom)
                if (chatRoom != null) addChatRoom(sourceChatRoom, false, chatRoom.isAutoJoin)
            }
        } else if (LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_LEFT == eventType || LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_KICKED == eventType || (LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_DROPPED
                        == eventType)) {
            if (existingContact) {
                foundContact!!.presenceStatus = ChatRoomPresenceStatus.CHAT_ROOM_OFFLINE
                synchronized(contactResults) { contactResults.add(foundContact) }
                fireContactChanged(foundContact)
            }
        }
    }

    /**
     * Adds found result to the query results.
     *
     * @param room the chat room.
     * @param addQueryResult indicates whether we should add the chat room to the query results or fire an event
     * without adding it to the results.
     * @param isAutoJoin the auto join state of the contact.
     */
    private fun addChatRoom(room: ChatRoom?, addQueryResult: Boolean, isAutoJoin: Boolean) {
        if (queryString == null || (room!!.getName().contains(queryString)
                        || room.getName().contains(queryString))) {
            val contact = ChatRoomSourceContact(room, this, isAutoJoin)
            synchronized(contactResults) { contactResults.add(contact) }
            if (addQueryResult) {
                addQueryResult(contact, false)
            } else {
                fireContactReceived(contact, false)
            }
        }
    }

    /**
     * Adds found result to the query results.
     *
     * @param pps the protocol provider associated with the found chat room.
     * @param chatRoomName the name of the chat room.
     * @param chatRoomID the id of the chat room.
     * @param addQueryResult indicates whether we should add the chat room to the query results or fire an event
     * without adding it to the results.
     * @param isAutoJoin the auto join state of the contact.
     */
    private fun addChatRoom(pps: ProtocolProviderService?, chatRoomName: String?, chatRoomID: String?,
            addQueryResult: Boolean, isAutoJoin: Boolean) {
        if (queryString == null || chatRoomName!!.contains(queryString) || chatRoomID!!.contains(queryString)) {
            val contact = ChatRoomSourceContact(chatRoomName!!, chatRoomID, this, pps, isAutoJoin)
            synchronized(contactResults) { contactResults.add(contact) }
            if (addQueryResult) {
                addQueryResult(contact, false)
            } else {
                fireContactReceived(contact, false)
            }
        }
    }

    /**
     * Indicates that a change has occurred in the chat room data list.
     *
     * @param evt the event that describes the change.
     */
    override fun contentChanged(evt: ChatRoomListChangeEvent) {
        val chatRoom = evt!!.sourceChatRoom
        when (evt.eventID) {
            ChatRoomListChangeEvent.CHAT_ROOM_ADDED -> addChatRoom(chatRoom.chatRoom, false, chatRoom.isAutoJoin)
            ChatRoomListChangeEvent.CHAT_ROOM_REMOVED -> {
                var tmpContactResults: LinkedList<ChatRoomSourceContact?>
                synchronized(contactResults) {
                    tmpContactResults = LinkedList(contactResults)
                    for (contact in tmpContactResults) {
                        if (contactEqualsChatRoom(contact, chatRoom)) {
                            contactResults.remove(contact)
                            fireContactRemoved(contact)
                            break
                        }
                    }
                }
            }
            ChatRoomListChangeEvent.CHAT_ROOM_CHANGED -> synchronized(contactResults) {
                for (contact in contactResults) {
                    if (contactEqualsChatRoom(contact!!, chatRoom.chatRoom!!)) {
                        if (chatRoom.isAutoJoin != contact.isAutoJoin) {
                            contact.isAutoJoin = chatRoom.isAutoJoin
                            fireContactChanged(contact)
                        }
                        break
                    }
                }
            }
            else -> {}
        }
    }

    override fun chatRoomProviderWrapperAdded(provider: ChatRoomProviderWrapper) {
        providerAdded(provider, false)
    }

    override fun chatRoomProviderWrapperRemoved(provider: ChatRoomProviderWrapper) {
        var tmpContactResults: LinkedList<ChatRoomSourceContact?>
        synchronized(contactResults) {
            tmpContactResults = LinkedList(contactResults)
            for (contact in tmpContactResults) {
                if (contact!!.provider == provider.protocolProvider) {
                    contactResults.remove(contact)
                    fireContactRemoved(contact)
                }
            }
        }
    }

    /**
     * Test equality of contact to chat room. This test recognizes that chat rooms may have equal
     * names but connected to different accounts.
     *
     * @param contact the contact
     * @param chatRoom the chat room
     * @return returns `true` if they are equal, or `false` if they are different
     */
    private fun contactEqualsChatRoom(contact: ChatRoomSourceContact, chatRoom: ChatRoom): Boolean {
        return (contact.provider === chatRoom.getParentProvider()
                && chatRoom.getIdentifier().equals(contact.contactAddress))
    }

    /**
     * Test equality of contact to chat room wrapper. This method does not rely on a chat room
     * instance, since that may not be available in case of removal.
     *
     * @param contact the contact
     * @param chatRoomWrapper the chat room wrapper
     * @return returns `true` if they are equal, or `false` if they are different.
     */
    private fun contactEqualsChatRoom(contact: ChatRoomSourceContact?, chatRoomWrapper: ChatRoomWrapper): Boolean {
        return contact!!.provider === chatRoomWrapper.protocolProvider && contact!!.contactAddress == chatRoomWrapper.chatRoomID
    }

    /**
     * Returns the index of the contact in the contact results list.
     *
     * @param contact the contact.
     * @return the index of the contact in the contact results list.
     */
    @Synchronized
    fun indexOf(contact: ChatRoomSourceContact): Int {
        val it = contactResults.iterator()
        var i = 0
        while (it.hasNext()) {
            if (contact == it.next()) {
                return i
            }
            i++
        }
        return -1
    }

    /**
     * Clears any listener we used.
     */
    private fun clearListeners() {
        mucService.removeChatRoomListChangeListener(this)
        mucService.removeChatRoomProviderWrapperListener(this)
        if (protolProviderRegistrationListener != null) MUCActivator.bundleContext!!.removeServiceListener(protolProviderRegistrationListener)
        protolProviderRegistrationListener = null
        for (pps in MUCActivator.getChatRoomProviders()!!) {
            removeQueryFromProviderPresenceListeners(pps)
        }
    }

    /**
     * Cancels this `ContactQuery`.
     *
     * @see ContactQuery.cancel
     */
    override fun cancel() {
        clearListeners()
        super.cancel()
    }

    /**
     * If query has status changed to cancel, let's clear listeners.
     *
     * status [ContactQuery.QUERY_CANCELED], [ContactQuery.QUERY_COMPLETED]
     */
    override var status: Int
        get() = super.status
        set(status) {
            if (status == ContactQuery.QUERY_CANCELED) clearListeners()
            super.status = status
        }

    override fun addContactQueryListener(l: ContactQueryListener?) {
        super.addContactQueryListener(l)
        contactQueryListenersCount++
        if (contactQueryListenersCount == 1) {
            initListeners()
        }
    }

    override fun removeContactQueryListener(l: ContactQueryListener?) {
        super.removeContactQueryListener(l)
        contactQueryListenersCount--
        if (contactQueryListenersCount == 0) {
            clearListeners()
        }
    }

    /**
     * Listens for `ProtocolProviderService` registrations.
     */
    private inner class ProtocolProviderRegListener : ServiceListener {
        /**
         * Handles service change events.
         */
        override fun serviceChanged(event: ServiceEvent) {
            val serviceRef = event.serviceReference

            // if the event is caused by a bundle being stopped, we don't want to know
            if (serviceRef.bundle.state == Bundle.STOPPING) {
                return
            }
            val service = MUCActivator.bundleContext!!.getService<Any>(serviceRef as ServiceReference<Any>)
                    ?: return

            // we don't care if the source service is not a protocol provider
            when (event.type) {
                ServiceEvent.REGISTERED -> addQueryToProviderPresenceListeners(service as ProtocolProviderService)
                ServiceEvent.UNREGISTERING -> removeQueryFromProviderPresenceListeners(service as ProtocolProviderService)
            }
        }
    }
}