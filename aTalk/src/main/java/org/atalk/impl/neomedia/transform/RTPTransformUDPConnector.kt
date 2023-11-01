/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform

import org.atalk.impl.neomedia.RTPConnectorUDPImpl
import org.atalk.impl.neomedia.RTPConnectorUDPInputStream
import org.atalk.service.neomedia.StreamConnector
import timber.log.Timber
import java.io.IOException

/**
 * TransformConnector implements the RTPConnector interface. RTPConnector is originally designed for
 * programmers to abstract the underlying transport mechanism for RTP control and data from the
 * RTPManager. However, it provides the possibility to modify / transform the RTP and RTCP packets
 * before they are sent to network, or after the have been received from the network.
 *
 *
 * The RTPConnector interface is very powerful. But just to perform packets transformation, we do
 * not need all the flexibility. So, we designed this TransformConnector, which uses UDP to transfer
 * RTP/RTCP packets just like normal RTP stack, and then provides the TransformInputStream interface
 * for people to define their own transformation.
 *
 *
 * With TransformConnector, people can implement RTP/RTCP packets transformation and/or manipulation
 * by implementing the TransformEngine interface.
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 *
 * @see TransformEngine
 *
 * @see RTPConnector
 *
 * @see RTPManager
 */
open class RTPTransformUDPConnector
/**
 * Initializes a new `TransformConnector` which is to use a given pair of datagram
 * sockets for RTP and RTCP traffic specified in the form of a `StreamConnector`.
 *
 * @param connector the pair of datagram sockets for RTP and RTCP traffic the new instance is to use
 */
(connector: StreamConnector?) : RTPConnectorUDPImpl(connector) {
    /**
     * Gets the customized `TransformEngine` which contains the concrete transform logic.
     *
     * @return the `TransformEngine` which contains the concrete transform logic
     */
    /**
     * The customized `TransformEngine` which contains the concrete transform logic.
     */
    var engine: TransformEngine? = null
        private set

    /**
     * Overrides RTPConnectorImpl#createControlInputStream() to use TransformInputStream.
     */
    @Throws(IOException::class)
    override fun createControlInputStream(): RTPConnectorUDPInputStream {
        val controlInputStream = ControlTransformInputStream(controlSocket!!)
        controlInputStream.transformer = rtcpTransformer
        return controlInputStream
    }

    /**
     * Overrides RTPConnectorImpl#createControlOutputStream() to use TransformOutputStream.
     */
    @Throws(IOException::class)
    override fun createControlOutputStream(): TransformUDPOutputStream {
        val controlOutputStream = TransformUDPOutputStream(controlSocket)
        controlOutputStream.transformer = rtcpTransformer
        return controlOutputStream
    }

    /**
     * Overrides RTPConnectorImpl#createDataInputStream() to use TransformInputStream.
     */
    @Throws(IOException::class)
    override fun createDataInputStream(): RTPConnectorUDPInputStream {
        val dataInputStream = RTPConnectorUDPInputStream(dataSocket)
        dataInputStream.transformer = rtpTransformer
        return dataInputStream
    }

    /**
     * Overrides RTPConnectorImpl#createDataOutputStream() to use TransformOutputStream.
     */
    @Throws(IOException::class)
    override fun createDataOutputStream(): TransformUDPOutputStream {
        val dataOutputStream = TransformUDPOutputStream(dataSocket)
        dataOutputStream.transformer = rtpTransformer
        return dataOutputStream
    }

    /**
     * Gets the `PacketTransformer` specified by the current `TransformerEngine`
     * which is used to transform and reverse-transform RTCP packets.
     *
     * @return the `PacketTransformer` specified by the current `TransformEngine`
     * which is used to transform and reverse-transform RTCP packets if there is currently a
     * `TransformEngine` and it specifies a `TransformEngine` for RTCP data;
     * otherwise, `null`
     */
    private val rtcpTransformer: PacketTransformer?
        get() {
            val engine = engine
            return engine?.rtcpTransformer
        }

    /**
     * Gets the `PacketTransformer` specified by the current `TransformerEngine`
     * which is used to transform and reverse-transform RTP packets.
     *
     * @return the `PacketTransformer` specified by the current `TransformEngine`
     * which is used to transform and reverse-transform RTP packets if there is currently a
     * `TransformEngine` and it specifies a `TransformEngine` for RTP data;
     * otherwise, `null`
     */
    private val rtpTransformer: PacketTransformer?
        get() {
            val engine = engine
            return engine?.rtpTransformer
        }

    /**
     * Sets the customized `TransformEngine` which contains the concrete transform logic.
     *
     * @param engine the `TransformEngine` which contains the concrete transform logic
     */
    fun setEngine(engine: TransformEngine) {
        if (this.engine != engine) {
            this.engine = engine

            /*
             * Deliver the new PacketTransformers defined by the new TransformEngine to the
             * respective streams.
             */
            val controlInputStream = try {
                getControlInputStream(false) as RTPConnectorUDPInputStream?
            } catch (ioex: IOException) {
                Timber.e(ioex, "The impossible happened")
                null
            }
            if (controlInputStream != null) controlInputStream.transformer = rtcpTransformer

            val controlOutputStream = try {
                getControlOutputStream(false) as TransformUDPOutputStream?
            } catch (ioex: IOException) {
                Timber.e(ioex, "The impossible happened")
                null
            }
            if (controlOutputStream != null) controlOutputStream.transformer = rtcpTransformer

            val dataInputStream = try {
                getDataInputStream(false) as RTPConnectorUDPInputStream?
            } catch (ioex: IOException) {
                Timber.e(ioex, "The impossible happened")
                null
            }
            if (dataInputStream != null) dataInputStream.transformer = rtpTransformer

            val dataOutputStream = try {
                getDataOutputStream(false) as TransformUDPOutputStream?
            } catch (ioex: IOException) {
                Timber.e(ioex, "The impossible happened")
                null
            }

            if (dataOutputStream != null) dataOutputStream.transformer = rtpTransformer
        }
    }
}