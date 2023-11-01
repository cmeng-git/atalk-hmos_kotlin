/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.util

import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen

/**
 * Utility class exposing methods to operate on `Preference` subclasses.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
object PreferenceUtil {
    /**
     * Sets the `CheckBoxPreference` "checked" property.
     *
     * @param screen the `PreferenceScreen` containing the `CheckBoxPreference` we want to edit.
     * @param prefKey preference key id from `R.string`.
     * @param isChecked the value we want to set to the "checked" property of `CheckBoxPreference`.
     */
    fun setCheckboxVal(screen: PreferenceScreen, prefKey: String?, isChecked: Boolean) {
        val cbPref = screen.findPreference<CheckBoxPreference>(prefKey!!)
        cbPref!!.isChecked = isChecked
    }

    /**
     * Sets the text of `EditTextPreference` identified by given preference key string.
     *
     * @param screen the `PreferenceScreen` containing the `EditTextPreference` we want to edit.
     * @param prefKey preference key id from `R.string`.
     * @param txtValue the text value we want to set on `EditTextPreference`
     */
    fun setEditTextVal(screen: PreferenceScreen, prefKey: String?, txtValue: String?) {
        val cbPref = screen.findPreference<EditTextPreference>(prefKey!!)
        cbPref!!.text = txtValue
    }

    /**
     * Sets the value of `ListPreference` identified by given preference key string.
     *
     * @param screen the `PreferenceScreen` containing the `ListPreference` we want to edit.
     * @param prefKey preference key id from `R.string`.
     * @param value the value we want to set on `ListPreference`
     */
    fun setListVal(screen: PreferenceScreen, prefKey: String?, value: String?) {
        val lstPref = screen.findPreference<ListPreference>(prefKey!!)
        lstPref!!.value = value
    }
}