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
import net.java.sip.communicator.service.contactlist.MetaContactGroup

/**
 * Fired whenever a meta contact has been moved from one parent group to another. The event
 * contains the old and new parents as well as a reference to the source contact.
 *
 * @author Emil Ivov
 */
class MetaContactMovedEvent
/**
 * Create as an instance of this `MetaContactMovedEvent` using the specified arguments.
 *
 * @param sourceContact a reference to the `MetaContact` that this
 * event is about.
 * @param oldParent a reference to the `MetaContactGroup` that contained `sourceContact`
 * before it was moved.
 * @param newParent a reference to the `MetaContactGroup` that contains `sourceContact`
 * after it was moved.
 */
(sourceContact: MetaContact?, oldParent: MetaContactGroup?, newParent: MetaContactGroup?) : MetaContactPropertyChangeEvent(sourceContact, MetaContactPropertyChangeEvent.Companion.META_CONTACT_MOVED, oldParent, newParent) {
    /**
     * Returns the old parent of this meta contact.
     *
     * @return a reference to the `MetaContactGroup` that contained the source meta
     * contact before it was moved.
     */
    fun getOldParent(): MetaContactGroup {
        return oldValue as MetaContactGroup
    }

    /**
     * Returns the new parent of this meta contact.
     *
     * @return a reference to the `MetaContactGroup` that contains the source meta contact
     * after it was moved.
     */
    fun getNewParent(): MetaContactGroup {
        return newValue as MetaContactGroup
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}