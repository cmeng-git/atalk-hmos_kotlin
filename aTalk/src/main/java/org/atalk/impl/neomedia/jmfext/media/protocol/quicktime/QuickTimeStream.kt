/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.quicktime

import okhttp3.internal.notifyAll
import org.atalk.impl.neomedia.NeomediaServiceUtils
import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.codec.video.AVFrame
import org.atalk.impl.neomedia.codec.video.AVFrameFormat
import org.atalk.impl.neomedia.codec.video.ByteBuffer
import org.atalk.impl.neomedia.jmfext.media.protocol.*
import org.atalk.impl.neomedia.quicktime.*
import java.awt.Dimension
import java.io.IOException
import javax.media.Buffer
import javax.media.Format
import javax.media.control.FormatControl
import javax.media.control.FrameRateControl
import javax.media.format.RGBFormat
import javax.media.format.VideoFormat
import javax.media.format.YUVFormat
import javax.media.protocol.BufferTransferHandler

/**
 * Implements a `PushBufferStream` using QuickTime/QTKit.
 *
 * @author Lyubomir Marinov
 */
class QuickTimeStream internal constructor(dataSource: DataSource, formatControl: FormatControl?) : AbstractPushBufferStream<DataSource?>(dataSource, formatControl) {
    /**
     * The indicator which determines whether [.captureOutput] automatically drops late
     * frames. If `false`, we have to drop them ourselves because QuickTime/QTKit will buffer
     * them all and the video will be late.
     */
    private val automaticallyDropsLateVideoFrames: Boolean

    /**
     * The pool of `ByteBuffer`s this instances is using to transfer the media data captured
     * by [.captureOutput] out of this instance through the `Buffer`s specified in its
     * [.read].
     */
    private val byteBufferPool = ByteBufferPool()

    /**
     * The `QTCaptureOutput` represented by this `SourceStream`.
     */
    val captureOutput = QTCaptureDecompressedVideoOutput()

    /**
     * The `VideoFormat` which has been successfully set on [.captureOutput].
     */
    private var captureOutputFormat: VideoFormat? = null

    /**
     * The captured media data to be returned in [.read].
     */
    private var data: ByteBuffer? = null

    /**
     * The `Format` of [.data] if known. If possible, determined by the
     * `CVPixelBuffer` video frame from which `data` is acquired.
     */
    private var dataFormat: Format? = null

    /**
     * The `Object` which synchronizes the access to the [.data]-related fields of this
     * instance.
     */
    private val dataSyncRoot = Any()

    /**
     * The time stamp in nanoseconds of [.data].
     */
    private var dataTimeStamp = 0L

    /**
     * The last-known `Format` of the media data made available by this
     * `PushBufferStream`.
     */
    private var mFormat: Format? = null

    /**
     * The captured media data to become the value of [.data] as soon as the latter becomes is
     * consumed. Thus prepares this `QuickTimeStream` to provide the latest available frame
     * and not wait for QuickTime/QTKit to capture a new one.
     */
    private var nextData: ByteBuffer? = null

    /**
     * The `Format` of [.nextData] if known.
     */
    private var nextDataFormat: Format? = null

    /**
     * The time stamp in nanoseconds of [.nextData].
     */
    private var nextDataTimeStamp = 0L

    /**
     * The `Thread` which is to call
     * [BufferTransferHandler.transferData] for this
     * `QuickTimeStream` so that the call is not made in QuickTime/QTKit and we can drop late
     * frames when [.automaticallyDropsLateVideoFrames] is `false`.
     */
    private var transferDataThread: Thread? = null

