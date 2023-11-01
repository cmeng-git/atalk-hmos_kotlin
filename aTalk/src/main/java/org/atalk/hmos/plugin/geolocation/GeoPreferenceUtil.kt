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
package org.atalk.hmos.plugin.geolocation

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.text.TextUtils

/**
 * Class implementation to save/retrieve last known GeoLocation
 *
 * @author Eng Chong Meng
 */
class GeoPreferenceUtil private constructor(context: Context) {
    private val mPreferences: SharedPreferences

    init {
        mPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    val lastKnownLocation: Location?
        get() {
            val locationString = mPreferences.getString(LAST_KNOWN_LOCATION, null)
            return if (TextUtils.isEmpty(locationString)) null else {
                val latLong = locationString!!.split(",")
                val location = Location(GPS)
                location.latitude = latLong[0].toDouble()
                location.longitude = latLong[1].toDouble()
                location.altitude = if (latLong.size == 3) latLong[2].toDouble() else 0.0
                location
            }
        }

    fun saveLastKnownLocation(location: Location) {
        val geoLocation = location.latitude.toString() + "," + location.longitude + "," + location.altitude
        mPreferences.edit().putString(LAST_KNOWN_LOCATION, geoLocation).apply()
    }

    companion object {
        private const val LAST_KNOWN_LOCATION = "last_known_location"
        private const val GPS = "GPS"
        private const val PREF_NAME = "geolocation"
        private var instance: GeoPreferenceUtil? = null
        fun getInstance(context: Context): GeoPreferenceUtil? {
            if (instance == null) {
                instance = GeoPreferenceUtil(context.applicationContext)
            }
            return instance
        }
    }
}