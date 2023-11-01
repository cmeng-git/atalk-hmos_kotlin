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
 * The `EditableSourceContact` is an extension to the
 * `SourceContact` interface that allows editing.
 *
 * @see SourceContact
 *
 *
 * @author Yana Stamcheva
 */
interface EditableSourceContact : SourceContact {
    /**
     * Adds a contact detail to the list of contact details.
     *
     * @param detail the `ContactDetail` to add
     */
    fun addContactDetail(detail: ContactDetail?)

    /**
     * Removes the given `ContactDetail` from the list of details for
     * this `SourceContact`.
     *
     * @param detail the `ContactDetail` to remove
     */
    fun removeContactDetail(detail: ContactDetail?)

    /**
     * Locks this object before adding or removing several contact details.
     */
    fun lock()

    /**
     * Unlocks this object before after or removing several contact details.
     */
    fun unlock()
}