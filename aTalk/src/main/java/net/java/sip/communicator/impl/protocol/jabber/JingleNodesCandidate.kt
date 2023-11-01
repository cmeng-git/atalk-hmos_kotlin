/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import org.ice4j.TransportAddress
import org.ice4j.ice.CandidateExtendedType
import org.ice4j.ice.CandidateType
import org.ice4j.ice.Component
import org.ice4j.ice.LocalCandidate
import org.ice4j.socket.IceSocketWrapper
import org.ice4j.socket.IceUdpSocketWrapper
import org.ice4j.socket.MultiplexingDatagramSocket
import java.lang.reflect.UndeclaredThrowableException
import java.net.SocketException

/**
 * Represents a `Candidate` obtained via Jingle Nodes.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class JingleNodesCandidate(transportAddress: TransportAddress?, parentComponent: Component?,
        localEndPoint: TransportAddress?) : LocalCandidate(transportAddress, parentComponent, CandidateType.RELAYED_CANDIDATE,
        CandidateExtendedType.JINGLE_NODE_CANDIDATE, null) {
    /**
     * The socket used to communicate with relay.
     */
    private var socket: IceSocketWrapper? = null

    /**
     * The `RelayedCandidateDatagramSocket` of this `JingleNodesCandidate`.
     */
    private var jingleNodesCandidateDatagramSocket: JingleNodesCandidateDatagramSocket? = null

    /**
     * `TransportAddress` of the Jingle Nodes relay where we will send our packet.
     */
    private var localEndPoint: TransportAddress? = null

    /**
     * Creates a `JingleNodesRelayedCandidate` for the specified transport, address, and base.
     *
     * @param transportAddress the transport address that this candidate is encapsulating.
     * @param parentComponent the `Component` that this candidate belongs to.
     * @param localEndPoint `TransportAddress` of the Jingle Nodes relay where we will send our packet.
     */
    init {
        base = this
        relayServerAddress = localEndPoint
        this.localEndPoint = localEndPoint
    }

    /**
     * Gets the `JingleNodesCandidateDatagramSocket` of this `JingleNodesCandidate`.
     *
     * **Note**: The method is part of the internal API of `RelayedCandidate` and
     * `TurnCandidateHarvest` and is not intended for public use.
     *
     * @return the `RelayedCandidateDatagramSocket` of this `RelayedCandidate`
     */
    @get:Synchronized
    private val relayedCandidateDatagramSocket: JingleNodesCandidateDatagramSocket
        get() {
            if (jingleNodesCandidateDatagramSocket == null) {
                jingleNodesCandidateDatagramSocket = try {
                    JingleNodesCandidateDatagramSocket(this, localEndPoint)
                } catch (sex: SocketException) {
                    throw UndeclaredThrowableException(sex)
                }
            }
            return jingleNodesCandidateDatagramSocket!!
        }

    /**
     * Gets the `DatagramSocket` associated with this `Candidate`.
     *
     * @return the `DatagramSocket` associated with this `Candidate`
     */
    public override fun getCandidateIceSocketWrapper(): IceSocketWrapper {
        if (socket == null) {
            socket = try {
                IceUdpSocketWrapper(MultiplexingDatagramSocket(relayedCandidateDatagramSocket))
            } catch (sex: SocketException) {
                throw UndeclaredThrowableException(sex)
            }
        }
        return socket!!
    }
}