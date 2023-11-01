/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.atalk.hmos.gui.call.AndroidCallUtil
import org.atalk.service.osgi.OSGiActivity

/**
 * `Activity` implements aTalk global settings.
 *
 * @author Eng Chong Meng
 */
class SettingsActivity : OSGiActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // We do not allow opening settings if there is a call currently active
        if (AndroidCallUtil.checkCallInProgress(this)) return

        // Display the fragment as the android main content.
        supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(classLoader, pref.fragment!!)
        fragment.arguments = args
        fragment.setTargetFragment(caller, 0)

        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit()
        return true
    }
}