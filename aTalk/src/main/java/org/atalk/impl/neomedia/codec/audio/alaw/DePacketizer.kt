/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.alaw

import com.sun.media.codec.audio.AudioCodec
import org.atalk.service.neomedia.codec.Constants
import javax.media.Buffer
import javax.media.Format
import javax.media.format.AudioFormat

/**
 * DePacketizer for ALAW codec
 *
 * @author Damian Minkov
 */
class DePacketizer : AudioCodec() {
    /**
     * Creates DePacketizer
     */
    init {
        inputFormats = arrayOf<Format>(AudioFormat(Constants.ALAW_RTP))
    }

    /**
     * Returns the name of the DePacketizer
     *
     * @return String
     */
    override fun getName(): String {
        return "ALAW DePacketizer"
    }

    /**
     * Returns the supported output formats
     *
     * @param `in`
     * Format
     * @return Format[]
     */
    override fun getSupportedOutputFormats(inFormat: Format?): Array<Format?> {
        if (inFormat == null) {
            return arrayOf(AudioFormat(AudioFormat.ALAW))
        }
        if (matches(inFormat, inputFormats) == null) {
            return arrayOfNulls(1)
        }

        if (inFormat !is AudioFormat) {
            return arrayOf(AudioFormat(AudioFormat.ALAW))
        }
        return arrayOf(AudioFormat(AudioFormat.ALAW, inFormat.sampleRate,
                inFormat.sampleSizeInBits, inFormat.channels))
    }

    /**
     * Initializes the codec.
     */
    override fun open() {}

    /**
     * Clean up
     */
    override fun close() {}

    /**
     * decode the buffer
     *
     * @param inputBuffer
     * Buffer
     * @param outputBuffer
     * Buffer
     * @return int
     */
    override fun process(inputBuffer: Buffer, outputBuffer: Buffer): Int {
        if (!checkInputBuffer(inputBuffer)) {
            return BUFFER_PROCESSED_FAILED
        }
        if (isEOM(inputBuffer)) {
            propagateEOM(outputBuffer)
            return BUFFER_PROCESSED_OK
        }
        val outData = outputBuffer.data
        outputBuffer.data = inputBuffer.data
        inputBuffer.data = outData
        outputBuffer.length = inputBuffer.length
        outputBuffer.format = outputFormat
        outputBuffer.offset = inputBuffer.offset
        return BUFFER_PROCESSED_OK
    }
}