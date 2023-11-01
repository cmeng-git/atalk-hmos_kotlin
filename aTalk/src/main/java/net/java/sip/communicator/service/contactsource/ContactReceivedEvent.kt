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
 * The `ContactReceivedEvent` indicates that a
 * `SourceContact` has been received as a result of a
 * `ContactQuery`.
 * @author Yana Stamcheva
 */
class ContactReceivedEvent : EventObject {
    /**
     * Returns the received contact.
     * @return the received contact
     */
    /**
     * The contact that has been received.
     */
    val contact: SourceContact?
    /**
     * Returns `true` if show more label should be shown and
     * `false` if not.
     * @return `true` if show more label should be shown and
     * `false` if not.
     */
    /**
     * Indicates whether show more label should be shown or not.
     */
    val isShowMoreEnabled: Boolean

    /**
     * Creates a `ContactReceivedEvent` by specifying the contact search
     * source and the received `searchContact`.
     * @param source the source that triggered this event
     * @param contact the received contact
     */
    constructor(source: ContactQuery?,
            contact: SourceContact?) : super(source) {
        this.contact = contact
        isShowMoreEnabled = true
    }

    /**
     * Creates a `ContactReceivedEvent` by specifying the contact search
     * source and the received `searchContact`.
     * @param source the source that triggered this event
     * @param contact the received contact
     * @param showMoreEnabled indicates whether show more label should be shown
     * or not.
     */
    constructor(source: ContactQuery?,
            contact: SourceContact?,
            showMoreEnabled: Boolean) : super(source) {
        this.contact = contact
        isShowMoreEnabled = showMoreEnabled
    }

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