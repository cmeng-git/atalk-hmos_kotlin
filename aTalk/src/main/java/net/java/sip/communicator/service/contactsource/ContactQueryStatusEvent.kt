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
package net.java.sip.communicator.service.contactsource

import java.util.*

/**
 * The `ContactQueryStatusEvent` is triggered each time a
 * `ContactQuery` changes its status. Possible statuses are:
 * QUERY_COMPLETED, QUERY_CANCELED and QUERY_ERROR.
 *
 * @author Yana Stamcheva
 */
class ContactQueryStatusEvent
/**
 * Creates a `ContactQueryStatusEvent` by specifying the source
 * `ContactQuery` and the `eventType` indicating why initially
 * this event occurred.
 * @param source the initiator of the event
 * @param eventType the type of the event. One of the QUERY_XXX constants
 * defined in this class
 */
(source: ContactQuery?,
        /**
         * Indicates the type of this event.
         */
        val eventType: Int) : EventObject(source) {
    /**
     * Returns the type of this event.
     * @return the type of this event
     */

    /**
     * Returns the `ContactQuery` that triggered this event.
     * @return the `ContactQuery` that triggered this event
     */
    val querySource: ContactQuery
        get() = source as ContactQuery

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