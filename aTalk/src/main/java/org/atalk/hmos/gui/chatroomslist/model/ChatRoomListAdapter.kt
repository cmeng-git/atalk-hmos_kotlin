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

import android.text.TextUtils
import net.java.sip.communicator.impl.muc.ChatRoomWrapperImpl
import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.impl.muc.MUCServiceImpl
import net.java.sip.communicator.service.muc.ChatRoomListChangeEvent
import net.java.sip.communicator.service.muc.ChatRoomListChangeListener
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapperListener
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.ChatSessionManager
import org.atalk.hmos.gui.chatroomslist.ChatRoomListFragment
import org.atalk.hmos.gui.contactlist.model.UIGroupRenderer
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.util.StringUtils
import org.jivesoftware.smackx.bookmarks.BookmarkManager
import org.jivesoftware.smackx.bookmarks.BookmarkedConference
import timber.log.Timber
import java.util.*
import java.util.regex.Pattern

/**
 * ChatRoom list model is responsible for caching current chatRoomWrapper list obtained from
 * ChatRoomProviderWrapperImpl.(It will apply source filters which result in different output model).
 *
 * @author Eng Chong Meng
 */
class ChatRoomListAdapter(chatRoomListFragment: ChatRoomListFragment) : BaseChatRoomListAdapter(chatRoomListFragment), ChatRoomProviderWrapperListener, ChatRoomListChangeListener, UIGroupRenderer {
    /**
     * The group of original chatRoomProviderWrapper before filtered
     */
    private val originalCrpWrapperGroup = LinkedList<ChatRoomProviderWrapper>()

    /**
     * The group of chatRoomProviderWrapper for view display
     */
    private val mCrpWrapperGroup = LinkedList<ChatRoomProviderWrapper>()

    /**
     * The original list of chatRoomWrapper before filtered.
     */
    private val originalCrWrapperList = LinkedList<TreeSet<ChatRoomWrapper>>()

    /**
     * The list of chatRoomWrapper for view display.
     */
    private val mCrWrapperList = LinkedList<TreeSet<ChatRoomWrapper>>()

    /**
     * The `MUCService`, which is the back end of this chatRoom list adapter.
     */
    private var mucService: MUCServiceImpl? = null

    /**
     * `ChatRoomRenderer` instance used by this adapter.
     */
    private var chatRoomRenderer: ChatRoomRenderer? = null

    /**
     * The currently used filter query.
     */
    private var currentFilterQuery: String? = null

    /**
     * A local reference of the last fetched bookmarks list
     */
    private var bookmarksList: List<BookmarkedConference>? = null

    /**
     * Initializes the adapter data.
     */
    override fun initModelData() {
        mucService = MUCActivator.mucService
        // Timber.d("ChatRoom list change listener is added %s", this);
        mucService!!.addChatRoomProviderWrapperListener(this)
        mucService!!.addChatRoomListChangeListener(this)
        object : Thread() {
            override fun run() {
                addChatRooms(mucService!!.chatRoomProviders)
            }
        }.start()
    }

    /**
     * Releases all resources used by this instance.
     */
    override fun dispose() {
        if (mucService != null) {
            // Timber.d("ChatRoom list change listener is removed %s", this);
            mucService!!.removeChatRoomProviderWrapperListener(this)
            mucService!!.removeChatRoomListChangeListener(this)
            removeChatRooms(mucService!!.chatRoomProviders)
        }
    }

    /**
     * Locally implemented UIGroupRenderer
     * {@inheritDoc}
     */
    public override fun getGroupRenderer(groupPosition: Int): UIGroupRenderer {
        return this
    }

    /**
     * {@inheritDoc}
     */
    public override fun getChatRoomRenderer(groupIndex: Int): UIChatRoomRenderer? {
        if (chatRoomRenderer == null) {
            chatRoomRenderer = ChatRoomRenderer()
        }
        return chatRoomRenderer
    }

