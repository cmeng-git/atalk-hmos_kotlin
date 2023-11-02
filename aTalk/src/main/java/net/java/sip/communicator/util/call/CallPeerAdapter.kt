/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.call

import net.java.sip.communicator.service.gui.call.CallPeerRenderer
import net.java.sip.communicator.service.protocol.CallPeer
import net.java.sip.communicator.service.protocol.CallPeerState
import net.java.sip.communicator.service.protocol.event.CallPeerAdapter
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityListener
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityMessageEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityNegotiationStartedEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOffEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOnEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityTimeoutEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

/**
 * `CallPeerAdapter` implements common `CallPeer` related
 * listeners in order to facilitate the task of implementing
 * `CallPeerRenderer`.
 *
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class CallPeerAdapter(
        /**
         * The `CallPeer` which is depicted by [.renderer].
         */
        private val peer: CallPeer,
        /**
         * The `CallPeerRenderer` which is facilitated by this instance.
         */
        private val renderer: CallPeerRenderer) : CallPeerAdapter(), CallPeerSecurityListener, PropertyChangeListener {
    /**
     * Initializes a new `CallPeerAdapter` instance which is to listen to
     * a specific `CallPeer` on behalf of a specific
     * `CallPeerRenderer`. The new instance adds itself to the specified
     * `CallPeer` as a listener for each of the implemented listener types.
     *
     * peer the `CallPeer` which the new instance is to listen to
     * on behalf of the specified `renderer`
     * renderer the `CallPeerRenderer` which is to be facilitated
     * by the new instance
     */
    init {
        peer.addCallPeerListener(this)
        peer.addCallPeerSecurityListener(this)
        peer.addPropertyChangeListener(this)
    }

    /**
     * Removes the listeners implemented by this instance from the associated
     * `CallPeer` and prepares it for garbage collection.
     */
    fun dispose() {
        peer.removeCallPeerListener(this)
        peer.removeCallPeerSecurityListener(this)
        peer.removePropertyChangeListener(this)
    }

    /**
     * {@inheritDoc}
     */
    override fun peerDisplayNameChanged(evt: CallPeerChangeEvent) {
        if (peer == evt.getSourceCallPeer()) renderer.setPeerName(evt.newValue as String)
    }

    /**
     * {@inheritDoc}
     */
    override fun peerImageChanged(evt: CallPeerChangeEvent) {
        if (peer == evt.getSourceCallPeer()) renderer.setPeerImage(evt.newValue as ByteArray)
    }

    /**
     * {@inheritDoc}
     */
    override fun peerStateChanged(evt: CallPeerChangeEvent) {
        val sourcePeer = evt.getSourceCallPeer()
        if (sourcePeer != peer) return
        val newState = evt.newValue as CallPeerState
        val oldState = evt.oldValue as CallPeerState
        val newStateString = sourcePeer.getState()!!.getLocalizedStateString()
        if (newState === CallPeerState.CONNECTED) {
            if (!CallPeerState.isOnHold(oldState)) {
                if (!renderer.getCallRenderer()!!.isCallTimerStarted()) renderer.getCallRenderer()!!.startCallTimer()
            } else {
                renderer.setOnHold(false)
                renderer.getCallRenderer()!!.updateHoldButtonState()
            }
        } else if (newState === CallPeerState.DISCONNECTED) {
            // The call peer should be already removed from the call
            // see CallPeerRemoved
        } else if (newState === CallPeerState.FAILED) {
            // The call peer should be already removed from the call
            // see CallPeerRemoved
        } else if (CallPeerState.isOnHold(newState)) {
            renderer.setOnHold(true)
            renderer.getCallRenderer()!!.updateHoldButtonState()
        }
        renderer.setPeerState(oldState, newState, newStateString)
        val reason = evt.getReasonString()
        if (reason != null) renderer.setErrorReason(reason)
    }

    /**
     * {@inheritDoc}
     */
    override fun propertyChange(ev: PropertyChangeEvent) {
        val propertyName = ev.propertyName
        if (propertyName == CallPeer.MUTE_PROPERTY_NAME) {
            val mute = ev.newValue as Boolean
            renderer.setMute(mute)
        }
    }

    /**
     * {@inheritDoc}
     *
     * `CallPeerAdapter` does nothing.
     */
    override fun securityMessageReceived(event: CallPeerSecurityMessageEvent) {}

    /**
     * {@inheritDoc}
     */
    override fun securityNegotiationStarted(
            securityStartedEvent: CallPeerSecurityNegotiationStartedEvent) {
        if (peer == securityStartedEvent.source) renderer.securityNegotiationStarted(securityStartedEvent)
    }

    /**
     * {@inheritDoc}
     */
    override fun securityOff(securityEvent: CallPeerSecurityOffEvent) {
        if (peer == securityEvent.source) renderer.securityOff(securityEvent)
    }

    /**
     * {@inheritDoc}
     */
    override fun securityOn(securityEvent: CallPeerSecurityOnEvent) {
        if (peer == securityEvent.source) renderer.securityOn(securityEvent)
    }

    /**
     * {@inheritDoc}
     */
    override fun securityTimeout(securityTimeoutEvent: CallPeerSecurityTimeoutEvent) {
        if (peer == securityTimeoutEvent.source) renderer.securityTimeout(securityTimeoutEvent)
    }
}