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

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.CheckBox
import android.widget.ExpandableListView
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.FragmentTransaction
import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chatroomslist.ChatRoomBookmarkDialog
import org.atalk.hmos.gui.chatroomslist.ChatRoomListFragment
import org.atalk.hmos.gui.contactlist.model.UIGroupRenderer
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.hmos.gui.widgets.UnreadCountCustomView
import org.atalk.service.osgi.OSGiActivity
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smackx.bookmarks.BookmarkManager
import timber.log.Timber

/**
 * Base class for chatRoom list adapter implementations.
 *
 * @author Eng Chong Meng
 */
abstract class BaseChatRoomListAdapter(crlFragment: ChatRoomListFragment) : BaseExpandableListAdapter(), View.OnClickListener, View.OnLongClickListener, ChatRoomBookmarkDialog.OnFinishedCallback {
    /**
     * UI thread handler used to call all operations that access data model. This guarantees that
     * it's accessed from the main thread.
     */
    protected open val uiHandler = OSGiActivity.uiHandler

    /**
     * The chatRoom list view.
     */
    private val chatRoomListFragment: ChatRoomListFragment

    /**
     * The list view.
     */
    private val chatRoomListView: ExpandableListView?
    private var mViewHolder: ChatRoomViewHolder? = null

    /**
     * A map reference of ChatRoomWrapper to ChatRoomViewHolder for the unread message count update
     */
    private val crwViewHolder = HashMap<ChatRoomWrapper, ChatRoomViewHolder>()
    private val mInflater = LayoutInflater.from(aTalkApp.globalContext)

    /**
     * Creates the chatRoom list adapter.
     *
     * crlFragment the parent `ChatRoomListFragment`
     */
    init {
        // cmeng - must use this mInflater as crlFragment may not always attached to FragmentManager
        chatRoomListFragment = crlFragment
        chatRoomListView = chatRoomListFragment.getChatRoomListView()
    }

    /**
     * Initializes model data. Is called before adapter is used for the first time.
     */
    abstract fun initModelData()

    /**
     * Filter the chatRoom list with given `queryString`
     *
     * @param queryString the query string we want to match.
     */
    abstract fun filterData(queryString: String)

    /**
     * Returns the `UIChatRoomRenderer` for chatRoom of group at given `groupIndex`.
     *
     * @param groupIndex index of the chatRoomWrapper group.
     * @return the `UIChatRoomRenderer` for chatRoom of group at given `groupIndex`.
     */
    protected abstract fun getChatRoomRenderer(groupIndex: Int): UIChatRoomRenderer?

    /**
     * Returns the `UIGroupRenderer` for group at given `groupPosition`.
     *
     * @param groupPosition index of the chatRoom group.
     * @return the `UIGroupRenderer` for group at given `groupPosition`.
     */
    protected abstract fun getGroupRenderer(groupPosition: Int): UIGroupRenderer

    /**
     * Releases all resources used by this instance.
     */
    open fun dispose() {
        notifyDataSetInvalidated()
    }

    /**
     * Expands all contained groups.
     */
    fun expandAllGroups() {
        // Expand group view only when chatRoomListView is in focus (UI mode) - not null
        // cmeng - do not use isFocused() - may not in sync with actual
        uiHandler.post {
            // FFR:  v2.1.5 NPE even with pre-check for non-null, so add catch exception
            if (chatRoomListView != null) {
                val count = groupCount
                for (position in 0 until count) {
                    try {
                        chatRoomListView.expandGroup(position)
                    } catch (e: Exception) {
                        Timber.e(e, "Expand group Exception %s; %s", position, chatRoomListFragment)
                    }
                }
            }
        }
    }

    /**
     * Refreshes the view with expands group and invalid view.
     */
    fun invalidateViews() {
        if (chatRoomListView != null) {
            chatRoomListFragment.runOnUiThread { chatRoomListView.invalidateViews() }
        }
    }

    /**
     * Updates the chatRoomWrapper display name.
     *
     * @param groupIndex the index of the group to update
     * @param chatRoomIndex the index of the chatRoomWrapper to update
     */
    protected fun updateDisplayName(groupIndex: Int, chatRoomIndex: Int) {
        val firstIndex = chatRoomListView!!.firstVisiblePosition
        val chatRoomView = chatRoomListView.getChildAt(getListIndex(groupIndex, chatRoomIndex) - firstIndex)
        if (chatRoomView != null) {
            val crWrapper = getChild(groupIndex, chatRoomIndex) as ChatRoomWrapper
            ViewUtil.setTextViewValue(chatRoomView, R.id.displayName, crWrapper.chatRoomID)
        }
    }

