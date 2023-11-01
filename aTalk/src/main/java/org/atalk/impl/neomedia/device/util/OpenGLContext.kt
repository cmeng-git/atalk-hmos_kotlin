/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device.util

import android.opengl.*
import timber.log.Timber

/**
 * Code for EGL context handling
 */
open class OpenGLContext(recorder: Boolean, objSurface: Any?, sharedContext: EGLContext?) {
    private var mEGLDisplay = EGL14.EGL_NO_DISPLAY
    var context = EGL14.EGL_NO_CONTEXT
        private set
    private var mEGLSurface = EGL14.EGL_NO_SURFACE

    /**
     * Prepares EGL. We want a GLES 2.0 context and a surface that supports recording.
     */
    init {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (mEGLDisplay === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("unable to get EGL14 display")
        }
        val majorVersion = IntArray(1)
        val minorVersion = IntArray(1)
        if (!EGL14.eglInitialize(mEGLDisplay, majorVersion, 0, minorVersion, 0)) {
            mEGLDisplay = null
            throw RuntimeException("unable to initialize EGL14")
        }
        Timber.i("EGL version: %s.%s", majorVersion[0], minorVersion[0])
        val eglConfig = chooseEglConfig(mEGLDisplay, recorder)

        // Configure context for OpenGL ES 2.0.
        val attrib_list = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(mEGLDisplay, eglConfig, sharedContext, attrib_list, 0)
        checkEglError("eglCreateContext")

        // Create a window surface, and attach it to the Surface we received.
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, eglConfig, objSurface, surfaceAttribs, 0)
        checkEglError("eglCreateWindowSurface")
    }

    private fun chooseEglConfig(eglDisplay: EGLDisplay?, recorder: Boolean): EGLConfig? {
        val configs = arrayOfNulls<EGLConfig>(1)
        val attribList: IntArray
        attribList = if (recorder) {
            // Configure EGL for recording and OpenGL ES 2.0.
            intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE)
        } else {
            // Configure EGL for OpenGL ES 2.0 only.
            intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_NONE)
        }
        val numconfigs = IntArray(1)
        require(EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size,
                numconfigs, 0)) {
            ("eglChooseConfig failed "
                    + GLUtils.getEGLErrorString(EGL14.eglGetError()))
        }
        require(numconfigs[0] > 0) {
            ("eglChooseConfig failed "
                    + GLUtils.getEGLErrorString(EGL14.eglGetError()))
        }
        return configs[0]
    }

    /**
     * Discards all resources held by this class, notably the EGL context. Also releases the Surface
     * that was passed to our constructor.
     */
    open fun release() {
        if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
            // Android is unusual in that it uses a reference-counted EGLDisplay.  So for
            // every eglInitialize() we need an eglTerminate().
            EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface)
            EGL14.eglDestroyContext(mEGLDisplay, context)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(mEGLDisplay)
        }
        mEGLDisplay = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        mEGLSurface = EGL14.EGL_NO_SURFACE
    }

    fun makeCurrent() {
        val ctx = EGL14.eglGetCurrentContext()
        val surface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        if (context != ctx || mEGLSurface != surface) {
            if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, context)) {
                throw RuntimeException("eglMakeCurrent failed "
                        + GLUtils.getEGLErrorString(EGL14.eglGetError()))
            }
        }
    }

    /**
     * Sets "no surface" and "no context" on the current display.
     */
    fun releaseEGLSurfaceContext() {
        if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT)
        }
    }

    /**
     * Calls eglSwapBuffers. Use this to "publish" the current frame.
     */
    fun swapBuffers() {
        if (!EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface)) {
            throw RuntimeException("Cannot swap buffers")
        }
        checkEglError("opSwapBuffers")
    }

    /**
     * Sends the presentation time stamp to EGL. Time is expressed in nanoseconds.
     */
    fun setPresentationTime(nsecs: Long) {
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs)
        checkEglError("eglPresentationTimeANDROID")
    }

    /**
     * Checks for EGL errors. Throws an exception if one is found.
     */
    private fun checkEglError(msg: String) {
        var error: Int
        if (EGL14.eglGetError().also { error = it } != EGL14.EGL_SUCCESS) {
            throw RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error))
        }
    }

    companion object {
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}