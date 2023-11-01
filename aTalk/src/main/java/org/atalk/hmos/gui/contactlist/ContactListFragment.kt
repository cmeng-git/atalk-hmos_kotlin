/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.contactlist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ExpandableListAdapter
import android.widget.ExpandableListView
import android.widget.ExpandableListView.OnGroupClickListener
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.FragmentTransaction
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging
import net.java.sip.communicator.service.protocol.OperationSetExtendedAuthorizations
import net.java.sip.communicator.util.ConfigurationUtils.isTtsEnable
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.aTalkApp.Companion.getResString
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.chat.ChatPanel
import org.atalk.hmos.gui.chat.ChatSessionManager
import org.atalk.hmos.gui.chat.ChatSessionManager.createChatForChatId
import org.atalk.hmos.gui.chat.ChatSessionManager.getActiveChats
import org.atalk.hmos.gui.chat.ChatSessionManager.getChatIntent
import org.atalk.hmos.gui.chat.ChatSessionManager.removeActiveChat
import org.atalk.hmos.gui.chat.ChatSessionManager.removeAllActiveChats
import org.atalk.hmos.gui.chat.chatsession.ChatSessionFragment
import org.atalk.hmos.gui.contactlist.model.MetaContactListAdapter
import org.atalk.hmos.gui.contactlist.model.MetaGroupExpandHandler
import org.atalk.hmos.gui.contactlist.model.QueryContactListAdapter
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.share.ShareActivity
import org.atalk.hmos.gui.util.EntityListHelper.eraseAllEntityHistory
import org.atalk.hmos.gui.util.EntityListHelper.eraseEntityChatHistory
import org.atalk.hmos.gui.util.EntityListHelper.removeEntity
import org.atalk.hmos.gui.util.EntityListHelper.removeMetaContactGroup
import org.atalk.hmos.gui.util.ViewUtil.toString
import org.atalk.service.osgi.OSGiFragment
import org.jxmpp.jid.DomainJid
import timber.log.Timber

