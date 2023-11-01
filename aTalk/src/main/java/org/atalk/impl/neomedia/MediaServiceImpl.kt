/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import com.sun.media.util.Registry
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp.Companion.getResString
import org.atalk.impl.neomedia.MediaUtils.getMediaFormat
import org.atalk.impl.neomedia.codec.EncodingConfigurationConfigImpl
import org.atalk.impl.neomedia.codec.EncodingConfigurationImpl
import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.codec.FMJPlugInConfiguration.registerCustomCodecs
import org.atalk.impl.neomedia.codec.FMJPlugInConfiguration.registerCustomMultiplexers
import org.atalk.impl.neomedia.codec.FMJPlugInConfiguration.registerCustomPackages
import org.atalk.impl.neomedia.codec.video.AVFrameFormat
import org.atalk.impl.neomedia.codec.video.HFlip
import org.atalk.impl.neomedia.codec.video.SwScale
import org.atalk.impl.neomedia.device.AudioMediaDeviceImpl
import org.atalk.impl.neomedia.device.AudioMixerMediaDevice
import org.atalk.impl.neomedia.device.DeviceConfiguration
import org.atalk.impl.neomedia.device.DeviceSystem
import org.atalk.impl.neomedia.device.DeviceSystem.Companion.initializeDeviceSystems
import org.atalk.impl.neomedia.device.MediaDeviceImpl
import org.atalk.impl.neomedia.device.ScreenDeviceImpl
import org.atalk.impl.neomedia.device.VideoTranslatorMediaDevice
import org.atalk.impl.neomedia.format.MediaFormatFactoryImpl
import org.atalk.impl.neomedia.format.MediaFormatImpl
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl
import org.atalk.impl.neomedia.recording.RecorderEventHandlerJSONImpl
import org.atalk.impl.neomedia.recording.RecorderImpl
import org.atalk.impl.neomedia.recording.RecorderRtpImpl
import org.atalk.impl.neomedia.rtp.translator.RTPTranslatorImpl
import org.atalk.impl.neomedia.transform.dtls.DtlsControlImpl
import org.atalk.impl.neomedia.transform.sdes.SDesControlImpl
import org.atalk.impl.neomedia.transform.zrtp.ZrtpControlImpl
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.libjitsi.LibJitsi.Companion.configurationService
import org.atalk.service.neomedia.BasicVolumeControl
import org.atalk.service.neomedia.MediaService
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.MediaUseCase
import org.atalk.service.neomedia.RTPTranslator
import org.atalk.service.neomedia.SrtpControl
import org.atalk.service.neomedia.SrtpControlType
import org.atalk.service.neomedia.StreamConnector
import org.atalk.service.neomedia.VolumeControl
import org.atalk.service.neomedia.codec.EncodingConfiguration
import org.atalk.service.neomedia.device.MediaDevice
import org.atalk.service.neomedia.device.ScreenDevice
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.service.neomedia.format.MediaFormatFactory
import org.atalk.service.neomedia.recording.Recorder
import org.atalk.service.neomedia.recording.RecorderEventHandler
import org.atalk.util.MediaType
import org.atalk.util.OSUtils
import org.atalk.util.event.PropertyChangeNotifier
import org.atalk.util.swing.VideoContainer
import org.json.JSONObject
import timber.log.Timber
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.Window
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowListener
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.*
import javax.media.CaptureDeviceInfo
import javax.media.Codec
import javax.media.ConfigureCompleteEvent
import javax.media.ControllerEvent
import javax.media.Format
import javax.media.Manager
import javax.media.MediaLocator
import javax.media.NotConfiguredError
import javax.media.Player
import javax.media.Processor
import javax.media.RealizeCompleteEvent
import javax.media.UnsupportedPlugInException
import javax.media.control.TrackControl
import javax.media.format.RGBFormat
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Implements `MediaService` for JMF.
 *
 * @author Lyubomir Marinov
 * @author Dmitri Melnikov
 * @author Eng Chong Meng
 * @author MilanKral
 * @author Eng Chong Meng
 */
class MediaServiceImpl : PropertyChangeNotifier(), MediaService {
    /**
     * Gets the `CaptureDevice` user choices such as the default audio and video capture devices.
     *
     * @return the `CaptureDevice` user choices such as the default audio and video capture devices.
     */
    /**
     * The `CaptureDevice` user choices such as the default audio and video capture devices.
     */
    val deviceConfiguration = DeviceConfiguration()

    /**
     * The `PropertyChangeListener` which listens to [.deviceConfiguration].
     */
    private val deviceConfigurationPropertyChangeListener = PropertyChangeListener { event: PropertyChangeEvent -> deviceConfigurationPropertyChange(event) }

    /**
     * The list of audio `MediaDevice`s reported by this instance when its
     * [MediaService.getDevices] method is called with an argument
     * [MediaType.AUDIO].
     */
    private val audioDevices = ArrayList<MediaDeviceImpl>()
    /**
     * Returns the current encoding configuration -- the instance that contains the global settings.
     * Note that any changes made to this instance will have immediate effect on the configuration.
     *
     * @return the current encoding configuration -- the instance that contains the global settings.
     */
    /**
     * The [EncodingConfiguration] instance that holds the current (global) list of formats and their preference.
     */
    override val currentEncodingConfiguration: EncodingConfiguration
    /**
     * Gets the `MediaFormatFactory` through which `MediaFormat` instances may be
     * created for the purposes of working with the `MediaStream`s created by this `MediaService`.
     *
     * @return the `MediaFormatFactory` through which `MediaFormat` instances may be
     * created for the purposes of working with the `MediaStream`s created by this `MediaService`
     * @see MediaService.formatFactory
     */
    /**
     * The `MediaFormatFactory` through which `MediaFormat` instances may be created
     * for the purposes of working with the `MediaStream`s created by this `MediaService`.
     */
    override var formatFactory: MediaFormatFactory? = null
        get() {
            if (field == null) field = MediaFormatFactoryImpl()
            return field
        }
        private set
    /**
     * Gets the one and only `MediaDevice` instance with `MediaDirection` not
     * allowing sending and `MediaType` equal to `AUDIO`.
     *
     * @return the one and only `MediaDevice` instance with `MediaDirection` not
     * allowing sending and `MediaType` equal to `AUDIO`
     */
    /**
     * The one and only `MediaDevice` instance with `MediaDirection` not allowing
     * sending and `MediaType` equal to `AUDIO`.
     */
    private var nonSendAudioDevice: MediaDevice? = null
        get() {
            if (field == null) field = AudioMediaDeviceImpl()
            return field
        }
    /**
     * Gets the one and only `MediaDevice` instance with `MediaDirection` not
     * allowing sending and `MediaType` equal to `VIDEO`.
     *
     * @return the one and only `MediaDevice` instance with `MediaDirection` not
     * allowing sending and `MediaType` equal to `VIDEO`
     */
    /**
     * The one and only `MediaDevice` instance with `MediaDirection` not allowing
     * sending and `MediaType` equal to `VIDEO`.
     */
    private var nonSendVideoDevice: MediaDevice? = null
        get() {
            if (field == null) field = MediaDeviceImpl(MediaType.VIDEO)
            return field
        }

