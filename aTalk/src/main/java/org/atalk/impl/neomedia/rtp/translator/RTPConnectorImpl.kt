/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.translator

import org.atalk.service.neomedia.MediaStream
import java.io.IOException
import java.lang.reflect.UndeclaredThrowableException
import java.util.*
import javax.media.protocol.PushSourceStream
import javax.media.rtp.OutputDataStream
import javax.media.rtp.RTPConnector

/**
 * Implements the `RTPConnector` with which this instance initializes its `RTPManager`
 * . It delegates to the `RTPConnector` of the various `StreamRTPManager`s.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
internal class RTPConnectorImpl(val translator: RTPTranslatorImpl) : RTPConnector {
    /**
     * The `RTPConnector`s this instance delegates to.
     */
    private val connectors: MutableList<RTPConnectorDesc> = LinkedList()
    private var controlInputStream: PushSourceStreamImpl? = null
    private var controlOutputStream: OutputDataStreamImpl? = null
    private var dataInputStream: PushSourceStreamImpl? = null
    private var dataOutputStream: OutputDataStreamImpl? = null

    /**
     * The indicator which determines whether [.close] has been invoked on this instance.
     */
    private var closed = false
    @Synchronized
    fun addConnector(connector: RTPConnectorDesc) {
        // XXX Could we use a read/write lock instead of a synchronized here?
        // We acquire a write lock and as soon as add the connector to the
        // connectors we downgrade to a read lock.
        if (!connectors.contains(connector)) {
            connectors.add(connector)
            if (controlInputStream != null) {
                var controlInputStream: PushSourceStream? = null
                controlInputStream = try {
                    connector.connector!!.controlInputStream
                } catch (ioe: IOException) {
                    throw UndeclaredThrowableException(ioe)
                }
                if (controlInputStream != null) {
                    this.controlInputStream!!.addStream(connector, controlInputStream)
                }
            }
            if (controlOutputStream != null) {
                var controlOutputStream: OutputDataStream? = null
                controlOutputStream = try {
                    connector.connector!!.controlOutputStream
                } catch (ioe: IOException) {
                    throw UndeclaredThrowableException(ioe)
                }
                if (controlOutputStream != null) {
                    this.controlOutputStream!!.addStream(connector, controlOutputStream)
                }
            }
            if (dataInputStream != null) {
                var dataInputStream: PushSourceStream? = null
                dataInputStream = try {
                    connector.connector!!.dataInputStream
                } catch (ioe: IOException) {
                    throw UndeclaredThrowableException(ioe)
                }
                if (dataInputStream != null) {
                    this.dataInputStream!!.addStream(connector, dataInputStream)
                }
            }
            if (dataOutputStream != null) {
                var dataOutputStream: OutputDataStream? = null
                dataOutputStream = try {
                    connector.connector!!.dataOutputStream
                } catch (ioe: IOException) {
                    throw UndeclaredThrowableException(ioe)
                }
                if (dataOutputStream != null) {
                    this.dataOutputStream!!.addStream(connector, dataOutputStream)
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        if (controlInputStream != null) {
            controlInputStream!!.close()
            controlInputStream = null
        }
        if (controlOutputStream != null) {
            controlOutputStream!!.close()
            controlOutputStream = null
        }
        if (dataInputStream != null) {
            dataInputStream!!.close()
            dataInputStream = null
        }
        if (dataOutputStream != null) {
            dataOutputStream!!.close()
            dataOutputStream = null
        }
        closed = true
        for (connectorDesc in connectors) connectorDesc.connector!!.close()
    }

    @Synchronized
    @Throws(IOException::class)
    override fun getControlInputStream(): PushSourceStream {
        if (controlInputStream == null) {
            controlInputStream = PushSourceStreamImpl(this, false)
            for (connectorDesc in connectors) {
                val controlInputStream = connectorDesc.connector!!.controlInputStream
                if (controlInputStream != null) {
                    this.controlInputStream!!.addStream(connectorDesc, controlInputStream)
                }
            }
        }
        return controlInputStream!!
    }

    @Synchronized
    @Throws(IOException::class)
    override fun getControlOutputStream(): OutputDataStreamImpl {
        check(!closed) { "Connector closed." }
        if (controlOutputStream == null) {
            controlOutputStream = OutputDataStreamImpl(this, false)
            for (connectorDesc in connectors) {
                val controlOutputStream = connectorDesc.connector!!.controlOutputStream
                if (controlOutputStream != null) {
                    this.controlOutputStream!!.addStream(connectorDesc, controlOutputStream)
                }
            }
        }
        return controlOutputStream!!
    }

    @Synchronized
    @Throws(IOException::class)
    override fun getDataInputStream(): PushSourceStream {
        if (dataInputStream == null) {
            dataInputStream = PushSourceStreamImpl(this, true)
            for (connectorDesc in connectors) {
                val dataInputStream = connectorDesc.connector!!.dataInputStream
                if (dataInputStream != null) {
                    this.dataInputStream!!.addStream(connectorDesc, dataInputStream)
                }
            }
        }
        return dataInputStream!!
    }

    @Synchronized
    @Throws(IOException::class)
    override fun getDataOutputStream(): OutputDataStreamImpl {
        check(!closed) { "Connector closed." }
        if (dataOutputStream == null) {
            dataOutputStream = OutputDataStreamImpl(this, true)
            for (connectorDesc in connectors) {
                val dataOutputStream = connectorDesc.connector!!.dataOutputStream
                if (dataOutputStream != null) {
                    this.dataOutputStream!!.addStream(connectorDesc, dataOutputStream)
                }
            }
        }
        return dataOutputStream!!
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    override fun getReceiveBufferSize(): Int {
        return -1
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    override fun getRTCPBandwidthFraction(): Double {
        return (-1).toDouble()
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    override fun getRTCPSenderBandwidthFraction(): Double {
        return (-1).toDouble()
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    override fun getSendBufferSize(): Int {
        return -1
    }

    @Synchronized
    fun removeConnector(connector: RTPConnectorDesc) {
        if (connectors.contains(connector)) {
            if (controlInputStream != null) controlInputStream!!.removeStreams(connector)
            if (controlOutputStream != null) controlOutputStream!!.removeStreams(connector)
            if (dataInputStream != null) dataInputStream!!.removeStreams(connector)
            if (dataOutputStream != null) dataOutputStream!!.removeStreams(connector)
            connectors.remove(connector)
        }
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    @Throws(IOException::class)
    override fun setReceiveBufferSize(receiveBufferSize: Int) {
        // TODO Auto-generated method stub
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    @Throws(IOException::class)
    override fun setSendBufferSize(sendBufferSize: Int) {
        // TODO Auto-generated method stub
    }

    /**
     * Writes an `RTCPFeedbackMessage` into a destination identified by a specific
     * `MediaStream`.
     *
     * @param controlPayload
     * @param destination
     * @return `true` if the `controlPayload` was written into the
     * `destination`; otherwise, `false`
     */
    fun writeControlPayload(controlPayload: Payload, destination: MediaStream): Boolean {
        val controlOutputStream = controlOutputStream
        return controlOutputStream?.writeControlPayload(controlPayload, destination) ?: false
    }
}