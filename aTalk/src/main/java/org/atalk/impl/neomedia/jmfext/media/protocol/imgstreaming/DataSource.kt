/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.imgstreaming

import org.atalk.impl.neomedia.control.ImgStreamingControl
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferCaptureDevice
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractVideoPullBufferCaptureDevice
import java.awt.Component
import javax.media.MediaLocator
import javax.media.control.FormatControl

/**
 * Implements `CaptureDevice` and `DataSource` for the purposes of image and desktop
 * streaming.
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class DataSource : AbstractVideoPullBufferCaptureDevice {
    /**
     * The `ImgStreamingControl` implementation which allows controlling this
     * `DataSource` through the "standard" FMJ/JMF `javax.media.Control` means.
     */
    private val imgStreamingControl = object : ImgStreamingControl {
        override fun getControlComponent(): Component? {
            /*
			 * This javax.media.Control implementation provides only programmatic control and not UI control.
			 */
            return null
        }

        override fun setOrigin(streamIndex: Int, displayIndex: Int, x: Int, y: Int) {
            this@DataSource.setOrigin(streamIndex, displayIndex, x, y)
        }
    }

    /**
     * Initializes a new `DataSource` instance.
     */
    constructor() {}

    /**
     * Initializes a new `DataSource` instance from a specific `MediaLocator`.
     *
     * @param locator the `MediaLocator` to initialize the new instance from
     */
    constructor(locator: MediaLocator?) : super(locator) {}

    /**
     * Creates a new `PullBufferStream` which is to be at a specific zero-based index in the
     * list of streams of this `PullBufferDataSource`. The `Format`-related
     * information of the new instance is to be abstracted by a specific `FormatControl`.
     *
     * @param streamIndex the zero-based index of the `PullBufferStream` in the list of streams of this `PullBufferDataSource`
     * @param formatControl the `FormatControl` which is to abstract the `Format`-related information of the new instance
     * @return a new `PullBufferStream` which is to be at the specified `streamIndex`
     * in the list of streams of this `PullBufferDataSource` and which has its
     * `Format`-related information abstracted by the specified `formatControl`
     * @see AbstractPullBufferCaptureDevice.createStream
     */
    override fun createStream(streamIndex: Int, formatControl: FormatControl?): ImageStream {
        /*
		 * full desktop: remainder => index part of desktop: remainder => index,x,y
		 */
        val remainder = locator.remainder
        val split = remainder.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val displayIndex: Int
        val x: Int
        val y: Int
        if (split != null && split.size > 1) {
            displayIndex = split[0].toInt()
            x = split[1].toInt()
            y = split[2].toInt()
        } else {
            displayIndex = remainder.toInt()
            x = 0
            y = 0
        }
        val stream = ImageStream(this, formatControl)
        stream.setDisplayIndex(displayIndex)
        stream.setOrigin(x, y)
        return stream
    }

    /**
     * Gets the control of the specified type available for this instance.
     *
     * @param controlType the type of the control available for this instance to be retrieved
     * @return an `Object` which represents the control of the specified type available for
     * this instance if such a control is indeed available; otherwise, `null`
     */
    override fun getControl(controlType: String): Any? {
        /*
		 * TODO As a matter of fact, we have to override getControls() and not getControl(String).
		 * However, overriding getControls() is much more complex and the ImgStreamingControl is too
		 * obscure. Besides, the ImgStreamingControl implementation of this DataSource does not
		 * provide UI control so it makes no sense for the caller to try to get it through getControls().
		 */
        return if (ImgStreamingControl::class.java.name == controlType) imgStreamingControl else super.getControl(controlType)
    }

    /**
     * Set the display index and the origin of the `ImageStream` associated with a specific index in this `DataSource`.
     *
     * @param streamIndex the index in this `DataSource` of the `ImageStream` to set the display index and the origin of
     * @param displayIndex the display index to set on the specified `ImageStream`
     * @param x the x coordinate of the origin to set on the specified `ImageStream`
     * @param y the y coordinate of the origin to set on the specified `ImageStream`
     */
    fun setOrigin(streamIndex: Int, displayIndex: Int, x: Int, y: Int) {
        synchronized(streamSyncRoot) {
            val streams = streams()
            if (streams != null && streamIndex < streams.size) {
                val stream = streams[streamIndex] as ImageStream?
                if (stream != null) {
                    stream.setDisplayIndex(displayIndex)
                    stream.setOrigin(x, y)
                }
            }
        }
    }
}