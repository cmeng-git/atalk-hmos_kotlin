/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService
import net.java.sip.communicator.service.globaldisplaydetails.GlobalDisplayDetailsService
import net.java.sip.communicator.service.googlecontacts.GoogleContactsEntry
import net.java.sip.communicator.service.googlecontacts.GoogleContactsService
import net.java.sip.communicator.service.gui.UIService
import net.java.sip.communicator.service.hid.HIDService
import net.java.sip.communicator.service.netaddr.NetworkAddressManagerService
import net.java.sip.communicator.service.protocol.PhoneNumberI18nService
import net.java.sip.communicator.service.protocol.ProtocolNames
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.resources.ResourceManagementServiceUtils
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.neomedia.MediaService
import org.atalk.service.resources.ResourceManagementService
import org.atalk.service.version.VersionService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import org.osgi.framework.ServiceRegistration
import java.util.*

/**
 * Loads the Jabber provider factory and registers it with service in the OSGI bundle context.
 *
 * @author Damian Minkov
 * @author Symphorien Wanko
 * @author Emil Ivov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class JabberActivator : BundleActivator {
    /**
     * Service reference for the currently valid Jabber provider factory.
     */
    private var jabberPpFactoryServReg: ServiceRegistration<*>? = null

    /**
     * The `UriHandler` implementation that we use to handle "xmpp:" URIs
     */
    private var uriHandlerImpl: UriHandlerJabberImpl? = null

    /**
     * Called when this bundle is started so the Framework can perform the bundle-specific
     * activities necessary to start this bundle.
     *
     * @param context The execution context of the bundle being started.
     * @throws Exception If this method throws an exception, this bundle is marked as stopped and the
     * Framework will remove this bundle's listeners, unregister all services registered by
     * this bundle, and release all services used by this bundle.
     */
    @Throws(Exception::class)
    override fun start(context: BundleContext) {
        bundleContext = context
        val hashtable = Hashtable<String, String>()
        hashtable[ProtocolProviderFactory.PROTOCOL] = ProtocolNames.JABBER
        protocolProviderFactory = ProtocolProviderFactoryJabberImpl()

        /*
         * Install the UriHandler prior to registering the factory service in order to allow it to
         * detect when the stored accounts are loaded (because they may be asynchronously loaded).
         */
        uriHandlerImpl = UriHandlerJabberImpl(protocolProviderFactory)

        // register the jabber account man.
        jabberPpFactoryServReg = context.registerService(ProtocolProviderFactory::class.java.name, protocolProviderFactory, hashtable)
    }

    /**
     * Called when this bundle is stopped so the Framework can perform the bundle-specific
     * activities necessary to stop the bundle.
     *
     * @param context The execution context of the bundle being stopped.
     * @throws Exception If this method throws an exception, the bundle is still marked as stopped, and the
     * Framework will remove the bundle's listeners, unregister all services registered by
     * the bundle, and release all services used by the bundle.
     */
    @Throws(Exception::class)
    override fun stop(context: BundleContext) {
        protocolProviderFactory.stop()
        jabberPpFactoryServReg!!.unregister()
        if (uriHandlerImpl != null) {
            uriHandlerImpl!!.dispose()
            uriHandlerImpl = null
        }
        configurationService = null
        mediaService = null
        networkAddressManagerService = null
        credentialsService = null
    }

    companion object {
        /**
         * Bundle context from OSGi.
         */
        lateinit var bundleContext: BundleContext

        /**
         * Configuration service.
         */
        private var configurationService: ConfigurationService? = null

        /**
         * Media service.
         */
        private var mediaService: MediaService? = null

        /**
         * A reference to the currently valid [NetworkAddressManagerService].
         */
        private var networkAddressManagerService: NetworkAddressManagerService? = null

        /**
         * A reference to the currently valid [CredentialsStorageService].
         */
        private var credentialsService: CredentialsStorageService? = null
        /**
         * Returns a reference to the protocol provider factory that we have registered.
         *
         * @return a reference to the `ProtocolProviderFactoryJabberImpl` instance that we have
         * registered from this package.
         */
        /**
         * The Jabber protocol provider factory.
         */
        lateinit var protocolProviderFactory: ProtocolProviderFactoryJabberImpl

        /**
         * A reference to the currently valid `UIService`.
         */
        private var uiService: UIService? = null

        /**
         * A reference to the currently valid `ResoucreManagementService` instance.
         */
        private var resourcesService: ResourceManagementService? = null

        /**
         * A reference to the currently valid `HIDService` instance.
         */
        private var hidService: HIDService? = null
        /**
         * Returns a reference to the GoogleContactsService implementation currently registered in the
         * bundle context or null if no such implementation was found.
         *
         * @return a reference to a GoogleContactsService implementation currently registered in the
         * bundle context or null if no such implementation was found.
         */
        /**
         * A reference to the currently valid `GoogleContactsService` instance.
         */
        var googleService: GoogleContactsService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, GoogleContactsService::class.java)
                }
                return field
            }
            private set
        /**
         * Returns a reference to a VersionService implementation currently registered in the bundle
         * context or null if no such implementation was found.
         *
         * @return a reference to a VersionService implementation currently registered in the bundle
         * context or null if no such implementation was found.
         */
        /**
         * A reference to the currently valid `VersionService` instance.
         */
        var versionService: VersionService? = null
            get() {
                if (field == null) {
                    val versionServiceReference = bundleContext.getServiceReference(VersionService::class.java.name) as ServiceReference<VersionService>
                    field = bundleContext.getService(versionServiceReference) as VersionService
                }
                return field
            }
            private set
        /**
         * Returns the PhoneNumberI18nService.
         *
         * @return returns the PhoneNumberI18nService.
         */
        /**
         * The registered PhoneNumberI18nService.
         */
        var phoneNumberI18nService: PhoneNumberI18nService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, PhoneNumberI18nService::class.java)
                }
                return field
            }
            private set
        /**
         * Returns the `GlobalDisplayDetailsService` obtained from the bundle context.
         *
         * @return the `GlobalDisplayDetailsService` obtained from the bundle context
         */
        /**
         * The global display details service instance.
         */
        var globalDisplayDetailsService: GlobalDisplayDetailsService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, GlobalDisplayDetailsService::class.java)
                }
                return field
            }
            private set

        /**
         * Returns a reference to a ConfigurationService implementation currently registered in the
         * bundle context or null if no such implementation was found.
         *
         * @return ConfigurationService a currently valid implementation of the configuration service.
         */
        fun getConfigurationService(): ConfigurationService? {
            if (configurationService == null) {
                configurationService = ServiceUtils.getService(bundleContext, ConfigurationService::class.java)
            }
            return configurationService
        }

        /**
         * Returns a reference to the UIService implementation currently registered in the bundle
         * context or null if no such implementation was found.
         *
         * @return a reference to a UIService implementation currently registered in the bundle context
         * or null if no such implementation was found.
         */
        val uIService: UIService?
            get() {
                if (uiService == null) {
                    val uiServiceReference = bundleContext.getServiceReference(UIService::class.java.name)
                    uiService = bundleContext.getService(uiServiceReference) as UIService
                }
                return uiService
            }

        /**
         * Returns a reference to the ResourceManagementService implementation currently registered in
         * the bundle context or `null` if no such implementation was found.
         *
         * @return a reference to the ResourceManagementService implementation currently registered in
         * the bundle context or `null` if no such implementation was found.
         */
        val resources: ResourceManagementService?
            get() {
                if (resourcesService == null) resourcesService = ResourceManagementServiceUtils.getService(bundleContext)
                return resourcesService
            }

        /**
         * Returns a reference to a [MediaService] implementation currently registered in the
         * bundle context or null if no such implementation was found.
         *
         * @return a reference to a [MediaService] implementation currently registered in the
         * bundle context or null if no such implementation was found.
         */
        @JvmStatic
        fun getMediaService(): MediaService? {
            if (mediaService == null) {
                val mediaServiceReference = bundleContext.getServiceReference(MediaService::class.java.name)
                mediaService = bundleContext.getService(mediaServiceReference) as MediaService
            }
            return mediaService
        }

        /**
         * Returns a reference to a NetworkAddressManagerService implementation currently registered in
         * the bundle context or null if no such implementation was found.
         *
         * @return a currently valid implementation of the NetworkAddressManagerService .
         */
        fun getNetworkAddressManagerService(): NetworkAddressManagerService? {
            if (networkAddressManagerService == null) {
                val confReference = bundleContext.getServiceReference(NetworkAddressManagerService::class.java.name)
                networkAddressManagerService = bundleContext.getService(confReference) as NetworkAddressManagerService
            }
            return networkAddressManagerService
        }

        /**
         * Returns a reference to a CredentialsStorageService implementation currently registered in the
         * bundle context or null if no such implementation was found.
         *
         * @return a currently valid implementation of the CredentialsStorageService
         */
        val credentialsStorageService: CredentialsStorageService?
            get() {
                if (credentialsService == null) {
                    val confReference = bundleContext.getServiceReference(CredentialsStorageService::class.java.name)
                    credentialsService = bundleContext.getService(confReference) as CredentialsStorageService
                }
                return credentialsService
            }

        /**
         * Returns a reference to `HIDService` implementation currently registered in the bundle
         * context or null if no such implementation was found
         *
         * @return a currently valid implementation of the `HIDService`
         */
        val hIDService: HIDService?
            get() {
                if (hidService == null) {
                    val hidReference = bundleContext.getServiceReference(HIDService::class.java.name)
                            ?: return null
                    hidService = bundleContext.getService(hidReference) as HIDService
                }
                return hidService
            }
    }
}