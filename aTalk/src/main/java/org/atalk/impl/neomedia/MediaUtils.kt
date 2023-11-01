/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.codec.FFmpeg.avcodec_find_encoder
import org.atalk.impl.neomedia.device.ScreenDeviceImpl.Companion.defaultScreenDevice
import org.atalk.impl.neomedia.format.AudioMediaFormatImpl
import org.atalk.impl.neomedia.format.MediaFormatImpl
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.MediaService
import org.atalk.service.neomedia.codec.Constants
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.util.MediaType
import org.atalk.util.OSUtils
import timber.log.Timber
import java.awt.Dimension
import javax.media.Format
import javax.media.format.AudioFormat
import javax.media.format.VideoFormat
import javax.sdp.SdpConstants

/**
 * Implements static utility methods used by media classes.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
object MediaUtils {
    /**
     * An empty array with `MediaFormat` element type. Explicitly defined in order to reduce
     * unnecessary allocations, garbage collection.
     */
    val EMPTY_MEDIA_FORMATS = emptyArray<MediaFormat>()

    /**
     * The `Map` of JMF-specific encodings to well-known encodings as defined in RFC 3551.
     */
    private val jmfEncodingToEncodings = HashMap<String, String>()

    /**
     * The maximum number of channels for audio that is available through `MediaUtils`.
     */
    var MAX_AUDIO_CHANNELS = 0

    /**
     * The maximum sample rate for audio that is available through `MediaUtils`.
     */
    var MAX_AUDIO_SAMPLE_RATE = 0.0

    /**
     * The maximum sample size in bits for audio that is available through `MediaUtils`.
     */
    var MAX_AUDIO_SAMPLE_SIZE_IN_BITS = 0

    /**
     * The `MediaFormat`s which do not have RTP payload types assigned by RFC 3551 and are
     * thus referred to as having dynamic RTP payload types.
     */
    private val rtpPayloadTypelessMediaFormats = ArrayList<MediaFormat>()

    /**
     * The `Map` of RTP payload types (expressed as `String`s) to `MediaFormat`s.
     */
    private val rtpPayloadTypeStrToMediaFormats = HashMap<String, Array<MediaFormat>>()

    init {
        addMediaFormats(SdpConstants.PCMU.toByte(),
                "PCMU",
                MediaType.AUDIO,
                AudioFormat.ULAW_RTP,
                8000.0)

        /*
         * Some codecs depend on JMF native libraries which are only available on 32-bit Linux and
         * 32-bit Windows.
         */
        if (OSUtils.IS_LINUX32 || OSUtils.IS_WINDOWS32) {
            val g723FormatParams = HashMap<String, String>()
            g723FormatParams["annexa"] = "no"
            g723FormatParams["bitrate"] = "6.3"
            addMediaFormats(SdpConstants.G723.toByte(),
                    "G723",
                    MediaType.AUDIO,
                    AudioFormat.G723_RTP,
                    g723FormatParams,
                    null,
                    8000.0)
        }
        addMediaFormats(SdpConstants.GSM.toByte(),
                "GSM",
                MediaType.AUDIO,
                AudioFormat.GSM_RTP,
                8000.0)
        addMediaFormats(SdpConstants.PCMA.toByte(),
                "PCMA",
                MediaType.AUDIO,
                Constants.ALAW_RTP,
                8000.0)
        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                "iLBC",
                MediaType.AUDIO,
                Constants.ILBC_RTP,
                8000.0)
        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                Constants.SPEEX,
                MediaType.AUDIO,
                Constants.SPEEX_RTP,
                8000.0, 16000.0, 32000.0)
        addMediaFormats(SdpConstants.G722.toByte(),
                "G722",
                MediaType.AUDIO,
                Constants.G722_RTP,
                8000.0)

        /*
         * @see https://en.wikipedia.org/wiki/G.729 #Licensing
         * As of January 1, 2017, the patent terms of most licensed patents under the G.729 Consortium
         * have expired, the remaining unexpired patents are usable on a royalty-free basis.
         */
        // Set the encoder option according to user configuration; default to enable if none found.
        val cfg = LibJitsi.configurationService
        val g729Vad = cfg.getBoolean(Constants.PROP_G729_VAD, true)
        val g729FormatParams = HashMap<String, String>()
        g729FormatParams["annexb"] = if (g729Vad) "yes" else "no"
        addMediaFormats(SdpConstants.G729.toByte(),
                "G729",
                MediaType.AUDIO,
                AudioFormat.G729_RTP,
                g729FormatParams,
                null,
                8000.0)
        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                "telephone-event",
                MediaType.AUDIO,
                Constants.TELEPHONE_EVENT,
                8000.0)

        // Although we use "red" and "ulpfec" as jmf encodings here, FMJ should never see RTP
        // packets of these types. Such packets should be handled by transform  engines before
        // being passed to FMJ.
        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                Constants.RED,
                MediaType.VIDEO,
                Constants.RED)
        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                Constants.ULPFEC,
                MediaType.VIDEO,
                Constants.ULPFEC)
        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                Constants.FLEXFEC_03,
                MediaType.VIDEO,
                Constants.FLEXFEC_03)
        val advertiseFEC = cfg.getBoolean(Constants.PROP_SILK_ADVERSISE_FEC, false)
        val silkFormatParams = HashMap<String, String>()
        if (advertiseFEC) {
            silkFormatParams["useinbandfec"] = "1"
        }
        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                "SILK",
                MediaType.AUDIO,
                Constants.SILK_RTP,
                silkFormatParams,
                null,
                8000.0, 12000.0, 16000.0, 24000.0)

        /*
         * RTP Payload Format for the Opus Speech and Audio Codec (June 2015)
         * https://tools.ietf.org/html/rfc7587
         */
        val opusFormatParams = HashMap<String, String>()
        // opusFormatParams.put("minptime", "10"); /* not in rfc7587 */

        /*
         * Decoder support for FEC SHOULD be indicated at the time a session is setup.
         */
        val opusFec = cfg.getBoolean(Constants.PROP_OPUS_FEC, true)
        if (opusFec) {
            opusFormatParams["useinbandfec"] = "1"
        }

        /*
         * DTX can be used with both variable and constant bitrate.  It will have a slightly lower speech
         * or audio quality than continuous transmission.  Therefore, using continuous transmission is
         * RECOMMENDED unless constraints on available network bandwidth are severe.
         * If no value is specified, the default is 0.
         */
        val opusDtx = cfg.getBoolean(Constants.PROP_OPUS_DTX, false)
        if (opusDtx) {
            opusFormatParams["usedtx"] = "1"
        }
        val opusAdvancedParams = HashMap<String, String>()
        /* The preferred duration of media represented by a packet that a decoder wants to receive, in milliseconds */
        opusAdvancedParams[Constants.PTIME] = "20"
        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                Constants.OPUS,
                MediaType.AUDIO,
                Constants.OPUS_RTP,
                2,
                opusFormatParams,
                opusAdvancedParams,
                48000.0)


        // Adaptive Multi-Rate Wideband (AMR-WB)
        // Checks whether ffmpeg is enabled and whether AMR-WB is available in the provided binaries
        val enableFfmpeg = cfg.getBoolean(MediaService.ENABLE_FFMPEG_CODECS_PNAME, true)
        var amrwbEnabled = false
        if (enableFfmpeg) {
            try {
                amrwbEnabled = avcodec_find_encoder(FFmpeg.CODEC_ID_AMR_WB) != 0L
            } catch (t: Throwable) {
                Timber.d("AMR-WB codec not found %s", t.message)
            }
        }
        if (amrwbEnabled) {
            addMediaFormats(
                    MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                    Constants.AMR_WB,
                    MediaType.AUDIO,
                    Constants.AMR_WB_RTP,
                    16000.0)
        }

        /* H264 */
        // Checks whether ffmpeg is enabled and whether h264 is available in the provided binaries
        var h264Enabled = false
        if (enableFfmpeg) {
            try {
                h264Enabled = avcodec_find_encoder(FFmpeg.CODEC_ID_H264) != 0L
            } catch (t: Throwable) {
                Timber.d("H264 codec not found: %s", t.message)
            }
        }

        // register h264 media formats if codec is present or there is
        // a property that forces enabling the formats (in case of videobridge)
        if (h264Enabled
                || cfg.getBoolean(MediaService.ENABLE_H264_FORMAT_PNAME, false)) {
            val h264FormatParams = HashMap<String, String>()
            val packetizationMode = VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP
            val h264AdvancedAttributes = HashMap<String, String>()

            /*
             * Disable PLI because the periodic intra-refresh feature of FFmpeg/x264 is used.
             */
            // h264AdvancedAttributes.put("rtcp-fb", "nack pli");

            /*
             * XXX The initialization of MediaServiceImpl is very complex so it is wise to not
             * reference it at the early stage of its initialization.
             */
            val res = defaultScreenDevice?.size
            h264AdvancedAttributes["imageattr"] = createImageAttr(null, res)

            /* if ((cfg == null)
                    || cfg.getString("neomedia.codec.video.h264.defaultProfile",
                    JNIEncoder.MAIN_PROFILE).equals(JNIEncoder.MAIN_PROFILE)) {
                // main profile, common features, HD capable level 3.1
                h264FormatParams.put("profile-level-id", "4DE01f");
            }
            else */
            run {
                // baseline profile, common features, HD capable level 3.1
                h264FormatParams["profile-level-id"] = "42E01f"
            }

            // if (cfg.getBoolean("neomedia.codec.video.h264.enabled", false)) {
            // By default, packetization-mode=1 is enabled.
            if (cfg.getBoolean("neomedia.codec.video.h264.packetization-mode-1.enabled", true)) {
                // packetization-mode=1
                h264FormatParams[packetizationMode] = "1"
                addMediaFormats(
                        MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                        Constants.H264,
                        MediaType.VIDEO,
                        Constants.H264_RTP,
                        h264FormatParams,
                        h264AdvancedAttributes)
            }
            // packetization-mode=0

            /*
             * XXX At the time of this writing,
             * EncodingConfiguration#compareEncodingPreferences(MediaFormat, MediaFormat) is incomplete
             * and considers two MediaFormats to be equal if they have an equal number of format
             * parameters (given that the encodings and clock rates are equal, of course). Either fix
             * the method in question or don't add a format parameter for packetization-mode 0
             * equivalent to having packetization-mode explicitly defined as 0 anyway, according to the
             * respective RFC).
             */
            h264FormatParams.remove(packetizationMode)
            addMediaFormats(
                    MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                    Constants.H264,
                    MediaType.VIDEO,
                    Constants.H264_RTP,
                    h264FormatParams,
                    h264AdvancedAttributes
            )
        }
        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                Constants.VP8,
                MediaType.VIDEO,
                Constants.VP8_RTP,
                null, null)
        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                Constants.VP9,
                MediaType.VIDEO,
                Constants.VP9_RTP,
                null, null)
        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                Constants.RTX,
                MediaType.VIDEO,
                Constants.RTX_RTP,
                null, null)

        // Calculate the values of the MAX_AUDIO_* static fields of MediaUtils.
        val audioMediaFormats = ArrayList<MediaFormat>(
                rtpPayloadTypeStrToMediaFormats.size + rtpPayloadTypelessMediaFormats.size)
        for (mediaFormats in rtpPayloadTypeStrToMediaFormats.values) for (mediaFormat in mediaFormats) if (MediaType.AUDIO == mediaFormat.mediaType) audioMediaFormats.add(mediaFormat)
        for (mediaFormat in rtpPayloadTypelessMediaFormats) if (MediaType.AUDIO == mediaFormat.mediaType) audioMediaFormats.add(mediaFormat)
        var maxAudioChannels = Format.NOT_SPECIFIED
        var maxAudioSampleRate = Format.NOT_SPECIFIED.toDouble()
        var maxAudioSampleSizeInBits = Format.NOT_SPECIFIED
        for (mediaFormat in audioMediaFormats) {
            val audioMediaFormat = mediaFormat as AudioMediaFormatImpl
            val encoding = audioMediaFormat.encoding
            var channels = audioMediaFormat.channels
            val sampleRate = audioMediaFormat.clockRate
            val sampleSizeInBits = audioMediaFormat.format!!.sampleSizeInBits

            // The opus/rtp format has 2 channels, but we don't want it to trigger use of stereo
            // elsewhere.
            if (Constants.OPUS.equals(encoding, ignoreCase = true)) channels = 1
            if (maxAudioChannels < channels) maxAudioChannels = channels
            if (maxAudioSampleRate < sampleRate) maxAudioSampleRate = sampleRate
            if (maxAudioSampleSizeInBits < sampleSizeInBits) maxAudioSampleSizeInBits = sampleSizeInBits
        }
        MAX_AUDIO_CHANNELS = maxAudioChannels
        MAX_AUDIO_SAMPLE_RATE = maxAudioSampleRate
        MAX_AUDIO_SAMPLE_SIZE_IN_BITS = maxAudioSampleSizeInBits
    }

    /**
     * Adds a new mapping of a specific RTP payload type to a list of `MediaFormat`s
     * of a specific `MediaType`, with a specific JMF encoding and, optionally, with
     * specific clock rates.
     *
     * @param rtpPayloadType the RTP payload type to be associated with a list of `MediaFormat`s
     * @param encoding the well-known encoding (name) corresponding to `rtpPayloadType` (in
     * contrast to the JMF-specific encoding specified by `jmfEncoding`)
     * @param mediaType the `MediaType` of the `MediaFormat`s to be associated with
     * `rtpPayloadType`
     * @param jmfEncoding the JMF encoding of the `MediaFormat`s to be associated with
     * `rtpPayloadType`
     * @param clockRates the optional list of clock rates of the `MediaFormat`s to be associated
     * with `rtpPayloadType`
     */
    private fun addMediaFormats(
            rtpPayloadType: Byte,
            encoding: String,
            mediaType: MediaType,
            jmfEncoding: String,
            vararg clockRates: Double,
    ) {
        addMediaFormats(
                rtpPayloadType,
                encoding,
                mediaType,
                jmfEncoding,
                null,
                null,
                *clockRates)
    }

    /**
     * Adds a new mapping of a specific RTP payload type to a list of `MediaFormat`s of a
     * specific `MediaType`, with a specific JMF encoding and, optionally, with specific
     * clock rates.
     *
     * @param rtpPayloadType the RTP payload type to be associated with a list of `MediaFormat`s
     * @param encoding the well-known encoding (name) corresponding to `rtpPayloadType` (in contrast
     * to the JMF-specific encoding specified by `jmfEncoding`)
     * @param mediaType the `MediaType` of the `MediaFormat`s to be associated with
     * `rtpPayloadType`
     * @param jmfEncoding the JMF encoding of the `MediaFormat`s to be associated with
     * `rtpPayloadType`
     * @param channels number of channels
     * @param formatParameters the set of format-specific parameters of the `MediaFormat`s to be associated
     * with `rtpPayloadType`
     * @param advancedAttributes the set of advanced attributes of the `MediaFormat`s to be associated with
     * `rtpPayload`
     * @param clockRates the optional list of clock rates of the `MediaFormat`s to be associated with
     * `rtpPayloadType`
     */
    private fun addMediaFormats(
            rtpPayloadType: Byte,
            encoding: String,
            mediaType: MediaType,
            jmfEncoding: String,
            channels: Int,
            formatParameters: Map<String, String>?,
            advancedAttributes: Map<String, String>?,
            vararg clockRates: Double,
    ) {
        val clockRateCount = clockRates.size
        val mediaFormats = ArrayList<MediaFormat>(clockRateCount)
        if (clockRateCount > 0) {
            for (clockRate in clockRates) {

                val format = when (mediaType) {
                    MediaType.AUDIO ->
                        if (channels == 1) AudioFormat(jmfEncoding)
                        else {
                            AudioFormat(
                                    jmfEncoding,
                                    Format.NOT_SPECIFIED.toDouble(),
                                    Format.NOT_SPECIFIED,
                                    channels)
                        }

                    MediaType.VIDEO -> ParameterizedVideoFormat(jmfEncoding, formatParameters)
                    else -> throw IllegalArgumentException("mediaType")
                }

                val mediaFormat = MediaFormatImpl.createInstance(format, clockRate,
                        formatParameters, advancedAttributes)
                if (mediaFormat != null) mediaFormats.add(mediaFormat)
            }
        } else {
            val format: Format
            val clockRate: Double
            when (mediaType) {
                MediaType.AUDIO -> {
                    val audioFormat = AudioFormat(jmfEncoding)
                    format = audioFormat
                    clockRate = audioFormat.sampleRate
                }

                MediaType.VIDEO -> {
                    format = ParameterizedVideoFormat(jmfEncoding, formatParameters)
                    clockRate = VideoMediaFormatImpl.DEFAULT_CLOCK_RATE
                }

                else -> throw IllegalArgumentException("mediaType")
            }

            val mediaFormat = MediaFormatImpl.createInstance(format, clockRate, formatParameters, advancedAttributes)
            if (mediaFormat != null) mediaFormats.add(mediaFormat)
        }
        if (mediaFormats.size > 0) {
            when (MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN) {
                rtpPayloadType -> rtpPayloadTypelessMediaFormats.addAll(mediaFormats)
                else -> rtpPayloadTypeStrToMediaFormats[rtpPayloadType.toString()] = mediaFormats.toArray(EMPTY_MEDIA_FORMATS)
            }
            jmfEncodingToEncodings[(mediaFormats[0] as MediaFormatImpl<out Format?>).jMFEncoding] = encoding
        }
    }

    /**
     * Adds a new mapping of a specific RTP payload type to a list of `MediaFormat`s of a
     * specific `MediaType`, with a specific JMF encoding and, optionally, with specific
     * clock rates.
     *
     * @param rtpPayloadType the RTP payload type to be associated with a list of `MediaFormat`s
     * @param encoding the well-known encoding (name) corresponding to `rtpPayloadType` (in contrast
     * to the JMF-specific encoding specified by `jmfEncoding`)
     * @param mediaType the `MediaType` of the `MediaFormat`s to be associated with `rtpPayloadType`
     * @param jmfEncoding the JMF encoding of the `MediaFormat`s to be associated with rtpPayloadType`
     * @param formatParameters the set of format-specific parameters of the `MediaFormat`s to be associated
     * with `rtpPayloadType`
     * @param advancedAttributes the set of advanced attributes of the `MediaFormat`s to be associated with
     * `rtpPayload`
     * @param clockRates the optional list of clock rates of the `MediaFormat`s to be associated with
     * `rtpPayloadType`
     */
    private fun addMediaFormats(
            rtpPayloadType: Byte,
            encoding: String,
            mediaType: MediaType,
            jmfEncoding: String,
            formatParameters: Map<String, String>?,
            advancedAttributes: Map<String, String>?,
            vararg clockRates: Double,
    ) {
        addMediaFormats(
                rtpPayloadType,
                encoding,
                mediaType,
                jmfEncoding,
                1 /* channel */,
                formatParameters,
                advancedAttributes,
                *clockRates)
    }

    /**
     * Creates value of an imgattr.
     *
     * [...](https://tools.ietf.org/html/rfc6236)
     *
     * @param sendSize maximum size peer can send
     * @param maxRecvSize maximum size peer can display
     *
     * @return string that represent imgattr that can be encoded via SIP/SDP or XMPP/Jingle
     */
    fun createImageAttr(sendSize: Dimension?, maxRecvSize: Dimension?): String {
        val img = StringBuilder()

        /* send width */
        if (sendSize != null) {
            /* single value => send [x=width,y=height] */
            /*img.append("send [x=");
			img.append((int)sendSize.getWidth());
            img.append(",y=");
            img.append((int)sendSize.getHeight());
            img.append("]");*/
            /* send [x=[min:max],y=[min:max]] */
            img.append("send [x=[1:")
            img.append(sendSize.getWidth().toInt())
            img.append("],y=[1:")
            img.append(sendSize.getHeight().toInt())
            img.append("]]")
            /*
			else
            {
                // range
                img.append(" send [x=[");
                img.append((int)minSendSize.getWidth());
                img.append(":");
                img.append((int)maxSendSize.getWidth());
                img.append("],y=[");
                img.append((int)minSendSize.getHeight());
                img.append(":");
                img.append((int)maxSendSize.getHeight());
                img.append("]]");
            }
            */
        } else {
            /* can send "all" sizes */
            img.append("send *")
        }

        /* receive size */
        if (maxRecvSize != null) {
            // basically we can receive any size up to our screen display size

            /* recv [x=[min:max],y=[min:max]] */
            img.append(" recv [x=[1:")
            img.append(maxRecvSize.getWidth().toInt())
            img.append("],y=[1:")
            img.append(maxRecvSize.getHeight().toInt())
            img.append("]]")
        } else {
            /* accept all sizes */
            img.append(" recv *")
        }
        return img.toString()
    }

    /**
     * Gets a `MediaFormat` predefined in `MediaUtils` which represents a specific
     * JMF `Format`. If there is no such representing `MediaFormat` in
     * `MediaUtils`, returns `null`.
     *
     * @param format the JMF `Format` to get the `MediaFormat` representation for
     *
     * @return a `MediaFormat` predefined in `MediaUtils` which represents
     * `format` if any; `null` if there is no such representing
     * `MediaFormat` in `MediaUtils`
     */
    fun getMediaFormat(format: Format): MediaFormat? {
        val clockRate = when (format) {
            is AudioFormat -> format.sampleRate
            is VideoFormat -> VideoMediaFormatImpl.DEFAULT_CLOCK_RATE
            else -> Format.NOT_SPECIFIED.toDouble()
        }

        val rtpPayloadType = getRTPPayloadType(format.encoding, clockRate)
        if (MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN != rtpPayloadType) {
            for (mediaFormat in getMediaFormats(rtpPayloadType)) {
                val mediaFormatImpl = mediaFormat as MediaFormatImpl<out Format>
                if (format.matches(mediaFormatImpl.format)) return mediaFormat
            }
        }
        return null
    }

    /**
     * Gets the `MediaFormat` known to `MediaUtils` and having the specified
     * well-known `encoding` (name) and `clockRate`.
     *
     * @param encoding the well-known encoding (name) of the `MediaFormat` to get
     * @param clockRate the clock rate of the `MediaFormat` to get
     *
     * @return the `MediaFormat` known to `MediaUtils` and having the specified
     * `encoding` and `clockRate`
     */
    @JvmStatic
    fun getMediaFormat(encoding: String, clockRate: Double): MediaFormat? {
        return getMediaFormat(encoding, clockRate, null)
    }

    /**
     * Gets the `MediaFormat` known to `MediaUtils` and having the specified
     * well-known `encoding` (name), `clockRate` and matching format parameters.
     *
     * @param encoding the well-known encoding (name) of the `MediaFormat` to get
     * @param clockRate the clock rate of the `MediaFormat` to get
     * @param fmtps the format parameters of the `MediaFormat` to get
     *
     * @return the `MediaFormat` known to `MediaUtils` and having the specified
     * `encoding` (name), `clockRate` and matching format parameters
     */
    @JvmStatic
    fun getMediaFormat(encoding: String, clockRate: Double, fmtps: Map<String, String>?): MediaFormat? {
        for (format in getMediaFormats(encoding)) {
            if (format.clockRate == clockRate && format.formatParametersMatch(fmtps)) return format
        }
        return null
    }

    /**
     * Gets the index of a specific `MediaFormat` instance within the internal storage of
     * `MediaUtils`. Since the index is in the internal storage which may or may not be one
     * and the same for the various `MediaFormat` instances and which may or may not be
     * searched for the purposes of determining the index, the index is not to be used as a way to
     * determine whether `MediaUtils` knows the specified `mediaFormat`
     *
     * @param mediaFormat the `MediaFormat` to determine the index of
     *
     * @return the index of the specified `mediaFormat` in the internal storage of
     * `MediaUtils`
     */
    fun getMediaFormatIndex(mediaFormat: MediaFormat): Int {
        return rtpPayloadTypelessMediaFormats.indexOf(mediaFormat)
    }

    /**
     * Gets the `MediaFormat`s (expressed as an array) corresponding to a specific RTP
     * payload type.
     *
     * @param rtpPayloadType the RTP payload type to retrieve the corresponding `MediaFormat`s for
     *
     * @return an array of `MediaFormat`s corresponding to the specified RTP payload type
     */
    fun getMediaFormats(rtpPayloadType: Byte): Array<MediaFormat> {
        val mediaFormats = rtpPayloadTypeStrToMediaFormats[rtpPayloadType.toString()]
        return mediaFormats?.clone() ?: EMPTY_MEDIA_FORMATS
    }

    /**
     * Gets the `MediaFormat`s known to `MediaUtils` and being of the specified
     * `MediaType`.
     *
     * @param mediaType the `MediaType` of the `MediaFormat`s to get
     *
     * @return the `MediaFormat`s known to `MediaUtils` and being of the specified
     * `mediaType`
     */
    fun getMediaFormats(mediaType: MediaType): Array<MediaFormat> {
        val mediaFormats = ArrayList<MediaFormat>()
        for (formats in rtpPayloadTypeStrToMediaFormats.values) {
            for (format in formats) {
                if (format.mediaType == mediaType) mediaFormats.add(format)
            }
        }
        for (format in rtpPayloadTypelessMediaFormats) {
            if (format.mediaType == mediaType) mediaFormats.add(format)
        }
        return mediaFormats.toArray(EMPTY_MEDIA_FORMATS)
    }

    /**
     * Gets the `MediaFormat`s predefined in `MediaUtils` with a specific well-known
     * encoding (name) as defined by RFC 3551
     * "RTP Profile for Audio and Video Conferences with Minimal Control".
     *
     * @param encoding the well-known encoding (name) to get the corresponding `MediaFormat`s of
     *
     * @return a `List` of `MediaFormat`s corresponding to the specified encoding (name)
     */
    fun getMediaFormats(encoding: String): List<MediaFormat> {
        var jmfEncoding: String? = null
        for ((key, value) in jmfEncodingToEncodings) {
            if (value.equals(encoding, ignoreCase = true)) {
                jmfEncoding = key
                break
            }
        }
        val mediaFormats = ArrayList<MediaFormat>()
        if (jmfEncoding != null) {
            for (rtpPayloadTypeMediaFormats in rtpPayloadTypeStrToMediaFormats.values) {
                for (rtpPayloadTypeMediaFormat in rtpPayloadTypeMediaFormats) {
                    if ((rtpPayloadTypeMediaFormat as MediaFormatImpl<out Format?>)
                                    .jMFEncoding == jmfEncoding) mediaFormats.add(rtpPayloadTypeMediaFormat)
                }
            }
            if (mediaFormats.size < 1) {
                for (rtpPayloadTypelessMediaFormat in rtpPayloadTypelessMediaFormats) {
                    if ((rtpPayloadTypelessMediaFormat as MediaFormatImpl<out Format?>)
                                    .jMFEncoding == jmfEncoding) mediaFormats.add(rtpPayloadTypelessMediaFormat)
                }
            }
        }
        return mediaFormats
    }

    /**
     * Gets the RTP payload type corresponding to a specific JMF encoding and clock rate.
     *
     * @param jmfEncoding the JMF encoding as returned by
     * [Format.getEncoding] or the respective `AudioFormat` and
     * `VideoFormat` encoding constants to get the corresponding RTP payload type of
     * @param clockRate the clock rate to be taken into account in the search for the RTP payload type if the
     * JMF encoding does not uniquely identify it
     *
     * @return the RTP payload type corresponding to the specified JMF encoding and clock rate if
     * known in RFC 3551 "RTP Profile for Audio and Video Conferences with Minimal Control";
     * otherwise,
     * [MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN]
     */
    fun getRTPPayloadType(jmfEncoding: String?, clockRate: Double): Byte {
        return if (jmfEncoding == null) {
            MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN
        } else if (jmfEncoding == AudioFormat.ULAW_RTP) {
            SdpConstants.PCMU.toByte()
        } else if (jmfEncoding == Constants.ALAW_RTP) {
            SdpConstants.PCMA.toByte()
        } else if (jmfEncoding == AudioFormat.G723_RTP) {
            SdpConstants.G723.toByte()
        } else if (jmfEncoding == AudioFormat.DVI_RTP && clockRate == 8000.0) {
            SdpConstants.DVI4_8000.toByte()
        } else if (jmfEncoding == AudioFormat.DVI_RTP && clockRate == 16000.0) {
            SdpConstants.DVI4_16000.toByte()
        } else if (jmfEncoding == AudioFormat.ALAW) {
            SdpConstants.PCMA.toByte()
        } else if (jmfEncoding == Constants.G722) {
            SdpConstants.G722.toByte()
        } else if (jmfEncoding == Constants.G722_RTP) {
            SdpConstants.G722.toByte()
        } else if (jmfEncoding == AudioFormat.GSM) {
            SdpConstants.GSM.toByte()
        } else if (jmfEncoding == AudioFormat.GSM_RTP) {
            SdpConstants.GSM.toByte()
        } else if (jmfEncoding == AudioFormat.G728_RTP) {
            SdpConstants.G728.toByte()
        } else if (jmfEncoding == AudioFormat.G729_RTP) {
            SdpConstants.G729.toByte()
        } else if (jmfEncoding == VideoFormat.JPEG_RTP) {
            SdpConstants.JPEG.toByte()
        } else if (jmfEncoding == VideoFormat.H261_RTP) {
            SdpConstants.H261.toByte()
        } else {
            MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN
        }
    }

    /**
     * Gets the well-known encoding (name) as defined in RFC 3551
     * "RTP Profile for Audio and Video Conferences with Minimal Control" corresponding to a given
     * JMF-specific encoding.
     *
     * @param jmfEncoding the JMF encoding to get the corresponding well-known encoding of
     *
     * @return the well-known encoding (name) as defined in RFC 3551
     * "RTP Profile for Audio and Video Conferences with Minimal Control" corresponding to
     * `jmfEncoding` if any; otherwise, `null`
     */
    fun jmfEncodingToEncoding(jmfEncoding: String): String? {
        return jmfEncodingToEncodings[jmfEncoding]
    }
}