/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.gsm

import net.sf.fmj.media.AbstractDePacketizer
import javax.media.Format
import javax.media.format.AudioFormat

/**
 *
 * DePacketizer for GSM/RTP. Doesn't have to do much, just copies input to output. Uses
 * buffer-swapping observed in debugging and seen in other open-source DePacketizer implementations.
 *
 * @author Martin Harvan
 * @author Damian Minkov
 */
open class DePacketizer : AbstractDePacketizer() {
    override fun getName(): String {
        return "GSM DePacketizer"
    }

    // TODO: move to base class?
    protected var outputFormats = arrayOf<Format?>(AudioFormat(AudioFormat.GSM, 8000.0, 8, 1,
            -1, AudioFormat.SIGNED, 264, -1.0, Format.byteArray))

    /**
     * Constructs a new `DePacketizer`.
     */
    init {
        inputFormats = arrayOf<Format>(AudioFormat(AudioFormat.GSM_RTP, 8000.0, 8, 1,
                Format.NOT_SPECIFIED, AudioFormat.SIGNED, 264, Format.NOT_SPECIFIED.toDouble(), Format.byteArray))
    }

    override fun getSupportedOutputFormats(input: Format?): Array<Format?> {
        return if (input == null) outputFormats else {
            if (input !is AudioFormat) {
                return arrayOf(null)
            }
            val inputCast = input
            if (inputCast.encoding != AudioFormat.GSM_RTP) {
                return arrayOf(null)
            }
            val result = AudioFormat(AudioFormat.GSM, inputCast.sampleRate,
                    inputCast.sampleSizeInBits, inputCast.channels, inputCast.endian,
                    inputCast.signed, inputCast.frameSizeInBits, inputCast.frameRate,
                    inputCast.dataType)
            arrayOf(result)
        }
    }

    override fun open() {}
    override fun close() {}
    override fun setInputFormat(f: Format): Format {
        return super.setInputFormat(f)
    }

    override fun setOutputFormat(f: Format): Format {
        return super.setOutputFormat(f)
    }
}