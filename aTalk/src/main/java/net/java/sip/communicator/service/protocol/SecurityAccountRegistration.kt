/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.util.UtilActivator
import org.atalk.impl.neomedia.transform.dtls.DtlsControlImpl
import org.atalk.impl.neomedia.transform.zrtp.ZrtpControlImpl
import org.atalk.service.neomedia.SDesControl
import org.atalk.service.neomedia.SrtpControlType
import java.io.Serializable
import java.math.BigInteger
import java.security.SecureRandom
import java.util.*

/**
 * The `SecurityAccountRegistration` is used to determine security options for different
 * registration protocol (Jabber, SIP). Useful to the SecurityPanel.
 *
 * @author Vincent Lucas
 * @author Pawel Domas
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 * @author Eng Chong Meng
 * @author MilanKral
 */
abstract class SecurityAccountRegistration : Serializable {
    /**
     * Enables support to encrypt calls.
     */
    private var mCallEncryptionEnable = true

    /**
     * Enables ZRTP encryption advertise in jingle session content.
     */
    private var mSipZrtpAttribute = true

    /**
     * Tells if SDES is enabled for this account.
     */
    private var mSdesEnable = false

    /**
     * The list of cipher suites enabled for SDES.
     */
    private var mSdesCipherSuites: String?

    /**
     * DTLS_SRTP Certificate Signature Algorithm.
     */
    private var mTlsCertificateSA: String?

    /**
     * The map between encryption protocols and their priority order.
     */
    private var mEncryptionProtocol: MutableMap<String, Int>

    /**
     * The map between encryption protocols and their status (enabled or disabled).
     */
    private var mEncryptionProtocolStatus: MutableMap<String, Boolean>

    /**
     * Random salt value used for ZID calculation.
     */
    private lateinit var mZIDSalt: String

    /**
     * Initializes the security account registration properties with the default values.
     */
    init {
        // Sets the default values.
        mEncryptionProtocol = object : HashMap<String, Int>() {
            init {
                put("ZRTP", 0)
                put("DTLS_SRTP", 1)
            }
        }
        mEncryptionProtocolStatus = object : HashMap<String, Boolean>() {
            init {
                put("ZRTP", true)
                put("DTLS_SRTP", true)
            }
        }

        randomZIDSalt()
        mTlsCertificateSA = DtlsControlImpl.DEFAULT_SIGNATURE_AND_HASH_ALGORITHM
        mSdesCipherSuites = UtilActivator.resources.getSettingsString(SDesControl.SDES_CIPHER_SUITES)
    }

    /**
     * If call encryption is enabled
     *
     * @return If call encryption is enabled
     */
    fun isCallEncryption(): Boolean {
        return mCallEncryptionEnable
    }

    /**
     * Sets call encryption enable status
     *
     * @param callEncryption if we want to set call encryption on as default
     */
    fun setCallEncryption(callEncryption: Boolean) {
        mCallEncryptionEnable = callEncryption
    }

    /**
     * Check if to include the ZRTP attribute to SIP/SDP or to Jabber/ Jingle IQ
     *
     * @return include the ZRTP attribute to SIP/SDP or to Jabber/ Jingle IQ
     */
    fun isSipZrtpAttribute(): Boolean {
        return mSipZrtpAttribute
    }

    /**
     * Sets ZRTP attribute support
     *
     * @param sipZrtpAttribute include the ZRTP attribute to SIP/SDP or to Jabber/IQ
     */
    fun setSipZrtpAttribute(sipZrtpAttribute: Boolean) {
        mSipZrtpAttribute = sipZrtpAttribute
    }

    /**
     * Tells if SDES is enabled for this account.
     *
     * @return True if SDES is enabled. False, otherwise.
     */
    fun isSDesEnable(): Boolean {
        return mSdesEnable
    }

    /**
     * Enables or disables SDES for this account.
     *
     * @param sdesEnable True to enable SDES. False, otherwise.
     */
    fun setSDesEnable(sdesEnable: Boolean) {
        mSdesEnable = sdesEnable
    }

    /**
     * Returns the list of cipher suites enabled for SDES.
     *
     * @return The list of cipher suites enabled for SDES. Null if no cipher suite is enabled.
     */
    fun getSDesCipherSuites(): String? {
        return mSdesCipherSuites
    }

    /**
     * Sets the list of cipher suites enabled for SDES.
     *
     * @param cipherSuites The list of cipher suites enabled for SDES. Null if no cipher suite is enabled.
     */
    fun setSDesCipherSuites(cipherSuites: String?) {
        mSdesCipherSuites = cipherSuites
    }

    /**
     * Returns the tls certificate signature algorithm.
     *
     * @return the tls certificate signature algorithm.
     */
    fun getDtlsCertSa(): String? {
        return mTlsCertificateSA
    }