    /**
     * Returns the mCrpWrapperGroup at the given `groupPosition`.
     *
     * @param groupPosition the index of the mCrpWrapperGroup
     */
    override fun getGroup(groupPosition: Int): ChatRoomProviderWrapper? {

        return if (groupPosition >= 0 && groupPosition < mCrpWrapperGroup.size)
            mCrpWrapperGroup[groupPosition];
        else {
            null
        }
    }

    /**
     * Returns the count of all ChatRoomProviderWrapper contained in this adapter.
     */
    override fun getGroupCount(): Int {
        return mCrpWrapperGroup.size
    }

    /**
     * Finds mCrpWrapperGroup index for given `ChatRoomProviderWrapper`.
     *
     * @param group the mCrpWrapperGroup for which we need the index.
     * @return index of given `ChatRoomProviderWrapper` or -1 if not found
     */
    fun getGroupIndex(group: ChatRoomProviderWrapper): Int {
        return mCrpWrapperGroup.indexOf(group)
    }

    /**
     * Finds `ChatRoomWrapper` index in `ChatRoomProviderWrapper` identified by given `groupIndex`.
     *
     * @param groupIndex index of mCrpWrapperGroup we want to search.
     * @param chatRoomWrapper the `ChatRoomWrapper` to find inside the mCrpWrapperGroup.
     * @return index of `ChatRoomWrapper` inside mCrpWrapperGroup identified by given mCrpWrapperGroup index.
     */
    fun getChildIndex(groupIndex: Int, chatRoomWrapper: ChatRoomWrapper): Int {
        return getChildIndex(getCrWrapperList(groupIndex), chatRoomWrapper)
    }

    /**
     * Returns the count of children contained in the mCrpWrapperGroup given by the `groupPosition`.
     *
     * @param groupPosition the index of the mCrpWrapperGroup, which children we would like to count
     */
    override fun getChildrenCount(groupPosition: Int): Int {
        val chatRoomList = getCrWrapperList(groupPosition)
        return chatRoomList?.size ?: 0
    }

    /**
     * Get mCrpWrapperGroup list from filtered CrWrapperList list.
     *
     * @param groupIndex mCrpWrapperGroup index.
     * @return mCrWrapper list from filtered CrWrapperList list.
     */
    private fun getCrWrapperList(groupIndex: Int): TreeSet<ChatRoomWrapper>? {
        return if (groupIndex >= 0 && groupIndex < mCrWrapperList.size) {
            mCrWrapperList[groupIndex]
        }
        else {
            null
        }
    }

    /**
     * Get mCrpWrapperGroup list from original chatRoomWrapper list.
     *
     * @param groupIndex mCrpWrapperGroup index.
     * @return mCrpWrapperGroup list from original list.
     */
    private fun getOriginalCrWrapperList(groupIndex: Int): TreeSet<ChatRoomWrapper>? {
        return if (groupIndex >= 0 && groupIndex < originalCrWrapperList.size) {
            originalCrWrapperList[groupIndex]
        }
        else {
            null
        }
    }

    /**
     * Adds all child mCrWrapperList for all the given `mCrpWrapperGroup`. Skip adding group of zero child.
     *
     * @param providers the providers mCrpWrapperGroup, which child mCrWrapperList to add
     */
    private fun addChatRooms(providers: List<ChatRoomProviderWrapper>) {
        for (provider in providers) {
            val chatRoomWrappers = initBookmarkChatRooms(provider)
            if (chatRoomWrappers != null && chatRoomWrappers.isNotEmpty()) {
                addGroup(provider)

                // Use Iterator to avoid ConcurrentModificationException on addChatRoom(); do not user foreach
                val iteratorCRW = chatRoomWrappers.iterator()
                while (iteratorCRW.hasNext()) {
                    addChatRoom(provider, iteratorCRW.next())
                }
                // for (ChatRoomWrapper crWrapper : chatRoomWrappers) {
                //     addChatRoom(provider, crWrapper); // ConcurrentModificationException
                // }
            }
        }

        // must refresh list view only after chatRoomWrappers fetch with bookmark info updated
        invalidateViews()
    }

