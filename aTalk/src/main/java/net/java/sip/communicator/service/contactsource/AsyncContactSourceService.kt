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

import java.util.regex.Pattern

/**
 * Declares the interface of a `ContactSourceService` which performs
 * `ContactQuery`s in a separate `Thread`.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class AsyncContactSourceService : ExtendedContactSourceService {
    /**
     * Creates query that searches for `SourceContact`s
     * which match a specific `query` `String`.
     *
     * @param queryString the `String` which this `ContactSourceService`
     * is being queried for
     * @return a `ContactQuery` which represents the query of this
     * `ContactSourceService` implementation for the specified
     * `String` and via which the matching `SourceContact`s (if
     * any) will be returned
     * @see ContactSourceService.createContactQuery
     */
    override fun createContactQuery(queryString: String): ContactQuery? {
        return createContactQuery(
                Pattern.compile(queryString, Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
    }

    /**
     * Creates query that searches for `SourceContact`s
     * which match a specific `query` `String`.
     *
     * @param queryString the `String` which this `ContactSourceService`
     * is being queried for
     * @param contactCount the maximum count of result contacts
     * @return a `ContactQuery` which represents the query of this
     * `ContactSourceService` implementation for the specified
     * `String` and via which the matching `SourceContact`s (if
     * any) will be returned
     * @see ContactSourceService.createContactQuery
     */
    override fun createContactQuery(queryString: String, contactCount: Int): ContactQuery? {
        return createContactQuery(
                Pattern.compile(queryString!!, Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
    }

    /**
     * Stops this `ContactSourceService`.
     */
    abstract fun stop()

    /**
     * Defines whether using this contact source service (Outlook or MacOSX
     * Contacts) can be used as result for the search field. This is
     * useful when an external plugin looks for result of this contact source
     * service, but want to display the search field result from its own (avoid
     * duplicate results).
     *
     * @return True if this contact source service can be used to perform search
     * for contacts. False otherwise.
     */
    abstract fun canBeUsedToSearchContacts(): Boolean
}