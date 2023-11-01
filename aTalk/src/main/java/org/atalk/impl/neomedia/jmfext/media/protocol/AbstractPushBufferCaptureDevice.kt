/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol

import java.io.IOException
import javax.media.CaptureDeviceInfo
import javax.media.Format
import javax.media.MediaLocator
import javax.media.Time
import javax.media.control.FormatControl
import javax.media.control.FrameRateControl
import javax.media.protocol.*

/**
 * Provides a base implementation of `PushBufferDataSource` and `CaptureDevice` in
 * order to facilitate implementers by taking care of boilerplate in the most common cases.
 *
 * @author Lyubomir Marinov
 */
abstract class AbstractPushBufferCaptureDevice protected constructor(locator: MediaLocator? = null) : PushBufferDataSource(), CaptureDevice {
    /**
     * The `AbstractBufferCaptureDevice` which provides the implementation of this
     * `AbstractPushBufferCaptureDevice`.
     */
    private val impl = object : AbstractBufferCaptureDevice<AbstractPushBufferStream<*>>() {
        override fun createFrameRateControl(): FrameRateControl? {
            return this@AbstractPushBufferCaptureDevice.createFrameRateControl()
        }

        override fun createStream(streamIndex: Int, formatControl: FormatControl): AbstractPushBufferStream<*> {
            return this@AbstractPushBufferCaptureDevice.createStream(streamIndex, formatControl)
        }

        @Throws(IOException::class)
        override fun doConnect() {
            this@AbstractPushBufferCaptureDevice.doConnect()
        }

        override fun doDisconnect() {
            this@AbstractPushBufferCaptureDevice.doDisconnect()
        }

        @Throws(IOException::class)
        override fun doStart() {
            this@AbstractPushBufferCaptureDevice.doStart()
        }

        @Throws(IOException::class)
        override fun doStop() {
            this@AbstractPushBufferCaptureDevice.doStop()
        }

        override fun getCaptureDeviceInfo(): CaptureDeviceInfo {
            return this@AbstractPushBufferCaptureDevice.captureDeviceInfo
        }

        /**
         * Overrides [AbstractBufferCaptureDevice.getControls] to add controls specific to
         * this `AbstractPushBufferCaptureDevice`.
         *
         * {@inheritDoc}
         */
        override fun getControls(): Array<Any> {
            return this@AbstractPushBufferCaptureDevice.controls
        }

        override fun getFormat(streamIndex: Int, oldValue: Format?): Format? {
            return this@AbstractPushBufferCaptureDevice.getFormat(streamIndex, oldValue)
        }

        override fun getSupportedFormats(streamIndex: Int): Array<Format> {
            return this@AbstractPushBufferCaptureDevice.getSupportedFormats(streamIndex)
        }

        override fun setFormat(streamIndex: Int, oldValue: Format?, newValue: Format?): Format? {
            return this@AbstractPushBufferCaptureDevice.setFormat(streamIndex, oldValue, newValue)
        }
    }
    /**
     * Initializes a new `AbstractPushBufferCaptureDevice` instance from a specific `MediaLocator`.
     *
     * @param locator the `MediaLocator` to create the new instance from
     */
    /**
     * Initializes a new `AbstractPushBufferCaptureDevice` instance.
     */
    init {
        locator?.let { setLocator(it) }
    }

    /**
     * Opens a connection to the media source specified by the `MediaLocator` of this `DataSource`.
     *
     * @throws IOException if anything goes wrong while opening the connection to the media source specified by
     * the `MediaLocator` of this `DataSource`
     */
    @Throws(IOException::class)
    override fun connect() {
        impl.connect()
    }

    /**
     * Creates a new `FrameRateControl` instance which is to allow the getting and
     * setting of the frame rate of this `AbstractPushBufferCaptureDevice`.
     *
     * @return a new `FrameRateControl` instance which is to allow the getting and
     * setting of the frame rate of this `AbstractPushBufferCaptureDevice`
     */
    protected open fun createFrameRateControl(): FrameRateControl? {
        return null
    }

