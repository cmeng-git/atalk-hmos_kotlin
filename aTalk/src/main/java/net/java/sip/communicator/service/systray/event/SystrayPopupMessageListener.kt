/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.systray.event

import java.util.*

/**
 * Listens for `SystrayPopupMessageEvent`s posted when user clicks on the system tray popup message.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface SystrayPopupMessageListener : EventListener {
    /**
     * Indicates that user has clicked on the systray popup message.
     *
     * @param evt the event triggered when user clicks on the systray popup message
     */
    fun popupMessageClicked(evt: SystrayPopupMessageEvent?)
}