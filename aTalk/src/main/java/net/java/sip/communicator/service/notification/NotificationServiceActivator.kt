/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification

import org.atalk.service.configuration.ConfigurationService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import org.osgi.framework.ServiceRegistration
import timber.log.Timber

/**
 * The `NotificationActivator` is the activator of the notification bundle.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class NotificationServiceActivator : BundleActivator {
    private var notificationService: ServiceRegistration<*>? = null
    @Throws(Exception::class)
    override fun start(bc: BundleContext) {
        bundleContext = bc
        notificationService = bundleContext!!.registerService(NotificationService::class.java.name,
                NotificationServiceImpl(), null)
        Timber.d("Notification Service ...[REGISTERED]")
    }

    @Throws(Exception::class)
    override fun stop(bc: BundleContext) {
        notificationService!!.unregister()
        Timber.d("Notification Service ...[STOPPED]")
    }

    companion object {
        protected var bundleContext: BundleContext? = null
        private var configService: ConfigurationService? = null

        /**
         * Returns the `ConfigurationService` obtained from the bundle context.
         *
         * @return the `ConfigurationService` obtained from the bundle context
         */
        val configurationService: ConfigurationService?
            get() {
                if (configService == null) {
                    val configReference = bundleContext!!.getServiceReference(ConfigurationService::class.java.name)
                    configService = bundleContext!!.getService<Any>(configReference as ServiceReference<Any>) as ConfigurationService
                }
                return configService
            }
    }
}