/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util

import net.java.sip.communicator.service.gui.AlertUIService
import net.java.sip.communicator.service.gui.UIService
import net.java.sip.communicator.service.protocol.AccountManager
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.resources.ResourceManagementServiceUtils
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.fileaccess.FileAccessService
import org.atalk.service.neomedia.MediaConfigurationService
import org.atalk.service.neomedia.MediaService
import org.atalk.service.resources.ResourceManagementService
import org.atalk.util.OSUtils
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.util.*

/**
 * The only reason d'etre for this Activator is so that it would set a global exception handler.
 * It doesn't export any services and neither it runs any initialization - all it does is call
 * `Thread.setUncaughtExceptionHandler()`
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class UtilActivator : BundleActivator, Thread.UncaughtExceptionHandler {
    /**
     * Calls `Thread.setUncaughtExceptionHandler()`
     *
     * @param context The execution context of the bundle being started (unused).
     * @throws Exception If this method throws an exception, this bundle is marked as stopped and the Framework
     * will remove this bundle's listeners, unregister all services registered by this
     * bundle, and release all services used by this bundle.
     */
    @Throws(Exception::class)
    override fun start(context: BundleContext) {
        bundleContext = context
        if (OSUtils.IS_ANDROID) loadLoggingConfig()
        Timber.log(TimberLog.FINER, "Setting default uncaught exception handler.")
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    /**
     * Loads logging config if any. Need to be loaded in order to activate logging and need to be
     * activated after bundle context is initialized.
     */
    private fun loadLoggingConfig() {
        try {
            Class.forName("net.java.sip.communicator.util.JavaUtilLoggingConfig").newInstance()
        } catch (ignore: Throwable) {
        }
    }

    /**
     * Method invoked when a thread would terminate due to the given uncaught exception. All we do
     * here is simply log the exception using the system logger.
     *
     *
     *
     *
     * Any exception thrown by this method will be ignored by the Java Virtual Machine and thus
     * won't screw our application.
     *
     * @param thread the thread
     * @param exc the exception
     */
    override fun uncaughtException(thread: Thread, exc: Throwable) {
        Timber.e(exc, "An uncaught exception occurred in thread = %s and message was: %s", thread, exc.message)
    }

    /**
     * Doesn't do anything.
     *
     * @param context The execution context of the bundle being stopped.
     * @throws Exception If this method throws an exception, the bundle is still marked as stopped, and the
     * Framework will remove the bundle's listeners, unregister all services registered by
     * the bundle, and release all services used by the bundle.
     */
    @Throws(Exception::class)
    override fun stop(context: BundleContext) {
    }

    companion object {
        /**
         * Returns the `ConfigurationService` currently registered.
         *
         * @return the `ConfigurationService`
         */
        var configurationService: ConfigurationService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, ConfigurationService::class.java)
                }
                return field
            }
            private set
        private var resourceService: ResourceManagementService? = null
        private var uiService: UIService? = null

        /**
         * Returns the `FileAccessService` obtained from the bundle context.
         *
         * @return the `FileAccessService` obtained from the bundle context
         */
        var fileAccessService: FileAccessService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, FileAccessService::class.java)
                }
                return field
            }
            private set

        /**
         * An instance of the `MediaService` obtained from the bundle context.
\         */
        var mediaService: MediaService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, MediaService::class.java)
                }
                return field
            }
            private set

        var bundleContext: BundleContext? = null

        /**
         * Returns the `AccountManager` obtained from the bundle context.
         *
         * @return the `AccountManager` obtained from the bundle context
         */
        var accountManager: AccountManager? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, AccountManager::class.java)
                }
                return field
            }
            private set

        /**
         * Returns the `AlertUIService` obtained from the bundle context.
         *
         * @return the `AlertUIService` obtained from the bundle context
         */
        var alertUIService: AlertUIService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, AlertUIService::class.java)
                }
                return field
            }
            private set

        /**
         * Returns the service giving access to all application resources.
         *
         * @return the service giving access to all application resources.
         */
        val resources: ResourceManagementService
            get() {
                if (resourceService == null) {
                    resourceService = ResourceManagementServiceUtils.getService(bundleContext)
                }
                return resourceService!!
            }

        /**
         * Gets the `UIService` instance registered in the `BundleContext` of the
         * `UtilActivator`.
         *
         * @return the `UIService` instance registered in the `BundleContext` of the
         * `UtilActivator`
         */
        val uIService: UIService?
            get() {
                if (uiService == null) uiService = ServiceUtils.getService(bundleContext, UIService::class.java)
                return uiService
            }

        /**
         * Returns the [MediaConfigurationService] instance registered in the `BundleContext` of the `UtilActivator`.
         *
         * @return the `UIService` instance registered in the `BundleContext` of the `UtilActivator`
         */
        val mediaConfiguration: MediaConfigurationService?
            get() = ServiceUtils.getService(bundleContext, MediaConfigurationService::class.java)// get all registered provider factories

        /**
         * Returns all `ProtocolProviderFactory`s obtained from the bundle context.
         *
         * @return all `ProtocolProviderFactory`s obtained from the bundle context
         */
        val protocolProviderFactories: MutableMap<Any, ProtocolProviderFactory>
            get() {
                var serRefs: Collection<ServiceReference<ProtocolProviderFactory>>? = null
                val providerFactoriesMap = Hashtable<Any, ProtocolProviderFactory>()

                // get all registered provider factories
                try {
                    if (bundleContext != null) {
                        serRefs = bundleContext!!.getServiceReferences(ProtocolProviderFactory::class.java, null)
                    }
                } catch (ex: InvalidSyntaxException) {
                    serRefs = null
                    Timber.e("LoginManager : %s", ex.message)
                }

                if (serRefs != null && !serRefs.isEmpty()) {
                    for (serRef in serRefs) {
                        val providerFactory = bundleContext!!.getService(serRef)
                        providerFactoriesMap[serRef.getProperty(ProtocolProviderFactory.PROTOCOL)] = providerFactory
                    }
                }
                return providerFactoriesMap
            }
    }
}