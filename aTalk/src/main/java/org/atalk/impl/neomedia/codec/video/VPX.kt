/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video

/**
 * A wrapper for the libvpx native library.
 * See []//www.webmproject.org/docs/"">&quot;http://www.webmproject.org/docs/&quot;
 *
 * @author Boris Grozev
 */
object VPX {
    /**
     * Operation completed without error.
     * Corresponds to `VPX_CODEC_OK` from `vpx/vpx_codec.h`
     */
    const val CODEC_OK = 0

    /**
     * An iterator reached the end of list.
     * Corresponds to `VPX_CODEC_LIST_END` from `vpx/vpx_codec.h`
     */
    const val CODEC_LIST_END = 9

    /**
     * Use external Memory Allocation mode flag
     * Corresponds to `VPX_CODEC_USE_XMA` from `vpx/vpx_codec.h`
     */
    const val CODEC_USE_XMA = 0x00000001

    /**
     * Output one partition at a time. Each partition is returned in its own `VPX_CODEC_CX_FRAME_PKT`.
     */
    const val CODEC_USE_OUTPUT_PARTITION = 0x20000

    /**
     * Improve resiliency against losses of whole frames.
     *
     * To set this option for an encoder, enable this bit in the value passed
     * to `vpx_enc_cft_set_error_resilient` for the encoder's configuration.
     *
     * Corresponds to `VPX_ERROR_RESILIENT_DEFAULT` from `vpx/vpx_encoder.h`
     */
    const val ERROR_RESILIENT_DEFAULT = 0x01

    /**
     * The frame partitions are independently decodable by the bool decoder, meaning that
     * partitions can be decoded even though earlier partitions have been lost. Note that intra
     * prediction is still done over the partition boundary.
     *
     * To set this option for encoder, enable this bit in the value passed
     * to `vpx_enc_cft_set_error_resilient` for the encoder's configuration.
     *
     * Corresponds to `VPX_ERROR_RESILIENT_PARTITIONS` from `vpx/vpx_encoder.h`
     */
    const val ERROR_RESILIENT_PARTITIONS = 0x02

    /**
     * I420 format constant
     * Corresponds to `VPX_IMG_FMT_I420` from `vpx/vpx_image.h`
     */
    /* See vpx_image.h
        define VPX_IMG_FMT_PLANAR 0x100  Image is a planar format.

        brief List of supported image formats
        VPX_IMG_FMT_I420 = VPX_IMG_FMT_PLANAR | 2,
        VPX_IMG_FMT_I422 = VPX_IMG_FMT_PLANAR | 5,
        VPX_IMG_FMT_I444 = VPX_IMG_FMT_PLANAR | 6,
        VPX_IMG_FMT_I440 = VPX_IMG_FMT_PLANAR | 7,
     */
    const val IMG_FMT_I420 = 258
    const val IMG_FMT_I422 = 261
    const val IMG_FMT_I444 = 262
    const val IMG_FMT_I440 = 263

    /**
     * Variable Bitrate mode.
     * Corresponds to `VPX_VBR` from `vpx/vpx_encoder.h`
     */
    const val RC_MODE_VBR = 0

    /**
     * Constant Bitrate mode.
     * Corresponds to `VPX_CBR` from `vpx/vpx_encoder.h`
     */
    const val RC_MODE_CBR = 1

    /**
     * Constant Quality mode.
     * Corresponds to `VPX_CQ` from `vpx/vpx_encoder.h`
     */
    const val RC_MODE_CQ = 2

    /**
     * Encoder determines optimal placement automatically.
     * Corresponds to `VPX_KF_AUTO` from in `vpx/vpx_encoder.h`
     */
    const val KF_MODE_AUTO = 1

    /**
     * Encoder does not place keyframes.
     * Corresponds to `VPX_KF_DISABLED` from `vpx/vpx_encoder.h`
     */
    const val KF_MODE_DISABLED = 1

    /**
     * Force this frame to be a keyframe
     * Corresponds to `VPX_EFLAG_FORCE_KF` from `vpx/vpx_encoder.h`
     */
    const val EFLAG_FORCE_KF = 1 shl 0

    /**
     * Process and return as soon as possible ('realtime' deadline)
     * Corresponds to `VPX_DL_REALTIME` from `vpx/vpx_encoder.h`
     */
    const val DL_REALTIME = 1
    const val DL_GOOD_QUALITY = 1000000
    const val DL_BEST_QUALITY = 0

