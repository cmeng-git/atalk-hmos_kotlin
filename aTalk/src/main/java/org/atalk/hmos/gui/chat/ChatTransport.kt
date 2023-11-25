/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.chat

import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.MessageListener
import org.atalk.hmos.gui.chat.filetransfer.FileSendConversation
import org.jivesoftware.smackx.chatstates.ChatState
import org.jxmpp.jid.EntityBareJid
import java.io.File

/**
 * The `ChatTransport` is an abstraction of the transport method used when sending messages,
 * making calls, etc. through the chat fragment window.
 * A interface class to which the metaContactChat, conference chats are being implemented.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface ChatTransport {
    /**
     * Returns the descriptor object of this ChatTransport.
     *
     * @return the descriptor object of this ChatTransport
     */
    val descriptor: Any?

    /**
     * Returns `true` if this chat transport supports instant
     * messaging, otherwise returns `false`.
     *
     * @return `true` if this chat transport supports instant
     * messaging, otherwise returns `false`
     */
    fun allowsInstantMessage(): Boolean

    /**
     * Returns `true` if this chat transport supports message corrections and false otherwise.
     *
     * @return `true` if this chat transport supports message corrections and false otherwise.
     */
    fun allowsMessageCorrections(): Boolean

    /**
     * Returns `true` if this chat transport supports sms
     * messaging, otherwise returns `false`.
     *
     * @return `true` if this chat transport supports sms
     * messaging, otherwise returns `false`
     */
    fun allowsSmsMessage(): Boolean

    /**
     * Returns `true` if this chat transport supports message delivery receipts,
     * otherwise returns `false`.
     *
     * @return `true` if this chat transport supports message delivery receipts,
     * otherwise returns `false`
     */
    fun allowsMessageDeliveryReceipt(): Boolean

    /**
     * Returns `true` if this chat transport supports chat state
     * notifications, otherwise returns `false`.
     *
     * @return `true` if this chat transport supports chat state
     * notifications, otherwise returns `false`
     */
    fun allowsChatStateNotifications(): Boolean

    /**
     * Returns the name of this chat transport. This is for example the name of the
     * contact in a single chat mode and the name of the chat room in the multi-chat mode.
     *
     * @return The name of this chat transport.
     */
    val name: String

    /**
     * Returns the display name of this chat transport. This is for example the
     * name of the contact in a single chat mode and the name of the chat room
     * in the multi-chat mode.
     *
     * @return The display name of this chat transport.
     */
    val displayName: String

    /**
     * Returns the resource name of this chat transport. This is for example the
     * name of the user agent from which the contact is logged.
     *
     * @return The display name of this chat transport resource.
     */
    val resourceName: String?

    /**
     * Indicates if the display name should only show the resource.
     *
     * @return `true` if the display name shows only the resource, `false` - otherwise
     */
    val isDisplayResourceOnly: Boolean

    /**
     * Returns the presence status of this transport.
     *
     * @return the presence status of this transport.
     */
    val status: PresenceStatus?

    /**
     * Returns the `ProtocolProviderService`, corresponding to this chat transport.
     *
     * @return the `ProtocolProviderService`, corresponding to this chat transport.
     */
    val protocolProvider: ProtocolProviderService

    /**
     * Sends the given instant message trough this chat transport, by specifying
     * the mime type (html or plain text).
     *
     * @param message The message to send.
     * @param encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     * @throws Exception if the send doesn't succeed
     */
    @Throws(Exception::class)
    fun sendInstantMessage(message: String, encType: Int)

    /**
     * Sends `message` as a message correction through this transport,
     * specifying the mime type (html or plain text) and the id of the
     *
     * @param message The message to send.
     * @param encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     * @param correctedMessageUID The ID of the message being corrected by this message.
     */
    fun sendInstantMessage(message: String, encType: Int, correctedMessageUID: String?)

    /**
     * Determines whether this chat transport supports the supplied content type
     *
     * @param mimeType the mime type we want to check
     * @return `true` if the chat transport supports it and `false` otherwise.
     */
    fun isContentTypeSupported(mimeType: Int): Boolean

    /**
     * Whether a dialog need to be opened so the user can enter the destination number.
     *
     * @return `true` if dialog needs to be open.
     */
    fun askForSMSNumber(): Boolean

    /**
     * Sends the given SMS message trough this chat transport.
     *
     * @param phoneNumber the phone number to which to send the message
     * @param message The message to send.
     * @throws Exception if the send doesn't succeed
     */
    @Throws(Exception::class)
    fun sendSmsMessage(phoneNumber: String, message: String)

    /**
     * Sends the given SMS message through this chat transport, leaving the transport to choose the destination.
     *
     * @param message The message to send.
     * @throws Exception if the send doesn't succeed
     */
    @Throws(Exception::class)
    fun sendSmsMessage(message: String)

    /**
     * Sends the given SMS multimedia message through this chat transport,
     * leaving the transport to choose the destination.
     *
     * @param file the file to send
     * @throws Exception if the send doesn't succeed
     */
    @Throws(Exception::class)
    fun sendMultimediaFile(file: File): Any?

    /**
     * Sends the given sticker file through this chat transport,
     * leaving the transport to choose the destination.
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon an instance of FileSendConversation
     *
     * @return the `FileTransfer` or HTTPFileUpload object charged to transfer the given `file`.
     * @throws Exception if the send doesn't succeed
     */
    @Throws(Exception::class)
    fun sendSticker(file: File, chatType: Int, xferCon: FileSendConversation): Any?

    /**
     * Sends the given file through this chat transport.
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon an instance of FileSendConversation
     *
     * @return the `FileTransfer` or HTTPFileUpload object charged to transfer the given `file`.
     * @throws Exception if the send doesn't succeed
     */
    @Throws(Exception::class)
    fun sendFile(file: File, chatType: Int, xferCon: FileSendConversation): Any?

    /**
     * Sends a chat state notification.
     *
     * @param chatState the chat state notification to send
     */
    fun sendChatStateNotification(chatState: ChatState)

    /**
     * Returns `true` if this chat transport supports file transfer, otherwise returns `false`.
     *
     * @return `true` if this chat transport supports file transfer, otherwise returns `false`.
     */
    fun allowsFileTransfer(): Boolean

    /**
     * Returns the maximum file length supported by the protocol in bytes.
     *
     * @return the file length that is supported.
     */
    val maximumFileLength: Long

    /**
     * Invites a contact to join this chat.
     *
     * @param contactAddress the address of the contact we invite
     * @param reason the reason for the invite
     */
    fun inviteChatContact(contactAddress: EntityBareJid, reason: String?)

    /**
     * Returns the parent session of this chat transport. A `ChatSession`
     * could contain more than one transports.
     *
     * @return the parent session of this chat transport
     */
    val parentChatSession: ChatSession

    /**
     * Adds an sms message listener to this chat transport.
     *
     * @param l The message listener to add.
     */
    fun addSmsMessageListener(l: MessageListener)

    /**
     * Adds an instant message listener to this chat transport.
     *
     * @param l The message listener to add.
     */
    fun addInstantMessageListener(l: MessageListener)

    /**
     * Removes the given sms message listener from this chat transport.
     *
     * @param l The message listener to remove.
     */
    fun removeSmsMessageListener(l: MessageListener)

    /**
     * Removes the instant message listener from this chat transport.
     *
     * @param l The message listener to remove.
     */
    fun removeInstantMessageListener(l: MessageListener)

    /**
     * Disposes this chat transport.
     */
    fun dispose()
}