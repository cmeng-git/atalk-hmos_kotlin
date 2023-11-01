/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.CallPeer

/**
 * The `CallPeerSecurityAuthenticationEvent` is triggered whenever a the security strings are
 * received in a secure call.
 *
 * @author Yana Stamcheva
 */
class CallPeerSecurityOffEvent
/**
 * The event constructor.
 *
 * @param callPeer
 * the call peer associated with this event
 * @param sessionType
 * the type of the session: audio or video
 */
(callPeer: CallPeer?, sessionType: Int) : CallPeerSecurityStatusEvent(callPeer, sessionType) {
    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}