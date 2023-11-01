/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.g729

import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.util.ArrayIOUtils.writeShort
import javax.media.Buffer
import javax.media.Format
import javax.media.PlugIn
import javax.media.ResourceUnavailableException
import javax.media.format.AudioFormat

/**
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class JNIDecoder : AbstractCodec2("G.729 JNI Decoder", AudioFormat::class.java, SUPPORTED_OUTPUT_FORMATS) {
    private var decoder: Long = 0
    private var bitStream: ByteArray? = null
    private var sp16: ShortArray? = null

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
        G729.g729_decoder_close(decoder)
        sp16 = null
        bitStream = null
    }

    /**
     * Open this `Codec` and acquire the resources that it needs to operate. A call to
     * [PlugIn.open] on this instance will result in a call to `doOpen` only if
     * AbstractCodec.opened is `false`. All required input and/or output formats are
     * assumed to have been set on this `Codec` before `doOpen` is called.
     *
     * @throws ResourceUnavailableException if any of the resources that this `Codec` needs to operate cannot be acquired
     * @see AbstractCodecExt.doOpen
     */
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        sp16 = ShortArray(G729.L_FRAME)
        bitStream = ByteArray(INPUT_FRAME_SIZE_IN_BYTES)
        decoder = G729.g729_decoder_open()
    }
    //****************************************************************************/
    /* bcg729Decoder :                                                           */
    /*    parameters:                                                            */
    /*      -(i) decoderChannelContext : the channel context data                */
    /*      -(i) bitStream : 15 parameters on 80 bits                            */
    /*      -(i): bitStreamLength : in bytes, length of previous buffer          */
    /*      -(i) frameErased: flag: true, frame has been erased                  */
    /*      -(i) SIDFrameFlag: flag: true, frame is a SID one                    */
    /*      -(i) rfc3389PayloadFlag: true when CN payload follow rfc3389         */
    /*      -(o) signal : a decoded frame 80 samples (16 bits PCM)               */
    /*                                                                           */
    //****************************************************************************/
    /**
     * Implements AbstractCodec2#doProcess(Buffer, Buffer).
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        var inLength = inBuf.length
        /*
         * Decode as many G.729 frames as possible in one go in order to mitigate an issue with
         * sample rate conversion which leads to audio glitches.
         */
        val frameCount = inLength / INPUT_FRAME_SIZE_IN_BYTES
        if (frameCount < 1) {
            discardOutputBuffer(outBuf)
            return BUFFER_PROCESSED_OK or OUTPUT_BUFFER_NOT_FILLED
        }
        val `in` = inBuf.data as ByteArray
        var inOffset = inBuf.offset
        var outOffset = outBuf.offset
        val outLength = OUTPUT_FRAME_SIZE_IN_BYTES * frameCount
        val out = validateByteArraySize(outBuf, outOffset + outLength, false)
        for (i in 0 until frameCount) {
            System.arraycopy(`in`, inOffset, bitStream as Any, 0, INPUT_FRAME_SIZE_IN_BYTES)
            inLength -= INPUT_FRAME_SIZE_IN_BYTES
            inOffset += INPUT_FRAME_SIZE_IN_BYTES

//            if ((i % 50) == 0 || frameCount < 5) {
//                Timber.w("G729 Decode a frame: %s", bytesToHex(bitStream, 10));
//            }
            G729.g729_decoder_process(decoder, bitStream, INPUT_FRAME_SIZE_IN_BYTES, 0, 0, 0, sp16)
            writeShorts(sp16, out, outOffset)
            outOffset += OUTPUT_FRAME_SIZE_IN_BYTES
        }
        inBuf.length = inLength
        inBuf.offset = inOffset
        outBuf.length = outLength
        return BUFFER_PROCESSED_OK
    }

    companion object {
        private const val INPUT_FRAME_SIZE_IN_BYTES = G729.L_FRAME / 8
        private const val OUTPUT_FRAME_SIZE_IN_BYTES = 2 * G729.L_FRAME
        val SUPPORTED_INPUT_FORMATS = arrayOf<Format>(
                AudioFormat(
                        AudioFormat.G729_RTP,
                        8000.0,
                        Format.NOT_SPECIFIED /* sampleSizeInBits */,
                        1)
        )

        val SUPPORTED_OUTPUT_FORMATS = arrayOf<Format>(
                AudioFormat(
                        AudioFormat.LINEAR,
                        8000.0,
                        16,
                        1,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED /* frameRate */.toDouble(),
                        Format.byteArray) /* frameRate */
        )

        private fun writeShorts(`in`: ShortArray?, out: ByteArray, outOffset: Int) {
            var i = 0
            var o = outOffset
            while (i < `in`!!.size) {
                writeShort(`in`[i], out, o)
                i++
                o += 2
            }
        }
    }
}