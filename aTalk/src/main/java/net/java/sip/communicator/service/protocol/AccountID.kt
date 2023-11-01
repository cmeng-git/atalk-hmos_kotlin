/*
 * aTalk / Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl
import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService
import net.java.sip.communicator.util.ServiceUtils.getService
import net.java.sip.communicator.util.account.AccountUtils.getRegisteredProviderForAccount
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.account.settings.BoshProxyDialog
import org.atalk.service.neomedia.SrtpControlType
import org.json.JSONException
import org.json.JSONObject
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.DomainBareJid
import org.osgi.framework.BundleContext
import timber.log.Timber
import java.util.*

/**
 * The AccountID is an account identifier that, uniquely represents a specific user account over a
 * specific protocol. The class needs to be extended by every protocol implementation because of its
 * protected constructor. The reason why this constructor is protected is mostly avoiding confusion
 * and letting people (using the protocol provider service) believe that they are the ones who are
 * supposed to instantiate the AccountID class.
 *
 * Every instance of the `ProtocolProviderService`, created through the
 * ProtocolProviderFactory is assigned an AccountID instance, that uniquely represents it and whose
 * string representation (obtained through the getAccountUniqueID() method) can be used for
 * identification of persistently stored account details.
 *
 * Account id's are guaranteed to be different for different accounts and in the same time are bound
 * to be equal for multiple installations of the same account.
 *
 * @author Emil Ivov
 * @author Lubomir Marinov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class AccountID protected constructor(userID: String?, accountProperties: MutableMap<String, String?>,
        /**
         * The real protocol name.
         */
        open val protocolName: String, serviceName: String?) {

    protected var avatarHash: String? = null

    private var rosterVersion: String? = null

    val otrFingerprint: String? = null

    protected var statusMessage = "status_Message"

    private var mKeys = JSONObject()

    /**
     * The protocol display name. In the case of overridden protocol name this would be the new name.
     */
    val protocolDisplayName: String?

    /**
     * Contains all implementation specific properties that define the account. The exact names
     * of the keys are protocol (and sometimes implementation) specific. Currently, only String
     * property keys and values will get properly stored. If you need something else, please
     * consider converting it through custom accessors (get/set) in your implementation.
     */
    protected var mAccountProperties: MutableMap<String, String?>

    /**
     * @return e.g. acc1567990097080
     */
    /**
     * A String uniquely identifying the user for this particular account with prefix "acc", and
     * is used as link in the account properties retrieval
     */
    val accountUuid: String?
    /**
     * Returns a String uniquely identifying this account, guaranteed to remain the same across
     * multiple installations of the same account and to always be unique for differing accounts.
     *
     * @return String
     */
    /**
     * A String uniquely identifying this account, that can also be used for storing and
     * unambiguously retrieving details concerning it. e.g. jabber:abc123@example.org
     */
    var accountUniqueID: String?
        protected set

    /**
     * A String uniquely identifying the user for this particular account. e.g. abc123@example.org
     */
    var mUserID: String? = null

    // Override for Jabber implementation for the BareJid e.g. abc123@example.org.
    /**
     * An XMPP Jabber ID associated with this particular account. e.g. abc123@example.org
     */
    var bareJid: BareJid? = null
        protected set
    /**
     * Returns the name of the service that defines the context for this account. Often this name
     * would be an FQDN or even an ipAddress but this would not always be the case (e.g. p2p
     * providers may return a name that does not directly correspond to an IP address or host name).
     *
     * @return the name of the service that defines the context for this account.
     */
    /**
     * The name of the service that defines the context for this account. e.g. example.org
     */
    val service: String?

    /**
     * Creates an account id for the specified provider userId and accountProperties. If account
     * uid exists in account properties, we are loading the account and so load its value from
     * there, prevent changing account uid when server changed (serviceName has changed).
     *
     * userID a String that uniquely identifies the user.
     * accountProperties a Map containing any other protocol and implementation specific account
     * initialization properties
     * protocolName the protocol name implemented by the provider that this id is meant for e.g. Jabber
     * serviceName the name of the service is what follows after the '@' sign in XMPP addresses (JIDs).
     * (e.g. iptel.org, jabber.org, icq.com) the service of the account registered with.
     *
     * Note: parameters userID is null and new empty accountProperties when called from
     * @see @jabber.JabberAccountRegistration or
     *
     * @see @sip.SIPAccountRegistration constructor
     */

    init {
        /*
         * Allow account registration wizards to override the default protocol name through
         * accountProperties for the purposes of presenting a well-known protocol name associated
         * with the account that is different from the name of the effective protocol.
         */
        protocolDisplayName = getOverriddenProtocolName(accountProperties, protocolName)
        mUserID = userID
        mAccountProperties = HashMap(accountProperties)
        service = serviceName
        accountUuid = accountProperties[ProtocolProviderFactory.ACCOUNT_UUID]
        accountUniqueID = accountProperties[ProtocolProviderFactory.ACCOUNT_UID]
        var tmp = JSONObject()
        val strKeys = accountProperties[ProtocolProviderFactory.KEYS]
        if (StringUtils.isNotEmpty(strKeys)) {
            try {
                tmp = JSONObject(strKeys!!)
            } catch (e: JSONException) {
                Timber.w("Cannot convert JSONObject from: %s", strKeys)
            }
        }
        mKeys = tmp
        Timber.d("### Set Account UUID to: %s: %s for %s", accountUuid, accountUniqueID, userID)
    }

    /**
     * Get the Entity XMPP domain. The XMPP domain is what follows after the '@' sign in XMPP addresses (JIDs).
     *
     * @return XMPP service domain.
     */
    val xmppDomain: DomainBareJid
        get() = bareJid!!.asDomainBareJid()// If the ACCOUNT_DISPLAY_NAME property has been set for this account, we'll be using it
    // as a display name.

    // Otherwise construct a display name.
    /**
     * Sets [ProtocolProviderFactory.DISPLAY_NAME] property value.
     *
     * displayName the display name value to set.
     */
    /**
     * Returns a name that can be displayed to the user when referring to this account.
     * e.g. abc123@example.org or abc123@example.org (jabber). Create one if none is found
     *
     * @return A String identifying the user inside this particular service.
     */
    var displayName: String?
        get() {
            // If the ACCOUNT_DISPLAY_NAME property has been set for this account, we'll be using it
            // as a display name.
            val key = ProtocolProviderFactory.ACCOUNT_DISPLAY_NAME
            val accountDisplayName = mAccountProperties[key]
            if (StringUtils.isNotEmpty(accountDisplayName)) {
                return accountDisplayName
            }

            // Otherwise construct a display name.
            var returnValue = mUserID
            val protocolName = protocolDisplayName
            if (StringUtils.isNotEmpty(protocolName)) {
                returnValue += " ($protocolName)"
            }
            return returnValue
        }
        set(displayName) {
            setOrRemoveIfEmpty(ProtocolProviderFactory.DISPLAY_NAME, displayName)
        }

    /**
     * Gets the ProtocolProviderService for mAccountID
     *
     * @return the ProtocolProviderService if currently registered for the AccountID or `null` otherwise
     */
    val protocolProvider: ProtocolProviderService?
        get() = getRegisteredProviderForAccount(this)

    /**
     * Returns a Map containing protocol and implementation account initialization properties.
     *
     * @return a Map containing protocol and implementation account initialization properties.
     */
    val accountProperties: MutableMap<String, String?>
        get() = HashMap(mAccountProperties)

    /**
     * Returns the specific account property.
     *
     * @param key property key
     * @param defaultValue default value if the property does not exist
     * @return property value corresponding to property key
     */
    fun getAccountPropertyBoolean(key: Any, defaultValue: Boolean): Boolean {
        val value = getAccountPropertyString(key)
        return value?.toBoolean() ?: defaultValue
    }

    /**
     * Gets the value of a specific property as a signed decimal integer. If the specified property
     * key is associated with a value in this `AccountID`, the string representation of the
     * value is parsed into a signed decimal integer according to the rules of
     * [Integer.parseInt]. If parsing the value as a signed decimal integer fails or
     * there is no value associated with the specified property key, `defaultValue` is returned.
     *
     * @param key the key of the property to get the value of as a signed decimal integer
     * @param defaultValue the value to be returned if parsing the value of the specified property key as a
     * signed decimal integer fails or there is no value associated with the specified
     * property key in this `AccountID`
     * @return the value of the property with the specified key in this `AccountID` as a
     * signed decimal integer; `defaultValue` if parsing the value of the specified
     * property key fails or no value is associated in this `AccountID` with the
     * specified property name
     */
    fun getAccountPropertyInt(key: Any, defaultValue: Int): Int {
        var intValue = defaultValue
        val stringValue = getAccountPropertyString(key)
        if (StringUtils.isNotEmpty(stringValue)) {
            try {
                intValue = stringValue!!.toInt()
            } catch (ex: NumberFormatException) {
                Timber.e("Failed to parse account property %s value %s as an integer: %s",
                        key, stringValue, ex.message)
            }
        }
        return intValue
    }

    /**
     * Returns the account property string corresponding to the given key.
     *
     * key the key, corresponding to the property string we're looking for
     * @return the account property string corresponding to the given key
     */
    fun getAccountPropertyString(key: Any): String? {
        return getAccountPropertyString(key, null)
    }

    /**
     * Returns the account property string corresponding to the given key.
     *
     * @param key the key, corresponding to the property string we're looking for
     * @param defValue the default value returned when given `key` is not present
     * @return the account property string corresponding to the given key
     */
    fun getAccountPropertyString(key: Any, defValue: String?): String? {
        val property = key.toString()
        var value = mAccountProperties[property]
        if (value == null) {
            // try load from accountProperties and keep a copy in mAccountProperties if found for later retrieval
            val configService = ProtocolProviderActivator.getConfigurationService()

            if (configService != null) {
                value = configService.getString("$accountUuid.$property")
                if (StringUtils.isNotEmpty(value)) {
                    putAccountProperty(key.toString(), value!!)
                } else {
                    value = getDefaultString(property)
                }
            } else {
                value = getDefaultString(property)
            }
        }
        return value ?: defValue
    }

    /**
     * Store the value to the account property of the given key to persistence store.
     *
     * @param key the name of the property to change.
     * @param property the new value of the specified property. Null will remove the propertyName item
     */
    fun storeAccountProperty(key: String, property: Any?) {
        val accPropertyName = "$accountUuid.$key"
        val configService = ProtocolProviderActivator.getConfigurationService()
        if (configService != null) {
            if (property != null)
                putAccountProperty(key, property)
            else
                removeAccountProperty(key)

            configService.setProperty(accPropertyName, property)
        }
    }

    /**
     * Adds a property to the map of properties for this account identifier.
     *
     * @param key the key of the property
     * @param value the property value.
     */
    fun putAccountProperty(key: String, value: String) {
        mAccountProperties[key] = value
    }

    /**
     * Adds property to the map of properties for this account identifier.
     *
     * @param key the key of the property
     * @param value the property value
     */
    fun putAccountProperty(key: String, value: Any) {
        mAccountProperties[key] = value.toString()
    }

    /**
     * Removes specified account property.
     *
     * @param key the key to remove.
     */
    fun removeAccountProperty(key: String) {
        mAccountProperties.remove(key)
    }

    /**
     * Returns a hash code value for the object. This method is supported for the benefit of
     * HashTables such as those provided by `java.util.Hashtable`.
     *
     * @return a hash code value for this object.
     * @see java.lang.Object.equals
     * @see java.util.Hashtable
     */
    override fun hashCode(): Int {
        return if (accountUniqueID == null) 0 else accountUniqueID.hashCode()
    }

    /**
     * Indicates whether some other object is "equal to" this account id.
     *
     * @param other the reference object with which to compare.
     * @return `true` if this object is the same as the obj argument; `false` otherwise.
     * @see .hashCode
     * @see java.util.Hashtable
     */
    override fun equals(other: Any?): Boolean {
        return (((this === other) || ((other != null) && javaClass.isInstance(other) && (accountUniqueID == (other as AccountID).accountUniqueID))))
    }

    /**
     * Returns a string representation of this account id (same as calling getAccountUniqueID()).
     *
     * @return a string representation of this account id.
     */
    override fun toString(): String {
        return accountUniqueID!!
    }

    /**
     * Returns a string that could be directly used (or easily converted to) an address that other
     * users of the protocol can use to communicate with us. By default this string is set to
     * userID@serviceName. Protocol implementors should override it if they'd need it to respect a
     * different syntax.
     *
     * @return a String in the form of userID@service that other protocol users should be able to
     * parse into a meaningful address and use it to communicate with us.
     */
    open val accountJid: String
        get() = if (mUserID!!.indexOf('@') > 0) mUserID!! else "$mUserID@$service"

    /**
     * Indicates if this account is currently enabled.
     *
     * @return `true` if this account is enabled, `false` - otherwise.
     */
    val isEnabled: Boolean
        get() = !getAccountPropertyBoolean(ProtocolProviderFactory.IS_ACCOUNT_DISABLED, false)

    /**
     * Get the [ProtocolProviderFactory.ACCOUNT_DISPLAY_NAME] property.
     *
     * @return the [ProtocolProviderFactory.ACCOUNT_DISPLAY_NAME] property value.
     */

    /**
     * Sets [ProtocolProviderFactory.ACCOUNT_DISPLAY_NAME] property value.
     *
     * displayName the account display name value to set.
     */
    var accountDisplayName: String?
        get() = getAccountPropertyString(ProtocolProviderFactory.ACCOUNT_DISPLAY_NAME)
        set(displayName) {
            setOrRemoveIfEmpty(ProtocolProviderFactory.ACCOUNT_DISPLAY_NAME, displayName)
        }

    /**
     * The password of the account.
     */
    var password: String?
        get() = getAccountPropertyString(ProtocolProviderFactory.PASSWORD)
        set(password) {
            setOrRemoveIfEmpty(ProtocolProviderFactory.PASSWORD, password)
        }

    /**
     * Determines whether or not the passWord is to be stored persistently (insecure!) or not.
     *
     * @return true if the underlying protocol provider is to persistently (and possibly
     * insecurely) store the passWord and false otherwise.
     * Note: Default must set to be the same default as in, until user changes it
     * @link JabberPreferenceFragment.rememberPassword
     */
    /**
     * Specifies whether or not the passWord is to be stored persistently (insecure!) or not.
     *
     * storePassword indicates whether password is to be stored persistently.
     */
    var isPasswordPersistent: Boolean
        get() = getAccountPropertyBoolean(ProtocolProviderFactory.PASSWORD_PERSISTENT, true)
        set(storePassword) {
            putAccountProperty(ProtocolProviderFactory.PASSWORD_PERSISTENT, storePassword)
        }

    /**
     * The dnssMode of the account.
     */
    var dnssMode: String?
        get() = getAccountPropertyString(ProtocolProviderFactory.DNSSEC_MODE,
                aTalkApp.appResources.getStringArray(R.array.dnssec_Mode_value)[0])
        set(dnssMode) {
            setOrRemoveIfEmpty(ProtocolProviderFactory.DNSSEC_MODE, dnssMode)
        }

    /**
     * The authorization name.
     */
    var authorizationName: String?
        get() = getAccountPropertyString(ProtocolProviderFactory.AUTHORIZATION_NAME)
        set(authName) {
            setOrRemoveIfEmpty(ProtocolProviderFactory.AUTHORIZATION_NAME, authName)
        }

    /**
     * Determines whether sending of keep alive packets is enabled.
     */
    val isKeepAliveEnable: Boolean
        get() = getAccountPropertyBoolean(ProtocolProviderFactory.IS_KEEP_ALIVE_ENABLE, false)

    /**
     * Specifies whether SIP Communicator should send keep alive packets to keep this account registered.
     *
     * isKeepAliveEnable `true` if we are to send keep alive packets and `false` otherwise.
     */
    fun setKeepAliveOption(isKeepAliveEnable: Boolean) {
        putAccountProperty(ProtocolProviderFactory.IS_KEEP_ALIVE_ENABLE, isKeepAliveEnable)
    }

    /**
     * Determines whether ping interval auto optimization is enabled.
     *
     * @return `true` if ping interval optimization for this account is enabled and `false` otherwise.
     */
    val isPingAutoTuneEnable: Boolean
        get() = getAccountPropertyBoolean(ProtocolProviderFactory.IS_PING_AUTO_TUNE_ENABLE, true)

    /**
     * Specifies whether protocol provide should perform auto ping optimization for this account registered.
     *
     * isPingAutoEnable `true` if allow to perform ping auto optimization, `false` otherwise.
     */
    fun setPingAutoTuneOption(isPingAutoEnable: Boolean) {
        putAccountProperty(ProtocolProviderFactory.IS_PING_AUTO_TUNE_ENABLE, isPingAutoEnable)
    }
    /**
     * Get the network Ping Interval default to aTalk default
     *
     * @return int
     */
    /**
     * Sets the network ping interval.
     *
     * interval Keep alive ping interval
     */
    var pingInterval: String?
        get() = getAccountPropertyString(ProtocolProviderFactory.PING_INTERVAL,
                ProtocolProviderServiceJabberImpl.defaultPingInterval.toString())
        set(interval) {
            setOrRemoveIfEmpty(ProtocolProviderFactory.PING_INTERVAL, interval)
        }

    /**
     * Returns `true` if server was overridden.
     *
     * @return `true` if server was overridden.
     */
    /**
     * Sets `isServerOverridden` property.
     *
     * isServerOverridden indicates if the server is overridden
     */
    var isServerOverridden: Boolean
        get() = getAccountPropertyBoolean(ProtocolProviderFactory.IS_SERVER_OVERRIDDEN, false)
        set(isServerOverridden) {
            putAccountProperty(ProtocolProviderFactory.IS_SERVER_OVERRIDDEN, isServerOverridden)
        }

    /**
     * The address of the server we will use for this account.  Default to serviceName if null.
     *
     * @return String
     */
    /**
     * Sets the server
     *
     * serverAddress String
     */
    var serverAddress: String?
        get() = getAccountPropertyString(ProtocolProviderFactory.SERVER_ADDRESS, service)
        set(serverAddress) {
            setOrRemoveIfEmpty(ProtocolProviderFactory.SERVER_ADDRESS, serverAddress)
        }

    /**
     * The port on the specified server. Return DEFAULT_PORT if null.
     *
     * @return int
     */
    /**
     * Sets the server port.
     *
     * port proxy server port
     */
    var serverPort: String?
        get() = getAccountPropertyString(ProtocolProviderFactory.SERVER_PORT, DEFAULT_PORT)
        set(port) {
            setOrRemoveIfEmpty(ProtocolProviderFactory.SERVER_PORT, port)
        }// The isUseProxy state is to take care of old DB?

    /**
     * Sets the `useProxy` property.
     *
     * isUseProxy `true` to indicate that Proxy should be used for this account, `false` - otherwise.
     */
    /**
     * Indicates if proxy should be used for this account if Type != NONE.
     *
     * @return `true` if (Type != NONE) for this account, otherwise returns `false`
     */
    var isUseProxy: Boolean
        get() {
            // The isUseProxy state is to take care of old DB?
            val isUseProxy = "true" == getAccountPropertyString(ProtocolProviderFactory.IS_USE_PROXY)
            return isUseProxy && BoshProxyDialog.NONE != getAccountPropertyString(ProtocolProviderFactory.PROXY_TYPE)
        }
        set(isUseProxy) {
            putAccountProperty(ProtocolProviderFactory.IS_USE_PROXY, isUseProxy)
        }

    /**
     * The Type of proxy we will use for this account
     *
     * @return String
     */
    /**
     * Sets the Proxy Type
     *
     * proxyType String
     */
    var proxyType: String?
        get() = getAccountPropertyString(ProtocolProviderFactory.PROXY_TYPE)
        set(proxyType) {
            setOrRemoveIfEmpty(ProtocolProviderFactory.PROXY_TYPE, proxyType)
        }

    /**
     * The address of the proxy we will use for this account
     *
     * @return String
     */
    /**
     * Sets the proxy address
     *
     * proxyAddress String
     */
    var proxyAddress: String?
        get() = getAccountPropertyString(ProtocolProviderFactory.PROXY_ADDRESS)
        set(proxyAddress) {
            setOrRemoveIfEmpty(ProtocolProviderFactory.PROXY_ADDRESS, proxyAddress)
        }

    /**
     * The port on the specified proxy
     *
     * @return int
     */
    /**
     * Sets the proxy port.
     *
     * proxyPort int
     */
    open var proxyPort: String?
        get() = getAccountPropertyString(ProtocolProviderFactory.PROXY_PORT)
        set(proxyPort) {
            setOrRemoveIfEmpty(ProtocolProviderFactory.PROXY_PORT, proxyPort)
        }

    /**
     * The port on the specified server
     *
     * @return int
     */
    /**
     * Sets the server port.
     *
     * port int
     */
    var proxyUserName: String?
        get() = getAccountPropertyString(ProtocolProviderFactory.PROXY_USERNAME)
        set(port) {
            setOrRemoveIfEmpty(ProtocolProviderFactory.PROXY_USERNAME, port)
        }

    /**
     * The port on the specified server
     *
     * @return int
     */
    /**
     * Sets the server port.
     *
     * port int
     */
    var proxyPassword: String?
        get() = getAccountPropertyString(ProtocolProviderFactory.PROXY_PASSWORD)
        set(port) {
            setOrRemoveIfEmpty(ProtocolProviderFactory.PROXY_PASSWORD, port)
        }

    /**
     * Returns `true` if the account requires IB Registration with the server
     *
     * @return `true` if account requires IB Registration with the server
     */
    /**
     * Sets `IBR_REGISTRATION` property.
     *
     * ibRegistration indicates if the account wants to perform an IBR registration with the server
     */
    var isIbRegistration: Boolean
        get() = getAccountPropertyBoolean(ProtocolProviderFactory.IBR_REGISTRATION, false)
        set(ibRegistration) {
            putAccountProperty(ProtocolProviderFactory.IBR_REGISTRATION, ibRegistration)
        }

    /**
     * Returns the protocol icon path stored under
     * [ProtocolProviderFactory.PROTOCOL_ICON_PATH] key.
     *
     * @return the protocol icon path.
     */
    /**
     * Sets the protocol icon path that will be held under
     * [ProtocolProviderFactory.PROTOCOL_ICON_PATH] key.
     *
     * iconPath a path to the protocol icon to set.
     */
    private var protocolIconPath: String?
        get() = getAccountPropertyString(ProtocolProviderFactory.PROTOCOL_ICON_PATH)
        set(iconPath) {
            putAccountProperty(ProtocolProviderFactory.PROTOCOL_ICON_PATH, iconPath!!)
        }

    /**
     * Returns the protocol icon path stored under
     * [ProtocolProviderFactory.ACCOUNT_ICON_PATH] key.
     *
     * @return the protocol icon path.
     */
    /**
     * Sets the account icon path that will be held under
     * [ProtocolProviderFactory.ACCOUNT_ICON_PATH] key.
     *
     * iconPath a path to the account icon to set.
     */
    private var accountIconPath: String?
        get() = getAccountPropertyString(ProtocolProviderFactory.ACCOUNT_ICON_PATH)
        set(iconPath) {
            putAccountProperty(ProtocolProviderFactory.ACCOUNT_ICON_PATH, iconPath!!)
        }

    /**
     * Returns the DTMF method.
     *
     * @return the DTMF method.
     */
    /**
     * Sets the DTMF method.
     *
     * dtmfMethod the DTMF method to set
     */
    var dtmfMethod: String?
        get() = getAccountPropertyString(ProtocolProviderFactory.DTMF_METHOD)
        set(dtmfMethod) {
            putAccountProperty(ProtocolProviderFactory.DTMF_METHOD, dtmfMethod!!)
        }

    /**
     * Returns the minimal DTMF tone duration.
     *
     * @return The minimal DTMF tone duration.
     */
    /**
     * Sets the minimal DTMF tone duration.
     *
     * dtmfMinimalToneDuration The minimal DTMF tone duration to set.
     */
    var dtmfMinimalToneDuration: String?
        get() = getAccountPropertyString(ProtocolProviderFactory.DTMF_MINIMAL_TONE_DURATION)
        set(dtmfMinimalToneDuration) {
            putAccountProperty(ProtocolProviderFactory.DTMF_MINIMAL_TONE_DURATION, dtmfMinimalToneDuration!!)
        }

    /**
     * Gets the ID of the client certificate configuration.
     *
     * @return the ID of the client certificate configuration.
     */
    /**
     * Sets the ID of the client certificate configuration.
     *
     * id the client certificate configuration template ID.
     */
    var tlsClientCertificate: String?
        get() = getAccountPropertyString(ProtocolProviderFactory.CLIENT_TLS_CERTIFICATE)
        set(id) {
            setOrRemoveIfEmpty(ProtocolProviderFactory.CLIENT_TLS_CERTIFICATE, id)
        }

    /**
     * Checks if the account is hidden.
     *
     * @return `true` if this account is hidden or `false` otherwise.
     */
    val isHidden: Boolean
        get() = getAccountPropertyString(ProtocolProviderFactory.IS_PROTOCOL_HIDDEN) != null

    /**
     * Checks if the account config is hidden.
     *
     * @return `true` if the account config is hidden or `false` otherwise.
     */
    val isConfigHidden: Boolean
        get() = getAccountPropertyString(ProtocolProviderFactory.IS_ACCOUNT_CONFIG_HIDDEN) != null

    /**
     * Checks if the account status menu is hidden.
     *
     * @return `true` if the account status menu is hidden or `false` otherwise.
     */
    val isStatusMenuHidden: Boolean
        get() = getAccountPropertyString(ProtocolProviderFactory.IS_ACCOUNT_STATUS_MENU_HIDDEN) != null

    /**
     * Checks if the account is marked as readonly.
     *
     * @return `true` if the account is marked as readonly or `false` otherwise.
     */
    val isReadOnly: Boolean
        get() = getAccountPropertyString(ProtocolProviderFactory.IS_ACCOUNT_READ_ONLY) != null

    /**
     * Returns the first `ProtocolProviderService` implementation corresponding to the
     * preferred protocol
     *
     * @return the `ProtocolProviderService` corresponding to the preferred protocol
     */
    val isPreferredProvider: Boolean
        get() {
            val preferredProtocolProp = getAccountPropertyString(ProtocolProviderFactory.IS_PREFERRED_PROTOCOL)
            return StringUtils.isNotEmpty(preferredProtocolProp) && java.lang.Boolean.parseBoolean(preferredProtocolProp)
        }

    /**
     * Set the account properties.
     *
     * accountProperties the properties of the account
     */
    fun setAccountProperties(accountProperties: MutableMap<String, String?>) {
        mAccountProperties = accountProperties
    }

    /**
     * Returns if the encryption protocol given in parameter is enabled.
     *
     * srtpType The name of the encryption protocol ("ZRTP", "SDES" or "MIKEY").
     */
    fun isEncryptionProtocolEnabled(srtpType: SrtpControlType): Boolean {
        // The default value is false, except for ZRTP.
        val defaultValue = (srtpType == SrtpControlType.ZRTP)
        return getAccountPropertyBoolean(ProtocolProviderFactory.ENCRYPTION_PROTOCOL_STATUS
                + "." + srtpType.toString(), defaultValue)
    }

    /**
     * Returns the list of STUN servers that this account is currently configured to use.
     *
     * @return the list of STUN servers that this account is currently configured to use.
     */
    fun getStunServers(bundleContext: BundleContext?): List<StunServerDescriptor> {
        val accountProperties = accountProperties
        val stunServerList = ArrayList<StunServerDescriptor>()
        for (i in 0 until StunServerDescriptor.MAX_STUN_SERVER_COUNT) {
            val stunServer = StunServerDescriptor.loadDescriptor(
                    accountProperties, ProtocolProviderFactory.STUN_PREFIX + i) ?: break

            // If we don't find a stun server with the given index, it means there are no more
            // servers left in the table so we've nothing more to do here.
            val password = loadStunPassword(bundleContext, this, ProtocolProviderFactory.STUN_PREFIX + i)
            if (password != null) stunServer.setPassword(password)
            stunServerList.add(stunServer)
        }
        return stunServerList
    }

    /**
     * Determines whether this account's provider is supposed to auto discover STUN and TURN servers.
     *
     * @return `true` if this provider would need to discover STUN/TURN servers
     * otherwise false if serverOverride is enabled; serviceDomain is likely not reachable.
     */
    val isStunServerDiscoveryEnabled: Boolean
        get() = getAccountPropertyBoolean(ProtocolProviderFactory.AUTO_DISCOVER_STUN, !isServerOverridden)

    /**
     * Determines whether this account's provider uses UPnP (if available).
     *
     * @return `true` if this provider would use UPnP (if available), `false`
     * otherwise
     */
    val isUPNPEnabled: Boolean
        get() = getAccountPropertyBoolean(ProtocolProviderFactory.IS_USE_UPNP, true)

    /**
     * Determines whether this account's provider uses the default STUN server provided by Jitsi
     * (stun.jitsi.net) if there is no other STUN/TURN server discovered/configured.
     *
     * @return `true` if this provider would use the default STUN server, `false`
     * otherwise
     */
    val isUseDefaultStunServer: Boolean
        get() = getAccountPropertyBoolean(ProtocolProviderFactory.USE_DEFAULT_STUN_SERVER, true)

    /**
     * Returns the actual name of the protocol used rather than a branded variant. The method is
     * primarily meant for open protocols such as SIP or XMPP so that it would always return SIP
     * or XMPP even in branded protocols who otherwise return things like GTalk and ippi for
     * PROTOCOL_NAME.
     *
     * @return the real non-branded name of the protocol.
     */

    open val systemProtocolName: String?
        get() = protocolName

    // If the key is set. {
    // Second: remove all disabled protocols.
    // If the account is not yet configured, then ZRTP is activated by default.

    // First: add all protocol in the right order.
    /**
     * Sorts the enabled encryption protocol list given in parameter to match the preferences set
     * for this account.
     *
     * @return Sorts the enabled encryption protocol list given in parameter to match the
     * preferences set for this account.
     */
    val sortedEnabledEncryptionProtocolList: List<SrtpControlType>
        get() {
            val encryptionProtocol = getIntegerPropertiesByPrefix(ProtocolProviderFactory.ENCRYPTION_PROTOCOL, true)
            val encryptionProtocolStatus = getBooleanPropertiesByPrefix(ProtocolProviderFactory.ENCRYPTION_PROTOCOL_STATUS, true, false)

            // If the account is not yet configured, then ZRTP is activated by default.
            if (encryptionProtocol.isEmpty()) {
                encryptionProtocol[ProtocolProviderFactory.ENCRYPTION_PROTOCOL + ".ZRTP"] = 0
                encryptionProtocolStatus[ProtocolProviderFactory.ENCRYPTION_PROTOCOL_STATUS + ".ZRTP"] = true
            }
            val sortedEncryptionProtocols = ArrayList<SrtpControlType>(encryptionProtocol.size)

            // First: add all protocol in the right order.
            for (e in encryptionProtocol.entries) {
                var index = e.value
                if (index != -1) {
                    // If the key is set. {
                    if (index > sortedEncryptionProtocols.size) {
                        index = sortedEncryptionProtocols.size
                    }
                    val name = e.key.substring(ProtocolProviderFactory.ENCRYPTION_PROTOCOL.length + 1)
                    try {
                        sortedEncryptionProtocols.add(index, SrtpControlType.valueOf(name))
                    } catch (exc: IllegalArgumentException) {
                        Timber.e(exc, "Failed to get SRTP control type for name: '%s', key: '%s'", name, e.key)
                    }
                }
            }
            // Second: remove all disabled protocols.
            val i = sortedEncryptionProtocols.iterator()
            while (i.hasNext()) {
                val encryptProtoName = "ENCRYPTION_PROTOCOL_STATUS." + i.next().toString()
                if (!encryptionProtocolStatus.containsKey(encryptProtoName)) {
                    i.remove()
                }
            }
            return sortedEncryptionProtocols
        }

    /**
     * Returns a `java.util.Map` of `String`s containing the all property names that
     * have the specified prefix and `Boolean` containing the value for each property
     * selected. Depending on the value of the `exactPrefixMatch` parameter the method will
     * (when false) or will not (when exactPrefixMatch is true) include property names that have
     * prefixes longer than the specified `prefix` param.
     *
     * Example:
     * Imagine a configuration service instance containing 2 properties only:<br></br>
     * `
     * net.java.sip.communicator.PROP1=value1<br></br>
     * net.java.sip.communicator.service.protocol.PROP1=value2
    ` *
     *
     * A call to this method with a prefix="net.java.sip.communicator" and exactPrefixMatch=true
     * would only return the first property - net.java.sip.communicator.PROP1, whereas the same
     * call with exactPrefixMatch=false would return both properties as the second prefix includes
     * the requested prefix string.
     *
     * prefix a String containing the prefix (the non dotted non-caps part of a property name) that
     * we're looking for.
     * exactPrefixMatch a boolean indicating whether the returned property names should all have a prefix that
     * is an exact match of the the `prefix` param or whether properties with prefixes
     * that contain it but are longer than it are also accepted.
     * defaultValue the default value if the key is not set.
     * @return a `java.util.Map` containing all property name String-s matching the specified
     * conditions and the corresponding values as Boolean.
     */
    fun getBooleanPropertiesByPrefix(prefix: String,
            exactPrefixMatch: Boolean, defaultValue: Boolean): MutableMap<String, Boolean> {
        val propertyNames = getPropertyNamesByPrefix(prefix, exactPrefixMatch)
        val properties = HashMap<String, Boolean>(propertyNames.size)
        for (propertyName in propertyNames) {
            properties[propertyName] = getAccountPropertyBoolean(propertyName, defaultValue)
        }
        return properties
    }

    /**
     * Returns a `java.util.Map` of `String`s containing the all property names that
     * have the specified prefix and `Integer` containing the value for each property
     * selected. Depending on the value of the `exactPrefixMatch` parameter the method will
     * (when false) or will not (when exactPrefixMatch is true) include property names that have
     * prefixes longer than the specified `prefix` param.
     *
     * Example:
     * Imagine a configuration service instance containing 2 properties only:<br></br>
     * `
     * net.java.sip.communicator.PROP1=value1<br></br>
     * net.java.sip.communicator.service.protocol.PROP1=value2
    ` *
     *
     * A call to this method with a prefix="net.java.sip.communicator" and exactPrefixMatch=true
     * would only return the first property - net.java.sip.communicator.PROP1, whereas the same
     * call with exactPrefixMatch=false would return both properties as the second prefix includes
     * the requested prefix string.
     *
     * prefix a String containing the prefix (the non dotted non-caps part of a property name) that
     * we're looking for.
     * exactPrefixMatch a boolean indicating whether the returned property names should all have a prefix that
     * is an exact match of the the `prefix` param or whether properties with prefixes
     * that contain it but are longer than it are also accepted.
     * @return a `java.util.Map` containing all property name String-s matching the specified
     * conditions and the corresponding values as Integer.
     */
    fun getIntegerPropertiesByPrefix(prefix: String, exactPrefixMatch: Boolean): MutableMap<String, Int> {
        val propertyNames = getPropertyNamesByPrefix(prefix, exactPrefixMatch)
        val properties = HashMap<String, Int>(propertyNames.size)
        for (propertyName in propertyNames) {
            properties[propertyName] = getAccountPropertyInt(propertyName, -1)
        }
        return properties
    }

    /**
     * Returns a `java.util.List` of `String`s containing the all property names that
     * have the specified prefix. Depending on the value of the `exactPrefixMatch` parameter
     * the method will (when false) or will not (when exactPrefixMatch is true) include property
     * names that have prefixes longer than the specified `prefix` param.
     *
     * Example:
     * Imagine a configuration service instance containing 2 properties only:<br></br>
     * `
     * net.java.sip.communicator.PROP1=value1<br></br>
     * net.java.sip.communicator.service.protocol.PROP1=value2
    ` *
     *
     * A call to this method with a prefix="net.java.sip.communicator" and exactPrefixMatch=true
     * would only return the first property - net.java.sip.communicator.PROP1, whereas the same call
     * with exactPrefixMatch=false would return both properties as the second prefix includes the
     * requested prefix string.
     *
     * prefix a String containing the prefix (the non dotted non-caps part of a property name) that
     * we're looking for.
     * exactPrefixMatch a boolean indicating whether the returned property names should all have a prefix that
     * is an exact match of the the `prefix` param or whether properties with prefixes
     * that contain it but are longer than it are also accepted.
     * @return a `java.util.List`containing all property name String-s matching the
     * specified conditions.
     */
    fun getPropertyNamesByPrefix(prefix: String, exactPrefixMatch: Boolean): List<String> {
        val resultKeySet = LinkedList<String>()
        for (key in mAccountProperties.keys) {
            val ix = key.lastIndexOf('.')
            if (ix != -1) {
                val keyPrefix = key.substring(0, ix)
                if (exactPrefixMatch) {
                    if (prefix == keyPrefix) resultKeySet.add(key)
                } else if (keyPrefix.startsWith(prefix)) {
                    resultKeySet.add(key)
                }
            }
        }
        return resultKeySet
    }

    /**
     * Sets the property a new value, but only if it's not `null` or the property is removed
     * from the map.
     *
     * key the property key
     * value the property value
     */
    fun setOrRemoveIfNull(key: String, value: String?) {
        if (value != null) {
            putAccountProperty(key, value)
        } else {
            removeAccountProperty(key)
        }
    }

    /**
     * Puts the new property value if it's not `null` nor empty.
     *
     * key the property key
     * value the property value
     */
    fun setOrRemoveIfEmpty(key: String, value: String?) {
        setOrRemoveIfEmpty(key, value, false)
    }

    /**
     * Puts the new property value if it's not `null` nor empty. If `trim` parameter
     * is set to `true` the string will be trimmed, before checked for emptiness.
     *
     * key the property key
     * value the property value
     * trim `true` if the value will be trimmed, before `isEmpty()` is called.
     */
    private fun setOrRemoveIfEmpty(key: String, value: String?, trim: Boolean) {
        if (value != null && (if (trim) value.trim { it <= ' ' }.isNotEmpty() else value.isNotEmpty())) {
            putAccountProperty(key, value)
        } else {
            removeAccountProperty(key)
        }
    }

    /**
     * Stores configuration properties held by this object into given `accountProperties`
     * map.
     *
     * protocolIconPath the path to the protocol icon is used
     * accountIconPath the path to the account icon if used
     * accountProperties output properties map
     */
    fun storeProperties(protocolIconPath: String?, accountIconPath: String?, accountProperties: MutableMap<String, String?>) {
        if (protocolIconPath != null) this.protocolIconPath = protocolIconPath
        if (accountIconPath != null) this.accountIconPath = accountIconPath

        // cmeng - mergeProperties mAccountProperties into accountProperties and later save accountProperties to database
        mergeProperties(mAccountProperties, accountProperties)

        // Removes encrypted password property, as it will be restored during account storage.
        accountProperties.remove("ENCRYPTED_PASSWORD")
    }

    /**
     * Gets default property value for given `key`.
     *
     * key the property key
     * @return default property value for given`key`
     */
    protected open fun getDefaultString(key: String): String? {
        return getDefaultStr(key)
    }

    open val contentValues: ContentValues?
        get() {
            val values = ContentValues()
            values.put(ACCOUNT_UUID, accountUuid)
            values.put(PROTOCOL, protocolName)
            values.put(USER_ID, mUserID)
            values.put(ACCOUNT_UID, accountUniqueID)
            synchronized(mKeys) { values.put(KEYS, mKeys.toString()) }
            return values
        }

    fun getKey(name: String?): String {
        synchronized(mKeys) { return mKeys.optString(name, "null") }
    }

    fun getKeyAsInt(name: String?, defaultValue: Int): Int {
        val key = getKey(name)
        return try {
            key.toInt() ?: defaultValue
        } catch (e: NumberFormatException) {
            defaultValue
        }
    }

    fun setKey(keyName: String, keyValue: String): Boolean {
        synchronized(mKeys) {
            return try {
                mKeys.put(keyName, keyValue)
                true
            } catch (e: JSONException) {
                false
            }
        }
    }

    fun unsetKey(key: String?): Boolean {
        synchronized(mKeys) { return mKeys.remove(key) != null }
    }

    companion object {
        /**
         * Table accountID columns
         */
        const val TABLE_NAME = "accountID"
        const val ACCOUNT_UUID = "accountUuid" // ACCOUNT_UUID_PREFIX + System.currentTimeMillis()
        const val PROTOCOL = "protocolName" // Default to Jabber
        const val USER_ID = "userID" // abc123@atalk.org i.e. BareJid
        const val ACCOUNT_UID = "accountUid" // jabber:abc123@atalk.org (uuid)
        const val KEYS = "keys"

        // Not use
        const val SERVICE_NAME = "serviceName" // domainPart of jid
        const val STATUS = "status"
        const val STATUS_MESSAGE = "statusMessage"

        /**
         * Table accountProperties columns
         */
        const val TBL_PROPERTIES = "accountProperties"

        // public static final String ACCOUNT_UID = "accountUuid";
        const val COLUMN_NAME = "Name"
        const val COLUMN_VALUE = "Value"
        const val PROTOCOL_DEFAULT = "'Jabber'"

        /**
         * The prefix of the account unique identifier.
         */
        const val ACCOUNT_UUID_PREFIX = "acc"
        private const val KEY_PGP_SIGNATURE = "pgp_signature"
        private const val KEY_PGP_ID = "pgp_id"
        const val DEFAULT_PORT = "5222"

        /**
         * The default properties common key prefix used in lib/atalk-defaults.properties which are
         * independent of protocol.
         */
        const val DEFAULT_PREFIX = "protocol."

        /**
         * Allows a specific set of account properties to override a given default protocol name (e.g.
         * account registration wizards which want to present a well-known protocol name associated
         * with the account that is different from the name of the effective protocol).
         *
         * Note: The logic of the SIP protocol implementation at the time of this writing modifies
         * `accountProperties` to contain the default protocol name if an override hasn't been
         * defined. Since the desire is to enable all account registration wizards to override the
         * protocol name, the current implementation places the specified `defaultProtocolName`
         * in a similar fashion.
         *
         * accountProperties a Map containing any other protocol and implementation specific
         * account initialization properties
         * defaultProtocolName the protocol name to be used in case `accountProperties`
         * doesn't provide an overriding value
         * @return the protocol name
         */
        private fun getOverriddenProtocolName(accountProperties: MutableMap<String, String?>, defaultProtocolName: String): String? {
            val key = ProtocolProviderFactory.PROTOCOL
            var protocolName = accountProperties[key]
            if (StringUtils.isEmpty(protocolName) && StringUtils.isNotEmpty(defaultProtocolName)) {
                protocolName = defaultProtocolName
                accountProperties[key] = protocolName
            }
            return protocolName
        }

        /**
         * Returns the password for the STUN server with the specified prefix.
         *
         * bundleContext the OSGi bundle context that we are currently running in.
         * accountID account ID
         * namePrefix name prefix
         * @return password or null if empty
         */
        fun loadStunPassword(bundleContext: BundleContext?, accountID: AccountID,
                namePrefix: String): String? {
            val providerFactory = ProtocolProviderFactory.getProtocolProviderFactory(bundleContext!!, accountID.systemProtocolName!!)
            val password: String?
            val className = providerFactory!!.javaClass.name
            val packageSourceName = className.substring(0, className.lastIndexOf('.'))
            val accountPrefix = ProtocolProviderFactory.findAccountPrefix(bundleContext, accountID, packageSourceName)
            val credentialsService = getService(bundleContext, CredentialsStorageService::class.java)
            try {
                password = credentialsService!!.loadPassword("$accountPrefix.$namePrefix")
            } catch (e: Exception) {
                return null
            }
            return password
        }

        /**
         * Gets default property value for given `key`.
         *
         * key the property key
         * @return default property value for given`key`
         */
        fun getDefaultStr(key: String): String? {
            return ProtocolProviderActivator.getConfigurationService()?.getString(DEFAULT_PREFIX + key)
        }

        /**
         * Copies all properties from `input` map to `output` map overwritten any value in output.
         *
         * input source properties map
         * output destination properties map
         */
        fun mergeProperties(input: Map<String, String?>, output: MutableMap<String, String?>) {
            for (key in input.keys) {
                output[key] = input[key]
            }
        }
        // *********************************************************
        /**
         * Create the new accountID based on two separate tables data i.e.
         * accountID based on given cursor and accountProperties table
         *
         * db aTalk SQLite Database
         * cursor AccountID table cursor for properties extraction
         * factory Account protocolProvider Factory
         * @return the new AccountID constructed
         */
        fun fromCursor(db: SQLiteDatabase, cursor_: Cursor, factory: ProtocolProviderFactory): AccountID {
            var cursor = cursor_
            val accountUuid = cursor.getString(cursor.getColumnIndexOrThrow(ACCOUNT_UUID))

            val accountProperties = Hashtable<String, String?>()
            accountProperties[ProtocolProviderFactory.ACCOUNT_UUID] = accountUuid
            accountProperties[ProtocolProviderFactory.PROTOCOL] = cursor.getString(cursor.getColumnIndexOrThrow(PROTOCOL))
            accountProperties[ProtocolProviderFactory.USER_ID] = cursor.getString(cursor.getColumnIndexOrThrow(USER_ID))
            accountProperties[ProtocolProviderFactory.ACCOUNT_UID] = cursor.getString(cursor.getColumnIndexOrThrow(ACCOUNT_UID))
            accountProperties[ProtocolProviderFactory.KEYS] = cursor.getString(cursor.getColumnIndexOrThrow(KEYS))

            // Retrieve the remaining account properties from table
            val args = arrayOf(accountUuid)
            cursor = db.query(TBL_PROPERTIES, null, "$ACCOUNT_UUID=?", args, null, null, null)
            val columnName = cursor.getColumnIndex("Name")
            val columnValue = cursor.getColumnIndex("Value")
            while (cursor.moveToNext()) {
                accountProperties[cursor.getString(columnName)] = cursor.getString(columnValue)
            }
            cursor.close()
            return factory.createAccount(accountProperties)
        }
    }
}