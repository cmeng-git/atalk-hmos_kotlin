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
import android.content.res.TypedArray
import android.text.format.DateFormat
import android.util.AttributeSet
import androidx.preference.DialogPreference
import androidx.preference.Preference
import java.util.*

/**
 * This class is used in our preference where user can pick a quiet time for notifications not to appear.
 * Specifically, this class is responsible for saving/retrieving preference data.
 *
 * @author Eng Chong Meng
 */
class TimePreference(context: Context?, attrs: AttributeSet?) : DialogPreference(context!!, attrs, 0), Preference.OnPreferenceChangeListener {
    init {
        this.onPreferenceChangeListener = this
    }

    fun setTime(time: Long) {
        persistLong(time)
        notifyDependencyChange(shouldDisableDependents())
        notifyChanged()
        updateSummary(time)
    }

    private fun updateSummary(time: Long) {
        val dateFormat = DateFormat.getTimeFormat(context)
        val date = minutesToCalender(time).time
        summary = dateFormat.format(date.time)
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        return a.getInteger(index, 0)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        val time = if (defaultValue is Long) defaultValue else DEFAULT_VALUE
        setTime(time)
    }

    /**
     * get the TimePreference persistent value the timePicker value update
     */
    val persistedValue: Long
        get() = getPersistedLong(DEFAULT_VALUE)

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        (preference as TimePreference).updateSummary(newValue as Long)
        return true
    }

    companion object {
        const val DEFAULT_VALUE = 0L
        private fun minutesToCalender(time: Long): Calendar {
            val c = Calendar.getInstance()
            c[Calendar.HOUR_OF_DAY] = (time % (24 * 60) / 60).toInt()
            c[Calendar.MINUTE] = (time % (24 * 60) % 60).toInt()
            return c
        }

        fun minutesToTimestamp(time: Long): Long {
            return minutesToCalender(time).timeInMillis
        }
    }
}