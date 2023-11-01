/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.CallPeer
import net.java.sip.communicator.service.protocol.ConferenceMember
import java.util.*

/**
 * Represents an event fired by a `CallPeer` to notify interested
 * `CallPeerConferenceListener`s about changes in its conference-related information such
 * as it acting or not acting as a conference focus and conference membership details.
 *
 * @author Lubomir Marinov
 */
class CallPeerConferenceEvent
/**
 * Initializes a new `CallPeerConferenceEvent` which is to be fired by a specific
 * `CallPeer` and which notifies about a change in its conference-related information
 * not including a change pertaining to a specific `ConferenceMember`.
 *
 * @param sourceCallPeer
 * the `CallPeer` which is to fire the new event
 * @param eventID
 * the ID of this event which may be [.CONFERENCE_FOCUS_CHANGED] and indicates the
 * specifics of the change in the conference-related information and the details this
 * event carries
 */ @JvmOverloads constructor(sourceCallPeer: CallPeer?,
        /**
         * The ID of this event which may be one of [.CONFERENCE_FOCUS_CHANGED],
         * [.CONFERENCE_MEMBER_ADDED], [.CONFERENCE_MEMBER_ERROR_RECEIVED] and
         * [.CONFERENCE_MEMBER_REMOVED] and indicates the specifics of the change in the
         * conference-related information and the details this event carries.
         */
        private val eventID: Int,
        /**
         * The `ConferenceMember` which has been changed (e.g. added to or removed from the
         * conference) if this event has been fired because of such a change; otherwise, `null`.
         */
        private val conferenceMember: ConferenceMember? = null,
        /**
         * The error message associated with the error packet that was received. If the eventID is not
         * [.CONFERENCE_MEMBER_ERROR_RECEIVED] the value should should be `null`.
         */
        private val errorString: String? = null) : EventObject(sourceCallPeer) {
    /**
     * Initializes a new `CallPeerConferenceEvent` which is to be fired by a specific
     * `CallPeer` and which notifies about a change in its conference-related information
     * pertaining to a specific `ConferenceMember`.
     *
     * @param sourceCallPeer
     * the `CallPeer` which is to fire the new event
     * @param eventID
     * the ID of this event which may be [.CONFERENCE_MEMBER_ADDED] and
     * [.CONFERENCE_MEMBER_REMOVED] and indicates the specifics of the change in the
     * conference-related information and the details this event carries
     * @param conferenceMember
     * the `ConferenceMember` which caused the new event to be fired
     * @param errorString
     * the error string associated with the error packet that is received
     */
    /**
     * Initializes a new `CallPeerConferenceEvent` which is to be fired by a specific
     * `CallPeer` and which notifies about a change in its conference-related information
     * pertaining to a specific `ConferenceMember`.
     *
     * @param sourceCallPeer
     * the `CallPeer` which is to fire the new event
     * @param eventID
     * the ID of this event which may be [.CONFERENCE_MEMBER_ADDED] and
     * [.CONFERENCE_MEMBER_REMOVED] and indicates the specifics of the change in the
     * conference-related information and the details this event carries
     * @param conferenceMember
     * the `ConferenceMember` which caused the new event to be fired
     */
    /**
     * Gets the `ConferenceMember` which has been changed (e.g. added to or removed from
     * the conference) if this event has been fired because of such a change.
     *
     * @return the `ConferenceMember` which has been changed if this event has been fired
     * because of such a change; otherwise, `null`
     */
    fun getConferenceMember(): ConferenceMember? {
        return conferenceMember
    }

    /**
     * Gets the ID of this event which may be one of [.CONFERENCE_FOCUS_CHANGED],
     * [.CONFERENCE_MEMBER_ADDED] and [.CONFERENCE_MEMBER_REMOVED] and indicates the
     * specifics of the change in the conference-related information and the details this event
     * carries.
     *
     * @return the ID of this event which may be one of [.CONFERENCE_FOCUS_CHANGED],
     * [.CONFERENCE_MEMBER_ADDED] and [.CONFERENCE_MEMBER_REMOVED] and indicates
     * the specifics of the change in the conference-related information and the details
     * this event carries
     */
    fun getEventID(): Int {
        return eventID
    }

    /**
     * Gets the `CallPeer` which is the source of/fired the event.
     *
     * @return the `CallPeer` which is the source of/fired the event
     */
    fun getSourceCallPeer(): CallPeer {
        return getSource() as CallPeer
    }

    /**
     * Gets the value of [.errorString].
     *
     * @return the error string.
     */
    fun getErrorString(): String? {
        return errorString
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * The ID of `CallPeerConferenceEvent` which notifies about a change in the
         * characteristic of a specific `CallPeer` being a conference focus. The event does
         * not carry information about a specific `ConferenceMember` i.e. the
         * `conferenceMember` property is of value `null`.
         */
        const val CONFERENCE_FOCUS_CHANGED = 1

        /**
         * The ID of `CallPeerConferenceEvent` which notifies about an addition to the list
         * of `ConferenceMember`s managed by a specific `CallPeer`. The
         * `conferenceMember` property specifies the `ConferenceMember` which was
         * added and thus caused the event to be fired.
         */
        const val CONFERENCE_MEMBER_ADDED = 2

        /**
         * The ID of `CallPeerConferenceEvent` which notifies about a removal from the list
         * of `ConferenceMember`s managed by a specific `CallPeer`. The
         * `conferenceMember` property specifies the `ConferenceMember` which was
         * removed and thus caused the event to be fired.
         */
        const val CONFERENCE_MEMBER_REMOVED = 3

        /**
         * The ID of `CallPeerConferenceEvent` which notifies about an error packet received from
         * a `CallPeer` .
         */
        const val CONFERENCE_MEMBER_ERROR_RECEIVED = 4
    }
}