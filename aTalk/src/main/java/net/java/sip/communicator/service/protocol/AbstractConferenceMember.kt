/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import org.atalk.service.neomedia.MediaDirection
import org.atalk.util.event.PropertyChangeNotifier
import java.lang.NullPointerException

/**
 * Provides the default implementation of the `ConferenceMember` interface.
 *
 * @author Lyubomir Marinov
 * @author Yana Stamcheva
 * @author Emil Ivov
 */
class AbstractConferenceMember(conferenceFocusCallPeer: CallPeer?, address: String?) : PropertyChangeNotifier(), ConferenceMember {
    /**
     * The protocol address of this `ConferenceMember`.
     */
    private val address: String

    /**
     * The audio SSRC value if transmitted by the focus of the conference.
     */
    private var audioSsrc = -1L

    /**
     * The status in both directions of the audio RTP stream from the point of view of this
     * `ConferenceMember`.
     */
    private var audioStatus = MediaDirection.INACTIVE

    /**
     * The `CallPeer` which is the conference focus of this `ConferenceMember`.
     */
    private val conferenceFocusCallPeer: CallPeer

    /**
     * The user-friendly display name of this `ConferenceMember` in the conference.
     */
    private var displayName: String? = null

    /**
     * The state of the device and signaling session of this `ConferenceMember` in the
     * conference.
     */
    private var state = ConferenceMemberState.UNKNOWN

    /**
     * The video SSRC value if transmitted by the focus of the conference.
     */
    private var videoSsrc = -1L

    /**
     * The status in both directions of the video RTP stream from the point of view of this
     * `ConferenceMember`.
     */
    private var videoStatus = MediaDirection.INACTIVE

    /**
     * Creates an instance of `AbstractConferenceMember` by specifying the corresponding
     * `conferenceFocusCallPeer`, to which this member is connected.
     *
     * @param conferenceFocusCallPeer the `CallPeer` to which this member is connected
     * @param address the protocol address of this `ConferenceMember`
     * @throws NullPointerException if `conferenceFocusCallPeer` or `address` is `null`
     */
    init {
        if (conferenceFocusCallPeer == null) throw NullPointerException("conferenceFocusCallPeer")
        if (address == null) throw NullPointerException("address")
        this.conferenceFocusCallPeer = conferenceFocusCallPeer
        this.address = address
    }

    /**
     * Returns the protocol address of this `ConferenceMember`.
     *
     * @return the protocol address of this `ConferenceMember`
     */
    override fun getAddress(): String {
        return address
    }

    /**
     * Returns the SSRC value associated with this participant;
     *
     * @return the audio ssrc id
     */
    override fun getAudioSsrc(): Long {
        return audioSsrc
    }

    /**
     * {@inheritDoc}
     */
    override fun getAudioStatus(): MediaDirection? {
        return audioStatus
    }

    /**
     * {@inheritDoc}
     *
     * Implements [ConferenceMember.getConferenceFocusCallPeer].
     */
    override fun getConferenceFocusCallPeer(): CallPeer {
        return conferenceFocusCallPeer
    }

    /**
     * Returns the display name of this conference member. Implements
     * `ConferenceMember#getDisplayName()`.
     *
     * @return the display name of this conference member
     */
    override fun getDisplayName(): String? {
        val displayName = displayName
        if (displayName == null || displayName.length < 1) {
            val address = getAddress()
            if (address != null && address.length > 0) return address
        }
        return displayName
    }

    /**
     * Returns the state of this conference member. Implements `ConferenceMember#getState()`.
     *
     * @return the state of this conference member
     */
    override fun getState(): ConferenceMemberState {
        return state
    }

    /**
     * Returns the SSRC value associated with this participant;
     *
     * @return the video ssrc id
     */
    override fun getVideoSsrc(): Long {
        return videoSsrc
    }

    /**
     * {@inheritDoc}
     */
    override fun getVideoStatus(): MediaDirection? {
        return videoStatus
    }

