/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidversion

import net.java.sip.communicator.util.SimpleServiceActivator
import org.atalk.service.version.VersionService
import org.osgi.framework.BundleContext

/**
 * Android version service activator.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class VersionActivator
/**
 * Creates a new instance of `VersionActivator`.
 */
    : SimpleServiceActivator<VersionService?>(VersionService::class.java, "Android version") {
    /**
     * {@inheritDoc}
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        Companion.bundleContext = bundleContext
        super.start(bundleContext)
    }

    /**
     * {@inheritDoc}
     */
    override fun createServiceImpl(): VersionService {
        return VersionServiceImpl()
    }

    companion object {
        /**
         * `BundleContext` instance.
         */
        var bundleContext: BundleContext? = null
    }
}