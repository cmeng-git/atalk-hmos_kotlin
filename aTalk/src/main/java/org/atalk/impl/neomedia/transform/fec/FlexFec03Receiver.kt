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

import org.atalk.service.neomedia.RawPacket
import org.atalk.util.ArrayUtils
import org.atalk.util.RTPUtils
import timber.log.Timber
import java.util.*

/**
 * Receive and process FlexFec03 packets, recovering missing packets where possible
 *
 * @author bbaldino
 * @author Eng Chong Meng
 */
class FlexFec03Receiver(mediaSsrc: Long, fecPayloadType: Byte) : AbstractFECReceiver(mediaSsrc, fecPayloadType) {
    /**
     * Helper class to reconstruct missing packets
     */
    private val reconstructor = Reconstructor(mediaPackets)

    /**
     * {@inheritDoc}
     */
    @Synchronized
    override fun doReverseTransform(pkts: Array<RawPacket?>): Array<RawPacket?> {
        var packets = pkts as Array<RawPacket>
        val flexFecPacketsToRemove: MutableSet<Int> = HashSet()
        // Try to recover any missing media packets
        for ((_, value) in fecPackets) {
            val flexFecPacket: FlexFec03Packet = FlexFec03Packet.create(value) ?: continue
            reconstructor.setFecPacket(flexFecPacket)
            if (reconstructor.complete()) {
                flexFecPacketsToRemove.add(flexFecPacket.sequenceNumber)
                continue
            }
            if (reconstructor.canRecover()) {
                Timber.d("Attempting recovery of missing sequence number %s", reconstructor.missingSequenceNumber)
                flexFecPacketsToRemove.add(flexFecPacket.sequenceNumber)
                val recovered = reconstructor.recover()
                if (recovered != null) {
                    Timber.i("Recovered packet %s", recovered.sequenceNumber)
                    statistics.numRecoveredPackets++
                    saveMedia(recovered)
                    packets = ArrayUtils.insert(packets, RawPacket::class.java, recovered)
                } else {
                    Timber.e("Recovery of packet %d failed even though it should have been possible",
                            reconstructor.missingSequenceNumber)
                    statistics.failedRecoveries++
                }
            }
        }
        for (flexFecSeqNum in flexFecPacketsToRemove) {
            fecPackets.remove(flexFecSeqNum)
        }
        return packets  as Array<RawPacket?>
    }

