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

/**
 * Implements a Speex decoder and RTP depacketizer using the native Speex library.
 *
 * @author Lubomir Marinov
 */
class JNIDecoder : AbstractCodec2("Speex JNI Decoder", AudioFormat::class.java, SUPPORTED_OUTPUT_FORMATS) {
    /**
     * The pointer to the native `SpeexBits` from which the native Speex decoder (i.e.
     * [.state]) reads the encoded audio data.
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
     * The sample rate configured into [.state].
     */
    private var sampleRate = 0

    /**
     * The native Speex decoder represented by this instance.
     */
    private var state: Long = 0

    /**
     * Initializes a new `JNIDecoder` instance.
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
            Speex.speex_decoder_destroy(state)
            state = 0
            sampleRate = 0
            frameSize = 0
            duration = 0
        }
        // bits
        Speex.speex_bits_destroy(bits)
        bits = 0
    }

    /**
     * Opens this `Codec` and acquires the resources that it needs to operate. A call to
     * [PlugIn.open] on this instance will result in a call to `doOpen` only if
     * AbstractCodec.opened is `false`. All required input and/or output formats are
     * assumed to have been set on this `Codec` before `doOpen` is called.
     *
     * @throws ResourceUnavailableException if any of the resources that this `Codec` needs
     * to operate cannot be acquired
     * @see AbstractCodec2.doOpen
     */
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        bits = Speex.speex_bits_init()
        if (bits == 0L) throw ResourceUnavailableException("speex_bits_init")
    }

    /**
     * Decodes Speex media from a specific input `Buffer`
     *
     * @param inBuf input `Buffer`
     * @param outBuf output `Buffer`
     * @return `BUFFER_PROCESSED_OK` if `inBuffer` has been successfully processed
     * @see AbstractCodec2.doProcess
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        var inputFormat = inBuf.format
        if ((inputFormat != null) && (inputFormat != this.inputFormat)
                && !(inputFormat == this.inputFormat)) {
            if (null == setInputFormat(inputFormat)) return BUFFER_PROCESSED_FAILED
        }
        inputFormat = this.inputFormat

        /*
         * Make sure that the native Speex decoder which is represented by this instance is
         * configured to work with the inputFormat.
         */
        val inputAudioFormat = inputFormat as AudioFormat
        val inputSampleRate = inputAudioFormat.sampleRate.toInt()
        if (state != 0L && sampleRate != inputSampleRate) {
            Speex.speex_decoder_destroy(state)
            state = 0
            sampleRate = 0
            frameSize = 0
        }
        if (state == 0L) {
            val mode = Speex.speex_lib_get_mode(if (inputSampleRate == 16000) Speex.SPEEX_MODEID_WB else if (inputSampleRate == 32000) Speex.SPEEX_MODEID_UWB else Speex.SPEEX_MODEID_NB)
            if (mode == 0L) return BUFFER_PROCESSED_FAILED
            state = Speex.speex_decoder_init(mode)
            if (state == 0L) return BUFFER_PROCESSED_FAILED
            if (Speex.speex_decoder_ctl(state, Speex.SPEEX_SET_ENH, 1) != 0) return BUFFER_PROCESSED_FAILED
            if (Speex.speex_decoder_ctl(state, Speex.SPEEX_SET_SAMPLING_RATE, inputSampleRate) != 0) return BUFFER_PROCESSED_FAILED
            val frameSize = Speex.speex_decoder_ctl(state, Speex.SPEEX_GET_FRAME_SIZE)
            if (frameSize < 0) return BUFFER_PROCESSED_FAILED
            sampleRate = inputSampleRate
            this.frameSize = frameSize * 2 /* (sampleSizeInBits / 8) */
            duration = (frameSize * 1000 * 1000000 / sampleRate).toLong()
        }

        /* Read the encoded audio data from inputBuffer into the SpeexBits. */
        var inputLength = inBuf.length
        if (inputLength > 0) {
            val input = inBuf.data as ByteArray
            val inputOffset = inBuf.offset
            Speex.speex_bits_read_from(bits, input, inputOffset, inputLength)
            inputLength = 0
            inBuf.length = inputLength
            inBuf.offset = inputOffset + inputLength
        }

        /* At long last, do the actual decoding. */
        val outputLength = frameSize
        val inputBufferNotConsumed: Boolean
        if (outputLength > 0) {
            val output = validateByteArraySize(outBuf, outputLength, false)
            if (0 == Speex.speex_decode_int(state, bits, output, 0)) {
                outBuf.duration = duration
                outBuf.format = getOutputFormat()
                outBuf.length = outputLength
                outBuf.offset = 0
                inputBufferNotConsumed = Speex.speex_bits_remaining(bits) > 0
            } else {
                outBuf.length = 0
                discardOutputBuffer(outBuf)
                inputBufferNotConsumed = false
            }
        } else {
            outBuf.length = 0
            discardOutputBuffer(outBuf)
            inputBufferNotConsumed = false
        }
        return if (inputLength < 1 && !inputBufferNotConsumed) BUFFER_PROCESSED_OK else BUFFER_PROCESSED_OK or INPUT_BUFFER_NOT_CONSUMED
    }

    /**
     * Get all supported output `Format`s.
     *
     * @param inputFormat input `Format` to determine corresponding output `Format/code>s
     * @return array of supported `Format`
     * @see AbstractCodec2.getMatchingOutputFormats
    ` */
    override fun getMatchingOutputFormats(inputFormat: Format): Array<Format> {
        return arrayOf(
                AudioFormat(
                        AudioFormat.LINEAR,
                        (inputFormat as AudioFormat).sampleRate,
                        16,
                        1,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED,
                        Format.NOT_SPECIFIED.toDouble(),
                        Format.byteArray)
        )
    }

    /**
     * Sets the `Format` of the media data to be input for processing in this `Codec`.
     *
     * @param format the `Format` of the media data to be input for processing in this `Codec`
     * @return the `Format` of the media data to be input for processing in this
     * `Codec` if `format` is compatible with this `Codec`; otherwise, `null`
     * @see AbstractCodec2.setInputFormat
     */
    override fun setInputFormat(format: Format): Format {
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
                                AudioFormat.LINEAR,
                                inputSampleRate,
                                16,
                                inputChannels,
                                AudioFormat.LITTLE_ENDIAN,
                                AudioFormat.SIGNED,
                                Format.NOT_SPECIFIED,
                                Format.NOT_SPECIFIED.toDouble(),
                                Format.byteArray))
            }
        }
        return inputFormat!!
    }

    companion object {
        /**
         * The list of `Format`s of audio data supported as input by `JNIDecoder` instances.
         */
        private val SUPPORTED_INPUT_FORMATS: Array<Format>

        /**
         * The list of `Format`s of audio data supported as output by `JNIDecoder` instances.
         */
        private val SUPPORTED_OUTPUT_FORMATS = arrayOf<Format>(
                AudioFormat(
                        AudioFormat.LINEAR,
                        Format.NOT_SPECIFIED.toDouble(),
                        16,
                        1,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED,
                        Format.NOT_SPECIFIED.toDouble(),
                        Format.byteArray)
        )

        init {
            Speex.assertSpeexIsFunctional()
            val supportedInputSampleRates = JNIEncoder.SUPPORTED_INPUT_SAMPLE_RATES
            val supportedInputCount = supportedInputSampleRates.size
            val SUPPORTED_INPUT_FORMATS = ArrayList<Format>()

            for (i in 0 until supportedInputCount) {
                SUPPORTED_INPUT_FORMATS.add(AudioFormat(
                        Constants.SPEEX_RTP,
                        supportedInputSampleRates[i],
                        Format.NOT_SPECIFIED,
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