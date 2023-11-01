/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.speex

import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.service.neomedia.codec.Constants
import javax.media.Buffer
import javax.media.Format
import javax.media.PlugIn
import javax.media.ResourceUnavailableException
import javax.media.format.AudioFormat
import kotlin.math.min

/**
 * Implements a Speex encoder and RTP packetizer using the native Speex library.
 *
 * @author Lubomir Marinov
 */
class JNIEncoder : AbstractCodec2("Speex JNI Encoder", AudioFormat::class.java, SUPPORTED_OUTPUT_FORMATS) {
    /**
     * The pointer to the native `SpeexBits` into which the native Speex encoder (i.e.
     * [.state]) writes the encoded audio data.
     */
    private var bits: Long = 0

    /**
     * The duration in nanoseconds of an output `Buffer` produced by this `Codec`.
     */
    private var duration: Long = 0

    /**
     * The number of bytes from an input `Buffer` that this `Codec` processes in one
     * call of its [.process].
     */
    private var frameSize = 0

    /**
     * The bytes from an input `Buffer` from a previous call to
     * [.process] that this `Codec` didn't process because the total
     * number of bytes was less than [.frameSize] and need to be prepended to a subsequent
     * input `Buffer` in order to process a total of [.frameSize] bytes.
     */
    private var previousInput: ByteArray? = null

    /**
     * The length of the audio data in [.previousInput].
     */
    private var previousInputLength = 0

    /**
     * The sample rate configured into [.state].
     */
    private var sampleRate = 0

    /**
     * The native Speex encoder represented by this instance.
     */
    private var state: Long = 0

    /**
     * Initializes a new `JNIEncoder` instance.
     */
    init {
        inputFormats = SUPPORTED_INPUT_FORMATS
    }

    /**
     * @see AbstractCodec2.doClose
     */
    override fun doClose() {
        // state
        if (state != 0L) {
            Speex.speex_encoder_destroy(state)
            state = 0
            sampleRate = 0
            frameSize = 0
            duration = 0
        }
        // bits
        Speex.speex_bits_destroy(bits)
        bits = 0
        // previousInput
        previousInput = null
    }

