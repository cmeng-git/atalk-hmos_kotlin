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
package org.atalk.hmos.gui.chat.chatsession

import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.util.SparseBooleanArray
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView.MultiChoiceModeListener
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import net.java.sip.communicator.impl.msghistory.MessageHistoryActivator
import net.java.sip.communicator.impl.msghistory.MessageHistoryServiceImpl
import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.impl.muc.MUCServiceImpl
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import net.java.sip.communicator.service.muc.ChatRoomListChangeEvent
import net.java.sip.communicator.service.muc.ChatRoomListChangeListener
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.muc.MUCService
import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony
import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.OperationSetVideoTelephony
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener
import net.java.sip.communicator.util.account.AccountUtils.registeredProviders
import org.apache.commons.lang3.StringUtils
import org.atalk.crypto.CryptoFragment
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.call.AndroidCallUtil.createAndroidCall
import org.atalk.hmos.gui.call.telephony.TelephonyFragment
import org.atalk.hmos.gui.chat.ChatFragment
import org.atalk.hmos.gui.chat.ChatSession
import org.atalk.hmos.gui.chat.ChatSessionManager.getChatIntent
import org.atalk.hmos.gui.util.AndroidImageUtil.bitmapFromBytes
import org.atalk.hmos.gui.util.EntityListHelper
import org.atalk.hmos.gui.widgets.UnreadCountCustomView
import org.atalk.service.osgi.OSGiActivity
import org.atalk.service.osgi.OSGiFragment
import org.jivesoftware.smackx.avatar.AvatarManager
import org.jxmpp.jid.DomainBareJid
import org.jxmpp.jid.DomainJid
import org.jxmpp.util.XmppStringUtils
import timber.log.Timber
import java.beans.PropertyChangeListener
import java.util.*

/**
 * The user interface that allows user to have direct access to the previous chat sessions.
 *
 * @author Eng Chong Meng
 */
open class ChatSessionFragment : OSGiFragment(), View.OnClickListener, ContactPresenceStatusListener, ChatRoomListChangeListener, EntityListHelper.TaskCompleted {
    /**
     * The list of chat session records
     */
    private val sessionRecords = ArrayList<ChatSessionRecord>()

    /**
     * The chat session list view representing the chat session.
     */
    private lateinit var chatSessionListView: ListView

    /**
     * A map of <Entity Jid, MetaContact>
    </Entity> */
    private val mMetaContacts: MutableMap<String?, MetaContact?> = LinkedHashMap()

    /**
     * A map of <Entity Jid, ChatRoomWrapper>
    </Entity> */
    private val chatRoomWrapperList: MutableMap<String, ChatRoomWrapper?> = LinkedHashMap()

    /**
     * A map of <Account Jid, ChatRoomProviderWrapper>
    </Account> */
    private val mucRCProviderList: MutableMap<String?, ChatRoomProviderWrapper?> = LinkedHashMap()
    private var chatRoomList: MutableList<String> = ArrayList()
    private lateinit var mucService: MUCServiceImpl

    /**
     * UI thread handler used to call all operations that access data model. This guarantees that
     * it's accessed from the main thread.
     */
    private val uiHandler = OSGiActivity.uiHandler

    /**
     * View for room configuration title description from the room configuration form
     */
    private var mTitle: TextView? = null
    private var mMHS: MessageHistoryServiceImpl? = null

