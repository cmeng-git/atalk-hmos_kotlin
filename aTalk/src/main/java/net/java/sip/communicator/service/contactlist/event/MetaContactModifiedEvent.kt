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
package net.java.sip.communicator.service.contactlist.event

import net.java.sip.communicator.service.contactlist.MetaContact

/**
 * Indicates that a meta contact has changed.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class MetaContactModifiedEvent
/**
 * Creates an instance of this event using the specified arguments.
 *
 * @param source the `MetaContact` that this event is about.
 * @param modificationName name of the modification
 * @param oldValue the old value for the modification of this meta contact.
 * @param newValue the new value for the modification of this meta contact.
 */
(source: MetaContact?,
    /**
     * Name of the modification.
     */
    private val modificationName: String, oldValue: Any?, newValue: Any?) : MetaContactPropertyChangeEvent(source, MetaContactPropertyChangeEvent.Companion.META_CONTACT_MODIFIED, oldValue, newValue) {
    /**
     * Returns the modification name of the source meta contact.
     *
     * @return the modification name for the meta contact.
     */
    fun getModificationName(): String {
        return modificationName
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}