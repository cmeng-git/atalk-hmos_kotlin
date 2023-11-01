/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import android.text.TextUtils
import net.java.sip.communicator.service.protocol.event.CallChangeEvent
import net.java.sip.communicator.service.protocol.event.CallChangeListener
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent
import net.java.sip.communicator.service.protocol.event.CallPeerEvent
import net.java.sip.communicator.service.protocol.event.SoundLevelListener
import net.java.sip.communicator.util.DataObject
import org.jivesoftware.smackx.jingle.JingleManager
import timber.log.Timber
import java.beans.PropertyChangeListener
import java.util.*

/**
 * A representation of a call. `Call` instances must only be created by users (i.e. telephony
 * protocols) of the PhoneUIService such as a SIP protocol implementation. Extensions of this class
 * might have names like `CallSipImpl`, `CallJabberImpl`, or
 * `CallAnyOtherTelephonyProtocolImpl`. Call is DataObject, this way it will be able to store
 * custom data and carry it to various parts of the project.
 *
 * @author Emil Ivov
 * @author Emanuel Onica
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
abstract class Call<T : CallPeer> protected constructor(
        /**
         * A reference to the ProtocolProviderService instance that created us.
         */
        val pps: ProtocolProviderService,

        /**
         * An identifier uniquely representing the call; set to be same as Jingle Sid if available.
         */
        var callId: String,
) : DataObject() {

    /**
     * Returns the id of the specified Call.
     *
     * @return a String uniquely identifying the call.
     */
    /**
     * An identifier uniquely representing the call; set to same as Jingle Sid if available.
     */
    // var callId: String

    /**
     * A list of all listeners currently registered for `CallChangeEvent`s
     */
    private val callListeners = Vector<CallChangeListener>()

    /**
     * A reference to the ProtocolProviderService instance that created us.
     */
    // val protocolProvider: ProtocolProviderService

    /**
     * Returns the default call encryption flag
     *
     * @return the default call encryption flag
     */
    /**
     * If this flag is set to true according to the account properties related with the
     * sourceProvider the associated CallSession will start encrypted by default (where applicable)
     */
    val isDefaultEncrypted: Boolean
    /**
     * Check if to include the ZRTP attribute to SIP/SDP
     *
     * @return include the ZRTP attribute to SIP/SDP
     */
    /**
     * If this flag is set to true according to the account properties related with the
     * sourceProvider the associated CallSession will set the SIP/SDP attribute (where applicable)
     */
    val isSipZrtpAttribute: Boolean

    /**
     * Returns an iterator over all call peers.
     *
     * @return an Iterator over all peers currently involved in the call.
     */
    abstract fun getCallPeers(): Iterator<T>

    /**
     * The state that this call is currently in.
     */
    @JvmField
    var callState = CallState.CALL_INITIALIZATION

    /**
     * The telephony conference-related state of this `Call`. Since a non-conference
     * `Call` may be converted into a conference `Call` at any time, every
     * `Call` instance maintains a `CallConference` instance regardless of whether
     * the `Call` in question is participating in a telephony conference.
     */
    private var conference: CallConference? = null

    /**
     * Returns `true` iff incoming calls into this `Call` should be auto-answered.
     *
     * @return `true` iff incoming calls into this `Call` should be auto-answered.
     */
    /**
     * Sets the flag that specifies whether incoming calls into this `Call` should be auto-answered.
     *
     * autoAnswer whether incoming calls into this `Call` should be auto-answered.
     */
    /**
     * The flag that specifies whether incoming calls into this `Call` should be
     * auto-answered.
     */
    var isAutoAnswer = false

    /**
     * The indicator which determines whether any telephony conference represented by this instance
     * is mixing or relaying. By default what can be mixed is mixed (audio) and rest is relayed.
     */
    @JvmField
    val useTranslator: Boolean

    /**
     * Creates a new Call instance.
     *
     * sourceProvider the proto provider that created us.
     * sid the Jingle session-initiate id if provided.
     */
    init {
        // create the uid
        this.callId = if (TextUtils.isEmpty(callId)) {
            JingleManager.randomId()
        } else {
            callId
        }

        val accountID = pps.accountID
        isDefaultEncrypted = accountID.getAccountPropertyBoolean(
                ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
        isSipZrtpAttribute = accountID.getAccountPropertyBoolean(
                ProtocolProviderFactory.DEFAULT_SIPZRTP_ATTRIBUTE, true)
        useTranslator = accountID.getAccountPropertyBoolean(
                ProtocolProviderFactory.USE_TRANSLATOR_IN_CONFERENCE, false)
    }

    /**
     * Compares the specified object with this call and returns true if it the specified object is
     * an instance of a Call object and if the extending telephony protocol considers the calls
     * represented by both objects to be the same.
     *
     * other the call to compare this one with.
     * @return true in case both objects are pertaining to the same call and false otherwise.
     */
    override fun equals(other: Any?): Boolean {
        return if (other !is Call<*>) false else other === this || other.callId == callId
    }

    /**
     * Returns a hash code value for this call.
     *
     * @return a hash code value for this call.
     */
    override fun hashCode(): Int {
        return callId.hashCode()
    }

    /**
     * Adds a call change listener to this call so that it could receive events on new call peers,
     * theme changes and others.
     *
     * listener the listener to register
     */
    fun addCallChangeListener(listener: CallChangeListener) {
        synchronized(callListeners) {
            if (!callListeners.contains(listener)) {
                callListeners.add(listener)
            }
        }
    }

    /**
     * Removes `listener` to this call so that it won't receive further `CallChangeEvent`s.
     *
     * listener the listener to register
     */
    fun removeCallChangeListener(listener: CallChangeListener) {
        synchronized(callListeners) { callListeners.remove(listener) }
    }

    /**
     * Creates a `CallPeerEvent` with `sourceCallPeer` and `eventID` and
     * dispatches it on all currently registered listeners.
     *
     * sourceCallPeer the source `CallPeer` for the newly created event.
     * eventID the ID of the event to create (see constants defined in `CallPeerEvent`)
     * delayed `true` if the adding/removing of the peer from the GUI should be delayed and
     * `false` if not.
     */
    /**
     * Creates a `CallPeerEvent` with `sourceCallPeer` and `eventID` and
     * dispatches it on all currently registered listeners.
     *
     * sourceCallPeer the source `CallPeer` for the newly created event.
     * eventID the ID of the event to create (see constants defined in `CallPeerEvent`)
     */
    protected fun fireCallPeerEvent(sourceCallPeer: CallPeer?, eventID: Int, delayed: Boolean = false) {
        val event = CallPeerEvent(sourceCallPeer, this, eventID, delayed)
        // Timber.d("Dispatching CallPeer event to %s listeners. The event is: %s", callListeners.size(), event);
        var listeners: Iterator<CallChangeListener>
        synchronized(callListeners) { listeners = ArrayList(callListeners).iterator() }
        while (listeners.hasNext()) {
            val listener = listeners.next()
            if (eventID == CallPeerEvent.CALL_PEER_ADDED) listener.callPeerAdded(event) else if (eventID == CallPeerEvent.CALL_PEER_REMOVED) listener.callPeerRemoved(event)
        }
    }

    /**
     * Returns a string textually representing this Call.
     *
     * @return a string representation of the object.
     */
    override fun toString(): String {
        return "Call: id=$callId peers=$callPeerCount"
    }
    /**
     * Creates a `CallChangeEvent` with this class as `sourceCall`, and the specified
     * `eventID` and old and new values and dispatches it on all currently registered listeners.
     *
     * type the type of the event to create (see CallChangeEvent member ints)
     * oldValue the value of the call property that changed, before the event had occurred.
     * newValue the value of the call property that changed, after the event has occurred.
     * cause the event that is the initial cause of the current one.
     */
    /**
     * Creates a `CallChangeEvent` with this class as `sourceCall`, and the specified
     * `eventID` and old and new values and dispatches it on all currently registered
     * listeners.
     *
     * type the type of the event to create (see CallChangeEvent member ints)
     * oldValue the value of the call property that changed, before the event had occurred.
     * newValue the value of the call property that changed, after the event has occurred.
     */
    protected fun fireCallChangeEvent(type: String?, oldValue: Any?, newValue: Any?, cause: CallPeerChangeEvent? = null) {
        val event = CallChangeEvent(this, type, oldValue, newValue, cause)
        Timber.d("Dispatching CallChangeEvent to (%s) listeners. %s", callListeners.size, event)
        var listeners: Array<CallChangeListener>
        synchronized(callListeners) { listeners = callListeners.toTypedArray() }
        for (listener in listeners) listener.callStateChanged(event)
    }

    /**
     * Returns the state that this call is currently in.
     *
     * @return a reference to the `CallState` instance that the call is currently in.
     */
    fun getCallState(): CallState {
        return callState
    }

    /**
     * Sets the state of this call and fires a call change event notifying registered listeners for the change.
     *
     * newState a reference to the `CallState` instance that the call is to enter.
     */
    protected fun setCallState(newState: CallState) {
        setCallState(newState, null)
    }

    /**
     * Sets the state of this `Call` and fires a new `CallChangeEvent` notifying the
     * registered `CallChangeListener`s about the change of the state.
     *
     * newState the `CallState` into which this `Call` is to enter
     * cause the `CallPeerChangeEvent` which is the cause for the request to have this
     * `Call` enter the specified `CallState`
     */
    protected open fun setCallState(newState: CallState, cause: CallPeerChangeEvent?) {
        val oldState = getCallState()
        if (oldState != newState) {
            callState = newState
            try {
                fireCallChangeEvent(CallChangeEvent.CALL_STATE_CHANGE, oldState, callState, cause)
            } finally {
                if (CallState.CALL_ENDED == getCallState()) conference = null
            }
        }
    }

    /**
     * Returns the number of peers currently associated with this call.
     *
     * @return an `int` indicating the number of peers currently associated with this call.
     */
    abstract val callPeerCount: Int

    /**
     * Gets the indicator which determines whether the local peer represented by this `Call`
     * is acting as a conference focus. In the case of SIP, for example, it determines whether the
     * local peer should send the &quot;isfocus&quot; parameter in the Contact headers of its
     * outgoing SIP signaling.
     *
     * @return `true` if the local peer represented by this `Call` is acting as a
     * conference focus; otherwise, `false`
     */
    abstract val isConferenceFocus: Boolean

    /**
     * Adds a specific `SoundLevelListener` to the list of listeners interested in and
     * notified about changes in local sound level information.
     *
     * l the `SoundLevelListener` to add
     */
    abstract fun addLocalUserSoundLevelListener(l: SoundLevelListener?)

    /**
     * Removes a specific `SoundLevelListener` from the list of listeners interested in and
     * notified about changes in local sound level information.
     *
     * l the `SoundLevelListener` to remove
     */
    abstract fun removeLocalUserSoundLevelListener(l: SoundLevelListener?)

    /**
     * Creates a new `CallConference` instance which is to represent the telephony
     * conference-related state of this `Call`. Allows extenders to override and customize
     * the runtime type of the `CallConference` to used by this `Call`.
     *
     * @return a new `CallConference` instance which is to represent the telephony
     * conference-related state of this `Call`
     */
    protected open fun createConference(): CallConference? {
        return CallConference()
    }

    /**
     * Gets the telephony conference-related state of this `Call`. Since a non-conference
     * `Call` may be converted into a conference `Call` at any time, every
     * `Call` instance maintains a `CallConference` instance regardless of whether
     * the `Call` in question is participating in a telephony conference.
     *
     * @return a `CallConference` instance which represents the telephony conference-related
     * state of this `Call`.
     */
    open fun getConference(): CallConference {
        if (conference == null) {
            val newValue = createConference()
            checkNotNull(newValue) {
                /*
                 * Call is documented to always have a telephony conference-related state because
                 * there is an expectation that a 1-to-1 Call can always be turned into a
                 * conference Call.
                 */
                "conference"
            }
            setConference(newValue)
        }
        return conference!!
    }

    /**
     * Sets the telephony conference-related state of this `Call`. If the invocation modifies
     * this instance, it adds this `Call` to the newly set `CallConference` and fires
     * a `PropertyChangeEvent` for the `CONFERENCE` property to its listeners.
     *
     * @param conference the `CallConference` instance to represent the telephony conference-related
     * state of this `Call`
     */
    open fun setConference(conference: CallConference) {
        if (this.conference != conference) {
            val oldValue = this.conference
            this.conference = conference
            val newValue = this.conference
            oldValue?.removeCall(this)
            newValue?.addCall(this)
            firePropertyChange(CONFERENCE, oldValue, newValue)
        }
    }

    /**
     * Adds a specific `PropertyChangeListener` to the list of listeners interested in and
     * notified about changes in the values of the properties of this `Call`.
     *
     * listener a `PropertyChangeListener` to be notified about changes in the values of the
     * properties of this `Call`. If the specified listener is already in the list of
     * interested listeners (i.e. it has been previously added), it is not added again.
     */
    abstract fun addPropertyChangeListener(listener: PropertyChangeListener?)

    /**
     * Fires a new `PropertyChangeEvent` to the `PropertyChangeListener`s registered
     * with this `Call` in order to notify about a change in the value of a specific
     * property which had its old value modified to a specific new value.
     *
     * property the name of the property of this `Call` which had its value changed
     * oldValue the value of the property with the specified name before the change
     * newValue the value of the property with the specified name after the change
     */
    protected abstract fun firePropertyChange(property: String?, oldValue: Any?, newValue: Any?)

    /**
     * Removes a specific `PropertyChangeListener` from the list of listeners interested in
     * and notified about changes in the values of the properties of this `Call`.
     *
     * listener a `PropertyChangeListener` to no longer be notified about changes in the values
     * of the properties of this `Call`
     */
    abstract fun removePropertyChangeListener(listener: PropertyChangeListener?)

    companion object {
        /**
         * The name of the `Call` property which represents its telephony conference-related state.
         */
        const val CONFERENCE = "conference"

        /**
         * The name of the `Call` property which indicates whether the local peer/user
         * represented by the respective `Call` is acting as a conference focus.
         */
        const val CONFERENCE_FOCUS = "conferenceFocus"
    }
}