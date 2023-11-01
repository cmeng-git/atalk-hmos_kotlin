/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event

import net.java.sip.communicator.service.gui.Chat
import java.util.*

/**
 * The `ChatFocusEvent` indicates that a `Chat` has gained or lost the current focus.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class ChatFocusEvent
/**
 * Creates a new `ChatFocusEvent` according to the specified parameters.
 *
 * @param source The `Chat` that triggers the event.
 * @param eventID one of the FOCUS_XXX static fields indicating the nature of the event.
 */
(source: Any?,
        /**
         * ID of the event.
         */
        val eventID: Int) : EventObject(source) {
    /**
     * Returns an event id specifying what is the type of this event (FOCUS_GAINED or FOCUS_LOST)
     *
     * @return one of the REGISTRATION_XXX int fields of this class.
     */

    /**
     * Returns the `Chat` object that corresponds to this event.
     *
     * @return the `Chat` object that corresponds to this event
     */
    val chat: Chat
        get() = source as Chat

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Indicates that the ChatFocusEvent instance was triggered by `Chat` gaining the focus.
         */
        const val FOCUS_GAINED = 1

        /**
         * Indicates that the ChatFocusEvent instance was triggered by `Chat` losing the focus.
         */
        const val FOCUS_LOST = 2
    }
}