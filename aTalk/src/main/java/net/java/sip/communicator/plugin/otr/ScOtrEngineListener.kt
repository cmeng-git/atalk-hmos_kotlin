/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr

import net.java.sip.communicator.plugin.otr.OtrContactManager.OtrContact
import net.java.sip.communicator.service.protocol.Contact

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
interface ScOtrEngineListener {
    fun contactPolicyChanged(contact: Contact?)
    fun globalPolicyChanged()
    fun sessionStatusChanged(contact: OtrContact?)
    fun multipleInstancesDetected(contact: OtrContact?)
    fun outgoingSessionChanged(contact: OtrContact?)
}