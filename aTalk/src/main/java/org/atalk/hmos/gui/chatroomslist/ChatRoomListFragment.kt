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
package org.atalk.hmos.gui.chatroomslist

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ExpandableListAdapter
import android.widget.ExpandableListView
import android.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.protocol.ChatRoomMemberRole
import net.java.sip.communicator.util.ConfigurationUtils
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.chat.ChatPanel
import org.atalk.hmos.gui.chat.ChatSessionManager
import org.atalk.hmos.gui.chat.chatsession.ChatSessionFragment
import org.atalk.hmos.gui.chatroomslist.model.ChatRoomGroupExpandHandler
import org.atalk.hmos.gui.chatroomslist.model.ChatRoomListAdapter
import org.atalk.hmos.gui.chatroomslist.model.QueryChatRoomListAdapter
import org.atalk.hmos.gui.share.ShareActivity
import org.atalk.hmos.gui.util.EntityListHelper
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.service.osgi.OSGiFragment
import org.jxmpp.util.XmppStringUtils
import timber.log.Timber

/**
 * Class to display the ChatRoom in Expandable List View
 *
 * @author Eng Chong Meng
 */
class ChatRoomListFragment : OSGiFragment(), ExpandableListView.OnGroupClickListener {
    /**
     * Search options menu items.
     */
    private var mSearchItem: MenuItem? = null

    /**
     * ChatRoom TTS option item
     */
    private var mChatRoomTtsEnable: MenuItem? = null

    /**
     * ChatRoom list data model.
     */
    private var chatRoomListAdapter: ChatRoomListAdapter? = null

    /**
     * ChatRoom groups expand memory.
     */
    private var listExpandHandler: ChatRoomGroupExpandHandler? = null

    /**
     * List model used to search chatRoom list and chatRoom sources.
     */
    private var sourcesAdapter: QueryChatRoomListAdapter? = null

    /**
     * The chatRoom list view.
     */
    private var chatRoomListView: ExpandableListView? = null

    /**
     * Stores last clicked `chatRoom`.
     */
    private var mClickedChatRoom: ChatRoomWrapper? = null

    /**
     * Stores recently clicked chatRoom group.
     */
    private val mClickedGroup: ChatRoomProviderWrapper? = null
    private var mContext: Context? = null

    /**
     * Creates a new instance of `ContactListFragment`.
     */
    init {
        // This fragment will create options menu.
        setHasOptionsMenu(true)
    }

    /**
     * {@inheritDoc}
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
    }

    /**
     * {@inheritDoc}
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (AndroidGUIActivator.bundleContext == null) {
            return null
        }
        val content = inflater.inflate(R.layout.chatroom_list, container, false) as ViewGroup
        chatRoomListView = content.findViewById(R.id.chatRoomListView)
        chatRoomListView!!.setOnGroupClickListener(this)
        initChatRoomListAdapter()
        return content
    }

    /**
     * Initialize the chatRoom list adapter;
     * Leave invalidateViews() to BaseChatRoomListAdapter as data update is async in new thread
     */
    private fun initChatRoomListAdapter() {
        chatRoomListView!!.setAdapter(getChatRoomListAdapter())

        // Attach ChatRoomProvider expand memory
        listExpandHandler = ChatRoomGroupExpandHandler(chatRoomListAdapter!!, chatRoomListView!!)
        listExpandHandler!!.bindAndRestore()

        // Restore search state based on entered text
        if (mSearchItem != null) {
            val searchView = mSearchItem!!.actionView as SearchView?
            val filter = ViewUtil.toString(searchView!!.findViewById(R.id.search_src_text))
            filterChatRoomWrapperList(filter!!)
            bindSearchListener()
        } else {
            chatRoomListAdapter!!.filterData("")
        }

        // Restore scroll position
        chatRoomListView!!.setSelectionFromTop(scrollPosition, scrollTopPosition)
    }

