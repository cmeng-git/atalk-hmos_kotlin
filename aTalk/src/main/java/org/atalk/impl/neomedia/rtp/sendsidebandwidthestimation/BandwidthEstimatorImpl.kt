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
package org.atalk.impl.neomedia.rtp.sendsidebandwidthestimation

import net.sf.fmj.media.rtp.RTCPReport
import org.atalk.impl.neomedia.MediaStreamImpl
import org.atalk.impl.neomedia.rtp.sendsidebandwidthestimation.SendSideBandwidthEstimation.StatisticsImpl
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.MediaStreamStats
import org.atalk.service.neomedia.rtp.BandwidthEstimator
import org.atalk.service.neomedia.rtp.RTCPReportAdapter
import org.atalk.util.concurrent.RecurringRunnable
import java.util.concurrent.ConcurrentHashMap

/**
 * Implements part of the send-side bandwidth estimation described in
 * https://tools.ietf.org/html/draft-ietf-rmcat-gcc-01 Heavily based on code from webrtc.org
 * (bitrate_controller_impl.cc, commit ID 7ad9e661f8a035d49d049ccdb87c77ae8ecdfa35).
 *
 * @author Boris Grozev
 */
class BandwidthEstimatorImpl(stream: MediaStreamImpl) : RTCPReportAdapter(), BandwidthEstimator, RecurringRunnable {
    /**
     * bitrate_controller_impl.h
     */
    private val ssrc_to_last_received_extended_high_seq_num_: MutableMap<Long, Long> = ConcurrentHashMap()
    private var lastUpdateTime = -1L

    /**
     * bitrate_controller_impl.h
     */
    private val sendSideBandwidthEstimation: SendSideBandwidthEstimation

    /**
     * Initializes a new instance which is to belong to a particular MediaStream.
     */
    init {
        sendSideBandwidthEstimation = SendSideBandwidthEstimation(stream, START_BITRATE_BPS)
        sendSideBandwidthEstimation.setMinMaxBitrate(MIN_BITRATE_BPS, MAX_BITRATE_BPS)

        // Hook us up to receive Report Blocks and REMBs.
        val stats = stream.mediaStreamStats
        stats.addRTCPPacketListener(sendSideBandwidthEstimation)
        stats.rtcpReports.addRTCPReportListener(this)
    }

    /**
     * {@inheritDoc}
     *
     * bitrate_controller_impl.cc BitrateControllerImpl::OnReceivedRtcpReceiverReport
     */
    override fun rtcpReportReceived(report: RTCPReport) {
        if (report.feedbackReports == null || report.feedbackReports.isEmpty()) {
            return
        }
        var total_number_of_packets = 0L
        var fraction_lost_aggregate = 0L

        // Compute the a weighted average of the fraction loss from all report
        // blocks.
        for (feedback in report.feedbackReports) {
            val ssrc = feedback.ssrc
            val extSeqNum = feedback.xtndSeqNum
            var lastEHSN = ssrc_to_last_received_extended_high_seq_num_[ssrc]
            if (lastEHSN == null) {
                lastEHSN = extSeqNum
            }
            ssrc_to_last_received_extended_high_seq_num_[ssrc] = extSeqNum
            if (lastEHSN >= extSeqNum) {
                // the first report for this SSRC
                continue
            }
            val number_of_packets = extSeqNum - lastEHSN
            fraction_lost_aggregate += number_of_packets * feedback.fractionLost
            total_number_of_packets += number_of_packets
        }
        fraction_lost_aggregate = if (total_number_of_packets == 0L) {
            0
        } else {
            ((fraction_lost_aggregate + total_number_of_packets / 2)
                    / total_number_of_packets)
        }
        if (fraction_lost_aggregate > 255) {
            return
        }
        synchronized(sendSideBandwidthEstimation) {
            lastUpdateTime = System.currentTimeMillis()
            sendSideBandwidthEstimation.updateReceiverBlock(fraction_lost_aggregate,
                    total_number_of_packets, lastUpdateTime)
        }
    }

    override fun addListener(listener: BandwidthEstimator.Listener?) {
        sendSideBandwidthEstimation.addListener(listener)
    }

    override fun removeListener(listener: BandwidthEstimator.Listener?) {
        sendSideBandwidthEstimation.removeListener(listener)
    }

    /**
     * {@inheritDoc}
     */
    override val latestEstimate: Long
        get() = sendSideBandwidthEstimation.latestEstimate

    /**
     * {@inheritDoc}
     */
    override val latestREMB: Long
        get() = sendSideBandwidthEstimation.latestREMB

    /**
     * {@inheritDoc}
     */
    override fun updateReceiverEstimate(bandwidth: Long) {
        sendSideBandwidthEstimation.updateReceiverEstimate(bandwidth)
    }

    /**
     * {@inheritDoc}
     */
    override val latestFractionLoss: Int
        get() = sendSideBandwidthEstimation.latestFractionLoss

    /**
     * @return the send-side bwe-specific statistics.
     */
    override val statistics: StatisticsImpl
        get() = sendSideBandwidthEstimation.statistics
    override val timeUntilNextRun: Long
        get() {
            val timeSinceLastProcess = Math.max(System.currentTimeMillis() - lastUpdateTime, 0)
            return Math.max(25 - timeSinceLastProcess, 0)
        }

    override fun run() {
        synchronized(sendSideBandwidthEstimation) {
            lastUpdateTime = System.currentTimeMillis()
            sendSideBandwidthEstimation.updateEstimate(lastUpdateTime)
        }
    }

    companion object {
        /**
         * The system property name of the initial value of the estimation, in bits per second.
         */
        const val START_BITRATE_BPS_PNAME = "neomedia.rtp.sendsidebandwidthestimation.BandwidthEstimatorImpl.START_BITRATE_BPS"

        /**
         * The minimum value to be output by this estimator, in bits per second.
         */
        private const val MIN_BITRATE_BPS = 30000

        /**
         * The maximum value to be output by this estimator, in bits per second.
         */
        private const val MAX_BITRATE_BPS = 20 * 1000 * 1000

        /**
         * The ConfigurationService to get config values from.
         */
        private val cfg = LibJitsi.configurationService

        /**
         * The initial value of the estimation, in bits per second.
         */
        private val START_BITRATE_BPS = if (cfg != null) cfg.getLong(START_BITRATE_BPS_PNAME, 300000) else 300000
    }
}