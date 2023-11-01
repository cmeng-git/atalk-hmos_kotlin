/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.history

import net.java.sip.communicator.service.history.HistoryQuery
import net.java.sip.communicator.service.history.event.HistoryQueryListener
import net.java.sip.communicator.service.history.event.HistoryQueryStatusEvent
import net.java.sip.communicator.service.history.event.HistoryRecordEvent
import net.java.sip.communicator.service.history.records.HistoryRecord
import java.util.*

/**
 * The `HistoryQueryImpl` is an implementation of the `HistoryQuery` interface. It
 * corresponds to a query made through the `InteractiveHistoryReader`. It allows to be
 * canceled, to listen for changes in the results and to obtain initial results if available.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class HistoryQueryImpl
/**
 * Creates an instance of `HistoryQueryImpl` by specifying the query string it was
 * created for.
 *
 * @param queryString
 * the query string we're looking for in this query
 */
    (
        /**
         * The query string we're looking for in this query.
         */
        private val queryString: String,
) : HistoryQuery {
    /**
     * The list of query listeners registered in this query.
     */
    private val queryListeners = LinkedList<HistoryQueryListener?>()

    /**
     * The list of history records, which is the result of this query.
     */
    private val historyRecords = Vector<HistoryRecord?>()

    /**
     * Indicates if this query has been canceled.
     */
    var isCanceled = false
        private set

    /**
     * Cancels this query.
     */
    override fun cancel() {
        isCanceled = true
    }

    /**
     * Returns a collection of the results for this query. It's up to the implementation to
     * determine how and when to ill this list of results.
     *
     *
     * This method could be used in order to obtain first fast initial results and then obtain the
     * additional results through the `HistoryQueryListener`, which should improve user
     * experience when waiting for results.
     *
     * @return a collection of the initial results for this query
     */
    override fun getHistoryRecords(): Collection<HistoryRecord?> {
        return Vector(historyRecords)
    }

    /**
     * Adds the given `HistoryQueryListener` to the list of listeners interested in query result changes.
     *
     * @param l the `HistoryQueryListener` to add
     */
    override fun addHistoryRecordsListener(l: HistoryQueryListener?) {
        synchronized(queryListeners) { queryListeners.add(l) }
    }

    /**
     * Removes the given `HistoryQueryListener` from the list of listeners interested in query result changes.
     *
     * @param l the `HistoryQueryListener` to remove
     */
    override fun removeHistoryRecordsListener(l: HistoryQueryListener?) {
        synchronized(queryListeners) { queryListeners.remove(l) }
    }

    /**
     * Adds the given `HistoryRecord` to the result list of this query and notifies all
     * interested listeners that a new record is received.
     *
     * @param record the `HistoryRecord` to add
     */
    fun addHistoryRecord(record: HistoryRecord) {
        historyRecords.add(record)
        fireQueryEvent(record)
    }

    /**
     * Sets this query status to the given `queryStatus` and notifies all interested listeners of the change.
     *
     * @param queryStatus the new query status to set
     */
    fun setStatus(queryStatus: Int) {
        fireQueryStatusEvent(queryStatus)
    }

    /**
     * Notifies all registered `HistoryQueryListener`s that a new record has been received.
     *
     * @param record  the `HistoryRecord`
     */
    private fun fireQueryEvent(record: HistoryRecord) {
        val event = HistoryRecordEvent(this, record)
        synchronized(queryListeners) {
            for (l in queryListeners) {
                l!!.historyRecordReceived(event)
            }
        }
    }

    /**
     * Notifies all registered `HistoryQueryListener`s that a new status has been received.
     *
     * @param newStatus  the new status
     */
    private fun fireQueryStatusEvent(newStatus: Int) {
        val event = HistoryQueryStatusEvent(this, newStatus)
        synchronized(queryListeners) {
            for (l in queryListeners) {
                l!!.queryStatusChanged(event)
            }
        }
    }

    /**
     * Returns the query string, this query was created for.
     *
     * @return the query string, this query was created for
     */
    override fun getQueryString(): String {
        return queryString
    }
}