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

import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService
import net.java.sip.communicator.service.credentialsstorage.CryptoException
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.service.configuration.ConfigurationService
import org.bouncycastle.util.encoders.Base64
import org.osgi.framework.BundleContext
import timber.log.Timber

/**
 * Implements [CredentialsStorageService] to load and store user credentials from/to the
 * [ConfigurationService].
 *
 * @author Dmitri Melnikov
 * @author Eng Chong Meng
 */
class CredentialsStorageServiceImpl : CredentialsStorageService {
    /**
     * The configuration service.
     */
    private lateinit var configurationService: ConfigurationService

    /**
     * A [Crypto] instance that does the actual encryption and decryption.
     */
    private var crypto: Crypto? = null

    /**
     * Initializes the credentials service by fetching the configuration service reference from
     * the bundle context. Encrypts and moves all passwords to new properties.
     *
     * @param bc bundle context
     */
    fun start(bc: BundleContext?) {
        configurationService = ServiceUtils.getService(bc, ConfigurationService::class.java)!!

        /*
         * If a master password is set, the migration of the unencrypted passwords will have to
         * wait for the UIService to register in order to be able to ask for the master password.
         * But that is unreasonably late in the case of no master password.
         */
        if (!isUsingMasterPassword) moveAllPasswordProperties()
    }

    /**
     * Forget the encryption/decryption key when stopping the service.
     */
    fun stop() {
        crypto = null
    }

    /**
     * Stores the password for the specified account. When password is null the property is cleared.
     *
     * Many threads can call this method at the same time, and the first thread may present the
     * user with the master password prompt and create a `Crypto` instance based on the input
     * (`createCrypto` method). This instance will be used later by all other threads.
     *
     * @param accountUuid account UUID
     * @param password the password to store; remove if password == null
     * @return `true` if the specified `password` was successfully stored; otherwise, `false`
     * @see CredentialsStorageServiceImpl.storePassword
     */
    @Synchronized
    override fun storePassword(accountUuid: String, password: String?): Boolean {
        return if (createCrypto()) {
            val encryptedPassword: String?
            try {
                if (password != null) {
                    encryptedPassword = crypto!!.encrypt(password)
                    setEncrypted(accountUuid, encryptedPassword)
                } else {
                    setEncrypted(accountUuid, null)
                }
                true
            } catch (ex: Exception) {
                Timber.e(ex, "Encryption failed, password not saved")
                false
            }
        } else false
    }

    /**
     * Loads the password for the specified account. If the password is stored encrypted,
     * decrypts it with the master password.
     *
     * Many threads can call this method at the same time, and the first thread may present the
     * user with the master password prompt and create a `Crypto` instance based on the
     * input (`createCrypto` method). This instance will be used later by all other threads.
     *
     * @param accountUuid account UUID
     * @return the loaded password for the `accountUuid`
     * @see CredentialsStorageServiceImpl.createCrypto
     */
    @Synchronized
    override fun loadPassword(accountUuid: String): String? {
        var password: String? = null
        if (isStoredEncrypted(accountUuid) && createCrypto()) {
            try {
                password = crypto!!.decrypt(getEncrypted(accountUuid))
            } catch (ex: Exception) {
                // password stays null
                Timber.e(ex, "Decryption with master password failed")
            }
        }
        return password
    }

    /**
     * Removes the password for the account that starts with the given prefix by setting its
     * value in the configuration to null.
     *
     * @param accountUuid account UUID
     * @return `true` if the password for the specified `accountUuid` was successfully removed; otherwise, `false`
     */
    override fun removePassword(accountUuid: String): Boolean {
        setEncrypted(accountUuid, null)
        Timber.d("Password for '%s' removed", accountUuid)
        return true
    }

    /**
     * Checks if master password is used to encrypt saved account passwords.
     *
     * @return true if used, false if not
     */
    override val isUsingMasterPassword: Boolean
        get() = (null != configurationService.getString(MASTER_PROP))

    /**
     * Verifies the correctness of the master password. Since we do not store the MP itself, if
     * [.MASTER_PROP_VALUE] is equal to the decrypted [.MASTER_PROP]'s value, then
     * the MP is considered correct.
     *
     * @param master master password
     * @return `true` if the password is correct; `false`, otherwise
     */
    override fun verifyMasterPassword(master: String?): Boolean {
        val localCrypto = AESCrypto(master)
        try {
            // use this value to verify master password correctness
            val encryptedValue = getEncryptedMasterPropValue()
            val correct = (MASTER_PROP_VALUE == localCrypto.decrypt(encryptedValue))
            if (correct) {
                // also set the crypto instance to use the correct MP
                setMasterPassword(master)
            }
            return correct
        } catch (e: CryptoException) {
            if (e.errorCode == CryptoException.WRONG_KEY) {
                Timber.d(e, "Incorrect master pass")
                return false
            } else {
                // this should not happen, so just in case it does..
                throw RuntimeException("Decryption failed", e)
            }
        }
    }


