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
 * Loads and saves user credentials from/to the persistent storage
 * (configuration file in the default implementation).
 *
 * @author Dmitri Melnikov
 * @author Eng Chong Meng
 */
interface CredentialsStorageService {
    /**
     * Store the password for the account that starts with the given prefix.
     *
     * @param accountUuid account UUID
     * @param password the password to store
     * @return `true` if the specified `password` was successfully
     * stored; otherwise, `false`
     */
    fun storePassword(accountUuid: String, password: String?): Boolean

    /**
     * Load the password for the account that starts with the given prefix.
     *
     * @param accountUuid account UUID
     * @return the loaded password for the `accountUuid`
     */
    fun loadPassword(accountUuid: String): String?

    /**
     * Remove the password for the account that starts with the given prefix.
     *
     * @param accountUuid account UUID
     * @return `true` if the password for the specified
     * `accountUuid` was successfully removed; otherwise,
     * `false`
     */
    fun removePassword(accountUuid: String): Boolean

    /**
     * Checks if master password was set by the user and
     * it is used to encrypt saved account passwords.
     *
     * @return `true` if used, `false` if not
     */
    val isUsingMasterPassword: Boolean

    /**
     * Changes the old master password to the new one.
     * For all saved account passwords it decrypts them with the old MP and then
     * encrypts them with the new MP.
     *
     * @param oldPassword the old master password
     * @param newPassword the new master password
     * @return `true` if master password was changed successfully;
     * `false`, otherwise
     */
    fun changeMasterPassword(oldPassword: String?, newPassword: String?): Boolean

    /**
     * Verifies the correctness of the master password.
     *
     * @param master the master password to verify
     * @return `true` if the password is correct; `false`,
     * otherwise
     */
    fun verifyMasterPassword(master: String?): Boolean

    /**
     * Checks if the account password that starts with the given prefix is saved
     * in encrypted form.
     *
     * @param accountUuid account UUID
     * @return `true` if saved, `false` if not
     */
    fun isStoredEncrypted(accountUuid: String?): Boolean
}