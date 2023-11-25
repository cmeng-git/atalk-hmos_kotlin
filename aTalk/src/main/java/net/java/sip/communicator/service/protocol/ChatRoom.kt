/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.*
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smackx.muc.MultiUserChat
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.Jid
import org.jxmpp.jid.parts.Resourcepart

/**
 * Represents a chat channel/room/rendezvous point/ where multiple chat users could rally and
 * communicate in a many-to-many fashion.
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 * @author Valentin Martinet
 * @author Eng Chong Meng
 */
interface ChatRoom {
    /**
     * Returns the name of this `ChatRoom`.
     *
     * @return a `String` containing the name of this `ChatRoom`.
     */
    fun getName(): String

    /**
     * Returns the EntityBareJid of this `ChatRoom`.
     *
     * @return a `EntityBareJid` containing the identifier of this `ChatRoom`.
     */
    fun getIdentifier(): EntityBareJid

    /**
     * Returns the muc for the chat room instance.
     *
     * @return the muc in the protocol stack.
     */
    fun getMultiUserChat(): MultiUserChat

    /**
     * Joins this chat room with the nickname of the local user so that the user would start
     * receiving events and messages for it.
     *
     * @throws OperationFailedException with the corresponding code if an error occurs while joining the room.
     */
    @Throws(OperationFailedException::class)
    fun join(): Boolean

    /**
     * Joins this chat room so that the user would start receiving events and messages for it. The
     * method uses the nickname of the local user and the specified password in order to enter the chatRoom.
     *
     * @param password the password to use when authenticating on the chatRoom.
     * @throws OperationFailedException with the corresponding code if an error occurs while joining the room.
     */
    @Throws(OperationFailedException::class)
    fun join(password: ByteArray): Boolean

    /**
     * Joins this chat room with the specified nickname so that the user would start receiving
     * events and messages for it. If the chatRoom already contains a user with this nickname, the
     * method would throw an OperationFailedException with code IDENTIFICATION_CONFLICT.
     *
     * @param nickname the nickname to use.
     * @throws OperationFailedException with the corresponding code if an error occurs while joining the room.
     */
    @Throws(OperationFailedException::class)
    fun joinAs(nickname: String?): Boolean

    /**
     * Joins this chat room with the specified nickname and password so that the user would start
     * receiving events and messages for it. If the chatRoom already contains a user with this
     * nickname, the method would throw an OperationFailedException with code IDENTIFICATION_CONFLICT.
     *
     * @param nickname the nickname to use.
     * @param password a password necessary to authenticate when joining the room.
     * @throws OperationFailedException with the corresponding code if an error occurs while joining the room.
     */
    @Throws(OperationFailedException::class)
    fun joinAs(nickname: String?, password: ByteArray?): Boolean

    /**
     * Returns true if the local user is currently in the multi user chat (after calling one of the
     * [.join] methods).
     *
     * @return true if currently we're currently in this chat room and false otherwise.
     */
    fun isJoined(): Boolean

    /**
     * Leave this chat room. Once this method is called, the user won't be listed as a member of the
     * chat room any more and no further chat events will be delivered. Depending on the underlying
     * protocol and implementation leave() might cause the room to be destroyed if it has been
     * created by the local user.
     */
    fun leave()

    /**
     * Returns the last known room subject/theme or `null` if the user hasn't joined the room
     * or the room does not have a subject yet.
     *
     * To be notified every time the room's subject change you should add a
     * `ChatRoomChangeListener` to this room.
     * [.addPropertyChangeListener]
     *
     * To change the room's subject use [.setSubject].
     *
     * @return the room subject or `null` if the user hasn't joined the room or the room does
     * not have a subject yet.
     */
    fun getSubject(): String?

    /**
     * Sets the subject of this chat room. If the user does not have the right to change the room
     * subject, or the protocol does not support this, or the operation fails for some other reason,
     * the method throws an `OperationFailedException` with the corresponding code.
     *
     * @param subject the new subject that we'd like this room to have
     * @throws OperationFailedException if the user doesn't have the right to change this property.
     */
    @Throws(OperationFailedException::class)
    fun setSubject(subject: String?)

