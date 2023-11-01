/*
 * Copyright @ 2017 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.transform.fec

import org.atalk.impl.neomedia.transform.PacketTransformer
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.RawPacket
import org.atalk.util.RTPUtils
import timber.log.Timber
import java.util.*

/**
 * A [PacketTransformer] which handles incoming fec packets.  This class
 * contains only the generic fec handling logic.
 *
 * @author bgrozev
 * @author bbaldino
 * @author Eng Chong Meng
 */
abstract class AbstractFECReceiver
/**
 * Initialize the FEC receiver
 *
 * @param ssrc the ssrc of the stream on which fec packets will be received
 * @param payloadType the payload type of the fec packets
 */ internal constructor(
        /**
         * The SSRC of the fec stream
         * NOTE that for ulpfec this might be the same as the associated media
         * stream, whereas for flexfec it will be different
         */
        protected var ssrc: Long,
        /**
         * The payload type of the fec stream
         */
        private var payloadType: Byte) : PacketTransformer {
    /**
     * Statistics for this fec receiver
     */
    protected val statistics = Statistics()

    /**
     * Allow disabling of handling of ulpfec packets for testing purposes.
     */
    protected var handleFec = true

    /**
     * Buffer which keeps (copies of) received media packets.
     *
     * We keep them ordered by their RTP sequence numbers, so that
     * we can easily select the oldest one to discard when the buffer is
     * full (when the map has more than `MEDIA_BUFF_SIZE` entries).
     *
     * We keep them in a `Map` so that we can easily search for a
     * packet with a specific sequence number.
     *
     * Note: This might turn out to be inefficient, especially with increased
     * buffer sizes. In the vast majority of cases (e.g. on every received
     * packet) we do an insert at one end and a delete from the other -- this
     * can be optimized. We very rarely (when we receive a packet out of order)
     * need to insert at an arbitrary location.
     * FIXME: Look at using the existing packet cache instead of our own here
     */
    protected val mediaPackets: SortedMap<Int, RawPacket> = TreeMap(RTPUtils.sequenceNumberComparator)

    /**
     * Buffer which keeps (copies of) received fec packets.
     *
     * We keep them ordered by their RTP sequence numbers, so that
     * we can easily select the oldest one to discard when the buffer is
     * full (when the map has more than `FEC_BUFF_SIZE` entries.
     *
     * We keep them in a `Map` so that we can easily search for a
     * packet with a specific sequence number.
     *
     * Note: This might turn out to be inefficient, especially with increased
     * buffer sizes. In the vast majority of cases (e.g. on every received
     * packet) we do an insert at one end and a delete from the other -- this
     * can be optimized. We very rarely (when we receive a packet out of order)
     * need to insert at an arbitrary location.
     * FIXME: Look at using the existing packet cache instead of our own here
     */
    protected val fecPackets: SortedMap<Int, RawPacket> = TreeMap(RTPUtils.sequenceNumberComparator)

    /**
     * Saves `p` into `fecPackets`. If the size of
     * `fecPackets` has reached `FEC_BUFF_SIZE` discards the
     * oldest packet from it.
     *
     * @param p the packet to save.
     */
    private fun saveFec(p: RawPacket) {
        if (fecPackets.size >= FEC_BUF_SIZE) fecPackets.remove(fecPackets.firstKey())
        fecPackets[p.sequenceNumber] = p
    }

    /**
     * Makes a copy of `p` into `mediaPackets`. If the size of
     * `mediaPackets` has reached `MEDIA_BUFF_SIZE` discards
     * the oldest packet from it and reuses it.
     *
     * @param p the packet to copy.
     */
    protected fun saveMedia(p: RawPacket) {
        val newMedia: RawPacket?
        if (mediaPackets.size < MEDIA_BUF_SIZE) {
            newMedia = RawPacket()
            newMedia.buffer = ByteArray(FECTransformEngine.INITIAL_BUFFER_SIZE)
            newMedia.offset = 0
        } else {
            newMedia = mediaPackets.remove(mediaPackets.firstKey())
        }
        val pLen = p.length
        if (pLen > newMedia!!.buffer.size) {
            newMedia.buffer = ByteArray(pLen)
        }
        System.arraycopy(p.buffer, p.offset, newMedia.buffer, 0, pLen)
        newMedia.length = pLen
        newMedia.offset = 0
        mediaPackets[newMedia.sequenceNumber] = newMedia
    }

    /**
     * Sets the ulpfec payload type.
     *
     * @param payloadType the payload type.
     * FIXME(brian): do we need both this and the ability to pass the payload
     * type in the ctor? Can we get rid of this or get rid of the arg in the ctor?
     */
    fun setPayloadType(payloadType: Byte) {
        this.payloadType = payloadType
    }

    /**
     * {@inheritDoc}
     *
     * Don't touch "outgoing".
     */
    override fun transform(pkts: Array<RawPacket?>): Array<RawPacket?> {
        return pkts
    }

    @Synchronized
    override fun reverseTransform(pkts: Array<RawPacket?>): Array<RawPacket?> {
        var packets = pkts
        for (i in packets.indices) {
            val pkt = packets[i] ?: continue

            if (pkt.payloadType == payloadType) {
                // Don't forward it
                packets[i] = null
                statistics.numRxFecPackets++
                if (handleFec) {
                    saveFec(pkt)
                }
            } else {
                if (handleFec) {
                    saveMedia(pkt)
                }
            }
        }
        packets = doReverseTransform(packets)
        return packets
    }

    /**
     * {@inheritDoc}
     */
    override fun close() {
        Timber.i("Closing FEC-Receiver for SSRC: %d. Received %d FEC packets, recovered %s media packets. Recovery failed %d times",
                ssrc, statistics.numRxFecPackets, statistics.numRecoveredPackets, statistics.failedRecoveries)
    }

    /**
     * Perform fec receive logic specific to the fec implementation
     *
     * @param pkts the input media packets
     * @return a RawPacket[] containing the given media packets as well as any
     * media packets that were recovered
     */
    protected abstract fun doReverseTransform(pkts: Array<RawPacket?>): Array<RawPacket?>

    inner class Statistics {
        var numRxFecPackets = 0
        var numRecoveredPackets = 0
        var failedRecoveries = 0
    }

    companion object {
        /**
         * The number of media packets to keep.
         */
        private var MEDIA_BUF_SIZE = 0

        /**
         * The maximum number of ulpfec packets to keep.
         */
        private var FEC_BUF_SIZE = 0

        /**
         * The name of the `ConfigurationService` property which specifies
         * the value of [.MEDIA_BUF_SIZE].
         */
        private const val MEDIA_BUF_SIZE_PNAME = "neomedia.transform.fec.AbstractFECReciever.MEDIA_BUFF_SIZE"

        /**
         * The name of the `ConfigurationService` property which specifies
         * the value of [.FEC_BUF_SIZE].
         */
        private const val FEC_BUF_SIZE_PNAME = "neomedia.transform.fec.AbstractFECReciever.FEC_BUFF_SIZE"

        init {
            val cfg = LibJitsi.configurationService
            var fecBufSize = 32
            var mediaBufSize = 64
            if (cfg != null) {
                fecBufSize = cfg.getInt(FEC_BUF_SIZE_PNAME, fecBufSize)
                mediaBufSize = cfg.getInt(MEDIA_BUF_SIZE_PNAME, mediaBufSize)
            }
            FEC_BUF_SIZE = fecBufSize
            MEDIA_BUF_SIZE = mediaBufSize
        }
    }
}