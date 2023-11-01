/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.Call
import java.beans.PropertyChangeEvent

/**
 * CallChangeEvent-s are triggered whenever a change occurs in a Call. Dispatched events may be of
 * one of the following types.
 *
 * CALL_STATE_CHANGE - indicates a change in the state of a Call.
 *
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class CallChangeEvent
/**
 * Creates a CallChangeEvent with the specified source, type, oldValue and newValue.
 *
 * @param source the peer that produced the event.
 * @param type the type of the event (the name of the property that has changed).
 * @param oldValue the value of the changed property before the event occurred
 * @param newValue current value of the changed property.
 */
@JvmOverloads constructor(source: Call<*>?, type: String?, oldValue: Any?, newValue: Any?,
        /**
         * The `CallPeerChangeEvent`, if any, which is the cause for this `CallChangeEvent` to be fired.
         * For example, when the last `CallPeer` in a `Call` is disconnected, the `Call` will end.
         */
        val cause: CallPeerChangeEvent? = null) : PropertyChangeEvent(source, type, oldValue, newValue) {
    /**
     * The event which was the cause for current event, like last peer removed from call will hangup
     * current call, if any, otherwise is null.
     *
     * @return `CallPeerChangeEvent` that represents the cause
     */
    /**
     * Creates a CallChangeEvent with the specified source, type, oldValue and newValue.
     *
     * @param source the peer that produced the event.
     * @param type the type of the event (the name of the property that has changed).
     * @param oldValue the value of the changed property before the event occurred
     * @param newValue current value of the changed property.
     * @param cause the event that causes this event, if any(null otherwise).
     */
    /**
     * Returns the type of this event.
     *
     * @return a string containing the name of the property whose change this event is reflecting.
     */
    val eventType: String
        get() = propertyName

    /**
     * The Call on which the event has occurred.
     *
     * @return The Call on which the event has occurred.
     */
    val sourceCall: Call<*>
        get() = getSource() as Call<*>

    /**
     * Returns a String representation of this CallChangeEvent.
     *
     * @return A a String representation of this CallChangeEvent.
     */
    override fun toString(): String {
        return "CallChangeEvent: type=" + eventType + " oldV=" + oldValue + " newV=" + newValue
    }

    companion object {
        /**
         * The type of `CallChangeEvent` which indicates that the state of the associated
         * `Call` has changed.
         */
        const val CALL_STATE_CHANGE = "CallState"

        /**
         * The type of `CallChangeEvent` which indicates that there was some kind of change in
         * the participants in the associated `Call` (e.g. a `CallPeer` participating in
         * the `Call` has enabled or disabled video)
         */
        const val CALL_PARTICIPANTS_CHANGE = "CallParticipantsChanged"

        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}