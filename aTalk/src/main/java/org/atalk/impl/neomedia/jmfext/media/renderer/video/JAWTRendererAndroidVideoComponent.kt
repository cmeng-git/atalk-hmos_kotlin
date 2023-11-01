/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.renderer.video

import android.content.Context
import android.opengl.GLSurfaceView
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.service.neomedia.ViewAccessor
import java.awt.Component
import java.awt.Graphics
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Implements `java.awt.Component` for `JAWTRenderer` on Android using a [GLSurfaceView].
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class JAWTRendererAndroidVideoComponent
/**
 * Initializes a new `JAWTRendererAndroidVideoComponent` which is to be the visual
 * `Component` of a specific `JAWTRenderer`.
 *
 * @param renderer the `JAWTRenderer` which is to use the new instance as its visual `Component`
 */(
        /**
         * The `JAWTRenderer` which is to use or is using this instance as its visual `Component`.
         */
        private val renderer: JAWTRenderer) : Component(), ViewAccessor {
    /**
     * The `GLSurfaceView` is the actual visual counterpart of this `java.awt.Component`.
     */
    private var glSurfaceView: GLSurfaceView? = null

    /**
     * Implements [ViewAccessor.getView]. Gets the [View] provided by this
     * instance which is to be used in a specific [Context].
     *
     * @param context the `Context` in which the provided `View` will be used
     * @return the `View` provided by this instance which is to be used in a specific `Context`
     * @see ViewAccessor.getView
     */
    @Synchronized
    override fun getView(context: Context?): GLSurfaceView? {
        if (glSurfaceView == null && context != null) {
            glSurfaceView = GLSurfaceView(context)
            if (TimberLog.isTraceEnable) glSurfaceView!!.debugFlags = GLSurfaceView.DEBUG_LOG_GL_CALLS
            glSurfaceView!!.setRenderer(object : GLSurfaceView.Renderer {
                /**
                 * Implements [GLSurfaceView.Renderer.onDrawFrame]. Draws the current frame.
                 *
                 * @param gl the `GL10` interface with which the drawing is to be performed
                 */
                override fun onDrawFrame(gl: GL10) {
                    this@JAWTRendererAndroidVideoComponent.onDrawFrame(gl)
                }

                override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
                    // TODO Auto-generated method stub
                }

                override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
                    // TODO Auto-generated method stub
                }
            })
            glSurfaceView!!.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
        return glSurfaceView
    }

    /**
     * Called by the `GLSurfaceView` which is the actual visual counterpart of this
     * `java.awt.Component` to draw the current frame.
     *
     * @param gl the `GL10` interface with which the drawing is to be performed
     */
    protected fun onDrawFrame(gl: GL10?) {
        synchronized(renderer.handleLock) {
            val handle = renderer.handle
            if (handle != 0L) {
                val g: Graphics? = null
                val zOrder = -1
                JAWTRenderer.paint(handle, this, g, zOrder)
            }
        }
    }

    @Synchronized
    override fun repaint() {
        if (glSurfaceView != null) glSurfaceView!!.requestRender()
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}