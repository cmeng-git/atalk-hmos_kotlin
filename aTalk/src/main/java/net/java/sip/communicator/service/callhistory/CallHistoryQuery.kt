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
package net.java.sip.communicator.service.callhistory

import net.java.sip.communicator.service.callhistory.event.CallHistoryQueryListener

/**
 * The `CallHistoryQuery` corresponds to a query made to the `CallHistoryService`.
 * It allows to be canceled, to listen for changes in the results and to obtain initial results if available.
 *
 * @author Yana Stamcheva
 */
interface CallHistoryQuery {
    /**
     * Cancels this query.
     */
    fun cancel()

    /**
     * Returns the query string, this query was created for.
     *
     * @return the query string, this query was created for
     */
    fun getQueryString(): String

    /**
     * Returns a collection of the initial results for this query. It's up to the implementation
     * to determine, which and how many the initial results would be.
     *
     *
     * This method is meant to be used in order to return first fast initial results and then
     * notify interested parties of additional results the `CallHistoryQueryListener`,
     * which should improve user experience when waiting for results.
     *
     * @return a collection of the initial results for this query
     */
    fun getCallRecords(): Collection<CallRecord?>?

    /**
     * Adds the given `CallHistoryQueryListener` to the list of listeners interested in query result changes.
     *
     * @param l the `CallHistoryQueryListener` to add
     */
    fun addQueryListener(l: CallHistoryQueryListener?)

    /**
     * Removes the given `CallHistoryQueryListener` from the list of listeners interested in query result changes.
     *
     * @param l the `CallHistoryQueryListener` to remove
     */
    fun removeQueryListener(l: CallHistoryQueryListener?)
}