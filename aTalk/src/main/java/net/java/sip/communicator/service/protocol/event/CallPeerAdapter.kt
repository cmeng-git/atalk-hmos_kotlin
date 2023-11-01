/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

/**
 * An abstract adapter class for receiving call peer (change) events. This class exists only as a
 * convenience for creating listener objects.
 *
 *
 * Extend this class to create a `CallPeerChangeEvent` listener and override the methods for
 * the events of interest. (If you implement the `CallPeerListener` interface, you have to
 * define all of the methods in it. This abstract class defines null methods for them all, so you
 * only have to define methods for events you care about.)
 *
 *
 * @see CallPeerChangeEvent
 *
 * @see CallPeerListener
 *
 *
 * @author Lubomir Marinov
 */
abstract class CallPeerAdapter : CallPeerListener {
    /**
     * Indicates that a change has occurred in the address of the source `CallPeer`.
     *
     * @param evt
     * the `CallPeerChangeEvent` instance containing the source event as well as its
     * previous and its new address
     */
    override fun peerAddressChanged(evt: CallPeerChangeEvent) {}

    /**
     * Indicates that a change has occurred in the display name of the source `CallPeer`.
     *
     * @param evt
     * the `CallPeerChangeEvent` instance containing the source event as well as its
     * previous and its new display names
     */
    override fun peerDisplayNameChanged(evt: CallPeerChangeEvent) {}

    /**
     * Indicates that a change has occurred in the image of the source `CallPeer`.
     *
     * @param evt
     * the `CallPeerChangeEvent` instance containing the source event as well as its
     * previous and its new image
     */
    override fun peerImageChanged(evt: CallPeerChangeEvent) {}

    /**
     * Indicates that a change has occurred in the status of the source `CallPeer`.
     *
     * @param evt
     * the `CallPeerChangeEvent` instance containing the source event as well as its
     * previous and its new status
     */
    override fun peerStateChanged(evt: CallPeerChangeEvent) {}

    /**
     * Indicates that a change has occurred in the transport address that we use to communicate with
     * the source `CallPeer`.
     *
     * @param evt
     * the `CallPeerChangeEvent` instance containing the source event as well as its
     * previous and its new transport address
     */
    override fun peerTransportAddressChanged(evt: CallPeerChangeEvent) {}
}