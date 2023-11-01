/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.codec.video.AndroidEncoder
import org.atalk.impl.neomedia.control.ImgStreamingControl
import org.atalk.impl.neomedia.device.DeviceConfiguration
import org.atalk.impl.neomedia.device.DeviceSystem
import org.atalk.impl.neomedia.device.MediaDeviceImpl
import org.atalk.impl.neomedia.device.MediaDeviceSession
import org.atalk.impl.neomedia.device.ScreenDeviceImpl
import org.atalk.impl.neomedia.device.VideoMediaDeviceSession
import org.atalk.impl.neomedia.rtcp.RTCPReceiverFeedbackTermination
import org.atalk.impl.neomedia.rtp.StreamRTPManager
import org.atalk.impl.neomedia.rtp.VideoMediaStreamTrackReceiver
import org.atalk.impl.neomedia.rtp.remotebitrateestimator.RemoteBitrateEstimatorWrapper
import org.atalk.impl.neomedia.rtp.remotebitrateestimator.RemoteBitrateObserver
import org.atalk.impl.neomedia.rtp.sendsidebandwidthestimation.BandwidthEstimatorImpl
import org.atalk.impl.neomedia.transform.CachingTransformer
import org.atalk.impl.neomedia.transform.PaddingTermination
import org.atalk.impl.neomedia.transform.RetransmissionRequesterImpl
import org.atalk.impl.neomedia.transform.RtxTransformer
import org.atalk.impl.neomedia.transform.TransformEngine
import org.atalk.impl.neomedia.transform.TransformEngineWrapper
import org.atalk.impl.neomedia.transform.fec.FECTransformEngine
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.QualityPreset
import org.atalk.service.neomedia.SrtpControl
import org.atalk.service.neomedia.StreamConnector
import org.atalk.service.neomedia.VideoMediaStream
import org.atalk.service.neomedia.VideoMediaStream.Companion.REQUEST_RETRANSMISSIONS_PNAME
import org.atalk.service.neomedia.control.KeyFrameControl
import org.atalk.service.neomedia.control.KeyFrameControlAdapter
import org.atalk.service.neomedia.device.MediaDevice
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.service.neomedia.rtp.BandwidthEstimator
import org.atalk.util.OSUtils
import org.atalk.util.concurrent.RecurringRunnableExecutor
import org.atalk.util.event.VideoEvent
import org.atalk.util.event.VideoListener
import org.atalk.util.event.VideoNotifierSupport
import timber.log.Timber
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.media.Format
import javax.media.control.BufferControl
import javax.media.control.FormatControl
import javax.media.format.VideoFormat
import javax.media.format.YUVFormat
import javax.media.protocol.DataSource
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Extends `MediaStreamImpl` in order to provide an implementation of `VideoMediaStream`.
 *
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
open class VideoMediaStreamImpl(connector: StreamConnector?, device: MediaDevice?, srtpControl: SrtpControl?) : MediaStreamImpl(connector, device, srtpControl), VideoMediaStream {
    /**
     * The `VideoListener` which handles `VideoEvent`s from the `MediaDeviceSession` of this
     * instance and fires respective `VideoEvent`s from this `VideoMediaStream` to its `VideoListener`s.
     */
    private var deviceSessionVideoListener: VideoListener? = null
    /**
     * Implements VideoMediaStream.getKeyFrameControl.
     *
     * {@inheritDoc}
     *
     * @see VideoMediaStream.keyFrameControl
     */
    /**
     * The `KeyFrameControl` of this `VideoMediaStream`.
     */
    final override var keyFrameControl: KeyFrameControl? = null
        get() {
            if (field == null) field = KeyFrameControlAdapter()
            return field
        }
        private set

    /**
     * Negotiated output size of the video stream. It may need to scale original capture device stream.
     */
    private var outputSize: Dimension? = null

    /**
     * {@inheritDoc}
     */
    /**
     * The instance that is aware of all of the RTPEncodingDesc of the remote endpoint.
     */
    override val mediaStreamTrackReceiver = VideoMediaStreamTrackReceiver(this)
    /**
     * {@inheritDoc}
     */
    /**
     * The transformer which handles outgoing rtx (RFC-4588) packets for this [VideoMediaStreamImpl].
     */
    override val rtxTransformer = RtxTransformer(this)
    /**
     * {@inheritDoc}
     */
    /**
     * The transformer which handles incoming and outgoing fec
     */
    override var fecTransformEngineWrapper: TransformEngineWrapper<FECTransformEngine>? = null

    /**
     * {@inheritDoc}
     *
     * @param fecTransformEngine
     */
    override fun setFecTransformEngine(fecTransformEngine: FECTransformEngine) {
        fecTransformEngineWrapper!!.wrapped = fecTransformEngine
    }

