/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

/**
 * The `SrtpControlType` enumeration contains all currently known `SrtpControl`
 * implementations.
 *
 * @author Ingo Bauersachs
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
enum class SrtpControlType
/**
 * Initializes a new `SrtpControlType` instance with a specific human-readable
 * non-localized (S)RTP transport protocol name.
 *
 * @param protoName the human-readable non-localized name of the (S)RTP transport protocol represented by
 * the new instance and its respective `SrtpControl` class
 */
(
        /**
         * The human-readable non-localized name of the (S)RTP transport protocol represented by this
         * `SrtpControlType` and its respective `SrtpControl` class.
         */
        private val protoName: String) {
    /**
     * Datagram Transport Layer Security (DTLS) Extension to Establish Keys for the Secure Real-time
     * Transport Protocol (SRTP). The key points of DTLS-SRTP are that:
     *
     * o application data is protected using SRTP,
     *
     * o the DTLS handshake is used to establish keying material, algorithms, and parameters for SRTP,
     *
     * o a DTLS extension is used to negotiate SRTP algorithms, and
     *
     * o other DTLS record-layer content types are protected using the ordinary DTLS record format.
     */
    DTLS_SRTP("DTLS_SRTP"),

    /**
     * Multimedia Internet KEYing (RFC 3830)
     */
    MIKEY("MIKEY"),

    /**
     * Session Description Protocol (SDP) Security Descriptions for Media Streams (RFC 4568)
     */
    SDES("SDES"),

    /**
     * ZRTP: Media Path Key Agreement for Unicast Secure RTP (RFC 6189)
     */
    ZRTP("ZRTP"),

    /**
     * A no-op implementation.
     */
    NULL("NULL");

    override fun toString(): String {
        return protoName
    }

    companion object {
        /**
         * @see SrtpControlType.valueOf
         */
        fun fromString(protoName: String): SrtpControlType {
            return if (protoName == DTLS_SRTP.toString()) {
                DTLS_SRTP
            } else {
                valueOf(protoName)
            }
        }
    }
}