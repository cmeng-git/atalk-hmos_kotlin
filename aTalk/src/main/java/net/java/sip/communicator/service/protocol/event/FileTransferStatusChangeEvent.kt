/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.FileTransfer
import java.util.*

/**
 * The `FileTransferStatusChangeEvent` is the event indicating of a change in the state of a file transfer.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class FileTransferStatusChangeEvent
/**
 * Creates a `FileTransferStatusChangeEvent` by specifying the source
 * `fileTransfer`, the old transfer status and the new status.
 *
 * @param fileTransfer the source file transfer, for which this status change occured
 * @param oldStatus the old status
 * @param newStatus the new status
 * @param reason the reason of this status change
 */(fileTransfer: FileTransfer?,
        /**
         * The state of the file transfer before this event occurred.
         */
        private val oldStatus: Int,
        /**
         * The new state of the file transfer.
         */
        private val newStatus: Int,
        /**
         * The reason of this status change.
         */
        private val reason: String) : EventObject(fileTransfer) {
    /**
     * Returns the source `FileTransfer` that triggered this event.
     *
     * @return the source `FileTransfer` that triggered this event
     */
    fun getFileTransfer(): FileTransfer {
        return source as FileTransfer
    }

    /**
     * Returns the state of the file transfer before this event occured.
     *
     * @return the old state
     */
    fun getOldStatus(): Int {
        return oldStatus
    }

    /**
     * The new state of the file transfer.
     *
     * @return the new state
     */
    fun getNewStatus(): Int {
        return newStatus
    }

    /**
     * Returns the reason of the status change.
     *
     * @return the reason of the status change
     */
    fun getReason(): String {
        return reason
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Indicates that the file transfer has been completed, or has finishing sending to stream.
         */
        const val COMPLETED = 10

        /**
         * Indicates that the file transfer has failed.
         */
        const val FAILED = 11

        /**
         * Indicates that the file transfer has been canceled.
         */
        const val CANCELED = 12

        /**
         * Indicates that the file transfer has been declined.
         */
        const val DECLINED = 13

        /**
         * Indicates that the file transfer waits for the user/recipient decision e.g. accept/decline.
         */
        const val WAITING = 14

        /**
         * Indicates that the file transfer is at the start of protocol initial state.
         */
        const val PREPARING = 15

        /**
         * Indicates that the file transfer is in progress.
         */
        const val IN_PROGRESS = 16
        const val UNKNOWN = -1
    }
}