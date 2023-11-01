/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event

import java.util.*

/**
 * The `MetaContactQueryStatusEvent` is triggered each time a
 * `MetaContactQuery` changes its status. Possible statuses are:
 * QUERY_COMPLETED, QUERY_CANCELED and QUERY_ERROR.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class MetaContactQueryStatusEvent
/**
 * Creates a `MetaContactQueryStatusEvent` by specifying the source
 * `MetaContactQuery` and the `eventType` indicating why initially this event occurred.
 *
 * @param source the initiator of the event
 * @param eventType the type of the event. One of the QUERY_XXX constants defined in this class
 */
(source: MetaContactQuery?,
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
     * Returns the `ContactQuery` that triggered this event.
     *
     * @return the `ContactQuery` that triggered this event
     */
    val querySource: MetaContactQuery
        get() = source as MetaContactQuery

    companion object {
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