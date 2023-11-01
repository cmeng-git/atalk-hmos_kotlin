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

import net.java.sip.communicator.service.protocol.AdHocChatRoom
import net.java.sip.communicator.service.protocol.IMessage
import org.atalk.hmos.gui.chat.ChatMessage
import java.util.*

/**
 * `MessageDeliveredEvent`s confirm successful delivery of an instant message. Here, it's
 * applied to an `AdHocChatRoom`.
 *
 * @author Valentin Martinet
 */
class AdHocChatRoomMessageDeliveredEvent
/**
 * Creates a `MessageDeliveredEvent` representing delivery of the `source` message
 * to the specified `to` contact.
 *
 * @param source the `AdHocChatRoom` which triggered this event.
 * @param timestamp a date indicating the exact moment when the event occurred
 * @param message the message that triggered this event.
 * @param eventType indicating the type of the delivered event. It's either an ACTION_MESSAGE_DELIVERED or
 * a CONVERSATION_MESSAGE_DELIVERED.
 */(source: AdHocChatRoom?,
        /**
         * A timestamp indicating the exact date when the event occurred.
         */
        private val timestamp: Date,
        /**
         * The received `IMessage`.
         */
        private val message: IMessage,
        /**
         * The type of message event that this instance represents.
         */
        private val eventType: Int) : EventObject(source) {
    /**
     * Returns the received message.
     *
     * @return the `IMessage` that triggered this event.
     */
    fun getMessage(): IMessage {
        return message
    }

    /**
     * A timestamp indicating the exact date when the event occurred.
     *
     * @return a Date indicating when the event occurred.
     */
    fun getTimestamp(): Date {
        return timestamp
    }

    /**
     * Returns the `AdHocChatRoom` that triggered this event.
     *
     * @return the `AdHocChatRoom` that triggered this event.
     */
    fun getSourceChatRoom(): AdHocChatRoom {
        return getSource() as AdHocChatRoom
    }

    /**
     * Returns the type of message event represented by this event instance. IMessage event type is
     * one of the XXX_MESSAGE_DELIVERED fields of this class.
     *
     * @return one of the XXX_MESSAGE_DELIVERED fields of this class indicating the type of this
     * event.
     */
    fun getEventType(): Int {
        return eventType
    }

    companion object {
        /**
         * An event type indicating that the message being received is a standard conversation message
         * sent by another participant of the ad-hoc chat room to all current participants.
         */
        val CONVERSATION_MESSAGE_DELIVERED = ChatMessage.MESSAGE_MUC_OUT

        /**
         * An event type indicating that the message being received is a special message that sent by
         * either another participant or the server itself, indicating that some kind of action (other
         * than the delivery of a conversation message) has occurred. Action messages are widely used in
         * IRC through the /action and /me commands
         */
        val ACTION_MESSAGE_DELIVERED = ChatMessage.MESSAGE_ACTION
    }
}