/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.protocol.event.CallChangeEvent
import net.java.sip.communicator.service.protocol.event.CallEvent
import net.java.sip.communicator.service.protocol.event.DTMFReceivedEvent
import net.java.sip.communicator.service.protocol.media.*
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.impl.neomedia.transform.dtls.DtlsControlImpl
import org.atalk.impl.neomedia.transform.dtls.DtlsControlImpl.Companion.setTlsCertificateSA
import org.atalk.service.neomedia.DtlsControl
import org.atalk.service.neomedia.DtlsControl.Setup
import org.atalk.service.neomedia.MediaDirection
import org.atalk.service.neomedia.SrtpControlType
import org.atalk.service.neomedia.StreamConnectorFactory
import org.atalk.util.MediaType
import org.jivesoftware.smack.SmackException.NoResponseException
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smack.packet.ExtensionElement
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smackx.coin.CoinExtension
import org.jivesoftware.smackx.colibri.ColibriConferenceIQ
import org.jivesoftware.smackx.disco.packet.DiscoverInfo
import org.jivesoftware.smackx.jingle.element.Jingle
import org.jivesoftware.smackx.jingle.element.JingleContent
import org.jivesoftware.smackx.jingle.element.JingleReason
import org.jivesoftware.smackx.jingle_rtp.JingleCallSessionImpl
import org.jivesoftware.smackx.jingle_rtp.JingleUtils
import org.jivesoftware.smackx.jingle_rtp.element.*
import org.jxmpp.jid.FullJid
import org.jxmpp.jid.Jid
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.*

/**
 * A Jabber implementation of the `Call` abstract class encapsulating Jabber jingle sessions.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 * @author MilanKral
 */
