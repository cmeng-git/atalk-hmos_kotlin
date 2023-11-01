/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.srtp.crypto

import org.bouncycastle.crypto.CipherParameters
import org.bouncycastle.crypto.DataLengthException
import org.bouncycastle.crypto.Mac
import org.bouncycastle.crypto.params.KeyParameter

/**
 * Implements the interface `org.bouncycastle.crypto.Mac` using the OpenSSL Crypto library.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class OpenSslHmac(digestAlgorithm: Int) : Mac {
    /**
     * The context of the OpenSSL (Crypto) library through which the actual
     * algorithm implementation is invoked by this instance.
     */
    private var ctx: Long

    /**
     * The key provided in the form of a [KeyParameter] in the last invocation of [.init].
     */
    private var key: ByteArray? = null

    /**
     * The block size in bytes for this MAC.
     */
    private val macSize: Int

    /**
     * The OpenSSL Crypto type of the message digest implemented by this instance.
     */
    private val md: Long

    /**
     * Initializes a new `OpenSslHmac` instance with a specific digest algorithm.
     *
     * digestAlgorithm the algorithm of the digest to initialize the new instance with SHA1
     */
    init {
        if (!OpenSslWrapperLoader.isLoaded)
            throw RuntimeException("OpenSSL wrapper not loaded")
        require(digestAlgorithm == SHA1) {
            "digestAlgorithm $digestAlgorithm"
        }

        md = EVP_sha1()
        check(md != 0L) {
            "EVP_sha1 == 0"
        }

        macSize = EVP_MD_size(md)
        check(macSize != 0) {
            "EVP_MD_size == 0"
        }

        ctx = HMAC_CTX_create()
        if (ctx == 0L) throw RuntimeException("HMAC_CTX_create == 0")
    }

    /**
     * {@inheritDoc}
     */
    @Throws(DataLengthException::class, IllegalStateException::class)
    override fun doFinal(out: ByteArray?, outOff: Int): Int {
        if (out == null)
            throw NullPointerException("out")
        if (outOff < 0 || out.size <= outOff)
            throw ArrayIndexOutOfBoundsException(outOff)

        var outLen = out.size - outOff
        val macSize = getMacSize()

        if (outLen < macSize) {
            throw DataLengthException("Space in out must be at least " + macSize
                    + "bytes but is " + outLen + " bytes!")
        }

        return if (ctx == 0L) {
            throw IllegalStateException("ctx")
        } else {
            outLen = HMAC_Final(ctx, out, outOff, outLen)
            if (outLen < 0) {
                throw RuntimeException("HMAC_Final")
            } else {
                // As the javadoc on interface method specifies, the doFinal
                // call leaves this Digest reset.
                reset()
                outLen
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Throws(Throwable::class)
    protected fun finalize() {
        try {
            // Well, the destroying in the finalizer should exist as a backup
            // anyway. There is no way to explicitly invoke the destroying at
            // the time of this writing but it is a start.
            if (ctx != 0L) {
                this.ctx = 0
                HMAC_CTX_destroy(ctx)
            }
        } finally {
            // super.finalize()
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun getAlgorithmName(): String {
        return Companion.algorithmName
    }

    /**
     * {@inheritDoc}
     */
    override fun getMacSize(): Int {
        return macSize
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IllegalArgumentException::class)
    override fun init(params: CipherParameters) {
        key = if (params is KeyParameter)
            params.key
        else
            null

        checkNotNull(key) { "key == null" }
        check(ctx != 0L) { "ctx == 0" }

        if (!HMAC_Init_ex(ctx, key, key!!.size, md, 0))
            throw RuntimeException("HMAC_Init_ex() init failed")
    }

    /**
     * {@inheritDoc}
     */
    override fun reset() {
        checkNotNull(key) { "key == null" }
        check(ctx != 0L) { "ctx == 0" }

        // just reset the ctx (keep same key and md)
        if (!HMAC_Init_ex(ctx, null, 0, 0, 0))
            throw RuntimeException("HMAC_Init_ex() reset failed")
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IllegalStateException::class)
    override fun update(byte: Byte) {
        // TODO Auto-generated method stub
    }

    /**
     * {@inheritDoc}
     */
    @Throws(DataLengthException::class, IllegalStateException::class)
    override fun update(bytes: ByteArray?, off: Int, len: Int) {
        if (len != 0) {
            if (bytes == null)
                throw NullPointerException("in")
            if (off < 0 || bytes.size <= off)
                throw ArrayIndexOutOfBoundsException(off)

            if (len < 0 || bytes.size < (off + len))
                throw java.lang.IllegalArgumentException("len $len")

            //val ctx = ctx
            check(ctx != 0L) { "ctx" }

            if (!HMAC_Update(ctx, bytes, off, len)) throw RuntimeException("HMAC_Update")
        }
    }

    private external fun EVP_MD_size(md: Long): Int
    private external fun EVP_sha1(): Long
    private external fun HMAC_CTX_create(): Long
    private external fun HMAC_CTX_destroy(ctx: Long)
    private external fun HMAC_Final(ctx: Long, md: ByteArray, mdOff: Int, mdLen: Int): Int
    private external fun HMAC_Init_ex(ctx: Long, key: ByteArray?, keyLen: Int, md: Long, impl: Long): Boolean
    private external fun HMAC_Update(ctx: Long, data: ByteArray, off: Int, len: Int): Boolean

    companion object {
        /**
         * The name of the algorithm implemented by this instance.
         */
        private const val algorithmName = "SHA-1/HMAC"

        /**
         * The algorithm of the SHA-1 cryptographic hash function/digest.
         */
        const val SHA1 = 1
    }
}