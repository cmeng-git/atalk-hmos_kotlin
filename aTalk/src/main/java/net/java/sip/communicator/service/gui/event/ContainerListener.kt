/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event

import java.util.*

/**
 * Listens for all events caused by a change in the supported containers list.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface ContainerListener : EventListener {
    /**
     * Indicates that a container was added to the list of supported containers.
     * @param event the ContainerEvent containing the corresponding container.
     */
    fun containerAdded(event: ContainerEvent?)

    /**
     * Indicates that a container was removed from the list of supported containers.
     * @param event the ContainerEvent containing the corresponding container.
     */
    fun containerRemoved(event: ContainerEvent?)
}