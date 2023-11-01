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
package net.java.sip.communicator.service.callhistory.event

import net.java.sip.communicator.service.callhistory.CallHistoryQuery
import java.util.*

/**
 * The `CallHistoryQueryStatusEvent` is triggered each time a
 * `CallHistoryQuery` changes its status. Possible statuses are:
 * QUERY_COMPLETED, QUERY_CANCELED and QUERY_ERROR.
 *
 * @author Yana Stamcheva
 */
class CallHistoryQueryStatusEvent
/**
 * Creates a `CallHistoryQueryStatusEvent` by specifying the source
 * `CallHistoryQuery` and the `eventType` indicating why initially this event occurred.
 *
 * @param source the `CallHistoryQuery` this event is about
 * @param eventType the type of the event. One of the QUERY_XXX constants defined in the `CallHistoryQuery`
 */
(source: CallHistoryQuery?,
    /**
     * Indicates the type of this event.
     */
    private val eventType: Int) : EventObject(source) {
    /**
     * Returns the `CallHistoryQuery` that triggered this event.
     *
     * @return the `CallHistoryQuery` that triggered this event
     */
    fun getQuerySource(): CallHistoryQuery {
        return source as CallHistoryQuery
    }

    /**
     * Returns the type of this event.
     *
     * @return the type of this event
     */
    fun getEventType(): Int {
        return eventType
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}