/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.opus

import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.impl.neomedia.jmfext.media.renderer.audio.AbstractAudioRenderer
import org.atalk.service.neomedia.codec.Constants
import org.atalk.service.neomedia.control.FECDecoderControl
import timber.log.Timber
import java.awt.Component
import javax.media.Buffer
import javax.media.Format
import javax.media.ResourceUnavailableException
import javax.media.format.AudioFormat

/**
 * Implements an Opus decoder.
 *
 * @author Boris Grozev
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class JNIDecoder : AbstractCodec2("Opus JNI Decoder", AudioFormat::class.java, SUPPORTED_OUTPUT_FORMATS), FECDecoderControl {
    /**
     * Number of channels to decode into.
     */
    private val channels = 1

    /**
     * Pointer to the native OpusDecoder structure
     */
    private var decoder: Long = 0

    /**
     * The size in samples per channel of the last decoded frame in the terms of the Opus library.
     */
    private var lastFrameSizeInSamplesPerChannel = 0

    /**
     * The sequence number of the last processed `Buffer`.
     */
    private var lastSeqNo = Buffer.SEQUENCE_UNKNOWN

    /**
     * Number of packets decoded with FEC
     */
    private var nbDecodedFec = 0

    /**
     * The size in bytes of an audio frame in the terms of the output `AudioFormat` of this
     * instance i.e. based on the values of the `sampleSizeInBits` and `channels`
     * properties of the `outputFormat` of this instance.
     */
    private var outputFrameSize = 0

    /**
     * The sample rate of the audio data output by this instance.
     */
    private var outputSampleRate = 0

    /**
     * Initializes a new `JNIDecoder` instance.
     */
    init {
        features = BUFFER_FLAG_FEC or BUFFER_FLAG_PLC
        inputFormats = SUPPORTED_INPUT_FORMATS
        addControl(this)
    }

    /**
     * @see AbstractCodec2.doClose
     */
    override fun doClose() {
        if (decoder != 0L) {
            Opus.decoder_destroy(decoder)
            decoder = 0
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
        if (decoder == 0L) {
            decoder = Opus.decoder_create(outputSampleRate, channels)
            if (decoder == 0L) throw ResourceUnavailableException("opus_decoder_create")
            lastFrameSizeInSamplesPerChannel = 0
            lastSeqNo = Buffer.SEQUENCE_UNKNOWN
        }
    }

    /**
     * {@inheritDoc}
     *
     * Decodes an Opus packet.
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        val inFormat = inBuf.format

        if ((inFormat != null) && (inFormat != this.inputFormat)
                && !inFormat.equals(this.inputFormat) && (null == setInputFormat(inFormat))) {
            return BUFFER_PROCESSED_FAILED
        }
        val seqNo = inBuf.sequenceNumber

        /*
         * Buffer.FLAG_SILENCE is set only when the intention is to drop the specified input Buffer
         * but to note that it has not been lost.
         */
        if (Buffer.FLAG_SILENCE and inBuf.flags != 0) {
            lastSeqNo = seqNo
            return OUTPUT_BUFFER_NOT_FILLED
        }
        val lostSeqNoCount = calculateLostSeqNoCount(lastSeqNo, seqNo)
        /*
         * Detect the lost Buffers/packets and decode FEC/PLC. When no in-band forward error
         * correction data is available, the Opus decoder will operate as if PLC has been specified.
         */
        var decodeFEC = ((lostSeqNoCount > 0) && (lostSeqNoCount <= MAX_AUDIO_SEQUENCE_NUMBERS_TO_PLC) && (lastFrameSizeInSamplesPerChannel != 0))
        // boolean decodeFEC = ((lostSeqNoCount > 0) && (lastFrameSizeInSamplesPerChannel != 0));
        if (decodeFEC && inBuf.flags and Buffer.FLAG_SKIP_FEC != 0) {
            decodeFEC = false
            Timber.d("Opus: not decoding FEC/PLC for %s because of Buffer.FLAG_SKIP_FEC.", seqNo)
        }

        // After we have determined what is to be decoded, do decode it.
        val inBytes = inBuf.data as ByteArray
        val inOffset = inBuf.offset
        var inLength = inBuf.length
        var outOffset = 0
        var outLength = 0
        var totalFrameSizeInSamplesPerChannel = 0

        if (decodeFEC) {
            inLength =
                    if (lostSeqNoCount == 1) inLength /* FEC */
                    else 0 /* PLC */

            val out = validateByteArraySize(outBuf, outOffset + lastFrameSizeInSamplesPerChannel
                    * outputFrameSize, outOffset != 0)
            val frameSizeInSamplesPerChannel = Opus.decode(decoder, inBytes, inOffset, inLength, out,
                outOffset, lastFrameSizeInSamplesPerChannel, 1)
            if (frameSizeInSamplesPerChannel > 0) {
                val frameSizeInBytes = frameSizeInSamplesPerChannel * outputFrameSize
                outLength += frameSizeInBytes
                outOffset += frameSizeInBytes
                totalFrameSizeInSamplesPerChannel += frameSizeInSamplesPerChannel
                outBuf.flags = (outBuf.flags
                        or if (inBytes == null || inLength == 0) BUFFER_FLAG_PLC else BUFFER_FLAG_FEC)
                var ts = inBuf.rtpTimeStamp
                ts -= (lostSeqNoCount * lastFrameSizeInSamplesPerChannel).toLong()
                if (ts < 0) ts += 1L shl 32
                outBuf.rtpTimeStamp = ts
                nbDecodedFec++
            }
            lastSeqNo = incrementSeqNo(lastSeqNo)
        }
        else {
            var frameSizeInSamplesPerChannel = Opus.decoder_get_nb_samples(decoder, inBytes, inOffset, inLength)
            val out = validateByteArraySize(outBuf, outOffset + frameSizeInSamplesPerChannel
                    * outputFrameSize, outOffset != 0)
            frameSizeInSamplesPerChannel = Opus.decode(decoder, inBytes, inOffset, inLength, out,
                outOffset, frameSizeInSamplesPerChannel,  /* decodeFEC */
                0)
            if (frameSizeInSamplesPerChannel > 0) {
                val frameSizeInBytes = frameSizeInSamplesPerChannel * outputFrameSize
                outLength += frameSizeInBytes
                outOffset += frameSizeInBytes
                totalFrameSizeInSamplesPerChannel += frameSizeInSamplesPerChannel
                outBuf.flags = outBuf.flags and (BUFFER_FLAG_FEC or BUFFER_FLAG_PLC).inv()

                /*
                 * When we encounter a lost frame, we will presume that it was of the same duration
                 * as the last received frame.
                 */
                lastFrameSizeInSamplesPerChannel = frameSizeInSamplesPerChannel
            }
            lastSeqNo = seqNo
        }
        var ret = if (lastSeqNo == seqNo) BUFFER_PROCESSED_OK
        else INPUT_BUFFER_NOT_CONSUMED

        if (outLength > 0) {
            outBuf.duration = totalFrameSizeInSamplesPerChannel * 1000L * 1000L * 1000L / outputSampleRate
            outBuf.format = getOutputFormat()
            outBuf.length = outLength
            outBuf.offset = 0
            /*
             * The sequence number is not likely to be important after the depacketization and the
             * decoding but BasicFilterModule will copy them from the input Buffer into the output
             * Buffer anyway so it makes sense to keep the sequence number straight for the sake of
             * completeness.
             */
            outBuf.sequenceNumber = lastSeqNo
        }
        else {
            ret = ret or OUTPUT_BUFFER_NOT_FILLED
        }
        return ret
    }

    /**
     * Returns the number of packets decoded with FEC.
     *
     * @return the number of packets decoded with FEC
     */
    override fun fecPacketsDecoded(): Int {
        return nbDecodedFec
    }

    /**
     * Implements [Control.getControlComponent()]. `JNIDecoder` does not provide user interface of its own.
     *
     * @return `null` to signify that `JNIDecoder` does not provide user interface of its own
     */
    override fun getControlComponent(): Component? {
        return null
    }

    /**
     * {@inheritDoc}
     */
    override fun getMatchingOutputFormats(inputFormat: Format): Array<Format> {
        return arrayOf(
            AudioFormat(
                AudioFormat.LINEAR,
                (inputFormat as AudioFormat).sampleRate,
                16,
                1,
                AbstractAudioRenderer.NATIVE_AUDIO_FORMAT_ENDIAN,
                AudioFormat.SIGNED,  /* frameSizeInBits */
                Format.NOT_SPECIFIED,  /* frameRate */
                Format.NOT_SPECIFIED.toDouble(),
                Format.byteArray))
    }

    /**
     * {@inheritDoc}
     *
     * Makes sure that the `outputFormat` of this instance is in accord with the `inputFormat` of this instance.
     */
    override fun setInputFormat(format: Format): Format? {
        val inFormat = super.setInputFormat(format)
        if (inFormat != null && outputFormat == null) setOutputFormat(SUPPORTED_OUTPUT_FORMATS[0])
        return inFormat
    }

    /**
     * {@inheritDoc}
     */
    override fun setOutputFormat(format: Format): Format? {
        val setOutputFormat = super.setOutputFormat(format)
        if (setOutputFormat != null) {
            val af = setOutputFormat as AudioFormat
            outputFrameSize = af.sampleSizeInBits / 8 * af.channels
            outputSampleRate = af.sampleRate.toInt()
        }
        return setOutputFormat
    }

    companion object {
        /**
         * The list of `Format`s of audio data supported as input by `JNIDecoder` instances.
         */
        private val SUPPORTED_INPUT_FORMATS = arrayOf<Format>(AudioFormat(Constants.OPUS_RTP))

        /**
         * The list of `Format`s of audio data supported as output by `JNIDecoder` instances.
         */
        private val SUPPORTED_OUTPUT_FORMATS = arrayOf<Format>(
            AudioFormat(
                AudioFormat.LINEAR,
                48000.0,
                16,
                1,
                AbstractAudioRenderer.NATIVE_AUDIO_FORMAT_ENDIAN,
                AudioFormat.SIGNED,  /* frameSizeInBits */
                Format.NOT_SPECIFIED,  /* frameRate */
                Format.NOT_SPECIFIED.toDouble(),
                Format.byteArray)
        )

        init {
            /*
         * If the Opus class or its supporting JNI library are not functional, it is too late to
         * discover the fact in #doOpen() because a JNIDecoder instance has already been initialized
         * and it has already signaled that the Opus codec is supported.
         */
            Opus.assertOpusIsFunctional()
        }
    }
}