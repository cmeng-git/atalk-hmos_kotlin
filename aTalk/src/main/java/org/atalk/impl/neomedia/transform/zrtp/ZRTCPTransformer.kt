/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.zrtp

import org.atalk.impl.neomedia.transform.SinglePacketTransformer
import org.atalk.service.neomedia.RawPacket

/**
 * PacketTransformer that delegates the forward/reverse transformation of packets to different
 * packet transformers.
 *
 * @author Werner Dittmann <Werner.Dittmann></Werner.Dittmann>@t-online.de>
 */
class ZRTCPTransformer : SinglePacketTransformer() {
    /**
     * We support different SRTCP contexts for input and output traffic:
     *
     * Transform() uses the srtcpOut to perform encryption reverseTransform() uses srtcpIn to
     * perform decryption
     */
    private var srtcpIn: SinglePacketTransformer? = null
    private var srtcpOut: SinglePacketTransformer? = null

    /**
     * Close the transformer engine.
     *
     * The close functions closes all stored default crypto contexts. This deletes key data and
     * forces a cleanup of the crypto contexts.
     */
    override fun close() {
        if (srtcpOut != null) {
            srtcpOut!!.close()
            srtcpOut = null
        }
        if (srtcpIn != null) {
            srtcpIn!!.close()
            srtcpIn = null
        }
    }

    /**
     * Encrypt a SRTCP packet
     *
     * Currently SRTCP packet encryption / decryption is not supported
     * So this method does not change the packet content
     *
     * @param pkt plain SRTCP packet to be encrypted
     * @return encrypted SRTCP packet
     */
    override fun transform(pkt: RawPacket): RawPacket? {
        return if (srtcpOut == null) pkt
        else srtcpOut!!.transform(pkt)
    }

    /**
     * Decrypt a SRTCP packet
     *
     * Currently SRTCP packet encryption / decryption is not supported
     * So this method does not change the packet content
     *
     * @param pkt encrypted SRTCP packet to be decrypted
     * @return decrypted SRTCP packet
     */
    override fun reverseTransform(pkt: RawPacket): RawPacket? {
        return if (srtcpIn == null) pkt else srtcpIn!!.reverseTransform(pkt)
    }

    /**
     * @param srtcpIn the srtcpIn to set
     */
    fun setSrtcpIn(srtcpIn: SinglePacketTransformer?) {
        this.srtcpIn = srtcpIn
    }

    /**
     * @param srtcpOut the srtcpOut to set
     */
    fun setSrtcpOut(srtcpOut: SinglePacketTransformer?) {
        this.srtcpOut = srtcpOut
    }
}