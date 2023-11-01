/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.CallPeer
import net.java.sip.communicator.service.protocol.OperationFailedException
import org.atalk.service.neomedia.MediaStreamTarget
import org.atalk.service.neomedia.StreamConnector
import org.atalk.util.MediaType
import org.jivesoftware.smack.packet.ExtensionElement
import org.jivesoftware.smackx.jingle.element.JingleContent
import org.jivesoftware.smackx.jingle_rtp.CandidateType
import org.jivesoftware.smackx.jingle_rtp.JingleUtils
import org.jivesoftware.smackx.jingle_rtp.element.IceUdpTransportCandidate
import org.jivesoftware.smackx.jingle_rtp.element.RawUdpTransport
import org.jivesoftware.smackx.jingle_rtp.element.RtpDescription
import java.net.InetSocketAddress
import java.util.*

/**
 * A [TransportManagerJabberImpl] implementation that would only gather a single candidate
 * pair (i.e. RTP and RTCP).
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class RawUdpTransportManager
/**
 * Creates a new instance of this transport manager, binding it to the specified peer.
 *
 * @param callPeer the [CallPeer] whose traffic we will be taking care of.
 */
    (callPeer: CallPeerJabberImpl?) : TransportManagerJabberImpl(callPeer) {
    /**
     * The list of `JingleContent`s which represents the local counterpart of the
     * negotiation between the local and the remote peers.
     */
    private var local: List<JingleContent>? = null

    /**
     * The collection of `JingleContent`s which represents the remote counterpart of
     * the negotiation between the local and the remote peers.
     */
    private val remotes = LinkedList<Iterable<JingleContent>>()

    /**
     * {@inheritDoc}
     */
    @Throws(OperationFailedException::class)
    override fun createTransport(media: String): ExtensionElement {
        val mediaType = MediaType.parseString(media)
        return createTransport(mediaType, getStreamConnector(mediaType))
    }

    /**
     * Creates a raw UDP transport element according to a specific `StreamConnector`.
     *
     * @param mediaType the `MediaType` of the `MediaStream` which uses the specified
     * `connector` or `channel`
     * @param connector the `StreamConnector` to be described within the transport element
     * @return a [RawUdpTransport] containing the RTP and RTCP candidates of the specified `connector`
     */
    private fun createTransport(mediaType: MediaType, connector: StreamConnector?): RawUdpTransport {
        val tpBuilder = RawUdpTransport.getBuilder()
        val generation = currentGeneration

        // create and add candidates that correspond to the stream connector
        //=== RTP ===/
        val dataSocket = connector!!.dataSocket
        val tcpBuilder = IceUdpTransportCandidate.getBuilder()
        tcpBuilder.setComponent(IceUdpTransportCandidate.RTP_COMPONENT_ID)
                .setGeneration(generation)
                .setID(nextID)
                .setType(CandidateType.host)
                .setIP(dataSocket!!.localAddress.hostAddress)
                .setPort(dataSocket.localPort)
        tpBuilder.addChildElement(tcpBuilder.build())

        //=== RTCP ===/
        val controlSocket = connector.controlSocket
        val rtcpBuilder = IceUdpTransportCandidate.getBuilder()
                .setComponent(IceUdpTransportCandidate.RTCP_COMPONENT_ID)
                .setGeneration(generation)
                .setID(nextID)
                .setType(CandidateType.host)
                .setIP(controlSocket!!.localAddress.hostAddress)
                .setPort(controlSocket.localPort)
        tpBuilder.addChildElement(rtcpBuilder.build())
        return tpBuilder.build()
    }

    /**
     * {@inheritDoc}
     */
    override fun createTransportPacketExtension(): ExtensionElement {
        return RawUdpTransport()
    }

    /**
     * Implements [TransportManagerJabberImpl.getStreamTarget]. Gets the
     * `MediaStreamTarget` to be used as the `target` of the `MediaStream` with
     * a specific `MediaType`.
     *
     * @param mediaType the `MediaType` of the `MediaStream` which is to have its
     * `target` set to the returned `MediaStreamTarget`
     * @return the `MediaStreamTarget` to be used as the `target` of the
     * `MediaStream` with the specified `MediaType`
     * @see TransportManagerJabberImpl.getStreamTarget
     */
    override fun getStreamTarget(mediaType: MediaType): MediaStreamTarget? {
        val channel = getColibriChannel(mediaType, true /* local */)
        var streamTarget: MediaStreamTarget? = null
        if (channel == null) {
            val media = mediaType.toString()
            for (remote in remotes) {
                for (content in remote) {
                    val rtpDescription = content.getFirstChildElement(RtpDescription::class.java)
                    if (media == rtpDescription.media) {
                        streamTarget = JingleUtils.extractDefaultTarget(content)
                        break
                    }
                }
            }
        }
        else {
            val transport = channel.transport
            if (transport != null) streamTarget = JingleUtils.extractDefaultTarget(transport)
            if (streamTarget == null) {
                /*
                 * For the purposes of compatibility with legacy Jitsi Videobridge, support the
                 * channel attributes host, rtpPort and rtcpPort.
                 */
                val host = channel.host
                if (host != null) {
                    val rtpPort = channel.rtpPort
                    val rtcpPort = channel.rtcpPort
                    streamTarget = MediaStreamTarget(InetSocketAddress(host, rtpPort),
                        InetSocketAddress(host, rtcpPort))
                }
            }
        }
        return streamTarget
    }

    /**
     * Implements TransportManagerJabberImpl.getXmlNamespace. Gets the XML namespace of
     * the Jingle transport implemented by this `TransportManagerJabberImpl`.
     *
     * @return the XML namespace of the Jingle transport implemented by this `TransportManagerJabberImpl`
     * @see TransportManagerJabberImpl.getXmlNamespace
     */
    override val xmlNamespace: String
        get() = ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RAW_UDP_0

    /**
     * Removes a content with a specific name from the transport-related part of the session
     * represented by this `TransportManagerJabberImpl` which may have been reported through
     * previous calls to the `startCandidateHarvest` and `startConnectivityEstablishment` methods.
     *
     * @param name the name of the content to be removed from the transport-related part of the session
     * represented by this `TransportManagerJabberImpl`
     * @see TransportManagerJabberImpl.removeContent
     */
    override fun removeContent(name: String) {
        if (local != null) removeContent(local!!, name)
        removeRemoteContent(name)
    }

    /**
     * Removes a content with a specific name from the remote counterpart of the negotiation between
     * the local and the remote peers.
     *
     * @param name the name of the content to be removed from the remote counterpart of the negotiation
     * between the local and the remote peers
     */
    private fun removeRemoteContent(name: String) {
        val remoteIter = remotes.iterator()
        while (remoteIter.hasNext()) {
            val remote = remoteIter.next()

            /*
             * Once the remote content is removed, make sure that we are not retaining sets which do
             * not have any contents.
             */
            if (removeContent(remote, name) != null && !remote.iterator().hasNext()) {
                remoteIter.remove()
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Throws(OperationFailedException::class)
    override fun startCandidateHarvest(
            theirContent: JingleContent?,
            ourContent: JingleContent, transportInfoSender: TransportInfoSender?, media: String,
    ): ExtensionElement? {
        return createTransportForStartCandidateHarvest(media)
    }

    /**
     * Starts transport candidate harvest. This method should complete rapidly and, in case of
     * lengthy procedures like STUN/TURN/UPnP candidate harvests are necessary, they should be
     * executed in a separate thread. Candidate harvest would then need to be concluded in the
     * [.wrapupCandidateHarvest] method which would be called once we absolutely need the candidates.
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
        local = ourAnswer
        super.startCandidateHarvest(theirOffer, ourAnswer, transportInfoSender)
    }

    /**
     * Overrides the super implementation in order to remember the remote counterpart of the
     * negotiation between the local and the remote peer for subsequent calls to
     * [.getStreamTarget].
     *
     * @param remote the collection of `JingleContent`s which represents the remote
     * counterpart of the negotiation between the local and the remote peer
     * @return `true` because `RawUdpTransportManager` does not perform connectivity checks
     * @see TransportManagerJabberImpl.startConnectivityEstablishment
     */
    @Throws(OperationFailedException::class)
    override fun startConnectivityEstablishment(remote: Iterable<JingleContent>?): Boolean {
        if (remote != null && !remotes.contains(remote)) {
            /*
             * The state of the session in Jingle is maintained by each peer and is modified by
             * content-add and content-remove. The remotes field of this RawUdpTransportManager
             * represents the state of the session with respect to the remote peer. When the remote
             * peer tells us about a specific set of contents, make sure that it is the only record
             * we will have with respect to the specified set of contents.
             */
            for (content in remote) removeRemoteContent(content.name)
            remotes.add(remote)
        }
        return super.startConnectivityEstablishment(remote)
    }

    /**
     * Simply returns the list of local candidates that we gathered during the harvest. This is a
     * raw UDP transport manager so there's no real wrapping up to do.
     *
     * @return the list of local candidates that we gathered during the harvest
     * @see TransportManagerJabberImpl.wrapupCandidateHarvest
     */
    override fun wrapupCandidateHarvest(): List<JingleContent> {
        return local!!
    }

    /**
     * Returns the extended type of the candidate selected if this transport manager is using ICE.
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return The extended type of the candidate selected if this transport manager is using ICE. Otherwise, returns null.
     */
    override fun getICECandidateExtendedType(streamName: String?): String? {
        return null
    }

    /**
     * Returns the current state of ICE processing.
     *
     * @return the current state of ICE processing.
     */
    override fun getICEState(): String? {
        return null
    }

    /**
     * Returns the ICE local host address.
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE local host address if this transport manager is using ICE. Otherwise, returns null.
     */
    override fun getICELocalHostAddress(streamName: String?): InetSocketAddress? {
        return null
    }

    /**
     * Returns the ICE remote host address.
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE remote host address if this transport manager is using ICE. Otherwise, returns null.
     */
    override fun getICERemoteHostAddress(streamName: String?): InetSocketAddress? {
        return null
    }

    /**
     * Returns the ICE local reflexive address (server or peer reflexive).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE local reflexive address. May be null if this transport manager is not using
     * ICE or if there is no reflexive address for the local candidate used.
     */
    override fun getICELocalReflexiveAddress(streamName: String?): InetSocketAddress? {
        return null
    }

    /**
     * Returns the ICE remote reflexive address (server or peer reflexive).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE remote reflexive address. May be null if this transport manager is not using
     * ICE or if there is no reflexive address for the remote candidate used.
     */
    override fun getICERemoteReflexiveAddress(streamName: String?): InetSocketAddress? {
        return null
    }

    /**
     * Returns the ICE local relayed address (server or peer relayed).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE local relayed address. May be null if this transport manager is not using ICE
     * or if there is no relayed address for the local candidate used.
     */
    override fun getICELocalRelayedAddress(streamName: String?): InetSocketAddress? {
        return null
    }

    /**
     * Returns the ICE remote relayed address (server or peer relayed).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE remote relayed address. May be null if this transport manager is not using
     * ICE or if there is no relayed address for the remote candidate used.
     */
    override fun getICERemoteRelayedAddress(streamName: String?): InetSocketAddress? {
        return null
    }

    /**
     * Returns the total harvesting time (in ms) for all harvesters.
     *
     * @return The total harvesting time (in ms) for all the harvesters. 0 if the ICE agent is null,
     * or if the agent has nevers harvested.
     */
    override fun getTotalHarvestingTime(): Long {
        return 0
    }

    /**
     * Returns the harvesting time (in ms) for the harvester given in parameter.
     *
     * @param harvesterName The class name if the harvester.
     * @return The harvesting time (in ms) for the harvester given in parameter. 0 if this harvester
     * does not exists, if the ICE agent is null, or if the agent has never harvested with this harvester.
     */
    override fun getHarvestingTime(harvesterName: String?): Long {
        return 0
    }

    /**
     * Returns the number of harvesting for this agent.
     *
     * @return The number of harvesting for this agent.
     */
    override fun getNbHarvesting(): Int {
        return 0
    }

    /**
     * Returns the number of harvesting time for the harvester given in parameter.
     *
     * @param harvesterName The class name if the harvester.
     * @return The number of harvesting time for the harvester given in parameter.
     */
    override fun getNbHarvesting(harvesterName: String?): Int {
        return 0
    }

    /**
     * {@inheritDoc}
     */
    override var isRtcpmux = false
        set(rtcpmux) = require(!rtcpmux) { "rtcp mux not supported by " + javaClass.simpleName }

}