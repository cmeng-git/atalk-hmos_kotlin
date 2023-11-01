/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.certificate

import java.security.GeneralSecurityException
import java.security.cert.Certificate
import java.security.cert.CertificateException
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * A service which implementors will ask the user for permission for the certificates which are
 * for some reason not valid and not globally trusted.
 *
 * @author Damian Minkov
 * @author Ingo Bauersachs
 * @author Eng Chong Meng
 */
interface CertificateService {
    // ------------------------------------------------------------------------
    // Client authentication configuration
    // ------------------------------------------------------------------------

    /**
     * Returns all saved [CertificateConfigEntry]s.
     *
     * @return List of the saved authentication configurations.
     */
    fun getClientAuthCertificateConfigs(): MutableList<CertificateConfigEntry>

    /**
     * Deletes a saved [CertificateConfigEntry].
     *
     * @param id The ID ([CertificateConfigEntry.getId]) of the entry to delete.
     */
    fun removeClientAuthCertificateConfig(id: String?)

    /**
     * Saves or updates the passed [CertificateConfigEntry] to the config. If
     * [CertificateConfigEntry.getId] returns null, a new entry is created.
     *
     * @param entry The @see CertificateConfigEntry to save or update.
     */
    fun setClientAuthCertificateConfig(entry: CertificateConfigEntry?)

    /**
     * Gets a list of all supported KeyStore types.
     *
     * @return a list of all supported KeyStore types.
     */
    fun getSupportedKeyStoreTypes(): List<KeyStoreType>
    // ------------------------------------------------------------------------
    // Certificate trust handling
    // ------------------------------------------------------------------------
    /**
     * Get an SSL Context that validates certificates based on the JRE default check and asks the
     * user when the JRE check fails.
     *
     *
     * CAUTION: Only the certificate itself is validated, no check is performed whether it is
     * valid for a specific server or client.
     *
     * @return An SSL context based on a user confirming trust manager.
     * @throws GeneralSecurityException a general security-related exception
     */
    @Throws(GeneralSecurityException::class)
    fun getSSLContext(): SSLContext?

    /**
     * Get an SSL Context with the specified trustManager.
     *
     * @param trustManager The trustManager that will be used by the created SSLContext
     * @return An SSL context based on the supplied trust manager.
     * @throws GeneralSecurityException a general security-related exception
     */
    @Throws(GeneralSecurityException::class)
    fun getSSLContext(trustManager: X509TrustManager?): SSLContext?

    /**
     * Get an SSL Context with the specified trustManager.
     *
     * @param clientCertConfig The ID of a client certificate configuration entry that is to be
     * used when the server asks for a client TLS certificate
     * @param trustManager The trustManager that will be used by the created SSLContext
     * @return An SSL context based on the supplied trust manager.
     * @throws GeneralSecurityException a general security-related exception
     */
    @Throws(GeneralSecurityException::class)
    fun getSSLContext(clientCertConfig: String?, trustManager: X509TrustManager?): SSLContext?

    /**
     * Get an SSL Context with the specified trustManager.
     *
     * @param keyManagers The key manager(s) to be used for client authentication
     * @param trustManager The trustManager that will be used by the created SSLContext
     * @return An SSL context based on the supplied trust manager.
     * @throws GeneralSecurityException a general security-related exception
     */
    @Throws(GeneralSecurityException::class)
    fun getSSLContext(keyManagers: Array<KeyManager?>?, trustManager: X509TrustManager?): SSLContext?

    /**
     * Creates a trustManager that validates the certificate based on the JRE default check and
     * asks the user when the JRE check fails. When `null` is passed as the
     * `identityToTest` then no check is performed whether the certificate is valid for a
     * specific server or client. The passed identities are checked by applying a behavior
     * similar to the on regular browsers use.
     *
     * @param identitiesToTest when not `null`, the values are assumed to be hostNames for invocations of
     * checkServerTrusted and e-mail addresses for invocations of checkClientTrusted
     * @return TrustManager to use in an SSLContext
     * @throws GeneralSecurityException a general security-related exception
     */
    @Throws(GeneralSecurityException::class)
    fun getTrustManager(identitiesToTest: Iterable<String?>?): X509TrustManager?

    /**
     * @param identityToTest when not `null`, the value is assumed to be a hostname for invocations of
     * checkServerTrusted and an e-mail address for invocations of checkClientTrusted
     * @return TrustManager to use in an SSLContext
     * @throws GeneralSecurityException a general security-related exception
     * @see .getTrustManager
     */
    @Throws(GeneralSecurityException::class)
    fun getTrustManager(identityToTest: String?): X509TrustManager?