    /**
     * Compressed video frame packet type.
     * Corresponds to `VPX_CODEC_CX_FRAME_PKT` from `vpx/vpx_encoder.h`
     */
    const val CODEC_CX_FRAME_PKT = 0

    /**
     * brief Codec control function to set encoder internal speed settings.
     *
     * Changes in this value influences, among others, the encoder's selection
     * of motion estimation methods. Values greater than 0 will increase encoder
     * speed at the expense of quality.
     *
     * \note Valid range for VP8: -16..16
     * \note Valid range for VP9: -9..9
     *
     * Supported in codecs: VP8, VP9
     */
    const val VP8E_SET_CPUUSED = 13
    const val VP9E_SET_LOSSLESS = 32
    const val VP9E_SET_POSTENCODE_DROP = 65

    /**
     * Constant for VP8 decoder interface
     */
    const val INTERFACE_VP8_DEC = 0

    /**
     * Constant for VP8 encoder interface
     */
    const val INTERFACE_VP8_ENC = 1

    /**
     * Constant for VP9 decoder interface
     */
    const val INTERFACE_VP9_DEC = 2

    /**
     * Constant for VP9 encoder interface
     */
    const val INTERFACE_VP9_ENC = 3

    /**
     * Allocates memory for a `vpx_codec_ctx_t` on the heap.
     *
     * @return A pointer to the allocated memory.
     */
    external fun codec_ctx_malloc(): Long

    /**
     * Initializes a vpx decoder context.
     *
     * @param context Pointer to a pre-allocated `vpx_codec_ctx_t`.
     * @param iface Interface to be used. Has to be one of the `VPX.INTERFACE_*` constants.
     * @param cfg Pointer to a pre-allocated `vpx_codec_dec_cfg_t`, may be 0.
     * @param flags Flags.
     * @return `CODEC_OK` on success, or an error code otherwise. The error code can be
     * converted to a `String` with [VPX.codec_err_to_string]
     */
    external fun codec_dec_init(context: Long, iface: Int, cfg: Long, flags: Long): Int

    /**
     * Decodes the frame in `buf`, at offset `buf_offset`.
     *
     * @param context The context to use.
     * @param buf Encoded frame buffer.
     * @param buf_offset Offset into `buf` where the encoded frame begins.
     * @param buf_size Size of the encoded frame.
     * @param user_priv Application specific data to associate with this frame.
     * @param deadline Soft deadline the decoder should attempt to meet,
     * in microseconds. Set to zero for unlimited.
     * @return `CODEC_OK` on success, or an error code otherwise. The
     * error code can be converted to a `String` with [VPX.codec_err_to_string]
     */
    external fun codec_decode(context: Long,
            buf: ByteArray?,
            buf_offset: Int,
            buf_size: Int,
            user_priv: Long,
            deadline: Long): Int

    /**
     * Gets the next frame available to display from the decoder context `context`.
     * The list of available frames becomes valid upon completion of the `codec_decode`
     * call, and remains valid until the next call to `codec_decode`.
     *
     * @param context The decoder context to use.
     * @param iter Iterator storage, initialized by setting its first element to 0.
     * @return Pointer to a `vpx_image_t` describing the decoded frame, or 0 if no more frames are available
     */
    external fun codec_get_frame(context: Long, iter: LongArray?): Long

    /**
     * Destroys a codec context, freeing any associated memory buffers.
     *
     * @param context Pointer to the `vpx_codec_ctx_t` context to destroy.
     * @return `CODEC_OK` on success, or an error code otherwise. The error code can be
     * converted to a `String` with [VPX.codec_err_to_string]
     */
    external fun codec_destroy(context: Long): Int

    /**
     * Initializes a vpx encoder context.
     *
     * @param context Pointer to a pre-allocated `vpx_codec_ctx_t`.
     * @param iface Interface to be used. Has to be one of the `VPX.INTERFACE_*` constants.
     * @param cfg Pointer to a pre-allocated `vpx_codec_enc_cfg_t`, may be 0.
     * @param flags Flags.
     * @return `CODEC_OK` on success, or an error code otherwise. The error code can be
     * converted to a `String` with [VPX.codec_err_to_string]
     */
    external fun codec_enc_init(context: Long, iface: Int, cfg: Long, flags: Long): Int

