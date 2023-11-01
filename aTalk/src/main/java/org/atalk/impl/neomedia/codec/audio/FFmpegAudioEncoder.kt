/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio

import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.codec.FFmpeg.avcodec_encode_audio
import org.atalk.impl.neomedia.codec.FFmpeg.avcodec_find_encoder
import org.atalk.impl.neomedia.codec.FFmpeg.avcodeccontext_set_sample_fmt
import timber.log.Timber
import javax.media.Buffer
import javax.media.Format
import javax.media.format.AudioFormat

/**
 * Implements an audio `Codec` using the FFmpeg library.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
open class FFmpegAudioEncoder
/**
 * Initializes a new `FFmpegAudioEncoder` instance with a specific `PlugIn` name,
 * a specific `AVCodecID`, and a specific list of `Format`s supported as output.
 *
 * @param name the `PlugIn` name of the new instance
 * @param codecID the `AVCodecID` of the FFmpeg codec to be represented by the new instance
 * @param supportedOutputFormats the list of `Format`s supported by the new instance as output
 */
protected constructor(name: String, codecID: Int, supportedOutputFormats: Array<out Format>) : AbstractFFmpegAudioCodec(name, codecID, supportedOutputFormats) {
    /**
     * The audio data which was given to this `AbstractFFmpegAudioCodec` in a previous call
     * to [.doProcess] but was less than [.frameSizeInBytes] in length
     * and was thus left to be prepended to the audio data in a next call to `doProcess`.
     */
    private var prevIn: ByteArray? = null

    /**
     * The length of the valid audio data in [.prevIn].
     */
    protected var prevInLen = 0

    /**
     * {@inheritDoc}
     */
    override fun configureAVCodecContext(avctx: Long, format: AudioFormat?) {
        super.configureAVCodecContext(avctx, format)
        try {
            avcodeccontext_set_sample_fmt(avctx, FFmpeg.AV_SAMPLE_FMT_S16P)
        } catch (ule: UnsatisfiedLinkError) {
            Timber.w("The FFmpeg JNI library is out-of-date.")
        }
    }

    /**
     * {@inheritDoc}
     */
    @Synchronized
    override fun doClose() {
        super.doClose()
        prevIn = null
        prevInLen = 0
    }

    /**
     * {@inheritDoc}
     */
    @Synchronized
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        var inBytes = inBuf.data as ByteArray
        var inLen = inBuf.length
        var inOff = inBuf.offset
        if (prevInLen > 0 || inLen < frameSizeInBytes) {
            val newPrevInLen = Math.min(frameSizeInBytes - prevInLen, inLen)
            if (newPrevInLen > 0) {
                if (prevIn == null) {
                    prevIn = ByteArray(frameSizeInBytes)
                    prevInLen = 0
                }
                System.arraycopy(inBytes, inOff, prevIn, prevInLen, newPrevInLen)
                inBuf.length = inLen - newPrevInLen
                inBuf.offset = inOff + newPrevInLen
                prevInLen += newPrevInLen
                if (prevInLen == frameSizeInBytes) {
                    inBytes = prevIn!!
                    inLen = prevInLen
                    inOff = 0
                    prevInLen = 0
                } else {
                    return OUTPUT_BUFFER_NOT_FILLED
                }
            }
        } else {
            inBuf.length = inLen - frameSizeInBytes
            inBuf.offset = inOff + frameSizeInBytes
        }
        val outData = outBuf.data
        var out = if (outData is ByteArray) outData else null
        var outOff = outBuf.offset
        val minOutLen = Math.max(FFmpeg.FF_MIN_BUFFER_SIZE, inLen)
        if (out == null || out.size - outOff < minOutLen) {
            out = ByteArray(minOutLen)
            outBuf.data = out
            outOff = 0
            outBuf.offset = outOff
        }
        val outLen = avcodec_encode_audio(avctx, out, outOff, out.size - outOff, inBytes, inOff)
        return if (outLen < 0) {
            BUFFER_PROCESSED_FAILED
        } else {
            outBuf.format = getOutputFormat()
            outBuf.length = outLen
            if (inBuf.length > 0) INPUT_BUFFER_NOT_CONSUMED else if (outLen == 0) OUTPUT_BUFFER_NOT_FILLED else BUFFER_PROCESSED_OK
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun findAVCodec(codecID: Int): Long {
        return avcodec_find_encoder(codecID)
    }

    /**
     * {@inheritDoc}
     */
    protected override val aVCodecContextFormat: AudioFormat
        protected get() = getInputFormat() as AudioFormat

    companion object {
        /**
         * Asserts that an encoder with a specific `AVCodecID` is found by FFmpeg.
         *
         * @param codecID the `AVCodecID` of the encoder to find
         * @throws RuntimeException if no encoder with the specified `codecID` is found by FFmpeg
         */
        fun assertFindAVCodec(codecID: Int) {
            if (avcodec_find_encoder(codecID) == 0L) {
                throw RuntimeException("Could not find FFmpeg encoder: " + codecIDToString(codecID) + "!")
            }
        }
    }
}