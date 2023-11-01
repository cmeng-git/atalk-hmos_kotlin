/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.video4linux2

/**
 * Provides the interface to the native Video for Linux Two API Specification
 * (http://v4l2spec.bytesex.org/spec/) implementation.
 *
 * @author Lyubomir Marinov
 */
object Video4Linux2 {
    const val MAP_SHARED = 0x01
    const val O_NONBLOCK = 2048
    const val O_RDWR = 2
    const val PROT_READ = 0x1
    const val PROT_WRITE = 0x2
    const val V4L2_BUF_TYPE_VIDEO_CAPTURE = 1
    const val V4L2_CAP_STREAMING = 0x04000000
    const val V4L2_CAP_VIDEO_CAPTURE = 0x00000001
    const val V4L2_FIELD_NONE = 1
    const val V4L2_MEMORY_MMAP = 1
    const val V4L2_MEMORY_USERPTR = 2
    const val V4L2_PIX_FMT_NONE = 0
    val V4L2_PIX_FMT_RGB24 = v4l2_fourcc('R', 'G', 'B', '3')
    val V4L2_PIX_FMT_BGR24 = v4l2_fourcc('B', 'G', 'R', '3')
    val V4L2_PIX_FMT_UYVY = v4l2_fourcc('U', 'Y', 'V', 'Y')
    val V4L2_PIX_FMT_VYUY = v4l2_fourcc('V', 'Y', 'U', 'Y')
    val V4L2_PIX_FMT_YUV420 = v4l2_fourcc('Y', 'U', '1', '2')
    val V4L2_PIX_FMT_YUYV = v4l2_fourcc('Y', 'U', 'Y', 'V')
    val V4L2_PIX_FMT_MJPEG = v4l2_fourcc('M', 'J', 'P', 'G')
    val V4L2_PIX_FMT_JPEG = v4l2_fourcc('J', 'P', 'E', 'G')

    var VIDIOC_DQBUF = 0
    var VIDIOC_G_FMT = 0
    var VIDIOC_QBUF = 0
    var VIDIOC_QUERYBUF = 0
    var VIDIOC_QUERYCAP = 0
    var VIDIOC_REQBUFS = 0
    var VIDIOC_S_FMT = 0
    var VIDIOC_S_PARM = 0
    var VIDIOC_STREAMOFF = 0
    var VIDIOC_STREAMON = 0

    init {
        System.loadLibrary("jnvideo4linux2")
        VIDIOC_DQBUF = VIDIOC_DQBUF()
        VIDIOC_G_FMT = VIDIOC_G_FMT()
        VIDIOC_QBUF = VIDIOC_QBUF()
        VIDIOC_QUERYBUF = VIDIOC_QUERYBUF()
        VIDIOC_QUERYCAP = VIDIOC_QUERYCAP()
        VIDIOC_REQBUFS = VIDIOC_REQBUFS()
        VIDIOC_S_FMT = VIDIOC_S_FMT()
        VIDIOC_S_PARM = VIDIOC_S_PARM()
        VIDIOC_STREAMOFF = VIDIOC_STREAMOFF()
        VIDIOC_STREAMON = VIDIOC_STREAMON()
    }

    external fun close(fd: Int): Int
    external fun free(ptr: Long)
    external fun ioctl(fd: Int, request: Int, argp: Long): Int
    external fun memcpy(dest: Long, src: Long, n: Int): Long
    external fun mmap(start: Long, length: Int, prot: Int, flags: Int, fd: Int, offset: Long): Long
    external fun munmap(start: Long, length: Int): Int
    external fun open(deviceName: String?, flags: Int): Int
    external fun v4l2_buffer_alloc(type: Int): Long
    external fun v4l2_buffer_getBytesused(v4l2_buffer: Long): Int
    external fun v4l2_buffer_getIndex(v4l2_buffer: Long): Int
    external fun v4l2_buffer_getLength(v4l2_buffer: Long): Int
    external fun v4l2_buffer_getMOffset(v4l2_buffer: Long): Long
    external fun v4l2_buffer_setIndex(v4l2_buffer: Long, index: Int)
    external fun v4l2_buffer_setMemory(v4l2_buffer: Long, memory: Int)
    external fun v4l2_buf_type_alloc(type: Int): Long
    external fun v4l2_capability_alloc(): Long
    external fun v4l2_capability_getCapabilities(v4l2_capability: Long): Int
    external fun v4l2_capability_getCard(v4l2_capability: Long): String?
    private fun v4l2_fourcc(a: Char, b: Char, c: Char, d: Char): Int {
        return a.code and 0xFF or (b.code and 0xFF shl 8) or (c.code and 0xFF shl 16) or (d.code and 0xFF shl 24)
    }

    external fun v4l2_format_alloc(type: Int): Long
    external fun v4l2_format_getFmtPix(v4l2_format: Long): Long
    external fun v4l2_pix_format_getHeight(v4l2_pix_format: Long): Int
    external fun v4l2_pix_format_getPixelformat(v4l2_pix_format: Long): Int
    external fun v4l2_pix_format_getWidth(v4l2_pix_format: Long): Int
    external fun v4l2_pix_format_setBytesperline(v4l2_pix_format: Long, bytesperline: Int)
    external fun v4l2_pix_format_setField(v4l2_pix_format: Long, field: Int)
    external fun v4l2_pix_format_setPixelformat(v4l2_pix_format: Long, pixelformat: Int)
    external fun v4l2_pix_format_setWidthAndHeight(v4l2_pix_format: Long, width: Int,
            height: Int)

    external fun v4l2_requestbuffers_alloc(type: Int): Long
    external fun v4l2_requestbuffers_getCount(v4l2_requestbuffers: Long): Int
    external fun v4l2_requestbuffers_setCount(v4l2_requestbuffers: Long, count: Int)
    external fun v4l2_requestbuffers_setMemory(v4l2_requestbuffers: Long, memory: Int)
    external fun v4l2_streamparm_alloc(type: Int): Long
    external fun v4l2_streamparm_setFps(v4l2_streamparm: Long, fps: Int)
    private external fun VIDIOC_DQBUF(): Int
    private external fun VIDIOC_G_FMT(): Int
    private external fun VIDIOC_QBUF(): Int
    private external fun VIDIOC_QUERYBUF(): Int
    private external fun VIDIOC_QUERYCAP(): Int
    private external fun VIDIOC_REQBUFS(): Int
    private external fun VIDIOC_S_FMT(): Int
    private external fun VIDIOC_S_PARM(): Int
    private external fun VIDIOC_STREAMOFF(): Int
    private external fun VIDIOC_STREAMON(): Int
}