/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.rtp

import net.sf.fmj.media.rtp.RTCPFeedback
import net.sf.fmj.media.rtp.RTCPReport
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.service.neomedia.rtp.RTCPExtendedReport.VoIPMetricsReportBlock
import timber.log.Timber
import java.util.*

/**
 * Collects the (last) RTCP (SR, RR, and XR) reports sent and received by a local peer (for the
 * purposes of `MediaStreamStats`).
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class RTCPReports {
    /**
     * Gets a list of the `RTCPReportListener`s to be notified by this instance about the
     * receiving and sending of RTCP RR, SR, and XR.
     *
     * @return a list of the `RTCPReportListener`s to be notified by this instance about the
     * receiving and sending of RTCP RR, SR, and XR
     */
    /**
     * The list of `RTCPReportListener`s to be notified by this instance about the receiving
     * and sending of RTCP RR, SR, and XR. Implemented as copy-on-write storage in order to
     * optimize the firing of events to the listeners.
     */
    private var rtcpReportListeners = emptyList<RTCPReportListener>()

    /**
     * The `Object` which synchronizes the (write) access to [.listeners].
     */
    private val listenerSyncRoot = Any()

    /**
     * The RTCP extended reports (XR) received by the local endpoint represented by this instance
     * associated with the synchronization source identifiers of their respective originator (SSRC
     * defined by RFC 3611).
     */
    private val receivedExtendedReports = HashMap<Int, RTCPExtendedReport>()

    /**
     * The RTCP sender report (SR) and/or receiver report (RR) blocks received by the local
     * endpoint represented by this instance associated with the synchronization source
     * identifiers of their respective source (SSRC of source defined by RFC 3550).
     */
    private val receivedFeedbacks = HashMap<Int, RTCPFeedback>()

    /**
     * The RTCP sender reports (SR) and/or receiver reports (RR) received by the local endpoint
     * represented by this instance associated with the synchronization source identifiers of their
     * respective originator (SSRC of sender defined by RFC 3550).
     */
    private val receivedReports = HashMap<Int, RTCPReport>()

    /**
     * The RTCP extended report (XR) VoIP Metrics blocks received by the local endpoint represented
     * by this instance associated with the synchronization source identifiers of their respective
     * source (SSRC of source defined by RFC 3611).
     */
    private val receivedVoIPMetrics = HashMap<Int, VoIPMetricsReportBlock>()

    /**
     * The RTCP extended reports (XR) sent by the local endpoint represented by this instance
     * associated with the synchronization source identifiers of their respective originator (SSRC
     * defined by RFC 3611).
     */
    private val sentExtendedReports = HashMap<Int, RTCPExtendedReport>()

    /**
     * The RTCP sender report (SR) and/or receiver report (RR) blocks sent by the local endpoint
     * represented by this instance associated with the synchronization source identifiers of their
     * respective source (SSRC of source defined by RFC 3550).
     */
    private val sentFeedbacks = HashMap<Int, RTCPFeedback>()

    /**
     * The RTCP sender reports (SR) and/or receiver reports (RR) sent by the local endpoint
     * represented by this instance associated with the synchronization source identifiers of their
     * respective originator (SSRC of sender defined by RFC 3550).
     */
    private val sentReports = HashMap<Int, RTCPReport>()

    /**
     * The RTCP extended report (XR) VoIP Metrics blocks sent by the local endpoint represented by
     * this instance associated with the synchronization source identifiers of their respective
     * source (SSRC of source defined by RFC 3611).
     */
    private val sentVoIPMetrics = HashMap<Int, VoIPMetricsReportBlock>()

    /**
     * Adds a new `RTCPReportListener` to be notified by this instance about the receiving
     * and sending of RTCP RR, SR and XR.
     *
     * @param listener the `RTCPReportListener` to add
     * @throws NullPointerException if the specified `listener` is `null`
     */
    fun addRTCPReportListener(listener: RTCPReportListener?) {
        if (listener == null) throw NullPointerException("listener")
        synchronized(listenerSyncRoot) {
            if (!rtcpReportListeners.contains(listener)) {
                val newListeners = ArrayList<RTCPReportListener>(rtcpReportListeners.size + 1)
                newListeners.addAll(rtcpReportListeners)
                newListeners.add(listener)
                rtcpReportListeners = Collections.unmodifiableList(newListeners)
            }
        }
    }

    /**
     * Gets the latest RTCP XR received from a specific SSRC (of remote originator).
     *
     * @param ssrc the SSRC of the RTCP XR (remote) originator
     * @return the latest RTCP XR received from the specified `ssrc`
     */
    fun getReceivedRTCPExtendedReport(ssrc: Int): RTCPExtendedReport? {
        synchronized(receivedExtendedReports) { return receivedExtendedReports[ssrc] }
    }

    /**
     * Gets the RTCP extended reports (XR) received by the local endpoint.
     *
     * @return the RTCP extended reports (XR) received by the local endpoint
     */
    val receivedRTCPExtendedReports: Array<RTCPExtendedReport>
        get() {
            synchronized(receivedExtendedReports) {
                val values = receivedExtendedReports.values
                return values.toTypedArray()
            }
        }

    /**
     * Gets the latest RTCP SR or RR report block received from a remote sender/originator for a
     * local source.
     *
     * @param sourceSSRC the SSRC of the local source
     * @return the latest RTCP SR or RR report block received from a remote sender/originator for
     * the specified `sourceSSRC`
     */
    fun getReceivedRTCPFeedback(sourceSSRC: Int): RTCPFeedback? {
        synchronized(receivedReports) { return receivedFeedbacks[sourceSSRC] }
    }

    /**
     * Gets the RTCP sender report (SR) and/or receiver report (RR) blocks received by the local
     * endpoint.
     *
     * @return the RTCP sender report (SR) and/or receiver report (RR) blocks received by the local
     * endpoint
     */
    val receivedRTCPFeedbacks: Array<RTCPFeedback>
        get() {
            synchronized(receivedReports) {
                val values = receivedFeedbacks.values
                return values.toTypedArray()
            }
        }

    /**
     * Gets the latest RTCP SR or RR received from a specific SSRC (of remote sender/originator).
     *
     * @param senderSSRC the SSRC of the RTCP SR or RR (remote) sender/originator
     * @return the latest RTCP SR or RR received from the specified `senderSSRC`
     */
    fun getReceivedRTCPReport(senderSSRC: Int): RTCPReport? {
        synchronized(receivedReports) { return receivedReports[senderSSRC] }
    }

    /**
     * Gets the RTCP sender reports (SR) and/or receiver reports (RR) received by the local
     * endpoint.
     *
     * @return the RTCP sender reports (SR) and/or receiver reports (RR) received by the local
     * endpoint
     */
    val receivedRTCPReports: Array<RTCPReport>
        get() {
            synchronized(receivedReports) {
                val values = receivedReports.values
                return values.toTypedArray()
            }
        }

    /**
     * Gets the RTCP extended report (XR) VoIP Metrics blocks received by the local endpoint.
     *
     * @return the RTCP extended report (XR) VoIP Metrics blocks received by the local endpoint
     */
    val receivedRTCPVoIPMetrics: Array<VoIPMetricsReportBlock>
        get() {
            synchronized(receivedExtendedReports) {
                val values = receivedVoIPMetrics.values
                return values.toTypedArray()
            }
        }

    /**
     * Gets the latest RTCP extended report (XR) VoIP Metrics block received from a remote
     * originator for a local source.
     *
     * @param sourceSSRC the SSRC of the local source
     * @return the RTCP extended report (XR) VoIP Metrics block received from a remote originator
     * for the specified `sourceSSRC`
     */
    fun getReceivedRTCPVoIPMetrics(sourceSSRC: Int): VoIPMetricsReportBlock? {
        synchronized(receivedExtendedReports) { return receivedVoIPMetrics[sourceSSRC] }
    }

    /**
     * Gets the latest RTCP XR sent from a specific SSRC (of local originator).
     *
     * @param ssrc the SSRC of the RTCP XR (local) originator
     * @return the latest RTCP XR sent from the specified `ssrc`
     */
    fun getSentRTCPExtendedReport(ssrc: Int): RTCPExtendedReport? {
        synchronized(sentExtendedReports) { return sentExtendedReports[ssrc] }
    }

    /**
     * Gets the RTCP extended reports (XR) sent by the local endpoint.
     *
     * @return the RTCP extended reports (XR) sent by the local endpoint
     */
    val sentRTCPExtendedReports: Array<RTCPExtendedReport>
        get() {
            synchronized(sentExtendedReports) {
                val values = sentExtendedReports.values
                return values.toTypedArray()
            }
        }

    /**
     * Gets the latest RTCP SR or RR report block sent from a local sender/originator for a remote
     * source.
     *
     * @param sourceSSRC the SSRC of the remote source
     * @return the latest RTCP SR or RR report block received from a local sender/originator for
     * the specified `sourceSSRC`
     */
    fun getSentRTCPFeedback(sourceSSRC: Int): RTCPFeedback? {
        synchronized(sentReports) { return sentFeedbacks[sourceSSRC] }
    }

    /**
     * Gets the RTCP sender report (SR) and/or receiver report (RR) blocks sent by the local endpoint.
     *
     * @return the RTCP sender report (SR) and/or receiver report (RR) blocks sent by the local endpoint
     */
    val sentRTCPFeedbacks: Array<RTCPFeedback>
        get() {
            synchronized(sentReports) {
                val values = sentFeedbacks.values
                return values.toTypedArray()
            }
        }

    /**
     * Gets the latest RTCP SR or RR sent from a specific SSRC (of local sender/originator).
     *
     * @param senderSSRC the SSRC of the RTCP SR or RR (local) sender/originator
     * @return the latest RTCP SR or RR sent from the specified `senderSSRC`
     */
    fun getSentRTCPReport(senderSSRC: Int): RTCPReport? {
        synchronized(sentReports) { return sentReports[senderSSRC] }
    }

    /**
     * Gets the RTCP sender reports (SR) and/or receiver reports (RR) sent by the local endpoint.
     *
     * @return the RTCP sender reports (SR) and/or receiver reports (RR) sent by the local endpoint
     */
    val sentRTCPReports: Array<RTCPReport>
        get() {
            synchronized(sentReports) {
                val values = sentReports.values
                return values.toTypedArray()
            }
        }

    /**
     * Gets the RTCP extended report (XR) VoIP Metrics blocks sent by the local endpoint.
     *
     * @return the RTCP extended report (XR) VoIP Metrics blocks sent by the local endpoint
     */
    val sentRTCPVoIPMetrics: Array<VoIPMetricsReportBlock>
        get() {
            synchronized(sentExtendedReports) {
                return sentVoIPMetrics.values.toTypedArray()
            }
        }

    /**
     * Gets the latest RTCP extended report (XR) VoIP Metrics block sent from a local originator
     * for
     * a remote source.
     *
     * @param sourceSSRC the SSRC of the remote source
     * @return the RTCP extended report (XR) VoIP Metrics block sent from a local originator for
     * the specified `sourceSSRC`
     */
    fun getSentRTCPVoIPMetrics(sourceSSRC: Int): VoIPMetricsReportBlock? {
        synchronized(sentExtendedReports) { return sentVoIPMetrics[sourceSSRC] }
    }

    /**
     * Removes an existing `RTCPReportListener` to no longer be notified by this instance
     * about the receiving and sending of RTCP RR, SR and XR.
     *
     * @param listener the `RTCPReportListener` to remove
     */
    fun removeRTCPReportListener(listener: RTCPReportListener?) {
        if (listener == null) return
        synchronized(listenerSyncRoot) {
            val index = rtcpReportListeners.indexOf(listener)
            if (index != -1) {
                if (rtcpReportListeners.size == 1) {
                    rtcpReportListeners = emptyList()
                } else {
                    val newListeners = ArrayList(rtcpReportListeners)
                    newListeners.removeAt(index)
                    rtcpReportListeners = Collections.unmodifiableList(newListeners)
                }
            }
        }
    }

    /**
     * Notifies this instance that a specific `RTCPExtendedReport` was received by the local
     * endpoint. Remembers the received `extendedReport` and notifies the
     * `RTCPReportListener`s added to this instance.
     *
     * @param extendedReport the received `RTCPExtendedReport`
     * @throws NullPointerException if the specified `extendedReport` is `null`
     */
    fun rtcpExtendedReportReceived(extendedReport: RTCPExtendedReport?) {
        if (extendedReport == null) throw NullPointerException("extendedReport")

        var fire: Boolean
        synchronized(receivedExtendedReports) {
            val oldValue = receivedExtendedReports.put(extendedReport.ssrc, extendedReport)
            if (extendedReport == oldValue) {
                fire = false
            } else {
                if (extendedReport.systemTimeStamp == 0L) {
                    extendedReport.systemTimeStamp = System.currentTimeMillis()
                }

                // VoIP Metrics Report Block
                for (reportBlock in extendedReport.reportBlocks) {
                    if (reportBlock is VoIPMetricsReportBlock) {
                        val voipMetrics = reportBlock
                        receivedVoIPMetrics[voipMetrics.sourceSSRC] = voipMetrics
                    }
                }
                fire = true
            }
        }
        if (fire) {
            for (listener in rtcpReportListeners) listener.rtcpExtendedReportReceived(extendedReport)
        }
        Timber.log(TimberLog.FINER, "Received %s", extendedReport)
    }

    /**
     * Notifies this instance that a specific `RTCPExtendedReport` was sent by the local
     * endpoint. Remembers the sent `extendedReport` and notifies the
     * `RTCPReportListener`s added to this instance.
     *
     * @param extendedReport the sent `RTCPExtendedReport`
     * @throws NullPointerException if the specified `extendedReport` is `null`
     */
    fun rtcpExtendedReportSent(extendedReport: RTCPExtendedReport?) {
        if (extendedReport == null) throw NullPointerException("extendedReport")
        var fire: Boolean
        synchronized(sentExtendedReports) {
            val oldValue = sentExtendedReports.put(extendedReport.ssrc, extendedReport)
            if (extendedReport == oldValue) {
                fire = false
            } else {
                if (extendedReport.systemTimeStamp == 0L) {
                    extendedReport.systemTimeStamp = System.currentTimeMillis()
                }

                // VoIP Metrics Report Block
                for (reportBlock in extendedReport.reportBlocks) {
                    if (reportBlock is VoIPMetricsReportBlock) {
                        val voipMetrics = reportBlock
                        sentVoIPMetrics[voipMetrics.sourceSSRC] = voipMetrics
                    }
                }
                fire = true
            }
        }
        if (fire) {
            for (listener in rtcpReportListeners) listener.rtcpExtendedReportSent(extendedReport)
        }
        Timber.log(TimberLog.FINER, "Sent $extendedReport.")
    }

    /**
     * Notifies this instance that a specific `RTCPReport` was received by the local
     * endpoint. Remembers the received `report` and notifies the
     * `RTCPReportListener`s added to this instance.
     *
     * @param report the received `RTCPReport`
     * @throws NullPointerException if the specified `report` is `null`
     */
    fun rtcpReportReceived(report: RTCPReport?) {
        if (report == null) throw NullPointerException("report")
        var fire: Boolean
        synchronized(receivedReports) {
            val oldValue = receivedReports.put(report.ssrc.toInt(), report)
            if (report == oldValue) {
                fire = false
            } else {
                if (report.systemTimeStamp == 0L) report.systemTimeStamp = System.currentTimeMillis()

                // RTCPFeedback
                val feedbacks = report.feedbackReports
                if (feedbacks != null) {
                    for (feedback in feedbacks) {
                        receivedFeedbacks[feedback.ssrc.toInt()] = feedback
                    }
                    if (!feedbacks.isEmpty() && TimberLog.isTraceEnable) {
                        val s = StringBuilder()
                        s.append("Received RTCP RR blocks from SSRC ")
                                .append(report.ssrc and 0xFFFFFFFFL)
                                .append(" at time (ms) ")
                                .append(report.systemTimeStamp)
                                .append(" for SSRC(s):")
                        for (feedback in feedbacks) {
                            s.append(' ')
                                    .append(feedback.ssrc and 0xFFFFFFFFL)
                                    .append(',')
                        }
                        Timber.log(TimberLog.FINER, "%s", s)
                    }
                }
                fire = true
            }
        }
        if (fire) {
            for (listener in rtcpReportListeners) listener.rtcpReportReceived(report)
        }
    }

    /**
     * Notifies this instance that a specific `RTCPReport` was sent by the local endpoint.
     * Remembers the sent `report` and notifies the `RTCPReportListener`s added to
     * this instance.
     *
     * @param report the sent `RTCPReport`
     * @throws NullPointerException if the specified `report` is `null`
     */
    fun rtcpReportSent(report: RTCPReport?) {
        if (report == null) throw NullPointerException("report")
        var fire: Boolean
        synchronized(sentReports) {
            val oldValue = sentReports.put(report.ssrc.toInt(), report)
            if (report == oldValue) {
                fire = false
            } else {
                if (report.systemTimeStamp == 0L) report.systemTimeStamp = System.currentTimeMillis()

                // RTCPFeedback
                val feedbacks = report.feedbackReports
                if (feedbacks != null) {
                    for (feedback in feedbacks) {
                        sentFeedbacks[feedback.ssrc.toInt()] = feedback
                    }
                }
                fire = true
            }
        }
        if (fire) {
            for (listener in rtcpReportListeners) listener.rtcpReportSent(report)
        }
    }
}