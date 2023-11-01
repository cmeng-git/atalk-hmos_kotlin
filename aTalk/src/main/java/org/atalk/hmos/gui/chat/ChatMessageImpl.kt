/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.chat

import android.text.TextUtils
import net.java.sip.communicator.impl.protocol.jabber.HttpFileDownloadJabberImpl
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.filehistory.FileRecord
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.protocol.AccountInfoUtils
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.IMessage
import net.java.sip.communicator.service.protocol.IncomingFileTransferRequest
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer
import net.java.sip.communicator.service.protocol.OperationSetServerStoredAccountInfo
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageReceivedEvent
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent
import org.apache.commons.text.StringEscapeUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import timber.log.Timber
import java.io.File
import java.util.*

/**
 * The `ChatMessageImpl` class encapsulates message information in order to provide a
 * single object containing all data needed to display a chat message.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ChatMessageImpl(
        /**
         * The string Id of the message sender. The value is used in quoted messages.
         * Actual value pending message type i.e.:
         * userId: delivered chat message e.g. swordfish@atalk.org
         * contactId: received chat message e.g. leopoard@atalk.org
         * chatRoom: delivered group chat message e.g. conference@atalk.org
         * nickName: received group chat message e.g. leopard
         *
         * Exception as recipient:
         * contactId: ChatMessage.MESSAGE_FILE_TRANSFER_SEND & ChatMessage.MESSAGE_STICKER_SEND:
         */
        override val sender: String?,
        /**
         * The display name of the message sender. It may be the same as sender
         */
        override val senderName: String?,
        /**
         * The date and time of the message.
         */
        override val date: Date,
        /**
         * The type of the message.
         */
        override var messageType: Int,
        /**
         * The mime type of the message content.
         */
        override val mimeType: Int,
        /**
         * The content of the message.
         */
        override val contentForClipboard: String?,
        /**
         * The encryption type of the message content.
         */
        override val encryptionType: Int,
        /**
         * A unique identifier for this message.
         */
        override val uidForCorrection: String?,
        /**
         * The unique identifier of the last message that this message should replace,
         * or `null` if this is a new message.
         */
        override val correctedMessageUID: String?,
        /**
         * The direction of the message.
         */
        override val messageDir: String?,
        /**
         * The HTTP file download file transfer status
         */
        override var xferStatus: Int,
        /**
         * The encryption type of the message content.
         */
        override var receiptStatus: Int,
        /**
         * The sent message stanza Id.
         */
        override val serverMsgId: String?,
        /**
         * The received message stanza Id.
         */
        override val remoteMsgId: String?,

        opSet: OperationSetFileTransfer?, request: Any?, fileRecord: FileRecord?) : ChatMessage {

    /**
     * Returns the name of the entity sending the message.
     *
     * @return the name of the entity sending the message.
     */
    /**
     * Returns the display name of the entity sending the message.
     *
     * @return the display name of the entity sending the message
     */
    /**
     * Returns the date and time of the message.
     *
     * @return the date and time of the message.
     */
    /**
     * Returns the type of the message.
     *
     * @return the type of the message.
     */
    /**
     * Set the type of the message.
     */
    /**
     * Returns the mime type of the content type (e.g. "text", "text/html", etc.).
     *
     * @return the mimeType
     */
    /**
     * {@inheritDoc}
     */
    /**
     * {@inheritDoc}
     */
    /**
     * Returns the message direction i.e. in/put.
     *
     * @return the direction of this message.
     */

    /**
     * Returns the message delivery receipt status
     *
     * @return the receipt status
     */
    /**
     * Returns the encryption type of the original received message.
     *
     * @return the encryption type
     */
    /**
     * Returns the UID of this message.
     *
     * @return the UID of this message.
     */
    /**
     * {@inheritDoc}
     */
    /**
     * Returns the UID of the message that this message replaces, or `null` if this is a new message.
     *
     * @return the UID of the message that this message replaces, or `null` if this is a new message.
     */
    /**
     * Returns the server message Id of the message sent - for tracking delivery receipt
     *
     * @return the server message Id of the message sent.
     */
    /**
     * Returns the remote message Id of the message received - for tracking delivery receipt
     *
     * @return the remote message Id of the message received.
     */

    /**
     * Field used to cache processed message body after replacements and corrections. This text is
     * used to display the message on the screen.
     */
    private var cachedOutput: String? = null

    /**
     * The file transfer OperationSet (event).
     */
    override val opSetFT: OperationSetFileTransfer?

    /**
     * The Incoming file transfer request (event).
     */
    private var request: IncomingFileTransferRequest? = null
    override var httpFileTransfer: HttpFileDownloadJabberImpl? = null

    /**
     * The file transfer history record.
     */
    override var fileRecord: FileRecord? = null

    /*
     * ChatMessageImpl with enclosed IMessage as content
     */
    constructor(sender: String, senderName: String?, date: Date, messageType: Int, msg: IMessage, correctedMessageUID: String?, direction: String?) : this(sender, senderName, date, messageType, msg.getMimeType(), msg.getContent(),
            msg.getEncryptionType(), msg.getMessageUID(), correctedMessageUID, direction,
            msg.getXferStatus(), msg.getReceiptStatus(), msg.getServerMsgId(), msg.getRemoteMsgId(), null, null, null)

    /*
     * Default direction ot DIR_OUT, not actually being use in message except in file transfer
     */
    constructor(sender: String, senderName: String?, date: Date, messageType: Int, mimeType: Int, content: String?, messageUID: String?, direction: String?) : this(sender, senderName, date, messageType, mimeType, content, IMessage.ENCRYPTION_NONE, messageUID, null, direction,
            FileRecord.STATUS_UNKNOWN, ChatMessage.MESSAGE_DELIVERY_NONE, "", "", null, null, null)

    /**
     * Creates a `ChatMessageImpl` by specifying all parameters of the message.
     *
     * @param sender The string Id of the message sender.
     * @param senderName the sender display name
     * @param date the DateTimeStamp
     * @param messageType the type (INCOMING, OUTGOING, SYSTEM etc)
     * @param mimeType the content type of the message
     * @param content the message content
     * @param encryptionType the message original encryption type
     * @param messageUID The ID of the message.
     * @param correctedMessageUID The ID of the message being replaced.
     * @param xferStatus The file transfer status.
     * @param receiptStatus The message delivery receipt status.
     * @param serverMsgId The sent message stanza Id.
     * @param remoteMsgId The received message stanza Id.
     * @param opSet The OperationSetFileTransfer.
     * @param request IncomingFileTransferRequest or HttpFileDownloadJabberImpl
     * @param fileRecord The history file record.
     */
    constructor(sender: String, date: Date, messageType: Int, mimeType: Int, content: String?, messageUID: String?,
            direction: String?, opSet: OperationSetFileTransfer?, request: Any?, fileRecord: FileRecord?) : this(sender, sender, date, messageType, mimeType, content, IMessage.ENCRYPTION_NONE, messageUID, null, direction,
            FileRecord.STATUS_UNKNOWN, ChatMessage.MESSAGE_DELIVERY_NONE, null, null, opSet, request, fileRecord)

    init {
        this.fileRecord = fileRecord
        this.opSetFT = opSet
        if (request is IncomingFileTransferRequest) {
            this.request = request
            httpFileTransfer = null
        }
        else {
            this.request = null
            httpFileTransfer = request as HttpFileDownloadJabberImpl?
        }
    }

    fun updateCachedOutput(msg: String?) {
        cachedOutput = msg
    }

    /**
     * Returns the content of the message or cached output if it is an correction message
     *
     * @return the content of the message.
     */
    override val message: String?
        get() {
            if (cachedOutput != null) return cachedOutput
            if (contentForClipboard == null) return null
            var output = contentForClipboard
            // Escape HTML content -  seems not necessary for android OS (getMimeType() can be null)
            if (IMessage.ENCODE_HTML != mimeType) {
                output = StringEscapeUtils.escapeHtml4(contentForClipboard)
            }
            // Process replacements (cmeng - just do a direct unicode conversion for std emojis)
            output = StringEscapeUtils.unescapeXml(output)

            // Apply the "edited at" tag for corrected message
            if (correctedMessageUID != null) {
                val editStr = aTalkApp.getResString(R.string.service_gui_EDITED)
                output = String.format("<i>%s <small><font color='#989898'>(%s)</font></small></i>", output, editStr)
            }
            cachedOutput = output
            return cachedOutput
        }

    /**
     * {@inheritDoc}
     */
    override fun mergeMessage(consecutiveMessage: ChatMessage): ChatMessage {
        return if (uidForCorrection != null && uidForCorrection == consecutiveMessage.correctedMessageUID) {
            consecutiveMessage
        }
        else MergedMessage(this).mergeMessage(consecutiveMessage)
    }

    override val contentForCorrection: String?
        get() = message

    /**
     * Update file transfer status for the message with the specified msgUuid.
     *
     * msgUuid Message uuid
     * status Status of the fileTransfer
     */
    fun updateFTStatus(msgUuid: String, status: Int) {
        if (uidForCorrection == msgUuid) {
            xferStatus = status
        }
    }

    /**
     * Update file transfer status and FileRecord for the message with the specified msgUuid.
     *
     * descriptor The recipient
     * msgUuid Message uuid also use as FileRecord id
     * status Status of the fileTransfer
     * fileName FileName
     * encType Message encode Type
     * recordType ChatMessage#Type
     * direction File received or send
     *
     * @return True if found the a matching msgUuid for update
     */
    fun updateFTStatus(descriptor: Any, msgUuid: String, status: Int, fileName: String, encType: Int, recordType: Int, dir: String): Boolean {
        if (uidForCorrection == msgUuid) {
            xferStatus = status
            messageType = recordType

            // Require to create new if (fileName != null) to update both filePath and mXferStatus
            if (!TextUtils.isEmpty(fileName)) {
                val entityJid = when (descriptor) {
                    is ChatRoomWrapper -> descriptor.chatRoom
                    is MetaContact -> {
                        descriptor.getDefaultContact()
                    }
                    else -> {}
                }
                fileRecord = FileRecord(msgUuid, entityJid!!, dir, date, File(fileName), encType, xferStatus)
            }
            // Timber.d("Updated ChatMessage Uid: %s (%s); status: %s => FR: %s", msgUuid, dir, status, fileRecord);
            return true
        }
        return false
    }

    /**
     * Returns the IncomingFileTransferRequest of this message.
     *
     * @return the IncomingFileTransferRequest of this message.
     */
    override val ftRequest: IncomingFileTransferRequest?
        get() = request

    override val messageUID: String? = null

    /**
     * Indicate if this.message should be considered as consecutive message;
     * Must check against the current message and the next message i.e isNonMerge(nextMsg).
     *
     * nextMsg the next message to check
     *
     * @return `true` if the given message is a consecutive message, `false` - otherwise
     */
    override fun isConsecutiveMessage(nextMsg: ChatMessage): Boolean {
        if (nextMsg == null) return false
        val isNonEmpty = !TextUtils.isEmpty(contentForClipboard)

        // Same UID specified i.e. corrected message
        val isCorrectionMessage = uidForCorrection != null && uidForCorrection == nextMsg.correctedMessageUID

        // FTRequest and FTHistory messages are always treated as non-consecutiveMessage
        val isFTMsg = messageType == ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE || messageType == ChatMessage.MESSAGE_FILE_TRANSFER_SEND || messageType == ChatMessage.MESSAGE_STICKER_SEND || messageType == ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY
        val isHttpFTMsg = isNonEmpty && contentForClipboard!!.matches(HTTP_FT_MSG)
        val isMarkUpText = isNonEmpty && contentForClipboard!!.matches(ChatMessage.HTML_MARKUP)

        // New GeoLocation message always treated as non-consecutiveMessage
        val isLatLng = isNonEmpty && (contentForClipboard!!.contains("geo:") || contentForClipboard.contains("LatLng:"))

        // system message always treated as non-consecutiveMessage
        val isSystemMsg = messageType == ChatMessage.MESSAGE_SYSTEM || messageType == ChatMessage.MESSAGE_ERROR

        // Same message type and from the same contactName
        val isJidSame = sender != null && messageType == nextMsg.messageType && sender == nextMsg.sender

        // same message encryption type
        val isEncTypeSame = encryptionType == nextMsg.encryptionType

        // true if the new message is within a minute from the last one
        val inElapseTime = nextMsg.date.time - date.time < 60000
        return (isCorrectionMessage || !(isFTMsg || isHttpFTMsg || isMarkUpText || isLatLng || isSystemMsg || isNonMerge(nextMsg))) && isEncTypeSame && isJidSame && inElapseTime
    }

    /**
     * Check the next ChatMessage to ascertain if this.message should be treated as non-consecutiveMessage
     *
     * nextMessage ChatMessage to check
     *
     * @return true if non-consecutiveMessage
     */
    private fun isNonMerge(nextMessage: ChatMessage): Boolean {
        val msgType = nextMessage.messageType
        val bodyText = nextMessage.message!!
        val isNonEmpty = !TextUtils.isEmpty(bodyText)

        // FTRequest and FTHistory messages are always treated as non-consecutiveMessage
        val isFTMsg = msgType == ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE || msgType == ChatMessage.MESSAGE_FILE_TRANSFER_SEND || msgType == ChatMessage.MESSAGE_STICKER_SEND || msgType == ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY
        val isHttpFTMsg = isNonEmpty && bodyText.matches(HTTP_FT_MSG)

        // XHTML markup message always treated as non-consecutiveMessage
        val isMarkUpText = isNonEmpty && bodyText.matches(ChatMessage.HTML_MARKUP)

        // New GeoLocation message always treated as non-consecutiveMessage
        val isLatLng = isNonEmpty && (bodyText.contains("geo:") || bodyText.contains("LatLng:"))

        // system message always treated as non-consecutiveMessage
        val isSystemMsg = msgType == ChatMessage.MESSAGE_SYSTEM || msgType == ChatMessage.MESSAGE_ERROR
        return isFTMsg || isHttpFTMsg || isMarkUpText || isLatLng || isSystemMsg
    }

    companion object {
        var HTTP_FT_MSG = Regex("(?s)^aesgcm:.*|^http[s].*")
        fun getMsgForEvent(evt: MessageDeliveredEvent): ChatMessageImpl {
            val imessage = evt.getSourceMessage()
            val sender = evt.getContact().protocolProvider.accountID.accountJid
            val senderName = if (evt.getSender()!!.isEmpty()) sender else evt.getSender()
            return ChatMessageImpl(sender, senderName, evt.getTimestamp(),
                    ChatMessage.MESSAGE_OUT, imessage, evt.getCorrectedMessageUID(), ChatMessage.DIR_OUT)
        }

        fun getMsgForEvent(evt: MessageReceivedEvent): ChatMessageImpl {
            val imessage = evt.getSourceMessage()
            val contact = evt.getSourceContact()
            val sender = evt.getSender().ifEmpty { AndroidGUIActivator.contactListService.findMetaContactByContact(contact)!!.getDisplayName() }
            return ChatMessageImpl(contact.address, sender,
                    evt.getTimestamp(), evt.getEventType(), imessage, evt.getCorrectedMessageUID(), ChatMessage.DIR_IN)
        }

        fun getMsgForEvent(evt: ChatRoomMessageDeliveredEvent): ChatMessageImpl {
            val imessage = evt.getMessage()!!
            val chatRoom = evt.getSourceChatRoom().getName()
            return ChatMessageImpl(chatRoom, chatRoom, evt.getTimestamp(),
                    ChatMessage.MESSAGE_MUC_OUT, imessage, null, ChatMessage.DIR_OUT)
        }

        fun getMsgForEvent(evt: ChatRoomMessageReceivedEvent): ChatMessageImpl {
            val imessage = evt.getMessage()
            val nickName = evt.getSourceChatRoomMember().getNickName()
            val contact = evt.getSourceChatRoomMember().getContactAddress()
            return ChatMessageImpl(nickName!!, contact, evt.getTimestamp(),
                    evt.getEventType(), imessage, null, ChatMessage.DIR_IN)
        }

        fun getMsgForEvent(fileRecord: FileRecord): ChatMessageImpl {
            return ChatMessageImpl(fileRecord.getJidAddress(), fileRecord.date,
                    ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY, IMessage.ENCODE_PLAIN, null,
                    fileRecord.id, fileRecord.direction, null, null, fileRecord)
        }

        /**
         * Returns the account user display name for the given protocol provider.
         *
         * protocolProvider the protocol provider corresponding to the account to add
         *
         * @return The account user display name for the given protocol provider.
         */
        private fun getAccountDisplayName(protocolProvider: ProtocolProviderService?): String? {
            // Get displayName from OperationSetServerStoredAccountInfo need account to be login in
            if (protocolProvider == null || !protocolProvider.isRegistered) {
                return protocolProvider!!.accountID.displayName
            }
            val accountInfoOpSet = protocolProvider.getOperationSet(OperationSetServerStoredAccountInfo::class.java)
            try {
                if (accountInfoOpSet != null) {
                    val displayName = AccountInfoUtils.getDisplayName(accountInfoOpSet)
                    if (displayName != null && displayName.isNotEmpty()) return displayName
                }
            } catch (e: Exception) {
                Timber.w("Cannot obtain display name through OPSet")
            }
            return protocolProvider.accountID.displayName
        }
    }
}