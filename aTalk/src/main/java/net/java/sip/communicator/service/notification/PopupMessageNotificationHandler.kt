/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification

import net.java.sip.communicator.service.systray.event.SystrayPopupMessageListener

/**
 * The `PopupMessageNotificationHandler` interface is meant to be implemented by the notification
 * bundle in order to provide handling of popup message actions.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface PopupMessageNotificationHandler : NotificationHandler {
    /**
     * Shows the given `PopupMessage`
     *
     * @param action the action to act upon
     * @param data `NotificationData` that contains the name/key, icon and extra info for popup notification
     */
    fun popupMessage(action: PopupMessageNotificationAction, data: NotificationData)

    /**
     * Adds a listener for `SystrayPopupMessageEvent`s posted when user clicks on the system tray popup message.
     *
     * @param listener the listener to add
     */
    fun addPopupMessageListener(listener: SystrayPopupMessageListener?)

    /**
     * Removes a listener previously added with `addPopupMessageListener`.
     *
     * @param listener the listener to remove
     */
    fun removePopupMessageListener(listener: SystrayPopupMessageListener?)
}