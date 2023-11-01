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
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import java.beans.PropertyChangeEvent

/**
 * A Contact property change event is issued whenever a contact property has changed. Event codes
 * defined in this class describe properties whose changes are being announced through this event.
 *
 * @author Emil Ivov
 */
class ContactPropertyChangeEvent
/**
 * Creates a ContactPropertyChangeEvent indicating that a change has occurred for property
 * `propertyName` in the `source` contact and that its value has changed from
 * `oldValue` to `newValue`.
 *
 *
 *
 * @param source
 * the Contact whose property has changed.
 * @param propertyName
 * the name of the property that has changed.
 * @param oldValue
 * the value of the property before the change occurred.
 * @param newValue
 * the value of the property after the change occurred.
 */
(source: Contact?, propertyName: String?, oldValue: Any?,
        newValue: Any?) : PropertyChangeEvent(source, propertyName, oldValue, newValue) {
    /**
     * Returns a reference to the `Contact` whose property has changed.
     *
     *
     *
     * @return a reference to the `Contact` whose reference has changed.
     */
    fun getSourceContact(): Contact {
        return getSource() as Contact
    }

    /**
     * Returns a reference to the protocol provider where the event has originated.
     *
     *
     *
     * @return a reference to the ProtocolProviderService instance where this event originated.
     */
    fun getProtocolProvider(): ProtocolProviderService {
        return getSourceContact().protocolProvider
    }

    /**
     * Returns a reference to the source contact parent `ContactGroup`.
     *
     * @return a reference to the `ContactGroup` instance that contains the source
     * `Contact`.
     */
    fun getParentContactGroup(): ContactGroup? {
        return getSourceContact().parentContactGroup
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Indicates that a change has occurred in the display name of the source contact.
         */
        const val PROPERTY_DISPLAY_NAME = "DisplayName"

        /**
         * Indicates that a change has occurred in the image of the source contact.
         */
        const val PROPERTY_IMAGE = "Image"

        /**
         * Indicates that a change has occurred in the data that the contact is storing in external
         * sources.
         */
        const val PROPERTY_PERSISTENT_DATA = "PersistentData"

        /**
         * Indicates that a change has occurred in the display details of the source contact.
         */
        const val PROPERTY_DISPLAY_DETAILS = "DisplayDetails"
    }
}