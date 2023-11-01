/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.WhiteboardInvitationListener
import net.java.sip.communicator.service.protocol.event.WhiteboardInvitationRejectionListener
import net.java.sip.communicator.service.protocol.event.WhiteboardSessionPresenceListener
import java.util.*

/**
 * Provides basic functionality for white-board.
 *
 * @author Julien Waechter
 */
interface OperationSetWhiteboarding : OperationSet {
    /**
     * Returns a list of the `WhiteboardSession`s that we have joined and are currently
     * active in.
     *
     * @return a `List` of the `WhiteboardSession`s where the user has joined using a
     * given connection.
     */
    fun getCurrentlyJoinedWhiteboards(): List<WhiteboardSession?>?

    /**
     * Returns a list of the `WhiteboardSession`s that `WhiteboardParticipant` has
     * joined and is currently active in.
     *
     * @param participant
     * the participant whose current `WhiteboardSession`s we will be querying.
     * @return a list of the `WhiteboardSession`s that `WhiteboardParticipant` has
     * joined and is currently active in.
     *
     * @throws OperationFailedException
     * if an error occurs while trying to discover the session.
     * @throws OperationNotSupportedException
     * if the server does not support white-boarding
     */
    @Throws(OperationFailedException::class, OperationNotSupportedException::class)
    fun getCurrentlyJoinedWhiteboards(participant: WhiteboardParticipant?): List<WhiteboardSession?>?

    /**
     * Creates a `WhiteboardSession` with the name `sessionName` and according to the
     * specified `sessionProperties`. When the method returns the white-board session object,
     * the local user will not have joined it and thus will not receive messages on it until the
     * `WhiteboardSession.join()` method is called.
     *
     *
     *
     * @param sessionName
     * the name of the `WhiteboardSession` to create.
     * @param sessionProperties
     * properties specifying how the session should be created.
     * @throws OperationFailedException
     * if the room couldn't be created for some reason (e.g. room already exists; user
     * already joined to an existent room or user has no permissions to create a chat room).
     * @throws OperationNotSupportedException
     * if chat room creation is not supported by this server
     *
     * @return the newly created `WhiteboardSession` named `sessionName`.
     */
    @Throws(OperationFailedException::class, OperationNotSupportedException::class)
    fun createWhiteboardSession(sessionName: String?,
            sessionProperties: Hashtable<Any?, Any?>?): WhiteboardSession?

    /**
     * Returns a reference to a `WhiteboardSession` named `sessionName` or null if no
     * such session exists.
     *
     *
     *
     * @param sessionName
     * the name of the `WhiteboardSession` that we're looking for.
     * @return the `WhiteboardSession` named `sessionName` or null if no such session
     * exists on the server that this provider is currently connected to.
     *
     * @throws OperationFailedException
     * if an error occurs while trying to discover the white-board session on the server.
     * @throws OperationNotSupportedException
     * if the server does not support white-boarding
     */
    @Throws(OperationFailedException::class, OperationNotSupportedException::class)
    fun findWhiteboardSession(sessionName: String?): WhiteboardSession?

    /**
     * Informs the sender of an invitation that we decline their invitation.
     *
     * @param invitation
     * the invitation we are rejecting.
     * @param rejectReason
     * the reason to reject the invitation (optional)
     */
    fun rejectInvitation(invitation: WhiteboardInvitation?, rejectReason: String?)

    /**
     * Adds a listener to invitation notifications. The listener will be fired anytime an invitation
     * is received.
     *
     * @param listener
     * an invitation listener.
     */
    fun addInvitationListener(listener: WhiteboardInvitationListener?)

    /**
     * Removes `listener` from the list of invitation listeners registered to receive
     * invitation events.
     *
     * @param listener
     * the invitation listener to remove.
     */
    fun removeInvitationListener(listener: WhiteboardInvitationListener?)

    /**
     * Adds a listener to invitation notifications. The listener will be fired anytime an invitation
     * is received.
     *
     * @param listener
     * an invitation listener.
     */
    fun addInvitationRejectionListener(listener: WhiteboardInvitationRejectionListener?)

    /**
     * Removes the given listener from the list of invitation listeners registered to receive events
     * every time an invitation has been rejected.
     *
     * @param listener
     * the invitation listener to remove.
     */
    fun removeInvitationRejectionListener(listener: WhiteboardInvitationRejectionListener?)

    /**
     * Returns true if `contact` supports white-board sessions.
     *
     * @param contact
     * reference to the contact whose support for white-boards we are currently querying.
     * @return a boolean indicating whether `contact` supports white-boards.
     */
    fun isWhiteboardingSupportedByContact(contact: Contact?): Boolean

    /**
     * Adds a listener that will be notified of changes in our participation in a white-board
     * session such as us being joined, left, dropped.
     *
     * @param listener
     * a local user participation listener.
     */
    fun addPresenceListener(listener: WhiteboardSessionPresenceListener?)

    /**
     * Removes a listener that was being notified of changes in our participation in a room such as
     * us being kicked, joined, left.
     *
     * @param listener
     * a local user participation listener.
     */
    fun removePresenceListener(listener: WhiteboardSessionPresenceListener?)
}