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
package org.atalk.hmos.plugin.permissions

import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener

open class AppPermissionListener(private val activity: PermissionsActivity) : PermissionListener {
    override fun onPermissionGranted(response: PermissionGrantedResponse) {
        activity.showPermissionGranted(response.permissionName)
    }

    override fun onPermissionDenied(response: PermissionDeniedResponse) {
        activity.showPermissionDenied(response.permissionName, response.isPermanentlyDenied)
    }

    override fun onPermissionRationaleShouldBeShown(permission: PermissionRequest, token: PermissionToken) {
        // activity.showPermissionRationale(token);
        token.continuePermissionRequest()
    }
}