/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.control

import javax.media.Control

/**
 * Defines the interface for controlling `CaptureDevice`s/ `DataSource`s associated
 * with the `imgstreaming` FMJ/JMF protocol.
 *
 * @author Lyubomir Marinov
 */
interface ImgStreamingControl : Control {
    /**
     * Set the display index and the origin of the stream associated with a specific index in the
     * `DataSource` of this `Control`.
     *
     * @param streamIndex
     * the index in the associated `DataSource` of the stream to set the display index
     * and the origin of
     * @param displayIndex
     * the display index to set on the specified stream
     * @param x
     * the x coordinate of the origin to set on the specified stream
     * @param y
     * the y coordinate of the origin to set on the specified stream
     */
    fun setOrigin(streamIndex: Int, displayIndex: Int, x: Int, y: Int)
}