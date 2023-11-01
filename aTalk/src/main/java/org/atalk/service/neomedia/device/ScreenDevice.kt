/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.device

import java.awt.Dimension
import java.awt.Point

/**
 * Represents a physical screen display.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
interface ScreenDevice {
    /**
     * Determines whether this screen contains a specified point.
     *
     * @param p
     * point coordinate
     * @return `true` if `point` belongs to this screen; `false`, otherwise
     */
    fun containsPoint(p: Point?): Boolean

    /**
     * Gets this screen's index.
     *
     * @return this screen's index
     */
    val index: Int

    /**
     * Gets the current resolution of this screen.
     *
     * @return the current resolution of this screen
     */
    val size: Dimension?
}