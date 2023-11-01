/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.event

import org.atalk.service.neomedia.AudioMediaStream
import org.atalk.service.neomedia.DTMFRtpTone
import java.util.*

/**
 * This event represents starting or ending reception of a specific `DTMFRtpTone`.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class DTMFToneEvent
/**
 * Creates an instance of this `DTMFToneEvent` with the specified source stream and DTMF
 * tone.
 *
 * @param source
 * the `AudioMediaSteam` instance that received the tone.
 * @param dtmfTone
 * the tone that we (started/stopped) receiving.
 */
(source: AudioMediaStream?,
        /**
         * The tone that this event is pertaining to.
         */
        val dtmfTone: DTMFRtpTone) : EventObject(source) {
    /**
     * Returns the `DTMFTone` instance that this event pertains to.
     *
     * @return the `DTMFTone` instance that this event pertains to.
     */

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}