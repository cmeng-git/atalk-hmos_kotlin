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
import net.java.sip.communicator.service.protocol.ChatRoomMemberRole
import java.util.*

/**
 * Dispatched to notify interested parties that a change in our role in the source chat room has
 * occurred. Changes may include us being granted admin permissions, or other permissions.
 *
 * @see ChatRoomMemberRole
 *
 *
 * @author Emil Ivov
 * @author Stephane Remy
 */
class ChatRoomLocalUserRoleChangeEvent(sourceRoom: ChatRoom?, previousRole: ChatRoomMemberRole?,
        newRole: ChatRoomMemberRole?, isInitial: Boolean) : EventObject(sourceRoom) {
    /**
     * The previous role that local participant had.
     */
    private var previousRole: ChatRoomMemberRole? = null

    /**
     * The new role that local participant get.
     */
    private var newRole: ChatRoomMemberRole? = null

    /**
     * If `true` this is initial role set.
     */
    private var isInitial = false

    /**
     * Creates a `ChatRoomLocalUserRoleChangeEvent` representing that a change in local
     * participant role in the source chat room has occured.
     *
     * @param sourceRoom
     * the `ChatRoom` that produced the event
     * @param previousRole
     * the previous role that local participant had
     * @param newRole
     * the new role that local participant get
     * @param isInitial
     * if `true` this is initial role set.
     */
    init {
        this.previousRole = previousRole
        this.newRole = newRole
        this.isInitial = isInitial
    }

    /**
     * Returns the new role the local participant get.
     *
     * @return newRole the new role the local participant get
     */
    fun getNewRole(): ChatRoomMemberRole? {
        return newRole
    }

    /**
     * Returns the previous role that local participant had.
     *
     * @return previousRole the previous role that local participant had
     */
    fun getPreviousRole(): ChatRoomMemberRole? {
        return previousRole
    }

    /**
     * Returns the `ChatRoom`, where this event occured.
     *
     * @return the `ChatRoom`, where this event occured
     */
    fun getSourceChatRoom(): ChatRoom {
        return getSource() as ChatRoom
    }

    /**
     * Returns `true` if this is initial role set.
     *
     * @return `true` if this is initial role set.
     */
    fun isInitial(): Boolean {
        return isInitial
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}