/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol

import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferCaptureDevice
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferStream
import java.io.IOException
import javax.media.Buffer
import javax.media.Format
import javax.media.control.FormatControl

/**
 * Implements [PushBufferDataSource] for the purposes of [RTPTranslatorImpl] when it
 * does not have a `CaptureDevice` yet `RTPManager.createSendStream(DataSource, int)`
 * has to be called to have `RTPTranslatorImpl` send packets.
 *
 * @author Lyubomir Marinov
 */
class FakePushBufferDataSource(vararg supportedFormats: Format?) : AbstractPushBufferCaptureDevice() {
    /**
     * Initializes a new `FakePushBufferCaptureDevice` instance which is to report a
     * specific list of `Format`s as supported.
     *
     * @param supportedFormats the list of `Format`s to be reported as supported by the new instance
     */
    /**
     * The `Format`s in which this `DataSource` is capable of providing media.
     */
    // private val supportedFormats: Array<Format?>?
    private val supportedFormats = supportedFormats.clone()

    /**
     * Opens a connection to the media source specified by the `MediaLocator` of this
     * `DataSource`.
     *
     * @throws IOException
     * if anything goes wrong while opening the connection to the media source specified by
     * the `MediaLocator` of this `DataSource`
     */
    @Throws(IOException::class)
    override fun connect() {
        /*
		 * The connect, disconnect, start and stop methods of the super have been overridden in
		 * order to disable consistency checks with respect to the connected and started states.
		 */
    }

    /**
     * Create a new `PushBufferStream` which is to be at a specific zero-based index in the
     * list of streams of this `PushBufferDataSource`. The `Format`-related
     * information of the new instance is to be abstracted by a specific `FormatControl` .
     *
     * @param streamIndex
     * the zero-based index of the `PushBufferStream` in the list of streams of this
     * `PushBufferDataSource`
     * @param formatControl
     * the `FormatControl` which is to abstract the `Format`-related
     * information of the new instance
     * @return a new `PushBufferStream` which is to be at the specified `streamIndex`
     * in the list of streams of this `PushBufferDataSource` and which has its
     * `Format` -related information abstracted by the specified
     * `formatControl`
     */
    override fun createStream(streamIndex: Int, formatControl: FormatControl): FakePushBufferStream {
        return FakePushBufferStream(this, formatControl)
    }

    /**
     * Closes the connection to the media source specified of this
     * `AbstractBufferCaptureDevice`. If such a connection has not been opened, the call is
     * ignored.
     */
    override fun disconnect() {
        /*
		 * The connect, disconnect, start and stop methods of the super have been overridden in
		 * order to disable consistency checks with respect to the connected and started states.
		 */
    }

    /**
     * Gets the `Format`s which are to be reported by a `FormatControl` as supported
     * formats for a `PushBufferStream` at a specific zero-based index in the list of
     * streams of this `PushBufferDataSource`.
     *
     * @param streamIndex
     * the zero-based index of the `PushBufferStream` for which the specified
     * `FormatControl` is to report the list of supported `Format`s
     * @return an array of `Format`s to be reported by a `FormatControl` as the
     * supported formats for the `PushBufferStream` at the specified
     * `streamIndex` in the list of streams of this `PushBufferDataSource`
     */
    override fun getSupportedFormats(streamIndex: Int): Array<Format> {
        return supportedFormats.clone() as Array<Format>
    }

    /**
     * {@inheritDoc}
     *
     *
     * Allows setting an arbitrary `Format` on this `DataSource` because it does not
     * really provide any media.
     */
    override fun setFormat(streamIndex: Int, oldValue: Format?, newValue: Format?): Format? {
        return newValue
    }

    /**
     * Starts the transfer of media data from this `DataSource`.
     *
     * @throws IOException
     * if anything goes wrong while starting the transfer of media data from this
     * `DataSource`
     */
    @Throws(IOException::class)
    override fun start() {
        /*
		 * The connect, disconnect, start and stop methods of the super have been overridden in
		 * order to disable consistency checks with respect to the connected and started states.
		 */
    }

    /**
     * Stops the transfer of media data from this `DataSource`.
     *
     * @throws IOException
     * if anything goes wrong while stopping the transfer of media data from this
     * `DataSource`
     */
    @Throws(IOException::class)
    override fun stop() {
        /*
		 * The connect, disconnect, start and stop methods of the super have been overridden in
		 * order to disable consistency checks with respect to the connected and started states.
		 */
    }

    /**
     * Implements [PushBufferStream] for the purposes of `FakePushBufferDataSource`.
     */
    class FakePushBufferStream
    /**
     * Initializes a new `FakePushBufferStream` instance which is to have its
     * `Format`-related information abstracted by a specific `FormatControl`.
     *
     * @param dataSource
     * the `FakePushBufferDataSource` which is creating the new instance so that
     * it becomes one of its `streams`
     * @param formatControl
     * the `FormatControl` which is to abstract the `Format`-related
     * information of the new instance
     */
    (dataSource: FakePushBufferDataSource?, formatControl: FormatControl?) : AbstractPushBufferStream<FakePushBufferDataSource?>(dataSource, formatControl) {
        /**
         * {@inheritDoc}
         *
         *
         * Allows setting an arbitrary format on this `SourceStream` because it does not
         * really provide any media.
         */
        override fun doSetFormat(format: Format): Format {
            return format
        }

        /**
         * Reads media data from this `PushBufferStream` into a specific `Buffer`
         * without blocking.
         *
         * @param buffer
         * the `Buffer` in which media data is to be read from this
         * `PushBufferStream`
         * @throws IOException
         * if anything goes wrong while reading media data from this
         * `PushBufferStream` into the specified `buffer`
         */
        @Throws(IOException::class)
        override fun read(buffer: Buffer?) {
            /*
			 * The whole point of FakePushBufferDataSource and FakePushBufferStream is that this
			 * read method is a no-op (and this FakePushBufferStream will never invoke its
			 * associated transferHandler).
			 */
        }
    }
}