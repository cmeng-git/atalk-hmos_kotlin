/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui

import net.java.sip.communicator.service.contactsource.ContactQuery
import net.java.sip.communicator.service.contactsource.ContactQueryListener
import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.gui.event.ContactListListener
import net.java.sip.communicator.service.gui.event.MetaContactQueryListener
import java.awt.Component

/**
 * The `ContactList` interface represents a contact list. All contact
 * list components that need to be available as a service could implement this interface.
 *
 * @author Yana Stamcheva
 */
interface ContactList : ContactQueryListener, MetaContactQueryListener {
    /**
     * Returns the actual component corresponding to the contact list.
     *
     * @return the actual component corresponding to the contact list
     */
    val component: Component?

    /**
     * Returns the list of registered contact sources to search in.
     *
     * @return the list of registered contact sources to search in
     */
    val contactSources: Collection<UIContactSource?>?

    /**
     * Returns the `ExternalContactSource` corresponding to the given `ContactSourceService`.
     *
     * @param contactSource the `ContactSourceService`, which
     * corresponding external source implementation we're looking for
     * @return the `ExternalContactSource` corresponding to the given `ContactSourceService`
     */
    fun getContactSource(contactSource: ContactSourceService?): UIContactSource?

    /**
     * Adds the given contact source to the list of available contact sources.
     *
     * @param contactSource the `ContactSourceService`
     */
    fun addContactSource(contactSource: ContactSourceService?)

    /**
     * Removes the given contact source from the list of available contact sources.
     *
     * @param contactSource
     */
    fun removeContactSource(contactSource: ContactSourceService?)

    /**
     * Removes all stored contact sources.
     */
    fun removeAllContactSources()
    /**
     * Gets the default filter for this contact list.
     *
     * @return the default filter for this contact list
     */
    /**
     * Sets the default filter to the given `filter`.
     *
     * @param filter the `ContactListFilter` to set as default
     */
    var defaultFilter: ContactListFilter?

    /**
     * Returns all `UIContactSource`s of the given type.
     *
     * @param type the type of sources we're looking for
     * @return a list of all `UIContactSource`s of the given type
     */
    fun getContactSources(type: Int): List<UIContactSource?>?

    /**
     * Adds the given group to this list.
     *
     * @param group the `UIGroup` to add
     * @param isSorted indicates if the contact should be sorted regarding to the `GroupNode` policy
     */
    fun addGroup(group: UIGroup?, isSorted: Boolean)

    /**
     * Removes the given group and its children from the list.
     *
     * @param group the `UIGroup` to remove
     */
    fun removeGroup(group: UIGroup?)

    /**
     * Adds the given `contact` to this list.
     *
     * @param contact the `UIContact` to add
     * @param group the `UIGroup` to add to
     * @param isContactSorted indicates if the contact should be sorted
     * regarding to the `GroupNode` policy
     * @param isGroupSorted indicates if the group should be sorted regarding to
     * the `GroupNode` policy in case it doesn't exist and should be dded
     */
    fun addContact(contact: UIContact?,
            group: UIGroup?,
            isContactSorted: Boolean,
            isGroupSorted: Boolean)

    /**
     * Adds the given `contact` to this list.
     *
     * @param query the `ContactQuery` that adds the given contact
     * @param contact the `UIContact` to add
     * @param group the `UIGroup` to add to
     * @param isSorted indicates if the contact should be sorted regarding to
     * the `GroupNode` policy
     */
    fun addContact(query: ContactQuery?,
            contact: UIContact?,
            group: UIGroup?,
            isSorted: Boolean)

    /**
     * Removes the node corresponding to the given `MetaContact` from this list.
     *
     * @param contact the `UIContact` to remove
     * @param removeEmptyGroup whether we should delete the group if is empty
     */
    fun removeContact(contact: UIContact?,
            removeEmptyGroup: Boolean)

    /**
     * Removes the node corresponding to the given `MetaContact` from
     * this list.
     *
     * @param contact the `UIContact` to remove
     */
    fun removeContact(contact: UIContact?)

    /**
     * Removes all entries in this contact list.
     */
    fun removeAll()

    /**
     * Returns a collection of all direct child `UIContact`s of the given
     * `UIGroup`.
     *
     * @param group the parent `UIGroup`
     * @return a collection of all direct child `UIContact`s of the given `UIGroup`
     */
    fun getContacts(group: UIGroup?): Collection<UIContact?>?

    /**
     * Returns the currently applied filter.
     *
     * @return the currently applied filter
     */
    val currentFilter: ContactListFilter?

    /**
     * Returns the currently applied filter.
     *
     * @return the currently applied filter
     */
    val currentFilterQuery: FilterQuery?

    /**
     * Applies the given `filter`.
     *
     * @param filter the `ContactListFilter` to apply.
     * @return the filter query
     */
    fun applyFilter(filter: ContactListFilter?): FilterQuery?

    /**
     * Applies the default filter.
     *
     * @return the filter query that keeps track of the filtering results
     */
    fun applyDefaultFilter(): FilterQuery?
    /**
     * Returns the currently selected `UIContact`. In case of a multiple
     * selection returns the first contact in the selection.
     *
     * @return the currently selected `UIContact` if there's one.
     */
    /**
     * Selects the given `UIContact` in the contact list.
     *
     * @param uiContact the contact to select
     */
    var selectedContact: UIContact?

    /**
     * Returns the list of selected contacts.
     *
     * @return the list of selected contacts
     */
    val selectedContacts: List<UIContact?>?
    /**
     * Returns the currently selected `UIGroup` if there's one.
     *
     * @return the currently selected `UIGroup` if there's one.
     */
    /**
     * Selects the given `UIGroup` in the contact list.
     *
     * @param uiGroup the group to select
     */
    var selectedGroup: UIGroup?

    /**
     * Selects the first found contact node from the beginning of the contact list.
     */
    fun selectFirstContact()

    /**
     * Removes the current selection.
     */
    fun removeSelection()

    /**
     * Adds a listener for `ContactListEvent`s.
     *
     * @param listener the listener to add
     */
    fun addContactListListener(listener: ContactListListener?)

    /**
     * Removes a listener previously added with `addContactListListener`.
     *
     * @param listener the listener to remove
     */
    fun removeContactListListener(listener: ContactListListener?)

    /**
     * Refreshes the given `UIContact`.
     *
     * @param uiContact the contact to refresh
     */
    fun refreshContact(uiContact: UIContact?)

    /**
     * Indicates if this contact list is empty.
     *
     * @return `true` if this contact list contains no children, otherwise returns `false`
     */
    val isEmpty: Boolean
    /**
     * Shows/hides buttons shown in contact row.
     *
     * return `true` to indicate that contact buttons are shown,
     * `false` - otherwise.
     */
    /**
     * Shows/hides buttons shown in contact row.
     *
     * @param isVisible `true` to show contact buttons, `false` - otherwise.
     */
    var isContactButtonsVisible: Boolean

    /**
     * Enables/disables multiple selection.
     *
     * @param isEnabled `true` to enable multiple selection,
     * `false` - otherwise
     */
    fun setMultipleSelectionEnabled(isEnabled: Boolean)

    /**
     * Enables/disables drag operations on this contact list.
     *
     * @param isEnabled `true` to enable drag operations, `false` otherwise
     */
    fun setDragEnabled(isEnabled: Boolean)

    /**
     * Enables/disables the right mouse click menu.
     *
     * @param isEnabled `true` to enable right button menu, `false` otherwise.
     */
    fun setRightButtonMenuEnabled(isEnabled: Boolean)
}