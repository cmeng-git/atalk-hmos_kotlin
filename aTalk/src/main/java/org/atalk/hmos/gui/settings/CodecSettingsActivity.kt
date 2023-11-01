/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.settings

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import org.atalk.hmos.R
import org.atalk.service.osgi.OSGiPreferenceActivity

/**
 * Base class for settings screens which only adds preferences from XML resource.
 * By default preference resource id is obtained from `Activity` meta-data,
 * resource key: "androidx.preference".
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class CodecSettingsActivity : OSGiPreferenceActivity() {// Cant' find custom preference classes using:
    // addPreferencesFromIntent(getActivity().getIntent());
    /**
     * Returns preference XML resource ID.
     *
     * @return preference XML resource ID.
     */
    protected open val preferencesXmlId: Int
        get() =// Cant' find custom preference classes using:
                // addPreferencesFromIntent(getActivity().getIntent());
            try {
                val app = packageManager.getActivityInfo(getComponentName(), PackageManager.GET_META_DATA)
                if (app.name.contains("Opus")) setMainTitle(R.string.service_gui_settings_OPUS) else setMainTitle(R.string.service_gui_settings_SILK)
                app.metaData.getInt("android.preference")
            } catch (e: PackageManager.NameNotFoundException) {
                throw RuntimeException(e)
            }

    /**
     * {@inheritDoc}
     */
    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction().replace(android.R.id.content, MyPreferenceFragment(preferencesXmlId)).commit()
    }

    class MyPreferenceFragment(private val mPreferResId: Int) : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(mPreferResId, rootKey)
        }
    }
}