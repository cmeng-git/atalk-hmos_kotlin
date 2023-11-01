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

import android.annotation.SuppressLint
import android.content.*
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.hardware.GeomagneticField
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.*
import android.view.*
import android.widget.ImageButton
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import androidx.fragment.app.Fragment
import org.atalk.hmos.R
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.IOrientationConsumer
import org.osmdroid.views.overlay.compass.IOrientationProvider
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import timber.log.Timber

/**
 * OSM fragment supporting various osmdroid overlays i.e. followMe, compass, scaleBar, rotation etc
 * Based off the osmdroid examples
 *
 * @author Eng Chong Meng
 * @author Alex O'Ree
 */
open class OsmFragment : Fragment(), LocationListenerCompat, IOrientationConsumer {
    private var mLocationOverlay: MyLocationNewOverlay? = null
    private var mCompassOverlay: CompassOverlay? = null
    private var mScaleBarOverlay: ScaleBarOverlay? = null
    private var mRotationGestureOverlay: RotationGestureOverlay? = null
    private lateinit var mLocationManager: LocationManager
    private var mLocationRequest: LocationRequestCompat? = null
    private lateinit var mProvider: String
    private var mMapView: MapView? = null
    private var mMarker: Marker? = null
    lateinit var mActivity: OsmActivity
    private var mThread: Thread? = null
    private var mLocation: Location? = null
    private var mLocations: ArrayList<Location>? = null
    private var mLocationFetchMode = GeoConstants.FOLLOW_ME_FIX
    private lateinit var btCenterMap: ImageButton
    private lateinit var btFollowMe: ImageButton
    private var mOrientationProvider: IOrientationProvider? = null
    private var mDeviceOrientation = 0
    private var gpsSpeed = 0f
    private var lat = 0f
    private var lon = 0f
    private var alt = 0f
    private var timeOfFix = 0L
    private var mOrientationSupported = false
    private var mHasBearing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mActivity = activity as OsmActivity
        val criteria = Criteria()
        criteria.accuracy = Criteria.ACCURACY_FINE
        criteria.verticalAccuracy = Criteria.ACCURACY_HIGH
        criteria.powerRequirement = Criteria.POWER_MEDIUM
        mLocationManager = mActivity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        mProvider = mLocationManager.getBestProvider(criteria, true)!!

        // Disable all OSMap overlays menu items
        setHasOptionsMenu(false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.osm_followme, null)
        mMapView = v.findViewById(R.id.mapview)
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val dm = mActivity.resources.displayMetrics
        val mGpsMyLocationProvider = GpsMyLocationProvider(mActivity)
        mGpsMyLocationProvider.clearLocationSources()
        mGpsMyLocationProvider.addLocationSource(mProvider)
        mGpsMyLocationProvider.locationUpdateMinTime = mLocationUpdateMinTime
        mGpsMyLocationProvider.locationUpdateMinDistance = mLocationUpdateMinDistance
        val navIcon = BitmapFactory.decodeResource(mActivity.resources, R.drawable.map_navigation_icon)
        mLocationOverlay = MyLocationNewOverlay(mGpsMyLocationProvider, mMapView)
        mLocationOverlay!!.setDirectionIcon(navIcon)
        mLocationOverlay!!.setDirectionAnchor(.5f, .63f)

