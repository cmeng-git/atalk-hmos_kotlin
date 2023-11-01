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
/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.contactlist.model

import android.widget.ExpandableListView
import android.widget.ExpandableListView.OnGroupCollapseListener
import android.widget.ExpandableListView.OnGroupExpandListener
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import java.lang.Boolean
import kotlin.Int

/**
 * Implements contact groups expand memory.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class MetaGroupExpandHandler
/**
 * Creates new instance of `MetaGroupExpandHandler`.
 *
 * @param contactList
 * contact list data model.
 * @param contactListView
 * contact list view.
 */
(
        /**
         * Meta contact list adapter used by this instance.
         */
        private val contactList: MetaContactListAdapter,
        /**
         * The contact list view.
         */
        private val contactListView: ExpandableListView) : OnGroupExpandListener, OnGroupCollapseListener {
    /**
     * Binds the listener and restores previous groups expanded/collapsed state.
     */
    fun bindAndRestore() {
        for (gIdx in 0 until contactList.groupCount) {
            val metaGroup = contactList.getGroup(gIdx) as MetaContactGroup
            if (Boolean.FALSE == metaGroup.getData(KEY_EXPAND_MEMORY)) {
                contactListView.collapseGroup(gIdx)
            } else {
                // Will expand by default
                contactListView.expandGroup(gIdx)
            }
        }
        contactListView.setOnGroupExpandListener(this)
        contactListView.setOnGroupCollapseListener(this)
    }

    /**
     * Unbinds the listener.
     */
    fun unbind() {
        contactListView.setOnGroupExpandListener(null)
        contactListView.setOnGroupCollapseListener(null)
    }

    override fun onGroupCollapse(groupPosition: Int) {
        (contactList.getGroup(groupPosition) as MetaContactGroup).setData(KEY_EXPAND_MEMORY, false)
    }

    override fun onGroupExpand(groupPosition: Int) {
        (contactList.getGroup(groupPosition) as MetaContactGroup).setData(KEY_EXPAND_MEMORY, true)
    }

    companion object {
        /**
         * Data key used to remember group state.
         */
        private const val KEY_EXPAND_MEMORY = "key.expand.memory"
    }
}