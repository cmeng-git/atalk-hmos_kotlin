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
package net.java.sip.communicator.service.contactlist

import net.java.sip.communicator.service.protocol.ContactGroup
import net.java.sip.communicator.service.protocol.ProtocolProviderService

/**
 * `MetaContactGroup`s are used to merge groups (often originating in different protocols).
 *
 * A `MetaContactGroup` may contain contacts and some groups may
 * also have sub-groups as children. To verify whether or not a particular
 * group may contain subgroups, a developer has to call the `canContainSubgroups()` method
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface MetaContactGroup : Comparable<MetaContactGroup?> {
    /**
     * Returns an iterator over all the protocol specific groups that this
     * contact group represents.
     *
     * Note to implementors:  In order to prevent problems with concurrency, the
     * `Iterator` returned by this method should not be over the actual
     * list of groups but rather over a copy of that list.
     *
     * @return an Iterator over the protocol specific groups that this group represents.
     */
    fun getContactGroups(): Iterator<ContactGroup?>?

    /**
     * Returns all protocol specific ContactGroups, encapsulated by this
     * MetaContactGroup and coming from the indicated ProtocolProviderService.
     * If none of the contacts encapsulated by this MetaContact is originating
     * from the specified provider then an empty iterator is returned.
     *
     * Note to implementors:  In order to prevent problems with concurrency, the
     * `Iterator` returned by this method should not be over the actual
     * list of groups but rather over a copy of that list.
     *
     * @param provider a reference to the `ProtocolProviderService`
     * whose ContactGroups we'd like to get.
     * @return an `Iterator` over all contacts encapsulated in this
     * `MetaContact` and originating from the specified provider.
     */
    fun getContactGroupsForProvider(provider: ProtocolProviderService?): Iterator<ContactGroup?>?

    /**
     * Returns all protocol specific ContactGroups, encapsulated by this
     * MetaContactGroup and coming from the provider matching the
     * `accountID` param. If none of the contacts encapsulated by this
     * MetaContact is originating from the specified account then an empty
     * iterator is returned.
     *
     * Note to implementors:  In order to prevent problems with concurrency, the
     * `Iterator` returned by this method should not be over the actual
     * list of groups but rather over a copy of that list.
     * *
     *
     * @param accountID the id of the account whose contact groups we'd like to retrieve.
     * @return an `Iterator` over all contacts encapsulated in this
     * `MetaContact` and originating from the provider with the specified account id.
     */
    fun getContactGroupsForAccountID(accountID: String?): Iterator<ContactGroup?>?

    /**
     * Returns true if and only if `contact` is a direct child of this group.
     *
     * @param metaContact the `MetaContact` whose relation to this group we'd like to determine.
     * @return `true` if `contact` is a direct child of this group and `false` otherwise.
     */
    operator fun contains(metaContact: MetaContact?): Boolean

    /**
     * Returns true if and only if `group` is a direct subgroup of this `MetaContactGroup`.
     *
     * @param group the `MetaContactGroup` whose relation to this group we'd like to determine.
     * @return `true` if `group` is a direct child of this
     * `MetaContactGroup` and `false` otherwise.
     */
    operator fun contains(group: MetaContactGroup?): Boolean

    /**
     * Returns a contact group encapsulated by this meta contact group, having
     * the specified groupName and coming from the indicated ownerProvider.
     *
     * @param groupName the name of the contact group who we're looking for.
     * @param ownerProvider a reference to the ProtocolProviderService that
     * the contact we're looking for belongs to.
     * @return a reference to a `ContactGroup`, encapsulated by this
     * MetaContactGroup, carrying the specified name and originating from the specified ownerProvider.
     */
    fun getContactGroup(groupName: String?, ownerProvider: ProtocolProviderService?): ContactGroup?

    /**
     * Returns a `java.util.Iterator` over the `MetaContact`s
     * contained in this `MetaContactGroup`.
     *
     * Note to implementors:  In order to prevent problems with concurrency, the
     * `Iterator` returned by this method should not be over the actual
     * list of contacts but rather over a copy of that list.
     *
     * @return a `java.util.Iterator` over the `MetaContacts` in this group.
     */
    fun getChildContacts(): Iterator<MetaContact?>?

    /**
     * Returns the number of `MetaContact`s that this group contains
     *
     * @return an int indicating the number of MetaContact-s that this group contains.
     */
    fun countChildContacts(): Int

    /**
     * Returns the number of online `MetaContact`s that this group
     * contains.
     *
     * @return the number of online `MetaContact`s that this group
     * contains.
     */
    fun countOnlineChildContacts(): Int

    /**
     * Returns the number of `ContactGroups`s that this group encapsulates
     *
     * @return an int indicating the number of ContactGroups-s that this group encapsulates.
     */
    fun countContactGroups(): Int

    /**
     * Returns an `java.util.Iterator` over the sub groups that this
     * `MetaContactGroup` contains. Not all `MetaContactGroup`s
     * can have sub groups. In case there are no subgroups in this
     * `MetaContactGroup`, the method would return an empty list.
     * The `canContainSubgroups()` method allows us to verify whether
     * this is the case with the group at hand.
     *
     * Note to implementors:  In order to prevent problems with concurrency, the
     * `Iterator` returned by this method should not be over the actual
     * list of groups but rather over a copy of that list.
     *
     * @return a `java.util.Iterator` containing all subgroups.
     */
    fun getSubgroups(): Iterator<MetaContactGroup?>?

    /**
     * Returns the number of subgroups that this `MetaContactGroup` contains.
     *
     * @return an int indicating the number of subgroups in this group.
     */
    fun countSubgroups(): Int

    /**
     * Determines whether or not this group can contain subgroups. The method
     * should be called before creating subgroups in order to avoid invalid argument exceptions.
     *
     * @return `true` if this groups can contain subgroups and
     * `false` otherwise.
     */
    fun canContainSubgroups(): Boolean

    /**
     * Returns the meta contact encapsulating a contact belonging to the
     * specified `provider` with the specified identifier.
     *
     * @param provider the ProtocolProviderService that the specified `contactID` is pertaining to.
     * @param contactID a String identifier of the protocol specific contact
     * whose container meta contact we're looking for.
     * @return the `MetaContact` with the specified identifier.
     */
    fun getMetaContact(provider: ProtocolProviderService?, contactID: String?): MetaContact?

    /**
     * Returns the contact with the specified identifier
     *
     * @param metaContactId a String identifier obtained through the `MetaContact.getMetaUID()` method.
     * @return the `MetaContact` with the specified identifier.
     */
    fun getMetaContact(metaContactId: String?): MetaContact?

    /**
     * Returns the index of metaContact in relation to other contacts in this or
     * -1 if metaContact does not belong to this group. The returned index is
     * only valid until another contact has been added / removed or a contact
     * has changed its status and hence - position. In such a case a REORDERED event is fired.
     *
     * @param metaContact the `MetaContact` whose index we're looking for.
     * @return the index of `metaContact` in the list of child contacts or -1 if `metaContact`.
     */
    fun indexOf(metaContact: MetaContact?): Int

    /**
     * Returns the index of metaContactGroup in relation to other subgroups in
     * this group or -1 if metaContact does not belong to this group. The
     * returned index is only valid until another group has been added /
     * removed or renamed In such a case a REORDERED event is fired.
     *
     * @param metaContactGroup the `MetaContactGroup` whose index we're looking for.
     * @return the index of `metaContactGroup` in the list of child
     * contacts or -1 if `metaContact`.
     */
    fun indexOf(metaContactGroup: MetaContactGroup?): Int

    /**
     * Returns the meta contact on the specified index.
     *
     * @param index the index of the meta contact to return.
     * @return the MetaContact with the specified index,
     * @throws java.lang.IndexOutOfBoundsException in case `index` is
     * not a valid index for this group.
     */
    @Throws(IndexOutOfBoundsException::class)
    fun getMetaContact(index: Int): MetaContact?

    /**
     * Returns the name of this group.
     *
     * @return a String containing the name of this group.
     */
    fun getGroupName(): String

    /**
     * Returns the `MetaContactGroup` with the specified name.
     *
     * @param groupName the name of the group to return.
     * @return the `MetaContactGroup` with the specified name or null
     * if no such group exists.
     */
    fun getMetaContactSubgroup(groupName: String?): MetaContactGroup?

    /**
     * Returns the `MetaContactGroup` with the specified index.
     *
     * @param index the index of the group to return.
     * @return the `MetaContactGroup` with the specified index.
     * @throws java.lang.IndexOutOfBoundsException if `index` is not a valid index.
     */
    @Throws(IndexOutOfBoundsException::class)
    fun getMetaContactSubgroup(index: Int): MetaContactGroup?

    /**
     * Returns the MetaContactGroup currently containing this group or null if
     * this is the root group
     *
     * @return a reference to the MetaContactGroup currently containing this
     * meta contact group or null if this is the root group.
     */
    fun getParentMetaContactGroup(): MetaContactGroup?

    /**
     * Returns a String representation of this group and the contacts it
     * contains (may turn out to be a relatively long string).
     *
     * @return a String representing this group and its child contacts.
     */
    override fun toString(): String

    /**
     * Returns a String identifier (the actual contents is left to
     * implementations) that uniquely represents this `MetaContact` in
     * the containing `MetaContactList`
     *
     * @return a String uniquely identifying this meta contact.
     */
    fun getMetaUID(): String?

    /**
     * Gets the user data associated with this instance and a specific key.
     *
     * @param key the key of the user data associated with this instance to be retrieved
     * @return an `Object` which represents the value associated with
     * this instance and the specified `key`; `null`
     * if no association with the specified `key` exists in this instance
     */
    fun getData(key: Any?): Any?

    /**
     * Sets a user-specific association in this instance in the form of a
     * key-value pair. If the specified `key` is already associated
     * in this instance with a value, the existing value is overwritten with the
     * specified `value`.
     *
     * The user-defined association created by this method and stored in this
     * instance is not serialized by this instance and is thus only meant for
     * runtime use.
     *
     * The storage of the user data is implementation-specific and is thus not
     * guaranteed to be optimized for execution time and memory use.
     *
     * @param key the key to associate in this instance with the specified value
     * @param value the value to be associated in this instance with the specified `key`
     */
    fun setData(key: Any?, value: Any?)

    /**
     * Determines whether or not this meta group contains only groups that are
     * being stored by a server.
     *
     * @return true if the meta group is persistent and false otherwise.
     */
    fun isPersistent(): Boolean

    companion object {
        const val TABLE_NAME = "metaContactGroup"
        const val ID = "id"
        const val ACCOUNT_UUID = "accountUuid"
        const val MC_GROUP_NAME = "mcGroupName"
        const val MC_GROUP_UID = "mcGroupUID"
        const val PARENT_PROTO_GROUP_UID = "parentProtoGroupUID"
        const val PROTO_GROUP_UID = "protoGroupUID"
        const val PERSISTENT_DATA = "persistentData"
        const val TBL_CHILD_CONTACTS = "childContacts"
        const val MC_UID = "mcUID"

        // String ACCOUNT_UUID = "accountUuid";
        // String PROTO_GROUP_UID = "protoGroupUID";
        const val CONTACT_JID = "contactJid"
        const val MC_DISPLAY_NAME = "mcDisplayName"
        const val MC_USER_DEFINED = "mcDNUserDefined"

        // String PERSISTENT_DATA = "persistentData";
        const val MC_DETAILS = "details"
    }
}