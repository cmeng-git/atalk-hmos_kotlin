/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media

import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.event.DTMFListener
import net.java.sip.communicator.service.protocol.event.DTMFReceivedEvent
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.NeomediaServiceUtils
import org.atalk.impl.neomedia.transform.zrtp.ZrtpControlImpl
import org.atalk.service.neomedia.AudioMediaStream
import org.atalk.service.neomedia.DTMFTone
import org.atalk.service.neomedia.MediaDirection
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.MediaStreamTarget
import org.atalk.service.neomedia.RTPExtension
import org.atalk.service.neomedia.SrtpControl
import org.atalk.service.neomedia.SrtpControlType
import org.atalk.service.neomedia.StreamConnector
import org.atalk.service.neomedia.VideoMediaStream
import org.atalk.service.neomedia.control.KeyFrameControl
import org.atalk.service.neomedia.control.KeyFrameControl.KeyFrameRequester
import org.atalk.service.neomedia.device.MediaDevice
import org.atalk.service.neomedia.event.CsrcAudioLevelListener
import org.atalk.service.neomedia.event.DTMFToneEvent
import org.atalk.service.neomedia.event.SimpleAudioLevelListener
import org.atalk.service.neomedia.event.SrtpListener
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.util.MediaType
import org.atalk.util.event.PropertyChangeNotifier
import org.atalk.util.event.VideoEvent
import org.atalk.util.event.VideoListener
import org.atalk.util.event.VideoNotifierSupport
import timber.log.Timber
import java.awt.Component
import java.beans.PropertyChangeListener
import java.util.*

/**
 * Implements media control code which allows state sharing among multiple `CallPeerMediaHandler`s.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 * @author MilanKral
 */
class MediaHandler : PropertyChangeNotifier() {
    /**
     * The `AudioMediaStream` which this instance uses to send and receive audio.
     */
    private var audioStream: AudioMediaStream? = null

    /**
     * The `CsrcAudioLevelListener` that this instance sets on its [.audioStream] if
     * [.csrcAudioLevelListeners] is not empty.
     */
    private val csrcAudioLevelListener = object : CsrcAudioLevelListener {
        override fun audioLevelsReceived(audioLevels: LongArray?) {
            audioLevelsReceived(audioLevels)
        }
    }

    /**
     * The `Object` which synchronizes the access to [.csrcAudioLevelListener] and [.csrcAudioLevelListeners].
     */
    private val csrcAudioLevelListenerLock = Any()

    /**
     * The list of `CsrcAudioLevelListener`s to be notified about audio level-related
     * information received from the remote peer(s).
     */
    // private var csrcAudioLevelListeners = emptyList<CsrcAudioLevelListener>().toMutableList()
    private var csrcAudioLevelListeners = ArrayList<CsrcAudioLevelListener>()

    /**
     * The `KeyFrameControl` currently known to this `MediaHandler` and made
     * available by [.mVideoStream].
     */
    private var keyFrameControl: KeyFrameControl? = null

    /**
     * The `KeyFrameRequester` implemented by this `MediaHandler` and provided to [.keyFrameControl] .
     */
    private val keyFrameRequester = object : KeyFrameRequester {
        override fun requestKeyFrame(): Boolean {
            return this@MediaHandler.requestKeyFrame()
        }
    }

    private val keyFrameRequesters = LinkedList<KeyFrameRequester>()

    /**
     * The last-known local SSRCs of the `MediaStream`s of this instance indexed by `MediaType` ordinal.
     */
    private val localSSRCs: LongArray

    /**
     * The `SimpleAudioLeveListener` that this instance sets on its [.audioStream] if
     * [.localUserAudioLevelListeners] is not empty in order to listen to changes in the
     * levels of the audio sent from the local user/peer to the remote peer(s).
     */
    private val localUserAudioLevelListener = object : SimpleAudioLevelListener {
        override fun audioLevelChanged(level: Int) {
            this@MediaHandler.audioLevelChanged(localUserAudioLevelListenerLock, localUserAudioLevelListeners, level)
        }
    }

    /**
     * The `Object` which synchronizes the access to [.localUserAudioLevelListener]
     * and [.localUserAudioLevelListeners].
     */
    private val localUserAudioLevelListenerLock = Any()

    /**
     * The list of `SimpleAudioLevelListener`s to be notified about changes in the level of
     * the audio sent from the local peer/user to the remote peer(s).
     */
    private var localUserAudioLevelListeners = emptyList<SimpleAudioLevelListener>().toMutableList()

    /**
     * The last-known remote SSRCs of the `MediaStream`s of this instance indexed by `MediaType` ordinal.
     */
    private val remoteSSRCs: LongArray

    /**
     * The `SrtpControl`s of the `MediaStream`s of this instance.
     */
    private val srtpControls = SrtpControls()

    private val srtpListener = object : SrtpListener {
        override fun securityMessageReceived(messageType: String?, i18nMessage: String?, severity: Int) {
            for (listener in getSrtpListeners()) {
                listener.securityMessageReceived(messageType, i18nMessage, severity)
            }
        }

        override fun securityNegotiationStarted(mediaType: MediaType?, sender: SrtpControl) {
            for (listener in getSrtpListeners()) listener.securityNegotiationStarted(mediaType, sender)
        }

        override fun securityTimeout(mediaType: MediaType?) {
            for (listener in getSrtpListeners()) listener.securityTimeout(mediaType)
        }

        override fun securityTurnedOff(mediaType: MediaType?) {
            for (listener in getSrtpListeners()) listener.securityTurnedOff(mediaType)
        }

        override fun securityTurnedOn(mediaType: MediaType?, cipher: String?, sender: SrtpControl?) {
            for (listener in getSrtpListeners()) listener.securityTurnedOn(mediaType, cipher, sender)
        }
    }

