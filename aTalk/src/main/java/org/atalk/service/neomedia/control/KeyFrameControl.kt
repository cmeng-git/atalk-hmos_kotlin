/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.control

/**
 * Represents a control over the key frame-related logic of a `VideoMediaStream`.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface KeyFrameControl {
    /**
     * Adds a `KeyFrameRequestee` to be made available through this `KeyFrameControl`.
     *
     * @param index the zero-based index at which `keyFrameRequestee` is to be added to the list of
     * `KeyFrameRequestee`s made available or `-1` to have this
     * `KeyFrameControl` choose at which index it is to be added in accord with its
     * internal logic through this `KeyFrameControl`
     * @param keyFrameRequestee the `KeyFrameRequestee` to be added to this `KeyFrameControl` so that it
     * is made available through it
     */
    fun addKeyFrameRequestee(index: Int, keyFrameRequestee: KeyFrameRequestee)

    /**
     * Adds a `KeyFrameRequester` to be made available through this `KeyFrameControl`.
     *
     * @param index the zero-based index at which `keyFrameRequester` is to be added to the list of
     * `KeyFrameRequester`s made available or `-1` to have this
     * `KeyFrameControl` choose at which index it is to be added in accord with its
     * internal logic through this `KeyFrameControl`
     * @param keyFrameRequester the `KeyFrameRequester` to be added to this `KeyFrameControl` so that it
     * is made available through it
     */
    fun addKeyFrameRequester(index: Int, keyFrameRequester: KeyFrameRequester)

    /**
     * Gets the `KeyFrameRequestee`s made available through this `KeyFrameControl`.
     *
     * @return an unmodifiable list of `KeyFrameRequestee`s made available through this `KeyFrameControl`
     */
    fun getKeyFrameRequestees(): List<KeyFrameRequestee>

    /**
     * Gets the `KeyFrameRequester`s made available through this `KeyFrameControl`.
     *
     * @return an unmodifiable list of `KeyFrameRequester`s made available through this `KeyFrameControl`
     */
    fun getKeyFrameRequesters(): List<KeyFrameRequester>

    /**
     * Notifies this `KeyFrameControl` that the remote peer of the associated
     * `VideoMediaStream` has requested a key frame from the local peer.
     *
     * @return `true` if the local peer has honored the request from the remote peer for a key frame; otherwise, `false`
     */
    fun keyFrameRequest(): Boolean

    /**
     * Removes a `KeyFrameRequestee` to no longer be made available through this `KeyFrameControl`.
     *
     * @param keyFrameRequestee the `KeyFrameRequestee` to be removed from this `KeyFrameControl` so
     * that it is no longer made available through it
     * @return `true` if `keyFrameRequestee` was found in this `KeyFrameControl`; otherwise, `false`
     */
    fun removeKeyFrameRequestee(keyFrameRequestee: KeyFrameRequestee): Boolean

    /**
     * Removes a `KeyFrameRequester` to no longer be made available through this `KeyFrameControl`.
     *
     * @param keyFrameRequester the `KeyFrameRequester` to be removed from this `KeyFrameControl` so
     * that it is no longer made available through it
     * @return `true` if `keyFrameRequester` was found in this
     * `KeyFrameControl`; otherwise, `false`
     */
    fun removeKeyFrameRequester(keyFrameRequester: KeyFrameRequester): Boolean

    /**
     * Requests a key frame from the remote peer of the associated `VideoMediaStream`.
     *
     * @param urgent `true` if the caller has determined that the need for a key frame is urgent and
     * should not obey all constraints with respect to time between two subsequent requests for key frames
     * @return `true` if a key frame was indeed requested from the remote peer of the
     * associated `VideoMediaStream` in response to the call; otherwise, `false`
     */
    fun requestKeyFrame(urgent: Boolean): Boolean

    /**
     * Represents a way for the remote peer of a `VideoMediaStream` to request a key frame from its local peer.
     */
    interface KeyFrameRequestee {
        /**
         * Notifies this `KeyFrameRequestee` that the remote peer of the associated
         * `VideoMediaStream` requests a key frame from the local peer.
         *
         * @return `true` if this `KeyFrameRequestee` has honored the request for a key frame; otherwise, `false`
         */
        fun keyFrameRequest(): Boolean
    }

    /**
     * Represents a way for a `VideoMediaStream` to request a key frame from its remote peer.
     */
    interface KeyFrameRequester {
        /**
         * Requests a key frame from the remote peer of the associated `VideoMediaStream`.
         *
         * @return `true` if this `KeyFrameRequester` has indeed requested a key frame from the
         * remote peer of the associated `VideoMediaStream` in response to the call; otherwise, `false`
         */
        fun requestKeyFrame(): Boolean

        companion object {
            /**
             * The name of the `ConfigurationService` property which specifies the preferred
             * `KeyFrameRequester` to be used.
             */
            const val PREFERRED_PNAME = "neomedia.codec.video.h264.preferredKeyFrameRequester"

            /**
             * The value of the [.PREFERRED_PNAME] `ConfigurationService` property which
             * indicates that the RTCP `KeyFrameRequester` is preferred.
             */
            const val RTCP = "rtcp"

            /**
             * The value of the [.PREFERRED_PNAME] `ConfigurationService` property which
             * indicates that the signaling/protocol `KeyFrameRequester` is preferred.
             */
            const val SIGNALING = "signaling"

            /**
             * The default value of the [.PREFERRED_PNAME] `ConfigurationService` property.
             */
            const val DEFAULT_PREFERRED = RTCP
        }
    }
}