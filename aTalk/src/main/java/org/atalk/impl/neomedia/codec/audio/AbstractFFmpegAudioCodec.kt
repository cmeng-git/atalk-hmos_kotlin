/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio

import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.codec.FFmpeg.av_free
import org.atalk.impl.neomedia.codec.FFmpeg.avcodec_alloc_context3
import org.atalk.impl.neomedia.codec.FFmpeg.avcodec_close
import org.atalk.impl.neomedia.codec.FFmpeg.avcodec_open2
import org.atalk.impl.neomedia.codec.FFmpeg.avcodeccontext_get_frame_size
import org.atalk.impl.neomedia.codec.FFmpeg.avcodeccontext_set_ch_layout
import org.atalk.impl.neomedia.codec.FFmpeg.avcodeccontext_set_sample_rate
import javax.media.Format
import javax.media.ResourceUnavailableException
import javax.media.format.AudioFormat

/**
 * Implements an audio `Codec` using the FFmpeg library.
 *
 * @author Lyubomir Marinov
 */
abstract class AbstractFFmpegAudioCodec
/**
 * Initializes a new `AbstractFFmpegAudioCodec` instance with a specific `PlugIn`
 * name, a specific `AVCodecID`, and a specific list of `Format`s supported as output.
 *
 * @param name the `PlugIn` name of the new instance
 * @param codecID the `AVCodecID` of the FFmpeg codec to be represented by the new instance
 * @param supportedOutputFormats the list of `Format`s supported by the new instance as output
 */
protected constructor(name: String,
        /**
         * The `AVCodecID` of [.avctx].
         */
        protected val codecID: Int, supportedOutputFormats: Array<out Format>) : AbstractCodec2(name, AudioFormat::class.java, supportedOutputFormats) {
    /**
     * The `AVCodecContext` which performs the actual encoding/decoding and which is the
     * native counterpart of this open `AbstractFFmpegAudioCodec`.
     */
    protected var avctx = 0L

    /**
     * The number of bytes of audio data to be encoded with a single call to
     * [FFmpeg.avcodec_encode_audio] based on the
     * `frame_size` of [.avctx].
     */
    protected var frameSizeInBytes = 0

    /**
     * Configures the `AVCodecContext` initialized in [.doOpen] prior to invoking one
     * of the FFmpeg functions in the `avcodec_open` family. Allows extenders to override and
     * provide additional, optional configuration.
     *
     * @param avctx the `AVCodecContext` to configure
     * @param format the `AudioFormat` with which `avctx` is being configured
     */
    protected open fun configureAVCodecContext(avctx: Long, format: AudioFormat?) {}

    /**
     * {@inheritDoc}
     */
    @Synchronized
    override fun doClose() {
        if (avctx != 0L) {
            avcodec_close(avctx)
            av_free(avctx)
            avctx = 0
        }
    }

    /**
     * {@inheritDoc}
     */
    @Synchronized
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        val codecID = codecID
        val codec = findAVCodec(codecID)
        if (codec == 0L) {
            throw ResourceUnavailableException("Could not find FFmpeg codec " + codecIDToString(codecID) + "!")
        }
        avctx = avcodec_alloc_context3(codec)
        if (avctx == 0L) {
            throw ResourceUnavailableException(
                    "Could not allocate AVCodecContext for FFmpeg codec " + codecIDToString(codecID) + "!")
        }
        var avcodec_open = -1
        try {
            val format = aVCodecContextFormat
            var channels = format.channels
            val sampleRate = format.sampleRate.toInt()
            if (channels == Format.NOT_SPECIFIED) channels = 1
            if (channels == 1) {
                // mono
                avcodeccontext_set_ch_layout(avctx, FFmpeg.AV_CH_LAYOUT_MONO)
            } else if (channels == 2) {
                // stereo
                avcodeccontext_set_ch_layout(avctx, FFmpeg.AV_CH_LAYOUT_STEREO)
            }
            // For ffmpeg 5.1, the following is not required to set with avcodeccontext_set_ch_layout()
            // FFmpeg.avcodeccontext_set_channels(avctx, channels);
            if (sampleRate != Format.NOT_SPECIFIED) avcodeccontext_set_sample_rate(avctx, sampleRate)
            configureAVCodecContext(avctx, format)
            avcodec_open = avcodec_open2(avctx, codec)

            // When encoding, set by libavcodec in avcodec_open2 and may be 0 to
            // indicate unrestricted frame size. When decoding, may be set by
            // some decoders to indicate constant frame size.
            val frameSize = avcodeccontext_get_frame_size(avctx)
            frameSizeInBytes = frameSize * (format.sampleSizeInBits / 8) * channels
        } finally {
            if (avcodec_open < 0) {
                av_free(avctx)
                avctx = 0
            }
        }
        if (avctx == 0L) {
            throw ResourceUnavailableException("Could not open FFmpeg codec "
                    + codecIDToString(codecID) + "!")
        }
    }

    /**
     * Finds an `AVCodec` with a specific `AVCodecID`. The method is invoked by
     * [.doOpen] in order to (eventually) open a new `AVCodecContext`.
     *
     * @param codecID the `AVCodecID` of the `AVCodec` to find
     * @return an `AVCodec` with the specified `codecID` or `0`
     */
    protected abstract fun findAVCodec(codecID: Int): Long

    /**
     * Gets the `AudioFormat` with which [.avctx] is to be configured and opened by
     * [.doOpen].
     *
     * @return the `AudioFormat` with which `avctx` is to be configured and opened by
     * `doOpen()`
     */
    protected abstract val aVCodecContextFormat: AudioFormat

    companion object {
        /**
         * Returns a `String` representation of a specific `AVCodecID`.
         *
         * @param codecID the `AVCodecID` to represent as a `String`
         * @return a `String` representation of the specified `codecID`
         */
        fun codecIDToString(codecID: Int): String {
            return when (codecID) {
                FFmpeg.CODEC_ID_MP3 -> "CODEC_ID_MP3"
                else -> "0x" + java.lang.Long.toHexString(codecID.toLong() and 0xFFFFFFFFL)
            }
        }
    }
}