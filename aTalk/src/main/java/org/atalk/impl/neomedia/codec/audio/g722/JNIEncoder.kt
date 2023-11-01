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
import javax.media.Buffer
import javax.media.Format
import javax.media.ResourceUnavailableException
import javax.media.format.AudioFormat

/**
 *
 * @author Lyubomir Marinov
 */
class JNIEncoder : AbstractCodec2(
        "G.722 JNI Encoder",
        AudioFormat::class.java,
        JNIDecoder.SUPPORTED_INPUT_FORMATS) {
    private var encoder: Long = 0

    /**
     * Initializes a new `JNIEncoderImpl` instance.
     */
    init {
        inputFormats = JNIDecoder.SUPPORTED_OUTPUT_FORMATS
    }

    private fun computeDuration(length: Long): Long {
        return length * 1000000L / 8L
    }

    /**
     *
     * @see AbstractCodec2.doClose
     */
    override fun doClose() {
        G722.g722_encoder_close(encoder)
    }

    /**
     * @see AbstractCodec2.doOpen
     */
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        encoder = G722.g722_encoder_open()
        if (encoder == 0L) throw ResourceUnavailableException("g722_encoder_open")
    }

    /**
     * @see AbstractCodec2.doProcess
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        val inputOffset = inBuf.offset
        val inputLength = inBuf.length
        val input = inBuf.data as ByteArray
        val outputOffset = outBuf.offset
        val outputLength = inputLength / 4
        val output = validateByteArraySize(
                outBuf,
                outputOffset + outputLength,
                true)
        G722.g722_encoder_process(
                encoder,
                input, inputOffset,
                output, outputOffset, outputLength)
        outBuf.duration = computeDuration(outputLength.toLong())
        outBuf.format = getOutputFormat()
        outBuf.length = outputLength
        return BUFFER_PROCESSED_OK
    }

    /**
     * Get the output <tt>Format</tt>.
     *
     * @return output <tt>Format</tt> configured for this <tt>Codec</tt>
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
                            return this@JNIEncoder.computeDuration(length)
                        }
                    })
        }
        return outputFormat
    }
}