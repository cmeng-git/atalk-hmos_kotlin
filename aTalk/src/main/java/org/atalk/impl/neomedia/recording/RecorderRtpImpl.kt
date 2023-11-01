/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.recording

import com.sun.media.util.Registry
import org.atalk.impl.neomedia.audiolevel.AudioLevelEffect
import org.atalk.impl.neomedia.codec.SilenceEffect
import org.atalk.impl.neomedia.device.MediaDeviceImpl
import org.atalk.impl.neomedia.recording.RecorderRtpImpl.RTPConnectorImpl.*
import org.atalk.impl.neomedia.rtp.StreamRTPManager
import org.atalk.impl.neomedia.rtp.translator.RTCPFeedbackMessageSender
import org.atalk.impl.neomedia.rtp.translator.RTPTranslatorImpl
import org.atalk.impl.neomedia.transform.PacketTransformer
import org.atalk.impl.neomedia.transform.REDTransformEngine
import org.atalk.impl.neomedia.transform.SinglePacketTransformerAdapter
import org.atalk.impl.neomedia.transform.TransformEngine
import org.atalk.impl.neomedia.transform.TransformEngineChain
import org.atalk.impl.neomedia.transform.fec.FECTransformEngine
import org.atalk.impl.neomedia.transform.rtcp.CompoundPacketEngine
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.MediaException
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.RTPTranslator
import org.atalk.service.neomedia.RawPacket
import org.atalk.service.neomedia.codec.Constants
import org.atalk.service.neomedia.control.FlushableControl
import org.atalk.service.neomedia.control.KeyFrameControlAdapter
import org.atalk.service.neomedia.event.SimpleAudioLevelListener
import org.atalk.service.neomedia.recording.Recorder
import org.atalk.service.neomedia.recording.RecorderEvent
import org.atalk.service.neomedia.recording.RecorderEventHandler
import org.atalk.util.MediaType
import org.atalk.util.dsi.ActiveSpeakerChangedListener
import org.atalk.util.dsi.ActiveSpeakerDetector
import org.atalk.util.dsi.DominantSpeakerIdentification
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.lang.reflect.UndeclaredThrowableException
import java.util.*
import javax.media.CaptureDeviceInfo
import javax.media.Codec
import javax.media.ConfigureCompleteEvent
import javax.media.ControllerEvent
import javax.media.ControllerListener
import javax.media.DataSink
import javax.media.Format
import javax.media.Manager
import javax.media.MediaLocator
import javax.media.NoDataSinkException
import javax.media.NoProcessorException
import javax.media.Processor
import javax.media.RealizeCompleteEvent
import javax.media.UnsupportedPlugInException
import javax.media.format.AudioFormat
import javax.media.format.VideoFormat
import javax.media.protocol.ContentDescriptor
import javax.media.protocol.DataSource
import javax.media.protocol.FileTypeDescriptor
import javax.media.protocol.PushBufferDataSource
import javax.media.protocol.PushSourceStream
import javax.media.protocol.SourceTransferHandler
import javax.media.rtp.OutputDataStream
import javax.media.rtp.RTPConnector
import javax.media.rtp.RTPManager
import javax.media.rtp.ReceiveStream
import javax.media.rtp.ReceiveStreamListener
import javax.media.rtp.event.NewReceiveStreamEvent
import javax.media.rtp.event.ReceiveStreamEvent
import javax.media.rtp.event.TimeoutEvent

