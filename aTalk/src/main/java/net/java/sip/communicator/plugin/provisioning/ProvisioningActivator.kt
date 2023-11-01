/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.provisioning

import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService
import net.java.sip.communicator.service.gui.ConfigurationForm
import net.java.sip.communicator.service.gui.LazyConfigurationForm
import net.java.sip.communicator.service.gui.UIService
import net.java.sip.communicator.service.netaddr.NetworkAddressManagerService
import net.java.sip.communicator.service.provdisc.ProvisioningDiscoveryService
import net.java.sip.communicator.service.provisioning.ProvisioningService
import org.apache.commons.lang3.StringUtils
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.util.*

/**
 * Activator the provisioning system. It will gather provisioning URL depending on the
 * configuration (DHCP, manual, ...), retrieve configuration file and push properties to the
 * `ConfigurationService`.
 */
class ProvisioningActivator : BundleActivator {
    /**
     * Starts this bundle
     *
     * @param bundleContext BundleContext
     * @throws Exception if anything goes wrong during the start of the bundle
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        Companion.bundleContext = bundleContext
        var url: String? = null
        provisioningService = ProvisioningServiceImpl()

        // Show/hide provisioning configuration form.
        if (!configurationService!!.getBoolean(DISABLED_PROP, false)) {
            val properties: Dictionary<String, String> = Hashtable()
            properties.put(ConfigurationForm.FORM_TYPE, ConfigurationForm.ADVANCED_TYPE)
            bundleContext.registerService(ConfigurationForm::class.java.name,
                    LazyConfigurationForm(
                            "net.java.sip.communicator.plugin.provisioning.ProvisioningForm",
                            javaClass.classLoader, "plugin.provisioning.PLUGIN_ICON",
                            "plugin.provisioning.PROVISIONING", 2000, true), properties)
        }
        val method = provisioningService!!.provisioningMethod
        if (StringUtils.isBlank(method) || method == "NONE") {
            return
        }
        val serviceReferences = bundleContext.getServiceReferences(
                ProvisioningDiscoveryService::class.java.name, null)

        /*
         * search the provisioning discovery implementation that correspond to the method name
         */
        if (serviceReferences != null) {
            for (ref in serviceReferences) {
                val provdisc = bundleContext.getService<Any>(ref as ServiceReference<Any>) as ProvisioningDiscoveryService
                if (provdisc.getMethodName() == method) {
                    /* may block for sometime depending on the method used */
                    url = provdisc.discoverURL()
                    break
                }
            }
        }
        provisioningService!!.start(url)
        bundleContext.registerService(ProvisioningService::class.java.name, provisioningService, null)
        Timber.d("Provisioning discovery [REGISTERED]")
    }

    /**
     * Stops this bundle
     *
     * @param bundleContext BundleContext
     * @throws Exception if anything goes wrong during the stop of the bundle
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
        Companion.bundleContext = null
        Timber.d("Provisioning discovery [STOPPED]")
    }

    companion object {
        /**
         * The current BundleContext.
         */
        var bundleContext: BundleContext? = null
        /**
         * Returns a reference to a ConfigurationService implementation currently registered in the
         * bundle context or null if no such implementation was found.
         *
         * @return a currently valid implementation of the ConfigurationService.
         */
        /**
         * A reference to the ConfigurationService implementation instance that is currently
         * registered with the bundle context.
         */
        var configurationService: ConfigurationService? = null
            get() {
                if (field == null) {
                    val confReference = bundleContext!!.getServiceReference(ConfigurationService::class.java.getName())
                    field = bundleContext!!.getService<Any>(confReference as ServiceReference<Any>) as ConfigurationService
                }
                return field
            }
            private set

        /**
         * A reference to the CredentialsStorageService implementation instance that is registered
         * with the bundle context.
         */
        private var credentialsService: CredentialsStorageService? = null

        /**
         * A reference to the NetworkAddressManagerService implementation instance that is registered
         * with the bundle context.
         */
        private var netaddrService: NetworkAddressManagerService? = null

        /**
         * The user interface service.
         */
        private var uiService: UIService? = null
        /**
         * Returns the `ResourceManagementService` obtained from the bundle context.
         *
         * @return the `ResourceManagementService` obtained from the bundle context
         */
        /**
         * The resource service.
         */
        var resourceService: ResourceManagementService? = null
            get() {
                if (field == null) {
                    val resourceReference = bundleContext!!.getServiceReference(ResourceManagementService::class.java.getName())
                    field = bundleContext!!.getService<Any>(resourceReference as ServiceReference<Any>) as ResourceManagementService
                }
                return field
            }
            private set
        /**
         * Returns a reference to a `ProvisioningService` implementation.
         *
         * @return a currently valid implementation of `ProvisioningService`
         */
        /**
         * Provisioning service.
         */
        var provisioningService: ProvisioningServiceImpl? = null

        /**
         * Indicates if the provisioning configuration form should be disabled, i.e. not visible to the user.
         */
        private const val DISABLED_PROP = "provisionconfig.DISABLED"

        /**
         * Returns the `UIService` obtained from the bundle context.
         *
         * @return the `UIService` obtained from the bundle context
         */
        val uIService: UIService?
            get() {
                if (uiService == null) {
                    val uiReference = bundleContext!!.getServiceReference(UIService::class.java.getName())
                    uiService = bundleContext!!.getService<Any>(uiReference as ServiceReference<Any>) as UIService
                }
                return uiService
            }

        /**
         * Returns a reference to a CredentialsStorageService implementation currently registered in
         * the bundle context or null if no such implementation was found.
         *
         * @return a currently valid implementation of the CredentialsStorageService.
         */
        val credentialsStorageService: CredentialsStorageService?
            get() {
                if (credentialsService == null) {
                    val credentialsReference = bundleContext!!.getServiceReference(CredentialsStorageService::class.java.getName())
                    credentialsService = bundleContext!!.getService<Any>(credentialsReference as ServiceReference<Any>) as CredentialsStorageService
                }
                return credentialsService
            }

        /**
         * Returns a reference to a NetworkAddressManagerService implementation currently registered
         * in the bundle context or null if no such implementation was found.
         *
         * @return a currently valid implementation of the NetworkAddressManagerService.
         */
        val networkAddressManagerService: NetworkAddressManagerService?
            get() {
                if (netaddrService == null) {
                    val netaddrReference = bundleContext!!.getServiceReference(NetworkAddressManagerService::class.java.getName())
                    netaddrService = bundleContext!!.getService<Any>(netaddrReference as ServiceReference<Any>) as NetworkAddressManagerService
                }
                return netaddrService
            }
    }
}