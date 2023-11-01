/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.ContactResourceListener
import org.jxmpp.jid.Jid

/**
 * This class represents the notion of a Contact or Buddy, that is widely used in instant messaging
 * today. From a protocol point of view, a contact is generally considered to be another user of the
 * service that proposes the protocol. Instances of Contact could be used for delivery of presence
 * notifications or when addressing instant messages.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface Contact {
    //	String CONTACT_ADDRESS = "contactAddress";
    //	String IS_PERSISTENT = "is_persistent";
    //	String IS_RESOLVED = "is_resolved";
    //	String SYSTEM_ACCOUNT = "systemAccount";
    //	String GROUPS = "groups";
    /**
     * Returns a String that can be used for identifying the contact. The exact contents of the
     * string depends on the protocol. In the case of SIP, for example, that would be the SIP uri
     * (e.g. sip:alice@biloxi.com) in the case of icq - a UIN (12345653) and for AIM a screen name
     * (myname). Jabber (and hence Google) would be having e-mail like addresses.
     *
     * @return a String id representing and uniquely identifying the contact.
     */
    val address: String

    /**
     * Either return the bareJid of contact as retrieved from the Roster Entry
     * Or The VolatileContact full Jid
     *
     * @return Either return the bareJid of contact as retrieved from the Roster Entry
     * Or The VolatileContact full Jid
     */
    var contactJid: Jid?

    /**
     * Returns a String that could be used by any user interacting modules for referring to this
     * contact. An alias is not necessarily unique but is often more human readable than an address (or id).
     *
     * @return a String that can be used for referring to this contact when interacting with the user.
     */
    val displayName: String

    /**
     * Returns a byte array containing an image (most often a photo or an avatar) that the contact
     * uses as a representation.
     *
     * @return byte[] an image representing the contact.
     */
    var image: ByteArray?

    /**
     * Returns the status of the contact as per the last status update we've received for it. Note
     * that this method is not to perform any network operations and will simply return the status
     * received in the last status update message. If you want a reliable way of retrieving
     * someone's status, you should use the `queryContactStatus()` method in `OperationSetPresence`.
     *
     * @return the PresenceStatus that we've received in the last status update pertaining to this contact.
     */
    var presenceStatus: PresenceStatus

    /**
     * Get the contact last activityTime
     * @return contact last activityTime
     */
    var lastActiveTime: Long

    /**
     * Returns a reference to the contact group that this contact is currently a child of or null if
     * the underlying protocol does not support persistent presence.
     *
     * @return a reference to the contact group that this contact is currently a child of or null if
     * the underlying protocol does not support persistent presence.
     */
    val parentContactGroup: ContactGroup?

    /**
     * Returns a reference to the protocol provider that created the contact.
     *
     * @return a reference to an instance of the ProtocolProviderService
     */
    val protocolProvider: ProtocolProviderService

    /**
     * Determines whether or not this contact is being stored by the server. Non persistent contacts
     * are common in the case of simple, non-persistent presence operation sets. They could however
     * also be seen in persistent presence operation sets when for example we have received an event
     * from someone not on our contact list. Non persistent contacts are volatile even when coming
     * from a persistent presence op. set. They would only exist until the application is closed and
     * will not be there next time it is loaded.
     *
     * @return true if the contact is persistent and false otherwise.
     */
    var isPersistent: Boolean

    /**
     * Determines whether or not this contact has been resolved against the server. Unresolved
     * contacts are used when initially loading a contact list that has been stored in a local file
     * until the presence operation set has managed to retrieve all the contact list from the server
     * and has properly mapped contacts to their on-line buddies.
     *
     * @return true if the contact has been resolved (mapped against a buddy) and false otherwise.
     */
    fun isResolved(): Boolean

    /**
     * Returns a String that can be used to create a unresolved instance of this contact. Unresolved
     * contacts are created through the createUnresolvedContact() method in the persistent presence
     * operation set. The method may also return null if no such data is required and the contact
     * address is sufficient for restoring the contact.
     *
     * @return A `String` that could be used to create a unresolved instance of this contact
     * during a next run of the application, before establishing network connectivity or
     * null if no such data is required.
     */
    val persistentData: String?

    /**
     * When access on start-up, return ttsEnable may be null.
     * Change Contact tts enable value in configuration service.
     * Null value in DB is considered as false
     *
     */
    var isTtsEnable: Boolean?

    /**
     * Return the current status message of this contact.
     *
     * @return the current status message
     */
    var statusMessage: String?

    /**
     * Indicates if this contact supports resources.
     *
     * @return `true` if this contact supports resources, `false` otherwise
     */
    val isSupportResources: Boolean

    /**
     * Returns a collection of resources supported by this contact or null if it doesn't support resources.
     *
     * @return a collection of resources supported by this contact or null if it doesn't support resources
     */
    fun getResources(): Collection<ContactResource?>?

    /**
     * Adds the given `ContactResourceListener` to listen for events related to contact resources changes.
     *
     * @param l the `ContactResourceListener` to add
     */
    fun addResourceListener(l: ContactResourceListener)

    /**
     * Removes the given `ContactResourceListener` listening for events related to contact resources changes.
     *
     * @param l the `ContactResourceListener` to remove
     */
    fun removeResourceListener(l: ContactResourceListener)

    /**
     * Returns the persistent contact address.
     *
     * @return the address of the contact.
     */
    fun getPersistableAddress(): String?

    /**
     * Whether contact is mobile one. Logged in only from mobile device.
     *
     * @return whether contact is mobile one.
     */
    var isMobile: Boolean

    companion object {
        const val TABLE_NAME = "contacts"
        const val CONTACT_UUID = "contactUuid"
        const val PROTOCOL_PROVIDER = "protocolProvider"
        const val CONTACT_JID = "contactJid"
        const val SVR_DISPLAY_NAME = "svrDisplayName"
        const val OPTIONS = "options" // subscriptions
        const val PHOTO_URI = "photoUri"
        const val AVATAR_HASH = "avatarHash"
        const val LAST_PRESENCE = "lastPresence" // last use resource
        const val PRESENCE_STATUS = "presenceStatus" // 0 ~ 100
        const val LAST_SEEN = "lastSeen"
        const val KEYS = "keys" // all secured keys
    }
}