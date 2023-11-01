/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol

import net.sf.fmj.media.util.RTPInfo
import org.atalk.impl.neomedia.control.AbstractControls
import org.atalk.impl.neomedia.control.AbstractFormatControl
import org.atalk.impl.neomedia.control.ControlsAdapter
import timber.log.Timber
import java.io.IOException
import java.lang.reflect.UndeclaredThrowableException
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import javax.media.*
import javax.media.Controls
import javax.media.control.FormatControl
import javax.media.control.FrameRateControl
import javax.media.protocol.*

/**
 * Facilitates the implementations of the `CaptureDevice` and `DataSource` interfaces
 * provided by `AbstractPullBufferCaptureDevice` and `AbstractPushBufferCaptureDevice`
 *
 * @param <AbstractBufferStreamT> the type of `AbstractBufferStream` through which this
 * `AbstractBufferCaptureDevice` is to give access to its media data
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
</AbstractBufferStreamT> */
abstract class AbstractBufferCaptureDevice<AbstractBufferStreamT : AbstractBufferStream<*>?> : CaptureDevice, Controls {
    /**
     * The indicator which determines whether a connection to the media source specified by the
     * `MediaLocator` of this `DataSource` has been opened.
     */
    private var connected = false

    /**
     * The `Object` to synchronize the access to the state related to the `Controls`
     * interface implementation in order to avoid locking `this` if not necessary.
     */
    private val controlsSyncRoot = Any()

    /**
     * The array of `FormatControl` instances each one of which can be used before
     * [.connect] to get and set the capture `Format` of each one of the capture streams.
     */
    private var formatControls: Array<FormatControl?>? = null

    /**
     * The `FrameRateControl`s of this `AbstractBufferCaptureDevice`.
     */
    private var frameRateControls: Array<FrameRateControl?>? = null
    private val lock = ReentrantLock()

    /**
     * The `RTPInfo`s of this `AbstractBufferCaptureDevice`.
     */
    private var rtpInfos: Array<RTPInfo?>? = null

    /**
     * The indicator which determines whether the transfer of media data from this `DataSource` has been started.
     */
    private var started = false

    /**
     * The `PushBufferStream`s through which this `PushBufferDataSource` gives access to its media data.
     *
     *
     * Warning: Caution is advised when directly using the field and access to it is to be
     * synchronized with synchronization root `this`.
     *
     */
    private var streams: Array<AbstractBufferStream<*>>? = null

    /**
     * Gets the `Object` which is to synchronize the access to [.streams] and its return value.
     *
     * @return the `Object` which is to synchronize the access to [.streams] and its return value
     */
    val streamSyncRoot = Any()

    /**
     * Opens a connection to the media source of this `AbstractBufferCaptureDevice`.
     *
     * @throws IOException if anything goes wrong while opening the connection to the media source of this
     * `AbstractBufferCaptureDevice`
     */
    @Throws(IOException::class)
    override fun connect() {
        lock()
        try {
            if (!connected) {
                doConnect()
                connected = true
            }
        } finally {
            unlock()
        }
    }

    /**
     * Creates a new `FormatControl` instance which is to be associated with a `PushBufferStream`
     * at a specific zero-based index in the list of streams of this `PushBufferDataSource`.
     * As the `FormatControl`s of a `PushBufferDataSource` can be requested before [.connect],
     * its `PushBufferStream`s may not exist at the time of the request for the creation of the
     * `FormatControl`.
     *
     * @param streamIndex the zero-based index of the `PushBufferStream` in the list of streams of this
     * `PushBufferDataSource` which is to be associated with the new `FormatControl` instance
     * @return a new `FormatControl` instance which is to be associated with a
     * `PushBufferStream` at the specified `streamIndex` in the list of
     * streams of this `PushBufferDataSource`
     */
    private fun createFormatControl(streamIndex: Int): FormatControl {
        return object : AbstractFormatControl() {
            /**
             * Gets the `Format` of the media data of the owner of this `FormatControl`.
             *
             * @return the `Format` of the media data of the owner of this `FormatControl`
             */
            override fun getFormat(): Format {
                mFormat = internalGetFormat(streamIndex, mFormat)
                return mFormat!!
            }

            /**
             * Gets the `Format`s in which the owner of this `FormatControl` is
             * capable of providing media data.
             *
             * @return an array of `Format`s in which the owner of this `FormatControl`
             * is capable of providing media data
             */
            override fun getSupportedFormats(): Array<Format>? {
                // Timber.d("FormatControl getSupportedFormats for streamIndex: %s; size = %s", streamIndex,
                //        AbstractBufferCaptureDevice.this.getSupportedFormats(streamIndex).length);
                return this@AbstractBufferCaptureDevice.getSupportedFormats(streamIndex)
            }

            /**
             * Implements [FormatControl.setFormat]. Attempts to set the
             * `Format` in which the owner of this `FormatControl` is to provide media data.
             *
             * @param format the `Format` to be set on this instance
             * @return the currently set `Format` after the attempt to set it on this
             * instance if `format` is supported by this instance and regardless of
             * whether it was actually set; `null` if `format` is not supported by this instance
             */
            override fun setFormat(format: Format): Format? {
                val oldFormat = super.getFormat()
                var setFormat = super.setFormat(format)
                if (setFormat != null) {
                    setFormat = internalSetFormat(streamIndex, oldFormat, format)!!
                }
                return setFormat
            }
        }
    }

