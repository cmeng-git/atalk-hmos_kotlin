/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.atalk.hmos.gui.contactlist

import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.contactsource.SourceContact
import net.java.sip.communicator.service.gui.ContactListFilter
import net.java.sip.communicator.service.gui.FilterQuery
import net.java.sip.communicator.service.gui.UIContact
import net.java.sip.communicator.service.gui.UIGroup
import net.java.sip.communicator.service.gui.event.MetaContactQuery
import net.java.sip.communicator.service.gui.event.MetaContactQueryStatusEvent
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.util.ConfigurationUtils
import org.atalk.hmos.gui.AndroidGUIActivator

/**
 * The `PresenceFilter` is used to filter offline contacts from the contact list.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class PresenceFilter : ContactListFilter {
    /**
     * Indicates if this presence filter shows or hides the offline contacts.
     */
    private var isShowOffline: Boolean

    /**
     * Creates an instance of `PresenceFilter`.
     */
    init {
        isShowOffline = ConfigurationUtils.isShowOffline()
    }

    /**
     * Applies this filter. This filter is applied over the `MetaContactListService`.
     *
     * @param filterQuery the query which keeps track of the filtering results
     */
    override fun applyFilter(filterQuery: FilterQuery?) {
        // Create the query that will track filtering.
        val query = MetaContactQuery()

        // Add this query to the filterQuery.
        filterQuery!!.addContactQuery(query)
        val filterSources = AndroidGUIActivator.contactSources
        var maxIndex = 0
        for (filterSource in filterSources) {
            val currIx = filterSource.index
            if (maxIndex < currIx) maxIndex = currIx
        }

        //		contactListAdapter.getMetaContactListSource().setIndex(maxIndex + 1);
        //		for (ContactSourceService filterSource : filterSources) {
        //			filterSource.setContactSourceIndex(filterSource.getIndex());
        //			ContactSourceService sourceService = filterSource.getContactSourceService();
        //
        //			ContactQuery contactQuery = sourceService.createContactQuery(null);
        //			if (contactQuery == null)
        //				continue;
        //
        //			// Add this query to the filterQuery.
        //			filterQuery.addContactQuery(contactQuery);
        //			// contactQuery.addContactQueryListener(contactList);
        //			contactQuery.start();
        //		}

        // Closes this filter to indicate that we finished adding queries to it.
        filterQuery.close()

        // query.addContactQueryListener(AndroidGUIActivator.getContactList());
        val resultCount = 0
        addMatching(AndroidGUIActivator.contactListService.getRoot(), query, resultCount)
        query.fireQueryEvent(if (query.isCanceled) MetaContactQueryStatusEvent.QUERY_CANCELED else MetaContactQueryStatusEvent.QUERY_COMPLETED)
    }

    /**
     * Indicates if the given `uiContact` is matching this filter.
     *
     * @param uiContact the `UIContact` to check
     * @return `true` if the given `uiContact` is matching this filter, otherwise returns `false`
     */
    override fun isMatching(uiContact: UIContact?): Boolean {
        val descriptor = uiContact!!.descriptor
        return if (descriptor is MetaContact) isMatching(descriptor) else if (descriptor is SourceContact) isMatching(descriptor) else false
    }

    /**
     * Indicates if the given `uiGroup` is matching this filter.
     *
     * @param uiGroup the `UIGroup` to check
     * @return `true` if the given `uiGroup` is matching this filter, otherwise returns `false`
     */
    override fun isMatching(uiGroup: UIGroup?): Boolean {
        val descriptor = uiGroup!!.descriptor
        return if (descriptor is MetaContactGroup) isMatching(descriptor) else false
    }

    /**
     * Sets the show offline property.
     *
     * @param isShowOffline indicates if offline contacts are shown
     */
    fun setShowOffline(isShowOffline: Boolean) {
        this.isShowOffline = isShowOffline
        ConfigurationUtils.setShowOffline(isShowOffline)
    }

    /**
     * Returns `true` if offline contacts are shown, otherwise returns `false`.
     *
     * @return `true` if offline contacts are shown, otherwise returns `false`
     */
    fun isShowOffline(): Boolean {
        return isShowOffline
    }

    /**
     * Returns `true` if offline contacts are shown or if the given `MetaContact` is online,
     * otherwise returns false.
     *
     * @param metaContact the `MetaContact` to check
     * @return `true` if the given `MetaContact` is matching this filter
     */
    fun isMatching(metaContact: MetaContact): Boolean {
        return isShowOffline || isContactOnline(metaContact)
    }

    /**
     * Returns `true` if offline contacts are shown or if the given `MetaContact` is online,
     * otherwise returns false.
     *
     * @param contact the `MetaContact` to check
     * @return `true` if the given `MetaContact` is matching this filter
     */
    private fun isMatching(contact: SourceContact): Boolean {
        // make sure we always show chat rooms and recent messages
        return (isShowOffline
                || contact.presenceStatus!!.isOnline) || contact.contactSource.type == ContactSourceService.CONTACT_LIST_TYPE
    }

    /**
     * Returns `true` if offline contacts are shown or if the given `MetaContactGroup`
     * contains online contacts.
     *
     * @param metaGroup the `MetaContactGroup` to check
     * @return `true` if the given `MetaContactGroup` is matching this filter
     */
    fun isMatching(metaGroup: MetaContactGroup): Boolean {
        return metaGroup.countChildContacts() > 0 && (isShowOffline || metaGroup.countOnlineChildContacts() > 0)
    }

    /**
     * Returns `true` if the given meta contact is online, `false` otherwise.
     *
     * @param contact the meta contact
     * @return `true` if the given meta contact is online, `false` otherwise
     */
    private fun isContactOnline(contact: MetaContact): Boolean {
        // If for some reason the default contact is null we return false.
        val defaultContact = contact.getDefaultContact() ?: return false

        // Lays on the fact that the default contact is the most connected.
        return defaultContact.presenceStatus.status >= PresenceStatus.ONLINE_THRESHOLD
    }

    /**
     * Adds all contacts contained in the given `MetaContactGroup` matching the current filter
     * and not contained in the contact list.
     *
     * @param metaGroup the `MetaContactGroup`, which matching contacts to add
     * @param query the `MetaContactQuery` that notifies interested listeners of the results of this matching
     * @param resultCount the initial result count we would insert directly to the contact list without firing events
     */
    private fun addMatching(metaGroup: MetaContactGroup, query: MetaContactQuery, resultCount: Int) {
        val childContacts = metaGroup.getChildContacts()

        //		while (childContacts.hasNext() && !query.isCanceled()) {
        //			MetaContact metaContact = childContacts.next();

        //			if (isMatching(metaContact)) {
        //				resultCount++;
        //				if (resultCount <= INITIAL_CONTACT_COUNT) {
        //					UIGroup uiGroup = null;

        //					if (!MetaContactListSource.isRootGroup(metaGroup)) {
        //						synchronized (metaGroup) {
        //							uiGroup = MetaContactListSource.getUIGroup(metaGroup);
        //							if (uiGroup == null)
        //								uiGroup = MetaContactListSource.createUIGroup(metaGroup);
        //						}
        //					}
        //
        //						Timber.d("Presence filter contact added: " + metaContact.getDisplayName());
        //
        //					UIContactImpl newUIContact;
        //					synchronized (metaContact) {
        //						newUIContact = MetaContactListSource.getUIContact(metaContact);
        //						if (newUIContact == null) {
        //							newUIContact = MetaContactListSource.createUIContact(metaContact);
        //						}
        //					}

        // AndroidGUIActivator.getContactList().addContact(newUIContact, uiGroup, true, true);
        //					query.setInitialResultCount(resultCount);
        //				}
        //				else {
        //					query.fireQueryEvent(metaContact);
        //				}
        //			}
        //		}

        // If in the meantime the filtering has been stopped we return here.
        if (query.isCanceled) return
        val subgroups = metaGroup.getSubgroups()
        while (subgroups!!.hasNext() && !query.isCanceled) {
            val subgroup = subgroups.next()

            //			if (isMatching(subgroup)) {
            //				UIGroup uiGroup;
            //				synchronized (subgroup) {
            //					uiGroup = MetaContactListSource.getUIGroup(subgroup);
            //					if (uiGroup == null)
            //						uiGroup = MetaContactListSource.createUIGroup(subgroup);
            //				}
            //
            //				AndroidGUIActivator.getContactList().addGroup(uiGroup, true);
            //				addMatching(subgroup, query, resultCount);
            //			}
        }
    }

    companion object {
        /**
         * The initial result count below which we insert all filter results directly to the contact list
         * without firing events.
         */
        private const val INITIAL_CONTACT_COUNT = 30
    }
}