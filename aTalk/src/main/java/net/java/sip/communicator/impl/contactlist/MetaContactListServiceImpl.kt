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

import android.text.TextUtils
import net.java.sip.communicator.impl.contactlist.MclStorageManager.StoredProtoContactDescriptor
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import net.java.sip.communicator.service.contactlist.MetaContactListException
import net.java.sip.communicator.service.contactlist.MetaContactListService
import net.java.sip.communicator.service.contactlist.event.MetaContactAvatarUpdateEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactGroupEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactListListener
import net.java.sip.communicator.service.contactlist.event.MetaContactModifiedEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactMovedEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactPropertyChangeEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactRenamedEvent
import net.java.sip.communicator.service.contactlist.event.ProtoContactEvent
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ContactGroup
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.OperationSetContactCapabilities
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresence
import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.ContactCapabilitiesEvent
import net.java.sip.communicator.service.protocol.event.ContactCapabilitiesListener
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener
import net.java.sip.communicator.service.protocol.event.ContactPropertyChangeEvent
import net.java.sip.communicator.service.protocol.event.ServerStoredGroupEvent
import net.java.sip.communicator.service.protocol.event.ServerStoredGroupListener
import net.java.sip.communicator.service.protocol.event.SubscriptionEvent
import net.java.sip.communicator.service.protocol.event.SubscriptionListener
import net.java.sip.communicator.service.protocol.event.SubscriptionMovedEvent
import okhttp3.internal.notifyAll
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.json.JSONObject
import org.osgi.framework.BundleContext
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.util.*

