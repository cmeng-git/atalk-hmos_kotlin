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
package net.java.sip.communicator.impl.callhistory

import net.java.sip.communicator.service.callhistory.CallHistoryQuery
import net.java.sip.communicator.service.callhistory.CallRecord
import net.java.sip.communicator.service.callhistory.event.CallHistoryQueryListener
import net.java.sip.communicator.service.callhistory.event.CallHistoryQueryStatusEvent
import net.java.sip.communicator.service.callhistory.event.CallRecordEvent
import net.java.sip.communicator.service.contactsource.ContactQuery
import net.java.sip.communicator.service.contactsource.ContactQueryListener
import net.java.sip.communicator.service.contactsource.ContactQueryStatusEvent
import net.java.sip.communicator.service.contactsource.ContactReceivedEvent
import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.contactsource.SourceContact
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import java.util.*

/**
 * The `CallHistoryContactSource` is the contact source for the call history.
 *
 * @author Yana Stamcheva
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class CallHistoryContactSource : ContactSourceService {
    override val type: Int
        get() = ContactSourceService.HISTORY_TYPE

    /**
     * Returns the display name of this contact source.
     *
     * @return the display name of this contact source
     */
    override val displayName: String
        get() = aTalkApp.getResString(R.string.service_gui_CALL_HISTORY_GROUP_NAME)

    /**
     * Creates query for the given `searchString`.
     *
     * @param queryString the string to search for
     * @return the created query
     */
    override fun createContactQuery(queryString: String): ContactQuery {
        return createContactQuery(queryString, 50)
    }

    /**
     * Creates query for the given `searchString`.
     *
     * @param queryString the string to search for
     * @param contactCount the maximum count of result contacts
     * @return the created query
     */
    override fun createContactQuery(queryString: String, contactCount: Int): ContactQuery {
        return if (queryString != null && queryString.isNotEmpty()) {
            CallHistoryContactQuery(CallHistoryActivator.getCallHistoryService()!!
                    .findByPeer(queryString, contactCount))
        } else {
            CallHistoryContactQuery(CallHistoryActivator.getCallHistoryService()!!.findLast(contactCount))
        }
    }

    /**
     * The `CallHistoryContactQuery` contains information about a current
     * query to the contact source.
     */
    private inner class CallHistoryContactQuery : ContactQuery {
        /**
         * A list of all registered query listeners.
         */
        private val queryListeners = LinkedList<ContactQueryListener?>()

        /**
         * A list of all source contact results.
         */
        private val sourceContacts = LinkedList<SourceContact>()

        /**
         * The underlying `CallHistoryQuery`, on which this
         * `ContactQuery` is based.
         */
        private var callHistoryQuery: CallHistoryQuery? = null
        /**
         * Returns the status of this query. One of the static constants defined
         * in this class.
         *
         * @return the status of this query
         */
        /**
         * Indicates the status of this query. When created this query is in rogress.
         */
        override var status = ContactQuery.QUERY_IN_PROGRESS
            private set

        /**
         * Iterator for the queried contacts.
         */
        var recordsIter: Iterator<CallRecord?>? = null

        /**
         * Indicates whether show more label should be displayed or not.
         */
        private var showMoreLabelAllowed = true

        /**
         * Creates an instance of `CallHistoryContactQuery` by specifying the list of call records results.
         *
         * @param callRecords the list of call records, which are the result of this query
         */
        constructor(callRecords: Collection<CallRecord>) {
            recordsIter = callRecords.iterator()
            val recordsIter = callRecords.iterator()
            while (recordsIter.hasNext() && status != ContactQuery.QUERY_CANCELED) {
                sourceContacts.add(
                        CallHistorySourceContact(this@CallHistoryContactSource, recordsIter.next()))
            }
            showMoreLabelAllowed = false
        }

        override fun start() {
            if (callHistoryQuery != null) {
                callHistoryQuery!!.addQueryListener(object : CallHistoryQueryListener {
                    override fun callRecordReceived(event: CallRecordEvent?) {
                        if (status == ContactQuery.QUERY_CANCELED) return
                        val contact = CallHistorySourceContact(
                                this@CallHistoryContactSource, event!!.getCallRecord())
                        sourceContacts.add(contact)
                        fireQueryEvent(contact)
                    }

                    override fun queryStatusChanged(event: CallHistoryQueryStatusEvent?) {
                        status = event!!.getEventType()
                        fireQueryStatusEvent(status)
                    }
                })
                recordsIter = callHistoryQuery!!.getCallRecords()!!.iterator()
            }
            while (recordsIter!!.hasNext()) {
                val contact = CallHistorySourceContact(
                        this@CallHistoryContactSource, recordsIter!!.next()!!)
                sourceContacts.add(contact)
                fireQueryEvent(contact)
            }
            if (status != ContactQuery.QUERY_CANCELED) {
                status = ContactQuery.QUERY_COMPLETED
                if (callHistoryQuery == null) fireQueryStatusEvent(status)
            }
        }

        /**
         * Creates an instance of `CallHistoryContactQuery` based on the
         * given `callHistoryQuery`.
         *
         * @param callHistoryQuery the query used to track the call history
         */
        constructor(callHistoryQuery: CallHistoryQuery?) {
            this.callHistoryQuery = callHistoryQuery
        }

        /**
         * Adds the given `ContactQueryListener` to the list of query
         * listeners.
         *
         * @param l the `ContactQueryListener` to add
         */
        override fun addContactQueryListener(l: ContactQueryListener?) {
            synchronized(queryListeners) { queryListeners.add(l) }
        }

        /**
         * This query could not be canceled.
         */
        override fun cancel() {
            status = ContactQuery.QUERY_CANCELED
            if (callHistoryQuery != null) callHistoryQuery!!.cancel()
        }

        /**
         * Removes the given `ContactQueryListener` from the list of
         * query listeners.
         *
         * @param l the `ContactQueryListener` to remove
         */
        override fun removeContactQueryListener(l: ContactQueryListener?) {
            synchronized(queryListeners) { queryListeners.remove(l) }
        }

        /**
         * Returns a list containing the results of this query.
         *
         * @return a list containing the results of this query
         */
        override val queryResults: MutableCollection<SourceContact>
            get() = sourceContacts

        /**
         * Returns the `ContactSourceService`, where this query was first
         * initiated.
         *
         * @return the `ContactSourceService`, where this query was first
         * initiated
         */
        override val contactSource: ContactSourceService
            get() = this@CallHistoryContactSource

        /**
         * Notifies all registered `ContactQueryListener`s that a new
         * contact has been received.
         *
         * @param contact the `SourceContact` this event is about
         */
        private fun fireQueryEvent(contact: SourceContact) {
            val event = ContactReceivedEvent(this, contact, showMoreLabelAllowed)
            var listeners: Collection<ContactQueryListener?>
            synchronized(queryListeners) { listeners = ArrayList(queryListeners) }
            for (l in listeners) l!!.contactReceived(event)
        }

        /**
         * Notifies all registered `ContactQueryListener`s that a new
         * record has been received.
         *
         * @param newStatus the new status
         */
        private fun fireQueryStatusEvent(newStatus: Int) {
            var listeners: Collection<ContactQueryListener?>
            val event = ContactQueryStatusEvent(this, newStatus)
            synchronized(queryListeners) { listeners = ArrayList(queryListeners) }
            for (l in listeners) l!!.queryStatusChanged(event)
        }

        override val queryString: String
            get() = callHistoryQuery!!.getQueryString()
    }

    /**
     * Returns the index of the contact source in the result list.
     *
     * @return the index of the contact source in the result list
     */
    override val index: Int
        get() = -1
}