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

/**
 * GeoLocation application constant definitions.
 *
 * @author Eng Chong Meng
 */
object GeoConstants {
    const val ZERO_FIX = 0 // user defined location to show on map view (for fdroid release)
    const val SINGLE_FIX = 1 // Location provider updated location to show on map view
    const val FOLLOW_ME_FIX = 2 // Location providers updated location with user on motion
    const val ACTION_LOCATION_FETCH_START = "location.fetch.start"
    const val ACTION_LOCATION_FETCH_STOP = "location.fetch.stop"
    const val INTENT_LOCATION_RECEIVED = "intent.location.received"
    const val INTENT_NO_LOCATION_RECEIVED = "intent.no.location.received"
}