/*
 * Copyright @ 2015 - present 8x8, Inc
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

import org.bouncycastle.crypto.BlockCipher
import org.bouncycastle.crypto.params.KeyParameter
import java.util.*

/**
 * SrtpCipherF8 implements Srtp F8 Mode Encryption for 128 bits block cipher.
 * F8 Mode AES Encryption algorithm is defined in RFC3711, section 4.1.2.
 *
 * Other than Null Cipher, RFC3711 defined two two encryption algorithms:
 * Counter Mode AES Encryption and F8 Mode AES encryption. Both encryption
 * algorithms are capable to encrypt / decrypt arbitrary length data, and the
 * size of packet data is not required to be a multiple of the cipher block
 * size (128bit). So, no padding is needed.
 *
 * Please note: these two encryption algorithms are specially defined by SRTP.
 * They are not common AES encryption modes, so you will not be able to find a
 * replacement implementation in common cryptographic libraries.
 *
 * As defined by RFC3711: F8 mode encryption is optional.
 *
 * mandatory to impl     optional      default
 * -------------------------------------------------------------------------
 * encryption           AES-CM, NULL          AES-f8        AES-CM
 * message integrity    HMAC-SHA1                -          HMAC-SHA1
 * key derivation       (PRF) AES-CM             -          AES-CM
 *
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Werner Dittmann <werner.dittmann></werner.dittmann>@t-online.de>
 * @author Eng Chong Meng
 */
class SrtpCipherF8(
        /**
         * A 128 bits block cipher (AES or TwoFish)
         */
        private val cipher: BlockCipher?) {
    /**
     * F8 mode encryption context, see RFC3711 section 4.1.2 for detailed description.
     */
    internal class F8Context {
        lateinit var S: ByteArray
        lateinit var ivAccent: ByteArray
        var J = 0L
    }

    /**
     * Encryption key
     * (k_e)
     */
    private lateinit var encKey: ByteArray

    /**
     * Masked Encryption key (F8 mode specific)
     * (k_e XOR (k_s || 0x555..5))
     */
    private lateinit var maskedKey: ByteArray

    /**
     * @param k_e encryption key
     * @param k_s salt key
     */
    fun init(k_e: ByteArray, k_s: ByteArray) {
        require(k_e.size == BLKLEN) { "k_e.length != BLKLEN" }
        require(k_s.size <= k_e.size) { "k_s.length > k_e.length" }
        encKey = k_e.copyOf(k_e.size)

        /*
         * XOR the original key with the salt||0x55 to get
         * the special key maskedKey.
         */
        maskedKey = k_e.copyOf(k_e.size)
        var i = 0
        while (i < k_s.size) {
            maskedKey[i] = (maskedKey[i].toInt() xor k_s[i].toInt()).toByte()
            ++i
        }
        while (i < maskedKey.size) {
            maskedKey[i] = (maskedKey[i].toInt() xor 0x55).toByte()
            ++i
        }
    }

    fun process(data: ByteArray, off: Int, len: Int, iv: ByteArray) {
        var offset = off
        require(iv.size == BLKLEN) { "iv.length != BLKLEN" }
        require(offset >= 0) { "off < 0" }
        require(len >= 0) { "len < 0" }
        require(offset + len <= data.size) { "off + len > data.length" }
        /*
         * RFC 3711 says we should not encrypt more than 2^32 blocks which is
         * way more than java array max size, so no checks needed here
         */
        val f8ctx = F8Context()

        /*
         * Get memory for the derived IV (IV')
         */
        f8ctx.ivAccent = ByteArray(BLKLEN)

        /*
         * Encrypt the original IV to produce IV'.
         */cipher!!.init(true, KeyParameter(maskedKey))
        cipher.processBlock(iv, 0, f8ctx.ivAccent, 0)

        /*
         * re-init cipher with the "normal" key
         */cipher.init(true, KeyParameter(encKey))
        f8ctx.J = 0 // initialize the counter
        f8ctx.S = ByteArray(BLKLEN) // get the key stream buffer
        Arrays.fill(f8ctx.S, 0.toByte())
        var inLen = len
        while (inLen >= BLKLEN) {
            processBlock(f8ctx, data, offset, BLKLEN)
            inLen -= BLKLEN
            offset += BLKLEN
        }
        if (inLen > 0) {
            processBlock(f8ctx, data, offset, inLen)
        }
    }

    /**
     * Encrypt / Decrypt a block using F8 Mode AES algorithm, read len bytes
     * data from in at inOff and write the output into out at outOff
     *
     * @param f8ctx F8 encryption context
     * @param inOut byte array holding the data to be processed
     * @param off start offset of the data to be processed inside inOut array
     * @param len  length of the data to be processed inside inOut array from off
     */
    private fun processBlock(f8ctx: F8Context, inOut: ByteArray, off: Int, len: Int) {
        /*
         * XOR the previous key stream with IV'
         * ( S(-1) xor IV' )
         */
        for (i in 0 until BLKLEN) f8ctx.S[i] = (f8ctx.S[i].toInt() xor f8ctx.ivAccent[i].toInt()).toByte()

        /*
         * Now XOR (S(n-1) xor IV') with the current counter, then increment
         * the counter
         */
        f8ctx.S[12] = (f8ctx.S[12].toLong() xor (f8ctx.J shr 24)).toByte()
        f8ctx.S[13] = (f8ctx.S[13].toLong() xor (f8ctx.J shr 16)).toByte()
        f8ctx.S[14] = (f8ctx.S[14].toLong() xor (f8ctx.J shr 8)).toByte()
        f8ctx.S[15] = (f8ctx.S[15].toLong() xor f8ctx.J).toByte()
        f8ctx.J++

        /*
         * Now compute the new key stream using AES encrypt
         */cipher!!.processBlock(f8ctx.S, 0, f8ctx.S, 0)

        /*
         * As the last step XOR the plain text with the key stream to produce
         * the cipher text.
         */
        for (i in 0 until len) inOut[off + i] = (inOut[off + i].toInt() xor f8ctx.S[i].toInt()).toByte()
    }

    companion object {
        /**
         * block size, just a short name.
         */
        private const val BLKLEN = 16
    }
}