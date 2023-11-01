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
 * Provides a base implementation of `PullBufferDataSource` and `CaptureDevice` in
 * order to facilitate implementers by taking care of boilerplate in the most common cases.
 *
 * @author Lyubomir Marinov
 */
abstract class AbstractPullBufferCaptureDevice : PullBufferDataSource, CaptureDevice {
    /**
     * The `CaptureDeviceInfo`.
     */
    private var deviceInfo: CaptureDeviceInfo? = null

    /**
     * The `AbstractBufferCaptureDevice` which provides the implementation of this
     * `AbstractPullBufferCaptureDevice`.
     */
    private val impl = object : AbstractBufferCaptureDevice<AbstractPullBufferStream<*>>() {
        override fun createFrameRateControl(): FrameRateControl? {
            return this@AbstractPullBufferCaptureDevice.createFrameRateControl()
        }

        override fun createStream(streamIndex: Int, formatControl: FormatControl): AbstractPullBufferStream<*> {
            return this@AbstractPullBufferCaptureDevice.createStream(streamIndex, formatControl)
        }

        @Throws(IOException::class)
        override fun doConnect() {
            this@AbstractPullBufferCaptureDevice.doConnect()
        }

        override fun doDisconnect() {
            this@AbstractPullBufferCaptureDevice.doDisconnect()
        }

        @Throws(IOException::class)
        override fun doStart() {
            this@AbstractPullBufferCaptureDevice.doStart()
        }

        @Throws(IOException::class)
        override fun doStop() {
            this@AbstractPullBufferCaptureDevice.doStop()
        }

        override fun getCaptureDeviceInfo(): CaptureDeviceInfo {
            return this@AbstractPullBufferCaptureDevice.captureDeviceInfo
        }

        override fun getFormat(streamIndex: Int, oldValue: Format?): Format? {
            return this@AbstractPullBufferCaptureDevice.getFormat(streamIndex, oldValue)
        }

        override fun getSupportedFormats(streamIndex: Int): Array<Format> {
            return this@AbstractPullBufferCaptureDevice.getSupportedFormats(streamIndex)
        }

        override fun setFormat(streamIndex: Int, oldValue: Format?, newValue: Format?): Format? {
            return this@AbstractPullBufferCaptureDevice.setFormat(streamIndex, oldValue, newValue)
        }
    }

    /**
     * Initializes a new `AbstractPullBufferCaptureDevice` instance.
     */
    protected constructor()

    /**
     * Initializes a new `AbstractPullBufferCaptureDevice` instance from a specific `MediaLocator`.
     *
     * @param locator the `MediaLocator` to create the new instance from
     */
    protected constructor(locator: MediaLocator?) {
        setLocator(locator)
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
     * setting of the frame rate of this `AbstractPullBufferCaptureDevice`.
     *
     * @return a new `FrameRateControl` instance which is to allow the getting and
     * setting of the frame rate of this `AbstractPullBufferCaptureDevice`
     */
    protected open fun createFrameRateControl(): FrameRateControl? {
        return null
    }

    /**
     * Creates a new `PullBufferStream` which is to be at a specific zero-based index in the
     * list of streams of this `PullBufferDataSource`. The `Format`-related
     * information of the new instance is to be abstracted by a specific `FormatControl`.
     *
     * @param streamIndex the zero-based index of the `PullBufferStream` in the list of streams of this
     * `PullBufferDataSource`
     * @param formatControl the `FormatControl` which is to abstract the `Format`-related
     * information of the new instance
     * @return a new `PullBufferStream` which is to be at the specified `streamIndex`
     * in the list of streams of this `PullBufferDataSource` and which has its
     * `Format`-related information abstracted by the specified `formatControl`
     */
    protected abstract fun createStream(streamIndex: Int, formatControl: FormatControl?): AbstractPullBufferStream<*>

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
         * adapts (i.e. this DataSource when SourceCloneable support is to be created for it)
         * before #connect(). Unfortunately, it means that it isn't clear when the streams are
         * to be disposed.
         */
    }

    /**
     * Starts the transfer of media data from this `DataSource`. Allows extenders to
     * override and be sure that there will be no request to start the transfer of media data if
     * it has already been started.
     *
     * @throws IOException if anything goes wrong while starting the transfer of media data from this
     * `DataSource`
     */
    @Throws(IOException::class)
    protected fun doStart() {
        impl.defaultDoStart()
    }

    /**
     * Stops the transfer of media data from this `DataSource`. Allows extenders to override and be
     * sure that there will be no request to stop the transfer of media data if it has not been started yet.
     *
     * @throws IOException if anything goes wrong while stopping the transfer of media data from this `DataSource`
     */
    @Throws(IOException::class)
    protected fun doStop() {
        impl.defaultDoStop()
    }

