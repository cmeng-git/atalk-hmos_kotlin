/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.impl.certificate

import android.os.Build
import androidx.annotation.RequiresApi
import net.java.sip.communicator.service.certificate.CertificateConfigEntry
import net.java.sip.communicator.service.certificate.CertificateMatcher
import net.java.sip.communicator.service.certificate.CertificateService
import net.java.sip.communicator.service.certificate.KeyStoreType
import org.apache.http.conn.ssl.SSLSocketFactory
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.service.httputil.HttpConnectionManager.open
import org.atalk.util.OSUtils
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.x509.AccessDescription
import org.bouncycastle.asn1.x509.AuthorityInformationAccess
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import timber.log.Timber
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.UnrecoverableEntryException
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import javax.security.auth.callback.Callback
import javax.security.auth.callback.CallbackHandler
import javax.security.auth.callback.PasswordCallback
import javax.security.auth.callback.UnsupportedCallbackException
import kotlin.math.abs

/**
 * Implementation of the CertificateService. It asks the user to trust a certificate when
 * the automatic verification fails.
 *
 * @author Ingo Bauersachs
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class CertificateServiceImpl : CertificateService, PropertyChangeListener {
    // ------------------------------------------------------------------------
    // static data
    // ------------------------------------------------------------------------
    private val supportedTypes = object : LinkedList<KeyStoreType>() {
        /**
         * Serial version UID.
         */
        private val serialVersionUID = 0L

        init {
            if (!OSUtils.IS_WINDOWS64) {
                add(KeyStoreType("PKCS11", arrayOf(".dll", ".so"), false))
            }
            add(KeyStoreType("PKCS12", arrayOf(".p12", ".pfx"), true))
            add(KeyStoreType(KeyStore.getDefaultType(), arrayOf(".ks", ".jks"), true))
        }
    }

    // ------------------------------------------------------------------------
    // services
    // ------------------------------------------------------------------------
    private val config = CertificateVerificationActivator.configurationService
    private val credService = CertificateVerificationActivator.getCredService()
    // ------------------------------------------------------------------------
    // fields
    // ------------------------------------------------------------------------
    /**
     * Stores the certificates that are trusted as long as this service lives.
     */
    private val sessionAllowedCertificates = HashMap<String, MutableList<String>>()

    /**
     * Caches retrievals of AIA information (downloaded certs or failures).
     */
    private val aiaCache = HashMap<URI, AiaCacheEntry>()
    // ------------------------------------------------------------------------
    // Map access helpers
    // ------------------------------------------------------------------------
    /**
     * Helper method to avoid accessing null-lists in the session allowed certificate map
     *
     * @param propName the key to access
     * @return the list for the given list or a new, empty list put in place for the key
     */
    private fun getSessionCertEntry(propName: String): MutableList<String> {
        var entry = sessionAllowedCertificates[propName]
        if (entry == null) {
            entry = LinkedList()
            sessionAllowedCertificates[propName] = entry
        }
        return entry
    }

    fun purgeSessionCertificate() {
        sessionAllowedCertificates.clear()
    }

    /**
     * AIA cache retrieval entry.
     */
    private class AiaCacheEntry(var cacheDate: Date, var cert: X509Certificate?)
    // ------------------------------------------------------------------------
    // TrustStore configuration
    // ------------------------------------------------------------------------
    /**
     * Initializes a new `CertificateServiceImpl` instance.
     */
    init {
        setTrustStore()
        config!!.addPropertyChangeListener(CertificateService.PNAME_TRUSTSTORE_TYPE, this)
        System.setProperty(CertificateService.SECURITY_CRLDP_ENABLE, config.getString(CertificateService.PNAME_REVOCATION_CHECK_ENABLED, "false"))
        System.setProperty(CertificateService.SECURITY_SSL_CHECK_REVOCATION, config.getString(CertificateService.PNAME_REVOCATION_CHECK_ENABLED, "false"))
        Security.setProperty(CertificateService.SECURITY_OCSP_ENABLE, config.getString(CertificateService.PNAME_OCSP_ENABLED, "false"))
    }

    override fun propertyChange(evt: PropertyChangeEvent) {
        setTrustStore()
    }

    private fun setTrustStore() {
        var tsType = config!!.getProperty(CertificateService.PNAME_TRUSTSTORE_TYPE) as String?
        val tsFile = config.getProperty(CertificateService.PNAME_TRUSTSTORE_FILE) as String?
        val tsPassword = credService!!.loadPassword(CertificateService.PNAME_TRUSTSTORE_PASSWORD)

        // use the OS store as default store on Windows
        if ("meta:default" != tsType && OSUtils.IS_WINDOWS) {
            tsType = "Windows-ROOT"
            config.setProperty(CertificateService.PNAME_TRUSTSTORE_TYPE, tsType)
        }
        if (tsType != null && "meta:default" != tsType) System.setProperty("javax.net.ssl.trustStoreType", tsType) else System.getProperties().remove("javax.net.ssl.trustStoreType")
        if (tsFile != null) System.setProperty("javax.net.ssl.trustStore", tsFile) else System.getProperties().remove("javax.net.ssl.trustStore")
        if (tsPassword != null) System.setProperty("javax.net.ssl.trustStorePassword", tsPassword) else System.getProperties().remove("javax.net.ssl.trustStorePassword")
    }
    // ------------------------------------------------------------------------
    // Client authentication configuration
    // ------------------------------------------------------------------------
    /**
     * (non-Javadoc)
     *
     * @see CertificateService.getSupportedKeyStoreTypes
     */
    override fun getSupportedKeyStoreTypes(): List<KeyStoreType> {
        return supportedTypes
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService.getClientAuthCertificateConfigs
     */
    override fun getClientAuthCertificateConfigs(): MutableList<CertificateConfigEntry> {
        val map = LinkedList<CertificateConfigEntry>()
        for (propName in config!!.getPropertyNamesByPrefix(CertificateService.PNAME_CLIENTAUTH_CERTCONFIG_BASE, false)) {
            val propValue = config.getString(propName)
            if (propValue == null || !propName.endsWith(propValue)) continue
            val pnBase = CertificateService.PNAME_CLIENTAUTH_CERTCONFIG_BASE + "." + propValue
            val entry = CertificateConfigEntry(null)
            entry.id = propValue
            entry.alias = config.getString("$pnBase.alias")
            entry.displayName = config.getString("$pnBase.displayName")!!
            entry.keyStore = config.getString("$pnBase.keyStore")
            entry.isSavePassword = config.getBoolean("$pnBase.savePassword", false)
            if (entry.isSavePassword) {
                entry.keyStorePassword = credService!!.loadPassword(pnBase)
            }
            val type = config.getString("$pnBase.keyStoreType")
            for (kt in getSupportedKeyStoreTypes()) {
                if (kt.name == type) {
                    entry.keyStoreType = kt
                    break
                }
            }
            map.add(entry)
        }
        return map
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService.setClientAuthCertificateConfig
     */
    override fun setClientAuthCertificateConfig(entry: CertificateConfigEntry?) {
        if (entry!!.id == null) entry.id = "conf" + abs(Random().nextInt())
        val pn = CertificateService.PNAME_CLIENTAUTH_CERTCONFIG_BASE + "." + entry.id
        config!!.setProperty(pn, entry.id)
        config.setProperty("$pn.alias", entry.alias)
        config.setProperty("$pn.displayName", entry.displayName)
        config.setProperty("$pn.keyStore", entry.keyStore)
        config.setProperty("$pn.savePassword", entry.isSavePassword)
        if (entry.isSavePassword) credService!!.storePassword(pn, entry.keyStorePassword) else credService!!.removePassword(pn)
        config.setProperty("$pn.keyStoreType", entry.keyStoreType)
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService.removeClientAuthCertificateConfig
     */
    override fun removeClientAuthCertificateConfig(id: String?) {
        for (p in config!!.getPropertyNamesByPrefix(CertificateService.PNAME_CLIENTAUTH_CERTCONFIG_BASE + "." + id, true)) {
            config.removeProperty(p)
        }
        config.removeProperty(CertificateService.PNAME_CLIENTAUTH_CERTCONFIG_BASE + "." + id)
    }
    // ------------------------------------------------------------------------
    // Certificate trust handling
    // ------------------------------------------------------------------------
    /**
     * (non-Javadoc)
     *
     * @see CertificateService.addCertificateToTrust
     */
    @Throws(CertificateException::class)
    override fun addCertificateToTrust(cert: Certificate?, trustFor: String?, trustMode: Int) {
        val propName = PNAME_CERT_TRUST_PREFIX + CERT_TRUST_PARAM_SUBFIX + trustFor
        val thumbprint = getThumbprint(cert, THUMBPRINT_HASH_ALGORITHM)
        when (trustMode) {
            CertificateService.DO_NOT_TRUST -> throw IllegalArgumentException("Cannot add a certificate to trust when no trust is requested.")
            CertificateService.TRUST_ALWAYS -> {
                val current = config!!.getString(propName)
                if (!current!!.contains(thumbprint)) {
                    var newValue = thumbprint
                    newValue += ",$current"
                    config.setProperty(propName, newValue)
                }
            }
            CertificateService.TRUST_THIS_SESSION_ONLY -> getSessionCertEntry(propName).add(thumbprint)
        }
    }

    /**
     * Fetch all the server authenticated certificates
     *
     * @return all the server authenticated certificates
     */
    val allServerAuthCertificates: MutableList<String>
        get() = config!!.getPropertyNamesByPrefix(PNAME_CERT_TRUST_PREFIX, false)

    /**
     * Remove server certificate for the given certEntry
     *
     * @param certEntry to be removed
     */
    fun removeCertificateEntry(certEntry: String) {
        config!!.setProperty(certEntry, null)
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService.getSSLContext
     */
    @Throws(GeneralSecurityException::class)
    override fun getSSLContext(): SSLContext? {
        return getSSLContext(getTrustManager(null as Iterable<String?>?))
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService.getSSLContext
     */
    @Throws(GeneralSecurityException::class)
    override fun getSSLContext(trustManager: X509TrustManager?): SSLContext? {
        return try {
            val ks = KeyStore.getInstance(System.getProperty("javax.net.ssl.keyStoreType",
                    KeyStore.getDefaultType()))
            val kmFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            val keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword")
            if (System.getProperty("javax.net.ssl.keyStore") != null) {
                ks.load(FileInputStream(System.getProperty("javax.net.ssl.keyStore")), null)
            } else {
                ks.load(null, null)
            }
            kmFactory.init(ks, keyStorePassword?.toCharArray())
            getSSLContext(kmFactory.keyManagers, trustManager)
        } catch (e: Exception) {
            throw GeneralSecurityException("Cannot init SSLContext", e)
        }
    }

    @Throws(KeyStoreException::class, UnrecoverableEntryException::class)
    private fun loadKeyStore(entry: CertificateConfigEntry): KeyStore.Builder {
        val f = File(entry.keyStore!!)
        val keyStoreType = entry.keyStoreType!!.name
        if ("PKCS11" == keyStoreType) {
            val config = "name=${f.name}\nlibrary=${f.absoluteFile}"
            try {
                val pkcs11c = Class.forName("sun.security.pkcs11.SunPKCS11")
                val c = pkcs11c.getConstructor(InputStream::class.java)
                val p = c.newInstance(ByteArrayInputStream(config.toByteArray())) as Provider
                Security.insertProviderAt(p, 0)
            } catch (e: Exception) {
                Timber.e("Access PKCS11 provider on an unsupported platform or the load failed: %s", e.message)
            }
        }
        return KeyStore.Builder.newInstance(keyStoreType, null, f,
                KeyStore.CallbackHandlerProtection(CallbackHandler { callbacks: Array<Callback?> ->
                    for (cb in callbacks) {
                        if (cb !is PasswordCallback) throw UnsupportedCallbackException(cb)
                        if (entry.isSavePassword) {
                            cb.password = entry.getKSPassword()
                            return@CallbackHandler
                        } else {
                            val authenticationWindowService = CertificateVerificationActivator.authenticationWindowService
                            if (authenticationWindowService == null) {
                                Timber.e("No AuthenticationWindowService implementation")
                                throw IOException("User cancel")
                            }
                            val aw = authenticationWindowService.create(f.name, null, keyStoreType, false,
                                    false, null, null, null, null, null, null, null
                            )
                            aw!!.isAllowSavePassword =true
                            aw.setVisible(true)
                            if (!aw.isCanceled) {
                                cb.password = aw.password
                                entry.keyStorePassword = String(aw.password!!)
                            } else throw IOException("User cancel")
                        }
                    }
                }))
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService.getSSLContext
     */
    @Throws(GeneralSecurityException::class)
    override fun getSSLContext(clientCertConfig: String?, trustManager: X509TrustManager?): SSLContext? {
        return try {
            if (clientCertConfig == null) return getSSLContext(trustManager)
            var entry: CertificateConfigEntry? = null
            for (e in getClientAuthCertificateConfigs()) {
                if (e.toString() == clientCertConfig) {
                    entry = e
                    break
                }
            }
            if (entry == null) {
                throw GeneralSecurityException("Client certificate config with id <$clientCertConfig> not found.")
            }
            val clientKeyStore = loadKeyStore(entry).keyStore
            val clientKeyStorePass = entry.getKSPassword()

            // cmeng: "NewSunX509" or "SunX509": NoSuchAlgorithmException: SunX509 KeyManagerFactory not available
            // final KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509"); OR "NewSunX509"

            // Not supported: GeneralSecurityException: Cannot init SSLContext: ManagerFactoryParameters not supported
            // KeyStoreBuilderParameters ksBuilerParm = new KeyStoreBuilderParameters(loadKeyStore(entry));
            // kmf.init(ksBuilerParm);

            // so use getDefaultAlgorithm() => "PKIX"
            val kmfAlgorithm = KeyManagerFactory.getDefaultAlgorithm()
            val kmf = KeyManagerFactory.getInstance(kmfAlgorithm)
            kmf.init(clientKeyStore, clientKeyStorePass)
            val kms = kmf.keyManagers
            getSSLContext(kms, trustManager)
        } catch (e: Exception) {
            throw GeneralSecurityException("Cannot init SSLContext: " + e.message, e)
        }
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService.getSSLContext
     */
    @Throws(GeneralSecurityException::class)
    override fun getSSLContext(keyManagers: Array<KeyManager?>?, trustManager: X509TrustManager?): SSLContext? {
        // cmeng: need to take care o daneVerifier? from abstractXMPPConnection#getSmackTlsContext()
        // if (daneVerifier != null) {
        //     // User requested DANE verification.
        //     daneVerifier.init(context, kms, customTrustManager, secureRandom);
        // }
        return try {
            val secureRandom = SecureRandom()
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(keyManagers, arrayOf<TrustManager?>(trustManager), secureRandom)
            sslContext
        } catch (e: Exception) {
            throw GeneralSecurityException("Cannot init SSLContext", e)
        }
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService.getTrustManager
     */
    @Throws(GeneralSecurityException::class)
    override fun getTrustManager(identitiesToTest: Iterable<String?>?): X509TrustManager {
        return getTrustManager(identitiesToTest, EMailAddressMatcher(), BrowserLikeHostnameMatcher())
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService.getTrustManager
     */
    @Throws(GeneralSecurityException::class)
    override fun getTrustManager(identityToTest: String?): X509TrustManager {
        return getTrustManager(listOf(identityToTest),
                EMailAddressMatcher(), BrowserLikeHostnameMatcher())
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService.getTrustManager
     */
    @Throws(GeneralSecurityException::class)
    override fun getTrustManager(identityToTest: String?,
            clientVerifier: CertificateMatcher?, serverVerifier: CertificateMatcher?): X509TrustManager {
        return getTrustManager(listOf(identityToTest), clientVerifier, serverVerifier)
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService.getTrustManager
     */
    @Throws(GeneralSecurityException::class)
    override fun getTrustManager(identitiesToTest: Iterable<String?>?,
            clientVerifier: CertificateMatcher?, serverVerifier: CertificateMatcher?): X509TrustManager {
        // Obtain the system default X509 trust manager
        var defaultTm: X509TrustManager? = null
        val tmFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        // TrustManagerFactory tmFactory = TrustManagerFactory.getInstance("X509");

        // Workaround for https://bugs.openjdk.java.net/browse/JDK-6672015
        var ks: KeyStore? = null
        val tsType = System.getProperty("javax.net.ssl.trustStoreType", null)
        if ("Windows-ROOT" == tsType) {
            try {
                ks = KeyStore.getInstance(tsType)
                ks.load(null, null)
            } catch (e: Exception) {
                Timber.e(e, "Could not rename Windows-ROOT aliases")
            }
        }
        tmFactory.init(ks)
        for (m in tmFactory.trustManagers) {
            if (m is X509TrustManager) {
                defaultTm = m
                break
            }
        }
        if (defaultTm == null) throw GeneralSecurityException("No default X509 trust manager found")
        val tm = defaultTm
        return EntityTrustManager(tm, identitiesToTest, clientVerifier, serverVerifier)
    }

    /**
     * Creates a trustManager that validates the certificate based on the specified verifiers and
     * asks the user when the validation fails. When `null` is passed as the
     * `identityToTest` then no check is performed whether the certificate is valid for a
     * specific server or client.
     *
     * The trust manager which asks the client whether to trust particular certificate which is not
     * android root's CA trusted.
     *
     * Return TrustManager to use in an SSLContext
     */
    private inner class EntityTrustManager

    /**
     * Creates the custom trust manager.
     *
     * @param tm the default trust manager for verification.
     * @param identitiesToTest The identities to match against the supplied verifiers.
     * @param clientVerifier The verifier to use in calls to checkClientTrusted
     * @param serverVerifier The verifier to use in calls to checkServerTrusted
     */
    (private val tm: X509TrustManager, private val identitiesToTest: Iterable<String?>?,
            private val clientVerifier: CertificateMatcher?, private val serverVerifier: CertificateMatcher?) : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return tm.acceptedIssuers
        }

        @Throws(CertificateException::class)
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            checkCertTrusted(chain, authType, true)
        }

        @Throws(CertificateException::class)
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            checkCertTrusted(chain, authType, false)
        }

        @Throws(CertificateException::class)
        private fun checkCertTrusted(chain_: Array<X509Certificate>, authType: String, serverCheck: Boolean) {
            // check and default configurations for property if missing default is null - false
            var chain = chain_
            val defaultAlwaysTrustMode = CertificateVerificationActivator.resources!!
                    .getSettingsString(CertificateService.PNAME_ALWAYS_TRUST)
            if (config!!.getBoolean(CertificateService.PNAME_ALWAYS_TRUST, java.lang.Boolean.parseBoolean(defaultAlwaysTrustMode))) return
            try {
                // check the certificate itself (issuer, validity)!!
                try {
                    chain = tryBuildChain(chain)
                } catch (e: Exception) {
                    // don't care and take the chain as is
                    Timber.e("Build chain exception: %s", e.message)
                }

                /*
                 * Domain specific configurations require that hostname aware
                 * checkServerTrusted(X509Certificate[], String, String) is used
                 * but required X509ExtenderTrustManager (API=24)
                 */
                if (serverCheck) tm.checkServerTrusted(chain, authType) else tm.checkClientTrusted(chain, authType)
                when {
                    identitiesToTest == null || !identitiesToTest.iterator().hasNext() -> {
                        Timber.d("None check is required!")
                    }
                    serverCheck -> {
                        serverVerifier!!.verify(identitiesToTest, chain[0])
                    }
                    else -> {
                        clientVerifier!!.verify(identitiesToTest, chain[0])
                    }
                }
                // ok, globally valid cert
            } catch (e: CertificateException) {
                val thumbprint = getThumbprint(chain[0], THUMBPRINT_HASH_ALGORITHM)
                val message: String
                val propNames = LinkedList<String>()
                val storedCerts = LinkedList<String>()
                val appName = aTalkApp.getResString(R.string.APPLICATION_NAME)
                Timber.w("SSL certificate untrusted chain (Self-Generated Certificate) : %s", e.message)
                if (identitiesToTest == null || !identitiesToTest.iterator().hasNext()) {
                    val propName = PNAME_CERT_TRUST_PREFIX + CERT_TRUST_SERVER_SUBFIX + thumbprint
                    propNames.add(propName)
                    message = aTalkApp.getResString(R.string.service_gui_CERT_DIALOG_DESCRIPTION_TXT_NOHOST, appName)

                    // get the thumbprints from the permanent allowances
                    val hashes = config.getString(propName)
                    if (hashes != null) Collections.addAll(storedCerts, *hashes.split(",").toTypedArray())

                    // get the thumbprints from the session allowances
                    val sessionCerts = sessionAllowedCertificates[propName]
                    if (sessionCerts != null) storedCerts.addAll(sessionCerts)
                } else {
                    message = if (serverCheck) {
                        aTalkApp.getResString(R.string.service_gui_CERT_DIALOG_DESCRIPTION_TXT,
                                appName, identitiesToTest.toString())
                    } else {
                        aTalkApp.getResString(R.string.service_gui_CERT_DIALOG_PEER_DESCRIPTION_TXT,
                                appName, identitiesToTest.toString())
                    }
                    for (identity in identitiesToTest) {
                        val propName = PNAME_CERT_TRUST_PREFIX + CERT_TRUST_PARAM_SUBFIX + identity
                        propNames.add(propName)

                        // get the thumbprints from the permanent allowances
                        val hashes = config.getString(propName)
                        if (hashes != null) Collections.addAll(storedCerts, *hashes.split(",").toTypedArray())

                        // get the thumbprints from the session allowances
                        val sessionCerts = sessionAllowedCertificates[propName]
                        if (sessionCerts != null) storedCerts.addAll(sessionCerts)
                    }
                }
                if (!storedCerts.contains(thumbprint)) {
                    when (verify(chain, message)) {
                        CertificateService.DO_NOT_TRUST -> throw CertificateException("Peer provided certificate with Subject <"
                                + chain[0].subjectDN + "> is not trusted", e)
                        CertificateService.TRUST_ALWAYS -> for (propName in propNames) {
                            val current = config.getString(propName)
                            var newValue = thumbprint
                            if (current != null) newValue += ",$current"
                            // Timber.w(new Exception("Add Certificate To Trust: " + propName + ": " + newValue));
                            config.setProperty(propName, newValue)
                        }
                        CertificateService.TRUST_THIS_SESSION_ONLY -> for (propName in propNames) getSessionCertEntry(propName).add(thumbprint)
                    }
                }
                // ok, we've seen this certificate before
            }
        }

        /*
         * Only try to build chains for servers that send only their own cert, but no issuer.
         * This also matches self signed (will be ignored later) and Root-CA signed certs.
         * In this case we throw the Root-CA away after the lookup
         */
        @Throws(IOException::class, URISyntaxException::class, CertificateException::class)
        private fun tryBuildChain(chain_: Array<X509Certificate>): Array<X509Certificate> {
            var chain = chain_
            if (chain.size != 1) return chain

            // ignore self signed certs (issuer == signer)
            if (chain[0].issuerDN == chain[0].subjectDN) return chain

            // prepare for the newly created chain
            val newChain = ArrayList<X509Certificate>(chain.size + 4)
            Collections.addAll(newChain, *chain)

            // search from the topmost certificate upwards
            val certFactory = CertificateFactory.getInstance("X.509")
            var current = chain[chain.size - 1]
            var foundParent: Boolean
            var chainLookupCount = 0
            do {
                foundParent = false
                // extract the url(s) where the parent certificate can be found
                val aiaBytes = current.getExtensionValue(Extension.authorityInfoAccess.id)
                        ?: break
                val aia = AuthorityInformationAccess.getInstance(JcaX509ExtensionUtils.parseExtensionValue(aiaBytes))

                // the AIA may contain different URLs and types, try all of them
                for (ad in aia.accessDescriptions) {
                    // we are only interested in the issuer certificate, not in OCSP urls the like
                    if (!ad.accessMethod.equals(AccessDescription.id_ad_caIssuers)) continue
                    val gn = ad.accessLocation
                    if (!(gn.tagNo == GeneralName.uniformResourceIdentifier
                                    && gn.name is DERIA5String)) continue
                    val uri = URI((gn.name as DERIA5String).string)
                    // only http(s) urls; LDAP is taken care of in the default implementation
                    if ((uri.scheme.equals("http", ignoreCase = true) || uri.scheme != "https")) continue
                    var cert: X509Certificate? = null

                    // try to get cert from cache first to avoid consecutive-slow http lookups
                    val cache = aiaCache[uri]
                    if (cache != null && cache.cacheDate.after(Date())) {
                        cert = cache.cert
                    } else {
                        // download if no cache entry or if it has expired
                        Timber.d("Downloading parent certificate for <%s> from <%s>",
                                current.subjectDN, uri)
                        try {
                            val `is` = open(uri.path, false)
                            if (`is` != null) {
                                cert = certFactory.generateCertificate(`is`) as X509Certificate
                            }
                        } catch (e: Exception) {
                            Timber.e("No response body found: %s", uri)
                        }
                        // cache for 10mins
                        aiaCache[uri] = AiaCacheEntry(Date(Date().time + 10 * 60 * 1000), cert)
                    }
                    if (cert != null) {
                        if (cert.issuerDN != cert.subjectDN) {
                            newChain.add(cert)
                            foundParent = true
                            current = cert
                            break // an AD was valid, ignore others
                        } else Timber.d("Parent is self-signed, ignoring")
                    }
                }
                chainLookupCount++
            }
            while (foundParent && chainLookupCount < 10)
            @RequiresApi(Build.VERSION_CODES.TIRAMISU)
            chain = (newChain as ArrayList).toArray(chain)
            return chain
        }
    }

    private class BrowserLikeHostnameMatcher : CertificateMatcher {
        @Throws(CertificateException::class)
        override fun verify(identitiesToTest: Iterable<String?>?, cert: X509Certificate?) {
            // check whether one of the hostname is present in the certificate
            var oneMatched = false
            for (identity in identitiesToTest!!) {
                try {
                    SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER.verify(identity, cert)
                    oneMatched = true
                    break
                } catch (e: SSLException) {
                    Timber.e("Timber SSLException: %s", e.message)
                }
            }
            if (!oneMatched) throw CertificateException("None of <" + identitiesToTest
                    + "> matched the cert with CN=" + cert!!.subjectDN)
        }
    }

    private class EMailAddressMatcher : CertificateMatcher {
        @Throws(CertificateException::class)
        override fun verify(identitiesToTest: Iterable<String?>?, cert: X509Certificate?) {
            // check if the certificate contains the E-Mail address(es) in the SAN(s)
            // TODO: extract address from DN (E-field) too?
            var oneMatched = false
            val emails = getSubjectAltNames(cert, 6)
            for (identity in identitiesToTest!!) {
                for (email in emails) {
                    if (identity.equals(email, ignoreCase = true)) {
                        oneMatched = true
                        break
                    }
                }
            }
            if (!oneMatched) {
                throw CertificateException("The peer provided certificate with Subject <"
                        + cert!!.subjectDN + "> contains no SAN for <" + identitiesToTest + ">")
            }
        }
    }

    /**
     * Asks the user whether he trusts the supplied chain of certificates.
     *
     * @param chain The chain of the certificates to check with user.
     * @param message A text that describes why the verification failed.
     * @return The result of the user interaction. One of
     * [CertificateService.DO_NOT_TRUST],
     * [CertificateService.TRUST_THIS_SESSION_ONLY],
     * [CertificateService.TRUST_ALWAYS]
     */
    private fun verify(chain: Array<X509Certificate>?, message: String?): Int {
        if (config!!.getBoolean(CertificateService.PNAME_NO_USER_INTERACTION, false)) return CertificateService.DO_NOT_TRUST
        if (CertificateVerificationActivator.getCertificateDialogService() == null) {
            Timber.e("Missing CertificateDialogService by default will not trust!")
            return CertificateService.DO_NOT_TRUST
        }

        // show for proper moment, other may be obscure by others
        val waitForFocus = aTalkApp.waitForFocus()
        val dialog = CertificateVerificationActivator.getCertificateDialogService()!!.createDialog(chain as Array<Certificate>?, null, message)!!
        dialog.setVisible(true)
        return if (!dialog.isTrusted()) CertificateService.DO_NOT_TRUST else if (dialog.isAlwaysTrustSelected()) CertificateService.TRUST_ALWAYS else CertificateService.TRUST_THIS_SESSION_ONLY
    }

    companion object {
        // ------------------------------------------------------------------------
        // properties
        // ------------------------------------------------------------------------
        /**
         * Base property name for the storage of certificate user preferences.
         */
        const val PNAME_CERT_TRUST_PREFIX = "certservice"
        const val CERT_TRUST_SERVER_SUBFIX = ".server."
        const val CERT_TRUST_PARAM_SUBFIX = ".param."
        const val CERT_XMPP_CLIENT_SUBFIX = "_xmpp-client."

        /**
         * Hash algorithm for the cert thumbprint
         */
        private const val THUMBPRINT_HASH_ALGORITHM = "SHA1"

        /**
         * Calculates the hash of the certificate known as the "thumbprint" and returns it as a string representation.
         *
         * @param cert The certificate to hash.
         * @param algorithm The hash algorithm to use.
         * @return The SHA-1 hash of the certificate.
         * @throws CertificateException Certificate exception
         */
        @Throws(CertificateException::class)
        private fun getThumbprint(cert: Certificate?, algorithm: String): String {
            val digest = try {
                MessageDigest.getInstance(algorithm)
            } catch (e: NoSuchAlgorithmException) {
                throw CertificateException(e)
            }
            val encodedCert = cert!!.encoded
            val sb = StringBuilder(encodedCert.size * 2)
            Formatter(sb).use { f -> for (b in digest.digest(encodedCert)) f.format("%02x", b) }
            return sb.toString()
        }

        /**
         * Gets the SAN (Subject Alternative Name) of the specified type.
         *
         * @param cert the certificate to extract from
         * @param altNameType The type to be returned
         * @return SAN of the type
         *
         *
         * <PRE>
         * GeneralName ::= CHOICE {
         * otherName                   [0]   OtherName,
         * rfc822Name                  [1]   IA5String,
         * dNSName                     [2]   IA5String,
         * x400Address                 [3]   ORAddress,
         * directoryName               [4]   Name,
         * ediPartyName                [5]   EDIPartyName,
         * uniformResourceIdentifier   [6]   IA5String,
         * iPAddress                   [7]   OCTET STRING,
         * registeredID                [8]   OBJECT IDENTIFIER
         * }
         *
         *
         * <PRE>
        </PRE></PRE> */
        private fun getSubjectAltNames(cert: X509Certificate?, altNameType: Int): Iterable<String> {
            val altNames = try {
                cert!!.subjectAlternativeNames
            } catch (e: CertificateParsingException) {
                return emptyList()
            }

            val matchedAltNames = LinkedList<String>()
            for (item in altNames) {
                if (item.contains(altNameType)) {
                    val type = item[0] as Int
                    if (type == altNameType) matchedAltNames.add(item[1] as String)
                }
            }
            return matchedAltNames
        }
    }
}