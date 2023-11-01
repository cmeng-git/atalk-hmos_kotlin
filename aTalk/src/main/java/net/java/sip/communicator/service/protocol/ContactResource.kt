/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * The `ContactResource` class represents a resource, from which a `Contact` is connected.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
@Suppress("LeakingThis")
open class ContactResource {
    /**
     * Returns the `Contact`, this resources belongs to.
     *
     * @return the `Contact`, this resources belongs to
     */
    /**
     * The contact, to which this resource belongs.
     */
    lateinit var contact: Contact
        private set

    /**
     * The name of this contact resource.
     */
    var resourceName: String? = null
        private set

    /**
     * The presence status of this contact resource.
     */
    open lateinit var presenceStatus: PresenceStatus

    /**
     * The priority of this contact source.
     */
    open var priority = 0

    /**
     * Whether this contact resource is a mobile one.
     */
    open var isMobile = false

    /**
     * Creates an empty instance of `ContactResource` representing the base resource.
     */
    constructor()

    /**
     * Creates a `ContactResource` by specifying the `resourceName`, the `presenceStatus` and the `priority`.
     *
     * @param contact the parent `Contact` this resource is about
     * @param resourceName the name of this resource
     * @param presenceStatus the presence status of this resource
     * @param priority the priority of this resource
     */
    constructor(contact: Contact, resourceName: String, presenceStatus: PresenceStatus, priority: Int, mobile: Boolean) {
        this.contact = contact
        this.resourceName = resourceName
        this.presenceStatus = presenceStatus
        this.priority = priority
        this.isMobile = mobile
    }

    companion object {
        /**
         * A static instance of this class representing the base resource. If this base resource is
         * passed as a parameter for any operation (send message, call) the operation should explicitly
         * use the base contact address. This is meant to force a call or a message sending to all the
         * resources for the corresponding contact.
         */
        var BASE_RESOURCE = ContactResource()
    }
}