    /**
     * @param context Pointer to the codec context on which to set the control
     * @param ctrl_id control parameter to set.
     * @param arg arg to set to.
     *
     * @return `CODEC_OK` on success, or an error code otherwise. The error code can be
     * converted to a `String` with [VPX.codec_err_to_string]
     */
    external fun codec_control(context: Long, ctrl_id: Int, arg: Int): Int

    /**
     * @param context Pointer to the codec context on which to set the configuration
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t` to set.
     * @return `CODEC_OK` on success, or an error code otherwise. The error code can be
     * converted to a `String` with [VPX.codec_err_to_string]
     */
    external fun codec_enc_config_set(context: Long, cfg: Long): Int

    /**
     * Encodes the frame described by `img`, `buf`,
     * `offset0`, `offset1` and `offset2`.
     *
     * Note that `buf` and the offsets describe where the frames is stored, but
     * `img` has to have all of its other parameters (format, dimensions, strides) already set.
     *
     * The reason `buf` and the offsets are treated differently is to allow for the
     * encoder to operate on java memory and avoid copying the raw frame to native memory.
     *
     * @param context Pointer to the codec context to use.
     * @param img Pointer to a `vpx_image_t` describing the raw frame
     * @param buf Contains the raw frame
     * @param offset0 Offset of the first plane
     * @param offset1 Offset of the second plane
     * @param offset2 Offset of the third plane
     * @param pts Presentation time stamp, in timebase units.
     * @param duration Duration to show frame, in timebase units.
     * @param flags Flags to use for encoding this frame.
     * @param deadline Time to spend encoding, in microseconds. (0=infinite)
     * @return `CODEC_OK` on success, or an error code otherwise. The error code can be
     * converted to a `String` with [VPX.codec_err_to_string]
     */
    external fun codec_encode(context: Long, img: Long, buf: ByteArray?, offset0: Int,
            offset1: Int, offset2: Int, pts: Long, duration: Long, flags: Long, deadline: Long): Int
    // public static native long codec_encode_frame(long context, long img, byte[] buf, int offset0,
    //       int offset1, int offset2, long pts, long duration, long flags, long deadline, long[] iter);
    /**
     * Encoded data iterator.
     * Iterates over a list of data packets to be passed from the encoder to the application. The
     * kind of a packet can be determined using [VPX.codec_cx_pkt_get_kind]
     * Packets of kind `CODEC_CX_FRAME_PKT` should be passed to the application's muxer.
     *
     * @param context The codec context to use.
     * @param iter Iterator storage, initialized by setting its first element to 0.
     * @return Pointer to a vpx_codec_cx_pkt_t containing the output data
     * packet, or 0 to indicate the end of available packets
     */
    external fun codec_get_cx_data(context: Long, iter: LongArray?): Long

    /**
     * Returns the `kind` of the `vpx_codec_cx_pkt_t` pointed to by `pkt`.
     *
     * @param pkt Pointer to the `vpx_codec_cx_pkt_t` to return the `kind` of.
     * @return The kind of `pkt`.
     */
    external fun codec_cx_pkt_get_kind(pkt: Long): Int

    /**
     * Returns the size of the data in the `vpx_codec_cx_pkt_t` pointed
     * to by `pkt`. Can only be used for packets of `kind` `CODEC_CX_FRAME_PKT`.
     *
     * @param pkt Pointer to a `vpx_codec_cx_pkt_t`.
     * @return The size of the data of `pkt`.
     */
    external fun codec_cx_pkt_get_size(pkt: Long): Int

    /**
     * Returns a pointer to the data in the `vpx_codec_cx_pkt_t` pointed to by`pkt`.
     * Can only be used for packets of `kind` `CODEC_CX_FRAME_PKT`.
     *
     * @param pkt Pointer to the `vpx_codec_cx_pkt_t`.
     * @return Pointer to the data of `pkt`.
     */
    external fun codec_cx_pkt_get_data(pkt: Long): Long
    //============ img ============
    /**
     * Allocates memory for a `vpx_image_t` on the heap.
     *
     * @return A pointer to the allocated memory.
     */
    external fun img_malloc(): Long
    external fun img_alloc(img: Long, img_fmt: Int, frame_width: Int, frame_height: Int, align: Int): Long

