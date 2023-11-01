/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.msghistory

import net.java.sip.communicator.service.contactlist.MetaContactListService
import net.java.sip.communicator.service.history.HistoryService
import net.java.sip.communicator.service.msghistory.MessageHistoryService
import net.java.sip.communicator.util.ServiceUtils.getService
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import timber.log.Timber

/**
 * Activates the MessageHistoryService
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class MessageHistoryActivator : BundleActivator {
    /**
     * Initialize and start message history
     *
     * @param bc the BundleContext
     * @throws Exception if initializing and starting message history service fails
     */
    @Throws(Exception::class)
    override fun start(bc: BundleContext) {
        bundleContext = bc
        val refHistory = bundleContext.getServiceReference(HistoryService::class.java.name)
        val historyService = bundleContext.getService<Any>(refHistory as ServiceReference<Any>) as HistoryService

        // Create and start the message history service.
        messageHistoryService = MessageHistoryServiceImpl()
        messageHistoryService.setHistoryService(historyService)
        messageHistoryService.start(bundleContext)
        bundleContext.registerService(MessageHistoryService::class.java.name, messageHistoryService, null)
        Timber.i("Message History Service ...[REGISTERED]")
    }

    /**
     * Stops this bundle.
     *
     * @param bundleContext the `BundleContext`
     * @throws Exception if the stop operation goes wrong
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
        messageHistoryService.stop(bundleContext)
    }

    companion object {
        /**
         * The `BundleContext` of the service.
         */
        lateinit var bundleContext: BundleContext

        /**
         * Returns the `MessageHistoryService` registered to the bundle context.
         *
         * @return the `MessageHistoryService` registered to the bundle context
         */
        /**
         * The `MessageHistoryService` reference.
         */
        lateinit var messageHistoryService: MessageHistoryServiceImpl
            private set

        /**
         * The `ResourceManagementService` reference.
         */
        private var resourcesService: ResourceManagementService? = null

        /**
         * The `MetaContactListService` reference.
         */
        private var metaCListService: MetaContactListService? = null

        /**
         * The `ConfigurationService` reference.
         */
        private var configService: ConfigurationService? = null

        /**
         * Returns the `MetaContactListService` obtained from the bundle context.
         *
         * @return the `MetaContactListService` obtained from the bundle context
         */
        val contactListService: MetaContactListService?
            get() {
                if (metaCListService == null) {
                    metaCListService = getService(bundleContext, MetaContactListService::class.java)
                }
                return metaCListService
            }

        /**
         * Returns the `ResourceManagementService`, through which we will access all resources.
         *
         * @return the `ResourceManagementService`, through which we will access all resources.
         */
        val resources: ResourceManagementService?
            get() {
                if (resourcesService == null) {
                    resourcesService = getService(bundleContext, ResourceManagementService::class.java)
                }
                return resourcesService
            }

        /**
         * Returns the `ConfigurationService` obtained from the bundle context.
         *
         * @return the `ConfigurationService` obtained from the bundle context
         */
        val configurationService: ConfigurationService?
            get() {
                if (configService == null) {
                    configService = getService(bundleContext, ConfigurationService::class.java)
                }
                return configService
            }
    }
}