/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.impl.contactlist

import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ContactGroup
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.atalk.hmos.plugin.timberlog.TimberLog
import timber.log.Timber
import java.util.*

/**
 * A straightforward implementation of the meta contact group. The group implements a simple
 * algorithm of sorting its children according to their status.
 *
 * @author Emil Ivov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
class MetaContactGroupImpl @JvmOverloads internal constructor(
        /**
         * The `MetaContactListService` implementation which manages this
         * `MetaContactGroup` and its associated hierarchy.
         */
        val mclServiceImpl: MetaContactListServiceImpl,
        /**
         * The name of the group (fixed for root groups since it won't show).
         */
        private var groupName: String, mcgUID: String? = null) : MetaContactGroup {
    /**
     * All the subgroups that this group contains.
     */
    private val subgroups = TreeSet<MetaContactGroupImpl?>()

    /**
     * A list containing all child contacts.
     */
    private val childContacts = TreeSet<MetaContactImpl?>()

    /**
     * A list of the contact groups encapsulated by this MetaContactGroup
     */
    private val mProtoGroups = Vector<ContactGroup?>()

    /**
     * An id uniquely identifying the meta contact group in this contact list.
     */
    private val groupUID: String

    /**
     * We use this copy for returning iterators and searching over the list in order to avoid
     * creating it upon each query. The copy is updated upon each modification
     */
    private var childContactsOrderedCopy = LinkedList<MetaContact?>()

    /**
     * We use this copy for returning iterators and searching over the list in order to avoid
     * creating it upon each query. The copy is updated upon each modification
     */
    private var subgroupsOrderedCopy = LinkedList<MetaContactGroup?>()

    /**
     * The meta contact group that is currently containing us.
     */
    private var parentMetaContactGroup: MetaContactGroupImpl? = null
    /**
     * Returns the implementation of the `MetaContactListService`, to which this group belongs.
     *
     * @return the implementation of the `MetaContactListService`
     */

    /**
     * The user-specific key-value associations stored in this instance.
     *
     * Like the Widget implementation of Eclipse SWT, the storage type takes into account that
     * there are likely to be many `MetaContactGroupImpl` instances and
     * `Map`s are thus likely to impose increased memory use. While an array may
     * very well perform worse than a `Map` with respect to search, the mechanism of
     * user-defined key-value associations explicitly states that it is not guaranteed to be
     * optimized for any particular use and only covers the most basic cases and
     * performance-savvy code will likely implement a more optimized solution anyway.
     *
     */
    private var data: Array<Any?>? = null

    /**
     * Creates an instance of the root meta contact group assigning it the specified meta contact
     * uid. This constructor MUST NOT be used for any other purposes except restoring contacts
     * extracted from the database
     *
     * @param mclServiceImpl the implementation of the `MetaContactListService`, to which this group belongs
     * @param groupName the name of the group to create
     * @param mcgUID a metaContact UID that has been stored earlier or null when a new UID needs to be created.
     */
    /**
     * Creates an instance of the root meta contact group.
     *
     * mclServiceImpl the `MetaContactListService` implementation which is to use the new
     * `MetaContactGroup` instance as its root groupName the name of the group to create
     */
    init {
        groupUID = mcgUID ?: (System.currentTimeMillis().toString() + hashCode().toString())
    }

    /**
     * Returns a String identifier (the actual contents is left to implementations) that uniquely
     * represents this `MetaContactGroup` in the containing `MetaContactList`
     *
     * @return a String uniquely identifying this metaContactGroup.
     */
    override fun getMetaUID(): String {
        return groupUID
    }

    /**
     * Returns the MetaContactGroup currently containing this group or null/aTalk if this is the
     * root group
     *
     * @return a reference to the MetaContactGroup currently containing this meta contact group or
     * null if this is the root group.
     */
    override fun getParentMetaContactGroup(): MetaContactGroup? {
        return parentMetaContactGroup
    }

    /**
     * Determines whether or not this group can contain subgroups.
     *
     * @return always `true` since this is the root contact group and in our impl it can
     * only contain groups.
     */
    override fun canContainSubgroups(): Boolean {
        return false
    }

    /**
     * Returns the number of `MetaContact`s that this group contains.
     *
     *
     * @return the number of `MetaContact`s that this group contains.
     */
    override fun countChildContacts(): Int {
        return childContacts.size
    }

    /**
     * Returns the number of online `MetaContact`s that this group contains.
     *
     *
     * @return the number of online `MetaContact`s that this group contains.
     */
    override fun countOnlineChildContacts(): Int {
        var onlineContactsNumber = 0
        try {
            val itr = getChildContacts()
            while (itr.hasNext()) {
                val contact = itr.next()!!.getDefaultContact() ?: continue
                if (contact.presenceStatus.isOnline) {
                    onlineContactsNumber++
                }
            }
        } catch (e: Exception) {
            Timber.d(e, "Failed to count online contacts.")
        }
        return onlineContactsNumber
    }

    /**
     * Returns the number of `ContactGroups`s that this group encapsulates
     *
     *
     * @return an int indicating the number of ContactGroups-s that this group encapsulates.
     */
    override fun countContactGroups(): Int {
        return mProtoGroups.size
    }

    /**
     * Returns the number of subgroups that this `MetaContactGroup` contains.
     *
     * @return an int indicating the number of subgroups in this group.
     */
    override fun countSubgroups(): Int {
        return subgroups.size
    }

    /**
     * Returns a `java.util.Iterator` over the `MetaContact`s contained in this
     * `MetaContactGroup`.
     *
     * In order to prevent problems with concurrency, the `Iterator` returned by this
     * method is not over the actual list of groups but over a copy of that list.
     *
     *
     * @return a `java.util.Iterator` over an empty contacts list.
     */
    override fun getChildContacts(): Iterator<MetaContact?> {
        return childContactsOrderedCopy.iterator()
    }

    /**
     * Returns the contact with the specified identifier
     *
     * @param metaContactId a String identifier obtained through the `MetaContact.getMetaUID()` method.
     *
     * @return the `MetaContact` with the specified identifier.
     */
    override fun getMetaContact(metaContactId: String?): MetaContact? {
        val contactsIter = getChildContacts()
        while (contactsIter.hasNext()) {
            val contact = contactsIter.next()
            if (contact!!.getMetaUID() == metaContactId) return contact
        }
        return null
    }

    /**
     * Returns the index of metaContact according to other contacts in this or -1 if metaContact
     * does not belong to this group. The returned index is only valid until another contact has
     * been added / removed or a contact has changed its status and hence - position. In such
     * a case a REORDERED event is fired.
     *
     * @param metaContact the `MetaContact` whose index we're looking for.
     * @return the index of `metaContact` in the list of child contacts or -1 if
     * `metaContact`.
     */
    override fun indexOf(metaContact: MetaContact?): Int {
        var i = 0
        val childrenIter = getChildContacts()
        while (childrenIter.hasNext()) {
            val current = childrenIter.next()
            if (current === metaContact) {
                return i
            }
            i++
        }
        // if we got here then metaContact is not in this list
        return -1
    }

    /**
     * Returns the index of metaContactGroup in relation to other subgroups in this group or -1 if
     * metaContact does not belong to this group. The returned index is only valid until another
     * group has been added / removed or renamed In such a case a REORDERED event is fired.
     *
     * @param metaContactGroup the `MetaContactGroup` whose index we're looking for.
     * @return the index of `metaContactGroup` in the list of child contacts or -1 if
     * `metaContact`.
     */
    override fun indexOf(metaContactGroup: MetaContactGroup?): Int {
        var i = 0
        val childrenIter = getSubgroups()
        while (childrenIter.hasNext()) {
            val current = childrenIter.next()
            if (current === metaContactGroup) {
                return i
            }
            i++
        }
        // if we got here then metaContactGroup is not in this list
        return -1
    }

    /**
     * Returns the meta contact encapsulating a contact belonging to the specified
     * `provider` with the specified identifier.
     *
     * @param provider the ProtocolProviderService that the specified `contactID` is pertaining to.
     * @param contactID a String identifier of the protocol specific contact whose container meta contact
     * we're looking for.
     * @return the `MetaContact` with the specified identifier.
     */
    override fun getMetaContact(provider: ProtocolProviderService?, contactID: String?): MetaContact? {
        val metaContactsIter = getChildContacts()
        while (metaContactsIter.hasNext()) {
            val metaContact = metaContactsIter.next()
            if (metaContact!!.getContact(contactID, provider) != null) return metaContact
        }
        return null
    }

    /**
     * Returns a meta contact, a child of this group or its subgroups, that has the specified
     * metaUID. If no such meta contact exists, the method would return null.
     *
     * @param metaUID the Meta UID of the contact we're looking for.
     * @return the MetaContact with the specified UID or null if no such contact exists.
     */
    fun findMetaContactByMetaUID(metaUID: String?): MetaContact? {
        // first go through the contacts that are direct children of this method.
        val contactsIter = getChildContacts()
        while (contactsIter.hasNext()) {
            val metaContact = contactsIter.next()
            if (metaContact!!.getMetaUID() == metaUID) return metaContact
        }

        // if we didn't find it here, let's try in the subgroups
        val groupsIter = getSubgroups()
        while (groupsIter.hasNext()) {
            val mGroup = groupsIter.next() as MetaContactGroupImpl?
            val metaContact = mGroup!!.findMetaContactByMetaUID(metaUID)
            if (metaContact != null) return metaContact
        }
        return null
    }

    /**
     * Returns a meta contact group this group or some of its subgroups, that has the specified
     * metaUID. If no such meta contact group exists, the method would return null.
     *
     * @param metaUID the Meta UID of the contact group we're looking for.
     * @return the MetaContactGroup with the specified UID or null if no such contact exists.
     */
    fun findMetaContactGroupByMetaUID(metaUID: String?): MetaContactGroup? {
        if (metaUID == groupUID) return this

        // if we didn't find it here, let's try in the subgroups
        val groupsIter = getSubgroups()
        while (groupsIter.hasNext()) {
            val mGroup = groupsIter.next() as MetaContactGroupImpl?
            if (metaUID == mGroup!!.getMetaUID()) return mGroup else mGroup.findMetaContactByMetaUID(metaUID)
        }
        return null
    }

    /**
     * Returns an iterator over all the protocol specific groups that this contact group
     * represents.
     *
     * In order to prevent problems with concurrency, the `Iterator` returned by this
     * method is not over the actual list of groups but over a copy of that list.
     *
     *
     * @return an Iterator over the protocol specific groups that this group represents.
     */
    override fun getContactGroups(): Iterator<ContactGroup?> {
        return LinkedList(mProtoGroups).iterator()
    }

    /**
     * Returns a contact group encapsulated by this meta contact group, having the specified
     * groupName and coming from the indicated ownerProvider.
     *
     * @param groupName the name of the contact group who we're looking for.
     * @param ownerProvider a reference to the ProtocolProviderService that the contact we're looking for belongs
     * to.
     * @return a reference to a `ContactGroup`, encapsulated by this MetaContactGroup,
     * carrying the specified name and originating from the specified ownerProvider or null if no
     * such contact group was found.
     */
    override fun getContactGroup(groupName: String?, ownerProvider: ProtocolProviderService?): ContactGroup? {
        val encapsulatedGroups = getContactGroups()
        while (encapsulatedGroups.hasNext()) {
            val group = encapsulatedGroups.next()
            if (group!!.getGroupName() == groupName && group.getProtocolProvider() === ownerProvider) {
                return group
            }
        }
        return null
    }

    /**
     * Returns all protocol specific ContactGroups, encapsulated by this MetaContactGroup and
     * coming from the indicated ProtocolProviderService. If none of the contactGroups encapsulated by
     * this MetaContact is originating from the specified provider then an empty iterator is returned.
     *
     * @param provider a reference to the `ProtocolProviderService` whose ContactGroups we'd like to get.
     * @return an `Iterator` over all contacts encapsulated in this `MetaContact`
     * and originating from the specified provider.
     */
    override fun getContactGroupsForProvider(provider: ProtocolProviderService?): Iterator<ContactGroup?> {
        val encapsulatedGroups = getContactGroups()
        val protoGroups = LinkedList<ContactGroup?>()
        while (encapsulatedGroups.hasNext()) {
            val group = encapsulatedGroups.next()
            if (group!!.getProtocolProvider() === provider) {
                protoGroups.add(group)
            }
        }
        return protoGroups.iterator()
    }

    /**
     * Returns all protocol specific ContactGroups, encapsulated by this MetaContactGroup and
     * coming from the provider matching the `accountID` param. If none of the contacts
     * encapsulated by this MetaContact is originating from the specified account then an
     * empty iterator is returned.
     *
     * Note to implementers: In order to prevent problems with concurrency, the `Iterator`
     * returned by this method should not be over the actual list of groups but rather over a
     * copy of that list.
     *
     * @param accountID the id of the account whose contact groups we'd like to retrieve.
     * @return an `Iterator` over all contacts encapsulated in this `MetaContact`
     * and originating from the provider with the specified account id.
     */
    override fun getContactGroupsForAccountID(accountID: String?): Iterator<ContactGroup?> {
        val encapsulatedGroups = getContactGroups()
        val protoGroups = LinkedList<ContactGroup?>()
        while (encapsulatedGroups.hasNext()) {
            val group = encapsulatedGroups.next()
            if (group!!.getProtocolProvider()!!.accountID.accountUniqueID
                    == accountID) {
                protoGroups.add(group)
            }
        }
        return protoGroups.iterator()
    }

    /**
     * Returns a meta contact, a child of this group or its subgroups, that has the specified
     * protocol specific contact. If no such meta contact exists, the method would return null.
     *
     * @param protoContact the protocol specific contact whom meta contact we're looking for.
     * @return the MetaContactImpl that contains the specified protocol specific contact.
     */
    fun findMetaContactByContact(protoContact: Contact?): MetaContact? {
        // first go through the contacts that are direct children of this method.
        val contactsIter = getChildContacts()
        while (contactsIter.hasNext()) {
            val mContact = contactsIter.next()
            val storedProtoContact = mContact!!.getContact(protoContact!!.address, protoContact.protocolProvider)
            if (storedProtoContact != null) return mContact
        }

        // if we didn't find it here, let's try in the subgroups
        val groupsIter = getSubgroups()
        while (groupsIter.hasNext()) {
            val mGroup = groupsIter.next() as MetaContactGroupImpl?
            val mContact = mGroup!!.findMetaContactByContact(protoContact)
            if (mContact != null) return mContact
        }
        return null
    }

    /**
     * Returns a meta contact, a child of this group or its subgroups, with address equald to
     * `contactAddress` and a source protocol provider with the matching
     * `accountID`. If no such meta contact exists, the method would return null.
     *
     * @param contactAddress the address of the protocol specific contact whose meta contact we're looking for.
     * @param accountID the ID of the account that the contact we are looking for must belong to.
     * @return the MetaContactImpl that contains the specified protocol specific contact.
     */
    fun findMetaContactByContact(contactAddress: String?, accountID: String): MetaContact? {
        // first go through the contacts that are direct children of this method.
        val contactsIter = getChildContacts()
        while (contactsIter.hasNext()) {
            val mContact = contactsIter.next() as MetaContactImpl?
            val storedProtoContact = mContact!!.getContact(contactAddress, accountID)
            if (storedProtoContact != null) return mContact
        }

        // if we didn't find it here, let's try in the subgroups
        val groupsIter = getSubgroups()
        while (groupsIter.hasNext()) {
            val mGroup = groupsIter.next() as MetaContactGroupImpl?
            val mContact = mGroup!!.findMetaContactByContact(contactAddress, accountID)
            if (mContact != null) return mContact
        }
        return null
    }

    /**
     * Returns a meta contact group, encapsulated by this group or its subgroups, that has the
     * specified protocol specific contact. If no such meta contact group exists, the method
     * would return null.
     *
     * @param protoContactGroup the protocol specific contact group whose meta contact group we're looking for.
     * @return the MetaContactImpl that contains the specified protocol specific contact.
     */
    fun findMetaContactGroupByContactGroup(protoContactGroup: ContactGroup?): MetaContactGroupImpl? {
        // first check here, in this meta group
        if (mProtoGroups.contains(protoContactGroup)) return this

        // if we didn't find it here, let's try in the subgroups
        val groupsIter = getSubgroups()
        while (groupsIter.hasNext()) {
            val mGroup = groupsIter.next() as MetaContactGroupImpl?
            val foundMetaContactGroup = mGroup!!.findMetaContactGroupByContactGroup(protoContactGroup)
            if (foundMetaContactGroup != null) return foundMetaContactGroup
        }
        return null
    }

    /**
     * Returns the meta contact on the specified index.
     *
     * @param index the index of the meta contact to return.
     * @return the MetaContact with the specified index,
     *
     * @throws IndexOutOfBoundsException in case `index` is not a valid index for this group.
     */
    @Throws(IndexOutOfBoundsException::class)
    override fun getMetaContact(index: Int): MetaContact? {
        return childContactsOrderedCopy[index]
    }

    /**
     * Adds the specified `metaContact` to ths local list of child contacts.
     *
     * @param metaContact the `MetaContact` to add in the local vector.
     */
    fun addMetaContact(metaContact: MetaContactImpl) {
        // set this group as a callback in the meta contact
        metaContact.parentGroup = this
        lightAddMetaContact(metaContact)
    }

    /**
     * Adds the `metaContact` to the local list of child contacts without setting its
     * parent contact and without any synchronization. This method is meant for use _PRIMARILY_
     * by the `MetaContact` itself upon change in its encapsulated protocol specific
     * contacts.
     *
     * @param metaContact the `MetaContact` to add in the local vector.
     * @return the index at which the contact was added.
     */
    fun lightAddMetaContact(metaContact: MetaContactImpl?): Int {
        synchronized(childContacts) {
            childContacts.add(metaContact)
            // no need to synch it's not a disaster if s.o. else reads the old copy.
            childContactsOrderedCopy = LinkedList<MetaContact?>(childContacts)
            return childContactsOrderedCopy.indexOf(metaContact)
        }
    }

    /**
     * Removes the `metaContact` from the local list of child contacts without unsetting
     * synchronization. This method is meant for use _PRIMARILY_ by the `MetaContact`
     * itself upon change in its encapsulated protocol specific contacts. The method would also
     * regenerate the ordered copy used for generating iterators and performing search operations
     * over the group.
     *
     * @param metaContact the `MetaContact` to remove from the local vector.
     */
    fun lightRemoveMetaContact(metaContact: MetaContactImpl?) {
        synchronized(childContacts) {
            childContacts.remove(metaContact)
            // no need to sync it's not a disaster if s.o. else reads the old copy.
            childContactsOrderedCopy = LinkedList<MetaContact?>(childContacts)
        }
    }

    /**
     * Removes the specified `metaContact` from the local list of contacts.
     *
     * @param metaContact the `MetaContact`
     */
    fun removeMetaContact(metaContact: MetaContactImpl?) {
        metaContact!!.unsetParentGroup(this)
        lightRemoveMetaContact(metaContact)
    }

    /**
     * Returns the `MetaContactGroup` with the specified index.
     *
     *
     * @param index the index of the group to return.
     * @return the `MetaContactGroup` with the specified index.
     *
     * @throws IndexOutOfBoundsException if `index` is not a valid index.
     */
    @Throws(IndexOutOfBoundsException::class)
    override fun getMetaContactSubgroup(index: Int): MetaContactGroup? {
        return subgroupsOrderedCopy[index]
    }

    /**
     * Returns the `MetaContactGroup` with the specified name.
     *
     * @param groupName the name of the group to return.
     * @return the `MetaContactGroup` with the specified name or null if no such group
     * exists.
     */
    override fun getMetaContactSubgroup(groupName: String?): MetaContactGroup? {
        val groupsIter = getSubgroups()
        while (groupsIter.hasNext()) {
            val mcGroup = groupsIter.next()
            if (mcGroup!!.getGroupName() == groupName) return mcGroup
        }
        return null
    }

    /**
     * Returns the `MetaContactGroup` with the specified groupUID.
     *
     * @param grpUID the uid of the group to return.
     * @return the `MetaContactGroup` with the specified uid or null if no such group
     * exists.
     */
    fun getMetaContactSubgroupByUID(grpUID: String): MetaContactGroup? {
        val groupsIter = getSubgroups()
        while (groupsIter.hasNext()) {
            val mcGroup = groupsIter.next()
            if (mcGroup!!.getMetaUID() == grpUID) return mcGroup
        }
        return null
    }

    /**
     * Returns true if and only if `contact` is a direct child of this group.
     *
     * @param metaContact the `MetaContact` whose relation to this group we'd like to determine.
     * @return `true` if `contact` is a direct child of this group and
     * `false` otherwise.
     */
    override fun contains(metaContact: MetaContact?): Boolean {
        synchronized(childContacts) { return childContacts.contains(metaContact) }
    }

    /**
     * Returns true if and only if `group` is a direct subgroup of this
     * `MetaContactGroup`.
     *
     * @param group the `MetaContactGroup` whose relation to this group we'd like to determine.
     * @return `true` if `group` is a direct child of this `MetaContactGroup`
     * and `false` otherwise.
     */
    override fun contains(group: MetaContactGroup?): Boolean {
        return subgroups.contains(group)
    }

    /**
     * Returns an `java.util.Iterator` over the sub groups that this
     * `MetaContactGroup` contains.
     *
     * In order to prevent problems with concurrency, the `Iterator` returned by this
     * method is not over the actual list of groups but over a copy of that list.
     *
     *
     * @return a `java.util.Iterator` containing all subgroups.
     */
    override fun getSubgroups(): Iterator<MetaContactGroup?> {
        return subgroupsOrderedCopy.iterator()
    }

    /**
     * Returns the name of this group.
     *
     * @return a String containing the name of this group.
     */
    override fun getGroupName(): String {
        return groupName
    }

    /**
     * Sets the name of this group.
     *
     * @param newGroupName a String containing the new name of this group.
     */
    fun setGroupName(newGroupName: String) {
        groupName = newGroupName
    }

    /**
     * Returns a String representation of this group and the contacts it contains (may turn out to
     * be a relatively long string).
     *
     * @return a String representing this group and its child contacts.
     */
    override fun toString(): String {
        val buff = StringBuilder(getGroupName())
        buff.append(".subGroups=${countSubgroups()}:\n")
    
        val subGroups = getSubgroups()
        while (subGroups.hasNext()) {
            val group = subGroups.next()
            buff.append(group!!.getGroupName())
            if (subGroups.hasNext()) buff.append("\n")
        }
        buff.append("\nProtoGroups=${countContactGroups()}:[")
        val contactGroups = getContactGroups()
        while (contactGroups.hasNext()) {
            val contactGroup = contactGroups.next()
            buff.append(contactGroup!!.getProtocolProvider())
            buff.append(".")
            buff.append(contactGroup.getGroupName())
            if (contactGroups.hasNext()) buff.append(", ")
        }
        buff.append("]")
        buff.append("RootChildContacts=${countChildContacts()}:[")
        val contacts = getChildContacts()
        while (contacts.hasNext()) {
            val contact = contacts.next()
            buff.append(contact.toString())
            if (contacts.hasNext()) buff.append(", ")
        }
        return buff.append("]").toString()
    }

    /**
     * Adds the specified group to the list of protocol specific groups that we're encapsulating
     * in this meta contact group.
     *
     * @param protoGroup the root to add to the groups merged in this meta contact group.
     */
    fun addProtoGroup(protoGroup: ContactGroup?) {
        mProtoGroups.add(protoGroup)
    }

    /**
     * Removes the specified group from the list of protocol specific groups that we're
     * encapsulating in this meta contact group.
     *
     * @param protoGroup the group to remove from the groups merged in this meta contact group.
     */
    fun removeProtoGroup(protoGroup: ContactGroup?) {
        mProtoGroups.remove(protoGroup)
    }

    /**
     * Adds the specified meta group to the subgroups of this one.
     *
     * @param subgroup the MetaContactGroup to register as a subgroup to this root meta contact group.
     */
    fun addSubgroup(subgroup: MetaContactGroup) {
        Timber.log(TimberLog.FINER, "Adding subgroup %s to %s", subgroup.getGroupName(), getGroupName())
        subgroups.add(subgroup as MetaContactGroupImpl)
        subgroup.parentMetaContactGroup = this
        subgroupsOrderedCopy = LinkedList<MetaContactGroup?>(subgroups)
    }

    /**
     * Removes the meta contact group with the specified index.
     *
     * @param index the index of the group to remove.
     * @return the `MetaContactGroup` that has just been removed.
     */
    private fun removeSubgroup(index: Int): MetaContactGroupImpl? {
        val subgroup = subgroupsOrderedCopy[index] as MetaContactGroupImpl?
        if (subgroups.remove(subgroup)) subgroup!!.parentMetaContactGroup = null
        subgroupsOrderedCopy = LinkedList<MetaContactGroup?>(subgroups)
        return subgroup
    }

    /**
     * Removes the specified group from the list of groups in this list.
     *
     * @param group the `MetaContactGroup` to remove.
     * @return true if the group has been successfully removed and false otherwise.
     */
    fun removeSubgroup(group: MetaContactGroup?): Boolean {
        return if (subgroups.contains(group)) {
            removeSubgroup(subgroupsOrderedCopy.indexOf(group))
            true
        } else {
            false
        }
    }

    /**
     * Implements [MetaContactGroup.getData].
     *
     * @return the data value corresponding to the given key
     */
    override fun getData(key: Any?): Any? {
        if (key == null) throw NullPointerException("key")
        val index = dataIndexOf(key)
        return if (index == -1) null else data!![index + 1]
    }

    /**
     * Implements [MetaContactGroup.setData].
     *
     * @param key the of the data
     * @param value the value of the data
     */
    override fun setData(key: Any?, value: Any?) {
        if (key == null) throw NullPointerException("key")
        val index = dataIndexOf(key)
        if (index == -1) {
            /*
             * If value is null, remove the association with key (or just don't add it).
             */
            if (data == null) {
                if (value != null) data = arrayOf(key, value)
            } else if (value == null) {
                val length = data!!.size - 2
                data = if (length > 0) {
                    val newData = arrayOfNulls<Any>(length)
                    System.arraycopy(data as Array<Any?>, 0, newData, 0, index)
                    System.arraycopy(data as Array<Any?>, index + 2, newData, index, length - index)
                    newData
                } else null
            } else {
                var length = data!!.size
                val newData = arrayOfNulls<Any>(length + 2)
                System.arraycopy(data as Array<Any?>, 0, newData, 0, length)
                data = newData
                data!![length++] = key
                data!![length] = value
            }
        } else data!![index + 1] = value
    }

    /**
     * Determines whether or not this meta group contains only groups that are being stored by a
     * server.
     *
     * @return true if the meta group is persistent and false otherwise.
     */
    override fun isPersistent(): Boolean {
        val contactGroupsIter = getContactGroups()
        while (contactGroupsIter.hasNext()) {
            val contactGroup = contactGroupsIter.next()
            if (contactGroup!!.isPersistent()) return true
        }

        // this is new and empty group, we can store it as user want this
        return countContactGroups() == 0
    }

    /**
     * Determines the index in `#data` of a specific key.
     *
     * @param key the key to retrieve the index in `#data` of
     * @return the index in `#data` of the specified `key` if it is
     * contained; `-1` if `key` is not
     * contained in `#data`
     */
    private fun dataIndexOf(key: Any): Int {
        if (data != null) {
            var index = 0
            while (index < data!!.size) {
                if (key == data!![index]) return index
                index += 2
            }
        }
        return -1
    }

    /**
     * Compares this meta contact group with the specified object for order. Returns a negative
     * integer, zero, or a positive integer as this meta contact group is less than, equal to, or
     * greater than the specified object.
     *
     * The result of this method is calculated the following way:
     *
     * + getGroupName().compareTo(o.getGroupName()) * 10 000 + getMetaUID().compareTo(o.getMetaUID
     * ())<br></br>
     *
     * Or in other words ordering of meta groups would be first done by display name, and finally
     * (in order to avoid equalities) be the fairly random meta contact group metaUID.
     *
     *
     * @param other the `MetaContactGroup` to be compared.
     * @return a negative integer, zero, or a positive integer as this object is less than, equal
     * to, or greater than the specified object.
     */
    override operator fun compareTo(other: MetaContactGroup?): Int {
        return (getGroupName().compareTo(other!!.getGroupName(), ignoreCase = true) * 10000
                + getMetaUID().compareTo(other.getMetaUID()!!))
    }
}