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
import java.util.*

/**
 * Dispatched to notify interested parties that a change in the presence of an ad-hoc chat room
 * participant has occurred. Changes may include the participant being join, left...
 *
 * @author Valentin Martinet
 */
class AdHocChatRoomParticipantPresenceChangeEvent
/**
 * Creates an `AdHocChatRoomParticipantPresenceChangeEvent` representing that a change in
 * the presence of an `Contact` has occurred. Changes may include the participant being
 * join, left, etc.
 *
 * @param sourceAdHocRoom
 * the `AdHocChatRoom` that produced this event
 * @param sourceParticipant
 * the `Contact` that this event is about
 * @param eventType
 * the event type; one of the CONTACT_XXX constants
 * @param reason
 * the reason explaining why this event might have occurred
 */(sourceAdHocRoom: AdHocChatRoom?,
        /**
         * The ad-hoc chat room participant that the event relates to.
         */
        private val sourceParticipant: Contact,
        /**
         * The type of this event. Values can be any of the CONTACT_XXX fields.
         */
        private val eventType: String,
        /**
         * An optional String indicating a possible reason as to why the event might have occurred.
         */
        private val reason: String) : EventObject(sourceAdHocRoom) {
    /**
     * Returns the ad-hoc chat room that produced this event.
     *
     * @return the `AdHocChatRoom` that produced this event
     */
    fun getAdHocChatRoom(): AdHocChatRoom {
        return getSource() as AdHocChatRoom
    }

    /**
     * Returns the participant that this event is about.
     *
     * @return the `Contact` that this event is about.
     */
    fun getParticipant(): Contact {
        return sourceParticipant
    }

    /**
     * A reason String indicating a human readable reason for this event.
     *
     * @return a human readable String containing the reason for this event, or null if no
     * particular reason was specified.
     */
    fun getReason(): String {
        return reason
    }

    /**
     * Gets the indicator which determines whether this event has occurred with the well-known
     * reason of listing all users in a `ChatRoom`.
     *
     * @return `true` if this event has occurred with the well-known reason of listing all
     * users in a `ChatRoom` i.e. [.getReason] returns a value of
     * [.REASON_USER_LIST]; otherwise, `false`
     */
    fun isReasonUserList(): Boolean {
        return REASON_USER_LIST == getReason()
    }

    /**
     * Returns the type of this event which could be one of the MEMBER_XXX member field values.
     *
     * @return one of the MEMBER_XXX member field values indicating the type of this event.
     */
    fun getEventType(): String {
        return eventType
    }

    /**
     * Returns a String representation of this event.
     *
     * @return string representation of this event
     */
    override fun toString(): String {
        return ("AdHocChatRoomParticipantPresenceChangeEvent[type=" + getEventType()
                + " sourceAdHocRoom=" + getAdHocChatRoom().toString() + " member="
                + getParticipant().toString() + "]")
    }

    companion object {
        /**
         * Indicates that this event was triggered as a result of the participant joining the source
         * ad-hoc chat room.
         */
        const val CONTACT_JOINED = "ContactJoined"

        /**
         * Indicates that this event was triggered as a result of the participant leaving the source
         * ad-hoc chat room.
         */
        const val CONTACT_LEFT = "ContactLeft"

        /**
         * Indicates that this event was triggered as a result of the participant being disconnected
         * from the server brutally, or due to a ping timeout.
         */
        const val CONTACT_QUIT = "ContactQuit"

        /**
         * The well-known reason for a `AdHocChatRoomParticipantPresenceChangeEvent` to occur
         * as part of an operation which lists all users in an `AdHocChatRoom`.
         */
        val REASON_USER_LIST = ChatRoomMemberPresenceChangeEvent.REASON_USER_LIST
    }
}