/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * This interface represents chat room participants. Instances are retrieved through implementations
 * of the `ChatRoom` interface and offer methods that allow querying member properties, such
 * as, moderation permissions, associated chat room and other.
 *
 * @author Emil Ivov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
interface ChatRoomMember {
    /**
     * Returns the chat room that this member is participating in.
     *
     * @return the `ChatRoom` instance that this member belongs to.
     */
    fun getChatRoom(): ChatRoom?

    /**
     * Returns the protocol provider instance that this member has originated in.
     *
     * @return the `ProtocolProviderService` instance that created this member and its
     * containing cht room
     */
    fun getProtocolProvider(): ProtocolProviderService?

    /**
     * Returns the contact identifier representing this contact. In protocols like IRC this method
     * would return the same as getNickName() but in others like Jabber, this method would return
     * a full contact id uri.
     *
     * @return a String (contact address), uniquely representing the contact over the service the
     * service being used by the associated protocol provider instance/
     */
    fun getContactAddress(): String

    /**
     * Returns the name of this member as it is known in its containing chatRoom (aka a nickname).
     * The name returned by this method, may sometimes match the string returned by getContactID()
     * which is actually the address of a contact in the realm of the corresponding protocol.
     *
     * @return the name of this member as it is known in the containing chat room (aka a nickname).
     */
    fun getNickName(): String?

    /**
     * Returns the avatar of this member, that can be used when including it in user interface.
     *
     * @return an avatar (e.g. user photo) of this member.
     */
    fun getAvatar(): ByteArray?

    /**
     * Returns the protocol contact corresponding to this member in our contact list. The contact
     * returned here could be used by the user interface to check if this member is contained in
     * our contact list and in function of this to show additional information add additional functionality.
     *
     * @return the protocol contact corresponding to this member in our contact list.
     */
    fun getContact(): Contact?

    /**
     * Returns the role of this chat room member in its containing room.
     *
     * @return a `ChatRoomMemberRole` instance indicating the role the this member in its
     * containing chat room.
     */
    fun getRole(): ChatRoomMemberRole?

    /**
     * Sets the role of this chat room member in its containing room.
     *
     * @param role `ChatRoomMemberRole` instance indicating the role to set for this member in its
     * containing chat room.
     */
    fun setRole(role: ChatRoomMemberRole?)

    /**
     * Returns the status of the chat room member as per the last status update we've received for
     * it. Note that this method is not to perform any network operations and will simply return
     * the status received in the last status update message.
     *
     * @return the PresenceStatus that we've received in the last status update pertaining to this contact.
     */
    fun getPresenceStatus(): PresenceStatus?

    /**
     * @return the display name of the chat room member.
     */
    val displayName: String?
}