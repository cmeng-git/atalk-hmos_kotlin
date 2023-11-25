/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.settings

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import net.java.sip.communicator.util.UtilActivator
import org.atalk.hmos.R
import org.atalk.hmos.gui.util.PreferenceUtil
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.osgi.OSGiActivity
import org.atalk.service.osgi.OSGiPreferenceFragment

/**
 * Chat security settings screen with omemo preferences - modified for aTalk
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ChatSecuritySettings : OSGiActivity() {
    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            // Display the fragment as the main content.
            supportFragmentManager.beginTransaction().replace(android.R.id.content, SettingsFragment()).commit()
        }
        setMainTitle(R.string.service_gui_settings_MESSAGING_SECURITY_TITLE)
    }

    /**
     * The preferences fragment implements chat security settings.
     */
    class SettingsFragment : OSGiPreferenceFragment(), OnSharedPreferenceChangeListener {
        /**
         * {@inheritDoc}
         */
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreatePreferences(savedInstanceState, rootKey)
            addPreferencesFromResource(R.xml.security_preferences)
        }

        /**
         * {@inheritDoc}
         */
        override fun onStart() {
            super.onStart()
            mConfig = UtilActivator.configurationService
            val screen = preferenceScreen
            PreferenceUtil.setCheckboxVal(screen, P_KEY_OMEMO_KEY_BLIND_TRUST,
                mConfig!!.getBoolean(ConfigurationService.PNAME_OMEMO_KEY_BLIND_TRUST, true))

            val shPrefs = preferenceManager.sharedPreferences
            shPrefs?.registerOnSharedPreferenceChangeListener(this)
        }

        /**
         * {@inheritDoc}
         */
        override fun onStop() {
            val shPrefs = preferenceManager.sharedPreferences
            shPrefs?.unregisterOnSharedPreferenceChangeListener(this)
            super.onStop()
        }

        /**
         * {@inheritDoc}
         */
        override fun onSharedPreferenceChanged(shPreferences: SharedPreferences, key: String) {
            if (key == P_KEY_OMEMO_KEY_BLIND_TRUST) {
                mConfig!!.setProperty(ConfigurationService.PNAME_OMEMO_KEY_BLIND_TRUST,
                    shPreferences.getBoolean(P_KEY_OMEMO_KEY_BLIND_TRUST, true))
            }
        }
    }

    companion object {
        // OMEMO Security section
        private const val P_KEY_OMEMO_KEY_BLIND_TRUST = "pref.key.omemo.key.blind.trust"
        private var mConfig: ConfigurationService? = null
    }
}