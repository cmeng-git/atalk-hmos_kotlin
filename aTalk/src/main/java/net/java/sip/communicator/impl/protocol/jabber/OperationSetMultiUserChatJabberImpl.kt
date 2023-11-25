/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.protocol.event.*
import org.atalk.hmos.gui.chat.ChatMessage
import org.atalk.hmos.gui.util.XhtmlUtil.getXhtmlExtension
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.SmackException.*
import org.jivesoftware.smack.StanzaListener
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smack.filter.*
import org.jivesoftware.smack.packet.*
import org.jivesoftware.smack.parsing.SmackParsingException
import org.jivesoftware.smack.util.PacketParserUtils
import org.jivesoftware.smack.util.StringUtils
import org.jivesoftware.smack.xml.XmlPullParserException
import org.jivesoftware.smackx.captcha.packet.CaptchaExtension
import org.jivesoftware.smackx.delay.packet.DelayInformation
import org.jivesoftware.smackx.muc.InvitationListener
import org.jivesoftware.smackx.muc.InvitationRejectionListener
import org.jivesoftware.smackx.muc.MultiUserChat
import org.jivesoftware.smackx.muc.MultiUserChat.MucCreateConfigFormHandle
import org.jivesoftware.smackx.muc.MultiUserChatException.MissingMucCreationAcknowledgeException
import org.jivesoftware.smackx.muc.MultiUserChatException.NotAMucServiceException
import org.jivesoftware.smackx.muc.MultiUserChatManager
import org.jivesoftware.smackx.muc.packet.MUCUser.Decline
import org.jivesoftware.smackx.muc.packet.MUCUser.Invite
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.OmemoMessage.Received
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException
import org.jivesoftware.smackx.omemo.exceptions.CryptoFailedException
import org.jivesoftware.smackx.omemo.exceptions.NoRawSessionException
import org.jivesoftware.smackx.omemo.listener.OmemoMucMessageListener
import org.jivesoftware.smackx.omemo.provider.OmemoVAxolotlProvider
import org.jivesoftware.smackx.xdata.form.FillableForm
import org.jxmpp.jid.*
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Localpart
import org.jxmpp.jid.parts.Resourcepart
import org.jxmpp.stringprep.XmppStringprepException
import org.jxmpp.util.XmppStringUtils
import timber.log.Timber
import java.io.IOException
import java.util.*

