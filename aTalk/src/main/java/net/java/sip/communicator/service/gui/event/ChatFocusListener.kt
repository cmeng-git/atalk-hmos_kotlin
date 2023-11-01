/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event

import java.util.*

/**
 * The listener interface for receiving focus events on a `Chat`.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface ChatFocusListener : EventListener {
    /**
     * Indicates that a `Chat` has gained the focus.
     *
     * @param event the ChatFocusEvent containing the corresponding chat.
     */
    fun chatFocusGained(event: ChatFocusEvent?)

    /**
     * Indicates that a `Chat` has lost the focus.
     *
     * @param event the ChatFocusEvent containing the corresponding chat.
     */
    fun chatFocusLost(event: ChatFocusEvent?)
}