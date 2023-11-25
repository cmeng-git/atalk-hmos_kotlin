/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device.util

import android.hardware.camera2.CameraCharacteristics
import org.atalk.impl.neomedia.NeomediaActivator
import org.atalk.impl.neomedia.device.DeviceConfiguration
import org.atalk.service.neomedia.MediaUseCase
import timber.log.Timber
import javax.media.CaptureDeviceInfo
import javax.media.Format
import javax.media.MediaLocator

/**
 * Class used to represent camera device in Android device systems.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AndroidCamera
/**
 * Creates a new instance of `AndroidCamera`
 *
 * @param name human readable name of the camera e.g. front camera.
 * @param locator the `MediaLocator` identifying the camera and it's system.
 * @param formats list of supported formats.
 */
(name: String?, locator: MediaLocator?, formats: Array<Format?>?) : CaptureDeviceInfo(name, locator, formats) {
    /**
     * Returns the protocol part of `MediaLocator` that identifies camera device system.
     *
     * @return the protocol part of `MediaLocator` that identifies camera device system.
     */
    val cameraProtocol: String
        get() = locator.protocol

    /**
     * Returns camera facing direction.
     *
     * @return camera facing direction.
     */
    val cameraFacing: Int
        get() = getCameraFacing(locator)

    // Used for sorting in ascending order CaptureDeviceInfo by name
    internal class SortByName : Comparator<CaptureDeviceInfo> {
        override fun compare(a: CaptureDeviceInfo, b: CaptureDeviceInfo): Int {
            return a.name.compareTo(b.name)
        }
    }

    companion object {
        /**
         * The facing of the camera is opposite to that of the screen.
         */
        const val FACING_BACK = CameraCharacteristics.LENS_FACING_FRONT

        /**
         * The facing of the camera is the same as that of the screen.
         */
        const val FACING_FRONT = CameraCharacteristics.LENS_FACING_FRONT

        /**
         * Creates `MediaLocator` for given parameters.
         *
         * @param locatorProtocol locator protocol that identifies device system.
         * @param cameraId ID of camera that identifies the camera.
         * @param facing direction of the corresponding to given `cameraId`.
         * @return camera `MediaLocator` for given parameters.
         */
        @JvmStatic
        fun constructLocator(locatorProtocol: String, cameraId: String, facing: Int): MediaLocator {
            return MediaLocator("$locatorProtocol:$cameraId/$facing")
        }

        /**
         * Extracts camera id from given `locator`.
         *
         * @param locator the `MediaLocator` that identifies the camera.
         * @return extracted camera id from given `locator`.
         */
        fun getCameraId(locator: MediaLocator): String {
            val remainder = locator.remainder
            return remainder.substring(0, remainder.indexOf("/"))
        }

        /**
         * Extracts camera facing from given `locator`.
         *
         * @param locator the `MediaLocator` that identifies the camera.
         * @return extracted camera facing from given `locator`.
         */
        fun getCameraFacing(locator: MediaLocator): Int {
            val remainder = locator.remainder
            return remainder.substring(remainder.indexOf("/") + 1).toInt()
        }// All cameras have to be of type AndroidCamera on Android

        /**
         * Returns array of cameras available in the system.
         *
         * @return array of cameras available in the system.
         */
        val cameras: Array<AndroidCamera?>
            get() {
                val devConfig: DeviceConfiguration = NeomediaActivator.getMediaServiceImpl()!!.deviceConfiguration
                val videoDevices = devConfig.getAvailableVideoCaptureDevices(MediaUseCase.CALL)
                videoDevices.sortWith(SortByName())

                val cameras = arrayOfNulls<AndroidCamera>(videoDevices.size)
                for (i in videoDevices.indices) {
                    val device = videoDevices[i]
                    // All cameras have to be of type AndroidCamera on Android
                    cameras[i] = device as AndroidCamera
                }
                return cameras
            }

        /**
         * Finds camera with given `facing` from the same device system as currently selected camera.
         *
         * @param facing the facing direction of camera to find.
         * @return camera with given `facing` from the same device system as currently selected camera.
         */
        fun getCameraFromCurrentDeviceSystem(facing: Int): AndroidCamera? {
            val currentCamera = selectedCameraDevInfo
            val currentProtocol = currentCamera?.cameraProtocol
            val cameras = cameras
            for (camera in cameras) {
                // Match facing
                if (camera!!.cameraFacing == facing) {
                    // Now match the protocol if any
                    if (currentProtocol != null) {
                        if (currentProtocol == camera.cameraProtocol) {
                            return camera
                        }
                    } else {
                        return camera
                    }
                }
            }
            return null
        }

        /**
         * Returns device info of the currently selected camera.
         *
         * @return device info of the currently selected camera.
         */
        val selectedCameraDevInfo: AndroidCamera?
            get() {
                val mediaServiceImpl = NeomediaActivator.getMediaServiceImpl()
                return if (mediaServiceImpl == null)
                    null
                else
                    mediaServiceImpl.deviceConfiguration.getVideoCaptureDevice(MediaUseCase.CALL) as AndroidCamera?
            }

        /**
         * Selects the camera identified by given locator to be used by the system.
         *
         * @param cameraLocator camera device locator that will be used.
         */
        fun setSelectedCamera(cameraLocator: MediaLocator): AndroidCamera? {
            val devConfig: DeviceConfiguration = NeomediaActivator.getMediaServiceImpl()!!.deviceConfiguration
            val videoDevices = devConfig.getAvailableVideoCaptureDevices(MediaUseCase.CALL)
            var selectedCamera: AndroidCamera? = null
            for (deviceInfo in videoDevices) {
                if (deviceInfo.locator == cameraLocator) {
                    selectedCamera = deviceInfo as AndroidCamera
                    break
                }
            }
            return if (selectedCamera != null) {
                devConfig.setVideoCaptureDevice(selectedCamera, true)
                selectedCamera
            } else {
                Timber.w("No camera found for name: %s", cameraLocator)
                null
            }
        }
    }
}