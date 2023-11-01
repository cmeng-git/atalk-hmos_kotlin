/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.androidcamera

import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.hardware.camera2.*
import android.view.Surface
import okhttp3.internal.notifyAll
import okhttp3.internal.wait
import org.atalk.hmos.gui.call.VideoCallActivity
import org.atalk.impl.neomedia.NeomediaServiceUtils
import org.atalk.impl.neomedia.device.util.CameraSurfaceRenderer
import org.atalk.impl.neomedia.device.util.CodecInputSurface
import org.atalk.impl.neomedia.device.util.OpenGLContext
import org.atalk.impl.neomedia.device.util.OpenGlCtxProvider
import org.atalk.service.osgi.OSGiActivity
import timber.log.Timber
import java.io.IOException
import javax.media.Buffer
import javax.media.Format
import javax.media.control.FormatControl

/**
 * Camera stream that uses `Surface` to capture video data. First input `Surface` is
 * obtained from `MediaCodec`. Then it is passed as preview surface to the camera init.
 * Note: `Surface` instance is passed through buffer objects in read method;
 * this stream #onInitPreview() won't start until it is provided.
 *
 * In order to display local camera preview in the app, `TextureView` is created in video
 * call `Activity`. It is used to create Open GL context that shares video texture and can
 * render it. Rendering is done here on camera capture `Thread`.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class SurfaceStream
/**
 * Creates a new instance of `SurfaceStream`.
 *
 * @param parent parent `DataSource`.
 * @param formatControl format control used by this instance.
 */
