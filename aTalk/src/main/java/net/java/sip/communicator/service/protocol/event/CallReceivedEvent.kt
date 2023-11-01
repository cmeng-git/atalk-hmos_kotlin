/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.Call
import java.util.*

/**
 * A class representing the event of a call reception.
 *
 * @author Emil Ivov
 */
class CallReceivedEvent
/**
 * Constructor.
 *
 * @param call
 * the `Call` received
 */
(call: Call<*>?) : EventObject(call) {
    /**
     * Returns the received call.
     *
     * @return received `Call`
     */
    fun getCall(): Call<*> {
        return getSource() as Call<*>
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}