/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util

import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import timber.log.Timber

/**
 * Base class for activators which only register new service in bundle context.
 * Service registration activity is logged on `Debug` level.
 *
 * @param <T> service implementation template type (for convenient instance access)
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
</T> */
abstract class SimpleServiceActivator<T>
/**
 * Creates new instance of `SimpleServiceActivator`
 *
 * @param serviceClass class of service that will be registered on bundle startup
 * @param serviceName service name that wil be used in log messages
 */
(
        /**
         * Class of the service
         */
        private val serviceClass: Class<*>,
        /**
         * Service name that will be used in log messages
         */
        private val serviceName: String) : BundleActivator {
    /**
     * Instance of service implementation
     */
    protected var serviceImpl: T? = null

    /**
     * Initialize and start the service.
     *
     * @param bundleContext the `BundleContext`
     * @throws Exception if initializing and starting this service fails
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        serviceImpl = createServiceImpl()
        bundleContext.registerService(serviceClass.name, serviceImpl, null)
        Timber.i("%s REGISTERED", serviceName)
    }

    /**
     * Stops this bundle.
     *
     * @param bundleContext the `BundleContext`
     * @throws @Exception if the stop operation goes wrong
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
    }

    /**
     * Called on bundle startup in order to create service implementation instance.
     *
     * @return should return new instance of service implementation.
     */
    protected abstract fun createServiceImpl(): T
}