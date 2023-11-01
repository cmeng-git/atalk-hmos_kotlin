/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

/**
 * ZRTP based SRTP MediaStream encryption control.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 * @author MilanKral
 */
interface ZrtpControl : SrtpControl {
    /**
     * Gets the cipher information for the current media stream.
     *
     * @return the cipher information string.
     */
    val cipherString: String?

    /**
     * Gets the negotiated ZRTP protocol version.
     *
     * @return the `int` representation of the negotiated ZRTP protocol version.
     */
    val currentProtocolVersion: Int

    /**
     * Returns the zrtp hello hash String.
     *
     * @param index Hello hash of the Hello packet identified by index. Must be
     * `0 <= index < SUPPORTED_ZRTP_VERSIONS`.
     * @return String the zrtp hello hash.
     */
    fun getHelloHash(index: Int): String?

    /**
     * Gets the ZRTP Hello Hash data - separate strings.
     *
     * @param index Hello hash of the Hello packet identified by index. Must be
     * `0 <= index < SUPPORTED_ZRTP_VERSIONS`.
     * @return String array containing the version string at offset 0, the Hello hash value as
     * hex-digits at offset 1. Hello hash is available immediately after class
     * instantiation. Returns `null` if ZRTP is not available.
     */
    fun getHelloHashSep(index: Int): Array<String>?

    /**
     * Gets the number of supported ZRTP protocol versions.
     *
     * @return the number of supported ZRTP protocol versions.
     */
    val numberSupportedVersions: Int

    /**
     * Gets the peer's Hello Hash data as a `String`.
     *
     * @return a String containing the Hello hash value as hex-digits. Peer Hello hash is available
     * after we received a Hello packet from our peer. If peer's hello hash is not
     * available, returns `null`.
     */
    val peerHelloHash: String?

    /**
     * Gets other party's ZID (ZRTP Identifier) data that was received during ZRTP processing. The
     * ZID data can be retrieved after ZRTP receives the first Hello packet from the other party.
     *
     * @return the ZID data as a `byte` array.
     */
    val peerZid: ByteArray?

    /**
     * Gets other party's ZID (ZRTP Identifier) data that was received during ZRTP processing as a
     * `String`. The ZID data can be retrieved after ZRTP receives the first Hello packet
     * from the other party.
     *
     * @return the ZID data as a `String`.
     */
    val peerZidString: String?

    /**
     * Gets the SAS for the current media stream.
     *
     * @return the four character ZRTP SAS.
     */
    val securityString: String?

    /**
     * Returns the timeout value in milliseconds that we will wait and fire timeout secure event if
     * call is not secured.
     *
     * @return the timeout value in milliseconds that we will wait and fire timeout secure event if
     * call is not secured.
     */
    val timeoutValue: Long

    /**
     * Gets the status of the SAS verification.
     *
     * @return `true` when the SAS has been verified.
     */
    val isSecurityVerified: Boolean

    /**
     * Sets the SAS verification
     *
     * @param verified the new SAS verification status
     */
    fun setSASVerification(verified: Boolean)

    /**
     * Set ZRTP version received from the signaling layer.
     * @param version received version
     */
    fun setReceivedSignaledZRTPVersion(version: String)

    /**
     * Set ZRTP hash value received from the signaling layer.
     * @param value hash value
     */
    fun setReceivedSignaledZRTPHashValue(value: String)
}