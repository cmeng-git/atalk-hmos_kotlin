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
package org.atalk.hmos.gui.chatroomslist.model

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
import org.atalk.hmos.gui.chatroomslist.ChatRoomListFragment
import org.atalk.hmos.gui.contactlist.model.UIGroupRenderer
import org.atalk.service.osgi.OSGiActivity
import org.osgi.framework.ServiceReference
import timber.log.Timber

/**
 * Class implements adapter that can be used to search chatRoomWrapper in the list.
 *
 * @author Eng Chong Meng
 */
class QueryChatRoomListAdapter
/**
 * Creates new instance of `QueryContactListAdapter`.
 *
 * @param fragment parent fragment.
 * chatRoomListModel meta contact list model used as a base data model
 */
(fragment: ChatRoomListFragment,
        /**
         * The meta contact list used as a base contact source. It is capable of filtering contacts
         * itself without queries.
         */
        private val chatRoomList: ChatRoomListAdapter) : BaseChatRoomListAdapter(fragment), UIGroupRenderer, ContactQueryListener {
    /**
     * Handler used to execute stuff on UI thread.
     */
    override val uiHandler = OSGiActivity.uiHandler

    /**
     * List of contact sources of type [ContactSourceService.SEARCH_TYPE].
     */
    private lateinit var sources: List<ContactSourceService>

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
            val contactSource = AndroidGUIActivator.bundleContext!!.getService(serRef as ServiceReference<ContactSourceService>)
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
        return chatRoomList.groupCount + results.size
    }

    override fun getGroup(position: Int): Any? {
        val metaGroupCount = chatRoomList.groupCount
        return if (position < metaGroupCount) {
            chatRoomList.getGroup(position)
        }
        else {
            results[position - metaGroupCount]
        }
    }

    public override fun getGroupRenderer(groupPosition: Int): UIGroupRenderer {
        return if (groupPosition < chatRoomList.groupCount) {
            chatRoomList.getGroupRenderer(groupPosition)
        }
        else {
            this
        }
    }

    override fun getChildrenCount(groupPosition: Int): Int {
        val metaGroupCount = chatRoomList.groupCount
        return if (groupPosition < metaGroupCount) {
            chatRoomList.getChildrenCount(groupPosition)
        }
        else {
            results[groupPosition - metaGroupCount].getCount()
        }
    }

    override fun getChild(groupPosition: Int, childPosition: Int): Any? {
        val metaGroupCount = chatRoomList.groupCount
        return if (groupPosition < metaGroupCount) {
            chatRoomList.getChild(groupPosition, childPosition)
        }
        else {
            results[0].contacts[childPosition]
        }
    }

    public override fun getChatRoomRenderer(groupIndex: Int): UIChatRoomRenderer? {
        return if (groupIndex < chatRoomList.groupCount) {
            chatRoomList.getChatRoomRenderer(groupIndex)
        }
        else {
            null
            // return SourceContactRenderer.instance;
        }
    }

    override fun filterData(queryString: String) {
        cancelQueries()
        for (css in sources) {
            val query = css.createContactQuery(queryString)!!
            queries.add(query)
            query.addContactQueryListener(this)
            query.start()
        }
        chatRoomList.filterData(queryString)
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
            val resultGroup = ResultGroup(query.contactSource, query.queryResults)
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

    private inner class ResultGroup(source: ContactSourceService, results: MutableCollection<SourceContact>) {
        val contacts: MutableList<SourceContact>
        val source: ContactSourceService

        init {
            this.source = source
            contacts = results as MutableList<SourceContact>
        }

        fun getCount(): Int {
            return contacts.size
        }
    }
}