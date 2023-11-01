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
import android.location.LocationManager
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.SvpApi

/**
 * The `SvpApiImpl` working in conjunction with ChatFragment to provide street map view support.
 * An implementation API for F-Droid release
 *
 * @author Eng Chong Meng
 */
class SvpApiImpl : SvpApi {
    /**
     * Perform osm street map view when user click the show map button
     */
    override fun onSVPClick(activity: Activity, dblLocation: DoubleArray) {
        val mLocation = toLocation(dblLocation)
        val intent = Intent(activity, OsmActivity::class.java)
        intent.putExtra(GeoIntentKey.LOCATION_FETCH_MODE, GeoConstants.ZERO_FIX)
        intent.putExtra(GeoIntentKey.LOCATION, mLocation)
        intent.putExtra(GeoIntentKey.LOCATION_LIST, ArrayList<Location>())
        activity.startActivity(intent)
    }

    /**
     * Perform osMap street map view followMe when user long click the show map button in chatFragment.
     *
     * @param dblLocations List of double[] values containing Latitude, Longitude and Altitude
     */
    override fun onSVPLongClick(activity: Activity, dblLocations: List<DoubleArray>) {
        val locations = ArrayList<Location>()
        for (entry in dblLocations) {
            locations.add(toLocation(entry))
        }

        // *** for testing only **** //
        // double delta = 0;
        // for (int i = 0; i < 100; i++) {
        //     delta += 0.001;
        //     Location location = toLocation(dblLocations.get(0));
        //     location.setLatitude(location.getLatitude() + delta);
        //     location.setLongitude(location.getLongitude() - delta);
        //     locations.add(location);
        // }
        val intent = Intent(activity, OsmActivity::class.java)
        intent.putExtra(GeoIntentKey.LOCATION_FETCH_MODE, GeoConstants.ZERO_FIX)
        intent.putExtra(GeoIntentKey.LOCATION, toLocation(dblLocations[0]))
        intent.putExtra(GeoIntentKey.LOCATION_LIST, locations)
        activity.startActivity(intent)
    }

    /**
     * Animate to the new given location in osmMap street view if active;
     * call by chatFragment when a new location is received.
     *
     * @param mSVP OsmActivity
     * @param dblLocation: double[] value containing Latitude, Longitude and Altitude
     * @return  OsmActivity, update if any
     */
    override fun svpHandler(mSVP: Any, dblLocation: DoubleArray): Any {
        var mSVP = mSVP
        if (mSVP == null) {
            val currentActivity = aTalkApp.getCurrentActivity()
            if (currentActivity != null) {
                if (currentActivity is OsmActivity) {
                    mSVP = currentActivity
                }
            }
        }
        if (mSVP != null) {
            (mSVP as OsmActivity).showLocation(toLocation(dblLocation))
        }
        return mSVP
    }

    /**
     * Covert double[] to Location
     *
     * @param dblLocation double[] value containing Latitude, Longitude and Altitude
     * @return Location
     */
    private fun toLocation(dblLocation: DoubleArray): Location {
        val mLocation = Location(LocationManager.GPS_PROVIDER)
        mLocation.latitude = dblLocation[0]
        mLocation.longitude = dblLocation[1]
        mLocation.altitude = dblLocation[2]
        return mLocation
    }
}