/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event

import java.util.*

/**
 * The `ContainerEvent` indicates that a change in a `container`
 * such a `Component` added or removed.
 */
class ContainerEvent(source: Any?, eventID: Int) : EventObject(source) {
    /**
     * Returns an event id specifying whether the type of this event (CONTAINER_ADDED or CONTAINER_REMOVED)
     * @return one of the CONTAINER_XXX int fields of this class.
     */
    /**
     * ID of the event.
     */
    var eventID = -1

    /**
     * Creates a new ContainerEvent according to the specified parameters.
     * @param source The containerID of the container that is added to supported containers.
     * @param eventID one of the CONTAINER_XXX static fields indicating the nature of the event.
     */
    init {
        this.eventID = eventID
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Indicates that the ContainerEvent instance was triggered by
         * adding a new container to the list of supported containers.
         */
        const val CONTAINER_ADDED = 1

        /**
         * Indicates that the ContainerEvent instance was triggered by the
         * removal of an existing container from the list of supported containers.
         */
        const val CONTAINER_REMOVED = 2
    }
}