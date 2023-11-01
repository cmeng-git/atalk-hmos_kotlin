/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidtray

import net.java.sip.communicator.service.systray.AbstractSystrayService
import net.java.sip.communicator.service.systray.PopupMessageHandler
import net.java.sip.communicator.service.systray.SystrayService.Companion.ENVELOPE_IMG_TYPE
import net.java.sip.communicator.service.systray.SystrayService.Companion.SC_IMG_AWAY_TYPE
import net.java.sip.communicator.service.systray.SystrayService.Companion.SC_IMG_DND_TYPE
import net.java.sip.communicator.service.systray.SystrayService.Companion.SC_IMG_FFC_TYPE
import net.java.sip.communicator.service.systray.SystrayService.Companion.SC_IMG_OFFLINE_TYPE
import net.java.sip.communicator.service.systray.SystrayService.Companion.SC_IMG_TYPE
import net.java.sip.communicator.service.systray.event.SystrayPopupMessageEvent
import net.java.sip.communicator.service.systray.event.SystrayPopupMessageListener
import org.atalk.service.osgi.OSGiService

/**
 * Android system tray implementation. Makes use of status bar notifications to provide tray functionality.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class SystrayServiceImpl : AbstractSystrayService(AndroidTrayActivator.bundleContext!!) {
    /**
     * Popup message handler.
     */
    private val trayPopupHandler = NotificationPopupHandler()

    /**
     * Popup message click listener impl.
     */
    private val popupMessageListener = PopupListenerImpl()

    /**
     * `BroadcastReceiver` that catches "on click" and "clear" events for displayed notifications.
     */
    private val clickReceiver: PopupClickReceiver

    /**
     * Creates new instance of `SystrayServiceImpl`.
     */
    init {
        AndroidTrayActivator.Companion.bundleContext!!.registerService(PopupMessageHandler::class.java, trayPopupHandler, null)
        initHandlers()
        clickReceiver = PopupClickReceiver(trayPopupHandler)
    }

    /**
     * Set the handler which will be used for popup message
     *
     * @param popupHandler the handler to set. providing a null handler is like disabling popup.
     * @return the previously used popup handler.
     */
    override fun setActivePopupMessageHandler(popupHandler: PopupMessageHandler?): PopupMessageHandler? {
        val oldHandler = activePopupMessageHandler

        oldHandler?.removePopupMessageListener(popupMessageListener)
        popupHandler?.addPopupMessageListener(popupMessageListener)
        return super.setActivePopupMessageHandler(popupHandler)
    }

    /**
     * Starts the service.
     */
    fun start() {
        clickReceiver.registerReceiver()
    }

    /**
     * Stops the service.
     */
    fun stop() {
        clickReceiver.unregisterReceiver()
        trayPopupHandler.dispose()
    }

    /**
     * {@inheritDoc}
     */
    override fun setSystrayIcon(imageType: Int) {
        System.err.println("Requested icon: $imageType")
        when (imageType) {
            SC_IMG_AWAY_TYPE -> {}
            SC_IMG_DND_TYPE -> {}
            SC_IMG_FFC_TYPE -> {}
            SC_IMG_OFFLINE_TYPE -> {}
            SC_IMG_TYPE -> {}
            ENVELOPE_IMG_TYPE -> {}
        }
    }

    /**
     * Class implements `SystrayPopupMessageListener` in order to display chat
     * `Activity` when popup is clicked.
     */
    private inner class PopupListenerImpl : SystrayPopupMessageListener {
        /**
         * Indicates that user has clicked on the systray popup message.
         *
         * @param evt the event triggered when user clicks on the systray popup message
         */
        override fun popupMessageClicked(evt: SystrayPopupMessageEvent?) {
            // TODO: notifications now fire intents directly and
            // SystrayPopupMessageListener is omitted. Make sure that this code
            // is no longer required and remove this code completely.

            /*
             * Object src = evt.getSource(); PopupMessage message = null; if(src instanceof PopupMessage) { message =
             * (PopupMessage) evt.getSource(); }
             *
             * Object tag = evt.getTag(); if(tag instanceof Contact) { Contact contact = (Contact) tag; MetaContact
             * metaContact = AndroidGUIActivator.getContactListService() .findMetaContactByContact(contact);
             * if(metaContact == null) { Timber.e("Meta contact not found for "+contact); return; }
             *
             * Intent targetIntent; String group = message != null ? message.getGroup() : null;
             *
             * if(AndroidNotifications.MESSAGE_GROUP.equals(group)) { targetIntent =
             * ChatSessionManager.getChatIntent(metaContact);
             *
             * if(targetIntent == null) { Timber.e( "Failed to create chat with " + metaContact); } } else { //
             * TODO: add call history intent here targetIntent = null; }
             *
             * if(targetIntent != null) { aTalkApp .getGlobalContext().startActivity(targetIntent); }
             *
             * return; }
             *
             * // Displays popup message details when the notification is clicked if(message != null) {
             * DialogActivity.showDialog( aTalkApp.globalContext, message.getMessageTitle(),
             * message.getMessage()); }
             */
        }

    }

    companion object {// Use service icon if available

        // There is not service icon available
        /**
         * Returns id of general notification that is bound to aTalk icon.
         *
         * @return id of general notification that is bound to aTalk icon.
         */
        /**
         * Id of Jitsi icon notification
         */
        var generalNotificationId = -1
            get() {
                val serviceIcondId = OSGiService.generalNotificationId

                // Use service icon if available
                if (serviceIcondId != -1 && field != serviceIcondId) {
                    field = serviceIcondId
                }

                // There is not service icon available
                if (field == -1) {
                    field = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                }
                return field
            }
            private set
    }
}