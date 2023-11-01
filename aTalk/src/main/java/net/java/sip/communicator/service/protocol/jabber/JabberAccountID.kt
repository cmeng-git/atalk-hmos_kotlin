/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.jabber

import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.JingleNodeDescriptor
import net.java.sip.communicator.service.protocol.ProtocolNames
import net.java.sip.communicator.service.protocol.ProtocolProviderActivator
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import org.atalk.hmos.gui.account.settings.BoshProxyDialog
import org.jivesoftware.smack.util.TLSUtils
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException
import org.jxmpp.util.XmppStringUtils
import timber.log.Timber

/**
 * The Jabber implementation of a sip-communicator AccountID
 *
 * @author Damian Minkov
 * @author Sebastien Vincent
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class JabberAccountID @JvmOverloads constructor(
        userId: String? = null,
        accountProperties: MutableMap<String, String?> = HashMap(),
) : AccountID(userId, accountProperties, ProtocolNames.JABBER, getServiceName(accountProperties)) {
    /**
     * Creates an account id from the specified id and account properties.
     *
     * @param userId the id identifying this account i.e hawk@example.org
     * @param accountProperties any other properties necessary!! for the account.
     */

    /**
     * Default constructor for serialization purposes. Do not removed - required by serialization
     */
    init {
        // id can be null on initial startup
        if (userId != null) {
            try {
                bareJid = JidCreate.bareFrom(userId)
            } catch (e: XmppStringprepException) {
                Timber.e("Unable to create BareJid for user account: %s", userId)
                throw IllegalArgumentException("User ID is not a valid xmpp BareJid")
            }
        }
    }

    /**
     * change to the new userId for the current AccountID. Mainly use of userId change in account settings
     * Need to change the userID, userBareJid, accountUID; and mAccountProperties.USER_ID if Account ID changed
     *
     * @param userId new userId
     */
    fun updateJabberAccountID(userId: String?) {
        if (userId != null) {
            mUserID = userId
            accountUniqueID = "$protocolName:$mUserID"
            mAccountProperties[USER_ID] = userId
            try {
                bareJid = JidCreate.bareFrom(userId)
            } catch (e: XmppStringprepException) {
                Timber.e("Unable to create BareJid for user account: %s", userId)
            }
        }
    }

    /**
     * Returns the BOSH URL which should be used to connect to the XMPP server.
     * The value must not be null if BOSH transport is enabled.
     *
     * @return a `String` with the URL which should be used for BOSH transport
     */
    fun getBoshUrl(): String? {
        return getAccountPropertyString(ProtocolProviderFactory.BOSH_URL)
    }

    /**
     * Sets new URL which should be used for the BOSH transport.
     *
     * @param boshPath a `String` with the new BOSH URL
     */
    fun setBoshUrl(boshPath: String?) {
        putAccountProperty(ProtocolProviderFactory.BOSH_URL, boshPath!!)
    }

    /**
     * Returns true is Type is BOSH else false.
     *
     * @return `true` if (Type == BOSH) else false
     */
    fun isBOSHEnable(): Boolean {
        return BoshProxyDialog.BOSH == getAccountPropertyString(ProtocolProviderFactory.PROXY_TYPE)
    }

    /**
     * Indicates if HTTP proxy should be used for with BOSH protocol. Only HTTP proxy is supported for BOSH
     *
     * @return `true` if Bosh Http Proxy should be used, otherwise returns `false`
     */
    fun isBoshHttpProxyEnabled(): Boolean {
        return getAccountPropertyBoolean(ProtocolProviderFactory.BOSH_PROXY_HTTP_ENABLED, false)
    }

    /**
     * set HTTP proxy should be used for with BOSH protocol.
     *
     * @param isBoshHttp `true to enable HTTP proxy for BOSH`
     */
    fun setBoshHttpProxyEnabled(isBoshHttp: Boolean) {
        putAccountProperty(ProtocolProviderFactory.BOSH_PROXY_HTTP_ENABLED, isBoshHttp)
    }

    /**
     * Returns the override phone suffix.
     *
     * @return the phone suffix
     */

    fun getOverridePhoneSuffix(): String? {
        return getAccountPropertyString(OVERRIDE_PHONE_SUFFIX)
    }

    /**
     * Sets the override value of the phone suffix.
     *
     * @param phoneSuffix the phone name suffix (the domain name after the @ sign)
     */
    fun setOverridePhoneSuffix(phoneSuffix: String?) {
        setOrRemoveIfEmpty(OVERRIDE_PHONE_SUFFIX, phoneSuffix)
    }

    /**
     * Returns the actual name of this protocol: ProtocolNames.JABBER.
     *
     * @return Jabber: the name of this protocol.
     */
    override val systemProtocolName: String
        get() = ProtocolNames.JABBER

    /**
     * Returns the alwaysCallWithGtalk value.
     *
     * @return the alwaysCallWithGtalk value
     */
    fun getBypassGtalkCaps(): Boolean {
        return getAccountPropertyBoolean(BYPASS_GTALK_CAPABILITIES, false)
    }

    /**
     * Sets value for alwaysCallWithGtalk.
     *
     * @param bypassGtalkCaps true to enable, false otherwise
     */
    fun setBypassGtalkCaps(bypassGtalkCaps: Boolean) {
        putAccountProperty(BYPASS_GTALK_CAPABILITIES, bypassGtalkCaps)
    }

    /**
     * Returns telephony domain that bypass GTalk caps.
     *
     * @return telephony domain
     */
    fun getTelephonyDomainBypassCaps(): String? {
        return getAccountPropertyString(TELEPHONY_BYPASS_GTALK_CAPS)
    }

    /**
     * Sets telephony domain that bypass GTalk caps.
     *
     * @param text telephony domain to set
     */
    fun setTelephonyDomainBypassCaps(text: String?) {
        setOrRemoveIfEmpty(TELEPHONY_BYPASS_GTALK_CAPS, text)
    }

    /**
     * Indicates whether anonymous authorization method is used by this account.
     *
     * @return `true` if anonymous login is enabled on this account.
     */
    fun isAnonymousAuthUsed(): Boolean {
        return getAccountPropertyBoolean(ANONYMOUS_AUTH, false)
    }

    /**
     * Gets if Jingle is disabled for this account.
     *
     * @return True if jingle is disabled for this account. False otherwise.
     */
    fun isJingleDisabled(): Boolean {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_CALLING_DISABLED_FOR_ACCOUNT, false)
    }

    /**
     * Determines whether SIP Communicator should be querying Gmail servers for unread mail messages.
     *
     * @return `true` if we are to enable Gmail notifications and `false` otherwise.
     */
    fun isGmailNotificationEnabled(): Boolean {
        return getAccountPropertyBoolean(GMAIL_NOTIFICATIONS_ENABLED, false)
    }

    /**
     * Specifies whether SIP Communicator should be querying Gmail servers for unread mail messages.
     *
     * @param enabled `true` if we are to enable Gmail notification and `false` otherwise.
     */
    fun setGmailNotificationEnabled(enabled: Boolean) {
        putAccountProperty(GMAIL_NOTIFICATIONS_ENABLED, enabled)
    }

    /**
     * Determines whether SIP Communicator should use Google Contacts as ContactSource
     *
     * @return `true` if we are to enable Google Contacts and `false` otherwise.
     */
    fun isGoogleContactsEnabled(): Boolean {
        return getAccountPropertyBoolean(GOOGLE_CONTACTS_ENABLED, true)
    }

    /**
     * Specifies whether SIP Communicator should use Google Contacts as ContactSource.
     *
     * @param enabled `true` if we are to enable Google Contacts and `false` otherwise.
     */
    fun setGoogleContactsEnabled(enabled: Boolean) {
        putAccountProperty(GOOGLE_CONTACTS_ENABLED, enabled)
    }

    /**
     * Enables anonymous authorization mode on this XMPP account.
     *
     * @param useAnonymousAuth `true` to use anonymous login.
     */
    fun setUseAnonymousAuth(useAnonymousAuth: Boolean) {
        putAccountProperty(ANONYMOUS_AUTH, useAnonymousAuth)
    }

    /**
     * Sets if Jingle is disabled for this account.
     *
     * @param disabled True if jingle is disabled for this account. False otherwise.
     */
    fun setDisableJingle(disabled: Boolean) {
        putAccountProperty(ProtocolProviderFactory.IS_CALLING_DISABLED_FOR_ACCOUNT, disabled)
    }

    /**
     * Returns the resource.
     *
     * @return the resource
     */
    fun getResource(): String? {
        return getAccountPropertyString(ProtocolProviderFactory.RESOURCE)
    }

    /**
     * Sets the resource.
     *
     * @param resource the resource for the jabber account
     */
    fun setResource(resource: String) {
        putAccountProperty(ProtocolProviderFactory.RESOURCE, resource)
    }

    /**
     * Returns the priority property.
     *
     * @return priority
     */
    fun getPriority(): Int {
        return getAccountPropertyInt(ProtocolProviderFactory.RESOURCE_PRIORITY, 30)
    }

    /**
     * Sets the priority property.
     *
     * @param priority the priority to set
     */
    fun setPriority(priority: Int) {
        putAccountProperty(ProtocolProviderFactory.RESOURCE_PRIORITY, priority)
    }

    /**
     * Indicates if ice should be used for this account.
     *
     * @return `true` if ICE should be used for this account, otherwise returns `false`
     */
    fun isUseIce(): Boolean {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_USE_ICE, true)
    }

    /**
     * Sets the `useIce` property.
     *
     * @param isUseIce `true` to indicate that ICE should be used for this account, `false` -
     * otherwise.
     */
    fun setUseIce(isUseIce: Boolean) {
        putAccountProperty(ProtocolProviderFactory.IS_USE_ICE, isUseIce)
    }

    /**
     * Indicates if the stun server should be automatically discovered.
     *
     * @return `true` if the stun server should be automatically discovered,
     * otherwise false if serverOverride is enabled; serviceDomain is likely not reachable.
     */
    fun isAutoDiscoverStun(): Boolean {
        return getAccountPropertyBoolean(ProtocolProviderFactory.AUTO_DISCOVER_STUN, !isServerOverridden)
    }

    /**
     * Sets the `autoDiscoverStun` property.
     *
     * @param isAutoDiscover `true` to indicate that stun server should be auto-discovered, `false` -
     * otherwise.
     */
    fun setAutoDiscoverStun(isAutoDiscover: Boolean) {
        putAccountProperty(ProtocolProviderFactory.AUTO_DISCOVER_STUN, isAutoDiscover)
    }

    /**
     * Sets the `useDefaultStunServer` property.
     *
     * @param isUseDefaultStunServer `true` to indicate that default stun server should be used if no others are
     * available, `false` otherwise.
     */
    fun setUseDefaultStunServer(isUseDefaultStunServer: Boolean) {
        putAccountProperty(ProtocolProviderFactory.USE_DEFAULT_STUN_SERVER, isUseDefaultStunServer)
    }

    /**
     * Indicates if the JingleNodes relay server should be automatically discovered.
     *
     * @return `true` if the relay server should be automatically discovered, otherwise returns `false`.
     */
    fun isAutoDiscoverJingleNodes(): Boolean {
        return getAccountPropertyBoolean(ProtocolProviderFactory.AUTO_DISCOVER_JINGLE_NODES, true)
    }

    /**
     * Sets the `autoDiscoverJingleNodes` property.
     *
     * @param isAutoDiscoverJingleNodes `true` to indicate that relay server should be auto-discovered,
     * `false` - otherwise.
     */
    fun setAutoDiscoverJingleNodes(isAutoDiscoverJingleNodes: Boolean) {
        putAccountProperty(ProtocolProviderFactory.AUTO_DISCOVER_JINGLE_NODES, isAutoDiscoverJingleNodes)
    }

    /**
     * Indicates if JingleNodes relay should be used.
     *
     * @return `true` if JingleNodes should be used, `false` otherwise
     */
    fun isUseJingleNodes(): Boolean {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_USE_JINGLE_NODES, true)
    }

    /**
     * Sets the `useJingleNodes` property.
     *
     * @param isUseJingleNodes `true` to indicate that Jingle Nodes should be used for this account,
     * `false` - otherwise.
     */
    fun setUseJingleNodes(isUseJingleNodes: Boolean) {
        putAccountProperty(ProtocolProviderFactory.IS_USE_JINGLE_NODES, isUseJingleNodes)
    }

    /**
     * Indicates if UPnP should be used for this account.
     *
     * @return `true` if UPnP should be used for this account, otherwise returns `false`
     */
    fun isUseUPNP(): Boolean {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_USE_UPNP, true)
    }

    /**
     * Sets the `useUPNP` property.
     *
     * @param isUseUPNP `true` to indicate that UPnP should be used for this account, `false` - otherwise.
     */
    fun setUseUPNP(isUseUPNP: Boolean) {
        putAccountProperty(ProtocolProviderFactory.IS_USE_UPNP, isUseUPNP)
    }

    /**
     * Minimum TLS protocol version used for TLS connections.
     *
     * @return minimum TLS protocol version. Default TLS 1.2
     */
    fun getMinimumTLSversion(): String? {
        return getAccountPropertyString(ProtocolProviderFactory.MINUMUM_TLS_VERSION, TLSUtils.PROTO_TLSV1_2)
    }

    /**
     * Sets the `minimumTLSversion` property.
     *
     * @param minimumTLSversion minimum TLS protocol version
     */
    fun setMinimumTLSversion(minimumTLSversion: String) {
        putAccountProperty(ProtocolProviderFactory.MINUMUM_TLS_VERSION, minimumTLSversion)
    }

    /**
     * Indicates if non-TLS is allowed for this account
     *
     * @return `true` if non-TLS is allowed for this account, otherwise returns `false`
     */
    fun isAllowNonSecure(): Boolean {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_ALLOW_NON_SECURE, false)
    }

    /**
     * Sets the `isAllowNonSecure` property.
     *
     * @param isAllowNonSecure `true` to indicate that non-TLS is allowed for this account, `false` otherwise.
     */
    fun setAllowNonSecure(isAllowNonSecure: Boolean) {
        putAccountProperty(ProtocolProviderFactory.IS_ALLOW_NON_SECURE, isAllowNonSecure)
    }

    /**
     * Indicates if message carbons are allowed for this account
     *
     * @return `true` if message carbons are allowed for this account, otherwise returns `false`
     */
    fun isCarbonDisabled(): Boolean {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_CARBON_DISABLED, false)
    }

    /**
     * Sets the `IS_CARBON_DISABLED` property.
     *
     * @param isCarbonEnabled `true` to indicate that message carbons are allowed for this account,
     * `false` otherwise.
     */
    fun setDisableCarbon(isCarbonEnabled: Boolean) {
        putAccountProperty(ProtocolProviderFactory.IS_CARBON_DISABLED, isCarbonEnabled)
    }

    /**
     * Is resource auto generate enabled.
     *
     * @return true if resource is auto generated
     */
    fun isResourceAutoGenerated(): Boolean {
        return getAccountPropertyBoolean(ProtocolProviderFactory.AUTO_GENERATE_RESOURCE, true)
    }

    /**
     * Set whether resource auto generation is enabled.
     *
     * @param resourceAutoGenerated `true` to indicate that the resource is to be auto generated,
     * `false` otherwise.
     */
    fun setResourceAutoGenerated(resourceAutoGenerated: Boolean) {
        putAccountProperty(ProtocolProviderFactory.AUTO_GENERATE_RESOURCE, resourceAutoGenerated)
    }

    /**
     * Returns the default sms server.
     *
     * @return the account default sms server
     */
    fun getSmsServerAddress(): String? {
        return getAccountPropertyString(ProtocolProviderFactory.SMS_SERVER_ADDRESS)
    }

    /**
     * Sets the default sms server.
     *
     * @param serverAddress the sms server to set as default
     */
    fun setSmsServerAddress(serverAddress: String?) {
        setOrRemoveIfEmpty(ProtocolProviderFactory.SMS_SERVER_ADDRESS, serverAddress)
    }

    /**
     * Returns the list of JingleNodes trackers/relays that this account is currently configured to use.
     *
     * @return the list of JingleNodes trackers/relays that this account is currently configured to use.
     */
    fun getJingleNodes(): List<JingleNodeDescriptor> {
        val accountProperties = accountProperties
        val serList: MutableList<JingleNodeDescriptor> = ArrayList()
        for (i in 0 until JingleNodeDescriptor.MAX_JN_RELAY_COUNT) {
            val node = JingleNodeDescriptor.loadDescriptor(accountProperties,
                JingleNodeDescriptor.JN_PREFIX + i) ?: break

            // If we don't find a relay server with the given index, it means that there're no
            // more servers left in the table so we've nothing more to do here.
            serList.add(node)
        }
        return serList
    }

    /**
     * Determines whether this account's provider is supposed to auto discover JingleNodes relay.
     *
     * @return `true` if this provider would need to discover JingleNodes relay,
     * `false` otherwise
     */
    fun isJingleNodesAutoDiscoveryEnabled(): Boolean {
        return getAccountPropertyBoolean(ProtocolProviderFactory.AUTO_DISCOVER_JINGLE_NODES, true)
    }

    /**
     * Determines whether this account's provider is supposed to auto discover JingleNodes relay by
     * searching our contacts.
     *
     * @return `true` if this provider would need to discover JingleNodes relay by searching
     * buddies, `false` otherwise
     */
    fun isJingleNodesSearchBuddiesEnabled(): Boolean {
        return getAccountPropertyBoolean(ProtocolProviderFactory.JINGLE_NODES_SEARCH_BUDDIES, false)
    }

    /**
     * Determines whether this account's provider uses JingleNodes relay (if available).
     *
     * @return `true` if this provider would use JingleNodes relay (if available),
     * `false` otherwise
     */
    fun isJingleNodesRelayEnabled(): Boolean {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_USE_JINGLE_NODES, true)
    }

    /**
     * {@inheritDoc}
     */
    override fun getDefaultString(key: String): String? {
        return getDefaultStr(key)
    }

    companion object {
        /**
         * Default properties prefix used in atalk-defaults.properties file for Jabber protocol.
         */
        private const val JBR_DEFAULT_PREFIX = DEFAULT_PREFIX + "jabber."

        /**
         * Uses anonymous XMPP login if set to `true`.
         */
        const val ANONYMOUS_AUTH = "ANONYMOUS_AUTH"

        /**
         * Account suffix for Google service.
         */
        const val GOOGLE_USER_SUFFIX = "gmail.com"

        /**
         * XMPP server for Google service.
         */
        const val GOOGLE_CONNECT_SRV = "talk.google.com"

        /**
         * The default value of stun server port for jabber accounts.
         */
        const val DEFAULT_STUN_PORT = "3478"

        /**
         * Indicates if gmail notifications should be enabled.
         */
        const val GMAIL_NOTIFICATIONS_ENABLED = "GMAIL_NOTIFICATIONS_ENABLED"

        /**
         * Always call with gtalk property.
         *
         * It is used to bypass capabilities checks: some software do not advertise GTalk support (but
         * they support it).
         */
        const val BYPASS_GTALK_CAPABILITIES = "BYPASS_GTALK_CAPABILITIES"

        /**
         * Indicates if Google Contacts should be enabled.
         */
        const val GOOGLE_CONTACTS_ENABLED = "GOOGLE_CONTACTS_ENABLED"

        /**
         * Domain name that will bypass GTalk caps.
         */
        const val TELEPHONY_BYPASS_GTALK_CAPS = "TELEPHONY_BYPASS_GTALK_CAPS"

        /**
         * The override domain for phone call.
         *
         * If Jabber account is able to call PSTN number and if domain name of the switch is different
         * than the domain of the account (gw.domain.org vs domain.org), you can use this property to
         * set the switch domain.
         */
        const val OVERRIDE_PHONE_SUFFIX = "OVERRIDE_PHONE_SUFFIX"

        /**
         * Returns the service name - the virtualHost (server) we are logging to if it is null which is
         * not supposed to be - we return for compatibility the string we used in the first release for
         * creating AccountID (Using this string is wrong, but used for compatibility for now)
         *
         * @param accountProperties Map
         * @return String
         */
        private fun getServiceName(accountProperties: Map<String, String?>): String? {
            // return accountProperties.get(ProtocolProviderFactory.SERVER_ADDRESS);
            val jid = accountProperties[ProtocolProviderFactory.USER_ID]
            return if (jid != null) XmppStringUtils.parseDomain(jid) else null
        }

        /**
         * Gets default property value for given `key`.
         *
         * @param key the property key
         * @return default property value for given`key`
         */
        fun getDefaultStr(key: String): String? {
            var value: String? = null
            val configService = ProtocolProviderActivator.getConfigurationService()
            if (configService != null) value = configService.getString(JBR_DEFAULT_PREFIX + key)
            return value ?: AccountID.getDefaultStr(key)
        }

        /**
         * Gets default boolean property value for given `key`.
         *
         * @param key the property key
         * @return default property value for given`key`
         */
        fun getDefaultBool(key: String): Boolean {
            return java.lang.Boolean.parseBoolean(getDefaultStr(key))
        }
    }
}