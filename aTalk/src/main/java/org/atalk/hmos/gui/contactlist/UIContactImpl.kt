/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
  * limitations under the License.
 */
package org.atalk.hmos.gui.contactlist

import android.graphics.drawable.Drawable
import net.java.sip.communicator.service.gui.UIContact
import org.atalk.hmos.gui.AndroidGUIActivator

/**
 * The `UIContactImpl` class extends the `UIContact` in order to add some more
 * methods specific the UI implementation.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
abstract class UIContactImpl : UIContact() {
    /**
     * Returns the corresponding `ContactNode`. The `ContactNode` is the real node
     * that is stored in the contact list component data model.
     *
     * @return the corresponding `ContactNode`
     */
    /**
     * Sets the given `contactNode`. The `ContactNode` is the real node that is
     * stored in the contact list component data model.
     *
     * @param contactNode
     * the `ContactNode` that corresponds to this `UIGroup`
     */
    abstract var contactNode: ContactNode?

    /**
     * Returns the general status icon of the given UIContact.
     *
     * @return PresenceStatus the most "available" status from all sub-contact statuses.
     */
    abstract val statusIcon: ByteArray?

    /**
     * Gets the avatar of a specific `UIContact` in the form of an `ImageIcon` value.
     *
     * @param isSelected
     * indicates if the contact is selected
     * @param width
     * the desired icon width
     * @param height
     * the desired icon height
     * @return an `ImageIcon` which represents the avatar of the specified
     * `MetaContact`
     */
    abstract fun getScaledAvatar(isSelected: Boolean, width: Int, height: Int): Drawable?

    /**
     * Gets the avatar of a specific `UIContact` in the form of an `ImageIcon` value.
     *
     * @return a byte array representing the avatar of this `UIContact`
     */
//    open val avatar: ByteArray?
//        get() = sourceContact.getImage()

    /**
     * Returns the display name of this `UIContact`.
     *
     * @return the display name of this `UIContact`
     */
    abstract override val displayName: String?

    /**
     * Filter address display if enabled will remove domain part of the addresses to show.
     *
     * @param addressToDisplay
     * the address to change
     * @return if enabled the address with removed domain part
     */
    protected fun filterAddressDisplay(addressToDisplay_: String): String {
        var addressToDisplay = addressToDisplay_
        if (!AndroidGUIActivator.configurationService.getBoolean(FILTER_DOMAIN_IN_TIP_ADDRESSES, false)) return addressToDisplay
        val ix = addressToDisplay.indexOf("@")
        val typeIx = addressToDisplay.indexOf("(")
        if (ix != -1) {
            addressToDisplay = if (typeIx != -1) (addressToDisplay.substring(0, ix) + " "
                    + addressToDisplay.substring(typeIx, addressToDisplay.length)) else addressToDisplay.substring(0, ix)
        }
        return addressToDisplay
    }

    companion object {
        /**
         * Whether we should filter all addresses shown in tooltips and to remove the domain part.
         */
        private const val FILTER_DOMAIN_IN_TIP_ADDRESSES = "gui.contactlist.FILTER_DOMAIN_IN_TIP_ADDRESSES"
    }
}