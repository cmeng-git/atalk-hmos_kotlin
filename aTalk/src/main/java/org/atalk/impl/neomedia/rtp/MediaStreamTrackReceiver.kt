/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.rtp

import org.atalk.impl.neomedia.MediaStreamImpl
import org.atalk.impl.neomedia.RTPPacketPredicate
import org.atalk.impl.neomedia.transform.PacketTransformer
import org.atalk.impl.neomedia.transform.SinglePacketTransformerAdapter
import org.atalk.impl.neomedia.transform.TransformEngine
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.RawPacket
import org.atalk.util.ArrayUtils.isNullOrEmpty

/**
 * This class is inserted in the receive transform chain and it updates the
 * [MediaStreamTrackDesc]s that is configured to receive.
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
open class MediaStreamTrackReceiver
/**
 * Ctor.
 *
 * @param stream The [MediaStream] that this instance receives
 * [MediaStreamTrackDesc]s from.
 */
(
        /**
         * The [MediaStreamImpl] that owns this instance.
         */
        val stream: MediaStreamImpl) : SinglePacketTransformerAdapter(RTPPacketPredicate), TransformEngine {
    /**
     * Gets the `RtpChannel` that owns this instance.
     *
     * @return the `RtpChannel` that owns this instance.
     */

    /**
     * The [MediaStreamTrackDesc]s that this instance is configured to receive.
     */
    private var tracks = NO_TRACKS

    /**
     * Finds the [RTPEncodingDesc] that matches the [RawPacket]
     * passed in as a parameter. Assumes that the packet is valid.
     *
     * @param pkt the packet to match.
     * @return the [RTPEncodingDesc] that matches the pkt passed in as
     * a parameter, or null if there is no matching [RTPEncodingDesc].
     */
    fun findRTPEncodingDesc(pkt: RawPacket): RTPEncodingDesc? {
        val localTracks = tracks
        if (isNullOrEmpty(localTracks)) {
            return null
        }
        for (track in localTracks) {
            val encoding = track!!.findRTPEncodingDesc(pkt)
            if (encoding != null) {
                return encoding
            }
        }
        return null
    }

    /**
     * Finds the [RTPEncodingDesc] that matches ByteArrayBuffer
     * passed in as a parameter.
     *
     * @param ssrc the SSRC of the [RTPEncodingDesc] to match. If multiple
     * encodings share the same SSRC, the first match will be returned.
     * @return the [RTPEncodingDesc] that matches the pkt passed in as
     * a parameter, or null if there is no matching [RTPEncodingDesc].
     */
    fun findRTPEncodingDesc(ssrc: Long): RTPEncodingDesc? {
        val localTracks = tracks
        if (isNullOrEmpty(localTracks)) {
            return null
        }
        for (track in localTracks) {
            val encoding = track!!.findRTPEncodingDesc(ssrc)
            if (encoding != null) {
                return encoding
            }
        }
        return null
    }

    /**
     * {@inheritDoc}
     */
    override val rtpTransformer: PacketTransformer
        get() = this

    /**
     * {@inheritDoc}
     */
    override val rtcpTransformer: PacketTransformer?
        get() = null

    /**
     * Gets the [MediaStreamTrackDesc]s that this instance is configured to receive.
     *
     * @return the [MediaStreamTrackDesc]s that this instance is configured to receive.
     */
    val mediaStreamTracks: Array<MediaStreamTrackDesc?>
        get() = if (tracks == null) NO_TRACKS else tracks

    /**
     * Updates this [MediaStreamTrackReceiver] with the new RTP encoding
     * parameters. Note that in order to avoid losing the state of existing
     * [MediaStreamTrackDesc] instances, when one of the new instances
     * matches (i.e. the primary SSRC of its first encoding matches) an old
     * instance we keep the old instance.
     * Currently we also keep the old instance's configuration
     * (TODO use the new configuration).
     *
     * @param nTracks the [MediaStreamTrackDesc]s that this instance
     * will receive. Note that the actual [MediaStreamTrackDesc] instances
     * might not match. To get the actual instances call
     * [.getMediaStreamTracks].
     * @return true if the MSTs have changed, otherwise false.
     */
    fun setMediaStreamTracks(nTracks: Array<MediaStreamTrackDesc?>?): Boolean {
        var newTracks = nTracks
        if (newTracks == null) {
            newTracks = NO_TRACKS
        }
        val oldTracks = tracks
        val oldTracksLen = oldTracks?.size ?: 0
        val newTracksLen = newTracks.size ?: 0
        return if (oldTracksLen == 0 || newTracksLen == 0) {
            tracks = newTracks
            oldTracksLen != newTracksLen
        } else {
            var cntMatched = 0
            val mergedTracks = arrayOfNulls<MediaStreamTrackDesc>(newTracks.size)
            for (i in newTracks.indices) {
                val newEncoding = newTracks[i]!!.rtpEncodings[0]
                for (oldTrack in oldTracks!!) {
                    if (oldTrack != null && oldTrack.matches(newEncoding!!.primarySSRC)) {
                        mergedTracks[i] = oldTrack
                        cntMatched++
                        break
                    }
                }
                if (mergedTracks[i] == null) {
                    mergedTracks[i] = newTracks[i]
                }
            }
            tracks = mergedTracks
            oldTracksLen != newTracksLen || cntMatched != oldTracks!!.size
        }
    }

    /**
     * Finds the [MediaStreamTrackDesc] that corresponds to the SSRC that
     * is specified in the arguments.
     *
     * @param ssrc the SSRC of the [MediaStreamTrackDesc] to match.
     * @return the [MediaStreamTrackDesc] that matches the specified SSRC.
     */
    fun findMediaStreamTrackDesc(ssrc: Long): MediaStreamTrackDesc? {
        val localTracks = tracks
        if (isNullOrEmpty(localTracks)) {
            return null
        }
        for (track in localTracks!!) {
            if (track!!.matches(ssrc)) {
                return track
            }
        }
        return null
    }

    companion object {
        /**
         * An empty array of [MediaStreamTrackDesc].
         */
        private val NO_TRACKS = arrayOfNulls<MediaStreamTrackDesc>(0)
    }
}