/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.filehistory.FileRecord
import org.atalk.hmos.gui.chat.ChatMessage

/**
 * Represents a default implementation of [IMessage] in order to make it easier for
 * implementers to provide complete solutions while focusing on implementation-specific details.
 *
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
/**
 * @param content the text content of the message.
 * @param mEncType contains both mime and encryption types @see ChatMessage.ENC_TYPE definition and other flags
 * @param subject the subject of the message or null for empty.
 * @param messageUID @see net.java.sip.communicator.service.protocol.IMessage#getMessageUID()
 */
abstract class AbstractMessage protected constructor(
        content: String, private val mEncType: Int, subject: String?, messageUID: String?,
        xferStatus: Int = FileRecord.STATUS_UNKNOWN,
        receiptStatus: Int = ChatMessage.MESSAGE_DELIVERY_NONE,
        serverMessageId: String? = null, remoteMessageId: String? = null,
) : IMessage {
    private val mEncryption = mEncType and IMessage.ENCRYPTION_MASK
    private val mMimeType = mEncType and IMessage.ENCODE_MIME_MASK
    private val mRemoteOnly = IMessage.FLAG_REMOTE_ONLY == mEncType and IMessage.FLAG_MODE_MASK
    private val isCarbon = IMessage.FLAG_IS_CARBON == mEncType and IMessage.FLAG_IS_CARBON
    private val mXferStatus: Int
    private var mReceiptStatus: Int
    private var mMessageUID: String
    private var mServerMessageId: String?
    private var mRemoteMessageId: String?

    /**
     * The content of this message, in raw bytes according to the encoding.
     */
    private val mSubject: String?
    private var mContent: String? = null
    private var rawData: ByteArray? = null

    init {
        mSubject = subject
        setContent(content)
        mMessageUID = messageUID ?: createMessageUID()
        mXferStatus = xferStatus
        mReceiptStatus = receiptStatus
        mServerMessageId = serverMessageId
        mRemoteMessageId = remoteMessageId
    }

    private fun createMessageUID(): String {
        return System.currentTimeMillis().toString() + hashCode().toString()
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.protocol.IMessage#getMimeType()
     */
    override fun getMimeType(): Int {
        return mMimeType
    }

    /**
     * Returns the content of this message if representable in text form or null if this message
     * does not contain text data.
     *
     * The implementation is final because it caches the raw data of the content.
     *
     * @return a String containing the content of this message or null if the message does not
     * contain data representable in text form.
     */
    override fun getContent(): String? {
        return mContent
    }

    /*
     * @return the Encryption Type for the message
     */
    override fun getEncryptionType(): Int {
        return mEncryption
    }

    /**
     * @return the encType info
     */
    override fun getEncType(): Int {
        return mEncType
    }

    /*
     * @return the file transfer status for HTTP File Download message
     */
    override fun getXferStatus(): Int {
        return mXferStatus
    }

    /*
     * @return the Encryption Type for the message
     */
    override fun getReceiptStatus(): Int {
        return mReceiptStatus
    }

    override fun setReceiptStatus(status: Int) {
        mReceiptStatus = status
    }

    /**
     * Returns the server message Id of the message sent - for tracking delivery receipt
     *
     * @return the server message Id of the message sent.
     */
    override fun getServerMsgId(): String? {
        return mServerMessageId
    }

    override fun setServerMsgId(msgId: String?) {
        mServerMessageId = msgId
    }

    /**
     * Returns the remote message Id of the message received - for tracking delivery receipt
     *
     * @return the remote message Id of the message received.
     */
    override fun getRemoteMsgId(): String? {
        return mRemoteMessageId
    }

    override fun setRemoteMsgId(msgId: String?) {
        mRemoteMessageId = msgId
    }

    override fun isRemoteOnly(): Boolean {
        return mRemoteOnly
    }

    override fun isCarbon(): Boolean {
        return isCarbon
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.protocol.IMessage#getMessageUID()
     */
    override fun getMessageUID(): String {
        return mMessageUID
    }

    override fun setMessageUID(msgUid: String) {
        mMessageUID = msgUid
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.protocol.IMessage#getRawData()
     */
    override fun getRawData(): ByteArray? {
        if (rawData == null) {
            val content = getContent()
            rawData = content?.toByteArray()
        }
        return rawData
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.protocol.IMessage#getSize()
     */
    override fun getSize(): Int {
        return if (rawData != null) rawData!!.size else 0
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.protocol.IMessage#getSubject()
     */
    override fun getSubject(): String? {
        return mSubject
    }

    private fun setContent(content: String) {
        if (!equals(mContent, content)) {
            mContent = content
            rawData = null
        }
    }

    companion object {
        private fun equals(a: String?, b: String?): Boolean {
            return a == b
        }
    }
}