/**
 * A `Recorder` implementation which attaches to an `RTPTranslator`.
 *
 * @author Vladimir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class RecorderRtpImpl(translator: RTPTranslator) : Recorder, ReceiveStreamListener, ActiveSpeakerChangedListener, ControllerListener {
    /**
     * The `RTPTranslator` that this recorder is/will be attached to.
     */
    private val translator: RTPTranslatorImpl

    /**
     * The custom `RTPConnector` that this instance uses to read from [.translator]
     * and write to [.rtpManager].
     */
    private var rtpConnector: RTPConnectorImpl? = null

    /**
     * Path to the directory where the output files will be stored.
     */
    private var path: String? = null

    /**
     * The `RTCPFeedbackMessageSender` that we use to send RTCP FIR messages.
     */
    private var rtcpFeedbackSender: RTCPFeedbackMessageSender? = null

    /**
     * The [RTPManager] instance we use to handle the packets coming from `RTPTranslator`.
     */
    private var rtpManager: RTPManager? = null

    /**
     * The instance which should be notified when events related to recordings (such as the
     * start or end of a recording) occur.
     */
    private var eventHandler: RecorderEventHandlerImpl? = null

    /**
     * Holds the `ReceiveStreams` added to this instance by [.rtpManager] and
     * additional information associated with each one (e.g. the `Processor`, if any, used for it).
     */
    private val receiveStreams = HashSet<ReceiveStreamDesc>()
    private val activeVideoSsrcs = HashSet<Long>()

    /**
     * The `ActiveSpeakerDetector` which will listen to the audio receive streams of this
     * `RecorderRtpImpl` and notify it about changes to the active speaker via calls to
     * [.activeSpeakerChanged]
     */
    private var activeSpeakerDetector: ActiveSpeakerDetector? = null

    /**
     * Controls whether this `RecorderRtpImpl` should perform active speaker detection and
     * fire `SPEAKER_CHANGED` recorder events.
     */
    private val performActiveSpeakerDetection: Boolean
    var streamRTPManager: StreamRTPManager? = null

    override var synchronizer: SynchronizerImpl? = null
        get() {
            if (field == null) field = SynchronizerImpl()
            return field
        }
        set(synchronizer) {
            if (synchronizer is SynchronizerImpl) {
                field = synchronizer
            }
        }
    private var started = false

    /**
     * {@inheritDoc}
     */
    override var mediaStream: MediaStream? = null
        private set

    /**
     * Constructor.
     *
     * translator the `RTPTranslator` to which this instance will attach in order to record
     * media.
     */
    init {
        this.translator = translator as RTPTranslatorImpl
        var performActiveSpeakerDetection = false
        if (cfg != null) {
            performActiveSpeakerDetection = cfg.getBoolean(PERFORM_ASD_PNAME, performActiveSpeakerDetection)

            // setting custom audio codec
            val audioCodec = cfg.getString(AUDIO_CODEC_PNAME)
            if ("wav".equals(audioCodec, ignoreCase = true)) {
                AUDIO_FILENAME_SUFFIX = ".wav"
                AUDIO_CONTENT_DESCRIPTOR = ContentDescriptor(FileTypeDescriptor.WAVE)
            }
        }
        this.performActiveSpeakerDetection = performActiveSpeakerDetection
    }

    /**
     * Implements [Recorder.addListener].
     */
    override fun addListener(listener: Recorder.Listener) {}

    /**
     * Implements [Recorder.removeListener].
     */
    override fun removeListener(listener: Recorder.Listener) {}

    /**
     * Implements [Recorder.supportedFormats].
     */
    override val supportedFormats: List<String>?
        get() = null

    /**
     * Implements [Recorder.setMute].
     */
    override fun setMute(mute: Boolean) {}

    /**
     * Implements [Recorder.filename]. Returns null, since we don't have a (single) associated filename.
     */
    override val filename: String?
        get() = null

    /**
     * Sets the instance which should be notified when events related to
     * recordings (such as the start or end of a recording) occur.
     */
    override fun setEventHandler(eventHandler: RecorderEventHandler?) {
        if (this.eventHandler == null
                || (this.eventHandler != eventHandler
                        && this.eventHandler!!.handler != eventHandler)) {
            if (this.eventHandler == null)
                this.eventHandler = RecorderEventHandlerImpl(eventHandler)
            else
                this.eventHandler!!.handler = eventHandler
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param format unused, since this implementation records multiple streams using potentially different formats.
     * @param filename the path to the directory into which this `Recorder` will store the recorded media files.
     */
    @Throws(IOException::class, MediaException::class)
    override fun start(format: String?, filename: String?) {
        Timber.i("Starting, format = %s %s", format, hashCode())
        path = filename
        val mediaService = LibJitsi.mediaService
        if (performActiveSpeakerDetection) {
            activeSpeakerDetector = DominantSpeakerIdentification()
            activeSpeakerDetector!!.addActiveSpeakerChangedListener(this)
        }

        /*
         * Register a fake call participant. TODO: can we use a more generic MediaStream here?
         */
        mediaStream = mediaService!!.createMediaStream(MediaDeviceImpl(
                CaptureDeviceInfo(), MediaType.VIDEO))
        /*
         * Note that we use only one RTPConnector for both the RTPTranslator and the RTPManager
         * instances. The this.translator will write to its output streams, and this.rtpManager
         * will read from its input streams.
         */
        rtpConnector = RTPConnectorImpl(redPayloadType, ulpfecPayloadType)
        rtpManager = RTPManager.newInstance()

        /*
         * Add the formats that we know about.
         */
        rtpManager!!.addFormat(vp8RtpFormat, vp8PayloadType.toInt())
        rtpManager!!.addFormat(opusFormat, opusPayloadType.toInt())
        rtpManager!!.addReceiveStreamListener(this)

        /*
         * Note: When this.rtpManager sends RTCP sender/receiver reports, they will end up being
         * written to its own input stream. This is not expected to cause problems, but might be
         * something to keep an eye on.
         */
        rtpManager!!.initialize(rtpConnector)
        streamRTPManager = StreamRTPManager(mediaStream!!, translator)
        streamRTPManager!!.initialize(rtpConnector)
        rtcpFeedbackSender = translator.rtcpFeedbackMessageSender
        translator.addFormat(streamRTPManager!!, opusFormat, opusPayloadType.toInt())

        // ((RTPTranslatorImpl)videoRTPTranslator).addFormat(streamRTPManager, redFormat, redPayloadType)
        // ((RTPTranslatorImpl)videoRTPTranslator).addFormat(streamRTPManager, ulpfecFormat, ulpfecPayloadType)
        // ((RTPTranslatorImpl)videoRTPTranslator).addFormat(streamRTPManager, mediaFormatImpl.getFormat(), vp8PayloadType)
        started = true
    }

    override fun stop() {
        if (started) {
            Timber.d("Stopping %s", this)

            // remove the recorder from the translator (e.g. stop new packets from being written to rtpConnector
            if (streamRTPManager != null) {
                streamRTPManager!!.dispose()
            }
            val streamsToRemove = HashSet<ReceiveStreamDesc>()
            synchronized(receiveStreams) { streamsToRemove.addAll(receiveStreams) }
            for (r in streamsToRemove) {
                removeReceiveStream(r, false)
            }
            rtpConnector!!.rtcpPacketTransformer!!.close()
            rtpConnector!!.rtpPacketTransformer!!.close()
            rtpManager!!.dispose()
            if (activeSpeakerDetector != null) activeSpeakerDetector!!.removeActiveSpeakerChangedListener(this)
            started = false
        }
    }

    /**
     * Implements [ReceiveStreamListener.update].
     *
     * [.rtpManager] will use this to notify us of `ReceiveStreamEvent`s.
     */
    override fun update(event: ReceiveStreamEvent) {
        // if (event == null) return
        val receiveStream = event.receiveStream
        if (event is NewReceiveStreamEvent) {
            if (receiveStream == null) {
                Timber.w("NewReceiveStreamEvent: null")
                return
            }
            val ssrc = getReceiveStreamSSRC(receiveStream)
            var receiveStreamDesc = findReceiveStream(ssrc)
            if (receiveStreamDesc != null) {
                var s = "NewReceiveStreamEvent for an existing SSRC. "
                if (receiveStream != receiveStreamDesc.receiveStream)
                    s += "(but different ReceiveStream object)"
                Timber.w("%s", s)
                return
            } else receiveStreamDesc = ReceiveStreamDesc(receiveStream)
            Timber.i("New ReceiveStream, ssrc = %s", ssrc)

            // Find the format of the ReceiveStream
            val dataSource = receiveStream.dataSource
            if (dataSource is PushBufferDataSource) {
                var format: Format? = null
                for (pbs in dataSource.streams) {
                    if (pbs.format.also { format = it } != null) break
                }
                if (format == null) {
                    Timber.e("Failed to handle new ReceiveStream: Failed to determine format")
                    return
                }
                receiveStreamDesc.format = format
            } else {
                Timber.e("Failed to handle new ReceiveStream: Unsupported DataSource")
                return
            }
            var rtpClockRate = -1
            if (receiveStreamDesc.format is AudioFormat) rtpClockRate = (receiveStreamDesc.format as AudioFormat?)!!.sampleRate.toInt() else if (receiveStreamDesc.format is VideoFormat) rtpClockRate = 90000
            synchronizer!!.setRtpClockRate(ssrc, rtpClockRate.toLong())

            // create a Processor and configure it
            val processor = try {
                Manager.createProcessor(receiveStream.dataSource)
            } catch (npe: NoProcessorException) {
                Timber.e(npe, "Failed to create Processor.")
                return
            } catch (npe: IOException) {
                Timber.e(npe, "Failed to create Processor.")
                return
            }
            Timber.i("Created processor for SSRC = %s", ssrc)
            processor.addControllerListener(this)
            receiveStreamDesc.processor = processor
            val streamCount: Int
            synchronized(receiveStreams) {
                receiveStreams.add(receiveStreamDesc)
                streamCount = receiveStreams.size
            }

            /*
             * XXX TODO IRBABOON This is a terrible hack which works around a failure to realize()
             * some of the Processor-s for audio streams, when multiple streams start nearly
             * simultaneously. The cause of the problem is currently unknown (and synchronizing all
             * FMJ calls in RecorderRtpImpl does not help). XXX TODO NOOBABRI
             */
            if (receiveStreamDesc.format is AudioFormat) {
                object : Thread() {
                    override fun run() {
                        // delay configuring the processors for the different audio streams to
                        // decrease the probability that they run together.
                        try {
                            val ms = 450 * (streamCount - 1)
                            Timber.w("Sleeping for %d ms before configuring processor for SSRC = %d %d",
                                    ms, ssrc, System.currentTimeMillis())
                            sleep(ms.toLong())
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                        processor.configure()
                    }
                }.start()
            } else {
                processor.configure()
            }
        } else if (event is TimeoutEvent) {
            if (receiveStream == null) {
                // TODO: we might want to get the list of ReceiveStream-s from
                // rtpManager and compare it to our list, to see if we should remove a stream.
                Timber.w("TimeoutEvent: null.")
                return
            }

            // FMJ silently creates new ReceiveStream instances, so we have to recognize them by the SSRC.
            val receiveStreamDesc = findReceiveStream(getReceiveStreamSSRC(receiveStream))
            if (receiveStreamDesc != null) {
                Timber.i("ReceiveStream timeout, ssrc = %s", receiveStreamDesc.ssrc)
                removeReceiveStream(receiveStreamDesc, true)
            } else {
                Timber.i("ReceiveStream timeout for an unknown stream (already removed?) %s",
                        getReceiveStreamSSRC(receiveStream))
            }
        } else {
            Timber.i("Unhandled ReceiveStreamEvent (%s): %s", event.javaClass.name, event)
        }
    }

    private fun removeReceiveStream(receiveStream: ReceiveStreamDesc, emptyJB: Boolean) {
        val ssrc = receiveStream.ssrc
        if (receiveStream.format is VideoFormat) {
            // Don't accept packets with this SSRC
            rtpConnector!!.packetBuffer!!.disable(ssrc)
            emptyPacketBuffer(ssrc)

            /*
             * Workaround an issue with Chrome resetting the RTP timestamps after a stream's
             * direction changes: if the stream with the same SSRC starts again later, we will
             * obtain new mappings based on the new Sender Reports. See
             * https://code.google.com/p/webrtc/issues/detail?id=3597
             */
            synchronizer!!.removeMapping(ssrc)

            // Continue accepting packets with this SSRC
            rtpConnector!!.packetBuffer!!.reset(ssrc)
        }
        if (receiveStream.dataSink != null) {
            try {
                receiveStream.dataSink!!.stop()
            } catch (e: IOException) {
                Timber.e("Failed to stop DataSink %s", e.message)
            }
            receiveStream.dataSink!!.close()
        }
        if (receiveStream.processor != null) {
            receiveStream.processor!!.stop()
            receiveStream.processor!!.close()
        }
        val dataSource = receiveStream.receiveStream.dataSource
        if (dataSource != null) {
            try {
                dataSource.stop()
            } catch (ioe: IOException) {
                Timber.w("Failed to stop DataSource")
            }
            dataSource.disconnect()
        }
        synchronized(receiveStreams) { receiveStreams.remove(receiveStream) }
        synchronized(activeVideoSsrcs) { activeVideoSsrcs.remove(ssrc) }
    }

    /**
     * Implements [ControllerListener.controllerUpdate]. Handles events from
     * the `Processor`s that this instance uses to transcode media.
     *
     * @param ev the event to handle.
     */
    override fun controllerUpdate(ev: ControllerEvent) {
        if (ev.sourceController == null) {
            return
        }
        val processor = ev.sourceController as Processor
        val desc = findReceiveStream(processor)
        if (desc == null) {
            Timber.w("Event from an orphaned processor, ignoring: %s", ev)
            return
        }
        when (ev) {
            is ConfigureCompleteEvent -> {
                Timber.i("Configured processor for ReceiveStream ssrc = %s (%s) %s",
                        desc.ssrc, desc.format, System.currentTimeMillis())
                val audio = desc.format is AudioFormat
                if (audio) {
                    val cd = processor.setContentDescriptor(AUDIO_CONTENT_DESCRIPTOR)
                    if (AUDIO_CONTENT_DESCRIPTOR != cd) {
                        Timber.e("Failed to set the Processor content descriptor to %s. Actual result: %s",
                                AUDIO_CONTENT_DESCRIPTOR, cd)
                        removeReceiveStream(desc, false)
                        return
                    }
                }
                for (track in processor.trackControls) {
                    val trackFormat = track.format
                    if (audio) {
                        val codecList: MutableList<Codec> = LinkedList()
                        val ssrc = desc.ssrc
                        val silenceEffect = if (Constants.OPUS_RTP == desc.format!!.encoding) {
                            SilenceEffect(48000)
                        } else {
                            // We haven't tested that the RTP timestamps survive the journey through
                            // the chain when codecs other than opus are in use, so for the moment we
                            // rely on FMJ's timestamps for non-opus formats.
                            SilenceEffect()
                        }
                        silenceEffect.setListener(object : SilenceEffect.Listener {
                            var first = true
                            override fun onSilenceNotInserted(timestamp: Long) {
                                if (first) {
                                    first = false
                                    // send event only
                                    audioRecordingStarted(ssrc, timestamp)
                                } else {
                                    // change file and send event
                                    resetRecording(ssrc, timestamp)
                                }
                            }
                        })
                        desc.silenceEffect = silenceEffect
                        codecList.add(silenceEffect)

                        if (performActiveSpeakerDetection) {
                            val audioLevelEffect = AudioLevelEffect()
                            audioLevelEffect.setAudioLevelListener(object : SimpleAudioLevelListener {
                                override fun audioLevelChanged(level: Int) {
                                    activeSpeakerDetector!!.levelChanged(ssrc, level)
                                }
                            })
                            codecList.add(audioLevelEffect)
                        }

                        try {
                            // We add an effect, which will insert "silence" in place of lost packets.
                            track.setCodecChain(codecList.toTypedArray())
                        } catch (upie: UnsupportedPlugInException) {
                            Timber.w("Failed to insert silence effect: %s", upie.message)
                            // But do go on, a recording without extra silence is better than nothing
                        }
                    } else {
                        // transcode vp8/rtp to vp8 (i.e. depacketize vp8)
                        if (trackFormat.matches(vp8RtpFormat)) track.format = vp8Format else {
                            Timber.e("Unsupported track format: %s for ssrc = %s", trackFormat, desc.ssrc)
                            // we currently only support vp8
                            removeReceiveStream(desc, false)
                            return
                        }
                    }
                }
                processor.realize()
            }

            is RealizeCompleteEvent -> {
                desc.dataSource = processor.dataOutput
                val ssrc = desc.ssrc
                val audio = desc.format is AudioFormat
                val suffix = if (audio) AUDIO_FILENAME_SUFFIX else VIDEO_FILENAME_SUFFIX

                // XXX '\' on windows?
                val filename = getNextFilename("$path/$ssrc", suffix)!!
                desc.filename = filename
                val dataSink = if (audio) {
                    try {
                        Manager.createDataSink(desc.dataSource, MediaLocator("file:$filename"))
                    } catch (ndse: NoDataSinkException) {
                        Timber.e("Could not create DataSink: %s", ndse.message)
                        removeReceiveStream(desc, false)
                        return
                    }
                } else {
                    WebmDataSink(filename, desc.dataSource!!)
                }
                Timber.i("Created DataSink (%s) for SSRC: %s. Output filename: %s", dataSink, ssrc, filename)
                try {
                    dataSink.open()
                } catch (e: IOException) {
                    Timber.i("Failed to open DataSink (%s) for SSRC = %s: %s", dataSink, ssrc, e.message)
                    removeReceiveStream(desc, false)
                    return
                }
                if (!audio) {
                    val webmDataSink = dataSink as WebmDataSink
                    webmDataSink.setSsrc(ssrc)
                    webmDataSink.eventHandler = eventHandler
                    webmDataSink.setKeyFrameControl(object : KeyFrameControlAdapter() {
                        override fun requestKeyFrame(urgent: Boolean): Boolean {
                            return requestFIR(webmDataSink)
                        }
                    })
                }
                try {
                    dataSink.start()
                } catch (e: IOException) {
                    Timber.e("Failed to start DataSink (%s) for SSRC = %s. %s", dataSink, ssrc, e.message)
                    removeReceiveStream(desc, false)
                    return
                }
                Timber.i("Started DataSink for SSRC = %s", ssrc)
                desc.dataSink = dataSink
                processor.start()
            }

            else -> {
                Timber.d("Unhandled ControllerEvent from the Processor for ssrc = %d: %s", desc.ssrc, ev)
            }
        }
    }

    /**
     * Restarts the recording for a specific SSRC.
     *
     * @param ssrc the SSRC for which to restart recording. RTP packet of the new recording).
     */
    private fun resetRecording(ssrc: Long, timestamp: Long) {
        val receiveStream = findReceiveStream(ssrc)

        // we only restart audio recordings
        if (receiveStream != null && receiveStream.format is AudioFormat) {
            val newFilename = getNextFilename("$path/$ssrc", AUDIO_FILENAME_SUFFIX)

            // flush the buffer contained in the MP3 encoder
            val p = receiveStream.processor
            if (p != null) {
                for (tc in p.trackControls) {
                    val o = tc.getControl(FlushableControl::class.java.name)
                    if (o != null) (o as FlushableControl).flush()
                }
            }
            Timber.i("Restarting recording for SSRC = %s. New filename: %s", ssrc, newFilename)
            receiveStream.dataSink!!.close()
            receiveStream.dataSink = null

            // flush the FMJ jitter buffer
            // DataSource ds = receiveStream.receiveStream.getDataSource()
            // if (ds instanceof net.sf.fmj.media.protocol.rtp.DataSource)
            // ((net.sf.fmj.media.protocol.rtp.DataSource)ds).flush()
            receiveStream.filename = newFilename
            try {
                receiveStream.dataSink = Manager.createDataSink(receiveStream.dataSource,
                        MediaLocator("file:$newFilename"))
            } catch (ndse: NoDataSinkException) {
                Timber.w("Could not reset recording for SSRC=%s: %s", ssrc, ndse.message)
                removeReceiveStream(receiveStream, false)
            }
            try {
                receiveStream.dataSink!!.open()
                receiveStream.dataSink!!.start()
            } catch (ioe: IOException) {
                Timber.w("Could not reset recording for SSRC=%s: %s", ssrc, ioe.message)
                removeReceiveStream(receiveStream, false)
            }
            audioRecordingStarted(ssrc, timestamp)
        }
    }

    private fun audioRecordingStarted(ssrc: Long, timestamp: Long) {
        val desc = findReceiveStream(ssrc) ?: return
        val event = RecorderEvent()
        event.type = RecorderEvent.Type.RECORDING_STARTED
        event.mediaType = MediaType.AUDIO
        event.ssrc = ssrc
        event.rtpTimestamp = timestamp
        event.filename = desc.filename
        if (eventHandler != null) eventHandler!!.handleEvent(event)
    }

    /**
     * Handles a request from a specific `DataSink` to request a keyframe by sending an RTCP
     * feedback FIR message to the media source.
     *
     * @param dataSink the `DataSink` which requests that a keyframe be requested with a FIR message.
     * @return `true` if a keyframe was successfully requested, `false` otherwise
     */
    private fun requestFIR(dataSink: WebmDataSink): Boolean {
        val desc = findReceiveStream(dataSink)
        return if (desc != null && rtcpFeedbackSender != null) {
            rtcpFeedbackSender!!.sendFIR(desc.ssrc.toInt())
        } else false
    }

    /**
     * Returns "prefix"+"suffix" if the file with this name does not exist. Otherwise, returns the
     * first inexistant filename of the form "prefix-"+i+"suffix", for an integer i. i is bounded by
     * 100 to prevent hanging, and on failure to find an inexistant filename the method will return null.
     *
     * @param prefix
     * @param suffix
     * @return
     */
    private fun getNextFilename(prefix: String, suffix: String): String? {
        if (!File(prefix + suffix).exists()) return prefix + suffix
        var i = 1
        var s: String
        do {
            s = "$prefix-$i$suffix"
            if (!File(s).exists()) return s
            i++
        } while (i < 1000) // don't hang indefinitely...
        return null
    }

    /**
     * Finds the `ReceiveStreamDesc` with a particular `Processor`
     *
     * @param processor The `Processor` to match.
     * @return the `ReceiveStreamDesc` with a particular `Processor`, or
     * `null`
     * .
     */
    private fun findReceiveStream(processor: Processor?): ReceiveStreamDesc? {
        if (processor == null) return null
        synchronized(receiveStreams) { for (r in receiveStreams) if (processor == r.processor) return r }
        return null
    }

    /**
     * Finds the `ReceiveStreamDesc` with a particular `DataSink`
     *
     * @param dataSink The `DataSink` to match.
     * @return the `ReceiveStreamDesc` with a particular `DataSink`, or
     * `null`.
     */
    private fun findReceiveStream(dataSink: DataSink?): ReceiveStreamDesc? {
        if (dataSink == null) return null
        synchronized(receiveStreams) { for (r in receiveStreams) if (dataSink == r.dataSink) return r }
        return null
    }

    /**
     * Finds the `ReceiveStreamDesc` with a particular SSRC.
     *
     * @param ssrc The SSRC to match.
     * @return the `ReceiveStreamDesc` with a particular SSRC, or `null`.
     */
    private fun findReceiveStream(ssrc: Long): ReceiveStreamDesc? {
        synchronized(receiveStreams) { for (r in receiveStreams) if (ssrc == r.ssrc) return r }
        return null
    }

    /**
     * Gets the SSRC of a `ReceiveStream` as a (non-negative) `long`.
     *
     *
     * FMJ stores the 32-bit SSRC values in `int`s, and the `ReceiveStream.getSSRC()`
     * implementation(s) don't take care of converting the negative `int` values sometimes
     * resulting from reading of a 32-bit field into the correct unsigned `long` value.
     * So do the conversion here.
     *
     * @param receiveStream the `ReceiveStream` for which to get the SSRC.
     * @return the SSRC of `receiveStream` an a (non-negative) `long`.
     */
    private fun getReceiveStreamSSRC(receiveStream: ReceiveStream): Long {
        return 0xffffffffL and receiveStream.ssrc
    }

    /**
     * Implements [ActiveSpeakerChangedListener.activeSpeakerChanged]. Notifies this
     * `RecorderRtpImpl` that the audio `ReceiveStream` considered active has
     * changed, and that the new active stream has SSRC `ssrc`.
     *
     * @param ssrc the SSRC of the new active stream.
     */
    override fun activeSpeakerChanged(ssrc: Long) {
        if (performActiveSpeakerDetection) {
            if (eventHandler != null) {
                val e = RecorderEvent()
                e.audioSsrc = ssrc
                // TODO: how do we time this?
                e.instant = System.currentTimeMillis()
                e.type = RecorderEvent.Type.SPEAKER_CHANGED
                e.mediaType = MediaType.VIDEO
                eventHandler!!.handleEvent(e)
            }
        }
    }

    private fun handleRtpPacket(pkt: RawPacket?) {
        if (pkt != null && pkt.payloadType == vp8PayloadType) {
            val ssrc = pkt.getSSRCAsLong()
            if (!activeVideoSsrcs.contains(ssrc)) {
                synchronized(activeVideoSsrcs) {
                    if (!activeVideoSsrcs.contains(ssrc)) {
                        activeVideoSsrcs.add(ssrc)
                        rtcpFeedbackSender!!.sendFIR(ssrc.toInt())
                    }
                }
            }
        }
    }

    private fun handleRtcpPacket(pkt: RawPacket?) {
        synchronizer!!.addRTCPPacket(pkt)
        eventHandler!!.nudge()
    }

    fun connect(recorder: Recorder?) {
        if (recorder !is RecorderRtpImpl) return
        recorder.synchronizer = synchronizer
    }

    private fun emptyPacketBuffer(ssrc: Long) {
        val pkts = rtpConnector!!.packetBuffer!!.emptyBuffer(ssrc)

        val dataStream = try {
            rtpConnector!!.dataOutputStream
        } catch (ioe: IOException) {
            Timber.e("Failed to empty packet buffer for SSRC=%s: %s", ssrc, ioe.message)
            return
        }

//        if (dataStream == null) {
//            return
//        }
        for (pkt in pkts) dataStream.write(pkt!!.buffer,
                pkt.offset,
                pkt.length,
                false /* already transformed */)
    }

    /**
     * The `RTPConnector` implementation used by this `RecorderRtpImpl`.
     */
    private inner class RTPConnectorImpl : RTPConnector {
        private var controlInputStream: PushSourceStreamImpl? = null
        private var controlOutputStream: OutputDataStreamImpl? = null
        private var dataInputStream: PushSourceStreamImpl? = null
        private var dataOutputStream: OutputDataStreamImpl? = null
        private var dataTransferHandler: SourceTransferHandler? = null
        private var controlTransferHandler: SourceTransferHandler? = null
        private var pendingDataPacket = RawPacket()
        private var pendingControlPacket = RawPacket()
        var rtpPacketTransformer: PacketTransformer? = null
        var rtcpPacketTransformer: PacketTransformer? = null

        /**
         * The PacketBuffer instance which we use as a jitter buffer.
         */
        var packetBuffer: PacketBuffer? = null

        private constructor()

        constructor(redPT: Byte, ulpfecPT: Byte) {
            packetBuffer = PacketBuffer()
            // The chain of transformers will be applied in reverse order for incoming packets.
            val transformEngine = TransformEngineChain(arrayOf(
                    packetBuffer!!,
                    TransformEngineImpl(),
                    CompoundPacketEngine(),
                    FECTransformEngine(FECTransformEngine.FecType.ULPFEC,
                            ulpfecPT, (-1).toByte(), mediaStream!!),
                    REDTransformEngine(redPT, (-1).toByte())
            ))

            rtpPacketTransformer = transformEngine.rtpTransformer
            rtcpPacketTransformer = transformEngine.rtcpTransformer
        }

        override fun close() {
            try {
                if (dataOutputStream != null) dataOutputStream!!.close()
                if (controlOutputStream != null) controlOutputStream!!.close()
            } catch (ioe: IOException) {
                throw UndeclaredThrowableException(ioe)
            }
        }

        @Throws(IOException::class)
        override fun getControlInputStream(): PushSourceStream {
            if (controlInputStream == null) {
                controlInputStream = PushSourceStreamImpl(true)
            }
            return controlInputStream!!
        }

        @Throws(IOException::class)
        override fun getControlOutputStream(): OutputDataStream {
            if (controlOutputStream == null) {
                controlOutputStream = OutputDataStreamImpl(true)
            }
            return controlOutputStream!!
        }

        @Throws(IOException::class)
        override fun getDataInputStream(): PushSourceStream {
            if (dataInputStream == null) {
                dataInputStream = PushSourceStreamImpl(false)
            }
            return dataInputStream!!
        }

        @Throws(IOException::class)
        override fun getDataOutputStream(): OutputDataStreamImpl {
            if (dataOutputStream == null) {
                dataOutputStream = OutputDataStreamImpl(false)
            }
            return dataOutputStream!!
        }

        override fun getRTCPBandwidthFraction(): Double {
            return (-1).toDouble()
        }

        override fun getRTCPSenderBandwidthFraction(): Double {
            return (-1).toDouble()
        }

        override fun getReceiveBufferSize(): Int {
            // TODO Auto-generated method stub
            return 0
        }

        override fun getSendBufferSize(): Int {
            // TODO Auto-generated method stub
            return 0
        }

        @Throws(IOException::class)
        override fun setReceiveBufferSize(arg0: Int) {
            // TODO Auto-generated method stub
        }

        @Throws(IOException::class)
        override fun setSendBufferSize(arg0: Int) {
            // TODO Auto-generated method stub
        }

        inner class OutputDataStreamImpl(var isControlStream: Boolean) : OutputDataStream {
            private var rawPacketArray: Array<RawPacket?> = arrayOfNulls(1)

            @Synchronized
            override fun write(buffer: ByteArray, offset: Int, length: Int): Int {
                return write(buffer, offset, length, true)
            }

            @Synchronized
            fun write(buffer: ByteArray?, offset: Int, length: Int, transform: Boolean): Int {
                var pkt = rawPacketArray[0]
                if (pkt == null) pkt = RawPacket()

                rawPacketArray[0] = pkt
                var pktBuf = pkt.buffer
                if (pktBuf == null || pktBuf.size < length) {
                    pktBuf = ByteArray(length)
                    pkt.buffer = pktBuf
                }
                System.arraycopy(buffer!!, offset, pktBuf, 0, length)
                pkt.offset = 0
                pkt.length = length
                if (transform) {
                    val packetTransformer = if (isControlStream) rtcpPacketTransformer else rtpPacketTransformer
                    if (packetTransformer != null) rawPacketArray = packetTransformer.reverseTransform(rawPacketArray)
                }
                val transferHandler: SourceTransferHandler?
                val pushSourceStream: PushSourceStream
                try {
                    if (isControlStream) {
                        transferHandler = controlTransferHandler
                        pushSourceStream = getControlInputStream()
                    } else {
                        transferHandler = dataTransferHandler
                        pushSourceStream = getDataInputStream()
                    }
                } catch (ioe: IOException) {
                    throw UndeclaredThrowableException(ioe)
                }
                for (i in rawPacketArray.indices) {
                    val packet = rawPacketArray[i]

                    // keep the first element for reuse
                    if (i != 0) rawPacketArray[i] = null
                    if (packet != null) {
                        if (isControlStream) pendingControlPacket = packet else pendingDataPacket = packet
                        transferHandler?.transferData(pushSourceStream)
                    }
                }
                return length
            }

            @Throws(IOException::class)
            fun close() {
            }
        }

        /**
         * A dummy implementation of [PushSourceStream].
         *
         * @author Vladimir Marinov
         */
        private inner class PushSourceStreamImpl(isControlStream: Boolean) : PushSourceStream {
            private var isControlStream = false

            init {
                this.isControlStream = isControlStream
            }

            /**
             * Not implemented because there are currently no uses of the underlying functionality.
             */
            override fun endOfStream(): Boolean {
                return false
            }

            /**
             * Not implemented because there are currently no uses of the underlying functionality.
             */
            override fun getContentDescriptor(): ContentDescriptor? {
                return null
            }

            /**
             * Not implemented because there are currently no uses of the underlying functionality.
             */
            override fun getContentLength(): Long {
                return 0
            }

            /**
             * Not implemented because there are currently no uses of the underlying functionality.
             */
            override fun getControl(arg0: String): Any? {
                return null
            }

            /**
             * Not implemented because there are currently no uses of the underlying functionality.
             */
            override fun getControls(): Array<Any>? {
                return null
            }

            /**
             * Not implemented because there are currently no uses of the underlying functionality.
             */
            override fun getMinimumTransferSize(): Int {
                if (isControlStream) {
                    if (pendingControlPacket.buffer != null) {
                        return pendingControlPacket.length
                    }
                } else {
                    if (pendingDataPacket.buffer != null) {
                        return pendingDataPacket.length
                    }
                }
                return 0
            }

            @Throws(IOException::class)
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val pendingPacket = if (isControlStream) {
                    pendingControlPacket
                } else {
                    pendingDataPacket
                }
                var bytesToRead = 0
                val pendingPacketBuffer = pendingPacket.buffer
                if (pendingPacketBuffer != null) {
                    val pendingPacketLength = pendingPacket.length
                    bytesToRead = if (length > pendingPacketLength) pendingPacketLength else length
                    System.arraycopy(pendingPacketBuffer, pendingPacket.offset, buffer,
                            offset, bytesToRead)
                }
                return bytesToRead
            }

            /**
             * {@inheritDoc}
             *
             *
             * We keep the first non-null `SourceTransferHandler` that was set, because we
             * don't want it to be overwritten when we initialize a second `RTPManager` with
             * this `RTPConnector`.
             *
             *
             * See [RecorderRtpImpl.start]
             */
            override fun setTransferHandler(transferHandler: SourceTransferHandler) {
                if (isControlStream) {
                    if (controlTransferHandler == null) {
                        controlTransferHandler = transferHandler
                    }
                } else {
                    if (dataTransferHandler == null) {
                        dataTransferHandler = transferHandler
                    }
                }
            }
        }

        /**
         * A transform engine implementation which allows `RecorderRtpImpl` to intercept RTP
         * and RTCP packets in.
         */
        private inner class TransformEngineImpl : TransformEngine {
            override var rtpTransformer = object : SinglePacketTransformerAdapter() {
                override fun reverseTransform(pkt: RawPacket): RawPacket? {
                    handleRtpPacket(pkt)
                    return pkt
                }

                override fun close() {}
            }
            override var rtcpTransformer = object : SinglePacketTransformerAdapter() {
                override fun reverseTransform(pkt: RawPacket): RawPacket? {
                    handleRtcpPacket(pkt)
                    if (pkt != null && pkt.rtcpPacketType == 203) {
                        // An RTCP BYE packet. Remove the receive stream before it gets to FMJ,
                        // because we want to, for example, flush the packet buffer before that.
                        val ssrc = pkt.rtcpSSRC
                        Timber.i("RTCP BYE for SSRC = %s", ssrc)
                        val receiveStream = findReceiveStream(ssrc)
                        if (receiveStream != null) removeReceiveStream(receiveStream, false)
                    } else if (pkt != null && pkt.rtcpPacketType == 201) {
                        // Do not pass Receiver Reports to FMJ, because it does not need them (it
                        // isn't sending) and because they causes weird problems.
                        return null
                    }
                    return pkt
                }

                override fun close() {}
            }
        }
    }

    private inner class RecorderEventHandlerImpl(var handler: RecorderEventHandler?) : RecorderEventHandler {
        private val pendingEvents = HashSet<RecorderEvent>()
        override fun handleEvent(ev: RecorderEvent?): Boolean {
            if (ev == null) return true
            if (RecorderEvent.Type.RECORDING_STARTED == ev.type) {
                val instant = synchronizer!!.getLocalTime(ev.ssrc, ev.rtpTimestamp)
                return if (instant != -1L) {
                    ev.instant = instant
                    handler!!.handleEvent(ev)
                } else {
                    pendingEvents.add(ev)
                    true
                }
            }
            return handler!!.handleEvent(ev)
        }

        fun nudge() {
            val iter = pendingEvents.iterator()
            while (iter.hasNext()) {
                val ev = iter.next()
                val instant = synchronizer!!.getLocalTime(ev.ssrc, ev.rtpTimestamp)
                if (instant != -1L) {
                    iter.remove()
                    ev.instant = instant
                    handler!!.handleEvent(ev)
                }
            }
        }

        override fun close() {
            for (ev in pendingEvents) handler!!.handleEvent(ev)
        }
    }

    /**
     * Represents a `ReceiveStream` for the purposes of this `RecorderRtpImpl`.
     */
    private inner class ReceiveStreamDesc(
            /**
             * The actual `ReceiveStream` which is represented by this
             * `ReceiveStreamDesc`.
             */
            val receiveStream: ReceiveStream,
    ) {
        /**
         * The SSRC of the stream.
         */
        var ssrc = getReceiveStreamSSRC(receiveStream)

        /**
         * The `Processor` used to transcode this receive stream into a format appropriate
         * for saving to a file.
         */
        var processor: Processor? = null

        /**
         * The `DataSink` which saves the `this.dataSource` to a file.
         */
        var dataSink: DataSink? = null

        /**
         * The `DataSource` for this receive stream which is to be saved using a
         * `DataSink` (i.e. the `DataSource` "after" all needed transcoding is done).
         */
        var dataSource: DataSource? = null

        /**
         * The name of the file into which this stream is being saved.
         */
        var filename: String? = null

        /**
         * The (original) format of this receive stream.
         */
        var format: Format? = null

        /**
         * The `SilenceEffect` used for this stream (for audio streams only).
         */
        var silenceEffect: SilenceEffect? = null

    }

    companion object {
        /**
         * The `ConfigurationService` used to load recorder configuration.
         */
        private val cfg = LibJitsi.configurationService

        // values hard-coded to match chrome =>
        // TODO: allow to set them dynamically
        // Issue 6705: Stop using hardcoded payload types for VideoCodecs
        // https://bugs.chromium.org/p/webrtc/issues/detail?id=6705
        private const val redPayloadType: Byte = 116
        private const val ulpfecPayloadType: Byte = 117
        private const val vp8PayloadType: Byte = 96
        private const val opusPayloadType: Byte = 111
        private val redFormat = VideoFormat(Constants.RED)
        private val ulpfecFormat = VideoFormat(Constants.ULPFEC)
        private val vp8RtpFormat = VideoFormat(Constants.VP8_RTP)
        private val vp8Format = VideoFormat(Constants.VP8)

        private val opusFormat = AudioFormat(
                Constants.OPUS_RTP,
                48000.0,
                Format.NOT_SPECIFIED,
                Format.NOT_SPECIFIED)

        /**
         * Config parameter for FMJ video jitter size
         */
        private const val FMJ_VIDEO_JITTER_BUFFER_MIN_SIZE_PNAME = "neomedia.recording.FMJ_VIDEO_JITTER_BUFFER_MIN_SIZE"
        private val FMJ_VIDEO_JITTER_BUFFER_MIN_SIZE = cfg.getInt(FMJ_VIDEO_JITTER_BUFFER_MIN_SIZE_PNAME, 300)

        /**
         * Config parameter for FMJ audio jitter size
         */
        private const val FMJ_AUDIO_JITTER_BUFFER_MIN_SIZE_PNAME = "neomedia.recording.FMJ_AUDIO_JITTER_BUFFER_MIN_SIZE_PNAME"
        private val FMJ_AUDIO_JITTER_BUFFER_MIN_SIZE = cfg.getInt(FMJ_AUDIO_JITTER_BUFFER_MIN_SIZE_PNAME, 16)

        /**
         * The name of the property which controls whether the recorder should
         * perform active speaker detection.
         */
        private const val PERFORM_ASD_PNAME = "neomedia.recording.PERFORM_ASD"

        /**
         * The name of the property which sets a custom output audio codec. Currently only WAV is supported
         */
        private const val AUDIO_CODEC_PNAME = "neomedia.recording.AUDIO_CODEC"

        /**
         * The `ContentDescriptor` to use when saving audio.
         */
        private var AUDIO_CONTENT_DESCRIPTOR = ContentDescriptor(FileTypeDescriptor.MPEG_AUDIO)

        /**
         * The suffix for audio file names.
         */
        private var AUDIO_FILENAME_SUFFIX = ".mp3"

        /**
         * The suffix for video file names.
         */
        private const val VIDEO_FILENAME_SUFFIX = ".webm"

        init {
            Registry.set("video_jitter_buffer_MIN_SIZE",  FMJ_VIDEO_JITTER_BUFFER_MIN_SIZE)
            Registry.set("adaptive_jitter_buffer_MIN_SIZE", FMJ_AUDIO_JITTER_BUFFER_MIN_SIZE)
        }
    }
}