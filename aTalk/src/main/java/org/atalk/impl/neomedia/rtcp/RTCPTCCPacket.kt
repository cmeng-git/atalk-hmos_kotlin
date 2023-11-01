/*
 * Copyright @ 2015 Atlassian Pty Ltd
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
package org.atalk.impl.neomedia.rtcp

import net.sf.fmj.media.rtp.RTCPCompoundPacket
import org.atalk.service.neomedia.ByteArrayBufferImpl
import org.atalk.util.ByteArrayBuffer
import org.atalk.util.RTCPUtils.getReportCount
import org.atalk.util.RTPUtils
import org.atalk.util.RTPUtils.readInt16AsInt
import org.atalk.util.RTPUtils.readUint16AsInt
import org.atalk.util.RTPUtils.readUint24AsInt
import org.atalk.util.RTPUtils.subtractNumber
import org.atalk.util.RTPUtils.writeShort
import org.atalk.util.RTPUtils.writeUint24
import org.atalk.util.logging.DiagnosticContext
import timber.log.Timber
import java.util.*
import kotlin.math.min

/**
 * A class which represents an RTCP packet carrying transport-wide congestion
 * control (transport-cc) feedback information. The format is defined here:
 * https://tools.ietf.org/html/draft-holmer-rmcat-transport-wide-cc-extensions-01
 *
 * <pre>`0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P|  FMT=15 |    PT=205     |           length              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                     SSRC of packet sender                     |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                      SSRC of media source                     |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |      base sequence number     |      packet status count      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                 reference time                | fb pkt. count |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |          packet chunk         |         packet chunk          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * .                                                               .
 * .                                                               .
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |         packet chunk          |  recv delta   |  recv delta   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * .                                                               .
 * .                                                               .
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           recv delta          |  recv delta   | zero padding  |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
`</pre> *
 *
 * @author Boris Grozev
 * @author George Politis
 * @author Eng Chong Meng
 */
class RTCPTCCPacket : RTCPFBPacket {
    /**
     * @return the map of packets represented by this [RTCPTCCPacket].
     *
     * Warning: the timestamps are represented in the 250µs format used by the
     * on-the-wire format, and don't represent local time. This is different
     * than the timestamps expected as input when constructing a packet with
     * RTCPTCCPacket.RTCPTCCPacket.
     */
    /**
     * The map which contains the sequence numbers (mapped to the reception
     * timestamp) of the packets described by this RTCP packet.
     */
    @get:Synchronized
    var packets: PacketMap? = null
        get() {
            if (field == null) {
                field = getPacketsFromFci(ByteArrayBufferImpl(fci, 0, fci!!.size))
            }
            return field
        }
        private set

    /**
     * Initializes a new `RTCPTCCPacket` instance.
     *
     * @param base
     */
    constructor(base: RTCPCompoundPacket?) : super(base)

