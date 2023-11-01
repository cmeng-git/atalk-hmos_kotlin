/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.ulaw

import com.ibm.media.codec.audio.AudioCodec
import javax.media.Buffer
import javax.media.Format
import javax.media.format.AudioFormat

class JavaEncoder : AudioCodec() {
    private var downmix = false
    private var inputBias = 0
    private var inputSampleSize = 0
    private var lastFormat: Format? = null
    private var lsbOffset = 0
    private var msbOffset = 0
    private var numberOfInputChannels = 0
    private var numberOfOutputChannels = 1
    private var signMask = 0

    init {
        supportedInputFormats = arrayOf(
                AudioFormat(AudioFormat.LINEAR, Format.NOT_SPECIFIED.toDouble(), 16, 1, Format.NOT_SPECIFIED,
                        Format.NOT_SPECIFIED),
                AudioFormat(AudioFormat.LINEAR, Format.NOT_SPECIFIED.toDouble(), 16, 2, Format.NOT_SPECIFIED,
                        Format.NOT_SPECIFIED),
                AudioFormat(AudioFormat.LINEAR, Format.NOT_SPECIFIED.toDouble(), 8, 1, Format.NOT_SPECIFIED,
                        Format.NOT_SPECIFIED),
                AudioFormat(AudioFormat.LINEAR, Format.NOT_SPECIFIED.toDouble(), 8, 2, Format.NOT_SPECIFIED,
                        Format.NOT_SPECIFIED)) // support
        // 1/2
        // channels
        // and
        // 8/16
        // bit
        // samples
        defaultOutputFormats = arrayOf(AudioFormat(AudioFormat.ULAW, 8000.0, 8, 1,
                Format.NOT_SPECIFIED, Format.NOT_SPECIFIED))
        PLUGIN_NAME = "pcm to mu-law converter"
    }

    private fun calculateOutputSize(inputLength: Int): Int {
        var inputLength = inputLength
        if (inputSampleSize == 16) inputLength /= 2
        if (downmix) inputLength /= 2
        return inputLength
    }

    private fun convert(
            input: ByteArray, inputOffset: Int, inputLength: Int, outData: ByteArray,
            outputOffset: Int,
    ) {
        var outputOffset = outputOffset
        var sample: Int
        var signBit: Int
        var inputSample: Int
        var i: Int
        i = inputOffset + msbOffset
        while (i < inputLength + inputOffset) {
            if (8 == inputSampleSize) {
                inputSample = input[i++].toInt() shl 8
                if (downmix) inputSample = (inputSample and signMask) + (input[i++].toInt() shl 8 and signMask) shr 1
            }
            else {
                inputSample = (input[i].toInt() shl 8) + (0xff and input[i + lsbOffset].toInt())
                i += 2
                if (downmix) {
                    inputSample = (inputSample and signMask) + ((input[i].toInt() shl 8) + (0xff and input[i
                            + lsbOffset].toInt()) and signMask) shr 1
                    i += 2
                }
            }
            sample = (inputSample + inputBias).toShort().toInt()
            if (sample >= 0) {
                signBit = 0x80 // sign bit
            }
            else {
                sample = -sample
                signBit = 0x00
            }
            sample = 132 + sample shr 3 // bias
            outData[outputOffset++] = (if (sample < 0x0020) signBit or (7 shl 4) or 31 - (sample shr 0) else if (sample < 0x0040) signBit or (6 shl 4) or 31 - (sample shr 1) else if (sample < 0x0080) signBit or (5 shl 4) or 31 - (sample shr 2) else if (sample < 0x0100) signBit or (4 shl 4) or 31 - (sample shr 3) else if (sample < 0x0200) signBit or (3 shl 4) or 31 - (sample shr 4) else if (sample < 0x0400) signBit or (2 shl 4) or 31 - (sample shr 5) else if (sample < 0x0800) signBit or (1 shl 4) or 31 - (sample shr 6) else if (sample < 0x1000) signBit or (0 shl 4) or 31 - (sample shr 7) else signBit or (0 shl 4) or 31 - (0xfff shr 7)).toByte()
        }
    }

    override fun getMatchingOutputFormats(`in`: Format): Array<out Format> {
        val inFormat = `in` as AudioFormat
        val channels = inFormat.channels
        val sampleRate = inFormat.sampleRate.toInt()
        supportedOutputFormats = if (channels == 2) {
            arrayOf(
                    AudioFormat(
                            AudioFormat.ULAW,
                            sampleRate.toDouble(), 8, 2,
                            Format.NOT_SPECIFIED,
                            Format.NOT_SPECIFIED),

                    AudioFormat(AudioFormat.ULAW, sampleRate.toDouble(),
                            8, 1,
                            Format.NOT_SPECIFIED,
                            Format.NOT_SPECIFIED)
            )
        }
        else {
            arrayOf(AudioFormat(
                    AudioFormat.ULAW,
                    sampleRate.toDouble(),
                    8, 1,
                    Format.NOT_SPECIFIED,
                    Format.NOT_SPECIFIED))
        }
        return supportedOutputFormats
    }

    private fun initConverter(inFormat: AudioFormat) {
        lastFormat = inFormat
        numberOfInputChannels = inFormat.channels
        if (outputFormat != null) numberOfOutputChannels = outputFormat.channels
        inputSampleSize = inFormat.sampleSizeInBits
        if (inFormat.endian == AudioFormat.BIG_ENDIAN || 8 == inputSampleSize) {
            lsbOffset = 1
            msbOffset = 0
        }
        else {
            lsbOffset = -1
            msbOffset = 1
        }
        if (inFormat.signed == AudioFormat.SIGNED) {
            inputBias = 0
            signMask = -0x1
        }
        else {
            inputBias = 32768
            signMask = 0x0000ffff
        }
        downmix = numberOfInputChannels == 2 && numberOfOutputChannels == 1
    }

    override fun process(inputBuffer: Buffer, outputBuffer: Buffer): Int {
        if (!checkInputBuffer(inputBuffer)) return BUFFER_PROCESSED_FAILED
        if (isEOM(inputBuffer)) {
            propagateEOM(outputBuffer)
            return BUFFER_PROCESSED_OK
        }
        val newFormat = inputBuffer.format
        if (lastFormat != newFormat)
            initConverter(newFormat as AudioFormat)
        val inpLength = inputBuffer.length
        val outLength = calculateOutputSize(inputBuffer.length)
        val inpData = inputBuffer.data as ByteArray
        val outData = validateByteArraySize(outputBuffer, outLength)
        convert(inpData, inputBuffer.offset, inpLength, outData, 0)
        updateOutput(outputBuffer, outputFormat, outLength, 0)
        return BUFFER_PROCESSED_OK
    }
}