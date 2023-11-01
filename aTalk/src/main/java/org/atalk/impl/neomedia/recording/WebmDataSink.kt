/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.recording

import org.atalk.impl.neomedia.recording.WebmWriter.FrameDescriptor
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.control.KeyFrameControl
import org.atalk.service.neomedia.recording.RecorderEvent
import org.atalk.service.neomedia.recording.RecorderEventHandler
import org.atalk.util.MediaType
import timber.log.Timber
import java.io.IOException
import javax.media.*
import javax.media.datasink.DataSinkListener
import javax.media.format.VideoFormat
import javax.media.protocol.*

/**
 * A `DataSink` implementation which writes output in webm format.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class WebmDataSink(filename: String, dataSource: DataSource) : DataSink, BufferTransferHandler {
    /**
     * The `WebmWriter` which we use to write the frames to a file.
     */
    private var writer: WebmWriter? = null
    var eventHandler: RecorderEventHandler? = null
    private var ssrc = -1L

    /**
     * Whether this `DataSink` is open and should write to its `WebmWriter`.
     */
    private var open = false
    private val openCloseSyncRoot = Any()

    /**
     * Whether we are in a state of waiting for a keyframe and discarding non-key frames.
     */
    private var waitingForKeyframe = true

    /**
     * The height of the video. Initialized on the first received keyframe.
     */
    private var height = 0

    /**
     * The height of the video. Initialized on the first received keyframe.
     */
    private var width = 0

    /**
     * A `Buffer` used to transfer frames.
     */
    private val buffer = Buffer()
    private val fd = FrameDescriptor()

    /**
     * Our `DataSource`.
     */
    private var dataSource: DataSource

    /**
     * The name of the file into which we will write.
     */
    private val filename: String

    /**
     * The RTP time stamp of the first frame written to the output webm file.
     */
    private var firstFrameRtpTimestamp = -1L

    /**
     * The time as returned by `System.currentTimeMillis()` of the first frame written to
     * the output webm file.
     */
    private var firstFrameTime = -1L

    /**
     * The PTS (presentation timestamp) of the last frame written to the output file. In milliseconds.
     */
    private var lastFramePts = -1L

    /**
     * The `KeyFrameControl` which we will use to request a keyframe.
     */
    private var keyFrameControl: KeyFrameControl? = null

    /**
     * Whether we have already requested a keyframe.
     */
    private var keyframeRequested = false
    private var framesSinceLastKeyframeRequest = 0
    private var autoKeyframeRequestInterval = 0

    /**
     * Initialize a new `WebmDataSink` instance.
     *
     * filename the name of the file into which to write.
     * dataSource the `DataSource` to use.
     */
    init {
        val cfg = LibJitsi.configurationService
        autoKeyframeRequestInterval = cfg.getInt(AUTO_REQUEST_KEYFRAME_PNAME, autoKeyframeRequestInterval)
        if (autoKeyframeRequestInterval > 0) {
            Timber.i("Auto keyframe request is initialized for every %s frames.", autoKeyframeRequestInterval)
        }
        this.filename = filename
        this.dataSource = dataSource
    }

    /**
     * {@inheritDoc}
     */
    override fun addDataSinkListener(dataSinkListener: DataSinkListener) {}

    /**
     * {@inheritDoc}
     */
    override fun close() {
        synchronized(openCloseSyncRoot) {
            if (!open) {
                Timber.d("Not closing WebmDataSink: already closed.")
                return
            }
            if (writer != null) writer!!.close()
            if (USE_RECORDING_ENDED_EVENTS && eventHandler != null && firstFrameTime != -1L && lastFramePts != -1L) {
                val event = RecorderEvent()
                event.type = RecorderEvent.Type.RECORDING_ENDED
                event.ssrc = ssrc
                event.filename = filename

                // make sure that the difference in the 'instant'-s of the
                // STARTED and ENDED events matches the duration of the file
                event.duration = lastFramePts
                event.mediaType = MediaType.VIDEO
                eventHandler!!.handleEvent(event)
            }
            open = false
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun getContentType(): String? {
        return null
    }

    /**
     * {@inheritDoc}
     */
    override fun getOutputLocator(): MediaLocator? {
        return null
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class, SecurityException::class)
    override fun open() {
        synchronized(openCloseSyncRoot) {
            if (dataSource is PushBufferDataSource) {
                val streams = (dataSource as PushBufferDataSource).streams

                // XXX: should we allow for multiple streams in the data source?
                for (stream in streams) {
                    // XXX whats the proper way to check for this? and handle?
                    if (!stream.format.matches(VideoFormat("VP8"))) throw IOException("Unsupported stream format")
                    stream.setTransferHandler(this)
                }
            }
            dataSource.connect()
            open = true
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun removeDataSinkListener(dataSinkListener: DataSinkListener) {}

    /**
     * {@inheritDoc}
     */
    override fun setOutputLocator(mediaLocator: MediaLocator) {}

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun start() {
        writer = WebmWriter(filename)
        dataSource.start()
        Timber.i("Created WebmWriter on %s", filename)
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun stop() {
        // XXX: should we do something here? reset waitingForKeyframe?
    }

    /**
     * {@inheritDoc}
     */
    override fun getControl(s: String): Any? {
        return null
    }

    /**
     * {@inheritDoc}
     */
    override fun getControls(): Array<Any> {
        return emptyArray()
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class, IncompatibleSourceException::class)
    override fun setSource(dataSource: DataSource) {
        // maybe we should throw an exception here, since we don't support
        // changing the data source?
    }

    /**
     * {@inheritDoc}
     */
    override fun transferData(stream: PushBufferStream) {
        synchronized(openCloseSyncRoot) {
            if (!open) return
            try {
                stream.read(buffer)
            } catch (ioe: IOException) {
                ioe.printStackTrace()
            }
            val data = buffer.data as ByteArray
            val offset = buffer.offset
            val len = buffer.length

            /*
             * Until an SDES packet is received by FMJ, it doesn't correctly set the packets'
             * timestamps. To avoid waiting, we use the RTP time stamps directly. We can do this
             * because VP8 always uses a rate of 90000.
             */
            val rtpTimeStamp = buffer.rtpTimeStamp
            // long rtpTimeStamp = buffer.getTimeStamp();
            val key = isKeyFrame(data, offset)
            val valid = isKeyFrameValid(data, offset)
            if (waitingForKeyframe && key) {
                if (valid) {
                    waitingForKeyframe = false
                    width = getWidth(data, offset)
                    height = getHeight(data, offset)
                    firstFrameRtpTimestamp = rtpTimeStamp
                    firstFrameTime = System.currentTimeMillis()
                    writer!!.writeWebmFileHeader(width, height)
                    Timber.i("Received the first keyframe (width=%d; height=%d) ssrc=%s",
                            width, height, ssrc)
                    if (eventHandler != null) {
                        val event = RecorderEvent()
                        event.type = RecorderEvent.Type.RECORDING_STARTED
                        event.ssrc = ssrc
                        if (height * 4 == width * 3) event.aspectRatio = RecorderEvent.AspectRatio.ASPECT_RATIO_4_3 else if (height * 16 == width * 9) event.aspectRatio = RecorderEvent.AspectRatio.ASPECT_RATIO_16_9
                        event.filename = filename
                        event.instant = firstFrameTime
                        event.rtpTimestamp = rtpTimeStamp
                        event.mediaType = MediaType.VIDEO
                        eventHandler!!.handleEvent(event)
                    }
                } else {
                    keyframeRequested = false
                    Timber.i("Received an invalid first keyframe. Requesting new: %s", ssrc)
                }
            }
            framesSinceLastKeyframeRequest++
            if (framesSinceLastKeyframeRequest > REREQUEST_KEYFRAME_INTERVAL) keyframeRequested = false
            if (!keyframeRequested
                    && (waitingForKeyframe // recording not started yet
                            || (autoKeyframeRequestInterval in 1 until framesSinceLastKeyframeRequest))) {
                Timber.i("Requesting keyframe. %s", ssrc)
                if (keyFrameControl != null) keyframeRequested = keyFrameControl!!.requestKeyFrame(true)
                framesSinceLastKeyframeRequest = 0
            }

            // that's temporary, aimed at debugging a specific issue
            if (key) {
                var s = ""
                var i = 0
                while (i < 10 && i < len) {
                    s += String.format("%02x", data[offset + i])
                    i++
                }
                Timber.i("Keyframe. First 10 bytes: %s", s)
            }
            if (!waitingForKeyframe) {
                if (key) {
                    if (!valid) {
                        Timber.i("Dropping an invalid VP8 keyframe.")
                        return
                    }
                    val oldWidth = width
                    width = getWidth(data, offset)
                    val oldHeight = height
                    height = getHeight(data, offset)
                    // TODO generate an event? start writing in a new file?
                    if (width != oldWidth || height != oldHeight) {
                        Timber.i("VP8 stream width/height changed. Old: %d/%d. New: %d/%d.",
                                oldWidth, oldHeight, width, height)
                    }
                }
                fd.buffer = data
                fd.offset = offset
                fd.length = len.toLong()
                fd.flags = if (key) WebmWriter.FLAG_FRAME_IS_KEY else 0
                if (!isShowFrame(data, offset)) fd.flags = fd.flags or WebmWriter.FLAG_FRAME_IS_INVISIBLE
                var diff = rtpTimeStamp - firstFrameRtpTimestamp
                if (diff < -(1L shl 31)) diff += 1L shl 32
                // pts is in milliseconds, the VP8 rtp clock rate is 90000
                fd.pts = diff / 90
                writer!!.writeFrame(fd)
                lastFramePts = fd.pts
            }
        } // synchronized
    }

    /**
     * Returns `true` if the VP8 compressed frame contained in `buf` at offset
     * `offset` is a keyframe. TODO: move it to a more general class?
     *
     * @param buf the buffer containing a compressed VP8 frame.
     * @param offset the offset in `buf` where the VP8 compressed frame starts.
     * @return `true`if the VP8 compressed frame contained in `buf` at offset
     * `offset` is a keyframe.
     */
    private fun isKeyFrame(buf: ByteArray, offset: Int): Boolean {
        return buf[offset].toInt() and 0x01 == 0
    }

    /**
     * Returns `true` if the VP8 compressed keyframe contained in `buf` at offset
     * `offset` is valid. TODO: move it to a more general class?
     *
     * @param buf the buffer containing a compressed VP8 frame.
     * @param offset the offset in `buf` where the VP8 compressed frame starts.
     * @return `true`if the VP8 compressed keyframe contained in `buf` at offset
     * `offset` is valid.
     */
    private fun isKeyFrameValid(buf: ByteArray, offset: Int): Boolean {
        return buf[offset + 3] == 0x9d.toByte() && buf[offset + 4] == 0x01.toByte() && buf[offset + 5] == 0x2a.toByte()
    }

    /**
     * Returns the width of the VP8 compressed frame contained in `buf` at offset
     * `offset`. See the format defined in RFC6386. TODO: move it to a more general class?
     *
     * @param buf the buffer containing a compressed VP8 frame.
     * @param offset the offset in `buf` where the VP8 compressed frame starts.
     * @return the width of the VP8 compressed frame contained in `buf` at offset
     * `offset`.
     */
    private fun getWidth(buf: ByteArray, offset: Int): Int {
        return buf[offset + 7].toInt() and 0xff shl 8 or (buf[offset + 6].toInt() and 0xff) and 0x3fff
    }

    /**
     * Returns the height of the VP8 compressed frame contained in `buf` at offset
     * `offset`. See the format defined in RFC6386. TODO: move it to a more general class?
     *
     * @param buf the buffer containing a compressed VP8 frame.
     * @param offset the offset in `buf` where the VP8 compressed frame starts.
     * @return the height of the VP8 compressed frame contained in `buf` at offset `offset`.
     */
    private fun getHeight(buf: ByteArray, offset: Int): Int {
        return buf[offset + 9].toInt() and 0xff shl 8 or (buf[offset + 8].toInt() and 0xff) and 0x3fff
    }

    /**
     * Returns the value of the `show_frame` field from the "uncompressed data chunk" in the
     * VP8 compressed frame contained in `buf` at offset `offset`. RFC6386 isn't
     * clear about the format, so the interpretation of
     *
     * @param buf the buffer containing a compressed VP8 frame.
     * @param offset the offset in `buf` where the VP8 compressed frame starts.
     * @return the value of the `show_frame` field from the "uncompressed data chunk" in the
     * VP8 compressed frame contained in `buf` at offset `offset`.
     * @{link https://tools.ietf.org/html/draft-ietf-payload-vp8-11} is used.
     */
    private fun isShowFrame(buf: ByteArray, offset: Int): Boolean {
        return buf[offset].toInt() and 0x10 == 0
    }

    fun setKeyFrameControl(keyFrameControl: KeyFrameControl?) {
        this.keyFrameControl = keyFrameControl
    }

    fun setSsrc(ssrc: Long) {
        this.ssrc = ssrc
    }

    companion object {
        /**
         * Whether to generate a RECORDING_ENDED event when closing.
         */
        private const val USE_RECORDING_ENDED_EVENTS = false
        private const val REREQUEST_KEYFRAME_INTERVAL = 100

        /**
         * Property name to control auto requesting keyframes periodically
         * to improve seeking speed without re-encoding the file
         */
        private val AUTO_REQUEST_KEYFRAME_PNAME = WebmDataSink::class.java.canonicalName!! + ".AUTOKEYFRAME"
    }
}