/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.impl.neomedia.MediaServiceImpl
import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.codec.video.AVFrameFormat
import org.atalk.util.MediaType
import org.atalk.util.OSUtils
import java.awt.Dimension
import java.awt.Toolkit
import javax.media.*
import javax.media.format.RGBFormat

/**
 * Add ImageStreaming capture device.
 *
 * @author Sebastien Vincent
 */
class ImgStreamingSystem
    /**
     * Add capture devices.
     *
     * @throws Exception if problem when adding capture devices
     */
    : DeviceSystem(MediaType.VIDEO, LOCATOR_PROTOCOL, FEATURE_REINITIALIZE) {

    @Throws(Exception::class)
    override fun doInitialize() {
        /*
         * XXX The initialization of MediaServiceImpl is very complex so it is wise to not reference
         * it at the early stage of its initialization.
         */
        val screens = ScreenDeviceImpl.availableScreenDevices
        val name = "Desktop Streaming"
        var multipleMonitorsOneScreen = false
        var screenSize: Dimension? = null

        /*
         * On Linux, multiple monitors may result in a single X display (:0.0) which combines them.
         */
        if (OSUtils.IS_LINUX) {
            val size = Dimension(0, 0)
            for (screen in screens) {
                val s = screen.size
                size.width += s!!.width
                size.height += s.height
            }
            try {
                screenSize = Toolkit.getDefaultToolkit().screenSize
                if (screenSize.width == size.width || screenSize.height == size.height) {
                    multipleMonitorsOneScreen = true
                }
            } catch (ignore: Exception) {
            }
        }

        for ((i, screen) in screens.withIndex()) {
            val size = screenSize ?: screen.size!!
            val formats = arrayOf<Format>(
                    AVFrameFormat(size, Format.NOT_SPECIFIED.toFloat(), FFmpeg.PIX_FMT_ARGB,
                            Format.NOT_SPECIFIED),
                    RGBFormat(size,  // size
                            Format.NOT_SPECIFIED,  // maxDataLength
                            Format.byteArray,  // dataType
                            Format.NOT_SPECIFIED.toFloat(),  // frameRate
                            32,  // bitsPerPixel
                            2 /* red */, 3 /* green */, 4 /* blue */))

            val cdi = CaptureDeviceInfo("$name $i",
                    MediaLocator("$LOCATOR_PROTOCOL:$i"), formats)
            CaptureDeviceManager.addDevice(cdi)
            if (multipleMonitorsOneScreen) break
        }
        if (!MediaServiceImpl.isJmfRegistryDisableLoad) CaptureDeviceManager.commit()
    }

    companion object {
        /**
         * The locator protocol used when creating or parsing `MediaLocator` s.
         */
        private const val LOCATOR_PROTOCOL = LOCATOR_PROTOCOL_IMGSTREAMING
    }
}