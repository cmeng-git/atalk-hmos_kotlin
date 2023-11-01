/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.codec.REDBlock
import org.atalk.impl.neomedia.codec.REDBlockIterator
import org.atalk.impl.neomedia.device.AbstractMediaDevice
import org.atalk.impl.neomedia.device.MediaDeviceSession
import org.atalk.impl.neomedia.device.ReceiveStreamPushBufferDataSource
import org.atalk.impl.neomedia.device.VideoMediaDeviceSession
import org.atalk.impl.neomedia.format.MediaFormatImpl
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat
import org.atalk.impl.neomedia.rtp.FrameMarkingHeaderExtension
import org.atalk.impl.neomedia.rtp.StreamRTPManager
import org.atalk.impl.neomedia.rtp.TransportCCEngine
import org.atalk.impl.neomedia.rtp.remotebitrateestimator.RemoteBitrateEstimatorWrapper
import org.atalk.impl.neomedia.rtp.translator.RTPTranslatorImpl
import org.atalk.impl.neomedia.stats.MediaStreamStats2Impl
import org.atalk.impl.neomedia.transform.AbsSendTimeEngine
import org.atalk.impl.neomedia.transform.CachingTransformer
import org.atalk.impl.neomedia.transform.DiscardTransformEngine
import org.atalk.impl.neomedia.transform.OriginalHeaderBlockTransformEngine
import org.atalk.impl.neomedia.transform.PaddingTermination
import org.atalk.impl.neomedia.transform.REDTransformEngine
import org.atalk.impl.neomedia.transform.RTPTransformTCPConnector
import org.atalk.impl.neomedia.transform.RTPTransformUDPConnector
import org.atalk.impl.neomedia.transform.RetransmissionRequesterImpl
import org.atalk.impl.neomedia.transform.RtxTransformer
import org.atalk.impl.neomedia.transform.TransformEngine
import org.atalk.impl.neomedia.transform.TransformEngineChain
import org.atalk.impl.neomedia.transform.TransformEngineWrapper
import org.atalk.impl.neomedia.transform.TransformTCPOutputStream
import org.atalk.impl.neomedia.transform.TransformUDPOutputStream
import org.atalk.impl.neomedia.transform.csrc.CsrcTransformEngine
import org.atalk.impl.neomedia.transform.csrc.SsrcTransformEngine
import org.atalk.impl.neomedia.transform.dtmf.DtmfTransformEngine
import org.atalk.impl.neomedia.transform.fec.FECTransformEngine
import org.atalk.impl.neomedia.transform.pt.PayloadTypeTransformEngine
import org.atalk.impl.neomedia.transform.rtcp.StatisticsEngine
import org.atalk.impl.neomedia.transform.zrtp.ZRTPTransformEngine
import org.atalk.service.neomedia.AbstractMediaStream
import org.atalk.service.neomedia.AudioMediaStream
import org.atalk.service.neomedia.MediaDirection
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.MediaStreamTarget
import org.atalk.service.neomedia.RTPExtension
import org.atalk.service.neomedia.RTPTranslator
import org.atalk.service.neomedia.RawPacket
import org.atalk.service.neomedia.RetransmissionRequester
import org.atalk.service.neomedia.SSRCFactory
import org.atalk.service.neomedia.SrtpControl
import org.atalk.service.neomedia.SrtpControlType
import org.atalk.service.neomedia.StreamConnector
import org.atalk.service.neomedia.TransmissionFailedException
import org.atalk.service.neomedia.VideoMediaStream
import org.atalk.service.neomedia.codec.Constants
import org.atalk.service.neomedia.control.PacketLossAwareEncoder
import org.atalk.service.neomedia.device.MediaDevice
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.util.ByteArrayBuffer
import org.atalk.util.MediaType
import org.atalk.util.RTPUtils
import org.atalk.util.logging.DiagnosticContext
import timber.log.Timber
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.media.Format
import javax.media.control.BufferControl
import javax.media.format.UnsupportedFormatException
import javax.media.protocol.DataSource
import javax.media.protocol.PullBufferDataSource
import javax.media.protocol.PullDataSource
import javax.media.protocol.PushBufferDataSource
import javax.media.protocol.PushDataSource
import javax.media.rtp.RTPStream
import javax.media.rtp.ReceiveStream
import javax.media.rtp.ReceiveStreamListener
import javax.media.rtp.RemoteListener
import javax.media.rtp.SendStream
import javax.media.rtp.SendStreamListener
import javax.media.rtp.SessionAddress
import javax.media.rtp.SessionListener
import javax.media.rtp.event.NewReceiveStreamEvent
import javax.media.rtp.event.NewSendStreamEvent
import javax.media.rtp.event.ReceiveStreamEvent
import javax.media.rtp.event.ReceiverReportEvent
import javax.media.rtp.event.RemoteEvent
import javax.media.rtp.event.RemotePayloadChangeEvent
import javax.media.rtp.event.SendStreamEvent
import javax.media.rtp.event.SenderReportEvent
import javax.media.rtp.event.SessionEvent
import javax.media.rtp.event.TimeoutEvent
import javax.media.rtp.rtcp.Feedback
import javax.media.rtp.rtcp.Report
import javax.media.rtp.rtcp.SenderReport
import kotlin.math.min

/**
 * Implements `MediaStream` using JMF.
 *
 * @author Lyubomir Marinov
 * @author Emil Ivov
 * @author Sebastien Vincent
 * @author Boris Grozev
 * @author George Politis
 * @author Eng Chong Meng
 * @author MilanKral
 * @author Eng Chong Meng
 */
