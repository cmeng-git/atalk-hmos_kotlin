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

import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.TimePicker
import androidx.preference.PreferenceDialogFragmentCompat

/**
 * This module is for managing the visual aspect of the dialog TimePreference UI display.
 * This is where we will actually create and display the TimePicker for user selection
 *
 * @author Eng Chong Meng
 */
class TimePickerPreferenceDialog : PreferenceDialogFragmentCompat() {
    private var timePicker: TimePicker? = null

    /**
     * Generate the TimePicker to be displayed
     * @param context Context
     *
     * @return a reference copy of the TimePicker
     */
    override fun onCreateDialogView(context: Context): View? {
        timePicker = TimePicker(context)
        timePicker!!.setIs24HourView(DateFormat.is24HourFormat(getContext()))
        return timePicker
    }

    /**
     * Get the TimePreference to set that value into the TimePicker.
     * @param v View
     */
    override fun onBindDialogView(v: View) {
        super.onBindDialogView(v)
        val pref = preference as TimePreference
        val time = pref.persistedValue
        timePicker!!.currentHour = (time % (24 * 60) / 60).toInt()
        timePicker!!.currentMinute = (time % (24 * 60) % 60).toInt()
    }

    /**
     * Save the value selected by the user after clicking the positive button.
     *
     * @param positiveResult true if changed
     */
    override fun onDialogClosed(positiveResult: Boolean) {
        // Save the user changed settings
        if (positiveResult) {
            val pref = preference as TimePreference
            pref.setTime((timePicker!!.currentHour * 60 + timePicker!!.currentMinute).toLong())
        }
    }

    companion object {
        const val ARG_KEY = "key"
        fun newInstance(pref: TimePreference): TimePickerPreferenceDialog {
            val dialogFragment = TimePickerPreferenceDialog()
            val args = Bundle(1)
            args.putString(ARG_KEY, pref.key)
            dialogFragment.arguments = args
            return dialogFragment
        }
    }
}