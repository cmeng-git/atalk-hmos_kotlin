/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.Call
import net.java.sip.communicator.service.protocol.CallConference
import org.atalk.service.neomedia.MediaDirection
import org.atalk.util.MediaType
import java.util.*

/**
 * An event class representing that an incoming, or an outgoing call has been created.
 * The event id indicates the exact reason for this event.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class CallEvent(call: Call<*>,
        /**
         * Determines whether this event has been fired to indicate an incoming or an outgoing call.
         */
        val eventID: Int, mediaDirections: Map<MediaType, MediaDirection>?) : EventObject(call) {
    /**
     * Returns an event ID indicates the event was triggered by an outgoing or an incoming call.
     *
     * @return one of the CALL_XXX static member ints.
     */

    /**
     * The media types supported by this call, if information is available.
     */
    private val mediaDirections: Map<MediaType, MediaDirection>

    /**
     * The conference of the call for this event. Must be set when creating this event. This is because when
     * a call ends, the call conference may be released just after creating this event, but its
     * reference will still be necessary in the future for the UI (i.e to release the call panel),
     */
    private val conference: CallConference?

    /**
     * Indicate whether the call is recognized to be video call and desktop streaming call.
     */
    var isDesktopStreaming = false

    /**
     * Initializes a new `CallEvent` instance which is to represent an event fired by a
     * specific `Call` as its source.
     *
     * call the call that triggered this event.
     * eventID determines whether the new instance represents an event notifying that:
     * a. an outgoing `Call` was initiated, or
     * b. an incoming `Call` was received, or
     * c. a `Call` has ended
     * mediaDirections Media Direction.
     */
    init {
        val thisMediaDirections = HashMap<MediaType, MediaDirection>()
        if (mediaDirections != null) thisMediaDirections.putAll(mediaDirections)
        this.mediaDirections = Collections.unmodifiableMap(thisMediaDirections)
        conference = call.getConference()
    }

    /**
     * Return the media directions map
     *
     * @return the supported media direction map of current call.
     */
    fun getMediaDirections(): Map<MediaType, MediaDirection> {
        return mediaDirections
    }

    /**
     * Return the media types supported by this call, if information is available. It can be empty
     * list if information wasn't provided for this event and call.
     *
     * @return the supported media types of current call.
     */
    val mediaTypes: List<MediaType>
        get() = ArrayList(mediaDirections.keys)

    /**
     * Returns the `Call` that triggered this event.
     *
     * @return the `Call` that triggered this event.
     */
    val sourceCall: Call<*>
        get() = getSource() as Call<*>

    /**
     * Returns the `CallConference` that triggered this event.
     *
     * @return the `CallConference` that triggered this event.
     */
    val callConference: CallConference?
        get() = conference

    /**
     * Returns true if the call a video call.
     *
     * @return true if the call is a video call, false otherwise
     */
    val isVideoCall: Boolean
        get() {
            val direction: MediaDirection? = mediaDirections[MediaType.VIDEO]
            return MediaDirection.SENDRECV === direction
        }

    /**
     * Returns a String representation of this CallEvent.
     *
     * @return A a String representation of this CallEvent.
     */
    override fun toString(): String {
        return "CallEvent:[id=$eventID Call=$sourceCall]"
    }

    companion object {
        /**
         * An event id value indicating that this event has been triggered as a result of an outgoing call.
         */
        const val CALL_INITIATED = 1

        /**
         * An event id value indicating that this event has been triggered as a result of an incoming call.
         */
        const val CALL_RECEIVED = 2

        /**
         * An event id value indicating that this event has been triggered as a result of a call ended (all its peers have left).
         */
        const val CALL_ENDED = 3

        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}