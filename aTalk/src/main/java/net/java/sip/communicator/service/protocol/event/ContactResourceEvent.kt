/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ContactResource
import java.util.*

/**
 * The `ContactResourceEvent` is the event that notifies for any changes in the
 * `ContactResource`-s for a certain `Contact`.
 *
 * @author Yana Stamcheva
 */
class ContactResourceEvent
/**
 * Creates an instance of `ContactResourceEvent` by specifying the source, where this
 * event occurred and the concerned `ContactSource`.
 *
 * @param source the source where this event occurred
 * @param contactResource the `ContactResource` that is concerned by the change
 * @param eventType an integer representing the type of this event. One of the types defined in this
 * class: RESOURCE_ADDED, RESOURCE_REMOVED, RESOURCE_MODIFIED.
 */
(source: Contact?,
        /**
         * The `ContactResource` that is concerned by the change.
         */
        private val contactResource: ContactResource,
        /**
         * One of the event types defined in this class: RESOURCE_ADDED, RESOURCE_REMOVED, RESOURCE_MODIFIED.
         */
        val eventType: Int) : EventObject(source) {
    /**
     * Returns the `Contact`, which is the source of this event.
     *
     * @return the `Contact`, which is the source of this event
     */
    fun getContact(): Contact {
        return getSource() as Contact
    }

    /**
     * Returns the `ContactResource` that is concerned by the change.
     *
     * @return the `ContactResource` that is concerned by the change
     */
    fun getContactResource(): ContactResource {
        return contactResource
    }

    companion object {
        /**
         * Indicates that the `ContactResourceEvent` instance was triggered by the add of a `ContactResource`.
         */
        const val RESOURCE_ADDED = 0

        /**
         * Indicates that the `ContactResourceEvent` instance was triggered by the removal of a `ContactResource`.
         */
        const val RESOURCE_REMOVED = 1

        /**
         * Indicates that the `ContactResourceEvent` instance was triggered by the modification
         * of a `ContactResource`.
         */
        const val RESOURCE_MODIFIED = 2
    }
}