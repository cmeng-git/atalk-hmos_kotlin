/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import android.text.TextUtils
import net.java.sip.communicator.impl.protocol.jabber.JabberActivator
import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService
import net.java.sip.communicator.service.protocol.event.AccountManagerEvent
import net.java.sip.communicator.service.protocol.event.AccountManagerListener
import net.java.sip.communicator.util.ServiceUtils.getService
import okhttp3.internal.notifyAll
import org.atalk.persistance.DatabaseBackend
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.osgi.OSGiService
import org.bouncycastle.util.encoders.Base64
import org.osgi.framework.BundleContext
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.util.*

/**
 * Represents an implementation of `AccountManager` which loads the accounts in a separate thread.
 *
 * @author Lyubomir Marinov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class AccountManager(
        /**
         * The `BundleContext` this service is registered in.
         */
        private val bundleContext: BundleContext) {
    private val configurationService: ConfigurationService?

    /**
     * The `AccountManagerListener`s currently interested in the events fired by this manager.
     */
    private val listeners = LinkedList<AccountManagerListener>()

    /**
     * The queue of `ProtocolProviderFactory` services awaiting their stored accounts to be loaded.
     */
    private val loadStoredAccountsQueue: Queue<ProtocolProviderFactory> = LinkedList()

    /**
     * The `Thread` loading the stored accounts of the `ProtocolProviderFactory`
     * services waiting in [.loadStoredAccountsQueue].
     */
    private var loadStoredAccountsThread: Thread? = null

    /**
     * The list of `AccountID`s, corresponding to all stored accounts.
     */
    /**
     * The list of all stored `AccountID`s. It includes all registered accounts and all disabled accounts.
     * In other words in this list we could find accounts that aren't loaded.
     *
     * In order to check if an account is already loaded please use the #isAccountLoaded(AccountID
     * accountID) method. To load an account use the #loadAccount(AccountID accountID) method.
     */
    val storedAccounts = Vector<AccountID>()

    /**
     * aTalk backend SQLite database
     */
    private val databaseBackend: DatabaseBackend?

    /**
     * Initializes a new `AccountManagerImpl` instance loaded in a specific
     * `BundleContext` (in which the caller will usually later register it).
     *
     * bundleContext the `BundleContext` in which the new instance is loaded (and in which the
     * caller will usually later register it as a service)
     */
    init {
        val context = getService(bundleContext, OSGiService::class.java)
        databaseBackend = DatabaseBackend.getInstance(context!!)
        configurationService = ProtocolProviderActivator.getConfigurationService()
        bundleContext.addServiceListener { serviceEvent: ServiceEvent -> serviceChanged(serviceEvent) }
    }

    /**
     * Implements AccountManager#addListener(AccountManagerListener).
     *
     * @param listener the `AccountManagerListener` to add
     */
    fun addListener(listener: AccountManagerListener) {
        synchronized(listeners) { if (!listeners.contains(listener)) listeners.add(listener) }
    }

    /**
     * Loads all the accounts stored for a specific `ProtocolProviderFactory`.
     *
     * @param factory the `ProtocolProviderFactory` to load the stored accounts of
     */
    private fun doLoadStoredAccounts(factory: ProtocolProviderFactory) {
        val accountIDs = databaseBackend!!.getAccounts(factory)
        Timber.d("Found %s %s accounts", accountIDs.size, factory.protocolName)
        val credentialsStorage = getService(bundleContext, CredentialsStorageService::class.java)
        for (accountID in accountIDs) {
            Timber.d("Loading account %s", accountID.accountJid)
            synchronized(storedAccounts) { storedAccounts.add(accountID) }
            if (accountID.isEnabled) {
                // Decode passwords.
                if (!credentialsStorage!!.isStoredEncrypted(accountID.accountUuid)) {
                    val b64EncodedPwd = accountID.getAccountPropertyString("ENCRYPTOED_PASSWORD")
                    if (!TextUtils.isEmpty(b64EncodedPwd)) {
                        /*
                         * Converting byte[] to String using the platform's default charset
                         * may result in an invalid password.
                         */
                        val decryptedPassword = String(Base64.decode(b64EncodedPwd))
                        accountID.password = decryptedPassword
                    }
                }
                factory.loadAccount(accountID)
            }
        }
    }

    /**
     * Notifies the registered [.listeners] that the stored accounts of a specific
     * `ProtocolProviderFactory` have just been loaded.
     *
     * @param factory the `ProtocolProviderFactory` which had its stored accounts just loaded
     */
    private fun fireStoredAccountsLoaded(factory: ProtocolProviderFactory) {
        var listeners: Array<AccountManagerListener>
        synchronized(this.listeners) { listeners = this.listeners.toTypedArray() }
        val listenerCount = listeners.size
        if (listenerCount > 0) {
            val event = AccountManagerEvent(this,
                    AccountManagerEvent.STORED_ACCOUNTS_LOADED, factory)
            for (listener in listeners) {
                listener.handleAccountManagerEvent(event)
            }
        }
    }

    /**
     * Returns the package name of the `factory`.
     *
     * @param factory the factory which package will be returned.
     * @return the package name of the `factory`.
     */
    fun getFactoryImplPackageName(factory: ProtocolProviderFactory): String {
        val className = factory.javaClass.name
        return className.substring(0, className.lastIndexOf('.'))
    }

    /**
     * Check for stored accounts for the supplied `protocolName`.
     *
     * @param protocolName the protocol name to check for
     * @param includeHidden whether to include hidden providers
     * @return `true` if there is any account stored in configuration service with
     * `protocolName`, `false` otherwise.
     */
    fun hasStoredAccounts(protocolName: String?, includeHidden: Boolean): Boolean {
        return hasStoredAccount(protocolName, includeHidden, null)
    }

    /**
     * Checks whether a stored account with `userID` is stored in configuration.
     *
     * @param protocolName the protocol name
     * @param includeHidden whether to check hidden providers
     * @param userID the user id to check.
     * @return `true` if there is any account stored in configuration service with
     * `protocolName` and `userID`, `false` otherwise.
     */
    private fun hasStoredAccount(protocolName: String?, includeHidden: Boolean, userID: String?): Boolean {
        var hasStoredAccount = false
        var accounts: Map<String, String>

        val factoryRefs = try {
            bundleContext.getServiceReferences(ProtocolProviderFactory::class.java.name, null)
        } catch (ex: InvalidSyntaxException) {
            Timber.e(ex, "Failed to retrieve the registered ProtocolProviderFactories")
            return false
        }

        if (factoryRefs != null && factoryRefs.isNotEmpty()) {
            for (factoryRef in factoryRefs) {
                val factory = bundleContext.getService(factoryRef as ServiceReference<ProtocolProviderFactory>)
                accounts = getStoredAccounts(factory)
                if (protocolName == null || protocolName == factory.protocolName) {
                    for (key in accounts.keys) {
                        val accountUuid = accounts[key]
                        var hidden = false
                        val accountUserID = key.split(":")[1]
                        if (!includeHidden || userID != null) {
                            hidden = configurationService!!.getBoolean(accountUuid + "."
                                    + ProtocolProviderFactory.IS_PROTOCOL_HIDDEN, false)
                        }
                        if (includeHidden || !hidden) {
                            if (userID == null || userID == accountUserID) {
                                hasStoredAccount = true
                                break
                            }
                        }
                    }
                    if (hasStoredAccount || protocolName != null) {
                        break
                    }
                }
            }
        }
        return hasStoredAccount
    }

    /**
     * Searches for stored account with `uid` in stored configuration. The `uid` is
     * the one generated when creating accounts with prefix `ACCOUNT_UID_PREFIX`.
     *
     * @return `AccountID` if there is any account stored in configuration service with
     * `uid`, `null` otherwise.
     */
    fun findAccountID(uid: String): AccountID? {
        var accounts: Map<String, String>

        val factoryRefs = try {
            bundleContext.getServiceReferences(ProtocolProviderFactory::class.java.name, null)
        } catch (ex: InvalidSyntaxException) {
            Timber.e(ex, "Failed to retrieve the registered ProtocolProviderFactories")
            return null
        }

        if (factoryRefs != null && factoryRefs.isNotEmpty()) {
            for (factoryRef in factoryRefs) {
                val factory = bundleContext.getService(factoryRef as ServiceReference<ProtocolProviderFactory>)
                accounts = getStoredAccounts(factory)
                for (accountUID in accounts.keys) {
                    if (uid == accounts[accountUID]) {
                        for (acc in storedAccounts) {
                            if (acc!!.accountUniqueID == accountUID) {
                                return acc
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Loads the accounts stored for a specific `ProtocolProviderFactory` and notifies
     * the registered [.listeners] that the stored accounts of the specified `factory`
     * have just been loaded
     *
     * @param factory the `ProtocolProviderFactory` to load the stored accounts of
     */
    private fun loadStoredAccounts(factory: ProtocolProviderFactory) {
        doLoadStoredAccounts(factory)
        fireStoredAccountsLoaded(factory)
    }

    /**
     * Notifies this manager that a specific `ProtocolProviderFactory` has been
     * registered as a service. The current implementation queues the specified `factory`
     * to have its stored accounts as soon as possible.
     *
     * @param factory the `ProtocolProviderFactory` which has been registered as a service.
     */
    private fun protocolProviderFactoryRegistered(factory: ProtocolProviderFactory) {
        queueLoadStoredAccounts(factory)
    }

    /**
     * Queues a specific `ProtocolProviderFactory` to have its stored accounts loaded as
     * soon as possible.
     *
     * @param factory the `ProtocolProviderFactory` to be queued for loading its stored accounts as
     * soon as possible
     */
    private fun queueLoadStoredAccounts(factory: ProtocolProviderFactory) {
        synchronized(loadStoredAccountsQueue) {
            loadStoredAccountsQueue.add(factory)
            loadStoredAccountsQueue.notifyAll()
            if (loadStoredAccountsThread == null) {
                loadStoredAccountsThread = object : Thread() {
                    override fun run() {
                        runInLoadStoredAccountsThread()
                    }
                }
                (loadStoredAccountsThread as Thread).isDaemon = true
                (loadStoredAccountsThread as Thread).name = "AccountManager.loadStoredAccounts"
                (loadStoredAccountsThread as Thread).start()
            }
        }
    }

    /**
     * Implements AccountManager#removeListener(AccountManagerListener).
     *
     * @param listener the `AccountManagerListener` to remove
     */
    fun removeListener(listener: AccountManagerListener) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    /**
     * Running in [.loadStoredAccountsThread], loads the stored accounts of the
     * `ProtocolProviderFactory` services waiting in [.loadStoredAccountsQueue]
     */
    private fun runInLoadStoredAccountsThread() {
        var interrupted = false
        outerLoop@  while (!interrupted) {
            try {
                var factory: ProtocolProviderFactory?
                synchronized(loadStoredAccountsQueue) {
                    factory = loadStoredAccountsQueue.poll()
                    if (factory == null) {
                        /*
                         * Technically, we should be handing spurious wakeup. However, we cannot
                         * check the condition in a queue. Anyway, we just want to keep this Thread
                         * alive long enough to allow it to not be re-created multiple times and
                         * not handing a spurious wakeup will just cause such an inconvenience.
                         */
                        try {
                            (loadStoredAccountsQueue as Object).wait(LOAD_STORED_ACCOUNTS_TIMEOUT)
                        } catch (ex: InterruptedException) {
                            Timber.w(ex, "The loading of the stored accounts has been interrupted")
                            interrupted = true
                            synchronized(loadStoredAccountsQueue) {
                                if (!interrupted && loadStoredAccountsQueue.size <= 0) {
                                    if (loadStoredAccountsThread === Thread.currentThread()) {
                                        loadStoredAccountsThread = null
                                        loadStoredAccountsQueue.notifyAll()
                                    }
                                    return;
                                }
                            }
                            return
                        }
                        factory = loadStoredAccountsQueue.poll()
                    }
                    if (factory != null) {
                        loadStoredAccountsQueue.notifyAll()
                    }
                }

                if (factory != null) {
                    try {
                        loadStoredAccounts(factory!!)
                    } catch (ex: Exception) {

                        /*
                         * Swallow the exception in order to prevent a single factory from halting
                         * the loading of subsequent factories.
                         */
                        Timber.e(ex, "Failed to load accounts for %s", factory)
                    }
                }
                synchronized(loadStoredAccountsQueue) {
                    if (!interrupted && loadStoredAccountsQueue.size <= 0) {
                        if (loadStoredAccountsThread === Thread.currentThread()) {
                            loadStoredAccountsThread = null
                            loadStoredAccountsQueue.notifyAll()
                        }
                        return
                    }
                }
            } finally {
                synchronized(loadStoredAccountsQueue) {
                    if (!interrupted && loadStoredAccountsQueue.size <= 0) {
                        if (loadStoredAccountsThread === Thread.currentThread()) {
                            loadStoredAccountsThread = null
                            loadStoredAccountsQueue.notifyAll()
                        }
                    }
                }
            }
        }
    }

    /**
     * Notifies this manager that an OSGi service has changed. The current implementation tracks
     * the registrations of `ProtocolProviderFactory` services in order to queue them for
     * loading their stored accounts.
     *
     * @param serviceEvent the `ServiceEvent` containing the event data
     */
    private fun serviceChanged(serviceEvent: ServiceEvent) {
        if (serviceEvent.type == ServiceEvent.REGISTERED) {
            val service = bundleContext.getService(serviceEvent.serviceReference)
            if (service is ProtocolProviderFactory) {
                protocolProviderFactoryRegistered(service)
            }
        }
    }

    /**
     * Stores an account represented in the form of an `AccountID` created by a specific
     * `ProtocolProviderFactory`.
     *
     * @param factory the `ProtocolProviderFactory` which created the account to be stored
     * @param accountID the account in the form of `AccountID` to be stored
     * @throws OperationFailedException if anything goes wrong while storing the account
     */
    @Throws(OperationFailedException::class)
    fun storeAccount(factory: ProtocolProviderFactory, accountID: AccountID) {
        val configurationProperties = HashMap<String, Any?>()
        synchronized(storedAccounts) { if (!storedAccounts.contains(accountID)) storedAccounts.add(accountID) }

        // Check to check if this is an existing stored account; else need to create the new
        // account in table before storing other account Properties
        val accountUid = accountID.accountUniqueID
        var accountUuid = getStoredAccountUUID(factory, accountUid!!)
        if (accountUuid == null) {
            accountUuid = accountID.accountUuid
            databaseBackend!!.createAccount(accountID)
        }

        // store the rest of the properties
        val accountProperties = accountID.accountProperties
        for ((property, value) in accountProperties) {

            // Properties already stored in table AccountID.TABLE_NAME; so skip
            if (property == ProtocolProviderFactory.ACCOUNT_UUID || property == ProtocolProviderFactory.PROTOCOL || property == ProtocolProviderFactory.USER_ID || property == ProtocolProviderFactory.ACCOUNT_UID || property == ProtocolProviderFactory.PASSWORD || property == "ENCRYPTED_PASSWORD") {
                continue
            } else {
                configurationProperties["$accountUuid.$property"] = value
            }
        }

        /*
         * Account modification can request password delete and only if it's not stored already in encrypted form.
         * Account registration object clears this property in order to forget the password
         * If password persistent is set and password is not null, then store password securely. Otherwise purge it.
         */
        val credentialsStorage = getService(bundleContext, CredentialsStorageService::class.java)
        if (credentialsStorage != null) {
            if (accountID.isPasswordPersistent) {
                val password = accountProperties[ProtocolProviderFactory.PASSWORD]
                credentialsStorage.storePassword(accountUuid!!, password)
            } else {
                credentialsStorage.removePassword(accountUuid!!)
            }
        } else {
            throw OperationFailedException("CredentialsStorageService failed to storePassword",
                    OperationFailedException.GENERAL_ERROR)
        }

        // Save all the account configurationProperties into the database
        if (configurationProperties.isNotEmpty()) configurationService!!.setProperties(configurationProperties)
        Timber.d("Stored account for id %s", accountUid)
    }

    /**
     * Modify accountID table with the new AccountID parameters e.g. user changes the userID
     *
     * @param accountID the account in the form of `AccountID` to be modified
     */
    fun modifyAccountId(accountID: AccountID?) {
        databaseBackend!!.createAccount(accountID!!)
    }

    /**
     * Gets account node name under which account configuration properties are stored.
     *
     * @param factory account's protocol provider factory
     * @param accountUID account for which the prefix will be returned
     * @return configuration prefix for given `accountID` if exists or `null` otherwise
     */
    fun getStoredAccountUUID(factory: ProtocolProviderFactory, accountUID: String): String? {
        val accounts = getStoredAccounts(factory)
        return if (accounts.containsKey(accountUID)) accounts[accountUID] else null
    }

    /**
     * Removes the account with `accountID` from the set of accounts that are persistently
     * stored inside the configuration service.
     *
     * @param factory_ the `ProtocolProviderFactory` which created the account to be stored
     * @param accountID the AccountID of the account to remove.
     * @return true if an account has been removed and false otherwise.
     */
    fun removeStoredAccount(factory_: ProtocolProviderFactory?, accountID: AccountID): Boolean {
        var factory = factory_
        synchronized(storedAccounts) { storedAccounts.remove(accountID) }
        /*
         * We're already doing it in #unloadAccount(AccountID) - we're figuring out the
         * ProtocolProviderFactory by the AccountID.
         */
        if (factory == null) {
            factory = ProtocolProviderActivator.getProtocolProviderFactory(accountID.protocolName)
        }
        // null means account has been removed.
        return getStoredAccountUUID(factory!!, accountID.accountUniqueID!!) == null
    }

    /**
     * Removes all accounts which have been persistently stored.
     *
     * @see .removeStoredAccount
     */
    fun removeStoredAccounts() {
        synchronized(loadStoredAccountsQueue) {

            /*
             * Wait for the Thread which loads the stored account to complete so that we can be
             * sure later on that it will not load a stored account while we are deleting it or
             * another one for that matter.
             */
            var interrupted = false
            while (loadStoredAccountsThread != null) {
                try {
                    (loadStoredAccountsQueue as Object).wait(LOAD_STORED_ACCOUNTS_TIMEOUT)
                } catch (ie: InterruptedException) {
                    interrupted = true
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt()
            }
            synchronized(storedAccounts) {
                val storedAccounts = storedAccounts.toTypedArray()
                for (storedAccount in storedAccounts) {
                    val ppf = ProtocolProviderActivator.getProtocolProviderFactory(storedAccount!!.protocolName)
                    ppf?.uninstallAccount(storedAccount)
                }
            }
        }
    }

    /**
     * Loads the account corresponding to the given `AccountID`. An account is loaded when
     * its `ProtocolProviderService` is registered in the bundle context. This method is
     * meant to load the account through the corresponding `ProtocolProviderFactory` .
     *
     * @param accountID the identifier of the account to load
     * @throws OperationFailedException if anything goes wrong while loading the account corresponding to the specified
     * `accountID`
     */
    @Throws(OperationFailedException::class)
    fun loadAccount(accountID: AccountID) {
        // If the account with the given id is already loaded we have nothing to do here.
        if (isAccountLoaded(accountID)) {
            Timber.w("Account is already loaded: %s", accountID)
            return
        }
        val providerFactory = ProtocolProviderActivator.getProtocolProviderFactory(accountID.protocolName)
        if (providerFactory!!.loadAccount(accountID)) {
            accountID.putAccountProperty(ProtocolProviderFactory.IS_ACCOUNT_DISABLED, false.toString())

            // must retrieve password before store the modified properties;
            // otherwise password become null if it was not login on app launch.
            val password = JabberActivator.protocolProviderFactory.loadPassword(accountID)
            accountID.putAccountProperty(ProtocolProviderFactory.PASSWORD, password!!)
            storeAccount(providerFactory, accountID)
        } else {
            Timber.w("Account was not loaded: %s ", accountID)
        }
    }

    /**
     * Unloads the account corresponding to the given `AccountID`. An account is unloaded
     * when its `ProtocolProviderService` is unregistered in the bundle context. This method
     * is meant to unload the account through the corresponding `ProtocolProviderFactory`.
     *
     * @param accountID the identifier of the account to load
     * @throws OperationFailedException if anything goes wrong while unloading the account corresponding
     * to the specified `accountID`
     */
    @Throws(OperationFailedException::class)
    fun unloadAccount(accountID: AccountID) {
        // If the account with the given id is already unloaded we have nothing to do here.
        if (!isAccountLoaded(accountID)) return

        // Obtain the protocol provider.
        val providerFactory = ProtocolProviderActivator.getProtocolProviderFactory(accountID.protocolName)
        val serRef = providerFactory!!.getProviderForAccount(accountID) ?: return

        // If there's no such provider we have nothing to do here.
        val protocolProvider = bundleContext.getService(serRef)
        // Set the account icon path for unloaded accounts.
        val iconPathProperty = accountID.getAccountPropertyString(ProtocolProviderFactory.ACCOUNT_ICON_PATH)
        if (iconPathProperty == null) {
            accountID.putAccountProperty(ProtocolProviderFactory.ACCOUNT_ICON_PATH,
                    protocolProvider.protocolIcon.getIconPath(ProtocolIcon.ICON_SIZE_32x32)!!)
        }
        accountID.putAccountProperty(ProtocolProviderFactory.IS_ACCOUNT_DISABLED, true.toString())
        if (!providerFactory.unloadAccount(accountID)) {
            accountID.putAccountProperty(ProtocolProviderFactory.IS_ACCOUNT_DISABLED, false.toString())
        }

        // must retrieve password before store the modified properties;
        // otherwise password may become null if it was never login on before unload.
        val password = JabberActivator.protocolProviderFactory.loadPassword(accountID)
        accountID.putAccountProperty(ProtocolProviderFactory.PASSWORD, password!!)

        // Finally store the modified properties.
        storeAccount(providerFactory, accountID)
    }

    /**
     * Checks if the account corresponding to the given `accountID` is loaded. An account is
     * loaded if its `ProtocolProviderService` is registered in the bundle context. By
     * default all accounts are loaded. However the user could manually unload an account, which
     * would be unregistered from the bundle context, but would remain in the configuration file.
     *
     * @param accountID the identifier of the account to load
     * @return `true` to indicate that the account with the given `accountID` is
     * loaded, `false` - otherwise
     */
    private fun isAccountLoaded(accountID: AccountID): Boolean {
        return storedAccounts.contains(accountID) && accountID.isEnabled
    }

    private fun stripPackagePrefix(property_: String): String {
        var property = property_
        val packageEndIndex = property.lastIndexOf('.')
        if (packageEndIndex != -1) {
            property = property.substring(packageEndIndex + 1)
        }
        return property
    }

    private fun getStoredAccounts(factory: ProtocolProviderFactory): Map<String, String> {
        val accounts = Hashtable<String, String>()
        val mDB = databaseBackend!!.readableDatabase
        val args = arrayOf(factory.protocolName)
        val cursor = mDB.query(AccountID.TABLE_NAME, null, AccountID.PROTOCOL + "=?",
                args, null, null, null)
        while (cursor.moveToNext()) {
            accounts[cursor.getString(cursor.getColumnIndexOrThrow(AccountID.ACCOUNT_UID))] = cursor.getString(cursor.getColumnIndexOrThrow(AccountID.ACCOUNT_UUID))
        }
        cursor.close()
        return accounts
    }

    companion object {
        /**
         * The delay in milliseconds the background `Thread` loading the stored accounts should wait
         * before dying so that it doesn't get recreated for each `ProtocolProviderFactory` registration.
         */
        private const val LOAD_STORED_ACCOUNTS_TIMEOUT = 30000L
    }
}