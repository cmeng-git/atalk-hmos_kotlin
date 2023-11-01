/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr

import net.java.sip.communicator.plugin.otr.OtrContactManager.OtrContact

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
interface ScOtrKeyManagerListener {
    fun contactVerificationStatusChanged(contact: OtrContact)
}