    private val srtpListeners = LinkedList<SrtpListener>()

    /**
     * The set of listeners in the application (`Jitsi`) which are to be notified of DTMF events.
     */
    private val dtmfListeners = HashSet<DTMFListener>()

    /**
     * The listener registered to receive DTMF events from [.audioStream].
     */
    private val dtmfListener = MyDTMFListener()

    /**
     * The `SimpleAudioLeveListener` that this instance sets on its [.audioStream] if
     * [.streamAudioLevelListeners] is not empty in order to listen to changes in the levels
     * of the audio received from the remote peer(s) to the local user/peer.
     */
    private val streamAudioLevelListener = object : SimpleAudioLevelListener {
        override fun audioLevelChanged(level: Int) {
            this@MediaHandler.audioLevelChanged(streamAudioLevelListenerLock,
                    streamAudioLevelListeners, level)
        }
    }

    /**
     * The `Object` which synchronizes the access to [.streamAudioLevelListener] and
     * [.streamAudioLevelListeners].
     */
    private val streamAudioLevelListenerLock = Any()

    /**
     * The list of `SimpleAudioLevelListener`s to be notified about changes in the level of
     * the audio sent from remote peer(s) to the local peer/user.
     */
    private var streamAudioLevelListeners = emptyList<SimpleAudioLevelListener>().toMutableList()

    /**
     * The `PropertyChangeListener` which listens to changes in the values of the properties
     * of the `MediaStream`s of this instance.
     */
    private val streamPropertyChangeListener = PropertyChangeListener { evt ->

        /**
         * Notifies this `PropertyChangeListener` that the value of a specific
         * property of the notifier it is registered with has changed.
         *
         * evt a `PropertyChangeEvent` which describes the source of the event, the name
         * of the property which has changed its value and the old and new values of the property
         * @see PropertyChangeListener.propertyChange
         */
        /**
         * Notifies this `PropertyChangeListener` that the value of a specific
         * property of the notifier it is registered with has changed.
         *
         * evt a `PropertyChangeEvent` which describes the source of the event, the name
         * of the property which has changed its value and the old and new values of the property
         * @see PropertyChangeListener.propertyChange
         */
        /**
         * Notifies this `PropertyChangeListener` that the value of a specific
         * property of the notifier it is registered with has changed.
         *
         * evt a `PropertyChangeEvent` which describes the source of the event, the name
         * of the property which has changed its value and the old and new values of the property
         * @see PropertyChangeListener.propertyChange
         */
        /**
         * Notifies this `PropertyChangeListener` that the value of a specific
         * property of the notifier it is registered with has changed.
         *
         * evt a `PropertyChangeEvent` which describes the source of the event, the name
         * of the property which has changed its value and the old and new values of the property
         * @see PropertyChangeListener.propertyChange
         */
        val propertyName = evt.propertyName
        if (MediaStream.PNAME_LOCAL_SSRC == propertyName) {
            val source = evt.source
            if (source === audioStream) {
                setLocalSSRC(MediaType.AUDIO, audioStream!!.getLocalSourceID())
            } else if (source === mVideoStream) {
                setLocalSSRC(MediaType.VIDEO, mVideoStream!!.getLocalSourceID())
            }
        } else if (MediaStream.PNAME_REMOTE_SSRC == propertyName) {
            val source = evt.source
            if (source === audioStream) {
                setRemoteSSRC(MediaType.AUDIO, audioStream!!.getRemoteSourceID())
            } else if (source === mVideoStream) {
                setRemoteSSRC(MediaType.VIDEO, mVideoStream!!.getRemoteSourceID())
            }
        }
    }

    /**
     * The number of references to the `MediaStream`s of this instance returned by
     * [.configureStream] to [CallPeerMediaHandler]s as new instances.
     */
    private val streamReferenceCounts: IntArray
    private val videoNotifierSupport = VideoNotifierSupport(this, true)

    /**
     * The `VideoMediaStream` which this instance uses to send and receive video.
     */
    private var mVideoStream: VideoMediaStream? = null

    /**
     * The `VideoListener` which listens to [.mVideoStream] for changes in the
     * availability of visual `Component`s displaying remote video and re-fires them as
     * originating from this instance.
     */
    private val videoStreamVideoListener = object : VideoListener {
        override fun videoAdded(event: VideoEvent) {
            val clone = event.clone(this@MediaHandler)
            fireVideoEvent(clone)
            if (clone.isConsumed) event.consume()
        }

        override fun videoRemoved(event: VideoEvent) {
            // Forwarded in the same way as VIDEO_ADDED.
            videoAdded(event)
        }

        override fun videoUpdate(event: VideoEvent) {
            // Forwarded in the same way as VIDEO_ADDED.
            videoAdded(event)
        }
    }

    init {
        val mediaTypeValueCount = MediaType.values().size
        localSSRCs = LongArray(mediaTypeValueCount)
        Arrays.fill(localSSRCs, CallPeerMediaHandler.SSRC_UNKNOWN)
        remoteSSRCs = LongArray(mediaTypeValueCount)
        Arrays.fill(remoteSSRCs, CallPeerMediaHandler.SSRC_UNKNOWN)
        streamReferenceCounts = IntArray(mediaTypeValueCount)
    }

