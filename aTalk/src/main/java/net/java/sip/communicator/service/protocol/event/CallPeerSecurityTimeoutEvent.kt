/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.CallPeer

/**
 * The `CallPeerSecurityTimeoutEvent` is triggered whenever a communication with a given peer
 * cannot be established, the peer did not answer our tries to secure the connection.
 *
 * @author Damian Minkov
 */
class CallPeerSecurityTimeoutEvent
/**
 * The event constructor
 *
 * @param callPeer
 * the call peer associated with this event
 * @param sessionType
 * the type of the session, either [CallPeerSecurityStatusEvent.AUDIO_SESSION] or
 * [CallPeerSecurityStatusEvent.VIDEO_SESSION]
 */
(callPeer: CallPeer?, sessionType: Int) : CallPeerSecurityStatusEvent(callPeer, sessionType) {
    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}