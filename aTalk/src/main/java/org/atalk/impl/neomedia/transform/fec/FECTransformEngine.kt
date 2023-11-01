/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.fec

import org.atalk.impl.neomedia.transform.PacketTransformer
import org.atalk.impl.neomedia.transform.TransformEngine
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.RawPacket
import timber.log.Timber

/**
 * Implements a [PacketTransformer] and
 * [TransformEngine] for RFC5109.
 *
 * @author Boris Grozev
 * @author bbaldino
 * @author Eng Chong Meng
 */
open class FECTransformEngine(
        /**
         * The fec type this transform engine will instantiate
         */
        private var fecType: FecType, incomingPT: Byte, outgoingPT: Byte, private val mediaStream: MediaStream) : TransformEngine, PacketTransformer {

    enum class FecType {
        ULPFEC,
        FLEXFEC_03
    }

    /**
     * The payload type for incoming ulpfec (RFC5109) packets.
     *
     * The special value "-1" is used to effectively disable reverse-transforming packets.
     */
    private var incomingPT: Byte = -1

    /**
     * The payload type for outgoing ulpfec (RFC5109) packets.
     *
     * The special value "-1" is used to effectively disable transforming packets.
     */
    private var outgoingPT: Byte = -1

    /**
     * The rate at which ulpfec packets will be generated and added to the stream by this
     * `PacketTransformer`. An ulpfec packet will be generated for every `fecRate`
     * media packets. If set to 0, no ulpfec packets will be generated.
     */
    private var fecRate = 0

    /**
     * Maps an SSRC to a `AbstractFECReceiver` to be used for packets with that SSRC.
     */
    private val fecReceivers = HashMap<Long, AbstractFECReceiver>()

    /**
     * Maps an SSRC to a `FECSender` to be used for packets with that SSRC.
     */
    private val fecSenders = HashMap<Long, FECSender>()

    /**
     * Initializes a new `FECTransformEngine` instance.
     *
     * incomingPT the RTP payload type number for incoming ulpfec packet.
     * outgoingPT the RTP payload type number for outgoing ulpfec packet.
     */
    init {
        setIncomingPT(incomingPT)
        setOutgoingPT(outgoingPT)
    }

    private fun getPrimarySsrc(ssrc: Long?): Long {
        if (ssrc == null) {
            return -1
        }
        val receiver = mediaStream.mediaStreamTrackReceiver ?: return -1
        val encoding = receiver.findRTPEncodingDesc(ssrc) ?: return -1
        return encoding.primarySSRC
    }

    /**
     * {@inheritDoc}
     *
     * Assumes that all packets in `pkts` have the same SSRC. Reverse- transforms using the
     * `FECReceiver` for the SSRC found in `pkts`.
     */
    override fun reverseTransform(pkts: Array<RawPacket?>): Array<RawPacket?> {
        if (incomingPT.toInt() == -1) return pkts

        // Assumption: all packets in pkts have the same SSRC
        val ssrc = findSSRC(pkts)
        val primarySsrc = getPrimarySsrc(ssrc)
        if (primarySsrc == -1L) {
            return pkts
        }
        var fecReceiver: AbstractFECReceiver?
        synchronized(fecReceivers) {
            fecReceiver = fecReceivers[primarySsrc]
            if (fecReceiver == null) {
                fecReceiver = when (fecType) {
                    FecType.ULPFEC -> {
                        ULPFECReceiver(primarySsrc, incomingPT)
                    }
                    FecType.FLEXFEC_03 -> {
                        FlexFec03Receiver(primarySsrc, incomingPT)
                    }
                }
                fecReceivers[primarySsrc] = fecReceiver!!
            }
        }
        return fecReceiver!!.reverseTransform(pkts)
    }

    /**
     * {@inheritDoc}
     *
     * Adds ulpfec packets to the stream (one ulpfec packet after every `fecRate` media
     * packets.
     */
    override fun transform(pkts: Array<RawPacket?>): Array<RawPacket?> {
        if (outgoingPT.toInt() == -1)
            return pkts

        val ssrc = findSSRC(pkts) ?: return pkts
        var fpt: FECSender?
        synchronized(fecSenders) {
            fpt = fecSenders[ssrc]
            if (fpt == null) {
                fpt = FECSender(ssrc, fecRate, outgoingPT)
                fecSenders[ssrc] = fpt!!
            }
        }
        return fpt!!.transform(pkts)
    }

    /**
     * {@inheritDoc}
     */
    override fun close() {
        var receivers: Collection<AbstractFECReceiver>
        var senders: Collection<FECSender>
        synchronized(fecReceivers) {
            receivers = fecReceivers.values
            fecReceivers.clear()
        }
        synchronized(fecSenders) {
            senders = fecSenders.values
            fecSenders.clear()
        }
        for (fecReceiver in receivers) fecReceiver.close()
        for (fecSender in senders) fecSender.close()
    }

    /**
     * {@inheritDoc}
     */
    override val rtpTransformer: PacketTransformer
        get() = this

    /**
     * {@inheritDoc}
     *
     * We don't touch RTCP.
     */
    override val rtcpTransformer: PacketTransformer?
        get() = null

    /**
     * Sets the payload type for incoming ulpfec packets.
     *
     * @param incomingPT the payload type to set
     */
    fun setIncomingPT(incomingPT: Byte) {
        this.incomingPT = incomingPT
        synchronized(fecReceivers) { for (f in fecReceivers.values) f.setPayloadType(incomingPT) }
        Timber.d("Setting payload type for incoming ulpfec: %s", incomingPT)
    }

    /**
     * Sets the payload type for outgoing ulpfec packets.
     *
     * @param outgoingPT the payload type to set
     */
    fun setOutgoingPT(outgoingPT: Byte) {
        this.outgoingPT = outgoingPT
        synchronized(fecSenders) { for (f in fecSenders.values) f.setUlpfecPT(outgoingPT) }
        Timber.d("Setting payload type for outgoing ulpfec: %s", outgoingPT)
    }

    /**
     * Sets the rate at which ulpfec packets will be generated and added to the stream by this
     * `PacketTransformer`.
     *
     * @param fecRate the rate to set, should be in [0, 16]
     */
    fun setFecRate(fecRate: Int) {
        synchronized(fecSenders) { for (f in fecSenders.values) f.setFecRate(fecRate) }
        this.fecRate = fecRate
    }

    /**
     * Get the rate at which ulpfec packets will be generated and added to the stream by this
     * `PacketTransformer` .
     *
     * @return the rate at which ulpfec packets will be generated and added to the stream by this
     * `PacketTransformer`.
     */
    fun getFecRate(): Int {
        return fecRate
    }

    /**
     * Returns the SSRC in the first non-null element of `pkts` or
     * `null` if all elements of `pkts` are `null`
     *
     * @param pkts array of to search for SSRC
     * @return the SSRC in the first non-null element of `pkts` or
     * `null` if all elements of `pkts` are `null`
     */
    private fun findSSRC(pkts: Array<RawPacket?>?): Long? {
        var ret: Long? = null
        if (pkts != null) {
            for (p in pkts) {
                if (p != null) {
                    ret = p.getSSRCAsLong()
                    break
                }
            }
        }
        return ret
    }

    companion object {
        /**
         * Initial size for newly allocated byte arrays.
         */
        const val INITIAL_BUFFER_SIZE = 1500
    }
}