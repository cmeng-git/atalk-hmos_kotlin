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
import org.ice4j.util.RateStatistics
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps track of how many channels receive it, its subjective quality index,
 * its last stable bitrate and other useful things for adaptivity/routing.
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
class RTPEncodingDesc(
        /**
         * The [MediaStreamTrackDesc] that this [RTPEncodingDesc] belongs to.
         */
        private val mediaStreamTrack: MediaStreamTrackDesc,
        /**
         * The index of this instance in the track encodings array.
         */
        val index: Int,
        /**
         * The primary SSRC for this layering/encoding.
         */
        val primarySSRC: Long,
        /**
         * The temporal layer ID of this instance.
         */
        private val tid: Int,
        /**
         * The spatial layer ID of this instance.
         */
        private val sid: Int,
        /**
         * The max height of the bitstream that this instance represents. The actual
         * height may be less due to bad network or system load.
         */
        val height: Int,
        /**
         * The max frame rate (in fps) of the bitstream that this instance
         * represents. The actual frame rate may be less due to bad network or system load.
         */
        val frameRate: Double,
        /**
         * The [RTPEncodingDesc] on which this layer depends.
         */
        private val dependencyEncodings: Array<RTPEncodingDesc>?) {
    /**
     * Gets the primary SSRC for this layering/encoding.
     *
     * @return the primary SSRC for this layering/encoding.
     */

    /**
     * The ssrcs associated with this encoding (for example, RTX or FLEXFEC)
     * Maps ssrc -> type Constants (rtx, etc.)
     */
    private val secondarySsrcs = HashMap<Long, String>()
    /**
     * Gets the subjective quality index of this instance.
     *
     * @return the subjective quality index of this instance.
     */

    /**
     * Gets the max height of the bitstream that this instance represents.
     *
     * @return the max height of the bitstream that this instance represents.
     */
    /**
     * Gets the max frame rate (in fps) of the bitstream that this instance represents.
     *
     * @return the max frame rate (in fps) of the bitstream that this instance represents.
     */
    /**
     * Gets the root [RTPEncodingDesc] of the dependencies DAG. Useful for simulcast handling.
     *
     * @return the root [RTPEncodingDesc] of the dependencies DAG. Useful for simulcast handling.
     */
    /**
     * The root [RTPEncodingDesc] of the dependencies DAG. Useful for simulcast handling.
     */
    private var baseLayer: RTPEncodingDesc? = null
    /**
     * Gets the [MediaStreamTrackDesc] that this instance belongs to.
     *
     * @return the [MediaStreamTrackDesc] that this instance belongs to.
     */

    /**
     * The [RateStatistics] instance used to calculate the receiving bitrate of this RTP encoding.
     */
    private val rateStatistics = RateStatistics(AVERAGE_BITRATE_WINDOW_MS)

    /**
     * The number of receivers for this encoding.
     */
    private val numOfReceivers = AtomicInteger()

    /**
     * Ctor.
     *
     * @param track the [MediaStreamTrackDesc] that this instance belongs to.
     * @param primarySSRC The primary SSRC for this layering/encoding.
     */
    constructor(track: MediaStreamTrackDesc, primarySSRC: Long) : this(track, 0, primarySSRC, -1, -1, NO_HEIGHT, NO_FRAME_RATE, null)

    /**
     * Ctor.
     *
     * track the MediaStreamTrackDesc that this instance belongs to.
     * idx the subjective quality index for this
     * layering/encoding.
     * primarySSRC The primary SSRC for this layering/encoding.
     * tid temporal layer ID for this layering/encoding.
     * sid spatial layer ID for this layering/encoding.
     * height the max height of this encoding
     * frameRate the max frame rate (in fps) of this encoding
     * dependencyEncodings The RTPEncodingDesc on which this layer depends.
     */
    init {
        // XXX we should be able to snif the actual height from the RTP packets.
        baseLayer = if (isNullOrEmpty(dependencyEncodings)) {
            this
        } else {
            dependencyEncodings!![0].baseLayer
        }
    }

    fun addSecondarySsrc(ssrc: Long, type: String) {
        secondarySsrcs[ssrc] = type
    }

    /**
     * Gets the last stable bitrate (in bps) for this instance.
     *
     * @return The last stable bitrate (in bps) for this instance.
     */
    fun getLastStableBitrateBps(nowMs: Long): Long {
        return rateStatistics.getRate(nowMs)
    }

    /**
     * Get the secondary ssrc for this stream that corresponds to the given type
     *
     * @param type the type of the secondary ssrc (e.g. RTX)
     * @return the ssrc for the stream that corresponds to the given type,
     * if it exists; otherwise -1
     */
    fun getSecondarySsrc(type: String): Long {
        for ((key, value) in secondarySsrcs) {
            if (value == type) {
                return key
            }
        }
        return -1
    }

    /**
     * {@inheritDoc}
     */
    override fun toString(): String {
        return "subjective_quality=" + index +
                ",primary_ssrc=" + primarySSRC +
                ",secondary_ssrcs=" + secondarySsrcs +
                ",temporal_id=" + tid +
                ",spatial_id=" + sid
    }

    /**
     * Returns a boolean that indicates whether or not this
     * [RTPEncodingDesc] depends on the subjective quality index that is passed as an argument.
     *
     * @param idx the index of this instance in the track encodings array.
     * @return true if this [RTPEncodingDesc] depends on the subjective
     * quality index that is passed as an argument, false otherwise.
     */
    fun requires(idx: Int): Boolean {
        if (idx < 0) {
            return false
        }
        if (idx == index) {
            return true
        }
        var requires = false
        if (!isNullOrEmpty(dependencyEncodings)) {
            for (enc in dependencyEncodings!!) {
                if (enc.requires(idx)) {
                    requires = true
                    break
                }
            }
        }
        return requires
    }

    /**
     * Gets a boolean indicating whether or not the specified packet matches
     * this encoding or not. Assumes that the packet is valid.
     *
     * @param pkt the RTP packet.
     */
    fun matches(pkt: RawPacket): Boolean {
        val ssrc = pkt.getSSRCAsLong()
        if (!matches(ssrc)) {
            return false
        }
        if (tid == -1 && sid == -1) {
            return true
        }
        val tid = if (tid != -1) mediaStreamTrack.mediaStreamTrackReceiver.stream.getTemporalID(pkt) else -1
        val sid = if (sid != -1) mediaStreamTrack.mediaStreamTrackReceiver.stream.getSpatialID(pkt) else -1
        return (tid == -1 && sid == -1 && index == 0 || tid == this.tid) && sid == this.sid
    }

    /**
     * Gets a boolean indicating whether or not the SSRC specified in the
     * arguments matches this encoding or not.
     *
     * @param ssrc the SSRC to match.
     */
    fun matches(ssrc: Long): Boolean {
        return primarySSRC == ssrc || secondarySsrcs.containsKey(ssrc)
    }

    /**
     * @param pkt
     * @param nowMs
     */
    fun update(pkt: RawPacket?, nowMs: Long) {
        // Update rate stats (this should run after padding termination).
        rateStatistics.update(pkt!!.length, nowMs)
    }

    /**
     * Gets the cumulative bitrate (in bps) of this [RTPEncodingDesc] and its dependencies.
     *
     * @param nowMs
     * @return the cumulative bitrate (in bps) of this [RTPEncodingDesc] and its dependencies.
     */
    private fun getBitrateBps(nowMs: Long): Long {
        val encodings = mediaStreamTrack.rtpEncodings
        if (isNullOrEmpty(encodings)) {
            return 0
        }
        val rates = LongArray(encodings.size)
        getBitrateBps(nowMs, rates)
        var bitrate = 0L
        for (rate in rates) {
            bitrate += rate
        }
        return bitrate
    }

    /**
     * Recursively adds the bitrate (in bps) of this [RTPEncodingDesc] and
     * its dependencies in the array passed in as an argument.
     *
     * @param nowMs
     */
    private fun getBitrateBps(nowMs: Long, rates: LongArray) {
        if (rates[index] == 0L) {
            rates[index] = rateStatistics.getRate(nowMs)
        }
        if (!isNullOrEmpty(dependencyEncodings)) {
            for (dependency in dependencyEncodings!!) {
                dependency.getBitrateBps(nowMs, rates)
            }
        }
    }

    /**
     * Gets the number of receivers for this encoding.
     *
     * @return the number of receivers for this encoding.
     */
    val isReceived: Boolean
        get() = numOfReceivers.get() > 0

    companion object {
        /**
         * The quality that is used to represent that forwarding is suspended.
         */
        const val SUSPENDED_INDEX = -1

        /**
         * A value used to designate the absence of height information.
         */
        private const val NO_HEIGHT = -1

        /**
         * A value used to designate the absence of frame rate information.
         */
        private const val NO_FRAME_RATE = -1.0

        /**
         * The default window size in ms for the bitrate estimation.
         *
         * TODO maybe make this configurable.
         */
        private const val AVERAGE_BITRATE_WINDOW_MS = 5000

        /**
         * The number of incoming frames to keep track of.
         */
        private const val FRAMES_HISTORY_SZ = 60

        /**
         * The maximum time interval (in millis) an encoding can be considered
         * active without new frames. This value corresponds to 4fps + 50 millis
         * to compensate for network noise. If the network is clogged and we don't
         * get a new frame within 300 millis, and if the encoding is being
         * received, then we will ask for a new key frame (this is done in the
         * JVB in SimulcastController).
         */
        private const val SUSPENSION_THRESHOLD_MS = 300
    }
}