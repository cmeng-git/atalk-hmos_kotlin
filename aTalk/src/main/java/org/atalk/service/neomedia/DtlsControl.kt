/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import java.util.*

/**
 * Implements [SrtpControl] for DTSL-SRTP.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface DtlsControl : SrtpControl {
    /**
     * Gets the fingerprint of the local certificate that this instance uses to authenticate its
     * ends of DTLS sessions.
     *
     * @return the fingerprint of the local certificate that this instance uses to authenticate its
     * ends of DTLS sessions
     */
    val localFingerprint: String?

    /**
     * Gets the hash function with which the fingerprint of the local certificate is computed i.e.
     * the digest algorithm of the signature algorithm of the local certificate.
     *
     * @return the hash function with which the fingerprint of the local certificate is computed
     */
    val localFingerprintHashFunction: String?

    /**
     * Sets the certificate fingerprints presented by the remote endpoint via the signaling path.
     *
     * @param remoteFingerprints a `Map` of hash functions to certificate fingerprints
     * that have been presented by the remote endpoint via the signaling path
     */
    fun setRemoteFingerprints(remoteFingerprints: Map<String?, String?>?)

    /**
     * Sets the value of the `setup` SDP attribute defined by RFC 4145 &quot;TCP-Based Media
     * Transport in the Session Description Protocol (SDP)&quot; which determines whether this
     * instance is to act as a DTLS client or a DTLS server.
     *
     * @param setup the value of the `setup` SDP attribute to set on this instance in order to
     * determine whether this instance is to act as a DTLS client or a DTLS server
     */
    // fun setSetup(setup: Setup?)
    var setup: Setup?

    /**
     * Enables/disables rtcp-mux.
     *
     * @param rtcpmux whether to enable or disable.
     */
    fun setRtcpmux(rtcpmux: Boolean)

    /**
     * Enumerates the possible values of the `setup` SDP attribute defined by RFC 4145
     * &quot;TCP-Based Media Transport in the Session Description Protocol (SDP)&quot;.
     *
     * @author Lyubomir Marinov
     */
    enum class Setup {
        ACTIVE, ACTPASS, HOLDCONN, PASSIVE;

        /**
         * {@inheritDoc}
         */
        override fun toString(): String {
            return name.lowercase(Locale.getDefault())
        }

        companion object {
            /**
             * Parses a `String` into a `Setup` enum value. The specified `String`
             * to parse must be in a format as produced by [.toString]; otherwise, the method
             * will throw an exception.
             *
             * @param s the `String` to parse into a `Setup` enum value
             * @return a `Setup` enum value on which `toString()` produces the specified `s`
             * @throws IllegalArgumentException if none of the `Setup` enum values produce
             * the specified `s` when `toString()` is invoked on them
             * @throws NullPointerException if `s` is `null`
             */
            fun parseSetup(s: String?): Setup {
                if (s == null) throw NullPointerException("s")
                for (v in values()) {
                    if (v.toString().equals(s, ignoreCase = true)) return v
                }
                throw IllegalArgumentException(s)
            }
        }
    }

    companion object {
        /**
         * The transport protocol (i.e. `<proto>`) to be specified in a SDP media description
         * (i.e. `m=` line) in order to denote a RTP/SAVP stream transported over DTLS with UDP.
         */
        const val UDP_TLS_RTP_SAVP = "UDP/TLS/RTP/SAVP"

        /**
         * The transport protocol (i.e. `<proto>`) to be specified in a SDP media description
         * (i.e. `m=` line) in order to denote a RTP/SAVPF stream transported over DTLS with UDP.
         */
        const val UDP_TLS_RTP_SAVPF = "UDP/TLS/RTP/SAVPF"
    }
}