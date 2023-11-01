/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification

import java.util.*

/**
 * Represents an event notification.
 *
 * @author Yana Stamcheva
 */
class Notification
/**
 * Creates an instance of `EventNotification` by specifying the event type as declared by the bundle
 * registering it.
 *
 * @param eventType
 * the name of the event
 */
(eventType: String?) {
    /**
     * Indicates if this event notification is currently active.
     *
     * @return true if this event notification is active, false otherwise.
     */
    /**
     * Activates or deactivates this event notification.
     *
     * @param isActive
     * indicates if this event notification is active
     */
    /**
     * Indicates if this event notification is currently active. By default all notifications are active.
     */
    var isActive = true

    /**
     * Contains all actions which will be executed when this event notification is fired.
     */
    private val actionsTable = Hashtable<String?, NotificationAction?>()

    /**
     * Adds the given `actionType` to the list of actions for this event notifications.
     *
     * @param action
     * the the handler that will process the given action type.
     *
     * @return the previous value of the actionHandler for the given actionType, if one existed, NULL if the actionType
     * is a new one
     */
    fun addAction(action: NotificationAction?): Any? {
        return actionsTable.put(action!!.actionType, action)
    }

    /**
     * Removes the action corresponding to the given `actionType`.
     *
     * @param actionType
     * one of NotificationService.ACTION_XXX constants
     */
    fun removeAction(actionType: String?) {
        actionsTable.remove(actionType)
    }

    /**
     * Returns the set of actions registered for this event notification.
     *
     * @return the set of actions registered for this event notification
     */
    val actions: Map<String?, NotificationAction?>
        get() = actionsTable

    /**
     * Returns the `Action` corresponding to the given `actionType`.
     *
     * @param actionType
     * one of NotificationService.ACTION_XXX constants
     *
     * @return the `Action` corresponding to the given `actionType`
     */
    fun getAction(actionType: String?): NotificationAction? {
        return actionsTable[actionType]
    }
}