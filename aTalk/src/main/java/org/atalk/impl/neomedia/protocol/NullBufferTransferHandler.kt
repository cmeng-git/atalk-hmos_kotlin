/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol

import javax.media.Buffer
import javax.media.protocol.BufferTransferHandler
import javax.media.protocol.PushBufferStream

/**
 * Implements a `BufferTransferHandler` which reads from a specified
 * `PushBufferStream` as soon as possible and throws the read data away.
 *
 * @author Lyubomir Marinov
 */
class NullBufferTransferHandler : BufferTransferHandler {
    /**
     * The FMJ `Buffer` into which this `BufferTransferHandler` is to read data from
     * any `PushBufferStream`.
     */
    private val buffer = Buffer()
    override fun transferData(stream: PushBufferStream) {
        try {
            stream.read(buffer)
        } catch (ex: Exception) {
            // The purpose of NullBufferTransferHandler is to read from the
            // specified PushBufferStream as soon as possible and throw the read
            // data away. Hence, Exceptions are of no concern.
        }
    }
}