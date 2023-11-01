/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.impl.muc

import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService
import net.java.sip.communicator.service.globaldisplaydetails.GlobalDisplayDetailsService
import net.java.sip.communicator.service.gui.AlertUIService
import net.java.sip.communicator.service.gui.UIService
import net.java.sip.communicator.service.msghistory.MessageHistoryService
import net.java.sip.communicator.service.muc.MUCService
import net.java.sip.communicator.service.protocol.AccountManager
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.util.ServiceUtils.getService
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.Bundle
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceReference
import java.util.*

/**
 * The activator for the chat room contact source bundle.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class MUCActivator : BundleActivator {
    /**
     * Starts this bundle.
     *
     *Collection<EventObject?> context the bundle context where we register and obtain services.
     */
    @Throws(Exception::class)
    override fun start(context: BundleContext) {
        bundleContext = context
        if (configurationService!!.getBoolean(DISABLED_PROPERTY, false)) return
        bundleContext!!.registerService(ContactSourceService::class.java.name, contactSource, null)
        mucService = MUCServiceImpl()
        bundleContext!!.registerService(MUCService::class.java.name, mucService, null)
    }

    @Throws(Exception::class)
    override fun stop(context: BundleContext) {
        if (protocolProviderRegListener != null) {
            bundleContext!!.removeServiceListener(protocolProviderRegListener)
        }
        if (chatRoomProviders != null) chatRoomProviders!!.clear()
    }

    /**
     * Listens for `ProtocolProviderService` registrations.
     */
    private class ProtocolProviderRegListener : ServiceListener {
        /**
         * Handles service change events.
         */
        override fun serviceChanged(event: ServiceEvent) {
            val serviceRef = event.serviceReference

            // if the event is caused by a bundle being stopped, we don't want to know
            if (serviceRef.bundle.state == Bundle.STOPPING) {
                return
            }
            val service = bundleContext!!.getService(serviceRef) as? ProtocolProviderService
                    ?: return

            // we don't care if the source service is not a protocol provider
            when (event.type) {
                ServiceEvent.REGISTERED -> handleProviderAdded(service)
                ServiceEvent.UNREGISTERING -> handleProviderRemoved(service)
            }
        }
    }

    companion object {
        /**
         * The configuration property to disable
         */
        private const val DISABLED_PROPERTY = "muc.MUCSERVICE_DISABLED"

        /**
         * The bundle context.
         */
        var bundleContext: BundleContext? = null

        /**
         * The configuration service.
         */
        private var configService: ConfigurationService? = null

        /**
         * Providers of contact info.
         */
        private var chatRoomProviders: MutableList<ProtocolProviderService>? = null
        /**
         * Returns the chat room contact source.
         *
         * @return the chat room contact source
         */
        /**
         * The chat room contact source.
         */
        val contactSource = ChatRoomContactSourceService()
        /**
         * Returns a reference to the ResourceManagementService implementation currently registered in
         * the bundle context or null if no such implementation was found.
         *
         * @return a reference to a ResourceManagementService implementation currently registered in
         * the bundle context or null if no such implementation was found.
         */
        /**
         * The resource service.
         */
        var resources: ResourceManagementService? = null
            get() {
                if (field == null) {
                    field = getService(bundleContext, ResourceManagementService::class.java)
                }
                return field
            }
            private set
        /**
         * Returns the MUC service instance.
         *
         * @return the MUC service instance.
         */
        /**
         * The MUC service.
         */
        lateinit var mucService: MUCServiceImpl
            private set

        /**
         * Returns the `AccountManager` obtained from the bundle context.
         *
         * @return the `AccountManager` obtained from the bundle context
         */
        /**
         * The account manager.
         */
        var accountManager: AccountManager? = null
            get() {
                if (field == null) {
                    field = getService(bundleContext, AccountManager::class.java)
                }
                return field
            }
            private set
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
         * The credential storage service.
         */
        private var credentialsService: CredentialsStorageService? = null

        /**
         * The UI service.
         */
        private var uiService: UIService? = null

        /**
         * Listens for ProtocolProviderService registrations.
         */
        private var protocolProviderRegListener: ProtocolProviderRegListener? = null
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
                if (field == null) field = getService(bundleContext, MessageHistoryService::class.java)
                return field
            }
            private set
        /**
         * Returns the `GlobalDisplayDetailsService` obtained from the bundle context.
         *
         * @return the `GlobalDisplayDetailsService` obtained from the bundle context
         */
        /**
         * The global display details service instance.
         */
        var globalDisplayDetailsService: GlobalDisplayDetailsService? = null
            get() {
                if (field == null) {
                    field = getService(bundleContext, GlobalDisplayDetailsService::class.java)
                }
                return field
            }
            private set

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
         * Returns a reference to a CredentialsStorageService implementation currently registered in
         * the bundle context or null if no such implementation was found.
         *
         * @return a currently valid implementation of the CredentialsStorageService.
         */
        val credentialsStorageService: CredentialsStorageService?
            get() {
                if (credentialsService == null) {
                    credentialsService = getService(bundleContext, CredentialsStorageService::class.java)
                }
                return credentialsService
            }

        /**
         * Returns a list of all currently registered providers.
         *
         * @return a list of all currently registered providers
         */
        fun getChatRoomProviders(): List<ProtocolProviderService>? {
            if (chatRoomProviders != null) return chatRoomProviders
            chatRoomProviders = LinkedList()
            protocolProviderRegListener = ProtocolProviderRegListener()
            bundleContext!!.addServiceListener(protocolProviderRegListener)
            var serRefs: Array<ServiceReference<*>?>? = null
            try {
                serRefs = bundleContext!!.getServiceReferences(ProtocolProviderFactory::class.java.name, null)
            } catch (e: InvalidSyntaxException) {
                e.printStackTrace()
            }
            if (serRefs != null && serRefs.isNotEmpty()) {
                for (ppfSerRef in serRefs) {
                    val providerFactory = bundleContext!!.getService<ProtocolProviderFactory>(ppfSerRef as ServiceReference<ProtocolProviderFactory>)
                    for (accountID in providerFactory.getRegisteredAccounts()) {
                        val ppsSerRef = providerFactory.getProviderForAccount(accountID)
                        val protocolProvider = bundleContext!!.getService(ppsSerRef)
                        handleProviderAdded(protocolProvider)
                    }
                }
            }
            return chatRoomProviders
        }

        /**
         * Handles the registration of a new `ProtocolProviderService`. Adds the given
         * `protocolProvider` to the list of queried providers.
         *
         *Collection<EventObject?> protocolProvider the `ProtocolProviderService` to add
         */
        private fun handleProviderAdded(protocolProvider: ProtocolProviderService) {
            if (protocolProvider.getOperationSet(OperationSetMultiUserChat::class.java) != null
                    && !chatRoomProviders!!.contains(protocolProvider)) {
                chatRoomProviders!!.add(protocolProvider)
            }
        }

        /**
         * Handles the un-registration of a `ProtocolProviderService`. Removes the given
         * `protocolProvider` from the list of queried providers.
         *
         *Collection<EventObject?> protocolProvider the `ProtocolProviderService` to remove
         */
        private fun handleProviderRemoved(protocolProvider: ProtocolProviderService) {
            chatRoomProviders!!.remove(protocolProvider)
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