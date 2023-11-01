/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event

import java.util.*

/**
 * Listens for events coming from mouse events over the contact list. For
 * example a contact been clicked or a group been selected.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface ContactListListener : EventListener {
    /**
     * Indicates that a group has been selected.
     *
     * @param evt the `ContactListEvent` that has been triggered from
     * the user selection
     */
    fun groupClicked(evt: ContactListEvent?)

    /**
     * Indicates that a group has been selected.
     *
     * @param evt the `ContactListEvent` that has been triggered from
     * the user selection
     */
    fun groupSelected(evt: ContactListEvent?)

    /**
     * Indicates that a contact has been clicked.
     *
     * @param evt the `ContactListEvent` that has been triggered from
     * the user click
     */
    fun contactClicked(evt: ContactListEvent?)

    /**
     * Indicates that a contact has been selected.
     *
     * @param evt the `ContactListEvent` that has been triggered from
     * the user selection
     */
    fun contactSelected(evt: ContactListEvent?)
}