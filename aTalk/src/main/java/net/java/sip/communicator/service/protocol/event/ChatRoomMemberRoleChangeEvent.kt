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
import net.java.sip.communicator.service.protocol.ChatRoomMemberRole
import java.util.*

/**
 * Dispatched to notify interested parties that a change in a member role in the source room has
 * occurred. Changes may include member being granted admin permissions, or other permissions.
 *
 * @see ChatRoomMemberRole
 *
 *
 * @author Emil Ivov
 * @author Stephane Remy
 */
class ChatRoomMemberRoleChangeEvent(sourceRoom: ChatRoom?, sourceMember: ChatRoomMember?,
        previousRole: ChatRoomMemberRole?, newRole: ChatRoomMemberRole?) : EventObject(sourceRoom) {
    /**
     * The member that the event relates to.
     */
    private var sourceMember: ChatRoomMember? = null

    /**
     * The previous role that this member had.
     */
    private var previousRole: ChatRoomMemberRole? = null

    /**
     * The new role that this member get.
     */
    private var newRole: ChatRoomMemberRole? = null

    /**
     * Creates a `ChatRoomMemberRoleChangeEvent` representing that a change in member role in
     * the source chat room has occured.
     *
     * @param sourceRoom
     * the `ChatRoom` that produced this event
     * @param sourceMember
     * the `ChatRoomMember` that this event is about
     * @param previousRole
     * the previous role that member had
     * @param newRole
     * the new role that member get
     */
    init {
        this.sourceMember = sourceMember
        this.previousRole = previousRole
        this.newRole = newRole
    }

    /**
     * Returns the new role given to the member that this event is about.
     *
     * @return the new role given to the member that this event is about
     */
    fun getNewRole(): ChatRoomMemberRole? {
        return newRole
    }

    /**
     * Returns the previous role the member that this event is about had.
     *
     * @return the previous role the member that this event is about had
     */
    fun getPreviousRole(): ChatRoomMemberRole? {
        return previousRole
    }

    /**
     * Returns the chat room that produced this event.
     *
     * @return the `ChatRoom` that produced this event
     */
    fun getSourceChatRoom(): ChatRoom {
        return getSource() as ChatRoom
    }

    /**
     * Returns the member that this event is about.
     *
     * @return the `ChatRoomMember` that this event is about
     */
    fun getSourceMember(): ChatRoomMember? {
        return sourceMember
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}