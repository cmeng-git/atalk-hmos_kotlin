/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.srtp.crypto

import org.bouncycastle.crypto.BlockCipher
import org.bouncycastle.crypto.CipherParameters
import org.bouncycastle.crypto.DataLengthException
import org.bouncycastle.crypto.params.KeyParameter
import timber.log.Timber
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.ShortBufferException
import javax.crypto.spec.SecretKeySpec

/**
 * Adapts the `javax.crypto.Cipher` class to the `org.bouncycastle.crypto.BlockCipher` interface.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class BlockCipherAdapter(cipher: Cipher?) : BlockCipher {
    /**
     * The name of the algorithm implemented by this instance.
     */
    private val algorithmName: String

    /**
     * The block size in bytes of this cipher.
     */
    private val blockSize: Int

    /**
     * The `javax.crypto.Cipher` instance which is adapted to the
     * `org.bouncycastle.crypto.BlockCipher` interface by this instance.
     */
    val cipher: Cipher

    /**
     * Initializes a new `BlockCipherAdapter` instance which is to adapt a specific
     * `javax.crypto.Cipher` instance to the `org.bouncycastle.crypto.BlockCipher` interface.
     */
    init {
        if (cipher == null) throw NullPointerException("cipher")
        this.cipher = cipher

        // The value of the algorithm property of javax.crypto.Cipher is a transformation i.e.
        // it may contain mode and padding. However, the algorithm name alone is necessary elsewhere.
        var algorithmName = cipher.algorithm

        if (algorithmName != null) {
            val endIndex = algorithmName.indexOf('/')
            if (endIndex > 0) algorithmName = algorithmName.substring(0, endIndex)
            val len = algorithmName.length

            if (len > 4 && (algorithmName.endsWith("_128")
                            || algorithmName.endsWith("_192")
                            || algorithmName.endsWith("_256"))) {
                algorithmName = algorithmName.substring(0, len - 4)
            }
        }

        this.algorithmName = algorithmName
        blockSize = cipher.blockSize
    }

    /**
     * {@inheritDoc}
     */
    override fun getAlgorithmName(): String {
        return algorithmName
    }

    /**
     * {@inheritDoc}
     */
    override fun getBlockSize(): Int {
        return blockSize
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IllegalArgumentException::class)
    override fun init(forEncryption: Boolean, params: CipherParameters) {
        var key: Key? = null

        if (params is KeyParameter) {
            val bytes = params.key
            if (bytes != null)
                key = SecretKeySpec(bytes, getAlgorithmName())
        }

        try {
            cipher.init(if (forEncryption) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, key)
        } catch (ike: InvalidKeyException) {
            Timber.e(ike, "%s", ike.message)
            throw IllegalArgumentException(ike)
        }
    }

    /**
     * {@inheritDoc}
     */
    @Throws(DataLengthException::class, IllegalStateException::class)
    override fun processBlock(`in`: ByteArray, inOff: Int, out: ByteArray, outOff: Int): Int {
        return try {
            cipher.update(`in`, inOff, getBlockSize(), out, outOff)
        } catch (sbe: ShortBufferException) {
            Timber.e(sbe, "%s", sbe.message)
            val dle = DataLengthException()
            dle.initCause(sbe)
            throw dle
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun reset() {
        try {
            cipher.doFinal()
        } catch (gse: GeneralSecurityException) {
            Timber.e(gse, "%s", gse.message)
        }
    }
}