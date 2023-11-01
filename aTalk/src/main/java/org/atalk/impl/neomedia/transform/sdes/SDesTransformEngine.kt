/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.sdes

import ch.imvs.sdes4j.srtp.SrtpCryptoAttribute
import ch.imvs.sdes4j.srtp.SrtpCryptoSuite
import org.atalk.impl.neomedia.transform.PacketTransformer
import org.atalk.impl.neomedia.transform.srtp.SRTCPTransformer
import org.atalk.impl.neomedia.transform.srtp.SRTPTransformer
import org.atalk.impl.neomedia.transform.srtp.SrtpContextFactory
import org.atalk.impl.neomedia.transform.srtp.SrtpPolicy
import org.atalk.service.neomedia.SrtpControl

/**
 * TransformEngine for SDES based SRTP encryption.
 *
 * @author Ingo Bauersachs
 * @author Eng Chong Meng
 */
class SDesTransformEngine(inAttribute: SrtpCryptoAttribute?, outAttribute: SrtpCryptoAttribute?) : SrtpControl.TransformEngine {
    private var srtpTransformer: SRTPTransformer? = null
    private var srtcpTransformer: SRTCPTransformer? = null
    private var inAttribute: SrtpCryptoAttribute? = null
    private var outAttribute: SrtpCryptoAttribute? = null
    private var reverseCtx: SrtpContextFactory? = null
    private var forwardCtx: SrtpContextFactory? = null

    /**
     * Creates a new instance of this class.
     *
     * inAttribute Key material for the incoming stream.
     * outAttribute Key material for the outgoing stream.
     */
    init {
        update(inAttribute, outAttribute)
    }

    /**
     * Updates this instance with new key materials.
     *
     * @param inAttribute Key material for the incoming stream.
     * @param outAttribute Key material for the outgoing stream.
     */
    fun update(inAttribute: SrtpCryptoAttribute?, outAttribute: SrtpCryptoAttribute?) {
        // Only reset the context if the new keys are really different, otherwise the ROC of an active
        // but paused (on hold) stream will be reset. A new stream (with a fresh context) should be
        // advertised by a new SSRC and thus receive a ROC of zero.
        var changed = false
        if (inAttribute != this.inAttribute) {
            this.inAttribute = inAttribute
            reverseCtx = getTransformEngine(inAttribute, false /* receiver */)
            changed = true
        }
        if (outAttribute != this.outAttribute) {
            this.outAttribute = outAttribute
            forwardCtx = getTransformEngine(outAttribute, true /* sender */)
            changed = true
        }
        if (changed) {
            if (srtpTransformer == null) {
                srtpTransformer = SRTPTransformer(forwardCtx, reverseCtx)
            } else {
                srtpTransformer!!.setContextFactory(forwardCtx, true)
                srtpTransformer!!.setContextFactory(reverseCtx, false)
            }
            if (srtcpTransformer == null) {
                srtcpTransformer = SRTCPTransformer(forwardCtx, reverseCtx)
            } else {
                srtcpTransformer!!.updateFactory(forwardCtx, true)
                srtcpTransformer!!.updateFactory(reverseCtx, false)
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun cleanup() {
        if (srtpTransformer != null) srtpTransformer!!.close()
        if (srtcpTransformer != null) srtcpTransformer!!.close()
        srtpTransformer = null
        srtcpTransformer = null
    }

    override val rtpTransformer: PacketTransformer?
        get() {
            return srtpTransformer
        }

    override val rtcpTransformer: PacketTransformer?
        get() {
            return srtcpTransformer
        }

    companion object {
        private fun getTransformEngine(attribute: SrtpCryptoAttribute?, sender: Boolean): SrtpContextFactory {
            val sessionParams = attribute!!.sessionParams
            require((sessionParams != null && sessionParams.isEmpty())) { "session parameters are not supported" }
            val cryptoSuite = attribute.cryptoSuite
            return SrtpContextFactory(
                    sender,
                    getKey(attribute),
                    getSalt(attribute),
                    SrtpPolicy(
                            getEncryptionCipher(cryptoSuite),
                            cryptoSuite.encKeyLength / 8,
                            getHashAlgorithm(cryptoSuite),
                            cryptoSuite.srtpAuthKeyLength / 8,
                            cryptoSuite.srtpAuthTagLength / 8,
                            cryptoSuite.saltKeyLength / 8),
                    SrtpPolicy(
                            getEncryptionCipher(cryptoSuite),
                            cryptoSuite.encKeyLength / 8,
                            getHashAlgorithm(cryptoSuite),
                            cryptoSuite.srtcpAuthKeyLength / 8,
                            cryptoSuite.srtcpAuthTagLength / 8,
                            cryptoSuite.saltKeyLength / 8))
        }

        private fun getKey(attribute: SrtpCryptoAttribute?): ByteArray {
            val length = attribute!!.cryptoSuite.encKeyLength / 8
            val key = ByteArray(length)
            System.arraycopy(attribute.keyParams[0].key, 0, key, 0, length)
            return key
        }

        private fun getSalt(attribute: SrtpCryptoAttribute?): ByteArray {
            val keyLength = attribute!!.cryptoSuite.encKeyLength / 8
            val saltLength = attribute.cryptoSuite.saltKeyLength / 8
            val salt = ByteArray(saltLength)
            System.arraycopy(attribute.keyParams[0].key, keyLength, salt, 0, saltLength)
            return salt
        }

        private fun getEncryptionCipher(cs: SrtpCryptoSuite): Int {
            return when (cs.encryptionAlgorithm) {
                SrtpCryptoSuite.ENCRYPTION_AES128_CM, SrtpCryptoSuite.ENCRYPTION_AES192_CM, SrtpCryptoSuite.ENCRYPTION_AES256_CM -> SrtpPolicy.Companion.AESCM_ENCRYPTION
                SrtpCryptoSuite.ENCRYPTION_AES128_F8 -> SrtpPolicy.Companion.AESF8_ENCRYPTION
                else -> throw IllegalArgumentException("Unsupported cipher")
            }
        }

        private fun getHashAlgorithm(cs: SrtpCryptoSuite): Int {
            return when (cs.hashAlgorithm) {
                SrtpCryptoSuite.HASH_HMAC_SHA1 -> SrtpPolicy.Companion.HMACSHA1_AUTHENTICATION
                else -> throw IllegalArgumentException("Unsupported hash")
            }
        }
    }
}