/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.impl.callhistory

import net.java.sip.communicator.service.callhistory.CallHistoryService
import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.history.HistoryService
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.util.ServiceUtils.getService
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.util.*

/**
 * Activates the `CallHistoryService`.
 *
 * @author Damian Minkov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class CallHistoryActivator : BundleActivator {
    /**
     * Initialize and start call history
     *
     * @param bc the `BundleContext`
     * @throws Exception if initializing and starting call history fails
     */
    @Throws(Exception::class)
    override fun start(bc: BundleContext) {
        bundleContext = bc
        val historyService = getService(bundleContext, HistoryService::class.java)

        // Create and start the call history service.
        callHistoryService = CallHistoryServiceImpl()
        // set the configuration and history service
        callHistoryService!!.setHistoryService(historyService)
        callHistoryService!!.start(bundleContext)
        bundleContext!!.registerService(CallHistoryService::class.java.name, callHistoryService, null)
        bundleContext!!.registerService(ContactSourceService::class.java.name, CallHistoryContactSource(), null)
        Timber.d("Call History Service ...[REGISTERED]")
    }

    /**
     * Stops this bundle.
     *
     * @param bundleContext the `BundleContext`
     * @throws Exception if the stop operation goes wrong
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
        if (callHistoryService != null) callHistoryService!!.stop(bundleContext)
    }

    companion object {
        /**
         * The bundle context.
         */
        var bundleContext: BundleContext? = null

        /**
         * The `CallHistoryServiceImpl` instantiated in the start method of this bundle.
         */
        private var callHistoryService: CallHistoryServiceImpl? = null

        /**
         * The service responsible for resources.
         */
        private var resourcesService: ResourceManagementService? = null

        /**
         * The map containing all registered
         */
        private val providerFactoriesMap = Hashtable<Any, ProtocolProviderFactory>()

        /**
         * Returns the instance of `CallHistoryService` created in this activator.
         *
         * @return the instance of `CallHistoryService` created in this activator
         */
        fun getCallHistoryService(): CallHistoryService? {
            return callHistoryService
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
         * Returns all `ProtocolProviderFactory`s obtained from the bundle context.
         *
         * @return all `ProtocolProviderFactory`s obtained from the bundle context
         */
        val protocolProviderFactories: Map<Any?, ProtocolProviderFactory?>?
            get() {
                var serRefs: Array<ServiceReference<*>>? = null
                serRefs = try {
                    bundleContext!!.getServiceReferences(ProtocolProviderFactory::class.java.name, null)
                } catch (e: InvalidSyntaxException) {
                    Timber.e(e, "Error while retrieving service refs")
                    return null
                }
                val providerFactoriesMap = Hashtable<Any?, ProtocolProviderFactory?>()
                if (serRefs!!.isNotEmpty()) {
                    for (serRef in serRefs) {
                        @Suppress("UNCHECKED_CAST")
                        val providerFactory = bundleContext!!.getService(serRef as ServiceReference<ProtocolProviderFactory>)
                        providerFactoriesMap[serRef.getProperty(ProtocolProviderFactory.PROTOCOL)] = providerFactory
                    }
                }
                return providerFactoriesMap
            }
    }
}