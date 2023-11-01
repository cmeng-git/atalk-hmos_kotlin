/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.service.httputil

import net.java.sip.communicator.service.certificate.CertificateService
import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService
import net.java.sip.communicator.service.gui.AuthenticationWindowService
import net.java.sip.communicator.service.resources.ResourceManagementServiceUtils
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext

/**
 * The http utils bundle activator. Do nothing just provide access to some services.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class HttpUtilActivator : BundleActivator {
    /**
     * Start the bundle.
     *
     * @param bundleContext
     * @throws Exception
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        Companion.bundleContext = bundleContext
    }

    /**
     * Stops the bundle.
     *
     * @param bundleContext
     * @throws Exception
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
        guiCertificateVerification = null
        credentialsService = null
        resourceService = null
        configurationService = null
    }

    companion object {
        /**
         * The service we use to interact with user regarding certificates.
         */
        private var guiCertificateVerification: CertificateService? = null

        /**
         * Reference to the credentials service
         */
        private var credentialsService: CredentialsStorageService? = null

        /**
         * The bundle context.
         */
        private lateinit var bundleContext: BundleContext

        /**
         * The resource service.
         */
        private var resourceService: ResourceManagementService? = null

        /**
         * A reference to the ConfigurationService implementation instance that
         * is currently registered with the bundle context.
         */
        private var configurationService: ConfigurationService? = null

        /**
         * Return the certificate verification service impl.
         *
         * @return the CertificateVerification service.
         */
        val certificateVerificationService: CertificateService
            get() {
                if (guiCertificateVerification == null) {
                    val guiVerifyReference = bundleContext.getServiceReference(CertificateService::class.java.name)
                    if (guiVerifyReference != null)
                        guiCertificateVerification = bundleContext.getService(guiVerifyReference) as CertificateService
                }
                return guiCertificateVerification!!
            }

        /**
         * Returns a reference to a CredentialsStorageConfigurationService implementation currently
         * registered in the bundle context or null if no such implementation was found.
         *
         * @return a currently valid implementation of the CredentialsStorageService.
         */
        fun getCredentialsService(): CredentialsStorageService {
            if (credentialsService == null) {
                val confReference = bundleContext.getServiceReference(CredentialsStorageService::class.java.name)
                credentialsService = bundleContext.getService(confReference) as CredentialsStorageService
            }
            return credentialsService!!
        }

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
         * Returns a reference to a ConfigurationService implementation currently
         * registered in the bundle context or null if no such implementation was found.
         *
         * @return a currently valid implementation of the ConfigurationService.
         */
        fun getConfigurationService(): ConfigurationService {
            if (configurationService == null) {
                configurationService = ServiceUtils.getService(bundleContext, ConfigurationService::class.java)
            }
            return configurationService!!
        }

        /**
         * Returns service to show authentication window.
         *
         * @return return service to show authentication window.
         */
        val authenticationWindowService: AuthenticationWindowService?
            get() = ServiceUtils.getService(bundleContext, AuthenticationWindowService::class.java)
    }
}