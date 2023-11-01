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

import net.java.sip.communicator.service.contactlist.MetaContactGroup
import net.java.sip.communicator.service.protocol.ContactGroup
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import java.util.*

/**
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class MetaContactGroupEvent(source: MetaContactGroup?, provider: ProtocolProviderService?,
        sourceProtoGroup: ContactGroup?, eventID: Int) : EventObject(source) {
    /**
     * ID of the event.
     */
    private var eventID = -1

    /**
     * the ProtocolProviderService instance where this event occurred.
     */
    private var sourceProvider: ProtocolProviderService? = null

    /**
     * The proto group associated with this event.
     */
    private var sourceProtoGroup: ContactGroup? = null

    /**
     * Creates a new MetaContactGroup event according to the specified parameters.
     *
     * @param source the MetaContactGroup instance that is being modified in the MetaContactList
     * @param provider the ProtocolProviderService instance where this event occurred
     * @param sourceProtoGroup the proto group associated with this event or null if the event does not concern a
     * particular source group.
     * @param eventID one of the META_CONTACT_XXX static fields indicating the nature of the event.
     */
    init {
        sourceProvider = provider
        this.sourceProtoGroup = sourceProtoGroup
        this.eventID = eventID
    }

    /**
     * Returns the provider that the source contact belongs to.
     *
     * @return the provider that the source contact belongs to.
     */
    fun getSourceProvider(): ProtocolProviderService? {
        return sourceProvider
    }

    /**
     * Returns the proto group associated with this event or null if the event does not concern a
     * particular source group.
     *
     * @return the proto group associated with this event or null if the event does not concern a
     * particular source group.
     */
    fun getSourceProtoGroup(): ContactGroup? {
        return sourceProtoGroup
    }

    /**
     * Returns the source MetaContactGroup.
     *
     * @return the source MetaContactGroup.
     */
    fun getSourceMetaContactGroup(): MetaContactGroup {
        return getSource() as MetaContactGroup
    }

    /**
     * Returns a String representation of this MetaContactGroupEvent
     *
     * @return A String representation of this MetaContactGroupEvent.
     */
    override fun toString(): String {
        return ("MetaContactGroupEvent-[ GroupName=" + getSourceMetaContactGroup().getGroupName()
                + ", eventID=" + getEventID() + " ]")
    }

    /**
     * Returns an event id specifying whether the type of this event (e.g.
     * META_CONTACT_GROUP_ADDED, META_CONTACT_GROUP_REMOVED and etc.)
     *
     * @return one of the META_CONTACT_GROUP_XXX int fields of this class.
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
         * Indicates that the MetaContactGroupEvent instance was triggered by adding a
         * MetaContactGroup.
         */
        const val META_CONTACT_GROUP_ADDED = 1

        /**
         * Indicates that the MetaContactGroupEvent instance was triggered by the removal of an
         * existing MetaContactGroup.
         */
        const val META_CONTACT_GROUP_REMOVED = 2

        /**
         * Indicates that the MetaContactGroupEvent instance was triggered by the removal of a
         * protocol specific ContactGroup in the source MetaContactGroup.
         */
        const val CONTACT_GROUP_REMOVED_FROM_META_GROUP = 3

        /**
         * Indicates that the MetaContactGroupEvent instance was triggered by the fact that child
         * contacts were reordered in the source group.
         */
        const val CHILD_CONTACTS_REORDERED = 4

        /**
         * Indicates that the MetaContactGroupEvent instance was triggered by the renaming of a
         * protocol specific ContactGroup in the source MetaContactGroup. Note that this does not in
         * any way mean that the name of the MetaContactGroup itself has changed.
         * `MetaContactGroup`s contain multiple protocol groups and their name cannot change
         * each time one of them is renamed.
         */
        const val CONTACT_GROUP_RENAMED_IN_META_GROUP = 5

        /**
         * Indicates that the MetaContactGroupEvent instance was triggered by adding a protocol
         * specific ContactGroup to the source MetaContactGroup.
         */
        const val CONTACT_GROUP_ADDED_TO_META_GROUP = 6

        /**
         * Indicates that the MetaContactGroupEvent instance was triggered by the renaming of an
         * existing MetaContactGroup.
         */
        const val META_CONTACT_GROUP_RENAMED = 7
    }
}