    /**
     * Gets the `CaptureDeviceInfo` of this `CaptureDevice` which describes it.
     *
     * @return the `CaptureDeviceInfo` of this `CaptureDevice` which describes it
     */
    override fun getCaptureDeviceInfo(): CaptureDeviceInfo {
        return if (deviceInfo == null) AbstractBufferCaptureDevice.getCaptureDeviceInfo(this)!! else deviceInfo!!
    }

    /**
     * Gets the content type of the media represented by this instance. The
     * `AbstractPullBufferCaptureDevice` implementation always returns
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
     * Implements [javax.media.Controls.getControls]. Gets the controls available for this instance.
     *
     * @return an array of `Object`s which represent the controls available for this instance
     */
    override fun getControls(): Array<Any> {
        return impl.controls
    }

    /**
     * Gets the duration of the media represented by this instance. The
     * `AbstractPullBufferCaptureDevice` always returns [.DURATION_UNBOUNDED].
     *
     * @return the duration of the media represented by this instance
     */
    override fun getDuration(): Time {
        return DURATION_UNBOUNDED
    }

    /**
     * Gets the `Format` to be reported by the `FormatControl` of a
     * `PullBufferStream` at a specific zero-based index in the list of streams of this
     * `PullBufferDataSource`. The `PullBufferStream` may not exist at the time of
     * requesting its `Format`. Allows extenders to override the default behavior which
     * is to report any last-known format or the first `Format` from the list of supported
     * formats as defined in the JMF registration of this `CaptureDevice`.
     *
     * @param streamIndex the zero-based index of the `PullBufferStream` the `Format` of which is
     * to be retrieved
     * @param oldValue the last-known `Format` for the `PullBufferStream` at the specified
     * `streamIndex`
     * @return the `Format` to be reported by the `FormatControl` of the
     * `PullBufferStream` at the specified `streamIndex` in the list of
     * streams of this `PullBufferDataSource`.
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
     * Gets the `PullBufferStream`s through which this `PullBufferDataSource` gives
     * access to its media data.
     *
     * @return an array of the `PullBufferStream`s through which this
     * `PullBufferDataSource` gives access to its media data
     */
    override fun getStreams(): Array<PullBufferStream> {
        return impl.getStreams(PullBufferStream::class.java)
    }

    /**
     * Gets the `Format`s which are to be reported by a `FormatControl` as supported
     * formats for a `PullBufferStream` at a specific zero-based index in the list of
     * streams of this `PullBufferDataSource`.
     *
     * @param streamIndex the zero-based index of the `PullBufferStream` for which the specified
     * `FormatControl` is to report the list of supported `Format`s
     * @return an array of `Format`s to be reported by a `FormatControl` as the
     * supported formats for the `PullBufferStream` at the specified
     * `streamIndex` in the list of streams of this `PullBufferDataSource`
     */
    protected open fun getSupportedFormats(streamIndex: Int): Array<Format> {
        return impl.defaultGetSupportedFormats(streamIndex)
    }

    /**
     * Sets a specific `CaptureDeviceInfo` on this `CaptureDevice`.
     *
     * @param deviceInfo the `CaptureDeviceInfo` on this `CaptureDevice`
     */
    fun setCaptureDeviceInfo(deviceInfo: CaptureDeviceInfo?) {
        this.deviceInfo = deviceInfo
    }

    /**
     * Attempts to set the `Format` to be reported by the `FormatControl` of a
     * `PullBufferStream` at a specific zero-based index in the list of streams of this
     * `PullBufferDataSource`. The `PullBufferStream` does not exist at the time of
     * the attempt to set its `Format`. Allows extenders to override the default behavior
     * which is to not attempt to set the specified `Format` so that they can enable setting
     * the `Format` prior to creating the `PullBufferStream`. If setting the
     * `Format` of an existing `PullBufferStream` is desired,
     * `AbstractPullBufferStream#doSetFormat(Format)` should be overridden instead.
     *
     * @param streamIndex the zero-based index of the `PullBufferStream` the `Format` of which is
     * to be set
     * @param oldValue the last-known `Format` for the `PullBufferStream` at the specified
     * `streamIndex`
     * @param newValue the `Format` which is to be set
     * @return the `Format` to be reported by the `FormatControl` of the
     * `PullBufferStream` at the specified `streamIndex` in the list of
     * streams of this `PullBufferStream` or `null` if the attempt to set the
     * `Format` did not success and any last-known `Format` is to be left in effect
     */
    protected open fun setFormat(streamIndex: Int, oldValue: Format?, newValue: Format?): Format? {
        return oldValue
    }

    /**
     * Starts the transfer of media data from this `DataSource`
     *
     * @throws IOException if anything goes wrong while starting the transfer of media data from this
     * `DataSource`
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