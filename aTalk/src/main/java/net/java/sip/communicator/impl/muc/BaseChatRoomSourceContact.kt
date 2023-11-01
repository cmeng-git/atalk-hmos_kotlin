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

import net.java.sip.communicator.service.contactsource.ContactDetail
import net.java.sip.communicator.service.contactsource.ContactQuery
import net.java.sip.communicator.service.contactsource.SortedGenericSourceContact
import net.java.sip.communicator.service.muc.ChatRoomPresenceStatus
import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService

/**
 * Basic source contact for the chat rooms.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
open class BaseChatRoomSourceContact(
        /**
         * The name of the chat room associated with the contact.
         */
        val chatRoomName: String,
        /**
         * The ID of the chat room associated with the contact.
         */
        val chatRoomID: String?,
        /**
         * The parent contact query.
         */
        protected val parentQuery: ContactQuery,
        /**
         * The protocol provider of the chat room associated with the contact.
         */
        val provider: ProtocolProviderService?)
    : SortedGenericSourceContact(parentQuery, parentQuery.contactSource, chatRoomName, generateDefaultContactDetails(chatRoomName)) {

    /**
     * Returns the name of the chat room associated with the contact.
     *
     * @return the chat room name
     */
    /**
     * Returns the id of the chat room associated with the contact.
     *
     * @return the chat room id.
     */
    /**
     * Returns the provider of the chat room associated with the contact.
     *
     * @return the provider
     */

    /**
     * Constructs new chat room source contact.
     */
    init {
        initContactProperties(ChatRoomPresenceStatus.CHAT_ROOM_OFFLINE)
        setDisplayDetails(provider!!.accountID.displayName!!)
    }

    /**
     * Sets the given presence status and the name of the chat room associated with the contact.
     *
     * @param status the presence status to be set.
     */
    protected fun initContactProperties(status: PresenceStatus) {
        presenceStatus = status
        contactAddress = chatRoomName
    }

    /**
     * Returns the index of this source contact in its parent group.
     *
     * @return the index of this contact in its parent
     */
    override val index: Int
        get() = if (parentQuery is ServerChatRoomQuery) parentQuery.indexOf(this) else -1

    companion object {
        /**
         * Generates the default contact details for `BaseChatRoomSourceContact` instances.
         *
         * @param chatRoomName the name of the chat room associated with the contact
         * @return list of default `ContactDetail`s for the contact.
         */
        private fun generateDefaultContactDetails(chatRoomName: String): List<ContactDetail> {
            val contactDetail = ContactDetail(chatRoomName)
            val supportedOpSets = ArrayList<Class<out OperationSet?>>()
            supportedOpSets.add(OperationSetMultiUserChat::class.java)
            contactDetail.setSupportedOpSets(supportedOpSets)
            val contactDetails = ArrayList<ContactDetail>()
            contactDetails.add(contactDetail)
            return contactDetails
        }
    }
}