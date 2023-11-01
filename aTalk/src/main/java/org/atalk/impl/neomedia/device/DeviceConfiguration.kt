/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.MediaServiceImpl
import org.atalk.impl.neomedia.codec.video.AVFrameFormat
import org.atalk.impl.neomedia.device.AudioSystem.Companion.getAudioSystem
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.MediaUseCase
import org.atalk.service.neomedia.codec.Constants
import org.atalk.util.MediaType
import org.atalk.util.OSUtils
import org.atalk.util.event.PropertyChangeNotifier
import timber.log.Timber
import java.awt.Dimension
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.IOException
import java.util.*
import javax.media.CaptureDeviceInfo
import javax.media.CaptureDeviceManager
import javax.media.Format
import javax.media.PlugInManager
import javax.media.Renderer
import javax.media.format.VideoFormat

/**
 * This class aims to provide a simple configuration interface for JMF. It retrieves stored
 * configuration when started or listens to ConfigurationEvent for property changes and configures
 * the JMF accordingly.
 *
 * @author Martin Andre
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Vincent Lucas
 * @author Eng Chong Meng
 */
class DeviceConfiguration : PropertyChangeNotifier(), PropertyChangeListener {
    /**
     * The currently selected audio system.
     */
    var audioSystem: AudioSystem? = null
        private set

    /**
     * The frame rate.
     */
    private var frameRate = DEFAULT_VIDEO_FRAMERATE

    /**
     * The value of the `ConfigurationService` property
     * [MediaServiceImpl.DISABLE_SET_AUDIO_SYSTEM_PNAME] at the time of the initialization of this instance.
     */
    private val setAudioSystemIsDisabled: Boolean

    /**
     * Current setting for video codec bitrate.
     */
    var videoBitrate = -1
        get() {
            if (field == -1) {
                val cfg = LibJitsi.configurationService
                val value = cfg.getInt(PROP_VIDEO_BITRATE, DEFAULT_VIDEO_BITRATE)
                field = if (value > 0)
                    value
                else DEFAULT_VIDEO_BITRATE
            }
            return field
        }
        set(videoBitrate) {
            field = videoBitrate
            val cfg = LibJitsi.configurationService
            if (videoBitrate != DEFAULT_VIDEO_BITRATE)
                cfg.setProperty(PROP_VIDEO_BITRATE, videoBitrate)
            else
                cfg.removeProperty(PROP_VIDEO_BITRATE)
        }

    /**
     * The device that we'll be using for video capture.
     */
    private var videoCaptureDevice: CaptureDeviceInfo? = null

    /**
     * Current setting for video maximum bandwidth.
     */
    private var videoMaxBandwidth = -1

    /**
     * The current resolution settings.
     */
    private var videoSize: Dimension? = null

    /**
     * Initializes a new `DeviceConfiguration` instance.
     */
    init {
        val cfg = LibJitsi.configurationService
        setAudioSystemIsDisabled = (cfg.getBoolean(MediaServiceImpl.DISABLE_SET_AUDIO_SYSTEM_PNAME, false))

        // Seem to be throwing exceptions randomly, so we'll blindly catch them for now
        try {
            DeviceSystem.initializeDeviceSystems()
            extractConfiguredCaptureDevices()
        } catch (ex: Exception) {
            Timber.e(ex, "Failed to initialize media.")
        }

        cfg.addPropertyChangeListener(PROP_VIDEO_WIDTH, this)
        cfg.addPropertyChangeListener(PROP_VIDEO_HEIGHT, this)
        cfg.addPropertyChangeListener(PROP_VIDEO_FRAMERATE, this)
        cfg.addPropertyChangeListener(PROP_VIDEO_RTP_PACING_THRESHOLD, this)

        registerCustomRenderers()
        fixRenderers()

        /*
         * Adds this instance as a PropertyChangeListener to all DeviceSystems which support
         * reinitialization/reloading in order to be able, for example, to switch from a
         * default/automatic selection of "None" to a DeviceSystem which has started providing at
         * least one device at runtime.
         */
        addDeviceSystemPropertyChangeListener()
    }

