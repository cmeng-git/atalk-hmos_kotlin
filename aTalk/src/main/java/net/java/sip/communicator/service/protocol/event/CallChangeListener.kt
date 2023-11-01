/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import java.util.*

/**
 * A call change listener receives events indicating that a call has changed and a peer has either left or joined.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface CallChangeListener : EventListener {
    /**
     * Indicates that a new call peer has joined the source call.
     *
     * @param evt the `CallPeerEvent` containing the source call and call peer.
     */
    fun callPeerAdded(evt: CallPeerEvent)

    /**
     * Indicates that a call peer has left the source call.
     *
     * @param evt the `CallPeerEvent` containing the source call and call peer.
     */
    fun callPeerRemoved(evt: CallPeerEvent)

    /**
     * Indicates that a change has occurred in the state of the source call.
     *
     * @param evt the `CallChangeEvent` instance containing the source calls and its old and new state.
     */
    fun callStateChanged(evt: CallChangeEvent)
}