/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform

import org.atalk.impl.neomedia.RTPConnectorTCPOutputStream
import org.atalk.service.neomedia.RawPacket
import java.net.Socket

/**
 * Extends `RTPConnectorTCPOutputStream` with transform logic.
 *
 * In this implementation, TCP socket is used to send the data out. When a normal RTP/RTCP packet is
 * passed down from RTPManager, we first transform the packet using user define PacketTransformer
 * and then send it out through network to all the stream targets.
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
class TransformTCPOutputStream(socket: Socket?) : RTPConnectorTCPOutputStream(socket), TransformOutputStream {
    /**
     * The `TransformOutputStream` which aids this instance in implementing the interface in question.
     */
    private val _impl = TransformOutputStreamImpl(this)

    /**
     * {@inheritDoc}
     */
    /**
     * {@inheritDoc}
     */
    override var transformer: PacketTransformer?
        get() = _impl.transformer
        set(transformer) {
            _impl.transformer = transformer
        }

    /**
     * {@inheritDoc}
     *
     * Transforms the array of `RawPacket`s returned by the super
     * [.packetize] implementation using the associated `PacketTransformer`.
     */
    override fun packetize(buf: ByteArray, off: Int, len: Int, context: Any?): Array<RawPacket?> {
        val pkts = super.packetize(buf, off, len, context)
        return _impl.transform(pkts, context)
    }
}