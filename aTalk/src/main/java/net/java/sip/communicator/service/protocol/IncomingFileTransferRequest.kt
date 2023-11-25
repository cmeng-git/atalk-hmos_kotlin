/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import java.io.File

/**
 * Used for incoming file transfer request.
 *
 * @author Nicolas Riegel
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface IncomingFileTransferRequest {
    /**
     * Recipient has prepared and ready to receive the incoming file offer;
     * listen in for any event for action e.g. remote cancel the file transfer before start
     *
     * @param file the file to accept
     * @return the `FileTransfer` object managing the transfer
     */
    fun onPrepare(file: File?): FileTransfer

    /**
     * Unique ID that is identifying the request and then the FileTransfer if the request has been accepted.
     *
     * @return the id.
     */
    fun getID(): String

    /**
     * Returns a String that represents the name of the file that is being received. If there is no
     * name, returns null.
     *
     * @return a String that represents the name of the file
     */
    fun getFileName(): String?

    /**
     * Returns a String that represents the description of the file that is being received. If there
     * is no description available, returns null.
     *
     * @return a String that represents the description of the file
     */
    fun getFileDescription(): String?

    /**
     * Identifies the type of file that is desired to be transferred.
     *
     * @return The mime-type.
     */
    fun getMimeType(): String?

    /**
     * Returns a long that represents the size of the file that is being received. If there is no
     * file size available, returns null.
     *
     * @return a long that represents the size of the file
     */
    fun getFileSize(): Long

    /**
     * Returns a String that represents the name of the sender of the file being received. If there
     * is no sender name available, returns null.
     *
     * @return a String that represents the name of the sender
     */
    fun getSender(): Contact?

    /**
     * Returns the encryption of the incoming file corresponding to this FileTransfer.
     *
     * @return the encryption of the file corresponding to this request
     */
    fun getEncryptionType(): Int

    /**
     * Function called to accept and receive the file.
     *
     * file the file to accept
     * @return the `FileTransfer` object managing the transfer
     */
    fun acceptFile()

    /**
     * Function called to decline the file offer.
     */
    @Throws(OperationFailedException::class)
    fun declineFile()

    /**
     * Returns the thumbnail contained in this request.
     *
     * @return the thumbnail contained in this request
     */
    fun getThumbnail(): ByteArray?
}