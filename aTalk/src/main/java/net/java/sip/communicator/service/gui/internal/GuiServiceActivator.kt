/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.internal

import net.java.sip.communicator.util.ServiceUtils.getService
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext

/**
 * @author Lubomir Marinov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class GuiServiceActivator : BundleActivator {
    /**
     * Initialize and start GUI service
     *
     * @param bundleContext the `BundleContext`
     */
    override fun start(bundleContext: BundleContext) {
        Companion.bundleContext = bundleContext
    }

    /**
     * Stops this bundle.
     *
     * @param bundleContext the `BundleContext`
     */
    override fun stop(bundleContext: BundleContext) {
        if (Companion.bundleContext === bundleContext) Companion.bundleContext = null
    }

    companion object {
        /**
         * Returns the `BundleContext`.
         *
         * @return bundle context
         */
        /**
         * The `BundleContext` of the service.
         */
        var bundleContext: BundleContext? = null
            private set

        /**
         * The `ResourceManagementService`, which gives access to application resources.
         */
        private var resourceService: ResourceManagementService? = null

        /**
         * Returns the `ResourceManagementService`, through which we will access all resources.
         *
         * @return the `ResourceManagementService`, through which we will access all resources.
         */
        val resources: ResourceManagementService?
            get() {
                if (resourceService == null) {
                    resourceService = getService(bundleContext, ResourceManagementService::class.java)
                }
                return resourceService
            }
    }
}