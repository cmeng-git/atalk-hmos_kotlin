/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.settings

import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.service.osgi.OSGiPreferenceActivity

/**
 * Provisioning preferences Settings.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ProvisioningSettings : OSGiPreferenceActivity() {
    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setMainTitle(R.string.plugin_provisioning_PROVISIONING)
        supportFragmentManager.beginTransaction().replace(android.R.id.content, MyPreferenceFragment()).commit()
    }

    class MyPreferenceFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
        /**
         * Username edit text
         */
        private var usernamePreference: EditTextPreference? = null

        /**
         * Password edit text
         */
        private var passwordPreference: EditTextPreference? = null

        /**
         * {@inheritDoc}
         */
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.provisioning_preferences, rootKey)

            // Load UUID
            val edtPref = findPreference<EditTextPreference>(P_KEY_UUID)!!
            edtPref.text = AndroidGUIActivator.configurationService.getString(edtPref.key)
            val cSS = AndroidGUIActivator.credentialsStorageService!!
            val password = cSS.loadPassword(P_KEY_PASS)
            val forgetPass = findPreference<Preference>(P_KEY_FORGET_PASS)!!
            val config = AndroidGUIActivator.configurationService
            // Enable clear credentials button if password exists
            if (StringUtils.isNotEmpty(password)) {
                forgetPass.isEnabled = true
            }
            // Forget password action handler
            forgetPass.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                askForgetPassword()
                false
            }

            // Initialize username and password fields
            usernamePreference = findPreference(P_KEY_USER)
            usernamePreference!!.text = config.getString(P_KEY_USER)
            passwordPreference = findPreference(P_KEY_PASS)
            passwordPreference!!.text = password
        }

        /**
         * Asks the user for confirmation of password clearing and eventually clears it.
         */
        private fun askForgetPassword() {
            val askForget = AlertDialog.Builder(context!!)
            askForget.setTitle(R.string.service_gui_REMOVE)
                    .setMessage(R.string.plugin_provisioning_REMOVE_CREDENTIALS_MESSAGE)
                    .setPositiveButton(R.string.service_gui_YES) { _: DialogInterface?, _: Int ->
                        AndroidGUIActivator.credentialsStorageService!!.removePassword(P_KEY_PASS)
                        AndroidGUIActivator.configurationService.removeProperty(P_KEY_USER)
                        usernamePreference!!.text = ""
                        passwordPreference!!.text = ""
                    }
                    .setNegativeButton(R.string.service_gui_NO) { dialog: DialogInterface, _: Int -> dialog.dismiss() }.show()
        }

        /**
         * {@inheritDoc}
         */
        override fun onResume() {
            super.onResume()
            preferenceManager.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)
        }

        /**
         * {@inheritDoc}
         */
        override fun onPause() {
            preferenceManager.sharedPreferences!!.unregisterOnSharedPreferenceChangeListener(this)
            super.onPause()
        }

        /**
         * {@inheritDoc}
         */
        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
            if (key == P_KEY_PROVISIONING_METHOD) {
                if ("NONE" == sharedPreferences.getString(P_KEY_PROVISIONING_METHOD, null)) {
                    AndroidGUIActivator.configurationService.setProperty(P_KEY_URL, null)
                }
            }
        }

        companion object {
            /**
             * Used preference keys
             */
            private const val P_KEY_PROVISIONING_METHOD = "plugin.provisioning.METHOD"
            private const val P_KEY_USER = "plugin.provisioning.auth.USERNAME"
            private const val P_KEY_PASS = "plugin.provisioning.auth"
            private const val P_KEY_FORGET_PASS = "pref.key.provisioning.forget_password"
            private const val P_KEY_UUID = "net.java.sip.communicator.UUID"
            private const val P_KEY_URL = "plugin.provisioning.URL"
        }
    }
}