/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent
import net.java.sip.communicator.service.protocol.event.CallPeerConferenceEvent
import net.java.sip.communicator.service.protocol.event.CallPeerConferenceListener
import net.java.sip.communicator.service.protocol.event.CallPeerListener
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityListener
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityMessageEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityNegotiationStartedEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOffEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOnEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityStatusEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityTimeoutEvent
import org.atalk.util.event.PropertyChangeNotifier
import timber.log.Timber
import java.net.URL
import java.util.*

/**
 * Provides a default implementation for most of the `CallPeer` methods with the purpose of
 * only leaving custom protocol development to clients using the PhoneUI service.
 *
 * @param <T> the call extension class like for example `CallSipImpl` or `CallJabberImpl`
 * @param <U> the provider extension class like for example `ProtocolProviderServiceSipImpl` or
 * `ProtocolProviderServiceJabberImpl`
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
</U></T> */
abstract class AbstractCallPeer<T : Call<*>, U : ProtocolProviderService> protected constructor() : PropertyChangeNotifier(), CallPeer {
    /**
     * The time this call started at.
     */
    private var callDurationStartTime = CallPeer.CALL_DURATION_START_TIME_UNKNOWN

    /**
     * The list of `CallPeerConferenceListener`s interested in and to be notified about
     * changes in conference-related information such as this peer acting or not acting as a
     * conference focus and conference membership details.
     */
    private val callPeerConferenceListeners = ArrayList<CallPeerConferenceListener>()

    /**
     * All the CallPeer listeners registered with this CallPeer.
     */
    private val callPeerListeners = ArrayList<CallPeerListener>()

    /**
     * All the CallPeerSecurityListener-s registered with this CallPeer.
     */
    private val callPeerSecurityListeners = ArrayList<CallPeerSecurityListener>()

    /**
     * The indicator which determines whether this peer is acting as a conference focus and thus may
     * provide information about `ConferenceMember` such as [.getConferenceMembers]
     * and [.getConferenceMemberCount].
     */
    private var conferenceFocus = false

    /**
     * The list of `ConferenceMember`s currently known to and managed in a conference by this
     * `CallPeer`. It is implemented as a copy-on-write storage in order to optimize the
     * implementation of [.getConferenceMembers] which is used more often than
     * [.addConferenceMember] and
     * [.removeConferenceMember].
     */
    private var conferenceMembers: List<ConferenceMember>

    /**
     * The `Object` which synchronizes the access to [.conferenceMembers] and
     * [.unmodifiableConferenceMembers].
     */
    private val conferenceMembersSyncRoot = Any()

    /**
     * The flag that determines whether our audio stream to this call peer is currently muted.
     */
    private var isMute = false

    /**
     * The last fired security event.
     */
    private var lastSecurityEvent: CallPeerSecurityStatusEvent? = null

    /**
     * The state of the call peer.
     */
    private var state = CallPeerState.UNKNOWN

    /**
     *
     */
    private var alternativeIMPPAddress: String? = null

    /**
     * An unmodifiable view of [.conferenceMembers]. The list of `ConferenceMember`s
     * participating in the conference managed by this instance is implemented as a copy-on-write
     * storage in order to optimize the implementation of [.getConferenceMembers] which is
     * used more often than [.addConferenceMember] and
     * [.removeConferenceMember].
     */
    private var unmodifiableConferenceMembers: List<ConferenceMember>

    /**
     * Initializes a new `AbstractCallPeer` instance.
     */
    init {
        conferenceMembers = emptyList()
        unmodifiableConferenceMembers = Collections.unmodifiableList(conferenceMembers)
    }

    /**
     * Returns an alternative IMPP address corresponding to this `CallPeer`.
     *
     * @return a string representing an alternative IMPP address corresponding to this `CallPeer`
     */
    override fun getAlternativeIMPPAddress(): String? {
        return alternativeIMPPAddress
    }

    /**
     * Returns an alternative IMPP address corresponding to this `CallPeer`.
     *
     * @param address an alternative IMPP address corresponding to this `CallPeer`
     */
    fun setAlternativeIMPPAddress(address: String?) {
        alternativeIMPPAddress = address
    }