    /**
     * Adds all child mCrWrapperList for all the given `mCrpWrapper` and update it with bookmark info
     *
     * @param crpWrapper the crpWrapper provider, which child mCrWrapperList to fetch
     */
    private fun initBookmarkChatRooms(crpWrapper: ChatRoomProviderWrapper?): List<ChatRoomWrapper>? {
        if (crpWrapper != null) {
            var connection: XMPPConnection?
            val pps = crpWrapper.protocolProvider
            if (pps.connection.also { connection = it } == null || !connection!!.isAuthenticated) {
                // reset bookmarks when user log off is detected.
                bookmarksList = null
                return null
            }

            // Just return room lists if bookmarks have been fetched and updated earlier
            if (bookmarksList != null) return crpWrapper.chatRooms
            Timber.d("Update conference bookmarks started.")

            val bookmarkManager = BookmarkManager.getBookmarkManager(connection)
            try {
                bookmarksList = bookmarkManager.bookmarkedConferences
                for (bookmarkedConference in bookmarksList!!) {
                    val chatRoomId = bookmarkedConference.jid.toString()
                    var chatRoomWrapper = crpWrapper.findChatRoomWrapperForChatRoomID(chatRoomId)
                    if (chatRoomWrapper == null) {
                        chatRoomWrapper = ChatRoomWrapperImpl(crpWrapper, chatRoomId)
                        crpWrapper.addChatRoom(chatRoomWrapper)
                    }
                    // cmeng: not working - problem chatRoom list empty
                    // else {
                    //     crWrappers.remove(chatRoomWrapper);
                    // }
                    chatRoomWrapper.setBookmark(true)
                    chatRoomWrapper.bookmarkName = bookmarkedConference.name
                    chatRoomWrapper.isAutoJoin = bookmarkedConference.isAutoJoin
                    val password = bookmarkedConference.password
                    if (StringUtils.isNotEmpty(password)) chatRoomWrapper.savePassword(password)
                }
            } catch (e: SmackException.NoResponseException) {
                Timber.w("Failed to fetch Bookmarks for %s: %s",
                        crpWrapper.protocolProvider.accountID, e.message)
            } catch (e: SmackException.NotConnectedException) {
                Timber.w("Failed to fetch Bookmarks for %s: %s",
                        crpWrapper.protocolProvider.accountID, e.message)
            } catch (e: XMPPException.XMPPErrorException) {
                Timber.w("Failed to fetch Bookmarks for %s: %s",
                        crpWrapper.protocolProvider.accountID, e.message)
            } catch (e: InterruptedException) {
                Timber.w("Failed to fetch Bookmarks for %s: %s",
                        crpWrapper.protocolProvider.accountID, e.message)
            }
            Timber.d("Update conference bookmarks completed")

            // Auto join chatRoom if any - not need
            // crpWrapper.synchronizeProvider();
            return crpWrapper.chatRooms
        }
        return null
    }

    /**
     * Adds the given `crpWrapper` to both the originalCrpWrapperGroup and
     * mCrpWrapperGroup only if no existing crpWrapper is found in current lists
     *
     * @param crpWrapper the `ChatRoomProviderWrapper` to add
     */
    private fun addGroup(crpWrapper: ChatRoomProviderWrapper) {
        if (!originalCrpWrapperGroup.contains(crpWrapper)) {
            originalCrpWrapperGroup.add(crpWrapper)
            originalCrWrapperList.add(TreeSet())
        }
        if (!mCrpWrapperGroup.contains(crpWrapper)) {
            mCrpWrapperGroup.add(crpWrapper)
            mCrWrapperList.add(TreeSet())
        }
    }

    /**
     * Remove an existing `crpWrapper` from both the originalCrpWrapperGroup and mCrpWrapperGroup if exist
     *
     * @param crpWrapper the `chatRoomProviderWrapper` to be removed
     */
    private fun removeGroup(crpWrapper: ChatRoomProviderWrapper) {
        val origGroupIndex = originalCrpWrapperGroup.indexOf(crpWrapper)
        if (origGroupIndex != -1) {
            originalCrWrapperList.removeAt(origGroupIndex)
            originalCrpWrapperGroup.remove(crpWrapper)
        }
        val groupIndex = mCrpWrapperGroup.indexOf(crpWrapper)
        if (groupIndex != -1) {
            mCrWrapperList.removeAt(groupIndex)
            mCrpWrapperGroup.remove(crpWrapper)
        }
    }

