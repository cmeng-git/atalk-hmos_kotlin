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
package net.java.sip.communicator.impl.provdisc.dhcp

import net.java.sip.communicator.service.netaddr.NetworkAddressManagerService
import net.java.sip.communicator.service.provdisc.ProvisioningDiscoveryService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import timber.log.Timber

/**
 * Implements `BundleActivator` for the DHCP provisioning bundle.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class ProvisioningDiscoveryDHCPActivator : BundleActivator {
    /**
     * Starts the DHCP provisioning service
     *
     * @param bundleContext the `BundleContext` as provided by the OSGi
     * framework.
     * @throws Exception if anything goes wrong
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        bundleContext.registerService(ProvisioningDiscoveryService::class.java.name, provisioningService, null)
        Companion.bundleContext = bundleContext
        Timber.i("DHCP provisioning discovery Service [REGISTERED]")
    }

    /**
     * Stops the DHCP provisioning service.
     *
     * @param bundleContext the `BundleContext` as provided by the OSGi framework.
     * @throws Exception if anything goes wrong
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
        Companion.bundleContext = null
        Timber.i("DHCP provisioning discovery Service ...[STOPPED]")
    }

    companion object {
        /**
         * DHCP provisioning service.
         */
        private val provisioningService = ProvisioningDiscoveryServiceDHCPImpl()

        /**
         * A reference to a NetworkAddressManagerService implementation currently registered in the bundle context
         * or null if no such implementation was found.
         */
        var networkAddressManagerService: NetworkAddressManagerService? = null
            get() {
                if (field == null) {
                    val confReference = bundleContext!!.getServiceReference(NetworkAddressManagerService::class.java.name)
                    field = bundleContext!!.getService<Any>(confReference as ServiceReference<Any>) as NetworkAddressManagerService
                }
                return field
            }
            private set

        /**
         * Bundle context from OSGi.
         */
        private var bundleContext: BundleContext? = null
    }
}