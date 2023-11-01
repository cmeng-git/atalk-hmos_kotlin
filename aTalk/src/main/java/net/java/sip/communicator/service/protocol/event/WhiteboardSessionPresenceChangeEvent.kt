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
 * Dispatched to notify interested parties that a change in our presence in the source whiteboard
 * has occured. Changes may include us being joined, left, etc.
 *
 * @author Yana Stamcheva
 */
class WhiteboardSessionPresenceChangeEvent(source: OperationSetWhiteboarding?,
        session: WhiteboardSession?, eventType: String?, reason: String?) : EventObject(source) {
    /**
     * The `WhiteboardSession` to which the change is related.
     */
    private var whiteboardSession: WhiteboardSession? = null

    /**
     * The type of this event.
     */
    private var eventType: String? = null

    /**
     * An optional String indicating a possible reason as to why the event might have occurred.
     */
    private var reason: String? = null

    /**
     * Creates a `WhiteboardSessionPresenceChangeEvent` representing that a change in local
     * participant presence in the source white-board has occured.
     *
     * @param source
     * the `OperationSetWhiteboarding`, which produced this event
     * @param session
     * the `WhiteboardSession` that this event is about
     * @param eventType
     * the type of this event.
     * @param reason
     * the reason explaining why this event might have occurred
     */
    init {
        whiteboardSession = session
        this.eventType = eventType
        this.reason = reason
    }

    /**
     * Returns the `OperationSetWhiteboarding`, where this event has occurred.
     *
     * @return the `OperationSetWhiteboarding`, where this event has occurred
     */
    fun getWhiteboardOpSet(): OperationSetWhiteboarding {
        return getSource() as OperationSetWhiteboarding
    }

    /**
     * Returns the `WhiteboardSession`, that this event is about.
     *
     * @return the `WhiteboardSession`, that this event is about
     */
    fun getWhiteboardSession(): WhiteboardSession? {
        return whiteboardSession
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
     * @return String representation of this event
     */
    override fun toString(): String {
        return ("WhiteboardSessionPresenceChangeEvent[type=" + getEventType() + " whiteboard="
                + getWhiteboardSession() + "]")
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Indicates that this event was triggered as a result of the local participant joining a
         * whiteboard.
         */
        const val LOCAL_USER_JOINED = "LocalUserJoined"

        /**
         * Indicates that this event was triggered as a result of the local participant failed to join a
         * whiteboard.
         */
        const val LOCAL_USER_JOIN_FAILED = "LocalUserJoinFailed"

        /**
         * Indicates that this event was triggered as a result of the local participant leaving a
         * whiteboard.
         */
        const val LOCAL_USER_LEFT = "LocalUserLeft"

        /**
         * Indicates that this event was triggered as a result of the local participant being kicked
         * from a whiteboard.
         */
        const val LOCAL_USER_KICKED = "LocalUserKicked"

        /**
         * Indicates that this event was triggered as a result of the local participant beeing
         * disconnected from the server brutally, or ping timeout.
         */
        const val LOCAL_USER_DROPPED = "LocalUserDropped"
    }
}