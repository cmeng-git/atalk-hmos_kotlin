/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.ScFileTransferListener
import java.io.File

/**
 * The File Transfer Operation Set provides an interface towards those functions of a given
 * protocol, that allow transferring files among users.
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface OperationSetFileTransfer : OperationSet {
    /**
     * Sends a file transfer request to the given `toContact` by specifying the local and
     * remote file path and the `fromContact`, sending the file.
     *
     * @param toContact the contact that should receive the file
     * @param file the file to send
     * @param msgUuid the uuid of the message that trigger the send file request
     *
     * @return the transfer object
     * @throws IllegalStateException if the protocol provider is not registered or connected
     * @throws IllegalArgumentException if some of the arguments doesn't fit the protocol requirements
     * @throws OperationNotSupportedException if the given contact client or server does not support file transfers
     */
    @Throws(IllegalStateException::class, IllegalArgumentException::class, OperationNotSupportedException::class)
    fun sendFile(toContact: Contact, file: File, msgUuid: String): FileTransfer

    /**
     * Adds the given `ScFileTransferListener` that would listen for file transfer requests and
     * created file transfers.
     *
     * @param listener the `ScFileTransferListener` to add
     */
    fun addFileTransferListener(listener: ScFileTransferListener)

    /**
     * Removes the given `ScFileTransferListener` that listens for file transfer requests and
     * created file transfers.
     *
     * @param listener the `ScFileTransferListener` to remove
     */
    fun removeFileTransferListener(listener: ScFileTransferListener)

    /**
     * Returns the maximum file length supported by the protocol in bytes.
     *
     * @return the file length that is supported.
     */
    fun getMaximumFileLength(): Long
}