    /**
     * Creates the `FormatControl`s of this `CaptureDevice`.
     *
     * @return an array of the `FormatControl`s of this `CaptureDevice`
     */
    private fun createFormatControls(): Array<FormatControl?> {
        val formatControl = createFormatControl(0)
        return arrayOf(formatControl) ?: EMPTY_FORMAT_CONTROLS
    }

    /**
     * Creates a new `FrameRateControl` instance which is to allow the getting and
     * setting of the frame rate of this `AbstractBufferCaptureDevice`.
     *
     * @return a new `FrameRateControl` instance which is to allow the getting and
     * setting of the frame rate of this `AbstractBufferCaptureDevice`
     */
    protected open fun createFrameRateControl(): FrameRateControl? {
        return null
    }

    /**
     * Creates a new `RTPInfo` instance of this `AbstractBufferCaptureDevice`.
     *
     * @return a new `RTPInfo` instance of this `AbstractBufferCaptureDevice`
     */
    private fun createRTPInfo(): RTPInfo {
        return RTPInfo { // TODO Auto-generated method stub
            null
        }
    }

    /**
     * Create a new `AbstractBufferStream` which is to be at a specific zero-based index in
     * the list of streams of this `AbstractBufferCaptureDevice`. The `Format`-related
     * information of the new instance is to be abstracted by a specific `FormatControl`.
     *
     * @param streamIndex the zero-based index of the `AbstractBufferStream` in the list of streams of
     * this `AbstractBufferCaptureDevice`
     * @param formatControl the `FormatControl` which is to abstract the `Format`-related
     * information of the new instance
     * @return a new `AbstractBufferStream` which is to be at the specified
     * `streamIndex` in the list of streams of this
     * `AbstractBufferCaptureDevice` and which has its `Format`-related
     * information abstracted by the specified `formatControl`
     */
    protected abstract fun createStream(streamIndex: Int, formatControl: FormatControl): AbstractBufferStreamT

    /**
     * Provides the default implementation of `AbstractBufferCaptureDevice` for [.doStart].
     *
     * @throws IOException if anything goes wrong while starting the transfer of media data from this
     * `AbstractBufferCaptureDevice`
     * @see .doStart
     */
    @Throws(IOException::class)
    fun defaultDoStart() {
        synchronized(streamSyncRoot) {
            if (streams != null) {
                for (stream in streams!!) stream.start()
            }
        }
    }

    /**
     * Provides the default implementation of `AbstractBufferCaptureDevice` for [.doStop].
     *
     * @throws IOException if anything goes wrong while stopping the transfer of media data from this
     * `AbstractBufferCaptureDevice`
     * @see .doStop
     */
    @Throws(IOException::class)
    fun defaultDoStop() {
        synchronized(streamSyncRoot) {
            if (streams != null) {
                for (stream in streams!!) stream.stop()
            }
        }
    }

