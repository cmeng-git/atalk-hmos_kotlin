/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr

import net.java.otr4j.OtrPolicy
import net.java.sip.communicator.service.contactlist.MetaContactListService
import net.java.sip.communicator.service.gui.UIService
import net.java.sip.communicator.service.msghistory.MessageHistoryService
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.OperationSetInstantMessageTransform
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.resources.ResourceManagementServiceUtils
import net.java.sip.communicator.util.AbstractServiceDependentActivator
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.BundleContext
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.util.*

/**
 * cmeng:
 * The OtrActivator etc have been modified to be use in aTalk to support coexistence with OMEMO
 *
 * @author George Politis
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class OtrActivator : AbstractServiceDependentActivator(), ServiceListener {
    private var otrTransformLayer: OtrTransformLayer? = null

    /**
     * The dependent class. We are waiting for the ui service.
     *
     * @return the ui service class.
     */
    override val dependentServiceClass: Class<*>
        get() = UIService::class.java

    private fun handleProviderAdded(provider: ProtocolProviderService) {
        val opSetMessageTransform = provider.getOperationSet(OperationSetInstantMessageTransform::class.java)
        if (opSetMessageTransform != null) opSetMessageTransform.addTransformLayer(otrTransformLayer!!) else Timber.log(TimberLog.FINER, "Service did not have a transform op. set.")
    }

    private fun handleProviderRemoved(provider: ProtocolProviderService) {
        // check whether the provider has a basic im operation set
        provider.getOperationSet(OperationSetInstantMessageTransform::class.java)?.removeTransformLayer(otrTransformLayer!!)
    }

    /*
     * Implements ServiceListener#serviceChanged(ServiceEvent).
     */
    override fun serviceChanged(serviceEvent: ServiceEvent) {
        val sService = bundleContext.getService(serviceEvent.serviceReference)
        if (TimberLog.isTraceEnable) {
            Timber.log(TimberLog.FINER, "Received a service event for: %s", sService.javaClass.name)
        }

        // we don't care if the source service is not a protocol provider
        if (sService !is ProtocolProviderService) return
        Timber.d("Service is a protocol provider.")
        if (serviceEvent.type == ServiceEvent.REGISTERED) {
            Timber.d("Handling registration of a new Protocol Provider.")
            handleProviderAdded(sService)
        } else if (serviceEvent.type == ServiceEvent.UNREGISTERING) {
            handleProviderRemoved(sService)
        }
    }

    /**
     * The bundle context to use.
     *
     * @param context the context to set.
     */
    override fun setBundleContext(context: BundleContext) {
        bundleContext = context
    }

    /**
     * Implements AbstractServiceDependentActivator#start(UIService).
     *
     * @param dependentService the service this activator is waiting.
     * @see ChatSecuritySettings .onSharedPreferenceChanged
     */
    override fun start(dependentService: Any?) {
        // Init all public static references used by other OTR classes
        uiService = dependentService as UIService
        configService = ServiceUtils.getService(bundleContext, ConfigurationService::class.java)!!
        resourceService = ResourceManagementServiceUtils.getService(bundleContext)

        // Init OTR static variables, do not proceed without them.
        scOtrEngine = ScOtrEngineImpl()
        otrContactManager = OtrContactManager()
        otrTransformLayer = OtrTransformLayer()

        // Check whether someone has disabled this plug-in (valid for encode enable only).
        val otrPolicy = scOtrEngine.globalPolicy
        val isEnabled = !configService.getBoolean(OTR_DISABLED_PROP, false)
        otrPolicy!!.enableManual = isEnabled

        // Disable AUTO_INIT_OTR_PROP & OTR_MANDATORY_PROP for aTalk implementation (saved to DB)
        // may be removed in aTalk latest release once given sufficient time to user
        otrPolicy.enableAlways = false
        otrPolicy.requireEncryption = false
        scOtrEngine.globalPolicy = otrPolicy

        // Register Transformation Layer
        bundleContext.addServiceListener(this)
        bundleContext.addServiceListener(scOtrEngine)
        bundleContext.addServiceListener(otrContactManager)
        val protocolProviderRefs = ServiceUtils.getServiceReferences(bundleContext, ProtocolProviderService::class.java) as Array<ServiceReference<ProtocolProviderService>>?
        if (protocolProviderRefs != null && protocolProviderRefs.isNotEmpty()) {
            Timber.d("Found %d already installed providers.", protocolProviderRefs.size)
            for (protocolProviderRef in protocolProviderRefs) {
                val provider = bundleContext.getService(protocolProviderRef)
                handleProviderAdded(provider)
            }
        }
    }

    /*
     * Implements BundleActivator#stop(BundleContext).
     */
    @Throws(Exception::class)
    override fun stop(bc: BundleContext) {
        // Unregister transformation layer.
        // start listening for newly register or removed protocol providers
        bundleContext.removeServiceListener(this)
        if (scOtrEngine != null) bundleContext.removeServiceListener(scOtrEngine)
        if (otrContactManager != null) bundleContext.removeServiceListener(otrContactManager)
        val protocolProviderRefs = try {
            bundleContext.getServiceReferences(ProtocolProviderService::class.java.name, null)
        } catch (ex: InvalidSyntaxException) {
            Timber.e(ex, "Error while retrieving service refs")
            return
        }
        if (protocolProviderRefs != null && protocolProviderRefs.isNotEmpty()) {
            // in case we found any
            for (protocolProviderRef in protocolProviderRefs) {
                val provider = bundleContext.getService(protocolProviderRef as ServiceReference<Any>) as ProtocolProviderService
                handleProviderRemoved(provider)
            }
        }
    }

    companion object {
        /**
         * A property used in configuration to disable the OTR plugin.
         */
        const val OTR_DISABLED_PROP = "otr.DISABLED"

        /**
         * Indicates if the security/chat config form should be disabled, i.e. not visible to the user.
         */
        private const val OTR_CHAT_CONFIG_DISABLED_PROP = "otr.otrchatconfig.DISABLED"

        /**
         * The [BundleContext] of the [OtrActivator].
         */
        lateinit var bundleContext: BundleContext

        /**
         * The [ConfigurationService] of the [OtrActivator]. Can also be
         * obtained from the [OtrActivator.bundleContext] on demand, but we add it here for convenience.
         */
        lateinit var configService: ConfigurationService

        /**
         * The [ResourceManagementService] of the [OtrActivator]. Can also be obtained from the
         * [OtrActivator.bundleContext] on demand, but we add it here for convenience.
         */
        var resourceService: ResourceManagementService? = null

        /**
         * The [ScOtrEngine] of the [OtrActivator].
         */
        lateinit var scOtrEngine: ScOtrEngineImpl

        /**
         * The [ScOtrKeyManager] of the [OtrActivator].
         */
        var scOtrKeyManager = ScOtrKeyManagerImpl()

        /**
         * The [UIService] of the [OtrActivator]. Can also be obtained from the
         * [OtrActivator.bundleContext] on demand, but we add it here for convenience.
         */
        lateinit var uiService: UIService

        /**
         * The `MetaContactListService` reference.
         */
        private var metaCListService: MetaContactListService? = null
        /**
         * Gets the service giving access to message history.
         *
         * @return the service giving access to message history.
         */
        /**
         * The message history service.
         */
        var messageHistoryService: MessageHistoryService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, MessageHistoryService::class.java)
                }
                return field
            }
            private set

        /**
         * The [OtrContactManager] of the [OtrActivator].
         */
        private var otrContactManager: OtrContactManager? = null

        /**
         * Gets an [AccountID] by its UID.
         *
         * @param uid The [AccountID] UID.
         * @return The [AccountID] with the requested UID or null.
         */
        fun getAccountIDByUID(uid: String?): AccountID? {
            if (uid == null || uid.isEmpty()) return null
            val providerFactoriesMap = protocolProviderFactories
                    ?: return null
            for (providerFactory in providerFactoriesMap.values) {
                for (accountID in providerFactory.getRegisteredAccounts()) {
                    if (accountID.accountUniqueID == uid) return accountID
                }
            }
            return null
        }

        /**
         * Gets all the available accounts in SIP Communicator.
         *
         * @return a [List] of [AccountID].
         */
        val allAccountIDs: List<Any>?
            get() {
                val providerFactoriesMap = protocolProviderFactories
                        ?: return null
                val accountIDs = Vector<AccountID>()
                for (providerFactory in providerFactoriesMap.values) {
                    accountIDs.addAll(providerFactory.getRegisteredAccounts())
                }
                return accountIDs
            }

        private val protocolProviderFactories: MutableMap<Any, ProtocolProviderFactory>?
            get() {
                val serRefs = try {
                    bundleContext.getServiceReferences(ProtocolProviderFactory::class.java.name, null)
                } catch (ex: InvalidSyntaxException) {
                    Timber.e(ex, "Error while retrieving service refs")
                    return null
                }
                val providerFactoriesMap = Hashtable<Any, ProtocolProviderFactory>()
                if (serRefs != null) {
                    for (serRef in serRefs) {
                        val providerFactory = bundleContext.getService(serRef as ServiceReference<Any>) as ProtocolProviderFactory
                        providerFactoriesMap[serRef.getProperty("PROTOCOL_NAME")] = providerFactory
                    }
                }
                return providerFactoriesMap
            }

        /**
         * Returns the `MetaContactListService` obtained from the bundle context.
         *
         * @return the `MetaContactListService` obtained from the bundle context
         */
        val contactListService: MetaContactListService?
            get() {
                if (metaCListService == null) {
                    metaCListService = ServiceUtils.getService(bundleContext, MetaContactListService::class.java)
                }
                return metaCListService
            }
    }
}