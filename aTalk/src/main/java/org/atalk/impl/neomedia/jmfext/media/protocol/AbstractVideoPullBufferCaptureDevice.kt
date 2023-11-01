/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol

import org.atalk.impl.neomedia.control.FrameRateControlAdapter
import javax.media.MediaLocator
import javax.media.control.FrameRateControl

/**
 * Provides a base implementation of `PullBufferDataSource` and `CaptureDevice` for
 * the purposes of video in order to facilitate implementers by taking care of boilerplate in the
 * most common cases.
 *
 * @author Lyubomir Marinov
 */
abstract class AbstractVideoPullBufferCaptureDevice : AbstractPullBufferCaptureDevice {
    /**
     * Initializes a new `AbstractVideoPullBufferCaptureDevice` instance.
     */
    protected constructor() {}

    /**
     * Initializes a new `AbstractVideoPullBufferCaptureDevice` instance from a specific
     * `MediaLocator`.
     *
     * @param locator
     * the `MediaLocator` to create the new instance from
     */
    protected constructor(locator: MediaLocator?) : super(locator) {}

    /**
     * Creates a new `FrameRateControl` instance which is to allow the getting and
     * setting of the frame rate of this `AbstractVideoPullBufferCaptureDevice`.
     *
     * @return a new `FrameRateControl` instance which is to allow the getting and
     * setting of the frame rate of this `AbstractVideoPullBufferCaptureDevice`
     * @see AbstractPullBufferCaptureDevice.createFrameRateControl
     */
    override fun createFrameRateControl(): FrameRateControl? {
        return object : FrameRateControlAdapter() {
            /**
             * The output frame rate of this `AbstractVideoPullBufferCaptureDevice`.
             */
            private var frameRate = -1f
            override fun getFrameRate(): Float {
                return frameRate
            }

            override fun setFrameRate(frameRate: Float): Float {
                this.frameRate = frameRate
                return this.frameRate
            }
        }
    }
}