    /**
     * @param identityToTest The identity to match against the supplied verifiers.
     * @param clientVerifier The verifier to use in calls to checkClientTrusted
     * @param serverVerifier The verifier to use in calls to checkServerTrusted
     * @return TrustManager to use in an SSLContext
     * @see .getTrustManager
     */
    @Throws(GeneralSecurityException::class)
    fun getTrustManager(identityToTest: String?,
            clientVerifier: CertificateMatcher?, serverVerifier: CertificateMatcher?): X509TrustManager?

    /**
     * Creates a trustManager that validates the certificate based on the JRE default check and
     * asks the user when the JRE check fails. When `null` is passed as the
     * `identityToTest` then no check is performed whether the certificate is valid for a
     * specific server or client.
     *
     * @param identitiesToTest The identities to match against the supplied verifiers.
     * @param clientVerifier The verifier to use in calls to checkClientTrusted
     * @param serverVerifier The verifier to use in calls to checkServerTrusted
     * @return TrustManager to use in an SSLContext
     * @throws GeneralSecurityException a general security-related exception
     */
    @Throws(GeneralSecurityException::class)
    fun getTrustManager(identitiesToTest: Iterable<String?>?,
            clientVerifier: CertificateMatcher?, serverVerifier: CertificateMatcher?): X509TrustManager?

    /**
     * Adds a certificate to the local trust store.
     *
     * @param cert The certificate to add to the trust store.
     * @param trustFor The certificate owner
     * @param trustMode Whether to trust the certificate permanently or only for the current session.
     * @throws CertificateException when the thumbprint could not be calculated
     */
    @Throws(CertificateException::class)
    fun addCertificateToTrust(cert: Certificate?, trustFor: String?, trustMode: Int)

    companion object {
        // ------------------------------------------------------------------------
        // Configuration property names
        // ------------------------------------------------------------------------
        /**
         * Property that is being applied to the system properties for PKIX TrustManager Support
         *
         * `com.sun.net.ssl.checkRevocation` and
         * `com.sun.security.enableCRLDP`
         * `ocsp.enable`
         */
        const val SECURITY_SSL_CHECK_REVOCATION = "com.sun.net.ssl.checkRevocation"
        const val SECURITY_CRLDP_ENABLE = "com.sun.security.enableCRLDP"
        const val SECURITY_OCSP_ENABLE = "ocsp.enable"

        /**
         * Property for always trust mode. When enabled certificate check is skipped.
         */
        const val PNAME_ALWAYS_TRUST = "gui.ALWAYS_TRUST_MODE_ENABLED"

        /**
         * When set to true, the certificate check is performed. If the check fails the user is not
         * asked and the error is directly reported to the calling service.
         */
        const val PNAME_NO_USER_INTERACTION = "tls.NO_USER_INTERACTION"

        /**
         * The property name prefix of all client authentication configurations.
         */
        const val PNAME_CLIENTAUTH_CERTCONFIG_BASE = "cert.clientauth"

        /**
         * Property that is being applied to the system property `javax.net.ssl.trustStoreType`
         */
        const val PNAME_TRUSTSTORE_TYPE = "cert.truststore.type"

        /**
         * Property that is being applied to the system property `javax.net.ssl.trustStore`
         */
        const val PNAME_TRUSTSTORE_FILE = "cert.truststore.file"

        /**
         * Property that is being applied to the system property `javax.net.ssl.trustStorePassword`
         */
        const val PNAME_TRUSTSTORE_PASSWORD = "cert.truststore.password"

        /**
         * Property that is being applied to the system properties
         * `com.sun.net.ssl.checkRevocation` and `com.sun.security.enableCRLDP`
         */
        const val PNAME_REVOCATION_CHECK_ENABLED = "cert.revocation.enabled"

        /**
         * Property that is being applied to the Security property `ocsp.enable`
         */
        const val PNAME_OCSP_ENABLED = "cert.ocsp.enabled"
        // ------------------------------------------------------------------------
        // constants
        // ------------------------------------------------------------------------
        /**
         * Result of user interaction. User does not trust this certificate.
         */
        const val DO_NOT_TRUST = 0

        /**
         * Result of user interaction. User will always trust this certificate.
         */
        const val TRUST_ALWAYS = 1

        /**
         * Result of user interaction. User will trust this certificate only for the current session.
         */
        const val TRUST_THIS_SESSION_ONLY = 2
    }
}