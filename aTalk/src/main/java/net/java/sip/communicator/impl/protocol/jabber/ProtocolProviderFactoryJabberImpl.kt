/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import android.text.TextUtils
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.ProtocolNames
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.jabber.JabberAccountID
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException
import timber.log.Timber

/**
 * The Jabber implementation of the ProtocolProviderFactory.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class ProtocolProviderFactoryJabberImpl
/**
 * Creates an instance of the ProtocolProviderFactoryJabberImpl.
 */
    : ProtocolProviderFactory(JabberActivator.bundleContext, ProtocolNames.JABBER) {
    /**
     * Overrides the original in order give access to specific protocol implementation.
     *
     * @param accountID the account identifier.
     */
    public override fun storeAccount(accountID: AccountID) {
        super.storeAccount(accountID)
    }

    /**
     * Initializes and creates an account corresponding to the specified accountProperties and
     * registers the resulting ProtocolProvider in the `context` BundleContext parameter.
     * This method has a persistent effect. Once created the resulting account will remain installed
     * until removed through the uninstall account method.
     *
     * @param userID the user identifier for the new account
     * @param accountProperties a set of protocol (or implementation) specific properties defining the new account.
     * @return the AccountID of the newly created account
     */
    @Throws(IllegalArgumentException::class, NullPointerException::class)
    override fun installAccount(userID: String?, accountProperties: MutableMap<String, String?>?): AccountID {
        val context = JabberActivator.bundleContext
                ?: throw NullPointerException("The specified BundleContext is null")
        requireNotNull(userID) { "The specified AccountID is null" }
        requireNotNull(accountProperties) { "The specified property map is null" }

        // Generate a new accountUuid for new account creation
        val accountUuid = AccountID.ACCOUNT_UUID_PREFIX + System.currentTimeMillis()
        accountProperties[ACCOUNT_UUID] = accountUuid
        val accountUID = "$protocolName:$userID"
        accountProperties[ACCOUNT_UID] = accountUID
        accountProperties[USER_ID] = userID

        // Create new accountID
        val accountID = JabberAccountIDImpl(userID, accountProperties)

        // make sure we haven't seen this accountID before.
        check(!registeredAccounts.containsKey(accountID)) { "Attempt to install an existing account: $userID" }

        // first store the account and only then load it as the load generates an osgi event, the
        // osgi event triggers (through the UI) a call to the register() method and it needs to
        // access the configuration service and check for a password.
        this.storeAccount(accountID, false)
        loadAccount(accountID)
        return accountID
    }

    /**
     * Create an account.
     *
     * @param userID the user ID
     * @param accountProperties the properties associated with the user ID
     * @return new `AccountID`
     */
    override fun createAccountID(userID: String, accountProperties: MutableMap<String, String?>): AccountID {
        return JabberAccountIDImpl(userID, accountProperties)
    }

    override fun createService(userID: String, accountID: AccountID): ProtocolProviderService? {
        val service = ProtocolProviderServiceJabberImpl()
        try {
            val jid = JidCreate.entityBareFrom(userID)
            service.initialize(jid, accountID as JabberAccountID)
            return service
        } catch (e: XmppStringprepException) {
            Timber.e(e, "%s is not a valid JID", userID)
        }
        return null
    }

    /**
     * Modify an existing account.
     *
     * @param protocolProvider the `ProtocolProviderService` responsible of the account
     * @param accountProperties modified properties to be set
     */
    @Throws(IllegalArgumentException::class, NullPointerException::class)
    override fun modifyAccount(protocolProvider: ProtocolProviderService?, accountProperties: MutableMap<String, String?>?) {
        val context = JabberActivator.bundleContext
                ?: throw NullPointerException("The specified BundleContext is null")
        requireNotNull(protocolProvider) { "The specified Protocol Provider is null" }

        // If the given accountID must be an existing account to modify, else return.
        val accountID = protocolProvider.accountID as JabberAccountIDImpl
        if (!registeredAccounts.containsKey(accountID)) return

        /*
         * Need to kill the protocolProvider service prior to making and account properties updates
         */
        val registration = registeredAccounts[accountID]
        if (registration != null) {
            try {
                // unregister provider before removing it.
                if (protocolProvider.isRegistered) {
                    protocolProvider.unregister()
                    protocolProvider.shutdown()
                }
                registration.unregister()
            } catch (e: Throwable) {
                // don't care as we are modifying and will unregister the service and will register again
                Timber.w("Exception in modifyAccount: %s", e.message)
            }
        }
        requireNotNull(accountProperties) { "The specified property map is null" }
        if (!accountProperties.containsKey(PROTOCOL)) accountProperties[PROTOCOL] = ProtocolNames.JABBER

        // If user has modified the UserId, then update the accountID parameters (userID, userBareJid and accountUID)
        // Must unload old account if account ID has changed
        val userId = accountProperties[USER_ID]
        if (!TextUtils.isEmpty(userId) && accountID.accountJid != userId) {
            unloadAccount(accountID)
            accountID.updateJabberAccountID(userId)
            accountManager.modifyAccountId(accountID)
            // Let purge unused identity to clean up. in case user change the mind
            // ((SQLiteOmemoStore) OmemoService.getInstance().getOmemoStoreBackend()).cleanUpOmemoDB();
        } else {
            accountProperties[USER_ID] = accountID.mUserID
        }

        // update the active accountID mAccountProperties with the modified accountProperties
        accountID.setAccountProperties(accountProperties)

        // First store the account and only then load it as the load generates an osgi event, the
        // osgi event triggers (through the UI) a call to the register() method and it needs to
        // access the configuration service and check for a password.
        this.storeAccount(accountID)
        val jid = accountID.bareJid!!.asEntityBareJidIfPossible()
        (protocolProvider as ProtocolProviderServiceJabberImpl).initialize(jid, accountID)

        // We store again the account in order to store all properties added during the protocol provider initialization.
        this.storeAccount(accountID)
        loadAccount(accountID) // to replace all below
        //        Hashtable<String, String> properties = new Hashtable<>();
//        properties.put(PROTOCOL, ProtocolNames.JABBER);
//        properties.put(USER_ID, accountID.getUserID());
//        registration = context.registerService(ProtocolProviderService.class.getName(), protocolProvider, properties);
//        registeredAccounts.put(accountID, registration);
    }

    companion object {
        /**
         * Indicates if ICE should be used.
         */
        const val IS_USE_JINGLE_NODES = "JINGLE_NODES_ENABLED"
    }
}