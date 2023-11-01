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
package net.java.sip.communicator.impl.netaddr

import net.java.sip.communicator.service.netaddr.NetworkAddressManagerService
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.service.configuration.ConfigurationService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import timber.log.Timber

/**
 * The activator manage the the bundles between OSGi framework and the Network address manager
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class NetaddrActivator : BundleActivator {
    /**
     * The network address manager implementation.
     */
    private var networkAMS: NetworkAddressManagerServiceImpl? = null

    /**
     * Creates a NetworkAddressManager, starts it, and registers it as a NetworkAddressManagerService.
     * @see MappingCandidateHarvesters.initialize
     * @see net.java.sip.communicator.util.NetworkUtils
     *
     * @see net.java.sip.communicator.util.launchutils.LaunchArgHandler.handleIPv4Enforcement
     * @param bundleContext OSGI bundle context
     * @throws Exception if starting the NetworkAddressManagerFails.
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        /*
         * Here we load static properties that should be elsewhere, end ugly property set;
         */
        // System.setProperty("java.net.preferIPv4Stack", "false");
        // System.setProperty("java.net.preferIPv6Addresses", "true");

        /*
         * cmeng: The latest ice4j-2.0.0-20190607.184546-36.jar and ice4j-3.0 is OK AWS harvester enabled
         * Earlier ice4j-2.0 works only with AWS disabled - otherwise hang in AWS EC2 conn.getContent()
         */
        // System.setProperty(MappingCandidateHarvesters.DISABLE_AWS_HARVESTER_PNAME, "true");

        /*
         * ice4j dependency lib org.bitlet.weupnp is used in uPnP Harvester; but not working for android:
         * see https://github.com/bitletorg/weupnp/issues/20'
         * Must redefine the xmp parser for weupnp for android;
        */
        System.setProperty("org.xml.sax.driver", "org.xmlpull.v1.sax2.Driver")

        // keep a reference to the bundle context for later usage.
        Companion.bundleContext = bundleContext

        // Create and start the network address manager.
        networkAMS = NetworkAddressManagerServiceImpl()

        // give references to the NetworkAddressManager implementation
        networkAMS!!.start()
        bundleContext.registerService(NetworkAddressManagerService::class.java.name, networkAMS, null)
        Timber.i("Network Address Manager ICE Service ...[REGISTERED]")
    }

    /**
     * Stops the Network Address Manager bundle
     *
     * @param bundleContext the OSGI bundle context
     */
    override fun stop(bundleContext: BundleContext) {
        if (networkAMS != null) networkAMS!!.stop()
        Timber.d("Network Address Manager Service ...[STOPPED]")
        configurationService = null
    }

    companion object {
        /**
         * The OSGi bundle context.
         */
        private var bundleContext: BundleContext? = null

        /**
         * The configuration service.
         */
        private var configurationService: ConfigurationService? = null

        /**
         * Returns a reference to a ConfigurationService implementation currently
         * registered in the bundle context or null if no such implementation was found.
         *
         * @return a currently valid implementation of the ConfigurationService.
         */
        fun getConfigurationService(): ConfigurationService? {
            if (configurationService == null) {
                configurationService = ServiceUtils.getService(bundleContext, ConfigurationService::class.java)
            }
            return configurationService
        }

        /**
         * Returns a reference to the bundle context that we were started with.
         *
         * @return a reference to the BundleContext instance that we were started with.
         */
        fun getBundleContext(): BundleContext? {
            return bundleContext
        }
    }
}