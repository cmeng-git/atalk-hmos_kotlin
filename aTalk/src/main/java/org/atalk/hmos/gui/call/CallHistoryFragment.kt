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
package org.atalk.hmos.gui.call

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.AsyncTask
import android.util.SparseBooleanArray
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.DatePicker
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.TimePicker
import androidx.fragment.app.FragmentActivity
import net.java.sip.communicator.impl.callhistory.CallHistoryActivator
import net.java.sip.communicator.service.callhistory.CallRecord
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import net.java.sip.communicator.service.protocol.CallPeerState
import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony
import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.OperationSetVideoTelephony
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener
import net.java.sip.communicator.util.account.AccountUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.call.telephony.TelephonyFragment
import org.atalk.hmos.gui.chat.ChatFragment
import org.atalk.hmos.gui.contactlist.model.MetaContactRenderer
import org.atalk.hmos.gui.util.EntityListHelper
import org.atalk.service.osgi.OSGiActivity
import org.atalk.service.osgi.OSGiFragment
import org.jxmpp.jid.DomainBareJid
import org.osgi.framework.Bundle
import timber.log.Timber
import java.util.*

/**
 * The user interface that allows user to view the call record history.
 *
 * @author Eng Chong Meng
 */
class CallHistoryFragment : OSGiFragment(), View.OnClickListener, ContactPresenceStatusListener, EntityListHelper.TaskCompleted {
    /**
     * A map of <contact></contact>, MetaContact>
     */
    private val mMetaContacts = LinkedHashMap<String, MetaContact>()

    /**
     * The list of call records
     */
    private val callRecords = ArrayList<CallRecord>()

    /**
     * The Call record list view adapter for user selection
     */
    private var callHistoryAdapter: CallHistoryAdapter? = null

    /**
     * The call history list view representing the chat.
     */
    private lateinit var callListView: ListView

    /**
     * UI thread handler used to call all operations that access data model. This guarantees that
     * it's accessed from the main thread.
     */
    private val uiHandler = OSGiActivity.uiHandler

    /**
     * View for room configuration title description from the room configuration form
     */
    private var mTitle: TextView? = null
    private var mContext: Context? = null

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
    fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val contentView = inflater.inflate(R.layout.call_history, container, false)
        mTitle = contentView.findViewById(R.id.call_history)
        callListView = contentView.findViewById(R.id.callListView)!!
        callHistoryAdapter = CallHistoryAdapter(inflater)
        callListView.adapter = callHistoryAdapter