    /**
     * The list of video `MediaDevice`s reported by this instance when its
     * [MediaService.getDevices] method is called with an argument
     * [MediaType.VIDEO].
     */
    private val videoDevices = ArrayList<MediaDeviceImpl>()

    /**
     * Listeners interested in Recorder events without the need to have access to their instances.
     */
    private val recorderListeners = ArrayList<Recorder.Listener>()

    /**
     * Initializes a new `MediaServiceImpl` instance.
     */
    init {
        /*
         * XXX The deviceConfiguration is initialized and referenced by this instance so adding
         * deviceConfigurationPropertyChangeListener does not need a matching removal.
         */
        deviceConfiguration.addPropertyChangeListener(deviceConfigurationPropertyChangeListener)
        currentEncodingConfiguration = EncodingConfigurationConfigImpl(ENCODING_CONFIG_PROP_PREFIX)

        /*
         * Perform one-time initialization after initializing the first instance of MediaServiceImpl.
         */
        synchronized(MediaServiceImpl::class.java) {
            if (!postInitializeOnce) {
                postInitializeOnce = true
                postInitializeOnce(this)
            }
        }
    }

    /**
     * Create a `MediaStream` which will use a specific `MediaDevice` for capture and
     * playback of media. The new instance will not have a `StreamConnector` at the time of
     * its construction, and a `StreamConnector` will be specified later on in order to
     * enable the new instance to send and receive media.
     *
     * @param device the `MediaDevice` to be used by the new instance for capture and playback of media
     * @return a newly-created `MediaStream` which will use the specified `device`
     * for capture and playback of media
     * @see MediaService.createMediaStream
     */
    override fun createMediaStream(device: MediaDevice): MediaStream? {
        return createMediaStream(null, device)
    }

    /**
     * {@inheritDoc}
     *
     * Implements [MediaService.createMediaStream]. Initializes a new
     * `AudioMediaStreamImpl` or `VideoMediaStreamImpl` in accord with `mediaType`
     */
    override fun createMediaStream(mediaType: MediaType): MediaStream? {
        return createMediaStream(mediaType, null, null, null)
    }

    /**
     * Creates a new `MediaStream` instance which will use the specified `MediaDevice`
     * for both capture and playback of media exchanged via the specified `StreamConnector`.
     *
     * @param connector the `StreamConnector` that the new `MediaStream` instance is to use for
     * sending and receiving media
     * @param device the `MediaDevice` that the new `MediaStream` instance is to use for both
     * capture and playback of media exchanged via the specified `connector`
     * @return a new `MediaStream` instance
     * @see MediaService.createMediaStream
     */
    override fun createMediaStream(connector: StreamConnector?, device: MediaDevice?): MediaStream? {
        return createMediaStream(connector, device, null)
    }

    /**
     * {@inheritDoc}
     */
    override fun createMediaStream(connector: StreamConnector?, mediaType: MediaType?): MediaStream? {
        return createMediaStream(connector, mediaType, null)
    }

    /**
     * Creates a new `MediaStream` instance which will use the specified `MediaDevice`
     * for both capture and playback of media exchanged via the specified `StreamConnector`.
     *
     * @param connector the `StreamConnector` that the new `MediaStream` instance is to use for
     * sending and receiving media
     * @param device the `MediaDevice` that the new `MediaStream` instance is to use for both
     * capture and playback of media exchanged via the specified `connector`
     * @param srtpControl a control which is already created, used to control the SRTP operations.
     * @return a new `MediaStream` instance
     * @see MediaService.createMediaStream
     */
    override fun createMediaStream(connector: StreamConnector?, device: MediaDevice?, srtpControl: SrtpControl?): MediaStream? {
        return createMediaStream(null, connector, device, srtpControl)
    }

    /**
     * {@inheritDocs}
     */
    override fun createMediaStream(connector: StreamConnector?, mediaType: MediaType?, srtpControl: SrtpControl?): MediaStream? {
        return createMediaStream(mediaType, connector, null, srtpControl)
    }

    /**
     * Initializes a new `MediaStream` instance. The method is the actual implementation to
     * which the public `createMediaStream` methods of `MediaServiceImpl` delegate.
     *
     * @param mType the `MediaType` of the new `MediaStream` instance to be initialized. If
     * `null`, `device` must be non- `null` and its
     * [MediaDevice.mediaType] will be used to determine the `MediaType` of
     * the new instance. If non-`null`, `device` may be `null`. If non-
     * `null` and `device` is non- `null`, the `MediaType` of
     * `device` must be (equal to) `mediaType`.
     * @param connector the `StreamConnector` to be used by the new instance if non-`null`
     * @param device the `MediaDevice` to be used by the instance if non- `null`
     * @param srtpControl the `SrtpControl` to be used by the new instance if non- `null`
     * @return a new `MediaStream` instance
     */
    private fun createMediaStream(
            mType: MediaType?, connector: StreamConnector?,
            device: MediaDevice?, srtpControl: SrtpControl?,
    ): MediaStream? {
        // Make sure that mediaType and device are in accord.
        var mediaType = mType
        when {
            mediaType == null -> {
                if (device == null) throw NullPointerException("device") else mediaType = device.mediaType
            }
            (device != null) && mediaType != device.mediaType -> throw IllegalArgumentException("device")
        }

        return when (mediaType) {
            MediaType.AUDIO -> AudioMediaStreamImpl(connector, device, srtpControl)
            MediaType.VIDEO -> VideoMediaStreamImpl(connector, device, srtpControl)
            else -> null
        }
    }

