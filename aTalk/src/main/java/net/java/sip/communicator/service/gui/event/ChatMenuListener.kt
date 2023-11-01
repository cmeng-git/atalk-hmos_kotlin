/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event

import net.java.sip.communicator.service.gui.Chat
import java.awt.event.MouseEvent
import javax.swing.JMenuItem

/**
 * Listens for the chat's right click menu becoming visible so menu items can be offered.
 *
 * @author Damian Johnson
 * @author Eng Chong Meng
 */
interface ChatMenuListener {
    /**
     * Provides menu items that should be contributed.
     *
     * @param source chat to which the menu belongs
     * @param event mouse event triggering menu
     * @return elements that should be added to the menu
     */
    fun getMenuElements(source: Chat?, event: MouseEvent?): List<JMenuItem?>?
}