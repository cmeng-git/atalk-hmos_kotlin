/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import org.atalk.service.neomedia.RawPacket
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * RTPConnectorOutputStream implementation for UDP protocol.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
open class RTPConnectorUDPOutputStream
/**
 * Initializes a new `RTPConnectorUDPOutputStream`.
 *
 * @param socket a `DatagramSocket`
 */
(
    /**
     * UDP socket used to send packet data
     */
    private val socket: DatagramSocket?) : RTPConnectorOutputStream() {

    /**
     * Sends a specific `RawPacket` through this `OutputDataStream` to a specific `InetSocketAddress`.
     *
     * @param packet the `RawPacket` to send through this `OutputDataStream` to the specified `target`
     * @param target the `InetSocketAddress` to which the specified `packet` is to be sent
     * through this `OutputDataStream`
     * @throws IOException if anything goes wrong while sending the specified `packet` through this
     * `OutputDataStream` to the specified `target`
     */
    @Throws(IOException::class)
    override fun sendToTarget(packet: RawPacket, target: InetSocketAddress) {
        socket!!.send(DatagramPacket(packet.buffer, packet.offset, packet.length,
                target.address, target.port))
    }

    /**
     * Returns whether or not this `RTPConnectorOutputStream` has a valid socket.
     *
     * @returns true if this `RTPConnectorOutputStream` has a valid socket, false otherwise
     */
    override fun isSocketValid(): Boolean {
        return socket != null
    }
}