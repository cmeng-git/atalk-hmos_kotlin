/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import java.util.*

/**
 * A listener that would gather events notifying of incoming file transfer requests.
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface ScFileTransferListener : EventListener {
    /**
     * Called when a new `IncomingFileTransferRequest` has been received.
     *
     * @param event the `FileTransferRequestEvent` containing the newly received request and other details.
     */
    fun fileTransferRequestReceived(event: FileTransferRequestEvent)

    /**
     * Called when a `FileTransferCreatedEvent` has been received.
     *
     * @param event the `FileTransferCreatedEvent` containing the newly received file transfer and other details.
     */
    fun fileTransferCreated(event: FileTransferCreatedEvent?)

    /**
     * Called when an `IncomingFileTransferRequest` has been rejected.
     *
     * @param event the `FileTransferRequestEvent` containing the received request which was rejected.
     */
    fun fileTransferRequestRejected(event: FileTransferRequestEvent)

    /**
     * Called when an `IncomingFileTransferRequest` has been canceled from the contact who sent it.
     *
     * @param event the `FileTransferRequestEvent` containing the request which was canceled.
     */
    fun fileTransferRequestCanceled(event: FileTransferRequestEvent)
}