    /**
     * Initializes a new [RTCPTCCPacket] instance with a specific "packet sender SSRC" and
     * "media source SSRC" values, and which describes a specific set of sequence numbers.
     *
     * @param senderSSRC the value to use for the "packet sender SSRC" field.
     * @param sourceSSRC the value to use for the "media source SSRC" field.
     * @param packets the set of RTP sequence numbers and their reception
     * timestamps which this packet is to describe. Note that missing sequence
     * numbers, as well as those mapped to a negative will be interpreted as
     * missing (not received) packets.
     * @param fbPacketCount the index of this feedback packet, to be used in the
     * "fb pkt count" field.
     * @param diagnosticContext the [DiagnosticContext] to use to print
     * diagnostic information.
     *
     * Warning: The timestamps for the packets are expected to be in
     * millisecond increments, which is different than the output map produced
     * after parsing a packet!
     * Note: this implementation is not optimized and might not always use
     * the minimal possible number of bytes to describe a given set of packets.
     */
    constructor(senderSSRC: Long, sourceSSRC: Long, packets: PacketMap, fbPacketCount: Byte, diagnosticContext: DiagnosticContext) : super(FMT, RTCPFBPacket.RTPFB, senderSSRC, sourceSSRC) {
        val first = packets.firstEntry()!!
        val firstSeq = first.key!!

        val (key) = packets.lastEntry()!!
        val packetCount = 1 + subtractNumber(key!!, firstSeq)
        require(packetCount <= MAX_PACKET_COUNT) { "Too many packets: $packetCount" }

        // Temporary buffer to store the fixed fields (8 bytes) and the list of
        // packet status chunks (see the format above). The buffer may be longer
        // than needed. We pack 7 packets in a chunk, and a chunk is 2 bytes.
        val buf = if (packetCount % 7 == 0) ByteArray(packetCount / 7 * 2 + 8) else ByteArray((packetCount / 7 + 1) * 2 + 8)
        // Temporary buffer to store the list of deltas (see the format above).
        // We allocated for the worst case (2 bytes per packet), which may
        // be longer than needed.
        val deltas = ByteArray(packetCount * 2)
        var deltaOff = 0
        var off = 0
        var referenceTime = first.value!!
        referenceTime -= referenceTime % 64

        // Set the 'base sequence number' field
        off += writeShort(buf, off, firstSeq.toShort())

        // Set the 'packet status count' field
        off += writeShort(buf, off, packetCount.toShort())

        // Set the 'reference time' field
        off += writeUint24(buf, off, ((referenceTime shr 6) and 0xffffffL).toInt())

        // Set the 'fb pkt count' field. TODO increment
        buf[off++] = fbPacketCount

        // Add the packet status chunks. In this first impl we'll just use
        // status vector chunks (T=1) with two-bit symbols (S=1) as this is
        // most straightforward to implement.
        // TODO: optimize for size
        var nextReferenceTime = referenceTime
        off-- // we'll take care of this inside the loop.
        for (seqDelta in 0 until packetCount) {
            // A status vector chunk with two-bit symbols contains 7 packet
            // symbols
            if (seqDelta % 7 == 0) {
                off++
                buf[off] = 0xc0.toByte() //T=1, S=1
            } else if (seqDelta % 7 == 3) {
                off++
                // Clear previous contents.
                buf[off] = 0
            }
            var symbol: Int
            val seq = firstSeq + seqDelta and 0xffff
            val ts = packets[seq]
            if (ts == null || ts < 0) {
                symbol = SYMBOL_NOT_RECEIVED
            } else {
                val tsDelta = ts - nextReferenceTime
                if (tsDelta in 0..63) {
                    symbol = SYMBOL_SMALL_DELTA

                    // The small delta is an 8-bit unsigned with a resolution of
                    // 250µs. Our deltas are all in milliseconds (hence << 2).
                    deltas[deltaOff++] = (tsDelta shl 2 and 0xffL).toByte()
                    Timber.d("%s", diagnosticContext
                            .makeTimeSeriesPoint("small_delta")
                            .addField("seq", seq)
                            .addField("arrival_time_ms", ts)
                            .addField("ref_time_ms", nextReferenceTime)
                            .addField("delta", tsDelta))
                } else if (tsDelta < 8191 && tsDelta > -8192) {
                    symbol = SYMBOL_LARGE_DELTA

                    // The large or negative delta is a 16-bit signed integer
                    // with a resolution of 250µs (hence << 2).
                    val d = (tsDelta shl 2).toShort()
                    deltas[deltaOff++] = (d.toInt() shr 8 and 0xff).toByte()
                    deltas[deltaOff++] = (d.toInt() and 0xff).toByte()
                    Timber.d("%s", diagnosticContext
                            .makeTimeSeriesPoint("large_delta")
                            .addField("seq", seq)
                            .addField("arrival_time_ms", ts)
                            .addField("ref_time_ms", nextReferenceTime)
                            .addField("delta", tsDelta))
                } else {
                    // The RTCP packet format does not support deltas bigger
                    // than what we handle above. As per the draft, if we want
                    // send feedback with such deltas, we should split it up
                    // into multiple RTCP packets. We can't do that here in the
                    // constructor.
                    throw IllegalArgumentException("Delta too big, needs new reference.")
                }

                // If the packet was received, the next delta will be relative
                // to its time. Otherwise, we'll just the previous reference.
                nextReferenceTime = ts
            }

            // Depending on the index of our packet, we have to offset its
            // symbol (we've already set 'off' to point to the correct byte).
            //  0 1 2 3 4 5 6 7          8 9 0 1 2 3 4 5
            //  S T <0> <1> <2>          <3> <4> <5> <6>
            val symbolShift = when (seqDelta % 7) {
                0, 4 -> 4
                1, 5 -> 2
                2, 6 -> 0
                3 -> 6
                else -> 6
            }
            symbol = symbol shl symbolShift
            buf[off] = (buf[off].toInt() or symbol).toByte()
        }
        off++
        if (packetCount % 7 in 1..3) {
            // the last chunk was not complete
            buf[off++] = 0
        }
        fci = ByteArray(off + deltaOff)
        System.arraycopy(buf, 0, fci, 0, off)
        System.arraycopy(deltas, 0, fci, off, deltaOff)
    }

