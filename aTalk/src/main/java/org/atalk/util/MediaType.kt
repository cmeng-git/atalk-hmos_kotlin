/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.util

/**
 * The `MediaType` enumeration contains a list of media types.
 *
 * @see [
 * Session Description Protocol
 * @author Emil Ivov
 * @author Eng Chong Meng
](http://www.iana.org/assignments/sdp-parameters/sdp-parameters.xhtml.sdp-parameters-1) */
enum class MediaType
/**
 * Creates a `MediaType` instance with the specified name.
 *
 * @param mediaTypeName the name of the `MediaType` we'd like to create.
 */
(
    /**
     * The name of this `MediaType`.
     */
    private val mediaTypeName: String) {

    /**
     * Represents an AUDIO media type.
     */
    AUDIO("audio"),

    /**
     * Represents a VIDEO media type.
     */
    VIDEO("video"),

    /**
     * Represents a TEXT media type. See RFC4103.
     */
    TEXT("text"),

    /**
     * Represents an APPLICATION media type.
     */
    APPLICATION("application"),

    /**
     * Represents a (chat-) MESSAGE media type.
     */
    MESSAGE("message"),

    /**
     * Represents an IMAGE media type. See RFC6466.
     */
    IMAGE("image"),

    /**
     * Represents a DATA media type.
     *
     */
    @Deprecated("In RFC4566. Still defined to avoid parsing errors.")
    CONTROL("control"),

    /**
     * Represents a DATA media type.
     */
    DATA("data");

    /**
     * Returns the name of this MediaType (e.g. "audio" or "video"). The name returned by this
     * method is meant for use by session description mechanisms such as SIP/SDP or XMPP/Jingle.
     *
     * @return the name of this MediaType (e.g. "audio" or "video").
     */
    override fun toString(): String {
        return mediaTypeName
    }

    companion object {
        /**
         * Returns a `MediaType` value corresponding to the specified `mediaTypeName`
         * or in other words `AUDIO`, `MESSAGE` or `VIDEO`.
         *
         * @param mediaTypeName the name that we'd like to parse.
         * @return a `MediaType` value corresponding to the specified `mediaTypeName`.
         * @throws IllegalArgumentException in case `mediaTypeName` is not a valid or currently supported media type.
         */
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun parseString(mediaTypeName: String): MediaType {
            if (AUDIO.toString().equals(mediaTypeName, ignoreCase = true)) return AUDIO
            if (VIDEO.toString().equals(mediaTypeName, ignoreCase = true)) return VIDEO
            if (TEXT.toString().equals(mediaTypeName, ignoreCase = true)) return TEXT
            if (APPLICATION.toString().equals(mediaTypeName, ignoreCase = true)) return APPLICATION
            if (MESSAGE.toString().equals(mediaTypeName, ignoreCase = true)) return MESSAGE
            if (IMAGE.toString().equals(mediaTypeName, ignoreCase = true)) return IMAGE
            if (CONTROL.toString().equals(mediaTypeName, ignoreCase = true)) return CONTROL
            if (DATA.toString().equals(mediaTypeName, ignoreCase = true)) return DATA
            throw IllegalArgumentException("$mediaTypeName is not a currently supported MediaType")
        }
    }
}