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
package org.atalk.hmos.gui.chat

import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.event.ChatStateNotificationEvent
import org.jxmpp.jid.EntityFullJid
import java.util.*

/**
 * The `ChatStateNotificationHandler` is the class that handles chat state notification
 * events and launches the corresponding user interface.
 *
 * @author Eng Chong Meng
 */
object ChatStateNotificationHandler {
    private var chatStateTimer = Timer()

    /**
     * Informs the user what is the chat state of his chat contacts.
     *
     * @param evt the event containing details on the chat state notification
     * @param chatFragment the chat parent fragment
     */
    fun handleChatStateNotificationReceived(evt: ChatStateNotificationEvent?, chatFragment: ChatFragment?) {
        chatStateTimer.cancel()
        chatStateTimer = Timer()

        /*
         * If the given event doesn't concern the chat fragment chatDescriptor we have nothing more to do here.
         */
        val chatDescriptor = evt!!.getChatDescriptor()
        if (chatFragment != null) {
            val chatSession = chatFragment.chatPanel!!.chatSession
            val chatTransport = chatSession!!.currentChatTransport
            // return if event is not for the chatDescriptor session
            if (chatTransport == null || chatDescriptor != chatTransport.descriptor) return

            // return if receive own chat state notification
            if (chatDescriptor is ChatRoom) {
                val entityJid = evt.getMessage().from
                val fromNick = entityJid.resourceOrNull
                val multiUserChat = chatDescriptor.getMultiUserChat()
                val nickName = multiUserChat.nickname
                if (nickName != null && nickName == fromNick) return
            }
            if (chatFragment.chatListView != null && chatFragment.chatListAdapter != null) {
                if (evt.getMessage().body != null) {
                    chatFragment.setChatState(null, null)
                } else {
                    val fullFrom = evt.getMessage().from as EntityFullJid
                    var sender = fullFrom.localpart.toString()
                    if (chatDescriptor is ChatRoom) {
                        sender = fullFrom.resourceOrEmpty.toString()
                    }

                    // Display current chatState for a 10-seconds duration
                    val chatState = evt.getChatState()
                    chatFragment.setChatState(chatState, sender)
                    chatStateTimer.schedule(ChatTimerTask(chatFragment), 10000)
                }
            }
        }
    }

    /**
     * Clear the chat state display message after display timer expired.
     */
    private class ChatTimerTask(private val chatFragment: ChatFragment) : TimerTask() {
        override fun run() {
            chatFragment.setChatState(null, null)
        }
    }
}