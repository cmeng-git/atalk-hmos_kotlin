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
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.IMessage
import org.atalk.hmos.gui.chat.ChatMessage
import org.atalk.persistance.FileBackend
import java.util.*

/**
 * `MessageReceivedEvent`s indicate reception of an instant message. (for an ad-hoc chat
 * room; see `AdHocChatRoom`)
 *
 * @author Valentin Martinet
 * @author Eng Chong Meng
 */
class AdHocChatRoomMessageReceivedEvent(source: AdHocChatRoom?, from: Contact, timestamp: Date,
        message: IMessage, eventType: Int) : EventObject(source) {
    /**
     * The contact that has sent this message.
     */
    private val from: Contact

    /**
     * A timestamp indicating the exact date when the event occurred.
     */
    private val timestamp: Date

    /**
     * The received `IMessage`.
     */
    private val message: IMessage

    /**
     * The type of message event that this instance represents.
     */
    private val eventType: Int

    /**
     * Creates a `MessageReceivedEvent` representing reception of the `source` message
     * received from the specified `from` contact.
     *
     * source the `AdHocChatRoom` for which the message is received.
     * from the `Contact` that has sent this message.
     * timestamp the exact date when the event occurred.
     * message the received `IMessage`.
     * eventType the type of message event that this instance represents (one of the
     * XXX_MESSAGE_RECEIVED static fields).
     */
    init {
        var eventType = eventType
        // Convert to MESSAGE_HTTP_FILE_DOWNLOAD if it is http download link
        if (FileBackend.isHttpFileDnLink(message.getContent())) {
            eventType = ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD
        }
        this.from = from
        this.timestamp = timestamp
        this.message = message
        this.eventType = eventType
    }

    /**
     * Returns a reference to the `Contact` that has send the `IMessage` whose
     * reception this event represents.
     *
     * @return a reference to the `Contact` that has send the `IMessage` whose
     * reception this event represents.
     */
    fun getSourceChatRoomParticipant(): Contact {
        return from
    }

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
     * one of the XXX_MESSAGE_RECEIVED fields of this class.
     *
     * @return one of the XXX_MESSAGE_RECEIVED fields of this class indicating the type of this
     * event.
     */
    fun getEventType(): Int {
        return eventType
    }

    companion object {
        /**
         * An event type indicating that the message being received is a standard conversation message
         * sent by another member of the chatRoom to all current participants.
         */
        val CONVERSATION_MESSAGE_RECEIVED = ChatMessage.MESSAGE_MUC_IN

        /**
         * An event type indicating that the message being received is a special message that sent by
         * either another member or the server itself, indicating that some kind of action (other than
         * the delivery of a conversation message) has occurred. Action messages are widely used in IRC
         * through the /action and /me commands
         */
        val ACTION_MESSAGE_RECEIVED = ChatMessage.MESSAGE_ACTION

        /**
         * An event type indicting that the message being received is a system message being sent by the
         * server or a system administrator, possibly notifying us of something important such as
         * ongoing maintenance activities or server downtime.
         */
        val SYSTEM_MESSAGE_RECEIVED = ChatMessage.MESSAGE_SYSTEM
    }
}