    /**
     * Implements `CallPeer#addCallPeerConferenceListener(
     * CallPeerConferenceListener)`. In the fashion of the addition of the other listeners, does
     * not throw an exception on attempting to add a `null` listeners and just ignores the call.
     *
     * @param listener the `CallPeerConferenceListener` to add
     */
    override fun addCallPeerConferenceListener(listener: CallPeerConferenceListener?) {
        if (listener != null) synchronized(callPeerConferenceListeners) { if (!callPeerConferenceListeners.contains(listener)) callPeerConferenceListeners.add(listener) }
    }

    /**
     * Registers the `listener` to the list of listeners that would be receiving CallPeerEvents.
     *
     * @param listener a listener instance to register with this peer.
     */
    override fun addCallPeerListener(listener: CallPeerListener?) {
        if (listener == null) return
        synchronized(callPeerListeners) { if (!callPeerListeners.contains(listener)) callPeerListeners.add(listener) }
    }

    /**
     * Registers the `listener` to the list of listeners that would be receiving CallPeerSecurityEvents.
     *
     * @param listener a listener instance to register with this peer.
     */
    override fun addCallPeerSecurityListener(listener: CallPeerSecurityListener?) {
        if (listener == null) return
        synchronized(callPeerSecurityListeners) { if (!callPeerSecurityListeners.contains(listener)) callPeerSecurityListeners.add(listener) }
    }

    /**
     * Adds a specific `ConferenceMember` to the list of `ConferenceMember`s reported
     * by this peer through [.getConferenceMembers] and [.getConferenceMemberCount]
     * and fires `CallPeerConferenceEvent#CONFERENCE_MEMBER_ADDED` to the currently
     * registered `CallPeerConferenceListener`s.
     *
     * @param conferenceMember a `ConferenceMember` to be added to the list of `ConferenceMember`
     * reported by this peer. If the specified `ConferenceMember` is already contained
     * in the list, it is not added again and no event is fired.
     */
    fun addConferenceMember(conferenceMember: ConferenceMember?) {
        if (conferenceMember == null) throw NullPointerException("conferenceMember") else {
            synchronized(conferenceMembersSyncRoot) {
                if (conferenceMembers.contains(conferenceMember)) return else {
                    val newConferenceMembers = ArrayList(conferenceMembers)
                    if (newConferenceMembers.add(conferenceMember)) {
                        conferenceMembers = newConferenceMembers
                        unmodifiableConferenceMembers = Collections.unmodifiableList(conferenceMembers)
                    } else return
                }
            }
            fireCallPeerConferenceEvent(CallPeerConferenceEvent(this,
                    CallPeerConferenceEvent.CONFERENCE_MEMBER_ADDED, conferenceMember))
        }
    }

