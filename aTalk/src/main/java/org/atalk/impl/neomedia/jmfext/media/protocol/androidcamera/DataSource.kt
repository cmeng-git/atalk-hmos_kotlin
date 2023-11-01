/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.androidcamera

import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferCaptureDevice
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferStream
import org.atalk.service.neomedia.codec.Constants
import timber.log.Timber
import javax.media.Format
import javax.media.MediaLocator
import javax.media.control.FormatControl
import javax.media.format.VideoFormat

/**
 * Camera data source. Creates `PreviewStream` or `SurfaceStream` based on the used encode format.
 *
 * @author Pawel Domas
 */
class DataSource : AbstractPushBufferCaptureDevice {
    constructor()
    constructor(locator: MediaLocator?) : super(locator) {}

    override fun createStream(streamIndex: Int, formatControl: FormatControl): AbstractPushBufferStream<*> {
        val encoding = formatControl.format.encoding
        Timber.d("createStream: %s", encoding)
        return if (encoding == Constants.ANDROID_SURFACE) {
            SurfaceStream(this, formatControl)
        } else {
            PreviewStream(this, formatControl)
        }
    }

    override fun setFormat(streamIndex: Int, oldValue: Format?, newValue: Format?): Format? {
        // This DataSource VideoFormat supports setFormat.
        return newValue as? VideoFormat ?: super.setFormat(streamIndex, oldValue, newValue)
    }
}