/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.impl.neomedia.MediaUtils
import org.atalk.impl.neomedia.control.DiagnosticsControl
import org.atalk.impl.neomedia.jmfext.media.renderer.audio.PortAudioRenderer
import org.atalk.impl.neomedia.portaudio.Pa
import timber.log.Timber
import java.util.*
import javax.media.Format
import javax.media.MediaLocator
import javax.media.format.AudioFormat

/**
 * Creates PortAudio capture devices by enumerating all host devices that have input channels.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
open class PortAudioSystem
/**
 * Initializes a new `PortAudioSystem` instance which creates PortAudio capture and
 * playback devices by enumerating all host devices with input channels.
 *
 * @throws Exception if anything wrong happens while creating the PortAudio capture and playback devices
 */
internal constructor() : AudioSystem2(LOCATOR_PROTOCOL, FEATURE_DENOISE or FEATURE_ECHO_CANCELLATION
        or FEATURE_NOTIFY_AND_PLAYBACK_DEVICES or FEATURE_REINITIALIZE) {
    private var devicesChangedCallback: Runnable? = null

    /**
     * {@inheritDoc}
     */
    @Throws(Exception::class)
    override fun doInitialize() {
        /*
         * If PortAudio fails to initialize because of, for example, a missing native counterpart,
         * it will throw an exception here and the PortAudio Renderer will not be initialized.
         */
        val deviceCount = Pa.GetDeviceCount()
        val channels = 1
        val sampleSizeInBits = 16
        val sampleFormat = Pa.getPaSampleFormat(sampleSizeInBits)
        val defaultInputDeviceIndex = Pa.GetDefaultInputDevice()
        val defaultOutputDeviceIndex = Pa.GetDefaultOutputDevice()

        val captureAndPlaybackDevices = LinkedList<CaptureDeviceInfo2>()
        val captureDevices = LinkedList<CaptureDeviceInfo2>()
        val playbackDevices = LinkedList<CaptureDeviceInfo2>()

        if (CoreAudioDevice.isLoaded) CoreAudioDevice.initDevices()

        for (deviceIndex in 0 until deviceCount) {
            val deviceInfo = Pa.GetDeviceInfo(deviceIndex)
            var name = Pa.DeviceInfo_getName(deviceInfo)

            if (name != null) name = name.trim { it <= ' ' }

            val maxInputChannels = Pa.DeviceInfo_getMaxInputChannels(deviceInfo)
            val maxOutputChannels = Pa.DeviceInfo_getMaxOutputChannels(deviceInfo)
            val transportType = Pa.DeviceInfo_getTransportType(deviceInfo)
            val deviceUID = Pa.DeviceInfo_getDeviceUID(deviceInfo)
            var modelIdentifier: String?
            var locatorRemainder: String

            if (deviceUID == null) {
                modelIdentifier = null
                locatorRemainder = name!!
            } else {
                modelIdentifier = if (CoreAudioDevice.isLoaded) CoreAudioDevice
                        .getDeviceModelIdentifier(deviceUID) else null
                locatorRemainder = deviceUID
            }

            /*
             * TODO The intention of reinitialize() was to perform the initialization from scratch.
             * However, AudioSystem was later changed to disobey. But we should at least search
             * through both CAPTURE_INDEX and PLAYBACK_INDEX.
             */
            val existingCdis = getDevices(DataFlow.CAPTURE)
            var cdi: CaptureDeviceInfo2? = null

            if (existingCdis != null) {
                for (existingCdi in existingCdis) {
                    /*
                     * The deviceUID is optional so a device may be identified by deviceUID if it is
                     * available or by name if the deviceUID is not available.
                     */
                    val id = existingCdi.identifier
                    if (id == deviceUID || id == name) {
                        cdi = existingCdi
                        break
                    }
                }
            }
            if (cdi == null) {
                cdi = CaptureDeviceInfo2(name, MediaLocator(LOCATOR_PROTOCOL + ":#"
                        + locatorRemainder), arrayOf(AudioFormat(
                        AudioFormat.LINEAR,
                        when {
                            maxInputChannels > 0 -> {
                                getSupportedSampleRate(true, deviceIndex, channels, sampleFormat)
                            }
                            else -> Pa.DEFAULT_SAMPLE_RATE
                        },
                        sampleSizeInBits, channels,
                        AudioFormat.LITTLE_ENDIAN, AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED /* frameRate */.toDouble(),
                        Format.byteArray)),
                        deviceUID, transportType, modelIdentifier)
            }

            /*
             * When we perform automatic selection of capture and playback/notify devices, we would
             * like to pick up devices from one and the same hardware because that sound like a
             * natural expectation from the point of view of the user. In order to achieve that, we
             * will bring the devices which support both capture and playback to the top.
             */
            if (maxInputChannels > 0) {
                val devices = if (maxOutputChannels > 0) captureAndPlaybackDevices else captureDevices
                if ((deviceIndex == defaultInputDeviceIndex || maxOutputChannels > 0 && deviceIndex == defaultOutputDeviceIndex)) {
                    devices.add(0, cdi)
                    Timber.d("Added default capture device: %s", name)
                } else {
                    devices.add(cdi)
                    Timber.d("Added capture device: %s", name)
                }
                if (deviceIndex == defaultOutputDeviceIndex) Timber.d("Added default playback device: %s", name) else Timber.d("Added playback device: %s", name)
            } else if (maxOutputChannels > 0) {
                if (deviceIndex == defaultOutputDeviceIndex) {
                    playbackDevices.add(0, cdi)
                    Timber.d("Added default playback device: %s", name)
                } else {
                    playbackDevices.add(cdi)
                    Timber.d("Added playback device: %s", name)
                }
            }
        }
        if (CoreAudioDevice.isLoaded) CoreAudioDevice.freeDevices()

        /*
         * Make sure that devices which support both capture and playback are reported as such and
         * are preferred over devices which support either capture or playback (in order to achieve
         * our goal to have automatic selection pick up devices from one and the same hardware).
         */
        bubbleUpUsbDevices(captureDevices)
        bubbleUpUsbDevices(playbackDevices)
        if (!captureDevices.isEmpty() && !playbackDevices.isEmpty()) {
            /*
             * Event if we have not been provided with the information regarding the matching of the
             * capture and playback/notify devices from one and the same hardware, we may still be
             * able to deduce it by examining their names.
             */
            matchDevicesByName(captureDevices, playbackDevices)
        }
        /*
         * Of course, of highest reliability is the fact that a specific instance supports both
         * capture and playback.
         */
        if (!captureAndPlaybackDevices.isEmpty()) {
            bubbleUpUsbDevices(captureAndPlaybackDevices)
            for (i in captureAndPlaybackDevices.indices.reversed()) {
                val cdi = captureAndPlaybackDevices[i]
                captureDevices.add(0, cdi)
                playbackDevices.add(0, cdi)
            }
        }
        setCaptureDevices(captureDevices)
        setPlaybackDevices(playbackDevices)
        if (devicesChangedCallback == null) {
            devicesChangedCallback = Runnable {
                try {
                    reinitialize()
                } catch (t: Throwable) {
                    if (t is ThreadDeath) throw t
                    Timber.w(t, "Failed to reinitialize PortAudio devices")
                }
            }
            Pa.setDevicesChangedCallback(devicesChangedCallback)
        }
    }

    /**
     * {@inheritDoc}
     */
    override val rendererClassName: String
        get() = PortAudioRenderer::class.java.name

    /**
     * {@inheritDoc}
     *
     * The implementation of `PortAudioSystem` always returns &quot;PortAudio&quot;.
     */
    override fun toString(): String {
        return "PortAudio"
    }

    override fun updateAvailableDeviceList() {
        Pa.UpdateAvailableDeviceList()
    }

    companion object {
        /**
         * The protocol of the `MediaLocator`s identifying PortAudio `CaptureDevice`s.
         */
        private const val LOCATOR_PROTOCOL = LOCATOR_PROTOCOL_PORTAUDIO

        /**
         * Gets a sample rate supported by a PortAudio device with a specific device index with which it
         * is to be registered with JMF.
         *
         * @param input `true` if the supported sample rate is to be retrieved for the PortAudio device
         * with the specified device index as an input device or `false` for an output
         * device
         * @param deviceIndex the device index of the PortAudio device for which a supported sample rate is to be
         * retrieved
         * @param channelCount number of channel
         * @param sampleFormat sample format
         * @return a sample rate supported by the PortAudio device with the specified device index with
         * which it is to be registered with JMF
         */
        private fun getSupportedSampleRate(input: Boolean, deviceIndex: Int, channelCount: Int,
                sampleFormat: Long): Double {
            val deviceInfo = Pa.GetDeviceInfo(deviceIndex)
            val supportedSampleRate: Double
            if (deviceInfo != 0L) {
                val defaultSampleRate = Pa.DeviceInfo_getDefaultSampleRate(deviceInfo)
                if (defaultSampleRate >= MediaUtils.MAX_AUDIO_SAMPLE_RATE) supportedSampleRate = defaultSampleRate else {
                    val streamParameters = Pa.StreamParameters_new(deviceIndex, channelCount,
                            sampleFormat, Pa.LATENCY_UNSPECIFIED)
                    if (streamParameters == 0L) supportedSampleRate = defaultSampleRate else {
                        try {
                            val inputParameters: Long
                            val outputParameters: Long
                            if (input) {
                                inputParameters = streamParameters
                                outputParameters = 0
                            } else {
                                inputParameters = 0
                                outputParameters = streamParameters
                            }
                            val formatIsSupported = Pa.IsFormatSupported(inputParameters,
                                    outputParameters, Pa.DEFAULT_SAMPLE_RATE)
                            supportedSampleRate = if (formatIsSupported) Pa.DEFAULT_SAMPLE_RATE else defaultSampleRate
                        } finally {
                            Pa.StreamParameters_free(streamParameters)
                        }
                    }
                }
            } else supportedSampleRate = Pa.DEFAULT_SAMPLE_RATE
            return supportedSampleRate
        }

        /**
         * Places a specific `DiagnosticsControl` under monitoring of its functional health
         * because of a malfunction in its procedure/process. The monitoring will automatically cease
         * after the procedure/process resumes executing normally or is garbage collected.
         *
         * @param diagnosticsControl the `DiagnosticsControl` to be placed under monitoring of its functional health
         * because of a malfunction in its procedure/process
         */
        fun monitorFunctionalHealth(diagnosticsControl: DiagnosticsControl?) {
            // TODO Auto-generated method stub
        }
    }
}