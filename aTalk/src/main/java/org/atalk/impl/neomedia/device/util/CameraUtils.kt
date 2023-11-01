/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device.util

import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.text.TextUtils
import android.util.Size
import android.view.Surface
import org.atalk.hmos.aTalkApp
import org.atalk.impl.neomedia.device.DeviceConfiguration
import timber.log.Timber
import java.awt.Dimension

/**
 * Utility methods for operations on `Camera` objects. Also shares preview surface provider
 * between `MediaRecorder` and `AndroidCamera` device systems.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
object CameraUtils {
    /**
     * Separator use when save camera formats to DB. Do not change
     */
    private const val FORMAT_SEPARATOR = ", "

    /**
     * Surface provider used to display camera preview
     */
    private var surfaceProvider: PreviewSurfaceProvider? = null

    /**
     * The list of sizes from which the first supported by the respective [Camera] is to be
     * chosen as the size of the one and only `Format` supported by the associated
     * `MediaRecorder` `CaptureDevice`.
     *
     * User selectable video resolution. The actual resolution use during video call is adjusted so
     * it is within device capability [ Any strides paddings if required, is properly handled in][.getOptimalPreviewSize]
     */
    val PREFERRED_SIZES = DeviceConfiguration.SUPPORTED_RESOLUTIONS

    /**
     * Map contains all the phone available cameras and their supported resolution sizes
     * This list is being update at the device start up in.
     *
     * @see org.atalk.impl.neomedia.device.MediaRecorderSystem
     */
    private val cameraSupportSize = HashMap<String, Array<Size>>()

    /**
     * Returns `true` if given `size` is on the list of preferred sizes.
     *
     * @param size the size to check.
     * @return `true` if given `size` is on the list of preferred sizes.
     */
    fun isPreferredSize(size: Dimension): Boolean {
        for (s in PREFERRED_SIZES) {
            if (s.width == size.width && s.height == size.height) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun isPreferredSize(size: Size): Boolean {
        for (s in PREFERRED_SIZES) {
            if (s.width == size.width && s.height == size.height) {
                return true
            }
        }
        return false
    }

    /**
     * Constructs a `String` representation of a specific `Iterable` of
     * `Size`s. The elements of the specified `Iterable` are delimited by
     * &quot;, &quot;. The method has been introduced because the `Camera.Size` class does
     * not provide a `String` representation which contains the `width` and the
     * `height` in human-readable form.
     *
     * @param sizes the `Iterable` of `Size`s which is to be represented as a
     * human-readable `String`
     * @return the human-readable `String` representation of the specified `sizes`
     */
    fun cameraSizesToString(sizes: Iterable<Size>): String {
        val s = StringBuilder()
        for (size in sizes) {
            if (s.length != 0) s.append(", ")
            s.append(size.toString())
        }
        return s.toString()
    }

    @JvmStatic
    fun cameraSizesToString(sizes: Array<Size>): String {
        val s = StringBuilder()
        for (size in sizes) {
            if (s.length != 0) s.append(", ")
            s.append(size.toString())
        }
        return s.toString()
    }

    /**
     * Constructs a `String` representation of a specific `Iterable` of
     * `Dimension`s. The elements of the specified `Iterable` are delimited by &quot;,
     * &quot;. The method has been introduced to match [CameraUtils.cameraSizesToString].
     *
     * @param sizes the `Iterable` of `Dimension`s which is to be represented as a
     * human-readable `String`
     * @return the human-readable `String` representation of the specified `sizes`
     */
    @JvmStatic
    fun dimensionsToString(sizes: Iterable<Dimension>): String {
        val s = StringBuilder()
        for (size in sizes) {
            if (s.length != 0) s.append(", ")
            s.append(size.width).append('x').append(size.height)
        }
        return s.toString()
    }

    /**
     * Returns the string representation of the formats contained in given list.
     *
     * @param formats the list of image formats integers defined in `ImageFormat` class.
     * @return the string representation of the formats contained in given list.
     */
    @JvmStatic
    fun cameraImgFormatsToString(formats: List<Int>): String {
        val s = StringBuilder()
        for (format in formats) {
            if (s.length != 0) s.append(FORMAT_SEPARATOR)
            when (format) {
                ImageFormat.YV12 -> s.append("YV12")
                ImageFormat.NV21 -> s.append("NV21")
                ImageFormat.NV16 -> s.append("NV16")
                ImageFormat.YUY2 -> s.append("YUY2")
                ImageFormat.RGB_565 -> s.append("RGB_565")
                ImageFormat.JPEG -> s.append("JPEG")
                ImageFormat.RAW_SENSOR -> s.append("RAW_SENSOR")
                ImageFormat.PRIVATE -> s.append("PRIVATE")
                ImageFormat.YUV_420_888 -> s.append("YUV_420_888")
                else -> s.append(format)
            }
        }
        return s.toString()
    }

    @JvmStatic
    fun stringToCameraFormat(sFormat: String?): List<Int> {
        val sFormats = ArrayList<Int>()
        if (!TextUtils.isEmpty(sFormat)) {
            val pfs = sFormat!!.split(FORMAT_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (cfx in pfs) {
                when (cfx) {
                    "YV12" -> sFormats.add(ImageFormat.YV12)
                    "NV21" -> sFormats.add(ImageFormat.NV21)
                    "JPEG" -> sFormats.add(ImageFormat.JPEG)
                    "RAW_SENSOR" -> sFormats.add(ImageFormat.RAW_SENSOR)
                    "PRIVATE" -> sFormats.add(ImageFormat.PRIVATE) //API-23
                    "YUV_420_888" -> sFormats.add(ImageFormat.YUV_420_888)
                    else -> try {
                        sFormats.add(Integer.valueOf(cfx))
                    } catch (nfe: NumberFormatException) {
                        Timber.w("Number Format Exception in Camera Format: %s", cfx)
                    }
                }
            }
        }
        return sFormats
    }

    /**
     * Sets the [PreviewSurfaceProvider] that will be used with camera
     *
     * @param provider the surface provider to set
     */
    fun setPreviewSurfaceProvider(provider: PreviewSurfaceProvider?) {
        surfaceProvider = provider
    }

    /**
     * Calculates preview orientation for the [android.view.Display]'s `rotation`
     * in degrees for the selected cameraId, also taking into account of the device orientation.
     *
     * valid camera orientation: 0 or 90
     * valid displayRotation: 0, 90, 180
     *
     * @return camera preview orientation value in degrees that can be used to adjust the preview orientation
     */
    fun getPreviewOrientation(cameraId: String): Int {
        // rotation current {@link android.view.Display} rotation value.
        val displayRotation = surfaceProvider!!.displayRotation
        var previewOrientation = 0
        try {
            val cameraManager = aTalkApp.cameraManager
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
            var degrees = 0
            when (displayRotation) {
                Surface.ROTATION_0 -> degrees = 0
                Surface.ROTATION_90 -> degrees = 90
                Surface.ROTATION_180 -> degrees = 180
                Surface.ROTATION_270 -> degrees = 270
            }

            // front-facing camera
            if (CameraCharacteristics.LENS_FACING_FRONT == facing) {
                previewOrientation = (sensorOrientation + degrees) % 360
                previewOrientation = (360 - previewOrientation) % 360 // compensate for the mirroring
            } else {
                previewOrientation = (sensorOrientation - degrees + 360) % 360
            }
        } catch (e: CameraAccessException) {
            Timber.e("Camera Access Exception: %s", e.message)
        }
        return previewOrientation
    }

    /**
     * Get the optimize size that is supported by the camera resolution capability
     * closely match to the preview size requested.
     * Note: Camera native natural orientation is always in landscape mode
     *
     * @param previewSize requested preview size
     * @param sizes List of camera supported sizes
     * @return optimized preview size based on camera capability
     */
    fun getOptimalPreviewSize(previewSize: Dimension, sizes: Array<Size>?): Dimension {
        if (sizes == null) return previewSize
        val w = previewSize.width
        val h = previewSize.height
        val maxH = sizes[0].height
        val ASPECT_TOLERANCE = 0.05
        val targetRatio = w.toDouble() / h
        var optimalSize: Size? = null
        var minDiff = Double.MAX_VALUE

        // obtain the highest possible resolution
        for (size in sizes) {
            val ratio = size.width.toDouble() / size.height
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue
            if (Math.abs(size.height - h) < minDiff) {
                optimalSize = size
                minDiff = Math.abs(size.height - h).toDouble()
            }
        }
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE
            optimalSize = Size(w, h)
            for (size in sizes) {
                if (Math.abs(size.height - h) < minDiff) {
                    optimalSize = size
                    minDiff = Math.abs(size.height - h).toDouble()
                }
            }
        }
        return Dimension(optimalSize!!.width, optimalSize.height)
    }

    /**
     * Store the supported video resolution by camera cameraId
     *
     * @param cameraId camera ID
     * @param sizes list of camera support video resolutions
     */
    @JvmStatic
    fun setCameraSupportSize(cameraId: String, sizes: Array<Size>) {
        cameraSupportSize[cameraId] = sizes
    }

    /**
     * Get the list of camera video resolutions supported by cameraId
     *
     * @param cameraId the request camera Id resolutions
     * @return List of camera video resolutions supported by cameraId
     */
    fun getSupportSizeForCameraId(cameraId: String): Array<Size> {
        return cameraSupportSize[cameraId]!!
    }

    @JvmStatic
    fun getSupportedSizes(vs: String?, sizes: MutableList<Dimension>): Boolean {
        if (!TextUtils.isEmpty(vs)) {
            val videoSizes = vs!!.split(FORMAT_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (videoSize in videoSizes) {
                if (!TextUtils.isEmpty(videoSize) && videoSize.contains("x")) {
                    val wh = videoSize.split("x".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val candidate = Dimension(wh[0].toInt(), wh[1].toInt())
                    if (isPreferredSize(candidate)) {
                        sizes.add(candidate)
                    }
                }
            }
            return true
        }
        return false
    }
}