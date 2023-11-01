/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification.event

import net.java.sip.communicator.service.notification.NotificationService
import java.util.*

/**
 * Fired any time an event type is added or removed.
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 */
class NotificationEventTypeEvent(source: NotificationService?, eventType: String?, sourceEventType: String?) : EventObject(source) {
    /**
     * The type of the event that a new action is being added for.
     */
    private var sourceEventType: String? = null

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
     */
    init {
        this.eventType = eventType
        this.sourceEventType = sourceEventType
    }

    /**
     * Returns the `eventType`, for which this event is about.
     *
     * @return the `eventType`, for which this event is about.
     */
    fun getSourceEventType(): String? {
        return sourceEventType
    }

    /**
     * The type of this event. One of EVENT_TYPE_XXX constants declared in this class.
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
         * Indicates that a new event type is added.
         */
        const val EVENT_TYPE_ADDED = "EventTypeAdded"

        /**
         * Indicates that an event type was removed.
         */
        const val EVENT_TYPE_REMOVED = "EventTypeRemoved"
    }
}