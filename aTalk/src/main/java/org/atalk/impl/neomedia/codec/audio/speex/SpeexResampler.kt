/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.speex

import org.atalk.impl.neomedia.codec.AbstractCodec2
import javax.media.Buffer
import javax.media.Format
import javax.media.PlugIn
import javax.media.ResourceUnavailableException
import javax.media.format.AudioFormat

/**
 * Implements an audio resampler using Speex.
 *
 * @author Lyubomir Marinov
 */
class SpeexResampler : AbstractCodec2("Speex Resampler", AudioFormat::class.java, SUPPORTED_FORMATS) {
    /**
     * The number of channels with which [.resampler] has been initialized.
     */
    private var channels = 0

    /**
     * The input sample rate configured in [.resampler].
     */
    private var inputSampleRate = 0

    /**
     * The output sample rate configured in [.resampler].
     */
    private var outputSampleRate = 0

    /**
     * The pointer to the native `SpeexResamplerState` which is represented by this instance.
     */
    private var resampler: Long = 0

    /**
     * Initializes a new `SpeexResampler` instance.
     */
    init {
        inputFormats = SUPPORTED_FORMATS
    }

    /**
     * @see AbstractCodec2.doClose
     */
    override fun doClose() {
        if (resampler != 0L) {
            Speex.speex_resampler_destroy(resampler)
            resampler = 0
        }
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
    }

