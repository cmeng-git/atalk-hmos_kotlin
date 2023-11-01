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
import timber.log.Timber

/**
 * This class handles the reception of incoming ULPFEC (RFC 5109) packets
 *
 * @author bgrozev
 * @author bbaldino
 * @author Eng Chong Meng
 */
class ULPFECReceiver(ssrc: Long, payloadType: Byte) : AbstractFECReceiver(ssrc, payloadType) {
    /**
     * A `Set` of packets which will be reused every time a
     * packet is recovered. Defined here to avoid recreating it on every call
     * to `reverseTransform`.
     */
    private val packetsToRemove: MutableSet<RawPacket> = HashSet()
    private val reconstructor: Reconstructor

    init {
        reconstructor = Reconstructor(mediaPackets, ssrc)
    }

    override fun doReverseTransform(pkts: Array<RawPacket?>): Array<RawPacket?> {
        // now that we've read the input packets, see if there's a packet
        // we could recover
        var packets = pkts
        if (handleFec) {
            // go over our saved fec packets and see if any of them can be
            // used to recover a media packet. Add packets which aren't
            // needed anymore to packetsToRemove
            packetsToRemove.clear()
            for ((_, fecPacket) in fecPackets) {
                reconstructor.setFecPacket(fecPacket)
                if (reconstructor.numMissing == 0) {
                    // We already have all media packets for this fec packet,
                    // no need to keep it and keep checking.
                    packetsToRemove.add(fecPacket)
                    continue
                }
                if (reconstructor.canRecover()) {
                    packetsToRemove.add(fecPacket)
                    val recovered = reconstructor.recover()

                    // save it
                    if (recovered != null) {
                        statistics.numRecoveredPackets++
                        saveMedia(recovered)

                        // search for an empty spot in pkts where to place
                        // recovered
                        var found = false
                        for (i in packets.indices) {
                            if (packets[i] == null) {
                                packets[i] = recovered
                                found = true
                                break
                            }
                        }
                        if (!found) {
                            val pkts2 = arrayOfNulls<RawPacket>(packets.size + 1)
                            System.arraycopy(packets, 0, pkts2, 0, packets.size)
                            pkts2[packets.size] = recovered
                            packets = pkts2
                        }
                    }
                }
            }
            for (p in packetsToRemove) fecPackets.remove(p.sequenceNumber)
        }
        return packets
    }

