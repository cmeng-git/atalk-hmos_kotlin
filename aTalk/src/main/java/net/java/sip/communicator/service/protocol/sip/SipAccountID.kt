/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol.sip

import android.content.ContentValues
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.ProtocolNames
import net.java.sip.communicator.service.protocol.ProtocolProviderActivator
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import org.json.JSONObject

/**
 * A SIP extension of the account ID property.
 *
 * @author Emil Ivov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class SipAccountID
/**
 * Creates a SIP account id from the specified ide and account properties.
 *
 * @param userID the user id part of the SIP uri identifying this contact.
 * @param accountProperties any other properties necessary for the account.
 * @param serverName the name of the server that the user belongs to.
 */
protected constructor(userID: String?, accountProperties: MutableMap<String, String?>, serverName: String)
    : AccountID(userID, accountProperties, ProtocolNames.SIP, serverName) {

    /**
     * Default constructor for wizard purposes.
     */
    // constructor() : super(null, HashMap<String, String?>(), ProtocolNames.SIP, null) {}

    /**
     * The proxy address
     *
     * @return the proxy address
     */
    fun getProxy(): String? {
        return getAccountPropertyString(ProtocolProviderFactory.PROXY_ADDRESS)
    }

    /**
     * Set new proxy address
     *
     * @param proxy
     * the proxy address to set
     */
    fun setProxy(proxy: String?) {
        setOrRemoveIfEmpty(ProtocolProviderFactory.PROXY_ADDRESS, proxy)
    }

    /**
     * Returns the UIN of the sip registration account.
     *
     * @return the UIN of the sip registration account.
     */
    fun getId(): String? {
        return getAccountPropertyString(ProtocolProviderFactory.USER_ID)
    }

    /**
     * Get the preferred transport.
     *
     * @return the preferred transport for this account identifier.
     */
    fun getPreferredTransport(): String? {
        return getAccountPropertyString(ProtocolProviderFactory.PREFERRED_TRANSPORT)
    }

    /**
     * Sets the preferred transport for this account identifier.
     *
     * @param preferredTransport
     * the preferred transport for this account identifier.
     */
    fun setPreferredTransport(preferredTransport: String) {
        putAccountProperty(ProtocolProviderFactory.PREFERRED_TRANSPORT, preferredTransport)
    }

    /**
     * The port on the specified proxy
     *
     * @return int
     */
    override var proxyPort: String?
        get() = getAccountPropertyString(ProtocolProviderFactory.PROXY_PORT)
        set(proxyPort) {
            setOrRemoveIfEmpty(ProtocolProviderFactory.PROXY_PORT, proxyPort)
        }

    /**
     * Sets the identifier of the sip registration account.
     *
     * @param id
     * the identifier of the sip registration account.
     */
    fun setUserID(id: String) {
        putAccountProperty(ProtocolProviderFactory.USER_ID, id)
    }

    /**
     * Is proxy auto configured.
     *
     * @return `true` if proxy is auto configured.
     */
    fun isProxyAutoConfigure(): Boolean {
        return getAccountPropertyBoolean(ProtocolProviderFactory.PROXY_AUTO_CONFIG, true)
    }

    /**
     * Sets auto configuration of proxy enabled or disabled.
     *
     * @param proxyAutoConfigure
     * `true` if the proxy will be auto configured.
     */
    fun setProxyAutoConfigure(proxyAutoConfigure: Boolean) {
        putAccountProperty(ProtocolProviderFactory.PROXY_AUTO_CONFIG, proxyAutoConfigure)
    }

    fun isProxyForceBypassConfigure(): Boolean {
        return getAccountPropertyBoolean(ProtocolProviderFactory.FORCE_PROXY_BYPASS, false)
    }

    fun setProxyForceBypassConfigure(proxyForceBypassConfigure: Boolean) {
        putAccountProperty(ProtocolProviderFactory.FORCE_PROXY_BYPASS, proxyForceBypassConfigure)
    }

    /**
     * If the presence is enabled
     *
     * @return If the presence is enabled
     */
    fun isEnablePresence(): Boolean {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_PRESENCE_ENABLED, true)
    }

    /**
     * If the p2p mode is forced
     *
     * @return If the p2p mode is forced
     */
    fun isForceP2PMode(): Boolean {
        return getAccountPropertyBoolean(ProtocolProviderFactory.FORCE_P2P_MODE, false)
    }

    /**
     * The offline contact polling period
     *
     * @return the polling period
     */
    fun getPollingPeriod(): String? {
        return getAccountPropertyString(ProtocolProviderFactory.POLLING_PERIOD)
    }

    /**
     * The default expiration of subscriptions
     *
     * @return the subscription expiration
     */
    fun getSubscriptionExpiration(): String? {
        return getAccountPropertyString(ProtocolProviderFactory.SUBSCRIPTION_EXPIRATION)
    }

    /**
     * Sets if the presence is enabled
     *
     * @param enablePresence
     * if the presence is enabled
     */
    fun setEnablePresence(enablePresence: Boolean) {
        putAccountProperty(ProtocolProviderFactory.IS_PRESENCE_ENABLED, enablePresence)
    }

    /**
     * Sets if we have to force the p2p mode
     *
     * @param forceP2PMode
     * if we have to force the p2p mode
     */
    fun setForceP2PMode(forceP2PMode: Boolean) {
        putAccountProperty(ProtocolProviderFactory.FORCE_P2P_MODE, forceP2PMode)
    }

    /**
     * Sets the offline contacts polling period
     *
     * @param pollingPeriod
     * the offline contacts polling period
     */
    fun setPollingPeriod(pollingPeriod: String) {
        putAccountProperty(ProtocolProviderFactory.POLLING_PERIOD, pollingPeriod)
    }

    /**
     * Sets the subscription expiration value
     *
     * @param subscriptionExpiration
     * the subscription expiration value
     */
    fun setSubscriptionExpiration(subscriptionExpiration: String) {
        putAccountProperty(ProtocolProviderFactory.SUBSCRIPTION_EXPIRATION, subscriptionExpiration)
    }

    /**
     * Returns the keep alive method.
     *
     * @return the keep alive method.
     */
    fun getKeepAliveMethod(): String? {
        return getAccountPropertyString(ProtocolProviderFactory.KEEP_ALIVE_METHOD)
    }

    /**
     * Sets the keep alive method.
     *
     * @param keepAliveMethod
     * the keep alive method to set
     */
    fun setKeepAliveMethod(keepAliveMethod: String) {
        putAccountProperty(ProtocolProviderFactory.KEEP_ALIVE_METHOD, keepAliveMethod)
    }

    /**
     * Checks if XCAP is enabled.
     *
     * @return true if XCAP is enabled otherwise false.
     */
    fun isXCapEnable(): Boolean {
        return getAccountPropertyBoolean(XCAP_ENABLE, false)
    }

    /**
     * Sets if XCAP is enable.
     *
     * @param xCapEnable
     * XCAP enable.
     */
    fun setXCapEnable(xCapEnable: Boolean) {
        putAccountProperty(XCAP_ENABLE, xCapEnable)
    }

    /**
     * Checks if XiVO option is enabled.
     *
     * @return true if XiVO is enabled otherwise false.
     */
    fun isXiVOEnable(): Boolean {
        return getAccountPropertyBoolean(XIVO_ENABLE, false)
    }

    /**
     * Sets if XiVO option is enable.
     *
     * @param xivoEnable
     * XiVO enable.
     */
    fun setXiVOEnable(xivoEnable: Boolean) {
        putAccountProperty(XIVO_ENABLE, xivoEnable)
    }

    /**
     * Gets the property related to XCAP/XIVO in old properties compatibility mode. If there is no
     * value under new key then old keys are selected based on whether XCAP or XIVO is currently
     * enabled.
     *
     * @param newKey
     * currently used property key
     * @param oldKeyXcap
     * old XCAP property key
     * @param oldKeyXivo
     * old XIVO property key
     * @return XIVO/XCAP property value
     */
    private fun getXcapCompatible(newKey: String, oldKeyXcap: String, oldKeyXivo: String): String? {
        var value = getAccountPropertyString(newKey)
        if (value == null) {
            val oldKey = if (isXCapEnable()) oldKeyXcap else oldKeyXivo
            value = getAccountPropertyString(oldKey)
            if (value != null) {
                // remove old
                mAccountProperties.remove(oldKey)
                // store under new property key
                mAccountProperties[newKey] = value
            }
        }
        return value
    }

    /**
     * Checks if contact list has to use SIP account credentials.
     *
     * @return `true` if contact list has to use SIP account credentials otherwise
     * `false`.
     */
    fun isClistOptionUseSipCredentials(): Boolean {
        val `val` = getXcapCompatible(OPT_CLIST_USE_SIP_CREDETIALS, "XCAP_USE_SIP_CREDETIALS",
                "XIVO_USE_SIP_CREDETIALS")
        if (`val` == null) getDefaultString(OPT_CLIST_USE_SIP_CREDETIALS)
        return java.lang.Boolean.parseBoolean(`val`)
    }

    /**
     * Sets if contact list has to use SIP account credentials.
     *
     * @param useSipCredentials
     * if the clist has to use SIP account credentials.
     */
    fun setClistOptionUseSipCredentials(useSipCredentials: Boolean) {
        putAccountProperty(OPT_CLIST_USE_SIP_CREDETIALS, useSipCredentials)
    }

    /**
     * Gets the contact list server uri.
     *
     * @return the contact list server uri.
     */
    fun getClistOptionServerUri(): String? {
        return getXcapCompatible(OPT_CLIST_SERVER_URI, "XCAP_SERVER_URI", "XIVO_SERVER_URI")
    }

    /**
     * Sets the contact list server uri.
     *
     * @param clistOptionServerUri
     * the contact list server uri.
     */
    fun setClistOptionServerUri(clistOptionServerUri: String?) {
        setOrRemoveIfNull(OPT_CLIST_SERVER_URI, clistOptionServerUri)
    }

    /**
     * Gets the contact list user.
     *
     * @return the contact list user.
     */
    fun getClistOptionUser(): String? {
        return getXcapCompatible(OPT_CLIST_USER, "XCAP_USER", "XIVO_USER")
    }

    /**
     * Sets the contact list user.
     *
     * @param clistOptionUser
     * the contact list user.
     */
    fun setClistOptionUser(clistOptionUser: String?) {
        setOrRemoveIfNull(OPT_CLIST_USER, clistOptionUser)
    }

    /**
     * Gets the contact list password.
     *
     * @return the contact list password.
     */
    fun getClistOptionPassword(): String? {
        return getXcapCompatible(OPT_CLIST_PASSWORD, "XCAP_PASSWORD", "XIVO_PASSWORD")
    }

    /**
     * Sets the contact list password.
     *
     * @param clistOptionPassword
     * the contact list password.
     */
    fun setClistOptionPassword(clistOptionPassword: String?) {
        setOrRemoveIfEmpty(OPT_CLIST_PASSWORD, clistOptionPassword)
    }

    /**
     * The voicemail URI.
     *
     * @return the voicemail URI.
     */
    fun getVoicemailURI(): String? {
        return getAccountPropertyString(ProtocolProviderFactory.VOICEMAIL_URI)
    }

    /**
     * Sets voicemail URI.
     *
     * @param voicemailURI
     * new URI.
     */
    fun setVoicemailURI(voicemailURI: String?) {
        putAccountProperty(ProtocolProviderFactory.VOICEMAIL_URI, voicemailURI!!)
    }

    /**
     * The voicemail check URI.
     *
     * @return the voicemail URI.
     */
    fun getVoicemailCheckURI(): String? {
        return getAccountPropertyString(ProtocolProviderFactory.VOICEMAIL_CHECK_URI)
    }

    /**
     * Sets voicemail check URI.
     *
     * @param voicemailCheckURI
     * new URI.
     */
    fun setVoicemailCheckURI(voicemailCheckURI: String) {
        putAccountProperty(ProtocolProviderFactory.VOICEMAIL_CHECK_URI, voicemailCheckURI)
    }

    /**
     * Check if messageWaitingIndications is enabled
     *
     * @return if messageWaitingIndications is enabled
     */
    fun isMessageWaitingIndicationsEnabled(): Boolean {
        return getAccountPropertyBoolean(ProtocolProviderFactory.VOICEMAIL_ENABLED, true)
    }

    /**
     * Sets message waiting indications.
     *
     * @param messageWaitingIndications
     * `true` to enable message waiting indications.
     */
    fun setMessageWaitingIndications(messageWaitingIndications: Boolean) {
        putAccountProperty(ProtocolProviderFactory.VOICEMAIL_ENABLED, messageWaitingIndications)
    }

    /**
     * Returns the protocol name
     *
     * @return the name of the protocol for this registration object
     */
    override val protocolName = ProtocolNames.SIP

    /**
     * Returns a string that could be directly used (or easily converted to) an address that other
     * users of the procotol can use to communicate with us. By default this string is set to
     * userid@servicename. Protocol implementors should override it if they'd need it to respect a
     * different syntax.
     *
     * @return a String in the form of userid@service that other protocol users should be able to
     * parse into a meaningful address and use it to communicate with us.
     */
    override val accountJid: String
        get() {
            val accountAddress = StringBuffer()
            accountAddress.append("sip:")
            accountAddress.append(mUserID)

            if (service != null) {
                accountAddress.append('@')
                accountAddress.append(service)
            }
            return accountAddress.toString()
        }

    /**
     * {@inheritDoc}
     */
    override fun getDefaultString(key: String): String? {
        return getDefaultStr(key)
    }

    //===================================
    var keys = JSONObject()
    protected var avatar = "avatar_filePath"
    var rosterVersion = "version"

    override val contentValues: ContentValues
        get() {
            val values = ContentValues()
            values.put(ACCOUNT_UUID, accountUniqueID)
            values.put(PROTOCOL, ProtocolNames.SIP)
            values.put(USER_ID, mUserID)
            values.put(ACCOUNT_UID, accountUniqueID)
            synchronized(keys) { values.put(KEYS, keys.toString()) }
            return values
        }

    companion object {
        /**
         * The name of the property under which the user may specify whether to use or not XCAP.
         */
        const val XCAP_ENABLE = "XCAP_ENABLE"

        /**
         * The name of the property under which the user may specify whether to use or not xivo.
         */
        const val XIVO_ENABLE = "XIVO_ENABLE"

        /**
         * The name of the property under which the user may specify whether to use original sip
         * credentials for the contact list.
         */
        const val OPT_CLIST_USE_SIP_CREDETIALS = "OPT_CLIST_USE_SIP_CREDETIALS"

        /**
         * The name of the property under which the user may specify the contact list server uri.
         */
        const val OPT_CLIST_SERVER_URI = "OPT_CLIST_SERVER_URI"

        /**
         * The name of the property under which the user may specify the XCAP user.
         */
        const val OPT_CLIST_USER = "OPT_CLIST_USER"

        /**
         * The name of the property under which the user may specify the XCAP user password.
         */
        const val OPT_CLIST_PASSWORD = "OPT_CLIST_PASSWORD"

        /**
         * Default properties prefix used in atalk-defaults.properties file for SIP protocol.
         */
        private const val SIP_DEFAULTS_PREFIX = DEFAULT_PREFIX + "sip."

        fun getDefaultStr(key: String): String {
            var value = ProtocolProviderActivator.getConfigurationService()!!.getString(
                    SIP_DEFAULTS_PREFIX + key)
            if (value == null) value = AccountID.getDefaultStr(key)
            return value!!
        }

        /**
         * Return the server part of the sip user name.
         *
         * @param userName
         * the username.
         * @return the server part of the sip user name.
         */
        fun getServerFromUserName(userName: String): String? {
            val delimIndex = userName.indexOf("@")
            return if (delimIndex != -1) {
                userName.substring(delimIndex + 1)
            } else null
        }
    }
}