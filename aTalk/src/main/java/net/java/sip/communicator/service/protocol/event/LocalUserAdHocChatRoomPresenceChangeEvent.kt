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
 * Dispatched to notify interested parties that a change in our presence in the source ad-hoc chat
 * room has occurred. Changes may include us being join, left, etc.
 *
 * @author Valentin Martinet
 */
class LocalUserAdHocChatRoomPresenceChangeEvent(source: OperationSetAdHocMultiUserChat?,
        adHocChatRoom: AdHocChatRoom?, eventType: String?, reason: String?) : EventObject(source) {
    /**
     * The `AdHocChatRoom` to which the change is related.
     */
    private var adHocChatRoom: AdHocChatRoom? = null

    /**
     * The type of this event.
     */
    private var eventType: String? = null

    /**
     * An optional String indicating a possible reason as to why the event might have occurred.
     */
    private var reason: String? = null

    /**
     * Creates an `AdHocChatRoomLocalUserPresenceChangeEvent` representing that a change in
     * local participant presence in the source ad-hoc chat room has occurred.
     *
     * @param source
     * the `OperationSetAdHocMultiUserChat`, which produced this event
     * @param adHocChatRoom
     * the `AdHocChatRoom` that this event is about
     * @param eventType
     * the type of this event.
     * @param reason
     * the reason explaining why this event might have occurred
     */
    init {
        this.adHocChatRoom = adHocChatRoom
        this.eventType = eventType
        this.reason = reason
    }

    /**
     * Returns the `OperationSetAdHocMultiUserChat`, where this event has occurred.
     *
     * @return the `OperationSetAdHocMultiUserChat`, where this event has occurred
     */
    fun getAdHocMultiUserChatOpSet(): OperationSetAdHocMultiUserChat {
        return getSource() as OperationSetAdHocMultiUserChat
    }

    /**
     * Returns the `AdHocChatRoom`, that this event is about.
     *
     * @return the `AdHocChatRoom`, that this event is about
     */
    fun getAdHocChatRoom(): AdHocChatRoom? {
        return adHocChatRoom
    }

    /**
     * A reason string indicating a human readable reason for this event.
     *
     * @return a human readable String containing the reason for this event, or null if no
     * particular reason was specified
     */
    fun getReason(): String? {
        return reason
    }

    /**
     * Returns the type of this event which could be one of the LOCAL_USER_XXX member fields.
     *
     * @return one of the LOCAL_USER_XXX fields indicating the type of this event.
     */
    fun getEventType(): String? {
        return eventType
    }

    /**
     * Returns a String representation of this event.
     *
     * @return a `String` for representing this event.
     */
    override fun toString(): String {
        return "AdHocChatRoomLocalUserPresenceChangeEvent[type=${getEventType()}; sourceAdHocRoom=${getAdHocChatRoom().toString()}]"
    }

    companion object {
        /**
         * Indicates that this event was triggered as a result of the local participant joining an
         * ad-hoc chat room.
         */
        const val LOCAL_USER_JOINED = "LocalUserJoined"

        /**
         * Indicates that this event was triggered as a result of the local participant failed to join
         * an ad-hoc chat room.
         */
        const val LOCAL_USER_JOIN_FAILED = "LocalUserJoinFailed"

        /**
         * Indicates that this event was triggered as a result of the local participant leaving an
         * ad-hoc chat room.
         */
        const val LOCAL_USER_LEFT = "LocalUserLeft"

        /**
         * Indicates that this event was triggered as a result of the local participant being
         * disconnected from the server brutally, or ping timeout.
         */
        const val LOCAL_USER_DROPPED = "LocalUserDropped"
    }
}