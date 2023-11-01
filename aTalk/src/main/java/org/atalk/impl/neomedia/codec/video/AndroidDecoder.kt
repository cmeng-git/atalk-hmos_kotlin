/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import org.atalk.impl.neomedia.device.util.PreviewSurfaceProvider
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.codec.Constants
import timber.log.Timber
import java.awt.Dimension
import javax.media.Format
import javax.media.format.VideoFormat
import javax.media.format.YUVFormat

/**
 * The video decoder based on `MediaCodec`.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class AndroidDecoder : AndroidCodec("AndroidDecoder", VideoFormat::class.java, outputFormats, false) {
    /**
     * Remembers if this instance is using decoding into the `Surface`.
     */
    private val useOutputSurface: Boolean

    /**
     * Output video size.
     */
    private var outputSize: Dimension? = null

    /**
     * Create a new instance of `AndroidDecoder`.
     */
    init {
        inputFormats = if (isHwDecodingEnabled) INPUT_FORMATS else EMPTY_FORMATS
        useOutputSurface = isDirectSurfaceEnabled
    }

    /**
     * {@inheritDoc}
     * inputFormat is not used to set video size; as the video dimension is not defined prior to received video
     *
     * @see RemoteVideoLayout.setVideoPreferredSize
     */
    override fun configureMediaCodec(codec: MediaCodec, codecType: String) {
        val format = MediaFormat.createVideoFormat(codecType, 176, 144)
        format.setInteger(MediaFormat.KEY_BIT_RATE, 8000000)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 30)

        // Select color format
        val colorFormat = colorFormat
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)

        // https://github.com/google/grafika/blob/master/app/src/main/java/com/android/grafika/PlayMovieSurfaceActivity.java
        val surface = if (useSurface()) surface else null
        codec.configure(format, surface, null, 0)
    }

    /**
     * Gets the matching output formats for a specific format.
     *
     * @param inputFormat input format
     * @return array of formats matching input format
     */
    override fun getMatchingOutputFormats(inputFormat: Format): Array<out Format> {
        if (inputFormat !is VideoFormat) return EMPTY_FORMATS
        val inputVideoFormat = inputFormat
        return if (useSurface()) {
            arrayOf(
                    VideoFormat(
                            Constants.ANDROID_SURFACE,
                            inputVideoFormat.size,
                            Format.NOT_SPECIFIED,
                            Surface::class.java,
                            Format.NOT_SPECIFIED.toFloat()))
        }
        else {
            arrayOf<VideoFormat>(
                    YUVFormat( /* size */
                            inputVideoFormat.size,  /* maxDataLength */
                            Format.NOT_SPECIFIED,
                            Format.byteArray,  /* frameRate */
                            Format.NOT_SPECIFIED.toFloat(),
                            YUVFormat.YUV_420,  /* strideY */
                            Format.NOT_SPECIFIED,  /* strideUV */
                            Format.NOT_SPECIFIED,  /* offsetY */
                            Format.NOT_SPECIFIED,  /* offsetU */
                            Format.NOT_SPECIFIED,  /* offsetV */
                            Format.NOT_SPECIFIED))
        }
    }

    /**
     * Sets the `Format` in which this `Codec` is to output media data.
     *
     * @param format the `Format` in which this `Codec` is to output media data
     * @return the `Format` in which this `Codec` is currently configured to output
     * media data or `null` if `format` was found to be incompatible with this `Codec`
     */
    override fun setOutputFormat(format: Format): Format? {
        if (format !is VideoFormat || matches(format, getMatchingOutputFormats(inputFormat)) == null) return null
        if (format is YUVFormat) {
            val yuvFormat = format as YUVFormat
            outputFormat = YUVFormat( /* size */
                outputSize,  /* maxDataLength */
                format.getMaxDataLength(), Format.byteArray,  /* frameRate */
                format.getFrameRate(), YUVFormat.YUV_420,  /* strideY */
                yuvFormat.strideY,  /* strideUV */
                yuvFormat.strideUV,  /* offsetY */
                yuvFormat.offsetY,  /* offsetU */
                yuvFormat.offsetU,  /* offsetV */
                yuvFormat.offsetV)
        }
        else {
            outputFormat = VideoFormat(format.encoding, outputSize,
                format.maxDataLength, format.dataType,
                format.frameRate)
        }
        // Return the selected outputFormat
        return outputFormat
    }

    /**
     * {@inheritDoc}
     */
    override fun useSurface(): Boolean {
        return useOutputSurface
    }

    /**
     * {@inheritDoc}
     */
    override val surface: Surface?
        get() = renderSurfaceProvider!!.obtainObject()!!.surface

    /**
     * {@inheritDoc}
     */
    override fun doClose() {
        super.doClose()
        renderSurfaceProvider!!.onObjectReleased()
    }

    public override fun onSizeChanged(dimension: Dimension?) {
        outputSize = dimension
        setOutputFormat(outputFormat)
        Timber.d("Set decode outputFormat on video dimension change: %s", dimension)
    }

    companion object {
        /**
         * Name of configuration property that enables hardware decoding.
         */
        const val HW_DECODING_ENABLE_PROPERTY = "neomedia.android.hw_decode"

        /**
         * Name of configuration property that enables decoding directly into provided `Surface` object.
         */
        const val DIRECT_SURFACE_DECODE_PROPERTY = "neomedia.android.surface_decode"

        /**
         * Surface provider used to obtain `SurfaceView` object that will be used for decoded remote video rendering.
         */
        var renderSurfaceProvider: PreviewSurfaceProvider? = null

        /**
         * Default Input formats  supported by android decoder.
         * https://developer.android.com/guide/topics/media/media-formats#video-formats
         */
        private val INPUT_FORMATS = arrayOf(
                VideoFormat(Constants.VP9),
                VideoFormat(Constants.VP8),
                VideoFormat(Constants.H264),
                ParameterizedVideoFormat(Constants.H264, VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, "0"))

        /**
         * Returns `true` if hardware decoding is supported and enabled.
         *
         * @return `true` if hardware decoding is supported and enabled.
         */
        val isHwDecodingEnabled: Boolean
            get() = LibJitsi.configurationService.getBoolean(HW_DECODING_ENABLE_PROPERTY, true)

        /**
         * Returns `true` if decoding into the `Surface` is enabled.
         *
         * @return `true` if decoding into the `Surface` is enabled.
         */
        val isDirectSurfaceEnabled: Boolean
            get() = (isHwDecodingEnabled
                    && LibJitsi.configurationService.getBoolean(DIRECT_SURFACE_DECODE_PROPERTY, true))

        /**
         * Obtain the video output video format based on user defined option:
         * a. None
         * b. Direct surface
         * c. YUV format
         *
         * @return video format as per User selected options
         */
        val outputFormats: Array<Format>
            get() {
                if (!isHwDecodingEnabled) return EMPTY_FORMATS
                return if (isDirectSurfaceEnabled) {
                    arrayOf(
                            VideoFormat(Constants.ANDROID_SURFACE)
                    )
                }
                else {
                    arrayOf(YUVFormat( /* size */
                            null,  /* maxDataLength */
                            Format.NOT_SPECIFIED,
                            Format.byteArray,  /* frameRate */
                            Format.NOT_SPECIFIED.toFloat(),
                            YUVFormat.YUV_420,  /* strideY */
                            Format.NOT_SPECIFIED,  /* strideUV */
                            Format.NOT_SPECIFIED,  /* offsetY */
                            Format.NOT_SPECIFIED,  /* offsetU */
                            Format.NOT_SPECIFIED,  /* offsetV */
                            Format.NOT_SPECIFIED))
                }
            }
    }
}