/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import org.jxmpp.jid.Jid

/**
 * The Jabber implementation for Volatile Contact
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class VolatileContactJabberImpl @JvmOverloads internal constructor(id: Jid, ssclCallback: ServerStoredContactListJabberImpl?,
        isPrivateMessagingContact: Boolean = false, displayName: String? = null) : ContactJabberImpl(null, ssclCallback!!, false, false) {
    /**
     * Checks if the contact is private messaging contact or not.
     *
     * @return `true` if this is private messaging contact and `false` if it isn't.
     */
    /**
     * Indicates whether the contact is private messaging contact or not.
     */
    var isPrivateMessagingContact = false

    /**
     * This contact id
     */
    override var contactJid: Jid? = null

    /**
     * Returns a String that could be used by any user interacting modules for referring to this
     * contact. An alias is not necessarily unique but is often more human readable than an address (or id).
     *
     * @return a String that can be used for referring to this contact when interacting with the user.
     */
    /**
     * The display name of the contact. This property is used only for private messaging contacts.
     */
    override val displayName: String

    /**
     * Creates an Volatile JabberContactImpl with the specified id
     *
     * @param id String the user id/address (bareJid from subscription)
     * <presence to='swan@atalk.org/atalk' from='leopard@icrypto.com' type='subscribe'></presence>
     * @param ssclCallback a reference to the ServerStoredContactListImpl instance that created us.
     * @param isPrivateMessagingContact if `true` this should be private messaging contact.
     * @param displayName the display name of the contact
     */
    /**
     * Creates an Volatile JabberContactImpl with the specified id
     *
     * @param id String the user id/address
     * @param ssclCallback a reference to the ServerStoredContactListImpl instance that created us.
     */
    /**
     * Creates an Volatile JabberContactImpl with the specified id
     *
     * id String the user id/address
     * ssclCallback a reference to the ServerStoredContactListImpl instance that created us.
     * isPrivateMessagingContact if `true` this should be private messaging contact.
     */
    init {
        this.isPrivateMessagingContact = isPrivateMessagingContact
        if (this.isPrivateMessagingContact) {
            this.displayName = id.resourceOrEmpty.toString() + " from " + id.asBareJid()
            contactJid = id
        } else {
            contactJid = id.asBareJid()
            this.displayName = displayName ?: contactJid.toString()
            val resource = id.resourceOrNull
            if (resource != null) {
                contactJid = id
            }
        }
    }

    /**
     * Returns the Jabber UserId of this contact
     *
     * @return the Jabber UserId of this contact
     */
    override val address: String
        get() {
            return contactJid.toString()
        }

    /**
     * Returns a string representation of this contact, containing most of its representative details.
     *
     * @return a string representation of this contact.
     */
    override fun toString(): String {
        return "VolatileJabberContact[id = $address]"
    }

    /**
     * Determines whether or not this contact is to be stored at local DB. Non persistent
     * contact exist for the sole purpose of displaying any received messages.
     *
     * @return true if the contact group is persistent and false otherwise.
     */
    override var isPersistent = true

    /**
     * Returns the real address of the contact. If the contact is not private messaging contact the
     * result will be the same as `getAddress`'s result.
     *
     * @return the real address of the contact.
     */
    override fun getPersistableAddress(): String? {
        if (!isPrivateMessagingContact) return address
        var chatRoomMember: ChatRoomMemberJabberImpl? = null
        val mucOpSet = protocolProvider.getOperationSet(OperationSetMultiUserChat::class.java) as OperationSetMultiUserChatJabberImpl?
        if (mucOpSet != null) {
            chatRoomMember = mucOpSet.getChatRoom(contactJid!!.asBareJid())!!.findMemberForNickName(contactJid!!.resourceOrEmpty)
        }
        return if (chatRoomMember == null) null else chatRoomMember.getJabberId()!!.asBareJid().toString()
    }
}