/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import ch.imvs.sdes4j.srtp.SrtpCryptoAttribute
import net.java.sip.communicator.service.protocol.CallPeerState
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.protocol.media.CallPeerMediaHandler
import okhttp3.internal.notify
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.call.VideoCallActivity
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.impl.neomedia.format.MediaFormatImpl
import org.atalk.impl.neomedia.transform.dtls.DtlsControlImpl
import org.atalk.impl.neomedia.transform.dtls.DtlsControlImpl.Companion.setTlsCertificateSA
import org.atalk.impl.neomedia.transform.zrtp.ZrtpControlImpl.Companion.generateMyZid
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.DtlsControl
import org.atalk.service.neomedia.DtlsControl.Setup
import org.atalk.service.neomedia.MediaDirection
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.MediaStreamTarget
import org.atalk.service.neomedia.QualityControl
import org.atalk.service.neomedia.QualityPreset
import org.atalk.service.neomedia.RTPExtension
import org.atalk.service.neomedia.SDesControl
import org.atalk.service.neomedia.SrtpControlType
import org.atalk.service.neomedia.StreamConnector
import org.atalk.service.neomedia.VideoMediaStream
import org.atalk.service.neomedia.ZrtpControl
import org.atalk.service.neomedia.device.MediaDevice
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.util.MediaType
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smackx.colibri.ColibriConferenceIQ
import org.jivesoftware.smackx.disco.packet.DiscoverInfo
import org.jivesoftware.smackx.jingle.element.JingleContent
import org.jivesoftware.smackx.jingle.element.JingleContent.Senders
import org.jivesoftware.smackx.jingle_rtp.JingleUtils
import org.jivesoftware.smackx.jingle_rtp.element.IceUdpTransport
import org.jivesoftware.smackx.jingle_rtp.element.InputEvent
import org.jivesoftware.smackx.jingle_rtp.element.ParameterElement
import org.jivesoftware.smackx.jingle_rtp.element.PayloadType
import org.jivesoftware.smackx.jingle_rtp.element.RtcpMux
import org.jivesoftware.smackx.jingle_rtp.element.RtpDescription
import org.jivesoftware.smackx.jingle_rtp.element.SdpCrypto
import org.jivesoftware.smackx.jingle_rtp.element.SdpSource
import org.jivesoftware.smackx.jingle_rtp.element.SrtpEncryption
import org.jivesoftware.smackx.jingle_rtp.element.SrtpFingerprint
import org.jivesoftware.smackx.jingle_rtp.element.ZrtpHash
import timber.log.Timber
import java.awt.Component
import java.beans.PropertyChangeEvent
import java.lang.reflect.UndeclaredThrowableException
import java.util.*

/**
 * An XMPP specific extension of the generic media handler.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Hristo Terezov
 * @author Boris Grozev
 * @author Eng Chong Meng
 * @author MilanKral
 */
