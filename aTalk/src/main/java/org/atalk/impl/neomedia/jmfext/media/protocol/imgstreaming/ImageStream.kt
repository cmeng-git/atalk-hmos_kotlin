/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.imgstreaming

import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.codec.video.AVFrame
import org.atalk.impl.neomedia.codec.video.AVFrameFormat
import org.atalk.impl.neomedia.codec.video.ByteBuffer
import org.atalk.impl.neomedia.imgstreaming.DesktopInteract
import org.atalk.impl.neomedia.imgstreaming.DesktopInteractImpl
import org.atalk.impl.neomedia.imgstreaming.ImgStreamingUtils.getImageBytes
import org.atalk.impl.neomedia.imgstreaming.ImgStreamingUtils.getScaledImage
import org.atalk.impl.neomedia.jmfext.media.protocol.*
import timber.log.Timber
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.IOException
import javax.media.Buffer
import javax.media.control.FormatControl
import javax.media.format.VideoFormat

/**
 * The stream used by JMF for our image streaming.
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class ImageStream
/**
 * Initializes a new `ImageStream` instance which is to have a specific `FormatControl`
 *
 * @param dataSource the `DataSource` which is creating the new instance so that it becomes one of its `streams`
 * @param formatControl the `FormatControl` of the new instance which is to specify the format in which
 * it is to provide its media data
 */
internal constructor(dataSource: DataSource?, formatControl: FormatControl?) : AbstractVideoPullBufferStream<DataSource?>(dataSource, formatControl) {
    /**
     * The pool of `ByteBuffer`s this instances is using to optimize the allocations and de-allocations of `ByteBuffer`s.
     */
    private val byteBufferPool = ByteBufferPool()

    /**
     * Desktop interaction (screen capture, key press, ...).
     */
    private var desktopInteract: DesktopInteract? = null

    /**
     * Index of display that we will capture from.
     */
    private var displayIndex = -1

    /**
     * Sequence number.
     */
    private var seqNo = 0L

    /**
     * X origin.
     */
    private var x = 0

    /**
     * Y origin.
     */
    private var y = 0

    /**
     * Blocks and reads into a `Buffer` from this `PullBufferStream`.
     *
     * @param buffer the `Buffer` this `PullBufferStream` is to read into
     * @throws IOException if an I/O error occurs while this `PullBufferStream` reads into the specified `Buffer`
     * @see AbstractVideoPullBufferStream.doRead
     */
    @Throws(IOException::class)
    override fun doRead(buffer: Buffer) {
        /*
         * Determine the Format in which we're expected to output. We cannot rely on the Format
         * always being specified in the Buffer because it is not its responsibility, the
         * DataSource of this ImageStream knows the output Format.
         */
        var format = buffer.format
        if (format == null) {
            format = format
            if (format != null) buffer.format = format
        }
        if (format is AVFrameFormat) {
            val o = buffer.data
            val frame: AVFrame
            if (o is AVFrame) frame = o else {
                frame = AVFrame()
                buffer.data = frame
            }
            val avFrameFormat = format
            val size = avFrameFormat.size
            val data = readScreenNative(size)
            if (data != null) {
                if (frame.avpicture_fill(data, avFrameFormat) < 0) {
                    data.free()
                    throw IOException("avpicture_fill")
                }
            } else {
                /*
                 * This can happen when we disconnect a monitor from computer before or during grabbing.
                 */
                throw IOException("Failed to grab screen.")
            }
        } else {
            var bytes = buffer.data as ByteArray?
            val size = (format as VideoFormat).size
            bytes = readScreen(bytes, size)
            buffer.data = bytes
            buffer.offset = 0
            buffer.length = bytes!!.size
        }
        buffer.header = null
        buffer.timeStamp = System.nanoTime()
        buffer.sequenceNumber = seqNo
        buffer.flags = Buffer.FLAG_SYSTEM_TIME or Buffer.FLAG_LIVE_DATA
        seqNo++
    }

    /**
     * Read screen.
     *
     * @param output output buffer for screen bytes
     * @param dim dimension of the screen
     * @return raw bytes, it could be equal to output or not. Take care in the caller to check if
     * output is the returned value.
     */
    fun readScreen(output: ByteArray?, dim: Dimension): ByteArray? {
        var output = output
        val format = format as VideoFormat
        val formatSize = format.size
        val width = formatSize.width
        val height = formatSize.height
        var scaledScreen: BufferedImage?
        var screen: BufferedImage?
        var data: ByteArray? = null
        val size = width * height * 4

        // If output is not large enough, enlarge it.
        if (output == null || output.size < size) output = ByteArray(size)

        /* get desktop screen via native grabber if available */
        if (desktopInteract!!.captureScreen(displayIndex, x, y, dim.width, dim.height, output)) {
            return output
        }
        println("failed to grab with native! " + output.size)

        /*
         * OK native grabber failed or is not available, try with AWT Robot and convert it to the right format
         *
         * Note that it is very memory consuming since memory are allocated to capture screen (via
         * Robot) and then for converting to raw bytes Moreover support for multiple display has
         * not yet been investigated
         *
         * Normally not of our supported platform (Windows (x86, x64), Linux (x86, x86-64), Mac OS X
         * (i386, x86-64, ppc) and FreeBSD (x86, x86-64) should go here.
         */
        screen = desktopInteract!!.captureScreen()
        if (screen != null) {
            /* convert to ARGB BufferedImage */
            scaledScreen = getScaledImage(screen, width, height, BufferedImage.TYPE_INT_ARGB)
            /* get raw bytes */
            data = getImageBytes(scaledScreen!!, output)
        }
        screen = null
        scaledScreen = null
        return data
    }

    /**
     * Read screen and store result in native buffer.
     *
     * @param dim dimension of the video
     * @return true if success, false otherwise
     */
    private fun readScreenNative(dim: Dimension): ByteBuffer? {
        val size = dim.width * dim.height * 4 + FFmpeg.FF_INPUT_BUFFER_PADDING_SIZE
        var data= byteBufferPool.getBuffer(size) as ByteBuffer?
        data!!.setLength(size)

        /* get desktop screen via native grabber */
        val b = try {
            desktopInteract!!.captureScreen(displayIndex, x, y, dim.width, dim.height,
                    data.ptr, data.getLength())
        } catch (t: Throwable) {
            if (t is ThreadDeath) {
                throw t
            } else {
                false
                // Timber.e(t, "Failed to grab screen!");
            }
        }
        if (!b) {
            data.free()
            data = null
        }
        return data
    }

    /**
     * Sets the index of the display to be used by this `ImageStream`.
     *
     * @param displayIndex the index of the display to be used by this `ImageStream`
     */
    fun setDisplayIndex(displayIndex: Int) {
        this.displayIndex = displayIndex
    }

    /**
     * Sets the origin to be captured by this `ImageStream`.
     *
     * @param x the x coordinate of the origin to be set on this instance
     * @param y the y coordinate of the origin to be set on this instance
     */
    fun setOrigin(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    /**
     * Start desktop capture stream.
     *
     * @see AbstractPullBufferStream.start
     */
    @Throws(IOException::class)
    override fun start() {
        super.start()
        if (desktopInteract == null) {
            try {
                desktopInteract = DesktopInteractImpl()
            } catch (e: Exception) {
                Timber.w("Cannot create DesktopInteract object!")
            }
        }
    }

    /**
     * Stop desktop capture stream.
     *
     * @see AbstractPullBufferStream.stop
     */
    @Throws(IOException::class)
    override fun stop() {
        Timber.i("Stop stream")
        super.stop()
        byteBufferPool.drain()
    }
}