    /**
     * Sets the audio SSRC identifier of this member.
     *
     * @param ssrc the audio SSRC ID to set for this member.
     */
    fun setAudioSsrc(ssrc: Long) {
        if (audioSsrc != ssrc) {
            val oldValue = audioSsrc
            audioSsrc = ssrc
            firePropertyChange(ConferenceMember.Companion.AUDIO_SSRC_PROPERTY_NAME, oldValue, audioSsrc)
        }
    }

    /**
     * Sets the status in both directions of the audio RTP stream from the point of view of this
     * `ConferenceMember`.
     *
     * @param status the status in both directions of the audio RTP stream from the point of view of this
     * `ConferenceMember`. If `null`, the method executes as if
     * [MediaDirection.INACTIVE]. was specified.
     */
    private fun setAudioStatus(status: MediaDirection?) {
        var status = status
        if (status == null) status = MediaDirection.INACTIVE
        if (audioStatus != status) {
            val oldValue = audioStatus
            audioStatus = status
            firePropertyChange(ConferenceMember.Companion.AUDIO_STATUS_PROPERTY_NAME, oldValue, audioStatus)
        }
    }

    /**
     * Sets the user-friendly display name of this `ConferenceMember` in the conference and
     * fires a new `PropertyChangeEvent` for the property
     * `#DISPLAY_NAME_PROPERTY_NAME`.
     *
     * @param displayName the user-friendly display name of this `ConferenceMember` in the conference
     */
    fun setDisplayName(displayName: String?) {
        if ((this.displayName == null && displayName != null || this.displayName != null) && this.displayName != displayName) {
            val oldValue = this.displayName
            this.displayName = displayName
            firePropertyChange(ConferenceMember.Companion.DISPLAY_NAME_PROPERTY_NAME, oldValue, this.displayName)
        }
    }

    /**
     * Sets the `state` property of this `ConferenceMember` by translating it from its
     * conference-info XML endpoint status.
     *
     * @param endpointStatus the conference-info XML endpoint status of this `ConferenceMember` indicated by
     * its `conferenceFocusCallPeer`
     */
    fun setEndpointStatus(endpointStatus: String?) {
        val state = when {
            ALERTING.equals(endpointStatus, ignoreCase = true) -> ConferenceMemberState.ALERTING
            CONNECTED.equals(endpointStatus, ignoreCase = true) -> ConferenceMemberState.CONNECTED
            DIALING_IN.equals(endpointStatus, ignoreCase = true) -> ConferenceMemberState.DIALING_IN
            DIALING_OUT.equals(endpointStatus, ignoreCase = true) -> ConferenceMemberState.DIALING_OUT
            DISCONNECTED.equals(endpointStatus, ignoreCase = true) -> ConferenceMemberState.DISCONNECTED
            ON_HOLD.equals(endpointStatus, ignoreCase = true) -> ConferenceMemberState.ON_HOLD
            PENDING.equals(endpointStatus, ignoreCase = true) -> ConferenceMemberState.PENDING
            else -> ConferenceMemberState.UNKNOWN
        }
        setState(state)
    }

    fun setProperties(properties: MutableMap<String, Any?>): Boolean {
        var changed = false
        for ((key, value) in properties) {
            if (ConferenceMember.AUDIO_SSRC_PROPERTY_NAME == key) {
                val ssrc = parseMediaSSRC(value)
                if (getAudioSsrc() != ssrc) {
                    setAudioSsrc(ssrc)
                    changed = true
                }
            } else if (ConferenceMember.AUDIO_STATUS_PROPERTY_NAME == key) {
                val status = parseMediaStatus(value)
                if (getAudioStatus()!! != status) {
                    setAudioStatus(status)
                    changed = true
                }
            } else if (ConferenceMember.VIDEO_SSRC_PROPERTY_NAME == key) {
                val ssrc = parseMediaSSRC(value)
                if (getVideoSsrc() != ssrc) {
                    setVideoSsrc(ssrc)
                    changed = true
                }
            } else if (ConferenceMember.VIDEO_STATUS_PROPERTY_NAME == key) {
                val status = parseMediaStatus(value)
                if (getVideoStatus()!! != status) {
                    setVideoStatus(status)
                    changed = true
                }
            }
        }
        return changed
    }

