package org.atalk.hmos.plugin.geolocation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.atalk.hmos.R

class GeoLocationDelegate(private val mActivity: Activity, private val mGeoLocationListener: GeoLocationListener) {
    private val mLocationReceiver = LocationBroadcastReceiver(mGeoLocationListener)
    private var mLocationManager: LocationManager? = null
    private var mGeoLocationRequest: GeoLocationRequest? = null

    fun onCreate() {
        mLocationManager = mActivity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        registerLocationBroadcastReceiver()
    }

    fun onDestroy() {
        unregisterLocationBroadcastReceiver()
        stopLocationUpdates()
    }

    private fun registerLocationBroadcastReceiver() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(GeoConstants.INTENT_LOCATION_RECEIVED)
        intentFilter.addAction(GeoConstants.INTENT_NO_LOCATION_RECEIVED)
        LocalBroadcastManager.getInstance(mActivity).registerReceiver(mLocationReceiver, intentFilter)
    }

    fun unregisterLocationBroadcastReceiver() {
        LocalBroadcastManager.getInstance(mActivity).unregisterReceiver(mLocationReceiver)
    }

    private fun startLocationBGService() {
        if (!LocationManagerCompat.isLocationEnabled(mLocationManager!!)) showLocationServicesRequireDialog() else {
            val intent = Intent(mActivity, LocationBgService::class.java)
            intent.action = GeoConstants.ACTION_LOCATION_FETCH_START
            intent.putExtra(GeoIntentKey.LOCATION_REQUEST, mGeoLocationRequest)
            mActivity.startService(intent)
        }
    }

    fun stopLocationUpdates() {
        val intent = Intent(mActivity, LocationBgService::class.java)
        intent.action = GeoConstants.ACTION_LOCATION_FETCH_STOP
        mActivity.startService(intent)
    }

    fun requestLocationUpdate(geoLocationRequest: GeoLocationRequest?) {
        checkNotNull(geoLocationRequest) { "geoLocationRequest can't be null" }
        mGeoLocationRequest = geoLocationRequest
        checkForPermissionAndRequestLocation()
    }

    private fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        mActivity.startActivityForResult(intent, ENABLE_LOCATION_SERVICES_REQUEST)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(mActivity,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun showPermissionRequireDialog() {
        AlertDialog.Builder(mActivity)
                .setCancelable(true)
                .setTitle(R.string.location_permission_dialog_title)
                .setMessage(R.string.location_permission_dialog_message)
                .setNegativeButton(android.R.string.cancel) { dialogInterface: DialogInterface?, i: Int -> mGeoLocationListener.onLocationPermissionDenied() }
                .setPositiveButton(android.R.string.ok) { dialogInterface: DialogInterface?, i: Int -> requestPermission() }
                .create().show()
    }

    private fun showLocationServicesRequireDialog() {
        AlertDialog.Builder(mActivity)
                .setCancelable(true)
                .setTitle(R.string.location_services_off)
                .setMessage(R.string.open_location_settings)
                .setNegativeButton(android.R.string.cancel) { dialogInterface: DialogInterface?, i: Int -> mGeoLocationListener.onLocationProviderDisabled() }
                .setPositiveButton(android.R.string.ok) { dialogInterface: DialogInterface?, i: Int -> openLocationSettings() }
                .create().show()
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(mActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSIONS_REQUEST)
    }

    private fun checkForPermissionAndRequestLocation() {
        if (!hasLocationPermission()) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(mActivity, Manifest.permission.ACCESS_FINE_LOCATION)) showPermissionRequireDialog() else requestPermission()
        } else {
            startLocationBGService()
        }
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestLocationUpdate(mGeoLocationRequest)
                mGeoLocationListener.onLocationPermissionGranted()
            } else {
                mGeoLocationListener.onLocationPermissionDenied()
            }
        }
    }

    fun onActivityResult(requestCode: Int) {
        if (requestCode == ENABLE_LOCATION_SERVICES_REQUEST) {
            if (LocationManagerCompat.isLocationEnabled(mLocationManager!!)) {
                requestLocationUpdate(mGeoLocationRequest)
                mGeoLocationListener.onLocationProviderEnabled()
            } else {
                mGeoLocationListener.onLocationProviderDisabled()
            }
        }
    }

    val lastKnownLocation: Location?
        get() = GeoPreferenceUtil.getInstance(mActivity)!!.lastKnownLocation

    companion object {
        private const val PERMISSIONS_REQUEST = 100
        private const val ENABLE_LOCATION_SERVICES_REQUEST = 101
    }
}