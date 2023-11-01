/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.dtls

import org.bouncycastle.tls.*
import org.bouncycastle.tls.crypto.TlsCryptoParameters
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto
import timber.log.Timber
import java.io.IOException
import java.security.SecureRandom
import java.util.*

/**
 * Implements [TlsClientContext] for the purposes of supporting DTLS-SRTP - DTLSv12/DTLSv10.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
/**
 * Initializes a new `TlsClientImpl` instance.
 */
class TlsClientImpl
    (
        /**
         * The `PacketTransformer` which has initialized this instance.
         */
        private val packetTransformer: DtlsPacketTransformer,
) : DefaultTlsClient(BcTlsCrypto(SecureRandom())) {

    private val authentication = TlsAuthenticationImpl()

    /**
     * The `SRTPProtectionProfile` negotiated between this DTLS-SRTP client and its server.
     */
    private var chosenProtectionProfile = 0

    /**
     * The SRTP Master Key Identifier (MKI) used by the `SrtpCryptoContext` associated with this
     * instance. Since the `SrtpCryptoContext` class does not utilize it, the value is [TlsUtils.EMPTY_BYTES].
     */
    private val mki = TlsUtils.EMPTY_BYTES

    /**
     * {@inheritDoc}
     */
    @Synchronized
    override fun getAuthentication(): TlsAuthentication {
        return authentication
    }

    /**
     * {@inheritDoc}
     * The implementation of `TlsClientImpl` always returns `ProtocolVersion.DTLSv12 & DTLSv10`
     */
    override fun getSupportedVersions(): Array<ProtocolVersion> {
        return ProtocolVersion.DTLSv12.downTo(ProtocolVersion.DTLSv10)
    }

    /**
     * {@inheritDoc}
     *
     * Includes the `use_srtp` extension in the DTLS extended client hello.
     */
    @Throws(IOException::class)
    override fun getClientExtensions(): Hashtable<*, *>? {
        var clientExtensions = super.getClientExtensions()
        if (!isSrtpDisabled
                && TlsSRTPUtils.getUseSRTPExtension(clientExtensions) == null) {
            if (clientExtensions == null)
                clientExtensions = Hashtable<Any?, Any?>()
            TlsSRTPUtils.addUseSRTPExtension(clientExtensions,
                UseSRTPData(DtlsControlImpl.SRTP_PROTECTION_PROFILES, mki))
        }
        return clientExtensions
    }

    /**
     * Determines whether this `TlsClientImpl` is to operate in pure DTLS
     * mode without SRTP extensions or in DTLS/SRTP mode.
     *
     * @return `true` for pure DTLS without SRTP extensions or `false` for DTLS/SRTP
     */
    private val isSrtpDisabled: Boolean
        get() = packetTransformer.properties.isSrtpDisabled

    /**
     * {@inheritDoc}
     *
     * Forwards to [.mPacketTransformer].
     */
    override fun notifyAlertRaised(alertLevel: Short, alertDescription: Short, message: String, cause: Throwable) {
        packetTransformer.notifyAlertRaised(this, alertLevel, alertDescription, message, cause)
    }

    override fun notifyHandshakeComplete() {
        if (isSrtpDisabled) {
            // SRTP is disabled, nothing to do. Why did we get here in the first place?
            return
        }
        val srtpTransformer = packetTransformer.initializeSRTPTransformer(chosenProtectionProfile, context)
        synchronized(packetTransformer) {
            packetTransformer.setSrtpTransformer(srtpTransformer)
        }
    }

    /**
     * {@inheritDoc}
     *
     * Makes sure the DTLS extended server hello contains the `use_srtp` extension.
     */
    @Throws(IOException::class)
    override fun processServerExtensions(serverExtensions: Hashtable<*, *>?) {
        if (isSrtpDisabled) {
            super.processServerExtensions(serverExtensions)
            return
        }

        val useSRTPData = TlsSRTPUtils.getUseSRTPExtension(serverExtensions)
        chosenProtectionProfile = if (useSRTPData == null) {
            val msg = "DTLS extended server hello does not include the use_srtp extension!"
            val ioe = IOException(msg)
            Timber.e(ioe, "%s", msg)
            throw ioe
        }
        else {
            val protectionProfiles = useSRTPData.protectionProfiles
            val chosenProtectionProfile = if (protectionProfiles.size == 1)
                DtlsControlImpl.chooseSRTPProtectionProfile(protectionProfiles[0])
            else 0

            if (chosenProtectionProfile == 0) {
                val msg = "No chosen SRTP protection profile!"
                val tfa = TlsFatalAlert(AlertDescription.illegal_parameter)
                Timber.e(tfa, "%s", msg)
                throw tfa
            }
            else {
                /*
                 * If the client detects a nonzero-length MKI in the server's response that is
                 * different than the one the client offered, then the client MUST abort the
                 * handshake and SHOULD send an invalid_parameter alert.
                 */
                val mki = useSRTPData.mki
                if (Arrays.equals(mki, this.mki)) {
                    super.processServerExtensions(serverExtensions)
                    chosenProtectionProfile
                }
                else {
                    val msg = "Server's MKI does not match the one offered by this client!"
                    val tfa = TlsFatalAlert(AlertDescription.illegal_parameter)
                    Timber.e(tfa, "%s", msg)
                    throw tfa
                }
            }
        }
    }

    /**
     * Implements [TlsAuthentication] for the purposes of supporting DTLS-SRTP.
     */
    private inner class TlsAuthenticationImpl : TlsAuthentication {
        private var clientCredentials: TlsCredentials? = null

        /**
         * {@inheritDoc}
         */
        override fun getClientCredentials(certificateRequest: CertificateRequest): TlsCredentials? {
            if (clientCredentials == null) {
                // get the certInfo for the certificate that was used in Jingle session-accept setup
                val certInfo = packetTransformer.dtlsControl.certificateInfo!!
                val certificate = certInfo.certificate

                val sigAndHashAlg = TlsServerImpl.getSigAndHashAlg(certificate)
                        ?: return null

                val crypto = context.crypto
                val cryptoParams = TlsCryptoParameters(context)
                val privateKey = certInfo.keyPair.private
                clientCredentials = BcDefaultTlsCredentialedSigner(
                    cryptoParams, crypto as BcTlsCrypto, privateKey, certificate, sigAndHashAlg)
            }
            return clientCredentials
        }

        /**
         * {@inheritDoc}
         */
        @Throws(IOException::class)
        override fun notifyServerCertificate(serverCertificate: TlsServerCertificate) {
            try {
                packetTransformer.dtlsControl.verifyAndValidateCertificate(serverCertificate.certificate)
            } catch (e: Exception) {
                Timber.e(e, "Failed to verify and/or validate server certificate!")
                if (e is IOException)
                    throw e
                else
                    throw IOException(e)
            }
        }
    }
}