    /**
     * {@inheritDoc}
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
        mMHS = MessageHistoryActivator.messageHistoryService
        mucService = MUCActivator.mucService
        mucService.addChatRoomListChangeListener(this)
    }

    /**
     * {@inheritDoc}
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val contentView = inflater.inflate(R.layout.chat_session, container, false)
        mTitle = contentView.findViewById(R.id.chat_session)
        chatSessionListView = contentView.findViewById(R.id.chat_sessionListView)
        chatSessionAdapter = ChatSessionAdapter(inflater)
        chatSessionListView.adapter = chatSessionAdapter
        chatSessionListView.onItemClickListener = listItemClickListener

        // Using the contextual action mode with multi-selection
        chatSessionListView.choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
        chatSessionListView.setMultiChoiceModeListener(mMultiChoiceListener)
        return contentView
    }

    /**
     * Adapter displaying all the available chat session for user selection.
     */
    private inner class ChatSessionAdapter(var mInflater: LayoutInflater) : BaseAdapter() {
        var CHAT_SESSION_RECORD = 1

        init {
            InitChatRoomWrapper().execute()
            getChatSessionRecords(Date()).execute()
        }

        override fun getCount(): Int {
            return sessionRecords.size
        }

        override fun getItem(position: Int): Any {
            return sessionRecords[position]
        }

        /**
         * Remove the sessionRecord by its sessionUuid
         *
         * @param sessionUuid session Uuid
         */
        fun removeItem(sessionUuid: String) {
            var index = 0
            for (cdRecord in sessionRecords) {
                if (cdRecord.sessionUuid == sessionUuid) break
                index++
            }

            // ConcurrentModificationException if perform within the loop
            if (index < sessionRecords.size) removeItem(index)
        }

        /**
         * Remove item in sessionRecords by the given index
         * Note: caller must adjust the index if perform remove in loop
         *
         * @param index of the sessionRecord to be deleted
         */
        fun removeItem(index: Int) {
            sessionRecords.removeAt(index)
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getItemViewType(position: Int): Int {
            return CHAT_SESSION_RECORD
        }

        override fun getViewTypeCount(): Int {
            return 1
        }

        override fun isEmpty(): Boolean {
            return count == 0
        }

        override fun getView(position: Int, convertView_: View?, parent: ViewGroup): View {
            var convertView = convertView_
            val chatRecordViewHolder: ChatRecordViewHolder
            val chatSessionRecord = sessionRecords[position]
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.chat_session_row, parent, false)
                chatRecordViewHolder = ChatRecordViewHolder()
                chatRecordViewHolder.avatar = convertView.findViewById(R.id.avatar)
                chatRecordViewHolder.entityJId = convertView.findViewById(R.id.entityJid)
                chatRecordViewHolder.chatType = convertView.findViewById(R.id.chatType)
                chatRecordViewHolder.chatMessage = convertView.findViewById(R.id.chatMessage)
                chatRecordViewHolder.unreadCount = convertView.findViewById(R.id.unread_count)
                chatRecordViewHolder.unreadCount.tag = chatRecordViewHolder
                chatRecordViewHolder.callButton = convertView.findViewById(R.id.callButton)
                chatRecordViewHolder.callButton.setOnClickListener(this@ChatSessionFragment)
                chatRecordViewHolder.callButton.tag = chatRecordViewHolder
                chatRecordViewHolder.callVideoButton = convertView.findViewById(R.id.callVideoButton)
                chatRecordViewHolder.callVideoButton.setOnClickListener(this@ChatSessionFragment)
                chatRecordViewHolder.callVideoButton.tag = chatRecordViewHolder
                convertView.tag = chatRecordViewHolder
            }
            else {
                chatRecordViewHolder = convertView.tag as ChatRecordViewHolder
            }
            chatRecordViewHolder.childPosition = position
            chatRecordViewHolder.sessionUuid = chatSessionRecord.sessionUuid

            // Must init child Tag here as reused convertView may not necessary contains the correct reference
            val chatSessionView = convertView!!.findViewById<View>(R.id.chatSessionView)
            chatSessionView.tag = chatRecordViewHolder

            // setOnClickListener interfere with setMultiChoiceModeListener; use AdapterView.OnItemClickListener()
            // chatSessionView.setOnClickListener(ChatSessionFragment.this);
            crViewHolderMap[chatSessionRecord.entityId] = chatRecordViewHolder
            var unreadCount = 0
            var metaContact: MetaContact? = null
            val entityId = chatSessionRecord.entityId
            if (chatSessionRecord.chatMode == ChatSession.MODE_SINGLE) {
                val bareJid = chatSessionRecord.entityBareJid
                val avatar = AvatarManager.getAvatarImageByJid(bareJid)
                if (avatar != null) {
                    chatRecordViewHolder.avatar!!.setImageBitmap(bitmapFromBytes(avatar))
                }
                else {
                    chatRecordViewHolder.avatar!!.setImageResource(R.drawable.person_photo)
                }
                metaContact = mMetaContacts[entityId]
                if (metaContact != null) unreadCount = metaContact.getUnreadCount()
            }
            else {
                chatRecordViewHolder.avatar!!.setImageResource(R.drawable.ic_chatroom)
                val crpWrapper = chatRoomWrapperList[entityId]
                if (crpWrapper != null) unreadCount = crpWrapper.unreadCount
            }
            updateUnreadCount(entityId, unreadCount)
            chatRecordViewHolder.callButton.visibility = if (isShowCallBtn(metaContact)) View.VISIBLE else View.GONE
            chatRecordViewHolder.callVideoButton.visibility = if (isShowVideoCallBtn(metaContact)) View.VISIBLE else View.GONE
            setChatType(chatRecordViewHolder.chatType, chatSessionRecord.chatType)
            chatRecordViewHolder.entityJId!!.text = chatSessionRecord.entityId
            return convertView
        }

