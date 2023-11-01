/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.history

import net.java.sip.communicator.service.history.HistoryService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceRegistration

/**
 * Invoke "Service Binder" to parse the service XML and register all services.
 *
 * @author Alexander Pelov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
class HistoryActivator : BundleActivator {
    /**
     * The service registration.
     */
    private var serviceRegistration: ServiceRegistration<*>? = null

    /**
     * Initialize and start history service
     *
     * @param bundleContext
     * the `BundleContext`
     * @throws Exception
     * if initializing and starting history service fails
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        serviceRegistration = bundleContext.registerService(HistoryService::class.java,
                HistoryServiceImpl(bundleContext), null)
    }

    /**
     * Stops this bundle.
     *
     * @param bundleContext
     * the `BundleContext`
     * @throws Exception
     * if the stop operation goes wrong
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
        if (serviceRegistration != null) {
            serviceRegistration!!.unregister()
            serviceRegistration = null
        }
    }
}