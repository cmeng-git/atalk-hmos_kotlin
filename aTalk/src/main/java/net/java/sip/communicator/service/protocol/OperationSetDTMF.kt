/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import org.atalk.service.neomedia.DTMFTone

/**
 * An `OperationSet` that allows other modules to send DMF tones through this protocol
 * provider.
 *
 * @author JM HEITZ
 */
interface OperationSetDTMF : OperationSet {
    /**
     * Sends the `DTMFTone` `tone` to `callPeer`.
     *
     * @param callPeer
     * the call peer to send `tone` to.
     * @param tone
     * the DTMF tone to send to `callPeer`.
     *
     * @throws OperationFailedException
     * with code OPERATION_NOT_SUPPORTED if DTMF tones are not supported for
     * `callPeer`.
     *
     * @throws IllegalArgumentException
     * in case the call peer does not belong to the underlying implementation.
     */
    @Throws(OperationFailedException::class)
    fun startSendingDTMF(callPeer: CallPeer?, tone: DTMFTone?)

    /**
     * Stop sending of the currently transmitting DTMF tone.
     *
     * @param callPeer
     * the call peer to stop send `tone` to.
     */
    fun stopSendingDTMF(callPeer: CallPeer?)

    companion object {
        /**
         * The minimal tone duration value for RFC4733 is 100 ms.
         */
        const val DEFAULT_DTMF_MINIMAL_TONE_DURATION = 100

        /**
         * The maximal tone duration value is -1 in order to stop sending tone only when user requests
         * to stop it.
         */
        const val DEFAULT_DTMF_MAXIMAL_TONE_DURATION = -1

        /**
         * The default tone volume value.
         */
        const val DEFAULT_DTMF_TONE_VOLUME = 10

        /**
         * The name of the `ConfigurationService` `int` property which indicates the
         * minimal duration for a DTMF tone. The default value is 70 ms.
         */
        const val PROP_MINIMAL_RTP_DTMF_TONE_DURATION = "protocol.minimalRtpDtmfToneDuration"

        /**
         * The name of the `ConfigurationService` `int` property which indicates the
         * maximal duration for a DTMF tone (in ms). The default value is -1 to tell to stop DTMF tones
         * only following user will.
         */
        const val PROP_MAXIMAL_RTP_DTMF_TONE_DURATION = "protocol.maximalRtpDtmfToneDuration"
    }
}