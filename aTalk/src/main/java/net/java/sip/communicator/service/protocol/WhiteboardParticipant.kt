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

import net.java.sip.communicator.service.protocol.event.WhiteboardParticipantListener

/**
 * The WhiteboardParticipant is an interface that represents participants in a whiteboard.
 *
 * @author Julien Waechter
 * @author Emil Ivov
 */
interface WhiteboardParticipant {
    /**
     * Returns the chat room that this member is participating in.
     *
     * @return the `WhiteboardSession` instance that this member belongs to.
     */
    fun getWhiteboardSession(): WhiteboardSession?

    /**
     * Returns the protocol provider instance that this member has originated in.
     *
     * @return the `ProtocolProviderService` instance that created this member and its
     * containing cht room
     */
    fun getProtocolProvider(): ProtocolProviderService?

    /**
     * Returns the contact identifier representing this contact.
     *
     * @return a String contact address
     */
    fun getContactAddress(): String?

    /**
     * Returns the name of this member
     *
     * @return the name of this member in the room (nickname).
     */
    fun getName(): String?

    /**
     * Returns an object representing the current state of that participant.
     * WhiteboardParticipantState may vary among CONNECTING, BUSY, CONNECTED...
     *
     * @return a WhiteboardParticipantState instance representing the participant's state.
     */
    fun getState(): WhiteboardParticipantState?

    /**
     * Allows the user interface to register a listener interested in changes
     *
     * @param listener
     * a listener instance to register with this participant.
     */
    fun addWhiteboardParticipantListener(listener: WhiteboardParticipantListener?)

    /**
     * Unregisters the specified listener.
     *
     * @param listener
     * the listener to unregister.
     */
    fun removeWhiteboardParticipantListener(listener: WhiteboardParticipantListener?)
}