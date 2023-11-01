/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.codec

/**
 * Defines constants which are used by both neomedia clients and implementations.
 *
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
object Constants {
    /**
     * The `/rtp` constant. Introduced in order to achieve consistency in the casing of the `String`.
     */
    const val _RTP = "/rtp"

    /**
     * The ALAW/rtp constant.
     */
    const val ALAW_RTP = "ALAW" + _RTP

    /**
     * The AMR-WB constant.
     */
    const val AMR_WB = "AMR-WB"

    /**
     * The AMR-WB/rtp constant.
     */
    const val AMR_WB_RTP = AMR_WB + _RTP

    /**
     * The Android Surface constant. It is used as VideoFormat pseudo encoding in which case the
     * object is passed through the buffers instead of byte array (image) for example.
     */
    const val ANDROID_SURFACE = "android_surface"

    /**
     * The list of well-known sample rates of audio data used throughout neomedia.
     */
    val AUDIO_SAMPLE_RATES = doubleArrayOf(48000.0, 44100.0, 32000.0, 24000.0, 22050.0, 16000.0, 12000.0, 11025.0, 8000.0)

    /**
     * The G722 constant.
     */
    const val G722 = "g722"

    /**
     * The G722/rtp constant.
     */
    const val G722_RTP = G722 + _RTP

    /**
     * The H264 constant.
     */
    const val H264 = "h264"

    /**
     * The H264/rtp constant.
     */
    const val H264_RTP = H264 + _RTP

    /**
     * The ilbc constant.
     */
    const val ILBC = "ilbc"

    /**
     * mode: Frame size for the encoding/decoding
     * 20 - 20 ms
     * 30 - 30 ms
     */
    const val ILBC_MODE = 30

    /**
     * The ilbc/rtp constant.
     */
    const val ILBC_RTP = ILBC + _RTP

    /**
     * The ptime constant.
     */
    const val PTIME = "ptime"

    /**
     * The opus constant.
     */
    const val OPUS = "opus"

    /**
     * The opus/rtp constant.
     */
    const val OPUS_RTP = OPUS + _RTP

    /**
     * The name of the property used to control the Opus encoder "audio bandwidth" setting
     */
    const val PROP_OPUS_BANDWIDTH = "neomedia.codec.audio.opus.encoder.AUDIO_BANDWIDTH"

    /**
     * The name of the property used to control the Opus encoder bitrate setting
     */
    const val PROP_OPUS_BITRATE = "neomedia.codec.audio.opus.encoder.BITRATE"

    /**
     * The name of the property used to control the Opus encoder 'complexity' setting
     */
    const val PROP_OPUS_COMPLEXITY = "neomedia.codec.audio.opus.encoder.COMPLEXITY"

    /**
     * The name of the property used to control the Opus encoder "DTX" setting
     */
    const val PROP_OPUS_DTX = "neomedia.codec.audio.opus.encoder.DTX"

    /**
     * The name of the property used to control whether FEC is enabled for the Opus encoder
     */
    const val PROP_OPUS_FEC = "neomedia.codec.audio.opus.encoder.FEC"

    /**
     * The name of the property used to control the Opus encoder "minimum expected packet loss" setting
     */
    const val PROP_OPUS_MIN_EXPECTED_PACKET_LOSS = "neomedia.codec.audio.opus.encoder.MIN_EXPECTED_PACKET_LOSS"

    /**
     * The name of the property used to control whether VBR is enabled for the Opus encoder
     */
    const val PROP_OPUS_VBR = "neomedia.codec.audio.opus.encoder.VBR"

    /**
     * The name of the property used to control whether FEC support is advertised for SILK
     */
    const val PROP_SILK_ADVERSISE_FEC = "neomedia.codec.audio.silk.ADVERTISE_FEC"

    /**
     * The name of the property used to control the the 'always assume packet loss' setting for SILK
     */
    const val PROP_SILK_ASSUME_PL = "neomedia.codec.audio.silk.encoder.AWLAYS_ASSUME_PACKET_LOSS"

    /**
     * The name of the property used to control whether FEC is enabled for SILK
     */
    const val PROP_SILK_FEC = "neomedia.codec.audio.silk.encoder.USE_FEC"

    /**
     * The name of the property used to control the SILK 'speech activity threshold'
     */
    const val PROP_SILK_FEC_SAT = "neomedia.codec.audio.silk.encoder.SPEECH_ACTIVITY_THRESHOLD"

    /**
     * The name of the property used to control the G729 encoder "VAD" setting
     * G.729b provides a silence compression method that enables a voice activity detection (VAD) module.
     * It is used to detect voice activity in the signal. It also includes a discontinuous transmission (DTX) module
     * which decides on updating the background noise parameters for non speech (noisy frames).
     *
     * VAD enanbleVAD : flag set to 1: VAD/DTX is enabled
     */
    const val PROP_G729_VAD = "neomedia.codec.audio.g729.encoder.VAD"

    /**
     * The name of the RED RTP format (RFC2198)
     */
    const val RED = "red"

    /**
     * The SILK constant.
     */
    const val SILK = "SILK"

    /**
     * The SILK/rtp constant.
     */
    const val SILK_RTP = SILK + _RTP

    /**
     * The SPEEX constant.
     */
    const val SPEEX = "speex"

    /**
     * The SPEEX/RTP constant.
     */
    const val SPEEX_RTP = SPEEX + _RTP

    /**
     * Pseudo format representing DTMF tones sent over RTP.
     */
    const val TELEPHONE_EVENT = "telephone-event"

    /**
     * The name of the ulpfec RTP format (RFC5109)
     */
    const val ULPFEC = "ulpfec"

    /**
     * The name of the flexfec-03 rtp format
     */
    const val FLEXFEC_03 = "flexfec-03"

    /**
     * The VP8 constant
     */
    const val VP8 = "VP8"

    /**
     * The VP9 constant
     */
    const val VP9 = "VP9"

    /**
     * The RTX constant
     */
    const val RTX = "rtx"

    /**
     * The VP9/rtp constant.
     */
    const val VP9_RTP = VP9 + _RTP

    /**
     * The VP8/rtp constant.
     */
    const val VP8_RTP = VP8 + _RTP

    /**
     * The RTX/rtp constant.
     */
    const val RTX_RTP = RTX + _RTP
}