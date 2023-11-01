/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event

import net.java.sip.communicator.service.gui.Chat

/**
 * Listens to the creation and closing of `Chat`s.
 *
 * @author Damian Johnson
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface ChatListener {
    /**
     * Notifies this instance that a `Chat` has been closed.
     *
     * @param chat the `Chat` which has been closed
     */
    fun chatClosed(chat: Chat)

    /**
     * Notifies this instance that a new `Chat` has been created.
     *
     * @param chat the new `Chat` which has been created
     */
    fun chatCreated(chat: Chat)
}