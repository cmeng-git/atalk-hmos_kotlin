/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.video4linux2

import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.device.DeviceSystem
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferCaptureDevice
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractVideoPullBufferCaptureDevice
import java.io.IOException
import javax.media.MediaLocator
import javax.media.control.FormatControl

/**
 * Implements a `PullBufferDataSource` and `CaptureDevice` using the Video for Linux
 * Two API Specification.
 *
 * @author Lyubomir Marinov
 */
open class DataSource : AbstractVideoPullBufferCaptureDevice {
    /**
     * The file descriptor of the opened Video for Linux Two API Specification device represented by
     * this `DataSource`.
     */
    private var fd = -1

    /**
     * Initializes a new `DataSource` instance.
     */
    constructor()

    /**
     * Initializes a new `DataSource` instance from a specific `MediaLocator`.
     *
     * @param locator
     * the `MediaLocator` to create the new instance from
     */
    constructor(locator: MediaLocator?) : super(locator)

    /**
     * Creates a new `PullBufferStream` which is to be at a specific zero-based index in the
     * list of streams of this `PullBufferDataSource`. The `Format`-related
     * information of the new instance is to be abstracted by a specific `FormatControl`.
     *
     * @param streamIndex
     * the zero-based index of the `PullBufferStream` in the list of streams of this
     * `PullBufferDataSource`
     * @param formatControl
     * the `FormatControl` which is to abstract the `Format`-related
     * information of the new instance
     * @return a new `PullBufferStream` which is to be at the specified `streamIndex`
     * in the list of streams of this `PullBufferDataSource` and which has its
     * `Format`-related information abstracted by the specified
     * `formatControl`
     */
    override fun createStream(streamIndex: Int, formatControl: FormatControl?): Video4Linux2Stream {
        return Video4Linux2Stream(this, formatControl)
    }

    /**
     * Opens a connection to the media source specified by the `MediaLocator` of this
     * `DataSource`.
     *
     * @throws IOException
     * if anything goes wrong while opening the connection to the media source specified by
     * the `MediaLocator` of this `DataSource`
     * @see AbstractPullBufferCaptureDevice.doConnect
     */
    @Throws(IOException::class)
    override fun doConnect() {
        super.doConnect()
        val deviceName = deviceName
        var fd = Video4Linux2.open(deviceName, Video4Linux2.O_RDWR)
        if (-1 == fd) throw IOException("Failed to open $deviceName") else {
            var close = true

            try {
                synchronized(streamSyncRoot) { for (stream in streams) (stream as Video4Linux2Stream).setFd(fd) }
                close = false
            } finally {
                if (close) {
                    Video4Linux2.close(fd)
                    fd = -1
                }
            }

            this.fd = fd
        }
    }

    /**
     * Closes the connection to the media source specified by the `MediaLocator` of this
     * `DataSource`.
     */
    override fun doDisconnect() {
        try {
            /*
			 * Letting the Video4Linux2Stream know that the fd is going to be closed is necessary at
			 * least because AbstractPullBufferStream#close() is not guaranteed.
			 */
            synchronized(streamSyncRoot) {
                val streams = streams()
                if (streams != null) {
                    for (stream in streams) {
                        try {
                            (stream as Video4Linux2Stream).setFd(-1)
                        } catch (ignore: IOException) {
                        }
                    }
                }
            }
        } finally {
            try {
                super.doDisconnect()
            } finally {
                Video4Linux2.close(fd)
            }
        }
    }

    /**
     * Gets the name of the Video for Linux Two API Specification device which represents the media
     * source of this `DataSource`.
     *
     * @return the name of the Video for Linux Two API Specification device which represents the
     * media source of this `DataSource`
     */
    private val deviceName: String?
        get() {
            val locator = locator
            return if (locator != null && DeviceSystem.LOCATOR_PROTOCOL_VIDEO4LINUX2
                            .equals(locator.protocol, ignoreCase = true)) locator.remainder else null
        }

    companion object {
        /**
         * The map of Video for Linux Two API Specification pixel formats to FFmpeg pixel formats which
         * allows converting between the two.
         */
        private val V4L2_TO_FFMPEG_PIX_FMT = intArrayOf(Video4Linux2.V4L2_PIX_FMT_UYVY,
                FFmpeg.PIX_FMT_UYVY422, Video4Linux2.V4L2_PIX_FMT_YUV420, FFmpeg.PIX_FMT_YUV420P,
                Video4Linux2.V4L2_PIX_FMT_YUYV, FFmpeg.PIX_FMT_YUYV422, Video4Linux2.V4L2_PIX_FMT_MJPEG,
                FFmpeg.PIX_FMT_YUVJ422P, Video4Linux2.V4L2_PIX_FMT_JPEG, FFmpeg.PIX_FMT_YUVJ422P,
                Video4Linux2.V4L2_PIX_FMT_RGB24, FFmpeg.PIX_FMT_RGB24_1, Video4Linux2.V4L2_PIX_FMT_BGR24,
                FFmpeg.PIX_FMT_BGR24_1)

        /**
         * Gets the Video for Linux Two API Specification pixel format matching a specific FFmpeg pixel
         * format.
         *
         * @param v4l2PixFmt
         * the FFmpeg pixel format to get the matching Video for Linux Two API Specification
         * pixel format of
         * @return the Video for Linux Two API Specification pixel format matching the specified FFmpeg
         * format
         */
        fun getFFmpegPixFmt(v4l2PixFmt: Int): Int {
            var i = 0
            while (i < V4L2_TO_FFMPEG_PIX_FMT.size) {
                if (V4L2_TO_FFMPEG_PIX_FMT[i] == v4l2PixFmt) return V4L2_TO_FFMPEG_PIX_FMT[i + 1]
                i += 2
            }
            return FFmpeg.PIX_FMT_NONE
        }

        /**
         * Gets the FFmpeg pixel format matching a specific Video for Linux Two API Specification pixel
         * format.
         *
         * @param ffmpegPixFmt
         * the Video for Linux Two API Specification pixel format to get the matching FFmpeg
         * pixel format of
         * @return the FFmpeg pixel format matching the specified Video for Linux Two API Specification
         * pixel format
         */
        fun getV4L2PixFmt(ffmpegPixFmt: Int): Int {
            var i = 0
            while (i < V4L2_TO_FFMPEG_PIX_FMT.size) {
                if (V4L2_TO_FFMPEG_PIX_FMT[i + 1] == ffmpegPixFmt) return V4L2_TO_FFMPEG_PIX_FMT[i]
                i += 2
            }
            return Video4Linux2.V4L2_PIX_FMT_NONE
        }
    }
}