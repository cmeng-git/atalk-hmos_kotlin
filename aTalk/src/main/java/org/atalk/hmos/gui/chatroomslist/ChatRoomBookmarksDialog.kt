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
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.util.ThemeHelper
import org.atalk.hmos.gui.util.ViewUtil
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.util.StringUtils
import org.jivesoftware.smackx.bookmarks.BookmarkManager
import org.jivesoftware.smackx.bookmarks.BookmarkedConference
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart
import org.jxmpp.stringprep.XmppStringprepException
import org.jxmpp.util.XmppStringUtils
import timber.log.Timber

/**
 * The chatRoom Bookmarks dialog is the one shown when the user clicks on the Bookmarks option in the main menu.
 *
 * @author Eng Chong Meng
 */
class ChatRoomBookmarksDialog(context: Context) : Dialog(context), AdapterView.OnItemSelectedListener, DialogActivity.DialogListener {
    private val mParent = context
    private val mucService = MUCActivator.mucService

    /**
     * The account list view.
     */
    private lateinit var accountsSpinner: Spinner
    private lateinit var chatRoomSpinner: Spinner
    private lateinit var mucNameField: EditText
    private lateinit var nicknameField: EditText
    private lateinit var mAutoJoin: CheckBox
    private lateinit var mBookmark: CheckBox
    private lateinit var mPasswordField: EditText
    private var hasChanges = false

    /**
     * current bookmark view in focus that the user see
     */
    private var mBookmarkFocus: BookmarkConference? = null

    /**
     * A map of <account Jid, List></account><BookmarkConference>>
    </BookmarkConference> */
    private val mAccountBookmarkConferencesList = LinkedHashMap<String, List<BookmarkConference>>()

    /**
     * A map of <RoomJid></RoomJid>, BookmarkConference> retrieved from mAccountBookmarkConferencesList
     */
    private val mBookmarkConferenceList = LinkedHashMap<String, BookmarkConference>()

    /**
     * A map of <JID></JID>, ChatRoomProviderWrapper>
     */
    private val mucRoomWrapperList = LinkedHashMap<String, ChatRoomProviderWrapper>()
    private var mChatRoomList = ArrayList<String>()

