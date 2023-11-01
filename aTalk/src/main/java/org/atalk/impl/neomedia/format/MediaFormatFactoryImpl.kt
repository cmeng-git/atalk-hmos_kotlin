/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.format

import org.atalk.impl.neomedia.MediaUtils.getMediaFormats
import org.atalk.impl.neomedia.NeomediaServiceUtils
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.service.neomedia.format.MediaFormatFactory
import org.atalk.util.MediaType
import timber.log.Timber
import javax.media.Format
import javax.media.format.AudioFormat
import javax.media.format.VideoFormat

/**
 * Implements `MediaFormatFactory` for the JMF `Format` types.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class MediaFormatFactoryImpl : MediaFormatFactory {
    /**
     * Creates an unknown `MediaFormat`.
     *
     * @param type `MediaType`
     * @return unknown `MediaFormat`
     */
    override fun createUnknownMediaFormat(type: MediaType): MediaFormat? {
        var unknown: Format? = null

        /*
         * FIXME Why is a VideoFormat instance created for MediaType.AUDIO and an AudioFormat
         * instance for MediaType.VIDEO?
         */
        if (type == MediaType.AUDIO) {
            unknown = VideoFormat("unknown")
        }
        else {
            if (type == MediaType.VIDEO) unknown = AudioFormat("unknown")
        }
        return MediaFormatImpl.createInstance(unknown)
    }

    /**
     * Creates a `MediaFormat` for the specified `encoding` with default clock rate
     * and set of format parameters. If `encoding` is known to this
     * `MediaFormatFactory`, returns a `MediaFormat` which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance. Otherwise, returns
     * `null`.
     *
     * @param encoding the well-known encoding (name) to create a `MediaFormat` for
     * @return a `MediaFormat` with the specified `encoding` which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance if
     * `encoding` is known to this `MediaFormatFactory` ; otherwise, `null`
     * @see MediaFormatFactory.createMediaFormat
     */
    override fun createMediaFormat(encoding: String): MediaFormat? {
        return createMediaFormat(encoding, MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED)
    }

    /**
     * Creates a `MediaFormat` for the specified RTP payload type with default clock rate
     * and set of format parameters. If `rtpPayloadType` is known to this
     * `MediaFormatFactory`, returns a `MediaFormat` which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance. Otherwise, returns
     * `null`.
     *
     * @param rtpPayloadType the RTP payload type of the `MediaFormat` to create
     * @return a `MediaFormat` with the specified `rtpPayloadType` which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance if
     * `rtpPayloadType` is known to this `MediaFormatFactory`; otherwise, `null`
     * @see MediaFormatFactory.createMediaFormat
     */
    override fun createMediaFormat(rtpPayloadType: Byte): MediaFormat? {
        /*
         * We know which are the MediaFormat instances with the specified rtpPayloadType but we
         * cannot directly return them because they do not reflect the user's configuration with
         * respect to being enabled and disabled.
         */
        for (rtpPayloadTypeMediaFormat in getMediaFormats(rtpPayloadType)) {
            val mediaFormat = createMediaFormat(rtpPayloadTypeMediaFormat.encoding,
                rtpPayloadTypeMediaFormat.clockRate)
            if (mediaFormat != null) return mediaFormat
        }
        return null
    }

    /**
     * Creates a `MediaFormat` for the specified `encoding` with the specified
     * `clockRate` and a default set of format parameters. If `encoding` is known to
     * this `MediaFormatFactory`, returns a `MediaFormat` which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance. Otherwise, returns
     * `null`.
     *
     * @param encoding the well-known encoding (name) to create a `MediaFormat` for
     * @param clockRate the clock rate in Hz to create a `MediaFormat` for
     * @return a `MediaFormat` with the specified `encoding` and `clockRate`
     * which is either an `AudioMediaFormat` or a `VideoMediaFormat` instance
     * if `encoding` is known to this `MediaFormatFactory`; otherwise, `null`
     * @see MediaFormatFactory.createMediaFormat
     */
    override fun createMediaFormat(encoding: String, clockRate: Double): MediaFormat? {
        return createMediaFormat(encoding, clockRate, 1)
    }

    /**
     * Creates a `MediaFormat` for the specified `encoding`, `clockRate` and
     * `channels` and a default set of format parameters. If `encoding` is known to
     * this `MediaFormatFactory`, returns a `MediaFormat` which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance. Otherwise, returns
     * `null`.
     *
     * @param encoding the well-known encoding (name) to create a `MediaFormat` for
     * @param clockRate the clock rate in Hz to create a `MediaFormat` for
     * @param channels the number of available channels (1 for mono, 2 for stereo) if it makes sense for the
     * `MediaFormat` with the specified `encoding`; otherwise, ignored
     * @return a `MediaFormat` with the specified `encoding`, `clockRate` and
     * `channels` and a default set of format parameters which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance if
     * `encoding` is known to this `MediaFormatFactory`; otherwise,
     * `null`
     * @see MediaFormatFactory.createMediaFormat
     */
    override fun createMediaFormat(encoding: String, clockRate: Double, channels: Int): MediaFormat? {
        return createMediaFormat(encoding, clockRate, channels, null)
    }

    private fun createMediaFormat(
            encoding: String, clockRate: Double, channels: Int,
            fmtps: Map<String, String>?,
    ): MediaFormat? {
        for (format in getSupportedMediaFormats(encoding, clockRate)) {
            /*
             * The mediaType, encoding and clockRate properties are sure to match because format is
             * the result of the search for encoding and clockRate. We just want to make sure that
             * the channels and the format parameters match.
             */
            if (format.matches(format.mediaType, format.encoding, format.clockRate, channels, fmtps)) return format
        }
        return null
    }

    /**
     * Creates a `MediaFormat` for the specified `encoding`, `clockRate` and
     * set of format parameters. If `encoding` is known to this `MediaFormatFactory`,
     * returns a `MediaFormat` which is either an `AudioMediaFormat` or a
     * `VideoMediaFormat` instance. Otherwise, returns `null`.
     *
     * @param encoding the well-known encoding (name) to create a `MediaFormat` for
     * @param clockRate the clock rate in Hz to create a `MediaFormat` for
     * @param formatParams any codec specific parameters which have been received via SIP/SDP or XMPP/Jingle
     * @return a `MediaFormat` with the specified `encoding`, `clockRate` and
     * set of format parameters which is either an `AudioMediaFormat` or a
     * `VideoMediaFormat` instance if `encoding` is known to this
     * `MediaFormatFactory`; otherwise, `null`
     * @see MediaFormatFactory.createMediaFormat
     */
    override fun createMediaFormat(
            encoding: String, clockRate: Double,
            formatParams: Map<String, String>?, advancedAttrs: Map<String, String>?,
    ): MediaFormat? {
        return createMediaFormat(encoding, clockRate, 1, -1f, formatParams, advancedAttrs)
    }

    /**
     * Creates a `MediaFormat` for the specified `encoding`, `clockRate`,
     * `channels` and set of format parameters. If `encoding` is known to this
     * `MediaFormatFactory`, returns a `MediaFormat` which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance. Otherwise, returns
     * `null`.
     *
     * @param encoding the well-known encoding (name) to create a `MediaFormat` for
     * @param clockRate the clock rate in Hz to create a `MediaFormat` for
     * @param frameRate the frame rate in number of frames per second to create a `MediaFormat` for
     * @param channels the number of available channels (1 for mono, 2 for stereo) if it makes sense for the
     * `MediaFormat` with the specified `encoding`; otherwise, ignored
     * @param formatParams any codec specific parameters which have been received via SIP/SDP or XMPP/Jingle
     * @param advancedAttrs any parameters which have been received via SIP/SDP or XMPP/Jingle
     * @return a `MediaFormat` with the specified `encoding`, `clockRate`,
     * `channels` and set of format parameters which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance if
     * `encoding` is known to this `MediaFormatFactory`; otherwise,
     * `null`
     * @see MediaFormatFactory.createMediaFormat
     */
    override fun createMediaFormat(
            encoding: String, clockRate: Double, channels: Int,
            frameRate: Float, formatParams: Map<String, String>?, advancedAttrs: Map<String, String>?,
    ): MediaFormat? {

        var mediaFormat = createMediaFormat(encoding, clockRate, channels, formatParams) ?: return null

        /*
         * MediaFormatImpl is immutable so if the caller wants to change the format parameters
         * and/or the advanced attributes, we'll have to create a new MediaFormatImpl.
         */
        var formatParameters: Map<String, String>? = null
        var advancedParameters: Map<String, String>? = null
        if (formatParams != null && formatParams.isNotEmpty()) formatParameters = formatParams
        if (advancedAttrs != null && advancedAttrs.isNotEmpty()) advancedParameters = advancedAttrs
        if (formatParameters != null || advancedParameters != null) {
            when (mediaFormat.mediaType) {
                MediaType.AUDIO -> mediaFormat = AudioMediaFormatImpl(
                    (mediaFormat as AudioMediaFormatImpl).format!!, formatParameters, advancedParameters)

                MediaType.VIDEO -> {
                    val videoMediaFormatImpl = mediaFormat as VideoMediaFormatImpl

                    /*
                     * If the format of VideoMediaFormatImpl is a ParameterizedVideoFormat, it's
                     * possible for the format parameters of that ParameterizedVideoFormat and of
                     * the new VideoMediaFormatImpl (to be created) to be out of sync. While it's
                     * not technically perfect, it should be practically safe for the format
                     * parameters which distinguish VideoFormats with the same encoding and clock
                     * rate because mediaFormat has already been created in sync with formatParams
                     * (with respect to the format parameters which distinguish VideoFormats with
                     * the same encoding and clock rate).
                     */
                    mediaFormat = VideoMediaFormatImpl(videoMediaFormatImpl.format,
                        videoMediaFormatImpl.clockRate, frameRate, formatParameters, advancedParameters)
                }
                else -> return null //mediaFormat = null
            }
        }
        return mediaFormat
    }

    /**
     * Creates a `MediaFormat` either for the specified `rtpPayloadType` or for the
     * specified `encoding`, `clockRate`, `channels` and set of format
     * parameters. If `encoding` is known to this `MediaFormatFactory` , ignores
     * `rtpPayloadType` and returns a `MediaFormat` which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance. If
     * `rtpPayloadType` is not [MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN] and
     * `encoding` is `null`, uses the encoding associated with
     * `rtpPayloadType`.
     *
     * @param rtpPayloadType the RTP payload type to create a `MediaFormat` for;
     * [MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN] if `encoding` is not `null`
     * . If `rtpPayloadType` is not `MediaFormat#RTP_PAYLOAD_TYPE_UNKNOWN` and
     * `encoding` is not `null`, `rtpPayloadType` is ignored
     * @param encoding the well-known encoding (name) to create a `MediaFormat` for; `null`
     * @param clockRate the clock rate in Hz to create a `MediaFormat` for
     * @param frameRate the frame rate in number of frames per second to create a `MediaFormat` for
     * @param channels the number of available channels (1 for mono, 2 for stereo) if it makes sense for the
     * `MediaFormat` with the specified `encoding`; otherwise, ignored
     * @param formatParams any codec specific parameters which have been received via SIP/SDP or XMPP/Jingle
     * @param advancedAttrs any parameters which have been received via SIP/SDP or XMPP/Jingle
     * @return a `MediaFormat` with the specified `encoding`, `clockRate`,
     * `channels` and set of format parameters which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance if
     * `encoding` is known to this `MediaFormatFactory`; otherwise, `null`
     */
    override fun createMediaFormat(
            rtpPayloadType: Byte, encoding: String, clockRate: Double,
            channels: Int, frameRate: Float, formatParams: Map<String, String>?,
            advancedAttrs: Map<String, String>?,
    ): MediaFormat? {
        /*
         * If rtpPayloadType is specified, use it only to figure out encoding and/or clockRate in
         * case either one of them is unknown.
         */
        var encoding = encoding
        var clockRate = clockRate
        if (MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN != rtpPayloadType
                && (MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED == clockRate)) {
            val rtpPayloadTypeMediaFormats = getMediaFormats(rtpPayloadType)
            if (rtpPayloadTypeMediaFormats.isNotEmpty()) {
                encoding = rtpPayloadTypeMediaFormats[0].encoding

                // Assign or check the clock rate.
                if (MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED == clockRate)
                    clockRate = rtpPayloadTypeMediaFormats[0].clockRate
                else {
                    var clockRateIsValid = false
                    for (rtpPayloadTypeMediaFormat in rtpPayloadTypeMediaFormats) {
                        if (rtpPayloadTypeMediaFormat.encoding == encoding
                                && rtpPayloadTypeMediaFormat.clockRate == clockRate) {
                            clockRateIsValid = true
                            break
                        }
                    }
                    if (!clockRateIsValid) return null
                }
            }
        }
        return createMediaFormat(encoding, clockRate, channels, frameRate, formatParams,
            advancedAttrs)
    }

    /**
     * Gets the `MediaFormat`s among the specified `mediaFormats` which have the
     * specified `encoding` and, optionally, `clockRate`.
     *
     * @param mediaFormats the `MediaFormat`s from which to filter out only the ones which have the
     * specified `encoding` and, optionally, `clockRate`
     * @param encoding the well-known encoding (name) of the `MediaFormat`s to be retrieved
     * @param clockRate the clock rate of the `MediaFormat`s to be retrieved;
     * [.CLOCK_RATE_NOT_SPECIFIED] if any clock rate is acceptable
     * @return a `List` of the `MediaFormat`s among `mediaFormats` which have
     * the specified `encoding` and, optionally, `clockRate`
     */
    private fun getMatchingMediaFormats(
            mediaFormats: Array<MediaFormat>, encoding: String?,
            clockRate: Double,
    ): List<MediaFormat> {
        /*
         * XXX Use String#equalsIgnoreCase(String) because some clients transmit some of the codecs
         * starting with capital letters.
         */

        /*
         * As per RFC 3551.4.5.2, because of a mistake in RFC 1890 and for backward compatibility,
         * G.722 should always be announced as 8000 even though it is wideband. So, if someone is
         * looking for G722/16000, then: Forgive them, for they know not what they do!
         */
        var clkRate = clockRate
        if ("G722".equals(encoding, ignoreCase = true) && 16000.0 == clkRate) {
            clkRate = 8000.0
            Timber.i("Suppressing erroneous 16000 announcement for G.722")
        }
        val supportedMediaFormats = ArrayList<MediaFormat>()
        for (mediaFormat in mediaFormats) {
            if (mediaFormat.encoding.equals(encoding, ignoreCase = true)
                    && (MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED == clkRate
                            || mediaFormat.clockRate == clkRate)) {
                supportedMediaFormats.add(mediaFormat)
            }
        }
        return supportedMediaFormats
    }

    /**
     * Gets the `MediaFormat`s supported by this `MediaFormatFactory` and the
     * `MediaService` associated with it and having the specified `encoding` and,
     * optionally, `clockRate`.
     *
     * @param encoding the well-known encoding (name) of the `MediaFormat`s to be retrieved
     * @param clockRate the clock rate of the `MediaFormat`s to be retrieved;
     * [.CLOCK_RATE_NOT_SPECIFIED] if any clock rate is acceptable
     * @return a `List` of the `MediaFormat`s supported by the `MediaService`
     * associated with this `MediaFormatFactory` and having the specified encoding
     * and, optionally, clock rate
     */
    private fun getSupportedMediaFormats(encoding: String?, clockRate: Double): List<MediaFormat> {
        val encodingConfiguration = NeomediaServiceUtils.mediaServiceImpl!!.currentEncodingConfiguration
        var supportedMediaFormats = getMatchingMediaFormats(
            encodingConfiguration.getAllEncodings(MediaType.AUDIO), encoding, clockRate)
        if (supportedMediaFormats.isEmpty()) supportedMediaFormats = getMatchingMediaFormats(
            encodingConfiguration.getAllEncodings(MediaType.VIDEO), encoding, clockRate)
        return supportedMediaFormats
    }
}