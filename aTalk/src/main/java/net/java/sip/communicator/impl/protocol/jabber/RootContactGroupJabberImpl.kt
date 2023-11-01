/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ContactGroup
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException
import java.util.*

/**
 * A dummy ContactGroup implementation representing the ContactList root for Jabber contact lists.
 *
 * @author Damian Minkov
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class RootContactGroupJabberImpl// Do not add itself to subGroups => problem. Hanlder in code
// subGroups.add(this);
/**
 * Creates a ContactGroup instance; and include itself into the subGroups list.
 */ internal constructor(
        /**
         * The provider.
         */
        private val protocolProvider: ProtocolProviderServiceJabberImpl) : AbstractContactGroupJabberImpl() {
    /**
     * Maps all Jid in our roster to the actual contacts so that we could easily search the set of
     * existing contacts. Note that we only store lower case strings in the left column because JIDs
     * in XMPP are not case-sensitive.
     */
    private val contacts = Hashtable<Jid, Contact>()

    /**
     * Root group is always assumed resolved to avoid accidentally removal.
     */
    private val isResolved = true

    /**
     * A list of all the groups in the root tree.
     * subGroups also include itself.
     */
    private val subGroups = LinkedList<ContactGroup?>()

    /**
     * Returns the number, which is always 0, of `Contact` members of this `ContactGroup`
     *
     * @return an int indicating the number of `Contact`s, members of this `ContactGroup`.
     */
    override fun countContacts(): Int {
        return contacts.size
    }

    /**
     * Returns null as this is the root contact group.
     *
     * @return null as this is the root contact group.
     */
    override fun getParentContactGroup(): ContactGroup? {
        return null
    }

    /**
     * Adds the specified contact to the end of this group.
     *
     * @param contact the new contact to add to this group
     */
    override fun addContact(contact: ContactJabberImpl) {
        contacts[contact.contactJid!!] = contact
    }

    /**
     * Removes the specified contact from this contact group
     *
     * @param contact the contact to remove.
     */
    fun removeContact(contact: ContactJabberImpl?) {
        contacts.remove(contact!!.contactJid)
    }

    /**
     * Returns an Iterator over all contacts, member of this `ContactGroup`.
     *
     * @return a java.util.Iterator over all contacts inside this `ContactGroup`
     */
    override fun contacts(): Iterator<Contact?> {
        return contacts.values.iterator()
    }

    /**
     * Returns the `Contact` with the specified address or identifier.
     *
     * @param id the address or identifier of the `Contact` we are looking for.
     * @return the `Contact` with the specified id or address.
     */
    override fun getContact(id: String?): Contact? {
        return try {
            findContact(JidCreate.from(id))
        } catch (e: XmppStringprepException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Returns the contact encapsulating with the specified name or null if no such contact was found.
     *
     * @param id the id for the contact we're looking for.
     * @return the `ContactJabberImpl` corresponding to the specified jid or null if no such contact existed.
     */
    fun findContact(id: Jid?): ContactJabberImpl? {
        return if (id == null) null else contacts[id.asBareJid()] as ContactJabberImpl?
    }

    /**
     * Returns the name of this group which is always `ROOT_PROTO_GROUP_UID`.
     *
     * @return a String containing the name of this group.
     */
    override fun getGroupName(): String {
        return ContactGroup.ROOT_PROTO_GROUP_UID
    }

    /**
     * The ContactListRoot is the only group that can contain subgroups.
     *
     * @return true (always)
     */
    override fun canContainSubgroups(): Boolean {
        return true
    }

    /**
     * Returns the subgroup with the specified index.
     *
     * @param index the index of the `ContactGroup` to retrieve.
     * @return the `ContactGroup` with the specified index.
     */
    override fun getGroup(index: Int): ContactGroup? {
        return subGroups[index]
    }

    /**
     * Returns the subgroup with the specified name.
     *
     * @param groupName the name of the `ContactGroup` to retrieve.
     * @return the `ContactGroup` with the specified index.
     */
    override fun getGroup(groupName: String?): ContactGroup? {
        val subgroups = subgroups()
        while (subgroups.hasNext()) {
            val grp = subgroups.next()
            if (grp!!.getGroupName() == groupName) return grp
        }
        return null
    }

    /**
     * Returns an iterator over the sub groups that this `ContactGroup` contains.
     *
     * @return a java.util.Iterator over the `ContactGroup` children of this group (i.e. subgroups).
     */
    override fun subgroups(): Iterator<ContactGroup?> {
        return ArrayList(subGroups).iterator()
    }

    /**
     * Returns the number of subgroups contained by this `RootContactGroupImpl`.
     *
     * @return an int indicating the number of subgroups that this ContactGroup contains.
     */
    override fun countSubgroups(): Int {
        return subGroups.size
    }

    /**
     * Adds the specified group to the end of the list of sub groups.
     * Include the rootGroup, so change to pass-in para to ContactGroup
     *
     * @param group the group to add.
     */
    fun addSubGroup(group: ContactGroup?) {
        subGroups.add(group)
    }

    /**
     * Removes the sub group with the specified index.
     *
     * @param index the index of the group to remove
     */
    private fun removeSubGroup(index: Int) {
        subGroups.removeAt(index)
    }

    /**
     * Removes the specified from the list of sub groups
     *
     * @param group the group to remove.
     */
    fun removeSubGroup(group: ContactGroupJabberImpl?) {
        removeSubGroup(subGroups.indexOf(group))
    }

    /**
     * Returns the protocol provider that this group belongs to.
     *
     * @return a reference to the ProtocolProviderService instance that this ContactGroup belongs to.
     */
    override fun getProtocolProvider(): ProtocolProviderService {
        return protocolProvider
    }

    /**
     * Returns a string representation of the root contact group that contains all subGroups and subContacts of this group.
     * Ensure group.toString() does not end in endless loop under all circumstances
     *
     * @return a string representation of this root contact group.
     */
    override fun toString(): String {
        val buff = StringBuilder(getGroupName())
        buff.append(".subGroups=").append(countSubgroups()).append(":\n")
        val subGroups = subgroups()
        while (subGroups.hasNext()) {
            val group = subGroups.next()
            buff.append(group.toString())
            if (subGroups.hasNext()) buff.append("\n")
        }
        buff.append(".rootContacts=").append(countContacts()).append(":\n")
        val contactsIter = contacts()
        while (contactsIter.hasNext()) {
            buff.append(contactsIter.next())
            if (contactsIter.hasNext()) buff.append("\n")
        }
        return buff.toString()
    }

    /**
     * Determines whether or not this contact group is being stored by the server. Non persistent
     * contact groups exist for the sole purpose of containing non persistent contacts.
     *
     * @return true if the contact group is persistent and false otherwise.
     */
    override fun isPersistent(): Boolean {
        return true
    }

    /**
     * Returns null as no persistent data is required and the group name is sufficient for restoring the contact.
     *
     * @return null as no such data is needed.
     */
    override fun getPersistentData(): String? {
        return null
    }

    /**
     * Determines whether or not this group has been resolved against the server. Unresolved groups
     * are used when initially loading a contact list that has been stored in a local file until
     * the presence operation set has managed to retrieve all the contact list from the server
     * and has properly mapped groups to their on-line buddies.
     *
     * The root group must always be resolved to avoid any un-intention removal.
     *
     * @return true if the group has been resolved (mapped against a buddy) and false otherwise.
     */
    override fun isResolved(): Boolean {
        return isResolved
    }

    /**
     * Returns a `String` that uniquely represents the group. In this we use the name of the
     * group as an identifier. This may cause problems though, in case the name is changed by some
     * other application between consecutive runs of the sip-communicator.
     *
     * @return a String representing this group in a unique and persistent way.
     */
    override fun getUID(): String {
        return getGroupName()
    }
}