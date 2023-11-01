/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification

/**
 * This service is previewed for use by bundles that implement some kind of user notification (e.g. playing sounds,
 * popping systray tooltips, or triggering commands.) In the case when such bundles would like to leave the user the
 * possibility to enable/disable or configure these notifications they could register an event type in this Registry
 * service.
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 * @Todo: write an example once we have completed the definition of the service.
 */
interface NotificationService {
    /**
     * Registers a notification for the given `eventType` by specifying the action to be performed when a
     * notification is fired for this event.
     *
     * Unlike the other `registerNotificationForEvent` method, this one allows the user to specify its own
     * `NotificationAction`, which would be used to handle notifications for the specified `actionType`.
     *
     * @param eventType the name of the event (as defined by the plug-in that's registering it) that we are setting an action for.
     * @param action the `NotificationAction`, which would be used to perform the notification action.
     */
    fun registerNotificationForEvent(eventType: String?, action: NotificationAction?)

    /**
     * Registers a default notification for the given `eventType` by specifying the action
     * to be performed when a notification is fired for this event.
     *
     * Unlike the other `registerDefaultNotificationForEvent` method, this one allows the user
     * to specify its own `NotificationAction`, which would be used to handle notifications.
     *
     * Default events are stored or executed at first run or when they are missing in the configuration. Also the
     * registered default events are used when restoreDefaults is called.
     *
     * @param eventType the name of the event (as defined by the plug-in that's registering it) that we are setting an action for.
     * @param handler the `NotificationActionHandler`, which would be used to perform the notification action.
     */
    fun registerDefaultNotificationForEvent(eventType: String, handler: NotificationAction)

    /**
     * Registers a default notification for the given `eventType` by specifying the type of the action to be
     * performed when a notification is fired for this event, the `actionDescriptor` for sound and command
     * actions and the `defaultMessage` for popup and log actions.
     *
     * Actions registered by this method would be handled by some default `NotificationHandler`s,
     * declared by the implementation.
     *
     *
     * The method allows registering more than one actionType for a specific event. Setting the same `actionType`
     * for the same `eventType` twice however would cause the first setting to be overridden.
     *
     * Default events are stored or executed at first run or when they are missing in the configuration. Also the
     * registered default events are used when restoreDefaults is called.
     *
     * @param eventType the name of the event (as defined by the plug-in that's registering it) that we are setting an action for.
     * @param actionType the type of the action that is to be executed when the specified event occurs
     * (could be one of the ACTION_XXX fields).
     * @param actionDescriptor a String containing a description of the action (a URI to the sound file for audio
     * notifications or a command line for exec action types) that should be executed when the action occurs.
     * @param defaultMessage the default message to use if no specific message has been provided when firing the notification.
     */
    fun registerDefaultNotificationForEvent(eventType: String, actionType: String?, actionDescriptor: String?, defaultMessage: String?)

    /**
     * Registers a notification for the given `eventType` by specifying the type of the action to be performed
     * when a notification is fired for this event, the `actionDescriptor` for sound and command actions and the
     * `defaultMessage` for popup and log actions. Actions registered by this method would be handled by some
     * default `NotificationHandler`s, declared by the implementation.
     *
     *
     * The method allows registering more than one actionType for a specific event. Setting the same `actionType`
     * for the same `eventType` twice however would cause the first setting to be overridden.
     *
     * @param eventType the name of the event (as defined by the plug-in that's registering it) that we are setting an action for.
     * @param actionType the type of the action that is to be executed when the specified event occurs
     * (could be one of the ACTION_XXX fields).
     * @param actionDescriptor a String containing a description of the action (a URI to the sound file for audio
     * notifications or a command line for exec action types) that should be executed when the action occurs.
     * @param defaultMessage the default message to use if no specific message has been provided when firing the notification.
     */
    fun registerNotificationForEvent(eventType: String, actionType: String?, actionDescriptor: String?, defaultMessage: String?)

    /**
     * Deletes all registered events and actions and registers and saves the default events as current.
     */
    fun restoreDefaults()

    /**
     * Removes the given `eventType` from the list of event notifications. This means that we delete here all
     * registered notifications for the given `eventType`.
     *
     *
     * This method does nothing if the given `eventType` is not contained in the list of registered event types.
     *
     * @param eventType the name of the event (as defined by the plugin that's registering it) to be removed.
     */
    fun removeEventNotification(eventType: String?)

    /**
     * Removes the event notification corresponding to the specified `actionType` and `eventType`.
     *
     *
     * This method does nothing if the given `eventType` or `actionType` are not contained
     * in the list of registered types.
     *
     * @param eventType the name of the event (as defined by the plugin that's registering it) for which we'll
     * remove the notification.
     * @param actionType the type of the action that is to be executed when the specified event occurs
     * (could be one of the ACTION_XXX fields).
     */
    fun removeEventNotificationAction(eventType: String?, actionType: String?)

    /**
     * Returns an iterator over a list of all events registered in this notification service. Each line in the returned
     * list consists of a String, representing the name of the event (as defined by the plugin that registered it).
     *
     * @return an iterator over a list of all events registered in this notifications service
     */
    val registeredEvents: Iterable<String?>

