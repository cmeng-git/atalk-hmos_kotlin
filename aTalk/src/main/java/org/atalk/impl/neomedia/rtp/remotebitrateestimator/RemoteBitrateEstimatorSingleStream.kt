/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.remotebitrateestimator

import net.sf.fmj.media.rtp.util.RTPPacket
import org.atalk.service.neomedia.rtp.RemoteBitrateEstimator
import org.atalk.util.logging.DiagnosticContext
import org.ice4j.util.RateStatistics
import java.util.*

/**
 * webrtc/modules/remote_bitrate_estimator/remote_bitrate_estimator_single_stream.cc
 * webrtc/modules/remote_bitrate_estimator/remote_bitrate_estimator_single_stream.h
 *
 * @author Lyubomir Marinov
 */
class RemoteBitrateEstimatorSingleStream(
        private val observer: RemoteBitrateObserver,
        private val diagnosticContext: DiagnosticContext,
) : RemoteBitrateEstimator {

    private val critSect = Any()

    /**
     * Reduces the effects of allocations and garbage collection of the method
     * `incomingPacket`.
     */
    private val deltas = LongArray(3)
    private val incomingBitrate = RateStatistics(RemoteBitrateEstimator.kBitrateWindowMs, 8000f)

    /**
     * Reduces the effects of allocations and garbage collection of the method
     * `updateEstimate` by promoting the `RateControlInput` instance from a local
     * variable to a field and reusing the same instance across method invocations. (Consequently,
     * the default values used to initialize the field are of no importance because they will be
     * overwritten before they are actually used.)
     */
    private val input = RateControlInput(BandwidthUsage.kBwNormal, 0L, 0.0)
    private var lastProcessTime = -1L
    private val overuseDetectors = HashMap<Long, Detector?>()
    private var processIntervalMs = RemoteBitrateEstimator.kProcessIntervalMs.toLong()
    private val remoteRate = AimdRateControl(diagnosticContext)

    /**
     * The set of synchronization source identifiers (SSRCs) currently being received. Represents an
     * unmodifiable copy/snapshot of the current keys of [.overuseDetectors] suitable for
     * public access and introduced for the purposes of reducing the number of allocations and the
     * effects of garbage collection.
     */
    override var ssrcs: Collection<Long>? = null
        get() {
            synchronized(critSect) {
                if (ssrcs == null) {
                    ssrcs = Collections.unmodifiableList(ArrayList(overuseDetectors.keys))
                }
                return ssrcs
            }
        }

    private fun getExtensionTransmissionTimeOffset(header: RTPPacket): Long {
        // TODO Auto-generated method stub
        return 0
    }

    /**
     * {@inheritDoc}
     */
    override val latestEstimate: Long
        get() {
            var bitrateBps: Long
            synchronized(critSect) {
                bitrateBps = if (remoteRate.isValidEstimate) {
                    if (ssrcs!!.isEmpty()) 0L else remoteRate.latestEstimate
                } else {
                    -1L
                }
            }
            return bitrateBps
        }

    /**
     * Notifies this instance of an incoming packet.
     *
     * @param arrivalTimeMs the arrival time of the packet in millis.
     * @param timestamp the RTP timestamp of the packet (RFC3550).
     * @param payloadSize the payload size of the packet.
     * @param ssrc the SSRC of the packet.
     */
    override fun incomingPacketInfo(
            arrivalTimeMs: Long, timestamp: Long, payloadSize: Int, ssrc: Long,
    ) {
        val nowMs = System.currentTimeMillis()
        synchronized(critSect) {

            // XXX The variable naming is chosen to keep the source code close to
            // the original.
            var it = overuseDetectors[ssrc]
            if (it == null) {
                // This is a new SSRC. Adding to map.
                // TODO(holmer): If the channel changes SSRC the old SSRC will still
                // be around in this map until the channel is deleted. This is OK
                // since the callback will no longer be called for the old SSRC.
                // This will be automatically cleaned up when we have one
                // RemoteBitrateEstimator per REMB group.
                it = Detector(nowMs, OverUseDetectorOptions(), true)
                overuseDetectors[ssrc] = it
                ssrcs = null
            }

            // XXX The variable naming is chosen to keep the source code close to
            // the original.
            val estimator = it
            estimator.lastPacketTimeMs = nowMs
            incomingBitrate.update(payloadSize, nowMs)
            val priorState = estimator.detector.state
            val deltas = deltas

            /* long timestampDelta */
            deltas[0] = 0
            /* long timeDelta */
            deltas[1] = 0
            /* int sizeDelta */
            deltas[2] = 0
            if (estimator.interArrival.computeDeltas(timestamp, nowMs, payloadSize, deltas)) {
                val timestampDeltaMs =  /* timestampDelta */
                        deltas[0] * kTimestampToMs
                estimator.estimator.update( /* timeDelta */
                        deltas[1],
                        timestampDeltaMs, deltas[2].toInt(),
                        estimator.detector.state, nowMs)
                estimator.detector.detect(
                        estimator.estimator.offset,
                        timestampDeltaMs,
                        estimator.estimator.numOfDeltas,
                        nowMs)
            }
            var updateEstimate = false
            if (lastProcessTime < 0L
                    || lastProcessTime + processIntervalMs - nowMs <= 0L) {
                updateEstimate = true
            } else if (estimator.detector.state == BandwidthUsage.kBwOverusing) {
                val incomingBitrateBps = incomingBitrate.getRate(nowMs)
                if (priorState != BandwidthUsage.kBwOverusing
                        || remoteRate.isTimeToReduceFurther(nowMs, incomingBitrateBps)) {
                    // The first overuse should immediately trigger a new estimate.
                    // We also have to update the estimate immediately if we are
                    // overusing and the target bitrate is too high compared to what
                    // we are receiving.
                    updateEstimate = true
                }
            }
            if (updateEstimate) {
                updateEstimate(nowMs)
                lastProcessTime = nowMs
            }
        } // synchronized (critSect)
    }

    /**
     * {@inheritDoc}
     */
    override fun onRttUpdate(avgRttMs: Long, maxRttMs: Long) {
        synchronized(critSect) { remoteRate.setRtt(avgRttMs) }
    }

    /**
     * {@inheritDoc}
     */
    override fun removeStream(ssrc: Long) {
        synchronized(critSect) {

            // Ignoring the return value which is the removed OveruseDetector.
            overuseDetectors.remove(ssrc)
            ssrcs = null
        }
    }

    override fun setMinBitrate(minBitrateBps: Int) {
        synchronized(critSect) { remoteRate.setMinBitrate(minBitrateBps.toLong()) }
    }

    /**
     * Triggers a new estimate calculation.
     *
     * @param nowMs
     */
    private fun updateEstimate(nowMs: Long) {
        synchronized(critSect) {
            var bwState = BandwidthUsage.kBwNormal
            var sumVarNoise = 0.0
            val it = overuseDetectors.values.iterator()
            while (it.hasNext()) {
                val overuseDetector = it.next()
                val timeOfLastReceivedPacket = overuseDetector!!.lastPacketTimeMs
                if (timeOfLastReceivedPacket >= 0L
                        && nowMs - timeOfLastReceivedPacket > RemoteBitrateEstimator.kStreamTimeOutMs) {
                    // This over-use detector hasn't received packets for
                    // kStreamTimeOutMs milliseconds and is considered stale.
                    it.remove()
                    ssrcs = null
                } else {
                    sumVarNoise += overuseDetector.estimator.varNoise

                    // Make sure that we trigger an over-use if any of the over-use
                    // detectors is detecting over-use.
                    val overuseDetectorBwState = overuseDetector.detector.state
                    if (overuseDetectorBwState.ordinal > bwState.ordinal) bwState = overuseDetectorBwState
                }
            }
            // We can't update the estimate if we don't have any active streams.
            if (overuseDetectors.isEmpty()) {
                remoteRate.reset()
                return
            }
            val meanNoiseVar = sumVarNoise / overuseDetectors.size.toDouble()
            val input = input
            input.bwState = bwState
            input.incomingBitRate = incomingBitrate.getRate(nowMs)
            input.noiseVar = meanNoiseVar
            remoteRate.update(input, nowMs)
            val targetBitrate = remoteRate.updateBandwidthEstimate(nowMs)
            if (remoteRate.isValidEstimate) {
                processIntervalMs = remoteRate.feedBackInterval
                val observer = observer
                if (observer != null) observer.onReceiveBitrateChanged(ssrcs!!, targetBitrate)
            }
        } // synchronized (critSect)
    }

    private inner class Detector(var lastPacketTimeMs: Long, options: OverUseDetectorOptions, enableBurstGrouping: Boolean) {
        var detector: OveruseDetector
        var estimator: OveruseEstimator
        var interArrival: InterArrival

        init {
            interArrival = InterArrival((90 * RemoteBitrateEstimator.kTimestampGroupLengthMs).toLong(), kTimestampToMs,
                    enableBurstGrouping, diagnosticContext)
            estimator = OveruseEstimator(options, diagnosticContext)
            detector = OveruseDetector(options, diagnosticContext)
        }
    }

    companion object {
        const val kTimestampToMs = 1.0 / 90.0
    }
}