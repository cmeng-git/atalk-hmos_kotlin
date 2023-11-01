/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.mp3

import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.codec.FFmpeg.avcodeccontext_set_bit_rate
import org.atalk.impl.neomedia.codec.audio.FFmpegAudioEncoder
import org.atalk.service.neomedia.control.FlushableControl
import java.awt.Component
import javax.media.Format
import javax.media.format.AudioFormat

/**
 * Implements a MP3 encoder using the native FFmpeg library.
 *
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
class JNIEncoder : FFmpegAudioEncoder("MP3 JNI Encoder", FFmpeg.CODEC_ID_MP3, SUPPORTED_OUTPUT_FORMATS), FlushableControl {
    /**
     * Initializes a new `JNIEncoder` instance.
     */
    init {
        inputFormats = SUPPORTED_INPUT_FORMATS
        addControl(this)
    }

    /**
     * {@inheritDoc}
     */
    override fun configureAVCodecContext(avctx: Long, format: AudioFormat?) {
        super.configureAVCodecContext(avctx, format)
        avcodeccontext_set_bit_rate(avctx, 128000)
    }

    /**
     * {@inheritDoc}
     */
    @Synchronized
    override fun flush() {
        prevInLen = 0
    }

    /**
     * {@inheritDoc}
     */
    override fun getControlComponent(): Component? {
        return null
    }

    companion object {
        /**
         * The list of `Format`s of audio data supported as input by `JNIEncoder`
         * instances.
         */
        private val SUPPORTED_INPUT_FORMATS = arrayOf(
                AudioFormat(
                        AudioFormat.LINEAR,
                        Format.NOT_SPECIFIED /* sampleRate */.toDouble(),
                        16,
                        Format.NOT_SPECIFIED /* channels */,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED /* frameRate */.toDouble(),
                        Format.byteArray)
        )

        /**
         * The list of `Format`s of audio data supported as output by `JNIEncoder`
         * instances.
         */
        private val SUPPORTED_OUTPUT_FORMATS = arrayOf(AudioFormat(AudioFormat.MPEGLAYER3))

        init {
            assertFindAVCodec(FFmpeg.CODEC_ID_MP3)
        }
    }
}