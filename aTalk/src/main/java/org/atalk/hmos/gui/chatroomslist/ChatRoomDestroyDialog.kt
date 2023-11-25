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
import android.widget.TextView
import net.java.sip.communicator.impl.msghistory.MessageHistoryActivator
import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.ChatPanel
import org.atalk.hmos.gui.chat.ChatSessionManager
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.service.osgi.OSGiFragment
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException

/**
 * ChatRoom destroy dialog allowing user to provide a reason and an alternate venue.
 *
 * @author Eng Chong Meng
 */
class ChatRoomDestroyDialog : OSGiFragment() {
    private var chatRoomWrapper: ChatRoomWrapper? = null
    private var chatPanel: ChatPanel? = null

    /**
     * {@inheritDoc}
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.muc_room_destroy_dialog, container, false)
        val bundle = arguments!!
        val message = bundle.getString(DialogActivity.EXTRA_MESSAGE)
        val msgWarn = view.findViewById<TextView>(R.id.textAlert)
        msgWarn.text = message
        return view
    }

    /**
     * Create chatRoom destroy dialog
     *
     * @param context the parent `Context`
     * @param crWrapper chatRoom wrapper
     * @param cPanel the chatPanel to send message
     */
    fun show(context: Context?, crWrapper: ChatRoomWrapper, cPanel: ChatPanel) {
        chatRoomWrapper = crWrapper
        chatPanel = cPanel
        val msgWarn = context!!.getString(R.string.service_gui_CHATROOM_DESTROY_PROMPT,
                chatRoomWrapper!!.user, chatRoomWrapper!!.chatRoomID)
        val fragmentBundle = Bundle()
        fragmentBundle.putString(DialogActivity.EXTRA_MESSAGE, msgWarn)
        DialogActivity.showCustomDialog(context,
                context.getString(R.string.service_gui_CHATROOM_DESTROY_TITLE),
                ChatRoomDestroyDialog::class.java.name, fragmentBundle,
                context.getString(R.string.service_gui_REMOVE),
                DialogListenerImpl(), null)
    }

    /**
     * Implements `DialogActivity.DialogListener` interface and handles refresh stores process.
     */
    inner class DialogListenerImpl : DialogActivity.DialogListener {
        override fun onConfirmClicked(dialog: DialogActivity): Boolean {
            val view = dialog.contentFragment!!.view!!
            val reason = ViewUtil.toString(view.findViewById(R.id.ReasonDestroy))
            val venue = ViewUtil.toString(view.findViewById(R.id.VenueAlternate))
            var entityBareJid: EntityBareJid? = null
            if (venue != null) {
                entityBareJid = try {
                    JidCreate.entityBareFrom(venue)
                } catch (ex: XmppStringprepException) {
                    aTalkApp.showToastMessage(R.string.service_gui_INVALID_ADDRESS, venue)
                    return false
                }
            }

            // When a room is destroyed, purge all the chat messages and room chat session from the database.
            val chatRoom = chatRoomWrapper!!.chatRoom!!
            val MHS = MessageHistoryActivator.messageHistoryService
            MHS.eraseLocallyStoredChatHistory(chatRoom, null)
            MUCActivator.mucService.destroyChatRoom(chatRoomWrapper!!, reason, entityBareJid)
            if (chatPanel != null) {
                ChatSessionManager.removeActiveChat(chatPanel)
            }
            return true
        }

        override fun onDialogCancelled(dialog: DialogActivity) {}
    }
}