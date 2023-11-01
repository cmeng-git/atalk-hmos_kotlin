/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio

import org.atalk.impl.neomedia.codec.FFmpeg.avcodec_alloc_frame
import org.atalk.impl.neomedia.codec.FFmpeg.avcodec_alloc_packet
import org.atalk.impl.neomedia.codec.FFmpeg.avcodec_decode_audio4
import org.atalk.impl.neomedia.codec.FFmpeg.avcodec_find_decoder
import org.atalk.impl.neomedia.codec.FFmpeg.avcodec_free_frame
import org.atalk.impl.neomedia.codec.FFmpeg.avcodec_free_packet
import org.atalk.impl.neomedia.codec.FFmpeg.avframe_get_data0
import org.atalk.impl.neomedia.codec.FFmpeg.avframe_get_linesize0
import org.atalk.impl.neomedia.codec.FFmpeg.avpacket_set_data
import org.atalk.impl.neomedia.codec.FFmpeg.memcpy
import org.atalk.util.ArrayIOUtils.writeInt16
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.media.Buffer
import javax.media.Format
import javax.media.ResourceUnavailableException
import javax.media.format.AudioFormat
import kotlin.math.roundToInt

/**
 * Implements an audio `Codec` using the FFmpeg library.
 *
 * @author Lyubomir Marinov
 */
open class FFmpegAudioDecoder
/**
 * Initializes a new `FFmpegAudioDecoder` instance with a specific `PlugIn` name,
 * a specific `AVCodecID`, and a specific list of `Format`s supported as output.
 *
 * @param name
 * the `PlugIn` name of the new instance
 * @param codecID
 * the `AVCodecID` of the FFmpeg codec to be represented by the new instance
 * @param supportedOutputFormats
 * the list of `Format`s supported by the new instance as output
 */
protected constructor(name: String, codecID: Int, supportedOutputFormats: Array<out Format>) : AbstractFFmpegAudioCodec(name, codecID, supportedOutputFormats) {
    private var avpkt = 0L
    private val got_frame = BooleanArray(1)
    private var frame = 0L

    /**
     * {@inheritDoc}
     */
    @Synchronized
    override fun doClose() {
        super.doClose()

        // avpkt
        val avpkt = avpkt
        if (avpkt != 0L) {
            this.avpkt = 0
            avcodec_free_packet(avpkt)
        }

        // frame
        val frame = frame
        if (frame != 0L) {
            this.frame = 0
            avcodec_free_frame(frame)
        }
    }

    /**
     * {@inheritDoc}
     */
    @Synchronized
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        super.doOpen()

        // avpkt
        var avpkt = avpkt
        if (avpkt != 0L) {
            this.avpkt = 0
            avcodec_free_packet(avpkt)
        }
        avpkt = avcodec_alloc_packet(0)
        if (avpkt == 0L) {
            doClose()
            throw ResourceUnavailableException(
                    "Failed to allocate a new AVPacket for FFmpeg codec " + codecIDToString(codecID) + "!")
        } else {
            this.avpkt = avpkt
        }

        // frame
        var frame = frame
        if (frame != 0L) {
            this.frame = 0
            avcodec_free_frame(frame)
        }
        frame = avcodec_alloc_frame()
        if (frame == 0L) {
            doClose()
            throw ResourceUnavailableException(
                    "Failed to allocate a new AVFrame for FFmpeg codec " + codecIDToString(codecID) + "!")
        } else {
            this.frame = frame
        }
    }

    /**
     * {@inheritDoc}
     */
    @Synchronized
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        val `in` = inBuf.data as ByteArray
        var inLen = inBuf.length
        val inOff = inBuf.offset
        val avpkt = avpkt
        val frame = frame
        avpacket_set_data(avpkt, `in`, inOff, inLen)
        val consumedInLen = avcodec_decode_audio4(avctx, frame, got_frame, avpkt)
        return if (consumedInLen < 0 || consumedInLen > inLen) {
            BUFFER_PROCESSED_FAILED
        } else {
            var doProcess = BUFFER_PROCESSED_OK
            inLen -= consumedInLen
            inBuf.length = inLen
            if (inLen > 0) doProcess = doProcess or INPUT_BUFFER_NOT_CONSUMED
            if (got_frame[0]) {
                val data0 = avframe_get_data0(frame)
                val linesize0 = avframe_get_linesize0(frame)
                if (data0 == 0L) {
                    doProcess = BUFFER_PROCESSED_FAILED
                } else {
                    val bytes = ByteArray(linesize0)
                    memcpy(bytes, 0, bytes.size, data0)
                    val floats = ByteBuffer.wrap(bytes)
                            .order(ByteOrder.nativeOrder()).asFloatBuffer()
                    var outLen = floats.limit() * 2
                    val out = validateByteArraySize(outBuf, outLen, false)
                    outLen = 0
                    var floatI = 0
                    val floatEnd = floats.limit()
                    while (floatI < floatEnd) {
                        var s16 = (floats[floatI] * Short.MAX_VALUE).roundToInt()
                        if (s16 < Short.MIN_VALUE) s16 = Short.MIN_VALUE.toInt() else if (s16 > Short.MAX_VALUE) s16 = Short.MAX_VALUE.toInt()
                        writeInt16(s16, out, outLen)
                        outLen += 2
                        ++floatI
                    }
                    outBuf.duration = (20L /* milliseconds */
                            * 1000000L /* nanoseconds in a millisecond */)
                    outBuf.format = getOutputFormat()
                    outBuf.length = outLen
                    outBuf.offset = 0
                }
            } else {
                doProcess = doProcess or OUTPUT_BUFFER_NOT_FILLED
            }
            doProcess
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun findAVCodec(codecID: Int): Long {
        return avcodec_find_decoder(codecID)
    }

    /**
     * {@inheritDoc}
     */
    override val aVCodecContextFormat: AudioFormat
        get() = getOutputFormat() as AudioFormat

    companion object {
        /**
         * Asserts that an decoder with a specific `AVCodecID` is found by FFmpeg.
         *
         * @param codecID
         * the `AVCodecID` of the decoder to find
         * @throws RuntimeException
         * if no decoder with the specified `codecID` is found by FFmpeg
         */
        fun assertFindAVCodec(codecID: Int) {
            if (avcodec_find_decoder(codecID) == 0L) {
                throw RuntimeException("Could not find FFmpeg decoder " + codecIDToString(codecID) + "!")
            }
        }
    }
}