    /**
     * Fires `CallPeerConferenceEvent#CONFERENCE_MEMBER_ERROR_RECEIVED` to the currently
     * registered `CallPeerConferenceListener`s.
     *
     * @param errorMessage error message that can be displayed.
     */
    fun fireConferenceMemberErrorEvent(errorMessage: String?) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            Timber.w("The error message for %  is null or empty string.", getDisplayName())
            return
        }
        fireCallPeerConferenceEvent(CallPeerConferenceEvent(this,
                CallPeerConferenceEvent.CONFERENCE_MEMBER_ERROR_RECEIVED, null, errorMessage))
    }

    /**
     * Finds the first `ConferenceMember` whose `audioSsrc` is equals to a specific
     * value. The method is meant for very frequent use so it iterates over the `List` of
     * `ConferenceMember`s without creating an `Iterator`.
     *
     * @param ssrc the SSRC identifier of the audio RTP streams transmitted by the
     * `ConferenceMember` that we are looking for.
     * @return the first `ConferenceMember` whose `audioSsrc` is equal to
     * `ssrc` or `null` if no such `ConferenceMember` was found
     */
    protected fun findConferenceMember(ssrc: Long): ConferenceMember? {
        val members = getConferenceMembers()
        var i = 0
        val memberCount = members.size
        while (i < memberCount) {
            val member = members[i]
            if (member.getAudioSsrc() == ssrc) return member
            i++
        }
        return null
    }
    /**
     * Constructs a `CallPeerChangeEvent` using this call peer as source, setting it to be of
     * type `eventType` and the corresponding `oldValue` and `newValue`.
     *
     * eventType the type of the event to create and dispatch.
     * oldValue the value of the source property before it changed.
     * newValue the current value of the source property.
     * reason a string that could be set to contain a human readable explanation for the transition
     * (particularly handy when moving into a FAILED state).
     * reasonCode the reason code for the reason of this event.
     */
    /**
     * Constructs a `CallPeerChangeEvent` using this call peer as source, setting it to be of
     * type `eventType` and the corresponding `oldValue` and `newValue`.
     *
     * eventType the type of the event to create and dispatch.
     * oldValue the value of the source property before it changed.
     * newValue the current value of the source property.
     * reason a string that could be set to contain a human readable explanation for the transition
     * (particularly handy when moving into a FAILED state).
     */
    /**
     * Constructs a `CallPeerChangeEvent` using this call peer as source, setting it to be of
     * type `eventType` and the corresponding `oldValue` and `newValue`,
     *
     * @param eventType the type of the event to create and dispatch.
     * @param oldValue the value of the source property before it changed.
     * @param newValue the current value of the source property.
     */
    protected fun fireCallPeerChangeEvent(eventType: String?, oldValue: Any?, newValue: Any?, reason: String? = null, reasonCode: Int = -1) {
        val evt = CallPeerChangeEvent(this, eventType, oldValue, newValue, reason, reasonCode)
        var listeners: Iterator<CallPeerListener>
        synchronized(callPeerListeners) { listeners = ArrayList(callPeerListeners).iterator() }
        Timber.d("Dispatching CallPeerChangeEvent (%s): %s; Events: %s", callPeerListeners.size, callPeerListeners, evt)
        while (listeners.hasNext()) {
            val listener = listeners.next()
            // catch any possible errors, so we are sure we dispatch events to all listeners
            try {
                when (eventType) {
                    CallPeerChangeEvent.CALL_PEER_ADDRESS_CHANGE -> listener.peerAddressChanged(evt)
                    CallPeerChangeEvent.CALL_PEER_DISPLAY_NAME_CHANGE -> listener.peerDisplayNameChanged(evt)
                    CallPeerChangeEvent.CALL_PEER_IMAGE_CHANGE -> listener.peerImageChanged(evt)
                    CallPeerChangeEvent.CALL_PEER_STATE_CHANGE ->                         // Timber.d("Dispatching CallPeerChangeEvent CALL_PEER_STATE_CHANGE to: %s", listener);
                        listener.peerStateChanged(evt)
                }
            } catch (t: Throwable) {
                Timber.e(t, "Error dispatching event to %s: %s", listener, eventType)
            }
        }
    }

    /**
     * Fires a specific `CallPeerConferenceEvent` to the `CallPeerConferenceListener`s
     * interested in changes in the conference-related information provided by this peer.
     *
     * @param conferenceEvent a `CallPeerConferenceEvent` to be fired and carrying the event data
     */
    private fun fireCallPeerConferenceEvent(conferenceEvent: CallPeerConferenceEvent) {
        var listeners: Array<CallPeerConferenceListener>
        synchronized(callPeerConferenceListeners) { listeners = callPeerConferenceListeners.toTypedArray() }
        val eventID = conferenceEvent.getEventID()
        val eventIDString = when (eventID) {
            CallPeerConferenceEvent.CONFERENCE_FOCUS_CHANGED -> "CONFERENCE_FOCUS_CHANGED"
            CallPeerConferenceEvent.CONFERENCE_MEMBER_ADDED -> "CONFERENCE_MEMBER_ADDED"
            CallPeerConferenceEvent.CONFERENCE_MEMBER_REMOVED -> "CONFERENCE_MEMBER_REMOVED"
            CallPeerConferenceEvent.CONFERENCE_MEMBER_ERROR_RECEIVED -> "CONFERENCE_MEMBER_ERROR_RECEIVED"
            else -> "UNKNOWN"
        }
        Timber.d("Dispatching CallPeerConferenceEvent with ID %s to %s listeners", eventIDString, listeners.size)
        for (listener in listeners) when (eventID) {
            CallPeerConferenceEvent.CONFERENCE_FOCUS_CHANGED -> listener.conferenceFocusChanged(conferenceEvent)
            CallPeerConferenceEvent.CONFERENCE_MEMBER_ADDED -> listener.conferenceMemberAdded(conferenceEvent)
            CallPeerConferenceEvent.CONFERENCE_MEMBER_REMOVED -> listener.conferenceMemberRemoved(conferenceEvent)
            CallPeerConferenceEvent.CONFERENCE_MEMBER_ERROR_RECEIVED -> listener.conferenceMemberErrorReceived(conferenceEvent)
        }
    }

    /**
     * Constructs a `CallPeerSecurityStatusEvent` using this call peer as source, setting it
     * to be of type `eventType` and the corresponding `oldValue` and `newValue`.
     *
     * @param messageType the type of the message
     * @param i18nMessage message
     * @param severity severity level
     */
    protected fun fireCallPeerSecurityMessageEvent(messageType: String, i18nMessage: String, severity: Int) {
        val evt = CallPeerSecurityMessageEvent(this, messageType, i18nMessage, severity)
        Timber.d("Dispatching CallPeer Security Message Events to %s listeners:\n%s",
                callPeerSecurityListeners.size, evt.toString())
        var listeners: Iterator<CallPeerSecurityListener>
        synchronized(callPeerSecurityListeners) { listeners = ArrayList<CallPeerSecurityListener>(callPeerSecurityListeners).iterator() }
        while (listeners.hasNext()) {
            val listener = listeners.next()
            listener.securityMessageReceived(evt)
        }
    }

    /**
     * Constructs a `CallPeerSecurityStatusEvent` using this call peer as source, setting it
     * to be of type `eventType` and the corresponding `oldValue` and `newValue`.
     *
     * @param evt the event object with details to pass on to the consumers
     */
    protected fun fireCallPeerSecurityNegotiationStartedEvent(evt: CallPeerSecurityNegotiationStartedEvent) {
        lastSecurityEvent = evt
        Timber.d("Dispatching CallPeerSecurityStatusEvent Started to %s listeners: %s",
                callPeerSecurityListeners.size, evt.toString())
        var listeners: List<CallPeerSecurityListener?>
        synchronized(callPeerSecurityListeners) { listeners = ArrayList<CallPeerSecurityListener>(callPeerSecurityListeners) }
        for (listener in listeners) {
            listener!!.securityNegotiationStarted(evt)
        }
    }

    /**
     * Constructs a `CallPeerSecurityStatusEvent` using this call peer as source, setting it
     * to be of type `eventType` and the corresponding `oldValue` and `newValue`.
     *
     * @param evt the event object with details to pass on to the consumers
     */
    protected fun fireCallPeerSecurityOffEvent(evt: CallPeerSecurityOffEvent) {
        lastSecurityEvent = evt
        Timber.d("Dispatching CallPeerSecurityAuthenticationEvent OFF to %s listeners: %s",
                callPeerSecurityListeners.size, evt.toString())
        var listeners: List<CallPeerSecurityListener?>
        synchronized(callPeerSecurityListeners) { listeners = ArrayList<CallPeerSecurityListener>(callPeerSecurityListeners) }
        for (listener in listeners) {
            listener!!.securityOff(evt)
        }
    }

    /**
     * Constructs a `CallPeerSecurityStatusEvent` using this call peer as source, setting it
     * to be of type `eventType` and the corresponding `oldValue` and `newValue`.
     *
     * @param evt the event object with details to pass on to the consumers
     */
    protected fun fireCallPeerSecurityOnEvent(evt: CallPeerSecurityOnEvent) {
        lastSecurityEvent = evt
        Timber.d("Dispatching CallPeerSecurityStatusEvent ON to %s listeners: %s",
                callPeerSecurityListeners.size, evt.toString())
        var listeners: List<CallPeerSecurityListener?>
        synchronized(callPeerSecurityListeners) { listeners = ArrayList<CallPeerSecurityListener>(callPeerSecurityListeners) }
        for (listener in listeners) {
            listener!!.securityOn(evt)
        }
    }

    /**
     * Constructs a `CallPeerSecurityStatusEvent` using this call peer as source, setting it
     * to be of type `eventType` and the corresponding `oldValue` and
     * `newValue`.
     *
     * @param evt the event object with details to pass on to the consumers
     */
    protected fun fireCallPeerSecurityTimeoutEvent(evt: CallPeerSecurityTimeoutEvent) {
        lastSecurityEvent = evt
        Timber.d("Dispatching CallPeerSecurityStatusEvent Timeout to %s listeners: %s",
                callPeerSecurityListeners.size, evt.toString())
        var listeners: List<CallPeerSecurityListener?>
        synchronized(callPeerSecurityListeners) { listeners = ArrayList<CallPeerSecurityListener>(callPeerSecurityListeners) }
        for (listener in listeners) {
            listener!!.securityTimeout(evt)
        }
    }

    /**
     * Returns a reference to the call that this peer belongs to.
     *
     * @return a reference to the call containing this peer.
     */
    abstract override fun getCall(): T?

    /**
     * Gets the time at which this `CallPeer` transitioned into a state (likely
     * [CallPeerState.CONNECTED]) marking the start of the duration of the participation in a `Call`.
     *
     * @return the time at which this `CallPeer` transitioned into a state marking the start
     * of the duration of the participation in a `Call` or
     * [CallPeer.CALL_DURATION_START_TIME_UNKNOWN] if such a transition has not been performed
     */
    override fun getCallDurationStartTime(): Long {
        return callDurationStartTime
    }

    /**
     * Returns a URL pointing ta a location with call control information for this peer or
     * `null` if no such URL is available for this call peer.
     *
     * @return a URL link to a location with call information or a call control web interface
     * related to this peer or `null` if no such URL is available.
     */
    override fun getCallInfoURL(): URL? {
        // if signaling protocols (such as SIP) know where to get this URL from they should override this method
        return null
    }

    /**
     * {@inheritDoc}
     */
    override fun getConferenceMemberCount(): Int {
        synchronized(conferenceMembersSyncRoot) { return if (isConferenceFocus()) getConferenceMembers().size else 0 }
    }

    /**
     * {@inheritDoc}
     */
    override fun getConferenceMembers(): List<ConferenceMember> {
        synchronized(conferenceMembersSyncRoot) { return unmodifiableConferenceMembers }
    }

    /**
     * Returns the currently used security settings of this `CallPeer`.
     *
     * @return the `CallPeerSecurityStatusEvent` that contains the current security settings.
     */
    override fun getCurrentSecuritySettings(): CallPeerSecurityStatusEvent? {
        return lastSecurityEvent
    }

    /**
     * Returns the protocol provider that this peer belongs to.
     *
     * @return a reference to the ProtocolProviderService that this peer belongs to.
     */
    abstract override fun getProtocolProvider(): U

    /**
     * Returns an object representing the current state of that peer.
     *
     * @return a CallPeerState instance representing the peer's state.
     */
    override fun getState(): CallPeerState {
        return state
    }

    /**
     * Determines whether this call peer is currently a conference focus.
     *
     * @return `true` if this peer is a conference focus and `false` otherwise.
     */
    override fun isConferenceFocus(): Boolean {
        return conferenceFocus
    }

    /**
     * Determines whether the audio stream (if any) being sent to this peer is mute.
     *
     *
     * The default implementation returns `false`.
     *
     *
     * @return `true` if an audio stream is being sent to this peer and it is currently mute;
     * `false`, otherwise
     */
    override fun isMute(): Boolean {
        return isMute
    }

    /**
     * Implements `CallPeer#removeCallPeerConferenceListener(CallPeerConferenceListener)`.
     *
     * @param listener the `CallPeerConferenceListener` to remove
     */
    override fun removeCallPeerConferenceListener(listener: CallPeerConferenceListener?) {
        if (listener != null) synchronized(callPeerConferenceListeners) { callPeerConferenceListeners.remove(listener) }
    }

    /**
     * Unregisters the specified listener.
     *
     * @param listener the listener to unregister.
     */
    override fun removeCallPeerListener(listener: CallPeerListener?) {
        if (listener == null) return
        synchronized(callPeerListeners) { callPeerListeners.remove(listener) }
    }

    /**
     * Unregisters the specified listener.
     *
     * @param listener the listener to unregister.
     */
    override fun removeCallPeerSecurityListener(listener: CallPeerSecurityListener?) {
        if (listener == null) return
        synchronized(callPeerSecurityListeners) { callPeerSecurityListeners.remove(listener) }
    }

    /**
     * Removes a specific `ConferenceMember` from the list of `ConferenceMember`s
     * reported by this peer through [.getConferenceMembers] and
     * [.getConferenceMemberCount] if it is contained and fires
     * `CallPeerConferenceEvent#CONFERENCE_MEMBER_REMOVED` to the currently registered
     * `CallPeerConferenceListener`s.
     *
     * @param conferenceMember a `ConferenceMember` to be removed from the list of `ConferenceMember`
     * reported by this peer. If the specified `ConferenceMember` is no contained in the list, no event is fired.
     */
    open fun removeConferenceMember(conferenceMember: ConferenceMember?) {
        if (conferenceMember != null) {
            synchronized(conferenceMembersSyncRoot) {
                if (conferenceMembers.contains(conferenceMember)) {
                    val newConferenceMembers: MutableList<ConferenceMember> = ArrayList(conferenceMembers)
                    if (newConferenceMembers.remove(conferenceMember)) {
                        conferenceMembers = newConferenceMembers
                        unmodifiableConferenceMembers = Collections.unmodifiableList(conferenceMembers)
                    } else return
                } else return
            }
            fireCallPeerConferenceEvent(CallPeerConferenceEvent(this,
                    CallPeerConferenceEvent.CONFERENCE_MEMBER_REMOVED, conferenceMember))
        }
    }

    /**
     * Specifies whether this peer is a conference focus.
     *
     * @param conferenceFocus `true` if this peer is to become a conference focus and `false` otherwise.
     */
    fun setConferenceFocus(conferenceFocus: Boolean) {
        if (this.conferenceFocus != conferenceFocus) {
            this.conferenceFocus = conferenceFocus
            fireCallPeerConferenceEvent(CallPeerConferenceEvent(this,
                    CallPeerConferenceEvent.CONFERENCE_FOCUS_CHANGED))
        }
    }

    /**
     * Sets the mute property for this call peer.
     *
     * @param newMuteValue the new value of the mute property for this call peer
     */
    open fun setMute(newMuteValue: Boolean) {
        isMute = newMuteValue
        firePropertyChange(CallPeer.MUTE_PROPERTY_NAME, isMute, newMuteValue)
    }

    /**
     * Causes this CallPeer to enter the specified state. The method also sets the
     * currentStateStartDate field and fires a CallPeerChangeEvent.
     *
     * @param newState the state this call peer should enter.
     */
    fun setState(newState: CallPeerState) {
        setState(newState, null)
    }

    /**
     * Causes this CallPeer to enter the specified state. The method also sets the
     * currentStateStartDate field and fires a CallPeerChangeEvent.
     *
     * @param newState the state this call peer should enter.
     * @param reason a string that could be set to contain a human readable explanation for the transition
     * (particularly handy when moving into a FAILED state).
     */
    fun setState(newState: CallPeerState, reason: String?) {
        setState(newState, reason, -1)
    }

    /**
     * Causes this CallPeer to enter the specified state. The method also sets the
     * currentStateStartDate field and fires a CallPeerChangeEvent.
     *
     * @param newState the state this call peer should enter.
     * @param reason a string that could be set to contain a human readable explanation for the transition
     * (particularly handy when moving into a FAILED state).
     * @param reasonCode the code for the reason of the state change.
     */
    open fun setState(newState: CallPeerState, reason: String?, reasonCode: Int) {
        val oldState = getState()
        if (oldState === newState) return
        state = newState
        if (CallPeerState.CONNECTED == newState && !CallPeerState.isOnHold(oldState)) {
            callDurationStartTime = System.currentTimeMillis()
        }
        fireCallPeerChangeEvent(CallPeerChangeEvent.CALL_PEER_STATE_CHANGE, oldState, newState, reason, reasonCode)
    }

    /**
     * Returns a string representation of the peer in the form of <br></br>
     * Display Name &lt;address&gt;;status=CallPeerStatus
     *
     * @return a string representation of the peer and its state.
     */
    override fun toString(): String {
        return getDisplayName() + " <" + getAddress() + ">; status=" + getState().getStateString()
    }

    companion object {
        /**
         * The constant which describes an empty set of `ConferenceMember`s (and which can be
         * used to reduce allocations).
         */
        val NO_CONFERENCE_MEMBERS = arrayOfNulls<ConferenceMember>(0)
    }
}