    /**
     * Adds a specific `CsrcAudioLevelListener` to the list of
     * `CsrcAudioLevelListener`s to be notified about audio level-related information
     * received from the remote peer(s).
     *
     * listener the `CsrcAudioLevelListener` to add to the list of
     * `CsrcAudioLevelListener`s to be notified about audio level-related information
     * received from the remote peer(s)
     */
    fun addCsrcAudioLevelListener(listener: CsrcAudioLevelListener?) {
        if (listener == null) throw NullPointerException("listener")
        synchronized(csrcAudioLevelListenerLock) {
            if (!csrcAudioLevelListeners.contains(listener)) {
                csrcAudioLevelListeners = ArrayList(csrcAudioLevelListeners)
                if (csrcAudioLevelListeners.add(listener) && csrcAudioLevelListeners.size == 1) {
                    this.audioStream?.setCsrcAudioLevelListener(csrcAudioLevelListener)
                }
            }
        }
    }

    fun addKeyFrameRequester(index: Int, keyFrameRequester: KeyFrameRequester?): Boolean {
        if (keyFrameRequester == null) throw NullPointerException("keyFrameRequester") else {
            synchronized(keyFrameRequesters) {
                return if (keyFrameRequesters.contains(keyFrameRequester)) false else {
                    keyFrameRequesters.add(if (index == -1) keyFrameRequesters.size else index,
                            keyFrameRequester)
                    true
                }
            }
        }
    }

    /**
     * Adds a specific `SimpleAudioLevelListener` to the list of
     * `SimpleAudioLevelListener`s to be notified about changes in the level of the audio
     * sent from the local peer/user to the remote peer(s).
     *
     * listener the `SimpleAudioLevelListener` to add to the list of
     * `SimpleAudioLevelListener`s to be notified about changes in the level of the
     * audio sent from the local peer/user to the remote peer(s)
     */
    fun addLocalUserAudioLevelListener(listener: SimpleAudioLevelListener?) {
        if (listener == null) throw NullPointerException("listener")
        synchronized(localUserAudioLevelListenerLock) {
            if (!localUserAudioLevelListeners.contains(listener)) {
                localUserAudioLevelListeners = ArrayList<SimpleAudioLevelListener>(localUserAudioLevelListeners)
                if (localUserAudioLevelListeners.add(listener) && localUserAudioLevelListeners.size == 1) {
                    this.audioStream?.setLocalUserAudioLevelListener(localUserAudioLevelListener)
                }
            }
        }
    }

    fun addSrtpListener(listener: SrtpListener?) {
        if (listener == null) throw NullPointerException("listener") else {
            synchronized(srtpListeners) { if (!srtpListeners.contains(listener)) srtpListeners.add(listener) }
        }
    }

    /**
     * Adds a `DTMFListener` which will be notified when DTMF events are received from the
     * `MediaHandler` 's audio stream.
     *
     * listener the listener to add.
     */
    fun addDtmfListener(listener: DTMFListener?) {
        if (listener != null) dtmfListeners.add(listener)
    }

    /**
     * Removes a `DTMFListener` from the set of listeners to be notified for DTMF events
     * from this `MediaHandler`'s audio steam.
     *
     * listener the listener to remove.
     */
    fun removeDtmfListener(listener: DTMFListener) {
        dtmfListeners.remove(listener)
    }

    /**
     * Adds a specific `SimpleAudioLevelListener` to the list of
     * `SimpleAudioLevelListener`s to be notified about changes in the level of the audio
     * sent from remote peer(s) to the local peer/user.
     *
     * listener the `SimpleAudioLevelListener` to add to the list of
     * `SimpleAudioLevelListener`s to be notified about changes in the level of the
     * audio sent from the remote peer(s) to the local peer/user
     */
    fun addStreamAudioLevelListener(listener: SimpleAudioLevelListener?) {
        if (listener == null) throw NullPointerException("listener")
        synchronized(streamAudioLevelListenerLock) {
            if (!streamAudioLevelListeners.contains(listener)) {
                streamAudioLevelListeners = ArrayList<SimpleAudioLevelListener>(streamAudioLevelListeners)
                if (streamAudioLevelListeners.add(listener) && streamAudioLevelListeners.size == 1) {
                    this.audioStream?.setStreamAudioLevelListener(streamAudioLevelListener)
                }
            }
        }
    }

    /**
     * Registers a specific `VideoListener` with this instance so that it starts receiving
     * notifications from it about changes in the availability of visual `Component`s
     * displaying video.
     *
     * listener the `VideoListener` to be registered with this instance and to start receiving
     * notifications from it about changes in the availability of visual `Component`s
     * displaying video
     */
    fun addVideoListener(listener: VideoListener) {
        videoNotifierSupport.addVideoListener(listener)
    }

    /**
     * Notifies this instance that a `SimpleAudioLevelListener` has been invoked. Forwards
     * the notification to a specific list of `SimpleAudioLevelListener`s.
     *
     * lock the `Object` which is to be used to synchronize the access to
     * `listeners`.
     * listeners the list of `SimpleAudioLevelListener`s to forward the notification to
     * level the value of the audio level to notify `listeners` about
     */
    private fun audioLevelChanged(lock: Any, listeners: List<SimpleAudioLevelListener>,
            level: Int) {
        var ls: List<SimpleAudioLevelListener>
        synchronized(lock) { ls = listeners.ifEmpty { return } }
        var i = 0
        val count = ls.size
        while (i < count) {
            ls[i].audioLevelChanged(level)
            i++
        }
    }

    /**
     * Notifies this instance that audio level-related information has been received from the
     * remote peer(s). The method forwards the notification to [.csrcAudioLevelListeners].
     *
     * audioLevels the audio level-related information received from the remote peer(s)
     */
    private fun audioLevelsReceived(audioLevels: LongArray) {
        var listeners: List<CsrcAudioLevelListener>
        synchronized(csrcAudioLevelListenerLock) { listeners = csrcAudioLevelListeners.ifEmpty { return } }
        var i = 0
        val count = listeners.size
        while (i < count) {
            listeners[i].audioLevelsReceived(audioLevels)
            i++
        }
    }

