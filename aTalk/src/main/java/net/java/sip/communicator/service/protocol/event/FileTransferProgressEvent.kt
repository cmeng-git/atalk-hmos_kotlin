/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.FileTransfer
import java.util.*

/**
 * The `FileTransferProgressEvent` indicates the progress of a file transfer.
 *
 * @author Yana Stamcheva
 */
class FileTransferProgressEvent
/**
 * Creates a `FileTransferProgressEvent` by specifying the source file transfer object,
 * that triggered the event and the new progress value.
 *
 * @param fileTransfer the source file transfer object, that triggered the event
 * @param timestamp when this event occurred
 * @param progress the new progress value
 */(fileTransfer: FileTransfer?,
        /**
         * Indicates when this event occured.
         */
        private val timestamp: Long,
        /**
         * Indicates the progress of a file transfer in bytes.
         */
        private val progress: Long) : EventObject(fileTransfer) {
    /**
     * Returns the source `FileTransfer` that triggered this event.
     *
     * @return the source `FileTransfer` that triggered this event
     */
    fun getFileTransfer(): FileTransfer {
        return source as FileTransfer
    }

    /**
     * Returns the progress of the file transfer in transferred bytes.
     *
     * @return the progress of the file transfer
     */
    fun getProgress(): Long {
        return progress
    }

    /**
     * Returns the timestamp when this event initially occured.
     *
     * @return the timestamp when this event initially occured
     */
    fun getTimestamp(): Long {
        return timestamp
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}