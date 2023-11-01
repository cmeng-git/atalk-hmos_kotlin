/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.format

import org.atalk.service.neomedia.format.AudioMediaFormat
import org.atalk.util.MediaType
import javax.media.Format
import javax.media.format.AudioFormat

/**
 * Implements `AudioMediaFormat` for the JMF `AudioFormat`.
 *
 * @author Lubomir Marinov
 */
class AudioMediaFormatImpl
/**
 * Initializes a new `AudioMediaFormatImpl` instance which is to provide an
 * implementation of `AudioMediaFormat` for a specific JMF `AudioFormat` and to
 * have a specific set of format-specific parameters.
 *
 * @param format the JMF `AudioFormat` the new instance is to wrap and provide an implementation
 * of `AudioMediaFormat` for
 * @param formatParameters the set of format-specific parameters of the new instance
 * @param advancedParameters the set of format-specific parameters of the new instance
 */
/**
 * Initializes a new `AudioMediaFormatImpl` instance which is to provide an
 * implementation of `AudioMediaFormat` for a specific JMF `AudioFormat`.
 *
 * @param format the JMF `AudioFormat` the new instance is to wrap and provide an implementation
 * of `AudioMediaFormat` for
 */
@JvmOverloads internal constructor(
        format: AudioFormat,
        formatParameters: Map<String, String>? = null,
        advancedParameters: Map<String, String>? = null
) : MediaFormatImpl<AudioFormat>(fixChannels(format), formatParameters, advancedParameters), AudioMediaFormat {
    /**
     * Initializes a new `AudioMediaFormatImpl` instance with the specified encoding and a
     * single audio channel.
     *
     * @param encoding the encoding of the new `AudioMediaFormatImpl` instance
     */
    constructor(encoding: String) : this(AudioFormat(encoding))

    /**
     * Initializes a new `AudioMediaFormatImpl` instance with the specified encoding, clock
     * rate and format parameters and a single audio channel.
     *
     * @param encoding the encoding of the new `AudioMediaFormatImpl` instance
     * @param clockRate the clock (i.e. sample) rate of the new `AudioMediaFormatImpl` instance
     * @param formatParameters any codec-specific parameters that have been received via SIP/SDP or XMPP/Jingle.
     * @param advancedParameters set of advanced parameters that have been received by SIP/SDP or XMPP/Jingle
     */
    internal constructor(
            encoding: String, clockRate: Double, formatParameters: Map<String, String>?,
            advancedParameters: Map<String, String>?
    ) : this(encoding, clockRate, 1, formatParameters, advancedParameters) {
    }
    /**
     * Initializes a new `AudioMediaFormatImpl` instance with the specified encoding, clock
     * rate, number of audio channels and format parameters.
     *
     * @param encoding the encoding of the new `AudioMediaFormatImpl` instance
     * @param clockRate the clock (i.e. sample) rate of the new `AudioMediaFormatImpl` instance
     * @param channels the number of available channels (1 for mono, 2 for stereo)
     * @param formatParameters any codec-specific parameters that have been received via SIP/SDP or XMPP/Jingle
     * @param advancedParameters any parameters that have been received via SIP/SDP or XMPP/Jingle
     */
    @JvmOverloads
    internal constructor(
            encoding: String, clockRate: Double, channels: Int = 1,
            formatParameters: Map<String, String>? = null,
            advancedParameters: Map<String, String>? = null
    ) : this(AudioFormat(
            encoding,
            clockRate,
            AudioFormat.NOT_SPECIFIED,
            channels),
            formatParameters, advancedParameters)

    /**
     * Gets the number of audio channels associated with this `AudioMediaFormat`.
     *
     * @return the number of audio channels associated with this `AudioMediaFormat`
     * @see AudioMediaFormat.channels
     */
    override val channels: Int
        get() {
            val channels = format!!.channels
            return if (Format.NOT_SPECIFIED == channels) 1 else channels
        }

    /**
     * Gets the clock rate associated with this `MediaFormat`.
     *
     * @return the clock rate associated with this `MediaFormat`
     * @see MediaFormat.clockRate
     */
    override val clockRate: Double
        get() = format!!.sampleRate

    /**
     * Gets the type of this `MediaFormat` which is [MediaType.AUDIO] for`AudioMediaFormatImpl` instances.
     *
     * @return the `MediaType` that this format represents and which is
     * `MediaType.AUDIO` for `AudioMediaFormatImpl` instances
     * @see MediaFormat.MediaType
     */
    override val mediaType: MediaType
        get() = MediaType.AUDIO

    companion object {
        /**
         * Gets an `AudioFormat` instance which matches a specific `AudioFormat` and has
         * 1 channel if the specified `AudioFormat` has its number of channels not specified.
         *
         * @param fmt the `AudioFormat` to get a match of
         * @return if the specified `format` has a specific number of channels, `format`;
         * otherwise, a new `AudioFormat` instance which matches `format` and has 1 channel
         */
        private fun fixChannels(fmt: AudioFormat): AudioFormat {
            var format = fmt
            if (Format.NOT_SPECIFIED == format.channels) format = format.intersects(AudioFormat(
                    format.encoding,
                    format.sampleRate,
                    format.sampleSizeInBits,
                    1)
            ) as AudioFormat
            return format
        }
    }
}