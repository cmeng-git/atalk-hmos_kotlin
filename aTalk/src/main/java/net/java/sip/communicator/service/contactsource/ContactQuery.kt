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

/**
 * The `ContactQuery` corresponds to a particular query made through the
 * `ContactSourceService`. Each query once started could be
 * canceled. One could also register a listener in order to be notified for
 * changes in query status and query contact results.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface ContactQuery {
    /**
     * Returns the `ContactSourceService`, where this query was first  initiated.
     * @return the `ContactSourceService`, where this query was first initiated
     */
    val contactSource: ContactSourceService

    /**
     * Returns the query string, this query was created for.
     * @return the query string, this query was created for
     */
    val queryString: String

    /**
     * Returns the list of `SourceContact`s returned by this query.
     * @return the list of `SourceContact`s returned by this query
     * Type forced to by TreeSet: cmeng
     */
    val queryResults: MutableCollection<SourceContact>

    /**
     * Starts the query.
     */
    fun start()

    /**
     * Cancels this query.
     */
    fun cancel()

    /**
     * Returns the status of this query. One of the static constants QUERY_XXXX defined in this class.
     * @return the status of this query
     */
    val status: Int

    /**
     * Adds the given `ContactQueryListener` to the list of registered
     * listeners. The `ContactQueryListener` would be notified each
     * time a new `ContactQuery` result has been received or if the
     * query has been completed or has been canceled by user or for any other reason.
     * @param l the `ContactQueryListener` to add
     */
    fun addContactQueryListener(l: ContactQueryListener?)

    /**
     * Removes the given `ContactQueryListener` to the list of
     * registered listeners. The `ContactQueryListener` would be
     * notified each time a new `ContactQuery` result has been received
     * or if the query has been completed or has been canceled by user or for any other reason.
     * @param l the `ContactQueryListener` to remove
     */
    fun removeContactQueryListener(l: ContactQueryListener?)

    companion object {
        /**
         * Indicates that this query has been completed.
         */
        const val QUERY_COMPLETED = 0

        /**
         * Indicates that this query has been canceled.
         */
        const val QUERY_CANCELED = 1

        /**
         * Indicates that this query has been stopped because of an error.
         */
        const val QUERY_ERROR = 2

        /**
         * Indicates that this query is in progress.
         */
        const val QUERY_IN_PROGRESS = 3
    }
}