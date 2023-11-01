/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.AdHocChatRoomInvitationListener
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomInvitationRejectionListener
import net.java.sip.communicator.service.protocol.event.LocalUserAdHocChatRoomPresenceListener

/**
 * Allows creating, configuring, joining and administering of individual text-based ad-hoc
 * conference rooms.
 *
 * @author Valentin Martinet
 */
interface OperationSetAdHocMultiUserChat : OperationSet {
    /**
     * Creates an ad-hoc room with the named `adHocRoomName` and according to the specified
     * `adHocRoomProperties`. When the method returns the ad-hoc room the local user will
     * have joined it.
     *
     *
     *
     * @param adHocRoomName
     * the name of the `AdHocChatRoom` to create.
     * @param adHocRoomProperties
     * properties specifying how the ad-hoc room should be created; `null` for no
     * properties just like an empty `Map`
     * @throws OperationFailedException
     * if the ad-hoc room couldn't be created for some reason.
     * @throws OperationNotSupportedException
     * if chat room creation is not supported by this server
     *
     * @return the newly created `AdHocChatRoom` named `roomName`.
     */
    @Throws(OperationFailedException::class, OperationNotSupportedException::class)
    fun createAdHocChatRoom(adHocRoomName: String?,
            adHocRoomProperties: Map<String?, Any?>?): AdHocChatRoom?

    /**
     * Creates an ad-hoc room with the named `adHocRoomName` and in including to the
     * specified `contacts` for the given `reason
    ` * . When the method returns the ad-hoc room the local user will have joined it.
     *
     *
     *
     * @param adHocRoomName
     * the name of the `AdHocChatRoom` to create.
     * @param contacts
     * the contacts (ID) who are added to the room when it's created; `null` for no
     * contacts
     * @param reason
     * the reason for this invitation
     * @throws OperationFailedException
     * if the ad-hoc room couldn't be created for some reason.
     * @throws OperationNotSupportedException
     * if chat room creation is not supported by this server
     *
     * @return the newly created `AdHocChatRoom` named `roomName`.
     */
    @Throws(OperationFailedException::class, OperationNotSupportedException::class)
    fun createAdHocChatRoom(adHocRoomName: String?, contacts: List<String?>?,
            reason: String?): AdHocChatRoom?

    /**
     * Returns a list of all currently joined `AdHocChatRoom`-s.
     *
     * @return a list of all currently joined `AdHocChatRoom`-s
     */
    fun getAdHocChatRooms(): List<AdHocChatRoom?>?

    /**
     * Adds a listener that will be notified of changes in our participation in an ad-hoc chat room
     * such as us being joined, left.
     *
     * @param listener
     * a local user participation listener.
     */
    fun addPresenceListener(listener: LocalUserAdHocChatRoomPresenceListener?)

    /**
     * Removes a listener that was being notified of changes in our participation in an ad-hoc room
     * such as us being joined, left.
     *
     * @param listener
     * a local user participation listener.
     */
    fun removePresenceListener(listener: LocalUserAdHocChatRoomPresenceListener?)

    /**
     * Adds the given `listener` to the list of `AdHocChatRoomInvitationListener`-s
     * that would be notified when an add-hoc chat room invitation has been received.
     *
     * @param listener
     * the `AdHocChatRoomInvitationListener` to add
     */
    fun addInvitationListener(listener: AdHocChatRoomInvitationListener?)

    /**
     * Removes `listener` from the list of invitation listeners registered to receive
     * invitation events.
     *
     * @param listener
     * the invitation listener to remove.
     */
    fun removeInvitationListener(listener: AdHocChatRoomInvitationListener?)

    /**
     * Adds the given `listener` to the list of
     * `AdHocChatRoomInvitationRejectionListener`-s that would be notified when an add-hoc
     * chat room invitation has been rejected.
     *
     * @param listener
     * the `AdHocChatRoomInvitationListener` to add
     */
    fun addInvitationRejectionListener(listener: AdHocChatRoomInvitationRejectionListener?)

    /**
     * Removes the given listener from the list of invitation listeners registered to receive events
     * every time an invitation has been rejected.
     *
     * @param listener
     * the invitation listener to remove.
     */
    fun removeInvitationRejectionListener(listener: AdHocChatRoomInvitationRejectionListener?)

    /**
     * Informs the sender of an invitation that we decline their invitation.
     *
     * @param invitation
     * the invitation we are rejecting.
     * @param rejectReason
     * the reason to reject the invitation (optional)
     */
    fun rejectInvitation(invitation: AdHocChatRoomInvitation?, rejectReason: String?)
}