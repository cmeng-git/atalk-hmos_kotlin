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

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import org.atalk.hmos.R
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.service.osgi.OSGiFragment

/**
 * The dialog allows user to change nickName and/or Subject.
 *
 * @author Eng Chong Meng
 */
class ChatRoomInfoChangeDialog : OSGiFragment() {
    private var mContext: Context? = null
    private var mChatRoomWrapper: ChatRoomWrapper? = null

    /**
     * {@inheritDoc}
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.muc_room_info_change_dialog, container, false)
        val bundle = arguments
        if (bundle != null) {
            val chatRoom = view.findViewById<EditText>(R.id.chatRoom_Jid)
            chatRoom.setText(bundle.getString(EXTRA_CHATROOM))
            val nicknameField = view.findViewById<EditText>(R.id.NickName_Edit)
            nicknameField.setText(bundle.getString(EXTRA_NICK))
            val subjectField = view.findViewById<EditText>(R.id.chatRoom_Subject_Edit)
            subjectField.setText(bundle.getString(EXTRA_Subject))
        }
        return view
    }

    /**
     * Create chatRoom info change dialog
     *
     * @param context the parent `Context`
     * @param chatRoomWrapper chatRoom wrapper
     */
    fun show(context: Context, chatRoomWrapper: ChatRoomWrapper) {
        mContext = context
        mChatRoomWrapper = chatRoomWrapper
        val chatRoom = chatRoomWrapper.chatRoom!!
        val fragmentBundle = Bundle()
        fragmentBundle.putString(EXTRA_CHATROOM, mChatRoomWrapper!!.chatRoomName)
        val nick = if (chatRoom.getUserNickname() == null) null else chatRoom.getUserNickname().toString()
        fragmentBundle.putString(EXTRA_NICK, nick)
        fragmentBundle.putString(EXTRA_Subject, chatRoom.getSubject())
        DialogActivity.showCustomDialog(context,
                context.getString(R.string.service_gui_CHANGE_ROOM_INFO),
                ChatRoomInfoChangeDialog::class.java.name, fragmentBundle,
                context.getString(R.string.service_gui_APPLY),
                DialogListenerImpl(), null)
    }

    /**
     * Implements `DialogActivity.DialogListener` interface and handles refresh stores process.
     */
    inner class DialogListenerImpl : DialogActivity.DialogListener {
        override fun onConfirmClicked(dialog: DialogActivity): Boolean {
            // allow nickName to contain spaces
            val view = dialog.contentFragment!!.view!!
            val nickName = ViewUtil.toString(view.findViewById<TextView>(R.id.NickName_Edit))
            val subject = ViewUtil.toString(view.findViewById<TextView>(R.id.chatRoom_Subject_Edit))
            if (nickName == null) {
                DialogActivity.showDialog(mContext!!, R.string.service_gui_CHANGE_ROOM_INFO,
                        R.string.service_gui_CHANGE_NICKNAME_NULL)
                return false
            }
            val mucService = MUCActivator.mucService
            mucService.joinChatRoom(mChatRoomWrapper!!, nickName, null, subject)
            return true
        }

        override fun onDialogCancelled(dialog: DialogActivity) {}
    }

    companion object {
        private const val EXTRA_CHATROOM = "chatRoom"
        private const val EXTRA_NICK = "nick"
        private const val EXTRA_Subject = "subject"
    }
}