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

import net.java.sip.communicator.service.protocol.NotificationMessage
import net.java.sip.communicator.service.protocol.OperationSetMessageWaiting
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import java.util.*

/**
 * `MessageWaitingEvent` indicates a message waiting event is received.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
`` */
class MessageWaitingEvent
/**
 * Constructs the Event with the given source, typically the provider and number of messages.
 *
 * @param messageType the message type for this event.
 * @param source the protocol provider from which this event is coming.
 * @param account the account URI we can use to reach the messages.
 * @param unreadMessages the unread messages.
 * @param readMessages the read messages.
 * @param unreadUrgentMessages the unread urgent messages.
 * @param readUrgentMessages the read urgent messages.
 */ @JvmOverloads constructor(source: ProtocolProviderService?,
                              /**
                               * The message type for this event.
                               */
                              private val messageType: OperationSetMessageWaiting.MessageType,
                              /**
                               * The URI we can use to reach messages from provider that is firing the event.
                               */
                              private val account: String,
                              /**
                               * Number of new/unread messages.
                               */
                              private val unreadMessages: Int,
                              /**
                               * Number of old/read messages.
                               */
                              private val readMessages: Int,
                              /**
                               * Number of new/unread urgent messages.
                               */
                              private val unreadUrgentMessages: Int,
                              /**
                               * Number of old/read messages.
                               */
                              private val readUrgentMessages: Int,
                              /**
                               * The list of notification messages concerned by this event.
                               */
                              private val messageList: List<NotificationMessage>? = null) : EventObject(source) {
    /**
     * Constructs the Event with the given source, typically the provider and number of messages.
     *
     * @param messageType the message type for this event.
     * @param source the protocol provider from which this event is coming.
     * @param account the account URI we can use to reach the messages.
     * @param unreadMessages the unread messages.
     * @param readMessages the read messages.
     * @param unreadUrgentMessages the unread urgent messages.
     * @param readUrgentMessages the read urgent messages.
     * @param messageList the list of messages that this event is about.
     */
    /**
     * Returns the `ProtocolProviderService` which originated this event.
     *
     * @return the source `ProtocolProviderService`
     */
    fun getSourceProvider(): ProtocolProviderService {
        return getSource() as ProtocolProviderService
    }

    /**
     * The URI we can use to reach messages from provider that is firing the event.
     *
     * @return account URI.
     */
    fun getAccount(): String {
        return account
    }

    /**
     * Number of new/unread messages.
     *
     * @return Number of new/unread messages.
     */
    fun getUnreadMessages(): Int {
        return unreadMessages
    }

    /**
     * Number of old/read messages.
     *
     * @return Number of old/read messages.
     */
    fun getReadMessages(): Int {
        return readMessages
    }

    /**
     * Number of new/unread urgent messages.
     *
     * @return Number of new/unread urgent messages.
     */
    fun getUnreadUrgentMessages(): Int {
        return unreadUrgentMessages
    }

    /**
     * Number of old/read messages.
     *
     * @return Number of old/read messages.
     */
    fun getReadUrgentMessages(): Int {
        return readUrgentMessages
    }

    /**
     * The message type for this event.
     *
     * @return the message type.
     */
    fun getMessageType(): OperationSetMessageWaiting.MessageType {
        return messageType
    }

    fun getMessages(): Iterator<NotificationMessage>? {
        return messageList?.iterator()
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}