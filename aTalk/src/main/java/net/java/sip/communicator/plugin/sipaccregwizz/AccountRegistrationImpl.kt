/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.sipaccregwizz

import net.java.sip.communicator.service.gui.AccountRegistrationWizard
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.ProtocolNames
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.sip.SIPAccountRegistration
import org.osgi.framework.ServiceReference
import timber.log.Timber

/**
 * The `IPPIAccountRegistrationWizard` is an implementation of the
 * `AccountRegistrationWizard` for the SIP protocol. It should allow
 * the user to create and configure a new SIP account.
 *
 * @author Yana Stamcheva
 * @author Grigorii Balutsel
 * @author Eng Chong Meng
 */
class AccountRegistrationImpl : AccountRegistrationWizard() {
    /**
     * The protocol provider.
     */
    private var protocolProvider: ProtocolProviderService? = null
    var registration = SIPAccountRegistration()
        private set

    /**
     * Installs the account with the given user name and password.
     *
     * @param userName the account user name
     * @param password the password
     * @return the `ProtocolProviderService` corresponding to the newly created account.
     * @throws OperationFailedException problem signing in.
     */
    @Throws(OperationFailedException::class)
    fun signin(userName: String, password: String?): ProtocolProviderService? {
        val accountProperties = HashMap<String, String?>()
        return signin(userName, password, accountProperties)
    }

    /**
     * Installs the account with the given user name and password.
     *
     * @param userName the account user name
     * @param password the password
     * @return the `ProtocolProviderService` corresponding to the newly
     * created account.
     * @throws OperationFailedException problem signing in.
     */
    @Throws(OperationFailedException::class)
    override fun signin(userName: String, password: String?, accountProperties: MutableMap<String, String?>?): ProtocolProviderService? {
        var userName1 = userName
        if (userName1.startsWith("sip:")) userName1 = userName1.substring(4)
        val factory = SIPAccountRegistrationActivator.sipProtocolProviderFactory
        var pps: ProtocolProviderService? = null
        if (factory != null) pps = installAccount(factory, userName1, password, accountProperties)
        return pps
    }

    /**
     * Creates an account for the given user and password.
     *
     * @param providerFactory the ProtocolProviderFactory which will create
     * the account
     * @param userName the user identifier
     * @param passwd the password
     * @return the `ProtocolProviderService` for the new account.
     * @throws OperationFailedException problem installing account
     */
    @Throws(OperationFailedException::class)
    private fun installAccount(providerFactory: ProtocolProviderFactory, userName: String,
            passwd: String?, accountProperties: MutableMap<String, String?>?): ProtocolProviderService? {
        val protocolIconPath = protocolIconPath
        val accountIconPath = accountIconPath
        registration.storeProperties(userName, passwd, protocolIconPath, accountIconPath,
                isModification, accountProperties!!)
        if (isModification) {
            accountProperties[ProtocolProviderFactory.USER_ID] = userName
            providerFactory.modifyAccount(protocolProvider, accountProperties)
            isModification = false
            return protocolProvider
        }
        protocolProvider = try {
            val accountID = providerFactory.installAccount(userName, accountProperties)
            val serRef = providerFactory.getProviderForAccount(accountID!!)
            SIPAccountRegistrationActivator.bundleContext!!.getService(serRef as ServiceReference<ProtocolProviderService>)
        } catch (exc: IllegalStateException) {
            Timber.w("%s", exc.message)
            throw OperationFailedException("Account already exists.",
                    OperationFailedException.IDENTIFICATION_CONFLICT)
        } catch (exc: Exception) {
            Timber.w("%s", exc.message)
            throw OperationFailedException(exc.message, OperationFailedException.GENERAL_ERROR)
        }
        return protocolProvider
    }

    override val icon: ByteArray?
        get() = null

    override val pageImage: ByteArray? = null

    override val protocolName = ProtocolNames.SIP

    override val protocolDescription: String? = null

    override val userNameExample: String? = null

    override fun loadAccount(protocolProvider: ProtocolProviderService?) {
        isModification = true
        this.protocolProvider = protocolProvider
        registration = SIPAccountRegistration()
        val accountID = protocolProvider!!.accountID
        val password = SIPAccountRegistrationActivator.sipProtocolProviderFactory!!.loadPassword(accountID)

        // Loads account properties into registration object
        registration.loadAccount(accountID, password, SIPAccountRegistrationActivator.bundleContext)
    }

    override val firstPageIdentifier: Any? = null

    override val lastPageIdentifier: Any? = null

    override val summary: Iterator<Map.Entry<String, String>>? = null

    @Throws(OperationFailedException::class)
    override fun signin(): ProtocolProviderService? {
        return null
    }

    override fun getSimpleForm(isCreateAccount: Boolean): Any? {
        return null
    }

    /**
     * Returns the protocol icon path.
     *
     * @return the protocol icon path
     */
    private val protocolIconPath: String?
        get() = null

    /**
     * Returns the account icon path.
     *
     * @return the account icon path
     */
    private val accountIconPath: String?
        get() = null

    companion object {
        /**
         * Return the server part of the sip user name.
         *
         * @param userName the username.
         * @return the server part of the sip user name.
         */
        fun getServerFromUserName(userName: String): String? {
            val delimIndex = userName.indexOf("@")
            return if (delimIndex != -1) {
                userName.substring(delimIndex + 1)
            } else null
        }
    }
}