    /**
     * @return the value of the "fb packet count" field of this packet, or -1.
     */
    val fbPacketCount: Int
        get() = if (fci == null || fci.size < MIN_FCI_LENGTH) -1 else fci[7].toInt() and 0xff

    override fun toString(): String {
        return "RTCP transport-cc feedback"
    }

    /**
     * An ordered collection which maps sequence numbers to timestamps, the
     * order is by the sequence number.
     */
    class PacketMap : TreeMap<Int, Long>(RTPUtils.sequenceNumberComparator)
    companion object {
        /**
         * The maximum number of packets (including missing packets) to include
         * in an [RTCPTCCPacket] being constructed for a list of packets.
         */
        const val MAX_PACKET_COUNT = 200

        /**
         * Gets a boolean indicating whether or not the RTCP packet specified in the
         * [ByteArrayBuffer] that is passed as an argument is a TCC packet or not.
         *
         * @param baf the [ByteArrayBuffer]
         * @return true if the byte array buffer holds a TCC packet, otherwise false.
         */
        fun isTCCPacket(baf: ByteArrayBuffer?): Boolean {
            val rc = getReportCount(baf)
            return rc == FMT && isRTPFBPacket(baf)
        }

        /**
         * @param baf the buffer which contains the RTCP packet.
         * @return the packets represented in an RTCP transport-cc feedback packet.
         *
         * Warning: the timestamps are represented in the 250µs format used by the
         * on-the-wire format, and don't represent local time. This is different
         * than the timestamps expected as input when constructing a packet with
         * RTCPTCCPacket.RTCPTCCPacket.
         */
        fun getPackets(baf: ByteArrayBuffer): PacketMap? {
            return getPacketsFromFci(getFCI(baf))
        }

        /**
         * @return the reference time of the FCI buffer of an RTCP TCC packet.
         *
         * The format is 32 bits with 250µs resolution. Note that the format in the
         * transport-wide cc draft is 24bit with 2^6ms resolution. The change in the
         * unit facilitates the arrival time computations, as the deltas have 250µs resolution.
         */
        fun getReferenceTime250us(fciBuffer: ByteArrayBuffer): Long {
            val buf = fciBuffer.buffer
            val off = fciBuffer.offset

            // reference time. The 24 bit field uses increments of 2^6ms, and we
            // shift by 8 to change the resolution to 250µs.
            // FIXME this is supposed to be a signed int.
            return (readUint24AsInt(buf, off + 4) shl 8).toLong()
        }

        /**
         * Warning: the timestamps are represented in the 250µs format used by the
         * on-the-wire format, and don't represent local time. This is different
         * than the timestamps expected as input when constructing a packet with
         * RTCPTCCPacket.RTCPTCCPacket.
         *
         * Note that packets described as lost are NOT included in the results.
         *
         * @param fciBuffer the buffer which contains the FCI portion of the RTCP feedback packet.
         * @return the packets represented in the FCI portion of an RTCP transport-cc feedback packet.
         */
        fun getPacketsFromFci(fciBuffer: ByteArrayBuffer?): PacketMap? {
            return getPacketsFromFci(fciBuffer, false)
        }

        /**
         * @param fciBuffer the buffer which contains the FCI portion of the RTCP
         * feedback packet.
         * @param includeNotReceived whether the returned map should include the
         * packets described in the feedback packet as lost. Note that the RLE
         * encoding allows ~2^16 packets to be described as lost in just a few
         * bytes, so when parsing packets coming over the network it is wise to
         * not blindly set this option to `true`.
         * @return the packets represented in the FCI portion of an RTCP
         * transport-cc feedback packet.
         *
         * Warning: the timestamps are represented in the 250µs format used by the
         * on-the-wire format, and don't represent local time. This is different
         * than the timestamps expected as input when constructing a packet with
         * RTCPTCCPacket.RTCPTCCPacket.
         */
        private fun getPacketsFromFci(fciBuffer: ByteArrayBuffer?, includeNotReceived: Boolean): PacketMap? {
            var fciLen = -1
            if (fciBuffer == null || fciBuffer.length.also { fciLen = it } < MIN_FCI_LENGTH) {
                Timber.w("%s buffer is null or length too small: %s", PARSE_ERROR, fciLen)
                return null
            }
            val fciBuf = fciBuffer.buffer
            val fciOff = fciBuffer.offset

            // The fixed fields. The current sequence number starts from the one
            // in the 'base sequence number' field and increments as we parse.
            var currentSeq = readUint16AsInt(fciBuf, fciOff)
            val packetStatusCount = readUint16AsInt(fciBuf, fciOff + 2)
            var referenceTime = getReferenceTime250us(fciBuffer)

            // The offset at which the packet status chunk list starts.
            var currentPscOff = fciOff + PACKET_STATUS_CHUNK_OFFSET

            // First find where the delta list begins.
            var packetsRemaining = packetStatusCount
            while (packetsRemaining > 0) {
                if (currentPscOff + CHUNK_SIZE_BYTES > fciOff + fciLen) {
                    Timber.w("% sreached the end while reading chunks", PARSE_ERROR)
                    return null
                }
                val packetsInChunk = getPacketCount(fciBuf, currentPscOff)
                packetsRemaining -= packetsInChunk
                currentPscOff += CHUNK_SIZE_BYTES
            }

            // At this point we have the the beginning of the delta list. Start
            // reading from the chunk and delta lists together.
            val deltaOff = currentPscOff
            var currentDeltaOff = currentPscOff

            // Reset to the start of the chunks list.
            currentPscOff = fciOff + PACKET_STATUS_CHUNK_OFFSET
            packetsRemaining = packetStatusCount
            val packets = PacketMap()
            while (packetsRemaining > 0 && currentPscOff < deltaOff) {
                // packetsRemaining is based on the "packet status count" field,
                // which helps us find the correct number of packets described in
                // the last chunk. E.g. if the last chunk is a vector chunk, we
                // don't really know by the chunk alone how many packets are described.
                val packetsInChunk = min(getPacketCount(fciBuf, currentPscOff), packetsRemaining)
                val chunkType = getChunkType(fciBuf, currentPscOff)
                if (packetsInChunk > 0 && chunkType == CHUNK_TYPE_RLE && readSymbol(fciBuf, currentPscOff, chunkType, 0) == SYMBOL_NOT_RECEIVED) {
                    // This is an RLE chunk with NOT_RECEIVED symbols. So we can
                    // avoid reading every symbol individually in a loop.
                    if (includeNotReceived) {
                        for (i in 0 until packetsInChunk) {
                            val seq = (currentSeq + i) % 0xffff
                            packets[seq] = NEGATIVE_ONE
                        }
                    }
                    currentSeq = (currentSeq + packetsInChunk) % 0xffff
                } else {
                    // Read deltas for all packets in the chunk.
                    for (i in 0 until packetsInChunk) {
                        val symbol = readSymbol(fciBuf, currentPscOff, chunkType, i)
                        // -1 or delta in 250µs increments
                        var delta: Int
                        when (symbol) {
                            SYMBOL_SMALL_DELTA -> {
                                // The delta is an 8-bit unsigned integer.
                                if (currentDeltaOff >= fciOff + fciLen) {
                                    Timber.w("%s reached the end while reading delta.", PARSE_ERROR)
                                    return null
                                }
                                delta = fciBuf[currentDeltaOff++].toInt() and 0xff
                            }
                            SYMBOL_LARGE_DELTA -> {
                                // The delta is a 16-bit signed integer. we're about to read 2 bytes
                                if (currentDeltaOff + 1 >= fciOff + fciLen) {
                                    Timber.w("%s reached the end while reading long delta.", PARSE_ERROR)
                                    return null
                                }
                                delta = readInt16AsInt(fciBuf, currentDeltaOff)
                                currentDeltaOff += 2
                            }
                            SYMBOL_NOT_RECEIVED -> delta = -1
                            else -> {
                                Timber.w("%s invalid symbol: %s", PARSE_ERROR, symbol)
                                return null
                            }
                        }
                        if (delta == -1) {
                            // Packet not received. We don't update the reference time,
                            // but we push the packet in the map to indicate that it was
                            // marked as not received.
                            if (includeNotReceived) {
                                packets[currentSeq] = NEGATIVE_ONE
                            }
                        } else {
                            // The draft is not clear about what the reference time
                            // for each packet is. We adhere to the webrtc.org
                            // behavior so that every packet for which there is a
                            // delta updates the reference (even if the delta is negative).
                            referenceTime += delta.toLong()
                            packets[currentSeq] = referenceTime
                        }
                        currentSeq = currentSeq + 1 and 0xffff
                    }
                }

                // next packet status chunk
                currentPscOff += CHUNK_SIZE_BYTES
                packetsRemaining -= packetsInChunk
            }
            if (packetsRemaining > 0) {
                Timber.w("Reached the end of the buffer before having read all expected packets. Ill-formatted RTCP packet?")
            }
            return packets
        }

        /**
         * @param buf the buffer which contains the Packet Status Chunk.
         * @param off the offset in `buf` at which the Packet Status Chunk
         * @return the type of a Packet Status Chunk contained in `buf` at
         * offset `off`.
         */
        private fun getChunkType(buf: ByteArray?, off: Int): Int {
            return buf!![off].toInt() and 0x80 shr 7
        }

        /**
         * Reads the `i`-th (zero-based) symbol from the Packet Status Chunk
         * contained in `buf` at offset `off`. Returns -1 if the index
         * is found to be invalid (although the validity check is not performed
         * for RLE chunks).
         *
         * @param buf the buffer which contains the Packet Status Chunk.
         * @param off the offset in `buf` at which the Packet Status Chunk
         * begins.
         * @param i the zero-based index of the symbol to return.
         * @return the `i`-th symbol from the given Packet Status Chunk.
         */
        private fun readSymbol(buf: ByteArray?, off: Int, chunkType: Int, i: Int): Int {
            if (chunkType == CHUNK_TYPE_VECTOR) {
                return when (buf!![off].toInt() and 0x40 shr 6) {
                    SYMBOL_TYPE_LONG -> {
                        //  0                   1
                        //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
                        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                        // |T|S| s0| s1| s2| s3| s4| s5| s6|
                        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                        if (i in 0..2) {
                            return buf[off].toInt() shr 4 - 2 * i and 0x03
                        } else if (i in 3..6) {
                            return buf[off + 1].toInt() shr 6 - 2 * (i - 3) and 0x03
                        }
                        -1
                    }
                    SYMBOL_TYPE_SHORT -> {
                        // The format is similar to above, except with 14 one-bit symbols.
                        if (i in 0..5) {
                            return buf[off].toInt() shr 5 - i and 0x01
                        } else if (i in 6..13) {
                            return buf[off + 1].toInt() shr 13 - i and 0x01
                        }
                        -1
                    }
                    else -> -1
                }
            } else if (chunkType == CHUNK_TYPE_RLE) {

                // A RLE chunk looks like this:
                //  0                   1
                //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
                // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                // |T| S |       Run Length        |
                // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

                // We assume the caller knows what they are doing and they have
                // given us a valid i, so we just return the symbol (S). Otherwise
                // we'd have to read the Run Length field every time.
                return buf!![off].toInt() shr 5 and 0x03
            }
            return -1
        }

        /**
         * Returns the number of packets described in the Packet Status Chunk
         * contained in the buffer `buf` at offset `off`.
         * Note that this may not necessarily match with the number of packets
         * that we want to read from the chunk. E.g. if a feedback packet describes
         * 3 packets (indicated by the value "3" in the "packet status count" field),
         * and it contains a Vector Status Chunk which can describe 7 packets (long
         * symbols), then we want to read only 3 packets (but this method will
         * return 7).
         *
         * @param buf the buffer which contains the Packet Status Chunk
         * @param off the offset at which the Packet Status Chunk starts.
         * @return the number of packets described by the Packet Status Chunk.
         */
        private fun getPacketCount(buf: ByteArray?, off: Int): Int {
            val chunkType = getChunkType(buf, off)
            if (chunkType == CHUNK_TYPE_VECTOR) {
                // A vector chunk looks like this:
                //  0                   1
                //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
                // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                // |1|S|       symbol list         |
                // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                // The 14-bit long symbol list consists of either 14 single-bit
                // symbols, or 7 two-bit symbols, according to the S bit.
                val symbolType = buf!![off].toInt() and 0x40 shr 6
                return if (symbolType == SYMBOL_TYPE_SHORT) 14 else 7
            } else if (chunkType == CHUNK_TYPE_RLE) {
                // A RLE chunk looks like this:
                //  0                   1
                //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
                // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                // |T| S |       Run Length        |
                // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                return buf!![off].toInt() and 0x1f shl 8 or (buf[off + 1].toInt() and 0xff)
            }
            throw IllegalStateException(
                    "The one-bit chunk type is neither 0 nor 1. A superposition is not a valid chunk type.")
        }

        /**
         * The value of the "fmt" field for a transport-cc RTCP feedback packet.
         */
        const val FMT = 15

        /**
         * The symbol which indicates that a packet was not received.
         */
        private const val SYMBOL_NOT_RECEIVED = 0

        /**
         * The symbol which indicates that a packet was received with a small delta
         * (represented in a 1-byte field).
         */
        private const val SYMBOL_SMALL_DELTA = 1

        /**
         * The symbol which indicates that a packet was received with a large or
         * negative delta (represented in a 2-byte field).
         */
        private const val SYMBOL_LARGE_DELTA = 2

        /**
         * The value of the `T` bit of a Packet Status Chunk, which
         * identifies it as a Vector chunk.
         */
        private const val CHUNK_TYPE_VECTOR = 1

        /**
         * The value of the `T` bit of a Packet Status Chunk, which
         * identifies it as a Run Length Encoding chunk.
         */
        private const val CHUNK_TYPE_RLE = 0

        /**
         * The value of the `S` bit og a Status Vector Chunk, which
         * indicates 1-bit (short) symbols.
         */
        private const val SYMBOL_TYPE_SHORT = 0

        /**
         * The value of the `S` bit of a Status Vector Chunk, which
         * indicates 2-bit (long) symbols.
         */
        private const val SYMBOL_TYPE_LONG = 1

        /**
         * A static object defined here in the hope that it will reduce boxing.
         */
        private const val NEGATIVE_ONE = -1L

        /**
         * The minimum length of the FCI field of a valid transport-cc RTCP feedback message. 8 bytes
         * for the fixed fields + 2 bytes for one packet status chunk.
         */
        private const val MIN_FCI_LENGTH = 10

        /**
         * The size in bytes of a packet status chunk.
         */
        private const val CHUNK_SIZE_BYTES = 2

        /**
         * The offset of the first packet status chunk relative to the start of the
         * FCI.
         */
        private const val PACKET_STATUS_CHUNK_OFFSET = 8

        /**
         * An error message to use when parsing failed.
         */
        private const val PARSE_ERROR = "Failed to parse an RTCP transport-cc feedback packet: "
    }
}