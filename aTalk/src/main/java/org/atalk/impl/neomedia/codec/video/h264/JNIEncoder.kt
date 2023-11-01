/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video.h264

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.NeomediaServiceUtils
import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.device.DeviceConfiguration
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.codec.Constants
import org.atalk.service.neomedia.control.KeyFrameControl
import org.atalk.service.neomedia.control.KeyFrameControl.KeyFrameRequestee
import org.atalk.service.neomedia.event.RTCPFeedbackMessageEvent
import org.atalk.service.neomedia.event.RTCPFeedbackMessageListener
import timber.log.Timber
import java.awt.Dimension
import javax.media.Buffer
import javax.media.Format
import javax.media.PlugIn
import javax.media.ResourceUnavailableException
import javax.media.format.VideoFormat
import javax.media.format.YUVFormat

/**
 * Implements an FMJ H.264 encoder using FFmpeg (and x264).
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class JNIEncoder : AbstractCodec2("H.264 Encoder", VideoFormat::class.java, SUPPORTED_OUTPUT_FORMATS), RTCPFeedbackMessageListener {
    /**
     * The additional settings of this `Codec`.
     */
    private var additionalCodecSettings: Map<String, String>? = null

    /**
     * The codec we will use.
     */
    private var avctx = 0L

    /**
     * The encoded data is stored in avpicture.
     */
    private var avFrame = 0L

    /**
     * The indicator which determines whether the generation of a keyframe is to be forced during
     * a subsequent execution of [.process]. The first frame to undergo
     * encoding is naturally a keyframe and, for the sake of clarity, the initial value is `true`.
     */
    private var forceKeyFrame = true

    /**
     * The `KeyFrameControl` used by this `JNIEncoder` to control its key frame-related logic.
     */
    private var keyFrameControl: KeyFrameControl? = null
    private var keyFrameRequestee: KeyFrameRequestee? = null

    /**
     * The maximum GOP (group of pictures) size i.e. the maximum interval between keyframes (with
     * which [.open] has been invoked without an intervening [.close]). FFmpeg
     * calls it `gop_size`, x264 refers to it as `keyint` or `i_keyint_max`.
     */
    private var keyint = 0

    /**
     * The number of frames processed since the last keyframe.
     */
    private var lastKeyFrame = 0

    /**
     * The time in milliseconds of the last request for a key frame from the remote peer to this local peer.
     */
    private var lastKeyFrameRequestTime = System.currentTimeMillis()

    /**
     * The packetization mode to be used for the H.264 RTP payload output by this `JNIEncoder`,
     * and the associated packetizer. RFC 3984 "RTP Payload Format for H.264 Video" says that
     * "when the value of packetization-mode is equal to 0 or packetization-mode is not present,
     * the single NAL mode, as defined in section 6.2 of RFC 3984, MUST be used."
     */
    private var packetizationMode: String? = null

    /**
     * The raw frame buffer.
     */
    private var rawFrameBuffer = 0L

    /**
     * Length of the raw frame buffer. Once the dimensions are known, this is set to 3/2 *
     * (height*width), which is the size needed for a YUV420 frame.
     */
    private var rawFrameLen = 0

    /**
     * The indicator which determines whether two consecutive frames at the beginning of the video
     * transmission have been encoded as keyframes. The first frame is a keyframe but it is at
     * the very beginning of the video transmission and, consequently, there is a higher risk
     * that pieces of it will be lost on their way through the network. To mitigate possible
     * issues in the case of network loss, the second frame is also a keyframe.
     */
    private var secondKeyFrame = true
    private var mWidth = DeviceConfiguration.DEFAULT_VIDEO_HEIGHT
    private var mHeight = DeviceConfiguration.DEFAULT_VIDEO_WIDTH

    /**
     * Initializes a new `JNIEncoder` instance.
     */
    init {
        inputFormats = arrayOf<Format>(YUVFormat(
                null,  /* size */
                Format.NOT_SPECIFIED,  /* maxDataLength */
                Format.byteArray,
                Format.NOT_SPECIFIED.toFloat(),  /* frameRate */
                YUVFormat.YUV_420,
                Format.NOT_SPECIFIED,  /* strideY */
                Format.NOT_SPECIFIED,  /* strideUV */
                Format.NOT_SPECIFIED,  /* offsetY */
                Format.NOT_SPECIFIED,  /* offsetU */
                Format.NOT_SPECIFIED) /* offsetV */
        )
    }

    /**
     * Closes this `Codec`.
     */
    override fun doClose() {
        if (avctx != 0L) {
            FFmpeg.avcodec_close(avctx)
            FFmpeg.av_free(avctx)
            avctx = 0
        }
        if (avFrame != 0L) {
            FFmpeg.avcodec_free_frame(avFrame)
            avFrame = 0
        }
        if (rawFrameBuffer != 0L) {
            FFmpeg.av_free(rawFrameBuffer)
            rawFrameBuffer = 0
        }
        if (keyFrameRequestee != null) {
            if (keyFrameControl != null) keyFrameControl!!.removeKeyFrameRequestee(keyFrameRequestee!!)
            keyFrameRequestee = null
        }
    }

    /**
     * Opens this `Codec`.
     */
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        /*
         * An Encoder translates raw media data in (en)coded media data.
         * Consequently, the size of the output is equal to the size of the input.
         */
        val ipFormat = inputFormat as VideoFormat?
        val opFormat = outputFormat as VideoFormat?
        var size: Dimension? = null
        if (ipFormat != null) size = ipFormat.size
        if (size == null && opFormat != null) size = opFormat.size

        // Use the default if format size is null
        if (size != null) {
            Timber.d("H264 encode video size: %s", size)
            mWidth = size.width
            mHeight = size.height
        }

        /*
         * XXX We do not currently negotiate the profile so, regardless of the many AVCodecContext
         * properties we have set above, force the default profile configuration.
         */
        val config = LibJitsi.configurationService
        var intraRefresh = DEFAULT_DEFAULT_INTRA_REFRESH
        var keyint = DEFAULT_KEYINT
        var preset = DEFAULT_PRESET
        var profile = DEFAULT_DEFAULT_PROFILE

        intraRefresh = config.getBoolean(DEFAULT_INTRA_REFRESH_PNAME, intraRefresh)
        keyint = config.getInt(KEYINT_PNAME, keyint)
        preset = config.getString(PRESET_PNAME, preset)!!
        profile = config.getString(DEFAULT_PROFILE_PNAME, profile)!!

        if (additionalCodecSettings != null) {
            for ((k, v) in additionalCodecSettings!!) {
                if ("h264.intrarefresh" == k) {
                    if ("false" == v) intraRefresh = false
                } else if ("h264.profile" == k) {
                    if (BASELINE_PROFILE == v || HIGH_PROFILE == v || MAIN_PROFILE == v) profile = v
                }
            }
        }
        val avcodec = FFmpeg.avcodec_find_encoder(FFmpeg.CODEC_ID_H264)
        if (avcodec == 0L) {
            throw ResourceUnavailableException("Could not find H.264 encoder.")
        }
        avctx = FFmpeg.avcodec_alloc_context3(avcodec)
        FFmpeg.avcodeccontext_set_pix_fmt(avctx, FFmpeg.PIX_FMT_YUV420P)
        FFmpeg.avcodeccontext_set_size(avctx, mWidth, mHeight)
        FFmpeg.avcodeccontext_set_qcompress(avctx, 0.6f)
        val bitRate = 1000 * NeomediaServiceUtils.mediaServiceImpl!!.deviceConfiguration.videoBitrate
        var frameRate = Format.NOT_SPECIFIED

        // Allow the outputFormat to request a certain frameRate.
        if (opFormat != null) frameRate = opFormat.frameRate.toInt()
        // Otherwise, output in the frameRate of the inputFormat.
        if (frameRate == Format.NOT_SPECIFIED && ipFormat != null) frameRate = ipFormat.frameRate.toInt()
        if (frameRate == Format.NOT_SPECIFIED) frameRate = DEFAULT_FRAME_RATE

        // average bit rate
        FFmpeg.avcodeccontext_set_bit_rate(avctx, bitRate)
        // so to be 1 in x264
        FFmpeg.avcodeccontext_set_bit_rate_tolerance(avctx, bitRate / frameRate)
        FFmpeg.avcodeccontext_set_rc_max_rate(avctx, bitRate)
        FFmpeg.avcodeccontext_set_sample_aspect_ratio(avctx, 0, 0)
        FFmpeg.avcodeccontext_set_thread_count(avctx, 1)

        // time_base should be 1 / frame rate
        FFmpeg.avcodeccontext_set_time_base(avctx, 1, frameRate)
        FFmpeg.avcodeccontext_set_ticks_per_frame(avctx, 2)
        FFmpeg.avcodeccontext_set_quantizer(avctx, 30, 31, 4)

        // avctx.chromaoffset = -2;
        FFmpeg.avcodeccontext_set_mb_decision(avctx, FFmpeg.FF_MB_DECISION_SIMPLE)
        FFmpeg.avcodeccontext_add_flags(avctx, FFmpeg.CODEC_FLAG_LOOP_FILTER)
        FFmpeg.avcodeccontext_set_me_subpel_quality(avctx, 2)
        FFmpeg.avcodeccontext_set_me_range(avctx, 16)
        FFmpeg.avcodeccontext_set_me_cmp(avctx, FFmpeg.FF_CMP_CHROMA)
        FFmpeg.avcodeccontext_set_scenechange_threshold(avctx, 40)
        FFmpeg.avcodeccontext_set_rc_buffer_size(avctx, 10)
        FFmpeg.avcodeccontext_set_gop_size(avctx, keyint)
        FFmpeg.avcodeccontext_set_i_quant_factor(avctx, 1f / 1.4f)
        FFmpeg.avcodeccontext_set_refs(avctx, 1)
        // FFmpeg.avcodeccontext_set_trellis(avctx, 2);
        FFmpeg.avcodeccontext_set_keyint_min(avctx, X264_KEYINT_MIN_AUTO)
        if (null == packetizationMode || "0" == packetizationMode) {
            FFmpeg.avcodeccontext_set_rtp_payload_size(avctx, Packetizer.MAX_PAYLOAD_SIZE)
        }
        try {
            FFmpeg.avcodeccontext_set_profile(avctx, getProfileForConfig(profile))
        } catch (ule: UnsatisfiedLinkError) {
            Timber.w("The FFmpeg JNI library is out-of-date.")
        }

        /*
         * XXX crf=0 means lossless coding which is not supported by the baseline and main
         * profiles. Consequently, we cannot specify it because we specify either the
         * baseline or the main profile. Otherwise, x264 will detect the inconsistency in the
         * specified parameters/options and FFmpeg will fail.
         */
        if (FFmpeg.avcodec_open2(avctx, avcodec,  // "crf", "0",  /* constant quality mode, constant rate factor */
                        "intra-refresh", if (intraRefresh) "1" else "0",
                        "keyint", keyint.toString(),
                        "partitions", "b8x8,i4x4,p8x8",
                        "preset", preset,
                        "thread_type", "slice",
                        "tune", "zerolatency") < 0) {
            throw ResourceUnavailableException("Could not open H.264 encoder. (size= " + mWidth + "x" + mHeight + ")")
        }
        rawFrameLen = mWidth * mHeight * 3 / 2
        rawFrameBuffer = FFmpeg.av_malloc(rawFrameLen)
        avFrame = FFmpeg.avcodec_alloc_frame()
        // Required to be set for ffmpeg v4.4
        FFmpeg.avframe_set_properties(avFrame, FFmpeg.PIX_FMT_YUV420P, mWidth, mHeight)
        val sizeInBytes = mWidth * mHeight
        FFmpeg.avframe_set_data(avFrame, rawFrameBuffer, sizeInBytes.toLong(), (sizeInBytes / 4).toLong())
        FFmpeg.avframe_set_linesize(avFrame, mWidth, mWidth / 2, mWidth / 2)

        /*
         * In order to be sure keyint will be respected, we will implement it ourselves
         * (regardless of the fact that we have told FFmpeg and x264 about it). Otherwise, we may
         * end up not generating keyframes at all (apart from the two generated after open).
         */
        forceKeyFrame = true
        this.keyint = keyint
        lastKeyFrame = 0

        /*
         * Implement the ability to have the remote peer request key frames from this local peer.
         */
        if (keyFrameRequestee == null) {
            keyFrameRequestee = object : KeyFrameRequestee {
                override fun keyFrameRequest(): Boolean {
                    return this@JNIEncoder.keyFrameRequest()
                }
            }
        }
       if (keyFrameControl != null) keyFrameControl!!.addKeyFrameRequestee(-1, keyFrameRequestee!!)
    }

    /**
     * Processes/encodes a buffer.
     *
     * @param inBuf input buffer
     * @param outBuf output buffer
     * @return `BUFFER_PROCESSED_OK` if buffer has been successfully processed
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        val format = inBuf.format as YUVFormat
        val formatSize = format.size as Dimension
        val width = formatSize.width
        val height = formatSize.height
        if (width > 0 && height > 0 && (width != mWidth || height != mHeight)) {
            Timber.d("H264 encode video size changed: [width=%s, height=%s]=>%s", mWidth, mHeight, formatSize)
            doClose()
            try {
                doOpen()
            } catch (e: ResourceUnavailableException) {
                Timber.e("Could not find H.264 encoder.")
            }
        }
        if (inBuf.length < 10) {
            outBuf.isDiscard = true
            reset()
            return PlugIn.BUFFER_PROCESSED_OK
        }

        // Copy the data of inBuffer into avFrame.
        FFmpeg.memcpy(rawFrameBuffer, inBuf.data as ByteArray, inBuf.offset, rawFrameLen)
        val keyFrame = isKeyFrame
        FFmpeg.avframe_set_key_frame(avFrame, keyFrame)
        /*
         * In order to be sure that keyint will be respected, we will implement it ourselves
         * (regardless of the fact that we have told FFmpeg and x264 about it). Otherwise, we may
         * end up not generating keyframes at all (apart from the two generated after open).
         */
        if (keyFrame) lastKeyFrame = 0 else lastKeyFrame++

        // Encode avFrame into the data of outBuffer.
        val out = validateByteArraySize(outBuf, rawFrameLen, false)
        val outLength = FFmpeg.avcodec_encode_video(avctx, out, out.size, avFrame)
        outBuf.length = outLength
        outBuf.offset = 0
        outBuf.timeStamp = inBuf.timeStamp
        return PlugIn.BUFFER_PROCESSED_OK
    }

    /**
     * Gets the matching output formats for a specific format.
     *
     * @param inputFormat input format
     * @return array for formats matching input format
     */
    override fun getMatchingOutputFormats(inputFormat: Format): Array<Format> {
        val inputVideoFormat = inputFormat as VideoFormat

        val packetizationModes = if (packetizationMode == null) {
            arrayOf("0", "1")
        }
        else {
            arrayOf(packetizationMode)
        }

        val size = inputVideoFormat.size
        val frameRate = inputVideoFormat.frameRate
        val matchingOutputFormats = arrayOfNulls<Format>(packetizationModes.size)

        for (index in packetizationModes.indices.reversed()) {
            matchingOutputFormats[index] = ParameterizedVideoFormat(
                    Constants.H264, size,
                    Format.NOT_SPECIFIED,  /* maxDataLength */
                    Format.byteArray,
                    frameRate,
                    ParameterizedVideoFormat.toMap(VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, packetizationModes[index]!!)
            )
        }
        return matchingOutputFormats as Array<Format>
    }
    /*
     * In order to be sure that keyint will be respected, we will implement it ourselves
     * (regardless of the fact that we have told FFmpeg and x264 about it). Otherwise, we
     * may end up not generating keyframes at all (apart from the two generated after
     * open).
     *//*
     * The first frame is a keyframe, but it is at the very beginning of the video
     * transmission and, consequently, there is a higher risk that pieces of it will be
     * lost on their way through the network. To mitigate possible issues in the case of
     * network loss, the second frame is also a keyframe.
     */

    /**
     * Determines whether the encoding of [.avFrame] is to produce a keyframe. The returned
     * value will be set on `avFrame` via a call to
     * [FFmpeg.avframe_set_key_frame].
     *
     * @return `true` if the encoding of `avFrame` is to produce a keyframe; otherwise `false`
     */
    private val isKeyFrame: Boolean
        get() {
            val keyFrame: Boolean
            if (forceKeyFrame) {
                keyFrame = true

                /*
             * The first frame is a keyframe, but it is at the very beginning of the video
             * transmission and, consequently, there is a higher risk that pieces of it will be
             * lost on their way through the network. To mitigate possible issues in the case of
             * network loss, the second frame is also a keyframe.
             */
                if (secondKeyFrame) {
                    secondKeyFrame = false
                    forceKeyFrame = true
                } else forceKeyFrame = false
            } else {
                /*
             * In order to be sure that keyint will be respected, we will implement it ourselves
             * (regardless of the fact that we have told FFmpeg and x264 about it). Otherwise, we
             * may end up not generating keyframes at all (apart from the two generated after
             * open).
             */
                keyFrame = lastKeyFrame == keyint
            }
            return keyFrame
        }

    /**
     * Notifies this `JNIEncoder` that the remote peer has requested a key frame from this Local peer.
     *
     * @return `true` if this `JNIEncoder` has honored the request for a key frame; otherwise `false`
     */
    private fun keyFrameRequest(): Boolean {
        val now = System.currentTimeMillis()
        if (now > lastKeyFrameRequestTime + PLI_INTERVAL) {
            lastKeyFrameRequestTime = now
            forceKeyFrame = true
        }
        return true
    }

    /**
     * Notifies this `RTCPFeedbackListener` that an RTCP feedback message has been received
     *
     * @param event an `RTCPFeedbackMessageEvent` which specifies the details of the notification
     * event such as the feedback message type and the payload type
     */
    override fun rtcpFeedbackMessageReceived(event: RTCPFeedbackMessageEvent) {
        /*
         * If RTCP message is a Picture Loss Indication (PLI) or a Full Intra-frame Request (FIR)
         * the encoder will force the next frame to be a keyframe.
         */
        if (event.payloadType == RTCPFeedbackMessageEvent.PT_PS) {
            when (event.feedbackMessageType) {
                RTCPFeedbackMessageEvent.FMT_PLI, RTCPFeedbackMessageEvent.FMT_FIR -> {
                    Timber.log(TimberLog.FINER, "Scheduling a key-frame, because we received an RTCP PLI or FIR.")
                    keyFrameRequest()
                }
                else -> {}
            }
        }
    }

    /**
     * Sets additional settings on this `Codec`.
     *
     * @param additionalCodecSettings the additional settings to be set on this `Codec`
     */
    fun setAdditionalCodecSettings(additionalCodecSettings: Map<String, String>?) {
        this.additionalCodecSettings = additionalCodecSettings
    }

    /**
     * Sets the `KeyFrameControl` to be used by this `JNIEncoder` as a means of
     * control over its key frame-related logic.
     *
     * @param keyFrameControl the `KeyFrameControl` to be used by this `JNIEncoder` as a means of
     * control over its key frame-related logic
     */
    fun setKeyFrameControl(keyFrameControl: KeyFrameControl) {
        if (this.keyFrameControl != keyFrameControl) {
            if (this.keyFrameControl != null && keyFrameRequestee != null)
                this.keyFrameControl!!.removeKeyFrameRequestee(keyFrameRequestee!!)

            this.keyFrameControl = keyFrameControl

            if (this.keyFrameControl != null && keyFrameRequestee != null)
                this.keyFrameControl!!.addKeyFrameRequestee(-1, keyFrameRequestee!!)
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
        //  Return null if mismatch output format
        if (format !is VideoFormat || null == matches(format, getMatchingOutputFormats(inputFormat))) return null
        val videoFormat = format
        /*
         * An Encoder translates raw media data in (en)coded media data. Consequently, the size of
         * the output is equal to the size of the input.
         */
        var size: Dimension? = null
        if (inputFormat != null) size = (inputFormat as VideoFormat).size
        if (size == null && format.matches(outputFormat)) size = (outputFormat as VideoFormat).size
        var fmtps: MutableMap<String, String>? = null
        if (format is ParameterizedVideoFormat) fmtps = format.formatParameters
        if (fmtps == null) fmtps = HashMap()
        if (packetizationMode != null) {
            fmtps[VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP] = packetizationMode!!
        }
        outputFormat = ParameterizedVideoFormat(
                videoFormat.encoding,
                size,
                Format.NOT_SPECIFIED,  /* maxDataLength */
                Format.byteArray,
                videoFormat.frameRate,
                fmtps)

        // Return the selected outputFormat
        return outputFormat
    }

    /**
     * Sets the packetization mode to be used for the H.264 RTP payload output by this `JNIEncoder`,
     * and the associated packetizer.
     *
     * @param packetizationMode the packetization mode to be used for the H.264 RTP payload output by this
     * `JNIEncoder` and the associated packetizer
     */
    fun setPacketizationMode(packetizationMode: String?) {
        /*
         * RFC 3984 "RTP Payload Format for H.264 Video", packetization-mode:
         * This parameter signals the properties of an RTP payload type or the capabilities of a receiver implementation.
         * Only a single configuration point can be indicated; thus, when capabilities to support more than one
         * packetization-mode are declared, multiple configuration points (RTP payload types) must be used.
         *
         * The value of packetization mode MUST be an integer in the range of 0 to 2, inclusive.
         * a. When the value of packetization-mode is equal to 0 or packetization-mode is not present,
         *    the single NAL mode, as defined in section 6.2 of RFC 3984, MUST be used.
         * b. When the value of packetization-mode is equal to 1, the non- interleaved mode,
         *    as defined in section 6.3 of RFC 3984, MUST be used.
         * c. When the value of packetization-mode is equal to 2, the interleaved mode,
         *    as defined in section 6.4 of RFC 3984, MUST be used.
         */
        when (packetizationMode) {
            null, "0" -> this.packetizationMode = "0"
            "1" -> this.packetizationMode = "1"
            "2" -> this.packetizationMode = "2"
            else -> throw IllegalArgumentException("packetizationMode")
        }
    }

    companion object {
        /**
         * The available presets we can use with the encoder.
         */
        private val AVAILABLE_PRESETS = arrayOf(
                "ultrafast",
                "superfast",
                "veryfast",
                "faster",
                "fast",
                "medium",
                "slow",
                "slower",
                "veryslow"
        )

        /**
         * The name of the baseline H.264 (encoding) profile.
         */
        const val BASELINE_PROFILE = "baseline"

        /**
         * The default value of the [.DEFAULT_INTRA_REFRESH_PNAME] `ConfigurationService` property.
         */
        const val DEFAULT_DEFAULT_INTRA_REFRESH = true

        /**
         * The name of the main H.264 (encoding) profile.
         */
        const val MAIN_PROFILE = "main"

        /**
         * The default value of the [.DEFAULT_PROFILE_PNAME] `ConfigurationService` property.
         */
        const val DEFAULT_DEFAULT_PROFILE = BASELINE_PROFILE

        /**
         * The frame rate to be assumed by `JNIEncoder` instances in the absence of any other frame rate indication.
         */
        const val DEFAULT_FRAME_RATE = 15

        /**
         * The name of the boolean `ConfigurationService` property which specifies whether Periodic
         * Intra Refresh is to be used by default. The default value is `true`.
         * The value may be overridden by [.setAdditionalCodecSettings].
         */
        const val DEFAULT_INTRA_REFRESH_PNAME = "neomedia.codec.video.h264.defaultIntraRefresh"

        /**
         * The default maximum GOP (group of pictures) size i.e. the maximum interval between
         * keyframes. The x264 library defaults to 250.
         */
        const val DEFAULT_KEYINT = 150

        /**
         * The default value of the [.PRESET_PNAME] `ConfigurationService` property.
         */
        val DEFAULT_PRESET = AVAILABLE_PRESETS[0]

        /**
         * The name of the `ConfigurationService` property which specifies the H.264 (encoding)
         * profile to be used in the absence of negotiation. Though it seems that RFC 3984 "RTP
         * Payload Format for H.264 Video" specifies the baseline profile as the default, we have
         * till the time of this writing defaulted to the main profile and we do not currently want
         * to change from the main to the base profile unless we really have to.
         */
        const val DEFAULT_PROFILE_PNAME = "neomedia.codec.video.h264.defaultProfile"

        /**
         * The name of the high H.264 (encoding) profile.
         */
        const val HIGH_PROFILE = "high"

        /**
         * The name of the integer `ConfigurationService` property which specifies the maximum
         * GOP (group of pictures) size i.e. the maximum interval between keyframes. FFmpeg calls it
         * `gop_size`, x264 refers to it as `keyint` or `i_keyint_max`.
         */
        const val KEYINT_PNAME = "neomedia.codec.video.h264.keyint"

        /**
         * The minimum interval between two PLI request processing (in milliseconds).
         */
        private const val PLI_INTERVAL = 3000L

        /**
         * The name of the `ConfigurationService` property which specifies the x264 preset to
         * be used by `JNIEncoder`. A preset is a collection of x264 options that will provide
         * a certain encoding speed to compression ratio. A slower preset will provide better
         * compression i.e. quality per size.
         */
        const val PRESET_PNAME = "neomedia.codec.video.h264.preset"

        /**
         * The list of `Formats` supported by `JNIEncoder` instances as output.
         */
        val SUPPORTED_OUTPUT_FORMATS = arrayOf<Format>(
                ParameterizedVideoFormat(Constants.H264, VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, "0"),
                ParameterizedVideoFormat(Constants.H264, VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, "1"))

        const val X264_KEYINT_MAX_INFINITE = 1 shl 30
        const val X264_KEYINT_MIN_AUTO = 0

        /**
         * Checks the configuration and returns the profile to use.
         *
         * @param profile the profile setting.
         * @return the profile FFmpeg to use.
         */
        private fun getProfileForConfig(profile: String): Int {
            return when {
                BASELINE_PROFILE.equals(profile, ignoreCase = true) -> FFmpeg.FF_PROFILE_H264_BASELINE
                HIGH_PROFILE.equals(profile, ignoreCase = true) -> FFmpeg.FF_PROFILE_H264_HIGH
                else -> FFmpeg.FF_PROFILE_H264_MAIN
            }
        }
    }
}