    /**
     * Create a new `PushBufferStream` which is to be at a specific zero-based index in the
     * list of streams of this `PushBufferDataSource`. The `Format`-related
     * information of the new instance is to be abstracted by a specific `FormatControl`.
     *
     * @param streamIndex the zero-based index of the `PushBufferStream` in the list of streams of this
     * `PushBufferDataSource`
     * @param formatControl the `FormatControl` which is to abstract the `Format`-related
     * information of the new instance
     * @return a new `PushBufferStream` which is to be at the specified `streamIndex`
     * in the list of streams of this `PushBufferDataSource` and which has its
     * `Format`-related information abstracted by the specified `formatControl`
     */
    protected abstract fun createStream(streamIndex: Int, formatControl: FormatControl): AbstractPushBufferStream<*>

    /**
     * Closes the connection to the media source specified by the `MediaLocator` of this
     * `DataSource`. If such a connection has not been opened, the call is ignored.
     */
    override fun disconnect() {
        impl.disconnect()
    }

    /**
     * Opens a connection to the media source specified by the `MediaLocator` of this
     * `DataSource`. Allows extenders to override and be sure that there will be no request
     * to open a connection if the connection has already been opened.
     *
     * @throws IOException if anything goes wrong while opening the connection to the media source specified by
     * the `MediaLocator` of this `DataSource`
     */
    @Throws(IOException::class)
    protected open fun doConnect() {
    }

    /**
     * Closes the connection to the media source specified by the `MediaLocator` of this
     * `DataSource`. Allows extenders to override and be sure that there will be no request
     * to close a connection if the connection has not been opened yet.
     */
    protected open fun doDisconnect() {
        /*
         * While it is not clear whether the streams can be released upon disconnect,
         * com.imb.media.protocol.SuperCloneableDataSource gets the streams of the DataSource it
         * adapts (i.e. this DataSource when SourceCloneable support is to be created for it) before
         * #connect(). Unfortunately, it means that it isn't clear when the streams are to be disposed.
         */
    }

    /**
     * Starts the transfer of media data from this `DataSource`. Allows extenders to
     * override and be sure that there will be no request to start the transfer of media data if
     * it has already been started.
     *
     * @throws IOException if anything goes wrong while starting the transfer of media data from this `DataSource`
     */
    @Throws(IOException::class)
    protected open fun doStart() {
        impl.defaultDoStart()
    }

    /**
     * Stops the transfer of media data from this `DataSource`. Allows extenders to override
     * and be sure that there will be no request to stop the transfer of media data if it has not
     * been started yet.
     *
     * @throws IOException if anything goes wrong while stopping the transfer of media data from this `DataSource`
     */
    @Throws(IOException::class)
    protected open fun doStop() {
        impl.defaultDoStop()
    }

    /**
     * Gets the `CaptureDeviceInfo` of this `CaptureDevice` which describes it.
     *
     * @return the `CaptureDeviceInfo` of this `CaptureDevice` which describes it
     */
    override fun getCaptureDeviceInfo(): CaptureDeviceInfo {
        return AbstractBufferCaptureDevice.getCaptureDeviceInfo(this)!!
    }

    /**
     * Gets the content type of the media represented by this instance. The
     * `AbstractPushBufferCaptureDevice` implementation always returns
     * [ContentDescriptor.RAW].
     *
     * @return the content type of the media represented by this instance
     */
    override fun getContentType(): String {
        return ContentDescriptor.RAW
    }

    /**
     * Gets the control of the specified type available for this instance.
     *
     * @param controlType the type of the control available for this instance to be retrieved
     * @return an `Object` which represents the control of the specified type available for
     * this instance if such a control is indeed available; otherwise, `null`
     */
    override fun getControl(controlType: String): Any? {
        return impl.getControl(controlType)
    }

    /**
     * Implements [javax.media.protocol.DataSource.getControls]. Gets the controls available for this instance.
     *
     * @return an array of `Object`s which represent the controls available for this instance
     */
    override fun getControls(): Array<Any> {
        return impl.defaultGetControls()
    }

    /**
     * Gets the duration of the media represented by this instance. The
     * `AbstractPushBufferCaptureDevice` always returns [.DURATION_UNBOUNDED].
     *
     * @return the duration of the media represented by this instance
     */
    override fun getDuration(): Time {
        return DURATION_UNBOUNDED
    }

