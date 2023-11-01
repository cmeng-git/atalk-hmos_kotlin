/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.ilbc

import com.sun.media.controls.SilenceSuppressionAdapter
import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.service.neomedia.codec.Constants
import org.atalk.service.neomedia.control.FormatParametersAwareCodec
import java.awt.Component
import javax.media.Buffer
import javax.media.Format
import javax.media.format.AudioFormat

/**
 * Implements an iLBC encoder and RTP packetizer as a Codec.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
class JavaEncoder : AbstractCodec2("iLBC Encoder", AudioFormat::class.java, arrayOf(AudioFormat(Constants.ILBC_RTP,
        8000.0, 16, 1, AudioFormat.LITTLE_ENDIAN, AudioFormat.SIGNED))), FormatParametersAwareCodec {
    /**
     * The duration an output `Buffer` produced by this `Codec`.
     */
    private var duration = 0

    /**
     * The `ilbc_encoder` adapted to `Codec` by this instance.
     */
    private var enc: ilbc_encoder? = null

    /**
     * The input length in bytes with which [.enc] has been initialized.
     */
    private var inLen = 0

    /**
     * The output length in bytes with which [.enc] has been initialized.
     */
    private var outLen = 0

    /**
     * The input from previous calls to [.doProcess] which has not been
     * consumed yet.
     */
    private var prevIn: ByteArray? = null

    /**
     * The number of bytes in [.prevIn] which have not been consumed yet.
     */
    private var prevInLen = 0

    /**
     * Initializes a new iLBC `JavaEncoder` instance.
     */
    init {
        inputFormats = arrayOf<Format>(AudioFormat(AudioFormat.LINEAR, 8000.0, 16, 1,
                AudioFormat.LITTLE_ENDIAN, AudioFormat.SIGNED))
        addControl(SilenceSuppressionAdapter(this, false, false))
        addControl(this)
    }

    /**
     * Implements [AbstractCodec2.doClose].
     *
     * @see AbstractCodec2.doClose
     */
    override fun doClose() {
        enc = null
        outLen = 0
        inLen = 0
        prevIn = null
        prevInLen = 0
        duration = 0
    }

    /**
     * Implements [AbstractCodec2.doOpen].
     *
     * @see AbstractCodec2.doOpen
     */
    override fun doOpen() {
        // if not already initialised, use the default value (30).
        if (enc == null) initEncoder(Constants.ILBC_MODE)
    }

    /**
     * Implements [AbstractCodec2.doProcess].
     *
     * @param inBuf
     * the input buffer
     * @param outBuf
     * the output buffer
     * @return the status of the processing, whether buffer is consumed/filled..
     * @see AbstractCodec2.doProcess
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        var inLen = inBuf.length
        var inByte = inBuf.data as ByteArray
        var inOff = inBuf.offset
        if (prevInLen != 0 || inLen < this.inLen) {
            var bytesToCopy = this.inLen - prevInLen
            if (bytesToCopy > inLen) bytesToCopy = inLen
            System.arraycopy(inByte, inOff, prevIn!!, prevInLen, bytesToCopy)
            prevInLen += bytesToCopy
            inBuf.length = inLen - bytesToCopy
            inBuf.offset = inOff + bytesToCopy
            inLen = prevInLen
            inByte = prevIn!!
            inOff = 0
        } else {
            inBuf.length = inLen - this.inLen
            inBuf.offset = inOff + this.inLen
        }
        var ret: Int
        if (inLen >= this.inLen) {
            /*
			 * If we are about to encode from prevInput, we already have prevInputLength taken into
			 * consideration by using prevInput in the first place and we have to make sure that we
			 * will not use the same prevInput more than once.
			 */
            prevInLen = 0
            val outOff = 0
            val out = validateByteArraySize(outBuf, outOff + outLen, true)
            enc!!.encode(out, outOff, inByte, inOff)
            updateOutput(outBuf, getOutputFormat(), outLen, outOff)
            outBuf.duration = duration.toLong()
            ret = BUFFER_PROCESSED_OK
        } else {
            ret = OUTPUT_BUFFER_NOT_FILLED
        }
        if (inBuf.length > 0) ret = ret or INPUT_BUFFER_NOT_CONSUMED
        return ret
    }

    /**
     * Implements [javax.media.Control.getControlComponent].
     */
    override fun getControlComponent(): Component? {
        return null
    }

    /**
     * Get the output format.
     *
     * @return output format
     * @see net.sf.fmj.media.AbstractCodec.getOutputFormat
     */
    public override fun getOutputFormat(): Format {
        var f = super.getOutputFormat()
        if (f != null && f.javaClass == AudioFormat::class.java) {
            val af = f as AudioFormat
            f = setOutputFormat(object : AudioFormat(af.encoding, af.sampleRate,
                    af.sampleSizeInBits, af.channels, af.endian, af.signed,
                    af.frameSizeInBits, af.frameRate, af.dataType) {
                override fun computeDuration(length: Long): Long {
                    return duration.toLong()
                }
            })
        }
        return f
    }

    /**
     * Init encoder with specified mode.
     *
     * @param mode
     * the mode to use.
     */
    private fun initEncoder(mode: Int) {
        enc = ilbc_encoder(mode)
        outLen = when (mode) {
            20 -> ilbc_constants.NO_OF_BYTES_20MS
            30 -> ilbc_constants.NO_OF_BYTES_30MS
            else -> throw IllegalStateException("mode")
        }
        /* mode is 20 or 30 ms, duration must be in nanoseconds */
        duration = mode * 1000000
        inLen = enc!!.ULP_inst!!.blockl * 2
        prevIn = ByteArray(inLen)
        prevInLen = 0
    }

    /**
     * Sets the format parameters to `fmtps`
     *
     * @param fmtps
     * The format parameters to set
     */
    override fun setFormatParameters(fmtps: Map<String, String>) {
        val modeStr = fmtps["mode"]
        if (modeStr != null) {
            try {
                val mode = Integer.valueOf(modeStr)

                // supports only mode 20 or 30
                if (mode == 20 || mode == 30) initEncoder(mode)
            } catch (ignore: Throwable) {
            }
        }
    }
}