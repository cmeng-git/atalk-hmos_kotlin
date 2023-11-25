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
import net.java.sip.communicator.service.protocol.ChatRoomMember
import org.jxmpp.jid.Jid
import java.util.*

/**
 * Dispatched to notify interested parties that a change in the presence of a chat room member has
 * occurred. Changes may include the participant being kicked, join, left...
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
/**
 * Creates a `ChatRoomMemberPresenceChangeEvent` representing that a change in the presence of a
 * `ChatRoomMember` has occurred. Changes may include the participant being kicked, join, left, etc.
 *
 * @param sourceRoom the `ChatRoom` that produced this event
 * @param sourceMember the `ChatRoomMember` who this event is about
 * @param actor the ChatRoom Member who participated as an actor in the new event. For example, in the case
 * of a `MEMBER_KICKED` event the `actor` is the moderator (e.g. user@host.org) who kicked the `sourceMember`.
 * @param eventType the event type; one of the MEMBER_XXX constants
 * @param reason the reason explaining why this event might have occurred
 */
class ChatRoomMemberPresenceChangeEvent(
        sourceRoom: ChatRoom,
        /**
         * The chat room member that the event relates to.
         */
        private val sourceMember: ChatRoomMember,
        /**
         * The moderator that kicked the occupant from the room (e.g. user@host.org).
         */
        private val actor: Jid?,
        /**
         * The type of this event. Values can be any of the MEMBER_XXX fields.
         */
        private val eventType: String,
        /**
         * An optional String indicating a possible reason as to why the event might have occurred.
         */
        private val reason: String?,
) : EventObject(sourceRoom) {
    /**
     * Creates a `ChatRoomMemberPresenceChangeEvent` representing that a change in the presence of a
     * `ChatRoomMember` has occurred. Changes may include the participant being kicked, join, left, etc.
     *
     * @param sourceRoom the `ChatRoom` that produced this event
     * @param sourceMember the `ChatRoomMember` that this event is about
     * @param eventType the event type; one of the MEMBER_XXX constants
     * @param reason the reason explaining why this event might have occurred
     */
    constructor(
            sourceRoom: ChatRoom, sourceMember: ChatRoomMember,
            eventType: String, reason: String?,
    ) : this(sourceRoom, sourceMember, null, eventType, reason)

    /**
     * Returns the chat room that produced this event.
     *
     * @return the `ChatRoom` that produced this event
     */
    fun getChatRoom(): ChatRoom {
        return getSource() as ChatRoom
    }

    /**
     * Returns the chat room member that this event is about.
     *
     * @return the `ChatRoomMember` that this event is about.
     */
    fun getChatRoomMember(): ChatRoomMember {
        return sourceMember
    }

    /**
     * A reason String indicating a human readable reason for this event.
     *
     * @return a human readable String containing the reason for this event, or null if no
     * particular reason was specified.
     */
    fun getReason(): String? {
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
     * @return String representation of this event
     */
    override fun toString(): String {
        return ("ChatRoomMemberPresenceChangeEvent[type=" + getEventType() + " sourceRoom="
                + getChatRoom().toString() + " member=" + getChatRoomMember().toString() + "]")
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Indicates that this event was triggered as a result of the participant joining the source chat room.
         */
        const val MEMBER_JOINED = "MemberJoined"

        /**
         * Indicates that this event was triggered as a result of the participant leaving the source chat room.
         */
        const val MEMBER_LEFT = "MemberLeft"

        /**
         * Indicates that this event was triggered as a result of the participant being "kicked" out of the chat room.
         */
        const val MEMBER_KICKED = "MemberKicked"

        /**
         * Indicates that this event was triggered as a result of the participant being disconnected
         * from the server brutally, or due to a ping timeout.
         */
        const val MEMBER_QUIT = "MemberQuit"

        /**
         * Indicated that this event was triggered as a result of new information
         * about the participant becoming available due to a presence
         */
        const val MEMBER_UPDATED = "MemberUpdated"

        /**
         * The well-known reason for a `ChatRoomMemberPresenceChangeEvent` to occur as part of an
         * operation which lists all users in a `ChatRoom`.
         */
        const val REASON_USER_LIST = "ReasonUserList"
    }
}