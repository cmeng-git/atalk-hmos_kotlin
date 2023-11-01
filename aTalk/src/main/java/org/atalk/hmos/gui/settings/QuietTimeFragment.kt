/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.hmos.gui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import net.java.sip.communicator.util.ConfigurationUtils
import org.atalk.hmos.R
import org.atalk.hmos.gui.settings.util.SummaryMapper
import org.atalk.hmos.gui.util.PreferenceUtil
import org.atalk.service.osgi.OSGiPreferenceFragment

/**
 * The preferences fragment implements for QuietTime settings.
 *
 * @author Eng Chong Meng
 */
class QuietTimeFragment : OSGiPreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener, PreferenceFragmentCompat.OnPreferenceDisplayDialogCallback {
    private lateinit var mPreferenceScreen: PreferenceScreen
    private lateinit var shPrefs: SharedPreferences

    /**
     * Summary mapper used to display preferences values as summaries.
     */
    private val summaryMapper = SummaryMapper()
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the quiet time preferences from an XML resource
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.quiet_time_preferences, rootKey)
    }

    /**
     * {@inheritDoc}
     */
    override fun onResume() {
        super.onResume()
        setPrefTitle(R.string.title_pref_quiet_hours)
        mPreferenceScreen = preferenceScreen
        shPrefs = preferenceManager.sharedPreferences!!
        shPrefs.registerOnSharedPreferenceChangeListener(this)
        shPrefs.registerOnSharedPreferenceChangeListener(summaryMapper)
        initQuietTimePreferences()
    }

    /**
     * {@inheritDoc}
     */
    override fun onStop() {
        shPrefs.unregisterOnSharedPreferenceChangeListener(this)
        shPrefs.unregisterOnSharedPreferenceChangeListener(summaryMapper)
        super.onStop()
    }

    /**
     * Initializes notifications section
     */
    private fun initQuietTimePreferences() {
        // Quite hours enable
        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_QUIET_HOURS_ENABLE,
                ConfigurationUtils.isQuiteHoursEnable())
        findPreference<TimePreference>(P_KEY_QUIET_HOURS_START)!!.setTime(ConfigurationUtils.getQuiteHoursStart())
        findPreference<TimePreference>(P_KEY_QUIET_HOURS_END)!!.setTime(ConfigurationUtils.getQuiteHoursEnd())
    }

    /**
     * {@inheritDoc}
     */
    override fun onSharedPreferenceChanged(shPreferences: SharedPreferences, key: String) {
        when (key) {
            P_KEY_QUIET_HOURS_ENABLE -> ConfigurationUtils.setQuiteHoursEnable(shPreferences.getBoolean(P_KEY_QUIET_HOURS_ENABLE, true))
            P_KEY_QUIET_HOURS_START -> ConfigurationUtils.setQuiteHoursStart(shPreferences.getLong(P_KEY_QUIET_HOURS_START, TimePreference.DEFAULT_VALUE))
            P_KEY_QUIET_HOURS_END -> ConfigurationUtils.setQuiteHoursEnd(shPreferences.getLong(P_KEY_QUIET_HOURS_END, TimePreference.DEFAULT_VALUE))
        }
    }

    /**
     * Must override getCallbackFragment() to get PreferenceFragmentCompat to callback onPreferenceDisplayDialog();
     * else Cannot display dialog for an unknown Preference type: TimePreference. Make sure to implement
     * onPreferenceDisplayDialog() to handle displaying a custom dialog for this Preference.
     *
     * @return This fragment reference that implements OnPreferenceDisplayDialogCallback
     */
    override fun getCallbackFragment() = this

    /**
     * @param caller The fragment containing the preference requesting the dialog
     * @param pref The preference requesting the dialog
     *
     * @return `true` if the dialog creation has been handled
     */
    override fun onPreferenceDisplayDialog(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        if (pref is TimePreference) {
            val dialogFragment = TimePickerPreferenceDialog.newInstance(pref)
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(parentFragmentManager, DIALOG_FRAGMENT_TAG)
            return true
        }
        return false
    }

    companion object {
        // QuietTime
        const val P_KEY_QUIET_HOURS_ENABLE = "pref.key.quiet_hours_enable"
        const val P_KEY_QUIET_HOURS_START = "pref.key.quiet_hours_start"
        const val P_KEY_QUIET_HOURS_END = "pref.key.quiet_hours_end"
        private const val DIALOG_FRAGMENT_TAG = "TimePickerDialog"
    }
}