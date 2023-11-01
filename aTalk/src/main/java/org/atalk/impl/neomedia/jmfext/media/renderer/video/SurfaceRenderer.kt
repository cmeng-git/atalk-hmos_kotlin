/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.renderer.video

import android.content.Context
import android.view.Surface
import android.view.View
import org.atalk.impl.neomedia.codec.video.AndroidDecoder
import org.atalk.impl.neomedia.jmfext.media.renderer.AbstractRenderer
import org.atalk.service.neomedia.ViewAccessor
import org.atalk.service.neomedia.codec.Constants
import timber.log.Timber
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import javax.media.*
import javax.media.format.VideoFormat
import javax.media.renderer.VideoRenderer

/**
 * Dummy renderer used only to construct valid codec graph when decoding into `Surface` is enabled.
 * The actual video rendering is performed by MediaCodec, i.e. codec.configure(format, surface, null, 0)
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 * @see AndroidDecoder.configureMediaCodec
 */
class SurfaceRenderer : AbstractRenderer<VideoFormat>(), VideoRenderer {
    private var component: Component? = null
    override fun getSupportedInputFormats(): Array<Format?> {
        return INPUT_FORMATS
    }

    override fun process(buffer: Buffer): Int {
        return 0
    }

    override fun start() {}
    override fun stop() {}
    override fun close() {}
    override fun getName(): String {
        return "SurfaceRenderer"
    }

    @Throws(ResourceUnavailableException::class)
    override fun open() {
    }

    override fun setInputFormat(format: Format): Format {
        val newFormat = super.setInputFormat(format) as VideoFormat
        Timber.d("Set input format: %s = > %s", format, newFormat)
        if (newFormat.size != null) {
            getComponent().preferredSize = Dimension(newFormat.size)
        }
        return newFormat
    }

    override fun getBounds(): Rectangle? {
        return null
    }

    override fun setBounds(rectangle: Rectangle) {}
    override fun getComponent(): Component {
        if (component == null) {
            component = SurfaceComponent()
        }
        return component!!
    }

    override fun setComponent(component: Component): Boolean {
        return false
    }

    private class SurfaceComponent : Component(), ViewAccessor {
        override fun getView(context: Context?): View? {
            return AndroidDecoder.renderSurfaceProvider!!.view
        }
    }

    companion object {
        private val INPUT_FORMATS = arrayOf<Format?>(
                VideoFormat(
                        Constants.ANDROID_SURFACE,
                        null,
                        Format.NOT_SPECIFIED,
                        Surface::class.java,
                        Format.NOT_SPECIFIED.toFloat())
        )
    }
}