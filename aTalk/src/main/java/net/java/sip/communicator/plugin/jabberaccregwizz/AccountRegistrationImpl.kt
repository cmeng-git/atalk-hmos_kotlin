/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.jabberaccregwizz

import android.text.TextUtils
import android.util.Patterns
import net.java.sip.communicator.service.gui.AccountRegistrationWizard
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.ProtocolNames
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.jxmpp.util.XmppStringUtils
import org.osgi.framework.ServiceReference
import timber.log.Timber

/**
 * The `AccountRegistrationImpl` is an implementation of the `AccountRegistrationWizard` for the
 * Jabber protocol. It should allow the user to create and configure a new Jabber account.
 *
 * The method signin() is also called from JabberPreferenceFragment#doCommitChanges, with isModification set to true
 * to update the accountProperties DB with the preference changes by user
 *
 * @author Yana Stamcheva
 * @author Grigorii Balutsel
 * @author Eng Chong Meng
 */
class AccountRegistrationImpl : AccountRegistrationWizard() {
    /*
     * The protocol provider.
     */
    private var protocolProvider: ProtocolProviderService? = null
    var accountRegistration = JabberAccountRegistration()
        private set

    override val protocolName: String
        get() = ProtocolNames.JABBER

    /**
     * Install new or modify an account with the given user name and password;
     * pending on the flag isModification setting.
     *
     * @param userName the account user name
     * @param password the password
     * @param accountProperties additional account parameters for setting up new account/modify e.g. server and port
     * @return the `ProtocolProviderService` corresponding to the newly created account.
     * @throws OperationFailedException problem signing in.
     */
    @Throws(OperationFailedException::class)
    override fun signin(userName: String, password: String?, accountProperties: MutableMap<String, String?>?): ProtocolProviderService? {
        val factory = JabberAccountRegistrationActivator.jabberProtocolProviderFactory
        var pps: ProtocolProviderService? = null
        if (factory != null) pps = installAccount(factory, userName, password, accountProperties)
        return pps
    }

    /**
     * Create or modify an account for the given user, password and accountProperties pending isModification()
     *
     * @param providerFactory the ProtocolProviderFactory which will create the account
     * @param userName the user identifier
     * @param password the password
     * @param accountProperties additional account parameters for setting up new account/modify e.g. server and port
     * @return the `ProtocolProviderService` for the new account.
     * @throws OperationFailedException if the operation didn't succeed
     */
    @Throws(OperationFailedException::class)
    private fun installAccount(providerFactory: ProtocolProviderFactory,
            userName: String?, password: String?, accountProperties: MutableMap<String, String?>?): ProtocolProviderService? {
        // check for valid user account; will request password in actual login process if password is null
        if (TextUtils.isEmpty(userName) || !Patterns.EMAIL_ADDRESS.matcher(userName as CharSequence).matches()) {
            throw OperationFailedException("Should specify a valid user name and password "
                    + userName + ".", OperationFailedException.ILLEGAL_ARGUMENT)
        }
        Timber.log(TimberLog.FINER, "Preparing to install account for user %s", userName)

        // if server address is null, just extract it from userID even when server override option is set
        if (accountProperties!![ProtocolProviderFactory.SERVER_ADDRESS] == null) {
            val serverAddress = XmppStringUtils.parseDomain(userName)
            if (!TextUtils.isEmpty(serverAddress)) accountProperties[ProtocolProviderFactory.SERVER_ADDRESS] = serverAddress else throw OperationFailedException("Should specify a server for user name "
                    + userName + ".", OperationFailedException.SERVER_NOT_SPECIFIED)
        }
        // if server port is null, we will set default value
        if (accountProperties[ProtocolProviderFactory.SERVER_PORT] == null) {
            accountProperties[ProtocolProviderFactory.SERVER_PORT] = "5222"
        }

        // Add additional parameters to accountProperties
        accountProperties[ProtocolProviderFactory.IS_PREFERRED_PROTOCOL] = java.lang.Boolean.toString(isPreferredProtocol)
        accountProperties[ProtocolProviderFactory.PROTOCOL] = protocolName
        val protocolIconPath = protocolIconPath
        val accountIconPath = accountIconPath
        accountRegistration.storeProperties(providerFactory, password, protocolIconPath, accountIconPath,
                isModification, accountProperties)

        // Process account modification and return with the existing protocolProvider
        if (isModification) {
            providerFactory.modifyAccount(protocolProvider, accountProperties)
            isModification = false
            return protocolProvider
        }

        /* isModification() == false; Process to create new account and return the newly created protocolProvider */
        protocolProvider = try {
            Timber.i("Installing new account created for user %s", userName)
            val accountID = providerFactory.installAccount(userName, accountProperties)
            val serRef = providerFactory.getProviderForAccount(accountID!!)
            JabberAccountRegistrationActivator.bundleContext!!.getService(serRef)
        } catch (exc: IllegalArgumentException) {
            Timber.w("%s", exc.message)
            throw OperationFailedException("Username, password or server is null.",
                    OperationFailedException.ILLEGAL_ARGUMENT)
        } catch (exc: IllegalStateException) {
            Timber.w("%s", exc.message)
            throw OperationFailedException("Account already exists.",
                    OperationFailedException.IDENTIFICATION_CONFLICT)
        } catch (exc: Throwable) {
            Timber.w("%s", exc.message)
            throw OperationFailedException("Failed to add account.", OperationFailedException.GENERAL_ERROR)
        }
        return protocolProvider
    }// Check for preferred account through the PREFERRED_ACCOUNT_WIZARD property.
    //        String prefWName = JabberAccountRegistrationActivator.getResources().
    //            getSettingsString("gui.PREFERRED_ACCOUNT_WIZARD");
    //
    //        if(!TextUtils.isEmpty(prefWName) > 0 && prefWName.equals(this.getClass().getName()))
    //            return true;
    /**
     * Indicates if this wizard is for the preferred protocol. Currently on support XMPP, so always true
     *
     * @return `true` if this wizard corresponds to the preferred protocol, otherwise returns `false`
     */
    override val isPreferredProtocol: Boolean
        get() = true
        // Check for preferred account through the PREFERRED_ACCOUNT_WIZARD property.
        //        String prefWName = JabberAccountRegistrationActivator.getResources().
        //            getSettingsString("gui.PREFERRED_ACCOUNT_WIZARD");
        //
        //        if(!TextUtils.isEmpty(prefWName) > 0 && prefWName.equals(this.getClass().getName()))
                //            return true;
            

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

    @Throws(OperationFailedException::class)
    override fun signin(): ProtocolProviderService? {
        return null
    }

    override val icon: ByteArray?
        get() = null
    override val pageImage: ByteArray?
        get() = null
    override val protocolDescription: String?
        get() = null
    override val userNameExample: String?
        get() = null

    override fun loadAccount(protocolProvider: ProtocolProviderService?) {
        isModification = true
        this.protocolProvider = protocolProvider
        accountRegistration = JabberAccountRegistration()
        val accountID = protocolProvider!!.accountID

        // Loads account properties into registration object
        accountRegistration.loadAccount(accountID, JabberAccountRegistrationActivator.bundleContext)
    }

    override val firstPageIdentifier: Any?
        get() = null
    override val lastPageIdentifier: Any?
        get() = null
    override val summary: Iterator<Map.Entry<String, String>>?
        get() = null

    override fun getSimpleForm(isCreateAccount: Boolean): Any? {
        return null
    }

    override val isInBandRegistrationSupported: Boolean
        get() = true
}