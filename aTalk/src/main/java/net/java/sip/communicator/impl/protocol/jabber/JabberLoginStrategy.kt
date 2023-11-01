/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.certificate.CertificateService
import net.java.sip.communicator.service.protocol.*
import org.jivesoftware.smack.AbstractXMPPConnection
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException
import org.jxmpp.jid.parts.Resourcepart
import java.io.IOException
import java.security.GeneralSecurityException
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Is responsible to configure the login mechanism for smack and later login to the XMPP server.
 *
 * @author Stefan Sieber
 * @author Eng Chong Meng
 */
internal interface JabberLoginStrategy {
    /**
     * Prepare the login by e.g. asking the user for his password.
     *
     * @param authority SecurityAuthority to obtain the password
     * @param reasonCode reason why we're preparing for login
     * @param reason the reason descriptive text why we're preparing for login
     * @param isShowAlways `true` always show the credential prompt for user entry
     * @return UserCredentials in case they need to be cached for this session (i.e. password is not persistent)
     * @see SecurityAuthority
     */
    fun prepareLogin(authority: SecurityAuthority, reasonCode: Int, reason: String?, isShowAlways: Boolean): UserCredentials?

    /**
     * Determines whether the login preparation was successful and the strategy
     * is ready to start connecting.
     *
     * @return true if prepareLogin was successful.
     */
    fun loginPreparationSuccessful(): Boolean

    /**
     * Performs the login for the specified connection.
     *
     * @param connection Connection to login
     * @param userName userName to be used for the login.
     * @param resource the XMPP resource
     * @return true to continue connecting, false to abort
     */
    @Throws(XMPPException::class, InterruptedException::class, IOException::class, SmackException::class)
    fun login(connection: AbstractXMPPConnection, userName: String, resource: Resourcepart): Boolean

    @Throws(XMPPException::class, SmackException::class)
    fun registerAccount(pps: ProtocolProviderServiceJabberImpl, accountId: AccountID): Boolean

    /**
     * Is TLS required for this login strategy / account?
     *
     * @return true if TLS is required
     */
    var isTlsRequired: Boolean

    /**
     * Creates an SSLContext to use for the login strategy.
     *
     * @param certificateService certificate service to retrieve the ssl context
     * @param trustManager Trust manager to use for the context
     * @return the SSLContext
     */
    @Throws(GeneralSecurityException::class)
    fun createSslContext(certificateService: CertificateService, trustManager: X509TrustManager): SSLContext?

    /**
     * Gets the connection configuration builder.
     *
     * @return The connection configuration builder configured for this login strategy.
     */
    val connectionConfigurationBuilder: ConnectionConfiguration.Builder<*, *>
}