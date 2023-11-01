/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.FileTransferProgressListener
import net.java.sip.communicator.service.protocol.event.FileTransferStatusListener
import java.io.File

/**
 * The `FileTransfer` interface is meant to be used by parties interested in the file
 * transfer process. It contains information about the status and the progress of the transfer as
 * well as the bytes that have been transferred.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface FileTransfer {
    /**
     * Unique ID that is identifying the FileTransfer if the request has been accepted.
     *
     * @return the id.
     */
    fun getID(): String?

    /**
     * Cancels this file transfer. When this method is called transfer should be interrupted.
     */
    fun cancel()

    /**
     * The file transfer direction.
     *
     * @return returns the direction of the file transfer : IN or OUT.
     */
    fun getDirection(): Int

    /**
     * Returns the local file that is being transferred or to which we transfer.
     *
     * @return the file
     */
    fun getLocalFile(): File?

    /**
     * Returns the contact that we are transferring files with.
     *
     * @return the contact.
     */
    fun getContact(): Contact?

    /**
     * Returns the current status of the transfer. This information could be used from the user
     * interface to show the current status of the transfer. The status is returned as an
     * `int` and could be equal to one of the static constants declared in this interface
     * (i.e. COMPLETED, CANCELED, FAILED, etc.).
     *
     * @return the current status of the transfer
     */
    fun getStatus(): Int

    /**
     * Returns the number of bytes already transferred through this file transfer.
     *
     * @return the number of bytes already transferred through this file transfer
     */
    fun getTransferredBytes(): Long

    /**
     * Adds the given `FileTransferStatusListener` to listen for status changes on this file transfer.
     *
     * @param listener the listener to add
     */
    fun addStatusListener(listener: FileTransferStatusListener)

    /**
     * Removes the given `FileTransferStatusListener`.
     *
     * @param listener the listener to remove
     */
    fun removeStatusListener(listener: FileTransferStatusListener)

    /**
     * Adds the given `FileTransferProgressListener` to listen for status changes on this file transfer.
     *
     * @param listener the listener to add
     */
    fun addProgressListener(listener: FileTransferProgressListener)

    /**
     * Removes the given `FileTransferProgressListener`.
     *
     * @param listener the listener to remove
     */
    fun removeProgressListener(listener: FileTransferProgressListener)

    companion object {
        /**
         * File transfer is incoming.
         */
        const val IN = 1

        /**
         * File transfer is outgoing.
         */
        const val OUT = 2
    }
}