    /**
     * Adds this instance as a `PropertyChangeListener` to all `DeviceSystem`s which
     * support reinitialization/reloading in order to be able, for example, to switch from a
     * default/automatic selection of &quot;None&quot; to an `DeviceSystem` which has started
     * providing at least one device at runtime.
     */
    private fun addDeviceSystemPropertyChangeListener() {
        // Track all kinds of DeviceSystems i.e audio and video.
        for (mediaType in MediaType.values()) {
            val deviceSystems = DeviceSystem.getDeviceSystems(mediaType)

            if (deviceSystems.isNotEmpty()) {
                for (deviceSystem in deviceSystems) {
                    // It only makes sense to track DeviceSystems which support reinitialization/reloading.
                    if (deviceSystem!!.features and DeviceSystem.FEATURE_REINITIALIZE != 0) {
                        deviceSystem.addPropertyChangeListener(this)
                    }
                }
            }
        }
    }

    /**
     * Detects audio capture devices configured through JMF and disable audio if none was found.
     */
    private fun extractConfiguredAudioCaptureDevices() {
        if (!MediaServiceImpl.isMediaTypeSupportEnabled(MediaType.AUDIO))
            return

        Timber.i("Looking for configured audio devices.")
        val availableAudioSystems = availableAudioSystems

        if (availableAudioSystems != null && availableAudioSystems.isNotEmpty()) {
            var audioSystem = audioSystem
            if (audioSystem != null) {
                val audioSystemIsAvailable = setAudioSystemIsDisabled

                /*
                 * XXX Presently, the method is used in execution paths which require the user's
                 * selection (i.e. the value of the associated ConfigurationService property) to be
                 * respected or execute too early in the life of the library/application to
                 * necessitate the preservation of the audioSystem value.
                 */
                // for (AudioSystem availableAudioSystem : availableAudioSystems) {
                //     if (!NoneAudioSystem.LOCATOR_PROTOCOL.equals(
                //           availableAudioSystem.getLocatorProtocol())
                //            && availableAudioSystem.equals(audioSystem)) {
                //        audioSystemIsAvailable = true;
                //        break;
                //    }
                // }
                if (!audioSystemIsAvailable)
                    audioSystem = null
            }

            if (audioSystem == null) {
                val cfg = LibJitsi.configurationService

                val locatorProtocol = cfg.getString(PROP_AUDIO_SYSTEM)
                if (locatorProtocol != null) {
                    for (availableAudioSystem in availableAudioSystems) {
                        if (locatorProtocol.equals(availableAudioSystem.locatorProtocol, ignoreCase = true)) {
                            audioSystem = availableAudioSystem
                            break
                        }
                    }
                    /*
                     * If the user is not presented with any user interface which allows the
                     * selection of a particular AudioSystem, always use the configured
                     * AudioSystem regardless of whether it is available.
                     */
                    if (setAudioSystemIsDisabled && audioSystem == null) {
                        audioSystem = getAudioSystem(locatorProtocol)
                    }
                }

                if (audioSystem == null)
                    audioSystem = availableAudioSystems[0]
                setAudioSystem(audioSystem, false)
            }
        }
    }

    /**
     * Detects capture devices configured through JMF and disable audio and/or video transmission if none were found.
     */
    private fun extractConfiguredCaptureDevices() {
        extractConfiguredAudioCaptureDevices()
        extractConfiguredVideoCaptureDevices()
    }

    /**
     * Returns the configured video capture device with the specified output format.
     *
     * @param formats the output format array of the video format.
     * @return CaptureDeviceInfo for the video device.
     */
    private fun extractConfiguredVideoCaptureDevice(formats: Array<Format>): CaptureDeviceInfo? {
        val cfg = LibJitsi.configurationService
        val videoDevName = cfg.getString(PROP_VIDEO_DEVICE)
        var videoCaptureDevice: CaptureDeviceInfo? = null
        var tmpDevice: CaptureDeviceInfo? = null

        for (format in formats) {
            val videoCaptureDevices = CaptureDeviceManager.getDeviceList(format) as List<CaptureDeviceInfo>?
            if (videoCaptureDevices != null && videoCaptureDevices.isNotEmpty()) {
                if (videoDevName == null) {
                    tmpDevice = videoCaptureDevices[0]
                    break
                }
                else {
                    for (captureDeviceInfo in videoCaptureDevices) {
                        if (videoDevName == captureDeviceInfo.name) {
                            videoCaptureDevice = captureDeviceInfo
                            Timber.i("Found video capture device: '%s'; format: '%s'", videoCaptureDevice.name, format)
                            break
                        }
                    }
                }
                tmpDevice = videoCaptureDevices[0]
            }
        }

        if (videoCaptureDevice == null && tmpDevice != null) {
            // incorrect value specified in DB, so force and save to use last found valid videoCaptureDevice
            videoCaptureDevice = tmpDevice
            cfg.setProperty(PROP_VIDEO_DEVICE, videoCaptureDevice.name)
        }
        return videoCaptureDevice
    }

