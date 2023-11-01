/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import org.ice4j.TransportAddress
import org.ice4j.socket.StunDatagramPacketFilter
import timber.log.Timber
import java.io.IOException
import java.net.*

/**
 * Represents an application-purposed (as opposed to an ICE-specific) `DatagramSocket` for a
 * `JingleNodesCandidate`.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class JingleNodesCandidateDatagramSocket(
        /**
         * The `JingleNodesCandidate`.
         */
        private val jingleNodesCandidate: JingleNodesCandidate, localEndPoint: TransportAddress?) : DatagramSocket( /* bindaddr */null as SocketAddress?) {
    /**
     * `TransportAddress` of the Jingle Nodes relay where we will send our packet.
     */
    private var localEndPoint: TransportAddress? = null

    /**
     * The number of RTP packets received for this socket.
     */
    private val nbReceivedRtpPackets: Long = 0

    /**
     * The number of RTP packets sent for this socket.
     */
    private val nbSentRtpPackets: Long = 0

    /**
     * The number of RTP packets lost (not received) for this socket.
     */
    private var nbLostRtpPackets: Long = 0

    /**
     * The last RTP sequence number received for this socket.
     */
    private var lastRtpSequenceNumber: Long = -1

    /**
     * The last time an information about packet lost has been logged.
     */
    private var lastLostPacketLogTime: Long = 0

    /**
     * Initializes a new `JingleNodesdCandidateDatagramSocket` instance which is to be the
     * `socket` of a specific `JingleNodesCandidate`.
     *
     * jingleNodesCandidate the `JingleNodesCandidate` which is to use the new instance as
     * the value of its `socket` property
     * localEndPoint `TransportAddress` of the Jingle Nodes relay where we will send our packet.
     * @throws SocketException if anything goes wrong while initializing the new
     * `JingleNodesCandidateDatagramSocket` instance
     */
    init {
        this.localEndPoint = localEndPoint
    }

    /**
     * Sends a datagram packet from this socket. The `DatagramPacket` includes information indicating
     * the data to be sent, its length, the IP address of the remote host, and the port number on the remote host.
     *
     * p the `DatagramPacket` to be sent
     * @throws IOException if an I/O error occurs
     * @see DatagramSocket.send
     */
    @Throws(IOException::class)
    override fun send(p: DatagramPacket) {
        val data = p.data
        val dataLen = p.length
        val dataOffset = p.offset

        /* send to Jingle Nodes relay address on local port */
        val packet = DatagramPacket(data, dataOffset, dataLen,
                InetSocketAddress(localEndPoint!!.address, localEndPoint!!.port))

        // XXX reuse an existing DatagramPacket ?
        super.send(packet)
    }

    /**
     * Receives a `DatagramPacket` from this socket. The DatagramSocket is overridden to log
     * the received packet.
     *
     * p `DatagramPacket`
     * @throws IOException if something goes wrong
     */
    @Throws(IOException::class)
    override fun receive(p: DatagramPacket) {
        super.receive(p)
        // Log RTP losses if > 5%.
        updateRtpLosses(p)
    }

    /**
     * Gets the local address to which the socket is bound.
     * `JingleNodesCandidateDatagramSocket` returns the `address` of its
     * `localSocketAddress`.
     *
     * If there is a security manager, its `checkConnect` method is first called with the
     * host address and `-1` as its arguments to see if the operation is allowed.
     *
     * @return the local address to which the socket is bound, or an `InetAddress`
     * representing any local address if either the socket is not bound, or the security
     * manager `checkConnect` method does not allow the operation
     * @see .getLocalSocketAddress
     * @see DatagramSocket.getLocalAddress
     */
    override fun getLocalAddress(): InetAddress {
        return localSocketAddress.address
    }

    /**
     * Returns the port number on the local host to which this socket is bound.
     * `JingleNodesCandidateDatagramSocket` returns the `port` of its
     * `localSocketAddress`.
     *
     * @return the port number on the local host to which this socket is bound
     * @see .getLocalSocketAddress
     * @see DatagramSocket.getLocalPort
     */
    override fun getLocalPort(): Int {
        return localSocketAddress.port
    }

    /**
     * Returns the address of the endpoint this socket is bound to, or `null` if it is not
     * bound yet. Since `JingleNodesCandidateDatagramSocket` represents an
     * application-purposed `DatagramSocket` relaying data to and from a Jingle Nodes relay,
     * the `localSocketAddress` is the `transportAddress` of respective
     * `JingleNodesCandidate`.
     *
     * @return a `SocketAddress` representing the local endpoint of this socket, or
     * `null` if it is not bound yet
     * @see DatagramSocket.getLocalSocketAddress
     */
    override fun getLocalSocketAddress(): InetSocketAddress {
        return jingleNodesCandidate.transportAddress
    }

    /**
     * Updates and Logs information about RTP losses if there is more then 5% of RTP packet lost (at
     * most every 5 seconds).
     *
     * p The last packet received.
     */
    private fun updateRtpLosses(p: DatagramPacket) {
        // If this is not a STUN/TURN packet, then this is a RTP packet.
        if (!StunDatagramPacketFilter.isStunPacket(p)) {
            val newSeq = getRtpSequenceNumber(p)
            if (lastRtpSequenceNumber != -1L) {
                nbLostRtpPackets += getNbLost(lastRtpSequenceNumber, newSeq)
            }
            lastRtpSequenceNumber = newSeq
            lastLostPacketLogTime = logRtpLosses(nbLostRtpPackets, nbReceivedRtpPackets, lastLostPacketLogTime)
        }
    }

    companion object {
        /**
         * Logs information about RTP losses if there is more then 5% of RTP packet lost (at most every
         * 5 seconds).
         *
         * totalNbLost The total number of lost packet since the beginning of this stream.
         * totalNbReceived The total number of received packet since the beginning of this stream.
         * lastLogTime The last time we have logged information about RTP losses.
         * @return the last log time updated if this function as log new information about RTP losses.
         * Otherwise, returns the same last log time value as given in parameter.
         */
        private fun logRtpLosses(totalNbLost: Long, totalNbReceived: Long, lastLogTime: Long): Long {
            val percentLost = totalNbLost.toDouble() / (totalNbLost + totalNbReceived).toDouble()
            // Log the information if the loss rate is upper 5% and if the last log is before 5 seconds.
            if (percentLost > 0.05) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastLogTime >= 5000) {
                    Timber.i("RTP lost > 5%: %s", percentLost)
                    return currentTime
                }
            }
            return lastLogTime
        }

        /**
         * Return the number of loss between the 2 last RTP packets received.
         *
         * lastRtpSequenceNumber The previous RTP sequence number.
         * newSeq The current RTP sequence number.
         * @return the number of loss between the 2 last RTP packets received.
         */
        private fun getNbLost(lastRtpSequenceNumber: Long, newSeq: Long): Long {
            val newNbLost = if (lastRtpSequenceNumber <= newSeq) {
                newSeq - lastRtpSequenceNumber
            } else {
                0xffff - lastRtpSequenceNumber + newSeq
            }

            return if (newNbLost > 1) {
                if (newNbLost < 0x00ff) {
                    newNbLost - 1
                } else {
                    1
                }
            } else 0
        }

        /**
         * Determines the sequence number of an RTP packet.
         *
         * p the last RTP packet received.
         * @return The last RTP sequence number.
         */
        private fun getRtpSequenceNumber(p: DatagramPacket): Long {
            // The sequence number is contained in the third and fourth bytes of the RTP header (stored in big endian).
            val data = p.data
            val offset = p.offset
            val seq_high = (data[offset + 2].toInt() and 0xff).toLong()
            val seq_low = (data[offset + 3].toInt() and 0xff).toLong()
            return seq_high shl 8 or seq_low
        }
    }
}