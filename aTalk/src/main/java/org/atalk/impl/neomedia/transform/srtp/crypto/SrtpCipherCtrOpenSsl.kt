/*
 * Copyright @ 2016 - present 8x8, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.transform.srtp.crypto

/**
 * @see SrtpCipherCtr
 * SrtpCipherCtr implementation using OpenSSL via JNI.
 */
class SrtpCipherCtrOpenSsl : SrtpCipherCtr() {
    private var key_length = -1

    /**
     * the OpenSSL AES128CTR / AES192 / AES256 context
     */
    private var ctx: Long = 0

    init {
        if (!OpenSslWrapperLoader.isLoaded) throw RuntimeException("OpenSSL wrapper not loaded")
        ctx = AES128CTR_CTX_create()
        if (ctx == 0L) throw RuntimeException("CIPHER_CTX_create")
    }

    /**
     * {@inheritDoc}
     */
    override fun init(key: ByteArray) {
        when (key.size) {
            16 -> if (!AES128CTR_CTX_init(ctx, key)) throw RuntimeException("AES128CTR_CTX_init")
            24 -> if (!AES192CTR_CTX_init(ctx, key)) throw RuntimeException("AES256CTR_CTX_init")
            32 -> if (!AES256CTR_CTX_init(ctx, key)) throw RuntimeException("AES256CTR_CTX_init")
            else -> throw IllegalArgumentException("Only AES128, AES192 and AES256 is supported")
        }
        key_length = key.size
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
                AES128CTR_CTX_destroy(ctx)
                ctx = 0
            }
        } finally {
            // super.finalize()
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun process(data: ByteArray, off: Int, len: Int, iv: ByteArray) {
        checkProcessArgs(data, off, len, iv)
        when (key_length) {
            16 -> if (!AES128CTR_CTX_process(ctx, iv, data, off, len)) throw RuntimeException("AES128CTR_CTX_process")
            24 -> if (!AES192CTR_CTX_process(ctx, iv, data, off, len)) throw RuntimeException("AES192CTR_CTX_process")
            32 -> if (!AES256CTR_CTX_process(ctx, iv, data, off, len)) throw RuntimeException("AES256CTR_CTX_process")
            else -> throw IllegalArgumentException("Only AES128, AES192 and AES256 is supported")
        }
    }

    private external fun AES128CTR_CTX_create(): Long
    private external fun AES128CTR_CTX_destroy(ctx: Long)
    private external fun AES128CTR_CTX_init(ctx: Long, key: ByteArray): Boolean
    private external fun AES128CTR_CTX_process(ctx: Long, iv: ByteArray, inOut: ByteArray, offset: Int, len: Int): Boolean
    private external fun AES192CTR_CTX_init(ctx: Long, key: ByteArray): Boolean
    private external fun AES192CTR_CTX_process(ctx: Long, iv: ByteArray, inOut: ByteArray, offset: Int, len: Int): Boolean
    private external fun AES256CTR_CTX_init(ctx: Long, key: ByteArray): Boolean
    private external fun AES256CTR_CTX_process(ctx: Long, iv: ByteArray, inOut: ByteArray, offset: Int, len: Int): Boolean

    companion object {
    }
}