    /**
     * Returns the value of the `w` (width) field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @return The `w` (width) field of `img`.
     */
    external fun img_get_w(img: Long): Int

    /**
     * Returns the value of the `h` (height) field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @return The `h` (height) field of `img`.
     */
    external fun img_get_h(img: Long): Int

    /**
     * Returns the value of the `d_w` (displayed width) field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @return The `d_w` (displayed width) field of `img`.
     */
    external fun img_get_d_w(img: Long): Int

    /**
     * Returns the value of the `d_h` (displayed height) field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @return The `d_h` (displayed height) field of `img`.
     */
    external fun img_get_d_h(img: Long): Int

    /**
     * Returns the value of the `planes[0]` field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @return The `planes[0]` field of `img`.
     */
    external fun img_get_plane0(img: Long): Long

    /**
     * Returns the value of the `planes[1]` field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @return The `planes[1]` field of `img`.
     */
    external fun img_get_plane1(img: Long): Long

    /**
     * Returns the value of the `planes[2]` field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @return The `planes[2]` field of `img`.
     */
    external fun img_get_plane2(img: Long): Long

    /**
     * Returns the value of the `stride[0]` field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @return The `stride[0]` field of `img`.
     */
    external fun img_get_stride0(img: Long): Int

    /**
     * Returns the value of the `stride[1]` field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @return The `stride[1]` field of `img`.
     */
    external fun img_get_stride1(img: Long): Int

    /**
     * Returns the value of the `stride[2]` field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @return The `stride[2]` field of `img`.
     */
    external fun img_get_stride2(img: Long): Int

    /**
     * Returns the value of the `fmt` field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @return The `fmt` field of `img`.
     */
    external fun img_get_fmt(img: Long): Int

    /**
     * Sets the `w` (width) field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @param value The value to set.
     */
    external fun img_set_w(img: Long, value: Int)

    /**
     * Sets the `h` (height) field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @param value The value to set.
     */
    external fun img_set_h(img: Long, value: Int)

    /**
     * Sets the `d_w` (displayed width) field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @param value The value to set.
     */
    external fun img_set_d_w(img: Long, value: Int)

    /**
     * Sets the `d_h` (displayed height) field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @param value The value to set.
     */
    external fun img_set_d_h(img: Long, value: Int)

    /**
     * Sets the `stride[0]` field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @param value The value to set.
     */
    external fun img_set_stride0(img: Long, value: Int)

    /**
     * Sets the `stride[1]` field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @param value The value to set.
     */
    external fun img_set_stride1(img: Long, value: Int)

    /**
     * Sets the `stride[2]` field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @param value The value to set.
     */
    external fun img_set_stride2(img: Long, value: Int)

    /**
     * Sets the `stride[3]` field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @param value The value to set.
     */
    external fun img_set_stride3(img: Long, value: Int)

    /**
     * Sets the `fmt` (format) field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @param value The value to set.
     */
    external fun img_set_fmt(img: Long, value: Int)

    /**
     * Sets the `bps` (bits per sample) field of a `vpx_image_t`.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @param value The value to set.
     */
    external fun img_set_bps(img: Long, value: Int)

    /**
     * Open a descriptor, using existing storage for the underlying image.
     *
     * Returns a descriptor for storing an image of the given format. The storage for descriptor
     * has been allocated elsewhere, and a descriptor is desired to "wrap" that storage.
     *
     * @param img Pointer to a `vpx_image_t`.
     * @param fmt Format of the image.
     * @param d_w Width of the image.
     * @param d_h Height of the image.
     * @param align Alignment, in bytes, of each row in the image.
     * @param data Storage to use for the image
     */
    external fun img_wrap(img: Long, fmt: Int, d_w: Int, d_h: Int, align: Int, data: Long)

    /**
     * Allocates memory for a `vpx_codec_dec_cfg_t` on the heap.
     *
     * @return A pointer to the allocated memory.
     */
    external fun codec_dec_cfg_malloc(): Long

    /**
     * Sets the `w` field of a `vpx_codec_dec_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_dec_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_dec_cfg_set_w(cfg: Long, value: Int)

    /**
     * Sets the `h` field of a `vpx_codec_dec_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_dec_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_dec_cfg_set_h(cfg: Long, value: Int)

    /**
     * Allocates memory for a `vpx_codec_enc_cfg_t` on the heap.
     *
     * @return A pointer to the allocated memory.
     */
    external fun codec_enc_cfg_malloc(): Long

