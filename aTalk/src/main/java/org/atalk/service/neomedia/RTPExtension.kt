/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import java.net.URI

/**
 * RTP extensions are defined by RFC 5285 and they allow attaching additional information to some or
 * all RTP packets of an RTP stream. This class describes RTP extensions in a way that makes them
 * convenient for use in SDP generation/parsing.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class RTPExtension @JvmOverloads constructor(
        /**
         * The `URI` identifier of this extension.
         */
        val uri: URI, direction: MediaDirection = MediaDirection.SENDRECV, extensionAttributes: String? = null) {

    /**
     * Returns the direction that the corresponding `MediaDevice` supports for this
     * extension. By default RTP extension headers inherit the direction of a stream. When
     * explicitly specified `SENDONLY` direction indicates an ability to attach the extension
     * in outgoing RTP packets; a `RECVONLY` direction indicates a desire to receive the
     * extension in incoming packets; a `SENDRECV` direction indicates both. An
     * `INACTIVE` direction indicates neither, but later re-negotiation may make an extension
     * active.
     *
     * @return the direction that the corresponding `MediaDevice` supports for this
     * extension.
     */
    /**
     * The direction that this extension will be transmitted in.
     */
    var direction = MediaDirection.SENDRECV

    /**
     * Returns the `URI` that identifies the format and meaning of this extension.
     *
     * @return the `URI` (possibly a URN) that identifies the format and meaning of this
     * extension.
     */
    /**
     * Returns the extension attributes associated with this `RTPExtension` or `null`
     * if this extension does not have any.
     *
     * @return A `String` containing the extension attributes associated with this
     * `RTPExtension` or `null` if this extension does not have any.
     */
    /**
     * Extension specific attributes.
     */
    val extensionAttributes: String?

    /**
     * Creates an `RTPExtension` instance for the specified `extensionURI` using a
     * default `SENDRECV` direction and `extensionAttributes`.
     *
     * @param uri the `URI` (possibly a URN) of the RTP extension that we'd like to create.
     * @param extensionAttributes any attributes that we'd like to add to this extension.
     */
    constructor(uri: URI, extensionAttributes: String?) : this(uri, MediaDirection.SENDRECV, extensionAttributes)

    /**
     * Creates an `RTPExtension` instance for the specified `extensionURI` and
     * `direction` and sets the specified `extensionAttributes`.
     *
     * @param uri the `URI` (possibly a URN) of the RTP extension that we'd like to create.
     * @param direction a `MediaDirection` instance indication how this extension will be transmitted.
     * @param extensionAttributes any attributes that we'd like to add to this extension.
     */

    /**
     * Creates an `RTPExtension` instance for the specified `extensionURI` and `direction`.
     *
     * @param uri the `URI` (possibly a URN) of the RTP extension that we'd like to create.
     * @param direction a `MediaDirection` instance indication how this extension will be transmitted.
     */

    init {
        this.direction = direction
        this.extensionAttributes = extensionAttributes
    }

    /**
     * Returns a `String` representation of this `RTPExtension`'s `URI`.
     *
     * @return a `String` representation of this `RTPExtension`'s `URI`.
     */
    override fun toString(): String {
        return "$uri;$direction"
    }

    /**
     * Returns `true` if and only if `o` is an instance of `RTPExtension` and
     * `o`'s `URI` is equal to this extension's `URI`. The method returns
     * `false` otherwise.
     *
     * @param other the `Object` that we'd like to compare to this `RTPExtension`.
     * @return `true` when `o`'s `URI` is equal to this extension's
     * `URI` and `false` otherwise.
     */
    override fun equals(other: Any?): Boolean {
        return other is RTPExtension && other.uri == uri
    }

    /**
     * Returns the hash code of this extension instance which is actually the hash code of the
     * `URI` that this extension is encapsulating.
     *
     * @return the hash code of this extension instance which is actually the hash code of the
     * `URI` that this extension is encapsulating.
     */
    override fun hashCode(): Int {
        return uri.hashCode()
    }

    companion object {
        /**
         * The URN identifying the RTP extension that allows mixers to send to conference participants
         * the audio levels of all contributing sources. Defined in RFC6465.
         */
        const val CSRC_AUDIO_LEVEL_URN = "urn:ietf:params:rtp-hdrext:csrc-audio-level"

        /**
         * The URN identifying the RTP extension that allows clients to send to conference mixers the
         * audio level of their packet payload. Defined in RFC6464.
         */
        const val SSRC_AUDIO_LEVEL_URN = "urn:ietf:params:rtp-hdrext:ssrc-audio-level"

        /**
         * The URN identifying the abs-send-time RTP extension. Defined at
         * []//www.webrtc.org/experiments/rtp-hdrext/abs-send-time"">&quot;https://www.webrtc.org/experiments/rtp-hdrext/abs-send-time&quot;
         */
        const val ABS_SEND_TIME_URN = "http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time"

        /**
         * The URN which identifies the framemarking RTP extension defined at
         * []//tools.ietf.org/html/draft-ietf-avtext-framemarking-03"">&quot;https://tools.ietf.org/html/draft-ietf-avtext-framemarking-03&quot;
         */
        const val FRAME_MARKING_URN = "http://tools.ietf.org/html/draft-ietf-avtext-framemarking-07"

        /**
         * The URN which identifies the Original Header Block RTP extension defined
         * in []//tools.ietf.org/html/draft-ietf-perc-double-02"">&quot;https://tools.ietf.org/html/draft-ietf-perc-double-02&quot;.
         */
        const val ORIGINAL_HEADER_BLOCK_URN = "urn:ietf:params:rtp-hdrext:ohb"

        /**
         * The URN which identifies the Transport-Wide Congestion Control RTP
         * extension.
         */
        const val TRANSPORT_CC_URN = "http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01"

        /**
         * The URN which identifies the rtp-stream-id extensions
         * in []//tools.ietf.org/html/draft-ietf-mmusic-rid-10"">&quot;https://tools.ietf.org/html/draft-ietf-mmusic-rid-10&quot;.
         */
        const val RTP_STREAM_ID_URN = "urn:ietf:params:rtp-hdrext:sdes:rtp-stream-id"

        /**
         * The URN which identifies the transmission time-offset extensions
         * in []//tools.ietf.org/html/rfc5450"">&quot;https://tools.ietf.org/html/rfc5450&quot;.
         */
        const val TOF_URN = "urn:ietf:params:rtp-hdrext:toffset"

        /**
         * The URN which identifies the RTP Header Extension for Video Content Type.
         */
        const val VIDEO_CONTENT_TYPE_URN = "http://www.webrtc.org/experiments/rtp-hdrext/video-content-type"
    }
}