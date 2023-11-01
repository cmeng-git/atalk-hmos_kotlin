/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.service.muc

import java.util.*
/**
 * Parent class for gui chat room events indicating addition and removal of chat rooms in the gui chat rooms list.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class ChatRoomListChangeEvent(source: ChatRoomWrapper?, eventID: Int) : EventObject(source) {
    /**
     * Returns an event id specifying whether the type of this event (e.g. CHAT_ROOM_ADDED or CHAT_ROOM_REMOVED)
     *
     * @return one of the CHAT_ROOM_XXX int fields of this class.
     */
    var eventID = -1

    /**
     * Creates a new `ChatRoom` event according to the specified parameters.
     *
     * @param source the `ChatRoom` instance that is added to the ChatRoomsList
     * @param eventID one of the CHAT_ROOM_XXX static fields indicating the nature of the event.
     */
    init {
        this.eventID = eventID
    }

    /**
     * Returns the source `ChatRoom`.
     *
     * @return the source `ChatRoom`.
     */
    val sourceChatRoom: ChatRoomWrapper
        get() = getSource() as ChatRoomWrapper

    /**
     * Returns a String representation of this `GuiChatRoomEvent`.
     *
     * @return A String representation of this `GuiChatRoomEvent`.
     */
    override fun toString(): String {
        val buff = StringBuffer("GuiChatRoomEvent-[ ChatRoomID=")
        buff.append(sourceChatRoom.chatRoomName)
        buff.append(", eventID=").append(eventID)
        buff.append(", ProtocolProvider=")
        return buff.toString()
    }

    companion object {
        /**
         * Indicates that the ChatRoomListChangeEvent instance was triggered by adding a ChatRoom in the gui.
         */
        const val CHAT_ROOM_ADDED = 1

        /**
         * Indicates that the ChatRoomListChangeEvent instance was triggered by removing a ChatRoom from the gui.
         */
        const val CHAT_ROOM_REMOVED = 2

        /**
         * Indicates that the ChatRoomListChangeEvent instance was triggered by
         * changing a ChatRoom in the gui (like changing its status, etc.).
         */
        const val CHAT_ROOM_CHANGED = 3
    }
}