    /**
     * The instance that terminates RRs and REMBs.
     */
    private val rtcpFeedbackTermination = RTCPReceiverFeedbackTermination(this)
    /**
     * {@inheritDoc}
     */
    /**
     *
     */
    override val paddingTermination = PaddingTermination()
    /**
     * {@inheritDoc}
     */
    /**
     * The `RemoteBitrateEstimator` which computes bitrate estimates for the incoming RTP streams.
     */
    override val remoteBitrateEstimator = RemoteBitrateEstimatorWrapper(object : RemoteBitrateObserver {
        override fun onReceiveBitrateChanged(ssrcs: Collection<Long>, bitrate: Long) {
            remoteBitrateEstimatorOnReceiveBitrateChanged(ssrcs, bitrate)
        }
    }, getDiagnosticContext()
    )

    /**
     * The facility which aids this instance in managing a list of `VideoListener`s and
     * firing `VideoEvent`s to them.
     *
     *
     * Since the `videoNotifierSupport` of this `VideoMediaStreamImpl` just forwards
     * the `VideoEvent`s of the associated `VideoMediaDeviceSession` at the time of
     * this writing, it does not make sense to have `videoNotifierSupport` executing
     * asynchronously because it does not know whether it has to wait for the delivery of the
     * `VideoEvent`s and thus it has to default to waiting anyway.
     *
     */
    private val videoNotifierSupport = VideoNotifierSupport(this, true)

    /**
     * The `BandwidthEstimator` which estimates the available bandwidth from this endpoint to the remote peer.
     */
    private var bandwidthEstimator: BandwidthEstimatorImpl? = null

    /**
     * The [CachingTransformer] which caches outgoing/incoming packets from/to this [VideoMediaStreamImpl].
     */
    override var cachingTransformer: CachingTransformer? = null

    /**
     * Whether the remote end supports RTCP FIR.
     */
    private var supportsFir = false

    /**
     * Whether the remote end supports RTCP PLI.
     */
    private var supportsPli = false

    /**
     * Initializes a new `VideoMediaStreamImpl` instance which will use the specified `MediaDevice`
     * for both capture and playback of video exchanged via the specified `StreamConnector`.
     *
     * connector the `StreamConnector` the new instance is to use for sending and receiving video
     * device the `MediaDevice` the new instance is to use for both capture and playback of
     * video exchanged via the specified `StreamConnector`
     * srtpControl a control which is already created, used to control the srtp operations.
     */
    init {
        recurringRunnableExecutor.registerRecurringRunnable(rtcpFeedbackTermination)
    }

    /**
     * Sets the value of the flag which indicates whether the remote end supports RTCP FIR or not.
     *
     * @param supportsFir the value to set.
     */
    fun setSupportsFir(supportsFir: Boolean) {
        this.supportsFir = supportsFir
    }

    /**
     * Sets the value of the flag which indicates whether the remote end supports RTCP PLI or not.
     *
     * @param supportsPli the value to set.
     */
    fun setSupportsPli(supportsPli: Boolean) {
        this.supportsPli = supportsPli
    }

    /**
     * Sets the value of the flag which indicates whether the remote end supports RTCP REMB or not.
     *
     * @param supportsRemb the value to set.
     */
    fun setSupportsRemb(supportsRemb: Boolean) {
        remoteBitrateEstimator.setSupportsRemb(supportsRemb)
    }

    /**
     * @return `true` iff the remote end supports RTCP FIR.
     */
    fun supportsFir(): Boolean {
        return supportsFir
    }

    /**
     * @return `true` iff the remote end supports RTCP PLI.
     */
    fun supportsPli(): Boolean {
        return supportsPli
    }

    /**
     * Set remote SSRC.
     *
     * @param remoteSourceID remote SSRC
     */
    override fun addRemoteSourceID(remoteSourceID: Long) {
        super.addRemoteSourceID(remoteSourceID)
        val deviceSession = getDeviceSession()
        if (deviceSession is VideoMediaDeviceSession) deviceSession.setRemoteSSRC(remoteSourceID)
    }

    /**
     * Adds a specific `VideoListener` to this `VideoMediaStream` in order to receive
     * notifications when visual/video `Component`s are being added and removed.
     *
     *
     * Adding a listener which has already been added does nothing i.e. it is not added more than
     * once and thus does not receive one and the same `VideoEvent` multiple times.
     *
     *
     * @param listener the `VideoListener` to be notified when visual/video `Component`s are
     * being added or removed in this `VideoMediaStream`
     */
    override fun addVideoListener(listener: VideoListener) {
        videoNotifierSupport.addVideoListener(listener)
    }

