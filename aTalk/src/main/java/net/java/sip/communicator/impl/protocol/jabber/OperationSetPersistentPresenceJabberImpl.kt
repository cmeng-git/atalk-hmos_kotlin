/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.protocol.ServerStoredDetails.GenericDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.ImageDetail
import net.java.sip.communicator.service.protocol.event.*
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum
import net.java.sip.communicator.util.ConfigurationUtils.isPresenceSubscribeAuto
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.jivesoftware.smack.SmackException.*
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smack.packet.*
import org.jivesoftware.smack.roster.*
import org.jivesoftware.smack.roster.SubscribeListener.SubscribeAnswer
import org.jivesoftware.smackx.avatar.AvatarManager
import org.jivesoftware.smackx.avatar.useravatar.UserAvatarManager
import org.jivesoftware.smackx.avatar.useravatar.listener.UserAvatarListener
import org.jivesoftware.smackx.avatar.useravatar.packet.AvatarMetadata
import org.jivesoftware.smackx.avatar.vcardavatar.VCardAvatarManager
import org.jivesoftware.smackx.avatar.vcardavatar.listener.VCardAvatarListener
import org.jivesoftware.smackx.nick.packet.Nick
import org.jivesoftware.smackx.vcardtemp.packet.VCard
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.FullJid
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException
import timber.log.Timber
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The Jabber implementation of a Persistent Presence Operation set. This class manages our own
 * presence status as well as subscriptions for the presence status of our buddies. It also offers methods
 * for retrieving and modifying the buddy contact list and adding listeners for changes in its layout.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class OperationSetPersistentPresenceJabberImpl(pps: ProtocolProviderServiceJabberImpl,
        /**
         * Handles and retrieves all info of our contacts or account info from the downloaded vcard.
         *
         * @see InfoRetriever.retrieveDetails
         */
        private val mInfoRetriever: InfoRetriever) : AbstractOperationSetPersistentPresence<ProtocolProviderServiceJabberImpl?>(pps), VCardAvatarListener, UserAvatarListener, SubscribeListener, PresenceEventListener {
    /**
     * Contains our current status message. Note that this field would only be changed once the
     * server has confirmed the new status message and not immediately upon setting a new one..
     */
    private var currentStatusMessage: String? = ""

    /**
     * The presence status that we were last notified of entering. The initial one is OFFLINE
     */
    private var currentStatus: PresenceStatus?

    /**
     * `true` update both account and contacts status. set to `false` when the
     * session is resumed to leave contacts' status untouched.
     */
    private var updateAllStatus = true
    /**
     * Returns the contactList impl.
     *
     * @return the contactList impl.
     */
    /**
     * The server stored contact list that will be encapsulating smack's buddy list.
     */
    val ssContactList: ServerStoredContactListJabberImpl

    /**
     * Handle subscriptions event is ready uf true.
     */
    private var handleSubscribeEvent = false
    private var mRoster: Roster? = null

    /**
     * Current resource priority.
     */
    private var resourcePriorityAvailable = 30

    /**
     * Manages statuses and different user resources.
     */
    private var mContactChangesListener: ContactChangesListener? = null

    /**
     * Manages the presence extension to advertise the SHA-1 hash of this account avatar as
     * defined in XEP-0153. It also provide persistence storage of the received avatar
     */
    private var vCardAvatarManager: VCardAvatarManager? = null

    /**
     * Manages the event extension to advertise the SHA-1 hash of this account avatar as defined in XEP-0084.
     */
    private var userAvatarManager: UserAvatarManager? = null

    /**
     * Handles all the logic about mobile indicator for contacts.
     */
    private val mobileIndicator: MobileIndicator

    /**
     * The last sent presence to server, contains the status, the resource and its priority.
     */
    private var currentPresence: Presence? = null

    /**
     * The local contact presented by the provider.
     */
    private var localContact: ContactJabberImpl? = null

    /*
     * cmeng: 20190212 - Disable info Retrieval on first login even when local cache is empty
     * ejabberd will send VCardTempXUpdate with photo attr in <presence/> stanza when buddy come online
     */
    private val infoRetrieveOnStart = false

    /**
     * Registers a listener that would receive events upon changes in server stored groups.
     *
     * @param listener a ServerStoredGroupChangeListener impl that would receive events upon group changes.
     */
    override fun addServerStoredGroupChangeListener(listener: ServerStoredGroupListener) {
        ssContactList.addGroupListener(listener)
    }

    /**
     * Creates a group with the specified name and parent in the server stored contact list.
     *
     * @param parent the group where the new group should be created
     * @param groupName the name of the new group to create.
     *
     * @throws OperationFailedException if such group already exists
     */
    @Throws(OperationFailedException::class)
    override fun createServerStoredContactGroup(parent: ContactGroup, groupName: String) {
        assertConnected()
        require(parent.canContainSubgroups()) { "The specified parent group cannot contain child groups: $parent" }
        ssContactList.createGroup(groupName)
    }

    /**
     * Creates a non persistent contact for the specified address. This would also create (if
     * necessary) a group for volatile contacts that would not be added to the server stored
     * contact list. The volatile contact would remain in the list until it is really added to
     * the contact list or until the application is terminated.
     *
     * @param id the address of the contact to create.
     *
     * @return the newly created volatile `ContactImpl`
     */
    @Synchronized
    fun createVolatileContact(id: Jid): ContactJabberImpl {
        return createVolatileContact(id, false)
    }

    /**
     * Creates a non persistent contact for the specified address. This would also create (if
     * necessary) a group for volatile contacts that would not be added to the server stored
     * contact list. The volatile contact would remain in the list until it is really added to
     * the contact list or until the application is terminated.
     *
     * @param id the address of the contact to create.
     * @param displayName the display name of the contact.
     *
     * @return the newly created volatile `ContactImpl`
     */
    @Synchronized
    fun createVolatileContact(id: Jid, displayName: String?): ContactJabberImpl {
        return createVolatileContact(id, false, displayName)
    }

    /**
     * Creates a non persistent contact for the specified address. This would also create (if
     * necessary) a group for volatile contacts that would not be added to the server stored
     * contact list. The volatile contact would remain in the list until it is really added to
     * the contact list or until the application is terminated.
     *
     * @param id the address of the contact to create.
     * @param isPrivateMessagingContact indicates whether the contact should be private messaging contact or not.
     *
     * @return the newly created volatile `ContactImpl`
     */
    @Synchronized
    fun createVolatileContact(id: Jid, isPrivateMessagingContact: Boolean): ContactJabberImpl {
        return createVolatileContact(id, isPrivateMessagingContact, null)
    }

    /**
     * Creates a non persistent contact for the specified address. This would also create (if
     * necessary) a group for volatile contacts that would not be added to the server stored
     * contact list. The volatile contact would remain in the list until it is really added to
     * the contact list or until the application is terminated.
     *
     * @param id the address of the contact to create.
     * @param isPrivateMessagingContact indicates whether the contact should be private messaging contact or not.
     * @param displayName the display name of the contact.
     *
     * @return the newly created volatile `ContactImpl`
     */
    @Synchronized
    fun createVolatileContact(id: Jid, isPrivateMessagingContact: Boolean, displayName: String?): ContactJabberImpl {
        // Timber.w(new Exception(), "Created volatile contact %s", id);
        // first check for existing before created new.
        val notInContactListGroup = ssContactList.nonPersistentGroup
        var sourceContact: ContactJabberImpl? = null
        if (notInContactListGroup != null) {
            sourceContact = notInContactListGroup.findContact(id)
        }
        if (sourceContact == null) {
            sourceContact = ssContactList.createVolatileContact(id, isPrivateMessagingContact, displayName)
            if (isPrivateMessagingContact && id.hasResource()) {
                updateResources(sourceContact, false)
            }
        }
        return sourceContact
    }

    /**
     * Creates and returns a unresolved contact from the specified `address` and `persistentData`.
     *
     * @param address an identifier of the contact that we'll be creating.
     * @param persistentData a String returned Contact's getPersistentData() method during a previous run and that
     * has been persistently stored locally.
     * @param parentGroup the group where the unresolved contact is supposed to belong to.
     *
     * @return the unresolved `Contact` created from the specified `address` and `persistentData`
     */
    override fun createUnresolvedContact(address: String?, persistentData: String?, parentGroup: ContactGroup?): Contact? {
        require(parentGroup is ContactGroupJabberImpl
                || parentGroup is RootContactGroupJabberImpl) { "Argument is not an jabber contact group ($parentGroup)" }
        return try {
            ssContactList.createUnresolvedContact(parentGroup, JidCreate.from(address))
        } catch (e: XmppStringprepException) {
            throw IllegalArgumentException("Invalid JID", e)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid JID", e)
        }
    }

    /**
     * Creates and returns a unresolved contact from the specified `address` and `persistentData`.
     *
     * @param address an identifier of the contact that we'll be creating.
     * @param persistentData a String returned Contact's getPersistentData() method during a previous run and that
     * has been persistently stored locally.
     *
     * @return the unresolved `Contact` created from the specified `address` and `persistentData`
     */
    override fun createUnresolvedContact(address: String?, persistentData: String?): Contact? {
        return createUnresolvedContact(address, persistentData, getServerStoredContactListRoot())
    }

    /**
     * Creates and returns a unresolved contact group from the specified `address` and `persistentData`.
     *
     * @param groupUID an identifier, returned by ContactGroup's getGroupUID, that the protocol provider may
     * use in order to create the group.
     * @param persistentData a String returned ContactGroups's getPersistentData() method during a previous run and
     * that has been persistently stored locally.
     * @param parentGroup the group under which the new group is to be created or null if this is group directly
     * underneath the root.
     *
     * @return the unresolved `ContactGroup` created from the specified `uid` and `persistentData`
     */
    override fun createUnresolvedContactGroup(groupUID: String, persistentData: String?, parentGroup: ContactGroup?): ContactGroup {
        return ssContactList.createUnresolvedContactGroup(groupUID)
    }

    /**
     * Returns a reference to the contact with the specified ID in case we have a subscription for
     * it and null otherwise
     *
     * @param contactID a String identifier of the contact which we're seeking a reference of.
     *
     * @return a reference to the Contact with the specified `contactID` or null if we don't
     * have a subscription for the that identifier.
     */
    override fun findContactByID(contactID: String?): Contact? {
        return try {
            ssContactList.findContactById(JidCreate.from(contactID))
        } catch (e: XmppStringprepException) {
            Timber.e(e, "Could not parse contact into Jid: %s", contactID)
            null
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Could not parse contact into Jid: %s", contactID)
            null
        }
    }

    override fun findContactByJid(contactJid: Jid?): Contact? {
        return ssContactList.findContactById(contactJid)
    }

    /**
     * Returns the status message that was confirmed by the server
     *
     * @return the last status message that we have requested and the aim server has confirmed.
     */
    override fun getCurrentStatusMessage(): String? {
        return currentStatusMessage
    }

    /**
     * Returns the protocol specific contact instance representing the local user.
     *
     * @return the Contact (address, phone number, or uid) that the Provider implementation is communicating on behalf of.
     */
    override fun getLocalContact(): ContactJabberImpl? {
        if (localContact != null) return localContact

        val ourJID = mPPS!!.ourJID
        localContact = ContactJabberImpl(null, ssContactList, isPersistent = false, isResolved = true)
        localContact!!.setLocal(true)
        localContact!!.presenceStatus = currentStatus!!
        localContact!!.contactJid = ourJID.asBareJid()
        val rs = localContact!!.getResourcesMap()
        if (currentPresence != null) rs[ourJID] = createResource(currentPresence, ourJID, localContact!!)
        val presenceList = ssContactList.getPresences(ourJID.asBareJid())
        for (presence in presenceList) {
            val fullJid = presence.from.asFullJidIfPossible()
            if (fullJid != null) // NPE from field
                rs[fullJid] = createResource(presence, fullJid, localContact!!)
        }
        return localContact
    }

    /**
     * Creates ContactResource from the presence, full jid and contact.
     *
     * @param presence the presence object.
     * @param fullJid the full jid for the resource.
     * @param contact the contact.
     *
     * @return the newly created resource.
     */
    private fun createResource(presence: Presence?, fullJid: FullJid, contact: Contact): ContactResourceJabberImpl {
        return ContactResourceJabberImpl(fullJid, contact, jabberStatusToPresenceStatus(presence, mPPS!!),
                presence!!.priority, mobileIndicator.isMobileResource(fullJid))
    }

    /**
     * Clear resources used for local contact and before that update its resources in order to fire the needed events.
     */
    private fun clearLocalContactResources() {
        if (localContact != null) {
            removeResource(localContact, localContact!!.contactJid!!.asFullJidIfPossible())
        }
        currentPresence = null
        localContact = null
    }

    /**
     * Returns a PresenceStatus instance representing the state this provider is currently in.
     *
     * @return the PresenceStatus last published by this provider.
     */
    override fun getPresenceStatus(): PresenceStatus? {
        return currentStatus
    }

    /**
     * @param status the JabberStatusEnum
     *
     * @return JabberPresenceStatus#getStatus(String statusName)
     */
    override fun getPresenceStatus(status: String?): PresenceStatus {
        return mPPS!!.jabberStatusEnum!!.getStatus(status!!)
    }

    /**
     * Returns the root group of the server stored contact list.
     *
     * @return the root ContactGroup for the ContactList stored by this service.
     */
    override fun getServerStoredContactListRoot(): ContactGroup {
        return ssContactList.rootGroup
    }

    /**
     * Returns the list of PresenceStatus objects that a user of this service may request the provider to enter.
     *
     * @return PresenceStatus ListArray containing "selectable" status instances.
     */
    override fun getSupportedStatusSet(): List<PresenceStatus?> {
        return mPPS!!.jabberStatusEnum!!.getSupportedStatusSet()
    }

    /**
     * Checks if the contact address is associated with private messaging contact or not.
     *
     * @param contactJid the address of the contact.
     *
     * @return `true` the contact address is associated with private messaging contact and `false` if not.
     */
    fun isPrivateMessagingContact(contactJid: Jid?): Boolean {
        return ssContactList.isPrivateMessagingContact(contactJid)
    }

    /**
     * Removes the specified contact from its current parent and places it under `newParent`.
     *
     * @param contactToMove the `Contact` to move
     * @param newParent the `ContactGroup` where `Contact` would be placed.
     */
    @Throws(OperationFailedException::class)
    override fun moveContactToGroup(contactToMove: Contact?, newParent: ContactGroup?) {
        assertConnected()
        require(contactToMove is ContactJabberImpl) { "The specified contact is not an jabber contact. $contactToMove" }
        require(newParent is AbstractContactGroupJabberImpl) { "The specified group is not an jabber contact group. $newParent" }
        ssContactList.moveContact(contactToMove, newParent)
    }

    /**
     * Publish the provider has entered into a state corresponding to the specified parameters.
     *
     * @param status the PresenceStatus as returned by getSupportedStatusSet
     * @param statusMessage the message that should be set as the reason to enter that status
     *
     * @throws IllegalArgumentException if the status requested is not a valid PresenceStatus supported by this provider.
     * @throws IllegalStateException if the provider is not currently registered.
     */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun publishPresenceStatus(status: PresenceStatus?, statusMessage: String?) {
        assertConnected()
        val jabberStatusEnum = mPPS!!.jabberStatusEnum
        val supportedStatuses: List<PresenceStatus?> = jabberStatusEnum!!.getSupportedStatusSet()
        val isValidStatus = supportedStatuses.contains(status)
        require(isValidStatus) { status.toString() + " is not a valid Jabber status" }

        // if we got publish presence, and we are still in a process of initializing the roster,
        // just save the status and we will dispatch it when we are ready with the roster as
        // sending initial presence is recommended to be done after requesting the roster, but we
        // want to also dispatch it
        synchronized(ssContactList.rosterInitLock) {
            if (!ssContactList.isRosterInitialized) {
                // store it
                ssContactList.setInitialStatus(status)
                ssContactList.setInitialStatusMessage(statusMessage)
                Timber.i("Smack: In roster fetching-hold <presence:available/>")
                return
            }
        }
        if (status!! == jabberStatusEnum.getStatus(JabberStatusEnum.OFFLINE)) {
            mPPS.unregister()
            clearLocalContactResources()
        } else {
            val connection = mPPS.connection
            val presenceBuilder = connection!!.stanzaFactory.buildPresenceStanza()
                    .ofType(Presence.Type.available)
                    .setMode(presenceStatusToJabberMode(status))
                    .setPriority(getPriorityForPresenceStatus(status.statusName))

            // On the phone or in meeting has a special status which is different from custom status message
            when (status) {
                jabberStatusEnum.getStatus(JabberStatusEnum.ON_THE_PHONE) -> {
                    presenceBuilder.status = JabberStatusEnum.ON_THE_PHONE
                }
                jabberStatusEnum.getStatus(JabberStatusEnum.IN_A_MEETING) -> {
                    presenceBuilder.status = JabberStatusEnum.IN_A_MEETING
                }
                else -> presenceBuilder.status = statusMessage
            }

            currentPresence = presenceBuilder.build()
            try {
                connection.sendStanza(currentPresence)
            } catch (e: NotConnectedException) {
                Timber.e(e, "Could not send new presence status")
            } catch (e: InterruptedException) {
                Timber.e(e, "Could not send new presence status")
            }
            if (localContact != null) updateResource(localContact!!, mPPS.ourJID, currentPresence!!)
        }
        fireProviderStatusChangeEvent(currentStatus, status)
        val oldStatusMessage = getCurrentStatusMessage()
        if (oldStatusMessage != statusMessage) {
            currentStatusMessage = statusMessage
            fireProviderStatusMessageChangeEvent(oldStatusMessage, getCurrentStatusMessage())
        }
    }

    /**
     * Gets the `PresenceStatus` of a contact with a specific `String` identifier.
     *
     * @param contactJid the jid of the contact whose status we're interested in.
     *
     * @return the `PresenceStatus` of the contact with the specified `contactIdentifier`
     * @throws IllegalArgumentException if the specified `contactIdentifier` does not identify a contact
     * known to the underlying protocol provider
     * @throws IllegalStateException if the underlying protocol provider is not registered/signed on a public service
     */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun queryContactStatus(contactJid: BareJid): PresenceStatus {
        /*
         * As stated by the javadoc, IllegalStateException signals that the ProtocolProviderService is not registered.
         */
        assertConnected()
        val xmppConnection = mPPS!!.connection
                ?: throw IllegalArgumentException("The provider/account must be signed on in order"
                        + " to query the status of a contact in its roster")
        val roster = Roster.getInstanceFor(xmppConnection)
        val presence = roster.getPresence(contactJid)
        return jabberStatusToPresenceStatus(presence, mPPS)
    }

    /**
     * Removes the specified group from the server stored contact list.
     *
     * @param group the group to remove.
     */
    @Throws(OperationFailedException::class)
    override fun removeServerStoredContactGroup(group: ContactGroup?) {
        assertConnected()
        require(group is ContactGroupJabberImpl) { "The specified group is not an jabber contact group: $group" }
        ssContactList.removeGroup(group)
    }

    /**
     * Removes the specified group change listener so that it won't receive any further events.
     *
     * @param listener the ServerStoredGroupChangeListener to remove
     */
    override fun removeServerStoredGroupChangeListener(listener: ServerStoredGroupListener) {
        ssContactList.removeGroupListener(listener)
    }

    /**
     * Renames the specified group from the server stored contact list.
     *
     * @param group the group to rename.
     * @param newName the new name of the group.
     */
    override fun renameServerStoredContactGroup(group: ContactGroup?, newName: String?) {
        assertConnected()
        require(group is ContactGroupJabberImpl) { "The specified group is not an jabber contact group: $group" }
        ssContactList.renameGroup(group, newName)
    }

    /**
     * Handler for incoming authorization requests.
     *
     * @param handler an instance of an AuthorizationHandler for authorization requests coming from other
     * users requesting permission add us to their contact list.
     */
    override fun setAuthorizationHandler(handler: AuthorizationHandler?) {
        // subscriptionPacketListener is null when authenticated via ReconnectionManager, just
        // ignore as handler should have been setup during normal authentication
        if (handleSubscribeEvent) setHandler(handler)
    }

    /**
     * Persistently adds a subscription for the presence status of the contact corresponding to the
     * specified contactIdentifier and indicates that it should be added to the specified group of
     * the server stored contact list.
     *
     * @param parent the parent group of the server stored contact list where the contact should be added.
     * @param contactIdentifier the contact whose status updates we are subscribing for.
     *
     * @throws IllegalArgumentException if `contact` or `parent` are not a contact known to the
     * underlying protocol provider.
     * @throws IllegalStateException if the underlying protocol provider is not registered/signed on a public service.
     * @throws OperationFailedException with code NETWORK_FAILURE if subscribing fails due to errors experienced
     * during network communication
     */
    @Throws(IllegalArgumentException::class, IllegalStateException::class, OperationFailedException::class)
    override fun subscribe(parent: ContactGroup?, contactIdentifier: String?) {
        assertConnected()
        require(parent is ContactGroupJabberImpl) { "Argument is not an jabber contact group (group = $parent)" }
        ssContactList.addContact(parent, contactIdentifier)
    }

    /**
     * Adds a subscription for the presence status of the contact corresponding to the specified contactIdentifier.
     *
     * @param contactIdentifier the identifier of the contact whose status updates we are subscribing for.
     * @param pps the owner of the contact to be added to RootGroup.
     *
     * @throws IllegalArgumentException if `contact` is not a contact known to the underlying protocol provider
     * @throws IllegalStateException if the underlying protocol provider is not registered/signed on a public service.
     * @throws OperationFailedException with code NETWORK_FAILURE if subscribing fails due to errors experienced
     * during network communication
     */
    @Throws(IllegalArgumentException::class, IllegalStateException::class, OperationFailedException::class)
    override fun subscribe(pps: ProtocolProviderService?, contactIdentifier: String?) {
        assertConnected()
        ssContactList.addContact(pps, contactIdentifier)
    }

    /**
     * Removes a subscription for the presence status of the specified contact.
     *
     * @param contact the contact whose status updates we are unsubscribing from.
     *
     * @throws IllegalArgumentException if `contact` is not a contact known to the underlying protocol provider
     * @throws IllegalStateException if the underlying protocol provider is not registered/signed on a public service.
     * @throws OperationFailedException with code NETWORK_FAILURE if unSubscribing fails due to errors experienced
     * during network communication
     */
    @Throws(IllegalArgumentException::class, IllegalStateException::class, OperationFailedException::class)
    override fun unsubscribe(contact: Contact?) {
        assertConnected()
        require(contact is ContactJabberImpl) { "Argument is not an jabber contact (contact = $contact)" }
        ssContactList.removeContact(contact)
    }

    /**
     * Utility method throwing an exception if the stack is not properly initialized.
     *
     * @throws IllegalStateException if the underlying stack is not registered and initialized.
     */
    @Throws(IllegalStateException::class)
    fun assertConnected() {
        checkNotNull(mPPS) {
            "The provider must be non-null and signed on the " +
                    "Jabber service before able to communicate."
        }
        if (!mPPS.isRegistered) {
            // if we are not registered but the current status is online change the current status
            if (currentStatus != null && currentStatus!!.isOnline) {
                fireProviderStatusChangeEvent(currentStatus,
                        mPPS.jabberStatusEnum!!.getStatus(JabberStatusEnum.OFFLINE))
            }
            throw IllegalStateException("The provider must be signed on the Jabber service " +
                    "before being able to communicate.")
        }
    }

    /**
     * Fires provider status changes.
     *
     * @param oldValue old status
     * @param newValue new status
     */
    public override fun fireProviderStatusChangeEvent(oldValue: PresenceStatus?, newValue: PresenceStatus?) {
        if (oldValue!! != newValue) {
            currentStatus = newValue!!
            super.fireProviderStatusChangeEvent(oldValue, newValue)

            // Do not update contacts status if pps is in reconnecting state
            if (updateAllStatus) {
                val offlineStatus = mPPS!!.jabberStatusEnum!!.getStatus(JabberStatusEnum.OFFLINE)

                // cmeng: The passing jid is bareJid - not used when contact is offline, only contact is used.
                if (newValue == offlineStatus) {
                    // send event notifications saying that all our buddies are offline. The
                    // protocol does not implement top level buddies nor subgroups for top level
                    // groups so a simple nested loop would be enough.
                    val groupsIter = getServerStoredContactListRoot().subgroups()
                    while (groupsIter!!.hasNext()) {
                        val group = groupsIter.next()
                        val contactsIter = group!!.contacts()
                        while (contactsIter!!.hasNext()) {
                            val contact = contactsIter.next() as ContactJabberImpl
                            val jid = contact.contactJid!!
                            updateContactStatus(contact, jid, offlineStatus)
                        }
                    }
                    // do the same for all contacts in the root group
                    val contactsIter = getServerStoredContactListRoot().contacts()
                    while (contactsIter!!.hasNext()) {
                        val contact = contactsIter.next() as ContactJabberImpl
                        val jid = contact.contactJid!!
                        updateContactStatus(contact, jid, offlineStatus)
                    }
                }
            }
        }
    }

    /**
     * Sets the display name for `contact` to be `newName`.
     *
     * @param contact the `Contact` that we are renaming
     * @param newName a `String` containing the new display name for `metaContact`.
     *
     * @throws IllegalArgumentException if `contact` is not an instance that belongs to the underlying implementation.
     */
    @Throws(IllegalArgumentException::class)
    override fun setDisplayName(contact: Contact?, newName: String?) {
        assertConnected()
        require(contact is ContactJabberImpl) { "Argument is not an jabber contact (contact = $contact)" }
        val entry = contact.getSourceEntry()
        if (entry != null) try {
            entry.name = newName
        } catch (e: NotConnectedException) {
            throw IllegalArgumentException("Could not update name", e)
        } catch (e: NoResponseException) {
            throw IllegalArgumentException("Could not update name", e)
        } catch (e: XMPPErrorException) {
            throw IllegalArgumentException("Could not update name", e)
        } catch (e: InterruptedException) {
            throw IllegalArgumentException("Could not update name", e)
        }
    }

    /**
     * The listener that will tell us when we're registered to server and is ready to
     * init and accept the rosterListener.
     * cmeng: This implementation supports Roster Versioning / RosterStore for reduced
     * bandwidth requirements. See ProtocolProviderServiceJabberImpl#initRosterStore
     */
    private inner class RegistrationStateListener : RegistrationStateChangeListener {
        /**
         * The method is called by a ProtocolProvider implementation whenever a change in the
         * registration state of the corresponding provider had occurred.
         *
         * @param evt ProviderStatusChangeEvent the event describing the status change.
         */
        override fun registrationStateChanged(evt: RegistrationStateChangeEvent) {
            val eventNew = evt.getNewState()
            val xmppConnection = mPPS!!.connection
            if (eventNew === RegistrationState.REGISTERING) {
                // contactChangesListener will be used to store presence events till roster is initialized
                mContactChangesListener = ContactChangesListener()
                mContactChangesListener!!.storeEvents()
            } else if (eventNew === RegistrationState.REGISTERED) {
                /*
                 * Add a RosterLoaded listener as this will indicate when the roster is
                 * received or loaded from RosterStore (upon authenticated). We are then ready
                 * to dispatch the contact list. Note the actual RosterListener used is added
                 * and active just after the RosterLoadedListener is triggered.
                 *
                 * setup to init ssContactList upon receiving the rosterLoaded event
                 */
                mRoster = Roster.getInstanceFor(xmppConnection)!!
                mRoster!!.addRosterLoadedListener(ServerStoredListInit())

                // Adds subscription listeners only when user is authenticated
                if (!handleSubscribeEvent) {
                    mRoster!!.addSubscribeListener(this@OperationSetPersistentPresenceJabberImpl)
                    mRoster!!.addPresenceEventListener(this@OperationSetPersistentPresenceJabberImpl)
                    handleSubscribeEvent = true
                    Timber.log(TimberLog.FINER, "SubscribeListener and PresenceEventListener added")
                }
                if (vCardAvatarManager == null) {
                    /* Add avatar change listener to handle contacts' avatar changes via XEP-0153*/
                    vCardAvatarManager = VCardAvatarManager.getInstanceFor(xmppConnection)
                    vCardAvatarManager!!.addVCardAvatarChangeListener(this@OperationSetPersistentPresenceJabberImpl)
                }
                if (userAvatarManager == null) {
                    /* Add avatar change listener to handle contacts' avatar changes via XEP-0084 */
                    userAvatarManager = UserAvatarManager.getInstanceFor(xmppConnection)!!
                    userAvatarManager!!.addAvatarListener(this@OperationSetPersistentPresenceJabberImpl)
                }

                // Do the following if no from resumed (do once only)
                if (evt.getReasonCode() != RegistrationStateChangeEvent.REASON_RESUMED) {
                    /*
                     * Immediately Upon account registration, load the account VCard info and cache the
                     * retrieved info in retrievedDetails for later use (avoid duplicate vcard.load().
                     * The avatar Hash will also be updated if account photo is defined to support
                     * XEP-00135 VCard Avatar <vcard-temp:x:update/> protocol
                     */
                    val accountInfoOpSet = mPPS.getOperationSet(OperationSetServerStoredAccountInfo::class.java)
                    if (infoRetrieveOnStart && accountInfoOpSet != null) {
                        accountInfoOpSet.getAllAvailableDetails()
                    }
                }
            } else if (eventNew === RegistrationState.RECONNECTING) {
                // since we are disconnected, need to change our own status. Leave the contacts'
                // status untouched as we will not be informed when we resumed.
                val oldStatus = currentStatus
                val currentStatus = mPPS.jabberStatusEnum!!.getStatus(JabberStatusEnum.OFFLINE)
                updateAllStatus = false
                fireProviderStatusChangeEvent(oldStatus, currentStatus)
            } else if (eventNew === RegistrationState.UNREGISTERED || eventNew === RegistrationState.AUTHENTICATION_FAILED || eventNew === RegistrationState.CONNECTION_FAILED) {
                // since we are disconnected, we won't receive any further status updates so we need to change by
                // ourselves our own status as well as set to offline all contacts in our contact list that were online
                val oldStatus = currentStatus
                val currentStatus = mPPS.jabberStatusEnum!!.getStatus(JabberStatusEnum.OFFLINE)
                clearLocalContactResources()
                val accountInfoOpSet = mPPS.getOperationSet(OperationSetServerStoredAccountInfo::class.java)
                accountInfoOpSet?.clearDetails()
                updateAllStatus = true
                fireProviderStatusChangeEvent(oldStatus, currentStatus)
                ssContactList.cleanup()
                if (xmppConnection != null) {
                    // Remove all subscription listeners upon de-registration
                    if (mRoster != null) {
                        mRoster!!.removeSubscribeListener(this@OperationSetPersistentPresenceJabberImpl)
                        mRoster!!.removePresenceEventListener(this@OperationSetPersistentPresenceJabberImpl)
                        mRoster!!.removeRosterListener(mContactChangesListener)
                        mRoster = null
                        Timber.i("SubscribeListener and PresenceEventListener removed")
                    }

                    // vCardAvatarManager can be null for unRegistered account
                    if (vCardAvatarManager != null) {
                        vCardAvatarManager!!.removeVCardAvatarChangeListener(this@OperationSetPersistentPresenceJabberImpl)
                        userAvatarManager!!.removeAvatarListener(this@OperationSetPersistentPresenceJabberImpl)
                    }
                }
                handleSubscribeEvent = false
                mContactChangesListener = null
                vCardAvatarManager = null
                userAvatarManager = null
            }
        }
    }

    /**
     * Updates the resources for the contact.
     *
     * @param contact the contact which resources to update.
     * @param removeUnavailable whether to remove unavailable resources.
     *
     * @return whether resource has been updated
     */
    private fun updateResources(contact: ContactJabberImpl?, removeUnavailable: Boolean): Boolean {
        if (!contact!!.isResolved() || (contact is VolatileContactJabberImpl
                        && contact.isPrivateMessagingContact)) return false
        var eventFired = false
        val resources = contact.getResourcesMap()

        // Do not obtain getRoster if we are not connected, or new Roster will be created, all the resources
        // that will be returned will be unavailable. As we are not connected if set remove all resources
        val xmppConnection = mPPS!!.connection
        if (xmppConnection == null || !xmppConnection.isConnected) {
            if (removeUnavailable) {
                val iter = resources.entries.iterator()
                while (iter.hasNext()) {
                    val (_, value) = iter.next()
                    iter.remove()
                    contact.fireContactResourceEvent(ContactResourceEvent(contact,
                            value!!, ContactResourceEvent.RESOURCE_REMOVED))
                    eventFired = true
                }
            }
            return eventFired
        }
        val presences = mRoster!!.getPresences(contact.contactJid!!.asBareJid())
        // Choose the resource which has the highest priority AND supports Jingle, if we have two
        // resources with same priority take the most available.
        for (presence in presences) {
            eventFired = updateResource(contact, null, presence) || eventFired
        }
        if (!removeUnavailable) return eventFired
        val resourceKeys = resources.keys
        for (fullJid in resourceKeys) {
            if (!mRoster!!.getPresenceResource(fullJid).isAvailable) {
                eventFired = removeResource(contact, fullJid) || eventFired
            }
        }
        return eventFired
    }

    /**
     * Update the resources for the contact for the received presence.
     *
     * @param contact the contact which resources to update.
     * @param fullJid_ the full jid to use, if null will use those from the presence stanza
     * @param presence the presence stanza to use to get info.
     *
     * @return whether resource has been updated
     */
    private fun updateResource(contact: ContactJabberImpl, fullJid_: FullJid?, presence: Presence): Boolean {
        var fullJid = fullJid_
        if (fullJid == null) fullJid = presence.from.asFullJidIfPossible()
        if (fullJid == null) return false
        val resource = fullJid.resourceOrNull
        if (resource != null && resource.length > 0) {
            val resources = contact.getResourcesMap()
            var contactResource = resources[fullJid]
            val newPresenceStatus = jabberStatusToPresenceStatus(presence, mPPS!!)
            if (contactResource == null) {
                contactResource = createResource(presence, fullJid, contact)
                resources[fullJid] = contactResource
                contact.fireContactResourceEvent(ContactResourceEvent(contact, contactResource,
                        ContactResourceEvent.RESOURCE_ADDED))
                return true
            } else {
                val oldIndicator = contactResource.isMobile
                val newIndicator = mobileIndicator.isMobileResource(fullJid)
                val oldPriority = contactResource.priority

                // update mobile indicator, as cabs maybe added after creating the resource for the contact
                contactResource.isMobile = newIndicator
                contactResource.priority = presence.priority
                if (oldPriority != contactResource.priority) {
                    // priority has been updated so update and the mobile indicator before firing an event
                    mobileIndicator.resourcesUpdated(contact)
                }
                if (contactResource.presenceStatus.status != newPresenceStatus.status || oldIndicator != newIndicator || oldPriority != contactResource.priority) {
                    contactResource.presenceStatus = newPresenceStatus
                    contact.fireContactResourceEvent(ContactResourceEvent(contact,
                            contactResource, ContactResourceEvent.RESOURCE_MODIFIED))
                    return true
                }
            }
        }
        return false
    }

    /**
     * Removes the resource indicated by the fullJid from the list with resources for the contact.
     *
     * @param contact from its list of resources to remove
     * @param fullJid the full jid.
     *
     * @return whether resource has been updated
     */
    private fun removeResource(contact: ContactJabberImpl?, fullJid: FullJid?): Boolean {
        val resources = contact!!.getResourcesMap()
        if (fullJid != null && resources.containsKey(fullJid)) {
            val removedResource: ContactResource? = resources.remove(fullJid)
            contact.fireContactResourceEvent(ContactResourceEvent(contact, removedResource!!,
                    ContactResourceEvent.RESOURCE_REMOVED))
            return true
        }
        return false
    }

    /**
     * Fires the status change, respecting resource priorities.
     *
     * @param presence the presence changed.
     */
    fun firePresenceStatusChanged(presence: Presence) {
        if (mContactChangesListener != null) mContactChangesListener!!.firePresenceStatusChanged(presence)
    }

    /**
     * Updates contact status and its resources, fires PresenceStatusChange events.
     *
     * @param contact the contact which presence to update if needed.
     * @param jid the contact FullJid.
     * @param newStatus the new status.
     */
    private fun updateContactStatus(contact: ContactJabberImpl?, jid: Jid, newStatus: PresenceStatus) {
        // When status changes this may be related to a change in the available resources.
        val oldMobileIndicator = contact!!.isMobile
        val resourceUpdated = updateResources(contact, true)
        mobileIndicator.resourcesUpdated(contact)
        val oldStatus = contact.presenceStatus

        // when old and new status are the same do nothing no change
        if (oldStatus == newStatus && oldMobileIndicator == contact.isMobile) {
            return
        }
        contact.presenceStatus = newStatus
        Timber.d("Dispatching contact status update for %s: %s", jid, newStatus.statusName)
        fireContactPresenceStatusChangeEvent(contact, jid, contact.parentContactGroup!!,
                oldStatus, newStatus, resourceUpdated)
    }

    /**
     * Manage changes of statuses by resource.
     */
    inner class ContactChangesListener : AbstractRosterListener() {
        /**
         * Whether listener is currently storing presence events.
         */
        /**
         * Store events for later processing, used when initializing contactList.
         */
        var isStoringPresenceEvents = false
            private set

        /**
         * Stored presences for later processing.
         */
        private var storedPresences: CopyOnWriteArrayList<Presence>? = null

        /**
         * Map containing all statuses for a userJid.
         */
        private val statuses: MutableMap<Jid?, TreeSet<Presence>> = Hashtable()

        /**
         * Received on resource status change.
         *
         * @param presence presence that has changed
         */
        override fun presenceChanged(presence: Presence) {
            firePresenceStatusChanged(presence)
        }

        /*
         * Adds presence stanza to the list.
         *
         * @param presence presence stanza
         */
        fun addPresenceEvent(presence: Presence) {
            storedPresences!!.add(presence)
        }

        /**
         * Initialize new storedPresences<Presence> and sets store events to true.
        </Presence> */
        fun storeEvents() {
            storedPresences = CopyOnWriteArrayList()
            isStoringPresenceEvents = true
        }

        /**
         * Process stored presences.
         */
        fun processStoredEvents() {
            // Must not proceed if false as storedPresences has already cleared or not yet init?
            // FFR: NPE on synchronized (storedPresences)
            if (isStoringPresenceEvents) {
                isStoringPresenceEvents = false
                // ConcurrentModificationException from field
                synchronized(storedPresences!!) {
                    for (p in storedPresences!!) {
                        firePresenceStatusChanged(p)
                    }
                }
                storedPresences!!.clear()
                storedPresences = null
            }
        }

        /**
         * Fires the status change, respecting resource priorities.
         *
         * @param presence the presence changed.
         */
        fun firePresenceStatusChanged(presence: Presence) {
            /*
             * Smack block sending of presence update while roster loading is in progress.
             *
             * cmeng - just ignore and return to see if there is any side effect while process roster is in progress
             * seem to keep double copies and all unavailable triggered from roster - need to process??
             */
            if (isStoringPresenceEvents && storedPresences != null) {
                storedPresences!!.add(presence)
                return
            }
            try {
                var userJid: Jid? = presence.from.asBareJid()
                val mucOpSet = mPPS!!.getOperationSet(OperationSetMultiUserChat::class.java)
                if (userJid != null && mucOpSet != null) {
                    val chatRooms = mucOpSet.getCurrentlyJoinedChatRooms()
                    for (chatRoom in chatRooms!!) {
                        if (userJid!!.equals(chatRoom!!.getIdentifier())) {
                            userJid = presence.from
                            break
                        }
                    }
                }
                Timber.d("Smack presence update for: %s - %s", presence.from, presence.type)

                // all contact statuses that are received from all its resources ordered by priority (higher first)
                // and those with equal priorities order with the one that is most connected as first
                var userStats = statuses[userJid]
                if (userStats == null) {
                    userStats = TreeSet { o1: Presence, o2: Presence ->
                        var res = o2.priority - o1.priority

                        // if statuses are with same priorities return which one is more
                        // available counts the JabberStatusEnum order
                        if (res == 0) {
                            res = (jabberStatusToPresenceStatus(o2, mPPS).status
                                    - jabberStatusToPresenceStatus(o1, mPPS).status)
                            // We have run out of "logical" ways to order the presences inside
                            // the TreeSet. We have make sure we are consistent with equals.
                            // We do this by comparing the unique resource names. If this
                            // evaluates to 0 again, then we can safely assume this presence
                            // object represents the same resource and by that the same client.
                            if (res == 0) {
                                res = o1.from.compareTo(o2.from)
                            }
                        }
                        res
                    }
                    statuses[userJid] = userStats
                } else {
                    // remove the status for this resource if we are online we will update its value with the new status
                    val resource = presence.from.resourceOrEmpty
                    val iter = userStats.iterator()
                    while (iter.hasNext()) {
                        val p = iter.next()
                        if (resource == p.from.resourceOrEmpty) iter.remove()
                    }
                }
                if (jabberStatusToPresenceStatus(presence, mPPS) != mPPS.jabberStatusEnum!!.getStatus(JabberStatusEnum.OFFLINE)) {
                    userStats.add(presence)
                }
                val currentPresence: Presence
                if (userStats.size == 0) {
                    currentPresence = presence
                    /*
                     * We no longer have statuses for userJid so it doesn't make sense to retain
                     * (1) the TreeSet and
                     * (2) its slot in the statuses Map.
                     */
                    statuses.remove(userJid)
                } else currentPresence = userStats.first()
                val sourceContact = ssContactList.findContactById(userJid)
                if (sourceContact == null) {
                    Timber.w("Ignore own or no source contact found for id = %s", userJid)
                    return
                }

                // statuses may be the same and only change in status message
                sourceContact.statusMessage = currentPresence.status
                updateContactStatus(sourceContact, presence.from, jabberStatusToPresenceStatus(currentPresence, mPPS))
            } catch (ex: IllegalStateException) {
                Timber.e(ex, "Failed changing status")
            } catch (ex: IllegalArgumentException) {
                Timber.e(ex, "Failed changing status")
            }
        }
    }
    //================= Presence Subscription Handlers =========================
    /**
     * The authorization handler.
     */
    private var handler: AuthorizationHandler? = null

    /**
     * List of early subscriptions.
     */
    private val earlySubscriptions: MutableMap<Jid, String?> = HashMap()

    /**
     * Creates the OperationSet.
     *
     * pps an instance of the pps prior to registration i.e. connection == null
     * infoRetriever retrieve contact information.
     */
    init {
        ssContactList = ServerStoredContactListJabberImpl(this, pps, mInfoRetriever)
        mobileIndicator = MobileIndicator(pps, ssContactList)
        currentStatus = pps.jabberStatusEnum!!.getStatus(JabberStatusEnum.OFFLINE)
        initializePriorities()
        pps.addRegistrationStateChangeListener(RegistrationStateListener())
    }

    /**
     * Adds auth handler.
     *
     * @param handler Authorization handler with UI dialog
     */
    @Synchronized
    private fun setHandler(handler: AuthorizationHandler?) {
        this.handler = handler
        handleEarlySubscribeReceived()
    }

    /**
     * Handles early presence subscribe that were received.
     */
    private fun handleEarlySubscribeReceived() {
        for (from in earlySubscriptions.keys) {
            handleSubscribeReceived(from, earlySubscriptions[from])
        }
        earlySubscriptions.clear()
    }

    /**
     * Handles the received presence subscribe: run waiting for user response in different thread as this seems
     * to block the stanza dispatch thread and we don't receive anything till we unblock it
     *
     * @param fromJid sender in bareJid
     * @param displayName sender nickName for display in contact list
     */
    private fun handleSubscribeReceived(fromJid: Jid, displayName: String?) {
        Thread(Runnable {
            Timber.i("%s wants to add you to its contact list", fromJid)

            // buddy wants to add you to its roster contact
            var srcContact = ssContactList.findContactById(fromJid)
            var responsePresenceType: Presence.Type? = null
            if (srcContact == null) {
                srcContact = createVolatileContact(fromJid, displayName)
            } else {
                if (srcContact.isPersistent) {
                    responsePresenceType = Presence.Type.subscribed
                    Timber.i("Auto accept for persistent contact: %s", fromJid)
                }
            }
            if (responsePresenceType == null) {
                val req = AuthorizationRequest()
                val response = handler!!.processAuthorisationRequest(req, srcContact)
                if (response != null) {
                    if (response.getResponseCode() == AuthorizationResponse.ACCEPT) {
                        responsePresenceType = Presence.Type.subscribed
                        // return request for presence subscription
                        try {
                            RosterUtil.askForSubscriptionIfRequired(mRoster, fromJid.asBareJid())
                        } catch (e: NotConnectedException) {
                            Timber.e(e, "Return presence subscription request failed")
                        } catch (e: InterruptedException) {
                            Timber.e(e, "Return presence subscription request failed")
                        } catch (e: NotLoggedInException) {
                            e.printStackTrace()
                        }
                        Timber.i("Sending Accepted Subscription")
                    } else if (response.getResponseCode() == AuthorizationResponse.REJECT) {
                        responsePresenceType = Presence.Type.unsubscribed
                        Timber.i("Sending Rejected Subscription")
                    }
                }
            }

            // subscription ignored
            if (responsePresenceType == null) return@Runnable
            val connection = mPPS!!.connection
            val responsePacket = connection!!.stanzaFactory.buildPresenceStanza()
                    .ofType(responsePresenceType).build()
            responsePacket.to = fromJid
            try {
                connection.sendStanza(responsePacket)
            } catch (e: NotConnectedException) {
                Timber.e(e, "Sending presence subscription response failed.")
            } catch (e: InterruptedException) {
                Timber.e(e, "Sending presence subscription response failed.")
            }
        }).start()
    }

    /**
     * Handle incoming presence subscription request; run on a different thread if manual approval to avoid blocking smack.
     *
     * @param from the JID requesting the subscription.
     * @param subscribeRequest the presence stanza used for the request.
     *
     * @return an answer to the request for smack process, or `null`
     */
    override fun processSubscribe(from: Jid, subscribeRequest: Presence): SubscribeAnswer? {
        val fromJid = subscribeRequest.from
        /*
         * Approved presence subscription request if auto accept-all option is selected OR
         * if the contact is already persistent i.e. exist in DB
         */
        val srcContact = ssContactList.findContactById(fromJid)
        if (isPresenceSubscribeAuto() || srcContact != null && srcContact.isPersistent) {
            Timber.i("Approve and return request if required for contact: %s", fromJid)
            return SubscribeAnswer.ApproveAndAlsoRequestIfRequired
        }
        var displayName: String? = null
        // For 4.4.3-master (20200416): subscribeRequest.getExtension(Nick.class); => IllegalArgumentException
        val nickExt = subscribeRequest.getExtension(Nick.QNAME) as Nick?
        if (nickExt != null) displayName = nickExt.name
        Timber.d("Subscription authorization request from: %s", fromJid)
        synchronized(this) {
            // keep the request for later process when handler becomes ready
            if (handler == null) {
                earlySubscriptions.put(fromJid, displayName)
            } else {
                handleSubscribeReceived(fromJid, displayName)
            }
        }
        // Request smack roster to leave handling of the presence subscription request to user for manual approval
        return null
    }

    /**
     * cmeng (20190810) - Handler another instance user presence events return from smack
     * smack callback for all presenceAvailable for all entities (users login and contacts).
     *
     * @param address FullJid of own or the buddy subscribe to (user)
     * @param presence presence with available / unavailable state (from presenceUnavailable)
     */
    override fun presenceAvailable(address: FullJid?, presence: Presence) {
        // Keep a copy in storedPresences for later processing if isStoringPresenceEvents()
        if (mContactChangesListener != null && mContactChangesListener!!.isStoringPresenceEvents) {
            mContactChangesListener!!.addPresenceEvent(presence)
        }
        if (localContact == null) localContact = getLocalContact()

        // Update resource if receive from instances of user presence and localContact is not null
        if (localContact != null && address != null) {
            val ourJid = mPPS!!.ourJID // Received NPE from FFR
            if (ourJid != null && ourJid.asBareJid().isParentOf(address)) {
                // Timber.d("Smack presence update own instance %s %s: %s", userJid, address, localContact);
                updateResource(localContact!!, null, presence)
            }
        }
    }

    override fun presenceUnavailable(address: FullJid, presence: Presence) {
        presenceAvailable(address, presence)
    }

    override fun presenceError(address: Jid, errorPresence: Presence) {}

    /**
     * Buddy has approved the presence subscription request
     *
     * @param address FullJid of the the buddy subscribe to
     * @param subscribedPresence presence with subscribed state i.e. approved
     */
    override fun presenceSubscribed(address: BareJid, subscribedPresence: Presence) {
        val fromID = subscribedPresence.from
        if (handler == null) {
            Timber.w("No AuthorizationHandler to handle subscribed for %s", fromID)
            return
        }
        Timber.i("Smack presence subscription accepted by: %s", address)
        val contact = ssContactList.findContactById(fromID)
        val response = AuthorizationResponse(AuthorizationResponse.ACCEPT, "")
        handler!!.processAuthorizationResponse(response, contact!!)
    }

    /**
     * Buddy acknowledge the presence unsubscribed reply
     *
     * @param address FullJid of the the buddy whom was subscribed to
     * @param unsubscribedPresence presence with unsubscribed state i.e. removed
     */
    override fun presenceUnsubscribed(address: BareJid, unsubscribedPresence: Presence) {
        val fromID = unsubscribedPresence.from
        Timber.i("Smack presence subscription rejected by: %s", address)
        if (handler == null) {
            Timber.w("No unsubscribed Authorization Handler for %s", address)
            return
        }
        val contact = ssContactList.findContactById(fromID)
        if (contact != null) {
            val response = AuthorizationResponse(AuthorizationResponse.REJECT, "")
            handler!!.processAuthorizationResponse(response, contact)
            try {
                ssContactList.removeContact(contact)
            } catch (e: OperationFailedException) {
                Timber.e("Cannot remove contact that is unsubscribed.")
            }
        }
    }
    //================= End of Presence Subscription Handlers =========================
    /**
     * Runnable that resolves local contact list against the server side roster. This thread is the
     * one which will call getRoster for the first time. The thread wait until the roster
     * is loaded by the Smack Roster class
     */
    private inner class ServerStoredListInit : Runnable, RosterLoadedListener {
        override fun run() {
            // we are already being notified lets remove us from the rosterLoaded listener
            mRoster!!.removeRosterLoadedListener(this)

            // init the presenceChangeLister, RosterChangeLister and update contact list status
            mRoster!!.addRosterListener(mContactChangesListener)
            ssContactList.init(mContactChangesListener!!)

            // as we have dispatched the contact list and Roster is ready lets start the jingle nodes discovery
            mPPS!!.startJingleNodesDiscovery()
        }

        /**
         * When rosterLoaded event is received we are ready to dispatch the contact list,
         * doing it in different thread to avoid blocking xmpp stanza receiving.
         *
         * @param roster the roster stanza
         */
        override fun onRosterLoaded(roster: Roster) {
            mRoster = roster
            Timber.i("Roster loaded completed at startup!")
            if (!ssContactList.isRosterInitialized) {
                Thread(this, javaClass.name).start()
            }
        }

        override fun onRosterLoadingFailed(exception: Exception) {
            Timber.w("Roster loading failed at startup!")
        }
    }
    //	/**
    //	 * Updates the presence extension to advertise a new photo SHA-1 hash corresponding to the new
    //	 * avatar given in parameter.
    //	 *
    //	 * @param imageBytes The new avatar set for this account.
    //	 */
    //	public void updateAccountPhotoPresenceExtension(byte[] imageBytes)
    //	{
    //		try {
    //			// If the image has changed, then updates the presence extension and send immediately a
    //			// presence stanza to advertise the photo update.
    //			if (vCardAvatarManager.updateVCardAvatarHash(imageBytes, true)) {
    //				this.publishPresenceStatus(currentStatus, currentStatusMessage);
    //			}
    //		}
    //		catch (OperationFailedException ex) {
    //			Timber.i(ex, "Can not send presence extension to broadcast photo update");
    //		}
    //	}
    /**
     * Event is fired when a contact change avatar via XEP-0153: vCard-Based Avatars protocol.
     *
     * onAvatarChange event is triggered if a change in the VCard Avatar is detected via
     * <present></present> in its update <x xmlns='vcard-temp:x:update'></x><photo></photo> element imageHash value.
     * A new SHA-1 avatar contained in the photo tag represents a new avatar for this contact.
     *
     * @param userID The contact of the sent <presence></presence> stanza.
     * @param avatarHash The new photo image Hash value contains ["" | "{avatarHash}].
     * avatarHash == "" indicates that the contact does not have avatar specified.
     * avatarHash can be used to retrieve photo image from cache if auto downloaded
     * @param vCardInfo The contact VCard info - can contain null.
     */
    override fun onAvatarChange(userID: Jid, avatarHash: String, vCardInfo: VCard?) {
        /*
         * Retrieves the contact ID that aTalk currently managed concerning the peer that has
         * send this presence stanza with avatar update.
         */
        val sourceContact = ssContactList.findContactById(userID) ?: return

        /*
         * If this contact is not yet in our contact list, then there is no need to manage this avatar update.
         */
        val currentAvatar = sourceContact.getImage(false)

        /*
         * If vCardInfo is not null, vCardAvatarManager has already loaded the new image;
         * we can just retrieve from the vCardInfo if any. Otherwise try to get it from cache or
         * persistent store before we download on our own .
         * Note: newAvatar will have byte[0] when avatarHash == ""
         *
         * @see VCardAvatarManager#getAvatarImageByHash(String)
         */
        var newAvatar: ByteArray?
        if (null != vCardInfo) {
            newAvatar = vCardInfo.avatar
        } else {
            /*
             * Try to fetch from the cache/persistent before we proceed to download on our own.
             * Download via {@link InfoRetriever#retrieveDetails(BareJid)} method as it may have
             * other updated VCard info of interest that we would like to update the contact's
             * retrieveDetails
             */
            newAvatar = VCardAvatarManager.getAvatarImageByHash(avatarHash)
            if (newAvatar == null) {
                val details: List<GenericDetail?>? = mInfoRetriever.retrieveDetails(userID.asBareJid())
                for (detail in details!!) {
                    if (detail is ImageDetail) {
                        newAvatar = detail.getBytes()
                        break
                    }
                }
            }
        }
        if (newAvatar == null) newAvatar = ByteArray(0)

        // Sets the new avatar image for the contact.
        sourceContact.image = newAvatar

        // Fires a property change event to update the contact list.
        fireContactPropertyChangeEvent(sourceContact,
                ContactPropertyChangeEvent.PROPERTY_IMAGE, currentAvatar, newAvatar)
    }

    /*
     * Event is fired when a contact change avatar via XEP-0084: User Avatar protocol.
     *
     * @param from the contact EntityBareJid who change his avatar
     * @param avatarId the new avatar id, may be null if the contact set no avatar
     * The new photo image Hash value contains ["" | "{avatarHash}].
     * avatarHash == "" indicates that the contact does not have avatar specified.
     * avatarHash can be used to retrieve photo image from cache if auto downloaded
     * @param avatarInfo the metadata info of the userAvatar, may be empty if the contact set no avatar
     */
    override fun onAvatarChange(from: EntityBareJid, avatarId: String, avatarInfo: List<AvatarMetadata.Info>) {
        /*
         * Retrieves the contact ID that aTalk currently managed concerning the peer that has
         * send this presence stanza with avatar update.
         */
        val sourceContact = ssContactList.findContactById(from) ?: return

        /*
         * If this contact is not yet in our contact list, then there is no need to manage this avatar update.
         */
        val currentAvatar = sourceContact.getImage(false)

        /*
         * Try to retrieve from the cache/persistent before we proceed to download on our own via
         * {@link UserAvatarManager#downloadAvatar(EntityBareJid, String, AvatarMetadata.Info)}
         */
        var newAvatar = AvatarManager.getAvatarImageByHash(avatarId)
        if (newAvatar == null) {
            val info = userAvatarManager!!.selectAvatar(avatarInfo)
            if (userAvatarManager!!.downloadAvatar(from, avatarId, info)) {
                newAvatar = AvatarManager.getAvatarImageByHash(avatarId)
            }
        }
        if (newAvatar == null) newAvatar = ByteArray(0)

        // Sets the new avatar image for the contact.
        sourceContact.image = newAvatar

        // Fires a property change event to update the contact list.
        fireContactPropertyChangeEvent(sourceContact,
                ContactPropertyChangeEvent.PROPERTY_IMAGE, currentAvatar, newAvatar)
    }

    /**
     * Initializes the map with priorities and statuses which we will use when changing statuses.
     */
    private fun initializePriorities() {
        try {
            resourcePriorityAvailable = mPPS!!.accountID
                    .getAccountPropertyString(ProtocolProviderFactory.RESOURCE_PRIORITY)!!.toInt()
        } catch (ex: NumberFormatException) {
            Timber.e(ex, "Wrong value for resource priority")
        }
        addDefaultValue(JabberStatusEnum.AWAY, -5)
        addDefaultValue(JabberStatusEnum.EXTENDED_AWAY, -10)
        addDefaultValue(JabberStatusEnum.ON_THE_PHONE, -15)
        addDefaultValue(JabberStatusEnum.IN_A_MEETING, -16)
        addDefaultValue(JabberStatusEnum.DO_NOT_DISTURB, -20)
        addDefaultValue(JabberStatusEnum.FREE_FOR_CHAT, +5)
    }

    /**
     * Checks for account property that can override this status. If missing use the shift value to
     * create the priority to use, make sure it is not zero or less than it.
     *
     * @param statusName the status to check/create priority
     * @param availableShift the difference from available resource value to use.
     */
    private fun addDefaultValue(statusName: String, availableShift: Int) {
        val resourcePriority = getAccountPriorityForStatus(statusName)
        if (resourcePriority != null) {
            try {
                addPresenceToPriorityMapping(statusName, resourcePriority.toInt())
            } catch (ex: NumberFormatException) {
                Timber.e(ex, "Wrong value for resource priority for status: %s", statusName)
            }
        } else {
            // if priority is less than zero, use the available priority
            var priority = resourcePriorityAvailable + availableShift
            if (priority <= 0) priority = resourcePriorityAvailable
            addPresenceToPriorityMapping(statusName, priority)
        }
    }

    /**
     * Returns the priority which will be used for `statusName`. Make sure we replace ' '
     * with '_' and use upper case as this will be and the property names used in account
     * properties that can override this values.
     *
     * @param statusName the status name
     *
     * @return the priority which will be used for `statusName`.
     */
    private fun getPriorityForPresenceStatus(statusName: String): Int {
        return statusToPriorityMappings[statusName.replace(" ".toRegex(), "_")
                .uppercase()] ?: return resourcePriorityAvailable
    }

    /**
     * Returns the account property value for a status name, if missing return null. Make sure we
     * replace ' ' with '_' and use upper case as this will be and the property names used in
     * account properties that can override this values.
     *
     * @param statusName PresenceStatus name
     *
     * @return the account property value for a status name, if missing return null.
     */
    private fun getAccountPriorityForStatus(statusName: String): String? {
        return mPPS!!.accountID.getAccountPropertyString(ProtocolProviderFactory.RESOURCE_PRIORITY
                + "_" + statusName.replace(" ".toRegex(), "_").uppercase())
    }

    companion object {
        /**
         * A map containing bindings between aTalk's jabber presence status instances and Jabber status codes
         */
        private val scToJabberModesMappings: MutableMap<String, Presence.Mode> = Hashtable()

        init {
            scToJabberModesMappings[JabberStatusEnum.AWAY] = Presence.Mode.away
            scToJabberModesMappings[JabberStatusEnum.ON_THE_PHONE] = Presence.Mode.away
            scToJabberModesMappings[JabberStatusEnum.IN_A_MEETING] = Presence.Mode.away
            scToJabberModesMappings[JabberStatusEnum.EXTENDED_AWAY] = Presence.Mode.xa
            scToJabberModesMappings[JabberStatusEnum.DO_NOT_DISTURB] = Presence.Mode.dnd
            scToJabberModesMappings[JabberStatusEnum.FREE_FOR_CHAT] = Presence.Mode.chat
            scToJabberModesMappings[JabberStatusEnum.AVAILABLE] = Presence.Mode.available
        }

        /**
         * A map containing bindings between aTalk's xmpp presence status instances and priorities to use for statuses.
         */
        private val statusToPriorityMappings: MutableMap<String, Int> = Hashtable()

        /**
         * Converts the specified jabber status to one of the status fields of the JabberStatusEnum class.
         *
         * @param presence the Jabber Status
         * @param jabberProvider the parent provider.
         *
         * @return a PresenceStatus instance representation of the Jabber Status parameter. The
         * returned result is one of the JabberStatusEnum fields.
         */
        fun jabberStatusToPresenceStatus(presence: Presence?,
                jabberProvider: ProtocolProviderServiceJabberImpl): PresenceStatus {
            val jabberStatusEnum = jabberProvider.jabberStatusEnum
            if (!presence!!.isAvailable) {
                return jabberStatusEnum!!.getStatus(JabberStatusEnum.OFFLINE)
            }

            // Check status mode when user is available
            when (presence.mode) {
                Presence.Mode.available -> return jabberStatusEnum!!.getStatus(JabberStatusEnum.AVAILABLE)
                Presence.Mode.away ->                 // on the phone a special status which is away with custom status message
                    return if (presence.status != null && presence.status.contains(JabberStatusEnum.ON_THE_PHONE)) jabberStatusEnum!!.getStatus(JabberStatusEnum.ON_THE_PHONE) else if (presence.status != null && presence.status.contains(JabberStatusEnum.IN_A_MEETING)) jabberStatusEnum!!.getStatus(JabberStatusEnum.IN_A_MEETING) else jabberStatusEnum!!.getStatus(JabberStatusEnum.AWAY)
                Presence.Mode.chat -> return jabberStatusEnum!!.getStatus(JabberStatusEnum.FREE_FOR_CHAT)
                Presence.Mode.dnd -> return jabberStatusEnum!!.getStatus(JabberStatusEnum.DO_NOT_DISTURB)
                Presence.Mode.xa -> return jabberStatusEnum!!.getStatus(JabberStatusEnum.EXTENDED_AWAY)
                else -> {
                    //unknown status
                    if (presence.isAway) return jabberStatusEnum!!.getStatus(JabberStatusEnum.AWAY)
                    if (presence.isAvailable) return jabberStatusEnum!!.getStatus(JabberStatusEnum.AVAILABLE)
                }
            }
            return jabberStatusEnum!!.getStatus(JabberStatusEnum.OFFLINE)
        }

        /**
         * Converts the specified JabberStatusEnum member to the corresponding Jabber Mode
         *
         * @param status the jabberStatus
         *
         * @return a PresenceStatus instance
         */
        fun presenceStatusToJabberMode(status: PresenceStatus?): Presence.Mode? {
            return scToJabberModesMappings[status!!.statusName]
        }

        /**
         * Adds the priority mapping for the `statusName`. Make sure we replace ' ' with '_' and
         * use upper case as this will be and the property names used in account properties that can
         * override this values.
         *
         * @param statusName the status name to use
         * @param value and its priority
         */
        private fun addPresenceToPriorityMapping(statusName: String, value: Int) {
            statusToPriorityMappings[statusName.replace(" ".toRegex(), "_").uppercase()] = value
        }
    }
}