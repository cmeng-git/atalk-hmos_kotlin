/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.hid

/**
 * Human Interface Device service.
 * This service is used to regenerates key and mouse events on the local
 * computer. It is typically used in case of remote control features.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
interface HIDService {
    /**
     * Press a specific key using its keycode.
     *
     * @param keycode the Java keycode, all available keycode can be found
     * in java.awt.event.KeyEvent class (VK_A, VK_SPACE, ...)
     * @see java.awt.event.KeyEvent
     *
     * @see java.awt.Robot.keyRelease
     */
    fun keyPress(keycode: Int)

    /**
     * Release a specific key using its keycode.
     *
     * @param keycode the Java keycode, all available keycode can be found
     * in java.awt.event.KeyEvent class (VK_A, VK_SPACE, ...)
     * @see java.awt.event.KeyEvent
     *
     * @see java.awt.Robot.keyRelease
     */
    fun keyRelease(keycode: Int)

    /**
     * Press a specific key using its char representation.
     *
     * @param key char representation of the key
     */
    fun keyPress(key: Char)

    /**
     * Release a specific key using its char representation.
     *
     * @param key char representation of the key
     */
    fun keyRelease(key: Char)

    /**
     * Press a mouse button(s).
     *
     * @param btns button masks
     * @see java.awt.Robot.mousePress
     */
    fun mousePress(btns: Int)

    /**
     * Release a mouse button(s).
     *
     * @param btns button masks
     * @see java.awt.Robot.mouseRelease
     */
    fun mouseRelease(btns: Int)

    /**
     * Move the mouse on the screen.
     *
     * @param x x position on the screen
     * @param y y position on the screen
     * @see java.awt.Robot.mouseMove
     */
    fun mouseMove(x: Int, y: Int)

    /**
     * Release a mouse button(s).
     *
     * @param rotation wheel rotation (could be negative or positive depending on the direction).
     * @see java.awt.Robot.mouseWheel
     */
    fun mouseWheel(rotation: Int)
}