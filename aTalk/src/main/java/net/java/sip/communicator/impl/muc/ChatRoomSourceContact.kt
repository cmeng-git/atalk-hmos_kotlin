/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.impl.muc

import net.java.sip.communicator.service.muc.ChatRoomPresenceStatus
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService

/**
 * Source contact for the chat rooms.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class ChatRoomSourceContact : BaseChatRoomSourceContact {
    /**
     * The auto join state of the contact.
     */
    var isAutoJoin: Boolean

    /**
     * Constructs a new chat room source contact.
     *
     * @param chatRoomName the name of the chat room associated with the room.
     * @param chatRoomID the id of the chat room associated with the room.
     * @param query the query associated with the contact.
     * @param pps the protocol provider of the contact.
     * @param isAutoJoin the auto join state.
     */
    constructor(chatRoomName: String, chatRoomID: String?, query: ChatRoomQuery,
            pps: ProtocolProviderService?, isAutoJoin: Boolean) : super(chatRoomName, chatRoomID, query, pps) {
        this.isAutoJoin = isAutoJoin
        initContactProperties(chatRoomStateByName)
    }

    /**
     * Constructs new chat room source contact.
     *
     * @param chatRoom the chat room associated with the contact.
     * @param query the query associated with the contact.
     * @param isAutoJoin the auto join state
     */
    constructor(chatRoom: ChatRoom?, query: ChatRoomQuery, isAutoJoin: Boolean) : super(chatRoom!!.getName(), chatRoom.getName(), query, chatRoom.getParentProvider()) {
        this.isAutoJoin = isAutoJoin
        initContactProperties(if (chatRoom.isJoined()) ChatRoomPresenceStatus.CHAT_ROOM_ONLINE else ChatRoomPresenceStatus.CHAT_ROOM_OFFLINE)
    }

    /**
     * Checks if the chat room associated with the contact is joined or not and returns it
     * presence status.
     *
     * @return the presence status of the chat room associated with the contact.
     */
    private val chatRoomStateByName: PresenceStatus
        get() {
            for (room in provider!!.getOperationSet(OperationSetMultiUserChat::class.java)!!.getCurrentlyJoinedChatRooms()!!) {
                if (room!!.getName() == chatRoomName) {
                    return ChatRoomPresenceStatus.CHAT_ROOM_ONLINE
                }
            }
            return ChatRoomPresenceStatus.CHAT_ROOM_OFFLINE
        }

    /**
     * Returns the index of this source contact in its parent group.
     *
     * @return the index of this contact in its parent
     */
    override val index: Int
        get() = (parentQuery as ChatRoomQuery).indexOf(this)
}