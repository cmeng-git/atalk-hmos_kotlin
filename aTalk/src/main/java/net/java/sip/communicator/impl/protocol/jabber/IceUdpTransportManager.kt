/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.netaddr.NetworkAddressManagerService
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.SecurityAuthority
import net.java.sip.communicator.service.protocol.StunServerDescriptor
import net.java.sip.communicator.service.protocol.UserCredentials
import net.java.sip.communicator.util.PortTracker
import okhttp3.internal.notify
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.service.neomedia.DefaultStreamConnector
import org.atalk.service.neomedia.MediaStreamTarget
import org.atalk.service.neomedia.StreamConnector
import org.atalk.util.MediaType
import org.ice4j.Transport
import org.ice4j.TransportAddress
import org.ice4j.ice.Agent
import org.ice4j.ice.Candidate
import org.ice4j.ice.Component
import org.ice4j.ice.IceMediaStream
import org.ice4j.ice.IceProcessingState
import org.ice4j.ice.RemoteCandidate
import org.ice4j.ice.harvest.StunCandidateHarvester
import org.ice4j.ice.harvest.TurnCandidateHarvester
import org.ice4j.ice.harvest.UPNPHarvester
import org.ice4j.security.LongTermCredential
import org.ice4j.socket.DatagramPacketFilter
import org.jivesoftware.smack.packet.ExtensionElement
import org.jivesoftware.smackx.externalservicediscovery.IceCandidateHarvester
import org.jivesoftware.smackx.jingle.element.JingleContent
import org.jivesoftware.smackx.jingle_rtp.CandidateType
import org.jivesoftware.smackx.jingle_rtp.element.IceUdpTransport
import org.jivesoftware.smackx.jingle_rtp.element.IceUdpTransport.*
import org.jivesoftware.smackx.jingle_rtp.element.IceUdpTransportCandidate
import org.jivesoftware.smackx.jingle_rtp.element.RtcpMux
import org.jivesoftware.smackx.jingle_rtp.element.RtpDescription
import timber.log.Timber
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.math.max

/**
 * A [TransportManagerJabberImpl] implementation that would use ICE for candidate management.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 * @author MilanKral
 * @link https://github.com/MilanKral/atalk-android/commit/d61d5165dda4d290280ebb3e93075e8846e255ad
 * Enhance TURN with TCP, TLS, DTLS transport
 */
