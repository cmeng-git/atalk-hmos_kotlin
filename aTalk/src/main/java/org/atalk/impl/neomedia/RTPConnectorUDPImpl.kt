/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import org.atalk.service.neomedia.StreamConnector
import java.io.IOException
import java.net.DatagramSocket

/**
 * RTPConnector implementation for UDP.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
open class RTPConnectorUDPImpl
/**
 * Initializes a new `RTPConnectorUDPImpl` which is to use a given pair of datagram
 * sockets for RTP and RTCP traffic specified in the form of a `StreamConnector`.
 *
 * @param connector
 * the pair of datagram sockets for RTP and RTCP traffic the new instance is to use
 */
(connector: StreamConnector?) : AbstractRTPConnector(connector) {
    /**
     * The UDP socket this instance uses to send and receive RTP packets.
     */
    var dataSocket: DatagramSocket? = null
        get() {
            if ( field == null)
                field = connector.dataSocket
            return field
        }

    /**
     * The UDP socket this instance uses to send and receive RTCP packets.
     */
    var controlSocket: DatagramSocket? = null
    get() {
        if (field == null)
            field = connector.controlSocket
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
        return RTCPConnectorInputStream(controlSocket)
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
        return RTPConnectorUDPOutputStream(controlSocket)
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
        return RTPConnectorUDPInputStream(dataSocket)
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
        return RTPConnectorUDPOutputStream(dataSocket)
    }
}