        // Using the contextual action mode with multi-selection
        callListView.choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
        callListView.setMultiChoiceModeListener(mMultiChoiceListener)
        return contentView
    }

    /**
     * Adapter displaying all the available call history records for user selection.
     */
    private inner class CallHistoryAdapter(inflater: LayoutInflater) : BaseAdapter() {
        private val mInflater: LayoutInflater
        val CALL_RECORD = 1

        init {
            mInflater = inflater
            GetCallRecords(Date()).execute()
        }

        override fun getCount(): Int {
            return callRecords.size
        }

        override fun getItem(position: Int): Any {
            return callRecords[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getItemViewType(position: Int): Int {
            return CALL_RECORD
        }

        override fun getViewTypeCount(): Int {
            return 1
        }

        override fun isEmpty(): Boolean {
            return count == 0
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var cView = convertView
            val callRecordViewHolder: CallRecordViewHolder
            val callRecord = callRecords[position]
            if (cView == null) {
                cView = mInflater.inflate(R.layout.call_history_row, parent, false)
                callRecordViewHolder = CallRecordViewHolder()
                callRecordViewHolder.avatar = cView.findViewById(R.id.avatar)
                callRecordViewHolder.callType = cView.findViewById(R.id.callType)
                callRecordViewHolder.callButton = cView.findViewById(R.id.callButton)
                callRecordViewHolder.callButton!!.setOnClickListener(this@CallHistoryFragment)
                callRecordViewHolder.callButton!!.tag = callRecordViewHolder
                callRecordViewHolder.callVideoButton = cView.findViewById(R.id.callVideoButton)
                callRecordViewHolder.callVideoButton!!.setOnClickListener(this@CallHistoryFragment)
                callRecordViewHolder.callVideoButton!!.tag = callRecordViewHolder
                callRecordViewHolder.callType = cView.findViewById(R.id.callType)
                callRecordViewHolder.contactId = cView.findViewById(R.id.contactId)
                callRecordViewHolder.callInfo = cView.findViewById(R.id.callInfo)
                cView.tag = callRecordViewHolder
            } else {
                callRecordViewHolder = cView.tag as CallRecordViewHolder
            }
            callRecordViewHolder.childPosition = position

            // Must init child Tag here as reused convertView may not necessary contains the correct crWrapper
            // View callInfoView = convertView.findViewById(R.id.callInfoView);
            // callInfoView.setOnClickListener(CallHistoryFragment.this);
            // callInfoView.setOnLongClickListener(CallHistoryFragment.this);
            val peerRecord = callRecord.peerRecords[0]
            val peer = peerRecord.peerAddress!!
            val metaContact = mMetaContacts[peer.split("/".toRegex())[0]]
            callRecordViewHolder.metaContact = metaContact
            if (metaContact != null) {
                val avatar = MetaContactRenderer.getAvatarDrawable(metaContact)!!
                ChatFragment.setAvatar(callRecordViewHolder.avatar, avatar)
            }
            setCallState(callRecordViewHolder.callType, callRecord)
            callRecordViewHolder.callButton!!.visibility = if (isShowCallBtn(metaContact)) View.VISIBLE else View.GONE
            callRecordViewHolder.callVideoButton!!.visibility = if (isShowVideoCallBtn(metaContact)) View.VISIBLE else View.GONE
            callRecordViewHolder.contactId!!.text = peerRecord.peerAddress
            callRecordViewHolder.callInfo!!.text = callRecord.toString()
            return cView!!
        }

        /**
         * Retrieve the call history records from locally stored database
         * Populate the fragment with the call record for use in getView()
         */
        inner class GetCallRecords(val mEndDate: Date) : AsyncTask<Void?, Void?, Void?>() {
            init {
                callRecords.clear()
                mMetaContacts.clear()
                callListView.clearChoices()
            }

            override fun doInBackground(vararg params: Void?): Void? {
                initMetaContactList()
                var callRecordPPS: Collection<CallRecord>
                val CHS = CallHistoryActivator.getCallHistoryService()!!
                val providers = AccountUtils.registeredProviders
                for (pps in providers) {
                    if (pps.connection != null && pps.connection!!.isAuthenticated) {
                        addContactStatusListener(pps)
                        val accountId = pps.accountID
                        val userUuId = accountId.accountUniqueID!!
                        callRecordPPS = CHS.findByEndDate(userUuId, mEndDate)!!
                        if (callRecordPPS.isNotEmpty()) callRecords.addAll(callRecordPPS)
                    }
                }
                return null
            }

            override fun onPostExecute(result: Void?) {
                if (callRecords.size > 0) {
                    callHistoryAdapter!!.notifyDataSetChanged()
                }
                setTitle()
            }
        }
    }

    /**
     * Adds the given `addContactPresenceStatusListener` to listen for contact presence status change.
     *
     * @param pps the `ProtocolProviderService` for which we add the listener.
     */
    private fun addContactStatusListener(pps: ProtocolProviderService) {
        val presenceOpSet = pps.getOperationSet(OperationSetPresence::class.java)
        if (presenceOpSet != null) {
            presenceOpSet.removeContactPresenceStatusListener(this)
            presenceOpSet.addContactPresenceStatusListener(this)
        }
    }

    /**
     * Sets the call state.
     *
     * @param callStateView the call state image view
     * @param callRecord    the call record.
     */
    private fun setCallState(callStateView: ImageView?, callRecord: CallRecord) {
        val peerRecord = callRecord.peerRecords[0]
        val callState = peerRecord.state
        val resId = if (CallRecord.IN == callRecord.direction) {
            if (callState === CallPeerState.CONNECTED) R.drawable.call_incoming else R.drawable.call_incoming_missed
        } else {
            R.drawable.call_outgoing
        }
        callStateView!!.setImageResource(resId)
    }

    private fun setTitle() {
        val title = (aTalkApp.getResString(R.string.service_gui_CALL_HISTORY_GROUP_NAME)
                + " (" + callRecords.size + ")")
        mTitle!!.text = title
    }

    // Handle only if contactImpl instanceof MetaContact;
    private fun isShowCallBtn(contactImpl: Any?): Boolean {
        if (contactImpl is MetaContact) {
            val metaContact = contactImpl
            var isDomainJid = false
            if (metaContact.getDefaultContact() != null) isDomainJid = metaContact.getDefaultContact()!!.contactJid is DomainBareJid
            return isDomainJid || isShowButton(metaContact, OperationSetBasicTelephony::class.java)
        }
        return false
    }

    fun isShowVideoCallBtn(contactImpl: Any?): Boolean {
        return (contactImpl is MetaContact
                && isShowButton(contactImpl as MetaContact?, OperationSetVideoTelephony::class.java))
    }

    private fun isShowButton(metaContact: MetaContact?, opSetClass: Class<out OperationSet>): Boolean {
        return metaContact?.getOpSetSupportedContact(opSetClass) != null
    }

    /**
     * Initializes the adapter data.
     */
    fun initMetaContactList() {
        val contactListService = AndroidGUIActivator.contactListService
        if (contactListService != null) {
            addContacts(contactListService.getRoot())
        }
    }

    /**
     * Adds all child contacts for the given `group`. Omit metaGroup of zero child.
     *
     * @param group the group, which child contacts to add
     */
    private fun addContacts(group: MetaContactGroup) {
        if (group.countChildContacts() > 0) {

            // Use Iterator to avoid ConcurrentModificationException on addContact()
            val childContacts = group.getChildContacts()!!
            while (childContacts.hasNext()) {
                val metaContact = childContacts.next()!!
                val contactId = metaContact.getDefaultContact()!!.address
                mMetaContacts[contactId] = metaContact
            }
        }
        val subGroups = group.getSubgroups()!!
        while (subGroups.hasNext()) {
            addContacts(subGroups.next()!!)
        }
    }

    override fun contactPresenceStatusChanged(evt: ContactPresenceStatusChangeEvent) {
        uiHandler.post { callHistoryAdapter!!.notifyDataSetChanged() }
    }
    override fun onTaskComplete(result: Int, deletedUUIDs: List<String>?) {
        aTalkApp.showToastMessage(R.string.service_gui_CALL_HISTORY_REMOVE_COUNT, result)
        if (result > 0) {
            callHistoryAdapter!!.GetCallRecords(Date()).execute()
        }
    }

    override fun onClick(view: View) {
        var viewHolder: CallRecordViewHolder? = null
        var objTag = view.tag
        if (objTag is CallRecordViewHolder) {
            viewHolder = view.tag as CallRecordViewHolder
            // int childPos = viewHolder.childPosition;
            objTag = viewHolder.metaContact
        }
        if (objTag is MetaContact) {
            val metaContact = objTag
            val contact = metaContact.getDefaultContact()
            if (contact != null) {
                val jid = contact.contactJid!!
                when (view.id) {
                    R.id.callButton -> {
                        if (jid is DomainBareJid) {
                            val extPhone = TelephonyFragment.newInstance(contact.address)
                            (mContext as FragmentActivity?)!!.supportFragmentManager.beginTransaction()
                                    .replace(android.R.id.content, extPhone).commit()
                        }
                        else if (viewHolder != null) {
                            val isVideoCall = viewHolder.callVideoButton!!.isPressed
                            AndroidCallUtil.createAndroidCall(aTalkApp.globalContext, jid,
                                    viewHolder.callVideoButton, isVideoCall)
                        }
                    }

                    R.id.callVideoButton -> if (viewHolder != null) {
                        val isVideoCall = viewHolder.callVideoButton!!.isPressed
                        AndroidCallUtil.createAndroidCall(aTalkApp.globalContext, jid,
                                viewHolder.callVideoButton, isVideoCall)
                    }

                    else -> {}
                }
            }
        } else {
            Timber.w("Clicked item is not a valid MetaContact")
        }
    }

    /**
     * ActionMode with multi-selection implementation for chatListView
     */
    private val mMultiChoiceListener = object : AbsListView.MultiChoiceModeListener {
        var cPos = 0
        var headerCount = 0
        var checkListSize = 0
        private var mYear = 0
        private var mMonth = 0
        private var mDay = 0
        private var mHour = 0
        private var mMinute = 0
        var checkedList = SparseBooleanArray()
        override fun onItemCheckedStateChanged(mode: ActionMode, position: Int, id: Long, checked: Boolean) {
            // Here you can do something when items are selected/de-selected
            checkedList = callListView.checkedItemPositions
            checkListSize = checkedList.size()
            val checkedItemCount = callListView.checkedItemCount

            // Position must be aligned to the number of header views included
            cPos = position - headerCount
            mode.invalidate()
            callListView.setSelection(position)
            mode.title = checkedItemCount.toString()
        }

        // Called when the user selects a menu item. On action picked, close the CAB i.e. mode.finish();
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            var cType: Int
            var callRecord: CallRecord?
            return when (item.itemId) {
                R.id.cr_delete_older -> {
                    if (checkedList.size() > 0 && checkedList.valueAt(0)) {
                        cPos = checkedList.keyAt(0) - headerCount
                        cType = callHistoryAdapter!!.getItemViewType(cPos)
                        if (cType == callHistoryAdapter!!.CALL_RECORD) {
                            callRecord = callHistoryAdapter!!.getItem(cPos) as CallRecord?
                            if (callRecord != null) {
                                val sDate = callRecord.startTime
                                val c = Calendar.getInstance()
                                c.time = sDate
                                mYear = c[Calendar.YEAR]
                                mMonth = c[Calendar.MONTH]
                                mDay = c[Calendar.DAY_OF_MONTH]
                                mHour = c[Calendar.HOUR_OF_DAY]
                                mMinute = c[Calendar.MINUTE]

                                // Show DateTime picker for user to change the endDate
                                val datePickerDialog = DatePickerDialog(mContext!!,
                                        { view: DatePicker?, year: Int, monthOfYear: Int, dayOfMonth: Int ->
                                            mYear = year
                                            mMonth = monthOfYear
                                            mDay = dayOfMonth

                                            // Launch Time Picker Dialog
                                            val timePickerDialog = TimePickerDialog(mContext,
                                                    { view1: TimePicker?, hourOfDay: Int, minute: Int ->
                                                        c[mYear, mMonth, mDay, hourOfDay] = minute
                                                        EntityListHelper.eraseEntityCallHistory(this@CallHistoryFragment, c.time)
                                                        mode.finish()
                                                    }, mHour, mMinute, false)
                                            timePickerDialog.show()
                                        }, mYear, mMonth, mDay)
                                datePickerDialog.show()
                            }
                        }
                    }
                    true
                }
                R.id.cr_select_all -> {
                    val size = callHistoryAdapter!!.getCount()
                    if (size < 2) return true
                    var i = 0
                    while (i < size) {
                        cPos = i + headerCount
                        checkedList.put(cPos, true)
                        callListView.setSelection(cPos)
                        i++
                    }
                    checkListSize = size
                    mode.invalidate()
                    mode.title = size.toString()
                    true
                }
                R.id.cr_delete -> {
                    if (checkedList.size() == 0) {
                        aTalkApp.showToastMessage(R.string.service_gui_CALL_HISTORY_REMOVE_NONE)
                        return true
                    }
                    val callUuidDel = ArrayList<String>()
                    var i = 0
                    while (i < checkListSize) {
                        if (checkedList.valueAt(i)) {
                            cPos = checkedList.keyAt(i) - headerCount
                            cType = callHistoryAdapter!!.getItemViewType(cPos)
                            if (cType == callHistoryAdapter!!.CALL_RECORD) {
                                callRecord = callHistoryAdapter!!.getItem(cPos) as CallRecord?
                                if (callRecord != null) {
                                    callUuidDel.add(callRecord.callUuid)
                                }
                            }
                        }
                        i++
                    }
                    EntityListHelper.eraseEntityCallHistory(this@CallHistoryFragment, callUuidDel)
                    mode.finish()
                    true
                }
                else -> false
            }
        }

        // Called when the action ActionMode is created; startActionMode() was called
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            // Inflate the menu for the CAB
            val inflater = mode.menuInflater
            inflater.inflate(R.menu.call_history_menu, menu)
            headerCount = callListView.headerViewsCount
            return true
        }

        // Called each time the action ActionMode is shown. Always called after onCreateActionMode,
        // but may be called multiple times if the ActionMode is invalidated.
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            // Here you can perform updates to the CAB due to an invalidate() request
            // Return false if nothing is done
            return false
        }

        // Called when the user exits the action ActionMode
        override fun onDestroyActionMode(mode: ActionMode) {
            // Here you can make any necessary updates to the activity when
            // the CAB is removed. By default, selected items are deselected/unchecked.
            callHistoryAdapter!!.GetCallRecords(Date()).execute()
        }
    }

    private class CallRecordViewHolder {
        var avatar: ImageView? = null
        var callType: ImageView? = null
        var callButton: ImageView? = null
        var callVideoButton: ImageView? = null
        var contactId: TextView? = null
        var callInfo: TextView? = null
        var metaContact: MetaContact? = null
        var childPosition = 0
    }
}