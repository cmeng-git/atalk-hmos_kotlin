/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.CallChangeEvent
import net.java.sip.communicator.service.protocol.event.CallChangeListener
import net.java.sip.communicator.service.protocol.event.CallPeerConferenceAdapter
import net.java.sip.communicator.service.protocol.event.CallPeerConferenceEvent
import net.java.sip.communicator.service.protocol.event.CallPeerConferenceListener
import net.java.sip.communicator.service.protocol.event.CallPeerEvent
import org.atalk.util.event.PropertyChangeNotifier
import java.util.*

/**
 * Represents the telephony conference-related state of a `Call`. Multiple `Call`
 * instances share a single `CallConference` instance when the former are into a telephony
 * conference i.e. the local peer/user is the conference focus. `CallConference` is
 * protocol-agnostic and thus enables cross-protocol conferences. Since a non-conference
 * `Call` may be converted into a conference `Call` at any time, every `Call`
 * instance maintains a `CallConference` instance regardless of whether the `Call` in
 * question is participating in a telephony conference.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
open class CallConference @JvmOverloads constructor(
        /**
         * The indicator which determines whether the telephony conference represented by this instance
         * is utilizing the Jitsi Videobridge server-side telephony conferencing technology.
         */
        val isJitsiVideobridge: Boolean = false) : PropertyChangeNotifier() {
    /**
     * The `CallChangeListener` which listens to changes in the `Call`s participating
     * in this telephony conference.
     */
    private val callChangeListener = object : CallChangeListener {
        override fun callPeerAdded(evt: CallPeerEvent) {
            onCallPeerEvent(evt)
        }

        override fun callPeerRemoved(evt: CallPeerEvent) {
            onCallPeerEvent(evt)
        }

        override fun callStateChanged(evt: CallChangeEvent) {
            this@CallConference.callStateChanged(evt)
        }
    }

    /**
     * The list of `CallChangeListener`s added to the `Call`s participating in this
     * telephony conference via [.addCallChangeListener].
     */
    private val callChangeListeners = LinkedList<CallChangeListener>()

    /**
     * The `CallPeerConferenceListener` which listens to the `CallPeer`s associated
     * with the `Call` s participating in this telephony conference.
     */
    private val callPeerConferenceListener = object : CallPeerConferenceAdapter() {
        /**
         * {@inheritDoc}
         *
         * Invokes [CallConference.onCallPeerConferenceEvent].
         */
        override fun onCallPeerConferenceEvent(ev: CallPeerConferenceEvent) {
            this@CallConference.onCallPeerConferenceEvent(ev)
        }

        /**
         * {@inheritDoc}
         *
         * Invokes [CallConference.onCallPeerConferenceEvent].
         */
        override fun conferenceMemberErrorReceived(conferenceEvent: CallPeerConferenceEvent) {
            this@CallConference.onCallPeerConferenceEvent(conferenceEvent)
        }
    }

    /**
     * The list of `CallPeerConferenceListener`s added to the `CallPeer`s associated
     * with the `CallPeer`s participating in this telephony conference via
     * [.addCallPeerConferenceListener].
     */
    private val callPeerConferenceListeners = LinkedList<CallPeerConferenceListener>()

    /**
     * The synchronization root/`Object` which protects the access to [.immutableCalls]
     * and [.mutableCalls].
     */
    private val callsSyncRoot = Any()
    /**
     * Determines whether the local peer/user associated with this instance and represented by the
     * `Call`s participating into it is acting as a conference focus.
     *
     * @return `true` if the local peer/user associated by this instance is acting as a
     * conference focus; otherwise, `false`
     */
    /**
     * Sets the indicator which determines whether the local peer represented by this instance and
     * the `Call`s participating in it is acting as a conference focus (and thus may, for
     * example, need to send the corresponding parameters in its outgoing signaling).
     *
     * conferenceFocus `true` if the local peer represented by this instance and the `Call`s
     * participating in it is to act as a conference focus; otherwise, `false`
     */
    /**
     * The indicator which determines whether the local peer represented by this instance and the
     * `Call`s participating in it is acting as a conference focus. The SIP protocol, for
     * example, will add the &quot;isfocus&quot; parameter to the Contact headers of its outgoing
     * signaling if `true`.
     */
    var isConferenceFocus = false
        set(conferenceFocus) {
            if (isConferenceFocus != conferenceFocus) {
                val oldValue = isConferenceFocus
                field = conferenceFocus
                val newValue = isConferenceFocus
                if (oldValue != newValue) conferenceFocusChanged(oldValue, newValue)
            }
        }

    /**
     * The list of `Call`s participating in this telephony conference as an immutable
     * `List` which can be exposed out of this instance without the need to make a copy. In
     * other words, it is an unmodifiable view of [.mutableCalls].
     */
    private var immutableCalls: List<Call<*>>
    /**
     * Determines whether the telephony conference represented by this instance is utilizing the
     * Jitsi Videobridge server-side telephony conferencing technology.
     *
     * @return `true` if the telephony conference represented by this instance is utilizing
     * the Jitsi Videobridge server-side telephony conferencing technology
     */

    /**
     * The list of `Call`s participating in this telephony conference as a mutable
     * `List` which should not be exposed out of this instance.
     */
    private var mutableCalls: List<Call<*>>
    /**
     * Initializes a new `CallConference` instance which is to optionally utilize the Jitsi
     * Videobridge server-side telephony conferencing technology.
     *
     * isJitsiVideobridge `true` if the telephony conference represented by the new instance is to
     * utilize the Jitsi Videobridge server-side telephony conferencing technology;
     * otherwise, `false`
     */
    /**
     * Initializes a new `CallConference` instance.
     */
    init {
        mutableCalls = ArrayList()
        immutableCalls = Collections.unmodifiableList(mutableCalls)
    }

    /**
     * Adds a specific `Call` to the list of `Call`s participating in this telephony
     * conference.
     *
     * call the `Call` to add to the list of `Call`s participating in this telephony
     * conference
     * @return `true` if the list of `Call`s participating in this telephony
     * conference changed as a result of the method call; otherwise, `false`
     * @throws NullPointerException if `call` is `null`
     */
    fun addCall(call: Call<*>?): Boolean {
        if (call == null) throw NullPointerException("call")
        synchronized(callsSyncRoot) {
            if (mutableCalls.contains(call)) return false

            /*
             * Implement the List of Calls participating in this telephony conference as a
             * copy-on-write storage in order to optimize the getCalls method which is likely to be
             * executed much more often than the addCall and removeCall methods.
             */
            val newMutableCalls = ArrayList(mutableCalls)
            if (newMutableCalls.add(call)) {
                mutableCalls = newMutableCalls
                immutableCalls = Collections.unmodifiableList(mutableCalls)
            } else return false
        }
        callAdded(call)
        return true
    }

    /**
     * Adds a `CallChangeListener` to the `Call`s participating in this telephony
     * conference. The method is a convenience that takes on the responsibility of tracking the
     * `Call`s that get added/removed to/from this telephony conference.
     *
     * listener the `CallChangeListner` to be added to the `Call`s participating in this
     * telephony conference
     * @throws NullPointerException if `listener` is `null`
     */
    fun addCallChangeListener(listener: CallChangeListener?) {
        if (listener == null) throw NullPointerException("listener") else {
            synchronized(callChangeListeners) { if (!callChangeListeners.contains(listener)) callChangeListeners.add(listener) }
        }
    }

    /**
     * Adds [.callPeerConferenceListener] to the `CallPeer`s associated with a specific
     * `Call`.
     *
     * call the `Call` to whose associated `CallPeer`s
     * `callPeerConferenceListener` is to be added
     */
    private fun addCallPeerConferenceListener(call: Call<*>) {
        val callPeerIter = call.getCallPeers()
        while (callPeerIter.hasNext()) {
            callPeerIter.next()!!.addCallPeerConferenceListener(callPeerConferenceListener)
        }
    }

    /**
     * Adds a `CallPeerConferenceListener` to the `CallPeer`s associated with the
     * `Call`s participating in this telephony conference. The method is a convenience that
     * takes on the responsibility of tracking the `Call`s that get added/removed to/from
     * this telephony conference and the `CallPeer` that get added/removed to/from these
     * `Call`s.
     *
     * listener the `CallPeerConferenceListener` to be added to the `CallPeer`s
     * associated with the `Call`s participating in this telephony conference
     * @throws NullPointerException if `listener` is `null`
     */
    fun addCallPeerConferenceListener(listener: CallPeerConferenceListener?) {
        if (listener == null) throw NullPointerException("listener") else {
            synchronized(callPeerConferenceListeners) { if (!callPeerConferenceListeners.contains(listener)) callPeerConferenceListeners.add(listener) }
        }
    }

    /**
     * Notifies this `CallConference` that a specific `Call` has been added to the
     * list of `Call`s participating in this telephony conference.
     *
     * call the `Call` which has been added to the list of `Call`s participating in
     * this telephony conference
     */
    private fun callAdded(call: Call<*>) {
        call.addCallChangeListener(callChangeListener)
        addCallPeerConferenceListener(call)

        /*
         * Update the conferenceFocus state. Because the public setConferenceFocus method allows
         * forcing a specific value on the state in question and because it does not sound right to
         * have the adding of a Call set conferenceFocus to false, only update it if the new
         * conferenceFocus value is true,
         */
        val conferenceFocus = isConferenceFocus(calls)
        if (conferenceFocus) isConferenceFocus = true
        firePropertyChange(CALLS, null, call)
    }

    /**
     * Notifies this `CallConference` that a specific `Call` has been removed from the
     * list of `Call`s participating in this telephony conference.
     *
     * call the `Call` which has been removed from the list of `Call`s participating
     * in this telephony conference
     */
    protected open fun callRemoved(call: Call<*>) {
        call.removeCallChangeListener(callChangeListener)
        removeCallPeerConferenceListener(call)

        /*
         * Update the conferenceFocus state. Following the line of thinking expressed in the
         * callAdded method, only update it if the new conferenceFocus value is false.
         */
        val conferenceFocus = isConferenceFocus(calls)
        if (!conferenceFocus) isConferenceFocus = false
        firePropertyChange(CALLS, call, null)
    }

    /**
     * Notifies this telephony conference that the `CallState` of a `Call` has
     * changed.
     *
     * ev a `CallChangeEvent` which specifies the `Call` which had its
     * `CallState` changed and the old and new `CallState`s of that
     * `Call`
     */
    private fun callStateChanged(ev: CallChangeEvent) {
        val call = ev.sourceCall
        if (containsCall(call)) {
            try {
                // Forward the CallChangeEvent to the callChangeListeners.
                for (l in getCallChangeListeners()) l.callStateChanged(ev)
            } finally {
                if (CallChangeEvent.CALL_STATE_CHANGE == ev.propertyName && CallState.CALL_ENDED == ev.newValue) {
                    /*
                     * Should not be vital because Call will remove itself. Anyway, do it for the
                     * sake of completeness.
                     */
                    removeCall(call)
                }
            }
        }
    }

    /**
     * Notifies this `CallConference` that the value of its `conferenceFocus` property
     * has changed from a specific old value to a specific new value.
     *
     * oldValue the value of the `conferenceFocus` property of this instance before the change
     * newValue the value of the `conferenceFocus` property of this instance after the change
     */
    protected open fun conferenceFocusChanged(oldValue: Boolean, newValue: Boolean) {
        firePropertyChange(Call.CONFERENCE_FOCUS, oldValue, newValue)
    }

    /**
     * Determines whether a specific `Call` is participating in this telephony conference.
     *
     * call the `Call` which is to be checked whether it is participating in this telephony
     * conference
     * @return `true` if the specified `call` is participating in this telephony
     * conference
     */
    fun containsCall(call: Call<*>): Boolean {
        synchronized(callsSyncRoot) { return mutableCalls.contains(call) }
    }

    /**
     * Gets the list of `CallChangeListener`s added to the `Call`s participating in
     * this telephony conference via [.addCallChangeListener].
     *
     * @return the list of `CallChangeListener`s added to the `Call`s participating in
     * this telephony conference via [.addCallChangeListener]
     */
    private fun getCallChangeListeners(): Array<CallChangeListener> {
        synchronized(callChangeListeners) { return callChangeListeners.toTypedArray() }
    }

    /**
     * Gets the number of `Call`s that are participating in this telephony conference.
     *
     * @return the number of `Call`s that are participating in this telephony conference
     */
    val callCount: Int
        get() {
            synchronized(callsSyncRoot) { return mutableCalls.size }
        }

    /**
     * Gets the list of `CallPeerConferenceListener`s added to the `CallPeer`s
     * associated with the `Call`s participating in this telephony conference via
     * [.addCallPeerConferenceListener].
     *
     * @return the list of `CallPeerConferenceListener`s added to the `CallPeer`s
     * associated with the `Call`s participating in this telephony conference via
     * [.addCallPeerConferenceListener]
     */
    private fun getCallPeerConferenceListeners(): Array<CallPeerConferenceListener> {
        synchronized(callPeerConferenceListeners) { return callPeerConferenceListeners.toTypedArray() }
    }

    /**
     * Gets the number of `CallPeer`s associated with the `Call`s participating in
     * this telephony conference.
     *
     * @return the number of `CallPeer`s associated with the `Call`s participating in
     * this telephony conference
     */
    val callPeerCount: Int
        get() {
            var callPeerCount = 0
            for (call in calls) callPeerCount += call.callPeerCount
            return callPeerCount
        }

    /**
     * Gets a list of the `CallPeer`s associated with the `Call`s participating in
     * this telephony conference.
     *
     * @return a list of the `CallPeer`s associated with the `Call`s participating in
     * this telephony conference
     */
    val callPeers: List<CallPeer?>
        get() {
            val callPeers = ArrayList<CallPeer>()
            getCallPeers(callPeers)
            return callPeers
        }

    /**
     * Adds the `CallPeer`s associated with the `Call`s participating in this
     * telephony conference into a specific `List`.
     *
     * callPeers a `List` into which the `CallPeer`s associated with the `Call`s
     * participating in this telephony conference are to be added
     */
    fun getCallPeers(callPeers: MutableList<CallPeer>) {
        for (call in calls) {
            val callPeerIt = call.getCallPeers()
            while (callPeerIt.hasNext()) callPeers.add(callPeerIt.next()!!)
        }
    }

    /**
     * Gets the list of `Call` participating in this telephony conference.
     *
     * @return the list of `Call`s participating in this telephony conference. An empty array
     * of `Call` element type is returned if there are no `Call`s in this
     * telephony conference-related state.
     */
    val calls: List<Call<*>>
        get() {
            synchronized(callsSyncRoot) { return immutableCalls }
        }

    /**
     * Determines whether the current state of this instance suggests that the telephony conference
     * it represents has ended. Iterates over the `Call`s participating in this telephony
     * conference and looks for a `Call` which is not in the [CallState.CALL_ENDED]
     * state.
     *
     * @return `true` if the current state of this instance suggests that the telephony
     * conference it represents has ended; otherwise, `false`
     */
    val isEnded: Boolean
        get() {
            for (call in calls) {
                if (CallState.CALL_ENDED != call.getCallState()) return false
            }
            return true
        }

    /**
     * Notifies this telephony conference that a `CallPeerConferenceEvent` was fired by a
     * `CallPeer` associated with a `Call` participating in this telephony conference.
     * Forwards the specified `CallPeerConferenceEvent` to
     * [.callPeerConferenceListeners].
     *
     * ev the `CallPeerConferenceEvent` which was fired
     */
    private fun onCallPeerConferenceEvent(ev: CallPeerConferenceEvent) {
        val eventID = ev.getEventID()
        for (l in getCallPeerConferenceListeners()) {
            when (eventID) {
                CallPeerConferenceEvent.CONFERENCE_FOCUS_CHANGED -> l.conferenceFocusChanged(ev)
                CallPeerConferenceEvent.CONFERENCE_MEMBER_ADDED -> l.conferenceMemberAdded(ev)
                CallPeerConferenceEvent.CONFERENCE_MEMBER_REMOVED -> l.conferenceMemberRemoved(ev)
                CallPeerConferenceEvent.CONFERENCE_MEMBER_ERROR_RECEIVED -> l.conferenceMemberErrorReceived(ev)
                else -> throw UnsupportedOperationException(
                        "Unsupported CallPeerConferenceEvent eventID.")
            }
        }
    }

    /**
     * Notifies this telephony conference about a specific `CallPeerEvent` i.e. that a
     * `CallPeer` was either added to or removed from a `Call`.
     *
     * ev a `CallPeerEvent` which specifies the `CallPeer` which was added or
     * removed and the `Call` to which it was added or from which is was removed
     */
    private fun onCallPeerEvent(ev: CallPeerEvent) {
        val call = ev.getSourceCall()
        if (containsCall(call)) {
            /*
             * Update the conferenceFocus state. Following the line of thinking expressed in the
             * callAdded and callRemoved methods, only update it if the new conferenceFocus value is
             * in accord with the expectations.
             */
            val eventID = ev.getEventID()
            val conferenceFocus = isConferenceFocus(calls)
            when (eventID) {
                CallPeerEvent.CALL_PEER_ADDED -> if (conferenceFocus) isConferenceFocus = true
                CallPeerEvent.CALL_PEER_REMOVED -> if (!conferenceFocus) isConferenceFocus = false
                else -> {}
            }
            try {
                // Forward the CallPeerEvent to the callChangeListeners.
                for (l in getCallChangeListeners()) {
                    when (eventID) {
                        CallPeerEvent.CALL_PEER_ADDED -> l.callPeerAdded(ev)
                        CallPeerEvent.CALL_PEER_REMOVED -> l.callPeerRemoved(ev)
                        else -> {}
                    }
                }
            } finally {
                /*
                 * Add/remove the callPeerConferenceListener to/from the source CallPeer (for the
                 * purposes of the addCallPeerConferenceListener method of this CallConference).
                 */
                val callPeer = ev.getSourceCallPeer()
                when (eventID) {
                    CallPeerEvent.CALL_PEER_ADDED -> callPeer.addCallPeerConferenceListener(callPeerConferenceListener)
                    CallPeerEvent.CALL_PEER_REMOVED -> callPeer.removeCallPeerConferenceListener(callPeerConferenceListener)
                    else -> {}
                }
            }
        }
    }

    /**
     * Removes a specific `Call` from the list of `Call`s participating in this
     * telephony conference.
     *
     * call the `Call` to remove from the list of `Call`s participating in this
     * telephony conference
     * @return `true` if the list of `Call`s participating in this telephony
     * conference changed as a result of the method call; otherwise, `false`
     */
    fun removeCall(call: Call<*>?): Boolean {
        if (call == null) return false
        synchronized(callsSyncRoot) {
            if (!mutableCalls.contains(call)) return false

            /*
             * Implement the List of Calls participating in this telephony conference as a
             * copy-on-write storage in order to optimize the getCalls method which is likely to be
             * executed much more often than the addCall and removeCall methods.
             */
            val newMutableCalls = ArrayList(mutableCalls)
            if (newMutableCalls.remove(call)) {
                mutableCalls = newMutableCalls
                immutableCalls = Collections.unmodifiableList(mutableCalls)
            } else return false
        }
        callRemoved(call)
        return true
    }

    /**
     * Removes a `CallChangeListener` from the `Call`s participating in this telephony
     * conference.
     *
     * listener the `CallChangeListener` to be removed from the `Call`s participating in
     * this telephony conference
     * @see .addCallChangeListener
     */
    fun removeCallChangeListener(listener: CallChangeListener?) {
        if (listener != null) {
            synchronized(callChangeListeners) { callChangeListeners.remove(listener) }
        }
    }

    /**
     * Removes [.callPeerConferenceListener] from the `CallPeer`s associated with a
     * specific `Call`.
     *
     * call the `Call` from whose associated `CallPeer`s
     * `callPeerConferenceListener` is to be removed
     */
    private fun removeCallPeerConferenceListener(call: Call<*>) {
        val callPeerIter = call.getCallPeers()
        while (callPeerIter.hasNext()) {
            callPeerIter.next()!!.removeCallPeerConferenceListener(callPeerConferenceListener)
        }
    }

    /**
     * Removes a `CallPeerConferenceListener` from the `CallPeer`s associated with the
     * `Call`s participating in this telephony conference.
     *
     * listener the `CallPeerConferenceListener` to be removed from the `CallPeer`s
     * associated with the `Call`s participating in this telephony conference
     * @see .addCallPeerConferenceListener
     */
    fun removeCallPeerConferenceListener(listener: CallPeerConferenceListener?) {
        if (listener != null) {
            synchronized(callPeerConferenceListeners) { callPeerConferenceListeners.remove(listener) }
        }
    }

    companion object {
        /**
         * The name of the `CallConference` property which specifies the list of `Call`s
         * participating in a telephony conference. A change in the value of the property is delivered
         * in the form of a `PropertyChangeEvent` which has its `oldValue` or
         * `newValue` set to the `Call` which has been removed or added to the list of
         * `Call`s participating in the telephony conference.
         */
        const val CALLS = "calls"

        /**
         * Gets the number of `CallPeer`s associated with the `Call`s participating in the
         * telephony conference-related state of a specific `Call`.
         *
         * call the `Call` for which the number of `CallPeer`s associated with the
         * `Call`s participating in its associated telephony conference-related state
         * @return the number of `CallPeer`s associated with the `Call`s participating in
         * the telephony conference-related state of the specified `Call`
         */
        fun getCallPeerCount(call: Call<*>): Int {
            val conference = call.getConference()

            /*
         * A Call instance is supposed to always maintain a CallConference instance. Anyway, if it
         * turns out that it is not the case, we will consider the Call as a representation of a
         * telephony conference.
         */
            return conference.callPeerCount ?: call.callPeerCount
        }

        /**
         * Gets a list of the `CallPeer`s associated with the `Call`s participating in the
         * telephony conference in which a specific `Call` is participating.
         *
         * call the `Call` which specifies the telephony conference the `CallPeer`s of
         * which are to be retrieved
         * @return a list of the `CallPeer`s associated with the `Call`s participating in
         * the telephony conference in which the specified `call` is participating
         */
        fun getCallPeers(call: Call<*>): List<CallPeer?> {
            val conference = call.getConference()
            val callPeers = ArrayList<CallPeer>()
            if (conference == null) {
                val callPeerIt = call.getCallPeers()
                while (callPeerIt.hasNext()) callPeers.add(callPeerIt.next())
            } else conference.getCallPeers(callPeers)
            return callPeers
        }

        /**
         * Gets the list of `Call`s participating in the telephony conference in which a specific
         * `Call` is participating.
         *
         * call the `Call` which participates in the telephony conference the list of
         * participating `Call`s of which is to be returned
         * @return the list of `Call`s participating in the telephony conference in which the
         * specified `call` is participating
         */
        fun getCalls(call: Call<*>): List<Call<*>> {
            val conference = call.getConference()
            return conference.calls ?: emptyList()
        }

        /**
         * Determines whether a `CallConference` is to report the local peer/user as a conference
         * focus judging by a specific list of `Call`s.
         *
         * calls the list of `Call` which are to be judged whether the local peer/user that they
         * represent is to be considered as a conference focus
         * @return `true` if the local peer/user represented by the specified `calls` is
         * judged to be a conference focus; otherwise, `false`
         */
        private fun isConferenceFocus(calls: List<Call<*>>): Boolean {
            val callCount = calls.size
            return if (callCount < 1) false else if (callCount > 1) true else calls[0].callPeerCount > 1
        }
    }
}