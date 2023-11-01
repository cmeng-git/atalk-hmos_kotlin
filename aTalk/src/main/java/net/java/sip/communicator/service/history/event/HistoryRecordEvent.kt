/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history.event

import net.java.sip.communicator.service.history.HistoryQuery
import net.java.sip.communicator.service.history.records.HistoryRecord
import java.util.*

/**
 * The `HistoryRecordEvent` indicates that a `HistoryRecord`s has been received as a result of a
 * `HistoryQuery`.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class HistoryRecordEvent
/**
 * Creates a `HistoryRecordEvent` by specifying the initial query and the record this event is about.
 *
 * @param query the source that triggered this event
 * @param historyRecord the `HistoryRecord` this event is about
 */
(query: HistoryQuery?,
        /**
         * The `HistoryRecord` this event is about.
         */
        val historyRecord: HistoryRecord) : EventObject(query) {
    /**
     * Returns the `HistoryRecord`s this event is about.
     *
     * @return the `HistoryRecord`s this event is about
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
    }
}