/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.alaw

import com.ibm.media.codec.audio.AudioCodec
import javax.media.Buffer
import javax.media.Format
import javax.media.ResourceUnavailableException
import javax.media.format.AudioFormat

/**
 * The ALAW Encoder. Used the FMJ ALawEncoderUtil.
 *
 * @author Damian Minkov
 */
class JavaEncoder : AudioCodec() {
    /**
     * The last used format.
     */
    private var lastFormat: Format? = null

    /**
     * The input sample size in bits.
     */
    private var inputSampleSize = 0

    /**
     * The byte order.
     */
    private var bigEndian = false

    /**
     * Returns the output formats according to the input.
     *
     * @param `in`
     * the input format.
     * @return the possible output formats.
     */
    override fun getMatchingOutputFormats(inFormat: Format): Array<out Format> {
        val sampleRate = (inFormat as AudioFormat).sampleRate.toInt()
        supportedOutputFormats = arrayOf(
                AudioFormat(AudioFormat.ALAW,
                        sampleRate.toDouble(),
                        8, 1,
                        Format.NOT_SPECIFIED,
                        Format.NOT_SPECIFIED))
        return supportedOutputFormats
    }

    /**
     * No resources to be opened.
     *
     * @throws ResourceUnavailableException
     * if open failed (which cannot happend for this codec since no resources are to be
     * opened)
     */
    @Throws(ResourceUnavailableException::class)
    override fun open() {
    }

    /**
     * No resources used to be cleared.
     */
    override fun close() {}

    /**
     * Calculate the output data size.
     *
     * @param inLength
     * input length.
     * @return the output data size.
     */
    private fun calculateOutputSize(inLength: Int): Int {
        var inputLength = inLength
        if (inputSampleSize == 16) {
            inputLength /= 2
        }
        return inputLength
    }

    /**
     * Init the converter to the new format
     *
     * @param inFormat
     * AudioFormat
     */
    private fun initConverter(inFormat: AudioFormat) {
        lastFormat = inFormat
        inputSampleSize = inFormat.sampleSizeInBits
        bigEndian = inFormat.endian == AudioFormat.BIG_ENDIAN
    }

    /**
     * Encodes the input buffer passing it to the output one
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
        val newFormat = inputBuffer.format
        if (lastFormat != newFormat) {
            initConverter(newFormat as AudioFormat)
        }
        if (inputBuffer.length == 0) {
            return OUTPUT_BUFFER_NOT_FILLED
        }
        val outLength = calculateOutputSize(inputBuffer.length)
        val inpData = inputBuffer.data as ByteArray
        val outData = validateByteArraySize(outputBuffer, outLength)
        aLawEncode(bigEndian, inpData, inputBuffer.offset, inputBuffer.length, outData)
        updateOutput(outputBuffer, outputFormat, outLength, 0)
        return BUFFER_PROCESSED_OK
    }

    /**
     * Constructs the encoder and init the supported formats.
     */
    init {
        supportedInputFormats = arrayOf(
                AudioFormat(AudioFormat.LINEAR, Format.NOT_SPECIFIED.toDouble(), 16, 1, Format.NOT_SPECIFIED,
                        Format.NOT_SPECIFIED),
                AudioFormat(AudioFormat.LINEAR, Format.NOT_SPECIFIED.toDouble(), 8, 1, Format.NOT_SPECIFIED,
                        Format.NOT_SPECIFIED)) // support
        // 1
        // channel
        // and
        // 8/16
        // bit
        // samples
        defaultOutputFormats = arrayOf(AudioFormat(AudioFormat.ALAW, 8000.0, 8, 1,
                Format.NOT_SPECIFIED, Format.NOT_SPECIFIED))
        PLUGIN_NAME = "pcm to alaw converter"
    }