    /**
     * Returns the local user's nickname in the context of this chat room or `null` if not
     * currently joined.
     *
     * @return the nickname currently being used by the local user in the context of the local chat room.
     */
    fun getUserNickname(): Resourcepart

    /**
     * Returns the local user's role in the context of this chat room if currently joined.
     * Else retrieve from the value in DB that was previously saved, or `null` if none
     *
     * @return the role currently being used by the local user in the context of the chat room.
     */
    fun getUserRole(): ChatRoomMemberRole?

    /**
     * Changes the the local user's nickname in the context of this chatRoom.
     *
     * @param role the new role to set for the local user.
     * @throws OperationFailedException if an error occurs.
     */
    @Throws(OperationFailedException::class)
    fun setLocalUserRole(role: ChatRoomMemberRole)

    /**
     * Changes the the local user's nickname in the context of this chatRoom.
     *
     * @param nickname the new nickname within the room.
     * @throws OperationFailedException if the new nickname already exist in this room
     */
    @Throws(OperationFailedException::class)
    fun setUserNickname(nickname: String?)

    /**
     * Adds a listener that will be notified of changes in our participation in the room such as us
     * being kicked, join, left...
     *
     * @param listener a member participation listener.
     */
    fun addMemberPresenceListener(listener: ChatRoomMemberPresenceListener)

    /**
     * Removes a listener that was being notified of changes in the participation of other chat room
     * participants such as users being kicked, join, left.
     *
     * @param listener a member participation listener.
     */
    fun removeMemberPresenceListener(listener: ChatRoomMemberPresenceListener)

    /**
     * Adds a listener that will be notified of changes in our role in the room such as us being
     * granted operator.
     *
     * @param listener a local user role listener.
     */
    fun addLocalUserRoleListener(listener: ChatRoomLocalUserRoleListener)

    /**
     * Removes a listener that was being notified of changes in our role in this chat room such as
     * us being granted operator.
     *
     * @param listener a local user role listener.
     */
    fun removeLocalUserRoleListener(listener: ChatRoomLocalUserRoleListener)

    /**
     * Adds a listener that will be notified of changes of a member role in the room such as being
     * granted operator.
     *
     * @param listener a member role listener.
     */
    fun addMemberRoleListener(listener: ChatRoomMemberRoleListener)

    /**
     * Removes a listener that was being notified of changes of a member role in this chat room such
     * as us being granted operator.
     *
     * @param listener a member role listener.
     */
    fun removeMemberRoleListener(listener: ChatRoomMemberRoleListener)

    /**
     * Adds a listener that will be notified of changes in the property of the room such as the
     * subject being change or the room state being changed.
     *
     * @param listener a property change listener.
     */
    fun addPropertyChangeListener(listener: ChatRoomPropertyChangeListener)

    /**
     * Removes a listener that was being notified of changes in the property of the chat room such
     * as the subject being change or the room state being changed.
     *
     * @param listener a property change listener.
     */
    fun removePropertyChangeListener(listener: ChatRoomPropertyChangeListener)

    /**
     * Adds a listener that will be notified of changes in the property of a room member such as the
     * nickname being changed.
     *
     * @param listener a room member property change listener.
     */
    fun addMemberPropertyChangeListener(listener: ChatRoomMemberPropertyChangeListener)

    /**
     * Removes a listener that was being notified of changes in the property of a chat room member
     * such as the nickname being changed.
     *
     * @param listener a room member property change listener.
     */
    fun removeMemberPropertyChangeListener(listener: ChatRoomMemberPropertyChangeListener)

