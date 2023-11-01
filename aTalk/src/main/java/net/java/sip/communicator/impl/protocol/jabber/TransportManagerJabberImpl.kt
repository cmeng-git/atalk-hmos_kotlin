/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.CallPeer
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.media.TransportManager
import org.atalk.service.neomedia.MediaStreamTarget
import org.atalk.service.neomedia.StreamConnector
import org.atalk.service.neomedia.StreamConnectorFactory
import org.atalk.util.MediaType
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.packet.ExtensionElement
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smackx.colibri.ColibriConferenceIQ
import org.jivesoftware.smackx.jingle.element.JingleContent
import org.jivesoftware.smackx.jingle_rtp.JingleUtils
import org.jivesoftware.smackx.jingle_rtp.element.IceUdpTransport
import org.jivesoftware.smackx.jingle_rtp.element.RtpDescription
import java.net.InetAddress

/**
 * `TransportManager`s gather local candidates for incoming and outgoing calls. Their work
 * starts by calling a start method which, using the remote peer's session description, would start
 * the harvest. Calling a second wrap up method would deliver the candidate harvest, possibly after
 * blocking if it has not yet completed.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class TransportManagerJabberImpl
/**
 * Creates a new instance of this transport manager, binding it to the specified peer.
 *
 * @param callPeer the [CallPeer] whose traffic we will be taking care of.
 */
