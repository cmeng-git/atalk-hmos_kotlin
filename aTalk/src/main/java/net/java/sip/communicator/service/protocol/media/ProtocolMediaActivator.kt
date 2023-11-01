/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media

import net.java.sip.communicator.service.netaddr.NetworkAddressManagerService
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.neomedia.MediaService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import timber.log.Timber

/**
 * The activator doesn't really start anything as this service is mostly stateless, it's simply here
 * to allow us to obtain references to the services that we may need.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class ProtocolMediaActivator : BundleActivator {
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
        Timber.i("Protocol Media Started.")
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
        configurationService = null
        mediaService = null
        networkAddressManagerService = null
    }

    companion object {
        /**
         * a reference to the bundle context that we were started with.
         */
        var bundleContext: BundleContext? = null

        /**
         * A reference to a ConfigurationService implementation currently registered in the
         * bundle context or null if no such implementation was found.
         */
        var configurationService: ConfigurationService? = null
            get() {
                if (field == null) {
                    ServiceUtils.getService(bundleContext, ConfigurationService::class.java).also { field = it }
                }
                return field
            }

        /**
         * Returns a reference to a `MediaService` implementation currently registered in the
         * bundle context or null if no such implementation was found.
         *
         * @return a reference to a `MediaService` implementation currently registered in the
         * bundle context or null if no such implementation was found.
         */
        @JvmStatic
        var mediaService: MediaService? = null
            get() {
                if (field == null) {
                    ServiceUtils.getService(bundleContext, MediaService::class.java).also { field = it }
                }
                return field
            }

        /**
         * Areference to a NetworkAddressManagerService implementation currently registered in
         * the bundle context or null if no such implementation was found.
         *
         * @return a currently valid implementation of the `NetworkAddressManagerService`
         */
        var networkAddressManagerService: NetworkAddressManagerService? = null
            get() {
                if (field == null) {
                    ServiceUtils.getService(bundleContext, NetworkAddressManagerService::class.java).also { field = it }
                }
                return field
            }
    }
}