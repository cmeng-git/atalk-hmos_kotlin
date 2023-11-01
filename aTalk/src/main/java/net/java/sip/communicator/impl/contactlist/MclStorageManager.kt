/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.impl.contactlist

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import net.java.sip.communicator.service.contactlist.event.MetaContactAvatarUpdateEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactGroupEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactListListener
import net.java.sip.communicator.service.contactlist.event.MetaContactModifiedEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactMovedEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactRenamedEvent
import net.java.sip.communicator.service.contactlist.event.ProtoContactEvent
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ContactGroup
import org.apache.commons.lang3.StringUtils
import org.atalk.persistance.DatabaseBackend
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.osgi.framework.BundleContext
import timber.log.Timber
import java.util.*

/**
 * The class handles read / write operations over a  persistent copy of the meta contacts and
 * groups stored in SQLite tables i.e. metaContactGroup and childContacts.
 *
 * The load / resolve strategy that we use when storing contact lists is roughly the following:
 *
 * 1) The MetaContactListService is started. <br></br>
 * 2) We receive an OSGI event telling us that a new ProtocolProviderService is registered or we
 * simply retrieve one that was already in the bundle <br></br>
 * 3) We look through the database and load groups and contacts belonging to this new provider.
 * Unresolved proto groups and contacts will be created for every one of them.
 *
 * @author Eng Chong Meng
 */
class MclStorageManager : MetaContactListListener {
    /**
     * A reference to the MetaContactListServiceImpl that created and started us.
     */
    private lateinit var mclServiceImpl: MetaContactListServiceImpl
    private val mcValues = ContentValues()
    private val ccValues = ContentValues()

    /**
     * Initializes the storage manager to perform the initial loading and parsing of the
     * contacts and groups in the database
     *
     * @param bc a reference to the currently valid OSGI `BundleContext`
     * @param mclServiceImpl a reference to the currently valid instance of the `MetaContactListServiceImpl`
     * that we could use to pass parsed contacts and contact groups.
     */
    fun start(bc: BundleContext?, mclServiceImpl: MetaContactListServiceImpl) {
        this.mclServiceImpl = mclServiceImpl
        mclServiceImpl.addMetaContactListListener(this)
    }

    /**
     * Parses the contacts in childContacts table and calls corresponding "add" methods belonging
     * to `mclServiceImpl` for every metaContact and metaContactGroup stored in the
     * tables that correspond to a provider caring the specified `accountID`.
     *
     * @param accountUuid the identifier of the account whose contacts we're interested in.
     * @param accountUid a String identifier prefix with e.g. "jabber:" followed by BareJid.
     */
    fun extractContactsForAccount(accountUuid: String, accountUid: String) {
        // we don't want to receive meta contact events triggered by ourselves, so we stop
        // listening. It is possible but very unlikely that other events, not triggered by us are
        // received while we're off the channel.
        mclServiceImpl.removeMetaContactListListener(this)

        // Extract all its child groups and contacts
        processGroupContact(accountUuid, accountUid)

        // now we're done updating the contact list we can start listening again
        mclServiceImpl.addMetaContactListListener(this)
    }

    // For data base garbage clean-up during testing
    private fun mcgClean() {
        val ids = arrayOf("83")
        for (Id in ids) {
            val args = arrayOf(Id)
            mDB.delete(MetaContactGroup.TABLE_NAME, MetaContactGroup.ID + "=?", args)
            // mDB.delete(MetaContactGroup.TBL_CHILD_CONTACTS, MetaContactGroup.ID + "=?", args);
        }
    }

