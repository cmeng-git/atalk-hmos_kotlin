/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.ChatRoomMember
import net.java.sip.communicator.service.protocol.ChatRoomMemberRole
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresence
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.ChatRoomMemberPresenceChangeEvent
import org.jivesoftware.smack.packet.Presence
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart

/**
 * A Jabber implementation of the chat room member.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class ChatRoomMemberJabberImpl(
        /**
         * The chat room that we are a member of.
         */
        private val chatRoom: ChatRoomJabberImpl,
        /**
         * The nick name that this member is using inside its containing chat room.
         */
        private var nickName: Resourcepart?,
        /**
         * The jabber id of the member (will only be visible to members with necessary permissions)
         * can either be BareJid or NickName
         */
        private val jabberJid: Jid?) : ChatRoomMember {
    /**
     * The role that this member has in its member room.
     */
    private var mRole: ChatRoomMemberRole? = null

    /*
     * The mContact from our server stored mContact list corresponding to this member.
     */
    private var mContact: Contact? = null

    /**
     * The avatar of this chat room member.
     */
    private var avatar: ByteArray? = null

    private var presenceOpSet: OperationSetPersistentPresenceJabberImpl? = null

    /**
     * Creates a jabber chat room member with the specified containing chat room parent.
     *
     * chatRoom the room that this `ChatRoomMemberJabberImpl` is a member of.
     * nickName the nick name that the member is using to participate in the chat room
     * jabberJid the jabber id, if available, of the member or null otherwise.
     */
    init {
        presenceOpSet = chatRoom.getParentProvider().getOperationSet(OperationSetPersistentPresence::class.java) as OperationSetPersistentPresenceJabberImpl?

        // jabberID may be null e.g. remote anonymous conference, all jids are null
        if (jabberJid != null) {
            // If we found the mContact we set also its avatar.
            mContact = presenceOpSet!!.findContactByJid(jabberJid)
            if (mContact != null) {
                avatar = mContact!!.image
            }
        }
        // just query the server muc member for role, the value is set if present
        getRole()
    }

    /**
     * Returns the chat room that this member is participating in.
     *
     * @return the `ChatRoom` instance that this member belongs to.
     */
    override fun getChatRoom(): ChatRoom {
        return chatRoom
    }

    /**
     * Returns the jabber id of the member; can either be BareJid or reserved nick.
     *
     * @return the jabber id.
     */
    fun getJabberId(): Jid? {
        return jabberJid
    }

    /**
     * Returns the mContact identifier representing this mContact.
     *
     * @return a String (mContact address), uniquely representing the mContact over the service the
     * service being used by the associated protocol provider instance
     */
    override fun getContactAddress(): String {
        return jabberJid?.toString() ?: getNickName()!!
    }

    /**
     * Returns the name of this member as it is known in its containing chatRoom (aka a nickname).
     *
     * @return the name of this member as it is known in the containing chat room (aka a nickname).
     */
    override fun getNickName(): String? {
        return if (nickName == null) null else nickName.toString()
    }

    fun getNickAsResourcepart(): Resourcepart? {
        return nickName
    }

    /**
     * Update the name of this participant
     *
     * @param newNick the newNick of the participant
     */
    fun setNick(newNick: Resourcepart?) {
        require((newNick == null || newNick.length != 0)) { "a room member nickname could not be null" }
        nickName = newNick
    }

    /**
     * Returns the protocol provider instance that this member has originated in.
     *
     * @return the `ProtocolProviderService` instance that created this member and its
     * containing cht room
     */
    override fun getProtocolProvider(): ProtocolProviderService {
        return chatRoom.getParentProvider()
    }

    /**
     * Returns the role of this chat room member in its containing room.
     *
     * @return a `ChatRoomMemberRole` instance indicating the role the this member in its
     * containing chat room.
     */
    override fun getRole(): ChatRoomMemberRole? {
        if (mRole == null && nickName != null) {
            val memberJid = JidCreate.entityFullFrom(chatRoom.getIdentifier(), nickName)
            val o = chatRoom.getMultiUserChat().getOccupant(memberJid)
            mRole = if (o == null) {
                return ChatRoomMemberRole.GUEST
            } else {
                ChatRoomJabberImpl.smackRoleToScRole(o.role, o.affiliation)
            }
        }
        return mRole
    }

    /**
     * Returns the current role without trying to query it in the stack. Mostly used for event
     * creating on member role change.
     *
     * @return the current role of this member.
     */
    fun getCurrentRole(): ChatRoomMemberRole? {
        return mRole
    }

    /**
     * Sets the role of this member.
     *
     * @param role the role to set
     */
    override fun setRole(role: ChatRoomMemberRole?) {
        mRole = role
    }

    /**
     * Returns the avatar of this member, that can be used when including it in user interface.
     *
     * @return an avatar (e.g. user photo) of this member.
     */
    override fun getAvatar(): ByteArray? {
        return avatar
    }

    /**
     * Sets the avatar for this member.
     *
     * @param avatar the avatar to set.
     */
    fun setAvatar(avatar: ByteArray?) {
        this.avatar = avatar
    }

    /**
     * Returns the protocol mContact corresponding to this member in our mContact list. The mContact
     * returned here could be used by the user interface to check if this member is contained in our
     * mContact list and in function of this to show additional information add additional functionality.
     * Note: Use nick to retrieve mContact if null to take care the old history messages;
     *
     * For remote conference chatRoom members, aTalk does not have local stored contacts, so jabberJid can be null .
     *
     * @return the protocol mContact corresponding to this member in our mContact list.
     */
    override fun getContact(): Contact? {
        // old history muc message has mContact field = null (not stored);
        if (mContact == null && presenceOpSet != null && jabberJid != null) {
            mContact = presenceOpSet!!.findContactByJid(jabberJid)
        }
        return mContact
    }

    /**
     * Sets the given mContact to this member.
     *
     * @param contact the mContact to set.
     */
    fun setContact(contact: Contact?) {
        mContact = contact
    }

    override fun getPresenceStatus(): PresenceStatus {
        return mContact!!.presenceStatus
    }

    /**
     * The display name of this [ChatRoomMember].
     */
    override var displayName: String? = null

    /**
     * The email that this member is using inside its containing chat room.
     */
    var email: String? = null

    /**
     * The URL of the avatar of this member.
     */
    var avatarUrl: String? = null

    /**
     * The statistics id of this member.
     */
    var statisticsID: String? = null

    /**
     * Store the last [Presence] which was used to cause a
     * [ChatRoomMemberPresenceChangeEvent.MEMBER_UPDATED] event
     */
    var lastPresence: Presence? = null
}