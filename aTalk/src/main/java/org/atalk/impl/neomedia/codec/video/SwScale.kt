/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video

import net.sf.fmj.media.AbstractCodec
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.control.FrameProcessingControlImpl
import timber.log.Timber
import java.awt.Dimension
import javax.media.Buffer
import javax.media.Format
import javax.media.PlugIn
import javax.media.format.RGBFormat
import javax.media.format.VideoFormat
import javax.media.format.YUVFormat
import kotlin.math.abs
import kotlin.math.min

/**
 * Implements an FMJ `Codec` which uses libswscale to scale images and
 * convert between color spaces (typically, RGB and YUV).
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
open class SwScale @JvmOverloads constructor(
        /**
         * The indicator which determines whether this scaler will attempt to keep
         * the width and height of YUV 420 output even.
         */
        private val fixOddYuv420Size: Boolean = false,

        /**
         * The indicator which determines whether this instance is to preserve the
         * aspect ratio of the video frames provided to this instance as input to be
         * processed. If `true`, the `size` of the
         * `outputFormat` of this instance is used to device a rectangle into
         * which a scaled video frame should fit with the input aspect ratio preserved.
         */
        private val preserveAspectRatio: Boolean = false,
) : AbstractCodec() {
    /**
     * The `FrameProcessingControl` of this `Codec` which allows
     * JMF to instruct it to drop frames because it's behind schedule.
     */
    private val frameProcessingControl = FrameProcessingControlImpl()
    private var preserveAspectRatioCachedIn: Dimension? = null
    private var preserveAspectRatioCachedOut: Dimension? = null
    private var preserveAspectRatioCachedRet: Dimension? = null

    /**
     * Supported output formats.
     */
    private val supportedOutputFormats = arrayOf(
        RGBFormat(),
        YUVFormat(YUVFormat.YUV_420)
    )

    /**
     * The pointer to the `libswscale` context.
     */
    private var swsContext = 0L
    /**
     * Initializes a new `SwScale` instance which can optionally attempt
     * to keep the width and height of YUV 420 output even and to preserve the
     * aspect ratio of the video frames provided to the instance as input to be processed.
     *
     * fixOddYuv420Size `true` to have the new instance keep the
     * width and height of YUV 420 output even; otherwise, `false`
     * preserveAspectRatio `true` to have the new instance
     * preserve the aspect ratio of the video frames provided to it as input to
     * be processed; otherwise, `false`
     */
    /**
     * Initializes a new `SwScale` instance which can optionally attempt
     * to keep the width and height of YUV 420 output even.
     *
     * fixOddYuv420Size `true` to have the new instance keep the
     * width and height of YUV 420 output even; otherwise, `false`
     */
    /**
     * Initializes a new `SwScale` instance which doesn't have an output
     * size and will use a default one when it becomes necessary unless an
     * explicit one is specified in the meantime.
     */
    init {
        inputFormats = arrayOf<Format>(
            AVFrameFormat(),
            RGBFormat(),
            YUVFormat(YUVFormat.YUV_420)
        )
        addControl(frameProcessingControl)
    }

    /**
     * Close codec.
     */
    override fun close() {
        try {
            if (swsContext != 0L) {
                FFmpeg.sws_freeContext(swsContext)
                swsContext = 0
            }
        } finally {
            super.close()
        }
    }

    /*
     * If the specified output size is tiny enough to crash sws_scale, do not accept it.
    */
    // Set the size to the outputFormat as well.
    var outputSize: Dimension
        get() {
            var outputFormat = getOutputFormat()

            if (outputFormat == null) {
                // They all have one and the same size.
                outputFormat = supportedOutputFormats[0]
            }
            return (outputFormat as VideoFormat).size
        }
        set(size) {
            // If the specified output size is tiny enough to crash sws_scale, do not accept it.
            if (size.height < MIN_SWS_SCALE_HEIGHT_OR_WIDTH || size.width < MIN_SWS_SCALE_HEIGHT_OR_WIDTH) {
                return
            }

            for (i in supportedOutputFormats.indices) {
                supportedOutputFormats[i] = setSize(supportedOutputFormats[i], size)
            }
            // Set the size to the outputFormat as well.
            setOutputFormatSize(size)
        }

    /**
     * Gets the supported output formats for an input one.
     *
     * input input format to get supported output ones for
     * @return array of supported output formats
     */
    override fun getSupportedOutputFormats(input: Format?): Array<out Format> {
        if (input == null) return supportedOutputFormats

        /*
         * if size is set for element 0 (YUVFormat), it is also set for element 1 (RGBFormat) and so on...
         */
        var size = supportedOutputFormats[0].size

        /*
         * no specified size set so return the same size as input in output format supported
         */
        val videoInput = input as VideoFormat
        if (size == null) size = videoInput.size
        val frameRate = videoInput.frameRate

        return arrayOf(
            RGBFormat(
                size,  /* maxDataLength */
                Format.NOT_SPECIFIED,  /* dataType */
                null,
                frameRate,
                32,  /* red */
                Format.NOT_SPECIFIED,  /* green */
                Format.NOT_SPECIFIED,  /* blue */
                Format.NOT_SPECIFIED),

            YUVFormat(
                size,  /* maxDataLength */
                Format.NOT_SPECIFIED,  /* dataType */
                null,
                frameRate,
                YUVFormat.YUV_420,  /* strideY */
                Format.NOT_SPECIFIED,  /* strideUV */
                Format.NOT_SPECIFIED,  /* offsetY */
                Format.NOT_SPECIFIED,  /* offsetU */
                Format.NOT_SPECIFIED,  /* offsetV */
                Format.NOT_SPECIFIED)
        )
    }

    /**
     * Calculates an output size which has the aspect ratio of a specific input
     * size and fits into a specific output size.
     *
     * in the input size which defines the aspect ratio
     * out the output size which defines the rectangle into which the returned output size is to fit
     * @return an output size which has the aspect ratio of the specified input
     * size and fits into the specified output size
     */
    private fun preserveAspectRatio(`in`: Dimension, out: Dimension): Dimension {
        val inHeight = `in`.height
        val inWidth = `in`.width
        val outHeight = out.height
        val outWidth = out.width

        /*
         * Reduce the effects of allocation and garbage collection by caching
         * the arguments and the return value.
         */
        if (preserveAspectRatioCachedIn != null && preserveAspectRatioCachedOut != null
                && preserveAspectRatioCachedIn!!.height == inHeight && preserveAspectRatioCachedIn!!.width == inWidth
                && preserveAspectRatioCachedOut!!.height == outHeight && preserveAspectRatioCachedOut!!.width == outWidth
                && preserveAspectRatioCachedRet != null) return preserveAspectRatioCachedRet!!
        var scale = false
        val heightRatio: Double
        val widthRatio: Double
        if (outHeight != inHeight && outHeight > 0) {
            scale = true
            heightRatio = inHeight / outHeight.toDouble()
        }
        else heightRatio = 1.0
        if (outWidth != inWidth && outWidth > 0) {
            scale = true
            widthRatio = inWidth / outWidth.toDouble()
        }
        else widthRatio = 1.0
        var ret = out
        if (scale) {
            val ratio = min(heightRatio, widthRatio)
            val retHeight = (outHeight * ratio).toInt()
            val retWidth = (outWidth * ratio).toInt()
            /*
             * Preserve the aspect ratio only if it is going to make noticeable
             * differences in height and/or width; otherwise, play it safe.
             */
            if (abs(retHeight - outHeight) > 1 || abs(retWidth - outWidth) > 1) {
                // Make sure to not cause sws_scale to crash.
                if (retHeight < MIN_SWS_SCALE_HEIGHT_OR_WIDTH || retWidth < MIN_SWS_SCALE_HEIGHT_OR_WIDTH) {
                    ret = Dimension(retWidth, retHeight)
                    preserveAspectRatioCachedRet = ret
                }
            }
        }
        preserveAspectRatioCachedIn = Dimension(inWidth, inHeight)
        preserveAspectRatioCachedOut = Dimension(outWidth, outHeight)
        if (ret === out) preserveAspectRatioCachedRet = preserveAspectRatioCachedOut
        return ret
    }

    /**
     * Processes (converts color space and/or scales) an input `Buffer` into an output `Buffer`.
     *
     * in the input `Buffer` to process (from)
     * out the output `Buffer` to process into
     * @return `BUFFER_PROCESSED_OK` if `in` has been successfully processed into `out`
     */
    override fun process(inBuf: Buffer, outBuf: Buffer): Int {
        if (!checkInputBuffer(inBuf)) return PlugIn.BUFFER_PROCESSED_FAILED
        if (isEOM(inBuf)) {
            propagateEOM(outBuf)
            return PlugIn.BUFFER_PROCESSED_OK
        }
        if (inBuf.isDiscard || frameProcessingControl.isMinimalProcessing) {
            outBuf.isDiscard = true
            return PlugIn.BUFFER_PROCESSED_OK
        }

        // Determine the input Format and size.
        val inFormat = inBuf.format as VideoFormat
        val thisInFormat = inputFormat
        if ((inFormat != thisInFormat) && inFormat != thisInFormat) setInputFormat(inFormat)
        val inSize = inFormat.size ?: return PlugIn.BUFFER_PROCESSED_FAILED
        val inWidth = inSize.width
        val inHeight = inSize.height
        if (inWidth < MIN_SWS_SCALE_HEIGHT_OR_WIDTH || inHeight < MIN_SWS_SCALE_HEIGHT_OR_WIDTH) {
            return PlugIn.OUTPUT_BUFFER_NOT_FILLED // Otherwise, sws_scale will crash.
        }

        // Determine the output Format and size.
        var outFormat = getOutputFormat() as VideoFormat?
        if (outFormat == null) {
            /*
             * The format of the output Buffer is not documented to be used as
             * input to the #process method. Anyway, we're trying to use it in
             * case this Codec doesn't have an outputFormat set which is unlikely to ever happen.
             */
            outFormat = outBuf.format as VideoFormat?
            if (outFormat == null) return PlugIn.BUFFER_PROCESSED_FAILED
        }
        var outSize = outFormat.size
        if (outSize == null) outSize = inSize
        else if (preserveAspectRatio) outSize = preserveAspectRatio(inSize, outSize)
        val outWidth = outSize.width
        val outHeight = outSize.height
        if (outWidth < MIN_SWS_SCALE_HEIGHT_OR_WIDTH || outHeight < MIN_SWS_SCALE_HEIGHT_OR_WIDTH) {
            return PlugIn.OUTPUT_BUFFER_NOT_FILLED // Otherwise, sws_scale will crash.
        }

        // Apply outSize to outFormat of the output Buffer.
        outFormat = setSize(outFormat, outSize)
        if (outFormat == null) return PlugIn.BUFFER_PROCESSED_FAILED
        val dstFmt: Int
        var dstLength: Int
        when (outFormat) {
            is RGBFormat -> {
                dstFmt = getFFmpegPixelFormat(outFormat)
                dstLength = outWidth * outHeight * 4
            }
            is YUVFormat -> {
                dstFmt = FFmpeg.PIX_FMT_YUV420P
                /* YUV420P is 12 bits per pixel. */
                dstLength = (outWidth * outHeight
                        + 2 * ((outWidth + 1) / 2) * ((outHeight + 1) / 2))
            }
            else -> return PlugIn.BUFFER_PROCESSED_FAILED
        }

        val outDataType = outFormat.dataType
        var dst = outBuf.data
        when {
            Format.byteArray == outDataType -> {
                if (dst == null || (dst as ByteArray).size < dstLength) dst = ByteArray(dstLength)
            }
            Format.intArray == outDataType -> {
                /* Java int is always 4 bytes. */
                dstLength = dstLength / 4 + if (dstLength % 4 == 0) 0 else 1
                if (dst == null || (dst as IntArray).size < dstLength) dst = IntArray(dstLength)
            }
            Format.shortArray == outDataType -> {
                /* Java short is always 2 bytes. */
                dstLength = dstLength / 2 + if (dstLength % 2 == 0) 0 else 1
                if (dst == null || (dst as ShortArray).size < dstLength) dst = ShortArray(dstLength)
            }
            else -> {
                Timber.e("Unsupported output data type %s", outDataType)
                return PlugIn.BUFFER_PROCESSED_FAILED
            }
        }
        val src = inBuf.data
        val srcFmt: Int
        val srcFrame: Long
        if (src is AVFrame) {
            srcFmt = (inFormat as AVFrameFormat).pixFmt
            srcFrame = src.ptr
        }
        else {
            srcFmt = if (inFormat is YUVFormat) FFmpeg.PIX_FMT_YUV420P else getFFmpegPixelFormat(inFormat as RGBFormat)
            srcFrame = 0
        }
        swsContext = FFmpeg.sws_getCachedContext(
            swsContext,
            inWidth, inHeight, srcFmt,
            outWidth, outHeight, dstFmt,
            FFmpeg.SWS_BICUBIC)
        if (srcFrame == 0L) {
            FFmpeg.sws_scale(
                swsContext,
                src, srcFmt, inWidth, inHeight, 0, inHeight,
                dst, dstFmt, outWidth, outHeight)
        }
        else {
            FFmpeg.sws_scale(
                swsContext,
                srcFrame, 0, inHeight,
                dst, dstFmt, outWidth, outHeight)
        }
        outBuf.data = dst
        outBuf.duration = inBuf.duration
        outBuf.flags = inBuf.flags
        outBuf.format = outFormat
        outBuf.length = dstLength
        outBuf.offset = 0
        outBuf.sequenceNumber = inBuf.sequenceNumber
        outBuf.timeStamp = inBuf.timeStamp

        // flags
        val inFlags = inBuf.flags
        var outFlags = outBuf.flags
        if (inFlags and Buffer.FLAG_LIVE_DATA != 0) outFlags = outFlags or Buffer.FLAG_LIVE_DATA
        if (inFlags and Buffer.FLAG_NO_WAIT != 0) outFlags = outFlags or Buffer.FLAG_NO_WAIT
        if (inFlags and Buffer.FLAG_RELATIVE_TIME != 0) outFlags = outFlags or Buffer.FLAG_RELATIVE_TIME
        if (inFlags and Buffer.FLAG_RTP_TIME != 0) outFlags = outFlags or Buffer.FLAG_RTP_TIME
        if (inFlags and Buffer.FLAG_SYSTEM_TIME != 0) outFlags = outFlags or Buffer.FLAG_SYSTEM_TIME
        outBuf.flags = outFlags
        return PlugIn.BUFFER_PROCESSED_OK
    }

    public override fun getInputFormat(): Format? {
        return inputFormat
    }

    /**
     * Sets the input format.
     *
     * format format to set
     * @return format
     */
    override fun setInputFormat(format: Format): Format? {
        /* The input must be video, a size is not required. */
        val inputFormat = if (format is VideoFormat) super.setInputFormat(format) else null
        if (inputFormat != null) {
            Timber.log(TimberLog.FINER, "%s-%08x set to input in %s",
                javaClass.name, hashCode(), inputFormat)
        }
        return inputFormat
    }

    /**
     * Sets the `Format` in which this `Codec` is to output media
     * data.
     *
     * format the `Format` in which this `Codec` is to output media data
     * @return the `Format` in which this `Codec` is currently
     * configured to output media data or `null` if `format` was
     * found to be incompatible with this `Codec`
     */
    override fun setOutputFormat(format: Format): Format {
        var fmt = format
        if (fixOddYuv420Size && fmt is YUVFormat) {
            val yuvFormat = fmt
            if (YUVFormat.YUV_420 == yuvFormat.yuvType) {
                val size = yuvFormat.size
                if (size != null && size.width > 2 && size.height > 2) {
                    val width = size.width shr 1 shl 1
                    val height = size.height shr 1 shl 1

                    if (width != size.width || height != size.height) {
                        fmt = YUVFormat(
                            Dimension(width, height),  /* maxDataLength */
                            Format.NOT_SPECIFIED,
                            yuvFormat.dataType,
                            yuvFormat.frameRate,
                            yuvFormat.yuvType,  /* strideY */
                            Format.NOT_SPECIFIED,  /* strideUV */
                            Format.NOT_SPECIFIED,
                            0,  /* offsetU */
                            Format.NOT_SPECIFIED,  /* strideV */
                            Format.NOT_SPECIFIED)
                    }
                }
            }
        }
        val outputFormat = super.setOutputFormat(fmt)
        if (outputFormat != null) {
            Timber.log(TimberLog.FINER, "%s %08x set to output in %s", javaClass.name, hashCode(), outputFormat)
        }
        return outputFormat
    }

    /**
     * Sets the size i.e. width and height of the current `outputFormat` of this `SwScale`
     *
     * size the size i.e. width and height to be set on the current
     * `outputFormat` of this `SwScale`
     */
    private fun setOutputFormatSize(size: Dimension) {
        var outputFormat = getOutputFormat() as VideoFormat?

        if (outputFormat != null) {
            outputFormat = setSize(outputFormat, size)
            if (outputFormat != null) setOutputFormat(outputFormat)
        }
    }

    companion object {
        /**
         * The minimum height and/or width of the input and/or output to be passed
         * to `sws_scale` in order to prevent its crashing.
         */
        const val MIN_SWS_SCALE_HEIGHT_OR_WIDTH = 4

        /**
         * Gets the FFmpeg `PixelFormat` equivalent of a specific FMJ `RGBFormat`.
         *
         * rgb the FMJ `RGBFormat` to get the equivalent FFmpeg `PixelFormat` of
         * @return the FFmpeg `PixelFormat` equivalent of the specified FMJ `RGBFormat`
         */
        private fun getFFmpegPixelFormat(rgb: RGBFormat): Int {
            val pixfmt = when (rgb.bitsPerPixel) {
                24 -> FFmpeg.PIX_FMT_RGB24
                32 -> when (rgb.redMask) {
                    1, 0x000000ff -> FFmpeg.PIX_FMT_BGR32
                    2, 0x0000ff00 -> FFmpeg.PIX_FMT_BGR32_1
                    3, 0x00ff0000 -> FFmpeg.PIX_FMT_RGB32
                    4, -0x1000000 -> FFmpeg.PIX_FMT_RGB32_1
                    else -> FFmpeg.PIX_FMT_NONE
                }
                else -> FFmpeg.PIX_FMT_NONE
            }
            return pixfmt
        }

        /**
         * Gets a `VideoFormat` with a specific size i.e. width and height
         * using a specific `VideoFormat` as a template.
         *
         * format the `VideoFormat` which is the template for the `VideoFormat` to be returned
         * size the size i.e. width and height of the `VideoFormat` to be returned
         * @return a `VideoFormat` with the specified `size` and based on the specified `format`
         */
        private fun setSize(format_: VideoFormat, size: Dimension): VideoFormat {
            /*
             * Since the size of the Format has changed, its size-related properties
             * should change as well. Format#intersects doesn't seem to be cool
             * because it preserves them and thus the resulting Format is inconsistent.
             */
            var format = format_
            when {
                format is RGBFormat -> {
                    val rgbFormat = format
                    val dataType = format.dataType
                    val bitsPerPixel = rgbFormat.bitsPerPixel
                    var pixelStride = rgbFormat.pixelStride
                    if (pixelStride == Format.NOT_SPECIFIED && dataType != null && bitsPerPixel != Format.NOT_SPECIFIED) {
                        pixelStride = if (dataType == Format.byteArray) bitsPerPixel / 8 else 1
                    }

                    format = RGBFormat(
                        size,  /* maxDataLength */
                        Format.NOT_SPECIFIED,
                        dataType,
                        format.frameRate,
                        bitsPerPixel,
                        rgbFormat.redMask,
                        rgbFormat.greenMask,
                        rgbFormat.blueMask,
                        pixelStride,
                        if (pixelStride == Format.NOT_SPECIFIED) Format.NOT_SPECIFIED else pixelStride * size.width /* lineStride */,
                        rgbFormat.flipped,
                        rgbFormat.endian)
                }

                format is YUVFormat -> {
                    val yuvFormat = format

                    format = YUVFormat(
                        size,  /* maxDataLength */
                        Format.NOT_SPECIFIED,
                        format.dataType,
                        format.frameRate,
                        yuvFormat.yuvType,  /* strideY */
                        Format.NOT_SPECIFIED,  /* strideUV */
                        Format.NOT_SPECIFIED,
                        0,  /* offsetU */
                        Format.NOT_SPECIFIED,  /* offsetV */
                        Format.NOT_SPECIFIED)
                }

                format != null -> {
                    Timber.w("SwScale outputFormat of type %s is not supported for optimized scaling.",
                        format.javaClass.name)
                }
            }
            return format
        }
    }

}