    /**
     * A class that allows the recovery of a `RawPacket` given a set
     * of media packets and an ulpfec packet.
     *
     * Usage:
     * 1. Create an instance specifying the map off all available media packets
     * 2. Call setFecPacket() with an ulpfec packet
     * 3. Check if a recovery is possible using canRecover()
     * 4. Recover a packet with recover()
     */
    private class Reconstructor
    /**
     * Initializes a new instance.
     *
     * @param mediaPackets
     * all available media packets.
     * @param ssrc
     * the ssrc to use
     */
    (
            /**
             * All available media packets.
             */
            private val mediaPackets: Map<Int, RawPacket>,
            /**
             * SSRC to set on reconstructed packets.
             */
            private val ssrc: Long) {
        /**
         * Subset of the media packets which is needed for recovery, given a
         * specific value of `fecPacket`.
         */
        private val neededPackets: MutableSet<RawPacket> = HashSet()

        /**
         * The ulpfec packet to be used for recovery.
         */
        private var fecPacket: RawPacket? = null

        /**
         * Number of media packet which are needed for recovery (given a
         * specific value of `fecPacket`) which are not available.
         * If the value is `0`, this indicates that all media packets
         * referenced in `fecPacket` *are* available, and so no recovery
         * is needed.
         */
        var numMissing = -1

        /**
         * Sequence number of the packet to be reconstructed.
         */
        private var sequenceNumber = -1

        /**
         * Returns `true` if the `RawPacket` last set using
         * `setFecPacket` can be used to recover a media packet,
         * `false`otherwise.
         *
         * @return `true` if the `RawPacket` last set using
         * `setFecPacket` can be used to recover a media packet,
         * `false`otherwise.
         */
        fun canRecover(): Boolean {
            return numMissing == 1
        }

        /**
         * Sets the ulpfec packet to be used for recovery and also
         * updates the values of `numMissing`, `sequenceNumber`
         * and `neededPackets`.
         *
         * @param p
         * the ulpfec packet to set.
         */
        fun setFecPacket(p: RawPacket) {
            // reset all fields specific to fecPacket
            neededPackets.clear()
            numMissing = 0
            sequenceNumber = -1
            fecPacket = p
            var pkt: RawPacket?
            val buf = fecPacket!!.buffer
            var idx = fecPacket!!.offset + fecPacket!!.headerLength

            // mask length in bytes
            val maskLen = if (buf[idx].toInt() and 0x40 == 0) 2 else 6
            val base = fecPacket!!.readUint16AsInt(fecPacket!!.headerLength + 2)
            idx += 12 // skip FEC Header and Protection Length, point to mask
            outer@ for (i in 0 until maskLen) {
                for (j in 0..7) {
                    if (buf[idx + i].toInt() and (1 shl 7 - j and 0xff) != 0) {
                        //j-th bit in i-th byte in the mask is set
                        pkt = mediaPackets[base + i * 8 + j]
                        if (pkt != null) {
                            neededPackets.add(pkt)
                        } else {
                            sequenceNumber = base + i * 8 + j
                            numMissing++
                        }
                    }
                    if (numMissing > 1) break@outer
                }
            }
            if (numMissing != 1) sequenceNumber = -1
        }

        /**
         * Recovers a media packet using the ulpfec packet `fecPacket`
         * and the packets in `neededPackets`.
         *
         * @return the recovered packet.
         */
        fun recover(): RawPacket? {
            if (!canRecover()) return null
            val fecBuf = fecPacket!!.buffer
            var idx = fecPacket!!.offset + fecPacket!!.headerLength
            var lengthRecovery = fecBuf[idx + 8].toInt() and 0xff shl 8 or
                    (fecBuf[idx + 9].toInt() and 0xff)
            for (p in neededPackets) lengthRecovery = lengthRecovery xor p.length - 12
            lengthRecovery = lengthRecovery and 0xffff
            val recoveredBuf = ByteArray(lengthRecovery + 12) //include RTP header

            // restore the first 8 bytes of the header
            System.arraycopy(fecBuf, idx, recoveredBuf, 0, 8)
            for (p in neededPackets) {
                val pOffset = p.offset
                val pBuf = p.buffer
                for (i in 0..7) recoveredBuf[i] = (recoveredBuf[i].toInt() xor pBuf[pOffset + i].toInt()).toByte()
            }

            // set the version to 2
            recoveredBuf[0] = (recoveredBuf[0].toInt() and 0x3f).toByte()
            recoveredBuf[0] = (recoveredBuf[0].toInt() or 0x80).toByte()
            // the RTP header is now set, except for SSRC and seq. which are not
            // recoverable in this way and will be set later

            // check how many bytes of the payload are in the FEC packet
            val longMask = fecBuf[idx].toInt() and 0x40 != 0
            idx += 10 // skip FEC Header, point to FEC Level 0 Header
            val protectionLength = fecBuf[idx].toInt() and 0xff shl 8 or
                    (fecBuf[idx + 1].toInt() and 0xff)
            if (protectionLength < lengthRecovery) {
                // The FEC Level 0 payload only covers part of the media
                // packet, which isn't useful for us.
                Timber.w("Recovered only a partial RTP packet. Discarding.")
                return null
            }
            idx += 4 //skip the FEC Level 0 Header
            if (longMask) idx += 4 //long mask

            // copy the payload protection bits from the FEC packet
            System.arraycopy(fecBuf, idx, recoveredBuf, 12, lengthRecovery)

            // restore payload from media packets
            for (p in neededPackets) {
                val pBuf = p.buffer
                val pLen = p.length
                val pOff = p.offset
                var i = 12
                while (i < lengthRecovery + 12 && i < pLen) {
                    recoveredBuf[i] = (recoveredBuf[i].toInt() xor pBuf[pOff + i].toInt()).toByte()
                    i++
                }
            }
            val recovered = RawPacket(recoveredBuf, 0, lengthRecovery + 12)
            recovered.setSSRC(ssrc.toInt())
            recovered.sequenceNumber = sequenceNumber
            return recovered
        }
    }
}