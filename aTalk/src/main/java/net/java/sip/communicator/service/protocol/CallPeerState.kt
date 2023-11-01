/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp

/**
 * The CallPeerState class reflects the current state of a call peer. In other words when you start
 * calling your grand mother she will be in a INITIATING_CALL state, when her phone rings her state
 * will change to ALERTING_REMOTE_SIDE, and when she replies she will enter a CONNECTED state.
 *
 * Though not mandatory CallPeerState would generally have one of the following life cycles
 * In the case with your grand mother that we just described we have:
 * INITIATING_CALL -> CONNECTING -> ALERTING_REMOTE_USER -> CONNECTED -> DISCONNECTED
 *
 * If your granny was already on the phone we have:
 * INITIATING_CALL -> CONNECTING -> BUSY -> DISCONNECTED
 *
 * Whenever someone tries to reach you:
 * INCOMING_CALL -> CONNECTED -> DISCONNECTED
 *
 * A FAILED state is prone to appear at any place in the above diagram and is generally followed by
 * a disconnected state.
 *
 * Information on call peer state is shown in the phone user interface until they enter the DISCONNECTED state.
 * At that point call peer information is automatically removed from the user interface,
 * and the call is considered terminated.
 *
 * @author Emil Ivov
 * @author Lubomir Marinov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class CallPeerState
/**
 * Create a peer call state object with a value corresponding to the specified string.
 *
 * @param callPeerState a string representation of the state.
 * @param callStateLocalizedStr the localized string representing this state
 */ private constructor(
        /**
         * A string representation of this peer's Call State. Could be _CONNECTED, _FAILED, _CALLING and etc.
         */
        private val callStateStr: String,
        /**
         * A localized string representation of this peer's Call State.
         */
        private val callStateLocalizedStr: String) {
    /**
     * Returns a String representation of the CallPeerState.
     *
     * @return A string value (one of the _BUSY, _CALLING, _CONNECTED, _CONNECTING, _DISCONNECTED,
     * _FAILED, _RINGING constants) representing this call peer state).
     */
    fun getStateString(): String {
        return callStateStr
    }

    /**
     * Returns a localized String representation of the CallPeerState.
     *
     * @return a localized String representation of the CallPeerState
     */
    fun getLocalizedStateString(): String {
        return callStateLocalizedStr
    }

    /**
     * Returns a string representation of this call state. Strings returned by this method have the
     * following format: CallPeerState:<STATE_STRING> and are meant to be used for logging/debugging
     * purposes.
     *
     * @return a string representation of this object.
    </STATE_STRING> */
    override fun toString(): String {
        return javaClass.name + ":" + getStateString()
    }


    companion object {
        /**
         * This constant value indicates a String representation of the UNKNOWN call state.
         */
        private const val _UNKNOWN = "Unknown"

        /**
         * This constant value indicates that the state of the call peer is UNKNOWN - which means
         * that there is no information on the state for the time being (this constant should be used as
         * a default value for newly created call peer that don't yet have an attributed call state.
         */
        val UNKNOWN = CallPeerState(_UNKNOWN, aTalkApp.getResString(R.string.service_gui_UNKNOWN_STATUS))

        /**
         * This constant value indicates a String representation of the INITIATING_CALL state.
         */
        const val _INITIATING_CALL = "Initiating Call"

        /**
         * This constant value indicates that the state of the call peer is INITIATING_CALL - which
         * means that we're currently trying to open a socket and send our request. In the case of SIP
         * for example we will leave this state the moment we receive a "100 Trying" request from a
         * proxy or the remote side.
         */
        val INITIATING_CALL = CallPeerState(_INITIATING_CALL, aTalkApp.getResString(R.string.service_gui_CALL_INITIATING_STATUS))

        /**
         * This constant value indicates a String representation of the CONNECTING call state.
         */
        const val _CONNECTING = "Connecting"

        /**
         * This constant value indicates that the state of the call peer is CONNECTING - which means
         * that a network connection to that peer is currently being established.
         */
        val CONNECTING = CallPeerState(_CONNECTING, aTalkApp.getResString(R.string.service_gui_CONNECTING_STATUS))

        /**
         * This constant value indicates a String representation of the CONNECTING call state
         * but in cases where early media is being exchanged.
         */
        const val _CONNECTING_WITH_EARLY_MEDIA = "Connecting*"

        /**
         * This constant value indicates that the state of the call peer is CONNECTING - which means
         * that a network connection to that peer is currently being established.
         */
        @JvmField
        val CONNECTING_WITH_EARLY_MEDIA = CallPeerState(
                _CONNECTING_WITH_EARLY_MEDIA, aTalkApp.getResString(R.string.service_gui_CONNECTING_EARLY_MEDIA_STATUS))

        /**
         * This constant value indicates that the state of the incoming call peer is CONNECTING - which
         * means that a network connection to that peer is currently being established.
         */
        val CONNECTING_INCOMING_CALL = CallPeerState(
                _CONNECTING_WITH_EARLY_MEDIA, aTalkApp.getResString(R.string.service_gui_CONNECTING_STATUS))

        /**
         * This constant value indicates that the state of the incoming call peer is CONNECTING - which
         * means that a network connection to that peer is currently being established and during the
         * process before hearing the other peer we can still can hear media coming from the server for example.
         */
        val CONNECTING_INCOMING_CALL_WITH_MEDIA = CallPeerState(
                _CONNECTING_WITH_EARLY_MEDIA, aTalkApp.getResString(R.string.service_gui_CONNECTING_EARLY_MEDIA_STATUS))

        /**
         * This constant value indicates a String representation of the ALERTING_REMOTE_SIDE call state.
         */
        const val _ALERTING_REMOTE_SIDE = "Alerting Remote User (Ringing)"

        /**
         * This constant value indicates that the state of the call peer is ALERTING_REMOTE_SIDE -
         * which means that a network connection to that peer has been established and peer's phone is
         * currently alerting the remote user of the current call.
         */
        val ALERTING_REMOTE_SIDE = CallPeerState(_ALERTING_REMOTE_SIDE, aTalkApp.getResString(R.string.service_gui_RINGING_STATUS))

        /**
         * This constant value indicates a String representation of the INCOMING_CALL state.
         */
        const val _INCOMING_CALL = "Incoming Call"

        /**
         * This constant value indicates that the state of the call peer is INCOMING_CALL - which
         * means that the peer is willing to start a call with us. At that point local side should be
         * playing a sound or a graphical alert (the phone is ringing).
         */
        val INCOMING_CALL = CallPeerState(_INCOMING_CALL, aTalkApp.getResString(R.string.service_gui_CALL_INCOMING_STATUS))

        /**
         * This constant value indicates a String representation of the CONNECTED call state.
         */
        const val _CONNECTED = "Connected"

        /**
         * This constant value indicates that the state of the call peer is CONNECTED - which means
         * that there is an ongoing call with that peer.
         */
        @JvmField
        val CONNECTED = CallPeerState(_CONNECTED, aTalkApp.getResString(R.string.service_gui_CONNECTED_STATUS))

        /**
         * This constant value indicates a String representation of the DISCONNECTED call state.
         */
        const val _DISCONNECTED = "Disconnected"

        /**
         * This constant value indicates that the state of the call peer is DISCONNECTED - which
         * means that this peer is not participating :) in the call anymore.
         */
        @JvmField
        val DISCONNECTED = CallPeerState(_DISCONNECTED, aTalkApp.getResString(R.string.service_gui_DISCONNECTED_STATUS))

        /**
         * This constant value indicates a String representation of the REFERRED call state.
         */
        const val _REFERRED = "Referred"

        /**
         * This constant value indicates that the state of the call peer is REFERRED - which means
         * that this peer has transferred us to another peer.
         */
        @JvmField
        val REFERRED = CallPeerState(_REFERRED, aTalkApp.getResString(R.string.service_gui_REFERRED_STATUS))

        /**
         * This constant value indicates a String representation of the BUSY call state.
         */
        const val _BUSY = "Busy"

        /**
         * This constant value indicates that the state of the call peer is BUSY - which means that
         * an attempt to establish a call with that peer has been made and that it has been turned down
         * by them (e.g. because they were already in a call).
         */
        val BUSY = CallPeerState(_BUSY, aTalkApp.getResString(R.string.service_gui_BUSY_STATUS))

        /**
         * This constant value indicates a String representation of the FAILED call state.
         */
        const val _FAILED = "Failed"

        /**
         * This constant value indicates that the state of the call peer is ON_HOLD - which means
         * that an attempt to establish a call with that peer has failed for an unexpected reason.
         */
        @JvmField
        val FAILED = CallPeerState(_FAILED, aTalkApp.getResString(R.string.service_gui_CALL_FAILED))

        /**
         * The constant value being a String representation of the ON_HOLD_LOCALLY call peer state.
         */
        const val _ON_HOLD_LOCALLY = "Locally On Hold"

        /**
         * The constant value indicating that the state of a call peer is locally put on hold.
         */
        @JvmField
        val ON_HOLD_LOCALLY = CallPeerState(_ON_HOLD_LOCALLY, aTalkApp.getResString(R.string.service_gui_LOCALLY_ON_HOLD_STATUS))

        /**
         * The constant value being a String representation of the ON_HOLD_MUTUALLY call peer state.
         */
        const val _ON_HOLD_MUTUALLY = "Mutually On Hold"

        /**
         * The constant value indicating that the state of a call peer is mutually - locally and
         * remotely - put on hold.
         */
        @JvmField
        val ON_HOLD_MUTUALLY = CallPeerState(_ON_HOLD_MUTUALLY, aTalkApp.getResString(R.string.service_gui_MUTUALLY_ON_HOLD_STATUS))

        /**
         * The constant value being a String representation of the ON_HOLD_REMOTELY call peer state.
         */
        const val _ON_HOLD_REMOTELY = "Remotely On Hold"

        /**
         * The constant value indicating that the state of a call peer is remotely put on hold.
         */
        @JvmField
        val ON_HOLD_REMOTELY = CallPeerState(_ON_HOLD_REMOTELY, aTalkApp.getResString(R.string.service_gui_REMOTELY_ON_HOLD_STATUS))

        /**
         * Determines whether a specific `CallPeerState` value signal a call hold regardless of
         * the issuer (which may be local and/or remote).
         *
         * @param state the `CallPeerState` value to be checked whether it signals a call hold
         * @return `true` if the specified `state` signals a call hold; `false`, otherwise
         */
        fun isOnHold(state: CallPeerState?): Boolean {
            return ON_HOLD_LOCALLY == state || ON_HOLD_MUTUALLY == state || ON_HOLD_REMOTELY == state
        }
    }
}