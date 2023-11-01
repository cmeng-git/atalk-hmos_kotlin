/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification

/**
 * An implementation of the `PopupMessageNotificationHandler` interface.
 *
 * @author Yana Stamcheva
 */
class PopupMessageNotificationAction
/**
 * Creates an instance of `PopupMessageNotificationHandlerImpl` by specifying
 * the default message to use if no message is specified.
 *
 * @param defaultMessage the default message to use if no message is specified
 */
(
        /**
         * Return the default message to use if no message is specified.
         *
         * @return the default message to use if no message is specified.
         */
        val defaultMessage: String?) : NotificationAction(NotificationAction.ACTION_POPUP_MESSAGE) {
    /**
     * Returns suggested timeout value in ms for hiding the popup if not clicked by the user.
     *
     * @return timeout value in ms for hiding the popup, -1 for infinity.
     */
    /**
     * Suggested timeout in ms for hiding the popup if not clicked by the user.
     */
    var timeout: Long = -1
        private set
    /**
     * Returns name of popup group that will be used for merging notifications.
     *
     * @return name of popup group that will be used for merging notifications.
     */
    /**
     * Sets the name of the group that will be used for merging popups.
     *
     * @param groupName name of popup group to set.
     */
    /**
     * Group name used to group notifications on Android.
     */
    var groupName: String? = null

    /**
     * Creates an instance of `PopupMessageNotificationHandlerImpl` by specifying
     * the default message to use if no message is specified.
     *
     * @param defaultMessage the default message to use if no message is specified
     * @param timeout suggested timeout in ms for hiding the popup if not clicked by the user, -1 for infinity
     */
    constructor(defaultMessage: String?, timeout: Long) : this(defaultMessage) {
        this.timeout = timeout
    }

    /**
     * Creates an instance of `PopupMessageNotificationHandlerImpl` by specifying
     * the default message to use if no message is specified.
     *
     * @param defaultMessage the default message to use if no message is specified
     * @param timeout suggested timeout in ms for hiding the popup if not clicked by the user, -1 for infinity
     * @param groupName name of the group that will be used for merging popups,
     * it is also one of the android notification channel
     */
    constructor(defaultMessage: String?, timeout: Long, groupName: String?) : this(defaultMessage, timeout) {
        this.groupName = groupName
    }
}