class IceUdpTransportManager(
        callPeer: CallPeerJabberImpl?,
) : TransportManagerJabberImpl(callPeer), PropertyChangeListener {
    // The default STUN servers that will be used in the peer to peer connections if none is specified
    // pick the first one that is reachable multiple stun servers only add time in redundant candidates harvest
    private var stunServers = listOf(
        "stun1.l.google.com:19302",
        "stun2.l.google.com:19302",
        "stun3.l.google.com:19302"
    )

    /**
     * This is where we keep our answer between the time we get the offer and are ready with the answer.
     */
    private var cpeList: MutableList<JingleContent>? = null

    /**
     * The ICE agent that this transport manager would be using for ICE negotiation.
     */
    private val iceAgent: Agent?

    /**
     * Whether this transport manager should use rtcp-mux. When using rtcp-mux,
     * the ICE Agent initializes a single Component per stream, and we use
     * [org.ice4j.socket.MultiplexingDatagramSocket] to split its
     * socket into a socket accepting RTCP packets, and one for everything else (RTP, DTLS).
     *
     * Set the property as static so that it retains the state init by the caller. It will then use
     * as default when making a new call: to take care jitsi that cannot support <rtcp-mux></rtcp-mux>
     */
    override var isRtcpmux = true

    /**
     * Caches the sockets for the stream connector so that they are not re-created.
     */
    private var streamConnectorSockets: Array<DatagramSocket?>? = null

    /**
     * Creates a new instance of this transport manager, binding it to the specified peer.
     *
     * callPeer the CallPeer whose traffic we will be taking care of.
     */
    init {
        iceAgent = createIceAgent()
        iceAgent!!.addStateChangeListener(this)
    }

    /**
     * Creates the ICE agent that we would be using in this transport manager for all negotiation.
     *
     * @return the ICE agent to use for all the ICE negotiation that this transport manager would be going through
     */
    override fun createIceAgent(): Agent? {
        val startGatheringHarvesterTime = System.currentTimeMillis()
        val peer = getCallPeer()!!
        val provider = peer.getProtocolProvider()
        val connection = provider.connection
        val namSer = getNetAddrMgr()
        var atLeastOneStunServer = false
        val agent = namSer!!.createIceAgent()!!

        /*
         * XEP-0176: the initiator MUST include the ICE-CONTROLLING attribute,
         * the responder MUST include the ICE-CONTROLLED attribute.
         */
        agent.isControlling = !peer.isInitiator

        // we will now create the harvesters
        val accID = provider.accountID as JabberAccountIDImpl
        if (accID.isStunServerDiscoveryEnabled) {
            val extServiceHarvester = IceCandidateHarvester.getExtServiceHarvester(connection, StunServerDescriptor.PROTOCOL_UDP)
            Timber.i("Auto discovered STUN/TURN extService harvester: %s", extServiceHarvester)
            if (extServiceHarvester.isNotEmpty()) {
                for (iceCandidate in extServiceHarvester) {
                    agent.addCandidateHarvester(iceCandidate)
                }
                atLeastOneStunServer = true
            }

            // the default server is supposed to use the same user name and password as the account itself.
            val username = accID.bareJid.toString()
            var password = JabberActivator.protocolProviderFactory.loadPassword(accID)
            var credentials = provider.userCredentials
            if (credentials != null) password = credentials.getPasswordAsString()

            // ask for password if not saved
            if (password == null) {
                // create a default credentials object
                credentials = UserCredentials()
                credentials.userName = accID.mUserID
                // request a password from the user
                credentials = provider.authority!!.obtainCredentials(accID, credentials,
                    SecurityAuthority.AUTHENTICATION_REQUIRED, false)

                // in case user has canceled the login window
                if (credentials == null) {
                    Timber.i("Credentials were null. User has most likely canceled the login operation")
                    return null
                }

                // extract the password the user passed us.
                val pass = credentials.password

                // the user didn't provide us a password (i.e. canceled the operation)
                if (pass == null) {
                    Timber.i("Password was null. User has most likely canceled the login operation")
                    return null
                }
                password = String(pass)
                if (credentials.isPasswordPersistent) {
                    JabberActivator.protocolProviderFactory.storePassword(accID, password)
                }
            }
            val autoHarvester = namSer.discoverStunServer(accID.service,
                username.toByteArray(StandardCharsets.UTF_8),
                password.toByteArray(StandardCharsets.UTF_8))
            Timber.i("Auto discovered STUN/TURN-server harvester: %s", autoHarvester)
            if (autoHarvester != null) {
                atLeastOneStunServer = true
                agent.addCandidateHarvester(autoHarvester)
            }
        }

        // now create stun server descriptors for whatever other STUN/TURN servers the user may have set.
        // cmeng: added to support other protocol (20200428)
        // see https://github.com/MilanKral/atalk-android/commit/d61d5165dda4d290280ebb3e93075e8846e255ad
        for (desc in accID.stunServers) {
            val transport = when (val protocol = desc.protocol) {
                StunServerDescriptor.PROTOCOL_UDP -> Transport.UDP
                StunServerDescriptor.PROTOCOL_TCP -> Transport.TCP
                StunServerDescriptor.PROTOCOL_DTLS -> Transport.DTLS
                StunServerDescriptor.PROTOCOL_TLS -> Transport.TLS
                else -> {
                    Timber.w("Unknown protocol %s", protocol)
                    Transport.UDP
                }
            }
            for (addr in getTransportAddress(desc.address, desc.port, transport)) {
                // if we get STUN server from automatic discovery, it may just be server name
                // (i.e. stun.domain.org), and it may be possible that it cannot be resolved
                if (addr.address == null) {
                    Timber.i("Unresolved STUN server address for %s", addr)
                    continue
                }
                val harvester = if (desc.isTurnSupported) {
                    // this is a TURN server
                    TurnCandidateHarvester(addr, LongTermCredential(desc.getUsername(), desc.getPassword()))
                }
                else {
                    // this is a STUN only server
                    StunCandidateHarvester(addr)
                }
                Timber.i("Adding pre-configured harvester %s", harvester)
                atLeastOneStunServer = true
                agent.addCandidateHarvester(harvester)
            }
        }

        // Found no configured or discovered STUN server so takes default stunServers provided if user allows it
        if (!atLeastOneStunServer && accID.isUseDefaultStunServer) {
            for (stunServer in stunServers) {
                val hostPort = stunServer.split(":")
                for (addr in getTransportAddress(hostPort[0], hostPort[1].toInt(), Transport.UDP)) {
                    agent.addCandidateHarvester(StunCandidateHarvester(addr))
                    atLeastOneStunServer = true
                }

                // Skip the rest if one has set up successfully
                if (atLeastOneStunServer) break
            }
        }

        /* Jingle nodes candidate */
        if (accID.isJingleNodesRelayEnabled()) {
            /*
             * this method is blocking until Jingle Nodes auto-discovery (if enabled) finished
             */
            val serviceNode = provider.jingleNodesServiceNode
            if (serviceNode != null) {
                agent.addCandidateHarvester(JingleNodesHarvester(serviceNode))
            }
        }
        if (accID.isUPNPEnabled) {
            agent.addCandidateHarvester(UPNPHarvester())
        }
        val stopGatheringHarvesterTime = System.currentTimeMillis()
        val gatheringHarvesterTime = stopGatheringHarvesterTime - startGatheringHarvesterTime
        Timber.i("End gathering harvesters within %d ms size: %s Harvesters:\n%s",
            gatheringHarvesterTime, agent.harvesters.size, agent.harvesters)
        return agent
    }

    /**
     * Generate a list of TransportAddress from the given hostname, port and transport.
     * The given host name is resolved into both IPv4 and IPv6 InetAddresses.
     *
     * Note: android InetAddress.getByName(hostname) returns the first IP found, any may be an IPv6 InetAddress
     * if mobile network setting for APN=IPV4/IPv6 or APN=IPv6. This causes problem in STUN candidate harvest:
     *
     * @param hostname the address itself
     * @param port the port number
     * @param transport the transport to use with this address.
     * @see https://github.com/jitsi/ice4j/issues/255
     */
    private fun getTransportAddress(hostname: String?, port: Int, transport: Transport?): List<TransportAddress> {
        val transportAddress = ArrayList<TransportAddress>()
        try {
            // return all associated InetAddress in both IPv4 and IPv6 address
            val inetAddresses = InetAddress.getAllByName(hostname)
            for (inetAddress in inetAddresses) {
                transportAddress.add(TransportAddress(inetAddress, port, transport))
            }
        } catch (e: UnknownHostException) {
            Timber.e("UnknownHostException: %s", e.message)
        }
        return transportAddress
    }

    /**
     * {@inheritDoc}
     */
    @Throws(OperationFailedException::class)
    override fun doCreateStreamConnector(mediaType: MediaType): StreamConnector? {
        /*
         * If this instance is participating in a telephony conference utilizing the Jitsi
         * Videobridge server-side technology that is organized by the local peer, then there is a
         * single MediaStream (of the specified mediaType) shared among multiple TransportManagers
         * and its StreamConnector may be determined only by the TransportManager which is
         * establishing the connectivity with the Jitsi Videobridge server (as opposed to a CallPeer).
         */
        val delegate = findTransportManagerEstablishingConnectivityWithJitsiVideobridge()
        if (delegate != null && delegate != this)
            return delegate.doCreateStreamConnector(mediaType)
        val streamConnectorSockets = getStreamConnectorSockets(mediaType)

        /*
         * XXX If the iceAgent has not completed (yet), go with a default StreamConnector (until it completes).
         */
        return if (streamConnectorSockets == null) super.doCreateStreamConnector(mediaType) else DefaultStreamConnector(streamConnectorSockets[0], streamConnectorSockets[1])
    }

    /**
     * Gets the `StreamConnector` to be used as the `connector` of the
     * `MediaStream` with a specific `MediaType` .
     *
     * @param mediaType the `MediaType` of the `MediaStream` which is to have its
     * `connector` set to the returned `StreamConnector`
     * @return the `StreamConnector` to be used as the `connector` of the
     * `MediaStream` with the specified `MediaType`
     * @throws OperationFailedException if anything goes wrong while initializing the requested `StreamConnector`
     * @see net.java.sip.communicator.service.protocol.media.TransportManager.getStreamConnector
     */
    @Throws(OperationFailedException::class)
    override fun getStreamConnector(mediaType: MediaType): StreamConnector {
        var streamConnector = super.getStreamConnector(mediaType)

        /*
         * Since the super caches the StreamConnectors, make sure that the returned one is up-to-date with the iceAgent.
         */
        if (streamConnector != null) {
            val streamConnectorSockets = getStreamConnectorSockets(mediaType)

            /*
             * XXX If the iceAgent has not completed (yet), go with the default StreamConnector (until it completes).
             */
            if (streamConnectorSockets != null
                    && (streamConnector.dataSocket != streamConnectorSockets[0]
                            || streamConnector.controlSocket != streamConnectorSockets[1])) {
                // Recreate the StreamConnector for the specified mediaType.
                closeStreamConnector(mediaType)
                streamConnector = super.getStreamConnector(mediaType)
            }
        }
        return streamConnector
    }

    /**
     * Gets an array of `DatagramSocket`s which represents the sockets to be used by the
     * `StreamConnector` with the specified `MediaType` in the order of
     * [.COMPONENT_IDS] if [.iceAgent] has completed.
     *
     * @param mediaType the `MediaType` of the `StreamConnector` for which the
     * `DatagramSocket`s are to be returned
     * @return an array of `DatagramSocket`s which represents the sockets to be used by the
     * `StreamConnector` which the specified `MediaType` in the order of
     * [.COMPONENT_IDS] if [.iceAgent] has completed otherwise, `null`
     */
    private fun getStreamConnectorSockets(mediaType: MediaType): Array<DatagramSocket?>? {
        // cmeng: aTalk remote video cannot receive if enabled even for ice4j-2.0
        // if (streamConnectorSockets != null) {
        //     return streamConnectorSockets
        // }
        val stream = iceAgent!!.getStream(mediaType.toString())
        if (stream != null) {
            if (isRtcpmux) {
                val component = stream.getComponent(Component.RTP)
                val componentSocket = component.socket ?: return null

                // ICE is not ready yet
                val streamConnectorSockets = arrayOfNulls<DatagramSocket>(2)
                try {
                    streamConnectorSockets[0] = componentSocket.getSocket(RTP_FILTER)
                    streamConnectorSockets[1] = componentSocket.getSocket(RTCP_FILTER)
                } catch (e: Exception) {
                    Timber.e("Failed to create filtered sockets.")
                    return null
                }
                return streamConnectorSockets.also { this.streamConnectorSockets = it }
            }
            else {
                val streamConnectorSockets = arrayOfNulls<DatagramSocket>(COMPONENT_IDS.size)
                var streamConnectorSocketCount = 0
                for (i in COMPONENT_IDS.indices) {
                    val component = stream.getComponent(COMPONENT_IDS[i])
                    if (component != null) {
                        val streamConnectorSocket = component.socket
                        if (streamConnectorSocket != null) {
                            streamConnectorSockets[i] = streamConnectorSocket
                            streamConnectorSocketCount++
                            Timber.log(TimberLog.FINER, "Added a streamConnectorSocket to StreamConnectorSocket list"
                                    + " and increase streamConnectorSocketCount to %s", streamConnectorSocketCount)
                        }
                        // }
                    }
                }
                if (streamConnectorSocketCount > 0) {
                    return streamConnectorSockets.also { this.streamConnectorSockets = it }
                }
            }
        }
        return null
    }

    /**
     * Implements [TransportManagerJabberImpl.getStreamTarget]. Gets the `MediaStreamTarget`
     * to be used as the `target` of the `MediaStream` with a specific `MediaType`.
     *
     * @param mediaType the `MediaType` of the `MediaStream` which is to have its
     * `target` set to the returned `MediaStreamTarget`
     * @return the `MediaStreamTarget` to be used as the `target` of the
     * `MediaStream` with the specified `MediaType`
     * @see TransportManagerJabberImpl.getStreamTarget
     */
    override fun getStreamTarget(mediaType: MediaType): MediaStreamTarget? {
        /*
         * If this instance is participating in a telephony conference utilizing the Jitsi
         * Videobridge server-side technology that is organized by the local peer, then there is a
         * single MediaStream (of the specified mediaType) shared among multiple TransportManagers
         * and its MediaStreamTarget may be determined only by the TransportManager which is
         * establishing the connectivity with the Jitsi Videobridge server (as opposed to a CallPeer).
         */
        val delegate = findTransportManagerEstablishingConnectivityWithJitsiVideobridge()
        if (delegate != null && delegate != this)
            return delegate.getStreamTarget(mediaType)
        val stream = iceAgent!!.getStream(mediaType.toString())
        var streamTarget: MediaStreamTarget? = null

        if (stream != null) {
            val streamTargetAddresses = arrayOfNulls<InetSocketAddress>(COMPONENT_IDS.size)
            var streamTargetAddressCount = 0

            for (i in COMPONENT_IDS.indices) {
                val component = stream.getComponent(COMPONENT_IDS[i])
                if (component != null) {
                    val selectedPair = component.selectedPair
                    if (selectedPair != null) {
                        val streamTargetAddress = selectedPair.remoteCandidate.transportAddress
                        if (streamTargetAddress != null) {
                            streamTargetAddresses[i] = streamTargetAddress
                            streamTargetAddressCount++
                        }
                    }
                }
            }

            if (isRtcpmux) {
                streamTargetAddresses[1] = streamTargetAddresses[0]
                streamTargetAddressCount++
            }

            if (streamTargetAddressCount > 0) {
                streamTarget = MediaStreamTarget(streamTargetAddresses[0]!!, streamTargetAddresses[1]!!)
            }
        }
        return streamTarget
    }

    /**
     * Implements TransportManagerJabberImpl.getXmlNamespace. Gets the XML namespace of
     * the Jingle transport implemented by this `TransportManagerJabberImpl`.
     *
     * @return the XML namespace of the Jingle transport implemented by this `TransportManagerJabberImpl`
     * @see TransportManagerJabberImpl.xmlNamespace
     */
    override val xmlNamespace: String
        get() = ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_ICE_UDP_1

    /**
     * {@inheritDoc}
     *
     * Both the transport-info attributes i.e. ufrag and pwd must be set for IceUdpTransport by default
     * In case there are child elements other than candidates e.g. DTLS fingerPrint
     */
    override fun createTransportPacketExtension(): ExtensionElement {
        val tpBuilder = getBuilder()
            .setUfrag(iceAgent!!.localUfrag)
            .setPassword(iceAgent.localPassword)
        return tpBuilder.build()
    }

    /**
     * {@inheritDoc}
     */
    @Throws(OperationFailedException::class)
    override fun startCandidateHarvest(
            theirContent: JingleContent?, ourContent: JingleContent,
            transportInfoSender: TransportInfoSender?, media: String,
    ): ExtensionElement {
        val pe: ExtensionElement?

        // Report the gathered candidate addresses.
        if (transportInfoSender == null) {
            pe = createTransportForStartCandidateHarvest(media)
        }
        else {
            /*
             * The candidates will be sent in transport-info so the transport of session-accept just
             * has to be present, not populated with candidates.
             */
            pe = createTransportPacketExtension()

            /*
             * Create the content to be sent in a transport-info. The transport is the only
             * extension to be sent in transport-info so the content has the same attributes as in
             * our answer and none of its non-transport extensions.
             */
            val content = JingleContent.getBuilder()
            for (name in ourContent.attributes.keys) {
                val value = ourContent.getAttributeValue(name)
                if (value != null) content.addAttribute(name, value)
            }
            content.addChildElement(createTransportForStartCandidateHarvest(media))

            /*
             * We send each media content in separate transport-info. It is absolutely not mandatory
             * (we can simply send all content in one transport-info) but the XMPP Jingle client
             * Empathy (via telepathy-gabble), which is present on many Linux distributions and N900
             * mobile phone, has a bug when it receives more than one content in transport-info. The
             * related bug has been fixed in mainstream but the Linux distributions have not updated
             * their packages yet. That's why we made this modification to be fully interoperable
             * with Empathy right now. In the future, we will get back to the original behavior:
             * sending all content in one transport-info.
             */
            val transportInfoContents = LinkedList<JingleContent>()
            transportInfoContents.add(content.build())
            transportInfoSender.sendTransportInfo(transportInfoContents)
        }
        return pe!!
    }

    /**
     * Starts transport candidate harvest. This method should complete rapidly and, in case of lengthy procedures
     * like STUN/TURN/UPnP candidate harvests are necessary, they should be executed in a separate thread.
     * Candidate harvest would then need to be concluded in the [.wrapupCandidateHarvest] method which
     * would be called once we absolutely need the candidates.
     *
     * @param theirOffer a media description offer that we've received from the remote party and that we should
     * use in case we need to know what transports our peer is using.
     * @param ourAnswer the content descriptions that we should be adding our transport lists to (although not
     * necessarily in this very instance).
     * @param transportInfoSender the `TransportInfoSender` to be used by this
     * `TransportManagerJabberImpl` to send `transport-info` `Jingle`s
     * from the local peer to the remote peer if this `TransportManagerJabberImpl`
     * wishes to utilize `transport-info`. Local candidate addresses sent by this
     * `TransportManagerJabberImpl` in `transport-info` are expected to not be
     * included in the result of [.wrapupCandidateHarvest].
     * @throws OperationFailedException if we fail to allocate a port number.
     * @see TransportManagerJabberImpl.startCandidateHarvest
     */
    @Throws(OperationFailedException::class)
    override fun startCandidateHarvest(
            theirOffer: List<JingleContent>?,
            ourAnswer: MutableList<JingleContent>, transportInfoSender: TransportInfoSender?,
    ) {
        // Timber.w(new Exception("CPE list updated"))
        cpeList = ourAnswer
        super.startCandidateHarvest(theirOffer, ourAnswer, transportInfoSender)
    }

    /**
     * Converts the ICE media `stream` and its local candidates into a
     * [IceUdpTransport].
     *
     * @param stream the [IceMediaStream] that we'd like to describe in XML.
     * @return the [IceUdpTransport] that we
     */
    private fun createTransport(stream: IceMediaStream): ExtensionElement {
        val iceAgent = stream.parentAgent
        val tpBuilder = getBuilder()
            .setUfrag(iceAgent.localUfrag)
            .setPassword(iceAgent.localPassword)

        /*
         * @see RtcpMux per XEP-0167: Jingle RTP Sessions 1.2.1 (2020-09-29) https://xmpp.org/extensions/xep-0167.html#format
         *
         * This is a patch for jitsi and is non XEP standard: may want to remove once jitsi has updated
         * still required on 2.11.5633. Jitsi works only on the audio but no video call (only local video)
         * In aTalk: rtcp-mux will re-align itself to jitsi rtcp-mux mode after first call from jitsi i.e. false.
         */
        if (isRtcpmux) {
            tpBuilder.addChildElement(RtcpMux.builder(NAMESPACE).build())
        }
        for (component in stream.components) {
            for (candidate in component.localCandidates) tpBuilder.addChildElement(createCandidate(candidate))
        }
        return tpBuilder.build()
    }

    /**
     * {@inheritDoc}
     */
    @Throws(OperationFailedException::class)
    override fun createTransport(media: String): ExtensionElement {
        var iceStream = iceAgent!!.getStream(media)
        if (iceStream == null) iceStream = createIceStream(media)
        return createTransport(iceStream)
    }

    /**
     * Creates a [IceUdpTransportCandidate] and initializes it so that it would describe the
     * state of `candidate`
     *
     * @param candidate the ICE4J [Candidate] that we'd like to convert into an XMPP packet extension.
     * @return a new [IceUdpTransportCandidate] corresponding to the state of the `candidate` candidate.
     */
    private fun createCandidate(candidate: Candidate<*>): IceUdpTransportCandidate {
        val cBuilder = IceUdpTransportCandidate.getBuilder()
        cBuilder.setFoundation(candidate.foundation)
        val component = candidate.parentComponent
        val generation = cBuilder.setComponent(component.componentID)
            .setProtocol(candidate.transport.toString())
            .setPriority(candidate.priority)
            .setGeneration(component.parentStream.parentAgent.generation)
        val transportAddress = candidate.transportAddress
        cBuilder.setID(nextID)
            .setIP(transportAddress.hostAddress)
            .setPort(transportAddress.port)
            .setType(CandidateType.valueOf(candidate.type.toString()))
        val relAddr = candidate.relatedAddress
        if (relAddr != null) {
            cBuilder.setRelAddr(relAddr.hostAddress)
                .setRelPort(relAddr.port)
        }
        /*
         * FIXME The XML schema of XEP-0176: Jingle ICE-UDP Transport Method specifies the network attribute as required.
         */
        cBuilder.setNetwork(0)
        return cBuilder.build()
    }

    /**
     * Creates an [IceMediaStream] with the specified `media` name.
     *
     * @param media the name of the stream we'd like to create.
     * @return the newly created [IceMediaStream]
     * @throws OperationFailedException if binding on the specified media stream fails for some reason.
     */
    @Throws(OperationFailedException::class)
    private fun createIceStream(media: String): IceMediaStream {
        val stream: IceMediaStream?
        val portTracker: PortTracker
        Timber.d("Created Ice stream agent for %s", media)
        try {
            portTracker = getPortTracker(media)
            // the following call involves STUN processing so it may take a while
            stream = getNetAddrMgr()!!.createIceStream(if (isRtcpmux) 1 else 2, portTracker.port, media, iceAgent)
        } catch (ex: Exception) {
            throw OperationFailedException("Failed to initialize stream $media",
                OperationFailedException.INTERNAL_ERROR, ex)
        }

        // Attempt to minimize subsequent bind retries: see if we have allocated
        // any ports from the dynamic range, and if so update the port tracker.
        // Do NOT update the port tracker with non-dynamic ports (e.g. 4443
        // coming from TCP) because this will force it to revert back it its
        // configured min port. When maxPort is reached, allocation will begin
        // from minPort again, so we don't have to worry about wraps.
        try {
            val maxAllocatedPort = getMaxAllocatedPort(stream!!, portTracker.minPort, portTracker.maxPort)
            if (maxAllocatedPort > 0) {
                val nextPort = 1 + maxAllocatedPort
                portTracker.setNextPort(nextPort)
                Timber.d("Updating the port tracker min port: %s", nextPort)
            }
        } catch (t: Throwable) {
            //hey, we were just trying to be nice. if that didn't work for
            //some reason we really can't be held responsible!
            Timber.d(t, "Determining next port didn't work.")
        }
        return stream!!
    }

    /**
     * @return the highest local port used by any of the local candidates of
     * `iceStream`, which falls in the range [`min`, `max`].
     */
    private fun getMaxAllocatedPort(iceStream: IceMediaStream, min: Int, max: Int): Int {
        return max(
            getMaxAllocatedPort(iceStream.getComponent(Component.RTP), min, max),
            getMaxAllocatedPort(iceStream.getComponent(Component.RTCP), min, max))
    }

    /**
     * @return the highest local port used by any of the local candidates of
     * `component`, which falls in the range [`min`, `max`].
     */
    private fun getMaxAllocatedPort(component: Component?, min: Int, max: Int): Int {
        var maxAllocatedPort = -1
        if (component != null) {
            for (candidate in component.localCandidates) {
                val candidatePort = candidate.transportAddress.port
                if (candidatePort in min..max && maxAllocatedPort < candidatePort) {
                    maxAllocatedPort = candidatePort
                }
            }
        }
        return maxAllocatedPort
    }

    /**
     * Simply returns the list of local candidates that we gathered during the harvest.
     *
     * @return the list of local candidates that we gathered during the harvest
     * @see TransportManagerJabberImpl.wrapupCandidateHarvest
     */
    override fun wrapupCandidateHarvest(): List<JingleContent> {
        return cpeList!!
    }

    /**
     * Starts the connectivity establishment of the associated ICE `Agent`.
     *
     * @param remote the collection of `JingleContent`s which represents the remote
     * counterpart of the negotiation between the local and the remote peers
     * @return `true` if connectivity establishment has been started in response to the call
     * otherwise, `false`
     * @see TransportManagerJabberImpl.startConnectivityEstablishment
     */
    @Synchronized
    @Throws(OperationFailedException::class)
    override fun startConnectivityEstablishment(remote: Iterable<JingleContent>?): Boolean {
        // Timber.w(new Exception("start Connectivity Establishment"))
        val mediaTransports = LinkedHashMap<String, IceUdpTransport>()
        for (content in remote!!) {
            val transport = content.getFirstChildElement(IceUdpTransport::class.java)
            /*
             * If we cannot associate an IceMediaStream with the remote content, we will not have
             * anything to add the remote candidates to.
             */
            var description = content.getFirstChildElement(RtpDescription::class.java)
            if (description == null && cpeList != null) {
                val localContent = findContentByName(cpeList!!, content.name)
                if (localContent != null) {
                    description = localContent.getFirstChildElement(RtpDescription::class.java)
                }
            }
            if (description != null) {
                val media = description.media
                mediaTransports[media] = transport
                // Timber.d("### Processing Jingle IQ (transport-info) media map add: %s (%s)",
                // media, transport.getFirstChildElement(IceUdpTransportCandidate.class).toXML())
            }
        }

        /*
         * When the local peer is organizing a telephony conference using the Jitsi Videobridge
         * server-side technology, it is establishing connectivity by using information from a
         * colibri Channel and not from the offer/answer of the remote peer.
         */
        return if (getCallPeer()!!.isJitsiVideobridge) {
            sendTransportInfoToJitsiVideobridge(mediaTransports)
            false
        }
        else {
            val status = startConnectivityEstablishment(mediaTransports)
            Timber.d("### Processed Jingle (transport-info) for media: %s startConnectivityEstablishment: %s",
                mediaTransports.keys, status)
            status
        }
    }

    /**
     * Starts the connectivity establishment of the associated ICE `Agent`.
     *
     * @param remote a `Map` of media-`IceUdpTransport` pairs which represents
     * the remote counterpart of the negotiation between the local and the remote peers
     * @return `true` if connectivity establishment has been started in response to the call
     * otherwise, `false`
     * @see TransportManagerJabberImpl.startConnectivityEstablishment
     */
    @Synchronized
    override fun startConnectivityEstablishment(remote: MutableMap<String, IceUdpTransport>): Boolean {
        /*
         * If ICE is running already, we try to update the checklists with the candidates.
         * Note that this is a best effort.
         */
        // Timber.w("Ice Agent in used: %s", iceAgent)
        val iceAgentStateIsRunning = IceProcessingState.RUNNING == iceAgent!!.state
        if (iceAgentStateIsRunning) Timber.i("Updating ICE remote candidates")
        val generation = iceAgent.generation
        var startConnectivityEstablishment = false

        for ((media, transport) in remote) {
            val candidates = transport.getChildElements(IceUdpTransportCandidate::class.java)
            if (iceAgentStateIsRunning && candidates.isEmpty()) {
                Timber.i("Connectivity establishment has not been started because candidate list is empty")
                return false
            }
            val stream = iceAgent.getStream(media)
            if (stream == null) {
                Timber.w("No ICE media stream for media: %s (%s)", media, iceAgent.streams)
                continue
            }

            // Sort the remote candidates (host < reflexive < relayed) in order to create first the
            // host, then the reflexive, the relayed candidates and thus be able to set the
            // relative-candidate matching the rel-addr/rel-port attribute.
            candidates.sorted()

            // Different stream may have different ufrag/passwordProcess valid component candidate
            val ufrag = transport.ufrag
            if (ufrag != null) stream.remoteUfrag = ufrag
            val password = transport.password
            if (password != null) stream.remotePassword = password

            for (candidate in candidates) {
                /*
                 * Is the remote candidate from the current generation of the iceAgent?
                 */
                if (candidate.generation != generation) continue
                if (candidate.ip == null || "" == candidate.ip) {
                    Timber.w("Skipped ICE candidate with empty IP")
                    continue
                }
                var relAddr: String?
                var relPort = -1
                var relatedAddress: TransportAddress? = null
                if (candidate.relAddr.also { relAddr = it } != null && candidate.relPort.also { relPort = it } != -1) {
                    relatedAddress = TransportAddress(relAddr, relPort, Transport.parse(candidate.protocol))
                }

                // must check for null else NPE in component.findRemoteCandidate()
                val component = stream.getComponent(candidate.component)
                if (component != null) {
                    // Timber.d("Process valid component candidate type: %s: %s", media, iceAgent.getStream(media))// candidate.toXML())
                    val relatedCandidate = component.findRemoteCandidate(relatedAddress)
                    val remoteCandidate = RemoteCandidate(TransportAddress(
                        candidate.ip, candidate.port, Transport.parse(candidate.protocol)), component,
                        org.ice4j.ice.CandidateType.parse(candidate.type.toString()),
                        candidate.foundation, candidate.priority.toLong(), relatedCandidate)
                    if (iceAgentStateIsRunning) {
                        component.addUpdateRemoteCandidates(remoteCandidate)
                    }
                    else {
                        component.addRemoteCandidate(remoteCandidate)
                        startConnectivityEstablishment = true
                    }
                }
                else {
                    // Conversations sends single candidate with each transport, and sends component 1 and 2
                    // for RTP and RCTP even with <rtcp-mux/> specified So just skip and continue with next
                    // aTalk support <rtcp-mux/>.
                    Timber.w("Skip invalid component candidate: %s", candidate.toXML())
                }
            }
        }
        if (iceAgentStateIsRunning) {
            // update all components of all streams
            for (stream in iceAgent.streams) {
                for (component in stream.components) component.updateRemoteCandidates()
            }
        }
        else if (startConnectivityEstablishment) {
            /*
             * Once again because the ICE Agent does not support adding candidates after the
             * connectivity establishment has been started and because multiple transport-info
             * JingleIQs may be used to send the whole set of transport candidates from the remote
             * peer to the local peer, do not really start the connectivity establishment until we
             * have at least one remote candidate per ICE Component i.e. audio.RTP, audio.RTCP,
             * video.RTP & video.RTCP.
             */
            for (stream in iceAgent.streams) {
                for (component in stream.components) {
                    if (component.remoteCandidateCount < 1) {
                        Timber.d("### Insufficient remote candidates to startConnectivityEstablishment! %s: %s %s",
                            component.toShortString(), component.remoteCandidateCount, iceAgent.streams)
                        startConnectivityEstablishment = false
                        break
                    }
                }
                if (!startConnectivityEstablishment) break
            }
            if (startConnectivityEstablishment) {
                iceAgent.startConnectivityEstablishment()
                return true
            }
        }
        return false
    }

    /**
     * Waits for the associated ICE `Agent` to finish any started connectivity checks.
     *
     * @throws OperationFailedException if ICE processing has failed
     * @see TransportManagerJabberImpl.wrapupConnectivityEstablishment
     */
    @Throws(OperationFailedException::class)
    override fun wrapupConnectivityEstablishment() {
        val delegate = findTransportManagerEstablishingConnectivityWithJitsiVideobridge()
        if (delegate == null || delegate === this) {
            val iceProcessingStateSyncRoot = Any()
            val stateChangeListener = object : PropertyChangeListener {
                override fun propertyChange(evt: PropertyChangeEvent) {
                    val iceAgent = evt.source as Agent
                    if (iceAgent.isOver) {
                        Timber.d("Current IceProcessingState: %s", evt.newValue)
                        iceAgent.removeStateChangeListener(this)
                        if (iceAgent === this@IceUdpTransportManager.iceAgent) {
                            synchronized(iceProcessingStateSyncRoot) { iceProcessingStateSyncRoot.notify() }
                        }
                    }
                }
            }
            iceAgent!!.addStateChangeListener(stateChangeListener)

            /*
             * Wait for the ICE connectivity checks to complete if they have started or
             * waiting for transport info with max TOT of 5S.
             */
            var interrupted = false
            var maxWaitTimer = 5 // in seconds
            synchronized(iceProcessingStateSyncRoot) {
                while (IceProcessingState.RUNNING == iceAgent.state || IceProcessingState.WAITING == iceAgent.state) {
                    try {
                        (iceProcessingStateSyncRoot as Object).wait(1000)
                    } catch (ie: InterruptedException) {
                        interrupted = true
                    }
                    // Break the loop if maxWaitTimer timeout
                    if (maxWaitTimer-- < 0) break
                }
            }
            if (interrupted) Thread.currentThread().interrupt()
            /*
             * Make sure stateChangeListener is removed from iceAgent in case its
             * #propertyChange(PropertyChangeEvent) has never been executed.
             */
            iceAgent.removeStateChangeListener(stateChangeListener)
            /* check the state of ICE processing and throw exception if failed */
            if (IceProcessingState.FAILED == iceAgent.state) {
                val msg = aTalkApp.getResString(R.string.service_protocol_ICE_FAILED)
                throw OperationFailedException(msg, OperationFailedException.GENERAL_ERROR)
            }
        }
        else {
            delegate.wrapupConnectivityEstablishment()
        }

        /*
         * Once we're done establishing connectivity, we shouldn't be sending any more candidates
         * because we will not be able to perform connectivity checks for them.
         * Besides, they must have been sent in transport-info already.
         *
         * cmeng 2020529: Do not remove attributes UFRAG_ATTR_NAME and PWD_ATTR_NAME if
         * transport-info contains child elements e.g. DTLS FingerPrint
         */
        if (cpeList != null) {
            for (content in cpeList!!) {
                content.getFirstChildElement(IceUdpTransport::class.java)?.removeCandidate(IceUdpTransportCandidate())
            }
        }
    }

    /**
     * Removes a content with a specific name from the transport-related part of the session
     * represented by this `TransportManagerJabberImpl` which may have been reported through
     * previous calls to the `startCandidateHarvest` and
     * `startConnectivityEstablishment` methods.
     *
     * @param name the name of the content to be removed from the transport-related part of the session
     * represented by this `TransportManagerJabberImpl`
     * @see TransportManagerJabberImpl.removeContent
     */
    override fun removeContent(name: String) {
        val content = removeContent(cpeList!!, name)
        if (content != null) {
            val rtpDescription = content.getFirstChildElement(RtpDescription::class.java)
            if (rtpDescription != null) {
                val stream = iceAgent!!.getStream(rtpDescription.media)
                if (stream != null) iceAgent.removeStream(stream)
            }
        }
    }

    /**
     * Close this transport manager and release resources. In case of ICE, it releases Ice4j's Agent
     * that will cleanup all streams, component and close every candidate's sockets.
     */
    @Synchronized
    override fun close() {
        if (iceAgent != null) {
            iceAgent.removeStateChangeListener(this)
            iceAgent.free()
        }
    }

    /**
     * Returns the extended type of the candidate selected if this transport manager is using ICE.
     *
     * @param streamName The stream name (AUDIO, VIDEO)
     * @return The extended type of the candidate selected if this transport manager is using ICE.
     * Otherwise, returns null.
     */
    override fun getICECandidateExtendedType(streamName: String?): String? {
        return getICECandidateExtendedType(iceAgent, streamName)
    }

    /**
     * Returns the current state of ICE processing.
     *
     * @return the current state of ICE processing.
     */
    override fun getICEState(): String {
        return iceAgent!!.state.toString()
    }

    /**
     * Returns the ICE local host address.
     *
     * @param streamName The stream name (AUDIO, VIDEO)
     * @return the ICE local host address if this transport manager is using ICE. Otherwise, returns null.
     */
    override fun getICELocalHostAddress(streamName: String?): InetSocketAddress? {
        if (iceAgent != null) {
            val localCandidate = iceAgent.getSelectedLocalCandidate(streamName)
            if (localCandidate != null) return localCandidate.hostAddress
        }
        return null
    }

    /**
     * Returns the ICE remote host address.
     *
     * @param streamName The stream name (AUDIO, VIDEO)
     * @return the ICE remote host address if this transport manager is using ICE. Otherwise,
     * returns null.
     */
    override fun getICERemoteHostAddress(streamName: String?): InetSocketAddress? {
        if (iceAgent != null) {
            val remoteCandidate = iceAgent.getSelectedRemoteCandidate(streamName)
            if (remoteCandidate != null) return remoteCandidate.hostAddress
        }
        return null
    }

    /**
     * Returns the ICE local reflexive address (server or peer reflexive).
     *
     * @param streamName The stream name (AUDIO, VIDEO)
     * @return the ICE local reflexive address. May be null if this transport manager is not using
     * ICE or if there is no reflexive address for the local candidate used.
     */
    override fun getICELocalReflexiveAddress(streamName: String?): InetSocketAddress? {
        if (iceAgent != null) {
            val localCandidate = iceAgent.getSelectedLocalCandidate(streamName)
            if (localCandidate != null) return localCandidate.reflexiveAddress
        }
        return null
    }

    /**
     * Returns the ICE remote reflexive address (server or peer reflexive).
     *
     * @param streamName The stream name (AUDIO, VIDEO)
     * @return the ICE remote reflexive address. May be null if this transport manager is not using
     * ICE or if there is no reflexive address for the remote candidate used.
     */
    override fun getICERemoteReflexiveAddress(streamName: String?): InetSocketAddress? {
        if (iceAgent != null) {
            val remoteCandidate = iceAgent.getSelectedRemoteCandidate(streamName)
            if (remoteCandidate != null) return remoteCandidate.reflexiveAddress
        }
        return null
    }

    /**
     * Returns the ICE local relayed address (server or peer relayed).
     *
     * @param streamName The stream name (AUDIO, VIDEO)
     * @return the ICE local relayed address. May be null if this transport manager is not using ICE
     * or if there is no relayed address for the local candidate used.
     */
    override fun getICELocalRelayedAddress(streamName: String?): InetSocketAddress? {
        if (iceAgent != null) {
            val localCandidate = iceAgent.getSelectedLocalCandidate(streamName)
            if (localCandidate != null) return localCandidate.relayedAddress
        }
        return null
    }

    /**
     * Returns the ICE remote relayed address (server or peer relayed).
     *
     * @param streamName The stream name (AUDIO, VIDEO)
     * @return the ICE remote relayed address. May be null if this transport manager is not using
     * ICE or if there is no relayed address for the remote candidate used.
     */
    override fun getICERemoteRelayedAddress(streamName: String?): InetSocketAddress? {
        if (iceAgent != null) {
            val remoteCandidate = iceAgent.getSelectedRemoteCandidate(streamName)
            if (remoteCandidate != null) return remoteCandidate.relayedAddress
        }
        return null
    }

    /**
     * Returns the total harvesting time (in ms) for all harvesters.
     *
     * @return The total harvesting time (in ms) for all the harvesters. 0 if the ICE agent is null,
     * or if the agent has never harvested.
     */
    override fun getTotalHarvestingTime(): Long {
        return iceAgent?.totalHarvestingTime ?: 0
    }

    /**
     * Returns the harvesting time (in ms) for the harvester given in parameter.
     *
     * @param harvesterName The class name if the harvester.
     * @return The harvesting time (in ms) for the harvester given in parameter. 0 if this harvester
     * does not exists, if the ICE agent is null, or if the agent has never harvested with this harvester.
     */
    override fun getHarvestingTime(harvesterName: String?): Long {
        return iceAgent?.getHarvestingTime(harvesterName) ?: 0
    }

    /**
     * Returns the number of harvesting for this agent.
     *
     * @return The number of harvesting for this agent.
     */
    override fun getNbHarvesting(): Int {
        return iceAgent?.harvestCount ?: 0
    }

    /**
     * Returns the number of harvesting time for the harvester given in parameter.
     *
     * @param harvesterName The class name if the harvester.
     * @return The number of harvesting time for the harvester given in parameter.
     */
    override fun getNbHarvesting(harvesterName: String?): Int {
        return iceAgent?.getHarvestCount(harvesterName) ?: 0
    }

    /**
     * Retransmit state change events from the Agent to the media handler.
     *
     * @param evt the event for state change.
     */
    override fun propertyChange(evt: PropertyChangeEvent) {
        getCallPeer()!!.mediaHandler.firePropertyChange(evt.propertyName, evt.oldValue, evt.newValue)
    }

    companion object {
        /**
         * The ICE `Component` IDs in their common order used, for example,
         * by `DefaultStreamConnector`, `MediaStreamTarget`.
         */
        private val COMPONENT_IDS = intArrayOf(Component.RTP, Component.RTCP)

        /**
         * A filter which accepts any non-RTCP packets (RTP, DTLS, etc).
         */
        private val RTP_FILTER = DatagramPacketFilter { p ->
            !RTCP_FILTER.accept(p)
        }

        /**
         * A filter which accepts RTCP packets.
         */
        private val RTCP_FILTER = DatagramPacketFilter { p: DatagramPacket? ->
            if (p == null) {
                return@DatagramPacketFilter false
            }
            val buf = p.data
            val off = p.offset
            val len = p.length
            if (buf == null || len < 8 || off + len > buf.size) {
                return@DatagramPacketFilter false
            }
            val version = buf[off].toInt() and 0xC0 ushr 6
            if (version != 2) {
                return@DatagramPacketFilter false
            }
            val pt = buf[off + 1].toInt() and 0xff
            pt in 200..211
        }

        /**
         * Returns a reference to the [NetworkAddressManagerService]. The only reason this method
         * exists is that [#getNetworkAddressManagerService()][JabberActivator] is too long to
         * write and makes code look clumsy.
         *
         * @return a reference to the [NetworkAddressManagerService].
         */
        private fun getNetAddrMgr(): NetworkAddressManagerService? {
            return JabberActivator.getNetworkAddressManagerService()
        }
    }
}