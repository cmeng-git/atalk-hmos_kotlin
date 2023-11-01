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

import net.java.sip.communicator.service.protocol.ChatRoomInvitation
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import java.util.*

/**
 * `ChatRoomInvitationReceivedEvent`s indicate reception of an invitation to join a chat room.
 *
 * @author Emil Ivov
 * @author Stephane Remy
 * @author Yana Stamcheva
 */
class ChatRoomInvitationReceivedEvent
/**
 * Creates an `InvitationReceivedEvent` representing reception of the `source`
 * invitation received from the specified `from` chat room member.
 *
 * @param multiUserChatOpSet the `OperationSetMultiUserChat`, which dispatches this event
 * @param invitation the `ChatRoomInvitation` that this event is for
 * @param timestamp the exact date when the event occurred.
 */(multiUserChatOpSet: OperationSetMultiUserChat?,
        /**
         * The invitation corresponding to this event.
         */
        private val invitation: ChatRoomInvitation,
        /**
         * A timestamp indicating the exact date when the event occurred.
         */
        private val timestamp: Date) : EventObject(multiUserChatOpSet) {
    /**
     * Returns the multi user chat operation set that dispatches this event.
     *
     * @return the multi user chat operation set that dispatches this event.
     */
    fun getSourceOperationSet(): OperationSetMultiUserChat {
        return getSource() as OperationSetMultiUserChat
    }

    /**
     * Returns the `ChatRoomInvitation` that this event is for.
     *
     * @return the `ChatRoomInvitation` that this event is for.
     */
    fun getInvitation(): ChatRoomInvitation {
        return invitation
    }

    /**
     * A timestamp indicating the exact date when the event ocurred.
     *
     * @return a Date indicating when the event ocurred.
     */
    fun getTimestamp(): Date {
        return timestamp
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}