    /**
     * Invites another user to this room.
     *
     * If the room is password-protected, the invitee will receive a password to use to join the
     * room. If the room is members-only, the the invitee may be added to the member list.
     *
     * @param userJid the address of the user to invite to the room.(one may also invite users
     * not on their contact list).
     * @param reason a reason, subject, or welcome message that would tell the the user why they are being invited.
     */
    @Throws(SmackException.NotConnectedException::class, InterruptedException::class)
    fun invite(userJid: EntityBareJid, reason: String?)

    /**
     * Returns a `List` of `ChatRoomMember`s corresponding to all members currently
     * participating in this room.
     *
     * @return a `List` of `ChatRoomMember` instances corresponding to all room members.
     */
    fun getMembers(): List<ChatRoomMember>

    /**
     * Returns the number of participants that are currently in this chat room.
     *
     * @return the number of `Contact`s, currently participating in this room.
     */
    fun getMembersCount(): Int

    /**
     * Registers `listener` so that it would receive events every time a new message is
     * received on this chat room.
     *
     * @param listener a `MessageListener` that would be notified every time a new message is received
     * on this chat room.
     */
    fun addMessageListener(listener: ChatRoomMessageListener)

    /**
     * Removes `listener` so that it won't receive any further message events from this room.
     *
     * @param listener the `MessageListener` to remove from this room
     */
    fun removeMessageListener(listener: ChatRoomMessageListener)

    /**
     * Create a IMessage instance for sending a simple text messages with default (text/plain)
     * content type and encoding.
     *
     * @param messageText the string content of the message.
     * @return IMessage the newly created message
     */
    fun createMessage(messageText: String): IMessage

    /**
     * Create a IMessage instance for sending arbitrary MIME-encoding content.
     *
     * @param content content value
     * @param encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     * @param subject a `String` subject or `null` for now subject.
     * @return the newly created message.
     */
    fun createMessage(content: String, encType: Int, subject: String?): IMessage

    /**
     * Sends the `message` to this chat room.
     *
     * @param message the `IMessage` to send.
     * @throws OperationFailedException if sending the message fails for some reason.
     */
    @Throws(OperationFailedException::class)
    fun sendMessage(message: IMessage)
    fun sendMessage(message: IMessage, omemoManager: OmemoManager)

    /**
     * Returns a reference to the provider that created this room.
     *
     * @return a reference to the `ProtocolProviderService` instance that created this room.
     */
    fun getParentProvider(): ProtocolProviderService

    /**
     * Returns an Iterator over a set of ban masks for this chat room. The ban mask defines a group
     * of users that will be banned. The ban list is a list of all such ban masks defined for this chat room.
     *
     * @return an Iterator over a set of ban masks for this chat room
     * @throws OperationFailedException if an error occurred while performing the request to the server or
     * you don't have enough privileges to get this information
     */
    @Throws(OperationFailedException::class)
    fun getBanList(): Iterator<ChatRoomMember>

    /**
     * Bans a user from the room. An administrator or owner of the room can ban users from a room. A
     * banned user will no longer be able to join the room unless the ban has been removed. If the
     * banned user was present in the room then he/she will be removed from the room and notified
     * that he/she was banned along with the reason (if provided) and the user who initiated the ban.
     *
     * @param chatRoomMember the `ChatRoomMember` to be banned.
     * @param reason the reason why the user was banned.
     * @throws OperationFailedException if an error occurs while banning a user. In particular, an error can occur
     * if a moderator or a user with an affiliation of "owner" or "admin" was tried to be banned
     * or if the user that is banning have not enough permissions to ban.
     */
    @Throws(OperationFailedException::class)
    fun banParticipant(chatRoomMember: ChatRoomMember, reason: String)

    /**
     * Kicks a visitor or participant from the room.
     *
     * @param chatRoomMember the `ChatRoomMember` to kick from the room
     * @param reason the reason why the participant is being kicked from the room
     * @throws OperationFailedException if an error occurs while kicking the participant. In particular, an error can occur
     * if a moderator or a user with an affiliation of "owner" or "administrator" was
     * intended to be kicked; or if the participant that intended to kick another
     * participant does not have kicking privileges;
     */
    @Throws(OperationFailedException::class)
    fun kickParticipant(chatRoomMember: ChatRoomMember, reason: String)

