/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import java.net.DatagramSocket
import java.net.Socket

/**
 * Implements a [StreamConnector] which wraps a specific `StreamConnector` instance.
 *
 * @param <T>
 * the very type of the `StreamConnector` wrapped by `StreamConnectorDelegate`
 *
 * @author Lyubomir Marinov
</T> */
open class StreamConnectorDelegate<T : StreamConnector?>(streamConnector: T?) : StreamConnector {
    /**
     * The `StreamConnector` wrapped by this instance.
     */
    protected val streamConnector: T

    /**
     * Initializes a new `StreamConnectorDelegate` which is to wrap a specific
     * `StreamConnector`.
     *
     * @param streamConnector
     * the `StreamConnector` to be wrapped by the new instance
     */
    init {
        if (streamConnector == null) throw NullPointerException("streamConnector")
        this.streamConnector = streamConnector
    }

    /**
     * Releases the resources allocated by this instance in the course of its execution and prepares
     * it to be garbage collected. Calls [StreamConnector.close] on the
     * `StreamConnector` wrapped by this instance.
     */
    override fun close() {
        streamConnector!!.close()
    }

    /**
     * {@inheritDoc}
     */
    override val controlSocket: DatagramSocket?
        get() = streamConnector!!.controlSocket

    /**
     * {@inheritDoc}
     */
    override val controlTCPSocket: Socket?
        get() = streamConnector!!.controlTCPSocket

    /**
     * {@inheritDoc}
     */
    override val dataSocket: DatagramSocket?
        get() = streamConnector!!.dataSocket

    /**
     * {@inheritDoc}
     */
    override val dataTCPSocket: Socket?
        get() = streamConnector!!.dataTCPSocket

    /**
     * {@inheritDoc}
     */
    override val protocol: StreamConnector.Protocol?
        get() = streamConnector!!.protocol

    /**
     * {@inheritDoc}
     */
    override fun started() {
        streamConnector!!.started()
    }

    /**
     * {@inheritDoc}
     */
    override fun stopped() {
        streamConnector!!.stopped()
    }

    /**
     * {@inheritDoc}
     */
    override val isRtcpmux: Boolean
        get() = streamConnector!!.isRtcpmux
}