    /**
     * Resamples audio from a specific input `Buffer` into a specific output `Buffer`.
     *
     * @param inBuffer input `Buffer`
     * @param outBuffer output `Buffer`
     * @return `BUFFER_PROCESSED_OK` if `inBuffer` has been successfully processed
     * @see AbstractCodec2.doProcess
     */
    override fun doProcess(inBuffer: Buffer, outBuffer: Buffer): Int {
        var inFormat = inBuffer.format
        if (inFormat != null
                && inFormat != inputFormat
                && inFormat != inputFormat) {
            if (null == setInputFormat(inFormat))
                return BUFFER_PROCESSED_FAILED
        }
        inFormat = inputFormat
        val inAudioFormat = inFormat as AudioFormat
        val inSampleRate = inAudioFormat.sampleRate.toInt()
        val outAudioFormat = getOutputFormat() as AudioFormat
        val outSampleRate = outAudioFormat.sampleRate.toInt()
        if (inSampleRate == outSampleRate) {
            // passthrough
            val inDataType = inAudioFormat.dataType
            val outDataType = outAudioFormat.dataType
            if (Format.byteArray == inDataType) {
                val input = inBuffer.data as ByteArray
                if (Format.byteArray == outDataType) {
                    val length = input?.size ?: 0
                    val output = validateByteArraySize(outBuffer, length, false)
                    if (input != null && output != null) System.arraycopy(input, 0, output, 0, length)
                    outBuffer.format = inBuffer.format
                    outBuffer.length = inBuffer.length
                    outBuffer.offset = inBuffer.offset
                }
                else {
                    val inLength = inBuffer.length
                    val outOffset = 0
                    val outLength = inLength / 2
                    val output = validateShortArraySize(outBuffer, outLength)
                    var i = inBuffer.offset
                    var o = outOffset
                    while (o < outLength) {
                        output[o] = (input[i++].toInt() and 0xFF or (input[i++].toInt() and 0xFF shl 8)).toShort()
                        o++
                    }
                    outBuffer.format = outAudioFormat
                    outBuffer.length = outLength
                    outBuffer.offset = outOffset
                }
            }
            else {
                val input = inBuffer.data as ShortArray
                if (Format.byteArray == outDataType) {
                    val inLength = inBuffer.length
                    val outOffset = 0
                    val outLength = inLength * 2
                    val output = validateByteArraySize(outBuffer, outLength, false)
                    var i = inBuffer.offset
                    var o = outOffset
                    while (o < outLength) {
                        val s = input[i]
                        output[o++] = (s.toInt() and 0x00FF).toByte()
                        output[o++] = (s.toInt() and 0xFF00 ushr 8).toByte()
                        i++
                    }
                    outBuffer.format = outAudioFormat
                    outBuffer.length = outLength
                    outBuffer.offset = outOffset
                }
                else {
                    val length = input?.size ?: 0
                    val output = validateShortArraySize(outBuffer, length)
                    if (input != null && output != null) System.arraycopy(input, 0, output, 0, length)
                    outBuffer.format = inBuffer.format
                    outBuffer.length = inBuffer.length
                    outBuffer.offset = inBuffer.offset
                }
            }
        }
        else {
            val channels = inAudioFormat.channels
            if (outAudioFormat.channels != channels) return BUFFER_PROCESSED_FAILED
            val channelsHaveChanged = this.channels != channels
            if (channelsHaveChanged || inputSampleRate != inSampleRate || outputSampleRate != outSampleRate) {
                if (channelsHaveChanged && resampler != 0L) {
                    Speex.speex_resampler_destroy(resampler)
                    resampler = 0
                }
                if (resampler == 0L) {
                    resampler = Speex.speex_resampler_init(channels, inSampleRate, outSampleRate,
                            Speex.SPEEX_RESAMPLER_QUALITY_VOIP, 0)
                }
                else {
                    Speex.speex_resampler_set_rate(resampler, inSampleRate, outSampleRate)
                }
                if (resampler != 0L) {
                    inputSampleRate = inSampleRate
                    outputSampleRate = outSampleRate
                    this.channels = channels
                }
            }
            if (resampler == 0L) return BUFFER_PROCESSED_FAILED
            val `in` = inBuffer.data as ByteArray
            var inLength = inBuffer.length
            val frameSize = channels * (inAudioFormat.sampleSizeInBits / 8)
            /*
             * XXX The numbers of input and output samples which are to be specified to the
             * function speex_resampler_process_interleaved_int are per-channel.
             */
            val inSampleCount = inLength / frameSize
            var outSampleCount = inSampleCount * outSampleRate / inSampleRate
            val outOffset = outBuffer.offset
            val out = validateByteArraySize(outBuffer, outSampleCount * frameSize + outOffset,
                    outOffset != 0)

            /*
             * XXX The method Speex.speex_resampler_process_interleaved_int will crash if in is null.
             */
            if (inSampleCount == 0) {
                outSampleCount = 0
            }
            else {
                val inOffset = inBuffer.offset
                outSampleCount = Speex.speex_resampler_process_interleaved_int(
                        resampler, `in`, inOffset, inSampleCount, out, outOffset, outSampleCount)

                /*
                 * Report how many bytes of inBuffer have been consumed in the sample rate conversion.
                 */
                val resampled = inSampleCount * frameSize
                inLength -= resampled
                if (inLength < 0) inLength = 0
                inBuffer.length = inLength
                inBuffer.offset = inOffset + resampled
            }
            outBuffer.format = outAudioFormat
            outBuffer.length = outSampleCount * frameSize
            outBuffer.offset = outOffset
        }
        outBuffer.duration = inBuffer.duration
        outBuffer.isEOM = inBuffer.isEOM
        outBuffer.flags = inBuffer.flags
        outBuffer.header = inBuffer.header
        outBuffer.sequenceNumber = inBuffer.sequenceNumber
        outBuffer.timeStamp = inBuffer.timeStamp
        return BUFFER_PROCESSED_OK
    }

    /**
     * Get the output formats matching a specific input format.
     *
     * @param inputFormat the input format to get the matching output formats of
     * @return the output formats matching the specified input format
     * @see AbstractCodec2.getMatchingOutputFormats
     */
    override fun getMatchingOutputFormats(inputFormat: Format): Array<Format> {
        val inDataType = inputFormat.dataType
        val matchingOutputFormats = ArrayList<Format>()

        if (inputFormat is AudioFormat) {
            val inChannels = inputFormat.channels
            val inSampleRate = inputFormat.sampleRate
            for (supportedFormat in SUPPORTED_FORMATS) {
                val supportedAudioFormat = supportedFormat as AudioFormat?
                if (supportedAudioFormat!!.channels != inChannels) continue
                if (Format.byteArray == supportedFormat.dataType && Format.byteArray == inDataType || supportedAudioFormat.sampleRate == inSampleRate) {
                    matchingOutputFormats.add(supportedFormat)
                }
            }
        }
        return matchingOutputFormats.toTypedArray()
    }