    /**
     * Detects video capture devices configured through JMF and disable video if none was found.
     */
    private fun extractConfiguredVideoCaptureDevices() {
        if (!MediaServiceImpl.isMediaTypeSupportEnabled(MediaType.VIDEO))
            return

        val videoDevName = LibJitsi.configurationService.getString(PROP_VIDEO_DEVICE)
        if (NoneAudioSystem.LOCATOR_PROTOCOL.equals(videoDevName, ignoreCase = true)) {
            videoCaptureDevice = null
        }
        else {
            val formats = arrayOf<Format>(
                AVFrameFormat(),
                VideoFormat(Constants.ANDROID_SURFACE),
                VideoFormat(VideoFormat.RGB),
                VideoFormat(VideoFormat.YUV),
                VideoFormat(Constants.H264)
            )

            videoCaptureDevice = extractConfiguredVideoCaptureDevice(formats)
            if (videoCaptureDevice != null)
                Timber.i("Found configured video device: %s <= %s.", videoDevName, videoCaptureDevice!!.name)
            else
                Timber.w("No Video Device was found for %s.", videoDevName)
        }
    }

    /**
     * Returns a device that we could use for audio capture.
     *
     * @return the CaptureDeviceInfo of a device that we could use for audio capture.
     */
    val audioCaptureDevice: CaptureDeviceInfo2?
        get() {
            val audioSystem = audioSystem
            return audioSystem?.getSelectedDevice(AudioSystem.DataFlow.CAPTURE)
        }

    /**
     * @return the audioNotifyDevice
     */
    val audioNotifyDevice: CaptureDeviceInfo?
        get() {
            val audioSystem = audioSystem
            return audioSystem?.getSelectedDevice(AudioSystem.DataFlow.NOTIFY)
        }

    /**
     * Gets the list of audio capture devices which are available through this
     * `DeviceConfiguration`, amongst which is [.getAudioCaptureDevice] and represent
     * acceptable values for [//#setAudioCaptureDevice(CaptureDeviceInfo, boolean)][//.setAudioCaptureDevice]
     *
     * @return an array of `CaptureDeviceInfo` describing the audio capture devices available
     * through this `DeviceConfiguration`
     */
    val availableAudioCaptureDevices: MutableList<CaptureDeviceInfo2>
        get() = audioSystem!!.getDevices(AudioSystem.DataFlow.CAPTURE) as MutableList<CaptureDeviceInfo2>

    /**
     * Returns a list of available `AudioSystem`s. By default, an `AudioSystem` is
     * considered available if it reports at least one device.
     *
     * @return an array of available `AudioSystem`s
     */
    private val availableAudioSystems: Array<AudioSystem>?
        get() {
            val audioSystems = AudioSystem.getAudioSystems()
            return if (audioSystems == null || audioSystems.isEmpty())
                audioSystems
            else {
                val audioSystemsWithDevices = ArrayList<AudioSystem>()
                for (audioSystem in audioSystems) {
                    if (!NoneAudioSystem.LOCATOR_PROTOCOL.equals(audioSystem.locatorProtocol, ignoreCase = true)) {
                        val captureDevices = audioSystem.getDevices(AudioSystem.DataFlow.CAPTURE)

                        if (captureDevices.isEmpty()) {
                            if (AudioSystem.FEATURE_NOTIFY_AND_PLAYBACK_DEVICES and audioSystem.features == 0) {
                                continue
                            }
                            else {
                                val notifyDevices = audioSystem.getDevices(AudioSystem.DataFlow.NOTIFY)
                                if (notifyDevices.isEmpty()) {
                                    val playbackDevices = audioSystem.getDevices(AudioSystem.DataFlow.PLAYBACK)
                                    if (playbackDevices.isEmpty()) {
                                        continue
                                    }
                                }
                            }
                        }
                    }
                    audioSystemsWithDevices.add(audioSystem)
                }

                val audioSystemsWithDevicesCount = audioSystemsWithDevices.size
                if (audioSystemsWithDevicesCount == audioSystems.size)
                    audioSystems
                else
                    audioSystemsWithDevices.toTypedArray()
            }
        }

