/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.format

import org.atalk.util.MediaType

/**
 * The `MediaFormat` interface represents a generic (i.e. audio/video or other) format used
 * to represent media represent a media stream.
 *
 *
 * The interface contains utility methods for extracting common media format properties such as the
 * name of the underlying encoding, or clock rate or in order comparing to compare formats.
 * Extending interfaces representing audio or video formats are likely to add other methods.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface MediaFormat {
    /**
     * Returns the type of this `MediaFormat` (e.g. audio or video).
     *
     * @return the `MediaType` that this format represents (e.g. audio or video).
     */
    val mediaType: MediaType

    /**
     * Returns the name of the encoding (i.e. codec) used by this `MediaFormat`.
     *
     * @return The name of the encoding that this `MediaFormat` is using.
     */
    val encoding: String

    /**
     * Returns the clock rate associated with this `MediaFormat`.
     *
     * @return The clock rate associated with this format.
     */
    val clockRate: Double

    /**
     * Returns a `String` representation of the clock rate associated with this
     * `MediaFormat` making sure that the value appears as an integer (i.e. contains no
     * decimal point) unless it is actually a non integer.
     *
     * @return a `String` representation of the clock rate associated with this `MediaFormat`.
     */
    val clockRateString: String?

    /**
     * Returns a `String` representation of the real used clock rate associated with this
     * `MediaFormat` making sure that the value appears as an integer (i.e. contains no
     * decimal point) unless it is actually a non integer. This function corrects the problem of the
     * G.722 codec which advertises its clock rate to be 8 kHz while 16 kHz is really used to encode
     * the stream (that's an error noted in the respective RFC and kept for the sake of compatibility.).
     *
     * @return a `String` representation of the real used clock rate associated with this `MediaFormat`.
     */
    val realUsedClockRateString: String?

    /**
     * Determines whether this `MediaFormat` is equal to `mediaFormat` i.e. they have
     * the same encoding, clock rate, format parameters, advanced attributes, etc.
     *
     * @param other the `MediaFormat` to compare to this instance
     * @return `true` if `mediaFormat` is equal to this format and `false` otherwise.
     */
    override fun equals(other: Any?): Boolean

    /**
     * Determines whether the format parameters of this `MediaFormat` match a specific set of format parameters.
     *
     * @param fmtps the set of format parameters to match to the format parameters of this `MediaFormat`
     * @return `true` if this `MediaFormat` considers `fmtps` matching its
     * format parameters; otherwise, `false`
     */
    fun formatParametersMatch(fmtps: Map<String, String>?): Boolean

    /**
     * Returns a `Map` containing advanced parameters specific to this particular `MediaFormat`.
     * The parameters returned here are meant for use in SIP/SDP or XMPP session descriptions.
     *
     * @return a `Map` containing advanced parameters specific to this particular `MediaFormat`
     */
    val advancedAttributes: MutableMap<String, String>?

    /**
     * Check to see if advancedAttributes contains the specific parameter name-value pair
     *
     * @param parameterName the key of the <parameter></parameter> name-value pair
     * @return true if the <parameter></parameter> contains the specified key name
     */
    fun hasParameter(parameterName: String): Boolean

    /**
     * Remove the specific parameter name-value pair from advancedAttributes
     *
     * @param parameterName the key of the <parameter></parameter> name-value pair to be removed
     * @see org.atalk.impl.neomedia.format.MediaFormatImpl.FORMAT_PARAMETER_ATTR_IMAGEATTR
     */
    fun removeParameter(parameterName: String)

    /**
     * Returns a `Map` containing parameters specific to this particular `MediaFormat`.
     * The parameters returned here are meant for use in SIP/SDP or XMPP session descriptions
     * where they get transported through the "fmtp:" attribute or <parameter></parameter> tag respectively.
     *
     * @return a `Map` containing parameters specific to this particular `MediaFormat`
     * .
     */
    val formatParameters: Map<String, String>

    /**
     * Gets the RTP payload type (number) of this `MediaFormat` as it is known in RFC 3551
     * "RTP Profile for Audio and Video Conferences with Minimal Control".
     *
     * @return the RTP payload type of this `MediaFormat` if it is known in RFC 3551 "RTP
     * Profile for Audio and Video Conferences with Minimal Control"; otherwise,
     * [.RTP_PAYLOAD_TYPE_UNKNOWN]
     */
    val rtpPayloadType: Byte
    /**
     * Returns additional codec settings.
     *
     * @return additional settings represented by a map.
     */
    /**
     * Sets additional codec settings.; additionalCodecSettings represented by a map.
     */
    var additionalCodecSettings: Map<String, String>?

    /**
     * Returns a `String` representation of this `MediaFormat` containing important
     * format attributes such as the encoding for example.
     *
     * @return a `String` representation of this `MediaFormat`.
     */
    override fun toString(): String

    /**
     * Determines whether this `MediaFormat` matches properties of a specific
     * `MediaFormat`, such as `mediaType`, `encoding`, `clockRate` and
     * `channels` for `MediaFormat`s with `mediaType` equal to [MediaType.AUDIO].
     *
     * @param format the [MediaFormat] whose properties we'd like to examine
     */
    fun matches(format: MediaFormat?): Boolean

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
     * @return `true` if the specified `format` has specific values for its properties
     * `mediaType`, `encoding`, `clockRate` and `channels`; otherwise, `false`
     */
    fun matches(mediaType: MediaType, encoding: String, clockRate: Double, channels: Int,
            formatParameters: Map<String, String>?): Boolean

    companion object {
        /**
         * The constant returned by [.getRTPPayloadType] when the `MediaFormat` instance
         * describes a format without an RTP payload type (number) known in RFC 3551 "RTP Profile for
         * Audio and Video Conferences with Minimal Control".
         */
        const val RTP_PAYLOAD_TYPE_UNKNOWN: Byte = -1

        /**
         * The minimum integer that is allowed for use in dynamic payload type assignment.
         */
        const val MIN_DYNAMIC_PAYLOAD_TYPE = 96

        /**
         * The maximum integer that is allowed for use in dynamic payload type assignment.
         */
        const val MAX_DYNAMIC_PAYLOAD_TYPE = 127
    }
}