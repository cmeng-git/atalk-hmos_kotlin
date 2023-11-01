/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import java.beans.PropertyChangeEvent
import net.java.sip.communicator.service.protocol.CallPeer as CallPeer1

/**
 * CallPeerChangeEvent-s are triggered whenever a change occurs in a CallPeer.
 * Dispatched events may be of one of the following types.
 *
 * a. CALL_PEER_STATUS_CHANGE - indicate a change in the status of the peer.
 * b. CALL_PEER_DISPLAY_NAME_CHANGE - mean peer displayName has changed
 * c. CALL_PEER_ADDRESS_CHANGE - mean peer address has changed.
 * d. CALL_PEER_TRANSPORT_ADDRESS_CHANGE - mean peer transport address (the one use for communicate) has changed.
 * e. CALL_PEER_IMAGE_CHANGE - peer updated photo.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class CallPeerChangeEvent
/**
 * Creates a CallPeerChangeEvent with the specified source, type, oldValue and newValue.
 *
 * @param source the peer that produced the event.
 * @param type the type of the event (i.e. address change, state change etc.).
 * @param oldValue of the changed property before the event occurred
 * @param newValue current value of the changed property.
 */
@JvmOverloads constructor(source: CallPeer1?, type: String?, oldValue: Any?, newValue: Any?,
        /**
         * A reason string further explaining the event (may be null). The string would be mostly used
         * for events issued upon a CallPeerState transition that has led to a FAILED state.
         */
        private val reason: String? = null,
        /**
         * Reason code, if any, for the peer state change.
         */
        reasonCode: Int = -1) : PropertyChangeEvent(source, type, oldValue, newValue) {
    /**
     * Creates a CallPeerChangeEvent with the specified source, type, oldValue and newValue.
     *
     * @param source the peer that produced the event.
     * @param type the type of the event (i.e. address change, state change etc.).
     * @param oldValue the value of the changed property before the event occurred
     * @param newValue current value of the changed property.
     * @param reason a string containing a human readable reason that triggered this event (may be null).
     * @param reasonCode a code for the reason that triggered this event (may be -1 as not specified).
     */
    /**
     * Creates a CallPeerChangeEvent with the specified source, type, oldValue and newValue.
     *
     * @param source the peer that produced the event.
     * @param type the type of the event (i.e. address change, state change etc.).
     * @param oldValue the value of the changed property before the event occurred
     * @param newValue current value of the changed property.
     * @param reason a string containing a human readable reason that triggered this event (may be null).
     */
    val reasonCode = reasonCode

    /**
     * Returns the type of this event.
     *
     * @return a string containing one of the following values: CALL_PEER_STATUS_CHANGE,
     * CALL_PEER_DISPLAY_NAME_CHANGE, CALL_PEER_ADDRESS_CHANGE, CALL_PEER_IMAGE_CHANGE.
     */
    fun getEventType(): String {
        return propertyName
    }

    /**
     * Returns a String representation of this CallPeerChangeEvent.
     *
     * @return A a String representation of this CallPeerChangeEvent.
     */
    override fun toString(): String {
        return ("CallPeerChangeEvent: type=" + getEventType() + " oldV=" + oldValue + " newV="
                + newValue + " for peer=" + getSourceCallPeer())
    }

    /**
     * Returns the `CallPeer` that this event is about.
     *
     * @return a reference to the `CallPeer` that is the source of this event.
     */
    fun getSourceCallPeer(): CallPeer1 {
        return getSource() as CallPeer1
    }

    /**
     * Returns a reason string further explaining the event (may be null). The string would be
     * mostly used for events issued upon a CallPeerState transition that has led to a FAILED state.
     *
     * @return a reason string further explaining the event or null if no reason was set.
     */
    fun getReasonString(): String? {
        return reason
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * An event type indicating that the corresponding event is caused by a change of the CallPeer status.
         */
        const val CALL_PEER_STATE_CHANGE = "CallPeerStatusChange"

        /**
         * An event type indicating that the corresponding event is caused by a change of the peer display name.
         */
        const val CALL_PEER_DISPLAY_NAME_CHANGE = "CallPeerDisplayNameChange"

        /**
         * An event type indicating that the corresponding event is caused by a change of the peer address.
         */
        const val CALL_PEER_ADDRESS_CHANGE = "CallPeerAddressChange"

        /**
         * An event type indicating that the corresponding event is caused by a change of the peer transport address.
         */
        const val CALL_PEER_TRANSPORT_ADDRESS_CHANGE = "CallPeerAddressChange"

        /**
         * An event type indicating that the corresponding event is caused by a change of the peer photo/picture.
         */
        const val CALL_PEER_IMAGE_CHANGE = "CallPeerImageChange"

        /**
         * Code indicating normal call clear.
         */
        const val NORMAL_CALL_CLEARING = 200
    }
}