/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.notification

import net.java.sip.communicator.service.notification.NotificationAction
import net.java.sip.communicator.service.notification.NotificationData
import net.java.sip.communicator.service.notification.PopupMessageNotificationAction
import net.java.sip.communicator.service.notification.PopupMessageNotificationHandler
import net.java.sip.communicator.service.systray.PopupMessage
import net.java.sip.communicator.service.systray.event.SystrayPopupMessageListener
import org.apache.commons.lang3.StringUtils

/**
 * An implementation of the `PopupMessageNotificationHandler` interface.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class PopupMessageNotificationHandlerImpl : PopupMessageNotificationHandler {
    /**
     * Shows the given `PopupMessage`
     *
     * @param action the action to act upon
     * @param data `NotificationData` that contains the name/key, icon and extra info for popup message
     */
    override fun popupMessage(action: PopupMessageNotificationAction, data: NotificationData) {
        val sysTray = NotificationActivator.systray
                ?: return

        val message = data.message
        if (StringUtils.isNotEmpty(message)) {
            val popupMsg = PopupMessage(data.title, message, data.icon,
                    data.getExtra(NotificationData.POPUP_MESSAGE_HANDLER_TAG_EXTRA))
            popupMsg.eventType = data.eventType
            popupMsg.messageType = data.messageType
            popupMsg.timeout = action.timeout
            popupMsg.group = action.groupName
            sysTray.showPopupMessage(popupMsg)
        }
//        else if (message == null) {
//            Timber.e("Message is null!")
//        }
    }

    /**
     * Adds a listener for `SystrayPopupMessageEvent`s posted when user clicks on the system tray popup message.
     *
     * @param listener the listener to add
     */
    override fun addPopupMessageListener(listener: SystrayPopupMessageListener?) {
        val sysTray = NotificationActivator.systray
                ?: return
        sysTray.addPopupMessageListener(listener!!)
    }

    /**
     * Removes a listener previously added with `addPopupMessageListener`.
     *
     * @param listener the listener to remove
     */
    override fun removePopupMessageListener(listener: SystrayPopupMessageListener?) {
        val sysTray = NotificationActivator.systray
                ?: return
        sysTray.removePopupMessageListener(listener!!)
    }

    override val actionType: String
        get() = NotificationAction.ACTION_POPUP_MESSAGE
}