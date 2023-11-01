/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.impl.neomedia.audiolevel.AudioLevelEventDispatcher
import org.atalk.impl.neomedia.audiolevel.AudioLevelMap
import org.atalk.impl.neomedia.conference.AudioMixer
import org.atalk.impl.neomedia.conference.AudioMixingPushBufferDataSource
import org.atalk.impl.neomedia.conference.DataSourceFilter
import org.atalk.impl.neomedia.protocol.PushBufferDataSourceDelegate
import org.atalk.impl.neomedia.protocol.TranscodingDataSource
import org.atalk.service.neomedia.MediaDirection
import org.atalk.service.neomedia.QualityPreset
import org.atalk.service.neomedia.RTPExtension
import org.atalk.service.neomedia.VolumeControl
import org.atalk.service.neomedia.codec.EncodingConfiguration
import org.atalk.service.neomedia.device.MediaDevice
import org.atalk.service.neomedia.device.MediaDeviceWrapper
import org.atalk.service.neomedia.event.SimpleAudioLevelListener
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.util.MediaType
import timber.log.Timber
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import javax.media.Buffer
import javax.media.Player
import javax.media.Processor
import javax.media.Renderer
import javax.media.control.TrackControl
import javax.media.protocol.DataSource
import javax.media.protocol.PushBufferStream
import javax.media.rtp.ReceiveStream