    /**
     * {@inheritDoc}
     */
    override fun close() {
        try {
            super.close()
        } finally {
            if (cachingTransformer != null) {
                recurringRunnableExecutor.deRegisterRecurringRunnable(cachingTransformer)
            }
            if (bandwidthEstimator != null) {
                recurringRunnableExecutor.deRegisterRecurringRunnable(bandwidthEstimator)
            }
            if (rtcpFeedbackTermination != null) {
                recurringRunnableExecutor.deRegisterRecurringRunnable(rtcpFeedbackTermination)
            }
        }
    }

    /**
     * Performs any optional configuration on a specific `RTPConnectorOuputStream` of an
     * `RTPManager` to be used by this `MediaStreamImpl`.
     *
     * @param dataOutputStream the `RTPConnectorOutputStream` to be used by an `RTPManager` of this
     * `MediaStreamImpl` and to be configured
     */
    override fun configureDataOutputStream(dataOutputStream: RTPConnectorOutputStream) {
        super.configureDataOutputStream(dataOutputStream)

        /*
         * XXX Android's current video CaptureDevice is based on MediaRecorder which gives no
         * control over the number and the size of the packets, frame dropping is not implemented
         * because it is hard since MediaRecorder generates encoded video.
         */
        if (!OSUtils.IS_ANDROID) {
            val maxBandwidth = NeomediaServiceUtils.mediaServiceImpl!!.deviceConfiguration.videoRTPPacingThreshold

            // Ignore the case of maxBandwidth > 1000, because in this case
            // setMaxPacketsPerMillis fails. Effectively, this means that no pacing is performed
            // when the user deliberately set the setting to over 1000 (1MByte/s according to the
            // GUI). This is probably close to what the user expects, and makes more sense than
            // failing with an exception.
            // TODO: proper handling of maxBandwidth values >1000
            if (maxBandwidth <= 1000) {
                // maximum one packet for X milliseconds(the settings are for one second)
                dataOutputStream.setMaxPacketsPerMillis(1, (1000L / maxBandwidth))
            }
        }
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
        super.configureRTPManagerBufferControl(rtpManager, bufferControl)
        bufferControl!!.bufferLength = BufferControl.MAX_VALUE
    }

    /**
     * Notifies this `MediaStream` that the `MediaDevice` (and respectively the
     * `MediaDeviceSession` with it) which this instance uses for capture and playback of
     * media has been changed. Makes sure that the `VideoListener`s of this instance get
     * `VideoEvent`s for the new/current `VideoMediaDeviceSession` and not for the old one.
     *
     *
     * Note: this overloaded method gets executed in the `MediaStreamImpl` constructor. As a
     * consequence we cannot assume proper initialization of the fields specific to `VideoMediaStreamImpl`.
     *
     * @param oldValue the `MediaDeviceSession` with the `MediaDevice` this instance used work with
     * @param newValue the `MediaDeviceSession` with the `MediaDevice` this instance is to work with
     * @see MediaStreamImpl.deviceSessionChanged
     */
    override fun deviceSessionChanged(oldValue: MediaDeviceSession?, newValue: MediaDeviceSession?) {
        super.deviceSessionChanged(oldValue, newValue)
        if (oldValue is VideoMediaDeviceSession) {
            if (deviceSessionVideoListener != null) oldValue.removeVideoListener(deviceSessionVideoListener!!)

            /*
             * The oldVideoMediaDeviceSession is being disconnected from this VideoMediaStreamImpl
             * so do not let it continue using its keyFrameControl.
             */
            oldValue.setKeyFrameControl(null)
        }

        if (newValue is VideoMediaDeviceSession) {
            if (deviceSessionVideoListener == null) {
                deviceSessionVideoListener = object : VideoListener {
                    /**
                     * {@inheritDoc}
                     *
                     * Notifies that a visual `Component` depicting video was reported added
                     * by the provider this listener is added to.
                     */
                    override fun videoAdded(event: VideoEvent) {
                        if (fireVideoEvent(event.type, event.visualComponent, event.origin, true)) event.consume()
                    }

                    /**
                     * {@inheritDoc}
                     *
                     * Notifies that a visual `Component` depicting video was reported
                     * removed by the provider this listener is added to.
                     */
                    override fun videoRemoved(event: VideoEvent) {
                        // Process in the same way as VIDEO_ADDED.
                        videoAdded(event)
                    }

                    /**
                     * {@inheritDoc}
                     *
                     * Notifies that a visual `Component` depicting video was reported
                     * updated by the provider this listener is added to.
                     */
                    override fun videoUpdate(event: VideoEvent) {
                        fireVideoEvent(event, true)
                    }
                }
            }
            newValue.addVideoListener(deviceSessionVideoListener!!)
            newValue.setOutputSize(outputSize)
            val rtpConnector = rtpConnector
            if (rtpConnector != null) newValue.setConnector(rtpConnector)
            newValue.setRTCPFeedbackPLI(USE_RTCP_FEEDBACK_PLI)

            /*
             * The newVideoMediaDeviceSession is being connected to this VideoMediaStreamImpl so the key
             * frame-related logic will be controlled by the keyFrameControl of this VideoMediaStreamImpl.
             */
            newValue.setKeyFrameControl(keyFrameControl)
        }
    }

