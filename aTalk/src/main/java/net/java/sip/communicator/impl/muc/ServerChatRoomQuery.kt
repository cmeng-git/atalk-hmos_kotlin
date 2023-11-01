/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.impl.muc

import net.java.sip.communicator.service.contactsource.AsyncContactQuery
import net.java.sip.communicator.service.contactsource.ContactQuery
import net.java.sip.communicator.service.contactsource.ContactQueryListener
import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapperListener
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import java.util.*
import java.util.regex.Pattern

/**
 * The `ServerChatRoomQuery` is a query over the `ServerChatRoomContactSourceService`.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class ServerChatRoomQuery(
        /**
         * The query string.
         */
        override val queryString: String, contactSource: ServerChatRoomContactSourceService?,
        provider: ChatRoomProviderWrapper?) : AsyncContactQuery<ContactSourceService?>(contactSource!!, Pattern.compile(queryString, Pattern.CASE_INSENSITIVE or Pattern.LITERAL), true), ChatRoomProviderWrapperListener {
    /**
     * List with the current results for the query.
     */
    private val contactResults = TreeSet<BaseChatRoomSourceContact>()

    /**
     * MUC service.
     */
    private val mucService: MUCServiceImpl?

    /**
     * The number of contact query listeners.
     */
    private var contactQueryListenersCount = 0

    /**
     * The provider associated with the query.
     */
    private val provider: ChatRoomProviderWrapper?

    /**
     * Creates an instance of `ChatRoomQuery` by specifying the parent contact source, the query string
     * to match and the maximum result contacts to return.
     *
     * contactSource the parent contact source
     * queryString the query string to match
     * provider the provider associated with the query
     */
    init {
        mucService = MUCActivator.mucService
        this.provider = provider
    }

    /**
     * Adds listeners for the query
     */
    private fun initListeners() {
        mucService!!.addChatRoomProviderWrapperListener(this)
    }

    override fun run() {
        if (provider == null) {
            val chatRoomProviders = mucService!!.chatRoomProviders
            for (provider in chatRoomProviders) {
                providerAdded(provider, true)
            }
        } else {
            providerAdded(provider, true)
        }
        if (status != ContactQuery.QUERY_CANCELED) status = ContactQuery.QUERY_COMPLETED
    }

    /**
     * Handles adding a chat room provider.
     *
     * @param provider the provider.
     * @param addQueryResult indicates whether we should add the chat room to the query results or fire
     * an event without adding it to the results.
     */
    private fun providerAdded(provider: ChatRoomProviderWrapper?, addQueryResult: Boolean) {
        val pps = provider!!.protocolProvider
        val chatRoomNames = MUCActivator.mucService.getExistingChatRooms(provider)
                ?: return

        // Already create all the BaseChatRoomSourceContact instances since all
        // the data is already available.
        val chatRooms = HashSet<BaseChatRoomSourceContact>(chatRoomNames.size)
        for (name in chatRoomNames) {
            chatRooms.add(BaseChatRoomSourceContact(name, name, this, pps))
        }
        addChatRooms(pps, chatRooms, addQueryResult)
    }

    /**
     * Adds found result to the query results.
     *
     * @param pps the protocol provider associated with the found chat room.
     * @param chatRoomName the name of the chat room.
     * @param chatRoomID the id of the chat room.
     * @param addQueryResult indicates whether we should add the chat room to the query results or fire
     * an event without adding it to the results.
     */
    private fun addChatRoom(pps: ProtocolProviderService, chatRoomName: String, chatRoomID: String, addQueryResult: Boolean) {
        if ((queryString == null || chatRoomName.contains(queryString) || chatRoomID.contains(queryString))
                && isMatching(chatRoomID, pps)) {
            val contact = BaseChatRoomSourceContact(chatRoomName, chatRoomID, this, pps)
            synchronized(contactResults) { contactResults.add(contact) }
            if (addQueryResult) {
                addQueryResult(contact, false)
            } else {
                fireContactReceived(contact, false)
            }
        }
    }

    /**
     * Adds found results to the query results.
     *
     * @param pps the protocol provider associated with the found chat room.
     * @param chatRooms The set of chat rooms based on BaseChatRoomSourceContact. This is the full set and
     * it will be filtered according to demands of the queryString.
     * @param addQueryResult indicates whether we should add the chat room to the query results or fire
     * an event without adding it to the results.
     */
    private fun addChatRooms(pps: ProtocolProviderService?, chatRooms: MutableSet<BaseChatRoomSourceContact>,
            addQueryResult: Boolean) {
        var room: BaseChatRoomSourceContact
        val iterator = chatRooms.iterator()
        while (iterator.hasNext()) {
            room = iterator.next()

            // Notice the NOT operator at the start ...
            if (!((queryString == null || (room.chatRoomName.contains(queryString)
                            || room.chatRoomID!!.contains(queryString))) && isMatching(room.chatRoomID, pps))) {
                iterator.remove()
            }
        }
        synchronized(contactResults) { contactResults.addAll(chatRooms) }
        if (addQueryResult) {
            addQueryResults(chatRooms)
        } else {
            // TODO Need something to fire one event for multiple contacts.
            for (contact in chatRooms) {
                fireContactReceived(contact, false)
            }
        }
    }

    override fun chatRoomProviderWrapperAdded(provider: ChatRoomProviderWrapper) {
        providerAdded(provider, false)
    }

    override fun chatRoomProviderWrapperRemoved(provider: ChatRoomProviderWrapper) {
        var tmpContactResults: LinkedList<BaseChatRoomSourceContact>
        synchronized(contactResults) {
            tmpContactResults = LinkedList(contactResults)
            for (contact in tmpContactResults) {
                if (contact.provider == provider.protocolProvider) {
                    contactResults.remove(contact)
                    fireContactRemoved(contact)
                }
            }
        }
    }

    /**
     * Clears any listener we used.
     */
    private fun clearListeners() {
        mucService!!.removeChatRoomProviderWrapperListener(this)
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
     * Checks if the contact should be added to results or not.
     *
     * @param chatRoomID the chat room id associated with the contact.
     * @param pps the provider of the chat room contact.
     * @return `true` if the result should be added to the results and `false` if not.
     */
    private fun isMatching(chatRoomID: String?, pps: ProtocolProviderService?): Boolean {
        return MUCActivator.mucService.findChatRoomWrapperFromChatRoomID(chatRoomID!!, pps) == null
    }

    /**
     * Returns the index of the contact in the contact results list.
     *
     * @param contact the contact.
     * @return the index of the contact in the contact results list.
     */
    fun indexOf(contact: BaseChatRoomSourceContact): Int {
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
}