/*
 * aTalk, HMOS XMPP VoIP and Instant Messaging client
 * Copyright 2014-2023 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.plugin.jabberaccregwizz

import net.java.sip.communicator.service.certificate.CertificateService
import net.java.sip.communicator.service.gui.AccountRegistrationWizard
import net.java.sip.communicator.service.protocol.ProtocolNames
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import org.atalk.service.configuration.ConfigurationService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import java.util.*

/**
 * Registers the `SIPAccountRegistrationWizard` in the UI Service.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class JabberAccountRegistrationActivator : BundleActivator {
    /**
     * Starts this bundle.
     *
     * @param bc BundleContext
     * @throws Exception
     */
    @Throws(Exception::class)
    override fun start(bc: BundleContext) {
        bundleContext = bc

        val jabberRegistration = AccountRegistrationImpl()
        val containerFilter = Hashtable<String, String>()
        containerFilter[ProtocolProviderFactory.PROTOCOL] = ProtocolNames.JABBER
        bundleContext.registerService(AccountRegistrationWizard::class.java.name, jabberRegistration, containerFilter)

        val serviceReference = bundleContext.getServiceReference(ConfigurationService::class.java.name)
        configurationService = bundleContext.getService(serviceReference) as ConfigurationService

        val serviceReference1 = bundleContext.getServiceReference(CertificateService::class.java.name)
        certificateService = bundleContext.getService(serviceReference1) as CertificateService

        jabberProtocolProviderFactory = ProtocolProviderFactory.getProtocolProviderFactory(bundleContext, ProtocolNames.JABBER)
    }

    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
    }

    companion object {
        lateinit var bundleContext: BundleContext

        /**
         * The `ProtocolProviderFactory` for the Jabber protocol.
         */
        var jabberProtocolProviderFactory: ProtocolProviderFactory? = null

        /**
         * The `ConfigurationService` obtained from the bundle context.
         */
        lateinit var configurationService: ConfigurationService

        /**
         * The `CertificateService` obtained from the bundle context.
         */
        lateinit var certificateService: CertificateService
    }
}