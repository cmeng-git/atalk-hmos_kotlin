/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * The CallState class reflects the current state of a call. Compared to the state of a single call
 * peer, a call itself has a more limited amount of sets which follow the following cycle:
 *
 * CALL_INITIALIZATION -> CALL_IN_PROGRESS -> CALL_ENDED
 *
 * When you start calling someone or receive a call alert, the call that is automatically created is
 * in the CALL_INITIALIZATION_PHASE. As soon as one of the peers passes into a CONNECTED call peer
 * state, the call would enter the CALL_IN_PROGRESS state. When the last call peer enters a DISCONNECTED
 * state the call itself would go into the CALL_ENDED state and will be ready for garbage collection.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class CallState
/**
 * Create a call state object with a value corresponding to the specified string.
 *
 * callState a string representation of the state.
 */
private constructor(
        /**
         * A string representation of this Call State. Could be _CALL_INITIALIZATION, _CALL_IN_PROGRESS, CALL_ENDED.
         */
        private val callStateStr: String) {
    /**
     * Returns a String representation of this CallState.
     *
     * @return a string value (one of the _CALL_XXX constants) representing this call state).
     */
    fun getStateString(): String {
        return callStateStr
    }

    /**
     * Returns a string representation of this call state. Strings returned by this method have the
     * following format: "CallState:<STATE_STRING>" and are meant to be used for logging/debugging purposes.
     *
     * @return a string representation of this object. </STATE_STRING> */
    override fun toString(): String {
        return javaClass.name + ":" + getStateString()
    }

    companion object {
        /**
         * This constant containing a String representation of the CALL_INITIALIZATION state.
         */
        private const val _CALL_INITIALIZATION = "Initializing"

        /**
         * This constant value indicates that the associated call is currently in an initialization state.
         */
        val CALL_INITIALIZATION = CallState(_CALL_INITIALIZATION)

        /**
         * This constant containing a String representation of the CALL_IN_PROGRESS state.
         */
        private const val _CALL_IN_PROGRESS = "In Progress"

        /**
         * This constant value indicates that the associated call is currently in an active state.
         */
        @JvmField
        val CALL_IN_PROGRESS = CallState(_CALL_IN_PROGRESS)

        /**
         * This constant containing a String representation of the CALL_ENDED state.
         */
        private const val _CALL_ENDED = "Ended"

        /**
         * This constant value indicates that the associated call is currently in a terminated phase.
         */
        @JvmField
        val CALL_ENDED = CallState(_CALL_ENDED)

        /**
         * This constant containing a String representation of the CALL_REFERRED state.
         */
        private const val _CALL_REFERRED = "Referred"

        /**
         * This constant value indicates that the associated call is currently referred.
         */
        @JvmField
        val CALL_REFERRED = CallState(_CALL_REFERRED)
    }
}