    /**
     * Adds the given child crWrapper to the `crpWrapperGroup`.
     *
     * @param crpWrapperGroup the parent ChatRoomProviderWrapper Group of the child ChatRoomWrapper to add
     * @param crWrapper the `ChatRoomWrapper` to add
     */
    private fun addChatRoom(crpWrapperGroup: ChatRoomProviderWrapper, crWrapper: ChatRoomWrapper) {
        var origGroupIndex = originalCrpWrapperGroup.indexOf(crpWrapperGroup)
        var groupIndex = mCrpWrapperGroup.indexOf(crpWrapperGroup)
        val isMatchingQuery = isMatching(crWrapper, currentFilterQuery!!)

        // Add new crpWrapperGroup element (original and filtered) and
        // update both with the new Indexes (may be difference)
        if (origGroupIndex < 0 || isMatchingQuery && groupIndex < 0) {
            addGroup(crpWrapperGroup)
            origGroupIndex = originalCrpWrapperGroup.indexOf(crpWrapperGroup)
            groupIndex = mCrpWrapperGroup.indexOf(crpWrapperGroup)
        }
        val origCrWrapperList = getOriginalCrWrapperList(origGroupIndex)
        if (origCrWrapperList != null && getChildIndex(origCrWrapperList, crWrapper) < 0) {
            origCrWrapperList.add(crWrapper)
        }

        // New crWrapper is added to filtered crWrapperList only if isMatchingQuery
        if (isMatchingQuery) {
            val crWrapperList = getCrWrapperList(groupIndex)
            if (crWrapperList != null && getChildIndex(crWrapperList, crWrapper) < 0) {
                crWrapperList.add(crWrapper)
            }
        }
    }

    /**
     * Removes all the ChatRoomProviderWrappers and ChatRoomWrappers for the given providers.
     *
     * @param providers the `ChatRoomProviderWrapper`, which content we'd like to remove
     */
    private fun removeChatRooms(providers: List<ChatRoomProviderWrapper>) {
        for (provider in providers) {
            val crWrapperList = provider.chatRooms
            for (crWrapper in crWrapperList) {
                removeChatRoom(provider, crWrapper)
            }

            // May not be necessary as remove all children will also remove the provider with zero child
            // must do this last as the provider is used in removeChatRoom();
            removeGroup(provider)
        }
    }

    /**
     * Remove the given `ChatRoomWrapper` from both the original and the filtered list of
     * this adapter. Also remove the group with zero element
     *
     * @param crpWrapper the parent `ChatRoomProviderWrapper` of the ChatRoomWrapper to remove
     * @param crWrapper the `ChatRoomWrapper` to remove
     */
    private fun removeChatRoom(crpWrapper: ChatRoomProviderWrapper, crWrapper: ChatRoomWrapper) {
        // Remove the chatRoomWrapper from the original list and its crpWrapperGroup if empty.
        val origGroupIndex = originalCrpWrapperGroup.indexOf(crpWrapper)
        if (origGroupIndex != -1) {
            val origChatRoomList = getOriginalCrWrapperList(origGroupIndex)
            if (origChatRoomList != null) {
                origChatRoomList.remove(crWrapper)
                if (origChatRoomList.isEmpty()) removeGroup(crpWrapper)
            }
        }

        // Remove the chatRoomWrapper from the filtered list and its crpWrapperGroup if empty
        val groupIndex = mCrpWrapperGroup.indexOf(crpWrapper)
        if (groupIndex != -1) {
            val crWrapperList = getCrWrapperList(groupIndex)
            if (crWrapperList != null) {
                crWrapperList.remove(crWrapper)
                if (crWrapperList.isEmpty()) removeGroup(crpWrapper)
            }
        }
    }

