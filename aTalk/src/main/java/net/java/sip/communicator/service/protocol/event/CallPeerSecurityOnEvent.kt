/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.CallPeer
import org.atalk.service.neomedia.SrtpControl

/**
 * The `CallPeerSecurityOnEvent` is triggered whenever a communication with a given peer is
 * going secure.
 *
 * @author Werner Dittmann
 * @author Yana Stamcheva
 */
class CallPeerSecurityOnEvent(callPeer: CallPeer?, sessionType: Int, cipher: String,
        srtpControl: SrtpControl) : CallPeerSecurityStatusEvent(callPeer, sessionType) {
    private val cipher: String
    private val srtpControl: SrtpControl

    /**
     * The event constructor
     *
     * @param callPeer
     * the call peer associated with this event
     * @param sessionType
     * the type of the session, either [CallPeerSecurityStatusEvent.AUDIO_SESSION] or
     * [CallPeerSecurityStatusEvent.VIDEO_SESSION]
     * @param cipher
     * the cipher used for the encryption
     * @param srtpControl
     * the security controller that caused this event
     */
    init {
        this.srtpControl = srtpControl
        this.cipher = cipher
    }

    /**
     * Returns the cipher used for the encryption.
     *
     * @return the cipher used for the encryption.
     */
    fun getCipher(): String {
        return cipher
    }

    /**
     * Gets the security controller that caused this event.
     *
     * @return the security controller that caused this event.
     */
    fun getSecurityController(): SrtpControl {
        return srtpControl
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}