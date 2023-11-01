/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.video4linux2

import org.atalk.impl.neomedia.NeomediaServiceUtils
import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.codec.video.AVFrame
import org.atalk.impl.neomedia.codec.video.AVFrameFormat
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferStream
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractVideoPullBufferStream
import org.atalk.impl.neomedia.jmfext.media.protocol.ByteBufferPool
import java.awt.Dimension
import java.io.IOException
import javax.media.Buffer
import javax.media.Format
import javax.media.control.FormatControl
import javax.media.format.VideoFormat

/**
 * Implements a `PullBufferStream` using the Video for Linux Two API Specification.
 *
 * @author Lyubomir Marinov
 */
class Video4Linux2Stream(dataSource: DataSource?, formatControl: FormatControl?) : AbstractVideoPullBufferStream<DataSource?>(dataSource, formatControl) {
    /**
     * The `AVCodecContext` of the MJPEG decoder.
     */
    private var avctx = 0L

    /**
     * The `AVFrame` which represents the media data decoded by the MJPEG decoder/
     * [.avctx].
     */
    private var avframe = 0L

    /**
     * The pool of `ByteBuffer`s this instances is using to transfer the media data captured
     * by the Video for Linux Two API Specification device out of this instance through the
     * `Buffer`s specified in its [.read].
     */
    private val byteBufferPool = ByteBufferPool()

    /**
     * The capabilities of the Video for Linux Two API Specification device represented by
     * [.fd].
     */
    private var capabilities = 0

    /**
     * The file descriptor of the Video for Linux Two API Specification device read through this
     * `PullBufferStream`.
     */
    private var fd = -1

    /**
     * The last-known `Format` of the media data made available by this `PullBufferStream`
     */
    private var mFormat: Format? = null

    /**
     * The lengths in bytes of the buffers in the application's address space through which the
     * Video for Linux Two API Specification device provides the captured media data to this
     * instance when [.requestbuffersMemory] is equal to `V4L2_MEMORY_MAP`.
     */
    private var mmapLengths: IntArray? = null

    /**
     * The buffers through which the Video for Linux Two API Specification device provides the
     * captured media data to this instance when [.requestbuffersMemory] is equal to
     * `V4L2_MEMORY_MAP`. These are mapped in the application's address space.
     */
    private var mmaps: LongArray? = null

    /**
     * Native Video for Linux Two pixel format.
     */
    private var nativePixelFormat = 0

    /**
     * The number of buffers through which the Video for Linux Two API Specification device provides
     * the captured media data to this instance when [.requestbuffersMemory] is equal to
     * `V4L2_MEMORY_MMAP`.
     */
    private var requestbuffersCount = 0

    /**
     * The input method negotiated by this instance with the Video for Linux Two API Specification
     * device.
     */
    private var requestbuffersMemory = 0

    /**
     * Tell device to start capture in read() method.
     */
    private var startInRead = false

    /**
     * The `v4l2_buffer` instance via which captured media data is fetched from the Video for
     * Linux Two API Specification device to this instance in [.read].
     */
    private var v4l2_buffer: Long

    /**
     * Initializes a new `Video4Linux2Stream` instance which is to have its `Format`
     * -related information abstracted by a specific `FormatControl`.
     */
    init {
        v4l2_buffer = Video4Linux2.v4l2_buffer_alloc(Video4Linux2.V4L2_BUF_TYPE_VIDEO_CAPTURE)
        if (0L == v4l2_buffer) throw OutOfMemoryError("v4l2_buffer_alloc")
        Video4Linux2.v4l2_buffer_setMemory(v4l2_buffer, Video4Linux2.V4L2_MEMORY_MMAP)
    }

    /**
     * Releases the resources used by this instance throughout its existence and makes it available
     * for garbage collection. This instance is considered unusable after closing.
     *
     * @see AbstractPullBufferStream.close
     */
    override fun close() {
        super.close()
        if (v4l2_buffer != 0L) {
            Video4Linux2.free(v4l2_buffer)
            v4l2_buffer = 0
        }
        byteBufferPool.drain()
    }

    /**
     * Gets the `Format` of this `PullBufferStream` as directly known by it.
     *
     * @return the `Format` of this `PullBufferStream` as directly known by it or
     * `null` if this `PullBufferStream` does not directly know its
     * `Format` and it relies on the `PullBufferDataSource` which created it
     * to report its `Format`
     * @see AbstractPullBufferStream.doGetFormat
     */
    override fun doGetFormat(): Format? {
        var format: Format?
        if (this.mFormat == null) {
            format = fdFormat
            when (format) {
                null -> format = super.doGetFormat()
                else -> {
                    val videoFormat = format as VideoFormat
                    if (videoFormat.size != null) this.mFormat = format
                }
            }
        } else format = this.mFormat
        return format
    }

