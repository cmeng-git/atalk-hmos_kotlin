/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.ContactResourceEvent
import net.java.sip.communicator.service.protocol.event.ContactResourceListener

/**
 * An abstract base implementation of the [Contact] interface which is to aid implementers.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class AbstractContact : Contact {
    /**
     * The list of `ContactResourceListener`-s registered in this contact.
     */
    private val resourceListeners = ArrayList<ContactResourceListener>()

    override fun equals(other: Any?): Boolean {
        return when {
            other == null -> false
            other == this -> true
            other.javaClass != javaClass -> false
            else -> {
                val contact = other as Contact
                (contact.protocolProvider == protocolProvider) && (contact.address == address)
            }
        }
    }

    override fun hashCode(): Int {
        var hashCode = 0
        hashCode += protocolProvider.hashCode()
        hashCode += address.hashCode()
        return hashCode
    }

    /**
     * Indicates if this contact supports resources.
     *
     *
     * This default implementation indicates no support for contact resources.
     *
     * @return `true` if this contact supports resources, `false` otherwise
     */
    override var isSupportResources = false

    /**
     * Returns a collection of resources supported by this contact or null if it doesn't support resources.
     *
     *
     * This default implementation indicates no support for contact resources.
     *
     * @return a collection of resources supported by this contact or null if it doesn't support resources
     */
    override fun getResources(): Collection<ContactResource?>? {
        return null
    }

    /**
     * Adds the given `ContactResourceListener` to listen for events related to contact resources changes.
     *
     * @param l the `ContactResourceListener` to add
     */
    override fun addResourceListener(l: ContactResourceListener) {
        synchronized(resourceListeners) { resourceListeners.add(l) }
    }

    /**
     * Removes the given `ContactResourceListener` listening for events related to contact resources changes.
     *
     * @param l the `ContactResourceListener` to remove
     */
    override fun removeResourceListener(l: ContactResourceListener) {
        synchronized(resourceListeners) { resourceListeners.remove(l) }
    }

    /**
     * Notifies all registered `ContactResourceListener`s that an event has occurred.
     *
     * @param event the `ContactResourceEvent` to fire notification for
     */
    protected open fun fireContactResourceEvent(event: ContactResourceEvent) {
        var listeners: Collection<ContactResourceListener>
        synchronized(resourceListeners) { listeners = ArrayList(resourceListeners) }
        val listenersIter = listeners.iterator()
        while (listenersIter.hasNext()) {
            if (event.eventType == ContactResourceEvent.Companion.RESOURCE_ADDED) listenersIter.next().contactResourceAdded(event) else if (event.eventType == ContactResourceEvent.Companion.RESOURCE_REMOVED) listenersIter.next().contactResourceRemoved(event) else if (event.eventType == ContactResourceEvent.Companion.RESOURCE_MODIFIED) listenersIter.next().contactResourceModified(event)
        }
    }

    /**
     * Returns the same as `getAddress` function.
     *
     * @return the address of the contact.
     */
    override fun getPersistableAddress(): String? {
        return address
    }

    /**
     * Whether contact is mobile one. Logged in only from mobile device.
     *
     * @return whether contact is mobile one.
     */
    override var isMobile = false

    /**
     * A reference copy of last fetch contact activity. The value is set to -1 when contact is online
     * so a new lastActivity is fetched when the user is offline again
     */
    override var lastActiveTime = -1L
}