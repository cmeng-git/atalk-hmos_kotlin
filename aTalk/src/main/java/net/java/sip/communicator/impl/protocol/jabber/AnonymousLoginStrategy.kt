/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.certificate.CertificateService
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.SecurityAuthority
import net.java.sip.communicator.service.protocol.UserCredentials
import org.jivesoftware.smack.AbstractXMPPConnection
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException
import org.jxmpp.jid.parts.Resourcepart
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.*
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Implements anonymous login strategy for the purpose of some server side technologies. This makes
 * not much sense to be used with Jitsi directly.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 * @see JabberAccountIDImpl.ANONYMOUS_AUTH
 */
class AnonymousLoginStrategy(login: String?, ccBuilder: ConnectionConfiguration.Builder<*, *>) : JabberLoginStrategy {
    /**
     * `UserCredentials` used by accompanying services.
     */
    private val credentials = UserCredentials()
    override val connectionConfigurationBuilder: ConnectionConfiguration.Builder<*, *>

    /**
     * Creates new anonymous login strategy instance.
     *
     * @param login user login only for the purpose of returning `UserCredentials` that are used by
     * accompanying services.
     */
    init {
        connectionConfigurationBuilder = ccBuilder
        credentials.userName = login

        // FIXME: consider including password for TURN authentication ?
        credentials.password = charArrayOf()
    }

    override fun prepareLogin(authority: SecurityAuthority, reasonCode: Int, reason: String?, isShowAlways: Boolean): UserCredentials {
        return credentials
    }

    override fun loginPreparationSuccessful(): Boolean {
        connectionConfigurationBuilder.performSaslAnonymousAuthentication()
        return true
    }

    @Throws(XMPPException::class, InterruptedException::class, IOException::class, SmackException::class)
    override fun login(connection: AbstractXMPPConnection, userName: String, resource: Resourcepart): Boolean {
        connection.login()
        return true
    }

    @Throws(XMPPException::class, SmackException::class)
    override fun registerAccount(pps: ProtocolProviderServiceJabberImpl, accountId: AccountID): Boolean {
        return true
    }

    override var isTlsRequired = false

    @Throws(GeneralSecurityException::class)
    override fun createSslContext(certificateService: CertificateService, trustManager: X509TrustManager): SSLContext? {
        return Objects.requireNonNull(certificateService.getSSLContext(trustManager))
    }
}