/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.systray.event

import java.util.*

/**
 * The `SystrayPopupMessageEvent`s are posted when user clicks on the
 * system tray popup message.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class SystrayPopupMessageEvent
/**
 * Constructs a new `SystrayPopupMessageEvent` object.
 *
 * @param source object on which the Event initially occurred
 */ @JvmOverloads constructor(source: Any?,
        /** an object to distinguish this `SystrayPopupMessageEvent`  */
        var tag: Any? = null) : EventObject(source) {
    /**
     * @return the tag
     */
    /**
     * @param tag the tag to set
     */
    /**
     * Creates a new `SystrayPopupMessageEvent` with the source of the
     * event and additional info provided by the popup handler.
     * @param source the source of the event
     * @param tag additional info for listeners
     */
    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}