open class MediaStreamImpl(
        connector: StreamConnector?,
        device: MediaDevice?,
        srtpControl: SrtpControl?,
) : AbstractMediaStream(), ReceiveStreamListener, SendStreamListener, SessionListener, RemoteListener {
    /**
     * The map of currently active `RTPExtension`s and the IDs that they have been assigned
     * for the lifetime of this `MediaStream`.
     */

    private val activeRTPExtensions = Hashtable<Byte, RTPExtension>()

    /**
     * The engine that we are using in order to add CSRC lists in conference calls, send CSRC sound
     * levels, and handle incoming levels and CSRC lists.
     */
    var csrcEngine: CsrcTransformEngine? = null

    private var mDeviceSession: MediaDeviceSession? = null

    /**
     * The session with the `MediaDevice` this instance uses for both capture and playback of media.
     */
    open fun getDeviceSession(): MediaDeviceSession? {
        return mDeviceSession
    }

    /**
     * The `PropertyChangeListener` which listens to [.deviceSession] and changes in
     * the values of its [MediaDeviceSession.OUTPUT_DATA_SOURCE] property.
     */
    private val deviceSessionPropertyChangeListener = PropertyChangeListener { ev: PropertyChangeEvent ->
        when (ev.propertyName) {
            MediaDeviceSession.OUTPUT_DATA_SOURCE -> deviceSessionOutputDataSourceChanged()
            MediaDeviceSession.SSRC_LIST -> deviceSessionSsrcListChanged(ev)
        }
    }

    /**
     * The `MediaDirection` in which this `MediaStream` is allowed to stream media.
     */
    private var mDirection: MediaDirection? = null

    /**
     * Gets the existing associations in this `MediaStream` of RTP payload types to
     * `MediaFormat`s. The returned `Map` only contains associations previously added
     * in this instance with [.addDynamicRTPPayloadType] and not globally
     * or well-known associations reported by [MediaFormat.rtpPayloadType].
     *
     * @return a `Map` of RTP payload type expressed as `Byte` to
     * `MediaFormat` describing the existing (dynamic) associations in this instance of
     * RTP payload types to `MediaFormat`s. The `Map` represents a snapshot of the
     * existing associations at the time of the `getDynamicRTPPayloadTypes()` method call
     * and modifications to it are not reflected on the internal storage
     * @see MediaStream.getDynamicRTPPayloadTypes
     */
    /**
     * The `Map` of associations in this `MediaStream` and the `RTPManager` it
     * utilizes of (dynamic) RTP payload types to `MediaFormat`s.
     */
    private val dynamicRTPPayloadTypes = HashMap<Byte, MediaFormat>()

    /**
     * Returns the list of CSRC identifiers for all parties currently known to contribute to the
     * media that this stream is sending toward its remote counter part. In other words, the method
     * returns the list of CSRC IDs that this stream will include in outgoing RTP packets. This
     * method will return an `null` in case this stream is not part of a mixed conference call.
     *
     * @return a `long[]` array of CSRC IDs representing parties that are currently known to
     * contribute to the media that this stream is sending or an `null` in case this
     * `MediaStream` is not part of a conference call.
     */
    /**
     * The list of CSRC IDs contributing to the media that this `MediaStream` is sending to its remote party.
     */
    var localContributingSourceIDs: LongArray? = null
        private set

    /**
     * Our own SSRC identifier.
     *
     * XXX(gp) how about taking the local source ID directly from [rtpManager], given
     * that it offers this information with its getLocalSSRC() method? TAG(cat4-local-ssrc-hurricane)
     */
    private var mLocalSourceID = Random().nextInt().toLong() and 0x00000000FFFFFFFFL

    /**
     * The MediaStreamStatsImpl object used to compute the statistics about this MediaStreamImpl.
     */
    private val mediaStreamStatsImpl: MediaStreamStats2Impl

    /**
     * The indicator which determines whether this `MediaStream` is set to transmit
     * "silence" instead of the actual media fed from its `MediaDevice`.
     */
    private var mute = false

    /**
     * Number of received receiver reports. Used for logging and debugging only.
     */
    private var numberOfReceivedReceiverReports = 0L

    /**
     * Number of received sender reports. Used for logging and debugging only.
     */
    private var numberOfReceivedSenderReports = 0L

    /**
     * Engine chain overriding payload type if needed.
     */
    private var ptTransformEngine: PayloadTypeTransformEngine? = null

    /**
     * The `ReceiveStream`s this instance plays back on its associated `MediaDevice`.
     * The (read and write) accesses to the field are to be synchronized using [.receiveStreamsLock].
     */
    val receiveStreams = LinkedList<ReceiveStream?>()

    /**
     * The `ReadWriteLock` which synchronizes the (read and write) accesses to [.receiveStreams].
     */
    private val receiveStreamsLock = ReentrantReadWriteLock()

    /**
     * The SSRC identifiers of the party that we are exchanging media with.
     *
     * XXX(gp) I'm sure there's a reason why we do it the way we do it, but we might want to
     * re-think about how we manage receive SSRCs. We keep track of the receive SSRC in at least 3
     * places, in the MediaStreamImpl (we have a remoteSourceIDs vector), in
     * StreamRTPManager.receiveSSRCs and in RtpChannel.receiveSSRCs. TAG (cat4-remote-ssrc-hurricane)
     */
    private val remoteSourceIDs = Vector<Long>(1, 1)

    /**
     * Gets the `RTPConnector` through which this instance sends and receives RTP and RTCP traffic.
     *
     * @return the `RTPConnector` through which this instance sends and receives RTP and RTCP traffic
     */
    /**
     * The `RTPConnector` through which this instance sends and receives
     * RTP and RTCP traffic. The instance is a `TransformConnector` in
     * order to also enable packet transformations.
     */
    var rtpConnector: AbstractRTPConnector? = null
        private set

    /**
     * The one and only `MediaStreamTarget` this instance has added as a target in [.rtpConnector].
     */
    private var rtpConnectorTarget: MediaStreamTarget? = null

    /**
     * Gets the `RTPManager` instance which sends and receives RTP and RTCP traffic on
     * behalf of this `MediaStream`. If the `RTPManager` does not exist yet, it is not created.
     *
     * @return the `RTPManager` instance which sends and receives RTP and RTCP traffic on
     * behalf of this `MediaStream`
     */
    /**
     * The indicator which determines whether [.createSendStreams] has been executed for
     * [.rtpManager]. If `true`, the `SendStream`s have to be recreated when
     * the `MediaDevice`, respectively the `MediaDeviceSession`, of this instance is changed.
     */
    protected var sendStreamsAreCreated = false

    /**
     * Gets the `SrtpControl` which controls the SRTP of this stream.
     *
     * @return the `SrtpControl` which controls the SRTP of this stream
     */
    /**
     * The `SrtpControl` which controls the SRTP functionality of this `MediaStream`.
     */
    final override var srtpControl: SrtpControl

    /**
     * The `SSRCFactory` to be utilized by this instance to generate new synchronization
     * source (SSRC) identifiers. If `null`, this instance will employ internal logic to
     * generate new synchronization source (SSRC) identifiers.
     */
    private var ssrcFactory = SSRCFactoryImpl(getLocalSourceID())

    /**
     * The `MediaDirection` in which this instance is started. For example,
     * [MediaDirection.SENDRECV] if this instances is both sending and receiving data (e.g.
     * RTP and RTCP) or [MediaDirection.SENDONLY] if this instance is only sending data.
     */
    private var startedDirection: MediaDirection? = null

    /**
     * Engine chain reading sent RTCP sender reports and stores/prints statistics.
     */
    var statisticsEngine: StatisticsEngine? = null

    /**
     * The `TransformEngine` instance registered in the
     * `RTPConnector`'s transformer chain, which allows the "external" transformer to be swapped.
     */
    private val externalTransformerWrapper = TransformEngineWrapper<TransformEngine>()

    /**
     * The transformer which replaces the timestamp in an abs-send-time RTP header extension.
     */
    private var absSendTimeEngine: AbsSendTimeEngine? = null

    /**
     * The transformer which caches outgoing RTP packets for this [MediaStream].
     */
    open var cachingTransformer = createCachingTransformer()

    /**
     * The engine which adds an Original Header Block header extension to incoming packets.
     */
    private val ohbEngine = OriginalHeaderBlockTransformEngine()

    /**
     * The [DiagnosticContext] that this instance provides.
     */
    private val diagnosticContext = DiagnosticContext()

    /**
     * The ID of the frame markings RTP header extension. We use this field as
     * a cache, in order to not access [.activeRTPExtensions] every time.
     */
    private var frameMarkingsExtensionId = -1

    /**
     * The [TransportCCEngine] instance, if any, for this
     * [MediaStream]. The instance could be shared between more than one
     * [MediaStream], if they all use the same transport.
     */
    var mTransportCCEngine: TransportCCEngine? = null

    /*
      * Setting a new device resets any previously-set direction. Otherwise, we risk not
      * being able to set a new device if it is mandatory for the new device to fully cover
      * any previously-set direction.
      */
    // Require AbstractMediaDevice for MediaDeviceSession support.

    // cmeng: 20210403, new camera switching restricts to camera device preview handling only, does not
    // trigger codec close/open, so remove device change check. See PreviewStream#switchCamera()
    // if ((deviceSession == null) || (deviceSession.getDevice() != device)) {

    /**
     * Sets the `MediaDevice` that this stream should use to play back and capture media.
     *
     * **Note**: Also resets any previous direction set with
     * [.setDirection] to the direction of the specified `MediaDevice`.
     *
     * device the `MediaDevice` that this stream should use to play back and capture media
     * @see MediaStream.device
     */
    final override var device: MediaDevice?
        get() {
            return getDeviceSession()?.device
        }
        set(device) {
            if (device == null) throw NullPointerException("device")

            // Require AbstractMediaDevice for MediaDeviceSession support.
            val abstractMediaDevice = device as AbstractMediaDevice

            // cmeng: 20210403, new camera switching restricts to camera device preview handling only, does not
            // trigger codec close/open, so remove device change check. See PreviewStream#switchCamera()
            // if ((deviceSession == null) || (deviceSession.getDevice() != device)) {
            if (getDeviceSession() == null) {
                assertDirection(mDirection, device.direction, "device")

                val oldValue = getDeviceSession()
                val format: MediaFormat?
                val startedDirection: MediaDirection

                if (getDeviceSession() != null) {
                    format = this.format
                    startedDirection = getDeviceSession()!!.startedDirection
                    getDeviceSession()!!.removePropertyChangeListener(deviceSessionPropertyChangeListener)

                    // keep player active
                    getDeviceSession()!!.setDisposePlayerOnClose(getDeviceSession() !is VideoMediaDeviceSession)
                    getDeviceSession()!!.close(MediaDirection.SENDONLY)
                    mDeviceSession = null
                }
                else {
                    format = null
                    startedDirection = MediaDirection.INACTIVE
                }
                mDeviceSession = abstractMediaDevice.createSession()

                /*
                 * Copy the playback from the old MediaDeviceSession into the new MediaDeviceSession in
                 * order to prevent the recreation of the playback of the ReceiveStream(s) when just
                 * changing the MediaDevice of this MediaSteam.
                 */
                if (oldValue != null) getDeviceSession()!!.copyPlayback(oldValue)
                getDeviceSession()!!.addPropertyChangeListener(deviceSessionPropertyChangeListener)

                /*
                 * Setting a new device resets any previously-set direction. Otherwise, we risk not
                 * being able to set a new device if it is mandatory for the new device to fully cover
                 * any previously-set direction.
                 */
                mDirection = null
                if (getDeviceSession() != null) {
                    if (format != null) getDeviceSession()!!.setFormat(format)
                    getDeviceSession()!!.isMute = mute
                }
                deviceSessionChanged(oldValue, getDeviceSession())
                if (getDeviceSession() != null) {
                    getDeviceSession()!!.start(startedDirection)

                    // Add the receiveStreams of this instance to the new
                    // deviceSession.
                    val receiveStreamsReadLock = receiveStreamsLock.readLock()
                    receiveStreamsReadLock.lock()
                    try {
                        for (receiveStream in receiveStreams) getDeviceSession()!!.addReceiveStream(receiveStream!!)
                    } finally {
                        receiveStreamsReadLock.unlock()
                    }
                }
            }
        }

    /**
     * Initializes a new `MediaStreamImpl` instance which will use the specified
     * `MediaDevice` for both capture and playback of media. The new instance will not have
     * an associated `StreamConnector` and it must be set later for the new instance to be
     * able to exchange media with a remote peer.
     *
     * device the `MediaDevice` the new instance is to use for both capture and playback of media
     * srtpControl an existing control instance to control the SRTP operations
     */
    constructor(device: MediaDevice?, srtpControl: SrtpControl?) : this(null, device, srtpControl)

    /**
     * Initializes a new `MediaStreamImpl` instance which will use the specified `MediaDevice` for
     * both capture and playback of media exchanged via the specified `StreamConnector`.
     *
     * connector the `StreamConnector` the new instance is to use for sending and receiving
     * media or `null` if the `StreamConnector` of the new instance is to not
     * be set at initialization time but specified later on
     * device the `MediaDevice` the new instance is to use for both capture and playback of
     * media exchanged via the specified `StreamConnector`
     * srtpControl an existing control instance to control the ZRTP operations or `null` if a new
     * control instance is to be created by the new `MediaStreamImpl`
     */
    init {
        if (device != null) {
            /*
             * XXX Set the device early in order to make sure that it is of the right type because
             * we do not support just about any MediaDevice yet.
             */
            this.device = device
        }

        // TODO Add option to disable ZRTP, e.g. by implementing a NullControl.
        // If you change the default behavior (initiates a ZrtpControlImpl if the srtpControl
        // attribute is null), please accordingly modify the CallPeerMediaHandler.initStream function.
        this.srtpControl = srtpControl
                ?: NeomediaServiceUtils.mediaServiceImpl!!.createSrtpControl(SrtpControlType.ZRTP, null)

        this.srtpControl.registerUser(this)

        // cmeng: 2016/11/02 MediaStreamStats2Impl(this) must be executed before
        // setConnector(connector). This is to ensure mediaStreamStats is initialized before
        // StaticEngine makes reference to it.
        mediaStreamStatsImpl = MediaStreamStats2Impl(this)
        if (connector != null) setConnector(connector)
        Timber.log(TimberLog.FINER, "Created %S with hashCode %S", javaClass.simpleName, hashCode())
        diagnosticContext["stream"] = hashCode()
    }

    /**
     * Gets the [DiagnosticContext] of this instance.
     */
    fun getDiagnosticContext(): DiagnosticContext {
        return diagnosticContext
    }

    /**
     * Adds a new association in this `MediaStream` of the specified RTP payload type with
     * the specified `MediaFormat` in order to allow it to report `rtpPayloadType` in
     * RTP flows sending and receiving media in `format`. Usually, `rtpPayloadType`
     * will be in the range of dynamic RTP payload types.
     *
     * rtpPayloadType the RTP payload type to be associated in this
     * `MediaStream` with the specified `MediaFormat`
     * format the `MediaFormat` to be associated in this `MediaStream` with `rtpPayloadType`
     * @see MediaStream.addDynamicRTPPayloadType
     */
    override fun addDynamicRTPPayloadType(rtpPayloadType: Byte, format: MediaFormat) {
        val mediaFormatImpl = format as MediaFormatImpl<out Format?>

        synchronized(dynamicRTPPayloadTypes) {
            dynamicRTPPayloadTypes.put(rtpPayloadType, format)
        }

        when (format.encoding) {
            Constants.RED -> {
                val redTransformEngine = redTransformEngine
                if (redTransformEngine != null) {
                    redTransformEngine.setIncomingPT(rtpPayloadType)
                    // setting outgoingPT enables RED encapsulation for outgoing packets.
                    redTransformEngine.setOutgoingPT(rtpPayloadType)
                }
            }
            Constants.ULPFEC -> {
                val fecTransformEngineWrapper = fecTransformEngineWrapper
                if (fecTransformEngineWrapper?.wrapped != null) {
                    val fecTransformEngine = fecTransformEngineWrapper.wrapped
                    fecTransformEngine!!.setIncomingPT(rtpPayloadType)
                    // TODO ULPFEC without RED doesn't make sense.
                    fecTransformEngine.setOutgoingPT(rtpPayloadType)
                }
            }
            Constants.FLEXFEC_03 -> {
                val fecTransformEngineWrapper = fecTransformEngineWrapper
                if (fecTransformEngineWrapper!!.wrapped != null) {
                    Timber.i("Updating existing FlexFEC-03 transform engine with payload type %s", rtpPayloadType)
                    fecTransformEngineWrapper.wrapped!!.setIncomingPT(rtpPayloadType)
                    fecTransformEngineWrapper.wrapped!!.setOutgoingPT(rtpPayloadType)
                }
                else {
                    Timber.i("Creating FlexFEC-03 transform engine with payload type %s", rtpPayloadType)
                    val flexFecTransformEngine = FECTransformEngine(FECTransformEngine.FecType.FLEXFEC_03,
                        rtpPayloadType, rtpPayloadType, this)
                    setFecTransformEngine(flexFecTransformEngine)
                }
            }
        }
        if (rtpManager != null) {
            // We do not add RED and FEC payload types to the RTP Manager because RED and FEC
            // packets will be handled before they get to the RTP Manager.
            rtpManager!!.addFormat(mediaFormatImpl.format, rtpPayloadType.toInt())
        }
        onDynamicPayloadTypesChanged()
    }

    /**
     * {@inheritDoc}
     */
    override fun clearDynamicRTPPayloadTypes() {
        synchronized(dynamicRTPPayloadTypes) {
            dynamicRTPPayloadTypes.clear()
        }

        val redTransformEngine = redTransformEngine
        if (redTransformEngine != null) {
            redTransformEngine.setIncomingPT(m1Byte)
            redTransformEngine.setOutgoingPT(m1Byte)
        }

        if (fecTransformEngineWrapper?.wrapped != null) {
            val fecTransformEngine = fecTransformEngineWrapper!!.wrapped!!
            fecTransformEngine.setIncomingPT(m1Byte)
            fecTransformEngine.setOutgoingPT(m1Byte)
        }
        onDynamicPayloadTypesChanged()
    }

    /**
     * Add an RTP payload mapping that will overriding one we've set with
     * [.addDynamicRTPPayloadType]. This is necessary so that we can
     * support the RFC3264 case where the answerer has the right to declare what payload type
     * mappings it wants to receive RTP packets with even if they are different from those in the
     * offer. RFC3264 claims this is for support of legacy protocols such as H.323 but we've been
     * bumping with a number of cases where multi-component pure SIP systems also need to behave this way.
     *
     * originalPt the payload type that we are overriding
     * overloadPt the payload type that we are overriding it with
     */
    override fun addDynamicRTPPayloadTypeOverride(originalPt: Byte, overloadPt: Byte) {
        if (ptTransformEngine != null) ptTransformEngine!!.addPTMappingOverride(originalPt, overloadPt)
    }

    /**
     * Adds a specific `ReceiveStream` to [.receiveStreams].
     *
     * receiveStream the `ReceiveStream` to add
     * @return `true` if `receiveStreams` changed as a result of the method call; otherwise, `false`
     */
    private fun addReceiveStream(receiveStream: ReceiveStream): Boolean {
        val writeLock = receiveStreamsLock.writeLock()
        val readLock = receiveStreamsLock.readLock()
        var added = false

        writeLock.lock()
        try {
            // Downgrade the writeLock to a read lock in order to allow readers during the invocation of
            // MediaDeviceSession.addReceiveStream(ReceiveStream) (and disallow writers, of course).
            if (!receiveStreams.contains(receiveStream) && receiveStreams.add(receiveStream)) {
                readLock.lock()
                added = true
            }
        } finally {
            writeLock.unlock()
        }

        if (added) {
            try {
                val deviceSession = getDeviceSession()
                if (deviceSession == null || deviceSession.useTranslator) {
                    // Since there is no output MediaDevice to render the receiveStream on, the
                    // JitterBuffer of the receiveStream will needlessly buffer and, possibly,
                    // eventually try to adapt to the lack of free buffer space.
                    ReceiveStreamPushBufferDataSource.setNullTransferHandler(receiveStream)
                }
                else {
                    deviceSession.addReceiveStream(receiveStream)
                }
            } finally {
                readLock.unlock()
            }
        }
        return added
    }

    /**
     * Sets the remote SSRC identifier and fires the corresponding `PropertyChangeEvent`.
     *
     * remoteSourceID the SSRC identifier that this stream will be using in outgoing RTP packets from now on.
     */
    protected open fun addRemoteSourceID(remoteSourceID: Long) {
        val oldValue = getRemoteSourceID()
        if (!remoteSourceIDs.contains(remoteSourceID)) remoteSourceIDs.add(remoteSourceID)
        firePropertyChange(MediaStream.PNAME_REMOTE_SSRC, oldValue, remoteSourceID)
    }

    /**
     * Maps or updates the mapping between `extensionID` and `rtpExtension`. If
     * `rtpExtension`'s `MediaDirection` attribute is set to `INACTIVE` the
     * mapping is removed from the local extensions table and the extension would not be
     * transmitted or handled by this stream's `RTPConnector`.
     *
     * extensionID the ID that is being mapped to `rtpExtension`
     * rtpExtension the `RTPExtension` that we are mapping.
     */
    override fun addRTPExtension(extensionID: Byte, rtpExtension: RTPExtension) {
        // if (rtpExtension == null) return
        val active = MediaDirection.INACTIVE != rtpExtension.direction
        synchronized(activeRTPExtensions) {
            when {
                active -> activeRTPExtensions.put(extensionID, rtpExtension)
                else -> activeRTPExtensions.remove(extensionID)
            }
        }
        enableRTPExtension(extensionID, rtpExtension)
    }

    /**
     * {@inheritDoc}
     */
    override fun clearRTPExtensions() {
        synchronized(activeRTPExtensions) {
            activeRTPExtensions.clear()
            frameMarkingsExtensionId = -1
            if (mTransportCCEngine != null) {
                mTransportCCEngine!!.setExtensionID(-1)
            }
            if (ohbEngine != null) {
                ohbEngine.setExtensionID(-1)
            }
            val remoteBitrateEstimatorWrapper = remoteBitrateEstimator
            if (remoteBitrateEstimatorWrapper != null) {
                remoteBitrateEstimatorWrapper.setAstExtensionID(-1)
                remoteBitrateEstimatorWrapper.setTccExtensionID(-1)
            }
            if (absSendTimeEngine != null) {
                absSendTimeEngine!!.setExtensionID(-1)
            }
        }
    }

    /**
     * Enables all RTP extensions configured for this [MediaStream].
     */
    private fun enableRTPExtensions() {
        synchronized(activeRTPExtensions) {
            for ((key, value) in activeRTPExtensions) {
                enableRTPExtension(key, value)
            }
        }
    }

    /**
     * Enables the use of a specific RTP extension.
     *
     * extensionID the ID.
     * rtpExtension the extension.
     */
    private fun enableRTPExtension(extensionID: Byte, rtpExtension: RTPExtension) {
        val active = MediaDirection.INACTIVE != rtpExtension.direction
        val effectiveId = if (active) RTPUtils.as16Bits(extensionID.toInt()) else -1
        when (rtpExtension.uri.toString()) {
            RTPExtension.ABS_SEND_TIME_URN -> {
                if (absSendTimeEngine != null) {
                    absSendTimeEngine!!.setExtensionID(effectiveId)
                }
                remoteBitrateEstimator?.setAstExtensionID(effectiveId)
            }
            RTPExtension.FRAME_MARKING_URN -> frameMarkingsExtensionId = effectiveId
            RTPExtension.ORIGINAL_HEADER_BLOCK_URN -> ohbEngine.setExtensionID(effectiveId)
            RTPExtension.TRANSPORT_CC_URN -> {
                mTransportCCEngine!!.setExtensionID(effectiveId)
                remoteBitrateEstimator?.setTccExtensionID(effectiveId)
            }
        }
    }

    /**
     * Releases the resources allocated by this instance in the course of its execution and
     * prepares it to be garbage collected.
     *
     * @see MediaStream.close
     */
    override fun close() {
        /*
         * Some statistics cannot be taken from the RTP manager and have to be gathered from the
         * ReceiveStream. We need to do this before calling stop().
         */
        printReceiveStreamStatistics()

        stop()
        closeSendStreams()
        srtpControl.cleanup(this)

        if (csrcEngine != null) {
            csrcEngine = null
        }

        if (cachingTransformer != null) {
            cachingTransformer!!.close()
            cachingTransformer = null
        }

        retransmissionRequester?.close()

        if (transformEngineChain != null) {
            var t = transformEngineChain!!.rtpTransformer
            t?.close()
            t = transformEngineChain!!.rtcpTransformer
            t?.close()
            transformEngineChain = null
        }
        mTransportCCEngine?.removeMediaStream(this)

        if (rtpManager != null) {
            printFlowStatistics(rtpManager!!)

            rtpManager!!.removeReceiveStreamListener(this)
            rtpManager!!.removeSendStreamListener(this)
            rtpManager!!.removeSessionListener(this)
            rtpManager!!.removeRemoteListener(this)
            try {
                rtpManager!!.dispose()
                rtpManager = null
            } catch (t: Throwable) {
                if (t is ThreadDeath) throw t

                /*
                 * Analysis of heap dumps and application logs suggests that RTPManager#dispose()
                 * may throw an exception after a NullPointerException has been thrown by
                 * SendStream#close() as documented in #stopSendStreams(Iterable<SendStream>,
                 * boolean). It is unknown at the time of this writing whether we can do
                 * anything to prevent the exception here but it is clear that, if we let it go
                 * through, we will not release at least one capture device (i.e. we will at
                 * least skip the MediaDeviceSession#close() bellow). For example, if the
                 * exception is thrown for the audio stream in a call, its capture device will
                 * not be released and any video stream will not get its #close() method called at all.
                 */
                Timber.e(t, "Failed to dispose of RTPManager")
            }
        }

        /*
         * XXX Call AbstractRTPConnector#removeTargets() after StreamRTPManager#dispose().
         * Otherwise, the latter will try to send an RTCP BYE and there will be no targets to send it to.
         */
        rtpConnector?.removeTargets()
        rtpConnectorTarget = null

        mDeviceSession?.close(MediaDirection.SENDRECV)
    }

    /**
     * Closes the `SendStream`s this instance is sending to its remote peer.
     */
    private fun closeSendStreams() {
        stopSendStreams(true)
    }

    /**
     * Performs any optional configuration on a specific `RTPConnectorInputStream` of an
     * `RTPManager` to be used by this `MediaStreamImpl`. Allows extenders to override.
     *
     * dataInputStream the `RTPConnectorInputStream` to be used by an `RTPManager` of this
     * `MediaStreamImpl` and to be configured
     */
    protected fun configureDataInputStream(dataInputStream: RTPConnectorInputStream<*>) {
        dataInputStream.setPriority(priority)
    }

    /**
     * Performs any optional configuration on a specific `RTPConnectorOutputStream` of an
     * `RTPManager` to be used by this `MediaStreamImpl`. Allows extenders to override.
     *
     * dataOutputStream the `RTPConnectorOutputStream` to be used by an `RTPManager` of this
     * `MediaStreamImpl` and to be configured
     */
    protected open fun configureDataOutputStream(dataOutputStream: RTPConnectorOutputStream) {
        dataOutputStream.setPriority(priority)
    }

    /**
     * Performs any optional configuration on the `BufferControl` of the specified
     * `RTPManager` which is to be used as the `RTPManager` of this
     * `MediaStreamImpl`. Allows extenders to override.
     *
     * rtpManager the `RTPManager` which is to be used by this `MediaStreamImpl`
     * bufferControl the `BufferControl` of `rtpManager` on which any optional
     * configuration is to be performed
     */
    protected open fun configureRTPManagerBufferControl(rtpManager: StreamRTPManager?, bufferControl: BufferControl?) {}

    /**
     * A stub that allows audio oriented streams to create and keep a reference to a `DtmfTransformEngine`.
     *
     * @return a `DtmfTransformEngine` if this is an audio oriented stream and `null` otherwise.
     */
    protected open fun createDtmfTransformEngine(): DtmfTransformEngine? {
        return null
    }

    /**
     * Creates new `SendStream` instances for the streams of [.deviceSession] through
     * [.rtpManager].
     */
    private fun createSendStreams() {
        val rtpManager = this.rtpManager
        val dataSource = getDeviceSession()?.outputDataSource

        val streamCount = when (dataSource) {
            is PushBufferDataSource -> {
                val streams = dataSource.streams
                streams.size ?: 0
            }

            is PushDataSource -> {
                val streams = dataSource.streams
                streams.size ?: 0
            }

            is PullBufferDataSource -> {
                val streams = dataSource.streams
                streams.size ?: 0
            }

            is PullDataSource -> {
                val streams = dataSource.streams
                streams.size ?: 0
            }
            null -> 0
            else -> 1
        }

        /*
         * XXX We came up with a scenario in our testing in which G.722 would work fine for the
         * first call since the start of the application and then it would fail for subsequent
         * calls, JMF would complain that the G.722 RTP format is unknown to the RTPManager. Since
         * RTPManager#createSendStream(DataSource, int) is one of the cases in which the formats
         * registered with the RTPManager are necessary, register them (again) just before we use them.
         */
        registerCustomCodecFormats(rtpManager!!)
        for (streamIndex in 0 until streamCount) {
            try {
                val sendStream = rtpManager.createSendStream(dataSource, streamIndex)
                Timber.log(TimberLog.FINER,
                    "Created SendStream with hashCode %s for %s and streamIndex %s in RTPManager with hashCode %s",
                    sendStream.hashCode(), toString(dataSource!!), streamIndex, rtpManager.hashCode())

                /*
                 * JMF stores the synchronization source (SSRC) identifier as a 32-bit signed
                 * integer, we store it as unsigned.
                 */
                val localSSRC = sendStream.ssrc and 0xFFFFFFFFL
                if (mLocalSourceID != localSSRC) setLocalSourceID(localSSRC)
            } catch (ioe: IOException) {
                Timber.e(ioe, "Failed to create send stream for data source %s  and stream index %s;\n%s",
                    dataSource, streamIndex, ioe.message)
            } catch (ioe: NullPointerException) {
                Timber.e(ioe, "Failed to create send stream for data source %s  and stream index %s;\n%s",
                    dataSource, streamIndex, ioe.message)
            } catch (ufe: UnsupportedFormatException) {
                Timber.e(ufe,
                    "Failed to create send stream for data source %s and stream index %s because of failed format %s;;\n%s",
                    dataSource, streamIndex, ufe.failedFormat, ufe.message)
            }
        }

        sendStreamsAreCreated = true
        if (TimberLog.isTraceEnable) {
            val sendStreams = rtpManager.sendStreams
            val sendStreamCount = sendStreams.size ?: 0
            Timber.log(TimberLog.FINER, "Total number of SendStreams in RTPManager with hashCode %s is %s",
                rtpManager.hashCode(), sendStreamCount)
        }
    }

    protected open fun createSsrcTransformEngine(): SsrcTransformEngine? {
        return null
    }

    /**
     * Creates the [AbsSendTimeEngine] for this `MediaStream`.
     *
     * @return the created [AbsSendTimeEngine].
     */
    private fun createAbsSendTimeEngine(): AbsSendTimeEngine {
        return AbsSendTimeEngine()
    }

    /**
     * Creates the [CachingTransformer] for this `MediaStream`.
     *
     * @return the created [CachingTransformer].
     */
    protected open fun createCachingTransformer(): CachingTransformer? {
        return null
    }

    /**
     * Creates the [RetransmissionRequester] for this `MediaStream`.
     *
     * @return the created [RetransmissionRequester].
     */
    protected open fun createRetransmissionRequester(): RetransmissionRequesterImpl? {
        return null
    }

    /**
     * Creates a chain of transform engines for use with this stream. Note that this is the only
     * place where the `TransformEngineChain` is and should be manipulated to avoid
     * problems with the order of the transformers.
     *
     * @return the `TransformEngineChain` that this stream should be using.
     */
    private fun createTransformEngineChain(): TransformEngineChain {
        val engineChain = ArrayList<TransformEngine>(9)

        // CSRCs and CSRC audio levels
        if (csrcEngine == null) {
            csrcEngine = CsrcTransformEngine(this)
        }
        engineChain.add(csrcEngine!!)

        // DTMF
        val dtmfEngine = createDtmfTransformEngine()
        if (dtmfEngine != null) {
            engineChain.add(dtmfEngine)
        }
        engineChain.add(externalTransformerWrapper)

        // RRs and REMBs.
        val rtcpFeedbackTermination = rtcpTermination
        if (rtcpFeedbackTermination != null) {
            engineChain.add(rtcpFeedbackTermination)
        }

        // here comes the override payload type transformer
        // as it changes headers of packets, need to go before encryption
        if (ptTransformEngine == null) {
            ptTransformEngine = PayloadTypeTransformEngine()
        }
        engineChain.add(ptTransformEngine!!)

        // FEC
        if (fecTransformEngineWrapper != null) {
            engineChain.add(fecTransformEngineWrapper!!)
        }
        // RED
        val redTransformEngine = redTransformEngine
        if (redTransformEngine != null) {
            engineChain.add(redTransformEngine)
        }

        // RTCP Statistics
        if (statisticsEngine == null) {
            statisticsEngine = StatisticsEngine(this)
        }
        engineChain.add(statisticsEngine!!)
        if (retransmissionRequester != null) {
            engineChain.add(retransmissionRequester!!)
        }

        if (cachingTransformer != null) {
            engineChain.add(cachingTransformer!!)
        }

        // Discard
        val discardEngine = createDiscardEngine()
        if (discardEngine != null) engineChain.add(discardEngine)
        val mediaStreamTrackReceiver = mediaStreamTrackReceiver
        if (mediaStreamTrackReceiver != null) {
            engineChain.add(mediaStreamTrackReceiver)
        }

        // Padding termination.
        val paddingTermination = paddingTermination
        if (paddingTermination != null) {
            engineChain.add(paddingTermination)
        }

        // RTX
        val rtxTransformer = rtxTransformer
        if (rtxTransformer != null) {
            engineChain.add(rtxTransformer)
        }

        // TODO RTCP termination should end up here.
        val remoteBitrateEstimator = remoteBitrateEstimator
        if (remoteBitrateEstimator != null) {
            engineChain.add(remoteBitrateEstimator)
        }
        absSendTimeEngine = createAbsSendTimeEngine()
        if (absSendTimeEngine != null) {
            engineChain.add(absSendTimeEngine!!)
        }
        if (mTransportCCEngine != null) {
            engineChain.add(mTransportCCEngine!!.getEgressEngine())
        }

        // OHB
        engineChain.add(ohbEngine)

        // SRTP
        val srtpTransformEngine = srtpControl.transformEngine
        if (srtpTransformEngine != null) {
            engineChain.add(srtpControl.transformEngine!!)
        }
        if (mTransportCCEngine != null) {
            engineChain.add(mTransportCCEngine!!.ingressEngine)
        }

        // SSRC audio levels
        /*
         * It needs to go first in the reverse transform in order to be able to prevent RTP packets
         * from a muted audio source from being decrypted.
         */
        val ssrcEngine = createSsrcTransformEngine()
        if (ssrcEngine != null) {
            engineChain.add(ssrcEngine)
        }

        // RTP extensions may be implemented in some of the engines just created (e.g.
        // created (e.g. abs-send-time). So take into account their configuration.
        enableRTPExtensions()
        return TransformEngineChain(engineChain.toTypedArray())
    }

    /**
     * Notifies this `MediaStream` that the `MediaDevice` (and respectively the
     * `MediaDeviceSession` with it) which this instance uses for capture and playback of
     * media has been changed. Allows extenders to override and provide additional processing of
     * `oldValue` and `newValue`.
     *
     * oldValue the `MediaDeviceSession` with the `MediaDevice` this instance used work with
     * newValue the `MediaDeviceSession` with the `MediaDevice` this instance is to work with
     */
    protected open fun deviceSessionChanged(oldValue: MediaDeviceSession?, newValue: MediaDeviceSession?) {
        recreateSendStreams()
    }

    /**
     * Notifies this instance that the output `DataSource` of its
     * `MediaDeviceSession` has changed. Recreates the `SendStream`s of this
     * instance as necessary so that it, for example, continues streaming after the change if it
     * was streaming before the change.
     */
    private fun deviceSessionOutputDataSourceChanged() {
        recreateSendStreams()
    }

    /**
     * Recalculates the list of CSRC identifiers that this `MediaStream` needs to include in
     * RTP packets bound to its interlocutor. The method uses the list of SSRC identifiers
     * currently handled by our device (possibly a mixer), then removes the SSRC ID of this
     * stream's interlocutor. If this turns out to be the only SSRC currently in the list we set
     * the list of local CSRC identifiers to null since this is obviously a non-conf call and we
     * don't need to be advertising CSRC lists. If that's not the case, we also add our own SSRC
     * to the list of IDs and cache the entire list.
     *
     * ev the `PropertyChangeEvent` containing the list of SSRC identifiers handled by our
     * device session before and after it changed.
     */
    private fun deviceSessionSsrcListChanged(ev: PropertyChangeEvent) {
        val ssrcArray = ev.newValue as LongArray?

        // the list is empty
        if (ssrcArray == null) {
            localContributingSourceIDs = null
            return
        }
        var elementsToRemove = 0
        val remoteSourceIDs = this.remoteSourceIDs

        // in case of a conf call the mixer would return all SSRC IDs that are currently
        // contributing including this stream's counterpart. We need to remove that last one
        // since that's where we will be sending our csrc list
        for (csrc in ssrcArray) {
            if (remoteSourceIDs.contains(csrc))
                elementsToRemove++
        }

        // we don't seem to be in a conf call since the list only contains the SSRC id of the
        // party that we are directly interacting with.
        if (elementsToRemove >= ssrcArray.size) {
            localContributingSourceIDs = null
            return
        }

        // prepare the new array. make it big enough to also add the local SSRC id but do not
        // make it bigger than 15 since that's the maximum for RTP.
        val cc = min(ssrcArray.size - elementsToRemove + 1, 15)
        val csrcArray = LongArray(cc)
        var i = 0
        var j = 0
        while (i < ssrcArray.size && j < csrcArray.size - 1) {
            val ssrc = ssrcArray[i]
            if (!remoteSourceIDs.contains(ssrc)) {
                csrcArray[j] = ssrc
                j++
            }
            i++
        }
        csrcArray[csrcArray.size - 1] = mLocalSourceID
        localContributingSourceIDs = csrcArray
    }

    /**
     * Sets the target of this `MediaStream` to which it is to send and from which it is to
     * receive data (e.g. RTP) and control data (e.g. RTCP). In contrast to
     * [.setTarget], sets the specified `target` on this
     * `MediaStreamImpl` even if its current `target` is equal to the specified one.
     *
     * target the `MediaStreamTarget` describing the data (e.g. RTP) and the control data
     * (e.g. RTCP) locations to which this `MediaStream` is to send and from which it is to receive
     * @see MediaStreamImpl.target
     */
    private fun doSetTarget(target: MediaStreamTarget?) {
        val newDataAddr: InetSocketAddress?
        val newControlAddr: InetSocketAddress?
        val connector = rtpConnector
        if (target == null) {
            newDataAddr = null
            newControlAddr = null
        }
        else {
            newDataAddr = target.dataAddress
            newControlAddr = target.controlAddress
        }

        /*
         * Invoke AbstractRTPConnector#removeTargets() if the new value does actually remove an
         * RTP or RTCP target in comparison to the old value. If the new value is equal to the
         * oldValue or adds an RTP or RTCP target (i.e. the old value does not specify the
         * respective RTP or RTCP target and the new value does), then removeTargets is
         * unnecessary and would've needlessly allowed a (tiny) interval of (execution) time
         * (between removeTargets and addTarget) without a target.
         */
        if (rtpConnectorTarget != null && connector != null) {
            val oldDataAddr = rtpConnectorTarget!!.dataAddress
            var removeTargets = oldDataAddr != newDataAddr
            if (!removeTargets) {
                val oldControlAddr = rtpConnectorTarget!!.controlAddress
                removeTargets = oldControlAddr != newControlAddr
            }
            if (removeTargets) {
                connector.removeTargets()
                rtpConnectorTarget = null
            }
        }
        var targetIsSet: Boolean
        if (target == null || newDataAddr == null || connector == null) {
            targetIsSet = true
        }
        else {
            try {
                val controlInetAddr: InetAddress?
                val controlPort: Int
                if (newControlAddr == null) {
                    controlInetAddr = null
                    controlPort = 0
                }
                else {
                    controlInetAddr = newControlAddr.address
                    controlPort = newControlAddr.port
                }
                connector.addTarget(SessionAddress(
                    newDataAddr.address, newDataAddr.port, controlInetAddr, controlPort))
                targetIsSet = true
            } catch (ioe: IOException) {
                targetIsSet = false
                Timber.e(ioe, "Failed to set target %s", target)
            }
        }
        if (targetIsSet) {
            rtpConnectorTarget = target
            Timber.log(TimberLog.FINER, "Set target of %s with hashCode %s to %s",
                javaClass.simpleName, hashCode(), target)
        }
    }

    /**
     * Returns the ID currently assigned to a specific RTP extension.
     *
     * rtpExtension the RTP extension to get the currently assigned ID of
     * @return the ID currently assigned to the specified RTP extension or `-1` if no ID has
     * been defined for this extension so far
     */
    fun getActiveRTPExtensionID(rtpExtension: RTPExtension?): Byte {
        synchronized(activeRTPExtensions) {
            for ((key, value) in activeRTPExtensions) {
                if (value == rtpExtension) return key
            }
        }
        return -1
    }

    /**
     * Returns a map containing all currently active `RTPExtension`s in use by this stream.
     *
     * @return a map containing all currently active `RTPExtension`s in use by this stream.
     */
    override fun getActiveRTPExtensions(): Map<Byte, RTPExtension> {
        synchronized(activeRTPExtensions) {
            return HashMap(activeRTPExtensions)
        }
    }

    /**
     * Gets the `MediaDevice` that this stream uses to play back and capture media.
     *
     * @return the `MediaDevice` that this stream uses to play back and capture media
     * @see MediaStream.device
     */
    // Add the receiveStreams of this instance to the new deviceSession. keep player active

    /*
      * Copy the playback from the old MediaDeviceSession into the new MediaDeviceSession in
      * order to prevent the recreation of the playback of the ReceiveStream(s) when just
      * changing the MediaDevice of this MediaSteam.
      */

    /**
     * Gets the `MediaDirection` of the `device` of this instance. In case there
     * is no device, [MediaDirection.SENDRECV] is assumed.
     */
    private fun getDeviceDirection(): MediaDirection {
        return getDeviceSession()?.device?.direction ?: MediaDirection.SENDRECV
    }

    // Make sure that RTP is filtered in accord with the direction of this
    // MediaStream, so that we don't have to worry about, for example, new
    // ReceiveStreams being created while in sendonly/inactive.

    /*
     * Make sure that the specified direction is in accord with the direction of the
     * MediaDevice of this instance.
     */
    override var direction: MediaDirection
        /**
         * Gets the direction in which this `MediaStream` is allowed to stream media.
         *
         * @return the `MediaDirection` in which this `MediaStream` is allowed to stream media
         * @see MediaStream.direction
         */
        // Don't know what it may be (in the future) so ignore it.
        get() {
            return if (mDirection == null) getDeviceDirection() else mDirection!!
        }
        /**
         * Sets the direction in which media in this `MediaStream` is to be streamed. If this
         * `MediaStream` is not currently started, calls to [.start] later on will start
         * it only in the specified `direction`. If it is currently started in a direction
         * different than the specified, directions other than the specified will be stopped.
         *
         * direction the `MediaDirection` in which this `MediaStream` is to stream media when it is started
         * @see MediaStream.direction
         */
        set(dir) {
            // if (dir == null) throw NullPointerException("direction")
            if (mDirection == dir) return
            Timber.log(TimberLog.FINER, "Changing direction of stream %s from: %s to: %s",
                hashCode(), mDirection, dir)

            /*
             * Make sure that the specified direction is in accord with the direction of the
             * MediaDevice of this instance.
             */
            assertDirection(dir, getDeviceDirection(), "direction")
            mDirection = dir
            when (mDirection) {
                MediaDirection.INACTIVE -> {
                    stop(MediaDirection.SENDRECV)
                    return
                }
                MediaDirection.RECVONLY -> stop(MediaDirection.SENDONLY)
                MediaDirection.SENDONLY -> stop(MediaDirection.RECVONLY)
                MediaDirection.SENDRECV -> {}
                // Don't know what it may be (in the future) so ignore it.
                else -> return
            }

            if (isStarted) start(mDirection)

            // Make sure that RTP is filtered in accord with the direction of this
            // MediaStream, so that we don't have to worry about, for example, new
            // ReceiveStreams being created while in sendonly/inactive.
            val connector = rtpConnector
            connector?.setDirection(dir)
        }

    /**
     * Returns the payload type number that has been negotiated for the specified `encoding`
     * or `-1` if no payload type has been negotiated for it. If multiple formats match the
     * specified `encoding`, then this method would return the first one it encounters while
     * iterating through the map.
     *
     * encoding the encoding whose payload type we are trying to obtain.
     * @return the payload type number that has been negotiated for the specified `encoding`
     * or `-1` if no payload type has been negotiated for it.
     */
    override fun getDynamicRTPPayloadType(codec: String?): Byte {
        for ((key, value) in dynamicRTPPayloadTypes) if (value.encoding == codec) {
            return key
        }
        return -1
    }

    /**
     * Gets the existing associations in this `MediaStream` of RTP payload types to
     * `MediaFormat`s. The returned `Map` only contains associations previously added
     * in this instance with [.addDynamicRTPPayloadType] and not globally
     * or well-known associations reported by [MediaFormat.rtpPayloadType].
     *
     * @return a `Map` of RTP payload type expressed as `Byte` to
     * `MediaFormat` describing the existing (dynamic) associations in this instance of
     * RTP payload types to `MediaFormat`s. The `Map` represents a snapshot of the
     * existing associations at the time of the `getDynamicRTPPayloadTypes()` method call
     * and modifications to it are not reflected on the internal storage
     * @see MediaStream.getDynamicRTPPayloadTypes
     */
    override fun getDynamicRTPPayloadTypes(): MutableMap<Byte, MediaFormat> {
        synchronized(dynamicRTPPayloadTypes) {
            return HashMap(dynamicRTPPayloadTypes)
        }
    }

    /**
     * Sets the [FECTransformEngine] for this [MediaStream]
     * By default, nothing is done with the passed engine, allowing extenders to implement it
     */
    protected open var fecTransformEngineWrapper: TransformEngineWrapper<FECTransformEngine>? = null

    /**
     * Sets the [FECTransformEngine] for this [MediaStream]
     * By default, nothing is done with the passed engine, allowing extenders to implement it
     *
     * @param fecTransformEngine FEC Transform Engine
     */
    protected open fun setFecTransformEngine(fecTransformEngine: FECTransformEngine) {
        // no op
    }

    /**
     * Gets the `MediaFormat` that this stream is currently transmitting in.
     *
     * @return the `MediaFormat` that this stream is currently transmitting in
     * @see MediaStream.getFormat
     */
    override var format: MediaFormat?
        get() = getDeviceSession()?.getFormat()
        set(format) {
            var thisFormat: MediaFormatImpl<out Format?>? = null
            if (getDeviceSession() != null) {
                thisFormat = getDeviceSession()!!.getFormat()
                if (thisFormat != null
                        && thisFormat == format
                        && thisFormat.advancedAttributesAreEqual(thisFormat.advancedAttributes,
                            format.advancedAttributes)) {
                    return
                }
            }

            Timber.log(TimberLog.FINER, "Changing format of stream %s from: %S to: %S",
                hashCode(), thisFormat, format)
            handleAttributes(format!!, format.advancedAttributes)
            handleAttributes(format, format.formatParameters)
            getDeviceSession()?.setFormat(format)
            maybeUpdateDynamicRTPPayloadTypes(null)
        }

    /**
     * {@inheritDoc}
     */
    override fun getFormat(payloadType: Byte): MediaFormat? {
        synchronized(dynamicRTPPayloadTypes) {
            return dynamicRTPPayloadTypes[payloadType]
        }
    }

    /**
     * Gets the local address that this stream is sending RTCP traffic from.
     *
     * @return an `InetSocketAddress` instance indicating the local address that this stream
     * is sending RTCP traffic from.
     */
    val localControlAddress: InetSocketAddress?
        get() {
            val connector = if (rtpConnector != null) rtpConnector!!.connector else null
            if (connector != null) {
                if (connector.dataSocket != null) {
                    return connector.controlSocket!!.localSocketAddress as InetSocketAddress
                }
                else if (connector.dataTCPSocket != null) {
                    return connector.controlTCPSocket!!.localSocketAddress as InetSocketAddress
                }
            }
            return null
        }

    /**
     * Gets the local address that this stream is sending RTP traffic from.
     *
     * @return an `InetSocketAddress` instance indicating the local address that this stream
     * is sending RTP traffic from.
     */
    val localDataAddress: InetSocketAddress?
        get() {
            val connector = if (rtpConnector != null) rtpConnector!!.connector else null
            if (connector != null) {
                if (connector.dataSocket != null) {
                    return connector.dataSocket!!.localSocketAddress as InetSocketAddress
                }
                else if (connector.dataTCPSocket != null) {
                    return connector.dataTCPSocket!!.localSocketAddress as InetSocketAddress
                }
            }
            return null
        }

    /**
     * Gets the synchronization source (SSRC) identifier of the local peer or `-1` if it is not yet known.
     *
     * @return the synchronization source (SSRC) identifier of the local peer or `-1` if it is not yet known
     * @see MediaStream.getLocalSourceID
     */
    override fun getLocalSourceID(): Long {
        return mLocalSourceID
    }

    /**
     * Returns the statistical information gathered about this `MediaStream`.
     *
     * @return the statistical information gathered about this `MediaStream`
     */
    override val mediaStreamStats: MediaStreamStats2Impl
        get() = mediaStreamStatsImpl

    /**
     * Gets the `MediaType` of this `MediaStream`.
     *
     * @return the `MediaType` of this `MediaStream`
     */
    val mediaType: MediaType?
        get() {
            val format = format
            var mediaType: MediaType? = null
            if (format != null) mediaType = format.mediaType
            if (mediaType == null) {
                if (getDeviceSession() != null) mediaType = getDeviceSession()!!.device.mediaType
                if (mediaType == null) {
                    if (this is AudioMediaStream) mediaType = MediaType.AUDIO else if (this is VideoMediaStream) mediaType = MediaType.VIDEO
                }
            }
            return mediaType
        }

    /**
     * Used to set the priority of the receive/send streams. Underling implementations can override
     * this and return different than current default value.
     *
     * @return the priority for the current thread.
     */
    protected open val priority: Int
        get() = Thread.currentThread().priority

    /**
     * Gets a `ReceiveStream` which this instance plays back on its associated
     * `MediaDevice` and which has a specific synchronization source identifier (SSRC).
     *
     * ssrc the synchronization source identifier of the `ReceiveStream` to return
     * @return a `ReceiveStream` which this instance plays back on its associated
     * `MediaDevice` and which has the specified `ssrc`
     */
    fun getReceiveStream(ssrc: Int): ReceiveStream? {
        for (receiveStream in getReceiveStreams()) {
            val receiveStreamSSRC = receiveStream!!.ssrc.toInt()
            if (receiveStreamSSRC == ssrc) return receiveStream
        }
        return null
    }

    /**
     * Gets a list of the `ReceiveStream`s this instance plays back on its associated `MediaDevice`.
     *
     * @return a list of the `ReceiveStream`s this instance plays back on its associated
     * `MediaDevice`
     */
    fun getReceiveStreams(): Collection<ReceiveStream?> {
        val receiveStreams = HashSet<ReceiveStream?>()

        // This instance maintains a list of the ReceiveStreams.
        val readLock = receiveStreamsLock.readLock()
        readLock.lock()
        try {
            receiveStreams.addAll(this.receiveStreams)
        } finally {
            readLock.unlock()
        }

        /*
         * Unfortunately, it has been observed that sometimes there are valid ReceiveStreams in
         * this instance which are not returned by the rtpManager.
         */
        if (rtpManager != null) {
            val rtpManagerReceiveStreams = rtpManager!!.receiveStreams as Vector<ReceiveStream>?
            if (rtpManagerReceiveStreams != null) {
                receiveStreams.addAll(rtpManagerReceiveStreams)
            }
        }
        return receiveStreams
    }

    /**
     * Creates the `REDTransformEngine` for this `MediaStream`. By default none is
     * created, allows extenders to implement it.
     *
     * @return the `REDTransformEngine` created.
     */
    private val redTransformEngine: REDTransformEngine? = null

    /**
     * Returns the `List` of CSRC identifiers representing the parties contributing to the
     * stream that we are receiving from this `MediaStream`'s remote party.
     *
     * @return a `List` of CSRC identifiers representing the parties contributing to the
     * stream that we are receiving from this `MediaStream`'s remote party.
     */
    val remoteContributingSourceIDs: LongArray?
        get() = getDeviceSession()!!.remoteSSRCList

    /**
     * Gets the address that this stream is sending RTCP traffic to.
     *
     * @return an `InetSocketAddress` instance indicating the address that this stream is sending RTCP traffic to
     * @see MediaStream.remoteControlAddress
     */
    override val remoteControlAddress: InetSocketAddress?
        get() {
            if (rtpConnector != null) {
                val connector = rtpConnector!!.connector
                if (connector != null) {
                    if (connector.dataSocket != null) {
                        return connector.controlSocket!!.remoteSocketAddress as InetSocketAddress
                    }
                    else if (connector.dataTCPSocket != null) {
                        return connector.controlTCPSocket!!.remoteSocketAddress as InetSocketAddress
                    }
                }
            }
            return null
        }

    /**
     * Gets the address that this stream is sending RTP traffic to.
     *
     * @return an `InetSocketAddress` instance indicating the address that this stream is
     * sending RTP traffic to
     * @see MediaStream.remoteDataAddress
     */
    override val remoteDataAddress: InetSocketAddress?
        get() {
            val connector = if (rtpConnector != null) rtpConnector!!.connector else null
            if (connector != null) {
                if (connector.dataSocket != null) {
                    return connector.dataSocket!!.remoteSocketAddress as InetSocketAddress
                }
                else if (connector.dataTCPSocket != null) {
                    return connector.dataTCPSocket!!.remoteSocketAddress as InetSocketAddress
                }
            }
            return null
        }

    /**
     * {@inheritDoc}
     *
     * Returns the last element of [.getRemoteSourceIDs] which may or may not always be appropriate.
     *
     * @see MediaStream.getRemoteSourceID
     */
    override fun getRemoteSourceID(): Long {
        return if (remoteSourceIDs.isEmpty()) -1 else remoteSourceIDs.lastElement()
    }

    /**
     * Gets the synchronization source (SSRC) identifiers of the remote peer.
     *
     * @return the synchronization source (SSRC) identifiers of the remote peer
     */
    override fun getRemoteSourceIDs(): List<Long> {
        /*
         * TODO Returning an unmodifiable view of remoteSourceIDs prevents modifications of private
         * state from the outside but it does not prevent ConcurrentModificationException.
         */
        return Collections.unmodifiableList(remoteSourceIDs)
    }

    // JMF initializes the local SSRC upon #initialize(RTPConnector) so now's the time to
    // ask. As JMF stores the SSRC as a 32-bit signed integer value, convert it to unsigned.
    /**
     * Gets the `RTPManager` instance which sends and receives RTP and RTCP traffic on
     * behalf of this `MediaStream`. If the `RTPManager` does not exist yet, it is created.
     *
     * @return the `RTPManager` instance which sends and receives RTP and RTCP traffic on
     * behalf of this `MediaStream`
     */
    /**
     * The `RTPManager` which utilizes [.rtpConnector] and sends and receives RTP and
     * RTCP traffic on behalf of this `MediaStream`.
     */
    private var rtpManager: StreamRTPManager? = null
        get() {
            if (field == null) {
                val rtpConnector = rtpConnector ?: throw IllegalStateException("rtpConnector")

                field = StreamRTPManager(this, rtpTranslator)
                registerCustomCodecFormats(field!!)

                field!!.addReceiveStreamListener(this)
                field!!.addSendStreamListener(this)
                field!!.addSessionListener(this)
                field!!.addRemoteListener(this)

                val bc = field!!.getControl(BufferControl::class.java)
                configureRTPManagerBufferControl(field, bc)

                field!!.setSSRCFactory(ssrcFactory)
                field!!.initialize(rtpConnector)

                // JMF initializes the local SSRC upon #initialize(RTPConnector) so now's the time to
                // ask. As JMF stores the SSRC as a 32-bit signed integer value, convert it to
                // unsigned.
                val localSSRC = field!!.localSSRC
                setLocalSourceID(if (localSSRC == Long.MAX_VALUE) -1 else localSSRC and 0xFFFFFFFFL)
            }
            return field
        }

    /**
     * {@inheritDoc}
     */
    override val streamRTPManager: StreamRTPManager?
        get() = rtpManager

    /**
     * Returns the target of this `MediaStream` to which it is to send and from which it is
     * to receive data (e.g. RTP) and control data (e.g. RTCP).
     *
     * @return the `MediaStreamTarget` describing the data (e.g. RTP) and the control data
     * (e.g. RTCP) locations to which this `MediaStream` is to send and from which it is to receive
     * @see MediaStream.target
     */// Short-circuit if setting the same target.
    /**
     * Sets the target of this `MediaStream` to which it is to send and from which it is to
     * receive data (e.g. RTP) and control data (e.g. RTCP).
     *
     * target the `MediaStreamTarget` describing the data (e.g. RTP) and the control data
     * (e.g. RTCP) locations to which this `MediaStream` is to send and from which it is to receive
     * @see MediaStream.target
     */
    override var target: MediaStreamTarget?
        get() = rtpConnectorTarget
        set(target) {
            // Short-circuit if setting the same target.
            if (target == null) {
                if (rtpConnectorTarget == null) return
            }
            else if (target.equals(rtpConnectorTarget)) return
            doSetTarget(target)
        }

    /**
     * Returns the transport protocol used by the streams.
     *
     * @return the transport protocol (UDP or TCP) used by the streams. null if the stream connector is not instantiated.
     */
    override val transportProtocol: StreamConnector.Protocol?
        get() {
            val connector = (if (rtpConnector != null) rtpConnector!!.connector else null)
                    ?: return null
            return connector.protocol
        }


    /**
     * Determines whether this `MediaStream` is set to transmit "silence" instead of the
     * media being fed from its `MediaDevice`. "Silence" for video is understood as video
     * data which is not the captured video data and may represent, for example, a black image.
     *
     * @return `true` if this `MediaStream` is set to transmit "silence" instead of
     * the media fed from its `MediaDevice`; `false`, otherwise
     * @see MediaStream.isMute
     */
    override var isMute: Boolean
        get() = getDeviceSession()?.isMute ?: mute
        set(mute) {
            if (this.mute != mute) {
                Timber.d("%s stream with hashcode %s", if (mute) "Muting" else "Unmuting", hashCode())
                this.mute = mute
                getDeviceSession()?.isMute = this.mute
            }
        }

    /**
     * If necessary and the state of this `MediaStreamImpl` instance is appropriate, updates
     * the FMJ `Format`s registered with a specific `StreamRTPManager` in order to
     * possibly prevent the loss of format parameters (i.e. SDP fmtp) specified by the remote peer
     * and to be used for the playback of `ReceiveStream`s. The `Format`s in
     * [.dynamicRTPPayloadTypes] will likely represent the view of the local peer while the
     * `Format` set on this `MediaStream` instance will likely represent the view of
     * the remote peer. The view of the remote peer matters for the playback of `ReceiveStream`s.
     *
     * rtpManager the `StreamRTPManager` to update the registered FMJ `Format`s of. If
     * `null`, the method uses [.rtpManager].
     */
    private fun maybeUpdateDynamicRTPPayloadTypes(rtpManager1: StreamRTPManager?) {

        var rtpManager = rtpManager1
        if (rtpManager == null) {
            rtpManager = this.rtpManager
            if (rtpManager == null) return
        }

        val mediaFormat = format as? MediaFormatImpl<*> ?: return
        val format = mediaFormat.format as? ParameterizedVideoFormat ?: return

        for ((key, dynamicMediaFormat) in dynamicRTPPayloadTypes) {
            if (dynamicMediaFormat !is MediaFormatImpl<*>) continue
            val dynamicFormat = dynamicMediaFormat.format
            if (format.matches(dynamicFormat!!) && dynamicFormat.matches(format)) {
                rtpManager.addFormat(format, key.toInt())
            }
        }
    }

    /**
     * Prints all statistics available for [.rtpManager].
     *
     * rtpManager the `RTPManager` to print statistics for
     */
    private fun printFlowStatistics(rtpManager: StreamRTPManager) {
        try {
            if (!TimberLog.isFinestEnable) return

            // print flow statistics.
            val s = rtpManager.globalTransmissionStats
            val rtpstat = StatisticsEngine.RTP_STAT_PREFIX
            val mss = mediaStreamStats
            var buff = StringBuilder(rtpstat)
            val mediaType = mediaType
            val mediaTypeStr = mediaType?.toString() ?: ""
            val eol = "\n$rtpstat"
            buff.append("call stats for outgoing ").append(mediaTypeStr)
                    .append(" stream SSRC: ").append(getLocalSourceID()).append(eol)
                    .append("bytes sent: ").append(s.bytesSent).append(eol)
                    .append("RTP sent: ").append(s.rtpSent).append(eol)
                    .append("remote reported min inter-arrival jitter: ")
                    .append(mss.minUploadJitterMs).append("ms").append(eol)
                    .append("remote reported max inter-arrival jitter: ")
                    .append(mss.maxUploadJitterMs).append("ms").append(eol)
                    .append("local collisions: ").append(s.localColls)
                    .append(eol)
                    .append("remote collisions: ").append(s.remoteColls)
                    .append(eol)
                    .append("RTCP sent: ").append(s.rtcpSent).append(eol)
                    .append("transmit failed: ").append(s.transmitFailed)
            Timber.log(TimberLog.FINER, "%s", buff)
            val rs = rtpManager.globalReceptionStats
            val format = format
            buff = StringBuilder(rtpstat)
            buff.append("call stats for incoming ")
                    .append(format ?: "")
                    .append(" stream SSRC: ").append(getRemoteSourceID())
                    .append(eol)
                    .append("packets received: ").append(rs.packetsRecd)
                    .append(eol)
                    .append("bytes received: ").append(rs.bytesRecd)
                    .append(eol)
                    .append("packets lost: ")
                    .append(mss.receiveStats.packetsLost)
                    .append(eol)
                    .append("min inter-arrival jitter: ")
                    .append(statisticsEngine!!.minInterArrivalJitter)
                    .append(eol)
                    .append("max inter-arrival jitter: ")
                    .append(statisticsEngine!!.maxInterArrivalJitter)
                    .append(eol)
                    .append("RTCPs received: ").append(rs.rtcpRecd)
                    .append(eol)
                    .append("bad RTCP packets: ").append(rs.badRTCPPkts)
                    .append(eol)
                    .append("bad RTP packets: ").append(rs.badRTPkts)
                    .append(eol)
                    .append("local collisions: ").append(rs.localColls)
                    .append(eol)
                    .append("malformed BYEs: ").append(rs.malformedBye)
                    .append(eol)
                    .append("malformed RRs: ").append(rs.malformedRR)
                    .append(eol)
                    .append("malformed SDESs: ").append(rs.malformedSDES)
                    .append(eol)
                    .append("malformed SRs: ").append(rs.malformedSR)
                    .append(eol)
                    .append("packets looped: ").append(rs.packetsLooped)
                    .append(eol)
                    .append("remote collisions: ").append(rs.remoteColls)
                    .append(eol)
                    .append("SRs received: ").append(rs.srRecd)
                    .append(eol)
                    .append("transmit failed: ").append(rs.transmitFailed)
                    .append(eol)
                    .append("unknown types: ").append(rs.unknownTypes)
            Timber.log(TimberLog.FINER, "%s", buff)
        } catch (t: Throwable) {
            Timber.e(t, "Error writing statistics")
        }
    }

    private fun printReceiveStreamStatistics() {
        mediaStreamStatsImpl.updateStats()
        if (TimberLog.isTraceEnable) {
            val buff = StringBuilder("\nReceive stream stats: discarded RTP packets: ")
                    .append(mediaStreamStatsImpl.nbDiscarded)
                    .append("\nReceive stream stats: decoded with FEC: ")
                    .append(mediaStreamStatsImpl.nbFec)
            Timber.log(TimberLog.FINER, "%s", buff)
        }
    }

    /**
     * Recreates the `SendStream`s of this instance (i.e. of its `RTPManager`) as
     * necessary. For example, if there was no attempt to create the `SendStream`s prior to
     * the call, does nothing. If they were created prior to the call, closes them and creates them
     * again. If they were not started prior to the call, does not start them after recreating them.
     */
    protected fun recreateSendStreams() {
        if (sendStreamsAreCreated) {
            closeSendStreams()
            if (getDeviceSession() != null && rtpManager != null) {
                if (MediaDirection.SENDONLY == startedDirection
                        || MediaDirection.SENDRECV == startedDirection) startSendStreams()
            }
        }
    }

    /**
     * Registers any custom JMF `Format`s with a specific `RTPManager`. Extenders
     * should override in order to register their own customizations and should call back to this
     * super implementation during the execution of their override in order to register the
     * associations defined in this instance of (dynamic) RTP payload types to `MediaFormat`s.
     *
     * rtpManager the `RTPManager` to register any custom JMF `Format` s with
     */
    protected open fun registerCustomCodecFormats(rtpManager: StreamRTPManager) {
        for ((key, value) in dynamicRTPPayloadTypes) {
            val mediaFormatImpl = value as MediaFormatImpl<out Format>
            val format = mediaFormatImpl.format
            rtpManager.addFormat(format, key.toInt())
        }
        maybeUpdateDynamicRTPPayloadTypes(rtpManager)
    }

    /**
     * Removes a specific `ReceiveStream` from [.receiveStreams].
     *
     * receiveStream the `ReceiveStream` to remove
     * @return `true` if `receiveStreams` changed as a result of the method call;
     * otherwise, `false`
     */
    private fun removeReceiveStream(receiveStream: ReceiveStream): Boolean {
        val writeLock = receiveStreamsLock.writeLock()
        val readLock = receiveStreamsLock.readLock()
        var removed = false
        writeLock.lock()
        try {
            if (receiveStreams.remove(receiveStream)) {
                /*
                 * Downgrade the writeLock to a readLock in order to allow readers during the invocation of
                 * MediaDeviceSession#removeReceiveStream(ReceiveStream) (and disallow writers, of course).
                 */
                readLock.lock()
                removed = true
            }
        } finally {
            writeLock.unlock()
        }
        if (removed) {
            try {
                getDeviceSession()?.removeReceiveStream(receiveStream)
            } finally {
                readLock.unlock()
            }
        }
        return removed
    }

    /**
     * {@inheritDoc}
     */
    override fun removeReceiveStreamForSsrc(ssrc: Long) {
        val toRemove = getReceiveStream(ssrc.toInt())
        if (toRemove != null) removeReceiveStream(toRemove)
    }

    /**
     * Notifies this `MediaStream` implementation that its `RTPConnector` instance
     * has changed from a specific old value to a specific new value. Allows extenders to
     * override and perform additional processing after this `MediaStream` has changed its
     * `RTPConnector` instance.
     *
     * oldValue the `RTPConnector` of this `MediaStream` implementation before it got
     * changed to `newValue`
     * newValue the current `RTPConnector` of this `MediaStream` which replaced `oldValue`
     */
    protected open fun rtpConnectorChanged(oldValue: AbstractRTPConnector?, newValue: AbstractRTPConnector?) {
        if (newValue != null) {
            /*
             * Register the transform engines that we will be using in this stream.
             */
            if (newValue is RTPTransformUDPConnector) {
                transformEngineChain = createTransformEngineChain()
                newValue.setEngine(transformEngineChain!!)
            }
            else if (newValue is RTPTransformTCPConnector) {
                transformEngineChain = createTransformEngineChain()
                newValue.setEngine(transformEngineChain!!)
            }
            if (rtpConnectorTarget != null) doSetTarget(rtpConnectorTarget)

            // Trigger the re-configuration of RTP header extensions
            // addRTPExtension(0.toByte(), null)
        }
        srtpControl.setConnector(newValue)

        /*
         * TODO The following is a very ugly way to expose the RTPConnector created by this
         * instance so it may be configured from outside the class hierarchy. That's why the
         * property in use bellow is not defined as a well-known constant and is to be considered
         * internal and likely to be removed in a future revision.
         */
        try {
            firePropertyChange(MediaStreamImpl::class.java.name + ".rtpConnector", oldValue, newValue)
        } catch (t: Throwable) {
            if (t is ThreadDeath) throw t else Timber.e(t)
        }
    }

    /**
     * Notifies this instance that its [.rtpConnector] has created a new
     * `RTPConnectorInputStream` either RTP or RTCP.
     *
     * inputStream the new `RTPConnectorInputStream` instance created by the `rtpConnector`
     * of this instance
     * data `true` if `inputStream` will be used for RTP or `false` for RTCP
     */
    private fun rtpConnectorInputStreamCreated(inputStream: RTPConnectorInputStream<*>?, data: Boolean) {
        /*
         * TODO The following is a very ugly way to expose the RTPConnectorInputStreams created by
         * the rtpConnector of this instance so they may be configured from outside the class
         * hierarchy (e.g. to invoke addDatagramPacketFilter). That's why the property in use
         * bellow is not defined as a well-known constant and is to be considered internal and
         * likely to be removed in a future revision.
         */
        try {
            firePropertyChange(MediaStreamImpl::class.java.name + ".rtpConnector."
                    + (if (data) "data" else "control") + "InputStream", null, inputStream)
        } catch (t: Throwable) {
            if (t is ThreadDeath) throw t else Timber.e(t)
        }
    }

    /**
     * Sets the `StreamConnector` to be used by this instance for sending and receiving media.
     *
     * connector the `StreamConnector` to be used by this instance for sending and receiving media
     */
    final override fun setConnector(connector: StreamConnector) {
        // if (connector == null) throw NullPointerException("connector")
        val oldValue = rtpConnector
        // Is the StreamConnector really changing?
        if (oldValue != null && oldValue.connector == connector) return
        when (connector.protocol) {
            StreamConnector.Protocol.UDP -> rtpConnector = object : RTPTransformUDPConnector(connector) {
                @Throws(IOException::class)
                override fun createControlInputStream(): RTPConnectorUDPInputStream {
                    val s = super.createControlInputStream()
                    rtpConnectorInputStreamCreated(s, false)
                    return s
                }

                @Throws(IOException::class)
                override fun createDataInputStream(): RTPConnectorUDPInputStream {
                    val s = super.createDataInputStream()
                    rtpConnectorInputStreamCreated(s, true)
                    if (s != null) {
                        configureDataInputStream(s)
                    }
                    return s
                }

                @Throws(IOException::class)
                override fun createDataOutputStream(): TransformUDPOutputStream {
                    val s = super.createDataOutputStream()
                    if (s != null) configureDataOutputStream(s)
                    return s
                }
            }

            StreamConnector.Protocol.TCP -> rtpConnector = object : RTPTransformTCPConnector(connector) {
                @Throws(IOException::class)
                override fun createControlInputStream(): RTPConnectorTCPInputStream {
                    val s = super.createControlInputStream()
                    rtpConnectorInputStreamCreated(s, false)
                    return s
                }

                @Throws(IOException::class)
                override fun createDataInputStream(): RTPConnectorTCPInputStream {
                    val s = super.createDataInputStream()
                    rtpConnectorInputStreamCreated(s, true)
                    if (s != null) configureDataInputStream(s)
                    return s
                }

                @Throws(IOException::class)
                override fun createDataOutputStream(): TransformTCPOutputStream {
                    val s = super.createDataOutputStream()
                    if (s != null) configureDataOutputStream(s)
                    return s
                }
            }
            else -> throw IllegalArgumentException("connector")
        }
        rtpConnectorChanged(oldValue, rtpConnector)
    }

    /**
     * Sets the local SSRC identifier and fires the corresponding `PropertyChangeEvent`.
     *
     * localSourceID the SSRC identifier that this stream will be using in outgoing RTP packets from now on
     */
    protected open fun setLocalSourceID(localSourceID: Long) {
        if (this.mLocalSourceID != localSourceID) {
            val oldValue = this.mLocalSourceID
            this.setLocalSourceID(localSourceID)

            /*
             * If ZRTP is used, then let it know about the SSRC of the new SendStream. Currently,
             * ZRTP supports only one SSRC per engine.
             */
            val transformEngine = srtpControl.transformEngine
            if (transformEngine is ZRTPTransformEngine) {
                transformEngine.setOwnSSRC(localSourceID)
            }
            firePropertyChange(MediaStream.PNAME_LOCAL_SSRC, oldValue, this.getLocalSourceID())
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun setSSRCFactory(ssrcFactory: SSRCFactory) {
        if (this.ssrcFactory != ssrcFactory) {
            this.ssrcFactory = ssrcFactory as SSRCFactoryImpl

            val rtpManager = rtpManager
            val translator = rtpTranslator!!

            when {
                rtpManager != null -> rtpManager.setSSRCFactory(ssrcFactory)
                translator is RTPTranslatorImpl -> translator.setSSRCFactory(ssrcFactory)
            }
        }
    }

    /**
     * Starts capturing media from this stream's `MediaDevice` and then streaming it through
     * the local `StreamConnector` toward the stream's target address and port. Also puts
     * the `MediaStream` in a listening state which make it play all media received from the
     * `StreamConnector` on the stream's `MediaDevice`.
     *
     * @see MediaStream.start
     */
    /**
     * Determines whether [.start] has been called on this `MediaStream` without [.stop] or [.close] afterwards.
     *
     * @return `true` if [.start] has been called on this `MediaStream` without [.stop] or [.close] afterwards
     * @see MediaStream.isStarted
     */
    /**
     * The indicator which determines whether [.start] has been called on this
     * `MediaStream` without [.stop] or [.close].
     */
    override var isStarted = false

    override fun start() {
        start(direction)
        isStarted = true
    }

    /**
     * Starts the processing of media in this instance in a specific direction.
     *
     * direction a `MediaDirection` value which represents the direction of the processing of
     * media to be started. For example, [MediaDirection.SENDRECV] to start both
     * capture and playback of media in this instance or [MediaDirection.SENDONLY] to
     * only start the capture of media in this instance
     */
    private fun start(direction: MediaDirection?) {
        if (direction == null)
            throw NullPointerException("direction")

        /*
         * If the local peer is the focus of a conference for which it is to perform RTP
         * translation even without generating media to be sent, it should create its StreamRTPManager.
         */
        var getRTPManagerForRTPTranslator = true
        if (direction.allowsSending()
                && (startedDirection == null || !startedDirection!!.allowsSending())) {
            /*
             * The startSendStreams method will be called so the getRTPManager method will be
             * called as part of the execution of the former.
             */
            getRTPManagerForRTPTranslator = false
            startSendStreams()

            // cmeng: must send the actual direction which handled both send and receive stream at the same time;
            // else the remoteVideoContainer will get removed even if it is enabled
            // @link {VideoMediaDeviceSession#startedDirectionChanged(MediaDirection oldValue, MediaDirection newValue)}
            getDeviceSession()?.start(direction)

            when {
                MediaDirection.RECVONLY == startedDirection -> startedDirection = MediaDirection.SENDRECV
                startedDirection == null -> startedDirection = MediaDirection.SENDONLY
            }

            if (TimberLog.isTraceEnable) {
                val mediaType = mediaType
                val stats = mediaStreamStats
                Timber.i("%s codec/freq: %s/%s Hz", mediaType, stats.encoding, stats.encodingClockRate)
                Timber.i("%s remote IP/port: %s/%s", mediaType, stats.remoteIPAddress, stats.remotePort)
            }
        }
        if (direction.allowsReceiving()
                && (startedDirection == null || !startedDirection!!.allowsReceiving())) {
            /*
             * The startReceiveStreams method will be called so the getRTPManager method will be
             * called as part of the execution of the former.
             */
            getRTPManagerForRTPTranslator = false
            startReceiveStreams()
            getDeviceSession()?.start(direction)
            when {
                MediaDirection.SENDONLY == startedDirection -> startedDirection = MediaDirection.SENDRECV
                startedDirection == null -> startedDirection = MediaDirection.RECVONLY
            }
        }

        /*
         * If the local peer is the focus of a conference for which it is to perform RTP
         * translation even without generating media to be sent, it should create its StreamRTPManager.
         */
        if (getRTPManagerForRTPTranslator && rtpTranslator != null)
            rtpManager
    }

    /**
     * Starts the `ReceiveStream`s that this instance is receiving from its remote peer. By
     * design, a `MediaStream` instance is associated with a single
     * `ReceiveStream` at a time. However, the `ReceiveStream`s are created by
     * `RTPManager` and it tracks multiple `ReceiveStream`s. In practice, the
     * `RTPManager` of this `MediaStreamImpl` will have a single `ReceiveStream` in its list.
     */
    private fun startReceiveStreams() {
        /*
         * The ReceiveStreams originate from RtpManager, make sure that there is an actual
         * RTPManager to initialize ReceiveStreams which are then to be started.
         */
        rtpManager
        for (receiveStream in getReceiveStreams()) {
            try {

                /*
                 * For an unknown reason, the stream DataSource can be null at the end of the Call
                 * after re-invites have been handled.
                 */
                receiveStream!!.dataSource?.start()
            } catch (ioex: IOException) {
                Timber.w(ioex, "Failed to start receive stream %s", receiveStream)
            }
        }
    }

    /**
     * Starts the `SendStream`s of the `RTPManager` of this `MediaStreamImpl`.
     */
    private fun startSendStreams() {
        /*
         * Until it's clear that the SendStreams are required (i.e. we've negotiated to send), they
         * will not be created. Otherwise, their creation isn't only illogical but also causes the
         * CaptureDevice to be used.
         */
        if (!sendStreamsAreCreated)
            createSendStreams()

        val rtpManager = this.rtpManager
        val sendStreams = rtpManager!!.sendStreams

        if (sendStreams != null) {
            for (sendStream in sendStreams) {
                try {
                    val sendStreamDataSource = (sendStream as RTPStream).dataSource

                    // TODO Are we sure we want to connect here?
                    sendStreamDataSource.connect()
                    (sendStream as SendStream).start()
                    sendStreamDataSource.start()
                    Timber.log(TimberLog.FINER, "Started SendStream with hashCode %s", sendStream.hashCode())
                } catch (ioe: IOException) {
                    Timber.w(ioe, "Failed to start stream %s", sendStream)
                }
            }
        }
    }

    /**
     * Stops all streaming and capturing in this `MediaStream` and closes and releases all
     * open/allocated devices/resources. Has no effect if this `MediaStream` is already
     * closed and is simply ignored.
     *
     * @see MediaStream.stop
     */
    override fun stop() {
        stop(MediaDirection.SENDRECV)
        isStarted = false
    }

    /**
     * Stops the processing of media in this instance in a specific direction.
     *
     * direction a `MediaDirection` value which represents the direction of the processing of
     * media to be stopped. For example, [MediaDirection.SENDRECV] to stop both capture
     * and playback of media in this instance or [MediaDirection.SENDONLY] to only stop
     * the capture of media in this instance
     */
    private fun stop(direction: MediaDirection?) {
        if (direction == null) throw NullPointerException("direction")
        if (rtpManager == null) return
        if ((MediaDirection.SENDRECV == direction || MediaDirection.SENDONLY == direction)
                && (MediaDirection.SENDRECV == startedDirection || MediaDirection.SENDONLY == startedDirection)) {
            /*
             * XXX It is not very clear at the time of this writing whether the SendStreams are to
             * be stopped or closed. On one hand, stopping a direction may be a temporary
             * transition which relies on retaining the SSRC. On the other hand, it may be
             * permanent. In which case, the respective ReceiveStream on the remote peer will
             * timeout at some point in time. In the context of video conferences, when a member
             * stops the streaming of their video without leaving the conference, they will stop
             * their SendStreams. However, the other members will need respective BYE RTCP
             * packets in order to know that they are to remove the associated ReceiveStreams
             * from display. The initial version of the code here used to stop the SendStreams
             * without closing them but, given the considerations above, it is being changed to
             * close them in the case of video.
             */
            stopSendStreams(this is VideoMediaStream)
            if (getDeviceSession() != null) getDeviceSession()!!.stop(MediaDirection.SENDONLY)
            when {
                MediaDirection.SENDRECV == startedDirection -> startedDirection = MediaDirection.RECVONLY
                MediaDirection.SENDONLY == startedDirection -> startedDirection = null
            }
        }
        if ((MediaDirection.SENDRECV == direction || MediaDirection.RECVONLY == direction)
                && (MediaDirection.SENDRECV == startedDirection || MediaDirection.RECVONLY == startedDirection)) {
            stopReceiveStreams()

            if (getDeviceSession() != null) getDeviceSession()!!.stop(MediaDirection.RECVONLY)
            when {
                MediaDirection.SENDRECV == startedDirection -> startedDirection = MediaDirection.SENDONLY
                MediaDirection.RECVONLY == startedDirection -> startedDirection = null
            }
        }
    }

    /**
     * Stops the `ReceiveStream`s that this instance is receiving from its remote peer. By
     * design, a `MediaStream` instance is associated with a single
     * `ReceiveStream` at a time. However, the `ReceiveStream`s are created by
     * `RTPManager` and it tracks multiple `ReceiveStream`s. In practice, the
     * `RTPManager` of this `MediaStreamImpl` will have a single
     * `ReceiveStream` in its list.
     */
    private fun stopReceiveStreams() {
        for (receiveStream in getReceiveStreams()) {
            try {
                Timber.log(TimberLog.FINER, "Stopping receive stream with hashcode %s", receiveStream.hashCode())

                /*
                 * For an unknown reason, the stream DataSource can be null at the end of the Call
                 * after re-invites have been handled.
                 */
                receiveStream!!.dataSource?.stop()
            } catch (ioex: IOException) {
                Timber.w(ioex, "Failed to stop receive stream %s", receiveStream)
            }
        }
    }

    /**
     * Stops the `SendStream`s that this instance is sending to its remote peer and optionally closes them.
     *
     * close `true` to close the `SendStream`s that this instance is sending to its
     * remote peer after stopping them; `false` to only stop them
     * @return the `SendStream`s which were stopped
     */
    private fun stopSendStreams(close: Boolean): Iterable<SendStream>? {
        if (rtpManager == null) return null
        val sendStreams = rtpManager!!.sendStreams as Vector<SendStream>
        val stoppedSendStreams = stopSendStreams(sendStreams, close)
        if (close) sendStreamsAreCreated = false
        return stoppedSendStreams
    }

    /**
     * Stops specific `SendStream`s and optionally closes them.
     *
     * sendStreams the `SendStream`s to be stopped and optionally closed
     * close `true` to close the specified `SendStream`s after stopping them;
     * `false` to only stop them
     * @return the stopped `SendStream`s
     */
    private fun stopSendStreams(sendStreams: Iterable<SendStream>?, close: Boolean): Iterable<SendStream>? {
        if (sendStreams == null) return null
        for (sendStream in sendStreams) {
            try {
                Timber.log(TimberLog.FINER, "Stopping send stream with hashcode %s", sendStream.hashCode())
                sendStream.dataSource.stop()
                sendStream.stop()
                if (close) {
                    try {
                        sendStream.close()
                    } catch (npe: NullPointerException) {
                        /*
                         * Sometimes com.sun.media.rtp.RTCPTransmitter#bye() may throw
                         * NullPointerException but it does not seem to be guaranteed because it
                         * does not happen while debugging and stopping at a breakpoint on
                         * SendStream#close(). One of the cases in which it appears upon call
                         * hang-up is if we do not close the "old" SendStreams upon re-invite(s).
                         * Though we are now closing such SendStreams, ignore the exception here
                         * just in case because we already ignore IOExceptions.
                         */
                        Timber.e(npe, "Failed to close send stream %s", sendStream)
                    }
                }
            } catch (ioe: IOException) {
                Timber.w(ioe, "Failed to stop send stream %s", sendStream)
            }
        }
        return sendStreams
    }

    /**
     * Notifies this `ReceiveStreamListener` that the `RTPManager` it is registered
     * with has generated an event related to a `ReceiveStream`.
     *
     * ev the `ReceiveStreamEvent` which specifies the `ReceiveStream`
     * that is the cause of the event, and the very type of the event
     * @see ReceiveStreamListener.update
     */
    override fun update(ev: ReceiveStreamEvent) {
        val receiveStream = ev.receiveStream
        val hasStream = receiveStream != null
        if (ev is NewReceiveStreamEvent) {
            // XXX we might consider not adding (or not starting) new ReceiveStreams
            // unless this MediaStream's direction allows receiving.
            Timber.d("Received ReceiveStreamEvent (new): %s = %s", receiveStreams.contains(receiveStream), ev)
            if (hasStream) {
                val receiveStreamSSRC = 0xFFFFFFFFL and receiveStream.ssrc
                addRemoteSourceID(receiveStreamSSRC)
                addReceiveStream(receiveStream)
            }
        }
        else if (ev is TimeoutEvent) {
            val participant = ev.getParticipant()

            // If we recreate streams, we will already have restarted zrtpControl,
            // but when on the other end someone recreates his streams, we will receive a
            // ByeEvent (which extends TimeoutEvent) and then we must also restart our ZRTP.
            // This happens, for example, when we are already in a call and the remote peer
            // converts his side of the call into a conference call.
            // if(!zrtpRestarted)
            //		restartZrtpControl();
            val receiveStreamsToRemove = ArrayList<ReceiveStream?>()
            if (hasStream) {
                receiveStreamsToRemove.add(receiveStream)
                Timber.w("### Receiving stream TimeoutEvent occurred for: %s", receiveStream)
            }
            else if (participant != null) {
                val receiveStreams = getReceiveStreams()
                val rtpManagerReceiveStreams = rtpManager!!.receiveStreams
                for (receiveStreamX in receiveStreams) {
                    if (participant == receiveStreamX!!.participant && !participant.streams.contains(receiveStreamX)
                            && !rtpManagerReceiveStreams.contains(receiveStreamX)) {
                        receiveStreamsToRemove.add(receiveStreamX)
                    }
                }
            }

            // cmeng: can happen when remote video streaming is enabled but arriving
            // late/TimeoutEvent happen or remote video streaming is stop/terminated
            for (receiveStreamX in receiveStreamsToRemove) {
                removeReceiveStream(receiveStreamX!!)

                // The DataSource needs to be disconnected, because otherwise
                // its RTPStream thread will stay alive. We do this here because
                // we observed that in certain situations it fails to be done earlier.
                receiveStreamX.dataSource?.disconnect()
            }
        }
        else if (ev is RemotePayloadChangeEvent) {
            if (hasStream) {
                if (getDeviceSession() != null) {
                    val transcodingDS = getDeviceSession()!!.getTranscodingDataSource(receiveStream)

                    // we receive packets, streams are active if processor in transcoding
                    // DataSource is running, we need to recreate it by disconnect,
                    // connect and starting again the DataSource
                    try {
                        if (transcodingDS != null) {
                            transcodingDS.disconnect()
                            transcodingDS.connect()
                            transcodingDS.start()
                        }

                        // as output streams of the DataSource are recreated we
                        // need to update mixers and everything that are using them
                        getDeviceSession()!!.playbackDataSourceChanged(receiveStream.dataSource)
                    } catch (e: IOException) {
                        Timber.e(e, "Error re-creating TranscodingDataSource's processor!")
                    }
                }
            }
        }
    }

    /**
     * Method called back in the RemoteListener to notify listener of all RTP Remote
     * Events.RemoteEvents are one of ReceiverReportEvent, SenderReportEvent or RemoteCollisionEvent
     *
     * ev the event
     */
    override fun update(ev: RemoteEvent) {
        if (ev is SenderReportEvent || ev is ReceiverReportEvent) {
            val report: Report
            var senderReport = false
            if (ev is SenderReportEvent) {
                numberOfReceivedSenderReports++
                report = ev.report
                senderReport = true
            }
            else {
                numberOfReceivedReceiverReports++
                report = (ev as ReceiverReportEvent).report
            }
            var feedback: Feedback? = null
            var remoteJitter = -1L
            if (report.feedbackReports.size > 0) {
                feedback = report.feedbackReports[0] as Feedback
                remoteJitter = feedback.jitter
                mediaStreamStats.updateRemoteJitter(remoteJitter)
            }

            // Notify encoders of the percentage of packets lost by the other side.
            // See RFC3550 Section 6.4.1 for the interpretation of 'fraction lost'
            if (feedback != null && direction != MediaDirection.INACTIVE) {
                var plaes: Set<PacketLossAwareEncoder?>? = null
                if (getDeviceSession() != null) plaes = getDeviceSession()!!.getEncoderControls(
                    PacketLossAwareEncoder::class.java)
                if (plaes != null && plaes.isNotEmpty()) {
                    val expectedPacketLoss = feedback.fractionLost * 100 / 256
                    for (plae in plaes) {
                        plae?.setExpectedPacketLoss(expectedPacketLoss)
                    }
                }
            }

            /*
             * The level of logger used here is in accord with the level of
             * logger used in StatisticsEngine where sent reports are logged.
             */
            if (TimberLog.isTraceEnable) {
                // As reports are received on every 5 seconds
                // print every 4th packet, on every 20 seconds
                if ((numberOfReceivedSenderReports + numberOfReceivedReceiverReports) % 4 != 1L) return
                val buff = StringBuilder(StatisticsEngine.RTP_STAT_PREFIX)
                val mediaType = mediaType
                val mediaTypeStr = mediaType?.toString() ?: ""
                buff.append("Received a ")
                        .append(if (senderReport) "sender" else "receiver")
                        .append(" report for ")
                        .append(mediaTypeStr)
                        .append(" stream SSRC:")
                        .append(getLocalSourceID())
                        .append(" [")
                if (senderReport) {
                    buff.append("packet count:")
                            .append((report as SenderReport).senderPacketCount)
                            .append(", bytes:")
                            .append(report.senderByteCount)
                }
                if (feedback != null) {
                    buff.append(", inter-arrival jitter:")
                            .append(remoteJitter)
                            .append(", lost packets:").append(feedback.numLost)
                            .append(", time since previous report:")
                            .append((feedback.dlsr / 65.536).toInt())
                            .append("ms")
                }
                buff.append(" ]")
                Timber.log(TimberLog.FINER, "%s", buff)
            }
        }
    }

    /**
     * Notifies this `SendStreamListener` that the `RTPManager` it is registered with
     * has generated an event related to a `SendStream`.
     *
     * ev the `SendStreamEvent` which specifies the `SendStream` that is the cause
     * of the event and the very type of the event
     * @see SendStreamListener.update
     */
    override fun update(ev: SendStreamEvent) {
        if (ev is NewSendStreamEvent) {
            /*
             * JMF stores the synchronization source (SSRC) identifier as a 32-bit signed integer,
             * we store it as unsigned.
             */
            val localSourceID = ev.getSendStream().ssrc and 0xFFFFFFFFL
            if (this.mLocalSourceID != localSourceID) setLocalSourceID(localSourceID)
        }
    }

    /**
     * Notifies this `SessionListener` that the `RTPManager` it is registered with
     * has generated an event which pertains to the session as a whole and does not belong to a
     * `ReceiveStream` or a `SendStream` or a remote participant necessarily.
     *
     * ev the `SessionEvent` which specifies the source and the very type of the event
     * @see SessionListener.update
     */
    override fun update(ev: SessionEvent) {}

    /**
     * {@inheritDoc}
     */
    override fun setExternalTransformer(transformEngine: TransformEngine) {
        externalTransformerWrapper.wrapped = transformEngine
    }

    /**
     * {@inheritDoc}
     */
    @Throws(TransmissionFailedException::class)
    override fun injectPacket(pkt: RawPacket?, data: Boolean, after: TransformEngine?) {
        var iAfter = after
        try {
            if (pkt?.buffer == null) {
                // It's a waste of time to invoke the method with a null pkt so disallow it.
                throw NullPointerException(if (pkt == null) "pkt" else "pkt.getBuffer()")
            }
            val rtpConnector = rtpConnector ?: throw IllegalStateException("rtpConnector")
            val outputStream = if (data) rtpConnector.getDataOutputStream(false)
            else rtpConnector.getControlOutputStream(false)

            // We utilize TransformEngineWrapper, so it is possible to have after wrapped. Unless
            // we wrap after, pkt will go through the whole TransformEngine chain (which is
            // obviously not the idea of the caller).
            if (iAfter != null) {
                val wrapper: TransformEngineWrapper<*>

                // externalTransformerWrapper
                wrapper = externalTransformerWrapper
                if (wrapper.contains(iAfter)) {
                    iAfter = wrapper
                }
            }
            outputStream!!.write(pkt.buffer, pkt.offset, pkt.length, iAfter)
        } catch (e: IllegalStateException) {
            throw TransmissionFailedException(e)
        } catch (e: IOException) {
            throw TransmissionFailedException(e)
        } catch (e: NullPointerException) {
            throw TransmissionFailedException(e)
        }
    }

    /**
     * {@inheritDoc}
     *
     * pkt the packet from which to get the temporal layer id
     * @return the TID of the packet, -1 otherwise.
     *
     * FIXME(gp) conceptually this belongs to the [VideoMediaStreamImpl],
     * but I don't want to be obliged to cast to use this method.
     */
    fun getTemporalID(pkt: RawPacket): Int {
        if (frameMarkingsExtensionId != -1) {
            val fmhe = pkt.getHeaderExtension(frameMarkingsExtensionId.toByte())
            if (fmhe != null) {
                return FrameMarkingHeaderExtension.getTemporalID(fmhe).toInt()
            }
            // Note that we go on and try to use the payload itself. We may want
            // to change this behaviour in the future, because it will give
            // wrong results if the payload is encrypted.
        }
        val redBlock = getPrimaryREDBlock(pkt)
        if (redBlock == null || redBlock.length == 0) {
            return -1
        }
        val vp8PT = getDynamicRTPPayloadType(Constants.VP8)
        val vp9PT = getDynamicRTPPayloadType(Constants.VP9)
        return if (redBlock.payloadType == vp8PT) {
            org.atalk.impl.neomedia.codec.video.vp8.DePacketizer.VP8PayloadDescriptor
                    .getTemporalLayerIndex(redBlock.buffer, redBlock.offset, redBlock.length)
        }
        else if (redBlock.payloadType == vp9PT) {
            org.atalk.impl.neomedia.codec.video.vp9.DePacketizer.VP9PayloadDescriptor
                    .getTemporalLayerIndex(redBlock.buffer, redBlock.offset, redBlock.length)
        }
        else {
            // XXX not implementing temporal layer detection should not break things.
            -1
        }
    }

    /**
     * Utility method that determines the spatial layer index (SID) of an RTP packet.
     *
     * pkt the RTP packet.
     * @return the SID of the packet, -1 otherwise.
     *
     * FIXME(gp) conceptually this belongs to the [VideoMediaStreamImpl],
     * but I don't want to be obliged to cast to use this method.
     */
    fun getSpatialID(pkt: RawPacket): Int {
        if (frameMarkingsExtensionId != -1) {
            val encoding = getFormat(pkt.payloadType)!!.encoding
            val fmhe = pkt.getHeaderExtension(frameMarkingsExtensionId.toByte())
            if (fmhe != null) {
                return FrameMarkingHeaderExtension.getSpatialID(fmhe, encoding).toInt()
            }
            // Note that we go on and try to use the payload itself. We may want
            // to change this behaviour in the future, because it will give
            // wrong results if the payload is encrypted.
        }
        val redBlock = getPrimaryREDBlock(pkt)
        if (redBlock == null || redBlock.length == 0) {
            return -1
        }
        val vp9PT = getDynamicRTPPayloadType(Constants.VP9)
        return if (redBlock.payloadType == vp9PT) {
            org.atalk.impl.neomedia.codec.video.vp9.DePacketizer.VP9PayloadDescriptor
                    .getSpatialLayerIndex(redBlock.buffer, redBlock.offset, redBlock.length)
        }
        else {
            // XXX not implementing temporal layer detection should not break things.
            -1
        }
    }

    /**
     * Returns a boolean that indicates whether we're able to detect the frame boundaries
     * for the codec of the packet that is specified as an argument.
     *
     * pkt the [RawPacket] that holds the RTP packet.
     * @return true if we're able to detect the frame boundaries for the codec
     * of the packet that is specified as an argument, false otherwise.
     */
    fun supportsFrameBoundaries(pkt: RawPacket): Boolean {
        return if (frameMarkingsExtensionId == -1) {
            val redBlock = getPrimaryREDBlock(pkt)
            if (redBlock != null && redBlock.length != 0) {
                val vp9PT = getDynamicRTPPayloadType(Constants.VP9)
                val vp8PT = getDynamicRTPPayloadType(Constants.VP8)
                val pt = redBlock.payloadType
                vp9PT == pt || vp8PT == pt
            }
            else {
                false
            }
        }
        else {
            pkt.getHeaderExtension(frameMarkingsExtensionId.toByte()) != null
        }
    }

    /**
     * Utility method that determines whether a packet is the start of frame.
     *
     * pkt raw rtp packet.
     * @return true if the packet is the start of a frame, false otherwise.
     *
     * FIXME(gp) conceptually this belongs to the [VideoMediaStreamImpl],
     * but I don't want to be obliged to cast to use this method.
     */
    fun isStartOfFrame(pkt: RawPacket): Boolean {
        if (!RTPPacketPredicate.INSTANCE.test(pkt)) {
            return false
        }
        if (frameMarkingsExtensionId != -1) {
            val fmhe = pkt.getHeaderExtension(frameMarkingsExtensionId.toByte())
            if (fmhe != null) {
                return FrameMarkingHeaderExtension.isStartOfFrame(fmhe)
            }
            // Note that we go on and try to use the payload itself. We may want
            // to change this behaviour in the future, because it will give
            // wrong results if the payload is encrypted.
        }
        val redBlock = getPrimaryREDBlock(pkt)
        if (null == redBlock || 0 == redBlock.length) {
            return false
        }
        val vp8PT = getDynamicRTPPayloadType(Constants.VP8)
        val vp9PT = getDynamicRTPPayloadType(Constants.VP9)
        return if (redBlock.payloadType == vp8PT) {
            org.atalk.impl.neomedia.codec.video.vp8.DePacketizer.VP8PayloadDescriptor.isStartOfFrame(
                redBlock.buffer, redBlock.offset)
        }
        else if (redBlock.payloadType == vp9PT) {
            org.atalk.impl.neomedia.codec.video.vp9.DePacketizer.VP9PayloadDescriptor.isStartOfFrame(
                redBlock.buffer, redBlock.offset, redBlock.length)
        }
        else {
            false
        }
    }

    /**
     * Utility method that determines whether a packet is an end of frame.
     *
     * pkt raw rtp packet.
     * @return true if the packet is the end of a frame, false otherwise.
     *
     * FIXME(gp) conceptually this belongs to the [VideoMediaStreamImpl],
     * but I don't want to be obliged to cast to use this method.
     */
    fun isEndOfFrame(pkt: RawPacket): Boolean {
        if (!RTPPacketPredicate.INSTANCE.test(pkt)) {
            return false
        }
        if (frameMarkingsExtensionId != -1) {
            val fmhe = pkt.getHeaderExtension(frameMarkingsExtensionId.toByte())
            if (fmhe != null) {
                return FrameMarkingHeaderExtension.isEndOfFrame(fmhe)
            }
            // Note that we go on and try to use the payload itself. We may want
            // to change this behaviour in the future, because it will give
            // wrong results if the payload is encrypted.
        }
        val redBlock = getPrimaryREDBlock(pkt)
        if (redBlock == null || redBlock.length == 0) {
            return false
        }
        val vp9PT = getDynamicRTPPayloadType(Constants.VP9)
        return if (redBlock.payloadType == vp9PT) {
            org.atalk.impl.neomedia.codec.video.vp9.DePacketizer.VP9PayloadDescriptor.isEndOfFrame(
                redBlock.buffer, redBlock.offset, redBlock.length)
        }
        else {
            RawPacket.isPacketMarked(pkt)
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is absolutely terrible, but we need a RawPacket and the method is
     * used from RTPTranslator, which doesn't work with RawPacket.
     */
    override fun isKeyFrame(buf: ByteArray, off: Int, len: Int): Boolean {
        return isKeyFrame(RawPacket(buf, off, len))
    }

    /**
     * {@inheritDoc}
     */
    override fun isKeyFrame(pkt: RawPacket): Boolean {
        // XXX merge with GenericAdaptiveTrackProjectionContext.isKeyframe().
        if (!RTPPacketPredicate.INSTANCE.test(pkt)) {
            return false
        }
        if (frameMarkingsExtensionId != -1) {
            val fmhe = pkt.getHeaderExtension(frameMarkingsExtensionId.toByte())
            if (fmhe != null) {
                return FrameMarkingHeaderExtension.isKeyframe(fmhe)
            }
            // Note that we go on and try to use the payload itself. We may want
            // to change this behaviour in the future, because it will give
            // wrong results if the payload is encrypted.
        }
        val redBlock = getPrimaryREDBlock(pkt)
        if (redBlock == null || redBlock.length == 0) {
            return false
        }
        val vp8PT = getDynamicRTPPayloadType(Constants.VP8)
        val vp9PT = getDynamicRTPPayloadType(Constants.VP9)
        val h264PT = getDynamicRTPPayloadType(Constants.H264)
        return if (redBlock.payloadType == vp8PT) {
            org.atalk.impl.neomedia.codec.video.vp8.DePacketizer.isKeyFrame(
                redBlock.buffer, redBlock.offset, redBlock.length)
        }
        else if (redBlock.payloadType == vp9PT) {
            org.atalk.impl.neomedia.codec.video.vp9.DePacketizer.isKeyFrame(
                redBlock.buffer, redBlock.offset, redBlock.length)
        }
        else if (redBlock.payloadType == h264PT) {
            org.atalk.impl.neomedia.codec.video.h264.DePacketizer.isKeyFrame(
                redBlock.buffer, redBlock.offset, redBlock.length)
        }
        else {
            false
        }
    }

    /**
     * {@inheritDoc}
     * <br></br>
     * Note that the chain is only initialized when a [StreamConnector] is set for the
     * [MediaStreamImpl] via [.setConnector] or by passing a
     * non-null connector to the constructor. Until the chain is initialized, this method will return null.
     */
    /**
     * The chain used to by the RTPConnector to transform packets.
     */
    override var transformEngineChain: TransformEngineChain? = null

    /**
     * The `RetransmissionRequester` instance for this `MediaStream` which will request
     * missing packets by sending RTCP NACKs.
     */
    override val retransmissionRequester = createRetransmissionRequester()

    /**
     * {@inheritDoc}
     */
    override fun getPrimaryREDBlock(baf: ByteArrayBuffer): REDBlock? {
        return getPrimaryREDBlock(RawPacket(baf.buffer, baf.offset, baf.length))
    }

    /**
     * Gets the [REDBlock] that contains the payload of the packet passed in as a parameter.
     *
     * pkt the packet from which we want to get the primary RED block
     * @return the [REDBlock] that contains the payload of the packet
     * passed in as a parameter, or null if the buffer is invalid.
     */
    override fun getPrimaryREDBlock(pkt: RawPacket): REDBlock? {
        if (pkt.length < RawPacket.FIXED_HEADER_SIZE) {
            return null
        }
        val redPT = getDynamicRTPPayloadType(Constants.RED)
        val pktPT = pkt.payloadType
        return if (redPT == pktPT) {
            REDBlockIterator.getPrimaryBlock(pkt.buffer, pkt.offset, pkt.length)
        }
        else {
            REDBlock(pkt.buffer, pkt.payloadType.toInt(), pkt.payloadLength, pktPT)
        }
    }

    /**
     * Gets the `RtxTransformer`, if any, used by the `MediaStream`.
     *
     * @return the `RtxTransformer` used by the `MediaStream` or `null`
     */
    open val rtxTransformer: RtxTransformer?
        get() = null

    /**
     * Creates the [DiscardTransformEngine] for this stream. Allows extenders to override.
     */
    protected open fun createDiscardEngine(): DiscardTransformEngine? {
        return null
    }

    /**
     * Gets the RTCP termination for this [MediaStreamImpl].
     */
    protected open val rtcpTermination: TransformEngine?
        get() = null

    /**
     * Gets the [PaddingTermination] for this [MediaStreamImpl].
     */
    protected open val paddingTermination: PaddingTermination?
        get() = null

    /**
     * Gets the `RemoteBitrateEstimator` of this `VideoMediaStream`.
     *
     * @return the `RemoteBitrateEstimator` of this
     * `VideoMediaStream` if any; otherwise, `null`
     */
    open val remoteBitrateEstimator: RemoteBitrateEstimatorWrapper?
        get() = null

    /**
     * Code that runs when the dynamic payload types change.
     */
    private fun onDynamicPayloadTypesChanged() {
        this.rtxTransformer?.onDynamicPayloadTypesChanged()
    }

    /**
     * {@inheritDoc}
     */
    override fun setTransportCCEngine(engine: TransportCCEngine?) {
        if (mTransportCCEngine != null) {
            mTransportCCEngine!!.removeMediaStream(this)
        }
        mTransportCCEngine = engine
        if (mTransportCCEngine != null) {
            mTransportCCEngine!!.addMediaStream(this)
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun setRTPTranslator(rtpTranslator: RTPTranslator?) {
        super.setRTPTranslator(rtpTranslator)
        if (getDeviceSession() != null) {
            getDeviceSession()!!.useTranslator = (rtpTranslator != null)
        }
    }

    companion object {
        /**
         * The name of the property indicating the length of our receive buffer.
         */
        const val PROPERTY_NAME_RECEIVE_BUFFER_LENGTH = "neomedia.RECEIVE_BUFFER_LENGTH"

        const val m1Byte = (-1).toByte()

        /**
         * Returns a human-readable representation of a specific `DataSource` instance in the
         * form of a `String` value.
         *
         * dataSource the `DataSource` to return a human-readable representation of
         * @return a `String` value which gives a human-readable representation of the specified `dataSource`
         */
        fun toString(dataSource: DataSource): String {
            val str = StringBuilder()
            str.append(dataSource.javaClass.simpleName)
            str.append(" with hashCode ")
            str.append(dataSource.hashCode())
            val locator = dataSource.locator
            if (locator != null) {
                str.append(" and locator ")
                str.append(locator)
            }
            return str.toString()
        }
    }
}