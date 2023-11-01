/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.account

import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.AccountManager
import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.util.ServiceUtils
import net.java.sip.communicator.util.UtilActivator
import org.osgi.framework.InvalidSyntaxException
import timber.log.Timber
import java.util.*

/**
 * The `AccountUtils` provides utility methods helping us to easily obtain an account or
 * a groups of accounts or protocol providers by some specific criteria.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
object AccountUtils {
    /**
     * Returns an iterator over a list of all stored `AccountID`-s.
     *
     * @return an iterator over a list of all stored `AccountID`-s
     */
    val storedAccounts: List<AccountID?>
        get() {
            val accountManager = ServiceUtils.getService(UtilActivator.bundleContext, AccountManager::class.java)
            return accountManager?.storedAccounts ?: emptyList()
        }

    /**
     * Return the `AccountID` corresponding to the given string account unique identifier.
     *
     * @param accountUID the account unique identifier string
     * @return the `AccountID` corresponding to the given string account unique identifier
     */
    fun getAccountIDForUID(accountUID: String): AccountID? {
        val allAccounts = storedAccounts
        for (account in allAccounts) {
            if (account!!.accountUniqueID == accountUID) return account
        }
        return null
    }

    /**
     * Return the `AccountID` corresponding to the given string account userID; assuming
     * that userID is unique across all protocolServiceProviders
     *
     * @param userID the account unique identifier string
     * @return the `AccountID` corresponding to the given string account userID
     */
    fun getAccountIDForUserID(userID: String): AccountID? {
        val allAccounts = storedAccounts
        for (account in allAccounts) {
            if (account!!.mUserID == userID) return account
        }
        return null
    }

    /**
     * Returns a list of all currently registered providers, which support the given `operationSetClass`.
     *
     * @param opSetClass the operation set class for which we're looking for providers
     * @return a list of all currently registered providers, which support the given `operationSetClass`
     */
    fun getRegisteredProviders(opSetClass: Class<out OperationSet?>?): List<ProtocolProviderService> {
        val opSetProviders = LinkedList<ProtocolProviderService>()
        for (providerFactory in UtilActivator.protocolProviderFactories.values) {
            for (accountID in providerFactory.getRegisteredAccounts()) {
                val ref = providerFactory.getProviderForAccount(accountID)
                if (ref != null) {
                    val pps = UtilActivator.bundleContext!!.getService(ref)
                    if (pps.getOperationSet(opSetClass!!) != null && pps.isRegistered) {
                        opSetProviders.add(pps)
                    }
                }
            }
        }
        return opSetProviders
    }

    /**
     * Returns a list of all currently registered telephony providers for the given protocol name.
     *
     * @param protocolName the protocol name
     * @param opSetClass the operation set class for which we're looking for providers
     * @return a list of all currently registered providers for the given `protocolName`
     * and supporting the given `operationSetClass`
     */
    private fun getRegisteredProviders(
        protocolName: String,
        opSetClass: Class<out OperationSet?>?,
    ): List<ProtocolProviderService> {
        val opSetProviders = LinkedList<ProtocolProviderService>()
        val providerFactory = getProtocolProviderFactory(protocolName)

        if (providerFactory != null) {
            for (accountID in providerFactory.getRegisteredAccounts()) {
                val ref = providerFactory.getProviderForAccount(accountID)
                if (ref != null) {
                    val protocolProvider = UtilActivator.bundleContext!!.getService(ref)
                    if (protocolProvider.getOperationSet(opSetClass!!) != null && protocolProvider.isRegistered) {
                        opSetProviders.add(protocolProvider)
                    }
                }
            }
        }
        return opSetProviders
    }

    /**
     * Returns a list of all registered protocol providers that could be used for the operation
     * given by the operation set. Prefers the given preferred protocol provider and preferred
     * protocol name if they're available and registered.
     *
     * @param opSet the operation set for which we're looking for providers
     * @param preferredProvider the preferred protocol provider
     * @param preferredProtocolName the preferred protocol name
     * @return a list of all registered protocol providers that could be used for the operation
     * given by the operation set
     */
    fun getOpSetRegisteredProviders(
        opSet: Class<out OperationSet?>?, preferredProvider: ProtocolProviderService?, preferredProtocolName: String?,
    ): List<ProtocolProviderService> {
        val providers: List<ProtocolProviderService>

        if (preferredProvider != null) {
            if (preferredProvider.isRegistered) {
                providers = List(1){preferredProvider}
            }
            else {
                providers = getRegisteredProviders(preferredProvider.protocolName, opSet)
            }
        }
        else {
            providers = if (preferredProtocolName != null) {
                getRegisteredProviders(preferredProtocolName, opSet)
            }
            else {
                getRegisteredProviders(opSet)
            }
        }
        return providers
    }

    /**
     * Returns the `ProtocolProviderService` corresponding to the given account identifier
     * that is registered in the given factory
     *
     * @param accountID the identifier of the account
     * @return the `ProtocolProviderService` corresponding to the given account identifier
     * that is registered in the given factory
     */
    fun getRegisteredProviderForAccount(accountID: AccountID?): ProtocolProviderService? {
        for (factory in UtilActivator.protocolProviderFactories.values) {
            if (factory.registeredAccounts.contains(accountID)) {
                val ref = factory.getProviderForAccount(accountID!!)
                if (ref != null) {
                    return UtilActivator.bundleContext!!.getService(ref)
                }
            }
        }
        return null
    }

    /**
     * Returns a `ProtocolProviderFactory` for a given protocol provider.
     *
     * @param protocolProvider the `ProtocolProviderService`, which factory we're looking for
     * @return a `ProtocolProviderFactory` for a given protocol provider
     */
    fun getProtocolProviderFactory(protocolProvider: ProtocolProviderService): ProtocolProviderFactory? {
        return getProtocolProviderFactory(protocolProvider.protocolName)
    }

    /**
     * Returns a `ProtocolProviderFactory` for a given protocol provider.
     *
     * @param protocolName the name of the protocol
     * @return a `ProtocolProviderFactory` for a given protocol provider
     */
    fun getProtocolProviderFactory(protocolName: String): ProtocolProviderFactory? {
        val osgiFilter = "(" + ProtocolProviderFactory.PROTOCOL + "=" + protocolName + ")"
        var protocolProviderFactory: ProtocolProviderFactory? = null
        try {
            val refs = UtilActivator.bundleContext!!.getServiceReferences(ProtocolProviderFactory::class.java, osgiFilter)
            if (refs != null && !refs.isEmpty()) {
                protocolProviderFactory = UtilActivator.bundleContext!!.getService(refs.iterator().next())
            }
        } catch (ex: InvalidSyntaxException) {
            Timber.e("AccountUtils : %s", ex.message)
        }
        return protocolProviderFactory
    }

    /**
     * Returns all registered protocol providers independent of .isRegister().
     *
     * @return a list of all registered providers
     */
    val registeredProviders: Collection<ProtocolProviderService>
        get() {
            val registeredProviders = LinkedList<ProtocolProviderService>()
            for (providerFactory in UtilActivator.protocolProviderFactories.values) {
                for (accountID in providerFactory.getRegisteredAccounts()) {
                    val ref = providerFactory.getProviderForAccount(accountID)
                    if (ref != null) {
                        val protocolProvider = UtilActivator.bundleContext!!.getService(ref)
                        registeredProviders.add(protocolProvider)
                    }
                }
            }
            return registeredProviders
        }

    /**
     * Returns all registered protocol providers that are online (.isRegister() == true).
     *
     * @return a list of all registered providers
     */
    val onlineProviders: Collection<ProtocolProviderService>
        get() {
            val onlineProviders = LinkedList<ProtocolProviderService>()
            for (providerFactory in UtilActivator.protocolProviderFactories.values) {
                for (accountID in providerFactory.getRegisteredAccounts()) {
                    val ref = providerFactory.getProviderForAccount(accountID)
                    if (ref != null) {
                        val protocolProvider = UtilActivator.bundleContext!!.getService(ref)
                        if (protocolProvider.isRegistered) onlineProviders.add(protocolProvider)
                    }
                }
            }
            return onlineProviders
        }
}