    /**
     * Sets the state of the device and signaling session of this `ConferenceMember` in the
     * conference and fires a new `PropertyChangeEvent` for the property
     * `#STATE_PROPERTY_NAME`.
     *
     * @param state the state of the device and signaling session of this `ConferenceMember` in the
     * conference
     */
    fun setState(state: ConferenceMemberState) {
        if (this.state != state) {
            val oldValue = this.state
            this.state = state
            firePropertyChange(ConferenceMember.STATE_PROPERTY_NAME, oldValue, this.state)
        }
    }

    /**
     * Sets the video SSRC identifier of this member.
     *
     * @param ssrc the video SSRC ID to set for this member.
     */
    fun setVideoSsrc(ssrc: Long) {
        if (videoSsrc != ssrc) {
            val oldValue = videoSsrc
            videoSsrc = ssrc
            firePropertyChange(ConferenceMember.VIDEO_SSRC_PROPERTY_NAME, oldValue, videoSsrc)
        }
    }

    /**
     * Sets the status in both directions of the video RTP stream from the point of view of this
     * `ConferenceMember`.
     *
     * @param status the status in both directions of the video RTP stream from the point of view of this
     * `ConferenceMember`. If `null`, the method executes as if
     * [MediaDirection.INACTIVE]. was specified.
     */
    fun setVideoStatus(status: MediaDirection?) {
        var status = status
        if (status == null) status = MediaDirection.INACTIVE
        if (videoStatus != status) {
            val oldValue = videoStatus
            videoStatus = status
            firePropertyChange(ConferenceMember.VIDEO_STATUS_PROPERTY_NAME, oldValue, videoStatus)
        }
    }

    companion object {
        /**
         * A Public Switched Telephone Network (PSTN) ALERTING or SIP 180 Ringing was returned for the
         * outbound call; endpoint is being alerted.
         */
        const val ALERTING = "alerting"

        /**
         * The endpoint is a participant in the conference. Depending on the media policies, he/she can
         * send and receive media to and from other participants.
         */
        const val CONNECTED = "connected"

        /**
         * Endpoint is dialing into the conference, not yet in the roster (probably being
         * authenticated).
         */
        const val DIALING_IN = "dialing-in"

        /**
         * Focus has dialed out to connect the endpoint to the conference, but the endpoint is not yet
         * in the roster (probably being authenticated).
         */
        const val DIALING_OUT = "dialing-out"

        /**
         * The endpoint is not a participant in the conference, and no active dialog exists between the
         * endpoint and the focus.
         */
        const val DISCONNECTED = "disconnected"

        /**
         * Active signaling dialog exists between an endpoint and a focus, but endpoint is "on-hold" for
         * this conference, i.e., he/she is neither "hearing" the conference mix nor is his/her media
         * being mixed in the conference.
         */
        const val ON_HOLD = "on-hold"

        /**
         * Endpoint is not yet in the session, but it is anticipated that he/she will join in the near
         * future.
         */
        const val PENDING = "pending"
        private fun parseMediaSSRC(value: Any?): Long {
            val ssrc = if (value == null) -1 else if (value is Long) value else {
                val str = value.toString()
                if (str == null || str.isEmpty()) -1 else str.toLong()
            }
            return ssrc
        }

        private fun parseMediaStatus(value: Any?): MediaDirection {
            val status = when (value) {
                null -> MediaDirection.INACTIVE
                is MediaDirection -> value
                else -> {
                    val str = value.toString()
                    if (str == null || str.isEmpty()) MediaDirection.INACTIVE else MediaDirection.fromString(str)
                }
            }
            return status
        }
    }
}