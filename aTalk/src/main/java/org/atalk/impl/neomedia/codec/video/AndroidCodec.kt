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
import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.service.neomedia.codec.Constants
import timber.log.Timber
import java.awt.Dimension
import java.io.IOException
import java.nio.ByteBuffer
import javax.media.Buffer
import javax.media.Format
import javax.media.ResourceUnavailableException

/**
 * Abstract codec class uses android `MediaCodec` for video decoding/encoding.
 * Eventually `AndroidDecoder` and `AndroidEncoder` can be merged later.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
abstract class AndroidCodec
/**
 * Creates a new instance of `AndroidCodec`.
 *
 * @param name the `PlugIn` name of the new instance
 * @param formatClass the `Class` of input and output `Format`s supported by the new instance
 * @param supportedOutputFormats the list of `Format`s supported by the new instance as output.
 */
protected constructor(name: String, formatClass: Class<out Format>,
        supportedOutputFormats: Array<out Format>,
        /**
         * Indicates that this instance is used for encoding(and not for decoding).
         */
        private val isEncoder: Boolean) : AbstractCodec2(name, formatClass, supportedOutputFormats) {
    /**
     * `MediaCodec` used by this instance.
     */
    private var codec: MediaCodec? = null

    /**
     * Input `MediaCodec` buffer.
     */
    var codecInputBuf: ByteBuffer? = null

    /**
     * Output `MediaCodec` buffer.
     */
    var codecOutputBuf: ByteBuffer? = null

    /**
     * `BufferInfo` object that stores codec buffer information.
     */
    var mBufferInfo = MediaCodec.BufferInfo()

    /**
     * Class should return `true` if surface will be used.
     *
     * @return `true` if surface will be used.
     */
    protected abstract fun useSurface(): Boolean

    /**
     * Returns `Surface` used by this instance for encoding or decoding.
     *
     * @return `Surface` used by this instance for encoding or decoding.
     */
    protected abstract val surface: Surface?

    /**
     * Template method used to configure `MediaCodec` instance. Called before starting the codec.
     *
     * @param codec `MediaCodec` instance to be configured.
     * @param codecType string codec media type.
     * @throws ResourceUnavailableException Resource Unavailable Exception if not supported
     */
    @Throws(ResourceUnavailableException::class)
    protected abstract fun configureMediaCodec(codec: MediaCodec, codecType: String)

    /**
     * Selects `MediaFormat` color format used.
     *
     * @return used `MediaFormat` color format.
     */
    protected val colorFormat: Int
        get() = when {
            useSurface() -> COLOR_FormatSurface
            else -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        }

    /**
     * {@inheritDoc}
     */
    override fun doClose() {
        if (codec != null) {
            try {
                // Throws IllegalStateException – if in the Released state.
                codec!!.stop()
                codec!!.release()
            } catch (e: IllegalStateException) {
                Timber.w("Codec stop exception: %s", e.message)
            } finally {
                codec = null
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        val codecType: String
        val encoding = if (isEncoder) outputFormat.encoding else inputFormat.encoding
        codecType = when (encoding) {
            Constants.VP9 -> CodecInfo.MEDIA_CODEC_TYPE_VP9
            Constants.VP8 -> CodecInfo.MEDIA_CODEC_TYPE_VP8
            Constants.H264 -> CodecInfo.MEDIA_CODEC_TYPE_H264
            else -> throw RuntimeException("Unsupported encoding: $encoding")
        }
        val codecInfo = CodecInfo.getCodecForType(codecType, isEncoder)
                ?: throw ResourceUnavailableException("No $strName found for type: $codecType")
        try {
            codec = MediaCodec.createByCodecName(codecInfo.name)
        } catch (e: IOException) {
            Timber.e("Exception in create codec name: %s", e.message)
        }
        // Timber.d("starting %s %s for: %s; useSurface: %s", codecType, getStrName(), codecInfo.getName(), useSurface());
        configureMediaCodec(codec!!, codecType)
        codec!!.start()
    }

    private val strName: String
        get() = if (isEncoder) "encoder" else "decoder"

    /**
     * {@inheritDoc}
     *
     * Exception: IllegalStateException thrown by codec.dequeueOutputBuffer or codec.dequeueInputBuffer
     * Any RuntimeException will close remote view container.
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        return try {
            doProcessImpl(inBuf, outBuf)
        } catch (e: Exception) {
            Timber.e(e, "Do process for codec: %s; Exception: %s", codec!!.name, e.message)
            throw RuntimeException(e)
        }
    }

    /**
     * Process the video stream:
     * We will first process the output data from the mediaCodec; then we will feed input into the decoder.
     *
     * @param inputBuffer input buffer
     * @param outputBuffer output buffer
     * @return process status
     */
    private fun doProcessImpl(inputBuffer: Buffer, outputBuffer: Buffer): Int {
        val outputFormat = outputFormat
        var processed = INPUT_BUFFER_NOT_CONSUMED or OUTPUT_BUFFER_NOT_FILLED

        // Process the output data from the codec
        // Returns the index of an output buffer that has been successfully decoded or one of the INFO_* constants.
        val outputBufferIdx = codec!!.dequeueOutputBuffer(mBufferInfo, 0)
        if (outputBufferIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            val outFormat = codec!!.outputFormat
            if (!isEncoder) {
                val pixelFormat = outFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT)
                val requestedFormat = colorFormat
                if (!useSurface() && pixelFormat != requestedFormat) {
                    throw RuntimeException("MediaCodec returned different color format: "
                            + pixelFormat + "(requested " + requestedFormat
                            + ", try using the Surface")
                }
            }
            Timber.d("Codec output format changed (encoder: %s): %s", isEncoder, outFormat)
            // Video size should be known at this point
            val videoSize = Dimension(outFormat.getInteger(MediaFormat.KEY_WIDTH), outFormat.getInteger(MediaFormat.KEY_HEIGHT))
            onSizeChanged(videoSize)
        } else if (outputBufferIdx >= 0) {
            // Timber.d("Reading output: %s:%s flag: %s", mBufferInfo.offset, mBufferInfo.size, mBufferInfo.flags);
            var outputLength = 0
            codecOutputBuf = null
            try {
                if (!isEncoder && useSurface()) {
                    processed = processed and OUTPUT_BUFFER_NOT_FILLED.inv()
                    outputBuffer.format = outputFormat
                    // Timber.d("Codec output format: %s", outputFormat);
                } else if (mBufferInfo.size.also { outputLength = it } > 0) {
                    codecOutputBuf = codec!!.getOutputBuffer(outputBufferIdx)
                    codecOutputBuf!!.position(mBufferInfo.offset)
                    codecOutputBuf!!.limit(mBufferInfo.offset + mBufferInfo.size)
                    val out = validateByteArraySize(outputBuffer, mBufferInfo.size, false)
                    codecOutputBuf!![out, 0, mBufferInfo.size]
                    outputBuffer.format = outputFormat
                    outputBuffer.length = outputLength
                    outputBuffer.offset = 0
                    processed = processed and OUTPUT_BUFFER_NOT_FILLED.inv()
                }
            } finally {
                if (codecOutputBuf != null) codecOutputBuf!!.clear()
                /*
                 * releaseOutputBuffer: the output buffer data will be forwarded to SurfaceView for render if true.
                 * see https://developer.android.com/reference/android/media/MediaCodec
                 */
                codec!!.releaseOutputBuffer(outputBufferIdx, !isEncoder && useSurface())
            }
            /*
             * We will first exhaust the output of the mediaCodec, and then we will feed input into it.
             */
            if (outputLength > 0) return processed
        } else if (outputBufferIdx != MediaCodec.INFO_TRY_AGAIN_LATER) {
            Timber.w("Codec output reports: %s", outputBufferIdx)
        }

        // Feed more data to the decoder.
        if (isEncoder && useSurface()) {
            inputBuffer.data = surface
            processed = processed and INPUT_BUFFER_NOT_CONSUMED.inv()
        } else {
            val inputBufferIdx = codec!!.dequeueInputBuffer(0)
            if (inputBufferIdx >= 0) {
                val buf_data = inputBuffer.data as ByteArray
                val buf_offset = inputBuffer.offset
                val buf_size = inputBuffer.length
                codecInputBuf = codec!!.getInputBuffer(inputBufferIdx)
                if (codecInputBuf!!.capacity() < buf_size) {
                    throw RuntimeException("Input buffer too small: " + codecInputBuf!!.capacity() + " < " + buf_size)
                }
                codecInputBuf!!.clear()
                codecInputBuf!!.put(buf_data, buf_offset, buf_size)
                codec!!.queueInputBuffer(inputBufferIdx, 0, buf_size, inputBuffer.timeStamp, 0)
                Timber.d("Fed input with %s bytes of data; Offset: %s.", buf_size, buf_offset)
                processed = processed and INPUT_BUFFER_NOT_CONSUMED.inv()
            } else if (inputBufferIdx != MediaCodec.INFO_TRY_AGAIN_LATER) {
                Timber.w("Codec input reports: %s", inputBufferIdx)
            }
        }
        return processed
    }

    /**
     * Method fired when `MediaCodec` detects video size.
     *
     * @param dimension video dimension.
     * @see AndroidDecoder.onSizeChanged
     */
    protected open fun onSizeChanged(dimension: Dimension?) {}

    companion object {
        /**
         * Copied from `MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface`
         */
        private const val COLOR_FormatSurface = 0x7F000789
    }
}