    /**
     * Parses `RootMetaContactGroup` and all of its proto-groups, subgroups, and
     * child-contacts creating corresponding instances through `mclServiceImpl` as
     * children of each `parentGroup`
     *
     * RootMetaContactGroup: starting point where all the group we're parsing.
     * parentGroup:　the `MetaContactGroupImpl` where we should be creating children.
     * parentProtoGroups a Map containing all proto groups that could be parents of any groups
     * parsed from the specified groupNode. The map binds UIDs to group references and may be
     * null for the top-level groups.
     *
     * @param accountUuid a String identifier i.e. prefix with "acc" of the account whose contacts we're interested in.
     * @param accountUid a String identifier prefix with e.g. "jabber:" followed by BareJid.
     */
    private fun processGroupContact(accountUuid: String, accountUid: String) {
        // This map stores all proto groups that we find in the meta group table
        val protoGroupsMap = Hashtable<String, ContactGroup>()
        val metaGroupMap = Hashtable<String, MetaContactGroupImpl>()

        // Contact details attribute = value.
        val protoContacts = LinkedList<StoredProtoContactDescriptor>()
        var parentProtoGroup: ContactGroup?

        // mcg_clean();
        // #TODO: Rename of ROOT_PROTO_GROUP_UID to "Contacts" in v2.4.0 (20200817); need to remove on later version
        mcgPatch()

        /*
         * Initialize and create the Root MetalContact Group.
         * This eliminates the need to store the first entry in metaContactGroup table in old DB design;
         * was the first entry in metaContactGroup table where the ContactGroup.ROOT_PROTO_GROUP_UID
         * is the root of the contact tree.
         */
        var metaContactGroup = mclServiceImpl.rootMetaGroup
        var protoGroupUID = ContactGroup.ROOT_PROTO_GROUP_UID
        metaGroupMap[protoGroupUID] = metaContactGroup
        var newProtoGroup = mclServiceImpl.loadStoredContactGroup(metaContactGroup, protoGroupUID,
                null, null, accountUid)
        protoGroupsMap[protoGroupUID] = newProtoGroup
        var args = arrayOf(accountUuid)
        var cursor = mDB.query(MetaContactGroup.TABLE_NAME, null,
                MetaContactGroup.ACCOUNT_UUID + "=?", args, null, null, MetaContactGroup.ID)
        while (cursor.moveToNext()) {
            val parentProtoGroupUID = cursor.getString(cursor.getColumnIndexOrThrow(MetaContactGroup.PARENT_PROTO_GROUP_UID))
            val groupUID = cursor.getString(cursor.getColumnIndexOrThrow(MetaContactGroup.MC_GROUP_UID))
            val groupName = cursor.getString(cursor.getColumnIndexOrThrow(MetaContactGroup.MC_GROUP_NAME))
            protoGroupUID = cursor.getString(cursor.getColumnIndexOrThrow(MetaContactGroup.PROTO_GROUP_UID))
            val persistentData = cursor.getString(cursor.getColumnIndexOrThrow(MetaContactGroup.PERSISTENT_DATA))
            Timber.d("### Fetching contact group: %s: %s for %s", parentProtoGroupUID, protoGroupUID, accountUuid)
            val metaGroup = metaGroupMap[parentProtoGroupUID]
            if (metaGroup != null) {
                metaContactGroup = mclServiceImpl.loadStoredMetaContactGroup(metaGroup, groupUID, groupName)
                metaGroupMap[protoGroupUID] = metaContactGroup
                parentProtoGroup = protoGroupsMap[protoGroupUID]
                newProtoGroup = mclServiceImpl.loadStoredContactGroup(metaContactGroup, protoGroupUID,
                        parentProtoGroup, persistentData, accountUid)
                protoGroupsMap[protoGroupUID] = newProtoGroup
            }
        }
        cursor.close()
        args = arrayOf(accountUuid)
        val innerJoin = (" INNER JOIN " + Contact.TABLE_NAME + " ON "
                + MetaContactGroup.TBL_CHILD_CONTACTS + "." + MetaContactGroup.CONTACT_JID + "="
                + Contact.TABLE_NAME + "." + Contact.CONTACT_JID)
        cursor = mDB.query(MetaContactGroup.TBL_CHILD_CONTACTS + innerJoin, null,
                MetaContactGroup.ACCOUNT_UUID + "=?", args, null, null, null)
        while (cursor.moveToNext()) {
            val metaUID = cursor.getString(cursor.getColumnIndexOrThrow(MetaContactGroup.MC_UID))
            protoGroupUID = cursor.getString(cursor.getColumnIndexOrThrow(MetaContactGroup.PROTO_GROUP_UID))
            val contactAddress = cursor.getString(cursor.getColumnIndexOrThrow(MetaContactGroup.CONTACT_JID))
            val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MetaContactGroup.MC_DISPLAY_NAME))
            val isDisplayNameUserDefined =  cursor.getString(cursor.getColumnIndexOrThrow(MetaContactGroup.MC_USER_DEFINED)).toBoolean()
            val persistentData = cursor.getString(cursor.getColumnIndexOrThrow(MetaContactGroup.PERSISTENT_DATA))
            var details = JSONObject()
            try {
                details = JSONObject(cursor.getString(cursor.getColumnIndexOrThrow(MetaContactGroup.MC_DETAILS)))
            } catch (e: JSONException) {
                e.printStackTrace()
            }

