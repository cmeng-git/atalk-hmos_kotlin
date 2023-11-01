/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr

import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ContactResource
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * The OtrContactManager is used for accessing `OtrContact`s in a static way.
 *
 *
 * The `OtrContact` class is just a wrapper of [Contact, ContactResource]
 * pairs. Its purpose is for the otr plugin to be able to create different
 * `Session`s for every ContactResource that a Contact has.
 *
 *
 * Currently, only the Jabber protocol supports ContactResources.
 *
 * @author Marin Dzhigarov
 * @author Eng Chong Meng
 */
class OtrContactManager : ServiceListener {
    /**
     * Cleans up unused cached up Contacts.
     */
    override fun serviceChanged(event: ServiceEvent) {
        val service = OtrActivator.bundleContext.getService(event.serviceReference) as? ProtocolProviderService
                ?: return
        if (event.type == ServiceEvent.UNREGISTERING) {
            Timber.d("Unregistering a ProtocolProviderService, cleaning OTR's Contact to OtrContact map")
            synchronized(contactsMap) {
                val i = contactsMap.keys.iterator()
                while (i.hasNext()) {
                    if (service == i.next().protocolProvider) i.remove()
                }
            }
        }
    }

    /**
     * The `OtrContact` class is just a wrapper of
     * [Contact, ContactResource] pairs. Its purpose is for the otr plugin to be
     * able to create different `Session`s for every ContactResource that
     * a Contact has.
     *
     * @author Marin Dzhigarov
     */
    class OtrContact(contact: Contact, resource: ContactResource?) {
        val contact: Contact?
        val resource: ContactResource?

        init {
            this.contact = contact
            this.resource = resource
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is OtrContact) return false

            // val other = other
            return if (contact != null && contact == other.contact) {
                // cmeng: must only compare resourceName, other resource parameters may not be the same
                // e.g. presenceStatus - incoming otrContact can be offline?
                if (resource != null && other.resource != null && resource.resourceName == other.resource.resourceName) true else resource == null && other.resource == null
            } else false
        }

        override fun hashCode(): Int {
            var result = 17
            result = 31 * result + (contact?.hashCode() ?: 0)
            result = 31 * result + (resource?.hashCode() ?: 0)
            return result
        }
    }

    companion object {
        /**
         * A map that caches OtrContacts to minimize memory usage.
         */
        private val contactsMap = ConcurrentHashMap<Contact, MutableList<OtrContact>>()

        /**
         * Gets the `OtrContact` that represents this
         * [Contact, ContactResource] pair from the cache. If such pair does not
         * still exist it is then created and cached for further usage.
         *
         * @param contact the `Contact` that the returned OtrContact represents.
         * @param resource the `ContactResource` that the returned OtrContact represents.
         * @return The `OtrContact` that represents this [Contact, ContactResource] pair.
         */
        fun getOtrContact(contact: Contact?, resource: ContactResource?): OtrContact? {
            if (contact == null) return null
            var otrContactsList = contactsMap[contact]
            return if (otrContactsList != null) {
                for (otrContact in otrContactsList) {
                    if (resource != null && resource == otrContact.resource) return otrContact
                }

                // Create and cache new if none found
                val otrContact = OtrContact(contact, resource)
                synchronized(otrContactsList) { otrContactsList!!.add(otrContact) }
                otrContact
            } else {
                synchronized(contactsMap) {
                    if (!contactsMap.containsKey(contact)) {
                        otrContactsList = ArrayList()
                        contactsMap[contact] = otrContactsList!!
                    }
                }
                getOtrContact(contact, resource)
            }
        }
    }
}