/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.gsm

import net.sf.fmj.media.AbstractCodec

import javax.media.Buffer
import javax.media.Format
import javax.media.format.AudioFormat

/**
 * GSM encoder Codec. Encodes 160 16-bit PCM samples into array of 33 bytes (GSM frame).
 *
 * @author Martin Harvan
 * @author Damian Minkov
 */
class Encoder : AbstractCodec() {
    private val innerBuffer = Buffer()
    private var innerDataLength = 0
    var innerContent: ByteArray? = null
    override fun getName(): String {
        return "GSM Encoder"
    }

    // TODO: move to base class?
    protected var outputFormats = arrayOf<Format?>(AudioFormat(AudioFormat.GSM, 8000.0, 8, 1,
            Format.NOT_SPECIFIED, AudioFormat.SIGNED, 264, Format.NOT_SPECIFIED.toDouble(), Format.byteArray))

    override fun getSupportedOutputFormats(input: Format?): Array<Format?> {
        return if (input == null) {
            outputFormats
        } else {
            if (input !is AudioFormat) {
                return arrayOf(null)
            }
            val inputCast = input
            val result = AudioFormat(AudioFormat.GSM, inputCast.sampleRate,
                    8, 1, inputCast.endian, AudioFormat.SIGNED, 264, inputCast.frameRate,
                    Format.byteArray)
            arrayOf(result)
        }
    }

    override fun open() {}
    override fun close() {}

    /**
     * Constructs a new `Encoder`.
     */
    init {
        inputFormats = arrayOf<Format>(AudioFormat(AudioFormat.LINEAR, 8000.0, 16, 1,
                AudioFormat.BIG_ENDIAN, AudioFormat.SIGNED, Format.NOT_SPECIFIED, Format.NOT_SPECIFIED.toDouble(),
                Format.byteArray))
    }

    override fun process(inputBuffer: Buffer, outputBuffer: Buffer): Int {
        val inputContent = ByteArray(inputBuffer.length)
        System.arraycopy(inputBuffer.data, inputBuffer.offset, inputContent, 0,
                inputContent.size)
        val mergedContent = mergeArrays(innerBuffer.data as ByteArray, inputContent)
        innerBuffer.data = mergedContent
        innerBuffer.length = mergedContent!!.size
        innerDataLength = innerBuffer.length
        if (TRACE) dump("input ", inputBuffer)
        if (!checkInputBuffer(inputBuffer)) {
            return BUFFER_PROCESSED_FAILED
        }
        if (isEOM(inputBuffer)) {
            propagateEOM(outputBuffer)
            return BUFFER_PROCESSED_OK
        }
        val result: Int
        var outputBufferData = outputBuffer.data as ByteArray
        if (outputBufferData == null
                || outputBufferData.size < GSM_BYTES * innerDataLength / PCM_BYTES) {
            outputBufferData = ByteArray(GSM_BYTES * (innerDataLength / PCM_BYTES))
            outputBuffer.data = outputBufferData
        }
        if (innerDataLength < PCM_BYTES) {
            result = OUTPUT_BUFFER_NOT_FILLED
            println("Not filled")
        } else {
            val bigEndian = (outputFormat as AudioFormat).endian == AudioFormat.BIG_ENDIAN
            outputBufferData = ByteArray(GSM_BYTES * (innerDataLength / PCM_BYTES))
            outputBuffer.data = outputBufferData
            outputBuffer.length = GSM_BYTES * (innerDataLength / PCM_BYTES)
            GSMEncoderUtil.gsmEncode(bigEndian, innerBuffer.data as ByteArray,
                    innerBuffer.offset, innerDataLength, outputBufferData)
            outputBuffer.format = outputFormat
            outputBuffer.data = outputBufferData
            result = BUFFER_PROCESSED_OK
            val temp = ByteArray(innerDataLength - innerDataLength / PCM_BYTES * PCM_BYTES)
            innerContent = innerBuffer.data as ByteArray
            System.arraycopy(innerContent, innerDataLength / PCM_BYTES * PCM_BYTES, temp, 0,
                    temp.size)
            outputBuffer.offset = 0
            innerBuffer.length = temp.size
            innerBuffer.data = temp
        }
        if (TRACE) {
            dump("input ", inputBuffer)
            dump("output", outputBuffer)
        }
        return result
    }

    private fun mergeArrays(arr1: ByteArray?, arr2: ByteArray?): ByteArray? {
        if (arr1 == null) return arr2
        if (arr2 == null) return arr1
        val merged = ByteArray(arr1.size + arr2.size)
        System.arraycopy(arr1, 0, merged, 0, arr1.size)
        System.arraycopy(arr2, 0, merged, arr1.size, arr2.size)
        return merged
    }

    override fun setInputFormat(f: Format): Format {
        // TODO: force sample size, etc
        return super.setInputFormat(f)
    }

    override fun setOutputFormat(f: Format): Format {
        return super.setOutputFormat(f)
    }

    companion object {
        private const val PCM_BYTES = 320
        private const val GSM_BYTES = 33
        private const val TRACE = false
    }
}