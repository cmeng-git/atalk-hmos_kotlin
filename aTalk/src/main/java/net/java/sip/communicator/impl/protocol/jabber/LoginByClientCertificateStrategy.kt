/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.certificate.CertificateService
import net.java.sip.communicator.service.protocol.*
import org.jivesoftware.smack.*
import org.jivesoftware.smack.sasl.SASLMechanism
import org.jivesoftware.smack.sasl.provided.SASLExternalMechanism
import org.jxmpp.jid.parts.Resourcepart
import timber.log.Timber
import java.io.IOException
import java.security.GeneralSecurityException
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Login to Jabber using a client certificate (as defined in the account configuration)
 *
 * @author Stefan Sieber
 * @author Eng Chong Meng
 */
internal class LoginByClientCertificateStrategy
/**
 * Creates a new instance of this class.
 *
 * @param accountID The account to use for the strategy.
 * ccBuilder ConnectionConfiguration.Builder
 */
(private val accountID: AccountID, override val connectionConfigurationBuilder: ConnectionConfiguration.Builder<*, *>) : JabberLoginStrategy {

    /**
     * Does nothing.
     *
     * @param authority unused
     * @param reasonCode unused
     * @return always `null`
     */
    override fun prepareLogin(authority: SecurityAuthority, reasonCode: Int, reason: String?, isShowAlways: Boolean): UserCredentials? {
        // password is retrieved later when opening the key store.
        return null
    }

    /**
     * Does nothing.
     *
     * @return always `true`
     */
    override fun loginPreparationSuccessful(): Boolean {
        connectionConfigurationBuilder.allowEmptyOrNullUsernames()
                .setSecurityMode(ConnectionConfiguration.SecurityMode.required)
                .addEnabledSaslMechanism(SASLMechanism.EXTERNAL)
        return true
    }

    /**
     * Always true as the authentication occurs with the TLS client certificate.
     *
     * @return always `true`
     */
    override var isTlsRequired = true

    /**
     * Creates the SSLContext for the XMPP connection configured with a customized TrustManager and
     * a KeyManager based on the selected client certificate.
     *
     * @param certificateService certificate service to retrieve the SSL context
     * @param trustManager Trust manager to use for the context
     * @return Configured and initialized SSL Context
     * @throws GeneralSecurityException Security Exception
     */
    @Throws(GeneralSecurityException::class)
    override fun createSslContext(certificateService: CertificateService, trustManager: X509TrustManager): SSLContext? {
        val certConfigName = accountID.tlsClientCertificate
        return certificateService.getSSLContext(certConfigName, trustManager)
    }

    /**
     * Performs the login on the XMPP connection using the SASL EXTERNAL mechanism.
     *
     * @param connection The connection on which the login is performed.
     * @param userName The username for the login.
     * @param resource The XMPP resource.
     * @return true when the login succeeded, false when the certificate wasn't accepted.
     * @throws XMPPException Exception
     */
    @Throws(XMPPException::class, SmackException::class)
    override fun login(connection: AbstractXMPPConnection, userName: String, resource: Resourcepart): Boolean {
        SASLAuthentication.registerSASLMechanism(SASLExternalMechanism())

        // user/password MUST be empty. In fact they shouldn't be necessary at all
        // because the user name is derived from the client certificate.
        return try {
            try {
                connection.login("", "", resource)
            } catch (e: IOException) {
                Timber.e("Certificate login failed: %s", e.message)
            } catch (e: InterruptedException) {
                Timber.e("Certificate login failed: %s", e.message)
            }
            true
        } catch (ex: XMPPException) {
            if (ex.message!!.contains("EXTERNAL failed: not-authorized")) {
                Timber.e("Certificate login failed: %s", ex.message)
                return false
            }
            throw ex
        } catch (ex: SmackException) {
            if (ex.message!!.contains("EXTERNAL failed: not-authorized")) {
                Timber.e("Certificate login failed: %s", ex.message)
                return false
            }
            throw ex
        }
    }

    @Throws(XMPPException::class, SmackException::class)
    override fun registerAccount(pps: ProtocolProviderServiceJabberImpl, accountId: AccountID): Boolean {
        return false
    }
}