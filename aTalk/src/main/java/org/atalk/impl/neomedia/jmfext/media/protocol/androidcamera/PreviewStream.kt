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

import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import org.atalk.hmos.gui.call.VideoCallActivity
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.impl.neomedia.device.util.PreviewSurfaceProvider
import timber.log.Timber
import java.io.IOException
import java.util.*
import javax.media.Buffer
import javax.media.control.FormatControl

/**
 * The video stream captures frames using camera2 OnImageAvailableListener callbacks in YUV420_888 format
 * as input; and covert it from multi-plane to single plane. The output is transformed/rotated according
 * to the camera orientation See [.YUV420PlanarRotate].
 *
 * @author Eng Chong Meng
 */
class PreviewStream
/**
 * Creates a new instance of `PreviewStream`.
 *
 * @param dataSource parent `DataSource`.
 * @param formatControl format control used by this instance.
 */
(dataSource: DataSource?, formatControl: FormatControl) : CameraStreamBase(dataSource!!, formatControl) {
    /**
     * Buffers queue for camera2 YUV420_888 multi plan image buffered data
     */
    private val bufferQueue = LinkedList<Image>()
    private var mSurfaceProvider: PreviewSurfaceProvider? = null

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun start() {
        super.start()
        startImpl()
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun stop() {
        super.stop()
        // close the local video preview surface
        if (mSurfaceProvider != null) mSurfaceProvider!!.onObjectReleased()
    }

    /**
     * {@inheritDoc}
     * aTalk native camera acquired YUV420 preview is always in landscape mode
     */
    override fun onInitPreview() {
        try {
            /*
             * set up the target surfaces for local video preview display; Before calling obtainObject(),
             * must setViewSize() for use in surfaceHolder.setFixedSize() on surfaceCreated
             * Then only set the local previewSurface size by calling initLocalPreviewContainer()
             * Note: Do not change the following execution order
             */
            val videoFragment = VideoCallActivity.getVideoFragment()
            mSurfaceProvider = videoFragment!!.localPreviewSurface
            mSurfaceProvider!!.videoSize = optimizedSize
            Timber.d("Set surfaceSize (PreviewStream): %s", optimizedSize)
            val surfaceHolder = mSurfaceProvider!!.obtainObject() // this will create the surfaceView
            videoFragment.initLocalPreviewContainer(mSurfaceProvider)
            val previewSurface = surfaceHolder!!.surface

            // Setup ImageReader to retrieve image data for remote video streaming; maxImages = 3 and acquireLatestImage();
            // to fix problem with android camera2 API implementation in throwing waitForFreeSlotThenRelock on fast android devices.
            mImageReader = ImageReader.newInstance(optimizedSize.width, optimizedSize.height, ImageFormat.YUV_420_888, 3)
            mImageReader!!.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler)

            // Need to add both the surface and the ImageReader surface as targets to the preview capture request:
            mCaptureBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mCaptureBuilder!!.addTarget(previewSurface)
            mCaptureBuilder!!.addTarget(mImageReader!!.surface)
            // For picture taking only
            // mPreviewRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, mSensorOrientation);
            mCameraDevice!!.createCaptureSession(listOf(previewSurface, mImageReader!!.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            mCaptureSession = session
                            updateCaptureRequest()
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Timber.e("Camera capture session config failed: %s", session)
                        }
                    }, null)
        } catch (e: CameraAccessException) {
            Timber.e("Camera capture session create exception: %s", e.message)
        }
    }

    /**
     * Update the camera capture request.
     * Start the camera capture session with repeating request for smoother video streaming.
     */
    override fun updateCaptureRequest() {
        // The camera is already closed, so abort
        if (null == mCameraDevice) {
            Timber.e("Camera capture session config - camera closed, return")
            return
        }
        try {
            // Auto focus should be continuous for camera preview.
            mCaptureBuilder!!.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            mCaptureSession!!.setRepeatingRequest(mCaptureBuilder!!.build(), null, mBackgroundHandler)
            // Has sluggish video streaming performance, do not use
            // mCaptureSession.capture(mPreviewBuilder.build(), null, mBackgroundHandler);
            inTransition = false
        } catch (e: CameraAccessException) {
            Timber.e("Update capture request exception: %s", e.message)
        }
    }

    /**
     * To fix problem with android camera2 API implementation in throwing waitForFreeSlotThenRelock on fast android devices;
     * Setup ImageReader to retrieve image data for remote video streaming; maxImages = 3 and acquireLatestImage();
     * Use try wth resource in reader.acquireLatestImage() for any IllegalStateException;
     * Call #close to release buffer before camera can acquiring more.
     *
     * Note: The acquired image is always in landscape mode e.g. 1280x720.
     */
    private val mOnImageAvailableListener = OnImageAvailableListener { reader: ImageReader ->
        if (!inTransition) {
            try {
                reader.acquireLatestImage().use { image ->
                    if (image != null && ImageFormat.YUV_420_888 == image.format) {
                        // Seem to be cleaner without reading the image data; so skip below code
                        // if (inTransition) {
                        //     Timber.w("Discarded acquired image in transition @ ImageReader!");
                        //     image.close();
                        //     return;
                        // }
                        if (TimberLog.isTraceEnable) calcStats() // Calculate statistics for average frame rate if enable
                        synchronized(bufferQueue) { bufferQueue.addFirst(image) }
                        mTransferHandler!!.transferData(this)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "OnImage available exception: %s", e.message)
            }
        }
    }

    /**
     * Pop the oldest image in the bufferQueue for processing; i.e.
     * transformation and copy into the buffer for remote video data streaming
     *
     * Note: Sync problem between device rotation with new swap/flip; inTransition get clear with old image data in process.
     * (PreviewStream.java:188)#lambda$new$0$PreviewStream: OnImage available exception: index=345623 out of bounds (limit=345600)
     *
     * @param buffer streaming data buffer to be filled
     * @throws IOException on image buffer not accessible
     */
    @Throws(IOException::class)
    override fun read(buffer: Buffer) {
        var image: Image
        synchronized(bufferQueue) { image = bufferQueue.removeLast() }
        if (inTransition) {
            Timber.w("Discarded acquired image in transition @ packet read!")
            buffer.isDiscard = true
        } else {
            // Camera actual preview dimension may not necessary be same as set remote video format when rotated
            val w = mFormat!!.size.width
            val h = mFormat!!.size.height
            val outLen = w * h * 12 / 8

            // Set the buffer timeStamp before YUV processing, as it may take some times
            // On J7: Timestamp seems implausible relative to expectedPresent if performs after the process
            buffer.format = mFormat
            buffer.length = outLen
            buffer.flags = Buffer.FLAG_LIVE_DATA or Buffer.FLAG_RELATIVE_TIME
            buffer.timeStamp = System.currentTimeMillis()
            val copy = AbstractCodec2.validateByteArraySize(buffer, outLen, false)
            try {
                YUV420PlanarRotate(image, copy, w, h)
            } catch (e: Exception) {
                Timber.w("YUV420Planar Rotate exception: %s", e.message)
                buffer.isDiscard = true
            }
        }
        image.close()
    }

    /**
     * http://www.wordsaretoys.com/2013/10/25/roll-that-camera-zombie-rotation-and-coversion-from-yv12-to-yuv420planar/
     * Original code has been modified for camera2 UV420_888 and optimised for aTalk rotation without stretching the image
     *
     * Transform android YUV420_888 image orientation according to camera orientation.
     * ## Swap: means swapping the x & y coordinates, which provides a 90-degree anticlockwise rotation,
     * ## Flip: means mirroring the image for a 180-degree rotation, adjusted for inversion by for camera2
     * Note: Android does have condition with Swap && Flip in display orientation
     *
     * @param image input image with multi-plane YUV428_888 format.
     * @param width final output stream image width.
     * @param height final output stream image height.
     */
    protected fun YUV420PlanarRotate(image: Image, output: ByteArray, width: Int, height: Int) {
        // Init w * h parameters: Assuming input preview buffer dimension is always in landscape mode
        val wi = width - 1
        val hi = height - 1

        // Output buffer: I420uvSize is a 1/4 of the Y size
        val ySize = width * height
        val I420uvSize = ySize shr 2

        // Input image buffer parameters
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val yRowStride = image.planes[0].rowStride
        val yPixelStride = image.planes[0].pixelStride

        // The U/V planes are guaranteed to have the same row stride and pixel stride.
        val uvRowStride = image.planes[1].rowStride
        val uvPixelStride = image.planes[1].pixelStride

        // Performance input to output buffer transformation iterate over output buffer;
        // input index xi & yi are transformed according to swap & flip
        for (yo in 0 until height) {
            for (xo in 0 until width) {
                // default input index for direct 1:1 transform
                var xi = xo
                var yi = yo

                // The video frame: w and h are swapped at input frame - no image stretch required
                if (mSwap && mFlip) {
                    xi = yo
                    yi = wi - xo
                } else if (mSwap) {
                    xi = hi - yo
                    yi = xo
                } else if (mFlip) {
                    xi = wi - xi
                    yi = hi - yi
                }
                // Transform Y luminous data bytes from input to output
                output[width * yo + xo] = yBuffer[yRowStride * yi + yPixelStride * xi]

                /*
                 * ## Transform UV data bytes - UV has only 1/4 of Y bytes:
                 * To locate a pixel in these planes, divide all the xi and yi coordinates by two;
                 * and using the UV parameters i.e. uvRowStride and uvPixelStride
                 */
                if (yo % 2 + xo % 2 == 0) // 1 UV byte for 2x2 Y data bytes
                {
                    val uv420YIndex = ySize + (width shr 1) * (yo shr 1)
                    val uo = uv420YIndex + (xo shr 1)
                    val vo = I420uvSize + uo
                    val uvIdx = uvRowStride * (yi shr 1) + uvPixelStride * (xi shr 1)
                    output[uo] = uBuffer[uvIdx] // Cb (U)
                    output[vo] = vBuffer[uvIdx] // Cr (V)
                }
            }
        }
    }
}