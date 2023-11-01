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

import org.atalk.service.neomedia.RawPacket
import org.atalk.util.ArrayUtils.isNullOrEmpty
import org.atalk.util.MediaType

/**
 * Represents a collection of [RTPEncodingDesc]s that encode the same media source. This
 * specific implementation provides webrtc simulcast stream suspension detection.
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
class MediaStreamTrackDesc
/**
 * Ctor.
 *
 * @param mediaStreamTrackReceiver The [MediaStreamTrackReceiver] that receives this instance.
 * @param rtpEncodings The [RTPEncodingDesc]s that this instance possesses.
 */ @JvmOverloads constructor(
        /**
         * The [MediaStreamTrackReceiver] that receives this instance.
         */
        val mediaStreamTrackReceiver: MediaStreamTrackReceiver,
        /**
         * The [RTPEncodingDesc]s that this [MediaStreamTrackDesc]
         * possesses, ordered by their subjective quality from low to high.
         */
        val rtpEncodings: Array<RTPEncodingDesc?>,
        /**
         * A string which identifies the owner of this track (e.g. the endpoint which is the sender of the track).
         */
        val owner: String? = null) {
    /**
     * Returns an array of all the [RTPEncodingDesc]s for this instance, in subjective quality ascending order.
     *
     * @return an array of all the [RTPEncodingDesc]s for this instance, in subjective quality ascending order.
     */
    /**
     * Gets the [MediaStreamTrackReceiver] that receives this instance.
     *
     * @return The [MediaStreamTrackReceiver] that receives this instance.
     */
    /**
     * @return the identifier of the owner of this track.
     */
    /**
     * Ctor.
     *
     * @param mediaStreamTrackReceiver The [MediaStreamTrackReceiver] that receives this instance.
     * @param rtpEncodings The [RTPEncodingDesc]s that this instance possesses.
     */
    /**
     * @return the [MediaType] of this [MediaStreamTrackDesc].
     */
    val mediaType: MediaType?
        get() = mediaStreamTrackReceiver.stream.mediaType

    /**
     * Gets the last "stable" bitrate (in bps) of the encoding of the specified index. The
     * "stable" bitrate is measured on every new frame and with a 5000ms window.
     *
     * to have fresh data and not just its active property to be set to true.
     *
     * @return the last "stable" bitrate (bps) of the encoding at the specified index.
     */
    fun getBps(idx: Int): Long {
        if (isNullOrEmpty(rtpEncodings)) {
            return 0
        }
        if (idx > -1) {
            val nowMs = System.currentTimeMillis()
            for (i in idx downTo -1 + 1) {
                val bps = rtpEncodings[i]!!.getLastStableBitrateBps(nowMs)
                if (bps > 0) {
                    return bps
                }
            }
        }
        return 0
    }

    /**
     * Finds the [RTPEncodingDesc] that corresponds to the packet that is
     * passed in as an argument. Assumes that the packet is valid.
     *
     * @param pkt the packet to match.
     * @return the [RTPEncodingDesc] that corresponds to the packet that is
     * specified in the buffer passed in as an argument, or null.
     */
    fun findRTPEncodingDesc(pkt: RawPacket): RTPEncodingDesc? {
        if (isNullOrEmpty(rtpEncodings)) {
            return null
        }
        for (encoding in rtpEncodings) {
            if (encoding!!.matches(pkt)) {
                return encoding
            }
        }
        return null
    }

    override fun toString(): String {
        val sb = StringBuilder()
        if (!isNullOrEmpty<RTPEncodingDesc?>(rtpEncodings)) {
            for (encoding in rtpEncodings) {
                sb.append(" ").append(encoding)
            }
        }
        return sb.toString()
    }

    /**
     * Finds the [RTPEncodingDesc] that corresponds to the specified `ssrc`.
     *
     * @param ssrc the SSRC of the [RTPEncodingDesc] to find. If multiple
     * encodings share the same SSRC, the first match will be returned.
     * @return the [RTPEncodingDesc] that corresponds to the specified `ssrc`.
     */
    fun findRTPEncodingDesc(ssrc: Long): RTPEncodingDesc? {
        if (isNullOrEmpty(rtpEncodings)) {
            return null
        }
        for (encoding in rtpEncodings) {
            if (encoding!!.matches(ssrc)) {
                return encoding
            }
        }
        return null
    }

    /**
     * FIXME: this should probably check whether the specified SSRC is part
     * of this track (i.e. check all encodings and include secondary SSRCs).
     *
     * @param ssrc the SSRC to match.
     * @return `true` if the specified `ssrc` is the primary SSRC for this track.
     */
    fun matches(ssrc: Long): Boolean {
        return rtpEncodings[0]!!.primarySSRC == ssrc
    }
}