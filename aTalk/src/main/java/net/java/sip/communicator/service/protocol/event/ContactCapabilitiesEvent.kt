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
import net.java.sip.communicator.service.protocol.OperationSet
import org.jxmpp.jid.Jid
import java.util.*

/**
 * Represents an event/`EventObject` fired by `OperationSetClientCapabilities` in order
 * to notify about changes in the list of the `OperationSet` capabilities of a `Contact`.
 *
 * @author Lubomir Marinov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class ContactCapabilitiesEvent
/**
 * Initializes a new `ContactCapabilitiesEvent` instance which is to notify about a
 * specific change in the list of `OperationSet` capabilities of a specific `Contact`.
 *
 * @param sourceContact the `Contact` which is to be considered the source/cause of the new event
 * @param jid the full Jid of contact
 * @param opSets the new set of operation sets this event is about
 */(sourceContact: Contact?,
        /**
         * The full jid of the contact of which its resource is used for actual contact identification
         */
        private val contactJid: Jid?,
        /**
         * The new set of supported `OperationSet`s.
         */
        private val opSets: Map<String, OperationSet>) : EventObject(sourceContact) {
    /**
     * Gets the contact Jid which indicates the specifics of the change in the list of `OperationSet`
     * capabilities of the associated `sourceContact` and the details it carries.
     *
     * @return the the fullJid of the contact
     */
    fun getJid(): Jid? {
        return contactJid
    }

    /**
     * Gets the `Contact` which is the source/cause of this event i.e. which has changed its
     * list of `OperationSet` capabilities.
     *
     * @return the `Contact` which is the source/cause of this event
     */
    fun getSourceContact(): Contact {
        return getSource() as Contact
    }

    /**
     * Returns the new set of `OperationSet`-s this event is about
     *
     * @return the new set of `OperationSet`-s
     */
    fun getOperationSets(): Map<String, OperationSet> {
        return opSets
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}