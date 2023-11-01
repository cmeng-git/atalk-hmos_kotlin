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

import net.java.sip.communicator.service.protocol.WhiteboardParticipant
import net.java.sip.communicator.service.protocol.WhiteboardSession
import java.util.*

/**
 * `WhiteboardParticipantEvent`s indicate that a participant in a whiteboard session has
 * either left or entered the session.
 *
 * @author Julien Waechter
 * @author Emil Ivov
 */
class WhiteboardParticipantEvent(source: WhiteboardSession?,
        sourceWhiteboardParticipant: WhiteboardParticipant?, eventID: Int) : EventObject(source) {
    /**
     * The id indicating the type of this event.
     */
    private var eventID = -1

    /**
     * The whiteboard session participant that this event is about.
     */
    private var sourceWhiteboardParticipant: WhiteboardParticipant? = null

    /**
     * Creates a whiteboard participant event instance indicating that an event with id
     * `eventID` has happened to `sourceWhiteboardParticipant` in
     * `sourceWhiteboard`
     *
     * @param sourceWhiteboardParticipant
     * the whiteboard participant that this event is about.
     * @param source
     * the whiteboard that the source whiteboard participant is associated with.
     * @param eventID
     * one of the WHITEBOARD_PARTICIPANT_XXX member ints indicating the type of this event.
     */
    init {
        this.sourceWhiteboardParticipant = sourceWhiteboardParticipant
        this.eventID = eventID
    }

    /**
     * Returnst one of the WHITEBOARD_PARTICIPANT_XXX member ints indicating the type of this event.
     *
     * @return one of the WHITEBOARD_PARTICIPANT_XXX member ints indicating the type of this event.
     */
    fun getEventID(): Int {
        return eventID
    }

    /**
     * Returns the whiteboard session that produced this event.
     *
     * @return a reference to the `WhiteboardSession` that produced this event.
     */
    fun getSourceWhiteboard(): WhiteboardSession {
        return getSource() as WhiteboardSession
    }

    /**
     * Returns the whiteboard participant that this event is about.
     *
     * @return a reference to the `WhiteboardParticipant` instance that triggered this event.
     */
    fun getSourceWhiteboardParticipant(): WhiteboardParticipant? {
        return sourceWhiteboardParticipant
    }

    /**
     * Returns a String representation of this `WhiteboardParticipantEvent`.
     *
     * @return a String representation of this `WhiteboardParticipantEvent`.
     */
    override fun toString(): String {
        return ("WhiteboardParticipantEvent: ID=" + getEventID() + " source participant="
                + getSourceWhiteboardParticipant() + " source whiteboard=" + getSourceWhiteboard())
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * An event id value indicating that this event is about the fact that the source whiteboard
         * participant has joined the source whiteboard.
         */
        const val WHITEBOARD_PARTICIPANT_ADDED = 1

        /**
         * An event id value indicating that this event is about the fact that the source whiteboard
         * participant has left the source whiteboard.
         */
        const val WHITEBOARD_PARTICIPANT_REMOVED = 2
    }
}