    /**
     * Reads media data from this `PullBufferStream` into a specific `Buffer` with
     * blocking.
     *
     * @param buffer
     * the `Buffer` in which media data is to be read from this
     * `PullBufferStream`
     * @throws IOException
     * if anything goes wrong while reading media data from this `PullBufferStream`
     * into the specified `buffer`
     * @see AbstractVideoPullBufferStream.doRead
     */
    @Throws(IOException::class)
    override fun doRead(buffer: Buffer) {
        var format = buffer.format
        if (format !is AVFrameFormat) format = null
        if (format == null) {
            format = getFormat()
            if (format != null) buffer.format = format
        }
        if (startInRead) {
            startInRead = false
            val v4l2_buf_type = Video4Linux2.v4l2_buf_type_alloc(Video4Linux2.V4L2_BUF_TYPE_VIDEO_CAPTURE)
            if (0L == v4l2_buf_type) throw OutOfMemoryError("v4l2_buf_type_alloc")
            try {
                if (Video4Linux2.ioctl(fd, Video4Linux2.VIDIOC_STREAMON, v4l2_buf_type) == -1) {
                    throw IOException("ioctl: request= VIDIOC_STREAMON")
                }
            } finally {
                Video4Linux2.free(v4l2_buf_type)
            }
        }
        if (Video4Linux2.ioctl(fd, Video4Linux2.VIDIOC_DQBUF, v4l2_buffer) == -1) throw IOException("ioctl: request= VIDIOC_DQBUF")
        val timeStamp = System.nanoTime()
        try {
            val index = Video4Linux2.v4l2_buffer_getIndex(v4l2_buffer)
            val mmap = mmaps!![index]
            val bytesused = Video4Linux2.v4l2_buffer_getBytesused(v4l2_buffer)
            if (nativePixelFormat == Video4Linux2.V4L2_PIX_FMT_JPEG || nativePixelFormat == Video4Linux2.V4L2_PIX_FMT_MJPEG) {
                /* Initialize the FFmpeg MJPEG decoder if necessary. */
                if (avctx == 0L) {
                    val avcodec = FFmpeg.avcodec_find_decoder(FFmpeg.CODEC_ID_MJPEG)
                    avctx = FFmpeg.avcodec_alloc_context3(avcodec)
                    FFmpeg.avcodeccontext_set_workaround_bugs(avctx, FFmpeg.FF_BUG_AUTODETECT)
                    if (FFmpeg.avcodec_open2(avctx, avcodec) < 0) {
                        throw RuntimeException("" + "Could not open codec CODEC_ID_MJPEG")
                    }
                    avframe = FFmpeg.avcodec_alloc_frame()
                }
                if (FFmpeg.avcodec_decode_video(avctx, avframe, mmap, bytesused) != -1) {
                    val out = buffer.data
                    if (out !is AVFrame || out.ptr != avframe) {
                        buffer.data = AVFrame(avframe)
                    }
                }
            } else {
                val data = byteBufferPool.getBuffer(bytesused)
                if (data != null) {
                    Video4Linux2.memcpy(data.ptr, mmap, bytesused)
                    data.setLength(bytesused)
                    if (AVFrame.read(buffer, format, data) < 0) data.free()
                }
            }
        } finally {
            if (Video4Linux2.ioctl(fd, Video4Linux2.VIDIOC_QBUF, v4l2_buffer) == -1) {
                throw IOException("ioctl: request= VIDIOC_QBUF")
            }
        }
        buffer.flags = Buffer.FLAG_LIVE_DATA or Buffer.FLAG_SYSTEM_TIME
        buffer.timeStamp = timeStamp
    }

