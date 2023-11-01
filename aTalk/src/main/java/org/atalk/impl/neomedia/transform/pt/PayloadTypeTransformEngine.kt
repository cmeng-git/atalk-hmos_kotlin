/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.pt

import net.sf.fmj.media.rtp.RTPHeader
import org.atalk.impl.neomedia.transform.PacketTransformer
import org.atalk.impl.neomedia.transform.SinglePacketTransformerAdapter
import org.atalk.impl.neomedia.transform.TransformEngine
import org.atalk.service.neomedia.RawPacket

/**
 * We use this engine to change payload types of outgoing RTP packets if needed. This is necessary
 * so that we can support the RFC3264 case where the answerer has the right to declare what payload
 * type mappings it wants to receive even if they are different from those in the offer. RFC3264
 * claims this is for support of legacy protocols such as H.323 but we've been bumping with a number
 * of cases where multi-component pure SIP systems also need to behave this way.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class PayloadTypeTransformEngine : SinglePacketTransformerAdapter(), TransformEngine {
    /**
     * The mapping we use to override payloads. By default it is empty and we do nothing, packets
     * are passed through without modification. Maps source payload to target payload.
     */
    private val mappingOverrides: MutableMap<Byte, Byte> = HashMap()

    /**
     * This map is a copy of `mappingOverride` that we use during actual transformation
     */
    private var mappingOverridesCopy: Map<Byte, Byte>? = null

    /**
     * Checks if there are any override mappings, if no setting just pass through the packet. If the
     * `RawPacket` payload has entry in mappings to override, we override packet payload type.
     *
     * @param pkt the RTP `RawPacket` that we check for need to change payload type.
     *
     * @return the updated `RawPacket` instance containing the changed payload type.
     */
    override fun transform(pkt: RawPacket): RawPacket {
        if (mappingOverridesCopy == null
                || mappingOverridesCopy!!.isEmpty()
                || pkt.version != RTPHeader.VERSION)
            return pkt

        val newPT = mappingOverridesCopy!![pkt.payloadType]
        if (newPT != null)
            pkt.payloadType = newPT
        return pkt
    }

    /**
     * Closes this `PacketTransformer` i.e. releases the resources allocated by it and prepares it for garbage collection.
     */
    override fun close() {}

    /**
     * Returns a reference to this class since it is performing RTP transformations in here.
     *
     * @return a reference to `this` instance of the `PayloadTypeTransformEngine`.
     */
    override val rtpTransformer: PacketTransformer
        get() = this

    /**
     * Always returns `null` since this engine does not require any RTCP transformations.
     *
     * @return `null` since this engine does not require any RTCP transformations.
     */
    override val rtcpTransformer: PacketTransformer?
        get() = null

    /**
     * Adds an additional RTP payload type mapping used to override the payload type of outgoing RTP
     * packets. If an override for `originalPT<tt></tt>, was already being overridden, this call
     * is simply going to update the override to the new one.
     *
     *
     * This method creates a copy of the local overriding map so that mapping overrides could be
     * set during a call (e.g. after a SIP re-INVITE) in a thread-safe way without using
     * synchronization.
     *
     * @param originalPt the payload type that we are overriding
     * @param overridePt the payload type that we are overriding it with
    ` */
    fun addPTMappingOverride(originalPt: Byte, overridePt: Byte) {
        val existingOverride = mappingOverrides[originalPt]
        if (existingOverride == null || existingOverride != overridePt) {
            mappingOverrides[originalPt] = overridePt
            mappingOverridesCopy = HashMap(mappingOverrides)
        }
    }
}