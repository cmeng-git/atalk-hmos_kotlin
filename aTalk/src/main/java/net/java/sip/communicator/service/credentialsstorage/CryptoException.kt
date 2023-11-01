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
package net.java.sip.communicator.service.credentialsstorage

/**
 * Exception thrown by the Crypto encrypt/decrypt interface methods.
 *
 * @author Dmitri Melnikov
 */
class CryptoException
/**
 * Constructs the crypto exception.
 *
 * @param code the error code
 * @param cause the original exception that this instance wraps
 */
(
        /**
         * The error code of this exception.
         */
        val errorCode: Int, cause: Exception?) : Exception(cause) {
    /**
     * @return the error code for the exception.
     */

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = -5424208764356198091L

        /**
         * Set when encryption fails.
         */
        const val ENCRYPTION_ERROR = 1

        /**
         * Set when decryption fails.
         */
        const val DECRYPTION_ERROR = 2

        /**
         * Set when a decryption fail is caused by the wrong key.
         */
        const val WRONG_KEY = 3
    }
}