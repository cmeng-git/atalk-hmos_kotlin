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

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.*

/**
 * Parcelable  geoLocation request with builder implementation.
 * Replace gms LocationRequest for both gms and fdroid support
 *
 * @author Eng Chong Meng
 */
class GeoLocationRequest(
        val locationFetchMode: Int,
        val addressRequest: Boolean,
        val locationUpdateMinTime: Long,
        val locationUpdateMinDistance: Float,
        val fallBackToLastLocationTime: Long) : Parcelable {

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(locationFetchMode)
        dest.writeByte((if (addressRequest) 1 else 0).toByte())
        dest.writeLong(locationUpdateMinTime)
        dest.writeFloat(locationUpdateMinDistance)
        dest.writeLong(fallBackToLastLocationTime)
    }

    class GeoLocationRequestBuilder {
        private var mLocationFetchMode = 0
        private var mAddressRequest = false
        private var mUpdateMinTime = 0L
        private var mUpdateMinDistance = 0f
        private var mFallBackToLastLocationTime = 0L
        fun setLocationFetchMode(locationFetchMode: Int): GeoLocationRequestBuilder {
            mLocationFetchMode = locationFetchMode
            return this
        }

        fun setAddressRequest(addressRequest: Boolean): GeoLocationRequestBuilder {
            mAddressRequest = addressRequest
            return this
        }

        fun setLocationUpdateMinTime(minTime: Long): GeoLocationRequestBuilder {
            mUpdateMinTime = minTime
            return this
        }

        fun setLocationUpdateMinDistance(minDistance: Float): GeoLocationRequestBuilder {
            mUpdateMinDistance = minDistance
            return this
        }

        fun setFallBackToLastLocationTime(fallBackToLastLocationTime: Long): GeoLocationRequestBuilder {
            mFallBackToLastLocationTime = fallBackToLastLocationTime
            return this
        }

        fun build(): GeoLocationRequest {
            return GeoLocationRequest(
                    mLocationFetchMode,
                    mAddressRequest,
                    mUpdateMinTime,
                    mUpdateMinDistance,
                    mFallBackToLastLocationTime)
        }
    }

    companion object {
        @JvmField
        val CREATOR = object : Creator<GeoLocationRequest?> {
            override fun createFromParcel(`in`: Parcel): GeoLocationRequest {
                return GeoLocationRequest( /* mLoctionFetchMode = */
                        `in`.readInt(),  /* mAddressRequest= */
                        `in`.readByte().toInt() != 0,  /* mUpdateMinTime= */
                        `in`.readLong(),  /* mUpdateMinDistance= */
                        `in`.readFloat(),  /* mFallBackToLastLocationTime= */
                        `in`.readLong()
                )
            }

            // @Override
            override fun newArray(size: Int): Array<GeoLocationRequest?> {
                return arrayOfNulls(size)
            }
        }
    }
}