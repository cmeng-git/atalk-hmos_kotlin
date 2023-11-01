/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import org.atalk.hmos.aTalkApp
import org.atalk.impl.neomedia.MediaServiceImpl
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.codec.Constants
import timber.log.Timber
import java.awt.Dimension
import javax.media.Format
import javax.media.ResourceUnavailableException
import javax.media.format.VideoFormat
import javax.media.format.YUVFormat

/**
 * Video encoder based on `MediaCodec`.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AndroidEncoder : AndroidCodec("AndroidEncoder", VideoFormat::class.java,
    if (isHwEncodingEnabled) SUPPORTED_OUTPUT_FORMATS else EMPTY_FORMATS, true) {
    /**
     * Indicates if this instance is using `Surface` for data source.
     */
    private val useInputSurface = isDirectSurfaceEnabled
    /**
     * {@inheritDoc}
     */
    /**
     * Input `Surface` object.
     */
    override var surface: Surface? = null
        private set

    /**
     * Creates new instance of `AndroidEncoder`.
     */
    init {
        inputFormats = if (useInputSurface) {
            arrayOf(VideoFormat(
                Constants.ANDROID_SURFACE,
                null,
                Format.NOT_SPECIFIED,
                Surface::class.java,
                Format.NOT_SPECIFIED.toFloat()))
        }
        else {
            arrayOf(YUVFormat(
                null,
                /* maxDataLength */ Format.NOT_SPECIFIED, Format.byteArray,
                /* frameRate */ Format.NOT_SPECIFIED.toFloat(), YUVFormat.YUV_420,
                /* strideY */ Format.NOT_SPECIFIED,
                /* strideUV */ Format.NOT_SPECIFIED,
                /* offsetY */ Format.NOT_SPECIFIED,
                /* offsetU */ Format.NOT_SPECIFIED,
                /* offsetV */ Format.NOT_SPECIFIED))
        }
        inputFormat = null
        outputFormat = null
    }

    /**
     * {@inheritDoc}
     */
    @Throws(ResourceUnavailableException::class)
    override fun configureMediaCodec(codec: MediaCodec, codecType: String) {
        if (inputFormat == null) throw ResourceUnavailableException("Output format not set")
        var size = (inputFormat as VideoFormat).size
                ?: throw ResourceUnavailableException("Size not set")
        if (aTalkApp.isPortrait) {
            size = Dimension(size.height, size.width)
        }
        Timber.d("Encoder video input format: %s => %s", inputFormat, size)

        // Setup encoder properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        val format = MediaFormat.createVideoFormat(codecType, size.width, size.height)
        val colorFormat = if (useInputSurface) MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface else MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
        val bitrate = (LibJitsi.mediaService as MediaServiceImpl).deviceConfiguration.videoBitrate * 1024
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 30)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        if (useInputSurface) {
            surface = codec.createInputSurface()
        }
    }

    /**
     * Gets the matching output formats for a specific format.
     *
     * @param inputFormat input format
     * @return array of formats matching input format
     */
    override fun getMatchingOutputFormats(inputFormat: Format): Array<Format> {
        if (inputFormat !is VideoFormat || !isHwEncodingEnabled) return EMPTY_FORMATS

        val size = inputFormat.size
        val frameRate = inputFormat.frameRate
        return arrayOf(
            VideoFormat(Constants.VP9, size,  /* maxDataLength */
                Format.NOT_SPECIFIED, Format.byteArray, frameRate),

            VideoFormat(Constants.VP8, size,  /* maxDataLength */
                Format.NOT_SPECIFIED, Format.byteArray, frameRate),

            ParameterizedVideoFormat(Constants.H264, size, Format.NOT_SPECIFIED,
                Format.byteArray, frameRate, ParameterizedVideoFormat.toMap(
                    VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, "0")),

            ParameterizedVideoFormat(Constants.H264, size, Format.NOT_SPECIFIED,
                Format.byteArray, frameRate, ParameterizedVideoFormat.toMap(
                    VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, "1")))
    }

    /**
     * Sets the input format.
     *
     * @param format format to set
     * @return the selected inputFormat
     */
    override fun setInputFormat(format: Format): Format? {
        if (format !is VideoFormat || matches(format, inputFormats) == null) return null
        inputFormat = format
        // Timber.d(new Exception(),"Encoder video input format set: %s", inputFormat);

        // Return the selected inputFormat
        return inputFormat
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

        // Timber.d(new Exception(),"Encoder video output format set: %s", inputFormat);
        val videoFormat = format
        /*
         * An Encoder translates raw media data in (en)coded media data. Consequently, the size of
         * the output is equal to the size of the input.
         */
        var size: Dimension? = null
        if (inputFormat != null) size = (inputFormat as VideoFormat).size
        if (size == null && format.matches(outputFormat)) size = (outputFormat as VideoFormat).size
        outputFormat = VideoFormat(videoFormat.encoding, size,  /* maxDataLength */
            Format.NOT_SPECIFIED, videoFormat.dataType, videoFormat.frameRate)

        // Return the selected outputFormat
        return outputFormat
    }

    /**
     * {@inheritDoc}
     */
    override fun useSurface(): Boolean {
        return useInputSurface
    }

    /**
     * {@inheritDoc}
     */
    override fun doClose() {
        super.doClose()
        if (surface != null) {
            surface!!.release()
            surface = null
        }
    }

    //    @Override
    //    protected void onSizeChanged(Dimension dimension)
    //    {
    //        ((VideoFormat) inputFormat).setVideoSize(dimension);
    //        Timber.d("Encoder video input format set on video dimension change: %s", inputFormat);
    //    }

    companion object {
        /**
         * Name of configuration property that enables this encoder.
         */
        const val HW_ENCODING_ENABLE_PROPERTY = "neomedia.android.hw_encode"

        /**
         * Name of configuration property that enables usage of `Surface` object as a source of video data.
         */
        const val DIRECT_SURFACE_ENCODE_PROPERTY = "neomedia.android.surface_encode"

        /**
         * Default output formats supported by this android encoder
         *
         * see: https://developer.android.com/guide/topics/media/media-formats#video-formats
         */
        private val SUPPORTED_OUTPUT_FORMATS: Array<VideoFormat>

        /**
         * List of vFormats supported by this android device. VP9 encoder only supported on certain android device.
         */
        private val vFormats = ArrayList<VideoFormat>()

        init {
            if (CodecInfo.getCodecForType(CodecInfo.MEDIA_CODEC_TYPE_VP9, true) != null) {
                vFormats.add(VideoFormat(Constants.VP9))
            }
            vFormats.add(VideoFormat(Constants.VP8))
            vFormats.add(ParameterizedVideoFormat(Constants.H264, VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, "0"))
            vFormats.add(ParameterizedVideoFormat(Constants.H264, VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, "1"))
            SUPPORTED_OUTPUT_FORMATS = vFormats.toArray(vFormats.toTypedArray())
        }

        /**
         * Returns `true` if hardware encoding is enabled.
         *
         * @return `true` if hardware encoding is enabled.
         */
        private val isHwEncodingEnabled: Boolean
            get() =
                LibJitsi.configurationService.getBoolean(HW_ENCODING_ENABLE_PROPERTY, true)

        /**
         * Returns `true` if input `Surface` mode is enabled.
         *
         * @return `true` if input `Surface` mode is enabled.
         */
        val isDirectSurfaceEnabled: Boolean
            get() =// boolean supported = AndroidUtils.hasAPI(18);
                (isHwEncodingEnabled
                        && LibJitsi.configurationService.getBoolean(DIRECT_SURFACE_ENCODE_PROPERTY, true))

        /**
         * Check if the specified hardware encoder is supported on this device
         * @see VideoMediaStreamImpl.selectVideoSize
         * @param codec Encoder name
         * @return true if supported
         */
        fun isCodecSupported(codec: String?): Boolean {
            return isDirectSurfaceEnabled && vFormats.toString().contains(codec!!)
        }
    }
}