internal constructor(parent: DataSource?, formatControl: FormatControl) : CameraStreamBase(parent!!, formatControl), OnFrameAvailableListener {
    /**
     * `OpenGlCtxProvider` used by this instance.
     */
    private var myCtxProvider: OpenGlCtxProvider? = null

    /**
     * TextureView for local preview display
     */
    private var mDisplayTV: OpenGLContext? = null

    /**
     * Codec input surface obtained from `MediaCodec` for remote video streaming.
     */
    private var mEncoderSurface: CodecInputSurface? = null
    private var mSurfaceRender: CameraSurfaceRenderer? = null

    /**
     * SurfaceTexture that receives the output from the camera preview
     */
    private var mSurfaceTexture: SurfaceTexture? = null
    private var mPreviewSurface: Surface? = null

    /**
     * Capture thread.
     */
    private var captureThread: Thread? = null

    /**
     * Flag used to stop capture thread.
     */
    private var run = false

    /**
     * guards frameAvailable
     */
    private val frameSyncObject = Object()
    private var frameAvailable = false

    /**
     * Object used to synchronize local preview painting.
     */
    private val paintLock = Any()

    /**
     * Flag indicates that the local preview has been painted.
     */
    private var paintDone = false

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun start() {
        super.start()
        if (captureThread == null) startCaptureThread() else startImpl()
    }

    override fun getFormat(): Format {
        return mFormat as Format
    }

    // Start the captureThread
    private fun startCaptureThread() {
        run = true
        captureThread = object : Thread() {
            override fun run() {
                captureLoop()
            }
        }
        captureThread!!.start()
    }

    /**
     * Create all the renderSurfaces for the EGL drawFrame. The creation must be performed in the
     * same captureThread for GL and CameraSurfaceRenderer to work properly
     * Note: onInitPreview is executed in the mBackgroundHandler thread
     *
     * @param surface Encoder Surface object obtained from `MediaCodec` via read().
     * @see AndroidEncoder.configureMediaCodec
     */
    private fun initSurfaceConsumer(surface: Surface?) {
        // Get user selected default video resolution
        val deviceConfig = NeomediaServiceUtils.mediaServiceImpl!!.deviceConfiguration
        val videoSize = deviceConfig.getVideoSize()

        /*
         * Init the TextureView / SurfaceTexture for the local preview display:
         * Must setup the local preview container size before proceed to obtainObject;
         * Otherwise onSurfaceTextureAvailable()#SurfaceTexture will not have the correct aspect ratio;
         * and the local preview image size is also not correct even with setAspectRatio()
         *
         * Need to setViewSize() for use in initLocalPreviewContainer
         * Note: Do not change the following execution order
         */
        val videoFragment = VideoCallActivity.getVideoFragment()
        myCtxProvider = videoFragment!!.mLocalPreviewGlCtxProvider
        myCtxProvider!!.videoSize = videoSize
        videoFragment.initLocalPreviewContainer(myCtxProvider)
        mDisplayTV = myCtxProvider!!.obtainObject() // this will create a new TextureView

        // Init the encoder inputSurface for remote video streaming
        mEncoderSurface = CodecInputSurface(surface!!, mDisplayTV!!.context)
        mEncoderSurface!!.makeCurrent()

        // Init the surface for capturing the camera image for remote video streaming, and local preview display
        mSurfaceRender = CameraSurfaceRenderer()
        mSurfaceRender!!.surfaceCreated()
        mSurfaceTexture = SurfaceTexture(mSurfaceRender!!.textureId)
        mSurfaceTexture!!.setOnFrameAvailableListener(this)
        mPreviewSurface = Surface(mSurfaceTexture)
    }

    /**
     * {@inheritDoc}
     */
    override fun onInitPreview() {
        try {
            // Init capturing parameters for camera image for remote video streaming, and local preview display
            // @see also initSurfaceConsumer();
            // Need to do initLocalPreviewContainer here to take care of device orientation change
            // https://developer.android.com/reference/android/hardware/camera2/CameraDevice.html#createCaptureSession(android.hardware.camera2.params.SessionConfiguration)
            myCtxProvider!!.videoSize = optimizedSize
            VideoCallActivity.getVideoFragment()!!.initLocalPreviewContainer(myCtxProvider)
            mSurfaceTexture!!.setDefaultBufferSize(optimizedSize.width, optimizedSize.height)
            mCaptureBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mCaptureBuilder!!.addTarget(mPreviewSurface!!)
            Timber.d("Camera stream update preview: %s; %s; size %s (%s)", mFormat, mPreviewSurface, mPreviewSize, optimizedSize)

            // Has problem with this
            // mCaptureBuilder.addTarget(mEncoderSurface.getSurface());
            // mCameraDevice.createCaptureSession(Arrays.asList(mEncoderSurface.getSurface(), mPreviewSurface), //Collections.singletonList(mPreviewSurface),
            mCameraDevice!!.createCaptureSession(listOf(mPreviewSurface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            mCaptureSession = session
                            updateCaptureRequest()
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Timber.e("Camera capture session configure failed: %s", session)
                        }
                    }, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            Timber.w("Surface stream onInitPreview exception: %s", e.message)
        }
    }

    /**
     * Update the camera preview. [startPreview()][.] needs to be called in advance.
     */
    override fun updateCaptureRequest() {
        if (null == mCameraDevice) {
            Timber.e("Camera capture session config - camera closed, return")
            return
        }
        try {
            mCaptureBuilder!!.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            mCaptureSession!!.setRepeatingRequest(mCaptureBuilder!!.build(), null, mBackgroundHandler)
            inTransition = false
            // Timber.d("Camera stream update CaptureRequest: %s", mCaptureSession);
        } catch (e: CameraAccessException) {
            Timber.e("Update capture request exception: %s", e.message)
        }
    }

    /**
     * Capture thread loop.
     */
    private fun captureLoop() {
        // Wait for input surface to be returned before proceed
        // Post an empty frame to init encoder, and get the surface that is provided in read() method
        while (run && mCameraDevice == null) {
            mTransferHandler!!.transferData(this)
        }
        while (run) {
            // loop if camera switching is in progress or capture session is setting up
            if (mCaptureSession == null || inTransition) continue

            // Start the image acquire process from the surfaceView
            acquireNewImage()

            /*
             * Renders the preview on main thread for the local preview, return only on paintDone;
             */
            paintLocalPreview()
            val delay = calcStats()
            if (delay < 80) {
                try {
                    val wait = 80 - delay
                    // Timber.d("Delaying frame: %s", wait);
                    Thread.sleep(wait)
                } catch (e: InterruptedException) {
                    throw RuntimeException(e)
                }
            }

            /*
             * Push the received image frame to the android encoder; must be executed within onFrameAvailable
             * paintLocalPreview#mTextureRender.drawFrame(mSurfaceTexture) must not happen while in read();
             * else the new local preview video is streaming instead.
             */
            pushEncoderData()
        }
    }

    /**
     * Latches the next buffer into the texture. Must be called from the thread that created the OutputSurface object.
     * Wait for a max of 2.5s Timer
     */
    private fun acquireNewImage() {
        val TIMEOUT_MS = 2500

        // Timber.d("Waiting for onFrameAvailable!");
        synchronized(frameSyncObject) {
            while (!frameAvailable) {
                try {
                    // Wait for onFrameAvailable() to signal us. Use a timeout to avoid stalling the test if it doesn't arrive.
                    frameSyncObject.wait(TIMEOUT_MS.toLong())
                    if (!frameAvailable) {
                        throw RuntimeException("Camera frame wait timed out")
                    }
                } catch (ie: InterruptedException) {
                    throw RuntimeException(ie)
                }
            }
            frameAvailable = false
        }
        mSurfaceRender!!.checkGlError("before updateTexImage")
        mSurfaceTexture!!.updateTexImage()
    }

    /**
     * The SurfaceTexture uses this to signal the availability of a new frame.  The
     * thread that "owns" the external texture associated with the SurfaceTexture (which,
     * by virtue of the context being shared, *should* be either one) needs to call
     * updateTexImage() to latch the buffer i.e. the acquireNewImage in captureThread.
     *
     * @param st the SurfaceTexture that set for this callback
     */
    override fun onFrameAvailable(st: SurfaceTexture) {
        synchronized(frameSyncObject) {
            frameAvailable = true
            frameSyncObject.notifyAll()
        }
    }

    /**
     * Paints the local preview on UI thread by posting paint job and waiting for the UI handler to complete its job.
     */
    private fun paintLocalPreview() {
        paintDone = false
        OSGiActivity.uiHandler.post {
            try {
                // OpenGLContext mDisplayTV = myCtxProvider.tryObtainObject();
                /*
                 * Must wait until local preview frame is posted to the TextureSurface#onSurfaceTextureUpdated,
                 * otherwise we will freeze on trying to set the current context. We skip the frame in this case.
                 */
                if (!myCtxProvider!!.textureUpdated) {
                    Timber.w("Skipped preview frame, previewCtx: %s textureUpdated: %s",
                            mDisplayTV, myCtxProvider!!.textureUpdated)
                } else {
                    // myCtxProvider.configureTransform(myCtxProvider.getView().getWidth(), myCtxProvider.getView().getHeight());
                    mDisplayTV!!.makeCurrent()
                    mSurfaceRender!!.drawFrame(mSurfaceTexture!!)
                    mDisplayTV!!.swapBuffers()

                    /*
                     * If current context is not unregistered the main thread will freeze: at
                     * com.google.android.gles_jni.EGLImpl.eglMakeCurrent(EGLImpl.java:-1) at
                     * android.view.HardwareRenderer$GlRenderer.checkRenderContextUnsafe(HardwareRenderer.java:1767) at
                     * android.view.HardwareRenderer$GlRenderer.draw(HardwareRenderer.java:1438)
                     * at android.view.ViewRootImpl.draw(ViewRootImpl.java:2381) .... at
                     * com.android.internal.os.ZygoteInit.main(ZygoteInit.java:595) at
                     * dalvik.system.NativeStart.main(NativeStart.java:-1)
                     * cmeng: not required in camera2
                     */
                    // mDisplayTV.releaseEGLSurfaceContext();
                    myCtxProvider!!.textureUpdated = false
                }
            } finally {
                synchronized(paintLock) {
                    paintDone = true
                    paintLock.notifyAll()
                }
            }
        }

        // Wait for the main thread to finish painting before return to caller
        synchronized(paintLock) {
            if (!paintDone) {
                try {
                    paintLock.wait()
                } catch (e: InterruptedException) {
                    throw RuntimeException(e)
                }
            }
        }
    }

    /**
     * Pushes the received image frame to the android encoder; to be retrieve in ...
     *
     * @see .read
     */
    private fun pushEncoderData() {
        // Pushes the received image frame to the android encoder input surface
        // mEncoderSurface.makeCurrent();
        // myCtxProvider.configureTransform(mPreviewSize.width, mPreviewSize.height);
        mSurfaceRender!!.drawFrame(mSurfaceTexture!!)
        mEncoderSurface!!.setPresentationTime(mSurfaceTexture!!.timestamp)
        mEncoderSurface!!.swapBuffers()
        mTransferHandler!!.transferData(this)
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun read(buffer: Buffer) {
        val surface: Surface?

        if (mCaptureSession != null) {
            buffer.format = mFormat
            buffer.timeStamp = mSurfaceTexture!!.timestamp
        } else if (mCameraDevice == null) {
            surface = buffer.data as Surface?
            if (surface != null) {
                Timber.d("Retrieve android encoder surface: %s", surface)
                initSurfaceConsumer(surface)
                startImpl()
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun stop() {
        run = false
        if (captureThread != null) {
            captureThread = try {
                captureThread!!.join()
                null
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }
        super.stop()
        if (mSurfaceRender != null) {
            mSurfaceRender!!.release()
            mSurfaceRender = null
        }
        if (mSurfaceTexture != null) {
            mSurfaceTexture!!.setOnFrameAvailableListener(null)
            mSurfaceTexture!!.release()
            mSurfaceTexture = null
        }

        // null if the graph realization cannot proceed due to unsupported codec
        if (myCtxProvider != null) myCtxProvider!!.onObjectReleased()
    }
}