/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import org.atalk.impl.neomedia.jmfext.media.renderer.audio.AudioTrackRenderer
import org.atalk.service.neomedia.codec.Constants
import java.io.IOException
import java.io.InputStream
import javax.media.Format
import javax.media.MediaLocator
import javax.media.Renderer
import javax.media.format.AudioFormat

/**
 * Discovers and registers [android.media.AudioRecord] capture devices with FMJ.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class AudioRecordSystem
    /**
     * Initializes a new `AudioRecordSystem` instance which discovers and registers
     * `AudioRecord` capture devices with FMJ.
     *
     * @throws Exception if anything goes wrong while discovering and registering `AudioRecord`
     * capture devices with FMJ
     */
    : AudioSystem(LOCATOR_PROTOCOL_AUDIORECORD, featureSet) {
    override fun createRenderer(playback: Boolean): Renderer {
        return AudioTrackRenderer(playback)
    }

    @Throws(Exception::class)
    override fun doInitialize() {
        val inFormats = ArrayList<Format>()

        for (i in Constants.AUDIO_SAMPLE_RATES.indices) {
            val sampleRate = Constants.AUDIO_SAMPLE_RATES[i]

            // Certain sample rates do not seem to be supported by android.
            // if (sampleRate == 48000.0) continue  // to be allowed: cmeng (20231021)

            inFormats.add(AudioFormat(AudioFormat.LINEAR,
                    sampleRate,
                    16,
                    1,
                    AudioFormat.LITTLE_ENDIAN,
                    AudioFormat.SIGNED,
                    Format.NOT_SPECIFIED /* frameSizeInBits */,
                    Format.NOT_SPECIFIED.toDouble() /* frameRate */,
                    Format.byteArray))
        }
        val formats = inFormats.toTypedArray()

        // Audio capture device
        val captureDevice = CaptureDeviceInfo2(
                "android.media.AudioRecordCapture", MediaLocator("$LOCATOR_PROTOCOL_AUDIORECORD:"),
                formats, null, null, null)

        val captureDevices = ArrayList<CaptureDeviceInfo2>(1)
        captureDevices.add(captureDevice)
        setCaptureDevices(captureDevices)

        // Audio playback (playback, notification) devices
        val playbackDevice = CaptureDeviceInfo2(
                "android.media.AudioRecordPlayback", MediaLocator("$LOCATOR_PROTOCOL_AUDIORECORD:playback"),
                formats, null, null, null)
        val notificationDevice = CaptureDeviceInfo2(
                "android.media.AudioRecordNotification", MediaLocator(LOCATOR_PROTOCOL_AUDIORECORD
                + ":notification"), formats, null, null, null)

        val playbackDevices = ArrayList<CaptureDeviceInfo2>(2)
        playbackDevices.add(playbackDevice)
        playbackDevices.add(notificationDevice)
        setPlaybackDevices(playbackDevices)
        setDevice(DataFlow.NOTIFY, notificationDevice, true)
        setDevice(DataFlow.PLAYBACK, playbackDevice, true)
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
     * Support only Wave format in current implementation.
     *
     * @param audioInputStream the input stream.
     * @return the format of the audio stream.
     */
    override fun getFormat(audioInputStream: InputStream): AudioFormat {
        return AudioStreamUtils.getFormat(audioInputStream)
    }

    companion object {
        /**
         * Returns feature set for current android device;
         * a. capture
         * b. playback
         *
         * @return feature set for current device.
         */
        val featureSet: Int
            get() {
                val featureSet = FEATURE_NOTIFY_AND_PLAYBACK_DEVICES

                if (AcousticEchoCanceler.isAvailable()) {
                    featureSet != FEATURE_ECHO_CANCELLATION
                }
                if (NoiseSuppressor.isAvailable()) {
                    featureSet != FEATURE_DENOISE
                }
                if (AutomaticGainControl.isAvailable()) {
                    featureSet != FEATURE_AGC
                }
                return featureSet
            }
    }
}