    /**
     * Initializes a encoder configuration structure with default values.
     *
     * @param iface Interface. Should be one of the `INTERFACE_*` constants
     * @param cfg Pointer to the vpx_codec_enc_cfg_t to initialize
     * @param usage End usage. Set to 0 or use codec specific values.
     * @return `CODEC_OK` on success, or an error code otherwise. The
     * error code can be converted to a `String` with
     * [VPX.codec_err_to_string]
     */
    external fun codec_enc_config_default(iface: Int, cfg: Long, usage: Int): Int

    /**
     * Sets the `g_profile` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_profile(cfg: Long, value: Int)

    /**
     * Sets the `g_threads` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_threads(cfg: Long, value: Int)

    /**
     * Sets the `g_w` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_w(cfg: Long, value: Int)

    /**
     * Sets the `g_h` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_h(cfg: Long, value: Int)
    external fun codec_enc_cfg_set_tbnum(cfg: Long, value: Int)
    external fun codec_enc_cfg_set_tbden(cfg: Long, value: Int)

    /**
     * Sets the `g_error_resilient` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_error_resilient(cfg: Long, value: Int)

    /**
     * Sets the `g_lag_in_frames` field of a `vpx_codec_enc_cfg_t`.
     * https://chromium.googlesource.com/webm/libvpx/+/refs/tags/v1.10.0/vpx/vpx_encoder.h#362
     * Set to allow lagged encoding
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_lag_in_frames(cfg: Long, value: Int)

    /**
     * Sets the `rc_target_bitrate` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_rc_target_bitrate(cfg: Long, value: Int)

    /**
     * Sets the `rc_dropframe_thresh` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_rc_dropframe_thresh(cfg: Long, value: Int)

    /**
     * Sets the `rc_resize_allowed` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_rc_resize_allowed(cfg: Long, value: Int)

    /**
     * Sets the `rc_resize_up_thresh` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_rc_resize_up_thresh(cfg: Long, value: Int)

    /**
     * Sets the `rc_resize_down_thresh` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_rc_resize_down_thresh(cfg: Long, value: Int)

    /**
     * Sets the `rc_end_usage` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_rc_end_usage(cfg: Long, value: Int)

    /**
     * Sets the `rc_min_quantizer` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_rc_min_quantizer(cfg: Long, value: Int)

    /**
     * Sets the `rc_max_quantizer` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_rc_max_quantizer(cfg: Long, value: Int)

    /**
     * Sets the `rc_undershoot_pct` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_rc_undershoot_pct(cfg: Long, value: Int)

    /**
     * Sets the `rc_overshoot_pct` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_rc_overshoot_pct(cfg: Long, value: Int)

    /**
     * Sets the `rc_buf_sz` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_rc_buf_sz(cfg: Long, value: Int)

    /**
     * Sets the `rc_buf_initial_sz` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_rc_buf_initial_sz(cfg: Long, value: Int)

    /**
     * Sets the `rc_buf_optimal_sz` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_rc_buf_optimal_sz(cfg: Long, value: Int)

    /**
     * Sets the `kf_mode` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_kf_mode(cfg: Long, value: Int)

    /**
     * Sets the `kf_min_dist` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_kf_min_dist(cfg: Long, value: Int)

    /**
     * Sets the `kf_max_dist` field of a `vpx_codec_enc_cfg_t`.
     *
     * @param cfg Pointer to a `vpx_codec_enc_cfg_t`.
     * @param value The value to set.
     */
    external fun codec_enc_cfg_set_kf_max_dist(cfg: Long, value: Int)

    /**
     * Allocates memory for a `vpx_codec_stream_info_t` on the heap.
     *
     * @return A pointer to the allocated memory.
     */
    external fun stream_info_malloc(): Long

    /**
     * Returns the `w` field of a `vpx_codec_stream_info_t`.
     *
     * @param stream_info Pointer to a `vpx_codec_stream_info_t`.
     * @return The `w` field of a `stream_info`.
     */
    external fun stream_info_get_w(stream_info: Long): Int

