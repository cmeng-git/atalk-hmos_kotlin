/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import java.util.*

/**
 * Represents a listener of changes in the conference-related information of `CallPeer`
 * delivered in the form of `CallPeerConferenceEvent`s.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface CallPeerConferenceListener : EventListener {
    /**
     * Notifies this listener about a change in the characteristic of being a conference focus of a
     * specific `CallPeer`.
     *
     * @param conferenceEvent a `CallPeerConferenceEvent` with ID
     * `CallPeerConferenceEvent#CONFERENCE_FOCUS_CHANGED` and no associated
     * `ConferenceMember`
     */
    fun conferenceFocusChanged(conferenceEvent: CallPeerConferenceEvent)

    /**
     * Notifies this listener about the addition of a specific `ConferenceMember` to the list
     * of `ConferenceMember`s of a specific `CallPeer` acting as a conference focus.
     *
     * @param conferenceEvent a `CallPeerConferenceEvent` with ID
     * `CallPeerConferenceEvent#CONFERENCE_MEMBER_ADDED` and `conferenceMember`
     * property specifying the `ConferenceMember` which was added
     */
    fun conferenceMemberAdded(conferenceEvent: CallPeerConferenceEvent)

    /**
     * Notifies this listener about an error packet received from specific `CallPeer`.
     *
     * @param conferenceEvent a `CallPeerConferenceEvent` with ID
     * `CallPeerConferenceEvent#CONFERENCE_MEMBER_ERROR_RECEIVED` and the error
     * message associated with the packet.
     */
    fun conferenceMemberErrorReceived(conferenceEvent: CallPeerConferenceEvent)

    /**
     * Notifies this listener about the removal of a specific `ConferenceMember` from the
     * list of `ConferenceMember`s of a specific `CallPeer` acting as a conference
     * focus.
     *
     * @param conferenceEvent a `CallPeerConferenceEvent` with ID
     * `CallPeerConferenceEvent#CONFERENCE_MEMBER_REMOVED` and
     * `conferenceMember` property specifying the `ConferenceMember` which was
     * removed
     */
    fun conferenceMemberRemoved(conferenceEvent: CallPeerConferenceEvent)
}