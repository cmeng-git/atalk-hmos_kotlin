/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.jabber

import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.hmos.gui.menu.MainMenuActivity
import org.atalk.service.neomedia.MediaService
import org.jivesoftware.smack.util.StringUtils
import org.jxmpp.util.XmppStringUtils
import org.osgi.framework.BundleContext
import java.io.Serializable

/**
 * The `JabberAccountRegistration` is used to store all user input data through the
 * `JabberAccountRegistrationWizard`.
 *
 * @author Yana Stamcheva
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class JabberAccountRegistration
/**
 * Initializes a new JabberAccountRegistration.
 */
    : JabberAccountID(null, HashMap()), Serializable {

    /**
     * The default domain.
     */
    private val defaultUserSuffix: String? = null

    /**
     * Indicates if the password should be remembered.
     */
    private var rememberPassword = true

    /**
     * UID of edited account in form: jabber:user@example.com
     */
    private var editedAccUID: String? = null

    /**
     * The list of additional STUN servers entered by user. The list is guaranteed not to be `null`.
     */
    val additionalStunServers = ArrayList<StunServerDescriptor>()

    /**
     * The `List` of all additional stun servers entered by the user.
     * The list is guaranteed not to be `null`.
     */
    val additionalJingleNodes = ArrayList<JingleNodeDescriptor>()

    /**
     * The encodings registration object
     */
    private val encodingsRegistration = EncodingsRegistrationUtil()

    /**
     * The security registration object
     */
    private val securityRegistration = object : SecurityAccountRegistration() {
        /**
         * Sets the method used for RTP/SAVP indication.
         */
        override fun setSavpOption(savpOption: Int) {
            // SAVP option is not useful for XMPP account. Thereby, do nothing.
        }

        /**
         * RTP/SAVP is disabled for Jabber protocol.
         *
         * @return Always `ProtocolProviderFactory.SAVP_OFF`.
         */
        override fun getSavpOption(): Int {
            return ProtocolProviderFactory.SAVP_OFF
        }
    }

    /**
     * Sets the User ID of the jabber registration account e.g. user@example.com.
     *
     * @param userID the identifier of the jabber registration account.
     */
    fun setUserID(userID: String) {
        setOrRemoveIfEmpty(ProtocolProviderFactory.USER_ID, userID)
    }

    /**
     * {@inheritDoc}
     */
    fun getUserID(): String? {
        return getAccountPropertyString(ProtocolProviderFactory.USER_ID)
    }

    /**
     * Returns TRUE if password has to remembered, FALSE otherwise.
     *
     * @return TRUE if password has to remembered, FALSE otherwise
     */
    fun isRememberPassword(): Boolean {
        return rememberPassword
    }

    /**
     * Sets the rememberPassword value of this jabber account registration.
     *
     * @param rememberPassword TRUE if password has to remembered, FALSE otherwise
     */
    fun setRememberPassword(rememberPassword: Boolean) {
        this.rememberPassword = rememberPassword
    }

    /**
     * Adds the given `stunServer` to the list of additional stun servers.
     *
     * @param stunServer the `StunServer` to add
     */
    fun addStunServer(stunServer: StunServerDescriptor) {
        additionalStunServers.add(stunServer)
    }

    /**
     * Adds the given `node` to the list of additional JingleNodes.
     *
     * @param node the `node` to add
     */
    fun addJingleNodes(node: JingleNodeDescriptor) {
        additionalJingleNodes.add(node)
    }

    /**
     * Returns `EncodingsRegistrationUtil` object which stores encodings configuration.
     *
     * @return `EncodingsRegistrationUtil` object which stores encodings configuration.
     */
    fun getEncodingsRegistration(): EncodingsRegistrationUtil {
        return encodingsRegistration
    }

    /**
     * Returns `SecurityAccountRegistration` object which stores security settings.
     *
     * @return `SecurityAccountRegistration` object which stores security settings.
     */
    fun getSecurityRegistration(): SecurityAccountRegistration {
        return securityRegistration
    }

    /**
     * Merge Jabber account configuration held by this registration account (after cleanup and updated with
     * new STUN/JN, Security and Encoding settings into the given `accountProperties` map.
     *
     * @param passWord the password for this account.
     * @param protocolIconPath the path to protocol icon if used, or `null` otherwise.
     * @param accountIconPath the path to account icon if used, or `null` otherwise.
     * @param accountProperties the map used for storing account properties.
     * @throws OperationFailedException if properties are invalid.
     */
    @Throws(OperationFailedException::class)
    fun storeProperties(
            factory: ProtocolProviderFactory, passWord: String?, protocolIconPath: String?,
            accountIconPath: String?, isModification: Boolean, accountProperties: MutableMap<String, String?>,
    ) {
        // Remove all the old account properties value before populating with modified or new default settings
        mAccountProperties.clear()
        password = if (rememberPassword) {
            passWord
        } else {
            null
        }

        // aTalk STUN/JN implementation can only be added/modified via account modification
        if (isModification) {
            var accountUuid: String? = null
            // cmeng - editedAccUID contains the last edited account e.g. jabber:xxx@atalk.org.
            if (StringUtils.isNotEmpty(editedAccUID)) {
                val accManager = ProtocolProviderActivator.accountManager
                accountUuid = accManager!!.getStoredAccountUUID(factory, editedAccUID!!)
            }
            if (accountUuid != null) {
                // Must remove all the old STUN/JN settings in database and old copies in accountProperties
                val configSrvc = ProtocolProviderActivator.getConfigurationService()
                val allProperties = configSrvc!!.getAllPropertyNames(accountUuid)
                for (property in allProperties!!) {
                    if (property!!.startsWith(ProtocolProviderFactory.STUN_PREFIX)
                            || property.startsWith(JingleNodeDescriptor.JN_PREFIX)) {
                        configSrvc.setProperty("$accountUuid.$property", null)
                    }
                }

                // Also must remove STUN/JN settings from this instance of accountProperties - otherwise remove will not work
                val accKeys = accountProperties.keys.toTypedArray()
                for (property in accKeys) {
                    if (property.startsWith(ProtocolProviderFactory.STUN_PREFIX)
                            || property.startsWith(JingleNodeDescriptor.JN_PREFIX)) {
                        accountProperties.remove(property)
                    }
                }
                val stunServers = additionalStunServers
                var serverIndex = -1

                for (stunServer in stunServers) {
                    serverIndex++
                    stunServer.storeDescriptor(mAccountProperties, ProtocolProviderFactory.STUN_PREFIX + serverIndex)
                }

                serverIndex = -1
                for (jnRelay in additionalJingleNodes) {
                    serverIndex++
                    jnRelay.storeDescriptor(mAccountProperties, JingleNodeDescriptor.JN_PREFIX + serverIndex)
                }
            }
        }
        // Must include other jabber account default/modified properties (ZRTP and Encoding) for account saving to DB
        securityRegistration.storeProperties(mAccountProperties)
        encodingsRegistration.storeProperties(mAccountProperties)
        super.storeProperties(protocolIconPath, accountIconPath, accountProperties)
    }

    /**
     * Fills this registration object with configuration properties from given `account`.
     *
     * @param account the account object that will be used.
     * @param bundleContext the OSGi bundle context required for some operations.
     */
    fun loadAccount(account: AccountID, bundleContext: BundleContext?) {
        // cmeng - both same ???
        mergeProperties(account.accountProperties, mAccountProperties)
        val password = ProtocolProviderFactory.getProtocolProviderFactory(bundleContext!!, ProtocolNames.JABBER)!!.loadPassword(account)
        mUserID = account.mUserID
        editedAccUID = account.accountUniqueID
        super.password = password
        // rememberPassword = (password != null);
        rememberPassword = account.isPasswordPersistent

        // Security properties
        securityRegistration.loadAccount(account)

        // ICE
        additionalStunServers.clear()
        for (i in 0 until StunServerDescriptor.MAX_STUN_SERVER_COUNT) {
            val stunServer = StunServerDescriptor.loadDescriptor(
                    mAccountProperties, ProtocolProviderFactory.STUN_PREFIX + i) ?: break

            // If we don't find a stun server with the given index, it means that there're no more
            // servers left in the table so we've nothing more to do here.
            val stunPassword = loadStunPassword(bundleContext, account, ProtocolProviderFactory.STUN_PREFIX + i)
            if (stunPassword != null) {
                stunServer.setPassword(stunPassword)
            }
            addStunServer(stunServer)
        }
        additionalJingleNodes.clear()
        for (i in 0 until JingleNodeDescriptor.MAX_JN_RELAY_COUNT) {
            val jn = JingleNodeDescriptor.loadDescriptor(mAccountProperties,
                    JingleNodeDescriptor.JN_PREFIX + i) ?: break

            // If we don't find a jingle server with the given index, it means that there is no
            // more servers left in the table so we've nothing more to do here.
            addJingleNodes(jn)
        }

        // Encodings
        if (!MainMenuActivity.disableMediaServiceOnFault) encodingsRegistration.loadAccount(account, ServiceUtils.getService(bundleContext, MediaService::class.java)!!)
    }

    /**
     * Parse the server part from the jabber id and set it to server as default value. If Advanced
     * option is enabled Do nothing.
     *
     * @param userName the full JID that we'd like to parse.
     * @return returns the server part of a full JID
     */
    private fun getServerFromUserName(userName: String?): String? {
        val newServerAddr = XmppStringUtils.parseDomain(userName)
        return if (StringUtils.isNotEmpty(newServerAddr)) {
            if (newServerAddr == GOOGLE_USER_SUFFIX) GOOGLE_CONNECT_SRV else newServerAddr
        } else null
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}