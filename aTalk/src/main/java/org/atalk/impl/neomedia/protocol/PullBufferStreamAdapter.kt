/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol

import java.io.IOException
import javax.media.*
import javax.media.format.AudioFormat
import javax.media.protocol.PullBufferStream
import javax.media.protocol.PullSourceStream

/**
 * Represents a `PullBufferStream` which reads its data from a specific
 * `PullSourceStream`.
 *
 * @author Lyubomir Marinov
 */
class PullBufferStreamAdapter
/**
 * Initializes a new `PullBufferStreamAdapter` instance which reads its data from a
 * specific `PullSourceStream` with a specific `Format`
 *
 * @param stream
 * the `PullSourceStream` the new instance is to read its data from
 * @param format
 * the `Format` of the specified input `stream` and of the new instance
 */
(stream: PullSourceStream, format: Format) : BufferStreamAdapter<PullSourceStream?>(stream, format), PullBufferStream {

    override fun getFormat(): Format? {
        return super.mFormat
    }

    /**
     * Implements PullBufferStream#read(Buffer). Delegates to the wrapped PullSourceStream by
     * either allocating a new byte[] buffer or using the existing one in the specified Buffer.
     *
     * @param buffer
     * `Buffer` to read
     * @throws IOException
     * if I/O errors occurred during read operation
     */
    @Throws(IOException::class)
    override fun read(buffer: Buffer) {
        val data = buffer.data
        var bytes: ByteArray? = null

        if (data != null) {
            when (data) {
                is ByteArray -> {
                    bytes = data
                }
                is ShortArray -> {
                    bytes = ByteArray(2 * data.size)
                }
                is IntArray -> {
                    bytes = ByteArray(4 * data.size)
                }
            }
        }

        if (bytes == null) {
            val frameSizeInBytes = getFrameSizeInBytes(mFormat!!)
            bytes = ByteArray(1024 * if (frameSizeInBytes <= 0) 4 else frameSizeInBytes)
        }
        read(buffer, bytes, 0, bytes.size)
    }

    /**
     * Implements `BufferStreamAdapter#doRead(Buffer, byte[], int, int)`. Delegates to the
     * wrapped `PullSourceStream`.
     *
     * @param buffer
     * @param data
     * byte array to read
     * @param offset
     * to start reading
     * @param length
     * length to read
     * @return number of bytes read
     * @throws IOException
     * if I/O related errors occurred during read operation
     */
    @Throws(IOException::class)
    override fun doRead(buffer: Buffer?, data: ByteArray?, offset: Int, length: Int): Int {
        return stream!!.read(data, offset, length)
    }

    /**
     * Implements PullBufferStream#willReadBlock(). Delegates to the wrapped PullSourceStream.
     *
     * @return true if this stream will block on read operation, false otherwise
     */
    override fun willReadBlock(): Boolean {
        return stream!!.willReadBlock()
    }

    companion object {
        /**
         * Gets the frame size measured in bytes defined by a specific `Format`.
         *
         * @param format
         * the `Format` to determine the frame size in bytes of
         * @return the frame size measured in bytes defined by the specified `Format`
         */
        private fun getFrameSizeInBytes(format: Format): Int {
            val audioFormat = format as AudioFormat
            val frameSizeInBits = audioFormat.frameSizeInBits
            if (frameSizeInBits <= 0) return audioFormat.sampleSizeInBits / 8 * audioFormat.channels
            return if (frameSizeInBits <= 8) 1 else frameSizeInBits / 8
        }
    }
}