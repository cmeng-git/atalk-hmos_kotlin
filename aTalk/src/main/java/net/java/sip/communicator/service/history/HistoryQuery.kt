/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history

import net.java.sip.communicator.service.history.event.HistoryQueryListener
import net.java.sip.communicator.service.history.records.HistoryRecord

/**
 * The `HistoryQuery` corresponds to a query made through the
 * `InteractiveHistoryReader`. It allows to be canceled, to listen for changes in the
 * results and to obtain initial results if available.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface HistoryQuery {
    /**
     * Cancels this query.
     */
    fun cancel()

    /**
     * Returns the query string, this query was created for.
     *
     * @return the query string, this query was created for
     */
    fun getQueryString(): String?

    /**
     * Returns a collection of the results for this query. It's up to the implementation to
     * determine how and when to fill this list of results.
     *
     *
     * This method could be used in order to obtain first fast initial results and then obtain the
     * additional results through the `HistoryQueryListener`, which should improve user
     * experience when waiting for results.
     *
     * @return a collection of the initial results for this query
     */
    fun getHistoryRecords(): Collection<HistoryRecord?>?

    /**
     * Adds the given `HistoryQueryListener` to the list of listeners interested in query
     * result changes.
     *
     * @param l
     * the `HistoryQueryListener` to add
     */
    fun addHistoryRecordsListener(l: HistoryQueryListener?)

    /**
     * Removes the given `HistoryQueryListener` from the list of listeners interested in
     * query result changes.
     *
     * @param l
     * the `HistoryQueryListener` to remove
     */
    fun removeHistoryRecordsListener(l: HistoryQueryListener?)
}