    /**
     * Gets the `Format` of the media data captured by the Video for Linux Two API
     * Specification device represented by the `fd` of this instance.
     *
     * @return the `Format` of the media data captured by the Video for Linux Two API
     * Specification device represented by the `fd` of this instance
     */
    private val fdFormat: Format?
        get() {
            var format: Format? = null
            if (-1 != fd) {
                val v4l2_format = Video4Linux2.v4l2_format_alloc(Video4Linux2.V4L2_BUF_TYPE_VIDEO_CAPTURE)
                if (v4l2_format == 0L) throw OutOfMemoryError("v4l2_format_alloc") else {
                    try {
                        if (Video4Linux2.ioctl(fd, Video4Linux2.VIDIOC_G_FMT, v4l2_format) != -1) {
                            val fmtPix = Video4Linux2.v4l2_format_getFmtPix(v4l2_format)
                            val pixelFormat = Video4Linux2.v4l2_pix_format_getPixelformat(fmtPix)
                            val ffmpegPixFmt = DataSource.getFFmpegPixFmt(pixelFormat)
                            if (FFmpeg.PIX_FMT_NONE != ffmpegPixFmt) {
                                val width = Video4Linux2.v4l2_pix_format_getWidth(fmtPix)
                                val height = Video4Linux2.v4l2_pix_format_getHeight(fmtPix)
                                format = AVFrameFormat(Dimension(width, height),
                                        Format.NOT_SPECIFIED.toFloat(), ffmpegPixFmt, pixelFormat)
                            }
                        }
                    } finally {
                        Video4Linux2.free(v4l2_format)
                    }
                }
            }
            return format
        }

    /**
     * Unmaps the buffers through which the Video for Linux Two API Specification device provides
     * the captured media data to this instance when [.requestbuffersMemory] is equal to
     * `V4L2_MEMORY_MMAP` i.e. breaks the buffers' mappings between the driver's and the
     * application's address spaces.
     */
    private fun munmap() {
        try {
            if (mmaps != null) {
                for (i in mmaps!!.indices) {
                    val mmap = mmaps!![i]
                    if (mmap != 0L) {
                        Video4Linux2.munmap(mmap, mmapLengths!![i])
                        mmaps!![i] = 0
                        mmapLengths!![i] = 0
                    }
                }
            }
        } finally {
            mmaps = null
            mmapLengths = null
        }
    }

    /**
     * Negotiates the input method with the Video for Linux Two API Specification device represented
     * by the `fd` of this instance.
     *
     * @throws IOException
     * if anything goes wrong while negotiating the input method with the Video for Linux
     * Two API Specification device represented by the `fd` of this instance
     */
    @Throws(IOException::class)
    private fun negotiateFdInputMethod() {
        val v4l2_capability = Video4Linux2.v4l2_capability_alloc()
        if (0L == v4l2_capability) throw OutOfMemoryError("v4l2_capability_alloc")
        capabilities = try {
            if (Video4Linux2.ioctl(fd, Video4Linux2.VIDIOC_QUERYCAP, v4l2_capability) == -1) throw IOException("ioctl: request= VIDIOC_QUERYCAP")
            Video4Linux2.v4l2_capability_getCapabilities(v4l2_capability)
        } finally {
            Video4Linux2.free(v4l2_capability)
        }
        if (capabilities and Video4Linux2.V4L2_CAP_STREAMING != Video4Linux2.V4L2_CAP_STREAMING) throw IOException("Non-streaming V4L2 device not supported.")
        val v4l2_requestbuffers = Video4Linux2.v4l2_requestbuffers_alloc(Video4Linux2.V4L2_BUF_TYPE_VIDEO_CAPTURE)
        if (0L == v4l2_requestbuffers) throw OutOfMemoryError("v4l2_requestbuffers_alloc")
        try {
            requestbuffersMemory = Video4Linux2.V4L2_MEMORY_MMAP
            Video4Linux2.v4l2_requestbuffers_setMemory(v4l2_requestbuffers, requestbuffersMemory)
            Video4Linux2.v4l2_requestbuffers_setCount(v4l2_requestbuffers, 2)
            if (Video4Linux2.ioctl(fd, Video4Linux2.VIDIOC_REQBUFS, v4l2_requestbuffers) == -1) {
                throw IOException("ioctl: request= VIDIOC_REQBUFS, memory= "
                        + requestbuffersMemory)
            }
            requestbuffersCount = Video4Linux2.v4l2_requestbuffers_getCount(v4l2_requestbuffers)
        } finally {
            Video4Linux2.free(v4l2_requestbuffers)
        }
        if (requestbuffersCount < 1) throw IOException("Insufficient V4L2 device memory.")
        val v4l2_buffer = Video4Linux2.v4l2_buffer_alloc(Video4Linux2.V4L2_BUF_TYPE_VIDEO_CAPTURE)
        if (0L == v4l2_buffer) throw OutOfMemoryError("v4l2_buffer_alloc")
        try {
            Video4Linux2.v4l2_buffer_setMemory(v4l2_buffer, Video4Linux2.V4L2_MEMORY_MMAP)
            mmaps = LongArray(requestbuffersCount)
            mmapLengths = IntArray(requestbuffersCount)
            var munmap = true
            try {
                for (i in 0 until requestbuffersCount) {
                    Video4Linux2.v4l2_buffer_setIndex(v4l2_buffer, i)
                    if (Video4Linux2.ioctl(fd, Video4Linux2.VIDIOC_QUERYBUF, v4l2_buffer) == -1) {
                        throw IOException("ioctl: request= VIDIOC_QUERYBUF")
                    }
                    val length = Video4Linux2.v4l2_buffer_getLength(v4l2_buffer)
                    val offset = Video4Linux2.v4l2_buffer_getMOffset(v4l2_buffer)
                    val mmap = Video4Linux2.mmap(0, length, Video4Linux2.PROT_READ
                            or Video4Linux2.PROT_WRITE, Video4Linux2.MAP_SHARED, fd, offset)
                    if (-1L == mmap) throw IOException("mmap")
                    mmaps!![i] = mmap
                    mmapLengths!![i] = length
                }
                munmap = false
            } finally {
                if (munmap) munmap()
            }
        } finally {
            Video4Linux2.free(v4l2_buffer)
        }
    }