            // Proceed if only there is a pre-loaded protoGroup for the contact
            if (protoGroupsMap.containsKey(protoGroupUID)) {
                protoContacts.clear()
                protoContacts.add(StoredProtoContactDescriptor(contactAddress, persistentData,
                        protoGroupsMap[protoGroupUID]))
                try {
                    // pass the parsed proto contacts to the mcl service
                    val metaContactImpl = mclServiceImpl.loadStoredMetaContact(
                            metaGroupMap[protoGroupUID], metaUID, displayName, details, protoContacts, accountUid)
                    metaContactImpl.isDisplayNameUserDefined = isDisplayNameUserDefined
                } catch (ex: Throwable) {
                    // if we fail parsing a meta contact, we should remove it so that it stops causing trouble,
                    // and let other meta contacts continue to load.
                    Timber.w("Parse metaContact Exception. Proceed to remove (%s) and continue with other contacts: %s",
                            metaUID, ex.message)
                    args = arrayOf(metaUID)
                    mDB.delete(MetaContactGroup.TBL_CHILD_CONTACTS, MetaContactGroup.MC_UID + "=?", args)
                }
            }
        }
        cursor.close()
    }

    /**
     * Creates a `MetaContactGroup` and its decedents
     *
     * A metaGroup may contain:
     * a. proto-groups
     * b. subGroups (can repeat a, b and c etc)
     * c. child-contacts
     *
     * Except the rootGroup, all decedents are linked to its parent with "parent-proto-group-uid"
     * Except for rootGroup, all decedents must be owned by a specific account uuid.
     * Note: the rootGroup is created when a virgin database is first generated.
     *
     * @param mcGroup the MetaContactGroup that the new entry is to be created
     */
    private fun createMetaContactGroupEntry(mcGroup: MetaContactGroup) {
        val protoGroups = mcGroup.getContactGroups()
        while (protoGroups!!.hasNext()) {
            val protoGroup = protoGroups.next()
            createProtoContactGroupEntry(protoGroup, mcGroup)
        }

        // create the sub-groups entry
        val subgroups = mcGroup.getSubgroups()
        while (subgroups!!.hasNext()) {
            val subgroup = subgroups.next()!!
            createMetaContactGroupEntry(subgroup)
        }

        // create the child-contacts entry
        val childContacts = mcGroup.getChildContacts()
        while (childContacts!!.hasNext()) {
            val metaContact = childContacts.next()
            createMetaContactEntry(metaContact)
        }
    }

    /**
     * Creates a new `protoGroup` entry in the table
     *
     * @param protoGroup the `ContactGroup` which is to be created for
     * @param metaGroup the parent of the protoGroup
     */
    private fun createProtoContactGroupEntry(protoGroup: ContactGroup?, metaGroup: MetaContactGroup?) {
        // Do not create root group i.e. "Contacts", check of groupName == "Contacts"
        if (ContactGroup.ROOT_GROUP_NAME == metaGroup!!.getGroupName()) {
            Timber.w("Not allowed! Root group creation: %s", metaGroup.getGroupName())
            return
        }

        // Ignore if the group was created as an encapsulator of a non persistent proto group
        if (protoGroup != null && protoGroup.isPersistent()) {
            val mcgContent = ContentValues()
            val accountUuid = protoGroup.getProtocolProvider()!!.accountID.accountUuid
            mcgContent.put(MetaContactGroup.ACCOUNT_UUID, accountUuid)
            val mcGroupName = metaGroup.getGroupName()
            mcgContent.put(MetaContactGroup.MC_GROUP_NAME, mcGroupName)
            val mcGroupUid = metaGroup.getMetaUID()
            mcgContent.put(MetaContactGroup.MC_GROUP_UID, mcGroupUid)

            // Use default ContactGroup.ROOT_PROTO_GROUP_UID for all protoGroup entry
            val parentGroupUid = ContactGroup.ROOT_PROTO_GROUP_UID
            mcgContent.put(MetaContactGroup.PARENT_PROTO_GROUP_UID, parentGroupUid)
            val protoGroupUid = protoGroup.getUID()
            mcgContent.put(MetaContactGroup.PROTO_GROUP_UID, protoGroupUid)

            // add persistent data
            var persistentData = protoGroup.getPersistentData()
            if (StringUtils.isEmpty(persistentData)) persistentData = ""
            mcgContent.put(MetaContactGroup.PERSISTENT_DATA, persistentData)
            mDB.insert(MetaContactGroup.TABLE_NAME, null, mcgContent)
        }
    }

    /**
     * Creates a `metaContact` entry in the table
     *
     * @param metaContact the MetaContact that the new entry is to be created for
     */
    private fun createMetaContactEntry(metaContact: MetaContact?) {
        val contentValues = ContentValues()
        mcValues.clear()
        val mcUid = metaContact!!.getMetaUID()
        mcValues.put(MetaContactGroup.MC_UID, mcUid)
        val displayName = metaContact.getDisplayName()
        mcValues.put(MetaContactGroup.MC_DISPLAY_NAME, displayName)
        val isUserDefined = (metaContact as MetaContactImpl?)!!.isDisplayNameUserDefined
        mcValues.put(MetaContactGroup.MC_USER_DEFINED, isUserDefined.toString())
        val mcDetails = metaContact.getDetails()
        mcValues.put(MetaContactGroup.MC_DETAILS, mcDetails.toString())
        val contacts = metaContact.getContacts()
        while (contacts.hasNext()) {
            val contact = contacts.next()
            if (!contact!!.isPersistent) continue
            val accountUuid = contact.protocolProvider.accountID.accountUuid
            mcValues.put(MetaContactGroup.ACCOUNT_UUID, accountUuid)
            val protoGroupId = contact.parentContactGroup!!.getUID()
            mcValues.put(MetaContactGroup.PROTO_GROUP_UID, protoGroupId)
            val contactJid = contact.address
            mcValues.put(MetaContactGroup.CONTACT_JID, contactJid)
            val persistentData = contact.persistentData
            mcValues.put(MetaContactGroup.PERSISTENT_DATA, persistentData)
            mDB.insert(MetaContactGroup.TBL_CHILD_CONTACTS, null, mcValues)

            // Create the contact entry only if not found in contacts table
            if (findContactEntry(JABBER, contactJid) == null) {
                contentValues.clear()
                contentValues.put(Contact.CONTACT_UUID, mcUid)
                contentValues.put(Contact.PROTOCOL_PROVIDER, JABBER)
                contentValues.put(Contact.CONTACT_JID, contactJid)
                val svrDisplayName = if (isUserDefined) contactJid else displayName
                contentValues.put(Contact.SVR_DISPLAY_NAME, svrDisplayName)
                mDB.insert(Contact.TABLE_NAME, null, contentValues)
            }
        }
    }
    // ============= Event triggered handlers for MetaContactListService Implementation ===========
    /**
     * Creates a table entry for the source metaContact group, its child metaContacts and
     * associated proto-groups.
     *
     * @param evt the MetaContactListEvent containing the corresponding contact
     */
    override fun metaContactGroupAdded(evt: MetaContactGroupEvent) {
        // create metaContactGroup entry only if it is not null and has a parent associated with it
        val mcGroup = evt.getSourceMetaContactGroup()
        if (mcGroup.getParentMetaContactGroup() == null) {
            val mcGroupName = mcGroup.getGroupName()
            Timber.d("Abort metaContactGroup creation without a parent for: %s", mcGroupName)
            return
        }
        createMetaContactGroupEntry(mcGroup)
    }

    /**
     * Determines the exact type of the change and acts accordingly either updating group name
     *
     * @param evt the MetaContactListEvent containing the corresponding contact
     */
    override fun metaContactGroupModified(evt: MetaContactGroupEvent) {
        // ignore modification of non-persistent metaContactGroup
        val mcGroup = evt.getSourceMetaContactGroup()
        if (!mcGroup.isPersistent()) return

        /*
         * CONTACT_GROUP_ADDED_TO_META_GROUP not required metaContactGroup to exist - recreate
         * all new. Just logged in an internal err if metaContactGroup for modification not found
         */
        val mcGroupUid = mcGroup.getMetaUID()
        val mcGroupName = findMetaContactGroupEntry(mcGroupUid)
        if (MetaContactGroupEvent.CONTACT_GROUP_ADDED_TO_META_GROUP != evt.getEventID() && mcGroupName == null) {
            Timber.d("Debug ref only: Failed to find modifying metaContactGroup: %s", mcGroup.getGroupName())
            return
        }
        when (evt.getEventID()) {
            MetaContactGroupEvent.CONTACT_GROUP_ADDED_TO_META_GROUP -> contactGroupAddedToMetaGroup(evt)
            MetaContactGroupEvent.CONTACT_GROUP_RENAMED_IN_META_GROUP -> contactGroupRenamedInMetaGroup(evt)
            MetaContactGroupEvent.CONTACT_GROUP_REMOVED_FROM_META_GROUP -> contactGroupRemovedFromMetaGroup(evt)
            MetaContactGroupEvent.META_CONTACT_GROUP_RENAMED -> {
                mcValues.clear()
                val args = arrayOf(mcGroupUid)
                mcValues.put(MetaContactGroup.MC_GROUP_NAME, mcGroup.getGroupName())
                mDB.update(MetaContactGroup.TABLE_NAME, mcValues, MetaContactGroup.MC_GROUP_UID + "=?", args)
            }
        }
    }

    /**
     * Removes the corresponding metaContactGroup from the metaContactGroup table.
     *
     * @param evt the MetaContactGroupEvent containing the corresponding contact
     */
    override fun metaContactGroupRemoved(evt: MetaContactGroupEvent) {
        // ignore removal of non-persistent metaContactGroup
        val mcGroup = evt.getSourceMetaContactGroup() as MetaContactGroupImpl
        if (!mcGroup.isPersistent()) return

        // Just logged in an internal error if metaContactGroup not found; can happen when a
        // contact is removed - already triggered and removed in contactGroupRemovedFromMetaGroup()
        val mcGroupUid = mcGroup.getMetaUID()
        if (findMetaContactGroupEntry(mcGroupUid) == null) {
            Timber.d("Failed to find metaContactGroup for removal (may have been removed): %s", mcGroup.getGroupName())
            return
        }

        // proceed to remove metaContactGroup
        val args = arrayOf(mcGroupUid)
        mDB.delete(MetaContactGroup.TABLE_NAME, MetaContactGroup.MC_GROUP_UID + "=?", args)
    }

    /**
     * Creates table entries for the new Contact group in the mcGroup
     *
     * @param evt the MetaContactListEvent containing the corresponding contact
     */
    private fun contactGroupAddedToMetaGroup(evt: MetaContactGroupEvent?) {
        val protoGroup = evt!!.getSourceProtoGroup()
        val mcGroupImpl = evt.getSourceMetaContactGroup() as MetaContactGroupImpl
        createProtoContactGroupEntry(protoGroup, mcGroupImpl)
    }

    /**
     * Renamed of a protocol specific ContactGroup in the source MetaContactGroup. Note that
     * this does not in any way mean that the name of the MetaContactGroup itself has changed.
     * Change of the protoContactGroup name/UID is allowed if it is the only defined protoGroup;
     * If permitted, change its child contacts to the new ContactGroup Name are also required.
     *
     * `MetaContactGroup`s contain multiple protocol groups and their name cannot change
     * each time one of them is renamed.
     *
     * @param evt the MetaContactListEvent containing the corresponding contactGroup and other info.
     * @see MetaContactListServiceImpl.locallyRemoveAllContactsForProvider
     */
    private fun contactGroupRenamedInMetaGroup(evt: MetaContactGroupEvent?) {
        val mcGroup = evt!!.getSourceMetaContactGroup()
        val mcGroupUid = mcGroup.getMetaUID()
        val newProtoGroupUid = evt.getSourceProtoGroup()!!.getUID()
        val columns = arrayOf(MetaContactGroup.PROTO_GROUP_UID)
        var args = arrayOf(mcGroupUid)
        val cursor = mDB.query(MetaContactGroup.TABLE_NAME, columns,
                MetaContactGroup.MC_GROUP_UID + "=?", args, null, null, null)
        if (cursor.count != 1) {
            Timber.d("Ignore debug ref: Rename of the protoGroup is not allowed with multiple owners: %s", newProtoGroupUid)
        } else {
            cursor.moveToNext()
            val oldProtoGroupUid = cursor.getString(0)
            args = arrayOf(mcGroupUid, oldProtoGroupUid)
            mcValues.clear()
            mcValues.put(MetaContactGroup.PROTO_GROUP_UID, newProtoGroupUid)
            mDB.update(MetaContactGroup.TABLE_NAME, mcValues, MetaContactGroup.MC_GROUP_UID
                    + "=? AND " + MetaContactGroup.PROTO_GROUP_UID + "=?", args)

            // update childContacts to new protoGroupUid
            val accountUuid = evt.getSourceProvider()!!.accountID.accountUuid
            args = arrayOf(accountUuid, oldProtoGroupUid)
            mDB.update(MetaContactGroup.TBL_CHILD_CONTACTS, mcValues, MetaContactGroup.ACCOUNT_UUID
                    + "=? AND " + MetaContactGroup.PROTO_GROUP_UID + "=?", args)
        }
        cursor.close()
    }

    /**
     * Removal of a protocol specific ContactGroup in the source MetaContactGroup;
     *
     * Removal of its child contacts were already performed by mclServiceImpl prior to
     * call for contactGroup removal.
     *
     * @param evt the MetaContactListEvent containing the corresponding contactGroup and other info.
     * @see MetaContactListServiceImpl.locallyRemoveAllContactsForProvider
     */
    private fun contactGroupRemovedFromMetaGroup(evt: MetaContactGroupEvent?) {
        // Ignore if the group was created as an encapsulator of a non persistent proto group
        val protoGroup = evt!!.getSourceProtoGroup()
        if (protoGroup == null || !protoGroup.isPersistent()) return
        val accountUuid = evt.getSourceProvider()!!.accountID.accountUuid
        val mcGroupUid = evt.getSourceMetaContactGroup().getMetaUID()
        val protoGroupUid = protoGroup.getUID()
        Timber.d("Removing contact ProtoGroup: %s: %s", protoGroupUid, accountUuid)

        // Do not allow removal of root group i.e. "Contacts" or VOLATILE_GROUP or non-empty group
        if (ContactGroup.ROOT_GROUP_UID == mcGroupUid || ContactGroup.VOLATILE_GROUP == protoGroupUid || protoGroup.countContacts() > 0) {
            Timber.w("Not allowed! Group deletion for: %s (%s)", protoGroupUid, protoGroup.countContacts())
            return
        }
        var args = arrayOf(accountUuid, mcGroupUid, protoGroupUid)
        mDB.delete(MetaContactGroup.TABLE_NAME, MetaContactGroup.ACCOUNT_UUID + "=? AND "
                + MetaContactGroup.MC_GROUP_UID + "=? AND " + MetaContactGroup.PROTO_GROUP_UID + "=?", args)

        // Remove all the protoGroup orphan childContacts entry - in case not clean properly
        args = arrayOf(protoGroupUid)
        val cursor = mDB.query(MetaContactGroup.TABLE_NAME, null,
                MetaContactGroup.PROTO_GROUP_UID + "=?", args, null, null, null)
        if (cursor.count == 0) {  // found no parent
            Timber.d("Removing old protoGroup childContacts if any: %s: %s", protoGroupUid, accountUuid)
            args = arrayOf(accountUuid, protoGroupUid)
            mDB.delete(MetaContactGroup.TBL_CHILD_CONTACTS, MetaContactGroup.ACCOUNT_UUID
                    + "=? AND " + MetaContactGroup.PROTO_GROUP_UID + "=?", args)
        }
        cursor.close()
    }

    /**
     * Creates new table entry for the source metaContact, its contacts with the associated
     * protoGroups in childContacts table.
     *
     * @param evt the MetaContactListEvent containing the corresponding contact
     */
    override fun metaContactAdded(evt: MetaContactEvent) {
        // if the parent group is not persistent, do not do anything
        if (!evt.getParentGroup()!!.isPersistent()) return
        val metaContact = evt.getSourceMetaContact()
        createMetaContactEntry(metaContact)
    }

    /**
     * Changes the display name attribute of the specified meta contact node.
     *
     * @param evt the MetaContactListEvent containing the corresponding contact
     */
    override fun metaContactRenamed(evt: MetaContactRenamedEvent) {
        val metaContactImpl = evt.getSourceMetaContact() as MetaContactImpl
        val metaContactUid = metaContactImpl.getMetaUID()
        var contactJid = findMetaContactEntry(metaContactUid)

        // Just logged in an internal err if rename contact not found (non-persistent)
        if (contactJid == null) {
            Timber.d("MetaContact not found for rename: %s", metaContactImpl.getDisplayName())
            return
        }
        val oldDisplayName = evt.getOldDisplayName()
        val newDisplayName = evt.getNewDisplayName()
        if (StringUtils.isNotEmpty(newDisplayName) && newDisplayName != oldDisplayName) {
            mcValues.clear()
            mcValues.put(MetaContactGroup.MC_DISPLAY_NAME, newDisplayName)
            val isUserDefined = metaContactImpl.isDisplayNameUserDefined
            mcValues.put(MetaContactGroup.MC_USER_DEFINED, isUserDefined.toString())
            val contacts = metaContactImpl.getContacts()
            while (contacts.hasNext()) {
                val contact = contacts.next()
                contactJid = contact!!.address
                val args = arrayOf(metaContactUid, contactJid)
                val persistentData = contact.persistentData
                mcValues.put(MetaContactGroup.PERSISTENT_DATA, persistentData)
                mDB.update(MetaContactGroup.TBL_CHILD_CONTACTS, mcValues, MetaContactGroup.MC_UID
                        + "=? AND " + MetaContactGroup.CONTACT_JID + "=?", args)

                // Also update the contacts table entry if update is from server
                if (!isUserDefined) {
                    ccValues.clear()
                    ccValues.put(Contact.SVR_DISPLAY_NAME, newDisplayName)
                    mDB.update(Contact.TABLE_NAME, ccValues, Contact.CONTACT_JID + "=?", arrayOf(contactJid))
                }
            }
        }
    }

    /**
     * Indicates that a MetaContact is to be modified.
     *
     * @param evt the MetaContactModifiedEvent containing the corresponding contact
     */
    override fun metaContactModified(evt: MetaContactModifiedEvent) {
        val metaContactUid = evt.getSourceMetaContact().getMetaUID()
        val contactJid = findMetaContactEntry(metaContactUid)

        // Just logged in an internal err if rename contact not found (non-persistent)
        if (contactJid == null) {
            Timber.d("Ignore debug ref: MetaContact not found for modification: %s", evt.getSourceMetaContact())
            return
        }
        val details: JSONObject
        var jsonArray: JSONArray?
        val columns = arrayOf(MetaContactGroup.MC_DETAILS)
        val args = arrayOf(metaContactUid)
        val cursor = mDB.query(MetaContactGroup.TBL_CHILD_CONTACTS, columns,
                MetaContactGroup.MC_UID + "=?", args, null, null, null)
        val name = evt.getModificationName()
        try {
            details = JSONObject(cursor.getString(0))
            jsonArray = details.getJSONArray(name)
        } catch (e: JSONException) {
            e.printStackTrace()
            cursor.close()
            return
        }
        cursor.close()
        val oldValue = evt.oldValue
        val newValue = evt.newValue
        val jaSize = jsonArray.length()
        var isChanged = false
        try {
            // indicates add new item
            if (oldValue == null && newValue != null) {
                jsonArray.put(newValue)
            } else if (oldValue != null && newValue == null && jaSize > 0) {
                // indicates removing multiple items at one time
                if (oldValue is JSONArray) {
                    jsonArray = null
                    isChanged = true
                } else {
                    for (i in 0 until jaSize) {
                        if (oldValue == jsonArray[i]) {
                            jsonArray.remove(i)
                            isChanged = true
                            break
                        }
                    }
                }
            } else if (oldValue != null && newValue != null && jaSize > 0) {
                for (i in 0 until jaSize) {
                    if (oldValue == jsonArray[i]) {
                        jsonArray.put(i, newValue)
                        isChanged = true
                        break
                    }
                }
            }
            details.put(name, jsonArray)
        } catch (e: JSONException) {
            e.printStackTrace()
            return
        }
        if (isChanged) {
            mcValues.clear()
            mcValues.put(MetaContactGroup.MC_DETAILS, details.toString())
            mDB.update(MetaContactGroup.TBL_CHILD_CONTACTS, mcValues, MetaContactGroup.MC_UID + "=?", args)
        }
    }

    /**
     * Moves the corresponding metaContact from its old parent to the new parent metaContactGroup.
     * Create the new metaContactGroup if no exist. Leave the removal of old group with empty
     * child to user (or mclServiceImpl?)
     *
     * @param evt the MetaContactMovedEvent containing the reference move information
     */
    override fun metaContactMoved(evt: MetaContactMovedEvent) {
        val metaContact = evt.getSourceMetaContact()
        val metaContactUid = metaContact.getMetaUID()

        //		// null => case of moving from non persistent group to a persistent one.
        //		if (metaContactUid == null) {
        //			// create new metaContact Entry
        //			createMetaContactEntry(evt.getSourceMetaContact());
        //		}
        if (findMetaContactEntry(metaContactUid) == null) {
            Timber.d("MetaContact Uid cannot be null: %s", metaContact.getDisplayName())
            return
        }
        val newMCGroup = evt.getNewParent()
        val newGroupName = newMCGroup.getGroupName()
        val newGroupUid = newMCGroup.getMetaUID()

        // check if new metaContactGroup exist (give warning if none found); "Contacts" is not stored in DB
        if (ContactGroup.ROOT_GROUP_NAME != newGroupName
                && findMetaContactGroupEntry(newGroupUid) == null) {
            Timber.w("Destination mcGroup for metaContact move not found: %s", newGroupName)
        }
        mcValues.clear()
        val args = arrayOf(metaContactUid)
        mcValues.put(MetaContactGroup.PROTO_GROUP_UID, newGroupName)
        mDB.update(MetaContactGroup.TBL_CHILD_CONTACTS, mcValues, MetaContactGroup.MC_UID + "=?", args)
    }

    /**
     * Remove the corresponding metaContact from the childContacts table entry
     *
     * @param evt the MetaContactEvent containing the corresponding metaContact
     */
    override fun metaContactRemoved(evt: MetaContactEvent) {
        // ignore removal of metaContact of non-persistent parentGroup
        if (!evt.getParentGroup()!!.isPersistent()) return
        val metaContactUid = evt.getSourceMetaContact().getMetaUID()
        val contactJid = findMetaContactEntry(metaContactUid)

        // Just logged in an internal err if none is found
        if (contactJid == null) {
            Timber.d("Ignore debug ref: MetaContact not found for removal: %s", evt.getSourceMetaContact())
            return
        }

        // remove the meta contact entry.
        var args = arrayOf(metaContactUid)
        mDB.delete(MetaContactGroup.TBL_CHILD_CONTACTS, MetaContactGroup.MC_UID + "=?", args)

        // cmeng - need to remove from contacts table if not found in childContacts table
        if (findProtoContactEntry(null, contactJid) == 0) {
            args = arrayOf(JABBER, contactJid)
            mDB.delete(Contact.TABLE_NAME, Contact.PROTOCOL_PROVIDER + "=? AND "
                    + Contact.CONTACT_JID + "=?", args)
        }
    }

    /**
     * Indicates that a protocol specific `Contact` instance has been added to the list of
     * protocol specific buddies in this `MetaContact`
     *
     * Creates a table entry corresponding to `Contact`.
     *
     * @param evt a reference to the corresponding `ProtoContactEvent`
     */
    override fun protoContactAdded(evt: ProtoContactEvent) {
        val metaContact = evt.getParent()
        val mcUid = metaContact!!.getMetaUID()
        val contact = evt.getProtoContact()
        val contactJid = contact.address

        // Just logged in an internal err if the new contactJid already exist
        if (findProtoContactEntry(mcUid, contactJid) == 0) {
            Timber.d("Abort create new to an existing protoContact: %s", contactJid)
            return
        }

        // Abort if contact does not have a parent group defined.
        val parentGroup = contact.parentContactGroup
        if (parentGroup == null) {
            Timber.d("Abort entry creation, contact does not have a parent: %s", contact)
            return
        }
        mcValues.clear()
        mcValues.put(MetaContactGroup.MC_UID, mcUid)
        val accountUuid = contact.protocolProvider.accountID.accountUuid
        mcValues.put(MetaContactGroup.ACCOUNT_UUID, accountUuid)
        val protoGroupId = parentGroup.getUID()
        mcValues.put(MetaContactGroup.PROTO_GROUP_UID, protoGroupId)
        mcValues.put(MetaContactGroup.CONTACT_JID, contactJid)
        val displayName = metaContact.getDisplayName()
        mcValues.put(MetaContactGroup.MC_DISPLAY_NAME, displayName)
        val mcDetails = metaContact.getDetails()
        mcValues.put(MetaContactGroup.MC_DETAILS, mcDetails.toString())
        val persistentData = contact.persistentData
        mcValues.put(MetaContactGroup.PERSISTENT_DATA, persistentData)
        mDB.insert(MetaContactGroup.TBL_CHILD_CONTACTS, null, mcValues)

        // Create the contact entry only if not found in contacts table
        if (findContactEntry(JABBER, contactJid) == null) {
            mcValues.clear()
            mcValues.put(Contact.CONTACT_UUID, mcUid)
            mcValues.put(Contact.PROTOCOL_PROVIDER, JABBER)
            mcValues.put(Contact.CONTACT_JID, contactJid)
            mcValues.put(Contact.SVR_DISPLAY_NAME, contact.displayName)
            mDB.insert(Contact.TABLE_NAME, null, mcValues)
        }
    }

    /**
     * Updates the displayName for the contact that caused this event.
     *
     * @param evt the ProtoContactEvent containing the corresponding contact
     */
    override fun protoContactRenamed(evt: ProtoContactEvent) {
        // Just logged in an internal err if rename contact not found
        val contact = evt.getProtoContact()
        if (findContactEntry(JABBER, contact.address) == null) {
            val metaContact = evt.getParent()
            val metaContactUid = metaContact!!.getMetaUID()
            Timber.d("Ignore debug info: ProtoContact not found for modification: %s for: %s",
                    evt.getParent(), metaContactUid)
            return
        }

        // update the svrDisplayName for the specific contact
        val svrDisplayName = contact.displayName
        if (StringUtils.isNotEmpty(svrDisplayName)) {
            val args = arrayOf(JABBER, contact.address)
            mcValues.clear()
            mcValues.put(Contact.SVR_DISPLAY_NAME, svrDisplayName)
            mDB.update(Contact.TABLE_NAME, mcValues, Contact.PROTOCOL_PROVIDER
                    + "=? AND " + Contact.CONTACT_JID + "=?", args)
        }
    }

    /**
     * Updates the data stored for the contact that caused this event. The changes can either be
     * persistent data change etc
     *
     * @param evt the ProtoContactEvent containing the corresponding contact
     */
    override fun protoContactModified(evt: ProtoContactEvent) {
        val metaContact = evt.getParent()
        val metaContactUid = metaContact!!.getMetaUID()
        val contactJid = findMetaContactEntry(metaContactUid)

        // Just logged in an internal err if rename contact not found
        if (contactJid == null) {
            Timber.d("Ignore debug ref: ProtoContact not found for modification: %s for: %s", evt.getParent(), metaContactUid)
            return
        }

        // update the persistent data for the the specific contact
        mcValues.clear()
        val contact = evt.getProtoContact()
        val persistentData = contact.persistentData
        if (StringUtils.isNotEmpty(persistentData)) {
            val contactAddress = contact.address
            val args = arrayOf(metaContactUid, contactAddress)
            mcValues.put(MetaContactGroup.PERSISTENT_DATA, persistentData)
            mDB.update(MetaContactGroup.TBL_CHILD_CONTACTS, mcValues, MetaContactGroup.MC_UID
                    + "=? AND " + MetaContactGroup.CONTACT_JID + "=?", args)
        }
    }

    /**
     * Indicates that a protocol specific `Contact` instance has been moved from within one
     * `MetaContact` to another.
     *
     * @param evt a reference to the `ProtoContactMovedEvent` instance.
     */
    override fun protoContactMoved(evt: ProtoContactEvent) {
        val oldMcUid = evt.getOldParent().getMetaUID()
        val contactJid = evt.getProtoContact().address

        // Just logged in an internal err if the contactJid of the oldMcUid entry not found
        if (findProtoContactEntry(oldMcUid, contactJid) == 0) {
            Timber.d("Failed to find the metaContact for moving: %s", contactJid)
            return
        }
        val newMcUid = evt.getNewParent()!!.getMetaUID()
        val groupName = findMetaContactEntry(newMcUid)
        if (groupName == null) {
            Timber.d("Failed to find new destination metaContactGroup for: %s", newMcUid)
            return
        }

        // Just modified the groupName of old contact
        val args = arrayOf(oldMcUid, contactJid)
        mcValues.clear()
        mcValues.put(MetaContactGroup.MC_GROUP_NAME, groupName)
        mDB.update(MetaContactGroup.TBL_CHILD_CONTACTS, mcValues, MetaContactGroup.MC_UID
                + "=? AND " + MetaContactGroup.CONTACT_JID + "=?", args)
    }

    /**
     * Remove the contact in the metaContact entry from the childContacts table;
     * also the contact entry in contacts table if none found in childContacts after removal.
     *
     * Note: Both the contact chatSession and its associated chat messages are left in the DB
     * User may remove this in ChatSessionFragment when an invalid entity is selected.
     *
     * @param evt a reference to the corresponding `ProtoContactEvent`
     */
    override fun protoContactRemoved(evt: ProtoContactEvent) {
        val mcUid = evt.getParent()!!.getMetaUID()
        val contactJid = evt.getProtoContact().address

        // Just logged in an internal err if the contactJid of the mcUid entry not found
        if (findProtoContactEntry(mcUid, contactJid) == 0) {
            Timber.d("Failed to find the protoContact for removal: %s", contactJid)
            return
        }
        var args = arrayOf(mcUid, contactJid)
        mDB.delete(MetaContactGroup.TBL_CHILD_CONTACTS, MetaContactGroup.MC_UID + "=? AND "
                + MetaContactGroup.CONTACT_JID + "=?", args)

        // cmeng - need to remove from contacts if none found in contactList
        if (findProtoContactEntry(null, contactJid) == 0) {
            args = arrayOf(JABBER, contactJid)
            mDB.delete(Contact.TABLE_NAME, Contact.PROTOCOL_PROVIDER + "=? AND "
                    + Contact.CONTACT_JID + "=?", args)
        }
    }

    /**
     * Return the unique contact UUID corresponding to the contact with the specified protocol
     * provider or null if no such entry was found in the contacts table.
     *
     * @param protocolProvider the protocol provider of contact whose UUID we are looking for.
     * @param jid the jid String of the contact whose UUID we are looking for.
     * @return the contact UUID corresponding to the contact with the specified protocol provider
     * and Jid or null if no such contact was found in the contact table.
     */
    private fun findContactEntry(protocolProvider: String, jid: String?): String? {
        val columns = arrayOf(Contact.CONTACT_UUID)
        val args = arrayOf(protocolProvider, jid)
        val cursor = mDB.query(Contact.TABLE_NAME, columns, Contact.PROTOCOL_PROVIDER
                + "=? AND " + Contact.CONTACT_JID + "=?", args, null, null, null)
        var contactUuid: String? = null
        while (cursor.moveToNext()) {
            contactUuid = cursor.getString(0)
        }
        cursor.close()
        return contactUuid
    }

    /**
     * Return the number of entries in the childContacts table with the given contactJid AND
     * metaContact Uuid if given i.e. non-null)
     *
     * @param contactJid the contact Jid we are looking for.
     * @return the number of entry with the given contactJid.
     */
    private fun findProtoContactEntry(mcUid: String?, contactJid: String?): Int {
        val cursor: Cursor
        var args = arrayOf(contactJid)
        if (StringUtils.isEmpty(mcUid)) {
            cursor = mDB.query(MetaContactGroup.TBL_CHILD_CONTACTS, null,
                    MetaContactGroup.CONTACT_JID + "=?", args, null, null, null)
        } else {
            args = arrayOf(mcUid, contactJid)
            cursor = mDB.query(MetaContactGroup.TBL_CHILD_CONTACTS, null,
                    MetaContactGroup.MC_UID + "=? AND " + MetaContactGroup.CONTACT_JID + "=?",
                    args, null, null, null)
        }
        val count = cursor.count
        cursor.close()
        return count
    }

    /**
     * Return the contactJid corresponding to the specified metaContact uid or null if none is found.
     * Note: each metaContactUid may contain more than one contactJid entry in the table.
     * However current aTalk implementation is that metaContact has only single contactJid entry.
     *
     * @param metaContactUID the UID String of the metaContact whose contactJid we are looking for.
     * @return the contactJid corresponding to the metaContact with the specified UID or null if no
     * such metaContactUid entry was found in the metaContactGroup table.
     */
    private fun findMetaContactEntry(metaContactUID: String?): String? {
        val columns = arrayOf(MetaContactGroup.CONTACT_JID)
        val args = arrayOf(metaContactUID)
        val cursor = mDB.query(MetaContactGroup.TBL_CHILD_CONTACTS, columns,
                MetaContactGroup.MC_UID + "=?", args, null, null, null)
        var contactJid: String? = null
        while (cursor.moveToNext()) {
            contactJid = cursor.getString(0)
        }
        cursor.close()
        return contactJid
    }

    /**
     * Return the metaContact GroupName corresponding to the metaContactGroup with the specified
     * uid or null if no found.
     *
     * @param metaContactGroupUID the metaContactGroup UID whose groupName we are looking for.
     * @return the metaContact GroupName corresponding to the metaContactGroup with the
     * specified UID or null if no such group was found in the metaContactGroup table.
     */
    private fun findMetaContactGroupEntry(metaContactGroupUID: String?): String? {
        val columns = arrayOf(MetaContactGroup.MC_GROUP_NAME)
        val args = arrayOf(metaContactGroupUID)
        val cursor = mDB.query(MetaContactGroup.TABLE_NAME, columns,
                MetaContactGroup.MC_GROUP_UID + "=?", args, null, null, null)
        var mcGroupName: String? = null
        while (cursor.moveToNext()) {
            mcGroupName = cursor.getString(0)
        }
        cursor.close()
        return mcGroupName
    }

    /**
     * Contains details parsed out of the database, necessary for creating unresolved contacts.
     */
    class StoredProtoContactDescriptor(var contactAddress: String, var persistentData: String?, var parentProtoGroup: ContactGroup?) {
        /**
         * Returns a string representation of the descriptor.
         *
         * @return a string representation of the descriptor.
         */
        override fun toString(): String {
            return ("StoredProtocolContactDescriptor[ " + " contactAddress=" + contactAddress
                    + " persistentData=" + persistentData + " parentProtoGroup="
                    + (if (parentProtoGroup == null) "" else parentProtoGroup!!.getGroupName()) + "]")
        }

        companion object {
            /**
             * Utility method that allows us to verify whether a ContactDescriptor corresponding to a
             * particular contact is already in a descriptor list and thus eliminate duplicates.
             *
             * @param contactAddress the address of the contact whose descriptor we are looking for.
             * @param list the `List` of `StoredProtoContactDescriptor` that we are supposed to
             * search for `contactAddress`
             * @return a `StoredProtoContactDescriptor` corresponding to
             * `contactAddress` or `null` if no such descriptor exists.
             */
            private fun findContactInList(contactAddress: String,
                    list: List<StoredProtoContactDescriptor>?): StoredProtoContactDescriptor? {
                if (list != null && list.isNotEmpty()) {
                    for (desc in list) {
                        if (desc.contactAddress == contactAddress) return desc
                    }
                }
                return null
            }
        }
    }

    /**
     * We simply ignore - we're not interested in this kind of events.
     *
     * @param evt the `MetaContactGroupEvent` containing details of this event.
     */
    override fun childContactsReordered(evt: MetaContactGroupEvent) {
        // ignore - not interested in such kind of events
    }

    /**
     * Indicates that a new avatar is available for a `MetaContact`.
     *
     * @param evt the `MetaContactAvatarUpdateEvent` containing details of this event
     */
    override fun metaContactAvatarUpdated(evt: MetaContactAvatarUpdateEvent) {
        // TODO Store MetaContact avatar.
    }

    companion object {
        const val JABBER = "Jabber"

        /**
         * The property to enable multi tenant mode. When changing profiles/accounts the table
         * can be filled with groups and contacts from protocol provider we do not know about. This
         * mode will prevent loading empty and groups we do not know about.
         */
        private const val MULTI_TENANT_MODE_PROP = "contactlist.MULTI_TENANT_MODE"
        private val mDB = DatabaseBackend.writableDB

        // #TODO: Rename of ROOT_PROTO_GROUP_UID to "Contacts" in v2.4.0 (20200817); need to remove on later version
        fun mcgPatch() {
            // Remove table row: ContactGroup.ROOT_GROUP_UID in Table metaContactGroup
            var args = arrayOf<String?>(ContactGroup.ROOT_GROUP_UID)
            mDB.delete(MetaContactGroup.TABLE_NAME, MetaContactGroup.MC_GROUP_UID + "=?", args)

            // Rename all "ContactListRoot" to "Contacts" in Table metaContactGroup
            args = arrayOf("ContactListRoot")
            val values = ContentValues()
            values.put(MetaContactGroup.PARENT_PROTO_GROUP_UID, ContactGroup.ROOT_PROTO_GROUP_UID)
            mDB.update(MetaContactGroup.TABLE_NAME, values,
                    MetaContactGroup.PARENT_PROTO_GROUP_UID + "=?", args)

            // Rename all "ContactListRoot" to "Contacts" in Table childContacts
            values.clear()
            values.put(MetaContactGroup.PROTO_GROUP_UID, ContactGroup.ROOT_PROTO_GROUP_UID)
            mDB.update(MetaContactGroup.TBL_CHILD_CONTACTS, values,
                    MetaContactGroup.PROTO_GROUP_UID + "=?", args)
        }

        /**
         * Get the metaUuid for the given accountUuid and contactJid; start Chat session in muc.
         *
         * @param accountUuid the protocol user AccountUuid
         * @param contactJid ContactJid associated with the user account
         *
         * @return the metaUuid for start ChatActivity
         */
        fun getMetaUuid(accountUuid: String, contactJid: String): String? {
            val columns = arrayOf(MetaContactGroup.MC_UID)
            val args = arrayOf(accountUuid, contactJid)
            val cursor = mDB.query(MetaContactGroup.TBL_CHILD_CONTACTS, columns,
                    MetaContactGroup.ACCOUNT_UUID + "=? AND " + MetaContactGroup.CONTACT_JID + "=?",
                    args, null, null, null)
            var metaUuid: String? = null
            while (cursor.moveToNext()) {
                metaUuid = cursor.getString(0)
            }
            cursor.close()
            return metaUuid
        }
    }
}