    /**
     * Gets the list of video capture devices which are available through this
     * `DeviceConfiguration`, amongst which is [.getVideoCaptureDevice]
     * and represent acceptable values for [.setVideoCaptureDevice]
     *
     * @param useCase extract video capture devices that correspond to this `MediaUseCase`
     * @return an array of `CaptureDeviceInfo` describing the video capture devices available
     * through this `DeviceConfiguration`
     */
    fun getAvailableVideoCaptureDevices(useCase: MediaUseCase): MutableList<CaptureDeviceInfo> {
        val formats = arrayOf<Format>(
            AVFrameFormat(),
            VideoFormat(Constants.ANDROID_SURFACE),
            VideoFormat(VideoFormat.RGB),
            VideoFormat(VideoFormat.YUV),
            VideoFormat(Constants.H264)
        )

        val videoCaptureDevices = HashSet<CaptureDeviceInfo>()
        for (format in formats) {
            val cdis = CaptureDeviceManager.getDeviceList(format) as Vector<CaptureDeviceInfo>
            if (useCase != MediaUseCase.ANY) {
                for (cdi in cdis) {
                    val cdiUseCase = if (DeviceSystem.LOCATOR_PROTOCOL_IMGSTREAMING.equals(cdi.locator.protocol, ignoreCase = true))
                        MediaUseCase.DESKTOP
                    else
                        MediaUseCase.CALL
                    if (cdiUseCase == useCase) videoCaptureDevices.add(cdi)
                }
            }
            else {
                videoCaptureDevices.addAll(cdis)
            }
        }
        return ArrayList(videoCaptureDevices)
    }

    /**
     * Get the echo cancellation filter length (in milliseconds).
     *
     * @return echo cancel filter length in milliseconds
     */
    val echoCancelFilterLengthInMillis: Long
        get() {
            val cfg = LibJitsi.configurationService
            return cfg.getLong(PROP_AUDIO_ECHOCANCEL_FILTER_LENGTH_IN_MILLIS,
                DEFAULT_AUDIO_ECHOCANCEL_FILTER_LENGTH_IN_MILLIS)
        }

    /**
     * Gets the frame rate set on this `DeviceConfiguration`.
     *
     * @return the frame rate set on this `DeviceConfiguration`. The default value is [.DEFAULT_VIDEO_FRAMERATE]
     */
    fun getFrameRate(): Int {
        if (frameRate == -1) {
            val cfg = LibJitsi.configurationService
            frameRate = cfg.getInt(PROP_VIDEO_FRAMERATE, DEFAULT_VIDEO_FRAMERATE)
        }
        return frameRate
    }

    /**
     * Returns a device that we could use for video capture.
     *
     * @param useCase `MediaUseCase` that will determined device we will use
     * @return the CaptureDeviceInfo of a device that we could use for video capture.
     */
    fun getVideoCaptureDevice(useCase: MediaUseCase?): CaptureDeviceInfo? {
        var dev: CaptureDeviceInfo? = null
        when (useCase) {
            MediaUseCase.ANY, MediaUseCase.CALL -> dev = videoCaptureDevice
            MediaUseCase.DESKTOP -> {
                val devs = getAvailableVideoCaptureDevices(MediaUseCase.DESKTOP)
                if (devs.isNotEmpty()) dev = devs[0]
            }
            else -> {}
        }
        return dev
    }
    /**
     * Gets the maximum allowed video bandwidth.
     *
     * @return the maximum allowed video bandwidth. The default value is [.DEFAULT_VIDEO_RTP_PACING_THRESHOLD].
     */
    /**
     * Sets and stores the maximum allowed video bandwidth.
     *
     * videoMaxBandwidth the maximum allowed video bandwidth
     */
    var videoRTPPacingThreshold: Int
        get() {
            if (videoMaxBandwidth == -1) {
                val cfg = LibJitsi.configurationService
                val value = cfg.getInt(PROP_VIDEO_RTP_PACING_THRESHOLD, DEFAULT_VIDEO_RTP_PACING_THRESHOLD)

                videoMaxBandwidth = if (value > 0)
                    value
                else
                    DEFAULT_VIDEO_RTP_PACING_THRESHOLD
            }
            return videoMaxBandwidth
        }
        set(videoMaxBandwidth) {
            this.videoMaxBandwidth = videoMaxBandwidth
            val cfg = LibJitsi.configurationService

            if (videoMaxBandwidth != DEFAULT_VIDEO_RTP_PACING_THRESHOLD) {
                cfg.setProperty(PROP_VIDEO_RTP_PACING_THRESHOLD, videoMaxBandwidth)
            }
            else
                cfg.removeProperty(PROP_VIDEO_RTP_PACING_THRESHOLD)
        }

