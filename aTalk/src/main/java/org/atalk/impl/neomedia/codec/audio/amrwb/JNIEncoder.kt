/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.codec.audio.amrwb

import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.codec.FFmpeg.avcodec_open2
import org.atalk.impl.neomedia.codec.FFmpeg.avcodeccontext_set_bit_rate
import org.atalk.impl.neomedia.codec.audio.FFmpegAudioEncoder
import org.atalk.service.neomedia.codec.Constants
import javax.media.Buffer
import javax.media.Format
import javax.media.format.AudioFormat

/**
 * Implements an Adaptive Multi-Rate Wideband (AMR-WB) encoder using FFmpeg.
 *
 * @author Lyubomir Marinov
 */
class JNIEncoder : FFmpegAudioEncoder("AMR-WB JNI Encoder",
        FFmpeg.CODEC_ID_AMR_WB,
        SUPPORTED_OUTPUT_FORMATS) {

    /**
     * The bit rate to be produced by this <tt>JNIEncoder</tt>.
     */
    private val bitRate = BIT_RATES[2] // Mode 2 = 12650

    /**
     * The indicator which determines whether this <tt>JNIEncoder</tt> is to perform RTP packetization.
     */
    private var packetize = false

    /**
     * Initializes a new <tt>JNIEncoder</tt> instance.
     */
    init {
        inputFormats = SUPPORTED_INPUT_FORMATS
    }

    /**
     * {@inheritDoc}
     */
    override fun configureAVCodecContext(avctx: Long, format: AudioFormat?) {
        super.configureAVCodecContext(avctx, format)
        avcodeccontext_set_bit_rate(avctx, bitRate)
        /**
         * Enable the voice-activation detection (VAD) included in the
         * encoder which allows to transmit no RTP packets when no voice
         * was detected. This is called discontinuous transmission (DTX)
         * and reduces the used bandwidth.
         */
        val codec: Long = 0 // if 0, avctx->codec is used
        avcodec_open2(avctx, codec, "dtx", "1") // = on
    }

    /**
     * {@inheritDoc}
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        var doProcess = super.doProcess(inBuf, outBuf)
        if (packetize && doProcess and BUFFER_PROCESSED_FAILED == 0 && doProcess and OUTPUT_BUFFER_NOT_FILLED == 0) {
            val packetize = packetize(outBuf)
            if (packetize and BUFFER_PROCESSED_FAILED != 0) doProcess = doProcess or BUFFER_PROCESSED_FAILED
            if (packetize and OUTPUT_BUFFER_NOT_FILLED != 0) doProcess = doProcess or OUTPUT_BUFFER_NOT_FILLED
        }
        return doProcess
    }

    /**
     * Packetizes a specific <tt>Buffer</tt> for RTP.
     *
     * @param buf the <tt>Buffer</tt> to packetize for RTP
     * @return
     */
    private fun packetize(buf: Buffer): Int {
        val src = buf.data as ByteArray
        val srcLen = buf.length
        val srcOff = buf.offset
        val dstLen = srcLen + 1
        val dst = ByteArray(dstLen)
        val cmr /* codec mode request */ = 15
        val f /* followed by another frame */ = src[0].toInt() ushr 7 and 0x01
        val ft /* frame type index */ = src[0].toInt() ushr 3 and 0x0F
        val q /* frame quality indicator */ = src[0].toInt() ushr 2 and 0x01
        if (ft > 9) {
            /**
             * speech lost or no data, in case of DTX because of VAD
             * In both cases, no RTP packet shall be sent = silence. The return
             * code "OK" (with zero length or zero duration) does this not. The
             * return code "FAILED" does this.
             */
            return BUFFER_PROCESSED_FAILED
        }
        dst[0] = (cmr and 0x0F shl 4 or (f and 0x01 shl 3) or (ft and 0x0E ushr 1)).toByte()
        dst[1] = (ft and 0x01 shl 7 or (q and 0x01 shl 6)).toByte()
        var srcI = srcOff + 1
        val srcEnd = srcOff + srcLen
        var dstI = 1
        while (srcI < srcEnd) {
            val s = 0xFF and src[srcI].toInt()
            var d = 0xC0 and dst[dstI].toInt()
            d = d or (0xFC and s ushr 2)
            dst[dstI] = d.toByte()
            dstI++
            dst[dstI] = (0x03 and s shl 6).toByte()
            srcI++
        }
        buf.data = dst
        buf.duration = durationNanos
        buf.length = OCTETS[ft]
        buf.offset = 0
        return BUFFER_PROCESSED_OK
    }

    /**
     * {@inheritDoc}
     *
     * Additionally, determines whether this <tt>JNIEncoder</tt> is to perform RTP packetization.
     */
    override fun setOutputFormat(format: Format): Format? {
        val format_ = super.setOutputFormat(format)
        if (format_ != null) {
            val encoding = format_.encoding
            packetize = encoding != null && encoding.endsWith(Constants._RTP)
        }
        return format_
    }

    /**
     * Gets the <tt>Format</tt> of the media output by this <tt>Codec</tt>.
     *
     * @return the <tt>Format</tt> of the media output by this <tt>Codec</tt>
     * @see net.sf.fmj.media.AbstractCodec.getOutputFormat
     */
    /* copied from Speex and Opus Codec */
    public override fun getOutputFormat(): Format {
        var f = super.getOutputFormat()
        if (f != null && f.javaClass == AudioFormat::class.java) {
            val af = f as AudioFormat
            f = setOutputFormat(
                    object : AudioFormat(
                            af.encoding,
                            af.sampleRate,
                            af.sampleSizeInBits,
                            af.channels,
                            af.endian,
                            af.signed,
                            af.frameSizeInBits,
                            af.frameRate,
                            af.dataType) {
                        override fun computeDuration(length: Long): Long {
                            return durationNanos
                        }
                    })
        }
        return f
    }

    companion object {
        init {
            assertFindAVCodec(FFmpeg.CODEC_ID_AMR_WB)
        }

        /**
         * The bit rates supported by Adaptive Multi-Rate Wideband (AMR-WB).
         */
        val BIT_RATES = intArrayOf(6600, 8850, 12650, 14250, 15850, 18250, 19850, 23050, 23850)

        /**
         * The amount of bytes in the RTP frame, for each BIT_RATE and the
         * Silence Indicator (SID), see 3GPP TS 26.201 Table 2 and 3:
         * Total number of (speech) bits + 10, devided by 8 to get the octets.
         * 10 because of the added header, which is CMR=4, F=1, FT=4, Q=1.
         * Three examples:
         * Mode 0 ( 6600) has 132 speech bits and 10 header bits = 18 octets
         * Mode 8 (23850) has 477 speech bits and 10 header bits = 61 octets.
         * Mode 9 (SID) has 40 bits and 10 header bits = 7 octets.
         */
        val OCTETS = intArrayOf(18, 24, 33, 37, 41, 47, 51, 59, 61, 7)

        /**
         * The list of <tt>Format</tt>s of audio data supported as input by <tt>JNIEncoder</tt> instances.
         */
        val SUPPORTED_INPUT_FORMATS = arrayOf(
                AudioFormat(
                        AudioFormat.LINEAR,
                        16000.0,
                        16,
                        1,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,  /* frameSizeInBits */
                        Format.NOT_SPECIFIED,  /* frameRate */
                        Format.NOT_SPECIFIED.toDouble(),
                        Format.byteArray)
        )

        /**
         * The list of <tt>Format</tt>s of audio data supported as output by <tt>JNIEncoder</tt> instances.
         */
        val SUPPORTED_OUTPUT_FORMATS = arrayOf(
                AudioFormat(
                        Constants.AMR_WB_RTP,
                        16000.0,  /* sampleSizeInBits */
                        Format.NOT_SPECIFIED,
                        1),
                AudioFormat(
                        Constants.AMR_WB,
                        16000.0,  /* sampleSizeInBits */
                        Format.NOT_SPECIFIED,
                        1)
        )

        /**
         * The current implementation provides only single frames with 20ms size,
         * see [.doProcess].
         */
        private const val durationNanos = 20L /* milliseconds */ * 1000000L /* nanoseconds in a millisecond */
    }
}