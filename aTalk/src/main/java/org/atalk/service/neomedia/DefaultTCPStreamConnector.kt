/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import timber.log.Timber
import java.io.IOException
import java.net.DatagramSocket
import java.net.Socket

/**
 * Represents a default implementation of `StreamConnector` which is initialized with a
 * specific pair of control and data `Socket`s and which closes them (if they exist) when its
 * [.close] is invoked.
 *
 * @author Lubomir Marinov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class DefaultTCPStreamConnector
/**
 * Initializes a new `DefaultTCPStreamConnector` instance with no control and data
 * `Socket`s.
 *
 *
 * Suitable for extenders willing to delay the creation of the control and data sockets. For
 * example, they could override [.getControlSocket] and/or [.getDataSocket] and
 * create them on demand.
 */
@JvmOverloads constructor(
        /**
         * The `Socket` that a stream should use for data (e.g. RTP) traffic.
         */
        override var dataTCPSocket: Socket? = null,
        /**
         * The `Socket` that a stream should use for control data (e.g. RTCP) traffic.
         */
        override var controlTCPSocket: Socket? = null,
        /**
         * Whether this `DefaultStreamConnector` uses rtcp-mux.
         */
        override var isRtcpmux: Boolean = false) : StreamConnector {
    /**
     * Returns a reference to the `Socket` that a stream should use for control data (e.g.
     * RTCP).
     *
     * @return a reference to the `Socket` that a stream should use for control data (e.g.
     * RTCP).
     */
    /**
     * Returns a reference to the `Socket` that a stream should use for data (e.g. RTP)
     * traffic.
     *
     * @return a reference to the `Socket` that a stream should use for data (e.g. RTP)
     * traffic.
     */
    /**
     * {@inheritDoc}
     */
    /**
     * Initializes a new `DefaultTCPStreamConnector` instance which is to represent a
     * specific pair of control and data `Socket`s.
     *
     * @param dataTCPSocket the `Socket` to be used for data (e.g. RTP) traffic
     * @param controlTCPSocket the `Socket` to be used for control data (e.g. RTCP) traffic
     * @param isRtcpmux whether rtcpmux is used.
     */
    /**
     * Initializes a new `DefaultTCPStreamConnector` instance which is to represent a
     * specific pair of control and data `Socket`s.
     *
     * @param dataTCPSocket the `Socket` to be used for data (e.g. RTP) traffic
     * @param controlTCPSocket the `Socket` to be used for control data (e.g. RTCP) traffic
     */
    /**
     * Releases the resources allocated by this instance in the course of its execution and prepares
     * it to be garbage collected.
     *
     * @see StreamConnector.close
     */
    override fun close() {
        try {
            if (controlTCPSocket != null) controlTCPSocket!!.close()
            if (dataTCPSocket != null) dataTCPSocket!!.close()
        } catch (ioe: IOException) {
            Timber.d(ioe, "Failed to close TCP socket")
        }
    }

    override val dataSocket: DatagramSocket? = null

    override val controlSocket: DatagramSocket? = null

    /**
     * Returns the protocol of this `StreamConnector`.
     *
     * @return the protocol of this `StreamConnector`
     */
    override val protocol: StreamConnector.Protocol
        get() = StreamConnector.Protocol.TCP

    /**
     * Notifies this instance that utilization of its `Socket`s for data and/or control
     * traffic has started.
     *
     * @see StreamConnector.started
     */
    override fun started() {}

    /**
     * Notifies this instance that utilization of its `Socket`s for data and/or control
     * traffic has temporarily stopped. This instance should be prepared to be started at a later
     * time again though.
     *
     * @see StreamConnector.stopped
     */
    override fun stopped() {}
}