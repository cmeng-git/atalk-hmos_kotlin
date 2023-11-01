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
import net.java.sip.communicator.service.protocol.Contact
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smackx.chatstates.ChatState
import java.util.*

/**
 * `ChatStateNotificationEvent`s are delivered upon reception of a corresponding message
 * from a remote contact. `ChatStateNotificationEvent`s contain a state id, identifying
 * the exact chat state event that has occurred (a user has started or stopped composing), the
 * source `Contact` that generated the event and others.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class ChatStateNotificationEvent
/**
 * Creates a ChatStateNotificationEvent with the specified parameters.
 *
 * @param chatDescriptor the Chat Descriptor that has sent the notification.
 * @param state the `Contact`'s current chat state
 * @param msg the message received
 */
(chatDescriptor: Any?, // private int mChatState = OperationSetChatStateNotifications.STATE_UNKNOWN;
        private val mChatState: ChatState, private val message: Message?) : EventObject(chatDescriptor) {
    /**
     * Returns the chat state that this `ChatStateNotificationEvent` is carrying.
     *
     * @return one of the `ChatState`s indicating the chat state that this notification is about.
     */
    fun getChatState(): ChatState {
        return mChatState
    }

    /**
     * Returns a reference to the `Contact` that has sent this event.
     *
     * @return a reference to the `Contact` whose chat state we're being notified about.
     */
    fun getChatDescriptor(): Any {
        return getSource()
    }

    fun getMessage(): Message {
        return message!!
    }

    /**
     * Returns a String representation of this EventObject.
     *
     * @return A a String representation of this EventObject.
     */
    override fun toString(): String {
        val chatDescriptor = getChatDescriptor()
        val from = if (chatDescriptor is Contact) chatDescriptor.address else (chatDescriptor as ChatRoom).getName()
        return "ChatStateNotificationEvent[from = " + from + "; state = " + getChatState()
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}