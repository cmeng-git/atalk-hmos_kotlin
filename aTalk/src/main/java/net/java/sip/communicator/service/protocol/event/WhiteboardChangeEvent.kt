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

import net.java.sip.communicator.service.protocol.WhiteboardSession

import java.beans.PropertyChangeEvent

/**
 * `WhiteboardChangeEvent`s are triggerred whenever a change occurs in a Whiteboard.
 * Dispatched events may be of one of the following types.
 *
 *
 * WHITEBOARD_STATE_CHANGE - indicates a change in the state of a Whiteboard.
 *
 *
 *
 * @author Julien Waechter
 * @author Emil Ivov
 */
class WhiteboardChangeEvent
/**
 * Creates a WhiteboardChangeEvent with the specified source, type, oldValue and newValue.
 *
 * @param source
 * the participant that produced the event.
 * @param type
 * the type of the event (the name of the property that has changed).
 * @param oldValue
 * the value of the changed property before the event occurred
 * @param newValue
 * current value of the changed property.
 */
(source: WhiteboardSession?, type: String?, oldValue: Any?,
        newValue: Any?) : PropertyChangeEvent(source, type, oldValue, newValue) {
    /**
     * Returns the type of this event.
     *
     * @return a string containing the name of the property whose change this event is reflecting.
     */
    fun getEventType(): String {
        return propertyName
    }

    /**
     * Returns a String representation of this WhiteboardChangeEvent.
     *
     * @return A a String representation of this WhiteboardChangeEvent.
     */
    override fun toString(): String {
        return ("WhiteboardChangeEvent: type=" + getEventType() + " oldV=" + oldValue
                + " newV=" + newValue)
    }

    /**
     * The Whiteboard on which the event has occurred.
     *
     * @return A reference to the `Whiteboard` on which the event has occurred.
     */
    fun getSourceWhiteboard(): WhiteboardSession {
        return getSource() as WhiteboardSession
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * An event type indicating that the corresponding event is caused by a change of the Whiteboard
         * state.
         */
        const val WHITEBOARD_STATE_CHANGE = "WhiteboardState"
    }
}