    /**
     * Returns the `ChatRoomConfigurationForm` containing all configuration properties for
     * this chat room. If the user doesn't have permissions to see and change chat room
     * configuration an `OperationFailedException` is thrown.
     *
     * @return the `ChatRoomConfigurationForm` containing all configuration properties for
     * this chat room
     * @throws OperationFailedException if the user doesn't have permissions to see and change chat room configuration
     */
    @Throws(OperationFailedException::class, InterruptedException::class)
    fun getConfigurationForm(): ChatRoomConfigurationForm?

    /**
     * Returns `true` if this chat room is a system room and `false` otherwise.
     *
     * @return `true` if this chat room is a system room and `false` otherwise.
     */
    fun isSystem(): Boolean

    /**
     * Determines whether this chat room should be stored in the configuration file or not. If the
     * chat room is persistent it still will be shown after a restart in the chat room list. A
     * non-persistent chat room will be only in the chat room list until the the program is running.
     *
     * @return true if this chat room is persistent, false otherwise
     */
    fun isPersistent(): Boolean

    /**
     * Finds private messaging contact by nickname. If the contact doesn't exists a new volatile
     * contact is created.
     *
     * @param nickname the nickname of the contact.
     * @return the contact instance.
     */
    fun getPrivateContactByNickname(nickname: String): Contact?

    /**
     * Grants administrator privileges to another user. Room owners may grant administrator
     * privileges to a member or unaffiliated user. An administrator is allowed to perform
     * administrative functions such as banning users and edit moderator list.
     *
     * @param address the user address of the user to grant administrator privileges (e.g. "user@host.org").
     */
    fun grantAdmin(address: String)

    /**
     * Grants membership to a user. Only administrators are able to grant membership. A user that
     * becomes a room member will be able to enter a room of type Members-Only (i.e. a room that a
     * user cannot enter without being on the member list).
     *
     * @param address the user address of the user to grant membership privileges (e.g. "user@host.org").
     */
    fun grantMembership(address: String)

    /**
     * Grants moderator privileges to a participant or visitor. Room administrators may grant
     * moderator privileges. A moderator is allowed to kick users, grant and revoke voice, invite
     * other users, modify room's subject plus all the partcipants privileges.
     *
     * @param nickname the nickname of the occupant to grant moderator privileges.
     */
    fun grantModerator(nickname: String)

    /**
     * Grants ownership privileges to another user. Room owners may grant ownership privileges. Some
     * room implementations will not allow to grant ownership privileges to other users. An owner is
     * allowed to change defining room features as well as perform all administrative functions.
     *
     * @param address the user address of the user to grant ownership privileges (e.g. "user@host.org").
     */
    fun grantOwnership(address: String)

    /**
     * Grants voice to a visitor in the room. In a moderated room, a moderator may want to manage
     * who does and does not have "voice" in the room. To have voice means that a room occupant is
     * able to send messages to the room occupants.
     *
     * @param nickname the nickname of the visitor to grant voice in the room (e.g. "john").
     */
    fun grantVoice(nickname: String)

    /**
     * Revokes administrator privileges from a user. The occupant that loses administrator
     * privileges will become a member. Room owners may revoke administrator privileges from a
     * member or unaffiliated user.
     *
     * @param address the user address of the user to grant administrator privileges (e.g. "user@host.org").
     */
    fun revokeAdmin(address: String)

    /**
     * Revokes a user's membership. Only administrators are able to revoke membership. A user that
     * becomes a room member will be able to enter a room of type Members-Only (i.e. a room that a
     * user cannot enter without being on the member list). If the user is in the room and the room
     * is of type members-only then the user will be removed from the room.
     *
     * @param address the user address of the user to revoke membership (e.g. "user@host.org").
     */
    fun revokeMembership(address: String)

