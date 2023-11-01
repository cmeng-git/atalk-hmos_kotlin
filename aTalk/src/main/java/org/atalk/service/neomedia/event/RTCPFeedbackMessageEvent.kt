/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.event

import java.util.*

/**
 * Represents an event coming from RTCP that meant to tell codec to do something (i.e send a
 * keyframe, ...).
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class RTCPFeedbackMessageEvent
/**
 * Constructor.
 *
 * @param source
 * source
 * @param feedbackMessageType
 * feedback message type (FMT)
 * @param payloadType
 * payload type (PT)
 */
(source: Any?,
        /**
         * Feedback message type (FMT).
         */
        val feedbackMessageType: Int,
        /**
         * Payload type (PT).
         */
        val payloadType: Int) : EventObject(source) {
    /**
     * Get feedback message type (FMT).
     *
     * @return message type
     */
    /**
     * Get payload type (PT) of RTCP packet.
     *
     * @return payload type
     */

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Full Intra Request (FIR) RTCP feedback message type.
         */
        const val FMT_FIR = 4

        /**
         * Picture Loss Indication (PLI) feedback message type.
         */
        const val FMT_PLI = 1

        /**
         * The payload type (PT) of payload-specific RTCP feedback messages.
         */
        const val PT_PS = 206

        /**
         * The payload type (PT) of transport layer RTCP feedback messages.
         */
        const val PT_TL = 205
    }
}