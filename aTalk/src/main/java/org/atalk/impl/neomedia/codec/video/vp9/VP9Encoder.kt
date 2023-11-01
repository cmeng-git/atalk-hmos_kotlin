/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.codec.video.vp9

import org.atalk.impl.neomedia.NeomediaServiceUtils
import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.impl.neomedia.codec.video.VPX
import org.atalk.impl.neomedia.device.DeviceConfiguration
import org.atalk.service.neomedia.codec.Constants
import timber.log.Timber
import java.awt.Dimension
import javax.media.Buffer
import javax.media.Format
import javax.media.PlugIn
import javax.media.ResourceUnavailableException
import javax.media.format.VideoFormat
import javax.media.format.YUVFormat

/**
 * Implements a VP9 encoder.
 *
 * @author Eng Chong Meng
 */
class VP9Encoder : AbstractCodec2("VP9 Encoder", VideoFormat::class.java, SUPPORTED_OUTPUT_FORMATS) {
    /**
     * Pointer to a native vpx_codec_dec_cfg structure containing encoder configuration
     */
    private var cfg: Long = 0

    /**
     * Pointer to the libvpx codec context to be used
     */
    private var vpctx: Long = 0

    /**
     * Flags passed when (re-)initializing the encoder context on first and when orientation change
     */
    private var flags: Long = 0

    /**
     * Number of encoder frames so far. Used as pst (presentation time stamp)
     */
    private var frameCount: Long = 0

    /**
     * Pointer to a native vpx_image instance used to feed frames to the encoder
     */
    private var img: Long = 0

    /**
     * Iterator for the compressed frames in the encoder context.
     * Can be re-initialized by setting its only element to 0.
     */
    private val iter = LongArray(1)

    /**
     * Whether there are unprocessed packets left from a previous call to VP9.codec_encode()
     */
    private var leftoverPackets = false

    /**
     * Pointer to a vpx_codec_cx_pkt_t
     */
    private var pkt: Long = 0

    /**
     * Current width and height of the input and output frames
     * Assume the device is always started in portrait mode with weight and height swap for use in the codec;
     */
    private var mWidth: Int = DeviceConfiguration.DEFAULT_VIDEO_HEIGHT
    private var mHeight: Int = DeviceConfiguration.DEFAULT_VIDEO_WIDTH

