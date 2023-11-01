/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
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
package net.java.sip.communicator.impl.credentialsstorage

import net.java.sip.communicator.service.credentialsstorage.CryptoException
import net.java.sip.communicator.util.Base64
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.Key
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException
import java.security.spec.KeySpec
import javax.crypto.*
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Performs encryption and decryption of text using AES algorithm.
 *
 * @author Dmitri Melnikov
 * @author Eng Chong Meng
 */
class AESCrypto constructor(masterPassword: String?) : Crypto {
    /**
     * Key derived from the master password to use for encryption/decryption.
     */
    private var key: Key? = null

    /**
     * Decryption object.
     */
    private var decryptCipher: Cipher? = null

    /**
     * Encryption object.
     */
    private var encryptCipher: Cipher? = null

    /**
     * Creates the encryption and decryption objects and the key.
     *
     * @param masterPassword used to derive the key. Can be null.
     */
    init {
        try {
            // we try init of key with suupplied lengths
            // we stop after the first successful attempt
            for (i in KEY_LENGTHS.indices) {
                decryptCipher = Cipher.getInstance(CIPHER_ALGORITHM)
                encryptCipher = Cipher.getInstance(CIPHER_ALGORITHM)
                try {
                    initKey(masterPassword, KEY_LENGTHS[i])

                    // its ok stop trying
                    break
                } catch (e: InvalidKeyException) {
                    if (i == KEY_LENGTHS.size - 1) throw e
                }
            }
        } catch (e: InvalidKeyException) {
            throw RuntimeException("Invalid key", e)
        } catch (e: InvalidKeySpecException) {
            throw RuntimeException("Invalid key specification", e)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("Algorithm not found", e)
        } catch (e: NoSuchPaddingException) {
            throw RuntimeException("Padding not found", e)
        }
    }

    /**
     * Initialize key with specified length.
     *
     * @param masterPassword used to derive the key. Can be null.
     * @param keyLength Length of the key in bits.
     * @throws InvalidKeyException if the key is invalid (bad encoding,
     * wrong length, uninitialized, etc).
     * @throws NoSuchAlgorithmException if the algorithm chosen does not exist
     * @throws InvalidKeySpecException if the key specifications are invalid
     */
    @Throws(InvalidKeyException::class, NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    private fun initKey(masterPassword: String?, keyLength: Int) {
        // if the password is empty, we get an exception constructing the key
        var iMasterPassword = masterPassword
        if (iMasterPassword == null) {
            // here a default password can be set,
            // cannot be an empty string
            iMasterPassword = " "
        }

        // Password-Based Key Derivation Function found in PKCS5 v2.0.
        // This is only available with java 6.
        // SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBEWithSHA256And256BitAES-CBC-BC");
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        // Make a key from the master password
        val spec = PBEKeySpec(iMasterPassword.toCharArray(), SALT, ITERATION_COUNT, keyLength)
        val tmp = factory.generateSecret(spec)
        // Make an algorithm specific key
        key = SecretKeySpec(tmp.encoded, KEY_ALGORITHM)

        // just a check whether the key size is wrong
        encryptCipher!!.init(Cipher.ENCRYPT_MODE, key)
        decryptCipher!!.init(Cipher.DECRYPT_MODE, key)
    }

    /**
     * Decrypts the cyphertext using the key.
     *
     * @param ciphertext base64 encoded encrypted data
     * @return decrypted data
     * @throws CryptoException when the ciphertext cannot be decrypted with the
     * key or on decryption error.
     */
    @Throws(CryptoException::class)
    override fun decrypt(ciphertext: String?): String {
        try {
            decryptCipher!!.init(Cipher.DECRYPT_MODE, key)
            return String(decryptCipher!!.doFinal(Base64.decode((ciphertext)!!)), StandardCharsets.UTF_8)
        } catch (e: BadPaddingException) {
            throw CryptoException(CryptoException.WRONG_KEY, e)
        } catch (e: Exception) {
            throw CryptoException(CryptoException.DECRYPTION_ERROR, e)
        }
    }

    /**
     * Encrypts the plaintext using the key.
     *
     * @param plaintext data to be encrypted
     * @return base64 encoded encrypted data
     * @throws CryptoException on encryption error
     */
    @Throws(CryptoException::class)
    override fun encrypt(plaintext: String?): String {
        try {
            encryptCipher!!.init(Cipher.ENCRYPT_MODE, key)
            return String(Base64.encode(encryptCipher!!.doFinal(plaintext!!.toByteArray(StandardCharsets.UTF_8))))
        } catch (e: Exception) {
            throw CryptoException(CryptoException.ENCRYPTION_ERROR, e)
        }
    }

    companion object {
        /**
         * The algorithm associated with the key.
         */
        private const val KEY_ALGORITHM = "AES"

        /**
         * AES in ECB mode with padding.
         */
        private const val CIPHER_ALGORITHM = "AES/ECB/PKCS5PADDING"

        /**
         * Salt used when creating the key.
         */
        private val SALT = byteArrayOf(0x0C, 0x0A, 0x0F, 0x0E, 0x0B, 0x0E, 0x0E, 0x0F)

        /**
         * Possible length of the keys in bits.
         */
        private val KEY_LENGTHS = intArrayOf(256, 128)

        /**
         * Number of iterations to use when creating the key.
         */
        private const val ITERATION_COUNT = 1024
    }
}