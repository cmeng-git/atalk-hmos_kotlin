/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.gsm

import net.sf.fmj.media.AbstractCodec
import net.sf.fmj.media.AudioFormatCompleter

import javax.media.Buffer;
import javax.media.Format;
import javax.media.format.AudioFormat;

/**
 * GSM to PCM java decoder. Decodes GSM frame (33 bytes long) into 160 16-bit PCM samples (320
 * bytes).
 *
 * @author Martin Harvan
 * @author Damian Minkov
 */
open class Decoder : AbstractCodec() {
    private val innerBuffer = Buffer()
    private var innerDataLength = 0
    var innerContent: ByteArray? = null
    override fun getName(): String {
        return "GSM Decoder"
    }

    // TODO: move to base class?
    private var outputFormats = arrayOf<Format?>(AudioFormat(AudioFormat.LINEAR, 8000.0, 16,
            1, Format.NOT_SPECIFIED, AudioFormat.SIGNED, Format.NOT_SPECIFIED, Format.NOT_SPECIFIED.toDouble(),
            Format.byteArray))

    override fun setOutputFormat(format: Format): Format? {
        if (format !is AudioFormat) return null
        return super.setOutputFormat(AudioFormatCompleter.complete(format))
    }

    override fun getSupportedOutputFormats(input: Format?): Array<Format?> {
        return if (input == null) outputFormats else {
            if (input !is AudioFormat) {
                return arrayOf(null)
            }
            val inputCast = input
            if ((inputCast.encoding != AudioFormat.GSM || inputCast.sampleSizeInBits != 8 && inputCast.sampleSizeInBits != Format.NOT_SPECIFIED || inputCast.channels != 1 && inputCast.channels != Format.NOT_SPECIFIED || inputCast.signed != AudioFormat.SIGNED && inputCast.signed != Format.NOT_SPECIFIED || inputCast.frameSizeInBits != 264 && inputCast.frameSizeInBits != Format.NOT_SPECIFIED || inputCast.dataType != null) && inputCast.dataType != Format.byteArray) {
                return arrayOf(null)
            }
            val result = AudioFormat(AudioFormat.LINEAR,
                    inputCast.sampleRate, 16, 1, inputCast.endian, AudioFormat.SIGNED, 16,
                    Format.NOT_SPECIFIED.toDouble(), Format.byteArray)
            arrayOf(result)
        }
    }

    override fun open() {}
    override fun close() {}

    /**
     * Constructs a new `Decoder`.
     */
    init {
        inputFormats = arrayOf<Format>(AudioFormat(AudioFormat.GSM, 8000.0, 8, 1,
                Format.NOT_SPECIFIED, AudioFormat.SIGNED, 264, Format.NOT_SPECIFIED.toDouble(), Format.byteArray))
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
                || outputBufferData.size < PCM_BYTES * innerBuffer.length / GSM_BYTES) {
            outputBufferData = ByteArray(PCM_BYTES * (innerBuffer.length / GSM_BYTES))
            outputBuffer.data = outputBufferData
        }
        if (innerBuffer.length < GSM_BYTES) {
            result = OUTPUT_BUFFER_NOT_FILLED
        } else {
            val bigEndian = (outputFormat as AudioFormat).endian == AudioFormat.BIG_ENDIAN
            outputBufferData = ByteArray(PCM_BYTES * (innerBuffer.length / GSM_BYTES))
            outputBuffer.data = outputBufferData
            outputBuffer.length = PCM_BYTES * (innerBuffer.length / GSM_BYTES)
            GSMDecoderUtil.gsmDecode(bigEndian, innerBuffer.data as ByteArray,
                    inputBuffer.offset, innerBuffer.length, outputBufferData)
            outputBuffer.format = outputFormat
            result = BUFFER_PROCESSED_OK
            val temp = ByteArray(innerDataLength - innerDataLength / GSM_BYTES * GSM_BYTES)
            innerContent = innerBuffer.data as ByteArray
            System.arraycopy(innerContent, innerDataLength / GSM_BYTES * GSM_BYTES, temp, 0,
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

    override fun setInputFormat(arg0: Format): Format {
        // TODO: force sample size, etc
        return super.setInputFormat(arg0)
    }

    companion object {
        private const val PCM_BYTES = 320
        private const val GSM_BYTES = 33
        private const val TRACE = false
    }
}