    /**
     * Initializes a new `QuickTimeStream` instance which is to have its `Format`
     * -related information abstracted by a specific `FormatControl`.
     *
     * dataSource the `DataSource` which is creating the new instance so that it becomes one of
     * its `streams`
      formatControl the `FormatControl` which is to abstract the `Format`-related
     * information of the new instance
     */
    init {
        if (formatControl != null) {
            val format = formatControl.format
            format?.let { setCaptureOutputFormat(it) }
        }
        automaticallyDropsLateVideoFrames = captureOutput
                .setAutomaticallyDropsLateVideoFrames(true)
        captureOutput.setDelegate(object : QTCaptureDecompressedVideoOutput.Delegate() {
            /**
             * Notifies this `Delegate` that the `QTCaptureOutput` to which it is set
             * has output a specific `CVImageBuffer` representing a video frame with a
             * specific `QTSampleBuffer`.
             *
             * @param videoFrame
             * the `CVImageBuffer` which represents the output video frame
             * @param sampleBuffer
             * the `QTSampleBuffer` which represents additional details about the
             * output video samples
             */
            override fun outputVideoFrameWithSampleBuffer(videoFrame: CVImageBuffer?,
                    sampleBuffer: QTSampleBuffer?) {
                captureOutputDidOutputVideoFrameWithSampleBuffer(captureOutput, videoFrame,
                        sampleBuffer)
            }
        })
        val frameRateControl = dataSource.getControl(FrameRateControl::class.java.name) as FrameRateControl?
        if (frameRateControl != null) {
            val frameRate = frameRateControl.frameRate
            if (frameRate > 0) setFrameRate(frameRate)
        }
    }

    /**
     * Notifies this instance that its `QTCaptureOutput` has output a specific
     * `CVImageBuffer` representing a video frame with a specific `QTSampleBuffer`.
     *
     * @param captureOutput
     * the `QTCaptureOutput` which has output a video frame
     * @param videoFrame
     * the `CVImageBuffer` which represents the output video frame
     * @param sampleBuffer
     * the `QTSampleBuffer` which represents additional details about the output video
     * samples
     */
    private fun captureOutputDidOutputVideoFrameWithSampleBuffer(captureOutput: QTCaptureOutput,
            videoFrame: CVImageBuffer?, sampleBuffer: QTSampleBuffer?) {
        val pixelBuffer = videoFrame as CVPixelBuffer?
        var transferData: Boolean
        val videoFrameFormat = getVideoFrameFormat(pixelBuffer)
        synchronized(dataSyncRoot) {
            if (!automaticallyDropsLateVideoFrames && data != null) {
                if (nextData != null) {
                    nextData!!.free()
                    nextData = null
                }
                nextData = byteBufferPool.getBuffer(pixelBuffer!!.byteCount)
                if (nextData != null) {
                    nextData!!.setLength(pixelBuffer.getBytes(nextData!!.ptr,
                            nextData!!.capacity))
                    nextDataTimeStamp = System.nanoTime()
                    if (nextDataFormat == null) nextDataFormat = videoFrameFormat
                }
                return
            }
            if (data != null) {
                data!!.free()
                data = null
            }
            data = byteBufferPool.getBuffer(pixelBuffer!!.byteCount)
            if (data != null) {
                data!!.setLength(pixelBuffer.getBytes(data!!.ptr, data!!.capacity))
                dataTimeStamp = System.nanoTime()
                if (dataFormat == null) dataFormat = videoFrameFormat
            }
            if (nextData != null) {
                nextData!!.free()
                nextData = null
            }
            if (automaticallyDropsLateVideoFrames) transferData = data != null else {
                transferData = false
                dataSyncRoot.notifyAll()
            }
        }
        if (transferData) {
            val transferHandler = mTransferHandler
            transferHandler?.transferData(this)
        }
    }

    /**
     * Releases the resources used by this instance throughout its existence and makes it available
     * for garbage collection. This instance is considered unusable after closing.
     *
     * @see AbstractPushBufferStream.close
     */
    override fun close() {
        super.close()
       //captureOutput.setDelegate(null)
        byteBufferPool.drain()
    }

    /**
     * Gets the `Format` of this `PushBufferStream` as directly known by it.
     *
     * @return the `Format` of this `PushBufferStream` as directly known by it or
     * `null` if this `PushBufferStream` does not directly know its
     * `Format` and it relies on the `PushBufferDataSource` which created it
     * to report its `Format`
     */
    override fun doGetFormat(): Format? {
        var format: Format?
        if (this.mFormat == null) {
            format = getCaptureOutputFormat()
            if (format == null) format = super.doGetFormat() else {
                val videoFormat = format as VideoFormat
                if (videoFormat.size != null) this.mFormat = format else {
                    val defaultSize = NeomediaServiceUtils.mediaServiceImpl!!.deviceConfiguration.getVideoSize()
                    format = videoFormat.intersects(VideoFormat( /* encoding */
                            null, Dimension(defaultSize.width, defaultSize.height),  /* maxDataLength */
                            Format.NOT_SPECIFIED,  /* dataType */
                            null,  /* frameRate */
                            Format.NOT_SPECIFIED.toFloat()))
                }
            }
        } else format = this.mFormat
        return format
    }