    /**
     * Gets the video size set on this `DeviceConfiguration`.
     *
     * @return the video size set on this `DeviceConfiguration`
     */
    fun getVideoSize(): Dimension {
        if (videoSize == null) {
            val cfg = LibJitsi.configurationService
            val width = cfg.getInt(PROP_VIDEO_WIDTH, DEFAULT_VIDEO_WIDTH)
            val height = cfg.getInt(PROP_VIDEO_HEIGHT, DEFAULT_VIDEO_HEIGHT)
            videoSize = Dimension(width, height)
        }
        return videoSize!!
    }

    /**
     * Notifies this `PropertyChangeListener` about `PropertyChangeEvent`s fired by,
     * for example, the `ConfigurationService` and the `DeviceSystem`s which support
     * reinitialization/reloading.
     *
     * @param ev the `PropertyChangeEvent` to notify this `PropertyChangeListener` about
     * and which describes the source and other specifics of the notification
     */
    override fun propertyChange(ev: PropertyChangeEvent) {
        val propertyName = ev.propertyName
        if (AUDIO_CAPTURE_DEVICE == propertyName
                || AUDIO_NOTIFY_DEVICE == propertyName
                || AUDIO_PLAYBACK_DEVICE == propertyName) {
            /*
             * The current audioSystem may represent a default/automatic selection which may have
             * been selected because the user's selection may have been unavailable at the time.
             * Make sure that the user's selection is respected if possible.
             */
            extractConfiguredAudioCaptureDevices()

            /*
             * The specified PropertyChangeEvent has been fired by a DeviceSystem i.e. a certain
             * DeviceSystem is the source. Translate it to a PropertyChangeEvent fired by this
             * instance.
             */
            val audioSystem = audioSystem
            if (audioSystem != null) {
                val oldValue = ev.oldValue as CaptureDeviceInfo?
                val newValue = ev.newValue as CaptureDeviceInfo
                val device = oldValue ?: newValue

                // Fire an event on the selected device only if the event is
                // generated by the selected audio system.
                if (device.locator.protocol == audioSystem.locatorProtocol) {
                    firePropertyChange(propertyName, oldValue, newValue)
                }
            }
        }
        else if (DeviceSystem.PROP_DEVICES == propertyName) {
            if (ev.source is AudioSystem) {
                /*
                 * The current audioSystem may represent a default/automatic selection which may
                 * have been selected because the user's selection may have been unavailable at the
                 * time. Make sure that the user's selection is respected if possible.
                 */
                extractConfiguredAudioCaptureDevices()
                val newValue = ev.newValue as List<CaptureDeviceInfo>
                firePropertyChange(PROP_AUDIO_SYSTEM_DEVICES, ev.oldValue, newValue)
            }
        }
        else if (PROP_VIDEO_FRAMERATE == propertyName) {
            frameRate = -1
        }
        else if (PROP_VIDEO_HEIGHT == propertyName || PROP_VIDEO_WIDTH == propertyName) {
            videoSize = null
        }
        else if (PROP_VIDEO_RTP_PACING_THRESHOLD == propertyName) {
            videoMaxBandwidth = -1
        }
    }

