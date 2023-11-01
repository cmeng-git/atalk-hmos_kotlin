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

import net.java.sip.communicator.service.protocol.OperationSetWhiteboarding
import net.java.sip.communicator.service.protocol.WhiteboardSession
import java.util.*

/**
 * `WhiteboardInvitationRejectedEvent`s indicates the reception of a rejection of an
 * invitation.
 *
 * @author Yana Stamcheva
 */
class WhiteboardInvitationRejectedEvent
/**
 * Creates a `WhiteboardInvitationRejectedEvent` representing the rejection of an
 * invitation, rejected by the given `invitee`.
 *
 * @param source
 * the `OperationSetWhiteboarding` that dispatches this event
 * @param session
 * the `WhiteboardSession` for which the initial invitation was
 * @param invitee
 * the name of the invitee that rejected the invitation
 * @param reason
 * the reason of the rejection
 * @param timestamp
 * the exact date when the event ocurred
 */(source: OperationSetWhiteboarding?,
    /**
     * The `WhiteboardSession` for which the initial invitation was.
     */
    private val whiteboardSession: WhiteboardSession,
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
     * Returns the whiteboarding operation set that dispatches this event.
     *
     * @return the whiteboarding operation set that dispatches this event
     */
    fun getSourceOperationSet(): OperationSetWhiteboarding {
        return getSource() as OperationSetWhiteboarding
    }

    /**
     * Returns the `WhiteboardSession` for which the initial invitation was.
     *
     * @return the `WhiteboardSession` for which the initial invitation was
     */
    fun getWhiteboardSession(): WhiteboardSession {
        return whiteboardSession
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
     * Returns the reason for which the `ChatRoomInvitation` is rejected.
     *
     * @return the reason for which the `ChatRoomInvitation` is rejected.
     */
    fun getReason(): String {
        return reason
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