/*
 * Copyright @ 2019 - present 8x8, Inc
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
 * */
package org.atalk.impl.neomedia.transform.srtp

import org.atalk.impl.neomedia.transform.srtp.crypto.Aes
import org.atalk.impl.neomedia.transform.srtp.crypto.OpenSslWrapperLoader
import org.atalk.impl.neomedia.transform.srtp.crypto.SrtpCipherCtr
import org.atalk.impl.neomedia.transform.srtp.crypto.SrtpCipherCtrJava
import org.atalk.impl.neomedia.transform.srtp.crypto.SrtpCipherCtrOpenSsl
import org.bouncycastle.crypto.engines.TwofishEngine
import java.util.*

/**
 * SrtpKdf performs the SRTP key derivation function, as specified in section 4.3 of RFC 3711.
 */
internal class SrtpKdf(masterK: ByteArray, masterS: ByteArray?, policy: SrtpPolicy) {
    /**
     * implements the counter cipher mode for RTP key derivation according to RFC 3711
     */
    private var cipherCtr: SrtpCipherCtr? = null

    /**
     * Master salting key
     */
    private val masterSalt: ByteArray

    /**
     * Temp store.
     */
    private val ivStore = ByteArray(16)

    /**
     * Construct an SRTP Key Derivation Function object.
     *
     * masterK The master key from which to derive keys.
     * masterS The master salt from which to derive keys.
     * policy The SRTP policy to use for key derivation.
     */
    init {
        val encKeyLength = policy.encKeyLength
        cipherCtr = when (policy.encType) {
            SrtpPolicy.AESF8_ENCRYPTION, SrtpPolicy.AESCM_ENCRYPTION ->                 // use OpenSSL if available and AES128 is in use
                if (OpenSslWrapperLoader.isLoaded && encKeyLength == 16) {
                    SrtpCipherCtrOpenSsl()
                } else {
                    SrtpCipherCtrJava(Aes.createBlockCipher(encKeyLength))
                }
            SrtpPolicy.TWOFISHF8_ENCRYPTION, SrtpPolicy.TWOFISH_ENCRYPTION -> SrtpCipherCtrJava(TwofishEngine())
            SrtpPolicy.NULL_ENCRYPTION -> null
            else -> null
        }
        if (cipherCtr != null) {
            cipherCtr!!.init(masterK)
        }
        val saltKeyLength = policy.saltKeyLength
        masterSalt = ByteArray(saltKeyLength)
        if (saltKeyLength != 0) {
            System.arraycopy(masterS!!, 0, masterSalt, 0, saltKeyLength)
        }
    }

    /**
     * Derive a session key.
     *
     * @param sessKey A buffer into which the derived session key will be placed.
     * This should be allocated to be the desired key length.
     * @param label The key derivation label.
     */
    fun deriveSessionKey(sessKey: ByteArray?, label: Byte) {
        if (sessKey == null || sessKey.isEmpty()) {
            return
        }
        assert(masterSalt.size == 14)
        System.arraycopy(masterSalt, 0, ivStore, 0, masterSalt.size)
        ivStore[7] = (ivStore[7].toInt() xor label.toInt()).toByte()
        ivStore[14] = 0
        ivStore[15] = 0
        Arrays.fill(sessKey, 0.toByte())
        cipherCtr!!.process(sessKey, 0, sessKey.size, ivStore)
    }

    /**
     * Closes this KDF. The close function deletes key data and
     * performs a cleanup of this crypto context.
     */
    fun close() {
        /* TODO, clean up cipherCtr. */
    }

    companion object {
        /**
         * The SRTP KDF label value for RTP encryption.
         */
        const val LABEL_RTP_ENCRYPTION: Byte = 0x00

        /**
         * The SRTP KDF label value for RTP message authentication.
         */
        const val LABEL_RTP_MSG_AUTH: Byte = 0x01

        /**
         * The SRTP KDF label value for RTP message salting.
         */
        const val LABEL_RTP_SALT: Byte = 0x02

        /**
         * The SRTP KDF label value for RTCP encryption.
         */
        const val LABEL_RTCP_ENCRYPTION: Byte = 0x03

        /**
         * The SRTP KDF label value for RTCP message authentication.
         */
        const val LABEL_RTCP_MSG_AUTH: Byte = 0x04

        /**
         * The SRTP KDF label value for RTCP message salting.
         */
        const val LABEL_RTCP_SALT: Byte = 0x05
    }
}