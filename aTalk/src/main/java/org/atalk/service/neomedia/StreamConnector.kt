/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import java.net.DatagramSocket
import java.net.Socket

/**
 * The `StreamConnector` interface represents a pair of datagram sockets that a media stream
 * could use for RTP and RTCP traffic.
 *
 *
 * The reason why this media service makes sockets visible through this `StreamConnector` is
 * so that they could be shared among media and other libraries that may need to use them like an
 * ICE implementation for example.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface StreamConnector {
    /**
     * Enumerates the protocols supported by `StreamConnector`.
     */
    enum class Protocol {
        /**
         * UDP protocol.
         */
        UDP,

        /**
         * TCP protocol.
         */
        TCP
    }

    /**
     * Returns a reference to the `DatagramSocket` that a stream should use for data (e.g.
     * RTP) traffic.
     *
     * @return a reference to the `DatagramSocket` that a stream should use for data (e.g.
     * RTP) traffic or `null` if this `StreamConnector` does not handle UDP
     * sockets.
     */
    val dataSocket: DatagramSocket?

    /**
     * Returns a reference to the `DatagramSocket` that a stream should use for control data
     * (e.g. RTCP).
     *
     * @return a reference to the `DatagramSocket` that a stream should use for control data
     * (e.g. RTCP) or `null` if this `StreamConnector` does not handle UDP
     * sockets.
     */
    val controlSocket: DatagramSocket?

    /**
     * Returns a reference to the `Socket` that a stream should use for data (e.g. RTP)
     * traffic.
     *
     * @return a reference to the `Socket` that a stream should use for data (e.g. RTP)
     * traffic or `null` if this `StreamConnector` does not handle TCP
     * sockets.
     */
    val dataTCPSocket: Socket?

    /**
     * Returns a reference to the `Socket` that a stream should use for control data (e.g.
     * RTCP).
     *
     * @return a reference to the `Socket` that a stream should use for control data (e.g.
     * RTCP) or `null` if this `StreamConnector` does not handle TCP sockets.
     */
    val controlTCPSocket: Socket?

    /**
     * Returns the protocol of this `StreamConnector`.
     *
     * @return the protocol of this `StreamConnector`
     */
    val protocol: Protocol?

    /**
     * Releases the resources allocated by this instance in the course of its execution and prepares
     * it to be garbage collected.
     */
    fun close()

    /**
     * Notifies this instance that utilization of its `DatagramSocket`s for data and/or
     * control traffic has started.
     */
    fun started()

    /**
     * Notifies this instance that utilization of its `DatagramSocket`s for data and/or
     * control traffic has temporarily stopped. This instance should be prepared to be started at a
     * later time again though.
     */
    fun stopped()

    /**
     * Returns `true` if this `StreamConnector` uses rtcp-mux, that is, if its data
     * and control sockets share the same local address and port.
     *
     * @return `true` if this `StreamConnector` uses rtcp-mux, that is, if its data
     * and control sockets share the same local address and port.
     */
    val isRtcpmux: Boolean
}