    /**
     * Gets the `Format` of the media data made available by this `PushBufferStream`
     * as indicated by [.captureOutput].
     *
     * @return the `Format` of the media data made available by this
     * `PushBufferStream` as indicated by [.captureOutput]
     */
    private fun getCaptureOutputFormat(): Format? {
        val pixelBufferAttributes = captureOutput.pixelBufferAttributes()
        if (pixelBufferAttributes != null) {
            val pixelFormatType = pixelBufferAttributes
                    .intForKey(CVPixelBufferAttributeKey.kCVPixelBufferPixelFormatTypeKey)
            val width = pixelBufferAttributes
                    .intForKey(CVPixelBufferAttributeKey.kCVPixelBufferWidthKey)
            val height = pixelBufferAttributes
                    .intForKey(CVPixelBufferAttributeKey.kCVPixelBufferHeightKey)
            when (pixelFormatType) {
                CVPixelFormatType.kCVPixelFormatType_32ARGB -> return if (captureOutputFormat is AVFrameFormat) {
                    AVFrameFormat(if (width == 0 && height == 0) null else Dimension(width, height),  /* frameRate */
                            Format.NOT_SPECIFIED.toFloat(), FFmpeg.PIX_FMT_ARGB,
                            CVPixelFormatType.kCVPixelFormatType_32ARGB)
                } else {
                    RGBFormat(if (width == 0 && height == 0) null else Dimension(
                            width, height),  /* maxDataLength */
                            Format.NOT_SPECIFIED, Format.byteArray,  /* frameRate */
                            Format.NOT_SPECIFIED.toFloat(), 32, 2, 3, 4)
                }
                CVPixelFormatType.kCVPixelFormatType_420YpCbCr8Planar -> return if (width == 0 && height == 0) {
                    if (captureOutputFormat is AVFrameFormat) {
                        AVFrameFormat(FFmpeg.PIX_FMT_YUV420P,
                                CVPixelFormatType.kCVPixelFormatType_420YpCbCr8Planar)
                    } else YUVFormat(YUVFormat.YUV_420)
                } else if (captureOutputFormat is AVFrameFormat) {
                    AVFrameFormat(Dimension(width, height),  /* frameRate */
                            Format.NOT_SPECIFIED.toFloat(), FFmpeg.PIX_FMT_YUV420P,
                            CVPixelFormatType.kCVPixelFormatType_420YpCbCr8Planar)
                } else {
                    val strideUV = width / 2
                    val offsetY = 0
                    val offsetU = width * height
                    val offsetV = offsetU + strideUV * height / 2
                    YUVFormat(Dimension(width, height),  /* maxDataLength */
                            Format.NOT_SPECIFIED, Format.byteArray,  /* frameRate */
                            Format.NOT_SPECIFIED.toFloat(), YUVFormat.YUV_420, width, strideUV,
                            offsetY, offsetU, offsetV)
                }
            }
        }
        return null
    }

    /**
     * Gets the output frame rate of the `QTCaptureDecompressedVideoOutput` represented by
     * this `QuickTimeStream`.
     *
     * @return the output frame rate of the `QTCaptureDecompressedVideoOutput` represented by
     * this `QuickTimeStream`
     */
    val frameRate: Float
        get() = (1.0 / captureOutput.minimumVideoFrameInterval()).toFloat()

