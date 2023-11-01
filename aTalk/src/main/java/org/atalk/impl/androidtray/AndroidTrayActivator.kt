/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidtray

import net.java.sip.communicator.service.systray.SystrayService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import timber.log.Timber

/**
 * Android tray service activator.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AndroidTrayActivator : BundleActivator {
    /**
     * `SystrayServiceImpl` instance.
     */
    private var systrayService: SystrayServiceImpl? = null

    /**
     * {@inheritDoc}
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        Companion.bundleContext = bundleContext

        // Create the notification service implementation
        systrayService = SystrayServiceImpl()
        bundleContext.registerService(SystrayService::class.java.name, systrayService, null)
        systrayService!!.start()
        Timber.i("Systray Service ...[REGISTERED]")
    }

    /**
     * {@inheritDoc}
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
        systrayService!!.stop()
    }

    companion object {
        /**
         * OSGI bundle context
         */
        var bundleContext: BundleContext? = null
    }
}