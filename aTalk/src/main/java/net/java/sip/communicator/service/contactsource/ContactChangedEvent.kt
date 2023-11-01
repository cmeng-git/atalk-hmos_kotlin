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
 * The `ContactChangedEvent` indicates that a
 * `SourceContact` has been updated as a result of a
 * `ContactQuery`.
 * @author Yana Stamcheva
 */
class ContactChangedEvent
/**
 * Creates a `ContactChangedEvent` by specifying the contact search
 * source and the updated `searchContact`.
 * @param source the source that triggered this event
 * @param contact the updated contact
 */
(source: ContactQuery?,
        /**
         * The contact that has been updated.
         */
        val contact: SourceContact?) : EventObject(source) {
    /**
     * Returns the updated contact.
     * @return the updated contact
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
    }
}