/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import org.atalk.service.neomedia.RawPacket
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * RTPConnectorOutputStream implementation for TCP protocol.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
open class RTPConnectorTCPOutputStream
/**
 * Initializes a new `RTPConnectorTCPOutputStream`.
 *
 * @param socket a `Socket`
 */
(
        /**
         * TCP socket used to send packet data
         */
        private val socket: Socket?) : RTPConnectorOutputStream() {
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
        socket!!.getOutputStream().write(packet.buffer, packet.offset, packet.length)
    }

    /**
     * Returns whether or not this `RTPConnectorOutputStream` has a valid socket.
     *
     * @return `true`if this `RTPConnectorOutputStream` has a valid socket, and `false` otherwise.
     */
    override fun isSocketValid(): Boolean {
        return socket != null
    }
}