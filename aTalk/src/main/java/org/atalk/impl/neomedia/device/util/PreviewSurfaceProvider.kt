/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device.util

import android.view.*
import androidx.appcompat.app.AppCompatActivity
import timber.log.Timber

/**
 * The class exposes methods for managing preview surfaceView state which must be synchronized with
 * currently used [CameraDevice] state.
 * The surface must be present before the camera is started and for this purpose
 * [.obtainObject] method shall be used.
 *
 * When the call is ended, before the `Activity` is finished we should ensure that the camera
 * has been stopped (which is done by video telephony internals), so we should wait for it to be
 * disposed by invoking method [.waitForObjectRelease]. It will block current
 * `Thread` until it happens or an `Exception` will be thrown if timeout occurs.
 */
class PreviewSurfaceProvider
/**
 * Create a new instance of `PreviewSurfaceProvider`.
 *
 * @param parent parent `OSGiActivity` instance.
 * @param container the `ViewGroup` that will hold maintained `SurfaceView`.
 * @param setZMediaOverlay if set to `true` then the `SurfaceView` will be
 * displayed on the top of other surfaces e.g. local camera surface preview
 */
(parent: AppCompatActivity, container: ViewGroup,
        /**
         * Flag indicates whether [SurfaceView.setZOrderMediaOverlay] should be called on
         * created `SurfaceView`.
         */
        private val setZMediaOverlay: Boolean) : ViewDependentProvider<SurfaceHolder?>(parent, container), SurfaceHolder.Callback {

    private lateinit var mSurfaceView: AutoFitSurfaceView

    override fun createViewInstance(): View {
        mSurfaceView = AutoFitSurfaceView(mActivity)
        mSurfaceView.holder.addCallback(this)
        if (setZMediaOverlay) mSurfaceView.setZOrderMediaOverlay(true)
        return mSurfaceView
    }

    override fun setAspectRatio(width: Int, height: Int) {
        if (mSurfaceView != null) {
            mSurfaceView.setAspectRatio(width, height)
        } else {
            Timber.w(" setAspectRatio for mSurfaceView is null")
        }
    }

    /**
     * Method is called before `Camera` is started and shall return non `null` [SurfaceHolder] instance.
     * The is also used by android decoder.
     *
     * @return [SurfaceHolder] instance that will be used for local video preview
     */
    override fun obtainObject(): SurfaceHolder? {
        // Timber.e(new Exception("Obtain Object for testing only"));
        return super.obtainObject()
    }

    /**
     * Method is called when `Camera` is stopped and it's safe to release the [Surface] object.
     */
    override fun onObjectReleased() {
        super.onObjectReleased()
    }

    /**
     * Should return current [Display] rotation as defined in [android.view.Display.getRotation].
     *
     * @return current [Display] rotation as one of values:
     * [Surface.ROTATION_0], [Surface.ROTATION_90], [Surface.ROTATION_180], [Surface.ROTATION_270].
     */
    val displayRotation: Int
        get() = mActivity.windowManager.defaultDisplay.rotation
    // ============== SurfaceHolder.Callback ================== //
    /**
     * This is called immediately after the surface is first created. Implementations of this should
     * start up whatever rendering code they desire. Note that only one thread can ever draw into a
     * [Surface], so you should not draw into the Surface here if your normal rendering will
     * be in another thread.
     *
     * Must setFixedSize() to the user selected video size, to ensure local preview is in correct aspect ratio
     * https://developer.android.com/reference/android/hardware/camera2/CameraDevice.html#createCaptureSession(android.hardware.camera2.params.SessionConfiguration)
     *
     * @param holder The SurfaceHolder whose surface is being created.
     */
    override fun surfaceCreated(holder: SurfaceHolder) {
        // Timber.d("SurfaceHolder created setFixedSize: %s", mVideoSize);
        if (videoSize != null) {
            holder.setFixedSize(videoSize.width, videoSize.height)
        }
        onObjectCreated(holder)
    }

    /**
     * This is called immediately after any structural changes (format or size) have been made to
     * the surface. You should at this point update the imagery in the surface. This method is
     * always called at least once, after [.surfaceCreated].
     *
     * @param holder The SurfaceHolder whose surface has changed.
     * @param format The new PixelFormat of the surface.
     * @param width The new width of the surface.
     * @param height The new height of the surface.
     */
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        /*
         * surfaceChange event is mainly triggered by local video preview change by user; currently
         * not implemented in android aTalk. Hence no action is required.
         * Note: the event get trigger whenever there is an init of the local video preview e.g. init or toggle camera
         * Timber.w("Preview surface change size: %s x %s", width, height);
         */
        // Timber.d("SurfaceHolder size changed: [%s x %s]; %s", width, height, holder);
        // preview surface does not exist
        // if (mHolder.getSurface() == null){
        //     return;
        // }
        //
        // // stop preview before making changes
        // try {
        //     mCamera.stopPreview();
        // } catch (Exception e){
        // ignore: tried to stop a non-existent preview
        // }

        // set preview size and make any resize, rotate or reformatting changes here
        // start preview with new settings
        // try {
        //     mCamera.setPreviewDisplay(mHolder);
        //     mCamera.startPreview();
        // } catch (Exception e){
        //     Timber.e("Error starting camera preview: %s", e.getMessage());
        // }
    }

    /**
     * This is called immediately before a surface is being destroyed. After returning from this
     * call, you should no longer try to access this surface. If you have a rendering thread that
     * directly accesses the surface, you must ensure that thread is no longer touching the Surface
     * before returning from this function.
     *
     * @param holder The SurfaceHolder whose surface is being destroyed.
     */
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        onObjectDestroyed()
    }
}