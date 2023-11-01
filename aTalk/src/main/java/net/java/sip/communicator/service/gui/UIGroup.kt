/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui

import net.java.sip.communicator.plugin.desktoputil.SIPCommButton
import java.awt.Component

/**
 * The `UIGroup` represents the user interface contact list group.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
abstract class UIGroup {
    /**
     * Returns the preferred height of this group in the contact list.
     *
     * @return the preferred height of this group in the contact list
     */
    /**
     * Sets the preferred height of this group in the contact list.
     *
     * @param preferredHeight the preferred height of this group in the contact
     * list
     */
    /**
     * The preferred height of this group in the contact list.
     */
    var preferredHeight = -1
    /**
     * Returns the display details of this contact. These would be shown
     * whenever the contact is selected. The display details aren't obligatory,
     * so we return an empty string.
     *
     * @return the display details of this contact
     */
    /**
     * Sets the display details of this group.
     *
     * @return the display details of this group
     */
    /**
     * The display details of this group.
     */
    var displayDetails = ""

    /**
     * Returns the descriptor of the group. This would be the underlying object
     * that should provide all other necessary information for the group.
     *
     * @return the descriptor of the group
     */
    abstract val descriptor: Any?

    /**
     * The display name of the group. The display name is the name to be shown
     * in the contact list group row.
     *
     * @return the display name of the group
     */
    abstract val displayName: String?

    /**
     * Returns the index of this group in its source. In other words this is
     * the descriptor index.
     *
     * @return the index of this group in its source
     */
    abstract val sourceIndex: Int

    /**
     * Returns the parent group.
     *
     * @return the parent group
     */
    abstract val parentGroup: UIGroup?

    /**
     * Indicates if the group is collapsed or expanded.
     *
     * @return `true` to indicate that the group is collapsed,
     * `false` to indicate that it's expanded
     */
    abstract val isGroupCollapsed: Boolean

    /**
     * Returns the count of online child contacts.
     *
     * @return the count of online child contacts
     */
    abstract fun countOnlineChildContacts(): Int

    /**
     * Returns the child contacts count.
     *
     * @return child contacts count
     */
    abstract fun countChildContacts(): Int

    /**
     * Returns the identifier of this group.
     *
     * @return the identifier of this group
     */
    abstract val id: String?

    /**
     * Returns the right button menu for this group.
     *
     * @return the right button menu component for this group
     */
    abstract val rightButtonMenu: Component?

    /**
     * Returns all custom action buttons for this group.
     *
     * @return a list of all custom action buttons for this group
     */
    val customActionButtons: Collection<SIPCommButton>?
        get() = null

    companion object {
        /**
         * The maximum number of contacts in the contact source.
         */
        var MAX_GROUPS = 10000000

        /**
         * The maximum number of contacts in the group.
         */
        var MAX_CONTACTS = 10000
    }
}