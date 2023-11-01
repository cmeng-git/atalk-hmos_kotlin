/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.sipaccregwizz

import net.java.sip.communicator.service.certificate.CertificateService
import net.java.sip.communicator.service.gui.AccountRegistrationWizard
import net.java.sip.communicator.service.gui.UIService
import net.java.sip.communicator.service.protocol.ProtocolNames
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import org.atalk.service.configuration.ConfigurationService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.util.*

/**
 * Registers the `SIPAccountRegistrationWizard` in the UI Service.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class SIPAccountRegistrationActivator : BundleActivator {
    /**
     * Starts this bundle.
     *
     * @param bc BundleContext
     * @throws Exception
     */
    @Throws(Exception::class)
    override fun start(bc: BundleContext) {
        bundleContext = bc
        val uiServiceRef = bundleContext!!.getServiceReference(UIService::class.java.name)
        sipWizard = AccountRegistrationImpl()
        val containerFilter = Hashtable<String, String>()
        containerFilter[ProtocolProviderFactory.PROTOCOL] = ProtocolNames.SIP
        bundleContext!!.registerService(
                AccountRegistrationWizard::class.java.name,
                sipWizard,
                containerFilter)
    }

    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
    }

    companion object {
        var bundleContext: BundleContext? = null

        /**
         * A reference to the configuration service.
         */
        private var configService: ConfigurationService? = null
        private var sipWizard: AccountRegistrationImpl? = null
        private var certService: CertificateService? = null

        /**
         * Returns the `ProtocolProviderFactory` for the SIP protocol.
         *
         * @return the `ProtocolProviderFactory` for the SIP protocol
         */
        val sipProtocolProviderFactory: ProtocolProviderFactory?
            get() {
                val serRefs: Array<ServiceReference<*>?>?
                val osgiFilter = ("(" + ProtocolProviderFactory.PROTOCOL + "=" + ProtocolNames.SIP
                        + ")")
                serRefs = try {
                    bundleContext!!.getServiceReferences(
                            ProtocolProviderFactory::class.java.name, osgiFilter)
                } catch (ex: InvalidSyntaxException) {
                    Timber.e("SIPAccRegWizzActivator: %s", ex.message)
                    return null
                }
                return bundleContext!!.getService(serRefs!!.get(0)) as ProtocolProviderFactory
            }

        /**
         * Returns the `ConfigurationService` obtained from the bundle
         * context.
         *
         * @return the `ConfigurationService` obtained from the bundle
         * context
         */
        val configurationService: ConfigurationService?
            get() {
                if (configService == null) {
                    val serviceReference = bundleContext!!.getServiceReference(ConfigurationService::class.java.getName())
                    configService = bundleContext!!.getService(serviceReference) as ConfigurationService
                }
                return configService
            }

        /**
         * Returns the `CertificateService` obtained from the bundle
         * context.
         *
         * @return the `CertificateService` obtained from the bundle
         * context
         */
        val certificateService: CertificateService?
            get() {
                if (certService == null) {
                    val serviceReference = bundleContext!!.getServiceReference(CertificateService::class.java.getName())
                    certService = bundleContext!!.getService(serviceReference) as CertificateService
                }
                return certService
            }
    }
}