/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec

import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.*
import android.media.MediaCodecList
import android.media.MediaFormat
import org.atalk.impl.neomedia.codec.video.AVFrame
import org.atalk.impl.neomedia.codec.video.AVFrameFormat
import org.atalk.impl.neomedia.jmfext.media.protocol.ByteBufferPool
import org.atalk.service.neomedia.codec.Constants
import timber.log.Timber
import java.awt.Dimension
import java.io.IOException
import java.util.*
import javax.media.Buffer
import javax.media.Format
import javax.media.PlugIn
import javax.media.ResourceUnavailableException
import javax.media.format.VideoFormat
import kotlin.math.min

/**
 * Implements an FMJ `Codec` using Android's [MediaCodec].
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class AndroidMediaCodec : AbstractCodec2("MediaCodec", Format::class.java, SUPPORTED_OUTPUT_FORMATS) {
    /**
     * The `AVFrame` instance into which this `Codec` outputs media (data) if the
     * `outputFormat` is an `AVFrameFormat` instance.
     */
    private var avFrame: AVFrame? = null

    /**
     * A `byte` in the form of an array which is used to copy the bytes of a
     * `java.nio.ByteBuffer` into native memory (because the `memcpy` implementation
     * requires an array. Allocated once to reduce garbage collection.
     */
    private val b = ByteArray(1)
    private val byteBufferPool = ByteBufferPool()

    /**
     * The `colorFormat` value defined in the terms of Android's `MediaCodec` class
     * with which [.mediaCodec] is configured.
     */
    private var colorFormat = OMX_COLOR_FormatUnused

    /**
     * The indicator which determines whether [.mediaCodec] is configured to encode (or
     * decode) media (data).
     */
    private var encoder = false

    /**
     * The `MediaCodec.BufferInfo` instance which is populated by [.mediaCodec] to
     * describe the offset and length/size of the `java.nio.ByteBuffer`s it utilizes.
     * Allocated once to reduce garbage collection at runtime.
     */
    private val info = BufferInfo()
    private var inputBuffers: Array<java.nio.ByteBuffer>? = null

    /**
     * Android's `MediaCodec` instance which is wrapped by this instance and which performs
     * the very media processing (during the execution of [.doProcess]).
     */
    private var mediaCodec: MediaCodec? = null
    private var outputBuffers: Array<java.nio.ByteBuffer>? = null

    /**
     * The mime type defined in the terms of Android's `MediaCodec` class with which
     * [.mediaCodec] is configured.
     */
    private var type: String? = null

    /**
     * Initializes a new `AndroidMediaCodec` instance.
     */
    init {
        inputFormats = SUPPORTED_INPUT_FORMATS
    }

    /**
     * {@inheritDoc}
     *
     * Stops and releases [.mediaCodec] i.e. prepares it to be garbage collected.
     */
    override fun doClose() {
        if (mediaCodec != null) {
            try {
                try {
                    mediaCodec!!.stop()
                } finally {
                    mediaCodec!!.release()
                }
            } finally {
                mediaCodec = null

                /*
                 * The following are properties of mediaCodec which are not exposed by the
                 * MediaCodec class. The encoder property is of type boolean and either of its
                 * domain values is significant so clearing it is impossible.
                 */
                colorFormat = OMX_COLOR_FormatUnused
                inputBuffers = null
                outputBuffers = null
                type = null
            }
        }
        if (avFrame != null) {
            avFrame!!.free()
            avFrame = null
        }
        byteBufferPool.drain()
    }

    /**
     * {@inheritDoc}
     */
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        /*
         * If the inputFormat and outputFormat properties of this Codec have already been assigned
         * suitable values, initialize a MediaCodec instance, configure it and start it. Otherwise,
         * the procedure will be performed later on when the properties in question do get assigned
         * suitable values.
         */
        try {
            maybeConfigureAndStart()
        } catch (t: Throwable) {
            /*
             * PlugIn#open() (and, consequently, AbstractCodecExt#doOpen()) is supposed to throw
             * ResourceUnavailableException but maybeConfigureAndStart() does not throw such an
             * exception for the sake of ease of use.
             */
            if (t is ThreadDeath) throw t
            else {
                if (t is InterruptedException) Thread.currentThread().interrupt()
                Timber.e(t, "Failed to open %s", name)
                val rue = ResourceUnavailableException()
                rue.initCause(t)
                throw rue
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        var inputFormat = inBuf.format
        if (inputFormat != null
                && inputFormat != this.inputFormat
                && inputFormat != inputFormat
                && null == setInputFormat(inputFormat)) {
            return PlugIn.BUFFER_PROCESSED_FAILED
        }
        inputFormat = this.inputFormat

        /*
         * FIXME The implementation of AndroidMediaCodec is incomplete by relying on inputFormat
         * having Format.byteArray dataType.
         */
        if (inputFormat == null || Format.byteArray != inputFormat.dataType) {
            return PlugIn.BUFFER_PROCESSED_FAILED
        }
        val outputFormat = outputFormat as? AVFrameFormat ?: return PlugIn.BUFFER_PROCESSED_FAILED

        /*
         * FIXME The implementation of AndroidMediaCodec is incomplete by relying on outputFormat
         * being an AVFrameFormat instance.
         */
        val mediaCodecOutputIndex = mediaCodec!!.dequeueOutputBuffer(info, 0)
        /*
         * We will first exhaust the output of mediaCodec and then we will feed input into it.
         */
        var processed = PlugIn.INPUT_BUFFER_NOT_CONSUMED or PlugIn.OUTPUT_BUFFER_NOT_FILLED
        if (MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED == mediaCodecOutputIndex) {
            outputBuffers = mediaCodec!!.getOutputBuffers()
        }
        else if (0 <= mediaCodecOutputIndex) {
            var outputLength: Int
            var byteBuffer: java.nio.ByteBuffer? = null
            try {
                if (info.size.also { outputLength = it } > 0) {
                    byteBuffer = outputBuffers!![mediaCodecOutputIndex]
                    var avFrameData = avFrame!!.data
                    val avFrameFormat = outputFormat
                    if (avFrameData == null || avFrameData.capacity < outputLength) {
                        avFrameData = byteBufferPool.getBuffer(outputLength)
                        if (avFrame!!.avpicture_fill(avFrameData, avFrameFormat) < 0) {
                            processed = PlugIn.BUFFER_PROCESSED_FAILED
                        }
                    }
                    if (processed != PlugIn.BUFFER_PROCESSED_FAILED) {
                        var ptr = avFrameData.ptr
                        var i = info.offset
                        val end = i + outputLength
                        while (i < end) {
                            b[0] = byteBuffer[i]
                            FFmpeg.memcpy(ptr, b, 0, b.size)
                            ptr++
                            i++
                        }
                        outBuf.data = avFrame
                        outBuf.format = outputFormat
                        outBuf.length = outputLength
                        outBuf.offset = 0
                        processed = processed and PlugIn.OUTPUT_BUFFER_NOT_FILLED.inv()
                    }
                }
            } finally {
                // Well, it was recommended by the Internet.
                byteBuffer?.clear()
                mediaCodec!!.releaseOutputBuffer(mediaCodecOutputIndex,  /* render */
                    false)
            }
            /*
             * We will first exhaust the output of mediaCodec and then we will feed input into it.
             */
            if (processed == PlugIn.BUFFER_PROCESSED_FAILED || outputLength > 0) return processed
        }
        val mediaCodecInputIndex = MediaCodec.INFO_TRY_AGAIN_LATER // mediaCodec.dequeueInputBuffer(DEQUEUE_INPUT_BUFFER_TIMEOUT)
        if (0 <= mediaCodecInputIndex) {
            val mediaCodecInputOffset = 0
            var mediaCodecInputLength = 0
            try {
                val byteBuffer = inputBuffers!![mediaCodecInputIndex]
                val fmjLength = inBuf.length
                mediaCodecInputLength = min(byteBuffer.capacity(), fmjLength)
                val bytes = inBuf.data as ByteArray
                val fmjOffset = inBuf.offset
                var dst = 0
                var src = fmjOffset
                while (dst < mediaCodecInputLength) {
                    byteBuffer.put(dst, bytes[src])
                    dst++
                    src++
                }
                if (mediaCodecInputLength == fmjLength) processed = processed and PlugIn.INPUT_BUFFER_NOT_CONSUMED.inv()
                else {
                    inBuf.length = fmjLength - mediaCodecInputLength
                    inBuf.offset = fmjOffset + mediaCodecInputLength
                }
            } finally {
                mediaCodec!!.queueInputBuffer(mediaCodecInputIndex, mediaCodecInputOffset,
                    mediaCodecInputLength, 0, 0)
            }
        }
        return processed
    }

    /**
     * {@inheritDoc}
     */
    override fun getMatchingOutputFormats(inputFormat: Format): Array<Format> {
        /*
         * An input Format may be supported by multiple MediaCodecs and, consequently, the output
         * Formats supported by this AndroidMediaCodec is the set of the output formats supported by
         * the multiple MediaCodecs in question.
         */
        val outputFormats = ArrayList<Format>()
        var codecIndex = 0
        val codecCount = MediaCodecList.getCodecCount()
        while (codecIndex < codecCount) {
            val codecInfo = MediaCodecList.getCodecInfoAt(codecIndex)
            val supportedTypes = codecInfo.supportedTypes
            if (codecInfo.isEncoder) {
                /* The supported input Formats are the colorFormats. */
                for (supportedType in supportedTypes) {
                    val capabilities = getCapabilitiesForType(codecInfo, supportedType)
                    if (capabilities != null) {
                        val colorFormats = capabilities.colorFormats
                        if (colorFormats != null && colorFormats.isNotEmpty()) {
                            var matches = false
                            for (colorFormat in colorFormats) {
                                if (matchesMediaCodecColorFormat(inputFormat, colorFormat)) {
                                    matches = true
                                    break
                                }
                            }
                            if (matches) {
                                /*
                                 * The supported input Formats are the supportedTypes.
                                 */
                                val outputFormat = getFmjFormatFromMediaCodecType(supportedType)
                                if (outputFormat != null && !outputFormats.contains(outputFormat)) {
                                    outputFormats.add(outputFormat)
                                }
                            }
                        }
                    }
                }
            }
            else {
                /* The supported input Formats are the supportedTypes. */
                for (supportedType in supportedTypes) {
                    if (matchesMediaCodecType(inputFormat, supportedType)) {
                        /* The supported output Formats are the colorFormats. */
                        val capabilities = getCapabilitiesForType(codecInfo, supportedType)
                        if (capabilities != null) {
                            val colorFormats = capabilities.colorFormats
                            if (colorFormats != null && colorFormats.isNotEmpty()) {
                                for (colorFormat in colorFormats) {
                                    val outputFormat = getFmjFormatFromMediaCodecColorFormat(colorFormat)
                                    if (outputFormat != null
                                            && !outputFormats.contains(outputFormat)) {
                                        outputFormats.add(outputFormat)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            codecIndex++
        }
        return outputFormats.toTypedArray() // toArray(EMPTY_FORMATS)
    }

    /**
     * Configures and starts [.mediaCodec] if the `inputFormat` and
     * `outputFormat` properties of this `Codec` have already been assigned suitable
     * values.
     */
    private fun maybeConfigureAndStart() {
        /*
         * We can discover an appropriate MediaCodec to be wrapped by this instance only if the
         * inputFormat and outputFormat are suitably set.
         */
        if (inputFormat == null || outputFormat == null) return

        /*
         * If the inputFormat and outputFormat are supported by the current MediaCodec, we will not
         * have to bring it in accord with them (because it is already in accord with them).
         */
        if (mediaCodec != null) {
            val typeFormat: Format
            val colorFormatFormat: Format
            if (encoder) {
                typeFormat = outputFormat
                colorFormatFormat = inputFormat
            }
            else {
                typeFormat = inputFormat
                colorFormatFormat = outputFormat
            }
            if (!matchesMediaCodecType(typeFormat, type)
                    || !matchesMediaCodecColorFormat(colorFormatFormat, colorFormat)) {
                doClose()
            }
        }
        if (mediaCodec != null) return

        /*
         * Find a MediaCodecInfo which supports the specified inputFormat and outputFormat of this
         * instance, initialize a MediaCodec from it to be wrapped by this instance, configure it
         * and start it.
         */
        var codecInfo: MediaCodecInfo? = null
        var codecIndex = 0
        val codecCount = MediaCodecList.getCodecCount()
        while (codecIndex < codecCount) {
            codecInfo = MediaCodecList.getCodecInfoAt(codecIndex)
            var typeFormat: Format
            var colorFormatFormat: Format
            if (codecInfo.isEncoder) {
                typeFormat = outputFormat
                colorFormatFormat = inputFormat
            }
            else {
                typeFormat = inputFormat
                colorFormatFormat = outputFormat
            }
            val supportedTypes = codecInfo.supportedTypes
            for (supportedType in supportedTypes) {
                if (!matchesMediaCodecType(typeFormat, supportedType)) continue
                val capabilities = getCapabilitiesForType(codecInfo,
                    supportedType) ?: continue
                val colorFormats = capabilities.colorFormats
                if (colorFormats == null || colorFormats.isEmpty()) continue
                for (colorFormat in colorFormats) {
                    if (matchesMediaCodecColorFormat(colorFormatFormat, colorFormat)) {
                        // We have found a MediaCodecInfo which supports
                        // inputFormat and outputFormat.
                        this.colorFormat = colorFormat
                        type = supportedType
                        break
                    }
                }

                // Have we found a MediaCodecInfo which supports inputFormat and
                // outputFormat yet?
                if (colorFormat != OMX_COLOR_FormatUnused && type != null) {
                    break
                }
            }

            // Have we found a MediaCodecInfo which supports inputFormat and
            // outputFormat yet?
            if (colorFormat != OMX_COLOR_FormatUnused && type != null) {
                break
            }
            codecIndex++
        }

        // Have we found a MediaCodecInfo which supports inputFormat and
        // outputFormat yet?
        if (colorFormat != OMX_COLOR_FormatUnused && type != null) {
            var mediaCodec: MediaCodec? = null
            try {
                mediaCodec = MediaCodec.createByCodecName(codecInfo!!.name)
            } catch (e: IOException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            }
            if (mediaCodec != null) {
                val format = MediaFormat()
                var flags = 0
                format.setString(MediaFormat.KEY_MIME, type)
                if (codecInfo!!.isEncoder) {
                    encoder = true
                    flags = flags or MediaCodec.CONFIGURE_FLAG_ENCODE
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                }
                else {
                    encoder = false
                }

                // Well, this Codec is either an encoder or a decoder so it
                // seems like only its inputFormat may specify the size/Dimension.
                if (inputFormat is VideoFormat) {
                    var size = (inputFormat as VideoFormat).size
                    if (size == null) size = Dimension(640, 480)
                    format.setInteger(MediaFormat.KEY_HEIGHT, size.height)
                    format.setInteger(MediaFormat.KEY_WIDTH, size.width)
                }
                mediaCodec.configure(format, null, null, flags)
                mediaCodec.start()
                this.mediaCodec = mediaCodec
                inputBuffers = mediaCodec.inputBuffers
                outputBuffers = mediaCodec.outputBuffers
                if (avFrame == null) avFrame = AVFrame()
            }
        }

        // At this point, mediaCodec should have successfully been initialized,
        // configured and started.
        checkNotNull(mediaCodec) { "mediaCodec" }
    }

    /**
     * {@inheritDoc}
     *
     * Makes sure that [.mediaCodec] is in accord in terms of properties with the
     * `inputFormat` and `outputFormat` set on this `Codec`.
     */
    override fun setInputFormat(format: Format): Format {
        val oldValue = inputFormat
        val setInputFormat = super.setInputFormat(format)
        val newValue = inputFormat
        if (oldValue != newValue && opened) maybeConfigureAndStart()
        return setInputFormat!!
    }

    /**
     * {@inheritDoc}
     *
     * Makes sure that [.mediaCodec] is in accord in terms of properties with the
     * `inputFormat` and `outputFormat` set on this `Codec`.
     */
    override fun setOutputFormat(format: Format): Format {
        var iFormat = format
        if (iFormat is AVFrameFormat) {
            val avFrameFormat = iFormat
            if (avFrameFormat.size == null) {
                iFormat = AVFrameFormat(Dimension(640, 480), avFrameFormat.frameRate,
                    avFrameFormat.pixFmt, avFrameFormat.deviceSystemPixFmt)
            }
        }
        val oldValue = outputFormat
        val setOutputFormat = super.setOutputFormat(iFormat)
        val newValue = outputFormat
        if (oldValue != newValue && opened) maybeConfigureAndStart()
        return setOutputFormat!!
    }

    companion object {
        /**
         * The interval of time in microseconds to wait for [MediaCodec.dequeueInputBuffer]
         * to dequeue an input buffer.
         */
        private const val DEQUEUE_INPUT_BUFFER_TIMEOUT =  /* second */(1000000 / 30).toLong()

        /* frames per second * /

    / **
     * The map of FMJ <code>Format</code> encodings to <code>MediaCodec</code> mime types which allows
     * converting between the two.
     */
        private val FMJ_ENCODINGS_TO_MEDIA_CODEC_TYPES: Array<String>

        /**
         * The mime type of H.264-encoded media data as defined by Android's `MediaCodec` class.
         */
        private const val H264_MEDIA_CODEC_TYPE = "video/avc"

        /**
         * The constant defined by OpenMAX IL to signify that a `colorFormat` value defined in
         * the terms of Android's `MediaCodec` class is unknown.
         */
        private const val OMX_COLOR_FormatUnused = 0

        /**
         * The map of `FFmpeg` pixel formats to `MediaCodec` `colorFormat`s which
         * allows converting between the two.
         */
        private val PIX_FMTS_TO_MEDIA_CODEC_COLOR_FORMATS: IntArray

        /**
         * The list of `Format`s of media data supported as input by `AndroidMediaCodec` instances.
         */
        private val SUPPORTED_INPUT_FORMATS: Array<Format>

        /**
         * The list of `Format`s of media data supported as output by `AndroidMediaCodec` instances.
         */
        private val SUPPORTED_OUTPUT_FORMATS: Array<Format>

        /**
         * The mime type of VP8-encoded media data as defined by Android's `MediaCodec` class.
         */
        private const val VP8_MEDIA_CODEC_TYPE = "video/x-vnd.on2.vp8"

        /**
         * The mime type of VP9-encoded media data as defined by Android's `MediaCodec` class.
         */
        private const val VP9_MEDIA_CODEC_TYPE = "video/x-vnd.on2.vp9"

        init {
            /*
             * AndroidMediaCodec is an FMJ Codec and, consequently, defines the various formats of media
             * (data) in FMJ's terms. MediaCodec is defined in its own (Android) terms. Make it possible
             * to translate between the two domains of terms.
             */
            FMJ_ENCODINGS_TO_MEDIA_CODEC_TYPES = arrayOf(
                Constants.H264, H264_MEDIA_CODEC_TYPE,
                Constants.VP9, VP9_MEDIA_CODEC_TYPE,
                Constants.VP8, VP8_MEDIA_CODEC_TYPE)
            PIX_FMTS_TO_MEDIA_CODEC_COLOR_FORMATS = intArrayOf(
                FFmpeg.PIX_FMT_NV12, CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar)

            /*
             * The Formats supported by AndroidMediaCodec as input and output are the mime types and
             * colorFormats (in the cases of video) supported by the MediaCodecs available on the Android system.
             */

            /*
             * We'll keep the list of FMJ VideoFormats equivalent to MediaCodecInfo.CodecCapabilities
             * colorFormats out of the loop bellow in order to minimize the production of garbage.
             */
            var bSupportedFormats: MutableList<Format>? = null
            val supportedInputFormats = ArrayList<Format>()
            val supportedOutputFormats = ArrayList<Format>()
            val mCodecInfos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos

            for (codecInfo in mCodecInfos) {
                val supportedTypes = codecInfo.supportedTypes
                for (supportedType in supportedTypes) {
                    /*
                     * Represent Android's MediaCodec mime type in the terms of FMJ (i.e. as an FMJ
                     * Format) because AndroidMediaCodec implements an FMJ Codec.
                     */
                    val aSupportedFormat = getFmjFormatFromMediaCodecType(supportedType) ?: continue

                    /*
                     * Android's mime type will determine either the supported input Format or the
                     * supported output Format of AndroidMediaCodec. The colorFormats will determine the
                     * other half of the information related to the supported Formats. Of course, that
                     * means that we will not utilize Android's MediaCodec for audio just yet.
                     */
                    val capabilities = getCapabilitiesForType(codecInfo, supportedType)
                            ?: continue
                    val colorFormats = capabilities.colorFormats
                    if (colorFormats == null || colorFormats.isEmpty()) continue
                    bSupportedFormats?.clear()

                    for (colorFormat in colorFormats) {
                        val bSupportedFormat = getFmjFormatFromMediaCodecColorFormat(colorFormat)
                                ?: continue
                        if (bSupportedFormats == null) {
                            bSupportedFormats = ArrayList<Format>(colorFormats.size)
                        }
                        bSupportedFormats.add(bSupportedFormat)
                    }

                    if (bSupportedFormats == null || bSupportedFormats.isEmpty()) continue

                    /*
                     * Finally, we know the FMJ Formats supported by Android's MediaCodec as input and output.
                     */
                    var a: MutableList<Format>
                    var b: MutableList<Format>

                    if (codecInfo.isEncoder) {
                        /*
                         * Android's supportedType i.e. aSupportedFormat specifies the output Format of
                         * the MediaCodec. Respectively, Android's colorFormats i.e. bSupportedFormats
                         * define the input Formats supported by the MediaCodec.
                         */
                        a = supportedOutputFormats
                        b = supportedInputFormats
                    }
                    else {
                        /*
                         * Android's supportedType i.e. aSupportedFormat specifies the input Format of
                         * the MediaCodec. Respectively, Android's colorFormats i.e. bSupportedFormats
                         * define the output Formats supported by the MediaCodec.
                         */
                        a = supportedInputFormats
                        b = supportedOutputFormats
                    }
                    if (!a.contains(aSupportedFormat)) a.add(aSupportedFormat)
                    for (bSupportedFormat in bSupportedFormats) {
                        if (!b.contains(bSupportedFormat)) b.add(bSupportedFormat)
                    }
                    val s = StringBuilder()
                    s.append("Supported MediaCodec:")
                    s.append(" name= ").append(codecInfo.name).append(';')
                    s.append(" mime= ").append(supportedType).append(';')
                    s.append(" colorFormats= ").append(colorFormats.contentToString()).append(';')

                    val profileLevels = capabilities.profileLevels
                    if (profileLevels != null && profileLevels.isNotEmpty()) {
                        s.append(" profileLevels= [")
                        for (i in profileLevels.indices) {
                            if (i != 0) s.append(" ")
                            val profileLevel = profileLevels[i]
                            s.append("profile= ").append(profileLevel.profile).append(", level= ")
                                .append(profileLevel.level)
                        }
                        s.append("]")
                    }
                    Timber.d("%s", s)
                }
            }
            SUPPORTED_INPUT_FORMATS = supportedInputFormats.toArray(EMPTY_FORMATS)
            SUPPORTED_OUTPUT_FORMATS = supportedOutputFormats.toArray(EMPTY_FORMATS)
        }

        /**
         * Invokes [MediaCodecInfo.getCapabilitiesForType] on a specific
         * `MediaCodecInfo` instance with a specific supported/mime type and logs and swallows
         * any `IllegalArgumentException`. Such an exception has been seen thrown on at least one
         * device with no known reason.
         *
         * @param codecInfo the `MediaCodecInfo` to invoke the method on
         * @param type the supported/mime type to pass as an argument to the method to be invoked
         * @return the result of the invocation of the method on the specified `codecInfo`
         */
        private fun getCapabilitiesForType(
                codecInfo: MediaCodecInfo?, type: String,
        ): CodecCapabilities? {
            var capabilities: CodecCapabilities?
            try {
                capabilities = codecInfo!!.getCapabilitiesForType(type)
            } catch (iae: IllegalArgumentException) {
                capabilities = null
                Timber.w(iae, "Invocation failed for supported/mime type: %s", type)
            }
            return capabilities
        }

        /**
         * Gets an FMJ `VideoFormat` instance which represents the same information about media
         * data as a specific `colorFormat` defined in the terms of Android's `MediaCodec` class.
         *
         * @param colorFormat the `colorFormat` value in the terms of Android's `MediaCodec` class to
         * get an FMJ `VideoFormat` equivalent of
         * @return an FMJ `VideoFormat` instance which represents the same information about
         * media data as (i.e. is equivalent to) the specified `colorFormat`
         */
        private fun getFmjFormatFromMediaCodecColorFormat(colorFormat: Int): VideoFormat? {
            var pixfmt = FFmpeg.PIX_FMT_NONE
            var i = 0
            while (i < PIX_FMTS_TO_MEDIA_CODEC_COLOR_FORMATS.size) {
                if (PIX_FMTS_TO_MEDIA_CODEC_COLOR_FORMATS[i + 1] == colorFormat) {
                    pixfmt = PIX_FMTS_TO_MEDIA_CODEC_COLOR_FORMATS[i]
                    break
                }
                i += 2
            }
            return if (pixfmt == FFmpeg.PIX_FMT_NONE) null else AVFrameFormat(pixfmt)
        }

        /**
         * Gets an FMJ `Format` instance which represents the same information about media data
         * as a specific mime type defined in the terms of Android's `MediaCodec` class.
         *
         * @param type the mime type in the terms of Android's `MediaCodec` class to get an FMJ
         * `Format` equivalent of
         * @return an FMJ `Format` instance which represents the same information about media
         * data as (i.e. is equivalent to) the specified `type`
         */
        private fun getFmjFormatFromMediaCodecType(type: String): Format? {
            var encoding: String? = null
            var i = 0
            while (i < FMJ_ENCODINGS_TO_MEDIA_CODEC_TYPES.size) {
                if (FMJ_ENCODINGS_TO_MEDIA_CODEC_TYPES[i + 1] == type) {
                    encoding = FMJ_ENCODINGS_TO_MEDIA_CODEC_TYPES[i]
                    break
                }
                i += 2
            }
            return encoding?.let { VideoFormat(it) }
        }

        /**
         * Gets a `colorFormat` value defined in the terms of Android's `MediaCodec` class
         * which is equivalent to a specific FMJ `Format`.
         *
         * @param format the FMJ `Format` to get the equivalent to
         * @return a `colorFormat` value defined in the terms of Android's `MediaCodec`
         * class which is equivalent to the specified `format` or
         * [.OMX_COLOR_FormatUnused] if no equivalent is known to `AndroidMediaCodec`
         */
        private fun getMediaCodecColorFormatFromFmjFormat(format: Format): Int {
            if (format is AVFrameFormat) {
                val avFrameFormat = format
                val pixfmt = avFrameFormat.pixFmt
                var i = 0
                while (i < PIX_FMTS_TO_MEDIA_CODEC_COLOR_FORMATS.size) {
                    if (PIX_FMTS_TO_MEDIA_CODEC_COLOR_FORMATS[i] == pixfmt) return PIX_FMTS_TO_MEDIA_CODEC_COLOR_FORMATS[i + 1]
                    i += 2
                }
            }
            return OMX_COLOR_FormatUnused
        }

        /**
         * Gets a mime type defined in the terms of Android's `MediaCodec` class which is
         * equivalent to a specific FMJ `Format`.
         *
         * @param format the FMJ `Format` to get the equivalent to
         * @return a mime type defined in the terms of Android's `MediaCodec` class which is
         * equivalent to the specified `format` or `null` if no equivalent is
         * known to `AndroidMediaCodec`
         */
        private fun getMediaCodecTypeFromFmjFormat(format: Format?): String? {
            if (format != null) {
                val encoding = format.encoding
                var i = 0
                while (i < FMJ_ENCODINGS_TO_MEDIA_CODEC_TYPES.size) {
                    if (FMJ_ENCODINGS_TO_MEDIA_CODEC_TYPES[i] == encoding) return FMJ_ENCODINGS_TO_MEDIA_CODEC_TYPES[i + 1]
                    i += 2
                }
            }
            return null
        }

        /**
         * Determines whether a specific FMJ `Format` matches (i.e. is equivalent to) a specific
         * `colorFormat` defined in the terms of Android's `MediaCodec` class.
         *
         * @param format the FMJ `Format` to be compared to the specified `colorFormat`
         * @param colorFormat the `colorFormat` defined in the terms of Android's `MediaCodec` class
         * to be compared to the specified `format`
         * @return `true` if the specified `format` matches (i.e. is equivalent to) the
         * specified `colorFormat` otherwise, `false`
         */
        private fun matchesMediaCodecColorFormat(format: Format, colorFormat: Int): Boolean {
            val formatColorFormat = getMediaCodecColorFormatFromFmjFormat(format)
            return formatColorFormat != OMX_COLOR_FormatUnused && formatColorFormat == colorFormat
        }

        /**
         * Determines whether a specific FMJ `Format` matches (i.e. is equivalent to) a specific
         * mime type defined in the terms of Android's `MediaCodec` class.
         *
         * @param format the FMJ `Format` to be compared to the specified `type`
         * @param type the media type defined in the terms of Android's `MediaCodec` class to be
         * compared to the specified `format`
         * @return `true` if the specified `format` matches (i.e. is equal to) the
         * specified `type` otherwise, `false`
         */
        private fun matchesMediaCodecType(format: Format, type: String?): Boolean {
            val formatType = getMediaCodecTypeFromFmjFormat(format)
            return formatType != null && formatType == type
        }
    }
}