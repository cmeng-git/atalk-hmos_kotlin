/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.FileTransfer
import java.util.*

/**
 * The `FileTransferCreatedEvent` indicates the creation of a file transfer.
 *
 * @author Yana Stamcheva
 */
class FileTransferCreatedEvent
/**
 * Creates a `FileTransferCreatedEvent` representing creation of a file transfer.
 *
 * @param fileTransfer the `FileTransfer` whose creation this event represents.
 * @param timestamp the timestamp indicating the exact date when the event occurred
 */(fileTransfer: FileTransfer?,
        /**
         * The timestamp indicating the exact date when the event occurred.
         */
        private val timestamp: Date) : EventObject(fileTransfer) {
    /**
     * Returns the file transfer that triggered this event.
     *
     * @return the `FileTransfer` that triggered this event.
     */
    fun getFileTransfer(): FileTransfer {
        return getSource() as FileTransfer
    }

    /**
     * A timestamp indicating the exact date when the event occurred.
     *
     * @return a Date indicating when the event occurred.
     */
    fun getTimestamp(): Date {
        return timestamp
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}