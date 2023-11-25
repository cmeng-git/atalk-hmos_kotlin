/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.chat

import net.java.sip.communicator.impl.protocol.jabber.HttpFileDownloadJabberImpl
import net.java.sip.communicator.service.filehistory.FileRecord
import net.java.sip.communicator.service.protocol.IncomingFileTransferRequest
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer
import java.util.*

/**
 * The `ChatMessage` interface is used to display a chat message.
 *
 * @author Eng Chong Meng
 */
interface ChatMessage {
    /**
     * The display name of the message sender.
     *
     * Returns the string Id of the message sender.
     * Actual value is pending on message type i.e.:
     * a. userId: swordfish@atalk.org
     * b. contactId: leopard@atalk.org
     * c. chatRoom: conference@atalk.org
     * d. nickName: leopard
     *
     * Exception as recipient:
     * contactId: ChatMessage.MESSAGE_FILE_TRANSFER_SEND & ChatMessage.MESSAGE_STICKER_SEND:
     *
     * @return the string id of the message sender.
     */
    val sender: String?

    /**
     * Returns the display name of the message sender.
     *
     * @return the display name of the message sender
     */
    val senderName: String?

    /**
     * Returns the date and time of the message.
     *
     * @return the date and time of the message.
     */
    val date: Date

    /**
     * Returns the type of the message.
     *
     * @return the type of the message.
     */
    val messageType: Int

    /**
     * Returns the mime type of the content type (e.g. "text", "text/html", etc.).
     *
     * @return the mimeType
     */
    val mimeType: Int

    /**
     * Returns the content of the message.
     *
     * @return the content of the message.
     */
    val message: String?

    /**
     * Returns the encryption type of the content
     *
     * @return the encryption Type
     */
    val encryptionType: Int

    /**
     * Returns the HttpFileDownload file xfer status
     *
     * @return the HttpFileDownload file transfer status
     */
    val xferStatus: Int

    /**
     * Returns the message delivery receipt status
     *
     * @return the receipt status
     */
    val receiptStatus: Int

    /**
     * Returns the server message Id of the message sent - for tracking delivery receipt
     *
     * @return the server message Id of the message sent.
     */
    val serverMsgId: String?

    /**
     * Returns the remote message Id of the message received - for tracking delivery receipt
     *
     * @return the remote message Id of the message received.
     */
    val remoteMsgId: String?

    /**
     * Returns the UID of this message.
     *
     * @return the UID of this message.
     */
    fun getMessageUID(): String

    /**
     * Returns the message direction i.e. in/put.
     *
     * @return the direction of this message.
     */
    val messageDir: String

    /**
     * Returns the UID of the message that this message replaces, or `null` if this is a new message.
     *
     * @return the UID of the message that this message replaces, or `null` if this is a new message.
     */
    val correctedMessageUID: String?

    /**
     * Indicates if given `nextMsg` is a consecutive message or if the `nextMsg`
     * is a replacement for this message.
     *
     * @param nextMsg the next message to check
     * @return `true` if the given message is a consecutive or replacement message, `false` - otherwise
     */
    fun isConsecutiveMessage(nextMsg: ChatMessage): Boolean

    /**
     * Merges given message. If given message is consecutive to this one, then their contents will be merged.
     * If given message is a replacement message for `this` one, then the replacement will be returned.
     *
     * @param consecutiveMessage the next message to merge with `this` instance
     * (it must be consecutive in terms of `isConsecutiveMessage` method).
     * @return merge operation result that should be used instead of this `ChatMessage` instance.
     */
    fun mergeMessage(consecutiveMessage: ChatMessage): ChatMessage

    /**
     * Returns the UID that should be used for matching correction messages.
     *
     * @return the UID that should be used for matching correction messages.
     */
    fun getUidForCorrection(): String?

    /**
     * Returns original message content that should be given for the user to edit the correction.
     *
     * @return original message content that should be given for the user to edit the correction.
     */
    fun getContentForCorrection(): String?

    /**
     * Returns message content that should be used for copy and paste functionality.
     *
     * @return message content that should be used for copy and paste functionality.
     */
    fun getContentForClipboard(): String?

    /**
     * Returns the OperationSetFileTransfer of this message.
     *
     * @return the OperationSetFileTransfer of this message.
     */
    val opSetFT: OperationSetFileTransfer?

    /**
     * Returns IncomingFileTransferRequest]of this message.
     *
     * @return the IncomingFileTransferRequest of this message.
     */
    val ftRequest: IncomingFileTransferRequest?

    /**
     * Returns HttpFileTransferImpl of this message.
     *
     * @return the IncomingFileTransferRequest of this message.
     */
    val httpFileTransfer: HttpFileDownloadJabberImpl?

    /**
     * Returns history file transfer fileRecord
     *
     * @return file history file transferRecord.
     */
    val fileRecord: FileRecord?

