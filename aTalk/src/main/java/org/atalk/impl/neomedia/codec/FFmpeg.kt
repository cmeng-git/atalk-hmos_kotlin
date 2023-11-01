/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec

import java.nio.ByteOrder

/**
 * Provides the interface to the native FFmpeg library.
 *
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
object FFmpeg {
    /**
     * No pts value.
     */
    const val AV_NOPTS_VALUE = Long.MIN_VALUE // 0x8000000000000000L;
    const val AV_NUM_DATA_POINTERS = 8

    /**
     * Audio channel masks.
     */
    const val AV_CH_LAYOUT_STEREO = 3
    const val AV_CH_LAYOUT_MONO = 4

    /**
     * The AV sample format for signed 16.
     */
    const val AV_SAMPLE_FMT_S16 = 1

    /**
     * The AV sample format for signed 16 planar.
     */
    const val AV_SAMPLE_FMT_S16P = 6

    /**
     * AC pred flag.
     */
    const val CODEC_FLAG_AC_PRED = 1 shl 24

    /**
     * Loop filter flag.
     */
    const val CODEC_FLAG_LOOP_FILTER = 1 shl 11

    /**
     * The flag which allows incomplete frames to be passed to a decoder.
     */
    const val CODEC_FLAG2_CHUNKS = 1 shl 15

    /**
     * AMR-NB codec ID.
     */
    private const val CODEC_ID_AMR_NB = 0x12000

    /**
     * AMR-WB codec ID
     */
    const val CODEC_ID_AMR_WB = CODEC_ID_AMR_NB + 1

    /**
     * H264 codec ID.
     */
    const val CODEC_ID_H264 = 27

    /**
     * MJPEG codec ID.
     */
    const val CODEC_ID_MJPEG = 7

    /**
     * MP3 codec ID.
     */
    const val CODEC_ID_MP3 = 0x15000 + 1

    /**
     * VP8 codec ID
     */
    const val CODEC_ID_VP8 = 139

    /**
     * VP9 codec ID
     */
    const val CODEC_ID_VP9 = 167

    /**
     * Work around bugs in encoders which sometimes cannot be detected automatically.
     */
    const val FF_BUG_AUTODETECT = 1
    const val FF_CMP_CHROMA = 256

    /**
     * Padding size for FFmpeg input buffer.
     */
    const val FF_INPUT_BUFFER_PADDING_SIZE = 8
    const val FF_MB_DECISION_SIMPLE = 0

    /**
     * The minimum encoding buffer size defined by libavcodec.
     */
    const val FF_MIN_BUFFER_SIZE = 16384

    /**
     * The H264 baseline profile.
     */
    const val FF_PROFILE_H264_BASELINE = 66

    /**
     * The H264 high profile.
     */
    const val FF_PROFILE_H264_HIGH = 100

    /**
     * The H264 main profile.
     */
    const val FF_PROFILE_H264_MAIN = 77

    /**
     * ARGB format.
     */
    const val PIX_FMT_ARGB = 27

    /**
     * BGR24 format as of FFmpeg.
     */
    const val PIX_FMT_BGR24_1 = 3

    /**
     * BGR32 format handled in endian specific manner.
     * It is stored as ABGR on big-endian and RGBA on little-endian.
     */
    var PIX_FMT_BGR32 = 0

    /**
     * BGR32_1 format handled in endian specific manner.
     * It is stored as BGRA on big-endian and ARGB on little-endian.
     */
    var PIX_FMT_BGR32_1 = 0

    /**
     * "NONE" format.
     */
    const val PIX_FMT_NONE = -1

    /**
     * NV12 format.
     */
    const val PIX_FMT_NV12 = 23

    /**
     * RGB24 format handled in endian specific manner.
     * It is stored as RGB on big-endian and BGR on little-endian.
     */
    var PIX_FMT_RGB24 = 0

    /**
     * RGB24 format as of FFmpeg.
     */
    const val PIX_FMT_RGB24_1 = 2

    /**
     * RGB32 format handled in endian specific manner.
     * It is stored as ARGB on big-endian and BGRA on little-endian.
     */
    var PIX_FMT_RGB32 = 0

    /**
     * RGB32_1 format handled in endian specific manner.
     * It is stored as RGBA on big-endian and ABGR on little-endian.
     */
    var PIX_FMT_RGB32_1 = 0

    /**
     * UYVY422 format.
     */
    const val PIX_FMT_UYVY422 = 15

    /**
     * UYYVYY411 format.
     */
    const val PIX_FMT_UYYVYY411 = 16

    /**
     * Y41P format
     */
    const val PIX_FMT_YUV411P = 7

    /**
     * YUV420P format.
     */
    const val PIX_FMT_YUV420P = 0

    /**
     * YUVJ422P format.
     */
    const val PIX_FMT_YUVJ422P = 13

    /**
     * YUYV422 format.
     */
    const val PIX_FMT_YUYV422 = 1

    /**
     * BICUBIC type for libswscale conversion.
     */
    const val SWS_BICUBIC = 4

    // public static final int X264_RC_ABR = 2;
    init {
        System.loadLibrary("jnffmpeg")
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
            PIX_FMT_RGB24 = av_get_pix_fmt("rgb24")
            PIX_FMT_RGB32 = av_get_pix_fmt("argb")
            PIX_FMT_RGB32_1 = av_get_pix_fmt("rgba")
            PIX_FMT_BGR32 = av_get_pix_fmt("abgr")
            PIX_FMT_BGR32_1 = av_get_pix_fmt("bgra")
        } else {
            PIX_FMT_RGB24 = av_get_pix_fmt("bgr24")
            PIX_FMT_RGB32 = av_get_pix_fmt("bgra")
            PIX_FMT_RGB32_1 = av_get_pix_fmt("abgr")
            PIX_FMT_BGR32 = av_get_pix_fmt("rgba")
            PIX_FMT_BGR32_1 = av_get_pix_fmt("argb")
        }
    }

    external fun av_strerror(errnum: Int): String?
    external fun av_get_pix_fmt(name: String?): Int

    /**
     * Free a native pointer allocated by av_malloc.
     *
     * @param ptr native pointer to free
     */
    external fun av_free(ptr: Long)

    /**
     * Allocate memory.
     *
     * @param size size to allocate
     * @return native pointer or 0 if av_malloc failed
     */
    external fun av_malloc(size: Int): Long

    /**
     * Allocates a new `AVCodecContext`.
     *
     * @param codec
     * @return native pointer to the new `AVCodecContext`
     */
    external fun avcodec_alloc_context3(codec: Long): Long

    /**
     * Allocates an `AVFrame` instance and sets its fields to default values. The result must
     * be freed using [.avcodec_free_frame].
     *
     * @return an `AVFrame *` value which points to an `AVFrame` instance filled with
     * default values or `0` on failure
     */
    external fun avcodec_alloc_frame(): Long
    external fun avcodec_alloc_packet(size: Int): Long

    /**
     * Close an AVCodecContext
     *
     * @param ctx pointer to AVCodecContex
     * @return 0 if success, -1 otherwise
     */
    external fun avcodec_close(ctx: Long): Int
    external fun avcodec_decode_audio4(avctx: Long, frame: Long, got_frame: BooleanArray?, avpkt: Long): Int

    /**
     * Decode a video frame.
     *
     * @param ctx codec context
     * @param frame frame decoded
     * @param got_picture if the decoding has produced a valid picture
     * @param buf the input buffer
     * @param buf_size input buffer size
     * @return number of bytes written to buff if success
     */
    external fun avcodec_decode_video(ctx: Long, frame: Long, got_picture: BooleanArray?,
            buf: ByteArray?, buf_size: Int): Int

    /**
     * Decode a video frame.
     *
     * @param ctx codec context
     * @param avframe frame decoded
     * @param src input buffer
     * @param src_length input buffer size
     * @return number of bytes written to buff if success
     */
    external fun avcodec_decode_video(ctx: Long, avframe: Long, src: Long, src_length: Int): Int

    /**
     * Encodes an audio frame from `samples` into `buf`.
     *
     * @param ctx the codec context
     * @param buf the output buffer
     * @param buf_offset the output buffer offset
     * @param buf_size the output buffer size
     * @param samples the input buffer containing the samples. The number of samples read from this buffer
     * is `frame_size`*`channels`, both of which are defined in `ctx`.
     * For PCM audio the number of samples read from samples is equal to `buf_size`*
     * `input_sample_size`/`output_sample_size`.
     * @param samples_offset the offset in the input buffer containing the samples
     * @return on error a negative value is returned, on success zero or the number of bytes used to
     * encode the data read from the input buffer
     */
    external fun avcodec_encode_audio(ctx: Long, buf: ByteArray?, buf_offset: Int,
            buf_size: Int, samples: ByteArray?, samples_offset: Int): Int

    /**
     * Encode a video frame.
     *
     * @param ctx codec context
     * @param buff the output buffer
     * @param buf_size output buffer size
     * @param frame frame to encode
     * @return number of bytes written to buff if success
     */
    external fun avcodec_encode_video(ctx: Long, buff: ByteArray?, buf_size: Int, frame: Long): Int

    /**
     * Find a registered decoder with a matching ID.
     *
     * @param id `CodecID` of the requested encoder
     * @return an `AVCodec` encoder if one was found; `0`, otherwise
     */
    external fun avcodec_find_decoder(id: Int): Long

    /**
     * Finds a registered encoder with a matching codec ID.
     *
     * @param id `CodecID` of the requested encoder
     * @return an `AVCodec` encoder if one was found; `0`, otherwise
     */
    @JvmStatic
    external fun avcodec_find_encoder(id: Int): Long

    /**
     * Frees an `AVFrame` instance specified as an `AVFrame *` value and any
     * dynamically allocated objects in it (e.g. `extended_data`).
     *
     *
     * **Warning**: The method/function does NOT free the data buffers themselves because it does
     * not know how since they might have been allocated with a custom `get_buffer()`.
     *
     *
     * @param frame an `AVFrame *` value which points to the `AVFrame` instance to be freed
     */
    fun avcodec_free_frame(frame: Long) {
        // Invoke the native function avcodec_free_frame(AVFrame **).
        av_free(frame)
    }

    external fun avcodec_free_packet(pkt: Long)

    /**
     * Initializes the specified `AVCodecContext` to use the specified `AVCodec`.
     *
     * @param ctx the `AVCodecContext` which will be set up to use the specified `AVCodec`
     * @param codec the `AVCodec` to use within the `AVCodecContext`
     * @param options
     * @return zero on success, a negative value on error
     */
    external fun avcodec_open2(ctx: Long, codec: Long, vararg options: String?): Int

    /**
     * Add specific flags to AVCodecContext's flags member.
     *
     * @param ctx pointer to AVCodecContext
     * @param flags flags to add
     */
    external fun avcodeccontext_add_flags(ctx: Long, flags: Int)

    /**
     * Add specific flags to AVCodecContext's flags2 member.
     *
     * @param ctx pointer to AVCodecContext
     * @param flags2 flags to add
     */
    external fun avcodeccontext_add_flags2(ctx: Long, flags2: Int)

    /**
     * Gets the samples per packet of the specified `AVCodecContext`. The property is set by
     * libavcodec upon [.avcodec_open].
     *
     * @param ctx the `AVCodecContext` to get the samples per packet of
     * @return the samples per packet of the specified `AVCodecContext`
     */
    external fun avcodeccontext_get_frame_size(ctx: Long): Int

    /**
     * Get height of the video.
     *
     * @param ctx pointer to AVCodecContext
     * @return video height
     */
    external fun avcodeccontext_get_height(ctx: Long): Int

    /**
     * Get pixel format.
     *
     * @param ctx pointer to AVCodecContext
     * @return pixel format
     */
    external fun avcodeccontext_get_pix_fmt(ctx: Long): Int

    /**
     * Get width of the video.
     *
     * @param ctx pointer to AVCodecContext
     * @return video width
     */
    external fun avcodeccontext_get_width(ctx: Long): Int

    /**
     * Set the B-Frame strategy.
     *
     * @param ctx AVCodecContext pointer
     * @param b_frame_strategy strategy
     */
    external fun avcodeccontext_set_b_frame_strategy(ctx: Long, b_frame_strategy: Int)

    /**
     * Sets the average bit rate of the specified `AVCodecContext`. The property is to be set
     * by the user when encoding and is unused for the constant quantizer encoding. It is set by
     * the libavcodec when decoding and its value is `0` or some bitrate if this info is
     * available in the stream.
     *
     * @param ctx the `AVCodecContext` to set the average bit rate of
     * @param bit_rate the average bit rate to be set to the specified `AVCodecContext`
     */
    external fun avcodeccontext_set_bit_rate(ctx: Long, bit_rate: Int)

    /**
     * Set the bit rate tolerance
     *
     * @param ctx the `AVCodecContext` to set the bit rate of
     * @param bit_rate_tolerance bit rate tolerance
     */
    external fun avcodeccontext_set_bit_rate_tolerance(ctx: Long, bit_rate_tolerance: Int)
    //    /**
    //     * Sets the number of channels of the specified <code>AVCodecContext</code>.
    //     * The property is audio only. Deprecated in ffmpeg v5.1.
    //     *
    //     * @param ctx the <code>AVCodecContext</code> to set the number of channels of
    //     * @param channels the number of channels to set to the specified <code>AVCodecContext</code>
    //     */
    //    public static native void avcodeccontext_set_channels(long ctx, int channels);
    //    public static native void avcodeccontext_set_channel_layout(long ctx, int channelLayout);
    /**
     * Set the AVChannelLayout (ch_layout) of the specified `AVCodecContext` for ffmpeg 5.1.
     * The property is audio only: The number of channels is already defined in ch_layout.
     * Hence avcodeccontext_set_nb_channels in (ch_layout.nb_channels is not required.
     *
     * @param ctx the `AVCodecContext` to set the number of channels of
     * @param ch_Layout the AVChannelLayout
     */
    external fun avcodeccontext_set_ch_layout(ctx: Long, ch_Layout: Int)
    external fun avcodeccontext_set_nb_channels(ctx: Long, nb_channels: Int)
    external fun avcodeccontext_set_chromaoffset(ctx: Long, chromaoffset: Int)

    /**
     * Sets the maximum number of pictures in a group of pictures i.e. the maximum interval between
     * keyframes.
     *
     * @param ctx the `AVCodecContext` to set the `gop_size` of
     * @param gop_size the maximum number of pictures in a group of pictures i.e. the maximum interval between keyframes
     */
    external fun avcodeccontext_set_gop_size(ctx: Long, gop_size: Int)
    external fun avcodeccontext_set_i_quant_factor(ctx: Long, i_quant_factor: Float)

    /**
     * Sets the minimum GOP size.
     *
     * @param ctx the `AVCodecContext` to set the minimum GOP size of
     * @param keyint_min the minimum GOP size to set on `ctx`
     */
    external fun avcodeccontext_set_keyint_min(ctx: Long, keyint_min: Int)

    /**
     * Set the maximum B frames.
     *
     * @param ctx the `AVCodecContext` to set the maximum B frames of
     * @param max_b_frames maximum B frames
     */
    external fun avcodeccontext_set_max_b_frames(ctx: Long, max_b_frames: Int)
    external fun avcodeccontext_set_mb_decision(ctx: Long, mb_decision: Int)
    external fun avcodeccontext_set_me_cmp(ctx: Long, me_cmp: Int)
    external fun avcodeccontext_set_me_method(ctx: Long, me_method: Int)
    external fun avcodeccontext_set_me_range(ctx: Long, me_range: Int)
    external fun avcodeccontext_set_me_subpel_quality(ctx: Long, me_subpel_quality: Int)

    /**
     * Set the pixel format.
     *
     * @param ctx the `AVCodecContext` to set the pixel format of
     * @param pix_fmt pixel format
     */
    external fun avcodeccontext_set_pix_fmt(ctx: Long, pix_fmt: Int)
    external fun avcodeccontext_set_profile(ctx: Long, profile: Int)
    external fun avcodeccontext_set_qcompress(ctx: Long, qcompress: Float)
    external fun avcodeccontext_set_quantizer(ctx: Long, qmin: Int, qmax: Int, max_qdiff: Int)
    external fun avcodeccontext_set_rc_buffer_size(ctx: Long, rc_buffer_size: Int)
    external fun avcodeccontext_set_rc_eq(ctx: Long, rc_eq: String?)
    external fun avcodeccontext_set_rc_max_rate(ctx: Long, rc_max_rate: Int)
    external fun avcodeccontext_set_refs(ctx: Long, refs: Int)

    /**
     * Set the RTP payload size.
     *
     * @param ctx the `AVCodecContext` to set the RTP payload size of
     * @param rtp_payload_size RTP payload size
     */
    external fun avcodeccontext_set_rtp_payload_size(ctx: Long, rtp_payload_size: Int)
    external fun avcodeccontext_set_sample_aspect_ratio(ctx: Long, num: Int, den: Int)
    external fun avcodeccontext_set_sample_fmt(ctx: Long, sample_fmt: Int)

    /**
     * Sets the samples per second of the specified `AVCodecContext`. The property is audio
     * only.
     *
     * @param ctx the `AVCodecContext` to set the samples per second of
     * @param sample_rate the samples per second to set to the specified `AVCodecContext`
     */
    external fun avcodeccontext_set_sample_rate(ctx: Long, sample_rate: Int)

    /**
     * Set the scene change threshold (in percent).
     *
     * @param ctx AVCodecContext pointer
     * @param scenechange_threshold value between 0 and 100
     */
    external fun avcodeccontext_set_scenechange_threshold(ctx: Long, scenechange_threshold: Int)

    /**
     * Set the size of the video.
     *
     * @param ctx pointer to AVCodecContext
     * @param width video width
     * @param height video height
     */
    external fun avcodeccontext_set_size(ctx: Long, width: Int, height: Int)

    /**
     * Set the number of thread.
     *
     * @param ctx the `AVCodecContext` to set the number of thread of
     * @param thread_count number of thread to set
     */
    external fun avcodeccontext_set_thread_count(ctx: Long, thread_count: Int)
    external fun avcodeccontext_set_ticks_per_frame(ctx: Long, ticks_per_frame: Int)
    external fun avcodeccontext_set_time_base(ctx: Long, num: Int, den: Int)
    external fun avcodeccontext_set_trellis(ctx: Long, trellis: Int)
    external fun avcodeccontext_set_workaround_bugs(ctx: Long, workaround_bugs: Int)

    /**
     * Allocates a new `AVFilterGraph` instance.
     *
     * @return a pointer to the newly-allocated `AVFilterGraph` instance
     */
    external fun avfilter_graph_alloc(): Long

    /**
     * Checks the validity and configures all the links and formats in a specific
     * `AVFilterGraph` instance.
     *
     * @param graph a pointer to the `AVFilterGraph` instance to check the validity of and
     * configure
     * @param log_ctx the `AVClass` context to be used for logging
     * @return `0` on success; a negative `AVERROR` on error
     */
    external fun avfilter_graph_config(graph: Long, log_ctx: Long): Int

    /**
     * Frees a specific `AVFilterGraph` instance and destroys its links.
     *
     * @param graph a pointer to the `AVFilterGraph` instance to free
     */
    external fun avfilter_graph_free(graph: Long)

    /**
     * Gets a pointer to an `AVFilterContext` instance with a specific name in a specific
     * `AVFilterGraph` instance.
     *
     * @param graph a pointer to the `AVFilterGraph` instance where the `AVFilterContext`
     * instance with the specified name is to be found
     * @param name the name of the `AVFilterContext` instance which is to be found in the
     * specified `graph`
     * @return the filter graph pointer
     */
    external fun avfilter_graph_get_filter(graph: Long, name: String?): Long

    /**
     * Adds a filter graph described by a `String` to a specific `AVFilterGraph` instance.
     *
     * @param graph a pointer to the `AVFilterGraph` instance where to link the parsed graph context
     * @param filters the `String` to be parsed
     * @param inputs a pointer to a linked list to the inputs of the graph if any; otherwise, `0`
     * @param outputs a pointer to a linked list to the outputs of the graph if any; otherwise, `0`
     * @param log_ctx the `AVClass` context to be used for logging
     * @return `0` on success; a negative `AVERROR` on error
     */
    external fun avfilter_graph_parse(graph: Long, filters: String?, inputs: Long,
            outputs: Long, log_ctx: Long): Int

    /**
     * Initializes the `libavfilter` system and registers all built-in filters.
     */
    external fun avframe_get_data0(frame: Long): Long
    external fun avframe_get_linesize0(frame: Long): Int
    external fun avframe_get_pts(frame: Long): Long
    external fun avframe_set_properties(frame: Long, format: Int, width: Int, height: Int)
    external fun avframe_set_data(frame: Long, data0: Long, offset1: Long, offset2: Long)
    external fun avframe_set_key_frame(frame: Long, key_frame: Boolean)
    external fun avframe_set_linesize(frame: Long, linesize0: Int, linesize1: Int, linesize2: Int)
    external fun avpacket_set_data(avpkt: Long, `in`: ByteArray?, inOff: Int, inLen: Int)
    external fun avpicture_fill(picture: Long, ptr: Long, pix_fmt: Int, width: Int, height: Int): Int
    external fun get_filtered_video_frame(input: Long, width: Int, height: Int,
            pixFmt: Int, buffer: Long, ffsink: Long, output: Long): Long

    external fun memcpy(dst: ByteArray?, dst_offset: Int, dst_length: Int, src: Long)
    external fun memcpy(dst: IntArray?, dst_offset: Int, dst_length: Int, src: Long)
    external fun memcpy(dst: Long, src: ByteArray?, src_offset: Int, src_length: Int)

    /**
     * Get BGR32 pixel format.
     *
     * @return BGR32 pixel format
     */
    private external fun PIX_FMT_BGR32(): Int

    /**
     * Get BGR32_1 pixel format.
     *
     * @return BGR32_1 pixel format
     */
    private external fun PIX_FMT_BGR32_1(): Int

    /**
     * Get RGB24 pixel format.
     *
     * @return RGB24 pixel format
     */
    private external fun PIX_FMT_RGB24(): Int

    /**
     * Get RGB32 pixel format.
     *
     * @return RGB32 pixel format
     */
    private external fun PIX_FMT_RGB32(): Int

    /**
     * Get RGB32_1 pixel format.
     *
     * @return RGB32_1 pixel format
     */
    private external fun PIX_FMT_RGB32_1(): Int

    /**
     * Free an SwsContext.
     *
     * @param ctx SwsContext native pointer
     */
    external fun sws_freeContext(ctx: Long)

    /**
     * Get a SwsContext pointer.
     *
     * @param ctx SwsContext
     * @param srcW width of source image
     * @param srcH height of source image
     * @param srcFormat image format
     * @param dstW width of destination image
     * @param dstH height destination image
     * @param dstFormat destination format
     * @param flags flags
     * @return cached SwsContext pointer
     */
    external fun sws_getCachedContext(ctx: Long, srcW: Int, srcH: Int, srcFormat: Int,
            dstW: Int, dstH: Int, dstFormat: Int, flags: Int): Long

    /**
     * Scale an image.
     *
     * @param ctx SwsContext native pointer
     * @param src source image (native pointer)
     * @param srcSliceY slice Y of source image
     * @param srcSliceH slice H of source image
     * @param dst destination image (java type)
     * @param dstFormat destination format
     * @param dstW width of destination image
     * @param dstH height destination image
     * @return 0 if success, -1 otherwise
     */
    external fun sws_scale(ctx: Long, src: Long, srcSliceY: Int, srcSliceH: Int,
            dst: Any?, dstFormat: Int, dstW: Int, dstH: Int): Int

    /**
     * Scale image an image.
     *
     * @param ctx SwsContext native pointer
     * @param src source image (java type)
     * @param srcFormat image format
     * @param srcW width of source image
     * @param srcH height of source image
     * @param srcSliceY slice Y of source image
     * @param srcSliceH slice H of source image
     * @param dst destination image (java type)
     * @param dstFormat destination format
     * @param dstW width of destination image
     * @param dstH height destination image
     * @return 0 if success, -1 otherwise
     */
    external fun sws_scale(ctx: Long, src: Any?, srcFormat: Int, srcW: Int, srcH: Int,
            srcSliceY: Int, srcSliceH: Int, dst: Any?, dstFormat: Int, dstW: Int, dstH: Int): Int
}