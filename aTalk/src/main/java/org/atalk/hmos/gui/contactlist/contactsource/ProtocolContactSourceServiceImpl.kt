/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.atalk.hmos.gui.contactlist.contactsource

import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.contactlist.MetaContactListService
import net.java.sip.communicator.service.contactsource.AsyncContactQuery
import net.java.sip.communicator.service.contactsource.ContactDetail
import net.java.sip.communicator.service.contactsource.ContactQuery
import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.contactsource.SortedGenericSourceContact
import net.java.sip.communicator.service.protocol.ContactGroup
import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import okhttp3.internal.notify
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.gui.AndroidGUIActivator
import java.util.*
import java.util.regex.Pattern

/**
 * The `ProtocolContactSourceServiceImpl`
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class ProtocolContactSourceServiceImpl
/**
 * Creates an instance of `ProtocolContactSourceServiceImpl`.
 *
 * @param protocolProvider the protocol provider which is the contact source
 * @param opSetClass the `OperationSet` class that is supported by source contacts
 */
(
        /**
         * The protocol provider, providing the contacts.
         */
        private val protocolProvider: ProtocolProviderService,
        /**
         * The operation set class, we use to filter the capabilities of the contacts.
         */
        private val opSetClass: Class<out OperationSet?>) : ContactSourceService {
    /**
     * The `MetaContactListService`, providing the meta contact list.
     */
    var metaContactListService = AndroidGUIActivator.contactListService

    /**
     * The `List` of `ProtocolContactQuery` instances which have been started and haven't stopped yet.
     */
    private val queries = LinkedList<ProtocolCQuery>()

    override val type: Int
        get() = ContactSourceService.DEFAULT_TYPE

    /**
     * Returns a user-friendly string that identifies this contact source.
     *
     * @return the display name of this contact source
     */
    override val displayName: String
        get() = ContactGroup.ROOT_GROUP_NAME + ":" + protocolProvider.accountID.displayName

    /**
     * Creates query for the given `searchPattern`.
     *
     * @param queryString the string to search for
     * @return the created query
     */
    override fun createContactQuery(queryString: String): ContactQuery {
        return createContactQuery(queryString, -1)
    }

    /**
     * Creates query for the given `searchPattern`.
     *
     * @param queryString the string to search for
     * @param contactCount the maximum count of result contacts
     * @return the created query
     */
    override fun createContactQuery(queryString: String, contactCount: Int): ContactQuery {
        var mQueryString = queryString
        if (mQueryString == null) mQueryString = ""
        val contactQuery = ProtocolCQuery(mQueryString, contactCount)
        synchronized(queries) { queries.add(contactQuery) }
        return contactQuery
    }

    /**
     * Removes query from the list.
     *
     * @param contactQuery the query
     */
    @Synchronized
    fun removeQuery(contactQuery: ContactQuery?) {
        if (queries.remove(contactQuery)) queries.notify()
    }

    /**
     * The `ProtocolCQuery` performing the query for this contact source.
     */
    private inner class ProtocolCQuery
    /**
     * Creates an instance of `ProtocolCQuery`.
     *
     * @param queryString the query string
     * @param contactCount the maximum number of contacts to return as result
     */
    (
            /**
             * The query string used for filtering the results.
             */
            override val queryString: String,
            /**
             * The maximum number of contacts to return as result.
             */
            private val contactCount: Int) : AsyncContactQuery<ProtocolContactSourceServiceImpl?>(this@ProtocolContactSourceServiceImpl,
            Pattern.compile(queryString, Pattern.CASE_INSENSITIVE or Pattern.LITERAL), true) {
        /**
         * {@inheritDoc}
         *
         *
         * Always returns `false`.
         */
        override fun phoneNumberMatches(phoneNumber: String?): Boolean {
            return false
        }

        public override fun run() {
            val contactListIter = metaContactListService.findAllMetaContactsForProvider(protocolProvider)
            while (contactListIter.hasNext()) {
                val metaContact = contactListIter.next()
                if (status == ContactQuery.QUERY_CANCELED) return
                addResultContact(metaContact)
            }
            if (status != ContactQuery.QUERY_CANCELED) status = ContactQuery.QUERY_COMPLETED
        }

        @Synchronized
        override fun start() {
            var queryHasStarted = false

            try {
                super.start()
                queryHasStarted = true
            } finally {
                if (!queryHasStarted) {
                    if (queries.remove(this)) queries.notify()
                }
            }
        }

        /**
         * Adds the result for the given group.
         *
         * @param metaContact the metaContact, which child protocol contacts we'll be adding to the result
         */
        private fun addResultContact(metaContact: MetaContact?) {
            val contacts = metaContact!!.getContactsForProvider(protocolProvider)
            while (contacts!!.hasNext()) {
                if (status == ContactQuery.QUERY_CANCELED) return
                if (contactCount in 1 until queryResultCount) break
                val contact = contacts.next()
                val contactAddress = contact!!.address
                val contactDisplayName = contact.displayName
                val queryLowerCase = queryString.lowercase(Locale.getDefault())
                if (StringUtils.isEmpty(queryString)
                        || metaContact.getDisplayName()!!.lowercase(Locale.getDefault()).contains(queryLowerCase)
                        || contactAddress.lowercase(Locale.getDefault()).contains(queryLowerCase)
                        || contactDisplayName.lowercase(Locale.getDefault()).contains(queryLowerCase)) {
                    val contactDetail = ContactDetail(contactAddress)
                    val supportedOpSets = ArrayList<Class<out OperationSet?>>()
                    supportedOpSets.add(opSetClass)
                    contactDetail.setSupportedOpSets(supportedOpSets)
                    val contactDetails = ArrayList<ContactDetail>()
                    contactDetails.add(contactDetail)
                    val sourceContact = SortedGenericSourceContact(this,
                            this@ProtocolContactSourceServiceImpl, metaContact.getDisplayName()!!,
                            contactDetails)
                    if (contactAddress != contactDisplayName) sourceContact.setDisplayDetails(contactAddress)
                    sourceContact.image = metaContact.getAvatar()!!
                    sourceContact.presenceStatus = contact.presenceStatus
                    sourceContact.contactAddress = contactAddress
                    addQueryResult(sourceContact)
                }
            }
        }
    }

    /**
     * Returns the index of the contact source in the result list.
     *
     * @return the index of the contact source in the result list
     */
    override val index: Int
        get() = 1
}