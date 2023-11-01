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
import java.beans.PropertyChangeEvent

/**
 * A `ChatRoomPropertyChangeEvent` is issued whenever a chat room property has changed. Event
 * codes defined in this class describe properties whose changes are being announced through this
 * event.
 *
 * @author Emil Ivov
 * @author Stephane Remy
 */
class ChatRoomPropertyChangeEvent
/**
 * Creates a `ChatRoomPropertyChangeEvent` indicating that a change has occurred for
 * property `propertyName` in the `source` chat room and that its value has
 * changed from `oldValue` to `newValue`.
 *
 *
 *
 * @param source
 * the `ChatRoom` whose property has changed.
 * @param propertyName
 * the name of the property that has changed.
 * @param oldValue
 * the value of the property before the change occurred.
 * @param newValue
 * the value of the property after the change.
 */
(source: ChatRoom?, propertyName: String?, oldValue: Any?,
        newValue: Any?) : PropertyChangeEvent(source, propertyName, oldValue, newValue) {
    /**
     * Returns the source chat room for this event.
     *
     * @return the `ChatRoom` associated with this event.
     */
    fun getSourceChatRoom(): ChatRoom {
        return getSource() as ChatRoom
    }

    /**
     * Returns a String representation of this event.
     *
     * @return String representation of this event
     */
    override fun toString(): String {
        return ("ChatRoomPropertyChangeEvent[type=" + this.propertyName + " sourceRoom="
                + getSource() + "oldValue=" + this.oldValue + "newValue="
                + this.newValue + "]")
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * The name of the `ChatRoom` subject property.
         */
        const val CHAT_ROOM_SUBJECT = "ChatRoomSubject"

        /**
         * The name of the `ChatRoom` subject property.
         */
        const val CHAT_ROOM_USER_NICKNAME = "ChatRoomUserNickname"
    }
}