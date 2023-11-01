/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.impl.neomedia.codec.audio.silk.JavaDecoder.Companion.FRAME_DURATION
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.codec.Constants
import org.atalk.service.neomedia.control.PacketLossAwareEncoder
import timber.log.Timber
import java.awt.Component
import javax.media.Buffer
import javax.media.Format
import javax.media.PlugIn
import javax.media.ResourceUnavailableException
import javax.media.format.AudioFormat

/**
 * Implements the SILK encoder as an FMJ/JMF `Codec`.
 *
 * @author Dingxin Xu
 * @author Boris Grozev
 * @author Boris Grozev
 */
class JavaEncoder : AbstractCodec2("SILK Encoder", AudioFormat::class.java, SUPPORTED_OUTPUT_FORMATS), PacketLossAwareEncoder {
    /**
     * Whether to use FEC or not.
     */
    private val useFec: Boolean

    /**
     * Whether to always assume packet loss and set the encoder's expected packet loss over
     * `MIN_PACKET_LOSS_PERCENTAGE`.
     */
    private var alwaysAssumePacketLoss = true

    /**
     * The duration an output `Buffer` produced by this `Codec` in nanosecond.
     */
    private val duration = FRAME_DURATION * 1000000

    /**
     * The SILK encoder control (structure).
     */
    private var encControl: SKP_SILK_SDK_EncControlStruct? = null

    /**
     * The SILK encoder state.
     */
    private var encState: SKP_Silk_encoder_state_FLP? = null

    /**
     * The length of an output payload as reported by
     * Silk_enc_API.SKP_Silk_SDK_Encode
     * .
     */
    private val outputLength = ShortArray(1)

    /**
     * Initializes a new `JavaEncoder` instance.
     */
    init {
        inputFormats = SUPPORTED_INPUT_FORMATS
        val cfg = LibJitsi.configurationService
        // TODO: we should have a default value dependent on the SDP parameters
        // here.
        useFec = cfg.getBoolean(Constants.PROP_SILK_FEC, true)
        alwaysAssumePacketLoss = cfg.getBoolean(Constants.PROP_SILK_ASSUME_PL, true)

        // Update the statically defined value for "speech activity threshold"
        // according to our configuration
        val satStr = cfg.getString(Constants.PROP_SILK_FEC_SAT, "0.5")
        var sat = DefineFLP.LBRR_SPEECH_ACTIVITY_THRES
        if (satStr != null && satStr.isNotEmpty()) {
            try {
                sat = satStr.toFloat()
            } catch (ignore: NumberFormatException) {
            }
        }
        DefineFLP.LBRR_SPEECH_ACTIVITY_THRES = sat
        addControl(this)
    }

