/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.imgstreaming

import org.atalk.util.OSUtils
import timber.log.Timber
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.image.BufferedImage

/**
 * Capture desktop screen either via native code (JNI) if available or by using
 * `java.awt.Robot`.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 * @see java.awt.Robot
 */
class DesktopInteractImpl : DesktopInteract {
    /**
     * Screen capture robot.
     */
    private var robot: Robot? = null

    /**
     * Constructor.
     *
     * @throws AWTException if platform configuration does not allow low-level input control
     * @throws SecurityException if Robot creation is not permitted
     */
    init {
        // throws AWTException, SecurityException {
        robot = Robot()
    }

    /**
     * Capture the full desktop screen using native grabber.
     *
     *
     * Contrary to other captureScreen method, it only returns raw bytes and not
     * `BufferedImage`. It is done in order to limit slow operation such as converting ARGB
     * images (uint32_t) to bytes especially for big big screen. For example a 1920x1200 desktop
     * consumes 9 MB of memory for grabbing and another 9 MB array for conversion operation.
     *
     * @param display index of display
     * @param output output buffer to store bytes in. Be sure that output length is sufficient
     * @return true if success, false if JNI error or output length too short
     */
    override fun captureScreen(display: Int, output: ByteArray?): Boolean {
        val dim = Toolkit.getDefaultToolkit().screenSize
        return captureScreen(display, 0, 0, dim.width, dim.height, output)
    }

    /**
     * Capture the full desktop screen using native grabber.
     *
     *
     * Contrary to other captureScreen method, it only returns raw bytes and not
     * `BufferedImage`. It is done in order to limit slow operation such as converting ARGB
     * images (uint32_t) to bytes especially for big big screen. For example a 1920x1200 desktop
     * consumes 9 MB of memory for grabbing and another 9 MB array for conversion operation.
     *
     * @param display index of display
     * @param buffer native output buffer to store bytes in. Be sure that output length is sufficient
     * @param bufferLength length of native buffer
     * @return true if success, false if JNI error or output length too short
     */
    override fun captureScreen(display: Int, buffer: Long, bufferLength: Int): Boolean {
        val dim = Toolkit.getDefaultToolkit().screenSize
        return captureScreen(display, 0, 0, dim.width, dim.height, buffer, bufferLength)
    }

    /**
     * Capture a part of the desktop screen using native grabber.
     *
     *
     * Contrary to other captureScreen method, it only returns raw bytes and not
     * `BufferedImage`. It is done in order to limit slow operation such as converting ARGB
     * images (uint32_t) to bytes especially for big big screen. For example a 1920x1200 desktop
     * consumes 9 MB of memory for grabbing and another 9 MB array for conversion operation.
     *
     * @param display index of display
     * @param x x position to start capture
     * @param y y position to start capture
     * @param width capture width
     * @param height capture height
     * @param output output buffer to store bytes in. Be sure that output length is sufficient
     * @return true if success, false if JNI error or output length too short
     */
    override fun captureScreen(display: Int, x: Int, y: Int, width: Int, height: Int, output: ByteArray?): Boolean {
        return ((OSUtils.IS_LINUX || OSUtils.IS_MAC || OSUtils.IS_WINDOWS)
                && ScreenCapture.grabScreen(display, x, y, width, height, output))
    }

    /**
     * Capture a part of the desktop screen using native grabber.
     *
     *
     * Contrary to other captureScreen method, it only returns raw bytes and not
     * `BufferedImage`. It is done in order to limit slow operation such as converting ARGB
     * images (uint32_t) to bytes especially for big big screen. For example a 1920x1200 desktop
     * consumes 9 MB of memory for grabbing and another 9 MB array for conversion operation.
     *
     * @param display index of display
     * @param x x position to start capture
     * @param y y position to start capture
     * @param width capture width
     * @param height capture height
     * @param buffer native output buffer to store bytes in. Be sure that output length is sufficient
     * @param bufferLength length of native buffer
     * @return true if success, false if JNI error or output length too short
     */
    override fun captureScreen(display: Int, x: Int, y: Int, width: Int, height: Int, buffer: Long,
            bufferLength: Int): Boolean {
        return ((OSUtils.IS_LINUX || OSUtils.IS_MAC || OSUtils.IS_WINDOWS)
                && ScreenCapture.grabScreen(display, x, y, width, height, buffer, bufferLength))
    }

    /**
     * Capture the full desktop screen using `java.awt.Robot`.
     *
     * @return `BufferedImage` of the desktop screen
     */
    override fun captureScreen(): BufferedImage? {
        val dim = Toolkit.getDefaultToolkit().screenSize
        return captureScreen(0, 0, dim.width, dim.height)
    }

    /**
     * Capture a part of the desktop screen using `java.awt.Robot`.
     *
     * @param x x position to start capture
     * @param y y position to start capture
     * @param width capture width
     * @param height capture height
     * @return `BufferedImage` of a part of the desktop screen or null if Robot problem
     */
    override fun captureScreen(x: Int, y: Int, width: Int, height: Int): BufferedImage? {
        var img: BufferedImage? = null
        var rect: Rectangle? = null
        if (robot == null) {
            /* Robot has not been created so abort */
            return null
        }
        Timber.i("Begin capture: %d", System.nanoTime())
        rect = Rectangle(x, y, width, height)
        img = robot!!.createScreenCapture(rect)
        Timber.i("End capture: %s", System.nanoTime())
        return img
    }
}