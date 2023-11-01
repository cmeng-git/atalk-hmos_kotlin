/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec

import android.net.IpSecManager
import net.sf.fmj.media.AbstractCodec
import net.sf.fmj.media.AbstractPlugIn

import javax.media.Buffer
import javax.media.Effect
import javax.media.Format
import javax.media.ResourceUnavailableException
import javax.media.format.YUVFormat

/**
 * Extends FMJ's `AbstractCodec` to make it even easier to implement a `Codec`.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class AbstractCodec2 protected constructor(
        /**
         * The name of this `PlugIn`.
         */
        private val name: String,
        private val formatClass: Class<out Format>,
        private val supportedOutputFormats: Array<out Format>,
) : AbstractCodec() {
    /**
     * The bitmap/flag mask of optional features supported by this `AbstractCodec2` such as
     * [.BUFFER_FLAG_FEC] and [.BUFFER_FLAG_PLC].
     */
    protected var features = 0

    /**
     * The total input length processed by all invocations of [.process].
     * Introduced for the purposes of debugging at the time of this writing.
     */
    private var inLenProcessed = 0L

    /**
     * The total output length processed by all invocations of [.process].
     * Introduced for the purposes of debugging at the time of this writing.
     */
    private var outLenProcessed = 0L
    override fun close() {
        if (!opened) return
        doClose()
        opened = false
        super.close()
    }

    protected open fun discardOutputBuffer(outputBuffer: Buffer) {
        outputBuffer.isDiscard = true
    }

    protected abstract fun doClose()

    /**
     * Opens this `Codec` and acquires the resources that it needs to operate. A call to
     * PlugIn.open on this instance will result in a call to `doOpen` only if
     * [AbstractCodec.opened] is `false`. All required input and/or output formats are
     * assumed to have been set on this `Codec` before `doOpen` is called.
     *
     * @throws ResourceUnavailableException if any of the resources that this `Codec` needs to operate cannot be acquired
     */
    @Throws(IpSecManager.ResourceUnavailableException::class)
    protected abstract fun doOpen()
    protected abstract fun doProcess(inBuf: Buffer, outBuf: Buffer): Int

    /**
     * Gets the `Format`s which are supported by this `Codec` as output when the input is in a specific `Format`.
     *
     * inputFormat the `Format` of the input for which the supported
     * output `Format`s are to be returned
     * @return an array of `Format`s supported by this `Codec` as output when the
     * input is in the specified `inputFormat`
     */
    protected open fun getMatchingOutputFormats(inputFormat: Format): Array<out Format> {
        /*
         * An Effect is a Codec that does not modify the Format of the data, it modifies the contents.
         */
        return if (this is Effect) {
            arrayOf(inputFormat)
        }
        else {
            supportedOutputFormats.clone() //?: EMPTY_FORMATS
        }
    }

    override fun getName(): String {
        return name
    }

    /**
     * Implements [AbstractCodec.getSupportedOutputFormats].
     *
     * inputFormat input format
     * @return array of supported output format
     * @see AbstractCodec.getSupportedOutputFormats
     */
    override fun getSupportedOutputFormats(inputFormat: Format?): Array<out Format> {
        if (inputFormat == null) return supportedOutputFormats
        return if (!formatClass.isInstance(inputFormat) || matches(inputFormat, inputFormats) == null) EMPTY_FORMATS else getMatchingOutputFormats(inputFormat)
    }

    /**
     * Opens this `PlugIn` software or hardware component and acquires the resources that it
     * needs to operate. All required input and/or output formats have to be set on this
     * `PlugIn` before `open` is called. Buffers should not be passed into this
     * `PlugIn` without first calling `open`.
     *
     * @throws ResourceUnavailableException if any of the resources that this `PlugIn` needs to operate cannot be acquired
     * @see AbstractPlugIn.open
     */
    @Throws(IpSecManager.ResourceUnavailableException::class)
    override fun open() {
        if (opened) return
        doOpen()
        opened = true
        super.open()
    }

    /**
     * Implements `AbstractCodec#process(Buffer, Buffer)`.
     *
     * inBuf input buffer
     * outBuf out buffer
     * @return `BUFFER_PROCESSED_OK` if the specified `inBuff` was successfully
     * processed or `BUFFER_PROCESSED_FAILED` if the specified was not successfully processed
     * @see AbstractCodec.process
     */
    override fun process(inBuf: Buffer, outBuf: Buffer): Int {
        if (!checkInputBuffer(inBuf)) return BUFFER_PROCESSED_FAILED
        if (isEOM(inBuf)) {
            propagateEOM(outBuf)
            return BUFFER_PROCESSED_OK
        }
        if (inBuf.isDiscard) {
            discardOutputBuffer(outBuf)
            return BUFFER_PROCESSED_OK
        }

        // Must update the inputFormat when there is a change in inBuf format
        val inFormat = inBuf.format
        if (inFormat != inputFormat && !inFormat.matches(inputFormat))
            setInputFormat(inFormat)
        var inLenProcessed = inBuf.length

        // Buffer.FLAG_SILENCE is set only when the intention is to drop the
        // specified input Buffer but to note that it has not been lost. The
        // latter is usually necessary if this AbstractCodec2 does Forward Error
        // Correction (FEC) and/or Packet Loss Concealment (PLC) and may cause
        // noticeable artifacts otherwise.
        val process = if (BUFFER_FLAG_FEC or BUFFER_FLAG_PLC and features == 0 && Buffer.FLAG_SILENCE and inBuf.flags != 0) {
            OUTPUT_BUFFER_NOT_FILLED
        } else {
            doProcess(inBuf, outBuf)
        }

        // Keep track of additional information for the purposes of debugging.
        if (process and INPUT_BUFFER_NOT_CONSUMED != 0) inLenProcessed -= inBuf.length
        if (inLenProcessed < 0) inLenProcessed = 0
        var outLenProcessed: Int
        if (process and BUFFER_PROCESSED_FAILED != 0
                || process and OUTPUT_BUFFER_NOT_FILLED != 0) {
            outLenProcessed = 0
        } else {
            outLenProcessed = outBuf.length
            if (outLenProcessed < 0) outLenProcessed = 0
        }
        this.inLenProcessed += inLenProcessed.toLong()
        this.outLenProcessed += outLenProcessed.toLong()
        return process
    }

    override fun setInputFormat(format: Format): Format? {
        return if (!formatClass.isInstance(format) || matches(format, inputFormats) == null) null else super.setInputFormat(format)
    }

    override fun setOutputFormat(format: Format): Format? {
        return when {
            !formatClass.isInstance(format) || matches(format, getMatchingOutputFormats(inputFormat)) == null -> null
            else -> super.setOutputFormat(format)
        }
    }

    /**
     * Updates the `format`, `length` and `offset` of a specific output
     * `Buffer` to specific values.
     *
     * outputBuffer the output `Buffer` to update the properties of
     * format the `Format` to set on `outputBuffer`
     * length the length to set on `outputBuffer`
     * offset the offset to set on `outputBuffer`
     */
    protected fun updateOutput(outputBuffer: Buffer, format: Format?, length: Int, offset: Int) {
        outputBuffer.format = format
        outputBuffer.length = length
        outputBuffer.offset = offset
    }

    protected fun validateShortArraySize(buffer: Buffer, newSize: Int): ShortArray {
        val data = buffer.data
        val newShorts: ShortArray
        if (data is ShortArray) {
            if (data.size >= newSize) return data
            newShorts = ShortArray(newSize)
            System.arraycopy(data, 0, newShorts, 0, data.size)
        } else {
            newShorts = ShortArray(newSize)
            buffer.length = 0
            buffer.offset = 0
        }
        buffer.data = newShorts
        return newShorts
    }

    /**
     * Initializes a new `AbstractCodec2` instance with a specific `PlugIn` name, a
     * specific `Class` of input and output `Format`s and a specific list of
     * `Format`s supported as output.
     *
     * name the `PlugIn` name of the new instance
     * formatClass the `Class` of input and output `Format`s supported by the new instance
     * supportedOutputFormats the list of `Format`s supported by the new instance as output
     */
    init {
        /*
         * An Effect is a Codec that does not modify the Format of the data, it modifies the contents.
         */
        if (this is Effect) inputFormats = supportedOutputFormats
    }

    companion object {
        /**
         * The `Buffer` flag which indicates that the respective `Buffer` contains audio
         * data which has been decoded as a result of the operation of FEC.
         */
        const val BUFFER_FLAG_FEC = 1 shl 24

        /**
         * The `Buffer` flag which indicates that the respective `Buffer` contains audio
         * data which has been decoded as a result of the operation of PLC.
         */
        const val BUFFER_FLAG_PLC = 1 shl 25

        /**
         * An empty array of `Format` element type. Explicitly defined to reduce unnecessary allocations.
         */
        val EMPTY_FORMATS = arrayOf<Format>()

        /**
         * The maximum number of lost sequence numbers to conceal with packet loss mitigation
         * techniques such as Forward Error Correction (FEC) and Packet Loss Concealment (PLC)
         * when dealing with audio stream.
         */
        const val MAX_AUDIO_SEQUENCE_NUMBERS_TO_PLC = 3

        /**
         * The maximum (RTP) sequence number value.
         */
        private const val SEQUENCE_MAX = 65535

        /**
         * The minimum (RTP) sequence number value.
         */
        private const val SEQUENCE_MIN = 0

        /**
         * Calculates the number of sequences which have been lost i.e. which have not been received.
         *
         * lastSeqNo the last received sequence number (prior to the current sequence number represented by
         * `seqNo`.) May be [Buffer.SEQUENCE_UNKNOWN]. May be equal to
         * `seqNo` for the purposes of Codec implementations which repeatedly process one
         * and the same input Buffer multiple times.
         * seqNo the current sequence number. May be equal to `lastSeqNo` for the purposes of
         * Codec implementations which repeatedly process the same input Buffer multiple times.
         * @return the number of sequences (between `lastSeqNo` and `seqNo`) which have
         * been lost i.e. which have not been received
         */
        fun calculateLostSeqNoCount(lastSeqNo: Long, seqNo: Long): Int {
            if (lastSeqNo == Buffer.SEQUENCE_UNKNOWN) return 0
            val delta = (seqNo - lastSeqNo).toInt()

            /*
         * We explicitly allow the same sequence number to be received multiple times for the purposes of
         * Codec implementations which repeatedly process the one and the same input Buffer multiple times.
         */
            return if (delta == 0) 0 else if (delta > 0) delta - 1
            // The sequence number has not wrapped yet.
            else delta + SEQUENCE_MAX // The sequence number has wrapped.
        }

        /**
         * Increments a specific sequence number and makes sure that the result stays within the range
         * of valid RTP sequence number values.
         *
         * @param seqNo_ the sequence number to increment
         * @return a sequence number which represents an increment over the specified `seqNo`
         * within the range of valid RTP sequence number values.
         */
        fun incrementSeqNo(seqNo_: Long): Long {
            var seqNo = seqNo_
            seqNo++
            if (seqNo > SEQUENCE_MAX) seqNo = SEQUENCE_MIN.toLong()
            return seqNo
        }

        /**
         * Utility to perform format matching.
         *
         * @param inFormat input format
         * @param outFormats array of output formats
         * @return the first output format that is supported
         */
        fun matches(inFormat: Format, outFormats: Array<out Format>): Format? {
            for (outFormat in outFormats) {
                if (inFormat.matches(outFormat)) {
                    return outFormat
                }
            }
            return null
        }

        fun specialize(yuvFormat: YUVFormat, dataType: Class<*>): YUVFormat {
            val sizeYuv = yuvFormat.size
            var strideY = yuvFormat.strideY

            if (strideY == Format.NOT_SPECIFIED && null != sizeYuv) strideY = sizeYuv.width

            var strideUV = yuvFormat.strideUV
            if (strideUV == Format.NOT_SPECIFIED && strideY != Format.NOT_SPECIFIED) strideUV = (strideY + 1) / 2

            var offsetY = yuvFormat.offsetY
            if (offsetY == Format.NOT_SPECIFIED) offsetY = 0

            var offsetU = yuvFormat.offsetU
            if (offsetU == Format.NOT_SPECIFIED && strideY != Format.NOT_SPECIFIED && sizeYuv != null) {
                offsetU = offsetY + strideY * sizeYuv.height
            }

            var offsetV = yuvFormat.offsetV
            if (offsetV == Format.NOT_SPECIFIED && offsetU != Format.NOT_SPECIFIED && strideUV != Format.NOT_SPECIFIED && sizeYuv != null) {
                offsetV = offsetU + strideUV * ((sizeYuv.height + 1) / 2)
            }

            val maxDataLength = when {
                strideY != Format.NOT_SPECIFIED && strideUV != Format.NOT_SPECIFIED && sizeYuv != null -> strideY * sizeYuv.height + 2 * strideUV * ((sizeYuv.height + 1) / 2) + FFmpeg.FF_INPUT_BUFFER_PADDING_SIZE
                else -> Format.NOT_SPECIFIED
            }
            return YUVFormat(
                    sizeYuv,
                    maxDataLength,
                    dataType ?: yuvFormat.dataType,
                    yuvFormat.frameRate,
                    YUVFormat.YUV_420,
                    strideY, strideUV,
                    offsetY, offsetU, offsetV)
        }

        /**
         * Ensures that the value of the `data` property of a specific `Buffer` is an
         * array of `byte`s whose length is at least a specific number of bytes.
         *
         * @param buffer the `Buffer` whose `data` property value is to be validated
         * @param newSize the minimum length of the array of `byte` which is to be the value of the `data` property of `buffer`
         * @param arraycopy `true` to copy the bytes which are in the value of the `data` property
         * of `buffer` at the time of the invocation of the method if the value of the
         * `data` property of `buffer` is an array of `byte` whose length is less than `newSize`; otherwise, `false`
         *
         * @return an array of `byte`s which is the value of the `data` property of
         * `buffer` and whose length is at least `newSize` number of bytes
         */
        fun validateByteArraySize(buffer: Buffer, newSize: Int, arraycopy: Boolean): ByteArray {
            val data = buffer.data
            val newBytes: ByteArray
            if (data is ByteArray) {
                if (data.size < newSize) {
                    newBytes = ByteArray(newSize)
                    buffer.data = newBytes
                    if (arraycopy) {
                        System.arraycopy(data, 0, newBytes, 0, data.size)
                    } else {
                        buffer.length = 0
                        buffer.offset = 0
                    }
                } else {
                    newBytes = data
                }
            } else {
                newBytes = ByteArray(newSize)
                buffer.data = newBytes
                buffer.length = 0
                buffer.offset = 0
            }
            return newBytes
        }

        private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()
        fun bytesToHex(bytes: ByteArray, length: Int): String {
            var k = 0
            val hexChars = CharArray(length * 2 + length / 4)
            for (j in 0 until length) {
                val v = bytes[j].toInt() and 0xFF
                hexChars[j * 2 + k] = HEX_ARRAY[v ushr 4]
                hexChars[j * 2 + 1 + k] = HEX_ARRAY[v and 0x0F]
                if (j % 4 == 3) {
                    hexChars[j * 2 + 2 + k++] = 0x20.toChar()
                }
            }
            return String(hexChars)
        }
    }
}