    /**
     * Provides the default implementation of `AbstractBufferCaptureDevice` for [.getControls].
     *
     * @return an array of `Object`s which represent the controls available for this instance
     */
    fun defaultGetControls(): Array<Any> {
        val formatControls = internalGetFormatControls()
        val formatControlCount = formatControls?.size ?: 0
        val frameRateControls = internalGetFrameRateControls()
        val frameRateControlCount = frameRateControls.size ?: 0
        val rtpInfos = internalGetRTPInfos()
        val rtpInfoCount = rtpInfos.size ?: 0
        return when {
            formatControlCount == 0 && frameRateControlCount == 0 && rtpInfoCount == 0 -> ControlsAdapter.EMPTY_CONTROLS

            else -> {
                val controls = Array<Any>(formatControlCount + frameRateControlCount + rtpInfoCount) {Enum}
                var offset = 0
                if (formatControlCount != 0) {
                    System.arraycopy(formatControls!!, 0, controls, offset, formatControlCount)
                    offset += formatControlCount
                }
                if (frameRateControlCount != 0) {
                    System.arraycopy(frameRateControls, 0, controls, offset, frameRateControlCount)
                    offset += frameRateControlCount
                }
                if (rtpInfoCount != 0) {
                    System.arraycopy(rtpInfos, 0, controls, offset, rtpInfoCount)
                    offset += rtpInfoCount
                }
                controls
            }
        }
    }

    /**
     * Provides the default implementation of `AbstractBufferCaptureDevice` for [.getFormat].
     *
     * @param streamIndex the zero-based index of the `AbstractBufferStream` the `Format` of which
     * is to be retrieved
     * @param oldValue the last-known `Format` for the `AbstractBufferStream` at the specified
     * `streamIndex`
     * @return the `Format` to be reported by the `FormatControl` of the
     * `AbstractBufferStream` at the specified `streamIndex` in the list of
     * streams of this `AbstractBufferCaptureDevice`
     * @see .getFormat
     */
    fun defaultGetFormat(streamIndex: Int, oldValue: Format?): Format? {
        if (oldValue != null) return oldValue
        val supportedFormats = getSupportedFormats(streamIndex)
        return if (supportedFormats == null || supportedFormats.isEmpty()) null else supportedFormats[0]
    }

    /**
     * Provides the default implementation of `AbstractBufferCaptureDevice` for
     * [.getSupportedFormats].
     *
     * @param streamIndex the zero-based index of the `AbstractBufferStream` for which the specified
     * `FormatControl` is to report the list of supported `Format`s
     * @return an array of `Format`s to be reported by a `FormatControl` as the
     * supported formats for the `AbstractBufferStream` at the specified
     * `streamIndex` in the list of streams of this `AbstractBufferCaptureDevice`
     */
    fun defaultGetSupportedFormats(streamIndex: Int): Array<Format> {
        val captureDeviceInfo = captureDeviceInfo
        return if (captureDeviceInfo == null) emptyArray() else captureDeviceInfo.formats
    }

    /**
     * Closes the connection to the media source specified of this
     * `AbstractBufferCaptureDevice`. If such a connection has not been opened, the call is ignored.
     */
    override fun disconnect() {
        lock()
        try {
            try {
                stop()
            } catch (ioex: IOException) {
                Timber.e(ioex, "Failed to stop %s", javaClass.simpleName)
            }
            if (connected) {
                doDisconnect()
                connected = false
            }
        } finally {
            unlock()
        }
    }

    /**
     * Opens a connection to the media source of this `AbstractBufferCaptureDevice`. Allows
     * extenders to override and be sure that there will be no request to open a connection if the
     * connection has already been opened.
     *
     * @throws IOException if anything goes wrong while opening the connection to the media source of this
     * `AbstractBufferCaptureDevice`
     */
    @Throws(IOException::class)
    protected abstract fun doConnect()

    /**
     * Closes the connection to the media source of this `AbstractBufferCaptureDevice`.
     * Allows extenders to override and be sure that there will be no request to close a connection
     * if the connection has not been opened yet.
     */
    protected abstract fun doDisconnect()

    /**
     * Starts the transfer of media data from this `AbstractBufferCaptureDevice`. Allows
     * extenders to override and be sure that there will be no request to start the transfer of
     * media data if it has already been started.
     *
     * @throws IOException if anything goes wrong while starting the transfer of media data from this
     * `AbstractBufferCaptureDevice`
     */
    @Throws(IOException::class)
    protected abstract fun doStart()

    /**
     * Stops the transfer of media data from this `AbstractBufferCaptureDevice`. Allows
     * extenders to override and be sure that there will be no request to stop the transfer of
     * media data if it has not been started yet.
     *
     * @throws IOException if anything goes wrong while stopping the transfer of media data from this
     * `AbstractBufferCaptureDevice`
     */
    @Throws(IOException::class)
    protected abstract fun doStop()

    /**
     * Gets the `CaptureDeviceInfo` of this `CaptureDevice` which describes it.
     *
     * @return the `CaptureDeviceInfo` of this `CaptureDevice` which describes it
     */
    abstract override fun getCaptureDeviceInfo(): CaptureDeviceInfo

