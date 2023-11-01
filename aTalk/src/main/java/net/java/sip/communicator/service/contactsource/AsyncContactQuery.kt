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
import java.util.regex.Pattern

/**
 * Provides an abstract implementation of a `ContactQuery` which runs in a separate `Thread`.
 *
 * @param <T> the very type of `ContactSourceService` which performs the `ContactQuery`
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
</T> */
abstract class AsyncContactQuery<T : ContactSourceService?> : AbstractContactQuery<T> {
    /**
     * Gets the [.query] of this `AsyncContactQuery` as a
     * `String` which represents a phone number (if possible).
     *
     * @return a `String` which represents the `query` of this
     * `AsyncContactQuery` as a phone number if such parsing, formatting
     * and validation is possible; otherwise, `null`
     */
    /**
     * The [.query] in the form of a `String` telephone number if
     * such parsing, formatting and validation is possible; otherwise, `null`.
     */
    private var phoneNumberQuery: String? = null
        get() {
            if (field == null && !queryIsConvertedToPhoneNumber) {
                try {
                    val pattern = query.pattern()
                    if (pattern != null) {
                        val patternLength = pattern.length
                        if (patternLength > 2 && pattern[0] == '^' && pattern[patternLength - 1] == '$') {
                            field = pattern.substring(1, patternLength - 1)
                        } else if (patternLength > 4 && pattern[0] == '\\' && pattern[1] == 'Q' && pattern[patternLength - 2] == '\\' && pattern[patternLength - 1] == 'E') {
                            field = pattern.substring(2, patternLength - 2)
                        }
                    }
                } finally {
                    queryIsConvertedToPhoneNumber = true
                }
            }
            return field
        }

    /**
     * The `Pattern` for which the associated `ContactSourceService` is being queried.
     */
    protected val query: Pattern

    /**
     * The indicator which determines whether there has been an attempt to
     * convert [.query] to [.phoneNumberQuery]. If the conversion has
     * been successful, `phoneNumberQuery` will be non-`null`.
     */
    private var queryIsConvertedToPhoneNumber = false

    /**
     * Gets the `List` of `SourceContact`s which match this `ContactQuery`.
     *
     * @see ContactQuery.getQueryResults
     */
    /**
     * The `SourceContact`s which match [.query].
     */
    final override var queryResults: MutableCollection<SourceContact> = LinkedList()
        get() {
            var qr: MutableCollection<SourceContact>
            synchronized(field) {
                qr = ArrayList(field.size)
                qr.addAll(field)
            }
            return qr
        }

    /**
     * The `Thread` in which this `AsyncContactQuery` is performing [.query].
     */
    private var thread: Thread? = null

    /**
     * Initializes a new `AsyncContactQuery` instance which is to perform
     * a specific `query` on behalf of a specific `contactSource`.
     *
     * @param contactSource the `ContactSourceService` which is to
     * perform the new `ContactQuery` instance
     * @param query the `Pattern` for which `contactSource` is being queried
     * @param isSorted indicates if the results of this query should be sorted
     */
    protected constructor(contactSource: ContactSourceService, query: Pattern, isSorted: Boolean) : super(contactSource) {
        this.query = query
        if (isSorted) queryResults = TreeSet()
    }

    /**
     * Initializes a new `AsyncContactQuery` instance which is to perform
     * a specific `query` on behalf of a specific `contactSource`.
     *
     * @param contactSource the `ContactSourceService` which is to
     * perform the new `ContactQuery` instance
     * @param query the `Pattern` for which `contactSource` is being queried
     */
    protected constructor(contactSource: ContactSourceService, query: Pattern) : super(contactSource) {
        this.query = query
    }

    /**
     * Adds a specific `SourceContact` to the list of
     * `SourceContact`s to be returned by this `ContactQuery` in
     * response to [.getQueryResults].
     *
     * @param sourceContact the `SourceContact` to be added to the
     * `queryResults` of this `ContactQuery`
     * @param showMoreEnabled indicates whether show more label should be shown or not.
     * @return `true` if the `queryResults` of this
     * `ContactQuery` has changed in response to the call
     */
    protected fun addQueryResult(sourceContact: SourceContact, showMoreEnabled: Boolean): Boolean {
        var changed: Boolean
        synchronized(queryResults) { changed = queryResults.add(sourceContact) }
        if (changed) fireContactReceived(sourceContact, showMoreEnabled)
        return changed
    }

    /**
     * Adds a specific `SourceContact` to the list of
     * `SourceContact`s to be returned by this `ContactQuery` in response to [.getQueryResults].
     *
     * @param sourceContact the `SourceContact` to be added to the
     * `queryResults` of this `ContactQuery`
     * @return `true` if the `queryResults` of this
     * `ContactQuery` has changed in response to the call
     */
    protected open fun addQueryResult(sourceContact: SourceContact): Boolean {
        var changed: Boolean
        synchronized(queryResults) { changed = queryResults.add(sourceContact) }
        if (changed) fireContactReceived(sourceContact)
        return changed
    }

