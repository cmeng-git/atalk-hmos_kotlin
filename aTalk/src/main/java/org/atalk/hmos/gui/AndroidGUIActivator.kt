/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui

import net.java.sip.communicator.service.contactlist.MetaContactListService
import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.contactsource.DemuxContactSourceService
import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService
import net.java.sip.communicator.service.filehistory.FileHistoryService
import net.java.sip.communicator.service.globaldisplaydetails.GlobalDisplayDetailsService
import net.java.sip.communicator.service.gui.UIService
import net.java.sip.communicator.service.metahistory.MetaHistoryService
import net.java.sip.communicator.service.msghistory.MessageHistoryService
import net.java.sip.communicator.service.protocol.AccountManager
import net.java.sip.communicator.service.protocol.PhoneNumberI18nService
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService
import net.java.sip.communicator.service.systray.SystrayService
import net.java.sip.communicator.util.ConfigurationUtils
import net.java.sip.communicator.util.ServiceUtils
import net.java.sip.communicator.util.account.LoginManager
import org.atalk.crypto.CryptoFragment
import org.atalk.hmos.gui.account.AndroidLoginRenderer
import org.atalk.hmos.gui.chat.ChatSessionManager
import org.atalk.hmos.gui.login.AndroidSecurityAuthority
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.neomedia.MediaService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import java.util.*

