/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.service.neomedia.control.FECDecoderControl
import timber.log.Timber
import java.awt.Component
import javax.media.Buffer
import javax.media.Format
import javax.media.ResourceUnavailableException
import javax.media.format.AudioFormat

/**
 * Implements the SILK decoder as an FMJ/JMF `Codec`.
 *
 * @author Dingxin Xu
 * @author Boris Grozev
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class JavaDecoder : AbstractCodec2("SILK Decoder", AudioFormat::class.java, SUPPORTED_OUTPUT_FORMATS) {
    /**
     * A private class, an instance of which is registered via `addControl`. This instance
     * will be used by outside classes to access decoder statistics.
     */
    private inner class Stats : FECDecoderControl {
        /**
         * Returns the number packets for which FEC data was decoded in `JavaDecoder.this`
         *
         * @return Returns the number packets for which FEC data was decoded in `JavaDecoder.this`
         */
        override fun fecPacketsDecoded(): Int {
            return nbFECDecoded
        }

        /**
         * Stub. Always return `null`, as it's not used.
         *
         * @return `null`
         */
        override fun getControlComponent(): Component? {
            return null
        }
    }

    /**
     * The SILK decoder control (structure).
     */
    private var decControl: SKP_SILK_SDK_DecControlStruct? = null

    /**
     * The SILK decoder state.
     */
    private var decState: SKP_Silk_decoder_state? = null

    /**
     * The length of an output frame as determined by [.FRAME_DURATION] and the
     * `inputFormat` of this `JavaDecoder`.
     */
    private var frameLength: Short = 0

    /**
     * The number of frames decoded from the last input `Buffer` which has not been consumed yet.
     */
    private var framesPerPayload = 0

    /**
     * The sequence number of the last processed `Buffer`.
     */
    private var lastSeqNo = Buffer.SEQUENCE_UNKNOWN

    /**
     * Temporary buffer used when decoding FEC. Defined here to avoid using `new` in `doProcess`.
     */
    private val lbrrBytes = ShortArray(1)

    /**
     * Temporary buffer used to hold the lbrr data when decoding FEC. Defined here to avoid using
     * `new` in `doProcess`.
     */
    private val lbrrData = ByteArray(JavaEncoder.MAX_BYTES_PER_FRAME)

    /**
     * Number of packets which: were missing, the following packet was available and it contained FEC data.
     */
    private var nbFECDecoded = 0

    /**
     * Number of packets which: were missing, the next packet was available, but it did not contain FEC data.
     */
    private var nbFECNotDecoded = 0

    /**
     * Number of packets which: were successfully decoded
     */
    private var nbPacketsDecoded = 0

    /**
     * Number of packets which: were missing, and the subsequent packet was also missing.
     */
    private var nbPacketsLost = 0

    /**
     * The length of an output frame as reported by
     * [DecAPI.SKP_Silk_SDK_Decode]
     * .
     */
    private val outLength = ShortArray(1)

    /**
     * Initializes a new `JavaDecoder` instance.
     */
    init {
        features = BUFFER_FLAG_FEC or BUFFER_FLAG_PLC
        inputFormats = SUPPORTED_INPUT_FORMATS
        addControl(Stats())
    }

    override fun doClose() {
        Timber.d("Packets decoded normally: %s\nPackets decoded with FEC: %s", nbPacketsDecoded, nbFECDecoded)
        Timber.d("Packets lost (subsequent missing):%s\nPackets lost (no FEC in subsequent): %s", nbPacketsLost, nbFECNotDecoded)
        decState = null
        decControl = null
    }

    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        decState = SKP_Silk_decoder_state()
        if (DecAPI.SKP_Silk_SDK_InitDecoder(decState!!) != 0) {
            throw ResourceUnavailableException("DecAPI.SKP_Silk_SDK_InitDecoder")
        }
        val inputFormat = getInputFormat() as AudioFormat
        val sampleRate = inputFormat.sampleRate
        val channels = inputFormat.channels
        decControl = SKP_SILK_SDK_DecControlStruct()
        decControl!!.API_sampleRate = sampleRate.toInt()
        frameLength = ((FRAME_DURATION * sampleRate * channels) / 1000).toInt().toShort()
        lastSeqNo = Buffer.SEQUENCE_UNKNOWN
    }

    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        val seqNo = inBuf.sequenceNumber

        /*
         * Buffer.FLAG_SILENCE is set only when the intention is to drop the specified input Buffer
         * but to note that it has not been lost.
         */
        if (Buffer.FLAG_SILENCE and inBuf.flags != 0) {
            lastSeqNo = seqNo
            return OUTPUT_BUFFER_NOT_FILLED
        }

        /*
         * Check whether a packet has been lost. If a packet has more than one frame, we go through
         * each frame in a new call to the process method so having the same sequence number as on
         * the previous pass is fine.
         */
        val lostSeqNoCount = calculateLostSeqNoCount(lastSeqNo, seqNo)
        var decodeFEC = lostSeqNoCount in 1..MAX_AUDIO_SEQUENCE_NUMBERS_TO_PLC
        if (decodeFEC && inBuf.flags and Buffer.FLAG_SKIP_FEC != 0) {
            decodeFEC = false
            Timber.log(TimberLog.FINER, "Not decoding FEC/PLC for %s because of Buffer.FLAG_SKIP_FEC.", seqNo)
        }
        val `in` = inBuf.data as ByteArray
        val inOffset = inBuf.offset
        val inLength = inBuf.length
        val out = validateShortArraySize(outBuf, frameLength.toInt())
        val outOffset = 0
        var lostFlag = 0
        if (decodeFEC) /* Decode with FEC. */ {
            lbrrBytes[0] = 0
            DecAPI.SKP_Silk_SDK_search_for_LBRR(`in`, inOffset, inLength.toShort(),  /* lost_offset */
                    lostSeqNoCount, lbrrData, 0, lbrrBytes)
            Timber.log(TimberLog.FINER, "Packet loss detected. Last seen %s, current %s", lastSeqNo, seqNo)
            Timber.log(TimberLog.FINER, "Looking for FEC data, found %s bytes", lbrrBytes[0])
            outLength[0] = frameLength
            if (lbrrBytes[0].toInt() == 0) {
                // No FEC data found, process the packet as lost.
                lostFlag = 1
            } else if (DecAPI.SKP_Silk_SDK_Decode(decState, decControl, 0, lbrrData, 0, lbrrBytes[0].toInt(),
                            out, outOffset, outLength) == 0) {
                // Found FEC data, decode it.
                nbFECDecoded++
                outBuf.duration = (FRAME_DURATION * 1000000).toLong()
                outBuf.length = outLength[0].toInt()
                outBuf.offset = outOffset
                outBuf.flags = outBuf.flags or BUFFER_FLAG_FEC
                outBuf.flags = outBuf.flags and BUFFER_FLAG_PLC.inv()

                // We have decoded the expected sequence number from FEC data.
                lastSeqNo = incrementSeqNo(lastSeqNo)
                return INPUT_BUFFER_NOT_CONSUMED
            } else {
                nbFECNotDecoded++
                if (lostSeqNoCount != 0) nbPacketsLost += lostSeqNoCount
                lastSeqNo = seqNo
                return BUFFER_PROCESSED_FAILED
            }
        } else if (lostSeqNoCount != 0) nbPacketsLost += lostSeqNoCount
        var processed: Int

        /* Decode without FEC. */
        run {
            outLength[0] = frameLength
            if (DecAPI.SKP_Silk_SDK_Decode(decState, decControl, lostFlag, `in`, inOffset, inLength,
                            out, outOffset, outLength) == 0) {
                outBuf.duration = (FRAME_DURATION * 1000000).toLong()
                outBuf.length = outLength[0].toInt()
                outBuf.offset = outOffset
                if (lostFlag == 0) {
                    outBuf.flags = outBuf.flags and (BUFFER_FLAG_FEC or BUFFER_FLAG_PLC).inv()
                    processed = if (decControl!!.moreInternalDecoderFrames == 0) {
                        nbPacketsDecoded++
                        BUFFER_PROCESSED_OK
                    } else {
                        framesPerPayload++
                        if (framesPerPayload >= MAX_FRAMES_PER_PAYLOAD) {
                            nbPacketsDecoded++
                            BUFFER_PROCESSED_OK
                        } else INPUT_BUFFER_NOT_CONSUMED
                    }
                    lastSeqNo = seqNo
                } else {
                    outBuf.flags = outBuf.flags and BUFFER_FLAG_FEC.inv()
                    outBuf.flags = outBuf.flags or BUFFER_FLAG_PLC
                    processed = INPUT_BUFFER_NOT_CONSUMED
                    // We have decoded the expected sequence number with PLC.
                    lastSeqNo = incrementSeqNo(lastSeqNo)
                }
            } else {
                processed = BUFFER_PROCESSED_FAILED
                if (lostFlag == 1) {
                    nbFECNotDecoded++
                    if (lostSeqNoCount != 0) this.nbPacketsLost += lostSeqNoCount
                }
                lastSeqNo = seqNo
            }
            if (processed and INPUT_BUFFER_NOT_CONSUMED != INPUT_BUFFER_NOT_CONSUMED) framesPerPayload = 0
        }
        return processed
    }

    /**
     * Get the output formats matching a specific input format.
     *
     * @param inputFormat the input format to get the matching output formats of
     * @return the output formats matching the specified input format
     * @see AbstractCodec2.getMatchingOutputFormats
     */
    override fun getMatchingOutputFormats(inputFormat: Format): Array<Format> {
        return JavaEncoder.getMatchingOutputFormats(inputFormat, SUPPORTED_INPUT_FORMATS, SUPPORTED_OUTPUT_FORMATS)
    }

    companion object {
        /**
         * The duration of a frame in milliseconds as defined by the SILK standard.
         */
        const val FRAME_DURATION = 20

        /**
         * The maximum number of frames encoded into a single payload as defined by the SILK standard.
         */
        private const val MAX_FRAMES_PER_PAYLOAD = 5

        /**
         * The list of `Format`s of audio data supported as input by `JavaDecoder` instances.
         */
        private val SUPPORTED_INPUT_FORMATS = JavaEncoder.SUPPORTED_OUTPUT_FORMATS

        /**
         * The list of `Format`s of audio data supported as output by `JavaDecoder` instances.
         */
        private val SUPPORTED_OUTPUT_FORMATS = JavaEncoder.SUPPORTED_INPUT_FORMATS
    }
}