    /**
     * Gets the control of the specified type available for this instance.
     *
     * @param controlType the type of the control available for this instance to be retrieved
     * @return an `Object` which represents the control of the specified type available for
     * this instance if such a control is indeed available; otherwise, `null`
     */
    override fun getControl(controlType: String): Any? {
        return AbstractControls.getControl(this, controlType)
    }

    /**
     * Implements [javax.media.Controls.getControls]. Gets the controls available for this instance.
     *
     * @return an array of `Object`s which represent the controls available for this instance
     */
    override fun getControls(): Array<Any> {
        return defaultGetControls()
    }

    /**
     * Gets the `Format` to be reported by the `FormatControl` of an
     * `AbstractBufferStream` at a specific zero-based index in the list of streams of this
     * `AbstractBufferCaptureDevice`. The `AbstractBufferStream` may not exist at the
     * time of requesting its `Format`. Allows extenders to override the default behavior
     * which is to report any last-known format or the first `Format` from the list of
     * supported formats as defined in the JMF registration of this `CaptureDevice`.
     *
     * @param streamIndex the zero-based index of the `AbstractBufferStream` the `Format` of which
     * is to be retrieved
     * @param oldValue the last-known `Format` for the `PushBufferStream` at the specified
     * `streamIndex`
     * @return the `Format` to be reported by the `FormatControl` of the
     * `PushBufferStream` at the specified `streamIndex` in the list of
     * streams of this `PushBufferDataSource`.
     */
    protected abstract fun getFormat(streamIndex: Int, oldValue: Format?): Format?

    /**
     * Gets an array of `FormatControl` instances each one of which can be used before
     * [.connect] to get and set the capture `Format` of each one of the capture streams.
     *
     * @return an array of `FormatControl` instances each one of which can be used before
     * [.connect] to get and set the capture `Format` of each one of the capture streams
     */
    override fun getFormatControls(): Array<FormatControl> {
        return AbstractFormatControl.getFormatControls(this)
    }

    /**
     * Gets the `AbstractBufferStream`s through which this
     * `AbstractBufferCaptureDevice` gives access to its media data.
     *
     * @param <SourceStreamT> the type of `SourceStream` which is to be the element type of the returned array
     * @param clz the `Class` of `SourceStream` which is to be the element type of the returned array
     * @return an array of the `SourceStream`s through which this
     * `AbstractBufferCaptureDevice` gives access to its media data
    </SourceStreamT> */
    fun <SourceStreamT : SourceStream?> getStreams(clz: Class<SourceStreamT>): Array<SourceStreamT> {
        synchronized(streamSyncRoot) { return internalGetStreams(clz) }
    }

    /**
     * Gets the `Format`s which are to be reported by a `FormatControl` as supported
     * formats for a `AbstractBufferStream` at a specific zero-based index in the list of
     * streams of this `AbstractBufferCaptureDevice`.
     *
     * @param streamIndex the zero-based index of the `AbstractBufferStream` for which the specified
     * `FormatControl` is to report the list of supported `Format`s
     * @return an array of `Format`s to be reported by a `FormatControl` as the
     * supported formats for the `AbstractBufferStream` at the specified
     * `streamIndex` in the list of streams of this `AbstractBufferCaptureDevice`
     */
    protected abstract fun getSupportedFormats(streamIndex: Int): Array<Format>?

    /**
     * Gets the `Format` to be reported by the `FormatControl` of a
     * `PushBufferStream` at a specific zero-based index in the list of streams of this
     * `PushBufferDataSource`. The `PushBufferStream` may not exist at the time of
     * requesting its `Format`.
     *
     * @param streamIndex the zero-based index of the `PushBufferStream` the `Format` of which is
     * to be retrieved
     * @param oldValue the last-known `Format` for the `PushBufferStream` at the specified
     * `streamIndex`
     * @return the `Format` to be reported by the `FormatControl` of the
     * `PushBufferStream` at the specified `streamIndex` in the list of
     * streams of this `PushBufferDataSource`.
     */
    private fun internalGetFormat(streamIndex: Int, oldValue: Format?): Format? {
        if (lock.tryLock()) {
            try {
                synchronized(streamSyncRoot) {
                    if (streams != null) {
                        val stream = streams!![streamIndex]
                        if (stream != null) {
                            val streamFormat = stream.internalGetFormat()
                            if (streamFormat != null) return streamFormat
                        }
                    }
                }
            } finally {
                lock.unlock()
            }
        } else {
            /*
             * XXX In order to prevent a deadlock, do not ask the streams about the format.
             */
        }
        return getFormat(streamIndex, oldValue)
    }