    public override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.setTheme(mParent)
        super.onCreate(savedInstanceState)
        setTitle(R.string.service_gui_CHATROOM_BOOKMARK_TITLE)
        this.setContentView(R.layout.chatroom_bookmarks)
        accountsSpinner = findViewById(R.id.jid_Accounts_Spinner)
        initAccountSpinner()
        mucNameField = findViewById(R.id.mucName_Edit)
        nicknameField = findViewById(R.id.nickName_Edit)
        mAutoJoin = findViewById(R.id.cb_autojoin)
        mBookmark = findViewById(R.id.cb_bookmark)
        mPasswordField = findViewById(R.id.passwordField)
        val mShowPasswordCheckBox = findViewById<CheckBox>(R.id.show_password)
        mShowPasswordCheckBox.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean -> ViewUtil.showPassword(mPasswordField, isChecked) }
        chatRoomSpinner = findViewById(R.id.chatRoom_Spinner)
        // chatRoomSpinner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        initBookmarkedConference().execute()
        val mApplyButton = findViewById<Button>(R.id.button_Apply)
        mApplyButton.setOnClickListener { v: View? -> if (updateBookmarkedConference()) closeDialog() }
        val mCancelButton = findViewById<Button>(R.id.button_Cancel)
        mCancelButton.setOnClickListener { v: View? ->
            if (hasChanges) {
                DialogActivity.showConfirmDialog(mParent,
                        R.string.service_gui_CHATROOM_BOOKMARK_TITLE,
                        R.string.service_gui_UNSAVED_CHANGES,
                        R.string.service_gui_EXIT, this)
            } else closeDialog()
        }
        setCancelable(false)
    }

    override fun onConfirmClicked(dialog: DialogActivity): Boolean {
        closeDialog()
        return true
    }

    override fun onDialogCancelled(dialog: DialogActivity) {}

    // add items into accountsSpinner dynamically
    private fun initAccountSpinner() {
        var mAccount: String
        val ppsList = ArrayList<String>()
        val providers = mucService.chatRoomProviders
        for (provider in providers) {
            mAccount = provider.protocolProvider.accountID.accountJid
            mucRoomWrapperList[mAccount] = provider
            ppsList.add(mAccount)
        }

        // Create an ArrayAdapter using the string array and aTalk default spinner layout
        val mAdapter = ArrayAdapter<Any?>(mParent, R.layout.simple_spinner_item, ppsList as List<Any?>)
        // Specify the layout to use when the list of choices appears
        mAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        // Apply the adapter to the spinner
        accountsSpinner.adapter = mAdapter
        accountsSpinner.onItemSelectedListener = this
    }

    /**
     * Creates the providers comboBox and filling its content with the current available chatRooms
     * Add available server chatRooms to the chatRoomList when providers changes
     */
    private inner class initBookmarkedConference : AsyncTask<Void?, Void?, Void?>() {
        var bookmarkedList: List<BookmarkedConference> = ArrayList()
        var bookmarkList = ArrayList<BookmarkConference>()
        var bookmarkConference: BookmarkConference? = null

        override fun doInBackground(vararg params: Void?): Void? {
            val crpWrappers = mucService.chatRoomProviders
            for (crpWrapper in crpWrappers) {
                if (crpWrapper != null) {
                    val pps = crpWrapper.protocolProvider
                    val bookmarkManager = BookmarkManager.getBookmarkManager(pps.connection)
                    val mAccount = pps.accountID.accountJid

                    // local chatRooms
                    val chatRoomList = mucService.getExistingChatRooms(pps)

                    // server chatRooms
                    val sChatRoomList = mucService.getExistingChatRooms(crpWrapper)
                    for (sRoom in sChatRoomList!!) {
                        if (!chatRoomList.contains(sRoom)) chatRoomList.add(sRoom)
                    }
                    try {
                        // Fetch all the bookmarks from server
                        bookmarkedList = bookmarkManager.bookmarkedConferences

                        // Remove bookmarked chat rooms from chatRoomList
                        for (bookmarkedConference in bookmarkedList) {
                            chatRoomList.remove(bookmarkedConference.jid.toString())
                            bookmarkConference = BookmarkConference(bookmarkedConference)
                            bookmarkConference!!.isBookmark = true
                            bookmarkList.add(bookmarkConference!!)
                        }
                    } catch (e: SmackException.NoResponseException) {
                        Timber.w("Failed to fetch Bookmarks: %s", e.message)
                    } catch (e: SmackException.NotConnectedException) {
                        Timber.w("Failed to fetch Bookmarks: %s", e.message)
                    } catch (e: XMPPException.XMPPErrorException) {
                        Timber.w("Failed to fetch Bookmarks: %s", e.message)
                    } catch (e: InterruptedException) {
                        Timber.w("Failed to fetch Bookmarks: %s", e.message)
                    }
                    if (chatRoomList.size > 0) {
                        val mNickName = getDefaultNickname(pps)
                        for (chatRoom in chatRoomList) {
                            val chatRoomWrapper = mucService.findChatRoomWrapperFromChatRoomID(chatRoom, pps)
                            val isAutoJoin = chatRoomWrapper != null && chatRoomWrapper.isAutoJoin
                            val nickName = if (chatRoomWrapper != null) chatRoomWrapper.nickName else mNickName
                            val name = if (chatRoomWrapper != null) chatRoomWrapper.bookmarkName else ""
                            try {
                                val entityBareJid = JidCreate.entityBareFrom(chatRoom)
                                bookmarkConference = BookmarkConference(name!!, entityBareJid, isAutoJoin,
                                        Resourcepart.from(nickName), "")
                                bookmarkConference!!.isBookmark= false
                                bookmarkList.add(bookmarkConference!!)
                            } catch (e: XmppStringprepException) {
                                Timber.w("Failed to add Bookmark for %s: %s", chatRoom, e.message)
                            }
                        }
                    }
                    mAccountBookmarkConferencesList[mAccount] = bookmarkList
                }
            }
            return null
        }

        override fun onPostExecute(result: Void?) {
            super.onPostExecute(result)
            if (mAccountBookmarkConferencesList.isNotEmpty()) {
                val keySet = mAccountBookmarkConferencesList.keys.toTypedArray()
                if (keySet.isNotEmpty()) {
                    val accountId = keySet[0] as String
                    if (StringUtils.isNotEmpty(accountId)) initChatRoomSpinner(accountId)
                }
            }
        }
    }

    /**
     * Creates the providers comboBox and filling its content with the current available chatRooms
     * Add available server chatRooms to the chatRoomList when providers changes
     */
    private fun initChatRoomSpinner(accountId: String) {
        val mBookmarkConferences = mAccountBookmarkConferencesList[accountId]
        if (mBookmarkConferences != null) {
            for (bookmarkConference in mBookmarkConferences) {
                val chatRoom = bookmarkConference.jid.toString()
                mChatRoomList.add(chatRoom)
                mBookmarkConferenceList[chatRoom] = bookmarkConference
            }
        }

        // Create an ArrayAdapter using the string array and aTalk default spinner layout
        val mAdapter = ArrayAdapter<Any?>(mParent, R.layout.simple_spinner_item, mChatRoomList as List<Any?>)
        mAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)

        // Apply the adapter to the spinner
        chatRoomSpinner.adapter = mAdapter
        chatRoomSpinner.onItemSelectedListener = this
        if (mChatRoomList.size > 0) {
            val chatRoom = mChatRoomList[0]
            initBookMarkForm(chatRoom)
        }
    }

    private fun closeDialog() {
        cancel()
    }

    override fun onItemSelected(adapter: AdapterView<*>, view: View, pos: Int, id: Long) {
        when (adapter.id) {
            R.id.jid_Accounts_Spinner -> {
                val userId = adapter.getItemAtPosition(pos) as String
                val protocol = mucRoomWrapperList[userId]
                val pps = protocol?.protocolProvider
                if (pps != null) {
                    mBookmarkFocus = null
                    val accountId = pps.accountID.accountJid
                    initChatRoomSpinner(accountId)
                }
            }

            R.id.chatRoom_Spinner -> {
                val oldChatRoom = if (mBookmarkFocus != null) mBookmarkFocus!!.jid.toString() else ""
                val chatRoom = adapter.getItemAtPosition(pos) as String
                if (!initBookMarkForm(chatRoom)) {
                    chatRoomSpinner.setSelection(mChatRoomList.indexOf(oldChatRoom))
                }
            }
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        // Another interface callback
    }

    /**
     * Sets the default value in the nickname field based on pps.
     *
     * @param pps the ProtocolProviderService
     */
    private fun getDefaultNickname(pps: ProtocolProviderService): String {
        var nickName = AndroidGUIActivator.globalDisplayDetailsService!!.getDisplayName(pps)
        if (nickName == null || nickName.contains("@")) nickName = XmppStringUtils.parseLocalpart(pps.accountID.accountJid)
        return nickName!!
    }

    /**
     * Sets the value of chat room name field.
     *
     * @param chatRoom the chat room name.
     */
    private fun initBookMarkForm(chatRoom: String): Boolean {
        if (!updateBookmarkFocus()) {
            return false
        }
        mBookmarkFocus = mBookmarkConferenceList[chatRoom]
        if (mBookmarkFocus != null) {
            mucNameField.setText(mBookmarkFocus!!.name)
            nicknameField.setText(mBookmarkFocus!!.nickname)
            mPasswordField.setText(mBookmarkFocus!!.password)
            mAutoJoin.isChecked = mBookmarkFocus!!.isAutoJoin
            mBookmark.isChecked = mBookmarkFocus!!.isBookmark
            return true
        }
        return false
    }

    private fun updateBookmarkFocus(): Boolean {
        if (mBookmarkFocus != null) {
            val nickName = mBookmarkFocus!!.nickname.toString()
            hasChanges = ((isEqual(mBookmarkFocus!!.name, ViewUtil.toString(mucNameField))
                    && isEqual(nickName, ViewUtil.toString(nicknameField))
                    && isEqual(mBookmarkFocus!!.password, ViewUtil.toString(mPasswordField)))
                    && mBookmarkFocus!!.isAutoJoin == mAutoJoin.isChecked && mBookmarkFocus!!.isBookmark) != mBookmark.isChecked

            // Timber.w("Fields have changes: %s", hasChanges);
            if (hasChanges) {
                mBookmarkFocus!!.name = ViewUtil.toString(mucNameField)
                mBookmarkFocus!!.password = ViewUtil.toString(mPasswordField)
                mBookmarkFocus!!.isAutoJoin = mAutoJoin.isChecked
                mBookmarkFocus!!.isBookmark = mBookmark.isChecked
                try {
                    // nickName cannot be null => exception
                    mBookmarkFocus!!.nickname = Resourcepart.from(ViewUtil.toString(nicknameField))
                } catch (e: XmppStringprepException) {
                    aTalkApp.showToastMessage(R.string.service_gui_CHANGE_NICKNAME_ERROR,
                            mBookmarkFocus!!.jid, e.message)
                    return false
                }
            }
        }
        return true
    }

    /**
     * Compare two strings if they are equal. Must check for null before compare
     *
     * @param oldStr exiting string value
     * @param newStr newly edited string
     * @return true is both are equal
     */
    private fun isEqual(oldStr: String?, newStr: String?): Boolean {
        return (TextUtils.isEmpty(oldStr) && TextUtils.isEmpty(newStr))
                || ((oldStr != null) && oldStr == newStr)
    }

    /**
     * Update the bookmarks on server.
     */
    private fun updateBookmarkedConference(): Boolean {
        var success = true
        var bookmarkedList: List<BookmarkedConference>
        val bookmarkedEntityList = ArrayList<EntityBareJid>()

        // Update the last user change bookmarkFocus
        if (!updateBookmarkFocus()) return false
        val crpWrappers = mucService.chatRoomProviders
        for (crpWrapper in crpWrappers) {
            if (crpWrapper != null) {
                val pps = crpWrapper.protocolProvider
                val accountId = pps.accountID.accountJid
                val bookmarkConferences = mAccountBookmarkConferencesList[accountId]
                val bookmarkManager = BookmarkManager.getBookmarkManager(pps.connection)
                var chatRoomWrapper: ChatRoomWrapper? = null
                try {
                    bookmarkedList = bookmarkManager.bookmarkedConferences
                    for (bookmarkedConference in bookmarkedList) {
                        bookmarkedEntityList.add(bookmarkedConference.jid)
                    }
                    if (bookmarkConferences != null) {
                        for (bookmarkConference in bookmarkConferences) {
                            val autoJoin = bookmarkConference.isAutoJoin
                            val bookmark = bookmarkConference.isBookmark
                            val name = bookmarkConference.name
                            val password = bookmarkConference.password
                            val nick = bookmarkConference.nickname
                            val chatRoomEntity = bookmarkConference.jid

                            // Update server bookmark
                            if (bookmark) {
                                bookmarkManager.addBookmarkedConference(name, chatRoomEntity, autoJoin, nick, password)
                            } else if (bookmarkedEntityList.contains(chatRoomEntity)) {
                                bookmarkManager.removeBookmarkedConference(chatRoomEntity)
                            }
                            if (autoJoin) {
                                mucService.joinChatRoom(chatRoomEntity.toString(), crpWrapper)
                                // nick???
                            }

                            // save info to local chatRoomWrapper if present
                            chatRoomWrapper = crpWrapper.findChatRoomWrapperForChatRoomID(chatRoomEntity.toString())

                            // Create new chatRoom if none and user enabled autoJoin
//                            if (autoJoin) {
//                                mucService.joinChatRoom(chatRoomWrapper);
//
//                                chatRoomWrapper = createChatRoom(pps, bookmarkConference);
//                                if (chatRoomWrapper != null)
//                                    crpWrapper.addChatRoom(chatRoomWrapper);
//                            }
                            if (chatRoomWrapper != null) {
                                chatRoomWrapper.bookmarkName = name
                                chatRoomWrapper.savePassword(password)
                                chatRoomWrapper.nickName = nick.toString()
                                chatRoomWrapper.setBookmark(bookmark)
                                chatRoomWrapper.isAutoJoin = autoJoin

                                // cmeng - testing for clearing unwanted values i.e. MUCService.OPEN_ON_ACTIVITY
                                // MUCService.setChatRoomAutoOpenOption(pps, chatRoomEntity.toString(), null);
                            }
                        }
                    }
                } catch (e: SmackException.NoResponseException) {
                    val errMag = aTalkApp.getResString(R.string.service_gui_CHATROOM_BOOKMARK_UPDATE_FAILED,
                            chatRoomWrapper, e.message)
                    Timber.w(errMag)
                    aTalkApp.showToastMessage(errMag)
                    success = false
                } catch (e: SmackException.NotConnectedException) {
                    val errMag = aTalkApp.getResString(R.string.service_gui_CHATROOM_BOOKMARK_UPDATE_FAILED,
                            chatRoomWrapper, e.message)
                    Timber.w(errMag)
                    aTalkApp.showToastMessage(errMag)
                    success = false
                } catch (e: XMPPException.XMPPErrorException) {
                    val errMag = aTalkApp.getResString(R.string.service_gui_CHATROOM_BOOKMARK_UPDATE_FAILED,
                            chatRoomWrapper, e.message)
                    Timber.w(errMag)
                    aTalkApp.showToastMessage(errMag)
                    success = false
                } catch (e: InterruptedException) {
                    val errMag = aTalkApp.getResString(R.string.service_gui_CHATROOM_BOOKMARK_UPDATE_FAILED,
                            chatRoomWrapper, e.message)
                    Timber.w(errMag)
                    aTalkApp.showToastMessage(errMag)
                    success = false
                }
            }
        }
        return success
    }
}