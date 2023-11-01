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

import java.util.*

/**
 * A whiteboard change listener receives events indicating that a whiteboard has changed and a
 * participant has either left or joined.
 *
 * @author Julien Waechter
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface WhiteboardChangeListener : EventListener {
    /**
     * Indicates that a new whiteboard participant has joined the source whiteboard.
     *
     * @param evt the `WhiteboardParticipantEvent` containing the source whiteboard and
     * whiteboard participant.
     */
    fun whiteboardParticipantAdded(evt: WhiteboardParticipantEvent?)

    /**
     * Indicates that a whiteboard participant has left the source whiteboard.
     *
     * @param evt the `WhiteboardParticipantEvent` containing the source whiteboard and
     * whiteboard participant.
     */
    fun whiteboardParticipantRemoved(evt: WhiteboardParticipantEvent?)

    /**
     * Indicates that a change has occurred in the state of the source whiteboard.
     *
     * @param evt the `WhiteboardChangeEvent` instance containing the source whiteboards and its
     * old and new state.
     */
    fun whiteboardStateChanged(evt: WhiteboardChangeEvent?)
}