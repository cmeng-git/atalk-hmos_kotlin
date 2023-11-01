/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.call

import net.java.sip.communicator.service.protocol.CallPeerState
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityNegotiationStartedEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOffEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOnEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityTimeoutEvent

/**
 * The `CallPeerRenderer` interface is meant to be implemented by
 * different renderers of `CallPeer`s. Through this interface they would
 * could be updated in order to reflect the current state of the CallPeer.
 *
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface CallPeerRenderer {
    /**
     * Releases the resources (which require explicit disposal) acquired by this
     * `CallPeerRenderer` throughout its lifetime and prepares it for garbage collection.
     */
    fun dispose()

    /**
     * Returns the parent call renderer.
     *
     * @return the parent call renderer
     */
    fun getCallRenderer(): CallRenderer?

    /**
     * Indicates if the local video component is currently visible.
     *
     * @return `true` if the local video component is currently visible, `false` - otherwise
     */
    fun isLocalVideoVisible(): Boolean

    /**
     * Prints the given DTMG character through this `CallPeerRenderer`.
     *
     * @param dtmfChar the DTMF char to print
     */
    fun printDTMFTone(dtmfChar: Char)

    /**
     * The handler for the security event received. The security event
     * for starting establish a secure connection.
     *
     * @param securityStartedEvent the security started event received
     */
    fun securityNegotiationStarted(securityStartedEvent: CallPeerSecurityNegotiationStartedEvent)

    /**
     * Indicates that the security is turned off.
     *
     * @param evt Details about the event that caused this message.
     */
    fun securityOff(evt: CallPeerSecurityOffEvent)

    /**
     * Indicates that the security is turned on.
     *
     * @param evt Details about the event that caused this message.
     */
    fun securityOn(evt: CallPeerSecurityOnEvent)

    /**
     * Indicates that the security status is pending confirmation.
     */
    fun securityPending()

    /**
     * Indicates that the security is timeouted, is not supported by the other end.
     *
     * @param evt Details about the event that caused this message.
     */
    fun securityTimeout(evt: CallPeerSecurityTimeoutEvent)

    /**
     * Sets the reason of a call failure if one occurs. The renderer should
     * display this reason to the user.
     *
     * @param reason the reason of the error to set
     */
    fun setErrorReason(reason: String?)

    /**
     * Sets the mute property value.
     *
     * @param mute `true` to mute the `CallPeer` depicted by this
     * instance; `false`, otherwise
     */
    fun setMute(mute: Boolean)

    /**
     * Sets the "on hold" property value.
     *
     * @param onHold `true` to put the `CallPeer` depicted by this
     * instance on hold; `false`, otherwise
     */
    fun setOnHold(onHold: Boolean)

    /**
     * Sets the `image` of the peer.
     *
     * @param image the image to set
     */
    fun setPeerImage(image: ByteArray?)

    /**
     * Sets the name of the peer.
     *
     * @param name the name of the peer
     */
    fun setPeerName(name: String?)

    /**
     * Sets the state of the contained call peer by specifying the state name.
     *
     * @param oldState the previous state of the peer
     * @param newState the new state of the peer
     * @param stateString the state of the contained call peer
     */
    fun setPeerState(oldState: CallPeerState?, newState: CallPeerState?, stateString: String?)

    /**
     * Shows/hides the security panel.
     *
     * @param visible `true` to show the security panel or `false` to hide it
     */
    fun setSecurityPanelVisible(visible: Boolean)
    /**
     * @return true if DTMF handling enabled
     */
    /**
     * Enable or disable DTMF tone handle
     * @param enabled - if true DTMF tone is enabled and disabled if false
     */
    var isDtmfToneEnabled: Boolean
}