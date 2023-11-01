/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atalk.impl.neomedia.rtp.remotebitrateestimator

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.util.TimestampUtils
import org.atalk.util.TimestampUtils.subtractAsUnsignedInt32
import org.atalk.util.logging.DiagnosticContext
import timber.log.Timber

/**
 * Helper class to compute the inter-arrival time delta and the size delta between two timestamp
 * groups. A timestamp is a 32 bit unsigned number with a client defined rate.
 *
 * webrtc/modules/remote_bitrate_estimator/inter_arrival.cc
 * webrtc/modules/remote_bitrate_estimator/inter_arrival.h
 *
 * @author Lyubomir Marinov
 * @author Julian Chukwu
 * @author George Politis
 * @author Eng Chong Meng
 */
internal class InterArrival
/**
 * A timestamp group is defined as all packets with a timestamp which are at
 * most `timestampGroupLengthTicks` older than the first timestamp in that group.
 */
(private val kTimestampGroupLengthTicks: Long, private val timestampToMsCoeff: Double,
        private val burstGrouping: Boolean, private val diagnosticContext: DiagnosticContext) {
    private var currentTimestampGroup = TimestampGroup()
    private var prevTimestampGroup = TimestampGroup()
    private var numConsecutiveReorderedPackets = 0
    private fun belongsToBurst(arrivalTimeMs: Long, timestamp: Long): Boolean {
        if (!burstGrouping) return false
        check(currentTimestampGroup.completeTimeMs >= 0) { "currentTimestampGroup.completeTimeMs" }
        val arrivalTimeDeltaMs = arrivalTimeMs - currentTimestampGroup.completeTimeMs
        val timestampDiff = subtractAsUnsignedInt32(timestamp, currentTimestampGroup.timestamp)
        val tsDeltaMs = (timestampToMsCoeff * timestampDiff + 0.5).toLong()
        if (tsDeltaMs == 0L) return true
        val propagationDeltaMs = arrivalTimeDeltaMs - tsDeltaMs
        return propagationDeltaMs < 0 && arrivalTimeDeltaMs <= kBurstDeltaThresholdMs
    }

    /**
     * Returns `true` if a delta was computed, or `false` if the current group is still
     * incomplete or if only one group has been completed.
     *
     * @param timestamp is the timestamp.
     * @param arrivalTimeMs is the local time at which the packet arrived.
     * @param packetSize is the size of the packet.
     * @param deltas `timestampDelta` is the computed timestamp delta, `arrivalTimeDeltaMs` is
     * the computed arrival-time delta, `packetSizeDelta` is the computed size delta.
     * @return
     * @Note: We have two `computeDeltas`.
     * One with a valid `systemTimeMs` according to webrtc
     * implementation as of June 12,2017 and a previous one
     * with a default systemTimeMs (-1L). the later may be removed or
     * deprecated.
     */
    @JvmOverloads
    fun computeDeltas(timestamp: Long, arrivalTimeMs: Long, packetSize: Int, deltas: LongArray?, systemTimeMs: Long = -1L): Boolean {
        if (deltas == null) throw NullPointerException("deltas")
        require(deltas.size == 3) { "deltas.length" }
        var calculatedDeltas = false
        if (currentTimestampGroup.isFirstPacket) {
            // We don't have enough data to update the filter, so we store it
            // until we have two frames of data to process.
            currentTimestampGroup.timestamp = timestamp
            currentTimestampGroup.firstTimestamp = timestamp
        } else if (!isPacketInOrder(timestamp)) {
            return false
        } else if (isNewTimestampGroup(arrivalTimeMs, timestamp)) {
            // First packet of a later frame, the previous frame sample is ready.
            if (prevTimestampGroup.completeTimeMs >= 0) {
                /* long timestampDelta */
                deltas[0] = subtractAsUnsignedInt32(
                        currentTimestampGroup.timestamp, prevTimestampGroup.timestamp)
                deltas[1] = currentTimestampGroup.completeTimeMs - prevTimestampGroup.completeTimeMs
                val arrivalTimeDeltaMs = deltas[1]

                // Check system time differences to see if we have an unproportional jump
                // in arrival time. In that case reset the inter-arrival computations.
                val systemTimeDeltaMs = currentTimestampGroup.lastSystemTimeMs - prevTimestampGroup.lastSystemTimeMs
                if (prevTimestampGroup.lastSystemTimeMs != -1L && currentTimestampGroup.lastSystemTimeMs != -1L && arrivalTimeDeltaMs - systemTimeDeltaMs >= kArrivalTimeOffsetThresholdMs) {
                    Timber.w("The arrival time clock offset has changed (diff = %d ms), resetting.",
                            arrivalTimeDeltaMs - systemTimeDeltaMs)
                    Reset()
                    return false
                }
                numConsecutiveReorderedPackets = if (arrivalTimeDeltaMs < 0) {
                    ++numConsecutiveReorderedPackets
                    if (numConsecutiveReorderedPackets >= kReorderedResetThreshold) {
                        // The group of packets has been reordered since receiving
                        // its local arrival timestamp.
                        Timber.w("Packets are being reordered on the path from the socket to the bandwidth estimator. Ignoring this packet for bandwidth estimation.")
                        Reset()
                    }
                    return false
                } else {
                    0
                }
                /* int packetSizeDelta */
                deltas[2] = (currentTimestampGroup.size - prevTimestampGroup.size).toInt().toLong()
                Timber.log(TimberLog.FINER, "%s", diagnosticContext
                        .makeTimeSeriesPoint("computed_deltas")
                        .addField("inter_arrival", hashCode())
                        .addField("arrival_time_ms", arrivalTimeMs)
                        .addField("timestamp_delta", deltas[0])
                        .addField("arrival_time_ms_delta", deltas[1])
                        .addField("payload_size_delta", deltas[2]))
                calculatedDeltas = true
            }
            prevTimestampGroup.copy(currentTimestampGroup)
            // The new timestamp is now the current frame.
            currentTimestampGroup.firstTimestamp = timestamp
            currentTimestampGroup.timestamp = timestamp
            currentTimestampGroup.size = 0
        } else {
            currentTimestampGroup.timestamp = latestTimestamp(currentTimestampGroup.timestamp, timestamp)
        }
        // Accumulate the frame size.
        currentTimestampGroup.size += packetSize.toLong()
        currentTimestampGroup.completeTimeMs = arrivalTimeMs
        currentTimestampGroup.lastSystemTimeMs = systemTimeMs
        return calculatedDeltas
    }

    /**
     * Returns `true` if the last packet was the end of the current batch and the packet with
     * `timestamp` is the first of a new batch.
     *
     * @param arrivalTimeMs
     * @param timestamp
     * @return
     */
    private fun isNewTimestampGroup(arrivalTimeMs: Long, timestamp: Long): Boolean {
        return if (currentTimestampGroup.isFirstPacket) {
            false
        } else if (belongsToBurst(arrivalTimeMs, timestamp)) {
            false
        } else {
            val timestampDiff = subtractAsUnsignedInt32(timestamp, currentTimestampGroup.firstTimestamp)
            timestampDiff > kTimestampGroupLengthTicks
        }
    }

    /**
     * Returns `true` if the packet with timestamp `timestamp` arrived in order.
     *
     * @param timestamp
     * @return
     */
    private fun isPacketInOrder(timestamp: Long): Boolean {
        return if (currentTimestampGroup.isFirstPacket) {
            true
        } else {
            // Assume that a diff which is bigger than half the timestamp
            // interval (32 bits) must be due to reordering. This code is almost
            // identical to that in isNewerTimestamp() in module_common_types.h.
            val timestampDiff = subtractAsUnsignedInt32(timestamp, currentTimestampGroup.firstTimestamp)
            val tsDeltaMs = (timestampToMsCoeff * timestampDiff + 0.5).toLong()
            val inOrder = timestampDiff < 0x80000000L
            if (!inOrder) {
                Timber.log(TimberLog.FINER, "%s", diagnosticContext
                        .makeTimeSeriesPoint("reordered_packet")
                        .addField("inter_arrival", hashCode())
                        .addField("ts_delta_ms", tsDeltaMs))
            }
            inOrder
        }
    }

    private class TimestampGroup {
        var completeTimeMs = -1L
        var size = 0L
        var firstTimestamp = 0L
        var timestamp = 0L
        var lastSystemTimeMs = -1L

        /**
         * Assigns the values of the fields of `source` to the respective fields of this
         * `TimestampGroup`.
         *
         * @param source the `TimestampGroup` the values of the fields of which are to be assigned to
         * the respective fields of this `TimestampGroup`
         */
        fun copy(source: TimestampGroup) {
            completeTimeMs = source.completeTimeMs
            firstTimestamp = source.firstTimestamp
            size = source.size
            timestamp = source.timestamp
        }

        val isFirstPacket: Boolean
            get() = completeTimeMs == -1L
    }

    fun Reset() {
        numConsecutiveReorderedPackets = 0
        currentTimestampGroup = TimestampGroup()
        prevTimestampGroup = TimestampGroup()
    }

    companion object {
        private const val kBurstDeltaThresholdMs = 5

        /**
         * webrtc/modules/include/module_common_types.h
         *
         * @param timestamp1
         * @param timestamp2
         * @return
         */
        private fun latestTimestamp(timestamp1: Long, timestamp2: Long): Long {
            return TimestampUtils.latestTimestamp(timestamp1, timestamp2)
        }

        // After this many packet groups received out of order InterArrival will
        // reset, assuming that clocks have made a jump.
        private const val kReorderedResetThreshold = 3
        private const val kArrivalTimeOffsetThresholdMs = 3000
    }
}