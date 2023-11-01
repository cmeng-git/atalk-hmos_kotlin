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

import android.app.Service
import android.content.Intent
import android.location.Address
import android.location.Criteria
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.TextUtils
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import timber.log.Timber
import java.io.IOException
import java.util.*

/**
 * GeoLocation service that retrieve current location update, and broadcast to the intended receiver.
 * Use the best available Location provider on the device in onCreate()
 *
 * @author Eng Chong Meng
 */
class LocationBgService : Service(), LocationListenerCompat {
    private var mLocationManager: LocationManager? = null
    private var mProvider: String? = null
    private var mServiceHandler: Handler? = null
    private var mLocationMode = 0
    private var mAddressRequest = false
    private var mLocationUpdateMinTime = 0L
    private var mLocationUpdateMinDistance = 0.0f
    private var fallBackToLastLocationTime = 0L
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        mServiceHandler = Handler(Looper.getMainLooper())
        val criteria = Criteria()
        criteria.accuracy = Criteria.ACCURACY_FINE
        criteria.verticalAccuracy = Criteria.ACCURACY_HIGH
        criteria.powerRequirement = Criteria.POWER_MEDIUM
        mLocationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        mProvider = mLocationManager!!.getBestProvider(criteria, true)
        Timber.d("Best location provider selected: %s", mProvider)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val actionIntent = intent.action ?: return START_NOT_STICKY
        // action not defined on gps service first startup
        Timber.d("Location background service start command %s", actionIntent)
        if (actionIntent == GeoConstants.ACTION_LOCATION_FETCH_START) {
            val geoLocationRequest = intent.getParcelableExtra<GeoLocationRequest>(GeoIntentKey.LOCATION_REQUEST)!!
            mLocationMode = geoLocationRequest.locationFetchMode
            mAddressRequest = geoLocationRequest.addressRequest
            mLocationUpdateMinTime = geoLocationRequest.locationUpdateMinTime
            mLocationUpdateMinDistance = geoLocationRequest.locationUpdateMinDistance
            fallBackToLastLocationTime = geoLocationRequest.fallBackToLastLocationTime
            requestLocationUpdates()
        } else if (actionIntent == GeoConstants.ACTION_LOCATION_FETCH_STOP) {
            stopLocationService()
        }

        // Tells the system to not try to recreate the service after it has been killed.
        return START_NOT_STICKY
    }

    private fun requestLocationUpdates() {
        Timber.i("Requesting location updates")
        startFallbackToLastLocationTimer()

        // Use higher accuracy location fix for SINGLE_FIX request
        // int quality = (GeoConstants.SINGLE_FIX == mLocationMode) ? LocationRequestCompat.QUALITY_HIGH_ACCURACY :
        //        LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY;
        try {
            val locationRequest = LocationRequestCompat.Builder(mLocationUpdateMinTime)
                    .setMinUpdateIntervalMillis(mLocationUpdateMinTime)
                    .setMinUpdateDistanceMeters(mLocationUpdateMinDistance)
                    .setQuality(LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
                    .build()
            LocationManagerCompat.requestLocationUpdates(mLocationManager!!, mProvider!!, locationRequest, this, Looper.myLooper()!!)
        } catch (unlikely: SecurityException) {
            Timber.e("Lost location permission. Could not request updates:%s", unlikely.message)
        } catch (ex: Throwable) {
            Timber.e("Unable to attach listener for location provider %s; check permissions? %s", mProvider, ex.message)
        }
    }

    private fun startFallbackToLastLocationTimer() {
        if (fallBackToLastLocationTime != NO_FALLBACK) {
            mServiceHandler!!.removeCallbacksAndMessages(null)
            mServiceHandler!!.postDelayed({ lastLocation }, fallBackToLastLocationTime)
        }
    }

    private val lastLocation: Unit
        get() {
            try {
                LocationManagerCompat.getCurrentLocation(mLocationManager!!, mProvider!!, null, { obj: Runnable -> obj.run() }) { location: Location? ->
                    if (location != null) {
                        Timber.d("Fallback location received: %s", location)
                        onLocationChanged(location)
                    }
                }
            } catch (unlikely: SecurityException) {
                Timber.e(unlikely, "Lost location permission.")
            }
        }

    /**
     * Removes location updates. Note that in this sample we merely log the
     * [SecurityException].
     */
    private fun stopLocationService() {
        if (mServiceHandler != null) mServiceHandler!!.removeCallbacksAndMessages(null)
        if (mLocationManager != null) {
            // Do not set mLocationManager=null, startService immediately after softSelf will not execute onCreate()
            try {
                mLocationManager!!.removeUpdates(this)
            } catch (ex: Throwable) {
                Timber.w("Unable to de-attach location listener: %s", ex.message)
            }
        }
        stopSelf()
        // Timber.d("Stop Location Manager background service");
    }

    override fun onLocationChanged(location: Location) {
        // Timber.d("New location received: %s", location);
        if (location != null) {
            // force to a certain location for testing
            // ocation.setLatitude(34.687274);
            // location.setLongitude(135.525453);
            // location.setAltitude(12.023f);
            GeoPreferenceUtil.Companion.getInstance(this)!!.saveLastKnownLocation(location)
            var locAddress: String? = null
            if (mAddressRequest) {
                locAddress = getLocationAddress(location)
            }

            // Notify anyone listening for broadcasts about the new location.
            val intent = Intent()
            intent.action = GeoConstants.INTENT_LOCATION_RECEIVED
            intent.putExtra(GeoIntentKey.LOCATION, location)
            intent.putExtra(GeoIntentKey.ADDRESS, locAddress)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        } else {
            val intent = Intent()
            intent.action = GeoConstants.INTENT_NO_LOCATION_RECEIVED
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
        mServiceHandler!!.removeCallbacksAndMessages(null)
        if (mLocationMode == GeoConstants.SINGLE_FIX) {
            stopLocationService()
        }
    }

    /**
     * To get address location from coordinates
     *
     * @param loc location from which the address is being retrieved
     * @return the Address
     */
    private fun getLocationAddress(loc: Location): String {
        var locAddress = "No service available or no address found"
        val gcd = Geocoder(baseContext, Locale.getDefault())
        val addresses: List<Address>?
        try {
            addresses = gcd.getFromLocation(loc.latitude, loc.longitude, 1)
            val addressFragments = ArrayList<String?>()
            if (addresses != null && addresses.size > 0) {
                val address = addresses[0]
                // Fetch the address lines using getAddressLine, concatenate them, and send them to the thread.
                for (i in 0..address.maxAddressLineIndex) {
                    addressFragments.add(address.getAddressLine(i))
                }
                locAddress = TextUtils.join(" \n", addressFragments)
            }
        } catch (e: IllegalArgumentException) {
            Timber.e("Get location address: %s", e.message)
        } catch (e: IOException) {
            Timber.e("Get location address: %s", e.message)
        }
        return locAddress
    }

    companion object {
        private const val NO_FALLBACK = 0L
    }
}