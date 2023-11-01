package org.atalk.hmos.plugin.geolocation

import android.location.Location

interface GeoLocationListener {
    fun onLocationPermissionGranted()
    fun onLocationPermissionDenied()
    fun onLocationReceived(location: Location?, locAddress: String?)
    fun onLocationReceivedNone()
    fun onLocationProviderEnabled()
    fun onLocationProviderDisabled()
}