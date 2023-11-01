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
package net.java.sip.communicator.impl.certificate

import net.java.sip.communicator.service.certificate.CertificateService
import net.java.sip.communicator.service.certificate.VerifyCertificateDialogService
import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService
import net.java.sip.communicator.service.gui.AuthenticationWindowService
import net.java.sip.communicator.util.ServiceUtils.getService
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext

/**
 * The certificate verification bundle activator.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class CertificateVerificationActivator : BundleActivator {
    /**
     * Called when this bundle is started.
     *
     * @param bc The execution context of the bundle being started.
     * @throws Exception if the bundle is not correctly started
     */
    @Throws(Exception::class)
    override fun start(bc: BundleContext) {
        bundleContext = bc
        bundleContext!!.registerService(CertificateService::class.java.name, CertificateServiceImpl(), null)
    }

    /**
     * Called when this bundle is stopped so the Framework can perform the
     * bundle-specific activities necessary to stop the bundle.
     *
     * @param bc The execution context of the bundle being stopped.
     * @throws Exception If this method throws an exception, the bundle is
     * still marked as stopped, and the Framework will remove the bundle's
     * listeners, unregister all services registered by the bundle, and
     * release all services used by the bundle.
     */
    @Throws(Exception::class)
    override fun stop(bc: BundleContext) {
        configService = null
        resourcesService = null
        credService = null
        certificateDialogService = null
    }

    companion object {
        /**
         * The bundle context for this bundle.
         */
        protected var bundleContext: BundleContext? = null

        /**
         * The configuration service.
         */
        private var configService: ConfigurationService? = null

        /**
         * The service giving access to all resources.
         */
        private var resourcesService: ResourceManagementService? = null

        /**
         * The service to store and access passwords.
         */
        private var credService: CredentialsStorageService? = null

        /**
         * The service to create and show dialogs for user interaction.
         */
        private var certificateDialogService: VerifyCertificateDialogService? = null

        /**
         * Returns the `ConfigurationService` obtained from the bundle context.
         *
         * @return the `ConfigurationService` obtained from the bundle context
         */
        val configurationService: ConfigurationService?
            get() {
                if (configService == null) {
                    configService = getService(bundleContext, ConfigurationService::class.java)
                }
                return configService
            }

        /**
         * Returns the `ResourceManagementService`, through which we will access all resources.
         *
         * @return the `ResourceManagementService`, through which we will access all resources.
         */
        val resources: ResourceManagementService?
            get() {
                if (resourcesService == null) {
                    resourcesService = getService(bundleContext, ResourceManagementService::class.java)
                }
                return resourcesService
            }

        /**
         * Returns the `CredentialsStorageService`, through which we will access all passwords.
         *
         * @return the `CredentialsStorageService`, through which we will access all passwords.
         */
        fun getCredService(): CredentialsStorageService? {
            if (credService == null) {
                credService = getService(bundleContext, CredentialsStorageService::class.java)
            }
            return credService
        }

        /**
         * Returns the `VerifyCertificateDialogService`, through which we will use to create dialogs.
         *
         * @return the `VerifyCertificateDialogService`, through which we will use to create dialogs.
         */
        fun getCertificateDialogService(): VerifyCertificateDialogService? {
            if (certificateDialogService == null) {
                certificateDialogService = getService(bundleContext, VerifyCertificateDialogService::class.java)
            }
            return certificateDialogService
        }

        /**
         * Returns service to show authentication window.
         *
         * @return return service to show authentication window.
         */
        val authenticationWindowService: AuthenticationWindowService?
            get() = getService(bundleContext, AuthenticationWindowService::class.java)
    }
}