/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.contactlist.model

import android.os.Handler
import net.java.sip.communicator.service.contactsource.ContactChangedEvent
import net.java.sip.communicator.service.contactsource.ContactQuery
import net.java.sip.communicator.service.contactsource.ContactQueryListener
import net.java.sip.communicator.service.contactsource.ContactQueryStatusEvent
import net.java.sip.communicator.service.contactsource.ContactReceivedEvent
import net.java.sip.communicator.service.contactsource.ContactRemovedEvent
import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.contactsource.SourceContact
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.contactlist.ContactListFragment
import org.atalk.service.osgi.OSGiActivity
import timber.log.Timber

/**
 * Class implements adapter that can be used to search contact sources and the contact list. Meta contact list
 * is a base for this adapter and queries returned from contact sources are appended as next contact groups.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class QueryContactListAdapter
/**
 * Creates new instance of `QueryContactListAdapter`.
 *
 * @param fragment parent fragment.
 * @param contactListModel meta contact list model used as a base data model
 */
(fragment: ContactListFragment,
        /**
         * The meta contact list used as a base contact source. It is capable of filtering contacts itself without queries.
         */
        private val metaContactList: MetaContactListAdapter) : BaseContactListAdapter(fragment, true), UIGroupRenderer, ContactQueryListener {

    /**
     * Handler used to execute stuff on UI thread.
     */
    override val uiHandler = OSGiActivity.uiHandler

    /**
     * List of contact sources of type [ContactSourceService.SEARCH_TYPE].
     */
    private var sources: List<ContactSourceService>? = null

    /**
     * List of results groups. Each group corresponds to results from one contact source.
     */
    private var results = ArrayList<ResultGroup>()

    /**
     * List of queries currently handled.
     */
    private val queries = ArrayList<ContactQuery>()

    /**
     * Returns a list of all registered contact sources.
     *
     * @return a list of all registered contact sources
     */
    private fun getSources(): List<ContactSourceService> {
        val serRefs = ServiceUtils.getServiceReferences(AndroidGUIActivator.bundleContext!!, ContactSourceService::class.java)
        val contactSources = ArrayList<ContactSourceService>(serRefs.size)
        for (serRef in serRefs) {
            val contactSource = AndroidGUIActivator.bundleContext!!.getService(serRef) as ContactSourceService
            if (contactSource.type == ContactSourceService.SEARCH_TYPE) {
                contactSources.add(contactSource)
            }
        }
        return contactSources
    }

    /**
     * {@inheritDoc}
     */
    override fun initModelData() {
        sources = getSources()
    }

    /**
     * {@inheritDoc}
     */
    override fun dispose() {
        super.dispose()
        cancelQueries()
    }

    override fun getGroupCount(): Int {
        return metaContactList.groupCount + results.size
    }

    override fun getGroup(position: Int): Any? {
        val metaGroupCount = metaContactList.groupCount
        return if (position in 0 until metaGroupCount) {
            metaContactList.getGroup(position)
        } else {
            null
        }
    }

    public override fun getGroupRenderer(groupPosition: Int): UIGroupRenderer {
        return if (groupPosition < metaContactList.groupCount) {
            metaContactList.getGroupRenderer(groupPosition)
        } else {
            this
        }
    }

    override fun getChildrenCount(groupPosition: Int): Int {
        val metaGroupCount = metaContactList.groupCount
        return if (groupPosition < metaGroupCount) {
            metaContactList.getChildrenCount(groupPosition)
        } else {
            if (results.size == 0) 0 else results[groupPosition - metaGroupCount].getCount()
        }
    }

    override fun getChild(groupPosition: Int, childPosition: Int): Any? {
        val metaGroupCount = metaContactList.groupCount
        return if (groupPosition < metaGroupCount) {
            metaContactList.getChild(groupPosition, childPosition)
        } else {
            if (results.size == 0) return null
            val contacts = results[0].contacts
            if (childPosition >= 0 && childPosition < contacts.size) {
                contacts[childPosition]
            } else {
                null
            }
        }
    }

    public override fun getContactRenderer(groupIndex: Int): UIContactRenderer? {
        return if (groupIndex < metaContactList.groupCount) {
            metaContactList.getContactRenderer(groupIndex)
        } else {
            SourceContactRenderer.instance
        }
    }

    override fun filterData(queryString: String) {
        cancelQueries()
        for (css in sources!!) {
            val query = css.createContactQuery(queryString)!!
            queries.add(query)
            query.addContactQueryListener(this)
            query.start()
        }
        metaContactList.filterData(queryString)
        results = ArrayList()
        notifyDataSetChanged()
    }

    private fun cancelQueries() {
        for (query in queries) {
            query.cancel()
        }
        queries.clear()
    }

    override fun getDisplayName(groupImpl: Any): String {
        return (groupImpl as ResultGroup).source.displayName!!
    }

    override fun contactReceived(event: ContactReceivedEvent) {}

    override fun queryStatusChanged(event: ContactQueryStatusEvent) {
        if (event.eventType == ContactQuery.QUERY_COMPLETED) {
            val query = event.querySource
            val resultGroup = ResultGroup(query.contactSource, query.queryResults.toList())
            if (resultGroup.getCount() == 0) {
                return
            }
            uiHandler.post {
                if (!queries.contains(query)) {
                    Timber.w("Received event for cancelled query: %s", query)
                    return@post
                }
                results.add(resultGroup)
                notifyDataSetChanged()
                expandAllGroups()
            }
        }
    }

    override fun contactRemoved(event: ContactRemovedEvent) {
        Timber.e("CONTACT REMOVED NOT IMPLEMENTED")
    }

    override fun contactChanged(event: ContactChangedEvent) {
        Timber.e("CONTACT CHANGED NOT IMPLEMENTED")
    }

    private class ResultGroup(source: ContactSourceService, results: List<SourceContact>) {
        val contacts: List<SourceContact>
        val source: ContactSourceService

        init {
            this.source = source
            contacts = results
        }

        fun getCount(): Int {
            return contacts.size
        }
    }
}