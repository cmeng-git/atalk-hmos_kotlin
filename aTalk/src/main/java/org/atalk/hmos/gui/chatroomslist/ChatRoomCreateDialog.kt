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

import android.app.Dialog
import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Spinner
import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.impl.muc.MUCServiceImpl
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.muc.MUCService
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.chat.ChatSessionManager
import org.atalk.hmos.gui.menu.MainMenuActivity
import org.atalk.hmos.gui.util.ComboBox
import org.atalk.hmos.gui.util.ViewUtil
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.util.StringUtils
import org.jivesoftware.smackx.bookmarks.BookmarkManager
import org.jxmpp.util.XmppStringUtils
import timber.log.Timber
import java.beans.PropertyChangeEvent

/**
 * The invite dialog is the one shown when the user clicks on the conference option in the Contact List toolbar.
 *
 * @author Eng Chong Meng
 */
class ChatRoomCreateDialog(mContext: Context) : Dialog(mContext), AdapterView.OnItemSelectedListener, AdapterView.OnItemClickListener {
    private val mParent: MainMenuActivity
    private val mucService: MUCServiceImpl

    /**
     * The account list view.
     */
    private lateinit var accountsSpinner: Spinner
    private lateinit var chatRoomComboBox: ComboBox
    private lateinit var subjectField: EditText
    private lateinit var nicknameField: EditText
    private lateinit var passwordField: EditText
    private lateinit var mSavePasswordCheckBox: CheckBox
    private lateinit var mJoinButton: Button

    /**
     * A map of <JID></JID>, ChatRoomProviderWrapper>
     */
    private val mucRCProviderList = LinkedHashMap<String, ChatRoomProviderWrapper>()
    private var chatRoomList: MutableList<String> = ArrayList()
    private val chatRoomWrapperList = LinkedHashMap<String?, ChatRoomWrapper>()

    /**
     * Constructs the `ChatInviteDialog`.
     *
     * mContext the `ChatPanel` corresponding to the `ChatRoom`, where the contact is invited.
     */
    init {
        mParent = mContext as MainMenuActivity
        mucService = MUCActivator.mucService
    }