    /**
     * Sets the file descriptor of the Video for Linux Two API Specification device which is to be
     * read through this `PullBufferStream`.
     *
     * @param fd
     * the file descriptor of the Video for Linux Two API Specification device which is to be
     * read through this `PullBufferStream`
     * @throws IOException
     * if anything goes wrong while setting the file descriptor of the Video for Linux Two
     * API Specification device which is to be read through this `PullBufferStream`
     */
    @Throws(IOException::class)
    fun setFd(fd: Int) {
        if (this.fd != fd) {
            if (this.fd != -1) {
                try {
                    stop()
                } catch (ignore: IOException) {
                }
                munmap()
            }

            /*
			 * Before a Video for Linux Two API Specification device can be read, an attempt to set
			 * its format must be made and its cropping must be reset. We can only learn about the
			 * format to be set from formatControl. But since this AbstractPullBufferStream exists
			 * already, formatControl will ask it about its format. So pretend that there is no
			 * device prior to asking formatControl about the format in order to get the format that
			 * has been set by the user.
			 */
            this.fd = -1
            capabilities = 0
            requestbuffersMemory = 0
            requestbuffersCount = 0
            if (fd != -1) {
                val format = this.mFormat
                this.fd = fd
                if (format != null) setFdFormat(format)
                setFdCropToDefault()
                negotiateFdInputMethod()
            }
        }
    }

    /**
     * Sets the crop of the Video for Linux Two API Specification device represented by the
     * `fd` of this instance to its default value so that this `PullBufferStream`
     * reads media data without cropping.
     */
    private fun setFdCropToDefault() {
        // TODO Auto-generated method stub
    }

    /**
     * Sets the `Format` in which the Video for Linux Two API Specification device
     * represented by the `fd` of this instance is to capture media data.
     *
     * @param format
     * the `Format` of the media data to be captured by the Video for Linux Two API
     * Specification device represented by the `fd` of this instance
     * @throws IOException
     * if anything goes wrong while setting the `Format` of the media data to be
     * captured by the Video for Linux Two API Specification device represented by the
     * `fd` of this instance
     */
    @Throws(IOException::class)
    private fun setFdFormat(format: Format) {
        var pixelformat = 0
        if (format is AVFrameFormat) {
            pixelformat = format.deviceSystemPixFmt
            nativePixelFormat = pixelformat
        }
        if (Video4Linux2.V4L2_PIX_FMT_NONE == pixelformat) throw IOException("Unsupported format $format")
        val v4l2_format = Video4Linux2.v4l2_format_alloc(Video4Linux2.V4L2_BUF_TYPE_VIDEO_CAPTURE)
        if (v4l2_format == 0L) throw OutOfMemoryError("v4l2_format_alloc")
        try {
            if (Video4Linux2.ioctl(fd, Video4Linux2.VIDIOC_G_FMT, v4l2_format) == -1) throw IOException("ioctl: request= VIDIO_G_FMT")
            val videoFormat = format as VideoFormat
            var size = videoFormat.size
            val fmtPix = Video4Linux2.v4l2_format_getFmtPix(v4l2_format)
            val width = Video4Linux2.v4l2_pix_format_getWidth(fmtPix)
            val height = Video4Linux2.v4l2_pix_format_getHeight(fmtPix)
            var setFdFormat = false
            if (size == null) {
                // if there is no size in the format, respect settings
                size = NeomediaServiceUtils.mediaServiceImpl!!.deviceConfiguration.getVideoSize()
            }
            if (size.width != width || size.height != height) {
                Video4Linux2.v4l2_pix_format_setWidthAndHeight(fmtPix, size.width, size.height)
                setFdFormat = true
            }
            if (Video4Linux2.v4l2_pix_format_getPixelformat(v4l2_format) != pixelformat) {
                Video4Linux2.v4l2_pix_format_setPixelformat(fmtPix, pixelformat)
                setFdFormat = true
            }
            if (setFdFormat) setFdFormat(v4l2_format, fmtPix, size, pixelformat)
        } finally {
            Video4Linux2.free(v4l2_format)
        }
    }