    /**
     * {@inheritDoc}
     */
    override fun onResume() {
        super.onResume()

        // Invalidate view to update read counter and expand groups (collapsed when access settings)
        if (chatRoomListAdapter != null) {
            chatRoomListAdapter!!.expandAllGroups()
            chatRoomListAdapter!!.invalidateViews()
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onDestroy() {
        // Unbind search listener
        if (mSearchItem != null) {
            val searchView = mSearchItem!!.actionView as SearchView?
            searchView!!.setOnQueryTextListener(null)
            searchView.setOnCloseListener(null)
        }

        // Save scroll position
        if (chatRoomListView != null) {
            scrollPosition = chatRoomListView!!.firstVisiblePosition
            val itemView = chatRoomListView!!.getChildAt(0)
            if (itemView != null)
                scrollTopPosition = itemView.top
            chatRoomListView!!.setAdapter(null as ExpandableListAdapter?)
        }

        // Dispose of group expand memory
        if (listExpandHandler != null) {
            listExpandHandler!!.unbind()
            listExpandHandler = null
        }
        if (chatRoomListAdapter != null) {
            chatRoomListAdapter!!.dispose()
            chatRoomListAdapter = null
        }
        disposeSourcesAdapter()
        super.onDestroy()
    }

    /**
     * Invoked when the options menu is created. Creates our own options menu from the corresponding xml.
     *
     * @param menu the options menu
     */
    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, menuInflater)

        // Get the SearchView MenuItem
        mSearchItem = menu.findItem(R.id.search)
        if (mSearchItem == null) return
        mSearchItem!!.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                filterChatRoomWrapperList("")
                return true // Return true to collapse action view
            }

            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                return true // Return true to expand action view
            }
        })
        bindSearchListener()
    }

    private fun bindSearchListener() {
        if (mSearchItem != null) {
            val searchView = mSearchItem!!.actionView as SearchView?
            val listener = SearchViewListener()
            searchView!!.setOnQueryTextListener(listener)
            searchView.setOnCloseListener(listener)
        }
    }

    private fun getChatRoomListAdapter(): ChatRoomListAdapter? {
        if (chatRoomListAdapter == null) {
            chatRoomListAdapter = ChatRoomListAdapter(this)
            chatRoomListAdapter!!.initModelData()
        }
        return chatRoomListAdapter
    }

    private fun getSourcesAdapter(): QueryChatRoomListAdapter? {
        if (sourcesAdapter == null) {
            sourcesAdapter = QueryChatRoomListAdapter(this, chatRoomListAdapter!!)
            sourcesAdapter!!.initModelData()
        }
        return sourcesAdapter
    }

    private fun disposeSourcesAdapter() {
        if (sourcesAdapter != null) {
            sourcesAdapter!!.dispose()
        }
        sourcesAdapter = null
    }

    /**
     * Inflates chatRoom Item popup menu.
     * Avoid using android contextMenu (in fragment) - truncated menu list
     *
     * @param roomView click view.
     * @param crWrapper an instance of ChatRoomWrapper.
     */
    fun showPopupMenu(roomView: View?, crWrapper: ChatRoomWrapper) {
        // Inflate chatRoom list popup menu
        val popup = PopupMenu(mContext, roomView)
        val menu = popup.menu
        popup.menuInflater.inflate(R.menu.chatroom_ctx_menu, menu)
        popup.setOnMenuItemClickListener(PopupMenuItemClick())

        // Remember clicked chatRoomWrapper
        mClickedChatRoom = crWrapper

        // update contact TTS enable option title
        val ttsOption = aTalkApp.getResString(if (crWrapper.isTtsEnable) R.string.service_gui_TTS_DISABLE else R.string.service_gui_TTS_ENABLE)
        mChatRoomTtsEnable = menu.findItem(R.id.chatroom_tts_enable)
        mChatRoomTtsEnable!!.title = ttsOption
        mChatRoomTtsEnable!!.isVisible = ConfigurationUtils.isTtsEnable()

        // Only room owner is allowed to destroy chatRoom, or non-joined room (un-deterministic)
        val role = mClickedChatRoom!!.chatRoom!!.getUserRole()
        val allowDestroy = role == null || ChatRoomMemberRole.OWNER == role
        menu.findItem(R.id.destroy_chatroom).isVisible = allowDestroy

        // Checks if close chat option should be visible for this chatRoom
        val closeChatVisible = ChatSessionManager.getActiveChat(mClickedChatRoom!!.chatRoomID) != null
        menu.findItem(R.id.close_chatroom).isVisible = closeChatVisible

        // Close all chats option should be visible if chatList is not empty
        val chatList = ChatSessionManager.getActiveChats()
        val visible = chatList.size > 1 || chatList.size == 1 && !closeChatVisible
        menu.findItem(R.id.close_all_chatrooms).isVisible = visible

        // may not want to offer erase all chatRooms chat history
        menu.findItem(R.id.erase_all_chatroom_history).isVisible = false
        popup.show()
    }

    /**
     * Interface responsible for receiving menu item click events if the items
     * themselves do not have individual item click listeners.
     */
    private inner class PopupMenuItemClick : PopupMenu.OnMenuItemClickListener {
        /**
         * This method will be invoked when a menu item is clicked if the item
         * itself did not already handle the event.
         *
         * @param item the menu item that was clicked
         * @return `true` if the event was handled, `false` otherwise
         */
        override fun onMenuItemClick(item: MenuItem): Boolean {
            val chatPanel = ChatSessionManager.getActiveChat(mClickedChatRoom!!.chatRoomID)
            return when (item.itemId) {
                R.id.chatroom_tts_enable -> {
                    if (mClickedChatRoom!!.isTtsEnable) {
                        mClickedChatRoom!!.isTtsEnable = false
                        mChatRoomTtsEnable!!.setTitle(R.string.service_gui_TTS_ENABLE)
                    } else {
                        mClickedChatRoom!!.isTtsEnable = true
                        mChatRoomTtsEnable!!.setTitle(R.string.service_gui_TTS_DISABLE)
                    }
                    ChatSessionManager.getMultiChat(mClickedChatRoom!!, true)!!.updateChatTtsOption()
                    true
                }
                R.id.close_chatroom -> {
                    if (chatPanel != null) onCloseChat(chatPanel)
                    true
                }
                R.id.close_all_chatrooms -> {
                    onCloseAllChats()
                    true
                }
                R.id.erase_chatroom_history -> {
                    EntityListHelper.eraseEntityChatHistory(mContext!!, mClickedChatRoom!!, null, null)
                    true
                }
                R.id.erase_all_chatroom_history -> {
                    // This opton is currently being disabled - not offer to user
                    EntityListHelper.eraseAllEntityHistory(mContext!!)
                    true
                }
                R.id.destroy_chatroom -> {
                    ChatRoomDestroyDialog().show(mContext, mClickedChatRoom!!, chatPanel!!)
                    true
                }
                R.id.chatroom_info -> {
                    val chatRoomInfoDialog = ChatRoomInfoDialog.newInstance(mClickedChatRoom)
                    val ft = parentFragmentManager.beginTransaction()
                    ft.addToBackStack(null)
                    chatRoomInfoDialog.show(ft, "infoDialog")
                    true
                }
                R.id.chatroom_ctx_menu_exit -> true
                else -> false
            }
        }
    }

    /**
     * Method fired when given chat is being closed.
     *
     * @param closedChat closed `ChatPanel`.
     */
    private fun onCloseChat(closedChat: ChatPanel) {
        ChatSessionManager.removeActiveChat(closedChat)
        if (chatRoomListAdapter != null) chatRoomListAdapter!!.notifyDataSetChanged()
    }

    /**
     * Method fired when all chats are being closed.
     */
    private fun onCloseAllChats() {
        ChatSessionManager.removeAllActiveChats()
        if (chatRoomListAdapter != null) chatRoomListAdapter!!.notifyDataSetChanged()
    }

    /**
     * Returns the chatRoom list view.
     *
     * @return the chatRoom list view
     */
    fun getChatRoomListView(): ExpandableListView? {
        return chatRoomListView
    }

    /**
     * Open and join chat conference for the given chatRoomWrapper.
     */
    fun joinChatRoom(chatRoomWrapper: ChatRoomWrapper?) {
        if (chatRoomWrapper != null) {
            val pps = chatRoomWrapper.protocolProvider
            val nickName = XmppStringUtils.parseLocalpart(pps!!.accountID.accountJid)
            MUCActivator.mucService.joinChatRoom(chatRoomWrapper, nickName, null, null)
            var chatIntent = ChatSessionManager.getChatIntent(chatRoomWrapper)
            if (chatIntent != null) {
                val shareIntent = ShareActivity.getShareIntent(chatIntent)
                if (shareIntent != null) {
                    chatIntent = shareIntent
                }
                startActivity(chatIntent)
            } else {
                Timber.w("Failed to start chat with %s", chatRoomWrapper)
            }
        }
    }

    /**
     * Expands/collapses the group given by `groupPosition`.
     *
     * @param parent the parent expandable list view
     * @param v the view
     * @param groupPosition the position of the group
     * @param id the identifier
     * @return `true` if the group click action has been performed
     */
    override fun onGroupClick(parent: ExpandableListView, v: View, groupPosition: Int, id: Long): Boolean {
        if (chatRoomListView!!.isGroupExpanded(groupPosition)) chatRoomListView!!.collapseGroup(groupPosition) else {
            chatRoomListView!!.expandGroup(groupPosition, true)
        }
        return true
    }

    /**
     * Filters chatRoom list for given `query`.
     *
     * @param query the query string that will be used for filtering chat rooms.
     */
    private fun filterChatRoomWrapperList(query: String) {
        // FFR: 2.1.5 Samsung Galaxy J2 Prime (grandpplte), Android 6.0, NPE for chatRoomListView; happen when offline?
        if (chatRoomListView == null) return
        if (StringUtils.isEmpty(query)) {
            // Cancel any pending queries
            disposeSourcesAdapter()

            // Display the chatRoom list
            if (chatRoomListView!!.expandableListAdapter != getChatRoomListAdapter()) {
                chatRoomListView!!.setAdapter(getChatRoomListAdapter())
                chatRoomListAdapter!!.filterData("")
            }

            // Restore previously collapsed groups
            if (listExpandHandler != null) {
                listExpandHandler!!.bindAndRestore()
            }
        } else {
            // Unbind group expand memory
            if (listExpandHandler != null) listExpandHandler!!.unbind()

            // Display search results
            if (chatRoomListView!!.expandableListAdapter != getSourcesAdapter()) {
                chatRoomListView!!.setAdapter(getSourcesAdapter())
            }

            // Update query string
            sourcesAdapter!!.filterData(query)
        }
    }

    /**
     * Class used to implement `SearchView` listeners for compatibility purposes.
     */
    private inner class SearchViewListener : SearchView.OnQueryTextListener, SearchView.OnCloseListener {
        override fun onQueryTextSubmit(query: String): Boolean {
            filterChatRoomWrapperList(query)
            return true
        }

        override fun onQueryTextChange(query: String): Boolean {
            filterChatRoomWrapperList(query)
            return true
        }

        override fun onClose(): Boolean {
            filterChatRoomWrapperList("")
            return true
        }
    }

    /**
     * Update the unread message badge for the specified ChatRoomWrapper
     * The unread count is pre-stored in the crWrapper
     *
     * @param crWrapper The ChatRoomWrapper to be updated
     */
    fun updateUnreadCount(crWrapper: ChatRoomWrapper?) {
        runOnUiThread {
            if (crWrapper != null && chatRoomListAdapter != null) {
                val unreadCount = crWrapper.unreadCount
                chatRoomListAdapter!!.updateUnreadCount(crWrapper, unreadCount)
                val csf = aTalk.getFragment(aTalk.CHAT_SESSION_FRAGMENT)
                if (csf is ChatSessionFragment) {
                    csf.updateUnreadCount(crWrapper.chatRoomID, unreadCount)
                }
            }
        }
    }

    companion object {
        /**
         * Contact list item scroll position.
         */
        private var scrollPosition = 0

        /**
         * Contact list scroll top position.
         */
        private var scrollTopPosition = 0
    }
}