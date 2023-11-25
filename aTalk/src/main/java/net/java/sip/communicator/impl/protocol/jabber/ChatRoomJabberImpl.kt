/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import android.os.Handler
import android.os.Looper
import android.text.Html
import android.text.TextUtils
import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.service.protocol.AbstractChatRoom
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.ChatRoomConfigurationForm
import net.java.sip.communicator.service.protocol.ChatRoomMember
import net.java.sip.communicator.service.protocol.ChatRoomMemberRole
import net.java.sip.communicator.service.protocol.ConferenceDescription
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.IMessage
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresence
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.ChatRoomConferencePublishedEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomLocalUserRoleChangeEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomLocalUserRoleListener
import net.java.sip.communicator.service.protocol.event.ChatRoomMemberPresenceChangeEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMemberPresenceListener
import net.java.sip.communicator.service.protocol.event.ChatRoomMemberPropertyChangeEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMemberPropertyChangeListener
import net.java.sip.communicator.service.protocol.event.ChatRoomMemberRoleChangeEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMemberRoleListener
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveryFailedEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageListener
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageReceivedEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomPropertyChangeEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomPropertyChangeFailedEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomPropertyChangeListener
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceChangeEvent
import net.java.sip.communicator.service.protocol.event.MessageDeliveryFailedEvent
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum
import net.java.sip.communicator.util.ConfigurationUtils.getChatRoomProperty
import net.java.sip.communicator.util.ConfigurationUtils.updateChatRoomProperty
import org.apache.commons.lang3.StringUtils
import org.atalk.crypto.omemo.OmemoAuthenticateDialog
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.chat.ChatMessage
import org.atalk.hmos.gui.chat.conference.CaptchaDialog
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.util.XhtmlUtil.getXhtmlExtension
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.jivesoftware.smack.MessageListener
import org.jivesoftware.smack.PresenceListener
import org.jivesoftware.smack.SmackException.NoResponseException
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.SmackException.NotLoggedInException
import org.jivesoftware.smack.StanzaListener
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smack.packet.ExtensionElement
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.MessageBuilder
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.packet.PresenceBuilder
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.packet.StanzaBuilder
import org.jivesoftware.smack.packet.StanzaError
import org.jivesoftware.smack.packet.id.StandardStanzaIdSource
import org.jivesoftware.smack.util.Consumer
import org.jivesoftware.smackx.address.packet.MultipleAddresses
import org.jivesoftware.smackx.confdesc.ConferenceDescriptionExtension
import org.jivesoftware.smackx.confdesc.TransportExtension
import org.jivesoftware.smackx.delay.packet.DelayInformation
import org.jivesoftware.smackx.jitsimeet.AvatarUrl
import org.jivesoftware.smackx.jitsimeet.Email
import org.jivesoftware.smackx.jitsimeet.JsonMessageExtension
import org.jivesoftware.smackx.jitsimeet.StatsId
import org.jivesoftware.smackx.muc.InvitationRejectionListener
import org.jivesoftware.smackx.muc.MUCAffiliation
import org.jivesoftware.smackx.muc.MUCRole
import org.jivesoftware.smackx.muc.MultiUserChat
import org.jivesoftware.smackx.muc.MultiUserChatException
import org.jivesoftware.smackx.muc.MultiUserChatManager
import org.jivesoftware.smackx.muc.ParticipantStatusListener
import org.jivesoftware.smackx.muc.SubjectUpdatedListener
import org.jivesoftware.smackx.muc.UserStatusListener
import org.jivesoftware.smackx.muc.filter.MUCUserStatusCodeFilter
import org.jivesoftware.smackx.muc.packet.MUCInitialPresence
import org.jivesoftware.smackx.muc.packet.MUCUser
import org.jivesoftware.smackx.nick.packet.Nick
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.element.OmemoElement
import org.jivesoftware.smackx.omemo.exceptions.CryptoFailedException
import org.jivesoftware.smackx.omemo.exceptions.NoOmemoSupportException
import org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException
import org.jivesoftware.smackx.omemo.internal.OmemoDevice
import org.jivesoftware.smackx.omemo.util.OmemoConstants
import org.jivesoftware.smackx.xdata.FormField
import org.jivesoftware.smackx.xdata.form.FillableForm
import org.jivesoftware.smackx.xdata.form.Form
import org.jivesoftware.smackx.xdata.packet.DataForm
import org.jivesoftware.smackx.xhtmlim.XHTMLManager
import org.jivesoftware.smackx.xhtmlim.XHTMLText
import org.jivesoftware.smackx.xhtmlim.packet.XHTMLExtension
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.EntityFullJid
import org.jxmpp.jid.EntityJid
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart
import org.jxmpp.stringprep.XmppStringprepException
import org.jxmpp.util.XmppStringUtils
import timber.log.Timber
import java.beans.PropertyChangeEvent
import java.io.IOException
import java.util.*

