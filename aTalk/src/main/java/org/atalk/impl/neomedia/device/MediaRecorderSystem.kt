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
package org.atalk.impl.neomedia.device

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.text.TextUtils
import androidx.core.content.ContextCompat
import net.java.sip.communicator.util.UtilActivator
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.impl.neomedia.device.util.AndroidCamera
import org.atalk.impl.neomedia.device.util.CameraUtils
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl
import org.atalk.service.neomedia.codec.Constants
import org.atalk.util.MediaType
import timber.log.Timber
import java.awt.Dimension
import java.util.*
import javax.media.CaptureDeviceManager
import javax.media.Format
import javax.media.MediaLocator

/**
 * Discovers and registers `MediaRecorder` capture devices with FMJ.
 * Not further use in aTalk since v2.8.0; after android API 23, android drops support for non-seekable
 * file descriptors i.e. mediaRecorder.setOutputFile(createLocalSocket());
 *
 * @author Eng Chong Meng
 */
class MediaRecorderSystem
/**
 * Initializes a new `MediaRecorderSystem` instance which discovers and registers
 * `MediaRecorder` capture devices with FMJ.
 *
 * @throws Exception if anything goes wrong while discovering and registering
 * `MediaRecorder` capture devices with FMJ
 */
    : DeviceSystem(MediaType.VIDEO, LOCATOR_PROTOCOL_MEDIARECORDER) {
    /**
     * {@inheritDoc}
     */
    override fun doInitialize() {
        if (isMediaRecorderInitialized || ContextCompat.checkSelfPermission(aTalkApp.globalContext,
                        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        try {
            val cameraManager = aTalkApp.cameraManager
            val cameraIdList = cameraManager.cameraIdList

            // Timber.d("Number of android cameras: %s", cameraIdList.length);
            if (cameraIdList.isEmpty()) {
                return
            }
            val mConfig = UtilActivator.configurationService!!
            val captureDevices = LinkedList<AndroidCamera>()
            for (cameraId in cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        ?: continue

                // create a locator with camera id and its facing direction (cameraInfo)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)!!
                val locator = AndroidCamera.constructLocator(LOCATOR_PROTOCOL_MEDIARECORDER, cameraId, facing)

                // List of preferred resolutions which is supported by the Camera.
                val sizes = ArrayList<Dimension>()
                val vSize = mConfig.getString(locator.toString() + VIDEO_SIZE, null)!!
                if (TextUtils.isEmpty(vSize) || !CameraUtils.getSupportedSizes(vSize, sizes)) {
                    /*
                     * Check if the Camera API2 is supported with camCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                     * Return an int corresponding to the level of support for Camera API2.
                     * If equal to CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY, cameraId does not support Camera API2.
                     */
                    val sLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                    if (CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY == sLevel) {
                        Timber.w("Camera API2 is not supported for camera: %s", cameraId)
                        continue
                    } else {
                        Timber.w("Camera API2 is supported for camera: %s; Level: %s", cameraId, sLevel)
                    }
                    val supportedSizes = map.getOutputSizes(SurfaceTexture::class.java)
                    if (supportedSizes == null) {
                        /*
                         * The video size is the same as the preview size.
                         * MediaRecorder.setVideoSize(int,int) will most likely fail, print a line in
                         * logcat and not throw an exception (in DataSource.doStart()).
                         */
                        Timber.w("get Output Preview Sizes returned null for camera: %s", cameraId)
                        continue
                    } else {
                        Timber.i("Video sizes supported by %s: %s",
                                locator, CameraUtils.cameraSizesToString(supportedSizes))
                    }

                    // Save to DB and keep a copy of the video resolution supportSizes for cameraId
                    mConfig.setProperty(locator.toString() + VIDEO_SIZE, CameraUtils.cameraSizesToString(supportedSizes))
                    CameraUtils.setCameraSupportSize(cameraId, supportedSizes)

                    // Selects only compatible dimensions
                    for (candidate in supportedSizes) {
                        if (CameraUtils.isPreferredSize(candidate)) {
                            sizes.add(Dimension(candidate.width, candidate.height))
                        }
                    }
                }
                Timber.i("Video preferred: %s: %s", locator, CameraUtils.dimensionsToString(sizes))
                val count = sizes.size
                if (count == 0) continue

                // Saves supported video sizes
                val array = arrayOfNulls<Dimension>(count)
                (sizes as ArrayList).toArray<Dimension>(array)
                SUPPORTED_SIZES = array
                val formats = arrayOfNulls<Format>(count)
                for (i in 0 until count) {
                    formats[i] = ParameterizedVideoFormat(
                            Constants.H264,
                            sizes[i],
                            Format.NOT_SPECIFIED /* maxDataLength */,
                            Format.byteArray,
                            Format.NOT_SPECIFIED.toFloat() /* frameRate */,
                            ParameterizedVideoFormat.toMap(VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, "1"))
                }

                // Create display name
                var name = if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    aTalkApp.getResString(R.string.service_gui_settings_CAMERA_FRONT)
                }
                else {
                    aTalkApp.getResString(R.string.service_gui_settings_CAMERA_BACK)
                }
                name += " (MediaRecoder#$cameraId)"

                // XXX Prefer the front-facing camera over the back-facing one.
                val device = AndroidCamera(name, locator, formats)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) captureDevices.add(0, device) else captureDevices.add(device)
            }
            if (captureDevices.isNotEmpty()) {
                for (captureDevice in captureDevices) CaptureDeviceManager.addDevice(captureDevice)
            }
            isMediaRecorderInitialized = true
        } catch (e: CameraAccessException) {
            Timber.w("Exception in MediaRecorderSystem init: %s", e.message)
        }
    }

    companion object {
        private const val VIDEO_SIZE = ".video.size"

        /**
         * Supported preview sizes by android camera for user selection
         */
        var SUPPORTED_SIZES = arrayOf<Dimension?>()
        private var isMediaRecorderInitialized = false
    }
}