    /**
     * Updates the chatRoom icon.
     *
     * @param groupIndex the index of the group to update
     * @param chatRoomIndex the index of the chatRoom to update
     * @param chatRoomWrapper ChatRoomWrapper implementation object instance
     */
    protected fun updateChatRoomIcon(groupIndex: Int, chatRoomIndex: Int, chatRoomWrapper: Any?) {
        val firstIndex = chatRoomListView!!.firstVisiblePosition
        val chatRoomView = chatRoomListView.getChildAt(getListIndex(groupIndex, chatRoomIndex) - firstIndex)
        if (chatRoomView != null) {
            val avatarView = chatRoomView.findViewById<ImageView>(R.id.room_icon)
            if (avatarView != null) setRoomIcon(avatarView, getChatRoomRenderer(groupIndex)!!.getChatRoomIcon(chatRoomWrapper)!!)
        }
    }

    /**
     * Updates the chatRoomWrapper unread message count.
     * Hide widget if (count == 0)
     */
    fun updateUnreadCount(chatRoomWrapper: ChatRoomWrapper, count: Int) {
        val chatRoomViewHolder = crwViewHolder[chatRoomWrapper] ?: return
        if (count == 0) {
            chatRoomViewHolder.unreadCount.visibility = View.GONE
        }
        else {
            chatRoomViewHolder.unreadCount.visibility = View.VISIBLE
            chatRoomViewHolder.unreadCount.setUnreadCount(count)
        }
    }

    /**
     * Returns the flat list index for the given `groupIndex` and `chatRoomIndex`.
     *
     * @param groupIndex the index of the group
     * @param chatRoomIndex the index of the child chatRoom
     * @return an int representing the flat list index for the given `groupIndex` and `chatRoomIndex`
     */
    private fun getListIndex(groupIndex: Int, chatRoomIndex: Int): Int {
        val lastIndex = chatRoomListView!!.lastVisiblePosition
        for (i in 0..lastIndex) {
            val lPosition = chatRoomListView.getExpandableListPosition(i)
            val groupPosition = ExpandableListView.getPackedPositionGroup(lPosition)
            val childPosition = ExpandableListView.getPackedPositionChild(lPosition)
            if (groupIndex == groupPosition && chatRoomIndex == childPosition) {
                return i
            }
        }
        return -1
    }

    /**
     * Returns the identifier of the child contained on the given `groupPosition` and `childPosition`.
     *
     * @param groupPosition the index of the group
     * @param childPosition the index of the child
     * @return the identifier of the child contained on the given `groupPosition` and `childPosition`
     */
    override fun getChildId(groupPosition: Int, childPosition: Int): Long {
        return childPosition.toLong()
    }

