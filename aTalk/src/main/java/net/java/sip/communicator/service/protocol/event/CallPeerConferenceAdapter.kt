/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

/**
 * An abstract implementation of `CallPeerConferenceListener` which exists only as a
 * convenience to extenders. Additionally, provides a means to receive the
 * `CallPeerConferenceEvent`s passed to the various `CallPeerConferenceListener`
 * methods into a single method because their specifics can be determined based on their
 * `eventID`.
 *
 * @author Lyubomir Marinov
 */
open class CallPeerConferenceAdapter : CallPeerConferenceListener {
    /**
     * {@inheritDoc}
     *
     * Calls [.onCallPeerConferenceEvent].
     */
    override fun conferenceFocusChanged(conferenceEvent: CallPeerConferenceEvent) {
        onCallPeerConferenceEvent(conferenceEvent)
    }

    /**
     * {@inheritDoc}
     *
     * Calls [.onCallPeerConferenceEvent].
     */
    override fun conferenceMemberAdded(conferenceEvent: CallPeerConferenceEvent) {
        onCallPeerConferenceEvent(conferenceEvent)
    }

    /**
     * {@inheritDoc}
     *
     * Dummy implementation of [.conferenceMemberErrorReceived].
     */
    override fun conferenceMemberErrorReceived(conferenceEvent: CallPeerConferenceEvent) {}

    /**
     * {@inheritDoc}
     *
     * Calls [.onCallPeerConferenceEvent].
     */
    override fun conferenceMemberRemoved(conferenceEvent: CallPeerConferenceEvent) {
        onCallPeerConferenceEvent(conferenceEvent)
    }

    /**
     * Notifies this listener about a specific `CallPeerConferenceEvent` provided to one of
     * the `CallPeerConferenceListener` methods. The `CallPeerConferenceListener`
     * method which was originally invoked on this listener can be determined based on the
     * `eventID` of the specified `CallPeerConferenceEvent`. The implementation of
     * `CallPeerConferenceAdapter` does nothing.
     *
     * @param ev
     * the `CallPeerConferenceEvent` this listener is being notified about
     */
    protected open fun onCallPeerConferenceEvent(ev: CallPeerConferenceEvent) {}
}