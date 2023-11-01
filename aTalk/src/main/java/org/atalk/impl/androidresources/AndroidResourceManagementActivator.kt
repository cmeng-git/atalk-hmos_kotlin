/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidresources

import net.java.sip.communicator.util.SimpleServiceActivator
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.BundleContext

/**
 * Starts Android resource management service.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AndroidResourceManagementActivator : SimpleServiceActivator<AndroidResourceServiceImpl?>(ResourceManagementService::class.java, "Android Resource Manager") {
    /**
     * Starts this bundle.
     *
     * @param bundleContext
     * the OSGi bundle context
     * @throws Exception
     * if something goes wrong on start up
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        Companion.bundleContext = bundleContext
        super.start(bundleContext)
    }

    /**
     * Stops this bundle.
     *
     * @param bundleContext
     * the bundle context
     * @throws Exception
     * if something goes wrong on stop
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
        bundleContext.removeServiceListener(serviceImpl)
    }

    /**
     * {@inheritDoc}
     */
    override fun createServiceImpl(): AndroidResourceServiceImpl {
        return AndroidResourceServiceImpl()
    }

    companion object {
        /**
         * The osgi bundle context.
         */
        lateinit var bundleContext: BundleContext
    }
}