    /**
     * Returns the child view for the given `groupPosition`, `childPosition`.
     *
     * @param groupPosition the group position of the desired view
     * @param childPosition the child position of the desired view
     * @param isLastChild indicates if this is the last child
     * @param convertView the view to fill with data
     * @param parent the parent view group
     */
    override fun getChildView(groupPosition: Int, childPosition: Int, isLastChild: Boolean,
            convertView: View?, parent: ViewGroup): View {
        // Keeps reference to avoid future findViewById()
        var cView = convertView
        val chatRoomViewHolder: ChatRoomViewHolder
        val child = getChild(groupPosition, childPosition)
        if (cView == null) {
            cView = mInflater.inflate(R.layout.chatroom_list_row, parent, false)
            chatRoomViewHolder = ChatRoomViewHolder()
            chatRoomViewHolder.roomName = cView.findViewById(R.id.room_name)
            chatRoomViewHolder.statusMessage = cView.findViewById(R.id.room_status)
            chatRoomViewHolder.roomIcon = cView.findViewById(R.id.room_icon)
            chatRoomViewHolder.roomIcon.setOnClickListener(this)
            chatRoomViewHolder.roomIcon.tag = chatRoomViewHolder
            chatRoomViewHolder.unreadCount = cView.findViewById(R.id.unread_count)
            chatRoomViewHolder.unreadCount.tag = chatRoomViewHolder
            chatRoomViewHolder.autojoin = cView.findViewById(R.id.cb_autojoin)
            chatRoomViewHolder.autojoin.setOnClickListener(this)
            chatRoomViewHolder.autojoin.tag = chatRoomViewHolder
            chatRoomViewHolder.bookmark = cView.findViewById(R.id.cb_bookmark)
            chatRoomViewHolder.bookmark.setOnClickListener(this)
            chatRoomViewHolder.bookmark.tag = chatRoomViewHolder
            cView.tag = chatRoomViewHolder
        }
        else {
            chatRoomViewHolder = cView.tag as ChatRoomViewHolder
        }
        chatRoomViewHolder.groupPosition = groupPosition
        chatRoomViewHolder.childPosition = childPosition

        // return and stop further process if child has been removed
        if (child !is ChatRoomWrapper) return cView!!

        // Must init child Tag here as reused convertView may not necessary contains the correct crWrapper
        val roomView = cView!!.findViewById<View>(R.id.room_view)
        roomView.setOnClickListener(this)
        roomView.setOnLongClickListener(this)
        roomView.tag = child
        crwViewHolder[child] = chatRoomViewHolder
        updateUnreadCount(child, child.unreadCount)
        val renderer = getChatRoomRenderer(groupPosition)
        if (renderer!!.isSelected(child)) {
            cView.setBackgroundResource(R.drawable.color_blue_gradient)
        }
        else {
            cView.setBackgroundResource(R.drawable.list_selector_state)
        }
        // Update display information.
        val roomStatus = renderer.getStatusMessage(child)
        chatRoomViewHolder.statusMessage.text = roomStatus
        val roomName = renderer.getDisplayName(child)
        chatRoomViewHolder.roomName.text = roomName
        chatRoomViewHolder.autojoin.isChecked = renderer.isAutoJoin(child)
        chatRoomViewHolder.bookmark.isChecked = renderer.isBookmark(child)
        if (renderer.isDisplayBold(child)) {
            chatRoomViewHolder.roomName.typeface = Typeface.DEFAULT_BOLD
        }
        else {
            chatRoomViewHolder.roomName.typeface = Typeface.DEFAULT
        }

        // Set room Icon.
        setRoomIcon(chatRoomViewHolder.roomIcon, renderer.getChatRoomIcon(child)!!)
        return cView
    }

    /**
     * Returns the group view for the given `groupPosition`.
     *
     * @param groupPosition the group position of the desired view
     * @param isExpanded indicates if the view is currently expanded
     * @param convertView the view to fill with data
     * @param parent the parent view group
     */
    override fun getGroupView(groupPosition: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup): View {
        // Keeps reference to avoid future findViewById()
        var cView = convertView
        val groupViewHolder: GroupViewHolder
        if (cView == null) {
            cView = mInflater.inflate(R.layout.chatroom_list_group_row, parent, false)
            groupViewHolder = GroupViewHolder()
            groupViewHolder.ppsUserId = cView.findViewById(R.id.displayName)
            groupViewHolder.indicator = cView.findViewById(R.id.groupIndicatorView)
            cView.tag = groupViewHolder
        }
        else {
            groupViewHolder = cView.tag as GroupViewHolder
        }
        val group = getGroup(groupPosition)
        if (group != null) {
            val groupRenderer = getGroupRenderer(groupPosition)
            groupViewHolder.ppsUserId!!.text = groupRenderer.getDisplayName(group)
        }

        // Group expand indicator
        val indicatorResId = if (isExpanded) R.drawable.expanded_dark else R.drawable.collapsed_dark
        groupViewHolder.indicator!!.setImageResource(indicatorResId)
        return cView!!
    }

    /**
     * Returns the identifier of the group given by `groupPosition`.
     *
     * @param groupPosition the index of the group, which identifier we're looking for
     */
    override fun getGroupId(groupPosition: Int): Long {
        return groupPosition.toLong()
    }

    /**
     *
     */
    override fun hasStableIds(): Boolean {
        return true
    }

    /**
     * Indicates that all children are selectable.
     */
    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean {
        return true
    }

