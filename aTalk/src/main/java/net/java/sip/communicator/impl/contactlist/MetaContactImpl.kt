/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * * Licensed under the Apache License, Version 2.0 (the "License");
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
package net.java.sip.communicator.impl.contactlist

import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import net.java.sip.communicator.service.contactlist.event.MetaContactModifiedEvent
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ContactGroup
import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.OperationSetContactCapabilities
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.util.DataObject
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smackx.avatar.AvatarManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.jxmpp.jid.Jid
import timber.log.Timber
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * A default implementation of the `MetaContact` interface.
 *
 * @author Emil Ivov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
class MetaContactImpl : DataObject, MetaContact {
    /**
     * A vector containing all protocol specific contacts merged in this MetaContact.
     */
    private val protoContacts: MutableList<Contact?> = Vector()

    /**
     * The list of capabilities of the meta contact i.e. map of each OperationSet for all the contacts that support it.
     * Currently has problem as OpSet capability get updated by last contact resource presence
     */
    private val capabilities = ConcurrentHashMap<String, MutableList<Contact?>>()

    /**
     * The list of capabilities of the meta contact FullJid i.e. all contact resources. To overcome the above problem
     */
    private val capabilityJid = ConcurrentHashMap<String, MutableList<Jid?>>()

    /**
     * The number of contacts online in this meta contact.
     */
    private var contactsOnline = 0

    /**
     * The number of unread messages
     */
    private var unreadCount = 0

    /**
     * An id uniquely identifying the meta contact in this contact list.
     */
    private val uid: String

    /**
     * Returns a human readable string used by the UI to display the contact.
     */
    private var displayName: String? = ""

    /**
     * The contact that should be chosen by default when communicating with this meta contact.
     */
    private var defaultContact: Contact? = null

    /**
     * A locally cached copy of an avatar that we should return for lazy calls to the
     * getAvatarMethod() in order to speed up display.
     */
    private var mCachedAvatar: ByteArray? = null

    /**
     * A flag that tells us whether or not we have already tried to restore
     * an avatar from cache. We need this to know whether a `null` cached
     * avatar implies that there is no locally stored avatar or that we simply
     * haven't tried to retrieve it. This should allow us to only interrogate
     * the file system if haven't done so before.
     */
    private var avatarFileCacheAlreadyQueried = false

    /**
     * JSONObject containing the contact details i.e. Name -> JSONArray.
     */
    private val details: JSONObject
    /**
     * Determines if display name was changed for this `MetaContact` in user interface.
     *
     * @return whether display name was changed by user.
     */
    /**
     * Changes that display name was changed for this `MetaContact` in user interface.
     *
     * value control whether display name is user defined
     */
    /**
     * Whether user has renamed this meta contact.
     */
    var isDisplayNameUserDefined = false

    /**
     * Creates new meta contact with a newly generated meta contact UID.
     */
    internal constructor() {
        // create the uid
        uid = System.currentTimeMillis().toString() + hashCode()
        details = JSONObject()
    }

    /**
     * Creates a new meta contact with the specified UID. This constructor MUST ONLY be used when
     * restoring contacts from persistent storage.
     *
     * metaUID the meta uid that this meta contact should have.
     * details the already stored details for the contact.
     */
    internal constructor(metaUID: String, details: JSONObject) {
        uid = metaUID
        this.details = details
    }

    /**
     * Returns the number of protocol specific `Contact`s that this `MetaContact` contains.
     *
     * @return an int indicating the number of protocol specific contacts merged in this `MetaContact`
     */
    override fun getContactCount(): Int {
        return protoContacts.size
    }

    /**
     * Returns a Contact, encapsulated by this MetaContact and coming from the specified ProtocolProviderService.
     *
     * In order to prevent problems with concurrency, the `Iterator`
     * returned by this method is not be over the actual list of contacts but over a copy of that list.
     *
     * provider a reference to the `ProtocolProviderService` that we'd like to get a `Contact` for.
     * @return a `Contact` encapsulated in this `MetaContact` nd originating from the specified provider.
     */
    override fun getContactsForProvider(provider: ProtocolProviderService?): Iterator<Contact?> {
        val providerContacts = LinkedList<Contact?>()
        for (contact in protoContacts) {
            if (contact!!.protocolProvider === provider) providerContacts.add(contact)
        }
        return providerContacts.iterator()
    }