    /**
     * Returns the chatRoom with the given `groupPosition` and `childPosition`.
     *
     * @param groupPosition the index of the mCrpWrapperGroup
     * @param childPosition the index of the child
     * @return the chatRoom with the given `groupPosition` and `childPosition`
     */
    override fun getChild(groupPosition: Int, childPosition: Int): Any? {
        if (mCrWrapperList.size > 0) {
            val crWrapperList = getCrWrapperList(groupPosition)
            if (crWrapperList != null) {
                for ((i, crWrapper) in crWrapperList.withIndex()) {
                    if (i == childPosition) {
                        return crWrapper
                    }
                }
            }
        }
        return null
    }

    /**
     * Return crWrapper index in the originalCrWrapperList
     */
    private fun getChildIndex(crWrapperList: TreeSet<ChatRoomWrapper>?, crWrapper: ChatRoomWrapper): Int {
        if (crWrapperList != null) {
            for ((i, chatRoomWrapper) in crWrapperList.withIndex()) {
                if (chatRoomWrapper == crWrapper) return i
            }
        }
        return -1
    }

    /**
     * Filters list data to match the given `query`.
     *
     * @param queryString the query we'd like to match
     */
    override fun filterData(queryString: String) {
        currentFilterQuery = queryString.lowercase(Locale.getDefault())
        mCrpWrapperGroup.clear()
        mCrWrapperList.clear()
        for (crpWrapper in originalCrpWrapperGroup) {
            val filteredList = TreeSet<ChatRoomWrapper>()
            val groupIndex = originalCrpWrapperGroup.indexOf(crpWrapper)
            val crWrapperList = getOriginalCrWrapperList(groupIndex)
            if (crWrapperList != null) {
                for (crWrapper in crWrapperList) {
                    if (StringUtils.isEmpty(currentFilterQuery)
                            || isMatching(crWrapper, queryString)) {
                        filteredList.add(crWrapper)
                    }
                }
                if (filteredList.size > 0) {
                    mCrpWrapperGroup.add(crpWrapper)
                    mCrWrapperList.add(filteredList)
                }
            }
        }
        uiChangeUpdate()
        expandAllGroups()
    }

    /**
     * Create mCrpWrapperGroup/mCrWrapperList TreeView with non-zero mCrpWrapperGroup
     */
    fun nonZeroCrpWrapperGroupList() {
        mCrpWrapperGroup.clear()
        mCrWrapperList.clear()

        // hide mCrpWrapperGroup contains zero chatRoomWrapper
        for (crpWrapper in originalCrpWrapperGroup) {
            val groupIndex = originalCrpWrapperGroup.indexOf(crpWrapper)
            if (groupIndex != -1) {
                val orgCrwList = getOriginalCrWrapperList(groupIndex)
                if (orgCrwList != null && orgCrwList.size > 0) {
                    mCrpWrapperGroup.add(crpWrapper)
                    mCrWrapperList.add(orgCrwList)
                }
            }
        }
    }

    /**
     * Checks if the given `chatRoomWrapper` is matching the given `query`.
     * A `ChatRoomWrapper` would be matching the filter if one of the following is true:<br></br>
     * - it is online or user chooses show offline mCrWrapperList
     * - its chatRoom ID or Name matches the filter string
     *
     * @param chatRoomWrapper the `chatRoomWrapper` to check
     * @param query the query string i.e. chatRoomID to check for matches. A null always return true
     * @return `true` to indicate that the given `chatRoomWrapper` is matching the
     * current filter, otherwise returns `false`
     */
    private fun isMatching(chatRoomWrapper: ChatRoomWrapper, query: String): Boolean {
        if (TextUtils.isEmpty(query)) return true
        val chatRoomID = chatRoomWrapper.chatRoomID
        val queryPattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
        return queryPattern.matcher(chatRoomID).find()
    }

