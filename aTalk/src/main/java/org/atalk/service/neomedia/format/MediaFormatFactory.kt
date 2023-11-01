/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.format

import org.atalk.util.MediaType

/**
 * Allows the creation of audio and video `MediaFormat` instances.
 *
 * @author Emil Ivov
 * @author Lubomir Marinov
 */
interface MediaFormatFactory {
    /**
     * Creates an unknown `MediaFormat`.
     *
     * @param type `MediaType`
     * @return unknown `MediaFormat`
     */
    fun createUnknownMediaFormat(type: MediaType): MediaFormat?

    /**
     * Creates a `MediaFormat` for the specified `encoding` with default clock rate
     * and set of format parameters. If `encoding` is known to this
     * `MediaFormatFactory`, returns a `MediaFormat` which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance. Otherwise, returns `null`.
     *
     * @param encoding the well-known encoding (name) to create a `MediaFormat` for
     * @return a `MediaFormat` with the specified `encoding` which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance if
     * `encoding` is known to this `MediaFormatFactory` ; otherwise,
     * `null`
     */
    fun createMediaFormat(encoding: String): MediaFormat?

    /**
     * Creates a `MediaFormat` for the specified RTP payload type with default clock rate and
     * set of format parameters. If `rtpPayloadType` is known to this
     * `MediaFormatFactory`, returns a `MediaFormat` which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance. Otherwise, returns `null`.
     *
     * @param rtpPayloadType the RTP payload type of the `MediaFormat` to create
     * @return a `MediaFormat` with the specified `rtpPayloadType` which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance if
     * `rtpPayloadType` is known to this `MediaFormatFactory`; otherwise, `null`
     */
    fun createMediaFormat(rtpPayloadType: Byte): MediaFormat?

    /**
     * Creates a `MediaFormat` for the specified `encoding` with the specified
     * `clockRate` and a default set of format parameters. If `encoding` is known to
     * this `MediaFormatFactory`, returns a `MediaFormat` which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance. Otherwise, returns `null`.
     *
     * @param encoding the well-known encoding (name) to create a `MediaFormat` for
     * @param clockRate the clock rate in Hz to create a `MediaFormat` for
     * @return a `MediaFormat` with the specified `encoding` and `clockRate`
     * which is either an `AudioMediaFormat` or a `VideoMediaFormat` instance
     * if `encoding` is known to this `MediaFormatFactory`; otherwise, `null`
     */
    fun createMediaFormat(encoding: String, clockRate: Double): MediaFormat?

    /**
     * Creates a `MediaFormat` for the specified `encoding`, `clockRate` and
     * `channels` and a default set of format parameters. If `encoding` is known to
     * this `MediaFormatFactory`, returns a `MediaFormat` which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance. Otherwise, returns `null`.
     *
     * @param encoding the well-known encoding (name) to create a `MediaFormat` for
     * @param clockRate the clock rate in Hz to create a `MediaFormat` for
     * @param channels the number of available channels (1 for mono, 2 for stereo) if it makes sense for the
     * `MediaFormat` with the specified `encoding`; otherwise, ignored
     * @return a `MediaFormat` with the specified `encoding`, `clockRate` and
     * `channels` and a default set of format parameters which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance if
     * `encoding` is known to this `MediaFormatFactory`; otherwise, `null`
     */
    fun createMediaFormat(encoding: String, clockRate: Double, channels: Int): MediaFormat?

    /**
     * Creates a `MediaFormat` for the specified `encoding`, `clockRate` and
     * set of format parameters. If `encoding` is known to this `MediaFormatFactory`,
     * returns a `MediaFormat` which is either an `AudioMediaFormat` or a
     * `VideoMediaFormat` instance. Otherwise, returns `null`.
     *
     * @param encoding the well-known encoding (name) to create a `MediaFormat` for
     * @param clockRate the clock rate in Hz to create a `MediaFormat` for
     * @param formatParams any codec specific parameters which have been received via SIP/SDP or XMPP/Jingle
     * @param advancedAttrs advanced attributes received via SIP/SDP or XMPP/Jingle
     * @return a `MediaFormat` with the specified `encoding`, `clockRate` and
     * set of format parameters which is either an `AudioMediaFormat` or a
     * `VideoMediaFormat` instance if `encoding` is known to this
     * `MediaFormatFactory`; otherwise, `null`
     */
    fun createMediaFormat(encoding: String, clockRate: Double,
            formatParams: Map<String, String>?, advancedAttrs: Map<String, String>?): MediaFormat?

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
     * @param advancedAttrs advanced attributes received via SIP/SDP or XMPP/Jingle
     * @return a `MediaFormat` with the specified `encoding`, `clockRate`,
     * `channels` and set of format parameters which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance if
     * `encoding` is known to this `MediaFormatFactory`; otherwise, `null`
     */
    fun createMediaFormat(encoding: String, clockRate: Double, channels: Int,
            frameRate: Float, formatParams: Map<String, String>?, advancedAttrs: Map<String, String>?): MediaFormat?

    /**
     * Creates a `MediaFormat` either for the specified `rtpPayloadType` or for the
     * specified `encoding`, `clockRate`, `channels` and set of format
     * parameters. If `encoding` is known to this `MediaFormatFactory`, ignores
     * `rtpPayloadType` and returns a `MediaFormat` which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance. If `rtpPayloadType`
     * is not [MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN] and `encoding` is `null`,
     * uses the encoding associated with `rtpPayloadType`.
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
     * @param advancedAttrs advanced attributes received via SIP/SDP or XMPP/Jingle
     * @return a `MediaFormat` with the specified `encoding`, `clockRate`,
     * `channels` and set of format parameters which is either an
     * `AudioMediaFormat` or a `VideoMediaFormat` instance if
     * `encoding` is known to this `MediaFormatFactory`; otherwise, `null`
     */
    fun createMediaFormat(rtpPayloadType: Byte, encoding: String, clockRate: Double,
            channels: Int, frameRate: Float, formatParams: Map<String, String>?,
            advancedAttrs: Map<String, String>?): MediaFormat?

    companion object {
        /**
         * The constant to be used as an argument representing number of channels to denote that a
         * specific number of channels is not specified.
         */
        const val CHANNELS_NOT_SPECIFIED = -1

        /**
         * The constant to be used as an argument representing a clock rate to denote that a specific
         * clock rate is not specified.
         */
        const val CLOCK_RATE_NOT_SPECIFIED = -1.0
    }
}