/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.CallPeerConferenceListener
import net.java.sip.communicator.service.protocol.event.CallPeerListener
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityListener
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityStatusEvent
import net.java.sip.communicator.service.protocol.event.ConferenceMembersSoundLevelListener
import net.java.sip.communicator.service.protocol.event.SoundLevelListener
import org.jxmpp.jid.Jid
import java.beans.PropertyChangeListener
import java.net.URL

/**
 * The CallPeer is an interface that represents peers in a call. Users of the UIService need to
 * implement this interface (or one of its default implementations such DefaultCallPeer) in order to
 * be able to register call peer in the user interface.
 *
 *
 *
 * For SIP calls for example, it would be necessary to create a CallPeerSipImpl class that would
 * provide sip specific implementations of various methods (getAddress() for example would return
 * the peer's sip URI).
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface CallPeer {
    /**
     * Adds a specific `CallPeerConferenceListener` to the list of listeners interested in
     * and notified about changes in conference-related information such as this peer acting or not
     * acting as a conference focus and conference membership details.
     *
     * @param listener a `CallPeerConferenceListener` to be notified about changes in
     * conference-related information. If the specified listener is already in the list of
     * interested listeners (i.e. it has been previously added), it is not added again.
     */
    fun addCallPeerConferenceListener(listener: CallPeerConferenceListener?)

    /**
     * Allows the user interface to register a listener interested in changes
     *
     * @param listener a listener instance to register with this peer.
     */
    fun addCallPeerListener(listener: CallPeerListener?)

    /**
     * Allows the user interface to register a listener interested in security status changes.
     *
     * @param listener a listener instance to register with this peer
     */
    fun addCallPeerSecurityListener(listener: CallPeerSecurityListener?)

    /**
     * Adds a specific `SoundLevelListener` to the list of listeners interested in and
     * notified about changes in conference members sound level.
     *
     * @param listener the `SoundLevelListener` to add
     */
    fun addConferenceMembersSoundLevelListener(listener: ConferenceMembersSoundLevelListener?)

    /**
     * Allows the user interface to register a listener interested in property changes.
     *
     * @param listener a property change listener instance to register with this peer.
     */
    fun addPropertyChangeListener(listener: PropertyChangeListener)

    /**
     * Adds a specific `SoundLevelListener` to the list of listeners interested in and
     * notified about changes in stream sound level related information.
     *
     * @param listener the `SoundLevelListener` to add
     */
    fun addStreamSoundLevelListener(listener: SoundLevelListener)

    /**
     * Returns a String locator for that peer. A locator might be a SIP URI, an IP address or a telephone number.
     *
     * @return the peer's address or phone number.
     */
    fun getAddress(): String

    /**
     * Returns a reference to the call that this peer belongs to.
     *
     * @return a reference to the call containing this peer.
     */
    fun getCall(): Call<*>?

    /**
     * Gets the time at which this `CallPeer` transitioned into a state (likely
     * [CallPeerState.CONNECTED]) marking the start of the duration of the participation in a `Call`.
     *
     * @return the time at which this `CallPeer` transitioned into a state marking the start
     * of the duration of the participation in a `Call` or
     * [.CALL_DURATION_START_TIME_UNKNOWN] if such a transition has not been performed
     */
    fun getCallDurationStartTime(): Long

    /**
     * Returns a URL pointing to a location with call control information or null if such an URL is
     * not available for the current call peer.
     *
     * @return a URL link to a location with call information or a call control web interface
     * related to this peer or `null` if no such URL is available.
     */
    fun getCallInfoURL(): URL?

    /**
     * Gets the number of `ConferenceMember`s currently known to this peer if it is acting as
     * a conference focus.
     *
     * @return the number of `ConferenceMember`s currently known to this peer if it is acting
     * as a conference focus. If this peer is not acting as a conference focus or it does
     * but there are currently no members in the conference it manages, a value of zero is
     * returned.
     */
    fun getConferenceMemberCount(): Int

    /**
     * Gets the `ConferenceMember`s currently known to this peer if it is acting as a
     * conference focus.
     *
     * @return a `List` of `ConferenceMember`s describing the members of a conference
     * managed by this peer if it is acting as a conference focus. If this peer is not
     * acting as a conference focus or it does but there are currently no members in the
     * conference it manages, an empty `List` is returned.
     */
    fun getConferenceMembers(): List<ConferenceMember>

    /**
     * Returns the contact corresponding to this peer or null if no particular contact has been  associated.
     *
     *
     *
     * @return the `Contact` corresponding to this peer or null if no particular contact has
     * been associated.
     */
    fun getContact(): Contact?

    /**
     * Returns the currently used security settings of this `CallPeer`.
     *
     * @return the `CallPeerSecurityStatusEvent` that contains the current security settings.
     */
    fun getCurrentSecuritySettings(): CallPeerSecurityStatusEvent?

    /**
     * Returns a human readable name representing this peer.
     *
     * @return a String containing a name for that peer.
     */
    fun getDisplayName(): String?

    /**
     * Returns an alternative IMPP address corresponding to this `CallPeer`.
     *
     * @return a string representing an alternative IMPP address corresponding to this `CallPeer`
     */
    fun getAlternativeIMPPAddress(): String?

    /**
     * The method returns an image representation of the call peer (e.g. a photo). Generally, the
     * image representation is acquired from the underlying telephony protocol and is transferred
     * over the network during call negotiation.
     *
     * @return byte[] a byte array containing the image or null if no image is available.
     */
    fun getImage(): ByteArray

    /**
     * Returns a unique identifier representing this peer. Identifiers returned by this method
     * should remain unique across calls. In other words, if it returned the value of "A" for a
     * given peer it should not return that same value for any other peer and return a different
     * value even if the same person (address) is participating in another call. Values need not
     * remain unique after restarting the program.
     *
     * @return an identifier representing this call peer.
     */
    fun getPeerID(): String
    fun getPeerJid(): Jid?

    /**
     * Returns the protocol provider that this peer belongs to.
     *
     * @return a reference to the ProtocolProviderService that this peer belongs to.
     */
    fun getProtocolProvider(): ProtocolProviderService

    /**
     * Returns an object representing the current state of that peer. CallPeerState may vary among
     * CONNECTING, RINGING, CALLING, BUSY, CONNECTED, and others, and it reflects the state of the
     * connection between us and that peer.
     *
     * @return a CallPeerState instance representing the peer's state.
     */
    fun getState(): CallPeerState?

    /**
     * Returns full URI of the address. For example sip:user@domain.org or xmpp:user@domain.org.
     *
     * @return full URI of the address
     */
    fun getURI(): String?

    /**
     * Determines whether this peer is acting as a conference focus and thus may provide information
     * about `ConferenceMember` such as [.getConferenceMembers] and [.getConferenceMemberCount].
     *
     * @return `true` if this peer is acting as a conference focus; `false`, otherwise
     */
    fun isConferenceFocus(): Boolean

    /**
     * Determines whether the audio stream (if any) being sent to this peer is mute.
     *
     * @return `true` if an audio stream is being sent to this peer and it is currently mute; `false`, otherwise
     */
    fun isMute(): Boolean

    /**
     * Removes a specific `CallPeerConferenceListener` from the list of listeners interested
     * in and notified about changes in conference-related information such as this peer acting or
     * not acting as a conference focus and conference membership details.
     *
     * @param listener a `CallPeerConferenceListener` to no longer be notified about changes in
     * conference-related information
     */
    fun removeCallPeerConferenceListener(listener: CallPeerConferenceListener?)

    /**
     * Unregisters the specified listener.
     *
     * @param listener the listener to unregister.
     */
    fun removeCallPeerListener(listener: CallPeerListener?)

    /**
     * Unregisters the specified listener.
     *
     * @param listener the listener to unregister
     */
    fun removeCallPeerSecurityListener(listener: CallPeerSecurityListener?)

    /**
     * Removes a specific `SoundLevelListener` of the list of listeners interested in and
     * notified about changes in conference members sound level.
     *
     * @param listener the `SoundLevelListener` to remove
     */
    fun removeConferenceMembersSoundLevelListener(listener: ConferenceMembersSoundLevelListener)

    /**
     * Unregisters the specified property change listener.
     *
     * @param listener the property change listener to unregister.
     */
    fun removePropertyChangeListener(listener: PropertyChangeListener)

    /**
     * Removes a specific `SoundLevelListener` of the list of listeners interested in and
     * notified about changes in stream sound level related information.
     *
     * @param listener the `SoundLevelListener` to remove
     */
    fun removeStreamSoundLevelListener(listener: SoundLevelListener)

    /**
     * Returns a string representation of the peer in the form of <br></br>
     * Display Name &lt;address&gt;;status=CallPeerStatus
     *
     * @return a string representation of the peer and its state.
     */
    override fun toString(): String

    companion object {
        /**
         * The constant indicating that a `CallPeer` has not yet transitioned into a state
         * marking the beginning of a participation in a `Call` or that such a transition may
         * have happened but the time of its occurrence is unknown.
         */
        const val CALL_DURATION_START_TIME_UNKNOWN = 0L

        /**
         * The mute property name.
         */
        const val MUTE_PROPERTY_NAME = "Mute"
    }
}