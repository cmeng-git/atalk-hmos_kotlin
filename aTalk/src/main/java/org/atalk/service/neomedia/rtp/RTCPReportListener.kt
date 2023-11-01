/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.rtp

import net.sf.fmj.media.rtp.RTCPReport

/**
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface RTCPReportListener {
    /**
     * Notifies this listener that a specific RTCP XR was received by the local endpoint.
     *
     * @param extendedReport
     * the received RTCP XR
     */
    fun rtcpExtendedReportReceived(extendedReport: RTCPExtendedReport)

    /**
     * Notifies this listener that a specific RTCP XR was sent by the local endpoint.
     *
     * @param extendedReport
     * the sent RTCP XR
     */
    fun rtcpExtendedReportSent(extendedReport: RTCPExtendedReport)

    /**
     * Notifies this listener that a specific RTCP SR or RR was received by the local endpoint.
     *
     * @param report
     * the received RTCP SR or RR
     */
    fun rtcpReportReceived(report: RTCPReport)

    /**
     * Notifies this listener that a specific RTCP SR or RR was sent by the local endpoint.
     *
     * @param report
     * the sent RTCP SR or RR
     */
    fun rtcpReportSent(report: RTCPReport)
}