/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification

import net.java.sip.communicator.service.notification.event.NotificationActionTypeEvent
import net.java.sip.communicator.service.notification.event.NotificationEventTypeEvent
import java.util.*

/**
 * The `NotificationChangeListener` is notified any time an action type or an event type is added,
 * removed or changed.
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface NotificationChangeListener : EventListener {
    /**
     * This method gets called when a new notification action has been defined for a particular event type.
     *
     * @param event the `NotificationActionTypeEvent`, which is dispatched when a new action has been added.
     */
    fun actionAdded(event: NotificationActionTypeEvent)

    /**
     * This method gets called when a notification action for a particular event type has been removed.
     *
     * @param event the `NotificationActionTypeEvent`, which is dispatched when an action has been removed.
     */
    fun actionRemoved(event: NotificationActionTypeEvent)

    /**
     * This method gets called when a notification action for a particular event type has been changed
     * (for example the corresponding descriptor has changed).
     *
     * @param event the `NotificationActionTypeEvent`, which is dispatched when an action has been changed.
     */
    fun actionChanged(event: NotificationActionTypeEvent)

    /**
     * This method gets called when a new event type has been added.
     *
     * @param event the `NotificationEventTypeEvent`, which is dispatched when a new event type has been added
     */
    fun eventTypeAdded(event: NotificationEventTypeEvent)

    /**
     * This method gets called when an event type has been removed.
     *
     * @param event the `NotificationEventTypeEvent`, which is dispatched when an event type has been removed.
     */
    fun eventTypeRemoved(event: NotificationEventTypeEvent)
}