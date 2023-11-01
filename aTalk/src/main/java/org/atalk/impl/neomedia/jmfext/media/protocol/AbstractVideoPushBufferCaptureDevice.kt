/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol

import javax.media.MediaLocator
import javax.media.control.FrameRateControl

/**
 * Provides a base implementation of `PushBufferDataSource` and `CaptureDevice` for
 * the purposes of video in order to facilitate implementers by taking care of boilerplate in the
 * most common cases.
 *
 * @author Lyubomir Marinov
 */
abstract class AbstractVideoPushBufferCaptureDevice
/**
 * Initializes a new `AbstractVideoPushBufferCaptureDevice` instance from a specific
 * `MediaLocator`.
 *
 * @param locator
 * the `MediaLocator` to create the new instance from
 */
/**
 * Initializes a new `AbstractVideoPushBufferCaptureDevice` instance.
 */
protected constructor(locator: MediaLocator? = null) : AbstractPushBufferCaptureDevice(locator) {
    /**
     * Creates a new `FrameRateControl` instance which is to allow the getting and
     * setting of the frame rate of this `AbstractVideoPushBufferCaptureDevice`.
     *
     * @return a new `FrameRateControl` instance which is to allow the getting and
     * setting of the frame rate of this `AbstractVideoPushBufferCaptureDevice`
     * @see AbstractPushBufferCaptureDevice.createFrameRateControl
     */
    override fun createFrameRateControl(): FrameRateControl? {
        return null
    }
}