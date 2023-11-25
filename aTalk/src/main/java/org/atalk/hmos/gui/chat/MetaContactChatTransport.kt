/*
 * aTalk, XMPP VoIP and Instant Messaging client
 * Copyright 2023 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.hmos.gui.chat

import android.content.Context
import android.net.Uri
import net.java.sip.communicator.impl.protocol.jabber.OperationSetFileTransferJabberImpl
import net.java.sip.communicator.impl.protocol.jabber.OutgoingFileOfferJingleImpl
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ContactResource
import net.java.sip.communicator.service.protocol.IMessage
import net.java.sip.communicator.service.protocol.OperationNotSupportedException
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging
import net.java.sip.communicator.service.protocol.OperationSetChatStateNotifications
import net.java.sip.communicator.service.protocol.OperationSetContactCapabilities
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer
import net.java.sip.communicator.service.protocol.OperationSetMessageCorrection
import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.OperationSetSmsMessaging
import net.java.sip.communicator.service.protocol.OperationSetThumbnailedFileFactory
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.MessageListener
import net.java.sip.communicator.util.ConfigurationUtils
import org.atalk.crypto.omemo.OmemoAuthenticateDialog
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.filetransfer.FileSendConversation
import org.atalk.persistance.FileBackend
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smackx.chatstates.ChatState
import org.jivesoftware.smackx.hashes.HashManager
import org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager
import org.jivesoftware.smackx.jet.JetManager
import org.jivesoftware.smackx.jet.component.JetSecurityImpl
import org.jivesoftware.smackx.jingle_filetransfer.JingleFileTransferManager
import org.jivesoftware.smackx.jingle_filetransfer.component.JingleFile
import org.jivesoftware.smackx.jingle_filetransfer.component.JingleFileTransferImpl
import org.jivesoftware.smackx.jingle_filetransfer.controller.OutgoingFileOfferController
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException
import org.jivesoftware.smackx.omemo.internal.OmemoDevice
import org.jivesoftware.smackx.omemo.provider.OmemoVAxolotlProvider
import org.jivesoftware.smackx.omemo.util.OmemoConstants
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager
import org.jxmpp.jid.DomainBareJid
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.FullJid
import org.jxmpp.jid.Jid
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.security.NoSuchAlgorithmException
import javax.net.ssl.SSLHandshakeException

/**
 * The single chat implementation of the `ChatTransport` interface that provides abstraction
 * to protocol provider access and its supported features available to the metaContact.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
/**
 * The parent `ChatSession`, where this transport is available.
 */
