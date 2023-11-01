/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.quicktime

import org.atalk.impl.neomedia.control.FrameRateControlAdapter
import org.atalk.impl.neomedia.device.DeviceSystem
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractVideoPushBufferCaptureDevice
import org.atalk.impl.neomedia.quicktime.NSErrorException
import org.atalk.impl.neomedia.quicktime.QTCaptureDevice
import org.atalk.impl.neomedia.quicktime.QTCaptureDeviceInput
import org.atalk.impl.neomedia.quicktime.QTCaptureSession
import timber.log.Timber
import java.io.IOException
import java.lang.reflect.UndeclaredThrowableException
import java.util.*
import javax.media.Format
import javax.media.MediaLocator
import javax.media.PlugInManager
import javax.media.control.FormatControl
import javax.media.control.FrameRateControl
import javax.media.format.VideoFormat

/**
 * Implements a `PushBufferDataSource` and `CaptureDevice` using QuickTime/QTKit.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class DataSource
/**
 * Initializes a new `DataSource` instance from a specific `MediaLocator`.
 *
 * @param locator
 * the `MediaLocator` to create the new instance from
 */
/**
 * Initializes a new `DataSource` instance.
 */
@JvmOverloads constructor(locator: MediaLocator? = null) : AbstractVideoPushBufferCaptureDevice(locator) {
    /**
     * The `QTCaptureSession` which captures from [.device] and pushes media data to
     * the `PushBufferStream`s of this `PushBufferDataSource`.
     */
    private var captureSession: QTCaptureSession? = null

    /**
     * The `QTCaptureDevice` which represents the media source of this `DataSource`.
     */
    private var device: QTCaptureDevice? = null

    /**
     * Overrides [AbstractVideoPushBufferCaptureDevice.createFrameRateControl] to provide a
     * `FrameRateControl` which gets and sets the frame rate of the
     * `QTCaptureDecompressedVideoOutput` represented by the `QuickTimeStream` made
     * available by this `DataSource`.
     *
     * {@inheritDoc}
     *
     * @see AbstractVideoPushBufferCaptureDevice.createFrameRateControl
     */
    override fun createFrameRateControl(): FrameRateControl {
        return object : FrameRateControlAdapter() {
            /**
             * The output frame rate to be managed by this `FrameRateControl` when there is
             * no `QuickTimeStream` to delegate to.
             */
            private var frameRate = -1f
            override fun getFrameRate(): Float {
                var frameRate = -1f
                var frameRateFromQuickTimeStream = false
                synchronized(streamSyncRoot) {
                    val streams = streams()
                    if (streams != null && streams.isNotEmpty()) {
                        for (stream in streams) {
                            val quickTimeStream = stream as QuickTimeStream?
                            if (quickTimeStream != null) {
                                frameRate = quickTimeStream.frameRate
                                frameRateFromQuickTimeStream = true
                                if (frameRate != -1f) break
                            }
                        }
                    }
                }
                return if (frameRateFromQuickTimeStream) frameRate else this.frameRate
            }

            override fun setFrameRate(frameRate: Float): Float {
                var setFrameRate = -1f
                var frameRateFromQuickTimeStream = false
                synchronized(streamSyncRoot) {
                    val streams = streams()
                    if (streams != null && streams.isNotEmpty()) {
                        for (stream in streams) {
                            val quickTimeStream = stream as QuickTimeStream?
                            if (quickTimeStream != null) {
                                val quickTimeStreamFrameRate = quickTimeStream.setFrameRate(frameRate)
                                if (quickTimeStreamFrameRate != -1f) {
                                    setFrameRate = quickTimeStreamFrameRate
                                }
                                frameRateFromQuickTimeStream = true
                            }
                        }
                    }
                }
                return if (frameRateFromQuickTimeStream) setFrameRate else {
                    this.frameRate = frameRate
                    this.frameRate
                }
            }
        }
    }

    /**
     * Creates a new `PushBufferStream` which is to be at a specific zero-based index in the
     * list of streams of this `PushBufferDataSource`. The `Format`-related
     * information of the new instance is to be abstracted by a specific `FormatControl`.
     *
     * @param streamIndex the zero-based index of the `PushBufferStream` in the list of streams of this
     * `PushBufferDataSource`
     * @param formatControl the `FormatControl` which is to abstract the `Format`-related
     * information of the new instance
     *
     * @return a new `PushBufferStream` which is to be at the specified `streamIndex`
     * in the list of streams of this `PushBufferDataSource` and which has its
     * `Format`-related information abstracted by the specified
     * `formatControl`
     * @see AbstractPushBufferCaptureDevice.createStream
     */
    override fun createStream(streamIndex: Int, formatControl: FormatControl): QuickTimeStream {
        val stream = QuickTimeStream(this, formatControl)
        if (captureSession != null) try {
            captureSession!!.addOutput(stream.captureOutput)
        } catch (nseex: NSErrorException) {
            Timber.e(nseex, "Failed to addOutput to QTCaptureSession")
            throw UndeclaredThrowableException(nseex)
        }
        return stream
    }

    /**
     * Opens a connection to the media source specified by the `MediaLocator` of this
     * `DataSource`.
     *
     * @throws IOException
     * if anything goes wrong while opening the connection to the media source specified by
     * the `MediaLocator` of this `DataSource`
     * @see AbstractPushBufferCaptureDevice.doConnect
     */
    @Throws(IOException::class)
    override fun doConnect() {
        super.doConnect()
        val deviceIsOpened = try {
            device!!.open()
        } catch (nseex: NSErrorException) {
            val ioex = IOException()
            ioex.initCause(nseex)
            throw ioex
        }
        if (!deviceIsOpened) throw IOException("Failed to open QTCaptureDevice")
        val deviceInput = QTCaptureDeviceInput.deviceInputWithDevice(device!!)
        captureSession = QTCaptureSession()
        try {
            captureSession!!.addInput(deviceInput)
        } catch (nseex: NSErrorException) {
            val ioex = IOException()
            ioex.initCause(nseex)
            throw ioex
        }

        /*
		 * Add the QTCaptureOutputs represented by the QuickTimeStreams (if any) to the
		 * QTCaptureSession.
		 */
        synchronized(streamSyncRoot) {
            val streams = streams()
            if (streams != null) {
                for (stream in streams) {
                    try {
                        captureSession!!.addOutput((stream as QuickTimeStream).captureOutput)
                    } catch (nseex: NSErrorException) {
                        Timber.e(nseex, "Failed to addOutput to QTCaptureSession")
                        val ioex = IOException()
                        ioex.initCause(nseex)
                        throw ioex
                    }
                }
            }
        }
    }

    /**
     * Closes the connection to the media source specified by the `MediaLocator` of this
     * `DataSource`.
     *
     * @see AbstractPushBufferCaptureDevice.doDisconnect
     */
    override fun doDisconnect() {
        super.doDisconnect()
        if (captureSession != null) {
            captureSession!!.close()
            captureSession = null
        }
        device!!.close()
    }

    /**
     * Starts the transfer of media data from this `DataSource`.
     *
     * @throws IOException
     * if anything goes wrong while starting the transfer of media data from this
     * `DataSource`
     * @see AbstractPushBufferCaptureDevice.doStart
     */
    @Throws(IOException::class)
    override fun doStart() {
        captureSession!!.startRunning()
        super.doStart()
    }

    /**
     * Stops the transfer of media data from this `DataSource`.
     *
     * @throws IOException
     * if anything goes wrong while stopping the transfer of media data from this
     * `DataSource`
     * @see AbstractPushBufferCaptureDevice.doStop
     */
    @Throws(IOException::class)
    override fun doStop() {
        super.doStop()
        captureSession!!.stopRunning()
    }

    /**
     * Gets the `Format`s which are to be reported by a `FormatControl` as supported
     * formats for a `PushBufferStream` at a specific zero-based index in the list of streams
     * of this `PushBufferDataSource`.
     *
     * @param streamIndex
     * the zero-based index of the `PushBufferStream` for which the specified
     * `FormatControl` is to report the list of supported `Format`s
     * @return an array of `Format`s to be reported by a `FormatControl` as the
     * supported formats for the `PushBufferStream` at the specified
     * `streamIndex` in the list of streams of this `PushBufferDataSource`
     * @see AbstractPushBufferCaptureDevice.getSupportedFormats
     */
    override fun getSupportedFormats(streamIndex: Int): Array<Format> {
        return getSupportedFormats(super.getSupportedFormats(streamIndex))
    }

    /**
     * Sets the `QTCaptureDevice` which represents the media source of this
     * `DataSource`.
     *
     * @param device
     * the `QTCaptureDevice` which represents the media source of this
     * `DataSource`
     */
    private fun setDevice(device: QTCaptureDevice?) {
        if (this.device != device) this.device = device
    }

    /**
     * Attempts to set the `Format` to be reported by the `FormatControl` of a
     * `PushBufferStream` at a specific zero-based index in the list of streams of this
     * `PushBufferDataSource`. The `PushBufferStream` does not exist at the time of
     * the attempt to set its `Format`.
     *
     * @param streamIndex
     * the zero-based index of the `PushBufferStream` the `Format` of which is to be set
     * @param oldValue the last-known `Format` for the `PushBufferStream` at the specified `streamIndex`
     * @param newValue the `Format` which is to be set
     * @return the `Format` to be reported by the `FormatControl` of the
     * `PushBufferStream` at the specified `streamIndex` in the list of
     * streams of this `PushBufferStream` or `null` if the attempt to set the
     * `Format` did not success and any last-known `Format` is to be left in effect
     * @see AbstractPushBufferCaptureDevice.format
     */
    override fun setFormat(streamIndex: Int, oldValue: Format?, newValue: Format?): Format? {
        // This DataSource supports setFormat.
        return newValue as? VideoFormat ?: super.setFormat(streamIndex, oldValue, newValue)
    }

    /**
     * Sets the `MediaLocator` which specifies the media source of this `DataSource`.
     *
     * @param locator
     * the `MediaLocator` which specifies the media source of this `DataSource`
     * @see javax.media.protocol.DataSource.setLocator
     */
    override fun setLocator(locator: MediaLocator?) {
        var locator_ = locator
        super.setLocator(locator_)
        locator_ = getLocator()
        val device = if (locator_ != null
                && DeviceSystem.LOCATOR_PROTOCOL_QUICKTIME.equals(locator_.protocol, ignoreCase = true)) {
            val deviceUID = locator_.remainder
            QTCaptureDevice.deviceWithUniqueID(deviceUID)
        } else null
        setDevice(device)
    }

    companion object {
        /**
         * The list of `Format`s to be reported by `DataSource` instances as supported
         * formats.
         */
        private var supportedFormats: Array<Format>? = null

        /**
         * Gets a list of `Format`s which are more specific than given `Format`s with
         * respect to video size. The implementation tries to come up with sane video sizes (for
         * example, by looking for codecs which accept the encodings of the specified generic
         * `Format`s and using their sizes if any).
         *
         * @param genericFormats
         * the `Format`s from which more specific are to be derived
         * @return a list of `Format`s which are more specific than the given `Format`s
         * with respect to video size
         */
        @Synchronized
        private fun getSupportedFormats(genericFormats: Array<Format>): Array<Format> {
            if (supportedFormats != null && supportedFormats!!.isNotEmpty()) return supportedFormats!!.clone()
            val specificFormats = LinkedList<Format>()
            for (genericFormat in genericFormats) {
                val genericVideoFormat = genericFormat as VideoFormat?
                if (genericVideoFormat!!.size == null) {
                    val codecs = PlugInManager.getPlugInList(VideoFormat(
                            genericVideoFormat.encoding), null, PlugInManager.CODEC)
                    for (codec in codecs) {
                        val supportedInputFormats = PlugInManager.getSupportedInputFormats(codec.toString(),
                                PlugInManager.CODEC)
                        for (supportedInputFormat in supportedInputFormats) if (supportedInputFormat is VideoFormat) {
                            val size = supportedInputFormat.size
                            if (size != null) specificFormats.add(genericFormat.intersects(VideoFormat(null,
                                    size, Format.NOT_SPECIFIED, null, Format.NOT_SPECIFIED.toFloat())))
                        }
                    }
                }
                specificFormats.add(genericFormat)
            }
            supportedFormats = specificFormats.toTypedArray()
            return supportedFormats!!.clone()
        }
    }
}