class CallJabberImpl(parentOpSet: OperationSetBasicTelephonyJabberImpl, sid: String)
    : MediaAwareCall<CallPeerJabberImpl, OperationSetBasicTelephonyJabberImpl, ProtocolProviderServiceJabberImpl>(parentOpSet, sid) {
    /**
     * The Jitsi Videobridge conference which the local peer represented by this instance is a focus of.
     */
    private var colibri: ColibriConferenceIQ? = null

    /**
     * The shared `CallPeerMediaHandler` state which is to be used by the `CallPeer`s
     * of this `Call` which use [.colibri].
     */
    private var colibriMediaHandler: MediaHandler? = null

    /**
     * Contains one ColibriStreamConnector for each `MediaType`
     */
    private val colibriStreamConnectors: MutableList<WeakReference<ColibriStreamConnector>?>

    /**
     * Returns if the call support `inputevt` (remote control).
     *
     * @return true if the call support `inputevt`, false otherwise
     */
    /**
     * Enable or disable `inputevt` support (remote control).
     *
     * enable new state of inputevt support
     */
    /**
     * Indicates if the `CallPeer` will support `inputevt`
     * extension (i.e. will be able to be remote-controlled).
     */
    var localInputEvtAware = false

    /**
     * Initializes a new `CallJabberImpl` instance.
     *
     * parentOpSet the OperationSetBasicTelephonyJabberImpl instance in the context
     * of which this call has been created.
     * sid the Jingle session-initiate id if provided.
     */
    init {
        val mediaTypeValueCount = MediaType.values().size
        colibriStreamConnectors = ArrayList(mediaTypeValueCount)
        for (i in 0 until mediaTypeValueCount) colibriStreamConnectors.add(null)

        // let's add ourselves to the calls repo. we are doing it ourselves just to make sure that
        // no one ever forgets.
        parentOpSet.activeCallsRepository.addCall(this)
    }

    /**
     * Closes a specific `ColibriStreamConnector` which is associated with a
     * `MediaStream` of a specific `MediaType` upon request from a specific `CallPeer`.
     *
     * peer the `CallPeer` which requests the closing of the specified `colibriStreamConnector`
     * mediaType the `MediaType` of the `MediaStream` with which the specified
     * colibriStreamConnector` is associated
     * colibriStreamConnector the `ColibriStreamConnector` to close on behalf of the specified `peer`
     */
    fun closeColibriStreamConnector(peer: CallPeerJabberImpl?, mediaType: MediaType,
            colibriStreamConnector: ColibriStreamConnector) {
        colibriStreamConnector.close()
        synchronized(colibriStreamConnectors) {
            val index = mediaType.ordinal
            val weakReference = colibriStreamConnectors[index]
            if (weakReference != null && colibriStreamConnector == weakReference.get()) {
                colibriStreamConnectors[index] = null
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * Sends a `content` message to each of the `CallPeer`s associated with this
     * `CallJabberImpl` in order to include/exclude the &quot;isfocus&quot; attribute.
     */
    override fun conferenceFocusChanged(oldValue: Boolean, newValue: Boolean) {
        try {
            val peers = getCallPeers()
            while (peers.hasNext()) {
                val callPeer = peers.next()
                if (callPeer.getState() == CallPeerState.CONNECTED) callPeer.sendCoinSessionInfo()
            }
        } catch (e: NotConnectedException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } finally {
            super.conferenceFocusChanged(oldValue, newValue)
        }
    }

    /**
     * Allocates colibri (conference) channels for a specific `MediaType` to be used by a
     * specific `CallPeer`.
     *
     * peer the `CallPeer` which is to use the allocated colibri (conference) channels
     * contentMap the local and remote `JingleContent`s which specify the
     * `MediaType`s for which colibri (conference) channels are to be allocated
     * @return a `ColibriConferenceIQ` which describes the allocated colibri (conference)
     * channels for the specified `mediaTypes` which are to be used by the specified
     * `peer`; otherwise, `null`
     */
    @Throws(OperationFailedException::class)
    fun createColibriChannels(peer: CallPeerJabberImpl,
            contentMap: Map<JingleContent, JingleContent?>): ColibriConferenceIQ? {
        if (!peer.isJitsiVideobridge) return null

        /*
         * For a colibri conference to work properly, all CallPeers in the conference must share
         * one and the same CallPeerMediaHandler state i.e. they must use a single set of
         * MediaStreams as if there was a single CallPeerMediaHandler.
         */
        val peerMediaHandler = peer.mediaHandler
        if (peerMediaHandler.mediaHandler != colibriMediaHandler) {
            for (mediaType in MediaType.values()) {
                if (peerMediaHandler.getStream(mediaType) != null) return null
            }
        }
        // val protocolProvider = getProtocolProvider()
        val jvb = if (colibri == null) jitsiVideobridge else colibri!!.from
        if (jvb == null || jvb.isEmpty()) {
            Timber.e("Failed to allocate colibri channels: no videobridge found.")
            return null
        }

        /*
         * The specified CallPeer will participate in the colibri conference organized by this Call so it
         * must use the shared CallPeerMediaHandler state of all CallPeers in the same colibri conference.
         */
        if (colibriMediaHandler == null) colibriMediaHandler = MediaHandler()
        peerMediaHandler.mediaHandler = colibriMediaHandler
        val conferenceRequest = ColibriConferenceIQ()
        if (colibri != null) conferenceRequest.id = colibri!!.id
        for ((localContent, remoteContent) in contentMap) {
            val cpe = remoteContent ?: localContent
            val rdpe = cpe.getFirstChildElement(RtpDescription::class.java)
            val media = rdpe.media
            val mediaType = MediaType.parseString(media)
            val contentName = mediaType.toString()
            val contentRequest = ColibriConferenceIQ.Content(contentName)
            conferenceRequest.addContent(contentRequest)
            var requestLocalChannel = true
            if (colibri != null) {
                val content = colibri!!.getContent(contentName)
                if (content != null && content.channelCount > 0) requestLocalChannel = false
            }
            val peerIsInitiator = peer.isInitiator
            if (requestLocalChannel) {
                val localChannelRequest = ColibriConferenceIQ.Channel()
                localChannelRequest.endpoint = sourceProvider.ourJID.toString()
                localChannelRequest.isInitiator = peerIsInitiator
                for (ptpe in rdpe.getChildElements(PayloadType::class.java)) localChannelRequest.addPayloadType(ptpe)
                setTransportOnChannel(peer, media, localChannelRequest)
                // DTLS-SRTP
                setDtlsEncryptionOnChannel(jitsiVideobridge, peer, mediaType, localChannelRequest)
                /*
                 * Since Jitsi Videobridge supports multiple Jingle transports, it is a good
                 * idea to indicate which one is expected on a channel.
                 */
                ensureTransportOnChannel(localChannelRequest, peer)
                contentRequest.addChannel(localChannelRequest)
            }
            val remoteChannelRequest = ColibriConferenceIQ.Channel()
            remoteChannelRequest.endpoint = peer.getAddress()
            remoteChannelRequest.isInitiator = !peerIsInitiator
            for (ptpe in rdpe.getChildElements(PayloadType::class.java)) remoteChannelRequest.addPayloadType(ptpe)
            setTransportOnChannel(media, localContent, remoteContent, remoteChannelRequest)
            // DTLS-SRTP
            setDtlsEncryptionOnChannel(mediaType, localContent, remoteContent, peer, remoteChannelRequest)
            /*
             * Since Jitsi Videobridge supports multiple Jingle transports, it is a good idea to
             * indicate which one is expected on a channel.
             */
            ensureTransportOnChannel(remoteChannelRequest, peer)
            contentRequest.addChannel(remoteChannelRequest)
        }
        conferenceRequest.to = jitsiVideobridge
        conferenceRequest.type = IQ.Type.get
        val connection = sourceProvider.connection
        val response: Stanza = try {
            val stanzaCollector = connection!!.createStanzaCollectorAndSend(conferenceRequest)
            try {
                stanzaCollector.nextResultOrThrow()
            } finally {
                stanzaCollector.cancel()
            }
        } catch (e1: NotConnectedException) {
            throw OperationFailedException("Could not send the conference request",
                    OperationFailedException.REGISTRATION_REQUIRED, e1)
        } catch (e1: InterruptedException) {
            throw OperationFailedException("Could not send the conference request",
                    OperationFailedException.REGISTRATION_REQUIRED, e1)
        } catch (e: XMPPErrorException) {
            Timber.e("Failed to allocate colibri channel: %s", e.message)
            return null
        } catch (e: NoResponseException) {
            Timber.e("Failed to allocate colibri channels: %s", e.message)
            return null
        }

        val conferenceResponse = response as ColibriConferenceIQ
        val conferenceResponseID = conferenceResponse.id

        /*
         * Update the complete ColibriConferenceIQ representation maintained by this instance with
         * the information given by the (current) response.
         */
        if (colibri == null) {
            colibri = ColibriConferenceIQ()
            /*
             * XXX We must remember the JID of the Jitsi Videobridge because (1) we do not want to
             * re-discover it in every method invocation on this Call instance and (2) we want to
             * use one and the same for all CallPeers within this Call instance.
             */
            colibri!!.from = conferenceResponse.from
        }
        val colibriID = colibri!!.id
        if (colibriID == null) colibri!!.id = conferenceResponseID else check(colibriID == conferenceResponseID) { "conference.id" }
        for (contentResponse in conferenceResponse.contents) {
            val contentName = contentResponse.name
            val content = colibri!!.getOrCreateContent(contentName)
            for (channelResponse in contentResponse.channels) {
                val channelIndex = content.channelCount
                content.addChannel(channelResponse)
                if (channelIndex == 0) {
                    val transportManager = peerMediaHandler.transportManager
                    transportManager.isEstablishingConnectivityWithJitsiVideobridge = true
                    transportManager.startConnectivityEstablishmentWithJitsiVideobridge = true
                    val mediaType = MediaType.parseString(contentName)

                    // DTLS-SRTP
                    addDtlsAdvertisedEncryptions(peer, channelResponse, mediaType)
                }
            }
        }

        /*
         * Formulate the result to be returned to the caller which is a subset of the whole
         * conference information kept by this CallJabberImpl and includes the remote channels
         * explicitly requested by the method caller and their respective local channels.
         */
        val conferenceResult = ColibriConferenceIQ()
        conferenceResult.from = colibri!!.from
        conferenceResult.id = conferenceResponseID
        for ((localContent, remoteContent) in contentMap) {
            val cpe = remoteContent ?: localContent
            val mediaType = JingleUtils.getMediaType(cpe)
            val contentResponse = conferenceResponse.getContent(mediaType.toString())
            if (contentResponse != null) {
                val contentName = contentResponse.name
                val contentResult = ColibriConferenceIQ.Content(contentName)
                conferenceResult.addContent(contentResult)

                /*
                 * The local channel may have been allocated in a previous method call as part of
                 * the allocation of the first remote channel in the respective content. Anyway,
                 * the current method caller still needs to know about it.
                 */
                val content = colibri!!.getContent(contentName)
                var localChannel: ColibriConferenceIQ.Channel? = null
                if (content != null && content.channelCount > 0) {
                    localChannel = content.getChannel(0)
                    contentResult.addChannel(localChannel)
                }
                val localChannelID = localChannel?.id
                for (channelResponse in contentResponse.channels) {
                    if (localChannelID == null || localChannelID != channelResponse.id) contentResult.addChannel(channelResponse)
                }
            }
        }
        return conferenceResult
    }

    /**
     * Initializes a `ColibriStreamConnector` on behalf of a specific `CallPeer` to be used
     * in association with a specific `ColibriConferenceIQ.Channel` of a specific `MediaType`.
     *
     * peer the `CallPeer` which requests the initialization of a `ColibriStreamConnector`
     * mediaType the `MediaType` of the stream which is to use the initialized
     * `ColibriStreamConnector` for RTP and RTCP traffic
     * channel the `ColibriConferenceIQ.Channel` to which RTP and RTCP traffic is to be sent
     * and from which such traffic is to be received via the initialized `ColibriStreamConnector`
     * factory a `StreamConnectorFactory` implementation which is to allocate the sockets to
     * be used for RTP and RTCP traffic
     * @return a `ColibriStreamConnector` to be used for RTP and RTCP traffic associated
     * with the specified `channel`
     */
    fun createColibriStreamConnector(peer: CallPeerJabberImpl?,
            mediaType: MediaType, channel_: ColibriConferenceIQ.Channel, factory: StreamConnectorFactory): ColibriStreamConnector? {
        var channel = channel_
        val channelID = channel.id ?: throw IllegalArgumentException("channel")
        checkNotNull(colibri) { "colibri" }
        val content = colibri!!.getContent(mediaType.toString())
                ?: throw IllegalArgumentException("mediaType")
        require(!(content.channelCount < 1
                || channelID != content.getChannel(0).also { channel = it }.id)) { "channel" }
        var colibriStreamConnector: ColibriStreamConnector?
        synchronized(colibriStreamConnectors) {
            val index = mediaType.ordinal
            val weakReference = colibriStreamConnectors[index]
            colibriStreamConnector = weakReference?.get()
            if (colibriStreamConnector == null) {
                val streamConnector = factory.createStreamConnector()
                if (streamConnector != null) {
                    colibriStreamConnector = ColibriStreamConnector(streamConnector)
                    colibriStreamConnectors[index] = WeakReference(colibriStreamConnector)
                }
            }
        }
        return colibriStreamConnector
    }

    /**
     * Expires specific (colibri) conference channels used by a specific `CallPeer`.
     *
     * peer the `CallPeer` which uses the specified (colibri) conference channels to be expired
     * conference a `ColibriConferenceIQ` which specifies the (colibri) conference channels to be expired
     */
    @Throws(NotConnectedException::class, InterruptedException::class)
    fun expireColibriChannels(peer: CallPeerJabberImpl?, conference: ColibriConferenceIQ) {
        // Formulate the ColibriConferenceIQ request which is to be sent.
        if (colibri != null) {
            val conferenceID = colibri!!.id
            if (conferenceID == conference.id) {
                val conferenceRequest = ColibriConferenceIQ()
                conferenceRequest.id = conferenceID
                for (content in conference.contents) {
                    val colibriContent = colibri!!.getContent(content.name)
                    if (colibriContent != null) {
                        val contentRequest = conferenceRequest.getOrCreateContent(colibriContent.name)
                        for (channel in content.channels) {
                            val colibriChannel = colibriContent.getChannel(channel.id)
                            if (colibriChannel != null) {
                                val channelRequest = ColibriConferenceIQ.Channel()
                                channelRequest.expire = 0
                                channelRequest.id = colibriChannel.id
                                contentRequest.addChannel(channelRequest)
                            }
                        }
                    }
                }

                /*
                 * Remove the channels which are to be expired from the internal state of the
                 * conference managed by this CallJabberImpl.
                 */
                for (contentRequest in conferenceRequest.contents) {
                    val colibriContent = colibri!!.getContent(contentRequest.name)
                    for (channelRequest_ in contentRequest.channels) {
                        var channelRequest = channelRequest_
                        var colibriChannel = colibriContent.getChannel(channelRequest.id)
                        colibriContent.removeChannel(colibriChannel)

                        /*
                         * If the last remote channel is to be expired, expire the local channel as well.
                         */
                        if (colibriContent.channelCount == 1) {
                            colibriChannel = colibriContent.getChannel(0)
                            channelRequest = ColibriConferenceIQ.Channel()
                            channelRequest.expire = 0
                            channelRequest.id = colibriChannel.id
                            contentRequest.addChannel(channelRequest)
                            colibriContent.removeChannel(colibriChannel)
                            break
                        }
                    }
                }

                /*
                 * At long last, send the ColibriConferenceIQ request to expire the channels.
                 */
                conferenceRequest.to = colibri!!.from
                conferenceRequest.type = IQ.Type.set
                sourceProvider.connection!!.sendStanza(conferenceRequest)
            }
        }
    }

    /**
     * Sends a `ColibriConferenceIQ` to the videobridge used by this `CallJabberImpl`,
     * in order to request the the direction of the `channel` with ID `channelID` be
     * set to `direction`
     *
     * channelID the ID of the `channel` for which to set the direction.
     * mediaType the `MediaType` of the channel (we can deduce this by searching the
     * `ColibriConferenceIQ`, but it's more convenient to have it)
     * direction the `MediaDirection` to set.
     */
    @Throws(NotConnectedException::class, InterruptedException::class)
    fun setChannelDirection(channelID: String?, mediaType: MediaType, direction: MediaDirection?) {
        if (colibri != null && channelID != null) {
            val content = colibri!!.getContent(mediaType.toString())
            if (content != null) {
                val channel = content.getChannel(channelID)

                /*
                 * Note that we send requests even when the local Channel's direction and the
                 * direction we are setting are the same. We can easily avoid this, but we risk not
                 * sending necessary packets if local Channel and the actual channel on the
                 * videobridge are out of sync.
                 */
                if (channel != null) {
                    val requestChannel = ColibriConferenceIQ.Channel()
                    requestChannel.id = channelID
                    requestChannel.direction = direction
                    val requestContent = ColibriConferenceIQ.Content()
                    requestContent.name = mediaType.toString()
                    requestContent.addChannel(requestChannel)
                    val conferenceRequest = ColibriConferenceIQ()
                    conferenceRequest.id = colibri!!.id
                    conferenceRequest.to = colibri!!.from
                    conferenceRequest.type = IQ.Type.set
                    conferenceRequest.addContent(requestContent)
                    sourceProvider.connection!!.sendStanza(conferenceRequest)
                }
            }
        }
    }

    /**
     * Creates a `CallPeerJabberImpl` from `calleeJID` and sends them `session-initiate` IQ request.
     *
     * calleeJid the party that we would like to invite to this call.
     * discoverInfo any discovery information that we have for the jid we are trying to reach and
     * that we are passing in order to avoid having to ask for it again.
     * sessionInitiateExtensions a collection of additional and optional `ExtensionElement`s to be
     * added to the `session-initiate` [Jingle] which is to init this `CallJabberImpl`
     * supportedTransports the XML namespaces of the jingle transports to use.
     * @return the newly created `CallPeerJabberImpl` corresponding to `calleeJID`.
     * All following state change events will be delivered through this call peer.
     * @throws OperationFailedException with the corresponding code if we fail to create the call.
     */
    @Throws(OperationFailedException::class)
    fun initiateSession(calleeJid: FullJid, discoverInfo: DiscoverInfo?,
            sessionInitiateExtensions: Iterable<ExtensionElement?>?, supportedTransports: Collection<String>?): CallPeerJabberImpl {
        // create the session-initiate IQ
        val callPeer = CallPeerJabberImpl(calleeJid, this)
        callPeer.discoveryInfo = discoverInfo
        addCallPeer(callPeer)
        callPeer.setState(CallPeerState.INITIATING_CALL)

        // If this is the first peer we added in this call, then the call is new;
        // then we need to notify everyone of its creation.
        if (callPeerCount == 1)
                parentOperationSet.fireCallEvent(CallEvent.CALL_INITIATED, this)

        // set the supported transports before the transport manager is being created
        val mediaHandler = callPeer.mediaHandler
        mediaHandler.setSupportedTransports(supportedTransports)

        /* enable video if it is a video call */
        mediaHandler.isLocalVideoTransmissionEnabled = localVideoAllowed

        /* enable remote-control if it is a desktop sharing session - cmeng: get and set back???*/
        //  mMediaHandler.setLocalInputEvtAware(mMediaHandler.getLocalInputEvtAware());

        /*
         * Set call state to connecting so that the user interface would start playing the tones.
         * We do that here because we may be harvesting STUN/TURN addresses in initiateSession()
         * which would take a while.
         */
        callPeer.setState(CallPeerState.CONNECTING)

        // if initializing session fails, set peer to failed by default
        var sessionInitiated = false
        try {
            callPeer.initiateSession(sessionInitiateExtensions, callId)
            sessionInitiated =true
        } finally {
            // if initialization throws an exception
            if (!sessionInitiated) callPeer.setState(CallPeerState.FAILED)
        }
        return callPeer
    }

    /**
     * Updates the Jingle sessions for the `CallPeer`s of this `Call`, to reflect the
     * current state of the video contents of this `Call`. Sends a `content-modify`,
     * `content-add` or `content-remove` message to each of the current `CallPeer`s.
     *
     * cmeng (20210321): Approach aborted due to complexity and NewReceiveStreamEvent not alway gets triggered:
     * - content-remove/content-add are not used on device orientation changed - use blocking impl is aborted due
     * to its complexity.
     * - @see CallPeerJabberImpl#getDirectionForJingle(MediaType)
     *
     * @throws OperationFailedException if a problem occurred during message generation or there was a network problem
     */
    @Throws(OperationFailedException::class)
    fun modifyVideoContent() {
        Timber.d("Updating video content for %s", this)
        var change = false
        for (peer in getCallPeerList()) {
            try {
                // cmeng (2016/09/14): Never send 'sendModifyVideoContent' before it is connected => Smack Exception
                if (peer.getState() == CallPeerState.CONNECTED) change = change or peer.sendModifyVideoContent()
            } catch (e: NotConnectedException) {
                throw OperationFailedException("Could send modify video content to " + peer.getAddress(), 0, e)
            } catch (e: InterruptedException) {
                throw OperationFailedException("Could send modify video content to " + peer.getAddress(), 0, e)
            }
        }
        if (change) fireCallChangeEvent(CallChangeEvent.CALL_PARTICIPANTS_CHANGE, null, null)
    }

    /**
     * Notifies this instance that a specific `ColibriConferenceIQ` has been received.
     *
     * conferenceIQ the `ColibriConferenceIQ` which has been received
     * @return `true` if the specified `conferenceIQ` was processed by this instance
     * and no further processing is to be performed by other possible processors of
     * `ColibriConferenceIQ`s; otherwise, `false`. Because a
     * `ColibriConferenceIQ` request sent from the Jitsi Videobridge server to the
     * application as its client concerns a specific `CallJabberImpl` implementation,
     * no further processing by other `CallJabberImpl` instances is necessary once
     * the `ColibriConferenceIQ` is processed by the associated `CallJabberImpl` instance.
     */
    fun processColibriConferenceIQ(conferenceIQ: ColibriConferenceIQ): Boolean {
        return if (colibri == null) {
            /*
             * This instance has not set up any conference using the Jitsi Videobridge server-side
             * technology yet so it cannot be bothered with related requests.
             */
            false
        } else if (conferenceIQ.id == colibri!!.id) {
            /*
             * Remove the local Channels (from the specified conferenceIQ) i.e. the Channels on
             * which the local peer/user is sending to the Jitsi Videobridge server because they
             * concern this Call only and not its CallPeers.
             */
            for (mediaType in MediaType.values()) {
                val contentName = mediaType.toString()
                val content = conferenceIQ.getContent(contentName)
                if (content != null) {
                    val thisContent = colibri!!.getContent(contentName)
                    if (thisContent != null && thisContent.channelCount > 0) {
                        val thisChannel = thisContent.getChannel(0)
                        val channel = content.getChannel(thisChannel.id)
                        if (channel != null) content.removeChannel(channel)
                    }
                }
            }
            for (callPeer in getCallPeerList()) callPeer.processColibriConferenceIQ(conferenceIQ)

            /*
             * We have removed the local Channels from the specified conferenceIQ. Consequently, it
             * is no longer the same and fit for processing by other CallJabberImpl instances.
             */
            true
        } else {
            /*
             * This instance has set up a conference using the Jitsi Videobridge server-side
             * technology but it is not the one referred to by the specified conferenceIQ i.e. the
             * specified conferenceIQ does not concern this instance.
             */
            false
        }
    }

    /**
     * Creates a new call peer upon receiving session-initiate, and sends a RINGING response if required.
     *
     * Handle addCallPeer() in caller to avoid race condition as code here is handled on a new thread;
     * required when transport-info is sent separately
     * @see OperationSetBasicTelephonyJabberImpl.processJingleSynchronize
     * callPeer the [CallPeerJabberImpl]: the one that sent the INVITE.
     * jingle the [Jingle] that created the session.
     */
    fun processSessionInitiate(callPeer: CallPeerJabberImpl, jingle: Jingle, session: JingleCallSessionImpl) {
        /* cmeng (20200528): Must handle addCallPeer() in caller to handle transport-info sent separately */
        // FullJid remoteParty = jingle.getFrom().asFullJidIfPossible();
        // CallPeerJabberImpl callPeer = new CallPeerJabberImpl(remoteParty, this, jingle);
        // addCallPeer(callPeer);
        var autoAnswer = false
        var attendant: CallPeerJabberImpl? = null
        var basicTelephony: OperationSetBasicTelephonyJabberImpl? = null

        /*
         * We've already sent the ack to the specified session-initiate so if it has been
         * sent as part of an attended transfer, we have to hang up on the attendant.
         */
        val transfer = jingle.getExtension(SdpTransfer::class.java)
        if (transfer != null) {
            val sid = transfer.sid
            if (sid != null) {
                // val protocolProvider = getProtocolProvider()
                basicTelephony = sourceProvider.getOperationSet(OperationSetBasicTelephony::class.java) as OperationSetBasicTelephonyJabberImpl?
                val attendantCall = basicTelephony!!.activeCallsRepository.findBySid(sid)
                if (attendantCall != null) {
                    attendant = attendantCall.getPeerBySid(sid)
                    if (attendant != null
                            && basicTelephony.getFullCalleeURI(attendant.getPeerJid()).equals(transfer.from)
                            && sourceProvider.ourJID.equals(transfer.to)) {
                        autoAnswer = true
                    }
                }
            }
        }
        val coin = jingle.getExtension(CoinExtension::class.java)
        if (coin != null) {
            callPeer.setConferenceFocus(coin.isFocus)
        }

        // before notifying about this incoming call, make sure the session-initiate looks alright
        try {
            callPeer.processSessionInitiate(jingle)
        } catch (e: NotConnectedException) {
            callPeer.setState(CallPeerState.INCOMING_CALL)
            return
        } catch (e: InterruptedException) {
            callPeer.setState(CallPeerState.INCOMING_CALL)
            return
        }

        // if paranoia is set, to accept the call we need to know that the other party has support for media encryption
        if (sourceProvider.accountID.getAccountPropertyBoolean(ProtocolProviderFactory.MODE_PARANOIA, false)
                && callPeer.getMediaHandler().getAdvertisedEncryptionMethods().isEmpty()) {

            // send an error response;
            val reasonText = aTalkApp.getResString(R.string.service_gui_security_ENCRYPTION_REQUIRED)
            callPeer.setState(CallPeerState.FAILED, reasonText)
            session.terminateSessionAndUnregister(JingleReason.Reason.security_error, reasonText)
            return
        }
        if (callPeer.getState() == CallPeerState.FAILED) return
        callPeer.setState(CallPeerState.INCOMING_CALL)

        // in case of attended transfer, auto answer the call.
        if (autoAnswer) {
            // hang up the call before answer, else may terminate as busy.
            try {
                basicTelephony!!.hangupCallPeer(attendant)
            } catch (e: OperationFailedException) {
                Timber.w("Failed to hang up on attendant as part of session transfer")
            }

            /* answer directly */
            try {
                callPeer.answer()
            } catch (e: Exception) {
                Timber.w("Exception occurred while answer transferred call")
            }
            return
        }

        /*
         * see if offer contains audio and video so that we can propose option to the user
         * (i.e. answer with video if it is a video call...)
         */
        val offer = callPeer.sessionIQ!!.contents
        val directions = EnumMap<MediaType, MediaDirection>(MediaType::class.java)
        directions[MediaType.AUDIO] = MediaDirection.INACTIVE
        directions[MediaType.VIDEO] = MediaDirection.INACTIVE
        for (c in offer) {
            val mediaType = c.getFirstChildElement(RtpDescription::class.java).media
            val remoteDirection = JingleUtils.getDirection(c, callPeer.isInitiator)
            if (MediaType.AUDIO.toString() == mediaType) directions[MediaType.AUDIO] = remoteDirection else if (MediaType.VIDEO.toString() == mediaType) directions[MediaType.VIDEO] = remoteDirection
        }

        // If this was the first peer we added in this call, then the call is new,
        // and we need to notify everyone of its creation.
        if (callPeerCount == 1) {
            (parentOperationSet as AbstractOperationSetBasicTelephony<*>).fireCallEvent(CallEvent.CALL_RECEIVED, this, directions)
        }

        // Manages auto answer with "audio only", or "audio/video" answer.
        val autoAnswerOpSet = sourceProvider.getOperationSet(OperationSetBasicAutoAnswer::class.java) as OperationSetAutoAnswerJabberImpl?

        // See AndroidCallListener#onCallEvent(): For auto-answer to work properly for JingleMessage incoming call:
        // Setting answerOnJingleMessageAccept flag gets trigger only after <ringing/>; as this method is handled via separate thread;
        autoAnswerOpSet?.autoAnswer(this, directions, jingle)
    }

    /**
     * Updates the state of the local DTLS-SRTP endpoint (i.e. the local `DtlsControl`
     * instance) from the state of the remote DTLS-SRTP endpoint represented by a specific
     * `ColibriConferenceIQ.Channel`.
     *
     * peer the `CallPeer` associated with the method invocation
     * channel the `ColibriConferenceIQ.Channel` which represents the state of the remote
     * DTLS-SRTP endpoint
     * mediaType the `MediaType` of the media to be transmitted over the DTLS-SRTP session
     */
    private fun addDtlsAdvertisedEncryptions(peer: CallPeerJabberImpl,
            channel: ColibriConferenceIQ.Channel, mediaType: MediaType): Boolean {
        val peerMediaHandler = peer.mediaHandler
        val dtlsControl = peerMediaHandler.srtpControls[mediaType, SrtpControlType.DTLS_SRTP] as DtlsControl?
        dtlsControl?.setup = if(peer.isInitiator) Setup.ACTIVE else Setup.ACTPASS
        val remoteTransport = channel.transport
        return peerMediaHandler.addDtlsSrtpAdvertisedEncryption(true, remoteTransport, mediaType, false)
    }

    /**
     * Updates the state of the remote DTLS-SRTP endpoint represented by a specific
     * `ColibriConferenceIQ.Channel` from the state of the local DTLS-SRTP endpoint. The
     * specified `channel` is to be used by the conference focus for the purposes of
     * transmitting media between a remote peer and the Jitsi Videobridge server.
     *
     * mediaType the `MediaType` of the media to be transmitted over the DTLS-SRTP session
     * localContent the `JingleContent` of the local peer in the negotiation between the
     * local and the remote peers. If `remoteContent` is `null`, represents an
     * offer from the local peer to the remote peer; otherwise, represents an answer from the
     * local peer to an offer from the remote peer.
     * remoteContent the `JingleContent`, if any, of the remote peer in the negotiation
     * between the local and the remote peers. If `null`, `localContent`
     * represents an offer from the local peer to the remote peer; otherwise,
     * `localContent` represents an answer from the local peer to an offer from the remote peer
     * peer the `CallPeer` which represents the remote peer and which is associated with
     * the specified `channel`
     * channel the `ColibriConferenceIQ.Channel` which represents the state of the remote
     * DTLS-SRTP endpoint.
     */
    private fun setDtlsEncryptionOnChannel(mediaType: MediaType, localContent: JingleContent, remoteContent: JingleContent?,
            peer: CallPeerJabberImpl, channel: ColibriConferenceIQ.Channel) {
        val accountID = sourceProvider.accountID
        if ((accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
                        && accountID.isEncryptionProtocolEnabled(SrtpControlType.DTLS_SRTP)) && remoteContent != null) {
            val remoteTransport = remoteContent.getFirstChildElement(IceUdpTransport::class.java)
            if (remoteTransport != null) {
                val remoteFingerprints = remoteTransport.getChildElements(SrtpFingerprint::class.java)
                if (remoteFingerprints.isNotEmpty()) {
                    val localTransport = ensureTransportOnChannel(channel, peer)
                    if (localTransport != null) {
                        val localFingerprints = localTransport.getChildElements(SrtpFingerprint::class.java)
                        if (localFingerprints.isEmpty()) {
                            for (fingerprint in remoteFingerprints) {
                                localTransport.addChildElement(SrtpFingerprint.getBuilder()
                                        .setFingerprint(fingerprint.fingerprint)
                                        .setHash(fingerprint.hash)
                                        .setSetup(fingerprint.setup)
                                        .build()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Updates the state of the remote DTLS-SRTP endpoint represented by a specific
     * `ColibriConferenceIQ.Channel` from the state of the local DTLS-SRTP endpoint (i.e.
     * the local `DtlsControl` instance). The specified `channel` is to be used by the
     * conference focus for the purposes of transmitting media between the local peer and the Jitsi
     * Videobridge server.
     *
     * jvb the address/JID of the Jitsi Videobridge
     * peer the `CallPeer` associated with the method invocation
     * mediaType the `MediaType` of the media to be transmitted over the DTLS-SRTP session
     * channel the `ColibriConferenceIQ.Channel` which represents the state of the remote
     * DTLS-SRTP endpoint.
     */
    private fun setDtlsEncryptionOnChannel(jvb: Jid?, peer: CallPeerJabberImpl,
            mediaType: MediaType, channel: ColibriConferenceIQ.Channel) {
        // val protocolProvider = getProtocolProvider()
        val accountID = sourceProvider.accountID
        if (accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
                && accountID.isEncryptionProtocolEnabled(SrtpControlType.DTLS_SRTP)
                && sourceProvider.isFeatureSupported(jvb!!, ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_DTLS_SRTP)) {
            val tlsCertSA = accountID.getAccountPropertyString(ProtocolProviderFactory.DTLS_CERT_SIGNATURE_ALGORITHM, DtlsControlImpl.DEFAULT_SIGNATURE_AND_HASH_ALGORITHM)
            setTlsCertificateSA(tlsCertSA!!)
            val mediaHandler = peer.mediaHandler
            val dtlsControl = mediaHandler.srtpControls.getOrCreate(mediaType, SrtpControlType.DTLS_SRTP, null) as DtlsControl?
            if (dtlsControl != null) {
                val transport = ensureTransportOnChannel(channel, peer)
                if (transport != null) setDtlsEncryptionOnTransport(dtlsControl, transport)
            }
        }
    }

    @Throws(OperationFailedException::class)
    private fun setTransportOnChannel(peer: CallPeerJabberImpl, media: String, channel: ColibriConferenceIQ.Channel) {
        val transport = peer.mediaHandler.transportManager.createTransport(media)
        if (transport is IceUdpTransport) channel.transport = transport
    }

    @Throws(OperationFailedException::class)
    private fun setTransportOnChannel(media: String, localContent: JingleContent,
            remoteContent: JingleContent?, channel: ColibriConferenceIQ.Channel) {
        if (remoteContent != null) {
            val transport = remoteContent.getFirstChildElement(IceUdpTransport::class.java)
            channel.transport = TransportManagerJabberImpl.cloneTransportAndCandidates(transport)
        }
    }

    /**
     * Makes an attempt to ensure that a specific `ColibriConferenceIQ.Channel` has a non-
     * `null` `transport` set. If the specified `channel` does not have a
     * `transport`, the method invokes the `TransportManager` of the specified
     * `CallPeerJabberImpl` to initialize a new `ExtensionElement`.
     *
     * channel the `ColibriConferenceIQ.Channel` to ensure the `transport` on
     * peer the `CallPeerJabberImpl` which is associated with the specified
     * `channel` and which specifies the `TransportManager` to be described in
     * the specified `channel`
     * @return the `transport` of the specified `channel`
     */
    private fun ensureTransportOnChannel(channel: ColibriConferenceIQ.Channel, peer: CallPeerJabberImpl): IceUdpTransport? {
        var transport = channel.transport
        if (transport == null) {
            val pe = peer.mediaHandler.transportManager.createTransportPacketExtension()
            if (pe is IceUdpTransport) {
                transport = pe
                channel.transport = transport
            }
        }
        return transport
    }

    /**
     * Gets the entity ID of the Jitsi Videobridge to be utilized by this `Call` for the
     * purposes of establishing a server-assisted telephony conference.
     *
     * @return the entity ID of the Jitsi Videobridge to be utilized by this `Call` for the
     * purposes of establishing a server-assisted telephony conference.
     */
    var jitsiVideobridge: Jid? = null
        get() {
            if (field == null && conference.isJitsiVideobridge) {
                val jvb = sourceProvider.jitsiVideobridge
                if (jvb != null) field = jvb
            }
            return field
        }

    /**
     * {@inheritDoc}
     *
     * Implements [ #toneReceived(net.java.sip.communicator.service.protocol.event.DTMFReceivedEvent)][net.java.sip.communicator.service.protocol.event.DTMFListener]
     *
     * Forwards DTMF events to the `IncomingDTMF` operation set, setting this `Call` as the source.
     */
    override fun toneReceived(evt: DTMFReceivedEvent?) {
        val opSet = sourceProvider.getOperationSet(OperationSetIncomingDTMF::class.java)
        if (opSet is OperationSetIncomingDTMFJabberImpl) {
            // Re-fire the event using this Call as the source.
            opSet.toneReceived(DTMFReceivedEvent(this,
                    evt!!.getValue(), evt.getDuration(), evt.getStart()))
        }
    }

    /**
     * Returns the peer whose corresponding session has the specified `sid`.
     *
     * sid the ID of the session whose peer we are looking for.
     * @return the [CallPeerJabberImpl] with the specified jingle
     * `sid` and `null` if no such peer exists in this call.
     */
    fun getPeerBySid(sid: String?): CallPeerJabberImpl? {
        if (sid == null) return null
        for (peer in getCallPeerList()) {
            if (sid == peer.sid) return peer
        }
        return null
    }

    /**
     * Determines if this call contains a peer whose corresponding session has the specified `sid`.
     *
     * sid the ID of the session whose peer we are looking for.
     * @return `true` if this call contains a peer with the specified jingle `sid` and false otherwise.
     */
    fun containsSid(sid: String?): Boolean {
        return getPeerBySid(sid) != null
    }

    /**
     * Returns the peer associated session-initiate JingleIQ has the specified `stanzaId`.
     *
     * stanzaId the Stanza Id of the session-initiate JingleIQ whose peer we are looking for.
     * @return the [CallPeerJabberImpl] with the specified IQ `stanzaId`
     * and `null` if no such peer exists in this call.
     */
    fun getPeerByJingleIQStanzaId(stanzaId: String?): CallPeerJabberImpl? {
        if (stanzaId == null) return null
        for (peer in getCallPeerList()) {
            if (stanzaId == peer.jingleIQStanzaId) return peer
        }
        return null
    }

    companion object {
        /**
         * Sets the properties (i.e. fingerprint and hash function) of a specific `DtlsControl`
         * on the specific `IceUdpTransport`.
         *
         * dtlsControl the `DtlsControl` the properties of which are to be set on the specified
         * `localTransport`
         * localTransport the `IceUdpTransport` on which the properties of the specified
         * `dtlsControl` are to be set
         */
        fun setDtlsEncryptionOnTransport(dtlsControl: DtlsControl, localTransport: IceUdpTransport) {
            val fingerprint = dtlsControl.localFingerprint
            val hash = dtlsControl.localFingerprintHashFunction
            val setup = (dtlsControl as DtlsControlImpl).setup.toString()
            localTransport.addChildElement(SrtpFingerprint.getBuilder()
                    .setFingerprint(fingerprint)
                    .setHash(hash)
                    .setSetup(setup)
                    .build()
            )
        }
    }
}