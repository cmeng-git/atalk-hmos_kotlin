/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import org.atalk.service.neomedia.StreamConnector
import java.io.IOException
import java.net.Socket

/**
 * RTPConnector implementation for UDP.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
open class RTPConnectorTCPImpl
/**
 * Initializes a new `RTPConnectorTCPImpl` which is to use a given pair of sockets for
 * RTP and RTCP traffic specified in the form of a `StreamConnector`.
 *
 * @param connector
 * the pair of sockets for RTP and RTCP traffic the new instance is to use
 */
(connector: StreamConnector?) : AbstractRTPConnector(connector) {
    /**
     * The TCP socket this instance uses to send and receive RTP packets.
     */
    var dataSocket: Socket? = null
        /**
         * Gets the TCP socket this instance uses to send and receive RTP packets.
         */
        get() {
            field ?: connector.dataTCPSocket
            return field
        }

    /**
     * The TCP socket this instance uses to send and receive RTCP packets.
     */
    var controlSocket: Socket? = null
        /**
         * Gets the TCP Socket this instance uses to send and receive RTCP packets.
         */
        get() {
            field ?: connector.controlTCPSocket
            return field
        }

    /**
     * Creates the RTCP packet input stream to be used by `RTPManager`.
     *
     * @return a new RTCP packet input stream to be used by `RTPManager`
     * @throws IOException
     * if an error occurs during the creation of the RTCP packet input stream
     */
    @Throws(IOException::class)
    override fun createControlInputStream(): RTPConnectorInputStream<*> {
        return RTPConnectorTCPInputStream(controlSocket)
    }

    /**
     * Creates the RTCP packet output stream to be used by `RTPManager`.
     *
     * @return a new RTCP packet output stream to be used by `RTPManager`
     * @throws IOException
     * if an error occurs during the creation of the RTCP packet output stream
     */
    @Throws(IOException::class)
    override fun createControlOutputStream(): RTPConnectorOutputStream? {
        return RTPConnectorTCPOutputStream(controlSocket)
    }

    /**
     * Creates the RTP packet input stream to be used by `RTPManager`.
     *
     * @return a new RTP packet input stream to be used by `RTPManager`
     * @throws IOException
     * if an error occurs during the creation of the RTP packet input stream
     */
    @Throws(IOException::class)
    override fun createDataInputStream(): RTPConnectorInputStream<*> {
        return RTPConnectorTCPInputStream(dataSocket)
    }

    /**
     * Creates the RTP packet output stream to be used by `RTPManager`.
     *
     * @return a new RTP packet output stream to be used by `RTPManager`
     * @throws IOException
     * if an error occurs during the creation of the RTP packet output stream
     */
    @Throws(IOException::class)
    override fun createDataOutputStream(): RTPConnectorOutputStream? {
        return RTPConnectorTCPOutputStream(dataSocket)
    }
}