    /**
     * Notifies the `VideoListener`s registered with this `VideoMediaStream` about a
     * specific type of change in the availability of a specific visual `Component` depicting video.
     *
     * @param type the type of change as defined by `VideoEvent` in the availability of the
     * specified visual `Component` depicting video
     * @param visualComponent the visual `Component` depicting video which has been added or removed in this
     * `VideoMediaStream`
     * @param origin [VideoEvent.LOCAL] if the origin of the video is local (e.g. it is being locally
     * captured); [VideoEvent.REMOTE] if the origin of the video is remote (e.g. a remote peer is streaming it)
     * @param wait `true` if the call is to wait till the specified `VideoEvent` has been
     * delivered to the `VideoListener`s; otherwise, `false`
     * @return `true` if this event and, more specifically, the visual `Component` it
     * describes have been consumed and should be considered owned, referenced (which is important because
     * `Component`s belong to a single `Container` at a time); otherwise, `false`
     */
    protected fun fireVideoEvent(type: Int, visualComponent: Component?, origin: Int, wait: Boolean): Boolean {
        Timber.log(TimberLog.FINER, "Firing VideoEvent with type %s and origin %s",
                VideoEvent.typeToString(type), VideoEvent.originToString(origin))
        return videoNotifierSupport.fireVideoEvent(type, visualComponent, origin, wait)
    }

    /**
     * Notifies the `VideoListener`s registered with this instance about a specific `VideoEvent`.
     *
     * @param event the `VideoEvent` to be fired to the `VideoListener`s registered with this instance
     * @param wait `true` if the call is to wait till the specified `VideoEvent` has been
     * delivered to the `VideoListener`s; otherwise, `false`
     */
    protected fun fireVideoEvent(event: VideoEvent?, wait: Boolean) {
        videoNotifierSupport.fireVideoEvent(event!!, wait)
    }

    /**
     * Gets the visual `Component`, if any, depicting the video streamed from the local peer to the remote peer.
     *
     * @return the visual `Component` depicting the local video if local video is actually
     * being streamed from the local peer to the remote peer; otherwise, `null`
     */
    override val localVisualComponent: Component?
        get() {
            val deviceSession = getDeviceSession()
            return if (deviceSession is VideoMediaDeviceSession) deviceSession.localVisualComponent else null
        }

    /**
     * The priority of the video is 5, which is meant to be higher than other threads and lower than the audio one.
     *
     * @return video priority.
     */
    override val priority = 5

    /**
     * The `QualityControl` of this `VideoMediaStream`.
     */
    override val qualityControl = QualityControlImpl()

    /**
     * Gets the visual `Component` where video from the remote peer is being rendered or
     * `null` if no video is currently being rendered.
     *
     * @return the visual `Component` where video from the remote peer is being rendered or
     * `null` if no video is currently being rendered
     * @see VideoMediaStream.getVisualComponent
     */
    @get:Deprecated("")
    override val visualComponent: Component?
        get() {
            val visualComponents = visualComponents
            return if (visualComponents!!.isEmpty()) null else visualComponents[0]
        }

    /**
     * Gets the visual `Component`s rendering the `ReceiveStream` corresponding to the given ssrc.
     *
     * @param ssrc the src-id of the receive stream, which visual `Component` we're looking for
     * @return the visual `Component` rendering the `ReceiveStream` corresponding to the given ssrc
     */
    override fun getVisualComponent(ssrc: Long): Component? {
        val deviceSession = getDeviceSession()
        return if (deviceSession is VideoMediaDeviceSession) deviceSession.getVisualComponent(ssrc) else null
    }

    /**
     * Gets a list of the visual `Component`s where video from the remote peer is being rendered.
     *
     * @return a list of the visual `Component`s where video from the remote peer is being rendered
     * @see VideoMediaStream.visualComponents
     */
    override val visualComponents: List<Component>?
        get() {
            val deviceSession = getDeviceSession()
            val visualComponents = if (deviceSession is VideoMediaDeviceSession) {
                deviceSession.visualComponents
            } else emptyList()
            return visualComponents
        }

