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

/**
 * The `ProtocolSourceContact` provides a sorted
 * `GenericSourceContact`. `SourceContact`-s are sorted
 * alphabetically and based on their presence status.
 */
open class SortedGenericSourceContact
/**
 * Creates an instance of `ProtocolSourceContact`.
 *
 * @param parentQuery the parent `ContactQuery`, which generated this result contact
 * @param cSourceService the parent `ContactSourceService`, of which this source contact is part
 * @param displayName the display name of the contact
 * @param contactDetails the list of contact details
 */
(
        /**
         * The parent contact query.
         */
        private val parentQuery: ContactQuery,
        cSourceService: ContactSourceService,
        displayName: String,
        contactDetails: List<ContactDetail>)
    : GenericSourceContact(cSourceService, displayName, contactDetails), Comparable<SourceContact> {

    /**
     * Compares this contact with the specified object for order. Returns
     * a negative integer, zero, or a positive integer as this contact is
     * less than, equal to, or greater than the specified object.
     *
     *
     * The result of this method is calculated the following way:
     *
     *
     * ( (10 - isOnline) - (10 - targetIsOnline)) * 100000000 <br></br>
     * + getDisplayName()
     * .compareToIgnoreCase(target.getDisplayName()) * 10000 <br></br>
     * + compareDDetails * 1000 <br></br>
     * + String.valueOf(hashCode())
     * .compareToIgnoreCase(String.valueOf(o.hashCode()))
     *
     *
     * Or in other words ordering of source contacts would be first done by
     * presence status, then display name, then display details and finally
     * (in order to avoid equalities) be the hashCode of the object.
     *
     *
     * @param   other the `SourceContact` to be compared.
     * @return  a negative integer, zero, or a positive integer as this
     * object is less than, equal to, or greater than the specified object.
     */
    override fun compareTo(other: SourceContact): Int {
        var comparePresence = 0
        if (presenceStatus != null && other.presenceStatus != null) {
            val isOnline = if (presenceStatus!!.isOnline) 1 else 0
            val targetIsOnline = if (other.presenceStatus!!.isOnline) 1 else 0
            comparePresence = 10 - isOnline - (10 - targetIsOnline)
        }
        var compareDDetails = 0
        if (displayDetails != null && other.displayDetails != null) {
            compareDDetails = displayDetails!!
                    .compareTo(other.displayDetails!!, ignoreCase = true)
        }
        return comparePresence * 100000000 + displayName
                .compareTo(other.displayName!!, ignoreCase = true) * 10000 + compareDDetails * 100 + hashCode().toString().compareTo(other.hashCode().toString(), ignoreCase = true)
    }

    /**
     * Returns the index of this source contact in its parent group.
     *
     * @return the index of this contact in its parent
     */
    override val index: Int
        get() = parentQuery.queryResults.indexOf(this)
}