    /**
     * Closes the `MediaStream` that this instance uses for a specific `MediaType`
     * and prepares it for garbage collection.
     *
     * mediaType the `MediaType` that we'd like to stop a stream for.
     */
    fun closeStream(callPeerMediaHandler: CallPeerMediaHandler<*>, mediaType: MediaType) {
        val index = mediaType.ordinal
        var streamReferenceCount = streamReferenceCounts[index]

        /*
         * The streamReferenceCounts should not fall into an invalid state but anyway...
         */
        if (streamReferenceCount <= 0) return
        streamReferenceCount--
        streamReferenceCounts[index] = streamReferenceCount

        /*
         * The MediaStream of the specified mediaType is still referenced by other
         * CallPeerMediaHandlers so it is not to be closed yet.
         */
        if (streamReferenceCount > 0) return
        when (mediaType) {
            MediaType.AUDIO -> setAudioStream(null)
            MediaType.VIDEO -> setVideoStream(null)
            else -> {}
        }

        // Clean up the SRTP controls used for the associated Call.
        callPeerMediaHandler.removeAndCleanupOtherSrtpControls(mediaType, null)
    }

    /**
     * Configures `stream` to use the specified `device`, `format`,
     * `target`, `direction`, etc.
     *
     * device the `MediaDevice` to be used by `stream` for capture and playback
     * format the `MediaFormat` that we'd like the new stream to transmit in.
     * target the `MediaStreamTarget` containing the RTP and RTCP address:port couples that
     * the new stream would be sending packets to.
     * direction the `MediaDirection` that we'd like the new stream to use (i.e. sendonly,
     * sendrecv, recvonly, or inactive).
     * rtpExtensions the list of `RTPExtension`s that should be enabled for this stream.
     * stream the `MediaStream` that we'd like to configure.
     * masterStream whether the stream to be used as master if secured
     * @return the `MediaStream` that we received as a parameter (for convenience reasons).
     * @throws OperationFailedException if setting the `MediaFormat` or connecting to the specified
     * `MediaDevice` fails for some reason.
     */
    @Throws(OperationFailedException::class)
    private fun configureStream(callPeerMediaHandler: CallPeerMediaHandler<*>,
            device: MediaDevice?, format: MediaFormat, target: MediaStreamTarget, direction: MediaDirection,
            rtpExtensions: List<RTPExtension>, stream: MediaStream, masterStream: Boolean): MediaStream {
        registerDynamicPTsWithStream(callPeerMediaHandler, stream)
        registerRTPExtensionsWithStream(callPeerMediaHandler, rtpExtensions, stream)
        stream.device = device
        stream.target = target
        stream.direction = direction
        stream.format = format

        // cmeng: call has NPE during testing. Received content-reject while processing content-add
        // Call terminated? Just return with original stream if so ??
        // cmeng: 20200518 - just return stream instead of causing NPE
        val call = callPeerMediaHandler.peer.call ?: return stream

        val mediaType = if (stream is AudioMediaStream) MediaType.AUDIO else MediaType.VIDEO
        stream.setRTPTranslator(call.getRTPTranslator(mediaType))
        when (mediaType) {
            MediaType.AUDIO -> {
                val audioStream = stream as AudioMediaStream

                /*
                 * The volume (level) of the audio played back in calls should be call-specific
                 * i.e. it should be able to change the volume (level) of a call without
                 * affecting any other simultaneous calls.
                 */
                setOutputVolumeControl(audioStream, call)
                setAudioStream(audioStream)
            }
            MediaType.VIDEO -> setVideoStream(stream as VideoMediaStream?)
            else -> {}
        }

        if (call.isDefaultEncrypted) {
            /*
             * We'll use the audio stream as the master stream when using SRTP multistreams.
             */
            val srtpControl = stream.srtpControl
            srtpControl.setMasterSession(masterStream)
            srtpControl.srtpListener = srtpListener
            srtpControl.start(mediaType)
        }

        /*
         * If the specified callPeerMediaHandler is going to see the stream as a new instance,
         * count a new reference to it so that this MediaHandler knows when it really needs to
         * close the stream later on upon calls to #closeStream(CallPeerMediaHandler<?>,
         * MediaType).
         */
        if (stream != callPeerMediaHandler.getStream(mediaType)) streamReferenceCounts[mediaType.ordinal]++
        return stream
    }

    /**
     * Notifies the `VideoListener`s registered with this `MediaHandler` about a
     * specific type of change in the availability of a specific visual `Component`
     * depicting video.
     *
     * type the type of change as defined by `VideoEvent` in the availability of the
     * specified visual `Component` depicting video
     * visualComponent the visual `Component` depicting video which has been added or removed in this
     * `MediaHandler`
     * origin [VideoEvent.LOCAL] if the origin of the video is local (e.g. it is being locally
     * captured) [VideoEvent.REMOTE] if the origin of the video is remote (e.g. a
     * remote peer is streaming it)
     * @return `true` if this event and, more specifically, the visual `Component` it
     * describes have been consumed and should be considered owned, referenced (which is
     * important because `Component`s belong to a single `Container` at a time) otherwise, `false`
     */
    private fun fireVideoEvent(type: Int, visualComponent: Component?, origin: Int): Boolean {
        return videoNotifierSupport.fireVideoEvent(type, visualComponent, origin, true)
    }

    /**
     * Notifies the `VideoListener`s registered with this `MediaHandler` about a
     * specific `VideoEvent`.
     *
     * event the `VideoEvent` to fire to the `VideoListener`s registered with this
     * `MediaHandler`
     */
    private fun fireVideoEvent(event: VideoEvent?) {
        videoNotifierSupport.fireVideoEvent(event!!, true)
    }

