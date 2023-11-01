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
import android.content.IntentFilter
import android.location.Location
import android.net.ConnectivityManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.service.httputil.HttpConnectionManager
import org.osmdroid.config.Configuration
import timber.log.Timber

/**
 * Default osMap view activity ported from osmdroid.
 * Created by plusminus on 00:23:14 - 03.10.2008
 *
 * @author Manuel Stahl
 * @author Eng Chong Meng
 */
class OsmActivity : AppCompatActivity() {
    private var osmFragment: OsmFragment? = null
    private var mLocation: Location? = null
    private var mLocations: ArrayList<Location>? = null
    private var mLocationFetchMode = GeoConstants.ZERO_FIX

    /**
     * The idea behind that is to force a MapView refresh when switching from offline to online.
     * If you don't do that, the map may display - when online - approximated tiles
     * - that were computed when offline
     * - that could be replaced by downloaded tiles
     * - but as the display is not refreshed there's no try to get better tiles
     *
     * @since 6.0
     */
    private val networkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                osmFragment!!.invalidateMapView()
            } catch (e: NullPointerException) {
                // lazy handling of an improbable NPE
                Timber.e("Network receiver exception: %s", e.message)
            }
        }
    }

    /**
     * Called when the activity is first created.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContentView(R.layout.osm_map_main)
        Configuration.getInstance().userAgentValue = HttpConnectionManager.userAgent

        // noinspection ConstantConditions
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        registerReceiver(networkReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
        if (savedInstanceState == null) {
            mLocationFetchMode = intent.getIntExtra(GeoIntentKey.LOCATION_FETCH_MODE, GeoConstants.FOLLOW_ME_FIX)
            mLocation = intent.getParcelableExtra(GeoIntentKey.LOCATION)
            mLocations = intent.getParcelableArrayListExtra(GeoIntentKey.LOCATION_LIST)
        } else {
            mLocationFetchMode = savedInstanceState.getInt(GeoIntentKey.LOCATION_FETCH_MODE, GeoConstants.FOLLOW_ME_FIX)
            mLocation = savedInstanceState.getParcelable(GeoIntentKey.LOCATION)
            mLocations = savedInstanceState.getParcelableArrayList(GeoIntentKey.LOCATION_LIST)
        }
        val fm = supportFragmentManager
        osmFragment = fm.findFragmentByTag(MAP_FRAGMENT_TAG) as OsmFragment?
        if (osmFragment == null) {
            osmFragment = OsmFragment()
            val args = Bundle()
            args.putInt(GeoIntentKey.LOCATION_FETCH_MODE, mLocationFetchMode)
            args.putParcelable(GeoIntentKey.LOCATION, mLocation)
            args.putParcelableArrayList(GeoIntentKey.LOCATION_LIST, mLocations)
            osmFragment!!.arguments = args
            fm.beginTransaction().add(R.id.map_container, osmFragment!!, MAP_FRAGMENT_TAG).commit()
        }
    }

    override fun onResume() {
        super.onResume()
        aTalkApp.setCurrentActivity(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(GeoIntentKey.LOCATION_FETCH_MODE, mLocationFetchMode.toLong())
        outState.putParcelable(GeoIntentKey.LOCATION, mLocation)
        outState.putParcelableArrayList(GeoIntentKey.LOCATION_LIST, mLocations)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onDestroy() {
        unregisterReceiver(networkReceiver)
        super.onDestroy()
    }

    /**
     * Move the marker to the new Location location on the street map view
     *
     * @param location the new location to animate to
     */
    fun showLocation(location: Location?) {
        if (osmFragment == null) {
            osmFragment = supportFragmentManager.findFragmentByTag(MAP_FRAGMENT_TAG) as OsmFragment?
        }
        if (osmFragment != null) {
            osmFragment!!.showLocation(location)
        }
    }

    companion object {
        private const val MAP_FRAGMENT_TAG = "org.osmdroid.MAP_FRAGMENT_TAG"
    }
}