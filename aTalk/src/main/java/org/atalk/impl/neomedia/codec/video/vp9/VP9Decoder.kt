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

import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.codec.FFmpeg.avframe_set_data
import org.atalk.impl.neomedia.codec.FFmpeg.avframe_set_linesize
import org.atalk.impl.neomedia.codec.video.AVFrame
import org.atalk.impl.neomedia.codec.video.AVFrameFormat
import org.atalk.impl.neomedia.codec.video.VPX
import org.atalk.impl.neomedia.codec.video.VPX.codec_ctx_malloc
import org.atalk.impl.neomedia.codec.video.VPX.codec_dec_init
import org.atalk.impl.neomedia.codec.video.VPX.codec_decode
import org.atalk.impl.neomedia.codec.video.VPX.codec_destroy
import org.atalk.impl.neomedia.codec.video.VPX.codec_err_to_string
import org.atalk.impl.neomedia.codec.video.VPX.codec_get_frame
import org.atalk.impl.neomedia.codec.video.VPX.free
import org.atalk.impl.neomedia.codec.video.VPX.img_get_d_h
import org.atalk.impl.neomedia.codec.video.VPX.img_get_d_w
import org.atalk.impl.neomedia.codec.video.VPX.img_get_plane0
import org.atalk.impl.neomedia.codec.video.VPX.img_get_plane1
import org.atalk.impl.neomedia.codec.video.VPX.img_get_plane2
import org.atalk.impl.neomedia.codec.video.VPX.img_get_stride0
import org.atalk.impl.neomedia.codec.video.VPX.img_get_stride1
import org.atalk.impl.neomedia.codec.video.VPX.img_get_stride2
import org.atalk.service.neomedia.codec.Constants
import timber.log.Timber
import java.awt.Dimension
import javax.media.Buffer
import javax.media.Format
import javax.media.ResourceUnavailableException
import javax.media.format.VideoFormat

/**
 * Implements a VP9 decoder.
 *
 * @author Eng Chong Meng
 */
class VP9Decoder : AbstractCodec2("VP9 VPX Decoder", VideoFormat::class.java, SUPPORTED_OUTPUT_FORMATS) {
    /**
     * Pointer to a native vpx_codec_dec_cfg structure containing the decoder configuration
     */
    private val cfg: Long = 0

    /**
     * Pointer to the libvpx codec context to be used
     */
    private var vpctx: Long = 0

    /**
     * Pointer to a native vpx_image structure, containing a decoded frame.
     * When doProcess() is called, this is either 0, or it has the address of
     * the next unprocessed image from the decoder.
     */
    private var img: Long = 0

    /**
     * Iterator for the frames in the decoder context. Can be re-initialized by setting its only element to 0.
     */
    private val iter = LongArray(1)

    /**
     * Whether there are unprocessed frames left from a previous call to VP9.codec_decode()
     */
    private var leftoverFrames = false

    /**
     * The last known height of the video output by this `VPXDecoder`. Used to detect changes in the output size.
     */
    private var mWidth = 0

    /**
     * The last known width of the video output by this `VPXDecoder`. Used to detect changes in the output size.
     */
    private var mHeight = 0

    /**
     * {@inheritDoc}
     */
    override fun doClose() {
        Timber.d("Closing decoder")
        if (vpctx != 0L) {
            codec_destroy(vpctx)
            free(vpctx)
        }
        if (cfg != 0L) free(cfg)
    }

    /**
     * {@inheritDoc}
     *
     * @throws ResourceUnavailableException if initialization failed
     */
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        vpctx = codec_ctx_malloc()
        //cfg = VPX.codec_dec_cfg_malloc();
        val flags: Long = 0 //VPX.CODEC_USE_XMA;