    override fun doClose() {
        encState = null
        encControl = null
    }

    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        encState = SKP_Silk_encoder_state_FLP()
        encControl = SKP_SILK_SDK_EncControlStruct()
        if (EncAPI.SKP_Silk_SDK_InitEncoder(encState!!, encControl!!) != 0) {
            throw ResourceUnavailableException("EncAPI.SKP_Silk_SDK_InitEncoder")
        }
        val inputFormat = getInputFormat() as AudioFormat
        val sampleRate = inputFormat.sampleRate
        val channels = inputFormat.channels
        encControl!!.API_sampleRate = sampleRate.toInt()
        encControl!!.bitRate = BITRATE
        encControl!!.complexity = COMPLEXITY
        encControl!!.maxInternalSampleRate = encControl!!.API_sampleRate
        setExpectedPacketLoss(0)
        encControl!!.packetSize = (FRAME_DURATION * sampleRate * channels / 1000).toInt()
        encControl!!.useDTX = if (USE_DTX) 1 else 0
        encControl!!.useInBandFEC = if (useFec) 1 else 0
    }

    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        val inputData = inBuf.data as ShortArray
        var inputLength = inBuf.length
        val inputOffset = inBuf.offset
        if (inputLength > encControl!!.packetSize) inputLength = encControl!!.packetSize
        val outputData = validateByteArraySize(outBuf, MAX_BYTES_PER_FRAME, false)
        val outputOffset = 0
        var processed: Int
        outputLength[0] = MAX_BYTES_PER_FRAME.toShort()
        if (EncAPI.SKP_Silk_SDK_Encode(encState, encControl, inputData, inputOffset, inputLength,
                        outputData, outputOffset, outputLength) == 0) {
            outBuf.length = outputLength[0].toInt()
            outBuf.offset = outputOffset
            processed = PlugIn.BUFFER_PROCESSED_OK
        }
        else processed = PlugIn.BUFFER_PROCESSED_FAILED

        inBuf.length = inBuf.length - inputLength
        inBuf.offset = inBuf.offset + inputLength
        if (processed != PlugIn.BUFFER_PROCESSED_FAILED) {
            if (PlugIn.BUFFER_PROCESSED_OK == processed) {
                updateOutput(outBuf, outputFormat, outBuf.length, outBuf.offset)
                outBuf.duration = duration.toLong()
            }

            if (inBuf.length > 0) processed = processed or PlugIn.INPUT_BUFFER_NOT_CONSUMED
        }
        return processed
    }

    /**
     * Get the output formats matching a specific input format.
     *
     * @param inputFormat the input format to get the matching output formats of
     * @return the output formats matching the specified input format
     * @see AbstractCodecExt.matchingOutputFormats
     */
    override fun getMatchingOutputFormats(inputFormat: Format): Array<Format> {
        return getMatchingOutputFormats(inputFormat, SUPPORTED_INPUT_FORMATS, SUPPORTED_OUTPUT_FORMATS)
    }

    /**
     * Get the output format.
     *
     * @return output format
     * @see net.sf.fmj.media.AbstractCodec.getOutputFormat
     */
    override fun getOutputFormat(): Format? {
        var outputFormat = super.getOutputFormat()
        if (outputFormat != null && outputFormat.javaClass == AudioFormat::class.java) {
            val outputAudioFormat = outputFormat as AudioFormat
            outputFormat = setOutputFormat(object : AudioFormat(
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
                    return duration.toLong()
                }
            })
        }
        return outputFormat
    }

    /**
     * Updates the encoder's packet loss percentage. Takes into account `this.alwaysAssumePacketLoss`.
     *
     * @param percentage the expected packet loss percentage to set.
     */
    override fun setExpectedPacketLoss(percentage: Int) {
        var percentage = percentage
        if (opened) {
            if (alwaysAssumePacketLoss && MIN_PACKET_LOSS_PERCENTAGE >= percentage) percentage = MIN_PACKET_LOSS_PERCENTAGE
            encControl!!.packetLossPercentage = percentage
            Timber.d("Setting expected packet loss to: %s", percentage)
        }
    }

    override fun getControlComponent(): Component? {
        return null
    }

    companion object {
        private const val BITRATE = 40000
        private const val COMPLEXITY = 2

        /**
         * The maximum number of output payload bytes per input frame. Equals peak bitrate of 100 kbps.
         */
        const val MAX_BYTES_PER_FRAME = 250

        /**
         * The list of `Format`s of audio data supported as input by `JavaEncoder` instances.
         */
        val SUPPORTED_INPUT_FORMATS: Array<Format>

        /**
         * The list of `Format`s of audio data supported as output by `JavaEncoder` instances.
         */
        val SUPPORTED_OUTPUT_FORMATS: Array<Format>

        /**
         * The list of sample rates of audio data supported as input and output by `JavaEncoder`
         * instances.
         */
        private val SUPPORTED_SAMPLE_RATES = doubleArrayOf(8000.0, 12000.0, 16000.0, 24000.0)

        /**
         * Default value for the use DTX setting
         */
        private const val USE_DTX = false

        /**
         * If `alwaysExpectPacketLoss` is `true` the expected packet loss will always be
         * set at or above this threshold.
         */
        private const val MIN_PACKET_LOSS_PERCENTAGE = 3

        init {
            val supportedCount = SUPPORTED_SAMPLE_RATES.size
            val SUPPORTED_INPUT_FORMATS = ArrayList<Format>()
            val SUPPORTED_OUTPUT_FORMATS = ArrayList<Format>()

            for (i in 0 until supportedCount) {
                val supportedSampleRate = SUPPORTED_SAMPLE_RATES[i]

                SUPPORTED_INPUT_FORMATS.add(AudioFormat(
                        AudioFormat.LINEAR,
                        supportedSampleRate,
                        16,
                        1,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED.toDouble() /* frameRate */,
                        Format.shortArray)
                )

                SUPPORTED_OUTPUT_FORMATS.add(AudioFormat(
                        Constants.SILK_RTP,
                        supportedSampleRate,
                        Format.NOT_SPECIFIED /* sampleSizeInBits */,
                        1,
                        Format.NOT_SPECIFIED /* endian */,
                        Format.NOT_SPECIFIED /* signed */,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED.toDouble() /* frameRate */,
                        Format.byteArray)
                )
            }

            this.SUPPORTED_INPUT_FORMATS = SUPPORTED_INPUT_FORMATS.toTypedArray()
            this.SUPPORTED_OUTPUT_FORMATS = SUPPORTED_OUTPUT_FORMATS.toTypedArray()
        }

        fun getMatchingOutputFormats(
                inputFormat: Format?, supportedInputFormats: Array<Format>,
                supportedOutputFormats: Array<Format>,
        ): Array<Format> {

            return if (inputFormat == null) {
                supportedOutputFormats
            }
            else {
                val matchingInputFormat = matches(inputFormat, supportedInputFormats)

                if (matchingInputFormat == null) {
                    arrayOf()
                }
                else {
                    val matchingInputAudioFormat = matchingInputFormat.intersects(inputFormat) as AudioFormat
                    val outputFormat: Format = AudioFormat(
                            null /* encoding */,
                            matchingInputAudioFormat.sampleRate,
                            Format.NOT_SPECIFIED /* sampleSizeInBits */,
                            Format.NOT_SPECIFIED /* channels */,
                            Format.NOT_SPECIFIED /* endian */,
                            Format.NOT_SPECIFIED /* signed */,
                            Format.NOT_SPECIFIED /* frameSizeInBits */,
                            Format.NOT_SPECIFIED.toDouble() /* frameRate */,
                            null)

                    val matchingOutputFormat = matches(outputFormat, supportedOutputFormats)
                    if (matchingOutputFormat == null) arrayOf() else arrayOf(matchingOutputFormat.intersects(outputFormat))
                }
            }
        }
    }
}