/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import android.text.Html
import android.text.TextUtils
import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.plugin.notificationwiring.NotificationManager
import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.protocol.event.*
import net.java.sip.communicator.util.ConfigurationUtils.isSendChatStateNotifications
import org.apache.commons.lang3.RandomStringUtils
import org.atalk.crypto.omemo.OmemoAuthenticateDialog
import org.atalk.crypto.omemo.OmemoAuthenticateDialog.AuthenticateListener
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.ChatMessage
import org.atalk.hmos.gui.chat.ChatSessionManager.createChatForContact
import org.atalk.hmos.gui.util.XhtmlUtil.getXhtmlExtension
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.jivesoftware.smack.SmackException.*
import org.jivesoftware.smack.StanzaListener
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smack.chat2.Chat
import org.jivesoftware.smack.chat2.ChatManager
import org.jivesoftware.smack.chat2.IncomingChatMessageListener
import org.jivesoftware.smack.filter.*
import org.jivesoftware.smack.packet.*
import org.jivesoftware.smack.parsing.SmackParsingException
import org.jivesoftware.smack.util.PacketParserUtils
import org.jivesoftware.smack.xml.XmlPullParserException
import org.jivesoftware.smackx.carbons.CarbonCopyReceivedListener
import org.jivesoftware.smackx.carbons.CarbonManager
import org.jivesoftware.smackx.carbons.packet.CarbonExtension
import org.jivesoftware.smackx.chatstates.ChatState
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension
import org.jivesoftware.smackx.delay.packet.DelayInformation
import org.jivesoftware.smackx.message_correct.element.MessageCorrectExtension
import org.jivesoftware.smackx.muc.packet.MUCUser
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.OmemoMessage.Received
import org.jivesoftware.smackx.omemo.element.OmemoElement
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException
import org.jivesoftware.smackx.omemo.exceptions.CryptoFailedException
import org.jivesoftware.smackx.omemo.exceptions.NoRawSessionException
import org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException
import org.jivesoftware.smackx.omemo.internal.OmemoDevice
import org.jivesoftware.smackx.omemo.listener.OmemoMessageListener
import org.jivesoftware.smackx.omemo.provider.OmemoVAxolotlProvider
import org.jivesoftware.smackx.omemo.util.OmemoConstants
import org.jivesoftware.smackx.sid.element.StanzaIdElement
import org.jivesoftware.smackx.xhtmlim.XHTMLManager
import org.jivesoftware.smackx.xhtmlim.XHTMLText
import org.jivesoftware.smackx.xhtmlim.packet.XHTMLExtension
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.EntityFullJid
import org.jxmpp.jid.Jid
import timber.log.Timber
import java.io.IOException
import java.util.*

