/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

/**
 * An abstract adapter class for receiving call-state (change) events. This class exists only as a
 * convenience for creating listener objects.
 *
 *
 * Extend this class to create a `CallChangeEvent` listener and override the methods for
 * the events of interest. (If you implement the `CallChangeListener` interface, you have
 * to define all of the methods in it. This abstract class defines null methods for them all, so you
 * only have to define methods for events you care about.)
 *
 *
 * @see CallChangeEvent
 *
 * @see CallChangeListener
 *
 *
 * @author Lubomir Marinov
 */
abstract class CallChangeAdapter : CallChangeListener {
    /**
     * A dummy implementation of this listener's callPeerAdded() method.
     *
     * @param evt
     * the `CallPeerEvent` containing the source call and call peer.
     */
    override fun callPeerAdded(evt: CallPeerEvent) {}

    /**
     * A dummy implementation of this listener's callPeerRemoved() method.
     *
     * @param evt
     * the `CallPeerEvent` containing the source call and call peer.
     */
    override fun callPeerRemoved(evt: CallPeerEvent) {}

    /**
     * A dummy implementation of this listener's callStateChanged() method.
     *
     * @param evt
     * the `CallChangeEvent` instance containing the source calls and its old and new
     * state.
     */
    override fun callStateChanged(evt: CallChangeEvent) {}
}