/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video.h264

import net.iharder.Base64
import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.codec.video.AVFrame
import org.atalk.impl.neomedia.codec.video.AVFrameFormat
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl
import org.atalk.service.neomedia.codec.Constants
import org.atalk.service.neomedia.control.KeyFrameControl
import timber.log.Timber
import java.awt.Dimension
import java.io.ByteArrayOutputStream
import javax.media.Buffer
import javax.media.Format
import javax.media.PlugIn
import javax.media.ResourceUnavailableException
import javax.media.format.VideoFormat

/**
 * Decodes H.264 NAL units and returns the resulting frames as FFmpeg `AVFrame`s (i.e. in YUV format).
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class JNIDecoder : AbstractCodec2("H.264 Decoder", VideoFormat::class.java, SUPPORTED_OUTPUT_FORMATS) {
    /**
     * The codec context native pointer we will use.
     */
    private var avctx = 0L

    /**
     * The `AVFrame` in which the video frame decoded from the encoded media data is stored.
     */
    private var avframe: AVFrame? = null

    /**
     * If decoder has got a picture. Use array to pass a pointer
     */
    private val got_picture = BooleanArray(1)
    private var gotPictureAtLeastOnce = false

    /**
     * The `KeyFrameControl` used by this `JNIDecoder` to control its key frame-related logic.
     */
    private var keyFrameControl: KeyFrameControl? = null

    /**
     * Array of output `VideoFormat`s.
     */
    private val outputFormats: Array<VideoFormat>

    /**
     * The last known width of [.avctx] i.e. the video output by this `JNIDecoder`.
     * Used to detect changes in the output size.
     */
    private var mWidth = 0

    /**
     * The last known height of [.avctx] i.e. the video output by this `JNIDecoder`.
     * Used to detect changes in the output size.
     */
    private var mHeight = 0

    /**
     * Initializes a new `JNIDecoder` instance which is to decode H.264 NAL units into frames in YUV format.
     */
    init {

        /*
         * Explicitly state both ParameterizedVideoFormat (to receive any format parameters which
         * may be of concern to this JNIDecoder) and VideoFormat (to make sure that nothing
         * breaks because of equality and/or matching tests involving ParameterizedVideoFormat).
         */
        inputFormats = arrayOf(
            ParameterizedVideoFormat(Constants.H264),
            VideoFormat(Constants.H264))

        outputFormats = SUPPORTED_OUTPUT_FORMATS
    }

    /**
     * Check `Format`.
     *
     * @param format `Format` to check
     * @return true if `Format` is H264_RTP
     */
    fun checkFormat(format: Format): Boolean {
        return format.encoding == Constants.H264_RTP
    }

    /**
     * Close `Codec`.
     */
    override fun doClose() {
        Timber.d("Closing decoder")
        FFmpeg.avcodec_close(avctx)
        FFmpeg.av_free(avctx)
        avctx = 0

        if (avframe != null) {
            avframe!!.free()
            avframe = null
        }
        gotPictureAtLeastOnce = false
    }

    /**
     * Init the codec instances.
     */
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        Timber.d("Opening decoder")
        if (avframe != null) {
            avframe!!.free()
            avframe = null
        }
        avframe = AVFrame()

        val avcodec: Long = FFmpeg.avcodec_find_decoder(FFmpeg.CODEC_ID_H264)
        if (avcodec == 0L) {
            throw ResourceUnavailableException("Could not find H.264 decoder.")
        }

        avctx = FFmpeg.avcodec_alloc_context3(avcodec)
        FFmpeg.avcodeccontext_set_workaround_bugs(avctx, FFmpeg.FF_BUG_AUTODETECT)

        /* allow to pass the incomplete frame to decoder */
        FFmpeg.avcodeccontext_add_flags2(avctx, FFmpeg.CODEC_FLAG2_CHUNKS)
        if (FFmpeg.avcodec_open2(avctx, avcodec) < 0)
            throw RuntimeException("Could not open H.264 decoder.")

        gotPictureAtLeastOnce = false

        /*
         * After this JNIDecoder has been opened, handle format parameters such as
         * sprop-parameter-sets which require this JNIDecoder to be in the opened state.
         */
        handleFmtps()
    }

    /**
     * Decodes H.264 media data read from a specific input `Buffer` into a specific output `Buffer`.
     *
     * @param inBuf input `Buffer`
     * @param outBuf output `Buffer`
     * @return `BUFFER_PROCESSED_OK` if `in` has been successfully processed
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        // Ask FFmpeg to decode.
        got_picture[0] = false
        // TODO Take into account the offset of the input Buffer.
        FFmpeg.avcodec_decode_video(avctx, avframe!!.ptr, got_picture, inBuf.data as ByteArray, inBuf.length)

        if (!got_picture[0]) {
            if (inBuf.flags and Buffer.FLAG_RTP_MARKER != 0) {
                if (keyFrameControl != null) keyFrameControl!!.requestKeyFrame(!gotPictureAtLeastOnce)
            }
            outBuf.isDiscard = true
            return PlugIn.BUFFER_PROCESSED_OK
        }
        gotPictureAtLeastOnce = true

        // format: cmeng: must get the output dimension to allow auto rotation
        val width: Int = FFmpeg.avcodeccontext_get_width(avctx)
        val height: Int = FFmpeg.avcodeccontext_get_height(avctx)

        // cmeng (20210309) = decoded avframe.width and avframe.height; does not get updated when inBuf video data is rotated.
        // There h264 currently cannot support auto rotation when remote camera is rotated. VP8 is OK
        if (width > 0 && height > 0 && (mWidth != width || mHeight != height)) {
            Timber.d("H264 decode video size changed: [width=%s, height=%s]=>[width=%s, height=%s]", mWidth, mHeight, width, height)
            mWidth = width
            mHeight = height

            // Output in same size and frame rate as input.
            val outSize = Dimension(mWidth, mHeight)
            val inFormat: VideoFormat = inBuf.format as VideoFormat
            val outFrameRate = ensureFrameRate(inFormat.frameRate)
            outputFormat = AVFrameFormat(outSize, outFrameRate, FFmpeg.PIX_FMT_YUV420P)
        }
        outBuf.format = outputFormat

        // data
        if (outBuf.data != avframe)
            outBuf.data = avframe

        // timeStamp
        val pts: Long = FFmpeg.avframe_get_pts(avframe!!.ptr) //  FFmpeg.AV_NOPTS_VALUE; // TODO avframe_get_pts(avframe);
        if (pts == FFmpeg.AV_NOPTS_VALUE) {
            outBuf.timeStamp = Buffer.TIME_UNKNOWN
        }
        else {
            outBuf.timeStamp = pts
            var outFlags = outBuf.flags
            outFlags = outFlags or Buffer.FLAG_RELATIVE_TIME
            outFlags = outFlags and (Buffer.FLAG_RTP_TIME or Buffer.FLAG_SYSTEM_TIME).inv()
            outBuf.flags = outFlags
        }
        return PlugIn.BUFFER_PROCESSED_OK
    }

    /**
     * Ensure frame rate.
     *
     * @param frameRate frame rate
     * @return frame rate
     */
    private fun ensureFrameRate(frameRate: Float): Float {
        return frameRate
    }

    /**
     * Get matching outputs for a specified input `Format`.
     *
     * @param inputFormat input `Format`
     * @return array of matching outputs or null if there are no matching outputs.
     */
    override fun getMatchingOutputFormats(inputFormat: Format): Array<Format> {
        val inputVideoFormat = inputFormat as VideoFormat

        return arrayOf(
            AVFrameFormat(
                inputVideoFormat.size,
                ensureFrameRate(inputVideoFormat.frameRate),
                FFmpeg.PIX_FMT_YUV420P
            )
        )
    }

    /**
     * Get all supported output `Format`s.
     *
     * @param inputFormat input `Format` to determine corresponding output `Format/code>s
     * @return an array of supported output `Format`s
    ` */
    override fun getSupportedOutputFormats(inputFormat: Format?): Array<Format> {
        val supportedOutputFormats = if (inputFormat == null) {
            outputFormats
        }
        else {
            // mismatch input format
            if (inputFormat !is VideoFormat || matches(inputFormat, inputFormats) == null) {
                EMPTY_FORMATS
            }
            else {
                // match input format
                getMatchingOutputFormats(inputFormat)
            }
        }

        return supportedOutputFormats as Array<Format>
    }

    /**
     * Handles any format parameters of the input and/or output `Format`s with which this
     * `JNIDecoder` has been configured. For example, takes into account the format
     * parameter `sprop-parameter-sets` if it is specified by the input `Format`.
     */
    private fun handleFmtps() {
        try {
            val f: Format = getInputFormat()
            if (f is ParameterizedVideoFormat) {
                val spropParameterSets = f.getFormatParameter(VideoMediaFormatImpl.H264_SPROP_PARAMETER_SETS_FMTP)

                if (spropParameterSets != null) {
                    val nals = ByteArrayOutputStream()

                    for (s in spropParameterSets.split(",")) {
                        if (s != null && s.isNotEmpty()) {
                            val nal = Base64.decode(s)
                            if (nal != null && nal.isNotEmpty()) {
                                nals.write(H264.NAL_PREFIX)
                                nals.write(nal)
                            }
                        }
                    }
                    if (nals.size() != 0) {
                        // Add padding because it seems to be required by FFmpeg.
                        for (i in 0 until FFmpeg.FF_INPUT_BUFFER_PADDING_SIZE) {
                            nals.write(0)
                        }

                        /*
                         * In accord with RFC 6184 "RTP Payload Format for H.264 Video", place the
                         * NAL units conveyed by sprop-parameter-sets in the NAL unit stream to
                         * precede any other NAL units in decoding order.
                         */
                        FFmpeg.avcodec_decode_video(avctx, avframe!!.ptr, got_picture,
                            nals.toByteArray(), nals.size())
                    }
                }
            }
            /*
             * Because the handling of format parameter is new at the time of this writing and it
             * currently handles only the format parameter sprop-parameter-sets the failed
             * handling of which will be made visible later on anyway, do not let it kill this JNIDecoder.
             */
        } catch (t: Throwable) {
            when (t) {
                is InterruptedException -> Thread.currentThread().interrupt()
                is ThreadDeath -> throw t
                else -> Timber.e(t, "Failed to handle format parameters")
            }
        }
    }

    /**
     * Sets the `Format` of the media data to be input for processing in this `Codec`.
     *
     * @param format the `Format` of the media data to be input for processing in this `Codec`
     * @return the `Format` of the media data to be input for processing in this `Codec` if
     * `format` is compatible with this `Codec`; otherwise, `null`
     */
    override fun setInputFormat(format: Format): Format? {
        val setFormat = super.setInputFormat(format)
        if (setFormat != null) reset()
        return setFormat
    }

    /**
     * Sets the `KeyFrameControl` to be used by this `DePacketizer` as a means of
     * control over its key frame-related logic.
     *
     * @param keyFrameControl the `KeyFrameControl` to be used by this `DePacketizer` as a means of
     * control over its key frame-related logic
     */
    fun setKeyFrameControl(keyFrameControl: KeyFrameControl?) {
        this.keyFrameControl = keyFrameControl
    }

    companion object {
        /**
         * The default output `VideoFormat`.
         */
        private val SUPPORTED_OUTPUT_FORMATS: Array<VideoFormat> = arrayOf(AVFrameFormat(FFmpeg.PIX_FMT_YUV420P))
        /**
         * Get plugin name.
         *
         * @return "H.264 Decoder"
         */
        /**
         * Plugin name.
         */
        const val name = "H.264 Decoder"
    }
}