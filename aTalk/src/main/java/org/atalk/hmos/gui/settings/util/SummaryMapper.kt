/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.settings.util

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.preference.ListPreference
import androidx.preference.Preference

/**
 * The class can be used to set [Preference] value as its summary text. Optionally the empty string can be
 * provided that will be used when value is `null` or empty `String`. To make it work it has to be
 * registered to the [SharedPreferences] instance containing preferences we want to handle.
 * Single instance can map multiple [Preference] at one time.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class SummaryMapper : OnSharedPreferenceChangeListener {
    /**
     * The key to [androidx.preference.Preference] mapping
     */
    private val mappedPreferences = HashMap<String, Preference>()

    /**
     * Mapping containing optional [SummaryConverter] that can provide custom operation on
     * the value before it is applied as a summary.
     */
    private val convertersMap = HashMap<String, SummaryConverter>()

    /**
     * Mapping containing empty string definitions
     */
    private val emptyStrMap = HashMap<String, String>()
    /**
     * Includes the [Preference] into summary mapping.
     *
     * @param pref the [Preference] to be included
     * @param empty optional empty String that will be set when the `Preference` value is `null` or empty
     * @param converter optional [SummaryConverter]
     * @see SummaryMapper
     */
    /**
     * Overload method for [.includePreference]
     *
     * @see .includePreference
     */
    @JvmOverloads
    fun includePreference(pref: Preference?, empty: String, converter: SummaryConverter? = null) {
        if (pref == null) throw NullPointerException("The preference cannot be null")
        val key = pref.key
        mappedPreferences[key] = pref
        emptyStrMap[key] = empty
        if (converter != null) convertersMap[pref.key] = converter
        setSummary(pref.sharedPreferences, pref)
    }

    /**
     * Triggers summary update on all registered `Preference`s.
     */
    fun updatePreferences() {
        for (pref in mappedPreferences.values) {
            setSummary(pref.sharedPreferences, pref)
        }
    }

    /**
     * Sets the summary basing on actual [Preference] value
     *
     * @param sharedPrefs the [SharedPreferences] that manages the `preference`
     * @param preference Android Preference
     */
    private fun setSummary(sharedPrefs: SharedPreferences?, preference: Preference) {
        val key = preference.key
        var value = sharedPrefs!!.getString(key, "")

        // Map entry instead of value for ListPreference
        if (preference is ListPreference) {
            val entry = preference.entry
            value = entry?.toString() ?: ""
        }
        if (!value!!.isEmpty()) {
            val converter = convertersMap[key]
            if (converter != null) value = converter.convertToSummary(value)
        } else {
            value = emptyStrMap[key]
        }
        preference.summary = value
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        val pref = mappedPreferences[key]
        pref?.let { setSummary(sharedPreferences, it) }
    }

    /**
     * The interface is used to provide custom value into summary conversion.
     */
    interface SummaryConverter {
        /**
         * The method shall return summary text for given `input` value.
         *
         * @param input [Preference] value as a `String`
         * @return output summary value
         */
        fun convertToSummary(input: String?): String?
    }

    /**
     * Class is used for password preferences to display text as "*".
     */
    class PasswordMask : SummaryConverter {
        override fun convertToSummary(input: String?): String? {
            return input!!.replace("(?s).".toRegex(), "*")
        }
    }
}