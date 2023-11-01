/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media

import net.java.sip.communicator.service.protocol.*
import org.atalk.service.neomedia.MediaUseCase
import org.atalk.service.neomedia.QualityControl
import org.atalk.service.neomedia.QualityPreset
import org.atalk.service.neomedia.VideoMediaStream
import org.atalk.util.MediaType
import org.atalk.util.event.VideoListener
import java.awt.Component
import java.beans.PropertyChangeListener
import java.text.ParseException

/**
 * Represents a default implementation of `OperationSetVideoTelephony` in order to make it
 * easier for implementers to provide complete solutions while focusing on implementation-specific details.
 *
 * @param <T> the implementation specific telephony operation set class like for example
 * `OperationSetBasicTelephonySipImpl`.
 * @param <U> the implementation specific provider class like for example `ProtocolProviderServiceSipImpl`.
 * @param <V> the `MediaAwareCall` implementation like `CallSipImpl` or `CallJabberImpl`.
 * @param <W> the `MediaAwarePeerCall` implementation like `CallPeerSipImpl` or `CallPeerJabberImpl`.
 * @author Emil Ivov
 * @author Sebastien Vincent
</W></V></U></T> */
abstract class AbstractOperationSetVideoTelephony<
        T : OperationSetBasicTelephony<U>,
        U : ProtocolProviderService,
        V : MediaAwareCall<W, T, U>,
        W : MediaAwareCallPeer<V, *, U>>(
        /**
         * The telephony-related functionality this extension builds upon.
         */
        protected val basicTelephony: T) : OperationSetVideoTelephony {
    /**
     * The SIP `ProtocolProviderService` implementation which created this instance and for
     * which telephony conferencing services are being provided by this instance.
     */
    protected val parentProvider: U = basicTelephony.getProtocolProvider()

    /**
     * Delegates to the `CallPeerMediaHandler` of the specified `CallPeer` because the
     * video is provided by it. Because other `OperationSetVideoTelephony` implementations
     * may not provide their video through the `CallPeerMediaHandler`, this implementation
     * promotes itself as the provider of the video by replacing the `CallPeerMediaHandler`
     * in the `VideoEvents` it fires.
     *
     * @param peer the `CallPeer` that we will be registering `listener` with.
     * @param listener the `VideoListener` that we'd like to register.
     */
    // work with MediaAware* in media package
    override fun addVideoListener(peer: CallPeer, listener: VideoListener) {
        // if (listener == null) throw NullPointerException("listener")
        (peer as W).getMediaHandler().addVideoListener(listener)
    }

    /**
     * Implements [OperationSetVideoTelephony.getLocalVisualComponent].
     *
     * @param peer the `CallPeer` that we are sending our local video to.
     * @return the `Component` containing the local video.
     * @throws OperationFailedException if we fail extracting the local video.
     */
    @Throws(OperationFailedException::class)  // work with MediaAware* in media package
    override fun getLocalVisualComponent(peer: CallPeer): Component? {
        return (peer as W).getMediaHandler().localVisualComponent
    }

    /**
     * Gets the visual/video `Component` available in this telephony for a specific `CallPeer`.
     *
     * @param peer the `CallPeer` whose video is to be retrieved
     * @return the visual/video `Component` available in this telephony for the specified
     * `peer` if any; otherwise, `null`
     */
    @Deprecated("")
    override fun getVisualComponent(peer: CallPeer): Component? {
        val visualComponents = getVisualComponents(peer)
        return if (visualComponents!!.isEmpty()) null else visualComponents[0]
    }

    /**
     * Gets the visual/video `Component`s available in this telephony for a specific `CallPeer`.
     *
     * @param peer the `CallPeer` whose videos are to be retrieved
     * @return the visual/video `Component`s available in this telephony for the specified `peer`
     */
    // work with MediaAware* in media package
    override fun getVisualComponents(peer: CallPeer): List<Component?>? {
        return (peer as W).getMediaHandler().visualComponents
    }

    /**
     * Returns the `ConferenceMember` corresponding to the given `visualComponent`.
     *
     * @param peer the parent `CallPeer`
     * @param visualComponent the visual `Component`, which corresponding `ConferenceMember` we're
     * looking for
     * @return the `ConferenceMember` corresponding to the given `visualComponent`.
     */
    override fun getConferenceMember(peer: CallPeer, visualComponent: Component): ConferenceMember? {
        val w = peer as W
        val videoStream = w.getMediaHandler().getStream(MediaType.VIDEO) as VideoMediaStream?
        if (videoStream != null) {
            for (member in peer.getConferenceMembers()) {
                val memberComponent: Component? = videoStream.getVisualComponent(member.getVideoSsrc())
                if (visualComponent == memberComponent) return member
            }
        }
        return null
    }

    /**
     * Delegates to the `CallPeerMediaHandler` of the specified `CallPeer` because the
     * video is provided by it.
     *
     * @param peer the `CallPeer` that we'd like to unregister our `VideoListener` from.
     * @param listener the `VideoListener` that we'd like to unregister.
     */
    // work with MediaAware* in media package
    override fun removeVideoListener(peer: CallPeer?, listener: VideoListener?) {
        if (listener != null) (peer as W).mediaHandler.removeVideoListener(listener)
    }

    /**
     * Implements OperationSetVideoTelephony#setLocalVideoAllowed(Call, boolean). Modifies the local
     * media setup to reflect the requested setting for the streaming of the local video and then
     * re-invites all CallPeers to re-negotiate the modified media setup.
     *
     * @param call the call where we'd like to allow sending local video.
     * @param allowed `true` if local video transmission is allowed and `false` otherwise.
     * @throws OperationFailedException if video initialization fails.
     */
    @Throws(OperationFailedException::class)
    override fun setLocalVideoAllowed(call: Call<*>, allowed: Boolean) {
        val mediaAwareCall = call as MediaAwareCall<*, *, *>
        val useCase = MediaUseCase.CALL
        mediaAwareCall.setLocalVideoAllowed(allowed, useCase)
    }

    /**
     * Determines whether the streaming of local video in a specific `Call` is currently
     * allowed. The setting does not reflect the availability of actual video capture devices, it
     * just expresses the desire of the user to have the local video streamed in the case the system
     * is actually able to do so.
     *
     * @param call the `Call` whose video transmission properties we are interested in.
     * @return `true` if the streaming of local video for the specified `Call` is allowed;
     * otherwise, `false`
     */
    // work with MediaAware* in media package
    override fun isLocalVideoAllowed(call: Call<*>): Boolean {
        return (call as V).isLocalVideoAllowed(MediaUseCase.CALL)
    }

    /**
     * Determines whether a specific `Call` is currently streaming the local video (to a
     * remote destination).
     *
     * @param call the `Call` whose video transmission we are interested in.
     * @return `true` if the specified `Call` is currently streaming the local video
     * (to a remote destination); otherwise, `false`
     */
    // work with MediaAware* in media package
    override fun isLocalVideoStreaming(call: Call<*>): Boolean {
        return (call as V).isLocalVideoStreaming
    }

    /**
     * Adds a specific `PropertyChangeListener` to the list of listeners which get notified
     * when the properties (e.g. [.LOCAL_VIDEO_STREAMING]) associated with a specific
     * `Call` change their values.
     *
     * @param call the `Call` to start listening to the changes of the property values of
     * @param listener the `PropertyChangeListener` to be notified when the properties associated with
     * the specified `Call` change their values
     */
    // work with MediaAware* in media package
    override fun addPropertyChangeListener(call: Call<*>, listener: PropertyChangeListener?) {
        (call as V).addVideoPropertyChangeListener(listener)
    }

    /**
     * Removes a specific `PropertyChangeListener` from the list of listeners which get
     * notified when the properties (e.g. [.LOCAL_VIDEO_STREAMING]) associated with a specific
     * `Call` change their values.
     *
     * @param call the `Call` to stop listening to the changes of the property values of
     * @param listener the `PropertyChangeListener` to no longer be notified when the properties
     * associated with the specified `Call` change their values
     */
    // work with MediaAware* in media package
    override fun removePropertyChangeListener(call: Call<*>, listener: PropertyChangeListener?) {
        (call as V).removeVideoPropertyChangeListener(listener)
    }

    /**
     * Get the `MediaUseCase` of a video telephony operation set.
     *
     * @return `MediaUseCase.CALL`
     */
    fun getMediaUseCase(): MediaUseCase {
        return MediaUseCase.CALL
    }

    /**
     * Returns the quality control for video calls if any. Return null so protocols who supports it to override it.
     *
     * @param peer the peer which this control operates on.
     * @return the implemented quality control.
     */
    override fun getQualityControl(peer: CallPeer?): QualityControl? {
        return null
    }

    /**
     * Create a new video call and invite the specified CallPeer to it with initial video setting.
     *
     * @param uri the address of the callee that we should invite to a new call.
     * @param qualityPreferences the quality preset we will use establishing the video call, and we will expect from
     * the other side. When establishing call we don't have any indications whether remote
     * part supports quality presets, so this setting can be ignored.
     * @return CallPeer the CallPeer that will represented by the specified uri. All following state
     * change events will be delivered through that call peer. The Call that this peer is a member of
     * could be retrieved from the CallParticipatn instance with the use of the corresponding method.
     * @throws OperationFailedException with the corresponding code if we fail to create the video call.
     * @throws java.text.ParseException if `callee` is not a valid sip address string.
     */
    @Throws(OperationFailedException::class, ParseException::class)
    override fun createVideoCall(uri: String?, qualityPreferences: QualityPreset?): Call<*>? {
        return createVideoCall(uri)
    }

    /**
     * Create a new video call and invite the specified CallPeer to it with initial video setting.
     *
     * @param callee the address of the callee that we should invite to a new call.
     * @param qualityPreferences the quality preset we will use establishing the video call, and we will expect from
     * the other side. When establishing call we don't have any indications whether remote
     * part supports quality presets, so this setting can be ignored.
     * @return CallPeer the CallPeer that will represented by the specified uri. All following state
     * change events will be delivered through that call peer. The Call that this peer is a member of
     * could be retrieved from the CallParticipatn instance with the use of the corresponding method.
     * @throws OperationFailedException with the corresponding code if we fail to create the video call.
     */
    @Throws(OperationFailedException::class)
    override fun createVideoCall(callee: Contact?, qualityPreferences: QualityPreset?): Call<*>? {
        return createVideoCall(callee)
    }
}