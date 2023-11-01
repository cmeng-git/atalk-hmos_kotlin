/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.format

import org.atalk.service.neomedia.format.MediaFormatFactory
import org.atalk.service.neomedia.format.VideoMediaFormat
import org.atalk.util.MediaType
import java.awt.Dimension
import javax.media.format.VideoFormat

/**
 * Implements `VideoMediaFormat` for the JMF `VideoFormat`.
 *
 * @author Lyubomir Marinov
 */
class VideoMediaFormatImpl
/**
 * Initializes a new `VideoMediaFormatImpl` instance which is to provide an
 * implementation of `VideoMediaFormat` for a specific JMF `VideoFormat`.
 *
 * @param format the JMF `VideoFormat` the new instance is to wrap and provide an implementation
 * of `VideoMediaFormat` for
 */
@JvmOverloads internal constructor(format: VideoFormat?,
        /**
         * The clock rate of this `VideoMediaFormat`.
         */
        override val clockRate: Double = DEFAULT_CLOCK_RATE, frameRate: Float = -1f,
        formatParameters: Map<String, String>? = null, advancedParameters: Map<String, String>? = null) : MediaFormatImpl<VideoFormat?>(ParameterizedVideoFormat(
        format!!.encoding,
        format.size,
        format.maxDataLength,
        format.dataType,
        frameRate,
        formatParameters),
        formatParameters, advancedParameters), VideoMediaFormat {
    /**
     * Gets the clock rate associated with this `MediaFormat`.
     *
     * @return the clock rate associated with this `MediaFormat`
     * @see MediaFormat.getClockRate
     */
    /**
     * Initializes a new `VideoMediaFormatImpl` instance with a specific encoding and a
     * specific clock rate.
     *
     * @param encoding the encoding of the new `VideoMediaFormatImpl` instance
     * @param clockRate the clock rate of the new `VideoMediaFormatImpl` instance
     */
    /**
     * Initializes a new `VideoMediaFormatImpl` instance with a specific encoding.
     *
     * @param encoding the encoding of the new `VideoMediaFormatImpl` instance
     */
    @JvmOverloads
    internal constructor(encoding: String?, clockRate: Double = DEFAULT_CLOCK_RATE) : this(VideoFormat(encoding), clockRate)

    /**
     * Initializes a new `VideoMediaFormatImpl` instance which is to provide an
     * implementation of `VideoMediaFormat` for a specific JMF `VideoFormat` and to
     * have specific clock rate and set of format-specific parameters.
     *
     * @param format the JMF `VideoFormat` the new instance is to wrap and provide an implementation
     * of `VideoMediaFormat` for
     * @param clockRate the clock rate of the new `VideoMediaFormatImpl` instance
     * @param frameRate the frame rate of the new `VideoMediaFormatImpl` instance
     * @param formatParameters the set of format-specific parameters of the new instance
     * @param advancedParameters set of advanced parameters of the new instance
     */
    /**
     * Initializes a new `VideoMediaFormatImpl` instance which is to provide an
     * implementation of `VideoMediaFormat` for a specific JMF `VideoFormat` and to
     * have a specific clock rate.
     *
     * @param format the JMF `VideoFormat` the new instance is to wrap and provide an implementation
     * of `VideoMediaFormat` for
     * @param clockRate the clock rate of the new `VideoMediaFormatImpl` instance
     */
    /**
     * Implements `MediaFormat#equals(Object)` and actually compares the encapsulated JMF
     * `Format` instances.
     *
     * @param mediaFormat the object that we'd like to compare `this` one to
     * @return `true` if the JMF `Format` instances encapsulated by this instance and
     * their other characteristics are equal; `false`, otherwise.
     * @see MediaFormatImpl.equals
     */
    override fun equals(mediaFormat: Any?): Boolean {
        if (this === mediaFormat) return true
        if (!super.equals(mediaFormat)) return false
        val videoMediaFormatImpl = mediaFormat as VideoMediaFormatImpl
        var clockRate = clockRate
        var videoMediaFormatImplClockRate = videoMediaFormatImpl.clockRate
        if (MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED == clockRate) clockRate = DEFAULT_CLOCK_RATE
        if (MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED == videoMediaFormatImplClockRate) videoMediaFormatImplClockRate = DEFAULT_CLOCK_RATE
        return clockRate == videoMediaFormatImplClockRate
    }

    /**
     * {@inheritDoc}
     *
     *
     * Takes into account RFC 3984 "RTP Payload Format for H.264 Video" which says that &quot;
     * when the value of packetization-mode [format parameter] is equal to 0 or packetization-mode is
     * not present, the single NAL mode, as defined in section 6.2 of RFC 3984, MUST be used.&quot;
     *
     *
     * @see MediaFormatImpl.formatParametersAreEqual
     */
    override fun formatParametersAreEqual(fmtps1: Map<String, String>?, fmtps2: Map<String, String>?): Boolean {
        return formatParametersAreEqual(encoding, fmtps1, fmtps2)
    }

    /**
     * Determines whether the format parameters of this `MediaFormat` match a specific
     * set of format parameters.
     *
     *
     * `VideoMediaFormat` reflects the fact that the `packetization-mode` format
     * parameter distinguishes H.264 payload types.
     *
     *
     * @param fmtps the set of format parameters to match to the format parameters of this `MediaFormat`
     * @return `true` if this `MediaFormat` considers `fmtps` matching its
     * format parameters; otherwise, `false`
     */
    override fun formatParametersMatch(fmtps: Map<String, String>?): Boolean {
        return (formatParametersMatch(encoding, formatParameters, fmtps)
                && super.formatParametersMatch(fmtps))
    }

    /**
     * Gets the frame rate associated with this `MediaFormat`.
     *
     * @return the frame rate associated with this `MediaFormat`
     * @see VideoMediaFormat.getFrameRate
     */
    override val frameRate: Float
        get() = format!!.frameRate

    /**
     * Gets the type of this `MediaFormat` which is [MediaType.VIDEO] for
     * `AudioMediaFormatImpl` instances.
     *
     * @return the `MediaType` that this format represents and which is
     * `MediaType.VIDEO` for `AudioMediaFormatImpl` instances
     * @see MediaFormat.getMediaType
     */
    override val mediaType: MediaType
        get() = MediaType.VIDEO

    /**
     * Gets the size of the image that this `VideoMediaFormat` describes.
     *
     * @return a [Dimension] instance indicating the image size (in pixels) of this `VideoMediaFormat`
     * @see VideoMediaFormat.getSize
     */
    override val size: Dimension
        get() = format!!.size

    /**
     * Overrides `MediaFormatImpl#hashCode()` because `Object#equals(Object)` is overridden.
     *
     * @return a hash code value for this `VideoMediaFormatImpl`
     * @see MediaFormatImpl.hashCode
     */
    override fun hashCode(): Int {
        var clockRate = clockRate

        /*
         * The implementation of #equals(Object) of this instance assumes that
         * MediaFormatFactory#CLOCK_RATE_NOT_SPECIFIED and #DEFAULT_CLOCK_RATE are equal.
         */
        if (MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED == clockRate) clockRate = DEFAULT_CLOCK_RATE
        return super.hashCode() or java.lang.Double.valueOf(clockRate).hashCode()
    }

    companion object {
        /**
         * The default value of the `clockRate` property of `VideoMediaFormatImpl`.
         */
        const val DEFAULT_CLOCK_RATE = 90000.0

        /**
         * The name of the format parameter which specifies the packetization mode of H.264 RTP payload.
         */
        const val H264_PACKETIZATION_MODE_FMTP = "packetization-mode"
        const val H264_SPROP_PARAMETER_SETS_FMTP = "sprop-parameter-sets"

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
        fun formatParametersAreEqual(encoding: String?, fmtps1: MutableMap<String, String>?,
                fmtps2: MutableMap<String, String>?): Boolean {
            /*
         * RFC 3984 "RTP Payload Format for H.264 Video" says that "[w]hen the value of
         * packetization-mode is equal to 0 or packetization-mode is not present, the single NAL
         * mode, as defined in section 6.2 of RFC 3984, MUST be used."
         */
            if ("H264".equals(encoding, ignoreCase = true) || "h264/rtp".equals(encoding, ignoreCase = true)) {
                val packetizationMode = H264_PACKETIZATION_MODE_FMTP
                var pm1: String? = null
                var pm2: String? = null
                if (fmtps1 != null) pm1 = fmtps1.remove(packetizationMode)
                if (fmtps2 != null) pm2 = fmtps2.remove(packetizationMode)
                if (pm1 == null) pm1 = "0"
                if (pm2 == null) pm2 = "0"
                if (pm1 != pm2) return false
            }
            return MediaFormatImpl.formatParametersAreEqual(encoding!!, fmtps1, fmtps2)
        }

        /**
         * Determines whether two sets of format parameters match in the context of a specific encoding.
         *
         * @param encoding the encoding (name) related to the two sets of format parameters to be matched.
         * @param fmtps1 the first set of format parameters which is to be matched against `fmtps2`
         * @param fmtps2 the second set of format parameters which is to be matched against `fmtps1`
         * @return `true` if the two sets of format parameters match in the context of the
         * specified `encoding`; otherwise, `false`
         */
        fun formatParametersMatch(encoding: String?, fmtps1: Map<String, String>?, fmtps2: Map<String, String>?): Boolean {
            /*
         * RFC 3984 "RTP Payload Format for H.264 Video" says that "When the value of
         * packetization-mode is equal to 0 or packetization-mode is not present, the single NAL
         * mode, as defined in section 6.2 of RFC 3984, MUST be used."
         */
            if ("H264".equals(encoding, ignoreCase = true) || "h264/rtp".equals(encoding, ignoreCase = true)) {
                val packetizationMode = H264_PACKETIZATION_MODE_FMTP
                var pm1 = fmtps1?.get(packetizationMode)
                var pm2 = fmtps2?.get(packetizationMode)
                if (pm1 == null) pm1 = "0"
                if (pm2 == null) pm2 = "0"
                if (pm1 != pm2) return false
            }
            return true
        }
    }
}