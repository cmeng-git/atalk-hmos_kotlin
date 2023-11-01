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

import java.util.*

/**
 * A listener that registers for `AdHocChatRoomMessageEvent`s issued by a particular
 * `AdHocChatRoom`.
 *
 * @author Valentin Martinet
 * @author Eng Chong Meng
 */
interface AdHocChatRoomMessageListener : EventListener {
    /**
     * Called when a new incoming `IMessage` has been received.
     *
     * @param evt the `AdHocChatRoomMessageReceivedEvent` containing the newly received
     * message, its sender and other details.
     */
    fun messageReceived(evt: AdHocChatRoomMessageReceivedEvent)

    /**
     * Called when the underlying implementation has received an indication that a message, sent
     * earlier has been successfully received by the destination.
     *
     * @param evt the `AdHocChatRoomMessageDeliveredEvent` containing the id of the message
     * that has caused the event.
     */
    fun messageDelivered(evt: AdHocChatRoomMessageDeliveredEvent)

    /**
     * Called to indicate that delivery of a message sent earlier to the chat room has failed.
     * Reason code and phrase are contained by the `MessageFailedEvent`
     *
     * @param evt the `AdHocChatroomMessageDeliveryFailedEvent` containing the ID of the
     * message whose delivery has failed.
     */
    fun messageDeliveryFailed(evt: AdHocChatRoomMessageDeliveryFailedEvent)
}