open class CallPeerMediaHandlerJabberImpl(peer: CallPeerJabberImpl) : CallPeerMediaHandler<CallPeerJabberImpl>(peer,
    peer) {
    /**
     * The current description of the streams that we have going toward the remote side. We use
     * [LinkedHashMap]s to make sure that we preserve the order of the individual content extensions.
     */
    private val localContentMap = LinkedHashMap<String?, JingleContent?>()

    /**
     * The `QualityControl` of this `CallPeerMediaHandler`.
     */
    private val qualityControls: QualityControlWrapper?

    /**
     * The current description of the streams that the remote side has with us. We use
     * [LinkedHashMap]s to make sure that we preserve the order of the individual content extensions.
     */
    private val remoteContentMap = LinkedHashMap<String?, JingleContent?>()
    /**
     * {@inheritDoc}
     *
     * The super implementation relies on the direction of the streams and is therefore not
     * accurate when we use a Videobridge.
     */
    /*
     * In conferences we use INACTIVE to prevent, for example, on-hold music from
     * being played to all the participants.
     */
    // no Videobridge// TODO Does SENDRECV always make sense?

    /*
    * If we are the focus of a Videobridge conference, we need to ask the Videobridge
    * to change the stream direction on our behalf.
    */

    /**
     * Whether other party is able to change video quality settings. Normally it's whether we have
     * detected existence of imageattr in sdp.
     *
     * @see MediaFormatImpl.FORMAT_PARAMETER_ATTR_IMAGEATTR
     */
    private var supportQualityControls = false

    /**
     * The `TransportManager` implementation handling our address management.
     */
    private var transportManager: TransportManagerJabberImpl? = null

    /**
     * The `Object` which is used for synchronization (e.g. `wait` and
     * `notify`) related to [.transportManager].
     */
    private val transportManagerSyncRoot = Any()

    /**
     * The ordered by preference array of the XML namespaces of the jingle transports that this
     * peer supports. If it is non-null, it will be used instead of checking disco#info in order
     * to select an appropriate transport manager.
     */
    private var supportedTransports: Array<String?>? = null

    /**
     * Object used to synchronize access to `supportedTransports`
     */
    private val supportedTransportsSyncRoot = Any()

    /**
     * Indicates if the `CallPeer` will support inputevt
     * extension (i.e. will be able to be remote-controlled).
     */
    private var localInputEvtAware = false

    /**
     * Creates a new handler that will be managing media streams for `peer`.
     *
     * peer that `CallPeerJabberImpl` instance that we will be managing media for.
     */
    init {
        qualityControls = QualityControlWrapper(peer)
    }

    /**
     * Determines the direction that a stream, which has been placed on hold by the remote party,
     * would need to go back to after being re-activated. If the stream is not currently on hold
     * (i.e. it is still sending media), this method simply returns its current direction.
     *
     * @param stream the [MediaStreamTarget] whose post-hold direction we'd like to determine.
     * @return the [MediaDirection] that we need to set on `stream` once it is reactivate.
     */
    private fun calculatePostHoldDirection(stream: MediaStream): MediaDirection {
        val streamDirection = stream.direction
        if (streamDirection.allowsSending()) return streamDirection

        /*
         * When calculating a direction we need to take into account 1) what direction the remote
         * party had asked for before putting us on hold, 2) what the user preference is for the
         * stream's media type, 3) our local hold status, 4) the direction supported by the device
         * this stream is reading from.
         */

        // 1. what the remote party originally told us (from our perspective)
        val content = remoteContentMap[stream.name]
        var postHoldDir = JingleUtils.getDirection(content, !peer.isInitiator)

        // 2. the user preference
        val device = stream.device
        postHoldDir = postHoldDir.and(getDirectionUserPreference(device!!.mediaType))

        // 3. our local hold status
        if (isLocallyOnHold) postHoldDir = postHoldDir.and(MediaDirection.SENDONLY)

        // 4. the device direction
        postHoldDir = postHoldDir.and(device.direction)
        return postHoldDir
    }

    /**
     * Closes the `CallPeerMediaHandler`.
     */
    @Synchronized
    override fun close() {
        super.close()
//        OperationSetDesktopSharingClientJabberImpl client = (OperationSetDesktopSharingClientJabberImpl)
//                peer.getProtocolProvider().getOperationSet(OperationSetDesktopSharingClient.class);
//        if (client != null)
//            client.fireRemoteControlRevoked(peer);
    }

    /**
     * Creates a [JingleContent]s of the streams for a specific `MediaDevice`.
     *
     * @param dev `MediaDevice`
     * @return the [JingleContent]s of stream that this handler is prepared to initiate.
     * @throws OperationFailedException if we fail to create the descriptions for reasons like problems with device
     * interaction, allocating ports, etc.
     */
    @Throws(OperationFailedException::class)
    private fun createContent(dev: MediaDevice?): JingleContent? {
        val mediaType = dev!!.mediaType
        // this is the direction to be used in the jingle session
        var direction = dev.direction

        /*
         * In the case of RTP translation performed by the conference focus, the conference
         * focus is not required to capture media.
         */
        if (!(MediaType.VIDEO == mediaType && isRTPTranslationEnabled(mediaType))) direction = direction.and(
            getDirectionUserPreference(mediaType))

        /*
         * Check if we need to announce sending on behalf of other peers
         */
        val call = peer.getCall()
        if (call!!.isConferenceFocus) {
            for (anotherPeer in call.getCallPeerList()) {
                if (anotherPeer != peer && anotherPeer.getDirection(mediaType).allowsReceiving()) {
                    direction = direction.or(MediaDirection.SENDONLY)
                    break
                }
            }
        }
        if (isLocallyOnHold) direction = direction.and(MediaDirection.SENDONLY)
        var sendQualityPreset: QualityPreset? = null
        var receiveQualityPreset: QualityPreset? = null
        if (qualityControls != null) {
            // the one we will send is the one the remote has announced as receive
            sendQualityPreset = qualityControls.getRemoteReceivePreset()
            // the one we want to receive is the one the remote can send
            receiveQualityPreset = qualityControls.getRemoteSendMaxPreset()
        }
        if (direction != MediaDirection.INACTIVE) {
            val content = createContentForOffer(getLocallySupportedFormats(dev, sendQualityPreset,
                receiveQualityPreset), direction, dev.supportedExtensions)
            val description = content.getFirstChildElement(RtpDescription::class.java)

            // DTLS-SRTP
            setDtlsEncryptionOnContent(mediaType, content, null)
            /*
             * Neither SDES nor ZRTP is supported in telephony conferences utilizing the
             * server-side technology Jitsi Videobridge yet.
             */
            if (!peer.isJitsiVideobridge) {
                // SDES - It is important to set SDES before ZRTP in order to make GTALK
                // application able to work with SDES.
                setSdesEncryptionOnDescription(mediaType, description, null)
                // ZRTP
                setZrtpEncryptionOnDescription(mediaType, description, null)
            }
            return content
        }
        return null
    }

    /**
     * Creates a [JingleContent] for a particular stream.
     *
     * @param mediaType `MediaType` of the content
     * @return a [JingleContent]
     * @throws OperationFailedException if we fail to create the descriptions for reasons like
     * - problems with device interaction, allocating ports, etc.
     */
    @Throws(OperationFailedException::class)
    fun createContentForMedia(mediaType: MediaType?): JingleContent? {
        val dev = getDefaultDevice(mediaType)
        return if (isDeviceActive(dev)) createContent(dev) else null
    }

    /**
     * Generates an Jingle [JingleContent] for the specified [MediaFormat]
     * list, direction and RTP extensions taking account the local streaming preference for the
     * corresponding media type.
     *
     * @param supportedFormats the list of `MediaFormats` that we'd like to advertise.
     * @param direction the `MediaDirection` that we'd like to establish the stream in.
     * @param supportedExtensions the list of `RTPExtension`s that we'd like to advertise in the
     * `MediaDescription`.
     * @return a newly created [JingleContent] representing streams that we'd be able to handle.
     */
    private fun createContentForOffer(
            supportedFormats: List<MediaFormat>,
            direction: MediaDirection, supportedExtensions: List<RTPExtension>?,
    ): JingleContent {
        val content = JingleUtils.createDescription(
            JingleContent.Creator.initiator,
            supportedFormats[0].mediaType.toString(),
            JingleUtils.getSenders(direction, !peer.isInitiator),
            supportedFormats, supportedExtensions, dynamicPayloadTypes,
            rtpExtensionsRegistry, getTransportManager().isRtcpmux, isImageattr(peer))
        localContentMap[content.name] = content
        return content
    }

    /**
     * Creates a `List` containing the [JingleContent]s of the streams that
     * this handler is prepared to initiate depending on available `MediaDevice`s and local
     * on-hold and video transmission preferences.
     *
     * @return a [List] containing the [JingleContent]s of streams that this
     * handler is prepared to initiate.
     * @throws OperationFailedException if we fail to create the descriptions for reasons like problems
     * with device interaction, allocating ports, etc.
     */
    @Throws(OperationFailedException::class)
    fun createContentList(): List<JingleContent> {
        // Describe the media.
        val mediaDescs = ArrayList<JingleContent>()
        for (mediaType in MediaType.values()) {
            val dev = getDefaultDevice(mediaType)
            if (isDeviceActive(dev)) {
                var direction = dev!!.direction

                /*
                 * In the case of RTP translation performed by the conference focus, the conference
                 * focus is not required to capture media.
                 */
                if (!(MediaType.VIDEO == mediaType && isRTPTranslationEnabled(mediaType))) {
                    direction = direction.and(getDirectionUserPreference(mediaType))
                }
                if (isLocallyOnHold) direction = direction.and(MediaDirection.SENDONLY)

                /*
                 * If we're only able to receive, we don't have to offer it at all. For example, we
                 * have to offer audio and no video when we start an audio call.
                 */
                if (MediaDirection.RECVONLY == direction) direction = MediaDirection.INACTIVE
                if (direction != MediaDirection.INACTIVE) {
                    val content = createContentForOffer(getLocallySupportedFormats(dev),
                        direction, dev.supportedExtensions)
                    val description = content.getFirstChildElement(RtpDescription::class.java)

                    // DTLS-SRTP
                    setDtlsEncryptionOnContent(mediaType, content, null)
                    /*
                     * Neither SDES nor ZRTP is supported in telephony conferences utilizing the
                     * server-side technology Jitsi Videobridge yet.
                     */
                    if (!peer.isJitsiVideobridge) {
                        // SDES: It is important to set SDES before ZRTP in order to make GTALK
                        // application able to work with
                        setSdesEncryptionOnDescription(mediaType, description, null)
                        // ZRTP
                        setZrtpEncryptionOnDescription(mediaType, description, null)
                    }

                    // we request a desktop sharing session so add the inputevt extension in the "video" content
                    if (description.media == MediaType.VIDEO.toString() && getLocalInputEvtAware()) {
                        content.addChildElement(InputEvent.getBuilder().build())
                    }
                    mediaDescs.add(content)
                }
            }
        }

        // Fail if no media content/description element (e.g. all devices are inactive).
        if (mediaDescs.isEmpty()) {
            ProtocolProviderServiceJabberImpl.throwOperationFailedException(
                aTalkApp.getResString(R.string.service_gui_CALL_NO_ACTIVE_DEVICE),
                OperationFailedException.GENERAL_ERROR, null)
        }

        // Add transport-info to the media contents
        return harvestCandidates(null, mediaDescs, null)
    }

    /**
     * Creates a `List` containing the [JingleContent]s of the streams of a
     * specific `MediaType` that this handler is prepared to initiate depending on available
     * `MediaDevice`s and local on-hold and video transmission preferences.
     *
     * @param mediaType `MediaType` of the content
     * @return a [List] containing the [JingleContent]s of streams that this
     * handler is prepared to initiate.
     * @throws OperationFailedException if we fail to create the descriptions for reasons like - problems with device
     * interaction, allocating ports, etc.
     */
    @Throws(OperationFailedException::class)
    fun createContentList(mediaType: MediaType?): List<JingleContent?>? {
        val dev = getDefaultDevice(mediaType)
        val mediaDescs = ArrayList<JingleContent>()
        if (isDeviceActive(dev)) {
            val content = createContent(dev)
            if (content != null) mediaDescs.add(content)
        }

        // Fail if no media is described (e.g. all devices are inactive).
        if (mediaDescs.isEmpty()) {
            ProtocolProviderServiceJabberImpl.throwOperationFailedException(
                aTalkApp.getResString(R.string.service_gui_CALL_NO_ACTIVE_DEVICE),
                OperationFailedException.GENERAL_ERROR, null)
        }
        // Describe the transport(s).
        return harvestCandidates(null, mediaDescs, null)
    }

    /**
     * Overrides to give access to the transport manager to send events about ICE state changes.
     *
     * @param property the name of the property of this `PropertyChangeNotifier` which had its value changed
     * @param oldValue the value of the property with the specified name before the change
     * @param newValue the value of the property with the specified name after
     */
    public override fun firePropertyChange(property: String?, oldValue: Any?, newValue: Any?) {
        super.firePropertyChange(property, oldValue, newValue)
    }

    /**
     * Wraps up any ongoing candidate harvests and returns our response to the last offer we've
     * received, so that the peer could use it to send a `session-accept`.
     *
     * @return the last generated list of [JingleContent]s that the call peer could
     * use to send a `session-accept`.
     * @throws OperationFailedException if we fail to configure the media stream
     */
    @Throws(OperationFailedException::class)
    fun generateSessionAccept(): Iterable<JingleContent> {
        val transportManager = getTransportManager()
        val sessionAccept = transportManager.wrapupCandidateHarvest()

        // user answered an incoming call, so we go through whatever content entries we are
        // initializing and init their corresponding streams

        // First parse content so we know how many streams and what type of content we have
        val contents = HashMap<JingleContent?, RtpDescription>()
        for (ourContent in sessionAccept) {
            val description = ourContent.getFirstChildElement(RtpDescription::class.java)
            contents[ourContent] = description
        }
        var masterStreamSet = false
        for ((ourContent, description) in contents) {
            val type = MediaType.parseString(description.media)

            // stream connector
            val connector = transportManager.getStreamConnector(type)

            // the device this stream would be reading from and writing to.
            val dev = getDefaultDevice(type)
            if (!isDeviceActive(dev)) continue

            // stream target
            val target = transportManager.getStreamTarget(type)

            // stream direction
            var direction = JingleUtils.getDirection(ourContent, !peer.isInitiator)

            // if we answer with video, tell remotePeer that video direction is sendrecv, and
            // whether video device can capture/send
            if (MediaType.VIDEO == type && (isLocalVideoTransmissionEnabled || isRTPTranslationEnabled(type))
                    && dev!!.direction.allowsSending()) {
                direction = MediaDirection.SENDRECV
                ourContent!!.senders = Senders.both
            }

            // let's now see what was the format we announced as first and configure the stream with it.
            val contentName = ourContent!!.name
            val theirContent = remoteContentMap[contentName]
            val theirDescription = theirContent!!.getFirstChildElement(RtpDescription::class.java)
            var format: MediaFormat? = null
            val localFormats = getLocallySupportedFormats(dev)
            for (payload in theirDescription.getChildElements(PayloadType::class.java)) {
                val remoteFormat = JingleUtils.payloadTypeToMediaFormat(payload, dynamicPayloadTypes)
                if (remoteFormat != null && findMediaFormat(localFormats, remoteFormat).also { format = it } != null) {
                    break
                }
            }
            if (format == null) {
                ProtocolProviderServiceJabberImpl.throwOperationFailedException("No matching codec.",
                    OperationFailedException.ILLEGAL_ARGUMENT, null)
            }

            // extract the extensions that we are advertising: check whether we will be exchanging any RTP extensions.
            val rtpExtensions = JingleUtils.extractRTPExtensions(description, rtpExtensionsRegistry)
            supportQualityControls = format!!.hasParameter(MediaFormatImpl.FORMAT_PARAMETER_ATTR_IMAGEATTR)
            var masterStream = false
            // if we have more than one stream, lets the audio be the master
            if (!masterStreamSet) {
                if (contents.size > 1) {
                    if (type == MediaType.AUDIO) {
                        masterStream = true
                        masterStreamSet = true
                    }
                }
                else {
                    masterStream = true
                    masterStreamSet = true
                }
            }
            // create the corresponding stream...
            val stream = initStream(contentName, connector, dev, format, target, direction, rtpExtensions, masterStream)
            val ourSsrc = stream.getLocalSourceID()
            if (direction.allowsSending() && ourSsrc != -1L) {
                description.ssrc = ourSsrc.toString()
                addSourceExtension(description, ourSsrc)
            }
        }
        return sessionAccept
    }

    /**
     * Adds a `SdpSourceGroup` as a child element of `description`. See XEP-0339.
     *
     * @param description the `RtpDescriptionExtensionElement` to which a child element will be added.
     * @param ssrc the SSRC for the `SdpSourceGroup` to use.
     */
    private fun addSourceExtension(description: RtpDescription, ssrc: Long) {
        val type = MediaType.parseString(description.media)
        val srcBuilder = SdpSource.getBuilder()
                .setSsrc(ssrc)
                .addParameter(ParameterElement.builder(SdpSource.NAMESPACE)
                        .setNameValue("cname", LibJitsi.mediaService!!.rtpCname)
                        .build())
                .addParameter(ParameterElement.builder(SdpSource.NAMESPACE)
                        .setNameValue("msid", getMsid(type))
                        .build())
                .addParameter(ParameterElement.builder(SdpSource.NAMESPACE)
                        .setNameValue("mslabel", msLabel)
                        .build())
                .addParameter(ParameterElement.builder(SdpSource.NAMESPACE)
                        .setNameValue("label", getLabel(type))
                        .build())
        description.addChildElement(srcBuilder.build())
    }

    /**
     * Returns the local content of a specific content type (like audio or video).
     *
     * @param contentType content type name
     * @return remote `JingleContent` or null if not found
     */
    fun getLocalContent(contentType: String): JingleContent? {
        for (key in localContentMap.keys) {
            val content = localContentMap[key]
            if (content != null) {
                val description = content.getFirstChildElement(RtpDescription::class.java)
                if (description.media == contentType) return content
            }
        }
        return null
    }

    /**
     * Returns a complete list of call currently known local content-s.
     *
     * @return a list of [JingleContent] `null` if not found
     */
    fun getLocalContentList(): Iterable<JingleContent?> {
        return localContentMap.values
    }

    /**
     * Returns the quality control for video calls if any.
     *
     * @return the implemented quality control.
     */
    fun getQualityControl(): QualityControl? {
        return if (supportQualityControls) {
            qualityControls
        }
        else {
            // we have detected that its not supported and return null and control ui won't be visible
            null
        }
    }

    /**
     * Get the remote content of a specific content type (like audio or video).
     *
     * @param contentType content type name
     * @return remote `JingleContent` or null if not found
     */
    fun getRemoteContent(contentType: String): JingleContent? {
        for (key in remoteContentMap.keys) {
            val content = remoteContentMap[key]
            val description = content!!.getFirstChildElement(RtpDescription::class.java)
            if (description.media == contentType) return content
        }
        return null
    }

    /**
     * {@inheritDoc}
     *
     * In the case of a telephony conference organized by the local peer/user via the Jitsi
     * Videobridge server-side technology, returns an SSRC reported by the server as received on
     * the channel allocated by the local peer/user for the purposes of communicating with the
     * `CallPeer` associated with this instance.
     */
    override fun getRemoteSSRC(mediaType: MediaType?): Long {
        val ssrcs = getRemoteSSRCs(mediaType)

        /*
         * A peer (regardless of whether it is local or remote) may send multiple RTP streams at
         * any time. In such a case, it is not clear which one of their SSRCs is to be returned.
         * Anyway, the super says that the returned is the last known. We will presume that the
         * last known in the list reported by the Jitsi Videobridge server is the last.
         */
        if (ssrcs.isNotEmpty()) return 0xFFFFFFFFL and ssrcs[ssrcs.size - 1].toLong()

        /*
         * XXX In the case of Jitsi Videobridge, the super implementation of
         * getRemoteSSRC(MediaType) cannot be trusted because there is a single VideoMediaStream
         * with multiple ReceiveStreams.
         */
        return if (peer.isJitsiVideobridge) SSRC_UNKNOWN else super.getRemoteSSRC(mediaType)
    }

    /**
     * Gets the SSRCs of RTP streams with a specific `MediaType` known to be received by a
     * `MediaStream` associated with this instance.
     *
     * **Warning**: The method may return only one of the many possible remote SSRCs in the case
     * of no utilization of the Jitsi Videobridge server-side technology because the super
     * implementation does not currently provide support for keeping track of multiple remote SSRCs.
     *
     * @param mediaType the `MediaType` of the RTP streams the SSRCs of which are to be returned
     * @return an array of `int` values which represent the SSRCs of RTP streams with the
     * specified `mediaType` known to be received by a `MediaStream` associated with this instance
     */
    private fun getRemoteSSRCs(mediaType: MediaType?): IntArray {
        /*
         * If the Jitsi Videobridge server-side technology is utilized, a single MediaStream (per
         * MediaType) is shared among the participating CallPeers and, consequently, the remote
         * SSRCs cannot be associated with the CallPeers from which they are actually being sent.
         * That's why the server will report them to the conference focus.
         */
        val channel = getColibriChannel(mediaType)
        if (channel != null) return channel.ssrCs

        /*
         * XXX The fallback to the super implementation that follows may lead to unexpected
         * behavior due to the lack of ability to keep track of multiple remote SSRCs.
         */
        val ssrc = super.getRemoteSSRC(mediaType)
        return if (ssrc == SSRC_UNKNOWN) ColibriConferenceIQ.NO_SSRCS else intArrayOf(ssrc.toInt())
    }

    /**
     * Get the `TransportManager` implementation handling our address management.
     *
     * TODO: this method can and should be simplified.
     *
     * @return the `TransportManager` implementation handling our address management
     * @see CallPeerMediaHandler.getTransportManager()
     */
    @Synchronized
    public override fun getTransportManager(): TransportManagerJabberImpl {
        if (transportManager == null) {
            if (peer.isInitiator) {
                synchronized(transportManagerSyncRoot) {
                    try {
                        (transportManagerSyncRoot as Object).wait(5000)
                    } catch (e: InterruptedException) {
                        Timber.e("transportManagerSyncRoot Exception: %s", e.message)
                    }
                }
                return if (transportManager == null) {
                    throw IllegalStateException("The initiator is expected to specify the transport in their offer.")
                }
                else transportManager!!
            }
            else {
                val protocolProvider = peer.getProtocolProvider()
                val discoveryManager = protocolProvider.discoveryManager
                val peerDiscoverInfo = peer.discoveryInfo

                /*
                 * If this.supportedTransports has been explicitly set, we use it to select the
                 * transport manager -- we use the first transport in the list which we recognize
                 * (e.g. the first that is either ice or raw-udp
                 */
                synchronized(supportedTransportsSyncRoot) {
                    if (supportedTransports != null && supportedTransports!!.isNotEmpty()) {
                        for (supportedTransport in supportedTransports!!) {
                            if (ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_ICE_UDP_1 == supportedTransport) {
                                transportManager = IceUdpTransportManager(peer)
                                break
                            }
                            else if (ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RAW_UDP_0 == supportedTransport) {
                                transportManager = RawUdpTransportManager(peer)
                                break
                            }
                        }
                        if (transportManager == null) {
                            Timber.w(
                                "Could not find a supported TransportManager in supportedTransports. Will try to select one based on disco#info.")
                        }
                    }
                }
                if (transportManager == null) {
                    /*
                     * The list of possible transports ordered by decreasing preference.
                     */
                    val transports = arrayOf<String?>(
                        ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_ICE_UDP_1,
                        ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RAW_UDP_0)

                    /*
                     * If Jitsi Videobridge is to be employed, pick up a Jingle transport supported by it.
                     */
                    if (peer.isJitsiVideobridge) {
                        val call = peer.getCall()
                        if (call != null) {
                            val jitsiVideobridge = (peer.getCall() as CallJabberImpl).jitsiVideobridge

                            /*
                             * Jitsi Videobridge supports the Jingle Raw UDP transport from its
                             * inception. But that is not the case with the Jingle ICE-UDP transport.
                             */
                            if (jitsiVideobridge != null
                                    && !protocolProvider.isFeatureSupported(jitsiVideobridge,
                                        ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_ICE_UDP_1)) {
                                for (i in transports.indices.reversed()) {
                                    if (ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_ICE_UDP_1 == transports[i]) {
                                        transports[i] = null
                                    }
                                }
                            }
                        }
                    }

                    /*
                     * Select the first transport from the list of possible transports ordered by
                     * decreasing preference which is supported by the local and the remote peers.
                     */
                    for (transport in transports) {
                        if (transport == null) continue
                        if (isFeatureSupported(discoveryManager, peerDiscoverInfo, transport)) {
                            if (ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_ICE_UDP_1 == transport) {
                                transportManager = IceUdpTransportManager(peer)
                            }
                            else if (ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RAW_UDP_0 == transport) {
                                transportManager = RawUdpTransportManager(peer)
                            }
                            if (transportManager != null) break
                        }
                    }
                    if (transportManager == null) {
                        aTalkApp.showToastMessage("No known Jingle transport supported by Jabber call peer $peer")
                    }
                }
            }
        }
        return transportManager!!
    }

    /**
     * {@inheritDoc}
     *
     * @see CallPeerMediaHandler.queryTransportManager
     */
    @Synchronized
    override fun queryTransportManager(): TransportManagerJabberImpl {
        return transportManager!!
    }

    /**
     * {@inheritDoc}
     *
     * In the case of utilization of the Jitsi Videobridge server-side technology, returns the
     * visual `Component` s which display RTP video streams reported by the server to be
     * sent by the remote peer represented by this instance.
     */
    override fun getVisualComponents(): List<Component> {
        /*
         * TODO The super is currently unable to provide the complete set of remote SSRCs (i.e. in
         * the case of no utilization of the Jitsi Videobridge server-side technology) so we
         * have to explicitly check for Jitsi Videobridge instead of just relying on the
         * implementation of the getRemoteSSRCs(MediaType) method to abstract away that detail.
         */
        if (peer.isJitsiVideobridge) {
            val stream = getStream(MediaType.VIDEO)
            return if (stream == null) emptyList()
            else {
                val remoteSSRCs = getRemoteSSRCs(MediaType.VIDEO)
                if (remoteSSRCs.isEmpty()) emptyList()
                else {
                    val videoStream = stream as VideoMediaStream
                    val visualComponents = LinkedList<Component>()
                    for (remoteSSRC in remoteSSRCs) {
                        val visualComponent = videoStream.getVisualComponent(0xFFFFFFFFL and remoteSSRC.toLong())
                        if (visualComponent != null) visualComponents.add(visualComponent)
                    }
                    visualComponents
                }
            }
        }
        return super.getVisualComponents()
    }

    /**
     * Gathers local candidate addresses.
     *
     * @param remote the media descriptions received from the remote peer if any or `null` if
     * `local` represents an offer from the local peer to be sent to the remote peer
     * @param local the media descriptions sent or to be sent from the local peer to the remote peer. If
     * `remote` is `null`, `local` represents an offer from the local
     * peer to be sent to the remote peer
     * @param transportInfoSender the `TransportInfoSender` to be used by this
     * `TransportManagerJabberImpl` to send `transport-info` `Jingle`s
     * from the local peer to the remote peer if this `TransportManagerJabberImpl`
     * wishes to utilize `transport-info`
     * @return the media descriptions of the local peer after the local candidate addresses have
     * been gathered as returned by
     * [TransportManagerJabberImpl.wrapupCandidateHarvest]
     * @throws OperationFailedException if anything goes wrong while starting or wrapping up the gathering
     * of local candidate addresses
     */
    @Throws(OperationFailedException::class)
    private fun harvestCandidates(
            remote: List<JingleContent>?,
            local: MutableList<JingleContent>, transportInfoSender: TransportInfoSender?,
    ): List<JingleContent> {
        val startCandidateHarvestTime = System.currentTimeMillis()

        // Do not proceed if transport is null => NPE
        val transportManager = getTransportManager()
                ?: return emptyList()

        /*
         * aTalk Session-initiate will include the transport-info's in contents,
         * So it doesn't make sense to send them by transportInfoSender.
         */
        if ((remote == null) && (transportInfoSender != null)) {
            throw IllegalArgumentException("transportInfoSender not required in session-initiate offer");
        }

        // Setup TransportManger for (rtcp-mux) per callPeer capability support
        transportManager.isRtcpmux = isRtpcMux(peer)
        transportManager.startCandidateHarvest(remote, local, transportInfoSender)

        val stopCandidateHarvestTime = System.currentTimeMillis()
        val candidateHarvestTime = stopCandidateHarvestTime - startCandidateHarvestTime
        Timber.i("End candidate harvest within %s ms", candidateHarvestTime)
        setDtlsEncryptionOnTransports(remote, local)
        if (transportManager.startConnectivityEstablishmentWithJitsiVideobridge) {
            val mediaTransport = LinkedHashMap<String, IceUdpTransport>()
            for (mediaType in MediaType.values()) {
                val channel = transportManager.getColibriChannel(mediaType, true /* local */)
                if (channel != null) {
                    val transport = channel.transport
                    if (transport != null) mediaTransport[mediaType.toString()] = transport
                }
            }
            if (mediaTransport.isNotEmpty()) {
                transportManager.startConnectivityEstablishmentWithJitsiVideobridge = false
                transportManager.startConnectivityEstablishment(mediaTransport)
            }
        }

        // TODO Ideally, we wouldn't wrap up that quickly. We need to revisit this.
        return transportManager.wrapupCandidateHarvest()
    }

    /**
     * Creates if necessary, and configures the stream that this `MediaHandler` is using for
     * the `MediaType` matching the one of the `MediaDevice`. This method extends the
     * one already available by adding a stream name, corresponding to a stream's content name.
     *
     * @param streamName the name of the stream as indicated in the XMPP `content` element.
     * @param connector the `MediaConnector` that we'd like to bind the newly created stream to.
     * @param device the `MediaDevice` that we'd like to attach the newly created `MediaStream` to.
     * @param format the `MediaFormat` that we'd like the new `MediaStream` to be set to transmit in.
     * @param target the `MediaStreamTarget` containing the RTP and RTCP address:port couples that
     * the new stream would be sending packets to.
     * @param direction the `MediaDirection` that we'd like the new stream to use (i.e. sendonly,
     * sendrecv, recvonly, or inactive).
     * @param rtpExtensions the list of `RTPExtension`s that should be enabled for this stream.
     * @param masterStream whether the stream to be used as master if secured
     * @return the newly created `MediaStream`.
     * @throws OperationFailedException if creating the stream fails for any reason (like for example accessing
     * the device or setting the format).
     */
    @Throws(OperationFailedException::class)
    private fun initStream(
            streamName: String?, connector: StreamConnector?,
            device: MediaDevice, format: MediaFormat?, target: MediaStreamTarget?,
            direction: MediaDirection, rtpExtensions: List<RTPExtension>, masterStream: Boolean,
    ): MediaStream {
        val stream = super.initStream(connector, device, format, target, direction, rtpExtensions, masterStream)
        if (stream != null) stream.name = streamName
        return stream
    }

    /**
     * {@inheritDoc}
     *
     * In the case of a telephony conference organized by the local peer/user and utilizing the
     * Jitsi Videobridge server-side technology, a single `MediaHandler` is shared by
     * multiple `CallPeerMediaHandler`s in order to have a single `AudioMediaStream`
     * and a single `VideoMediaStream`. However, `CallPeerMediaHandlerJabberImpl` has
     * redefined the reading/getting the remote audio and video SSRCs. Consequently,
     * `CallPeerMediaHandlerJabberImpl` has to COMPLETELY redefine the writing/setting as
     * well i.e. it has to stop related `PropertyChangeEvent`s fired by the super.
     */
    override fun mediaHandlerPropertyChange(ev: PropertyChangeEvent) {
        val propertyName = ev.propertyName
        if (AUDIO_REMOTE_SSRC == propertyName || VIDEO_REMOTE_SSRC == propertyName
                && peer.isJitsiVideobridge) return
        super.mediaHandlerPropertyChange(ev)
    }

    /**
     * Handles the specified `answer` by creating and initializing the corresponding `MediaStream`s.
     *
     * @param contentList the Jingle answer
     * @throws OperationFailedException if we fail to handle `answer` for reasons like failing
     * to initialize media devices or streams.
     * @throws IllegalArgumentException if there's a problem with the syntax or the semantics of `answer`.
     * Method is synchronized in order to avoid closing mediaHandler when we are currently in process of initializing,
     * configuring and starting streams and anybody interested in this operation can synchronize to the mediaHandler
     * instance to wait processing to stop (method setState in CallPeer).
     */
    @Throws(OperationFailedException::class, IllegalArgumentException::class)
    fun processSessionAcceptContent(contentList: List<JingleContent>) {
        /*
         * The answer given in session-accept may contain transport-related information compatible
         * with that carried in transport-info.
         */
        processTransportInfo(contentList)
        var masterStreamSet = false
        for (content in contentList) {
            remoteContentMap[content.name] = content
            var masterStream = false
            // if we have more than one stream, let the audio be the master
            if (!masterStreamSet) {
                if (contentList.size > 1) {
                    val description = content.getFirstChildElement(RtpDescription::class.java)
                    if (MediaType.AUDIO.toString() == description.media) {
                        masterStream = true
                        masterStreamSet = true
                    }
                }
                else {
                    masterStream = true
                    masterStreamSet = true
                }
            }
            processContent(content, false, masterStream)
        }
    }

    /**
     * Notifies this instance that a specific `ColibriConferenceIQ` has been received. This
     * `CallPeerMediaHandler` uses the part of the information provided in the specified
     * `conferenceIQ` which concerns it only.
     *
     * @param conferenceIQ the `ColibriConferenceIQ` which has been received
     */
    fun processColibriConferenceIQ(conferenceIQ: ColibriConferenceIQ) {
        /*
         * This CallPeerMediaHandler stores the media information but it does not store the colibri
         * Channels (which contain both media and transport information). The TransportManager
         * associated with this instance stores the colibri Channels but does not store media
         * information (such as the remote SSRCs). An design/implementation choice has to be made
         * though and the present one is to have this CallPeerMediaHandler transparently (with
         * respect to the TransportManager) store the media information inside the
         * TransportManager.
         */
        val transportManager = transportManager
        if (transportManager != null) {
            val oldAudioRemoteSSRC = getRemoteSSRC(MediaType.AUDIO)
            val oldVideoRemoteSSRC = getRemoteSSRC(MediaType.VIDEO)
            for (mediaType in MediaType.values()) {
                val dst = transportManager.getColibriChannel(mediaType, false /* remote */)
                if (dst != null) {
                    val content = conferenceIQ.getContent(mediaType.toString())
                    if (content != null) {
                        val src = content.getChannel(dst.id)
                        if (src != null) {
                            val ssrcs = src.ssrCs
                            val dstSSRCs = dst.ssrCs
                            if (!Arrays.equals(dstSSRCs, ssrcs)) dst.ssrCs = ssrcs
                        }
                    }
                }
            }

            /*
             * Do fire new PropertyChangeEvents for the properties AUDIO_REMOTE_SSRC and
             * VIDEO_REMOTE_SSRC if necessary.
             */
            val newAudioRemoteSSRC = getRemoteSSRC(MediaType.AUDIO)
            val newVideoRemoteSSRC = getRemoteSSRC(MediaType.VIDEO)
            if (oldAudioRemoteSSRC != newAudioRemoteSSRC) {
                firePropertyChange(AUDIO_REMOTE_SSRC, oldAudioRemoteSSRC, newAudioRemoteSSRC)
            }
            if (oldVideoRemoteSSRC != newVideoRemoteSSRC) {
                firePropertyChange(VIDEO_REMOTE_SSRC, oldVideoRemoteSSRC, newVideoRemoteSSRC)
            }
        }
    }

    /**
     * Process a `JingleContent` and initialize its corresponding `MediaStream`.
     * Each Jingle session-accept can contain both audio and video; and will be process individually
     *
     * @param content a `JingleContent`
     * @param modify if it corresponds to a content-modify for resolution change
     * @param masterStream whether the stream to be used as master
     * @throws OperationFailedException if we fail to handle `content` for reasons like failing to
     * initialize media devices or streams.
     * @throws IllegalArgumentException if there's a problem with the syntax or the semantics of `content`.
     * The method is synchronized in order to avoid closing mediaHandler when we are currently in
     * process of initializing, configuring and starting streams and anybody interested in
     * this operation can synchronize to the mediaHandler instance to wait processing to
     * stop (method setState in CallPeer).
     */
    @Throws(OperationFailedException::class, IllegalArgumentException::class)
    private fun processContent(content: JingleContent, modify: Boolean, masterStream: Boolean) {
        val description = content.getFirstChildElement(RtpDescription::class.java)
        val mediaType = JingleUtils.getMediaType(content)

        // if sender has paused the video temporary, then set backToChat flag to avoid checkReplay failure on resume
        val sender = content.senders
        if (Senders.responder == sender) {
            VideoCallActivity.setBackToChat(true)
        }

        // stream targeted transport-info rtp/rtcp
        val transportManager = getTransportManager()
        var target = transportManager.getStreamTarget(mediaType)

        /*
         * If transport and session-accept/content-accept are received one after the other, then must wait for transport
         * processing to be completed before attempt again. Otherwise, getStream(MediaType) will always return nul
         */
        if (target == null) {
            Timber.e("### Waiting transport processing to complete, bind mediaStream is null for: %s", mediaType)
            transportManager.wrapupConnectivityEstablishment()
            target = transportManager.getStreamTarget(mediaType)
        }

        // cmeng - get transport candidate from session-accept may produce null as <transport/> child
        // element can be null if candidates are sent separately. No reliable, fixed with above
        // if (target == null)
        //    target = JingleUtils.extractDefaultTarget(content);
        Timber.d("### Process media content for: sender = %s: %s => %s", sender, mediaType, target)

        // aborted if associated target address is not available: Process transport-info completed
        // ~120ms lead time on Note-10 (aTalk-initiator) send from Note-3 (conversations-responder)
        if (target?.dataAddress == null) {
            closeStream(mediaType)
            return
        }

        // Check to ensure we have the appropriate device to handle the received mediaType
        val dev = getDefaultDevice(mediaType)
        if (!isDeviceActive(dev)) {
            closeStream(mediaType)
            return
        }

        // Take the preference of the user with respect to streaming mediaType into account.
        var devDirection = dev?.direction ?: MediaDirection.INACTIVE
        devDirection = devDirection.and(getDirectionUserPreference(mediaType))
        var supportedFormats = JingleUtils.extractFormats(description, dynamicPayloadTypes)
        if (supportedFormats!!.isEmpty()) {
            // remote party must have messed up our Jingle description. throw an exception.
            ProtocolProviderServiceJabberImpl.throwOperationFailedException(
                "Remote party sent an invalid Jingle Content stanza.",
                OperationFailedException.ILLEGAL_ARGUMENT, null)
        }

        /*
         * Neither SDES nor ZRTP is supported in telephony conferences utilizing the server-side
         * technology Jitsi Videobridge yet.
         */
        val call = peer.getCall()
        val conference = call?.conference
        if (conference == null || !conference.isJitsiVideobridge) {
            addZrtpAdvertisedEncryption(true, description, mediaType)
            addSDesAdvertisedEncryption(true, description, mediaType)
        }
        addDtlsSrtpAdvertisedEncryption(true, content, mediaType, false)

        /*
         * Determine the direction that we need to announce.
         * If we are the focus of a conference, we need to take into account the other participants.
         */
        var remoteDirection = JingleUtils.getDirection(content, peer.isInitiator)
        if (conference != null && conference.isConferenceFocus) {
            for (peer in call.getCallPeerList()) {
                val senders = peer.getSenders(mediaType)
                val initiator = peer.isInitiator
                // check if the direction of the jingle session we have with this peer allows us
                // receiving media. If senders is null, assume the default of 'both'
                if ((Senders.both == senders)
                        || (initiator && Senders.initiator == senders)
                        || (!initiator && Senders.responder == senders)) {
                    remoteDirection = remoteDirection.or(MediaDirection.SENDONLY)
                }
            }
        }
        val direction = devDirection.getDirectionForAnswer(remoteDirection)

        // update the RTP extensions that we will be exchanging.
        val remoteRTPExtensions = JingleUtils.extractRTPExtensions(description, rtpExtensionsRegistry)
        val supportedExtensions = getExtensionsForType(mediaType)
        val rtpExtensions = intersectRTPExtensions(remoteRTPExtensions, supportedExtensions)

        // Media format offer priority is send according in the sequence; use sender first preferred choice
        val offerFormat = supportedFormats[0]
        supportQualityControls = offerFormat.hasParameter(MediaFormatImpl.FORMAT_PARAMETER_ATTR_IMAGEATTR)

        // check for video options from remote party and set them locally
        if (mediaType == MediaType.VIDEO && modify) {
            val stream = getStream(MediaType.VIDEO)
            if (stream != null && dev != null) {
                val fmt = supportedFormats[0]
                (stream as VideoMediaStream).updateQualityControl(fmt.advancedAttributes)
            }
            if (qualityControls != null) {
                val receiveQualityPreset = qualityControls.getRemoteReceivePreset()
                val sendQualityPreset = qualityControls.getRemoteSendMaxPreset()
                supportedFormats = if (dev == null) {
                    null
                }
                else {
                    intersectFormats(supportedFormats, getLocallySupportedFormats(dev, sendQualityPreset, receiveQualityPreset))
                }
            }
        }

        // create the corresponding stream... with the first preferred format matching our capabilities.
        if (supportedFormats != null && supportedFormats.isNotEmpty()) {
            val connector = transportManager.getStreamConnector(mediaType)
            initStream(content.name, connector, dev, supportedFormats[0], target, direction,
                rtpExtensions, masterStream)
        }
        else {
            // remote party must have messed up our Jingle description. throw an exception.
            ProtocolProviderServiceJabberImpl.throwOperationFailedException(
                "No matching media format supported.", OperationFailedException.ILLEGAL_ARGUMENT, null)
        }
    }

    /**
     * Parses and handles the specified `offer` and returns a content extension representing
     * the current state of this media handler. This method MUST only be called when `offer`
     * is the first session description that this `MediaHandler` is seeing.
     *
     * @param offer the offer that we'd like to parse, handle and get an answer for.
     * @throws OperationFailedException if we have a problem satisfying the description received in
     * `offer` (e.g. failed to open a device or initialize a stream ...).
     * @throws IllegalArgumentException if there's a problem with `offer`'s format or semantics.
     */
    @Throws(OperationFailedException::class, IllegalArgumentException::class)
    fun processOffer(offer: List<JingleContent>) {
        // prepare to generate answers to all the incoming descriptions
        val answer = ArrayList<JingleContent>(offer.size)
        var atLeastOneValidDescription = false
        var remoteFormats = mutableListOf<MediaFormat>()
        for (content in offer) {
            content.also { remoteContentMap[content.name] = it }
            val description = content.getFirstChildElement(RtpDescription::class.java)
            val mediaType = JingleUtils.getMediaType(content)
            remoteFormats = JingleUtils.extractFormats(description, dynamicPayloadTypes)
            val dev = getDefaultDevice(mediaType)
            var devDirection = dev?.direction ?: MediaDirection.INACTIVE

            // Take the preference of the user with respect to streaming mediaType into account.
            devDirection = devDirection.and(getDirectionUserPreference(mediaType))

            // determine the direction that we need to announce.
            val remoteDirection = JingleUtils.getDirection(content, peer.isInitiator)
            val direction = devDirection.getDirectionForAnswer(remoteDirection)

            // intersect the MediaFormats of our device with remote ones
            val mutuallySupportedFormats = intersectFormats(remoteFormats, getLocallySupportedFormats(dev))

            // check whether we will be exchanging any RTP extensions.
            val offeredRTPExtensions = JingleUtils.extractRTPExtensions(description,
                rtpExtensionsRegistry)
            val supportedExtensions = getExtensionsForType(mediaType)
            val rtpExtensions = intersectRTPExtensions(offeredRTPExtensions, supportedExtensions)

            /*
             * Transport: RawUdpTransport extends IceUdpTransport so getting IceUdpTransport should suffice.
             */
            val transport = content.getFirstChildElement(IceUdpTransport::class.java)

            // stream target
            var target: MediaStreamTarget? = null
            try {
                target = JingleUtils.extractDefaultTarget(content)
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Fail to extract default target")
            }

            // according to XEP-176, transport element in session-initiate "MAY instead be empty
            // (with each candidate to be sent as the payload of a transport-info message)".
            val targetDataPort = if (target == null && transport != null) -1
            else target?.dataAddress?.port
                    ?: 0

            /*
             * TODO If the offered transport is not supported, attempt to fall back to a supported
             * one using transport-replace.
             */
            setTransportManager(transport!!.namespace)

            // RtcpMux per XEP-0167: Jingle RTP Sessions 1.2.0 (2020-04-22) and patch for jitsi
            var rtcpmux = false
            if (description.getChildElements(RtcpMux::class.java).isNotEmpty()
                    || transport.getChildElements(RtcpMux::class.java).isNotEmpty()) {
                rtcpmux = true
            }
            // getTransportManager().setRtcpmux(rtcpmux);
            rtcpMuxes[peer.getPeerJid()] = rtcpmux
            if (mutuallySupportedFormats.isEmpty() || devDirection === MediaDirection.INACTIVE || targetDataPort == 0) {
                // skip stream and continue. contrary to sip we don't seem to need to send
                // per-stream disabling answer and only one at the end.

                // close the stream in case it already exists
                closeStream(mediaType)
                continue
            }
            val senders = JingleUtils.getSenders(direction, !peer.isInitiator)
            // create the answer description
            val ourContent = JingleUtils.createDescription(content.creator,
                content.name, senders, mutuallySupportedFormats, rtpExtensions,
                dynamicPayloadTypes, rtpExtensionsRegistry, rtcpmux, isImageattr(peer))

            /*
             * Sets ZRTP, SDES or DTLS-SRTP depending on the preferences for this media call.
             */
            setAndAddPreferredEncryptionProtocol(mediaType, ourContent, content, rtcpmux)

            // Got a content which has InputEvent. It means that the peer requests
            // a desktop sharing session so tell it we support InputEvent.
            if (content.getChildElements(InputEvent::class.java) != null) {
                ourContent.addChildElement(InputEvent.getBuilder().build())
            }
            answer.add(ourContent)
            localContentMap[content.name] = ourContent
            atLeastOneValidDescription = true
        }
        if (!atLeastOneValidDescription) {
            // don't just throw exception. Must inform user to take action
            DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_CALL,
                R.string.service_gui_CALL_NO_MATCHING_FORMAT_H, remoteFormats.toString())
            ProtocolProviderServiceJabberImpl.throwOperationFailedException(
                "Offer contained no media formats or no valid media descriptions.",
                OperationFailedException.ILLEGAL_ARGUMENT, null)
        }
        harvestCandidates(offer, answer, object : TransportInfoSender {
            override fun sendTransportInfo(contents: Iterable<JingleContent?>?) {
                try {
                    peer.sendTransportInfo(contents)
                } catch (e: NotConnectedException) {
                    Timber.e(e, "Could not send transport info")
                } catch (e: InterruptedException) {
                    Timber.e(e, "Could not send transport info")
                }
            }
        })

        /*
         * cmeng (20210405): with mux on RTP channel has greatly improved the connection speed.
         * cmeng: newly added (20200112)? need to check if it helps - may not be needed anymore as stated above.
         * In order to minimize post-pickup delay, start establishing the connectivity prior to ringing.
         */
        // harvestCandidates(offer, answer, infoSender);

        /*
         * While it may sound like we can completely eliminate the post-pickup delay by waiting for
         * the connectivity establishment to finish, it may not be possible in all cases. We are
         * the Jingle session responder so, in the case of the ICE UDP transport, we are not the
         * controlling ICE Agent and we cannot be sure when the controlling ICE Agent will perform
         * the nomination. It could, for example, choose to wait for our session-accept to perform
         * the nomination which will deadlock us if we have chosen to wait for the connectivity
         * establishment to finish before we begin ringing and send session-accept.
         */
        getTransportManager().startConnectivityEstablishment(offer)
    }

    /**
     * Processes the transport-related information provided by the remote `peer` in a
     * specific set of `JingleContent`s.
     *
     * @param contents the `JingleContent`s provided by the remote `peer` and
     * containing the transport-related information to be processed
     * @throws OperationFailedException if anything goes wrong while processing the transport-related information
     * provided by the remote `peer` in the specified set of `JingleContent`s
     */
    @Throws(OperationFailedException::class)
    fun processTransportInfo(contents: List<JingleContent>?) {
        transportManager = getTransportManager()
        if (transportManager != null) {
            transportManager!!.startConnectivityEstablishment(contents)
        }
    }

    /**
     * Reinitialize all media contents.
     *
     * @throws OperationFailedException if we fail to handle `content` for reasons like failing
     * to initialize media devices or streams.
     * @throws IllegalArgumentException if there's a problem with the syntax or the semantics of `content`.
     * Method is synchronized in order to avoid closing mediaHandler when we are currently in process
     * of initializing, configuring and starting streams and anybody interested in this
     * operation can synchronize to the mediaHandler instance to wait processing to stop (method setState in CallPeer).
     */
    @Throws(OperationFailedException::class, IllegalArgumentException::class)
    fun reinitAllContents() {
        var masterStreamSet = false
        for (key in remoteContentMap.keys) {
            val content = remoteContentMap[key]
            var masterStream = false
            // if we have more than one stream, lets the audio be the master
            if (!masterStreamSet) {
                val description = content!!.getFirstChildElement(RtpDescription::class.java)
                val mediaType = MediaType.parseString(description.media)
                if (remoteContentMap.size > 1) {
                    if (mediaType == MediaType.AUDIO) {
                        masterStream = true
                        masterStreamSet = true
                    }
                }
                else {
                    masterStream = true
                    masterStreamSet = true
                }
            }
            if (content != null) processContent(content, false, masterStream)
        }
    }

    /**
     * Reinitialize a media content such as video.
     *
     * @param name name of the Jingle content
     * @param content media content
     * @param modify if it corresponds to a content-modify for resolution change
     * @throws OperationFailedException if we fail to handle `content` for reasons like failing to
     * initialize media devices or streams.
     * @throws IllegalArgumentException if there's a problem with the syntax or the semantics of `content`.
     * Method is synchronized in order to avoid closing mediaHandler when we are currently in process of initializing,
     * configuring and starting streams and anybody interested in this operation can synchronize to the mediaHandler
     * instance to wait processing to stop (method setState in CallPeer).
     */
    @Throws(OperationFailedException::class, IllegalArgumentException::class)
    fun reinitContent(name: String?, content: JingleContent, modify: Boolean) {
        val remoteContent = remoteContentMap[name]

        // Timber.w("Reinit Content: " + name + "; remoteContent: " + content + "; modify: " + modify);
        if (remoteContent != null) {
            if (modify) {
                processContent(content, modify, false)
                remoteContentMap[name] = content
            }
            else {
                remoteContent.senders = content.senders
                processContent(remoteContent, modify, false)
                remoteContentMap[name] = remoteContent
            }
        }
    }

    /**
     * Removes a media content with a specific name from the session represented by this
     * `CallPeerMediaHandlerJabberImpl` and closes its associated media stream.
     *
     * @param contentMap the `Map` in which the specified `name` has an association with the
     * media content to be removed
     * @param name the name of the media content to be removed from this session
     */
    private fun removeContent(contentMap: MutableMap<String?, JingleContent?>, name: String) {
        val content = contentMap.remove(name)
        if (content != null) {
            val description = content.getFirstChildElement(RtpDescription::class.java)
            val media = description.media
            if (media != null) closeStream(MediaType.parseString(media))
        }
    }

    /**
     * Removes a media content with a specific name from the session represented by this
     * `CallPeerMediaHandlerJabberImpl` and closes its associated media stream.
     *
     * @param name the name of the media content to be removed from this session
     */
    fun removeContent(name: String) {
        removeContent(localContentMap, name)
        removeContent(remoteContentMap, name)
        val transportManager = queryTransportManager()
        transportManager.removeContent(name)
    }

    /**
     * Acts upon a notification received from the remote party indicating that they've put us on/off hold.
     *
     * onHold `true` if the remote party has put us on hold and `false` if they've
     * just put us off hold.
     */
    /**
     * Indicates whether the remote party has placed us on hold.
     */
    var remotelyOnHold = false
        @Throws(NotConnectedException::class, InterruptedException::class)
        set(onHold) {
            field = onHold
            for (mediaType in MediaType.values()) {
                val stream = getStream(mediaType) ?: continue
                if (peer.isJitsiVideobridge) {
                    /*
                     * If we are the focus of a Videobridge conference, we need to ask the Videobridge
                     * to change the stream direction on our behalf.
                     */
                    val channel = getColibriChannel(mediaType)
                    val direction = if (remotelyOnHold) {
                        MediaDirection.INACTIVE
                    }
                    else {
                        // TODO Does SENDRECV always make sense?
                        MediaDirection.SENDRECV
                    }
                    peer.getCall()!!.setChannelDirection(channel!!.id, mediaType, direction)
                }
                else { // no Videobridge
                    if (remotelyOnHold) {
                        /*
                         * In conferences we use INACTIVE to prevent, for example, on-hold music from
                         * being played to all the participants.
                         */
                        val newDirection = if (peer.getCall()!!.isConferenceFocus) MediaDirection.INACTIVE
                        else stream.direction.and(MediaDirection.RECVONLY)
                        stream.direction = newDirection
                    }
                    else {
                        stream.direction = calculatePostHoldDirection(stream)
                    }
                }
            }
        }

    /**
     * Sometimes as initiating a call with custom preset can set and we force that quality controls is supported.
     *
     * @param value whether quality controls is supported..
     */
    fun setSupportQualityControls(value: Boolean) {
        supportQualityControls = value
    }

    /**
     * Sets the `TransportManager` implementation to handle our address management by Jingle
     * transport XML namespace.
     *
     * @param xmlns the Jingle transport XML namespace specifying the `TransportManager`
     * implementation type to be set on this instance to handle our address management
     * @throws IllegalArgumentException if the specified `xmlns` does not specify a (supported)
     * `TransportManager` implementation type
     */
    @Throws(IllegalArgumentException::class)
    private fun setTransportManager(xmlns: String) {
        // Is this really going to be an actual change?
        if (transportManager != null && transportManager!!.xmlNamespace == xmlns) {
            return
        }
        require(peer.getProtocolProvider().discoveryManager!!.includesFeature(
            xmlns)) { "Unsupported Jingle transport $xmlns" }
        transportManager = when (xmlns) {
            ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_ICE_UDP_1 -> IceUdpTransportManager(peer)
            ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RAW_UDP_0 -> RawUdpTransportManager(peer)
            else -> throw IllegalArgumentException("Unsupported Jingle transport $xmlns")
        }
        synchronized(transportManagerSyncRoot) { transportManagerSyncRoot.notify() }
    }

    /**
     * Waits for the associated `TransportManagerJabberImpl` to conclude any started
     * connectivity establishment and then starts this `CallPeerMediaHandler`.
     *
     * @throws IllegalStateException if no offer or answer has been provided or generated earlier
     */
    @Throws(IllegalStateException::class)
    override fun start() {
        try {
            wrapupConnectivityEstablishment()
        } catch (ofe: OperationFailedException) {
            throw UndeclaredThrowableException(ofe)
        }
        super.start()
    }

    /**
     * Lets the underlying implementation take note of this error and only then throws it to the
     * using bundles.
     *
     * @param message the message to be logged and then wrapped in a new `OperationFailedException`
     * @param errorCode the error code to be assigned to the new `OperationFailedException`
     * @param cause the `Throwable` that has caused the necessity to log an error and have a new
     * `OperationFailedException` thrown
     * @throws OperationFailedException the exception that we wanted this method to throw.
     */
    @Throws(OperationFailedException::class)
    override fun throwOperationFailedException(message: String?, errorCode: Int, cause: Throwable?) {
        ProtocolProviderServiceJabberImpl.throwOperationFailedException(message, errorCode, cause)
    }

    /**
     * Notifies the associated `TransportManagerJabberImpl` that it should conclude any
     * connectivity establishment, waits for it to actually do so and sets the `connector`s
     * and `target`s of the `MediaStream`s managed by this
     * `CallPeerMediaHandler`.
     *
     * @throws OperationFailedException if anything goes wrong while setting the `connector`s and/or `target`s
     * of the `MediaStream`s managed by this `CallPeerMediaHandler`
     */
    @Throws(OperationFailedException::class)
    private fun wrapupConnectivityEstablishment() {
        val transportManager = getTransportManager()
        transportManager.wrapupConnectivityEstablishment()
        for (mediaType in MediaType.values()) {
            val stream = getStream(mediaType)
            if (stream != null) {
                stream.setConnector(transportManager.getStreamConnector(mediaType)!!)
                stream.target = transportManager.getStreamTarget(mediaType)
            }
        }
    }

    /**
     * If Jitsi Videobridge is in use, returns the `ColibriConferenceIQ.Channel` that this
     * `CallPeerMediaHandler` uses for media of type `mediaType`. Otherwise, returns `null`
     *
     * @param mediaType the `MediaType` for which to return a `ColibriConferenceIQ.Channel`
     * @return the `ColibriConferenceIQ.Channel` that this `CallPeerMediaHandler`
     * uses for media of type `mediaType` or `null`.
     */
    private fun getColibriChannel(mediaType: MediaType?): ColibriConferenceIQ.Channel? {
        var channel: ColibriConferenceIQ.Channel? = null
        if (peer.isJitsiVideobridge) {
            val transportManager = transportManager
            if (transportManager != null) {
                channel = transportManager.getColibriChannel(mediaType, false /* remote */)
            }
        }
        return channel
    }

    /**
     * {@inheritDoc}
     *
     * Handles the case when a Videobridge is in use.
     *
     * @param locallyOnHold `true` if we are to make our streams stop transmitting and `false` if we
     * are to start transmitting
     */
    @Throws(OperationFailedException::class)
    override fun setLocallyOnHold(locallyOnHold: Boolean) {
        if (peer.isJitsiVideobridge) {
            this.locallyOnHold = locallyOnHold
            if (locallyOnHold || CallPeerState.ON_HOLD_MUTUALLY != peer.getState()) {
                for (mediaType in MediaType.values()) {
                    val channel = getColibriChannel(mediaType)
                    if (channel != null) {
                        val direction = if (locallyOnHold) MediaDirection.INACTIVE else MediaDirection.SENDRECV
                        try {
                            peer.getCall()!!.setChannelDirection(channel.id, mediaType, direction)
                        } catch (e: NotConnectedException) {
                            throw OperationFailedException("Could not send the channel direction",
                                OperationFailedException.GENERAL_ERROR, e)
                        } catch (e: InterruptedException) {
                            throw OperationFailedException("Could not send the channel direction",
                                OperationFailedException.GENERAL_ERROR, e)
                        }
                    }
                }
            }
        }
        else {
            super.setLocallyOnHold(locallyOnHold)
        }
    }

    /**
     * Detects and adds DTLS-SRTP available encryption method present in the content (description)
     * given in parameter.
     *
     * @param isInitiator `true` if the local call instance is the initiator of the call; `false`,
     * otherwise.
     * @param content The CONTENT element of the JINGLE element which contains the TRANSPORT element
     * @param mediaType The type of media (AUDIO or VIDEO).
     */
    private fun addDtlsSrtpAdvertisedEncryption(
            isInitiator: Boolean,
            content: JingleContent?, mediaType: MediaType?, rtcpmux: Boolean,
    ): Boolean {
        return if (peer.isJitsiVideobridge) {
            false
        }
        else {
            val remoteTransport = content!!.getFirstChildElement(IceUdpTransport::class.java)
            addDtlsSrtpAdvertisedEncryption(isInitiator, remoteTransport, mediaType, rtcpmux)
        }
    }

    /**
     * Detects and adds DTLS-SRTP available encryption method present in the transport (description) given in parameter.
     *
     * @param isInitiator `true` if the local call instance is the initiator of the call; `false`, otherwise.
     * @param remoteTransport the TRANSPORT element
     * @param mediaType The type of media (AUDIO or VIDEO).
     */
    fun addDtlsSrtpAdvertisedEncryption(
            isInitiator: Boolean,
            remoteTransport: IceUdpTransport?, mediaType: MediaType?, rtcpmux: Boolean,
    ): Boolean {
        val srtpControls = srtpControls
        var b = false
        if (remoteTransport != null) {
            val remoteFingerprintPEs = remoteTransport.getChildElements(SrtpFingerprint::class.java)
            if (remoteFingerprintPEs.isNotEmpty()) {
                val accountID = peer.getProtocolProvider().accountID
                if (accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
                        && accountID.isEncryptionProtocolEnabled(SrtpControlType.DTLS_SRTP)) {
                    val remoteFingerprints = LinkedHashMap<String?, String?>()
                    for (remoteFingerprintPE in remoteFingerprintPEs) {
                        val remoteFingerprint = remoteFingerprintPE.fingerprint
                        val remoteHash = remoteFingerprintPE.hash
                        remoteFingerprints[remoteHash] = remoteFingerprint
                    }

                    // TODO Read the setup from the remote DTLS fingerprint elementExtension.
                    val dtlsControl: DtlsControl?
                    val setup: Setup
                    if (isInitiator) {
                        dtlsControl = srtpControls[mediaType!!, SrtpControlType.DTLS_SRTP] as DtlsControl?
                        setup = Setup.ACTPASS
                    }
                    else { // cmeng: must update transport-info with ufrag and pwd
                        val tlsCertSA = accountID.getAccountPropertyString(
                            ProtocolProviderFactory.DTLS_CERT_SIGNATURE_ALGORITHM,
                            DtlsControlImpl.DEFAULT_SIGNATURE_AND_HASH_ALGORITHM)
                        setTlsCertificateSA(tlsCertSA!!)
                        dtlsControl = srtpControls.getOrCreate(mediaType!!, SrtpControlType.DTLS_SRTP,
                            null) as DtlsControl?
                        setup = Setup.ACTIVE
                    }
                    if (dtlsControl != null) {
                        dtlsControl.setRemoteFingerprints(remoteFingerprints)
                        dtlsControl.setup = setup
                        if (rtcpmux) {
                            dtlsControl.setRtcpmux(true)
                        }
                        removeAndCleanupOtherSrtpControls(mediaType, SrtpControlType.DTLS_SRTP)
                        addAdvertisedEncryptionMethod(SrtpControlType.DTLS_SRTP)
                        b = true
                    }
                }
            }
        }
        /*
         * If they haven't advertised DTLS-SRTP in their (media) description, then DTLS-SRTP
         * shouldn't be functioning as far as we're concerned.
         */
        if (!b) {
            val dtlsControl = srtpControls[mediaType!!, SrtpControlType.DTLS_SRTP]
            if (dtlsControl != null) {
                srtpControls.remove(mediaType, SrtpControlType.DTLS_SRTP)
                dtlsControl.cleanup(null)
            }
        }
        return b
    }

    /**
     * Selects the preferred encryption protocol (only used by the callee).
     *
     * @param mediaType The type of media (AUDIO or VIDEO).
     * @param localContent The element containing the media DESCRIPTION and its encryption.
     * @param remoteContent The element containing the media DESCRIPTION and its encryption for the remote peer;
     * `null` if the local peer is the initiator of the call.
     */
    private fun setAndAddPreferredEncryptionProtocol(
            mediaType: MediaType,
            localContent: JingleContent?, remoteContent: JingleContent?, rtcpmux: Boolean,
    ) {
        val preferredEncryptionProtocols = peer.getProtocolProvider().accountID.sortedEnabledEncryptionProtocolList
        for (srtpControlType in preferredEncryptionProtocols) {
            // DTLS-SRTP
            if (srtpControlType === SrtpControlType.DTLS_SRTP) {
                addDtlsSrtpAdvertisedEncryption(false, remoteContent, mediaType, rtcpmux)
                if (setDtlsEncryptionOnContent(mediaType, localContent, remoteContent)) {
                    // Stop once an encryption advertisement has been chosen.
                    return
                }
            }
            else {
                val localDescription = localContent?.getFirstChildElement(RtpDescription::class.java)
                val remoteDescription = remoteContent?.getFirstChildElement(RtpDescription::class.java)
                if (setAndAddPreferredEncryptionProtocol(srtpControlType, mediaType, localDescription,
                            remoteDescription)) {
                    // Stop once an encryption advertisement has been chosen.
                    return
                }
            }
        }
    }

    /**
     * Sets DTLS-SRTP element(s) to the TRANSPORT element of the CONTENT for a given media.
     *
     * @param mediaType The type of media we are modifying the CONTENT to integrate the DTLS-SRTP element(s).
     * @param localContent The element containing the media CONTENT and its TRANSPORT.
     * @param remoteContent The element containing the media CONTENT and its TRANSPORT for the remote peer. Null,
     * if the local peer is the initiator of the call.
     * @return `true` if any DTLS-SRTP element has been added to the specified
     * `localContent`; `false`, otherwise.
     */
    private fun setDtlsEncryptionOnContent(
            mediaType: MediaType?, localContent: JingleContent?,
            remoteContent: JingleContent?,
    ): Boolean {
        var b = false
        if (peer.isJitsiVideobridge) {
            b = setDtlsEncryptionOnTransport(mediaType, localContent, remoteContent)
            return b
        }
        val protocolProvider = peer.getProtocolProvider()
        val accountID = protocolProvider.accountID
        val srtpControls = srtpControls
        if (accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
                && accountID.isEncryptionProtocolEnabled(SrtpControlType.DTLS_SRTP)) {

            // initiator
            val addFingerprintToLocalTransport = when (remoteContent) {
                null -> {
                    protocolProvider.isFeatureSupported(peer.getPeerJid()!!,
                        ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_DTLS_SRTP)
                }
                else -> {
                    addDtlsSrtpAdvertisedEncryption(false, remoteContent, mediaType, false)
                }
            }
            if (addFingerprintToLocalTransport) {
                val tlsCertSA = accountID.getAccountPropertyString(
                    ProtocolProviderFactory.DTLS_CERT_SIGNATURE_ALGORITHM,
                    DtlsControlImpl.DEFAULT_SIGNATURE_AND_HASH_ALGORITHM)
                setTlsCertificateSA(tlsCertSA!!)
                val dtlsControl = srtpControls.getOrCreate(mediaType!!, SrtpControlType.DTLS_SRTP, null) as DtlsControl?
                if (dtlsControl != null) {
                    val setup = if (remoteContent == null) Setup.ACTPASS else Setup.ACTIVE
                    dtlsControl.setup = setup
                    b = true
                    setDtlsEncryptionOnTransport(mediaType, localContent, remoteContent)
                }
            }
        }
        /*
         * If we haven't advertised DTLS-SRTP in our (media) description, then DTLS-SRTP shouldn't
         * be functioning as far as we're concerned.
         */
        if (!b) {
            val dtlsControl = srtpControls[mediaType!!, SrtpControlType.DTLS_SRTP]
            if (dtlsControl != null) {
                srtpControls.remove(mediaType, SrtpControlType.DTLS_SRTP)
                dtlsControl.cleanup(null)
            }
        }
        return b
    }

    /**
     * Sets DTLS-SRTP element(s) to the TRANSPORT element of the CONTENT for a given media.
     *
     * @param mediaType The type of media we are modifying the CONTENT to integrate the DTLS-SRTP element(s).
     * @param localContent The element containing the media CONTENT and its TRANSPORT.
     */
    private fun setDtlsEncryptionOnTransport(
            mediaType: MediaType?,
            localContent: JingleContent?, remoteContent: JingleContent?,
    ): Boolean {
        val localTransport = localContent!!.getFirstChildElement(IceUdpTransport::class.java)
                ?: return false

        var encryption = false
        if (peer.isJitsiVideobridge) {
            val protocolProvider = peer.getProtocolProvider()
            val accountID = protocolProvider.accountID
            if (accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION,
                        true) && accountID.isEncryptionProtocolEnabled(SrtpControlType.DTLS_SRTP)) {
                // Gather the local fingerprints to be sent to the remote peer.
                val channel = getColibriChannel(mediaType)
                var localFingerprints: List<SrtpFingerprint>? = null
                if (channel != null) {
                    val transport = channel.transport
                    if (transport != null) {
                        localFingerprints = transport.getChildElements(SrtpFingerprint::class.java)
                    }
                }
                /*
                 * Determine whether the local fingerprints are to be sent to the remote peer.
                 */
                if (localFingerprints != null && localFingerprints.isNotEmpty()) {
                    if (remoteContent == null) { // initiator
                        if (!protocolProvider.isFeatureSupported(peer.getPeerJid()!!,
                                    ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_DTLS_SRTP)) {
                            localFingerprints = null
                        }
                    }
                    else { // responder
                        val transport = remoteContent.getFirstChildElement(IceUdpTransport::class.java)
                        if (transport == null) {
                            localFingerprints = null
                        }
                        else {
                            val remoteFingerprints = transport.getChildElements(SrtpFingerprint::class.java)
                            if (remoteFingerprints.isEmpty()) localFingerprints = null
                        }
                    }
                    // Send the local fingerprints to the remote peer.
                    if (localFingerprints != null) {
                        val fingerprintPEs = localTransport.getChildElements(SrtpFingerprint::class.java)
                        if (fingerprintPEs.isEmpty()) {
                            for (localFingerprint in localFingerprints) {
                                val srtpFingerPrint = SrtpFingerprint.getBuilder()
                                        .setFingerprint(localFingerprint.fingerprint)
                                        .setHash(localFingerprint.hash)
                                        .setSetup(localFingerprint.setup)
                                        .build()
                                localTransport.addChildElement(srtpFingerPrint)
                            }
                        }
                        encryption = true
                    }
                }
            }
        }
        else {
            val srtpControls = srtpControls
            val dtlsControl = srtpControls[mediaType!!, SrtpControlType.DTLS_SRTP] as DtlsControl?
            if (dtlsControl != null) {
                CallJabberImpl.setDtlsEncryptionOnTransport(dtlsControl, localTransport)
                encryption = true
            }
        }
        return encryption
    }

    /**
     * Sets DTLS-SRTP element(s) to the TRANSPORT element of a specified list of CONTENT elements.
     *
     * @param localContents The elements containing the media CONTENT elements and their respective TRANSPORT elements.
     */
    private fun setDtlsEncryptionOnTransports(
            remoteContents: List<JingleContent>?,
            localContents: List<JingleContent>,
    ) {
        for (localContent in localContents) {
            val description = localContent.getFirstChildElement(RtpDescription::class.java)
            if (description != null) {
                val mediaType = JingleUtils.getMediaType(localContent)
                if (mediaType != null) {
                    val remoteContent = if (remoteContents == null) null
                    else TransportManagerJabberImpl.findContentByName(remoteContents, localContent.name)
                    setDtlsEncryptionOnTransport(mediaType, localContent, remoteContent)
                }
            }
        }
    }

    /**
     * Sets the jingle transports that this `CallPeerMediaHandlerJabberImpl` supports.
     * Unknown transports are ignored, and the `transports` `Collection` is put into
     * order depending on local preference.
     *
     * Currently only ice and raw-udp are recognized, with ice being preferred over raw-udp
     *
     * @param transports A `Collection` of XML namespaces of jingle transport elements to be set as the
     * supported jingle transports for this `CallPeerMediaHandlerJabberImpl`
     */
    fun setSupportedTransports(transports: Collection<String>?) {
        if (transports == null) return
        val ice = ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_ICE_UDP_1
        val rawUdp = ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RAW_UDP_0
        var size = 0
        for (transport in transports) if (ice == transport || rawUdp == transport) size++
        if (size > 0) {
            synchronized(supportedTransportsSyncRoot) {
                supportedTransports = arrayOfNulls(size)
                var i = 0

                // we prefer ice over raw-udp
                if (transports.contains(ice)) {
                    supportedTransports!![i] = ice
                    i++
                }

                if (transports.contains(rawUdp)) {
                    supportedTransports!![i] = rawUdp
                    i++
                }
            }
        }
    }

    /**
     * Gets the `inputevt` support: true for enable, false for disable.
     *
     * @return The state of inputevt support: true for enable, false for disable.
     */
    private fun getLocalInputEvtAware(): Boolean {
        return localInputEvtAware
    }

    /**
     * Enable or disable `inputevt` support (remote-control).
     *
     * @param enable new state of inputevt support
     */
    fun setLocalInputEvtAware(enable: Boolean) {
        localInputEvtAware = enable
    }

    /**
     * Detects and adds ZRTP available encryption method present in the description given in parameter.
     *
     * @param isInitiator True if the local call instance is the initiator of the call. False otherwise.
     * @param description The DESCRIPTION element of the JINGLE element which
     * contains the PAYLOAD-TYPE and (more important here) the ENCRYPTION.
     * @param mediaType The type of media (AUDIO or VIDEO).
     */
    private fun addZrtpAdvertisedEncryption(isInitiator: Boolean, description: RtpDescription?, mediaType: MediaType) {
        // ZRTP is not supported in telephony conferences utilizing the
        // server-side technology Jitsi Videobridge yet.
        if (peer.isJitsiVideobridge) return

        // Conforming to XEP-0167 schema there is 0 or 1 <encryption/> element for a given <description/>>.
        val srtpEncryption = description!!.getFirstChildElement(SrtpEncryption::class.java)
        if (srtpEncryption != null) {
            val accountID = peer.getProtocolProvider().accountID
            val call = peer.getCall()
            if (accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
                    && accountID.isEncryptionProtocolEnabled(SrtpControlType.ZRTP) && call!!.isSipZrtpAttribute) {

                // ZRTP
                val zrtpHash = srtpEncryption.getFirstChildElement(ZrtpHash::class.java)
                if (zrtpHash != null && zrtpHash.hashValue != null) {
                    addAdvertisedEncryptionMethod(SrtpControlType.ZRTP)
                    val zrtpControl = srtpControls[mediaType, SrtpControlType.ZRTP] as ZrtpControl?
                    if (zrtpControl != null) {
                        zrtpControl.setReceivedSignaledZRTPVersion(zrtpHash.version)
                        zrtpControl.setReceivedSignaledZRTPHashValue(zrtpHash.hashValue)
                    }
                }
            }
        }
    }

    /**
     * Detects and adds SDES available encryption method present in the description given in parameter.
     *
     * @param isInitiator True if the local call instance is the initiator of the call. False otherwise.
     * @param description The DESCRIPTION element of the JINGLE element which
     * contains the PAYLOAD-TYPE and (more important here) the ENCRYPTION.
     * @param mediaType The type of media (AUDIO or VIDEO).
     */
    private fun addSDesAdvertisedEncryption(isInitiator: Boolean, description: RtpDescription?, mediaType: MediaType) {
        // SDES is not supported in telephony conferences utilizing the
        // server-side technology Jitsi Videobridge yet.
        if (peer.isJitsiVideobridge) return

        // Conforming to XEP-0167 schema there is 0 or 1 ENCRYPTION element for a given DESCRIPTION.
        val srtpEncryption = description!!.getFirstChildElement(SrtpEncryption::class.java)
        if (srtpEncryption != null) {
            val accountID = peer.getProtocolProvider().accountID

            // SDES
            if (accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
                    && accountID.isEncryptionProtocolEnabled(SrtpControlType.SDES)) {
                val srtpControls = srtpControls
                val sdesControl = srtpControls.getOrCreate(mediaType, SrtpControlType.SDES, null) as SDesControl?
                val selectedSdes = selectSdesCryptoSuite(isInitiator, sdesControl, srtpEncryption)
                if (selectedSdes != null) {
                    //found an SDES answer, remove all other controls
                    removeAndCleanupOtherSrtpControls(mediaType, SrtpControlType.SDES)
                    addAdvertisedEncryptionMethod(SrtpControlType.SDES)
                }
                else {
                    sdesControl!!.cleanup(null)
                    srtpControls.remove(mediaType, SrtpControlType.SDES)
                }
            }
        }
        else if (isInitiator) {
            // SDES
            val sdesControl = srtpControls.remove(mediaType, SrtpControlType.SDES)
            sdesControl?.cleanup(null)
        }
    }

    /**
     * Returns the selected SDES crypto suite selected.
     *
     * @param isInitiator True if the local call instance is the initiator of the call. False otherwise.
     * @param sDesControl The SDES based SRTP MediaStream encryption control.
     * @param srtpEncryption The ENCRYPTION element received from the
     * remote peer. This may contain the SDES crypto suites available for the remote peer.
     * @return The selected SDES crypto suite supported by both the local and
     * the remote peer. Or null, if there is no crypto suite supported by both of the peers.
     */
    private fun selectSdesCryptoSuite(
            isInitiator: Boolean, sDesControl: SDesControl?,
            srtpEncryption: SrtpEncryption,
    ): SrtpCryptoAttribute? {
        val sdpCryptos = srtpEncryption.cryptoList
        val peerAttributes = mutableListOf<SrtpCryptoAttribute>()
        for (cpe in sdpCryptos) peerAttributes.add(SrtpCryptoAttribute.create(cpe.tag, cpe.cryptoSuite,
            cpe.keyParams, cpe.sessionParams))
        return when {
            isInitiator -> sDesControl!!.initiatorSelectAttribute(peerAttributes)
            else -> sDesControl!!.responderSelectAttribute(peerAttributes)
        }
    }

    /**
     * Returns if the remote peer supports ZRTP.
     *
     * @param srtpEncryption The ENCRYPTION element received from
     * the remote peer. This may contain the ZRTP packet element for the remote peer.
     * @return True if the remote peer supports ZRTP. False, otherwise.
     */
    private fun isRemoteZrtpCapable(srtpEncryption: SrtpEncryption): Boolean {
        return srtpEncryption.getChildElements(ZrtpHash::class.java) != null
    }

    /**
     * Sets ZRTP element to the ENCRYPTION element of the DESCRIPTION for a given media.
     *
     * @param mediaType The type of media we are modifying the DESCRIPTION to integrate the ENCRYPTION element.
     * @param description The element containing the media DESCRIPTION and its encryption.
     * @param remoteDescription The element containing the media DESCRIPTION and
     * its encryption for the remote peer. Null, if the local peer is the initiator of the call.
     * @return True if the ZRTP element has been added to encryption. False, otherwise.
     */
    private fun setZrtpEncryptionOnDescription(
            mediaType: MediaType?, description: RtpDescription?,
            remoteDescription: RtpDescription?,
    ): Boolean {
        // ZRTP is not supported in telephony conferences utilizing the server-side technology Jitsi Videobridge yet.
        if (peer.isJitsiVideobridge) return false
        val isRemoteZrtpCapable = if (remoteDescription == null) true
        else {
            // Conforming to XEP-0167 schema there is 0 or 1 ENCRYPTION element for a given DESCRIPTION.
            val remoteSrtpEncryption = remoteDescription.getFirstChildElement(SrtpEncryption::class.java)
            remoteSrtpEncryption != null && isRemoteZrtpCapable(remoteSrtpEncryption)
        }
        var zrtpHashSet = false // Will become true if at least one is set.
        if (isRemoteZrtpCapable) {
            val accountID = peer.getProtocolProvider().accountID
            val call = peer.getCall()
            if (accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
                    && accountID.isEncryptionProtocolEnabled(SrtpControlType.ZRTP)
                    && call!!.isSipZrtpAttribute) {
                val myZid = generateMyZid(accountID, peer.getPeerJid()!!.asBareJid())
                val zrtpControl = srtpControls.getOrCreate(mediaType!!, SrtpControlType.ZRTP, myZid) as ZrtpControl?
                val numberSupportedVersions = zrtpControl!!.numberSupportedVersions

                // Try to get the remote ZRTP version and hash value
                if (remoteDescription != null) {
                    val remoteSrtpEncryption = remoteDescription.getFirstChildElement(SrtpEncryption::class.java)
                    if (remoteSrtpEncryption != null) {
                        val zrtpHash = remoteSrtpEncryption.getFirstChildElement(ZrtpHash::class.java)
                        if (zrtpHash != null && zrtpHash.hashValue != null) {
                            zrtpControl.setReceivedSignaledZRTPVersion(zrtpHash.version)
                            zrtpControl.setReceivedSignaledZRTPHashValue(zrtpHash.hashValue)
                        }
                    }
                }
                for (i in 0 until numberSupportedVersions) {
                    val helloHash = zrtpControl.getHelloHashSep(i)
                    if (helloHash != null && helloHash[1].isNotEmpty()) {
                        val zrtpHash = ZrtpHash.getBuilder()
                                .setVersion(helloHash[0])
                                .setHashValue(helloHash[1])
                                .build()
                        val srtpEncryption = description!!.getFirstChildElement(SrtpEncryption::class.java)
                        if (srtpEncryption == null) {
                            description.addChildElement(SrtpEncryption.getBuilder()
                                    .addChildElement(zrtpHash)
                                    .build())
                        }
                        else {
                            srtpEncryption.addChildElement(zrtpHash)
                        }
                        zrtpHashSet = true
                    }
                }
            }
        }
        return zrtpHashSet
    }

    /**
     * Sets SDES element(s) to the ENCRYPTION element of the DESCRIPTION for a given media.
     *
     * @param mediaType The type of media we are modifying the DESCRIPTION to integrate the ENCRYPTION element.
     * @param localDescription The element containing the media DESCRIPTION and its encryption.
     * @param remoteDescription The element containing the media DESCRIPTION and
     * its encryption for the remote peer. Null, if the local peer is the initiator of the call.
     * @return True if the crypto element has been added to encryption. False, otherwise.
     */
    private fun setSdesEncryptionOnDescription(
            mediaType: MediaType?, localDescription: RtpDescription?,
            remoteDescription: RtpDescription?,
    ): Boolean {
        /*
         * SDES is not supported in telephony conferences utilizing the server-side technology Jitsi Videobridge yet.
         */
        if (peer.isJitsiVideobridge) return false
        val accountID = peer.getProtocolProvider().accountID

        // check if SDES and encryption is enabled at all
        if (accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
                && accountID.isEncryptionProtocolEnabled(SrtpControlType.SDES)) {
            // get or create the control
            val srtpControls = srtpControls
            val sdesControl = srtpControls.getOrCreate(mediaType!!, SrtpControlType.SDES, null) as SDesControl?
            // set the enabled ciphers suites (must remove any unwanted spaces)
            var ciphers = accountID.getAccountPropertyString(ProtocolProviderFactory.SDES_CIPHER_SUITES)
            if (ciphers == null) {
                ciphers = JabberActivator.resources!!.getSettingsString(SDesControl.SDES_CIPHER_SUITES)
            }
            sdesControl!!.setEnabledCiphers(ciphers!!.split(","))

            // act as initiator
            if (remoteDescription == null) {
                val srtpBuilder = SrtpEncryption.getBuilder()
                val localSrtpEncryption = localDescription!!.getFirstChildElement(SrtpEncryption::class.java)
                if (localSrtpEncryption != null) {
                    srtpBuilder.addChildElements(localSrtpEncryption.cryptoList)
                    localDescription.removeChildElement(localSrtpEncryption)
                }
                for (ca in sdesControl.initiatorCryptoAttributes!!) {
                    val crypto = SdpCrypto.getBuilder()
                            .setCrypto(
                                ca.tag, ca.cryptoSuite.encode(),
                                ca.keyParamsString, ca.sessionParamsString)
                            .build()
                    srtpBuilder.addChildElement(crypto)
                }
                localDescription.addChildElement(srtpBuilder.build())
                return true
            }
            else {
                // Conforming to XEP-0167 schema there is 0 or 1 ENCRYPTION element for a given DESCRIPTION.
                val remoteSrtpEncryption = remoteDescription.getFirstChildElement(SrtpEncryption::class.java)
                if (remoteSrtpEncryption != null) {
                    val selectedSdes = selectSdesCryptoSuite(false, sdesControl, remoteSrtpEncryption)
                    if (selectedSdes != null) {
                        val localSrtpEncryption = localDescription!!.getFirstChildElement(SrtpEncryption::class.java)
                        val srtpBuilder = SrtpEncryption.getBuilder()
                        if (localSrtpEncryption != null) {
                            srtpBuilder.addChildElements(localSrtpEncryption.cryptoList)
                            localDescription.removeChildElement(localSrtpEncryption)
                        }
                        val crypto = SdpCrypto.getBuilder()
                                .setCrypto(
                                    selectedSdes.tag, selectedSdes.cryptoSuite.encode(),
                                    selectedSdes.keyParamsString, selectedSdes.sessionParamsString)
                                .build()
                        srtpBuilder.addChildElement(crypto)
                        localDescription.addChildElement(srtpBuilder.build())
                        return true
                    }
                    else {
                        // none of the offered suites match, destroy the sdes control
                        sdesControl.cleanup(null)
                        srtpControls.remove(mediaType, SrtpControlType.SDES)
                        Timber.w("Received unsupported sdes crypto attribute")
                    }
                }
                else {
                    // peer doesn't offer any SDES attribute, destroy the sdes control
                    sdesControl.cleanup(null)
                    srtpControls.remove(mediaType, SrtpControlType.SDES)
                }
            }
        }
        return false
    }

    /**
     * Selects a specific encryption protocol if it is the preferred (only used by the callee).
     *
     * @param mediaType The type of media (AUDIO or VIDEO).
     * @param localDescription The element containing the media DESCRIPTION and its encryption.
     * @param remoteDescription The element containing the media DESCRIPTION and
     * its encryption for the remote peer; `null` if the local peer is the initiator of the call.
     * @return `true` if the specified encryption protocol has been selected; `false`, otherwise
     */
    private fun setAndAddPreferredEncryptionProtocol(
            srtpControlType: SrtpControlType, mediaType: MediaType,
            localDescription: RtpDescription?, remoteDescription: RtpDescription?,
    ): Boolean {
        /*
         * Neither SDES nor ZRTP is supported in telephony conferences utilizing the server-side technology
         * Jitsi Videobridge yet.
         */
        if (peer.isJitsiVideobridge) return false

        // SDES
        if (srtpControlType === SrtpControlType.SDES) {
            addSDesAdvertisedEncryption(false, remoteDescription, mediaType)
            if (setSdesEncryptionOnDescription(mediaType, localDescription, remoteDescription)) {
                // Stop once an encryption advertisement has been chosen.
                return true
            }
        }
        else if (srtpControlType === SrtpControlType.ZRTP) {
            if (setZrtpEncryptionOnDescription(mediaType, localDescription, remoteDescription)) {
                addZrtpAdvertisedEncryption(false, remoteDescription, mediaType)
                // Stop once an encryption advertisement has been chosen.
                return true
            }
        }
        return false
    }

    companion object {
        /**
         * Determines whether a specific XMPP feature is supported by both a specific
         * `ScServiceDiscoveryManager` (may be referred to as the local peer) and a specific
         * `DiscoverInfo` (may be thought of as the remote peer).
         *
         * @param discoveryManager the `ScServiceDiscoveryManager` to be checked whether it includes
         * the specified feature
         * @param discoverInfo the `DiscoveryInfo` which is to be checked whether it contains the specified
         * feature. If `discoverInfo` is `null`, it is considered to contain the specified feature.
         * @param feature the feature to be determined whether it is supported by both the specified
         * `discoveryManager` and the specified `discoverInfo`
         * @return `true` if the specified `feature` is supported by both the specified
         * `discoveryManager` and the specified `discoverInfo`; otherwise, `false`
         */
        private fun isFeatureSupported(
                discoveryManager: ScServiceDiscoveryManager?,
                discoverInfo: DiscoverInfo?, feature: String,
        ): Boolean {
            return (discoveryManager!!.includesFeature(feature)
                    && (discoverInfo === null || discoverInfo.containsFeature(feature)))
        }
    }
}