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

import net.java.sip.communicator.service.protocol.AdHocChatRoomInvitation
import net.java.sip.communicator.service.protocol.OperationSetAdHocMultiUserChat
import java.util.*

/**
 * `AdHocChatRoomInvitationReceivedEvent`s indicate reception of an invitation to join an
 * ad-hoc chat room.
 *
 * @author Valentin Martinet
 */
class AdHocChatRoomInvitationReceivedEvent
/**
 * Creates an `InvitationReceivedEvent` representing reception of the `source`
 * invitation received from the specified `from` ad-hoc chat room participant.
 *
 * @param adHocMultiUserChatOpSet
 * the `OperationSetAdHocMultiUserChat`, which dispatches this event
 * @param invitation
 * the `AdHocChatRoomInvitation` that this event is for
 * @param timestamp
 * the exact date when the event occurred.
 */(
        adHocMultiUserChatOpSet: OperationSetAdHocMultiUserChat?,
        /**
         * The invitation corresponding to this event.
         */
        private val invitation: AdHocChatRoomInvitation,
        /**
         * A timestamp indicating the exact date when the event occurred.
         */
        private val timestamp: Date) : EventObject(adHocMultiUserChatOpSet) {
    /**
     * Returns the ad-hoc multi user chat operation set that dispatches this event.
     *
     * @return the ad-hoc multi user chat operation set that dispatches this event.
     */
    fun getSourceOperationSet(): OperationSetAdHocMultiUserChat {
        return getSource() as OperationSetAdHocMultiUserChat
    }

    /**
     * Returns the `AdHocChatRoomInvitation` that this event is for.
     *
     * @return the `AdHocChatRoomInvitation` that this event is for.
     */
    fun getInvitation(): AdHocChatRoomInvitation {
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
}