    /**
     * Creates a new `MediaDevice` which uses a specific `MediaDevice` to capture and
     * play back media and performs mixing of the captured media and the media played back by any
     * other users of the returned `MediaDevice`. For the `AUDIO` `MediaType`,
     * the returned device is commonly referred to as an audio mixer. The `MediaType` of the
     * returned `MediaDevice` is the same as the `MediaType` of the specified `device`.
     *
     * @param device the `MediaDevice` which is to be used by the returned `MediaDevice` to
     * actually capture and play back media
     * @return a new `MediaDevice` instance which uses `device` to capture and play
     * back media and performs mixing of the captured media and the media played back by any
     * other users of the returned `MediaDevice` instance
     * @see MediaService.createMixer
     */
    override fun createMixer(device: MediaDevice): MediaDevice? {
        return when (device.mediaType) {
            MediaType.AUDIO -> AudioMixerMediaDevice(device as AudioMediaDeviceImpl)

            MediaType.VIDEO -> VideoTranslatorMediaDevice(device as MediaDeviceImpl)

            else ->
                /*
                 * TODO If we do not support mixing, should we return null or rather a MediaDevice
                 * with INACTIVE MediaDirection?
                 */
                null
        }
    }

    /**
     * Gets the default `MediaDevice` for the specified `MediaType`.
     *
     * @param mediaType a `MediaType` value indicating the type of media to be handled by the
     * `MediaDevice` to be obtained
     * @param useCase the `MediaUseCase` to obtain the `MediaDevice` list for
     * @return the default `MediaDevice` for the specified `mediaType` if such a
     * `MediaDevice` exists; otherwise, `null`
     * @see MediaService.getDefaultDevice
     */
    override fun getDefaultDevice(mediaType: MediaType, useCase: MediaUseCase): MediaDevice? {
        val captureDeviceInfo = when (mediaType) {
            MediaType.AUDIO -> deviceConfiguration.audioCaptureDevice
            MediaType.VIDEO -> deviceConfiguration.getVideoCaptureDevice(useCase)
            else -> null
        }
        var defaultDevice: MediaDevice? = null
        if (captureDeviceInfo != null) {
            for (device in getDevices(mediaType, useCase)) {
                if ((device is MediaDeviceImpl)
                        && (captureDeviceInfo == device.getCaptureDeviceInfo())) {
                    defaultDevice = device
                    break
                }
            }
        }
        if (defaultDevice == null) {
            when (mediaType) {
                MediaType.AUDIO -> defaultDevice = nonSendAudioDevice
                MediaType.VIDEO -> defaultDevice = nonSendVideoDevice
                else -> {}
            }
        }
        return defaultDevice
    }

    /**
     * Gets a list of the `MediaDevice`s known to this `MediaService` and handling
     * the specified `MediaType`.
     *
     * @param mediaType the `MediaType` to obtain the `MediaDevice` list for
     * @param useCase the `MediaUseCase` to obtain the `MediaDevice` list for
     * @return a new `List` of `MediaDevice`s known to this `MediaService` and
     * handling the specified `MediaType`. The returned `List` is a copy of the
     * internal storage and, consequently, modifications to it do not affect this instance.
     * Despite the fact that a new `List` instance is returned by each call to this
     * method, the `MediaDevice` instances are the same if they are still known to this
     * `MediaService` to be available.
     * @see MediaService.getDevices
     */
    override fun getDevices(mediaType: MediaType, useCase: MediaUseCase): List<MediaDevice> {
        val cdis: MutableList<out CaptureDeviceInfo>
        val privateDevices: MutableList<MediaDeviceImpl>
        if (MediaType.VIDEO == mediaType) {
            /*
             * In case a video capture device has been added to or removed from system (i.e.
             * webcam, monitor, etc.), rescan the video capture devices.
             */
            initializeDeviceSystems(MediaType.VIDEO)
        }
        when (mediaType) {
            MediaType.AUDIO -> {
                cdis = deviceConfiguration.availableAudioCaptureDevices
                privateDevices = audioDevices
            }
            MediaType.VIDEO -> {
                cdis = deviceConfiguration.getAvailableVideoCaptureDevices(useCase)
                privateDevices = videoDevices
            }
            else ->
                /*
                 * MediaService does not understand MediaTypes other than AUDIO and VIDEO.
                 */
                return EMPTY_DEVICES
        }

        var publicDevices: MutableList<MediaDevice>
        synchronized(privateDevices) {
            if (cdis.size <= 0) privateDevices.clear() else {
                val deviceIter = privateDevices.iterator()
                while (deviceIter.hasNext()) {
                    val cdiIter = cdis.iterator()
                    val captureDeviceInfo = deviceIter.next().getCaptureDeviceInfo()
                    var deviceIsFound = false
                    while (cdiIter.hasNext()) {
                        if (captureDeviceInfo == cdiIter.next()) {
                            deviceIsFound = true
                            cdiIter.remove()
                            break
                        }
                    }
                    if (!deviceIsFound) deviceIter.remove()
                }
                for (cdi: CaptureDeviceInfo? in cdis) {
                    if (cdi == null) continue

                    val device = when (mediaType) {
                        MediaType.AUDIO -> AudioMediaDeviceImpl(cdi)
                        MediaType.VIDEO -> MediaDeviceImpl(cdi, mediaType)
                        else -> null
                    }
                    if (device != null) privateDevices.add(device)
                }
            }
            publicDevices = ArrayList(privateDevices)
        }

        /*
         * If there are no MediaDevice instances of the specified mediaType, make sure that
         * there is at least one MediaDevice which does not allow sending.
         */
        if (publicDevices.isEmpty()) {
            val nonSendDevice = when (mediaType) {
                MediaType.AUDIO -> nonSendAudioDevice
                MediaType.VIDEO -> nonSendVideoDevice
                else ->
                    /*
                     * There is no MediaDevice with direction not allowing sending and mediaType
                     * other than AUDIO and VIDEO.
                     */
                    null
            }
            if (nonSendDevice != null) publicDevices.add(nonSendDevice)
        }
        return publicDevices
    }

