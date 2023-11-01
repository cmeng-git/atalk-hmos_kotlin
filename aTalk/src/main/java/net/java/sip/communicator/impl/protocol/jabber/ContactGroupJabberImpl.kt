/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ContactGroup
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.jivesoftware.smack.roster.RosterEntry
import org.jivesoftware.smack.roster.RosterGroup
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException
import java.util.*

/**
 * The Jabber implementation of the ContactGroup interface. Instances of this class (contrary to
 * `RootContactGroupJabberImpl`) may only contain contacts and cannot have subgroups.
 * Note that instances of this class only use the corresponding smack source group for reading their
 * names and only initially fill their `contacts` `java.util.List` with the
 * ContactJabberImpl objects corresponding to those contained in the source group at the moment it
 * is being created. They would, however, never try to sync or update their contents ulteriorly.
 * This would have to be done through the addContact()/removeContact() methods. The content of
 * contacts is created on creating of the group and when the smack source group is changed.
 *
 * @author Damian Minkov
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
open class ContactGroupJabberImpl : AbstractContactGroupJabberImpl {
    /**
     * Maps all Jid in our roster to the actual contacts so that we could easily search the set of
     * existing contacts. Note that we only store lower case strings in the left column because JIDs
     * in XMPP are not case-sensitive.
     */
    private val buddies = Hashtable<BareJid, Contact>()

    /**
     * Whether or not this contact group has been resolved against the server.
     */
    private var isResolved: Boolean

    /**
     * The Jabber Group id(the name), corresponding to this contact group. Used to resolve the
     * RosterGroup against the roster.
     */
    private var id: String? = null

    /**
     * a list that would always remain empty. We only use it so that we're able to extract empty iterators
     */
    private val dummyGroupsList = LinkedList<ContactGroup>()

    /**
     * A variable that we use as a means of detecting changes in the name of this group.
     */
    private var nameCopy: String? = null

    /**
     * Used when creating unresolved groups, temporally id.
     */
    private var tempId: String? = null

    /**
     * The contact list handler that creates us.
     */
    private val ssclCallback: ServerStoredContactListJabberImpl

    /**
     * Creates an Jabber group using the specified `RosterGroup` as a source. The newly
     * created group will always return the name of the underlying RosterGroup and would thus
     * automatically adapt to changes. It would, however, not receive or try to poll for
     * modifications of the contacts it contains and would therefore have to be updated manually by
     * ServerStoredContactListImpl update will only be done if source group is changed.
     *
     * @param rosterGroup the Jabber Group corresponding to the group
     * @param groupMembers the group members that we should add to the group.
     * @param ssclCallback a callback to the server stored contact list we're creating.
     * @param isResolved a boolean indicating whether or not the group has been resolved against the server.
     */
    internal constructor(rosterGroup: RosterGroup?, groupMembers: Iterator<RosterEntry>,
            ssclCallback: ServerStoredContactListJabberImpl, isResolved: Boolean) {
        // rosterGroup can be null when creating volatile contact group
        if (rosterGroup != null) id = rosterGroup.name
        this.isResolved = isResolved
        this.ssclCallback = ssclCallback

        // init the name copy if its not volatile
        if (rosterGroup != null) nameCopy = rosterGroup.name
        while (groupMembers.hasNext()) {
            val rEntry = groupMembers.next()
            if (!ServerStoredContactListJabberImpl.isEntryDisplayable(rEntry)) continue

            // only add the buddy if it doesn't already exist in some other group
            // this is necessary because XMPP would allow having one and the
            // same buddy in more than one group.
            if (ssclCallback.findContactById(rEntry.jid) != null) {
                continue
            }
            addContact(ContactJabberImpl(rEntry, ssclCallback, true, true))
        }
    }

    /**
     * Used when creating unresolved groups.
     *
     * @param id the id of the group.
     * @param ssclCallback the contact list handler that created us.
     */
    internal constructor(id: String?, ssclCallback: ServerStoredContactListJabberImpl) {
        tempId = id
        isResolved = false
        this.ssclCallback = ssclCallback
    }

    /**
     * Returns the number of `Contact` members of this `ContactGroup`
     *
     * @return an int indicating the number of `Contact`s, members of this
     * `ContactGroup`.
     */
    override fun countContacts(): Int {
        return buddies.size
    }

    /**
     * Returns a reference to the root group which in Jabber is the parent of any other group since
     * the protocol does not support subgroups.
     *
     * @return a reference to the root group.
     */
    override fun getParentContactGroup(): ContactGroup? {
        return ssclCallback.rootGroup
    }

    /**
     * Adds the specified contact to the end of this group.
     *
     * @param contact the new contact to add to this group
     */
    final override fun addContact(contact: ContactJabberImpl) {
        buddies[contact.contactJid!!.asBareJid()] = contact
    }

    /**
     * Removes the specified contact from this contact group
     *
     * @param contact the contact to remove.
     */
    fun removeContact(contact: ContactJabberImpl) {
        buddies.remove(contact.contactJid!!.asBareJid())
    }

    /**
     * Returns an Iterator over all contacts, member of this `ContactGroup`.
     *
     * @return a java.util.Iterator over all contacts inside this `ContactGroup`. In case the
     * group doesn't contain any members it will return an empty iterator.
     */
    override fun contacts(): Iterator<Contact?>? {
        return buddies.values.iterator()
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
     * The search is always based on BareJid
     *
     * @param jid the id for the contact we're looking for.
     * @return the `ContactJabberImpl` corresponding to the specified screenName or null
     * if no such contact existed.
     */
    fun findContact(jid: Jid?): ContactJabberImpl? {
        return if (jid == null) null else buddies[jid.asBareJid()] as ContactJabberImpl?
    }

    /**
     * Returns the name of this group.
     *
     * @return a String containing the name of this group.
     */
    override fun getGroupName(): String? {
        return if (isResolved) // now we use the id field to store the rosterGroup name for later retrieval from
        // roster. return rosterGroup.getName();
            id else tempId
    }

    /**
     * Determines whether the group may contain subgroups or not.
     *
     * @return always false since only the root group may contain subgroups.
     */
    override fun canContainSubgroups(): Boolean {
        return false
    }

    /**
     * Returns the subgroup with the specified index (i.e. always null since this group may not contain subgroups).
     *
     * @param index the index of the `ContactGroup` to retrieve.
     * @return always null
     */
    override fun getGroup(index: Int): ContactGroup? {
        return null
    }

    /**
     * Returns the subgroup with the specified name.
     *
     * @param groupName the name of the `ContactGroup` to retrieve.
     * @return the `ContactGroup` with the specified index.
     */
    override fun getGroup(groupName: String?): ContactGroup? {
        return null
    }

    /**
     * Returns an empty iterator. Subgroups may only be present in the root group.
     *
     * @return an empty iterator
     */
    override fun subgroups(): Iterator<ContactGroup?>? {
        return dummyGroupsList.iterator()
    }

    /**
     * Returns the number of subgroups contained by this group, which is always 0 since sub groups
     * in the protocol may only be contained by the root group - `RootContactGroupImpl`.
     *
     * @return a 0 int.
     */
    override fun countSubgroups(): Int {
        return 0
    }

    /**
     * Returns a hash code value for the object, which is actually the hashcode value of the groupName.
     *
     * @return a hash code value for this ContactGroup.
     */
    override fun hashCode(): Int {
        return getGroupName().hashCode()
    }

    /**
     * Indicates whether some other object is "equal to" this group.
     *
     * @param other the reference object with which to compare.
     * @return `true` if this object is the same as the obj argument; `false` otherwise.
     */
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is ContactGroupJabberImpl) return false
        return if ((other as ContactGroup).getGroupName() != getGroupName()) false else getProtocolProvider() === (other as ContactGroup).getProtocolProvider()

        //since Jabber does not support having two groups with the same name
        // at this point we could bravely state that the groups are the same
        // and not bother to compare buddies. (gotta check that though)
    }

    /**
     * Returns the protocol provider that this group belongs to.
     *
     * @return a reference to the ProtocolProviderService instance that this ContactGroup belongs to.
     */
    override fun getProtocolProvider(): ProtocolProviderService? {
        return ssclCallback.parentProvider
    }

    /**
     * Returns a string representation of this group, in the form JabberGroup.GroupName[size]{
     * buddy1.toString(), buddy2.toString(), ...}.
     *
     * @return a String representation of the object.
     */
    override fun toString(): String {
        val buff = StringBuilder("JabberGroup.")
        buff.append(getGroupName()).append(", childContacts=").append(countContacts()).append(":[")
        val contacts = contacts()
        while (contacts!!.hasNext()) {
            val contact = contacts.next()
            buff.append(contact.toString())
            if (contacts.hasNext()) buff.append(", ")
        }
        return buff.append("]").toString()
    }

    /**
     * Sets the name copy field that we use as a means of detecting changes in the group name.
     *
     * @param newName String
     */
    fun setNameCopy(newName: String?) {
        nameCopy = newName
    }

    /**
     * Returns the name of the group as it was at the last call of initNameCopy.
     *
     * @return a String containing a copy of the name of this group as it was last time when we
     * called `initNameCopy`.
     */
    fun getNameCopy(): String? {
        return nameCopy
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
     * Returns null as no persistent data is required and the contact address is sufficient for restoring the contact.
     *
     * @return null as no such data is needed.
     */
    override fun getPersistentData(): String? {
        return null
    }

    /**
     * Determines whether or not this contact group has been resolved against the server. Unresolved
     * group are used when initially loading a contact list that has been stored in a local file
     * until the presence operation set has managed to retrieve all the contact list from the server
     * and has properly mapped contacts and groups to their corresponding on-line contacts.
     *
     * @return true if the contact has been resolved (mapped against a buddy) and false otherwise.
     */
    override fun isResolved(): Boolean {
        return isResolved
    }

    /**
     * Resolve this contact group against the specified group
     *
     * @param source the server stored group
     */
    fun setResolved(source: RosterGroup) {
        if (isResolved) return
        isResolved = true
        id = source.name
        for (item in source.entries) {
            val contact = ssclCallback.findContactById(item.jid)

            // some services automatically adds contacts from an addressBook to our roster and
            // this contacts are with subscription none. if such already exist, remove it. This
            // is typically our own contact
            if (!ServerStoredContactListJabberImpl.isEntryDisplayable(item)) {
                if (contact != null) {
                    removeContact(contact)
                    ssclCallback.fireContactRemoved(this, contact)
                }
                continue
            }
            if (contact != null) {
                contact.setResolved(item)
                ssclCallback.fireContactResolved(this, contact)
            } else {
                val newContact = ContactJabberImpl(item, ssclCallback, true, true)
                addContact(newContact)
                ssclCallback.fireContactAdded(this, newContact)
            }
        }
    }

    /**
     * Returns a `String` that uniquely represents the group. In this we use the name of the
     * group as an identifier. This may cause problems though, in case the name is changed by some
     * other application between consecutive runs of the sip-communicator.
     *
     * @return a String representing this group in a unique and persistent way.
     */
    override fun getUID(): String? {
        return getGroupName()
    }

    /**
     * The source group we are encapsulating
     *
     * @return RosterGroup
     */
    fun getSourceGroup(): RosterGroup {
        return ssclCallback.getRosterGroup(id)
    }

    /**
     * Change the source group, used when renaming groups.
     *
     * @param newGroup RosterGroup
     */
    fun setSourceGroup(newGroup: RosterGroup) {
        id = newGroup.name
    }
}