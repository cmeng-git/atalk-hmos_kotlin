/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.util.event

import java.awt.event.ActionListener
import javax.swing.event.DocumentListener

/**
 * Utility class to that stores the list of [EventListener]s. Provides add/remove
 * and notify all operations.
 *
 * @param <T> the event object class
 * @author Pawel Domas
</T> */
class EventListenerList<T> {
    /**
     * The list of [EventListener]
     */
    private val listeners = ArrayList<EventListener<T>>()

    /**
     * Adds the `listener` to the list
     *
     * @param listener the [EventListener] that will be added to the list
     */
    fun addEventListener(listener: EventListener<T>) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    /**
     * Removes the `listener` from the list
     *
     * @param listener the [EventListener] that will be removed from the list
     */
    fun removeEventListener(listener: EventListener<T>) {
        listeners.remove(listener)
    }

    /**
     * Runs the event change notification on listeners list
     *
     * @param eventObject the source object of the event
     */
    fun notifyEventListeners(eventObject: T) {
        for (l in listeners) {
            l.onChangeEvent(eventObject)
        }
    }

    /**
     * Clears the listeners list
     */
    fun clear() {
        listeners.clear()
    }

    fun add(class1: Class<DocumentListener?>?, paramDocumentListener: DocumentListener?) {
        // TODO Auto-generated method stub
    }

    fun add(class1: Class<ActionListener?>?, paramActionListener: ActionListener?) {
        // TODO Auto-generated method stub
    }
}