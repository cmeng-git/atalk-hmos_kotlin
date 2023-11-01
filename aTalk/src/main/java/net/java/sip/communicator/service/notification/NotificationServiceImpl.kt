/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification

import net.java.sip.communicator.service.notification.event.NotificationActionTypeEvent
import net.java.sip.communicator.service.notification.event.NotificationEventTypeEvent
import net.java.sip.communicator.service.systray.SystrayService
import net.java.sip.communicator.util.ConfigurationUtils.getQuiteHoursEnd
import net.java.sip.communicator.util.ConfigurationUtils.getQuiteHoursStart
import net.java.sip.communicator.util.ConfigurationUtils.isQuiteHoursEnable
import org.atalk.hmos.gui.settings.TimePreference
import org.atalk.hmos.plugin.timberlog.TimberLog
import timber.log.Timber
import java.util.*

/**
 * The implementation of the `NotificationService`.
 *
 * @author Yana Stamcheva
 * @author Ingo Bauersachs
 * @author Eng Chong Meng
 */
internal class NotificationServiceImpl : NotificationService {
    /**
     * A list of all registered `NotificationChangeListener`s.
     */
    private val changeListeners = Vector<NotificationChangeListener>()
    private val configService = NotificationServiceActivator.configurationService

    /**
     * A set of all registered event notifications.
     */
    private val defaultNotifications = HashMap<String, Notification>()

    /**
     * Contains the notification handler per action type.
     */
    private val handlers = HashMap<String?, NotificationHandler>()

    /**
     * Queue to cache fired notifications before all handlers are registered.
     */
    private var notificationCache: Queue<NotificationData>? = LinkedList()

    /**
     * A set of all registered event notifications.
     */
    private val notifications = HashMap<String?, Notification>()

    /**
     * Creates an instance of `NotificationServiceImpl` by loading all previously saved notifications.
     */
    init {
        // Load all previously saved notifications.
        loadNotifications()
    }

    /**
     * Adds an object that executes the actual action of a notification action. If the same action
     * type is added twice, the last added wins.
     *
     * @param handler The handler that executes the action.
     */
    override fun addActionHandler(handler: NotificationHandler?) {
        requireNotNull(handler) { "handler cannot be null" }
        synchronized(handlers) {
            handlers[handler.actionType] = handler
            if (handlers.size == NUM_ACTIONS && notificationCache != null) {
                for (event in notificationCache!!) {
                    fireNotification(event)
                }
                notificationCache!!.clear()
                notificationCache = null
            }
        }
    }

    /**
     * Adds the given `listener` to the list of change listeners.
     *
     * @param listener the listener that we'd like to register to listen for changes in the event
     * notifications stored by this service.
     */
    override fun addNotificationChangeListener(listener: NotificationChangeListener) {
        synchronized(changeListeners) { changeListeners.add(listener) }
    }

    /**
     * Checking an action when it is edited (property.default=false). Checking for older versions
     * of the property. If it is older one we migrate it to new configuration using the default values.
     *
     * @param eventType the event type.
     * @param defaultAction the default action which values we will use.
     */
    private fun checkDefaultAgainstLoadedNotification(eventType: String, defaultAction: NotificationAction) {
        // checking for new sound action properties
        if (defaultAction is SoundNotificationAction) {
            val soundAction = getEventNotificationAction(eventType, NotificationAction.ACTION_SOUND) as SoundNotificationAction
            val isSoundNotificationEnabledPropExist = getNotificationActionProperty(eventType, defaultAction, "isSoundNotificationEnabled") != null
            if (!isSoundNotificationEnabledPropExist) {
                soundAction.isSoundNotificationEnabled = defaultAction.isSoundNotificationEnabled
            }
            val isSoundPlaybackEnabledPropExist = getNotificationActionProperty(eventType,
                    defaultAction, "isSoundPlaybackEnabled") != null
            if (!isSoundPlaybackEnabledPropExist) {
                soundAction.isSoundPlaybackEnabled = defaultAction.isSoundPlaybackEnabled
            }
            val isSoundPCSpeakerEnabledPropExist = getNotificationActionProperty(eventType,
                    defaultAction, "isSoundPCSpeakerEnabled") != null
            if (!isSoundPCSpeakerEnabledPropExist) {
                soundAction.isSoundPCSpeakerEnabled = defaultAction.isSoundPCSpeakerEnabled
            }

            // cmeng: does not apply to aTalk - can be removed
            var fixDialingLoop = false
            // hack to fix wrong value:just check whether loop for outgoing call (dialing) has
            // gone into config as 0, should be -1
            if (eventType == "Dialing" && soundAction.loopInterval == 0) {
                soundAction.loopInterval = defaultAction.loopInterval
                fixDialingLoop = true
            }
            if (!(isSoundNotificationEnabledPropExist
                            && isSoundPCSpeakerEnabledPropExist
                            && isSoundPlaybackEnabledPropExist) || fixDialingLoop) {
                // this check is done only when the notification is edited and is not default
                saveNotification(eventType, soundAction, soundAction.isEnabled, false)
            }
        } else if (defaultAction is PopupMessageNotificationAction) {
            saveNotification(eventType, defaultAction, defaultAction.isEnabled, false)
        }
    }

