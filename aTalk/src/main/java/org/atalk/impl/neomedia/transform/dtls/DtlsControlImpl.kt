/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.dtls

import android.text.TextUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.AbstractRTPConnector
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.AbstractSrtpControl
import org.atalk.service.neomedia.DtlsControl
import org.atalk.service.neomedia.DtlsControl.Setup
import org.atalk.service.neomedia.SrtpControl
import org.atalk.service.neomedia.SrtpControlType
import org.atalk.service.neomedia.event.SrtpListener
import org.atalk.util.*
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.Certificate
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.bc.BcX509v3CertificateBuilder
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECKeyGenerationParameters
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.bouncycastle.crypto.params.RSAKeyParameters
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.bc.BcDefaultDigestProvider
import org.bouncycastle.operator.bc.BcECContentSignerBuilder
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder
import org.bouncycastle.tls.*
import org.bouncycastle.tls.crypto.TlsCertificate
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCertificate
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto
import timber.log.Timber
import java.io.IOException
import java.math.BigInteger
import java.security.SecureRandom
import java.util.*

/**
 * Implements [DtlsControl] i.e. [SrtpControl] for DTLS-SRTP.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class DtlsControlImpl @JvmOverloads constructor(
        srtpDisabled: Boolean = false,
) : AbstractSrtpControl<DtlsTransformEngine>(SrtpControlType.DTLS_SRTP), DtlsControl {
    private var mSecurityState = false

    /**
     * Gets the certificate with which the local endpoint represented by this instance
     * authenticates its ends of DTLS sessions.
     *
     * @return the certificate with which the local endpoint represented by this instance
     * authenticates its ends of DTLS sessions.
     */
    /**
     * The certificate with which the local endpoint represented by this instance authenticates its
     * ends of DTLS sessions.
     */
    val certificateInfo: CertificateInfo?

    /**
     * The indicator which determines whether this instance has been disposed
     * i.e. prepared for garbage collection by [.doCleanup].
     */
    private var disposed = false

    /**
     * The fingerprints presented by the remote endpoint via the signaling path.
     */
    private var remoteFingerprints: Map<String, String>? = null
    /**
     * Gets the properties of `DtlsControlImpl` and their values which this instance shares
     * with [DtlsTransformEngine] and [DtlsPacketTransformer].
     *
     * @return the properties of `DtlsControlImpl` and their values which this instance
     * shares with `DtlsTransformEngine` and `DtlsPacketTransformer`
     */
    /**
     * The properties of `DtlsControlImpl` and their values which this
     * instance shares with [DtlsTransformEngine] and [DtlsPacketTransformer].
     */
    val properties: Properties
    /**
     * Initializes a new `DtlsControlImpl` instance.
     *
     * @param srtpDisabled `true` if pure DTLS mode without SRTP extensions is to be used; otherwise, `false`
     */
    /**
     * Initializes a new `DtlsControlImpl` instance. By default aTalk works in DTLS/SRTP mode.
     */
    init {
        var certificateInfo: CertificateInfo?
        // Timber.e(new Exception("TLS Certificate Signature Algorithm: " + mSignatureAlgorithm + "; " + certificateInfoCache));

        // The methods generateKeyPair(), generateX509Certificate(), findHashFunction(), and/or
        // computeFingerprint() may be too CPU intensive to invoke for each new DtlsControlImpl instance.
        // That's why we've decided to reuse their return values within a certain time frame (Default 1 day).
        // Attempt to retrieve from the cache.
        synchronized(DtlsControlImpl::class.java) {
            certificateInfo = certificateInfoCache
            // The cache doesn't exist yet or has outlived its lifetime. Rebuild the cache.
            if (certificateInfo == null
                    || certificateInfo!!.timestamp + CERT_CACHE_EXPIRE_TIME < System.currentTimeMillis()) {
                certificateInfo = generateCertificateInfo()
                certificateInfoCache = certificateInfo
            }
        }
        this.certificateInfo = certificateInfo
        properties = Properties(srtpDisabled)
    }

    /**
     * Initializes a new `DtlsTransformEngine` instance to be associated with and used by
     * this `DtlsControlImpl` instance. The method is implemented as a factory.
     *
     * @return a new `DtlsTransformEngine` instance to be associated with
     * and used by this `DtlsControlImpl` instance
     */
    override fun createTransformEngine(): DtlsTransformEngine {
        return DtlsTransformEngine(this)
    }

    /**
     * {@inheritDoc}
     */
    override fun doCleanup() {
        super.doCleanup()
        setConnector(null)
        synchronized(this) {
            disposed = true
            (this as Object).notifyAll()
        }
    }
    /*
     * Cannot regenerate certificate with new signer once it has been advertised in session-initiate; otherwise
     * protocol handshake will fail in verifyAndValidateCertificate(Certificate certificate) as fingerPrints
     * are mismatched between the new certificate and the one in session-initiate.
     */
    //    CertificateInfo getCertificateInfo(String signatureAlgorithm)
    //    {
    //        if (!mSignatureAlgorithm.equals(signatureAlgorithm)) {
    //            Timber.e("Regenerate certificate %s => %s", mSignatureAlgorithm, signatureAlgorithm);
    //            mSignatureAlgorithm = signatureAlgorithm;
    //            certificateInfoCache = mCertificateInfo = generateCertificateInfo();
    //        }
    //        return mCertificateInfo;
    //    }
    /**
     * {@inheritDoc}
     */
    override val localFingerprint: String
        get() {
            // Timber.d("CertificateInfo => LocalFingerprint' %s\n%s",
            //        mCertificateInfo.getCertificateType(), getCertificateInfo().localFingerprint);
            return certificateInfo!!.localFingerprint
        }

    /**
     * {@inheritDoc}
     */
    override val localFingerprintHashFunction: String
        get() {
            // Timber.d("CertificateInfo => LocalFingerprintHashFunction' %s: %s",
            //        mCertificateInfo.getCertificateType(), getCertificateInfo().localFingerprintHashFunction);
            return certificateInfo!!.localFingerprintHashFunction
        }

    /**
     * {@inheritDoc}
     */
    override val secureCommunicationStatus: Boolean
        get() {
            return mSecurityState
        }

    /**
     * {@inheritDoc}
     *
     *
     * The implementation of `DtlsControlImpl` always returns `true`.
     */
    override fun requiresSecureSignalingTransport(): Boolean {
        return true
    }

    /**
     * {@inheritDoc}
     */
    override fun setConnector(connector: AbstractRTPConnector?) {
        properties.put(Properties.CONNECTOR_PNAME, connector)
    }

    /**
     * {@inheritDoc}
     */
    override fun setRemoteFingerprints(remoteFingerprints: Map<String?, String?>?) {
        if (remoteFingerprints == null) throw NullPointerException("remoteFingerprints")

        // Don't pass an empty list to the stack in order to avoid wiping
        // certificates that were contained in a previous request.
        if (remoteFingerprints.isEmpty()) {
            return
        }

        // Make sure that the hash functions (which are keys of the field
        // remoteFingerprints) are written in lower case.
        val rfs: MutableMap<String, String> = HashMap(remoteFingerprints.size)
        for ((k, v) in remoteFingerprints) {

            // It makes no sense to provide a fingerprint without a hash function.
            if (k != null) {

                // It makes no sense to provide a hash function without a fingerprint.
                if (v != null) rfs[k.lowercase(Locale.getDefault())] = v
            }
        }
        this.remoteFingerprints = rfs
    }

    /**
     * {@inheritDoc}
     */
    override fun setRtcpmux(rtcpmux: Boolean) {
        properties.put(Properties.RTCPMUX_PNAME, rtcpmux)
    }

    /**
     * Gets the value of the `setup` SDP attribute defined by RFC 4145
     * &quot;TCP-Based Media Transport in the Session Description Protocol (SDP)&quot;
     * which determines whether this instance acts as a DTLS client or a DTLS server.
     *
     * @return the value of the `setup` SDP attribute defined by RFC 4145 &quot;TCP-Based
     * Media Transport in the Session Description Protocol (SDP)&quot; which determines whether
     * this instance acts as a DTLS client or a DTLS server
     */
    override var setup: Setup?
        get() = properties.setup
        set(setup) {
            properties.put(Properties.SETUP_PNAME, setup)
        }

    /**
     * {@inheritDoc}
     */
    override fun start(mediaType: MediaType) {
        properties.put(Properties.MEDIA_TYPE_PNAME, mediaType)
    }

    /**
     * Update app on the security status of the handshake result
     *
     * @param securityState Security state
     */
    fun secureOnOff(securityState: Boolean) {
        val srtpListener = srtpListener!!
        val mediaType = properties[Properties.MEDIA_TYPE_PNAME] as MediaType
        mSecurityState = securityState
        if (securityState)
            srtpListener.securityTurnedOn(mediaType, srtpControlType.toString(), this)
        else
            srtpListener.securityTurnedOff(mediaType)
    }

    /**
     * Notifies this instance that the DTLS record layer associated with a specific `TlsPeer` has raised an alert.
     *
     * @param alertLevel [AlertLevel] has similar values as SrtpListener
     * @param alertDescription [AlertDescription]
     * @param msg a human-readable message explaining what caused the alert. May be `null`.
     * @param cause the exception that caused the alert to be raised. May be `null`.
     */
    fun notifyAlertRaised(
            tlsPeer: TlsPeer?, alertLevel: Short, alertDescription: Short,
            msg: String?, cause: Throwable?,
    ) {
        var message = msg
        val srtpListener = srtpListener!!
        val errDescription = AlertDescription.getName(alertDescription)

        var srtError = SrtpListener.INFORMATION
        if (AlertLevel.fatal == alertLevel) {
            srtError = SrtpListener.SEVERE
        }
        else if (AlertLevel.warning == alertLevel) {
            srtError = SrtpListener.WARNING
        }

        /* The client and the server must share knowledge that the connection is ending in order to avoid a truncation
         * a initiate the exchange of closing messages.
         * close_notify: This message notifies the recipient that the sender will not send any more messages on this connection.
         * Note that as of TLS 1.1, failure to properly close a connection no longer requires that a session not be resumed.
         * This is a change from TLS 1.0 to conform with widespread implementation practice.
         */
        if (TextUtils.isEmpty(message)) {
            if (AlertDescription.close_notify == alertDescription) {
                srtError = SrtpListener.INFORMATION // change to for info only
                message = aTalkApp.getResString(R.string.imp_media_security_ENCRYPTION_ENDED, errDescription)
            }
            else {
                message = aTalkApp.getResString(R.string.impl_media_security_INTERNAL_PROTOCOL_ERROR, errDescription)
            }
        }
        srtpListener.securityMessageReceived(errDescription, message, srtError)
    }

    /**
     * Verifies and validates a specific certificate against the fingerprints presented by the
     * remote endpoint via the signaling path.
     *
     * @param certificate the certificate to be verified and validated against the fingerprints
     * presented by the remote endpoint via the signaling path
     *
     * @throws Exception if the specified `certificate` failed to verify and validate
     * against the fingerprints presented by the remote endpoint via the signaling path
     */
    @Throws(Exception::class)
    private fun verifyAndValidateCertificate(certificate: Certificate) {
        /*
         * RFC 4572 "Connection-Oriented Media Transport over the Transport Layer Security (TLS)
         * Protocol in the Session Description Protocol (SDP)" defines that "[a] certificate
         * fingerprint MUST be computed using the same one-way hash function as is used in the
         * certificate's signature algorithm."
         */
        var hashFunction = findHashFunction(certificate)

        /*
         * As RFC 5763 "Framework for Establishing a Secure Real-time Transport Protocol (SRTP)
         * Security Context Using Datagram Transport Layer Security (DTLS)" states, "the
         * certificate presented during the DTLS handshake MUST match the fingerprint exchanged
         * via the signaling path in the SDP."
         */
        var remoteFingerprint: String?

        synchronized(this) {
            check(!disposed) { "disposed" }

            val remoteFingerprints = remoteFingerprints
                    ?: throw IOException("No fingerprints declared over the signaling path!")

            remoteFingerprint = remoteFingerprints[hashFunction]

            // Unfortunately, Firefox does not comply with RFC 5763 at the time of this writing.
            // Its certificate uses SHA-1 and it sends a fingerprint computed with SHA-256. We
            // could, of course, wait for Mozilla to make Firefox compliant. However, we would
            // like to support Firefox in the meantime. That is why we will allow the fingerprint
            // to "upgrade" the hash function of the certificate much like SHA-256 is an "upgrade" of SHA-1.
            if (remoteFingerprint == null) {
                val hashFunctionUpgrade = findHashFunctionUpgrade(hashFunction, remoteFingerprints)
                if (hashFunctionUpgrade != null
                        && !hashFunctionUpgrade.equals(hashFunction, ignoreCase = true)) {
                    remoteFingerprint = remoteFingerprints[hashFunctionUpgrade]
                    if (remoteFingerprint != null)
                        hashFunction = hashFunctionUpgrade
                }
            }
        }
        if (remoteFingerprint == null) {
            throw IOException("No fingerprint declared over the signaling path with hash function: "
                    + hashFunction + "!")
        }
        val fingerprint = computeFingerprint(certificate, hashFunction)
        if (remoteFingerprint == fingerprint) {
            Timber.log(TimberLog.FINER, "Fingerprint %s matches the %s-hashed certificate.",
                remoteFingerprint, hashFunction)
        }
        else {
            throw IOException("Fingerprint " + remoteFingerprint + " does not match the "
                    + hashFunction + "-hashed certificate " + fingerprint + "!")
        }
    }

    /**
     * Verifies and validates a specific certificate against the fingerprints presented by the
     * remote endpoint via the signaling path.
     *
     * @param certificate the certificate to be verified and validated against the fingerprints
     * presented by the remote endpoint via the signaling path
     *
     * @throws Exception if the specified `certificate` failed to verify and validate against the
     * fingerprints presented by the remote endpoint over the signaling path
     */
    @Throws(Exception::class)
    fun verifyAndValidateCertificate(certificate: org.bouncycastle.tls.Certificate) {
        try {
            require(!certificate.isEmpty) { "certificate.certificateList" }

            val chain = certificate.certificateList
            for (tlsCertificate in chain) {
                val entry = Certificate.getInstance(tlsCertificate.encoded)
                verifyAndValidateCertificate(entry)
            }
        } catch (e: Exception) {
            val message = ("Failed to verify and/or validate a certificate offered over"
                    + " the media path against fingerprints declared over the signaling path!")
            val throwableMessage = e.message

            if (VERIFY_AND_VALIDATE_CERTIFICATE) {
                if (throwableMessage == null || throwableMessage.isEmpty())
                    Timber.e(e, "%s", message)
                else
                    Timber.e("%s %s", message, throwableMessage)
                throw e
            }
            else {
                // XXX Contrary to RFC 5763 "Framework for Establishing a Secure
                // Real-time Transport Protocol (SRTP) Security Context Using
                // Datagram Transport Layer Security (DTLS)", we do NOT want to
                // teardown the media session if the fingerprint does not match
                // the hashed certificate. We want to notify the user via the SrtpListener.
                if (throwableMessage == null || throwableMessage.isEmpty())
                    Timber.w(e, "%s", message)
                else
                    Timber.w("%s %s", message, throwableMessage)
            }
        }
    }

    companion object {
        /**
         * The map which specifies which hash functions are to be considered
         * &quot;upgrades&quot; of which other hash functions. The keys are the hash
         * functions which have &quot;upgrades&quot; defined and are written in lower case.
         */
        private val HASH_FUNCTION_UPGRADES = HashMap<String, Array<String>>()

        /**
         * The table which maps half-`byte`s to their hex characters.
         */
        private val HEX_ENCODE_TABLE = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
        )

        /**
         * The number of milliseconds within a day i.e. 24 hours.
         */
        private const val ONE_DAY = 1000L * 60L * 60L * 24L

        /**
         * The name of the property which specifies the SignatureAndHashAlgorithm used
         * during certificate creation. When a certificate is created and this
         * property is not set, this DEFAULT_SIGNATURE_AND_HASH_ALGORITHM will be used.
         */
        const val DEFAULT_SIGNATURE_AND_HASH_ALGORITHM = "SHA256withECDSA"
        // public static final String DEFAULT_SIGNATURE_ALGORITHM = "SHA256withRSA";

        /**
         * The name of the property to specify RSA Key length.
         */
        private const val RSA_KEY_SIZE_PNAME = "neomedia.transform.dtls.RSA_KEY_SIZE"

        /**
         * The default RSA key size when configuration properties are not found.
         */
        private const val DEFAULT_RSA_KEY_SIZE = 2048

        /**
         * The RSA key size to use.
         * The default value is `DEFAULT_RSA_KEY_SIZE` but may be overridden
         * by the `ConfigurationService` and/or `System` property `RSA_KEY_SIZE_PNAME`.
         */
        private val RSA_KEY_SIZE : Int

        /**
         * The name of the property to specify RSA key size certainty.
         * https://docs.oracle.com/javase/7/docs/api/java/math/BigInteger.html
         */
        private const val RSA_KEY_SIZE_CERTAINTY_PNAME = "neomedia.transform.dtls.RSA_KEY_SIZE_CERTAINTY"

        /**
         * The RSA key size certainty to use.
         * The default value is `DEFAULT_RSA_KEY_SIZE_CERTAINTY` but may be overridden by the
         * `ConfigurationService` and/or `System` property `RSA_KEY_SIZE_CERTAINTY_PNAME`.
         * For more on certainty, look at the three parameter constructor here:
         * https://docs.oracle.com/javase/7/docs/api/java/math/BigInteger.html
         */
        private val RSA_KEY_SIZE_CERTAINTY : Int

        /**
         * The default RSA key size certainty when config properties are not found.
         */
        private const val DEFAULT_RSA_KEY_SIZE_CERTAINTY = 80

        /**
         * The name of the property which specifies the signature algorithm used
         * during certificate creation. When a certificate is created and this
         * property is not set, a default value of "SHA256withRSA" will be used.
         */
        const val CERT_TLS_SIGNATURE_ALGORITHM = "neomedia.transform.dtls.SIGNATURE_ALGORITHM"

        /**
         * The name of the property to specify DTLS certificate cache expiration.
         */
        private const val CERT_CACHE_EXPIRE_TIME_PNAME = "neomedia.transform.dtls.CERT_CACHE_EXPIRE_TIME"

        /**
         * The certificate cache expiration time to use, in milliseconds.
         * The default value is `DEFAULT_CERT_CACHE_EXPIRE_TIME` but may be overridden by the
         * `ConfigurationService` and/or `System` property `CERT_CACHE_EXPIRE_TIME_PNAME`.
         */
        private val CERT_CACHE_EXPIRE_TIME : Long

        /**
         * The default certificate cache expiration time, when config properties are not found.
         */
        private const val DEFAULT_CERT_CACHE_EXPIRE_TIME = ONE_DAY

        /**
         * The public exponent to always use for RSA key generation.
         */
        val RSA_KEY_PUBLIC_EXPONENT = BigInteger("10001", 16)

        /**
         * The `SRTPProtectionProfile`s supported by `DtlsControlImpl`.
         */
        val SRTP_PROTECTION_PROFILES = intArrayOf(
            // RFC 5764 4.1.2.
            SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_80,
            SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_32
        )

        /**
         * The indicator which specifies whether `DtlsControlImpl` is to tear down the media session
         * if the fingerprint does not match the hashed certificate. The default value is `true` and
         * may be overridden by the `ConfigurationService` and/or `System` property
         * `VERIFY_AND_VALIDATE_CERTIFICATE_PNAME`.
         */
        private var VERIFY_AND_VALIDATE_CERTIFICATE : Boolean

        /**
         * The name of the `ConfigurationService` and/or `System` property which specifies whether
         * `DtlsControlImpl` is to tear down the media session if the fingerprint does not match the hashed
         * certificate. The default value is `true`.
         */
        private const val VERIFY_AND_VALIDATE_CERTIFICATE_PNAME = "neomedia.transform.dtls.verifyAndValidateCertificate"

        /**
         * The cache of [.certificateInfo] so that we do not invoke CPU
         * intensive methods for each new `DtlsControlImpl` instance.
         */
        private var certificateInfoCache: CertificateInfo? = null

        init {
            // Set configurable options using ConfigurationService.
            VERIFY_AND_VALIDATE_CERTIFICATE = ConfigUtils.getBoolean(
                LibJitsi.configurationService,
                VERIFY_AND_VALIDATE_CERTIFICATE_PNAME,
                true)

            RSA_KEY_SIZE = ConfigUtils.getInt(
                LibJitsi.configurationService,
                RSA_KEY_SIZE_PNAME,
                DEFAULT_RSA_KEY_SIZE)

            RSA_KEY_SIZE_CERTAINTY = ConfigUtils.getInt(
                LibJitsi.configurationService,
                RSA_KEY_SIZE_CERTAINTY_PNAME,
                DEFAULT_RSA_KEY_SIZE_CERTAINTY)

            CERT_CACHE_EXPIRE_TIME = ConfigUtils.getLong(
                LibJitsi.configurationService,
                CERT_CACHE_EXPIRE_TIME_PNAME,
                DEFAULT_CERT_CACHE_EXPIRE_TIME)

            // HASH_FUNCTION_UPGRADES
            HASH_FUNCTION_UPGRADES["sha-1"] = arrayOf("sha-224", "sha-256", "sha-384", "sha-512")
        }

        private var mSignatureAlgorithm: String? = null

        /**
         * Generates a new certificate from a new key pair, determines the hash function, and computes the fingerprint.
         *
         * @return CertificateInfo a new certificate generated from a new key pair, its hash function, and fingerprint
         */
        private fun generateCertificateInfo(): CertificateInfo {
            val keyPair = generateKeyPair()
            val x509Certificate = generateX509Certificate(generateCN(), keyPair)

            val tlsCertificate = BcTlsCertificate(BcTlsCrypto(SecureRandom()), x509Certificate)
            val certificate = Certificate(arrayOf<TlsCertificate>(tlsCertificate))

            val localFingerprintHashFunction = findHashFunction(x509Certificate)
            val localFingerprint = computeFingerprint(x509Certificate, localFingerprintHashFunction)
            val timestamp = System.currentTimeMillis()
            return CertificateInfo(keyPair, certificate, localFingerprintHashFunction, localFingerprint, timestamp)
        }

        /**
         * Return a pair of RSA private and public keys.
         *
         * The signature algorithm of the generated certificate defaults to SHA256.
         * However, allow the overriding of the default via the ConfigurationService mSignatureAlgorithm.
         *
         * @return a pair of private and public keys
         */
        private fun generateKeyPair(): AsymmetricCipherKeyPair {
            if (mSignatureAlgorithm!!.uppercase(Locale.ROOT).endsWith("RSA")) {
                val generator = RSAKeyPairGenerator()
                generator.init(RSAKeyGenerationParameters(
                    RSA_KEY_PUBLIC_EXPONENT, SecureRandom(), RSA_KEY_SIZE, RSA_KEY_SIZE_CERTAINTY))
                return generator.generateKeyPair()
            }
            else if (mSignatureAlgorithm!!.uppercase(Locale.ROOT).endsWith("ECDSA")) {
                val generator = ECKeyPairGenerator()
                val curve = ECNamedCurveTable.getParameterSpec("secp256r1")
                val domainParams = ECDomainParameters(curve.curve, curve.g, curve.n, curve.h, curve.seed)
                generator.init(ECKeyGenerationParameters(domainParams, SecureRandom()))
                return generator.generateKeyPair()
            }
            throw IllegalArgumentException("Unknown signature algorithm: $mSignatureAlgorithm")
        }

        /**
         * Generates a new subject for a self-signed certificate to be generated by `DtlsControlImpl`.
         *
         * @return an `X500Name` which is to be used as the subject of a self-signed certificate
         * to be generated by `DtlsControlImpl`
         */
        private fun generateCN(): X500Name {
            val builder = X500NameBuilder(BCStyle.INSTANCE)

            val secureRandom = SecureRandom()
            val bytes = ByteArray(16)
            secureRandom.nextBytes(bytes)
            val chars = CharArray(32)

            for (i in 0..15) {
                val b = bytes[i].toInt() and 0xff
                chars[i * 2] = HEX_ENCODE_TABLE[b ushr 4]
                chars[i * 2 + 1] = HEX_ENCODE_TABLE[b and 0x0f]
            }
            builder.addRDN(BCStyle.CN, String(chars).lowercase(Locale.getDefault()))
            return builder.build()
        }

        /**
         * Generates a new self-signed certificate with a specific subject and a specific pair of
         * private and public keys.
         *
         * @param subject the subject (and issuer) of the new certificate to be generated
         * @param keyPair the pair of private and public keys of the certificate to be generated
         *
         * @return a new self-signed certificate with the specified `subject` and `keyPair`
         */
        private fun generateX509Certificate(subject: X500Name, keyPair: AsymmetricCipherKeyPair): Certificate {
            Timber.d("Signature algorithm: %s", mSignatureAlgorithm)
            return try {
                val now = System.currentTimeMillis()
                val notBefore = Date(now - ONE_DAY)
                val notAfter = Date(now + ONE_DAY * 6 + CERT_CACHE_EXPIRE_TIME)
                val builder: X509v3CertificateBuilder = BcX509v3CertificateBuilder(
                    subject, /* issuer */
                    BigInteger.valueOf(now),  /* serial */
                    notBefore, notAfter, subject,
                    keyPair.public  /* publicKey */)

                val sigAlgId = DefaultSignatureAlgorithmIdentifierFinder().find(mSignatureAlgorithm)
                val digAlgId = DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId)

                val signer = if (keyPair.private is RSAKeyParameters) {
                    BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(keyPair.private)
                }
                else {
                    BcECContentSignerBuilder(sigAlgId, digAlgId).build(keyPair.private)
                }
                builder.build(signer).toASN1Structure()
            } catch (t: Throwable) {
                if (t is ThreadDeath)
                    throw t
                else {
                    Timber.e(t, "Failed to generate self-signed X.509 certificate")
                    if (t is RuntimeException)
                        throw t
                    else
                        throw RuntimeException(t)
                }
            }
        }

        /**
         * Set the default TLS certificate signature algorithm; This value must be set prior to DtlsControlImpl().
         * Init certificateInfoCache if the mSignatureAlgorithm is s new user defined SignatureAlgorithm
         *
         * @param tlsCertSA TLS certificate signature algorithm
         */
        @JvmStatic
        fun setTlsCertificateSA(tlsCertSA: String) {
            if (mSignatureAlgorithm != null && mSignatureAlgorithm != tlsCertSA) {
                certificateInfoCache = null
            }
            mSignatureAlgorithm = tlsCertSA
        }

        /**
         * Chooses the first from a list of `SRTPProtectionProfile`s that is supported by `DtlsControlImpl`.
         *
         * @param theirs the list of `SRTPProtectionProfile`s to choose from
         *
         * @return the first from the specified `theirs` that is supported by `DtlsControlImpl`
         */
        fun chooseSRTPProtectionProfile(vararg theirs: Int): Int {
            if (theirs != null) {
                for (their in theirs) {
                    for (our in SRTP_PROTECTION_PROFILES) {
                        if (their == our) {
                            return their
                        }
                    }
                }
            }
            return 0
        }

        /**
         * Computes the fingerprint of a specific certificate using a specific hash function.
         *
         * @param certificate the certificate the fingerprint of which is to be computed
         * @param hashFunction the hash function to be used in order to compute the fingerprint of the specified `certificate`
         *
         * @return the fingerprint of the specified `certificate` computed using the specified `hashFunction`
         */
        private fun computeFingerprint(certificate: Certificate, hashFunction: String): String {
            return try {
                val digAlgId = DefaultDigestAlgorithmIdentifierFinder().find(hashFunction.uppercase(Locale.getDefault()))
                val digest = BcDefaultDigestProvider.INSTANCE[digAlgId]
                val inByte = certificate.getEncoded(ASN1Encoding.DER)
                val outByte = ByteArray(digest.digestSize)

                digest.update(inByte, 0, inByte.size)
                digest.doFinal(outByte, 0)
                toHex(outByte)
            } catch (t: Throwable) {
                if (t is ThreadDeath) {
                    throw t
                }
                else {
                    Timber.e(t, "Failed to generate certificate fingerprint!")
                    if (t is RuntimeException)
                        throw t
                    else
                        throw RuntimeException(t)
                }
            }
        }

        /**
         * Determines the hash function i.e. the digest algorithm of the signature algorithm of a specific certificate.
         *
         * @param certificate the certificate the hash function of which is to be determined
         *
         * @return the hash function of the specified `certificate` written in lower case
         */
        private fun findHashFunction(certificate: Certificate): String {
            return try {
                val sigAlgId = certificate.signatureAlgorithm
                val digAlgId = DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId)
                BcDefaultDigestProvider.INSTANCE[digAlgId].algorithmName.lowercase(Locale.getDefault())
            } catch (t: Throwable) {
                if (t is ThreadDeath) {
                    throw t
                }
                else {
                    Timber.w(t, "Failed to find the hash function of the signature algorithm of a certificate!")
                    if (t is RuntimeException)
                        throw t
                    else
                        throw RuntimeException(t)
                }
            }
        }

        /**
         * Finds a hash function which is an &quot;upgrade&quot; of a specific hash
         * function and has a fingerprint associated with it.
         *
         * @param hashFunction the hash function which is not associated with a
         * fingerprint and for which an &quot;upgrade&quot; associated with a fingerprint is to be found
         * @param fingerprints the set of available hash function-fingerprint associations
         *
         * @return a hash function written in lower case which is an &quot;upgrade&quot; of the specified
         * `hashFunction` and has a fingerprint associated with it in `fingerprints` if
         * there is such a hash function; otherwise, `null`
         */
        private fun findHashFunctionUpgrade(hashFunction: String, fingerprints: Map<String, String>): String? {
            val hashFunctionUpgrades = HASH_FUNCTION_UPGRADES[hashFunction]
            if (hashFunctionUpgrades != null) {
                for (hashFunctionUpgrade in hashFunctionUpgrades) {
                    val fingerprint = fingerprints[hashFunctionUpgrade]
                    if (fingerprint != null)
                        return hashFunctionUpgrade.lowercase(Locale.getDefault())
                }
            }
            return null
        }

        /**
         * Gets the `String` representation of a fingerprint specified in the form of an
         * array of `byte`s in accord with RFC 4572.
         *
         * @param fingerprint an array of `bytes` which represents a fingerprint the `String`
         * representation in accord with RFC 4572 of which is to be returned
         *
         * @return the `String` representation in accord with RFC 4572 of the specified `fingerprint`
         */
        private fun toHex(fingerprint: ByteArray): String {
            require(fingerprint.isNotEmpty()) { "fingerprint" }

            val chars = CharArray(3 * fingerprint.size - 1)
            var f = 0
            val fLast = fingerprint.size - 1
            var c = 0
            while (f <= fLast) {
                val b = fingerprint[f].toInt() and 0xff

                chars[c++] = HEX_ENCODE_TABLE[b ushr 4]
                chars[c++] = HEX_ENCODE_TABLE[b and 0x0f]
                if (f != fLast)
                    chars[c++] = ':'
                f++
            }
            return String(chars)
        }
    }
}