/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media

import net.java.sip.communicator.service.netaddr.NetworkAddressManagerService
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony
import net.java.sip.communicator.util.PortTracker
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.neomedia.DefaultStreamConnector
import org.atalk.service.neomedia.MediaStreamTarget
import org.atalk.service.neomedia.RawPacket
import org.atalk.service.neomedia.StreamConnector
import org.atalk.util.MediaType
import org.ice4j.ice.Agent
import org.ice4j.ice.IceMediaStream
import org.ice4j.ice.LocalCandidate
import timber.log.Timber
import java.net.*

/**
 * `TransportManager`s are responsible for allocating ports, gathering local candidates and
 * managing ICE whenever we are using it.
 *
 * @param <U> the peer extension class like for example `CallPeerSipImpl` or `CallPeerJabberImpl`
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
</U> */
abstract class TransportManager<U : MediaAwareCallPeer<*, *, *>?>
/**
 * Creates a new instance of this transport manager, binding it to the specified peer.
 *
 * @param callPeer the [MediaAwareCallPeer] whose traffic we will be taking care of.
 */
protected constructor(
        /**
         * The [MediaAwareCallPeer] whose traffic we will be taking care of.
         */
        private val callPeer: U) {
    /**
     * The RTP/RTCP socket couples that this `TransportManager` uses to send and receive
     * media flows through indexed by `MediaType` (ordinal).
     */
    private val streamConnectors: Array<StreamConnector?> = arrayOfNulls(MediaType.values().size)

    /**
     * Returns the `StreamConnector` instance that this media handler should use for streams
     * of the specified `mediaType`. The method would also create a new
     * `StreamConnector` if no connector has been initialized for this `mediaType` yet
     * or in case one of its underlying sockets has been closed.
     *
     * @param mediaType the `MediaType` that we'd like to create a connector for.
     * @return this media handler's `StreamConnector` for the specified `mediaType`.
     * @throws OperationFailedException in case we failed to initialize our connector.
     */
    @Throws(OperationFailedException::class)
    open fun getStreamConnector(mediaType: MediaType): StreamConnector {
        val streamConnectorIndex = mediaType.ordinal
        var streamConnector = streamConnectors[streamConnectorIndex]
        if (streamConnector == null || streamConnector.protocol == StreamConnector.Protocol.UDP) {
            var controlSocket: DatagramSocket?
            if (streamConnector == null || streamConnector.dataSocket!!.isClosed
                    || (streamConnector.controlSocket.also { controlSocket = it } != null
                            && controlSocket!!.isClosed)) {
                streamConnector = createStreamConnector(mediaType)
                streamConnectors[streamConnectorIndex] = streamConnector
            }
        } else if (streamConnector.protocol == StreamConnector.Protocol.TCP) {
            var controlTCPSocket: Socket?
            if (streamConnector.dataSocket!!.isClosed
                    || (streamConnector.controlTCPSocket.also { controlTCPSocket = it } != null
                            && controlTCPSocket!!.isClosed)) {
                streamConnector = createStreamConnector(mediaType)
                streamConnectors[streamConnectorIndex] = streamConnector
            }
        }
        return streamConnector
    }

    /**
     * Closes the existing `StreamConnector`, if any, associated with a specific
     * `MediaType` and removes its reference from this `TransportManager`.
     *
     * @param mediaType the `MediaType` associated with the `StreamConnector` to close
     */
    fun closeStreamConnector(mediaType: MediaType) {
        val index = mediaType.ordinal
        val streamConnector = streamConnectors[index]
        if (streamConnector != null) {
            try {
                closeStreamConnector(mediaType, streamConnector)
            } catch (e: OperationFailedException) {
                Timber.e(e, "Failed to close stream connector for %s", mediaType)
            }
            streamConnectors[index] = null
        }
    }

    /**
     * Closes a specific `StreamConnector` associated with a specific `MediaType`. If
     * this `TransportManager` has a reference to the specified `streamConnector`, it
     * remains. Allows extenders to override and perform additional customizations to the closing of
     * the specified `streamConnector`.
     *
     * @param mediaType the `MediaType` associated with the specified `streamConnector`
     * @param streamConnector the `StreamConnector` to be closed @see #closeStreamConnector(MediaType)
     */
    @Throws(OperationFailedException::class)
    protected open fun closeStreamConnector(mediaType: MediaType?, streamConnector: StreamConnector) {
        /*
         * XXX The connected owns the sockets so it is important that it decides whether to close
         * them i.e. this TransportManager is not allowed to explicitly close the sockets by itself.
         */
        streamConnector.close()
    }

    /**
     * Creates a media `StreamConnector` for a stream of a specific `MediaType`. The
     * minimum and maximum of the media port boundaries are taken into account.
     *
     * @param mediaType the `MediaType` of the stream for which a `StreamConnector` is to be created
     * @return a `StreamConnector` for the stream of the specified `mediaType`
     * @throws OperationFailedException if the binding of the sockets fails
     */
    @Throws(OperationFailedException::class)
    protected open fun createStreamConnector(mediaType: MediaType?): StreamConnector {
        val nam = ProtocolMediaActivator.networkAddressManagerService
        val intendedDestination = getIntendedDestination(getCallPeer())
        val localHostForPeer = nam!!.getLocalHost(intendedDestination)
        val portTracker = getPortTracker(mediaType)

        // create the RTP socket.
        val rtpSocket = createDatagramSocket(localHostForPeer, portTracker)

        // create the RTCP socket, preferably on the port following our RTP one.
        val rtcpSocket = createDatagramSocket(localHostForPeer, portTracker)
        return DefaultStreamConnector(rtpSocket, rtcpSocket)
    }

    /**
     * Creates `DatagramSocket` bind to `localHostForPeer`,
     * used the port numbers provided by `portTracker` and update it with
     * the result socket port so we do not try to bind to occupied ports.
     *
     * @param portTracker the port tracker.
     * @param localHostForPeer the address to bind to.
     * @return the newly created datagram socket.
     * @throws OperationFailedException if we fail to create the socket.
     */
    @Throws(OperationFailedException::class)
    private fun createDatagramSocket(localHostForPeer: InetAddress, portTracker: PortTracker): DatagramSocket {
        val nam = ProtocolMediaActivator.networkAddressManagerService

        //create the socket.
        val socket = try {
            nam!!.createDatagramSocket(localHostForPeer, portTracker.port, portTracker.minPort, portTracker.maxPort)
        } catch (exc: Exception) {
            throw OperationFailedException("Failed to allocate the network ports necessary for the call.",
                    OperationFailedException.INTERNAL_ERROR, exc)
        }
        //make sure that next time we don't try to bind on occupied ports
        portTracker.setNextPort(socket!!.localPort + 1)
        return socket
    }

    /**
     * Returns the `InetAddress` that we are using in one of our `StreamConnector`s
     * or, in case we don't have any connectors yet the address returned by the our network address
     * manager as the best local address to use when contacting the `CallPeer` associated
     * with this `MediaHandler`. This method is primarily meant for use with the o= and c=
     * fields of a newly created session description. The point is that we create our
     * `StreamConnector`s when constructing the media descriptions so we already have a
     * specific local address assigned to them at the time we get ready to create the c= and o=
     * fields. It is therefore better to try and return one of these addresses before trying the net
     * address manager again and running the slight risk of getting a different address.
     *
     * @return an `InetAddress` that we use in one of the `StreamConnector`s in this class.
     */
    fun getLastUsedLocalHost(): InetAddress {
        for (mediaType in MediaType.values()) {
            val streamConnector: StreamConnector? = streamConnectors[mediaType.ordinal]
            if (streamConnector != null) return streamConnector.dataSocket!!.localAddress
        }
        val nam: NetworkAddressManagerService? = ProtocolMediaActivator.networkAddressManagerService
        val intendedDestination = getIntendedDestination(getCallPeer())
        return nam!!.getLocalHost(intendedDestination)
    }

    /**
     * Sends empty UDP packets to target destination data/control ports in order
     * to open ports on NATs or and help RTP proxies latch onto our RTP ports.
     *
     * @param target `MediaStreamTarget`
     * @param type the [MediaType] of the connector we'd like to send the hole punching packet through.
     */
    fun sendHolePunchPacket(target: MediaStreamTarget?, type: MediaType) {
        this.sendHolePunchPacket(target, type, null)
    }

    /**
     * Sends empty UDP packets to target destination data/control ports in order
     * to open ports on NATs or/and help RTP proxies latch onto our RTP ports.
     *
     * @param target `MediaStreamTarget`
     * @param type the [MediaType] of the connector we'd like to send the hole punching packet through.
     * @param packet (optional) use a pre-generated packet that will be sent
     */
    fun sendHolePunchPacket(target: MediaStreamTarget?, type: MediaType, packet: RawPacket?) {
        // target may have been closed by remote action
        if (target == null) return

        // check how many hole punch packets we would be supposed to send:
        var packetCount = ProtocolMediaActivator.configurationService!!
                .getInt(HOLE_PUNCH_PKT_COUNT_PROPERTY, DEFAULT_HOLE_PUNCH_PKT_COUNT)
        if (packetCount < 0) packetCount = DEFAULT_HOLE_PUNCH_PKT_COUNT
        if (packetCount == 0) return
        Timber.i("Send NAT hole punch packets to port for media: %s", type.name)
        try {
            val connector = getStreamConnector(type)
            if (connector.protocol === StreamConnector.Protocol.TCP) return
            val buf = packet?.buffer ?: ByteArray(0)
            synchronized(connector) {
                // we may want to send more than one packet in case they get lost
                for (i in 0 until packetCount) {
                    var socket: DatagramSocket?
                    // data/RTP
                    if (connector.dataSocket.also { socket = it } != null) {
                        val dataAddress = target.dataAddress
                        // Timber.e(new Exception(), "Send Hole Punch Packet for media: %s; %s", type.name(), target);
                        socket!!.send(DatagramPacket(buf, buf.size, dataAddress.address, dataAddress.port))
                    }

                    // control/RTCP
                    if (connector.dataSocket.also { socket = it } != null) {
                        val controlAddress = target.controlAddress
                        socket!!.send(DatagramPacket(buf, buf.size, controlAddress.address, controlAddress.port))
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in sending remote peer for media: %s; %s", type.name, target)
        }
    }

    /**
     * Set traffic class (QoS) for the RTP socket.
     *
     * @param target `MediaStreamTarget`
     * @param type the [MediaType] of the connector we'd like to set traffic class
     */
    fun setTrafficClass(target: MediaStreamTarget?, type: MediaType) {
        // get traffic class value for RTP audio/video
        val trafficClass = getDSCP(type)
        if (trafficClass <= 0) return
        Timber.i("Set traffic class for %s to %s", type, trafficClass)
        try {
            val connector = getStreamConnector(type)
            synchronized(connector) {
                if (connector.protocol === StreamConnector.Protocol.TCP) {
                    connector.dataTCPSocket!!.trafficClass = trafficClass
                    val controlTCPSocket: Socket? = connector.controlTCPSocket
                    if (controlTCPSocket != null) controlTCPSocket.trafficClass = trafficClass
                } else {
                    /* data port (RTP) */
                    connector.dataTCPSocket!!.trafficClass = trafficClass

                    /* control port (RTCP) */
                    val controlSocket: DatagramSocket? = connector.controlSocket
                    if (controlSocket != null) controlSocket.trafficClass = trafficClass
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set traffic class for %s to %s", type, trafficClass)
        }
    }

    /**
     * Gets the SIP traffic class associated with a specific `MediaType` from the configuration.
     *
     * @param type the `MediaType` to get the associated SIP traffic class of
     * @return the SIP traffic class associated with the specified `MediaType` or `0`
     * if not configured
     */
    private fun getDSCP(type: MediaType): Int {
        val dscpPropertyName: String? = when (type) {
            MediaType.AUDIO -> RTP_AUDIO_DSCP_PROPERTY
            MediaType.VIDEO -> RTP_VIDEO_DSCP_PROPERTY
            else -> null
        }
        return if (dscpPropertyName == null) 0 else ProtocolMediaActivator.configurationService!!.getInt(dscpPropertyName, 0) shl 2
    }

    /**
     * Returns the `InetAddress` that is most likely to be used as a next hop when contacting
     * the specified `destination`. This is an utility method that is used whenever we have
     * to choose one of our local addresses to put in the Via, Contact or (in the case of no
     * registrar accounts) From headers.
     *
     * @param peer the CallPeer that we would contact.
     * @return the `InetAddress` that is most likely to be to be used as a next hop when
     * contacting the specified `destination`.
     * @throws IllegalArgumentException if `destination` is not a valid host/ip/fqdn
     */
    protected abstract fun getIntendedDestination(peer: U): InetAddress

    /**
     * Returns the [MediaAwareCallPeer] that this transport manager is serving.
     *
     * @return the [MediaAwareCallPeer] that this transport manager is serving.
     */
    fun getCallPeer(): U {
        return callPeer
    }

    /**
     * Returns the extended type of the candidate selected if this transport manager is using ICE.
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return The extended type of the candidate selected if this transport manager is using ICE.
     * Otherwise, returns null.
     */
    abstract fun getICECandidateExtendedType(streamName: String?): String?

    /**
     * Returns the current state of ICE processing.
     *
     * @return the current state of ICE processing if this transport manager is using ICE. Otherwise, returns null.
     */
    abstract fun getICEState(): String?

    /**
     * Returns the ICE local host address.
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE local host address if this transport manager is using ICE. Otherwise, returns null.
     */
    abstract fun getICELocalHostAddress(streamName: String?): InetSocketAddress?

    /**
     * Returns the ICE remote host address.
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE remote host address if this transport manager is using ICE. Otherwise, returns null.
     */
    abstract fun getICERemoteHostAddress(streamName: String?): InetSocketAddress?

    /**
     * Returns the ICE local reflexive address (server or peer reflexive).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE local reflexive address. May be null if this transport manager is not using
     * ICE or if there is no reflexive address for the local candidate used.
     */
    abstract fun getICELocalReflexiveAddress(streamName: String?): InetSocketAddress?

    /**
     * Returns the ICE remote reflexive address (server or peer reflexive).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE remote reflexive address. May be null if this transport manager is not using
     * ICE or if there is no reflexive address for the remote candidate used.
     */
    abstract fun getICERemoteReflexiveAddress(streamName: String?): InetSocketAddress?

    /**
     * Returns the ICE local relayed address (server or peer relayed).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE local relayed address. May be null if this transport manager is not using ICE
     * or if there is no relayed address for the local candidate used.
     */
    abstract fun getICELocalRelayedAddress(streamName: String?): InetSocketAddress?

    /**
     * Returns the ICE remote relayed address (server or peer relayed).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE remote relayed address. May be null if this transport manager is not using
     * ICE or if there is no relayed address for the remote candidate used.
     */
    abstract fun getICERemoteRelayedAddress(streamName: String?): InetSocketAddress?

    /**
     * Returns the total harvesting time (in ms) for all harvesters.
     *
     * @return The total harvesting time (in ms) for all the harvesters. 0 if the ICE agent is null,
     * or if the agent has nevers harvested.
     */
    abstract fun getTotalHarvestingTime(): Long

    /**
     * Returns the harvesting time (in ms) for the harvester given in parameter.
     *
     * @param harvesterName The class name if the harvester.
     * @return The harvesting time (in ms) for the harvester given in parameter. 0 if this harvester
     * does not exists, if the ICE agent is null, or if the agent has never harvested with this harvester.
     */
    abstract fun getHarvestingTime(harvesterName: String?): Long

    /**
     * Returns the number of harvesting for this agent.
     *
     * @return The number of harvesting for this agent.
     */
    abstract fun getNbHarvesting(): Int

    /**
     * Returns the number of harvesting time for the harvester given in parameter.
     *
     * @param harvesterName The class name if the harvester.
     * @return The number of harvesting time for the harvester given in parameter.
     */
    abstract fun getNbHarvesting(harvesterName: String?): Int

    /**
     * Creates the ICE agent that we would be using in this transport manager for all negotiation.
     *
     * @return the ICE agent to use for all the ICE negotiation that this transport manager would be going through
     */
    protected open fun createIceAgent(): Agent? {
        // work in progress
        return null
    }

    /**
     * Creates an [IceMediaStream] with the specified `media` name.
     *
     * @param media the name of the stream we'd like to create.
     * @param agent the ICE [Agent] that we will be appending the stream to.
     * @return the newly created [IceMediaStream]
     * @throws OperationFailedException if binding on the specified media stream fails for some reason.
     */
    @Throws(OperationFailedException::class)
    protected fun createIceStream(media: String?, agent: Agent?): IceMediaStream? {
        return null
    }

    companion object {
        /**
         * The port tracker that we should use when binding generic media streams.
         *
         * Initialized by [.initializePortNumbers].
         */
        private val defaultPortTracker = PortTracker(5000, 6000)

        /**
         * The port tracker that we should use when binding video media streams.
         *
         * Potentially initialized by [.initializePortNumbers] if the necessary properties are set.
         */
        private var videoPortTracker: PortTracker? = null

        /**
         * The port tracker that we should use when binding data channels.
         *
         * Potentially initialized by [.initializePortNumbers] if the necessary properties are set.
         */
        private var dataPortTracker: PortTracker? = null

        /**
         * The port tracker that we should use when binding data media streams.
         *
         * Potentially initialized by [.initializePortNumbers] if the necessary properties are set.
         */
        private var audioPortTracker: PortTracker? = null

        /**
         * RTP audio DSCP configuration property name.
         */
        private const val RTP_AUDIO_DSCP_PROPERTY = "protocol.RTP_AUDIO_DSCP"

        /**
         * RTP video DSCP configuration property name.
         */
        private const val RTP_VIDEO_DSCP_PROPERTY = "protocol.RTP_VIDEO_DSCP"

        /**
         * Number of empty UDP packets to send for NAT hole punching.
         */
        private const val HOLE_PUNCH_PKT_COUNT_PROPERTY = "protocol.HOLE_PUNCH_PKT_COUNT"

        /**
         * Number of empty UDP packets to send for NAT hole punching.
         */
        private const val DEFAULT_HOLE_PUNCH_PKT_COUNT = 3

        /**
         * Returns the port tracker that we are supposed to use when binding ports for the specified [MediaType].
         *
         * @param mediaType the media type that we want to obtain the port tracker for. Use `null` to
         * obtain the default port tracker.
         * @return the port tracker that we are supposed to use when binding ports for the specified [MediaType].
         */
        protected fun getPortTracker(mediaType: MediaType?): PortTracker {
            // make sure our port numbers reflect the configuration service settings
            initializePortNumbers()
            if (mediaType != null) {
                return when (mediaType) {
                    MediaType.AUDIO -> {
                        audioPortTracker ?: defaultPortTracker
                    }
                    MediaType.VIDEO -> {
                        videoPortTracker ?: defaultPortTracker
                    }
                    MediaType.DATA -> {
                        dataPortTracker ?: defaultPortTracker
                    }
                    else -> {
                        defaultPortTracker
                    }
                }
            }
            return defaultPortTracker
        }

        /**
         * Returns the port tracker that we are supposed to use when binding ports for the
         * [MediaType] indicated by the string param. If we do not recognize the string as a valid
         * media type, we simply return the default port tracker.
         *
         * @param mediaTypeStr the name of the media type that we want to obtain a port tracker for.
         * @return the port tracker that we are supposed to use when binding ports for the
         * [MediaType] with the specified name or the default tracker in case the name doesn't ring a bell.
         */
        @JvmStatic
        protected fun getPortTracker(mediaTypeStr: String): PortTracker {
            return try {
                getPortTracker(MediaType.parseString(mediaTypeStr))
            } catch (e: Exception) {
                Timber.i("Returning default port tracker for unrecognized media type: %s", mediaTypeStr)
                defaultPortTracker
            }
        }

        /**
         * Tries to set the ranges of the `PortTracker`s (e.g. default, audio, video, data
         * channel) to the values specified in the `ConfigurationService`.
         */
        @Synchronized
        protected fun initializePortNumbers() {
            // try the default tracker first
            val cfg: ConfigurationService? = ProtocolMediaActivator.configurationService
            var maxPort: String?
            var minPort: String? = cfg!!.getString(OperationSetBasicTelephony.MIN_MEDIA_PORT_NUMBER_PROPERTY_NAME)
            if (minPort != null) {
                maxPort = cfg.getString(OperationSetBasicTelephony.MAX_MEDIA_PORT_NUMBER_PROPERTY_NAME)
                if (maxPort != null) {
                    // Try the specified range; otherwise, leave the tracker as it is: [5000, 6000].
                    defaultPortTracker.tryRange(minPort, maxPort)
                }
            }

            // try the VIDEO tracker
            minPort = cfg.getString(OperationSetBasicTelephony.MIN_VIDEO_PORT_NUMBER_PROPERTY_NAME)
            if (minPort != null) {
                maxPort = cfg.getString(OperationSetBasicTelephony.MAX_VIDEO_PORT_NUMBER_PROPERTY_NAME)
                if (maxPort != null) {
                    // Try the specified range; otherwise, leave the tracker to null.
                    if (videoPortTracker == null) {
                        videoPortTracker = PortTracker.createTracker(minPort, maxPort)
                    } else {
                        videoPortTracker!!.tryRange(minPort, maxPort)
                    }
                }
            }

            // try the AUDIO tracker
            minPort = cfg.getString(OperationSetBasicTelephony.MIN_AUDIO_PORT_NUMBER_PROPERTY_NAME)
            if (minPort != null) {
                maxPort = cfg.getString(OperationSetBasicTelephony.MAX_AUDIO_PORT_NUMBER_PROPERTY_NAME)
                if (maxPort != null) {
                    // Try the specified range; otherwise, leave the tracker to null.
                    if (audioPortTracker == null) {
                        audioPortTracker = PortTracker.createTracker(minPort, maxPort)
                    } else {
                        audioPortTracker!!.tryRange(minPort, maxPort)
                    }
                }
            }

            // try the DATA CHANNEL tracker
            minPort = cfg.getString(OperationSetBasicTelephony.MIN_DATA_CHANNEL_PORT_NUMBER_PROPERTY_NAME)
            if (minPort != null) {
                maxPort = cfg.getString(OperationSetBasicTelephony.MAX_DATA_CHANNEL_PORT_NUMBER_PROPERTY_NAME)
                if (maxPort != null) {
                    // Try the specified range; otherwise, leave the tracker to null.
                    if (dataPortTracker == null) {
                        dataPortTracker = PortTracker.createTracker(minPort, maxPort)
                    } else {
                        dataPortTracker!!.tryRange(minPort, maxPort)
                    }
                }
            }
        }

        /**
         * Returns the ICE candidate extended type selected by the given agent.
         *
         * @param iceAgent The ICE agent managing the ICE offer/answer exchange, collecting and selecting the candidate.
         * @param streamName The stream name (AUDIO, VIDEO);
         * @return The ICE candidate extended type selected by the given agent. null if the iceAgent is
         * null or if there is no candidate selected or available.
         */
        fun getICECandidateExtendedType(iceAgent: Agent?, streamName: String?): String? {
            if (iceAgent != null) {
                val localCandidate: LocalCandidate? = iceAgent.getSelectedLocalCandidate(streamName)
                if (localCandidate != null) return localCandidate.getExtendedType().toString()
            }
            return null
        }
    }
}