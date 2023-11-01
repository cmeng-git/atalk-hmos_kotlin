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
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import org.jxmpp.jid.EntityBareJid
import java.util.*

/**
 * `ChatRoomInvitationRejectedEvent`s indicates the reception of a rejection of an
 * invitation.
 *
 * @author Emil Ivov
 * @author Stephane Remy
 * @author Yana Stamcheva
 */
class ChatRoomInvitationRejectedEvent
/**
 * Creates a `ChatRoomInvitationRejectedEvent` representing the rejection of an
 * invitation, rejected by the given `invitee`.
 *
 * @param source
 * the `OperationSetMultiUserChat` that dispatches this event
 * @param chatRoom
 * the `ChatRoom` for which the initial invitation was
 * @param invitee
 * the name of the invitee that rejected the invitation
 * @param reason
 * the reason of the rejection
 * @param timestamp
 * the exact date when the event occurred
 */(source: OperationSetMultiUserChat?,
        /**
         * The `ChatRoom` for which the initial invitation was.
         */
        private val chatRoom: ChatRoom,
        /**
         * The invitee that rejected the invitation.
         */
        private val invitee: EntityBareJid,
        /**
         * The reason why this invitation is rejected or null if there is no reason specified.
         */
        private val reason: String,
        /**
         * The exact date at which this event occurred.
         */
        private val timestamp: Date) : EventObject(source) {
    /**
     * Returns the multi user chat operation set that dispatches this event.
     *
     * @return the multi user chat operation set that dispatches this event
     */
    fun getSourceOperationSet(): OperationSetMultiUserChat {
        return getSource() as OperationSetMultiUserChat
    }

    /**
     * Returns the `ChatRoom` for which the initial invitation was.
     *
     * @return the `ChatRoom` for which the initial invitation was
     */
    fun getChatRoom(): ChatRoom {
        return chatRoom
    }

    /**
     * Returns the name of the invitee that rejected the invitation.
     *
     * @return the name of the invitee that rejected the invitation
     */
    fun getInvitee(): EntityBareJid {
        return invitee
    }

    /**
     * Returns the reason for which the `ChatRoomInvitation` is rejected.
     *
     * @return the reason for which the `ChatRoomInvitation` is rejected.
     */
    fun getReason(): String {
        return reason
    }

    /**
     * A timestamp indicating the exact date when the event occurred.
     *
     * @return a Date indicating when the event occurred.
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