/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atalk.hmos.gui.chat.conference

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ExpandableListView
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.gui.ContactList
import net.java.sip.communicator.service.protocol.CallConference
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import net.java.sip.communicator.service.protocol.OperationSetTelephonyConferencing
import net.java.sip.communicator.service.protocol.ProtocolProviderActivator
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.util.ConfigurationUtils
import org.atalk.hmos.R
import org.atalk.hmos.gui.call.CallManager
import org.atalk.hmos.gui.contactlist.ContactListFragment
import org.atalk.hmos.gui.contactlist.model.BaseContactListAdapter
import org.atalk.hmos.gui.contactlist.model.MetaContactListAdapter
import org.atalk.hmos.gui.contactlist.model.MetaGroupExpandHandler
import java.util.*

/**
 * The invite dialog is the one shown when the user clicks on the conference button in the chat toolbar.
 *
 * @author Eng Chong Meng
 */
class ConferenceCallInviteDialog @JvmOverloads constructor(mContext: Context?, conference: CallConference? = null, preselectedProvider: ProtocolProviderService? = null,
        protocolProviders: List<ProtocolProviderService>? = null, isJitsiVideobridge: Boolean = false) : Dialog(mContext!!), ExpandableListView.OnChildClickListener, DialogInterface.OnShowListener {
    private lateinit var mInviteButton: Button
    private lateinit var mCancelButton: Button

    /**
     * Contact list data model.
     */
    private var contactListAdapter: MetaContactListAdapter? = null

    /**
     * Meta contact groups expand memory.
     */
    private lateinit var listExpandHandler: MetaGroupExpandHandler

    /**
     * The contact list view.
     */
    private lateinit var contactListView: ExpandableListView

    /**
     * Stores last clicked `MetaContact`.
     */
    var clickedContact: MetaContact? = null

    /**
     * The source contact list.
     */
    private var srcContactList: ContactList? = null

    /**
     * The destination contact list.
     */
    private var destContactList: ContactList? = null

    /**
     * The telephony conference into which this instance is to invite participants.
     */
    private val conference: CallConference?

    /**
     * The previously selected protocol provider, with which this dialog has been instantiated.
     */
    private val preselectedProtocolProvider: ProtocolProviderService?

    /**
     * Indicates whether this conference invite dialog is associated with a Jitsi Videobridge invite.
     */
    private val isJitsiVideobridge: Boolean
    /**
     * Initializes a new `ConferenceCallInviteDialog` instance which is to invite
     * contacts/participants in a specific telephony conference.
     *
     * @param conference the telephony conference in which the new instance is to invite contacts/participants
     * @param preselectedProvider the preselected protocol provider
     * @param protocolProviders the protocol providers list
     * @param isJitsiVideobridge `true` if this dialog should create a conference through a Jitsi Videobridge;
     * otherwise, `false`
     */
    /**
     * Constructs the `ConferenceCallInviteDialog`.
     */
    /**
     * Creates an instance of `ConferenceCallInviteDialog` by specifying an already created
     * conference. To use when inviting contacts to an existing conference is needed.
     *
     * conference the existing `CallConference`
     */
    init {
        this.conference = conference
        preselectedProtocolProvider = preselectedProvider
        this.isJitsiVideobridge = isJitsiVideobridge
        if (preselectedProtocolProvider == null) initAccountSelectorPanel(protocolProviders)
        setOnShowListener(this)
    }

    /**
     * Creates an instance of `ConferenceCallInviteDialog` by specifying an already created
     * conference. To use when inviting contacts to an existing conference is needed.
     *
     * @param conference the existing `CallConference`
     */
    constructor(mContext: Context?, conference: CallConference?,
            preselectedProtocolProvider: ProtocolProviderService?, isJitsiVideobridge: Boolean) : this(mContext, conference, preselectedProtocolProvider, null, isJitsiVideobridge) {
    }

    /**
     * Creates an instance of `ConferenceCallInviteDialog` by specifying a preselected protocol
     * provider to be used and if this is an invite for a video bridge conference.
     *
     * @param protocolProviders the protocol providers list
     * @param isJitsiVideobridge `true` if this dialog should create a conference through a Jitsi Videobridge;
     * otherwise, `false`
     */
    constructor(mContext: Context?,
            protocolProviders: List<ProtocolProviderService>?, isJitsiVideobridge: Boolean) : this(mContext, null, null, protocolProviders, isJitsiVideobridge)

    /**
     * Creates an instance of `ConferenceCallInviteDialog` by specifying a preselected protocol
     * provider to be used and if this is an invite for a video bridge conference.
     *
     * @param selectedConfProvider the preselected protocol provider
     * @param isJitsiVideobridge `true` if this dialog should create a conference through a Jitsi Videobridge;
     * otherwise, `false`
     */
    constructor(mContext: Context?, selectedConfProvider: ProtocolProviderService?,
            isJitsiVideobridge: Boolean) : this(mContext, null, selectedConfProvider, null, isJitsiVideobridge) {
    }

    public override fun onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.service_gui_INVITE_CONTACT_TO_VIDEO_BRIDGE)
        this.setContentView(R.layout.videobridge_invite_dialog)
        contactListView = findViewById(R.id.ContactListView)
        contactListView.setSelector(R.drawable.list_selector_state)
        contactListView.setOnChildClickListener(this)

        // Adds context menu for contact list items
        registerForContextMenu(contactListView)
        initListAdapter()
        mInviteButton = findViewById(R.id.button_invite)
        if (mucContactList.isEmpty()) {
            mInviteButton.isEnabled = false
            mInviteButton.alpha = .3f
        } else {
            mInviteButton.isEnabled = true
            mInviteButton.alpha = 1.0f
        }
        mInviteButton.setOnClickListener { v: View? ->
            val mContacts: List<MetaContact> = LinkedList(mucContactList.values)
            if (mContacts.isNotEmpty()) {
                if (isJitsiVideobridge) inviteJitsiVideobridgeContacts(preselectedProtocolProvider, mContacts) else inviteContacts(mContacts)

                // Store the last used account in order to pre-select it next time.
                ConfigurationUtils.setLastCallConferenceProvider(preselectedProtocolProvider!!)
                closeDialog()
            }
        }
        mCancelButton = findViewById(R.id.buttonCancel)
        mCancelButton.setOnClickListener { v: View? -> closeDialog() }
        // this.initContactListData();
    }

    private fun initListAdapter() {
        contactListView.setAdapter(getContactListAdapter())

        // Attach contact groups expand memory
        listExpandHandler = MetaGroupExpandHandler(contactListAdapter!!, contactListView)
        listExpandHandler.bindAndRestore()

        // setDialogMode to true to avoid contacts being filtered
        contactListAdapter!!.setDialogMode(true)

        // Update ExpandedList View
        contactListAdapter!!.invalidateViews()
    }

    fun chkSelectedContact() {
        val mContacts: List<MetaContact> = LinkedList(mucContactList.values)
        val indexes = contactListAdapter!!.groupCount
        for (mContact in mContacts) {
            var childIdx: Int
            for (gIdx in 0 until indexes) {
                childIdx = contactListAdapter!!.getChildIndex(gIdx, mContact)
                if (childIdx != -1) {
                    contactListView.setItemChecked(childIdx, true)
                    break
                }
            }
        }
    }

    fun closeDialog() {
        // must clear dialogMode on exit dialog
        contactListAdapter!!.setDialogMode(false)
        cancel()
    }

    private fun getContactListAdapter(): MetaContactListAdapter? {
        if (contactListAdapter == null) {
            val clf = ContactListFragment() //(ContactListFragment) aTalk.getFragment(aTalk.CL_FRAGMENT);
            // Disable call button options
            contactListAdapter = MetaContactListAdapter(clf, false)
            contactListAdapter!!.initModelData()
        }
        // Do not include groups with zero member in main contact list
        contactListAdapter!!.nonZeroContactGroupList()
        return contactListAdapter
    }

    /**
     *
     */
    override fun onChildClick(listView: ExpandableListView, v: View, groupPosition: Int,
            childPosition: Int, id: Long): Boolean {
        val adapter: BaseContactListAdapter = listView.expandableListAdapter as BaseContactListAdapter
        val position = adapter.getListIndex(groupPosition, childPosition)
        contactListView.setSelection(position)
        // adapter.invalidateViews();

        // Get v index for multiple selection highlight
        val index = listView.getFlatListPosition(ExpandableListView.getPackedPositionForChild(groupPosition, childPosition))
        val clicked = adapter.getChild(groupPosition, childPosition)
        if (clicked is MetaContact) {
            if (MUC_OFFLINE_ALLOW
                    || clicked.getContactsForOperationSet(OperationSetMultiUserChat::class.java)!!.isNotEmpty()) {
                // Toggle muc Contact Selection
                val key = clicked.getMetaUID()
                if (mucContactList.containsKey(key)) {
                    mucContactList.remove(key)
                    listView.setItemChecked(index, false)
                    // v.setSelected(false);
                }
                else {
                    mucContactList[key] = clicked
                    listView.setItemChecked(index, true)
                    // v.setSelected(true); for single item selection only
                }
                if (mucContactList.isEmpty()) {
                    mInviteButton.isEnabled = false
                    mInviteButton.alpha = .3f
                }
                else {
                    mInviteButton.isEnabled = true
                    mInviteButton.alpha = 1.0f
                }
                return true
            }
            return false
        }
        return false
    }

    /**
     * Initializes the account selector panel.
     *
     * @param protocolProviders the list of protocol providers we'd like to show in the account selector box
     */
    private fun initAccountSelectorPanel(protocolProviders: List<ProtocolProviderService>?) {
        // Initialize the account selector box.
        if (protocolProviders != null && protocolProviders.isNotEmpty()) this.initAccountListData(protocolProviders) else this.initAccountListData()
    }

    /**
     * Initializes the account selector box with the given list of `ProtocolProviderService`
     * -s.
     *
     * @param protocolProviders the list of `ProtocolProviderService`-s we'd like to show in the account
     * selector box
     */
    private fun initAccountListData(protocolProviders: List<ProtocolProviderService>) {
        for (protocolProvider in protocolProviders) {
            // accountSelectorBox.addItem(protocolProvider);
        }

        // if (accountSelectorBox.getItemCount() > 0)
        // accountSelectorBox.setSelectedIndex(0);
    }

    /**
     * Initializes the account list.
     */
    private fun initAccountListData() {
        val protocolProviders = ProtocolProviderActivator.protocolProviders
        for (protocolProvider in protocolProviders) {
            val opSet = protocolProvider.getOperationSet(OperationSetTelephonyConferencing::class.java)
            if (opSet != null && protocolProvider.isRegistered) {
                // accountSelectorBox.addItem(protocolProvider);
            }
        }

        // Try to select the last used account if available.
        var pps = ConfigurationUtils.getLastCallConferenceProvider()
        if (pps == null && conference != null) {
            /*
             * Pick up the first account from the ones participating in the associated telephony
             * conference which supports OperationSetTelephonyConferencing.
             */
            for (call in conference.calls) {
                val callPps = call.pps
                if (callPps.getOperationSet(OperationSetTelephonyConferencing::class.java) != null) {
                    pps = callPps
                    break
                }
            }
        }
        // if (pps != null)
        // accountSelectorBox.setSelectedItem(pps);
        // else if (accountSelectorBox.getItemCount() > 0)
        // accountSelectorBox.setSelectedIndex(0);
    }

    /**
     * Invites the contacts to the chat conference.
     */
    private fun inviteContacts(mContacts: List<MetaContact>) {
        val selectedProviderCallees = HashMap<ProtocolProviderService, List<String>>()
        val callees = ArrayList<String>()

        // Collection<String> selectedContactAddresses = new ArrayList<String>();
        for (mContact in mContacts) {
            val mAddress = mContact.getDefaultContact()!!.address
            callees.add(mAddress)
        }

        // Invite all selected.
        if (callees.size > 0) {
            selectedProviderCallees[preselectedProtocolProvider!!] = callees
            if (conference != null) {
                CallManager.inviteToConferenceCall(selectedProviderCallees, conference)
            } else {
                CallManager.createConferenceCall(selectedProviderCallees)
            }
        }
    }

    /**
     * Invites the contacts to the chat conference.
     *
     * @param mContacts the list of contacts to invite
     */
    private fun inviteJitsiVideobridgeContacts(preselectedProvider: ProtocolProviderService?,
            mContacts: List<MetaContact>) {
        val callees = mutableListOf<String>()
        for (mContact in mContacts) {
            val mAddress = mContact.getDefaultContact()!!.address
            callees.add(mAddress)
        }

        // Invite all selected.
        if (callees.size > 0) {
            if (conference != null) {
                CallManager.inviteToJitsiVideobridgeConfCall(callees.toTypedArray(), conference.calls[0])
            } else {
                CallManager.createJitsiVideobridgeConfCall(preselectedProvider!!, callees.toTypedArray())
            }
        }
    }

    override fun onShow(arg0: DialogInterface) {
        val mContacts: List<MetaContact> = LinkedList(mucContactList.values)
        val indexes = contactListAdapter!!.groupCount
        for (mContact in mContacts) {
            var childIdx: Int
            for (gIdx in 0 until indexes) {
                childIdx = contactListAdapter!!.getChildIndex(gIdx, mContact)
                if (childIdx != -1) {
                    childIdx += gIdx + 1
                    contactListView.setItemChecked(childIdx, true)
                    break
                }
            }
        }
    }

    companion object {
        private const val MUC_OFFLINE_ALLOW = true

        /**
         * A map of all active chats i.e. metaContactChat, MUC etc.
         */
        private val mucContactList: MutableMap<String, MetaContact> = LinkedHashMap<String, MetaContact>()
    }
}