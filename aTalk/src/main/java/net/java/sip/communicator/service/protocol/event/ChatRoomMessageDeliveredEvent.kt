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
import net.java.sip.communicator.service.protocol.IMessage
import java.util.*

/**
 * `MessageDeliveredEvent`s confirm successful delivery of an instant message.
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class ChatRoomMessageDeliveredEvent
/**
 * Creates a `MessageDeliveredEvent` representing delivery of the `source` message
 * to the specified `to` contact.
 *
 * @param source the `ChatRoom` which triggered this event.
 * @param timestamp a date indicating the exact moment when the event occurred
 * @param message the message that triggered this event.
 * @param eventType indicating the type of the delivered event.
 * It is either an ACTION_MESSAGE_DELIVERED or a CONVERSATION_MESSAGE_DELIVERED.
 */(source: ChatRoom?,
        /**
         * A timestamp indicating the exact date when the event occurred.
         */
        private val mTimestamp: Date,
        /**
         * The received `IMessage`.
         */
        private val mMessage: IMessage,
        /**
         * The type of message event that this instance represents.
         */
        private val mEventType: Int) : EventObject(source) {
    /**
     * Some services can fill our room with message history.
     */
    private var mHistoryMessage = false

    /**
     * Returns the received message.
     *
     * @return the `IMessage` that triggered this event.
     */
    fun getMessage(): IMessage? {
        return mMessage
    }

    /**
     * A timestamp indicating the exact date when the event occurred.
     *
     * @return a Date indicating when the event occurred.
     */
    fun getTimestamp(): Date {
        return mTimestamp
    }

    /**
     * Returns the `ChatRoom` that triggered this event.
     *
     * @return the `ChatRoom` that triggered this event.
     */
    fun getSourceChatRoom(): ChatRoom {
        return getSource() as ChatRoom
    }

    /**
     * Returns the type of message event represented by this event instance. IMessage event type is
     * one of the XXX_MESSAGE_DELIVERED fields of this class.
     *
     * @return one of the XXX_MESSAGE_DELIVERED fields of this class indicating the type of this event.
     */
    fun getEventType(): Int {
        return mEventType
    }

    /**
     * Is current event for history message.
     *
     * @return is current event for history message.
     */
    fun isHistoryMessage(): Boolean {
        return mHistoryMessage
    }

    /**
     * Changes property, whether this event is for a history message.
     *
     * @param historyMessage whether its event for history message.
     */
    fun setHistoryMessage(historyMessage: Boolean) {
        mHistoryMessage = historyMessage
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}