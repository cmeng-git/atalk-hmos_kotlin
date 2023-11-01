/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.chat.conference

import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.MessageListener
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.ChatFragment
import org.atalk.hmos.gui.chat.ChatMessage
import org.atalk.hmos.gui.chat.ChatSession
import org.atalk.hmos.gui.chat.ChatTransport
import org.atalk.hmos.gui.chat.filetransfer.FileSendConversation
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smackx.chatstates.ChatState
import org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jxmpp.jid.EntityBareJid
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * The conference implementation of the `ChatTransport` interface that provides
 * abstraction to access to protocol providers.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class ConferenceChatTransport(chatSession: ChatSession, chatRoom: ChatRoom) : ChatTransport {
    private val chatSession: ChatSession
    private val chatRoom: ChatRoom?
    private val mPPS: ProtocolProviderService
    private var httpFileUploadManager: HttpFileUploadManager? = null

    /**
     * Creates an instance of `ConferenceChatTransport` by specifying the parent chat
     * session and the chat room associated with this transport.
     *
     * chatSession the parent chat session.
     * chatRoom the chat room associated with this conference transport.
     */
    init {
        this.chatSession = chatSession
        this.chatRoom = chatRoom
        mPPS = chatRoom.getParentProvider()

        // mPPS.connection == null from field FER
        if (mPPS.connection != null) {
            isChatStateSupported = mPPS.getOperationSet(OperationSetChatStateNotifications::class.java) != null
            httpFileUploadManager = HttpFileUploadManager.getInstanceFor(mPPS.connection)
        }
    }

    /**
     * Returns the contact address corresponding to this chat transport.
     *
     * @return The contact address corresponding to this chat transport.
     */
    override val name: String
        get() = chatRoom!!.getName()

    /**
     * Returns the display name corresponding to this chat transport.
     *
     * @return The display name corresponding to this chat transport.
     */
    override val displayName: String
        get() = chatRoom!!.getName()

    /**
     * Returns the resource name of this chat transport. This is for example the name of the user
     * agent from which the contact is logged.
     *
     * @return The display name of this chat transport resource.
     */
    override val resourceName: String?
        get() = null

    /**
     * Indicates if the display name should only show the resource.
     *
     * @return `true` if the display name shows only the resource, `false` - otherwise
     */
    override val isDisplayResourceOnly: Boolean
        get() = false

    /**
     * Returns the presence status of this transport.
     *
     * @return the presence status of this transport.
     */
    override val status: PresenceStatus?
        get() = null

    /**
     * Returns the `ProtocolProviderService`, corresponding to this chat transport.
     *
     * @return the `ProtocolProviderService`, corresponding to this chat transport.
     */
    override val protocolProvider: ProtocolProviderService
        get() = mPPS

    /**
     * Returns `true` if this chat transport supports instant messaging,
     * otherwise returns `false`.
     *
     * @return `true` if this chat transport supports instant messaging,
     * otherwise returns `false`.
     */
    override fun allowsInstantMessage(): Boolean {
        return chatRoom!!.isJoined()
    }

    /**
     * Returns `true` if this chat transport supports sms messaging,
     * otherwise returns `false`.
     *
     * @return `true` if this chat transport supports sms messaging,
     * otherwise returns `false`.
     */
    override fun allowsSmsMessage(): Boolean {
        return false
    }

    /**
     * Returns `true` if this chat transport supports message delivery receipts,
     * otherwise returns `false`.
     *
     * @return `true` if this chat transport supports message delivery receipts,
     * otherwise returns `false`
     */
    override fun allowsMessageDeliveryReceipt(): Boolean {
        return false
    }

    /**
     * Returns `true` if this chat transport supports chat state notifications,
     * otherwise returns `false`.
     *
     * @return `true` if this chat transport supports chat state notifications,
     * otherwise returns `false`.
     */
    override fun allowsChatStateNotifications(): Boolean {
        // Object tnOpSet = mPPS.getOperationSet(OperationSetChatStateNotifications.class);
        // return ((tnOpSet != null) && isChatStateSupported);
        return isChatStateSupported
    }

    /**
     * Sends the given instant message trough this chat transport, by specifying the mime type
     * (html or plain text).
     *
     * @param message The message to send.
     * @param encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     * @param encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     */
    @Throws(Exception::class)
    override fun sendInstantMessage(message: String, encType: Int) {
        // If this chat transport does not support instant messaging we do nothing here.
        if (!allowsInstantMessage()) {
            aTalkApp.showToastMessage(R.string.service_gui_CHATROOM_NOT_JOINED)
            return
        }
        val iMessage = chatRoom!!.createMessage(message!!, encType, null)
        if (IMessage.ENCRYPTION_OMEMO == encType and IMessage.ENCRYPTION_OMEMO) {
            val omemoManager = OmemoManager.getInstanceFor(mPPS.connection)
            chatRoom.sendMessage(iMessage, omemoManager)
        } else {
            chatRoom.sendMessage(iMessage)
        }
    }

    /**
     * Sends `message` as a message correction through this transport, specifying the
     * mime type (html or plain text) and the id of the message to replace.
     *
     * @param message The message to send.
     * @param encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     * @param correctedMessageUID The ID of the message being corrected by this message.
     *
     * @see ChatMessage Encryption Type
     */
    override fun sendInstantMessage(message: String, encType: Int, correctedMessageUID: String?) {}

    /**
     * Determines whether this chat transport supports the supplied content type
     *
     * @param mimeType the mime type we want to check
     *
     * @return `true` if the chat transport supports it and `false` otherwise.
     */
    override fun isContentTypeSupported(mimeType: Int): Boolean {
        // we only support plain text for chat rooms for now
        return IMessage.ENCODE_PLAIN == mimeType
    }

    /**
     * Sending sms messages is not supported by this chat transport implementation.
     */
    @Throws(Exception::class)
    override fun sendSmsMessage(phoneNumber: String, message: String) {
    }

    /**
     * Sending sms messages is not supported by this chat transport implementation.
     */
    @Throws(Exception::class)
    override fun sendSmsMessage(message: String) {
    }

    /**
     * Sending file in sms messages is not supported by this chat transport implementation.
     */
    @Throws(Exception::class)
    override fun sendMultimediaFile(file: File): FileTransfer? {
        return null
    }

    /**
     * Sends the given sticker through this chat transport file will always use http file upload
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon and instance of #FileSendConversation
     *
     * @return the HTTPFileUpload object charged to transfer the given `file`.
     * @throws Exception if anything goes wrong
     */
    @Throws(Exception::class)
    override fun sendSticker(file: File, chatType: Int, xferCon: FileSendConversation): Any? {
        return sendFile(file, chatType, xferCon)
    }

    /**
     * Not used.
     *
     * @return status
     */
    override fun askForSMSNumber(): Boolean {
        return false
    }

    /**
     * Sending chat state notifications for this chat transport.
     */
    override fun sendChatStateNotification(chatState: ChatState) {
        // Proceed only if this chat transport allows chat state notification
        if (mPPS.isRegistered && allowsChatStateNotifications() && allowsInstantMessage()) {
            val tnOperationSet = mPPS.getOperationSet(OperationSetChatStateNotifications::class.java)
            try {
                tnOperationSet!!.sendChatStateNotification(chatRoom, chatState)
            } catch (ex: Exception) {
                Timber.e("Failed to send chat state notifications for %s: %s", chatRoom, ex.message)
            }
        }
    }

    /**
     * Sending files through a chat room will always use http file upload
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon and instance of #FileSendConversation
     *
     * @return the HTTPFileUpload object charged to transfer the given `file`.
     * @throws Exception if anything goes wrong
     */
    @Throws(Exception::class)
    override fun sendFile(file: File, chatType: Int, xferCon: FileSendConversation): Any? {
        // If this chat transport does not support file transfer we do nothing and just return.
        return if (!allowsFileTransfer()) null else httpFileUpload(file, chatType, xferCon)
        // return MetaContactChatTransport.httpFileUpload(chatRoom, file, chatType, xferCon, httpFileUploadManager);
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
    private fun httpFileUpload(file: File?, chatType: Int, xferCon: FileSendConversation): Any {
        // check to see if server supports httpFileUpload service if contact is off line or legacy file transfer failed
        return if (httpFileUploadManager!!.isUploadServiceDiscovered) {
            var encType = IMessage.ENCRYPTION_NONE
            val url: Any
            try {
                when (ChatFragment.MSGTYPE_OMEMO) {
                    chatType -> {
                        encType = IMessage.ENCRYPTION_OMEMO
                        url = httpFileUploadManager!!.uploadFileEncrypted(file, xferCon)
                    }
                    else -> {
                        url = httpFileUploadManager!!.uploadFile(file, xferCon)
                    }
                }
                xferCon.setStatus(FileTransferStatusChangeEvent.IN_PROGRESS, chatRoom, encType, "HTTP File Upload")
                url
            } catch (e: InterruptedException) {
                throw OperationNotSupportedException(e.message)
            } catch (e: XMPPException.XMPPErrorException) {
                throw OperationNotSupportedException(e.message)
            } catch (e: SmackException) {
                throw OperationNotSupportedException(e.message)
            } catch (e: IOException) {
                throw OperationNotSupportedException(e.message)
            }
        } else throw OperationNotSupportedException(aTalkApp.getResString(R.string.service_gui_FILE_TRANSFER_NOT_SUPPORTED))
    }

    /**
     * Returns `true` if this chat transport supports file transfer, otherwise returns `false`.
     *
     * @return `true` if this chat transport supports file transfer, otherwise returns `false`.
     */
    private fun allowsFileTransfer(): Boolean {
        return httpFileUploadManager != null && httpFileUploadManager!!.isUploadServiceDiscovered()
    }

    /**
     * Returns the maximum file length supported by the protocol in bytes.
     *
     * @return the file length that is supported.
     */
    override val maximumFileLength: Long
        get() = if (httpFileUploadManager == null) 0 else httpFileUploadManager!!.getDefaultUploadService().getMaxFileSize()

    /**
     * Invites the given contact in this chat conference.
     *
     * @param contactAddress the address of the contact to invite
     * @param reason the reason for the invitation
     */
    override fun inviteChatContact(contactAddress: EntityBareJid, reason: String?) {
        if (chatRoom != null) try {
            chatRoom.invite(contactAddress, reason)
        } catch (e: SmackException.NotConnectedException) {
            Timber.w("Invite chat contact exception: %s", e.message)
        } catch (e: InterruptedException) {
            Timber.w("Invite chat contact exception: %s", e.message)
        }
    }

    /**
     * Returns the parent session of this chat transport. A `ChatSession` could contain
     * more than one transports.
     *
     * @return the parent session of this chat transport
     */
    override val parentChatSession: ChatSession
        get() = chatSession

    /**
     * Adds an sms message listener to this chat transport.
     *
     * @param l The message listener to add.
     */
    override fun addSmsMessageListener(l: MessageListener) {
        // If this chat transport does not support sms messaging we do nothing here.
        if (!allowsSmsMessage()) return
        val smsOpSet = mPPS.getOperationSet(OperationSetSmsMessaging::class.java)
        smsOpSet!!.addMessageListener(l)
    }

    /**
     * Adds an instant message listener to this chat transport.
     *
     * @param l The message listener to add.
     */
    override fun addInstantMessageListener(l: MessageListener) {
        // If this chat transport does not support instant messaging we do nothing here.
        if (!allowsInstantMessage()) return
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
        // If this chat transport does not support instant messaging we do nothing here.
        if (!allowsInstantMessage()) return
        val imOpSet = mPPS.getOperationSet(OperationSetBasicInstantMessaging::class.java)
        imOpSet!!.removeMessageListener(l)
    }

    override fun dispose() {}

    /**
     * Returns the descriptor of this chat transport.
     *
     * @return the descriptor of this chat transport
     */
    override val descriptor: Any?
        get() = chatRoom

    /**
     * Returns `true` if this chat transport supports message corrections and false otherwise.
     *
     * @return `true` if this chat transport supports message corrections and false otherwise.
     */
    override fun allowsMessageCorrections(): Boolean {
        return false
    }

    companion object {
        /**
         * `true` when a contact sends a message with XEP-0085 chat state notifications;
         * override contact disco#info no XEP-0085 feature advertised.
         */
        private var isChatStateSupported = false
        fun setChatStateSupport(isEnable: Boolean) {
            isChatStateSupported = isEnable
        }
    }
}