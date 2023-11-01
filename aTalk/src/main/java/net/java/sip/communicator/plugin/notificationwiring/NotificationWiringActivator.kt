/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.notificationwiring

import net.java.sip.communicator.service.gui.UIService
import net.java.sip.communicator.service.notification.NotificationService
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.service.neomedia.MediaService
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import timber.log.Timber

/**
 * The `NotificationActivator` is the activator of the notification bundle.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class NotificationWiringActivator : BundleActivator {
    @Throws(Exception::class)
    override fun start(bc: BundleContext) {
        bundleContext = bc
        // Get the notification service implementation
        val notifReference = bundleContext!!.getServiceReference(NotificationService::class.java.name)
        notificationService = bundleContext!!.getService(notifReference as ServiceReference<Any>) as NotificationService
        NotificationManager().init()
        Timber.d("Notification wiring plugin ...[REGISTERED]")
    }

    @Throws(Exception::class)
    override fun stop(bc: BundleContext) {
        Timber.d("Notification handler Service ...[STOPPED]")
    }

    companion object {
        var bundleContext: BundleContext? = null
        private lateinit var notificationService: NotificationService
        private var resourcesService: ResourceManagementService? = null
        private var uiService: UIService? = null

        /**
         * Returns an instance of the `MediaService` obtained from the bundle context.
         *
         * @return an instance of the `MediaService` obtained from the bundle context
         */
        var mediaService: MediaService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, MediaService::class.java)
                }
                return field
            }
            private set

        /**
         * Returns the `NotificationService` obtained from the bundle context.
         *
         * @return the `NotificationService` obtained from the bundle context
         */
        fun getNotificationService(): NotificationService {
            return notificationService
        }

        /**
         * Returns the `ResourceManagementService`, through which we will access all resources.
         *
         * @return the `ResourceManagementService`, through which we will access all resources.
         */
        val resources: ResourceManagementService
            get() {
                if (resourcesService == null) {
                    resourcesService = ServiceUtils.getService(bundleContext, ResourceManagementService::class.java)
                }
                return resourcesService!!
            }

        /**
         * Returns the current implementation of the `UIService`.
         *
         * @return the current implementation of the `UIService`
         */
        val uIService: UIService?
            get() {
                if (uiService == null) {
                    uiService = ServiceUtils.getService(bundleContext, UIService::class.java)
                }
                return uiService
            }
    }
}