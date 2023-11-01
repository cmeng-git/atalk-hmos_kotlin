/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.settings

import android.content.SharedPreferences
import android.content.SharedPreferences.*
import android.os.Bundle
import androidx.preference.PreferenceScreen
import net.java.sip.communicator.plugin.otr.OtrActivator
import net.java.sip.communicator.util.UtilActivator
import org.atalk.hmos.R
import org.atalk.hmos.gui.util.PreferenceUtil
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.osgi.OSGiActivity
import org.atalk.service.osgi.OSGiPreferenceFragment

/**
 * Chat security settings screen with OTR preferences - modified for aTalk
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
     * The preferences fragment implements OTR settings.
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
            val otrPolicy = OtrActivator.scOtrEngine.globalPolicy!!
            val screen = preferenceScreen
            PreferenceUtil.setCheckboxVal(screen, P_KEY_CRYPTO_ENABLE, otrPolicy.enableManual)
            PreferenceUtil.setCheckboxVal(screen, P_KEY_OMEMO_KEY_BLIND_TRUST,
                    mConfig!!.getBoolean(ConfigurationService.PNAME_OMEMO_KEY_BLIND_TRUST, true))
            val shPrefs = preferenceManager.sharedPreferences!!

            // cmeng: remove unused preferences
            val mEditor = shPrefs.edit()
            mEditor.remove("pref.key.crypto.auto")
            mEditor.remove("pref.key.crypto.require")
            mEditor.apply()

            // cmeng: Purge all the unnecessary OTR implementations for aTalk - will be removed in future release
            mConfig!!.setProperty(AUTO_INIT_OTR_PROP, null)
            mConfig!!.setProperty(OTR_MANDATORY_PROP, null)
            shPrefs.registerOnSharedPreferenceChangeListener(this)
        }

        /**
         * {@inheritDoc}
         */
        override fun onStop() {
            val shPrefs = preferenceManager.sharedPreferences!!
            shPrefs.unregisterOnSharedPreferenceChangeListener(this)
            super.onStop()
        }

        /**
         * {@inheritDoc}
         */
        override fun onSharedPreferenceChanged(shPreferences: SharedPreferences, key: String) {
            if (key == P_KEY_CRYPTO_ENABLE) {
                val otrPolicy = OtrActivator.scOtrEngine.globalPolicy!!
                val isEnabled = shPreferences.getBoolean(P_KEY_CRYPTO_ENABLE, otrPolicy.enableManual)
                otrPolicy.enableManual = isEnabled
                OtrActivator.configService.setProperty(OtrActivator.OTR_DISABLED_PROP, java.lang.Boolean.toString(!isEnabled))

                // Store changes immediately
                OtrActivator.scOtrEngine.globalPolicy = otrPolicy
            } else if (key == P_KEY_OMEMO_KEY_BLIND_TRUST) {
                mConfig!!.setProperty(ConfigurationService.PNAME_OMEMO_KEY_BLIND_TRUST,
                        shPreferences.getBoolean(P_KEY_OMEMO_KEY_BLIND_TRUST, true))
            }
        }
    }

    companion object {
        // Preference mKeys
        private const val P_KEY_CRYPTO_ENABLE = "pref.key.crypto.enable"
        private const val AUTO_INIT_OTR_PROP = "otr.AUTO_INIT_PRIVATE_MESSAGING"

        /**
         * A property specifying whether private messaging should be made mandatory.
         */
        private const val OTR_MANDATORY_PROP = "otr.PRIVATE_MESSAGING_MANDATORY"

        // OMEMO Security section
        private const val P_KEY_OMEMO_KEY_BLIND_TRUST = "pref.key.omemo.key.blind.trust"
        private var mConfig: ConfigurationService? = null
    }
}