    /**
     * Gets the `Format` of the media data made available by this `PushBufferStream`
     * as indicated by a specific `CVPixelBuffer`.
     *
     * @param videoFrame
     * the `CVPixelBuffer` which provides details about the `Format` of the
     * media data made available by this `PushBufferStream`
     * @return the `Format` of the media data made available by this
     * `PushBufferStream` as indicated by the specified `CVPixelBuffer`
     */
    private fun getVideoFrameFormat(videoFrame: CVPixelBuffer?): Format? {
        var format = mFormat
        val size = (format as VideoFormat?)!!.size
        if (size == null || size.width == 0 && size.height == 0) {
            format = format!!.intersects(VideoFormat( /* encoding */
                    null, Dimension(videoFrame!!.width, videoFrame.height),  /* maxDataLength */
                    Format.NOT_SPECIFIED,  /* dataType */
                    null,  /* frameRate */
                    Format.NOT_SPECIFIED.toFloat()))
        }
        return format
    }

    /**
     * Reads media data from this `PushBufferStream` into a specific `Buffer` without
     * blocking.
     *
     * @param buffer
     * the `Buffer` in which media data is to be read from this
     * `PushBufferStream`
     * @throws IOException
     * if anything goes wrong while reading media data from this `PushBufferStream`
     * into the specified `buffer`
     */
    @Throws(IOException::class)
    override fun read(buffer: Buffer) {
        synchronized(dataSyncRoot) {
            if (data == null) {
                buffer.length = 0
                return
            }
            if (dataFormat != null) buffer.format = dataFormat

            var format = buffer.format
            if (format == null) {
                format = this.mFormat
                if (format != null) buffer.format = format
            }
            if (format is AVFrameFormat) {
                if (AVFrame.read(buffer, format, data!!) < 0) data!!.free()
                /*
				 * XXX For the sake of safety, make sure that this instance does not reference the
				 * data instance as soon as it is set on the AVFrame.
				 */
                data = null
            } else {
                val o = buffer.data
                var bytes: ByteArray?
                val length = data!!.getLength()
                if (o is ByteArray) {
                    bytes = o
                    if (bytes.size < length) bytes = null
                } else bytes = null
                if (bytes == null) {
                    bytes = ByteArray(length)
                    buffer.data = bytes
                }
                CVPixelBuffer.memcpy(bytes, 0, length, data!!.ptr)
                data!!.free()
                data = null
                buffer.length = length
                buffer.offset = 0
            }
            buffer.flags = Buffer.FLAG_LIVE_DATA or Buffer.FLAG_SYSTEM_TIME
            buffer.timeStamp = dataTimeStamp
            if (!automaticallyDropsLateVideoFrames) dataSyncRoot.notifyAll()
        }
    }

    /**
     * Calls [BufferTransferHandler.transferData] from inside
     * [.transferDataThread] so that the call is not made in QuickTime/QTKit and we can drop
     * late frames in the meantime.
     */
    private fun runInTransferDataThread() {
        var transferData = false
        while (Thread.currentThread() == transferDataThread) {
            if (transferData) {
                val transferHandler = mTransferHandler
                transferHandler?.transferData(this)
                synchronized(dataSyncRoot) {
                    if (data != null) data!!.free()
                    data = nextData
                    dataTimeStamp = nextDataTimeStamp
                    if (dataFormat == null) dataFormat = nextDataFormat
                    nextData = null
                }
            }
            synchronized(dataSyncRoot) {
                if (data == null) {
                    data = nextData
                    dataTimeStamp = nextDataTimeStamp
                    if (dataFormat == null) dataFormat = nextDataFormat
                    nextData = null
                }
                if (data == null) {
                    var interrupted = false
                    try {
                        (dataSyncRoot as Object).wait()
                    } catch (iex: InterruptedException) {
                        interrupted = true
                    }
                    if (interrupted) Thread.currentThread().interrupt()
                    transferData = data != null
                } else transferData = true
            }
        }
    }

