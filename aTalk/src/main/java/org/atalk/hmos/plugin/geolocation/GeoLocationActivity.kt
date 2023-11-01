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

import android.app.Activity
import android.content.Intent
import android.location.Location
import org.atalk.hmos.aTalkApp

/**
 * OSM class for displaying map view
 *
 * @author Eng Chong Meng
 */
class GeoLocationActivity : GeoLocationBase() {
    private var mSVP: OsmActivity? = null
    override fun onResume() {
        super.onResume()
        mSVP = null
    }

    override fun showStreetMap(location: Location?) {
        if (!mSVPStarted) {
            mSVPStarted = true
            val intent = Intent(this, OsmActivity::class.java)
            intent.putExtra(GeoIntentKey.LOCATION_FETCH_MODE, mLocationFetchMode)
            intent.putExtra(GeoIntentKey.LOCATION, location)
            startActivity(intent)
        } else if (GeoConstants.ZERO_FIX == mLocationFetchMode) {
            if (mSVP == null) {
                val currentActivity = aTalkApp.getCurrentActivity()
                if (currentActivity != null) {
                    if (currentActivity is OsmActivity) {
                        mSVP = currentActivity
                    }
                }
            }
            if (mSVP != null) {
                mSVP!!.showLocation(location)
            }
        }
    }
}