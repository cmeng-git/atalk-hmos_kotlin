/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.OperationSetIncomingDTMF
import net.java.sip.communicator.service.protocol.event.DTMFListener
import net.java.sip.communicator.service.protocol.event.DTMFReceivedEvent

/**
 * Implements `OperationSetIncomingDTMF` for the jabber protocol.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class OperationSetIncomingDTMFJabberImpl constructor() : OperationSetIncomingDTMF, DTMFListener {
    private val listeners: MutableSet<DTMFListener?> = HashSet()

    /**
     * {@inheritDoc}
     *
     * Implements
     * [net.java.sip.communicator.service.protocol.OperationSetIncomingDTMF.addDTMFListener]
     */
    public override fun addDTMFListener(listener: DTMFListener?) {
        listeners.add(listener)
    }

    /**
     * {@inheritDoc}
     *
     * Implements
     * [net.java.sip.communicator.service.protocol.OperationSetIncomingDTMF.removeDTMFListener]
     */
    public override fun removeDTMFListener(listener: DTMFListener?) {
        listeners.remove(listener)
    }

    /**
     * {@inheritDoc}
     *
     * Implements
     * [net.java.sip.communicator.service.protocol.event.DTMFListener.toneReceived]
     */
    public override fun toneReceived(evt: DTMFReceivedEvent?) {
        for (listener: DTMFListener? in listeners) listener!!.toneReceived(evt)
    }
}