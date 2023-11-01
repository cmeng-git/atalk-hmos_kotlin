/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.service.neomedia.device.ScreenDevice
import java.awt.Dimension
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle

/**
 * Implementation of `ScreenDevice`.
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 */
class ScreenDeviceImpl
/**
 * Constructor.
 *
 * @param index
 * screen index
 * @param screen
 * screen device
 */ protected constructor(
        /**
         * Screen index.
         */
        override val index: Int,
        /**
         * AWT `GraphicsDevice`.
         */
        private val screen: GraphicsDevice) : ScreenDevice {
    /**
     * Get the screen index.
     *
     * @return screen index
     */

    /**
     * If the screen contains specified point.
     *
     * @param p
     * point coordinate
     * @return true if point belongs to screen, false otherwise
     */
    override fun containsPoint(p: Point?): Boolean {
        return screen.defaultConfiguration.bounds.contains(p)
    }

    /**
     * Get bounds of the screen.
     *
     * @return bounds of the screen
     */
    val bounds: Rectangle
        get() = screen.defaultConfiguration.bounds

    /**
     * Get the identifier of the screen.
     *
     * @return ID of the screen
     */
    val name: String
        get() = screen.iDstring

    /**
     * Gets the (current) size/resolution of this `ScreenDevice`.
     *
     * @return the (current) size/resolution of this `ScreenDevice`
     */
    override val size: Dimension?
        get() {
            val displayMode = screen.displayMode
            return if (displayMode == null) null else Dimension(displayMode.width,
                    displayMode.height)
        }

    companion object {
        /**
         * An array with `ScreenDevice` element type which is empty. Explicitly defined to reduce
         * allocations, garbage collection.
         */
        private val EMPTY_SCREEN_DEVICE_ARRAY = arrayOf<ScreenDevice>()

        // We know that GraphicsDevice type is TYPE_RASTER_SCREEN.
        /*
        * We've seen NoClassDefFoundError at one time and InternalError at another.
        */
        /*
            * Make sure the GraphicsEnvironment is not headless in order to avoid a HeadlessException.
            */
        /**
         * Returns all available `ScreenDevice`s.
         *
         * @return an array of all available `ScreenDevice`s
         */
        val availableScreenDevices: Array<ScreenDevice>
            get() {
                val ge = try {
                    GraphicsEnvironment.getLocalGraphicsEnvironment()
                } catch (t: Throwable) {
                    /*
                        * We've seen NoClassDefFoundError at one time and InternalError at another.
                        */
                    if (t is ThreadDeath) throw t else null
                }

                /*
                 * Make sure the GraphicsEnvironment is not headless in order to avoid a HeadlessException.
                 */
                var screens: Array<ScreenDevice>? = null
                if (ge != null && !ge.isHeadlessInstance) {
                    val devices = ge.screenDevices
                    if (devices != null && devices.isNotEmpty()) {
                        screens = arrayOf()
                        var i = 0
                        for (dev in devices) {
                            // We know that GraphicsDevice type is TYPE_RASTER_SCREEN.
                            screens[i] = ScreenDeviceImpl(i, dev)
                            i++
                        }
                    }
                }
                return screens ?: EMPTY_SCREEN_DEVICE_ARRAY
            }

        /**
         * Gets the default `ScreenDevice`. The implementation attempts to return the
         * `ScreenDevice` with the highest resolution.
         *
         * @return the default `ScreenDevice`
         */
        val defaultScreenDevice: ScreenDevice?
            get() {
                var width = 0
                var height = 0
                var best: ScreenDevice? = null
                for (screen in availableScreenDevices) {
                    val size = screen!!.size
                    if (size != null && (width < size.width || height < size.height)) {
                        width = size.width
                        height = size.height
                        best = screen
                    }
                }
                return best
            }
    }
}