    /**
     * Revokes moderator privileges from another user. The occupant that loses moderator privileges
     * will become a participant. Room administrators may revoke moderator privileges only to
     * occupants whose affiliation is member or none. This means that an administrator is not
     * allowed to revoke moderator privileges from other room administrators or owners.
     *
     * @param nickname the nickname of the occupant to revoke moderator privileges.
     */
    fun revokeModerator(nickname: String)

    /**
     * Revokes ownership privileges from another user. The occupant that loses ownership privileges
     * will become an administrator. Room owners may revoke ownership privileges. Some room
     * implementations will not allow to grant ownership privileges to other users.
     *
     * @param address the user address of the user to revoke ownership (e.g. "user@host.org").
     */
    fun revokeOwnership(address: String)

    /**
     * Revokes voice from a participant in the room. In a moderated room, a moderator may want to
     * revoke an occupant's privileges to speak. To have voice means that a room occupant is able to
     * send messages to the room occupants.
     *
     * @param nickname the nickname of the participant to revoke voice (e.g. "john").
     */
    fun revokeVoice(nickname: String)

    /**
     * Publishes a `ConferenceDescription` to the chat room.
     *
     * @param cd the description to publish
     * @param name the name of the conference
     * @return the published conference
     */
    fun publishConference(cd: ConferenceDescription?, name: String?): ConferenceDescription?

    /**
     * Updates the presence status of private messaging contact.
     *
     * @param chatRoomMember the chatRoom member.
     */
    fun updatePrivateContactPresenceStatus(chatRoomMember: ChatRoomMember)

    /**
     * Updates the presence status of private messaging contact.
     *
     * @param contact the contact.
     */
    fun updatePrivateContactPresenceStatus(contact: Contact?)

    /**
     * Adds a listener that will be notified when a member of this chat room has published a
     * `ConferenceDescription` to the room.
     *
     * @param listener the listener to add.
     */
    fun addConferencePublishedListener(listener: ChatRoomConferencePublishedListener)

    /**
     * Removes a listener that was being notified when a member of this chat room had published a
     * `ConferenceDescription` to the room.
     *
     * @param listener the listener to remove.
     */
    fun removeConferencePublishedListener(listener: ChatRoomConferencePublishedListener)

    /**
     * Returns cached `ConferenceDescription` instances.
     *
     * @return the cached `ConferenceDescription` instances.
     */
    fun getCachedConferenceDescriptions(): Map<String, ConferenceDescription?>

    /**
     * Returns the number of cached `ConferenceDescription` instances.
     *
     * @return the number of cached `ConferenceDescription` instances.
     */
    fun getCachedConferenceDescriptionSize(): Int

    /**
     * Destroys the chat room.
     *
     * @param reason the reason for destroying.
     * @param alternateAddress the alternate address
     * @return `true` if the room is destroyed.
     */
    @Throws(XMPPException::class)
    fun destroy(reason: String?, alternateAddress: EntityBareJid?): Boolean

    /**
     * Returns the ids of the users that has the member role in the room. When the room is member
     * only, this are the users allowed to join.
     *
     * @return the ids of the users that has the member role in the room.
     */
    fun getMembersWhiteList(): MutableList<Jid>

    /**
     * Changes the list of users that has role member for this room. When the room is member only,
     * this are the users allowed to join.
     *
     * @param members the ids of user to have member role.
     */
    fun setMembersWhiteList(members: List<Jid>)

    companion object {
        /**
         * The constant defined for chatRoom configuration attributes property.
         */
        const val CHATROOM = "chatRoom"
        const val CHATROOM_NAME = "chatRoomName"
        const val USER_NICK_NAME = "userNickName"
        const val USER_ROLE = "userRole"

        /**
         * Proceed to create room if isPrivate
         */
        const val IS_PRIVATE = "isPrivate"

        /**
         * indicate if the room is already on the server - persistent
         */
        const val ON_SERVER_ROOM = "onServerRoom"
        const val CHATROOM_LAST_STATUS = "lastChatRoomStatus"
    }
}