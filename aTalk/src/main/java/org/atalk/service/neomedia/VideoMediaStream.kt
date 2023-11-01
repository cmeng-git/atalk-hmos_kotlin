/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import org.atalk.service.neomedia.control.KeyFrameControl
import org.atalk.service.neomedia.rtp.BandwidthEstimator
import org.atalk.util.event.VideoListener
import java.awt.Component

/**
 * Extends the `MediaStream` interface and adds methods specific to video streaming.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface VideoMediaStream : MediaStream {
    /**
     * Adds a specific `VideoListener` to this `VideoMediaStream` in order to receive
     * notifications when visual/video `Component`s are being added and removed.
     *
     *
     * Adding a listener which has already been added does nothing i.e. it is not added more than
     * once and thus does not receive one and the same `VideoEvent` multiple times
     *
     *
     * @param listener the `VideoListener` to be notified when visual/video `Component`s are
     * being added or removed in this `VideoMediaStream`
     */
    fun addVideoListener(listener: VideoListener)

    /**
     * Gets the `KeyFrameControl` of this `VideoMediaStream`.
     *
     * @return the `KeyFrameControl` of this `VideoMediaStream`
     */
    val keyFrameControl: KeyFrameControl?

    /**
     * Gets the visual `Component`, if any, depicting the video streamed from the local peer
     * to the remote peer.
     *
     * @return the visual `Component` depicting the local video if local video is actually
     * being streamed from the local peer to the remote peer; otherwise, `null`
     */
    val localVisualComponent: Component?

    /**
     * Gets the `QualityControl` of this `VideoMediaStream`.
     *
     * @return the `QualityControl` of this `VideoMediaStream`
     */
    val qualityControl: QualityControl?

    /**
     * Gets the visual `Component` where video from the remote peer is being rendered or
     * `null` if no video is currently being rendered.
     *
     * @return the visual `Component` where video from the remote peer is being rendered or
     * `null` if no video is currently being rendered
     */
    @get:Deprecated("""Since multiple videos may be received from the remote peer and rendered, it is
      not clear which one of them is to be singled out as the return value. Thus
      {@link #getVisualComponent(long)} and {@link #getVisualComponents()} are to be used instead.""")

    val visualComponent: Component?

    /**
     * Gets the visual `Component` rendering the `ReceiveStream` with a specific SSRC.
     *
     * @param ssrc the SSRC of the `ReceiveStream` to get the associated rendering visual `Component` of
     * @return the visual `Component` rendering the `ReceiveStream` with the specified
     * `ssrc` if any; otherwise, `null`
     */
    fun getVisualComponent(ssrc: Long): Component?

    /**
     * Gets a list of the visual `Component`s where video from the remote peer is being rendered.
     *
     * @return a list of the visual `Component`s where video from the remote peer is being rendered
     */
    val visualComponents: List<Component>?

    /**
     * Move origin of a partial desktop streaming `MediaDevice`.
     *
     * @param x new x coordinate origin
     * @param y new y coordinate origin
     */
    fun movePartialDesktopStreaming(x: Int, y: Int)

    /**
     * Removes a specific `VideoListener` from this `VideoMediaStream` in order to have to
     * no longer receive notifications when visual/video `Component`s are being added and removed.
     *
     * @param listener the `VideoListener` to no longer be notified when visual/video
     * `Component`s are being added or removed in this `VideoMediaStream`
     */
    fun removeVideoListener(listener: VideoListener?)

    /**
     * Updates the `QualityControl` of this `VideoMediaStream`.
     *
     * @param advancedParams parameters of advanced attributes that may affect quality control
     */
    fun updateQualityControl(advancedParams: Map<String, String>?)

    /**
     * Creates an instance of [BandwidthEstimator] for this [MediaStream] if one doesn't
     * already exist. Returns the instance.
     */
    val orCreateBandwidthEstimator: BandwidthEstimator?

    companion object {
        /**
         * The name of the property used to control whether [VideoMediaStream] should request
         * retransmissions for lost RTP packets using RTCP NACK.
         */
        val REQUEST_RETRANSMISSIONS_PNAME = VideoMediaStream::class.java.name + ".REQUEST_RETRANSMISSIONS"
    }
}