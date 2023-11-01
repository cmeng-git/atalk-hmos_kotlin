/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event

/**
 * The `MetaContactQueryListener` listens for events coming from a `MetaContactListService` filtering.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface MetaContactQueryListener {
    /**
     * Indicates that a `MetaContact` has been received for a search in the `MetaContactListService`.
     *
     * @param event the received `MetaContactQueryEvent`
     */
    fun metaContactReceived(event: MetaContactQueryEvent?)

    /**
     * Indicates that a `MetaGroup` has been received from a search in the `MetaContactListService`.
     *
     * @param event the `MetaGroupQueryEvent` that has been received
     */
    fun metaGroupReceived(event: MetaGroupQueryEvent?)

    /**
     * Indicates that a query has changed its status.
     *
     * @param event the `MetaContactQueryStatusEvent` that notified us
     */
    fun metaContactQueryStatusChanged(event: MetaContactQueryStatusEvent?)
}