    /**
     * Sets the `Format` in which the Video for Linux Two API Specification device
     * represented by the `fd` of this instance is to capture media data.
     *
     * @param v4l2_format native format to set on the Video for Linux Two API Specification device
     * @param fmtPix native pixel format of the device
     * @param size size to set on the device
     * @param pixelFormat requested pixel format
     * @throws IOException
     * if anything goes wrong while setting the native format of the media data to be
     * captured by the Video for Linux Two API Specification device represented by the
     * `fd` of this instance
     */
    @Throws(IOException::class)
    private fun setFdFormat(v4l2_format: Long, fmtPix: Long, size: Dimension?, pixelFormat: Int) {
        Video4Linux2.v4l2_pix_format_setField(fmtPix, Video4Linux2.V4L2_FIELD_NONE)
        Video4Linux2.v4l2_pix_format_setBytesperline(fmtPix, 0)
        if (Video4Linux2.ioctl(fd, Video4Linux2.VIDIOC_S_FMT, v4l2_format) == -1) {
            throw IOException("ioctl: request= VIDIOC_S_FMT"
                    + (if (size == null) "" else ", size= " + size.width + "x" + size.height)
                    + ", pixelformat= " + pixelFormat)
        } else if (Video4Linux2.v4l2_pix_format_getPixelformat(fmtPix) != pixelFormat) {
            throw IOException("Failed to change the format of the V4L2 device to " + pixelFormat)
        }
    }

    /**
     * Starts the transfer of media data from this `PullBufferStream`.
     *
     * @throws IOException
     * if anything goes wrong while starting the transfer of media data from this
     * `PullBufferStream`
     * @see AbstractPullBufferStream.start
     */
    @Throws(IOException::class)
    override fun start() {
        super.start()
        val v4l2_buffer = Video4Linux2.v4l2_buffer_alloc(Video4Linux2.V4L2_BUF_TYPE_VIDEO_CAPTURE)
        if (0L == v4l2_buffer) throw OutOfMemoryError("v4l2_buffer_alloc")
        try {
            Video4Linux2.v4l2_buffer_setMemory(v4l2_buffer, Video4Linux2.V4L2_MEMORY_MMAP)
            for (i in 0 until requestbuffersCount) {
                Video4Linux2.v4l2_buffer_setIndex(v4l2_buffer, i)
                if (Video4Linux2.ioctl(fd, Video4Linux2.VIDIOC_QBUF, v4l2_buffer) == -1) {
                    throw IOException("ioctl: request= VIDIOC_QBUF, index= $i")
                }
            }
        } finally {
            Video4Linux2.free(v4l2_buffer)
        }

        /*
		 * we will start capture in read() method (i.e do the VIDIOC_STREAMON ioctl) because for
		 * some couple of fps/resolution the captured image will be weird (shift, not a JPEG for
		 * JPEG/MJPEG format, ...) if it is done here. Maybe it is due because sometime JMF do the
		 * sequence start/stop/start too quickly...
		 */
        startInRead = true
    }

    /**
     * Stops the transfer of media data from this `PullBufferStream`.
     *
     * @throws IOException
     * if anything goes wrong while stopping the transfer of media data from this
     * `PullBufferStream`
     * @see AbstractPullBufferStream.stop
     */
    @Throws(IOException::class)
    override fun stop() {
        try {
            val v4l2_buf_type = Video4Linux2.v4l2_buf_type_alloc(Video4Linux2.V4L2_BUF_TYPE_VIDEO_CAPTURE)
            if (0L == v4l2_buf_type) throw OutOfMemoryError("v4l2_buf_type_alloc")
            try {
                if (Video4Linux2.ioctl(fd, Video4Linux2.VIDIOC_STREAMOFF, v4l2_buf_type) == -1) {
                    throw IOException("ioctl: request= VIDIOC_STREAMOFF")
                }
            } finally {
                Video4Linux2.free(v4l2_buf_type)
            }
        } finally {
            super.stop()
            if (avctx != 0L) {
                FFmpeg.avcodec_close(avctx)
                FFmpeg.av_free(avctx)
                avctx = 0
            }
            if (avframe != 0L) {
                FFmpeg.avcodec_free_frame(avframe)
                avframe = 0
            }
            byteBufferPool.drain()
        }
    }
}