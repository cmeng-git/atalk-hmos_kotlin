/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol

import java.io.IOException
import javax.media.*
import javax.media.protocol.BufferTransferHandler
import javax.media.protocol.PushBufferStream
import javax.media.protocol.PushSourceStream

/**
 * Represents a `PushBufferStream` which reads its data from a specific `PushSourceStream`.
 *
 * @author Lyubomir Marinov
 */
open class PushBufferStreamAdapter
/**
 * Initializes a new `PushBufferStreamAdapter` instance which reads its data from a
 * specific `PushSourceStream` with a specific `Format`
 *
 * @param stream the `PushSourceStream` the new instance is to read its data from
 * @param format the `Format` of the specified input `stream` and of the new instance
 */
(stream: PushSourceStream, format: Format?) : BufferStreamAdapter<PushSourceStream?>(stream, format), PushBufferStream {

    override fun getFormat(): Format? {
        return super.mFormat
    }

    /**
     * Implements PushBufferStream#read(Buffer). Delegates to the wrapped PushSourceStream by
     * allocating a new byte[] buffer of size equal to PushSourceStream#getMinimumTransferSize().
     *
     * @param buffer the `Buffer` to read
     * @throws IOException if I/O related errors occurred during read operation
     */
    @Throws(IOException::class)
    override fun read(buffer: Buffer) {
        var data = buffer.data as ByteArray?
        val minimumTransferSize = stream!!.minimumTransferSize
        if (data == null || data.size < minimumTransferSize) {
            data = ByteArray(minimumTransferSize)
            buffer.data = data
        }
        buffer.offset = 0
        read(buffer, data, 0, minimumTransferSize)
    }

    /**
     * Implements `BufferStreamAdapter#doRead(Buffer, byte[], int, int)`. Delegates to the wrapped `PushSourceStream`.
     *
     * @param buffer
     * @param data byte array to read
     * @param offset offset to start reading
     * @param length length to read
     * @return number of bytes read
     * @throws IOException if I/O related errors occurred during read operation
     */
    @Throws(IOException::class)
    override fun doRead(buffer: Buffer?, data: ByteArray?, offset: Int, length: Int): Int {
        return stream!!.read(data, offset, length)
    }

    /**
     * Implements PushBufferStream#setTransferHandler(BufferTransferHandler). Delegates to the
     * wrapped PushSourceStream by translating the specified BufferTransferHandler to a SourceTransferHandler.
     *
     * @param transferHandler a `BufferTransferHandler` to set
     */
    override fun setTransferHandler(transferHandler: BufferTransferHandler) {
        stream!!.setTransferHandler { transferHandler.transferData(this@PushBufferStreamAdapter) }
    }
}