/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.DTMFListener

/**
 * An `OperationSet` that allows us to receive DTMF tones through this protocol provider.
 *
 * @author Damian Minkov
 */
interface OperationSetIncomingDTMF : OperationSet {
    /**
     * Registers the specified DTMFListener with this provider so that it could be notified when
     * incoming DTMF tone is received.
     *
     * @param listener
     * the listener to register with this provider.
     */
    fun addDTMFListener(listener: DTMFListener?)

    /**
     * Removes the specified listener from the list of DTMF listeners.
     *
     * @param listener
     * the listener to unregister.
     */
    fun removeDTMFListener(listener: DTMFListener?)
}