    /**
     * Gets an array of `FormatControl` instances each one of which can be used before
     * [.connect] to get and set the capture `Format` of each one of the capture streams.
     *
     * @return an array of `FormatControl` instances each one of which can be used before
     * [.connect] to get and set the capture `Format` of each one of the capture streams
     */
    private fun internalGetFormatControls(): Array<FormatControl?>? {
        synchronized(controlsSyncRoot) {
            if (formatControls == null) {
                formatControls = createFormatControls()
            }
            return formatControls
        }
    }

    /**
     * Gets an array of `FrameRateControl` instances which can be used to get and/or set the
     * output frame rate of this `AbstractBufferCaptureDevice`.
     *
     * @return an array of `FrameRateControl` instances which can be used to get and/or set
     * the output frame rate of this `AbstractBufferCaptureDevice`.
     */
    private fun internalGetFrameRateControls(): Array<FrameRateControl?> {
        synchronized(controlsSyncRoot) {
            if (frameRateControls == null) {
                val frameRateControl = createFrameRateControl()

                // Don't try to create the FrameRateControl more than once.
                frameRateControls = frameRateControl?.let { arrayOf(it) } ?: arrayOfNulls(0)
            }
            return frameRateControls!!
        }
    }

    /**
     * Gets an array of `RTPInfo` instances of this `AbstractBufferCaptureDevice`.
     *
     * @return an array of `RTPInfo` instances of this `AbstractBufferCaptureDevice`.
     */
    private fun internalGetRTPInfos(): Array<RTPInfo?> {
        synchronized(controlsSyncRoot) {
            if (rtpInfos == null) {
                val rtpInfo = createRTPInfo()

                // Don't try to create the RTPInfo more than once.
                rtpInfos = rtpInfo.let { arrayOf(it) }
            }
            return rtpInfos!!
        }
    }

    /**
     * Gets the `AbstractBufferStream`s through which this
     * `AbstractBufferCaptureDevice` gives access to its media data.
     *
     * @param <SourceStreamT> the type of `SourceStream` which is to be the element type of the returned array
     * @param clz the `Class` of `SourceStream` which is to be the element type of the returned array
     * @return an array of the `SourceStream`s through which this
     * `AbstractBufferCaptureDevice` gives access to its media data
    </SourceStreamT> */
    private fun <SourceStreamT : SourceStream?> internalGetStreams(clz: Class<SourceStreamT>): Array<SourceStreamT> {
        if (streams == null) {
            val formatControls = internalGetFormatControls()
            if (formatControls != null) {
                val formatControlCount = formatControls.size
                val streams = arrayOfNulls<AbstractBufferStream<*>>(formatControlCount)
                for (i in 0 until formatControlCount) {
                    streams[i] = createStream(i, formatControls[i]!!)!!
                    // Timber.d("Index: %s; Stream: %s; Control: %s", i, streams[i], formatControls[i]);
                }
                this.streams = streams as Array<AbstractBufferStream<*>>

                /*
                 * Start the streams if this DataSource has already been started.
                 */
                if (started) {
                    for (stream in streams) {
                        try {
                            stream.start()
                        } catch (ioex: IOException) {
                            throw UndeclaredThrowableException(ioex)
                        }
                    }
                }
            }
        }

        val streamCount = if (streams == null) 0 else streams!!.size
        val clone = java.lang.reflect.Array.newInstance(clz, streamCount) as Array<SourceStreamT>
        if (streamCount != 0) System.arraycopy(streams!!, 0, clone, 0, streamCount)
        return clone
    }

    /**
     * Attempts to set the `Format` to be reported by the `FormatControl` of a `PushBufferStream`
     * at a specific zero-based index in the list of streams of this `PushBufferDataSource`.
     *
     * @param streamIndex the zero-based index of the `PushBufferStream` the `Format` of which is to be set
     * @param oldValue the last-known `Format` for the `PushBufferStream` at the specified `streamIndex`
     * @param newValue the `Format` which is to be set
     * @return the `Format` to be reported by the `FormatControl` of the
     * `PushBufferStream` at the specified `streamIndex` in the list of
     * streams of this `PushBufferStream` or `null` if the attempt to set the
     * `Format` did not success and any last-known `Format` is to be left in effect
     */
    private fun internalSetFormat(streamIndex: Int, oldValue: Format?, newValue: Format): Format? {
        lock()
        try {
            synchronized(streamSyncRoot) {
                if (streams != null) {
                    val stream = streams!![streamIndex]
                    if (stream != null) return stream.internalSetFormat(newValue)
                }
            }
        } finally {
            unlock()
        }
        return setFormat(streamIndex, oldValue, newValue)
    }

