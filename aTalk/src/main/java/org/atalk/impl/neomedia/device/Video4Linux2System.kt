/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.impl.neomedia.MediaServiceImpl
import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.codec.video.AVFrameFormat
import org.atalk.impl.neomedia.jmfext.media.protocol.video4linux2.DataSource
import org.atalk.impl.neomedia.jmfext.media.protocol.video4linux2.Video4Linux2
import org.atalk.util.MediaType
import timber.log.Timber
import javax.media.CaptureDeviceInfo
import javax.media.CaptureDeviceManager
import javax.media.Format
import javax.media.MediaLocator

/**
 * Discovers and registers `CaptureDevice`s which implement the Video for Linux Two API
 * Specification with JMF.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class Video4Linux2System
/**
 * Initializes a new `Video4Linux2System` instance which discovers and registers
 * `CaptureDevice`s which implement the Video for Linux Two API Specification with JMF.
 *
 * @throws Exception if anything goes wrong while discovering and registering `CaptureDevice`s
 * which implement the Video for Linux Two API Specification with JMF
 */
    : DeviceSystem(MediaType.VIDEO, LOCATOR_PROTOCOL) {
    /**
     * Discovers and registers a `CaptureDevice` implementing the Video for Linux Two API
     * Specification with a specific device name with JMF.
     *
     * @param deviceName the device name of a candidate for a `CaptureDevice` implementing the Video for
     * Linux Two API Specification to be discovered and registered with JMF
     * @return `true` if a `CaptureDeviceInfo` for the specified
     * `CaptureDevice` has been added to `CaptureDeviceManager`; otherwise,
     * `false`
     * @throws Exception if anything goes wrong while discovering and registering the specified
     * `CaptureDevice` with JMF
     */
    @Throws(Exception::class)
    private fun discoverAndRegister(deviceName: String): Boolean {
        val fd = Video4Linux2.open(deviceName, Video4Linux2.O_RDWR)
        var captureDeviceInfoIsAdded = false
        if (-1 != fd) {
            try {
                val v4l2_capability = Video4Linux2.v4l2_capability_alloc()
                if (0L != v4l2_capability) {
                    try {
                        if (Video4Linux2.ioctl(fd, Video4Linux2.VIDIOC_QUERYCAP, v4l2_capability) != -1 && Video4Linux2.v4l2_capability_getCapabilities(v4l2_capability) and Video4Linux2.V4L2_CAP_VIDEO_CAPTURE == Video4Linux2.V4L2_CAP_VIDEO_CAPTURE) {
                            captureDeviceInfoIsAdded = register(deviceName, fd, v4l2_capability)
                        }
                    } finally {
                        Video4Linux2.free(v4l2_capability)
                    }
                }
            } finally {
                Video4Linux2.close(fd)
            }
        }
        return captureDeviceInfoIsAdded
    }

    @Throws(Exception::class)
    override fun doInitialize() {
        val baseDeviceName = "/dev/video"
        var captureDeviceInfoIsAdded = discoverAndRegister(baseDeviceName)
        for (deviceMinorNumber in 0..63) {
            captureDeviceInfoIsAdded = (discoverAndRegister(baseDeviceName + deviceMinorNumber)
                    || captureDeviceInfoIsAdded)
        }
        if (captureDeviceInfoIsAdded && !MediaServiceImpl.isJmfRegistryDisableLoad) CaptureDeviceManager.commit()
    }

    /**
     * Registers a `CaptureDevice` implementing the Video for Linux Two API Specification
     * with a specific device name, a specific `open()` file descriptor and a specific
     * `v4l2_capability` with JMF.
     *
     * @param deviceName name of the device (i.e. /dev/videoX)
     * @param fd file descriptor of the device
     * @param v4l2_capability device V4L2 capability
     * @return `true` if a `CaptureDeviceInfo` for the specified
     * `CaptureDevice` has been added to `CaptureDeviceManager`; otherwise,
     * `false`
     * @throws Exception if anything goes wrong while registering the specified `CaptureDevice` with
     * JMF
     */
    @Throws(Exception::class)
    private fun register(deviceName: String, fd: Int, v4l2_capability: Long): Boolean {
        val v4l2_format = Video4Linux2.v4l2_format_alloc(Video4Linux2.V4L2_BUF_TYPE_VIDEO_CAPTURE)
        var pixelformat = 0
        var supportedRes: String? = null
        if (0L != v4l2_format) {
            try {
                if (Video4Linux2.ioctl(fd, Video4Linux2.VIDIOC_G_FMT, v4l2_format) != -1) {
                    val fmtPix = Video4Linux2.v4l2_format_getFmtPix(v4l2_format)
                    pixelformat = Video4Linux2.v4l2_pix_format_getPixelformat(fmtPix)
                    if (FFmpeg.PIX_FMT_NONE == DataSource.getFFmpegPixFmt(pixelformat)) {
                        Video4Linux2.v4l2_pix_format_setPixelformat(fmtPix,
                                Video4Linux2.V4L2_PIX_FMT_RGB24)
                        if (Video4Linux2.ioctl(fd, Video4Linux2.VIDIOC_S_FMT, v4l2_format) != -1) {
                            pixelformat = Video4Linux2.v4l2_pix_format_getPixelformat(fmtPix)
                        }
                    }
                    supportedRes = Video4Linux2.v4l2_pix_format_getWidth(fmtPix).toString() + "x" +
                           Video4Linux2.v4l2_pix_format_getHeight(fmtPix).toString()
                }
            } finally {
                Video4Linux2.free(v4l2_format)
            }
        }
        val format: Format
        val ffmpegPixFmt = DataSource.getFFmpegPixFmt(pixelformat)
        if (FFmpeg.PIX_FMT_NONE != ffmpegPixFmt) format = AVFrameFormat(ffmpegPixFmt, pixelformat) else return false
        var name = Video4Linux2.v4l2_capability_getCard(v4l2_capability)
        if (name == null || name.isEmpty()) name = deviceName else name += " ($deviceName)"
        if (supportedRes != null) {
            Timber.i("Webcam available resolution for %s:%s", name, supportedRes)
        }
        CaptureDeviceManager.addDevice(CaptureDeviceInfo(name, MediaLocator(
                "$LOCATOR_PROTOCOL:$deviceName"), arrayOf<Format>(format)))
        return true
    }

    companion object {
        /**
         * The protocol of the `MediaLocator`s identifying `CaptureDevice` which implement
         * the Video for Linux Two API Specification.
         */
        private const val LOCATOR_PROTOCOL = LOCATOR_PROTOCOL_VIDEO4LINUX2
    }
}