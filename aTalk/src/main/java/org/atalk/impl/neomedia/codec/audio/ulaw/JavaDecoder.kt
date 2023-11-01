/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.ulaw

import com.ibm.media.codec.audio.AudioCodec
import com.sun.media.controls.SilenceSuppressionAdapter
import javax.media.Buffer
import javax.media.Format
import javax.media.format.AudioFormat

class JavaDecoder : AudioCodec() {
    init {
        supportedInputFormats = arrayOf(AudioFormat(AudioFormat.ULAW))
        defaultOutputFormats = arrayOf(AudioFormat(AudioFormat.LINEAR))
        PLUGIN_NAME = "Mu-Law Decoder"
    }

    override fun getControls(): Array<Any> {
        if (controls == null) {
            controls = arrayOf<Any>(SilenceSuppressionAdapter(this, false, false))
        }
        return controls
    }

    override fun getMatchingOutputFormats(`in`: Format): Array<out Format> {
        val af = `in` as AudioFormat
        supportedOutputFormats = arrayOf(
                AudioFormat(
                        AudioFormat.LINEAR,
                        af.sampleRate, 16,
                        af.channels,
                        AudioFormat.LITTLE_ENDIAN,  // isBigEndian(),
                        AudioFormat.SIGNED // isSigned());
                )
        )
        return supportedOutputFormats
    }

    private fun initTables() {
        for (i in 0..255) {
            val input = i.inv()
            val mantissa = (input and 0xf shl 3) + 0x84
            val segment = input and 0x70 shr 4
            var value = mantissa shl segment
            value -= 0x84
            if (input and 0x80 != 0) value = -value
            lutTableL[i] = value.toByte()
            lutTableH[i] = (value shr 8).toByte()
        }
    }

    /** Initializes the codec.  */
    override fun open() {
        initTables()
    }

    /** Decodes the buffer  */
    override fun process(inputBuffer: Buffer, outputBuffer: Buffer): Int {
        if (!checkInputBuffer(inputBuffer)) return BUFFER_PROCESSED_FAILED
        if (isEOM(inputBuffer)) {
            propagateEOM(outputBuffer)
            return BUFFER_PROCESSED_OK
        }
        val inData = inputBuffer.data as ByteArray
        val outData = validateByteArraySize(outputBuffer, inData.size * 2)
        val inpLength = inputBuffer.length
        val outLength = 2 * inpLength
        var inOffset = inputBuffer.offset
        var outOffset = outputBuffer.offset

        for (i in 0 until inpLength) {
            val temp = inData[inOffset++].toInt() and 0xff
            outData[outOffset++] = lutTableL[temp]
            outData[outOffset++] = lutTableH[temp]
        }

        updateOutput(outputBuffer, outputFormat, outLength, outputBuffer.offset)
        return BUFFER_PROCESSED_OK
    }

    companion object {
        private val lutTableH = ByteArray(256)
        private val lutTableL = ByteArray(256)
    }
}