    /**
     * Gets the SRTP control type used for a given media type.
     *
     * mediaType the `MediaType` to get the SRTP control type for
     * @return the SRTP control type (MIKEY, SDES, ZRTP) used for the given media type or
     * `null` if SRTP is not enabled for the given media type
     */
    fun getEncryptionMethod(callPeerMediaHandler: CallPeerMediaHandler<*>?, mediaType: MediaType): SrtpControl? {
        /*
         * Find the first existing SRTP control type for the specified media type which is active
         * i.e. secures the communication.
         */
        for (srtpControlType in SrtpControlType.values()) {
            val srtpControl: SrtpControl? = getSrtpControls(callPeerMediaHandler)[mediaType, srtpControlType]
            if (srtpControl != null && srtpControl.secureCommunicationStatus) {
                return srtpControl
            }
        }
        return null
    }

    fun getRemoteSSRC(callPeerMediaHandler: CallPeerMediaHandler<*>?, mediaType: MediaType): Long {
        return remoteSSRCs[mediaType.ordinal]
    }

    /**
     * Gets the `SrtpControl`s of the `MediaStream`s of this instance.
     *
     * @return the `SrtpControl`s of the `MediaStream`s of this instance
     */
    fun getSrtpControls(callPeerMediaHandler: CallPeerMediaHandler<*>?): SrtpControls {
        return srtpControls
    }

    private fun getSrtpListeners(): Array<SrtpListener> {
        synchronized(srtpListeners) { return srtpListeners.toTypedArray() }
    }

    /**
     * Gets the `MediaStream` of this instance which is of a specific `MediaType`. If
     * this instance doesn't have such a `MediaStream`, returns `null`
     *
     * mediaType the `MediaType` of the `MediaStream` to retrieve
     * @return the `MediaStream` of this `CallPeerMediaHandler` which is of the
     * specified `mediaType` if this instance has such a `MediaStream` otherwise, `null`
     */
    fun getStream(callPeerMediaHandler: CallPeerMediaHandler<*>?, mediaType: MediaType?): MediaStream? {
        return when (mediaType) {
            MediaType.AUDIO -> audioStream
            MediaType.VIDEO -> mVideoStream
            else -> throw IllegalArgumentException("mediaType")
        }
    }

    /**
     * Creates if necessary, and configures the stream that this `MediaHandler` is using for
     * the `MediaType` matching the one of the `MediaDevice`.
     *
     * connector the `MediaConnector` that we'd like to bind the newly created stream to.
     * device the `MediaDevice` that we'd like to attach the newly created
     * `MediaStream` to.
     * format the `MediaFormat` that we'd like the new `MediaStream` to be set to
     * transmit in.
     * target the `MediaStreamTarget` containing the RTP and RTCP address:port couples that
     * the new stream would be sending packets to.
     * direction the `MediaDirection` that we'd like the new stream to use (i.e. sendonly,
     * sendrecv, recvonly, or inactive).
     * rtpExtensions the list of `RTPExtension`s that should be enabled for this stream.
     * masterStream whether the stream to be used as master if secured
     * @return the newly created `MediaStream`.
     * @throws OperationFailedException if creating the stream fails for any reason (like for example accessing
     * the device or setting the format).
     */
    @Throws(OperationFailedException::class)
    fun initStream(callPeerMediaHandler: CallPeerMediaHandler<*>, connector: StreamConnector?,
            device: MediaDevice, format: MediaFormat, target: MediaStreamTarget, direction: MediaDirection,
            rtpExtensions: List<RTPExtension>, masterStream: Boolean): MediaStream {
        val mediaType = device.mediaType
        var stream = getStream(callPeerMediaHandler, mediaType)

        if (stream == null) {
            if (mediaType != format.mediaType)
                Timber.log(TimberLog.FINER, "The media types of device and format differ: $mediaType")
            val mediaService = ProtocolMediaActivator.mediaService
            var srtpControl = srtpControls.findFirst(mediaType)

            // If a SrtpControl does not exist yet, create a default one.
            if (srtpControl == null) {

                /*
                 * The default SrtpControl is currently ZRTP without the hello-hash.
                 * It needs to be linked to the srtpControls Map.
                 */
                val accountID = callPeerMediaHandler.peer.getProtocolProvider().accountID
                val myZid = ZrtpControlImpl.generateMyZid(accountID, callPeerMediaHandler.peer.getPeerJid()!!.asBareJid())
                srtpControl = NeomediaServiceUtils.mediaServiceImpl!!.createSrtpControl(SrtpControlType.ZRTP, myZid)
                srtpControls[mediaType] = srtpControl
            }
            stream = mediaService!!.createMediaStream(connector, device, srtpControl)
        } else {
            Timber.log(TimberLog.FINER, "Reinitializing stream: $stream")
        }
        // cmeng: stream can still be null in testing. why?
        return configureStream(callPeerMediaHandler, device, format, target, direction, rtpExtensions, stream!!, masterStream)
    }

    /**
     * Processes a request for a (video) key frame from a remote peer to the local peer.
     *
     * @return `true` if the request for a (video) key frame has been honored by the local
     * peer otherwise, `false`
     */
    fun processKeyFrameRequest(callPeerMediaHandler: CallPeerMediaHandler<*>?): Boolean {
        val keyFrameControl = keyFrameControl
        return keyFrameControl != null && keyFrameControl.keyFrameRequest()
    }

