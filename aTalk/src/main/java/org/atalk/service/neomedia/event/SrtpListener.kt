/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.event

import org.atalk.service.neomedia.SrtpControl
import org.atalk.util.MediaType

/**
 * The `SrtpListener` is meant to be used by the media stream creator, as the name indicates
 * in order to be notified when a security event has occurred that concerns a secure (media)
 * transport i.e. `SrtpControl` such as ZRTP, SDES and DTLS-SRTP.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface SrtpListener {
    /**
     * Indicates that the security has been turned on. When we are in the case of using multistreams
     * when the master stream ZRTP is initialized and established the param multiStreamData holds
     * the data needed for the slave streams to establish their sessions. If this is a
     * securityTurnedOn event on non master stream the multiStreamData is null.
     *
     * @param mediaType the `MediaType` of the call session
     * @param cipher the security cipher that encrypts the call
     * @param sender the control that initiated the event.
     */
    fun securityTurnedOn(mediaType: MediaType?, cipher: String?, sender: SrtpControl?)

    /**
     * Indicates that the security has been turned off.
     *
     * @param mediaType the `MediaType` of the call session
     */
    fun securityTurnedOff(mediaType: MediaType?)

    /**
     * Indicates that a security message has occurred associated with a failure/warning or
     * information coming from the encryption protocol/secure transport.
     *
     * @param messageType the type of the message
     * @param i18nMessage the internationalized message
     * @param severity severity level
     */
    fun securityMessageReceived(messageType: String?, i18nMessage: String?, severity: Int)

    /**
     * Indicates that the other party has timed out replying to our offer to secure the connection.
     *
     * @param mediaType the `MediaType` of the call session
     */
    fun securityTimeout(mediaType: MediaType?)

    /**
     * Indicates that we started the process of securing the connection.
     *
     * @param mediaType the `MediaType` of the call session
     * @param sender the control that initiated the event.
     */
    fun securityNegotiationStarted(mediaType: MediaType?, sender: SrtpControl)

    companion object {
        /**
         * This is a information message. Security will be established.
         */
        const val INFORMATION = 0

        /**
         * This is a warning message. Security will not be established.
         */
        const val WARNING = 1

        /**
         * This is a severe error. Security will not be established.
         */
        const val SEVERE = 2

        /**
         * This is an error message. Security will not be established.
         */
        const val ERROR = 3
    }
}