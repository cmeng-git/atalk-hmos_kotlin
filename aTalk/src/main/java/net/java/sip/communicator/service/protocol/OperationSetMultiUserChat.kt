/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.ChatRoomInvitationListener
import net.java.sip.communicator.service.protocol.event.ChatRoomInvitationRejectionListener
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceListener
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.Jid
import org.jxmpp.stringprep.XmppStringprepException

/**
 * Allows creating, configuring, joining and administering of individual text-based conference
 * rooms.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface OperationSetMultiUserChat : OperationSet {
    /**
     * Returns the `List` of `String`s indicating chat rooms currently available on
     * the server that this protocol provider is connected to.
     *
     * @return a `java.util.List` of the name `String`s for chat rooms that are
     * currently available on the server that this protocol provider is connected to.
     * @throws OperationFailedException if we failed retrieving this list from the server.
     * @throws OperationNotSupportedException if the server does not support multi-user chat
     */
    @Throws(OperationFailedException::class, OperationNotSupportedException::class)
    fun getExistingChatRooms(): List<EntityBareJid?>?

    /**
     * Returns a list of the chat rooms that we have joined and are currently active in.
     *
     * @return a `List` of the rooms where the user has joined using a given connection.
     */
    fun getCurrentlyJoinedChatRooms(): List<ChatRoom?>?

    /**
     * Returns a list of the chat rooms that `chatRoomMember` has joined and is currently active in.
     *
     * @param chatRoomMember the chatRoomMember whose current ChatRooms we will be querying.
     * @return a list of the chat rooms that `chatRoomMember` has joined and is currently
     * active in.
     * @throws OperationFailedException if an error occurs while trying to discover the room on the server.
     * @throws OperationNotSupportedException if the server does not support multi-user chat
     */
    @Throws(OperationFailedException::class, OperationNotSupportedException::class)
    fun getCurrentlyJoinedChatRooms(chatRoomMember: ChatRoomMember?): List<String?>?

    /**
     * Creates a room with the named `roomName` and according to the specified
     * `roomProperties` on the server that this protocol provider is currently connected to.
     * When the method returns the room the local user will not have joined it and thus will not
     * receive messages on it until the `ChatRoom.join()` method is called.
     *
     * @param roomName the name of the `ChatRoom` to create.
     * @param roomProperties properties specifying how the room should be created; `null` for no properties
     * just like an empty `Map`
     * @return the newly created `ChatRoom` named `roomName`.
     * @throws OperationFailedException if the room couldn't be created for some reason (e.g. room already exists; user
     * already joined to an existent room or user has no permissions to create a chat room).
     * @throws OperationNotSupportedException if chat room creation is not supported by this server
     */
    @Throws(OperationFailedException::class, OperationNotSupportedException::class, XmppStringprepException::class)
    fun createChatRoom(roomName: String?, roomProperties: Map<String?, Any?>?): ChatRoom?

    /**
     * Returns a reference to a chatRoom named `roomName` or null if no room with the given
     * name exist on the server.
     *
     * @param roomName the name of the `ChatRoom` that we're looking for.
     * @return the `ChatRoom` named `roomName` if it exists, null otherwise.
     * @throws OperationFailedException if an error occurs while trying to discover the room on the server.
     * @throws OperationNotSupportedException if the server does not support multi-user chat
     */
    @Throws(OperationFailedException::class, OperationNotSupportedException::class, XmppStringprepException::class)
    fun findRoom(roomName: String?): ChatRoom?

    /**
     * @param entityBareJid ChatRoom EntityBareJid
     * @return ChatRoom
     */
    fun findRoom(entityBareJid: EntityBareJid?): ChatRoom?

    /**
     * Informs the sender of an invitation that we decline their invitation.
     *
     * @param invitation the invitation we are rejecting.
     * @param rejectReason the reason to reject the invitation (optional)
     */
    @Throws(OperationFailedException::class)
    fun rejectInvitation(invitation: ChatRoomInvitation?, rejectReason: String?)

    /**
     * Adds a listener to invitation notifications. The listener will be fired anytime an invitation is received.
     *
     * @param listener an invitation listener.
     */
    fun addInvitationListener(listener: ChatRoomInvitationListener)

    /**
     * Removes `listener` from the list of invitation listeners registered to receive invitation events.
     *
     * @param listener the invitation listener to remove.
     */
    fun removeInvitationListener(listener: ChatRoomInvitationListener)

    /**
     * Adds a listener to invitation notifications. The listener will be fired anytime an invitation is received.
     *
     * @param listener an invitation listener.
     */
    fun addInvitationRejectionListener(listener: ChatRoomInvitationRejectionListener)

    /**
     * Removes the given listener from the list of invitation listeners registered to receive events
     * every time an invitation has been rejected.
     *
     * @param listener the invitation listener to remove.
     */
    fun removeInvitationRejectionListener(listener: ChatRoomInvitationRejectionListener)

    /**
     * Returns true if `contact` supports multi-user chat sessions.
     *
     * @param contact reference to the contact whose support for chat rooms we are currently querying.
     * @return a boolean indicating whether `contact` supports chat rooms.
     */
    fun isMultiChatSupportedByContact(contact: Contact?): Boolean

    /**
     * Checks if the contact Jid is associated with private messaging contact or not.
     *
     * @return `true` if the contact Jid not null and is associated with
     * private messaging contact and `false` if not.
     */
    fun isPrivateMessagingContact(contactJid: Jid?): Boolean

    /**
     * Adds a listener that will be notified of changes in our participation in a chat room such as
     * us being kicked, joined, left.
     *
     * @param listener a local user participation listener.
     */
    fun addPresenceListener(listener: LocalUserChatRoomPresenceListener)

    /**
     * Removes a listener that was being notified of changes in our participation in a room such as
     * us being kicked, joined, left.
     *
     * @param listener a local user participation listener.
     */
    fun removePresenceListener(listener: LocalUserChatRoomPresenceListener)
}