    /**
     * We keep one instance of view click listener to avoid unnecessary allocations.
     * Clicked positions are obtained from the view holder.
     */
    override fun onClick(view: View) {
        var obj = view.tag
        if (obj is ChatRoomViewHolder) {
            mViewHolder = view.tag as ChatRoomViewHolder
            val groupPos = mViewHolder!!.groupPosition
            val childPos = mViewHolder!!.childPosition
            obj = getChild(groupPos, childPos)
        }

        if (obj is ChatRoomWrapper) {
            val chatRoomWrapper = obj
            when (view.id) {
                R.id.room_view -> chatRoomListFragment.joinChatRoom(chatRoomWrapper)

                R.id.cb_autojoin -> {
                    // Set chatRoom autoJoin on first login
                    chatRoomWrapper.isAutoJoin = mViewHolder!!.autojoin.isChecked
                    if (chatRoomWrapper.isAutoJoin) {
                        MUCActivator.mucService.joinChatRoom(chatRoomWrapper)
                    }

                    // Continue to update server BookMarkConference data if bookmark is checked
                    if (mViewHolder!!.bookmark.isChecked) {
                        val pps = chatRoomWrapper.protocolProvider
                        val bookmarkManager = BookmarkManager.getBookmarkManager(pps!!.connection)
                        val entityBareJid = chatRoomWrapper.entityBareJid
                        chatRoomWrapper.setBookmark(mViewHolder!!.bookmark.isChecked)
                        try {
                            if (mViewHolder!!.bookmark.isChecked) {
                                bookmarkManager.addBookmarkedConference(chatRoomWrapper.bookmarkName, entityBareJid,
                                        chatRoomWrapper.isAutoJoin, chatRoomWrapper.nickResource,
                                        chatRoomWrapper.loadPassword())
                            }
                            else {
                                bookmarkManager.removeBookmarkedConference(entityBareJid)
                            }
                        } catch (e: SmackException.NoResponseException) {
                            Timber.w("Failed to update Bookmarks: %s", e.message)
                        } catch (e: SmackException.NotConnectedException) {
                            Timber.w("Failed to update Bookmarks: %s", e.message)
                        } catch (e: XMPPException.XMPPErrorException) {
                            Timber.w("Failed to update Bookmarks: %s", e.message)
                        } catch (e: InterruptedException) {
                            Timber.w("Failed to update Bookmarks: %s", e.message)
                        }
                    }
                }
                R.id.room_icon, R.id.cb_bookmark -> {
                    val ft = chatRoomListFragment.parentFragmentManager.beginTransaction()
                    ft.addToBackStack(null)
                    val chatRoomBookmarkFragment = ChatRoomBookmarkDialog.getInstance(chatRoomWrapper, this)
                    chatRoomBookmarkFragment.show(ft, "bmDdialog")
                }
                else -> {}
            }
        }
        else {
            Timber.w("Clicked item is not a chatRoom Wrapper")
        }
    }

    override fun onLongClick(view: View): Boolean {
        val chatRoomWrapper = view.tag
        if (chatRoomWrapper is ChatRoomWrapper) {
            chatRoomListFragment.showPopupMenu(view, chatRoomWrapper)
            return true
        }
        return false
    }

    /**
     * update bookmark check on dialog close
     */
    override fun onCloseDialog() {
        // retain current state unless change by user in dialog
        val chatRoomWrapper = getChild(mViewHolder!!.groupPosition, mViewHolder!!.childPosition) as ChatRoomWrapper?
        if (chatRoomWrapper != null) mViewHolder!!.bookmark.isChecked = chatRoomWrapper.isBookmarked
    }

    /**
     * Sets the room icon of the chatRoom row.
     *
     * @param roomIconView the room Icon image view
     * @param image the room Icon image view
     */
    private fun setRoomIcon(roomIconView: ImageView?, image: Drawable?) {
        var roomImage = image
        if (roomImage == null) {
            roomImage = ResourcesCompat.getDrawable(aTalkApp.appResources, R.drawable.ic_chatroom, null)
        }
        roomIconView!!.setImageDrawable(roomImage)
    }

    private class ChatRoomViewHolder {
        lateinit var roomName: TextView
        lateinit var statusMessage: TextView
        lateinit var roomIcon: ImageView
        lateinit var autojoin: CheckBox
        lateinit var bookmark: CheckBox
        lateinit var unreadCount: UnreadCountCustomView
        var groupPosition = 0
        var childPosition = 0
    }

    private class GroupViewHolder {
        var indicator: ImageView? = null
        var ppsUserId: TextView? = null
    }
}