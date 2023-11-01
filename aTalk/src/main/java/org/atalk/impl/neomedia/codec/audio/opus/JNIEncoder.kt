/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.opus

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.impl.neomedia.jmfext.media.renderer.audio.AbstractAudioRenderer
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.codec.Constants
import org.atalk.service.neomedia.control.FormatParametersAwareCodec
import org.atalk.service.neomedia.control.PacketLossAwareEncoder
import timber.log.Timber
import java.awt.Component
import javax.media.Buffer
import javax.media.Format
import javax.media.PlugIn
import javax.media.ResourceUnavailableException
import javax.media.format.AudioFormat
import kotlin.math.max
import kotlin.math.min

/**
 * Implements an Opus encoder.
 *
 * @author Boris Grozev
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class JNIEncoder : AbstractCodec2("Opus JNI Encoder", AudioFormat::class.java, SUPPORTED_OUTPUT_FORMATS), FormatParametersAwareCodec, PacketLossAwareEncoder {
    /**
     * Codec audio bandwidth, obtained from configuration.
     */
    private var bandwidth = 0

    /**
     * The bitrate in bits per second obtained from the configuration and set on [.encoder].
     */
    private var bitrate = 0

    /**
     * Number of channels to use, default to 1.
     */
    private var channels = 1

    /**
     * Complexity setting, obtained from configuration.
     */
    private var complexity = 0

    /**
     * The pointer to the native OpusEncoder structure
     */
    private var encoder: Long = 0

    /**
     * The size in bytes of an audio frame input by this instance. Automatically calculated, based
     * on [.frameSizeInMillis] and the `inputFormat` of this instance.
     */
    private var frameSizeInBytes = 0

    /**
     * The size/duration in milliseconds of an audio frame output by this instance. The possible
     * values are: 2.5, 5, 10, 20, 40 and 60. The default value is 20.
     */
    private val frameSizeInMillis = 20

    /**
     * The size in samples per channel of an audio frame input by this instance. Automatically
     * calculated, based on [.frameSizeInMillis] and the `inputFormat` of this instance.
     */
    private var frameSizeInSamplesPerChannel = 0

    /**
     * The minimum expected packet loss percentage to set to the encoder.
     */
    private var minPacketLoss = 0

    /**
     * The bytes from an input `Buffer` from a previous call to
     * [.process] that this `Codec` didn't process because the total
     * number of bytes was less than [.inputFrameSize] need to be prepended to a subsequent
     * input `Buffer` in order to process a total of [.inputFrameSize] bytes.
     */
    private var prevIn: ByteArray? = null

    /**
     * The length of the audio data in [.prevIn].
     */
    private var prevInLength = 0

    /**
     * Whether to use DTX, obtained from configuration.
     */
    private var useDtx = false

    /**
     * Whether to use FEC, obtained from configuration.
     */
    private var useFec = false

    /**
     * Whether to use VBR, obtained from configuration.
     */
    private var useVbr = false

    /**
     * Initializes a new `JNIEncoder` instance.
     */
    init {
        inputFormats = SUPPORTED_INPUT_FORMATS
        addControl(this)
    }

    /**
     * {@inheritDoc}
     *
     * @see AbstractCodec2.doClose
     */
    override fun doClose() {
        if (encoder != 0L) {
            Opus.encoder_destroy(encoder)
            encoder = 0
        }
    }

    /**
     * Opens this `Codec` and acquires the resources that it needs to operate. A call to
     * [PlugIn.open] on this instance will result in a call to `doOpen` only if
     * AbstractCodec.opened is `false`. All required input and/or output formats are
     * assumed to have been set on this `Codec` before `doOpen` is called.
     *
     * @throws ResourceUnavailableException if any of the resources that this `Codec` needs to operate cannot be acquired
     * @see AbstractCodec2.doOpen
     */
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        val inputFormat = getInputFormat() as AudioFormat
        val sampleRate = inputFormat.sampleRate.toInt()
        channels = inputFormat.channels
        encoder = Opus.encoder_create(sampleRate, channels)
        if (encoder == 0L) throw ResourceUnavailableException("opus_encoder_create()")

        // Set encoder options according to user configuration
        val cfg = LibJitsi.configurationService
        val bandwidthStr = cfg.getString(Constants.PROP_OPUS_BANDWIDTH, "auto")
        bandwidth = Opus.OPUS_AUTO
        when (bandwidthStr) {
            "fb" -> bandwidth = Opus.BANDWIDTH_FULLBAND
            "swb" -> bandwidth = Opus.BANDWIDTH_SUPERWIDEBAND
            "wb" -> bandwidth = Opus.BANDWIDTH_WIDEBAND
            "mb" -> bandwidth = Opus.BANDWIDTH_MEDIUMBAND
            "nb" -> bandwidth = Opus.BANDWIDTH_NARROWBAND
        }

        Opus.encoder_set_bandwidth(encoder, bandwidth)
        bitrate = (1000 /* configuration is in kilobits per second */
                * cfg.getInt(Constants.PROP_OPUS_BITRATE, 32))
        if (bitrate < 500) bitrate = 500 else if (bitrate > 512000) bitrate = 512000
        Opus.encoder_set_bitrate(encoder, bitrate)
        complexity = cfg.getInt(Constants.PROP_OPUS_COMPLEXITY, 0)
        if (complexity != 0) Opus.encoder_set_complexity(encoder, complexity)
        useFec = cfg.getBoolean(Constants.PROP_OPUS_FEC, true)
        Opus.encoder_set_inband_fec(encoder, if (useFec) 1 else 0)
        minPacketLoss = cfg.getInt(Constants.PROP_OPUS_MIN_EXPECTED_PACKET_LOSS, 1)
        Opus.encoder_set_packet_loss_perc(encoder, minPacketLoss)
        useDtx = cfg.getBoolean(Constants.PROP_OPUS_DTX, false)
        Opus.encoder_set_dtx(encoder, if (useDtx) 1 else 0)
        useVbr = cfg.getBoolean(Constants.PROP_OPUS_VBR, true)
        Opus.encoder_set_vbr(encoder, if (useVbr) 1 else 0)
        if (TimberLog.isTraceEnable) {
            val bw = when (Opus.encoder_get_bandwidth(encoder)) {
                Opus.BANDWIDTH_FULLBAND -> "fb"
                Opus.BANDWIDTH_SUPERWIDEBAND -> "swb"
                Opus.BANDWIDTH_WIDEBAND -> "wb"
                Opus.BANDWIDTH_MEDIUMBAND -> "mb"
                else -> "nb"
            }
            Timber.log(TimberLog.FINER, "Encoder settings: audio bandwidth %s, bitrate %s, DTX %s, FEC %s",
                    bw, Opus.encoder_get_bitrate(encoder), Opus.encoder_get_dtx(encoder), Opus.encoder_get_inband_fec(encoder))
        }
    }

    /**
     * Processes (i.e. encodes) a specific input `Buffer`.
     *
     * @param inBuf the `Buffer` from which the media to be encoded is to be read
     * @param outBuf the `Buffer` into which the encoded media is to be written
     * @return `BUFFER_PROCESSED_OK` if the specified `inBuffer` has been processed successfully
     * @see AbstractCodec2.doProcess
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        val inFormat = inBuf.format
        if (inFormat != null && inFormat != inputFormat
                && inFormat != inputFormat && null == setInputFormat(inFormat)) {
            return BUFFER_PROCESSED_FAILED
        }

        var `in` = inBuf.data as ByteArray
        var inLength = inBuf.length
        var inOffset = inBuf.offset
        if (prevIn != null && prevInLength > 0) {
            if (prevInLength < frameSizeInBytes) {
                if (prevIn!!.size < frameSizeInBytes) {
                    val newPrevIn = ByteArray(frameSizeInBytes)
                    System.arraycopy(prevIn as Any, 0, newPrevIn, 0, prevIn!!.size)
                    prevIn = newPrevIn
                }

                val bytesToCopyFromInToPrevIn = min(frameSizeInBytes - prevInLength, inLength)
                if (bytesToCopyFromInToPrevIn > 0) {
                    System.arraycopy(`in` as Any, inOffset, prevIn as Any, prevInLength, bytesToCopyFromInToPrevIn)
                    prevInLength += bytesToCopyFromInToPrevIn
                    inLength -= bytesToCopyFromInToPrevIn
                    inBuf.length = inLength
                    inBuf.offset = inOffset + bytesToCopyFromInToPrevIn
                }
            }

            if (prevInLength == frameSizeInBytes) {
                `in` = prevIn!!
                inOffset = 0
                prevInLength = 0
            }
            else {
                outBuf.length = 0
                discardOutputBuffer(outBuf)
                return if (inLength < 1) BUFFER_PROCESSED_OK else BUFFER_PROCESSED_OK or INPUT_BUFFER_NOT_CONSUMED
            }
        }
        else if (inLength < 1) {
            outBuf.length = 0
            discardOutputBuffer(outBuf)
            return BUFFER_PROCESSED_OK
        }
        else if (inLength < frameSizeInBytes) {
            if (prevIn == null || prevIn!!.size < inLength) prevIn = ByteArray(frameSizeInBytes)
            System.arraycopy(`in` as Any, inOffset, prevIn as Any, 0, inLength)
            prevInLength = inLength
            outBuf.length = 0
            discardOutputBuffer(outBuf)
            return BUFFER_PROCESSED_OK
        }
        else {
            inLength -= frameSizeInBytes
            inBuf.length = inLength
            inBuf.offset = inOffset + frameSizeInBytes
        }

        // At long last, do the actual encoding.
        val out = validateByteArraySize(outBuf, Opus.MAX_PACKET, false)
        val outLength = Opus.encode(encoder, `in`, inOffset, frameSizeInSamplesPerChannel, out, 0, out.size)
        if (outLength < 0) // error from opus_encode
            return BUFFER_PROCESSED_FAILED

        if (outLength > 0) {
            outBuf.duration = frameSizeInMillis.toLong() * 1000 * 1000
            outBuf.format = getOutputFormat()
            outBuf.length = outLength
            outBuf.offset = 0
            outBuf.headerExtension = inBuf.headerExtension
        }

        return if (inLength < 1) {
            BUFFER_PROCESSED_OK
        }
        else {
            BUFFER_PROCESSED_OK or INPUT_BUFFER_NOT_CONSUMED
        }
    }

    /**
     * Implements Control.getControlComponent. `JNIEncoder` does not provide user interface of its own.
     *
     * @return `null` to signify that `JNIEncoder` does not provide user interface of its own
     */
    override fun getControlComponent(): Component? {
        return null
    }

    /**
     * Gets the `Format` of the media output by this `Codec`.
     *
     * @return the `Format` of the media output by this `Codec`
     * @see net.sf.fmj.media.AbstractCodec.getOutputFormat
     */
    public override fun getOutputFormat(): Format {
        var f = super.getOutputFormat()
        if (f != null && f.javaClass == AudioFormat::class.java) {
            val af = f as AudioFormat
            f = setOutputFormat(object : AudioFormat(af.encoding, af.sampleRate,
                    af.sampleSizeInBits, af.channels, af.endian, af.signed,
                    af.frameSizeInBits, af.frameRate, af.dataType) {
                override fun computeDuration(length: Long): Long {
                    return frameSizeInMillis.toLong() * 1000 * 1000
                }
            })
        }
        return f
    }

    /**
     * Updates the encoder's expected packet loss percentage to the bigger of `percentage`
     * and `this.minPacketLoss`.
     *
     * @param percentage the expected packet loss percentage to set
     */
    override fun setExpectedPacketLoss(percentage: Int) {
        if (opened) {
            Opus.encoder_set_packet_loss_perc(encoder, max(percentage, minPacketLoss))
            Timber.log(TimberLog.FINER, "Updating expected packet loss: %s (minimum %s)", percentage, minPacketLoss)
        }
    }

    /**
     * Sets the format parameters.
     *
     * @param fmtps the format parameters to set
     */
    override fun setFormatParameters(fmtps: Map<String, String>) {
        Timber.d("Setting format parameters: %s", fmtps)

        /*
         * TODO Use the default value for maxaveragebitrate as defined at
         * https://tools.ietf.org/html/draft-spittka-payload-rtp-opus-02#section-6.1
         */
        var maxaveragebitrate = -1
        try {
            val s = fmtps["maxaveragebitrate"]
            if (s != null && s.isNotEmpty()) maxaveragebitrate = s.toInt()
        } catch (e: Exception) {
            // Ignore and fall back to the default value.
        }

        if (maxaveragebitrate > 0) {
            Opus.encoder_set_bitrate(encoder, min(maxaveragebitrate, bitrate))
        }

        // DTX is off unless specified.
        val useDtx = useDtx && "1" == fmtps["usedtx"]
        Opus.encoder_set_dtx(encoder, if (useDtx) 1 else 0)

        // FEC is on unless specified.
        var s: String?
        val useFec1 = useFec && (((fmtps["useinbandfec"].also { s = it } == null) || (s == "1")))
        Opus.encoder_set_inband_fec(encoder, if (useFec1) 1 else 0)
    }

    /**
     * {@inheritDoc}
     *
     * Automatically tracks and calculates the size in bytes of an audio frame (to be) output by
     * this instance.
     */
    override fun setInputFormat(format: Format): Format? {
        val oldValue = getInputFormat()
        val setInputFormat = super.setInputFormat(format)
        val newValue = getInputFormat()
        if (oldValue != newValue) {
            val af = newValue as AudioFormat
            val sampleRate = af.sampleRate.toInt()
            frameSizeInSamplesPerChannel = sampleRate * frameSizeInMillis / 1000
            frameSizeInBytes = (2 /* sizeof(opus_int16) */ * channels * frameSizeInSamplesPerChannel)
        }
        return setInputFormat
    }

    companion object {
        /**
         * The list of `Format`s of audio data supported as input by `JNIEncoder` instances.
         */
        private val SUPPORTED_INPUT_FORMATS: Array<Format>

        /**
         * The list of sample rates of audio data supported as input by `JNIEncoder` instances.
         *
         * The implementation does support 8, 12, 16, 24 and 48kHz but the lower sample rates are not
         * listed to prevent FMJ from defaulting to them.
         */
        private var SUPPORTED_INPUT_SAMPLE_RATES = doubleArrayOf(48000.0)

        /**
         * The list of `Format`s of audio data supported as output by `JNIEncoder` instances.
         */
        private val SUPPORTED_OUTPUT_FORMATS = arrayOf(
                AudioFormat(
                        Constants.OPUS_RTP,
                        48000.0,
                        Format.NOT_SPECIFIED,
                        2,  /* endian */
                        Format.NOT_SPECIFIED,  /* signed */
                        Format.NOT_SPECIFIED,  /* frameSizeInBits */
                        Format.NOT_SPECIFIED,  /* frameRate */
                        Format.NOT_SPECIFIED.toDouble(),
                        Format.byteArray)
        )

        /*
        * Sets the supported input formats.
        */
        init {
            /*
             * If the Opus class or its supporting JNI library are not functional, it is too late to
             * discover the fact in #doOpen() because a JNIEncoder instance has already been initialized
             * and it has already signaled that the Opus codec is supported.
             */
            Opus.assertOpusIsFunctional()
            val supportedInputCount = SUPPORTED_INPUT_SAMPLE_RATES.size
            val SUPPORTED_INPUT_FORMATS = ArrayList<Format>()

            for (i in 0 until supportedInputCount) {
                SUPPORTED_INPUT_FORMATS.add(AudioFormat(
                        AudioFormat.LINEAR,
                        SUPPORTED_INPUT_SAMPLE_RATES[i],
                        16,
                        1,
                        AbstractAudioRenderer.NATIVE_AUDIO_FORMAT_ENDIAN,
                        AudioFormat.SIGNED,  /* frameSizeInBits */
                        Format.NOT_SPECIFIED,  /* frameRate */
                        Format.NOT_SPECIFIED.toDouble(),
                        Format.byteArray)
                )
            }
            this.SUPPORTED_INPUT_FORMATS = SUPPORTED_INPUT_FORMATS.toTypedArray()
        }
    }
}