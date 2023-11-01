/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.event

/**
 * Represents a listener of `RTCPFeedbackMessageListener` instances.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
interface RTCPFeedbackMessageCreateListener {
    /**
     * Notifies this `RTCPFeedbackCreateListener` that a `RTCPFeedbackMessageListener`
     * is created.
     *
     * @param rtcpFeedbackMessageListener
     * the created `RTCPFeedbackMessageListener` instance
     */
    fun onRTCPFeedbackMessageCreate(rtcpFeedbackMessageListener: RTCPFeedbackMessageListener)
}