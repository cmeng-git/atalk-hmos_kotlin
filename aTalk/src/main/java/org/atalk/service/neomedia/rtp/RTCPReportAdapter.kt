/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.rtp

import net.sf.fmj.media.rtp.RTCPReport

/**
 * A default implementation of `RTCPReportListener` to facilitate implementers.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
open class RTCPReportAdapter : RTCPReportListener {
    /**
     * {@inheritDoc}
     */
    override fun rtcpExtendedReportReceived(extendedReport: RTCPExtendedReport) {}

    /**
     * {@inheritDoc}
     */
    override fun rtcpExtendedReportSent(extendedReport: RTCPExtendedReport) {}

    /**
     * {@inheritDoc}
     */
    override fun rtcpReportReceived(report: RTCPReport) {}

    /**
     * {@inheritDoc}
     */
    override fun rtcpReportSent(report: RTCPReport) {}
}