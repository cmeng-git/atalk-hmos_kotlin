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

import android.widget.ExpandableListView
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper

/**
 * Implements contact groups expand memory.
 *
 * @author Eng Chong Meng
 */
class ChatRoomGroupExpandHandler
/**
 * Creates new instance of `MetaGroupExpandHandler`.
 *
 * @param chatRoomList
 * contact list data model.
 * @param chatRoomListView
 * contact list view.
 */
(
        /**
         * Meta contact list adapter used by this instance.
         */
        private val chatRoomList: ChatRoomListAdapter,
        /**
         * The contact list view.
         */
        private val chatRoomListView: ExpandableListView) : ExpandableListView.OnGroupExpandListener, ExpandableListView.OnGroupCollapseListener {
    /**
     * Binds the listener and restores previous groups expanded/collapsed state.
     */
    fun bindAndRestore() {
        for (gIdx in 0 until chatRoomList.groupCount) {
            val chatRoomProviderWrapperGroup = chatRoomList.getGroup(gIdx) as ChatRoomProviderWrapper

            if (false == chatRoomProviderWrapperGroup.getData(KEY_EXPAND_MEMORY)) {
                chatRoomListView.collapseGroup(gIdx)
            } else {
                // Will expand by default
                chatRoomListView.expandGroup(gIdx)
            }
        }
        chatRoomListView.setOnGroupExpandListener(this)
        chatRoomListView.setOnGroupCollapseListener(this)
    }

    /**
     * Unbinds the listener.
     */
    fun unbind() {
        chatRoomListView.setOnGroupExpandListener(null)
        chatRoomListView.setOnGroupCollapseListener(null)
    }

    override fun onGroupCollapse(groupPosition: Int) {
        (chatRoomList.getGroup(groupPosition) as ChatRoomProviderWrapper).setData(KEY_EXPAND_MEMORY, false)
    }

    override fun onGroupExpand(groupPosition: Int) {
        (chatRoomList.getGroup(groupPosition) as ChatRoomProviderWrapper)
                .setData(KEY_EXPAND_MEMORY, true)
    }

    companion object {
        /**
         * Data key used to remember group state.
         */
        private const val KEY_EXPAND_MEMORY = "key.expand.memory"
    }
}