    /**
     * Initializes a new `VP9Encoder` instance.
     */
    init {
        inputFormats = arrayOf<VideoFormat>(YUVFormat(
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
     * {@inheritDoc}
     */
    override fun doClose() {
        Timber.d("Closing encoder")
        if (vpctx != 0L) {
            // /data/app/org.atalk.hmos-GeVNob40TxcTyYuV2rXATA==/lib/arm/libjnvpx.so (vp9_remove_compressor+224)
            VPX.codec_destroy(vpctx)
            VPX.free(vpctx)
            vpctx = 0
        }
        if (img != 0L) {
            VPX.free(img)
            img = 0
        }
        if (cfg != 0L) {
            VPX.free(cfg)
            cfg = 0
        }
    }
    // private FileOutputStream fos;
    /**
     * {@inheritDoc}
     *
     * @throws ResourceUnavailableException
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
            Timber.d("VP9 encode video size: %s", size)
            mWidth = size.width
            mHeight = size.height
        }
        img = VPX.img_alloc(img, VPX.IMG_FMT_I420, mWidth, mHeight, 1)
        if (img == 0L) {
            throw RuntimeException("Failed to allocate image.")
        }
        cfg = VPX.codec_enc_cfg_malloc()
        if (cfg == 0L) {
            throw RuntimeException("Could not codec_enc_cfg_malloc()")
        }
        VPX.codec_enc_config_default(INTERFACE, cfg, 0)

        // setup the decoder required parameter settings
        val bitRate = NeomediaServiceUtils.mediaServiceImpl!!.deviceConfiguration.videoBitrate
        VPX.codec_enc_cfg_set_w(cfg, mWidth)
        VPX.codec_enc_cfg_set_h(cfg, mHeight)

        // VPX.codec_enc_cfg_set_tbnum(cfg, 1);
        // VPX.codec_enc_cfg_set_tbden(cfg, 15);
        VPX.codec_enc_cfg_set_rc_target_bitrate(cfg, bitRate)
        VPX.codec_enc_cfg_set_rc_resize_allowed(cfg, 1)
        VPX.codec_enc_cfg_set_rc_end_usage(cfg, VPX.RC_MODE_CBR)
        VPX.codec_enc_cfg_set_kf_mode(cfg, VPX.KF_MODE_AUTO)

        // cfg.g_lag_in_frames should be set to 0 for realtime
        VPX.codec_enc_cfg_set_lag_in_frames(cfg, 0)

        // Must be enabled together with VP8E_SET_CPUUSED for realtime encode
        VPX.codec_enc_cfg_set_threads(cfg, 1)
        VPX.codec_enc_cfg_set_error_resilient(cfg, VPX.ERROR_RESILIENT_DEFAULT or VPX.ERROR_RESILIENT_PARTITIONS)
        vpctx = VPX.codec_ctx_malloc()
        flags = 0
        val ret: Int = VPX.codec_enc_init(vpctx, INTERFACE, cfg, flags)
        if (ret != VPX.CODEC_OK) throw RuntimeException("Failed to initialize encoder, libvpx error:\n"
                + VPX.codec_err_to_string(ret))

        // Must be defined together with g_threads for realtime encode
        VPX.codec_control(vpctx, VPX.VP8E_SET_CPUUSED, 7)
        VPX.codec_control(vpctx, VPX.VP9E_SET_POSTENCODE_DROP, 0)
        // VPX.codec_control(vpctx, VpCx.VpCid.VP9E_SET_POSTENCODE_DROP.ordinal(), 0);

        // ji… via monorail: For realtime video application you should not use a lossless mode.
        // VPX.codec_control(context, VPX.VP9E_SET_LOSSLESS, 1);
        if (inputFormat == null) throw ResourceUnavailableException("No input format selected")
        if (outputFormat == null) throw ResourceUnavailableException("No output format selected")
        Timber.d("VP9 encoder opened successfully")

        // Create an output file for saving video stream
//        String fileName = "yuv420_480x720.jpg";
//        String downloadPath = FileBackend.TMP + File.separator;
//        File downloadDir = FileBackend.getaTalkStore(downloadPath, true);
//        File outFile = new File(downloadDir, fileName);
//        try {
//            fos = new FileOutputStream(outFile);
//        } catch (FileNotFoundException e) {
//            Timber.e("Output stream file creation exception: %s", e.getMessage());
//        }
    }

    /**
     * {@inheritDoc}
     * Encode the frame in `inputBuffer` (in `YUVFormat`) into a VP9 frame (in `outputBuffer`)
     *
     * @param inBuf input `Buffer`
     * @param outBuf output `Buffer`
     * @return `BUFFER_PROCESSED_OK` if `inBuffer` has been successfully processed
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        var ret: Int = PlugIn.BUFFER_PROCESSED_OK
        val output: ByteArray
        if (!leftoverPackets) {
            val format = inBuf.format as YUVFormat
            val formatSize: Dimension = format.size
            val width = formatSize.width
            val height = formatSize.height
            flags = 0
            if (width > 0 && height > 0 && (width != mWidth || height != mHeight)) {
                Timber.d("VP9 encode video size changed: [width=%s, height=%s]=>%s", mWidth, mHeight, formatSize)
                updateSize(width, height)
            }
            var offsetY: Int = format.offsetY
            if (offsetY == Format.NOT_SPECIFIED) offsetY = 0
            var offsetU: Int = format.offsetU
            if (offsetU == Format.NOT_SPECIFIED) offsetU = offsetY + width * height
            var offsetV: Int = format.offsetV
            if (offsetV == Format.NOT_SPECIFIED) offsetV = offsetU + width * height / 4

            // if (frameCount < 5)
            // Timber.d("VP9: Encoding a frame #%s: %s %s", frameCount, bytesToHex((byte[]) inputBuffer.getData(), 32), inputBuffer.getLength());

            // routine to save raw input data into a file.
//             if (frameCount < 40) {
//                 if (fos != null) {
//                     try {
//                         fos.write((byte[]) inputBuffer.getData());
//                         // Timber.e("File fos write frame #: %s:", frameCount);
//                         // Timber.d("VP9: Encoding a frame #%s: %s %s", frameCount, bytesToHex((byte[]) inputBuffer.getData(), 32), inputBuffer.getLength());
//                         if (frameCount == 39) {
//                             fos.close();
//                             Timber.d("File fos write completed:");
//                         }
//                     } catch (IOException e) {
//                         Timber.e("fos write exception: %s", e.getMessage());
//                     }
//                 }
//             }
            val result: Int = VPX.codec_encode(vpctx, img, inBuf.data as ByteArray,
                    offsetY, offsetU, offsetV,
                    frameCount++, 1, flags, VPX.DL_REALTIME.toLong())
            if (result != VPX.CODEC_OK) {
                if (frameCount % 50 == 1L) Timber.w("Failed to encode a frame: %s %s %s %s %s %s", VPX.codec_err_to_string(result), inBuf.length,
                        format.size, offsetY, offsetU, offsetV)
                outBuf.isDiscard = true
                return PlugIn.BUFFER_PROCESSED_OK
            }
            iter[0] = 0
            pkt = VPX.codec_get_cx_data(vpctx, iter)
        }
        if (pkt != 0L
                && VPX.codec_cx_pkt_get_kind(pkt) == VPX.CODEC_CX_FRAME_PKT) {
            val size: Int = VPX.codec_cx_pkt_get_size(pkt)
            val data: Long = VPX.codec_cx_pkt_get_data(pkt)
            output = validateByteArraySize(outBuf, size, false)
            VPX.memcpy(output, data, size)
            outBuf.offset = 0
            outBuf.length = size
            outBuf.timeStamp = inBuf.timeStamp
        } else {
            // not a compressed frame, skip this packet
            Timber.w("Skip partial compressed frame packet: %s: %s", pkt, frameCount)
            ret = ret or PlugIn.OUTPUT_BUFFER_NOT_FILLED
        }

        // Check for more encoded frame
        pkt = VPX.codec_get_cx_data(vpctx, iter)
        leftoverPackets = pkt != 0L
        return if (leftoverPackets) ret or PlugIn.INPUT_BUFFER_NOT_CONSUMED else {
            ret
        }
    }

    /**
     * Gets the matching output formats for a specific format.
     *
     * @param inputFormat input format
     * @return array of formats matching input format
     */
    override fun getMatchingOutputFormats(inputFormat: Format): Array<Format> {
        val inputVideoFormat = inputFormat as VideoFormat
        return arrayOf(VideoFormat(
                Constants.VP9,
                inputVideoFormat.size,
                Format.NOT_SPECIFIED,  /* maxDataLength */
                Format.byteArray,
                inputVideoFormat.frameRate)
        )
    }

    /**
     * Updates the input width and height the encoder should expect.
     * Force keyframe generation; needed when the input size changes.
     *
     * @param w new width
     * @param h new height
     */
    private fun updateSize(w: Int, h: Int) {
        mWidth = w
        mHeight = h
        img = VPX.img_alloc(img, VPX.IMG_FMT_I420, mWidth, mHeight, 1)
        if (img == 0L) throw RuntimeException("Failed to re-initialize VP9 encoder")
        if (cfg != 0L) {
            VPX.codec_enc_cfg_set_w(cfg, w)
            VPX.codec_enc_cfg_set_h(cfg, h)
        }
        VPX.codec_enc_config_set(vpctx, cfg)
        flags = flags or VPX.EFLAG_FORCE_KF.toLong()

        // vpx_jni: [0705/123845.503533:ERROR:scoped_ptrace_attach.cc(27)] ptrace: Operation not permitted (1)
        // org.atalk.hmos A/libc: Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x2 in tid 5868 (Loop thread: ne), pid 2084 (g.atalk.android)
        // org.atalk.hmos A/libc: crash_dump helper failed to exec
        // if (vpctx != 0) {
        //     VPX.codec_destroy(vpctx);
        //
        //     int ret = VPX.codec_enc_init(vpctx, INTERFACE, cfg, flags);
        //     if (ret != VPX.CODEC_OK)
        //         throw new RuntimeException("Failed to re-initialize VP9 encoder, libvpx error:\n"
        //                 + VPX.codec_err_to_string(ret));
        // }
    }

    /**
     * Sets the input format.
     *
     * @param format format to set
     * @return format
     */
    override fun setInputFormat(format: Format): Format? {
        if (format !is VideoFormat || matches(format, inputFormats) == null) return null
        val yuvFormat = format as YUVFormat
        if (yuvFormat.offsetU > yuvFormat.offsetV) return null

        // Return the selected inputFormat
        inputFormat = specialize(yuvFormat, Format.byteArray)
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
        /*
         * An Encoder translates raw media data in (en)coded media data.
         * Consequently, the size of the output is equal to the size of the input.
         */
        var size: Dimension? = null
        if (inputFormat != null) size = (inputFormat as VideoFormat).size
        if (size == null && format.matches(outputFormat)) size = (outputFormat as VideoFormat).size
        outputFormat = VideoFormat(
                format.encoding,
                size,
                Format.NOT_SPECIFIED,  /* maxDataLength */
                Format.byteArray,
                format.frameRate
        )

        // Return the selected outputFormat
        return outputFormat
    }

    companion object {
        /**
         * VPX interface to use
         */
        private const val INTERFACE = VPX.INTERFACE_VP9_ENC

        /**
         * Default output formats
         */
        private val SUPPORTED_OUTPUT_FORMATS = arrayOf(VideoFormat(Constants.VP9))
    }
}