    /**
     * Handles attributes contained in `MediaFormat`.
     *
     * @param format the `MediaFormat` to handle the attributes of
     * @param attrs the attributes `Map` to handle
     */
    override fun handleAttributes(format: MediaFormat, attrs: Map<String, String>?) {
        // Keep a reference copy for use in selectVideoSize()
        mFormat = format

        /*
         * Iterate over the specified attributes and handle those of them which we recognize.
         */
        if (attrs != null) {
            /*
             * The width and height attributes are separate but they have to be collected into a
             * Dimension in order to be handled.
             */
            var width: String? = null
            var height: String? = null
            var dim: Dimension
            for ((key, value) in attrs) {
                when (key) {
                    "rtcp-fb" -> {}
                    "imageattr" -> {
                        /*
                         * If the width and height attributes have been collected into
                         * outputSize, do not override the Dimension they have specified.
                         */
                        if ((attrs.containsKey("width") || attrs.containsKey("height")) && outputSize != null) {
                            continue
                        }
                        val res = parseSendRecvResolution(value)
                        setOutputSize(res[1])
                        qualityControl.setRemoteSendMaxPreset(QualityPreset(res[0]))
                        qualityControl.setRemoteReceiveResolution(outputSize)
                        (getDeviceSession() as VideoMediaDeviceSession).setOutputSize(outputSize)
                    }
                    "CIF" -> {
                        dim = Dimension(352, 288)
                        if ((outputSize == null || outputSize!!.width < dim.width && outputSize!!.height < dim.height)) {
                            setOutputSize(dim)
                            (getDeviceSession() as VideoMediaDeviceSession).setOutputSize(outputSize)
                        }
                    }
                    "QCIF" -> {
                        dim = Dimension(176, 144)
                        if ((outputSize == null || outputSize!!.width < dim.width && outputSize!!.height < dim.height)) {
                            setOutputSize(dim)
                            (getDeviceSession() as VideoMediaDeviceSession).setOutputSize(outputSize)
                        }
                    }
                    "VGA" -> {
                        dim = Dimension(640, 480)
                        if (outputSize == null || outputSize!!.width < dim.width && outputSize!!.height < dim.height) {
                            // X-Lite does not display anything if we send 640x480.
                            setOutputSize(dim)
                            (getDeviceSession() as VideoMediaDeviceSession).setOutputSize(outputSize)
                        }
                    }
                    "CUSTOM" -> {
                        val args = value.split(",")
                        if (args.size < 3) continue
                        try {
                            dim = Dimension(args[0].toInt(), args[1].toInt())
                            if ((outputSize == null || outputSize!!.width < dim.width && outputSize!!.height < dim.height)) {
                                setOutputSize(dim)
                                (getDeviceSession() as VideoMediaDeviceSession).setOutputSize(outputSize)
                            }
                        } catch (e: Exception) {
                            Timber.e("Exception in handle attribute: %s", e.message)
                        }
                    }
                    "width" -> {
                        width = value
                        if (height != null) {
                            setOutputSize(Dimension(width.toInt(), height.toInt()))
                            (getDeviceSession() as VideoMediaDeviceSession).setOutputSize(outputSize)
                        }
                    }
                    "height" -> {
                        height = value
                        if (width != null) {
                            setOutputSize(Dimension(width.toInt(), height.toInt()))
                            (getDeviceSession() as VideoMediaDeviceSession).setOutputSize(outputSize)
                        }
                    }
                }
            }
        }
    }

    /**
     * Move origin of a partial desktop streaming `MediaDevice`.
     *
     * @param x new x coordinate origin
     * @param y new y coordinate origin
     */
    override fun movePartialDesktopStreaming(x: Int, y: Int) {
        val dev = device as MediaDeviceImpl
        if (DeviceSystem.LOCATOR_PROTOCOL_IMGSTREAMING != dev.getCaptureDeviceInfoLocatorProtocol()) {
            return
        }
        val captureDevice = getDeviceSession()!!.getCaptureDevice()!!
        val imgStreamingControl = captureDevice.getControl(ImgStreamingControl::class.java.name)
                ?: return

        // Makes the screen detection with a point inside a real screen i.e.
        // x and y are both greater than or equal to 0.
        val screen = NeomediaServiceUtils.mediaServiceImpl!!.getScreenForPoint(Point(max(x, 0), max(y, 0)))
        if (screen != null) {
            val bounds = (screen as ScreenDeviceImpl).bounds
            (imgStreamingControl as ImgStreamingControl).setOrigin(0, screen.index,
                    x - bounds.x, y - bounds.y)
        }
    }

