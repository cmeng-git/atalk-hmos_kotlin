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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location

/**
 * GeoLocation broadcast receiver implementation.
 *
 * @author Eng Chong Meng
 */
class LocationBroadcastReceiver(private val geoLocationListener: GeoLocationListener) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == GeoConstants.INTENT_LOCATION_RECEIVED) {
            val location = intent.getParcelableExtra<Location>(GeoIntentKey.LOCATION)
            val locAddress = intent.getStringExtra(GeoIntentKey.ADDRESS)
            geoLocationListener.onLocationReceived(location, locAddress)
        } else if (GeoConstants.INTENT_NO_LOCATION_RECEIVED == intent.action) {
            geoLocationListener.onLocationReceivedNone()
        }
    }
}