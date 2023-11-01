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

import net.java.sip.communicator.service.protocol.OperationNotSupportedException
import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.PresenceStatus

/**
 * The `SourceContact` is the result contact of a search in the source. It should be
 * identifier by a display name, an image if available and a telephony string, which would allow
 * to call this contact through the preferred telephony provider defined in the `ContactSourceService`.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface SourceContact {
    /**
     * Returns the display name of this search contact. This is a user-friendly name that could
     * be shown in the user interface.
     *
     * @return the display name of this search contact
     */
    val displayName: String?
    /**
     * Returns the address of the contact.
     *
     * @return the contact address.
     */
    /**
     * Sets the address of the contact.
     *
     * @param contactAddress the address to set.
     */
    var contactAddress: String?

    /**
     * Returns the parent `ContactSourceService` from which this contact came from.
     *
     * @return the parent `ContactSourceService` from which this contact came from
     */
    val contactSource: ContactSourceService

    /**
     * Returns the display details of this search contact. This could be any important
     * information that should be shown to the user.
     *
     * @return the display details of the search contact
     */
    val displayDetails: String?

    /**
     * Returns a list of available contact details.
     *
     * @return a list of available contact details
     */
    val contactDetails: List<ContactDetail>?

    /**
     * The image/avatar of this `SourceContact`
     */
   val image: ByteArray?

    /**
     * Returns a list of all `ContactDetail`s supporting the given `OperationSet` class.
     *
     * @param operationSet the `OperationSet` class we're looking for
     *
     * @return a list of all `ContactDetail`s supporting the given `OperationSet` class
     */
    fun getContactDetails(operationSet: Class<out OperationSet?>?): List<ContactDetail>?

    /**
     * Returns a list of all `ContactDetail`s corresponding to the given category.
     *
     * @param category the `OperationSet` class we're looking for
     *
     * @return a list of all `ContactDetail`s corresponding to the given category
     * @throws OperationNotSupportedException if categories aren't supported for call history records
     */
    @Throws(OperationNotSupportedException::class)
    fun getContactDetails(category: ContactDetail.Category): List<ContactDetail>?

    /**
     * Returns the preferred `ContactDetail` for a given `OperationSet` class.
     *
     * @param operationSet the `OperationSet` class, for which we would like to obtain a `ContactDetail`
     *
     * @return the preferred `ContactDetail` for a given `OperationSet` class
     */
    fun getPreferredContactDetail(operationSet: Class<out OperationSet?>?): ContactDetail?

    /**
     * Whether the current image returned by @see #getImage() is the one provided by the
     * SourceContact by default, or is a one used and obtained from external source.
     *
     * @return whether this is the default image for this SourceContact.
     */
    val isDefaultImage: Boolean

    /**
     * Gets the user data associated with this instance and a specific key.
     *
     * @param key the key of the user data associated with this instance to be retrieved
     *
     * @return an `Object` which represents the value associated with this instance and
     * the specified `key`; `null` if no association with the specified
     * `key` exists in this instance
     */
    fun getData(key: Any?): Any?

    /**
     * Sets a user-specific association in this instance in the form of a key-value pair. If the
     * specified `key` is already associated in this instance with a value, the existing
     * value is overwritten with the specified `value`.
     *
     *
     * The user-defined association created by this method and stored in this instance is not
     * serialized by this instance and is thus only meant for runtime use.
     *
     *
     *
     * The storage of the user data is implementation-specific and is thus not guaranteed to be
     * optimized for execution time and memory use.
     *
     *
     * @param key the key to associate in this instance with the specified value
     * @param value the value to be associated in this instance with the specified `key`
     */
    fun setData(key: Any?, value: Any?)

    /**
     * Returns the status of the source contact. And null if such information is not available.
     *
     * @return the PresenceStatus representing the state of this source contact.
     */
    val presenceStatus: PresenceStatus?

    /**
     * Returns the index of this source contact in its parent.
     *
     * @return the index of this source contact in its parent
     */
    val index: Int

    companion object {
        /**
         * The key that can be used to store `SourceContact` ids where need it.
         */
        val DATA_ID = SourceContact::class.java.name + ".id"
    }
}