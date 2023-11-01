/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.systray

import net.java.sip.communicator.service.systray.event.SystrayPopupMessageListener

/**
 * The `SystrayService` manages the system tray icon, menu and messages.
 * It is meant to be used by all bundles that want to show a system tray message.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface SystrayService {
    /**
     * Shows the given `PopupMessage`
     *
     * @param popupMessage the message to show
     */
    fun showPopupMessage(popupMessage: PopupMessage)

    /**
     * Adds a listener for `SystrayPopupMessageEvent`s posted when user
     * clicks on the system tray popup message.
     *
     * @param listener the listener to add
     */
    fun addPopupMessageListener(listener: SystrayPopupMessageListener)

    /**
     * Removes a listener previously added with `addPopupMessageListener`.
     *
     * @param listener the listener to remove
     */
    fun removePopupMessageListener(listener: SystrayPopupMessageListener)

    /**
     * Set the handler which will be used for popup message
     * @param popupHandler the handler to use
     * @return the previously used popup handler
     */
    fun setActivePopupMessageHandler(popupHandler: PopupMessageHandler?): PopupMessageHandler?

    /**
     * Get the handler currently used by the systray service for popup message
     * @return the handler used by the systray service
     */
    val activePopupMessageHandler: PopupMessageHandler?

    /**
     * Sets a new icon to the systray.
     *
     * @param imageType the type of the image to set
     */
    fun setSystrayIcon(imageType: Int)

    /**
     * Selects the best available popup message handler
     */
    fun selectBestPopupMessageHandler()

    companion object {
        /**
         * Message type corresponding to an error message.
         */
        const val ERROR_MESSAGE_TYPE = 0

        /**
         * Message type corresponding to an information message.
         */
        const val INFORMATION_MESSAGE_TYPE = 1

        /**
         * Message type corresponding to a warning message.
         */
        const val WARNING_MESSAGE_TYPE = 2

        /**
         * Message type corresponding to a missed call message.
         */
        const val MISSED_CALL_MESSAGE_TYPE = 3

        /**
         * Message type corresponding to a Jingle <session-initiate></session-initiate> call message.
         */
        const val JINGLE_INCOMING_CALL = 4

        /**
         * Message type corresponding to a JingleMessage <propose></propose> call message.
         */
        const val JINGLE_MESSAGE_PROPOSE = 5

        /**
         * Message type corresponding to JINGLE_INCOMING_CALL arise due to JingleMessage <accept></accept>
         * or via HeadsUp notification where user has already accepted the call.
         */
        const val HEADS_UP_INCOMING_CALL = 6

        /**
         * Message type is not accessible.
         */
        const val NONE_MESSAGE_TYPE = -1

        /**
         * Image type corresponding to the jitsi icon
         */
        const val SC_IMG_TYPE = 0

        /**
         * Image type corresponding to the jitsi offline icon
         */
        const val SC_IMG_OFFLINE_TYPE = 2

        /**
         * Image type corresponding to the jitsi away icon
         */
        const val SC_IMG_AWAY_TYPE = 3

        /**
         * Image type corresponding to the jitsi free for chat icon
         */
        const val SC_IMG_FFC_TYPE = 4

        /**
         * Image type corresponding to the jitsi do not disturb icon
         */
        const val SC_IMG_DND_TYPE = 5

        /**
         * Image type corresponding to the jitsi away icon
         */
        const val SC_IMG_EXTENDED_AWAY_TYPE = 6

        /**
         * Image type corresponding to the envelope icon
         */
        const val ENVELOPE_IMG_TYPE = 1
    }
}