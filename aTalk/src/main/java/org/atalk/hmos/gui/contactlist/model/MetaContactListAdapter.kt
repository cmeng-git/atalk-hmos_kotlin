/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.contactlist.model

import android.text.TextUtils
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import net.java.sip.communicator.service.contactlist.MetaContactListService
import net.java.sip.communicator.service.contactlist.event.MetaContactAvatarUpdateEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactGroupEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactListListener
import net.java.sip.communicator.service.contactlist.event.MetaContactModifiedEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactMovedEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactRenamedEvent
import net.java.sip.communicator.service.contactlist.event.ProtoContactEvent
import net.java.sip.communicator.service.protocol.ContactGroup
import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.chat.ChatSessionManager
import org.atalk.hmos.gui.contactlist.ContactListFragment
import org.atalk.hmos.gui.contactlist.PresenceFilter
import org.atalk.hmos.plugin.timberlog.TimberLog
import timber.log.Timber
import java.util.*
import java.util.regex.Pattern

/**
 * Contact list model is responsible for caching current contact list obtained from contact
 * sources.(It will apply contact source filters which result in different output model).
 *
 * Note: All contactList view update (from events) must be performed on UI thread using UI thread handler;
 * IllegalStateException: The content of the adapter has changed but ListView did not receive a notification.
 * Make sure the content of your adapter is not modified from a background thread, but only from the UI thread.
 * Make sure your adapter calls notifyDataSetChanged() when its content changes.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class MetaContactListAdapter(contactListFragment: ContactListFragment, mainContactList: Boolean) : BaseContactListAdapter(contactListFragment, mainContactList), MetaContactListListener, ContactPresenceStatusListener, UIGroupRenderer {
    /**
     * The list of contact list in original groups before filtered
     */
    private val originalGroups = LinkedList<MetaContactGroup>()

    /**
     * The list of contact list groups for view display
     */
    private val groups = LinkedList<MetaContactGroup>()

    /**
     * The list of original contacts before filtered.
     */
    private val originalContacts = LinkedList<TreeSet<MetaContact>>()

    /**
     * The list of contacts for view display.
     */
    private val contacts = LinkedList<TreeSet<MetaContact>?>()

    /**
     * The `MetaContactListService`, which is the back end of this contact list adapter.
     */
    private var contactListService: MetaContactListService? = null

    /**
     * `MetaContactRenderer` instance used by this adapter.
     */
    private var contactRenderer: MetaContactRenderer? = null

    //    /**
    //     * The default filter is initially set to the PresenceFilter. But anyone
    //     * could change it by calling setDefaultFilter().
    //     */
    //    private final ContactListFilter defaultFilter = presenceFilter;
    //    /**
    //     * The current filter.
    //     */
    //    private final ContactListFilter currentFilter = defaultFilter;
    /**
     * The currently used filter query.
     */
    private var currentFilterQuery: String? = null

    /**
     * if mDialogMode, update only contact status changes, not to filter data with isShownOnline
     */
    private var mDialogMode = false

    /**
     * Initializes the adapter data.
     */
    override fun initModelData() {
        contactListService = AndroidGUIActivator.contactListService
        addContacts(contactListService!!.getRoot(), true)
        contactListService!!.addMetaContactListListener(this)
    }

    /**
     * Releases all resources used by this instance.
     */
    override fun dispose() {
        if (contactListService != null) {
            contactListService!!.removeMetaContactListListener(this)
            removeContacts(contactListService!!.getRoot())
        }
    }

    override fun getGroupCount(): Int {
        return groups.size
    }

    /**
     * Locally implemented UIGroupRenderer
     * {@inheritDoc}
     */
    public override fun getGroupRenderer(groupPosition: Int): UIGroupRenderer {
        return this
    }

    /**
     * {@inheritDoc}
     */
    public override fun getContactRenderer(groupIndex: Int): UIContactRenderer? {
        if (contactRenderer == null) {
            contactRenderer = MetaContactRenderer()
        }
        return contactRenderer
    }

    /**
     * Returns the group at the given `groupPosition`.
     *
     * @param groupPosition the index of the group
     */
    override fun getGroup(groupPosition: Int): Any? {
        return if (groupPosition >= 0 && groupPosition < groups.size) groups[groupPosition]
        else {
            null
        }
    }

    /**
     * Finds group index for given `MetaContactGroup`.
     *
     * @param group the group for which we need the index.
     * @return index of given `MetaContactGroup` or -1 if not found
     */
    fun getGroupIndex(group: MetaContactGroup): Int {
        return groups.indexOf(group)
    }

    /**
     * Finds `MetaContact` index in `MetaContactGroup` identified by given `groupIndex`.
     *
     * @param groupIndex index of group we want to search.
     * @param contact the `MetaContact` to find inside the group.
     * @return index of `MetaContact` inside group identified by given group index.
     */
    fun getChildIndex(groupIndex: Int, contact: MetaContact): Int {
        return getChildIndex(getContactList(groupIndex), contact)
    }

    /**
     * Returns the count of children contained in the group given by the `groupPosition`.
     *
     * @param groupPosition the index of the group, which children we would like to count
     */
    override fun getChildrenCount(groupPosition: Int): Int {
        val contactList = getContactList(groupPosition)
        return contactList?.size ?: 0
    }

    /**
     * Get group contact list from filtered contact list.
     *
     * @param groupIndex contact group index.
     * @return group contact list from filtered contact list.
     */
    private fun getContactList(groupIndex: Int): TreeSet<MetaContact>? {
        return if (groupIndex >= 0 && groupIndex < contacts.size) {
            contacts[groupIndex]
        }
        else {
            null
        }
    }

    /**
     * Get group contact list from original contact list.
     *
     * @param groupIndex contact group index.
     * @return group contact list from original contact list.
     */
    private fun getOriginalCList(groupIndex: Int): TreeSet<MetaContact>? {
        return if (groupIndex >= 0 && groupIndex < originalContacts.size) {
            originalContacts[groupIndex]
        }
        else {
            null
        }
    }

    /**
     * Adds all child contacts for the given `group`. Omit metaGroup of zero child
     *
     * @param group the group, which child contacts to add
     */
    private fun addContacts(group: MetaContactGroup, filtered: Boolean) {
        if (!filtered || group.countChildContacts() > 0) {
            // Add the new metaGroup
            addGroup(group, filtered)

            // Use Iterator to avoid ConcurrentModificationException on addContact()
            val childContacts = group.getChildContacts()!!
            while (childContacts.hasNext()) {
                addContact(group, childContacts.next()!!)
            }
        }
        val subGroups = group.getSubgroups()!!
        while (subGroups.hasNext()) {
            addContacts(subGroups.next()!!, filtered)
        }
    }

    /**
     * Adds the given `group` to both the originalGroups and Groups with
     * zero contact if no existing group is found
     *
     * @param metaGroup the `MetaContactGroup` to add
     * @param filtered false will also create group if not found
     */
    private fun addGroup(metaGroup: MetaContactGroup, filtered: Boolean) {
        if (!originalGroups.contains(metaGroup)) {
            originalGroups.add(metaGroup)
            originalContacts.add(TreeSet<MetaContact>())
        }

        // cmeng: invalidateView causes childContact to be null, contact list not properly updated
        // add new group will have no contact; hence cannot remove the check for isMatching
        if ((!filtered || isMatching(metaGroup, currentFilterQuery)) && !groups.contains(metaGroup)) {
            groups.add(metaGroup)
            contacts.add(TreeSet<MetaContact>())
        }
    }

    /**
     * Remove an existing `group` from both the originalGroups and Groups
     *
     * @param metaGroup the `MetaContactGroup` to be removed
     */
    private fun removeGroup(metaGroup: MetaContactGroup) {
        val origGroupIndex = originalGroups.indexOf(metaGroup)
        if (origGroupIndex != -1) {
            originalContacts.removeAt(origGroupIndex)
            originalGroups.remove(metaGroup)
        }
        val groupIndex = groups.indexOf(metaGroup)
        if (groupIndex != -1) {
            contacts.removeAt(groupIndex)
            groups.remove(metaGroup)
        }
    }

    /**
     * Adds all child contacts for the given `group`.
     *
     * @param metaGroup the parent group of the child contact to add
     * @param metaContact the `MetaContact` to add
     */
    private fun addContact(metaGroup: MetaContactGroup, metaContact: MetaContact) {
        addContactStatusListener(metaContact, this)
        var origGroupIndex = originalGroups.indexOf(metaGroup)
        var groupIndex = groups.indexOf(metaGroup)
        val isMatchingQuery = isMatching(metaContact, currentFilterQuery)

        // Add new group element and update both the Indexes (may be difference)
        if (origGroupIndex < 0 || isMatchingQuery && groupIndex < 0) {
            addGroup(metaGroup, true)
            origGroupIndex = originalGroups.indexOf(metaGroup)
            groupIndex = groups.indexOf(metaGroup)
        }
        val origContactList = getOriginalCList(origGroupIndex)
        if (origContactList != null && getChildIndex(origContactList, metaContact) < 0) {
            origContactList.add(metaContact)
        }

        // cmeng: new group & contact are added only if isMatchingQuery is true
        if (isMatchingQuery) {
            val contactList = getContactList(groupIndex)
            if (contactList != null && getChildIndex(contactList, metaContact) < 0) {
                // do no allow duplication with multiple accounts registration on same server
                //	if (!contactList.contains(metaContact)) ??? not correct test
                contactList.add(metaContact)
            }
        }
    }

    /**
     * Removes the contacts contained in the given group.
     *
     * @param group the `MetaContactGroup`, which contacts we'd like to remove
     */
    private fun removeContacts(group: MetaContactGroup) {
        removeGroup(group)
        val childContacts = group.getChildContacts()
        while (childContacts!!.hasNext()) {
            removeContact(group, childContacts.next()!!)
        }
        val subGroups = group.getSubgroups()
        while (subGroups!!.hasNext()) {
            removeContacts(subGroups.next()!!)
        }
    }

    /**
     * Removes the given `metaContact` from both the original and the filtered list of this adapter.
     *
     * @param metaGroup the parent `MetaContactGroup` of the contact to remove
     * @param metaContact the `MetaContact` to remove
     */
    private fun removeContact(metaGroup: MetaContactGroup, metaContact: MetaContact) {
        removeContactStatusListener(metaContact, this)

        // Remove the contact from the original list and its group if empty.
        val origGroupIndex = originalGroups.indexOf(metaGroup)
        if (origGroupIndex != -1) {
            val origContactList = getOriginalCList(origGroupIndex)
            if (origContactList != null) {
                origContactList.remove(metaContact)
                if (origContactList.isEmpty()) removeGroup(metaGroup)
            }
        }

        // Remove the contact from the filtered list and its group if empty
        val groupIndex = groups.indexOf(metaGroup)
        if (groupIndex != -1) {
            val contactList = getContactList(groupIndex)
            if (contactList != null) {
                contactList.remove(metaContact)
                if (contactList.isEmpty()) removeGroup(metaGroup)
            }
        }
    }

    /**
     * Updates the display name of the given `metaContact`.
     *
     * @param metaContact the `MetaContact`, which display name to update
     */
    private fun updateDisplayName(metaContact: MetaContact) {
        val groupIndex = groups.indexOf(metaContact.getParentMetaContactGroup())
        if (groupIndex >= 0) {
            val contactIndex = getChildIndex(getContactList(groupIndex), metaContact)
            if (contactIndex >= 0) updateDisplayName(groupIndex, contactIndex)
        }
    }

    /**
     * Updates the avatar of the given `metaContact`.
     *
     * @param metaContact the `MetaContact`, which avatar to update
     */
    private fun updateAvatar(metaContact: MetaContact) {
        val groupIndex = groups.indexOf(metaContact.getParentMetaContactGroup())
        if (groupIndex >= 0) {
            val contactIndex = getChildIndex(getContactList(groupIndex), metaContact)
            if (contactIndex >= 0) updateAvatar(groupIndex, contactIndex, metaContact)
        }
    }

    /**
     * Updates the status of the given `metaContact`.
     *
     * @param metaContact the `MetaContact`, which status to update
     */
    private fun updateStatus(metaContact: MetaContact) {
        val groupIndex = groups.indexOf(metaContact.getParentMetaContactGroup())
        if (groupIndex >= 0) {
            val contactIndex = getChildIndex(getContactList(groupIndex), metaContact)
            if (contactIndex >= 0) {
                updateStatus(groupIndex, contactIndex, metaContact)
            }
        }
    }

    /**
     * Indicates that a `MetaContact` has been added to the list.
     *
     * @param evt the `MetaContactEvent` that notified us
     */
    override fun metaContactAdded(evt: MetaContactEvent) {
        Timber.d("CONTACT ADDED: %s", evt.getSourceMetaContact())
        uiHandler.post {
            addContact(evt.getParentGroup()!!, evt.getSourceMetaContact())
            notifyDataSetChanged()
        }
    }

    /**
     * Indicates that a `MetaContact` has been modified.
     *
     * @param evt the `MetaContactEvent` that notified us
     */
    override fun metaContactModified(evt: MetaContactModifiedEvent) {
        Timber.d("META CONTACT MODIFIED: %s", evt.getSourceMetaContact())
        invalidateViews()
    }

    /**
     * Indicates that a `MetaContact` has been removed from the list.
     *
     * @param evt the `MetaContactEvent` that notified us
     */
    override fun metaContactRemoved(evt: MetaContactEvent) {
        Timber.d("CONTACT REMOVED: %s", evt.getSourceMetaContact())
        uiHandler.post {
            removeContact(evt.getParentGroup()!!, evt.getSourceMetaContact())
            notifyDataSetChanged()
        }
    }

    /**
     * Indicates that a `MetaContact` has been moved.
     *
     * @param evt the `MetaContactEvent` that notified us
     */
    override fun metaContactMoved(evt: MetaContactMovedEvent) {
        val oldParent = evt.getOldParent()
        val newParent = evt.getNewParent()
        val metaContact = evt.getSourceMetaContact()
        val destGroup = newParent.getGroupName()
        Timber.d("CONTACT MOVED (%s): %s to %s", metaContact, oldParent, destGroup)

        // Happen when a contact is moved to RootGroup i.e. "Contacts"; RootGroup is not ContactGroupJabberImpl
        if (!groups.contains(newParent)) {
            Timber.w("Add missing move-to group: %s (%s)", destGroup, newParent.getMetaUID())
            addGroup(newParent, false)
        }

        // Modify original group
        var oldGroupIdx = originalGroups.indexOf(oldParent)
        var newGroupIdx = originalGroups.indexOf(newParent)
        if (oldGroupIdx < 0 || newGroupIdx < 0) {
            Timber.e("Move group error for originalGroups, srcGroupIdx: %s, dstGroupIdx: %s (%s)",
                    oldGroupIdx, newGroupIdx, destGroup)
        }
        else {
            val srcGroup = getOriginalCList(oldGroupIdx)
            srcGroup?.remove(metaContact)
            val dstGroup = getOriginalCList(newGroupIdx)
            dstGroup?.add(metaContact)
        }

        // Move results group
        oldGroupIdx = groups.indexOf(oldParent)
        newGroupIdx = groups.indexOf(newParent)
        if (oldGroupIdx < 0 || newGroupIdx < 0) {
            Timber.e("Move group error for groups, srcGroupIdx: %s. dstGroupIdx: %s (%s)",
                    oldGroupIdx, newGroupIdx, destGroup)
        }
        else {
            val srcGroup = getContactList(oldGroupIdx)
            srcGroup?.remove(metaContact)
            val dstGroup = getContactList(newGroupIdx)
            dstGroup?.add(metaContact)

            // Hide oldParent if zero-contacts - not to do this to allow user delete empty new group
            // if (oldParent.countChildContacts() == 0) {
            //    groups.remove(oldParent);
            // }
        }

        // Note: use refreshModelData - create other problems with contacts = null
        uiHandler.post { notifyDataSetChanged() }
    }

    /**
     * Indicates that a `MetaContact` has been removed from the list.
     *
     * @param evt the `MetaContactEvent` that notified us
     */
    override fun metaContactRenamed(evt: MetaContactRenamedEvent) {
        Timber.d("CONTACT RENAMED: %s", evt.getSourceMetaContact())
        uiHandler.post { updateDisplayName(evt.getSourceMetaContact()) }
    }

    /**
     * Indicates that a protocol specific `Contact` has been added to the list.
     *
     * @param evt the `MetaContactEvent` that notified us
     */
    override fun protoContactAdded(evt: ProtoContactEvent) {
        Timber.d("PROTO CONTACT ADDED: %s", evt.getNewParent())
        uiHandler.post { updateStatus(evt.getNewParent()!!) }
    }

    /**
     * Indicates that a protocol specific `Contact` has been renamed.
     *
     * @param evt the `MetaContactEvent` that notified us
     */
    override fun protoContactRenamed(evt: ProtoContactEvent) {
        Timber.d("PROTO CONTACT RENAMED: %s", evt.getProtoContact().address)
        invalidateViews()
    }

    /**
     * Indicates that a protocol specific `Contact` has been modified.
     *
     * @param evt the `MetaContactEvent` that notified us
     */
    override fun protoContactModified(evt: ProtoContactEvent) {
        Timber.d("PROTO CONTACT MODIFIED: %s", evt.getProtoContact().address)
        invalidateViews()
    }

    /**
     * Indicates that a protocol specific `Contact` has been removed from the list.
     *
     * @param evt the `MetaContactEvent` that notified us
     */
    override fun protoContactRemoved(evt: ProtoContactEvent) {
        Timber.d("PROTO CONTACT REMOVED: %s", evt.getProtoContact().address)
        uiHandler.post { updateStatus(evt.getOldParent()) }
    }

    /**
     * Indicates that a protocol specific `Contact` has been moved.
     *
     * @param evt the `MetaContactEvent` that notified us
     */
    override fun protoContactMoved(evt: ProtoContactEvent) {
        Timber.d("PROTO CONTACT MOVED: %s", evt.getProtoContact().address)
        uiHandler.post {
            updateStatus(evt.getOldParent())
            updateStatus(evt.getNewParent()!!)
        }
    }

    /**
     * Indicates that a `MetaContactGroup` has been added to the list.
     * Need to do it asap, as this method is called as sub-dialog of the addContact and MoveContact
     * Otherwise has problem in i.e. both the originalGroups and Groups do not contain the new metaGroup
     *
     * @param evt the `MetaContactEvent` that notified us
     * @see .metaContactMoved
     */
    override fun metaContactGroupAdded(evt: MetaContactGroupEvent) {
        val metaGroup = evt.getSourceMetaContactGroup()
        Timber.d("META CONTACT GROUP ADDED: %s", metaGroup)
        // filtered = false; to add new group to both originalGroups and Groups even with zero contact
        addContacts(metaGroup, false)
        uiHandler.post { notifyDataSetChanged() }
    }

    /**
     * Indicates that a `MetaContactGroup` has been modified.
     *
     * @param evt the `MetaContactEvent` that notified us
     */
    override fun metaContactGroupModified(evt: MetaContactGroupEvent) {
        Timber.d("META CONTACT GROUP MODIFIED: %s", evt.getSourceMetaContactGroup())
        invalidateViews()
    }

    /**
     * Indicates that a `MetaContactGroup` has been removed from the list.
     *
     * @param evt the `MetaContactEvent` that notified us
     */
    override fun metaContactGroupRemoved(evt: MetaContactGroupEvent) {
        Timber.d("META CONTACT GROUP REMOVED: %s", evt.getSourceMetaContactGroup())
        uiHandler.post {
            removeGroup(evt.getSourceMetaContactGroup())
            notifyDataSetChanged()
        }
    }

    /**
     * Indicates that the child contacts of a given `MetaContactGroup` has been reordered.
     * Note:
     * 1. add (insert) new before remove old data to avoid indexOutOfBound
     * 2. synchronized LinkList access to avoid ConcurrentModificationException
     *
     * @param evt the `MetaContactEvent` that notified us
     */
    override fun childContactsReordered(evt: MetaContactGroupEvent) {
        Timber.d("CHILD CONTACTS REORDERED: %s", evt.getSourceMetaContactGroup())
        uiHandler.post {
            val group = evt.getSourceMetaContactGroup()
            val origGroupIndex = originalGroups.indexOf(group)
            val groupIndex = groups.indexOf(group)
            if (origGroupIndex >= 0) {
                val contactList = getOriginalCList(origGroupIndex)
                if (contactList != null) {
                    // Timber.w("Modify originalGroups: " + origGroupIndex + " / " + originalGroups.size());
                    synchronized(originalContacts) {
                        originalContacts.add(origGroupIndex, TreeSet(contactList))
                        originalContacts.removeAt(origGroupIndex + 1)
                    }
                }
            }
            if (groupIndex >= 0) {
                val contactList = getContactList(groupIndex)
                if (contactList != null) {
                    // Timber.w("Modify groups: " + groupIndex + " / " + groups.size());
                    synchronized(contacts) {
                        contacts.add(groupIndex, TreeSet(contactList))
                        contacts.removeAt(groupIndex + 1)
                    }
                }
            }
            notifyDataSetChanged()
        }
    }

    /**
     * Indicates that a `MetaContact` avatar has changed and needs to be updated.
     *
     * @param evt the `MetaContactEvent` that notified us
     */
    override fun metaContactAvatarUpdated(evt: MetaContactAvatarUpdateEvent) {
        Timber.log(TimberLog.FINER, "metaContact avatar updated: %s", evt.getSourceMetaContact())
        uiHandler.post { updateAvatar(evt.getSourceMetaContact()) }
    }

    /**
     * Returns the contained object on the given `groupPosition` and `childPosition`.
     * Note that this method must be called on UI thread.
     *
     * @param groupPosition the index of the group
     * @param childPosition the index of the child
     * @return the contained object on the given `groupPosition` and `childPosition`
     */
    override fun getChild(groupPosition: Int, childPosition: Int): Any? {
        if (contacts.size > 0) {
            val contactList = getContactList(groupPosition)
            if (contactList != null) {
                for ((i, metaContact) in contactList.withIndex()) {
                    if (i == childPosition) {
                        return metaContact
                    }
                }
            }
        }
        return null
    }

    /**
     * Return metaContact index in the contactList
     */
    private fun getChildIndex(contactList: TreeSet<MetaContact>?, metaContact: MetaContact): Int {
        if (contactList == null || contactList.isEmpty()) return -1
        var i = 0
        for (mContact in contactList) {
            if (metaContact == mContact) return i
            i++
        }
        return -1
    }

    /**
     * Filters list data to match the given `query`.
     *
     * @param queryString the query we'd like to match
     */
    override fun filterData(queryString: String) {
        uiHandler.post {
            currentFilterQuery = queryString.lowercase()
            groups.clear()
            contacts.clear()
            if (presenceFilter.isShowOffline() && TextUtils.isEmpty(queryString)) {
                // hide group contains zero contact
                for (metaGroup in originalGroups) {
                    if (metaGroup.countChildContacts() > 0) {
                        val groupIndex = originalGroups.indexOf(metaGroup)
                        groups.add(metaGroup)
                        contacts.add(getOriginalCList(groupIndex))
                    }
                }
            }
            else {
                for (metaGroup in originalGroups) {
                    if (metaGroup.countChildContacts() > 0) {
                        val groupIndex = originalGroups.indexOf(metaGroup)
                        val contactList = getOriginalCList(groupIndex)
                        if (contactList != null) {
                            val filteredList = TreeSet<MetaContact>()
                            for (metaContact in contactList) {
                                if (isMatching(metaContact, queryString)) filteredList.add(metaContact)
                            }
                            if (filteredList.size > 0) {
                                groups.add(metaGroup)
                                contacts.add(filteredList)
                            }
                        }
                    }
                }
            }
            notifyDataSetChanged()
            expandAllGroups()
        }
    }

    /**
     * Create group/contacts TreeView with non-zero groups
     */
    fun nonZeroContactGroupList() {
        groups.clear()
        contacts.clear()

        // hide group contains zero contact
        for (metaGroup in originalGroups) {
            if (metaGroup.countChildContacts() > 0) {
                val groupIndex = originalGroups.indexOf(metaGroup)
                groups.add(metaGroup)
                contacts.add(getOriginalCList(groupIndex))
            }
        }
    }

    /**
     * Checks if the given `metaContact` is matching the given `query`.
     * A `MetaContact` would be matching the filter if one of the following is true:<br></br>
     * - it is online or user chooses show offline contacts
     * - its display name contains the filter string
     * - at least one of its child protocol contacts has a display name or
     * - an address that contains the filter string.
     *
     * @param metaContact the `MetaContact` to check
     * @param query the query string to check for matches
     * @return `true` to indicate that the given `metaContact` is matching the
     * current filter, otherwise returns `false`
     */
    private fun isMatching(metaContact: MetaContact, query: String?): Boolean {
        if (presenceFilter.isMatching(metaContact)) {
            if (TextUtils.isEmpty(query)) return true

            val queryPattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
            if (queryPattern.matcher(metaContact.getDisplayName()).find()) return true
            else {
                val contacts = metaContact.getContacts()
                while (contacts.hasNext()) {
                    val contact = contacts.next()!!
                    if (queryPattern.matcher(contact.displayName).find()
                            || queryPattern.matcher(contact.address).find()) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Checks if the given `metaGroup` is matching the current filter. A group is matching
     * the current filter only if it contains at least one child `MetaContact`, which is
     * matching the current filter.<br></br>
     * Note that this method must be called on UI thread.
     *
     * @param metaGroup the `MetaContactGroup` to check
     * @param query the query string to check for matches
     * @return `true` to indicate that the given `metaGroup` is matching the current
     * filter, otherwise returns `false`
     */
    private fun isMatching(metaGroup: MetaContactGroup, query: String?): Boolean {
        if (presenceFilter.isMatching(metaGroup)) {
            if (TextUtils.isEmpty(query)) return true

            val contacts = metaGroup.getChildContacts()!!
            while (contacts.hasNext()) {
                val metaContact = contacts.next()!!
                if (isMatching(metaContact, query!!)) return true
            }
        }
        return false
    }

    fun setDialogMode(isDialogMode: Boolean) {
        mDialogMode = isDialogMode
    }

    /**
     * Indicates that a contact Presence Status Change has been received.
     *
     * mDialog true indicates the contact list is shown for user multiple selection e.g. invite;
     * in this case do not refreshModelData() to sort, as items selected is tracked by their position
     *
     * @param evt the `ContactPresenceStatusChangeEvent` that notified us
     */
    override fun contactPresenceStatusChanged(evt: ContactPresenceStatusChangeEvent) {
        uiHandler.post {
            //  mDialogMode: just update the status icon without sorting
            if (mDialogMode) {
                val sourceContact = evt.getSourceContact()
                Timber.d("Contact presence status changed: %s", sourceContact.address)
                val metaContact = contactListService!!.findMetaContactByContact(sourceContact)
                // metaContact is already existing, just update it
                if (metaContact != null) {
                    updateStatus(metaContact)
                }
            }
            else {
                refreshModelData()
            }
        }
    }

    /**
     * Refresh the contact list from contactListService, with contact presence status sorted esp originalContacts
     * Then perform filterData if showOffline contacts is disabled, other newly online contacts are not included
     */
    private fun refreshModelData() {
        originalGroups.clear()
        originalContacts.clear()
        groups.clear()
        contacts.clear()
        addContacts(contactListService!!.getRoot(), true)
        if (!presenceFilter.isShowOffline()) {
            filterData("")
        }
    }

    /**
     * Adds the given `ContactPresenceStatusListener` to listen for contact presence status change.
     *
     * @param metaContact the `MetaContact` for which we add the listener
     * @param l the `MessageListener` to add
     */
    private fun addContactStatusListener(metaContact: MetaContact, l: ContactPresenceStatusListener) {
        val protoContacts = metaContact.getContacts()
        while (protoContacts.hasNext()) {
            val protoContact = protoContacts.next()!!
            protoContact.protocolProvider.getOperationSet(OperationSetPresence::class.java)?.addContactPresenceStatusListener(l)
        }
    }

    /**
     * Remove the given `ContactPresenceStatusListener` to listen for contact presence status change.
     *
     * @param metaContact the `MetaContact` for which we remove the listener
     * @param l the `MessageListener` to remove
     */
    private fun removeContactStatusListener(metaContact: MetaContact, l: ContactPresenceStatusListener) {
        val protoContacts = metaContact.getContacts()
        while (protoContacts.hasNext()) {
            val protoContact = protoContacts.next()!!
            protoContact.protocolProvider.getOperationSet(OperationSetPresence::class.java)?.removeContactPresenceStatusListener(l)
        }
    }

    /**
     * Implements [UIGroupRenderer]. {@inheritDoc}
     */
    override fun getDisplayName(groupImpl: Any): String {
        val metaGroup = groupImpl as MetaContactGroup
        return if (metaGroup == contactListService!!.getRoot()) ContactGroup.ROOT_GROUP_NAME else metaGroup.getGroupName()
    } //	/**

    //	 * Sets the default filter to the given <code>filter</code>.
    //	 *
    //	 * @param filter the <code>ContactListFilter</code> to set as default
    //	 */
    //	public void setDefaultFilter(ContactListFilter filter) {
    //		this.defaultFilter = filter;
    //		this.currentFilter = defaultFilter;
    //	}
    //
    //	/**
    //	 * Gets the default filter for this contact list.
    //	 *
    //	 * @return the default filter for this contact list
    //	 */
    //	public ContactListFilter getDefaultFilter() {
    //		return defaultFilter;
    //	}
    //
    //	/**
    //	 * Returns the currently applied filter.
    //	 *
    //	 * @return the currently applied filter
    //	 */
    //	public ContactListFilter getCurrentFilter() {
    //		return currentFilter;
    //	}
    //
    //	/**
    //	 * Returns the currently applied filter.
    //	 *
    //	 * @return the currently applied filter
    //	 */
    //
    //	public String getCurrentFilterQuery() {
    //		return currentFilterQuery;
    //	}
    //
    //	/**
    //	 * Initializes the list of available contact sources for this contact list.
    //	 */
    //	private void initContactSources() {
    //		List<ContactSourceService> contactSources = AndroidGUIActivator.getContactSources();
    //		for (ContactSourceService contactSource : contactSources) {
    //			if (!(contactSource instanceof AsyncContactSourceService)
    //					|| ((AsyncContactSourceService) contactSource).canBeUsedToSearchContacts()) {
    //
    //				// ExternalContactSource extContactSource = new ExternalContactSource(contactSource, this);
    //				int sourceIndex = contactSource.getIndex();
    ////				if (sourceIndex >= 0 && mContactSources.size() >= sourceIndex)
    ////					mContactSources.add(sourceIndex, extContactSource);
    ////				else
    ////					mContactSources.add(extContactSource);
    //			}
    //		}
    ////		AndroidGUIActivator.bundleContext.addServiceListener(new ContactSourceServiceListener());
    //	}
    //
    //	/**
    //	 * Returns the list of registered contact sources to search in.
    //	 *
    //	 * @return the list of registered contact sources to search in
    //	 */
    //	public List<ContactSourceService> getContactSources() {
    //		return mContactSources;
    //	}
    //
    //
    //	/**
    //	 * Adds the given contact source to the list of available contact sources.
    //	 *
    //	 * @param contactSource
    //	 * 		the <code>ContactSourceService</code>
    //	 */
    //	public void addContactSource(ContactSourceService contactSource) {
    ////		if (!(contactSource instanceof AsyncContactSourceService)
    ////				|| ((AsyncContactSourceService) contactSource).canBeUsedToSearchContacts()) {
    ////			mContactSources.add(new ExternalContactSource(contactSource, this));
    ////		}
    //	}
    //
    //	/**
    //	 * Removes the given contact source from the list of available contact
    //	 * sources.
    //	 *
    //	 * @param contactSource
    //	 */
    //	public void removeContactSource(ContactSourceService contactSource) {
    ////		for (ContactSourceService extSource : mContactSources) {
    ////			if (extSource.getContactSourceService().equals(contactSource)) {
    ////				mContactSources.remove(extSource);
    ////				break;
    ////			}
    ////		}
    //	}
    //
    //	/**
    //	 * Removes all stored contact sources.
    //	 */
    //	public void removeAllContactSources() {
    //		mContactSources.clear();
    //	}
    //
    //	/**
    //	 * Returns the notification contact source.
    //	 *
    //	 * @return the notification contact source
    //	 */
    ////	public static NotificationContactSource getNotificationContactSource()
    ////	{
    ////		if (notificationSource == null)
    ////			notificationSource = new NotificationContactSource();
    ////		return notificationSource;
    ////	}
    //
    //	/**
    //	 * Returns the <code>ExternalContactSource</code> corresponding to the given
    //	 * <code>ContactSourceService</code>.
    //	 *
    //	 * @param contactSource the <code>ContactSourceService</code>, which
    //	 * 		corresponding external source implementation we're looking for
    //	 * @return the <code>ExternalContactSource</code> corresponding to the given <code>ContactSourceService</code>
    //	 */
    //	public ContactSourceService getContactSource(ContactSourceService contactSource) {
    ////		for (ContactSourceService extSource : mContactSources) {
    ////			if (extSource.getContactSourceService().equals(contactSource)) {
    ////				return extSource;
    ////			}
    ////		}
    //		return null;
    //	}
    //
    //	/**
    //	 * Returns all <code>UIContactSource</code>s of the given type.
    //	 *
    //	 * @param type the type of sources we're looking for
    //	 * @return a list of all <code>UIContactSource</code>s of the given type
    //	 */
    //	public List<ContactSourceService> getContactSources(int type) {
    ////		List<ContactSourceService> sources = new ArrayList<>();
    //
    ////		for (ContactSourceService extSource : mContactSources) {
    ////			if (extSource.getContactSourceService().getType() == type)
    ////				sources.add(extSource);
    ////		}
    //		return null; // sources;
    //	}
    companion object {
        /**
         * The presence filter.
         */
        val presenceFilter = PresenceFilter()

        /**
         * Checks if given `metaContact` is considered to be selected. That is if the chat
         * session with given `metaContact` is the one currently visible.
         *
         * @param metaContact the `MetaContact` to check.
         * @return `true` if given `metaContact` is considered to be selected.
         */
        fun isContactSelected(metaContact: MetaContact?): Boolean {
            return ChatSessionManager.getCurrentChatId() != null && ChatSessionManager.getActiveChat(metaContact) != null && ChatSessionManager.getCurrentChatId() == ChatSessionManager.getActiveChat(metaContact)!!.chatSession!!.chatId
        }
    }
}