    /**
     * Checks if the given `chatRoomProviderWrapper` is matching the current filter.
     * A chatRoomProviderWrapper is matching the current filter only if its protocolProvider
     * matching the current filter.
     *
     * @param chatRoomProviderWrapper the `ChatRoomProviderWrapper` to check
     * @param query the query string i.e. accountUuid to check for matches. A null will always return true
     * @return `true` to indicate that the given `metaGroup` is matching the current
     * filter, otherwise returns `false`
     */
    fun isMatching(chatRoomProviderWrapper: ChatRoomProviderWrapper, query: String): Boolean {
        if (TextUtils.isEmpty(query)) return true
        val userUuid = chatRoomProviderWrapper.protocolProvider.accountID.accountUniqueID
        val queryPattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
        return queryPattern.matcher(userUuid!!).find()
    }

    /**
     * Implements [UIGroupRenderer]. {@inheritDoc}
     */
    override fun getDisplayName(groupImpl: Any): String {
        return (groupImpl as ChatRoomProviderWrapper).protocolProvider.accountID.accountUniqueID!!
    }

    /**
     * When a provider wrapper is added this method is called to inform listeners.
     * Add group and child only if there is at least one child in the group.
     *
     * @param provider which was added.
     */
    override fun chatRoomProviderWrapperAdded(provider: ChatRoomProviderWrapper) {
        // Add the original/filtered chatRoomProvider Wrapper and its list.
        if (originalCrpWrapperGroup.indexOf(provider) < 0 || mCrpWrapperGroup.indexOf(provider) < 0) {
            val chatRoomWrappers = provider.chatRooms
            if (chatRoomWrappers.isNotEmpty()) {
                addGroup(provider)
                for (crWrapper in chatRoomWrappers) {
                    addChatRoom(provider, crWrapper)
                }
                uiChangeUpdate()
            }
        }
    }

    /**
     * When a provider wrapper is removed this method is called to inform listeners.
     *
     * @param provider which was removed.
     */
    override fun chatRoomProviderWrapperRemoved(provider: ChatRoomProviderWrapper) {
        // Remove the original/filtered chatRoomProvider Wrapper and its chatRoomWrapper if exist.
        if (originalCrpWrapperGroup.indexOf(provider) >= 0 || mCrpWrapperGroup.indexOf(provider) >= 0) {
            removeChatRooms(listOf(provider))
            uiChangeUpdate()
        }
    }

    /**
     * Indicates that a change has occurred in the chatRoom List.
     */
    override fun contentChanged(evt: ChatRoomListChangeEvent) {
        val chatRoomWrapper = evt.sourceChatRoom
        when (evt.eventID) {
            ChatRoomListChangeEvent.CHAT_ROOM_ADDED -> addChatRoom(chatRoomWrapper.parentProvider, chatRoomWrapper)
            ChatRoomListChangeEvent.CHAT_ROOM_REMOVED -> {
                removeChatRoom(chatRoomWrapper.parentProvider, chatRoomWrapper)
                aTalkApp.showToastMessage(R.string.service_gui_CHATROOM_DESTROY_SUCCESSFUL,
                        chatRoomWrapper.chatRoomID)
            }
            ChatRoomListChangeEvent.CHAT_ROOM_CHANGED -> {}
            else -> {}
        }
        uiChangeUpdate()
    }

    /**
     * All chatRoom fragment view update must be perform on UI thread
     * UI thread handler used to call all operations that access data model. This guarantees that
     * it's accessed from the single thread.
     */
    private fun uiChangeUpdate() {
        uiHandler.post { notifyDataSetChanged() }
    }

    companion object {
        /**
         * Checks if given `ChatRoomWrapper` is considered to be selected. That is if the chat
         * session with given `ChatRoomWrapper` is the one currently visible.
         *
         * @param chatId the `ChatID` to check.
         * @return `true` if given `ChatRoomWrapper` is considered to be selected.
         */
        fun isChatRoomWrapperSelected(chatId: String?): Boolean {
            val currentId = ChatSessionManager.getCurrentChatId()
            val activeCP = ChatSessionManager.getActiveChat(chatId)
            return currentId != null && activeCP != null && currentId == activeCP.chatSession!!.chatId
        }
    }
}