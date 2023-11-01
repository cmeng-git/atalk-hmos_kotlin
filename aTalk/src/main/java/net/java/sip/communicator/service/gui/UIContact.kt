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
package net.java.sip.communicator.service.gui

import net.java.sip.communicator.service.protocol.OperationSet
import java.awt.Component
import javax.swing.JButton
import javax.swing.JMenuItem

/**
 * The `UIContact` represents the user interface contact contained in the contact list component.
 *
 * @author Yana Stamcheva
 */
abstract class UIContact {
    /**
     * Returns the descriptor of this contact.
     *
     * @return the descriptor of this contact
     */
    abstract val descriptor: Any?

    /**
     * Returns the display name of this contact.
     *
     * @return the display name of this contact
     */
    abstract val displayName: String?

    /**
     * Returns the display details of this contact. These would be shown whenever the contact is selected.
     *
     * @return the display details of this contact
     */
    abstract val displayDetails: String?

    /**
     * Returns the index of this contact in its source.
     *
     * @return the source index
     */
    abstract val sourceIndex: Int
    /**
     * Creates a tool tip for this contact. If such tooltip is
     * provided it would be shown on mouse over over this `UIContact`.
     *
     * @return the tool tip for this contact descriptor
     */
    // public abstract ExtendedTooltip getToolTip();
    /**
     * Returns the right button menu component.
     *
     * @return the right button menu component
     */
    abstract val rightButtonMenu: Component?
    /**
     * Returns the parent group.
     *
     * @return the parent group
     */
    /**
     * Sets the given `UIGroup` to be the parent group of this `UIContact`.
     *
     * @param parentGroup the parent `UIGroup` of this contact
     */
    abstract var parentGroup: UIGroup?

    /**
     * Returns an `Iterator` over a list of the search strings of this contact.
     *
     * @return an `Iterator` over a list of the search strings of this contact
     */
    abstract val searchStrings: MutableList<String?>

    /**
     * Returns an `Iterator` over a list of the search strings of this contact.
     *
     * @return an `Iterator` over a list of the search strings of this contact
     */
    abstract fun getSearchStringIter(): Iterator<String?>?

    /**
     * Returns the default `ContactDetail` to use for any operations
     * depending to the given `OperationSet` class.
     *
     * @param opSetClass the `OperationSet` class we're interested in
     * @return the default `ContactDetail` to use for any operations
     * depending to the given `OperationSet` class
     */
    abstract fun getDefaultContactDetail(opSetClass: Class<out OperationSet?>?): UIContactDetail?

    /**
     * Returns a list of all `UIContactDetail`s corresponding to the
     * given `OperationSet` class.
     *
     * @param opSetClass the `OperationSet` class we're looking for
     * @return a list of all `UIContactDetail`s corresponding to the
     * given `OperationSet` class
     */
    abstract fun getContactDetailsForOperationSet(opSetClass: Class<out OperationSet?>?): List<UIContactDetail?>

    /**
     * Returns a list of all `UIContactDetail`s within this `UIContact`.
     *
     * @return a list of all `UIContactDetail`s within this `UIContact`
     */
    abstract val contactDetails: List<UIContactDetail?>?

    /**
     * Returns all custom action buttons for this notification contact.
     *
     * @return a list of all custom action buttons for this notification contact
     */
    abstract val contactCustomActionButtons: Collection<JButton?>?

    /**
     * Returns the preferred height of this group in the contact list.
     *
     * @return the preferred height of this group in the contact list
     */
    val preferredHeight: Int
        get() = -1

    /**
     * Returns all custom action menu items for this contact.
     *
     * @param initActions if `true` the actions will be reloaded.
     * @return a list of all custom action menu items for this contact.
     */
    open fun getContactCustomActionMenuItems(initActions: Boolean): Collection<JMenuItem>? {
        return null
    }
}