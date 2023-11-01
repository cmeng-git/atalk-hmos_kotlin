/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.Call
import net.java.sip.communicator.service.protocol.CallPeer
import java.util.*

/**
 *
 * @author Emil Ivov
 */
class CallPeerEvent
/**
 * Creates a call peer event instance indicating that an event with id `eventID` has
 * happened to `sourceCallPeer` in `sourceCall`
 *
 * @param sourceCallPeer
 * the call peer that this event is about.
 * @param sourceCall
 * the call that the source call peer is associated with.
 * @param eventID
 * one of the CALL_PEER_XXX member ints indicating the type of this event.
 */ @JvmOverloads constructor(sourceCallPeer: CallPeer?,
        /**
         * The call that the source call peer is associated with.
         */
        private val sourceCall: Call<*>,
        /**
         * The id indicating the type of this event.
         */
        private val eventID: Int,
        /**
         * Indicates if adding/removing peer should be delayed or not.
         */
        private val delayed: Boolean = false) : EventObject(sourceCallPeer) {
    /**
     * Creates a call peer event instance indicating that an event with id `eventID` has
     * happened to `sourceCallPeer` in `sourceCall`
     *
     * @param sourceCallPeer
     * the call peer that this event is about.
     * @param sourceCall
     * the call that the source call peer is associated with.
     * @param eventID
     * one of the CALL_PEER_XXX member ints indicating the type of this event.
     * @param delayed
     * initial value for `delayed` property. If the value is true adding/removing peer
     * from GUI will be delayed.
     */
    /**
     * Checks whether the adding/removing of the peer should be delayed or not.
     *
     * @return true if the adding/removing should be delayed from the GUI and false if not.
     */
    fun isDelayed(): Boolean {
        return delayed
    }

    /**
     * Returns one of the CALL_PEER_XXX member ints indicating the type of this event.
     *
     * @return one of the CALL_PEER_XXX member ints indicating the type of this event.
     */
    fun getEventID(): Int {
        return eventID
    }

    /**
     * Returns the call that the source call peer is associated with.
     *
     * @return a reference to the `Call` that the source call peer is associated with.
     */
    fun getSourceCall(): Call<*> {
        return sourceCall
    }

    /**
     * Returns the source call peer (the one that this event is about).
     *
     * @return a reference to the source `CallPeer` instance.
     */
    fun getSourceCallPeer(): CallPeer {
        return getSource() as CallPeer
    }

    /**
     * Returns a String representation of this `CallPeerEvent`.
     *
     * @return a String representation of this `CallPeerEvent`.
     */
    override fun toString(): String {
        return ("CallPeerEvent: ID=" + getEventID() + " source peer=" + getSourceCallPeer()
                + " source call=" + getSourceCall())
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * An event id value indicating that this event is about the fact that the source call peer has
         * joined the source call.
         */
        const val CALL_PEER_ADDED = 1

        /**
         * An event id value indicating that this event is about the fact that the source call peer has
         * left the source call.
         */
        const val CALL_PEER_REMOVED = 2
    }
}