    companion object {
        /* DB database column  fields */
        const val TABLE_NAME = "messages"
        const val UUID = "uuid" // msg Unique identification in database (deletion Id)
        const val SESSION_UUID = "chatSessionUuid" // chatSession Uuid
        const val TIME_STAMP = "timeStamp" // message sent or received timestamp
        const val ENTITY_JID = "entityJid" // to BareJid: nick (muc) or contact (others)
        const val JID = "Jid" // from/sender FullJid if available: chatRoom member etc
        const val MSG_BODY = "msgBody" // message content
        const val ENC_TYPE = "encType" // see IMessage for the ENCRYPTION_xxx & MASK definitions
        const val MSG_TYPE = "msgType" // as defined in below * message type *
        const val DIRECTION = "direction" // in or out
        const val STATUS = "status" // Use by FileTransferStatusChangeEvent and FileRecord STATUS_xxx
        const val FILE_PATH = "filePath" // filepath
        const val FINGERPRINT = "OmemoFingerprint" // rx fingerPrint
        const val STEALTH_TIMER = "stealthTimer" // stealth timer
        const val CARBON = "carbon"
        const val READ = "read" // read status
        const val OOB = "oob" // 0
        const val ERROR_MSG = "errorMsg"
        const val SERVER_MSG_ID = "serverMsgId" // chat msg Id - message out
        const val REMOTE_MSG_ID = "remoteMsgId" // chat msg Id - message in
        const val ME_COMMAND = "/me "

        // <a href="...">...</a>; <font ...>
        val HTML_MARKUP = Regex("(?s).*?<[A-Za-z]+.*?>.*?</[A-Za-z]+>.*?")

        /**
         * @see ChatMessage defined constant below
         */
        /* chat message or File transfer status - see FileRecord.STATUS_XXX */
        const val STATUS_SEND = 0
        const val STATUS_RECEIVED = 1
        const val STATUS_DELETE = 99 // to be deleted

        /* READ - message delivery status: Do not change the order, values used in MergedMessage */
        const val MESSAGE_DELIVERY_NONE = 0
        const val MESSAGE_DELIVERY_CLIENT_SENT = 1
        const val MESSAGE_DELIVERY_SERVER_SENT = 2
        const val MESSAGE_DELIVERY_RECEIPT = 4

        // Not used
        // int ENCRYPTION_DECRYPTED = 0x50;
        // int ENCRYPTION_DECRYPTION_FAILED = 0x60;
        /* Chat message stream direction */
        const val DIR_OUT = "out"
        const val DIR_IN = "in"

        /**
         * The message type representing outgoing messages.
         */
        const val MESSAGE_OUT = 0

        /**
         * The message type representing incoming messages.
         *
         * An event type indicating that the message being received is a standard conversation message
         * sent by another contact.
         */
        const val MESSAGE_IN = 1

        /**
         * The message type representing status messages.
         */
        const val MESSAGE_STATUS = 3

        /**
         * The message type representing system messages received.
         *
         * An event type indicting that the message being received is a system message being sent by the
         * server or a system administrator, possibly notifying us of something important such as
         * ongoing maintenance activities or server downtime.
         */
        const val MESSAGE_SYSTEM = 5

        /**
         * The message type representing action messages. These are message specific for IRC,
         * but could be used in other protocols also.
         *
         * An event type indicating that the message being received is a special message that sent by
         * either another member or the server itself, indicating that some kind of action (other than
         * the delivery of a conversation message) has occurred. Action messages are widely used in IRC
         * through the /action and /me commands
         */
        const val MESSAGE_ACTION = 6

        /**
         * The message type representing error messages.
         */
        const val MESSAGE_ERROR = 9

        /**
         * The history outgoing message type.
         */
        const val MESSAGE_HISTORY_OUT = 10

        /**
         * The history incoming message type.
         */
        const val MESSAGE_HISTORY_IN = 11

        /**
         * The Location message type.
         */
        const val MESSAGE_LOCATION_OUT = 20
        const val MESSAGE_LOCATION_IN = 21

        /**
         * The Stealth message type.
         */
        const val MESSAGE_STEALTH_OUT = 30
        const val MESSAGE_STEALTH_IN = 31

        /**
         * The message type representing sms messages.
         */
        const val MESSAGE_SMS_OUT = 40

        /**
         * an event type indicating that the message being received is an SMS message.
         */
        const val MESSAGE_SMS_IN = 41

        /**
         * The file transfer message type.
         */
        const val MESSAGE_FILE_TRANSFER_SEND = 50

        /**
         * The file transfer message type.
         */
        const val MESSAGE_FILE_TRANSFER_RECEIVE = 51

        /**
         * The Http file upload message type.
         */
        const val MESSAGE_HTTP_FILE_UPLOAD = 52

        /**
         * The Http file download message type.
         */
        const val MESSAGE_HTTP_FILE_DOWNLOAD = 53

        /**
         * The sticker message type.
         */
        const val MESSAGE_STICKER_SEND = 54

        /**
         * The file transfer history message type.
         */
        const val MESSAGE_FILE_TRANSFER_HISTORY = 55
        /* ***********************
     * All muc messages are numbered >=80 cmeng?
     * The MUC message type.
     */
        /**
         * An event type indicating that the message being received is a standard conversation message
         * sent by another member of the chat room to all current participants.
         */
        const val MESSAGE_MUC_OUT = 80

        /**
         * An event type indicating that the message being received is a standard conversation message
         * sent by another member of the chatRoom to all current participants.
         */
        const val MESSAGE_MUC_IN = 81
    }
}