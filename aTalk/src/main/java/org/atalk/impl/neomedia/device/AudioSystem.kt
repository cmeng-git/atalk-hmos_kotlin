/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.impl.neomedia.jmfext.media.renderer.audio.AbstractAudioRenderer
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.util.MediaType
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Constructor
import java.net.MalformedURLException
import java.net.URL
import javax.media.MediaLocator
import javax.media.Renderer
import javax.media.format.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.UnsupportedAudioFileException

/**
 * Represents a `DeviceSystem` which provides support for the devices to capture and play
 * back audio (media). Examples include implementations which integrate the native PortAudio,
 * PulseAudio libraries.
 *
 * @author Lyubomir Marinov
 * @author Vincent Lucas
 * @author Timothy Price
 * @author Eng Chong Meng
 */
abstract class AudioSystem protected constructor(
        locatorProtocol: String,
        features: Int = 0,
) : DeviceSystem(MediaType.AUDIO, locatorProtocol, features) {
    /**
     * Enumerates the different types of media data flow of `CaptureDeviceInfo2`s contributed by an `AudioSystem`.
     */
    enum class DataFlow {
        CAPTURE, NOTIFY, PLAYBACK
    }

    /**
     * The list of devices detected by this `AudioSystem` indexed by their category which is
     * among [DataFlow.CAPTURE], [DataFlow.NOTIFY] and [DataFlow.PLAYBACK].
     */
    private var devices: Array<Devices?>? = null

    /**
     * {@inheritDoc}
     *
     * Delegates to [.createRenderer] with the value of the `playback` argument set to true.
     */
    override fun createRenderer(): Renderer? {
        return createRenderer(true)
    }

    /**
     * Initializes a new `Renderer` instance which is to either perform playback on or sound
     * a notification through a device contributed by this system. The (default) implementation of
     * `AudioSystem` ignores the value of the `playback` argument and delegates to
     * [DeviceSystem.createRenderer].
     *
     * @param playback `true` if the new instance is to perform playback or `false` if the new
     * instance is to sound a notification
     * @return a new `Renderer` instance which is to either perform playback on or sound a
     * notification through a device contributed by this system
     */
    open fun createRenderer(playback: Boolean): Renderer? {
        val className = rendererClassName
        var renderer: Renderer?

        if (className == null) {
            /*
             * There is no point in delegating to the super's createRenderer() because it will not
             * have a class to instantiate.
             */
            renderer = null
        }
        else {
            var clazz: Class<*>?
            try {
                clazz = Class.forName(className)
            } catch (t: Throwable) {
                if (t is ThreadDeath) throw t
                else {
                    clazz = null
                    Timber.e(t, "Failed to get class %s", className)
                }
            }
            if (clazz == null) {
                /*
                 * There is no point in delegating to the super's createRenderer() because it will fail to get the class.
                 */
                renderer = null
            }
            else if (!Renderer::class.java.isAssignableFrom(clazz)) {
                /*
                 * There is no point in delegating to the super's createRenderer() because it will
                 * fail to cast the new instance to a Renderer.
                 */
                renderer = null
            }
            else {
                val superCreateRenderer: Boolean
                if (features and FEATURE_NOTIFY_AND_PLAYBACK_DEVICES != 0
                        && AbstractAudioRenderer::class.java.isAssignableFrom(clazz)) {
                    var constructor: Constructor<*>? = null
                    try {
                        constructor = clazz.getConstructor(Boolean::class.javaPrimitiveType)
                    } catch (nsme: NoSuchMethodException) {
                        /*
                         * Such a constructor is optional; so the failure to get it will be allowed,
                         * and the super's createRenderer() will be invoked.
                         */
                    } catch (se: SecurityException) {
                        Timber.e(se, "SecurityException: Failed to initialize %s instance", className)
                    }
                    if (constructor != null) {
                        superCreateRenderer = false
                        try {
                            renderer = constructor.newInstance(playback) as Renderer
                        } catch (t: Throwable) {
                            if (t is ThreadDeath) throw t
                            else {
                                renderer = null
                                Timber.e(t, "Failed to initialize a new %s instance", className)
                            }
                        }
                        if (renderer != null && !playback) {
                            val device = getSelectedDevice(DataFlow.NOTIFY)
                            if (device == null) {
                                /*
                                 * If there is no notification device, then no notification is to be
                                 * sounded.
                                 */
                                renderer = null
                            }
                            else {
                                val locator = device.locator
                                (renderer as AbstractAudioRenderer<*>).setLocator(locator)
                            }
                        }
                    }
                    else {
                        /*
                         * The super's createRenderer() will be invoked because either there is no
                         * non-default constructor, or it is not meant to be invoked by the public.
                         */
                        superCreateRenderer = true
                        renderer = null
                    }
                }
                else {
                    /*
                     * The super's createRenderer() will be invoked because either this AudioSystem
                     * does not distinguish between playback and notify data flows, or the Renderer
                     * implementation class in not familiar.
                     */
                    superCreateRenderer = true
                    renderer = null
                }
                if (superCreateRenderer && renderer == null)
                    renderer = super.createRenderer()
            }
        }
        return renderer!!
    }

    /**
     * Obtains an audio input stream from the URL provided.
     *
     * @param uri a valid uri to a sound resource.
     * @return the input stream to audio data.
     * @throws IOException if an I/O exception occurs
     */
    @Throws(IOException::class)
    open fun getAudioInputStream(uri: String): InputStream? {
        var url = LibJitsi.resourceManagementService.getSoundURLForPath(uri)
        var audioStream: AudioInputStream? = null
        try {
            // Not found by the class loader? Perhaps it is a local file.
            if (url == null)
                url = URL(uri)
            audioStream = javax.sound.sampled.AudioSystem.getAudioInputStream(url)
        } catch (murle: MalformedURLException) {
            // Do nothing, the value of audioStream will remain equal to null.
        } catch (uafe: UnsupportedAudioFileException) {
            Timber.e(uafe, "Unsupported format of audio stream %s", url)
        }
        return audioStream
    }

    /**
     * Gets a `CaptureDeviceInfo2` which has been contributed by this `AudioSystem`,
     * supports a specific flow of media data (i.e. capture, notify or playback) and is identified
     * by a specific `MediaLocator`.
     *
     * @param dataFlow the flow of the media data supported by the `CaptureDeviceInfo2` to be returned
     * @param locator the `MediaLocator` of the `CaptureDeviceInfo2` to be returned
     * @return a `CaptureDeviceInfo2` which has been contributed by this instance, supports
     * the specified `dataFlow` and is identified by the specified `locator`
     */
    fun getDevice(dataFlow: DataFlow, locator: MediaLocator): CaptureDeviceInfo2? {
        return devices!![dataFlow.ordinal]?.getDevice(locator)
    }

    /**
     * Gets the list of devices with a specific data flow: capture, notify or playback.
     *
     * @param dataFlow the data flow of the devices to retrieve: capture, notify or playback
     * @return the list of devices with the specified `dataFlow`
     */
    fun getDevices(dataFlow: DataFlow): List<CaptureDeviceInfo2> {
        return devices!![dataFlow.ordinal]!!.getDevices()
    }

    /**
     * Returns the FMJ format of a specific `InputStream` providing audio media.
     *
     * @param audioInputStream the `InputStream` providing audio media to determine the FMJ format of
     * @return the FMJ format of the specified `audioInputStream` or `null` if such an
     * FMJ format could not be determined
     */
    open fun getFormat(audioInputStream: InputStream): AudioFormat? {
        if (audioInputStream is AudioInputStream) {
            val af = audioInputStream.format
            return AudioFormat(AudioFormat.LINEAR,
                af.sampleRate.toDouble(),
                af.sampleSizeInBits,
                af.channels)
        }
        return null
    }

    /**
     * Gets the (full) name of the `ConfigurationService` property which is associated with a
     * (base) `AudioSystem`-specific property name.
     *
     * @param basePropertyName the (base) `AudioSystem`-specific property name of which the associated (full)
     * `ConfigurationService` property name is to be returned
     * @return the (full) name of the `ConfigurationService` property which is associated
     * with the (base) `AudioSystem` -specific property name
     */
    fun getPropertyName(basePropertyName: String): String {
        return DeviceConfiguration.PROP_AUDIO_SYSTEM + "." + locatorProtocol + "." + basePropertyName
    }

    /**
     * Gets the selected device for a specific data flow: capture, notify or playback.
     *
     * @param dataFlow the data flow of the selected device to retrieve: capture, notify or playback.
     * @return the selected device for the specified `dataFlow`
     */
    fun getSelectedDevice(dataFlow: DataFlow): CaptureDeviceInfo2? {
        return devices!![dataFlow.ordinal]!!.getSelectedDevice(getDevices(dataFlow))
    }

    /**
     * The indicator which determines whether automatic gain control (AGC) is to be performed
     * for captured audio.
     */
    var isAutomaticGainControl: Boolean
        get() {
            val cfg = LibJitsi.configurationService
            var value = features and FEATURE_AGC == FEATURE_AGC
            value = cfg.getBoolean(getPropertyName(PNAME_AGC), value)
            return value
        }
        set(automaticGainControl) {
            val cfg = LibJitsi.configurationService
            cfg.setProperty(getPropertyName(PNAME_AGC), automaticGainControl)
        }

    /**
     * The indicator which determines whether noise suppression is to be performed for captured audio.
     */
    var isDenoise: Boolean
        get() {
            val cfg = LibJitsi.configurationService
            var value = features and FEATURE_DENOISE == FEATURE_DENOISE
            value = cfg.getBoolean(getPropertyName(PNAME_DENOISE), value)
            return value
        }
        set(denoise) {
            val cfg = LibJitsi.configurationService
            cfg.setProperty(getPropertyName(PNAME_DENOISE), denoise)
        }

    /**
     * The indicator which determines whether echo cancellation is to be performed for captured audio.
     */
    var isEchoCancel: Boolean
        get() {
            val cfg = LibJitsi.configurationService
            var value = features and FEATURE_ECHO_CANCELLATION == FEATURE_ECHO_CANCELLATION
            value = cfg.getBoolean(getPropertyName(PNAME_ECHOCANCEL), value)
            return value
        }
        set(echoCancel) {
            val cfg = LibJitsi.configurationService
            cfg.setProperty(getPropertyName(PNAME_ECHOCANCEL), echoCancel)
        }

    /**
     * {@inheritDoc}
     *
     * Because `AudioSystem` may support playback and notification audio devices apart from
     * capture audio devices, fires more specific `PropertyChangeEvent`s than `DeviceSystem`
     */
    @Throws(Exception::class)
    override fun postInitialize() {
        try {
            try {
                postInitializeSpecificDevices(DataFlow.CAPTURE)
            } finally {
                if (FEATURE_NOTIFY_AND_PLAYBACK_DEVICES and features != 0) {
                    try {
                        postInitializeSpecificDevices(DataFlow.NOTIFY)
                    } finally {
                        postInitializeSpecificDevices(DataFlow.PLAYBACK)
                    }
                }
            }
        } finally {
            super.postInitialize()
        }
    }

    /**
     * Sets the device lists after the different audio systems (PortAudio, PulseAudio, etc) have
     * finished detecting their devices.
     *
     * @param dataFlow the data flow of the devices to perform post-initialization on
     */
    private fun postInitializeSpecificDevices(dataFlow: DataFlow) {
        // Gets all current active devices.
        val activeDevices = getDevices(dataFlow)
        // Gets the default device.
        val devices = devices!![dataFlow.ordinal]
        val selectedActiveDevice = devices!!.getSelectedDevice(activeDevices)

        // Sets the default device as selected. The function will fire a
        // property change only if the device has changed
        // from a previous configuration. The "set" part is important because
        // only the fired property event provides a
        // way to get the hotplugged devices working during a call.
        devices.setDevice(selectedActiveDevice, false)
    }

    /**
     * {@inheritDoc}
     *
     * Removes any capture, playback and notification devices previously detected by this
     * `AudioSystem` and prepares it for the execution of its
     * [DeviceSystem.doInitialize] implementation (which detects all devices to be provided
     * by this instance).
     */
    @Throws(Exception::class)
    override fun preInitialize() {
        super.preInitialize()
        if (devices == null) {
            devices = arrayOfNulls(3)
            devices!![DataFlow.CAPTURE.ordinal] = CaptureDevices(this)
            devices!![DataFlow.NOTIFY.ordinal] = NotifyDevices(this)
            devices!![DataFlow.PLAYBACK.ordinal] = PlaybackDevices(this)
        }
    }

    /**
     * Fires a new `PropertyChangeEvent` to the `PropertyChangeListener`s registered
     * with this `PropertyChangeNotifier` in order to notify about a change in the value of a
     * specific property which had its old value modified to a specific new value.
     * `PropertyChangeNotifier` does not check whether the specified `oldValue` and
     * `newValue` are indeed different.
     *
     * @param property the name of the property of this `PropertyChangeNotifier` which had its value
     * changed
     * @param oldValue the value of the property with the specified name before the change
     * @param newValue the value of the property with the specified name after the change
     */
    fun propertyChange(property: String?, oldValue: Any?, newValue: Any?) {
        firePropertyChange(property, oldValue, newValue)
    }

    /**
     * Sets the list of a kind of devices: capture, notify or playback.
     *
     * @param captureDevices The list of a kind of devices: capture, notify or playback.
     */
    protected fun setCaptureDevices(captureDevices: List<CaptureDeviceInfo2>) {
        devices!![DataFlow.CAPTURE.ordinal]!!.setDevices(captureDevices)
    }

    /**
     * Selects the active device.
     *
     * @param dataFlow the data flow of the device to set: capture, notify or playback
     * @param device The selected active device.
     * @param save Flag set to true in order to save this choice in the configuration. False otherwise.
     */
    fun setDevice(dataFlow: DataFlow, device: CaptureDeviceInfo2?, save: Boolean) {
        devices!![dataFlow.ordinal]!!.setDevice(device, save)
    }

    /**
     * Sets the list of the active devices.
     *
     * @param playbackDevices The list of the active devices.
     */
    protected fun setPlaybackDevices(playbackDevices: List<CaptureDeviceInfo2>) {
        devices!![DataFlow.PLAYBACK.ordinal]!!.setDevices(playbackDevices)

        // The notify devices are the same as the playback devices.
        devices!![DataFlow.NOTIFY.ordinal]!!.setDevices(playbackDevices)
    }

    companion object {
        /**
         * The constant/flag (to be) returned by [.getFeatures] in order to indicate that the
         * respective `AudioSystem` supports toggling its automatic gain control (AGC)
         * functionality between on and off. The UI will look for the presence of the flag in order to
         * determine whether a check box is to be shown to the user to enable toggling the automatic
         * gain control (AGC) functionality.
         */
        const val FEATURE_AGC = 1 shl 4

        /**
         * The constant/flag (to be) returned by [.getFeatures] in order to indicate that the
         * respective `AudioSystem` supports toggling its denoise functionality between on and
         * off. The UI will look for the presence of the flag in order to determine whether a check box
         * is to be shown to the user to enable toggling the denoise functionality.
         */
        const val FEATURE_DENOISE = 1 shl 1

        /**
         * The constant/flag (to be) returned by [.getFeatures] in order to indicate that the
         * respective `AudioSystem` supports toggling its echo cancellation functionality between
         * on and off. The UI will look for the presence of the flag in order to determine whether a
         * check box is to be shown to the user to enable toggling the echo cancellation functionality.
         */
        const val FEATURE_ECHO_CANCELLATION = 1 shl 2

        /**
         * The constant/flag (to be) returned by [.getFeatures] in order to indicate that the
         * respective `AudioSystem` differentiates between playback and notification audio
         * devices. The UI, for example, will look for the presence of the flag in order to determine
         * whether separate combo boxes are to be shown to the user to allow the configuration of the
         * preferred playback and notification audio devices.
         */
        const val FEATURE_NOTIFY_AND_PLAYBACK_DEVICES = 1 shl 3

        /**
         * The protocol of the `MediaLocator`s identifying `AudioRecord` capture devices.
         */
        const val LOCATOR_PROTOCOL_AUDIORECORD = "audiorecord"
        const val LOCATOR_PROTOCOL_AUDIOSILENCE = "audiosilence"
        const val LOCATOR_PROTOCOL_JAVASOUND = "javasound"

        /**
         * The protocol of the `MediaLocator`s identifying `CaptureDeviceInfo`s
         * contributed by `MacCoreaudioSystem`.
         */
        const val LOCATOR_PROTOCOL_MACCOREAUDIO = "maccoreaudio"

        /**
         * The protocol of the `MediaLocator`s identifying OpenSL ES capture devices.
         */
        const val LOCATOR_PROTOCOL_OPENSLES = "opensles"
        const val LOCATOR_PROTOCOL_PORTAUDIO = "portaudio"
        const val LOCATOR_PROTOCOL_PULSEAUDIO = "pulseaudio"

        /**
         * The protocol of the `MediaLocator`s identifying `CaptureDeviceInfo`s contributed by `WASAPISystem`.
         */
        const val LOCATOR_PROTOCOL_WASAPI = "wasapi"

        /**
         * The (base) name of the `ConfigurationService` property which indicates whether
         * automatic gain control (AGC) is to be performed for the captured audio.
         */
        private const val PNAME_AGC = "automaticgaincontrol"

        /**
         * The (base) name of the `ConfigurationService` property which indicates whether noise
         * suppression is to be performed for the captured audio.
         */
        protected const val PNAME_DENOISE = "denoise"

        /**
         * The (base) name of the `ConfigurationService` property which indicates whether noise
         * cancellation is to be performed for the captured audio.
         */
        protected const val PNAME_ECHOCANCEL = "echocancel"

        @JvmStatic
        fun getAudioSystem(locatorProtocol: String?): AudioSystem? {
            val audioSystems = getAudioSystems()
            var audioSystemWithLocatorProtocol: AudioSystem? = null

            if (audioSystems != null) {
                for (audioSystem in audioSystems) {
                    if (audioSystem.locatorProtocol.equals(locatorProtocol, ignoreCase = true)) {
                        audioSystemWithLocatorProtocol = audioSystem
                        break
                    }
                }
            }
            return audioSystemWithLocatorProtocol
        }

        @JvmStatic
        fun getAudioSystems(): Array<AudioSystem>? {
            val deviceSystems = getDeviceSystems(MediaType.AUDIO)
            val audioSystems: MutableList<AudioSystem>?

            if (deviceSystems == null) audioSystems = null
            else {
                audioSystems = ArrayList(deviceSystems.size)
                for (deviceSystem in deviceSystems) {
                    if (deviceSystem is AudioSystem)
                        audioSystems.add(deviceSystem)
                }
            }

            return audioSystems?.toTypedArray()
        }
    }
}