/**
 * A jabber implementation of the multiUser chat operation set.
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class OperationSetMultiUserChatJabberImpl internal constructor(
        /**
         * The currently valid Jabber protocol provider service implementation.
         */
        private val mPPS: ProtocolProviderServiceJabberImpl,
) : AbstractOperationSetMultiUserChat(), SubscriptionListener, OmemoMucMessageListener {
    private var mConnection: XMPPConnection? = null
    private var mOmemoManager: OmemoManager? = null
    private val omemoVAxolotlProvider = OmemoVAxolotlProvider()

    /**
     * A reference of the MultiUserChatManager
     */
    private var mMucMgr: MultiUserChatManager? = null
    private var mInvitationListener: SmackInvitationListener? = null

    /**
     * A list of the rooms that are currently open by this account. Note that we have not
     * necessarily joined these rooms, we might have simply been searching through them.
     */
    private val chatRoomCache = Hashtable<BareJid?, ChatRoomJabberImpl?>()

    /**
     * A reference to the persistent presence operation set that we use to match incoming messages
     * to `Contact`s and vice versa.
     */
    private val opSetPersPresence: OperationSetPersistentPresenceJabberImpl?

    // setup message listener to receive captcha challenge message and error message from room
    private val MUC_ROOM_FILTER = AndFilter(FromTypeFilter.ENTITY_BARE_JID,
        OrFilter(MessageTypeFilter.NORMAL, MessageTypeFilter.ERROR))

    /**
     * Add SmackInvitationRejectionListener to `MultiUserChat` instance which will dispatch all rejection events.
     *
     * muc the smack MultiUserChat instance that we're going to wrap our chat room around.
     * chatRoom the associated chat room instance
     */
    fun addSmackInvitationRejectionListener(muc: MultiUserChat, chatRoom: ChatRoom?) {
        muc.addInvitationRejectionListener(SmackInvitationRejectionListener(chatRoom))
    }

    /**
     * Creates a room with the named `roomName` and according to the specified
     * `roomProperties` on the server that this protocol provider is currently connected to.
     *
     * @param roomName the name of the `ChatRoom` to create.
     * @param roomProperties properties specifying how the room should be created.
     *
     * @return ChatRoom the chat room that we've just created.
     * @throws OperationFailedException if the room couldn't be created for some reason (e.g. room already exists; user
     * already joined to an existent room or user has no permissions to create a chat room).
     * @throws OperationNotSupportedException if chat room creation is not supported by this server
     */
    @Throws(OperationFailedException::class, OperationNotSupportedException::class)
    override fun createChatRoom(roomName: String?, roomProperties: Map<String?, Any?>?): ChatRoom? {
        // first make sure we are connected and the server supports multiChat
        var iRoomName = roomName
        assertSupportedAndConnected()
        var chatRoom: ChatRoom? = null
        if (iRoomName == null) {
            // rooms using google servers needs special name in the form private-chat-UUID@groupchat.google.com
            iRoomName = if (mConnection != null && mConnection!!.host.lowercase().contains("google")) {
                "private-chat-" + UUID.randomUUID() + "@groupchat.google.com"
            }
            else "chatroom-" + StringUtils.randomString(4)
        }
        else {
            // findRoom(roomName) => auto create room without member-only; this does not support OMEMO encryption
            // Do not proceed to create the room if the room is already listed in server room list
            val onServerRoom = roomProperties != null && java.lang.Boolean.TRUE == roomProperties[ChatRoom.ON_SERVER_ROOM]
            val entityBareJid = getCanonicalRoomName(iRoomName)
            if (onServerRoom) {
                return findRoom(entityBareJid)
            }

            // proceed to create the room is none is found
            chatRoom = chatRoomCache[entityBareJid]

            // check room on server using getRoomInfo() if exist, throw exception otherwise - slow response from server
//            if ((chatRoom == null) && (mMucMgr != null)) {
//                try {
//                    // some server takes ~8sec to response  due to disco#info request (default timer = 5seconds)
//                    mConnection.setReplyTimeout(10000);
//                    RoomInfo info = mMucMgr.getRoomInfo(entityBareJid);
//                    Timber.d("Chat Room Info = Persistent:%s; MemberOnly:%s; PasswordProtected:%s",
//                            info.isPersistent(), info.isMembersOnly(), info.isPasswordProtected());
//
//                    MultiUserChat muc = mMucMgr.getMultiUserChat(entityBareJid);
//                    mConnection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT);
//
//                    return createLocalChatRoomInstance(muc);
//                } catch (NoResponseException | XMPPErrorException | NotConnectedException | InterruptedException e) {
//                    Timber.w("Chat Room not found on server: %s", e.getMessage());
//                    mConnection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT);
//                }
//            }
        }
        if (chatRoom == null && mMucMgr != null) {
            val muc = mMucMgr!!.getMultiUserChat(getCanonicalRoomName(iRoomName))
            chatRoom = createLocalChatRoomInstance(muc)

            // some server takes ~8sec to response  due to disco#info request (default timer = 5seconds)
            mConnection!!.replyTimeout = ProtocolProviderServiceJabberImpl.SMACK_REPLY_EXTENDED_TIMEOUT_10
            var mucFormHandler: MucCreateConfigFormHandle? = null
            try {
                // XMPPError not-authorized - if it is an existing server room on third party server
                // ths has pre-assigned owner; catch exception and ignore
                val nick = Resourcepart.from(XmppStringUtils.parseLocalpart(mPPS.accountID.accountJid))
                mucFormHandler = muc.create(nick)
            } catch (ignore: MissingMucCreationAcknowledgeException) {
                Timber.d("Missing Muc Creation Acknowledge Exception: %s", iRoomName)
            } catch (ex: XMPPException) {
                // throw new OperationFailedException("Failed to create chat room", OperationFailedException.GENERAL_ERROR, ex);
                Timber.e("Failed to assigned owner %s", ex.message)
            } catch (ex: SmackException) {
                Timber.e("Failed to assigned owner %s", ex.message)
            } catch (ex: XmppStringprepException) {
                Timber.e("Failed to assigned owner %s", ex.message)
            } catch (ex: InterruptedException) {
                Timber.e("Failed to assigned owner %s", ex.message)
            }
            mConnection!!.replyTimeout = ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT

            // Proceed only if we have acquired the owner privilege to change room properties
            if (mucFormHandler != null) {
                try {
                    val isPrivate = roomProperties != null && java.lang.Boolean.TRUE == roomProperties[ChatRoom.IS_PRIVATE]
                    if (isPrivate) {
                        /**
                         * @see Form.getFillableForm
                         * @see FillableForm.setAnswer
                         */
                        val initForm = muc.configurationForm
                        val fillableForm = initForm.fillableForm

                        // cmeng - update all the below fields in the default form.
                        val fields = arrayOf("muc#roomconfig_membersonly", "muc#roomconfig_allowinvites", "muc#roomconfig_publicroom")
                        val values = arrayOf(true, true, false)
                        for (i in fields.indices) {
                            try {
                                fillableForm.setAnswer(fields[i], values[i])
                            } catch (ignore: IllegalArgumentException) {
                                // Just ignore and continue for IllegalArgumentException variable
                                Timber.w("Exception in setAnswer for field: %s = %s", fields[i], values[i])
                            }
                        }
                        muc.sendConfigurationForm(fillableForm)
                    }
                    else {
                        mucFormHandler.makeInstant()
                    }
                    // We are creating the room hence the owner of it at least that's what MultiUserChat.create says
                    chatRoom.setLocalUserRole(ChatRoomMemberRole.OWNER)
                } catch (e: XMPPException) {
                    Timber.w("Failed to submit room configuration form: %s", e.message)
                } catch (e: NoResponseException) {
                    Timber.w("Failed to submit room configuration form: %s", e.message)
                } catch (e: NotConnectedException) {
                    Timber.w("Failed to submit room configuration form: %s", e.message)
                } catch (e: InterruptedException) {
                    Timber.w("Failed to submit room configuration form: %s", e.message)
                }
            }
        }
        return chatRoom
    }

    /**
     * Creates a `ChatRoom` from the specified smack `MultiUserChat`.
     *
     * @param muc the smack MultiUserChat instance that we're going to wrap our chat room around.
     *
     * @return ChatRoom the chat room that we've just created.
     */
    private fun createLocalChatRoomInstance(muc: MultiUserChat): ChatRoom {
        synchronized(chatRoomCache) {
            val chatRoom = ChatRoomJabberImpl(muc, mPPS)
            chatRoomCache[muc.room] = chatRoom

            // Add the contained in this class SmackInvitationRejectionListener which will dispatch
            // all rejection events to the ChatRoomInvitationRejectionListener.
            addSmackInvitationRejectionListener(muc, chatRoom)
            return chatRoom
        }
    }

    /**
     * Returns a reference to a chatRoom named `roomName`.
     * If the room doesn't exists in the cache then creates it.
     *
     * @param roomName the name of the `ChatRoom` that we're looking for.
     *
     * @return the `ChatRoom` named `roomName`
     * @throws OperationFailedException if an error occurs while trying to discover the room on the server.
     * @throws OperationNotSupportedException if the server does not support multi user chat
     */
    @Synchronized
    @Throws(OperationFailedException::class, OperationNotSupportedException::class)
    override fun findRoom(roomName: String?): ChatRoom? {
        // make sure we are connected and multiChat is supported.
        assertSupportedAndConnected()
        val canonicalRoomName = getCanonicalRoomName(roomName)
        return findRoom(canonicalRoomName)
    }

    /**
     * Returns a reference to a ChatRoomJabberImpl named `room`.
     * If the room doesn't exists in the cache then creates it.
     * Note: actual create on server only happen when user join the room
     *
     * @param entityBareJid the EntityBareJid of the `ChatRoom` that we're looking for.
     *
     * @return the `ChatRoomJabberImpl` named `room`
     */
    @Synchronized
    override fun findRoom(entityBareJid: EntityBareJid?): ChatRoomJabberImpl? {
        var room = chatRoomCache[entityBareJid]
        if (room != null)
            return room

        if (mMucMgr != null) {
            val muc = mMucMgr!!.getMultiUserChat(entityBareJid)
            room = ChatRoomJabberImpl(muc, mPPS)
            chatRoomCache[entityBareJid] = room
        }
        return room
    }

    /**
     * Returns a list of the chat rooms that we have joined and are currently active in.
     *
     * @return a `List` of the rooms where the user has joined using a given connection.
     */
    override fun getCurrentlyJoinedChatRooms(): List<ChatRoom?> {
        synchronized(chatRoomCache) {
            val joinedRooms: MutableList<ChatRoom?> = LinkedList(chatRoomCache.values)
            val joinedRoomsIter = joinedRooms.iterator()
            while (joinedRoomsIter.hasNext()) {
                if (!joinedRoomsIter.next()!!.isJoined()) joinedRoomsIter.remove()
            }
            return joinedRooms
        }
    }
    //    /**
    //     * Returns a list of the names of all chat rooms that <code>contact</code> is currently a member of.
    //     *
    //     * contact the contact whose current ChatRooms we will be querying.
    //     * @return a list of <code>String</code> indicating the names of the chat rooms that
    //     * <code>contact</code> has joined and is currently active in.
    //     * @throws OperationFailedException if an error occurs while trying to discover the room on the server.
    //     * @throws OperationNotSupportedException if the server does not support multi user chat
    //     */
    //    /* this method is not used */
    //	public List getCurrentlyJoinedChatRooms(Contact contact)
    //			throws OperationFailedException, OperationNotSupportedException
    //	{
    //		assertSupportedAndConnected();
    //		Iterator joinedRoomsIter = MultiUserChat.getJoinedRooms(getXmppConnection(), contact.getAddress());
    //		List joinedRoomsForContact = new LinkedList();
    //
    //		while (joinedRoomsIter.hasNext()) {
    //			MultiUserChat muc = (MultiUserChat) joinedRoomsIter.next();
    //			joinedRoomsForContact.add(muc.getRoom());
    //		}
    //		return joinedRoomsForContact;
    //	}
    /**
     * Returns the `List` of `String`s indicating chat rooms currently available on
     * the server that this protocol provider is connected to.
     *
     * @return a `java.util.List` of the name `String`s for chat rooms that are
     * currently available on the server that this protocol provider is connected to.
     * @throws OperationFailedException if we failed retrieving this list from the server.
     * @throws OperationNotSupportedException if the server does not support multi user chat
     */
    @Throws(OperationFailedException::class, OperationNotSupportedException::class)
    override fun getExistingChatRooms(): List<EntityBareJid?> {
        assertSupportedAndConnected()
        val list: MutableList<EntityBareJid?> = LinkedList()

        // first retrieve all conference service names available on this server
        val serviceNames: List<DomainBareJid>
        if (mMucMgr != null) {
            serviceNames = try {
                // serviceNames = MultiUserChat.getServiceNames(getXmppConnection()).iterator();
                mMucMgr!!.mucServiceDomains
            } catch (ex: XMPPException) {
                throw OperationFailedException("Failed to retrieve Jabber conference service names",
                    OperationFailedException.GENERAL_ERROR, ex)
            } catch (ex: NoResponseException) {
                throw OperationFailedException("Failed to retrieve Jabber conference service names",
                    OperationFailedException.GENERAL_ERROR, ex)
            } catch (ex: NotConnectedException) {
                throw OperationFailedException("Failed to retrieve Jabber conference service names",
                    OperationFailedException.GENERAL_ERROR, ex)
            } catch (ex: InterruptedException) {
                throw OperationFailedException("Failed to retrieve Jabber conference service names",
                    OperationFailedException.GENERAL_ERROR, ex)
            }

            // Now retrieve all hostedRooms available for each service name and
            // add the room EntityBareJid the list of room names we are returning
            for (serviceName in serviceNames) {
                try {
                    val hostedRooms = mMucMgr!!.getRoomsHostedBy(serviceName)
                    list.addAll(hostedRooms.keys)
                } catch (ex: XMPPException) {
                    Timber.e("Failed to retrieve room for %s : %s", serviceName, ex.message)
                } catch (ex: NoResponseException) {
                    Timber.e("Failed to retrieve room for %s : %s", serviceName, ex.message)
                } catch (ex: NotConnectedException) {
                    Timber.e("Failed to retrieve room for %s : %s", serviceName, ex.message)
                } catch (ex: IllegalArgumentException) {
                    Timber.e("Failed to retrieve room for %s : %s", serviceName, ex.message)
                } catch (ex: NotAMucServiceException) {
                    Timber.e("Failed to retrieve room for %s : %s", serviceName, ex.message)
                } catch (ex: InterruptedException) {
                    Timber.e("Failed to retrieve room for %s : %s", serviceName, ex.message)
                }
            }
        }
        return list
    }

    /**
     * Returns true if `contact` supports multiUser chat sessions.
     *
     * @param contact reference to the contact whose support for chat rooms we are currently querying.
     *
     * @return a boolean indicating whether `contact` supports chatRooms.
     */
    override fun isMultiChatSupportedByContact(contact: Contact?): Boolean {
        return (contact!!.protocolProvider.getOperationSet(OperationSetMultiUserChat::class.java)) != null
    }

    /**
     * Checks if the contact Jid is associated with private messaging contact or not.
     *
     * @return `true` if the contact Jid not null and is associated with
     * private messaging contact and `false` if not.
     */
    override fun isPrivateMessagingContact(contactJid: Jid?): Boolean {
        return contactJid != null && opSetPersPresence!!.isPrivateMessagingContact(contactJid)
    }

    /**
     * Informs the sender of an invitation that we decline their invitation.
     *
     * @param invitation the connection to use for sending the rejection.
     * @param rejectReason the reason to reject the given invitation
     */
    @Throws(OperationFailedException::class)
    override fun rejectInvitation(invitation: ChatRoomInvitation?, rejectReason: String?) {
        if (mMucMgr != null) {
            try {
                mMucMgr!!.decline(JidCreate.entityBareFrom(invitation!!.getTargetChatRoom().getIdentifier()),
                    invitation.getInviter().asEntityBareJidIfPossible(), rejectReason)
            } catch (e: NotConnectedException) {
                throw OperationFailedException("Could not reject invitation",
                    OperationFailedException.GENERAL_ERROR, e)
            } catch (e: InterruptedException) {
                throw OperationFailedException("Could not reject invitation",
                    OperationFailedException.GENERAL_ERROR, e)
            } catch (e: XmppStringprepException) {
                throw OperationFailedException("Could not reject invitation",
                    OperationFailedException.GENERAL_ERROR, e)
            }
        }
    }

    /**
     * Makes sure that we are properly connected and that the server supports multi user chats.
     *
     * @throws OperationFailedException if the provider is not registered or the xmpp connection not connected.
     * @throws OperationNotSupportedException if the service is not supported by the server.
     */
    @Throws(OperationFailedException::class, OperationNotSupportedException::class)
    private fun assertSupportedAndConnected() {
        // throw an exception if the provider is not registered or the xmpp connection not connected.
        if (!mPPS.isRegistered || mConnection == null || !mConnection!!.isConnected) {
            throw OperationFailedException("Provider not connected to jabber server",
                OperationFailedException.NETWORK_FAILURE)
        }
    }

    /**
     * In case `roomName` does not represent a complete room id, the method returns a canonical
     * chat room name in the following form: roomName@muc-servicename.jabserver.com. In case `roomName`
     * is already a canonical room name, the method simply returns it without changing it.
     *
     * @param roomName the name of the room that we'd like to "canonize".
     *
     * @return the canonical name of the room (which might be equal to roomName in case it was
     * already in a canonical format).
     * @throws OperationFailedException if we fail retrieving the conference service name
     */
    @Throws(OperationFailedException::class)
    private fun getCanonicalRoomName(roomName: String?): EntityBareJid {
        try {
            return JidCreate.entityBareFrom(roomName)
        } catch (e: XmppStringprepException) {
            // try to append to domain part of our own JID
        }
        var serviceNames: List<DomainBareJid?>? = null
        try {
            if (mMucMgr != null) serviceNames = mMucMgr!!.mucServiceDomains
        } catch (ex: XMPPException) {
            val accountId = mPPS.accountID
            val errMsg = ("Failed to retrieve conference service name for user: "
                    + accountId.mUserID + " on server: " + accountId.service)
            Timber.e(ex, "%s", errMsg)
            throw OperationFailedException(errMsg, OperationFailedException.GENERAL_ERROR, ex)
        } catch (ex: NoResponseException) {
            val accountId = mPPS.accountID
            val errMsg = ("Failed to retrieve conference service name for user: "
                    + accountId.mUserID + " on server: " + accountId.service)
            Timber.e(ex, "%s", errMsg)
            throw OperationFailedException(errMsg, OperationFailedException.GENERAL_ERROR, ex)
        } catch (ex: NotConnectedException) {
            val accountId = mPPS.accountID
            val errMsg = ("Failed to retrieve conference service name for user: "
                    + accountId.mUserID + " on server: " + accountId.service)
            Timber.e(ex, "%s", errMsg)
            throw OperationFailedException(errMsg, OperationFailedException.GENERAL_ERROR, ex)
        } catch (ex: InterruptedException) {
            val accountId = mPPS.accountID
            val errMsg = ("Failed to retrieve conference service name for user: "
                    + accountId.mUserID + " on server: " + accountId.service)
            Timber.e(ex, "%s", errMsg)
            throw OperationFailedException(errMsg, OperationFailedException.GENERAL_ERROR, ex)
        }
        if (serviceNames != null && serviceNames.isNotEmpty()) {
            return try {
                JidCreate.entityBareFrom(Localpart.from(roomName), serviceNames[0])
            } catch (e: XmppStringprepException) {
                throw OperationFailedException("$roomName is not a valid JID local part",
                    OperationFailedException.GENERAL_ERROR, e
                )
            }
        }
        throw OperationFailedException("Failed to retrieve MultiUserChat service names.",
            OperationFailedException.GENERAL_ERROR)
    }

    /*
     * Returns a reference to the chat room named <code>chatRoomName</code> or null if the room hasn't been cached yet.
     *
     * @param chatRoomName the name of the room we're looking for.
     * @return the <code>ChatRoomJabberImpl</code> instance that has been cached for
     * <code>chatRoomName</code> or null if no such room has been cached so far.
     */
    fun getChatRoom(chatRoomName: BareJid?): ChatRoomJabberImpl? {
        return chatRoomCache[chatRoomName]
    }

    /**
     * Returns the list of currently joined chat rooms for `chatRoomMember`.
     *
     * @param chatRoomMember the member we're looking for
     *
     * @return a list of all currently joined chat rooms
     * @throws OperationFailedException if the operation fails
     * @throws OperationNotSupportedException if the operation is not supported
     */
    @Throws(OperationFailedException::class, OperationNotSupportedException::class)
    override fun getCurrentlyJoinedChatRooms(chatRoomMember: ChatRoomMember?): List<String?> {
        assertSupportedAndConnected()
        val joinedRooms: MutableList<String?> = ArrayList()
        if (mMucMgr != null) {
            try {
                for (joinedRoom in mMucMgr!!.getJoinedRooms(JidCreate.entityFullFrom(chatRoomMember!!.getContactAddress()))) {
                    joinedRooms.add(joinedRoom.toString())
                }
            } catch (e: NoResponseException) {
                throw OperationFailedException("Could not get list of joined rooms",
                    OperationFailedException.GENERAL_ERROR, e)
            } catch (e: XMPPErrorException) {
                throw OperationFailedException("Could not get list of joined rooms",
                    OperationFailedException.GENERAL_ERROR, e)
            } catch (e: NotConnectedException) {
                throw OperationFailedException("Could not get list of joined rooms",
                    OperationFailedException.GENERAL_ERROR, e)
            } catch (e: XmppStringprepException) {
                throw OperationFailedException("Could not get list of joined rooms",
                    OperationFailedException.GENERAL_ERROR, e)
            } catch (e: InterruptedException) {
                throw OperationFailedException("Could not get list of joined rooms",
                    OperationFailedException.GENERAL_ERROR, e)
            }
        }
        return joinedRooms
    }

    /**
     * Delivers a `ChatRoomInvitationReceivedEvent` to all registered
     * `ChatRoomInvitationListener`s.
     *
     * @param targetChatRoom the room that invitation refers to
     * @param inviter the inviter that sent the invitation
     * @param reason the reason why the inviter sent the invitation
     * @param password the password to use when joining the room
     */
    fun fireInvitationEvent(targetChatRoom: ChatRoom, inviter: EntityJid, reason: String?, password: ByteArray?) {
        val invitation = ChatRoomInvitationJabberImpl(targetChatRoom, inviter, reason, password)
        fireInvitationReceived(invitation)
    }

    /**
     * A listener that is fired anytime an invitation to join a MUC room is received.
     */
    private inner class SmackInvitationListener : InvitationListener {
        /**
         * Called when the an invitation to join a MUC room is received.
         *
         * If the room is password-protected, the invitee will receive a password to use to join
         * the room. If the room is members-only, then the invitee may be added to the member list.
         *
         * @param conn the XMPPConnection that received the invitation.
         * @param muc the multi user chatRoom that invitation refers to.
         * @param inviter the inviter that sent the invitation. (e.g. crone1@shakespeare.lit).
         * @param reason the reason why the inviter sent the invitation.
         * @param password the password to use when joining the room.
         * @param message the message used by the inviter to send the invitation.
         */
        override fun invitationReceived(
                conn: XMPPConnection, muc: MultiUserChat, inviter: EntityJid,
                reason: String?, password: String?, message: Message, invitation: Invite,
        ) {
            val room = muc.room
            if (muc.isJoined) {
                Timber.w("Decline invitation! Already in the chat Room: %s", room)
                return
            }
            val chatRoom = findRoom(room)!!
            if (password != null)
                fireInvitationEvent(chatRoom, inviter, reason, password.toByteArray())
            else
                fireInvitationEvent(chatRoom, inviter, reason, null)
        }
    }

    /**
     * A listener that is fired anytime an invitee declines or rejects an invitation.
     */
    /**
     * Creates an instance of `SmackInvitationRejectionListener` and passes to it the
     * chat room for which it will listen for rejection events.
     *
     * chatRoom chat room for which this instance will listen for rejection events
     */
    private inner class SmackInvitationRejectionListener(
            /**
             * The chat room for this listener.
             */
            private val chatRoom: ChatRoom?,
    ) : InvitationRejectionListener {
        /**
         * Called when the invitee declines the invitation.
         *
         * @param invitee the invitee that declined the invitation. (e.g. hecate@shakespeare.lit).
         * @param reason the reason why the invitee declined the invitation.
         */
        override fun invitationDeclined(invitee: EntityBareJid, reason: String, message: Message, rejection: Decline) {
            fireInvitationRejectedEvent(chatRoom!!, invitee, reason)
        }
    }

    /**
     * Our listener that will tell us when we're registered to Jabber and the smack
     * MultiUserChat is ready to accept us as a listener.
     */
    private inner class RegistrationStateListener : RegistrationStateChangeListener {
        /**
         * The method is called by a ProtocolProvider implementation whenever a change in the
         * registration state of the corresponding provider had occurred.
         *
         * @param evt ProviderStatusChangeEvent the event describing the status change.
         */
        override fun registrationStateChanged(evt: RegistrationStateChangeEvent) {
            if (evt.getNewState() === RegistrationState.REGISTERED) {
                Timber.d("adding an Invitation listener to the smack muc")
                mConnection = mPPS.connection
                mMucMgr = MultiUserChatManager.getInstanceFor(mConnection)
                mInvitationListener = SmackInvitationListener()
                mMucMgr!!.addInvitationListener(mInvitationListener)
                mConnection!!.addAsyncStanzaListener(chatRoomMessageListener, MUC_ROOM_FILTER)
            }
            else if (evt.getNewState() === RegistrationState.UNREGISTERED
                    || evt.getNewState() === RegistrationState.CONNECTION_FAILED) {
                // clear cached chatRooms as there are no longer valid
                if (mConnection != null) mConnection!!.removeAsyncStanzaListener(chatRoomMessageListener)
                chatRoomCache.clear()
            }
            else if (evt.getNewState() === RegistrationState.UNREGISTERING) {
                if (mMucMgr != null) {
                    mMucMgr!!.removeInvitationListener(mInvitationListener)
                    mInvitationListener = null
                }
                // lets check for joined rooms and leave them
                val joinedRooms = getCurrentlyJoinedChatRooms()
                for (room in joinedRooms) {
                    room!!.leave()
                }
            }
        }
    }

    /**
     * chatRoom stanza listener for messages that are not supported by smack currently i.e.
     * a. Captcha challenge message
     * b. ChatRoom system error messages
     */
    private val chatRoomMessageListener = StanzaListener { packet: Stanza ->
        val message = packet as Message
        val entityBareJid = message.from.asEntityBareJidIfPossible()
        val chatRoom = findRoom(entityBareJid)
        if (message.getExtension(CaptchaExtension::class.java) != null) {
            chatRoom!!.initCaptchaProcess(message)
        }
        else if (Message.Type.error == message.type) {
            // Timber.d("ChatRoom Message: %s", sMessage.toXML());
            chatRoom!!.processMessage(message)
        }
    }

    /**
     * Instantiates the user operation set with a currently valid instance of the Jabber protocol provider.
     * Note: the xmpp connection is not established yet.
     *
     * pps a currently valid instance of ProtocolProviderServiceJabberImpl.
     */
    init {
        // The registration listener that would get notified when the underlying Jabber provider gets registered.
        val providerRegListener = RegistrationStateListener()
        mPPS.addRegistrationStateChangeListener(providerRegListener)
        opSetPersPresence = mPPS.getOperationSet(OperationSetPersistentPresence::class.java) as OperationSetPersistentPresenceJabberImpl?
        opSetPersPresence!!.addSubscriptionListener(this)
    }

    /**
     * Updates corresponding chat room members when a contact has been modified in our contact list.
     *
     * @param evt the `SubscriptionEvent` that notified us
     */
    override fun contactModified(evt: ContactPropertyChangeEvent?) {
        val modifiedContact = evt!!.getSourceContact()
        updateChatRoomMembers(modifiedContact)
    }

    /**
     * Updates corresponding chat room members when a contact has been created in our contact list.
     *
     * @param evt the `SubscriptionEvent` that notified us
     */
    override fun subscriptionCreated(evt: SubscriptionEvent?) {
        val createdContact = evt!!.getSourceContact()
        updateChatRoomMembers(createdContact)
    }

    /**
     * Not interested in this event for our member update purposes.
     *
     * @param evt the `SubscriptionEvent` that notified us
     */
    override fun subscriptionFailed(evt: SubscriptionEvent?) {}

    /**
     * Not interested in this event for our member update purposes.
     *
     * @param evt the `SubscriptionEvent` that notified us
     */
    override fun subscriptionMoved(evt: SubscriptionMovedEvent?) {}

    /**
     * Updates corresponding chat room members when a contact has been removed from our contact list.
     *
     * evt the `SubscriptionEvent` that notified us
     */
    override fun subscriptionRemoved(evt: SubscriptionEvent?) {}

    /**
     * Not interested in this event for our member update purposes.
     *
     * @paramevt the `SubscriptionEvent` that notified us
     */
    override fun subscriptionResolved(evt: SubscriptionEvent?) {}

    /**
     * Finds all chat room members, which name corresponds to the name of the given contact and
     * updates their contact references.
     *
     * @param contact the contact we're looking correspondences for.
     */
    private fun updateChatRoomMembers(contact: Contact) {
        // ConcurrentModificationException happens during test
        synchronized(chatRoomCache) {
            for (chatRoom in chatRoomCache.values) {
                val nick: Resourcepart? = try {
                    contact.contactJid!!.resourceOrThrow
                } catch (e: IllegalStateException) {
                    continue
                }
                val member = chatRoom!!.findMemberForNickName(nick)
                if (member != null) {
                    member.setContact(contact)
                    member.setAvatar(contact.image)
                }
            }
        }
    }

    /**
     * Return XEP-0203 time-stamp of the message if present or current time;
     *
     * @param msg Message
     *
     * @return the correct message timeStamp
     */
    private fun getTimeStamp(msg: Message): Date {
        val timeStamp: Date
        val delayInfo = msg.getExtension(DelayInformation::class.java)
        timeStamp = if (delayInfo != null) {
            delayInfo.stamp
        }
        else {
            Date()
        }
        return timeStamp
    }

    // =============== OMEMO message received =============== //
    fun registerOmemoMucListener(omemoManager: OmemoManager) {
        mOmemoManager = omemoManager
        omemoManager.addOmemoMucMessageListener(this)
    }

    fun unRegisterOmemoMucListener(omemoManager: OmemoManager) {
        omemoManager.removeOmemoMucMessageListener(this)
        mOmemoManager = null
    }

    /**
     * Gets called whenever an OMEMO message has been received in a MultiUserChat and successfully decrypted.
     *
     * @param muc MultiUserChat the message was sent in
     * @param stanza Original Stanza
     * @param decryptedOmemoMessage decrypted Omemo message
     */
    override fun onOmemoMucMessageReceived(
            muc: MultiUserChat, stanza: Stanza,
            decryptedOmemoMessage: Received,
    ) {
        // Do not process if decryptedMessage isKeyTransportMessage i.e. msgBody == null
        if (decryptedOmemoMessage.isKeyTransportMessage) return
        val message = stanza as Message
        val timeStamp = getTimeStamp(message)
        val sender = decryptedOmemoMessage.senderDevice.jid
        val chatRoom = getChatRoom(muc.room)
        val member = chatRoom!!.findMemberFromParticipant(message.from)
        val msgID = message.stanzaId
        var encType = IMessage.ENCRYPTION_OMEMO
        val msgBody = decryptedOmemoMessage.body

        // aTalk OMEMO msgBody may contains markup text then set as ENCODE_HTML mode
        encType = if (msgBody.matches(ChatMessage.HTML_MARKUP)) {
            encType or IMessage.ENCODE_HTML
        }
        else {
            encType or IMessage.ENCODE_PLAIN
        }
        var newMessage = MessageJabberImpl(msgBody, encType, null, msgID)

        // check if the message is available in xhtml
        val xhtmString = getXhtmlExtension(message)
        if (xhtmString != null) {
            try {
                val xpp = PacketParserUtils.getParserFor(xhtmString)
                val omemoElement = omemoVAxolotlProvider.parse(xpp)
                val xhtmlMessage = mOmemoManager!!.decrypt(sender, omemoElement)
                encType = encType or IMessage.ENCODE_HTML
                newMessage = MessageJabberImpl(xhtmlMessage.body, encType, null, msgID)
            } catch (e: NotLoggedInException) {
                Timber.e("Error decrypting xhtmlExtension message %s:", e.message)
            } catch (e: IOException) {
                Timber.e("Error decrypting xhtmlExtension message %s:", e.message)
            } catch (e: CorruptedOmemoKeyException) {
                Timber.e("Error decrypting xhtmlExtension message %s:", e.message)
            } catch (e: NoRawSessionException) {
                Timber.e("Error decrypting xhtmlExtension message %s:", e.message)
            } catch (e: CryptoFailedException) {
                Timber.e("Error decrypting xhtmlExtension message %s:", e.message)
            } catch (e: XmlPullParserException) {
                Timber.e("Error decrypting xhtmlExtension message %s:", e.message)
            } catch (e: SmackParsingException) {
                Timber.e("Error decrypting xhtmlExtension message %s:", e.message)
            }
        }
        newMessage.setRemoteMsgId(msgID)
        val msgReceivedEvt = ChatRoomMessageReceivedEvent(chatRoom, member!!, timeStamp, newMessage, ChatMessage.MESSAGE_MUC_IN)
        chatRoom.fireMessageEvent(msgReceivedEvt)
    }
}