    /**
     * Executes a notification data object on the handlers on conditions:
     * a. EvenType is active
     * b. The specific action of the eventype is enabled
     * c. There is a valid handler for the action
     *
     * @param data The notification data to act upon.
     */
    private fun fireNotification(data: NotificationData) {
        val notification = notifications[data.eventType]
        if (notification == null || !notification.isActive) return

        // Loop and take action for each action that is enabled
        for (action in notification.actions.values) {
            val actionType = action!!.actionType
            if (!action.isEnabled) continue
            val handler = handlers[actionType] ?: continue
            try {
                when (actionType) {
                    NotificationAction.ACTION_LOG_MESSAGE -> (handler as LogMessageNotificationHandler).logMessage(
                            action as LogMessageNotificationAction, data.message)
                    NotificationAction.ACTION_SOUND -> {
                        val soundNotificationAction = action as SoundNotificationAction
                        if (!isQuietHours && (soundNotificationAction.isSoundNotificationEnabled
                                        || soundNotificationAction.isSoundPlaybackEnabled
                                        || soundNotificationAction.isSoundPCSpeakerEnabled)) {
                            (handler as SoundNotificationHandler).start(action, data)
                        }
                    }
                    NotificationAction.ACTION_COMMAND -> {
                        val cmdargs = data.getExtra(
                                NotificationData.COMMAND_NOTIFICATION_HANDLER_CMDARGS_EXTRA) as Map<String, String>
                        (handler as CommandNotificationHandler).execute(action as CommandNotificationAction, cmdargs)
                    }
                    NotificationAction.ACTION_VIBRATE -> (handler as VibrateNotificationHandler).vibrate(action as VibrateNotificationAction)
                    NotificationAction.ACTION_POPUP_MESSAGE -> (handler as PopupMessageNotificationHandler).popupMessage(action as PopupMessageNotificationAction, data)
                    else -> (handler as PopupMessageNotificationHandler).popupMessage(action as PopupMessageNotificationAction, data)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error dispatching notification of type %s from %s", actionType, handler)
            }
        }
    }

    /**
     * If there is a registered event notification of the given `eventType` and the event
     * notification is currently activated, we go through the list of registered actions and execute them.
     *
     * @param eventType the type of the event that we'd like to fire a notification for.
     *
     * @return An object referencing the notification. It may be used to stop a still running
     * notification. Can be null if the eventType is unknown, or the notification is not active.
     */
    override fun fireNotification(eventType: String): NotificationData? {
        return fireNotification(eventType, SystrayService.INFORMATION_MESSAGE_TYPE, "", "", null)
    }

    /**
     * If there is a registered event notification of the given `eventType` and the event
     * notification is currently activated, the list of registered actions is executed.
     *
     * @param eventType the type of the event that we'd like to fire a notification for.
     * @param msgType the notification sub-category message type
     * @param messageTitle the title of the given message
     * @param message the message to use if and where appropriate (e.g. with systray or log notification.)
     * @param icon the icon to show in the notification if and where appropriate
     *
     * @return An object referencing the notification. It may be used to stop a still running
     * notification. Can be null if the eventType is unknown or the notification is not active.
     */
    override fun fireNotification(eventType: String, msgType: Int, messageTitle: String, message: String, icon: ByteArray?): NotificationData? {
        return fireNotification(eventType, msgType, messageTitle, message, icon, null)
    }

    /**
     * If there is a registered event notification of the given `eventType` and the event
     * notification is currently activated, the list of registered actions is executed.
     *
     * @param eventType the type of the event that we'd like to fire a notification for.
     * @param msgType the notification sub-category message type
     * @param messageTitle the title of the given message
     * @param message the message to use if and where appropriate (e.g. with systray or log notification.)
     * @param icon the icon to show in the notification if and where appropriate
     * @param extras additional/extra [NotificationHandler]-specific data to be provided to the firing
     * of the specified notification(s). The well-known keys are defined by the
     * `NotificationData` `XXX_EXTRA` constants.
     *
     * @return An object referencing the notification. It may be used to stop a still running
     * notification. Can be null if the eventType is unknown or the notification is not active.
     */
    override fun fireNotification(
            eventType: String, msgType: Int, messageTitle: String, message: String,
            icon: ByteArray?, extras: Map<String, Any>?): NotificationData? {
        val notification = notifications[eventType]
        if (notification == null || !notification.isActive) return null
        val data = NotificationData(eventType, msgType, messageTitle, message, icon, extras)
        // cache the notification when the handlers are not yet ready
        // Timber.d("Fire notification for: %s %s", eventType, notificationCache);
        if (notificationCache != null) notificationCache!!.add(data) else fireNotification(data)
        return data
    }

    /**
     * Notifies all registered `NotificationChangeListener`s that a
     * `NotificationActionTypeEvent` has occurred.
     *
     * @param eventType the type of the event, which is one of ACTION_XXX constants declared in the
     * `NotificationActionTypeEvent` class.
     * @param sourceEventType the `eventType`, which is the parent of the action
     * @param action the notification action
     */
    private fun fireNotificationActionTypeEvent(eventType: String, sourceEventType: String?, action: NotificationAction?) {
        val event = NotificationActionTypeEvent(this, eventType, sourceEventType, action)
        for (listener in changeListeners) {
            when (eventType) {
                NotificationActionTypeEvent.ACTION_ADDED -> listener.actionAdded(event)
                NotificationActionTypeEvent.ACTION_REMOVED -> listener.actionRemoved(event)
                NotificationActionTypeEvent.ACTION_CHANGED -> listener.actionChanged(event)
            }
        }
    }

    /**
     * Notifies all registered `NotificationChangeListener`s that a
     * `NotificationEventTypeEvent` has occurred.
     *
     * @param eventType the type of the event, which is one of EVENT_TYPE_XXX constants declared in the
     * `NotificationEventTypeEvent` class.
     * @param sourceEventType the `eventType`, for which this event is about
     */
    private fun fireNotificationEventTypeEvent(eventType: String, sourceEventType: String?) {
        Timber.d("Dispatching NotificationEventType Change. Listeners = %s evt = %s",
                changeListeners.size, eventType)
        val event = NotificationEventTypeEvent(this, eventType, sourceEventType)
        for (listener in changeListeners) {
            if (eventType == NotificationEventTypeEvent.EVENT_TYPE_ADDED) {
                listener.eventTypeAdded(event)
            } else if (eventType == NotificationEventTypeEvent.EVENT_TYPE_REMOVED) {
                listener.eventTypeRemoved(event)
            }
        }
    }

    /**
     * Gets a list of handler for the specified action type.
     *
     * @param actionType the type for which the list of handlers should be retrieved or `null` if all
     * handlers shall be returned.
     */
    override fun getActionHandlers(actionType: String?): Iterable<NotificationHandler> {
        return if (actionType != null) {
            val handler = handlers[actionType]
            val ret = handler?.let { setOf(it) }
                    ?: emptySet()
            ret
        } else handlers.values
    }

    /**
     * Returns the notification action corresponding to the given `eventType` and `actionType`.
     *
     * @param eventType the type of the event that we'd like to retrieve.
     * @param actionType the type of the action that we'd like to retrieve a descriptor for.
     *
     * @return the notification action of the action to be executed when an event of the specified type has occurred.
     */
    override fun getEventNotificationAction(eventType: String?, actionType: String?): NotificationAction? {
        val notification = notifications[eventType]
        return notification?.getAction(actionType)
    }

    /**
     * Getting a notification property directly from configuration service. Used to check do we
     * have an updated version of already saved/edited notification configurations. Detects old configurations.
     *
     * @param eventType the event type
     * @param action the action which property to check.
     * @param property the property name without the action prefix.
     *
     * @return the property value or null if missing.
     * @throws IllegalArgumentException when the event ot action is not found.
     */
    @Throws(IllegalArgumentException::class)
    private fun getNotificationActionProperty(eventType: String, action: NotificationAction, property: String): String? {
        var eventTypeNodeName: String? = null
        var actionTypeNodeName: String? = null
        val eventTypes = configService!!.getPropertyNamesByPrefix(NOTIFICATIONS_PREFIX, true)
        for (eventTypeRootPropName in eventTypes) {
            val eType = configService.getString(eventTypeRootPropName)
            if (eType == eventType) eventTypeNodeName = eventTypeRootPropName
        }

        // If we didn't find the given event type in the configuration there is not need to
        // further check
        requireNotNull(eventTypeNodeName) { "Missing event type node" }

        // Go through contained actions.
        val actionPrefix = "$eventTypeNodeName.actions"
        val actionTypes = configService.getPropertyNamesByPrefix(actionPrefix, true)
        for (actionTypeRootPropName in actionTypes) {
            val aType = configService.getString(actionTypeRootPropName)
            if (aType == action.actionType) actionTypeNodeName = actionTypeRootPropName
        }

        // If we didn't find the given actionType in the configuration there is no need to further check
        requireNotNull(actionTypeNodeName) { "Missing action type node" }
        return configService.getProperty("$actionTypeNodeName.$property") as String?
    }

    /**
     * Returns an iterator over a list of all events registered in this notification service. Each
     * line in the returned list consists of a String, representing the name of the event (as
     * defined by the plugin that registered it).
     *
     * @return an iterator over a list of all events registered in this notifications service
     */
    override val registeredEvents: Iterable<String?>
        get() = Collections.unmodifiableSet(notifications.keys)

    /**
     * Finds the `EventNotification` corresponding to the given `eventType` and
     * returns its isActive status.
     *
     * @param eventType the name of the event (as defined by the plugin that's registered it) that we are
     * checking.
     *
     * @return `true` if actions for the specified `eventType` are activated,
     * `false` - otherwise. If the given `eventType` is not contained in the
     * list of registered event types - returns `false`.
     */
    override fun isActive(eventType: String?): Boolean {
        val eventNotification = notifications[eventType]
        return eventNotification != null && eventNotification.isActive
    }

    private fun isDefault(eventType: String, actionType: String?): Boolean {
        val eventTypes = configService!!.getPropertyNamesByPrefix(NOTIFICATIONS_PREFIX, true)
        for (eventTypeRootPropName in eventTypes) {
            val eType = configService.getString(eventTypeRootPropName)
            if (eType != eventType) continue
            val actions = configService.getPropertyNamesByPrefix(eventTypeRootPropName
                    + ".actions", true)
            for (actionPropName in actions) {
                val aType = configService.getString(actionPropName)
                if (aType != actionType) continue

                // if setting is missing we accept it is true this way we override old saved settings
                val isDefaultObj = configService.getProperty("$actionPropName.default")
                return isDefaultObj == null || java.lang.Boolean.parseBoolean(isDefaultObj as String?)
            }
        }
        return true
    }

    private fun isEnabled(configProperty: String): Boolean {
        // if setting is missing we accept it is true this way we not affect old saved settings
        val isEnabledObj = configService!!.getProperty(configProperty)
        return isEnabledObj == null || java.lang.Boolean.parseBoolean(isEnabledObj as String?)
    }

    /**
     * Loads all previously saved event notifications.
     */
    private fun loadNotifications() {
        val eventTypes = configService!!.getPropertyNamesByPrefix(NOTIFICATIONS_PREFIX, true)
        for (eventTypeRootPropName in eventTypes) {
            val isEventActive = isEnabled("$eventTypeRootPropName.active")
            val eventType = configService.getString(eventTypeRootPropName)

            // cmeng: Patch to purge old eventType "missed_call" which has been replaced with MissedCall;
            // Will be removed in future aTalk releases > e.g. v2.1.0
            if ("missed_call" == eventType) {
                configService.removeProperty(eventTypeRootPropName)
                continue
            }
            val actions = configService.getPropertyNamesByPrefix("$eventTypeRootPropName.actions", true)
            for (actionPropName in actions) {
                val actionType = configService.getString(actionPropName)
                var action: NotificationAction? = null
                when (actionType) {
                    NotificationAction.ACTION_SOUND -> {
                        val soundFileDescriptor = configService.getString("$actionPropName.soundFileDescriptor")
                        // loopInterval must not be null
                        val loopInterval = configService.getString("$actionPropName.loopInterval", "-1")
                        val isSoundNotificationEnabled = configService.getBoolean(
                                "$actionPropName.isSoundNotificationEnabled", soundFileDescriptor != null)
                        val isSoundPlaybackEnabled = configService.getBoolean(
                                "$actionPropName.isSoundPlaybackEnabled", false)
                        val isSoundPCSpeakerEnabled = configService.getBoolean(
                                "$actionPropName.isSoundPCSpeakerEnabled", false)
                        action = SoundNotificationAction(soundFileDescriptor, loopInterval!!.toInt(),
                                isSoundNotificationEnabled, isSoundPlaybackEnabled, isSoundPCSpeakerEnabled)
                    }
                    NotificationAction.ACTION_POPUP_MESSAGE -> {
                        val defaultMessage = configService.getString("$actionPropName.defaultMessage")
                        val timeout = configService.getLong("$actionPropName.timeout", -1)
                        val groupName = configService.getString("$actionPropName.groupName")
                        action = PopupMessageNotificationAction(defaultMessage, timeout, groupName)
                    }
                    NotificationAction.ACTION_LOG_MESSAGE -> {
                        val logType = configService.getString("$actionPropName.logType")
                        action = LogMessageNotificationAction(logType)
                    }
                    NotificationAction.ACTION_COMMAND -> {
                        val commandDescriptor = configService.getString("$actionPropName.commandDescriptor")
                        action = CommandNotificationAction(commandDescriptor)
                    }
                    NotificationAction.ACTION_VIBRATE -> {
                        val descriptor = configService.getString("$actionPropName.descriptor")
                        val patternLen = configService.getInt("$actionPropName.patternLength", -1)
                        if (patternLen == -1) {
                            Timber.e("Invalid pattern length: %s", patternLen)
                            continue
                        }
                        val pattern = LongArray(patternLen)
                        var pIdx = 0
                        while (pIdx < patternLen) {
                            pattern[pIdx] = configService.getLong("$actionPropName.patternItem$pIdx", -1)
                            if (pattern[pIdx] == -1L) {
                                Timber.e("Invalid pattern interval: %s", pattern as Any)
                            }
                            pIdx++
                        }
                        val repeat = configService.getInt("$actionPropName.repeat", -1)
                        action = VibrateNotificationAction(descriptor, pattern, repeat)
                    }
                }
                if (action == null) continue
                action.isEnabled = isEnabled("$actionPropName.enabled")

                // Load the data in the notifications table.
                var notification = notifications[eventType]
                if (notification == null) {
                    notification = Notification(eventType)
                    notifications[eventType] = notification
                }
                notification.isActive = isEventActive
                notification.addAction(action)
            }
        }
    }

    /**
     * Creates a new default `EventNotification` or obtains the corresponding existing one
     * and registers a new action in it.
     *
     * @param eventType the name of the event (as defined by the plugin that's registering it) that we are
     * setting an action for.
     * @param handler the `NotificationAction` to register
     */
    override fun registerDefaultNotificationForEvent(eventType: String, handler: NotificationAction) {
        if (isDefault(eventType, handler.actionType)) {
            var h = getEventNotificationAction(eventType, handler.actionType)
            var isNew = false
            if (h == null) {
                isNew = true
                h = handler
            }
            saveNotification(eventType, handler, h.isEnabled, true)
            val notification: Notification?
            if (notifications.containsKey(eventType)) notification = notifications[eventType] else {
                notification = Notification(eventType)
                notifications[eventType] = notification
            }
            notification!!.addAction(handler)

            // We fire the appropriate event depending on whether this is an already existing actionType or a new one.
            fireNotificationActionTypeEvent(if (isNew) NotificationActionTypeEvent.ACTION_ADDED else NotificationActionTypeEvent.ACTION_CHANGED, eventType, handler)
        } else checkDefaultAgainstLoadedNotification(eventType, handler)

        // now store this default events if we want to restore them
        val notification: Notification?
        if (defaultNotifications.containsKey(eventType)) notification = defaultNotifications[eventType] else {
            notification = Notification(eventType)
            defaultNotifications[eventType] = notification
        }
        notification!!.addAction(handler)
    }

    /**
     * Creates a new default `EventNotification` or obtains the corresponding existing one
     * and registers a new action in it.
     *
     * @param eventType the name of the event (as defined by the plugin that's registering it) that we are
     * setting an action for.
     * @param actionType the type of the action that is to be executed when the specified event occurs (could
     * be one of the ACTION_XXX fields).
     * @param actionDescriptor a String containing a description of the action (a URI to the sound file for audio
     * notifications or a command line for exec action types) that should be executed when the action occurs.
     * @param defaultMessage the default message to use if no specific message has been provided when firing the
     * notification.
     */
    override fun registerDefaultNotificationForEvent(eventType: String, actionType: String?,
            actionDescriptor: String?, defaultMessage: String?) {
        Timber.log(TimberLog.FINER, "Registering default event Type: %s; Action: %s; Descriptor: %s; Message: %s",
                eventType, actionType, actionDescriptor, defaultMessage)
        if (isDefault(eventType, actionType)) {
            var action = getEventNotificationAction(eventType, actionType)
            var isNew = false
            if (action == null) {
                isNew = true
                when (actionType) {
                    NotificationAction.ACTION_SOUND -> action = SoundNotificationAction(actionDescriptor, -1)
                    NotificationAction.ACTION_LOG_MESSAGE -> action = LogMessageNotificationAction(LogMessageNotificationAction.INFO_LOG_TYPE)
                    NotificationAction.ACTION_POPUP_MESSAGE -> action = PopupMessageNotificationAction(defaultMessage)
                    NotificationAction.ACTION_COMMAND -> action = CommandNotificationAction(actionDescriptor)
                }
            }
            saveNotification(eventType, action, action!!.isEnabled, true)
            val notification: Notification?
            if (notifications.containsKey(eventType)) notification = notifications[eventType] else {
                notification = Notification(eventType)
                notifications[eventType] = notification
            }
            notification!!.addAction(action)

            // We fire the appropriate event depending on whether this is an already existing
            // actionType or a new one.
            fireNotificationActionTypeEvent(if (isNew) NotificationActionTypeEvent.ACTION_ADDED else NotificationActionTypeEvent.ACTION_CHANGED, eventType, action)
        }

        // now store this default events if we want to restore them
        val notification: Notification?
        if (defaultNotifications.containsKey(eventType)) notification = defaultNotifications[eventType] else {
            notification = Notification(eventType)
            defaultNotifications[eventType] = notification
        }
        var action: NotificationAction? = null
        when (actionType) {
            NotificationAction.ACTION_SOUND -> action = SoundNotificationAction(actionDescriptor, -1)
            NotificationAction.ACTION_LOG_MESSAGE -> action = LogMessageNotificationAction(
                    LogMessageNotificationAction.INFO_LOG_TYPE)
            NotificationAction.ACTION_POPUP_MESSAGE -> action = PopupMessageNotificationAction(defaultMessage)
            NotificationAction.ACTION_COMMAND -> action = CommandNotificationAction(actionDescriptor)
        }
        notification!!.addAction(action)
    }

    /**
     * Creates a new `EventNotification` or obtains the corresponding existing one and
     * registers a new action in it.
     *
     * @param eventType the name of the event (as defined by the plugin that's registering it) that we are
     * setting an action for.
     * @param action the `NotificationAction` responsible for handling the given `actionType`
     */
    override fun registerNotificationForEvent(eventType: String?, action: NotificationAction?) {
        val notification: Notification?
        if (notifications.containsKey(eventType)) notification = notifications[eventType] else {
            notification = Notification(eventType)
            notifications[eventType] = notification
            fireNotificationEventTypeEvent(NotificationEventTypeEvent.EVENT_TYPE_ADDED, eventType)
        }
        val existingAction = notification!!.addAction(action)

        // We fire the appropriate event depending on whether this is an already existing actionType or a new one.
        if (existingAction != null) {
            fireNotificationActionTypeEvent(NotificationActionTypeEvent.ACTION_CHANGED, eventType, action)
        } else {
            fireNotificationActionTypeEvent(NotificationActionTypeEvent.ACTION_ADDED, eventType, action)
        }
        // Save the notification through the ConfigurationService.
        saveNotification(eventType, action, isActive = true, isDefault = false)
    }

    /**
     * Creates a new `EventNotification` or obtains the corresponding existing one and
     * registers a new action in it.
     *
     * @param eventType the name of the event (as defined by the plugin that's registering it) that we are
     * setting an action for.
     * @param actionType the type of the action that is to be executed when the specified event occurs (could
     * be one of the ACTION_XXX fields).
     * @param actionDescriptor a String containing a description of the action (a URI to the sound file for audio
     * notifications or a command line for exec action types) that should be executed when the action occurs.
     * @param defaultMessage the default message to use if no specific message has been provided when firing the
     * notification.
     */
    override fun registerNotificationForEvent(eventType: String, actionType: String?,
            actionDescriptor: String?, defaultMessage: String?) {
        Timber.d("Registering event Type: %s; Action: %s; Descriptor: %s; Message: %s",
                eventType, actionType, actionDescriptor, defaultMessage)
        when (actionType) {
            NotificationAction.ACTION_SOUND -> {
                val notification = defaultNotifications[eventType]
                val action = notification!!.getAction(NotificationAction.ACTION_SOUND) as SoundNotificationAction
                registerNotificationForEvent(eventType, SoundNotificationAction(
                        actionDescriptor, action.loopInterval))
            }
            NotificationAction.ACTION_LOG_MESSAGE -> registerNotificationForEvent(eventType, LogMessageNotificationAction(
                    LogMessageNotificationAction.INFO_LOG_TYPE))
            NotificationAction.ACTION_POPUP_MESSAGE -> registerNotificationForEvent(eventType, PopupMessageNotificationAction(defaultMessage))
            NotificationAction.ACTION_COMMAND -> registerNotificationForEvent(eventType, CommandNotificationAction(actionDescriptor))
        }
    }

    /**
     * Removes an object that executes the actual action of notification action.
     *
     * @param actionType The handler type to remove.
     */
    override fun removeActionHandler(actionType: String?) {
        requireNotNull(actionType) { "actionType cannot be null" }
        synchronized(handlers) { handlers.remove(actionType) }
    }

    /**
     * Removes the `EventNotification` corresponding to the given `eventType` from
     * the table of registered event notifications.
     *
     * @param eventType the name of the event (as defined by the plugin that's registering it) to be removed.
     */
    override fun removeEventNotification(eventType: String?) {
        notifications.remove(eventType)
        fireNotificationEventTypeEvent(NotificationEventTypeEvent.EVENT_TYPE_REMOVED, eventType)
    }

    /**
     * Removes the given actionType from the list of actions registered for the given `eventType`.
     *
     * @param eventType the name of the event (as defined by the plugin that's registering it) for which we'll
     * remove the notification.
     * @param actionType the type of the action that is to be executed when the specified event occurs (could
     * be one of the ACTION_XXX fields).
     */
    override fun removeEventNotificationAction(eventType: String?, actionType: String?) {
        val notification = notifications[eventType] ?: return
        val action = notification.getAction(actionType) ?: return
        notification.removeAction(actionType)
        action.isEnabled = false
        saveNotification(eventType, action, isActive = false, isDefault = false)
        fireNotificationActionTypeEvent(NotificationActionTypeEvent.ACTION_REMOVED, eventType, action)
    }

    /**
     * Removes the given `listener` from the list of change listeners.
     *
     * @param listener the listener that we'd like to remove
     */
    override fun removeNotificationChangeListener(listener: NotificationChangeListener) {
        synchronized(changeListeners) { changeListeners.remove(listener) }
    }

    /**
     * Deletes all registered events and actions and registers and saves the default events as current.
     */
    override fun restoreDefaults() {
        for (eventType in Vector(notifications.keys)) {
            val notification = notifications[eventType]
            if (notification != null) {
                for (actionType in Vector(notification.actions.keys)) removeEventNotificationAction(eventType, actionType)
            }
            removeEventNotification(eventType)
        }
        for ((eventType, notification) in defaultNotifications) {
            for (action in notification.actions.values) registerNotificationForEvent(eventType, action)
        }
    }

    /**
     * Saves the event notification action given by these parameters through the `ConfigurationService`.
     * OR globally set the active state if action is null.
     *
     * @param eventType the name of the event
     * @param action the notification action of the event to be changed
     * @param isActive is the global notification event active state, valid only if action is null
     * @param isDefault is it a default one
     */
    private fun saveNotification(eventType: String?, action: NotificationAction?, isActive: Boolean, isDefault: Boolean) {
        var eventTypeNodeName: String? = null
        var actionTypeNodeName: String? = null
        val eventTypes = configService!!.getPropertyNamesByPrefix(NOTIFICATIONS_PREFIX, true)
        for (eventTypeRootPropName in eventTypes) {
            val eType = configService.getString(eventTypeRootPropName)
            if (eType == eventType) {
                eventTypeNodeName = eventTypeRootPropName
                break
            }
        }

        // If we didn't find the given event type in the configuration we create new here.
        if (eventTypeNodeName == null) {
            eventTypeNodeName = NOTIFICATIONS_PREFIX + ".eventType" + System.currentTimeMillis()
            configService.setProperty(eventTypeNodeName, eventType)
        }

        // We set active/inactive for the whole event notification if action is null
        if (action == null) {
            configService.setProperty("$eventTypeNodeName.active", java.lang.Boolean.toString(isActive))
            return
        }

        // Go through contained actions to find the specific action
        val actionPrefix = "$eventTypeNodeName.actions"
        val actionTypes = configService.getPropertyNamesByPrefix(actionPrefix, true)
        for (actionTypeRootPropName in actionTypes) {
            val aType = configService.getString(actionTypeRootPropName)
            if (aType == action.actionType) {
                actionTypeNodeName = actionTypeRootPropName
                break
            }
        }

        // List of specific action properties to be updated in database
        val configProperties = HashMap<String, Any?>()

        // If we didn't find the given actionType in the configuration we create new here.
        if (actionTypeNodeName == null) {
            actionTypeNodeName = actionPrefix + ".actionType" + System.currentTimeMillis()
            configProperties[actionTypeNodeName] = action.actionType
        }
        when (action) {
            is SoundNotificationAction -> {
                configProperties["$actionTypeNodeName.soundFileDescriptor"] = action.descriptor
                configProperties["$actionTypeNodeName.loopInterval"] = action.loopInterval
                configProperties["$actionTypeNodeName.isSoundNotificationEnabled"] = action.isSoundNotificationEnabled
                configProperties["$actionTypeNodeName.isSoundPlaybackEnabled"] = action.isSoundPlaybackEnabled
                configProperties["$actionTypeNodeName.isSoundPCSpeakerEnabled"] = action.isSoundPCSpeakerEnabled
            }
            is PopupMessageNotificationAction -> {
                configProperties["$actionTypeNodeName.defaultMessage"] = action.defaultMessage
                configProperties["$actionTypeNodeName.timeout"] = action.timeout
                configProperties["$actionTypeNodeName.groupName"] = action.groupName
            }
            is LogMessageNotificationAction -> {
                configProperties["$actionTypeNodeName.logType"] = action.logType
            }
            is CommandNotificationAction -> {
                configProperties["$actionTypeNodeName.commandDescriptor"] = action.descriptor
            }
            is VibrateNotificationAction -> {
                configProperties["$actionTypeNodeName.descriptor"] = action.descriptor
                val pattern = action.pattern
                configProperties["$actionTypeNodeName.patternLength"] = pattern.size
                for (pIdx in pattern.indices) {
                    configProperties["$actionTypeNodeName.patternItem$pIdx"] = pattern[pIdx]
                }
                configProperties["$actionTypeNodeName.repeat"] = action.repeat
            }
        }

        // cmeng: should update based on action.isEnabled() instead of active meant for the global event state
        configProperties["$actionTypeNodeName.enabled"] = java.lang.Boolean.toString(action.isEnabled)
        configProperties["$actionTypeNodeName.default"] = java.lang.Boolean.toString(isDefault)
        configService.setProperties(configProperties)
    }

    /**
     * Finds the `EventNotification` corresponding to the given `eventType` and
     * marks it as activated/deactivated.
     *
     * @param eventType the name of the event, which actions should be activated /deactivated.
     * @param isActive indicates whether to activate or deactivate the actions related to the specified `eventType`.
     */
    override fun setActive(eventType: String?, isActive: Boolean) {
        val eventNotification = notifications[eventType] ?: return
        eventNotification.isActive = isActive
        saveNotification(eventType, null, isActive, false)
    }

    /**
     * Stops a notification if notification is continuous, like playing sounds in loops.
     * Do nothing if there are no such events currently processing.
     *
     * @param data the data that has been returned when firing the event..
     */
    override fun stopNotification(data: NotificationData?) {
        val soundHandlers = getActionHandlers(NotificationAction.ACTION_SOUND)

        // There could be no sound action handler for this event type e.g. call ringtone
        if (soundHandlers != null) {
            for (handler in soundHandlers) {
                if (handler is SoundNotificationHandler) handler.stop(data)
            }
        }
        val vibrateHandlers = getActionHandlers(NotificationAction.ACTION_VIBRATE)
        if (vibrateHandlers != null) {
            for (handler in vibrateHandlers) {
                (handler as VibrateNotificationHandler).cancel()
            }
        }
    }

    /**
     * Tells if the given sound notification is currently played.
     *
     * @param data Additional data for the event.
     */
    override fun isPlayingNotification(data: NotificationData?): Boolean {
        var isPlaying = false
        val soundHandlers = getActionHandlers(NotificationAction.ACTION_SOUND)

        // There could be no sound action handler for this event type
        if (soundHandlers != null) {
            for (handler in soundHandlers) {
                if (handler is SoundNotificationHandler) {
                    isPlaying = isPlaying or handler.isPlaying(data)
                }
            }
        }
        return isPlaying
    }

    companion object {
        private const val NOTIFICATIONS_PREFIX = "notifications"

        /**
         * Defines the number of actions that have to be registered before cached notifications are fired.
         *
         *
         * Current value = 4 (vibrate action excluded).
         */
        const val NUM_ACTIONS = 4

        /**
         * Check if Quite Hours is in effect
         *
         * @return false if option is not enable or is not wihtin the quite hours period.
         */
        val isQuietHours: Boolean
            get() {
                if (!isQuiteHoursEnable()) return false
                val startTime = TimePreference.minutesToTimestamp(getQuiteHoursStart())
                val endTime = TimePreference.minutesToTimestamp(getQuiteHoursEnd())
                val nowTime = Calendar.getInstance().timeInMillis
                return if (endTime < startTime) {
                    nowTime > startTime || nowTime < endTime
                } else {
                    nowTime in (startTime + 1) until endTime
                }
            }
    }
}