        // orientation tracking - cannot reuse InternalCompassOrientationProvider from mCompassOverlay
        mCompassOverlay = CompassOverlay(mActivity, InternalCompassOrientationProvider(mActivity), mMapView)
        mOrientationProvider = InternalCompassOrientationProvider(mActivity)
        mScaleBarOverlay = ScaleBarOverlay(mMapView)
        mScaleBarOverlay!!.setCentred(true)
        mScaleBarOverlay!!.setScaleBarOffset(dm.widthPixels / 2, (15 * dm.density).toInt())
        mScaleBarOverlay!!.unitsOfMeasure = ScaleBarOverlay.UnitsOfMeasure.metric
        mRotationGestureOverlay = RotationGestureOverlay(mMapView)
        mRotationGestureOverlay!!.isEnabled = true
        mMarker = Marker(mMapView)
        mMapView!!.controller.setZoom(16.0)
        mMapView!!.isTilesScaledToDpi = true
        mMapView!!.setMultiTouchControls(true)
        mMapView!!.isFlingEnabled = true
        mMapView!!.overlays.add(mLocationOverlay)
        mMapView!!.overlays.add(mCompassOverlay)
        mMapView!!.overlays.add(mScaleBarOverlay)
        mMapView!!.overlays.add(CopyrightOverlay(mActivity))
        mMapView!!.controller
        val args = arguments
        if (args != null) {
            mLocationFetchMode = args.getInt(GeoIntentKey.LOCATION_FETCH_MODE)
            mLocation = args.getParcelable(GeoIntentKey.LOCATION)
            mLocations = args.getParcelableArrayList(GeoIntentKey.LOCATION_LIST)
        }
        btCenterMap = view.findViewById(R.id.ic_center_map)
        btCenterMap.setOnClickListener { v: View? ->
            if (mLocation != null) {
                mMapView!!.controller.animateTo(GeoPoint(mLocation))
            }
        }
        btFollowMe = view.findViewById(R.id.ic_follow_me)
        btFollowMe.setOnClickListener { v: View? -> updateFollowMe(!mLocationOverlay!!.isFollowLocationEnabled) }
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        if (mMapView != null) {
            mMapView!!.onResume()
        }
        try {
            mLocationRequest = LocationRequestCompat.Builder(mLocationUpdateMinTime)
                    .setMinUpdateIntervalMillis(mLocationUpdateMinTime)
                    .setMinUpdateDistanceMeters(mLocationUpdateMinDistance)
                    .setQuality(LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
                    .build()
            LocationManagerCompat.requestLocationUpdates(mLocationManager, mProvider, mLocationRequest!!, this, Looper.myLooper()!!)
        } catch (unlikely: SecurityException) {
            Timber.e("Lost location permission. Could not request updates:%s", unlikely.message)
        } catch (ex: Throwable) {
            Timber.e("Unable to attach listener for location provider %s; check permissions? %s", mProvider, ex.message)
        }
        mOrientationSupported = mOrientationProvider!!.startOrientationProvider(this)
        mLocationOverlay!!.enableMyLocation()
        mCompassOverlay!!.enableCompass()
        mScaleBarOverlay!!.enableScaleBar()

        // Always locks the current screen orientation when showing map.
        setDeviceOrientation()

        // Enable followMe if from user selected location to show
        val isFollowMe = GeoConstants.ZERO_FIX != mLocationFetchMode
        updateFollowMe(isFollowMe)
        if (!isFollowMe) {
            mLocationManager.removeUpdates(this)
            if (mLocation != null) {
                mMarker!!.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                mMapView!!.overlays.add(mMarker)
                showLocation(mLocation)
            }
            if (mLocations != null && mLocations!!.isNotEmpty()) {
                startLocationFollowMe(mLocations!!)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (mMapView != null) {
            mMapView!!.onPause()
        }
        if (mThread != null) {
            mThread!!.interrupt()
            mThread = null
        }
        try {
            mLocationManager.removeUpdates(this)
        } catch (ex: Exception) {
            Timber.d("Unexpected exception: %s", ex.message)
        }
        mLocationOverlay!!.disableMyLocation()
        mCompassOverlay!!.disableCompass()
        mScaleBarOverlay!!.disableScaleBar()

        // unlock the orientation
        mActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (mOrientationProvider != null) {
            mOrientationProvider!!.stopOrientationProvider()
        }
        updateFollowMe(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (mMapView != null) mMapView!!.onDetach()
        mMapView = null
        // mLocationManager = null
        mLocation = null
        mLocationOverlay = null
        mCompassOverlay = null
        mScaleBarOverlay = null
        mRotationGestureOverlay = null
        mOrientationProvider!!.destroy()
        mOrientationProvider = null
        // btCenterMap = null
        // btFollowMe = null
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        try {
            mMapView!!.overlayManager.onCreateOptionsMenu(menu, MENU_LAST_ID, mMapView)
        } catch (npe: NullPointerException) {
            // can happen during CI tests and very rapid fragment switching
        }
        super.onCreateOptionsMenu(menu, inflater)
    }

    @Deprecated("Deprecated in Java")
    override fun onPrepareOptionsMenu(menu: Menu) {
        mMapView!!.overlayManager.onPrepareOptionsMenu(menu, MENU_LAST_ID, mMapView)
        super.onPrepareOptionsMenu(menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return mMapView!!.overlayManager.onOptionsItemSelected(item, MENU_LAST_ID, mMapView)
    }

    fun invalidateMapView() {
        mMapView!!.invalidate()
    }

    @SuppressLint("MissingPermission")
    private fun updateFollowMe(isFollowMe: Boolean) {
        if (isFollowMe) {
            mLocationOverlay!!.enableFollowLocation()
            btFollowMe.setImageResource(R.drawable.ic_follow_me_on)
            if (GeoConstants.ZERO_FIX != mLocationFetchMode) {
                mLocationFetchMode = GeoConstants.FOLLOW_ME_FIX
                LocationManagerCompat.requestLocationUpdates(mLocationManager, mProvider, mLocationRequest!!, this, Looper.myLooper()!!)
            }
        } else {
            mLocationOverlay!!.disableFollowLocation()
            btFollowMe.setImageResource(R.drawable.ic_follow_me)
        }
    }

    private fun setDeviceOrientation() {
        val orientation: Int
        val rotation = mActivity.windowManager.defaultDisplay.rotation
        when (rotation) {
            Surface.ROTATION_0 -> {
                orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                mDeviceOrientation = 0
            }
            Surface.ROTATION_90 -> {
                mDeviceOrientation = 90
                orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            Surface.ROTATION_180 -> {
                mDeviceOrientation = 180
                orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            }
            Surface.ROTATION_270 -> {
                mDeviceOrientation = 270
                orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            }
            else -> {
                mDeviceOrientation = 270
                orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            }
        }

        // Lock the device in current screen orientation
        mActivity.requestedOrientation = orientation
    }

    /*
     * Adjusts the desired map rotation based on device orientation and compass-trueNorth/gps-bearing heading
     */
    private fun setMapOrientation(direction: Float) {
        var t = 360 - direction - mDeviceOrientation
        if (t < 0) {
            t += 360f
        }
        if (t > 360) {
            t -= 360f
        }

        // help smooth everything out
        t = t.toInt().toFloat()
        t /= 5
        t = t.toInt().toFloat()
        t *= 5
        mMapView!!.mapOrientation = t
    }

    // Note: on devices without a compass this never fires this...
    // Only use the compass bit if we aren't moving, since gps is more accurate when we are moving.
    // aTalk always uses Compass if available, for screen orientation alignment
    override fun onOrientationChanged(orientationToMagneticNorth: Float, source: IOrientationProvider) {
        // if (gpsSpeed < gpsSpeedThreshold || !mHasBearing) {
        if (mOrientationSupported) {
            val gmField = GeomagneticField(lat, lon, alt, timeOfFix)
            var trueNorth = orientationToMagneticNorth + gmField.declination
            synchronized(trueNorth) {
                if (trueNorth > 360.0f) {
                    trueNorth -= 360.0f
                }
                setMapOrientation(trueNorth)
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        if (GeoConstants.FOLLOW_ME_FIX == mLocationFetchMode) {
            mLocation = location
        }
        lat = location.latitude.toFloat()
        lon = location.longitude.toFloat()
        alt = location.altitude.toFloat() //meters
        timeOfFix = location.time
        mHasBearing = location.hasBearing()
        gpsSpeed = location.speed

        // Let the compass take over if stationary and Orientation is supported
        // if (mHasBearing && (gpsSpeed >= gpsSpeedThreshold || !mOrientationSupported)) {
        if (!mOrientationSupported && mHasBearing) {
            val gpsBearing = location.bearing
            setMapOrientation(gpsBearing)
            // Timber.d("Bearing GPS: %s (%s)", gpsBearing, gpsSpeed);
        }
    }

    /**
     * Move the marker to the new user selected location on the street map view
     *
     * @param location the new location location to animate to
     */
    fun showLocation(location: Location?) {
        if (mMapView != null) {
            mLocation = location
            mMapView!!.controller.animateTo(GeoPoint(location))
            mLocationOverlay!!.onLocationChanged(mLocation, null)
            mMarker!!.position = GeoPoint(location)
            Timber.d("Animate to location: %s", location)
        }
    }

    /**
     * Animate the followMe with the given location arrayList at 2 second interval
     *
     * @param locations the ArrayList<Location>
    </Location> */
    private fun startLocationFollowMe(locations: ArrayList<Location>) {
        mThread = Thread {
            for (xLocation in locations) {
                try {
                    Thread.sleep(2000)
                    mActivity.runOnUiThread { showLocation(xLocation) }
                } catch (ex: InterruptedException) {
                    break
                } catch (ex: Exception) {
                    Timber.e("Exception: %s", ex.message)
                }
            }
        }
        mThread!!.start()
    }

    companion object {
        private const val MENU_LAST_ID = Menu.FIRST
        private const val mLocationUpdateMinTime = 1000L // mS
        private const val mLocationUpdateMinDistance = 1.0f // meters
        private const val gpsSpeedThreshold = 0.5f // m/s
    }
}