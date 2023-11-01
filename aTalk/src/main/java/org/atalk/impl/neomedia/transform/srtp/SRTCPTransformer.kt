/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.srtp

import org.atalk.impl.neomedia.transform.SinglePacketTransformer
import org.atalk.service.neomedia.RawPacket

/**
 * SRTCPTransformer implements PacketTransformer. It encapsulate the encryption / decryption logic
 * for SRTCP packets
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Werner Dittmann <Werner.Dittmann></Werner.Dittmann>@t-online.de>
 * @author Eng Chong Meng
 */
class SRTCPTransformer(private var forwardFactory: SrtpContextFactory?, private var reverseFactory: SrtpContextFactory?) : SinglePacketTransformer() {
    /**
     * All the known SSRC's corresponding SRTCPCryptoContexts
     */
    private val contexts: MutableMap<Int, SrtcpCryptoContext?>

    /**
     * Constructs an `SRTCPTransformer`, sharing its `SRTPContextFactory` instances
     * with a given `SRTPTransformer`.
     *
     * @param srtpTransformer the `SRTPTransformer` with which this `SRTCPTransformer` will share its
     * `SRTPContextFactory` instances.
     */
    constructor(srtpTransformer: SRTPTransformer) : this(srtpTransformer.forwardFactory, srtpTransformer.reverseFactory)

    /**
     * Constructs a SRTCPTransformer object.
     *
     * @param factory The associated context factory for both transform directions.
     */
    constructor(factory: SrtpContextFactory?) : this(factory, factory)

    /**
     * Constructs a SRTCPTransformer object.
     *
     * forwardFactory The associated context factory for forward transformations.
     * reverseFactory The associated context factory for reverse transformations.
     */
    init {
        contexts = HashMap()
    }

    /**
     * Sets a new key factory when key material has changed.
     *
     * @param factory The associated context factory for transformations.
     * @param forward `true` if the supplied factory is for forward transformations, `false`
     * for the reverse transformation factory.
     */
    fun updateFactory(factory: SrtpContextFactory?, forward: Boolean) {
        synchronized(contexts) {
            if (forward) {
                if (forwardFactory != null && forwardFactory != factory) {
                    forwardFactory!!.close()
                }
                forwardFactory = factory
            } else {
                if (reverseFactory != null && reverseFactory != factory) {
                    reverseFactory!!.close()
                }
                reverseFactory = factory
            }
        }
    }

    /**
     * Closes this `SRTCPTransformer` and the underlying transform engine. It closes all
     * stored crypto contexts. It deletes key data and forces a cleanup of the crypto contexts.
     */
    override fun close() {
        synchronized(contexts) {
            forwardFactory!!.close()
            if (reverseFactory != forwardFactory) reverseFactory!!.close()
            val i = contexts.values.iterator()
            while (i.hasNext()) {
                val context = i.next()
                i.remove()
                context?.close()
            }
        }
    }

    private fun getContext(pkt: RawPacket?, engine: SrtpContextFactory?): SrtcpCryptoContext? {
        val ssrc = pkt!!.rtcpSSRC.toInt()
        var context: SrtcpCryptoContext?
        synchronized(contexts) {
            context = contexts[ssrc]
            if (context == null && engine != null) {
                context = engine.deriveControlContext(ssrc)
                contexts[ssrc] = context
            }
        }
        return context
    }

    /**
     * Decrypts a SRTCP packet
     *
     * @param pkt encrypted SRTCP packet to be decrypted
     * @return decrypted SRTCP packet
     */
    override fun reverseTransform(pkt: RawPacket): RawPacket? {
        val context = getContext(pkt, reverseFactory) ?: return null
        return if (context.reverseTransformPacket(pkt) == SrtpErrorStatus.OK)
            pkt
        else null
    }

    /**
     * Encrypts a SRTCP packet
     *
     * @param pkt plain SRTCP packet to be encrypted
     * @return encrypted SRTCP packet
     */
    override fun transform(pkt: RawPacket): RawPacket? {
        val context = getContext(pkt, forwardFactory)
        return if (context != null) {
            context.transformPacket(pkt)
            pkt
        } else {
            // The packet cannot be encrypted. Thus, do not send it.
            null
        }
    }
}