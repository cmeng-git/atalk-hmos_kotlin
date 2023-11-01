/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.ilbc

import com.sun.media.controls.SilenceSuppressionAdapter
import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.service.neomedia.codec.Constants
import javax.media.Buffer
import javax.media.Format
import javax.media.format.AudioFormat

/**
 * Implements an iLBC decoder and RTP depacketizer as a [Codec].
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
class JavaDecoder : AbstractCodec2("iLBC Decoder", AudioFormat::class.java, arrayOf(AudioFormat(AudioFormat.LINEAR))) {
    /**
     * The `ilbc_decoder` adapted to `Codec` by this instance.
     */
    private var dec: ilbc_decoder? = null

    /**
     * The input length in bytes with which [.dec] has been initialized.
     */
    private var inputLength = 0

    /**
     * List of offsets for a "more than one" iLBC frame per RTP packet.
     */
    private val offsets = ArrayList<Int>()

    /**
     * Initializes a new iLBC `JavaDecoder` instance.
     */
    init {
        inputFormats = arrayOf<Format>(
                AudioFormat(
                        Constants.ILBC_RTP,
                        8000.0, 16, 1,
                        Format.NOT_SPECIFIED /* endian */,
                        Format.NOT_SPECIFIED /* signed */))
        addControl(SilenceSuppressionAdapter(this, false, false))
    }

    /**
     * Implements [AbstractCodecExt.doClose].
     *
     * @see AbstractCodecExt.doClose
     */
    override fun doClose() {
        dec = null
        inputLength = 0
    }

    /**
     * Implements [AbstractCodecExt.doOpen].
     *
     * @see AbstractCodecExt.doOpen
     */
    override fun doOpen() {}

    /**
     * Implements [AbstractCodecExt.doProcess].
     *
     * @param inBuf
     * @param outBuf
     * @return
     * @see AbstractCodecExt.doProcess
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        val input = inBuf.data as ByteArray
        val inputLength = inBuf.length
        if (offsets.size == 0 && inputLength > ilbc_constants.NO_OF_BYTES_20MS && inputLength != ilbc_constants.NO_OF_BYTES_30MS) {
            var nb = 0
            var len = 0
            if (inputLength % ilbc_constants.NO_OF_BYTES_20MS == 0) {
                nb = inputLength % ilbc_constants.NO_OF_BYTES_20MS
                len = ilbc_constants.NO_OF_BYTES_20MS
            }
            else if (inputLength % ilbc_constants.NO_OF_BYTES_30MS == 0) {
                nb = inputLength % ilbc_constants.NO_OF_BYTES_30MS
                len = ilbc_constants.NO_OF_BYTES_30MS
            }

            if (this.inputLength != len) initDec(len)
            for (i in 0 until nb) {
                offsets.add(inputLength + i * len)
            }
        }
        else if (this.inputLength != inputLength) initDec(inputLength)
        val outputLength = dec!!.ULP_inst!!.blockl * 2
        val output = validateByteArraySize(outBuf, outputLength, false)
        val outputOffset = 0
        var offsetToAdd = 0
        if (offsets.size > 0) offsetToAdd = offsets.removeAt(0)
        dec!!.decode(output, outputOffset, input, inBuf.offset + offsetToAdd, 1.toShort())
        updateOutput(outBuf, getOutputFormat(), outputLength, outputOffset)
        var flags = BUFFER_PROCESSED_OK
        if (offsets.size > 0) flags = flags or INPUT_BUFFER_NOT_CONSUMED
        return flags
    }

    override fun getMatchingOutputFormats(inputFormat: Format): Array<Format> {
        return arrayOf(AudioFormat(
                AudioFormat.LINEAR,
                (inputFormat as AudioFormat).sampleRate,
                16, 1,
                AudioFormat.LITTLE_ENDIAN,
                AudioFormat.SIGNED))
    }

    /**
     * Initializes [.dec] so that it processes a specific number of bytes as input.
     *
     * @param inputLength
     * the number of bytes of input to be processed by [.dec]
     */
    private fun initDec(inputLength: Int) {
        val mode = when (inputLength) {
            ilbc_constants.NO_OF_BYTES_20MS -> 20
            ilbc_constants.NO_OF_BYTES_30MS -> 30
            else -> throw IllegalArgumentException("inputLength")
        }
        dec = ilbc_decoder(mode, 1)
        this.inputLength = inputLength
    }
}