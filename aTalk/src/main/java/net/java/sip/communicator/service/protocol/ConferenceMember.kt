/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import org.atalk.service.neomedia.MediaDirection
import java.beans.PropertyChangeListener

/**
 * Represents a member and its details in a telephony conference managed by a `CallPeer` in
 * its role as a conference focus.
 *
 * @author Lyubomir Marinov
 */
interface ConferenceMember {
    /**
     * Adds a specific `PropertyChangeListener` to the list of listeners interested in and
     * notified about changes in the values of the properties of this `ConferenceMember` such
     * as `#DISPLAY_NAME_PROPERTY_NAME` and `#STATE_PROPERTY_NAME`.
     *
     * @param listener
     * a `PropertyChangeListener` to be notified about changes in the values of the
     * properties of this `ConferenceMember`. If the specified listener is already in
     * the list of interested listeners (i.e. it has been previously added), it is not added
     * again.
     */
    fun addPropertyChangeListener(listener: PropertyChangeListener)

    /**
     * Gets the SIP address of this `ConferenceMember` as specified by the conference-info
     * XML received from its `conferenceFocusCallPeer`.
     *
     * @return the SIP address of this `ConferenceMember` as specified by the conference-info
     * XML received from its `conferenceFocusCallPeer`
     */
    fun getAddress(): String

    /**
     * Returns the SSRC of the audio content/RTP stream sent by this `ConferenceMember` in
     * the conference or `-1` if such information is not currently available.
     *
     * @return the SSRC of the audio content/RTP stream sent by this `ConferenceMember` in
     * the conference or `-1` if such information is not currently available
     */
    fun getAudioSsrc(): Long

    /**
     * Gets the status in both directions of the audio RTP stream from the point of view of this
     * `ConferenceMember`.
     *
     * @return a `MediaDIrection` which represents the status in both directions of the audio
     * RTP stream from the point of view of this `ConferenceMember`
     */
    fun getAudioStatus(): MediaDirection?

    /**
     * Gets the `CallPeer` which is the conference focus of this `ConferenceMember`.
     *
     * @return the `CallPeer` which is the conference focus of this `ConferenceMember`
     */
    fun getConferenceFocusCallPeer(): CallPeer

    /**
     * Gets the user-friendly display name of this `ConferenceMember` in the conference.
     *
     * @return the user-friendly display name of this `ConferenceMember` in the conference
     */
    fun getDisplayName(): String?

    /**
     * Gets the state of the device and signaling session of this `ConferenceMember` in the
     * conference in the form of a `ConferenceMemberState` value.
     *
     * @return a `ConferenceMemberState` value which represents the state of the device and
     * signaling session of this `ConferenceMember` in the conference
     */
    fun getState(): ConferenceMemberState

    /**
     * Returns the SSRC of the video content/RTP stream sent by this `ConferenceMember` in
     * the conference or `-1` if such information is not currently available.
     *
     * @return the SSRC of the video content/RTP stream sent by this `ConferenceMember` in
     * the conference or `-1` if such information is not currently available
     */
    fun getVideoSsrc(): Long

    /**
     * Gets the status in both directions of the video RTP stream from the point of view of this
     * `ConferenceMember`.
     *
     * @return a `MediaDIrection` which represents the status in both directions of the video
     * RTP stream from the point of view of this `ConferenceMember`
     */
    fun getVideoStatus(): MediaDirection?

    /**
     * Removes a specific `PropertyChangeListener` from the list of listeners interested in
     * and notified about changes in the values of the properties of this `ConferenceMember`
     * such as `#DISPLAY_NAME_PROPERTY_NAME` and `#STATE_PROPERTY_NAME`.
     *
     * @param listener
     * a `PropertyChangeListener` to no longer be notified about changes in the values
     * of the properties of this `ConferenceMember`
     */
    fun removePropertyChangeListener(listener: PropertyChangeListener)

    companion object {
        /**
         * The name of the property of `ConferenceMember` which specifies the SSRC of the audio
         * content/RTP stream sent by the respective `ConferenceMember` in the conference.
         */
        const val AUDIO_SSRC_PROPERTY_NAME = "audioSsrc"

        /**
         * The name of the property of `ConferenceMember` which specifies the status of the audio
         * RTP stream from the point of view of the `ConferenceMember`.
         */
        const val AUDIO_STATUS_PROPERTY_NAME = "audioStatus"

        /**
         * The name of the property of `ConferenceMember` which specifies the user-friendly
         * display name of the respective `ConferenceMember` in the conference.
         */
        const val DISPLAY_NAME_PROPERTY_NAME = "displayName"

        /**
         * The name of the property of `ConferenceMember` which specifies the state of the device
         * and signaling session of the respective `ConferenceMember` in the conference.
         */
        const val STATE_PROPERTY_NAME = "state"

        /**
         * The name of the property of `ConferenceMember` which specifies the SSRC of the video
         * content/RTP stream sent by the respective `ConferenceMember` in the conference.
         */
        const val VIDEO_SSRC_PROPERTY_NAME = "videoSsrc"

        /**
         * The name of the property of `ConferenceMember` which specifies the status of the video
         * RTP stream from the point of view of the `ConferenceMember`.
         */
        const val VIDEO_STATUS_PROPERTY_NAME = "videoStatus"
    }
}