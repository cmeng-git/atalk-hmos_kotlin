/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.opus

/**
 * Defines the API of the native opus library to be utilized by the libjitsi library.
 *
 * @author Boris Grozev
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
object Opus {
    /**
     * Opus fullBand constant
     */
    const val BANDWIDTH_FULLBAND = 1105

    /**
     * Opus mediumband constant
     */
    const val BANDWIDTH_MEDIUMBAND = 1102

    /**
     * Opus narrowband constant
     */
    const val BANDWIDTH_NARROWBAND = 1101

    /**
     * Opus superwideband constant
     */
    const val BANDWIDTH_SUPERWIDEBAND = 1104

    /**
     * Opus wideband constant
     */
    const val BANDWIDTH_WIDEBAND = 1103

    /**
     * Opus constant for an invalid packet
     */
    const val INVALID_PACKET = -4

    /**
     * The maximum size of a packet we can create. Since we're only creating packets with a single
     * frame, that's a 1 byte TOC + the maximum frame size.
     * See http://tools.ietf.org/html/rfc6716#section-3.2
     */
    const val MAX_PACKET = 1 + 1275

    /**
     * Constant used to set various settings to "automatic"
     */
    const val OPUS_AUTO = -1000

    /**
     * Constant usually indicating that no error occurred
     */
    const val OPUS_OK = 0

    /*
     * Loads the native JNI library.
     */
    init {
        System.loadLibrary("jnopus")
    }

    /**
     * Asserts that the `Opus` class and the JNI library which supports it are functional.
     * The method is to be invoked early (e.g. static/class initializers) by classes which require
     * it (i.e. they depend on it and they cannot function without it).
     */
    fun assertOpusIsFunctional() {
        val channels = 1
        decoder_get_size(channels)
        encoder_get_size(channels)
    }

    /**
     * Decodes an opus packet from `input` into `output`.
     *
     * @param decoder the `OpusDecoder` state to perform the decoding
     * @param input an array of `byte`s which represents the input payload to decode. If
     * `null`, indicates packet loss.
     * @param inputOffset the offset in `input` at which the payload to be decoded begins
     * @param inputLength the length in bytes in `input` beginning at `inputOffset` of the payload
     * to be decoded
     * @param output an array of `byte`s into which the decoded signal is to be output
     * @param outputOffset the offset in `output` at which the output of the decoded signal is to begin
     * @param outputFrameSize the number of samples per channel `output` beginning at `outputOffset`
     * of the maximum space available for output of the decoded signal
     * @param decodeFEC 0 to decode the packet normally, 1 to decode the FEC data in the packet
     * @return the number of decoded samples written into `output` (beginning at `outputOffset`)
     */
    external fun decode(decoder: Long, input: ByteArray?, inputOffset: Int, inputLength: Int,
            output: ByteArray?, outputOffset: Int, outputFrameSize: Int, decodeFEC: Int): Int

    /**
     * Creates an OpusDecoder structure, returns a pointer to it or 0 on error.
     *
     * @param Fs Sample rate to decode to
     * @param channels number of channels to decode to(1/2)
     * @return A pointer to the OpusDecoder structure created, 0 on error.
     */
    external fun decoder_create(Fs: Int, channels: Int): Long

    /**
     * Destroys an OpusDecoder, freeing it's resources.
     *
     * @param decoder Address of the structure (as returned from decoder_create)
     */
    external fun decoder_destroy(decoder: Long)

    /**
     * Returns the number of samples in an opus packet
     *
     * @param decoder The decoder to use.
     * @param packet Array holding the packet.
     * @param offset Offset into packet where the actual packet begins.
     * @param length Length of the packet.
     * @return the number of samples in `packet` .
     */
    external fun decoder_get_nb_samples(decoder: Long, packet: ByteArray?, offset: Int, length: Int): Int

    /**
     * Returns the size in bytes required for an OpusDecoder structure.
     *
     * @param channels number of channels (1/2)
     * @return the size in bytes required for an OpusDecoder structure.
     */
    external fun decoder_get_size(channels: Int): Int

    /**
     * Encodes the input from `input` into an opus packet in `output`.
     *
     * @param encoder The encoder to use.
     * @param input Array containing PCM encoded input.
     * @param inputOffset Offset to use into the `input` array
     * @param inputFrameSize The number of samples per channel in `input`.
     * @param output Array where the encoded packet will be stored.
     * @param outputOffset output offset
     * @param outputLength The number of available bytes in `output`.
     * @return The number of bytes written in `output`, or a negative on error.
     */
    external fun encode(encoder: Long, input: ByteArray?, inputOffset: Int,
            inputFrameSize: Int, output: ByteArray?, outputOffset: Int, outputLength: Int): Int

    /**
     * Creates an OpusEncoder structure, returns a pointer to it casted to long. The native
     * function's `application` parameter is always set to OPUS_APPLICATION_VOIP.
     *
     * @param Fs Sample rate of the input PCM
     * @param channels number of channels in the input (1/2)
     * @return A pointer to the OpusEncoder structure created, 0 on error
     */
    external fun encoder_create(Fs: Int, channels: Int): Long

    /**
     * Destroys an OpusEncoder, freeing it's resources.
     *
     * @param encoder Address of the structure (as returned from encoder_create)
     */
    external fun encoder_destroy(encoder: Long)

    /**
     * Wrapper around the native `opus_encoder_ctl` function. Returns the current encoder audio bandwidth .
     *
     * @param encoder The encoder to use
     * @return the current encoder audio bandwidth
     */
    external fun encoder_get_bandwidth(encoder: Long): Int

    /**
     * Wrapper around the native `opus_encoder_ctl` function. Returns the current encoder bitrate.
     *
     * @param encoder The encoder to use
     * @return The current encoder bitrate.
     */
    external fun encoder_get_bitrate(encoder: Long): Int
    external fun encoder_get_complexity(encoder: Long): Int

    /**
     * Wrapper around the native `opus_encoder_ctl` function. Returns the current DTX setting of the encoder.
     *
     * @param encoder The encoder to use
     * @return the current DTX setting of the encoder.
     */
    external fun encoder_get_dtx(encoder: Long): Int

    /**
     * Wrapper around the native `opus_encoder_ctl` function. Returns the current inband FEC encoder setting.
     *
     * @param encoder The encoder to use
     * @return the current inband FEC encoder setting.
     */
    external fun encoder_get_inband_fec(encoder: Long): Int

    /**
     * Returns the size in bytes required for an OpusEncoder structure.
     *
     * @param channels number of channels (1/2)
     * @return the size in bytes required for an OpusEncoder structure.
     */
    external fun encoder_get_size(channels: Int): Int

    /**
     * Wrapper around the native `opus_encoder_ctl` function. Returns the current encoder VBR setting
     *
     * @param encoder The encoder to use
     * @return The current encoder VBR setting.
     */
    external fun encoder_get_vbr(encoder: Long): Int

    /**
     * Wrapper around the native `opus_encoder_ctl` function. Returns the current VBR
     * constraint encoder setting.
     *
     * @param encoder The encoder to use
     * @return the current VBR constraint encoder setting.
     */
    external fun encoder_get_vbr_constraint(encoder: Long): Int

    /**
     * Wrapper around the native `opus_encoder_ctl` function. Sets the encoder audio bandwidth.
     *
     * @param encoder The encoder to use
     * @param bandwidth The bandwidth to set, should be one of `BANDWIDTH_FULLBAND`,
     * `BANDWIDTH_MEDIUMBAND`, `BANDWIDTH_NARROWBAND`,
     * `BANDWIDTH_SUPERWIDEBAND` or `BANDWIDTH_WIDEBAND`.
     * @return OPUS_OK on success
     */
    external fun encoder_set_bandwidth(encoder: Long, bandwidth: Int): Int

    /**
     * Wrapper around the native `opus_encoder_ctl` function. Sets the encoder bitrate
     *
     * @param encoder The encoder to use
     * @param bitrate The bitrate to set
     * @return `OPUS_OK` on success
     */
    external fun encoder_set_bitrate(encoder: Long, bitrate: Int): Int

    /**
     * Wrapper around the native `opus_encoder_ctl` function. Sets the encoder complexity setting.
     *
     * @param encoder The encoder to use
     * @param complexity The complexity level, from 1 to 10
     * @return OPUS_OK on success
     */
    external fun encoder_set_complexity(encoder: Long, complexity: Int): Int

    /**
     * Wrapper around the native `opus_encoder_ctl` function. Sets the DTX setting of the
     * encoder.
     *
     * @param encoder The encoder to use
     * @param dtx 0 to turn DTX off, non-zero to turn it on
     * @return OPUS_OK on success
     */
    external fun encoder_set_dtx(encoder: Long, dtx: Int): Int

    /**
     * Wrapper around the native `opus_encoder_ctl` function. Sets the force channels setting of the encoder.
     *
     * @param encoder The encoder to use
     * @param forcechannels Number of channels
     * @return OPUS_OK on success
     */
    external fun encoder_set_force_channels(encoder: Long, forcechannels: Int): Int

    /**
     * Wrapper around the native `opus_encoder_ctl` function. Sets the encoder FEC setting.
     *
     * @param encoder The encoder to use
     * @param inbandFEC 0 to turn FEC off, non-zero to turn it on.
     * @return OPUS_OK on success
     */
    external fun encoder_set_inband_fec(encoder: Long, inbandFEC: Int): Int

    /**
     * Wrapper around the native `opus_encoder_ctl` function. Sets the maximum audio
     * bandwidth to be used by the encoder.
     *
     * @param encoder The encoder to use
     * @param maxBandwidth The maximum bandwidth to use, should be one of `BANDWIDTH_FULLBAND`,
     * `BANDWIDTH_MEDIUMBAND`, `BANDWIDTH_NARROWBAND`,
     * `BANDWIDTH_SUPERWIDEBAND` or `BANDWIDTH_WIDEBAND`
     * @return `OPUS_OK` on success.
     */
    external fun encoder_set_max_bandwidth(encoder: Long, maxBandwidth: Int): Int

    /**
     * Wrapper around the native `opus_encoder_ctl` function. Sets the encoder's expected
     * packet loss percentage.
     *
     * @param encoder The encoder to use
     * @param packetLossPerc
     * @return OPUS_OK on success.
     */
    external fun encoder_set_packet_loss_perc(encoder: Long, packetLossPerc: Int): Int

    /**
     * Wrapper around the native `opus_encoder_ctl` function. Sets the encoder VBR setting
     *
     * @param encoder The encoder to use
     * @param vbr 0 to turn VBR off, non-zero to turn it on.
     * @return OPUS_OK on success
     */
    external fun encoder_set_vbr(encoder: Long, vbr: Int): Int

    /**
     * Wrapper around the native `opus_encoder_ctl` function. Sets the encoder VBR constraint setting
     *
     * @param encoder The encoder to use
     * @param use_cvbr 0 to turn VBR constraint off, non-zero to turn it on.
     * @return OPUS_OK on success
     */
    external fun encoder_set_vbr_constraint(encoder: Long, use_cvbr: Int): Int

    /**
     * Returns the audio bandwidth of an Opus packet, one of `BANDWIDTH_FULLBAND`,
     * `BANDWIDTH_MEDIUMBAND`, `BANDWIDTH_NARROWBAND`,
     * `BANDWIDTH_SUPERWIDEBAND` or `BANDWIDTH_WIDEBAND`, or `INVALID_PACKET` on error.
     *
     * @param data Array holding the packet.
     * @param offset Offset into packet where the actual packet begins.
     * @return one of `BANDWIDTH_FULLBAND`, `BANDWIDTH_MEDIUMBAND`,
     * `BANDWIDTH_NARROWBAND`, `BANDWIDTH_SUPERWIDEBAND`,
     * `BANDWIDTH_WIDEBAND`, or `INVALID_PACKET` on error.
     */
    external fun packet_get_bandwidth(data: ByteArray?, offset: Int): Int

    /**
     * Returns the number of channels encoded in an Opus packet.
     *
     * @param data Array holding the packet.
     * @param offset Offset into packet where the actual packet begins.
     * @return the number of channels encoded in `data`.
     */
    external fun packet_get_nb_channels(data: ByteArray?, offset: Int): Int

    /**
     * Returns the number of frames in an Opus packet.
     *
     * @param packet Array holding the packet.
     * @param offset Offset into packet where the actual packet begins.
     * @param length Length of the packet.
     * @return the number of frames in `packet`.
     */
    external fun packet_get_nb_frames(packet: ByteArray?, offset: Int, length: Int): Int
}