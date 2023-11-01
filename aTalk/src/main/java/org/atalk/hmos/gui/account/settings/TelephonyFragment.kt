/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account.settings

import android.content.SharedPreferences
import android.content.SharedPreferences.*
import android.os.Bundle
import androidx.preference.Preference
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration
import net.java.sip.communicator.util.account.AccountUtils
import org.atalk.hmos.R
import org.atalk.hmos.gui.settings.util.SummaryMapper
import org.atalk.service.osgi.OSGiPreferenceFragment
import timber.log.Timber

/**
 * The preferences fragment implements for Telephony settings.
 *
 * @author Eng Chong Meng
 */
class TelephonyFragment : OSGiPreferenceFragment(), OnSharedPreferenceChangeListener {
    private lateinit var shPrefs: SharedPreferences

    /**
     * Summary mapper used to display preferences values as summaries.
     */
    private val summaryMapper = SummaryMapper()

    /**
     * {@inheritDoc}
     */
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.telephony_preference, rootKey)
        setPrefTitle(R.string.service_gui_JBR_TELEPHONY)
        val accountID = arguments!!.getString(AccountPreferenceFragment.EXTRA_ACCOUNT_ID)
        val account = AccountUtils.getAccountIDForUID(accountID!!)
        val pps = AccountUtils.getRegisteredProviderForAccount(account)
        if (pps == null) {
            Timber.w("No protocol provider registered for %s", account)
            return
        }
        jbrReg = JabberPreferenceFragment.jbrReg
        shPrefs = preferenceManager.sharedPreferences!!
        shPrefs.registerOnSharedPreferenceChangeListener(this)
        shPrefs.registerOnSharedPreferenceChangeListener(summaryMapper)
        initPreferences()
        mapSummaries(summaryMapper)
    }

    /**
     * {@inheritDoc}
     */
    private fun initPreferences() {
        val editor = shPrefs.edit()

        // Telephony
        editor.putBoolean(P_KEY_CALLING_DISABLED, jbrReg.isJingleDisabled())
        editor.putString(P_KEY_OVERRIDE_PHONE_SUFFIX, jbrReg.getOverridePhoneSuffix())
        editor.putString(P_KEY_TEL_BYPASS_GTALK_CAPS, jbrReg.getTelephonyDomainBypassCaps())
        editor.apply()
    }

    /**
     * {@inheritDoc}
     */
    private fun mapSummaries(summaryMapper: SummaryMapper) {
        val emptyStr = getString(R.string.service_gui_SETTINGS_NOT_SET)

        // Telephony
        summaryMapper.includePreference(findPreference(P_KEY_OVERRIDE_PHONE_SUFFIX), emptyStr)
        summaryMapper.includePreference(findPreference(P_KEY_TEL_BYPASS_GTALK_CAPS), emptyStr)
    }

    /**
     * {@inheritDoc}
     */
    override fun onSharedPreferenceChanged(shPreferences: SharedPreferences, key: String) {
        // Check to ensure a valid key before proceed
        if (findPreference<Preference>(key) == null) return
        AccountPreferenceFragment.uncommittedChanges = true
        if (key == P_KEY_CALLING_DISABLED) {
            jbrReg.setDisableJingle(shPrefs.getBoolean(P_KEY_CALLING_DISABLED, false))
        } else if (key == P_KEY_OVERRIDE_PHONE_SUFFIX) {
            jbrReg.setOverridePhoneSuffix(shPrefs.getString(P_KEY_OVERRIDE_PHONE_SUFFIX, null))
        } else if (key == P_KEY_TEL_BYPASS_GTALK_CAPS) {
            jbrReg.setTelephonyDomainBypassCaps(shPrefs.getString(P_KEY_TEL_BYPASS_GTALK_CAPS, null))
        }
    }

    companion object {
        // Telephony
        private const val P_KEY_CALLING_DISABLED = "pref_key_calling_disabled"
        private const val P_KEY_OVERRIDE_PHONE_SUFFIX = "pref_key_override_phone_suffix"
        private const val P_KEY_TEL_BYPASS_GTALK_CAPS = "pref_key_tele_bypass_gtalk_caps"

        /*
     * A new instance of AccountID and is not the same as accountID.
     * Defined as static, otherwise it may get clear onActivityResult - on some android devices
     */
        private lateinit var jbrReg: JabberAccountRegistration
    }
}