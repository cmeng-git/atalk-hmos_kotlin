/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.imgstreaming

import java.awt.image.BufferedImage

/**
 * Interface to capture desktop screen.
 *
 * @author Sebastien Vincent
 */
interface DesktopInteract {
    /**
     * Capture the full desktop screen using native grabber.
     *
     *
     * Contrary to other captureScreen method, it only returns raw bytes and not
     * `BufferedImage`. It is done in order to limit slow operation such as converting ARGB
     * images (uint32_t) to bytes especially for big big screen. For example a 1920x1200 desktop
     * consumes 9 MB of memory for grabbing and another 9 MB array for conversion operation.
     *
     * @param display
     * index of display
     * @param output
     * output buffer to store bytes in. Be sure that output length is sufficient
     * @return true if success, false if JNI error or output length too short
     */
    fun captureScreen(display: Int, output: ByteArray?): Boolean

    /**
     * Capture the full desktop screen using native grabber.
     *
     *
     * Contrary to other captureScreen method, it only returns raw bytes and not
     * `BufferedImage`. It is done in order to limit slow operation such as converting ARGB
     * images (uint32_t) to bytes especially for big big screen. For example a 1920x1200 desktop
     * consumes 9 MB of memory for grabbing and another 9 MB array for conversion operation.
     *
     * @param display
     * index of display
     * @param buffer
     * native output buffer to store bytes in. Be sure that output length is sufficient
     * @param bufferLength
     * length of native buffer
     * @return true if success, false if JNI error or output length too short
     */
    fun captureScreen(display: Int, buffer: Long, bufferLength: Int): Boolean

    /**
     * Capture a part of the desktop screen using native grabber.
     *
     *
     * Contrary to other captureScreen method, it only returns raw bytes and not
     * `BufferedImage`. It is done in order to limit slow operation such as converting ARGB
     * images (uint32_t) to bytes especially for big big screen. For example a 1920x1200 desktop
     * consumes 9 MB of memory for grabbing and another 9 MB array for conversion operation.
     *
     * @param display
     * index of display
     * @param x
     * x position to start capture
     * @param y
     * y position to start capture
     * @param width
     * capture width
     * @param height
     * capture height
     * @param output
     * output buffer to store bytes in. Be sure that output length is sufficient
     * @return true if success, false if JNI error or output length too short
     */
    fun captureScreen(display: Int, x: Int, y: Int, width: Int, height: Int, output: ByteArray?): Boolean

    /**
     * Capture a part of the desktop screen using native grabber.
     *
     *
     * Contrary to other captureScreen method, it only returns raw bytes and not
     * `BufferedImage`. It is done in order to limit slow operation such as converting ARGB
     * images (uint32_t) to bytes especially for big big screen. For example a 1920x1200 desktop
     * consumes 9 MB of memory for grabbing and another 9 MB array for conversion operation.
     *
     * @param display
     * index of display
     * @param x
     * x position to start capture
     * @param y
     * y position to start capture
     * @param width
     * capture width
     * @param height
     * capture height
     * @param buffer
     * native output buffer to store bytes in. Be sure that output length is sufficient
     * @param bufferLength
     * length of native buffer
     * @return true if success, false if JNI error or output length too short
     */
    fun captureScreen(display: Int, x: Int, y: Int, width: Int, height: Int, buffer: Long,
            bufferLength: Int): Boolean

    /**
     * Capture the full desktop screen.
     *
     * @return `BufferedImage` of the desktop screen
     */
    fun captureScreen(): BufferedImage?

    /**
     * Capture a part of the desktop screen.
     *
     * @param x
     * x position to start capture
     * @param y
     * y position to start capture
     * @param width
     * capture width
     * @param height
     * capture height
     * @return `BufferedImage` of a part of the desktop screen or null if `Robot`
     * problem
     */
    fun captureScreen(x: Int, y: Int, width: Int, height: Int): BufferedImage?
}