    /**
     * Registers all dynamic payload mappings and any payload type overrides that are known to this
     * `MediaHandler` with the specified `MediaStream`.
     *
     * stream the `MediaStream` that we'd like to register our dynamic payload mappings with.
     */
    private fun registerDynamicPTsWithStream(callPeerMediaHandler: CallPeerMediaHandler<*>, stream: MediaStream) {
        val dynamicPayloadTypes = callPeerMediaHandler.dynamicPayloadTypes

        // first register the mappings
        var dbgMessage = StringBuffer("Dynamic PT map: ")
        for ((fmt, pt) in dynamicPayloadTypes.getMappings()) {
            dbgMessage.append(pt.toInt()).append("=").append(fmt).append(" ")
            stream.addDynamicRTPPayloadType(pt, fmt)
        }
        Timber.i("%s", dbgMessage)
        dbgMessage = StringBuffer("PT overrides [")
        // now register whatever overrides we have for the above mappings
        for ((originalPt, overridePt) in dynamicPayloadTypes.getMappingOverrides()) {
            dbgMessage.append(originalPt.toInt()).append("->").append(overridePt.toInt()).append(" ")
            stream.addDynamicRTPPayloadTypeOverride(originalPt, overridePt)
        }
        dbgMessage.append("]")
        Timber.i("%s", dbgMessage)
    }

    /**
     * Registers with the specified `MediaStream` all RTP extensions negotiated by this
     * `MediaHandler`.
     *
     * stream the `MediaStream` that we'd like to register our `RTPExtension`s with.
     * rtpExtensions the list of `RTPExtension`s that should be enabled for `stream`.
     */
    private fun registerRTPExtensionsWithStream(callPeerMediaHandler: CallPeerMediaHandler<*>,
            rtpExtensions: List<RTPExtension>, stream: MediaStream?) {
        val rtpExtensionsRegistry = callPeerMediaHandler.rtpExtensionsRegistry
        for (rtpExtension in rtpExtensions) {
            val extensionID = rtpExtensionsRegistry.getExtensionMapping(rtpExtension)
            stream!!.addRTPExtension(extensionID, rtpExtension)
        }
    }

    /**
     * Removes a specific `CsrcAudioLevelListener` to the list of
     * `CsrcAudioLevelListener`s to be notified about audio level-related information
     * received from the remote peer(s).
     *
     * listener the `CsrcAudioLevelListener` to remove from the list of
     * `CsrcAudioLevelListener`s to be notified about audio level-related information
     * received from the remote peer(s)
     */
    fun removeCsrcAudioLevelListener(listener: CsrcAudioLevelListener?) {
        if (listener == null) return
        synchronized(csrcAudioLevelListenerLock) {
            if (csrcAudioLevelListeners.contains(listener)) {
                csrcAudioLevelListeners = ArrayList(csrcAudioLevelListeners)
                if (csrcAudioLevelListeners.remove(listener)
                        && csrcAudioLevelListeners.isEmpty()) {
                    this.audioStream?.setCsrcAudioLevelListener(null)
                }
            }
        }
    }

    fun removeKeyFrameRequester(keyFrameRequester: KeyFrameRequester?): Boolean {
        return if (keyFrameRequester == null) false else {
            synchronized(keyFrameRequesters) { return keyFrameRequesters.remove(keyFrameRequester) }
        }
    }

    /**
     * Removes a specific `SimpleAudioLevelListener` to the list of
     * `SimpleAudioLevelListener`s to be notified about changes in the level of the audio
     * sent from the local peer/user to the remote peer(s).
     *
     * listener the `SimpleAudioLevelListener` to remove from the list of
     * `SimpleAudioLevelListener`s to be notified about changes in the level of the
     * audio sent from the local peer/user to the remote peer(s)
     */
    fun removeLocalUserAudioLevelListener(listener: SimpleAudioLevelListener?) {
        if (listener == null) return
        synchronized(localUserAudioLevelListenerLock) {
            if (localUserAudioLevelListeners.contains(listener)) {
                localUserAudioLevelListeners = ArrayList<SimpleAudioLevelListener>(localUserAudioLevelListeners)
                if (localUserAudioLevelListeners.remove(listener)
                        && localUserAudioLevelListeners.isEmpty()) {
                    this.audioStream?.setLocalUserAudioLevelListener(null)
                }
            }
        }
    }

    fun removeSrtpListener(listener: SrtpListener?) {
        if (listener != null) {
            synchronized(srtpListeners) { srtpListeners.remove(listener) }
        }
    }

    /**
     * Removes a specific `SimpleAudioLevelListener` to the list of
     * `SimpleAudioLevelListener`s to be notified about changes in the level of the audio
     * sent from remote peer(s) to the local peer/user.
     *
     * listener the `SimpleAudioLevelListener` to remote from the list of
     * `SimpleAudioLevelListener`s to be notified about changes in the level of the
     * audio sent from the remote peer(s) to the local peer/user
     */
    fun removeStreamAudioLevelListener(listener: SimpleAudioLevelListener?) {
        if (listener == null) return
        synchronized(streamAudioLevelListenerLock) {
            if (streamAudioLevelListeners.contains(listener)) {
                streamAudioLevelListeners = ArrayList<SimpleAudioLevelListener>(streamAudioLevelListeners)
                if (streamAudioLevelListeners.remove(listener)
                        && streamAudioLevelListeners.isEmpty()) {
                    this.audioStream?.setStreamAudioLevelListener(null)
                }
            }
        }
    }

    /**
     * Unregisters a specific `VideoListener` from this instance so that it stops receiving
     * notifications from it about changes in the availability of visual `Component`s displaying video.
     *
     * listener the `VideoListener` to be unregistered from this instance and to stop receiving
     * notifications from it about changes in the availability of visual `Component`s displaying video
     */
    fun removeVideoListener(listener: VideoListener) {
        videoNotifierSupport.removeVideoListener(listener)
    }