    /**
     * Opens this `Codec` and acquires the resources that it needs to operate. A call to
     * [PlugIn.open] on this instance will result in a call to `doOpen` only if
     * [AbstractCodec.opened] is `false`. All required input and/or output formats are
     * assumed to have been set on this `Codec` before `doOpen` is called.
     *
     * @throws ResourceUnavailableException if any of the resources that this `Codec` needs to operate cannot be acquired
     * @see AbstractCodec2.doOpen
     */
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        bits = Speex.speex_bits_init()
        if (bits == 0L) throw ResourceUnavailableException("speex_bits_init")
    }

    /**
     * Processes (encode) a specific input `Buffer`.
     *
     * @param inBuf input buffer
     * @param outBuf output buffer
     * @return `BUFFER_PROCESSED_OK` if buffer has been successfully processed
     * @see AbstractCodec2.doProcess
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        var inputFormat = inBuf.format
        if ((inputFormat != null) && (inputFormat != this.inputFormat)
                && (inputFormat != this.inputFormat)) {
            if (null == setInputFormat(inputFormat)) return BUFFER_PROCESSED_FAILED
        }
        inputFormat = this.inputFormat

        /*
         * Make sure that the native Speex encoder which is represented by this instance is
         * configured to work with the inputFormat.
         */
        val inputAudioFormat = inputFormat as AudioFormat
        val inputSampleRate = inputAudioFormat.sampleRate.toInt()
        if (state != 0L && sampleRate != inputSampleRate) {
            Speex.speex_encoder_destroy(state)
            state = 0
            sampleRate = 0
            frameSize = 0
        }
        if (state == 0L) {
            val mode = Speex.speex_lib_get_mode(when (inputSampleRate) {
                16000 -> Speex.SPEEX_MODEID_WB
                32000 -> Speex.SPEEX_MODEID_UWB
                else -> Speex.SPEEX_MODEID_NB
            })
            if (mode == 0L) return BUFFER_PROCESSED_FAILED

            state = Speex.speex_encoder_init(mode)
            if (state == 0L) return BUFFER_PROCESSED_FAILED
            if (Speex.speex_encoder_ctl(state, Speex.SPEEX_SET_QUALITY, 4) != 0) return BUFFER_PROCESSED_FAILED
            if (Speex.speex_encoder_ctl(state, Speex.SPEEX_SET_SAMPLING_RATE, inputSampleRate) != 0) return BUFFER_PROCESSED_FAILED
            val frameSize = Speex.speex_encoder_ctl(state, Speex.SPEEX_GET_FRAME_SIZE)
            if (frameSize < 0) return BUFFER_PROCESSED_FAILED
            sampleRate = inputSampleRate
            this.frameSize = frameSize * 2 /* (sampleSizeInBits / 8) */
            duration = frameSize.toLong() * 1000 * 1000000 / sampleRate
        }

        /*
         * The native Speex encoder always processes frameSize bytes from the input in one call. If
         * any specified inputBuffer is with a different length, then we'll have to wait for more
         * bytes to arrive until we have frameSize bytes. Remember whatever is left unprocessed in
         * previousInput and prepend it to the next inputBuffer.
         */
        var input = inBuf.data as ByteArray
        var inputLength = inBuf.length
        var inputOffset = inBuf.offset
        if (previousInput != null && previousInputLength > 0) {
            if (previousInputLength < frameSize) {
                if (previousInput!!.size < frameSize) {
                    val newPreviousInput = ByteArray(frameSize)
                    System.arraycopy(previousInput!!, 0, newPreviousInput, 0, previousInput!!.size)
                    previousInput = newPreviousInput
                }
                val bytesToCopyFromInputToPreviousInput = min(frameSize - previousInputLength, inputLength)
                if (bytesToCopyFromInputToPreviousInput > 0) {
                    System.arraycopy(input, inputOffset, previousInput!!, previousInputLength,
                            bytesToCopyFromInputToPreviousInput)
                    previousInputLength += bytesToCopyFromInputToPreviousInput
                    inputLength -= bytesToCopyFromInputToPreviousInput
                    inBuf.length = inputLength
                    inBuf.offset = inputOffset + bytesToCopyFromInputToPreviousInput
                }
            }

            if (previousInputLength == frameSize) {
                input = previousInput!!
                inputOffset = 0
                previousInputLength = 0
            } else if (previousInputLength > frameSize) {
                input = ByteArray(frameSize)
                System.arraycopy(previousInput!!, 0, input, 0, input.size)
                inputOffset = 0
                previousInputLength -= input.size
                System.arraycopy(previousInput!!, input.size, previousInput!!, 0,
                        previousInputLength)
            } else {
                outBuf.length = 0
                discardOutputBuffer(outBuf)
                return if (inputLength < 1) BUFFER_PROCESSED_OK else BUFFER_PROCESSED_OK or INPUT_BUFFER_NOT_CONSUMED
            }
        } else if (inputLength < 1) {
            outBuf.length = 0
            discardOutputBuffer(outBuf)
            return BUFFER_PROCESSED_OK
        } else if (inputLength < frameSize) {
            if (previousInput == null || previousInput!!.size < inputLength) previousInput = ByteArray(frameSize)
            System.arraycopy(input, inputOffset, previousInput!!, 0, inputLength)
            previousInputLength = inputLength
            outBuf.length = 0
            discardOutputBuffer(outBuf)
            return BUFFER_PROCESSED_OK
        } else {
            inputLength -= frameSize
            inBuf.length = inputLength
            inBuf.offset = inputOffset + frameSize
        }

        /* At long last, do the actual encoding. */
        Speex.speex_bits_reset(bits)
        Speex.speex_encode_int(state, input, inputOffset, bits)

        /* Read the encoded audio data from the SpeexBits into outputBuffer. */
        var outputLength = Speex.speex_bits_nbytes(bits)
        if (outputLength > 0) {
            val output = validateByteArraySize(outBuf, outputLength, false)
            outputLength = Speex.speex_bits_write(bits, output, 0, output.size)
            if (outputLength > 0) {
                outBuf.duration = duration
                outBuf.format = getOutputFormat()
                outBuf.length = outputLength
                outBuf.offset = 0
            } else {
                outBuf.length = 0
                discardOutputBuffer(outBuf)
            }
        } else {
            outBuf.length = 0
            discardOutputBuffer(outBuf)
        }
        return if (inputLength < 1) BUFFER_PROCESSED_OK else BUFFER_PROCESSED_OK or INPUT_BUFFER_NOT_CONSUMED
    }

    /**
     * Get the output formats matching a specific input format.
     *
     * @param inputFormat the input format to get the matching output formats of
     * @return the output formats matching the specified input format
     * @see AbstractCodec2.getMatchingOutputFormats
     */
    override fun getMatchingOutputFormats(inputFormat: Format): Array<Format> {
        return arrayOf(
                AudioFormat(
                        Constants.SPEEX_RTP,
                        (inputFormat as AudioFormat).sampleRate,
                        Format.NOT_SPECIFIED,
                        1,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED,
                        Format.NOT_SPECIFIED.toDouble(),
                        Format.byteArray)
        )
    }

    /**
     * Get the output format.
     *
     * @return output format
     * @see net.sf.fmj.media.AbstractCodec.getOutputFormat
     */
    public override fun getOutputFormat(): Format {
        var outputFormat = super.getOutputFormat()
        if (outputFormat != null && outputFormat.javaClass == AudioFormat::class.java) {
            val outputAudioFormat = outputFormat as AudioFormat
            outputFormat = setOutputFormat(
                    object : AudioFormat(
                            outputAudioFormat.encoding,
                            outputAudioFormat.sampleRate,
                            outputAudioFormat.sampleSizeInBits,
                            outputAudioFormat.channels,
                            outputAudioFormat.endian,
                            outputAudioFormat.signed,
                            outputAudioFormat.frameSizeInBits,
                            outputAudioFormat.frameRate,
                            outputAudioFormat.dataType) {
                        val serialVersionUID = 0L
                        override fun computeDuration(length: Long): Long {
                            return duration
                        }
                    })
        }
        return outputFormat
    }

    /**
     * Sets the input format.
     *
     * @param format format to set
     * @return format
     * @see AbstractCodec2.setInputFormat
     */
    override fun setInputFormat(format: Format): Format? {
        val inputFormat = super.setInputFormat(format)
        if (inputFormat != null) {
            val outputSampleRate: Double
            val outputChannels: Int
            if (outputFormat == null) {
                outputSampleRate = Format.NOT_SPECIFIED.toDouble()
                outputChannels = Format.NOT_SPECIFIED
            } else {
                val outputAudioFormat = outputFormat as AudioFormat
                outputSampleRate = outputAudioFormat.sampleRate
                outputChannels = outputAudioFormat.channels
            }
            val inputAudioFormat = inputFormat as AudioFormat
            val inputSampleRate = inputAudioFormat.sampleRate
            val inputChannels = inputAudioFormat.channels
            if (outputSampleRate != inputSampleRate || outputChannels != inputChannels) {
                setOutputFormat(
                        AudioFormat(
                                Constants.SPEEX_RTP,
                                inputSampleRate,
                                Format.NOT_SPECIFIED,
                                inputChannels,
                                AudioFormat.LITTLE_ENDIAN,
                                AudioFormat.SIGNED,
                                Format.NOT_SPECIFIED,
                                Format.NOT_SPECIFIED.toDouble(),
                                Format.byteArray))
            }
        }
        return inputFormat
    }

    companion object {
        /**
         * The list of `Format`s of audio data supported as input by `JNIEncoder` instances.
         */
        private val SUPPORTED_INPUT_FORMATS: Array<Format>

        /**
         * The list of sample rates of audio data supported as input by `JNIEncoder` instances.
         */
        val SUPPORTED_INPUT_SAMPLE_RATES = doubleArrayOf(8000.0, 16000.0, 32000.0)

        /**
         * The list of `Format`s of audio data supported as output by `JNIEncoder` instances.
         */
        private val SUPPORTED_OUTPUT_FORMATS = arrayOf<Format>(AudioFormat(Constants.SPEEX_RTP))

        init {
            Speex.assertSpeexIsFunctional()
            val supportedInputCount = SUPPORTED_INPUT_SAMPLE_RATES.size
            val SUPPORTED_INPUT_FORMATS = ArrayList<Format>()

            for (i in 0 until supportedInputCount) {
                SUPPORTED_INPUT_FORMATS.add(AudioFormat(
                        AudioFormat.LINEAR,
                        SUPPORTED_INPUT_SAMPLE_RATES[i],
                        16,
                        1,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED,
                        Format.NOT_SPECIFIED.toDouble(),
                        Format.byteArray)
                )
            }
            this.SUPPORTED_INPUT_FORMATS = SUPPORTED_INPUT_FORMATS.toTypedArray()
        }
    }
}