/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.ChatRoomMember
import java.beans.PropertyChangeEvent

/**
 * A `ChatRoomMemberPropertyChangeEvent` is issued whenever a chat room member property has
 * changed (such as the nickname for example). Event codes defined in this class describe properties
 * whose changes are being announced through this event.
 *
 * @author Emil Ivov
 * @author Stephane Remy
 */
class ChatRoomMemberPropertyChangeEvent
/**
 * Creates a `ChatRoomMemberPropertyChangeEvent` indicating that a change has occurred
 * for property `propertyName` in the `source` chat room member and that its value
 * has changed from `oldValue` to `newValue`.
 *
 *
 *
 * @param source
 * the `ChatRoomMember` whose property has changed.
 * @param memberChatRoom
 * the `ChatRoom` of the member
 * @param propertyName
 * the name of the property that has changed.
 * @param oldValue
 * the value of the property before the change occurred.
 * @param newValue
 * the value of the property after the change.
 */(source: ChatRoomMember?,
        /**
         * The `ChatRoom`, to which the corresponding member belongs.
         */
        private val memberChatRoom: ChatRoom,
        propertyName: String?, oldValue: Any?, newValue: Any?) : PropertyChangeEvent(source, propertyName, oldValue, newValue) {
    /**
     * Returns the member of the chat room, for which this event is about.
     *
     * @return the `ChatRoomMember` for which this event is about
     */
    fun getSourceChatRoomMember(): ChatRoomMember {
        return getSource() as ChatRoomMember
    }

    /**
     * Returns the chat room, to which the corresponding member belongs.
     *
     * @return the chat room, to which the corresponding member belongs
     */
    fun getMemberChatRoom(): ChatRoom {
        return memberChatRoom
    }

    /**
     * Returns a String representation of this event.
     *
     * @return String representation of this event
     */
    override fun toString(): String {
        return ("ChatRoomMemberPropertyChangeEvent[type=" + this.propertyName
                + " sourceRoomMember=" + getSource().toString() + "oldValue="
                + this.oldValue.toString() + "newValue=" + this.newValue.toString() + "]")
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * The nick name of the `ChatRoomMember` property.
         */
        const val MEMBER_NICKNAME = "MemberNickname"

        /**
         * The presence status of the `ChatRoomMember` property.
         */
        const val MEMBER_PRESENCE = "MemberPresence"
    }
}