    /**
     * Removes a specific `SourceContact` from the list of
     * `SourceContact`s.
     *
     * @param sourceContact the `SourceContact` to be removed from the
     * `queryResults` of this `ContactQuery`
     * @return `true` if the `queryResults` of this
     * `ContactQuery` has changed in response to the call
     */
    protected fun removeQueryResult(sourceContact: SourceContact): Boolean {
        var changed: Boolean
        synchronized(queryResults) { changed = queryResults.remove(sourceContact) }
        if (changed) fireContactRemoved(sourceContact)
        return changed
    }

    /**
     * Adds a set of `SourceContact` instances to the list of
     * `SourceContact`s to be returned by this `ContactQuery` in
     * response to [.getQueryResults].
     *
     * @param sourceContacts the set of `SourceContact` to be added to
     * the `queryResults` of this `ContactQuery`
     * @return `true` if the `queryResults` of this
     * `ContactQuery` has changed in response to the call
     */
    protected fun addQueryResults(sourceContacts: Set<SourceContact>): Boolean {
        val changed: Boolean
        synchronized(queryResults) { changed = queryResults.addAll(sourceContacts) }
        if (changed) {
            // TODO Need something to fire one event for multiple contacts.
            for (contact in sourceContacts) {
                fireContactReceived(contact, false)
            }
        }
        return changed
    }

    /**
     * Gets the number of `SourceContact`s which match this `ContactQuery`.
     *
     * @return the number of `SourceContact` which match this `ContactQuery`
     */
    val queryResultCount: Int
        get() {
            synchronized(queryResults) { return queryResults.size }
        }

    /**
     * Returns the query string, this query was created for.
     *
     * @return the query string, this query was created for
     */
    override val queryString: String
        get() = query.toString()

    /**
     * Performs this `ContactQuery` in a background `Thread`.
     */
    protected abstract fun run()

    /**
     * Starts this `AsyncContactQuery`.
     */
    @Synchronized
    override fun start() {
        if (thread == null) {
            thread = object : Thread() {
                override fun run() {
                    var completed = false
                    completed = try {
                        this@AsyncContactQuery.run()
                        true
                    } finally {
                        synchronized(this@AsyncContactQuery) { if (thread === currentThread()) stopped(completed) }
                    }
                }
            }
            thread!!.isDaemon = true
            thread!!.start()
        } else throw IllegalStateException("thread")
    }

    /**
     * Notifies this `AsyncContactQuery` that it has stopped performing
     * in the associated background `Thread`.
     *
     * @param completed `true` if this `ContactQuery` has
     * successfully completed, `false` if an error has been encountered during its execution
     */
    protected fun stopped(completed: Boolean) {
        if (status == ContactQuery.QUERY_IN_PROGRESS)
            status = (if (completed) ContactQuery.QUERY_COMPLETED else ContactQuery.QUERY_ERROR)
    }

    /**
     * Determines whether a specific `String` phone number matches the
     * [.query] of this `AsyncContactQuery`.
     *
     * @param phoneNumber the `String` which represents the phone number
     * to match to the `query` of this `AsyncContactQuery`
     * @return `true` if the specified `phoneNumber` matches the
     * `query` of this `AsyncContactQuery`; otherwise, `false`
     */
    protected open fun phoneNumberMatches(phoneNumber: String?): Boolean {
        /*
         * PhoneNumberI18nService implements functionality to aid the parsing,
         * formatting and validation of international phone numbers so attempt
         * to use it to determine whether the specified phoneNumber matches the
         * query. For example, check whether the normalized phoneNumber matches the query.
         */
        var phoneNumberMatches = false
        if (query.matcher(ContactSourceActivator.phoneNumberI18nService!!.normalize(phoneNumber)).find()) {
            phoneNumberMatches = true
        } else {
            /*
             * The fact that the normalized form of the phoneNumber doesn't
             * match the query doesn't mean that, for example, it doesn't
             * match the normalized form of the query. The latter, though,
             * requires the query to look like a phone number as well. In
             * order to not accidentally start matching all queries to phone
             * numbers, it seems justified to normalize the query only when
             * it is a phone number, not whenever it looks like a piece of a
             * phone number.
             */
            val phoneNumberQuery = phoneNumberQuery
            if (phoneNumberQuery != null && phoneNumberQuery.isNotEmpty()) {
                try {
                    phoneNumberMatches = ContactSourceActivator.phoneNumberI18nService!!.phoneNumbersMatch(
                            phoneNumberQuery,
                            phoneNumber)
                } catch (iaex: IllegalArgumentException) {
                    /*
                     * Ignore it, phoneNumberMatches will remain equal to
                     * false.
                     */
                }
            }
        }
        return phoneNumberMatches
    }
}