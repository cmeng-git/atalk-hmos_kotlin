/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * Used to access the content of instant messages that are sent or received via the instant
 * messaging operation set.
 *
 *
 * This class provides easy access to the content and key fields of an instant IMessage. Content
 * types are represented using MIME types. [IETF RFC 2045-2048].
 *
 *
 * Messages are created through the `OperationSetBasicInstanceMessaging` operation set.
 *
 *
 * All messages have message ids that allow the underlying implementation to notify the user of
 * their successful delivery.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface IMessage {
    /**
     * Returns the content of this message if representable in text form or null if this message
     * does not contain text data.
     *
     * @return a String containing the content of this message or null if the message does not
     * contain data representable in text form.
     */
    fun getContent(): String

    /**
     * Returns the mime type for the message content.
     *
     * @return an integer for the mime type of the message content.
     */
    fun getMimeType(): Int

    /**
     * Returns the encryption type for the message content.
     *
     * @return an integer for the encryption type of the message content.
     */
    fun getEncryptionType(): Int

    /**
     * The Flag with EncryptionType | EncodeType | isRemoteFlag
     *
     * @return the message encType
     */
    fun getEncType(): Int

    /**
     * Returns the Http File Download status
     *
     * @return the file xfer status
     */
    fun getXferStatus(): Int
    // void setXferStatus(int status);
    /**
     * Returns the message delivery receipt status
     *
     * @return the receipt status
     */
    fun getReceiptStatus(): Int
    fun setReceiptStatus(status: Int)

    /**
     * Returns the server message Id of the message sent - for tracking delivery receipt
     *
     * @return the server message Id of the message sent.
     */
    fun getServerMsgId(): String?
    fun setServerMsgId(msgId: String?)

    /**
     * Returns the remote message Id of the message received - for tracking delivery receipt
     *
     * @return the remote message Id of the message received.
     */
    fun getRemoteMsgId(): String?
    fun setRemoteMsgId(msgId: String?)

    /**
     * Returns true if the message is for remote consumption only; No local storage or Display is required.
     *
     * @return the remoteOnly flag status.
     */
    fun isRemoteOnly(): Boolean

    /**
     * Returns true if the message is carbon message.
     *
     * @return the carbon status.
     */
    fun isCarbon(): Boolean

    /**
     * Get the raw/binary content of an instant message.
     *
     * @return a byte[] array containing message bytes.
     */
    fun getRawData(): ByteArray

    /**
     * Returns the subject of this message or null if the message contains no subject.
     *
     * @return the subject of this message or null if the message contains no subject.
     */
    fun getSubject(): String?

    /**
     * Returns the size of the content stored in this message.
     *
     * @return an int indicating the number of bytes that this message contains.
     */
    fun getSize(): Int

    /**
     * Returns a unique identifier of this message.
     *
     * @return a String that uniquely represents this message in the scope of this protocol.
     */
    fun getMessageUID(): String
    fun setMessageUID(msgUid: String)

    companion object {
        /*
     * ENC_TYPE type defined in DB; use by IMessage Local to define the required actions
     * Upper nibble (b7...b4) for body encryption Type i.e. OMEMO, OTR, NONE
     * Lower nibble (b3...b2) for special mode flag - may not be included in DB e.g FLAG_REMOTE_ONLY etc
     * Lower nibble (b1...b0) for body mimeType i.e. HTML or PLAIN
     */
        const val ENCRYPTION_MASK = 0xF0
        const val FLAG_MODE_MASK = 0x0C
        const val ENCODE_MIME_MASK = 0x03

        // Only the following three defined value are store in DB encType column
        const val ENCRYPTION_OTR = 0x20
        const val ENCRYPTION_OMEMO = 0x10
        const val ENCRYPTION_NONE = 0x00

        /*
     * The flag signifies that message is for remote sending - DO NOT store in DB or display in sender chat window
     * e.g. http file upload link for remote action
     */
        const val FLAG_REMOTE_ONLY = 0x08

        /*
     * The flag signifies that message is a carbon copy.
     */
        const val FLAG_IS_CARBON = 0x04
        const val ENCODE_HTML = 0x01 // text/html
        const val ENCODE_PLAIN = 0x00 // text/plain (UTF-8)
    }
}