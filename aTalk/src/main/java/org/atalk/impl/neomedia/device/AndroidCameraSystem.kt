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

import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.text.TextUtils
import android.view.Surface
import net.java.sip.communicator.util.UtilActivator
import org.apache.commons.lang3.ArrayUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.impl.neomedia.codec.video.AndroidEncoder
import org.atalk.impl.neomedia.device.util.AndroidCamera
import org.atalk.impl.neomedia.device.util.CameraUtils
import org.atalk.service.neomedia.codec.Constants
import org.atalk.util.MediaType
import timber.log.Timber
import java.awt.Dimension
import javax.media.CaptureDeviceManager
import javax.media.Format
import javax.media.format.VideoFormat
import javax.media.format.YUVFormat

/**
 * Device system that provides YUV and Surface format camera data source. YUV frames are captured
 * using camera preview callback. Surface is passed directly through static methods to encoders.
 *
 * @author Eng Chong Meng
 */
class AndroidCameraSystem
    /**
     * Creates a new instance of `AndroidCameraSystem`.
     *
     * @throws Exception from super
     */
    : DeviceSystem(MediaType.VIDEO, LOCATOR_PROTOCOL_ANDROIDCAMERA, 0) {

    /**
     * {@inheritDoc}
     */
    override fun doInitialize() {
        if (isCameraInitialized) {
            return
        }

        // cleanup camera properties messed up by camera1/2 testing during development
        // cleanMediaDB();
        try {
            val cameraManager = aTalkApp.cameraManager
            val cameraIdList = cameraManager.cameraIdList

            // Timber.d("Number of android cameras: %s", cameraIdList.length);
            if (cameraIdList.isEmpty()) {
                return
            }

            val mConfig = UtilActivator.configurationService!!
            for (cameraId in cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        ?: continue

                // create a locator with camera id and its facing direction
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                val locator = AndroidCamera.constructLocator(LOCATOR_PROTOCOL_ANDROIDCAMERA, cameraId, facing!!)

                // Retrieve the camera formats supported by this cameraId from DB
                var sFormat = mConfig.getString(locator.toString() + PREVIEW_FORMAT, null)
                var cameraFormats = CameraUtils.stringToCameraFormat(sFormat)

                // List of preferred resolutions which is supported by the Camera.
                val sizes = ArrayList<Dimension>()
                var vSize = mConfig.getString(locator.toString() + VIDEO_SIZE, null)
                if (TextUtils.isEmpty(sFormat) || !CameraUtils.getSupportedSizes(vSize, sizes)) {
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
                        Timber.d("Camera API2 is supported for camera: %s; Level: %s", cameraId, sLevel)
                    }

                    val previewSizes = map.getOutputSizes(SurfaceTexture::class.java)
                    if (previewSizes == null) {
                        /*
                         * The video size is the same as the preview size.
                         * MediaRecorder.setVideoSize(int,int) will most likely fail, print a line in
                         * logcat and not throw an exception (in DataSource.doStart()).
                         */
                        Timber.w("Output Preview Sizes returned null for camera: %s", cameraId)
                        continue
                    }

                    vSize = CameraUtils.cameraSizesToString(previewSizes)
                    // Save to DB and keep a copy of the video resolution supportSizes for cameraId
                    mConfig.setProperty(locator.toString() + VIDEO_SIZE, vSize)
                    CameraUtils.setCameraSupportSize(cameraId, previewSizes)

                    // Selects only compatible dimensions
                    sizes.clear()
                    for (candidate in previewSizes) {
                        if (CameraUtils.isPreferredSize(candidate)) {
                            sizes.add(Dimension(candidate.width, candidate.height))
                        }
                    }

                    cameraFormats = listOf(*ArrayUtils.toObject(map.outputFormats))
                    sFormat = CameraUtils.cameraImgFormatsToString(cameraFormats)
                    mConfig.setProperty(locator.toString() + PREVIEW_FORMAT, sFormat)
                }

                Timber.i("#Video supported: %s (%s)\nsupported: %s\npreferred: %s",
                    locator, sFormat, vSize, CameraUtils.dimensionsToString(sizes))

                val count = sizes.size
                if (count == 0) continue

                // Saves supported video sizes
                val array = arrayOfNulls<Dimension>(count)
                sizes.toArray<Dimension>(array)
                SUPPORTED_SIZES = array

                // Surface format
                val formats = ArrayList<Format>()
                if (AndroidEncoder.isDirectSurfaceEnabled) {
                    // TODO: camera will not be detected if only surface format is reported
                    for (size in sizes) {
                        formats.add(VideoFormat(
                                Constants.ANDROID_SURFACE,
                                size,
                                Format.NOT_SPECIFIED,
                                Surface::class.java,
                                Format.NOT_SPECIFIED.toFloat()))
                    }
                }

                // Add only if YUV_420_888 or YV12 format is supported.  v2.8.0 supports only YUV_420_888
                // Note: YUV_420_888 is supported by camera2, and is mutually exclusive with YV12
                if (cameraFormats.contains(ImageFormat.YUV_420_888) || cameraFormats.contains(ImageFormat.YV12)) {
                    // Image formats
                    for (size in sizes) {
                        formats.add(YUVFormat(size,
                                Format.NOT_SPECIFIED,
                                Format.byteArray,
                                YUVFormat.YUV_420.toFloat(),
                                Format.NOT_SPECIFIED,
                                Format.NOT_SPECIFIED,
                                Format.NOT_SPECIFIED,
                                Format.NOT_SPECIFIED,
                                Format.NOT_SPECIFIED,
                                Format.NOT_SPECIFIED))
                    }
                    // 40x30, 176x144, 320x240, 352x288, 640x480,
                    // 704x576, 720x480, 720x576, 768x432, 1280x720
                    /*
                     * Format newFormat = new YUVFormat( //new Dimension(40,30), //new
                     * Dimension(176,144), //new Dimension(320,240), new Dimension(352,288), //new
                     * Dimension(640,480), //new Dimension(704,576), //new Dimension(720,480), //new
                     * Dimension(720,576), //new Dimension(768,432), //new Dimension(1280,720),
                     * Format.NOT_SPECIFIED, Format.byteArray, YUVFormat.YUV_420, Format.NOT_SPECIFIED,
                     * Format.NOT_SPECIFIED, Format.NOT_SPECIFIED, Format.NOT_SPECIFIED,
                     * Format.NOT_SPECIFIED, Format.NOT_SPECIFIED); formats.add(newFormat);
                     */
                }

                // Construct display name
                var name = if (facing == CameraCharacteristics.LENS_FACING_FRONT)
                    aTalkApp.getResString(R.string.service_gui_settings_CAMERA_FRONT)
                else
                    aTalkApp.getResString(R.string.service_gui_settings_CAMERA_BACK)
                name += " (AndroidCamera#$cameraId)"
                if (formats.isEmpty()) {
                    Timber.e("No supported formats reported by camera: %s", locator)
                    continue
                }
                val device = AndroidCamera(name, locator, formats.toTypedArray())
                CaptureDeviceManager.addDevice(device)
            }
            isCameraInitialized = true
        } catch (e: CameraAccessException) {
            Timber.w("Exception in AndroidCameraSystem init: %s", e.message)
        }
    }

    companion object {
        private const val VIDEO_SIZE = ".video.size"
        const val PREVIEW_FORMAT = ".preview.format"

        /**
         * Supported preview sizes by android camera for user selection
         */
        var SUPPORTED_SIZES = arrayOf<Dimension?>()
        var isCameraInitialized = false
        fun cleanMediaDB() {
            val prefixes = arrayOf(LOCATOR_PROTOCOL_MEDIARECORDER, LOCATOR_PROTOCOL_ANDROIDCAMERA)
            val cs = UtilActivator.configurationService!!
            for (prefix in prefixes) {
                val mediaProperties = cs.getPropertyNamesByPrefix(prefix, false)
                for (property in mediaProperties) {
                    cs.setProperty(property, null)
                }
            }
        }
    }
}