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
import java.util.*

/**
 * Parent class for meta contact events indicating addition and removal of meta contacts in a
 * meta contact list.
 *
 * @author Yana Stamcheva
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class MetaContactEvent(source: MetaContact, parentGroup: MetaContactGroup?, eventID: Int) : EventObject(source) {
    /**
     * ID of the event.
     */
    private var eventID = -1

    /**
     * The parent group of the contact.
     */
    private var parentGroup: MetaContactGroup?

    /**
     * Creates a new MetaContact event according to the specified parameters.
     *
     * @param source the MetaContact instance that is added to the MetaContactList
     * @param parentGroup the MetaContactGroup under which the corresponding MetaContact is located
     * @param eventID one of the METACONTACT_XXX static fields indicating the nature of the event.
     */
    init {
        this.parentGroup = parentGroup
        this.eventID = eventID
    }

    /**
     * Returns the source MetaContact.
     *
     * @return the source MetaContact.
     */
    fun getSourceMetaContact(): MetaContact {
        return getSource() as MetaContact
    }

    /**
     * Returns the MetaContactGroup that the MetaContact belongs to.
     *
     * @return the MetaContactGroup that the MetaContact belongs to.
     */
    fun getParentGroup(): MetaContactGroup? {
        return parentGroup
    }

    /**
     * Returns a String representation of this MetaContactEvent
     *
     * @return A String representation of this MetaContactListEvent.
     */
    override fun toString(): String {
        val buff = StringBuilder("MetaContactEvent-[ ContactID=")
        buff.append(getSourceMetaContact().getDisplayName())
        buff.append(", eventID=").append(getEventID())
        if (getParentGroup() != null) buff.append(", ParentGroup=").append(getParentGroup()!!.getGroupName())
        return buff.toString()
    }

    /**
     * Returns an event id specifying whether the type of this event (e.g. METACONTACT_ADDED, METACONTACT_REMOVED and etc.)
     *
     * @return one of the METACONTACT_XXX int fields of this class.
     */
    fun getEventID(): Int {
        return eventID
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Indicates that the MetaContactEvent instance was triggered by adding a MetaContact.
         */
        const val META_CONTACT_ADDED = 1

        /**
         * Indicates that the MetaContactEvent instance was triggered by the removal of an existing MetaContact.
         */
        const val META_CONTACT_REMOVED = 2

        /**
         * Indicates that the MetaContactEvent instance was triggered by moving an existing MetaContact.
         */
        const val META_CONTACT_MOVED = 3
    }
}