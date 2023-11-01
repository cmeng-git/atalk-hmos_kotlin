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
import net.java.sip.communicator.service.protocol.Contact
import java.beans.PropertyChangeEvent

/**
 * Event delivered upon addition, removal or change of a protocol specific
 * contact inside an existing meta contact.
 *
 * @author Emil Ivov
 */
class ProtoContactEvent
/**
 * Creates an instance of this `ProtoContactEvent`.
 * @param source the proto `Contact` that this event is about.
 * @param eventName the name of the event, one of the PROTO_CONTACT_XXX fields.
 * @param oldParent the `MetaContact` that was parent of the source
 * contact before the event occurred or null for a new contact or when
 * irrelevant.
 * @param newParent the `MetaContact` that is parent of the source
 * contact after the event occurred or null for a removed contact or when irrelevant.
 */
(source: Contact?, eventName: String?, oldParent: MetaContact?, newParent: MetaContact?) : PropertyChangeEvent(source, eventName, oldParent, newParent) {
    /**
     * Returns the protoContact that this event is about.
     * @return he `Contact` that this event is about.
     */
    fun getProtoContact(): Contact {
        return getSource() as Contact
    }

    /**
     * Returns the `MetaContact` that was parent of the source contact
     * before the event occurred or null for a new contact or when irrelevant.
     *
     * @return the `MetaContact` that was parent of the source contact
     * before the event occurred or null for a new contact or when irrelevant.
     */
    fun getOldParent(): MetaContact {
        return oldValue as MetaContact
    }

    /**
     * Returns the `MetaContact` that is parent of the source contact
     * after the event occurred or null for a removed contact or when irrelevant.
     *
     * @return the `MetaContact` that is parent of the source contact
     * after the event occurred or null for a removed contact or when irrelevant.
     */
    fun getNewParent(): MetaContact? {
        return newValue as MetaContact
    }

    /**
     * Returns the `MetaContact` that is the most relevant parent of
     * the source proto `Contact`. In the case of a moved or newly
     * added `Contact` the method would return same as getNewParent()
     * and would return the contact's old parent in the case of a `PROTO_CONTACT_REMOVED` event.
     * @return  the `MetaContact` that is most apt to be called parent to the source `Contact`.
     */
    fun getParent(): MetaContact? {
        return if (getNewParent() != null) getNewParent() else getOldParent()
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Indicates that the MetaContactEvent instance was triggered by the
         * removal of a protocol specific contact from an existing MetaContact.
         */
        const val PROTO_CONTACT_REMOVED = "ProtoContactRemoved"

        /**
         * Indicates that the MetaContactEvent instance was triggered by the
         * a protocol specific contact to a new MetaContact parent.
         */
        const val PROTO_CONTACT_ADDED = "ProtoContactAdded"

        /**
         * Indicates that the MetaContactEvent instance was triggered by moving
         * addition of a protocol specific contact to an existing MetaContact.
         */
        const val PROTO_CONTACT_MOVED = "ProtoContactMoved"

        /**
         * Indicates that this event instance was triggered by changing a protocol
         * specific contact in some way.
         */
        const val PROTO_CONTACT_MODIFIED = "ProtoContactModified"

        /**
         * Indicates that this event instance was triggered by renaming a protocol
         * specific contact.
         */
        const val PROTO_CONTACT_RENAMED = "ProtoContactRenamed"
    }
}