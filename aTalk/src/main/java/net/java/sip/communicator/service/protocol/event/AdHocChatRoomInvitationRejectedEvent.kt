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
import net.java.sip.communicator.service.protocol.OperationSetAdHocMultiUserChat
import java.util.*

/**
 * `AdHocChatRoomInvitationRejectedEvent`s indicates the reception of a rejection of an
 * invitation.
 *
 * @author Valentin Martinet
 */
class AdHocChatRoomInvitationRejectedEvent
/**
 * Creates a `AdHocChatRoomInvitationRejectedEvent` representing the rejection of an
 * invitation, rejected by the given `invitee`.
 *
 * @param source
 * the `OperationSetAdHocMultiUserChat` that dispatches this event
 * @param adHocChatRoom
 * the `AdHocChatRoom` for which the initial invitation was
 * @param invitee
 * the name of the invitee that rejected the invitation
 * @param reason
 * the reason of the rejection
 * @param timestamp
 * the exact date when the event ocurred
 */(source: OperationSetAdHocMultiUserChat?,
        /**
         * The `AdHocChatRoom` for which the initial invitation was.
         */
        private val adHocChatRoom: AdHocChatRoom,
        /**
         * The invitee that rejected the invitation.
         */
        private val invitee: String,
        /**
         * The reason why this invitation is rejected or null if there is no reason specified.
         */
        private val reason: String,
        /**
         * The exact date at which this event occured.
         */
        private val timestamp: Date) : EventObject(source) {
    /**
     * Returns the ad-hoc multi user chat operation set that dispatches this event.
     *
     * @return the ad-hoc multi user chat operation set that dispatches this event
     */
    fun getSourceOperationSet(): OperationSetAdHocMultiUserChat {
        return getSource() as OperationSetAdHocMultiUserChat
    }

    /**
     * Returns the `AdHocChatRoom` for which the initial invitation was.
     *
     * @return the `AdHocChatRoom` for which the initial invitation was
     */
    fun getChatRoom(): AdHocChatRoom {
        return adHocChatRoom
    }

    /**
     * Returns the name of the invitee that rejected the invitation.
     *
     * @return the name of the invitee that rejected the invitation
     */
    fun getInvitee(): String {
        return invitee
    }

    /**
     * Returns the reason for which the `AdHocChatRoomInvitation` is rejected.
     *
     * @return the reason for which the `AdHocChatRoomInvitation` is rejected.
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
}