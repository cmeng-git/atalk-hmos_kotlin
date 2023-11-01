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
package org.atalk.hmos.gui.chat.conference

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.Button
import android.widget.ExpandableListView
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.gui.ContactList
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.chat.ChatPanel
import org.atalk.hmos.gui.chat.ChatTransport
import org.atalk.hmos.gui.chat.MetaContactChatSession
import org.atalk.hmos.gui.contactlist.ContactListFragment
import org.atalk.hmos.gui.contactlist.model.BaseContactListAdapter
import org.atalk.hmos.gui.contactlist.model.MetaContactListAdapter
import org.atalk.hmos.gui.contactlist.model.MetaGroupExpandHandler
import org.atalk.hmos.gui.util.ViewUtil
import org.jxmpp.jid.DomainJid
import java.util.*

/**
 * The invite dialog is the one shown when the user clicks on the conference button in the chat toolbar.
 *
 * @author Eng Chong Meng
 */
class ChatInviteDialog(mContext: Context?, mChatPanel: ChatPanel) : Dialog(mContext!!), ExpandableListView.OnChildClickListener, ExpandableListView.OnGroupClickListener, DialogInterface.OnShowListener {
    private val chatPanel: ChatPanel
    private val inviteChatTransport: ChatTransport

    /**
     * Contact list data model.
     */
    private var contactListAdapter: MetaContactListAdapter? = null

    /**
     * The contact list view.
     */
    private lateinit var contactListView: ExpandableListView
    private lateinit var mInviteButton: Button

    /**
     * Constructs the `ChatInviteDialog`.
     *
     * mChatPanel the `ChatPanel` corresponding to the `ChatRoom`, where the contact is invited.
     */
    init {
        chatPanel = mChatPanel
        inviteChatTransport = chatPanel.findInviteChatTransport()!!
        setOnShowListener(this)
    }

