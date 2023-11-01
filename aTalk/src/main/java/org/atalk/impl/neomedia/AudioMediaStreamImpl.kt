/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.device.AudioMediaDeviceSession
import org.atalk.impl.neomedia.device.MediaDeviceSession
import org.atalk.impl.neomedia.rtcp.AudioRTCPTermination
import org.atalk.impl.neomedia.rtp.MediaStreamTrackReceiver
import org.atalk.impl.neomedia.rtp.StreamRTPManager
import org.atalk.impl.neomedia.transform.DiscardTransformEngine
import org.atalk.impl.neomedia.transform.csrc.SsrcTransformEngine
import org.atalk.impl.neomedia.transform.dtmf.DtmfTransformEngine
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.AudioMediaStream
import org.atalk.service.neomedia.DTMFInbandTone
import org.atalk.service.neomedia.DTMFMethod
import org.atalk.service.neomedia.DTMFRtpTone
import org.atalk.service.neomedia.DTMFTone
import org.atalk.service.neomedia.MediaDirection
import org.atalk.service.neomedia.RTPExtension
import org.atalk.service.neomedia.SrtpControl
import org.atalk.service.neomedia.StreamConnector
import org.atalk.service.neomedia.VolumeControl
import org.atalk.service.neomedia.codec.Constants
import org.atalk.service.neomedia.device.MediaDevice
import org.atalk.service.neomedia.event.CsrcAudioLevelListener
import org.atalk.service.neomedia.event.DTMFListener
import org.atalk.service.neomedia.event.DTMFToneEvent
import org.atalk.service.neomedia.event.SimpleAudioLevelListener
import org.atalk.util.event.PropertyChangeNotifier
import timber.log.Timber
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.IOException
import javax.media.Format
import javax.media.control.BufferControl
import javax.media.format.AudioFormat
import kotlin.math.max

