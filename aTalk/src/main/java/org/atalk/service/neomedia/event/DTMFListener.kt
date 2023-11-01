/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.event

/**
 * The purpose of a `DTMFListener` is to notify implementors when new DMTF tones are received
 * by this MediaService implementation.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface DTMFListener {
    /**
     * Indicates that we have started receiving a `DTMFTone`.
     *
     * @param event
     * the `DTMFToneEvent` instance containing the `DTMFTone`
     */
    fun dtmfToneReceptionStarted(event: DTMFToneEvent)

    /**
     * Indicates that reception of a DTMF tone has stopped.
     *
     * @param event
     * the `DTMFToneEvent` instance containing the `DTMFTone`
     */
    fun dtmfToneReceptionEnded(event: DTMFToneEvent)
}