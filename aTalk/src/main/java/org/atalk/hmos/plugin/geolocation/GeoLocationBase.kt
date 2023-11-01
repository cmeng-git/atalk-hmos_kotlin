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

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.os.*
import android.view.View
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import org.atalk.hmos.BaseActivity
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.plugin.geolocation.GeoLocationRequest.GeoLocationRequestBuilder
import timber.log.Timber
import java.util.*

/**
 * GeoLocationBase class for updating Location info and displaying map view if desired
 *
 * @author Eng Chong Meng
 */
open class GeoLocationBase : BaseActivity(), View.OnClickListener, OnSeekBarChangeListener, GeoLocationListener {
    private var mLocation: Location? = null
    private var mAnimation: ObjectAnimator? = null
    private var isFollowMe = false
    private var mShareAllow = false
    private var mShowMap = false
    protected var mSVPStarted = false
    protected var mLocationFetchMode = 0
    private val gpsDistanceStep = 5 // meters
    private val timeIntervalStep = 10 // seconds
    private var mLatitudeTextView: TextView? = null
    private var mLongitudeTextView: TextView? = null
    private var mAltitudeTextView: TextView? = null
    private var mLocationAddressTextView: TextView? = null
    private var mSeekDistanceInterval: SeekBar? = null
    private var mBtnSingleFix: Button? = null
    private var mBtnFollowMe: Button? = null
    private var mBtnGpsShare: CheckBox? = null
    private var mDemo = false
    private var delta = 0f // for demo
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setMainTitle(R.string.service_gui_GEO_LOCATION)
        isFollowMe = mGeoLocationDelegate != null
        if (isFollowMe) {
            mGeoLocationDelegate!!.unregisterLocationBroadcastReceiver()
            mGeoLocationDelegate = null
        }
        mGeoLocationDelegate = GeoLocationDelegate(this, this)
        mGeoLocationDelegate!!.onCreate()
        mShareAllow = savedInstanceState?.getBoolean(SHARE_ALLOW)
                ?: intent.extras!!.getBoolean(SHARE_ALLOW, false)
        setContentView(R.layout.geo_location)
        mLatitudeTextView = findViewById(R.id.latitude_textview)
        mLongitudeTextView = findViewById(R.id.longitude_textview)
        mAltitudeTextView = findViewById(R.id.altitude_textview)
        mLocationAddressTextView = findViewById(R.id.locationAddress_textview)
        mBtnSingleFix = findViewById(R.id.btn_single_fix)
        mBtnSingleFix!!.setOnClickListener(this)
        mBtnFollowMe = findViewById(R.id.btn_follow_me)
        mBtnFollowMe!!.text = String.format(getString(R.string.start_follow_me), gpsMinDistance, sendTimeInterval)
        mBtnFollowMe!!.setOnClickListener(this)
        mAnimation = ObjectAnimator.ofInt(mBtnFollowMe, "textColor", Color.GREEN, Color.BLACK)
        mAnimation!!.duration = 1000
        mAnimation!!.setEvaluator(ArgbEvaluator())
        mAnimation!!.repeatCount = ValueAnimator.INFINITE
        mAnimation!!.repeatMode = ValueAnimator.REVERSE
        mBtnGpsShare = findViewById(R.id.gps_share)
        mBtnGpsShare!!.isEnabled = mShareAllow
        mBtnGpsShare!!.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean -> Companion.isGpsShare = isChecked }
        mBtnGpsShare!!.isChecked = mShareAllow && Companion.isGpsShare
        mSeekDistanceInterval = findViewById(R.id.seekDistanceInterval)
        mSeekDistanceInterval!!.max = 100
        mSeekDistanceInterval!!.progress = gpsMinDistance / gpsDistanceStep
        mSeekDistanceInterval!!.setOnSeekBarChangeListener(this)
        val seekTimeInterval = findViewById<SeekBar>(R.id.seekTimeInterval)
        seekTimeInterval.max = 100
        var progress = (sendTimeInterval - timeIntervalStep) / timeIntervalStep
        if (progress < 0) progress = 0
        seekTimeInterval.progress = progress
        seekTimeInterval.setOnSeekBarChangeListener(this)

        // Long press for demo at 0m and 2S interval
        mBtnFollowMe!!.setOnLongClickListener {
            mDemo = true
            mSeekDistanceInterval!!.progress = 0
            sendTimeInterval = 2
            mBtnFollowMe!!.performClick()
            true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SHARE_ALLOW, mShareAllow)
    }

    override fun onResume() {
        super.onResume()
        aTalkApp.setCurrentActivity(this)
        mLocation = null
        mSVPStarted = false
        mShowMap = false
        mDemo = false
        if (isFollowMe) {
            updateSendButton(false)
            mAnimation!!.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isFollowMe && mGeoLocationDelegate != null) {
            mGeoLocationDelegate!!.unregisterLocationBroadcastReceiver()
            mGeoLocationDelegate = null
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.btn_single_fix -> {
                mLocationFetchMode = GeoConstants.SINGLE_FIX
                if (isFollowMe) {
                    updateSendButton(true)
                    stopLocationUpdates()
                }
                mShowMap = true
                val geoLocationRequest = GeoLocationRequestBuilder()
                        .setLocationFetchMode(mLocationFetchMode)
                        .setAddressRequest(true)
                        .setLocationUpdateMinTime(0L)
                        .setLocationUpdateMinDistance(0.0f)
                        .setFallBackToLastLocationTime(3000)
                        .build()
                requestLocationUpdates(geoLocationRequest)
            }

            R.id.btn_follow_me -> {
                mLocationFetchMode = if (mDemo) GeoConstants.ZERO_FIX else GeoConstants.FOLLOW_ME_FIX
                if (isFollowMe) {
                    updateSendButton(true)
                    stopLocationUpdates()
                } else {
                    updateSendButton(false)
                    mShowMap = true
                    val geoLocationRequest = GeoLocationRequestBuilder()
                            .setLocationFetchMode(mLocationFetchMode)
                            .setAddressRequest(true)
                            .setLocationUpdateMinTime(sendTimeInterval * 1000L)
                            .setLocationUpdateMinDistance(gpsMinDistance.toFloat())
                            .setFallBackToLastLocationTime(sendTimeInterval * 500L)
                            .build()
                    requestLocationUpdates(geoLocationRequest)
                }
            }
        }
    }

    private fun updateSendButton(followMe: Boolean) {
        if (followMe) {
            isFollowMe = false
            mBtnFollowMe!!.text = getString(R.string.start_follow_me, gpsMinDistance, sendTimeInterval)
            mBtnFollowMe!!.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            mAnimation!!.end()
            mAnimation!!.cancel()
        } else {
            isFollowMe = true
            mBtnFollowMe!!.text = getString(R.string.stop_follow_me, gpsMinDistance, sendTimeInterval)
            mAnimation!!.start()
        }
    }

    val isGpsShare: Boolean
        get() = isFollowMe && Companion.isGpsShare

    override fun onLocationPermissionGranted() {
        showToast("Location permission granted")
    }

    override fun onLocationPermissionDenied() {
        showToast("Location permission denied")
    }

    override fun onLocationReceived(location: Location?, locAddress: String?) {
        if (mDemo) {
            delta += 0.0001.toFloat()
            location!!.latitude = location.latitude + delta
            location.longitude = location.longitude - delta
        }
        val mLatitude = location!!.latitude.toString()
        val mLongitude = location.longitude.toString()
        val mAltitude = String.format(Locale.US, "%.03fm", location.altitude)
        mLatitudeTextView!!.text = mLatitude
        mLongitudeTextView!!.text = mLongitude
        mAltitudeTextView!!.text = mAltitude
        mLocationAddressTextView!!.text = locAddress
        Timber.d("Update map needed: %s %s %s", isFollowMe,
                if (mLocation != null) location.distanceTo(mLocation!!) else 0, location)
        // aTalkApp.showToastMessage("on Location Received: " + ((mLocation != null) ? location.distanceTo(mLocation) : 0) + "; " + location);
        mLocation = location
        if (mBtnGpsShare!!.isChecked && mCallBack != null) {
            mCallBack!!.onResult(location, locAddress)
        }
        if (mShowMap) showStreetMap(location)
    }

    /**
     * To be implemented by app if show streetMap is desired after a new Location is received.
     *
     * @param location at which the pointer is place and map centered
     */
    open fun showStreetMap(location: Location?) {}
    override fun onLocationReceivedNone() {
        showToast("No location received")
    }

    override fun onLocationProviderEnabled() {
        showToast("Location services are now ON")
    }

    override fun onLocationProviderDisabled() {
        showToast("Location services are still Off")
    }

    /**
     * Notification that the progress level has changed. Clients can use the fromUser parameter
     * to distinguish user-initiated changes from those that occurred programmatically.
     *
     * @param seekBar The SeekBar whose progress has changed
     * @param progress The current progress level. This will be in the range 0..max where max
     * was set by [ProgressBar.setMax]. (The default value for max is 100.)
     * @param fromUser True if the progress change was initiated by the user.
     */
    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (seekBar === mSeekDistanceInterval) gpsMinDistance = progress * gpsDistanceStep else {
            sendTimeInterval = if (progress == 0) 5 else progress * timeIntervalStep
        }
        mBtnFollowMe!!.text = getString(R.string.start_follow_me, gpsMinDistance, sendTimeInterval)
        mBtnFollowMe!!.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {}
    override fun onStopTrackingTouch(seekBar: SeekBar) {
        if (isFollowMe) {
            mBtnFollowMe!!.text = getString(R.string.stop_follow_me, gpsMinDistance, sendTimeInterval)
        }
        showToast(getString(R.string.apply_new_location_setting))
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    interface LocationListener {
        fun onResult(location: Location?, locAddress: String?)
    }

    protected val lastKnownLocation: Location?
        get() = mGeoLocationDelegate!!.lastKnownLocation

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        mGeoLocationDelegate!!.onActivityResult(requestCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        mGeoLocationDelegate!!.onRequestPermissionsResult(requestCode, grantResults)
    }

    private fun requestLocationUpdates(geoLocationRequest: GeoLocationRequest?) {
        mGeoLocationDelegate!!.requestLocationUpdate(geoLocationRequest)
    }

    private fun stopLocationUpdates() {
        mGeoLocationDelegate!!.stopLocationUpdates()
    }

    companion object {
        const val SHARE_ALLOW = "Share_Allow"
        private var mCallBack: LocationListener? = null
        private var mGeoLocationDelegate: GeoLocationDelegate? = null
        private var isGpsShare = false
        private var gpsMinDistance = 50 // meters
        private var sendTimeInterval = 60 // seconds
        @JvmStatic
        fun registeredLocationListener(listener: LocationListener?) {
            mCallBack = listener
        }
    }
}