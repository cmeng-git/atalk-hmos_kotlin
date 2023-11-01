/*
 * Jitsi Videobridge, OpenSource video conferencing.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.recording

import net.sf.fmj.media.codec.JavaSoundCodec
import net.sf.fmj.media.multiplexer.BasicMux
import net.sf.fmj.media.renderer.audio.JavaSoundUtils
import javax.media.Format
import javax.media.format.AudioFormat
import javax.media.protocol.FileTypeDescriptor

/**
 * Implements a multiplexer for WAV files based on FMJ's `BasicMux`.
 *
 * @author Boris Grozev
 */
class BasicWavMux : BasicMux() {
    /**
     * Initializes a `BasicWavMux` instance.
     */
    init {
        supportedInputs = arrayOf(SUPPORTED_INPUT_FORMAT)
        supportedOutputs = arrayOfNulls(1)
        supportedOutputs[0] = FileTypeDescriptor(FileTypeDescriptor.WAVE)
    }

    /**
     * {@inheritDoc}
     */
    override fun getName(): String {
        return "libjitsi wav mux"
    }

    /**
     * {@inheritDoc}
     */
    override fun setInputFormat(format: Format, trackID: Int): Format? {
        if (format !is AudioFormat) return null
        return if (AudioFormat.LINEAR != format.getEncoding()) null else format.also { inputs[0] = it }
    }

    /**
     * {@inheritDoc}
     */
    public override fun writeHeader() {
        val javaSoundAudioFormat = JavaSoundUtils.convertFormat(inputs[0] as AudioFormat)
        val header = JavaSoundCodec.createWavHeader(javaSoundAudioFormat)
        if (header != null) write(header, 0, header.size)
    }

    companion object {
        /**
         * The input formats supported by this `BasicWavMux`.
         */
        var SUPPORTED_INPUT_FORMAT = AudioFormat(AudioFormat.LINEAR)
    }
}