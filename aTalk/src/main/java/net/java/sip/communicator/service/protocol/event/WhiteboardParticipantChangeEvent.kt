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
import java.beans.PropertyChangeEvent

/**
 * WhiteboardParticipantChangeEvent-s are triggerred wheneve a change occurs in a
 * WhiteboardParticipant. Dispatched events may be of one of the following types.
 *
 *
 * WHITEBOARD_PARTICIPANT_STATUS_CHANGE - indicates a change in the status of the participant.
 *
 *
 * WHITEBOARD_PARTICIPANT_DISPLAY_NAME_CHANGE - means that participant's display name has changed
 *
 *
 * WHITEBOARD_PARTICIPANT_IMAGE_CHANGE - participant updated photo.
 *
 *
 *
 * @author Julien Waechter
 * @author Emil Ivov
 */
class WhiteboardParticipantChangeEvent @JvmOverloads constructor(source: WhiteboardParticipant?, type: String?,
        oldValue: Any?, newValue: Any?, reason: String? = null) : PropertyChangeEvent(source, type, oldValue, newValue) {
    /**
     * A reason string further explaining the event (may be null). The string would be mostly used
     * for events issued upon a WhiteboardParticipantState transition that has led to a FAILED
     * state.
     */
    private var reason: String? = null
    /**
     * Creates a WhiteboardParticipantChangeEvent with the specified source, type, oldValue and
     * newValue.
     *
     * @param source
     * the participant that produced the event.
     * @param type
     * the type of the event (i.e. address change, state change etc.).
     * @param oldValue
     * the value of the changed property before the event occurred
     * @param newValue
     * current value of the changed property.
     * @param reason
     * a string containing a human readable explanation for the reason that triggerred this
     * event (may be null).
     */
    /**
     * Creates a WhiteboardParticipantChangeEvent with the specified source, type, oldValue and
     * newValue.
     *
     * @param source
     * the participant that produced the event.
     * @param type
     * the type of the event (i.e. address change, state change etc.).
     * @param oldValue
     * the value of the changed property before the event occurred
     * @param newValue
     * current value of the changed property.
     */
    init {
        this.reason = reason
    }

    /**
     * Returns the type of this event.
     *
     * @return a string containing one of the following values:
     * WHITEBOARD_PARTICIPANT_STATUS_CHANGE, WHITEBOARD_PARTICIPANT_DISPLAY_NAME_CHANGE,
     * WHITEBOARD_PARTICIPANT_ADDRESS_CHANGE, WHITEBOARD_PARTICIPANT_IMAGE_CHANGE
     */
    fun getEventType(): String {
        return propertyName
    }

    /**
     * Returns a String representation of this WhiteboardParticipantChangeEvent.
     *
     * @return A a String representation of this WhiteboardParticipantChangeEvent.
     */
    override fun toString(): String {
        return ("WhiteboardParticipantChangeEvent: type=" + getEventType() + " oldV="
                + oldValue + " newV=" + newValue + " for participant="
                + getSourceWhiteboardParticipant())
    }

    /**
     * Returns the `WhiteboardParticipant` that this event is about.
     *
     * @return a reference to the `WhiteboardParticipant` that is the source of this event.
     */
    fun getSourceWhiteboardParticipant(): WhiteboardParticipant {
        return getSource() as WhiteboardParticipant
    }

    /**
     * Returns a reason string further explaining the event (may be null). The string would be
     * mostly used for events issued upon a WhiteboardParticipantState transition that has led to a
     * FAILED state.
     *
     * @return a reason string further explaining the event or null if no reason was set.
     */
    fun getReasonString(): String? {
        return reason
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * An event type indicating that the corresponding event is caused by a change of the
         * WhiteboardParticipant's status.
         */
        const val WHITEBOARD_PARTICIPANT_STATE_CHANGE = "WhiteboardParticipantStatusChange"

        /**
         * An event type indicating that the corresponding event is caused by a change of the
         * participant's display name.
         */
        const val WHITEBOARD_PARTICIPANT_DISPLAY_NAME_CHANGE = "WhiteboardParticipantDisplayNameChange"

        /**
         * An event type indicating that the corresponding event is caused by a change of the
         * participant's photo/picture.
         */
        const val WHITEBOARD_PARTICIPANT_IMAGE_CHANGE = "WhiteboardParticipantImageChange"
    }
}