class MetaContactChatTransport @JvmOverloads constructor(
        override val parentChatSession: MetaContactChatSession,
        contact: Contact,
        contactResource: ContactResource? = null,
        isDisplayResourceOnly: Boolean = false,
) : ChatTransport, ContactPresenceStatusListener {

    /**
     * The associated protocol `Contact`.
     */
    private val mContact: Contact

    /**
     * The resource associated with this contact.
     */
    private val mContactResource: ContactResource?

    /**
     * The associated protocol provider service for the `Contact`.
     */
    private val mPPS: ProtocolProviderService

    private val ftOpSet: OperationSetFileTransferJabberImpl?
    private lateinit var httpFileUploadManager: HttpFileUploadManager
    private var jingleFTManager: JingleFileTransferManager? = null
    private var jetManager: JetManager? = null

    /**
     * `true` when a contact sends a message with XEP-0164 message delivery receipt;
     * override contact disco#info no XEP-0184 feature advertised.
     */
    private var isDeliveryReceiptSupported = false

    /**
     * The protocol presence operation set associated with this transport.
     */
    private val presenceOpSet: OperationSetPresence?

    /**
     * Indicates if only the resource name should be displayed.
     */
    override val isDisplayResourceOnly: Boolean
    /**
     * Creates an instance of `MetaContactChatTransport` by specifying the parent
     * `chatSession`, `contact`, and the `contactResource` associated with the transport.
     *
     * parentChatSession the parent `ChatSession`
     * contact the `Contact` associated with this transport
     * contactResource the `ContactResource` associated with the contact
     * isDisplayResourceOnly indicates if only the resource name should be displayed
     */
    /**
     * Creates an instance of `MetaContactChatTransport` by specifying the parent
     * `chatSession` and the `contact` associated with the transport.
     *
     * chatSession the parent `ChatSession`
     * contact the `Contact` associated with this transport
     */
    init {
        mContact = contact
        mContactResource = contactResource
        this.isDisplayResourceOnly = isDisplayResourceOnly
        mPPS = contact.protocolProvider
        ftOpSet = mPPS.getOperationSet(OperationSetFileTransfer::class.java) as OperationSetFileTransferJabberImpl
        presenceOpSet = mPPS.getOperationSet(OperationSetPresence::class.java)
        presenceOpSet?.addContactPresenceStatusListener(this)

        // Timber.d("Transport mContact: %s (%s)", mContact, mContact instanceof VolatileContactJabberImpl);
        isChatStateSupported = mPPS.getOperationSet(OperationSetChatStateNotifications::class.java) != null

        // checking these can be slow so make sure they are run in new thread
        object : Thread() {
            override fun run() {
                val connection = mPPS.connection
                if (connection != null) {
                    // For unencrypted file transfer
                    jingleFTManager = JingleFileTransferManager.getInstanceFor(connection)

                    // For encrypted file transfer using Jet and OMEMO encryption
                    JetManager.registerEnvelopeProvider(OmemoConstants.OMEMO_NAMESPACE_V_AXOLOTL,
                        OmemoVAxolotlProvider())
                    jetManager = JetManager.getInstanceFor(connection)
                    jetManager!!.registerEnvelopeManager(OmemoManager.getInstanceFor(connection))

                    // For HttpFileUpload service
                    httpFileUploadManager = HttpFileUploadManager.getInstanceFor(connection)
                    isDeliveryReceiptSupported = checkDeliveryReceiptSupport(connection)
                }
                checkImCaps()
            }
        }.start()
    }

    /**
     * Check for Delivery Receipt support for all registered contacts (ANR from field - so run in thread)
     * Currently isDeliveryReceiptSupported is not used - Smack autoAddDeliveryReceiptRequests support is global
     */
    private fun checkDeliveryReceiptSupport(connection: XMPPConnection?): Boolean {
        var isSupported = false
        var fullJid: Jid? = null

        // ANR from field - check isAuthenticated() before proceed
        if (connection != null && connection.isAuthenticated) {
            val deliveryReceiptManager = DeliveryReceiptManager.getInstanceFor(connection)
            val presences = Roster.getInstanceFor(connection).getPresences(mContact.contactJid!!.asBareJid())
            for (presence in presences) {
                fullJid = presence.from
                try {
                    if (fullJid != null && deliveryReceiptManager.isSupported(fullJid)) {
                        isSupported = true
                        break
                    }
                } catch (e: XMPPException) {
                    // AbstractXMPPConnection.createStanzaCollectorAndSend() throws IllegalArgumentException
                    Timber.w("Check Delivery Receipt exception for %s: %s", fullJid, e.message)
                } catch (e: SmackException) {
                    Timber.w("Check Delivery Receipt exception for %s: %s", fullJid, e.message)
                } catch (e: InterruptedException) {
                    Timber.w("Check Delivery Receipt exception for %s: %s", fullJid, e.message)
                } catch (e: IllegalArgumentException) {
                    Timber.w("Check Delivery Receipt exception for %s: %s", fullJid, e.message)
                }
            }
            Timber.d("isDeliveryReceiptSupported for: %s = %s", fullJid, isSupported)
        }
        return isSupported
    }

    /**
     * If sending im is supported check it for supporting html messages if a font is set.
     * As it can be slow make sure its not on our way
     */
    private fun checkImCaps() {
        if (ConfigurationUtils.chatDefaultFontFamily != null && ConfigurationUtils.chatDefaultFontSize > 0) {
            mPPS.getOperationSet(OperationSetBasicInstantMessaging::class.java)?.isContentTypeSupported(
                IMessage.ENCODE_HTML, mContact)
        }
    }

    /**
     * Returns the contact associated with this transport.
     *
     * @return the contact associated with this transport
     */
    val contact: Contact
        get() = mContact

    /**
     * Returns the contact address corresponding to this chat transport.
     *
     * @return The contact address corresponding to this chat transport.
     */
    override val name: String
        get() = mContact.address

    /**
     * Returns the display name corresponding to this chat transport.
     *
     * @return The display name corresponding to this chat transport.
     */
    override val displayName: String
        get() = mContact.displayName

    /**
     * The contact resource of this chat transport that encapsulate contact information of the contact who is logged.
     */
    val contactResource: ContactResource?
        get() = mContactResource

    /**
     * The resource name of this chat transport. This is for example the name of the
     * user agent from which the contact is logged.
     */
    override val resourceName: String?
        get() = mContactResource?.resourceName

    /**
     * The presence status of this transport; with the higher threshold of the two when (contactResource != null)
     */
    override val status: PresenceStatus
        get() {
            val contactStatus = mContact.presenceStatus
            return if (mContactResource != null) {
                val resourceStatus = mContactResource.presenceStatus
                if (resourceStatus < contactStatus) contactStatus else resourceStatus
            }
            else {
                contactStatus
            }
        }

    /**
     * The `ProtocolProviderService`, corresponding to this chat transport.
     */
    override val protocolProvider: ProtocolProviderService
        get() = mPPS

    /**
     * `true` if this chat transport supports instant messaging, otherwise returns `false`.
     */
    override fun allowsInstantMessage(): Boolean {
        // First try to ask the capabilities operation set if such is available.
        val capOpSet = mPPS.getOperationSet(OperationSetContactCapabilities::class.java)
        return if (capOpSet != null) {
            if (mContact.contactJid!!.asEntityBareJidIfPossible() == null) {
                isChatStateSupported = false
                return false
            }
            true
        }
        else mPPS.getOperationSet(OperationSetBasicInstantMessaging::class.java) != null
    }

    /**
     * `true` if this chat transport supports message corrections and false otherwise.
     */
    override fun allowsMessageCorrections(): Boolean {
        val capOpSet = protocolProvider.getOperationSet(OperationSetContactCapabilities::class.java)
        return if (capOpSet != null) {
            true
        }
        else {
            mPPS.getOperationSet(OperationSetMessageCorrection::class.java) != null
        }
    }

    /**
     * `true` if this chat transport supports sms messaging, otherwise returns `false`.
     */
    override fun allowsSmsMessage(): Boolean {
        // First try to ask the capabilities operation set if such is available.
        val capOpSet = protocolProvider.getOperationSet(OperationSetContactCapabilities::class.java)
        return if (capOpSet != null) {
            capOpSet.getOperationSet(mContact, OperationSetSmsMessaging::class.java) != null
        }
        else mPPS.getOperationSet(OperationSetSmsMessaging::class.java) != null
    }

    /**
     * `true` if this chat transport supports message delivery receipts, otherwise returns `false`.
     * User SHOULD explicitly discover whether the Contact supports the protocol or negotiate the
     * use of message delivery receipt with the Contact (e.g., via XEP-0184 Stanza Session Negotiation).
     */
    override fun allowsMessageDeliveryReceipt(): Boolean {
        return isDeliveryReceiptSupported
    }

    /**
     * Returns `true` if this chat transport supports chat state notifications, otherwise returns `false`.
     * User SHOULD explicitly discover whether the Contact supports the protocol or negotiate the
     * use of chat state notifications with the Contact (e.g., via XEP-0155 Stanza Session Negotiation).
     */
    override fun allowsChatStateNotifications(): Boolean {
        // Object tnOpSet = mPPS.getOperationSet(OperationSetChatStateNotifications.class);
        // return ((tnOpSet != null) && isChatStateSupported);
        return isChatStateSupported
    }

    /**
     * `true` if this chat transport supports file transfer, otherwise returns `false`.
     */
    override fun allowsFileTransfer(): Boolean {
        return ftOpSet != null || hasUploadService()
    }

    private fun hasUploadService(): Boolean {
        return httpFileUploadManager.isUploadServiceDiscovered
    }

    /**
     * Sends the given instant message through this chat transport, by specifying the mime type (html or plain text).
     *
     * @param message The message to send.
     * @param encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     */
    override fun sendInstantMessage(message: String, encType: Int) {
        // If this chat transport does not support instant messaging we do nothing here.
        var iEncType = encType
        if (!allowsInstantMessage()) {
            aTalkApp.showToastMessage(R.string.service_gui_SEND_MESSAGE_NOT_SUPPORTED, name)
            return
        }
        val imOpSet = mPPS.getOperationSet(OperationSetBasicInstantMessaging::class.java)
        // Strip HTML flag if ENCODE_HTML not supported by the operation
        if (!imOpSet!!.isContentTypeSupported(IMessage.ENCODE_HTML)) iEncType = iEncType and IMessage.FLAG_MODE_MASK
        val msg = imOpSet.createMessage(message, iEncType, "")
        val toResource = mContactResource ?: ContactResource.BASE_RESOURCE
        if (IMessage.ENCRYPTION_OMEMO == iEncType and IMessage.ENCRYPTION_MASK) {
            val omemoManager = OmemoManager.getInstanceFor(mPPS.connection)
            imOpSet.sendInstantMessage(mContact, toResource, msg, null, omemoManager)
        }
        else {
            imOpSet.sendInstantMessage(mContact, toResource, msg)
        }
    }

    /**
     * Sends `message` as a message correction through this transport, specifying the mime
     * type (html or plain text) and the id of the message to replace.
     *
     * @param message The message to send.
     * @param encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     * @param correctedMessageUID The ID of the message being corrected by this message.
     */
    override fun sendInstantMessage(message: String, encType: Int, correctedMessageUID: String?) {
        var iEncType = encType
        if (!allowsMessageCorrections()) {
            return
        }
        val mcOpSet = mPPS.getOperationSet(OperationSetMessageCorrection::class.java)
        if (!mcOpSet!!.isContentTypeSupported(IMessage.ENCODE_HTML)) iEncType = iEncType and IMessage.FLAG_MODE_MASK
        val msg = mcOpSet.createMessage(message, iEncType, "")
        val toResource = mContactResource ?: ContactResource.BASE_RESOURCE
        if (IMessage.ENCRYPTION_OMEMO == iEncType and IMessage.ENCRYPTION_MASK) {
            val omemoManager = OmemoManager.getInstanceFor(mPPS.connection)
            mcOpSet.sendInstantMessage(mContact, toResource, msg, correctedMessageUID, omemoManager)
        }
        else {
            mcOpSet.correctMessage(mContact, toResource, msg, correctedMessageUID)
        }
    }

    /**
     * Determines whether this chat transport supports the supplied content type
     *
     * @param mimeType the mime type we want to check
     *
     * @return `true` if the chat transport supports it and `false` otherwise.
     */
    override fun isContentTypeSupported(mimeType: Int): Boolean {
        val imOpSet = mPPS.getOperationSet(OperationSetBasicInstantMessaging::class.java)
        return imOpSet != null && imOpSet.isContentTypeSupported(mimeType)
    }

    /**
     * Sends the given sms message through this chat transport.
     *
     * @param phoneNumber phone number of the destination
     * @param message The message to send.
     *
     * @throws Exception if the send operation is interrupted
     */
    @Throws(Exception::class)
    override fun sendSmsMessage(phoneNumber: String, message: String) {
        // If this chat transport does not support sms messaging we do nothing here.
        if (allowsSmsMessage()) {
            Timber.w("Method not implemented")
            // SMSManager.sendSMS(mPPS, phoneNumber, messageText);}
        }
    }

    /**
     * Whether a dialog need to be opened so the user can enter the destination number.
     *
     * @return `true` if dialog needs to be open.
     */
    override fun askForSMSNumber(): Boolean {
        // If this chat transport does not support sms messaging we do nothing here.
        if (!allowsSmsMessage()) return false
        val smsOpSet = mPPS.getOperationSet(OperationSetSmsMessaging::class.java)
        return smsOpSet!!.askForNumber(mContact)
    }

    /**
     * Sends the given sms message through this chat transport.
     *
     * @param message the message to send
     *
     * @throws Exception if the send operation is interrupted
     */
    @Throws(Exception::class)
    override fun sendSmsMessage(message: String) {
        // If this chat transport does not support sms messaging we do nothing here.
        if (allowsSmsMessage()) {
            Timber.w("Method not implemented")
            // SMSManager.sendSMS(contact, message);
        }
    }

    /**
     * Sends a chat state notification.
     *
     * @param chatState the chat state notification to send
     */
    override fun sendChatStateNotification(chatState: ChatState) {
        // If this chat transport does not allow chat state notification then just return
        if (allowsChatStateNotifications()) {
            // if protocol is not registered or contact is offline don't try to send chat state notifications
            if (mPPS.isRegistered && mContact.presenceStatus.status >= PresenceStatus.ONLINE_THRESHOLD) {
                val tnOperationSet = mPPS.getOperationSet(OperationSetChatStateNotifications::class.java)
                try {
                    tnOperationSet!!.sendChatStateNotification(mContact, chatState)
                } catch (ex: Exception) {
                    Timber.e(ex, "Failed to send chat state notifications.")
                }
            }
        }
    }

    /**
     * Sends the given sticker through this chat transport file transfer operation set.
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon an instance of FileSendConversation
     *
     * @return the `FileTransfer` or HTTPFileUpload object charged to transfer the given `file`.
     * @throws Exception if anything goes wrong
     */
    @Throws(Exception::class)
    override fun sendSticker(file: File, chatType: Int, xferCon: FileSendConversation): Any? {
        // If this chat transport does not support file transfer we do nothing and just return.
        return if (!allowsFileTransfer()) null else getFileTransferTransport(file, chatType, xferCon)
    }

    /**
     * Sends the given SMS multimedia message via this chat transport, leaving the
     * transport to choose the destination.
     *
     * @param file the file to send
     *
     * @throws Exception if the send file is unsuccessful
     */
    @Throws(Exception::class)
    override fun sendMultimediaFile(file: File): Any? {
        return sendFile(file, true, ChatFragment.MSGTYPE_NORMAL, null)
    }

    /**
     * Sends the given file through this chat transport file transfer operation set.
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon an instance of FileSendConversation
     *
     * @return the `FileTransfer` or HTTPFileUpload object charged to transfer the given `file`.
     * @throws Exception if anything goes wrong
     */
    @Throws(Exception::class)
    override fun sendFile(file: File, chatType: Int, xferCon: FileSendConversation): Any? {
        return sendFile(file, false, chatType, xferCon)
    }

    /**
     * Sends the given file through this chat transport file transfer operation set.
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon an instance of FileSendConversation
     *
     * @return the `FileTransfer` or HTTPFileUpload object charged to transfer the given `file`.
     * @throws Exception if anything goes wrong
     */
    @Throws(Exception::class)
    private fun sendFile(
            file: File, isMultimediaMessage: Boolean, chatType: Int,
            xferCon: FileSendConversation?,
    ): Any? {
        // If this chat transport does not support file transfer we do nothing and just return.
        var sFile = file
        if (!allowsFileTransfer()) return null
        val tfOpSet = mPPS.getOperationSet(OperationSetThumbnailedFileFactory::class.java)
        if (tfOpSet != null) {
            val thumbnail = xferCon!!.fileThumbnail
            if (thumbnail != null && thumbnail.isNotEmpty()) {
                sFile = tfOpSet.createFileWithThumbnail(sFile, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, "image/png",
                    thumbnail)
            }
        }
        return if (isMultimediaMessage) {
            val smsOpSet = mPPS.getOperationSet(OperationSetSmsMessaging::class.java) ?: return null
            smsOpSet.sendMultimediaFile(mContact, sFile)
        }
        else {
            getFileTransferTransport(sFile, chatType, xferCon)
        }
    }

    /**
     * Process to determine the appropriate file transfer transport based on:
     * a. contact online status
     * b. file transfer protocol supported by the recipient contact,
     * c. current active session i.e. chatType,
     * d. server httpFileUpload service support
     * e. fallback on failure for legacy byteStream transfer protocol.
     *
     * The file transport is selected with the following priority order if contact is online:
     * a. jingleFileSend (Secure JET or Plain)
     * b. httpFileUpload (for OMEMO and plain chat session: chatType)
     * c. Legacy byteStream transfer protocol for SOCK5 with fallback on IBB on user retry
     * #see [](https://xmpp.org/extensions/xep-0096.html)XEP-0096: SI File Transfer 1.3.1 (2022-03-22)
     *
     * file the file to send
     * chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * xferCon an instance of FileSendConversation
     *
     * @return the `FileTransfer` or HTTPFileUpload object charged to transfer the given `file`.
     * @throws Exception if anything goes wrong
     */
    @Throws(Exception::class)
    private fun getFileTransferTransport(file: File, chatType: Int, xferCon: FileSendConversation?): Any {
        return if (status.isOnline) {
            try {
                // Try jingle file transfer protocol as first attempt if supported by buddy
                jingleFileSend(file, chatType, xferCon)

                /* ==== For testing Bytestream or httpFileUpload only ==== */
                // return httpFileUpload(file, chatType, xferCon);
                // return ftOpSet.sendFile(mContact, file, xferCon.getMessageUuid());
            } catch (ex: OperationNotSupportedException) {
                // Use http file upload if available.
                try {
                    httpFileUpload(file, chatType, xferCon)
                } catch (ex2: OperationNotSupportedException) {
                    // Use legacy FileTransfer starting with SOCKS5, fallback to IBB ByteStream transfer.
                    ftOpSet!!.sendFile(mContact, file, xferCon!!.msgUuid)
                }
            }
        }
        else {
            // Use http file upload for all media file sharing for offline user
            httpFileUpload(file, chatType, xferCon)
        }
    }

    /**
     * Use Jingle File Transfer or Http file upload that is supported by the transport
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon an instance of FileSendConversation
     *
     * @return `OutgoingFileOfferController` or HTTPFileUpload object to transfer the given `file`.
     * @throws Exception if anything goes wrong
     */
    @Throws(Exception::class)
    private fun jingleFileSend(file: File, chatType: Int, xferCon: FileSendConversation?): OutgoingFileOfferJingleImpl {
        // toJid is not null if contact is online and supports the jet/jingle file transfer
        val recipient = if (ChatFragment.MSGTYPE_OMEMO == chatType) {
            ftOpSet!!.getFullJid(mContact, JetSecurityImpl.NAMESPACE, JingleFileTransferImpl.NAMESPACE)
        }
        else {
            ftOpSet!!.getFullJid(mContact, JingleFileTransferImpl.NAMESPACE)
        }

        // Conversations allows Jet FileSent but failed with session-terminate and reason = connectivity-error
        // So retry with HttpFileUpload if previously hasSecurityError
        return if (recipient != null && (!OutgoingFileOfferJingleImpl.hasSecurityError(
                    mContact) || ChatFragment.MSGTYPE_OMEMO != chatType)) {
            val ofoController: OutgoingFileOfferController
            var encType = IMessage.ENCRYPTION_NONE
            val msgUuid = xferCon!!.msgUuid
            val ctx = aTalkApp.globalContext
            val jingleFile = createJingleFile(ctx, file)
            val omemoManager = OmemoManager.getInstanceFor(mPPS.connection)
            try {
                if (ChatFragment.MSGTYPE_OMEMO == chatType) {
                    encType = IMessage.ENCRYPTION_OMEMO
                    ofoController = jetManager!!.sendEncryptedFile(file, jingleFile, recipient, omemoManager)
                }
                else {
                    // For testing only: forced to use next in priority file transfer
                    // throw new OperationNotSupportedException("Use next available File Transfer");
                    ofoController = jingleFTManager!!.sendFile(file, jingleFile, recipient)
                }
                // Let OutgoingFileOfferJingleImpl handle status changes
                // xferCon.setStatus(FileTransferStatusChangeEvent.IN_PROGRESS, mContact, encType, "JingleFile Sending");
                OutgoingFileOfferJingleImpl(mContact, file, msgUuid, ofoController, mPPS.connection!!)
            } catch (ex: SSLHandshakeException) {
                throw OperationNotSupportedException(if (ex.cause != null) ex.cause!!.message else ex.message)
            } catch (e: UndecidedOmemoIdentityException) {
                // Display dialog for use to verify omemoDevice; throw OperationNotSupportedException to use other methods for this file transfer.
                val omemoAuthListener = OmemoAuthenticateListener(recipient, omemoManager)
                ctx.startActivity(
                    OmemoAuthenticateDialog.createIntent(ctx, omemoManager, e.undecidedDevices, omemoAuthListener))
                throw OperationNotSupportedException(e.message)
            } catch (e: InterruptedException) {
                throw OperationNotSupportedException(e.message)
            } catch (e: XMPPException.XMPPErrorException) {
                throw OperationNotSupportedException(e.message)
            } catch (e: SmackException) {
                throw OperationNotSupportedException(e.message)
            } catch (e: IOException) {
                throw OperationNotSupportedException(e.message)
            }
        }
        else {
            throw OperationNotSupportedException(
                aTalkApp.getResString(R.string.service_gui_FILE_TRANSFER_NOT_SUPPORTED))
        }
    }

    /**
     * Create JingleFile from the given file
     *
     * @param file sending file
     *
     * @return JingleFile metaData
     */
    private fun createJingleFile(ctx: Context, file: File): JingleFile? {
        var jingleFile: JingleFile? = null
        val mimeType = FileBackend.getMimeType(ctx, Uri.fromFile(file))
        try {
            jingleFile = JingleFile.fromFile(file, null, mimeType, HashManager.ALGORITHM.SHA3_256)
        } catch (e: NoSuchAlgorithmException) {
            Timber.e("JingleFile creation error: %s", e.message)
        } catch (e: IOException) {
            Timber.e("JingleFile creation error: %s", e.message)
        }
        return jingleFile
    }

    /**
     * Omemo listener callback on user authentication for undecided omemoDevices
     */
    private class OmemoAuthenticateListener(
            recipient: FullJid,
            omemoManager: OmemoManager,
    ) : OmemoAuthenticateDialog.AuthenticateListener {
        var recipient: FullJid
        var omemoManager: OmemoManager

        init {
            this.recipient = recipient
            this.omemoManager = omemoManager
        }

        override fun onAuthenticate(allTrusted: Boolean, omemoDevices: Set<OmemoDevice>?) {
            if (!allTrusted) {
                aTalkApp.showToastMessage(R.string.omemo_send_error,
                    "Undecided Omemo Identity: " + omemoDevices.toString())
            }
        }
    }

    /**
     * Http file upload if supported by the server
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon an instance of FileSendConversation
     *
     * @return the `FileTransfer` or HTTPFileUpload object charged to transfer the given `file`.
     * @throws Exception if anything goes wrong
     */
    @Throws(Exception::class)
    private fun httpFileUpload(file: File, chatType: Int, xferCon: FileSendConversation?): Any {
        // check to see if server supports httpFileUpload service if contact is off line or legacy file transfer failed
        return if (hasUploadService()) {
            var encType = IMessage.ENCRYPTION_NONE
            val url: Any
            try {
                if (ChatFragment.MSGTYPE_OMEMO == chatType) {
                    encType = IMessage.ENCRYPTION_OMEMO
                    url = httpFileUploadManager.uploadFileEncrypted(file, xferCon)
                }
                else {
                    url = httpFileUploadManager.uploadFile(file, xferCon)
                }
                xferCon!!.setStatus(FileTransferStatusChangeEvent.IN_PROGRESS, mContact, encType, "HTTP File Upload")
                url
            } catch (ex: SSLHandshakeException) {
                throw OperationNotSupportedException(if (ex.cause != null) ex.cause!!.message else ex.message)
            } // uploadFile exception; uploadFileEncrypted will throw Exception
            catch (e: InterruptedException) {
                throw OperationNotSupportedException(e.message)
            } catch (e: XMPPException.XMPPErrorException) {
                throw OperationNotSupportedException(e.message)
            } catch (e: SmackException) {
                throw OperationNotSupportedException(e.message)
            } catch (e: IOException) {
                throw OperationNotSupportedException(e.message)
            }
        }
        else throw OperationNotSupportedException(
            aTalkApp.getResString(R.string.service_gui_FILE_TRANSFER_NOT_SUPPORTED))
    }

    /**
     * Returns the maximum file length supported by the protocol in bytes.
     *
     * @return the file length that is supported.
     */
    override val maximumFileLength: Long
        get() = ftOpSet!!.getMaximumFileLength()

    override fun inviteChatContact(contactAddress: EntityBareJid, reason: String?) {}

    /**
     * Adds an SMS message listener to this chat transport.
     *
     * l The message listener to add.
     */
    override fun addSmsMessageListener(l: MessageListener) {
        // If this chat transport does not support sms messaging we do nothing here.
        if (!allowsSmsMessage()) return
        val smsOpSet = mPPS.getOperationSet(OperationSetSmsMessaging::class.java)
        smsOpSet!!.addMessageListener(l)
    }

    /**
     * Adds an instant message listener to this chat transport.
     * Special case for DomainJid to display received messages from server
     *
     * @param l The message listener to add.
     */
    override fun addInstantMessageListener(l: MessageListener) {
        // Skip if this chat transport does not support instant messaging; except if it is a DomainJid
        if (!allowsInstantMessage() && mContact.contactJid !is DomainBareJid) return
        val imOpSet = mPPS.getOperationSet(OperationSetBasicInstantMessaging::class.java)
        imOpSet!!.addMessageListener(l)
    }

    /**
     * Removes the given sms message listener from this chat transport.
     *
     * @param l The message listener to remove.
     */
    override fun removeSmsMessageListener(l: MessageListener) {
        // If this chat transport does not support sms messaging we do nothing here.
        if (!allowsSmsMessage()) return
        val smsOpSet = mPPS.getOperationSet(OperationSetSmsMessaging::class.java)
        smsOpSet!!.removeMessageListener(l)
    }

    /**
     * Removes the instant message listener from this chat transport.
     *
     * @param l The message listener to remove.
     */
    override fun removeInstantMessageListener(l: MessageListener) {
        // Skip if this chat transport does not support instant messaging; except if it is a DomainJid
        if (!allowsInstantMessage() && mContact.contactJid !is DomainBareJid) return
        val imOpSet = mPPS.getOperationSet(OperationSetBasicInstantMessaging::class.java)
        imOpSet!!.removeMessageListener(l)
    }

    /**
     * Indicates that a contact has changed its status.
     *
     * @param evt The presence event containing information about the contact status change.
     */
    override fun contactPresenceStatusChanged(evt: ContactPresenceStatusChangeEvent) {
        // If the contactResource is set then the status will be updated from the MetaContactChatSession.
        // cmeng: contactResource condition removed to fix contact goes offline<->online // && (contactResource == null)
        if (evt.getSourceContact() == mContact && evt.getOldStatus() != evt.getNewStatus()) {
            updateContactStatus()
        }
    }

    /**
     * Updates the status of this contact with the new given status.
     */
    private fun updateContactStatus() {
        // Update the status of the given contact in the "send via" selector box.
        parentChatSession.chatSessionRenderer.updateChatTransportStatus(this)
    }

    /**
     * Removes all previously added listeners.
     */
    override fun dispose() {
        presenceOpSet?.removeContactPresenceStatusListener(this)
    }

    /**
     * Returns the descriptor of this chat transport.
     *
     * @return the descriptor of this chat transport
     */
    override val descriptor: Any
        get() = mContact

    companion object {
        /**
         * `true` when a contact sends a message with XEP-0085 chat state notifications;
         * override contact disco#info no XEP-0085 feature advertised.
         */
        private var isChatStateSupported = false

        /**
         * The thumbnail default width.
         */
        private const val THUMBNAIL_WIDTH = 64

        /**
         * The thumbnail default height.
         */
        private const val THUMBNAIL_HEIGHT = 64
        fun setChatStateSupport(isEnable: Boolean) {
            isChatStateSupported = isEnable
        }
    }

}