/**
 * A straightforward implementation of the basic instant messaging operation set.
 *
 * @author Damian Minkov
 * @author Matthieu Helleringer
 * @author Alain Knaebel
 * @author Emil Ivov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class OperationSetBasicInstantMessagingJabberImpl internal constructor(
        /**
         * The provider that created us.
         */
        private val mPPS: ProtocolProviderServiceJabberImpl,
) : AbstractOperationSetBasicInstantMessaging(), RegistrationStateChangeListener, OperationSetMessageCorrection, IncomingChatMessageListener, StanzaListener, CarbonCopyReceivedListener, OmemoMessageListener {
    /**
     * A table mapping contact addresses to message threads that can be used to target a specific
     * resource (rather than sending a message to all logged instances of a user).
     */
    private val jidThreads: MutableMap<BareJid?, StoredThreadID> = Hashtable()

    /**
     * The most recent FullJid used for the contact address.
     */
    private val recentJidForContact: MutableMap<BareJid, Jid?> = Hashtable()

    /**
     * CarbonManager and ChatManager instances used by OperationSetBasicInstantMessagingJabberImpl
     */
    private var mCarbonManager: CarbonManager? = null
    private var mChatManager: ChatManager? = null
    private val mhs = MUCActivator.messageHistoryService

    /**
     * Current active chat
     */
    private var mChat: Chat? = null

    /**
     * Contains the complete jid of a specific user and the time that it was last used so that we
     * could remove it after a certain point.
     */
    class StoredThreadID {
        /**
         * The time that we last sent or received a message from this jid
         */
        var lastUpdatedTime: Long = 0

        /**
         * The last chat used, this way we will reuse the thread-id
         */
        var threadID: String? = null
    }

    private lateinit var mUserJid: String
    private var mOmemoManager: OmemoManager? = null
    private val omemoVAxolotlProvider = OmemoVAxolotlProvider()

    /**
     * A reference to the persistent presence operation set that we use to match incoming messages
     * to `Contact`s and vice versa.
     */
    private var opSetPersPresence: OperationSetPersistentPresenceJabberImpl? = null

    /**
     * Whether carbon is enabled or not.
     */
    private var isCarbonEnabled = false

    // Indicate if the received message is carbon copy
    private var isCarbon = false

    /**
     * Create a IMessage instance with the specified UID, content type and a default encoding. This
     * method can be useful when message correction is required. One can construct the corrected
     * message to have the same UID as the message before correction.
     *
     * @param messageText the string content of the message.
     * @param encType the encryption type for the `content`
     * @param messageUID the unique identifier of this message.
     *
     * @return IMessage the newly created message
     */
    override fun createMessageWithUID(messageText: String, encType: Int, messageUID: String): IMessage {
        return MessageJabberImpl(messageText, encType, null, messageUID)
    }

    /**
     * Create a IMessage instance for sending arbitrary MIME-encoding content.
     *
     * @param content content value
     * @param encType the encryption type for the `content`
     *
     * @return the newly created message.
     */
    fun createMessage(content: String, encType: Int): IMessage {
        return createMessage(content, encType, null)
    }

    /**
     * Create a IMessage instance for sending arbitrary MIME-encoding content.
     *
     * @param content content value
     * @param encType the encryption type for the `content`
     * @param subject the Subject of the message that we'd like to create.
     *
     * @return the newly created message.
     */
    override fun createMessage(content: String, encType: Int, subject: String?): IMessage {
        return MessageJabberImpl(content, encType, subject, null)
    }

    /**
     * Determines whether the protocol provider (or the protocol itself) support sending and
     * receiving offline messages. Most often this method would return true for protocols that
     * support offline messages and false for those that don't. It is however possible for a
     * protocol to support these messages and yet have a particular account that does not
     * (i.e. feature not enabled on the protocol server). In cases like this it is possible for
     * this method to return true even when offline messaging is not supported, and then have the
     * sendMessage method throw an OperationFailedException with code - OFFLINE_MESSAGES_NOT_SUPPORTED.
     *
     * @return `true` if the protocol supports offline messages and `false` otherwise.
     */
    override fun isOfflineMessagingSupported(): Boolean {
        return true
    }

    /**
     * Determines whether the protocol supports the supplied content type
     *
     * @param mimeType the encryption type we want to check
     *
     * @return `true` if the protocol supports it and `false` otherwise.
     */
    override fun isContentTypeSupported(mimeType: Int): Boolean {
        return IMessage.ENCODE_PLAIN == mimeType || IMessage.ENCODE_HTML == mimeType
    }

    /**
     * Determines whether the protocol supports the supplied content type for the given contact.
     *
     * @param mimeType the encryption type we want to check
     * @param contact contact which is checked for supported encType
     *
     * @return `true` if the contact supports it and `false` otherwise.
     */
    override fun isContentTypeSupported(mimeType: Int, contact: Contact): Boolean {
        // by default we support default mime type, for other mime types method must be overridden
        if (IMessage.ENCODE_PLAIN == mimeType) {
            return true
        }
        else if (IMessage.ENCODE_HTML == mimeType) {
            val toJid = getRecentFullJidForContactIfPossible(contact)
            return mPPS.isFeatureListSupported(toJid, XHTMLExtension.NAMESPACE)
        }
        return false
    }

    /**
     * Remove from our `jidThreads` map all entries that have not seen any activity
     * (i.e. neither outgoing nor incoming messages) for more than JID_INACTIVITY_TIMEOUT.
     * Note that this method is not synchronous and that it is only meant for use by the
     * [.getThreadIDForAddress] and [.putJidForAddress]
     */
    private fun purgeOldJidThreads() {
        val currentTime = System.currentTimeMillis()
        val entries: MutableIterator<Map.Entry<BareJid?, StoredThreadID>> = jidThreads.entries.iterator()
        while (entries.hasNext()) {
            val (_, target) = entries.next()
            if (currentTime - target.lastUpdatedTime > JID_INACTIVITY_TIMEOUT) entries.remove()
        }
    }

    /**
     * When chat state enter ChatState.gone, existing thread should not be used again.
     *
     * @param bareJid the `address` that we'd like to remove a threadID for.
     */
    fun purgeGoneJidThreads(bareJid: BareJid?) {
        jidThreads.remove(bareJid)
    }

    /**
     * Returns the threadID that the party with the specified `address` contacted us from or
     * `new ThreadID` if `null` and `generateNewIfNoExist` is true; otherwise
     * `null` if we don't have a jid for the specified `address` yet.
     *
     * The method would also purge all entries that haven't seen any activity (i.e. no one has
     * tried to get or remap it) for a delay longer than `JID_INACTIVITY_TIMEOUT`.
     *
     * @param bareJid the `Jid` that we'd like to obtain a threadID for.
     * @param generateNewIfNoExist if `true` generates new threadID if null is found.
     *
     * @return new or last threadID that the party with the specified `address` contacted
     * us from OR `null` if we don't have a jid for the specified `address` and
     * `generateNewIfNoExist` is false.
     */
    private fun getThreadIDForAddress(bareJid: BareJid?, generateNewIfNoExist: Boolean): String? {
        synchronized(jidThreads) {
            purgeOldJidThreads()
            var ta = jidThreads[bareJid]

            // https://xmpp.org/extensions/xep-0201.html message thread Id is only recommended. buddy may sent without it
            if (ta?.threadID == null) {
                if (generateNewIfNoExist) {
                    ta = StoredThreadID()
                    ta.threadID = nextThreadID()
                    putJidForAddress(bareJid, ta.threadID)
                }
                else {
                    return null
                }
            }
            ta.lastUpdatedTime = System.currentTimeMillis()
            return ta.threadID
        }
    }

    /**
     * Maps the specified `address` to `jid`. The point of this method is to allow us
     * to send all messages destined to the contact with the specified `address` to the
     * `jid` that they last contacted us from.
     *
     * @param threadID the threadID of conversation.
     * @param jid the jid (i.e. address/resource) that the contact with the specified `address`
     * last contacted us from.
     */
    private fun putJidForAddress(jid: Jid?, threadID: String?) {
        synchronized(jidThreads) {
            purgeOldJidThreads()
            var ta = jidThreads[jid!!.asBareJid()]
            if (ta == null) {
                ta = StoredThreadID()
                jidThreads[jid.asBareJid()] = ta
            }
            recentJidForContact[jid.asBareJid()] = jid
            ta.lastUpdatedTime = System.currentTimeMillis()
            ta.threadID = threadID
        }
    }

    /**
     * Helper function used to send a message to a contact, with the given extensions attached.
     *
     * @param to The contact to send the message to.
     * @param toResource The resource to send the message to or null if no resource has been specified
     * @param message The message to send.
     * @param extElements The XMPP extensions that should be attached to the message before sending.
     *
     * @return The MessageDeliveryEvent that resulted after attempting to send this message, so the
     * calling function can modify it if needed.
     */
    private fun sendMessage(
            to: Contact?, toResource: ContactResource?,
            message: IMessage?, extElements: Collection<ExtensionElement>,
    ): MessageDeliveredEvent? {
        require(to is ContactJabberImpl) { "The specified contact is not a Jabber contact: $to" }
        try {
            assertConnected()
        } catch (ex: IllegalStateException) {
            val msgDeliveryFailed = MessageDeliveryFailedEvent(message!!, to, MessageDeliveryFailedEvent.PROVIDER_NOT_REGISTERED, null)
            fireMessageEvent(msgDeliveryFailed)
            // throw ex; Do not throw to cause system to crash, return null instead
            return null
        }
        val toJid = to.contactJid!!.asEntityBareJidIfPossible()
        mChat = mChatManager!!.chatWith(toJid)
        val threadID = getThreadIDForAddress(toJid, true)
        val timeStamp = Date()
        val messageBuilder = StanzaBuilder.buildMessage(message!!.getMessageUID())
            .ofType(Message.Type.chat)
            .to(toJid)
            .from(mPPS.connection!!.user)
            .setThread(threadID)
            .addExtensions(extElements)
        Timber.log(TimberLog.FINER, "MessageDeliveredEvent - Sending a message to: %s", toJid)
        message.setServerMsgId(messageBuilder.stanzaId)
        val msgDeliveryPendingEvt = MessageDeliveredEvent(message, to, toResource, mUserJid, timeStamp)
        val transformedEvents = messageDeliveryPendingTransform(msgDeliveryPendingEvt)
        if (transformedEvents == null || transformedEvents.isEmpty()) {
            val msgDeliveryFailed = MessageDeliveryFailedEvent(message, to, MessageDeliveryFailedEvent.UNSUPPORTED_OPERATION, null)
            fireMessageEvent(msgDeliveryFailed)
            return null
        }
        message.setReceiptStatus(ChatMessage.MESSAGE_DELIVERY_CLIENT_SENT)
        for (event in transformedEvents) {
            val content = event!!.getSourceMessage().getContent()
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

            if (event.isMessageEncrypted() && isCarbonEnabled) {
                CarbonExtension.Private.addTo(messageBuilder)
            }

            // Add ChatState.active extension to message send if option is enabled
            if (isSendChatStateNotifications()) {
                val extActive = ChatStateExtension(ChatState.active)
                messageBuilder.addExtension(extActive)
                // messageBuilder.addExtension(new MUCUser());
            }
            try {
                mChat!!.send(messageBuilder.build())
            } catch (e: NotConnectedException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            putJidForAddress(toJid, threadID)
        }
        return MessageDeliveredEvent(message, to, toResource, mUserJid, timeStamp)
    }

    /**
     * Sends the `message` to the destination indicated by the `to` contact.
     *
     * @param to the `Contact` to send `message` to
     * @param message the `IMessage` to send.
     *
     * @throws java.lang.IllegalStateException if the underlying stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException if `to` is not an instance of ContactImpl.
     */
    override fun sendInstantMessage(to: Contact, message: IMessage) {
        sendInstantMessage(to, null, message)
    }

    /**
     * Sends the `message` to the destination indicated by the `to`. Provides a
     * default implementation of this method.
     *
     * @param to the `Contact` to send `message` to
     * @param toResource the resource to which the message should be send
     * @param message the `IMessage` to send.
     *
     * @throws java.lang.IllegalStateException if the underlying ICQ stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException if `to` is not an instance belonging to the underlying implementation.
     */
    override fun sendInstantMessage(to: Contact, toResource: ContactResource?, message: IMessage) {
        val msgDelivered = sendMessage(to, toResource, message, emptyList())
        msgDelivered?.let { fireMessageEvent(it) }
    }

    /**
     * Replaces the message with ID `correctedMessageUID` sent to the contact `to`
     * with the message `message`
     *
     * @param to The contact to send the message to.
     * @param message The new message.
     * @param correctedMessageUID The ID of the message being replaced.
     */
    override fun correctMessage(
            to: Contact?, resource: ContactResource?, message: IMessage?,
            correctedMessageUID: String?,
    ) {
        val extElements: Collection<ExtensionElement> = listOf<ExtensionElement>(MessageCorrectExtension(correctedMessageUID))
        val msgDelivered = sendMessage(to, resource, message, extElements)
        if (msgDelivered != null) {
            msgDelivered.setCorrectedMessageUID(correctedMessageUID)
            fireMessageEvent(msgDelivered)
        }
    }

    override fun sendInstantMessage(
            to: Contact, resource: ContactResource?, message: IMessage, correctedMessageUID: String?,
            omemoManager: OmemoManager,
    ) {
        val bareJid = to.contactJid!!.asBareJid()
        var msgContent = message.getContent()
        var errMessage: String? = null
        try {
            var encryptedMessage = omemoManager.encrypt(bareJid, msgContent)
            var messageBuilder = StanzaBuilder.buildMessage(message.getMessageUID())
            if (correctedMessageUID != null) messageBuilder.addExtension(MessageCorrectExtension(correctedMessageUID))
            var sendMessage = encryptedMessage.buildMessage(messageBuilder, bareJid)
            if (IMessage.ENCODE_HTML == message.getMimeType()) {
                // Make this into encrypted xhtmlText for inclusion
                val xhtmlEncrypted = encryptedMessage.element.toXML().toString()
                val xhtmlText = XHTMLText("", "us")
                    .append(xhtmlEncrypted)
                    .appendCloseBodyTag()

                // OMEMO body message content will strip off any xhtml tags info
                msgContent = Html.fromHtml(msgContent).toString()
                encryptedMessage = omemoManager.encrypt(bareJid, msgContent)
                messageBuilder = StanzaBuilder.buildMessage(message.getMessageUID())
                if (correctedMessageUID != null) messageBuilder.addExtension(MessageCorrectExtension(correctedMessageUID))

                // Add the XHTML text to the message builder
                XHTMLManager.addBody(messageBuilder, xhtmlText)
                sendMessage = encryptedMessage.buildMessage(messageBuilder, bareJid)
            }

            // proceed to send the message if there is no exception.
            mChat = mChatManager!!.chatWith(bareJid.asEntityBareJidIfPossible())
            mChat!!.send(sendMessage)
            message.setServerMsgId(sendMessage.stanzaId)
            message.setReceiptStatus(ChatMessage.MESSAGE_DELIVERY_CLIENT_SENT)
            val msgDelivered = MessageDeliveredEvent(message, to, resource, mUserJid, correctedMessageUID)
            fireMessageEvent(msgDelivered)
        } catch (ex: Exception) {
            when (ex) {
                is UndecidedOmemoIdentityException -> {
                    val omemoAuthListener = OmemoAuthenticateListener(to, resource!!, message, correctedMessageUID, omemoManager)
                    val ctx = aTalkApp.globalContext
                    ctx.startActivity(OmemoAuthenticateDialog.createIntent(ctx, omemoManager, ex.undecidedDevices, omemoAuthListener))
                    return
                }

                is CryptoFailedException,
                is InterruptedException,
                is NotConnectedException,
                is NoResponseException,
                is IOException,
                -> errMessage = aTalkApp.getResString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, ex.message)

                is NotLoggedInException ->
                    errMessage = aTalkApp.getResString(R.string.service_gui_MSG_SEND_CONNECTION_PROBLEM)
            }
        }
        if (!TextUtils.isEmpty(errMessage)) {
            Timber.w("%s", errMessage)
            val failedEvent = MessageDeliveryFailedEvent(message, to,
                MessageDeliveryFailedEvent.OMEMO_SEND_ERROR, System.currentTimeMillis(), errMessage)
            fireMessageEvent(failedEvent)
        }
    }

    /**
     * Omemo listener callback on user authentication for undecided omemoDevices
     */
    private inner class OmemoAuthenticateListener(
            var to: Contact, var resource: ContactResource, var message: IMessage, var correctedMessageUID: String?,
            var omemoManager: OmemoManager,
    ) : AuthenticateListener {
        override fun onAuthenticate(allTrusted: Boolean, omemoDevices: Set<OmemoDevice>?) {
            if (allTrusted) {
                sendInstantMessage(to, resource, message, correctedMessageUID, omemoManager)
            }
            else {
                val errMessage = aTalkApp.getResString(R.string.omemo_send_error,
                    "Undecided Omemo Identity: " + omemoDevices.toString())
                Timber.w("%s", errMessage)
                val failedEvent = MessageDeliveryFailedEvent(message, to,
                    MessageDeliveryFailedEvent.OMEMO_SEND_ERROR, System.currentTimeMillis(), errMessage)
                fireMessageEvent(failedEvent)
            }
        }
    }

    /**
     * Utility method throwing an exception if the stack is not properly initialized.
     *
     * @throws java.lang.IllegalStateException if the underlying stack is not registered and initialized.
     */
    @Throws(IllegalStateException::class)
    private fun assertConnected() {
        checkNotNull(opSetPersPresence) { "The provider must be sign in before able to communicate." }
        opSetPersPresence!!.assertConnected()
    }

    /**
     * The method is called by a ProtocolProvider implementation whenever a change in the
     * registration state of the corresponding provider had occurred.
     *
     * @param evt ProviderStatusChangeEvent the event describing the status change.
     */
    override fun registrationStateChanged(evt: RegistrationStateChangeEvent) {
        val connection = mPPS.connection
        if (evt.getNewState() === RegistrationState.REGISTERING) {
            opSetPersPresence = mPPS.getOperationSet(OperationSetPersistentPresence::class.java) as OperationSetPersistentPresenceJabberImpl?
            /*
             * HashSet<OmemoMessageListener> omemoMessageListeners;
             * Cannot just add here, has a problem as jid is not known to omemoManager yet
             * See AndroidOmemoService implementation for fix
             */
            // OmemoManager omemoManager = OmemoManager.getInstanceFor(xmppConnection);
            // registerOmemoMucListener(omemoManager);

            // make sure this listener is not already installed in this connection - ChatManager has taken care <set>
            mChatManager = ChatManager.getInstanceFor(connection)
            mChatManager!!.addIncomingListener(this)
        }
        else if (evt.getNewState() === RegistrationState.REGISTERED) {
            val userJid = connection!!.user
            mUserJid = userJid.toString()

            // make sure this listener is not already registered in this connection
            connection.removeAsyncStanzaListener(this@OperationSetBasicInstantMessagingJabberImpl)
            connection.addAsyncStanzaListener(this, INCOMING_SVR_MESSAGE_FILTER)
            enableDisableCarbon(userJid)
        }
        else if (evt.getNewState() === RegistrationState.UNREGISTERED || evt.getNewState() === RegistrationState.CONNECTION_FAILED || evt.getNewState() === RegistrationState.AUTHENTICATION_FAILED) {
            if (connection != null) {  // must not assume - may call after log off
                connection.removeAsyncStanzaListener(this)
                if (connection.isAuthenticated) {
                    unRegisterOmemoListener(mOmemoManager)
                }
            }
            if (mChatManager != null) {
                mChatManager!!.removeIncomingListener(this)
                mChatManager = null
            }
            if (mCarbonManager != null) {
                isCarbonEnabled = false
                mCarbonManager!!.removeCarbonCopyReceivedListener(this)
                mCarbonManager = null
            }
        }
    }

    /**
     * The listener that we use in order to handle incoming server messages currently not supported by smack
     *
     * @param stanza the packet that we need to handle (if it is a message).
     *
     * @see .INCOMING_SVR_MESSAGE_FILTER filter settings
     *
     * Handles incoming messages and dispatches whatever events that are necessary.
     */
    @Throws(NotConnectedException::class, InterruptedException::class, NotLoggedInException::class)
    override fun processStanza(stanza: Stanza) {
        val message = stanza as Message
        if (message.bodies.isEmpty()) return
        val encType = IMessage.ENCRYPTION_NONE or IMessage.ENCODE_PLAIN
        var content = message.body
        val subject = message.subject
        if (!TextUtils.isEmpty(subject)) {
            content = "$subject: $content"
        }

        // Some DomainBareJid message contain null msgId, so get it from StanzaIdElement if available
        var stanzaId = message.stanzaId
        if (TextUtils.isEmpty(stanzaId)) {
            val stanzaIdElement = stanza.getExtension(StanzaIdElement.QNAME)
            if (stanzaIdElement is StanzaIdElement) {
                stanzaId = stanzaIdElement.id
            }
        }
        val newMessage = createMessageWithUID(content, encType, stanzaId)
        newMessage.setRemoteMsgId(stanzaId)

        // createVolatileContact will check before create
        val sourceContact = opSetPersPresence!!.createVolatileContact(message.from)
        val sender = message.from.toString()
        val msgEvt = MessageReceivedEvent(newMessage, sourceContact,
            null, sender, getTimeStamp(message), null)
        fireMessageEvent(msgEvt)
    }

    /**
     * Enable carbon feature if supported by server.
     */
    private fun enableDisableCarbon(userJid: EntityFullJid) {
        var enableCarbon = false
        mCarbonManager = CarbonManager.getInstanceFor(mPPS.connection)
        try {
            enableCarbon = (mCarbonManager!!.isSupportedByServer
                    && !mPPS.accountID.getAccountPropertyBoolean(
                ProtocolProviderFactory.IS_CARBON_DISABLED, false))
            if (enableCarbon) {
                mCarbonManager!!.carbonsEnabled = true
                mCarbonManager!!.addCarbonCopyReceivedListener(this)
                isCarbonEnabled = true
            }
            else {
                isCarbonEnabled = false
                mCarbonManager = null
            }
            Timber.i("Successfully setting carbon new state for: %s to %s", userJid, isCarbonEnabled)
        } catch (e: NoResponseException) {
            Timber.e("Failed to set carbon state for: %s to %S\n%s", userJid, enableCarbon, e.message)
        } catch (e: InterruptedException) {
            Timber.e("Failed to set carbon state for: %s to %S\n%s", userJid, enableCarbon, e.message)
        } catch (e: NotConnectedException) {
            Timber.e("Failed to set carbon state for: %s to %S\n%s", userJid, enableCarbon, e.message)
        } catch (e: XMPPErrorException) {
            Timber.e("Failed to set carbon state for: %s to %S\n%s", userJid, enableCarbon, e.message)
        }
    }

    /**
     * The listener that we use in order to handle incoming messages and carbon messages.
     */
    private var isForwardedSentMessage = false
    override fun onCarbonCopyReceived(
            direction: CarbonExtension.Direction,
            carbonCopy: Message, wrappingMessage: Message,
    ) {
        isForwardedSentMessage = CarbonExtension.Direction.sent == direction
        val userJId = if (isForwardedSentMessage) carbonCopy.to else carbonCopy.from
        isCarbon = wrappingMessage.hasExtension(CarbonExtension.NAMESPACE)
        newIncomingMessage(userJId.asEntityBareJidIfPossible(), carbonCopy, null)
        isForwardedSentMessage = false
    }

    /**
     * Handles incoming messages and dispatches whatever events are necessary.
     *
     * @param message the message that we need to handle.
     */
    override fun newIncomingMessage(from: EntityBareJid, message: Message, chat: Chat?) {
        // Leave handling of omemo messages to onOmemoMessageReceived()
        if (message.hasExtension(OmemoElement.NAME_ENCRYPTED, OmemoConstants.OMEMO_NAMESPACE_V_AXOLOTL)) return

        // Return if it is for group chat
        if (Message.Type.groupchat == message.type) return
        val msgBody = message.body ?: return
        var userFullJId = if (isForwardedSentMessage) message.to else message.from
        val userBareID = userFullJId!!.asBareJid()
        var privateContactRoom: ChatRoomJabberImpl? = null
        val isPrivateMessaging = message.hasExtension(MUCUser.QNAME)
        if (isPrivateMessaging) {
            val mucOpSet = mPPS.getOperationSet(OperationSetMultiUserChat::class.java) as OperationSetMultiUserChatJabberImpl?
            if (mucOpSet != null) {
                privateContactRoom = mucOpSet.getChatRoom(userBareID)
                userFullJId = privateContactRoom!!.findMemberFromParticipant(userFullJId)!!.getJabberId()
            }
        }

        // Timber.d("Received from %s the message %s", userBareID, message.toString());
        val msgID = message.stanzaId
        val correctedMessageUID = getCorrectionMessageId(message)
        var encType = IMessage.ENCRYPTION_NONE

        // set up default in case XHTMLExtension contains no message
        // if msgBody contains markup text then set as ENCODE_HTML mode
        if (msgBody.matches(ChatMessage.HTML_MARKUP)) {
            encType = encType or IMessage.ENCODE_HTML
        }

        // isCarbon only valid if from onCarbonCopyReceived i.e. chat == null;
        if (chat == null && isCarbon) {
            encType = encType or IMessage.FLAG_IS_CARBON
        }
        var newMessage = createMessageWithUID(msgBody, encType, msgID)

        // check if the message is available in xhtml
        val xhtmString = getXhtmlExtension(message)
        if (xhtmString != null) {
            encType = encType or IMessage.ENCODE_HTML
            newMessage = createMessageWithUID(xhtmString, encType, msgID)
        }
        newMessage.setRemoteMsgId(message.stanzaId)

        // cmeng: source contact will have contact Jid if isPrivateMessaging.
        var sourceContact = opSetPersPresence!!.findContactByJid(if (isPrivateMessaging) userFullJId else userBareID)
        if (message.type == Message.Type.error) {
            // error which is multi-chat and we don't know about the contact is a muc message
            // error which is missing muc extension and is coming from the room, when we try
            // to send message to room which was deleted or offline on the server
            val error = message.error
            var errorResultCode = MessageDeliveryFailedEvent.UNKNOWN_ERROR
            if (isPrivateMessaging && privateContactRoom != null && sourceContact == null) {
                if (error != null && StanzaError.Condition.forbidden == error.condition) {
                    errorResultCode = MessageDeliveryFailedEvent.FORBIDDEN
                }
                val errorReason = error?.toString() ?: ""
                val msgDeliveryFailed = ChatRoomMessageDeliveryFailedEvent(privateContactRoom,
                    null, errorResultCode, System.currentTimeMillis(), errorReason, newMessage)
                privateContactRoom.fireMessageEvent(msgDeliveryFailed)
                return
            }
            Timber.i("Message error received from %s", userBareID)
            if (error != null) {
                val errorCondition = error.condition
                if (StanzaError.Condition.service_unavailable == errorCondition) {
                    if (!sourceContact!!.presenceStatus.isOnline) {
                        errorResultCode = MessageDeliveryFailedEvent.OFFLINE_MESSAGES_NOT_SUPPORTED
                    }
                }
            }
            if (sourceContact == null) {
                sourceContact = opSetPersPresence!!.createVolatileContact(userFullJId, isPrivateMessaging)
            }
            val msgDeliveryFailed = MessageDeliveryFailedEvent(newMessage, sourceContact, errorResultCode, correctedMessageUID)
            fireMessageEvent(msgDeliveryFailed)
            return
        }
        putJidForAddress(userFullJId, message.thread)

        // In the second condition we filter all group chat messages, because they are managed
        // by the multi user chat operation set.
        if (sourceContact == null) {
            Timber.d("received a message from an unknown contact: %s", userBareID)
            // create the volatile contact
            sourceContact = opSetPersPresence!!.createVolatileContact(userFullJId, isPrivateMessaging)
        }
        val timestamp = getTimeStamp(message)
        val resource = (sourceContact as ContactJabberImpl?)!!.getResourceFromJid(userFullJId!!.asFullJidIfPossible())
        val msgEvt: EventObject
        val sender = message.from.toString()
        if (isForwardedSentMessage) {
            msgEvt = MessageDeliveredEvent(newMessage, sourceContact, null, sender, timestamp)
            // Update message unread count for carbon message for the actual recipient.
            NotificationManager.updateMessageCount(sourceContact)
        }
        else {
            msgEvt = MessageReceivedEvent(newMessage, sourceContact, resource!!, sender, timestamp,
                correctedMessageUID, isPrivateMessaging, privateContactRoom)
        }

        // Start up Chat session if no exist, for offline message when history log is disabled;
        // Else first offline message is not shown
        if (mhs != null && !mhs.isHistoryLoggingEnabled && message.hasExtension(DelayInformation.QNAME)) {
            createChatForContact(sourceContact)
        }
        fireMessageEvent(msgEvt)
    }

    /**
     * A filter that prevents this operation set from handling multi user chat messages.
     */
    private class GroupMessagePacketFilter : StanzaFilter {
        /**
         * Returns `true` if `packet` is a `Message` and false otherwise.
         *
         * @param packet the packet that we need to check.
         *
         * @return `true` if `packet` is a `Message` and false otherwise.
         */
        override fun accept(packet: Stanza): Boolean {
            if (packet !is Message) return false
            return packet.type != Message.Type.groupchat
        }
    }

    private fun getRecentFullJidForContactIfPossible(contact: Contact?): Jid {
        val contactJid = contact!!.contactJid
        var jid = recentJidForContact[contactJid!!.asBareJid()]
        if (jid == null) jid = contactJid
        return jid
    }

    /**
     * Returns the inactivity timeout in milliseconds.
     *
     * @return The inactivity timeout in milliseconds. Or -1 if undefined
     */
    override fun getInactivityTimeout(): Long {
        return JID_INACTIVITY_TIMEOUT
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

    /**
     * Get messageCorrectionID if presence
     *
     * @param message Message
     *
     * @return messageCorrectionID if presence or null
     */
    private fun getCorrectionMessageId(message: Message): String? {
        val correctionExtension = MessageCorrectExtension.from(message)
        return correctionExtension?.idInitialMessage
    }

    // =============== OMEMO message received =============== //
    fun registerOmemoListener(omemoManager: OmemoManager) {
        mOmemoManager = omemoManager
        omemoManager.addOmemoMessageListener(this)
    }

    private fun unRegisterOmemoListener(omemoManager: OmemoManager?) {
        omemoManager!!.removeOmemoMessageListener(this)
        mOmemoManager = null
    }

    private var isForwardedSentOmemoMessage = false

    /**
     * Creates an instance of this operation set.
     *
     * provider a reference to the `ProtocolProviderServiceImpl` that created us and that we'll
     * use for retrieving the underlying aim connection.
     */
    init {
        mPPS.addRegistrationStateChangeListener(this)
    }

    /**
     * Gets called, whenever an OmemoMessage has been received and was successfully decrypted.
     *
     * @param stanza Received (encrypted) stanza.
     * @param decryptedMessage decrypted OmemoMessage.
     */
    override fun onOmemoMessageReceived(stanza: Stanza, decryptedMessage: Received) {
        // Do not process if decryptedMessage isKeyTransportMessage i.e. msgBody == null
        if (decryptedMessage.isKeyTransportMessage) return
        val message = stanza as Message
        val timeStamp = getTimeStamp(message)
        val userFullJid = if (isForwardedSentOmemoMessage) message.to else message.from
        val userBareJid = userFullJid.asBareJid()
        putJidForAddress(userBareJid, message.thread)
        var contact = opSetPersPresence!!.findContactByJid(userBareJid)
        if (contact == null) {
            // create new volatile contact
            contact = opSetPersPresence!!.createVolatileContact(userBareJid)
        }
        val msgID = message.stanzaId
        val correctedMsgID = getCorrectionMessageId(message)
        var encType = IMessage.ENCRYPTION_OMEMO
        val msgBody = decryptedMessage.body

        // Send by remote when doing Jet-OMEMO jingle file transfer
        // org.jivesoftware.smackx.jingle_filetransfer.component.JingleOutgoingFileOffer@ecd901a
        // if (msgBody != null && msgBody.contains("JingleOutgoingFileOffer"))
        //    return;

        // aTalk OMEMO msgBody may contains markup text then set as ENCODE_HTML mode
        if (msgBody.matches(ChatMessage.HTML_MARKUP)) {
            encType = encType or IMessage.ENCODE_HTML
        }
        var newMessage = createMessageWithUID(msgBody, encType, msgID)

        // check if the message is available in xhtml
        val xhtmString = getXhtmlExtension(message)
        if (xhtmString != null) {
            try {
                val xpp = PacketParserUtils.getParserFor(xhtmString)
                val omemoElement = omemoVAxolotlProvider.parse(xpp)
                val xhtmlMessage = mOmemoManager!!.decrypt(userBareJid, omemoElement)
                encType = encType or IMessage.ENCODE_HTML
                newMessage = createMessageWithUID(xhtmlMessage.body, encType, msgID)
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
        val msgEvt: EventObject
        val sender = message.from.toString()
        if (isForwardedSentOmemoMessage) {
            msgEvt = MessageDeliveredEvent(newMessage, contact, null, sender, timeStamp)
            // Update message unread count for carbon message for the actual recipient.
            NotificationManager.updateMessageCount(contact)
        }
        else {
            msgEvt = MessageReceivedEvent(newMessage, contact, null, sender, timeStamp, correctedMsgID)
        }

        // Start up Chat session if no exist, for offline message when history log is disabled;
        // Else first offline message is not shown
        if (mhs != null && !mhs.isHistoryLoggingEnabled && message.hasExtension(DelayInformation.QNAME)) {
            createChatForContact(contact)
        }
        fireMessageEvent(msgEvt)
    }

    override fun onOmemoCarbonCopyReceived(
            direction: CarbonExtension.Direction,
            carbonCopy: Message, wrappingMessage: Message, decryptedCarbonCopy: Received,
    ) {
        isForwardedSentOmemoMessage = CarbonExtension.Direction.sent == direction
        onOmemoMessageReceived(carbonCopy, decryptedCarbonCopy)

        // Need to reset isForwardedSentOmemoMessage to receive normal Omemo message
        isForwardedSentOmemoMessage = false
    }

    companion object {
        /**
         * A prefix helps to make sure that thread ID's are unique across multiple instances.
         */
        private val prefix = RandomStringUtils.random(5)

        /**
         * Keeps track of the current increment, which is appended to the prefix to forum a unique thread ID.
         */
        private var id: Long = 0

        /**
         * The number of milliseconds that we preserve threads with no traffic before considering them dead.
         */
        private const val JID_INACTIVITY_TIMEOUT = (10 * 60 * 1000).toLong()

        /**
         * Message filter to listen for message sent from DomainJid i.e. server with normal or
         * has extensionElement i.e. XEP-0071: XHTML-IM
         */
        private val MESSAGE_FILTER = AndFilter(
            MessageTypeFilter.NORMAL_OR_CHAT, OrFilter(MessageWithBodiesFilter.INSTANCE,
                StanzaExtensionFilter(XHTMLExtension.ELEMENT, XHTMLExtension.NAMESPACE))
        )
        private val INCOMING_SVR_MESSAGE_FILTER = AndFilter(MESSAGE_FILTER, FromTypeFilter.DOMAIN_BARE_JID)

        /**
         * Returns the next unique thread id. Each thread id made up of a short alphanumeric prefix
         * along with a unique numeric value.
         *
         * @return the next thread id.
         */
        @Synchronized
        private fun nextThreadID(): String {
            return prefix + id++
        }
    }
}