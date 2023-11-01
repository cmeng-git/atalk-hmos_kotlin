/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.appupdate

import net.java.sip.communicator.service.update.UpdateService
import net.java.sip.communicator.util.ServiceUtils.getService
import net.java.sip.communicator.util.SimpleServiceActivator
import org.atalk.service.configuration.ConfigurationService
import org.osgi.framework.BundleContext

/**
 * Android update service activator.
 *
 * @author Pawel Domas
 */
class UpdateActivator
/**
 * Creates new instance of `UpdateActivator`.
 */
    : SimpleServiceActivator<UpdateService?>(UpdateService::class.java, "Android update service") {
    /**
     * {@inheritDoc}
     */
    override fun createServiceImpl(): UpdateService {
        return UpdateServiceImpl()
    }

    /**
     * {@inheritDoc}
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        Companion.bundleContext = bundleContext
        super.start(bundleContext)
        (serviceImpl as UpdateServiceImpl).removeOldDownloads()
    }

    companion object {
        /**
         * `BundleContext` instance.
         */
        var bundleContext: BundleContext? = null

        /**
         * Gets the `ConfigurationService` using current `BundleContext`.
         *
         * @return the `ConfigurationService`
         */
        val configuration: ConfigurationService?
            get() = getService(bundleContext, ConfigurationService::class.java)
    }
}