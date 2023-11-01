/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ContactResource
import net.java.sip.communicator.service.protocol.PresenceStatus
import org.jxmpp.jid.FullJid

/**
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class ContactResourceJabberImpl
/**
 * Creates a `ContactResource` by specifying the `resourceName`, the
 * `presenceStatus` and the `priority`.
 *
 * @param fullJid the full jid corresponding to this contact resource
 * @param contact
 * @param presenceStatus
 * @param priority
 */
(
        /**
         * Returns the full jid corresponding to this contact resource.
         *
         * @return the full jid corresponding to this contact resource
         */
        val fullJid: FullJid,
        contact: Contact,
        presenceStatus: PresenceStatus, priority: Int, isMobile: Boolean,
) : ContactResource(contact, fullJid.resourceOrEmpty.toString(), presenceStatus, priority, isMobile) {

    /**
     * Sets the new `PresenceStatus` of this resource.
     */
    override var presenceStatus: PresenceStatus
        get() = super.presenceStatus
        set(newStatus) {
            super.presenceStatus = newStatus
        }

    /**
     * Changed whether contact is mobile one. Logged in only from mobile device.
     *
     * isMobile whether contact is mobile one.
     */
    override var isMobile = false
        get() = super.isMobile
        set(isMobile) {
            field = isMobile
        }

    /**
     * Changes resource priority.
     *
     * priority the new priority
     */
    override var priority = 0
        get() = super.priority

        set(priority) {
            field = priority
        }
}