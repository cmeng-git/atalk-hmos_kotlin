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

import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.util.DataObject
import java.util.*

/**
 * Implements a generic `SourceContact` for the purposes of the support
 * for the OS-specific Address Book.
 *
 * @author Lyubomir Marinov
 */
open class GenericSourceContact
/**
 * Initializes a new `AddrBookSourceContact` instance.
 *
 * @param contactSource the `ContactSourceService` which is creating the new instance
 * @param displayName the display name of the new instance
 * @param contactDetails the `ContactDetail`s of the new instance
 */
(
        /**
         * The `ContactSourceService` which has created this `SourceContact`.
         */
        override val contactSource: ContactSourceService,
        /**
         * The display name of this `SourceContact`.
         */
        override var displayName: String,
        /**
         * The `ContactDetail`s of this `SourceContact`.
         */
        contactDetails: List<ContactDetail>
        ) : DataObject(), SourceContact {

    override val contactDetails = Collections.unmodifiableList(contactDetails)

    /**
     * The display details of this contact.
     */
    final override var displayDetails: String? = null
        private set

    /**
     * The presence status of the source contact. And null if such information is not available.
     */
    override var presenceStatus: PresenceStatus? = null

    /**
     * The image/avatar of this `SourceContact`
     */
    override var image: ByteArray? = null

    /**
     * The address of the contact.
     */
    override var contactAddress: String? = null

    /**
     * Gets the `ContactDetail`s of this `SourceContact` which
     * support a specific `OperationSet`.
     *
     * @param operationSet the `OperationSet` the supporting
     * `ContactDetail`s of which are to be returned
     * @return the `ContactDetail`s of this `SourceContact` which support the specified
     * `operationSet`
     * @see SourceContact.getContactDetails
     */
    override fun getContactDetails(operationSet: Class<out OperationSet?>?): List<ContactDetail> {
        val contactDetails = LinkedList<ContactDetail>()
        for (contactDetail in contactDetails) {
            val supportedOperationSets = contactDetail.supportedOperationSets
            if (supportedOperationSets != null
                    && supportedOperationSets.contains(operationSet)) contactDetails.add(contactDetail)
        }
        return contactDetails
    }

    /**
     * Returns a list of all `ContactDetail`s corresponding to the given category.
     *
     * @param category the `OperationSet` class we're looking for
     * @return a list of all `ContactDetail`s corresponding to the given category
     */
    override fun getContactDetails(category: ContactDetail.Category): List<ContactDetail> {
        val contactDetails = LinkedList<ContactDetail>()
        for (contactDetail in contactDetails) {
            if (contactDetail != null) {
                val detailCategory = contactDetail.category
                if (detailCategory != null && detailCategory == category) contactDetails.add(contactDetail)
            }
        }
        return contactDetails
    }

    /**
     * Sets the display details of this `SourceContact`.
     *
     * @param displayDetails the display details of this `SourceContact`
     */
    fun setDisplayDetails(displayDetails: String): String {
        return displayDetails.also { this.displayDetails = it }
    }

    /**
     * Gets the preferred `ContactDetail` for a specific `OperationSet`.
     *
     * @param operationSet the `OperationSet` to get the preferred `ContactDetail` for
     * @return the preferred `ContactDetail` for the specified `operationSet`
     * @see SourceContact.getPreferredContactDetail
     */
    override fun getPreferredContactDetail(operationSet: Class<out OperationSet?>?): ContactDetail? {
        val contactDetails = getContactDetails(operationSet)
        return if (contactDetails.isEmpty()) null else contactDetails[0]
    }// in this SourceContact we always show an externally set image or null

    /**
     * Whether the current image returned by @see #getImage() is the one
     * provided by the SourceContact by default, or is a one used and obtained from external source.
     *
     * @return whether this is the default image for this SourceContact.
     */
    override val isDefaultImage: Boolean
        get() = false

    /**
     * Returns the index of this source contact in its parent.
     *
     * @return the index of this source contact in its parent
     */
    override val index: Int
        get() = -1

}