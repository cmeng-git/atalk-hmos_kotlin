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
import net.java.sip.communicator.service.protocol.ContactGroup
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.jxmpp.jid.Jid
import java.beans.PropertyChangeEvent

/**
 * Instances of this class represent a change in the status of a particular contact.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class ContactPresenceStatusChangeEvent
/**
 * Creates an event instance indicating that the specified source contact has changed status
 * from `oldValue` to `newValue`.
 *
 * @param source the provider that generated the event
 * @param jid the contact FullJid that generated the event
 * @param sourceProvider the protocol provider that the contact belongs to.
 * @param parentGroup the group containing the contact that caused this event (to be set as null in cases
 * where groups are not supported);
 * @param oldValue the status the source contact was in before entering the new state.
 * @param newValue the status the source contact is currently in.
 */
@JvmOverloads constructor(source: Contact?,
        /**
         * The contact's FullJid that trigger the event.
         */
        private val contactJid: Jid,
        /**
         * The contact's `ProtocolProviderService`.
         */
        private val sourceProvider: ProtocolProviderService,
        /**
         * The parent group of the contact.
         */
        private val parentGroup: ContactGroup, oldValue: PresenceStatus?, newValue: PresenceStatus?,
        /**
         * When not the status but just the resource of the contact has changed, for those protocols
         * that support resources.
         */
        private val resourceChanged: Boolean = false) : PropertyChangeEvent(source, ContactPresenceStatusChangeEvent::class.java.name, oldValue, newValue) {

    /**
     * Creates an event instance indicating that the specified source contact has changed status
     * from `oldValue` to `newValue`.
     *
     * @param source the provider that generated the event
     * @param contactJid the contact FullJid that generated the event
     * @param sourceProvider the protocol provider that the contact belongs to.
     * @param parentGroup the group containing the contact that caused this event (to be set as null in cases
     * where groups are not supported);
     * @param oldValue the status the source contact was in before entering the new state.
     * @param newValue the status the source contact is currently in.
     */
    /**
     * Returns the provider that the source contact belongs to.
     *
     * @return the provider that the source contact belongs to.
     */
    fun getSourceProvider(): ProtocolProviderService {
        return sourceProvider
    }

    /**
     * Returns the provider that the source contact belongs to.
     *
     * @return the provider that the source contact belongs to.
     */
    fun getSourceContact(): Contact {
        return getSource() as Contact
    }

    /**
     * Returns the FullJid that the source contact belongs to.
     *
     * @return the FullJid that the source contact belongs to.
     */
    fun getJid(): Jid {
        return contactJid
    }

    /**
     * Returns the status of the provider before this event took place.
     *
     * @return a PresenceStatus instance indicating the event the source provider was in before it
     * entered its new state.
     */
    fun getOldStatus(): PresenceStatus {
        return super.getOldValue() as PresenceStatus
    }

    /**
     * Returns the status of the provider after this event took place. (i.e. at the time the event
     * is being dispatched).
     *
     * @return a PresenceStatus instance indicating the event the source provider is in after the
     * status change occurred.
     */
    fun getNewStatus(): PresenceStatus {
        return super.getNewValue() as PresenceStatus
    }

    /**
     * Returns (if applicable) the group containing the contact that cause this event. In the case
     * of a non persistent presence operation set this field is null.
     *
     * @return the ContactGroup (if there is one) containing the contact that caused the event.
     */
    private fun getParentGroup(): ContactGroup {
        return parentGroup
    }

    /**
     * Returns a String representation of this ContactPresenceStatusChangeEvent
     *
     * @return A a String representation of this ContactPresenceStatusChangeEvent.
     */
    override fun toString(): String {
        val buff = StringBuilder("ContactPresenceStatusChangeEvent-[ ContactID=")
        buff.append(getSourceContact().address)
        if (getParentGroup() != null) buff.append(", ParentGroup").append(getParentGroup().getGroupName())
        buff.append(", OldStatus=").append(getOldStatus())
                .append(", NewStatus=").append(getNewStatus())
                .append("]")
        return buff.toString()
    }

    /**
     * When the event fired is change in the resource of the contact will return `true`.
     *
     * @return the event fired is only for change in the resource of the contact.
     */
    fun isResourceChanged(): Boolean {
        return resourceChanged
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}