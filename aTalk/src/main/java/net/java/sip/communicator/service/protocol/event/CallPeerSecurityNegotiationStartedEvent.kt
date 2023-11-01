/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.CallPeer
import org.atalk.service.neomedia.SrtpControl

/**
 * The `CallPeerSecurityNegotiationStartedEvent` is triggered whenever a communication with a
 * given peer is established, we started securing the connection.
 *
 * @author Damian Minkov
 */
class CallPeerSecurityNegotiationStartedEvent(callPeer: CallPeer?, sessionType: Int,
        srtpControl: SrtpControl) : CallPeerSecurityStatusEvent(callPeer, sessionType) {
    /**
     * The sender of this event.
     */
    private val srtpControl: SrtpControl

    /**
     * The event constructor
     *
     * @param callPeer
     * the call peer associated with this event
     * @param sessionType
     * the type of the session, either
     * [net.java.sip.communicator.service.protocol.event.CallPeerSecurityStatusEvent.AUDIO_SESSION]
     * or
     * [net.java.sip.communicator.service.protocol.event.CallPeerSecurityStatusEvent.VIDEO_SESSION]
     * @param srtpControl
     * the security controller that caused this event
     */
    init {
        this.srtpControl = srtpControl
    }

    /**
     * Gets the security controller that caused this event.
     *
     * @return the security controller that caused this event.
     */
    val securityController: SrtpControl
        get() = srtpControl

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}