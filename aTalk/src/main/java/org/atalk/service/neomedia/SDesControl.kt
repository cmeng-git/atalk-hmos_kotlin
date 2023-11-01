/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import ch.imvs.sdes4j.srtp.SrtpCryptoAttribute

/**
 * SDES based SRTP MediaStream encryption control.
 *
 * @author Ingo Bauersachs
 * @author Eng Chong Meng
 */
interface SDesControl : SrtpControl {
    /**
     * Gets the crypto attribute of the incoming MediaStream.
     *
     * @return the crypto attribute of the incoming MediaStream.
     */
    val inAttribute: SrtpCryptoAttribute?

    /**
     * Returns the crypto attributes enabled on this computer.
     *
     * @return The crypto attributes enabled on this computer.
     */
    val initiatorCryptoAttributes: Array<SrtpCryptoAttribute>?

    /**
     * Gets the crypto attribute of the outgoing MediaStream.
     *
     * @return the crypto attribute of the outgoing MediaStream.
     */
    val outAttribute: SrtpCryptoAttribute?

    /**
     * Gets all supported cipher suites.
     *
     * @return all supported cipher suites.
     */
    val supportedCryptoSuites: Iterable<String>

    /**
     * Selects the local crypto attribute from the initial offering (
     * [.getInitiatorCryptoAttributes]) based on the peer's first matching cipher suite.
     *
     * @param peerAttributes The peer's crypto offers.
     * @return A SrtpCryptoAttribute when a matching cipher suite was found; `null`, otherwise.
     */
    fun initiatorSelectAttribute(peerAttributes: Iterable<SrtpCryptoAttribute>): SrtpCryptoAttribute?

    /**
     * Chooses a supported crypto attribute from the peer's list of supplied attributes and creates
     * the local crypto attribute. Used when the control is running in the role as responder.
     *
     * @param peerAttributes The peer's crypto attribute offering.
     * @return The local crypto attribute for the answer of the offer or `null` if no
     * matching cipher suite could be found.
     */
    fun responderSelectAttribute(peerAttributes: Iterable<SrtpCryptoAttribute>): SrtpCryptoAttribute?

    /**
     * Sets the enabled SDES ciphers.
     *
     * @param ciphers The list of enabled ciphers.
     */
    fun setEnabledCiphers(ciphers: Iterable<String>)

    companion object {
        /**
         * Name of the config setting that supplies the default enabled cipher suites. Cipher suites are
         * comma-separated.
         */
        const val SDES_CIPHER_SUITES = "neomedia.SDES_CIPHER_SUITES"
    }
}