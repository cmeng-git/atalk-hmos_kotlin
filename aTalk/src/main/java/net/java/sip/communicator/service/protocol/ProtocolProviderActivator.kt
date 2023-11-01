/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.calendar.CalendarService
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceReference
import org.osgi.framework.ServiceRegistration
import timber.log.Timber

/**
 * Implements `BundleActivator` for the purposes of
 * protocol.jar/protocol.provider.manifest.mf and in order to register and start services
 * independent of the specifics of a particular protocol.
 *
 * @author Lubomir Marinov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class ProtocolProviderActivator : BundleActivator {
    /**
     * The `ServiceRegistration` of the `AccountManager` implementation
     * registered as a service by this activator and cached so that the service in question can be
     * properly disposed of upon stopping this activator.
     */
    private var accountManagerServiceRegistration: ServiceRegistration<*>? = null

    /**
     * The `SingleCallInProgressPolicy` making sure that the `Call`s
     * accessible in the `BundleContext` of this activator will obey to the rule that a
     * new `Call` should put the other existing `Call`s on hold.
     */
    private var singleCallInProgressPolicy: SingleCallInProgressPolicy? = null

    /**
     * Registers a new `AccountManagerImpl` instance as an `AccountManager`
     * service and starts a new `SingleCallInProgressPolicy` instance to ensure that only
     * one of the `Call`s accessible in the `BundleContext` in which this activator
     * is to execute will be in progress and the others will automatically be put on hold.
     *
     * @param bundleContext the `BundleContext` in which the bundle activation represented by this
     * `BundleActivator` executes
     */
    override fun start(bundleContext: BundleContext) {
        Companion.bundleContext = bundleContext
        accountManager = AccountManager(bundleContext)
        accountManagerServiceRegistration = bundleContext.registerService(AccountManager::class.java.name, accountManager, null)
        Timber.log(TimberLog.FINER, "ProtocolProviderActivator will create SingleCallInProgressPolicy instance.")
        singleCallInProgressPolicy = SingleCallInProgressPolicy(bundleContext)
    }

    /**
     * Unregisters the `AccountManagerImpl` instance registered as an
     * `AccountManager` service in [.start] and stops the
     * `SingleCallInProgressPolicy` started there as well.
     *
     * @param bundleContext the `BundleContext` in which the bundle activation represented by this
     * `BundleActivator` executes
     */
    override fun stop(bundleContext: BundleContext) {
        if (accountManagerServiceRegistration != null) {
            accountManagerServiceRegistration!!.unregister()
            accountManagerServiceRegistration = null
            accountManager = null
        }
        if (singleCallInProgressPolicy != null) {
            singleCallInProgressPolicy!!.dispose()
            singleCallInProgressPolicy = null
        }
        if (bundleContext == Companion.bundleContext) Companion.bundleContext = null
        configurationService = null
        resourceService = null
    }

    companion object {
        /**
         * Get the `AccountManager` of the protocol.
         *
         * @return `AccountManager` of the protocol
         */
        /**
         * The account manager.
         */
        var accountManager: AccountManager? = null
            private set
        /**
         * Returns OSGI bundle context.
         *
         * @return OSGI bundle context.
         */
        /**
         * The `BundleContext` of the one and only `ProtocolProviderActivator`
         * instance which is currently started.
         */
        var bundleContext: BundleContext? = null
            private set

        /**
         * The `ConfigurationService` used by the classes in the bundle represented by
         * `ProtocolProviderActivator`.
         */
        private var configurationService: ConfigurationService? = null

        /**
         * The resource service through which we obtain localized strings.
         */
        private var resourceService: ResourceManagementService? = null
        /**
         * Gets the `CalendarService` to be used by the classes in the bundle represented by
         * `ProtocolProviderActivator`.
         *
         * @return the `CalendarService` to be used by the classes in the bundle represented
         * by `ProtocolProviderActivator`
         */
        /**
         * The calendar service instance.
         */
        var calendarService: CalendarService? = null
            get() {
                if (field == null) {
                    val serviceReference = bundleContext!!
                            .getServiceReference(CalendarService::class.java) ?: return null
                    field = bundleContext!!.getService(serviceReference) as CalendarService
                }
                return field
            }
            private set

        /**
         * Gets the `ConfigurationService` to be used by the classes in the bundle
         * represented by `ProtocolProviderActivator`.
         *
         * @return the `ConfigurationService` to be used by the classes in the bundle
         * represented by `ProtocolProviderActivator`
         */
        fun getConfigurationService(): ConfigurationService? {
            if (configurationService == null && bundleContext != null) {
                val svrReference = bundleContext!!.getServiceReference(ConfigurationService::class.java)
                configurationService = bundleContext!!.getService(svrReference) as ConfigurationService
            }
            return configurationService
        }

        /**
         * Gets the `ResourceManagementService` to be used by the classes in the bundle
         * represented by `ProtocolProviderActivator`.
         *
         * @return the `ResourceManagementService` to be used by the classes in the bundle
         * represented by `ProtocolProviderActivator`
         */
        fun getResourceService(): ResourceManagementService {
            if (resourceService == null) {
                resourceService = bundleContext!!.getService(bundleContext!!.getServiceReference(ResourceManagementService::class.java)) as ResourceManagementService
            }
            return resourceService!!
        }

        /**
         * Returns a `ProtocolProviderFactory` for a given protocol provider.
         *
         * @param protocolName the name of the protocol, which factory we're looking for
         * @return a `ProtocolProviderFactory` for a given protocol provider
         */
        fun getProtocolProviderFactory(protocolName: String): ProtocolProviderFactory? {
            val osgiFilter = "(" + ProtocolProviderFactory.PROTOCOL + "=" + protocolName + ")"
            var protocolProviderFactory: ProtocolProviderFactory? = null
            try {
                val serRefs = bundleContext!!.getServiceReferences(ProtocolProviderFactory::class.java.name, osgiFilter)
                if (serRefs != null && serRefs.isNotEmpty()) {
                    protocolProviderFactory = bundleContext!!.getService(serRefs[0] as ServiceReference<ProtocolProviderFactory>) as ProtocolProviderFactory
                }
            } catch (ex: InvalidSyntaxException) {
                Timber.i("ProtocolProviderActivator : %s", ex.message)
            }
            return protocolProviderFactory
        }// get all registered provider factories

        /**
         * Returns all protocol providers currently registered.
         *
         * @return all protocol providers currently registered.
         */
        val protocolProviders: List<ProtocolProviderService>
            get() {
                var serRefs: Array<ServiceReference<*>?>? = null
                try {
                    // get all registered provider factories
                    serRefs = bundleContext!!.getServiceReferences(ProtocolProviderService::class.java.name, null)
                } catch (e: InvalidSyntaxException) {
                    Timber.e("ProtocolProviderActivator: %s", e.message)
                }
                val providersList = ArrayList<ProtocolProviderService>()
                if (serRefs != null) {
                    for (serRef in serRefs) {
                        val pp = bundleContext!!.getService(serRef) as ProtocolProviderService
                        providersList.add(pp)
                    }
                }
                return providersList
            }
    }
}