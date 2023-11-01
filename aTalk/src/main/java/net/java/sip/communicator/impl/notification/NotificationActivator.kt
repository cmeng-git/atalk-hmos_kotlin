/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.notification

import net.java.sip.communicator.service.gui.UIService
import net.java.sip.communicator.service.notification.CommandNotificationHandler
import net.java.sip.communicator.service.notification.LogMessageNotificationHandler
import net.java.sip.communicator.service.notification.NotificationService
import net.java.sip.communicator.service.notification.PopupMessageNotificationHandler
import net.java.sip.communicator.service.notification.SoundNotificationHandler
import net.java.sip.communicator.service.systray.SystrayService
import net.java.sip.communicator.util.ServiceUtils.getService
import org.atalk.service.audionotifier.AudioNotifierService
import org.atalk.service.configuration.ConfigurationService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import timber.log.Timber

/**
 * The `NotificationActivator` is the activator of the notification bundle.
 *
 * @author Yana Stamcheva
 */
class NotificationActivator : BundleActivator {
    private var commandHandler: CommandNotificationHandler? = null
    private var logMessageHandler: LogMessageNotificationHandler? = null
    private var popupMessageHandler: PopupMessageNotificationHandler? = null
    private var soundHandler: SoundNotificationHandler? = null
    @Throws(Exception::class)
    override fun start(bc: BundleContext) {
        bundleContext = bc
        // Get the notification service implementation
        val notifReference = bundleContext!!.getServiceReference(NotificationService::class.java.name)
        notificationService = bundleContext!!.getService<Any>(notifReference as ServiceReference<Any>) as NotificationService
        commandHandler = CommandNotificationHandlerImpl()
        logMessageHandler = LogMessageNotificationHandlerImpl()
        popupMessageHandler = PopupMessageNotificationHandlerImpl()
        soundHandler = SoundNotificationHandlerImpl()
        notificationService!!.addActionHandler(commandHandler)
        notificationService!!.addActionHandler(logMessageHandler)
        notificationService!!.addActionHandler(popupMessageHandler)
        notificationService!!.addActionHandler(soundHandler)
        Timber.i("Notification handler Service ...[REGISTERED]")
    }

    @Throws(Exception::class)
    override fun stop(bc: BundleContext) {
        notificationService!!.removeActionHandler(commandHandler!!.actionType)
        notificationService!!.removeActionHandler(logMessageHandler!!.actionType)
        notificationService!!.removeActionHandler(popupMessageHandler!!.actionType)
        notificationService!!.removeActionHandler(soundHandler!!.actionType)
        Timber.i("Notification handler Service ...[STOPPED]")
    }

    companion object {
        protected var bundleContext: BundleContext? = null
        private var audioNotifierService: AudioNotifierService? = null
        private var systrayService: SystrayService? = null
        private var notificationService: NotificationService? = null

        /**
         * A reference to the `UIService` currently in use in Jitsi.
         */
        private var uiService: UIService? = null
        /**
         * Returns a reference to a ConfigurationService implementation currently registered in the bundle context
         * or null if no such implementation was found.
         *
         * @return a currently valid implementation of the ConfigurationService.
         */
        /**
         * The `ConfigurationService` registered in [.bundleContext] and used by the
         * `NotificationActivator` instance to read and write configuration properties.
         */
        var configurationService: ConfigurationService? = null
            get() {
                if (field == null) {
                    field = getService(bundleContext, ConfigurationService::class.java)
                }
                return field
            }
            private set

        /**
         * Returns the `AudioNotifierService` obtained from the bundle context.
         *
         * @return the `AudioNotifierService` obtained from the bundle context
         */
        val audioNotifier: AudioNotifierService?
            get() {
                if (audioNotifierService == null) {
                    val serviceReference = bundleContext!!.getServiceReference(AudioNotifierService::class.java.name)
                    if (serviceReference != null) audioNotifierService = bundleContext!!.getService<Any>(serviceReference as ServiceReference<Any>) as AudioNotifierService
                }
                return audioNotifierService
            }

        /**
         * Returns the `SystrayService` obtained from the bundle context.
         *
         * @return the `SystrayService` obtained from the bundle context
         */
        val systray: SystrayService?
            get() {
                if (systrayService == null) {
                    systrayService = getService(bundleContext, SystrayService::class.java)
                }
                return systrayService
            }

        /**
         * Returns a reference to an UIService implementation currently registered in the bundle context
         * or null if no such implementation was found.
         *
         * @return a reference to an UIService implementation currently registered in the bundle context
         * or null if no such implementation was found.
         */
        val uIService: UIService?
            get() {
                if (uiService == null) uiService = getService(bundleContext, UIService::class.java)
                return uiService
            }
    }
}