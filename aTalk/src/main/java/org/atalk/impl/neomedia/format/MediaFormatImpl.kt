/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.format

import org.atalk.impl.neomedia.MediaUtils.getMediaFormat
import org.atalk.impl.neomedia.MediaUtils.getRTPPayloadType
import org.atalk.impl.neomedia.MediaUtils.jmfEncodingToEncoding
import org.atalk.service.neomedia.format.AudioMediaFormat
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.service.neomedia.format.MediaFormatFactory
import org.atalk.util.MediaType
import javax.media.Format
import javax.media.format.AudioFormat
import javax.media.format.VideoFormat

/**
 * Implements `MediaFormat` for the JMF `Format`.
 *
 * @param <T> the type of the wrapped `Format`
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
</T> */
abstract class MediaFormatImpl<T : Format?> protected constructor(
        format: T?,
        formatParameters: Map<String, String>? = null, advancedAttributes: Map<String, String>? = null,
) : MediaFormat {
    /**
     * The advanced parameters of this instance which have been received via SIP/SDP or XMPP/Jingle.
     */
    final override val advancedAttributes: MutableMap<String, String>
        /**
         * Returns a copy of the attribute properties of this instance. Modifications to the returned
         * Map do no affect the format properties of this instance.
         */
        get() {
            return HashMap(field)
        }

    /**
     * The additional codec settings.
     */
    private var codecSettings = EMPTY_FORMAT_PARAMETERS

    /**
     * The JMF `Format` this instance wraps and provides an implementation of `MediaFormat` for.
     */
    val format: T?

    /**
     * The codec-specific parameters of this instance which have been received via SIP/SDP or XMPP/Jingle.
     */
    final override val formatParameters: Map<String, String>
        /**
         * Returns a copy of the format properties of this instance.
         * Modifications to the returned Map do no affect the format properties of this instance.
         */
        get() {
            return if (field == EMPTY_FORMAT_PARAMETERS) EMPTY_FORMAT_PARAMETERS else HashMap(field)
        }

    /**
     * Initializes a new `MediaFormatImpl` instance which is to provide an implementation of
     * `MediaFormat` for a specific `Format` and which is to have a specific set of
     * codec-specific parameters.
     *
     * @param format the JMF `Format` the new instance is to provide an implementation of `MediaFormat`
     * @param formatParameters any codec-specific parameters that have been received via SIP/SDP or XMPP/Jingle
     * @param advancedAttributes any parameters that have been received via SIP/SDP or XMPP/Jingle
     */
    /**
     * Initializes a new `MediaFormatImpl` instance which is to provide an implementation of
     * `MediaFormat` for a specific `Format`.
     *
     * format the JMF `Format` the new instance is to provide an implementation of `MediaFormat`
     */
    init {
        if (format == null) throw NullPointerException("format")
        this.format = format
        this.formatParameters = when {
            formatParameters == null || formatParameters.isEmpty() -> EMPTY_FORMAT_PARAMETERS
            else -> HashMap(formatParameters)
        }
        this.advancedAttributes = when {
            advancedAttributes == null || advancedAttributes.isEmpty() -> mutableMapOf()
            else -> HashMap(advancedAttributes)
        }
    }

    /**
     * Determines whether a specific set of advanced attributes is equal to another set of advanced
     * attributes in the sense that they define an equal number of parameters and assign them equal
     * values. Since the values are `String`s, presumes that a value of `null` is
     * equal to the empty `String`.
     *
     *
     *
     * @param adv the first set of advanced attributes to be tested for equality
     * @param adv2 the second set of advanced attributes to be tested for equality
     * @return `true` if the specified sets of advanced attributes equal; `false`, otherwise
     */
    fun advancedAttributesAreEqual(adv: Map<String, String>?, adv2: Map<String, String>?): Boolean {
        if (adv == null && adv2 != null || adv != null && adv2 == null) return false
        if (adv == null && adv2 == null) return true
        if (adv!!.size != adv2!!.size) return false
        for ((key, value1) in adv) {
            val value = adv2[key]
            if (value == null) return false else if (value != value1) return false
        }
        return true
    }

    /**
     * Implements MediaFormat#equals(Object) and actually compares the encapsulated JMF `Format` instances.
     *
     * @param other the object that we'd like to compare `this` one to. 8*
     * @return `true` if the JMF `Format` instances encapsulated by this class are
     * equal and `false` otherwise.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (!javaClass.isInstance(other)) return false

        val mediaFormatImpl = other as MediaFormatImpl<T>
        return format == mediaFormatImpl.format
                && formatParametersAreEqual(formatParameters, mediaFormatImpl.formatParameters)
    }

    /**
     * Determines whether a specific set of format parameters is equal to another set of format
     * parameters in the sense that they define an equal number of parameters and assign them equal
     * values. Since the values are `String` s, presumes that a value of `null` is
     * equal to the empty `String`.
     *
     *
     * The two `Map` instances of format parameters to be checked for equality are presumed
     * to be modifiable in the sense that if the lack of a format parameter in a given `Map`
     * is equivalent to it having a specific value, an association of the format parameter to the
     * value in question may be added to or removed from the respective `Map` instance for
     * the purposes of determining equality.
     *
     *
     * @param fmtps1 the first set of format parameters to be tested for equality
     * @param fmtps2 the second set of format parameters to be tested for equality
     * @return `true` if the specified sets of format parameters are equal; `false`, otherwise
     */
    protected open fun formatParametersAreEqual(fmtps1: Map<String, String>?, fmtps2: Map<String, String>?): Boolean {
        return formatParametersAreEqual(encoding, fmtps1, fmtps2)
    }

    /**
     * {@inheritDoc}
     *
     *
     * The default implementation of `MediaFormatImpl` always returns `true` because
     * format parameters in general do not cause the distinction of payload types.
     *
     */
    override fun formatParametersMatch(fmtps: Map<String, String>?): Boolean {
        return true
    }
    /**
     * Returns additional codec settings.
     *
     * @return additional settings represented by a map.
     */
    /**
     * Sets additional codec settings.
     *
     * settings additional settings represented by a map.
     */
    override var additionalCodecSettings: Map<String, String>?
        get() = codecSettings
        set(settings) {
            codecSettings = if (settings == null || settings.isEmpty()) EMPTY_FORMAT_PARAMETERS else settings
        }

    /**
     * Check to see if advancedAttributes contains the specific parameter name-value pair
     *
     * @param parameterName the key of the <parameter></parameter> name-value pair
     * @return true if the <parameter></parameter> contains the specified key name
     */
    override fun hasParameter(parameterName: String): Boolean {
        return advancedAttributes.containsKey(parameterName)
    }

    /**
     * Remove the specific parameter name-value pair from advancedAttributes
     *
     * @param parameterName the key of the <parameter></parameter> name-value pair to be removed
     * @see .FORMAT_PARAMETER_ATTR_IMAGEATTR
     */
    override fun removeParameter(parameterName: String) {
        advancedAttributes.remove(parameterName)
    }

    /**
     * Returns a `String` representation of the clock rate associated with this
     * `MediaFormat` making sure that the value appears as an integer (i.e. its long-casted
     * value is equal to its original one) unless it is actually a non integer.
     *
     * @return a `String` representation of the clock rate associated with this `MediaFormat`.
     */
    override val clockRateString: String
        get() {
            val clockRate = clockRate
            val clockRateL = clockRate.toLong()
            return if (clockRateL.toDouble() == clockRate) clockRateL.toString() else clockRate.toString()
        }

    /**
     * Implements MediaFormat#getEncoding() and returns the encoding of the JMF `Format`
     * that we are encapsulating here but it is the RFC-known encoding and not the internal JMF encoding.
     *
     * @return the RFC-known encoding of the JMF `Format` that we are encapsulating
     */
    override val encoding: String
        get() {
            val jmfEncoding = jMFEncoding
            var encoding = jmfEncodingToEncoding(jmfEncoding)
            if (encoding == null) {
                encoding = jmfEncoding
                val encodingLength = encoding.length
                if (encodingLength > 3) {
                    val rtpPos = encodingLength - 4
                    if ("/rtp".equals(encoding.substring(rtpPos), ignoreCase = true)) encoding = encoding.substring(0,
                        rtpPos)
                }
            }
            return encoding
        }

    /**
     * Gets the encoding of the JMF `Format` represented by this instance as it is known to
     * JMF (in contrast to its RFC name).
     *
     * @return the encoding of the JMF `Format` represented by this instance as it is known
     * to JMF (in contrast to its RFC name)
     */
    val jMFEncoding: String
        get() = format!!.encoding// RFC 1890 erroneously assigned 8 kHz to the RTP clock rate for the
    // G722 payload format. The actual sampling rate for G.722 audio is 16 kHz.
    /**
     * Returns a `String` representation of the real used clock rate associated with this
     * `MediaFormat` making sure that the value appears as an integer (i.e. contains no
     * decimal point) unless it is actually a non integer. This function corrects the problem of
     * the G.722 codec which advertises its clock rate to be 8 kHz while 16 kHz is really used to
     * encode the stream (that's an error noted in the respective RFC and kept for the sake of compatibility.).
     *
     * @return a `String` representation of the real used clock rate associated with this
     * `MediaFormat`.
     */
    override val realUsedClockRateString: String
        get() =// RFC 1890 erroneously assigned 8 kHz to the RTP clock rate for the
                // G722 payload format. The actual sampling rate for G.722 audio is 16 kHz.
            if (encoding.equals("G722", ignoreCase = true)) {
                "16000"
            }
            else clockRateString

    /**
     * Gets the RTP payload type (number) of this `MediaFormat` as it is known in RFC 3551
     * "RTP Profile for Audio and Video Conferences with Minimal Control".
     *
     * @return the RTP payload type of this `MediaFormat` if it is known in RFC 3551 "RTP
     * Profile for Audio and Video Conferences with Minimal Control"; otherwise, [.RTP_PAYLOAD_TYPE_UNKNOWN]
     * @see MediaFormat .getRTPPayloadType
     */
    override val rtpPayloadType: Byte
        get() = getRTPPayloadType(jMFEncoding, clockRate)

    /**
     * Overrides Object#hashCode() because Object#equals(Object) is overridden.
     *
     * @return a hash code value for this `MediaFormat`.
     */
    override fun hashCode(): Int {
        /*
         * XXX We've experienced a case of JMF's VideoFormat#hashCode() returning different values
         * for instances which are reported equal by VideoFormat#equals(Object) which is
         * inconsistent with the protocol covering the two methods in question and causes problems,
         * for example, with Map. While jmfEncoding is more generic than format, it still
         * provides a relatively good distribution given that we do not have a lot of instances
         * with one and the same jmfEncoding.
         */
        return jMFEncoding.hashCode() or formatParameters.hashCode()
    }

    /**
     * Determines whether this `MediaFormat` matches properties of a specific
     * `MediaFormat`, such as `mediaType`, `encoding`, `clockRate` and
     * `channels` for `MediaFormat`s with `mediaType` equal to [MediaType.AUDIO].
     *
     * @param format the [MediaFormat] whose properties we'd like to examine and compare with ours.
     */
    override fun matches(format: MediaFormat?): Boolean {
        if (format == null) return false
        val mediaType = format.mediaType
        val encoding = format.encoding
        val clockRate = format.clockRate
        val channels = if (MediaType.AUDIO == mediaType) (format as AudioMediaFormat).channels else MediaFormatFactory.CHANNELS_NOT_SPECIFIED
        val fmtps = format.formatParameters
        return matches(mediaType, encoding, clockRate, channels, fmtps)
    }

    /**
     * Determines whether this `MediaFormat` has specific values for its properties
     * `mediaType`, `encoding`, `clockRate` and `channels` for
     * `MediaFormat`s with `mediaType` equal to [MediaType.AUDIO].
     *
     * @param mediaType the type we expect [MediaFormat] to have
     * @param encoding the encoding we are looking for.
     * @param clockRate the clock rate that we'd like the format to have.
     * @param channels the number of channels that expect to find in this format
     * @param formatParameters the format parameters expected to match these of the specified `format`
     * @return `true` if the specified `format` has specific values for its
     * properties `mediaType`, `encoding`, `clockRate` and `channels`; otherwise, `false`
     */
    override fun matches(
            mediaType: MediaType, encoding: String, clockRate: Double, channels: Int,
            formatParameters: Map<String, String>?,
    ): Boolean {
        // mediaType encoding
        if (this.mediaType != mediaType || this.encoding != encoding) return false

        // clockRate
        if (clockRate != MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED) {
            val formatClockRate = this.clockRate
            if (formatClockRate != MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED
                    && formatClockRate != clockRate)
                return false
        }

        // channels
        var channels = channels
        if (MediaType.AUDIO == mediaType) {
            if (channels == MediaFormatFactory.CHANNELS_NOT_SPECIFIED) channels = 1

            var formatChannels = (this as AudioMediaFormat).channels
            if (formatChannels == MediaFormatFactory.CHANNELS_NOT_SPECIFIED) formatChannels = 1
            if (formatChannels != channels)
                return false
        }
        // formatParameters
        return formatParametersMatch(formatParameters)
    }

    /**
     * Returns a `String` representation of this `MediaFormat` containing, among
     * other things, its encoding and clockrate values.
     *
     * @return a `String` representation of this `MediaFormat`.
     */
    override fun toString(): String {
        val str = StringBuilder()
        str.append("rtpmap:")
        str.append(rtpPayloadType.toInt())
        str.append(' ')
        str.append(encoding)
        str.append('/')
        str.append(clockRateString)

        /*
         * If the number of channels is 1, it does not have to be mentioned because it is the default.
         */
        if (MediaType.AUDIO == mediaType) {
            val channels = (format as AudioFormat).channels
            if (channels != 1) {
                str.append('/')
                str.append(channels)
            }
        }
        val formatParameters = formatParameters
        if (formatParameters.isNotEmpty()) {
            str.append(" fmtp:")
            var prependSeparator = false
            for ((key, value) in formatParameters) {
                if (prependSeparator) str.append(';') else prependSeparator = true
                str.append(key)
                str.append('=')
                str.append(value)
            }
        }
        return str.toString()
    }

    companion object {
        /**
         * The name of the `clockRate` property of `MediaFormatImpl`.
         */
        const val CLOCK_RATE_PNAME = "clockRate"

        /**
         * The value of the `formatParameters` property of `MediaFormatImpl` when no
         * codec-specific parameters have been received via SIP/SDP or XMPP/Jingle. Explicitly defined
         * in order to reduce unnecessary allocations.
         */
        val EMPTY_FORMAT_PARAMETERS = emptyMap<String, String>()

        /**
         * The name of the `encoding` property of `MediaFormatImpl`.
         */
        const val ENCODING_PNAME = "encoding"

        /**
         * The name of the `formatParameters` property of `MediaFormatImpl`.
         */
        const val FORMAT_PARAMETERS_PNAME = "fmtps"

        /**
         * The attribute name of the `formatParameter` property of `MediaFormatImpl`.
         * Negotiation of Generic Image Attributes in the Session Description Protocol (SDP); https://tools.ietf.org/html/rfc6236
         * 3.2.  Considerations
         * 3.2.1.  No imageattr in First Offer
         * When the initial offer does not contain the 'imageattr' attribute, the rules
         * in Section 3.1.1.2 require the attribute to be absent in the answer.
         */
        const val FORMAT_PARAMETER_ATTR_IMAGEATTR = "imageattr"

        /**
         * Creates a new `MediaFormat` instance for a specific JMF `Format`.
         *
         * @param format the JMF `Format` the new instance is to provide an implementation of `MediaFormat`
         * @return a new `MediaFormat` instance for the specified JMF `Format`
         */
        fun createInstance(format: Format?): MediaFormat? {
            var mediaFormat = getMediaFormat(format!!)
            if (mediaFormat == null) {
                when (format) {
                    is AudioFormat -> mediaFormat = AudioMediaFormatImpl(format)
                    is VideoFormat -> mediaFormat = VideoMediaFormatImpl(format)
                }
            }
            return mediaFormat
        }

        /**
         * Creates a new `MediaFormat` instance for a specific JMF `Format` and
         * assigns its specific clock rate and set of format-specific parameters.
         *
         * @param format the JMF `Format` the new instance is to provide an implementation of `MediaFormat`
         * @param clockRate the clock rate of the new instance
         * @param formatParameters the set of format-specific parameters of the new instance
         * @param advancedAttributes advanced attributes of the new instance
         * @return a new `MediaFormat` instance for the specified JMF `Format` and with
         * the specified clock rate and set of format-specific parameters
         */
        fun createInstance(
                format: Format?, clockRate: Double,
                formatParameters: Map<String, String>?, advancedAttributes: Map<String, String>?,
        ): MediaFormatImpl<out Format?>? {
            return when (format) {
                is AudioFormat -> {
                    val clockRateAudioFormat = AudioFormat(
                        format.encoding,
                        clockRate,
                        format.sampleSizeInBits,
                        format.channels)

                    AudioMediaFormatImpl(
                        clockRateAudioFormat.intersects(format) as AudioFormat,
                        formatParameters,
                        advancedAttributes)
                }

                is VideoFormat -> {
                    VideoMediaFormatImpl(
                        format as VideoFormat?,
                        clockRate,
                        -1f,
                        formatParameters,
                        advancedAttributes)
                }
                else -> null
            }
        }

        /**
         * Determines whether a specific set of format parameters is equal to another set of format
         * parameters in the sense that they define an equal number of parameters and assign them equal
         * values. Since the values are `String` s, presumes that a value of `null` is
         * equal to the empty `String`.
         *
         *
         * The two `Map` instances of format parameters to be checked for equality are presumed
         * to be modifiable in the sense that if the lack of a format parameter in a given `Map`
         * is equivalent to it having a specific value, an association of the format parameter to the
         * value in question may be added to or removed from the respective `Map` instance for
         * the purposes of determining equality.
         *
         *
         * @param encoding the encoding (name) related to the two sets of format parameters to be tested for equality
         * @param fmtps1 the first set of format parameters to be tested for equality
         * @param fmtps2 the second set of format parameters to be tested for equality
         * @return `true` if the specified sets of format parameters are equal; `false`, otherwise
         */
        fun formatParametersAreEqual(
                encoding: String, fmtps1: Map<String, String>?,
                fmtps2: Map<String, String>?,
        ): Boolean {
            if (fmtps1 == null) return fmtps2 == null || fmtps2.isEmpty()
            if (fmtps2 == null) return fmtps1.isEmpty()
            return if (fmtps1.size == fmtps2.size) {
                for ((key1, value1) in fmtps1) {
                    if (!fmtps2.containsKey(key1)) return false
                    val value2 = fmtps2[key1]

                    /*
                     * Since the values are strings, allow null to be equal to the empty string.
                     */
                    if (value1.isEmpty()) {
                        if (value2 != null && value2.isNotEmpty()) return false
                    }
                    else if (value1 != value2) return false
                }
                true
            }
            else false
        }
    }
}