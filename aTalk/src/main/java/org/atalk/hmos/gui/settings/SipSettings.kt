/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.settings

import android.os.*
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import net.java.sip.communicator.util.ConfigurationUtils
import org.atalk.hmos.R
import java.util.*

/**
 * SIP protocol settings screen.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class SipSettings : CodecSettingsActivity() {
    override val preferencesXmlId: Int
        get() = R.xml.sip_preferences

    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, MyPreferenceFragment()).commit()
    }

    inner class MyPreferenceFragment : PreferenceFragmentCompat() {
        /**
         * {@inheritDoc}
         */
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreate(savedInstanceState)

            // Create supported protocols checkboxes
            val protocols = findPreference<PreferenceCategory>(P_KEY_SIP_SSL_PROTOCOL)
            val configuredProtocols = ConfigurationUtils.enabledSslProtocols.contentToString()
            for (protocol in ConfigurationUtils.availableSslProtocols) {
                val cbPRef = CheckBoxPreference(context!!)
                cbPRef.title = protocol
                cbPRef.isChecked = configuredProtocols.contains(protocol)
                protocols!!.addPreference(cbPRef)
            }
        }

        /**
         * {@inheritDoc}
         */
        override fun onDestroy() {
            super.onDestroy()

            // Find ssl protocol checkboxes and commit changes
            val protocols = findPreference<PreferenceCategory>(P_KEY_SIP_SSL_PROTOCOL)
            val count = protocols!!.preferenceCount
            val enabledSslProtocols = ArrayList<String>(count)
            for (i in 0 until count) {
                val protoPref = protocols.getPreference(i) as CheckBoxPreference
                if (protoPref.isChecked) enabledSslProtocols.add(protoPref.title.toString())
            }
            ConfigurationUtils.setEnabledSslProtocols(enabledSslProtocols.toArray(arrayOf<String>()))
        }
    }

    companion object {
        private const val P_KEY_SIP_SSL_PROTOCOL = "pref.cat.sip.ssl_protocols"
    }
}