    /**
     * Gets the `Format` to be reported by the `FormatControl` of a
     * `PushBufferStream` at a specific zero-based index in the list of streams of this
     * `PushBufferDataSource`. The `PushBufferStream` may not exist at the time of
     * requesting its `Format`. Allows extenders to override the default behavior which
     * is to report any last-known format or the first `Format` from the list of supported
     * formats as defined in the JMF registration of this `CaptureDevice`.
     *
     * @param streamIndex the zero-based index of the `PushBufferStream` the `Format` of which is
     * to be retrieved
     * @param oldValue the last-known `Format` for the `PushBufferStream` at the specified
     * `streamIndex`
     * @return the `Format` to be reported by the `FormatControl` of the
     * `PushBufferStream` at the specified `streamIndex` in the list of
     * streams of this `PushBufferDataSource`.
     */
    protected fun getFormat(streamIndex: Int, oldValue: Format?): Format? {
        return impl.defaultGetFormat(streamIndex, oldValue)
    }

    /**
     * Gets an array of `FormatControl` instances each one of which can be used before
     * [.connect] to get and set the capture `Format` of each one of the capture streams.
     *
     * @return an array of `FormatControl` instances each one of which can be used before
     * [.connect] to get and set the capture `Format` of each one of the capture streams
     */
    override fun getFormatControls(): Array<FormatControl> {
        return impl.formatControls
    }

    /**
     * Gets the `Object` which is to synchronize the access to [.streams] and its return value.
     *
     * @return the `Object` which is to synchronize the access to [.streams] and its return value
     */
    protected val streamSyncRoot: Any
        get() = impl.streamSyncRoot

    /**
     * Gets the `PushBufferStream`s through which this `PushBufferDataSource` gives
     * access to its media data.
     *
     * @return an array of the `PushBufferStream`s through which this
     * `PushBufferDataSource` gives access to its media data
     */
    override fun getStreams(): Array<PushBufferStream> {
        return impl.getStreams(PushBufferStream::class.java)
    }

    /**
     * Gets the `Format`s which are to be reported by a `FormatControl` as supported
     * formats for a `PushBufferStream` at a specific zero-based index in the list of
     * streams of this `PushBufferDataSource`.
     *
     * @param streamIndex the zero-based index of the `PushBufferStream` for which the specified
     * `FormatControl` is to report the list of supported `Format`s
     * @return an array of `Format`s to be reported by a `FormatControl` as the
     * supported formats for the `PushBufferStream` at the specified
     * `streamIndex` in the list of streams of this `PushBufferDataSource`
     */
    protected open fun getSupportedFormats(streamIndex: Int): Array<Format> {
        return impl.defaultGetSupportedFormats(streamIndex)
    }

    /**
     * Attempts to set the `Format` to be reported by the `FormatControl` of a
     * `PushBufferStream` at a specific zero-based index in the list of streams of this
     * `PushBufferDataSource`. The `PushBufferStream` does not exist at the time of
     * the attempt to set its `Format`. Allows extenders to override the default behavior
     * which is to not attempt to set the specified `Format` so that they can enable setting
     * the `Format` prior to creating the `PushBufferStream`. If setting the
     * `Format` of an existing `PushBufferStream` is desired,
     * `AbstractPushBufferStream#doSetFormat(Format)` should be overridden instead.
     *
     * @param streamIndex the zero-based index of the `PushBufferStream` the `Format` of which is to be set
     * @param oldValue the last-known `Format` for the `PushBufferStream` at the specified `streamIndex`
     * @param newValue the `Format` which is to be set
     * @return the `Format` to be reported by the `FormatControl` of the
     * `PushBufferStream` at the specified `streamIndex` in the list of
     * streams of this `PushBufferStream` or `null` if the attempt to set the
     * `Format` did not success and any last-known `Format` is to be left in effect
     */
    protected open fun setFormat(streamIndex: Int, oldValue: Format?, newValue: Format?): Format? {
        return oldValue
    }

    /**
     * Starts the transfer of media data from this `DataSource`.
     *
     * @throws IOException if anything goes wrong while starting the transfer of media data from this `DataSource`
     */
    @Throws(IOException::class)
    override fun start() {
        impl.start()
    }

    /**
     * Stops the transfer of media data from this `DataSource`.
     *
     * @throws IOException if anything goes wrong while stopping the transfer of media data from this `DataSource`
     */
    @Throws(IOException::class)
    override fun stop() {
        impl.stop()
    }

    /**
     * Gets the internal array of `AbstractPushBufferStream`s through which this
     * `AbstractPushBufferCaptureDevice` gives access to its media data.
     *
     * @return the internal array of `AbstractPushBufferStream`s through which this
     * `AbstractPushBufferCaptureDevice` gives access to its media data
     */
    protected fun streams(): Array<AbstractBufferStream<*>>? {
        return impl.streams()
    }
}