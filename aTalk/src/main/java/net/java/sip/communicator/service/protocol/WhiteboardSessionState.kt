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
package net.java.sip.communicator.service.protocol

/**
 * The WhiteboardSessionState class reflects the current state of a whiteboard session.
 *
 * @author Julien Waechter
 * @author Emil Ivov
 */
class WhiteboardSessionState
/**
 * Create a whiteboard state object with a value corresponding to the specified string.
 *
 * @param whiteboardState
 * a string representation of the state.
 */
private constructor(
    /**
     * A string representationf this Whiteboard State. Could be _WHITEBOARD_INITIALIZATION,
     * _WHITEBOARD_IN_PROGRESS, _WHITEBOARD_ENDED.
     */
    private val whiteboardStateStr: String) {
    /**
     * Returns a String representation of tha WhiteboardSte.
     *
     * @return a string value (one of the _WHITEBOARD_XXX constants) representing this whiteboard
     * state).
     */
    fun getStateString(): String {
        return whiteboardStateStr
    }

    /**
     * Returns a string represenation of this whiteboard state. Strings returned by this method have
     * the following format: "WhiteboardState:<STATE_STRING>" and are meant to be used for
     * loggin/debugging purposes.
     *
     * @return a string representation of this object.
    </STATE_STRING> */
    override fun toString(): String {
        return javaClass.name + ":" + getStateString()
    }

    companion object {
        /**
         * This constant containing a String representation of the WHITEBOARD_INITIALIZATION state.
         *
         *
         * This constant has the String value "Initializing".
         */
        const val _WHITEBOARD_INITIALIZATION = "Initializing"

        /**
         * This constant value indicates that the associated whiteboard is currently in an
         * initialization state.
         */
        val WHITEBOARD_INITIALIZATION = WhiteboardSessionState(_WHITEBOARD_INITIALIZATION)

        /**
         * This constant containing a String representation of the WHITEBOARD_IN_PROGRESS state.
         *
         *
         * This constant has the String value "In Progress".
         */
        const val _WHITEBOARD_IN_PROGRESS = "In Progress"

        /**
         * This constant value indicates that the associated whiteboard is currently in an active state.
         */
        val WHITEBOARD_IN_PROGRESS = WhiteboardSessionState(_WHITEBOARD_IN_PROGRESS)

        /**
         * This constant containing a String representation of the WHITEBOARD_ENDED state.
         *
         *
         * This constant has the String value "Ended".
         */
        const val _WHITEBOARD_ENDED = "Ended"

        /**
         * This constant value indicates that the associated whiteboard is currently in a terminated
         * phase.
         */
        val WHITEBOARD_ENDED = WhiteboardSessionState(_WHITEBOARD_ENDED)
    }
}