/**
 * An implementation of the MetaContactListService that would connect to protocol service
 * providers and build its contact list accordingly basing itself on the contact list stored by
 * the various protocol provider services and the contact list instance saved in persistent store.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class MetaContactListServiceImpl : MetaContactListService, ServiceListener, ContactPresenceStatusListener, ContactCapabilitiesListener {
    /**
     * The BundleContext that we got from the OSGI bus.
     */
    private var mBundleContext: BundleContext? = null

    /**
     * The list of AccountUID to protocol providers that we're currently aware of
     */
    private val mCurrentlyInstalledProviders: MutableMap<String, ProtocolProviderService> = Hashtable()

    /**
     * The root of the meta contact list.
     */
    val rootMetaGroup = MetaContactGroupImpl(this, ContactGroup.ROOT_GROUP_NAME, ContactGroup.ROOT_GROUP_UID)

    /**
     * The event handler that will be handling our subscription events.
     */
    private val clSubscriptionEventHandler = ContactListSubscriptionListener()

    /**
     * The event handler that will be handling group events.
     */
    private val clGroupEventHandler = ContactListGroupListener()

    /**
     * Listeners interested in events dispatched upon modification of the meta contact list.
     */
    private val mMetaContactListListeners: MutableList<MetaContactListListener?> = Vector()

    /**
     * Contains (as keys) `MetaContactGroup` names that are currently being resolved
     * against a given protocol and that this class's `ContactGroupListener` should ignore
     * as corresponding events will be handled by the corresponding methods. The table maps the
     * meta contact group names against lists of protocol providers. An incoming group event would
     * therefore be ignored by the class group listener if and only if it carries a name present
     * in this table and is issued by one of the providers mapped against this groupName.
     */
    private val mGroupEventIgnoreList = Hashtable<String?, MutableList<ProtocolProviderService?>>()

    /**
     * Contains (as keys) `Contact` addresses that are currently being resolved against a
     * given protocol and that this class's `ContactListener` should ignore as
     * corresponding events will be handled by the corresponding methods. The table maps the meta
     * contact addresses against lists of protocol providers. An incoming group event would
     * therefore be ignored by the class group listener if and only if it carries a name present
     * in this table and is issued by one of the providers mapped against this groupName.
     */
    private val mContactEventIgnoreList = Hashtable<String, MutableList<ProtocolProviderService?>>()

    /**
     * The instance of the storage manager which is handling the local copy of our contact list.
     */
    private val storageManager = MclStorageManager()

    /**
     * Starts this implementation of the MetaContactListService. The implementation would first
     * restore a default contact list from a persistent storage. It would then connect
     * to OSGI and retrieve any existing protocol providers and if <br></br>
     * 1) They provide implementations of OperationSetPersistentPresence, it would synchronize
     * their contact lists with the local one (adding subscriptions for contacts that do not
     * exist in the server stored contact list and adding locally contacts that were found on
     * the server but not in the local database).
     *
     *
     * 2) The only provide non persistent implementations of OperationSetPresence, the meta contact list
     * impl would create subscriptions for all local contacts in the corresponding protocol provider.
     *
     *
     * This implementation would also start listening for any newly registered protocol provider
     * implementations and perform the same algorithm with them.
     *
     *
     *
     * @param bc the currently valid OSGI bundle context.
     */
    fun start(bc: BundleContext) {
        Timber.d("Starting the meta contact list implementation.")
        mBundleContext = bc

        // initialize the meta contact list from what has been stored locally.
        try {
            storageManager.start(mBundleContext, this)
        } catch (ex: Exception) {
            Timber.e("Failed loading the stored contact list: %s", ex.message)
        }

        // start listening for newly register or removed protocol providers
        bc.addServiceListener(this)
        var ppsRefs: Array<ServiceReference<*>>? = null
        try {
            // first discover the icq service then find the protocol provider service
            ppsRefs = mBundleContext!!.getServiceReferences(ProtocolProviderService::class.java.name, null)
        } catch (e: InvalidSyntaxException) {
            e.printStackTrace()
        }
        // in case we found any, retrieve the root groups for all protocol providers and create the meta contact list
        if (ppsRefs != null) {
            for (ppsRef in ppsRefs) {
                val pps = mBundleContext!!.getService(ppsRef as ServiceReference<ProtocolProviderService>)
                handleProviderAdded(pps)
            }
        }
    }

    /**
     * Prepares the meta contact list service for shut-down.
     *
     * @param bc the currently active bundle context.
     */
    fun stop(bc: BundleContext) {
        bc.removeServiceListener(this)

        // stop listening to all currently installed providers
        for (pps in mCurrentlyInstalledProviders.values) {
            val opSetPersPresence = pps.getOperationSet(OperationSetPersistentPresence::class.java)
            if (opSetPersPresence != null) {
                opSetPersPresence.removeContactPresenceStatusListener(this)
                opSetPersPresence.removeSubscriptionListener(clSubscriptionEventHandler)
                opSetPersPresence.removeServerStoredGroupChangeListener(clGroupEventHandler)
            } else {
                // check if a non persistent presence operation set exists.
                val opSetPresence = pps.getOperationSet(OperationSetPresence::class.java)
                if (opSetPresence != null) {
                    opSetPresence.removeContactPresenceStatusListener(this)
                    opSetPresence.removeSubscriptionListener(clSubscriptionEventHandler)
                }
            }
        }
        mCurrentlyInstalledProviders.clear()
    }

    /**
     * Adds a listener for `MetaContactListChangeEvent`s posted after the tree changes.
     *
     * @param l the listener to add
     */
    override fun addMetaContactListListener(l: MetaContactListListener?) {
        synchronized(mMetaContactListListeners) { if (!mMetaContactListListeners.contains(l)) mMetaContactListListeners.add(l) }
    }

    /**
     * First makes the specified protocol provider create the contact as indicated by `contactID`,
     * and then associates it to the _existing_ `metaContact` given as an argument.
     *
     * @param provider the ProtocolProviderService that should create the contact indicated by `contactID`.
     * @param metaContact the meta contact where that the newly created contact should be associated to.
     * @param contactID the identifier of the contact that the specified provider
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    @Throws(MetaContactListException::class)
    override fun addNewContactToMetaContact(provider: ProtocolProviderService?, metaContact: MetaContact?, contactID: String?) {
        addNewContactToMetaContact(provider, metaContact, contactID, true)
    }

    /**
     * First makes the specified protocol provider create the contact as indicated by `contactID`,
     * and then associates it to the _existing_ `metaContact` given as an argument.
     *
     * @param provider the ProtocolProviderService that should create the contact indicated by `contactID`.
     * @param metaContact the meta contact where that the newly created contact should be associated to.
     * @param contactID the identifier of the contact that the specified provider
     * @param fireEvent specifies whether an even is to be fire at the end of the method.Used when this
     * method is called upon creation of a new meta contact and not only a new contact.
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    @Throws(MetaContactListException::class)
    fun addNewContactToMetaContact(provider: ProtocolProviderService?,
            metaContact: MetaContact?, contactID: String?, fireEvent: Boolean) {
        // find the parent group in the corresponding protocol.
        val parentMetaGroup = findParentMetaContactGroup(metaContact)
                ?: throw MetaContactListException("orphan Contact: $metaContact", null,
                        MetaContactListException.CODE_NETWORK_ERROR)
        addNewContactToMetaContact(provider, parentMetaGroup, metaContact, contactID, fireEvent)
    }

    /**
     * First makes the specified protocol provider create the contact as indicated  `contactID`,
     * and then associates it to the _existing_ `metaContact` given as an argument.
     *
     * @param provider the ProtocolProviderService that should create the contact indicated by `contactID`.
     * @param parentMetaGroup the meta contact group which is the parent group of the newly created contact
     * @param metaContact the meta contact where that the newly created contact should be associated to.
     * @param contactID the identifier of the contact that the specified provider
     * @param fireEvent specifies whether an even is to be fired at the end of the method.Used when
     * this method is called upon creation of a new meta contact and not only a new contact.
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    @Throws(MetaContactListException::class)
    private fun addNewContactToMetaContact(provider: ProtocolProviderService?, parentMetaGroup: MetaContactGroup,
            metaContact: MetaContact?, contactID: String?, fireEvent: Boolean) {
        val opSetPersPresence = provider!!.getOperationSet(OperationSetPersistentPresence::class.java)
                ?: /* @todo handle non-persistent presence operation sets as well */
                return
        require(metaContact is MetaContactImpl) { metaContact.toString() + " is not an instance of MetaContactImpl" }
        val parentProtoGroup = resolveProtoPath(provider, parentMetaGroup as MetaContactGroupImpl)
                ?: throw MetaContactListException("Could not obtain proto group parent for "
                        + metaContact, null, MetaContactListException.CODE_NETWORK_ERROR)
        val evtRetriever = BlockingSubscriptionEventRetriever(contactID)
        addContactToEventIgnoreList(contactID!!, provider)
        opSetPersPresence.addSubscriptionListener(evtRetriever)
        opSetPersPresence.addServerStoredGroupChangeListener(evtRetriever)
        try {
            // create and subscribe the contact in the group; if it is the root group just call subscribe
            if (parentMetaGroup == rootMetaGroup) opSetPersPresence.subscribe(provider, contactID) else opSetPersPresence.subscribe(parentProtoGroup, contactID)

            // wait for a confirmation event
            evtRetriever.waitForEvent(CONTACT_LIST_MODIFICATION_TIMEOUT.toLong())
        } catch (ex: OperationFailedException) {
            if (ex.getErrorCode() == OperationFailedException.SUBSCRIPTION_ALREADY_EXISTS) {
                throw MetaContactListException("failed to create contact $contactID", ex,
                        MetaContactListException.CODE_CONTACT_ALREADY_EXISTS_ERROR)
            } else if (ex.getErrorCode() == OperationFailedException.NOT_SUPPORTED_OPERATION) {
                throw MetaContactListException("failed to create contact $contactID", ex,
                        MetaContactListException.CODE_NOT_SUPPORTED_OPERATION)
            }
            throw MetaContactListException("failed to create contact $contactID", ex,
                    MetaContactListException.CODE_NETWORK_ERROR)
        } catch (ex: Exception) {
            throw MetaContactListException("failed to create contact $contactID", ex,
                    MetaContactListException.CODE_NETWORK_ERROR)
        } finally {
            // whatever happens we need to remove the event collector end the ignore filter.
            removeContactFromEventIgnoreList(contactID, provider)
            opSetPersPresence.removeSubscriptionListener(evtRetriever)
        }
        // attach the newly created contact to a meta contact
        if (evtRetriever.mEvent == null) {
            throw MetaContactListException("Failed to create a contact with address: "
                    + contactID, null, MetaContactListException.CODE_NETWORK_ERROR)
        }
        if (evtRetriever.mEvent is SubscriptionEvent
                && (evtRetriever.mEvent as SubscriptionEvent?)!!.getEventID() == SubscriptionEvent.SUBSCRIPTION_FAILED) {
            throw MetaContactListException(
                    "Failed to create a contact with address: " + contactID + " "
                            + (evtRetriever.mEvent as SubscriptionEvent?)!!.getErrorReason(), null,
                    MetaContactListException.CODE_UNKNOWN_ERROR)
        }

        // now finally - add the contact to the meta contact
        metaContact.addProtoContact(evtRetriever.mSourceContact)

        // only fire an event here if the calling method wants us to. in case this is the creation
        // of a new contact and not only addition of a proto contact we should remain silent and
        // the calling method will do the eventing.
        if (fireEvent) {
            fireProtoContactEvent(evtRetriever.mSourceContact,
                    ProtoContactEvent.PROTO_CONTACT_ADDED, null, metaContact)
        }
        parentMetaGroup.addMetaContact(metaContact)
    }

    /**
     * Makes sure the directories in the whole path from the root to the specified group have
     * corresponding directories in the protocol indicated by `protoProvider`.
     * The method does not return before creating all groups has completed.
     *
     * @param protoProvider a reference to the protocol provider where the groups should be created.
     * @param metaGroup a ref to the last group of the path that should be created in the specified `protoProvider`
     * @return e reference to the newly created `ContactGroup`
     */
    private fun resolveProtoPath(protoProvider: ProtocolProviderService?, metaGroup: MetaContactGroupImpl?): ContactGroup? {
        // NA for aTalk, as groups are stored in DB
        // Iterator<ContactGroup> contactGroupsForPPS = metaGroup.getContactGroupsForProvider(protoProvider);
        // if (contactGroupsForPPS.hasNext()) {
        // we already have at least one group corresponding to the metaGroup
        //     return contactGroupsForPPS.next();
        // }
        val opSetPersPresence = protoProvider!!.getOperationSet(OperationSetPersistentPresence::class.java)
                ?: return null

        // if persistent presence is not supported - just bail out as we should have verified this earlier anyway
        val parentProtoGroup: ContactGroup?
        // MetaContactGroupImpl parentMetaGroup = (MetaContactGroupImpl) findParentMetaContactGroup(metaGroup);
        val parentMetaGroup = metaGroup!!.getParentMetaContactGroup() as MetaContactGroupImpl?
        // if (parentMetaGroup == null) {
        //     Timber.d("Resolve failed at group %s", metaGroup);
        //    throw new NullPointerException("Internal Error. Orphan group.");
        // }

        // special treatment for the root group (stop the recursion and return the root contactGroup
        if (parentMetaGroup == null) {
            Timber.d("Assume RootGroup, resolve parentMetaGroup failed: %s", metaGroup)
            return opSetPersPresence.getServerStoredContactListRoot()
        } else {
            parentProtoGroup = resolveProtoPath(protoProvider, parentMetaGroup)

            // Return the existing contactGroup if found.
            val contactGroup = parentProtoGroup!!.getGroup(metaGroup.getGroupName())
            if (contactGroup != null) return contactGroup
        }

        // create the proto group
        val evtRetriever = BlockingGroupEventRetriever(metaGroup.getGroupName())
        opSetPersPresence.addServerStoredGroupChangeListener(evtRetriever)
        addGroupToEventIgnoreList(metaGroup.getGroupName(), protoProvider)
        try {
            // create the group
            opSetPersPresence.createServerStoredContactGroup(parentProtoGroup, metaGroup.getGroupName())

            // wait for a confirmation event
            evtRetriever.waitForEvent(CONTACT_LIST_MODIFICATION_TIMEOUT.toLong())
        } catch (ex: Exception) {
            throw MetaContactListException("failed to create contact group " + metaGroup.getGroupName(),
                    ex, MetaContactListException.CODE_NETWORK_ERROR)
        } finally {
            // whatever happens we need to remove the event collector and ignore filter.
            removeGroupFromEventIgnoreList(metaGroup.getGroupName(), protoProvider)
            opSetPersPresence.removeServerStoredGroupChangeListener(evtRetriever)
        }

        // something went wrong.
        if (evtRetriever.mEvent == null) {
            throw MetaContactListException("Failed to create a proto group named: " + metaGroup.getGroupName(),
                    null, MetaContactListException.CODE_NETWORK_ERROR)
        }

        // now add the proto group to the meta group.
        metaGroup.addProtoGroup(evtRetriever.mEvent!!.getSourceGroup())
        fireMetaContactGroupEvent(metaGroup, evtRetriever.mEvent!!.getSourceProvider(),
                evtRetriever.mEvent!!.getSourceGroup(), MetaContactGroupEvent.CONTACT_GROUP_ADDED_TO_META_GROUP)
        return evtRetriever.mEvent!!.getSourceGroup()
    }

    /**
     * Returns the meta contact group that is a direct parent of the specified `child`. If
     * no parent is found `null` is returned.
     *
     * @param child the `MetaContactGroup` whose parent group we're looking for. If no parent is
     * found `null` is returned.
     * @return the `MetaContactGroup` that contains `child` or null if no parent was found.
     */
    override fun findParentMetaContactGroup(child: MetaContactGroup?): MetaContactGroup? {
        return findParentMetaContactGroup(rootMetaGroup, child)
    }

    /**
     * Returns the meta contact group that is a direct parent of the specified `child`,
     * beginning the search at the specified root. If no parent is found `null` is returned.
     *
     * @param child the `MetaContactGroup` whose parent group we're looking for.
     * @param root the parent where the search should start.
     * @return the `MetaContactGroup` that contains `child` or null if no parent was found.
     */
    private fun findParentMetaContactGroup(root: MetaContactGroupImpl, child: MetaContactGroup?): MetaContactGroup? {
        return child!!.getParentMetaContactGroup()
    }

    /**
     * Returns the meta contact group that is a direct parent of the specified `child`.
     *
     * @param child the `MetaContact` whose parent group we're looking for.
     * @return the `MetaContactGroup`
     * @throws IllegalArgumentException if `child` is not an instance of MetaContactImpl
     */
    override fun findParentMetaContactGroup(child: MetaContact?): MetaContactGroup? {
        require(child is MetaContactImpl) { child.toString() + " is not a MetaContactImpl instance." }
        return child.parentGroup
    }

    /**
     * First makes the specified protocol provider create a contact corresponding to the specified
     * `contactID`, then creates a new MetaContact which will encapsulate the newly
     * created protocol specific contact.
     *
     * @param provider a ref to `ProtocolProviderService` instance which will create the actual
     * protocol specific contact.
     * @param metaContactGroup the MetaContactGroup where the newly created meta contact should be stored.
     * @param contactID a protocol specific string identifier indicating the contact the protocol provider
     * should create.
     * @return the newly created `MetaContact`
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    @Throws(MetaContactListException::class)
    override fun createMetaContact(provider: ProtocolProviderService?, metaContactGroup: MetaContactGroup?,
            contactID: String?): MetaContact {
        require(metaContactGroup is MetaContactGroupImpl) { metaContactGroup.toString() + " is not an instance of MetaContactGroupImpl" }
        val newMetaContact = MetaContactImpl()
        addNewContactToMetaContact(provider, metaContactGroup, newMetaContact, contactID, false)

        // don't fire a PROTO_CONT_ADDED event we'll fire our own event here.
        fireMetaContactEvent(newMetaContact, findParentMetaContactGroup(newMetaContact), MetaContactEvent.META_CONTACT_ADDED)
        return newMetaContact
    }

    /**
     * Creates a `MetaContactGroup` with the specified group name. The meta contact group
     * would only be created locally and resolved against the different server stored protocol
     * contact lists upon the creation of the first protocol specific child contact in the
     * respective group.
     *
     * @param parentGroup the meta contact group inside which the new child group must be created.
     * @param groupName the name of the `MetaContactGroup` to create.
     * @return the newly created `MetaContactGroup`
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    @Throws(MetaContactListException::class)
    override fun createMetaContactGroup(parentGroup: MetaContactGroup?, groupName: String?): MetaContactGroup {
        require(parentGroup is MetaContactGroupImpl) { parentGroup.toString() + " is not an instance of MetaContactGroupImpl" }

        // make sure that "parent" does not already contain a subgroup called "groupName"
        val subgroups = parentGroup.getSubgroups()
        while (subgroups.hasNext()) {
            val group = subgroups.next()
            if (group!!.getGroupName() == groupName) {
                throw MetaContactListException("Parent " + parentGroup.getGroupName()
                        + " already contains a group called " + groupName,
                        CloneNotSupportedException("just testing nested exc-s"),
                        MetaContactListException.CODE_GROUP_ALREADY_EXISTS_ERROR)
            }
        }
        // we only have to create the meta contact group here. we don't care about protocol
        // specific groups.
        val newMetaGroup = MetaContactGroupImpl(this, groupName!!)
        parentGroup.addSubgroup(newMetaGroup)

        // fire the event (pps is null)
        fireMetaContactGroupEvent(newMetaGroup, null, null, MetaContactGroupEvent.META_CONTACT_GROUP_ADDED)
        return newMetaGroup
    }

    /**
     * Renames the specified `MetaContactGroup` as indicated by the `newName` param.
     * The operation would only affect the local meta group and would not "touch" any
     * encapsulated protocol specific group.
     *
     *
     *
     * @param group the group to rename.
     * @param newGroupName the new name of the `MetaContactGroup` to rename.
     */
    override fun renameMetaContactGroup(group: MetaContactGroup?, newGroupName: String?) {
        (group as MetaContactGroupImpl?)!!.setGroupName(newGroupName!!)
        val groups = group!!.getContactGroups()
        while (groups.hasNext()) {
            val protoGroup = groups.next()

            // get a persistent presence operation set
            val opSetPresence = protoGroup!!.getProtocolProvider()!!.getOperationSet(OperationSetPersistentPresence::class.java)
            if (opSetPresence != null) {
                try {
                    opSetPresence.renameServerStoredContactGroup(protoGroup, newGroupName)
                } catch (t: Throwable) {
                    Timber.e(t, "Error renaming protocol group: %s", protoGroup)
                }
            }
        }
        fireMetaContactGroupEvent(group, null, null,
                MetaContactGroupEvent.META_CONTACT_GROUP_RENAMED)
    }

    /**
     * Returns the root `MetaContactGroup` in this contact list.
     *
     * @return the root `MetaContactGroup` for this contact list.
     */
    override fun getRoot(): MetaContactGroup {
        return rootMetaGroup
    }

    /**
     * Sets the display name for `metaContact` to be `newName`.
     *
     * @param metaContact the `MetaContact` that we are renaming
     * @param newName a `String` containing the new display name for `metaContact`.
     * @throws IllegalArgumentException if `metaContact` is not an instance that belongs to the underlying
     * implementation.
     */
    @Throws(IllegalArgumentException::class)
    override fun renameMetaContact(metaContact: MetaContact?, newName: String?) {
        renameMetaContact(metaContact, newName, true)
    }

    /**
     * Sets the display name for `metaContact` to be `newName`.
     *
     * @param metaContact the `MetaContact` that we are renaming
     * @param newDisplayName a `String` containing the new display name for `metaContact`.
     * @throws IllegalArgumentException if `metaContact` is not an instance that belongs to the underlying
     * implementation.
     */
    @Throws(IllegalArgumentException::class)
    private fun renameMetaContact(metaContact: MetaContact?, newDisplayName: String?, isUserDefined: Boolean) {
        require(metaContact is MetaContactImpl) { metaContact.toString() + " is not a MetaContactImpl instance." }
        val oldDisplayName = metaContact.getDisplayName()
        metaContact.setDisplayName(newDisplayName)
        metaContact.isDisplayNameUserDefined = isUserDefined
        val contacts = metaContact.getContacts()
        while (contacts.hasNext()) {
            val protoContact = contacts.next()

            // get a persistent presence operation set
            val opSetPresence = protoContact!!.protocolProvider
                    .getOperationSet(OperationSetPersistentPresence::class.java)
            if (opSetPresence != null) {
                try {
                    opSetPresence.setDisplayName(protoContact, newDisplayName)
                } catch (t: Throwable) {
                    Timber.e(t, "Error renaming protocol contact: %s", protoContact)
                }
            }
        }
        fireMetaContactEvent(MetaContactRenamedEvent(metaContact, oldDisplayName, newDisplayName))

        // changing the display name has surely brought a change in the order as well so let's
        // tell the others
        fireMetaContactGroupEvent(findParentMetaContactGroup(metaContact), null, null,
                MetaContactGroupEvent.CHILD_CONTACTS_REORDERED)
    }

    /**
     * Resets display name of the MetaContact to show the value from the underlying contacts.
     *
     * @param metaContact the `MetaContact` that we are operating on
     * @throws IllegalArgumentException if `metaContact` is not an instance that belongs to the underlying
     * implementation.
     */
    @Throws(IllegalArgumentException::class)
    override fun clearUserDefinedDisplayName(metaContact: MetaContact?) {
        require(metaContact is MetaContactImpl) { metaContact.toString() + " is not a MetaContactImpl instance." }
        // set display name
        metaContact.isDisplayNameUserDefined = false
        if (metaContact.getContactCount() == 1) {
            renameMetaContact(metaContact, metaContact.getDefaultContact()!!.displayName, false)
        } else {
            // just fire event so the modification is stored
            fireMetaContactEvent(MetaContactRenamedEvent(metaContact,
                    metaContact.getDisplayName(), metaContact.getDisplayName()))
        }
    }

    /**
     * Sets the avatar for `metaContact` to be `newAvatar`.
     *
     * @param metaContact the `MetaContact` that change avatar
     * @param protoContact the `Contact> that change avatar
     * @param newAvatar avatar image bytes
     * @throws IllegalArgumentException if `metaContact` is not an instance that belongs to the underlying
     * implementation.
    ` */
    @Throws(IllegalArgumentException::class)
    fun changeMetaContactAvatar(metaContact: MetaContact?, protoContact: Contact?, newAvatar: ByteArray?) {
        if (metaContact !is MetaContactImpl) {
            //cmeng: do not throw; there is no proper handler defined. Just log and return
            // throw new IllegalArgumentException(metaContact + " is not a MetaContactImpl instance.");
            Timber.e("%s is not a MetaContactImpl instance.", metaContact)
            return
        }
        val oldAvatar = metaContact.getAvatar(true)
        metaContact.cacheAvatar(protoContact, newAvatar)
        fireMetaContactEvent(MetaContactAvatarUpdateEvent(metaContact, oldAvatar, newAvatar))
    }

    /**
     * Makes the specified `contact` a child move to the `newParentMetaGroup`
     * MetaContactGroup. If `contact` was previously a child of a meta contact, it will be
     * removed from its old parent and to a newly created one even if they both are in the same
     * group. If the specified contact was the only child of its previous parent, then the meta
     * contact will also be moved.
     *
     * @param contact the `Contact` to move to the
     * @param newParent the MetaContactGroup where we'd like contact to be moved.
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    @Throws(MetaContactListException::class)
    override fun moveContact(contact: Contact?, newParent: MetaContactGroup?) {
        if (contact!!.getPersistableAddress() == null) {
            Timber.i("Contact cannot be moved! This contact doesn't have persistent address.")
            return
        }
        val provider = contact.protocolProvider
        val opSetMUC = provider.getOperationSet(OperationSetMultiUserChat::class.java)
        if (opSetMUC != null && opSetMUC.isPrivateMessagingContact(contact.contactJid)) {
            val metaContactImpl = MetaContactImpl()
            val newParentMetaGroupImpl = newParent as MetaContactGroupImpl?
            newParentMetaGroupImpl!!.addMetaContact(metaContactImpl)
            fireMetaContactEvent(metaContactImpl, newParentMetaGroupImpl, MetaContactEvent.META_CONTACT_ADDED)
            addNewContactToMetaContact(provider, metaContactImpl, contact.getPersistableAddress())
            return
        }
        // get a persistent presence operation set
        val opSetPresence = provider.getOperationSet(OperationSetPersistentPresence::class.java)
        if (opSetPresence == null) {
            /* @todo handle non persistent presence operation sets */
            Timber.d("Unhandled OperationSetPersistentPresence for: %s", provider)
        }
        val currentParentMetaContact = findMetaContactByContact(contact) as MetaContactImpl?
        val parentProtoGroup = resolveProtoPath(contact.protocolProvider,
                newParent as MetaContactGroupImpl?)

        // if the contact is not currently in the proto group corresponding to its new metaContact
        // group parent then move it
        try {
            if ((contact.parentContactGroup != parentProtoGroup) && (opSetPresence != null)) {
                opSetPresence.moveContactToGroup(contact, parentProtoGroup)
            }
            // remove the proto-contact only if move is successful
            currentParentMetaContact!!.removeProtoContact(contact)
        } catch (ex: OperationFailedException) {
            throw MetaContactListException(ex.message, MetaContactListException.CODE_MOVE_CONTACT_ERROR)
        }

        // first check if this has been already done on other place
        // (SubscriptionListener.subscriptionMoved)
        var metaContactImpl: MetaContactImpl? = null
        synchronized(contact) {
            val checkContact = findMetaContactByContact(contact)
            if (checkContact == null) {
                metaContactImpl = MetaContactImpl()
                newParent!!.addMetaContact(metaContactImpl!!)
                metaContactImpl!!.addProtoContact(contact)
            }
        }
        if (metaContactImpl != null) {
            fireMetaContactEvent(metaContactImpl!!, newParent, MetaContactEvent.META_CONTACT_ADDED)

            // fire an event telling everyone that contact has been added to its new parent.
            fireProtoContactEvent(contact, ProtoContactEvent.PROTO_CONTACT_MOVED,
                    currentParentMetaContact, metaContactImpl)
        }

        // if this was the last contact in the meta contact - remove it. it is true that in some
        // cases the move would be followed by some kind may trigger the removal of empty meta
        // contacts. Yet in many cases particularly if parent groups were not changed in the
        // protocol contact list no event would come and the meta contact will remain empty that's
        // why we delete it here and if an event follows it would simply be ignored.
        if (currentParentMetaContact.getContactCount() == 0) {
            val parentMetaGroup = currentParentMetaContact.parentGroup
            parentMetaGroup!!.removeMetaContact(currentParentMetaContact)
            fireMetaContactEvent(currentParentMetaContact, parentMetaGroup, MetaContactEvent.META_CONTACT_REMOVED)
        }
    }

    /**
     * Makes the specified `contact` a child of the `newParent` MetaContact.
     *
     * @param contact the `Contact` to move to the
     * @param newParent the MetaContact where we'd like contact to be moved.
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    @Throws(MetaContactListException::class)
    override fun moveContact(contact: Contact?, newParent: MetaContact?) {
        if (contact!!.getPersistableAddress() == null) {
            Timber.i("Contact cannot be moved! This contact doesn't have persistent address.")
            return
        }
        val provider = contact.protocolProvider
        val opSetMUC = provider.getOperationSet(OperationSetMultiUserChat::class.java)
        if (opSetMUC != null && opSetMUC.isPrivateMessagingContact(contact.contactJid)) {
            addNewContactToMetaContact(provider, newParent, contact.getPersistableAddress())
            return
        }

        // get a persistent presence operation set
        val opSetPresence = provider.getOperationSet(OperationSetPersistentPresence::class.java)
        if (opSetPresence == null) {
            /* @todo handle non persistent presence operation sets */
            Timber.d("Unhandled OperationSetPersistentPresence for: %s", provider)
        }
        require(newParent is MetaContactImpl) { newParent.toString() + " is not a MetaContactImpl instance." }
        val currentParentMetaContact = findMetaContactByContact(contact) as MetaContactImpl?
        val newParentGroup = findParentMetaContactGroup(newParent)
        val parentProtoGroup = resolveProtoPath(contact.protocolProvider, newParentGroup as MetaContactGroupImpl?)

        // if the contact is not currently in the proto group corresponding to its new metaContact
        // group parent then move it
        try {
            if ((contact.parentContactGroup != parentProtoGroup) && (opSetPresence != null)) {
                opSetPresence.moveContactToGroup(contact, parentProtoGroup)
            }
            // remove the proto-contact only if move is successful
            currentParentMetaContact!!.removeProtoContact(contact)
        } catch (ex: OperationFailedException) {
            throw MetaContactListException(ex.message, MetaContactListException.CODE_MOVE_CONTACT_ERROR)
        }
        synchronized(contact) {
            val checkContact = findMetaContactByContact(contact)
            if (checkContact == null) {
                newParent.addProtoContact(contact)
            }
        }
        if (newParent.containsContact(contact)) {
            // fire an event telling everyone that contact has been added to its new parent.
            fireProtoContactEvent(contact, ProtoContactEvent.PROTO_CONTACT_MOVED,
                    currentParentMetaContact, newParent)
        }

        // if this was the last contact in the meta contact - remove it. it is true that in some
        // cases the move would be followed by some kind of protocol provider events indicating
        // the change which on its turn may trigger the removal of empty meta contacts. Yet in
        // many cases particularly if parent groups were not changed in the protocol contact list
        // no event would come and the meta contact will remain empty that's why we delete it
        // here and if an event follows it would simply be ignored.
        if (currentParentMetaContact.getContactCount() == 0) {
            val parentMetaGroup = currentParentMetaContact.parentGroup
            parentMetaGroup!!.removeMetaContact(currentParentMetaContact)
            fireMetaContactEvent(currentParentMetaContact, parentMetaGroup,
                    MetaContactEvent.META_CONTACT_REMOVED)
        }
    }

    /**
     * Moves the specified `MetaContact` to `newGroup`.
     *
     * @param metaContact the `MetaContact` to move.
     * @param newGroup the `MetaContactGroup` that should be the new parent of `contact`.
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     * @throws IllegalArgumentException if `newMetaGroup` or `metaContact` do not come from this implementation.
     */
    @Throws(MetaContactListException::class, IllegalArgumentException::class)
    override fun moveMetaContact(metaContact: MetaContact?, newGroup: MetaContactGroup?) {
        require(newGroup is MetaContactGroupImpl) { newGroup.toString() + " is not a MetaContactGroupImpl instance" }
        require(metaContact is MetaContactImpl) { metaContact.toString() + " is not a MetaContactImpl instance" }

        // first remove the meta contact from its current parent, then add to new metaGroup
        val currentParent = findParentMetaContactGroup(metaContact) as MetaContactGroupImpl?
        currentParent?.removeMetaContact(metaContact)
        newGroup.addMetaContact(metaContact)
        try {
            // first make sure the new meta contact group path is resolved against all
            // protocols that the MetaContact requires.
            // Then move the meta contact in there and move all proto contacts inside it.
            val contacts = metaContact.getContacts()
            while (contacts.hasNext()) {
                val protoContact = contacts.next()
                val protoGroup = resolveProtoPath(protoContact!!.protocolProvider, newGroup)

                // get a persistent or non persistent presence operation set
                val opSetPresence = protoContact.protocolProvider.getOperationSet(OperationSetPersistentPresence::class.java)
                if (opSetPresence == null) {
                    /* @todo handle non persistent presence operation sets */
                } else {
                    if (newGroup == getRoot()) opSetPresence.moveContactToGroup(protoContact, opSetPresence.getServerStoredContactListRoot()) else opSetPresence.moveContactToGroup(protoContact, protoGroup)
                }
            }
        } catch (ex: Exception) {
            Timber.e(ex, "Cannot move contact")

            // now move the contact to previous parent
            newGroup.removeMetaContact(metaContact)
            currentParent!!.addMetaContact(metaContact)
            throw MetaContactListException(ex.message, MetaContactListException.CODE_MOVE_CONTACT_ERROR)
        }
        // fire the move event.
        fireMetaContactEvent(MetaContactMovedEvent(metaContact, currentParent, newGroup))
    }

    /**
     * Deletes the specified contact from both the local contact list and (if applicable) the
     * server stored contact list if supported by the corresponding protocol.
     *
     * @param contact the contact to remove.
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    @Throws(MetaContactListException::class)
    override fun removeContact(contact: Contact?) {
        // remove the contact from the provider and do nothing else; updating and/or removing the
        // corresponding meta contact would happen once a confirmation event is received from the
        // underlying protocol provider
        var opSetPresence = contact!!.protocolProvider.getOperationSet(OperationSetPresence::class.java)

        // in case the provider only has a persistent operation set:
        if (opSetPresence == null) {
            opSetPresence = contact.protocolProvider.getOperationSet(OperationSetPersistentPresence::class.java)
            checkNotNull(opSetPresence) { "Cannot remove a contact from a provider with no presence operation set." }
        }
        try {
            opSetPresence.unsubscribe(contact)
        } catch (ex: Exception) {
            var errorTxt: String? = "Failed to remove $contact from its protocol provider. "
            if (ex is OperationFailedException || ex is IllegalStateException) errorTxt += ex.message
            throw MetaContactListException(errorTxt, ex, MetaContactListException.CODE_NETWORK_ERROR)
        }
    }

    /**
     * Removes a listener previously added with `addContactListListener`.
     *
     * @param l the listener to remove
     */
    override fun removeMetaContactListListener(l: MetaContactListListener?) {
        synchronized(mMetaContactListListeners) { mMetaContactListListeners.remove(l) }
    }

    /**
     * Removes the specified `metaContact` as well as all of its underlying contacts.
     * Do not fire events. that will be done by the contact listener as soon as it gets
     * confirmation events of proto contact removal the removal of the last contact would
     * also generate an even for the removal of the meta contact itself.
     *
     * @param metaContact the metaContact to remove.
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    @Throws(MetaContactListException::class)
    override fun removeMetaContact(metaContact: MetaContact?) {
        val protoContactsIter = metaContact!!.getContacts()
        while (protoContactsIter.hasNext()) {
            removeContact(protoContactsIter.next())
        }
    }

    /**
     * Removes the specified meta contact group, all its corresponding protocol specific groups
     * and all their children.
     *
     * @param groupToRemove the `MetaContactGroup` to have removed.
     * @throws MetaContactListException with an appropriate code if the operation fails for some reason.
     */
    @Throws(MetaContactListException::class)
    override fun removeMetaContactGroup(groupToRemove: MetaContactGroup?) {
        require(groupToRemove is MetaContactGroupImpl) { groupToRemove.toString() + " is not an instance of MetaContactGroupImpl" }

        // First remove all its protoGroups; and then remove the metaGroup itself.
        try {
            val protoGroups = groupToRemove.getContactGroups()
            while (protoGroups.hasNext()) {
                val protoGroup = protoGroups.next()
                val opSetPersPresence = protoGroup!!.getProtocolProvider()!!
                        .getOperationSet(OperationSetPersistentPresence::class.java)
                        ?: /* @todo handle removal of non persistent proto groups */
                        return
                opSetPersPresence.removeServerStoredContactGroup(protoGroup)
            }
        } catch (ex: Exception) {
            throw MetaContactListException(ex.message,
                    MetaContactListException.CODE_REMOVE_GROUP_ERROR)
        }
        val parentMetaGroup = findParentMetaContactGroup(groupToRemove) as MetaContactGroupImpl?
        parentMetaGroup!!.removeSubgroup(groupToRemove)
        fireMetaContactGroupEvent(groupToRemove, null, null, MetaContactGroupEvent.META_CONTACT_GROUP_REMOVED)
    }

    /**
     * Removes the protocol specific group from the specified meta contact group and removes from
     * meta contacts all proto contacts that belong to the same provider as the group which is
     * being removed.
     *
     * @param metaContainer the MetaContactGroup that we'd like to remove a contact group from.
     * @param groupToRemove the ContactGroup that we'd like removed.
     * @param sourceProvider the ProtocolProvider that the contact group belongs to.
     */
    fun removeContactGroupFromMetaContactGroup(metaContainer: MetaContactGroupImpl?,
            groupToRemove: ContactGroup?, sourceProvider: ProtocolProviderService?) {
        // if we failed to find the metaGroup corresponding to proto group
        if (metaContainer == null) {
            Timber.w("No meta container found, when trying to remove group: %s", groupToRemove)
            return
        }

        /*
         * Go through all meta contacts and remove all contacts that belong to the same provider
         * and are therefore children of the group that is being removed.
         */
        locallyRemoveAllContactsForProvider(metaContainer, groupToRemove)
        fireMetaContactGroupEvent(metaContainer, sourceProvider, groupToRemove,
                MetaContactGroupEvent.CONTACT_GROUP_REMOVED_FROM_META_GROUP)
    }

    /**
     * Removes local resources storing copies of the meta contact list. This method is meant
     * primarily to aid automated testing which may depend on beginning the tests with an empty
     * local contact list.
     */
    override fun purgeLocallyStoredContactListCopy() {
        Timber.log(TimberLog.FINER, "Removed meta contact list storage file.")
    }

    /**
     * Goes through the specified group and removes from all meta contacts, protocol specific
     * contacts belonging to the specified `groupToRemove`. Note that this method won't
     * undertake any calls to the protocol itself as it is used only to update the local contact
     * list as a result of a server generated event.
     *
     * @param parentMetaGroup the MetaContactGroup whose children we should go through
     * @param groupToRemove the proto group that we want removed together with its children.
     */
    // cmeng - SQLite will remove all decedent of the groupToRemove base on accountUuid etc
    // need to fireEvent for all listeners.
    private fun locallyRemoveAllContactsForProvider(parentMetaGroup: MetaContactGroupImpl?, groupToRemove: ContactGroup?) {
        val childrenContacts = parentMetaGroup!!.getChildContacts()
        // first go through all direct children.
        while (childrenContacts.hasNext()) {
            val child = childrenContacts.next() as MetaContactImpl?

            // Get references to all contacts that will be removed in case we need to fire an
            // event.
            val contactsToRemove = child!!.getContactsForContactGroup(groupToRemove)
            child.removeContactsForGroup(groupToRemove)

            // if this was the last proto contact inside this meta contact, then remove the meta
            // contact as well. Otherwise only fire an event.
            if (child.getContactCount() == 0) {
                parentMetaGroup.removeMetaContact(child)
                fireMetaContactEvent(child, parentMetaGroup, MetaContactEvent.META_CONTACT_REMOVED)
            } else {
                // there are other proto contacts left in the contact child meta contact so we'll
                // have to send an event for each of the removed contacts and not only a single
                // event for the whole meta contact.
                while (contactsToRemove.hasNext()) {
                    fireProtoContactEvent(contactsToRemove.next(),
                            ProtoContactEvent.PROTO_CONTACT_REMOVED, child, null)
                }
            }
        }

        // then go through all subgroups.
        val subgroups = parentMetaGroup.getSubgroups()
        while (subgroups.hasNext()) {
            val subMetaGroup = subgroups.next() as MetaContactGroupImpl?
            val contactGroups = subMetaGroup!!.getContactGroups()
            var protoGroup: ContactGroup? = null
            while (contactGroups.hasNext()) {
                protoGroup = contactGroups.next()
                if (groupToRemove === protoGroup!!.getParentContactGroup()) locallyRemoveAllContactsForProvider(subMetaGroup, protoGroup)
            }

            // remove the group if there are no children left.
            if (subMetaGroup.countSubgroups() == 0 && subMetaGroup.countChildContacts() == 0 && subMetaGroup.countContactGroups() == 0) {
                parentMetaGroup.removeSubgroup(subMetaGroup)
                fireMetaContactGroupEvent(subMetaGroup, groupToRemove!!.getProtocolProvider(),
                        protoGroup, MetaContactGroupEvent.META_CONTACT_GROUP_REMOVED)
            }
        }
        parentMetaGroup.removeProtoGroup(groupToRemove)
    }

    /**
     * Returns the MetaContactGroup corresponding to the specified contactGroup or null if no such
     * MetaContactGroup was found.
     *
     * @param group the protocol specific `contactGroup` that we're looking for.
     * @return the MetaContactGroup corresponding to the specified contactGroup or null if no such
     * MetaContactGroup was found.
     */
    override fun findMetaContactGroupByContactGroup(group: ContactGroup?): MetaContactGroup? {
        return rootMetaGroup.findMetaContactGroupByContactGroup(group)
    }

    /**
     * Returns the MetaContact containing the specified contact or null if no such MetaContact was
     * found. The method can be used when for example we need to find the MetaContact that is the
     * author of an incoming message and the corresponding ProtocolProviderService has only
     * provided a `Contact` as its author.
     *
     * @param contact the protocol specific `contact` that we're looking for.
     * @return the MetaContact containing the specified contact or null if no such contact is
     * present in this contact list.
     */
    override fun findMetaContactByContact(contact: Contact?): MetaContact? {
        return rootMetaGroup.findMetaContactByContact(contact)
    }

    /**
     * Returns the MetaContact containing a contact with an address equal to
     * `contactAddress` and with a source provider matching `accountID`, or null if
     * no such MetaContact was found. The method can be used when for example we need to find the
     * MetaContact that is the author of an incoming message and the corresponding
     * ProtocolProviderService has only provided a `Contact` as its author.
     *
     * @param contactAddress the address of the protocol specific `contact` that we're looking for.
     * @param accountID the ID of the account that the contact we're looking for must belong to.
     * @return the MetaContact containing the specified contact or null if no such contact is
     * present in this contact list.
     */
    fun findMetaContactByContact(contactAddress: String?, accountID: String): MetaContact? {
        return rootMetaGroup.findMetaContactByContact(contactAddress, accountID)
    }

    /**
     * Returns the MetaContact that corresponds to the specified metaContactID.
     *
     * @param metaContactID a String identifier of a meta contact.
     * @return the MetaContact with the specified string identifier or null if no such meta
     * contact was found.
     */
    override fun findMetaContactByMetaUID(metaContactID: String?): MetaContact? {
        return rootMetaGroup.findMetaContactByMetaUID(metaContactID)
    }

    /**
     * Returns the MetaContactGroup that corresponds to the specified metaGroupID.
     *
     * @param metaGroupID a String identifier of a meta contact group.
     * @return the MetaContactGroup with the specified string identifier or null if no such meta
     * contact was found.
     */
    override fun findMetaContactGroupByMetaUID(metaGroupID: String?): MetaContactGroup? {
        return rootMetaGroup.findMetaContactGroupByMetaUID(metaGroupID)
    }

    /**
     * Returns a list of all `MetaContact`s containing a protocol contact from the given
     * `ProtocolProviderService`.
     *
     * @param protocolProvider the `ProtocolProviderService` whose contacts we're looking for.
     * @return a list of all `MetaContact`s containing a protocol contact from the given
     * `ProtocolProviderService`.
     */
    override fun findAllMetaContactsForProvider(protocolProvider: ProtocolProviderService?): Iterator<MetaContact?> {
        val resultList: MutableList<MetaContact?> = ArrayList()
        findAllMetaContactsForProvider(protocolProvider, rootMetaGroup, resultList)
        return resultList.iterator()
    }

    /**
     * Returns a list of all `MetaContact`s contained in the given group and containing a
     * protocol contact from the given `ProtocolProviderService`.
     *
     * @param protocolProvider the `ProtocolProviderService` whose contacts we're looking for.
     * @param metaContactGroup the parent group.
     * @return a list of all `MetaContact`s containing a protocol contact from the given
     * `ProtocolProviderService`.
     */
    override fun findAllMetaContactsForProvider(
            protocolProvider: ProtocolProviderService?, metaContactGroup: MetaContactGroup?): Iterator<MetaContact?> {
        val resultList: MutableList<MetaContact?> = LinkedList()
        findAllMetaContactsForProvider(protocolProvider, metaContactGroup, resultList)
        return resultList.iterator()
    }

    /**
     * Returns a list of all `MetaContact`s containing a protocol contact corresponding to
     * the given `contactAddress` string.
     *
     * @param contactAddress the contact address for which we're looking for a parent `MetaContact`.
     * @return a list of all `MetaContact`s containing a protocol contact corresponding to
     * the given `contactAddress` string.
     */
    override fun findAllMetaContactsForAddress(contactAddress: String?): Iterator<MetaContact?> {
        val resultList: MutableList<MetaContact?> = LinkedList()
        findAllMetaContactsForAddress(rootMetaGroup, contactAddress, resultList)
        return resultList.iterator()
    }

    /**
     * Returns a list of all `MetaContact`s containing a protocol contact corresponding to
     * the given `contactAddress` string.
     *
     * @param contactAddress the contact address for which we're looking for a parent `MetaContact`.
     * @param metaContactGroup the parent group.
     * @param resultList the list containing the result of the search.
     */
    private fun findAllMetaContactsForAddress(metaContactGroup: MetaContactGroup?,
            contactAddress: String?, resultList: MutableList<MetaContact?>) {
        val childContacts = metaContactGroup!!.getChildContacts()
        while (childContacts!!.hasNext()) {
            val metaContact = childContacts.next()
            val protocolContacts = metaContact!!.getContacts()
            while (protocolContacts.hasNext()) {
                val protocolContact = protocolContacts.next()
                if (protocolContact!!.address == contactAddress || protocolContact.displayName == contactAddress) resultList.add(metaContact)
            }
        }
        val subGroups = metaContactGroup.getSubgroups()
        while (subGroups!!.hasNext()) {
            val subGroup = subGroups.next()
            val protocolSubgroups = subGroup!!.getContactGroups()
            if (protocolSubgroups!!.hasNext()) {
                findAllMetaContactsForAddress(subGroup, contactAddress, resultList)
            }
        }
    }

    /**
     * Returns a list of all `MetaContact`s contained in the given group and containing a
     * protocol contact from the given `ProtocolProviderService`.
     *
     * @param protocolProvider the `ProtocolProviderService` whose contacts we're looking for.
     * @param metaContactGroup the parent group.
     * @param resultList the list containing the result of the search.
     */
    private fun findAllMetaContactsForProvider(protocolProvider: ProtocolProviderService?,
            metaContactGroup: MetaContactGroup?, resultList: MutableList<MetaContact?>) {
        val childContacts = metaContactGroup!!.getChildContacts()
        while (childContacts!!.hasNext()) {
            val metaContact = childContacts.next()
            val protocolContacts = metaContact!!.getContactsForProvider(protocolProvider)
            if (protocolContacts!!.hasNext()) {
                resultList.add(metaContact)
            }
        }
        val subGroups = metaContactGroup.getSubgroups()
        while (subGroups!!.hasNext()) {
            val subGroup = subGroups.next()
            val protocolSubgroups = subGroup!!.getContactGroupsForProvider(protocolProvider)
            if (protocolSubgroups!!.hasNext()) {
                findAllMetaContactsForProvider(protocolProvider, subGroup, resultList)
            }
        }
    }

    /**
     * Goes through the server stored ContactList of the specified operation set, retrieves all
     * protocol specific contacts it contains and makes sure they are all present in the local
     * contact list.
     *
     * @param presenceOpSet the presence operation set whose contact list we'd like to synchronize with the local
     * contact list.
     */
    private fun synchronizeOpSetWithLocalContactList(presenceOpSet: OperationSetPersistentPresence) {
        val rootProtoGroup = presenceOpSet.getServerStoredContactListRoot()
        if (rootProtoGroup != null) {
            Timber.log(TimberLog.FINER, "subgroups: %s; child contacts: %s", rootProtoGroup.countSubgroups(), rootProtoGroup.countContacts())
            addContactGroupToMetaGroup(rootProtoGroup, rootMetaGroup, true)
        }
        presenceOpSet.addSubscriptionListener(clSubscriptionEventHandler)
        presenceOpSet.addServerStoredGroupChangeListener(clGroupEventHandler)
    }

    /**
     * Creates meta contacts and meta contact groups for all children of the specified
     * `contactGroup` and adds them to `metaGroup`
     *
     * @param protoGroup the `ContactGroup` to add.
     *
     *
     * @param metaGroup the `MetaContactGroup` where `ContactGroup` should be added.
     * @param fireEvents indicates whether or not events are to be fired upon adding subContacts and subgroups.
     * When this method is called recursively, the parameter should will be false in order
     * to generate a minimal number of events for the whole addition and not an event per
     * every subgroup and child contact.
     */
    private fun addContactGroupToMetaGroup(protoGroup: ContactGroup?, metaGroup: MetaContactGroupImpl, fireEvents: Boolean) {
        // first register the root group
        metaGroup.addProtoGroup(protoGroup)

        // register subgroups and contacts
        val subgroupsIter = protoGroup!!.subgroups()
        while (subgroupsIter!!.hasNext()) {
            val group = subgroupsIter.next()

            // continue if we have already loaded this group from the locally stored contact list.
            if (metaGroup.findMetaContactGroupByContactGroup(group) != null) continue

            // right now we simply map this group to an existing one without being cautious and
            // verify whether we already have it registered
            val newMetaGroup = MetaContactGroupImpl(this, group!!.getGroupName()!!)
            metaGroup.addSubgroup(newMetaGroup)
            addContactGroupToMetaGroup(group, newMetaGroup, false)
            if (fireEvents) {
                fireMetaContactGroupEvent(newMetaGroup, group.getProtocolProvider(), group,
                        MetaContactGroupEvent.META_CONTACT_GROUP_ADDED)
            }
        }

        // now add all contacts, located in this group
        val contactsIter = protoGroup.contacts()
        while (contactsIter!!.hasNext()) {
            val contact = contactsIter.next()

            // continue if we have already loaded this contact from the locally stored contact
            // list.
            if (metaGroup.findMetaContactByContact(contact) != null) continue
            val newMetaContact = MetaContactImpl()
            newMetaContact.addProtoContact(contact)
            metaGroup.addMetaContact(newMetaContact)
            if (fireEvents) {
                fireMetaContactEvent(newMetaContact, metaGroup, MetaContactEvent.META_CONTACT_ADDED)
            }
        }
    }

    /**
     * Adds the specified provider to the list of currently known providers. In case the provider
     * supports persistent presence, the method would also extract all contacts and synchronize
     * them with the local contact list. Otherwise it would start a process where local
     * contacts would be added on the server.
     *
     * @param provider the ProtocolProviderService that we've just detected.
     */
    @Synchronized
    private fun handleProviderAdded(provider: ProtocolProviderService) {
        val accountUid = provider.accountID.accountUniqueID
        val accountUuid = provider.accountID.accountUuid!!
        Timber.d("Adding protocol provider %s", accountUid)
        mCurrentlyInstalledProviders[accountUid!!] = provider

        // If we have a persistent presence opSet for the provider - then retrieve its contact list
        // and merge it with the local one.
        val opSetPersPresence = provider.getOperationSet(OperationSetPersistentPresence::class.java)
        if (opSetPersPresence != null) {
            // load contacts, stored in the local contact list and corresponding to this provider.
            storageManager.extractContactsForAccount(accountUuid, accountUid)
            Timber.d("All contacts loaded for account %s", accountUid)
            synchronizeOpSetWithLocalContactList(opSetPersPresence)
        } else {
            Timber.d("Service did not have a opSetPersPresence")
        }

        /* @todo implement handling non persistent presence operation sets */

        // add a presence status listener so that we could reorder contacts upon status change.
        // NOTE that we MUST NOT add the presence listener before extracting the locally stored
        // contact list or otherwise we'll get events for all contacts that we have already extracted
        opSetPersPresence?.addContactPresenceStatusListener(this)

        // Check if the capabilities operation set is available for this contact and add a
        // listener to it in order to track capabilities' changes for all contained protocol
        // contacts.
        val capOpSet = provider.getOperationSet(OperationSetContactCapabilities::class.java)
        capOpSet?.addContactCapabilitiesListener(this)
    }

    /**
     * Removes the specified provider from the list of currently known providers and ignores all
     * the contacts that it has registered locally.
     *
     * @param pps the ProtocolProviderService that has been unregistered.
     */
    private fun handleProviderRemoved(pps: ProtocolProviderService) {
        Timber.d("Removing protocol provider %s", pps)
        val accountID = pps.accountID
        mCurrentlyInstalledProviders.remove(accountID.accountUniqueID)

        // Check if the capabilities operation set is available for this contact and remove
        // previously added listeners.
        val capOpSet = pps.getOperationSet(OperationSetContactCapabilities::class.java)
        capOpSet?.removeContactCapabilitiesListener(this)

        // get the root group for the provider so that we could remove it.
        val persPresOpSet = pps.getOperationSet(OperationSetPersistentPresence::class.java)

        // ignore if persistent presence is not supported.
        if (persPresOpSet != null) {
            // we don't care about subscription and presence status events here any longer.
            persPresOpSet.removeContactPresenceStatusListener(this)
            persPresOpSet.removeSubscriptionListener(clSubscriptionEventHandler)
            persPresOpSet.removeServerStoredGroupChangeListener(clGroupEventHandler)
            val rootGroup = persPresOpSet.getServerStoredContactListRoot()
            // iterate all sub groups and remove them one by one (we don't simply remove the root
            // group because the mcl storage manager is stupid (i wrote it) and doesn't know root
            // groups exist. that's why it needs to hear an event for every single group.)
            val subgroups = rootGroup!!.subgroups()
            while (subgroups!!.hasNext()) {
                val group = subgroups.next()
                // remove the group
                removeContactGroupFromMetaContactGroup(findMetaContactGroupByContactGroup(group) as MetaContactGroupImpl?, group, pps)
            }
            // cmeng - not allow to remove the root group
            // removeContactGroupFromMetaContactGroup(rootMetaGroup, rootGroup, pps);
        }
    }

    /**
     * Registers `group` to the event ignore list. This would make the method that is
     * normally handling events for newly created groups ignore any events for that particular
     * group and leave the responsibility to the method that added the group to the ignore list.
     *
     * @param group the name of the group that we'd like to register.
     * @param ownerProvider the protocol provider that we expect the addition to come from.
     */
    private fun addGroupToEventIgnoreList(group: String?, ownerProvider: ProtocolProviderService?) {
        // first check whether registrations in the ignore list already exist for this group.
        if (isGroupInEventIgnoreList(group, ownerProvider)) {
            return
        }
        var existingProvList = mGroupEventIgnoreList[group]
        if (existingProvList == null) {
            existingProvList = LinkedList()
        }
        existingProvList.add(ownerProvider)
        mGroupEventIgnoreList[group] = existingProvList
    }

    /**
     * Verifies whether the specified group is in the group event ignore list.
     *
     * @param group the group whose presence in the ignore list we'd like to verify.
     * @param ownerProvider the provider that `group` belongs to.
     * @return true if the group is in the group event ignore list and false otherwise.
     */
    private fun isGroupInEventIgnoreList(group: String?, ownerProvider: ProtocolProviderService?): Boolean {
        val existingProvList: List<ProtocolProviderService?>? = mGroupEventIgnoreList[group]
        return existingProvList != null && existingProvList.contains(ownerProvider)
    }

    /**
     * Removes the `group` from the group event ignore list so that events concerning this
     * group get treated.
     *
     * @param group the group whose that we'd want out of the ignore list.
     * @param ownerProvider the provider that `group` belongs to.
     */
    private fun removeGroupFromEventIgnoreList(group: String?, ownerProvider: ProtocolProviderService?) {
        // first check whether the registration actually exists.
        if (!isGroupInEventIgnoreList(group, ownerProvider)) {
            return
        }
        val existingProvList = mGroupEventIgnoreList[group]!!
        if (existingProvList.size < 1) {
            mGroupEventIgnoreList.remove(group)
        } else {
            existingProvList.remove(ownerProvider)
        }
    }

    /**
     * Registers `contact` to the event ignore list. This would make the method that is
     * normally handling events for newly created contacts ignore any events for that particular
     * contact and leave the responsibility to the method that added the contact to the ignore
     * list.
     *
     * @param contact the address of the contact that we'd like to ignore.
     * @param ownerProvider the protocol provider that we expect the addition to come from.
     */
    private fun addContactToEventIgnoreList(contact: String, ownerProvider: ProtocolProviderService?) {
        // first check whether registration is in the ignored list already exist for this contact.
        if (TextUtils.isEmpty(contact) || isContactInEventIgnoreList(contact, ownerProvider)) {
            return
        }
        var existingProvList = mContactEventIgnoreList[contact]
        if (existingProvList == null) {
            existingProvList = LinkedList()
        }
        existingProvList.add(ownerProvider)
        mContactEventIgnoreList[contact] = existingProvList
    }

    /**
     * Verifies whether the specified contact is in the contact event ignore list.
     *
     * @param contact the contact whose presence in the ignore list we'd like to verify.
     * @param ownerProvider the provider that `contact` belongs to.
     * @return true if the contact is in the contact event ignore list and false otherwise.
     */
    private fun isContactInEventIgnoreList(contact: String, ownerProvider: ProtocolProviderService?): Boolean {
        val existingProvList: List<ProtocolProviderService?>? = mContactEventIgnoreList[contact]
        return existingProvList != null && existingProvList.contains(ownerProvider)
    }

    /**
     * Verifies whether the specified contact is in the contact event ignore list. The reason we
     * need this method in addition to the one that takes a string contact address is necessary
     * for the following reason: In some cases the ID that we create a contact with
     * (e.g. mybuddy) could be different from the one returned by its getAddress() method
     * (e.g. mybuddy@hisnet.com). If this is the case we hope that the difference would be
     * handled gracefully in the equals method of the contact so we also compare with it.
     *
     * @param contact the contact whose presence in the ignore list we'd like to verify.
     * @param ownerProvider the provider that `contact` belongs to.
     * @return true if the contact is in the contact event ignore list and false otherwise.
     */
    private fun isContactInEventIgnoreList(contact: Contact, ownerProvider: ProtocolProviderService?): Boolean {
        for ((contactAddress, existingProvList) in mContactEventIgnoreList) {
            if (contact.address == contactAddress || contact.toString() == contactAddress) {
                return existingProvList != null && existingProvList.contains(ownerProvider)
            }
        }
        return false
    }

    /**
     * Removes the `contact` from the group event ignore list so that events concerning this group get treated.
     *
     * @param contact the contact whose that we'd want out of the ignore list.
     * @param ownerProvider the provider that `group` belongs to.
     */
    private fun removeContactFromEventIgnoreList(contact: String, ownerProvider: ProtocolProviderService?) {
        // first check whether the registration actually exists.
        if (TextUtils.isEmpty(contact) || !isContactInEventIgnoreList(contact, ownerProvider)) {
            return
        }
        val existingProvList = mContactEventIgnoreList[contact]
        if (existingProvList != null) {
            if (existingProvList.size < 1) {
                mGroupEventIgnoreList.remove(contact)
            } else {
                existingProvList.remove(ownerProvider)
            }
        }
    }

    /**
     * Implements the `ServiceListener` method. Verifies whether the passed event concerns
     * a `ProtocolProviderService` and modifies the list of registered protocol providers accordingly.
     *
     * @param event The `ServiceEvent` object.
     */
    override fun serviceChanged(event: ServiceEvent) {
        val pps = mBundleContext!!.getService(event.serviceReference)
        Timber.log(TimberLog.FINER, "Received a service event for: %s", pps.javaClass.name)

        // we don't care if the source service is not a protocol provider
        if (pps !is ProtocolProviderService) {
            return
        }

        Timber.d("Service is a protocol provider: %s", pps)
        val accountID = pps.accountID

        // first check if the event really means that the accounts is uninstalled/installed (or
        // is it just stopped ... e.g. we could be shutting down, or in the other case it could
        // be just modified) ... before that however, we'd need to get a reference to the service.
        var sourceFactory: ProtocolProviderFactory? = null
        val allBundleServices = event.serviceReference.bundle.registeredServices
        for (bundleServiceRef in allBundleServices) {
            val service = mBundleContext!!.getService(bundleServiceRef)
            if (service is ProtocolProviderFactory) {
                sourceFactory = service
                break
            }
        }

        if (event.type == ServiceEvent.REGISTERED) {
            Timber.d("Handling registration of a new Protocol Provider.")
            // if we have the PROVIDER_MASK property set, make sure that this provider has it and if not ignore it.
            val providerMask = System.getProperty(MetaContactListService.PROVIDER_MASK_PROPERTY)
            if (providerMask != null && providerMask.trim { it <= ' ' }.isNotEmpty()) {
                val servRefMask = event.serviceReference.getProperty(
                        MetaContactListService.PROVIDER_MASK_PROPERTY) as String
                if (servRefMask != providerMask) {
                    Timber.d("Ignoring masked provider: %s", accountID)
                    return
                }
            }

            if (sourceFactory != null && mCurrentlyInstalledProviders.containsKey(accountID.accountUniqueID)) {
                Timber.d("Modifying an existing installed account: %s", accountID)
                // the account is already installed and this event is coming from a modification.
                // we don't return here as the account is removed and added again and we must
                // create its unresolved contact and give him a chance to resolve them and not
                // fire new subscription to duplicate the already existing.
                // return;
            }
            handleProviderAdded(pps)
        } else if (event.type == ServiceEvent.UNREGISTERING) {
            // strange ... we must be shutting down. just bail
            if (sourceFactory == null) {
                return
            }

            // If the account is still registered or is just being unloaded but remains stored,
            // we remove its contacts but without storing this
            if (ContactlistActivator.accountManager!!.storedAccounts.contains(accountID)) {
                // the account is still installed it means we are modifying it. we remove all its
                // contacts from current contactList but remove the storage manager in order to
                // avoid losing those contacts from the storage as its modification later
                // unresolved contacts will be created which will be resolved from the already
                // modified account
                synchronized(this) {
                    removeMetaContactListListener(storageManager)
                    handleProviderRemoved(pps)
                    addMetaContactListListener(storageManager)
                }
                return
            }
            Timber.w("Account uninstalled. Removing all its meta contacts: %s", accountID)
            handleProviderRemoved(pps)
        }
    }

    /**
     * The class would listen for events delivered to `SubscriptionListener`s.
     */
    private inner class ContactListSubscriptionListener : SubscriptionListener {
        /**
         * Creates a meta contact for the source contact indicated by the specified
         * SubscriptionEvent, or updates an existing one if there is one. The method would also
         * generate the corresponding `MetaContactEvent`.
         *
         * @param evt the SubscriptionEvent that we'll be handling.
         */
        override fun subscriptionCreated(evt: SubscriptionEvent?) {
            Timber.log(TimberLog.FINER, "Subscription created: %s", evt)

            // ignore the event if the source contact is in the ignore list
            if (isContactInEventIgnoreList(evt!!.getSourceContact(), evt.getSourceProvider())) {
                return
            }
            val parentGroup = findMetaContactGroupByContactGroup(evt.getParentGroup()) as MetaContactGroupImpl?
            if (parentGroup == null) {
                Timber.e("Received a subscription for a group that we hadn't seen before!")
                return
            }
            val newMetaContact = MetaContactImpl()
            newMetaContact.addProtoContact(evt.getSourceContact())
            newMetaContact.setDisplayName(evt.getSourceContact().displayName)
            parentGroup.addMetaContact(newMetaContact)

            // fire the meta contact event.
            fireMetaContactEvent(newMetaContact, parentGroup, MetaContactEvent.META_CONTACT_ADDED)

            // make sure we have a local copy of the avatar;
            newMetaContact.getAvatar()
        }

        /**
         * Indicates that a contact/subscription has been moved from one server stored group to
         * another. The way we handle the event depends on whether the source
         * contact/subscription is the only proto contact found in its current MetaContact
         * encapsulator or not.
         *
         *
         * If this is the case (the source contact has no siblings in its current meta contact
         * list encapsulator) then we will move the whole meta contact to the meta contact group
         * corresponding to the new parent ContactGroup of the source contact. In this case we
         * would only fire a MetaContactMovedEvent containing the old and new parents of the
         * MetaContact in question.
         *
         *
         * If, however, the MetaContact that currently encapsulates the source contact also
         * encapsulates other proto contacts, then we will create a new MetaContact instance,
         * place it in the MetaContactGroup corresponding to the new parent ContactGroup of the
         * source contact and add the source contact inside it. In this case we would first fire a
         * metaContact added event over the empty meta contact and then, once the proto contact
         * has been moved inside it, we would also fire a ProtoContactEvent with event id
         * PROTO_CONTACT_MOVED.
         *
         *
         *
         * @param evt a reference to the SubscriptionMovedEvent containing previous and new parents as
         * well as a ref to the source contact.
         */
        override fun subscriptionMoved(evt: SubscriptionMovedEvent?) {
            Timber.d("Subscription moved: %s", evt)
            val sourceContact = evt!!.getSourceContact()

            // ignore the event if the source contact is in the ignore list
            if (isContactInEventIgnoreList(sourceContact, evt.getSourceProvider())) {
                return
            }
            val oldParentGroup = findMetaContactGroupByContactGroup(evt.getOldParentGroup()) as MetaContactGroupImpl?
            val newParentGroup = findMetaContactGroupByContactGroup(evt.getNewParentGroup()) as MetaContactGroupImpl?
            if (newParentGroup == null || oldParentGroup == null) {
                Timber.e("Received a subscription for a group that we hadn't seen before!")
                return
            }
            val currentMetaContact = findMetaContactByContact(sourceContact) as MetaContactImpl?
            if (currentMetaContact == null) {
                Timber.w(NullPointerException(), "Received a move event for a contact that is not in our contact list.")
                return
            }

            // if the move was caused by us (when merging contacts) then chances are that the
            // contact is already in the right group
            val currentParentGroup = currentMetaContact.getParentMetaContactGroup()
            if (currentParentGroup === newParentGroup) {
                return
            }

            // if the meta contact does not have other children apart from the contact that we're
            // currently moving then move the whole meta contact to the new parent group.
            if (currentMetaContact.getContactCount() == 1) {
                oldParentGroup.removeMetaContact(currentMetaContact)
                newParentGroup.addMetaContact(currentMetaContact)
                fireMetaContactEvent(MetaContactMovedEvent(currentMetaContact, oldParentGroup, newParentGroup))
            } else {
                var newMetaContact: MetaContactImpl? = null

                // first check whether a contact hasn't been already added to a metaContact
                synchronized(sourceContact) {

                    // move the proto contact and fire the corresponding event
                    currentMetaContact.removeProtoContact(sourceContact)
                    val checkContact = findMetaContactByContact(sourceContact)
                    if (checkContact == null) {
                        newMetaContact = MetaContactImpl()
                        newMetaContact!!.setDisplayName(sourceContact.displayName)
                        newParentGroup.addMetaContact(newMetaContact!!)
                        newMetaContact!!.addProtoContact(sourceContact)
                    }
                }
                // new contact was created
                if (newMetaContact != null) {
                    // fire an event notifying that a new meta contact was added.
                    fireMetaContactEvent(newMetaContact!!, newParentGroup, MetaContactEvent.META_CONTACT_ADDED)
                    fireProtoContactEvent(sourceContact, ProtoContactEvent.PROTO_CONTACT_MOVED,
                            currentMetaContact, newMetaContact)
                }
            }
        }

        override fun subscriptionFailed(evt: SubscriptionEvent?) {
            Timber.log(TimberLog.FINER, "Subscription failed: %s", evt)
        }

        /**
         * Events delivered through this method are ignored as they are of no interest to this
         * implementation of the meta contact list service.
         *
         * @param evt the SubscriptionEvent containing the source contact
         */
        override fun subscriptionResolved(evt: SubscriptionEvent?) {
            // this was a contact we already had so all we need to do is update its details
            val mc = findMetaContactByContact(evt!!.getSourceContact()) as MetaContactImpl?
            if (mc != null) {
                mc.getAvatar()
                if (mc.getContactCount() == 1 && !mc.isDisplayNameUserDefined) {
                    val oldDisplayName = mc.getDisplayName()

                    // if we have one contact, display name of metaContact haven't been changed by
                    // user and contact display name is different from metaContact's one let's
                    // change it
                    val ontact = mc.getDefaultContact()
                    val newDisplayName = ontact!!.displayName
                    if (newDisplayName != oldDisplayName) {
                        mc.setDisplayName(newDisplayName)
                        fireMetaContactEvent(MetaContactRenamedEvent(mc, oldDisplayName, newDisplayName))

                        // changing the display name has surely brought a change in the order as
                        // well so let's tell the others
                        fireMetaContactGroupEvent(findParentMetaContactGroup(mc), null, null,
                                MetaContactGroupEvent.CHILD_CONTACTS_REORDERED)
                    }
                }
            }
        }

        /**
         * In the case where the event refers to a change in the display name we compare the old
         * value with the display name of the corresponding meta contact. If they are equal this
         * means that the user has not specified their own display name for the meta contact and
         * that the display name was using this contact's display name for its own display name.
         * In this case we change the display name of the meta contact to match the new display
         * name of the proto contact.
         *
         *
         *
         * @param evt the `ContactPropertyChangeEvent` containing the source contact and the old
         * and new values of the changed property.
         */
        override fun contactModified(evt: ContactPropertyChangeEvent?) {
            val mc = findMetaContactByContact(evt!!.getSourceContact()) as MetaContactImpl?
            if (ContactPropertyChangeEvent.PROPERTY_DISPLAY_NAME == evt.propertyName) {
                if (evt.oldValue != null && evt.oldValue == mc!!.getDisplayName()) {
                    renameMetaContact(mc, evt.newValue as String, false)
                } else {
                    // we get here if the name of a contact has changed but the meta contact list
                    // is not going to reflect any change because it is not displaying that name.
                    // in this case we simply make sure everyone (e.g. the storage manager)
                    // knows about the change.
                    fireProtoContactEvent(evt.getSourceContact(), ProtoContactEvent.PROTO_CONTACT_RENAMED, mc, mc)
                }
            } else if (ContactPropertyChangeEvent.PROPERTY_IMAGE == evt.propertyName && evt.newValue != null) {
                changeMetaContactAvatar(mc, evt.getSourceContact(), evt.newValue as ByteArray)
            } else if (ContactPropertyChangeEvent.PROPERTY_PERSISTENT_DATA == evt.propertyName || ContactPropertyChangeEvent.PROPERTY_DISPLAY_DETAILS == evt.propertyName) {
                // if persistent data changed fire an event to store it
                fireProtoContactEvent(evt.getSourceContact(),
                        ProtoContactEvent.PROTO_CONTACT_MODIFIED, mc, mc)
            }
        }

        /**
         * Locates the `MetaContact` corresponding to the contact that has been removed and updates it.
         * If the removed proto contact was the last one in it, then the `MetaContact` is also removed.
         *
         * @param evt the `SubscriptionEvent` containing the contact that has been removed.
         */
        override fun subscriptionRemoved(evt: SubscriptionEvent?) {
            Timber.log(TimberLog.FINER, "Subscription removed: %s", evt)
            val metaContact = findMetaContactByContact(evt!!.getSourceContact()) as MetaContactImpl?
            val metaContactGroup = findMetaContactGroupByContactGroup(evt.getParentGroup()) as MetaContactGroupImpl?
            metaContact!!.removeProtoContact(evt.getSourceContact())

            // if this was the last protocol contact in this meta contact then remove the meta contact as well.
            if (metaContact.getContactCount() == 0) {
                metaContactGroup!!.removeMetaContact(metaContact)
                fireMetaContactEvent(metaContact, metaContactGroup, MetaContactEvent.META_CONTACT_REMOVED)
            } else {
                // this was not the las proto contact so only generate the corresponding event.
                fireProtoContactEvent(evt.getSourceContact(),
                        ProtoContactEvent.PROTO_CONTACT_REMOVED, metaContact, null)
            }
        }
    }

    /**
     * The class would listen for events delivered to `ServerStoredGroupListener`s.
     */
    private inner class ContactListGroupListener : ServerStoredGroupListener {
        /**
         * The method is called upon receiving notification that a new server stored group has been created.
         *
         * @param parent a reference to the `MetaContactGroupImpl` where `group`'s newly
         * created `MetaContactGroup` wrapper should be added as a subgroup.
         * @param group the newly added `ContactGroup`
         * @return the `MetaContactGroup` that now wraps the newly created `ContactGroup`.
         */
        private fun handleGroupCreatedEvent(parent: MetaContactGroupImpl?, group: ContactGroup?): MetaContactGroup {
            // if parent already contains a meta group with the same name, we'll
            // reuse it as the container for the new contact group.
            var newMetaGroup = parent!!.getMetaContactSubgroup(group!!.getGroupName()) as MetaContactGroupImpl?

            // if there was no meta group with the specified name, create a new one
            if (newMetaGroup == null) {
                newMetaGroup = MetaContactGroupImpl(this@MetaContactListServiceImpl, group.getGroupName()!!)
                newMetaGroup.addProtoGroup(group)
                parent.addSubgroup(newMetaGroup)
            } else {
                newMetaGroup.addProtoGroup(group)
            }

            // check if there were any subgroups
            val subgroups = group.subgroups()
            while (subgroups!!.hasNext()) {
                val subgroup = subgroups.next()
                handleGroupCreatedEvent(newMetaGroup, subgroup)
            }
            val contactsIter = group.contacts()
            while (contactsIter!!.hasNext()) {
                val contact = contactsIter.next()
                val newMetaContact = MetaContactImpl()
                newMetaContact.addProtoContact(contact)
                newMetaContact.setDisplayName(contact!!.displayName)
                newMetaGroup.addMetaContact(newMetaContact)
            }
            return newMetaGroup
        }

        /**
         * Adds the source group and its child contacts to the meta contact list.
         *
         * @param evt the ServerStoredGroupEvent containing the source group.
         */
        override fun groupCreated(evt: ServerStoredGroupEvent?) {
            Timber.log(TimberLog.FINER, "ContactGroup created: %s", evt)

            // ignore the event if the source group is in the ignore list
            if (isGroupInEventIgnoreList(evt!!.getSourceGroup().getGroupName(), evt.getSourceProvider())) {
                return
            }
            val parentMetaGroup = findMetaContactGroupByContactGroup(evt.getParentGroup()) as MetaContactGroupImpl?
            if (parentMetaGroup == null) {
                Timber.e("Failed to identify a parent where group %s should be placed.",
                        evt.getSourceGroup().getGroupName())
            }

            // check whether the meta group was already existing before adding proto-groups to it
            val isExisting = parentMetaGroup!!.getMetaContactSubgroup(evt.getSourceGroup().getGroupName()) != null

            // add parent group to the ServerStoredGroupEvent
            val newMetaGroup = handleGroupCreatedEvent(parentMetaGroup,
                    evt.getSourceGroup())

            // if this was the first contact group in the meta group fire an
            // ADDED event. otherwise fire a modification event.
            if (newMetaGroup.countContactGroups() > 1 || isExisting) {
                fireMetaContactGroupEvent(newMetaGroup, evt.getSourceProvider(),
                        evt.getSourceGroup(), MetaContactGroupEvent.CONTACT_GROUP_ADDED_TO_META_GROUP)
            } else {
                fireMetaContactGroupEvent(newMetaGroup, evt.getSourceProvider(),
                        evt.getSourceGroup(), MetaContactGroupEvent.META_CONTACT_GROUP_ADDED)
            }
        }

        /**
         * Dummy implementation.
         *
         *
         *
         * @param evt a ServerStoredGroupEvent containing the source group.
         */
        override fun groupResolved(evt: ServerStoredGroupEvent?) {
            // we couldn't care less :)
        }

        /**
         * Updates the local contact list by removing the meta contact group corresponding to the
         * group indicated by the delivered `evt`
         *
         * @param evt the ServerStoredGroupEvent confining the group that has been removed.
         */
        override fun groupRemoved(evt: ServerStoredGroupEvent?) {
            Timber.log(TimberLog.FINER, "ContactGroup removed: %s", evt)
            val metaContactGroup = findMetaContactGroupByContactGroup(evt!!.getSourceGroup()) as MetaContactGroupImpl?
            if (metaContactGroup == null) {
                Timber.e("Received a RemovedGroup event for an orphan grp: %s", evt.getSourceGroup())
                return
            }
            removeContactGroupFromMetaContactGroup(metaContactGroup, evt.getSourceGroup(), evt.getSourceProvider())
            if (metaContactGroup.countContactGroups() == 0) {
                removeMetaContactGroup(metaContactGroup)
            }
        }

        /**
         * Nothing to do here really. Oh yes .... we should actually trigger a MetaContactGroup
         * event indicating the change for interested parties but that's all.
         *
         * @param evt the ServerStoredGroupEvent containing the source group.
         */
        override fun groupNameChanged(evt: ServerStoredGroupEvent?) {
            Timber.log(TimberLog.FINER, "ContactGroup renamed: %s", evt)
            val metaContactGroup = findMetaContactGroupByContactGroup(evt!!.getSourceGroup())
            if (metaContactGroup!!.countContactGroups() == 1) {
                // if the only group contained in this group is renamed rename it
                (metaContactGroup as MetaContactGroupImpl?)!!.setGroupName(evt.getSourceGroup().getGroupName()!!)
            }
            fireMetaContactGroupEvent(metaContactGroup, evt.getSourceProvider(),
                    evt.getSourceGroup(), MetaContactGroupEvent.CONTACT_GROUP_RENAMED_IN_META_GROUP)
        }
    }

    /**
     * Creates the corresponding MetaContact event and notifies all
     * `MetaContactListListener`s that a MetaContact is added or removed from the
     * MetaContactList.
     *
     * @param sourceContact the contact that this event is about.
     * @param parentGroup the group that the source contact belongs or belonged to.
     * @param eventID the id indicating the exact type of the event to fire.
     */
    @Synchronized
    private fun fireMetaContactEvent(sourceContact: MetaContact, parentGroup: MetaContactGroup?, eventID: Int) {
        val evt = MetaContactEvent(sourceContact, parentGroup, eventID)
        Timber.log(TimberLog.FINER, "Will dispatch the following mcl event: %s", evt)
        for (listener in metaContactListListeners) {
            when (evt.getEventID()) {
                MetaContactEvent.META_CONTACT_ADDED -> listener!!.metaContactAdded(evt)
                MetaContactEvent.META_CONTACT_REMOVED -> listener!!.metaContactRemoved(evt)
                else -> Timber.e("Unknown event type %s", evt.getEventID())
            }
        }
    }

    /**
     * Gets a copy of the list of current `MetaContactListListener` interested in
     * events fired by this instance.
     *
     * @return an array of `MetaContactListListener`s currently interested in events
     * fired by this instance. The returned array is a copy of the internal listener storage and
     * thus can be safely modified.
     */
    private val metaContactListListeners: Array<MetaContactListListener?>
        get() {
            var listeners: Array<MetaContactListListener?>
            synchronized(mMetaContactListListeners) { listeners = mMetaContactListListeners.toTypedArray() }
            return listeners
        }

    /**
     * Creates the corresponding `MetaContactPropertyChangeEvent` instance and notifies all
     * `MetaContactListListener`s that a MetaContact has been modified. Synchronized to
     * avoid firing events when we are editing the account (there we temporally remove and
     * then add again the storage manager and don't want anybody to interrupt us).
     *
     * @param event the event to dispatch.
     */
    @Synchronized
    fun fireMetaContactEvent(event: MetaContactPropertyChangeEvent?) {
        Timber.log(TimberLog.FINER, "Will dispatch the following mcl property change event: %s", event)
        for (listener in metaContactListListeners) {
            when (event) {
                is MetaContactMovedEvent -> {
                    listener!!.metaContactMoved(event)
                }
                is MetaContactRenamedEvent -> {
                    listener!!.metaContactRenamed(event)
                }
                is MetaContactModifiedEvent -> {
                    listener!!.metaContactModified(event)
                }
                is MetaContactAvatarUpdateEvent -> {
                    listener!!.metaContactAvatarUpdated(event)
                }
            }
        }
    }

    /**
     * Creates the corresponding `ProtoContactEvent` instance and notifies all
     * `MetaContactListListener`s that a protocol specific `Contact` has been added
     * moved or removed. Synchronized to avoid firing events when we are editing the account (there
     * we temporally remove and then add again the storage manager and don't want anybody to
     * interrupt us).
     *
     * @param source the contact that has caused the event.
     * @param eventName One of the ProtoContactEvent.PROTO_CONTACT_XXX fields indicating the exact type of the
     * event.
     * @param oldParent the `MetaContact` that was wrapping the source `Contact` before the
     * event occurred or `null` if the event is caused by adding a new `Contact`
     * @param newParent the `MetaContact` that is wrapping the source `Contact` after the event
     * occurred or `null` if the event is caused by removing a `Contact`
     */
    @Synchronized
    private fun fireProtoContactEvent(source: Contact?, eventName: String,
            oldParent: MetaContact?, newParent: MetaContact?) {
        val event = ProtoContactEvent(source, eventName, oldParent, newParent)
        Timber.log(TimberLog.FINER, "Will dispatch the following mcl property change event: %s", event)
        for (listener in metaContactListListeners) {
            when (eventName) {
                ProtoContactEvent.PROTO_CONTACT_ADDED -> listener!!.protoContactAdded(event)
                ProtoContactEvent.PROTO_CONTACT_MOVED -> listener!!.protoContactMoved(event)
                ProtoContactEvent.PROTO_CONTACT_REMOVED -> listener!!.protoContactRemoved(event)
                ProtoContactEvent.PROTO_CONTACT_RENAMED -> listener!!.protoContactRenamed(event)
                ProtoContactEvent.PROTO_CONTACT_MODIFIED -> listener!!.protoContactModified(event)
            }
        }
    }

    /**
     * Upon each status notification this method finds the corresponding meta contact and updates
     * the ordering in its parent group.
     *
     * @param evt the ContactPresenceStatusChangeEvent describing the status change.
     */
    override fun contactPresenceStatusChanged(evt: ContactPresenceStatusChangeEvent) {
        val metaContactImpl = findMetaContactByContact(evt.getSourceContact()) as MetaContactImpl?
                ?: return

        // ignore if we have no meta contact.
        val oldContactIndex = metaContactImpl.parentGroup!!.indexOf(metaContactImpl)
        val newContactIndex = metaContactImpl.reevalContact()
        if (oldContactIndex != newContactIndex) {
            fireMetaContactGroupEvent(findParentMetaContactGroup(metaContactImpl),
                    evt.getSourceProvider(), null, MetaContactGroupEvent.CHILD_CONTACTS_REORDERED)
        }
    }

    /**
     * The method is called from the storage manager whenever a new contact group has been parsed
     * and it has to be created.
     *
     * @param parentGroup the group that contains the meta contact group we're about to load.
     * @param metaContactGroupUID the unique identifier of the meta contact group.
     * @param displayName the name of the meta contact group.
     * @return the newly created meta contact group.
     */
    fun loadStoredMetaContactGroup(parentGroup: MetaContactGroupImpl,
            metaContactGroupUID: String, displayName: String): MetaContactGroupImpl {
        // first check if the group exists already.
        var newMetaGroup = parentGroup.getMetaContactSubgroupByUID(metaContactGroupUID) as MetaContactGroupImpl?

        // if the group exists then we have already loaded it for another
        // account and we should reuse the same instance.
        if (newMetaGroup != null) return newMetaGroup
        newMetaGroup = MetaContactGroupImpl(this, displayName, metaContactGroupUID)
        parentGroup.addSubgroup(newMetaGroup)

        // I don't think this method needs to produce events since it is
        // currently only called upon initialization ... but it doesn't hurt trying
        fireMetaContactGroupEvent(newMetaGroup, null, null, MetaContactGroupEvent.META_CONTACT_GROUP_ADDED)
        return newMetaGroup
    }

    /**
     * Creates a unresolved instance of the proto specific contact group according to the
     * specified arguments and adds it to `containingMetaContactGroup`
     *
     * @param containingMetaGroup the `MetaContactGroupImpl` where the restored contact group should be added.
     * @param contactGroupUID the unique identifier of the group.
     * @param parentProtoGroup the identifier of the parent proto group.
     * @param persistentData the persistent data last returned by the contact group.
     * @param accountID the ID of the account that the proto group belongs to.
     * @return a reference to the newly created (unresolved) contact group.
     */
    fun loadStoredContactGroup(containingMetaGroup: MetaContactGroupImpl?,
            contactGroupUID: String?, parentProtoGroup: ContactGroup?, persistentData: String?, accountID: String): ContactGroup? {
        // get the presence op set
        val sourceProvider = mCurrentlyInstalledProviders[accountID]
        val presenceOpSet = sourceProvider!!.getOperationSet(OperationSetPersistentPresence::class.java)
        val newProtoGroup = presenceOpSet!!.createUnresolvedContactGroup(contactGroupUID!!, persistentData,
                parentProtoGroup ?: presenceOpSet.getServerStoredContactListRoot())
        containingMetaGroup!!.addProtoGroup(newProtoGroup)
        return newProtoGroup
    }

    /**
     * The method is called from the storage manager whenever a new contact has been parsed and it
     * has to be created.
     *
     * @param parentGroup the group contains the meta contact we're about to load.
     * @param metaUID the unique identifier of the meta contact.
     * @param displayName the display name of the meta contact.
     * @param details the details for the contact to create.
     * @param protoContacts a list containing descriptors of proto contacts encapsulated by the meta contact that
     * we're about to create.
     * @param accountID the identifier of the account that the contacts originate from.
     * @return the loaded meta contact.
     */
    fun loadStoredMetaContact(parentGroup: MetaContactGroupImpl?, metaUID: String,
            displayName: String?, details: JSONObject,
            protoContacts: List<StoredProtoContactDescriptor>, accountID: String): MetaContactImpl {
        // first check if the meta contact exists already.
        var newMetaContact = findMetaContactByMetaUID(metaUID) as MetaContactImpl?
        if (newMetaContact == null) {
            newMetaContact = MetaContactImpl(metaUID, details)
            newMetaContact.setDisplayName(displayName)
        }

        // create unresolved contacts for the protoContacts associated with this metaContact
        val sourceProvider = mCurrentlyInstalledProviders[accountID]
        val presenceOpSet = sourceProvider!!.getOperationSet(OperationSetPersistentPresence::class.java)
        for (contactDescriptor in protoContacts) {
            // this contact has already been registered by another metaContact, so we'll ignore it.
            // If this is the only contact in the meta contact, we'll throw an exception at
            // the end of the method and cause the mcl storage manager to remove it.
            val mc = findMetaContactByContact(contactDescriptor.contactAddress, accountID)
            if (mc != null) {
                Timber.w("Ignoring duplicate metaContact %s accountID = %s. Duplication in metaContact: %s",
                        contactDescriptor, accountID, mc)
                continue
            }
            val protoContact = presenceOpSet!!.createUnresolvedContact(
                    contactDescriptor.contactAddress, contactDescriptor.persistentData,
                    if (contactDescriptor.parentProtoGroup == null) presenceOpSet.getServerStoredContactListRoot() else contactDescriptor.parentProtoGroup)
            newMetaContact.addProtoContact(protoContact)
        }
        if (newMetaContact.getContactCount() == 0) {
            Timber.e("Found an empty metaContact. Throw exception for MciStorageManager to remove it!")
            throw IllegalArgumentException("MetaContact[" + newMetaContact
                    + "] contains zero non-duplicating child contacts.")
        }
        parentGroup!!.addMetaContact(newMetaContact)
        fireMetaContactEvent(newMetaContact, parentGroup, MetaContactEvent.META_CONTACT_ADDED)
        Timber.log(TimberLog.FINER, "Created meta contact: %s", newMetaContact)
        return newMetaContact
    }

    /**
     * Creates the corresponding MetaContactGroup event and notifies all
     * `MetaContactListListener`s that a MetaContactGroup is added or removed from the
     * MetaContactList. Synchronized to avoid firing events when we are editing the account
     * (there we temporally remove and then add again the storage manager and don't want anybody
     * to interrupt us).
     *
     * cmeng - have same effect in new sql implementation with single table row entry?
     * MetaContactGroupEvent.CONTACT_GROUP_REMOVED_FROM_META_GROUP:
     * MetaContactGroupEvent.META_CONTACT_GROUP_REMOVED: ==> Failed to find ...
     *
     * @param source the MetaContactGroup instance that is added to the MetaContactList
     * @param provider the ProtocolProviderService instance where this event occurred
     * @param sourceProtoGroup the proto group associated with this event or null if the event does not concern a
     * particular source group.
     * @param eventID one of the METACONTACT_GROUP_XXX static fields indicating the nature of the event.
     */
    @Synchronized
    private fun fireMetaContactGroupEvent(source: MetaContactGroup?,
            provider: ProtocolProviderService?, sourceProtoGroup: ContactGroup?, eventID: Int) {
        val evt = MetaContactGroupEvent(source, provider, sourceProtoGroup, eventID)
        Timber.log(TimberLog.FINER, "Will dispatch the following mcl event: %s", evt)
        for (listener in metaContactListListeners) {
            when (eventID) {
                MetaContactGroupEvent.META_CONTACT_GROUP_ADDED -> listener!!.metaContactGroupAdded(evt)
                MetaContactGroupEvent.META_CONTACT_GROUP_RENAMED -> listener!!.metaContactGroupModified(evt)
                MetaContactGroupEvent.META_CONTACT_GROUP_REMOVED -> listener!!.metaContactGroupRemoved(evt)
                MetaContactGroupEvent.CONTACT_GROUP_ADDED_TO_META_GROUP, MetaContactGroupEvent.CONTACT_GROUP_RENAMED_IN_META_GROUP, MetaContactGroupEvent.CONTACT_GROUP_REMOVED_FROM_META_GROUP -> listener!!.metaContactGroupModified(evt)
                MetaContactGroupEvent.CHILD_CONTACTS_REORDERED -> listener!!.childContactsReordered(evt)
                else -> Timber.e("Unknown event type (%s) for event: %s", eventID, evt)
            }
        }
    }

    /**
     * Utility class used for blocking the current thread until an event is delivered confirming
     * the creation of a particular group.
     */
    private class BlockingGroupEventRetriever
    /**
     * Creates an instance of the retriever that will wait for events confirming the creation
     * of the group with the specified name.
     *
     * groupName the name of the group whose birth we're waiting for.
     */
    (private val mGroupName: String?) : ServerStoredGroupListener {
        var mEvent: ServerStoredGroupEvent? = null

        /**
         * Called whoever an indication is received that a new server stored group is created.
         *
         * @param evt a ServerStoredGroupChangeEvent containing a reference to the newly created group.
         */
        @Synchronized
        override fun groupCreated(evt: ServerStoredGroupEvent?) {
            if (evt!!.getSourceGroup().getGroupName().equals(mGroupName)) {
                mEvent = evt
                notifyAll()
            }
        }

        /**
         * Evens delivered through this method are ignored
         *
         * @param evt param ignored
         */
        override fun groupRemoved(evt: ServerStoredGroupEvent?) {}

        /**
         * Evens delivered through this method are ignored
         *
         * @param evt param ignored
         */
        override fun groupNameChanged(evt: ServerStoredGroupEvent?) {}

        /**
         * Evens delivered through this method are ignored
         *
         * @param evt param ignored
         */
        override fun groupResolved(evt: ServerStoredGroupEvent?) {}

        /**
         * Block the execution of the current thread until either a group created event is
         * received or milliseconds pass.
         *
         * @param millis the number of ms that we should wait before we determine failure.
         */
        @Synchronized
        fun waitForEvent(millis: Long) {
            // no need to wait if an event is already there.
            if (mEvent == null) {
                try {
                    (this as Object).wait(millis)
                } catch (ex: InterruptedException) {
                    Timber.e(ex, "Interrupted while waiting for group creation")
                }
            }
        }
    }

    /**
     * Utility class used for blocking the current thread until an event is delivered confirming
     * the creation of a particular contact.
     */
    private class BlockingSubscriptionEventRetriever
    /**
     * Creates an instance of the retriever that will wait for events confirming the creation
     * of the subscription with the specified address.
     *
     * subscriptionAddress the name of the group whose birth we're waiting for.
     */
    (private val mSubscriptionAddress: String?) : SubscriptionListener, ServerStoredGroupListener {
        var mSourceContact: Contact? = null
        var mEvent: EventObject? = null

        /**
         * Events delivered through this method are ignored
         *
         * @param evt param ignored
         */
        override fun groupResolved(evt: ServerStoredGroupEvent?) {}

        /**
         * Events delivered through this method are ignored
         *
         * @param evt param ignored
         */
        override fun groupRemoved(evt: ServerStoredGroupEvent?) {}

        /**
         * Events delivered through this method are ignored
         *
         * @param evt param ignored
         */
        override fun groupNameChanged(evt: ServerStoredGroupEvent?) {}

        /**
         * Called whenever an indication is received that a new server stored group is created.
         *
         * @param evt a ServerStoredGroupEvent containing a reference to the newly created group.
         */
        @Synchronized
        override fun groupCreated(evt: ServerStoredGroupEvent?) {
            val contact: Contact? = evt!!.getSourceGroup().getContact(mSubscriptionAddress)
            if (contact != null) {
                mEvent = evt
                mSourceContact = contact
                notifyAll()
            }
        }

        /**
         * Called whenever an indication is received that a subscription is created.
         *
         * @param evt a `SubscriptionEvent` containing a reference to the newly created contact.
         */
        @Synchronized
        override fun subscriptionCreated(evt: SubscriptionEvent?) {
            if (evt!!.getSourceContact().address == mSubscriptionAddress || evt.getSourceContact().toString() == mSubscriptionAddress) {
                mEvent = evt
                mSourceContact = evt.getSourceContact()
                notifyAll()
            }
        }

        /**
         * Events delivered through this method are ignored
         *
         * @param evt param ignored
         */
        override fun subscriptionRemoved(evt: SubscriptionEvent?) {}

        /**
         * Called whenever an indication is received that a subscription creation has failed.
         *
         * @param evt a `SubscriptionEvent` containing a reference to the contact we are trying
         * to subscribe.
         */
        @Synchronized
        override fun subscriptionFailed(evt: SubscriptionEvent?) {
            if (evt!!.getSourceContact().address == mSubscriptionAddress) {
                mEvent = evt
                mSourceContact = evt.getSourceContact()
                notifyAll()
            }
        }

        /**
         * Events delivered through this method are ignored
         *
         * @param evt param ignored
         */
        override fun subscriptionMoved(evt: SubscriptionMovedEvent?) {}

        /**
         * Events delivered through this method are ignored
         *
         * @param evt param ignored
         */
        override fun subscriptionResolved(evt: SubscriptionEvent?) {}

        /**
         * Events delivered through this method are ignored
         *
         * @param evt param ignored
         */
        override fun contactModified(evt: ContactPropertyChangeEvent?) {}

        /**
         * Block the execution of the current thread until either a contact created event is
         * received or milliseconds pass.
         *
         * @param millis the number of ms to wait upon determining a failure.
         */
        @Synchronized
        fun waitForEvent(millis: Long) {
            // no need to wait if an event is already there.
            if (mEvent == null) {
                try {
                    (this as Object).wait(millis)
                } catch (ex: InterruptedException) {
                    Timber.e(ex, "Interrupted while waiting for contact creation")
                }
            }
        }
    }

    /**
     * Notify the listener that the list of the `OperationSet` capabilities of a `Contact` has changed.
     *
     * cmeng: need more work here? to handle protocol contact capability changes for
     * MciStorageManager which only taking care of persistent data.
     *
     * @param event a `ContactCapabilitiesEvent` which specifies the `Contact`
     * whose list of `OperationSet` capabilities has changed
     */
    override fun supportedOperationSetsChanged(event: ContactCapabilitiesEvent?) {
        // If the source contact not in this meta contact, we have nothing more to do here.
        val metaContactImpl = findMetaContactByContact(event!!.getSourceContact()) as MetaContactImpl?
                ?: return
        val contact = event.getSourceContact()
        metaContactImpl.updateCapabilities(contact, event.getJid(), event.getOperationSets())
        fireProtoContactEvent(contact, ProtoContactEvent.PROTO_CONTACT_MODIFIED, metaContactImpl, metaContactImpl)
    }

    companion object {
        /**
         * The number of milliseconds to wait for confirmations of account modifications before deciding to drop.
         */
        private const val CONTACT_LIST_MODIFICATION_TIMEOUT = 10000
    }
}