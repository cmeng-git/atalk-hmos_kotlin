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
package org.atalk.impl.neomedia.rtp.remotebitrateestimator

import org.atalk.impl.neomedia.transform.AbsSendTimeEngine.Companion.getAbsSendTime
import org.atalk.impl.neomedia.transform.PacketTransformer
import org.atalk.impl.neomedia.transform.SinglePacketTransformerAdapter
import org.atalk.impl.neomedia.transform.TransformEngine
import org.atalk.service.neomedia.RawPacket
import org.atalk.service.neomedia.rtp.RemoteBitrateEstimator
import org.atalk.util.logging.DiagnosticContext

/**
 * This is the receive-side remote bitrate estimator. If REMB support has not
 * been signaled or if TCC support has been signaled, it's going to be disabled.
 *
 * If it's enabled, then if AST has been signaled it's going to use the
 * [RemoteBitrateEstimatorAbsSendTime] otherwise it's going to use the
 * [RemoteBitrateEstimatorSingleStream].
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
class RemoteBitrateEstimatorWrapper(
        /**
         * The observer to notify on bitrate estimation changes.
         */
        private val observer: RemoteBitrateObserver,
        /**
         * The [DiagnosticContext] for this instance.
         */
        private val diagnosticContext: DiagnosticContext) : SinglePacketTransformerAdapter(), RemoteBitrateEstimator, TransformEngine {
    /**
     * Determines the minimum bitrate (in bps) for the estimates of this remote bitrate estimator.
     */
    private var minBitrateBps = -1

    /**
     * The ID of the abs-send-time RTP header extension.
     */
    private var astExtensionID = -1

    /**
     * The ID of the TCC RTP header extension.
     */
    private var tccExtensionID = -1

    /**
     * The flag which indicates whether the remote end supports RTCP REMB or not.
     */
    private var supportsRemb = false

    /**
     * A boolean that determines whether the AST RBE is in use.
     */
    private var usingAbsoluteSendTime = false

    /**
     * Counts packets without the AST header extension. After
     * [.SS_THRESHOLD] many packets we switch back to the single stream RBE.
     */
    private var packetsSinceAbsoluteSendTime = 0

    /**
     * The RBE that this class wraps.
     */
    private var rbe: RemoteBitrateEstimator

    /**
     * Ctor.
     *
     * observer the observer to notify on bitrate estimation changes.
     * diagnosticContext the DiagnosticContext to be used by this instance.
     */
    init {
        // Initialize to the default RTP timestamp based RBE.
        rbe = RemoteBitrateEstimatorSingleStream(observer, diagnosticContext)
    }

    /**
     * {@inheritDoc}
     */
    override val latestEstimate: Long
        get() = rbe.latestEstimate

    /**
     * {@inheritDoc}
     */
    override val ssrcs: Collection<Long>?
        get() = rbe.ssrcs

    /**
     * {@inheritDoc}
     */
    override fun removeStream(ssrc: Long) {
        rbe.removeStream(ssrc)
    }

    /**
     * {@inheritDoc}
     */
    override fun setMinBitrate(minBitrateBps: Int) {
        this.minBitrateBps = minBitrateBps
        rbe.setMinBitrate(minBitrateBps)
    }

    /**
     * {@inheritDoc}
     */
    override fun incomingPacketInfo(arrivalTimeMs: Long, timestamp: Long, payloadSize: Int, ssrc: Long) {
        rbe.incomingPacketInfo(arrivalTimeMs, timestamp, payloadSize, ssrc)
    }

    /**
     * {@inheritDoc}
     */
    override fun reverseTransform(pkt: RawPacket): RawPacket? {
        if (!receiveSideBweEnabled()) {
            return pkt
        }
        var ext: RawPacket.HeaderExtension? = null
        val astExtensionID = astExtensionID
        if (astExtensionID != -1) {
            ext = pkt!!.getHeaderExtension(astExtensionID.toByte())
        }
        if (ext != null) {
            // If we see AST in header, switch RBE strategy immediately.
            if (!usingAbsoluteSendTime) {
                usingAbsoluteSendTime = true
                rbe = RemoteBitrateEstimatorAbsSendTime(observer, diagnosticContext)
                val minBitrateBps = minBitrateBps
                if (minBitrateBps > 0) {
                    rbe.setMinBitrate(minBitrateBps)
                }
            }
            packetsSinceAbsoluteSendTime = 0
        } else {
            // When we don't see AST, wait for a few packets before going
            // back to SS.
            if (usingAbsoluteSendTime) {
                ++packetsSinceAbsoluteSendTime
                if (packetsSinceAbsoluteSendTime >= SS_THRESHOLD) {
                    usingAbsoluteSendTime = false
                    rbe = RemoteBitrateEstimatorSingleStream(observer, diagnosticContext)
                    val minBitrateBps = minBitrateBps
                    if (minBitrateBps > 0) {
                        rbe.setMinBitrate(minBitrateBps)
                    }
                }
            }
        }
        if (!usingAbsoluteSendTime) {
            incomingPacketInfo(System.currentTimeMillis(), pkt!!.timestamp,
                    pkt.payloadLength, pkt.getSSRCAsLong())
            return pkt
        }
        val sendTime24bits = if (astExtensionID == -1) -1 else getAbsSendTime(pkt!!, astExtensionID.toByte())
        if (usingAbsoluteSendTime && sendTime24bits != -1L) {
            incomingPacketInfo(System.currentTimeMillis(), sendTime24bits,
                    pkt!!.payloadLength, pkt.getSSRCAsLong())
        }
        return pkt
    }

    /**
     * {@inheritDoc}
     */
    override fun onRttUpdate(avgRttMs: Long, maxRttMs: Long) {
        rbe.onRttUpdate(avgRttMs, maxRttMs)
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
     * Sets the ID of the abs-send-time RTP extension. Set to -1 to effectively
     * disable the AST remote bitrate estimator.
     *
     * @param astExtensionID the ID to set.
     */
    fun setAstExtensionID(astExtensionID: Int) {
        this.astExtensionID = astExtensionID
    }

    /**
     * Gets a boolean that indicates whether or not to perform receive-side
     * bandwidth estimations.
     *
     * @return true if receive-side bandwidth estimations are enabled, false otherwise.
     */
    fun receiveSideBweEnabled(): Boolean {
        return tccExtensionID == -1 && supportsRemb
    }

    /**
     * Sets the value of the flag which indicates whether the remote end supports RTCP REMB or not.
     *
     * @param supportsRemb the value to set.
     */
    fun setSupportsRemb(supportsRemb: Boolean) {
        this.supportsRemb = supportsRemb
    }

    /**
     * Sets the ID of the transport-cc RTP extension. Anything other than -1
     * disables receive-side bandwidth estimations.
     *
     * @param tccExtensionID the ID to set.
     */
    fun setTccExtensionID(tccExtensionID: Int) {
        this.tccExtensionID = tccExtensionID
    }

    companion object {
        /**
         * After this many packets without the AST header, switch to the SS RBE.
         */
        private const val SS_THRESHOLD = 30L
    }
}