    /**
     * Returns the `h` field of a `vpx_codec_stream_info_t`.
     *
     * @param stream_info Pointer to a `vpx_codec_stream_info_t`.
     * @return The `h` field of a `stream_info`.
     */
    external fun stream_info_get_h(stream_info: Long): Int

    /**
     * Returns the `is_kf` field of a `vpx_codec_stream_info_t`.
     *
     * @param stream_info Pointer to a `vpx_codec_stream_info_t`.
     * @return The `w` field of a `stream_info`.
     */
    external fun stream_info_get_is_kf(stream_info: Long): Int

    /**
     * Performs high level parsing of the bitstream. Construction of a decoder
     * context is not necessary. Can be used to determine if the bitstream is
     * of the proper format, and to extract information from the stream.
     *
     * @param iface Interface, should be one of the `INTERFACE_*` constants.
     * @param buf Buffer containing a compressed frame.
     * @param buf_offset Offset into `buf` where the compressed frame begins.
     * @param buf_size Size of the compressed frame.
     * @param si_ptr Pointer to a `vpx_codec_stream_info_t` which will
     * be filled with information about the compressed frame.
     * @return `CODEC_OK` on success, or an error code otherwise. The error code can be
     * converted to a `String` with [VPX.codec_err_to_string]
     */
    external fun codec_peek_stream_info(iface: Int,
            buf: ByteArray?,
            buf_offset: Int,
            buf_size: Int,
            si_ptr: Long): Int

    /**
     * Allocates memory on the heap (a simple wrapped around the native `malloc()`)
     *
     * @param s Number of bytes to allocate
     * @return Pointer to the memory allocated.
     */
    external fun malloc(s: Long): Long

    /**
     * Frees memory, which has been allocated with [VPX.malloc] or
     * one of the `*_malloc()` functions.
     *
     * @param ptr Pointer to the memory to free.
     */
    external fun free(ptr: Long)

    /**
     * Copies `n` bytes from `src` to `dst`. Simple wrapper
     * around the native `memcpy()` function.
     *
     * @param dst Destination.
     * @param src Source.
     * @param n Number of bytes to copy.
     */
    external fun memcpy(dst: ByteArray?, src: Long, n: Int)

    /**
     * Fills in `buf` with a string description of the error code
     * `err`. Fills at most `buf_size` bytes of `buf`
     *
     * @param err Error code
     * @param buf Buffer to copy the string into
     * @param buf_size Buffer size
     * @return The number of bytes written to `buf`
     */
    external fun codec_err_to_string(err: Int,
            buf: ByteArray?, buf_size: Int): Int

    /**
     * Returns a `String` describing the error code `err`.
     *
     * @param err Error code
     * @return A `String` describing the error code `err`.
     */
    fun codec_err_to_string(err: Int): String {
        val buf = ByteArray(100)
        codec_err_to_string(err, buf, buf.size)
        return String(buf).trim { it <= ' ' } // Remove all null characters i.e. '0'
    }

    init {
        System.loadLibrary("jnvpx")
    }

    /**
     * Java wrapper around vpx_codec_stream_info_t. Contains basic information,
     * obtainable from an encoded frame without a decoder context.
     */
    internal class StreamInfo(iface: Int, buf: ByteArray?, buf_offset: Int, buf_size: Int) {
        /**
         * Width
         */
        var w = 0

        /**
         * Height
         */
        var h = 0

        /**
         * Is keyframe
         */
        var is_kf = false

        /**
         * Initializes this instance by parsing `buf`
         *
         * @param iface Interface, should be one of the `INTERFACE_*` constants.
         * @param buf Buffer containing a compressed frame to parse.
         * @param buf_offset Offset into buffer where the compressed frame begins.
         * @param buf_size Size of the compressed frame.
         */
        init {
            val si = stream_info_malloc()
            if (codec_peek_stream_info(iface, buf, buf_offset, buf_size, si) == CODEC_OK) {
                w = stream_info_get_w(si)
                h = stream_info_get_h(si)
                is_kf = stream_info_get_is_kf(si) != 0
                if (si != 0L) free(si)
            }
        }

        /**
         * Gets the `is_kf` (is keyframe) field of this instance.
         *
         * @return the `is_kf` (is keyframe) field of this instance.
         */
        fun isKf(): Boolean {
            return is_kf
        }
    }
}