    /**
     * Set the tls certificate signature algorithm.
     */
    fun setDtlsCertSa(certSA: String?) {
        mTlsCertificateSA = certSA
    }

    /**
     * Sets the method used for RTP/SAVP indication.
     */
    abstract fun setSavpOption(savpOption: Int)

    /**
     * Returns the method used for RTP/SAVP indication.
     *
     * @return the method used for RTP/SAVP indication.
     */
    abstract fun getSavpOption(): Int

    /**
     * Returns the map between the encryption protocols and their priority order.
     *
     * @return The map between the encryption protocols and their priority order.
     */
    fun getEncryptionProtocol(): MutableMap<String, Int> {
        return mEncryptionProtocol
    }

    /**
     * Sets the map between the encryption protocols and their priority order.
     *
     * @param encryptionProtocol The map between the encryption protocols and their priority order.
     */
    fun setEncryptionProtocol(encryptionProtocol: MutableMap<String, Int>) {
        mEncryptionProtocol = encryptionProtocol
    }

    /**
     * Returns the map between the encryption protocols and their status.
     *
     * @return The map between the encryption protocols and their status.
     */
    fun getEncryptionProtocolStatus(): MutableMap<String, Boolean> {
        return mEncryptionProtocolStatus
    }

    /**
     * Sets the map between the encryption protocols and their status.
     *
     * @param encryptionProtocolStatus The map between the encryption protocols and their status.
     */
    fun setEncryptionProtocolStatus(encryptionProtocolStatus: MutableMap<String, Boolean>) {
        mEncryptionProtocolStatus = encryptionProtocolStatus
    }

    /**
     * Adds the ordered encryption protocol names to the property list given in parameter.
     *
     * @param properties The property list to fill in.
     */
    private fun addEncryptionProtocolsToProperties(properties: MutableMap<String, String?>) {
        for ((key, value) in getEncryptionProtocol()) {
            properties[ProtocolProviderFactory.ENCRYPTION_PROTOCOL + "." + key] = value.toString()
        }
    }

    /**
     * Adds the encryption protocol status to the property list given in parameter.
     *
     * @param properties The property list to fill in.
     */
    private fun addEncryptionProtocolStatusToProperties(properties: MutableMap<String, String?>) {
        for ((key, value) in getEncryptionProtocolStatus()) {
            properties[ProtocolProviderFactory.ENCRYPTION_PROTOCOL_STATUS + "." + key] = value.toString()
        }
    }

    /**
     * Stores security properties held by this registration object into given properties map.
     *
     * @param propertiesMap the map that will be used for storing security properties held by this object.
     */
    fun storeProperties(propertiesMap: MutableMap<String, String?>) {
        propertiesMap[ProtocolProviderFactory.DEFAULT_ENCRYPTION] = java.lang.Boolean.toString(isCallEncryption())

        // Sets the ordered list of encryption protocols.
        addEncryptionProtocolsToProperties(propertiesMap)
        // Sets the list of encryption protocol status.
        addEncryptionProtocolStatusToProperties(propertiesMap)
        propertiesMap[ProtocolProviderFactory.DEFAULT_SIPZRTP_ATTRIBUTE] = java.lang.Boolean.toString(isSipZrtpAttribute())
        propertiesMap[ProtocolProviderFactory.ZID_SALT] = getZIDSalt()
        propertiesMap[ProtocolProviderFactory.DTLS_CERT_SIGNATURE_ALGORITHM] = getDtlsCertSa()
        propertiesMap[ProtocolProviderFactory.SAVP_OPTION] = getSavpOption().toString()
        propertiesMap[ProtocolProviderFactory.SDES_CIPHER_SUITES] = getSDesCipherSuites()
    }

    /**
     * Loads security properties for the user account with the given identifier.
     *
     * @param accountID the account identifier.
     */
    fun loadAccount(accountID: AccountID) {
        // Clear all the default values
        mEncryptionProtocol = HashMap()
        mEncryptionProtocolStatus = HashMap()
        setCallEncryption(accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true))
        val srcEncryptionProtocol = accountID.getIntegerPropertiesByPrefix(
                ProtocolProviderFactory.ENCRYPTION_PROTOCOL, true)
        val srcEncryptionProtocolStatus = accountID.getBooleanPropertiesByPrefix(
                ProtocolProviderFactory.ENCRYPTION_PROTOCOL_STATUS, true, false)
        // Load stored values.
        val prefixeLength = ProtocolProviderFactory.ENCRYPTION_PROTOCOL.length + 1
        for ((key, value) in srcEncryptionProtocol) {
            val name = key.substring(prefixeLength)
            if (isExistingEncryptionProtocol(name)) {
                // Copy the priority
                mEncryptionProtocol[name] = value

                // Extract the status
                var isEnable = false
                val mEncryptProtoKey = ProtocolProviderFactory.ENCRYPTION_PROTOCOL_STATUS + "." + name
                if (srcEncryptionProtocolStatus.containsKey(mEncryptProtoKey)) {
                    isEnable = java.lang.Boolean.TRUE == srcEncryptionProtocolStatus[mEncryptProtoKey]
                }
                mEncryptionProtocolStatus[name] = isEnable
            }
        }

