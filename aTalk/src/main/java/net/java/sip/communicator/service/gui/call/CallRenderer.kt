/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.call

import net.java.sip.communicator.service.protocol.CallPeer

/**
 * The `CallRenderer` represents a renderer for a call. All user
 * interfaces representing a call should implement this interface.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface CallRenderer {
    /**
     * Releases the resources acquired by this instance which require explicit
     * disposal (e.g. any listeners added to the depicted
     * `CallConference`, the participating `Call`s, and their
     * associated `CallPeer`s). Invoked by `CallPanel` when it
     * determines that this `CallRenderer` is no longer necessary.
     */
    fun dispose()

    /**
     * Returns the `CallPeerRenderer` corresponding to the given `callPeer`.
     *
     * @param callPeer the `CallPeer`, for which we're looking for a renderer
     * @return the `CallPeerRenderer` corresponding to the given `callPeer`
     */
    fun getCallPeerRenderer(callPeer: CallPeer): CallPeerRenderer?

    /**
     * Starts the timer that counts call duration.
     */
    fun startCallTimer()

    /**
     * Stops the timer that counts call duration.
     */
    fun stopCallTimer()

    /**
     * Returns `true` if the call timer has been started, otherwise returns `false`.
     * @return `true` if the call timer has been started, otherwise returns `false`
     */
    fun isCallTimerStarted(): Boolean

    /**
     * Updates the state of the general hold button. The hold button is selected
     * only if all call peers are locally or mutually on hold at the same time.
     * In all other cases the hold button is unselected.
     */
    fun updateHoldButtonState()
}