/**
 * Implements chat rooms for jabber. The class encapsulates instances of the jive software `MultiUserChat`.
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 * @author Valentin Martinet
 * @author Boris Grozev
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class ChatRoomJabberImpl(
        multiUserChat: MultiUserChat,
        provider: ProtocolProviderServiceJabberImpl,
) : AbstractChatRoom(), CaptchaDialog.CaptchaDialogListener {
    /**
     * The multi user chat smack object that we encapsulate in this room.
     */
    private val mMultiUserChat: MultiUserChat

    /**
     * Listeners that will be notified of changes in member status in the room such as member
     * joined, left or being kicked or dropped.
     */
    private val memberListeners = Vector<ChatRoomMemberPresenceListener>()

    /**
     * Listeners that will be notified of changes in member mRole in the room such as member being
     * granted admin permissions, or revoked admin permissions.
     */
    private val memberRoleListeners = Vector<ChatRoomMemberRoleListener>()

    /**
     * Listeners that will be notified of changes in local user mRole in the room such as member
     * being granted admin permissions, or revoked admin permissions.
     */
    private val localUserRoleListeners = Vector<ChatRoomLocalUserRoleListener>()

    /**
     * Listeners that will be notified every time a new message is received on this chat room.
     */
    private val messageListeners = Vector<ChatRoomMessageListener>()

    /**
     * Listeners that will be notified every time a chat room property has been changed.
     */
    private val propertyChangeListeners = Vector<ChatRoomPropertyChangeListener>()

    /**
     * Listeners that will be notified every time a chat room member property has been changed.
     */
    private val memberPropChangeListeners = Vector<ChatRoomMemberPropertyChangeListener>()

    /**
     * The protocol mProvider that created us
     */
    private val mPPS: ProtocolProviderServiceJabberImpl

    /**
     * The operation set that created us.
     */
    private val opSetMuc: OperationSetMultiUserChatJabberImpl?

    /**
     * The list of members of this chat room EntityFullJid
     */
    private val members = Hashtable<Resourcepart?, ChatRoomMemberJabberImpl?>()

    /**
     * The list of banned members of this chat room EntityFullJid.
     */
    private val banList = Hashtable<Resourcepart, ChatRoomMember>()

    /**
     * The Resource Part of this chat room local user participant i.e. NickName.
     */
    private var mNickName: Resourcepart? = null

    /**
     * The password use during join room
     */
    private var mPassword: ByteArray? = null

    /**
     * The subject of this chat room. Keeps track of the subject changes.
     */
    private var oldSubject: String?

    // The last send Message encType for reinsert into relayed delivered message from server
    private var mEncType = 0

    /**
     * The mRole of this chat room local user participant.
     */
    private var mUserRole: ChatRoomMemberRole? = null

    /**
     * Intercepts presences to set custom extensions.
     */
    private val presenceInterceptor = Consumer { presenceBuilder: PresenceBuilder -> presenceIntercept(presenceBuilder) }

    /**
     * The conference which we have announced in the room in our last sent `Presence` update.
     */
    private var publishedConference: ConferenceDescription? = null

    /**
     * The `ConferenceAnnouncementPacketExtension` corresponding to `publishedConference` which we
     * add to all our presence updates. This MUST be kept in sync with `publishedConference`
     */
    private var publishedConferenceExt: ConferenceDescriptionExtension? = null

    /**
     * List of packet extensions we need to add to every outgoing presence we send.
     * Currently used from external components reusing the protocol provider
     * to permanently add extension to the outgoing stanzas.
     */
    private val presencePacketExtensions = ArrayList<ExtensionElement>()

    /**
     * The last `Presence` packet we sent to the MUC.
     */
    private var lastPresenceSent: Presence? = null
    private val chatRoomConferenceCalls = ArrayList<CallJabberImpl>()

    /**
     * All <presence></presence>'s reason will default to REASON_USER_LIST until user own `Presence` has been received.
     *
     * @see ChatRoomMemberPresenceChangeEvent.REASON_USER_LIST
     */
    private var mucOwnPresenceReceived = false

    /**
     * Packet listener waits for rejection of invitations to join room.
     */
    private val invitationRejectionListeners: InvitationRejectionListeners

    /**
     * Local status change listener
     */
    private val userStatusListener: LocalUserStatusListener

    /**
     * Presence listener for joining participants.
     */
    private val participantListener: ParticipantListener
    private var mCaptchaState = CaptchaDialog.unknown
    private val messageListener: MucMessageListener
    private var captchaMessage: Message? = null

    /**
     * Creates an instance of a chat room and initialize all the necessary listeners
     * for group chat status monitoring
     *
     * multiUserChat MultiUserChat
     * provider a reference to the currently valid jabber protocol mProvider.
     */
    init {
        mMultiUserChat = multiUserChat
        mPPS = provider
        opSetMuc = provider.getOperationSet(OperationSetMultiUserChat::class.java) as OperationSetMultiUserChatJabberImpl?
        oldSubject = multiUserChat.subject
        multiUserChat.addSubjectUpdatedListener(MucSubjectUpdatedListener())
        multiUserChat.addParticipantStatusListener(MemberListener())
        messageListener = MucMessageListener()
        multiUserChat.addMessageListener(messageListener)
        userStatusListener = LocalUserStatusListener()
        multiUserChat.addUserStatusListener(userStatusListener)
        multiUserChat.addPresenceInterceptor(presenceInterceptor)
        participantListener = ParticipantListener()
        multiUserChat.addParticipantListener(participantListener)
        invitationRejectionListeners = InvitationRejectionListeners()
        multiUserChat.addInvitationRejectionListener(invitationRejectionListeners)
        val conferenceChatManager = AndroidGUIActivator.uIService.conferenceChatManager
        addMessageListener(conferenceChatManager)
    }

    /**
     * Show captcha challenge for group chat if requested
     *
     * message Stanza Message containing captcha challenge info
     */
    fun initCaptchaProcess(message: Message?) {
        // Set flag to ignore reply timeout
        captchaMessage = message
        mCaptchaState = CaptchaDialog.awaiting

        // Do not proceed to launch CaptchaDialog if app is in background - system crash
        if (!aTalkApp.isForeground) {
            return
        }
        aTalkApp.waitForFocus()
        Handler(Looper.getMainLooper()).post {

            // Must use activity (may be null at time) Otherwise token null is not valid is your activity running?
            val activity = aTalkApp.getCurrentActivity()
            if (activity != null) {
                val captchaDialog = CaptchaDialog(activity,
                    mMultiUserChat, message!!, this@ChatRoomJabberImpl)
                captchaDialog.show()
            }
        }
    }

    /**
     * Captcha dialog callback on user response to the challenge
     *
     * state Captcha dialog response state from server/user
     *
     * @see CaptchaDialog state
     */
    override fun onResult(state: Int) {
        mCaptchaState = state
        when (mCaptchaState) {
            CaptchaDialog.validated -> if (mMultiUserChat.isJoined) onJoinSuccess()
            else {
                try {
                    Timber.d("Rejoined chat room after captcha challenge")
                    // must re-joined immediately, otherwise smack has problem handling room delayed messages
                    joinAs(mNickName.toString(), mPassword)
                } catch (e: OperationFailedException) {
                    Timber.w("Rejoined error: %s", e.message)
                }
            }
            CaptchaDialog.failed ->                 // CaptchaDialog will display the error message, try to rejoin
                try {
                    joinAs(mNickName.toString(), mPassword)
                } catch (e: OperationFailedException) {
                    Timber.w("Rejoined error: %s", e.message)
                }
            CaptchaDialog.cancel -> {
                // Show in chat instead of launching an alert dialog
                val errMsg = aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_FAILED, mNickName, getName())
                addMessage(errMsg, ChatMessage.MESSAGE_ERROR)
                Timber.d("User cancel: %s", errMsg)
            }
            else -> {}
        }
    }

    /**
     * Captcha dialog callback on server response to user input
     *
     * message message string
     * msgType messageType
     */
    override fun addMessage(msg: String, msgType: Int) {
        val chatPanel = MUCActivator.uIService!!.getChat(this@ChatRoomJabberImpl)!!
        chatPanel.addMessage(mMultiUserChat.room.toString(), Date(), msgType, IMessage.ENCODE_PLAIN, msg)
    }

    /**
     * Provide access to the smack processMessage() by external class
     *
     * message chatRoom message
     */
    fun processMessage(message: Message) {
        messageListener.processMessage(message)
    }

    /**
     * Adds `listener` to the list of listeners registered to receive events upon
     * modification of chat room properties such as its subject for example.
     *
     * listener the `ChatRoomChangeListener` that is to be registered for
     * `ChatRoomChangeEvent`-s.
     */
    override fun addPropertyChangeListener(listener: ChatRoomPropertyChangeListener) {
        synchronized(propertyChangeListeners) { if (!propertyChangeListeners.contains(listener)) propertyChangeListeners.add(listener) }
    }

    /**
     * Removes `listener` from the list of listeners current registered for chat room modification events.
     *
     * listener the `ChatRoomChangeListener` to remove.
     */
    override fun removePropertyChangeListener(listener: ChatRoomPropertyChangeListener) {
        synchronized(propertyChangeListeners) { propertyChangeListeners.remove(listener) }
    }

    /**
     * Adds the given `listener` to the list of listeners registered to receive events upon
     * modification of chat room member properties such as its Nickname being changed for example.
     *
     * listener the `ChatRoomMemberPropertyChangeListener` that is to be registered for
     * `ChatRoomMemberPropertyChangeEvent`s.
     */
    override fun addMemberPropertyChangeListener(listener: ChatRoomMemberPropertyChangeListener) {
        synchronized(memberPropChangeListeners) { if (!memberPropChangeListeners.contains(listener)) memberPropChangeListeners.add(listener) }
    }

    /**
     * Removes the given `listener` from the list of listeners currently registered for chat
     * room member property change events.
     *
     * listener the `ChatRoomMemberPropertyChangeListener` to remove.
     */
    override fun removeMemberPropertyChangeListener(listener: ChatRoomMemberPropertyChangeListener) {
        synchronized(memberPropChangeListeners) { memberPropChangeListeners.remove(listener) }
    }

    /**
     * Registers `listener` so that it would receive events every time a new message is
     * received on this chat room.
     *
     * listener a `MessageListener` that would be notified every time a new message is received
     * on this chat room.
     */
    override fun addMessageListener(listener: ChatRoomMessageListener) {
        synchronized(messageListeners) { if (!messageListeners.contains(listener)) messageListeners.add(listener) }
    }

    /**
     * Removes `listener` so that it won't receive any further message events from this room.
     *
     * listener the `MessageListener` to remove from this room
     */
    override fun removeMessageListener(listener: ChatRoomMessageListener) {
        synchronized(messageListeners) { messageListeners.remove(listener) }
    }

    /**
     * Adds a listener that will be notified of changes in our status in the room such as us being
     * kicked, banned, or granted admin permissions.
     *
     * listener a participant status listener.
     */
    override fun addMemberPresenceListener(listener: ChatRoomMemberPresenceListener) {
        synchronized(memberListeners) { if (!memberListeners.contains(listener)) memberListeners.add(listener) }
    }

    /**
     * Removes a listener that was being notified of changes in the status of other chat room
     * participants such as users being kicked, banned, or granted admin permissions.
     *
     * listener a participant status listener.
     */
    override fun removeMemberPresenceListener(listener: ChatRoomMemberPresenceListener) {
        synchronized(memberListeners) { memberListeners.remove(listener) }
    }

    /**
     * Adds a `CallJabberImpl` instance to the list of conference calls associated with the room.
     *
     * call the call to add
     */
    @Synchronized
    fun addConferenceCall(call: CallJabberImpl) {
        if (!chatRoomConferenceCalls.contains(call)) chatRoomConferenceCalls.add(call)
    }

    /**
     * Removes a `CallJabberImpl` instance from the list of conference calls associated with the room.
     *
     * call the call to remove.
     */
    @Synchronized
    fun removeConferenceCall(call: CallJabberImpl) {
        chatRoomConferenceCalls.remove(call)
    }

    /**
     * Create a Message instance for sending arbitrary MIME-encoding content.
     *
     * content message content value
     * encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     * subject a `String` subject or `null` for now subject.
     *
     * @return the newly created message.
     */
    override fun createMessage(content: String, encType: Int, subject: String?): IMessage {
        return MessageJabberImpl(content, encType, subject, null)
    }

    /**
     * Create a Message instance for sending a simple text messages with default (text/plain)
     * content type and encoding.
     *
     * messageText the string content of the message.
     *
     * @return IMessage the newly created message
     */
    override fun createMessage(messageText: String): IMessage {
        return MessageJabberImpl(messageText, IMessage.ENCODE_PLAIN, "", null)
    }

    /**
     * Returns a List of `Member`s corresponding to all members currently participating in this room.
     *
     * @return a List of `Member` corresponding to all room members.
     */
    override fun getMembers(): List<ChatRoomMember> {
        synchronized(members) { return LinkedList<ChatRoomMember>(members.values) }
    }

    /**
     * Returns the number of participants that are currently in this chat room.
     *
     * @return int the number of `Contact`s, currently participating in this room.
     */
    override fun getMembersCount(): Int {
        return mMultiUserChat.occupantsCount
    }

    /**
     * Returns the name of this `ChatRoom`.
     *
     * @return a `String` containing the name of this `ChatRoom`.
     */
    override fun getName(): String {
        return mMultiUserChat.room.toString()
    }

    /**
     * Returns the EntityBareJid of this `ChatRoom`.
     *
     * @return a `EntityBareJid` containing the identifier of this `ChatRoom`.
     */
    override fun getIdentifier(): EntityBareJid {
        return mMultiUserChat.room
    }

    /**
     * Returns the local user's nickname in the context of this chat room or `null` if not currently joined.
     *
     * @return the nickname currently being used by the local user in the context of the local chat room.
     */
    override fun getUserNickname(): Resourcepart {
        return mMultiUserChat.nickname
    }

    private fun getAccountId(chatRoom: ChatRoom?): String {
        val accountId = chatRoom!!.getParentProvider().accountID
        return accountId.accountJid
    }

    /**
     * Finds private messaging contact by nickname. If the contact doesn't exists a new volatile contact is created.
     *
     * nickname the nickname of the contact.
     *
     * @return the contact instance.
     */
    override fun getPrivateContactByNickname(nickname: String): Contact {
        val opSetPersPresence = mPPS.getOperationSet(OperationSetPersistentPresence::class.java) as OperationSetPersistentPresenceJabberImpl?
        val jid = try {
            JidCreate.fullFrom(getIdentifier(), Resourcepart.from(nickname))
        } catch (e: XmppStringprepException) {
            throw IllegalArgumentException("Invalid XMPP nickname")
        }
        var sourceContact = opSetPersPresence!!.findContactByJid(jid)
        if (sourceContact == null) {
            sourceContact = opSetPersPresence.createVolatileContact(jid, true)
        }
        return sourceContact
    }

    /**
     * Returns the last known room subject/theme or `null` if the user hasn't joined the
     * room or the room does not have a subject yet.
     *
     * @return the room subject or `null` if the user hasn't joined the room or the room
     * does not have a subject yet.
     */
    override fun getSubject(): String? {
        return mMultiUserChat.subject
    }

    /**
     * Invites another user to this room. Block any domainJid from joining as it does not support IM
     *
     * userJid jid of the user to invite to the room.(one may also invite users not on their contact list).
     * reason a reason, subject, or welcome message that would tell the the user why they are being invited.
     */
    @Throws(NotConnectedException::class, InterruptedException::class)
    override fun invite(userJid: EntityBareJid, reason: String?) {
        if (TextUtils.isEmpty(XmppStringUtils.parseLocalpart(userJid.toString()))) {
            aTalkApp.showToastMessage(R.string.service_gui_SEND_MESSAGE_NOT_SUPPORTED, userJid)
        }
        else {
            mMultiUserChat.invite(userJid, reason)
        }
    }

    /**
     * Returns true if the local user is currently in the multi user chat (after calling one of the
     * [.join] methods).
     *
     * @return true if currently we're currently in this chat room and false otherwise.
     */
    override fun isJoined(): Boolean {
        return mMultiUserChat.isJoined
    }

    /**
     * Joins this chat room with the nickName of the local user so that the user would start
     * receiving events and messages for it.
     *
     * @throws OperationFailedException with the corresponding code if an error occurs while joining the room.
     */
    @Throws(OperationFailedException::class)
    override fun join(): Boolean {
        return joinAs(JabberActivator.globalDisplayDetailsService!!.getDisplayName(mPPS))
    }

    /**
     * Joins this chat room so that the user would start receiving events and messages for it.
     *
     * password the password to use when authenticating on the chatRoom.
     *
     * @throws OperationFailedException with the corresponding code if an error occurs while joining the room.
     */
    @Throws(OperationFailedException::class)
    override fun join(password: ByteArray): Boolean {
        return joinAs(JabberActivator.globalDisplayDetailsService!!.getDisplayName(mPPS), password)
    }

    /**
     * Joins this chat room with the specified nickname as anonymous so that the user would
     * start receiving events and messages for it.
     *
     * nickname the nickname can be jid or just nick.
     *
     * @throws OperationFailedException with the corresponding code if an error occurs while joining the room.
     */
    @Throws(OperationFailedException::class)
    override fun joinAs(nickname: String?): Boolean {
        return joinAs(nickname, null)
    }

    /**
     * Joins this chat room with the specified nickName and password so that the user would start
     * receiving events and messages for it.
     *
     * nickname the nickname can be jid or just nick.
     * password a password necessary to authenticate when joining the room.
     *
     * @throws OperationFailedException with the corresponding code if an error occurs while joining the room.
     */
    @Throws(OperationFailedException::class)
    override fun joinAs(nickname: String?, password: ByteArray?): Boolean {
        assertConnected()
        var retry = true
        var errorMessage = aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_FAILED, nickname, getName())
        mPassword = password

        if (TextUtils.isEmpty(nickname)) {
            throw OperationFailedException(errorMessage, OperationFailedException.GENERAL_ERROR)
        }

        // parseLocalPart or take nickname as it to join chatRoom
        val sNickname = nickname!!.split("@")[0]
        try {
            mNickName = Resourcepart.from(sNickname)
            if (mMultiUserChat.isJoined) {
                if (mMultiUserChat.nickname != mNickName)
                    mMultiUserChat.changeNickname(mNickName)
            }
            else {
                if (password == null) mMultiUserChat.join(mNickName)
                else mMultiUserChat.join(mNickName, String(password))
            }
            onJoinSuccess()
            retry = false
        } catch (ex: XMPPErrorException) {
            val xmppError = ex.stanzaError
            errorMessage += "\n${ex.message}"
            if (xmppError == null) {
                Timber.e(ex, "%s", errorMessage)
                throw OperationFailedException(errorMessage, OperationFailedException.GENERAL_ERROR, ex)
            }
            else if (xmppError.condition == StanzaError.Condition.not_authorized) {
                Timber.e("Join room exception: %s (state)\n%s", mCaptchaState, errorMessage)
                when (mCaptchaState) {
                    CaptchaDialog.unknown -> {
                        errorMessage += "\n${aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_FAILED_PASSWORD)}"
                        throw OperationFailedException(errorMessage, OperationFailedException.AUTHENTICATION_FAILED, ex)
                    }
                    CaptchaDialog.failed -> {
                        // Allow user retry, do not throw Exception back to caller.
                        // errorMessage += aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_CAPTCHA_VERIFICATION_FAILED)
                        // throw new OperationFailedException(errorMessage, OperationFailedException.CAPTCHA_CHALLENGE, ex)
                        aTalkApp.showToastMessage(errorMessage)
                        retry = true
                    }
                    CaptchaDialog.cancel -> {
                        // To abort retry by caller on user cancel captcha challenge request
                        retry = false
                    }
                }
            }
            else if (xmppError.condition == StanzaError.Condition.registration_required) {
                val errText = xmppError.descriptiveText
                errorMessage += if (TextUtils.isEmpty(errText))
                    "\n${aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_FAILED_REGISTRATION)}"
                else
                    "\n$errText"
                Timber.e(ex, "%s", errorMessage)
                throw OperationFailedException(errorMessage, OperationFailedException.REGISTRATION_REQUIRED, ex)
            }
            else {
                Timber.e(ex, "%s", errorMessage)
                throw OperationFailedException(errorMessage, OperationFailedException.GENERAL_ERROR, ex)
            }
        } catch (ex: Throwable) {
            Timber.e("%s: %s", errorMessage, ex.message)
            // Ignore server response timeout if received captcha challenge or user canceled captcha challenge request
            // - likely not get call as user cancel is implemented by sending empty reply to cancel smack delay
            if (ex is NoResponseException && (mCaptchaState == CaptchaDialog.cancel || mCaptchaState == CaptchaDialog.awaiting)) {
                Timber.d("Join room (Ignore NoResponseException): Received captcha challenge or user canceled")
                retry = false
            }
            else if (mCaptchaState == CaptchaDialog.unknown) {
                errorMessage = ex.message!!
                throw OperationFailedException(errorMessage, OperationFailedException.GENERAL_ERROR, ex)
            }
            else if (mCaptchaState != CaptchaDialog.awaiting) {
                errorMessage += "\n${aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_CAPTCHA_VERIFICATION_FAILED)}"
                throw OperationFailedException(errorMessage, OperationFailedException.CAPTCHA_CHALLENGE, ex)
            }
            else {
                errorMessage = aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_CAPTCHA_AWAITING, getName())
                throw OperationFailedException(errorMessage, OperationFailedException.CAPTCHA_CHALLENGE, ex)
            }
        }
        // Abort retry by caller if false
        return retry
    }

    /**
     * Process to perform upon a successful join room request
     */
    private fun onJoinSuccess() {
        // update members list only on successful joining chatRoom
        val member = ChatRoomMemberJabberImpl(this, mNickName, mPPS.ourJID)
        synchronized(members) { members.put(mNickName, member) }

        // unblock all conference event UI display on received own <presence/> stanza e.g. participants' <presence/> etc
        mucOwnPresenceReceived = true
        opSetMuc!!.fireLocalUserPresenceEvent(this, LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_JOINED, null)
    }

    /**
     * Returns the `ChatRoomMember` corresponding to the given smack participant.
     *
     * participant the EntityFullJid participant (e.g. sc-testroom@conference.voipgw.fr/userNick)
     *
     * @return the `ChatRoomMember` corresponding to the given smack participant
     */
    fun findMemberFromParticipant(participant: Jid?): ChatRoomMemberJabberImpl? {
        if (participant == null) {
            return null
        }
        val participantNick = participant.resourceOrThrow
        synchronized(members) {
            for (member in members.values) {
                if (participantNick == member!!.getNickAsResourcepart() || participant.equals(member.getJabberId())) return member
            }
        }
        return members[participantNick]
    }

    /**
     * Destroys the chat room.
     *
     * reason the reason for destroying.
     * roomName the chat Room Name (e.g. sc-testroom@conference.voipgw.fr)
     *
     * @return `true` if the room is destroyed.
     */
    @Throws(XMPPException::class)
    override fun destroy(reason: String?, alternateAddress: EntityBareJid?): Boolean {
        try {
            mMultiUserChat.destroy(reason, alternateAddress)
        } catch (e: NoResponseException) {
            DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_ERROR,
                R.string.service_gui_CHATROOM_DESTROY_EXCEPTION, e.message)
            return false
        } catch (e: NotConnectedException) {
            DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_ERROR,
                R.string.service_gui_CHATROOM_DESTROY_EXCEPTION, e.message)
            return false
        } catch (e: InterruptedException) {
            DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_ERROR,
                R.string.service_gui_CHATROOM_DESTROY_EXCEPTION, e.message)
            return false
        }
        return true
    }

    /**
     * Leave this chat room with no alternative room address to join.
     */
    override fun leave() {
        this.leave(null, aTalkApp.getResString(R.string.service_gui_LEAVE_ROOM))
    }

    /**
     * Leave this chat room
     *
     * alternateAddress alternate chatRoom to join
     * reason reason for leaving the room
     */
    private fun leave(alternateAddress: EntityBareJid?, reason: String) {
        val basicTelephony = mPPS.getOperationSet(OperationSetBasicTelephony::class.java) as OperationSetBasicTelephonyJabberImpl?
        if (basicTelephony != null && publishedConference != null) {
            val activeRepository = basicTelephony.activeCallsRepository
            val callId = publishedConference!!.getCallId()
            if (callId != null) {
                val call = activeRepository.findByCallId(callId)
                for (peer in call!!.getCallPeerList()) {
                    try {
                        peer!!.hangup(false, null, null)
                    } catch (e: NotConnectedException) {
                        Timber.e(e, "Could not hangup peer %s", peer!!.getAddress())
                    } catch (e: InterruptedException) {
                        Timber.e(e, "Could not hangup peer %s", peer!!.getAddress())
                    }
                }
            }
        }
        var tmpConferenceCalls: List<CallJabberImpl>
        synchronized(chatRoomConferenceCalls) {
            tmpConferenceCalls = ArrayList(chatRoomConferenceCalls)
            chatRoomConferenceCalls.clear()
        }
        for (call in tmpConferenceCalls) {
            for (peer in call.getCallPeerList()) try {
                peer!!.hangup(false, null, null)
            } catch (e: NotConnectedException) {
                Timber.e(e, "Could not hangup peer %s", peer!!.getAddress())
            } catch (e: InterruptedException) {
                Timber.e(e, "Could not hangup peer %s", peer!!.getAddress())
            }
        }
        clearCachedConferenceDescriptionList()
        val connection = mPPS.connection
        try {
            // if we are already disconnected leave may be called from gui when closing chat window
            // Patch smack-4.4.3-alpha3, ensure mMultiUserChat.isJoined() is false when room is destroyed
            if (connection != null && mMultiUserChat.isJoined) {
                mMultiUserChat.leave()
            }
        } catch (e: Throwable) {
            Timber.w(e, "Error leaving room (has been destroyed or disconnected): %s", e.message)
            // must proceed to clean up the rest even if exception
        }

        // cmeng: removed as chatPanel will closed ?
        synchronized(members) {
            for (member in members.values) {
                fireMemberPresenceEvent(member, ChatRoomMemberPresenceChangeEvent.MEMBER_LEFT,
                    "Local user has left the chat room.")
            }
            members.clear()
        }

        /*
         * Remove all the callback listeners for the chatRoom
         * connection can be null if we are leaving due to connection failed
         */
        if (connection != null && mMultiUserChat != null) {
            mMultiUserChat.removeInvitationRejectionListener(invitationRejectionListeners)
            mMultiUserChat.removePresenceInterceptor(presenceInterceptor)
            mMultiUserChat.removeUserStatusListener(userStatusListener)
            mMultiUserChat.removeParticipantListener(participantListener)
        }
        opSetMuc!!.fireLocalUserPresenceEvent(this, LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_LEFT,
            reason, alternateAddress?.toString())
    }

    /**
     * Construct the `message` for the required ENCODE mode for sending.
     *
     * message the `IMessage` to send.
     *
     * @throws OperationFailedException if sending the message fails for some reason.
     */
    @Throws(OperationFailedException::class)
    override fun sendMessage(message: IMessage) {
        mEncType = message.getEncType()
        val content = message.getContent()
        val messageBuilder = StanzaBuilder.buildMessage()
        if (IMessage.ENCODE_HTML == message.getMimeType()) {
            messageBuilder.addBody(null, Html.fromHtml(content).toString())

            // Just add XHTML element as it will be ignored by buddy without XEP-0071: XHTML-IM support
            // Also carbon messages may send to buddy on difference clients with different capabilities
            // Note isFeatureListSupported must use FullJid unless it is for service e.g. conference.atalk.org

            // Check if the buddy supports XHTML messages make sure we use our discovery manager as it caches calls
            // if (jabberProvider.isFeatureListSupported(toJid, XHTMLExtension.NAMESPACE)) {
            // Add the XHTML text to the message
            val htmlText = XHTMLText("", "us")
                .append(content)
                .appendCloseBodyTag()
            val xhtmlExtension = XHTMLExtension()
            xhtmlExtension.addBody(htmlText.toXML())
            messageBuilder.addExtension(xhtmlExtension)
        }
        else {
            // this is plain text so keep it as it is.
            messageBuilder.addBody(null, content)
        }
        sendMessage(messageBuilder)
    }

    /**
     * Sends the `message` with the json-message extension to the destination indicated by the `to` contact.
     *
     * json the json message to be sent.
     *
     * @throws OperationFailedException if sending the message fails for some reason.
     */
    @Throws(OperationFailedException::class)
    fun sendJsonMessage(json: String?) {
        val messageBuilder = StanzaBuilder.buildMessage().addExtension(JsonMessageExtension(json))
        sendMessage(messageBuilder)
    }

    /**
     * Sends the `message` to the destination `multiUserChat` chatRoom.
     *
     * messageBuilder the [Message] to be sent.
     *
     * @throws OperationFailedException if sending the message fails for some reason.
     */
    @Throws(OperationFailedException::class)
    private fun sendMessage(messageBuilder: MessageBuilder) {
        try {
            assertConnected()
            mMultiUserChat.sendMessage(messageBuilder)
        } catch (e: NotConnectedException) {
            Timber.e("Failed to send message: %s", e.message)
            throw OperationFailedException(aTalkApp.getResString(R.string.service_gui_SEND_MESSAGE_FAIL, messageBuilder.build()),
                OperationFailedException.GENERAL_ERROR, e)
        } catch (e: InterruptedException) {
            Timber.e("Failed to send message: %s", e.message)
            throw OperationFailedException(aTalkApp.getResString(R.string.service_gui_SEND_MESSAGE_FAIL, messageBuilder.build()),
                OperationFailedException.GENERAL_ERROR, e)
        }
    }

    override fun sendMessage(message: IMessage, omemoManager: OmemoManager) {
        val entityBareJid = mMultiUserChat.room
        var msgContent = message.getContent()
        var errMessage: String? = null
        try {
            var encryptedMessage = omemoManager.encrypt(mMultiUserChat, msgContent)
            var messageBuilder = StanzaBuilder.buildMessage()
            var sendMessage = encryptedMessage.buildMessage(messageBuilder, entityBareJid)
            if (IMessage.ENCODE_HTML == message.getMimeType()) {
                val xhtmlBody = encryptedMessage.element.toXML().toString()
                val xhtmlText = XHTMLText("", "us")
                    .append(xhtmlBody)
                    .appendCloseBodyTag()

                // OMEMO normal body message content will strip off any html tags info
                msgContent = Html.fromHtml(msgContent).toString()
                encryptedMessage = omemoManager.encrypt(mMultiUserChat, msgContent)
                messageBuilder = StanzaBuilder.buildMessage()
                // Add the XHTML text to the message
                XHTMLManager.addBody(messageBuilder, xhtmlText)
                sendMessage = encryptedMessage.buildMessage(messageBuilder, entityBareJid)
            }

            // proceed to send message if no exceptions.
            // sendMessage.setStanzaId(message.getMessageUID())
            mMultiUserChat.sendMessage(sendMessage.asBuilder())

            // Delivered message for own outgoing message view display
            message.setServerMsgId(sendMessage.stanzaId)
            message.setReceiptStatus(ChatMessage.MESSAGE_DELIVERY_CLIENT_SENT)
            val msgDeliveredEvt = ChatRoomMessageDeliveredEvent(this, Date(), message, ChatMessage.MESSAGE_MUC_OUT)
            fireMessageEvent(msgDeliveredEvt)
        } catch (e: UndecidedOmemoIdentityException) {
            val omemoAuthListener = OmemoAuthenticateListener(message, omemoManager)
            val ctx = aTalkApp.globalContext
            ctx.startActivity(OmemoAuthenticateDialog.createIntent(ctx, omemoManager, e.undecidedDevices, omemoAuthListener))
            return
        } catch (e: NoOmemoSupportException) {
            errMessage = aTalkApp.getResString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, "NoOmemoSupportException")
        } catch (e: CryptoFailedException) {
            errMessage = aTalkApp.getResString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message)
        } catch (e: InterruptedException) {
            errMessage = aTalkApp.getResString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message)
        } catch (e: NotConnectedException) {
            errMessage = aTalkApp.getResString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message)
        } catch (e: NoResponseException) {
            errMessage = aTalkApp.getResString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message)
        } catch (e: XMPPErrorException) {
            errMessage = aTalkApp.getResString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message)
        } catch (e: IOException) {
            errMessage = aTalkApp.getResString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.message)
        } catch (e: NotLoggedInException) {
            errMessage = aTalkApp.getResString(R.string.service_gui_MSG_SEND_CONNECTION_PROBLEM)
        }
        if (!TextUtils.isEmpty(errMessage)) {
            Timber.w("%s", errMessage)
            val failedEvent = ChatRoomMessageDeliveryFailedEvent(this,
                null, MessageDeliveryFailedEvent.OMEMO_SEND_ERROR, System.currentTimeMillis(), errMessage!!, message)
            fireMessageEvent(failedEvent)
        }
    }

    /**
     * Omemo listener callback on user authentication for undecided omemoDevices
     */
    private inner class OmemoAuthenticateListener(
            var message: IMessage,
            var omemoManager: OmemoManager,
    ) : OmemoAuthenticateDialog.AuthenticateListener {
        override fun onAuthenticate(allTrusted: Boolean, omemoDevices: Set<OmemoDevice>?) {
            if (allTrusted) {
                sendMessage(message, omemoManager)
            }
            else {
                val errMessage = aTalkApp.getResString(R.string.omemo_send_error,
                    "Undecided Omemo Identity: " + omemoDevices.toString())
                Timber.w("%s", errMessage)
                val failedEvent = ChatRoomMessageDeliveryFailedEvent(this@ChatRoomJabberImpl,
                    null, MessageDeliveryFailedEvent.OMEMO_SEND_ERROR, System.currentTimeMillis(), errMessage, message)
                fireMessageEvent(failedEvent)
            }
        }
    }

    /**
     * Sets the subject of this chat room.
     *
     * subject the new subject that we'd like this room to have
     *
     * @throws OperationFailedException throws Operation Failed Exception
     */
    @Throws(OperationFailedException::class)
    override fun setSubject(subject: String?) {
        try {
            mMultiUserChat.changeSubject(subject)
        } catch (ex: XMPPException) {
            val errMsg = "Failed to change subject for chat room" + getName()
            Timber.e(ex, "%s: %s", errMsg, ex.message)
            throw OperationFailedException(errMsg, OperationFailedException.NOT_ENOUGH_PRIVILEGES, ex)
        } catch (ex: NoResponseException) {
            val errMsg = "Failed to change subject for chat room" + getName()
            Timber.e(ex, "%s: %s", errMsg, ex.message)
            throw OperationFailedException(errMsg, OperationFailedException.NOT_ENOUGH_PRIVILEGES, ex)
        } catch (ex: NotConnectedException) {
            val errMsg = "Failed to change subject for chat room" + getName()
            Timber.e(ex, "%s: %s", errMsg, ex.message)
            throw OperationFailedException(errMsg, OperationFailedException.NOT_ENOUGH_PRIVILEGES, ex)
        } catch (ex: InterruptedException) {
            val errMsg = "Failed to change subject for chat room" + getName()
            Timber.e(ex, "%s: %s", errMsg, ex.message)
            throw OperationFailedException(errMsg, OperationFailedException.NOT_ENOUGH_PRIVILEGES, ex)
        }
    }

    /**
     * Returns a reference to the mProvider that created this room.
     *
     * @return a reference to the `ProtocolProviderService` instance that created this room.
     */
    override fun getParentProvider(): ProtocolProviderService {
        return mPPS
    }

    /**
     * Returns the local user's role in the context of this chat room if currently joined.
     * Else retrieve from the value in DB that was previously saved, or `null` if none
     *
     * Always save a copy of the userRole retrieved online in DB for later use
     *
     * @return ChatRoomMemberRole
     */
    override fun getUserRole(): ChatRoomMemberRole? {
        if (mUserRole == null) {
            // return role as GUEST if participant has not joined the chatRoom i.e. nickName == null
            val nick = mMultiUserChat.nickname
            if (nick == null) {
                val role = getChatRoomProperty(mPPS, getName(), ChatRoom.USER_ROLE)
                mUserRole = if (StringUtils.isEmpty(role)) null else ChatRoomMemberRole.fromString(role!!)
            }
            else {
                val participant = JidCreate.entityFullFrom(getIdentifier(), nick)
                val o = mMultiUserChat.getOccupant(participant)
                if (o != null) {
                    mUserRole = smackRoleToScRole(o.role, o.affiliation)
                    updateChatRoomProperty(mPPS, getName(), ChatRoom.USER_ROLE, mUserRole!!.roleName)
                }
            }
        }
        // Timber.d("ChatRoom user role: %s", mUserRole)
        return mUserRole
    }

    /**
     * Sets the new mRole for the local user in the context of this chatRoom.
     *
     * role the new mRole to be set for the local user
     */
    override fun setLocalUserRole(role: ChatRoomMemberRole) {
        setLocalUserRole(role, false)
    }

    /**
     * Sets the new mRole for the local user in the context of this chatRoom.
     *
     * role the new mRole to be set for the local user
     * isInitial if `true` this is initial mRole set.
     */
    private fun setLocalUserRole(role: ChatRoomMemberRole?, isInitial: Boolean) {
        fireLocalUserRoleEvent(getUserRole(), role, isInitial)

        // update only after getUserRole() to local variable and save to database
        mUserRole = role
        if (!isInitial) updateChatRoomProperty(mPPS, getName(), ChatRoom.USER_ROLE, role!!.roleName)
    }

    /**
     * Instances of this class should be registered as `ParticipantStatusListener` in smack
     * and translates events .
     */
    private inner class MemberListener : ParticipantStatusListener {
        /**
         * Called when a new room occupant has joined the room. Note: Take in consideration that
         * when you join a room you will receive the list of current occupants in the room. This
         * message will be sent for each occupant.
         *
         * participant the participant that has just joined the room (e.g.
         * room@conference.jabber.org/nick).
         */
        override fun joined(participant: EntityFullJid) {
            Timber.i("%s has joined chatRoom: %s", participant, getName())

            // We try to get the nickname of the participantName in case it's in the form john@servicename.com,
            // because the nickname we keep in the nickname property is just the user name like "john".
            val participantNick = participant.resourceOrThrow

            // when somebody changes its nickname we first receive event for its nickname changed
            // and after that that has joined we check if this already joined and if so we skip it
            // Note: mNickName may be null so order of equals is important
            if (participantNick != mNickName && !members.containsKey(participantNick)) {
                val reason = if (mucOwnPresenceReceived) ChatRoomMemberPresenceChangeEvent.MEMBER_JOINED else ChatRoomMemberPresenceChangeEvent.REASON_USER_LIST

                // smack returns fully qualified occupant names.
                val occupant = mMultiUserChat.getOccupant(participant)
                val member = ChatRoomMemberJabberImpl(this@ChatRoomJabberImpl, occupant.nick, occupant.jid)
                members[participantNick] = member
                // REASON_USER_LIST reason will not show participant 'has joined' in chat window
                fireMemberPresenceEvent(member, ChatRoomMemberPresenceChangeEvent.MEMBER_JOINED, reason)
            }
        }

        /**
         * Called when a room occupant has left the room on its own.
         * This means that the occupant was neither being kicked nor banned from the room.
         *
         * participant the participant that has left the room on its own.
         * (e.g. room@conference.jabber.org/nick).
         */
        override fun left(participant: EntityFullJid) {
            Timber.i("%s has left the chat room: %s", participant, getName())
            val member: ChatRoomMember? = findMemberFromParticipant(participant)
            if (member != null) {
                synchronized(members) { members.remove(participant.resourceOrThrow) }
                fireMemberPresenceEvent(member, ChatRoomMemberPresenceChangeEvent.MEMBER_LEFT, null)
            }
        }

        /**
         * Called when a room participant has been kicked from the room.
         * This means that the kicked participant is no longer participating in the room.
         *
         * participant the participant that was kicked from the room (e.g. room@conference.jabber.org/nick).
         * actor the moderator that kicked the occupant from the room (e.g. user@host.org).
         * reason the reason provided by the actor to kick the occupant from the room.
         */
        override fun kicked(participant: EntityFullJid, actor: Jid, reason: String) {
            val member: ChatRoomMember? = findMemberFromParticipant(participant)
            if (member != null) {
                synchronized(members) { members.remove(participant.resourceOrThrow) }
                fireMemberPresenceEvent(member, actor, ChatRoomMemberPresenceChangeEvent.MEMBER_KICKED, reason)
            }
        }

        /**
         * Called when a moderator grants voice to a visitor. This means that the visitor can now
         * participate in the moderated room sending messages to all occupants.
         *
         * participant the participant that was granted voice in the room (e.g.
         * room@conference.jabber.org/nick).
         */
        override fun voiceGranted(participant: EntityFullJid) {
            val member = findMemberFromParticipant(participant)
            if (member != null) fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.MEMBER)
        }

        /**
         * Called when a moderator revokes voice from a participant. This means that the
         * participant in the room was able to speak and now is a visitor that can't send
         * messages to the room occupants.
         *
         * participant the participant that was revoked voice from the room e.g. room@conference.jabber.org/nick
         */
        override fun voiceRevoked(participant: EntityFullJid) {
            val member = findMemberFromParticipant(participant)
            if (member != null) fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.SILENT_MEMBER)
        }

        /**
         * Called when an administrator or owner banned a participant from the room. This means
         * that banned participant will no longer be able to join the room unless the ban has been
         * removed.
         *
         * participant the participant that was banned from the room (e.g.
         * room@conference.jabber.org/nick).
         * actor the administrator that banned the occupant (e.g. user@host.org).
         * reason the reason provided by the administrator to ban the occupant.
         */
        override fun banned(participant: EntityFullJid, actor: Jid, reason: String) {
            Timber.i("%s has been banned from chat room: %s", participant, getName())
            val member = findMemberFromParticipant(participant)
            if (member != null) {
                val nick = participant.resourceOrThrow
                synchronized(members) { members.remove(nick) }
                banList[nick] = member
                fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.OUTCAST)
            }
        }

        /**
         * Called when an administrator grants a user membership to the room. This means that the
         * user will be able to join the members-only room.
         *
         * participant the participant that was granted membership in the room e.g. room@conference.jabber.org/nick
         */
        override fun membershipGranted(participant: EntityFullJid) {
            val member = findMemberFromParticipant(participant)
            if (member != null) fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.MEMBER)
        }

        /**
         * Called when an administrator revokes a user membership to the room. This means that the
         * user will not be able to join the members-only room.
         *
         * participant the participant that was revoked membership from the room (e.g.
         * room@conference.jabber.org/nick).
         */
        override fun membershipRevoked(participant: EntityFullJid) {
            val member = findMemberFromParticipant(participant)
            if (member != null) fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.GUEST)
        }

        /**
         * Called when an administrator grants moderator privileges to a user. This means that the
         * user will be able to kick users, grant and revoke voice, invite other users, modify
         * room's subject plus all the participants privileges.
         *
         * participant the participant that was granted moderator privileges in the room (e.g.
         * room@conference.jabber.org/nick).
         */
        override fun moderatorGranted(participant: EntityFullJid) {
            val member = findMemberFromParticipant(participant)
            if (member != null) fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.MODERATOR)
        }

        /**
         * Called when an administrator revokes moderator privileges from a user. This means that
         * the user will no longer be able to kick users, grant and revoke voice, invite other
         * users, modify room's subject plus all the participants privileges.
         *
         * participant the participant that was revoked moderator privileges in the room (e.g.
         * room@conference.jabber.org/nick).
         */
        override fun moderatorRevoked(participant: EntityFullJid) {
            val member = findMemberFromParticipant(participant)
            if (member != null) fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.MEMBER)
        }

        /**
         * Called when an owner grants a user ownership on the room. This means that the user will
         * be able to change defining room features as well as perform all administrative functions.
         *
         * participant the participant that was granted ownership on the room (e.g.
         * room@conference.jabber.org/nick).
         */
        override fun ownershipGranted(participant: EntityFullJid) {
            val member = findMemberFromParticipant(participant)
            if (member != null) fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.OWNER)
        }

        /**
         * Called when an owner revokes a user ownership on the room. This means that the user will
         * no longer be able to change defining room features as well as perform all administrative functions.
         *
         * participant the participant that was revoked ownership on the room (e.g.
         * room@conference.jabber.org/nick).
         */
        override fun ownershipRevoked(participant: EntityFullJid) {
            val member = findMemberFromParticipant(participant)
            if (member != null) fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.MEMBER)
        }

        /**
         * Called when an owner grants administrator privileges to a user. This means that the user
         * will be able to perform administrative functions such as banning users and edit
         * moderator list.
         *
         * participant the participant that was granted administrator privileges
         * (e.g. room@conference.jabber.org/nick).
         */
        override fun adminGranted(participant: EntityFullJid) {
            val member = findMemberFromParticipant(participant)
            if (member != null) fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.ADMINISTRATOR)
        }

        /**
         * Called when an owner revokes administrator privileges from a user. This means that the
         * user will no longer be able to perform administrative functions such as banning users
         * and edit moderator list.
         *
         * participant the participant that was revoked administrator privileges (e.g.
         * room@conference.jabber.org/nick).
         */
        override fun adminRevoked(participant: EntityFullJid) {
            val member = findMemberFromParticipant(participant)
            if (member != null) fireMemberRoleEvent(member, member.getCurrentRole(), ChatRoomMemberRole.MEMBER)
        }

        /**
         * Called when a participant changed his/her nickname in the room. The new participant's
         * nickname will be informed with the next available presence.
         *
         * participant the participant that has changed his nickname
         * newNickname the new nickname that the participant decided to use.
         */
        override fun nicknameChanged(participant: EntityFullJid, newNickname: Resourcepart) {
            val member = findMemberFromParticipant(participant) ?: return

            // update local mNickName if from own NickName change
            if (mNickName == member.getNickAsResourcepart()) mNickName = newNickname
            member.setNick(newNickname)
            synchronized(members) {

                // change the member key
                val mem = members.remove(participant.resourceOrThrow)
                members.put(newNickname, mem)
            }
            val evt = ChatRoomMemberPropertyChangeEvent(member,
                this@ChatRoomJabberImpl, ChatRoomMemberPropertyChangeEvent.MEMBER_NICKNAME,
                participant.resourceOrThrow, newNickname)
            fireMemberPropertyChangeEvent(evt)
        }
    }

    /**
     * Adds a listener that will be notified of changes in our mRole in the room such as us being
     * granted operator.
     *
     * listener a local user mRole listener.
     */
    override fun addLocalUserRoleListener(listener: ChatRoomLocalUserRoleListener) {
        synchronized(localUserRoleListeners) { if (!localUserRoleListeners.contains(listener)) localUserRoleListeners.add(listener) }
    }

    /**
     * Removes a listener that was being notified of changes in our mRole in this chat room such as
     * us being granted operator.
     *
     * listener a local user mRole listener.
     */
    override fun removeLocalUserRoleListener(listener: ChatRoomLocalUserRoleListener) {
        synchronized(localUserRoleListeners) { localUserRoleListeners.remove(listener) }
    }

    /**
     * Adds a packet extension which will be added to every presence we sent.
     *
     * ext the extension we want to add.
     */
    fun addPresencePacketExtensions(ext: ExtensionElement) {
        synchronized(presencePacketExtensions) { if (!presencePacketExtensions.contains(ext)) presencePacketExtensions.add(ext) }
    }

    /**
     * Removes a packet extension from the list of extensions we add to every presence we send.
     *
     * ext the extension we want to remove.
     */
    fun removePresencePacketExtensions(ext: ExtensionElement) {
        synchronized(presencePacketExtensions) { presencePacketExtensions.remove(ext) }
    }

    /**
     * Adds a listener that will be notified of changes of a member mRole in the room such as being granted operator.
     *
     * listener a member mRole listener.
     */
    override fun addMemberRoleListener(listener: ChatRoomMemberRoleListener) {
        synchronized(memberRoleListeners) { if (!memberRoleListeners.contains(listener)) memberRoleListeners.add(listener) }
    }

    /**
     * Removes a listener that was being notified of changes of a member mRole in this chat room
     * such as us being granted operator.
     *
     * listener a member mRole listener.
     */
    override fun removeMemberRoleListener(listener: ChatRoomMemberRoleListener) {
        synchronized(memberRoleListeners) { memberRoleListeners.remove(listener) }
    }

    /**
     * Returns the list of banned users.
     *
     * @return a list of all banned participants
     */
    override fun getBanList(): Iterator<ChatRoomMember> {
        return banList.values.iterator()
    }

    /**
     * Changes the local user nickname. If the new nickname already exist in the chat room
     * throws an OperationFailedException.
     *
     * nickname the new nickname within the room.
     *
     * @throws OperationFailedException if the new nickname already exist in this room
     */
    @Throws(OperationFailedException::class)
    override fun setUserNickname(nickname: String?) {
        // parseLocalPart or take nickname as it
        val sNickname = nickname!!.split("@")[0]
        try {
            mNickName = Resourcepart.from(sNickname)
            mMultiUserChat.changeNickname(mNickName)
        } catch (e: XMPPException) {
            val msg = "Failed to change nickname for chat room: " + getName() + " => " + e.message
            Timber.e("%s", msg)
            throw OperationFailedException(msg, OperationFailedException.IDENTIFICATION_CONFLICT)
        } catch (e: NoResponseException) {
            val msg = "Failed to change nickname for chat room: " + getName() + " => " + e.message
            Timber.e("%s", msg)
            throw OperationFailedException(msg, OperationFailedException.IDENTIFICATION_CONFLICT)
        } catch (e: NotConnectedException) {
            val msg = "Failed to change nickname for chat room: " + getName() + " => " + e.message
            Timber.e("%s", msg)
            throw OperationFailedException(msg, OperationFailedException.IDENTIFICATION_CONFLICT)
        } catch (e: XmppStringprepException) {
            val msg = "Failed to change nickname for chat room: " + getName() + " => " + e.message
            Timber.e("%s", msg)
            throw OperationFailedException(msg, OperationFailedException.IDENTIFICATION_CONFLICT)
        } catch (e: MultiUserChatException.MucNotJoinedException) {
            val msg = "Failed to change nickname for chat room: " + getName() + " => " + e.message
            Timber.e("%s", msg)
            throw OperationFailedException(msg, OperationFailedException.IDENTIFICATION_CONFLICT)
        } catch (e: InterruptedException) {
            val msg = "Failed to change nickname for chat room: " + getName() + " => " + e.message
            Timber.e("%s", msg)
            throw OperationFailedException(msg, OperationFailedException.IDENTIFICATION_CONFLICT)
        }
    }

    /**
     * Bans a user from the room. An admin or owner of the room can ban users from a room.
     *
     * member the `ChatRoomMember` to be banned.
     * reason the reason why the user was banned.
     *
     * @throws OperationFailedException if an error occurs while banning a user. In particular, an error can occur if a
     * moderator or a user with an affiliation of "owner" or "admin" was tried to be banned
     * or if the user that is banning have not enough permissions to ban.
     */
    @Throws(OperationFailedException::class)
    override fun banParticipant(chatRoomMember: ChatRoomMember, reason: String) {
        try {
            val jid = (chatRoomMember as ChatRoomMemberJabberImpl?)!!.getJabberId()
            mMultiUserChat.banUser(jid, reason)
        } catch (e: XMPPErrorException) {
            Timber.e(e, "Failed to ban participant.")

            // If a moderator or a user with an affiliation of "owner" or "admin" was intended to be kicked.
            if (e.stanzaError.condition == StanzaError.Condition.not_allowed) {
                throw OperationFailedException("Kicking an admin user or a chat room owner is a forbidden operation.",
                    OperationFailedException.FORBIDDEN)
            }
            else {
                throw OperationFailedException("An error occurred while trying to kick the participant.",
                    OperationFailedException.GENERAL_ERROR)
            }
        } catch (e: NoResponseException) {
            throw OperationFailedException("An error occurred while trying to kick the participant.",
                OperationFailedException.GENERAL_ERROR)
        } catch (e: NotConnectedException) {
            throw OperationFailedException("An error occurred while trying to kick the participant.",
                OperationFailedException.GENERAL_ERROR)
        } catch (e: InterruptedException) {
            throw OperationFailedException("An error occurred while trying to kick the participant.",
                OperationFailedException.GENERAL_ERROR)
        }
    }

    /**
     * Kicks a participant from the room.
     *
     * member the `ChatRoomMember` to kick from the room
     * reason the reason why the participant is being kicked from the room
     *
     * @throws OperationFailedException if an error occurs while kicking the participant. In particular, an error can occur
     * if a moderator or a user with an affiliation of "owner" or "admin" was intended to be kicked
     * or if the participant that intended to kick another participant does not have kicking privileges
     */
    @Throws(OperationFailedException::class)
    override fun kickParticipant(chatRoomMember: ChatRoomMember, reason: String) {
        try {
            val nick = (chatRoomMember as ChatRoomMemberJabberImpl?)!!.getNickAsResourcepart()
            mMultiUserChat.kickParticipant(nick, reason)
        } catch (e: XMPPErrorException) {
            Timber.e(e, "Failed to kick participant: %s.", e.message)

            // If a moderator or a user with an affiliation of "owner" or "admin" was intended to be kicked.
            when (e.stanzaError.condition) {
                StanzaError.Condition.not_allowed -> {
                    throw OperationFailedException(
                        "Kicking an admin user or a chat room owner is a forbidden operation.",
                        OperationFailedException.FORBIDDEN)
                }
                StanzaError.Condition.forbidden -> {
                    throw OperationFailedException(
                        "The user that intended to kick another participant does not have enough privileges to do that.",
                        OperationFailedException.NOT_ENOUGH_PRIVILEGES)
                }
                else -> {
                    throw OperationFailedException("An error occurred while trying to kick the participant. "
                            + e.message, OperationFailedException.GENERAL_ERROR)
                }
            }
        } catch (e: Exception) {
            throw OperationFailedException("An error occurred while trying to kick the participant. "
                    + e.message, OperationFailedException.GENERAL_ERROR)
        }
    }

    /**
     * Creates the corresponding ChatRoomMemberPresenceChangeEvent and notifies all
     * `ChatRoomMemberPresenceListener`s that a ChatRoomMember has joined or left this
     * `ChatRoom`.
     *
     * member the `ChatRoomMember` that this
     * eventID the identifier of the event
     * eventReason the reason of the event
     */
    private fun fireMemberPresenceEvent(member: ChatRoomMember?, eventID: String, eventReason: String?) {
        val evt = ChatRoomMemberPresenceChangeEvent(this, member!!, eventID, eventReason)
        Timber.log(TimberLog.FINER, "Will dispatch the following ChatRoom event: %s", evt)

        var listeners: Iterable<ChatRoomMemberPresenceListener>
        synchronized(memberListeners) { listeners = ArrayList(memberListeners) }
        for (listener in listeners) {
            listener.memberPresenceChanged(evt)
        }
    }

    /**
     * Creates the corresponding ChatRoomMemberPresenceChangeEvent and notifies all
     * `ChatRoomMemberPresenceListener`s that a ChatRoomMember has joined or left this `ChatRoom`.
     *
     * member the `ChatRoomMember` that changed its presence status
     * actor the `ChatRoomMember` that participated as an actor in this event
     * eventID the identifier of the event
     * eventReason the reason of this event
     */
    private fun fireMemberPresenceEvent(member: ChatRoomMember, actor: Jid, eventID: String, eventReason: String) {
        val evt = ChatRoomMemberPresenceChangeEvent(this, member, actor, eventID, eventReason)
        Timber.log(TimberLog.FINER, "Will dispatch the following ChatRoom event: %s", evt)
        var listeners: Iterable<ChatRoomMemberPresenceListener>
        synchronized(memberListeners) { listeners = ArrayList(memberListeners) }
        for (listener in listeners) listener.memberPresenceChanged(evt)
    }

    /**
     * Creates the corresponding ChatRoomMemberRoleChangeEvent and notifies all
     * `ChatRoomMemberRoleListener`s that a ChatRoomMember has changed its mRole in this `ChatRoom`.
     *
     * member the `ChatRoomMember` that has changed its mRole
     * previousRole the previous mRole that member had
     * newRole the new mRole the member get
     */
    private fun fireMemberRoleEvent(
            member: ChatRoomMember, previousRole: ChatRoomMemberRole?,
            newRole: ChatRoomMemberRole,
    ) {
        member.setRole(newRole)
        val evt = ChatRoomMemberRoleChangeEvent(this, member, previousRole, newRole)
        Timber.log(TimberLog.FINER, "Will dispatch the following ChatRoom event: %s", evt)
        var listeners: Iterable<ChatRoomMemberRoleListener>
        synchronized(memberRoleListeners) { listeners = ArrayList(memberRoleListeners) }
        for (listener in listeners) listener.memberRoleChanged(evt)
    }

    /**
     * Delivers the specified event to all registered message listeners.
     *
     * evt the `EventObject` that we'd like delivered to all registered message listeners.
     */
    fun fireMessageEvent(evt: EventObject) {
        var listeners: Iterable<ChatRoomMessageListener>
        synchronized(messageListeners) { listeners = ArrayList(messageListeners) }
        for (listener in listeners) {
            try {
                if (evt is ChatRoomMessageDeliveredEvent) {
                    listener.messageDelivered(evt)
                }
                else if (evt is ChatRoomMessageReceivedEvent) {
                    listener.messageReceived(evt)
                }
                else if (evt is ChatRoomMessageDeliveryFailedEvent) {
                    listener.messageDeliveryFailed(evt)
                }
            } catch (e: Throwable) {
                Timber.e(e, "Error delivering multi chat message for %s", listener)
            }
        }
    }

    /**
     * Publishes a conference to the room by sending a `Presence` IQ which contains a
     * `ConferenceDescriptionExtensionElement`
     *
     * cd the description of the conference to announce
     * name the name of the conference
     *
     * @return the `ConferenceDescription` that was announced (e.g. `cd` on
     * success or `null` on failure)
     */
    override fun publishConference(cd: ConferenceDescription?, name: String?): ConferenceDescription? {
        var cd_ = cd
        if (publishedConference != null) {
            cd_ = publishedConference!!
            cd_.setAvailable(false)
        }
        else {
            val displayName: String? = if (TextUtils.isEmpty(name)) {
                aTalkApp.getResString(R.string.service_gui_CHAT_CONFERENCE_ITEM_LABEL, mNickName.toString())
            }
            else {
                name
            }
            cd_!!.setDisplayName(displayName)
        }
        val ext = ConferenceDescriptionExtension(cd_.getUri(), cd_.getUri(), cd_.getPassword())
        if (lastPresenceSent != null) {
            if (setPacketExtension(lastPresenceSent!!, ext, ConferenceDescriptionExtension.NAMESPACE)) {
                try {
                    sendLastPresence()
                } catch (e: NotConnectedException) {
                    Timber.w(e, "Could not publish conference")
                } catch (e: InterruptedException) {
                    Timber.w(e, "Could not publish conference")
                }
            }
            else {
                return null
            }
        }
        else {
            Timber.w("Could not publish conference, lastPresenceSent is null.")
            publishedConference = null
            publishedConferenceExt = null
            return null
        }
        /*
         * Save the extensions to set to other outgoing Presence packets
         */publishedConference = if (!cd_.isAvailable()) null else cd_
        publishedConferenceExt = if (publishedConference == null) null else ext
        fireConferencePublishedEvent(members[mNickName]!!, cd_,
            ChatRoomConferencePublishedEvent.CONFERENCE_DESCRIPTION_SENT)
        return cd_
    }

    /**
     * Publishes new status message in chat room presence.
     *
     * newStatus the new status message to be published in the MUC.
     */
    fun publishPresenceStatus(newStatus: String?) {
        if (lastPresenceSent != null) {
            lastPresenceSent!!.status = newStatus
            try {
                sendLastPresence()
            } catch (e: NotConnectedException) {
                Timber.e(e, "Could not publish presence")
            } catch (e: InterruptedException) {
                Timber.e(e, "Could not publish presence")
            }
        }
    }

    /**
     * Adds given `ExtensionElement` to the MUC presence and publishes it immediately.
     *
     * extension the `ExtensionElement` to be included in MUC presence.
     */
    fun sendPresenceExtension(extension: ExtensionElement) {
        if (lastPresenceSent != null && setPacketExtension(lastPresenceSent!!, extension, extension.namespace, true)) {
            try {
                sendLastPresence()
            } catch (e: NotConnectedException) {
                Timber.e(e, "Could not send presence")
            } catch (e: InterruptedException) {
                Timber.e(e, "Could not send presence")
            }
        }
    }

    /**
     * Removes given `PacketExtension` from the MUC presence and publishes it immediately.
     *
     * extension the `PacketExtension` to be removed from the MUC presence.
     */
    fun removePresenceExtension(extension: ExtensionElement) {
        if (lastPresenceSent != null && setPacketExtension(lastPresenceSent!!, null, extension.namespace)) {
            try {
                sendLastPresence()
            } catch (e: NotConnectedException) {
                Timber.e(e, "Could not remove presence")
            } catch (e: InterruptedException) {
                Timber.e(e, "Could not remove presence")
            }
        }
    }

    /**
     * Returns the ids of the users that has the member mRole in the room. When the room is member
     * only, this are the users allowed to join.
     *
     * @return the ids of the users that has the member mRole in the room.
     */
    override fun getMembersWhiteList(): MutableList<Jid> {
        val res: MutableList<Jid> = ArrayList()
        try {
            for (a in mMultiUserChat.members) {
                res.add(a.jid)
            }
        } catch (e: XMPPException) {
            Timber.e(e, "Cannot obtain members list")
        } catch (e: NoResponseException) {
            Timber.e(e, "Cannot obtain members list")
        } catch (e: NotConnectedException) {
            Timber.e(e, "Cannot obtain members list")
        } catch (e: InterruptedException) {
            Timber.e(e, "Cannot obtain members list")
        }
        return res
    }

    /**
     * Changes the list of users that has mRole member for this room. When the room is member only,
     * this are the users allowed to join.
     *
     * members the ids of user to have member mRole.
     */
    override fun setMembersWhiteList(members: List<Jid>) {
        try {
            val membersToRemove = getMembersWhiteList()
            membersToRemove.removeAll(members)
            if (membersToRemove.size > 0) mMultiUserChat.revokeMembership(membersToRemove)
            if (members.isNotEmpty()) mMultiUserChat.grantMembership(members)
        } catch (e: XMPPException) {
            Timber.e(e, "Cannot modify members list")
        } catch (e: NoResponseException) {
            Timber.e(e, "Cannot modify members list")
        } catch (e: NotConnectedException) {
            Timber.e(e, "Cannot modify members list")
        } catch (e: InterruptedException) {
            Timber.e(e, "Cannot modify members list")
        }
    }

    /**
     * Prepares and sends the last seen presence.
     * Removes the initial <x> extension and sets new id.
     *
     * @throws NotConnectedException
     * @throws InterruptedException
    </x> */
    @Throws(NotConnectedException::class, InterruptedException::class)
    private fun sendLastPresence() {
        // The initial presence sent by smack contains an empty "x" extension.
        // If this extension is included in a subsequent stanza, it indicates that the client lost its
        // synchronization and causes the MUC service to re-send the presence of each occupant in the room.
        lastPresenceSent!!.removeExtension(MUCInitialPresence.ELEMENT, MUCInitialPresence.NAMESPACE)
        lastPresenceSent = lastPresenceSent!!
            .asBuilder(StandardStanzaIdSource.DEFAULT.newStanzaId)
            .build()
        mPPS.connection!!.sendStanza(lastPresenceSent)
    }

    /**
     * A listener that listens for packets of type Message and fires an event to notifier
     * interesting parties that a message was received.
     */
    private inner class MucMessageListener : MessageListener {
        /**
         * The timestamp of the last history message sent to the UI. Do not send earlier or
         * messages with the same timestamp.
         */
        private var lsdMessageTime: Date? = null

        /**
         * Process a Message stanza.
         *
         * message Smack Message to process.
         */
        override fun processMessage(message: Message) {
            // Leave handling of omemo messages to onOmemoMessageReceived()
            if (message.hasExtension(OmemoElement.NAME_ENCRYPTED, OmemoConstants.OMEMO_NAMESPACE_V_AXOLOTL)) return

            // Captcha challenge body is in body extension
            var msgBody: String? = null
            val msgBodies = message.bodies
            if (msgBodies.isNotEmpty()) {
                for (body in msgBodies) {
                    if (body != null) {
                        msgBody = body.message
                        break
                    }
                }
            }
            // Check if the message is of Type.error if none in Message Body
            if (msgBody == null && message.type == Message.Type.error) {
                msgBody = ""
            }
            if (msgBody == null) return
            val timeStamp: Date
            val delayInfo = message.getExtension(DelayInformation::class.java)
            if (delayInfo != null) {
                timeStamp = delayInfo.stamp

                // This is a delayed chat room message, a history message for the room coming from
                // server. Lets check have we already shown this message and if this is the case
                // skip it otherwise save it as last seen delayed message
                if (lsdMessageTime == null) {
                    // initialise this from configuration
                    val sTimestamp = getChatRoomProperty(mPPS, getName(),
                        Companion.LAST_SEEN_DELAYED_MESSAGE_PROP)
                    try {
                        if (!TextUtils.isEmpty(sTimestamp)) lsdMessageTime = Date(sTimestamp!!.toLong())
                    } catch (ex: Throwable) {
                        Timber.w("TimeStamp property is null! %s", timeStamp)
                    }
                }
                if (lsdMessageTime != null && !timeStamp.after(lsdMessageTime)) return

                // save it in configuration
                updateChatRoomProperty(mPPS, getName(),
                    Companion.LAST_SEEN_DELAYED_MESSAGE_PROP, timeStamp.time.toString())
                lsdMessageTime = timeStamp
            }
            else {
                timeStamp = Date()
            }

            // for delay message only
            var jabberID = message.from
            val mAddress = message.getExtension(MultipleAddresses::class.java)
            if (mAddress != null) {
                val addresses = mAddress.getAddressesOfType(MultipleAddresses.Type.ofrom)
                jabberID = addresses[0].jid.asBareJid()
            }
            var member: ChatRoomMember?
            var messageReceivedEventType = ChatMessage.MESSAGE_MUC_IN
            val entityJid = message.from // chatRoom entityJid
            val fromNick = entityJid.resourceOrNull

            // when the message comes from the room itself, it is a system message
            if (entityJid.equals(getName())) {
                messageReceivedEventType = ChatMessage.MESSAGE_SYSTEM
                member = ChatRoomMemberJabberImpl(this@ChatRoomJabberImpl, Resourcepart.EMPTY, getIdentifier())
            }
            else {
                member = members[entityJid.resourceOrThrow]
            }

            // sometimes when connecting to rooms they send history when the member is no longer
            // available we create a fake one so the messages to be displayed.
            if (member == null) {
                member = ChatRoomMemberJabberImpl(this@ChatRoomJabberImpl, fromNick, jabberID)
            }

            // set up default in case XHTMLExtension contains no message
            // if msgBody contains markup text then set as ENCODE_HTML mode
            var encType = IMessage.ENCODE_PLAIN
            if (msgBody.matches(ChatMessage.HTML_MARKUP)) {
                encType = IMessage.ENCODE_HTML
            }
            var newMessage = createMessage(msgBody, encType, null)

            // check if the message is available in xhtml
            val xhtmString = getXhtmlExtension(message)
            if (xhtmString != null) {
                newMessage = createMessage(xhtmString, IMessage.ENCODE_HTML, null)
            }
            newMessage.setRemoteMsgId(message.stanzaId)
            if (message.type == Message.Type.error) {
                Timber.d("Message error received from: %s", jabberID)
                val error = message.error
                var errorReason = error.conditionText
                if (TextUtils.isEmpty(errorReason)) {
                    // errorReason = error.getDescriptiveText()
                    errorReason = error.toString()
                }

                // Failed Event error
                var errorResultCode = if (ChatMessage.MESSAGE_SYSTEM == messageReceivedEventType) MessageDeliveryFailedEvent.SYSTEM_ERROR_MESSAGE else MessageDeliveryFailedEvent.UNKNOWN_ERROR
                val errorCondition = error.condition
                if (StanzaError.Condition.service_unavailable == errorCondition) {
                    if (!member.getPresenceStatus()!!.isOnline) {
                        errorResultCode = MessageDeliveryFailedEvent.OFFLINE_MESSAGES_NOT_SUPPORTED
                    }
                }
                else if (StanzaError.Condition.not_acceptable == errorCondition) {
                    errorResultCode = MessageDeliveryFailedEvent.NOT_ACCEPTABLE
                }
                val failedEvent = ChatRoomMessageDeliveryFailedEvent(this@ChatRoomJabberImpl,
                    member, errorResultCode, System.currentTimeMillis(), errorReason!!, newMessage)
                fireMessageEvent(failedEvent)
                return
            }

            // Check received message for sent message: either a delivery report or a message coming from the
            // chaRoom server. Checking using nick OR jid in case user join with a different nick.
            Timber.d("Received room message %s", message.toString())
            if (getUserNickname() != null && getUserNickname() == fromNick || jabberID != null && jabberID.equals(getAccountId(member.getChatRoom()))) {

                // MUC received message may be relayed from server on message sent hence reCreate the message if required
                if (IMessage.FLAG_REMOTE_ONLY == mEncType and IMessage.FLAG_MODE_MASK) {
                    newMessage = createMessage(msgBody, mEncType, "")
                    newMessage.setRemoteMsgId(message.stanzaId)
                }

                // message delivered for own outgoing message view display
                newMessage.setServerMsgId(message.stanzaId)
                newMessage.setReceiptStatus(ChatMessage.MESSAGE_DELIVERY_CLIENT_SENT)
                val msgDeliveredEvt = ChatRoomMessageDeliveredEvent(
                    this@ChatRoomJabberImpl, timeStamp, newMessage, ChatMessage.MESSAGE_MUC_OUT)
                msgDeliveredEvt.setHistoryMessage(true)
                fireMessageEvent(msgDeliveredEvt)
            }
            else {
                // CONVERSATION_MESSAGE_RECEIVED or SYSTEM_MESSAGE_RECEIVED
                val msgReceivedEvt = ChatRoomMessageReceivedEvent(
                    this@ChatRoomJabberImpl, member, timeStamp, newMessage, messageReceivedEventType)
                if (messageReceivedEventType == ChatMessage.MESSAGE_MUC_IN
                        && newMessage.getContent()!!.contains(getUserNickname().toString() + ":")) {
                    msgReceivedEvt.setImportantMessage(true)
                }
                msgReceivedEvt.setHistoryMessage(delayInfo != null)
                fireMessageEvent(msgReceivedEvt)
            }
        }
    }

    /**
     * A listener that is fired anytime a MUC room changes its subject.
     */
    private inner class MucSubjectUpdatedListener : SubjectUpdatedListener {
        /**
         * Notification that subject has changed
         *
         * @param subject the new subject
         * @param from the sender from room participants
         */
        override fun subjectUpdated(subject: String?, from: EntityFullJid?) {
            // only fire event if subject has really changed, not for new one
            if (subject != oldSubject) {
                Timber.d("ChatRoom subject updated to '%s'", subject)
                val evt = ChatRoomPropertyChangeEvent(
                    this@ChatRoomJabberImpl, ChatRoomPropertyChangeEvent.CHAT_ROOM_SUBJECT, oldSubject, subject)
                firePropertyChangeEvent(evt)
            }
            // Keeps track of the subject.
            oldSubject = subject
        }
    }

    /**
     * A listener that is fired anytime self status in a room is changed, such as the
     * user being kicked, banned, or granted admin permissions.
     */
    private inner class LocalUserStatusListener : UserStatusListener {
        /**
         * Called when a moderator kicked your user from the room. This means that you are no
         * longer participating in the room.
         *
         * actor the moderator that kicked your user from the room (e.g. user@host.org).
         * reason the reason provided by the actor to kick you from the room.
         */
        override fun kicked(actor: Jid, reason: String) {
            aTalkApp.showToastMessage(R.string.service_gui_CR_MEMBER_KICK)
            opSetMuc!!.fireLocalUserPresenceEvent(this@ChatRoomJabberImpl,
                LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_KICKED, reason)
            leave()
        }

        /**
         * Called when a moderator grants voice to your user. This means that you were a visitor in the
         * moderated room before and now you can participate in the room by sending messages to all occupants.
         */
        override fun voiceGranted() {
            setLocalUserRole(ChatRoomMemberRole.MEMBER)
        }

        /**
         * Called when a moderator revokes voice from your user. This means that you were a participant in the
         * room able to speak and now you are a visitor that can't send messages to the room occupants.
         */
        override fun voiceRevoked() {
            setLocalUserRole(ChatRoomMemberRole.SILENT_MEMBER)
        }

        /**
         * Called when an administrator or owner banned your user from the room. This means that
         * you will no longer be able to join the room unless the ban has been removed.
         *
         * actor the administrator that banned your user (e.g. user@host.org).
         * reason the reason provided by the administrator to banned you.
         */
        override fun banned(actor: Jid, reason: String) {
            opSetMuc!!.fireLocalUserPresenceEvent(this@ChatRoomJabberImpl,
                LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_DROPPED, reason)
            leave()
        }

        /**
         * Called when an administrator grants your user membership to the room. This means that
         * you will be able to join the members-only room.
         */
        override fun membershipGranted() {
            setLocalUserRole(ChatRoomMemberRole.MEMBER)
        }

        /**
         * Called when an administrator revokes your user membership to the room. This means that
         * you will not be able to join the members-only room.
         */
        override fun membershipRevoked() {
            setLocalUserRole(ChatRoomMemberRole.GUEST)
        }

        /**
         * Called when an administrator grants moderator privileges to your user. This means that
         * you will be able to kick users, grant and revoke voice, invite other users, modify
         * room's subject plus all the participants privileges.
         */
        override fun moderatorGranted() {
            setLocalUserRole(ChatRoomMemberRole.MODERATOR)
        }

        /**
         * Called when an administrator revokes moderator privileges from your user. This means
         * that you will no longer be able to kick users, grant and revoke voice, invite other
         * users, modify room's subject plus all the participants privileges.
         */
        override fun moderatorRevoked() {
            setLocalUserRole(ChatRoomMemberRole.MEMBER)
        }

        /**
         * Called when an owner grants to your user ownership on the room. This means that you will
         * be able to change defining room features as well as perform all administrative
         * functions.
         */
        override fun ownershipGranted() {
            aTalkApp.showToastMessage(R.string.service_gui_CR_MEMBER_GRANT_OWNER_PRIVILEGE)
            setLocalUserRole(ChatRoomMemberRole.OWNER)
        }

        /**
         * Called when an owner revokes from your user ownership on the room. This means that you
         * will no longer be able to change defining room features as well as perform all
         * administrative functions.
         */
        override fun ownershipRevoked() {
            aTalkApp.showToastMessage(R.string.service_gui_CR_MEMBER_REVOKE_OWNER_PRIVILEGE)
            setLocalUserRole(ChatRoomMemberRole.MEMBER)
        }

        /**
         * Called when an owner grants administrator privileges to your user. This means that you
         * will be able to perform administrative functions such as banning users and edit
         * moderator list.
         */
        override fun adminGranted() {
            setLocalUserRole(ChatRoomMemberRole.ADMINISTRATOR)
        }

        /**
         * Called when an owner revokes administrator privileges from your user. This means that
         * you will no longer be able to perform administrative functions such as banning users and
         * edit moderator list.
         */
        override fun adminRevoked() {
            setLocalUserRole(ChatRoomMemberRole.MEMBER)
        }

        /**
         * Called when the room is destroyed.
         *
         * alternateMUC an alternate MultiUserChat, may be null.
         * reason the reason why the room was destroyed, may be null.
         */
        override fun roomDestroyed(alternateMUC: MultiUserChat, reason: String) {
            Timber.d("CharRoom destroyed, alternate MUC: %s. Reason: %s", alternateMUC, reason)
        }
    }

    /**
     * Creates the corresponding ChatRoomLocalUserRoleChangeEvent and notifies all
     * `ChatRoomLocalUserRoleListener`s that local user's mRole has been changed in this
     * `ChatRoom`. Need to update LocalUser member Role in members list.
     *
     * previousRole the previous mRole that local user had
     * newRole the new mRole the local user gets
     * isInitial if `true` this is initial mRole set.
     */
    private fun fireLocalUserRoleEvent(
            previousRole: ChatRoomMemberRole?, newRole: ChatRoomMemberRole?,
            isInitial: Boolean,
    ) {
        if (mNickName != null) {
            val mUserLocal = members[mNickName]
            mUserLocal?.setRole(newRole)
        }
        val evt = ChatRoomLocalUserRoleChangeEvent(this, previousRole, newRole, isInitial)
        Timber.log(TimberLog.FINER, "Will dispatch the following ChatRoom event: %s", evt)
        var listeners: Iterable<ChatRoomLocalUserRoleListener>
        synchronized(localUserRoleListeners) { listeners = ArrayList(localUserRoleListeners) }
        for (listener in listeners) listener.localUserRoleChanged(evt)
    }

    /**
     * Delivers the specified event to all registered property change listeners.
     *
     * evt the `PropertyChangeEvent` that we'd like delivered to all registered property
     * change listeners.
     */
    private fun firePropertyChangeEvent(evt: PropertyChangeEvent) {
        var listeners: Iterable<ChatRoomPropertyChangeListener>
        synchronized(propertyChangeListeners) { listeners = ArrayList(propertyChangeListeners) }
        for (listener in listeners) {
            if (evt is ChatRoomPropertyChangeEvent) {
                listener.chatRoomPropertyChanged(evt)
            }
            else if (evt is ChatRoomPropertyChangeFailedEvent) {
                listener.chatRoomPropertyChangeFailed(evt)
            }
        }
    }

    /**
     * Delivers the specified event to all registered property change listeners.
     *
     * evt the `ChatRoomMemberPropertyChangeEvent` that we'd like deliver to all
     * registered member property change listeners.
     */
    private fun fireMemberPropertyChangeEvent(evt: ChatRoomMemberPropertyChangeEvent) {
        var listeners: Iterable<ChatRoomMemberPropertyChangeListener>
        synchronized(memberPropChangeListeners) { listeners = ArrayList(memberPropChangeListeners) }
        for (listener in listeners) listener.chatRoomPropertyChanged(evt)
    }

    /**
     * Utility method throwing an exception if the stack is not properly initialized.
     *
     * @throws java.lang.IllegalStateException if the underlying stack is not registered and initialized.
     */
    @Throws(IllegalStateException::class)
    private fun assertConnected() {
        check(mPPS.isRegistered) {
            ("The mProvider must be signed on the service before being able to communicate.")
        }
    }

    /**
     * Returns the `ChatRoomConfigurationForm` containing all configuration properties for
     * this chat room. If the user doesn't have permissions to see and change chat room
     * configuration an `OperationFailedException` is thrown.
     *
     * @return the `ChatRoomConfigurationForm` containing all configuration properties for this chat room
     * @throws OperationFailedException if the user doesn't have permissions to see and change chat room configuration
     */
    @Throws(OperationFailedException::class, InterruptedException::class)
    override fun getConfigurationForm(): ChatRoomConfigurationForm {
        // The corresponding configuration form.
        val configForm: ChatRoomConfigurationFormJabberImpl
        val smackConfigForm: Form
        try {
            smackConfigForm = mMultiUserChat.configurationForm
            configForm = ChatRoomConfigurationFormJabberImpl(mMultiUserChat, smackConfigForm)
        } catch (e: XMPPErrorException) {
            if (e.stanzaError.condition == StanzaError.Condition.forbidden) throw OperationFailedException(
                "Failed to obtain smack multi user chat config form. User doesn't have enough privileges to see the form.",
                OperationFailedException.NOT_ENOUGH_PRIVILEGES, e)
            else throw OperationFailedException(
                "Failed to obtain smack multi user chat config form.", OperationFailedException.GENERAL_ERROR, e)
        } catch (e: NoResponseException) {
            throw OperationFailedException(
                "Failed to obtain smack multi user chat config form.", OperationFailedException.GENERAL_ERROR, e)
        } catch (e: NotConnectedException) {
            throw OperationFailedException(
                "Failed to obtain smack multi user chat config form.", OperationFailedException.GENERAL_ERROR, e)
        }
        return configForm
    }

    /**
     * The Jabber multi user chat implementation doesn't support system rooms.
     *
     * @return false to indicate that the Jabber protocol implementation doesn't support system rooms.
     */
    override fun isSystem(): Boolean {
        return false
    }

    /**
     * Determines whether this chat room should be stored in the configuration file or not. If the
     * chat room is persistent it still will be shown after a restart in the chat room list. A
     * non-persistent chat room will only be in the chat room list until the the program is running.
     *
     * @return true if this chat room is persistent, false otherwise
     */
    override fun isPersistent(): Boolean {
        var persistent = false
        val room = mMultiUserChat.room
        try {
            // Do not use getRoomInfo, as it has bug and throws NPE
//            DiscoverInfo info = ServiceDiscoveryManager.getInstanceFor(mProvider.getConnection()).discoverInfo(room)
//            if (info != null)
//                persistent = info.containsFeature("muc_persistent")
            val roomInfo = MultiUserChatManager.getInstanceFor(mPPS.connection).getRoomInfo(room)
            persistent = roomInfo.isPersistent
        } catch (ex: Exception) {
            Timber.w("could not get persistent state for room '%s':%s", room, ex.message)
        }
        return persistent
    }

    /**
     * Finds the member of this chat room corresponding to the given nick name.
     *
     * nickName the nick name to search for.
     *
     * @return the member of this chat room corresponding to the given nick name.
     */
    fun findMemberForNickName(nickName: Resourcepart?): ChatRoomMemberJabberImpl? {
        synchronized(members) { return members[nickName] }
    }

    /**
     * Grants administrator privileges to another user. Room owners may grant administrator
     * privileges to a member or un-affiliated user. An administrator is allowed to perform
     * administrative functions such as banning users and edit moderator list.
     *
     * jid the bare XMPP user ID of the user to grant administrator privileges (e.g. "user@host.org").
     */
    override fun grantAdmin(address: String) {
        try {
            mMultiUserChat.grantAdmin(JidCreate.from(address))
        } catch (ex: XMPPException) {
            Timber.e(ex, "An error occurs granting administrator privileges to a user.")
        } catch (ex: NoResponseException) {
            Timber.e(ex, "An error occurs granting administrator privileges to a user.")
        } catch (ex: NotConnectedException) {
            Timber.e(ex, "An error occurs granting administrator privileges to a user.")
        } catch (ex: InterruptedException) {
            Timber.e(ex, "An error occurs granting administrator privileges to a user.")
        } catch (e: XmppStringprepException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Grants membership to a user. Only administrators are able to grant membership. A user that
     * becomes a room member will be able to enter a room of type Members-Only (i.e. a room that a
     * user cannot enter without being on the member list).
     *
     * jid the bare XMPP user ID of the user to grant membership privileges (e.g. "user@host.org").
     */
    override fun grantMembership(address: String) {
        try {
            mMultiUserChat.grantMembership(JidCreate.from(address))
        } catch (ex: XMPPException) {
            Timber.e(ex, "An error occurs granting membership to a user")
        } catch (ex: NoResponseException) {
            Timber.e(ex, "An error occurs granting membership to a user")
        } catch (ex: NotConnectedException) {
            Timber.e(ex, "An error occurs granting membership to a user")
        } catch (ex: InterruptedException) {
            Timber.e(ex, "An error occurs granting membership to a user")
        } catch (ex: XmppStringprepException) {
            Timber.e(ex, "An error occurs granting membership to a user")
        }
    }

    /**
     * Grants moderator privileges to a participant or visitor. Room administrators may grant
     * moderator privileges. A moderator is allowed to kick users, grant and revoke voice, invite
     * other users, modify room's subject plus all the participants privileges.
     *
     * nickname the nickname of the occupant to grant moderator privileges.
     */
    override fun grantModerator(nickname: String) {
        try {
            mMultiUserChat.grantModerator(Resourcepart.from(nickname))
        } catch (ex: XMPPException) {
            Timber.e(ex, "An error occurs granting moderator privileges to a user")
        } catch (ex: NoResponseException) {
            Timber.e(ex, "An error occurs granting moderator privileges to a user")
        } catch (ex: NotConnectedException) {
            Timber.e(ex, "An error occurs granting moderator privileges to a user")
        } catch (ex: InterruptedException) {
            Timber.e(ex, "An error occurs granting moderator privileges to a user")
        } catch (ex: XmppStringprepException) {
            Timber.e(ex, "An error occurs granting moderator privileges to a user")
        }
    }

    /**
     * Grants ownership privileges to another user. Room owners may grant ownership privileges.
     * Some room implementations will not allow to grant ownership privileges to other users. An
     * owner is allowed to change defining room features as well as perform all administrative functions.
     *
     * jid the bare XMPP user ID of the user to grant ownership privileges (e.g. "user@host.org").
     */
    override fun grantOwnership(address: String) {
        try {
            mMultiUserChat.grantOwnership(JidCreate.from(address))
        } catch (ex: XMPPException) {
            Timber.e(ex, "An error occurs granting ownership privileges to a user")
        } catch (ex: NoResponseException) {
            Timber.e(ex, "An error occurs granting ownership privileges to a user")
        } catch (ex: NotConnectedException) {
            Timber.e(ex, "An error occurs granting ownership privileges to a user")
        } catch (ex: InterruptedException) {
            Timber.e(ex, "An error occurs granting ownership privileges to a user")
        } catch (ex: XmppStringprepException) {
            Timber.e(ex, "An error occurs granting ownership privileges to a user")
        } catch (ex: IllegalArgumentException) {
            Timber.e(ex, "An error occurs granting ownership privileges to a user")
        }
    }

    /**
     * Grants voice to a visitor in the room. In a moderated room, a moderator may want to manage
     * who does and does not have "voice" in the room. To have voice means that a room occupant is
     * able to send messages to the room occupants.
     *
     * nickname the nickname of the visitor to grant voice in the room (e.g. "john").
     *
     * XMPPException if an error occurs granting voice to a visitor. In particular, a 403
     * error can occur if the occupant that intended to grant voice is not a moderator in
     * this room (i.e. Forbidden error) or a 400 error can occur if the provided nickname is
     * not present in the room.
     */
    override fun grantVoice(nickname: String) {
        try {
            mMultiUserChat.grantVoice(Resourcepart.from(nickname))
        } catch (ex: XMPPException) {
            Timber.e(ex, "An error occurs granting voice to a visitor")
        } catch (ex: NoResponseException) {
            Timber.e(ex, "An error occurs granting voice to a visitor")
        } catch (ex: NotConnectedException) {
            Timber.e(ex, "An error occurs granting voice to a visitor")
        } catch (ex: InterruptedException) {
            Timber.e(ex, "An error occurs granting voice to a visitor")
        } catch (ex: XmppStringprepException) {
            Timber.e(ex, "An error occurs granting voice to a visitor")
        }
    }

    /**
     * Revokes administrator privileges from a user. The occupant that loses administrator
     * privileges will become a member. Room owners may revoke administrator privileges from a
     * member or unaffiliated user.
     *
     * jid the bare XMPP user ID of the user to grant administrator privileges (e.g. "user@host.org").
     */
    override fun revokeAdmin(address: String) {
        try {
            mMultiUserChat.revokeAdmin(JidCreate.from(address) as EntityJid)
        } catch (ex: XMPPException) {
            Timber.e(ex, "n error occurs revoking administrator privileges to a user")
        } catch (ex: NoResponseException) {
            Timber.e(ex, "n error occurs revoking administrator privileges to a user")
        } catch (ex: NotConnectedException) {
            Timber.e(ex, "n error occurs revoking administrator privileges to a user")
        } catch (ex: XmppStringprepException) {
            Timber.e(ex, "n error occurs revoking administrator privileges to a user")
        } catch (ex: IllegalArgumentException) {
            Timber.e(ex, "n error occurs revoking administrator privileges to a user")
        } catch (ex: InterruptedException) {
            Timber.e(ex, "n error occurs revoking administrator privileges to a user")
        }
    }

    /**
     * Revokes a user's membership. Only administrators are able to revoke membership. A user that
     * becomes a room member will be able to enter a room of type Members-Only (i.e. a room that a
     * user cannot enter without being on the member list). If the user is in the room and the room
     * is of type members-only then the user will be removed from the room.
     *
     * jid the bare XMPP user ID of the user to revoke membership (e.g. "user@host.org").
     */
    override fun revokeMembership(address: String) {
        try {
            mMultiUserChat.revokeMembership(JidCreate.from(address))
        } catch (ex: XMPPException) {
            Timber.e(ex, "An error occurs revoking membership to a user")
        } catch (ex: NoResponseException) {
            Timber.e(ex, "An error occurs revoking membership to a user")
        } catch (ex: NotConnectedException) {
            Timber.e(ex, "An error occurs revoking membership to a user")
        } catch (ex: InterruptedException) {
            Timber.e(ex, "An error occurs revoking membership to a user")
        } catch (ex: IllegalArgumentException) {
            Timber.e(ex, "An error occurs revoking membership to a user")
        } catch (ex: XmppStringprepException) {
            Timber.e(ex, "An error occurs revoking membership to a user")
        }
    }

    /**
     * Revokes moderator privileges from another user. The occupant that loses moderator privileges
     * will become a participant. Room administrators may revoke moderator privileges only to
     * occupants whose affiliation is member or none. This means that an administrator is not
     * allowed to revoke moderator privileges from other room administrators or owners.
     *
     * nickname the nickname of the occupant to revoke moderator privileges.
     */
    override fun revokeModerator(nickname: String) {
        try {
            mMultiUserChat.revokeModerator(Resourcepart.from(nickname))
        } catch (ex: XMPPException) {
            Timber.e(ex, "An error occurs revoking moderator privileges from a user")
        } catch (ex: NoResponseException) {
            Timber.e(ex, "An error occurs revoking moderator privileges from a user")
        } catch (ex: NotConnectedException) {
            Timber.e(ex, "An error occurs revoking moderator privileges from a user")
        } catch (ex: InterruptedException) {
            Timber.e(ex, "An error occurs revoking moderator privileges from a user")
        } catch (ex: XmppStringprepException) {
            Timber.e(ex, "An error occurs revoking moderator privileges from a user")
        }
    }

    /**
     * Revokes ownership privileges from another user. The occupant that loses ownership privileges
     * will become an administrator. Room owners may revoke ownership privileges. Some room
     * implementations will not allow to grant ownership privileges to other users.
     *
     * jid the bare XMPP user ID of the user to revoke ownership (e.g. "user@host.org").
     */
    override fun revokeOwnership(address: String) {
        try {
            mMultiUserChat.revokeOwnership(JidCreate.from(address))
        } catch (ex: XMPPException) {
            Timber.e(ex, "An error occurs revoking ownership privileges from a user")
        } catch (ex: NoResponseException) {
            Timber.e(ex, "An error occurs revoking ownership privileges from a user")
        } catch (ex: NotConnectedException) {
            Timber.e(ex, "An error occurs revoking ownership privileges from a user")
        } catch (ex: InterruptedException) {
            Timber.e(ex, "An error occurs revoking ownership privileges from a user")
        } catch (ex: XmppStringprepException) {
            Timber.e(ex, "An error occurs revoking ownership privileges from a user")
        } catch (ex: IllegalArgumentException) {
            Timber.e(ex, "An error occurs revoking ownership privileges from a user")
        }
    }

    /**
     * Revokes voice from a participant in the room. In a moderated room, a moderator may want to
     * revoke an occupant's privileges to speak. To have voice means that a room occupant is
     * able to send messages to the room occupants.
     *
     * nickname the nickname of the participant to revoke voice (e.g. "john").
     *
     * XMPPException if an error occurs revoking voice from a participant. In particular, a
     * 405 error can occur if a moderator or a user with an affiliation of "owner" or "admin"
     * was tried to revoke his voice (i.e. Not Allowed error) or a 400 error can occur if
     * the provided nickname is not present in the room.
     */
    override fun revokeVoice(nickname: String) {
        try {
            mMultiUserChat.revokeVoice(Resourcepart.from(nickname))
        } catch (ex: XMPPException) {
            Timber.i(ex, "An error occurs revoking voice from a participant")
        } catch (ex: NoResponseException) {
            Timber.i(ex, "An error occurs revoking voice from a participant")
        } catch (ex: NotConnectedException) {
            Timber.i(ex, "An error occurs revoking voice from a participant")
        } catch (ex: InterruptedException) {
            Timber.i(ex, "An error occurs revoking voice from a participant")
        } catch (ex: XmppStringprepException) {
            Timber.i(ex, "An error occurs revoking voice from a participant")
        }
    }

    /**
     * Returns the internal stack used chat room instance.
     *
     * @return the MultiUserChat instance used in the protocol stack.
     */
    override fun getMultiUserChat(): MultiUserChat {
        return mMultiUserChat
    }

    /**
     * Class implementing MultiUserChat#PresenceListener
     */
    private inner class ParticipantListener : PresenceListener {
        /**
         * Processes an incoming presence packet from participantListener.
         *
         * presence the presence packet.
         */
        override fun processPresence(presence: Presence) {
            if (MUCUserStatusCodeFilter.STATUS_110_PRESENCE_TO_SELF.accept(presence)) processOwnPresence(presence) else processOtherPresence(presence)
        }

        /**
         * Processes a `Presence` packet addressed to our own occupant JID.
         * with either MUCUser extension or MUCInitialPresence extension (error)
         *
         * presence the packet to process.
         */
        private fun processOwnPresence(presence: Presence) {
            val mucUser = presence.getExtension(MUCUser::class.java)
            if (mucUser != null) {
                // lastPresenceSent = presence
                val affiliation = mucUser.item.affiliation
                val role = mucUser.item.role

                // if status 201 is available means that room is created and locked till we send the configuration
                if (mucUser.status != null
                        && mucUser.status.contains(MUCUser.Status.ROOM_CREATED_201)) {
                    try {
                        val formField = FormField.buildHiddenFormType("http://jabber.org/protocol/muc#roomconfig")
                        val dataForm = DataForm.builder(DataForm.Type.form).addField(formField).build()
                        mMultiUserChat.sendConfigurationForm(FillableForm(dataForm))

                        // Sending null also picked up the options OperationSetMultiUserChatJabberImpl#createChatRoom and sent
                        // mMultiUserChat.sendConfigurationForm(null)
                    } catch (e: XMPPException) {
                        Timber.e(e, "Failed to send config form.")
                    } catch (e: NoResponseException) {
                        Timber.e(e, "Failed to send config form.")
                    } catch (e: NotConnectedException) {
                        Timber.e(e, "Failed to send config form.")
                    } catch (e: InterruptedException) {
                        Timber.e(e, "Failed to send config form.")
                    }

                    // Update mNickName here as it is used in fireLocalUserRoleEvent before joinAs() is triggered.
                    val from = presence.from.asEntityFullJidIfPossible()
                    mNickName = from.resourceOrEmpty
                    opSetMuc!!.addSmackInvitationRejectionListener(mMultiUserChat, this@ChatRoomJabberImpl)
                    if (affiliation == MUCAffiliation.owner) {
                        setLocalUserRole(ChatRoomMemberRole.OWNER, true)
                    }
                    else setLocalUserRole(ChatRoomMemberRole.MODERATOR, true)
                }
                else {
                    // this is the presence for our own initial mRole and affiliation,
                    // as smack do not fire any initial events lets check it and fire events
                    if (role == MUCRole.moderator || affiliation == MUCAffiliation.owner || affiliation == MUCAffiliation.admin) {
                        val scRole = smackRoleToScRole(role, affiliation)
                        setLocalUserRole(scRole, true)
                    }
                    if (!presence.isAvailable && role == MUCRole.none && affiliation == MUCAffiliation.none) {
                        val destroy = mucUser.destroy

                        // the room is unavailable to us, there is no message we will just leave
                        if (destroy == null) {
                            leave()
                        }
                        else {
                            leave(destroy.jid, aTalkApp.getResString(R.string.service_gui_CHATROOM_DESTROY_MESSAGE,
                                destroy.reason))
                        }
                    }
                }
            }
            else if (Presence.Type.error == presence.type) {
                val errMessage = presence.error.toString()
                addMessage(errMessage, ChatMessage.MESSAGE_ERROR)
            }
        }

        /**
         * Process a `Presence` packet sent by one of the other room occupants.
         */
        private fun processOtherPresence(presence: Presence) {
            val from = presence.from
            var participantNick: Resourcepart? = null
            if (from != null) {
                participantNick = from.resourceOrNull
            }
            val member = if (participantNick == null) null else members[participantNick]
            // if member wasn't just created, we should potentially modify some elements
            if (member == null) {
                Timber.w("Received presence from an unknown member %s (%s)",
                    participantNick, mMultiUserChat.room)
                return
            }
            val cdExt = presence.getExtension(ConferenceDescriptionExtension::class.java)
            if (presence.isAvailable && cdExt != null) {
                val cd = ConferenceDescription(cdExt.uri, cdExt.callId, cdExt.password)
                cd.setAvailable(cdExt.isAvailable)
                cd.setDisplayName(getName())
                for (t in cdExt.getChildExtensionsOfType(TransportExtension::class.java)) {
                    cd.addTransport(t.namespace)
                }
                if (!processConferenceDescription(cd, participantNick)) return
                Timber.d("Received %s from %s in %s", cd, participantNick, mMultiUserChat.room)
                fireConferencePublishedEvent(member, cd, ChatRoomConferencePublishedEvent.CONFERENCE_DESCRIPTION_RECEIVED)
            }

            // For 4.4.3-master (20200416): presence.getExtension(Nick.class) => IllegalArgumentException
            val nickExt = presence.getExtension(Nick.QNAME) as Nick?
            if (nickExt != null) {
                member.displayName = nickExt.name
            }
            val emailExtension = presence.getExtension(Email::class.java)
            if (emailExtension != null) {
                member.email = emailExtension.address
            }
            val avatarUrl = presence.getExtension(AvatarUrl::class.java)
            if (avatarUrl != null) {
                member.avatarUrl = avatarUrl.avatarUrl
            }
            val statsId = presence.getExtension(StatsId::class.java)
            if (statsId != null) {
                member.statisticsID = statsId.statsId
            }

            // tell listeners the member was updated (and new information about it is available)
            member.lastPresence = presence
            fireMemberPresenceEvent(member, ChatRoomMemberPresenceChangeEvent.MEMBER_UPDATED, null)
        }
    }

    /**
     * Listens for rejection message and delivers system message when received.
     */
    private inner class InvitationRejectionListeners : InvitationRejectionListener {
        /**
         * Listens for rejection message and delivers system message when received.
         * Called when the invitee declines the invitation.
         *
         * invitee the invitee that declined the invitation. (e.g. hecate@shakespeare.lit).
         * reason the reason why the invitee declined the invitation.
         * message the message used to decline the invitation.
         * rejection the raw decline found in the message.
         */
        override fun invitationDeclined(
                invitee: EntityBareJid, reason: String,
                message: Message, rejection: MUCUser.Decline,
        ) {
            // MUCUser mucUser = packet.getExtension(MUCUser.class)
            val mucUser = MUCUser.from(message)

            // Check if the MUCUser informs that the invitee has declined the invitation
            if (mucUser != null && message.type != Message.Type.error) {
                val nick: Resourcepart? = try {
                    Resourcepart.from(invitee.localpart.toString())
                } catch (e: XmppStringprepException) {
                    Resourcepart.EMPTY
                }
                val member = ChatRoomMemberJabberImpl(this@ChatRoomJabberImpl, nick, invitee)
                val msgBody = aTalkApp.getResString(R.string.service_gui_INVITATION_REJECTED, invitee, reason)
                val msgReceivedEvt = ChatRoomMessageReceivedEvent(
                    this@ChatRoomJabberImpl, member, Date(), createMessage(msgBody), ChatMessage.MESSAGE_SYSTEM)
                fireMessageEvent(msgReceivedEvt)
            }
        }
    }

    /**
     * We use this to make sure that our outgoing <tt>Presence</tt> packets contain the correct
     * <tt>ConferenceAnnouncementPacketExtension</tt> and custom extensions.
     */
    private fun presenceIntercept(presenceBuilder: PresenceBuilder) {
        if (publishedConferenceExt != null) {
            presenceBuilder.overrideExtension(publishedConferenceExt)
        }
        else {
            presenceBuilder.removeExtension(ConferenceDescriptionExtension.ELEMENT, ConferenceDescriptionExtension.NAMESPACE)
        }
        for (ext in presencePacketExtensions) {
            presenceBuilder.overrideExtension(ext)
        }
        lastPresenceSent = presenceBuilder.build()
    }

    /**
     * Stores the last sent presence.
     */
    private inner class LastPresenceListener : StanzaListener {
        @Throws(NotConnectedException::class, InterruptedException::class, NotLoggedInException::class)
        override fun processStanza(packet: Stanza) {
            lastPresenceSent = packet as Presence
        }
    }

    /**
     * Updates the presence status of private messaging contact.
     * ChatRoom member always has e.g. conference@conference.atalk.org/swan,
     *
     * chatRoomMember the chatRoomMember of the contact.
     */
    override fun updatePrivateContactPresenceStatus(chatRoomMember: ChatRoomMember) {
        val presenceOpSet = mPPS.getOperationSet(OperationSetPersistentPresence::class.java)
        val contact = presenceOpSet!!.findContactByID(getName() + "/" + chatRoomMember.getNickName())
        updatePrivateContactPresenceStatus(contact)
    }

    /**
     * Updates the presence status of private messaging contact.
     *
     * contact the contact.
     */
    override fun updatePrivateContactPresenceStatus(contact: Contact?) {
        if (contact == null)
            return

        val presenceOpSet = mPPS.getOperationSet(OperationSetPersistentPresence::class.java) as OperationSetPersistentPresenceJabberImpl
        val oldContactStatus = contact.presenceStatus
        val nickname = try {
            JidCreate.from(contact.address).resourceOrEmpty
        } catch (e: XmppStringprepException) {
            Timber.e("Invalid contact address: %s", contact.address)
            return
        } catch (e: IllegalArgumentException) {
            Timber.e("Invalid contact address: %s", contact.address)
            return
        }
        val isOffline = !members.containsKey(nickname)
        val offlineStatus = mPPS.jabberStatusEnum!!.getStatus(
            if (isOffline) JabberStatusEnum.OFFLINE else JabberStatusEnum.AVAILABLE)

        // When status changes this may be related to a change in the available resources.
        (contact as ContactJabberImpl).presenceStatus = offlineStatus
        presenceOpSet.fireContactPresenceStatusChangeEvent(contact, contact.contactJid!!, contact.parentContactGroup!!,
            oldContactStatus, offlineStatus)
    }

    companion object {
        private const val LAST_SEEN_DELAYED_MESSAGE_PROP = "lastSeenDelayedMessage"
        //    /**
        //     * Send chatRoom registration request
        //     * <iq to='chatroom-8eev@conference.atalk.org' id='VAgx3-116' type='get'><query xmlns='jabber:iq:register'></query></iq>
        //     *
        //     * <iq xml:lang='en' to='leopard@atalk.org/atalk' from='chatroom-8eev@conference.atalk.org' type='error' id='VAgx3-116'>
        //     * <query xmlns='jabber:iq:register'/><error code='503' type='cancel'><service-unavailable xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>
        //     * <text xml:lang='en' xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'>The feature requested is not supported by the conference</text></error></iq>
        //     */
        //    private void initRegistrationRequest()
        //    {
        //        Registration req = new Registration(null)
        //        EntityBareJid chatRoom = mMultiUserChat.getRoom()
        //        DomainFullJid fromJid = JidCreate.domainFullFrom(chatRoom.asDomainBareJid(), mNickName)
        //        req.setTo(chatRoom)
        //        req.setType(IQ.Type.get)
        //        req.setFrom(fromJid)
        //        req.setStanzaId()
        //        try {
        //            StanzaCollector stanzaCollector
        //                    = mProvider.connection.createStanzaCollectorAndSend(new StanzaIdFilter(req.getStanzaId()), req)
        //        } catch (NotConnectedException | InterruptedException e) {
        //            e.printStackTrace()
        //        }
        //    }
        /**
         * Returns that `ChatRoomJabberRole` instance corresponding to the `smackRole` string.
         *
         * mucRole the smack mRole as returned by `Occupant.getRole()`.
         *
         * @return ChatRoomMemberRole
         */
        fun smackRoleToScRole(mucRole: MUCRole?, affiliation: MUCAffiliation?): ChatRoomMemberRole {
            if (affiliation != null) {
                if (affiliation == MUCAffiliation.admin) {
                    return ChatRoomMemberRole.ADMINISTRATOR
                }
                else if (affiliation == MUCAffiliation.owner) {
                    return ChatRoomMemberRole.OWNER
                }
            }
            if (mucRole != null) {
                if (mucRole == MUCRole.moderator) {
                    return ChatRoomMemberRole.MODERATOR
                }
                else if (mucRole == MUCRole.participant) {
                    return ChatRoomMemberRole.MEMBER
                }
            }
            return ChatRoomMemberRole.GUEST
        }

        /**
         * Sets `ext` as the only `ExtensionElement` that belongs to given `namespace` of the `packet`.
         *
         * packet the `Packet` to be modified.
         * extension the `ConferenceDescriptionPacketExtension` to set, or `null` to not set one.
         * namespace the namespace of `ExtensionElement`.
         * matchElementName if `true` only extensions matching both the element name and namespace will be matched
         * and removed. Otherwise, only the namespace will be matched.
         *
         * @return whether packet was modified.
        ```` */
        private fun setPacketExtension(
                packet: Stanza, extension: ExtensionElement?, namespace: String,
                matchElementName: Boolean,
        ): Boolean {
            var modified = false
            if (StringUtils.isEmpty(namespace)) {
                return modified
            }

            // clear previous announcements
            var pe: ExtensionElement
            if (matchElementName && extension != null) {
                val element = extension.elementName
                while (null != packet.getExtensionElement(element, namespace).also { pe = it }) {
                    if (packet.removeExtension(pe.elementName, pe.namespace) != null) {
                        modified = true
                    }
                }
            }
            else {
                while (null != packet.getExtension(namespace).also { pe = it }) {
                    if (packet.removeExtension(pe.elementName, pe.namespace) != null) {
                        modified = true
                    }
                }
            }
            if (extension != null) {
                packet.addExtension(extension)
                modified = true
            }
            return modified
        }

        /**
         * Sets `ext` as the only `ExtensionElement` that belongs to given `namespace`
         * of the `packet`.
         *
         * packet the `Packet` to be modified.
         * extension the `ConferenceDescriptionPacketExtension` to set, or `null` to not set one.
         * namespace the namespace of `ExtensionElement`.
        ```` */
        private fun setPacketExtension(packet: Stanza, extension: ExtensionElement?, namespace: String): Boolean {
            return setPacketExtension(packet, extension, namespace, false)
        }
    }
}