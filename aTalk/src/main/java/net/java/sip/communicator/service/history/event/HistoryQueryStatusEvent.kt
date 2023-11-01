/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history.event

import net.java.sip.communicator.service.history.HistoryQuery
import java.util.*

/**
 * The `HistoryQueryStatusEvent` is triggered each time a `HistoryQuery` changes its status. Possible
 * statuses are: QUERY_COMPLETED, QUERY_CANCELED and QUERY_ERROR.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class HistoryQueryStatusEvent
/**
 * Creates a `HistoryQueryStatusEvent` by specifying the source `HistoryQuery` and the
 * `eventType` indicating why initially this event occurred.
 *
 * @param source
 * the `HistoryQuery` this event is about
 * @param eventType
 * the type of the event. One of the QUERY_XXX constants defined in this class
 */
(source: HistoryQuery?,
        /**
         * Indicates the type of this event.
         */
        val eventType: Int) : EventObject(source) {
    /**
     * Returns the type of this event.
     *
     * @return the type of this event
     */

    /**
     * Returns the `HistoryQuery` that triggered this event.
     *
     * @return the `HistoryQuery` that triggered this event
     */
    val querySource: HistoryQuery
        get() = source as HistoryQuery

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Indicates that a query has been completed.
         */
        const val QUERY_COMPLETED = 0

        /**
         * Indicates that a query has been canceled.
         */
        const val QUERY_CANCELED = 1

        /**
         * Indicates that a query has been stopped because of an error.
         */
        const val QUERY_ERROR = 2
    }
}