/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device.util

import android.app.Activity
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.view.ViewGroup
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.plugin.timberlog.TimberLog
import timber.log.Timber

/**
 * Provider of Open GL context. Currently use to provide 'shared context' for recording/streaming video; and it
 * is used for rendering the local video preview.
 *
 * Note: A TextureView object wraps a SurfaceTexture, responding to callbacks and acquiring new buffers.
 * link: https://source.android.com/devices/graphics/arch-tv
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class OpenGlCtxProvider
/**
 * Creates new instance of `OpenGlCtxProvider`.
 *
 * @param activity parent `Activity`.
 * @param container the container that will hold maintained `View`.
 */
(activity: Activity, container: ViewGroup) : ViewDependentProvider<OpenGLContext?>(activity, container), SurfaceTextureListener {
    /**
     * The `OpenGLContext`.
     */
    private var mGLContext: OpenGLContext? = null
    private lateinit var mTextureView: AutoFitTextureView

    /**
     * Flag used to inform the `SurfaceStream` that the `onSurfaceTextureUpdated` event has occurred.
     */
    var textureUpdated = true

    override fun createViewInstance(): View {
        mTextureView = AutoFitTextureView(mActivity)
        mTextureView.surfaceTextureListener = this
        Timber.d("TextView created: %s", mTextureView)
        return mTextureView
    }

    /**
     * setup the TextureView with the given size to take care 4x3 and 16x9 aspect ration video
     *
     * @param width The width of `mTextureView`
     * @param height The height of `mTextureView`
     */
    override fun setAspectRatio(width: Int, height: Int) {
        if (mTextureView != null) {
            mTextureView.setAspectRatio(width, height)
        } else {
            Timber.w("onSurfaceTexture configure transform mTextureView is null")
        }
    }

    /**
     * ConfigureTransform with the previously setup mTextureView size
     */
    fun setTransformMatrix() {
        configureTransform(mTextureView.mRatioWidth, mTextureView.mRatioHeight)
    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined
     * and also the size of `mTextureView` is fixed.
     *
     * Note: The transform is not working when the local preview container is very first setup;
     * Subsequence device rotation work but it also affects change the stream video; so far unable to solve
     * this problem.
     *
     * @param viewWidth The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        val scale = Math.max(viewWidth.toFloat() / videoSize.height, viewHeight.toFloat() / videoSize.width)

        // Create an identity matrix
        val matrix = Matrix()
        val rotation = mActivity.windowManager.defaultDisplay.rotation
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            val degree = 90 * (rotation - 2)
            val bufferRect = RectF(0f, 0f, videoSize.height.toFloat(), videoSize.width.toFloat())
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(degree.toFloat(), centerX, centerY)
        }
        Timber.d("onSurfaceTexture configure transform: %s => [%sx%s]; scale: %s; rotation: %s",
                videoSize, viewWidth, viewHeight, scale, rotation)

        // Not properly rotate when in landscape if proceed
//        else { // else if (Surface.ROTATION_0 == rotation || Surface.ROTATION_180 == rotation) {
//            int degree = (rotation == 0) ? 0 : 180;
//            matrix.postRotate(degree, centerX, centerY);
//        }
        mTextureView.setTransform(matrix)
    }

    /**
     * The method has problem to get the surface image to fill the local container size.
     */
    private fun configureTransform2(viewWidth: Int, viewHeight: Int) {
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        val bufferWidth: Int
        val bufferHeight: Int
        val degree: Int
        if (aTalkApp.isPortrait) {
            bufferWidth = videoSize.height
            bufferHeight = videoSize.width
        } else {
            bufferWidth = videoSize.width
            bufferHeight = videoSize.height
        }
        val rotation = mActivity.windowManager.defaultDisplay.rotation
        degree = if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            90 * (rotation - 2)
        } else { // else if (Surface.ROTATION_0 == rotation || Surface.ROTATION_180 == rotation) {
            if (rotation == 0) 0 else 180
        }
        val bufferRect = RectF(0f, 0f, bufferWidth.toFloat(), bufferHeight.toFloat())
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
        val matrix = Matrix()
        val scale = Math.max(viewWidth.toFloat() / videoSize.width, viewHeight.toFloat() / videoSize.height)
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
        matrix.postScale(scale, scale, centerX, centerY)
        matrix.postRotate(degree.toFloat(), centerX, centerY)
        Timber.d("onSurfaceTexture configure transform: [%sx%s] => [%sx%s]; scale=%s (%s/%s); rotation: %s (%s)",
                bufferWidth, bufferHeight, viewWidth, viewHeight, scale, scale * bufferWidth, scale * bufferHeight, rotation, degree)
        mTextureView.setTransform(matrix)
    }

    // ========= SurfaceTextureListener implementation ========= //
    @Synchronized
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        mGLContext = OpenGLContext(false, surface, EGL14.EGL_NO_CONTEXT)
        onObjectCreated(mGLContext)
        Timber.d("onSurfaceTexture Available with dimension: [%s x %s] (%s)", width, height, videoSize)
    }

    @Synchronized
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        onObjectDestroyed()
        // Release context only when the View is destroyed
        if (mGLContext != null) {
            mGLContext!!.release()
            mGLContext = null
        }
        return false
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        Timber.d("onSurfaceTexture SizeChanged: [%s x %s]", width, height)
        configureTransform(width, height)
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        Timber.log(TimberLog.FINER, "onSurfaceTextureUpdated")
        textureUpdated = true
    }
}