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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.impl.muc.MUCServiceImpl
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.service.osgi.OSGiDialogFragment
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smackx.bookmarks.BookmarkManager
import org.jivesoftware.smackx.bookmarks.BookmarkedConference
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.parts.Resourcepart
import org.jxmpp.stringprep.XmppStringprepException
import timber.log.Timber

/**
 * The chatRoom Bookmarks dialog is the one shown when the user clicks on the Bookmarks option in the main menu.
 *
 * @author Eng Chong Meng
 */
class ChatRoomBookmarkDialog : OSGiDialogFragment() {
    private lateinit var mucService: MUCServiceImpl
    private lateinit var mChatRoomWrapper: ChatRoomWrapper
    private lateinit var finishedCallback: OnFinishedCallback

    /**
     * The account list view.
     */
    private lateinit var mAccount: TextView
    private lateinit var mChatRoom: TextView
    private lateinit var mucNameField: EditText
    private lateinit var nicknameField: EditText
    private lateinit var mAutoJoin: CheckBox
    private lateinit var mBookmark: CheckBox
    private lateinit var mPasswordField: EditText

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        if (dialog != null) dialog!!.setTitle(R.string.service_gui_CHATROOM_BOOKMARK_TITLE)
        val contentView = inflater.inflate(R.layout.chatroom_bookmark, container, false)
        mAccount = contentView.findViewById(R.id.jid_account)
        mucNameField = contentView.findViewById(R.id.mucName_Edit)
        nicknameField = contentView.findViewById(R.id.nickName_Edit)
        mAutoJoin = contentView.findViewById(R.id.cb_autojoin)
        mBookmark = contentView.findViewById(R.id.cb_bookmark)
        mPasswordField = contentView.findViewById(R.id.passwordField)
        val mShowPasswordCheckBox = contentView.findViewById<CheckBox>(R.id.show_password)
        mShowPasswordCheckBox.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean -> ViewUtil.showPassword(mPasswordField, isChecked) }
        mChatRoom = contentView.findViewById(R.id.jid_chatroom)
        initBookmarkedConference()
        val mApplyButton = contentView.findViewById<Button>(R.id.button_Apply)
        mApplyButton.setOnClickListener { v: View? -> if (updateBookmarkedConference()) closeDialog() }
        val mCancelButton = contentView.findViewById<Button>(R.id.button_Cancel)
        mCancelButton.setOnClickListener { v: View? -> closeDialog() }
        isCancelable = false
        return contentView
    }

    /**
     * Creates the providers comboBox and filling its content with the current available chatRooms
     * Add available server chatRooms to the chatRoomList when providers changes
     */
    private fun initBookmarkedConference() {
        val pps = mChatRoomWrapper.protocolProvider
        val accountId = pps!!.accountID.accountJid
        // getNickName() always returns a valid or default nickname string
        val nickName = mChatRoomWrapper.nickName
        mAccount.text = accountId
        mucNameField.setText(mChatRoomWrapper.bookmarkName)
        nicknameField.setText(nickName)
        mPasswordField.setText(mChatRoomWrapper.loadPassword())
        mChatRoom.text = mChatRoomWrapper.entityBareJid.toString()
        mAutoJoin.isChecked = mChatRoomWrapper.isAutoJoin
        mBookmark.isChecked = mChatRoomWrapper.isBookmarked
    }

    private fun closeDialog() {
        if (finishedCallback != null) finishedCallback.onCloseDialog()
        dismiss()
    }

    /**
     * Update the bookmarks on server.
     */
    private fun updateBookmarkedConference(): Boolean {
        val bookmarkedEntityList = ArrayList<EntityBareJid>()
        var success = true
        val pps = mChatRoomWrapper.protocolProvider
        val bookmarkManager = BookmarkManager.getBookmarkManager(pps!!.connection)
        try {
            val bookmarkedList = bookmarkManager.bookmarkedConferences
            for (bookmarkedConference in bookmarkedList) {
                bookmarkedEntityList.add(bookmarkedConference.jid)
            }
            val name = ViewUtil.toString(mucNameField)
            val nickStr = ViewUtil.toString(nicknameField)
            val nickName = if (nickStr == null) null else Resourcepart.from(nickStr)
            val password = ViewUtil.toString(mPasswordField)
            val autoJoin = mAutoJoin.isChecked
            val bookmark = mBookmark.isChecked
            val chatRoomEntity = mChatRoomWrapper.entityBareJid

            // Update server bookmark
            if (bookmark) {
                bookmarkManager.addBookmarkedConference(name, chatRoomEntity, autoJoin, nickName, password)
            }
            else if (bookmarkedEntityList.contains(chatRoomEntity)) {
                bookmarkManager.removeBookmarkedConference(chatRoomEntity)
            }
            mChatRoomWrapper.bookmarkName = name
            mChatRoomWrapper.savePassword(password)
            mChatRoomWrapper.nickName = nickStr
            mChatRoomWrapper.setBookmark(bookmark)
            mChatRoomWrapper.isAutoJoin = autoJoin

            // save info to local chatRoomWrapper
            val pwd = password?.toByteArray()
            if (autoJoin) {
                mucService.joinChatRoom(mChatRoomWrapper, nickStr, pwd)
            }
        } catch (e: SmackException.NoResponseException) {
            val errMag = aTalkApp.getResString(R.string.service_gui_CHATROOM_BOOKMARK_UPDATE_FAILED,
                    mChatRoomWrapper, e.message)
            Timber.w(errMag)
            aTalkApp.showToastMessage(errMag)
            success = false
        } catch (e: SmackException.NotConnectedException) {
            val errMag = aTalkApp.getResString(R.string.service_gui_CHATROOM_BOOKMARK_UPDATE_FAILED,
                    mChatRoomWrapper, e.message)
            Timber.w(errMag)
            aTalkApp.showToastMessage(errMag)
            success = false
        } catch (e: XMPPException.XMPPErrorException) {
            val errMag = aTalkApp.getResString(R.string.service_gui_CHATROOM_BOOKMARK_UPDATE_FAILED,
                    mChatRoomWrapper, e.message)
            Timber.w(errMag)
            aTalkApp.showToastMessage(errMag)
            success = false
        } catch (e: InterruptedException) {
            val errMag = aTalkApp.getResString(R.string.service_gui_CHATROOM_BOOKMARK_UPDATE_FAILED,
                    mChatRoomWrapper, e.message)
            Timber.w(errMag)
            aTalkApp.showToastMessage(errMag)
            success = false
        } catch (e: XmppStringprepException) {
            val errMag = aTalkApp.getResString(R.string.service_gui_CHATROOM_BOOKMARK_UPDATE_FAILED,
                    mChatRoomWrapper, e.message)
            Timber.w(errMag)
            aTalkApp.showToastMessage(errMag)
            success = false
        }
        return success
    }

    interface OnFinishedCallback {
        fun onCloseDialog()
    }

    companion object {
        /**
         * Constructs the `ChatInviteDialog`.
         *
         * @param chatRoomWrapper the `ChatRoomWrapper` whom attributes are to be modified.
         * @param callback to be call on dialog closed.
         */
        fun getInstance(chatRoomWrapper: ChatRoomWrapper, callback: OnFinishedCallback): ChatRoomBookmarkDialog {
            val dialog = ChatRoomBookmarkDialog()
            dialog.mucService = MUCActivator.mucService
            dialog.mChatRoomWrapper = chatRoomWrapper
            dialog.finishedCallback = callback
            val args = Bundle()
            dialog.arguments = args
            return dialog
        }
    }
}