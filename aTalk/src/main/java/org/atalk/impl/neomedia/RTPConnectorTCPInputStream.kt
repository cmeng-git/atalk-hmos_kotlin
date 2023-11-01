/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import org.atalk.impl.neomedia.transform.TransformInputStream
import timber.log.Timber
import java.io.IOException
import java.net.DatagramPacket
import java.net.Socket

/**
 * RTPConnectorInputStream implementation for TCP protocol.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class RTPConnectorTCPInputStream
/**
 * Initializes a new `RTPConnectorInputStream` which is to receive packet data from a
 * specific TCP socket.
 *
 * @param socket the TCP socket the new instance is to receive data from
 */
(socket: Socket?) : TransformInputStream<Socket?>(socket) {
    /**
     * Receive packet.
     *
     * @param p packet for receiving
     * @throws IOException if something goes wrong during receiving
     */
    @Throws(IOException::class)
    override fun receive(p: DatagramPacket) {
        var data: ByteArray?
        var len: Int
        try {
            data = p.data
            len = socket!!.getInputStream().read(data)
        } catch (e: Exception) {
            data = null
            len = -1
            Timber.i("problem read: %s", e.message)
        }
        if (len > 0) {
            p.data = data
            p.length = len
            p.address = socket!!.inetAddress
            p.port = socket.port
        } else {
            throw IOException("Failed to read on TCP socket")
        }
    }

    @Throws(IOException::class)
    override fun setReceiveBufferSize(receiveBufferSize: Int) {
        socket!!.receiveBufferSize = receiveBufferSize
    }
}