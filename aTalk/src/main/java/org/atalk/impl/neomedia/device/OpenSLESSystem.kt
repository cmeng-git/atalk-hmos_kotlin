/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.impl.neomedia.jmfext.media.renderer.audio.OpenSLESRenderer
import org.atalk.service.neomedia.codec.Constants
import java.io.IOException
import java.io.InputStream
import javax.media.Format
import javax.media.MediaLocator
import javax.media.Renderer
import javax.media.format.AudioFormat

/**
 * Discovers and registers OpenSL ES capture devices with FMJ.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class OpenSLESSystem
/**
 * Initializes a new `OpenSLESSystem` instance which discovers and registers OpenSL ES capture devices with FMJ.
 *
 * @throws Exception if anything goes wrong while discovering and registering OpenSL ES capture devices with FMJ
 */
    : AudioSystem(LOCATOR_PROTOCOL_OPENSLES) {

    override fun createRenderer(playback: Boolean): Renderer {
        return OpenSLESRenderer(playback)
    }

    @Throws(Exception::class)
    override fun doInitialize() {
        val formats = ArrayList<Format>()
        val supportedSampleRates = Constants.AUDIO_SAMPLE_RATES

        // cmeng 2018/10/06: queryAudioInputCapabilities not supported by android
        val audioInputCapabilities = queryAudioInputCapabilities(SL_DEFAULTDEVICEID_AUDIOINPUT,
                supportedSampleRates, SUPPORTED_SAMPLE_SIZES_IN_BITS, SUPPORTED_CHANNELS)

        if (audioInputCapabilities != null && audioInputCapabilities.isNotEmpty()) {
            var audioInputCapabilitiesIndex = 0

            while (true) {
                val sampleRateIndex = audioInputCapabilities[audioInputCapabilitiesIndex++]
                val sampleSizeInBitsIndex = audioInputCapabilities[audioInputCapabilitiesIndex++]
                val channelIndex = audioInputCapabilities[audioInputCapabilitiesIndex++]

                if (sampleRateIndex == -1 || sampleSizeInBitsIndex == -1 || channelIndex == -1) break

                val sampleRate = supportedSampleRates[sampleRateIndex]
                val sampleSizeInBits = SUPPORTED_SAMPLE_SIZES_IN_BITS[sampleSizeInBitsIndex]
                val channels = SUPPORTED_CHANNELS[channelIndex]

                formats.add(AudioFormat(
                        AudioFormat.LINEAR,
                        sampleRate,
                        sampleSizeInBits,
                        channels,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED /* frameRate */.toDouble(),
                        Format.byteArray))
            }
        }

        /*
         * In case SLAudioIODeviceCapabilitiesItf is not supported, use a default which is known to work on the tested devices.
         */
        if (formats.isEmpty()) {
            formats.add(AudioFormat(
                    AudioFormat.LINEAR,
                    48000.0,
                    16,
                    1,
                    AudioFormat.LITTLE_ENDIAN,
                    AudioFormat.SIGNED,
                    Format.NOT_SPECIFIED /* frameSizeInBits */,
                    Format.NOT_SPECIFIED /* frameRate */.toDouble(),
                    Format.byteArray))
        }
        if (formats.isNotEmpty()) {
            val captureDevice = CaptureDeviceInfo2("OpenSL ES",
                    MediaLocator("$LOCATOR_PROTOCOL_OPENSLES:"),
                    formats.toTypedArray(), null, null, null)

            val captureDevices = ArrayList<CaptureDeviceInfo2>()
            captureDevices.add(captureDevice)
            setCaptureDevices(captureDevices)
        }
    }

    /**
     * Obtains an audio input stream from the URL provided.
     *
     * @param uri a valid url to a sound resource.
     * @return the input stream to audio data.
     * @throws java.io.IOException if an I/O exception occurs
     */
    @Throws(IOException::class)
    override fun getAudioInputStream(uri: String): InputStream? {
        return AudioStreamUtils.getAudioInputStream(uri)
    }

    /**
     * Returns the audio format for the `InputStream`. Or null if format cannot be obtained.
     *
     * @param audioInputStream the input stream.
     * @return the format of the audio stream.
     */
    override fun getFormat(audioInputStream: InputStream): AudioFormat {
        return AudioStreamUtils.getFormat(audioInputStream)
    }

    private external fun queryAudioInputCapabilities(
            deviceID: Long, sampleRates: DoubleArray,
            sampleSizesInBits: IntArray, channels: IntArray,
    ): IntArray?

    companion object {
        /**
         * The identifier denoting the set of input devices that the implementation receives audio from
         * by default.
         */
        private const val SL_DEFAULTDEVICEID_AUDIOINPUT = 0xFFFFFFFFL

        /**
         * The list of channels to be checked for support by `OpenSLESAuto` in descending order
         * of preference.
         */
        private val SUPPORTED_CHANNELS = intArrayOf(1, 2)

        /**
         * The list of sample sizes in bits to be checked for support by `OpenSLESAuto` in
         * descending order of preference.
         */
        private val SUPPORTED_SAMPLE_SIZES_IN_BITS = intArrayOf(16, 8)

        init {
            System.loadLibrary("jnopensles")
        }

    }
}