    public override fun onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.service_gui_INVITE_CONTACT_TO_CHAT)
        this.setContentView(R.layout.muc_invite_dialog)
        contactListView = findViewById(R.id.ContactListView)
        contactListView.setSelector(R.drawable.list_selector_state)
        contactListView.setOnChildClickListener(this)
        contactListView.setOnGroupClickListener(this)
        initListAdapter()

        mInviteButton = findViewById(R.id.button_invite)
        mInviteButton.setOnClickListener { v: View? ->
            inviteContacts()
            closeDialog()
        }

        // Default to include the current contact of the MetaContactChatSession to be invited
        if (chatPanel.chatSession is MetaContactChatSession) {
            val mContact = chatPanel.metaContact
            mucContactInviteList[mContact!!.getMetaUID()] = mContact
        }
        updateInviteState()
        val mCancelButton = findViewById<Button>(R.id.buttonCancel)
        mCancelButton.setOnClickListener { v: View? -> closeDialog() }
    }

    /**
     * Enable the Invite button if mucContactInviteList is not empty
     */
    private fun updateInviteState() {
        if (mucContactInviteList.isEmpty()) {
            mInviteButton.isEnabled = false
            mInviteButton.alpha = .3f
        } else {
            mInviteButton.isEnabled = true
            mInviteButton.alpha = 1.0f
        }
    }

    private fun initListAdapter() {
        contactListView.setAdapter(getContactListAdapter())

        // Attach contact groups expand memory
        val listExpandHandler = MetaGroupExpandHandler(contactListAdapter!!, contactListView)
        listExpandHandler.bindAndRestore()

        // setDialogMode to true to avoid contacts being filtered
        contactListAdapter!!.setDialogMode(true)

        // Update ExpandedList View
        contactListAdapter!!.invalidateViews()
    }

    private fun getContactListAdapter(): MetaContactListAdapter? {
        if (contactListAdapter == null) {
            // FFR: clf may be null; use new instance will crash dialog on select contact
            val clf = aTalk.getFragment(aTalk.CL_FRAGMENT) as ContactListFragment
            contactListAdapter = MetaContactListAdapter(clf, false)
            contactListAdapter!!.initModelData()
        }
        // Do not include groups with zero member in main contact list
        contactListAdapter!!.nonZeroContactGroupList()
        return contactListAdapter
    }

    /**
     * Callback method to be invoked when a child in this expandable list has been clicked.
     *
     * @param v The view within the expandable list/ListView that was clicked
     * @param groupPosition The group position that contains the child that was clicked
     * @param childPosition The child position within the group
     * @param id The row id of the child that was clicked
     * @return True if the click was handled
     */
    override fun onChildClick(listView: ExpandableListView, v: View, groupPosition: Int, childPosition: Int, id: Long): Boolean {
        // Get v index for multiple selection highlight
        val index = listView.getFlatListPosition(
                ExpandableListView.getPackedPositionForChild(groupPosition, childPosition))
        val adapter = listView.expandableListAdapter as BaseContactListAdapter
        val clicked = adapter.getChild(groupPosition, childPosition)
        if (clicked is MetaContact) {
            val metaContact = clicked
            if (MUC_OFFLINE_ALLOW
                    || metaContact.getContactsForOperationSet(OperationSetMultiUserChat::class.java)!!.isNotEmpty()) {
                // Toggle muc Contact Selection
                val key = metaContact.getMetaUID()
                if (mucContactInviteList.containsKey(key)) {
                    mucContactInviteList.remove(key)
                    listView.setItemChecked(index, false)
                    // v.setSelected(false);
                } else {
                    mucContactInviteList[key] = metaContact
                    listView.setItemChecked(index, true)
                    // v.setSelected(true); for single item selection only
                }
                updateInviteState()
                return true
            }
            return false
        }
        return false
    }

    /**
     * Expands/collapses the group given by `groupPosition`.
     *
     * Group collapse will clear all highlight of selected contacts; On expansion
     * allow time for view to expand before proceed to refresh the selected contacts' highlight
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
            Handler().postDelayed({ refreshContactSelected(groupPosition) }, 500)
        }
        return true
    }

    /**
     * The `ChatInviteContactListFilter` is `InviteContactListFilter` which doesn't list
     * contact that don't have persistence addresses (for example private messaging contacts are not listed).
     */
    private inner class ChatInviteContactListFilter // extends InviteContactListFilter
    (sourceContactList: ContactList?) {
        /**
         * The Multi User Chat operation set instance.
         */
        private val opSetMUC = inviteChatTransport.protocolProvider.getOperationSet(OperationSetMultiUserChat::class.java)

        // @Override
        //        public boolean isMatching(UIContact uiContact)
        //        {
        //            SourceContact contact = (SourceContact) uiContact.getDescriptor();
        //            return !opSetMUC.isPrivateMessagingContact(contact.getContactAddress());
        //        }
    }

    /**
     * Invites the contacts to the chat conference.
     */
    private fun inviteContacts() {
        val selectedContactAddresses = ArrayList<String>()
        val selectedContacts: List<MetaContact> = LinkedList(mucContactInviteList.values)
        if (selectedContacts.isEmpty()) return

        // Obtain selected contacts.
        for (uiContact in selectedContacts) {
            // skip server/system account
            val jid = uiContact.getDefaultContact()!!.contactJid
            if (jid == null || jid is DomainJid) {
                aTalkApp.showToastMessage(R.string.service_gui_SEND_MESSAGE_NOT_SUPPORTED, uiContact.getDisplayName())
                continue
            }
            val mAddress: String = uiContact.getDefaultContact()!!.address
            selectedContactAddresses.add(mAddress)
        }

        // Invite all selected.
        if (selectedContactAddresses.isNotEmpty()) {
            chatPanel.inviteContacts(inviteChatTransport, selectedContactAddresses,
                    ViewUtil.toString(findViewById(R.id.text_reason)))
        }
    }

    /**
     * Refresh highlight for all the selected contacts when:
     * a. Dialog onShow
     * b. User collapse and expand group
     *
     * @param grpPosition the contact list group position
     */
    private fun refreshContactSelected(grpPosition: Int) {
        val mContactList = mucContactInviteList.values
        val lastIndex = contactListView.count
        for (index in 0..lastIndex) {
            val lPosition = contactListView.getExpandableListPosition(index)
            val groupPosition = ExpandableListView.getPackedPositionGroup(lPosition)
            if (grpPosition == -1 || groupPosition == grpPosition) {
                val childPosition = ExpandableListView.getPackedPositionChild(lPosition)
                val mContact = contactListAdapter!!.getChild(groupPosition, childPosition) as MetaContact?
                        ?: continue
                for (metaContact in mContactList) {
                    if (metaContact == mContact) {
                        contactListView.setItemChecked(index, true)
                        break
                    }
                }
            }
        }
    }

    override fun onShow(arg0: DialogInterface) {
        refreshContactSelected(-1)
        updateInviteState()
    }

    private fun closeDialog() {
        // must clear dialogMode on exit dialog
        contactListAdapter!!.setDialogMode(false)
        cancel()
    }

    companion object {
        /**
         * Allow offline contact selection for invitation
         */
        private const val MUC_OFFLINE_ALLOW = true

        /**
         * A reference map of all invitees i.e. MetaContact UID to MetaContact .
         */
        private val mucContactInviteList: MutableMap<String, MetaContact> = LinkedHashMap<String, MetaContact>()
    }
}