/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.hmos.gui.contactlist.contactsource

import net.java.sip.communicator.service.contactsource.AbstractContactQuery
import net.java.sip.communicator.service.contactsource.ContactDetail
import net.java.sip.communicator.service.contactsource.ContactQuery
import net.java.sip.communicator.service.contactsource.ContactQuery.Companion.QUERY_CANCELED
import net.java.sip.communicator.service.contactsource.ContactQuery.Companion.QUERY_COMPLETED
import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.contactsource.ContactSourceService.Companion.SEARCH_TYPE
import net.java.sip.communicator.service.contactsource.GenericSourceContact
import net.java.sip.communicator.service.contactsource.SourceContact
import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp

/**
 * The `StringContactSourceServiceImpl` is an implementation of the
 * `ContactSourceService` that returns the searched string as a result contact.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class StringContactSourceServiceImpl(protocolProvider: ProtocolProviderService,
        opSet: Class<out OperationSet?>) : ContactSourceService {
    /**
     * The protocol provider to be used with this string contact source.
     */
    private val protocolProvider: ProtocolProviderService

    /**
     * The operation set supported by this string contact source.
     */
    private val opSetClass: Class<out OperationSet?>

    /**
     * Can display disable adding display details for source contacts.
     */
    private var disableDisplayDetails = true

    /**
     * Creates an instance of `StringContactSourceServiceImpl`.
     *
     * protocolProvider the protocol provider to be used with this string
     * contact source
     * opSet the operation set supported by this string contact source
     */
    init {
        this.protocolProvider = protocolProvider
        opSetClass = opSet
    }

    /**
     * Returns the type of this contact source.
     *
     * @return the type of this contact source
     */
    override val type: Int
        get() = SEARCH_TYPE

    /**
     * Returns a user-friendly string that identifies this contact source.
     *
     * @return the display name of this contact source
     */
    override val displayName: String
        get() = aTalkApp.getResString(R.string.service_gui_SEARCH_STRING_CONTACT_SOURCE)

    /**
     * Creates query for the given `queryString`.
     *
     * queryString the string to search for
     * @return the created query
     */
    override fun createContactQuery(queryString: String): ContactQuery {
        return createContactQuery(queryString, -1)
    }

    /**
     * Creates query for the given `queryString`.
     *
     * queryString the string to search for
     * contactCount the maximum count of result contacts
     * @return the created query
     */
    override fun createContactQuery(queryString: String, contactCount: Int): ContactQuery {
        return StringQuery(queryString)
    }

    /**
     * Changes whether to add display details for contact sources.
     *
     * disableDisplayDetails
     */
    fun setDisableDisplayDetails(disableDisplayDetails: Boolean) {
        this.disableDisplayDetails = disableDisplayDetails
    }

    /**
     * Returns the source contact corresponding to the query string.
     *
     * @return the source contact corresponding to the query string
     */
    fun createSourceContact(queryString: String): SourceContact {
        val contactDetails = ArrayList<ContactDetail>()
        val contactDetail = ContactDetail(queryString)

        // Init supported operation sets.
        val supportedOpSets = ArrayList<Class<out OperationSet?>>()
        supportedOpSets.add(opSetClass)
        contactDetail.setSupportedOpSets(supportedOpSets)

        // Init preferred protocol providers.
        val providers = HashMap<Class<out OperationSet?>, ProtocolProviderService>()
        providers[opSetClass] = protocolProvider
        contactDetail.setPreferredProviders(providers)
        contactDetails.add(contactDetail)
        val sourceContact = GenericSourceContact(this@StringContactSourceServiceImpl, queryString, contactDetails)
        if (disableDisplayDetails) {
            sourceContact.setDisplayDetails(aTalkApp.getResString(
                    R.string.service_gui_CALL_VIA, protocolProvider.accountID.displayName))
        }
        return sourceContact
    }

    /**
     * The query implementation.
     */
    private inner class StringQuery(
            /**
             * The query string.
             */
            override val queryString: String) : AbstractContactQuery<ContactSourceService?>(this@StringContactSourceServiceImpl) {
        /**
         * Returns the query string.
         *
         * @return the query string
         */

        /**
         * The query result list.
         */
        private val results: MutableList<SourceContact>

        /**
         * Creates an instance of this query implementation.
         *
         * queryString the string to query
         */
        init {
            results = ArrayList()
        }

        /**
         * Returns the list of query results.
         *
         * @return the list of query results
         */
        override val queryResults: MutableList<SourceContact>
            get() = results

        override fun start() {
            val contact = createSourceContact(queryString)
            results.add(contact)
            fireContactReceived(contact)
            if (QUERY_CANCELED != status) status = QUERY_COMPLETED
        }
    }

    /**
     * Returns the index of the contact source in the result list.
     *
     * @return the index of the contact source in the result list
     */
    override val index: Int
        get() = 0
}