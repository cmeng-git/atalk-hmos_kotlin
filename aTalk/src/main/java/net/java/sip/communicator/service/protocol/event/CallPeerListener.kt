/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import java.util.*

/**
 * Receives events notifying of changes that have occurred within a `CallPeer`. Such changes
 * may pertain to current call peer state, their display name, address, image and (possibly in the
 * future) others.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface CallPeerListener : EventListener {
    /**
     * Indicates that a change has occurred in the status of the source CallPeer.
     *
     * @param evt The `CallPeerChangeEvent` instance containing the source event as well as its
     * previous and its new status.
     */
    fun peerStateChanged(evt: CallPeerChangeEvent)

    /**
     * Indicates that a change has occurred in the display name of the source CallPeer.
     *
     * @param evt The `CallPeerChangeEvent` instance containing the source event as well as its
     * previous and its new display names.
     */
    fun peerDisplayNameChanged(evt: CallPeerChangeEvent)

    /**
     * Indicates that a change has occurred in the address of the source CallPeer.
     *
     * @param evt The `CallPeerChangeEvent` instance containing the source event as well as its
     * previous and its new address.
     */
    fun peerAddressChanged(evt: CallPeerChangeEvent)

    /**
     * Indicates that a change has occurred in the transport address that we use to communicate with
     * the peer.
     *
     * @param evt The `CallPeerChangeEvent` instance containing the source event as well as its
     * previous and its new transport address.
     */
    fun peerTransportAddressChanged(evt: CallPeerChangeEvent)

    /**
     * Indicates that a change has occurred in the image of the source CallPeer.
     *
     * @param evt The `CallPeerChangeEvent` instance containing the source event as well as its
     * previous and its new image.
     */
    fun peerImageChanged(evt: CallPeerChangeEvent)
}