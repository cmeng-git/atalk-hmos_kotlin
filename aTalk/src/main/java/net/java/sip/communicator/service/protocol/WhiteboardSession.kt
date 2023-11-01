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

import net.java.sip.communicator.service.protocol.event.WhiteboardChangeListener
import net.java.sip.communicator.service.protocol.event.WhiteboardObjectListener
import net.java.sip.communicator.service.protocol.whiteboardobjects.WhiteboardObject
import java.util.*

/**
 * A represenation of a `WhiteboardSession`.
 *
 * @author Julien Waechter
 * @author Emil Ivov
 */
interface WhiteboardSession {
    /**
     * Returns the id of the specified Whiteboard.
     *
     * @return a String uniquely identifying the whiteboard.
     */
    fun getWhiteboardID(): String?

    /**
     * Returns an iterator over all whiteboard participants.
     *
     * @return an Iterator over all participants currently involved in the whiteboard.
     */
    fun getWhiteboardParticipants(): Iterator<WhiteboardParticipant?>?

    /**
     * Returns the number of participants currently associated with this whiteboard session.
     *
     * @return an `int` indicating the number of participants currently associated with this
     * whiteboard.
     */
    fun getWhiteboardParticipantsCount(): Int

    /**
     * Adds a whiteboard change listener to this whiteboard so that it could receive events on new
     * whiteboard participants, theme changes and others.
     *
     * @param listener
     * the listener to register
     */
    fun addWhiteboardChangeListener(listener: WhiteboardChangeListener?)

    /**
     * Removes `listener` to this whiteboard so that it won't receive further
     * `WhiteboardChangeEvent`s.
     *
     * @param listener
     * the listener to register
     */
    fun removeWhiteboardChangeListener(listener: WhiteboardChangeListener?)

    /**
     * Returns a reference to the `ProtocolProviderService` instance that created this
     * whiteboard.
     *
     * @return a reference to the `ProtocolProviderService` instance that created this
     * whiteboard.
     */
    fun getProtocolProvider(): ProtocolProviderService?

    /**
     * Joins this whiteboard with the nickname of the local user so that the user would start
     * receiving events and WhiteboardObject for it.
     *
     * @throws OperationFailedException
     * with the corresponding code if an error occurs while joining the room.
     */
    @Throws(OperationFailedException::class)
    fun join()

    /**
     * Joins this whiteboard so that the user would start receiving events and WhiteboardObject for
     * it. The method uses the nickname of the local user and the specified password in order to
     * enter the whiteboard session.
     *
     * @param password
     * the password to use when authenticating on the whiteboard session.
     * @throws OperationFailedException
     * with the corresponding code if an error occurs while joining the room.
     */
    @Throws(OperationFailedException::class)
    fun join(password: ByteArray?)

    /**
     * Returns true if the local user is currently in the whiteboard session (after whiteboarding
     * one of the [.join] methods).
     *
     * @return true if currently we're currently in this whiteboard and false otherwise.
     */
    fun isJoined(): Boolean

    /**
     * Leave this whiteboard. Once this method is whiteboarded, the user won't be listed as a member
     * of the whiteboard any more and no further whiteboard events will be delivered. Depending on
     * the underlying protocol and implementation leave() might cause the room to be destroyed if it
     * has been created by the local user.
     */
    fun leave()

    /**
     * Invites another user to this room.
     *
     *
     * If the room is password-protected, the invitee will receive a password to use to join the
     * room. If the room is members-only, the the invitee may be added to the member list.
     *
     * @param userAddress
     * the address of the user to invite to the room. (one may also invite users not on their
     * contact list).
     */
    fun invite(userAddress: String?)

    /**
     * Registers `listener` so that it would receive events every time a new WhiteboardObject
     * is received on this whiteboard.
     *
     *
     * @param listener
     * a `WhiteboardObjectListener` that would be notified every time a new
     * WhiteboardObject is received on this whiteboard.
     */
    fun addWhiteboardObjectListener(listener: WhiteboardObjectListener?)

    /**
     * Removes `listener` so that it won't receive any further WhiteboardObject events from
     * this room.
     *
     *
     * @param listener
     * the `WhiteboardObjectListener` to remove from this room
     */
    fun removeWhiteboardObjectListener(listener: WhiteboardObjectListener?)

    /**
     * Create a WhiteboardObject instance with the specified type. This method only creates the
     * object locally and it would not be visible to other session participants until it is resolved
     * with the sendWhiteboardObject(WhiteboardObject) method.
     *
     * @param name
     * the name of the object to create (should be one of the + * WhiteboardObjectXXX.NAME
     * fields).
     *
     * @return the newly created WhiteboardObject with an id
     */
    fun createWhiteboardObject(name: String?): WhiteboardObject?

    /**
     * Resolves `obj` with the other session participants. When called for the first time
     * with a specific `WhiteboardObject` instance it would appear on their whiteboards. If
     * `obj` has already been sent through this method previously, this method would result
     * in updating the way the object looks in other instances of this session (i.e. the method
     * should be used for both initially sending an object as well as sending changes made on an
     * object since the method was last called).
     *
     * @param obj
     * the `WhiteboardObject` to send.
     * @throws OperationFailedException
     * if sending the WhiteboardObject fails for some reason.
     */
    @Throws(OperationFailedException::class)
    fun sendWhiteboardObject(obj: WhiteboardObject?)

    /**
     * Sends a `WhiteboardObject` to modify and modifies the local `WhiteboardObject`
     *
     * @param obj
     * the `WhiteboardObject` to send and modify
     * @throws OperationFailedException
     * if sending the WhiteboardObject fails for some reason.
     */
    @Throws(OperationFailedException::class)
    fun moveWhiteboardObject(obj: WhiteboardObject?)

    /**
     * Sends a `WhiteboardObject` to delete and delete the local `WhiteboardObject`
     *
     * @param obj
     * the `WhiteboardObject` to send and delete
     * @throws OperationFailedException
     * if sending the WhiteboardObject fails for some reason.
     */
    @Throws(OperationFailedException::class)
    fun deleteWhiteboardObject(obj: WhiteboardObject?)

    /**
     * Adds `wbParticipant` to the list of participants in this whiteboard. If the wb
     * participant is already included in the whiteboard, the method has no effect.
     *
     * @param wbParticipant
     * the new `WhiteboardParticipant`
     */
    fun addWhiteboardParticipant(wbParticipant: WhiteboardParticipant?)

    /**
     * Removes `whiteboardParticipant` from the list of participants in this whiteboard. The
     * method has no effect if there was no such participant in the whiteboard.
     *
     * @param wbParticipant
     * the `WhiteboardParticipant` leaving the whiteboard;
     */
    fun removeWhiteboardParticipant(wbParticipant: WhiteboardParticipant?)

    /**
     * Returns the WhiteboardObjects in this whiteboard session.
     *
     * @return an `Vector` of WhiteboardObjects associated with this whiteboard.
     */
    fun getWhiteboardObjects(): Vector<WhiteboardObject?>?

    /**
     * Sets the state of this whiteboard
     *
     * @param newState
     * a reference to the `WhiteboardState` instance that the whiteboard is to enter.
     */
    fun setState(newState: WhiteboardSessionState?)

    /**
     * Returns the state that this whiteboard is currently in.
     *
     * @return a reference to the `WhiteboardState` instance that the whiteboard is currently
     * in.
     */
    fun getState(): WhiteboardSessionState?

    /**
     * Returns all the type of WhiteboardObject that this whiteboard support.
     *
     * @return all the WhiteboardObject supported by this WhiteboardSession.
     */
    fun getSupportedWhiteboardObjects(): Array<String?>?
}