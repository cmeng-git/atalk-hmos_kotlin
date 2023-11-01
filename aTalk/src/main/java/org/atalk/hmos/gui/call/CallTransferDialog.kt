/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014-2022 Eng Chong Meng
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
package org.atalk.hmos.gui.call

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Handler
import android.view.View
import android.widget.AbsListView
import android.widget.Button
import android.widget.ExpandableListView
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.protocol.CallPeer
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.OperationSetAdvancedTelephony
import org.atalk.hmos.R
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.contactlist.ContactListFragment
import org.atalk.hmos.gui.contactlist.model.BaseContactListAdapter
import org.atalk.hmos.gui.contactlist.model.MetaContactListAdapter
import org.atalk.hmos.gui.contactlist.model.MetaGroupExpandHandler
import timber.log.Timber

/**
 * The CallTransferDialog is for user to select the desired contact to transfer the call to.
 *
 * @author Eng Chong Meng
 */
class CallTransferDialog(mContext: Context?, private val mInitialPeer: CallPeer, callPeers: Collection<CallPeer>) : Dialog(mContext!!), ExpandableListView.OnChildClickListener, ExpandableListView.OnGroupClickListener, DialogInterface.OnShowListener {
    private var mCallPeer: CallPeer? = null
    private var mSelectedContact: Contact? = null
    private var mTransferButton: Button? = null

    /**
     * Contact list data model.
     */
    private var contactListAdapter: MetaContactListAdapter? = null

    /**
     * The contact list view.
     */
    private var transferListView: ExpandableListView? = null

    /**
     * Constructs the `CallTransferDialog`.
     * aTalk callPeers contains at most one callPeer for attended call transfer
     *
     * mContext android Context
     * initialPeer the callPeer that launches this dialog, and to which the call transfer request is sent
     * callPeers contains callPeer for attended call transfer, empty otherwise
     */
    init {
        if (callPeers.isNotEmpty()) {
            mCallPeer = callPeers.iterator().next()
        }
        Timber.d("Active call peers: %s", callPeers)
        setOnShowListener(this)
    }

    public override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(mInitialPeer.getPeerJid()!!.asBareJid())
        this.setContentView(R.layout.call_transfer_dialog)
        transferListView = findViewById(R.id.TransferListView)
        transferListView!!.setSelector(R.drawable.list_selector_state)
        transferListView!!.setOnChildClickListener(this)
        transferListView!!.setOnGroupClickListener(this)
        transferListView!!.choiceMode = AbsListView.CHOICE_MODE_SINGLE
        initListAdapter()
        mTransferButton = findViewById(R.id.buttonTransfer)
        mTransferButton!!.setOnClickListener { v: View? ->
            transferCall()
            closeDialog()
        }
        findViewById<View>(R.id.buttonCancel).setOnClickListener { v: View? -> closeDialog() }
        updateTransferState()
    }

    /**
     * Transfer call to the selected contact as Unattended or Attended if mCallPeer != null
     */
    private fun transferCall() {
        if (mCallPeer != null) {
            val callContact = mSelectedContact!!.contactJid!!
            if (callContact.isParentOf(mCallPeer!!.getPeerJid())) {
                CallManager.transferCall(mInitialPeer, mCallPeer!!)
                return
            }
        }
        CallManager.transferCall(mInitialPeer, mSelectedContact!!.address)
    }

    /**
     * Enable the mTransfer button if mSelected != null
     */
    private fun updateTransferState() {
        if (mSelectedContact == null) {
            mTransferButton!!.isEnabled = false
            mTransferButton!!.alpha = .3f
        } else {
            mTransferButton!!.isEnabled = true
            mTransferButton!!.alpha = 1.0f
        }
    }

    private fun initListAdapter() {
        transferListView!!.setAdapter(getContactListAdapter())

        // Attach contact groups expand memory
        val listExpandHandler = MetaGroupExpandHandler(contactListAdapter!!, transferListView!!)
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
        val adapter = listView.expandableListAdapter as BaseContactListAdapter
        val clicked = adapter.getChild(groupPosition, childPosition)
        if (clicked is MetaContact) {
            if (clicked.getContactsForOperationSet(OperationSetAdvancedTelephony::class.java)!!.isNotEmpty()) {
                mSelectedContact = clicked.getDefaultContact()
                v.isSelected = true
                updateTransferState()
                return true
            }
        }
        return false
    }

    /**
     * Expands/collapses the group given by `groupPosition`.
     *
     * Group collapse will clear all highlight of any selected contact; On expansion, allow time
     * for view to expand before proceed to refresh the selected contact's highlight
     *
     * @param parent the parent expandable list view
     * @param v the view
     * @param groupPosition the position of the group
     * @param id the identifier
     * @return `true` if the group click action has been performed
     */
    override fun onGroupClick(parent: ExpandableListView, v: View, groupPosition: Int, id: Long): Boolean {
        if (transferListView!!.isGroupExpanded(groupPosition)) transferListView!!.collapseGroup(groupPosition) else {
            transferListView!!.expandGroup(groupPosition, true)
            Handler().postDelayed({ refreshContactSelected(groupPosition) }, 500)
        }
        return true
    }

    /**
     * Refresh highlight for the selected contact when:
     * a. Dialog onShow
     * b. User collapse and expand group
     *
     * @param grpPosition the contact list group position
     */
    private fun refreshContactSelected(grpPosition: Int) {
        val lastIndex = transferListView!!.count
        for (index in 0..lastIndex) {
            val lPosition = transferListView!!.getExpandableListPosition(index)
            val groupPosition = ExpandableListView.getPackedPositionGroup(lPosition)
            if (grpPosition == -1 || groupPosition == grpPosition) {
                val childPosition = ExpandableListView.getPackedPositionChild(lPosition)
                val mContact = contactListAdapter!!.getChild(groupPosition, childPosition) as MetaContact?
                if (mContact != null) {
                    val mJid = mContact.getDefaultContact()!!.contactJid!!
                    val mView = transferListView!!.getChildAt(index)
                    if (mSelectedContact != null) {
                        if (mJid.isParentOf(mSelectedContact!!.contactJid)) {
                            mView.isSelected = true
                            break
                        }
                    } else if (mCallPeer != null) {
                        if (mJid.isParentOf(mCallPeer!!.getPeerJid())) {
                            mSelectedContact = mContact.getDefaultContact()
                            mView.isSelected = true
                            break
                        }
                    }
                }
            }
        }
    }

    override fun onShow(arg0: DialogInterface) {
        refreshContactSelected(-1)
        updateTransferState()
    }

    fun closeDialog() {
        // must clear dialogMode on exit dialog
        contactListAdapter!!.setDialogMode(false)
        cancel()
    }
}