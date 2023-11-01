/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.codec.FFmpeg.av_strerror
import org.atalk.impl.neomedia.codec.FFmpeg.avcodec_alloc_frame
import org.atalk.impl.neomedia.codec.FFmpeg.avcodec_free_frame
import org.atalk.impl.neomedia.codec.FFmpeg.avfilter_graph_alloc
import org.atalk.impl.neomedia.codec.FFmpeg.avfilter_graph_config
import org.atalk.impl.neomedia.codec.FFmpeg.avfilter_graph_free
import org.atalk.impl.neomedia.codec.FFmpeg.avfilter_graph_get_filter
import org.atalk.impl.neomedia.codec.FFmpeg.avfilter_graph_parse
import org.atalk.impl.neomedia.codec.FFmpeg.get_filtered_video_frame
import timber.log.Timber
import java.awt.Dimension
import javax.media.*

/**
 * Implements a video `Effect` which horizontally flips
 * `AVFrame`s.
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class HFlip
/**
 * Initializes a new `HFlip` instance.
 */
    : AbstractCodec2("FFmpeg HFlip Filter", AVFrameFormat::class.java, SUPPORTED_FORMATS), Effect {
    /**
     * The pointer to the `AVFilterContext` in [.graph] of the
     * FFmpeg video source with the name [.VSRC_BUFFER_NAME].
     */
    private var buffer = 0L

    /**
     * The pointer to the `AVFilterContext` in [.graph] of the
     * FFmpeg video sink with the name [.VSINK_FFSINK_NAME].
     */
    private var ffsink = 0L

    /**
     * The pointer to the `AVFilterGraph` instance which contains the
     * FFmpeg hflip filter represented by this `Effect`.
     */
    private var graph = 0L

    /**
     * The indicator which determines whether the fact that [.graph] is
     * equal to zero means that an attempt to initialize it is to be made. If
     * `false`, indicates that such an attempt has already been made and
     * has failed. In other words, prevents multiple initialization attempts
     * with the same parameters.
     */
    private var graphIsPending = true

    /**
     * The height of [.graph].
     */
    private var height = 0

    /**
     * The pointer to the `AVFrame` instance which is the output (data)
     * of this `Effect`.
     */
    private var outputFrame = 0L

    /**
     * The FFmpeg pixel format of [.graph].
     */
    private var pixFmt = FFmpeg.PIX_FMT_NONE

    /**
     * The width of [.graph].
     */
    private var width = 0

    /**
     * Closes this `Effect`.
     *
     * @see AbstractCodec2.doClose
     */
    @Synchronized
    override fun doClose() {
        try {
            if (outputFrame != 0L) {
                avcodec_free_frame(outputFrame)
                outputFrame = 0
            }
        } finally {
            reset()
        }
    }

    /**
     * Opens this `Effect`.
     *
     * @throws ResourceUnavailableException if any of the required resource
     * cannot be allocated
     * @see AbstractCodec2.doOpen
     */
    @Synchronized
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        outputFrame = avcodec_alloc_frame()
        if (outputFrame == 0L) {
            val reason = "avcodec_alloc_frame: $outputFrame"
            Timber.e("%s", reason)
            throw ResourceUnavailableException(reason)
        }
    }

    /**
     * Performs the media processing defined by this `Effect`.
     *
     * @param inputBuffer the `Buffer` that contains the media data to be processed
     * @param outputBuffer the `Buffer` in which to store the processed media data
     * @return `BUFFER_PROCESSED_OK` if the processing is successful
     * @see AbstractCodec2.doProcess
     */
    @Synchronized
    override fun doProcess(
            inputBuffer: Buffer,
            outputBuffer: Buffer): Int {
        // Make sure the graph is configured with the current Format i.e. size and pixFmt.
        val format = inputBuffer.format as AVFrameFormat
        val size = format.size
        val pixFmt = format.pixFmt
        if (width != size.width || height != size.height || this.pixFmt != pixFmt) reset()
        if (!allocateFfmpegGraph(format, size, pixFmt)) {
            return BUFFER_PROCESSED_FAILED
        }

        /*
         * The graph is configured for the current Format, apply its filters to the inputFrame.
         */
        val inputFrame = (inputBuffer.data as AVFrame).ptr
        val filterResult = get_filtered_video_frame(
                inputFrame, width, height, this.pixFmt, buffer, ffsink, outputFrame)
        if (filterResult < 0) {
            /*
             * If get_filtered_video_frame fails, it is likely to fail for any
             * frame. Consequently, printing that it has failed will result in a
             * lot of repeating logging output. Since the failure in question
             * will be visible in the UI anyway, just debug it.
             */
            Timber.log(TimberLog.FINER, "get_filtered_video_frame: %s", av_strerror(filterResult.toInt()))
            return BUFFER_PROCESSED_FAILED
        }
        val out = outputBuffer.data
        if (out !is AVFrame || out.ptr != outputFrame) {
            outputBuffer.data = AVFrame(outputFrame)
        }
        outputBuffer.isDiscard = inputBuffer.isDiscard
        outputBuffer.duration = inputBuffer.duration
        outputBuffer.isEOM = inputBuffer.isEOM
        outputBuffer.flags = inputBuffer.flags
        outputBuffer.format = format
        outputBuffer.header = inputBuffer.header
        outputBuffer.length = inputBuffer.length
        outputBuffer.sequenceNumber = inputBuffer.sequenceNumber
        outputBuffer.timeStamp = inputBuffer.timeStamp
        return BUFFER_PROCESSED_OK
    }

    private fun allocateFfmpegGraph(format: AVFrameFormat, size: Dimension, pixFmt: Int): Boolean {
        if (graph != 0L) {
            return true
        }
        var errorReason: String? = null
        var error = 0
        var buffer = 0L
        var ffsink = 0L
        if (graphIsPending) {
            graphIsPending = false
            graph = avfilter_graph_alloc()
            if (graph == 0L) errorReason = "avfilter_graph_alloc" else {
                val filters = (VSRC_BUFFER_NAME + "=" + size.width + ":" + size.height
                        + ":" + pixFmt + ":1:1000000:1:1,"
                        + "scale,hflip,scale,"
                        + "format=pix_fmts=" + pixFmt + ","
                        + VSINK_FFSINK_NAME)
                val log_ctx = 0L
                error = avfilter_graph_parse(graph, filters, 0, 0, log_ctx)
                if (error == 0) {
                    /*
                     * Unfortunately, the name of an AVFilterContext created by
                     * avfilter_graph_parse is not the name of the AVFilter.
                     */
                    val parsedFilterNameFormat = "Parsed_%2\$s_%1\$d"
                    var parsedFilterName = String.format(parsedFilterNameFormat, 0, VSRC_BUFFER_NAME)
                    buffer = avfilter_graph_get_filter(graph, parsedFilterName)
                    if (buffer == 0L) {
                        errorReason = "avfilter_graph_get_filter: " + VSRC_BUFFER_NAME + "/" + parsedFilterName
                    } else {
                        parsedFilterName = String.format(parsedFilterNameFormat, 5, VSINK_FFSINK_NAME)
                        ffsink = avfilter_graph_get_filter(graph, parsedFilterName)
                        if (ffsink == 0L) {
                            errorReason = "avfilter_graph_get_filter: " + VSINK_FFSINK_NAME + "/" + parsedFilterName
                        } else {
                            error = avfilter_graph_config(graph, log_ctx)
                            if (error != 0) errorReason = "avfilter_graph_config"
                        }
                    }
                } else {
                    errorReason = "avfilter_graph_parse"
                }
                if (errorReason != null) {
                    avfilter_graph_free(graph)
                    graph = 0
                }
            }
        }
        if (graph == 0L) {
            if (errorReason != null) {
                val msg = StringBuilder(errorReason)
                if (error != 0) {
                    msg.append(": ").append(error)
                }
                msg.append(", format ").append(format)
                Timber.e("%s", msg)
            }
            return false
        } else {
            width = size.width
            height = size.height
            this.pixFmt = pixFmt
            this.buffer = buffer
            this.ffsink = ffsink
        }
        return true
    }

    /**
     * Resets the state of this `PlugIn`.
     */
    @Synchronized
    override fun reset() {
        if (graph != 0L) {
            avfilter_graph_free(graph)
            graph = 0
            graphIsPending = true
            width = 0
            height = 0
            pixFmt = FFmpeg.PIX_FMT_NONE
            buffer = 0
            ffsink = 0
        }
    }

    companion object {
        /**
         * The list of `Format`s supported by `HFlip` instances as
         * input and output.
         */
        private val SUPPORTED_FORMATS = arrayOf<Format>(AVFrameFormat())

        /**
         * The name of the FFmpeg ffsink video source `AVFilter` used by `HFlip`.
         */
        private const val VSINK_FFSINK_NAME = "buffersink"

        /**
         * The name of the FFmpeg buffer video source `AVFilter` used by `HFlip`.
         */
        private const val VSRC_BUFFER_NAME = "buffer"
    }
}