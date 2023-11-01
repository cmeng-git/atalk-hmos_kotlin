/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.metahistory

import net.java.sip.communicator.service.metahistory.MetaHistoryService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import timber.log.Timber

/**
 * Activates the MetaHistoryService
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class MetaHistoryActivator : BundleActivator {
    /**
     * The `MetaHistoryService` reference.
     */
    private var metaHistoryService: MetaHistoryServiceImpl? = null

    /**
     * Initialize and start meta history
     *
     * @param bundleContext BundleContext
     * @throws Exception if initializing and starting meta history service fails
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        // Create and start the meta history service.
        metaHistoryService = MetaHistoryServiceImpl()
        metaHistoryService!!.start(bundleContext)
        bundleContext.registerService(MetaHistoryService::class.java.name, metaHistoryService, null)
        Timber.i("Meta History Service ...[REGISTERED]")
    }

    /**
     * Stops this bundle.
     *
     * @param bundleContext the `BundleContext`
     * @throws Exception if the stop operation goes wrong
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
        if (metaHistoryService != null) metaHistoryService!!.stop(bundleContext)
    }
}