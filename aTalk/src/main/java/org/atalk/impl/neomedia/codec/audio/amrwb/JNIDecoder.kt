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
import org.atalk.impl.neomedia.codec.audio.FFmpegAudioDecoder
import org.atalk.service.neomedia.codec.Constants
import javax.media.Buffer
import javax.media.Format

/**
 * Implements an Adaptive Multi-Rate Wideband (AMR-WB) decoder using FFmpeg.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class JNIDecoder : FFmpegAudioDecoder("AMR-WB JNI Decoder",
        FFmpeg.CODEC_ID_AMR_WB,
        JNIEncoder.SUPPORTED_INPUT_FORMATS) {

    /**
     * The indicator which determines whether this <tt>JNIDecoder</tt> is to perform RTP depacketization.
     */
    private var depacketize = false

    /**
     * Initializes a new <tt>JNIDecoder</tt> instance.
     */
    init {
        inputFormats = JNIEncoder.SUPPORTED_OUTPUT_FORMATS
    }

    private fun depacketize(buf: Buffer): Int {
        val inLen = buf.length

        // The payload header and payload table of contents together span at
        // least two bytes in both bandwidth-efficient mode and octet-aligned mode.
        if (inLen < 2) return BUFFER_PROCESSED_FAILED
        val `in` = buf.data as ByteArray
        val inOff = buf.offset
        val in0 = `in`[inOff].toInt() and 0xFF
        // F (1 bit): If set to 1, indicates that this frame is followed by
        // another speech frame in this payload; if set to 0, indicates that
        // this frame is the last frame in this payload.
        val f = 0x08 and in0 ushr 3

        // TODO Add support for multiple frames in a single payload.
        if (f == 1) return BUFFER_PROCESSED_FAILED
        val in1 = `in`[inOff + 1].toInt() and 0xFF
        // Q (1 bit): Frame quality indicator. If set to 0, indicates the corresponding frame is severely damaged.
        val q = 0x40 and in1 ushr 6
        if (q == 0) return BUFFER_PROCESSED_FAILED

        // FT (4 bits): Frame type index, indicating either the AMR-WB speech coding mode or
        // comfort noise (SID) mode of the corresponding frame carried in this payload.
        val ft = 0x07 and in0 shl 1 or (0x80 and in1 ushr 7)
        if (ft > 8) return OUTPUT_BUFFER_NOT_FILLED
        val out0 = ft and 0x0F shl 3 or (q and 0x01 shl 2)
        `in`[0] = out0.toByte()
        var inI = inOff + 1
        val inEnd = inOff + inLen
        var outI = 1
        while (inI < inEnd) {
            var i = 0x3F and `in`[inI].toInt() shl 2
            `in`[outI] = i.toByte()
            inI++
            if (inI < inEnd) {
                val o = 0xFC and `in`[outI].toInt()
                i = 0xC0 and `in`[inI].toInt() ushr 6
                `in`[outI] = (o or i).toByte()
            }
            outI++
        }
        buf.data = `in`
        buf.duration = 20L /* milliseconds */ * 1000000L
        buf.length = inLen
        buf.offset = 0
        return BUFFER_PROCESSED_OK
    }

    /**
     * {@inheritDoc}
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        if (depacketize) {
            val depacketize = depacketize(inBuf)
            if (depacketize and BUFFER_PROCESSED_FAILED != 0 || depacketize and OUTPUT_BUFFER_NOT_FILLED != 0) {
                return depacketize
            }
        }
        return super.doProcess(inBuf, outBuf)
    }

    /**
     * {@inheritDoc}
     *
     * Additionally, determines whether this <tt>JNIDecoder</tt> is to perform RTP depacketization.
     */
    override fun setInputFormat(format: Format): Format {
        var inFormat: Format? = format
        inFormat = super.setInputFormat(inFormat!!)
        if (inFormat != null) {
            val encoding = inFormat.encoding
            depacketize = encoding != null && encoding.endsWith(Constants._RTP)
        }
        return inFormat!!
    }

    companion object {
        init {
            assertFindAVCodec(FFmpeg.CODEC_ID_AMR_WB)
        }
    }
}