/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history.event

/**
 * The `HistoryQueryListener` listens for changes in the result of a given `HistoryQuery`. When a query to
 * the history is started, this listener would be notified every time new results are available for this query.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface HistoryQueryListener {
    /**
     * Indicates that new `HistoryRecord` has been received as a result of the query.
     *
     * @param event
     * the `HistoryRecordEvent` containing information about the query results.
     */
    fun historyRecordReceived(event: HistoryRecordEvent)

    /**
     * Indicates that the status of the history has changed.
     *
     * @param event
     * the `HistoryQueryStatusEvent` containing information about the status change
     */
    fun queryStatusChanged(event: HistoryQueryStatusEvent)
}