    /**
     * Requests a key frame from the remote peer of the associated `VideoMediaStream` of this `MediaHandler`.
     *
     * @return `true` if this `MediaHandler` has indeed requested a key frame from then
     * remote peer of its associated `VideoMediaStream` in response to the call otherwise, `false`
     */
    private fun requestKeyFrame(): Boolean {
        var keyFrameRequesters: Array<KeyFrameRequester>
        synchronized(this.keyFrameRequesters) { keyFrameRequesters = this.keyFrameRequesters.toTypedArray() }
        for (keyFrameRequester in keyFrameRequesters) {
            if (keyFrameRequester.requestKeyFrame()) return true
        }
        return false
    }

    /**
     * Sets the `AudioMediaStream` which this instance is to use to send and receive audio.
     *
     * audioStream the `AudioMediaStream` which this instance is to use to send and receive audio
     */
    private fun setAudioStream(audioStream: AudioMediaStream?) {
        if (this.audioStream != audioStream) {
            // Timber.w(new Exception("Set Audio Stream"), "set Audio Stream: %s => %s", this.audioStream, audioStream)
            if (this.audioStream != null) {
                synchronized(csrcAudioLevelListenerLock) {
                    if (csrcAudioLevelListeners.isNotEmpty())
                        this.audioStream!!.setCsrcAudioLevelListener(null)
                }

                synchronized(localUserAudioLevelListenerLock) {
                    if (localUserAudioLevelListeners.isNotEmpty())
                        this.audioStream!!.setLocalUserAudioLevelListener(null)
                }

                synchronized(streamAudioLevelListenerLock) {
                    if (streamAudioLevelListeners.isNotEmpty())
                        this.audioStream!!.setStreamAudioLevelListener(null)
                }

                this.audioStream!!.removePropertyChangeListener(streamPropertyChangeListener)
                this.audioStream!!.removeDTMFListener(dtmfListener)
                this.audioStream!!.close()
            }
            this.audioStream = audioStream
            val audioLocalSSRC: Long
            val audioRemoteSSRC: Long
            if (this.audioStream != null) {
                this.audioStream!!.addPropertyChangeListener(streamPropertyChangeListener)
                audioLocalSSRC = this.audioStream!!.getLocalSourceID()
                audioRemoteSSRC = this.audioStream!!.getRemoteSourceID()

                synchronized(csrcAudioLevelListenerLock) {
                    if (csrcAudioLevelListeners.isNotEmpty()) {
                        this.audioStream!!.setCsrcAudioLevelListener(csrcAudioLevelListener)
                    }
                }

                synchronized(localUserAudioLevelListenerLock) {
                    if (localUserAudioLevelListeners.isNotEmpty()) {
                        this.audioStream!!.setLocalUserAudioLevelListener(localUserAudioLevelListener)
                    }
                }

                synchronized(streamAudioLevelListenerLock) {
                    if (streamAudioLevelListeners.isNotEmpty()) {
                        this.audioStream!!.setStreamAudioLevelListener(streamAudioLevelListener)
                    }
                }
                this.audioStream!!.addDTMFListener(dtmfListener)
            } else {
                audioRemoteSSRC = CallPeerMediaHandler.SSRC_UNKNOWN
                audioLocalSSRC = audioRemoteSSRC
            }
            setLocalSSRC(MediaType.AUDIO, audioLocalSSRC)
            setRemoteSSRC(MediaType.AUDIO, audioRemoteSSRC)
        }
    }

    /**
     * Sets the `KeyFrameControl` currently known to this `MediaHandler` made
     * available by a specific `VideoMediaStream`.
     *
     * videoStream the `VideoMediaStream` the `KeyFrameControl` of which is to be set as
     * the currently known to this `MediaHandler`
     */
    private fun setKeyFrameControlFromVideoStream(videoStream: VideoMediaStream?) {
        val keyFrameControl: KeyFrameControl? = videoStream?.keyFrameControl
        if (this.keyFrameControl != keyFrameControl) {
            if (this.keyFrameControl != null)
                this.keyFrameControl!!.removeKeyFrameRequester(keyFrameRequester)
            this.keyFrameControl = keyFrameControl
            if (this.keyFrameControl != null)
                this.keyFrameControl!!.addKeyFrameRequester(-1, keyFrameRequester)
        }
    }

    /**
     * Sets the last-known local SSRC of the `MediaStream` of a specific `MediaType`.
     *
     * mediaType the `MediaType` of the `MediaStream` to set the last-known local SSRC of
     * localSSRC the last-known local SSRC of the `MediaStream` of the specified `mediaType`
     */
    private fun setLocalSSRC(mediaType: MediaType, localSSRC: Long) {
        val index = mediaType.ordinal
        val oldValue = localSSRCs[index]
        if (oldValue != localSSRC) {
            localSSRCs[index] = localSSRC
            val property: String? = when (mediaType) {
                MediaType.AUDIO -> CallPeerMediaHandler.AUDIO_LOCAL_SSRC
                MediaType.VIDEO -> CallPeerMediaHandler.VIDEO_LOCAL_SSRC
                else -> null
            }
            if (property != null) firePropertyChange(property, oldValue, localSSRC)
        }
    }

    /**
     * Sets the `VolumeControl` which is to control the volume (level) of the audio received in/by a
     * specific `AudioMediaStream` and played back in order to achieve call-specific volume (level).
     *
     *
     * **Note**: The implementation makes the volume (level) telephony conference-specific.
     *
     *
     * audioStream the `AudioMediaStream` on which a `VolumeControl` from the specified
     * `call` is to be set
     * call the `MediaAwareCall` which provides the `VolumeControl` to be set on the
     * specified `audioStream`
     */
    private fun setOutputVolumeControl(audioStream: AudioMediaStream, call: MediaAwareCall<*, *, *>) {
        /*
         * The volume (level) of the audio played back in calls should be call-specific i.e. it
         * should be able to change the volume (level) of a call without affecting any other
         * simultaneous calls. The implementation makes the volume (level) telephony conference-specific.
         */
        val conference = call.conference
        if (conference != null) {
            val outputVolumeControl = conference.outputVolumeControl
            audioStream.setOutputVolumeControl(outputVolumeControl)
        }
    }