    /**
     * {@inheritDoc}
     */
    override fun createSrtpControl(srtpControlType: SrtpControlType, myZid: ByteArray?): SrtpControl {
        return when (srtpControlType) {
            SrtpControlType.DTLS_SRTP -> DtlsControlImpl()
            SrtpControlType.SDES -> SDesControlImpl()
            SrtpControlType.ZRTP -> ZrtpControlImpl((myZid)!!)
            else -> DtlsControlImpl()
        }
    }

    /**
     * Gets the `VolumeControl` which controls the volume level of audio output/playback.
     *
     * @return the `VolumeControl` which controls the volume level of audio output/playback
     * @see MediaService.outputVolumeControl
     */
    override val outputVolumeControl: VolumeControl
        get() {
            if (Companion.outputVolumeControl == null) {
                Companion.outputVolumeControl = BasicVolumeControl(VolumeControl.PLAYBACK_VOLUME_LEVEL_PROPERTY_NAME)
            }
            return Companion.outputVolumeControl!!
        }

    // If available, use hardware. Otherwise, use software.
    /**
     * Gets the `VolumeControl` which controls the volume level of audio input/capture.
     *
     * @return the `VolumeControl` which controls the volume level of audio input/capture
     * @see MediaService.inputVolumeControl
     */
    override val inputVolumeControl: VolumeControl
        get() {
            if (Companion.inputVolumeControl == null) {
                // If available, use hardware.
                try {
                    Companion.inputVolumeControl = HardwareVolumeControl(this, VolumeControl.CAPTURE_VOLUME_LEVEL_PROPERTY_NAME)
                } catch (t: Throwable) {
                    if (t is ThreadDeath) throw t else if (t is InterruptedException) Thread.currentThread().interrupt()
                }

                // Otherwise, use software.
                if (Companion.inputVolumeControl == null) {
                    Companion.inputVolumeControl = BasicVolumeControl(VolumeControl.CAPTURE_VOLUME_LEVEL_PROPERTY_NAME)
                }
            }
            return Companion.inputVolumeControl!!
        }

    /**
     * Get available screens.
     *
     * @return screens
     */
    override val availableScreenDevices: List<ScreenDevice>
        get() {
            val screens = ScreenDeviceImpl.availableScreenDevices
            val screenList = when {
                screens.isNotEmpty() ->   screens.toList()
                else -> emptyList()
            }
            return screenList
        }

    /**
     * Get default screen device.
     *
     * @return default screen device
     */
    override val defaultScreenDevice: ScreenDevice?
        get() = ScreenDeviceImpl.defaultScreenDevice

    /**
     * Creates a new `Recorder` instance that can be used to record a call which captures
     * and plays back media using a specific `MediaDevice`.
     *
     * @param device the `MediaDevice` which is used for media capture and playback by the call to be recorded
     * @return a new `Recorder` instance that can be used to record a call which captures
     * and plays back media using the specified `MediaDevice`
     * @see MediaService.createRecorder
     */
    override fun createRecorder(device: MediaDevice): Recorder? {
        return if (device is AudioMixerMediaDevice) RecorderImpl(device) else null
    }

    /**
     * {@inheritDoc}
     */
    override fun createRecorder(translator: RTPTranslator): Recorder {
        return RecorderRtpImpl(translator)
    }
    // JSONObject json = (JSONObject) JSONValue.parseWithException(source);