    /**
     * Sets the `Format` of the media data made available by this `PushBufferStream`
     * to [.captureOutput].
     *
     * @param format
     * the `Format` of the media data made available by this `PushBufferStream`
     * to be set to [.captureOutput]
     */
    private fun setCaptureOutputFormat(format: Format) {
        val videoFormat = format as VideoFormat
        val size = videoFormat.size
        val width: Int
        val height: Int

        /*
		 * FIXME Mac OS X Leopard does not seem to report the size of the QTCaptureDevice in its
		 * formatDescriptions early in its creation. The workaround presented here is to just force
		 * a specific size.
		 */
        if (size == null) {
            val defaultSize = NeomediaServiceUtils.mediaServiceImpl!!.deviceConfiguration.getVideoSize()
            width = defaultSize.width
            height = defaultSize.height
        } else {
            width = size.width
            height = size.height
        }

        var pixelBufferAttributes: NSMutableDictionary? = null
        if (width > 0 && height > 0) {
            pixelBufferAttributes = NSMutableDictionary()
            pixelBufferAttributes.setIntForKey(width,
                    CVPixelBufferAttributeKey.kCVPixelBufferWidthKey)
            pixelBufferAttributes.setIntForKey(height,
                    CVPixelBufferAttributeKey.kCVPixelBufferHeightKey)
        }

        val encoding = if (format is AVFrameFormat) {
            when (format.pixFmt) {
                FFmpeg.PIX_FMT_ARGB -> VideoFormat.RGB
                FFmpeg.PIX_FMT_YUV420P -> VideoFormat.YUV
                else -> null
            }
        } else if (format.isSameEncoding(VideoFormat.RGB)) VideoFormat.RGB else if (format.isSameEncoding(VideoFormat.YUV)) VideoFormat.YUV else null
        if (VideoFormat.RGB.equals(encoding, ignoreCase = true)) {
            if (pixelBufferAttributes == null) pixelBufferAttributes = NSMutableDictionary()
            pixelBufferAttributes.setIntForKey(CVPixelFormatType.kCVPixelFormatType_32ARGB,
                    CVPixelBufferAttributeKey.kCVPixelBufferPixelFormatTypeKey)
        } else if (VideoFormat.YUV.equals(encoding, ignoreCase = true)) {
            if (pixelBufferAttributes == null) pixelBufferAttributes = NSMutableDictionary()
            pixelBufferAttributes.setIntForKey(
                    CVPixelFormatType.kCVPixelFormatType_420YpCbCr8Planar,
                    CVPixelBufferAttributeKey.kCVPixelBufferPixelFormatTypeKey)
        } else throw IllegalArgumentException("format")

        captureOutput.setPixelBufferAttributes(pixelBufferAttributes)
        captureOutputFormat = videoFormat
    }

    /**
     * Sets the output frame rate of the `QTCaptureDecompressedVideoOutput` represented by
     * this `QuickTimeStream`.
     *
     * @param frameRate
     * the output frame rate to be set on the `QTCaptureDecompressedVideoOutput`
     * represented by this `QuickTimeStream`
     * @return the output frame rate of the `QTCaptureDecompressedVideoOutput` represented by
     * this `QuickTimeStream`
     */
    fun setFrameRate(frameRate: Float): Float {
        captureOutput.setMinimumVideoFrameInterval(1.0 / frameRate)
        return this.frameRate
    }

    /**
     * Starts the transfer of media data from this `PushBufferStream`.
     *
     * @throws IOException
     * if anything goes wrong while starting the transfer of media data from this
     * `PushBufferStream`
     */
    @Throws(IOException::class)
    override fun start() {
        super.start()
        if (!automaticallyDropsLateVideoFrames) {
            transferDataThread = object : Thread(javaClass.simpleName) {
                override fun run() {
                    runInTransferDataThread()
                }
            }
            transferDataThread!!.start()
        }
    }

    /**
     * Stops the transfer of media data from this `PushBufferStream`.
     *
     * @throws IOException
     * if anything goes wrong while stopping the transfer of media data from this
     * `PushBufferStream`
     */
    @Throws(IOException::class)
    override fun stop() {
        try {
            transferDataThread = null
            synchronized(dataSyncRoot) {
                if (data != null) {
                    data!!.free()
                    data = null
                }
                dataFormat = null
                if (nextData != null) {
                    nextData!!.free()
                    nextData = null
                }
                nextDataFormat = null
                if (!automaticallyDropsLateVideoFrames) dataSyncRoot.notifyAll()
            }
        } finally {
            super.stop()
            byteBufferPool.drain()
        }
    }
}