/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import java.util.*

/**
 * Instance of this class is used for listening for notifications coming out of a telephony Provider
 * e.g. incoming Call. Whenever a telephony Provider receives an invitation to a call from a buddy
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface CallListener : EventListener {
    /**
     * This method is called by a protocol provider whenever an incoming call is received.
     *
     * @param event a CallEvent instance describing the new incoming call
     */
    fun incomingCallReceived(event: CallEvent)

    /**
     * This method is called by a protocol provider upon initiation of an outgoing call.
     *
     * @param event a CallEvent instance describing the new incoming call.
     */
    fun outgoingCallCreated(event: CallEvent)

    /**
     * Indicate that all peers have left the source call and that it has been ended. The event may
     * be considered redundant since there are already events issued upon termination of a single
     * call peer but we've decided to keep it for listeners that are only interested in call
     * duration and don't want to follow other call details.
     *
     * @param event the `CallEvent` containing the source call.
     */
    fun callEnded(event: CallEvent)
}