        /**
         * Retrieve all the chat sessions saved locally in the database
         * Populate the fragment with the chat session for each getView()
         */
        inner class getChatSessionRecords(val mEndDate: Date) : AsyncTask<Void?, Void?, Void?>() {
            init {
                sessionRecords.clear()
                mMetaContacts.clear()
                chatSessionListView.clearChoices()
            }

            protected override fun doInBackground(vararg params: Void?): Void? {
                initMetaContactList()
                var csRecordPPS: Collection<ChatSessionRecord>
                val providers = registeredProviders
                for (pps in providers) {
                    if (pps.connection != null && pps.connection!!.isAuthenticated) {
                        addContactStatusListener(pps)
                        val userUid = pps.accountID.accountUniqueID
                        csRecordPPS = mMHS!!.findSessionByEndDate(userUid!!, mEndDate)
                        if (csRecordPPS.isNotEmpty()) sessionRecords.addAll(csRecordPPS)
                    }
                }
                return null
            }

            override fun onPostExecute(result: Void?) {
                if (sessionRecords.size > 0) {
                    chatSessionAdapter!!.notifyDataSetChanged()
                }
                setTitle()
            }
        }
    }

    /**
     * Updates the entity unread message count and the last message.
     * Hide widget if (count == 0)
     *
     * @param entityJid the entity Jid of MetaContact or ChatRoom ID
     * @param count the message unread count
     */
    fun updateUnreadCount(entityJid: String, count: Int) {
        if (StringUtils.isNotEmpty(entityJid) && chatSessionAdapter != null) {
            val chatRecordViewHolder = crViewHolderMap[entityJid] ?: return
            runOnUiThread {
                if (count == 0) {
                    chatRecordViewHolder.unreadCount.visibility = View.GONE
                }
                else {
                    chatRecordViewHolder.unreadCount.visibility = View.VISIBLE
                    chatRecordViewHolder.unreadCount.setUnreadCount(count)
                }
                val msgBody = mMHS!!.getLastMessageForSessionUuid(chatRecordViewHolder.sessionUuid)
                chatRecordViewHolder.chatMessage!!.text = msgBody
            }
        }
    }

    /**
     * Adds the given `addContactPresenceStatusListener` to listen for contact presence status change.
     *
     * @param pps the `ProtocolProviderService` for which we add the listener
     */
    private fun addContactStatusListener(pps: ProtocolProviderService) {
        val presenceOpSet = pps.getOperationSet(OperationSetPresence::class.java)
        if (presenceOpSet != null) {
            presenceOpSet.removeContactPresenceStatusListener(this)
            presenceOpSet.addContactPresenceStatusListener(this)
        }
    }

    /**
     * Sets the chat type.
     *
     * @param chatTypeView the chat type state image view
     * @param chatType the chat session Type.
     */
    private fun setChatType(chatTypeView: ImageView?, chatType: Int) {
        val iconId = when (chatType) {
            ChatFragment.MSGTYPE_OMEMO -> R.drawable.encryption_omemo
            ChatFragment.MSGTYPE_OTR, ChatFragment.MSGTYPE_OTR_UA -> R.drawable.encryption_otr
            ChatFragment.MSGTYPE_NORMAL, ChatFragment.MSGTYPE_MUC_NORMAL -> R.drawable.encryption_none
            else -> R.drawable.encryption_none
        }
        chatTypeView!!.setImageResource(iconId)
    }

    private fun setTitle() {
        val title = (aTalkApp.getResString(R.string.service_gui_RECENT_MESSAGES)
                + " (" + sessionRecords.size + ")")
        mTitle!!.text = title
    }

    // Handle only if contactImpl instanceof MetaContact;
    private fun isShowCallBtn(contactImpl: Any?): Boolean {
        return (contactImpl is MetaContact
                && isShowButton(contactImpl as MetaContact?, OperationSetBasicTelephony::class.java))
    }

    private fun isShowVideoCallBtn(contactImpl: Any?): Boolean {
        return (contactImpl is MetaContact
                && isShowButton(contactImpl as MetaContact?, OperationSetVideoTelephony::class.java))
    }

    private fun isShowButton(metaContact: MetaContact?, opSetClass: Class<out OperationSet?>): Boolean {
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
    private fun addContacts(group: MetaContactGroup?) {
        if (group!!.countChildContacts() > 0) {

            // Use Iterator to avoid ConcurrentModificationException on addContact()
            val childContacts = group.getChildContacts()
            while (childContacts!!.hasNext()) {
                val metaContact = childContacts.next()
                val contactId = metaContact!!.getDefaultContact()!!.address
                mMetaContacts[contactId] = metaContact
            }
        }
        val subGroups = group.getSubgroups()
        while (subGroups!!.hasNext()) {
            addContacts(subGroups.next())
        }
    }

    override fun contactPresenceStatusChanged(evt: ContactPresenceStatusChangeEvent) {
        uiHandler.post { chatSessionAdapter!!.notifyDataSetChanged() }
    }

    /**
     * Indicates that a change has occurred in the chatRoom List.
     */
    override fun contentChanged(evt: ChatRoomListChangeEvent) {
        uiHandler.post { chatSessionAdapter!!.notifyDataSetChanged() }
    }

    override fun onTaskComplete(result: Int, deletedUUIDs: List<String>?) {
        if (result > 0) {
            chatSessionAdapter!!.getChatSessionRecords(Date()).execute()
        }
    }

    /**
     * Creates the providers comboBox and filling its content with the current available chatRooms.
     * Add all available server's chatRooms to the chatRoomList when providers changed.
     */
    private inner class InitChatRoomWrapper : AsyncTask<Void?, Void?, Void?>() {
        override fun doInBackground(vararg params: Void?): Void? {
            chatRoomList.clear()
            chatRoomWrapperList.clear()
            val providers = mucService.chatRoomProviders
            for (crpWrapper in providers) {
                val pps = crpWrapper.protocolProvider
                val mAccount = pps.accountID.accountJid
                mucRCProviderList[mAccount] = crpWrapper

                // local chatRooms
                chatRoomList = mucService.getExistingChatRooms(pps)

                // server chatRooms
                val sChatRoomList = mucService.getExistingChatRooms(crpWrapper)!!
                for (sRoom in sChatRoomList) {
                    if (!chatRoomList.contains(sRoom)) {
                        chatRoomList.add(sRoom)
                    }
                }

                // populate the chatRoomWrapperList for all the chatRooms
                for (room in chatRoomList) {
                    chatRoomWrapperList[room] = mucService.findChatRoomWrapperFromChatRoomID(room, pps)
                }
            }
            return null
        }
    }

    /**
     * Use OnItemClickListener to startChat; otherwise onClickListener interfere with MultiChoiceModeListener
     */
    private val listItemClickListener = OnItemClickListener { parent: AdapterView<*>?, view: View, position: Int, id: Long -> onClick(view.findViewById(R.id.chatSessionView)) }
    override fun onClick(view: View) {
        val viewHolder: ChatRecordViewHolder
        val chatSessionRecord: ChatSessionRecord
        val accountId: String
        val entityJid: String
        val `object` = view.tag
        if (`object` is ChatRecordViewHolder) {
            viewHolder = `object`
            val childPos = viewHolder.childPosition
            chatSessionRecord = sessionRecords[childPos]
            if (chatSessionRecord == null)
                return
            accountId = chatSessionRecord.accountUserId
            entityJid = chatSessionRecord.entityId
        }
        else {
            Timber.w("Clicked item is not a valid MetaContact or chatRoom")
            return
        }
        if (chatSessionRecord.chatMode == ChatSession.MODE_SINGLE) {
            val metaContact = mMetaContacts[entityJid]
            if (metaContact == null) {
                aTalkApp.showToastMessage(R.string.service_gui_CONTACT_INVALID, entityJid)
                return
            }
            val contact = metaContact.getDefaultContact()
            if (contact != null) {
                val jid = chatSessionRecord.entityBareJid
                when (view.id) {
                    R.id.chatSessionView -> startChat(metaContact)
                    R.id.callButton -> {
                        if (jid is DomainBareJid) {
                            val extPhone = TelephonyFragment.newInstance(contact.address)
                            (mContext as FragmentActivity).supportFragmentManager.beginTransaction()
                                    .replace(android.R.id.content, extPhone).commit()
                        }
                        else {
                            val isVideoCall = viewHolder.callVideoButton.isPressed
                            createAndroidCall(aTalkApp.globalContext, jid, viewHolder.callVideoButton, isVideoCall)
                        }
                    }
                    R.id.callVideoButton -> {
                        val isVideoCall = viewHolder.callVideoButton.isPressed
                        createAndroidCall(aTalkApp.globalContext, jid, viewHolder.callVideoButton, isVideoCall)
                    }
                    else -> {}
                }
            }
        }
        else {
            createOrJoinChatRoom(accountId, entityJid)
        }
    }

    /**
     * cmeng: when metaContact is owned by two different user accounts, the first launched chatSession
     * will take predominant over subsequent metaContact chat session launches by another account
     */
    private fun startChat(metaContact: MetaContact) {
        if (metaContact.getDefaultContact() == null) {
            aTalkApp.showToastMessage(R.string.service_gui_CONTACT_INVALID, metaContact.getDisplayName())
            return
        }

        // Default for domainJid - always show chat session
        if (metaContact.getDefaultContact()!!.contactJid is DomainJid) {
            startChatActivity(metaContact)
            return
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
        val chatIntent = getChatIntent(descriptor)
        try {
            startActivity(chatIntent!!)
        } catch (ex: Exception) {
            Timber.w("Failed to start chat with %s: %s", descriptor, ex.message)
        }
    }

    /**
     * Invites the contacts to the chat conference.
     */
    private fun createOrJoinChatRoom(userId: String, chatRoomID: String) {
        val contacts = ArrayList<String?>()
        val reason = "Let's chat"
        var nickName = XmppStringUtils.parseLocalpart(userId)
        var password: String? = null

        // create new if chatRoom does not exist
        val pps = mucRCProviderList[userId]!!.protocolProvider
        var chatRoomWrapper = chatRoomWrapperList[chatRoomID]
        if (chatRoomWrapper != null) {
            nickName = chatRoomWrapper.nickName
            password = chatRoomWrapper.loadPassword()
        }
        else {
            // Just create chatRoomWrapper without joining as nick and password options are not available
            chatRoomWrapper = mucService.createChatRoom(chatRoomID, pps, contacts,
                    reason, false, false, true, chatRoomList.contains(chatRoomID))

            // Return without open the chat room, the protocol failed to create a chat room (null)
            if (chatRoomWrapper == null || chatRoomWrapper.chatRoom == null) {
                aTalkApp.showToastMessage(R.string.service_gui_CHATROOM_CREATE_ERROR, chatRoomID)
                return
            }

            // Allow removal of new chatRoom if join failed
            if (AndroidGUIActivator.configurationService.getBoolean(MUCService.REMOVE_ROOM_ON_FIRST_JOIN_FAILED, false)) {
                val crWrapper = chatRoomWrapper

                chatRoomWrapper.addPropertyChangeListener(PropertyChangeListener { evt ->
                    if (evt.propertyName == ChatRoomWrapper.JOIN_SUCCESS_PROP) return@PropertyChangeListener

                    // if we failed for some , then close and remove the room
                    AndroidGUIActivator.uIService.closeChatRoomWindow(crWrapper)
                    MUCActivator.mucService.removeChatRoom(crWrapper)
                })
            }
        }

        chatRoomWrapper.nickName = nickName
        val pwdByte = if (StringUtils.isEmpty(password)) null else password!!.toByteArray()
        mucService.joinChatRoom(chatRoomWrapper, nickName, pwdByte, null)
        val chatIntent = getChatIntent(chatRoomWrapper)
        mContext!!.startActivity(chatIntent)
    }

    /**
     * ActionMode with multi-selection implementation for chatListView
     */
    private val mMultiChoiceListener = object : MultiChoiceModeListener {
        var cPos = 0
        var headerCount = 0
        var checkListSize = 0
        var mDelete: MenuItem? = null
        var mSelectAll: MenuItem? = null
        var checkedList: SparseBooleanArray? = null

        override fun onItemCheckedStateChanged(mode: ActionMode, position: Int, id: Long, checked: Boolean) {
            // Here you can do something when items are selected/de-selected
            checkedList = chatSessionListView.checkedItemPositions
            checkListSize = checkedList!!.size()
            val checkedItemCount = chatSessionListView.checkedItemCount

            // Position must be aligned to the number of header views included
            cPos = position - headerCount
            mode.invalidate()
            chatSessionListView.setSelection(position)
            mode.title = checkedItemCount.toString()
        }

        // Called when the user selects a menu item. On action picked, close the CAB i.e. mode.finish();
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            var cType: Int
            var sessionRecord: ChatSessionRecord
            return when (item.itemId) {
                R.id.cr_select_all -> {
                    val size = chatSessionAdapter!!.count
                    if (size < 2) return true
                    var i = 0
                    while (i < size) {
                        cPos = i + headerCount
                        checkedList!!.put(cPos, true)
                        chatSessionListView.setSelection(cPos)
                        i++
                    }
                    checkListSize = size
                    mode.invalidate()
                    mode.title = size.toString()
                    true
                }
                R.id.cr_delete -> {
                    // List of records with sessionUuids in chatSessionAdapter to be deleted.
                    val sessionUuidDel = ArrayList<String>()
                    var i = 0
                    while (i < checkListSize) {
                        if (checkedList!!.valueAt(i)) {
                            cPos = checkedList!!.keyAt(i) - headerCount
                            cType = chatSessionAdapter!!.getItemViewType(cPos)
                            if (cType == chatSessionAdapter!!.CHAT_SESSION_RECORD) {
                                sessionRecord = chatSessionAdapter!!.getItem(cPos) as ChatSessionRecord
                                if (sessionRecord != null) {
                                    val sessionUuid = sessionRecord.sessionUuid
                                    sessionUuidDel.add(sessionUuid)

                                    /*
                                     * Hide the session record if it is still a valid session record
                                     * otherwise purge both the session record and its associated messages from DB
                                     */
                                    val entityJid = sessionRecord.entityId
                                    if (mMetaContacts.containsKey(entityJid)
                                            || chatRoomWrapperList.containsKey(entityJid)) {
                                        mMHS!!.setSessionChatType(sessionUuid, sessionRecord.chatType or SESSION_HIDDEN)
                                        Timber.d("Hide chatSession for entityJid: %s (%s)", entityJid, sessionUuid)
                                    }
                                    else {
                                        val msgCount = mMHS!!.getMessageCountForSessionUuid(sessionUuid)
                                        mMHS!!.purgeLocallyStoredHistory(listOf(sessionUuid), true)
                                        Timber.w("Purged (%s) messages for invalid entityJid: %s (%s)",
                                                msgCount, entityJid, sessionUuid)
                                    }
                                }
                            }
                        }
                        i++
                    }
                    if (sessionUuidDel.isNotEmpty()) {
                        // Must do this inorder for notifyDataSetChanged to have effect;
                        // Also outside the checkListSize loop so it does not affect the cPos for record fetch.
                        for (sessionUuid in sessionUuidDel) {
                            chatSessionAdapter!!.removeItem(sessionUuid)
                        }

                        // reset the value so CryptoFragment reload and re-init chatType when use open the chatSession again
                        CryptoFragment.resetEncryptionChoice(null)
                        chatSessionAdapter!!.notifyDataSetChanged()
                    }
                    mode.finish()
                    true
                }
                else -> false
            }
        }

        // Called when the action mActionMode is created; startActionMode() was called
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            // Inflate the menu for the CAB
            val inflater = mode.menuInflater
            inflater.inflate(R.menu.call_history_menu, menu)
            headerCount = chatSessionListView.headerViewsCount
            mDelete = menu.findItem(R.id.cr_delete)
            mSelectAll = menu.findItem(R.id.cr_select_all)
            return true
        }

        // Called each time the action mActionMode is shown. Always called after onCreateActionMode,
        // but may be called multiple times if the mActionMode is invalidated.
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            // Here you can perform updates to the CAB due to an invalidate() request
            // Return false if nothing is done.
            return false
        }

        // Called when the user exits the action mActionMode
        override fun onDestroyActionMode(mode: ActionMode) {
            // Here you can make any necessary updates to the activity when
            // the CAB is removed. By default, selected items are deselected/unchecked.
            val mActionMode: ActionMode? = null
        }
    }

    private class ChatRecordViewHolder {
        var avatar: ImageView? = null
        var chatType: ImageView? = null
        lateinit var callButton: ImageView
        lateinit var callVideoButton: ImageView
        var entityJId: TextView? = null
        var chatMessage: TextView? = null
        var childPosition = 0
        var sessionUuid: String? = null
        lateinit var unreadCount: UnreadCountCustomView
    }

    companion object {
        /**
         * bit-7 of the ChatSession#STATUS is to hide session from UI if set
         *
         * @see ChatFragment.MSGTYPE_MASK
         */
        var SESSION_HIDDEN = 0x80

        /**
         * The Chat session adapter for user selection
         */
        private var chatSessionAdapter: ChatSessionAdapter? = null

        /**
         * A map reference of entity to ChatRecordViewHolder for the unread message count update
         */
        private val crViewHolderMap = HashMap<String, ChatRecordViewHolder>()

        private var mContext: Context? = null
    }
}