    private class Reconstructor
    /**
     * Initializes a new instance.
     *
     * @param mediaPackets the currently available media packets. Note that
     * this is a reference so it will remain up to date as the map is
     * filled out by the caller.
     */
    (
            /**
             * All available media packets.
             */
            private val mediaPackets: SortedMap<Int, RawPacket>,
    ) {
        /**
         * The FlexFEC packet to be used for recovery.
         */
        private var fecPacket: FlexFec03Packet? = null

        /**
         * We can only recover a single missing packet, so when we check
         * for how many are missing, we'll keep track of the first one we find
         */
        var missingSequenceNumber = -1

        /**
         * The number of missing media packets we've detected for the set
         * FlexFEC packet
         */
        var numMissing = -1
        fun complete(): Boolean {
            return numMissing == 0
        }

        fun canRecover(): Boolean {
            return numMissing == 1
        }

        /**
         * Set the [FlexFec03Packet] to be used for this reconstruction,
         * and check if any media packets protected by this fec packet are missing
         * and can be recovered
         *
         * @param p the [FlexFec03Packet] to be used for this reconstruction
         */
        fun setFecPacket(p: FlexFec03Packet?) {
            if (p == null) {
                Timber.e("Error setting flexfec packet")
                return
            }
            fecPacket = p
            Timber.d("Have %s saved media packets", mediaPackets.size)
            numMissing = 0
            Timber.d("Reconstructor checking if recovery is possible: fec packet %s protects packets:\n%s",
                    p.sequenceNumber, p.protectedSequenceNumbers)
            for (protectedSeqNum in fecPacket!!.protectedSequenceNumbers) {
                Timber.d("Checking if we've received media packet %s", protectedSeqNum)
                if (!mediaPackets.containsKey(protectedSeqNum)) {
                    Timber.d("We haven't, mark as missing")
                    numMissing++
                    missingSequenceNumber = protectedSeqNum
                }
            }
            Timber.d("There were %s missing media packets for flexfec %s",
                    numMissing, p.sequenceNumber)
            if (numMissing > 1) {
                missingSequenceNumber = -1
            }
        }

        /**
         * Initialize the given RawPacket with the RTP header information
         * and payload from fecPacket
         *
         * @param fecPacket the FlexFEC packet being used for recovery
         * @param recoveredPacket the blank RawPacket we're recreating the
         * recovered packet in
         * @return true on success, false otherwise
         */
        private fun startPacketRecovery(fecPacket: FlexFec03Packet?, recoveredPacket: RawPacket): Boolean {
            if (fecPacket!!.length < RawPacket.FIXED_HEADER_SIZE) {
                Timber.e("Given FlexFEC packet is too small")
                return false
            }
            if (recoveredPacket.buffer.size < fecPacket.length) {
                Timber.e("Given RawPacket buffer is too small")
                return false
            }
            // Copy over the recovery RTP header data from the fec packet
            // (fecPacket contains the RTP header, so we need to copy from it
            // starting after that)
            System.arraycopy(fecPacket.buffer, fecPacket.flexFecHeaderOffset,
                    recoveredPacket.buffer, 0, RawPacket.FIXED_HEADER_SIZE)

            // Copy over the recovery rtp payload data from the fec packet
            System.arraycopy(
                    fecPacket.buffer,
                    fecPacket.flexFecHeaderOffset + fecPacket.flexFecHeaderSize,
                    recoveredPacket.buffer,
                    RawPacket.FIXED_HEADER_SIZE,
                    fecPacket.flexFecPayloadLength)
            return true
        }

        /**
         * Xor the RTP headers of source and destination
         *
         * @param source the packet to xor the header from
         * @param dest the packet to xor the header into
         */
        private fun xorHeaders(source: RawPacket?, dest: RawPacket) {
            // XOR the first 2 bytes of the header: V, P, X, CC, M, PT fields.
            dest.buffer[0] = (dest.buffer[0].toInt() xor source!!.buffer[source.offset + 0].toInt()).toByte()
            dest.buffer[1] = (dest.buffer[1].toInt() xor source.buffer[source.offset + 1].toInt()).toByte()

            // XOR the length recovery field.
            val length = (source.length and 0xffff) - RawPacket.FIXED_HEADER_SIZE
            dest.buffer[2] = (dest.buffer[2].toInt() xor (length shr 8)).toByte()
            dest.buffer[3] = (dest.buffer[3].toInt() xor (length and 0x00ff)).toByte()

            // XOR the 5th to 8th bytes of the header: the timestamp field.
            dest.buffer[4] = (dest.buffer[4].toInt() xor source.buffer[source.offset + 4].toInt()).toByte()
            dest.buffer[5] = (dest.buffer[5].toInt() xor source.buffer[source.offset + 5].toInt()).toByte()
            dest.buffer[6] = (dest.buffer[6].toInt() xor source.buffer[source.offset + 6].toInt()).toByte()
            dest.buffer[7] = (dest.buffer[7].toInt() xor source.buffer[source.offset + 7].toInt()).toByte()

            // Skip the 9th to 12th bytes of the header.
        }

        /**
         * Xor the payloads of the source and destination
         *
         * @param source the packet to xor the payload from
         * @param sourceOffset the offset in the source packet at which the
         * payload begins.  Note that this is not necessarily the location
         * at which the RTP payload starts...for the purpose of FlexFEC,
         * everything after the fixed RTP header is considered the 'payload'
         * @param dest the packet to xor the payload into
         * @param destOffset the offset in the dest packet at which the payload
         * begins
         * @param payloadLength the length of the source's payload
         */
        private fun xorPayloads(
                source: ByteArray, sourceOffset: Int,
                dest: ByteArray, destOffset: Int, payloadLength: Int,
        ) {
            for (i in 0 until payloadLength) {
                dest[destOffset + i] = (dest[destOffset + i].toInt() xor source[sourceOffset + i].toInt()).toByte()
            }
        }

        /**
         * Do the final work when recovering an RTP packet (set the RTP version,
         * the length, the sequence number, and the ssrc)
         *
         * @param fecPacket the fec packet
         * @param recoveredPacket the media packet which was recovered
         */
        private fun finishPacketRecovery(fecPacket: FlexFec03Packet, recoveredPacket: RawPacket): Boolean {
            // Set the RTP version to 2.
            recoveredPacket.buffer[0] = (recoveredPacket.buffer[0].toInt() or 0x80).toByte() // Set the 1st bit
            recoveredPacket.buffer[0] = (recoveredPacket.buffer[0].toInt() and 0xbf).toByte() // Clear the second bit

            // Recover the packet length, from temporary location.
            val length = RTPUtils.readUint16AsInt(recoveredPacket.buffer, 2)
            // The length field used in the xor does not include the header
            // length, but we want to include the fixed header length when
            // setting the length on the packet object
            val lengthWithFixedHeader = length + RawPacket.FIXED_HEADER_SIZE
            if (lengthWithFixedHeader > recoveredPacket.buffer.size) {
                Timber.e("Length field of recovered packet is larger than its buffer")
                return false
            }
            recoveredPacket.length = lengthWithFixedHeader
            recoveredPacket.sequenceNumber = missingSequenceNumber
            recoveredPacket.setSSRC(fecPacket.protectedSsrc.toInt())
            return true
        }

        /**
         * Recover a media packet based on the given FlexFEC packet and
         * received media packets
         *
         * @return the recovered packet if successful, null otherwise
         */
        fun recover(): RawPacket? {
            if (!canRecover()) {
                return null
            }

            //TODO: use a pool?
            val buf = ByteArray(1500)
            val recoveredPacket = RawPacket(buf, 0, 1500)
            if (!startPacketRecovery(fecPacket, recoveredPacket)) {
                return null
            }
            for (protectedSeqNum in fecPacket!!.protectedSequenceNumbers) {
                if (protectedSeqNum != missingSequenceNumber) {
                    val mediaPacket = mediaPackets[protectedSeqNum]
                    xorHeaders(mediaPacket, recoveredPacket)
                    xorPayloads(
                            mediaPacket!!.buffer,
                            mediaPacket.offset + RawPacket.FIXED_HEADER_SIZE,
                            recoveredPacket.buffer,
                            RawPacket.FIXED_HEADER_SIZE,
                            mediaPacket.length - RawPacket.FIXED_HEADER_SIZE)
                }
            }
            return if (!finishPacketRecovery(fecPacket!!, recoveredPacket)) {
                null
            } else recoveredPacket
        }
    }

}