    /**
     * Returns the `NotificationAction` corresponding to the given event and action type.
     *
     *
     * This method returns **null** if the given `eventType` or `actionType` are not contained in the
     * list of registered types.
     *
     * @param eventType the type of the event that we'd like to retrieve.
     * @param actionType the type of the action that we'd like to retrieve a descriptor for.
     * @return the `NotificationAction` corresponding to the given event and action type
     */
    fun getEventNotificationAction(eventType: String?, actionType: String?): NotificationAction?

    /**
     * Registers a listener that would be notified of changes that have occurred in the registered event notifications.
     *
     * @param listener the listener that we'd like to register for changes in the event notifications stored by this service.
     */
    fun addNotificationChangeListener(listener: NotificationChangeListener)

    /**
     * Remove the specified listener so that it won't receive further notifications of changes that occur with actions
     * registered for events stored by this service.
     *
     * @param listener the listener to remove.
     */
    fun removeNotificationChangeListener(listener: NotificationChangeListener)

    /**
     * Adds an object that executes the actual action of a notification action.
     *
     * @param handler The handler that executes the action.
     */
    fun addActionHandler(handler: NotificationHandler?)

    /**
     * Removes an object that executes the actual action of notification action.
     *
     * @param actionType The handler type to remove.
     */
    fun removeActionHandler(actionType: String?)

    /**
     * Gets at list of handler for the specified action type.
     *
     * @param actionType the type for which the list of handlers should be retrieved or `null`
     * if all handlers shall be returned.
     * @return Iterable of NotificationHandler objects
     */
    fun getActionHandlers(actionType: String?): Iterable<NotificationHandler>

    /**
     * Fires all notifications registered for the specified `eventType` using `message` as
     * a notification message wherever appropriate (e.g. systray notifications, logs, etc.)
     *
     *
     * This method does nothing if the given `eventType` is not contained in the list of registered event types.
     *
     *
     * @param eventType the type of the event that we'd like to fire a notification for.
     * @param msgType the notification sub-category message type
     * @param messageTitle the message title to use if and where appropriate (e.g. with systray)
     * @param message the message to use if and where appropriate (e.g. with systray or log notification.)
     * @param icon the icon to show in the notification if and where appropriate
     * @return An object referencing the notification. It may be used to stop a still running notification.
     * Can be null if the eventType is unknown or the notification is not active.
     */
    fun fireNotification(eventType: String, msgType: Int, messageTitle: String, message: String, icon: ByteArray?): NotificationData?

    /**
     * Fires all notifications registered for the specified `eventType` using `message` as a
     * notification message wherever appropriate (e.g. systray notifications, logs, etc.)
     *
     *
     * This method does nothing if the given `eventType` is not contained in the list of registered event types.
     *
     * @param eventType the type of the event that we'd like to fire a notification for.
     * @param msgType the notification sub-category message type
     * @param messageTitle the message title to use if and where appropriate (e.g. with systray)
     * @param message the message to use if and where appropriate (e.g. with systray or log notification.)
     * @param icon the icon to show in the notification if and where appropriate
     * @param extras additional/extra [NotificationHandler]-specific data to be provided to the firing
     * of the specified notification(s). The well-known keys are defined by the `NotificationData`
     * `XXX_EXTRA` constants.
     * @return An object referencing the notification. It may be used to stop a still running notification.
     * Can be null if the eventType is unknown or the notification is not active.
     */
    fun fireNotification(eventType: String, msgType: Int, messageTitle: String, message: String, icon: ByteArray?,
            extras: Map<String, Any>?): NotificationData?

    /**
     * Fires all notifications registered for the specified `eventType` using the default message specified upon
     * registration as a notification message wherever appropriate. (e.g. systray notifications, logs, etc.)
     *
     *
     * This method does nothing if the given `eventType` is not contained in the list of registered event types.
     *
     * @param eventType the type of the event that we'd like to fire a notification for.
     * @return An object referencing the notification. It may be used to stop a still running notification.
     * Can be null if the eventType is unknown or the notification is not active.
     */
    fun fireNotification(eventType: String): NotificationData?

    /**
     * Stops a notification if notification is continuous, like playing sounds in loop. Do nothing
     * if there are no such events currently processing.
     *
     * @param data the data that has been returned when firing the event..
     */
    fun stopNotification(data: NotificationData?)

    /**
     * Activates or deactivates all notification actions related to the specified `eventType`. This method does
     * nothing if the given `eventType` is not contained in the list of registered event types.
     *
     * @param eventType the name of the event, which actions should be activated /deactivated.
     * @param isActive indicates whether to activate or deactivate the actions related to the specified `eventType`.
     */
    fun setActive(eventType: String?, isActive: Boolean)

    /**
     * Indicates whether or not actions for the specified `eventType` are activated. This method returns
     * `false` if the given `eventType` is not contained in the list of registered event types.
     *
     * @param eventType the name of the event (as defined by the plugin that's registered it) that we are checking.
     * @return `true</code> if actions for the specified <code>eventType</code> are activated, <code>false` -
     * otherwise. If the given `eventType` is not contained in the list of registered event types -
     * returns `false`.
     */
    fun isActive(eventType: String?): Boolean

    /**
     * Tells if the given sound notification is currently played.
     *
     * @param data Additional data for the event.
     */
    fun isPlayingNotification(data: NotificationData?): Boolean
}