/**
 * Creates `LoginManager` and registers `AlertUIService`. It's moved here from
 * launcher `Activity` because it could be created multiple times and result in multiple
 * objects/registrations for those services. It also guarantees that they wil be registered
 * each time OSGI service starts.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AndroidGUIActivator : BundleActivator {
    /**
     * The presence status handler
     */
    private var presenceStatusHandler: PresenceStatusHandler? = null

    /**
     * Called when this bundle is started.
     *
     * @param bundleContext The execution context of the bundle being started.
     * @throws Exception if the bundle is not correctly started
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        Companion.bundleContext = bundleContext

        // Registers UIService stub
        val securityAuthority = AndroidSecurityAuthority()
        uIService = AndroidUIServiceImpl(securityAuthority)
        bundleContext.registerService(UIService::class.java.name, uIService, null)

        // Creates and registers presence status handler
        presenceStatusHandler = PresenceStatusHandler()
        presenceStatusHandler!!.start(bundleContext)
        loginRenderer = AndroidLoginRenderer(securityAuthority)
        loginManager = LoginManager(loginRenderer!!)
        val accountManager = ServiceUtils.getService(bundleContext, AccountManager::class.java)
        if (accountManager != null) {
            val storedAccounts = accountManager.storedAccounts
            if (storedAccounts.isNotEmpty()) {
                Thread { loginManager.runLogin() }.start()
            }
        }
        ConfigurationUtils.loadGuiConfigurations()

        // Register show history settings OTR link listener
        ChatSessionManager.addChatLinkListener(CryptoFragment.ShowHistoryLinkListener())
    }

    /**
     * Called when this bundle is stopped so the Framework can perform the bundle-specific
     * activities necessary to stop the bundle.
     *
     * @param bundleContext The execution context of the bundle being stopped.
     * @throws Exception If this method throws an exception, the bundle is still marked as stopped, and the
     * Framework will remove the bundle's listeners, unregister all services registered by
     * the bundle, and release all services used by the bundle.
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
        presenceStatusHandler!!.stop(bundleContext)

        // Clears chat sessions
        ChatSessionManager.dispose()
        // loginRenderer = null
        // loginManager = null
        configService = null
        globalDisplayService = null
        metaCListService = null
        Companion.bundleContext = null
    }

    companion object {
        /**
         * The `LoginManager` for Android application.
         */
        lateinit var loginManager: LoginManager
            private set

        /**
         * Returns a reference to the UIService implementation currently registered in the bundle
         * context or null if no such implementation was found.
         */
        lateinit var uIService: AndroidUIServiceImpl
            private set

        /**
         * The OSGI bundle context.
         */
        var bundleContext: BundleContext? = null

        /**
         * Android login renderer impl.
         */
        var loginRenderer: AndroidLoginRenderer? = null
            private set

        /**
         * Configuration service instance.
         */
        private var configService: ConfigurationService? = null

        /**
         * Returns the `MetaHistoryService` obtained from the bundle context.
         */
        var metaHistoryService: MetaHistoryService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, MetaHistoryService::class.java)
                }
                return field
            }
            private set

        /**
         * `MetaContactListService` cached instance.
         */
        private var metaCListService: MetaContactListService? = null

        /**
         * The `SystrayService` instance.
         */
        var systrayService: SystrayService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, SystrayService::class.java)
                }
                return field
            }
            private set

        /**
         * An instance of the `MediaService` obtained from the bundle context.
         */
        var mediaService: MediaService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, MediaService::class.java)
                }
                return field
            }
            private set

        /**
         * The `GlobalStatusService` obtained from the bundle context.
         */
        var globalStatusService: GlobalStatusService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, GlobalStatusService::class.java)
                }
                return field
            }
            private set

        /**
         * The `DemuxContactSourceService` obtained from the bundle context.
         */
        var demuxContactSourceService: DemuxContactSourceService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, DemuxContactSourceService::class.java)
                }
                return field
            }
            private set

        /**
         * `GlobalDisplayDetailsService` instance.
         */
        private var globalDisplayService: GlobalDisplayDetailsService? = null
        private var credentialsService: CredentialsStorageService? = null

        /**
         * The service giving access to message history.
         */
        var messageHistoryService: MessageHistoryService? = null
            get() {
                if (field == null) field = ServiceUtils.getService(bundleContext, MessageHistoryService::class.java)
                return field
            }
            private set

        /**
         * The service giving access to message history.
         */
        var fileHistoryService: FileHistoryService? = null
            get() {
                if (field == null) field = ServiceUtils.getService(bundleContext, FileHistoryService::class.java)
                return field
            }
            private set

        /**
         * The registered PhoneNumberI18nService.
         */
        var phoneNumberI18nService: PhoneNumberI18nService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, PhoneNumberI18nService::class.java)
                }
                return field
            }
            private set

        /**
         * The `ConfigurationService`.
         */
        val configurationService: ConfigurationService
            get() {
                if (configService == null) {
                    configService = ServiceUtils.getService(bundleContext, ConfigurationService::class.java)
                }
                return configService!!
            }

        /**
         * The `MetaContactListService`.
         */
        val contactListService: MetaContactListService
            get() {
                if (metaCListService == null) {
                    metaCListService = ServiceUtils.getService(bundleContext, MetaContactListService::class.java)
                }
                return metaCListService!!
            }

        /**
         * The `GlobalDisplayDetailsService` obtained from the bundle context.
         */
        val globalDisplayDetailsService: GlobalDisplayDetailsService?
            get() {
                if (globalDisplayService == null) {
                    globalDisplayService = ServiceUtils.getService(bundleContext, GlobalDisplayDetailsService::class.java)
                }
                return globalDisplayService
            }

        /**
         * A list of all registered contact sources.
         */
        val contactSources: List<ContactSourceService>
            get() {
                val contactSources = Vector<ContactSourceService>()
                val serRefs = ServiceUtils.getServiceReferences(bundleContext!!, ContactSourceService::class.java)
                for (serRef in serRefs) {
                    val contactSource = bundleContext!!.getService<Any>(serRef as ServiceReference<Any>) as ContactSourceService
                    contactSources.add(contactSource)
                }
                return contactSources
            }

        /**
         * A reference to a CredentialsStorageService implementation currently registered in
         * the bundle context or null if no such implementation was found.
         */
        val credentialsStorageService: CredentialsStorageService?
            get() {
                if (credentialsService == null) {
                    credentialsService = ServiceUtils.getService(bundleContext, CredentialsStorageService::class.java)
                }
                return credentialsService
            }
    }
}