/**
 * Implements a `MediaDevice` which performs audio mixing using [AudioMixer].
 *
 * @author Lyubomir Marinov
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
open class AudioMixerMediaDevice(device: AudioMediaDeviceImpl) : AbstractMediaDevice(), MediaDeviceWrapper {
    /**
     * The `AudioMixer` which performs audio mixing in this `MediaDevice` (and rather
     * the session that it represents).
     */
    private var audioMixer: AudioMixer? = null

    /**
     * The actual `AudioMediaDeviceImpl` wrapped by this instance for the purposes of audio
     * mixing and used by [.audioMixer] as its `CaptureDevice`.
     */
    private val device: AudioMediaDeviceImpl

    /**
     * The `MediaDeviceSession` of this `AudioMixer` with [.device].
     */
    private var deviceSession: AudioMixerMediaDeviceSession? = null

    /**
     * The `SimpleAudioLevelListener` which is registered (or is to be registered) with
     * [.localUserAudioLevelDispatcher] and which delivers each of the audio level changes to
     * [.localUserAudioLevelListeners].
     */
    private val localUserAudioLevelDelegate = object : SimpleAudioLevelListener {
        override fun audioLevelChanged(level: Int) {
            lastMeasuredLocalUserAudioLevel = level
            fireLocalUserAudioLevelChanged(level)
        }
    }

    /**
     * The dispatcher that delivers to listeners calculations of the local audio level.
     */
    private val localUserAudioLevelDispatcher = AudioLevelEventDispatcher(
            "Local User Audio Level Dispatcher (Mixer Edition)")

    /**
     * The `List` where we store all listeners interested in changes of the local audio
     * level and the number of times each one of them has been added. We wrap listeners because
     * we may have multiple subscriptions with the same listener and we would only store it once.
     * If one of the multiple subscriptions of a particular listener is removed, however, we
     * wouldn't want to reset the listener to `null` as there are others still interested,
     * and hence the `referenceCount` in the wrapper.
     *
     *
     * **Note**: `localUserAudioLevelListeners` is a copy-on-write storage and access to
     * it is synchronized by [.localUserAudioLevelListenersSyncRoot].
     *
     */
    private var localUserAudioLevelListeners = ArrayList<SimpleAudioLevelListenerWrapper>()

    /**
     * The `Object` which synchronizes the access to [.localUserAudioLevelListeners].
     */
    private val localUserAudioLevelListenersSyncRoot = Any()

    /**
     * The levels map that we use to cache last measured audio levels for all streams associated with this mixer.
     */
    private val audioLevelCache = AudioLevelMap()

    /**
     * The most recently measured level of the locally captured audio stream.
     */
    private var lastMeasuredLocalUserAudioLevel = 0

    /**
     * The `List` of RTP extensions supported by this device (at the time of writing this
     * list is only filled for audio devices and is `null` otherwise).
     */
    private var rtpExtensions: MutableList<RTPExtension>? = null

    /**
     * The `Map` where we store audio level dispatchers and the streams they are interested
     * in.
     */
    private val streamAudioLevelListeners = HashMap<ReceiveStream, AudioLevelEventDispatcher>()

    /**
     * The `ReceiveStreamBufferListener` which gets notified when this
     * `MediaDevice` reads from the `CaptureDevice` to the `AudioMixer`
     */
    private var receiveStreamBufferListener: ReceiveStreamBufferListener? = null

    /**
     * Initializes a new `AudioMixerMediaDevice` instance which is to enable audio mixing on
     * a specific `AudioMediaDeviceImpl` .
     *
     * device the `AudioMediaDeviceImpl` which the new instance is to enable audio mixing on
     */
    init {
        /*
         * AudioMixer is initialized with a CaptureDevice so we have to be sure that the wrapped
         * device can provide one.
         */
        require(device.direction.allowsSending()) { "device must be able to capture" }
        this.device = device
    }

    /**
     * Connects to a specific `CaptureDevice` given in the form of a `DataSource`.
     *
     * @param captureDevice the `CaptureDevice` to be connected to
     * @throws IOException if anything wrong happens while connecting to the specified `captureDevice`
     * @see AbstractMediaDevice.connect
     */
    @Throws(IOException::class)
    override fun connect(captureDevice: DataSource) {
        var cDevice = captureDevice
        val effectiveCaptureDevice = cDevice

        /*
         * Unwrap wrappers of the captureDevice until AudioMixingPushBufferDataSource is found.
         */
        if (cDevice is PushBufferDataSourceDelegate<*>) cDevice = cDevice.dataSource

        /*
         * AudioMixingPushBufferDataSource is definitely not a CaptureDevice and does not need the
         * special connecting defined by AbstractMediaDevice and MediaDeviceImpl.
         */
        if (cDevice is AudioMixingPushBufferDataSource) effectiveCaptureDevice.connect() else device.connect(effectiveCaptureDevice)
    }

    /**
     * Creates a `DataSource` instance for this `MediaDevice` which gives access to
     * the captured media.
     *
     * @return a `DataSource` instance which gives access to the media captured by this
     * `MediaDevice`
     * @see AbstractMediaDevice.createOutputDataSource
     */
    override fun createOutputDataSource(): AudioMixingPushBufferDataSource {
        return getAudioMixer().createOutDataSource()
    }

    /**
     * {@inheritDoc}
     *
     *
     * Delegates to the [AbstractMediaDevice.createPlayer] implementation of the
     * `MediaDevice` on which this instance enables mixing i.e. [.getWrappedDevice].
     */
    @Throws(Exception::class)
    override fun createPlayer(dataSource: DataSource): Processor? {
        return device.createPlayer(dataSource)
    }

    /**
     * {@inheritDoc}
     *
     *
     * Delegates to the [AbstractMediaDevice.createRenderer] implementation of the
     * `MediaDevice` on which this instance enables mixing i.e. [.getWrappedDevice].
     */
    override fun createRenderer(): Renderer? {
        return device.createRenderer()
    }

    /**
     * Creates a new `MediaDeviceSession` instance which is to represent the use of this
     * `MediaDevice` by a `MediaStream`.
     *
     * @return a new `MediaDeviceSession` instance which is to represent the use of this
     * `MediaDevice` by a `MediaStream`
     * @see AbstractMediaDevice.createSession
     */
    @Synchronized
    override fun createSession(): MediaDeviceSession {
        if (deviceSession == null) deviceSession = AudioMixerMediaDeviceSession()
        return MediaStreamMediaDeviceSession(deviceSession!!)
    }

    /**
     * Notifies the `SimpleAudioLevelListener`s registered with this instance about the
     * new/current audio level of the local media stream.
     *
     * @param level the new/current audio level of the local media stream.
     */
    private fun fireLocalUserAudioLevelChanged(level: Int) {
        var localUserAudioLevelListeners: List<SimpleAudioLevelListenerWrapper>
        synchronized(localUserAudioLevelListenersSyncRoot) {
            /*
             * It is safe to not copy the localUserAudioLevelListeners of this instance here
             * because it is a copy-on-write storage.
             */
            localUserAudioLevelListeners = this.localUserAudioLevelListeners
        }

        /*
         * XXX These events are going to happen veeery often (~50 times per sec) and we'd like to
         * avoid creating an iterator every time.
         */
        val localUserAudioLevelListenerCount = localUserAudioLevelListeners.size
        for (i in 0 until localUserAudioLevelListenerCount) {
            localUserAudioLevelListeners[i].listener.audioLevelChanged(level)
        }
    }

    /**
     * Gets the `AudioMixer` which performs audio mixing in this `MediaDevice`
     * (and rather the session it represents). If it still does not exist, it is created.
     *
     * @return the `AudioMixer` which performs audio mixing in this `MediaDevice`
     * (and rather the session it represents)
     */
    @Synchronized
    private fun getAudioMixer(): AudioMixer {
        if (audioMixer == null) {
            audioMixer = object : AudioMixer(device.createCaptureDevice()) {
                @Throws(IOException::class)
                override fun connect(dataSource: DataSource, inputDataSource: DataSource) {
                    /*
                     * CaptureDevice needs special connecting as defined by AbstractMediaDevice
                     * and, especially, MediaDeviceImpl.
                     */
                    if (inputDataSource === captureDevice) this@AudioMixerMediaDevice.connect(dataSource) else super.connect(dataSource, inputDataSource)
                }

                @Throws(IOException::class)
                override fun read(stream: PushBufferStream, buffer: Buffer, dataSource: DataSource) {
                    super.read(stream, buffer, dataSource)

                    /*
                     * XXX The audio read from the specified stream has not been made available to
                     * the mixing yet. Slow code here is likely to degrade the performance of the
                     * whole mixer.
                     */
                    if (dataSource === captureDevice) {
                        /*
                         * The audio of the very CaptureDevice to be contributed to the mix.
                         */
                        synchronized(localUserAudioLevelListenersSyncRoot) { if (localUserAudioLevelListeners.isEmpty()) return }
                        localUserAudioLevelDispatcher.addData(buffer)
                    } else if (dataSource is ReceiveStreamPushBufferDataSource) {
                        /*
                         * The audio of a ReceiveStream to be contributed to the mix.
                         */
                        val receiveStream = dataSource.receiveStream
                        var streamEventDispatcher: AudioLevelEventDispatcher?
                        synchronized(streamAudioLevelListeners) { streamEventDispatcher = streamAudioLevelListeners[receiveStream] }
                        if (streamEventDispatcher != null && !buffer.isDiscard && buffer.length > 0 && buffer.data != null) {
                            streamEventDispatcher!!.addData(buffer)
                        }
                        val receiveStreamBufferListener = receiveStreamBufferListener
                        if ((receiveStreamBufferListener != null
                                        && !buffer.isDiscard) && buffer.length > 0 && buffer.data != null) {
                            receiveStreamBufferListener.bufferReceived(receiveStream, buffer)
                        }
                    }
                }
            }
        }
        return audioMixer!!
    }

    /**
     * Returns the `MediaDirection` supported by this device.
     *
     * @return [MediaDirection.SENDONLY] if this is a read-only device,
     * [MediaDirection.RECVONLY] if this is a write-only device or
     * [MediaDirection.SENDRECV] if this `MediaDevice` can both capture and
     * render media
     * @see MediaDevice.direction
     */
    override val direction: MediaDirection
        get() = device.direction

    /**
     * Gets the `MediaFormat` in which this <t>MediaDevice captures media.
     *
     * @return the `MediaFormat` in which this `MediaDevice` captures media
     * @see MediaDevice.format
    </t> */
    override val format: MediaFormat
        get() = device.format!!

    /**
     * Gets the `MediaType` that this device supports.
     *
     * @return [MediaType.AUDIO] if this is an audio device or [MediaType.VIDEO] if this is a video device
     * @see MediaDevice.mediaType
     */
    override val mediaType: MediaType
        get() = device.mediaType// can't happen since CSRC_AUDIO_LEVEL_URN is a valid URI and never changes.

    /**
     * Returns a `List` containing (at the time of writing) a single extension descriptor
     * indicating `SENDRECV` for mixer-to-client audio levels.
     *
     * @return a `List` containing the `CSRC_AUDIO_LEVEL_URN` extension descriptor.
     */
    override val supportedExtensions: List<RTPExtension>
        get() {
            if (rtpExtensions == null) {
                rtpExtensions = ArrayList<RTPExtension>(2)
                var csrcAudioLevelURN: URI?
                var ssrcAudioLevelURN: URI?
                try {
                    csrcAudioLevelURN = URI(RTPExtension.CSRC_AUDIO_LEVEL_URN)
                    ssrcAudioLevelURN = URI(RTPExtension.SSRC_AUDIO_LEVEL_URN)
                } catch (e: URISyntaxException) {
                    // can't happen since CSRC_AUDIO_LEVEL_URN is a valid URI and never changes.
                    csrcAudioLevelURN = null
                    ssrcAudioLevelURN = null
                    Timber.i(e, "Aha! Someone messed with the source!")
                }
                if (csrcAudioLevelURN != null) {
                    rtpExtensions!!.add(RTPExtension(csrcAudioLevelURN, MediaDirection.SENDRECV))
                }
                if (ssrcAudioLevelURN != null) {
                    rtpExtensions!!.add(RTPExtension(ssrcAudioLevelURN, MediaDirection.SENDRECV))
                }
            }
            return rtpExtensions!!
        }

    /**
     * Gets the list of `MediaFormat`s supported by this `MediaDevice`.
     *
     * @param localPreset not used
     * @param remotePreset not used
     * @return the list of `MediaFormat`s supported by this `MediaDevice`
     * @see MediaDevice.getSupportedFormats
     */
    override fun getSupportedFormats(localPreset: QualityPreset?, remotePreset: QualityPreset?): List<MediaFormat> {
        return device.supportedFormats
    }

    /**
     * Set the listener which gets notified when this `MediaDevice` reads data from a `ReceiveStream`
     *
     * @param listener the `ReceiveStreamBufferListener` which gets notified
     */
    fun setReceiveStreamBufferListener(listener: ReceiveStreamBufferListener?) {
        receiveStreamBufferListener = listener
    }

    /**
     * Gets the list of `MediaFormat`s supported by this `MediaDevice` and enabled in
     * `encodingConfiguration`.
     *
     * @param localPreset not used
     * @param remotePreset not used
     * @param encodingConfiguration the `EncodingConfiguration` instance to use
     * @return the list of `MediaFormat`s supported by this `MediaDevice` and enabled in `encodingConfiguration`.
     * @see MediaDevice.getSupportedFormats
     */
    override fun getSupportedFormats(
            localPreset: QualityPreset?,
            remotePreset: QualityPreset?, encodingConfiguration: EncodingConfiguration?,
    ): List<MediaFormat> {
        return device.getSupportedFormats(encodingConfiguration)
    }

    /**
     * Gets the actual `MediaDevice` which this `MediaDevice` is effectively built on
     * top of and forwarding to.
     *
     * @return the actual `MediaDevice` which this `MediaDevice` is effectively built
     * on top of and forwarding to
     * @see MediaDeviceWrapper.wrappedDevice
     */
    override val wrappedDevice: MediaDevice
        get() = device

    /**
     * Removes the `DataSource` accepted by a specific `DataSourceFilter` from the
     * list of input `DataSource` of the `AudioMixer` of this
     * `AudioMixerMediaDevice` from which it reads audio to be mixed.
     *
     * @param dataSourceFilter the `DataSourceFilter` which selects the `DataSource`s to be removed
     */
    private fun removeInputDataSources(dataSourceFilter: DataSourceFilter) {
        this.audioMixer?.removeInDataSources(dataSourceFilter)
    }

    /**
     * Represents the one and only `MediaDeviceSession` with the `MediaDevice` of this `AudioMixer`
     */
    private inner class AudioMixerMediaDeviceSession
    /**
     * Initializes a new `AudioMixingMediaDeviceSession` which is to represent the
     * `MediaDeviceSession` of this `AudioMixer` with its `MediaDevice`
     */
        : MediaDeviceSession(this@AudioMixerMediaDevice) {

        /**
         * The list of `MediaDeviceSession`s of `MediaStream`s which use this `AudioMixer`.
         */
        private val mediaStreamMediaDeviceSessions = LinkedList<MediaStreamMediaDeviceSession>()

        /**
         * The `VolumeControl` which is to control the volume (level) of the audio (to be)
         * played back by this instance.
         */
        private var outputVolumeControl: VolumeControl? = null

        /**
         * Adds `l` to the list of listeners that are being notified of new local audio
         * levels as they change. If `l` is added multiple times it would only be registered  once.
         *
         * @param l the listener we'd like to add.
         */
        fun addLocalUserAudioLevelListener(l: SimpleAudioLevelListener?) {
            // If the listener is null, we have nothing more to do here.
            if (l == null) return
            synchronized(localUserAudioLevelListenersSyncRoot) {

                // if this is the first listener that we are seeing then we also
                // need to create the dispatcher.
                if (localUserAudioLevelListeners.isEmpty()) {
                    localUserAudioLevelDispatcher.setAudioLevelListener(localUserAudioLevelDelegate)
                }

                // check if this listener has already been added.
                var wrapper = SimpleAudioLevelListenerWrapper(l)
                val index = localUserAudioLevelListeners.indexOf(wrapper)
                if (index != -1) {
                    wrapper = localUserAudioLevelListeners[index]
                    wrapper.referenceCount++
                } else {
                    /*
                     * XXX localUserAudioLevelListeners must be a copy-on-write storage so that
                     * firing events to its SimpleAudioLevelListeners can happen outside a block
                     * synchronized by localUserAudioLevelListenersSyncRoot and thus reduce the
                     * chances for a deadlock (which was, otherwise, observed in practice).
                     */
                    localUserAudioLevelListeners = ArrayList(localUserAudioLevelListeners)
                    localUserAudioLevelListeners.add(wrapper)
                }
            }
        }

        /**
         * Adds a specific `MediaStreamMediaDeviceSession` to the mix represented by this
         * instance so that it knows when it is in use.
         *
         * @param mediaStreamMediaDeviceSession the `MediaStreamMediaDeviceSession` to be added to the mix represented by
         * this instance
         */
        fun addMediaStreamMediaDeviceSession(
                mediaStreamMediaDeviceSession: MediaStreamMediaDeviceSession?,
        ) {
            if (mediaStreamMediaDeviceSession == null) throw NullPointerException("mediaStreamMediaDeviceSession")
            synchronized(mediaStreamMediaDeviceSessions) { if (!mediaStreamMediaDeviceSessions.contains(mediaStreamMediaDeviceSession)) mediaStreamMediaDeviceSessions.add(mediaStreamMediaDeviceSession) }
        }

        /**
         * Adds a specific `DataSource` providing remote audio to the mix produced by the associated `MediaDevice`.
         *
         * @param playbackDataSource the `DataSource` providing remote audio to be added to the mix produced by
         * the associated `MediaDevice`
         */
        override fun addPlaybackDataSource(playbackDataSource: DataSource) {
            /*
             * We don't play back the contributions of the conference members separately, we have a
             * single playback of the mix of all contributions but ours.
             */
            super.addPlaybackDataSource(getCaptureDevice()!!)
        }

        /**
         * Adds a specific `ReceiveStream` to the list of `ReceiveStream`s known to
         * this instance to be contributing audio to the mix produced by its associated `AudioMixer`.
         *
         * @param receiveStream the `ReceiveStream` to be added to the list of `ReceiveStream`s known
         * to this instance to be contributing audio to the mix produced by its associated `AudioMixer`
         */
        override fun addReceiveStream(receiveStream: ReceiveStream) {
            addSSRC(0xFFFFFFFFL and receiveStream.ssrc)
        }

        /**
         * Creates the `DataSource` that this instance is to read captured media from. Since
         * this is the `MediaDeviceSession` of this `AudioMixer` with its
         * `MediaDevice`, returns the `localOutputDataSource` of the `AudioMixer`
         * i.e. the `DataSource` which represents the mix of all `ReceiveStream`s
         * and excludes the captured data from the `MediaDevice` of the `AudioMixer`.
         *
         * @return the `DataSource` that this instance is to read captured media from
         * @see MediaDeviceSession.createCaptureDevice
         */
        override fun createCaptureDevice(): DataSource {
            return getAudioMixer().localOutDataSource
        }

        /**
         * {@inheritDoc}
         */
        override fun createPlayer(dataSource: DataSource): Player {
            /*
             * TODO AudioMixerMediaDevice wraps a MediaDevice so AudioMixerMediaDeviceSession
             * should wrap a MediaDeviceSession of that same wrapped MediaDevice.
             */
            return super.createPlayer(dataSource)!!
        }

        /**
         * Sets the `VolumeControl` which is to control the volume (level) of the audio (to
         * be) played back by this instance.
         *
         * @param outputVolumeControl the `VolumeControl` which is to be control the volume (level) of the audio
         * (to be) played back by this instance
         */
        fun setOutputVolumeControl(outputVolumeControl: VolumeControl?) {
            this.outputVolumeControl = outputVolumeControl
        }

        /**
         * Sets `listener` as the list of listeners that will receive notifications of audio
         * level event changes in the data arriving from `stream`.
         *
         * @param stream the stream that `l` would like to register as an audio level listener for.
         * @param listener the listener we'd like to register for notifications from `stream`.
         */
        fun setStreamAudioLevelListener(stream: ReceiveStream, listener: SimpleAudioLevelListener?) {
            synchronized(streamAudioLevelListeners) {
                var dispatcher = streamAudioLevelListeners[stream]
                if (listener == null) {
                    if (dispatcher != null) {
                        try {
                            dispatcher.setAudioLevelListener(null)
                            dispatcher.setAudioLevelCache(null, -1)
                        } finally {
                            streamAudioLevelListeners.remove(stream)
                        }
                    }
                } else {
                    if (dispatcher == null) {
                        dispatcher = AudioLevelEventDispatcher(
                                "Stream Audio Level Dispatcher (Mixer Edition)")
                        dispatcher.setAudioLevelCache(audioLevelCache, 0xFFFFFFFFL and stream.ssrc)
                        streamAudioLevelListeners[stream] = dispatcher
                    }
                    dispatcher.setAudioLevelListener(listener)
                }
            }
        }

        /**
         * {@inheritDoc}
         *
         *
         * Overrides the super implementation in order to configure the `VolumeControl` of
         * the returned `Renderer` for the purposes of having call/telephony
         * conference-specific volume (levels).
         */
        override fun createRenderer(player: Player, trackControl: TrackControl): Renderer? {
            val renderer = super.createRenderer(player, trackControl)
            if (renderer != null) {
                AudioMediaDeviceSession.setVolumeControl(renderer, outputVolumeControl)
            }
            return renderer
        }

        /**
         * Removes `l` from the list of listeners that are being notified of local audio
         * levels.If `l` is not in the list, the method has no effect.
         *
         * @param l the listener we'd like to remove.
         */
        fun removeLocalUserAudioLevelListener(l: SimpleAudioLevelListener) {
            synchronized(localUserAudioLevelListenersSyncRoot) {

                // check if this listener has already been added.
                val index = localUserAudioLevelListeners.indexOf(SimpleAudioLevelListenerWrapper(l))
                if (index != -1) {
                    val wrapper = localUserAudioLevelListeners[index]
                    if (wrapper.referenceCount > 1) wrapper.referenceCount-- else {
                        /*
                         * XXX localUserAudioLevelListeners must be a copy-on-write storage so that
                         * firing events to its SimpleAudioLevelListeners can happen outside a
                         * block
                         * synchronized by localUserAudioLevelListenersSyncRoot and thus reduce the
                         * chances for a deadlock (whic was, otherwise, observed in practice).
                         */
                        localUserAudioLevelListeners = ArrayList(localUserAudioLevelListeners)
                        localUserAudioLevelListeners.remove(wrapper)
                    }
                }

                // if this was the last listener then we also need to remove the dispatcher
                if (localUserAudioLevelListeners.isEmpty()) localUserAudioLevelDispatcher.setAudioLevelListener(null)
            }
        }

        /**
         * Removes a specific `MediaStreamMediaDeviceSession` from the mix represented by
         * this instance. When the last `MediaStreamMediaDeviceSession` is removed from this
         * instance, it is no longer in use and closes itself thus signaling to its
         * `MediaDevice` that it is no longer in use.
         *
         * @param mediaStreamMediaDeviceSession the `MediaStreamMediaDeviceSession` to be removed from the mix represented
         * by this instance
         */
        fun removeMediaStreamMediaDeviceSession(
                mediaStreamMediaDeviceSession: MediaStreamMediaDeviceSession?,
        ) {
            if (mediaStreamMediaDeviceSession != null) {
                synchronized(mediaStreamMediaDeviceSessions) {
                    if (mediaStreamMediaDeviceSessions.remove(mediaStreamMediaDeviceSession)
                            && mediaStreamMediaDeviceSessions.isEmpty()) close(MediaDirection.SENDRECV)
                }
            }
        }

        /**
         * Removes a specific `DataSource` providing remote audio from the mix produced by the associated `AudioMixer`.
         *
         * @param playbackDataSource the `DataSource` providing remote audio to be removed from the mix produced
         * by the associated `AudioMixer`
         */
        override fun removePlaybackDataSource(playbackDataSource: DataSource) {
            removeInputDataSources(object : DataSourceFilter {
                override fun accept(dataSource: DataSource?): Boolean {
                    return dataSource == playbackDataSource
                }
            })
        }

        /**
         * Removes a specific `ReceiveStream` from the list of `ReceiveStream`s known to this
         * instance to be contributing audio to the mix produced by its associated `AudioMixer`.
         *
         * @param receiveStream the `ReceiveStream` to be removed from the list of `ReceiveStream`s
         * known to this instance to be contributing audio to the mix produced by its
         * associated `AudioMixer`
         */
        override fun removeReceiveStream(receiveStream: ReceiveStream) {
            val ssrc = 0xFFFFFFFFL and receiveStream.ssrc
            removeSSRC(ssrc)

            // make sure we no longer cache levels for that stream.
            audioLevelCache.removeLevel(ssrc)
        }
    }

    /**
     * Represents the work of a `MediaStream` with the `MediaDevice` of an
     * `AudioMixer` and the contribution of that `MediaStream` to the mix.
     */
    private class MediaStreamMediaDeviceSession(
            /**
             * The `MediaDeviceSession` of the `AudioMixer` that this instance exposes to
             * a `MediaStream`. While there are multiple
             * `MediaStreamMediaDeviceSession`s each servicing a specific
             * `MediaStream`, they all share and delegate to one and the same
             * `AudioMixerMediaDeviceSession` so that they all contribute to the mix.
            `` */
            private val audioMixerMediaDeviceSession: AudioMixerMediaDeviceSession,
    ) : AudioMediaDeviceSession(audioMixerMediaDeviceSession.device), PropertyChangeListener {
        /**
         * We use this field to keep a reference to the listener that we've registered with the
         * audio mixer for local audio level notifications. We use this reference so that we could
         * unregister it if someone resets it or sets it to `null`.
         */
        private var localUserAudioLevelListener: SimpleAudioLevelListener? = null

        /**
         * We use this field to keep a reference to the listener that we've registered with the
         * audio mixer for stream audio level notifications. We use this reference so because at
         * the time we get it from the `MediaStream` it might be too early to register it with
         * the mixer as it is like that we don't have a receive stream yet. If that's the case, we
         * hold on to the listener and register it only when we get the `ReceiveStream`.
         */
        private var streamAudioLevelListener: SimpleAudioLevelListener? = null

        /**
         * The `Object` that we use to lock operations on `streamAudioLevelListener`.
         */
        private val streamAudioLevelListenerLock = Any()

        /**
         * Initializes a new `MediaStreamMediaDeviceSession` which is to represent the work of a
         * `MediaStream` with the `MediaDevice` of this `AudioMixer` and its contribution to the mix.
         *
         * audioMixerMediaDeviceSession the `MediaDeviceSession` of the `AudioMixer` with its
         * `MediaDevice` which the new instance is to delegate to in order to
         * contribute to the mix
         */
        init {
            audioMixerMediaDeviceSession.addMediaStreamMediaDeviceSession(this)
            audioMixerMediaDeviceSession.addPropertyChangeListener(this)
        }

        /**
         * Releases the resources allocated by this instance in the course of its execution and
         * prepares it to be garbage collected.
         *
         * @see MediaDeviceSession.close
         */
        override fun close(direction: MediaDirection?) {
            try {
                super.close(direction)
            } finally {
                audioMixerMediaDeviceSession.removeMediaStreamMediaDeviceSession(this)
            }
        }

        /**
         * Creates a new `Player` for a specific `DataSource` so that it is played
         * back on the `MediaDevice` represented by this instance.
         *
         * @param dataSource the `DataSource` to create a new `Player` for
         * @return a new `Player` for the specified `dataSource`
         * @see MediaDeviceSession.createPlayer
         */
        override fun createPlayer(dataSource: DataSource): Player? {
            /*
             * We don't want the contribution of each conference member played back separately, we
             * want the one and only mix of all contributions but ours to be played back once for all of them.
             */
            return null
        }

        /**
         * Returns the list of SSRC identifiers that are directly contributing to the media flows
         * that we are sending out. Note that since this is a pseudo device we would simply be
         * delegating the call to the corresponding method of the master mixer device session.
         *
         * @return a `long[]` array of SSRC identifiers that are currently contributing to
         * the mixer encapsulated by this device session.
         */
        override var remoteSSRCList: LongArray? = null
            get() {
                return audioMixerMediaDeviceSession.remoteSSRCList
            }

        /**
         * Notifies this `MediaDeviceSession` that a `DataSource` has been added for
         * playback on the represented `MediaDevice`.
         *
         * @param playbackDataSource the `DataSource` which has been added for playback on the represented `MediaDevice`
         * @see MediaDeviceSession.playbackDataSourceAdded
         */
        override fun playbackDataSourceAdded(playbackDataSource: DataSource) {
            super.playbackDataSourceAdded(playbackDataSource)
            var captureDevice = getCaptureDevice()

            /*
             * Unwrap wrappers of the captureDevice until AudioMixingPushBufferDataSource is found.
             */
            if (captureDevice is PushBufferDataSourceDelegate<*>) captureDevice = captureDevice.dataSource
            if (captureDevice is AudioMixingPushBufferDataSource) captureDevice
                    .addInDataSource(playbackDataSource)
            audioMixerMediaDeviceSession.addPlaybackDataSource(playbackDataSource)
        }

        /**
         * Notifies this `MediaDeviceSession` that a `DataSource` has been removed
         * from playback on the represented `MediaDevice`.
         *
         * @param playbackDataSource the `DataSource` which has been removed from playback on the represented `MediaDevice`
         * @see MediaDeviceSession.playbackDataSourceRemoved
         */
        override fun playbackDataSourceRemoved(playbackDataSource: DataSource) {
            super.playbackDataSourceRemoved(playbackDataSource)
            audioMixerMediaDeviceSession.removePlaybackDataSource(playbackDataSource)
        }

        /**
         * Notifies this `MediaDeviceSession` that a `DataSource` has been updated.
         *
         * @param playbackDataSource the `DataSource` which has been updated.
         * @see MediaDeviceSession.playbackDataSourceUpdated
         */
        override fun playbackDataSourceUpdated(playbackDataSource: DataSource) {
            super.playbackDataSourceUpdated(playbackDataSource)
            var captureDevice = getCaptureDevice()

            /*
             * Unwrap wrappers of the captureDevice until AudioMixingPushBufferDataSource is found.
             */
            if (captureDevice is PushBufferDataSourceDelegate<*>) captureDevice = captureDevice.dataSource
            if (captureDevice is AudioMixingPushBufferDataSource) {
                captureDevice.updateInDataSource(playbackDataSource)
            }
        }

        /**
         * The method relays `PropertyChangeEvent`s indicating a change in the SSRC_LIST in
         * the encapsulated mixer device so that the `MediaStream` that uses this device
         * session can update its CSRC list.
         *
         * @param evt that `PropertyChangeEvent` whose old and new value we will be relaying to the stream.
         */
        override fun propertyChange(evt: PropertyChangeEvent) {
            if (SSRC_LIST == evt.propertyName) {
                firePropertyChange(SSRC_LIST, evt.oldValue, evt.newValue)
            }
        }

        /**
         * Notifies this instance that a specific `ReceiveStream` has been added to the list
         * of playbacks of `ReceiveStream`s and/or `DataSource`s performed by
         * respective `Player`s on the `MediaDevice` represented by this instance.
         *
         * @param receiveStream the `ReceiveStream` which has been added to the list of playbacks of
         * `ReceiveStream`s and/or `DataSource`s performed by respective
         * `Player`s on the `MediaDevice` represented by this instance
         */
        override fun receiveStreamAdded(receiveStream: ReceiveStream) {
            super.receiveStreamAdded(receiveStream)

            /*
             * If someone registered a stream level listener, we can now add it since we have the
             * stream that it's supposed to listen to.
             */
            synchronized(streamAudioLevelListenerLock) {
                if (streamAudioLevelListener != null) audioMixerMediaDeviceSession.setStreamAudioLevelListener(
                        receiveStream, streamAudioLevelListener)
            }
            audioMixerMediaDeviceSession.addReceiveStream(receiveStream)
        }

        /**
         * Notifies this instance that a specific `ReceiveStream` has been removed from the
         * list of playbacks of `ReceiveStream`s and/or `DataSource`s performed by
         * respective `Player`s on the `MediaDevice` represented by this instance.
         *
         * @param receiveStream the `ReceiveStream` which has been removed from the list of playbacks of
         * `ReceiveStream` s and/or `DataSource`s performed by respective
         * `Player`s on the `MediaDevice` represented by this instance
         */
        override fun receiveStreamRemoved(receiveStream: ReceiveStream) {
            super.receiveStreamRemoved(receiveStream)
            audioMixerMediaDeviceSession.removeReceiveStream(receiveStream)
        }

        /**
         * Override it here cause we won't register effects to that stream cause we already have
         * one.
         *
         * @param processor the processor.
         */
        override fun registerLocalUserAudioLevelEffect(processor: Processor) {}

        /**
         * Adds a specific `SoundLevelListener` to the list of listeners interested in and
         * notified about changes in local sound level related information.
         *
         * @param listener the `SoundLevelListener` to add
         */
        override fun setLocalUserAudioLevelListener(listener: SimpleAudioLevelListener?) {
            if (localUserAudioLevelListener != null) {
                audioMixerMediaDeviceSession.removeLocalUserAudioLevelListener(localUserAudioLevelListener!!)
                localUserAudioLevelListener = null
            }

            if (listener != null) {
                localUserAudioLevelListener = listener

                // add the listener only if we are not muted this happens when holding a
                // conversation, stream is muted and when recreated listener is again set
                if (!isMute) {
                    audioMixerMediaDeviceSession.addLocalUserAudioLevelListener(listener)
                }
            }
        }

        /**
         * {@inheritDoc}
         *
         *
         * Overrides the super implementation to redirect/delegate the invocation to the
         * master/audioMixerMediaDeviceSession because `MediaStreamMediaDeviceSession` does
         * not perform playback/rendering.
         */
        override fun setOutputVolumeControl(outputVolumeControl: VolumeControl) {
            audioMixerMediaDeviceSession.setOutputVolumeControl(outputVolumeControl)
        }

        /**
         * Adds `listener` to the list of `SimpleAudioLevelListener`s registered with
         * the mixer session that this "slave session" encapsulates. This class does not keep a
         * reference to `listener`.
         *
         * @param listener the `SimpleAudioLevelListener` that we are to pass to the mixer device
         * session or `null` if we are trying to unregister it.
         */
        override fun setStreamAudioLevelListener(listener: SimpleAudioLevelListener?) {
            synchronized(streamAudioLevelListenerLock) {
                streamAudioLevelListener = listener
                for (receiveStream in receiveStreams) {
                    /*
                     * If we already have a ReceiveStream, register the listener with the mixer;
                     * otherwise, wait till we get one.
                     */
                    audioMixerMediaDeviceSession.setStreamAudioLevelListener(
                            receiveStream, streamAudioLevelListener)
                }
            }
        }

        /**
         * Returns the last audio level that was measured by the underlying mixer for the specified
         * `csrc`.
         *
         * @param ssrc the CSRC ID whose last measured audio level we'd like to retrieve.
         * @return the audio level that was last measured by the underlying mixer for the specified
         * `csrc` or `-1` if the `csrc` does not belong to neither of the conference participants.
         */
        override fun getLastMeasuredAudioLevel(ssrc: Long): Int {
            return (device as AudioMixerMediaDevice).audioLevelCache.getLevel(ssrc)
        }

        /**
         * Returns the last audio level that was measured by the underlying mixer for local user.
         *
         * @return the audio level that was last measured for the local user.
         */
        override val lastMeasuredLocalUserAudioLevel: Int
            get() {
                return (device as AudioMixerMediaDevice).lastMeasuredLocalUserAudioLevel
            }

        /**
         * Sets the indicator which determines whether this `MediaDeviceSession` is set to
         * output "silence" instead of the actual media fed from its `CaptureDevice`. If we
         * are muted we just remove the local level listener from the session.
         *
         * @param mute `true` to set this `MediaDeviceSession` to output "silence" instead
         * of the actual media fed from its `CaptureDevice`; otherwise, `false`
         */
        override var isMute = false
            set(mute) {
                val oldValue = isMute
                field = mute
                if (oldValue != mute) {
                    if (mute) {
                        audioMixerMediaDeviceSession.removeLocalUserAudioLevelListener(localUserAudioLevelListener!!)
                    } else {
                        audioMixerMediaDeviceSession.addLocalUserAudioLevelListener(localUserAudioLevelListener)
                    }
                }
            }
    }

    /**
     * A very lightweight wrapper that allows us to track the number of times that a particular
     * listener was added.
     */
    private class SimpleAudioLevelListenerWrapper(l: SimpleAudioLevelListener) {
        /**
         * The listener being wrapped by this wrapper.
         */
        val listener: SimpleAudioLevelListener

        /**
         * The number of times this listener has been added.
         */
        var referenceCount: Int

        /**
         * Creates a wrapper of the `l` listener.
         *
         * @param l the listener we'd like to wrap;
         */

        init {
            listener = l
            referenceCount = 1
        }

        /**
         * Returns `true` if `obj` is a wrapping the same listener as ours.
         *
         * @param obj the wrapper we'd like to compare to this instance
         * @return `true` if `obj` is a wrapping the same listener as ours.
         */
        override fun equals(obj: Any?): Boolean {
            return (obj is SimpleAudioLevelListenerWrapper
                    && obj.listener === listener)
        }

        /**
         * Returns a hash code value for this instance for the benefit of hashtables.
         *
         * @return a hash code value for this instance for the benefit of hashtables
         */
        override fun hashCode(): Int {
            /*
             * Equality is based on the listener field only so its hashCode is enough. Besides,
             * it's the only immutable of this instance i.e. the only field appropriate for the
             * calculation of the hashCode.
             */
            return listener.hashCode()
        }
    }

    /**
     * Returns the `TranscodingDataSource` associated with `inputDataSource` in this
     * object's `AudioMixer`.
     *
     * @param inputDataSource the `DataSource` to search for
     * @return Returns the `TranscodingDataSource` associated with `inputDataSource`
     * in this object's `AudioMixer`
     * @see AudioMixer.getTranscodingDataSource
     */
    fun getTranscodingDataSource(inputDataSource: DataSource): TranscodingDataSource? {
        return getAudioMixer().getTranscodingDataSource(inputDataSource)
    }
}