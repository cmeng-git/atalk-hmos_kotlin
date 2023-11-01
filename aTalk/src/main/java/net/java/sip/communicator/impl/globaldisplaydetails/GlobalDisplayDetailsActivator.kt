/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.globaldisplaydetails

import net.java.sip.communicator.service.globaldisplaydetails.GlobalDisplayDetailsService
import net.java.sip.communicator.service.gui.AlertUIService
import net.java.sip.communicator.service.gui.UIService
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService
import net.java.sip.communicator.util.ServiceUtils.getService
import net.java.sip.communicator.util.UtilActivator
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.Bundle
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceReference

/**
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class GlobalDisplayDetailsActivator : BundleActivator, ServiceListener {
    /**
     * Initialize and start file service
     *
     * @param bc
     * the `BundleContext`
     * @throws Exception
     * if initializing and starting file service fails
     */
    @Throws(Exception::class)
    override fun start(bc: BundleContext) {
        bundleContext = bc
        displayDetailsImpl = GlobalDisplayDetailsImpl()
        globalStatusService = GlobalStatusServiceImpl()
        bundleContext!!.addServiceListener(this)
        handleAlreadyRegisteredProviders()
        bundleContext!!.registerService(GlobalDisplayDetailsService::class.java.name, displayDetailsImpl, null)
        bundleContext!!.registerService(GlobalStatusService::class.java.name, globalStatusService, null)
    }

    /**
     * Searches and processes already registered providers.
     */
    private fun handleAlreadyRegisteredProviders() {
        bundleContext!!.addServiceListener(this as ServiceListener)
        var ppsRefs: Array<ServiceReference<ProtocolProviderService>>? = null
        try {
            ppsRefs = bundleContext!!.getServiceReferences(ProtocolProviderService::class.java.name, null) as Array<ServiceReference<ProtocolProviderService>>?
        } catch (e: InvalidSyntaxException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
        if (ppsRefs!!.isNotEmpty()) {
            for (ppsRef in ppsRefs) {
                val pps = bundleContext!!.getService(ppsRef)
                handleProviderAdded(pps)
            }
        }
    }

    /**
     * Used to attach the listeners to existing or just registered protocol provider.
     *
     * @param pps ProtocolProviderService
     */
    private fun handleProviderAdded(pps: ProtocolProviderService) {
        pps.addRegistrationStateChangeListener(displayDetailsImpl)
        globalStatusService!!.handleProviderAdded(pps)
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
    }

    /**
     * Implements the `ServiceListener` method. Verifies whether the passed event concerns a `ProtocolProviderService` and
     * adds or removes a registration listener.
     *
     * @param event
     * The `ServiceEvent` object.
     */
    override fun serviceChanged(event: ServiceEvent) {
        val serviceRef = event.serviceReference

        // if the event is caused by a bundle being stopped, we don't want to know
        if (serviceRef.bundle.state == Bundle.STOPPING) {
            return
        }

        // we don't care if the source service is not a protocol provider
        val service = UtilActivator.bundleContext!!.getService(serviceRef) as? ProtocolProviderService
                ?: return

        when (event.type) {
            ServiceEvent.REGISTERED -> handleProviderAdded(service)
            ServiceEvent.UNREGISTERING -> {
                service.removeRegistrationStateChangeListener(displayDetailsImpl!!)
                globalStatusService!!.handleProviderRemoved(service)
            }
        }
    }

    companion object {
        /**
         * The bundle context.
         */
        private var bundleContext: BundleContext? = null

        /**
         * The service giving access to image and string application resources.
         */
        private var resourcesService: ResourceManagementService? = null

        /**
         * The service giving access to the configuration resources.
         */
        private var configService: ConfigurationService? = null
        /**
         * Returns the `AlertUIService` obtained from the bundle context.
         *
         * @return the `AlertUIService` obtained from the bundle context
         */
        /**
         * The alert UI service.
         */
        var alertUIService: AlertUIService? = null
            get() {
                if (field == null) {
                    field = getService(bundleContext, AlertUIService::class.java)
                }
                return field
            }
            private set

        /**
         * The UI service.
         */
        private var uiService: UIService? = null

        /**
         * The display details implementation.
         */
        var displayDetailsImpl: GlobalDisplayDetailsImpl? = null
        var globalStatusService: GlobalStatusServiceImpl? = null

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

        /**
         * Returns the `UIService` obtained from the bundle context.
         *
         * @return the `UIService` obtained from the bundle context
         */
        val uIService: UIService?
            get() {
                if (uiService == null) {
                    uiService = getService(bundleContext, UIService::class.java)
                }
                return uiService
            }
    }
}