    public override fun onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.service_gui_CHATROOM_CREATE_JOIN)
        this.setContentView(R.layout.muc_room_create_dialog)
        accountsSpinner = findViewById(R.id.jid_Accounts_Spinner)
        initAccountSpinner()
        nicknameField = findViewById(R.id.NickName_Edit)
        subjectField = findViewById(R.id.chatRoom_Subject_Edit)
        subjectField.setText("")
        passwordField = findViewById(R.id.passwordField)
        val showPasswordCB = findViewById<CheckBox>(R.id.show_password)
        showPasswordCB.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean -> ViewUtil.showPassword(passwordField, isChecked) }
        mSavePasswordCheckBox = findViewById(R.id.store_password)
        chatRoomComboBox = findViewById(R.id.chatRoom_Combo)
        chatRoomComboBox.setOnItemClickListener(this)
        initComboBox().execute()
        mJoinButton = findViewById(R.id.button_Join)
        mJoinButton.setOnClickListener { v: View? -> if (createOrJoinChatRoom()) closeDialog() }
        val mCancelButton = findViewById<Button>(R.id.button_Cancel)
        mCancelButton.setOnClickListener { v: View? -> closeDialog() }
        setCanceledOnTouchOutside(false)
    }

    // add items into accountsSpinner dynamically
    private fun initAccountSpinner() {
        var mAccount: String
        val ppsList = ArrayList<String>()
        val providers = mucService.chatRoomProviders
        for (provider in providers) {
            mAccount = provider.protocolProvider.accountID.displayName!!
            mucRCProviderList[mAccount] = provider
            ppsList.add(mAccount)
        }

        // Create an ArrayAdapter using the string array and aTalk default spinner layout
        val mAdapter = ArrayAdapter(mParent, R.layout.simple_spinner_item, ppsList)
        // Specify the layout to use when the list of choices appears
        mAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        // Apply the adapter to the spinner
        accountsSpinner.adapter = mAdapter
        accountsSpinner.onItemSelectedListener = this
    }

    /**
     * Creates the providers comboBox and filling its content with the current available chatRooms.
     * Add all available server's chatRooms to the chatRoomList when providers changed.
     */
    private inner class initComboBox : AsyncTask<Void?, Void?, List<String>>() {
        override fun doInBackground(vararg params: Void?): List<String> {
            chatRoomList.clear()
            chatRoomWrapperList.clear()
            val crpWrapper = getSelectedProvider()
            if (crpWrapper != null) {
                val pps = crpWrapper.protocolProvider

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
                    chatRoomWrapperList[room] = mucService.findChatRoomWrapperFromChatRoomID(room, pps)!!
                }
            }
            return chatRoomList
        }

        override fun onPostExecute(result: List<String>) {
            super.onPostExecute(result)
            if (chatRoomList.size == 0) chatRoomList.add(CHATROOM)
            chatRoomComboBox.text = chatRoomList[0]
            // Must do this after setText as it clear the list; otherwise only one item in the list
            chatRoomComboBox.setSuggestionSource(chatRoomList)

            // Update the dialog form fields with all the relevant values, for first chatRoomWrapperList entry if available.
            if (!chatRoomWrapperList.isEmpty()) onItemClick(null, chatRoomComboBox, 0, 0)
        }
    }

    private fun closeDialog() {
        cancel()
    }

    /**
     * Updates the enable/disable state of the OK button.
     */
    private fun updateJoinButtonEnableState() {
        val nickName = ViewUtil.toString(nicknameField)
        val chatRoomField = chatRoomComboBox.text
        val mEnable = chatRoomField != null && nickName != null && getSelectedProvider() != null
        if (mEnable) {
            mJoinButton.isEnabled = true
            mJoinButton.alpha = 1.0f
        } else {
            mJoinButton.isEnabled = false
            mJoinButton.alpha = 0.5f
        }
    }

    override fun onItemSelected(adapter: AdapterView<*>?, view: View, pos: Int, id: Long) {
        initComboBox().execute()
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        // Another interface callback
    }

    /**
     * Callback method to be invoked when an item in this AdapterView i.e. comboBox has been clicked.
     *
     * @param parent The AdapterView where the click happened.
     * @param view The view within the AdapterView that was clicked (this will be a view provided by the adapter)
     * @param position The position of the view in the adapter.
     * @param id The row id of the item that was clicked.
     */
    override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        val chatRoomWrapper = chatRoomWrapperList[chatRoomList[position]]
        if (chatRoomWrapper != null) {
            // Timber.d("ComboBox Item clicked: %s; %s", position, chatRoomWrapper.getChatRoomName());
            val pwd = chatRoomWrapper.loadPassword()
            passwordField.setText(pwd)
            mSavePasswordCheckBox.isChecked = !TextUtils.isEmpty(pwd)
            val chatroom = chatRoomWrapper.chatRoom
            if (chatroom != null) {
                subjectField.setText(chatroom.getSubject())
            }
        }
        // chatRoomWrapper can be null, so always setDefaultNickname()
        setDefaultNickname()
    }

    /**
     * Sets the default value in the nickname field based on selected chatRoomWrapper stored value of PPS
     */
    private fun setDefaultNickname() {
        var chatRoom = chatRoomComboBox.text
        if (chatRoom != null) {
            chatRoom = chatRoom.replace("\\s".toRegex(), "")
        }
        val chatRoomWrapper = chatRoomWrapperList[chatRoom]
        var nickName: String? = null
        if (chatRoomWrapper != null) {
            nickName = chatRoomWrapper.nickName
        }
        if (TextUtils.isEmpty(nickName) && getSelectedProvider() != null) {
            val pps = getSelectedProvider()!!.protocolProvider
            if (pps != null) {
                nickName = AndroidGUIActivator.globalDisplayDetailsService!!.getDisplayName(pps)
                if (nickName == null || nickName.contains("@")) nickName = XmppStringUtils.parseLocalpart(pps.accountID.accountJid)
            }
        }
        nicknameField.setText(nickName)
        updateJoinButtonEnableState()
    }

    /**
     * Sets the (chat room) subject to be displayed in this `ChatRoomSubjectPanel`.
     *
     * @param subject the (chat room) subject to be displayed in this `ChatRoomSubjectPanel`
     */
    fun setSubject(subject: String?) {
        subjectField.setText(subject)
    }

    /**
     * Returns the selected provider in the providers combo box.
     *
     * @return the selected provider
     */
    private fun getSelectedProvider(): ChatRoomProviderWrapper? {
        val key = accountsSpinner.selectedItem as String
        return mucRCProviderList[key]
    }

    /**
     * Sets the value of chat room name field.
     *
     * @param chatRoom the chat room name.
     */
    fun setChatRoomField(chatRoom: String?) {
        chatRoomComboBox.text = chatRoom
        updateJoinButtonEnableState()
    }

    /**
     * Invites the contacts to the chat conference.
     */
    private fun createOrJoinChatRoom(): Boolean {
        // allow nickName to contain spaces
        val nickName = ViewUtil.toString(nicknameField)
        val password = ViewUtil.toString(passwordField)
        val subject = ViewUtil.toString(subjectField)
        var chatRoomID = chatRoomComboBox.text
        if (chatRoomID != null) {
            chatRoomID = chatRoomID.replace("\\s".toRegex(), "")
        }
        val savePassword = mSavePasswordCheckBox.isChecked
        val contacts = ArrayList<String>()
        val reason = "Let's chat"
        if (chatRoomID != null && nickName != null && subject != null && getSelectedProvider() != null) {
            val pps = getSelectedProvider()!!.protocolProvider

            // create new if chatRoom does not exist
            var chatRoomWrapper = mucService.findChatRoomWrapperFromChatRoomID(chatRoomID, pps)
            if (chatRoomWrapper == null) {
                // Just create chatRoomWrapper without joining as nick and password options are not available
                chatRoomWrapper = mucService.createChatRoom(chatRoomID, pps, contacts,
                        reason, false, false, true, chatRoomList.contains(chatRoomID))

                // Return without open the chat room, the protocol failed to create a chat room (null)
                if (chatRoomWrapper == null || chatRoomWrapper.chatRoom == null) {
                    aTalkApp.showToastMessage(R.string.service_gui_CHATROOM_CREATE_ERROR, chatRoomID)
                    return false
                }

                // retrieve and save the created chatRoom in database -> createChatRoom will save a copy in dB
                // chatRoomID = chatRoomWrapper.getChatRoomID();
                // ConfigurationUtils.saveChatRoom(pps, chatRoomID, chatRoomID);

                /*
                 * Save to server bookmark with autojoin option only for newly created chatRoom;
                 * Otherwise risk of overridden user previous settings
                 */
                chatRoomWrapper.isAutoJoin = true
                chatRoomWrapper.setBookmark(true)
                chatRoomWrapper.nickName = nickName // saved for later ResourcePart retrieval in addBookmarkedConference
                val entityBareJid = chatRoomWrapper.entityBareJid
                val bookmarkManager = BookmarkManager.getBookmarkManager(pps.connection)
                try {
                    // Use subject for bookmark name
                    bookmarkManager.addBookmarkedConference(subject, entityBareJid, true,
                            chatRoomWrapper.nickResource, password)
                } catch (e: SmackException.NoResponseException) {
                    Timber.w("Failed to add new Bookmarks: %s", e.message)
                } catch (e: SmackException.NotConnectedException) {
                    Timber.w("Failed to add new Bookmarks: %s", e.message)
                } catch (e: XMPPException.XMPPErrorException) {
                    Timber.w("Failed to add new Bookmarks: %s", e.message)
                } catch (e: InterruptedException) {
                    Timber.w("Failed to add new Bookmarks: %s", e.message)
                }

                // Allow removal of new chatRoom if join failed
                if (AndroidGUIActivator.configurationService
                                .getBoolean(MUCService.REMOVE_ROOM_ON_FIRST_JOIN_FAILED, false)) {
                    val crWrapper = chatRoomWrapper
                    chatRoomWrapper.addPropertyChangeListener { evt: PropertyChangeEvent ->
                        if (evt.propertyName == ChatRoomWrapper.JOIN_SUCCESS_PROP) return@addPropertyChangeListener

                        // if we failed for some , then close and remove the room
                        AndroidGUIActivator.uIService!!.closeChatRoomWindow(crWrapper)
                        MUCActivator.mucService.removeChatRoom(crWrapper)
                    }
                }
            }
            // Set chatRoom openAutomatically on_activity
            // MUCService.setChatRoomAutoOpenOption(pps, chatRoomID, MUCService.OPEN_ON_ACTIVITY);
            chatRoomWrapper.nickName = nickName
            if (savePassword) chatRoomWrapper.savePassword(password) else chatRoomWrapper.savePassword(null)
            val pwdByte = if (StringUtils.isEmpty(password)) null else password!!.toByteArray()
            mucService.joinChatRoom(chatRoomWrapper, nickName, pwdByte, subject)
            val chatIntent = ChatSessionManager.getChatIntent(chatRoomWrapper)
            mParent.startActivity(chatIntent)
            return true
        } else if (TextUtils.isEmpty(chatRoomID)) {
            aTalkApp.showToastMessage(R.string.service_gui_CHATROOM_JOIN_NAME)
        } else if (nickName == null) {
            aTalkApp.showToastMessage(R.string.service_gui_CHANGE_NICKNAME_NULL)
        } else if (subject == null) {
            aTalkApp.showToastMessage(R.string.service_gui_CHATROOM_JOIN_SUBJECT_NULL)
        } else {
            aTalkApp.showToastMessage(R.string.service_gui_CHATROOM_JOIN_FAILED, nickName, chatRoomID)
        }
        return false
    }

    companion object {
        private const val CHATROOM = "chatroom"
    }
}