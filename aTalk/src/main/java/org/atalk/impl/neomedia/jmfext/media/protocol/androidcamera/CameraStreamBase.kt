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
package org.atalk.impl.neomedia.jmfext.media.protocol.androidcamera

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.CameraDevice.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.call.VideoCallActivity
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.NeomediaServiceUtils
import org.atalk.impl.neomedia.device.util.AndroidCamera
import org.atalk.impl.neomedia.device.util.CameraUtils
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferStream
import timber.log.Timber
import java.awt.Dimension
import java.io.IOException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.media.Format
import javax.media.MediaLocator
import javax.media.control.FormatControl
import javax.media.format.VideoFormat

/**
 * Base class for android camera streams.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
abstract class CameraStreamBase internal constructor(  // protected ViewDependentProvider<?> mPreviewSurfaceProvider;
        dataSource: DataSource, formatControl: FormatControl) : AbstractPushBufferStream<DataSource?>(dataSource, formatControl) {
    /**
     * ID of the current [CameraDevice].
     */
    private var mCameraId: String

    /**
     * The fixed properties for a given CameraDevice, and can be queried through the CameraManager interface
     * with CameraManager.getCameraCharacteristics.
     */
    private var mCameraCharacteristics: CameraCharacteristics? = null

    /**
     * In use camera rotation, adjusted for camera lens facing direction  - for video streaming
     */
    protected var mSensorOrientation = 0

    /**
     * In use camera rotation, adjusted for camera lens facing direction  and device orientation - for video streaming
     */
    private var mPreviewOrientation = 0

    /**
     * [CaptureRequest.Builder] for the camera preview
     */
    protected var mCaptureBuilder: CaptureRequest.Builder? = null

    /**
     * A [CameraCaptureSession] for camera preview.
     */
    protected var mCaptureSession: CameraCaptureSession? = null

    /**
     * An [ImageReader] that handles still image capture.
     */
    protected var mImageReader: ImageReader? = null

    /**
     * Format of this stream: must use a clone copy of the reference format given in streamFormats[];
     * i.e. mFormat = (VideoFormat) streamFormats[0].clone(); otherwise mFormat.setVideoSize(mPreviewSize) will change
     * the actual item in the formatControl.getSupportedFormats(); causing problem in VideoMediaStreamImpl#selectVideoSize()
     * to fail with no matched item, and androidCodec to work on first instance only
     */
    protected var mFormat: VideoFormat? = null

    /**
     * Best closer match for the user selected to the camera available resolution
     */
    protected lateinit var optimizedSize: Dimension

    /**
     * Final previewSize (with respect to orientation) use for streaming
     */
    protected var mPreviewSize: Dimension? = null

    /**
     * The swap and flip state for the preview transformation for video streaming
     */
    protected var mSwap = true
    protected var mFlip = true

    /**
     * Flag indicates the system is in the process of shutting down the camera and ImageReader:
     * Do not access the ImageReader else: https://issuetracker.google.com/issues/203238264
     */
    protected var inTransition = true

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var backgroundThread: HandlerThread? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    protected var mBackgroundHandler: Handler? = null

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val mCameraOpenCloseLock = Semaphore(1)

    /**
     * Fps statistics
     */
    private var last = System.currentTimeMillis()
    private val avg = LongArray(10)
    private var idx = 0


    /**
     * Create a new instance of `CameraStreamBase`.
     *
     * parent parent `DataSource`.
     * formatControl format control used by this stream.
     */
    init {
        mCameraId = AndroidCamera.getCameraId(dataSource.locator)
        mInstance = this
    }

    /**
     * Method should be called by extending classes in order to start the camera.
     * Obtain optimized dimension from the device supported preview sizes with the given desired size.
     * a. always set camera preview captured dimension in its native orientation (landscape) - otherwise may not be supported.
     * b. Local preview dimension must follow current display orientation to maintain image aspect ratio
     * and width and height is interchanged if necessary. Transformation of preview stream video for sending
     * is carried out in:
     *
     * @throws IOException IO exception
     * @see PreviewStream.YUV420PlanarRotate
     */
    @SuppressLint("MissingPermission")
    @Throws(IOException::class)
    protected fun startImpl() {
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }

            // Get user selected default video resolution
            val deviceConfig = NeomediaServiceUtils.mediaServiceImpl!!.deviceConfiguration
            val videoSize = deviceConfig.getVideoSize()
            val cameraManager = aTalkApp.cameraManager
            mCameraCharacteristics = cameraManager.getCameraCharacteristics(mCameraId)
            val map = mCameraCharacteristics!!.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: throw RuntimeException("Cannot get camera available characteristics!")
            startBackgroundThread()

            // Find optimised video resolution with user selected against device support image format sizes
            val supportedPreviewSizes = map.getOutputSizes(ImageFormat.YUV_420_888)
            optimizedSize = CameraUtils.getOptimalPreviewSize(videoSize, supportedPreviewSizes)
            val streamFormats = streamFormats
            mFormat = streamFormats[0]!!.clone() as VideoFormat
            Timber.d("Camera data stream format #2: %s=>%s", videoSize, mFormat)
            initPreviewOrientation(true)
            cameraManager.openCamera(mCameraId, mStateCallback, mBackgroundHandler)
        } catch (e: SecurityException) {
            Timber.e("openCamera: %s", e.message)
        } catch (e: CameraAccessException) {
            Timber.e("openCamera: Cannot access the camera.")
        } catch (e: NullPointerException) {
            Timber.e("Camera2API is not supported on the device.")
        } catch (e: InterruptedException) {
            // throw new RuntimeException("Interrupted while trying to lock camera opening.");
            Timber.e("Exception in start camera init: %s", e.message)
        }
    }

    /**
     * Update swap and flip for YUV420PlanarRotate();
     * Set local preview display orientation according to device rotation and sensor orientation
     * Currently android phone device has 90/270 for back and front cameras native orientation
     *
     * Note: valid Sensor orientations: 0, 90, 270; 180 is not reported by android camera sensors
     *
     * @param initFormat Sending video orientation always in upright position when set to true;
     * Set to false on device rotation requires the remote device to rotate accordingly to view image upright
     */
    private fun initPreviewOrientation(initFormat: Boolean) {
        // Set preview display orientation according to device rotation
        mPreviewOrientation = if (initFormat) {
            CameraUtils.getPreviewOrientation(mCameraId)
        } else (
            mCameraCharacteristics!!.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
        )

        // Streaming video always send in the user selected dimension and image view in upright orientation
        mSwap = mPreviewOrientation == 90 || mPreviewOrientation == 270
        mPreviewSize = if (mSwap) {
            Dimension(optimizedSize.height, optimizedSize.width)
        } else {
            optimizedSize
        }
        mFormat!!.setVideoSize(mPreviewSize)

        // front-facing camera; take care android flip the video for front facing lens camera
        val facing = mCameraCharacteristics!!.get(CameraCharacteristics.LENS_FACING)
        mFlip = if (CameraCharacteristics.LENS_FACING_FRONT == facing) {
            (mPreviewOrientation == 180 || !aTalkApp.isPortrait) && mPreviewOrientation == 270
        } else {
            (mPreviewOrientation == 90 || mPreviewOrientation == 180)
        }
        Timber.d("Camera preview orientation: %s; portrait: %s; swap: %s; flip: %s; format: %s",
                mPreviewOrientation, aTalkApp.isPortrait, mSwap, mFlip, mFormat)
    }

    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its status.
     */
    private val mStateCallback = object : StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            mCameraDevice = cameraDevice
            onInitPreview()
            mCameraOpenCloseLock.release()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            mCameraDevice!!.close()
            mCameraDevice = null
            mCameraOpenCloseLock.release()
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            val errMessage = when (error) {
                ERROR_CAMERA_IN_USE -> "Camera in use"
                ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                ERROR_CAMERA_DISABLED -> "Device policy"
                ERROR_CAMERA_DEVICE -> "Fatal (device)"
                ERROR_CAMERA_SERVICE -> "Fatal (service)"
                else -> "UnKnown"
            }
            Timber.e("Set camera preview failed: %s", errMessage)
            aTalkApp.showGenericError(R.string.service_gui_DEVICE_VIDEO_FORMAT_NOT_SUPPORTED, mPreviewSize, errMessage)
            mCameraDevice!!.close()
            mCameraDevice = null
            mCameraOpenCloseLock.release()
        }
    }

    /**
     * Method called before camera preview is started. Extending classes should configure preview at this point.
     */
    protected abstract fun onInitPreview()

    /**
     * Method called to start camera image capture.
     */
    protected abstract fun updateCaptureRequest()// Timber.d("getStreamFormats Idx: %s/%s; format: %s", i, count, format);

    /**
     * Selects stream formats.
     *
     * @return stream formats.
     */
    private val streamFormats: Array<Format?>
        get() {
            val formatControls = dataSource!!.formatControls
            val count = formatControls.size
            val streamFormats = arrayOfNulls<Format>(count)
            for (i in 0 until count) {
                val formatControl = formatControls[i]
                var format = formatControl.format
                if (format == null) {
                    val supportedFormats = formatControl.supportedFormats
                    if (supportedFormats != null && supportedFormats.isNotEmpty()) {
                        format = supportedFormats[0]
                    }
                }
                // Timber.d("getStreamFormats Idx: %s/%s; format: %s", i, count, format);
                streamFormats[i] = format
            }
            return streamFormats
        }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun stop() {
        closeCamera()
        stopBackgroundThread()
        super.stop()
    }

    /**
     * Closes the current [CameraDevice].
     */
    private fun closeCamera() {
        if (mCameraDevice != null) {
            try {
                inTransition = true
                mCameraOpenCloseLock.acquire()
                if (null != mCaptureSession) {
                    mCaptureSession!!.stopRepeating()
                    // mCaptureSession.abortCaptures();
                    mCaptureSession!!.close()
                    mCaptureSession = null
                }
                if (null != mCameraDevice) {
                    mCameraDevice!!.close()
                    mCameraDevice = null
                }

                // (PreviewStream.java:165): OnImage available exception: buffer is inaccessible
                if (null != mImageReader) {
                    mImageReader!!.close()
                    mImageReader = null
                }
            } catch (e: InterruptedException) {
                throw RuntimeException("Interrupted while trying to close camera.", e)
            } catch (e: CameraAccessException) {
                throw RuntimeException("Interrupted while trying to close camera.", e)
            } finally {
                mCameraOpenCloseLock.release()
            }
        }
    }

    /**
     * Triggered on device rotation to init the remote video orientation sending;
     * initFormat == true has synchronised problem between imageReader data and YUV swap if not handled properly
     *
     * On Samsung J7 implementation, seems at times mCaptureSession and mCameraDevice can be null etc;
     * If exception happen, then reInit the whole camera sequence
     *
     * @param initFormat Sending video orientation always in upright position when set to true;
     * Set to false on device rotation requires the remote device to rotate accordingly to view image upright
     */
    fun initPreviewOnRotation(initFormat: Boolean) {
        if (initFormat) {
            inTransition = true
            try {
                if (null != mCaptureSession) {
                    mCaptureSession!!.stopRepeating()
                    // mCaptureSession.abortCaptures();
                }
                initPreviewOrientation(true)
                updateCaptureRequest()
                return
            } catch (e: Exception) {
                Timber.e("Close capture session exception: %s", e.message)
            }
            reInitCamera()
        } else {
            initPreviewOrientation(false)
        }
    }

    private fun reInitCamera() {
        val videoFragment = VideoCallActivity.getVideoFragment()
        closeCamera()
        if (videoFragment.isLocalVideoEnabled()) {
            try {
                start()
            } catch (e: IOException) {
                aTalkApp.showToastMessage(R.string.service_gui_DEVICE_VIDEO_FORMAT_NOT_SUPPORTED, mCameraId, e.message)
            }
        }
    }

    /**
     * Switch to the user selected lens facing camera. Start data streaming only if local video is enabled
     * User needs to enable the local video to send the video stream to remote user.
     *
     * @param cameraLocator MediaLocator
     * @param isLocalVideoEnable true is local video is enabled for sending
     */
    fun switchCamera(cameraLocator: MediaLocator, isLocalVideoEnable: Boolean) {
        AndroidCamera.setSelectedCamera(cameraLocator)
        mCameraId = AndroidCamera.getCameraId(cameraLocator)

        // Stop preview and release the current camera if any before switching, otherwise app will crash
        Timber.d("Switching camera: %s", cameraLocator.toString())
        closeCamera()
        if (isLocalVideoEnable) {
            try {
                start()
            } catch (e: IOException) {
                aTalkApp.showToastMessage(R.string.service_gui_DEVICE_VIDEO_FORMAT_NOT_SUPPORTED, cameraLocator, e.message)
            }
        }
    }

    /**
     * Calculates fps statistics.
     *
     * @return time elapsed in millis between subsequent calls to this method.
     */
    protected fun calcStats(): Long {
        // Measure moving average
        val current = System.currentTimeMillis()
        val delay = current - last
        last = System.currentTimeMillis()
        avg[idx] = delay
        if (++idx == avg.size) idx = 0
        var movAvg = 0L
        for (anAvg in avg) {
            movAvg += anAvg
        }
        Timber.log(TimberLog.FINER, "Avg frame rate: %d", 1000 / (movAvg / avg.size))
        return delay
    }

    // ===============================================================
    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraBackground")
            backgroundThread!!.start()
            mBackgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    private fun stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread!!.quitSafely()
            try {
                backgroundThread!!.join()
                backgroundThread = null
                mBackgroundHandler = null
            } catch (e: InterruptedException) {
                Timber.e(e, "Stop background thread exception: %s", e.message)
            }
        }
    }

    companion object {
        @JvmStatic
        var mInstance: CameraStreamBase? = null
            private set

        fun getInstance() : CameraStreamBase? {
            return mInstance
        }

        /**
         * A reference to the opened [CameraDevice].
         */
        var mCameraDevice: CameraDevice? = null
    }
}