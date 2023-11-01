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
 * Class merges consecutive `ChatMessage` instances.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class MergedMessage(
        /**
         * Root message instance.
         */
        private val mRootMessage: ChatMessage) : ChatMessage {
    /**
     * The list of messages consecutive to this `MergedMessage`.
     */
    private val children = ArrayList<ChatMessage?>()
    /**
     * {@inheritDoc}
     */
    /**
     * The message date (updated with each new merge message).
     */
    override lateinit var date: Date
        private set

    /**
     * Variable used to cache merged message content.
     */
    private var mergedMessage: String? = null

    /**
     * Variable used to cache merged message Ids.
     */
    private var serverMsgIds: String? = null

    /**
     * Creates a new instance of `MergedMessage` where the given message will become its
     * rootMessage on which other new messages are being merged.
     *
     * rootMsg the rootMessage message for this merged instance.
     */
    init {
        date = mRootMessage.date
    }

    /**
     * {@inheritDoc}
     */
    override val sender: String?
        get() = mRootMessage.sender

    /**
     * {@inheritDoc}
     */
    override val senderName: String?
        get() = mRootMessage.senderName

    /**
     * {@inheritDoc}
     */
    override val messageType: Int
        get() = mRootMessage.messageType

    /**
     * {@inheritDoc}
     */
    override val mimeType: Int
        get() = mRootMessage.mimeType

    /**
     * {@inheritDoc}
     */
    override val encryptionType: Int
        get() = mRootMessage.encryptionType
    override val xferStatus: Int
        get() = FileRecord.STATUS_UNKNOWN

    /**
     * Returns the merged message lowest delivery receipt status
     *
     * @return the receipt status
     */
    override val receiptStatus: Int
        get() {
            var receiptStatus = mRootMessage.receiptStatus
            for (ch in children) {
                if (ch!!.receiptStatus < receiptStatus) receiptStatus = ch.receiptStatus
            }
            return receiptStatus
        }/*
         * Variable used to cache merged message Ids.
         */

    // Merge the server message Ids
    /**
     * Returns the server message Id of the message sent - for tracking delivery receipt
     *
     * @return the server message Id of the message sent.
     */
    override val serverMsgId: String?
        get() {
            /*
              * Variable used to cache merged message Ids.
              */
            serverMsgIds = mRootMessage.serverMsgId

            // Merge the server message Ids
            for (ch in children) {
                serverMsgIds = mergeText(serverMsgIds, ch!!.serverMsgId)
            }
            return serverMsgIds
        }// Merge the remote server message Ids

    /**
     * Returns the remote message Id of the message received - for tracking delivery receipt
     *
     * @return the remote message Id of the message received.
     */
    override val remoteMsgId: String?
        get() {
            var remoteMsgId = mRootMessage.remoteMsgId

            // Merge the remote server message Ids
            for (ch in children) {
                remoteMsgId = mergeText(remoteMsgId, ch!!.remoteMsgId)
            }
            return remoteMsgId
        }

    /**
     * Returns the UID of this message.
     *
     * @return the UID of this message.
     */
    override val messageUID: String?
        get() = mRootMessage.messageUID

    /**
     * Returns the message direction i.e. in/put.
     *
     * @return the direction of this message.
     */
    override val messageDir: String?
        get() = mRootMessage.messageDir

    /**
     * Returns a list of all UIDs of the rootMessage and its children.
     *
     * @return list of all UIDs of the rootMessage and its children.
     */
    val messageUIDs: Collection<String>
        get() {
            val msgUuidList = ArrayList<String>()
            msgUuidList.add(mRootMessage.messageUID!!)
            for (child in children) {
                msgUuidList.add(child!!.messageUID!!)
            }
            return msgUuidList
        }

    /**
     * Returns the UID of the message that this message replaces, or `null` if this is a new message.
     *
     * @return the UID of the message that this message replaces, or `null` if this is a new message.
     */
    override val correctedMessageUID: String?
        get() = mRootMessage.correctedMessageUID

    /**
     * {@inheritDoc}
     */
    override fun mergeMessage(consecutiveMessage: ChatMessage): ChatMessage {
        val corrected = findCorrectedMessage(consecutiveMessage)
        if (corrected == null) {
            children.add(consecutiveMessage)
            // Use the most recent date, as main date
            date = consecutiveMessage.date
            // Append the text only if we have cached content, otherwise it will be lazily generated on content request
            if (mergedMessage != null) {
                mergedMessage = mergeText(mergedMessage, getMessageText(consecutiveMessage))
            }
        } else {
            // Merge chat message
            val correctionResult = corrected.mergeMessage(consecutiveMessage)
            val correctedIdx = children.indexOf(corrected)
            children[correctedIdx] = correctionResult

            // Clear content cache
            mergedMessage = null
        }
        return this
    }// Merge the child text to root Message

    /**
     * {@inheritDoc}
     * msgText = "&#x2611 &#x2713 &#x2612 &#x2716 &#x2717 &#x2718 &#x2715 " + msgText;
     */
    override val message: String?
        get() {
            if (mergedMessage == null) {
                mergedMessage = getMessageText(mRootMessage)

                // Merge the child text to root Message
                for (chatMessage in children) {
                    mergedMessage = mergeText(mergedMessage, getMessageText(chatMessage!!))
                }
            }
            return mergedMessage
        }

    fun updateDeliveryStatus(msgId: String, status: Int): MergedMessage {
        // FFR: getServerMsgId() may be null (20230329)
        if (msgId == mRootMessage.serverMsgId) {
            (mRootMessage as ChatMessageImpl).receiptStatus = status
        } else {
            for (i in children.indices) {
                val child = children[i]
                if (msgId == child!!.serverMsgId) {
                    (child as ChatMessageImpl).receiptStatus = status
                    break
                }
            }
        }
        // rebuild the mergeMessage
        mergedMessage = null
        message
        return this
    }

    /**
     * Returns the last child message if it has valid UID and content or the rootMessage message.
     *
     * @return the last child message if it has valid UID and content or the rootMessage message.
     */
    private val messageForCorrection: ChatMessage
        get() {
            if (children.size > 0) {
                val candidate = children[children.size - 1]
                if (candidate!!.uidForCorrection != null && candidate.contentForCorrection != null) return candidate
            }
            return mRootMessage
        }

    /**
     * {@inheritDoc}
     */
    override val uidForCorrection: String?
        get() = messageForCorrection.uidForCorrection

    /**
     * {@inheritDoc}
     */
    override val contentForCorrection: String?
        get() = messageForCorrection.contentForCorrection

    /**
     * {@inheritDoc}
     */
    override val contentForClipboard: String
        get() {
            val output = StringBuilder(mRootMessage.contentForClipboard!!)
            for (c in children) {
                output.append("\n").append(c!!.contentForClipboard)
            }
            return output.toString()
        }

    /**
     * Finds the message that should be corrected by given message instance.
     *
     * @param newMsg new message to check if it is a correction for any of merged messages.
     * @return message that is corrected by given `newMsg` or `null` if there isn't
     * any.
     */
    private fun findCorrectedMessage(newMsg: ChatMessage): ChatMessage? {
        for (msg in children) {
            val msgUID = msg!!.messageUID ?: continue
            if (msgUID == newMsg.correctedMessageUID) {
                return msg
            }
        }
        return null
    }

    /**
     * {@inheritDoc}
     */
    override fun isConsecutiveMessage(nextMsg: ChatMessage): Boolean {
        return findCorrectedMessage(nextMsg) != null || mRootMessage.isConsecutiveMessage(nextMsg)
    }

    override val fileRecord: FileRecord?
        get() = mRootMessage.fileRecord
    override val opSetFT: OperationSetFileTransfer?
        get() = mRootMessage.opSetFT
    override val ftRequest: IncomingFileTransferRequest?
        get() = mRootMessage.ftRequest

    /**
     * Returns the HttpFileDownloadJabberImpl of this message.
     *
     * @return the HttpFileDownloadJabberImpl of this message.
     */
    override val httpFileTransfer: HttpFileDownloadJabberImpl?
        get() = mRootMessage.httpFileTransfer

    companion object {
        /**
         * Utility method used for merging message contents.
         *
         * @param chatMessage Chat message
         * @return merged message text
         */
        private fun getMessageText(chatMessage: ChatMessage): String? {
            var msgText = chatMessage.message
            if (chatMessage.messageType == ChatMessage.MESSAGE_OUT) {
                msgText = getReceiptStatus(chatMessage) + msgText
            }
            return msgText
        }

        /**
         * Get the status display for the given message.
         *
         * @param chatMessage Chat message
         */
        private fun getReceiptStatus(chatMessage: ChatMessage): String {
            when (chatMessage.receiptStatus) {
                ChatMessage.MESSAGE_DELIVERY_NONE -> return "&#x2612 " // cross makr with square boundary
                ChatMessage.MESSAGE_DELIVERY_RECEIPT -> return "" // "&#x2713 "; do not want to show anything for receipt message
                ChatMessage.MESSAGE_DELIVERY_CLIENT_SENT -> return "&#x2717 " // cross mark for message delivery to server
                ChatMessage.MESSAGE_DELIVERY_SERVER_SENT -> return "&#x2618 " // bold cross mark
            }
            return ""
        }

        /**
         * Utility method used for merging message contents.
         *
         * @param msg current message text
         * @param nextMsg next message text to merge
         * @return merged message text
         */
        private fun mergeText(msg: String?, nextMsg: String?): String {
            return "$msg <br/>$nextMsg"
        }
    }
}