    /**
     * Sets the last-known remote SSRC of the `MediaStream` of a specific `MediaType`.
     *
     * mediaType the `MediaType` of the `MediaStream` to set the last-known remote SSRC
     * remoteSSRC the last-known remote SSRC of the `MediaStream` of the specified `mediaType`
     */
    private fun setRemoteSSRC(mediaType: MediaType, remoteSSRC: Long) {
        val index = mediaType.ordinal
        val oldValue = remoteSSRCs[index]
        if (oldValue != remoteSSRC) {
            remoteSSRCs[index] = remoteSSRC
            val property: String? = when (mediaType) {
                MediaType.AUDIO -> CallPeerMediaHandler.AUDIO_REMOTE_SSRC
                MediaType.VIDEO -> CallPeerMediaHandler.VIDEO_REMOTE_SSRC
                else -> null
            }
            if (property != null) firePropertyChange(property, oldValue, remoteSSRC)
        }
    }

    /**
     * Sets the `VideoMediaStream` which this instance is to use to send and receive video.
     *
     * videoStream the `VideoMediaStream` which this instance is to use to send and receive video
     */
    private fun setVideoStream(videoStream: VideoMediaStream?) {
        if (mVideoStream != videoStream) {
            /*
             * Make sure we will no longer notify the registered VideoListeners about changes in
             * the availability of video in the old videoStream.
             */
            var oldVisualComponents: List<Component?>? = null
            if (mVideoStream != null) {
                mVideoStream!!.removePropertyChangeListener(streamPropertyChangeListener)
                mVideoStream!!.removeVideoListener(videoStreamVideoListener)
                oldVisualComponents = mVideoStream!!.visualComponents

                /*
                 * The current videoStream is going away so this CallPeerMediaHandler should no
                 * longer use its KeyFrameControl.
                 */
                setKeyFrameControlFromVideoStream(null)
                mVideoStream!!.close()
            }
            mVideoStream = videoStream

            /*
             * The videoStream has just changed so this CallPeerMediaHandler should use its KeyFrameControl.
             */
            setKeyFrameControlFromVideoStream(mVideoStream)
            val videoLocalSSRC: Long
            val videoRemoteSSRC: Long
            /*
             * Make sure we will notify the registered VideoListeners about changes in the
             * availability of video in the new videoStream.
             */
            var newVisualComponents: MutableList<Component>? = null
            if (mVideoStream != null) {
                mVideoStream!!.addPropertyChangeListener(streamPropertyChangeListener)
                videoLocalSSRC = mVideoStream!!.getLocalSourceID()
                videoRemoteSSRC = mVideoStream!!.getRemoteSourceID()
                mVideoStream!!.addVideoListener(videoStreamVideoListener)
                newVisualComponents = mVideoStream!!.visualComponents as MutableList<Component>
            } else {
                videoRemoteSSRC = CallPeerMediaHandler.SSRC_UNKNOWN
                videoLocalSSRC = videoRemoteSSRC
            }
            setLocalSSRC(MediaType.VIDEO, videoLocalSSRC)
            setRemoteSSRC(MediaType.VIDEO, videoRemoteSSRC)

            /*
             * Notify the VideoListeners in case there was a change in the availability of the
             * visual Components displaying remote video.
             */
            if (oldVisualComponents != null && oldVisualComponents.isNotEmpty()) {
                /*
                 * Discard Components which are present in the old and in the new Lists.
                 */
                if (newVisualComponents == null) newVisualComponents = mutableListOf()
                for (oldVisualComponent in oldVisualComponents) {
                    if (!newVisualComponents.remove(oldVisualComponent)) {
                        fireVideoEvent(VideoEvent.VIDEO_REMOVED, oldVisualComponent, VideoEvent.REMOTE)
                    }
                }
            }
            if (newVisualComponents != null && newVisualComponents.isNotEmpty()) {
                for (newVisualComponent in newVisualComponents) {
                    fireVideoEvent(VideoEvent.VIDEO_ADDED, newVisualComponent, VideoEvent.REMOTE)
                }
            }
        }
    }

    /**
     * Implements a `libjitsi` `DTMFListener`, which receives events from an
     * `AudioMediaStream`, translate them into `Jitsi` events (
     * `DTMFReceivedEvent`s) and forward them to any registered listeners.
     */
    private inner class MyDTMFListener : org.atalk.service.neomedia.event.DTMFListener {
        /**
         * {@inheritDoc}
         */
        override fun dtmfToneReceptionStarted(event: DTMFToneEvent) {
            fireEvent(DTMFReceivedEvent(this,
                    DTMFTone.getDTMFTone(event.dtmfTone.value), true))
        }

        /**
         * {@inheritDoc}
         */
        override fun dtmfToneReceptionEnded(event: DTMFToneEvent) {
            fireEvent(DTMFReceivedEvent(this,
                    DTMFTone.getDTMFTone(event.dtmfTone.value), false))
        }

        /**
         * Sends an `DTMFReceivedEvent` to all listeners.
         *
         * event the event to send.
         */
        private fun fireEvent(event: DTMFReceivedEvent) {
            for (listener in dtmfListeners) {
                listener.toneReceived(event)
            }
        }
    }
}