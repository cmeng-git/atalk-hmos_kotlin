/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.dtls

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.operator.DefaultAlgorithmNameFinder
import org.bouncycastle.tls.*
import org.bouncycastle.tls.crypto.TlsCryptoParameters
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedDecryptor
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto
import timber.log.Timber
import java.io.IOException
import java.security.SecureRandom
import java.util.*

/**
 * Implements [TlsServer] for the purposes of supporting DTLS-SRTP - DTLSv12/DTLSv10.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 * @See [Datagram Transport Layer Security Version 1.2](https://www.rfc-editor.org/rfc/rfc6347)
 * @See [The Transport Layer Security (TLS) Protocol Version 1.2](https://www.rfc-editor.org/rfc/rfc5246)
 * or https://www.ietf.org/rfc/rfc5246.html
 */
class TlsServerImpl
/**
 * Initializes a new `TlsServerImpl` instance.
 *
 * packetTransformer the `PacketTransformer` which is initializing the new instance
 */
(
        /**
         * The `PacketTransformer` which has initialized this instance.
         */
        private val mPacketTransformer: DtlsPacketTransformer) : DefaultTlsServer(BcTlsCrypto(SecureRandom())) {

    /**
     * @see TlsServer.getCertificateRequest
     */
    private var certificateRequest: CertificateRequest? = null

    /**
     * If DTLSv12 or higher is negotiated, configures the set of supported signature algorithms in the
     * CertificateRequest (if one is sent). If null, uses the default set.
     */
    private val serverCertReqSigAlgs: Vector<*>? = null

    /**
     * The `SRTPProtectionProfile` negotiated between this DTLS-SRTP server and its client.
     */
    private var chosenProtectionProfile = 0

    /**
     * @see DefaultTlsServer.getRSAEncryptionCredentials
     */
    private var rsaEncryptionCredentials: TlsCredentialedDecryptor? = null

    /**
     * @see DefaultTlsServer.getRSASignerCredentials
     */
    private var rsaSignerCredentials: TlsCredentialedSigner? = null

    /**
     * @see DefaultTlsServer.getECDSASignerCredentials
     */
    private var ecdsaSignerCredentials: TlsCredentialedSigner? = null

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation to explicitly specify cipher suites
     * which we know to be supported by Bouncy Castle and provide Perfect Forward Secrecy.
     *
     * @see org/bouncycastle/crypto/tls/DefaultTlsServer.java
     *
     * @see https://www.acunetix.com/blog/articles/tls-ssl-cipher-hardening/ Preferred Cipher Suite Order
     * CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
     * CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
     * CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
     * CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
     * CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
     * CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
     */
    public override fun getSupportedCipherSuites(): IntArray {
        // [52393, 49196, 49195]
        val cipherSuites_ecdsa = intArrayOf(
                CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256)

        // [52392, 49200, 49199, 49192, 49191, 49172, 49171, 52394, 159, 158, 107, 103, 57, 51]
        val cipherSuites_rsa = intArrayOf(
                CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,

                CipherSuite.TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_DHE_RSA_WITH_AES_256_GCM_SHA384,
                CipherSuite.TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,
                CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA256,
                CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA,
                CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA
        )

        /*
         * Do not offer CipherSuite.TLS_ECDHE_ECDSA... if the local certificateType is rsa_signed,
         * i.e. user has selected e.g. SHA256withRSA. This is used in certificate generation, and its
         * fingerPrint is advertised in jingle session-initiate. Changing the signer will cause fingerPrint
         * mismatch error in DtlsControlImpl#verifyAndValidateCertificate(Certificate certificate).
         *
         * Note: conversations/webrtc offers CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256, but webrtc:113.0.0
         * does not support SignatureAndHashAlgorithm for ECDSASignerCredential and failed decode_error(50)
         * See BC TlsUtils#isValidSignatureAlgorithmForServerKeyExchangeshort signatureAlgorithm, int keyExchangeAlgorithm)
         * for matching between SignatureAlgorithm and KeyExchangeAlgorithm.
         */
        return if (dtlsControl.certificateInfo!!.certificateType == ClientCertificateType.rsa_sign) {
            TlsUtils.getSupportedCipherSuites(crypto, cipherSuites_rsa)
        } else {
            TlsUtils.getSupportedCipherSuites(crypto, cipherSuites_ecdsa)
        }
    }

    /**
     * {@inheritDoc}
     * The implementation of `TlsServerImpl` always returns `ProtocolVersion.DTLSv12 & DTLSv10`
     */
    override fun getSupportedVersions(): Array<ProtocolVersion> {
        return ProtocolVersion.DTLSv12.downTo(ProtocolVersion.DTLSv10)
    }

    /**
     * Gets the `DtlsControl` implementation associated with this instance.
     *
     * @return the `DtlsControl` implementation associated with this instance
     */
    private val dtlsControl: DtlsControlImpl
        get() = mPacketTransformer.dtlsControl

    private val properties: Properties
        get() = mPacketTransformer.properties

    /**
     * {@inheritDoc}
     */
    override fun getCertificateRequest(): CertificateRequest {
        if (certificateRequest == null) {
            val certificateTypes = shortArrayOf(ClientCertificateType.rsa_sign,
                    ClientCertificateType.dss_sign, ClientCertificateType.ecdsa_sign)

            var serverSigAlgs: Vector<*>? = null
            if (TlsUtils.isSignatureAlgorithmsExtensionAllowed(context.serverVersion)) {
                serverSigAlgs = serverCertReqSigAlgs
                if (serverSigAlgs == null) {
                    serverSigAlgs = TlsUtils.getDefaultSupportedSignatureAlgorithms(context)
                }
            }

            val certificateAuthorities = Vector<X500Name>()
            certificateAuthorities.addElement(X500Name("CN=atalk.org TLS CA"))

//            Certificate certificate = getDtlsControl().getCertificateInfo().getCertificate();
//            TlsCertificate[] chain = certificate.getCertificateList();
//            try {
//                for (TlsCertificate tlsCertificate : chain) {
//                    org.bouncycastle.asn1.x509.Certificate entry = org.bouncycastle.asn1.x509.Certificate.getInstance(tlsCertificate.getEncoded());
//                    certificateAuthorities.addElement(entry.getIssuer());
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
            certificateRequest = CertificateRequest(certificateTypes, serverSigAlgs, certificateAuthorities)
        }

        // Timber.w("getCertificateRequest = CertificateTypes: %s;\nSupportedSignatureAlgorithms: %s;\nCertificateAuthorities: %s",
        //        Shorts.asList(certificateRequest.getCertificateTypes()), certificateRequest.getSupportedSignatureAlgorithms(), certificateRequest.getCertificateAuthorities());
        return certificateRequest!!
    }

    /**
     * {@inheritDoc}
     *
     * Depending on the `selectedCipherSuite`, `DefaultTlsServer` will require either
     * `rsaEncryptionCredentials` or `rsaSignerCredentials` neither of which is
     * implemented by `DefaultTlsServer`.
     */
    override fun getRSAEncryptionCredentials(): TlsCredentialedDecryptor {
        if (rsaEncryptionCredentials == null) {
            val certInfo = dtlsControl.certificateInfo!!
            val certificate = certInfo.certificate

            val crypto = context.crypto
            val privateKey = certInfo.keyPair.private
            rsaEncryptionCredentials = BcDefaultTlsCredentialedDecryptor(crypto as BcTlsCrypto, certificate, privateKey)
        }
        return rsaEncryptionCredentials!!
    }

    /**
     * {@inheritDoc}
     *
     * Depending on the `selectedCipherSuite`, `DefaultTlsServer` will require either
     * `rsaEncryptionCredentials` or `rsaSignerCredentials` neither of which is
     * implemented by `DefaultTlsServer`.
     *
     * @return TlsCredentialedSigner: rsaSignerCredentials
     */
    override fun getRSASignerCredentials(): TlsCredentialedSigner? {
        if (rsaSignerCredentials == null) {
            val certInfo = dtlsControl.certificateInfo!!
            val certificate = certInfo.certificate
            val sigAndHashAlg = getSigAndHashAlg(certificate) ?: return null

            val crypto = context.crypto
            val cryptoParams = TlsCryptoParameters(context)
            val privateKey = certInfo.keyPair.private
            rsaSignerCredentials = BcDefaultTlsCredentialedSigner(
                    cryptoParams, crypto as BcTlsCrypto, privateKey, certificate, sigAndHashAlg)
        }
        return rsaSignerCredentials!!
    }

    /**
     * {@inheritDoc}
     *
     * Depending on the `selectedCipherSuite`, `DefaultTlsServer` will require
     * `ecdsaSignerCredentials` which is not implemented by `DefaultTlsServer`
     * when cipherSuite is KeyExchangeAlgorithm.ECDHE_ECDSA
     *
     * @return TlsCredentialedSigner: ecdsaSignerCredentials
     */
    override fun getECDSASignerCredentials(): TlsCredentialedSigner? {
        if (ecdsaSignerCredentials == null) {
            // CertificateInfo certInfo = getDtlsControl().getCertificateInfo(getAlgorithmName("ECDSA"));
            val certInfo = dtlsControl.certificateInfo!!
            val certificate = certInfo.certificate
            val sigAndHashAlg = getSigAndHashAlg(certificate) ?: return null

            val crypto = context.crypto
            val cryptoParams = TlsCryptoParameters(context)
            val privateKey = certInfo.keyPair.private

            ecdsaSignerCredentials = BcDefaultTlsCredentialedSigner(
                    cryptoParams, crypto as BcTlsCrypto, privateKey, certificate, sigAndHashAlg)
        }
        return ecdsaSignerCredentials
    }

    /**
     * {@inheritDoc}
     *
     *
     * Includes the `use_srtp` extension in the DTLS extended server hello.
     */
    @Throws(IOException::class)
    override fun getServerExtensions(): Hashtable<*, *> {
        var serverExtensions = serverExtensionsOverride
        if (isSrtpDisabled) {
            return serverExtensions
        }

        if (TlsSRTPUtils.getUseSRTPExtension(serverExtensions) == null) {
            if (serverExtensions == null)
                serverExtensions = Hashtable<Any?, Any?>()
            val useSRTPData = TlsSRTPUtils.getUseSRTPExtension(clientExtensions)
            val chosenProtectionProfile = DtlsControlImpl.chooseSRTPProtectionProfile(*useSRTPData.protectionProfiles)

            /*
             * If there is no shared profile and that is not acceptable, the server SHOULD
             * return an appropriate DTLS alert.
             */
            if (chosenProtectionProfile == 0) {
                val msg = "No chosen SRTP protection profile!"
                val tfa = TlsFatalAlert(AlertDescription.internal_error)
                Timber.e(tfa, "%s", msg)
                throw tfa
            } else {
                /*
                 * Upon receipt of a "use_srtp" extension containing a "srtp_mki" field, the server
                 * MUST include a matching "srtp_mki" value in its "use_srtp" extension to indicate
                 * that it will make use of the MKI.
                 */
                TlsSRTPUtils.addUseSRTPExtension(serverExtensions, UseSRTPData(intArrayOf(chosenProtectionProfile), useSRTPData.mki))
                this.chosenProtectionProfile = chosenProtectionProfile
            }
        }
        return serverExtensions
    }
    /*
     * RFC 4492 5.2. A server that selects an ECC cipher suite in response to a ClientHello
     * message including a Supported Point Formats Extension appends this extension (along
     * with others) to its ServerHello message, enumerating the point formats it can parse.
     *//*
     * draft-ietf-tls-encrypt-then-mac-03 3. If a server receives an encrypt-then-MAC
     * request extension from a client and then selects a stream or AEAD cipher suite, it
     * MUST NOT send an encrypt-then-MAC response extension back to the client.
     */

    /**
     * FIXME: If Client Hello does not include points format extensions then we will end up with
     * alert 47 failure caused by NPE on serverECPointFormats. It was causing JitsiMeet to fail
     * with Android version of Chrome.
     *
     * The fix has been posted upstream and this method should be removed once it is published.
     */
    @get:Throws(IOException::class)
    private val serverExtensionsOverride: Hashtable<*, *>
        get() {
            if (encryptThenMACOffered && allowEncryptThenMAC()) {
                /*
                 * draft-ietf-tls-encrypt-then-mac-03 3. If a server receives an encrypt-then-MAC
                 * request extension from a client and then selects a stream or AEAD cipher suite, it
                 * MUST NOT send an encrypt-then-MAC response extension back to the client.
                 */
                if (TlsUtils.isBlockCipherSuite(selectedCipherSuite)) {
                    TlsExtensionsUtils.addEncryptThenMACExtension(checkServerExtensions())
                }
            }

            if (maxFragmentLengthOffered >= 0
                    && MaxFragmentLength.isValid(maxFragmentLengthOffered)) {
                TlsExtensionsUtils.addMaxFragmentLengthExtension(checkServerExtensions(), maxFragmentLengthOffered)
            }

            if (truncatedHMacOffered && allowTruncatedHMac()) {
                TlsExtensionsUtils.addTruncatedHMacExtension(checkServerExtensions())
            }

            if (TlsECCUtils.isECCCipherSuite(selectedCipherSuite)) {
                /*
                 * RFC 4492 5.2. A server that selects an ECC cipher suite in response to a ClientHello
                 * message including a Supported Point Formats Extension appends this extension (along
                 * with others) to its ServerHello message, enumerating the point formats it can parse.
                 */
                val serverECPointFormats = shortArrayOf(
                        ECPointFormat.uncompressed,
                        ECPointFormat.ansiX962_compressed_prime,
                        ECPointFormat.ansiX962_compressed_char2)
                TlsExtensionsUtils.addSupportedPointFormatsExtension(checkServerExtensions(), serverECPointFormats)
            }
            return serverExtensions
        }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation as a simple means of detecting that the security-related
     * negotiations between the local and the remote endpoints are starting. The detection carried
     * out for the purposes of `SrtpListener`.
     */
    override fun init(context: TlsServerContext) {
        super.init(context)
    }

    /**
     * Determines whether this `TlsServerImpl` is to operate in pure DTLS
     * mode without SRTP extensions or in DTLS/SRTP mode.
     *
     * @return `true` for pure DTLS without SRTP extensions or `false` for DTLS/SRTP
     */
    private val isSrtpDisabled: Boolean
        get() = properties.isSrtpDisabled

    /**
     * {@inheritDoc}
     *
     * Forwards to [.mPacketTransformer].
     */
    override fun notifyAlertRaised(alertLevel: Short, alertDescription: Short, message: String, cause: Throwable) {
        mPacketTransformer.notifyAlertRaised(this, alertLevel, alertDescription, message, cause)
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun notifyHandshakeComplete() {
        super.notifyHandshakeComplete()
        mPacketTransformer.initializeSRTPTransformer(chosenProtectionProfile, context)
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun notifyClientCertificate(clientCertificate: Certificate) {
        try {
            dtlsControl.verifyAndValidateCertificate(clientCertificate)
        } catch (e: Exception) {
            Timber.e(e, "Failed to verify and/or validate client certificate!")
            if (e is IOException) throw e else throw IOException(e)
        }
    }

    /**
     * {@inheritDoc}
     *
     * Makes sure that the DTLS extended client hello contains the `use_srtp` extension.
     */
    @Throws(IOException::class)
    override fun processClientExtensions(clientExtensions: Hashtable<*, *>?) {
        if (isSrtpDisabled) {
            super.processClientExtensions(clientExtensions)
            return
        }

        val useSRTPData = TlsSRTPUtils.getUseSRTPExtension(clientExtensions)
        if (useSRTPData == null) {
            val msg = "DTLS extended client hello does not include the use_srtp extension!"
            val ioe = IOException(msg)
            Timber.e(ioe, "%s", msg)
            throw ioe
        } else {
            val chosenProtectionProfile = DtlsControlImpl.chooseSRTPProtectionProfile(*useSRTPData.protectionProfiles)

            /*
             * If there is no shared profile and that is not acceptable, the server SHOULD
             * return an appropriate DTLS alert.
             */
            if (chosenProtectionProfile == 0) {
                val msg = "No chosen SRTP protection profile!"
                val tfa = TlsFatalAlert(AlertDescription.illegal_parameter)
                Timber.e(tfa, "%s", msg)
                throw tfa
            } else
                super.processClientExtensions(clientExtensions)
        }
    }

    companion object {
        /**
         * Obtain the SignatureAndHashAlgorithm based on the given certificate
         *
         * @param certificate containing info for SignatureAndHashAlgorithm
         *
         * @return SignatureAndHashAlgorithm
         */
        fun getSigAndHashAlg(certificate: Certificate?): SignatureAndHashAlgorithm? {
            // FIXME ed448/ed25519? multiple certificates?
            val algName = DefaultAlgorithmNameFinder().getAlgorithmName(
                    ASN1ObjectIdentifier(certificate!!.getCertificateAt(0).sigAlgOID)
            )

            val sigAndHashAlg = when (algName) {
                "SHA1WITHRSA" -> SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha1, SignatureAlgorithm.rsa)
                "SHA224WITHRSA" -> SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha224, SignatureAlgorithm.rsa)
                "SHA256WITHRSA" -> SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha256, SignatureAlgorithm.rsa)
                "SHA384WITHRSA" -> SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha384, SignatureAlgorithm.rsa)
                "SHA512WITHRSA" -> SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha512, SignatureAlgorithm.rsa)
                "SHA1WITHECDSA" -> SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha1, SignatureAlgorithm.ecdsa)
                "SHA224WITHECDSA" -> SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha224, SignatureAlgorithm.ecdsa)
                "SHA256WITHECDSA" -> SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha256, SignatureAlgorithm.ecdsa)
                "SHA384WITHECDSA" -> SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha384, SignatureAlgorithm.ecdsa)
                "SHA512WITHECDSA" -> SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha512, SignatureAlgorithm.ecdsa)
                 else -> {
                    Timber.w("Unsupported algOID in certificate: %s", algName)
                    return null
                }
            }

            Timber.d("TLS Certificate SignatureAndHashAlgorithm: %s", sigAndHashAlg)
            return sigAndHashAlg
        }
    }
}