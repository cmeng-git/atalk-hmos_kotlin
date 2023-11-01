/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.AccountManagerEvent
import net.java.sip.communicator.service.protocol.event.AccountManagerListener
import okhttp3.internal.notify
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.BundleException
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceReference

/**
 * Provides utilities to aid the manipulation of [AccountManager].
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
object AccountManagerUtils {
    private fun getAccountManager(bundleContext: BundleContext): AccountManager {
        return bundleContext.getService(bundleContext.getServiceReference(AccountManager::class.java))
    }

    /**
     * Starts a specific `Bundle` and waits for the `AccountManager` available
     * in a specific `BundleContext` to load the stored accounts of a
     * `ProtocolProviderFactory` with a specific protocol name.
     *
     * @param bundleContextWithAccountManager
     * the `BundleContext` in which an `AccountManager` service is
     * registered
     * @param bundleToStart
     * the `Bundle` to be started
     * @param protocolNameToWait
     * the protocol name of a `ProtocolProviderFactory` to wait the end of the
     * loading of the stored accounts for
     * @throws BundleException
     * @throws InterruptedException
     * if any thread interrupted the current thread before or while the current thread was
     * waiting for the loading of the stored accounts
     */
    @Throws(BundleException::class, InterruptedException::class)
    fun startBundleAndWaitStoredAccountsLoaded(
            bundleContextWithAccountManager: BundleContext, bundleToStart: Bundle,
            protocolNameToWait: String) {
        val accountManager = getAccountManager(bundleContextWithAccountManager)
        val storedAccountsAreLoaded = BooleanArray(1)
        val listener = object : AccountManagerListener {
            override fun handleAccountManagerEvent(event: AccountManagerEvent) {
                if (AccountManagerEvent.STORED_ACCOUNTS_LOADED != event.type) return
                val factory = event.factory

                /*
                 * If the event is for a factory with a protocol name other than protocolNameToWait,
                 * it's not the one we're waiting for.
                 */
                if (factory != null && protocolNameToWait != factory.protocolName) return

                /*
                 * If the event if for a factory which is no longer registered, then it's not the
                 * one we're waiting for because we're waiting for the specified bundle to start and
                 * register a factory.
                 */
                if (factory != null) {
                    val bundleContext = bundleToStart.bundleContext ?: return

                /*
                 * If the specified bundle still hasn't started, the event cannot be the one
                 * we're waiting for.
                 */
                    val factoryRefs = try {
                        bundleContext.getServiceReferences(
                                ProtocolProviderFactory::class.java, "(" + ProtocolProviderFactory.PROTOCOL
                                + "=" + protocolNameToWait + ")")
                    } catch (isex: InvalidSyntaxException) {
                        /*
                         * Not likely so ignore it and assume the event is for a valid factory.
                         */
                        null
                    }
                    if (factoryRefs != null && !factoryRefs.isEmpty()) {
                        var factoryIsRegistered = false
                        for (factoryRef in factoryRefs) {
                            if (factory === bundleContext.getService(factoryRef)) {
                                factoryIsRegistered = true
                                break
                            }
                        }
                        if (!factoryIsRegistered) return
                    }
                }
                synchronized(storedAccountsAreLoaded) {
                    storedAccountsAreLoaded[0] = true
                    storedAccountsAreLoaded.notify()
                }
            }
        }
        accountManager.addListener(listener)
        try {
            bundleToStart.start()
            var loop = true
            while (loop) {
                synchronized(storedAccountsAreLoaded) {
                    if (!storedAccountsAreLoaded[0]) {
                        (storedAccountsAreLoaded as Object).wait()
                    } else
                        loop = false
                }
            }
        } finally {
            accountManager.removeListener(listener)
        }
    }
}