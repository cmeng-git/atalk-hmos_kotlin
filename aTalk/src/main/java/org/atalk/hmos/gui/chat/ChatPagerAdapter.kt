/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.chat

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.PagerAdapter
import net.java.sip.communicator.service.gui.Chat
import net.java.sip.communicator.service.gui.event.ChatListener

/**
 * A pager adapter used to display active chats.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ChatPagerAdapter(fm: FragmentManager?, parent: ChatActivity) : FragmentStatePagerAdapter(fm!!), ChatListener {
    /**
     * The list of contained chat session ids.
     */
    private val chats: MutableList<String?>?

    /**
     * Parent `ChatActivity`.
     */
    private val parent: ChatActivity

    /**
     * Remembers currently displayed `ChatFragment`.
     */
    var currentChatFragment: ChatFragment? = null
        private set

    /**
     * Creates an instance of `ChatPagerAdapter` by specifying the parent
     * `ChatActivity` and its `FragmentManager`.
     *
     * fm the parent `FragmentManager`
     */
    init {
        chats = ChatSessionManager.activeChatsIDs
        this.parent = parent
        ChatSessionManager.addChatListener(this)
    }

    /**
     * Releases resources used by this instance. Once called this instance is considered invalid.
     */
    fun dispose() {
        ChatSessionManager.removeChatListener(this)
    }

    /**
     * Returns chat id corresponding to the given position.
     *
     * @param pos the position of the chat we're looking for
     *
     * @return chat id corresponding to the given position
     */
    fun getChatId(pos: Int): String? {
        synchronized(chats!!) {
            return if (chats.size <= pos) null else chats[pos]
        }
    }

    /**
     * Returns index of the `ChatPanel` in this adapter identified by given
     * `sessionId`.
     *
     * @param sessionId chat session identifier.
     *
     * @return index of the `ChatPanel` in this adapter identified by given
     * `sessionId`.
     */
    fun getChatIdx(sessionId: String?): Int {
        if (sessionId == null) return -1
        for (i in chats!!.indices) {
            if (getChatId(i) == sessionId) return i
        }
        return -1
    }

    /**
     * Removes the given chat session id from this pager if exist.
     *
     * @param chatId the chat id to remove from this pager
     */
    private fun removeChatSession(chatId: String?) {
        synchronized(chats!!) {
            if (chats.remove(chatId)) {
                notifyDataSetChanged()
            }
        }
    }

    /**
     * Removes all `ChatFragment`s from this pager.
     */
    fun removeAllChatSessions() {
        synchronized(chats!!) { chats.clear() }
        notifyDataSetChanged()
    }

    /**
     * Returns the position of the given `object` in this pager.
     * cmeng - Seem this is not call by PagerAdapter at all
     *
     * @return the position of the given `object` in this pager
     */
    override fun getItemPosition(`object`: Any): Int {
        val id = (`object` as ChatFragment).chatPanel!!.chatSession!!.chatId
        synchronized(chats!!) { if (chats.contains(id)) return chats.indexOf(id) }
        return PagerAdapter.POSITION_NONE
    }

    /**
     * Returns the `Fragment` at the given position in this pager.
     *
     * @return the `Fragment` at the given position in this pager
     */
    override fun getItem(pos: Int): Fragment {
        return ChatFragment.newInstance(chats!![pos])
    }

    /**
     * Instantiate the `ChatFragment` in the given container, at the given position.
     *
     * @param container the parent `ViewGroup`
     * @param position the position in the `ViewGroup`
     *
     * @return the created `ChatFragment`
     */
    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        return super.instantiateItem(container, position)
    }

    /**
     * Returns the count of contained `ChatFragment`s.
     *
     * @return the count of contained `ChatFragment`s
     */
    override fun getCount(): Int {
        synchronized(chats!!) { return chats.size }
    }

    /**
     * {@inheritDoc}
     */
    override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
        super.setPrimaryItem(container, position, `object`)

        /*
         * Notifies ChatFragments about their visibility state changes. This method is invoked
         * many times with the same parameter, so we keep track of last item and notify only on changes.
         *
         * This is required, because normal onResume/onPause fragment cycle doesn't work
         * as expected with pager adapter.
         */
        val newPrimary = `object` as ChatFragment
        if (newPrimary != currentChatFragment) {
            if (currentChatFragment != null) currentChatFragment!!.setPrimarySelected(false)
            newPrimary.setPrimarySelected(true)
        }
        currentChatFragment = newPrimary
    }

    override fun chatClosed(chat: Chat) {
        parent.runOnUiThread { removeChatSession((chat as ChatPanel).chatSession!!.chatId) }
    }

    override fun chatCreated(chat: Chat) {
        parent.runOnUiThread {
            synchronized(chats!!) {
                chats.add((chat as ChatPanel).chatSession!!.chatId)
                notifyDataSetChanged()
            }
        }
    }
}