    /**
     * Registers the custom `Renderer` implementations defined by class name in
     * [.CUSTOM_RENDERERS] with JMF.
     */
    private fun registerCustomRenderers() {
        val renderers = PlugInManager.getPlugInList(null, null, PlugInManager.RENDERER) as Vector<String>?
        val audioSupportIsDisabled = !MediaServiceImpl.isMediaTypeSupportEnabled(MediaType.AUDIO)
        val videoSupportIsDisabled = !MediaServiceImpl.isMediaTypeSupportEnabled(MediaType.VIDEO)
        var commit = false

        for (customRenderer in CUSTOM_RENDERERS) {
            var customRenderer = customRenderer ?: continue
            if (customRenderer.startsWith(".")) {
                customRenderer = "org.atalk.impl.neomedia.jmfext.media.renderer$customRenderer"
            }

            /*
             * Respect the MediaServiceImpl properties DISABLE_AUDIO_SUPPORT_PNAME and DISABLE_VIDEO_SUPPORT_PNAME.
             */
            if (audioSupportIsDisabled && customRenderer.contains(".audio."))
                continue
            if (videoSupportIsDisabled && customRenderer.contains(".video."))
                continue

            if (renderers == null || !renderers.contains(customRenderer)) {
                try {
                    val customRendererInstance = Class.forName(customRenderer).newInstance() as Renderer
                    PlugInManager.addPlugIn(customRenderer, customRendererInstance.supportedInputFormats,
                        null, PlugInManager.RENDERER)
                    commit = true
                } catch (t: Throwable) {
                    Timber.e(t, "Failed to register custom Renderer %s with JMF.", customRenderer)
                }
            }
        }

        /*
         * Just in case, bubble our JMF contributions at the top so that they are considered preferred.
         */
        val pluginType = PlugInManager.RENDERER
        val plugins = PlugInManager.getPlugInList(null, null, pluginType) as Vector<String>?

        if (plugins != null) {
            val pluginCount = plugins.size
            var pluginBeginIndex = 0

            var pluginIndex = pluginCount - 1
            while (pluginIndex >= pluginBeginIndex) {
                val plugin = plugins[pluginIndex]
                if (plugin.startsWith("org.atalk.")
                        || plugin.startsWith("net.java.sip.communicator.")) {
                    plugins.removeAt(pluginIndex)
                    plugins.add(0, plugin)
                    pluginBeginIndex++
                    commit = true
                }
                else
                    pluginIndex--
            }
            PlugInManager.setPlugInList(plugins, pluginType)
            Timber.log(TimberLog.FINER, "Reordered plug-in list:%s", plugins)
        }

        if (commit && !MediaServiceImpl.isJmfRegistryDisableLoad) {
            try {
                PlugInManager.commit()
            } catch (ioex: IOException) {
                Timber.w("Failed to commit changes to the JMF plug-in list.")
            }
        }
    }

    fun setAudioSystem(audioSystem: AudioSystem?, save: Boolean) {
        if (this.audioSystem != audioSystem) {
            check(!(setAudioSystemIsDisabled && save)) {
                MediaServiceImpl.DISABLE_SET_AUDIO_SYSTEM_PNAME
            }

            // Removes the registration to change listener only if this audio system does not support reinitialize.
            if (this.audioSystem != null
                    && this.audioSystem!!.features and DeviceSystem.FEATURE_REINITIALIZE == 0) {
                this.audioSystem!!.removePropertyChangeListener(this)
            }

            val oldValue = this.audioSystem
            this.audioSystem = audioSystem

            // Registers the new selected audio system. Even if every
            // FEATURE_REINITIALIZE audio system is registered already, the
            // check for duplicate entries will be done by the
            // addPropertyChangeListener method.
            if (this.audioSystem != null)
                this.audioSystem!!.addPropertyChangeListener(this)

            if (save) {
                val cfg = LibJitsi.configurationService
                if (this.audioSystem == null)
                    cfg.removeProperty(PROP_AUDIO_SYSTEM)
                else
                    cfg.setProperty(PROP_AUDIO_SYSTEM, this.audioSystem!!.locatorProtocol)
            }
            firePropertyChange(PROP_AUDIO_SYSTEM, oldValue, this.audioSystem)
        }
    }

    /**
     * Sets and stores the frame rate.
     *
     * @param frameRate the frame rate to be set on this `DeviceConfiguration`
     */
    fun setFrameRate(frameRate: Int) {
        this.frameRate = frameRate
        val cfg = LibJitsi.configurationService
        if (frameRate != DEFAULT_VIDEO_FRAMERATE)
            cfg.setProperty(PROP_VIDEO_FRAMERATE, frameRate)
        else
            cfg.removeProperty(PROP_VIDEO_FRAMERATE)
    }

    /**
     * Sets the device which is to be used by this `DeviceConfiguration` for video capture.
     *
     * @param device a `CaptureDeviceInfo` describing device to be used by this
     * `DeviceConfiguration` for video capture.
     * @param save whether we will save this option or not.
     */
    fun setVideoCaptureDevice(device: CaptureDeviceInfo, save: Boolean) {
        if (videoCaptureDevice != device) {
            val oldDevice = videoCaptureDevice
            videoCaptureDevice = device

            if (save) {
                LibJitsi.configurationService.setProperty(PROP_VIDEO_DEVICE,
                    if (videoCaptureDevice == null)
                        NoneAudioSystem.LOCATOR_PROTOCOL
                    else videoCaptureDevice!!.name)
            }

            // cmeng (20210402), new implementation to switch camera without involving jingle message sending
            // Does not require blocking after the latest change in MediaStreamImpl@setDevice()
            firePropertyChange(VIDEO_CAPTURE_DEVICE, oldDevice, device)
        }
    }

