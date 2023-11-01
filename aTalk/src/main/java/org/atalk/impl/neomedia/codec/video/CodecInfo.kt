/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video

import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecList
import org.atalk.service.neomedia.codec.Constants
import timber.log.Timber
import java.util.*

/**
 * Class used to manage codecs information for `MediaCodec`.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
abstract class CodecInfo(
        /**
         * `MediaCodecInfo` encapsulated by this instance.
         */
        protected val codecInfo: MediaCodecInfo,
        /**
         * Media type of this `CodecInfo`.
         */
        private val mediaType: String) {
    /**
     * `MediaCodecInfo.CodecCapabilities` encapsulated by this instance.
     */
    protected val caps = codecInfo.getCapabilitiesForType(mediaType)

    /**
     * List of color formats supported by subject `MediaCodec`.
     */
    protected val colors = ArrayList<CodecColorFormat>()

    /**
     * Profile levels supported by subject `MediaCodec`.
     */
    private var profileLevels: Array<ProfileLevel?>? = null

    /**
     * Flag indicates that this codec is known to cause some troubles and is disabled
     * (will be ignored during codec select phase).
     */
    var isBanned = false

    /**
     * Creates a new instance of `CodecInfo` that will encapsulate given `codecInfo`.
     *
     * @param codecInfo the codec info object to encapsulate.
     * @param mediaType media type of the codec
     */
    init {
        val colorFormats = caps.colorFormats
        for (colorFormat in colorFormats) {
            colors.add(CodecColorFormat.Companion.fromInt(colorFormat))
        }
    }

    /**
     * Returns codec name that can be used to obtain `MediaCodec`.
     *
     * @return codec name that can be used to obtain `MediaCodec`.
     */
    val name: String
        get() = codecInfo.name

    /**
     * Returns the list of profiles supported.
     *
     * @return the list of profiles supported.
     */
    protected abstract val profileSet: Array<Profile>

    /**
     * Returns the list supported levels.
     *
     * @return the list supported levels.
     */
    protected abstract val levelSet: Array<Level>
    private fun getProfile(profileInt: Int): Profile {
        for (p in profileSet) {
            if (p.value == profileInt) return p
        }
        return Profile("Unknown", profileInt)
    }

    private fun getLevel(levelInt: Int): Level {
        for (l in levelSet) {
            if (l.value == levelInt) return l
        }
        return Level("Unknown", levelInt)
    }

    private fun getProfileLevels(): Array<ProfileLevel?> {
        if (profileLevels == null) {
            val plArray = caps.profileLevels
            profileLevels = arrayOfNulls(plArray.size)

            for (i in profileLevels!!.indices) {
                val p = getProfile(plArray[i].profile)
                val l = getLevel(plArray[i].level)
                profileLevels!![i] = ProfileLevel(p, l)
            }
        }
        return profileLevels!!
    }

    override fun toString(): String {
        val colorStr = StringBuilder("\ncolors:\n")
        for (i in colors.indices) {
            colorStr.append(colors[i])
            if (i != colors.size - 1) colorStr.append(", \n")
        }
        val plStr = StringBuilder("\nprofiles:\n")
        val profiles = getProfileLevels()
        for (i in profiles.indices) {
            plStr.append(profiles[i].toString())
            if (i != profiles.size - 1) plStr.append(", \n")
        }
        return codecInfo.name + "(" + libjitsiEncoding + ")" + colorStr + plStr
    }

    val isEncoder: Boolean
        get() = codecInfo.isEncoder
    val isNominated: Boolean
        get() = getCodecForType(mediaType, isEncoder) === this
    val libjitsiEncoding: String
        get() = when (mediaType) {
            MEDIA_CODEC_TYPE_H264 -> Constants.H264
            MEDIA_CODEC_TYPE_VP8 -> Constants.VP8
            MEDIA_CODEC_TYPE_VP9 -> Constants.VP9
            else -> mediaType
        }

    class ProfileLevel(private val profile: Profile, private val level: Level) {
        override fun toString(): String {
            return "P: $profile L: $level"
        }
    }

    class Profile(private val name: String, val value: Int) {
        override fun toString(): String {
            return name + "(0x" + Integer.toString(value, 16) + ")"
        }
    }

    class Level(private val name: String, val value: Int) {
        override fun toString(): String {
            return name + "(0x" + Integer.toString(value, 16) + ")"
        }
    }

    internal class H264CodecInfo(codecInfo: MediaCodecInfo) : CodecInfo(codecInfo, MEDIA_CODEC_TYPE_H264) {
        // from OMX_VIDEO_AVCPROFILETYPE
        private val PROFILES = arrayOf(
                Profile("AVCProfileBaseline", 0x01),
                Profile("AVCProfileMain", 0x02),
                Profile("AVCProfileExtended", 0x04),
                Profile("AVCProfileHigh", 0x08),
                Profile("AVCProfileHigh10", 0x10),
                Profile("AVCProfileHigh422", 0x20),
                Profile("AVCProfileHigh444", 0x40),
                Profile("AVCProfileConstrainedBaseline", 0x10000),
                Profile("AVCProfileConstrainedBaseline", 0x80000)
        )

        // from OMX_VIDEO_AVCLEVELTYPE
        private val LEVELS = arrayOf(
                Level("Level1", 0x01),
                Level("Level1b", 0x02),
                Level("Level11", 0x04),
                Level("Level12", 0x08),
                Level("Level13", 0x10),
                Level("Level2", 0x20),
                Level("Level21", 0x40),
                Level("Level22", 0x80),
                Level("Level3", 0x100),
                Level("Level31", 0x200),
                Level("Level32", 0x400),
                Level("Level4", 0x800),
                Level("Level41", 0x1000),
                Level("Level42", 0x2000),
                Level("Level5", 0x4000),
                Level("Level51", 0x8000)
        )

        override val profileSet: Array<Profile>
            get() = PROFILES

        override val levelSet: Array<Level>
            get() = LEVELS
    }

    internal class VP8CodecInfo(codecInfo: MediaCodecInfo) : CodecInfo(codecInfo, MEDIA_CODEC_TYPE_VP8) {
        private val PROFILES = arrayOf( // from OMX_VIDEO_VP8PROFILETYPE
                Profile("ProfileMain", 0x01)
        )
        private val LEVELS = arrayOf( // from OMX_VIDEO_VP8LEVELTYPE
                Level("VP8Level_Version0", 0x01),
                Level("VP8Level_Version1", 0x02),
                Level("VP8Level_Version2", 0x04),
                Level("VP8Level_Version3", 0x08)
        )

        override val profileSet: Array<Profile>
            get() = PROFILES

        override val levelSet: Array<Level>
            get() = LEVELS
    }

    internal class VP9CodecInfo(codecInfo: MediaCodecInfo) : CodecInfo(codecInfo, MEDIA_CODEC_TYPE_VP9) {
        private val PROFILES = arrayOf( // from OMX_VIDEO_VP9PROFILETYPE
                Profile("VP9Profile0", 0x01),
                Profile("VP9Profile1", 0x02),
                Profile("VP9Profile2", 0x04),
                Profile("VP9Profile3", 0x08),  // HDR profiles also support passing HDR metadata
                Profile("VP9Profile2HDR", 0x1000),
                Profile("VP9Profile3HDR", 0x2000))
        private val LEVELS = arrayOf( // from OMX_VIDEO_VP9LEVELTYPE
                Level("VP9Level1", 0x01),
                Level("VP9Level11", 0x02),
                Level("VP9Level2", 0x04),
                Level("VP9Level2", 0x08),
                Level("VP9Level3", 0x10),
                Level("VP9Level31", 0x20),
                Level("VP9Level4", 0x40),
                Level("VP9Level41", 0x80),
                Level("VP9Level5", 0x100),
                Level("VP9Level51", 0x200),
                Level("VP9Level52", 0x400),
                Level("VP9Level52", 0x800),
                Level("VP9Level61", 0x1000),
                Level("VP9Level62", 0x2000)
        )
        override val profileSet: Array<Profile>
            get() = PROFILES

        override val levelSet: Array<Level>
            get() = LEVELS
    }

    companion object {
        /**
         * The mime type of H.264-encoded media data as defined by Android's `MediaCodec` class.
         */
        const val MEDIA_CODEC_TYPE_H264 = "video/avc"

        /**
         * The mime type of VP8-encoded media data as defined by Android's `MediaCodec` class.
         */
        const val MEDIA_CODEC_TYPE_VP8 = "video/x-vnd.on2.vp8"

        /**
         * The mime type of VP9-encoded media data as defined by Android's `MediaCodec` class.
         */
        const val MEDIA_CODEC_TYPE_VP9 = "video/x-vnd.on2.vp9"

        /**
         * List of crashing codecs
         */
        private var bannedYuvCodecs = ArrayList<String>()

        /**
         * List of all codecs discovered in the system.
         */
        private val codecs = ArrayList<CodecInfo>()

        init {

            // Banned H264 encoders/decoders - Crashes
            bannedYuvCodecs.add("OMX.SEC.avc.enc")
            // Don't support 3.1 profile used by Jitsi
            bannedYuvCodecs.add("OMX.Nvidia.h264.decode")
            //bannedYuvCodecs.add("OMX.SEC.avc.dec");

            // Banned VP8 encoders/decoders
            bannedYuvCodecs.add("OMX.SEC.vp8.dec")
            // This one works only for res 176x144
            bannedYuvCodecs.add("OMX.google.vpx.encoder")
            val codecInfos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            for (codecInfo in  codecInfos) {
                // Timber.d("Codec discovered: %s/%s", codecInfo.getName(), Arrays.toString(codecInfo.getSupportedTypes()));
                val ci = getCodecInfo( codecInfo)
                if (ci != null) {
                    codecs.add(ci)
                    ci.isBanned = bannedYuvCodecs.contains(ci.name)
                }
            }
            Timber.i("H264 encoder info: %s", getCodecForType(MEDIA_CODEC_TYPE_H264, true))
            Timber.i("H264 decoder info: %s", getCodecForType(MEDIA_CODEC_TYPE_H264, false))
            Timber.i("VP8 encoder info: %s", getCodecForType(MEDIA_CODEC_TYPE_VP8, true))
            Timber.i("VP8 decoder info: %s", getCodecForType(MEDIA_CODEC_TYPE_VP8, false))
            Timber.i("VP9 encoder info: %s", getCodecForType(MEDIA_CODEC_TYPE_VP9, true))
            Timber.i("VP9 decoder info: %s", getCodecForType(MEDIA_CODEC_TYPE_VP9, false))
        }

        /**
         * Find the codec for given `mimeType`.
         *
         * @param mimeType mime type of the codec.
         * @param isEncoder `true` if encoder should be returned or `false` for decoder.
         * @return the codec for given `mimeType`.
         */
        fun getCodecForType(mimeType: String, isEncoder: Boolean): CodecInfo? {
            for (codec in codecs) {
                if (!codec.isBanned && codec.mediaType == mimeType && codec.codecInfo.isEncoder == isEncoder) {
                    return codec
                }
            }
            return null
        }

        /**
         * Returns the list of detected codecs.
         *
         * @return the list of detected codecs.
         */
        val supportedCodecs: List<CodecInfo>
            get() = Collections.unmodifiableList(codecs)

        private fun getCodecInfo(codecInfo: MediaCodecInfo): CodecInfo? {
            val types = codecInfo.supportedTypes
            for (type in types) {
                try {
                    when (type) {
                        MEDIA_CODEC_TYPE_H264 -> return CodecInfo.H264CodecInfo(codecInfo)
                        MEDIA_CODEC_TYPE_VP8 -> return VP8CodecInfo(codecInfo)
                        MEDIA_CODEC_TYPE_VP9 -> return VP9CodecInfo(codecInfo)
                    }
                } catch (e: IllegalArgumentException) {
                    Timber.e(e, "Error initializing codec info: %s, type: %s", codecInfo.name, type)
                }
            }
            return null
        }
    }
}