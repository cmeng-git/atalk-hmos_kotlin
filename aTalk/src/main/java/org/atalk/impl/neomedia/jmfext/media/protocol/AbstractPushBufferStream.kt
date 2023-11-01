/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol

import javax.media.control.FormatControl
import javax.media.protocol.BufferTransferHandler
import javax.media.protocol.PushBufferDataSource
import javax.media.protocol.PushBufferStream

/**
 * Provides a base implementation of `PushBufferStream` in order to facilitate implementers
 * by taking care of boilerplate in the most common cases.
 *
 * @author Lyubomir Marinov
 */
abstract class AbstractPushBufferStream<T : PushBufferDataSource?>
/**
 * Initializes a new `AbstractPushBufferStream` instance which is to have its
 * `Format`-related information abstracted by a specific `FormatControl`.
 *
 * @param dataSource
 * the `PushBufferDataSource` which is creating the new instance so that it
 * becomes one of its `streams`
 * @param formatControl
 * the `FormatControl` which is to abstract the `Format`-related
 * information of the new instance
 */
protected constructor(dataSource: T, formatControl: FormatControl?) : AbstractBufferStream<T>(dataSource, formatControl), PushBufferStream {
    /**
     * The `BufferTransferHandler` which is notified by this `PushBufferStream` when
     * data is available for reading.
     */
    protected var mTransferHandler: BufferTransferHandler? = null

    /**
     * Sets the `BufferTransferHandler` which is to be notified by this
     * `PushBufferStream` when data is available for reading.
     *
     * @param transferHandler
     * the `BufferTransferHandler` which is to be notified by this
     * `PushBufferStream` when data is available for reading
     */
    override fun setTransferHandler(transferHandler: BufferTransferHandler) {
        this.mTransferHandler = transferHandler
    }

    companion object {
        /**
         * The name of the `PushBufferStream` class.
         */
        val PUSH_BUFFER_STREAM_CLASS_NAME = PushBufferStream::class.java.name
    }
}