    /**
     * Sets and stores the video size selected by user
     *
     * @param videoSize the video size to be set on this `DeviceConfiguration`
     */
    fun setVideoSize(videoSize: Dimension) {
        val cfg = LibJitsi.configurationService
        cfg.setProperty(PROP_VIDEO_HEIGHT, videoSize.height)
        cfg.setProperty(PROP_VIDEO_WIDTH, videoSize.width)
        this.videoSize = videoSize
        firePropertyChange(VIDEO_CAPTURE_DEVICE, videoCaptureDevice, videoCaptureDevice)
    }

    companion object {
        /**
         * The name of the `DeviceConfiguration` property which represents the device used by
         * `DeviceConfiguration` for audio capture.
         */
        const val AUDIO_CAPTURE_DEVICE = CaptureDevices.PROP_DEVICE

        /**
         * The name of the `DeviceConfiguration` property which represents the device used by
         * `DeviceConfiguration` for audio notify.
         */
        const val AUDIO_NOTIFY_DEVICE = NotifyDevices.PROP_DEVICE

        /**
         * The name of the `DeviceConfiguration` property which represents the device used by
         * `DeviceConfiguration` for audio playback.
         */
        const val AUDIO_PLAYBACK_DEVICE = PlaybackDevices.PROP_DEVICE

        /**
         * The list of class names of custom `Renderer` implementations to be registered with JMF.
         */
        private val CUSTOM_RENDERERS = arrayOf(
            if (OSUtils.IS_ANDROID) ".audio.AudioTrackRenderer" else null,
            if (OSUtils.IS_ANDROID) ".audio.OpenSLESRenderer" else null,
            if (OSUtils.IS_LINUX) ".audio.PulseAudioRenderer" else null,
            if (OSUtils.IS_WINDOWS) ".audio.WASAPIRenderer" else null,
            if (OSUtils.IS_ANDROID) null else ".audio.PortAudioRenderer",
            if (OSUtils.IS_ANDROID) ".video.SurfaceRenderer" else null,
            ".video.JAWTRenderer"
        )

        /**
         * The default value to be used for the [//#PROP_AUDIO_DENOISE] property when it does not have a value.
         */
        const val DEFAULT_AUDIO_DENOISE = true

        /**
         * The default value to be used for the [//#PROP_AUDIO_ECHOCANCEL] property when it does not have a value.
         */
        const val DEFAULT_AUDIO_ECHOCANCEL = true

        /**
         * The default value to be used for the [.PROP_AUDIO_ECHOCANCEL_FILTER_LENGTH_IN_MILLIS]
         * property when it does not have a value. The recommended filter length is approximately the
         * third of the room reverberation time. For example, in a small room, reverberation time is in
         * the order of 300 ms, so a filter length of 100 ms is a good choice (800 samples at 8000 Hz sampling rate).
         */
        const val DEFAULT_AUDIO_ECHOCANCEL_FILTER_LENGTH_IN_MILLIS = 100L

        /**
         * The default value for video codec bitrate.
         */
        const val DEFAULT_VIDEO_BITRATE = 128

        /**
         * The default frame rate, `-1` unlimited.
         */
        const val DEFAULT_VIDEO_FRAMERATE = -1

        /**
         * The default video width.
         */
        const val DEFAULT_VIDEO_WIDTH = 1280

        /**
         * The default video height.
         */
        const val DEFAULT_VIDEO_HEIGHT = 720

        /**
         * The default value for video maximum bandwidth.
         */
        const val DEFAULT_VIDEO_RTP_PACING_THRESHOLD = 256

        /**
         * The name of the `long` property which determines the filter length in milliseconds to
         * be used by the echo cancellation implementation. The recommended filter length is
         * approximately the third of the room reverberation time. For example, in a small room,
         * reverberation time is in the order of 300 ms, so a filter length of 100 ms is a good choice
         * (800 samples at 8000 Hz sampling rate).
         */
        private const val PROP_AUDIO_ECHOCANCEL_FILTER_LENGTH_IN_MILLIS = "neomedia.echocancel.filterLengthInMillis"
        const val PROP_AUDIO_SYSTEM = "neomedia.audioSystem"
        const val PROP_AUDIO_SYSTEM_DEVICES = PROP_AUDIO_SYSTEM + "." + DeviceSystem.PROP_DEVICES

        /**
         * The property we use to store the settings for video codec bitrate.
         */
        private const val PROP_VIDEO_BITRATE = "neomedia.video.bitrate"

        /**
         * The `ConfigurationService` property which stores the device used by
         * `DeviceConfiguration` for video capture.
         */
        private const val PROP_VIDEO_DEVICE = "neomedia.videoDevice"

        /**
         * The property we use to store the video frame rate settings.
         */
        private const val PROP_VIDEO_FRAMERATE = "neomedia.video.framerate"

        /**
         * The name of the property which specifies the height of the video.
         */
        private const val PROP_VIDEO_HEIGHT = "neomedia.video.height"

        /**
         * The property we use to store the settings for maximum allowed video bandwidth (used to
         * normalize RTP traffic, and not in codec configuration)
         */
        const val PROP_VIDEO_RTP_PACING_THRESHOLD = "neomedia.video.maxbandwidth"

        /**
         * The name of the property which specifies the width of the video.
         */
        private const val PROP_VIDEO_WIDTH = "neomedia.video.width"

        /**
         * The currently supported resolutions we will show as option for user selection.
         */
        val SUPPORTED_RESOLUTIONS = arrayOf( // new Dimension(160, 120), // QQVGA
            // new Dimension(176, 144), // QCIF
            // new Dimension(320, 200), // QVGA
            Dimension(320, 240),  // QVGA
            // new Dimension(352, 288), // CIF
            Dimension(640, 480),  // VGA
            Dimension(720, 480),  // DV NTSC
            // new Dimension(800, 450), // < QHD - not support by camera preview
            Dimension(960, 720),  // Panasonic DVCPRO100
            Dimension(1280, 720), // HDTV
            Dimension(1440, 1080),// HDV 1080
            Dimension(1920, 1080) // HD
        )

        /**
         * The name of the `DeviceConfiguration` property which represents the device used by
         * `DeviceConfiguration` for video capture.
         */
        const val VIDEO_CAPTURE_DEVICE = "VIDEO_CAPTURE_DEVICE"

        /**
         * Fixes the list of `Renderer`s registered with FMJ in order to resolve operating system-specific issues.
         */
        private fun fixRenderers() {
            val renderers = PlugInManager.getPlugInList(null, null, PlugInManager.RENDERER) as Vector<String>

            /*
             * JMF is no longer in use, FMJ is used in its place. FMJ has its own JavaSoundRenderer
             * which is also extended into a JMF-compatible one.
             */
            PlugInManager.removePlugIn("com.sun.media.renderer.audio.JavaSoundRenderer", PlugInManager.RENDERER)

            if (OSUtils.IS_WINDOWS) {
                if (OSUtils.IS_WINDOWS32) {
                    /*
                 * DDRenderer will cause 32-bit Windows Vista/7 to switch its theme from Aero to
                 * Vista Basic so try to pick up a different Renderer.
                 */
                    if (renderers.contains("com.sun.media.renderer.video.GDIRenderer")) {
                        PlugInManager.removePlugIn("com.sun.media.renderer.video.DDRenderer", PlugInManager.RENDERER)
                    }
                }
                else if (OSUtils.IS_WINDOWS64) {
                    /*
                 * Remove the native Renderers for 64-bit Windows because native JMF libs are not
                 * available for 64-bit machines.
                 */
                    PlugInManager.removePlugIn("com.sun.media.renderer.video.GDIRenderer", PlugInManager.RENDERER)
                    PlugInManager.removePlugIn("com.sun.media.renderer.video.DDRenderer", PlugInManager.RENDERER)
                }
            }
            else if (!OSUtils.IS_LINUX32) {
                if (renderers.contains("com.sun.media.renderer.video.LightWeightRenderer")
                        || renderers.contains("com.sun.media.renderer.video.AWTRenderer")) {
                    // Remove XLibRenderer because it is native and JMF is supported on 32-bit machines only.
                    PlugInManager.removePlugIn("com.sun.media.renderer.video.XLibRenderer", PlugInManager.RENDERER)
                }
            }
        }
    }
}