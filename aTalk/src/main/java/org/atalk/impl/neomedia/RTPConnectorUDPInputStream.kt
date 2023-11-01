/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import org.atalk.impl.neomedia.transform.TransformInputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket

/**
 * RTPConnectorInputStream implementation for UDP protocol.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
open class RTPConnectorUDPInputStream
/**
 * Initializes a new `RTPConnectorInputStream` which is to receive packet data from a specific UDP socket.
 *
 * @param socket the UDP socket the new instance is to receive data from
 */
(socket: DatagramSocket?) : TransformInputStream<DatagramSocket?>(socket) {
    /**
     * Receive packet.
     *
     * @param p packet for receiving
     * @throws IOException if something goes wrong during receiving
     */
    @Throws(IOException::class)
    override fun receive(p: DatagramPacket) {
        socket!!.receive(p)
    }

    @Throws(IOException::class)
    override fun setReceiveBufferSize(receiveBufferSize: Int) {
        socket!!.receiveBufferSize = receiveBufferSize
    }
}