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
 * Indicates that a meta contact has changed its display name.
 *
 * @author Emil Ivov
 */
class MetaContactRenamedEvent
/**
 * Creates an instance of this event using the specified arguments.
 *
 * @param source the `MetaContact` that this event is about.
 * @param oldDisplayName the old display name of this meta contact.
 * @param newDisplayName the new display name of this meta contact.
 */
(source: MetaContact?, oldDisplayName: String?, newDisplayName: String?) : MetaContactPropertyChangeEvent(source, MetaContactPropertyChangeEvent.Companion.META_CONTACT_RENAMED, oldDisplayName, newDisplayName) {
    /**
     * Returns the display name of the source meta contact as it is now, after
     * the change.
     *
     * @return the new display name of the meta contact.
     */
    fun getNewDisplayName(): String {
        return newValue as String
    }

    /**
     * Returns the display name of the source meta contact as it was now, before the change.
     *
     * @return the meta contact name as it was before the change.
     */
    fun getOldDisplayName(): String {
        return oldValue as String
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}