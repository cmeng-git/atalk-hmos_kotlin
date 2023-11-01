/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.impl.neomedia.MediaServiceImpl
import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.codec.video.AVFrameFormat
import org.atalk.impl.neomedia.quicktime.QTCaptureDevice
import org.atalk.impl.neomedia.quicktime.QTFormatDescription
import org.atalk.impl.neomedia.quicktime.QTMediaType
import org.atalk.util.MediaType
import timber.log.Timber
import javax.media.CaptureDeviceInfo
import javax.media.CaptureDeviceManager
import javax.media.Format
import javax.media.MediaLocator
import javax.media.format.RGBFormat

/**
 * Discovers and registers QuickTime/QTKit capture devices with JMF.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class QuickTimeSystem
/**
 * Initializes a new `QuickTimeSystem` instance which discovers and registers
 * QuickTime/QTKit capture devices with JMF.
 *
 * @throws Exception if anything goes wrong while discovering and registering QuickTime/QTKit capture defines with JMF
 */
    : DeviceSystem(MediaType.VIDEO, LOCATOR_PROTOCOL) {
    @Throws(Exception::class)
    override fun doInitialize() {
        val inputDevices = QTCaptureDevice.inputDevicesWithMediaType(QTMediaType.Video)
        var captureDeviceInfoIsAdded = false
        for (inputDevice in inputDevices) {
            val device = CaptureDeviceInfo(inputDevice!!.localizedDisplayName(),
                    MediaLocator(LOCATOR_PROTOCOL + ':' + inputDevice!!.uniqueID()), arrayOf<Format>(
                    AVFrameFormat(FFmpeg.PIX_FMT_ARGB), RGBFormat()))
            for (fd in inputDevice!!.formatDescriptions()) {
                Timber.i("Webcam available resolution for %s:%s", inputDevice.localizedDisplayName(),
                        fd!!.sizeForKey(QTFormatDescription.VideoEncodedPixelsSizeAttribute))
            }
            CaptureDeviceManager.addDevice(device)
            captureDeviceInfoIsAdded = true
            Timber.d("Added CaptureDeviceInfo %s", device)
        }
        if (captureDeviceInfoIsAdded && !MediaServiceImpl.isJmfRegistryDisableLoad) CaptureDeviceManager.commit()
    }

    companion object {
        /**
         * The protocol of the `MediaLocator`s identifying QuickTime/QTKit capture devices.
         */
        private const val LOCATOR_PROTOCOL = LOCATOR_PROTOCOL_QUICKTIME
    }
}