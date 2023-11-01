/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.g729

import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.codec.Constants
import org.atalk.service.neomedia.control.AdvancedAttributesAwareCodec
import org.atalk.util.ArrayIOUtils.readShort
import java.awt.Component
import javax.media.Buffer
import javax.media.Format
import javax.media.PlugIn
import javax.media.ResourceUnavailableException
import javax.media.format.AudioFormat

/**
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class JNIEncoder : AbstractCodec2("G.729 JNI Encoder", AudioFormat::class.java, JNIDecoder.SUPPORTED_INPUT_FORMATS), AdvancedAttributesAwareCodec {
    /**
     * The count of the output frames to packetize. By default we packetize 2 audio frames in one G729 packet.
     */
    private var OUTPUT_FRAMES_COUNT = 2
    private var encoder: Long = 0
    private var outFrameCount = 0

    /**
     * The previous input if it was less than the input frame size and which is to be
     * prepended to the next input in order to form a complete input frame.
     */
    private var prevIn: ByteArray? = null

    /**
     * The length of the previous input if it was less than the input frame size and which is to be
     * prepended to the next input in order to form a complete input frame.
     */
    private var prevInLength = 0
    private var bitStream: ByteArray? = null
    private var sp16: ShortArray? = null

    /**
     * The duration an output `Buffer` produced by this `Codec` in nanosecond.
     * We packetize 2 audio frames in one G729 packet by default. i.e. 20mS
     */
    private var duration = OUTPUT_FRAME_SIZE_IN_BYTES * OUTPUT_FRAMES_COUNT * 1000000

    /**
     * Initializes a new JNIEncoder instance.
     */
    init {
        inputFormats = JNIDecoder.SUPPORTED_OUTPUT_FORMATS
    }

    /*
     * Implements AbstractCodec2#doClose().
     */
    override fun doClose() {
        G729.g729_encoder_close(encoder)
        prevIn = null
        prevInLength = 0
        sp16 = null
        bitStream = null
    }

    /**
     * Opens this `Codec` and acquires the resources that it needs to operate. A call to
     * [PlugIn.open] on this instance will result in a call to `doOpen` only if
     * AbstractCodec.opened is `false`. All required input and/or output formats are
     * assumed to have been set on this `Codec` before `doOpen` is called.
     *
     * @throws ResourceUnavailableException if any of the resources that this `Codec` needs to operate cannot be acquired
     * @see AbstractCodec2.doOpen
     */
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        prevIn = ByteArray(INPUT_FRAME_SIZE_IN_BYTES)
        prevInLength = 0
        sp16 = ShortArray(G729.L_FRAME)
        bitStream = ByteArray(OUTPUT_FRAME_SIZE_IN_BYTES)
        outFrameCount = 0
        frameCount = 0

        // Set the encoder option according to user configuration; default to enable if none found.
        val cfg = LibJitsi.configurationService
        val g729Vad = cfg.getBoolean(Constants.PROP_G729_VAD, true)
        encoder = G729.g729_encoder_open(if (g729Vad) 1 else 0)
        if (encoder == 0L) throw ResourceUnavailableException("g729_encoder_open")
    }
    //****************************************************************************/
    /* bcg729Encoder :                                                           */
    /*    parameters:                                                            */
    /*      -(i) encoderChannelContext : context for this encoder channel        */
    /*      -(i) inputFrame : 80 samples (16 bits PCM)                           */
    /*      -(o) bitStream : The 15 parameters for a frame on 80 bits            */
    /*           on 80 bits (10 8bits words)                                     */
    /*                                                                           */
    //****************************************************************************/
    /**
     * Implements AbstractCodec2#doProcess(Buffer, Buffer).
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        var inLength = inBuf.length
        var inOffset = inBuf.offset
        val `in` = inBuf.data as ByteArray

        // Need INPUT_FRAME_SIZE_IN_BYTES samples in input before process
        if (prevInLength + inLength < INPUT_FRAME_SIZE_IN_BYTES) {
            System.arraycopy(`in`, inOffset, prevIn as Any, prevInLength, inLength)
            prevInLength += inLength
            return BUFFER_PROCESSED_OK or OUTPUT_BUFFER_NOT_FILLED
        }
        var readShorts = 0
        if (prevInLength > 0) {
            readShorts += readShorts(prevIn, 0, sp16, 0, prevInLength / 2)
            prevInLength = 0
        }
        readShorts = readShorts(`in`, inOffset, sp16, readShorts, sp16!!.size - readShorts)
        val readBytes = 2 * readShorts
        inLength -= readBytes
        inBuf.length = inLength
        inOffset += readBytes
        inBuf.offset = inOffset
        val bsLength = G729.g729_encoder_process(encoder, sp16, bitStream)
        //        if ((frameCount % 100) == 0 || frameCount < 10) {
//            Timber.w("G729 Encoded frame: %s: %s", frameCount, bytesToHex(bitStream, bsLength));
//        }
        var outLength = outBuf.length
        val outOffset = outBuf.offset
        val output = validateByteArraySize(outBuf,
                outOffset + OUTPUT_FRAMES_COUNT * OUTPUT_FRAME_SIZE_IN_BYTES, true)
        val outFrameOffset = outOffset + OUTPUT_FRAME_SIZE_IN_BYTES * outFrameCount
        System.arraycopy(bitStream as Any, 0, output, outFrameOffset, bsLength)
        outLength += OUTPUT_FRAME_SIZE_IN_BYTES
        outBuf.length = outLength
        outBuf.format = outputFormat
        var ret = BUFFER_PROCESSED_OK
        if (outFrameCount == OUTPUT_FRAMES_COUNT - 1) {
            outFrameCount = 0
        } else {
            outFrameCount++
            frameCount++
            ret = ret or OUTPUT_BUFFER_NOT_FILLED
        }

        if (inLength > 0) {
            ret = ret or INPUT_BUFFER_NOT_CONSUMED
        }

        if (ret == BUFFER_PROCESSED_OK) {
            updateOutput(outBuf, getOutputFormat(), outLength, outOffset)
            outBuf.duration = duration.toLong()
        }
        return ret
    }

    /**
     * Get the output format.
     *
     * @return output format
     * @see net.sf.fmj.media.AbstractCodec.getOutputFormat
     */
    public override fun getOutputFormat(): Format {
        var outputFormat = super.getOutputFormat()
        if (outputFormat != null && outputFormat.javaClass == AudioFormat::class.java) {
            val af = outputFormat as AudioFormat
            outputFormat = setOutputFormat(object : AudioFormat(af.encoding, af.sampleRate,
                    af.sampleSizeInBits, af.channels, af.endian, af.signed,
                    af.frameSizeInBits, af.frameRate, af.dataType) {
                val serialVersionUID = 0L
                override fun computeDuration(length: Long): Long {
                    return duration.toLong()
                }
            })
        }
        return outputFormat
    }

    override fun discardOutputBuffer(outputBuffer: Buffer) {
        super.discardOutputBuffer(outputBuffer)
        outFrameCount = 0
    }

    /**
     * Sets the additional attributes to `attributes`
     *
     * @param attributes The additional attributes to set
     */
    override fun setAdvancedAttributes(attributes: Map<String, String>) {
        try {
            val s = attributes["ptime"]
            if (s != null && s.isNotEmpty()) {
                val ptime = s.toInt()
                OUTPUT_FRAMES_COUNT = ptime / OUTPUT_FRAME_SIZE_IN_BYTES
                duration = OUTPUT_FRAME_SIZE_IN_BYTES * OUTPUT_FRAMES_COUNT * 1000000
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * Not used.
     *
     * @return null as it is not used.
     */
    override fun getControlComponent(): Component? {
        return null
    }

    companion object {
        private const val INPUT_FRAME_SIZE_IN_BYTES = 2 * G729.L_FRAME
        private const val OUTPUT_FRAME_SIZE_IN_BYTES = G729.L_FRAME / 8
        private var frameCount = 0
        private fun readShorts(`in`: ByteArray?, inOffset: Int, out: ShortArray?, outOffset: Int, outLength: Int): Int {
            var o = outOffset
            var i = inOffset
            while (o < outLength) {
                out!![o] = readShort(`in`!!, i)
                o++
                i += 2
            }
            return outLength
        }
    }
}