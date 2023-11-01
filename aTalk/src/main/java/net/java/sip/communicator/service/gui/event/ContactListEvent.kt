/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event

import net.java.sip.communicator.service.gui.UIContact
import net.java.sip.communicator.service.gui.UIGroup
import java.util.*

/**
 * The `ContactListEvent` is triggered when a contact or a group is clicked in the contact list.
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class ContactListEvent(source: Any?, eventID: Int, clickCount: Int) : EventObject(source) {
    /**
     * Returns an event id specifying whether the type of this event
     * (CONTACT_SELECTED or PROTOCOL_CONTACT_SELECTED)
     * @return one of the XXX_SELECTED int fields of this class.
     */
    var eventID = -1
    /**
     * Returns the number of click of this event.
     * @return the number of click of this event.
     */
    /**
     * Indicated the number of click accompanying the event
     */
    val clickCount: Int

    /**
     * Creates a new ContactListEvent according to the specified parameters.
     * @param source the MetaContact which was selected
     * @param eventID one of the XXX_SELECTED static fields indicating the
     * nature of the event.
     * @param clickCount the number of clicks that was produced when clicking
     * over the contact list
     */
    init {
        this.eventID = eventID
        this.clickCount = clickCount
    }

    /**
     * Returns the `UIContactDescriptor` for which this event occured.
     * @return the UIContactDescriptor for which this event occured
     */
    val sourceContact: UIContact?
        get() = if (getSource() is UIContact) getSource() as UIContact else null

    /**
     * Returns the `UIGroupDescriptor` for which this event occured.
     * @return the `UIGroupDescriptor` for which this event occured
     */
    val sourceGroup: UIGroup?
        get() = if (getSource() is UIGroup) getSource() as UIGroup else null

    companion object {
        /**
         * Indicates that the ContactListEvent instance was triggered by
         * selecting a contact in the contact list.
         */
        const val CONTACT_CLICKED = 1

        /**
         * Indicates that the ContactListEvent instance was triggered by selecting
         * a group in the contact list.
         */
        const val GROUP_CLICKED = 2

        /**
         * Indicates that the ContactListEvent instance was triggered by
         * selecting a contact in the contact list.
         */
        const val CONTACT_SELECTED = 3

        /**
         * Indicates that the ContactListEvent instance was triggered by selecting
         * a group in the contact list.
         */
        const val GROUP_SELECTED = 4
    }
}