/**
 * Class to display the MetaContacts in Expandable List View
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class ContactListFragment : OSGiFragment(), OnGroupClickListener {
    /**
     * Search options menu items.
     */
    private var mSearchItem: MenuItem? = null

    /**
     * Contact TTS option item
     */
    private var mContactTtsEnable: MenuItem? = null

    /**
     * Contact list data model.
     */
    private var contactListAdapter: MetaContactListAdapter? = null

    /**
     * Meta contact groups expand memory.
     */
    private var listExpandHandler: MetaGroupExpandHandler? = null

    /**
     * List model used to search contact list and contact sources.
     */
    private var sourcesAdapter: QueryContactListAdapter? = null
    /**
     * Returns the contact list view.
     *
     * @return the contact list view
     */
    /**
     * The contact list view.
     */
    lateinit var contactListView: ExpandableListView
        protected set

    /**
     * Stores recently clicked contact group.
     */
    private var clickedGroup: MetaContactGroup? = null
    private var context: Context? = null

    /**
     * Creates new instance of `ContactListFragment`.
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
        this.context = context
    }

    /**
     * {@inheritDoc}
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (AndroidGUIActivator.bundleContext == null) {
            return null
        }
        val content = inflater.inflate(R.layout.contact_list, container, false) as ViewGroup
        contactListView = content.findViewById(R.id.contactListView)
        contactListView.setSelector(R.drawable.list_selector_state)
        contactListView.setOnGroupClickListener(this)
        initContactListAdapter()
        return content
    }

    /**
     * Initialize the contact list adapter;
     */
    private fun initContactListAdapter() {
        contactListView.setAdapter(getContactListAdapter())

        // Attach contact groups expand memory
        listExpandHandler = MetaGroupExpandHandler(contactListAdapter!!, contactListView)
        listExpandHandler!!.bindAndRestore()

        // Restore search state based on entered text
        if (mSearchItem != null) {
            val searchView = mSearchItem!!.actionView as SearchView?
            val filter = toString(searchView!!.findViewById(R.id.search_src_text))
            filterContactList(filter)
            bindSearchListener()
        } else {
            contactListAdapter!!.filterData("")
        }

        // Restore scroll position
        contactListView.setSelectionFromTop(scrollPosition, scrollTopPosition)
    }

    /**
     * {@inheritDoc}
     */
    override fun onResume() {
        super.onResume()

        // Invalidate view to update read counter and expand groups (collapsed when access settings)
        if (contactListAdapter != null) {
            contactListAdapter!!.expandAllGroups()
            contactListAdapter!!.invalidateViews()
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

        if (contactListView != null) {
            // Save scroll position
            scrollPosition = contactListView.firstVisiblePosition
            val itemView = contactListView.getChildAt(0)
            scrollTopPosition = itemView?.top ?: 0

            // Dispose of group expand memory
            if (listExpandHandler != null) {
                listExpandHandler!!.unbind()
                listExpandHandler = null
            }

            contactListView.setAdapter(null as ExpandableListAdapter?)
            if (contactListAdapter != null) {
                contactListAdapter!!.dispose()
                contactListAdapter = null
            }
            disposeSourcesAdapter()
        }
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
                filterContactList("")
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

    /**
     * Get the MetaContact list with media buttons
     *
     * @return MetaContact list showing the media buttons
     */
    fun getContactListAdapter(): MetaContactListAdapter {
        if (contactListAdapter == null) {
            contactListAdapter = MetaContactListAdapter(this, true)
            contactListAdapter!!.initModelData()
        }

        // Do not include groups with zero member in main contact list
        // contactListAdapter.nonZeroContactGroupList();
        return contactListAdapter!!
    }

    private fun getSourcesAdapter(): QueryContactListAdapter {
        if (sourcesAdapter == null) {
            sourcesAdapter = QueryContactListAdapter(this, getContactListAdapter())
            sourcesAdapter!!.initModelData()
        }
        return sourcesAdapter!!
    }

    private fun disposeSourcesAdapter() {
        if (sourcesAdapter != null) {
            sourcesAdapter!!.dispose()
        }
        sourcesAdapter = null
    }

    fun showPopUpMenuGroup(groupView: View?, group: MetaContactGroup?) {
        // Inflate chatRoom list popup menu
        val popup = PopupMenu(context!!, groupView!!)
        val menu = popup.menu
        popup.menuInflater.inflate(R.menu.group_menu, menu)
        popup.setOnMenuItemClickListener(PopupMenuItemClick())

        // Remembers clicked metaContactGroup
        clickedGroup = group
        popup.show()
    }

    /**
     * Inflates contact Item popup menu.
     * Avoid using android contextMenu (in fragment) - truncated menu list
     *
     * @param contactView click view.
     * @param metaContact an instance of MetaContact.
     */
    fun showPopupMenuContact(contactView: View?, metaContact: MetaContact?) {
        // Inflate contact list popup menu
        val popup = PopupMenu(context!!, contactView!!)
        val menu = popup.menu
        popup.menuInflater.inflate(R.menu.contact_ctx_menu, menu)
        popup.setOnMenuItemClickListener(PopupMenuItemClick())

        // Remembers clicked contact
        clickedContact = metaContact

        // Checks if close chat option should be visible for this contact
        val closeChatVisible = ChatSessionManager.getActiveChat(clickedContact) != null
        menu.findItem(R.id.close_chat).isVisible = closeChatVisible

        // Close all chats option should be visible if chatList is not empty
        val chatList = getActiveChats()
        val visible = chatList.size > 1 || chatList.size == 1 && !closeChatVisible
        menu.findItem(R.id.close_all_chats).isVisible = visible

        // Do not want to offer erase all contacts' chat history
        menu.findItem(R.id.erase_all_contact_chat_history).isVisible = false

        // Checks if the re-request authorization item should be visible
        val contact = clickedContact!!.getDefaultContact()
        if (contact == null) {
            Timber.w("No default contact for: %s", clickedContact)
            return
        }

        // update TTS enable option item title for the contact only if not DomainJid
        mContactTtsEnable = menu.findItem(R.id.contact_tts_enable)
        val jid = contact.contactJid
        if (jid == null || jid is DomainJid) {
            mContactTtsEnable!!.isVisible = false
        } else {
            val ttsOption = getResString(if (contact.isTtsEnable!!) R.string.service_gui_TTS_DISABLE else R.string.service_gui_TTS_ENABLE)
            mContactTtsEnable!!.title = ttsOption
            mContactTtsEnable!!.isVisible = isTtsEnable()
        }
        val pps = contact.protocolProvider
        if (pps == null) {
            Timber.w("No protocol provider found for: %s", contact)
            return
        }

        // Cannot send unsubscribed or move group if user in not online
        val userRegistered = pps.isRegistered
        menu.findItem(R.id.remove_contact).isVisible = userRegistered
        menu.findItem(R.id.move_contact).isVisible = userRegistered
        val authOpSet = pps.getOperationSet(OperationSetExtendedAuthorizations::class.java)
        val reRequestVisible = authOpSet?.getSubscriptionStatus(contact) != null && authOpSet.getSubscriptionStatus(contact) != OperationSetExtendedAuthorizations.SubscriptionStatus.Subscribed
        menu.findItem(R.id.re_request_auth).isVisible = reRequestVisible
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
            val ft: FragmentTransaction
            val chatPanel = ChatSessionManager.getActiveChat(clickedContact)
            return when (item.itemId) {
                R.id.close_chat -> {
                    chatPanel?.let { onCloseChat(it) }
                    true
                }
                R.id.close_all_chats -> {
                    onCloseAllChats()
                    true
                }
                R.id.erase_contact_chat_history -> {
                    eraseEntityChatHistory(context!!, clickedContact!!, null, null)
                    true
                }
                R.id.erase_all_contact_chat_history -> {
                    eraseAllEntityHistory(context!!)
                    true
                }
                R.id.contact_tts_enable -> {
                    if (clickedContact != null) {
                        val contact = clickedContact!!.getDefaultContact()
                        if (contact!!.isTtsEnable!!) {
                            contact.isTtsEnable = false
                            mContactTtsEnable!!.setTitle(R.string.service_gui_TTS_ENABLE)
                        } else {
                            contact.isTtsEnable = true
                            mContactTtsEnable!!.setTitle(R.string.service_gui_TTS_DISABLE)
                        }
                        createChatForChatId(clickedContact!!.getMetaUID(),
                                ChatSessionManager.MC_CHAT)!!.updateChatTtsOption()
                    }
                    true
                }
                R.id.rename_contact -> {
                    // Show rename contact dialog
                    ft = parentFragmentManager.beginTransaction()
                    ft.addToBackStack(null)
                    val renameFragment = ContactRenameDialog.getInstance(clickedContact)
                    renameFragment.show(ft, "renameDialog")
                    true
                }
                R.id.remove_contact -> {
                    removeEntity(context!!, clickedContact!!, chatPanel)
                    true
                }
                R.id.move_contact -> {
                    // Show move contact dialog
                    ft = parentFragmentManager.beginTransaction()
                    ft.addToBackStack(null)
                    val newFragment = MoveToGroupDialog.getInstance(clickedContact)
                    newFragment.show(ft, "moveDialog")
                    true
                }
                R.id.re_request_auth -> {
                    if (clickedContact != null) requestAuthorization(clickedContact!!.getDefaultContact()!!)
                    true
                }
                R.id.send_contact_file ->                     // ChatPanel clickedChat = ChatSessionManager.getActiveChat(clickedContact);
                    // AttachOptionDialog attachOptionDialog = new AttachOptionDialog(mActivity,
                    // clickedContact);
                    // attachOptionDialog.show();
                    true
                R.id.remove_group -> {
                    removeMetaContactGroup(clickedGroup!!)
                    true
                }
                R.id.contact_info -> {
                    startContactInfoActivity(clickedContact)
                    true
                }
                R.id.contact_ctx_menu_exit -> true
                else -> false
            }
        }
    }

    /**
     * Method fired when given chat is being closed.
     *
     * @param closedChat closed `ChatPanel`.
     */
    fun onCloseChat(closedChat: ChatPanel?) {
        removeActiveChat(closedChat)
        if (contactListAdapter != null) contactListAdapter!!.notifyDataSetChanged()
    }

    /**
     * Method fired when all chats are being closed.
     */
    fun onCloseAllChats() {
        removeAllActiveChats()
        if (contactListAdapter != null) contactListAdapter!!.notifyDataSetChanged()
    }

    /**
     * Requests authorization for contact.
     *
     * @param contact the contact for which we request authorization
     */
    private fun requestAuthorization(contact: Contact) {
        val authOpSet = contact.protocolProvider.getOperationSet(OperationSetExtendedAuthorizations::class.java)
                ?: return
        object : Thread() {
            override fun run() {
                val loginRenderer = AndroidGUIActivator.loginRenderer
                val request = (loginRenderer?.getAuthorizationHandler()?.createAuthorizationRequest(contact))
                        ?: return

                val any = try {
                    authOpSet.reRequestAuthorization(request, contact)
                } catch (e: OperationFailedException) {
                    val ctx = aTalkApp.globalContext
                    DialogActivity.showConfirmDialog(ctx, ctx.getString(R.string.service_gui_RE_REQUEST_AUTHORIZATION),
                        e.message, null, null)
                }
            }
        }.start()
    }

    /**
     * Starts the AccountInfoPresenceActivity for clicked Account
     *
     * @param metaContact the `Contact` for which info to be opened.
     */
    private fun startContactInfoActivity(metaContact: MetaContact?) {
        val statusIntent = Intent(context, ContactInfoActivity::class.java)
        statusIntent.putExtra(ContactInfoActivity.INTENT_CONTACT_ID, metaContact!!.getDisplayName())
        startActivity(statusIntent)
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
        if (contactListView.isGroupExpanded(groupPosition)) contactListView.collapseGroup(groupPosition) else {
            contactListView.expandGroup(groupPosition, true)
        }
        return true
    }

    /**
     * cmeng: when metaContact is owned by two different user accounts, the first launched chatSession
     * will take predominant over subsequent metaContact chat session launches by another account
     */
    fun startChat(metaContact: MetaContact) {
        if (metaContact.getDefaultContact() == null) {
            aTalkApp.showToastMessage(R.string.service_gui_CONTACT_INVALID, metaContact.getDisplayName())
        }

        // Default for domainJid - always show chat session
        if (metaContact.getDefaultContact()!!.contactJid is DomainJid) {
            startChatActivity(metaContact)
        }
        if (metaContact.getContactsForOperationSet(OperationSetBasicInstantMessaging::class.java)!!.isNotEmpty()) {
            startChatActivity(metaContact)
        }
    }

    /**
     * Starts the chat activity for the given metaContact.
     *
     * @param descriptor `MetaContact` for which chat activity will be started.
     */
    private fun startChatActivity(descriptor: Any) {
        var chatIntent = getChatIntent(descriptor)
        if (chatIntent != null) {
            // Get share object parameters for use with chatIntent if any.
            val shareIntent = ShareActivity.getShareIntent(chatIntent)
            if (shareIntent != null) {
                chatIntent = shareIntent
            }
            startActivity(chatIntent)
        } else {
            Timber.w("Failed to start chat with %s", descriptor)
        }
    }

    open fun getClickedContact(): MetaContact? {
        return clickedContact
    }
    /**
     * Filters contact list for given `query`.
     *
     * @param query the query string that will be used for filtering contacts.
     */
    private fun filterContactList(query: String?) {
        if (StringUtils.isEmpty(query)) {
            // Cancel any pending queries
            disposeSourcesAdapter()

            // Display the contact list
            if (contactListView.expandableListAdapter != getContactListAdapter()) {
                contactListView.setAdapter(getContactListAdapter())
                contactListAdapter!!.filterData("")
            }

            // Restore previously collapsed groups
            if (listExpandHandler != null) {
                listExpandHandler!!.bindAndRestore()
            }
        } else {
            // Unbind group expand memory
            if (listExpandHandler != null) listExpandHandler!!.unbind()

            // Display search results
            if (contactListView.expandableListAdapter != getSourcesAdapter()) {
                contactListView.setAdapter(getSourcesAdapter())
            }

            // Update query string
            sourcesAdapter!!.filterData(query!!)
        }
    }

    /**
     * Class used to implement `SearchView` listeners for compatibility purposes.
     */
    internal inner class SearchViewListener : SearchView.OnQueryTextListener, SearchView.OnCloseListener {
        override fun onQueryTextSubmit(query: String): Boolean {
            filterContactList(query)
            return true
        }

        override fun onQueryTextChange(query: String): Boolean {
            filterContactList(query)
            return true
        }

        override fun onClose(): Boolean {
            filterContactList("")
            return true
        }
    }

    /**
     * Update the unread message badge for the specified metaContact
     * The unread count is pre-stored in the metaContact
     *
     * @param metaContact The MetaContact to be updated
     */
    fun updateUnreadCount(metaContact: MetaContact?) {
        runOnUiThread {
            if (metaContact != null && contactListAdapter != null) {
                val unreadCount = metaContact.getUnreadCount()
                contactListAdapter!!.updateUnreadCount(metaContact, unreadCount)
                val csf = aTalk.getFragment(aTalk.CHAT_SESSION_FRAGMENT)
                if (csf is ChatSessionFragment) {
                    csf.updateUnreadCount(metaContact.getDefaultContact()!!.address, unreadCount)
                }
            }
        }
    }

    companion object {
        /**
         * Stores last clicked `MetaContact`; take care activity destroyed by OS.
         */
        var clickedContact: MetaContact? = null
            protected set

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