/**
 * Extends `MediaStreamImpl` in order to provide an implementation of `AudioMediaStream`.
 *
 * @author Lyubomir Marinov
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
open class AudioMediaStreamImpl(
        connector: StreamConnector?,
        device: MediaDevice?,
        srtpControl: SrtpControl?) : MediaStreamImpl(connector, device, srtpControl), AudioMediaStream, PropertyChangeListener {
    /**
     * A `PropertyChangeNotifier` which will inform this `AudioStream` if a selected
     * audio device (capture, playback or notification device) has changed. We want to listen to
     * these events, especially for those generated after the `AudioSystem` has changed.
    `` */
    private var audioSystemChangeNotifier: PropertyChangeNotifier? = null

    /**
     * The listener that gets notified of changes in the audio level of remote conference participants.
     */
    private var csrcAudioLevelListener: CsrcAudioLevelListener? = null

    /**
     * The list of DTMF listeners.
     */
    private val dtmfListeners = ArrayList<DTMFListener>()

    /**
     * The transformer that we use for sending and receiving DTMF packets.
     */
    private var dtmfTransformEngine: DtmfTransformEngine? = null

    /**
     * The listener which has been set on this instance to get notified of changes in the levels of
     * the audio that the local peer/user is sending to the remote peer(s).
     */
    private var localUserAudioLevelListener: SimpleAudioLevelListener? = null

    /**
     * The `VolumeControl` implementation which is to control the volume (level) of the
     * audio received in/by this `AudioMediaStream` and played back.
     */
    private var outputVolumeControl: VolumeControl? = null

    /**
     * The listener which has been set on this instance to get notified of changes in the levels of
     * the audios that the local peer/user is receiving from the remote peer(s).
     */
    private var streamAudioLevelListener: SimpleAudioLevelListener? = null
    private var ssrcTransformEngine: SsrcTransformEngine? = null
    /**
     * {@inheritDoc}
     */
    /**
     * The instance that is aware of all of the RTPEncodingDesc of the remote endpoint.
     */
    override val mediaStreamTrackReceiver = MediaStreamTrackReceiver(this)

    /**
     * Initializes a new `AudioMediaStreamImpl` instance which will use the specified
     * `MediaDevice` for both capture and playback of audio exchanged via the specified `StreamConnector`.
     *
     * connector the `StreamConnector` the new instance is to use for sending and receiving audio
     * device the `MediaDevice` the new instance is to use for both capture and playback of
     * audio exchanged via the specified `StreamConnector`
     * srtpControl a control which is already created, used to control the srtp operations.
     */
    init {
        val mediaService = LibJitsi.mediaService
        if (mediaService is PropertyChangeNotifier) {
            audioSystemChangeNotifier = mediaService
            audioSystemChangeNotifier!!.addPropertyChangeListener(this)
        } else audioSystemChangeNotifier = null
    }

    /**
     * Gets the time in milliseconds of the last input activity related to this
     * `AudioMediaStream`. We detect either RTP or RTCP activity.
     *
     * @return the time in milliseconds of the last input activity related to this `AudioMediaStream`
     * @throws IOException only in case we create input stream and it fails,
     * as we always pass false to skip creating, should never be thrown.
     */
    @get:Throws(IOException::class)
    val lastInputActivityTime: Long
        get() {
            val inData = rtpConnector!!.getDataInputStream(false)
            var inDataActivity = -1L
            if (inData != null) {
                inDataActivity = inData.lastActivityTime.get()
            }
            val inControl = rtpConnector!!.getControlInputStream(false)
            var inControlActivity = -1L
            if (inControl != null) {
                inControlActivity = inControl.lastActivityTime.get()
            }
            return max(inControlActivity, inDataActivity)
        }

    /**
     * Adds a `DTMFListener` to this `AudioMediaStream` which is to receive
     * notifications when the remote party starts sending DTMF tones to us.
     *
     * @param listener the `DTMFListener` to register for notifications about the remote party
     * starting sending of DTM tones to this `AudioMediaStream`
     * @see AudioMediaStream.addDTMFListener
     */
    override fun addDTMFListener(listener: DTMFListener?) {
        if (listener != null && !dtmfListeners.contains(listener)) dtmfListeners.add(listener)
    }

    /**
     * In addition to calling [MediaStreamImpl.addRTPExtension] this
     * method enables sending of CSRC audio levels. The reason we are doing this here rather
     * than in the super class is that CSRC levels only make sense for audio streams so we don't
     * want them enabled in any other type.
     *
     * @param extensionID the ID assigned to `rtpExtension` for the lifetime of this stream.
     * @param rtpExtension the RTPExtension that is being added to this stream.
     */
    override fun addRTPExtension(extensionID: Byte, rtpExtension: RTPExtension) {
        super.addRTPExtension(extensionID, rtpExtension)

        // Do go on even if the extension is null, to make sure that the currently active
        // extensions are configured.

        // The method invocation may add, remove, or replace the value associated with
        // extensionID. Consequently, we have to update csrcEngine with whatever is in
        // activeRTPExtensions eventually.
        val csrcEngine = csrcEngine
        val ssrcEngine = ssrcTransformEngine
        if (csrcEngine != null || ssrcEngine != null) {
            val activeRTPExtensions = getActiveRTPExtensions()
            var csrcExtID: Byte? = null
            var csrcDir = MediaDirection.INACTIVE
            var ssrcExtID: Byte? = null
            var ssrcDir = MediaDirection.INACTIVE
            if (activeRTPExtensions.isNotEmpty()) {
                for ((key, ext) in activeRTPExtensions) {
                    val uri = ext.uri.toString()
                    if (RTPExtension.CSRC_AUDIO_LEVEL_URN == uri) {
                        csrcExtID = key
                        csrcDir = ext.direction
                    } else if (RTPExtension.SSRC_AUDIO_LEVEL_URN == uri) {
                        ssrcExtID = key
                        ssrcDir = ext.direction

                        // jicofo is always setting this extension as one
                        // if we negotiate it to be something different let's at least print it
                        if (ssrcExtID.toInt() != 1) {
                            Timber.w("SSRC_AUDIO_LEVEL_URN extension id needs rewriting!")
                        }
                    }
                }
            }
            csrcEngine?.setCsrcAudioLevelExtensionID(csrcExtID ?: -1, csrcDir)
            if (ssrcEngine != null) {
                ssrcEngine.setSsrcAudioLevelExtensionID(ssrcExtID ?: -1, ssrcDir)
                if (ssrcDir.allowsSending()) {
                    this.getDeviceSession()?.enableOutputSSRCAudioLevels(true, ssrcExtID
                            ?: -1)
                }
            }
        }
    }

    /**
     * Delivers the `audioLevels` map to whoever is interested. This method is meant for use
     * primarily by the transform engine handling incoming RTP packets (currently `CsrcTransformEngine`).
     *
     * @param audioLevels an array mapping CSRC IDs to audio levels in consecutive elements.
     */
    fun audioLevelsReceived(audioLevels: LongArray?) {
        this.csrcAudioLevelListener?.audioLevelsReceived(audioLevels)
    }

    /**
     * Releases the resources allocated by this instance in the course of its execution and
     * prepares it to be garbage collected.
     */
    override fun close() {
        super.close()
        if (dtmfTransformEngine != null) {
            dtmfTransformEngine!!.close()
            dtmfTransformEngine = null
        }
        if (ssrcTransformEngine != null) {
            ssrcTransformEngine!!.close()
            ssrcTransformEngine = null
        }
        if (audioSystemChangeNotifier != null) audioSystemChangeNotifier!!.removePropertyChangeListener(this)
    }

    /**
     * Performs any optional configuration on the `BufferControl` of the specified
     * `RTPManager` which is to be used as the `RTPManager` of this `MediaStreamImpl`.
     *
     * @param rtpManager the `RTPManager` which is to be used by this `MediaStreamImpl`
     * @param bufferControl the `BufferControl` of `rtpManager` on which any optional configuration
     * is to be performed
     */
    override fun configureRTPManagerBufferControl(rtpManager: StreamRTPManager?, bufferControl: BufferControl?) {
        /*
         * It appears that, if we don't do the following, the RTPManager won't play.
         */
        val cfg = LibJitsi.configurationService
        /*
         * There isn't a particular reason why we'd choose 100 or 120. It may be that 120 is
         * divided by 30 (which is used by iLBC, for example) and 100 isn't. Anyway, what matters
         * most is that it's proportional to the latency of the playback.
         */
        var bufferLength = 120L
        val bufferLengthStr = cfg.getString(PROPERTY_NAME_RECEIVE_BUFFER_LENGTH)

        try {
            if (bufferLengthStr != null && bufferLengthStr.isNotEmpty()) bufferLength = bufferLengthStr.toLong()
        } catch (nfe: NumberFormatException) {
            Timber.w(nfe, "%s is not a valid receive buffer length/long value", bufferLengthStr)
        }

        bufferLength = bufferControl!!.setBufferLength(bufferLength)
        Timber.log(TimberLog.FINER, "Set receiver buffer length to %s", bufferLength)

        /*
         * The threshold should better be half of the bufferLength rather than equal to it (as it
         * used to be before). Whatever it is, FMJ/JMF doesn't take it into account anyway.
         */
        val minimumThreshold = bufferLength / 2
        bufferControl.enabledThreshold = minimumThreshold > 0
        bufferControl.minimumThreshold = minimumThreshold
    }

    /**
     * A stub that allows audio oriented streams to create and keep a reference to a `DtmfTransformEngine`.
     *
     * @return a `DtmfTransformEngine` if this is an audio oriented stream and `null` otherwise.
     */
    override fun createDtmfTransformEngine(): DtmfTransformEngine? {
        if (dtmfTransformEngine == null) {
            val cfg = LibJitsi.configurationService
            if (!cfg.getBoolean(AudioMediaStream.DISABLE_DTMF_HANDLING_PNAME, false)) {
                dtmfTransformEngine = DtmfTransformEngine(this)
            }
        }
        return dtmfTransformEngine
    }

    /**
     * {@inheritDoc}
     */
    override fun createSsrcTransformEngine(): SsrcTransformEngine? {
        if (ssrcTransformEngine == null) ssrcTransformEngine = SsrcTransformEngine(this)
        return ssrcTransformEngine
    }

    /**
     * {@inheritDoc}
     *
     *
     * Makes sure that [.localUserAudioLevelListener] and [.streamAudioLevelListener]
     * which have been set on this `AudioMediaStream` will be automatically updated when a
     * new `MediaDevice` is set on this instance.
     */
    override fun deviceSessionChanged(oldValue: MediaDeviceSession?, newValue: MediaDeviceSession?) {
        try {
            if (oldValue != null) {
                val deviceSession = oldValue as AudioMediaDeviceSession
                if (localUserAudioLevelListener != null) deviceSession.setLocalUserAudioLevelListener(null)
                if (streamAudioLevelListener != null) deviceSession.setStreamAudioLevelListener(null)
            }
            if (newValue != null) {
                val deviceSession = newValue as AudioMediaDeviceSession
                if (localUserAudioLevelListener != null) {
                    deviceSession.setLocalUserAudioLevelListener(localUserAudioLevelListener)
                }
                if (streamAudioLevelListener != null) {
                    deviceSession.setStreamAudioLevelListener(streamAudioLevelListener)
                }

                /*
                 * The output volume (level) of the newValue will begin to be controlled by the
                 * outputVolumeControl of this instance (of course). The output volume (level) of
                 * the oldValue will continue to be controlled by the outputVolumeControl of this
                 * instance (as well). The latter behaviour should not present a problem and keeps
                 * the design and implementation as simple as possible.
                 */
                if (outputVolumeControl != null) deviceSession.setOutputVolumeControl(outputVolumeControl!!)
            }
        } finally {
            super.deviceSessionChanged(oldValue, newValue)
        }
    }

    /**
     * Delivers the `DTMF` tones. The method is meant for use primarily by the transform
     * engine handling incoming RTP packets (currently `DtmfTransformEngine`).
     *
     * @param tone the new tone
     * @param end `true` if the tone is to be ended or `false` to be started
     */
    fun fireDTMFEvent(tone: DTMFRtpTone, end: Boolean) {
        val ev = DTMFToneEvent(this, tone)
        for (listener in dtmfListeners) {
            if (end) listener.dtmfToneReceptionEnded(ev) else listener.dtmfToneReceptionStarted(ev)
        }
    }

    /**
     * Returns the `MediaDeviceSession` associated with this stream after first casting it to
     * `AudioMediaDeviceSession` since this is, after all, an `AudioMediaStreamImpl`.
     *
     * @return the `AudioMediaDeviceSession` associated with this stream.
     */
    override fun getDeviceSession(): AudioMediaDeviceSession? {
        return super.getDeviceSession() as AudioMediaDeviceSession?
    }

    /**
     * Returns the last audio level that was measured by the underlying device session for the specified
     * `ssrc` (where `ssrc` could also correspond to our local sync source identifier).
     *
     * @param ssrc the SSRC ID whose last measured audio level we'd like to retrieve.
     * @return the audio level that was last measured for the specified `ssrc` or
     * `-1` if no level has been cached for that ID.
     */
    fun getLastMeasuredAudioLevel(ssrc: Long): Int {
        val devSession = getDeviceSession()
        return when {
            devSession == null -> -1
            ssrc == getLocalSourceID() -> devSession.lastMeasuredLocalUserAudioLevel
            else -> devSession.getLastMeasuredAudioLevel(ssrc)
        }
    }

    /**
     * The priority of the audio is 3, which is meant to be higher than other threads and higher than the video one.
     *
     * @return audio priority.
     */
    override val priority: Int
        get() = 3

    /**
     * Receives and reacts to property change events: if the selected device (for capture, playback
     * or notifications) has changed, then create or recreate the streams in order to use it. We
     * want to listen to these events, especially for those generated after the audio system has changed.
     *
     * @param ev The event which may contain a audio system change event.
     */
    override fun propertyChange(ev: PropertyChangeEvent) {
        /*
         * FIXME It is very wrong to do the following upon every PropertyChangeEvent fired by
         * MediaServiceImpl. Moreover, it does not seem right that we'd want to start this
         * MediaStream upon a PropertyChangeEvent (regardless of its specifics).
         */
        if (sendStreamsAreCreated) recreateSendStreams() else start()
    }

    /**
     * Registers [.CUSTOM_CODEC_FORMATS] with a specific `RTPManager`.
     *
     * @param rtpManager the `RTPManager` to register [.CUSTOM_CODEC_FORMATS] with
     * @see MediaStreamImpl.registerCustomCodecFormats
     */
    override fun registerCustomCodecFormats(rtpManager: StreamRTPManager) {
        super.registerCustomCodecFormats(rtpManager)
        for (format in CUSTOM_CODEC_FORMATS) {
            Timber.d("registering format %s with RTPManager", format)
            /*
             * NOTE (mkoch@rowa.de): com.sun.media.rtp.RtpSessionMgr.addFormat leaks memory, since
             * it stores the Format in a static Vector. AFAIK there is no easy way around it, but
             * the memory impact should not be too bad.
             */
            rtpManager.addFormat(format, MediaUtils.getRTPPayloadType(format.encoding, format.sampleRate).toInt())
        }
    }

    /**
     * Removes `listener` from the list of `DTMFListener`s registered with this
     * `AudioMediaStream` to receive notifications about incoming DTMF tones.
     *
     * @param listener the `DTMFListener` to no longer be notified by this `AudioMediaStream`
     * about incoming DTMF tones
     * @see AudioMediaStream.removeDTMFListener
     */
    override fun removeDTMFListener(listener: DTMFListener) {
        dtmfListeners.remove(listener)
    }

    /**
     * Registers `listener` as the `CsrcAudioLevelListener` that will receive
     * notifications for changes in the levels of conference participants that the remote party
     * could be mixing.
     *
     * @param listener the `CsrcAudioLevelListener` that we'd like to register or `null` if
     * we'd like to stop receiving notifications.
     */
    override fun setCsrcAudioLevelListener(listener: CsrcAudioLevelListener?) {
        csrcAudioLevelListener = listener
    }

    /**
     * Sets `listener` as the `SimpleAudioLevelListener` registered to receive
     * notifications from our device session for changes in the levels of the audio that this
     * stream is sending out.
     *
     * @param listener the `SimpleAudioLevelListener` that we'd like to register or `null` if
     * we want to stop local audio level measurements.
     */
    override fun setLocalUserAudioLevelListener(listener: SimpleAudioLevelListener?) {
        if (localUserAudioLevelListener != listener) {
            localUserAudioLevelListener = listener
            this.getDeviceSession()?.setLocalUserAudioLevelListener(localUserAudioLevelListener)
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun setOutputVolumeControl(outputVolumeControl: VolumeControl) {
        if (this.outputVolumeControl != outputVolumeControl) {
            this.outputVolumeControl = outputVolumeControl
            this.getDeviceSession()?.setOutputVolumeControl(this.outputVolumeControl!!)
        }
    }

    /**
     * Sets `listener` as the `SimpleAudioLevelListener` registered to receive
     * notifications from our device session for changes in the levels of the party that's at the
     * other end of this stream.
     *
     * @param listener the `SimpleAudioLevelListener` that we'd like to register or `null` if
     * we want to stop stream audio level measurements.
     */
    override fun setStreamAudioLevelListener(listener: SimpleAudioLevelListener?) {
        if (streamAudioLevelListener != listener) {
            streamAudioLevelListener = listener
            this.getDeviceSession()?.setStreamAudioLevelListener(streamAudioLevelListener)
        }
    }

    /**
     * Starts sending the specified `DTMFTone` until the `stopSendingDTMF()`
     * method is called (Excepts for INBAND DTMF, which stops by itself this is why where there
     * is no need to call the stopSendingDTMF). Callers should keep in mind the fact that calling
     * this method would most likely interrupt all audio transmission until the corresponding
     * stop method is called. Also, calling this method successively without invoking the
     * corresponding stop method between the calls will simply replace the `DTMFTone` from
     * the first call with that from the second.
     *
     * @param tone the `DTMFTone` to start sending.
     * @param dtmfMethod The kind of DTMF used (RTP, SIP-INOF or INBAND).
     * @param minimalToneDuration The minimal DTMF tone duration.
     * @param maximalToneDuration The maximal DTMF tone duration.
     * @param volume The DTMF tone volume.
     * @throws IllegalArgumentException if `dtmfMethod` is not one of [DTMFMethod.INBAND_DTMF],
     * [DTMFMethod.RTP_DTMF], and [DTMFMethod.SIP_INFO_DTMF]
     * @see AudioMediaStream.startSendingDTMF
     */
    override fun startSendingDTMF(tone: DTMFTone, dtmfMethod: DTMFMethod?, minimalToneDuration: Int,
                         maximalToneDuration: Int, volume: Int) {
        when (dtmfMethod) {
            DTMFMethod.INBAND_DTMF -> {
                this.getDeviceSession()?.addDTMF(DTMFInbandTone.mapTone(tone)!!)
            }
            DTMFMethod.RTP_DTMF -> if (dtmfTransformEngine != null) {
                val t = DTMFRtpTone.mapTone(tone)
                if (t != null) dtmfTransformEngine!!.startSending(t, minimalToneDuration, maximalToneDuration, volume)
            }
            DTMFMethod.SIP_INFO_DTMF -> {}
            else -> throw IllegalArgumentException("dtmfMethod")
        }
    }

    /**
     * Interrupts transmission of a `DTMFTone` started with the `startSendingDTMF()`
     * method. Has no effect if no tone is currently being sent.
     *
     * @param dtmfMethod The kind of DTMF used (RTP, SIP-INOF or INBAND).
     * @throws IllegalArgumentException if `dtmfMethod` is not one of [DTMFMethod.INBAND_DTMF],
     * [DTMFMethod.RTP_DTMF], and [DTMFMethod.SIP_INFO_DTMF]
     * @see AudioMediaStream.stopSendingDTMF
     */
    override fun stopSendingDTMF(dtmfMethod: DTMFMethod?) {
        when (dtmfMethod) {
            DTMFMethod.INBAND_DTMF -> {}
            DTMFMethod.RTP_DTMF -> if (dtmfTransformEngine != null) dtmfTransformEngine!!.stopSendingDTMF()
            DTMFMethod.SIP_INFO_DTMF -> {}
            else -> throw java.lang.IllegalArgumentException("dtmfMethod")
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun createDiscardEngine(): DiscardTransformEngine? {
        return DiscardTransformEngine(this)
    }

    /**
     * The instance that terminates REMBs.
     */
    override var rtcpTermination = AudioRTCPTermination()

    /**
     * {@inheritDoc}
     */
//    override val rtcpTermination: TransformEngine?
//        get() = this.rtcpTermination

    companion object {
        /**
         * List of RTP format strings which are supported by SIP Communicator in addition to the JMF
         * standard formats.
         *
         * @see .registerCustomCodecFormats
         */
        private val CUSTOM_CODEC_FORMATS = arrayOf( /*
             * these formats are specific, since RTP uses format numbers with no parameters.
             */
                AudioFormat(
                        Constants.ALAW_RTP,
                        8000.0,
                        8,
                        1,
                        Format.NOT_SPECIFIED,
                        AudioFormat.SIGNED),
                AudioFormat(
                        Constants.G722_RTP,
                        8000.0,
                        Format.NOT_SPECIFIED /* sampleSizeInBits */,
                        1)
        )
    }
}