    private fun lock() {
        lock.lock()
    }

    /**
     * Attempts to set the `Format` to be reported by the `FormatControl` of a
     * `AbstractBufferStream` at a specific zero-based index in the list of streams of this
     * `AbstractBufferCaptureDevice`. The `AbstractBufferStream` does not exist at
     * the time of the attempt to set its `Format`. Allows extenders to override the default
     * behavior which is to not attempt to set the specified `Format` so that they can
     * enable setting the `Format` prior to creating the `AbstractBufferStream`. If
     * setting the `Format` of an existing `AbstractBufferStream` is desired,
     * `AbstractBufferStream#doSetFormat(Format)` should be overridden instead.
     *
     * @param streamIndex the zero-based index of the `AbstractBufferStream` the `Format` of which
     * is to be set
     * @param oldValue the last-known `Format` for the `AbstractBufferStream` at the specified
     * `streamIndex`
     * @param newValue the `Format` which is to be set
     * @return the `Format` to be reported by the `FormatControl` of the
     * `AbstractBufferStream` at the specified `streamIndex` in the list of
     * streams of this `AbstractBufferStream` or `null` if the attempt to set
     * the `Format` did not success and any last-known `Format` is to be left in effect
     */
    protected abstract fun setFormat(streamIndex: Int, oldValue: Format?, newValue: Format?): Format?

    /**
     * Starts the transfer of media data from this `AbstractBufferCaptureDevice`.
     *
     * @throws IOException if anything goes wrong while starting the transfer of media data from this
     * `AbstractBufferCaptureDevice`
     */
    @Throws(IOException::class)
    override fun start() {
        lock()
        try {
            if (!started) {
                if (!connected) {
                    throw IOException(javaClass.name + " not connected")
                }
                doStart()
                started = true
            }
        } finally {
            unlock()
        }
    }

    /**
     * Stops the transfer of media data from this `AbstractBufferCaptureDevice`.
     *
     * @throws IOException if anything goes wrong while stopping the transfer of media data from this
     * `AbstractBufferCaptureDevice`
     */
    @Throws(IOException::class)
    override fun stop() {
        lock()
        try {
            if (started) {
                doStop()
                started = false
            }
        } finally {
            unlock()
        }
    }

    /**
     * Gets the internal array of `AbstractBufferStream`s through which this
     * `AbstractBufferCaptureDevice` gives access to its media data.
     *
     * @return the internal array of `AbstractBufferStream`s through which this
     * `AbstractBufferCaptureDevice` gives access to its media data
     */
    fun streams(): Array<AbstractBufferStream<*>>? {
        return streams
    }

    private fun unlock() {
        lock.unlock()
    }

    companion object {
        /**
         * The value of the `formatControls` property of `AbstractBufferCaptureDevice`
         * which represents an empty array of `FormatControl`s. Explicitly defined in order to
         * reduce unnecessary allocations.
         */
        private val EMPTY_FORMAT_CONTROLS = arrayOfNulls<FormatControl>(0)

        /**
         * Gets the `CaptureDeviceInfo` of a specific `CaptureDevice` by locating its
         * registration in JMF using its `MediaLocator`.
         *
         * @param captureDevice the `CaptureDevice` to gets the `CaptureDeviceInfo` of
         * @return the `CaptureDeviceInfo` of the specified `CaptureDevice` as registered in JMF
         */
        fun getCaptureDeviceInfo(captureDevice: DataSource): CaptureDeviceInfo? {
            /*
         * TODO The implemented search for the CaptureDeviceInfo of this CaptureDevice by looking
         * for its MediaLocator is inefficient.
         */
            val captureDeviceInfos = CaptureDeviceManager.getDeviceList(null)
            val locator = captureDevice.locator
            for (captureDeviceInfo in captureDeviceInfos) if ((captureDeviceInfo as CaptureDeviceInfo).locator.toString() == locator.toString()) return captureDeviceInfo
            return null
        }
    }
}