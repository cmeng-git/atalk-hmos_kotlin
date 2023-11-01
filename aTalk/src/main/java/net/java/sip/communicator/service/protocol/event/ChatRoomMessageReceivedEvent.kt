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

import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.impl.muc.MUCServiceImpl
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.ChatRoomMember
import net.java.sip.communicator.service.protocol.IMessage
import org.atalk.hmos.gui.chat.ChatMessage
import org.atalk.persistance.FileBackend
import java.util.*

/**
 * `MessageReceivedEvent`s indicate reception of an instant message.
 *
 * @author Emil Ivov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
class ChatRoomMessageReceivedEvent(source: ChatRoom, from: ChatRoomMember, timestamp: Date,
        message: IMessage, eventType_: Int) : EventObject(source) {
    /**
     * The chat room member that has sent this message.
     */
    private val from: ChatRoomMember

    /**
     * A timestamp indicating the exact date when the event occurred.
     */
    private val mTimestamp: Date

    /**
     * The received `IMessage`.
     */
    private val mMessage: IMessage

    /**
     * The type of message event that this instance represents.
     */
    private val mEventType: Int

    /**
     * Some services can fill our room with message history.
     */
    private var mHistoryMessage = false
    private val isAutoJoin: Boolean

    /**
     * Indicates whether the message is important or not.
     */
    private var isImportantMessage = false

    /**
     * Creates a `MessageReceivedEvent` representing reception of the `source`
     * message received from the specified `from` contact.
     *
     * @param source the `ChatRoom` for which the message is received.
     * @param from the `ChatRoomMember` that has sent this message.
     * @param timestamp the exact date when the event occurred.
     * @param message the received `IMessage`.
     * @param eventType the type of message event that this instance represents
     * (one of the XXX_MESSAGE_RECEIVED static fields).
     */
    init {
        var eventType = eventType_
        // Convert to MESSAGE_HTTP_FILE_DOWNLOAD if it is http download link
        if (FileBackend.isHttpFileDnLink(message.getContent())) {
            eventType = ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD
        }
        this.from = from
        mTimestamp = timestamp
        mMessage = message
        mEventType = eventType
        val mucService = MUCActivator.mucService
        val chatRoomWrapper: ChatRoomWrapper? = mucService.getChatRoomWrapperByChatRoom(source, false)
        isAutoJoin = chatRoomWrapper != null && chatRoomWrapper.isAutoJoin
    }

    /**
     * Returns a reference to the `ChatRoomMember` that has send the `IMessage`
     * whose reception this event represents.
     *
     * @return a reference to the `ChatRoomMember` that has send the `IMessage`
     * whose reception this event represents.
     */
    fun getSourceChatRoomMember(): ChatRoomMember {
        return from
    }

    /**
     * Returns the received message.
     *
     * @return the `IMessage` that triggered this event.
     */
    fun getMessage(): IMessage {
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
     * one of the XXX_MESSAGE_RECEIVED fields of this class.
     *
     * @return one of the XXX_MESSAGE_RECEIVED fields of this class indicating the type of this event.
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
     * Is current chatRoom autoJoined.
     *
     * @return true if current event is from autoJoined chatRoom.
     */
    fun isAutoJoin(): Boolean {
        return isAutoJoin
    }

    /**
     * Changes property, whether this event is for a history message.
     *
     * @param historyMessage whether its event for history message.
     */
    fun setHistoryMessage(historyMessage: Boolean) {
        mHistoryMessage = historyMessage
    }

    /**
     * Sets the the important message flag of the event.
     *
     * @param isImportant the value to be set.
     */
    fun setImportantMessage(isImportant: Boolean) {
        isImportantMessage = isImportant
    }

    /**
     * Returns `true` if message is important and `false` if not.
     *
     * @return `true` if message is important and `false` if not.
     */
    fun isImportantMessage(): Boolean {
        return isImportantMessage
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}