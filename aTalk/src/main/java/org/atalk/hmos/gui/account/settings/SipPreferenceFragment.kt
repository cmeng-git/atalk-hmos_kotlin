/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceManager
import net.java.sip.communicator.plugin.sipaccregwizz.AccountRegistrationImpl
import net.java.sip.communicator.plugin.sipaccregwizz.SIPAccountRegistrationActivator
import net.java.sip.communicator.service.certificate.CertificateConfigEntry
import net.java.sip.communicator.service.protocol.EncodingsRegistrationUtil
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.protocol.SecurityAccountRegistration
import net.java.sip.communicator.service.protocol.sip.SIPAccountRegistration
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.settings.util.SummaryMapper
import org.atalk.hmos.gui.settings.util.SummaryMapper.SummaryConverter
import timber.log.Timber

/**
 * The preferences edit fragment for SIP accounts.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class SipPreferenceFragment
/**
 * Creates new instance.
 */
    : AccountPreferenceFragment(R.xml.acc_sip_preferences) {
    /**
     * Returns SIP registration wizard.
     *
     * @return SIP registration wizard.
     */
    private val sipWizard: AccountRegistrationImpl? = null

    /**
     * Returns SIP account registration object.
     *
     * @return SIP account registration object.
     */
    private val accountRegistration: SIPAccountRegistration
        get() = sipWizard!!.registration

    /**
     * {@inheritDoc}
     */
    override val encodingsRegistration: EncodingsRegistrationUtil
        get() = accountRegistration.encodingsRegistration

    /**
     * {@inheritDoc}
     */
    override val securityRegistration: SecurityAccountRegistration
        get() = accountRegistration.getSecurityRegistration()

    /**
     * {@inheritDoc}
     */
    override fun onInitPreferences() {
        val registration = sipWizard!!.registration
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity as Context)
        val editor = preferences.edit()
        val account = accountID
        val userId = if (registration.serverAddress != null) account!!.mUserID else account!!.getAccountPropertyString(ProtocolProviderFactory.USER_ID)

        // User name and password
        val password = registration.password
        editor.putString(PREF_KEY_USER_ID, userId)
        editor.putString(PREF_KEY_PASSWORD, password)
        val storePass = registration.isRememberPassword
        editor.putBoolean(PREF_KEY_STORE_PASSWORD, storePass)
        editor.putString(PREF_KEY_DISPLAYNAME, registration.accountDisplayName)
        // Connection
        editor.putString(PREF_KEY_AUTH_NAME, registration.authorizationName)
        editor.putString(PREF_KEY_SERVER_PORT, registration.serverPort)
        editor.putString(PREF_KEY_SERVER_ADDRESS, registration.serverAddress)
        editor.putString(PREF_KEY_TLS_CERT_ID, registration.tlsClientCertificate)
        editor.putString(PREF_KEY_DTMF_METHOD, registration.dtmfMethod)
        editor.putString(PREF_KEY_PROXY_ADDRESS, registration.getProxy())
        editor.putString(PREF_KEY_PROXY_PORT, registration.proxyPort)
        editor.putString(PREF_KEY_PREFERRED_TRANSPORT, registration.getPreferredTransport())
        editor.putString(PREF_KEY_KEEP_ALIVE_METHOD, registration.getKeepAliveMethod())
        editor.putString(PREF_KEY_PING_INTERVAL, registration.pingInterval)
        editor.putBoolean(PREF_KEY_MWI_EN, registration.isMessageWaitingIndicationsEnabled())
        editor.putString(PREF_KEY_VOICE_MAIL_CHECK_URI, registration.getVoicemailCheckURI())
        editor.putString(PREF_KEY_VOICE_MAIL_URI, registration.getVoicemailCheckURI())
        // Presence
        editor.putBoolean(PREF_KEY_IS_PRESENCE_EN, registration.isEnablePresence())
        editor.putBoolean(PREF_KEY_FORCE_P2P, registration.isForceP2PMode())
        editor.putBoolean(PREF_KEY_PROXY_AUTO_CONFIG, registration.isProxyAutoConfigure())
        editor.putString(PREF_KEY_POLLING_PERIOD, registration.pingInterval)
        editor.putString(PREF_KEY_SUBSCRIPTION_PERIOD, registration.getSubscriptionExpiration())
        // Contact list
        // 0 - default contact list
        var cListTypeIdx = 0
        if (registration.isXCapEnable()) {
            cListTypeIdx = 1
        } else if (registration.isXiVOEnable()) {
            cListTypeIdx = 2
        }
        val cListType = aTalkApp.appResources.getStringArray(R.array.pref_sip_clist_type)[cListTypeIdx]
        editor.putString(PREF_KEY_CONTACT_LIST_TYPE, cListType)
        editor.putBoolean(PREF_KEY_CLIST_USE_SIP_CREDENTIALS, registration.isClistOptionUseSipCredentials())
        editor.putString(PREF_KEY_CLIST_SERVER_URI, registration.getClistOptionServerUri())
        editor.putString(PREF_KEY_CLIST_USER, registration.getClistOptionUser())
        editor.putString(PREF_KEY_CLIST_PASSWORD, registration.getClistOptionPassword())
        editor.apply()
    }

    /**
     * {@inheritDoc}
     */
    override fun onPreferencesCreated() {
        // Enable/disable contact list items on init
        updateContactListViews()
        val certList = ArrayList<String>()
        val cvs = SIPAccountRegistrationActivator.certificateService!!
        val certEntries = cvs.getClientAuthCertificateConfigs()
        certEntries.add(0, CertificateConfigEntry.CERT_NONE)
        for (e in certEntries) {
            certList.add(e.toString())
        }
        val accountID = accountID
        var currentCert = accountID!!.tlsClientCertificate
        if (!certList.contains(currentCert) && !isInitialized) {
            // The empty one
            currentCert = certList[0]
            preferenceManager.sharedPreferences!!.edit().putString(PREF_KEY_TLS_CERT_ID, currentCert).apply()
        }
        var entries = arrayOfNulls<String>(certList.size)
        entries = certList.toArray(entries)
        val certPreference = findPreference<ListPreference>(PREF_KEY_TLS_CERT_ID)!!
        certPreference.entries = entries
        certPreference.entryValues = entries
        if (!isInitialized) certPreference.value = currentCert
    }

    /**
     * {@inheritDoc}
     */
    override fun mapSummaries(summaryMapper: SummaryMapper) {
        val emptyStr = emptyPreferenceStr

        // User section
        summaryMapper.includePreference(findPreference(PREF_KEY_USER_ID), emptyStr)
        summaryMapper.includePreference(findPreference(PREF_KEY_PASSWORD), emptyStr, SummaryMapper.PasswordMask())
        summaryMapper.includePreference(findPreference(PREF_KEY_DISPLAYNAME), emptyStr)

        // Connection -> General
        summaryMapper.includePreference(findPreference(PREF_KEY_SERVER_ADDRESS), emptyStr)
        summaryMapper.includePreference(findPreference(PREF_KEY_SERVER_PORT), emptyStr)
        summaryMapper.includePreference(findPreference(PREF_KEY_AUTH_NAME), emptyStr)
        summaryMapper.includePreference(findPreference(PREF_KEY_TLS_CERT_ID), emptyStr)
        summaryMapper.includePreference(findPreference(PREF_KEY_DTMF_METHOD), emptyStr, object : SummaryConverter {
            override fun convertToSummary(input: String?): String {
                val lp = findPreference<ListPreference>(PREF_KEY_DTMF_METHOD)
                return lp!!.entry.toString()
            }
        })
        // Connection -> Keep alive
        summaryMapper.includePreference(findPreference(PREF_KEY_KEEP_ALIVE_METHOD), emptyStr)
        summaryMapper.includePreference(findPreference(PREF_KEY_PING_INTERVAL), emptyStr)

        // Connection -> Voicemail
        summaryMapper.includePreference(findPreference(PREF_KEY_VOICE_MAIL_URI), emptyStr)
        summaryMapper.includePreference(findPreference(PREF_KEY_VOICE_MAIL_CHECK_URI), emptyStr)

        // Proxy options
        summaryMapper.includePreference(findPreference(PREF_KEY_PROXY_ADDRESS), emptyStr)
        summaryMapper.includePreference(findPreference(PREF_KEY_PROXY_PORT), emptyStr)
        summaryMapper.includePreference(findPreference(PREF_KEY_PREFERRED_TRANSPORT), emptyStr)

        // Presence -> Presence options
        summaryMapper.includePreference(findPreference(PREF_KEY_POLLING_PERIOD), emptyStr)
        summaryMapper.includePreference(findPreference(PREF_KEY_SUBSCRIPTION_PERIOD), emptyStr)

        // Presence -> Contact list options
        summaryMapper.includePreference(findPreference(PREF_KEY_CONTACT_LIST_TYPE), emptyStr)
        summaryMapper.includePreference(findPreference(PREF_KEY_CLIST_SERVER_URI), emptyStr)
        summaryMapper.includePreference(findPreference(PREF_KEY_CLIST_USER), emptyStr)
        summaryMapper.includePreference(findPreference(PREF_KEY_CLIST_PASSWORD), emptyStr, SummaryMapper.PasswordMask())
    }

    /**
     * {@inheritDoc}
     */
    override fun onSharedPreferenceChanged(shPrefs: SharedPreferences, key: String) {
        super.onSharedPreferenceChanged(shPrefs, key)
        val reg = accountRegistration
        when (key) {
            PREF_KEY_PASSWORD -> reg.password = shPrefs.getString(PREF_KEY_PASSWORD, null)
            PREF_KEY_DISPLAYNAME -> reg.accountDisplayName = shPrefs.getString(PREF_KEY_DISPLAYNAME, null)
            PREF_KEY_STORE_PASSWORD -> reg.isRememberPassword = shPrefs.getBoolean(PREF_KEY_STORE_PASSWORD, true)
            PREF_KEY_SERVER_ADDRESS -> {}
            PREF_KEY_USER_ID -> {}
            PREF_KEY_SERVER_PORT -> reg.serverPort = shPrefs.getString(PREF_KEY_SERVER_PORT, null)
            PREF_KEY_AUTH_NAME -> reg.authorizationName = shPrefs.getString(PREF_KEY_AUTH_NAME, null)
            PREF_KEY_TLS_CERT_ID -> reg.tlsClientCertificate = shPrefs.getString(PREF_KEY_TLS_CERT_ID, null)
            PREF_KEY_PROXY_AUTO_CONFIG -> reg.setProxyAutoConfigure(shPrefs.getBoolean(PREF_KEY_PROXY_AUTO_CONFIG, true))
            PREF_KEY_PROXY_ADDRESS -> reg.setProxy(shPrefs.getString(PREF_KEY_PROXY_ADDRESS, null))
            PREF_KEY_PROXY_PORT -> reg.proxyPort = shPrefs.getString(PREF_KEY_PROXY_PORT, null)
            PREF_KEY_PREFERRED_TRANSPORT -> reg.setPreferredTransport(shPrefs.getString(PREF_KEY_PREFERRED_TRANSPORT, null)!!)
            PREF_KEY_KEEP_ALIVE_METHOD -> reg.setKeepAliveMethod(shPrefs.getString(PREF_KEY_KEEP_ALIVE_METHOD, null)!!)
            PREF_KEY_PING_INTERVAL -> reg.pingInterval = shPrefs.getString(PREF_KEY_PING_INTERVAL, null)
            PREF_KEY_MWI_EN -> reg.setMessageWaitingIndications(shPrefs.getBoolean(PREF_KEY_MWI_EN, true))
            PREF_KEY_VOICE_MAIL_URI -> reg.setVoicemailURI(shPrefs.getString(PREF_KEY_VOICE_MAIL_URI, null))
            PREF_KEY_VOICE_MAIL_CHECK_URI -> reg.setVoicemailCheckURI(shPrefs.getString(PREF_KEY_VOICE_MAIL_CHECK_URI, null)!!)
            PREF_KEY_DTMF_METHOD -> reg.dtmfMethod = shPrefs.getString(PREF_KEY_DTMF_METHOD, null)
            PREF_KEY_IS_PRESENCE_EN -> reg.setEnablePresence(shPrefs.getBoolean(PREF_KEY_IS_PRESENCE_EN, true))
            PREF_KEY_FORCE_P2P -> reg.setForceP2PMode(shPrefs.getBoolean(PREF_KEY_FORCE_P2P, false))
            PREF_KEY_POLLING_PERIOD -> reg.setPollingPeriod(shPrefs.getString(PREF_KEY_POLLING_PERIOD, null)!!)
            PREF_KEY_SUBSCRIPTION_PERIOD -> reg.setSubscriptionExpiration(shPrefs.getString(PREF_KEY_SUBSCRIPTION_PERIOD, null)!!)
            PREF_KEY_CONTACT_LIST_TYPE -> {
                updateContactListViews()
                val lp = findPreference<ListPreference>(PREF_KEY_CONTACT_LIST_TYPE)!!
                val cListTypeIdx = lp.findIndexOfValue(lp.value)
                sipWizard!!.registration.setXCapEnable(cListTypeIdx == 1)
                sipWizard.registration.setXiVOEnable(cListTypeIdx == 2)
            }
            PREF_KEY_CLIST_SERVER_URI -> reg.setClistOptionServerUri(shPrefs.getString(PREF_KEY_CLIST_SERVER_URI, null))
            PREF_KEY_CLIST_USE_SIP_CREDENTIALS -> reg.setClistOptionUseSipCredentials(shPrefs.getBoolean(PREF_KEY_CLIST_USE_SIP_CREDENTIALS, true))
            PREF_KEY_CLIST_USER -> reg.setClistOptionUser(shPrefs.getString(PREF_KEY_CLIST_USER, null))
            PREF_KEY_CLIST_PASSWORD -> reg.setClistOptionPassword(shPrefs.getString(PREF_KEY_CLIST_PASSWORD, null))
        }
    }

    /**
     * Update widgets responsible for contact list preferences
     */
    private fun updateContactListViews() {
        val clistTypePref = findPreference<ListPreference>(PREF_KEY_CONTACT_LIST_TYPE)!!
        val enable = clistTypePref.findIndexOfValue(clistTypePref.value) != 0
        findPreference<CheckBoxPreference>(PREF_KEY_CLIST_SERVER_URI)!!.isEnabled = enable
        findPreference<CheckBoxPreference>(PREF_KEY_CLIST_USE_SIP_CREDENTIALS)!!.isEnabled = enable
        findPreference<CheckBoxPreference>(PREF_KEY_CLIST_USER)!!.isEnabled = enable
        findPreference<CheckBoxPreference>(PREF_KEY_CLIST_PASSWORD)!!.isEnabled = enable
    }

    /**
     * {@inheritDoc}
     */
    override fun doCommitChanges() {
        try {
            val sipAccReg = accountRegistration
            val sipWizard = sipWizard
            sipWizard!!.isModification = true
            sipWizard.signin(sipAccReg.getId()!!, sipAccReg.password, sipAccReg.accountProperties)
        } catch (e: OperationFailedException) {
            Timber.e(e, "Failed to store account modifications: %s", e.localizedMessage)
        }
    }

    companion object {
        private const val PREF_KEY_USER_ID = "pref_key_user_id"
        private const val PREF_KEY_SERVER_ADDRESS = "pref_key_server_address"
        private const val PREF_KEY_AUTH_NAME = "pref_key_auth_name"

        /*
     * private static final String PREF_KEY_DEFAULT_DOMAIN = "pref_key_default_domain";
     */
        private const val PREF_KEY_TLS_CERT_ID = "pref_key_client_tls_cert"
        private const val PREF_KEY_PROXY_AUTO_CONFIG = "pref_key_proxy_auto_config"
        private const val PREF_KEY_SERVER_PORT = "pref_key_server_port"
        private const val PREF_KEY_PROXY_ADDRESS = "pref_key_ProxyAddress"
        private const val PREF_KEY_PREFERRED_TRANSPORT = "pref_key_preferred_transport"
        private const val PREF_KEY_PROXY_PORT = "pref_key_ProxyPort"
        private const val PREF_KEY_IS_PRESENCE_EN = "pref_key_is_presence_enabled"
        private const val PREF_KEY_FORCE_P2P = "pref_key_force_p2p"
        private const val PREF_KEY_POLLING_PERIOD = "pref_key_polling_period"
        private const val PREF_KEY_SUBSCRIPTION_PERIOD = "pref_key_subscription_period"
        private const val PREF_KEY_KEEP_ALIVE_METHOD = "pref_key_keep_alive_method"
        private const val PREF_KEY_PING_INTERVAL = "pref_key_ping_interval"
        private const val PREF_KEY_DTMF_METHOD = "pref_key_dtmf_method"
        private const val PREF_KEY_MWI_EN = "pref_key_mwi_enabled"
        private const val PREF_KEY_VOICE_MAIL_URI = "pref_key_voicemail_uri"
        private const val PREF_KEY_VOICE_MAIL_CHECK_URI = "pref_key_voicemail_check_uri"
        private const val PREF_KEY_CONTACT_LIST_TYPE = "pref_key_contact_list_type"
        private const val PREF_KEY_CLIST_SERVER_URI = "pref_key_clist_server_uri"
        private const val PREF_KEY_CLIST_USE_SIP_CREDENTIALS = "pref_key_clist_use_sip_credentials"
        private const val PREF_KEY_CLIST_USER = "pref_key_clist_user"
        private const val PREF_KEY_CLIST_PASSWORD = "pref_key_clist_password"
        private const val PREF_KEY_DISPLAYNAME = "pref_key_display_name"
        private const val PREF_KEY_PASSWORD = "pref_key_password"
        private const val PREF_KEY_STORE_PASSWORD = "pref_key_store_password"
    }
}