    companion object {
        /**
         * maximum that can be held in 15 bits
         */
        const val MAX = 0x7fff

        /**
         * An array where the index is the 16-bit PCM input, and the value is the a-law result.
         */
        private var pcmToALawMap = ByteArray(65536)

        init {
            for (i in Short.MIN_VALUE..Short.MAX_VALUE) pcmToALawMap[uShortToInt(i.toShort())] = encode(i)
        }

        /**
         * 65535
         */
        const val MAX_USHORT = Short.MAX_VALUE * 2 + 1

        /**
         * Unsigned short to integer.
         *
         * @param value
         * unsigned short.
         * @return integer.
         */
        private fun uShortToInt(value: Short): Int {
            return if (value >= 0) value.toInt() else MAX_USHORT + 1 + value
        }

        /**
         * Encode an array of pcm values into a pre-allocated target array
         *
         * @param bigEndian
         * the data byte order.
         * @param data
         * An array of bytes in Little-Endian format
         * @param target
         * A pre-allocated array to receive the A-law bytes. This array must be at least half the
         * size of the source.
         * @param offset
         * of the input.
         * @param length
         * of the input.
         */
        fun aLawEncode(
                bigEndian: Boolean, data: ByteArray, offset: Int, length: Int,
                target: ByteArray,
        ) {
            if (bigEndian) aLawEncodeBigEndian(data, offset, length, target) else aLawEncodeLittleEndian(data, offset, length, target)
        }

        /**
         * Encode little endian data.
         *
         * @param data
         * the input data.
         * @param offset
         * the input offset.
         * @param length
         * the input length.
         * @param target
         * the target array to fill.
         */
        private fun aLawEncodeLittleEndian(data: ByteArray, offset: Int, length: Int, target: ByteArray) {
            val size = length / 2
            for (i in 0 until size) target[i] = aLawEncode(data[offset + 2 * i + 1].toInt() and 0xff shl 8
                    or (data[offset + 2 * i].toInt() and 0xff))
        }

        /**
         * Encode big endian data.
         *
         * @param data
         * the input data.
         * @param offset
         * the input offset.
         * @param length
         * the input length.
         * @param target
         * the target array to fill.
         */
        private fun aLawEncodeBigEndian(data: ByteArray, offset: Int, length: Int, target: ByteArray) {
            val size = length / 2
            for (i in 0 until size) target[i] = aLawEncode(data[offset + 2 * i + 1].toInt() and 0xff
                    or (data[offset + 2 * i].toInt() and 0xff shl 8))
        }

        /**
         * Encode a pcm value into a a-law byte
         *
         * @param pcm
         * A 16-bit pcm value
         * @return A a-law encoded byte
         */
        private fun aLawEncode(pcm: Int): Byte {
            return pcmToALawMap[uShortToInt((pcm and 0xffff).toShort())]
        }

        /**
         * Encode one a-law byte from a 16-bit signed integer. Internal use only.
         *
         * @param pcmByte
         * A 16-bit signed pcm value
         * @return A a-law encoded byte
         */
        private fun encode(pcmByte: Int): Byte {
            // Get the sign bit. Shift it for later use without further modification
            var pcm = pcmByte
            val sign = pcm and 0x8000 shr 8
            // If the number is negative, make it positive (now it's a magnitude)
            if (sign != 0) pcm = -pcm
            // The magnitude must fit in 15 bits to avoid overflow
            if (pcm > MAX) pcm = MAX

            /*
		 * Finding the "exponent" Bits: 1 2 3 4 5 6 7 8 9 A B C D E F G S 7 6 5 4 3 2 1 0 0 0 0 0 0
		 * 0 0 We want to find where the first 1 after the sign bit is. We take the corresponding
		 * value from the second row as the exponent value. (i.e. if first 1 at position 7 ->
		 * exponent = 2) The exponent is 0 if the 1 is not found in bits 2 through 8. This means the
		 * exponent is 0 even if the "first 1" doesn't exist.
		 */
            var exponent = 7
            // Move to the right and decrement exponent until
            // we hit the 1 or the exponent hits 0
            var expMask = 0x4000
            while (pcm and expMask == 0 && exponent > 0) {
                exponent--
                expMask = expMask shr 1
            }

            /*
		 * The last part - the "mantissa" We need to take the four bits after the 1 we just found.
		 * To get it, we shift 0x0f : 1 2 3 4 5 6 7 8 9 A B C D E F G S 0 0 0 0 0 1 . . . . . . . .
		 * . (say that exponent is 2) . . . . . . . . . . . . 1 1 1 1 We shift it 5 times for an
		 * exponent of two, meaning we will shift our four bits (exponent + 3) bits. For
		 * convenience, we will actually just shift the number, then AND with 0x0f.
		 * 
		 * NOTE: If the exponent is 0: 1 2 3 4 5 6 7 8 9 A B C D E F G S 0 0 0 0 0 0 0 Z Y X W V U T
		 * S (we know nothing about bit 9) . . . . . . . . . . . . 1 1 1 1 We want to get ZYXW,
		 * which means a shift of 4 instead of 3
		 */
            val mantissa = pcm shr if (exponent == 0) 4 else exponent + 3 and 0x0f

            // The a-law byte bit arrangement is SEEEMMMM
            // (Sign, Exponent, and Mantissa.)
            val alaw = (sign or (exponent shl 4) or mantissa).toByte()

            // Last is to flip every other bit, and the sign bit (0xD5 = 1101 0101)
            return (alaw.toInt() xor 0xD5).toByte()
        }
    }
}