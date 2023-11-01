/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.gsm

import net.sf.fmj.media.AbstractPacketizer
import javax.media.Format
import javax.media.format.AudioFormat

/**
 * GSM/RTP packetizer Codec.
 *
 * @author Martin Harvan
 * @author Damian Minkov
 */
class Packetizer : AbstractPacketizer() {
    override fun getName(): String {
        return "GSM Packetizer"
    }

    // TODO: move to base class?
    private var outputFormats = arrayOf<Format?>(AudioFormat(AudioFormat.GSM_RTP, 8000.0, 8,
            1, Format.NOT_SPECIFIED, AudioFormat.SIGNED, 264, Format.NOT_SPECIFIED.toDouble(), Format.byteArray))

    /**
     * Constructs a new `Packetizer`.
     */
    init {
        inputFormats = arrayOf<Format>(AudioFormat(AudioFormat.GSM, 8000.0, 8, 1,
                Format.NOT_SPECIFIED, AudioFormat.SIGNED, 264, Format.NOT_SPECIFIED.toDouble(), Format.byteArray))
    }

    override fun getSupportedOutputFormats(input: Format?): Array<Format?> {
        return if (input == null) outputFormats else {
            if (input !is AudioFormat) {
                return arrayOf(null)
            }
            val inputCast = input
            if ((inputCast.encoding != AudioFormat.GSM || inputCast.sampleSizeInBits != 8 && inputCast.sampleSizeInBits != Format.NOT_SPECIFIED || inputCast.channels != 1 && inputCast.channels != Format.NOT_SPECIFIED || inputCast.frameSizeInBits != 264) && inputCast.frameSizeInBits != Format.NOT_SPECIFIED) {
                return arrayOf(null)
            }
            val result = AudioFormat(AudioFormat.GSM_RTP,
                    inputCast.sampleRate, 8, 1, inputCast.endian, inputCast.signed, 264,
                    inputCast.frameRate, inputCast.dataType)
            arrayOf(result)
        }
    }

    override fun open() {
        setPacketSize(Packetizer.Companion.PACKET_SIZE)
    }

    override fun close() {}
    override fun setInputFormat(f: Format): Format {
        return super.setInputFormat(f)
    }

    override fun setOutputFormat(f: Format): Format {
        return super.setOutputFormat(f)
    }

    companion object {
        private const val PACKET_SIZE = 33
    }
}