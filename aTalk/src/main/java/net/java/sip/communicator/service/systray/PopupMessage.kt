/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.systray

import javax.swing.JComponent

/**
 * The `PopupMessage` class encloses information to show in a popup.
 * While a message title and a message body are mandatory information,
 * a popup message could provides more stuffs like a component or an image which
 * may be used by a `PopupMessageHandler` capable to handle it.
 *
 * @author Symphorien Wanko
 */
class PopupMessage
/**
 * Creates a `PopupMessage` with the given title and message inside.
 *
 * @param title title of the message
 * @param message message to show in the systray
 */
(
        /**
         * Title of the message.
         */
        var messageTitle: String,
        /**
         * Message to show in the popup.
         */
        var message: String) {
    /**
     * Gets the type of the event that triggers the popup.
     *
     * @return the eventType of the popup
     */
    /**
     * Sets the event type that trigger the popup.
     *
     * @param evtType the event type of the popup.
     */
    /**
     * The type of the event that the popup message for.
     *
     * @see net.java.sip.communicator.plugin.notificationwiring.NotificationManager
     */
    var eventType: String? = null
    /**
     * Returns the message contained in this popup.
     *
     * @return the message contained in this popup
     */
    /**
     * Sets the message to show in the popup.
     *
     * @param message the message to show in the popup
     */
    /**
     * Returns the title of this popup message.
     *
     * @return the title of this popup message
     */
    /**
     * Sets the title of this popup message.
     *
     * @param messageTitle the title to set
     */
    /**
     * Returns the icon of this popup message.
     *
     * @return the icon of this popup message
     */
    /**
     * Sets the icon of this popup message.
     *
     * @param imageIcon the icon to set
     */
    /**
     * An icon representing the contact from which the notification comes.
     */
    var icon: ByteArray? = null
    /**
     * Returns the component contained in this popup message.
     *
     * @return the component contained in this popup message.
     */
    /**
     * Sets the component to be showed in this popup message.
     *
     * @param component the component to set
     */
    /**
     * A ready to show `JComponet` for this `PopupMessage`.
     */
    var component: JComponent? = null
    /**
     * Returns the type of this popup message.
     *
     * @return the type of this popup message.
     */
    /**
     * Sets the type of this popup message.
     *
     * @param messageType the type to set
     */
    /**
     * The type of the message.
     */
    var messageType = 0
    /**
     * Returns the object used to tag this `PopupMessage`.
     *
     * @return the object used to tag this `PopupMessage`
     */
    /**
     * Sets the object used to tag this popup message.
     *
     * @param tag the object to set
     */
    /**
     * Additional info to be used by the `PopupMessageHandler`.
     */
    var tag: Any? = null
    /**
     * Returns suggested timeout value in ms for hiding the popup if not clicked by the user.
     *
     * @return timeout for hiding the popup if not clicked by the user in ms.
     */
    /**
     * Sets suggested timeout for hiding the popup if not clicked by the user.
     *
     * @param timeout time value in ms for hiding the popup, -1 for infinity.
     */
    /**
     * Suggested timeout value in ms for hiding the popup if not clicked (-1 for infinity)
     */
    var timeout: Long = 0
    /**
     * Returns name of the popup group.
     *
     * @return name of the popup group.
     */
    /**
     * Set name of the group to which this popup will belong.
     *
     * @param group the popup group name to set.
     */
    /**
     * Name of the popup group to which this popup will belong. Used to group notifications on Android.
     */
    var group: String? = null

    /**
     * Creates a system tray message with the given title and message content.
     * The message type will affect the icon used to present the message.
     *
     * @param title the title, which will be shown
     * @param message the content of the message to display
     * @param messageType the message type; one of XXX_MESSAGE_TYPE constants declared in `SystrayService`
     */
    constructor(title: String, message: String, messageType: Int) : this(title, message) {
        this.messageType = messageType
    }

    /**
     * Creates a new `PopupMessage` with the given title, message and
     * icon.
     *
     * @param title the title of the message
     * @param message message to show in the systray
     * @param imageIcon an incon to show in the popup message.
     */
    constructor(title: String, message: String, imageIcon: ByteArray) : this(title, message) {
        icon = imageIcon
    }

    /**
     * Creates a new `PopupMessage` with the given
     * `JComponent` as its content. This constructor also takes a title
     * and a message as replacements in cases the component is not usable.
     *
     * @param component the component to put in the `PopupMessage`
     * @param title of the message
     * @param message message to use in place of the component
     */
    constructor(component: JComponent?, title: String, message: String) : this(title, message) {
        this.component = component
    }

    /**
     * Creates a new `PopupMessage` with the given
     * `JComponent` as its content. This constructor also takes a title
     * and a message as replacements in cases the component is not usable.
     *
     * @param title of the message
     * @param message the message to show in this popup
     * @param tag additional info to be used by the `PopupMessageHandler`
     */
    constructor(title: String, message: String, tag: Any?) : this(title, message) {
        this.tag = tag
    }

    /**
     * Creates a new `PopupMessage` with the given
     * `JComponent` as its content. This constructor also takes a title
     * and a message as replacements in cases the component is not usable.
     *
     * @param title the title of the message
     * @param message the message to show in this popup
     * @param imageIcon the image icon to show in this popup message
     * @param tag additional info to be used by the `PopupMessageHandler`
     */
    constructor(title: String, message: String, imageIcon: ByteArray?, tag: Any?) : this(title, message, imageIcon) {
        this.tag = tag
    }
}