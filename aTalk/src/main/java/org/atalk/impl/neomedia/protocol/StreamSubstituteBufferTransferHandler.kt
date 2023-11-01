/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol

import javax.media.protocol.BufferTransferHandler
import javax.media.protocol.PushBufferStream

/**
 * Implements a `BufferTransferHandler` wrapper which doesn't expose a
 * `PushBufferStream` but rather a specific substitute in order to give full control to the
 * [PushBufferStream.read] method of the substitute.
 *
 *
 * The purpose is achieved in `#transferData(PushBufferStream)` where the method argument
 * `stream` is ignored and the substitute is used instead.
 *
 *
 * @author Lubomir Marinov
 */
open class StreamSubstituteBufferTransferHandler
/**
 * Initializes a new `StreamSubstituteBufferTransferHandler` instance which is to
 * overwrite the source `PushBufferStream` of a specific `BufferTransferHandler`.
 *
 * @param transferHandler
 * the `BufferTransferHandler` the new instance is to overwrite the source
 * `PushBufferStream` of
 * @param stream
 * the `PushBufferStream` to be overridden for the specified
 * `transferHandler` with the specified (@code substitute}
 * @param substitute
 * the `PushBufferStream` to override the specified `stream` for
 * the specified `transferHandler`
 */
(
        /**
         * The wrapped `BufferTransferHandler` which receives the actual events from the wrapped
         * `PushBufferStream`.
         */
        private val transferHandler: BufferTransferHandler,
        /**
         * The `PushBufferStream` to be overridden for `transferHandler` with the
         * `substitute` of this instance.
         */
        private val stream: PushBufferStream,
        /**
         * The `PushBufferStream` to override the `stream` of this instance for
         * `transferHandler`.
         */
        private val substitute: PushBufferStream) : BufferTransferHandler {
    /**
     * Implements BufferTransferHandler#transferData(PushBufferStream). Puts in place the
     * essence of the StreamSubstituteBufferTransferHandler class which is to report to the
     * transferHandler from the same PushBufferStream to which it was set so that the substitute
     * can gain full control.
     *
     * @param stream
     * the `PushBufferStream` to transfer
     */
    override fun transferData(stream: PushBufferStream) {
        transferHandler.transferData(if (stream === this.stream) substitute else stream)
    }
}