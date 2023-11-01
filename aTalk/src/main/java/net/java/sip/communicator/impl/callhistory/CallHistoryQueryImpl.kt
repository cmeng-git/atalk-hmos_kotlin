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
import net.java.sip.communicator.service.history.HistoryQuery
import net.java.sip.communicator.service.history.event.HistoryQueryListener
import net.java.sip.communicator.service.history.event.HistoryQueryStatusEvent
import net.java.sip.communicator.service.history.event.HistoryRecordEvent
import java.util.*

/**
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class CallHistoryQueryImpl(private val historyQuery: HistoryQuery) : CallHistoryQuery {
    private val queryListeners = LinkedList<CallHistoryQueryListener?>()
    private val callRecords = Vector<CallRecord?>()

    /**
     * Creates an instance of `CallHistoryQueryImpl` by specifying the underlying
     * `HistoryQuery`.
     *
     * @param query
     * the underlying `HistoryQuery` this query is based on
     */
    init {
        historyQuery.addHistoryRecordsListener(object : HistoryQueryListener {
            override fun historyRecordReceived(event: HistoryRecordEvent) {
                val callRecord = CallHistoryServiceImpl.createCallRecordFromProperties(event.historyRecord.properties)
                callRecords.add(callRecord)
                fireQueryEvent(callRecord)
            }

            override fun queryStatusChanged(event: HistoryQueryStatusEvent) {
                fireQueryStatusEvent(event.eventType)
            }
        })
        for (historyRecord in historyQuery.getHistoryRecords()!!) {
            val callRecord = CallHistoryServiceImpl.createCallRecordFromProperties(historyRecord!!.properties)
            callRecords.add(callRecord)
        }
    }

    /**
     * Cancels this query.
     */
    override fun cancel() {
        historyQuery.cancel()
    }

    /**
     * Returns a collection of the results for this query. It's up to the implementation to
     * determine how and when to fill this list of results.
     *
     *
     * This method could be used in order to obtain first fast initial results and then obtain
     * the additional results through the `CallHistoryQueryListener`, which should improve
     * user experience when waiting for results.
     *
     * @return a collection of the initial results for this query
     */
    override fun getCallRecords(): Collection<CallRecord?> {
        return Vector(callRecords)
    }

    /**
     * Adds the given `CallHistoryQueryListener` to the list of listeners interested in
     * query result changes.
     *
     * @param l
     * the `CallHistoryQueryListener` to add
     */
    override fun addQueryListener(l: CallHistoryQueryListener?) {
        synchronized(queryListeners) { queryListeners.add(l) }
    }

    /**
     * Removes the given `CallHistoryQueryListener` from the list of listeners interested
     * in query result changes.
     *
     * @param l
     * the `CallHistoryQueryListener` to remove
     */
    override fun removeQueryListener(l: CallHistoryQueryListener?) {
        synchronized(queryListeners) { queryListeners.remove(l) }
    }

    /**
     * Notifies all registered `HistoryQueryListener`s that a new record has been received.
     *
     * @param record
     * the `HistoryRecord`
     */
    private fun fireQueryEvent(record: CallRecord) {
        val event = CallRecordEvent(this, record)
        synchronized(queryListeners) { for (l in queryListeners) l!!.callRecordReceived(event) }
    }

    /**
     * Notifies all registered `HistoryQueryListener`s that a new status has been received.
     *
     * @param newStatus
     * the new status
     */
    private fun fireQueryStatusEvent(newStatus: Int) {
        val event = CallHistoryQueryStatusEvent(this, newStatus)
        synchronized(queryListeners) { for (l in queryListeners) l!!.queryStatusChanged(event) }
    }

    /**
     * Returns the query string, this query was created for.
     *
     * @return the query string, this query was created for
     */
    override fun getQueryString(): String {
        return historyQuery.getQueryString()!!
    }

    /**
     * Adds the given `CallRecord` to the result list of this query and notifies all
     * interested listeners that a new record is received.
     *
     * @param callRecord
     * the `CallRecord` to add
     */
    fun addHistoryRecord(callRecord: CallRecord) {
        callRecords.add(callRecord)
        fireQueryEvent(callRecord)
    }
}