        // The cfg NULL pointer is passed to vpx_codec_dec_init(). This is to allow the algorithm
        // to determine the stream configuration (width/height) and allocate memory automatically.
        val ret = codec_dec_init(vpctx, INTERFACE, 0, flags)
        if (ret != VPX.CODEC_OK)
            throw RuntimeException("Failed to initialize decoder, libvpx error:\n" + codec_err_to_string(ret))
        Timber.d("VP9 decoder opened successfully")
    }

    var frameErrors = 0

    /**
     * Initializes a new `VPXDecoder` instance.
     */
    init {
        inputFormats = arrayOf(VideoFormat(Constants.VP9))
    }

    /**
     * {@inheritDoc}
     *
     * Decodes a VP9 frame contained in `inputBuffer` into `outputBuffer` (in `AVFrameFormat`)
     *
     * @param inBuf input `Buffer`
     * @param outBuf output `Buffer`
     * @return `BUFFER_PROCESSED_OK` if `inBuffer` has been successfully processed
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        if (!leftoverFrames) {
            /*
             * All frames from the decoder context have been processed. Decode the next VP9
             * frame, and fill outputBuffer with the first decoded frame.
             */
            val buf_data = inBuf.data as ByteArray
            val buf_offset = inBuf.offset
            val buf_size = inBuf.length

            // if (frameErrors++ < 2)
            //    Timber.d("VP9: Decoding a frame #%s: %s %s %s", frameErrors, bytesToHex(buf_data, 32), buf_offset, buf_size);
            val ret = codec_decode(vpctx,
                    buf_data, buf_offset, buf_size,
                    0, VPX.DL_BEST_QUALITY.toLong())
            if (ret != VPX.CODEC_OK) {
                // if ((frameErrors < 10))
                //     Timber.w("VP9: Discarding frame with decode error %s: %s %s %s %s", frameErrors, VPX.codec_err_to_string(ret),
                //             buf_data, buf_offset, buf_size);
                // if (frameErrors < 4) {
                //     Timber.d(new Exception());
                //     aTalkApp.showToastMessage(R.string.service_gui_CALL_NO_MATCHING_FORMAT_H, VPX.codec_err_to_string(ret));
                // }
                outBuf.isDiscard = true
                return BUFFER_PROCESSED_OK
            }

            //decode has just been called, reset iterator
            iter[0] = 0
            img = codec_get_frame(vpctx, iter)
        }

        // Timber.d("VP9: Decoded image frame; leftoverframe: %s %s", leftoverFrames, img);
        if (img == 0L) {
            outBuf.isDiscard = true
            return BUFFER_PROCESSED_OK
        }

        // Fill outputBuffer with the newly decoded or leftover frame data.
        updateOutputFormat(img_get_d_w(img), img_get_d_h(img),
                (inBuf.format as VideoFormat).frameRate)
        outBuf.format = outputFormat
        val avframe = makeAVFrame(img)
        outBuf.data = avframe

        // YUV420p format, 12 bits per pixel
        outBuf.length = mWidth * mHeight * 3 / 2
        outBuf.timeStamp = inBuf.timeStamp

        /*
         * outputBuffer is all setup now. Check the decoder context for more decoded frames.
         */img = codec_get_frame(vpctx, iter)
        return if (img == 0L) //no more frames
        {
            leftoverFrames = false
            BUFFER_PROCESSED_OK
        } else {
            leftoverFrames = true
            INPUT_BUFFER_NOT_CONSUMED
        }
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
                        inputVideoFormat.frameRate,
                        FFmpeg.PIX_FMT_YUV420P)
        )
    }

    /**
     * Allocates a new AVFrame and set its data fields to the data fields from the
     * `vpx_image_t` pointed to by `img`. Also set its 'linesize' according to `img`.
     *
     * @param img pointer to a `vpx_image_t` whose data will be used
     * @return an AVFrame instance with its data fields set to the fields from `img`
     */
    private fun makeAVFrame(img: Long): AVFrame {
        val avframe = AVFrame()
        val p0 = img_get_plane0(img)
        val p1 = img_get_plane1(img)
        val p2 = img_get_plane2(img)

        //p0, p1, p2 are pointers, while avframe_set_data uses offsets
        avframe_set_data(avframe.ptr,
                p0,
                p1 - p0,
                p2 - p1)
        avframe_set_linesize(avframe.ptr,
                img_get_stride0(img),
                img_get_stride1(img),
                img_get_stride2(img))
        return avframe
    }

    /**
     * Sets the `Format` of the media data to be input for processing in this `Codec`.
     *
     * @param format the `Format` of the media data to be input for processing in this `Codec`
     * @return the `Format` of the media data to be input for processing in this `Codec`
     * if `format` is compatible with this `Codec`; otherwise, `null`
     */
    override fun setInputFormat(format: Format): Format? {
        val setFormat = super.setInputFormat(format)
        if (setFormat != null) reset()
        return setFormat
    }

    /**
     * Changes the output format, if necessary, according to the new dimensions given via `width` and `height`.
     *
     * @param width new width
     * @param height new height
     * @param frameRate frame rate
     */
    private fun updateOutputFormat(width: Int, height: Int, frameRate: Float) {
        if (width > 0 && height > 0 && (mWidth != width || mHeight != height)) {
            mWidth = width
            mHeight = height
            outputFormat = AVFrameFormat(
                    Dimension(width, height),
                    frameRate,
                    FFmpeg.PIX_FMT_YUV420P)
        }
    }

    companion object {
        /**
         * The decoder interface to use
         */
        private const val INTERFACE = VPX.INTERFACE_VP9_DEC

        /**
         * Default output formats
         */
        private val SUPPORTED_OUTPUT_FORMATS = arrayOf<VideoFormat>(AVFrameFormat(FFmpeg.PIX_FMT_YUV420P))
    }
}