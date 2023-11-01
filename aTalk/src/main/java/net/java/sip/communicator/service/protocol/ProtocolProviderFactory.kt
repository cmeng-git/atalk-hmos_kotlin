/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService
import net.java.sip.communicator.util.ServiceUtils.getService
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.service.configuration.ConfigurationService
import org.osgi.framework.BundleContext
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceReference
import org.osgi.framework.ServiceRegistration
import timber.log.Timber
import java.lang.reflect.UndeclaredThrowableException
import java.util.*

/**
 * The ProtocolProviderFactory is what actually creates instances of a ProtocolProviderService
 * implementation. A provider factory would register, persistently store, and remove when necessary,
 * ProtocolProviders. The way things are in the SIP Communicator, a user account is represented (in
 * a 1:1 relationship) by an AccountID and a ProtocolProvider. In other words - one would have as
 * many protocol providers installed in a given moment as they would user account registered through
 * the various services.
 *
 * @author Emil Ivov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 * @author MilanKral
 */
abstract class ProtocolProviderFactory protected constructor(
        /**
         * The `BundleContext` containing (or to contain) the service registration of this factory.
         */
        val bundleContext: BundleContext,
        /**
         * The name of the protocol this factory registers its `ProtocolProviderService`s
         * with and to be placed in the properties of the accounts created by this factory.
         */
        val protocolName: String) {
    /**
     * Gets the `BundleContext` containing (or to contain) the service registration of this factory.
     *
     * @return the `BundleContext` containing (or to contain) the service registration of this factory
     */
    /**
     * Gets the name of the protocol this factory registers its `ProtocolProviderService`s
     * with and to be placed in the properties of the accounts created by this factory.
     *
     * @return the name of the protocol this factory registers its
     * `ProtocolProviderService`s with and to be placed in the properties of the
     * accounts created by this factory
     */

    /**
     * The configuration service.
     */
    private val configurationService = getService(bundleContext, ConfigurationService::class.java)

    /**
     * The table that we store our accounts in.
     *
     * TODO Synchronize the access to the field which may in turn be better achieved by also hiding
     * it from protected into private access.
     */
    val registeredAccounts = HashMap<AccountID, ServiceRegistration<ProtocolProviderService>>()

    /**
     * Initializes and creates an account corresponding to the specified accountProperties and
     * registers the resulting ProtocolProvider in the `context` BundleContext parameter.
     * Note that account registration is persistent and accounts that are registered during a
     * particular sip-communicator session would be automatically reloaded during all following
     * sessions until they are removed through the removeAccount method.
     *
     * @param userID the user identifier uniquely representing the newly created account within the protocol namespace.
     * @param accountProperties a set of protocol (or implementation) specific properties defining the new account.
     * @return the AccountID of the newly created account.
     * @throws java.lang.IllegalArgumentException if userID does not correspond to an identifier in the context of the
     * underlying protocol or if accountProperties does not contain a complete set of account installation properties.
     * @throws java.lang.IllegalStateException if the account has already been installed.
     * @throws java.lang.NullPointerException if any of the arguments is null.
     */
    @Throws(IllegalArgumentException::class, IllegalStateException::class, NullPointerException::class)
    abstract fun installAccount(userID: String?, accountProperties: MutableMap<String, String?>?): AccountID?

    /**
     * Modifies the account corresponding to the specified accountID. This method is meant to be
     * used to change properties of already existing accounts. Note that if the given accountID
     * doesn't correspond to any registered account this method would do nothing.
     *
     * @param protocolProvider the protocol provider service corresponding to the modified account.
     * @param accountProperties a set of protocol (or implementation) specific properties defining the new account.
     * @throws java.lang.IllegalArgumentException if userID does not correspond to an identifier in the context of the
     * underlying protocol or if accountProperties does not contain a complete set of account installation properties.
     * @throws java.lang.NullPointerException if any of the arguments is null.
     */
    @Throws(IllegalArgumentException::class, NullPointerException::class)
    abstract fun modifyAccount(protocolProvider: ProtocolProviderService?, accountProperties: MutableMap<String, String?>?)

    /**
     * Returns a copy of the list containing the `AccountID`s of all accounts currently
     * registered in this protocol provider.
     *
     * @return a copy of the list containing the `AccountID`s of all accounts currently
     * registered in this protocol provider.
     */
    fun getRegisteredAccounts(): ArrayList<AccountID> {
        synchronized(registeredAccounts) { return ArrayList(registeredAccounts.keys) }
    }

    /**
     * Returns the ServiceReference for the protocol provider corresponding to the specified
     * accountID or null if the accountID is unknown.
     *
     * @param accountID the accountID of the protocol provider we'd like to get
     * @return a ServiceReference object to the protocol provider with the specified account id and
     * null if the account id is unknown to the provider factory.
     */
    fun getProviderForAccount(accountID: AccountID): ServiceReference<ProtocolProviderService>? {
        var registration: ServiceRegistration<ProtocolProviderService>?
        synchronized(registeredAccounts) { registration = registeredAccounts[accountID] }
        try {
            if (registration != null) return registration!!.reference
        } catch (ise: IllegalStateException) {
            synchronized(registeredAccounts) { registeredAccounts.remove(accountID) }
        }
        return null
    }

    /**
     * Removes the specified account from the list of accounts that this provider factory is
     * handling. If the specified accountID is unknown to the ProtocolProviderFactory, the call has
     * no effect and false is returned. This method is persistent in nature and once called the
     * account corresponding to the specified ID will not be loaded during future runs of the project.
     *
     * @param accountID the ID of the account to remove.
     * @return true if an account with the specified ID existed and was removed and false otherwise.
     */
    fun uninstallAccount(accountID: AccountID): Boolean {
        // If the protocol provider service is registered, first unregister the service.
        val serRef = getProviderForAccount(accountID)
        if (serRef != null) {
            val bundleContext = bundleContext
            val protocolProvider = bundleContext.getService(serRef)
            try {
                protocolProvider.unregister()
            } catch (ex: OperationFailedException) {
                Timber.e("Failed to unregister protocol provider for account: %s caused by: %s",
                        accountID, ex.message)
            }
        }
        var registration: ServiceRegistration<ProtocolProviderService>?
        synchronized(registeredAccounts) { registration = registeredAccounts.remove(accountID) }

        // first remove the stored account so when PP is unregistered we can distinguish between
        // deleted or just disabled account
        val wasAccountExisting = removeStoredAccount(accountID)
        if (registration != null) {
            // Kill the service.
            registration!!.unregister()
        }
        return wasAccountExisting
    }

    /**
     * The method stores the specified account in the configuration service under the package name
     * of the source factory. The restore and remove account methods are to be used to obtain
     * access to and control the stored accounts.
     *
     * In order to store all account properties, the method would create an entry in the
     * configuration service corresponding (beginning with) the `sourceFactory`'s package
     * name and add to it a unique identifier (e.g. the current milliseconds.)
     *
     * @param accountID the AccountID corresponding to the account that we would like to store.
     */
    protected open fun storeAccount(accountID: AccountID) {
        this.storeAccount(accountID, true)
    }

    /**
     * The method stores the specified account in the configuration service under the package name
     * of the source factory. The restore and remove account methods are to be used to obtain
     * access to and control the stored accounts.
     *
     * In order to store all account properties, the method would create an entry in the
     * configuration service corresponding (beginning with) the `sourceFactory`'s package
     * name and add to it a unique identifier (e.g. the current milliseconds.)
     *
     * @param accountID the AccountID corresponding to the account that we would like to store.
     * @param isModification if `false` there must be no such already loaded account, it `true` ist
     * modification of an existing account. Usually we use this method with `false` in
     * method installAccount and with `true` or the overridden method in method
     * modifyAccount.
     */
    protected fun storeAccount(accountID: AccountID, isModification: Boolean) {
        check(!(!isModification && accountManager.storedAccounts.contains(accountID))) { "An account for id " + accountID.mUserID + " was already loaded!" }
        try {
            accountManager.storeAccount(this, accountID)
        } catch (ofex: OperationFailedException) {
            throw UndeclaredThrowableException(ofex)
        }
    }

    /**
     * Saves the password for the specified account after scrambling it a bit so that it is not
     * visible from first sight. (The method remains highly insecure).
     *
     * @param accountID the AccountID for the account whose password we're storing
     * @param password the password itself
     * @throws IllegalArgumentException if no account corresponding to `accountID` has been previously stored
     */
    @Throws(IllegalArgumentException::class)
    fun storePassword(accountID: AccountID, password: String?) {
        try {
            storePassword(bundleContext, accountID, password)
        } catch (ofex: OperationFailedException) {
            throw UndeclaredThrowableException(ofex)
        }
    }

    /**
     * Saves the password for the specified account after scrambling it a bit so that it is not
     * visible from first sight (Method remains highly insecure).
     *
     * TODO Delegate the implementation to [AccountManager] because it knows the format in
     * which the password (among the other account properties) is to be saved.
     *
     * @param bundleContext a currently valid bundle context.
     * @param accountID the `AccountID` of the account whose password is to be stored
     * @param password the password to be stored
     * @throws IllegalArgumentException if no account corresponding to `accountID` has been previously stored.
     * @throws OperationFailedException if anything goes wrong while storing the specified `password`
     */
    @Throws(IllegalArgumentException::class, OperationFailedException::class)
    protected fun storePassword(bundleContext: BundleContext?, accountID: AccountID, password: String?) {
        val accountUuid = accountID.accountUuid
                ?: throw IllegalArgumentException("No previous records found for account ID: "
                        + accountID.accountUniqueID)
        val credentialsStorage = getService(bundleContext, CredentialsStorageService::class.java)
        if (!credentialsStorage!!.storePassword(accountUuid, password)) {
            throw OperationFailedException("CredentialsStorageService failed to storePassword",
                    OperationFailedException.GENERAL_ERROR)
        }
        // Update password property also in the AccountID to prevent it from being removed during
        // account reload in some cases.
        accountID.password = password
    }

    /**
     * Saves the dnssec Mode for the specified account.
     *
     * @param accountID the AccountID for the account whose password we're storing
     * @param dnssecMode see DNSSEC_MODE definition
     * @throws IllegalArgumentException if no account corresponding to `accountID` has been previously stored
     */
    @Throws(IllegalArgumentException::class)
    fun storeDnssecMode(accountID: AccountID, dnssecMode: String) {
        try {
            storeDnssecMode(bundleContext, accountID, dnssecMode)
        } catch (ofex: OperationFailedException) {
            throw UndeclaredThrowableException(ofex)
        }
    }

    /**
     * Saves the password for the specified account after scrambling it a bit so that it is not
     * visible from first sight (Method remains highly insecure).
     *
     * TODO Delegate the implementation to [AccountManager] because it knows the format in
     * which the password (among the other account properties) is to be saved.
     *
     * @param bundleContext a currently valid bundle context.
     * @param accountID the `AccountID` of the account whose password is to be stored
     * @param dnssecMode the dnssecMode to be stored
     * @throws IllegalArgumentException if no account corresponding to `accountID` has been previously stored.
     * @throws OperationFailedException if anything goes wrong while storing the specified `password`
     */
    @Throws(IllegalArgumentException::class, OperationFailedException::class)
    protected fun storeDnssecMode(bundleContext: BundleContext?, accountID: AccountID, dnssecMode: String) {
        val accountUuid = accountID.accountUuid
                ?: throw IllegalArgumentException("No previous records found for account ID: "
                        + accountID.accountUniqueID)
        configurationService!!.setProperty("$accountUuid.$DNSSEC_MODE", dnssecMode)

        // Update dnssecMode in the AccountID to prevent it from being removed during account reload in some cases.
        accountID.dnssMode = dnssecMode
    }
    //=======================================
    /**
     * Returns the password last saved for the specified account.
     *
     * @param accountID the AccountID for the account whose password we're looking for
     * @return a String containing the password for the specified accountID
     */
    fun loadPassword(accountID: AccountID): String? {
        return loadPassword(bundleContext, accountID)
    }

    /**
     * Returns the password last saved for the specified account.
     *
     * TODO Delegate the implementation to [AccountManager] because it knows the format in
     * which the password (among the other account properties) was saved.
     *
     * @param bundleContext a currently valid bundle context.
     * @param accountID the AccountID for the account whose password we're looking for..
     * @return a String containing the password for the specified accountID.
     */
    protected fun loadPassword(bundleContext: BundleContext?, accountID: AccountID): String? {
        val credentialsStorage = getService(bundleContext, CredentialsStorageService::class.java)
        return credentialsStorage!!.loadPassword(accountID.accountUuid!!)
    }

    /**
     * Initializes and creates an account corresponding to the specified accountProperties and
     * registers the resulting ProtocolProvider in the `context` BundleContext parameter.
     * This method has a persistent effect. Once created the resulting account will remain
     * installed until removed through the uninstallAccount method.
     *
     * @param accountProperties a set of protocol (or implementation) specific properties defining the new account.
     * @return the AccountID of the newly loaded account
     */
    fun loadAccount(accountProperties: MutableMap<String, String?>?): AccountID {
        val accountID = createAccount(accountProperties)
        loadAccount(accountID)
        return accountID
    }

    /**
     * Creates a protocol provider for the given `accountID` and registers it in the bundle
     * context. This method has a persistent effect. Once created the resulting account will remain
     * installed until removed through the uninstallAccount method.
     *
     * @param accountID the account identifier
     * @return `true` if the account with the given `accountID` is successfully
     * loaded, otherwise returns `false`
     */
    fun loadAccount(accountID: AccountID): Boolean {
        // Need to obtain the original user id property, instead of calling accountID.userID,
        // because this method could return a modified version of the user id property.
        val userID = accountID.getAccountPropertyString(USER_ID)!!
        val service = createService(userID, accountID) ?: return false
        val properties = Hashtable<String, String?>()
        properties[PROTOCOL] = protocolName
        properties[USER_ID] = userID
        val serviceRegistration = bundleContext.registerService(ProtocolProviderService::class.java, service, properties)
        return if (serviceRegistration == null) {
            false
        } else {
            synchronized(registeredAccounts) { registeredAccounts.put(accountID, serviceRegistration) }
            true
        }
    }

    /**
     * Unloads the account corresponding to the given `accountID`. Unregisters the corresponding
     * protocol provider, but keeps the account in contrast to the uninstallAccount method.
     *
     * @param accountID the account identifier
     * @return true if an account with the specified ID existed and was unloaded and false otherwise.
     */
    fun unloadAccount(accountID: AccountID): Boolean {
        // Unregister the protocol provider.
        val serRef = getProviderForAccount(accountID) ?: return false
        val bundleContext = bundleContext
        val protocolProvider = bundleContext.getService(serRef)
        try {
            protocolProvider.unregister()
        } catch (ex: OperationFailedException) {
            Timber.e("Failed to unregister protocol provider for account: %s caused by: %s",
                    accountID, ex.message)
        }
        var registration: ServiceRegistration<ProtocolProviderService>?
        synchronized(registeredAccounts) { registration = registeredAccounts.remove(accountID) }
        if (registration == null) {
            return false
        }

        // Kill the service. // Catch based on Field Failure
        try {
            registration!!.unregister()
        } catch (ex: IllegalStateException) {
            return false
        }
        return true
    }

    /**
     * Initializes and creates an account corresponding to the specified accountProperties.
     *
     * @param accountProperties a set of protocol (or implementation) specific properties defining the new account.
     * @return the AccountID of the newly created account
     */
    fun createAccount(accountProperties: MutableMap<String, String?>?): AccountID {
        val bundleContext = bundleContext
                ?: throw NullPointerException("The specified BundleContext was null")
        if (accountProperties == null) throw NullPointerException("The specified property map was null")
        val userID = accountProperties[USER_ID]
                ?: throw NullPointerException("The account properties contained no user id.")
        val protocolName = protocolName
        if (!accountProperties.containsKey(PROTOCOL)) accountProperties[PROTOCOL] = protocolName
        return createAccountID(userID, accountProperties)
    }

    /**
     * Creates a new `AccountID` instance with a specific user ID to represent a given
     * set of account properties.
     *
     * The method is a pure factory allowing implementers to specify the runtime type of the created
     * `AccountID` and customize the instance. The returned `AccountID` will
     * later be associated with a `ProtocolProviderService` by the caller (e.g. using
     * [.createService]).
     *
     * @param userID the user ID of the new instance
     * @param accountProperties the set of properties to be represented by the new instance
     * @return a new `AccountID` instance with the specified user ID representing the
     * given set of account properties
     */
    protected abstract fun createAccountID(userID: String, accountProperties: MutableMap<String, String?>): AccountID

    /**
     * Initializes a new `ProtocolProviderService` instance with a specific user ID to
     * represent a specific `AccountID`.
     *
     * The method is a pure factory allowing implementers to specify the runtime type of the created
     * `ProtocolProviderService` and customize the instance. The caller will later
     * register the returned service with the `BundleContext` of this factory.
     *
     * @param userID the user ID to initialize the new instance with
     * @param accountID the `AccountID` to be represented by the new instance
     * @return a new `ProtocolProviderService` instance with the specific user ID
     * representing the specified `AccountID`
     */
    protected abstract fun createService(userID: String, accountID: AccountID): ProtocolProviderService?

    /**
     * Removes the account with `accountID` from the set of accounts that are persistently
     * stored inside the configuration service.
     *
     * @param accountID the AccountID of the account to remove.
     * @return true if an account has been removed and false otherwise.
     */
    private fun removeStoredAccount(accountID: AccountID): Boolean {
        return accountManager.removeStoredAccount(this, accountID)
    }

    /**
     * Returns the name of the package that we're currently running in (i.e. the name of the
     * package containing the proto factory that extends us).
     *
     * @return a String containing the package name of the concrete factory class that extends us.
     */
    private val factoryImplPackageName: String
        get() {
            val className = javaClass.name
            return className.substring(0, className.lastIndexOf('.'))
        }

    /**
     * Prepares the factory for bundle shutdown.
     */
    fun stop() {
        Timber.log(TimberLog.FINER, "Preparing to stop all protocol providers of: %s", this)
        synchronized(registeredAccounts) {
            for (reg in registeredAccounts.values) {
                stop(reg)
                reg.unregister()
            }
            registeredAccounts.clear()
        }
    }

    /**
     * Shuts down the `ProtocolProviderService` representing an account registered with
     * this factory.
     *
     * @param registeredAccount the `ServiceRegistration` of the `ProtocolProviderService`
     * representing an account registered with this factory
     */
    protected fun stop(registeredAccount: ServiceRegistration<ProtocolProviderService>) {
        val protocolProviderService = bundleContext.getService(registeredAccount.reference)
        protocolProviderService.shutdown()
    }

    /**
     * Get the `AccountManager` of the protocol.
     *
     * @return `AccountManager` of the protocol
     */
    val accountManager: AccountManager
        get() {
            val bundleContext = bundleContext
            val serviceReference = bundleContext.getServiceReference(AccountManager::class.java)
            return bundleContext.getService(serviceReference)
        }

    companion object {
        /**
         * The name of a property which represents a password.
         */
        const val PASSWORD = "PASSWORD"

        /**
         * The name of a property which indicate if password is persistent.
         */
        const val PASSWORD_PERSISTENT = "PASSWORD_PERSISTENT"

        /**
         * The name of a property which indicates dnssecMode i.e. disabled, needsDnssec or needsDnssecAndDane.
         */
        const val DNSSEC_MODE = "DNSSEC_MODE"

        /**
         * The name of a property representing the name of the protocol for an ProtocolProviderFactory.
         */
        const val PROTOCOL = "PROTOCOL_NAME"

        /**
         * The name of a property representing the path to protocol icons.
         */
        const val PROTOCOL_ICON_PATH = "PROTOCOL_ICON_PATH"

        /**
         * The name of a property representing the path to the account icon to be used in the user
         * interface, when the protocol provider service is not available.
         */
        const val ACCOUNT_ICON_PATH = "ACCOUNT_ICON_PATH"

        /**
         * The name of a property which represents the AccountID i.e. Entity BareJid (string) of a
         * ProtocolProvider and that, together with a password is used to login the protocol network.
         */
        const val USER_ID = "USER_ID"

        /**
         * The name that should be displayed to others when we are calling or writing them.
         */
        const val DISPLAY_NAME = "DISPLAY_NAME"

        /**
         * The name that should be displayed to the user on call via and chat via lists.
         */
        const val ACCOUNT_DISPLAY_NAME = "ACCOUNT_DISPLAY_NAME"

        /**
         * The unique identifier of the property under which we store protocol - AccountID accxxxxx...
         */
        const val ACCOUNT_UUID = "ACCOUNT_UUID"

        /**
         * The name of the property under which we store protocol AccountID - Jabber:abc123@atalk.org.
         */
        const val ACCOUNT_UID = "ACCOUNT_UID"

        /**
         * The name of the property under which we store CRYPTO keys information.
         */
        const val KEYS = "KEYS"

        /**
         * The options of the property under which we store protocol AccountID options.
         */
        // public static final String OPTIONS = "OPTIONS";
        // cmeng - xmpp domain setup with SRV record ???
        // custom xmpp domain to be resolved for SRV records used for connection.
        // e.g. one server (server1.domain.com) setup as "server1.domain.com", then DNS records is
        // setup as _xmpp-client._tcp.domain.com. IN SRV 0 5 5222 server1.domain.com.
        const val CUSTOM_XMPP_DOMAIN = "CUSTOM_XMPP_DOMAIN"

        /**
         * Indicates if the server settings are overridden
         */
        const val IS_SERVER_OVERRIDDEN = "IS_SERVER_OVERRIDDEN"

        /**
         * The name of the property under which we store protocol the address of a protocol centric
         * entity (any protocol server).
         */
        const val SERVER_ADDRESS = "SERVER_ADDRESS"

        /**
         * The name of the property under which we store the number of the port where the server stored
         * against the SERVER_ADDRESS property is expecting connections to be made via this protocol.
         */
        const val SERVER_PORT = "SERVER_PORT"

        /**
         * The name of the property under which we store the name of the transport protocol that needs
         * to be used to access the server.
         */
        const val SERVER_TRANSPORT = "SERVER_TRANSPORT"

        /**
         * Indicates if Proxy should be used.
         */
        const val IS_USE_PROXY = "PROXY_ENABLED"

        /**
         * Configures the URL which is to be used with BOSH transport.
         */
        const val BOSH_URL = "BOSH_URL"

        /**
         * Indicates HTTP proxy is enabled with BOSH protocol. Only HTTP proxy is allowwed with BOSH
         */
        const val BOSH_PROXY_HTTP_ENABLED = "BOSH_PROXY_HTTP_ENABLED"

        /**
         * The name of the property under which we store the the type of the proxy stored against the
         * PROXY_ADDRESS property. Exact type values depend on protocols and among them are socks4,
         * socks5, http and possibly others.
         */
        const val PROXY_TYPE = "PROXY_TYPE"

        /**
         * The name of the property under which we store protocol the address of a protocol proxy.
         */
        const val PROXY_ADDRESS = "PROXY_ADDRESS"

        /**
         * The name of the property under which we store the number of the port where the proxy stored
         * against the PROXY_ADDRESS property is expecting connections to be made via this protocol.
         */
        const val PROXY_PORT = "PROXY_PORT"

        /**
         * The name of the property under which we store the the username for the proxy stored against
         * the PROXY_ADDRESS property.
         */
        const val PROXY_USERNAME = "PROXY_USERNAME"

        /**
         * The name of the property under which we store the password for the proxy stored against the
         * PROXY_ADDRESS property.
         */
        const val PROXY_PASSWORD = "PROXY_PASSWORD"

        /**
         * The name of the property which defines whether proxy is auto configured by the protocol by
         * using known methods such as specific DNS queries.
         */
        const val PROXY_AUTO_CONFIG = "PROXY_AUTO_CONFIG"

        /**
         * The name of the property under which we store the name of the transport protocol that needs
         * to be used to access the proxy.
         */
        const val PROXY_TRANSPORT = "PROXY_TRANSPORT"

        /**
         * The name of the property that indicates whether loose routing should be forced for all
         * traffic in an account, rather than routing through an outbound proxy which is the default
         * for aTalk.
         */
        const val FORCE_PROXY_BYPASS = "FORCE_PROXY_BYPASS"

        /**
         * The name of the property under which we store the the authorization name for the proxy
         * stored against the PROXY_ADDRESS property.
         */
        const val AUTHORIZATION_NAME = "AUTHORIZATION_NAME"

        /**
         * The property indicating the preferred UDP and TCP port to bind to for clear communications.
         */
        const val PREFERRED_CLEAR_PORT_PROPERTY_NAME = "SIP_PREFERRED_CLEAR_PORT"

        /**
         * The property indicating the preferred TLS (TCP) port to bind to for secure communications.
         */
        const val PREFERRED_SECURE_PORT_PROPERTY_NAME = "SIP_PREFERRED_SECURE_PORT"

        /**
         * The name of the property under which we store the user preference for a transport
         * protocol to use (i.e. tcp or udp).
         */
        const val PREFERRED_TRANSPORT = "PREFERRED_TRANSPORT"

        /**
         * The name of the property under which we store whether we generate resource values or we just
         * use the stored one.
         */
        const val AUTO_GENERATE_RESOURCE = "AUTO_GENERATE_RESOURCE"

        /**
         * The name of the property under which we store resources such as the jabber resource property.
         */
        const val RESOURCE = "RESOURCE"

        /**
         * The name of the property under which we store resource priority.
         */
        const val RESOURCE_PRIORITY = "RESOURCE_PRIORITY"

        /**
         * The name of the property which defines that the call is encrypted by default
         */
        const val DEFAULT_ENCRYPTION = "DEFAULT_ENCRYPTION"

        /**
         * The name of the property that indicates the encryption protocols for this account.
         */
        const val ENCRYPTION_PROTOCOL = "ENCRYPTION_PROTOCOL"

        /**
         * The name of the property that indicates the status (enabled or disabled) encryption
         * protocols for this account.
         */
        const val ENCRYPTION_PROTOCOL_STATUS = "ENCRYPTION_PROTOCOL_STATUS"

        /**
         * The name of the property which defines if to include the ZRTP attribute to SIP/SDP
         */
        const val DEFAULT_SIPZRTP_ATTRIBUTE = "DEFAULT_SIPZRTP_ATTRIBUTE"

        /*
     * DTLS-SRTP TLS certificate signature algorithm e.g. SHA256withECDSA, SHA256withRSA
     */
        const val DTLS_CERT_SIGNATURE_ALGORITHM = "DTLS_CERT_SIGNATURE_ALGORITHM"

        /**
         * The name of the property which defines the ID of the client TLS certificate configuration entry.
         */
        const val CLIENT_TLS_CERTIFICATE = "CLIENT_TLS_CERTIFICATE"

        /**
         * The name of the property under which we store the boolean value indicating if the user name
         * should be automatically changed if the specified name already exists. This property is meant
         * to be used by IRC implementations.
         */
        const val AUTO_CHANGE_USER_NAME = "AUTO_CHANGE_USER_NAME"

        /**
         * The name of the property under which we store the boolean value indicating if a password is
         * required. Initially this property is meant to be used by IRC implementations.
         */
        const val NO_PASSWORD_REQUIRED = "NO_PASSWORD_REQUIRED"

        /**
         * The name of the property under which we store if the presence is enabled.
         */
        const val IS_PRESENCE_ENABLED = "IS_PRESENCE_ENABLED"

        /**
         * The name of the property under which we store if the p2p mode for SIMPLE should be forced.
         */
        const val FORCE_P2P_MODE = "FORCE_P2P_MODE"

        /**
         * The name of the property under which we store the offline contact polling period for SIMPLE.
         */
        const val POLLING_PERIOD = "POLLING_PERIOD"

        /**
         * The name of the property under which we store the chosen default subscription expiration
         * value for SIMPLE.
         */
        const val SUBSCRIPTION_EXPIRATION = "SUBSCRIPTION_EXPIRATION"

        /**
         * Indicates if the server address has been validated.
         */
        const val SERVER_ADDRESS_VALIDATED = "SERVER_ADDRESS_VALIDATED"

        /**
         * Indicates if the proxy address has been validated.
         */
        const val PROXY_ADDRESS_VALIDATED = "PROXY_ADDRESS_VALIDATED"

        /**
         * Indicates the search strategy chosen for the DICT protocol.
         */
        const val STRATEGY = "STRATEGY"

        /**
         * Indicates a protocol that would not be shown in the user interface as an account.
         */
        const val IS_PROTOCOL_HIDDEN = "IS_PROTOCOL_HIDDEN"

        /**
         * Indicates if the given account is the preferred account.
         */
        const val IS_PREFERRED_PROTOCOL = "IS_PREFERRED_PROTOCOL"

        /**
         * The name of the property that would indicate if a given account is currently enabled or disabled.
         */
        const val IS_ACCOUNT_DISABLED = "IS_ACCOUNT_DISABLED"

        /**
         * The name of the property that indicates if a given account needs InBand registration with the server
         */
        const val IBR_REGISTRATION = "IBR_REGISTRATION"

        /**
         * The name of the property that would indicate if a given account configuration form is currently hidden.
         */
        const val IS_ACCOUNT_CONFIG_HIDDEN = "IS_CONFIG_HIDDEN"

        /**
         * The name of the property that would indicate if a given account status menu is currently hidden.
         */
        const val IS_ACCOUNT_STATUS_MENU_HIDDEN = "IS_STATUS_MENU_HIDDEN"

        /**
         * The name of the property that would indicate if a given account configuration is read only.
         */
        const val IS_ACCOUNT_READ_ONLY = "IS_READ_ONLY"

        /**
         * The name of the property that would indicate if a given account groups are readonly, values
         * can be all or a comma separated group names including root.
         */
        const val ACCOUNT_READ_ONLY_GROUPS = "READ_ONLY_GROUPS"

        /**
         * Indicates if ICE should be used.
         */
        const val IS_USE_ICE = "ICE_ENABLED"

        /**
         * Indicates if UPnP should be used with ICE.
         */
        const val IS_USE_UPNP = "UPNP_ENABLED"

        /**
         * Indicates if STUN server should be automatically discovered.
         */
        const val AUTO_DISCOVER_STUN = "AUTO_DISCOVER_STUN"

        /**
         * Indicates if default STUN server would be used if no other STUN/TURN server are available.
         */
        const val USE_DEFAULT_STUN_SERVER = "USE_DEFAULT_STUN_SERVER"

        /**
         * The name of the boolean account property which indicates whether Jitsi
         * will use translator for media, instead of mixing, for conference calls.
         * By default if supported mixing is used (audio mixed, video relayed).
         */
        const val USE_TRANSLATOR_IN_CONFERENCE = "USE_TRANSLATOR_IN_CONFERENCE"

        /**
         * The property name prefix for all stun server properties. We generally use this prefix in
         * conjunction with an index which is how we store multiple servers.
         */
        const val STUN_PREFIX = "STUN"

        /**
         * The base property name for address of additional STUN servers specified.
         */
        const val STUN_ADDRESS = "STUN_ADDRESS"

        /**
         * The base property name for port of additional STUN servers specified.
         */
        const val STUN_PORT = "PORT"

        /**
         * The base property name for username of additional STUN servers specified.
         */
        const val STUN_USERNAME = "USERNAME"

        /**
         * The base property name for password of additional STUN servers specified.
         */
        const val STUN_PASSWORD = "PASSWORD"

        /**
         * The base property name for protocol of additional STUN servers specified.
         */
        const val STUN_TURN_PROTOCOL = "TURN_PROTOCOL"

        /**
         * The base property name for the turn supported property of additional STUN servers specified.
         */
        const val STUN_IS_TURN_SUPPORTED = "IS_TURN_SUPPORTED"

        /**
         * Indicates if JingleNodes should be used with ICE.
         */
        const val IS_USE_JINGLE_NODES = "JINGLE_NODES_ENABLED"

        /**
         * Indicates if JingleNodes should be used with ICE.
         */
        const val AUTO_DISCOVER_JINGLE_NODES = "AUTO_DISCOVER_JINGLE_NODES"

        /**
         * Indicates if JingleNodes should use buddies to search for nodes.
         */
        const val JINGLE_NODES_SEARCH_BUDDIES = "JINGLE_NODES_SEARCH_BUDDIES"

        /**
         * The name of the boolean account property which indicates whether Jitsi Videobridge is to be
         * used, if available and supported, for conference calls.
         */
        const val USE_JITSI_VIDEO_BRIDGE = "USE_JITSI_VIDEO_BRIDGE"

        /**
         * Minimum TLS protocol version.
         */
        const val MINUMUM_TLS_VERSION = "MINUMUM_TLS_VERSION"

        /**
         * Indicates if we allow non-TLS connection.
         */
        const val IS_ALLOW_NON_SECURE = "ALLOW_NON_SECURE"

        /**
         * Enable notifications for new voicemail messages.
         */
        const val VOICEMAIL_ENABLED = "VOICEMAIL_ENABLED"

        /**
         * Address used to reach voicemail box, by services able to subscribe for voicemail new messages notifications.
         */
        const val VOICEMAIL_URI = "VOICEMAIL_URI"

        /**
         * Address used to call to hear your messages stored on the server for your voicemail.
         */
        const val VOICEMAIL_CHECK_URI = "VOICEMAIL_CHECK_URI"

        /**
         * Indicates if calling is disabled for a certain account.
         */
        const val IS_CALLING_DISABLED_FOR_ACCOUNT = "CALLING_DISABLED"

        /**
         * Indicates if video calling is disabled for a certain account.
         */
        const val IS_VIDEO_CALLING_DISABLED_FOR_ACCOUNT = "VIDEO_CALLING_DISABLED"

        /**
         * Indicates if desktop streaming/sharing is disabled for a certain account.
         */
        const val IS_DESKTOP_STREAMING_DISABLED = "DESKTOP_STREAMING_DISABLED"

        /**
         * Indicates if desktop remote control is disabled for a certain account.
         */
        const val IS_DESKTOP_REMOTE_CONTROL_DISABLED = "DESKTOP_REMOTE_CONTROL_DISABLED"

        /**
         * The sms default server address.
         */
        const val SMS_SERVER_ADDRESS = "SMS_SERVER_ADDRESS"

        /**
         * Keep-alive method used by the protocol.
         */
        const val KEEP_ALIVE_METHOD = "KEEP_ALIVE_METHOD"

        /**
         * The keep-alive option enable / disable.
         */
        const val IS_KEEP_ALIVE_ENABLE = "IS_KEEP_ALIVE_ENABLE"

        /**
         * The Ping auto optimization option enable / disable.
         */
        const val IS_PING_AUTO_TUNE_ENABLE = "IS_PING_AUTO_TUNE_ENABLE"

        /**
         * The interval for keep-alive if any.
         */
        const val PING_INTERVAL = "PING_INTERVAL"

        /**
         * The name of the property holding DTMF method.
         */
        const val DTMF_METHOD = "DTMF_METHOD"

        /**
         * The minimal DTMF tone duration.
         */
        const val DTMF_MINIMAL_TONE_DURATION = "DTMF_MINIMAL_TONE_DURATION"

        /**
         * Paranoia mode when turned on requires all calls to be secure and indicated as such.
         */
        const val MODE_PARANOIA = "MODE_PARANOIA"

        /**
         * The name of the "override encodings" property
         */
        const val OVERRIDE_ENCODINGS = "OVERRIDE_ENCODINGS"

        /**
         * The prefix used to store account encoding properties
         */
        const val ENCODING_PROP_PREFIX = "Encodings"

        /**
         * An account property to provide a connected account to check for its status. Used when the
         * current provider need to reject calls but is missing presence operation set and need to
         * check other provider for status.
         */
        const val CUSAX_PROVIDER_ACCOUNT_PROP = "cusax.XMPP_ACCOUNT_ID"

        /**
         * The name of the property that indicates the AVP type.
         *
         *  * [.SAVP_OFF]
         *  * [.SAVP_MANDATORY]
         *  * [.SAVP_OPTIONAL]
         *
         */
        const val SAVP_OPTION = "SAVP_OPTION"

        /**
         * Always use RTP/AVP
         */
        const val SAVP_OFF = 0

        /**
         * Always use RTP/SAVP
         */
        const val SAVP_MANDATORY = 1

        /**
         * Sends two media description, with RTP/SAVP being first.
         */
        const val SAVP_OPTIONAL = 2

        /**
         * The name of the property that defines the enabled SDES cipher suites. Enabled suites are
         * listed as CSV by their RFC name.
         */
        const val SDES_CIPHER_SUITES = "SDES_CIPHER_SUITES"

        /**
         * The name of the property that defines the enabled/disabled state of message carbons.
         */
        const val IS_CARBON_DISABLED = "CARBON_DISABLED"

        /**
         * The name of the property that stores salt value for ZID computation.
         */
        const val ZID_SALT = "ZID_SALT"

        /**
         * Returns the prefix for all persistently stored properties of the account with the specified id.
         *
         * @param bundleContext a currently valid bundle context.
         * @param accountID the AccountID of the account whose properties we're looking for.
         * @param sourcePackageName a String containing the package name of the concrete factory class that extends us.
         * @return a String indicating the ConfigurationService property name prefix under which all
         * account properties are stored or null if no account corresponding to the specified id was found.
         */
        fun findAccountPrefix(bundleContext: BundleContext, accountID: AccountID, sourcePackageName: String): String? {
            val confReference = bundleContext.getServiceReference(ConfigurationService::class.java)
            val configurationService = bundleContext.getService(confReference)

            // first retrieve all accounts that we've registered
            val storedAccounts = configurationService.getPropertyNamesByPrefix(sourcePackageName, true)

            // find an account with the corresponding id.
            for (accountRootPropertyName in storedAccounts) {
                // unregister the account in the configuration service.
                // all the properties must have been registered in the following hierarchy:
                // net.java.sip.communicator.impl.protocol.PROTO_NAME.ACC_ID.PROP_NAME
                val accountUID = configurationService.getString("$accountRootPropertyName.$ACCOUNT_UID")
                // node idpropname
                if (accountID.accountUniqueID == accountUID) {
                    return accountRootPropertyName
                }
            }
            return null
        }

        /**
         * Finds registered `ProtocolProviderFactory` for given `protocolName`.
         *
         * @param bundleContext the OSGI bundle context that will be used.
         * @param protocolName the protocol name.
         * @return Registered `ProtocolProviderFactory` for given protocol name or `null`
         * if no provider was found.
         */
        fun getProtocolProviderFactory(bundleContext: BundleContext, protocolName: String): ProtocolProviderFactory? {
            val serRefs: Array<ServiceReference<*>>
            val osgiFilter = "(PROTOCOL_NAME=$protocolName)"
            serRefs = try {
                bundleContext.getServiceReferences(ProtocolProviderFactory::class.java.name, osgiFilter)
            } catch (ex: InvalidSyntaxException) {
                Timber.e(ex)
                return null
            }
            return bundleContext.getService<Any>(serRefs[0] as ServiceReference<Any>) as ProtocolProviderFactory
        }
    }
}