protected constructor(callPeer: CallPeerJabberImpl?) : TransportManager<CallPeerJabberImpl?>(callPeer) {
    /**
     * The information pertaining to the Jisti Videobridge conference which the local peer
     * represented by this instance is a focus of. It gives a view of the whole Jitsi Videobridge
     * conference managed by the associated `CallJabberImpl` which provides information
     * specific to this `TransportManager` only.
     */
    private var colibri: ColibriConferenceIQ? = null
    /**
     * Returns the generation that our current candidates belong to.
     *
     * @return the generation that we should assign to candidates that we are currently advertising.
     */
    /**
     * The generation of the candidates we are currently generating
     */
    protected var currentGeneration = 0
        private set

    /**
     * The indicator which determines whether this `TransportManager` instance is responsible t0
     * establish the connectivity with the associated Jitsi Videobridge (in case it is being employed at all).
     */
    var isEstablishingConnectivityWithJitsiVideobridge = false

    /**
     * The indicator which determines whether this `TransportManager` instance is yet to
     * start establishing the connectivity with the associated Jitsi Videobridge (in case it is
     * being employed at all).
     */
    var startConnectivityEstablishmentWithJitsiVideobridge = false

    /**
     * Returns the `InetAddress` that is most likely to be to be used as a next hop when
     * contacting the specified `destination`. This is an utility method that is used
     * whenever we have to choose one of our local addresses to put in the Via, Contact or (in the
     * case of no registrar accounts) From headers.
     *
     * @param peer the CallPeer that we would contact.
     * @return the `InetAddress` that is most likely to be to be used as a next hop when
     * contacting the specified `destination`.
     * @throws IllegalArgumentException if `destination` is not a valid host/IP/FQDN
     */
    override fun getIntendedDestination(peer: CallPeerJabberImpl?): InetAddress {
        return peer!!.getProtocolProvider().nextHop
    }

    /**
     * Returns the ID that we will be assigning to the next candidate we create.
     *
     * @return the next ID to use with a candidate.
     */
    protected val nextID: String
        get() {
            var nextID: Int
            synchronized(TransportManagerJabberImpl::class.java) { nextID = Companion.nextID++ }
            return nextID.toString()
        }

    /**
     * Gets the `MediaStreamTarget` to be used as the `target` of the
     * `MediaStream` with a specific `MediaType`.
     *
     * @param mediaType the `MediaType` of the `MediaStream` which is to have its
     * `target` set to the returned `MediaStreamTarget`
     * @return the `MediaStreamTarget` to be used as the `target` of the
     * `MediaStream` with the specified `MediaType`
     */
    abstract fun getStreamTarget(mediaType: MediaType): MediaStreamTarget?

    /**
     * Gets the XML namespace of the Jingle transport implemented by this `TransportManagerJabberImpl`.
     *
     * @return the XML namespace of the Jingle transport implemented by this `TransportManagerJabberImpl`
     */
    abstract val xmlNamespace: String?

    /**
     * Increments the generation that we are assigning candidates.
     */
    protected fun incrementGeneration() {
        currentGeneration++
    }

    /**
     * Sends transport-related information received from the remote peer to the associated Jiitsi
     * Videobridge in order to update the (remote) `ColibriConferenceIQ.Channel` associated
     * with this `TransportManager` instance.
     *
     * @param map a `Map` of media-IceUdpTransport pairs which represents the
     * transport-related information which has been received from the remote peer and which
     * is to be sent to the associated Jitsi Videobridge
     */
    @Throws(OperationFailedException::class)
    protected fun sendTransportInfoToJitsiVideobridge(map: Map<String, IceUdpTransport?>) {
        val peer = getCallPeer()
        val initiator = !peer!!.isInitiator
        var conferenceRequest: ColibriConferenceIQ? = null
        for ((media, value) in map) {
            val mediaType = MediaType.parseString(media)
            val channel = getColibriChannel(mediaType, false /* remote */)
            if (channel != null) {
                val transport = try {
                    cloneTransportAndCandidates(value)
                } catch (ofe: OperationFailedException) {
                    null
                } ?: continue

                val channelRequest = ColibriConferenceIQ.Channel()
                channelRequest.id = channel.id
                channelRequest.isInitiator = initiator
                channelRequest.transport = transport
                if (conferenceRequest == null) {
                    if (colibri == null) break else {
                        val id = colibri!!.id
                        if (id == null || id.isEmpty()) break else {
                            conferenceRequest = ColibriConferenceIQ()
                            conferenceRequest.id = id
                            conferenceRequest.to = colibri!!.from
                            conferenceRequest.type = IQ.Type.set
                        }
                    }
                }
                conferenceRequest.getOrCreateContent(media).addChannel(channelRequest)
            }
        }
        if (conferenceRequest != null) {
            try {
                peer.getProtocolProvider().connection!!.sendStanza(conferenceRequest)
            } catch (e1: NotConnectedException) {
                throw OperationFailedException("Could not send conference request",
                        OperationFailedException.GENERAL_ERROR, e1)
            } catch (e1: InterruptedException) {
                throw OperationFailedException("Could not send conference request",
                        OperationFailedException.GENERAL_ERROR, e1)
            }
        }
    }

    /**
     * Starts transport candidate harvest for a specific `JingleContent` that we are
     * going to offer or answer with.
     *
     * @param theirContent the `JingleContent` offered by the remote peer to which we are going
     * to answer with `ourContent` or `null` if `ourContent` will be an offer to the remote peer
     * @param ourContent the `JingleContent` for which transport candidate harvest is to be started
     * @param transportInfoSender a `TransportInfoSender` if the harvested transport candidates are to be sent in
     * a `transport-info` rather than in `ourContent`; otherwise, `null`
     * @param media the media of the `RtpDescriptionExtensionElement` child of `ourContent`
     * @return a `ExtensionElement` to be added as a child to `ourContent`; otherwise, `null`
     * @throws OperationFailedException if anything goes wrong while starting transport candidate harvest for
     * the specified `ourContent`
     */
    @Throws(OperationFailedException::class)
    protected abstract fun startCandidateHarvest(theirContent: JingleContent?,
            ourContent: JingleContent, transportInfoSender: TransportInfoSender?, media: String): ExtensionElement?

    /**
     * Starts transport candidate harvest. This method should complete rapidly and, in case of
     * lengthy procedures like STUN/TURN/UPnP candidate harvests are necessary, they should be
     * executed in a separate thread. Candidate harvest would then need to be concluded in the
     * [.wrapupCandidateHarvest] method which would be called once we absolutely need the candidates.
     *
     * @param theirOffer a media description offer that we've received from the remote party
     * and that we should use in case we need to know what transports our peer is using.
     * @param ourAnswer the content descriptions that we should be adding our transport lists to.
     * This is used i.e. when their offer is null, for sending the Jingle session-initiate offer.
     * @param transportInfoSender the `TransportInfoSender` to be used by this
     * `TransportManagerJabberImpl` to send `transport-info` `Jingle`s
     * from the local peer to the remote peer if this `TransportManagerJabberImpl`
     * wishes to utilize `transport-info`. Local candidate addresses sent by this
     * `TransportManagerJabberImpl` in `transport-info` are expected to not be
     * included in the result of [.wrapupCandidateHarvest].
     * @throws OperationFailedException if we fail to allocate a port number.
     */
    @Throws(OperationFailedException::class)
    open fun startCandidateHarvest(theirOffer: List<JingleContent>?, ourAnswer: MutableList<JingleContent>,
            transportInfoSender: TransportInfoSender?) {
        val cpes = theirOffer ?: ourAnswer

        /*
         * If Jitsi Videobridge is to be used, determine which channels are to be allocated and
         * attempt to allocate them now.
         */
        val peer = getCallPeer()
        if (peer!!.isJitsiVideobridge) {
            val contentMap: MutableMap<JingleContent, JingleContent?> = LinkedHashMap()
            for (cpe in cpes) {
                val mediaType = JingleUtils.getMediaType(cpe)

                /*
                 * The existence of a content for the mediaType and regardless of the existence of
                 * channels in it signals that a channel allocation request has already been sent
                 * for that mediaType.
                 */
                if (colibri == null || colibri!!.getContent(mediaType.toString()) == null) {
                    var local: JingleContent?
                    var remote: JingleContent?
                    if (cpes === ourAnswer) {
                        local = cpe
                        remote = if (theirOffer == null) null else findContentByName(theirOffer, cpe.name)
                    } else {
                        local = findContentByName(ourAnswer, cpe.name)
                        remote = cpe
                    }
                    contentMap[local!!] = remote
                }
            }
            if (contentMap.isNotEmpty()) {
                /*
                 * We are about to request the channel allocations for the media types found in
                 * contentMap. Regardless of the response, we do not want to repeat these requests.
                 */
                if (colibri == null) colibri = ColibriConferenceIQ()
                for (e in contentMap.entries) {
                    var cpe = e.value
                    if (cpe == null) cpe = e.key
                    colibri!!.getOrCreateContent(JingleUtils.getMediaType(cpe).toString())
                }
                val call = peer.getCall()
                val conferenceResult = call!!.createColibriChannels(peer, contentMap)
                if (conferenceResult != null) {
                    val videobridgeID = colibri!!.id
                    val conferenceResultID = conferenceResult.id
                    if (videobridgeID == null) colibri!!.id = conferenceResultID else check(videobridgeID == conferenceResultID) { "conference.id" }
                    val videobridgeFrom = conferenceResult.from
                    if (videobridgeFrom != null && videobridgeFrom.isNotEmpty()) {
                        colibri!!.from = videobridgeFrom
                    }
                    for (contentResult in conferenceResult.contents) {
                        val content = colibri!!.getOrCreateContent(contentResult.name)
                        for (channelResult in contentResult.channels) {
                            if (content.getChannel(channelResult.id) == null) {
                                content.addChannel(channelResult)
                            }
                        }
                    }
                } else {
                    /*
                     * The call fails if the createColibriChannels method fails which may happen if
                     * the conference packet times out or it can't be built.
                     */
                    ProtocolProviderServiceJabberImpl.throwOperationFailedException(
                            "Failed to allocate colibri channel.", OperationFailedException.GENERAL_ERROR, null)
                }
            }
        }
        for (cpe in cpes) {
            val contentName = cpe.name
            val ourContent = findContentByName(ourAnswer, contentName)

            // it might be that we decided not to reply to this content
            if (ourContent != null) {
                val theirContent = if (theirOffer == null) null else findContentByName(theirOffer, contentName)
                val rtpDesc = ourContent.getFirstChildElement(RtpDescription::class.java)
                val media = rtpDesc.media
                val pe = startCandidateHarvest(theirContent, ourContent, transportInfoSender, media)

                // This will add the transport-info into the jingleContent for session-initiate
                if (pe != null) {
                    ourContent.addChildElement(pe)
                }

                // cmeng (20220228): Not working: Correct JingleContent created but not the same instance/reference
                // i.e. OurContent1: JingleContent@67c0ff2 <> OurContent2: JingleContent@19b25f9 (new)
//                if (pe != null) {
//                    Timber.w("OurContent1: %s:\n %s\n%s", ourContent, ourContent.toXML(), pe.toXML());
//                    ourContent = (JingleContent) ourContent.getBuilder(null)
//                            .addChildElement(pe)
//                            .build();
//                    Timber.w("OurContent2: %s: %s",ourContent, ourContent.toXML());
//                }
            }
        }
    }

    /**
     * Notifies the transport manager that it should conclude candidate harvesting as soon as
     * possible and return the lists of candidates gathered so far.
     *
     * @return the content list that we received earlier (possibly cloned into a new instance) and
     * that we have updated with transport lists.
     */
    abstract fun wrapupCandidateHarvest(): List<JingleContent>

    /**
     * Starts the connectivity establishment of this `TransportManagerJabberImpl` i.e. checks
     * the connectivity between the local and the remote peers given the remote counterpart of the
     * negotiation between them.
     *
     * @param remote the collection of `JingleContent`s which represents the remote
     * counterpart of the negotiation between the local and the remote peer
     * @return `true` if connectivity establishment has been started in response to the call;
     * otherwise, `false`. `TransportManagerJabberImpl` implementations which
     * do not perform connectivity checks (e.g. raw UDP) should return `true`. The
     * default implementation does not perform connectivity checks and always returns `true`.
     */
    @Throws(OperationFailedException::class)
    open fun startConnectivityEstablishment(remote: Iterable<JingleContent>?): Boolean {
        return true
    }

    /**
     * Starts the connectivity establishment of this `TransportManagerJabberImpl` i.e. checks
     * the connectivity between the local and the remote peers given the remote counterpart of the
     * negotiation between them.
     *
     * @param remote a `Map` of media-`IceUdpTransport` pairs which represents
     * the remote counterpart of the negotiation between the local and the remote peers
     * @return `true` if connectivity establishment has been started in response to the call;
     * otherwise, `false`. `TransportManagerJabberImpl` implementations which
     * do not perform connectivity checks (e.g. raw UDP) should return `true`. The
     * default implementation does not perform connectivity checks and always returns `true`.
     */
    open fun startConnectivityEstablishment(remote: MutableMap<String, IceUdpTransport>): Boolean {
        return true
    }

    /**
     * Notifies this `TransportManagerJabberImpl` that it should conclude any started connectivity establishment.
     *
     * @throws OperationFailedException if anything goes wrong with connectivity establishment (i.e. ICE failed, ...)
     */
    @Throws(OperationFailedException::class)
    open fun wrapupConnectivityEstablishment() {
    }

    /**
     * Removes a content with a specific name from the transport-related part of the session
     * represented by this `TransportManagerJabberImpl` which may have been reported through
     * previous calls to the `startCandidateHarvest` and `startConnectivityEstablishment` methods.
     *
     * **Note**: Because `TransportManager` deals with `MediaType`s, not content
     * names and `TransportManagerJabberImpl` does not implement translating from content
     * name to `MediaType`, implementers are expected to call
     * [TransportManager.closeStreamConnector].
     *
     * @param name the name of the content to be removed from the transport-related part of the session
     * represented by this `TransportManagerJabberImpl`
     */
    abstract fun removeContent(name: String)

    /**
     * Removes a content with a specific name from a specific collection of contents and closes any
     * associated `StreamConnector`.
     *
     * @param contents the collection of contents to remove the content with the specified name from
     * @param name the name of the content to remove
     * @return the removed `JingleContent` if any; otherwise, `null`
     */
    protected fun removeContent(contents: Iterable<JingleContent>, name: String): JingleContent? {
         val contentIter = contents.toMutableList().iterator()

         while (contentIter.hasNext()) {
            val content = contentIter.next()
            if (name == content.name) {
                contentIter.remove()

                // closeStreamConnector
                val mediaType = JingleUtils.getMediaType(content)
                mediaType?.let { closeStreamConnector(it) }
                return content
            }
        }
        return null
    }

    /**
     * Releases the resources acquired by this `TransportManager` and prepares it for garbage collection.
     */
    open fun close() {
        for (mediaType in MediaType.values()) closeStreamConnector(mediaType)
    }

    /**
     * Closes a specific `StreamConnector` associated with a specific `MediaType`. If
     * this `TransportManager` has a reference to the specified `streamConnector`, it remains.
     * Also expires the `ColibriConferenceIQ.Channel` associated with the closed `StreamConnector`.
     *
     * @param mediaType the `MediaType` associated with the specified `streamConnector`
     * @param streamConnector the `StreamConnector` to be closed
     */
    @Throws(OperationFailedException::class)
    override fun closeStreamConnector(mediaType: MediaType?, streamConnector: StreamConnector) {
        try {
            var superCloseStreamConnector = true
            if (streamConnector is ColibriStreamConnector) {
                val peer = getCallPeer()
                if (peer != null) {
                    val call = peer.getCall()
                    if (call != null) {
                        superCloseStreamConnector = false
                        call.closeColibriStreamConnector(peer, mediaType!!, streamConnector)
                    }
                }
            }
            if (superCloseStreamConnector) super.closeStreamConnector(mediaType, streamConnector)
        } finally {
            /*
             * Expire the ColibriConferenceIQ.Channel associated with the closed StreamConnector.
             */
            if (colibri != null) {
                val content = colibri!!.getContent(mediaType.toString())
                if (content != null) {
                    val channels = content.channels
                    if (channels.size == 2) {
                        val requestConferenceIQ = ColibriConferenceIQ()
                        requestConferenceIQ.id = colibri!!.id
                        val requestContent = requestConferenceIQ.getOrCreateContent(content.name)
                        requestContent.addChannel(channels[1])

                        /*
                         * Regardless of whether the request to expire the Channel associated with
                         * mediaType succeeds, consider the Channel in question expired. Since
                         * RawUdpTransportManager allocates a single channel per MediaType, consider
                         * the whole Content expired.
                         */
                        colibri!!.removeContent(content)
                        val peer = getCallPeer()
                        if (peer != null) {
                            val call = peer.getCall()
                            if (call != null) {
                                try {
                                    call.expireColibriChannels(peer, requestConferenceIQ)
                                } catch (e: NotConnectedException) {
                                    throw OperationFailedException("Could not expire colibri channels",
                                            OperationFailedException.GENERAL_ERROR, e)
                                } catch (e: InterruptedException) {
                                    throw OperationFailedException("Could not expire colibri channels",
                                            OperationFailedException.GENERAL_ERROR, e)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * Adds support for telephony conferences utilizing the Jitsi Videobridge server-side technology.
     *
     * @see .doCreateStreamConnector
     */
    @Throws(OperationFailedException::class)
    override fun createStreamConnector(mediaType: MediaType?): StreamConnector {
        val channel = getColibriChannel(mediaType, true /* local */)
        if (channel != null) {
            val peer = getCallPeer()
            val call = peer!!.getCall()
            val streamConnector: StreamConnector? = call!!.createColibriStreamConnector(peer, mediaType!!, channel, object : StreamConnectorFactory {
                override fun createStreamConnector(): StreamConnector? {
                    return try {
                        doCreateStreamConnector(mediaType)
                    } catch (ofe: OperationFailedException) {
                        null
                    }
                }
            })
            if (streamConnector != null) return streamConnector
        }
        return doCreateStreamConnector(mediaType!!)!!
    }


    @Throws(OperationFailedException::class)
    abstract fun createTransport(media: String): ExtensionElement?

    @Throws(OperationFailedException::class)
    protected fun createTransportForStartCandidateHarvest(media: String): ExtensionElement? {
        var pe: ExtensionElement? = null
        if (getCallPeer()!!.isJitsiVideobridge) {
            val mediaType = MediaType.parseString(media)
            val channel = getColibriChannel(mediaType, false /* remote */)
            if (channel != null) pe = cloneTransportAndCandidates(channel.transport)
        } else pe = createTransport(media)
        return pe
    }

    /**
     * Initializes a new `ExtensionElement` instance appropriate to the type of Jingle
     * transport represented by this `TransportManager`. The new instance is not initialized
     * with any attributes or child extensions.
     *
     * @return a new `ExtensionElement` instance appropriate to the type of Jingle transport
     * represented by this `TransportManager`
     */
    abstract fun createTransportPacketExtension(): ExtensionElement?

    /**
     * Creates a media `StreamConnector` for a stream of a specific `MediaType`. The
     * minimum and maximum of the media port boundaries are taken into account.
     *
     * @param mediaType the `MediaType` of the stream for which a `StreamConnector` is to be created
     * @return a `StreamConnector` for the stream of the specified `mediaType`
     * @throws OperationFailedException if the binding of the sockets fails
     */
    @Throws(OperationFailedException::class)
    open fun doCreateStreamConnector(mediaType: MediaType): StreamConnector? {
        return super.createStreamConnector(mediaType)
    }

    /**
     * Finds a `TransportManagerJabberImpl` participating in a telephony conference utilizing
     * the Jitsi Videobridge server-side technology that this instance is participating in which is
     * establishing the connectivity with the Jitsi Videobridge server (as opposed to a `CallPeer`).
     *
     * @return a `TransportManagerJabberImpl` which is participating in a telephony
     * conference utilizing the Jitsi Videobridge server-side technology that this instance
     * is participating in which is establishing the connectivity with the Jitsi Videobridge
     * server (as opposed to a `CallPeer`).
     */
    fun findTransportManagerEstablishingConnectivityWithJitsiVideobridge(): TransportManagerJabberImpl? {
        var transportManager: TransportManagerJabberImpl? = null
        if (getCallPeer()!!.isJitsiVideobridge) {
            val conference = getCallPeer()!!.getCall()!!.conference
            for (aCall in conference.calls) {
                val callPeerIter = aCall.getCallPeers()
                while (callPeerIter.hasNext()) {
                    val aCallPeer = callPeerIter.next()
                    if (aCallPeer is CallPeerJabberImpl) {
                        val aTransportManager = aCallPeer.mediaHandler.transportManager
                        if (aTransportManager.isEstablishingConnectivityWithJitsiVideobridge) {
                            transportManager = aTransportManager
                            break
                        }
                    }
                }
            }
        }
        return transportManager
    }

    /**
     * Gets the [ColibriConferenceIQ.Channel] which belongs to a content associated with a
     * specific `MediaType` and is to be either locally or remotely used.
     *
     * **Note**: Modifications to the `ColibriConferenceIQ.Channel` instance returned by
     * the method propagate to (the state of) this instance.
     *
     * @param mediaType the `MediaType` associated with the content which contains the
     * `ColibriConferenceIQ.Channel` to get
     * @param local `true` if the `ColibriConferenceIQ.Channel` which is to be used locally
     * is to be returned or `false` for the one which is to be used remotely
     * @return the `ColibriConferenceIQ.Channel` which belongs to a content associated with
     * the specified `mediaType` and which is to be used in accord with the specified
     * `local` indicator if such a channel exists; otherwise, `null`
     */
    fun getColibriChannel(mediaType: MediaType?, local: Boolean): ColibriConferenceIQ.Channel? {
        var channel: ColibriConferenceIQ.Channel? = null
        if (colibri != null) {
            val content = colibri!!.getContent(mediaType.toString())
            if (content != null) {
                val channels = content.channels
                if (channels.size == 2) channel = channels[if (local) 0 else 1]
            }
        }
        return channel
    }

    /**
     * Sets the flag which indicates whether to use rtcpmux or not.
     */
    open var isRtcpmux: Boolean = false

    companion object {
        /**
         * The ID that we will be assigning to our next candidate. We use `int`s for
         * interoperability reasons (Emil: I believe that GTalk uses `int`s. If that turns out
         * not to be the case we can stop using `int`s here if that's an issue).
         */
        private var nextID = 1

        /**
         * Looks through the `cpExtList` and returns the [JingleContent] with the specified name.
         *
         * @param cpExtList the list that we will be searching for a specific content.
         * @param name the name of the content element we are looking for.
         * @return the [JingleContent] with the specified name or `null` if no
         * such content element exists.
         */
        fun findContentByName(cpExtList: Iterable<JingleContent>, name: String): JingleContent? {
            for (cpExt in cpExtList) {
                if (cpExt.name == name) return cpExt
            }
            return null
        }

        /**
         * Clones a specific `IceUdpTransport` and its candidates.
         *
         * @param src the `IceUdpTransport` to be cloned
         * @return a new `IceUdpTransport` instance which has the same run-time
         * type, attributes, namespace, text and candidates as the specified `src`
         * @throws OperationFailedException if an error occurs during the cloing of the specified `src` and its candidates
         */
        @Throws(OperationFailedException::class)
        fun cloneTransportAndCandidates(src: IceUdpTransport?): IceUdpTransport? {
            try {
                return IceUdpTransport.cloneTransportAndCandidates(src, false)
            } catch (e: Exception) {
                ProtocolProviderServiceJabberImpl.throwOperationFailedException(
                        "Failed to close transport and candidates.", OperationFailedException.GENERAL_ERROR, e)
            }
            return null
        }
    }
}