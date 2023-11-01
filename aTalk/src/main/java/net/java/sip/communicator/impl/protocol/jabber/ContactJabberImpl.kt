/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.AbstractContact
import net.java.sip.communicator.service.protocol.ContactGroup
import net.java.sip.communicator.service.protocol.ContactResource
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.ContactResourceEvent
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum
import net.java.sip.communicator.util.ConfigurationUtils.getContactProperty
import net.java.sip.communicator.util.ConfigurationUtils.updateContactProperty
import org.apache.commons.lang3.StringUtils
import org.jivesoftware.smack.roster.RosterEntry
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.jxmpp.jid.FullJid
import org.jxmpp.jid.Jid
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * The Jabber implementation of the service.protocol.Contact interface.
 *
 * @author Damian Minkov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
open class ContactJabberImpl : AbstractContact {
    /**
     * A reference to the ServerStoredContactListImpl instance that created us.
     */
    private val ssclCallback: ServerStoredContactListJabberImpl

    /**
     * Whether or not this contact has been resolved against the server.
     */
    private var isResolved: Boolean

    /**
     * Used to store contact id when creating unresolved contacts.
     */
    private val tempId: Jid?

    /**
     * The contact resources list.
     */
    private var resources: MutableMap<FullJid, ContactResourceJabberImpl?>? = null

    /**
     * Indicates whether or not this Contact instance represents the user used by this protocol
     * provider to connect to the service.
     */
    private var isLocal = false
    protected var keys = JSONObject()
    protected var groups = JSONArray()
    protected var subscription = 0
    private var photoUri: String? = null

    /**
     * Creates an JabberContactImpl
     *
     * rosterEntry the RosterEntry object that we will be encapsulating.
     * ssclCallback a reference to the ServerStoredContactListImpl instance that created us.
     * isPersistent determines whether this contact is persistent or not.
     * isResolved specifies whether the contact has been resolved against the server contact list
     */
    internal constructor(rosterEntry: RosterEntry?, ssclCallback: ServerStoredContactListJabberImpl,
            isPersistent: Boolean, isResolved: Boolean) {
        // rosterEntry can be null when creating volatile contact
        if (rosterEntry != null) {
            // RosterEntry contains only BareJid
            contactJid = rosterEntry.jid
            this.serverDisplayName = rosterEntry.name
        }
        tempId = null
        this.ssclCallback = ssclCallback
        this.isPersistent = isPersistent
        this.isResolved = isResolved
        presenceStatus = (protocolProvider as ProtocolProviderServiceJabberImpl).jabberStatusEnum!!.getStatus(JabberStatusEnum.OFFLINE)
    }

    /**
     * Used to create unresolved contacts with specified id.
     *
     * id contact id
     * ssclCallback the contact list handler that creates us.
     * isPersistent is the contact persistent.
     */
    internal constructor(id: Jid, ssclCallback: ServerStoredContactListJabberImpl, isPersistent: Boolean) {
        tempId = id
        this.ssclCallback = ssclCallback
        this.isPersistent = isPersistent
        isResolved = false
        presenceStatus = (protocolProvider as ProtocolProviderServiceJabberImpl).jabberStatusEnum!!.getStatus(JabberStatusEnum.OFFLINE)
    }

    /**
     * Returns the Jabber UserId of this contact
     *
     * @return the Jabber UserId of this contact
     */
    override val address: String
        get() {
            return if (isResolved && contactJid != null) contactJid.toString() else tempId.toString()
        }

    /**
     * Returns an avatar if one is already present or `null` in case it is not in which case
     * it the method also queues the contact for image updates.
     *
     * @return the avatar of this contact or `null` if no avatar is currently available.
     */
    /**
     * The image of the contact.
     */
    override var image: ByteArray? = null
        get() {
            if (field == null) ssclCallback.addContactForImageUpdate(this)
            return field
        }

    /**
     * Returns a reference to the image assigned to this contact. If (image == null) and the
     * retrieveIfNecessary flag is true, we schedule the image for retrieval from the server.
     *
     * (image.length == 0) indicates it has been retrieved before, so to avoid avatar retrieval in endless loop
     *
     * retrieveIfNecessary specifies whether the method should queue this contact for avatar update from the server.
     * @return a reference to the image currently stored by this contact.
     * @see ServerStoredContactListJabberImpl.ImageRetriever.run
     */
    fun getImage(retrieveIfNecessary: Boolean): ByteArray? {
        if (image == null && retrieveIfNecessary) ssclCallback.addContactForImageUpdate(this)
        return image
    }

