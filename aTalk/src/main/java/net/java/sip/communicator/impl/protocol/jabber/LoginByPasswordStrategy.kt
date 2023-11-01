/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import net.java.sip.communicator.service.certificate.CertificateService
import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.login.IBRCaptchaProcessDialog
import org.jivesoftware.smack.AbstractXMPPConnection
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smack.bosh.BOSHConfiguration
import org.jivesoftware.smack.packet.StanzaError
import org.jxmpp.jid.parts.Resourcepart
import java.io.IOException
import java.security.GeneralSecurityException
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Login to Jabber using username & password.
 *
 * @author Stefan Sieber
 * @author Eng Chong Meng
 */
class LoginByPasswordStrategy
/**
 * Create a login strategy that logs in using user credentials (username and password)
 *
 * @param protocolProvider protocol provider service to fire registration change events.
 * @param accountID The accountID to use for the login.
 */
(private val protocolProvider: AbstractProtocolProviderService, private val accountID: AccountID,
        override val connectionConfigurationBuilder: ConnectionConfiguration.Builder<*, *>) : JabberLoginStrategy {
    private var password: String? = null

    /**
     * Loads the account passwords as preparation for the login.
     *
     * @param authority SecurityAuthority to obtain the password
     * @param reasonCode reasonCode why we're preparing for login
     * @param reason the reason descriptive text why we're preparing for login
     * @param isShowAlways `true` always show the credential prompt for user entry
     * @return UserCredentials in case they need to be cached for this session (i.e. password is not persistent)
     */
    override fun prepareLogin(authority: SecurityAuthority, reasonCode: Int, reason: String?, isShowAlways: Boolean): UserCredentials? {
        return loadPassword(authority, reasonCode, reason, isShowAlways)
    }

    /**
     * Determines whether the strategy is ready to perform the login.
     *
     * @return True when the password was successfully loaded.
     */
    override fun loginPreparationSuccessful(): Boolean {
        return password != null
    }

    /**
     * Performs the login on an XMPP connection using SASL PLAIN.
     *
     * @param connection The connection on which the login is performed.
     * @param userName The full Jid username for the login.
     * @param resource The XMPP resource.
     * @return always true.
     * @throws XMPPException xmppException
     */
    @Throws(XMPPException::class, SmackException::class)
    override fun login(connection: AbstractXMPPConnection, userName: String, resource: Resourcepart): Boolean {
        try {
            connection.login(userName, password, resource)
        } catch (ex: IOException) {
            // No response received within reply timeout. Timeout was 5000ms (~10s).
            // Rethrow XMPPException will trigger a re-login dialog
            val exMsg = ex.message
            val xmppErrorBuilder = StanzaError.from(StanzaError.Condition.not_authorized, exMsg)
            xmppErrorBuilder.setType(StanzaError.Type.CANCEL)
            throw XMPPErrorException(null, xmppErrorBuilder.build())
        } catch (ex: InterruptedException) {
            val exMsg = ex.message
            val xmppErrorBuilder = StanzaError.from(StanzaError.Condition.not_authorized, exMsg)
            xmppErrorBuilder.setType(StanzaError.Type.CANCEL)
            throw XMPPErrorException(null, xmppErrorBuilder.build())
        }
        return true
    }

    /**
     * Perform the InBand Registration for the accountId on the defined XMPP connection by pps.
     * Registration can either be:
     * - simple username and password or
     * - With captcha protection using form with embedded captcha image if available, else the
     * image is retrieved from the given url in the form.
     *
     * @param pps The protocolServiceProvider.
     * @param accountId The username accountID for registration.
     */
    override fun registerAccount(pps: ProtocolProviderServiceJabberImpl, accountId: AccountID): Boolean {
        // Wait for right moment before proceed, otherwise captcha dialog will be
        // obscured by other launching activities in progress on first aTalk launch.
        aTalkApp.waitForFocus()
        Handler(Looper.getMainLooper()).post {
            val context = aTalkApp.getCurrentActivity()
            if (context != null && pps.connection != null) {
                val mCaptchaDialog = IBRCaptchaProcessDialog(context, pps, accountId, password!!)
                mCaptchaDialog.show()
            }
        }
        return true
    }

    /*
     * Requires TLS by default (i.e. it will not connect to a non-TLS server and will not fallback to clear-text)
     * BOSH connection does not support TLS - return false always
     *
     * @see net.java.sip.communicator.impl.protocol.jabber.JabberLoginStrategy# isTlsRequired()
     */
    override var isTlsRequired = false
        get() {
            field = !accountID.getAccountPropertyBoolean(ProtocolProviderFactory.IS_ALLOW_NON_SECURE, false)
            return field && connectionConfigurationBuilder !is BOSHConfiguration.Builder
        }

    /**
     * Prepares an SSL Context that is customized SSL context.
     *
     * @param certificateService The certificate service that provides the context.
     * @param trustManager The TrustManager to use within the context.
     * @return An initialized context for the current provider.
     * @throws GeneralSecurityException
     */
    @Throws(GeneralSecurityException::class)
    override fun createSslContext(certificateService: CertificateService, trustManager: X509TrustManager): SSLContext? {
        return certificateService.getSSLContext(trustManager)
    }

    /**
     * Load the password from the account configuration or ask the user.
     *
     * @param authority SecurityAuthority
     * @param reasonCode the authentication reason code. Indicates the reason of this authentication.
     * @return The UserCredentials in case they should be cached for this session (i.e. are not persistent)
     */
    private fun loadPassword(authority: SecurityAuthority, reasonCode: Int, loginReason: String?, isShowAlways: Boolean): UserCredentials? {
        /*
         * Get the persistent password from the database if unavailable from accountID
         * Note: the last password entered by user is only available in accountID i.e mAccountProperties until
         * it is save in persistent database if enabled.
         */
        password = accountID.password
        if (TextUtils.isEmpty(password)) password = JabberActivator.protocolProviderFactory.loadPassword(accountID)

        if (password == null || isShowAlways) {
            // create a default credentials object
            var credentials = UserCredentials()
            credentials.userName = accountID.mUserID
            credentials.loginReason = loginReason
            // Update password is found in DB else leave password field empty
            if (password != null) {
                credentials.password = password!!.toCharArray()
            }

            // request account settings from the user and also show user default server option
            credentials = authority.obtainCredentials(accountID, credentials, reasonCode, true)

            // just return the bare credential in case user has canceled the login window
            if (credentials.isUserCancel) {
                protocolProvider.fireRegistrationStateChanged(
                        protocolProvider.registrationState, RegistrationState.UNREGISTERED,
                        RegistrationStateChangeEvent.REASON_USER_REQUEST, "User cancel credentials request")
                return credentials
            }

            // Extract the password the user entered. If no password specified, then canceled the operation
            val pass = credentials.password
            if (pass == null) {
                protocolProvider.fireRegistrationStateChanged(
                        protocolProvider.registrationState, RegistrationState.UNREGISTERED,
                        RegistrationStateChangeEvent.REASON_USER_REQUEST, "No password entered")
                return null
            }

            // update password for this instance and save the password in accountID for later use by login()
            password = String(pass)
            accountID.password = password

            val passwordPersistent = credentials.isPasswordPersistent
            accountID.isPasswordPersistent = passwordPersistent
            accountID.isIbRegistration = credentials.isIbRegistration
            accountID.dnssMode = credentials.dnssecMode

            accountID.isServerOverridden = credentials.isServerOverridden
            accountID.serverAddress = credentials.serverAddress
            if (credentials.isServerOverridden) {
                accountID.serverPort = credentials.serverPort
            }

            // must save to DB in case user makes changes to the Account parameters
            val ppFactory = JabberActivator.protocolProviderFactory
            val userId = credentials.userName
            // Must unload the old account and replace with new to allow Account Settings editing for the specific account ID
            if (!TextUtils.isEmpty(userId) && accountID.accountJid != userId) {
                ppFactory.unloadAccount(accountID)
                (accountID as JabberAccountIDImpl).updateJabberAccountID(credentials.userName)
                ppFactory.accountManager.modifyAccountId(accountID)
                ppFactory.loadAccount(accountID)
                // Let purge unused identitity to clean up. incase user change the mind
                // ((SQLiteOmemoStore) OmemoService.getInstance().getOmemoStoreBackend()).cleanUpOmemoDB();
            }
            ppFactory.storeAccount(accountID)
            return credentials
        } else {
            accountID.password = password
        }
        return null
    }
}