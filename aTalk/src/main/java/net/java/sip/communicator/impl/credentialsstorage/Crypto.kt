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

/**
 * Allows to encrypt and decrypt text using a symmetric algorithm.
 *
 * @author Dmitri Melnikov
 * @author Eng Chong Meng
 */
interface Crypto {
    /**
     * Decrypts the cipher text and returns the result.
     *
     * @param ciphertext base64 encoded encrypted data
     * @return decrypted data
     * @throws CryptoException when the ciphertext cannot be decrypted with the
     * key or on decryption error.
     */
    @Throws(CryptoException::class)
    fun decrypt(ciphertext: String?): String

    /**
     * Encrypts the plain text and returns the result.
     *
     * @param plaintext data to be encrypted
     * @return base64 encoded encrypted data
     * @throws CryptoException on encryption error
     */
    @Throws(CryptoException::class)
    fun encrypt(plaintext: String?): String
}