/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.IncomingFileTransferRequest
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer
import java.util.*

/**
 * The `FileTransferRequestEvent` indicates the reception of a file transfer request.
 *
 * @author Nicolas Riegel
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class FileTransferRequestEvent
/**
 * Creates a `FileTransferRequestEvent` representing reception of an incoming file
 * transfer request.
 *
 * @param fileTransferOpSet the operation set, where this event initially occurred
 * @param request the `IncomingFileTransferRequest` whose reception this event represents.
 * @param timestamp the timestamp indicating the exact date when the event occurred
 */(fileTransferOpSet: OperationSetFileTransfer?,
        /**
         * The request that triggered this event.
         */
        private val request: IncomingFileTransferRequest,
        /**
         * The timestamp indicating the exact date when the event occurred.
         */
        private val timestamp: Date) : EventObject(fileTransferOpSet) {
    /**
     * Returns the `OperationSetFileTransfer`, where this event initially occurred.
     *
     * @return the `OperationSetFileTransfer`, where this event initially occurred
     */
    fun getFileTransferOperationSet(): OperationSetFileTransfer {
        return getSource() as OperationSetFileTransfer
    }

    /**
     * Returns the incoming file transfer request that triggered this event.
     *
     * @return the `IncomingFileTransferRequest` that triggered this event.
     */
    fun getRequest(): IncomingFileTransferRequest {
        return request
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