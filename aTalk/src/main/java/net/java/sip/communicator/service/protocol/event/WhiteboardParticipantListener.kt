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
 * A `WhiteboardParticipantListener` receives events notifying of changes that have occurred
 * within a `WhiteboardParticipant`. Such changes may pertain to current whiteboard
 * participant state, their display name, address...
 *
 * @author Julien Waechter
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface WhiteboardParticipantListener : EventListener {
    /**
     * Indicates that a change has occurred in the status of the source WhiteboardParticipant.
     *
     * @param evt The `WhiteboardParticipantChangeEvent` instance containing the source event as
     * well as its previous and its new status.
     */
    fun participantStateChanged(evt: WhiteboardParticipantChangeEvent?)

    /**
     * Indicates that a change has occurred in the display name of the source WhiteboardParticipant.
     *
     * @param evt The `WhiteboardParticipantChangeEvent` instance containing the source event as
     * well as its previous and its new display names.
     */
    fun participantDisplayNameChanged(evt: WhiteboardParticipantChangeEvent?)

    /**
     * Indicates that a change has occurred in the image of the source WhiteboardParticipant.
     *
     * @param evt The `WhiteboardParticipantChangeEvent` instance containing the source event as
     * well as its previous and its new image.
     */
    fun participantImageChanged(evt: WhiteboardParticipantChangeEvent?)
}