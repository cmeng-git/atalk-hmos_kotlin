/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import org.atalk.service.neomedia.QualityControl
import org.atalk.service.neomedia.QualityPreset
import org.atalk.util.event.VideoListener
import java.awt.Component
import java.beans.PropertyChangeListener
import java.text.ParseException

/**
 * Represents an `OperationSet` giving access to video-specific functionality in telephony such as
 * visual `Component`s displaying video and listening to dynamic availability of such `Component`s.
 *
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
interface OperationSetVideoTelephony : OperationSet {
    /**
     * Adds a specific `VideoListener` to this telephony in order to receive notifications
     * when visual/video `Component`s are being added and removed for a specific `CallPeer`.
     *
     * @param peer the `CallPeer` whose video the specified listener is to be notified about
     * @param listener the `VideoListener` to be notified when visual/video `Component`s are
     * being added or removed for `peer`
     */
    fun addVideoListener(peer: CallPeer, listener: VideoListener)

    /**
     * Gets the visual `Component` which depicts the local video being streamed to a specific `CallPeer`.
     *
     * @param peer the `CallPeer` to whom the local video which is to be depicted by the returned
     * visual `Component` is being streamed
     * @return a visual `Component` which depicts the local video being streamed to the
     * specified `CallPeer` if this telephony chooses to carry out the creation
     * synchronously; `null` if this telephony chooses to create the requested visual
     * `Component` asynchronously
     * @throws OperationFailedException if creating the component fails for whatever reason.
     */
    @Throws(OperationFailedException::class)
    fun getLocalVisualComponent(peer: CallPeer): Component?

    /**
     * Gets the visual/video `Component` available in this telephony for a specific
     * `CallPeer`.
     *
     * @param peer the `CallPeer` whose video is to be retrieved
     * @return the visual/video `Component` available in this telephony for the specified
     * `peer` if any; otherwise, `null`
     */
    @Deprecated("")
    fun getVisualComponent(peer: CallPeer): Component?

    /**
     * Gets the visual/video `Component`s available in this telephony for a specific `CallPeer`.
     *
     * @param peer the `CallPeer` whose videos are to be retrieved
     * @return the visual/video `Component`s available in this telephony for the specified `peer`
     */
    fun getVisualComponents(peer: CallPeer): List<Component?>?

    /**
     * Removes a specific `VideoListener` from this telephony in order to no longer have it
     * receive notifications when visual/video `Component`s are being added and removed for a
     * specific `CallPeer`.
     *
     * @param peer the `CallPeer` whose video the specified listener is to no longer be notified about
     * @param listener the `VideoListener` to no longer be notified when visual/video
     * `Component`s are being added or removed for `peer`
     */
    fun removeVideoListener(peer: CallPeer?, listener: VideoListener?)

    /**
     * Sets the indicator which determines whether the streaming of local video in a specific
     * `Call` is allowed. The setting does not reflect the availability of actual video
     * capture devices, it just expresses the desire of the user to have the local video streamed in
     * the case the system is actually able to do so.
     *
     * @param call the `Call` to allow/disallow the streaming of local video for
     * @param allowed `true` to allow the streaming of local video for the specified `Call`;
     * `false` to disallow it
     * @throws OperationFailedException if initializing local video fails.
     */
    @Throws(OperationFailedException::class)
    fun setLocalVideoAllowed(call: Call<*>, allowed: Boolean)

    /**
     * Gets the indicator which determines whether the streaming of local video in a specific
     * `Call` is allowed. The setting does not reflect the availability of actual video
     * capture devices, it just expresses the desire of the user to have the local video streamed in
     * the case the system is actually able to do so.
     *
     * @param call the `Call` to get the indicator of
     * @return `true` if the streaming of local video for the specified `Call` is
     * allowed; otherwise, `false`
     */
    fun isLocalVideoAllowed(call: Call<*>): Boolean

    /**
     * Gets the indicator which determines whether a specific `Call` is currently streaming
     * the local video (to a remote destination).
     *
     * @param call the `Call` to get the indicator of
     * @return `true` if the specified `Call` is currently streaming the local video
     * (to a remote destination); otherwise, `false`
     */
    fun isLocalVideoStreaming(call: Call<*>): Boolean

    /**
     * Adds a specific `PropertyChangeListener` to the list of listeners which get notified
     * when the properties (e.g. [.LOCAL_VIDEO_STREAMING]) associated with a specific
     * `Call` change their values.
     *
     * @param call the `Call` to start listening to the changes of the property values of
     * @param listener the `PropertyChangeListener` to be notified when the properties associated with
     * the specified `Call` change their values
     */
    fun addPropertyChangeListener(call: Call<*>, listener: PropertyChangeListener?)

    /**
     * Removes a specific `PropertyChangeListener` from the list of listeners which get
     * notified when the properties (e.g. [.LOCAL_VIDEO_STREAMING]) associated with a specific
     * `Call` change their values.
     *
     * @param call the `Call` to stop listening to the changes of the property values of
     * @param listener the `PropertyChangeListener` to no longer be notified when the properties
     * associated with the specified `Call` change their values
     */
    fun removePropertyChangeListener(call: Call<*>, listener: PropertyChangeListener?)

    /**
     * Create a new video call and invite the specified CallPeer to it.
     *
     * @param uri the address of the callee that we should invite to a new call.
     * @return CallPeer the CallPeer that will represented by the specified uri. All following state
     * change events will be delivered through that call peer. The Call that this peer is a
     * member of could be retrieved from the CallParticipatn instance with the use of the
     * corresponding method.
     * @throws OperationFailedException with the corresponding code if we fail to create the video call.
     * @throws ParseException if `callee` is not a valid sip address string.
     */
    @Throws(OperationFailedException::class, ParseException::class)
    fun createVideoCall(uri: String?): Call<*>?

    /**
     * Create a new video call and invite the specified CallPeer to it.
     *
     * @param callee the address of the callee that we should invite to a new call.
     * @return CallPeer the CallPeer that will represented by the specified uri. All following state
     * change events will be delivered through that call peer. The Call that this peer is a
     * member of could be retrieved from the CallParticipatn instance with the use of the
     * corresponding method.
     * @throws OperationFailedException with the corresponding code if we fail to create the video call.
     */
    @Throws(OperationFailedException::class)
    fun createVideoCall(callee: Contact?): Call<*>?

    /**
     * Create a new video call and invite the specified CallPeer to it.
     *
     * @param uri the address of the callee that we should invite to a new call.
     * @param qualityPreferences the quality preset we will use establishing the video call, and we will expect from
     * the other side. When establishing call we don't have any indications whether remote
     * part supports quality presets, so this setting can be ignored.
     * @return CallPeer the CallPeer that will represented by the specified uri. All following state
     * change events will be delivered through that call peer. The Call that this peer is a
     * member of could be retrieved from the CallParticipatn instance with the use of the
     * corresponding method.
     * @throws OperationFailedException with the corresponding code if we fail to create the video call.
     * @throws ParseException if `callee` is not a valid sip address string.
     */
    @Throws(OperationFailedException::class, ParseException::class)
    fun createVideoCall(uri: String?, qualityPreferences: QualityPreset?): Call<*>?

    /**
     * Create a new video call and invite the specified CallPeer to it.
     *
     * @param callee the address of the callee that we should invite to a new call.
     * @param qualityPreferences the quality preset we will use establishing the video call, and we will expect from
     * the other side. When establishing call we don't have any indications whether remote
     * part supports quality presets, so this setting can be ignored.
     * @return CallPeer the CallPeer that will represented by the specified uri. All following state
     * change events will be delivered through that call peer. The Call that this peer is a
     * member of could be retrieved from the CallParticipatn instance with the use of the
     * corresponding method.
     * @throws OperationFailedException with the corresponding code if we fail to create the video call.
     */
    @Throws(OperationFailedException::class)
    fun createVideoCall(callee: Contact?, qualityPreferences: QualityPreset?): Call<*>?

    /**
     * Indicates a user request to answer an incoming call with video enabled from the specified CallPeer.
     *
     * @param peer the call peer that we'd like to answer.
     * @throws OperationFailedException with the corresponding code if we encounter an error while performing this operation.
     */
    @Throws(OperationFailedException::class)
    fun answerVideoCallPeer(peer: CallPeer?)

    /**
     * Returns the quality control for video calls if any. It can be null if we were able to
     * successfully determine that other party does not support it.
     *
     * @param peer the peer which this control operates on.
     * @return the implemented quality control.
     */
    fun getQualityControl(peer: CallPeer?): QualityControl?

    /**
     * Determines the `ConferenceMember` which is participating in a telephony conference
     * with a specific `CallPeer` as its focus and which is sending a video content/RTP
     * stream displayed in a specific visual `Component`.
     *
     * @param peer the `CallPeer` which is the conference focus of the telephony conference to be
     * examined in order to locate the `ConferenceMember` which is sending the video
     * content/RTP stream displayed in the specified `visualComponent`
     * @param visualComponent the visual `Component` which displays the video content/RTP stream of the
     * `ConferenceMember` to be located
     * @return the `ConferenceMember`, if any, which is sending the video content/RTP stream
     * displayed in the specific `visualComponent`
     */
    fun getConferenceMember(peer: CallPeer, visualComponent: Component): ConferenceMember?

    companion object {
        /**
         * The property which indicates whether a specific `Call` is currently streaming the
         * local video (to a remote destination).
         */
        const val LOCAL_VIDEO_STREAMING = "LOCAL_VIDEO_STREAMING"
    }
}