    /**
     * Notifies this `VideoMediaStreamImpl` that [.remoteBitrateEstimator] has
     * computed a new bitrate estimate for the incoming streams.
     *
     * @param ssrcs Remote source
     * @param bitrate Source bitRate
     */
    private fun remoteBitrateEstimatorOnReceiveBitrateChanged(ssrcs: Collection<Long>, bitrate: Long) {
        // TODO Auto-generated method stub
    }

    /**
     * Removes a specific `VideoListener` from this `VideoMediaStream` in order to have to
     * no longer receive notifications when visual/video `Component`s are being added and removed.
     *
     * @param listener the `VideoListener` to no longer be notified when visual/video
     * `Component`s are being added or removed in this `VideoMediaStream`
     */
    override fun removeVideoListener(listener: VideoListener?) {
        videoNotifierSupport.removeVideoListener(listener!!)
    }

    /**
     * Notifies this `MediaStream` implementation that its `RTPConnector` instance
     * has changed from a specific old value to a specific new value. Allows extenders to
     * override and perform additional processing after this `MediaStream` has changed its
     * `RTPConnector` instance.
     *
     * @param oldValue the `RTPConnector` of this `MediaStream` implementation before it got
     * changed to `newValue`
     * @param newValue the current `RTPConnector` of this `MediaStream` which replaced `oldValue`
     * @see MediaStreamImpl.rtpConnectorChanged
     */
    override fun rtpConnectorChanged(oldValue: AbstractRTPConnector?, newValue: AbstractRTPConnector?) {
        super.rtpConnectorChanged(oldValue, newValue)
        if (newValue != null) {
            val deviceSession = getDeviceSession()
            Timber.w("rtpConnectorChanged: %s => %s", deviceSession!!.javaClass.simpleName,
                    newValue.connector.dataSocket!!.inetAddress)
            if (deviceSession is VideoMediaDeviceSession) {
                deviceSession.setConnector(newValue)
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun setLocalSourceID(localSourceID: Long) {
        super.setLocalSourceID(localSourceID)
        val deviceSession = getDeviceSession()
        if (deviceSession is VideoMediaDeviceSession) {
            deviceSession.setLocalSSRC(localSourceID)
        }
    }

    /**
     * Sets the size/resolution of the video to be output by this instance.
     *
     * @param outputSize the size/resolution of the video to be output by this instance
     */
    private fun setOutputSize(outputSize: Dimension?) {
        this.outputSize = outputSize
    }

    /**
     * Updates the `QualityControl` of this `VideoMediaStream`.
     *
     * @param advancedParams parameters of advanced attributes that may affect quality control
     */
    override fun updateQualityControl(advancedParams: Map<String, String>?) {
        for ((key, value) in advancedParams!!) {
            if (key == "imageattr") {
                val res = parseSendRecvResolution(value)
                qualityControl.setRemoteSendMaxPreset(QualityPreset(res[0]))
                qualityControl.setRemoteReceiveResolution(res[1]!!)
                setOutputSize(res[1])
                (getDeviceSession() as VideoMediaDeviceSession).setOutputSize(outputSize)
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun createCachingTransformer(): CachingTransformer {
        if (cachingTransformer == null) {
            cachingTransformer = CachingTransformer(this)
            recurringRunnableExecutor.registerRecurringRunnable(cachingTransformer!!)
        }
        return cachingTransformer!!
    }

    /**
     * {@inheritDoc}
     */
    override fun createRetransmissionRequester(): RetransmissionRequesterImpl? {
        val cfg = LibJitsi.configurationService
        return if (cfg.getBoolean(REQUEST_RETRANSMISSIONS_PNAME, false)) {
            RetransmissionRequesterImpl(this)
        } else null
    }

    /**
     * {@inheritDoc}
     */
    override val rtcpTermination: TransformEngine?
        get() = rtcpFeedbackTermination

    /**
     * {@inheritDoc}
     */
    override val orCreateBandwidthEstimator: BandwidthEstimator
        get() {
            if (bandwidthEstimator == null) {
                bandwidthEstimator = BandwidthEstimatorImpl(this)
                recurringRunnableExecutor.registerRecurringRunnable(bandwidthEstimator!!)
                Timber.i("Creating a BandwidthEstimator for stream %s", this)
            }
            return bandwidthEstimator!!
        }

    companion object {
        /**
         * The indicator which determines whether RTCP feedback Picture Loss Indication messages are to be used.
         */
        private const val USE_RTCP_FEEDBACK_PLI = true

        /**
         * The `RecurringRunnableExecutor` to be utilized by the `MediaStreamImpl` class and its instances.
         */
        private val recurringRunnableExecutor = RecurringRunnableExecutor(VideoMediaStreamImpl::class.java.simpleName)

        /**
         * MediaFormat handle by this method
         */
        private var mFormat: MediaFormat? = null

        /**
         * Extracts and returns maximum resolution can receive from the image attribute.
         *
         * imgattr send/recv resolution string
         * @return maximum resolution array (first element is send, second one is recv). Elements could
         * be null if image attribute is not present or if resolution is a wildcard.
         */
        fun parseSendRecvResolution(imgattr: String): Array<Dimension?> {
            val res = arrayOfNulls<Dimension>(2)
            var token: String
            val pSendSingle = Pattern.compile("send \\[x=\\d+,y=\\d+]")
            val pRecvSingle = Pattern.compile("recv \\[x=\\d+,y=\\d+]")
            val pSendRange = Pattern.compile("send \\[x=\\[\\d+([-:])\\d+],y=\\[\\d+([-:])\\d+]]")
            val pRecvRange = Pattern.compile("recv \\[x=\\[\\d+([-:])\\d+],y=\\[\\d+([-:])\\d+]]")
            val pNumeric = Pattern.compile("\\d+")
            var m: Matcher

            /*
         * resolution (width and height) can be on four forms
         *
         * - single value [x=1920,y=1200]
         * - range of values [x=[800:1024],y=[600:768]]
         * - fixed range of values [x=[800,1024],y=[600,768]]
         * - range of values with step [x=[800:32:1024],y=[600:32:768]]
         *
         * For the moment we only support the first two forms.
         */

            /* send part */
            var mSingle = pSendSingle.matcher(imgattr)
            var mRange = pSendRange.matcher(imgattr)
            if (mSingle.find()) {
                val `val` = IntArray(2)
                var i = 0
                token = imgattr.substring(mSingle.start(), mSingle.end())
                m = pNumeric.matcher(token)
                while (m.find() && i < 2) {
                    `val`[i] = token.substring(m.start(), m.end()).toInt()
                    i++
                }
                res[0] = Dimension(`val`[0], `val`[1])
            } else if (mRange.find()) /* try with range */ {
                /* have two value for width and two for height (min-max) */
                val `val` = IntArray(4)
                var i = 0
                token = imgattr.substring(mRange.start(), mRange.end())
                m = pNumeric.matcher(token)
                while (m.find() && i < 4) {
                    `val`[i] = token.substring(m.start(), m.end()).toInt()
                    i++
                }
                res[0] = Dimension(`val`[1], `val`[3])
            }

            /* recv part */
            mSingle = pRecvSingle.matcher(imgattr)
            mRange = pRecvRange.matcher(imgattr)
            if (mSingle.find()) {
                val `val` = IntArray(2)
                var i = 0
                token = imgattr.substring(mSingle.start(), mSingle.end())
                m = pNumeric.matcher(token)
                while (m.find() && i < 2) {
                    `val`[i] = token.substring(m.start(), m.end()).toInt()
                    i++
                }
                res[1] = Dimension(`val`[0], `val`[1])
            } else if (mRange.find()) /* try with range */ {
                /* have two value for width and two for height (min-max) */
                val `val` = IntArray(4)
                var i = 0
                token = imgattr.substring(mRange.start(), mRange.end())
                m = pNumeric.matcher(token)
                while (m.find() && i < 4) {
                    `val`[i] = token.substring(m.start(), m.end()).toInt()
                    i++
                }
                res[1] = Dimension(`val`[1], `val`[3])
            }
            return res
        }

        /**
         * Selects the `VideoFormat` from the list of supported formats of a specific video `DataSource`
         * which has a size as close as possible to a specific size and sets it as the format of the specified video
         * `DataSource`. Must also check if the VideoFormat is supported by the androidEncoder;
         * VP9 encode many not be supported in all android devices.
         *
         * @param videoDS the video `DataSource` which is to have its supported formats examined and its
         * format changed to the `VideoFormat` which is as close as possible to the
         * specified `preferredWidth` and `preferredHeight`
         * @param preferredWidth the width of the `VideoFormat` to be selected
         * @param preferredHeight the height of the `VideoFormat` to be selected
         * @return the size of the `VideoFormat` from the list of supported formats of
         * `videoDS` which is as close as possible to `preferredWidth` and
         * `preferredHeight` and which has been set as the format of `videoDS`
         */
        fun selectVideoSize(videoDS: DataSource?, preferredWidth: Int, preferredHeight: Int): Dimension? {
            if (videoDS == null) return null
            val formatControl = videoDS.getControl(FormatControl::class.java.name) as FormatControl
                    ?: return null
            val formats = formatControl.supportedFormats
            val count = formats.size
            if (count < 1) return null
            var selectedFormat: VideoFormat? = null
            if (count == 1) selectedFormat = formats[0] as VideoFormat else {
                class FormatInfo {
                    val difference: Double
                    val dimension: Dimension
                    val format: VideoFormat?

                    constructor(size: Dimension) {
                        format = null
                        dimension = size
                        difference = getDifference(dimension)
                    }

                    constructor(format: VideoFormat) {
                        this.format = format
                        dimension = format.size
                        difference = getDifference(dimension)
                        // Timber.d("format: %s; dimension: %s, difference: %s", format, dimension, difference);
                    }

                    private fun getDifference(size: Dimension?): Double {
                        val xScale = when (val width = size?.width ?: 0) {
                            0 -> Double.POSITIVE_INFINITY
                            preferredWidth -> 1.0
                            else -> preferredWidth / width.toDouble()
                        }
                        val yScale = when (val height = size?.height ?: 0) {
                            0 -> Double.POSITIVE_INFINITY
                            preferredHeight -> 1.0
                            else -> preferredHeight / height.toDouble()
                        }
                        return abs(1 - min(xScale, yScale))
                    }
                }

                // Check to see if the hardware encoder is supported
                val isCodecSupported = AndroidEncoder.isCodecSupported(mFormat!!.encoding)
                val infos = arrayOfNulls<FormatInfo>(count)
                var idx = -1
                for (i in 0 until count) {
                    infos[i] = FormatInfo(formats[i] as VideoFormat)
                    val info = infos[i]
                    if (info!!.difference == 0.0) {
                        if (info.format is YUVFormat || isCodecSupported) {
                            selectedFormat = info.format
                            idx = i
                            break
                        }
                    }
                }
                Timber.d("Selected video format: Count: %s/%s; Dimension: [%s x %s] => %s",
                        idx, count, preferredWidth, preferredHeight, selectedFormat)

                // Select the closest is none has perfect matched in Dimension
                if (selectedFormat == null) {
                    Arrays.sort(infos) { info0: FormatInfo?, info1: FormatInfo? -> info0!!.difference.compareTo(info1!!.difference) }
                    for (i in 0 until count) {
                        if (infos[i]!!.format is YUVFormat || isCodecSupported) {
                            selectedFormat = infos[i]!!.format
                        }
                    }
                }

                /*
                 * If videoDS states to support any size, use the sizes that we support which is
                 * closest(or smaller) to the preferred one.
                 */
                if (selectedFormat != null && selectedFormat.size == null) {
                    val currentFormat = formatControl.format as VideoFormat?
                    var currentSize: Dimension? = null
                    var width = preferredWidth
                    var height = preferredHeight

                    // Try to preserve the aspect ratio
                    if (currentFormat != null) currentSize = currentFormat.size

                    // sort supported resolutions by aspect
                    val supportedInfos = arrayOfNulls<FormatInfo>(DeviceConfiguration.SUPPORTED_RESOLUTIONS.size)
                    for (i in supportedInfos.indices) {
                        supportedInfos[i] = FormatInfo(DeviceConfiguration.SUPPORTED_RESOLUTIONS[i])
                    }
                    Arrays.sort(infos) { info0: FormatInfo?, info1: FormatInfo? -> info0!!.difference.compareTo(info1!!.difference) }
                    val preferredFormat = FormatInfo(Dimension(preferredWidth, preferredHeight))
                    var closestAspect: Dimension? = null
                    // Let's choose the closest size to the preferred one, finding the first suitable aspect
                    for (supported in supportedInfos) {
                        // find the first matching aspect
                        if (preferredFormat.difference > supported!!.difference) continue else if (closestAspect == null) closestAspect = supported.dimension
                        if (supported.dimension.height <= preferredHeight
                                && supported.dimension.width <= preferredWidth) {
                            currentSize = supported.dimension
                        }
                    }
                    if (currentSize == null) currentSize = closestAspect
                    if (currentSize!!.width > 0 && currentSize.height > 0) {
                        width = currentSize.width
                        height = currentSize.height
                    }
                    selectedFormat = VideoFormat(null, Dimension(width, height),
                            Format.NOT_SPECIFIED, null, Format.NOT_SPECIFIED.toFloat()).intersects(selectedFormat) as VideoFormat
                }
            }
            val setFormat = formatControl.setFormat(selectedFormat)
            return if (setFormat is VideoFormat) setFormat.size else null
        }
    }
}