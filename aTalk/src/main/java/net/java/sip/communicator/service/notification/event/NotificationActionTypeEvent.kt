/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification.event

import net.java.sip.communicator.service.notification.NotificationAction
import net.java.sip.communicator.service.notification.NotificationService
import java.util.*

/**
 * Fired any time an action type is added, removed or changed.
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 */
class NotificationActionTypeEvent(source: NotificationService?, eventType: String?,
        sourceEventType: String?, actionHandler: NotificationAction?) : EventObject(source) {
    /**
     * The type of the event that a new action is being added for.
     */
    private var sourceEventType: String? = null

    /**
     * The descriptor of the action (i.e. audio file uri, or a command line string) that will be performed when
     * notifications are being fired for the corresponding event type.
     */
    private var actionHandler: NotificationAction? = null

    /**
     * The type of this event. One of the static field constants declared in this class.
     */
    private var eventType: String? = null

    /**
     * Creates an instance of this event according to the specified type.
     *
     * @param source
     * the `NotificationService` that dispatched this event
     * @param eventType
     * the type of this event. One of the static fields declared in this class
     * @param sourceEventType
     * the event type for which this event occured
     * @param actionHandler
     * the `NotificationActionHandler` that handles the given action
     */
    init {
        this.eventType = eventType
        this.sourceEventType = sourceEventType
        this.actionHandler = actionHandler
    }

    /**
     * Returns the event type, to which the given action belongs.
     *
     * @return the event type, to which the given action belongs
     */
    fun getSourceEventType(): String? {
        return sourceEventType
    }

    /**
     * Returns the `NotificationActionHandler` that handles the action, for which this event is about.
     *
     * @return the `NotificationActionHandler` that handles the action, for which this event is about.
     */
    fun getActionHandler(): NotificationAction? {
        return actionHandler
    }

    /**
     * The type of this event. One of ACTION_XXX constants declared in this class.
     *
     * @return the type of this event
     */
    fun getEventType(): String? {
        return eventType
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Indicates that a new action is added to an event type.
         */
        const val ACTION_ADDED = "ActionAdded"

        /**
         * Indicates that an action was removed for a given event type.
         */
        const val ACTION_REMOVED = "ActionRemoved"

        /**
         * Indicates that an action for a given event type has changed. For example the action descriptor is changed.
         */
        const val ACTION_CHANGED = "ActionChanged"
    }
}