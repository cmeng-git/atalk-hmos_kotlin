/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.filehistory

import net.java.sip.communicator.service.filehistory.FileHistoryService
import net.java.sip.communicator.service.history.HistoryService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import timber.log.Timber

/**
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class FileHistoryActivator : BundleActivator {
    /**
     * A `FileHistoryService` service reference.
     */
    private var fileHistoryService: FileHistoryServiceImpl? = null

    /**
     * Initialize and start file history
     *
     * @param bundleContext BundleContext
     * @throws Exception if initializing and starting file history fails
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        val refHistory = bundleContext.getServiceReference(HistoryService::class.java.name) as ServiceReference<HistoryService>
        val historyService = bundleContext.getService(refHistory)

        // Create and start the file history service.
        fileHistoryService = FileHistoryServiceImpl()
        // set the history service
        fileHistoryService!!.setHistoryService(historyService)
        fileHistoryService!!.start(bundleContext)
        bundleContext.registerService(FileHistoryService::class.java.name, fileHistoryService, null)
        Timber.d("File History Service ...[REGISTERED]")
    }

    /**
     * Stops this bundle.
     *
     * @param bundleContext the `BundleContext`
     * @throws Exception if the stop operation goes wrong
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
        if (fileHistoryService != null) fileHistoryService!!.stop(bundleContext)
    }
}