    /**
     * Returns all protocol specific Contacts, encapsulated by this MetaContact and supporting the
     * given `opSetClass`. If none of the contacts encapsulated by this MetaContact is
     * supporting the specified `OperationSet` class then an empty iterator is returned.
     *
     * opSetClass the operation for which the default contact is needed
     * @return a `List` over all contacts encapsulated in this `MetaContact` and
     * supporting the specified `OperationSet`
     */
    override fun getContactsForOperationSet(opSetClass: Class<out OperationSet?>?): List<Contact?> {
        val opSetContacts = LinkedList<Contact?>()
        for (contact in protoContacts) {
            val contactProvider = contact!!.protocolProvider
            // First try to ask the capabilities operation set if such is available.
            val capOpSet = contactProvider.getOperationSet(OperationSetContactCapabilities::class.java)
            if (capOpSet != null) {
                synchronized(capabilities) {
                    val capContacts: List<Contact?>? = capabilities[opSetClass!!.name]
                    if (capContacts != null && capContacts.contains(contact)) opSetContacts.add(contact)
                }
            } else if (contactProvider.getOperationSet(opSetClass!!) != null) opSetContacts.add(contact)
        }
        return opSetContacts
    }

    /**
     * Determines if the given `feature` is supported by this metaContact for all presence contact.
     *
     * feature the feature to check for
     * @return `true` if the required feature is supported; otherwise, `false`
     */
    override fun isFeatureSupported(feature: String?): Boolean {
        val contact = getDefaultContact()
        val pps = contact!!.protocolProvider as ProtocolProviderServiceJabberImpl?
        val discoveryManager = pps!!.discoveryManager ?: return false

        // Proceed only for presence with Type.available
        val presences = Roster.getInstanceFor(pps.connection).getPresences(contact.contactJid!!.asBareJid())
        for (presence in presences) {
            if (presence.isAvailable) {
                val featureInfo = discoveryManager.discoverInfoNonBlocking(presence.from)
                if ((null != featureInfo) && featureInfo.containsFeature(feature)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Returns contacts, encapsulated by this MetaContact and belonging to the specified protocol ContactGroup.
     *
     * In order to prevent problems with concurrency, the `Iterator` returned by
     * this method is not be over the actual list of contacts but over a copy of that list.
     *
     * parentProtoGroup
     * @return an Iterator over all `Contact`s encapsulated in this
     * `MetaContact` and belonging to the specified proto ContactGroup.
     */
    fun getContactsForContactGroup(parentProtoGroup: ContactGroup?): Iterator<Contact?> {
        val providerContacts: MutableList<Contact?> = LinkedList()
        for (contact in protoContacts) {
            if (contact!!.parentContactGroup == parentProtoGroup) providerContacts.add(contact)
        }
        return providerContacts.iterator()
    }

    /**
     * Returns a contact encapsulated by this meta contact, having the specified
     * contactAddress and coming from the indicated ownerProvider.
     *
     * contactAddress the address of the contact who we're looking for.
     * ownerProvider a reference to the ProtocolProviderService that the contact we're looking for belongs
     * to.
     * @return a reference to a `Contact`, encapsulated by this MetaContact, carrying
     * the specified address and originating from the specified ownerProvider or null if no such contact exists..
     */
    override fun getContact(contactAddress: String?, ownerProvider: ProtocolProviderService?): Contact? {
        for (contact in protoContacts) {
            if (contact!!.protocolProvider === ownerProvider && (contact!!.address == contactAddress || contact.toString() == contactAddress)) return contact
        }
        return null
    }

    /**
     * Returns a contact encapsulated by this meta contact, having the specified
     * contactAddress and coming from a provider with a matching
     * `accountID`. The method returns null if no such contact exists.
     *
     * contactAddress the address of the contact who we're looking for.
     * accountID the identifier of the provider that the contact we're looking for must belong to.
     * @return a reference to a `Contact`, encapsulated by this MetaContact, carrying the
     * specified address and originating from the ownerProvider carrying `accountID`.
     */
    fun getContact(contactAddress: String?, accountID: String): Contact? {
        for (contact in protoContacts) {
            if (contact!!.protocolProvider.accountID.accountUniqueID == accountID && contact.address == contactAddress) return contact
        }
        return null
    }

    /**
     * Returns `true` if the given `protocolContact` is contained in
     * this `MetaContact`, otherwise - returns `false`.
     *
     * protocolContact the `Contact` we're looking for
     * @return `true` if the given `protocolContact` is contained in
     * this `MetaContact`, otherwise - returns `false`
     */
    override fun containsContact(protocolContact: Contact?): Boolean {
        return protoContacts.contains(protocolContact)
    }

    /**
     * Returns a `java.util.Iterator` over all protocol specific `Contacts`
     * encapsulated by this `MetaContact`.
     *
     * In order to prevent problems with concurrency, the `Iterator` returned by
     * this method is not over the actual list of contacts but over a copy of that list.
     *
     * @return a `java.util.Iterator` over all protocol specific `Contact`s
     * that were registered as subContacts for this `MetaContact`
     */
    override fun getContacts(): Iterator<Contact?> {
        return LinkedList(protoContacts).iterator()
    }

    /**
     * Currently simply returns the most connected protocol contact. We should add
     * the possibility to choose it also according to pre-configured preferences.
     *
     * @return the default `Contact` to use when communicating with this `MetaContact`
     */
    override fun getDefaultContact(): Contact {
        if (defaultContact == null) {
            var currentStatus: PresenceStatus? = null
            for (protoContact in protoContacts) {
                val contactStatus = protoContact!!.presenceStatus
                if (currentStatus != null) {
                    if (currentStatus.status < contactStatus.status) {
                        currentStatus = contactStatus
                        defaultContact = protoContact
                    }
                } else {
                    currentStatus = contactStatus
                    defaultContact = protoContact
                }
            }
        }
        return defaultContact!!
    }

    /**
     * Returns a default contact for a specific operation (call, file transfer, IM ...)
     * cmeng may possibly replaced by getOpSetSupportedContact()
     *
     * operationSet the operation for which the default contact is needed
     * @return the default contact for the specified operation.
     */
    override fun getDefaultContact(operationSet: Class<out OperationSet?>?): Contact? {
        var defaultOpSetContact: Contact? = null
        val defaultContact = getDefaultContact()

        // if the current default contact supports the requested operationSet we use it
        if (defaultContact != null) {
            val pps = defaultContact.protocolProvider

            // First try to ask the capabilities operation set if such is available.
            val capOpSet = pps.getOperationSet(OperationSetContactCapabilities::class.java)
            if (capOpSet != null) {
                synchronized(capabilities) {
                    val capContacts: MutableList<Contact?>? = capabilities[operationSet!!.name]
                    if (capContacts != null && capContacts.contains(defaultContact)) {
                        defaultOpSetContact = defaultContact
                    }
                }
            } else if (pps.getOperationSet(operationSet!!) != null) {
                defaultOpSetContact = defaultContact
            }
        }

        // if default not supported, then check the protoContacts for one
        if (defaultOpSetContact == null) {
            var currentStatus: PresenceStatus? = null
            for (protoContact in protoContacts) {
                val contactProvider = protoContact!!.protocolProvider

                // First try to ask the capabilities operation set if such is available.
                val capOpSet: OperationSetContactCapabilities? = contactProvider.getOperationSet(OperationSetContactCapabilities::class.java)

                // We filter to care only about contact which support the needed opset.
                if (capOpSet != null) {
                    val capContacts: MutableList<Contact?>?
                    synchronized(capabilities) {
                        capContacts = capabilities[operationSet!!.name]
                    }
                    if (capContacts == null || !capContacts.contains(protoContact)) {
                        continue
                    }
                } else if (contactProvider.getOperationSet(operationSet!!) == null) continue

                val contactStatus = protoContact.presenceStatus
                if (currentStatus != null) {
                    if (currentStatus.status < contactStatus.status) {
                        currentStatus = contactStatus
                        defaultOpSetContact = protoContact
                    }
                } else {
                    currentStatus = contactStatus
                    defaultOpSetContact = protoContact
                }
            }
        }
        return defaultOpSetContact
    }

    /**
     * Returns a contact for a specific operationSet (call, file transfer, IM ...), null if none is found
     * Note: this is currently used for showing the video/call buttons; and protoContacts.size() == 1
     *
     * operationSet the operation for which the contact is needed
     * @return a contact that supports the specified operation.
     */
    override fun getOpSetSupportedContact(operationSet: Class<out OperationSet?>?): Contact? {
        for (opSetContact in protoContacts) {
            val jid = opSetContact!!.contactJid // always a BareJid

            // First try to ask the capabilities operation set if such is available.
            val pps = opSetContact.protocolProvider
            val capOpSet = pps.getOperationSet(OperationSetContactCapabilities::class.java)

            // We filter to care only about opSetContact which support the needed opSet.
            if (capOpSet != null) {
                synchronized(capabilityJid) {
                    val capJids = capabilityJid[operationSet!!.name]
                            ?: return null
                    // Just return null if none supported
                    for (jidx in capJids) {
                        if (jid!!.isParentOf(jidx)) {
                            return opSetContact
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Returns a String identifier (the actual contents is left to implementations) this
     * `MetaContact` in that uniquely represents the containing `MetaContactList`
     *
     * @return a String uniquely identifying this meta contact.
     */
    override fun getMetaUID(): String {
        return uid
    }

    /**
     * Set the unread message count for this metaContact
     *
     * count unread message count
     */
    override fun setUnreadCount(count: Int) {
        unreadCount = count
    }

    /**
     * Returns the unread message count for this metaContact
     *
     * @return the unread message count
     */
    override fun getUnreadCount(): Int {
        return unreadCount
    }

    /**
     * Compares this meta contact with the specified object for order.  Returns
     * a negative integer, zero, or a positive integer as this meta contact is
     * less than, equal to, or greater than the specified object.
     *
     * The result of this method is calculated the following way:
     *
     * (contactsOnline - o.contactsOnline) * 1 000 000 <br></br>
     * + getDisplayName().compareTo(o.getDisplayName()) * 100 000
     * + getMetaUID().compareTo(o.getMetaUID())<br></br>
     *
     * Or in other words ordering of meta accounts would be first done by presence status,, and
     * finally (in order to avoid then display name equalities) be the fairly random meta contact metaUID.
     *
     * o the `MetaContact` to be compared.
     * @return a negative integer, zero, or a positive integer as this object
     * is less than, equal to, or greater than the specified object.
     * @throws ClassCastException if the specified object is not a MetaContactListImpl
     */

    override fun compareTo(other: MetaContact?): Int {
        val target = other as MetaContactImpl
        val isOnline = if (contactsOnline > 0) 1 else 0
        val targetIsOnline = if (target.contactsOnline > 0) 1 else 0
        return (10 - isOnline - (10 - targetIsOnline)) * 100000000 + getDisplayName()!!.compareTo(target.getDisplayName()!!, ignoreCase = true) * 10000 + getMetaUID().compareTo(target.getMetaUID())
    }

    /**
     * Returns a string representation of this contact, containing most of its representative details.
     *
     * @return a string representation of this contact.
     */
    override fun toString(): String {
        return "MetaContact[ DisplayName=" + getDisplayName() + "]"
    }

    /**
     * Returns a characteristic display name that can be used when including
     * this `MetaContact` in user interface.
     *
     * @return a human readable String that represents this meta contact.
     */
    override fun getDisplayName(): String? {
        return displayName
    }

    /**
     * Queries a specific protocol `Contact` for its avatar. Beware that this method
     * could cause multiple network operations. Use with caution.
     *
     * contact the protocol `Contact` to query for its avatar
     * @return an array of `byte`s representing the avatar returned by the
     * specified `Contact` or `null` if the
     * specified `Contact` did not or failed to return an avatar
     */
    private fun queryProtoContactAvatar(contact: Contact?): ByteArray? {
        try {
            val contactImage = contact!!.image
            if (contactImage != null && contactImage.isNotEmpty()) return contactImage
        } catch (ex: Exception) {
            Timber.e(ex, "Failed to get the photo of contact %s", contact)
        }
        return null
    }

    /**
     * Returns the avatar of this contact, that can be used when including this
     * `MetaContact` in user interface. The isLazy parameter would tell
     * the implementation if it could return the locally stored avatar or it
     * should obtain the avatar right from the server.
     *
     * isLazy Indicates if this method should return the locally stored avatar or it should
     * obtain the avatar right from the server.
     * @return an avatar (e.g. user photo) of this contact.
     */
    override fun getAvatar(isLazy: Boolean): ByteArray? {
        var result: ByteArray?
        if (!isLazy) {
            // the caller is willing to perform a lengthy operation so let's
            // query the proto contacts for their avatars.
            val protoContacts = getContacts()
            while (protoContacts.hasNext()) {
                val contact = protoContacts.next()
                result = queryProtoContactAvatar(contact)

                // if we got a result from the above, then let's cache and return it.
                if (result != null && result.isNotEmpty()) {
                    cacheAvatar(contact, result)
                    return result
                }
            }
        }

        // if we get here then the caller is probably not willing to perform
        // network operations and opted for a lazy retrieve (... or the
        // queryAvatar method returned null because we are calling it too often)
        if (mCachedAvatar != null && mCachedAvatar!!.isNotEmpty()) {
            // we already have a cached avatar, so let's return it
            return mCachedAvatar
        }

        // no cached avatar. let's try the file system for previously stored
        // ones. (unless we already did this)
        if (avatarFileCacheAlreadyQueried) return null
        avatarFileCacheAlreadyQueried = true
        val iter = getContacts()
        while (iter.hasNext()) {
            val protoContact = iter.next() ?: continue

            // mCachedAvatar = AvatarCacheUtils.getCachedAvatar(protoContact);
            val bareJid = protoContact.contactJid!!.asBareJid()
            mCachedAvatar = AvatarManager.getAvatarImageByJid(bareJid)

            /*
             * Caching a zero-length avatar happens but such an avatar isn't very useful.
             */
            if (mCachedAvatar != null && mCachedAvatar!!.isNotEmpty()) return mCachedAvatar
        }
        return null
    }

    /**
     * Returns an avatar that can be used when presenting this `MetaContact` in user interface.
     * The method would also make sure that we try the network for new versions of avatars.
     *
     * @return an avatar (e.g. user photo) of this contact.
     */
    override fun getAvatar(): ByteArray? {
        return getAvatar(false)
    }

    /**
     * Sets a name that can be used when displaying this contact in user interface components.
     *
     * displayName a human readable String representing this `MetaContact`
     */
    fun setDisplayName(displayName: String?) {
        synchronized(parentGroupModLock) {
            if (parentGroup != null) parentGroup!!.lightRemoveMetaContact(this)
            this.displayName = displayName ?: ""
            if (parentGroup != null) parentGroup!!.lightAddMetaContact(this)
        }
    }

    /**
     * Adds the specified protocol specific contact to the list of contacts merged in this
     * meta contact. The method also keeps up to date the contactsOnline field which is used
     * in the compareTo() method.
     *
     * contact the protocol specific Contact to add.
     */
    fun addProtoContact(contact: Contact?) {
        synchronized(parentGroupModLock) {
            if (parentGroup != null) parentGroup!!.lightRemoveMetaContact(this)
            contactsOnline += if (contact!!.presenceStatus.isOnline) 1 else 0
            protoContacts.add(contact)

            // Re-init the default contact.
            defaultContact = null

            // if this is our first contact and we don't already have a display
            // name, use theirs.
            if (protoContacts.size == 1 && (displayName == null
                            || displayName!!.trim { it <= ' ' }.isEmpty())) {
                // be careful not to use setDisplayName() here cause this will bring us into a deadlock.
                displayName = contact.displayName
            }
            if (parentGroup != null) parentGroup!!.lightAddMetaContact(this)
            val contactProvider = contact.protocolProvider

            // Check if the capabilities operation set is available for this
            // contact and add a listener to it in order to track capabilities'
            // changes for all contained protocol contacts.
            val capOpSet = contactProvider.getOperationSet(OperationSetContactCapabilities::class.java)
            if (capOpSet != null) {
                addCapabilities(contact, capOpSet.getSupportedOperationSets(contact))
            }
        }
    }

    /**
     * Called by MetaContactListServiceImpl after a contact has changed its status,
     * so that ordering in the parent group is updated. The method also elects the
     * most connected contact as default contact.
     *
     * @return the new index at which the contact was added.
     */
    fun reevalContact(): Int {
        synchronized(parentGroupModLock) {

            // first lightremove or otherwise we won't be able to get hold of the contact
            if (parentGroup != null) {
                parentGroup!!.lightRemoveMetaContact(this)
            }
            contactsOnline = 0
            var maxContactStatus = 0
            for (contact in protoContacts) {
                val contactStatus = contact!!.presenceStatus.status
                if (maxContactStatus < contactStatus) {
                    maxContactStatus = contactStatus
                    defaultContact = contact
                }
                if (contact.presenceStatus.isOnline) contactsOnline++
            }
            // now read it and the contact would be automatically placed
            // properly by the containing group
            if (parentGroup != null) {
                return parentGroup!!.lightAddMetaContact(this)
            }
        }
        return -1
    }

    /**
     * Removes the specified protocol specific contact from the contacts encapsulated in
     * this `MetaContact`. The method also updates the total status field accordingly.
     * And updates its ordered position in its parent group. If the display name of this
     * `MetaContact` was the one of the removed contact, we update it.
     *
     * contact the contact to remove
     */
    fun removeProtoContact(contact: Contact?) {
        synchronized(parentGroupModLock) {
            if (parentGroup != null) parentGroup!!.lightRemoveMetaContact(this)
            contactsOnline -= if (contact!!.presenceStatus.isOnline) 1 else 0
            protoContacts.remove(contact)
            if (defaultContact === contact) defaultContact = null
            if (protoContacts.size > 0 && displayName == contact.displayName) {
                displayName = getDefaultContact()!!.displayName
            }
            if (parentGroup != null) parentGroup!!.lightAddMetaContact(this)
            val contactProvider = contact.protocolProvider

            // Check if the capabilities operation set is available for this
            // contact and add a listener to it in order to track capabilities'
            // changes for all contained protocol contacts.
            val capOpSet = contactProvider.getOperationSet(
                    OperationSetContactCapabilities::class.java)
            if (capOpSet != null) {
                removeCapabilities(contact, capOpSet.getSupportedOperationSets(contact))
            }
        }
    }

    /**
     * Removes all proto contacts that belong to the specified provider.
     *
     * provider the provider whose contacts we want removed.
     * @return true if this `MetaContact` was modified and false otherwise.
     */
    fun removeContactsForProvider(provider: ProtocolProviderService): Boolean {
        var modified = false
        val contactsIter = protoContacts.iterator()
        while (contactsIter.hasNext()) {
            val contact = contactsIter.next()
            if (contact!!.protocolProvider === provider) {
                contactsIter.remove()
                modified = true
            }
        }
        // if the default contact has been modified, set it to null
        if (modified && !protoContacts.contains(defaultContact)) {
            defaultContact = null
        }
        return modified
    }

    /**
     * Removes all proto contacts that belong to the specified protocol group.
     *
     * protoGroup the group whose children we want removed.
     * @return true if this `MetaContact` was modified and false otherwise.
     */
    fun removeContactsForGroup(protoGroup: ContactGroup?): Boolean {
        var modified = false
        val contacts = protoContacts.iterator()
        while (contacts.hasNext()) {
            val contact = contacts.next()
            if (contact!!.parentContactGroup === protoGroup) {
                contacts.remove()
                modified = true
            }
        }
        // if the default contact has been modified, set it to null
        if (modified && !protoContacts.contains(defaultContact)) {
            defaultContact = null
        }
        return modified
    }

    /**
     * A callback to the meta contact group that is currently our parent. If
     * this is an orphan meta contact that has not yet been added or has been
     * removed from a group this callback is going to be null.
     */

    var parentGroup: MetaContactGroupImpl? = null
        /**
         * Sets `parentGroup` as a parent of this meta contact. Do not call this method with a
         * null argument even if a group is removing this contact from itself as this could lead to
         * race conditions (imagine another group setting itself as the new parent and you
         * removing it). Use unsetParentGroup instead.
         *
         * parentGroup the `MetaContactGroupImpl` that is currently a parent of this meta contact.
         * @throws NullPointerException if `parentGroup` is null.
         */
        set(parentGroup) {
            if (parentGroup == null) throw NullPointerException("Do not call this method with a "
                    + "null argument even if a group is removing this contact "
                    + "from itself as this could lead to race conditions "
                    + "(imagine another group setting itself as the new parent"
                    + " and you  removing it). Use unsetParentGroup instead.")
            synchronized(parentGroupModLock) { field = parentGroup }
        }

    /**
     * If `parentGroup` was the parent of this meta contact then it sets it to null. Call
     * this method when removing this contact from a meta contact group.
     *
     * parentGrp the `MetaContactGroupImpl` that we don't want considered as a parent of this
     * contact any more.
     */
    fun unsetParentGroup(parentGrp: MetaContactGroupImpl) {
        synchronized(parentGroupModLock) { if (parentGroup == parentGrp) this.parentGroup = null }
    }

    /**
     * Returns the MetaContactGroup currently containing this meta contact
     *
     * @return a reference to the MetaContactGroup currently containing this meta contact.
     */
    override fun getParentMetaContactGroup(): MetaContactGroup? {
        return parentGroup
    }

    /**
     * Adds a custom detail to this contact.
     *
     * name name of the detail.
     * value the value of the detail.
     */
    override fun addDetail(name: String, value: String) {
        try {
            var jsonArray = details[name] as JSONArray?
            if (jsonArray == null) {
                jsonArray = JSONArray()
            }
            jsonArray.put(value)
            details.put(name, jsonArray)
            fireMetaContactModified(name, null, value)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * Remove the given detail.
     *
     * name of the detail to be removed.
     * value value of the detail to be removed.
     */
    override fun removeDetail(name: String, value: String?) {
        try {
            val jsonArray = details[name] as JSONArray?
            if (jsonArray != null && jsonArray.length() != 0) {
                for (i in 0 until jsonArray.length()) {
                    if (value == jsonArray.getString(i)) {
                        jsonArray.remove(i)
                        fireMetaContactModified(name, value, null)
                        break
                    }
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * Remove all details with given name.
     *
     * name of the details to be removed.
     */
    override fun removeDetails(name: String) {
        val itemRemoved = details.remove(name)
        if (itemRemoved != null) fireMetaContactModified(name, itemRemoved, null)
    }

    /**
     * Change the detail.
     *
     * name of the detail to be changed.
     * oldValue the old value of the detail.
     * newValue the new value of the detail.
     */
    override fun changeDetail(name: String, oldValue: String, newValue: String) {
        try {
            val jsonArray = details[name] as JSONArray
            for (i in 0 until jsonArray.length()) {
                if (oldValue == jsonArray.getString(i)) {
                    jsonArray.put(i, newValue)
                    fireMetaContactModified(name, oldValue, newValue)
                    break
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * Gets the JSONObject details.
     *
     * @return the JSONObject which represent the details of the metaContactImpl
     */
    override fun getDetails(): JSONObject {
        return details
    }

    /**
     * Gets all details with a given name.
     *
     * name the name of the details we are searching for
     * @return a JSONArray which represent the details with the specified
     * `name`
     */
    override fun getDetails(name: String): JSONArray {
        var jsonArray = JSONArray()
        try {
            jsonArray = details[name] as JSONArray
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return jsonArray
    }

    /**
     * Fires a new `MetaContactModifiedEvent` which is to notify about a modification with
     * a specific name of this `MetaContact` which has caused a property value change from a specific
     * `oldValue` to a specific `newValue`.
     *
     * modificationName the name of the modification which has caused a new `MetaContactModifiedEvent`
     * to be fired
     * oldValue the value of the property before the modification
     * newValue the value of the property after the modification
     */
    private fun fireMetaContactModified(modificationName: String?, oldValue: Any?, newValue: Any?) {
        //val parentGroup = getParentGroup()
        parentGroup?.mclServiceImpl!!.fireMetaContactEvent(
                MetaContactModifiedEvent(this, modificationName!!, oldValue, newValue))
    }

    /**
     * Stores avatar bytes in the given `Contact`.
     *
     * protoContact The contact in which we store the avatar.
     * avatarBytes The avatar image bytes.
     */
    fun cacheAvatar(protoContact: Contact?, avatarBytes: ByteArray?) {
        mCachedAvatar = avatarBytes
        avatarFileCacheAlreadyQueried = true

        // AvatarCacheUtils.cacheAvatar(protoContact, avatarBytes);
        val userId = protoContact!!.contactJid!!.asBareJid()
        AvatarManager.addAvatarImage(userId, avatarBytes, false)
    }

    /**
     * Updates the capabilities for the given contact.
     *
     * contact the `Contact`, which capabilities have changed
     * opSets the new updated set of operation sets
     */
    fun updateCapabilities(contact: Contact, jid: Jid?, opSets: Map<String, OperationSet?>) {
        contact.protocolProvider.getOperationSet(OperationSetContactCapabilities::class.java)
                ?: return

        // This should not happen, because this method is called explicitly for
        // events coming from the capabilities operation set.

        // Update based on contact only (not considering the contact resource)
        removeCapabilities(contact, opSets)
        addCapabilities(contact, opSets)

        // Update based on FullJid
        removeCapabilities(jid, opSets)
        addCapabilities(jid, opSets)
    }

    /**
     * Remove capabilities for the given contacts.
     *
     * contact the `Contact`, which capabilities we remove
     * opSets the new updated set of operation sets
     */
    private fun removeCapabilities(contact: Contact, opSets: Map<String, OperationSet?>) {
        synchronized(capabilities) {
            val caps: MutableIterator<Map.Entry<String, MutableList<Contact?>>> = capabilities.entries.iterator()
            val contactNewCaps = opSets.keys
            while (caps.hasNext()) {
                val (opSetName, contactsForCap) = caps.next()
                if (contactsForCap.contains(contact) && !contactNewCaps.contains(opSetName)) {
                    contactsForCap.remove(contact)
                    if (contactsForCap.size == 0) caps.remove()
                }
            }
        }
    }

    /**
     * Adds the capabilities of the given contact.
     *
     * contact the `Contact`, which capabilities we add
     * opSets the map of operation sets supported by the contact
     */
    private fun addCapabilities(contact: Contact, opSets: Map<String, OperationSet?>) {
        synchronized(capabilities) {
            for (newCap in opSets.keys) {
                var capContacts: MutableList<Contact?>?
                if (!capabilities.containsKey(newCap)) {
                    capContacts = LinkedList()
                    capContacts.add(contact)
                    capabilities[newCap] = capContacts
                } else {
                    capContacts = capabilities[newCap]
                    if (capContacts != null && !capContacts.contains(contact)) {
                        capContacts.add(contact)
                    }
                }
            }
        }
    }

    /**
     * Remove capabilities for the given contacts based on FullJid
     *
     * jid the FullJid of the `Contact`, whom capabilities we remove. Null applies to all resources
     * opSets the new updated set of operation sets.
     */
    private fun removeCapabilities(jid: Jid?, opSets: Map<String, OperationSet?>) {
        Timber.d("Opset capability removal started: %s", jid)
        synchronized(capabilityJid) {
            val capJids: MutableIterator<Map.Entry<String, MutableList<Jid?>>> = capabilityJid.entries.iterator()
            val contactNewCaps = opSets.keys
            while (capJids.hasNext()) {
                val (opSetName, value) = capJids.next()
                val jidsForCap = value.iterator()
                while (jidsForCap.hasNext()) {
                    val jidx = jidsForCap.next()
                    if (jid!!.equals(jidx) && !contactNewCaps.contains(opSetName)) {
                        jidsForCap.remove()
                        if (!jidsForCap.hasNext()) {
                            capJids.remove()
                            break
                        }
                    }
                }
            }
        }
    }

    /**
     * Adds the capabilities of the given contact based on FullJid
     *
     * jid the FullJid of the `Contact`, whom capabilities we remove. Null applies to all resources
     * opSets the map of operation sets supported by the contact.
     */
    private fun addCapabilities(jid: Jid?, opSets: Map<String, OperationSet?>) {
        Timber.d("Opset capability adding started: %s", jid)
        synchronized(capabilityJid) {
            for (newCap in opSets.keys) {
                var capJids: MutableList<Jid?>?
                if (!capabilityJid.containsKey(newCap)) {
                    capJids = LinkedList()
                    capJids.add(jid)
                    capabilityJid[newCap] = capJids
                } else {
                    capJids = capabilityJid[newCap]
                    if (capJids != null && !capJids.contains(jid)) {
                        capJids.add(jid)
                    }
                }
            }
        }
    }
    /*
         * XXX The use of uid as parentGroupModLock is a bit unusual but a dedicated lock enlarges
         * the shallow runtime size of this instance and having hundreds of MetaContactImpl
         * instances is not unusual for a multi-protocol application. With respect to
         * parentGroupModLock being unique among the MetaContactImpl instances, uid is fine
         * because it is also supposed to be unique in the same way.
         */

    /**
     * Gets the sync lock for use when modifying [.parentGroup].
     *
     * @return the sync lock for use when modifying [.parentGroup]
     */
    private val parentGroupModLock: Any
        get() = uid
    /*
     * XXX The use of uid as parentGroupModLock is a bit unusual but a dedicated lock enlarges
     * the shallow runtime size of this instance and having hundreds of MetaContactImpl
     * instances is not unusual for a multi-protocol application. With respect to
     * parentGroupModLock being unique among the MetaContactImpl instances, uid is fine
     * because it is also supposed to be unique in the same way.
     */
}