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
import net.java.sip.communicator.service.callhistory.CallRecord
import java.util.*

/**
 * The `CallRecordEvent` indicates that a `CallRecord` has been
 * received as a result of a `CallHistoryQuery`.
 *
 * @author Yana Stamcheva
 */
class CallRecordEvent
/**
 * Creates a `CallRecordEvent` by specifying the parent `query`
 * and the `callRecord` this event is about.
 *
 * @param query the source that triggered this event
 * @param callRecord the `CallRecord` this event is about
 */
(query: CallHistoryQuery?,
    /**
     * The `CallRecord` this event is about.
     */
    private val callRecord: CallRecord) : EventObject(query) {
    /**
     * Returns the `ContactQuery` that triggered this event.
     *
     * @return the `ContactQuery` that triggered this event
     */
    fun getQuerySource(): CallHistoryQuery {
        return source as CallHistoryQuery
    }

    /**
     * Returns the `CallRecord`s this event is about.
     *
     * @return the `CallRecord`s this event is about
     */
    fun getCallRecord(): CallRecord {
        return callRecord
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}