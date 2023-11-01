/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.systray

import net.java.sip.communicator.service.systray.event.SystrayPopupMessageEvent
import net.java.sip.communicator.service.systray.event.SystrayPopupMessageListener
import java.util.*

/**
 * Abstract base implementation of `PopupMessageHandler` which
 * facilitates the full implementation of the interface.
 *
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
abstract class AbstractPopupMessageHandler : PopupMessageHandler {
    /**
     * The list of `SystrayPopupMessageListener`s registered with this
     * instance.
     */
    private val popupMessageListeners = Vector<SystrayPopupMessageListener>()

    /**
     * Adds a `SystrayPopupMessageListener` to this instance so that it
     * receives `SystrayPopupMessageEvent`s.
     *
     * @param listener the `SystrayPopupMessageListener` to be added to this instance
     * @see PopupMessageHandler.addPopupMessageListener
     */
    override fun addPopupMessageListener(listener: SystrayPopupMessageListener) {
        synchronized(popupMessageListeners) { if (!popupMessageListeners.contains(listener)) popupMessageListeners.add(listener) }
    }

    /**
     * Notifies the `SystrayPopupMessageListener`s registered with this
     * instance that a `SystrayPopupMessageEvent` has occurred.
     *
     * @param evt the `SystrayPopupMessageEvent` to be fired to the
     * `SystrayPopupMessageListener`s registered with this instance
     */
    protected fun firePopupMessageClicked(evt: SystrayPopupMessageEvent?) {
        var listeners: List<SystrayPopupMessageListener>
        synchronized(popupMessageListeners) { listeners = ArrayList(popupMessageListeners) }
        for (listener in listeners) listener.popupMessageClicked(evt)
    }

    /**
     * Removes a `SystrayPopupMessageListener` from this instance so that
     * it no longer receives `SystrayPopupMessageEvent`s.
     *
     * @param listener the `SystrayPopupMessageListener` to be removed from this instance
     * @see PopupMessageHandler.removePopupMessageListener
     */
    override fun removePopupMessageListener(listener: SystrayPopupMessageListener) {
        synchronized(popupMessageListeners) { popupMessageListeners.remove(listener) }
    }
}