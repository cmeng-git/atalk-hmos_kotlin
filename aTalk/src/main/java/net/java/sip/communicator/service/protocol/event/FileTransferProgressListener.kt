/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

/**
 * The `FileTransferStatusListener` listens for `FileTransferStatusChangeEvent` in
 * order to indicate a change in the current progress of a file transfer.
 *
 * @author Yana Stamcheva
 */
interface FileTransferProgressListener {
    /**
     * Indicates a change in the file transfer progress.
     *
     * @param event
     * the event containing information about the change
     */
    fun progressChanged(event: FileTransferProgressEvent?)
}