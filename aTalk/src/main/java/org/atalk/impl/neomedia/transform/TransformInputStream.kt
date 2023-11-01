/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform

import org.atalk.impl.neomedia.RTPConnectorInputStream
import org.atalk.service.neomedia.RawPacket
import java.io.Closeable
import java.net.DatagramPacket

/**
 * Extends `RTPConnectorInputStream` with transform logic.
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
abstract class TransformInputStream<T : Closeable?>
/**
 * Initializes a new `TransformInputStream` which is to transform the packets received
 * from a specific (network) socket.
 *
 * @param socket the (network) socket from which packets are to be received and transformed by the new instance
 */
protected constructor(socket: T) : RTPConnectorInputStream<T>(socket) {
    /**
     * Gets the `PacketTransformer` which is used to reverse-transform packets.
     *
     * @return the `PacketTransformer` which is used to reverse-transform packets
     */
    /**
     * Sets the `PacketTransformer` which is to be used to reverse-transform packets. Set to
     * `null` to disable transformation.
     *
     * @param transformer the `PacketTransformer` which is to be used to reverse-transform packets.
     */
    /**
     * The user defined `PacketTransformer` which is used to reverse transform packets.
     */
    var transformer: PacketTransformer? = null

    /**
     * Creates a new `RawPacket` array from a specific `DatagramPacket` in order to
     * have this instance receive its packet data through its [.read]
     * method. Reverse-transforms the received packet.
     *
     * @param datagramPacket the `DatagramPacket` containing the packet data
     * @return a new `RawPacket` array containing the packet data of the specified
     * `DatagramPacket` or possibly its modification; `null` to ignore the
     * packet data of the specified `DatagramPacket` and not make it available to
     * this instance through its [.read] method
     * @see RTPConnectorInputStream.createRawPacket
     */
    override fun createRawPacket(datagramPacket: DatagramPacket): Array<RawPacket?> {
        val pkts = super.createRawPacket(datagramPacket)

        // Don't try to transform invalid (e.g. empty) packets.
        for (i in pkts.indices) {
            val pkt = pkts[i]
            if (pkt != null && pkt.isInvalid)
                pkts[i] = null // null elements are ignored
        }
        val transformer = transformer
        return transformer?.reverseTransform(pkts) ?: pkts
    }
}