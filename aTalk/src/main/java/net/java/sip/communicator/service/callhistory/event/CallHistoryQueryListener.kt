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

/**
 * The `CallHistoryQueryListener` listens for changes in the result of
 * a given `CallHistoryQuery`. When a query to the call history is
 * started, this listener would be notified every time new results are available for this query.
 *
 * @author Yana Stamcheva
 */
interface CallHistoryQueryListener {
    /**
     * Indicates that new `CallRecord` is received as a result of the query.
     *
     * @param event the `CallRecordsEvent` containing information about the query results.
     */
    fun callRecordReceived(event: CallRecordEvent?)

    /**
     * Indicates that the status of the history has changed.
     *
     * @param event the `HistoryQueryStatusEvent` containing information about the status change
     */
    fun queryStatusChanged(event: CallHistoryQueryStatusEvent?)
}