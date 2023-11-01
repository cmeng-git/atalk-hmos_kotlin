/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.control

import java.awt.Component
import javax.media.control.FrameRateControl

/**
 * Provides a default implementation of `FrameRateControl`.
 *
 * @author Lyubomir Marinov
 */
open class FrameRateControlAdapter : FrameRateControl {
    /**
     * Gets the UI `Component` associated with this `Control` object.
     * `FrameRateControlAdapter` always returns `null`.
     *
     * @return the UI `Component` associated with this `Control` object
     * @see Control.getControlComponent
     */
    override fun getControlComponent(): Component? {
        return null
    }

    /**
     * Gets the current output frame rate. `FrameRateControlAdapter` always returns
     * `-1`.
     *
     * @return the current output frame rate if it is known; otherwise, `-1`
     * @see FrameRateControl.getFrameRate
     */
    override fun getFrameRate(): Float {
        return (-1).toFloat()
    }

    /**
     * Gets the maximum supported output frame rate. `FrameRateControlAdapter` always
     * returns `-1`.
     *
     * @return the maximum supported output frame rate if it is known; otherwise, `-1`
     * @see FrameRateControl.getMaxSupportedFrameRate
     */
    override fun getMaxSupportedFrameRate(): Float {
        return (-1).toFloat()
    }

    /**
     * Gets the default/preferred output frame rate. `FrameRateControlAdapter` always
     * returns
     * `-1`.
     *
     * @return the default/preferred output frame rate if it is known; otherwise, `-1`
     * @see FrameRateControl.getPreferredFrameRate
     */
    override fun getPreferredFrameRate(): Float {
        return (-1).toFloat()
    }

    /**
     * Sets the output frame rate. `FrameRateControlAdapter` always returns `-1`.
     *
     * @param frameRate
     * the output frame rate to change the current one to
     * @return the actual current output frame rate or `-1` if it is unknown or not
     * controllable
     * @see FrameRateControl.setFrameRate
     */
    override fun setFrameRate(frameRate: Float): Float {
        return (-1).toFloat()
    }
}