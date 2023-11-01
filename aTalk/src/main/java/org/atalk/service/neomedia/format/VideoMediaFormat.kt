/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.format

import java.awt.Dimension

/**
 * The interface represents a video format. Video formats characterize video streams and the
 * `VideoMediaFormat` interface gives access to some of their properties such as encoding and clock rate.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface VideoMediaFormat : MediaFormat {
    /**
     * Returns the size of the image that this `VideoMediaFormat` describes.
     *
     * @return a `java.awt.Dimension` instance indicating the image size (in pixels) of this `VideoMediaFormat`.
     */
    val size: Dimension?

    /**
     * Returns the frame rate associated with this `MediaFormat`.
     *
     * @return The frame rate associated with this format.
     */
    val frameRate: Float
}