    /**
     * Retrieve avatar from server and update the contact avatar image
     * For user manual download by long click on the avatar
     *
     * retrieveOnStart force to download from server if avatar is null
     */
    fun getAvatar(retrieveOnStart: Boolean) {
        ssclCallback.setRetrieveOnStart(retrieveOnStart)
        ssclCallback.addContactForImageUpdate(this)
    }

    /**
     * Returns a hashCode for this contact. The returned hashcode is actually that of the Contact's Address
     *
     * @return the hashcode of this Contact
     */
    override fun hashCode(): Int {
        return address.lowercase().hashCode()
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     *
     * obj the reference object with which to compare.
     * @return `true` if this object is the same as the obj argument; `false` otherwise.
     */
    override fun equals(other: Any?): Boolean {
        if (!(other is String || other is ContactJabberImpl)) return false
        if ((other is ContactJabberImpl
                        && other.address.equals(address, ignoreCase = true)) && other.protocolProvider == protocolProvider) {
            return true
        }
        if (other is String) {
            val atIndex = address.indexOf("@")
            return if (atIndex > 0) {
                (address.equals(other as String?, ignoreCase = true)
                        || address.substring(0, atIndex).equals(other as String?, ignoreCase = true))
            } else address.equals(other as String?, ignoreCase = true)
        }
        return false
    }

    /**
     * Returns a string representation of this contact, containing most of its representative details.
     *
     * @return a string representation of this contact.
     */
    override fun toString(): String {
        val buff = StringBuilder()
                .append("JabberContact[id=").append(address)
                .append(", isPersistent=").append(isPersistent)
                .append(", isResolved=").append(isResolved).append("]")
        return buff.toString()
    }

    /**
     * Sets the status that this contact is currently in. The method is to only be called as a
     * result of a status update received from the server.
     *
     * status the JabberStatusEnum that this contact is currently in.
     */
    /**
     * Returns the status of the contact as per the last status update we've received for it. Note
     * that this method is not to perform any network operations and will simply return the status
     * received in the last status update message. If you want a reliable way of retrieving
     * someone's status, you should use the `queryContactStatus()` method in `OperationSetPresence`.
     *
     * @return the PresenceStatus that we've received in the last status update pertaining to this contact.
     */
    /**
     * The status of the contact as per the last status update we've received for it.
     */
    final override var presenceStatus: PresenceStatus

    /**
     * Returns a String that could be used by any user interacting modules for referring to this contact.
     * An alias is not necessarily unique but is often more human readable than an address (or id).
     *
     * @return a String that can be used for referring to this contact when interacting with the user.
     */
    override val displayName: String
        get() {
            if (isResolved) {
                val entry = ssclCallback.getRosterEntry(contactJid!!.asBareJid())
                if (entry != null) {
                    val name = entry.name
                    if (StringUtils.isNotEmpty(name)) return name
                }
            }
            return address
        }

    /**
     * Returns the display name used when the contact was resolved. Used to detect renames.
     * The display name of the roster entry.
     */
    var serverDisplayName: String? = null

    /**
     * Returns a reference to the contact group that this contact is currently a child of or
     * null if the underlying protocol does not support persistent presence.
     *
     * @return a reference to the contact group that this contact is currently a child of or
     * null if the underlying protocol does not support persistent presence.
     */
    override val parentContactGroup: ContactGroup?
        get() {
            return ssclCallback.findContactGroup(this)
        }

    /**
     * Returns a reference to the protocol provider that created the contact.
     *
     * @return a reference to an instance of the ProtocolProviderService
     */
    final override val protocolProvider: ProtocolProviderService
        get() = ssclCallback.parentProvider

    /**
     * Specifies whether this contact is to be considered persistent or not. The method is to be
     * used _only_ when a non-persistent contact has been added to the contact list and its
     * encapsulated VolatileBuddy has been replaced with a standard buddy.
     *
     * persistent true if the buddy is to be considered persistent and false for volatile.
     */
    /**
     * Determines whether or not this contact is being stored by the server. Non persistent contacts
     * are common in the case of simple, non-persistent presence operation sets. They could however
     * also be seen in persistent presence operation sets when for example we have received an event
     * from someone not on our contact list. Non persistent contacts are volatile even when coming
     * from a persistent presence op. set. They would only exist until the application is closed
     * and will not be there next time it is loaded.
     *
     * @return true if the contact is persistent and false otherwise.
     */
    open override var isPersistent: Boolean

    /**
     * Returns the persistent data
     *
     * @return the persistent data
     */
    override val persistentData: String? = null

    /**
     * Determines whether or not this contact has been resolved against the server. Unresolved
     * contacts are used when initially loading a contact list that has been stored in a local file
     * until the presence operation set has managed to retrieve all the contact list from the
     * server and has properly mapped contacts to their on-line buddies.
     *
     * @return true if the contact has been resolved (mapped against a buddy) and false otherwise.
     */
    override fun isResolved(): Boolean {
        return isResolved
    }

    /**
     * Resolve this contact against the given entry
     *
     * entry the server stored entry
     */
    fun setResolved(entry: RosterEntry) {
        if (isResolved) return
        isResolved = true
        isPersistent = true
        contactJid = entry.jid
        serverDisplayName = entry.name
    }

    /**
     * Get source entry
     *
     * @return RosterEntry
     */
    fun getSourceEntry(): RosterEntry? {
        return if (contactJid == null) null else ssclCallback.getRosterEntry(contactJid!!.asBareJid())
    }

    /**
     * The current status message of this contact.
     */
    override var statusMessage: String? = null

    /**
     * Indicates if this contact supports resources.
     *
     * @return `false` to indicate that this contact doesn't support resources
     */
    override var isSupportResources = true

    /**
     * Returns an iterator over the resources supported by this contact or null if it doesn't support resources.
     *
     * @return null, as this contact doesn't support resources
     */
    override fun getResources(): Collection<ContactResource?>? {
        if (resources != null) {
            Timber.d("Contact: %s resources %s", displayName, resources!!.size)
            return ArrayList<ContactResource?>(resources!!.values)
        }
        return null
    }

    /**
     * Finds the `ContactResource` corresponding to the given bareJid.
     *
     * jid the fullJid for which we're looking for a resource
     * @return the `ContactResource` corresponding to the given bareJid.
     */
    fun getResourceFromJid(jid: FullJid?): ContactResource? {
        return if (resources == null || jid == null) null else resources!![jid]
    }

    fun getResourcesMap(): MutableMap<FullJid, ContactResourceJabberImpl?> {
        if (resources == null) {
            resources = ConcurrentHashMap()
        }
        return resources!!
    }

    /**
     * Notifies all registered `ContactResourceListener`s that an event has occurred.
     *
     * event the `ContactResourceEvent` to fire notification for
     */
    public override fun fireContactResourceEvent(event: ContactResourceEvent) {
        super.fireContactResourceEvent(event)
    }

    /**
     * Used from volatile contacts to handle jid and resources. Volatile contacts are always
     * unavailable so do not remove their resources from the contact as it will be the only
     * resource we will use. Note: volatile contact may not necessary contain a resource part.
     *
     * fullJid the fullJid of the volatile contact.
     */
    /**
     * Contains either the bareJid as retrieved from the Roster Entry, FullJid of ownJid OR
     * the VolatileContact BareJid/FullJid
     * Either return the bareJid of contact as retrieved from the Roster Entry Or The VolatileContact Jid.
     * VolatileContact jid may not have been resolved before it is being requested.
     */
    override var contactJid: Jid? = null
        get() {
            return if (isResolved && field != null) field else tempId!!
        }
        set(fullJid) {
            field = fullJid
            if (resources == null) resources = ConcurrentHashMap()

        }

    /**
     * Whether this contact is a mobile one.
     */
    override var isMobile = false
        get() {
            return presenceStatus.isOnline && field
        }

    /**
     * Changes the isLocal indicator.
     *
     * isLocal the new value.
     */
    fun setLocal(isLocal: Boolean) {
        this.isLocal = isLocal
    }

    fun setPhotoUri(uri: String?): Boolean {
        return if (uri != null && uri != photoUri) {
            photoUri = uri
            true
        } else if (photoUri != null && uri == null) {
            photoUri = null
            true
        } else {
            false
        }
    }

    private fun getOtrFingerprints(): ArrayList<String> {
        synchronized(keys) {
            val fingerprints = ArrayList<String>()
            try {
                if (keys.has(OTR_FP)) {
                    val prints = keys.getJSONArray(OTR_FP)
                    for (i in 0 until prints.length()) {
                        val print = if (prints.isNull(i)) null else prints.getString(i)
                        if (print != null && print.isNotEmpty()) {
                            fingerprints.add(prints.getString(i).lowercase())
                        }
                    }
                }
            } catch (ex: JSONException) {
                ex.printStackTrace()
            }
            return fingerprints
        }
    }

    fun addOtrFingerprint(print: String): Boolean {
        synchronized(keys) {
            return if (getOtrFingerprints().contains(print)) {
                false
            } else try {
                val fingerprints = if (!keys.has(OTR_FP)) {
                    JSONArray()
                } else {
                    keys.getJSONArray(OTR_FP)
                }
                fingerprints.put(print)
                keys.put(OTR_FP, fingerprints)
                true
            } catch (ex: JSONException) {
                ex.printStackTrace()
                false
            }
        }
    }

    fun getPgpKeyId(): Long {
        synchronized(keys) {
            return if (keys.has(PGP_KEY_ID)) {
                try {
                    keys.getLong(PGP_KEY_ID)
                } catch (e: JSONException) {
                    0
                }
            } else {
                0
            }
        }
    }

    fun setPgpKeyId(keyId: Long) {
        synchronized(keys) {
            try {
                keys.put(PGP_KEY_ID, keyId)
            } catch (ex: JSONException) {
                ex.printStackTrace()
            }
        }
    }

    /**
     * When access on start-up, return ttsEnable may be null.
     * Use getJid() to ensure unresolved contact (contactJid == null) returns a valid values.
     *
     * @return true if contact tts is enabled.
     */
    override var isTtsEnable: Boolean? = null
        get() {
            if (field == null) {
                val ttsEnable = getContactProperty(contactJid!!, TTS_ENABLE)
                field = if (StringUtils.isEmpty(ttsEnable)) false else ttsEnable.toBoolean()
            }
            return field
        }
        set(value) {
            if (field != value) {
                field = value
                if (value!!) {
                    updateContactProperty(contactJid!!, TTS_ENABLE, isTtsEnable.toString())
                } else {
                    updateContactProperty(contactJid!!, TTS_ENABLE, null)
                }
            }
        }

    /**
     * Unused method, need to clean up if required
     *
     * option
     */
    fun setOption(option: Int) {
        subscription = subscription or (1 shl option)
    }

    fun resetOption(option: Int) {
        subscription = subscription and (1 shl option).inv()
    }

    fun getOption(option: Int): Boolean {
        return subscription and (1 shl option) != 0
    }

    fun deleteOtrFingerprint(fingerprint: String): Boolean {
        synchronized(keys) {
            var success = false
            return try {
                if (keys.has("otr_fingerprints")) {
                    val newPrints = JSONArray()
                    val oldPrints = keys.getJSONArray("otr_fingerprints")
                    for (i in 0 until oldPrints.length()) {
                        if (oldPrints.getString(i) != fingerprint) {
                            newPrints.put(oldPrints.getString(i))
                        } else {
                            success = true
                        }
                    }
                    keys.put("otr_fingerprints", newPrints)
                }
                success
            } catch (e: JSONException) {
                false
            }
        }
    }

    object Options {
        const val TO = 0
        const val FROM = 1
        const val ASKING = 2
        const val PREEMPTIVE_GRANT = 3
        const val IN_ROSTER = 4
        const val PENDING_SUBSCRIPTION_REQUEST = 5
        const val DIRTY_PUSH = 6
        const val DIRTY_DELETE = 7
    }

    companion object {
        const val OTR_FP = "otr_fingerprints"
        const val OTR_POLICY = "otr_policy"
        private const val PGP_KEY_ID = "pgp_keyid"
        private const val TTS_ENABLE = "tts_Enable"
    }
}