    /**
     * Changes the master password from the old to the new one. Decrypts all encrypted password
     * properties from the configuration with the oldPassword and encrypts them again with newPassword.
     *
     * @param oldPassword old master password
     * @param newPassword new master password
     * @return `true` if master password was changed successfully; `false`, otherwise
     */
    override fun changeMasterPassword(oldPassword: String?, newPassword: String?): Boolean {
        // get all encrypted account password properties
        val encryptedAccountProps = configurationService.getPropertyNamesBySuffix(ACCOUNT_ENCRYPTED_PASSWORD)

        // this map stores propName -> password
        val passwords = HashMap<String?, String?>()
        try {
            // read from the config and decrypt with the old MP..
            setMasterPassword(oldPassword)
            for (propName: String? in encryptedAccountProps) {
                val propValue = configurationService.getString(propName!!)
                if (propValue != null) {
                    val decrypted = crypto!!.decrypt(propValue)
                    passwords[propName] = decrypted
                }
            }
            // ..and encrypt again with the new, write to the config
            setMasterPassword(newPassword)
            for (entry: Map.Entry<String?, String?> in passwords.entries) {
                val encrypted = crypto!!.encrypt(entry.value)
                this.configurationService.setProperty(entry.key!!, encrypted)
            }
            // save the verification value, encrypted with the new MP,
            // or remove it if the newPassword is null (we are unsetting MP)
            writeVerificationValue(newPassword == null)
        } catch (ce: CryptoException) {
            Timber.d(ce.message)
            crypto = null
            return false
        }
        return true
    }

    /**
     * Sets the master password to the argument value.
     *
     * @param master master password
     */
    private fun setMasterPassword(master: String?) {
        crypto = AESCrypto(master)
    }

    /**
     * Moves all password properties from unencrypted
     * [.ACCOUNT_UNENCRYPTED_PASSWORD] to the corresponding encrypted
     * [.ACCOUNT_ENCRYPTED_PASSWORD].
     */
    private fun moveAllPasswordProperties() {
        val unencryptedProperties = configurationService.getPropertyNamesBySuffix(ACCOUNT_UNENCRYPTED_PASSWORD)
        for (prop: String? in unencryptedProperties) {
            val idx = prop!!.lastIndexOf('.')
            if (idx != -1) {
                val prefix = prop.substring(0, idx)
                val encodedPassword = getUnencrypted(prefix)
                /*
                 * If the password is stored unencrypted, we have to migrate it, of course. But
                 * if it is also stored encrypted in addition to being stored unencrypted, the
                 * situation starts to look unclear and it may be better to just not migrate it.
                 */
                if (((encodedPassword == null)
                                || (encodedPassword.isEmpty())
                                || isStoredEncrypted(prefix))) {
                    setUnencrypted(prefix, null)
                } else if (!movePasswordProperty(prefix, String(Base64.decode(encodedPassword)))) {
                    Timber.w("Failed to move password for prefix %s", prefix)
                }
            }
        }
    }

    /**
     * Asks for master password if needed, encrypts the password, saves it to
     * the new property and removes the old property.
     *
     * @param accountUuid account UUID
     * @param password unencrypted password
     * @return `true` if the specified `password` was successfully moved; otherwise, `false`
     */
    private fun movePasswordProperty(accountUuid: String, password: String): Boolean {
        if (createCrypto()) {
            try {
                setEncrypted(accountUuid, crypto!!.encrypt(password))
                setUnencrypted(accountUuid, null)
                return true
            } catch (cex: CryptoException) {
                Timber.d(cex, "Encryption failed")
            }
        }
        // properties are not moved
        return false
    }

    /**
     * Writes the verification value to the configuration for later use or
     * removes it completely depending on the remove flag argument.
     *
     * @param remove to remove the verification value or just overwrite it.
     */
    private fun writeVerificationValue(remove: Boolean) {
        if (remove) configurationService.removeProperty(MASTER_PROP) else {
            try {
                configurationService.setProperty(MASTER_PROP, crypto!!.encrypt(MASTER_PROP_VALUE))
            } catch (cex: CryptoException) {
                Timber.e(cex, "Failed to encrypt and write verification value")
            }
        }
    }