    /**
     * Sets the `Format` of the media data to be input for processing in this `Codec`.
     *
     * @param format the `Format` of the media data to be input for processing in this `Codec`
     * @return the `Format` of the media data to be input for processing in this
     * `Codec` if `format` is compatible with this `Codec`; otherwise, `null`
     * @see AbstractCodec2.setInputFormat
     */
    override fun setInputFormat(format: Format): Format? {
        val inFormat = super.setInputFormat(format) as AudioFormat?
        if (inFormat != null) {
            val outSampleRate: Double
            var outDataType: Class<*>?
            if (outputFormat == null) {
                outSampleRate = inFormat.sampleRate
                outDataType = inFormat.dataType
            }
            else {
                val outAudioFormat = outputFormat as AudioFormat
                outSampleRate = outAudioFormat.sampleRate
                outDataType = outAudioFormat.dataType
                /*
                 * Conversion between data types is only supported when not resampling but rather passing through.
                 */
                if (outSampleRate != inFormat.sampleRate) outDataType = inFormat.dataType
            }
            setOutputFormat(
                    AudioFormat(
                            inFormat.encoding,
                            outSampleRate,
                            inFormat.sampleSizeInBits,
                            inFormat.channels,
                            inFormat.endian,
                            inFormat.signed,
                            Format.NOT_SPECIFIED,
                            Format.NOT_SPECIFIED.toDouble(),
                            outDataType
                    )
            )
        }
        return inFormat
    }

    companion object {
        /**
         * The list of `Format`s of audio data supported as input and output by `SpeexResampler` instances.
         */
        private val SUPPORTED_FORMATS: Array<Format>

        /**
         * The list of sample rates of audio data supported as input and output by `SpeexResampler` instances.
         */
        private val SUPPORTED_SAMPLE_RATES = doubleArrayOf(
                8000.0,
                11025.0,
                12000.0,
                16000.0,
                22050.0,
                24000.0,
                32000.0,
                44100.0,
                48000.0,
                Format.NOT_SPECIFIED.toDouble()
        )

        init {
            Speex.assertSpeexIsFunctional()
            val supportedCount = SUPPORTED_SAMPLE_RATES.size
            val SUPPORTED_FORMATS = ArrayList<Format>()

            for (i in 0 until supportedCount) {
                SUPPORTED_FORMATS.add(AudioFormat(
                        AudioFormat.LINEAR,
                        SUPPORTED_SAMPLE_RATES[i],
                        16 /* sampleSizeInBits */,
                        1 /* channels */,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED.toDouble() /* frameRate */,
                        Format.byteArray)
                )

                SUPPORTED_FORMATS.add(AudioFormat(
                        AudioFormat.LINEAR,
                        SUPPORTED_SAMPLE_RATES[i],
                        16 /* sampleSizeInBits */,
                        1 /* channels */,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED.toDouble() /* frameRate */,
                        Format.shortArray)
                )

                SUPPORTED_FORMATS.add(AudioFormat(
                        AudioFormat.LINEAR,
                        SUPPORTED_SAMPLE_RATES[i],
                        16 /* sampleSizeInBits */,
                        2 /* channels */,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED.toDouble() /* frameRate */,
                        Format.byteArray)
                )

                SUPPORTED_FORMATS.add(AudioFormat(
                        AudioFormat.LINEAR,
                        SUPPORTED_SAMPLE_RATES[i],
                        16 /* sampleSizeInBits */,
                        2 /* channels */,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED.toDouble() /* frameRate */,
                        Format.shortArray)
                )
            }

            this.SUPPORTED_FORMATS = SUPPORTED_FORMATS.toTypedArray()
        }
    }
}