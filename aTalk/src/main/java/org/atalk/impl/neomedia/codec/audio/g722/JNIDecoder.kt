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
package org.atalk.impl.neomedia.codec.audio.g722

import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.service.neomedia.codec.Constants
import javax.media.Buffer
import javax.media.Format
import javax.media.ResourceUnavailableException
import javax.media.format.AudioFormat

/**
 * @author Lyubomir Marinov
 */
class JNIDecoder : AbstractCodec2("G.722 JNI Decoder", AudioFormat::class.java, SUPPORTED_OUTPUT_FORMATS) {
    private var decoder: Long = 0

    /**
     * Initializes a new `JNIDecoderImpl` instance.
     */
    init {
        inputFormats = SUPPORTED_INPUT_FORMATS
    }

    /**
     * @see AbstractCodec2.doClose
     */
    override fun doClose() {
        G722.g722_decoder_close(decoder)
    }

    /**
     * @see AbstractCodec2.doOpen
     */
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        decoder = G722.g722_decoder_open()
        if (decoder == 0L) throw ResourceUnavailableException("g722_decoder_open")
    }

    /**
     * @see AbstractCodec2.doProcess
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        val input = inBuf.data as ByteArray
        val outputOffset = outBuf.offset
        val outputLength = inBuf.length * 4
        val output = validateByteArraySize(
                outBuf,
                outputOffset + outputLength,
                true)
        G722.g722_decoder_process(
                decoder,
                input, inBuf.offset,
                output, outputOffset, outputLength)
        outBuf.duration = (
                outputLength * 1000000L
                        / (16L /* kHz */ * 2L /* sampleSizeInBits / 8 */))
        outBuf.format = getOutputFormat()
        outBuf.length = outputLength
        return BUFFER_PROCESSED_OK
    }

    companion object {
        val SUPPORTED_INPUT_FORMATS = arrayOf<Format>(
                AudioFormat(
                        Constants.G722_RTP,
                        8000.0,
                        Format.NOT_SPECIFIED /* sampleSizeInBits */,
                        1)
        )
        val SUPPORTED_OUTPUT_FORMATS = arrayOf<Format>(
                AudioFormat(
                        AudioFormat.LINEAR,
                        16000.0,
                        16,
                        1,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED /* frameRate */.toDouble(),
                        Format.byteArray)
        )
    }
}