    /**
     * Creates a Crypto instance only when it's null, either with a user input master password or with null.
     * If the user decided not to input anything, the instance is not created.
     *
     * @return `true` if the Crypto instance was created; `false`, otherwise
     */
    @Synchronized
    private fun createCrypto(): Boolean {
        /*
         * XXX The method #createCrypto() is synchronized in order to not ask for the master
         * password more than once. Without the synchronization, it is possible to have the
         * master password prompt shown twice in a row during application startup when unencrypted passwords
         * are to be migrated with the master password already set and the accounts start loading.
         */
        if (crypto == null) {
            Timber.d("Crypto instance is null, creating.")
            if (isUsingMasterPassword) {
                val master = showPasswordPrompt()
                if (master == null) {
                    // User clicked cancel button in the prompt.
                    crypto = null
                } else {
                    /*
                     * At this point the master password must be correct, so we set the crypto instance to use it
                     */
                    setMasterPassword(master)
                }
                moveAllPasswordProperties()
            } else {
                Timber.d("Master password not set")
                /*
                 * Setting the master password to null means we shall still be using
                 * encryption/decryption but using some default value, not something specified by the user.
                 */
                setMasterPassword(null)
            }
        }
        return (crypto != null)
    }

    /**
     * Displays a password prompt to the user in a loop until it is correct or the user presses the cancel button.
     *
     * @return the entered password or `null` if none was provided.
     */
    private fun showPasswordPrompt(): String? {
        var master: String?
        // Ask for master password until the input is correct or cancel button is pressed and null returned
        var correct = true
        val masterPasswordInputService = CredentialsStorageActivator.getMasterPasswordInputService()
        if (masterPasswordInputService == null) {
            Timber.e("Missing MasterPasswordInputService to show input dialog")
            return null
        }
        do {
            master = masterPasswordInputService.showInputDialog(correct)
            if (master == null) return null
            correct = ((master.isNotEmpty()) && verifyMasterPassword(master))
        }
        while (!correct)
        return master
    }

    /**
     * Retrieves the property for the master password from the configuration service.
     *
     * @return the property for the master password
     */
    private fun getEncryptedMasterPropValue(): String? {
        return configurationService.getString(MASTER_PROP)
    }

    /**
     * Retrieves the encrypted account password using configuration service.
     *
     * @param accountUuid account UUID
     * @return the encrypted account password.
     */
    private fun getEncrypted(accountUuid: String): String? {
        return configurationService.getString("$accountUuid.$ACCOUNT_ENCRYPTED_PASSWORD")
    }

    /**
     * Saves the encrypted account password using configuration service.
     *
     * @param accountUuid account UUID
     * @param value the encrypted account password.
     */
    private fun setEncrypted(accountUuid: String, value: String?) {
        configurationService.setProperty("$accountUuid.$ACCOUNT_ENCRYPTED_PASSWORD", value)
    }

    /**
     * Check if encrypted account password is saved in the configuration.
     *
     * @param accountUuid account UUID
     * @return `true` if saved, `false` if not
     */
    override fun isStoredEncrypted(accountUuid: String?): Boolean {
        return configurationService.getString("$accountUuid.$ACCOUNT_ENCRYPTED_PASSWORD") != null
    }

    /**
     * Retrieves the unencrypted account password using configuration service.
     *
     * @param accountUuid account UUID
     * @return the unencrypted account password
     */
    private fun getUnencrypted(accountUuid: String): String? {
        return configurationService.getString("$accountUuid.$ACCOUNT_UNENCRYPTED_PASSWORD")
    }

    /**
     * Saves the unencrypted account password using configuration service.
     *
     * @param accountUuid account UUID
     * @param value the unencrypted account password
     */
    private fun setUnencrypted(accountUuid: String, value: String?) {
        configurationService.setProperty("$accountUuid.$ACCOUNT_UNENCRYPTED_PASSWORD", value)
    }

    companion object {
        /**
         * The name of a property which represents an encrypted password.
         */
        const val ACCOUNT_ENCRYPTED_PASSWORD = "ENCRYPTED_PASSWORD"

        /**
         * The name of a property which represents an unencrypted password.
         */
        const val ACCOUNT_UNENCRYPTED_PASSWORD = "PASSWORD"

        /**
         * The property in the configuration that we use to verify master password existence and correctness.
         */
        private const val MASTER_PROP = "credentialsstorage.MASTER"

        /**
         * This value will be encrypted and saved in MASTER_PROP and will be used to verify the key's correctness.
         */
        private const val MASTER_PROP_VALUE = "true"
    }
}