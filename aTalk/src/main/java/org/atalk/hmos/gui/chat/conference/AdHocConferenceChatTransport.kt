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
import java.io.File
import java.io.IOException
import java.net.URL

/**
 * The conference implementation of the `ChatTransport` interface that provides
 * abstraction to access to protocol providers.
 *
 * @author Valentin Martinet
 * @author Eng Chong Meng
 */
class AdHocConferenceChatTransport(chatSession: ChatSession, chatRoom: AdHocChatRoom?) : ChatTransport {
    private val chatSession: ChatSession
    private val adHocChatRoom: AdHocChatRoom?
    private val mPPS: ProtocolProviderService
    private val httpFileUploadManager: HttpFileUploadManager

    /**
     * Creates an instance of `ConferenceChatTransport` by specifying the parent chat
     * session and the ad-hoc chat room associated with this transport.
     *
     * chatSession the parent chat session.
     * chatRoom the ad-hoc chat room associated with this conference transport.
     */
    init {
        this.chatSession = chatSession
        adHocChatRoom = chatRoom
        mPPS = adHocChatRoom!!.getParentProvider()
        httpFileUploadManager = HttpFileUploadManager.getInstanceFor(mPPS.connection)
    }

    /**
     * Returns the contact address corresponding to this chat transport.
     *
     * @return The contact address corresponding to this chat transport.
     */
    override val name: String
        get() = adHocChatRoom!!.getName()

    /**
     * Returns the display name corresponding to this chat transport.
     *
     * @return The display name corresponding to this chat transport.
     */
    override val displayName: String
        get() = adHocChatRoom!!.getName()

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
        return true
    }

    /**
     * Returns `true` if this chat transport supports sms messaging, otherwise returns `false`.
     *
     * @return `true` if this chat transport supports sms messaging, otherwise returns `false`.
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
     * Returns `true` if this chat transport supports chat state notifications, otherwise returns `false`.
     *
     * @return `true` if this chat transport supports chat state notifications, otherwise returns `false`.
     */
    override fun allowsChatStateNotifications(): Boolean {
        val tnOpSet = mPPS.getOperationSet(OperationSetChatStateNotifications::class.java)
        // isJoined as one of the condition???
        return tnOpSet != null
    }

    /**
     * Sends the given instant message trough this chat transport, by specifying the mime type (html or plain text).
     *
     * @param message The message to send.
     * @param encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     */
    override fun sendInstantMessage(message: String, encType: Int) {
        // If this chat transport does not support instant messaging we do nothing here.
        if (!allowsInstantMessage()) {
            aTalkApp.showToastMessage(R.string.service_gui_SEND_MESSAGE_NOT_SUPPORTED, name)
            return
        }
        val iMessage = adHocChatRoom!!.createMessage(message, encType, null)!!
        if (IMessage.ENCRYPTION_OMEMO == encType and IMessage.ENCRYPTION_MASK) {
            val omemoManager = OmemoManager.getInstanceFor(mPPS.connection)
            adHocChatRoom.sendMessage(iMessage, omemoManager)
        } else {
            adHocChatRoom.sendMessage(iMessage)
        }
    }

    /**
     * Sends `message` as a message correction through this transport, specifying the
     * mime type (html or plain text) and the id of the message to replace.
     *
     * @param message The message to send.
     * @param encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     * @param correctedMessageUID The ID of the message being corrected by this message.
     * @see ChatMessage Encryption Type
     */
    override fun sendInstantMessage(message: String, encType: Int, correctedMessageUID: String?) {}

    /**
     * Determines whether this chat transport supports the supplied content type
     *
     * @param mimeType the mime type we want to check
     * @return `true` if the chat transport supports it and `false` otherwise.
     */
    override fun isContentTypeSupported(mimeType: Int): Boolean {
        // we only support plain text for chat rooms for now
        return IMessage.ENCODE_PLAIN == mimeType
    }

    /**
     * Sending sms messages is not supported by this chat transport implementation.
     */
    override fun sendSmsMessage(phoneNumber: String, message: String) {}

    /**
     * Sending sms messages is not supported by this chat transport implementation.
     */
    @Throws(Exception::class)
    override fun sendSmsMessage(message: String) {
    }

    /**
     * Sending file in sms messages is not supported by this chat transport implementation.
     */
    override fun sendMultimediaFile(file: File): FileTransfer? {
        return null
    }

    /**
     * Sends the given sticker through this chat transport file will always use http file upload
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon and instance of #FileSendConversation
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
     * Sending chat state notifications is not supported by this chat transport implementation.
     */
    override fun sendChatStateNotification(chatState: ChatState) {}

    /**
     * Sending files through a chat room will always use http file upload
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon and instance of #FileSendConversation
     * @return the HTTPFileUpload object charged to transfer the given `file`.
     * @throws Exception if anything goes wrong
     */
    @Throws(Exception::class)
    override fun sendFile(file: File, chatType: Int, xferCon: FileSendConversation): Any? {
        // If this chat transport does not support file transfer we do nothing and just return.
        return if (!allowsFileTransfer()) null else httpFileUpload(file, chatType, xferCon)
    }

    /**
     * Http file upload if supported by the server
     */
    @Throws(Exception::class)
    private fun httpFileUpload(file: File?, chatType: Int, xferCon: FileSendConversation): Any {
        // check to see if server supports httpFileUpload service if contact is off line or legacy file transfer failed
        return if (httpFileUploadManager.isUploadServiceDiscovered) {
            var encType = IMessage.ENCRYPTION_NONE
            val url: Any
            try {
                if (ChatFragment.MSGTYPE_OMEMO == chatType) {
                    encType = IMessage.ENCRYPTION_OMEMO
                    url = httpFileUploadManager.uploadFileEncrypted(file, xferCon)
                } else {
                    url = httpFileUploadManager.uploadFile(file, xferCon)
                }
                xferCon.setStatus(FileTransferStatusChangeEvent.IN_PROGRESS, adHocChatRoom, encType, null)
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
        return httpFileUploadManager.isUploadServiceDiscovered
    }

    /**
     * Returns the maximum file length supported by the protocol in bytes.
     *
     * @return the file length that is supported.
     */
    override val maximumFileLength: Long
        get() = httpFileUploadManager.defaultUploadService.maxFileSize

    /**
     * Invites the given contact in this chat conference.
     *
     * @param contactAddress the address of the contact to invite
     * @param reason the reason for the invitation
     */
    override fun inviteChatContact(contactAddress: EntityBareJid, reason: String?) {
        adHocChatRoom?.invite(contactAddress, reason)
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
        val smsOpSet = mPPS.getOperationSet(OperationSetSmsMessaging::class.java) as OperationSetSmsMessaging
        smsOpSet.removeMessageListener(l)
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
        get() = adHocChatRoom

    /**
     * Returns `true` if this chat transport supports message corrections and false otherwise.
     *
     * @return `true` if this chat transport supports message corrections and false otherwise.
     */
    override fun allowsMessageCorrections(): Boolean {
        return false
    }

    companion object {
        private val mURL: URL? = null
    }
}