        // Load ZRTP encryption parameters
        setSipZrtpAttribute(accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_SIPZRTP_ATTRIBUTE, true))
        mZIDSalt = ZrtpControlImpl.getAccountZIDSalt(accountID)

        // Load DTLS_SRTP TlsCertificateSA from DB or use DEFAULT_SIGNATURE_ALGORITHM if none is defined
        mTlsCertificateSA = accountID.getAccountPropertyString(
                ProtocolProviderFactory.DTLS_CERT_SIGNATURE_ALGORITHM, DtlsControlImpl.DEFAULT_SIGNATURE_AND_HASH_ALGORITHM)

        // Load SDES encryption parameters
        setSavpOption(accountID.getAccountPropertyInt(ProtocolProviderFactory.SAVP_OPTION, ProtocolProviderFactory.SAVP_OFF))
        setSDesCipherSuites(accountID.getAccountPropertyString(ProtocolProviderFactory.SDES_CIPHER_SUITES))
    }

    /**
     * Returns ZID salt
     *
     * @return ZID salt
     */
    fun getZIDSalt(): String {
        return mZIDSalt
    }

    /**
     * Set ZID salt
     *
     * @param ZIDSalt new ZID salt value
     */
    fun setZIDSalt(ZIDSalt: String) {
        mZIDSalt = ZIDSalt
    }

    /**
     * Generate new random value for the ZID salt and update the ZIDSalt.
     */
    fun randomZIDSalt(): String {
        mZIDSalt = BigInteger(256, mSecureRandom).toString(32)
        return mZIDSalt
    }

    companion object {
        /**
         * The encryption protocols managed by this SecurityPanel.
         */
        private val ENCRYPTION_PROTOCOL = Collections.unmodifiableList(listOf(
                SrtpControlType.ZRTP.toString(),
                SrtpControlType.DTLS_SRTP.toString(),
                SrtpControlType.SDES.toString()
        ))

        private val mSecureRandom = SecureRandom()

        /**
         * Loads the list of enabled and disabled encryption protocols with their priority into array of
         * `String` and array of `Boolean`. The protocols are positioned in the array by
         * the priority and the `Boolean` array holds the enabled flag on the corresponding index.
         *
         * @param encryptionProtocol The map of encryption protocols with their priority available for this account.
         * @param encryptionProtocolStatus The map of encryption protocol statuses.
         * @return `Object[]` array holding:<br></br>
         * - at [0] `String[]` the list of extracted protocol names<br></br>
         * - at [1] `boolean[]` the list of of protocol status flags
         */
        fun loadEncryptionProtocol(encryptionProtocol: Map<String, Int>,
                encryptionProtocolStatus: Map<String, Boolean>): Array<Any> {
            val nbEncryptionProtocol = ENCRYPTION_PROTOCOL.size
            val encryption = arrayOfNulls<String>(nbEncryptionProtocol)
            val selectedEncryption = BooleanArray(nbEncryptionProtocol)

            // Load stored values.
            for ((name, index) in encryptionProtocol) {
                // If the property is set.
                if (index != -1) {
                    if (isExistingEncryptionProtocol(name)) {
                        encryption[index] = name
                        selectedEncryption[index] = java.lang.Boolean.TRUE == encryptionProtocolStatus[name]
                    }
                }
            }

            // Load default values.
            var j = 0
            for (encProtocol in ENCRYPTION_PROTOCOL) {
                // Specify a default value only if there is no specific value set.
                if (!encryptionProtocol.containsKey(encProtocol)) {
                    var set = false
                    // Search for the first empty element.
                    while (j < encryption.size && !set) {
                        if (encryption[j] == null) {
                            encryption[j] = encProtocol
                            // By default only ZRTP is set to true.
                            selectedEncryption[j] = encProtocol == "ZRTP"
                            set = true
                        }
                        ++j
                    }
                }
            }
            return arrayOf(encryption, selectedEncryption)
        }

        /**
         * Checks if a specific `protocol` is on the list of supported (encryption) protocols.
         *
         * @param protocol the protocol name
         * @return `true` if `protocol` is supported; `false`, otherwise
         */
        private fun isExistingEncryptionProtocol(protocol: String): Boolean {
            return ENCRYPTION_PROTOCOL.contains(protocol)
        }
    }
}