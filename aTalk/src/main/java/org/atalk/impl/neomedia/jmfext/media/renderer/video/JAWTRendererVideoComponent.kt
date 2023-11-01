/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.renderer.video

import org.atalk.impl.neomedia.codec.video.SwScale
import org.atalk.util.OSUtils
import java.awt.Canvas
import java.awt.Graphics

/**
 * Implements an AWT `Component` in which `JAWTRenderer` paints.
 *
 * @author Lyubomir Marinov
 */
open class JAWTRendererVideoComponent
/**
 * Initializes a new `JAWTRendererVideoComponent` instance.
 *
 * @param renderer
 */(
        /**
         * The `JAWTRenderer` which paints in this `JAWTRendererVideoComponent`.
         */
        protected val renderer: JAWTRenderer) : Canvas() {
    /**
     * The indicator which determines whether the native counterpart of this `JAWTRenderer`
     * wants `paint` calls on its AWT `Component` to be delivered. For example, after
     * the native counterpart has been able to acquire the native handle of the AWT
     * `Component`, it may be able to determine when the native handle needs painting
     * without waiting for AWT to call `paint` on the `Component`. In such a
     * scenario, the native counterpart may indicate with `false` that it does not need
     * further `paint` deliveries.
     */
    private var wantsPaint = true

    /**
     * Overrides Component.addNotify to reset the indicator which determines whether the
     * native counterpart of this `JAWTRenderer` wants `paint` calls on its AWT
     * `Component` to be delivered.
     */
    override fun addNotify() {
        super.addNotify()
        wantsPaint = true
        synchronized(handleLock) {
            var handle: Long
            if (this.handle.also { handle = it } != 0L) {
                try {
                    JAWTRenderer.addNotify(handle, this)
                } catch (uler: UnsatisfiedLinkError) {
                    // The function/method has been introduced in a revision of the JAWTRenderer
                    // API and may not be available in the binary.
                }
                // The first task of the method paint(Graphics) is to attach to the native
                // view/widget/window of this Canvas. The sooner, the better. Technically, it
                // should be possible to do it immediately after the method addNotify().
                try {
                    paint(null)
                    if (OSUtils.IS_MAC) {
                        // XXX After JAWT is told about the CALayer via assignment to
                        // JAWT_SurfaceLayers, JAWT does not automatically place the CALayer in
                        // the necessary location and no video is drawn. A resize was observed to
                        // fix the two issues.
                        val x = x
                        val y = y
                        val width = width
                        val height = height
                        setBounds(
                                x - SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH,
                                y - SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH,
                                width + SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH,
                                height + SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH)
                        setBounds(x, y, width, height)
                    }
                } finally {
                    // Well, we explicitly invoked the method paint(Graphics) which is kind of
                    // extraordinary.
                    wantsPaint = true
                }
            }
        }
    }

    /**
     * Gets the handle of the native counterpart of the `JAWTRenderer` which paints in this
     * `AWTVideoComponent`.
     *
     * @return the handle of the native counterpart of the `JAWTRenderer` which paints in
     * this `AWTVideoComponent`
     */
    protected val handle: Long
        get() = renderer.handle

    /**
     * Gets the synchronization lock which protects the access to the `handle` property of
     * this `AWTVideoComponent`.
     *
     * @return the synchronization lock which protects the access to the `handle`
     * property of this `AWTVideoComponent`
     */
    private val handleLock: Any
        get() = renderer.handleLock

    /**
     * Overrides [Canvas.paint] to paint this `Component` in the native
     * counterpart of its associated `JAWTRenderer`.
     */
    override fun paint(g: Graphics?) {
        // XXX If the size of this Component is tiny enough to crash sws_scale, then it may cause
        // issues with other functionality as well. Stay on the safe side.
        if (wantsPaint && width >= SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH && height >= SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH) {
            synchronized(handleLock) {
                var handle: Long
                if (this.handle.also { handle = it } != 0L) {
                    val parent = parent
                    var zOrder: Int
                    if (parent == null) {
                        zOrder = -1
                    } else {
                        zOrder = parent.getComponentZOrder(this)
                        // CALayer is used in the implementation of JAWTRenderer
                        // on OS X and its zPosition is the reverse of AWT's
                        // componentZOrder (in terms of what appears above and bellow).
                        if (OSUtils.IS_MAC && zOrder != -1) zOrder = parent.componentCount - 1 - zOrder
                    }
                    wantsPaint = JAWTRenderer.paint(handle, this, g, zOrder)
                }
            }
        }
    }

    /**
     * Overrides Component.removeNotify to reset the indicator which determines whether
     * the native counterpart of this `JAWTRenderer` wants `paint` calls on its AWT
     * `Component` to be delivered.
     */
    override fun removeNotify() {
        synchronized(handleLock) {
            var handle: Long
            if (this.handle.also { handle = it } != 0L) {
                try {
                    JAWTRenderer.removeNotify(handle, this)
                } catch (uler: UnsatisfiedLinkError) {
                    // The function/method has been introduced in a revision of
                    // the JAWTRenderer API and may not be available in the binary.
                }
            }
        }

        // In case the associated JAWTRenderer has said that it does not want paint
        // events/notifications, ask it again next time because the native
        // handle of this Canvas may be recreated.
        wantsPaint = true
        super.removeNotify()
    }

    /**
     * Overrides [Canvas.update] to skip the filling with the background color in
     * order to prevent flickering.
     */
    override fun update(g: Graphics) {
        synchronized(handleLock) {
            if (!wantsPaint || handle == 0L) {
                super.update(g)
                return
            }
        }

        // Skip the filling with the background color because it causes flickering.
        paint(g)
    }

    companion object {
        /**
         * The serial version UID of the `JAWTRendererVideoComponent` class defined to silence a
         * serialization compile-time warning.
         */
        private const val serialVersionUID = 0L
    }
}