    /*
    * The dynamic payload type is the name of the property name and the format
    * which prefers it is the property value .
    */
    /*
    * Set the dynamicPayloadTypePreferences to their default values. If the user chooses to
    * override them through the ConfigurationService, they will be overwritten later on.
    */
    /*
      * Try to load dynamicPayloadTypePreferences from the ConfigurationService.
      */
    /**
     * Returns a [Map] that binds indicates whatever preferences this media service
     * implementation may have for the RTP payload type numbers that get dynamically assigned to
     * [MediaFormat]s with no static payload type. The method is useful for formats such as
     * "telephone-event" for example that is statically assigned the 101 payload type by some
     * legacy systems. Signaling protocol implementations such as SIP and XMPP should make sure
     * that, whenever this is possible, they assign to formats the dynamic payload type returned
     * in this [Map].
     *
     * @return a [Map] binding some formats to a preferred dynamic RTP payload type number.
     */
    override val dynamicPayloadTypePreferences: MutableMap<MediaFormat, Byte>
        get() {
            if (Companion.dynamicPayloadTypePreferences == null) {
                Companion.dynamicPayloadTypePreferences = HashMap()

                /*
             * Set the dynamicPayloadTypePreferences to their default values. If the user chooses to
             * override them through the ConfigurationService, they will be overwritten later on.
             */
                val telephoneEvent = getMediaFormat("telephone-event", 8000.0)
                if (telephoneEvent != null) dynamicPayloadTypePreferences[telephoneEvent] = 101.toByte()
                val h264 = getMediaFormat("H264", VideoMediaFormatImpl.DEFAULT_CLOCK_RATE)
                if (h264 != null) dynamicPayloadTypePreferences[h264] = 99.toByte()

                /*
             * Try to load dynamicPayloadTypePreferences from the ConfigurationService.
             */
                val cfg = configurationService
                val prefix = DYNAMIC_PAYLOAD_TYPE_PREFERENCES_PNAME_PREFIX
                val propertyNames = cfg.getPropertyNamesByPrefix(prefix, true)
                for (propertyName in propertyNames) {
                    /*
                 * The dynamic payload type is the name of the property name and the format
                 * which prefers it is the property value.
                 */
                    var dynamicPayloadTypePreference: Byte = 0
                    var exception: Throwable? = null
                    try {
                        dynamicPayloadTypePreference = propertyName.substring(prefix.length + 1).toByte()
                    } catch (ioobe: IndexOutOfBoundsException) {
                        exception = ioobe
                    } catch (ioobe: NumberFormatException) {
                        exception = ioobe
                    }
                    if (exception != null) {
                        Timber.w(exception, "Ignoring dynamic payload type preference which could not be parsed: %s", propertyName)
                        continue
                    }
                    val source = cfg.getString(propertyName)
                    if ((source != null) && (source.isNotEmpty())) {
                        try {
                            // JSONObject json = (JSONObject) JSONValue.parseWithException(source);
                            val json = JSONObject(source)
                            val encoding = json.getString(MediaFormatImpl.ENCODING_PNAME)
                            val clockRate = json.getLong(MediaFormatImpl.CLOCK_RATE_PNAME)
                            val fmtps = HashMap<String, String>()
                            if (json.has(MediaFormatImpl.FORMAT_PARAMETERS_PNAME)) {
                                val jsonFmtps = json[MediaFormatImpl.FORMAT_PARAMETERS_PNAME] as JSONObject
                                val keys = jsonFmtps.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    val value = jsonFmtps.getString(key)
                                    fmtps[key] = value
                                }
                            }
                            val mediaFormat = getMediaFormat(encoding, clockRate.toDouble(), fmtps)
                            if (mediaFormat != null) {
                                dynamicPayloadTypePreferences[mediaFormat] = dynamicPayloadTypePreference
                            }
                        } catch (jsone: Throwable) {
                            Timber.w(jsone, "Ignoring dynamic payload type preference which could not be parsed: %s", source)
                        }
                    }
                }
            }
            return Companion.dynamicPayloadTypePreferences!!
        }

    /**
     * Creates a preview component for the specified device(video device) used to show video
     * preview from that device.
     *
     * @param device the video device
     * @param preferredWidth the width we prefer for the component
     * @param preferredHeight the height we prefer for the component
     * @return the preview component.
     */
    override fun getVideoPreviewComponent(device: MediaDevice?, preferredWidth: Int, preferredHeight: Int): Any {
        var pfWidth = preferredWidth
        var pfHeight = preferredHeight
        val noPreviewText = getResString(R.string.impl_media_configform_NO_PREVIEW)
        val noPreview = JLabel(noPreviewText)
        noPreview.horizontalAlignment = SwingConstants.CENTER
        noPreview.verticalAlignment = SwingConstants.CENTER
        val videoContainer = VideoContainer(noPreview, false)
        if ((pfWidth > 0) && (pfHeight > 0)) {
            videoContainer.preferredSize = Dimension(pfWidth, pfHeight)
        }
        try {
            var captureDeviceInfo: CaptureDeviceInfo? = null
            if ((device != null) && (((device as MediaDeviceImpl).getCaptureDeviceInfo().also { captureDeviceInfo = (it)!! }) != null)) {
                val dataSource = Manager.createDataSource(captureDeviceInfo!!.locator)

                /*
                 * Don't let the size be uselessly small just because the videoContainer has too
                 * small a preferred size.
                 */
                if ((pfWidth < 128) || (pfHeight < 96)) {
                    pfWidth = 128
                    pfHeight = 96
                }
                VideoMediaStreamImpl.selectVideoSize(dataSource, pfWidth, pfHeight)

                // A Player is documented to be created on a connected DataSource.
                dataSource.connect()
                val player = Manager.createProcessor(dataSource)
                val listener = VideoContainerHierarchyListener(videoContainer, player)
                videoContainer.addHierarchyListener(listener)
                val locator = dataSource.locator
                player.addControllerListener { event -> controllerUpdateForPreview(event, videoContainer, locator, listener) }
                player.configure()
            }
        } catch (t: Throwable) {
            if (t is ThreadDeath) throw t else Timber.e(t, "Failed to create video preview")
        }
        return videoContainer
    }

    /**
     * Get a `MediaDevice` for a part of desktop streaming/sharing.
     *
     * @param width width of the part
     * @param height height of the part
     * @param x origin of the x coordinate (relative to the full desktop)
     * @param y origin of the y coordinate (relative to the full desktop)
     * @return `MediaDevice` representing the part of desktop or null if problem
     */
    override fun getMediaDeviceForPartialDesktopStreaming(width: Int, height: Int, x: Int, y: Int): MediaDevice? {
        var pWidth = width
        var pHeight = height
        var oX = x
        var oY = y
        val device: MediaDevice
        val name = "Partial desktop streaming"
        var multiple: Int
        val p = Point(max(oX, 0), max(oY, 0))
        val dev = getScreenForPoint(p)
        val display: Int
        if (dev != null) display = dev.index else return null

        /* on Mac OS X, width have to be a multiple of 16 */
        if (OSUtils.IS_MAC) {
            multiple = (pWidth / 16f).roundToInt()
            pWidth = multiple * 16
        } else {
            /* JMF filter graph seems to not like odd width */
            multiple = (pWidth / 2f).roundToInt()
            pWidth = multiple * 2
        }

        /* JMF filter graph seems to not like odd height */
        multiple = (pHeight / 2f).roundToInt()
        pHeight = multiple * 2
        val size = Dimension(pWidth, pHeight)
        val formats = arrayOf<Format>(
                AVFrameFormat(
                        size,
                        Format.NOT_SPECIFIED.toFloat(),
                        FFmpeg.PIX_FMT_ARGB,
                        Format.NOT_SPECIFIED),
                RGBFormat(
                        size,  // size
                        Format.NOT_SPECIFIED,  // maxDataLength
                        Format.byteArray,  // dataType
                        Format.NOT_SPECIFIED.toFloat(),  // frameRate
                        32,  // bitsPerPixel
                        2 /* red */,
                        3 /* green */,
                        4 /* blue */)
        )
        val bounds = (dev as ScreenDeviceImpl).bounds
        oX -= bounds.x
        oY -= bounds.y
        val devInfo = CaptureDeviceInfo("$name $display",
                MediaLocator(
                        (DeviceSystem.LOCATOR_PROTOCOL_IMGSTREAMING + ":"
                                + display + "," + oX + "," + oY)), formats)
        device = MediaDeviceImpl(devInfo, MediaType.VIDEO)
        return device
    }

    /**
     * If the `MediaDevice` corresponds to partial desktop streaming device.
     *
     * @param mediaDevice `MediaDevice`
     * @return true if `MediaDevice` is a partial desktop streaming device, false otherwise
     */
    override fun isPartialStreaming(mediaDevice: MediaDevice?): Boolean {
        if (mediaDevice == null) return false
        val dev = mediaDevice as MediaDeviceImpl
        val cdi = dev.getCaptureDeviceInfo()
        return (cdi != null) && cdi.name.startsWith("Partial desktop streaming")
    }

    /**
     * Find the screen device that contains specified point.
     *
     * @param p point coordinates
     * @return screen device that contains point
     */
    fun getScreenForPoint(p: Point?): ScreenDevice? {
        for (dev in availableScreenDevices) if (dev.containsPoint(p)) return dev
        return null
    }

    /**
     * Gets the origin of a specific desktop streaming device.
     *
     * @param mediaDevice the desktop streaming device to get the origin on
     * @return the origin of the specified desktop streaming device
     */
    override fun getOriginForDesktopStreamingDevice(mediaDevice: MediaDevice): Point? {
        val dev = mediaDevice as MediaDeviceImpl
        val cdi = dev.getCaptureDeviceInfo() ?: return null
        val locator = cdi.locator
        if (DeviceSystem.LOCATOR_PROTOCOL_IMGSTREAMING != locator.protocol) return null
        val remainder = locator.remainder
        val split = remainder.split(",")
        val index = if (split.size > 1) split[0].toInt() else remainder.toInt()
        val devs = availableScreenDevices
        if (devs.size - 1 >= index) {
            val r = (devs[index] as ScreenDeviceImpl).bounds
            return Point(r.x, r.y)
        }
        return null
    }

    /**
     * Those interested in Recorder events add listener through MediaService. This way they don't
     * need to have access to the Recorder instance. Adds a new `Recorder.Listener` to the
     * list of listeners interested in notifications from a `Recorder`.
     *
     * @param listener the new `Recorder.Listener` to be added to the list of listeners interested in
     * notifications from `Recorder`s.
     */
    override fun addRecorderListener(listener: Recorder.Listener) {
        synchronized(recorderListeners) { if (!recorderListeners.contains(listener)) recorderListeners.add(listener) }
    }

    /**
     * Removes an existing `Recorder.Listener` from the list of listeners interested in
     * notifications from `Recorder`s.
     *
     * @param listener the existing `Listener` to be removed from the list of listeners interested in
     * notifications from `Recorder`s
     */
    override fun removeRecorderListener(listener: Recorder.Listener) {
        synchronized(recorderListeners) { recorderListeners.remove(listener) }
    }

    /**
     * The value which will be used for the canonical end-point identifier (CNAME) in RTCP packets
     * sent by this running instance of libjitsi.
     */
    override val rtpCname = UUID.randomUUID().toString()

    /**
     * Gives access to currently registered `Recorder.Listener`s.
     *
     * @return currently registered `Recorder.Listener`s.
     */
    override fun getRecorderListeners(): Iterator<Recorder.Listener> {
        return getRecorderListeners().iterator()
    }

    /**
     * Notifies this instance that the value of a property of [.deviceConfiguration] has changed.
     *
     * @param event a `PropertyChangeEvent` which specifies the name of the property which had its
     * value changed and the old and the new values of that property
     */
    private fun deviceConfigurationPropertyChange(event: PropertyChangeEvent) {
        val propertyName = event.propertyName

        /*
         * While the AUDIO_CAPTURE_DEVICE is sure to affect the DEFAULT_DEVICE, AUDIO_PLAYBACK_DEVICE is not.
         * Anyway, MediaDevice is supposed to represent the device to be used for capture AND
         * playback (though its current implementation MediaDeviceImpl may be incomplete with
         * respect to the playback representation). Since it is not clear at this point of the
         * execution whether AUDIO_PLAYBACK_DEVICE really affects the DEFAULT_DEVICE and for the
         * sake of completeness, throw in the changes to the AUDIO_NOTIFY_DEVICE as well.
         */
        if ((DeviceConfiguration.AUDIO_CAPTURE_DEVICE == propertyName)
                || (DeviceConfiguration.AUDIO_NOTIFY_DEVICE == propertyName)
                || (DeviceConfiguration.AUDIO_PLAYBACK_DEVICE == propertyName)
                || (DeviceConfiguration.VIDEO_CAPTURE_DEVICE == propertyName)) {
            /*
             * We do not know the old value of the property at the time of this writing. We cannot
             * report the new value either because we do not know the MediaType and the MediaUseCase.
             * cmeng (20210322): must not forward the received event values, otherwise toggle camera does not work.
             */
            firePropertyChange(MediaService.DEFAULT_DEVICE, null, null)
        }
    }

    /**
     * Initializes a new `RTPTranslator` which is to forward RTP and RTCP traffic between
     * multiple `MediaStream`s.
     *
     * @return a new `RTPTranslator` which is to forward RTP and RTCP traffic between
     * multiple `MediaStream`s
     * @see MediaService.createRTPTranslator
     */
    override fun createRTPTranslator(): RTPTranslator {
        return RTPTranslatorImpl()
    }

    /**
     * Returns a new [EncodingConfiguration] instance that can be used by other bundles.
     *
     * @return a new [EncodingConfiguration] instance.
     */
    override fun createEmptyEncodingConfiguration(): EncodingConfiguration {
        return EncodingConfigurationImpl()
    }

    /**
     * The listener which will be notified for changes in the video container. Whether the
     * container is displayable or not we will stop the player or start it.
     */
    private class VideoContainerHierarchyListener
    /**
     * Creates VideoContainerHierarchyListener.
     *
     * @param container the video container.
     * @param player the player.
     */
    (
            /**
             * The parent container of our preview.
             */
            private val container: JComponent,

            /**
             * The player showing the video preview.
             */
            private val player: Player,
    ) : HierarchyListener {
        /**
         * The parent window.
         */
        private var window: Window? = null

        /**
         * The listener for the parent window. Used to dispose player on close.
         */
        private var windowListener: WindowListener? = null

        /**
         * The preview component of the player, must be set once the player has been realized.
         */
        private var preview: Component? = null

        /**
         * After the player has been realized the preview can be obtained and supplied to this
         * listener. Normally done on player RealizeCompleteEvent.
         *
         * @param preview the preview.
         */
        fun setPreview(preview: Component?) {
            this.preview = preview
        }

        /**
         * Disposes player and cleans listeners as we will no longer need them.
         */
        fun dispose() {
            if (windowListener != null) {
                if (window != null) {
                    window!!.removeWindowListener(windowListener)
                    window = null
                }
                windowListener = null
            }
            container.removeHierarchyListener(this)
            disposePlayer(player)

            /*
             * We've just disposed the player which created the preview component so the preview
             * component is of no use regardless of whether the Media configuration form will be
             * redisplayed or not. And since the preview component appears to be a huge object even
             * after its player is disposed, make sure to not reference it.
             */
            if (preview != null) container.remove(preview)
        }

        /**
         * Change in container.
         *
         * @param event the event for the change.
         */
        fun hierarchyChanged(event: HierarchyEvent) {
            if (event.changeFlags and HierarchyEvent.DISPLAYABILITY_CHANGED == 0) return
            if (!container.isDisplayable) {
                dispose()
                return
            } else {
                // if this is just a change in the video container and preview has not been
                // created yet, do nothing otherwise start the player which will show in preview
                if (preview != null) {
                    player.start()
                }
            }
            if (windowListener == null) {
                window = SwingUtilities.windowForComponent(container)
                if (window != null) {
                    windowListener = object : WindowAdapter() {
                        override fun windowClosing(event: WindowEvent) {
                            dispose()
                        }
                    }
                    window!!.addWindowListener(windowListener)
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun createRecorderEventHandlerJson(filename: String?): RecorderEventHandler {
        return RecorderEventHandlerJSONImpl(filename)
    }

    companion object {
        /**
         * The name of the `boolean` `ConfigurationService` property which indicates
         * whether the detection of audio `CaptureDevice`s is to be disabled. The default value
         * is `false` i.e. the audio `CaptureDevice`s are detected.
         */
        private const val DISABLE_AUDIO_SUPPORT_PNAME = "media.DISABLE_AUDIO_SUPPORT"

        /**
         * The name of the `boolean` `ConfigurationService` property which indicates
         * whether the method [DeviceConfiguration.setAudioSystem] is to be
         * considered disabled for the user i.e. the user is not presented with user interface which
         * allows selecting a particular `AudioSystem`.
         */
        const val DISABLE_SET_AUDIO_SYSTEM_PNAME = "neomedia.audiosystem.DISABLED"

        /**
         * The name of the `boolean` `ConfigurationService` property which indicates
         * whether the detection of video `CaptureDevice`s is to be disabled. The default value
         * is `false` i.e. the video `CaptureDevice`s are detected.
         */
        private const val DISABLE_VIDEO_SUPPORT_PNAME = "media.DISABLE_VIDEO_SUPPORT"

        /**
         * The prefix of the property names the values of which specify the dynamic payload type preferences.
         */
        private const val DYNAMIC_PAYLOAD_TYPE_PREFERENCES_PNAME_PREFIX = "neomedia.dynamicPayloadTypePreferences"

        /**
         * The value of the `devices` property of `MediaServiceImpl` when no
         * `MediaDevice`s are available. Explicitly defined in order to reduce unnecessary allocations.
         */
        private val EMPTY_DEVICES = emptyList<MediaDevice>()

        /**
         * The name of the `System` boolean property which specifies whether the committing of
         * the JMF/FMJ `Registry` is to be disabled.
         */
        private const val JMF_REGISTRY_DISABLE_COMMIT = "JmfRegistry.disableCommit"

        /**
         * The name of the `System` boolean property which specifies whether the loading of the
         * JMF/FMJ `Registry` is to be disabled.
         */
        private const val JMF_REGISTRY_DISABLE_LOAD = "JmfRegistry.disableLoad"
        /**
         * Gets the indicator which determines whether the loading of the JMF/FMJ `Registry` has
         * been disabled.
         *
         * @return `true` if the loading of the JMF/FMJ `Registry` has been disabled
         * otherwise, `false`
         */
        /**
         * The indicator which determines whether the loading of the JMF/FMJ `Registry` is disabled.
         */
        var isJmfRegistryDisableLoad = false
            private set

        /**
         * The indicator which determined whether [.postInitializeOnce] has
         * been executed in order to perform one-time initialization after initializing the first
         * instance of `MediaServiceImpl`.
         */
        private var postInitializeOnce = false

        /**
         * The prefix that is used to store configuration for encodings preference.
         */
        private const val ENCODING_CONFIG_PROP_PREFIX = "neomedia.codec.EncodingConfiguration"

        /**
         * A [Map] that binds indicates whatever preferences this media service implementation
         * may have for the RTP payload type numbers that get dynamically assigned to
         * [MediaFormat]s with no static payload type. The method is useful for formats such as
         * "telephone-event" for example that is statically assigned the 101 payload type by some
         * legacy systems. Signalling protocol implementations such as SIP and XMPP should make sure
         * whenever this is possible, they assign to format the dynamic payload type returned in this [Map].
         */
        private var dynamicPayloadTypePreferences: MutableMap<MediaFormat, Byte>? = null

        /**
         * The volume control of the media service playback.
         */
        private var outputVolumeControl: VolumeControl? = null

        /**
         * The volume control of the media service capture.
         */
        private var inputVolumeControl: VolumeControl? = null

        init {
            setupFMJ()
        }

        /**
         * Listens and shows the video in the video container when needed.
         *
         * @param event the event when player has ready visual component.
         * @param videoContainer the container.
         * @param locator input DataSource locator
         * @param listener the hierarchy listener we created for the video container.
         */
        private fun controllerUpdateForPreview(
                event: ControllerEvent,
                videoContainer: JComponent, locator: MediaLocator, listener: VideoContainerHierarchyListener,
        ) {
            if (event is ConfigureCompleteEvent) {
                val player = event.getSourceController() as Processor

                /*
             * Use SwScale for the scaling since it produces an image with better quality and add
             * the "flip" effect to the video.
             */
                val trackControls = player.trackControls
                if ((trackControls != null) && (trackControls.isNotEmpty())) try {
                    for (trackControl: TrackControl in trackControls) {
                        val codecs: Array<Codec>
                        val scaler = SwScale()

                        // do not flip desktop
                        codecs = when (DeviceSystem.LOCATOR_PROTOCOL_IMGSTREAMING) {
                            locator.protocol -> arrayOf(scaler)
                            else -> arrayOf(HFlip(), scaler)
                        }
                        trackControl.setCodecChain(codecs)
                        break
                    }
                } catch (upiex: UnsupportedPlugInException) {
                    Timber.w(upiex, "Failed to add SwScale/VideoFlipEffect to codec chain")
                }

                // Turn the Processor into a Player.
                try {
                    player.contentDescriptor = null
                } catch (nce: NotConfiguredError) {
                    Timber.e(nce, "Failed to set ContentDescriptor of Processor")
                }
                player.realize()
            } else if (event is RealizeCompleteEvent) {
                val player = event.getSourceController() as Player
                val video = player.visualComponent

                // sets the preview to the listener
                listener.setPreview(video)
                showPreview(videoContainer, video, player)
            }
        }

        /**
         * Shows the preview panel.
         *
         * @param previewContainer the container
         * @param preview the preview component.
         * @param player the player.
         */
        private fun showPreview(previewContainer: JComponent, preview: Component?, player: Player) {
            if (!SwingUtilities.isEventDispatchThread()) {
                SwingUtilities.invokeLater { showPreview(previewContainer, preview, player) }
                return
            }
            previewContainer.removeAll()
            if (preview != null) {
                previewContainer.add(preview)
                player.start()
                if (previewContainer.isDisplayable) {
                    previewContainer.revalidate()
                    previewContainer.repaint()
                } else previewContainer.doLayout()
            } else disposePlayer(player)
        }

        /**
         * Dispose the player used for the preview.
         *
         * @param player the player.
         */
        private fun disposePlayer(player: Player) {
            // launch disposing preview player in separate thread will lock renderer and can produce
            // lock if user has quickly requested preview component and can lock ui thread
            Thread {
                player.stop()
                player.deallocate()
                player.close()
            }.start()
        }

        /**
         * Performs one-time initialization after initializing the first instance of `MediaServiceImpl`.
         *
         * @param mediaServiceImpl the `MediaServiceImpl` instance which has caused the need to perform the
         * one-time initialization
         */
        private fun postInitializeOnce(mediaServiceImpl: MediaServiceImpl) {
            /*
         * Some SecureRandom() implementations like SHA1PRNG call /dev/random to seed themselves
         * on first use. Call SecureRandom early to avoid blocking when establishing
         * a connection for example.
         */
            val rnd = SecureRandom()
            val b = ByteArray(20)
            rnd.nextBytes(b)
            Timber.d("Warming up SecureRandom completed.")
        }

        /**
         * Sets up FMJ for execution. For example, sets properties which instruct FMJ whether it is to
         * create a log, where the log is to be created.
         */
        private fun setupFMJ() {
            /*
         * FMJ now uses java.util.logging.Logger, but only logs if allowLogging is set in its
         * registry. Since the levels can be configured through properties for the
         * net.sf.fmj.media.Log class, we always enable this (as opposed to only enabling it when
         * this.logger has debug enabled).
         */
            Registry.set("allowLogging", true)

            // ### cmeng - user only fmj codec (+ our custom codec)? - have problem
            // if only set for FMJ option (13 Nov 2015)
            // RegistryDefaults.setDefaultFlags(RegistryDefaults.FMJ);

            /*
         * Disable the loading of .fmj.registry because Kertesz Laszlo has reported that audio
         * input devices duplicate after restarting Jitsi. Besides, Jitsi does not really need
         * .fmj.registry on startup.
         */
            if (System.getProperty(JMF_REGISTRY_DISABLE_LOAD) == null) System.setProperty(JMF_REGISTRY_DISABLE_LOAD, "true")
            isJmfRegistryDisableLoad = "true".equals(System.getProperty(JMF_REGISTRY_DISABLE_LOAD), ignoreCase = true)
            if (System.getProperty(JMF_REGISTRY_DISABLE_COMMIT) == null) System.setProperty(JMF_REGISTRY_DISABLE_COMMIT, "true")

            val scHomeDirLocation = System.getProperty(ConfigurationService.PNAME_SC_CACHE_DIR_LOCATION)
            if (scHomeDirLocation != null) {
                val scHomeDirName = System.getProperty(ConfigurationService.PNAME_SC_HOME_DIR_NAME)
                if (scHomeDirName != null) {
                    val scHomeDir = File(scHomeDirLocation, scHomeDirName)

                    /* Write FMJ's log in Jitsi's log directory. */
                    Registry.set("secure.logDir", File(scHomeDir, "log").path)

                    /* Write FMJ's registry in Jitsi's user data directory. */
                    val jmfRegistryFilename = "JmfRegistry.filename"
                    if (System.getProperty(jmfRegistryFilename) == null) {
                        System.setProperty(jmfRegistryFilename, File(scHomeDir, ".fmj.registry").absolutePath)
                    }
                }
            }
            var enableFfmpeg = true
            val cfg = configurationService
            if (cfg != null) {
                enableFfmpeg = cfg.getBoolean(MediaService.ENABLE_FFMPEG_CODECS_PNAME, enableFfmpeg)
                for (prop: String in cfg.getPropertyNamesByPrefix("neomedia.adaptive_jitter_buffer", true)) {
                    val suffix = prop.substring(prop.lastIndexOf(".") + 1)
                    Registry.set("adaptive_jitter_buffer_$suffix", cfg.getString(prop))
                }
            }
            registerCustomPackages()
            registerCustomCodecs(enableFfmpeg)
            registerCustomMultiplexers()
        }

        /**
         * Determines whether the support for a specific `MediaType` is enabled. The
         * `ConfigurationService` and `System` properties
         * [.DISABLE_AUDIO_SUPPORT_PNAME] and [.DISABLE_VIDEO_SUPPORT_PNAME] allow
         * disabling the support for, respectively, [MediaType.AUDIO] and [MediaType.VIDEO].
         *
         * @param mediaType the `MediaType` to be determined whether the support for it is enabled
         * @return `true` if the support for the specified `mediaType` is enabled; otherwise, `false`
         */
        fun isMediaTypeSupportEnabled(mediaType: MediaType?): Boolean {
            val propertyName = when (mediaType) {
                MediaType.AUDIO -> DISABLE_AUDIO_SUPPORT_PNAME
                MediaType.VIDEO -> DISABLE_VIDEO_SUPPORT_PNAME
                else -> return true
            }
            val cfg = configurationService
            return (!cfg.getBoolean(propertyName, false)) && !java.lang.Boolean.getBoolean(propertyName)
        }
    }
}