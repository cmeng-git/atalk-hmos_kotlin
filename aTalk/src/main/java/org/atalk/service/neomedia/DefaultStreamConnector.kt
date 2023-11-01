/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import org.atalk.service.libjitsi.LibJitsi
import timber.log.Timber
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.net.SocketException

/**
 * Represents a default implementation of `StreamConnector` which is initialized with a
 * specific pair of control and data `DatagramSocket`s and which closes them (if they exist)
 * when its [.close] is invoked.
 *
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
class DefaultStreamConnector : StreamConnector {
    /**
     * The local `InetAddress` this `StreamConnector` attempts to bind to on demand.
     */
    private val bindAddr: InetAddress?

    /**
     * {@inheritDoc}
     */
    /**
     * Whether this `DefaultStreamConnector` uses rtcp-mux.
     */
    override var isRtcpmux = false
        protected set

    /**
     * Initializes a new `DefaultStreamConnector` instance with a specific bind
     * `InetAddress`. The new instance is to attempt to bind on demand to the specified
     * `InetAddress` in the port range defined by the `ConfigurationService`
     * properties [.MIN_PORT_NUMBER_PROPERTY_NAME] and [.MAX_PORT_NUMBER_PROPERTY_NAME]
     * at most [.BIND_RETRIES_PROPERTY_NAME] times.
     *
     * @param bindAddr the local `InetAddress` the new instance is to attempt to bind to
     */
    constructor(bindAddr: InetAddress?) {
        this.bindAddr = bindAddr
    }
    /**
     * Initializes a new `DefaultStreamConnector` instance which is to represent a specific
     * pair of control and data `DatagramSocket`s.
     *
     * dataSocket the `DatagramSocket` to be used for data (e.g. RTP) traffic
     * controlSocket the `DatagramSocket` to be used for control data (e.g. RTCP) traffic
     * rtcpmux whether rtcpmux is used.
     */
    /**
     * Initializes a new `DefaultStreamConnector` instance which is to represent a specific
     * pair of control and data `DatagramSocket`s.
     *
     * dataSocket the `DatagramSocket` to be used for data (e.g. RTP) traffic
     * controlSocket the `DatagramSocket` to be used for control data (e.g. RTCP) traffic
     */
    /**
     * Initializes a new `DefaultStreamConnector` instance with no control and data `DatagramSocket`s.
     *
     * Suitable for extenders willing to delay the creation of the control and data sockets. For
     * example, they could override [.getControlSocket] and/or [.getDataSocket] and create them on demand.
     */
    @JvmOverloads
    constructor(
            dataSocket: DatagramSocket? = null, controlSocket: DatagramSocket? = null,
            rtcpmux: Boolean = false,
    ) {
        this.controlSocket = controlSocket
        this.dataSocket = dataSocket
        bindAddr = null
        isRtcpmux = rtcpmux
    }

    /**
     * Releases the resources allocated by this instance in the course of its execution and prepares
     * it to be garbage collected.
     *
     * @see StreamConnector.close
     */
    override fun close() {
        controlSocket?.close()
        dataSocket?.close()
    }

    /**
     * Returns a reference to the `DatagramSocket` that a stream should use for control data (e.g. RTCP) traffic.
     *
     * @return a reference to the `DatagramSocket` that a stream should use for control data (e.g. RTCP) traffic.
     * @see StreamConnector.controlSocket
     */
    override var controlSocket: DatagramSocket? = null
        get() {
            if (field == null && bindAddr != null)
                field = createDatagramSocket(bindAddr)
            return field
        }

    /**
     * Returns a reference to the `DatagramSocket` that a stream should use for data (e.g. RTP) traffic.
     *
     * @return a reference to the `DatagramSocket` that a stream should use for data (e.g. RTP) traffic.
     * @see StreamConnector.getDataSocket
     */
    override var dataSocket: DatagramSocket? = null
        get() {
            if (field == null && bindAddr != null)
                field = createDatagramSocket(bindAddr)
            return field
        }

    /**
     * Returns a reference to the `Socket` that a stream should use for data (e.g. RTP) traffic.
     *
     * @return a reference to the `Socket` that a stream should use for data (e.g. RTP) traffic.
     */
    override val dataTCPSocket: Socket?
        get() = null

    /**
     * Returns a reference to the `Socket` that a stream should use for control data (e.g. RTCP).
     *
     * @return a reference to the `Socket` that a stream should use for control data (e.g. RTCP).
     */
    override val controlTCPSocket: Socket?
        get() = null

    /**
     * Returns the protocol of this `StreamConnector`.
     *
     * @return the protocol of this `StreamConnector`
     */
    override val protocol: StreamConnector.Protocol
        get() = StreamConnector.Protocol.UDP

    /**
     * Notifies this instance that utilization of its `DatagramSocket`s for data and/or
     * control traffic has started.
     *
     * @see StreamConnector.started
     */
    override fun started() {}

    /**
     * Notifies this instance that utilization of its `DatagramSocket`s for data and/or
     * control traffic has temporarily stopped. This instance should be prepared to be started at a
     * later time again though.
     *
     * @see StreamConnector.stopped
     */
    override fun stopped() {}

    companion object {
        /**
         * The default number of binds that a Media Service Implementation should execute in case a port
         * is already bound to (each retry would be on a new random port).
         */
        private const val BIND_RETRIES_DEFAULT_VALUE = 50

        /**
         * The name of the property containing the number of binds that a Media Service Implementation
         * should execute in case a port is already bound to (each retry would be on a new port in the
         * allowed boundaries).
         */
        private const val BIND_RETRIES_PROPERTY_NAME = "media.BIND_RETRIES"

        /**
         * The name of the property that contains the maximum port number that we'd like our RTP managers to bind upon.
         */
        private const val MAX_PORT_NUMBER_PROPERTY_NAME = "media.MAX_PORT_NUMBER"

        /**
         * The maximum port number `DefaultStreamConnector` instances are to attempt to bind to.
         */
        private var maxPort = -1

        /**
         * The name of the property that contains the minimum port number that we'd like our RTP managers to bind upon.
         */
        private const val MIN_PORT_NUMBER_PROPERTY_NAME = "media.MIN_PORT_NUMBER"

        /**
         * The minimum port number `DefaultStreamConnector` instances are to attempt to bind to.
         */
        private var minPort = -1

        /**
         * Creates a new `DatagramSocket` instance which is bound to the specified local
         * `InetAddress` and its port is within the range defined by the
         * `ConfigurationService` properties [.MIN_PORT_NUMBER_PROPERTY_NAME] and
         * [.MAX_PORT_NUMBER_PROPERTY_NAME]. Attempts at most [.BIND_RETRIES_PROPERTY_NAME]
         * times to bind.
         *
         * @param bindAddr the local `InetAddress` the new `DatagramSocket` is to bind to
         * @return a new `DatagramSocket` instance bound to the specified local `InetAddress`
         */
        @Synchronized
        private fun createDatagramSocket(bindAddr: InetAddress?): DatagramSocket? {
            val cfg = LibJitsi.configurationService
            var bindRetries = BIND_RETRIES_DEFAULT_VALUE
            if (cfg != null) bindRetries = cfg.getInt(BIND_RETRIES_PROPERTY_NAME, bindRetries)
            if (maxPort < 0) {
                maxPort = 6000
                if (cfg != null) maxPort = cfg.getInt(MAX_PORT_NUMBER_PROPERTY_NAME, maxPort)
            }
            for (i in 0 until bindRetries) {
                if (minPort < 0 || minPort > maxPort) {
                    minPort = 5000
                    if (cfg != null) {
                        minPort = cfg.getInt(MIN_PORT_NUMBER_PROPERTY_NAME, minPort)
                    }
                }
                val port = minPort++
                try {
                    return if (bindAddr == null) DatagramSocket(port) else DatagramSocket(port, bindAddr)
                } catch (se: SocketException) {
                    Timber.w(se, "Retrying a bind because of a failure to bind to address %s and port %d", bindAddr, port)
                }
            }
            return null
        }
    }
}