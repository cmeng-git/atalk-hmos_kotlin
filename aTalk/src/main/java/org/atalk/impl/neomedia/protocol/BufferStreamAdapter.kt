/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol

import java.io.IOException
import javax.media.*
import javax.media.protocol.*

/**
 * Represents a base class for adapters of `SourceStream`s, usually ones reading data in
 * arrays of bytes and not in `Buffer`s, to `SourceStream`s reading data in
 * `Buffer`s. An example use is creating a PushBufferStream representation of a
 * PushSourceStream.
 *
 * @param <T>
 * the very type of `SourceStream` to be adapted by a `BufferStreamAdapter`
 * @author Lyubomir Marinov
</T> */
abstract class BufferStreamAdapter<T : SourceStream?>
/**
 * Initializes a new `BufferStreamAdapter` which is to adapt a specific
 * `SourceStream` into a `SourceStream` with a specific `Format`.
 *
 * @param stream the `SourceStream` to be adapted
 * @param mFormat the specific `Format` of the `SourceStream`
 */
(
        /**
         * The `SourceStream` being adapted by this instance.
         */
        val stream: T,

        /**
         * The `Format` of this stream to be reported through the output `Buffer` this instance reads data into.
         */
        val mFormat: Format?,
) : SourceStream {

    /**
     * Gets the `Format` of the data this stream provides.
     *
     * @return the `Format` of the data this stream provides
     */
    /**
     * Gets the `SourceStream` wrapped by this instance.
     *
     * @return the `SourceStream` wrapped by this instance
     */

    /**
     * Implements SourceStream#endOfStream(). Delegates to the wrapped SourceStream.
     *
     * @return true if the stream is finished, false otherwise
     */
    override fun endOfStream(): Boolean {
        return stream!!.endOfStream()
    }

    /**
     * Implements SourceStream#getContentDescriptor(). Delegates to the wrapped SourceStream.
     *
     * @return the `ContentDescriptor` of the stream
     */
    override fun getContentDescriptor(): ContentDescriptor {
        return stream!!.contentDescriptor
    }

    /**
     * Implements SourceStream#getContentLength(). Delegates to the wrapped SourceStream.
     *
     * @return content length
     */
    override fun getContentLength(): Long {
        return stream!!.contentLength
    }

    /**
     * Implements Controls#getControl(String). Delegates to the wrapped SourceStream.
     *
     * @param controlType
     * a `String` value naming the type of the control of this instance to be retrieved
     * @return an `Object` which represents the control of this instance with the specified type
     */
    override fun getControl(controlType: String): Any {
        return stream!!.getControl(controlType)
    }

    /**
     * Implements Controls#getControls(). Delegates to the wrapped SourceStream.
     *
     * @return array of JMF `Control` objects
     */
    override fun getControls(): Array<Any> {
        return stream!!.controls
    }

    /**
     * Reads byte data from this stream into a specific `Buffer` which is to use a specific
     * array of bytes for its data.
     *
     * @param buffer the `Buffer` to read byte data into from this instance
     * @param data the array of `byte`s to read data into from this instance and to be set as the
     * data of the specified `buffer`
     * @throws IOException if I/O related errors occurred during read operation
     */
    @Throws(IOException::class)
    protected fun read(buffer: Buffer, data: ByteArray?, offset: Int, length: Int) {
        val numberOfBytesRead = doRead(buffer, data, offset, length)
        buffer.data = data
        if (numberOfBytesRead >= 0) {
            buffer.length = numberOfBytesRead
        } else {
            buffer.length = 0
            if (numberOfBytesRead == -1) buffer.isEOM = true
        }
        buffer.offset = offset
        buffer.format = mFormat
    }

    /**
     * Reads byte data from this stream into a specific array of `byte`s starting the storing
     * at a specific offset and reading at most a specific number of bytes.
     *
     * @param buffer an optional `Buffer` instance associated with the specified `data`,
     * `offset` and `length` and provided to the method in case the
     * implementation would like to provide additional `Buffer` properties such as `flags`
     * @param data the array of `byte`s into which the data read from this stream is to be written
     * @param offset the offset in the specified `data` at which writing data read from this stream
     * should start
     * @param length the maximum number of bytes to be written into the specified `data`
     * @return the number of bytes read from this stream and written into the specified `data`
     * @throws IOException if I/O related errors occurred during read operation
     */
    @Throws(IOException::class)
    protected abstract fun doRead(buffer: Buffer?, data: ByteArray?, offset: Int, length: Int): Int
}