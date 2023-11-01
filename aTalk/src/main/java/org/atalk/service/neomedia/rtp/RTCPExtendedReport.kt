/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.rtp

import net.sf.fmj.media.rtp.RTCPPacket
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.*

/**
 * Represents an RTP Control Protocol Extended Report (RTCP XR) packet in the terms of FMJ i.e. as
 * an `RTCPPacket` sub-class.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class RTCPExtendedReport() : RTCPPacket() {
    /**
     * Represents an abstract, base extended report block.
     *
     * @author Lyubomir Marinov
     */
    abstract class ReportBlock
    /**
     * Initializes a new `ReportBlock` instance of a specific block type.
     *
     * @param blockType
     * the block type/format of the new instance
     */
    protected constructor(
            /**
             * The block type/format of this report block.
             */
            private val blockType: Short,
    ) {

        /**
         * Serializes/writes the binary representation of this `ReportBlock` into a specific
         * `DataOutputStream`.
         *
         * dataoutputstream the `DataOutputStream` into which the binary representation of this
         * `ReportBlock` is to be serialized/written.
         * @throws IOException
         * if an input/output error occurs during the serialization/writing of the binary
         * representation of this `ReportBlock`
         */
        @Throws(IOException::class)
        abstract fun assemble(dataoutputstream: DataOutputStream)

        /**
         * Computes the length in `byte`s of this `ReportBlock`, including the header and any padding.
         *
         *
         * The implementation of `ReportBlock` returns the length in `byte`s of the
         * header of an extended report block i.e. `4`. The implementation is provided as a
         * convenience because RFC 3611 defines that the type-specific block contents of an extended
         * report block may be zero bits long if the block type definition permits.
         *
         *
         * @return the length in `byte`s of this `ReportBlock`, including the header and any padding.
         */
        open fun calcLength(): Int {
            return (1 /* block type (BT) */
                    + 1 /* type-specific */
                    + 2) /* block length */
        }
    }

    /**
     * Implements &quot;VoIP Metrics Report Block&quot; i.e. an extended report block which provides
     * metrics for monitoring voice over IP (VoIP) calls.
     *
     * @author Lyubomir Marinov
     */
    /**
     * Initializes a new `VoIPMetricsReportBlock` instance.
     */
    class VoIPMetricsReportBlock () : ReportBlock(VOIP_METRICS_REPORT_BLOCK_TYPE) {
        /**
         * Gets the fraction of RTP data packets within burst periods since the beginning of
         * reception that were either lost or discarded.
         *
         * @return the fraction of RTP data packets within burst periods since the beginning of
         * reception that were either lost or discarded
         */
        /**
         * Sets the fraction of RTP data packets within burst periods since the beginning of
         * reception that were either lost or discarded.
         *
         * @param burstDensity the fraction of RTP data packets within burst periods since the beginning of
         * reception that were either lost or discarded
         */
        /**
         * The fraction of RTP data packets within burst periods since the beginning of reception
         * that were either lost or discarded. The value is expressed as a fixed point number with
         * the binary point at the left edge of the field. It is calculated by dividing the total
         * number of packets lost or discarded (excluding duplicate packet discards) within burst
         * periods by the total number of packets expected within the burst periods, multiplying the
         * result of the division by 256, limiting the maximum value to 255 (to avoid overflow), and
         * taking the integer part. The field MUST be populated and MUST be set to zero if no
         * packets have been received.
         */
        var burstDensity: Short = 0
        var burstDuration = 0
        /**
         * Gets the fraction of RTP data packets from the source that have been discarded since the
         * beginning of reception, due to late or early arrival, under-run or overflow at the
         * receiving jitter buffer.
         *
         * @return the fraction of RTP data packets from the source that have been discarded since
         * the beginning of reception, due to late or early arrival, under-run or overflow
         * at the receiving jitter buffer
         * @see .discardRate
         */
        /**
         * Sets the fraction of RTP data packets from the source that have been discarded since the
         * beginning of reception, due to late or early arrival, under-run or overflow at the
         * receiving jitter buffer.
         *
         * @param discardRate
         * the fraction of RTP data packets from the source that have been discarded since
         * the beginning of reception, due to late or early arrival, under-run or overflow at
         * the receiving jitter buffer
         * @see .discardRate
         */
        /**
         * The fraction of RTP data packets from the source that have been discarded since the
         * beginning of reception, due to late or early arrival, under-run or overflow at the
         * receiving jitter buffer. The value is expressed as a fixed point number with the binary
         * point at the left edge of the field. It is calculated by dividing the total number of
         * packets discarded (excluding duplicate packet discards) by the total number of packets
         * expected, multiplying the result of the division by 256, limiting the maximum value to
         * 255 (to avoid overflow), and taking the integer part.
         */
        var discardRate: Short = 0
        var endSystemDelay = 0
        var extRFactor: Byte = 127
        /**
         * Get the fraction of RTP data packets within inter-burst gaps since the beginning of
         * reception that were either lost or discarded.
         *
         * @return the fraction of RTP data packets within inter-burst gaps since the beginning of
         * reception that were either lost or discarded
         */
        /**
         * Sets the fraction of RTP data packets within inter-burst gaps since the beginning of
         * reception that were either lost or discarded.
         *
         * @param gapDensity
         * the fraction of RTP data packets within inter-burst gaps since the beginning of
         * reception that were either lost or discarded
         */
        /**
         * The fraction of RTP data packets within inter-burst gaps since the beginning of reception
         * that were either lost or discarded. The value is expressed as a fixed point number with
         * the binary point at the left edge of the field. It is calculated by dividing the total
         * number of packets lost or discarded (excluding duplicate packet discards) within gap
         * periods by the total number of packets expected within the gap periods, multiplying the
         * result of the division by 256, limiting the maximum value to 255 (to avoid overflow), and
         * taking the integer part. The field MUST be populated and MUST be set to zero if no
         * packets have been received.
         */
        var gapDensity: Short = 0
        var gapDuration = 0
        var gMin: Short = 16
        var jitterBufferAbsoluteMaximumDelay = 0
        /**
         * Gets whether the jitter buffer is adaptive.
         *
         * @return [.ADAPTIVE_JITTER_BUFFER_ADAPTIVE],
         * [.NON_ADAPTIVE_JITTER_BUFFER_ADAPTIVE],
         * [.RESERVED_JITTER_BUFFER_ADAPTIVE], or
         * [.UNKNOWN_JITTER_BUFFER_ADAPTIVE]
         */
        /**
         * Sets whether the jitter buffer is adaptive.
         *
         * @param jitterBufferAdaptive
         * [.ADAPTIVE_JITTER_BUFFER_ADAPTIVE],
         * [.NON_ADAPTIVE_JITTER_BUFFER_ADAPTIVE],
         * [.RESERVED_JITTER_BUFFER_ADAPTIVE], or
         * [.UNKNOWN_JITTER_BUFFER_ADAPTIVE]
         * @throws IllegalArgumentException
         * if the specified `jitterBufferAdapter` is not one of the constants
         * `ADAPTIVE_JITTER_BUFFER_ADAPTIVE`,
         * `NON_ADAPTIVE_JITTER_BUFFER_ADAPTIVE`,
         * `RESERVED_JITTER_BUFFER_ADAPTIVE`, and
         * `UNKNOWN_JITTER_BUFFER_ADAPTIVE`
         */
        /**
         * Whether the jitter buffer is adaptive. The value is one of the constants
         * [.ADAPTIVE_JITTER_BUFFER_ADAPTIVE], [.NON_ADAPTIVE_JITTER_BUFFER_ADAPTIVE],
         * [.RESERVED_JITTER_BUFFER_ADAPTIVE], and [.UNKNOWN_JITTER_BUFFER_ADAPTIVE].
         */
        var jitterBufferAdaptive = UNKNOWN_JITTER_BUFFER_ADAPTIVE
            set(jitterBufferAdaptive) {
                field = when (jitterBufferAdaptive) {
                    ADAPTIVE_JITTER_BUFFER_ADAPTIVE, NON_ADAPTIVE_JITTER_BUFFER_ADAPTIVE, RESERVED_JITTER_BUFFER_ADAPTIVE, UNKNOWN_JITTER_BUFFER_ADAPTIVE -> jitterBufferAdaptive
                    else -> throw IllegalArgumentException("jitterBufferAdaptive")
                }
            }
        var jitterBufferMaximumDelay = 0
        var jitterBufferNominalDelay = 0
        /**
         * Gets the implementation specific adjustment rate of a jitter buffer in adaptive mode.
         *
         * @return the implementation specific adjustment rate of a jitter buffer in adaptive mode
         */
        /**
         * Sets the implementation specific adjustment rate of a jitter buffer in adaptive mode.
         *
         * @param jitterBufferRate
         * the implementation specific adjustment rate of a jitter buffer in adaptive mode
         */
        /**
         * The implementation specific adjustment rate of a jitter buffer in adaptive mode. Defined
         * in terms of the approximate time taken to fully adjust to a step change in peak to peak
         * jitter from 30 ms to 100 ms such that: `adjustment time = 2 * J * frame size (ms)`
         * where `J = adjustment rate (0-15)`. The parameter is intended
         * only to provide a guide to the degree of "aggressiveness"
         * of an adaptive jitter buffer and may be estimated. A value of
         * `0` indicates that the adjustment time is unknown for this implementation.
        ```` */
        var jitterBufferRate: Byte = 0
        /**
         * Gets the fraction of RTP data packets from the source lost since the beginning of reception.
         *
         * @return the fraction of RTP data packets from the source lost since the beginning of reception
         * @see .lossRate
         */
        /**
         * Sets the fraction of RTP data packets from the source lost since the beginning of reception.
         *
         * @param lossRate
         * the fraction of RTP data packets from the source lost since the beginning of reception
         * @see .lossRate
         */
        /**
         * The fraction of RTP data packets from the source lost since the beginning of reception,
         * expressed as a fixed point number with the binary point at the left edge of the field.
         * This value is calculated by dividing the total number of packets lost (after the effects
         * of applying any error protection such as FEC) by the total number of packets expected,
         * multiplying the result of the division by 256, limiting the maximum value to 255 (to
         * avoid overflow), and taking the integer part. The numbers of duplicated packets and
         * discarded packets do not enter into this calculation. Since receivers cannot be required
         * to maintain unlimited buffers, a receiver MAY categorize late-arriving packets as lost.
         * The degree of lateness that triggers a loss SHOULD be significantly greater than that
         * which triggers a discard.
         */
        var lossRate: Short = 0
        var mosCq: Byte = 127
        var mosLq: Byte = 127
        var noiseLevel: Byte = 127
        /**
         * Gets the type of packet loss concealment (PLC).
         *
         * @return [.STANDARD_PACKET_LOSS_CONCEALMENT],
         * [.ENHANCED_PACKET_LOSS_CONCEALMENT],
         * [.DISABLED_PACKET_LOSS_CONCEALMENT], or
         * [.UNSPECIFIED_PACKET_LOSS_CONCEALMENT]
         */
        /**
         * Sets the type of packet loss concealment (PLC).
         *
         * @param packetLossConcealment
         * [.STANDARD_PACKET_LOSS_CONCEALMENT],
         * [.ENHANCED_PACKET_LOSS_CONCEALMENT],
         * [.DISABLED_PACKET_LOSS_CONCEALMENT], or
         * [.UNSPECIFIED_PACKET_LOSS_CONCEALMENT]
         * @throws IllegalArgumentException
         * if the specified `packetLossConcealment` is not one of the constants
         * `STANDARD_PACKET_LOSS_CONCEALMENT`,
         * `ENHANCED_PACKET_LOSS_CONCEALMENT`,
         * `DISABLED_PACKET_LOSS_CONCEALMENT`, and
         * `UNSPECIFIED_PACKET_LOSS_CONCEALMENT`
         */
        /**
         * The type of packet loss concealment (PLC). The value is one of the constants
         * [.STANDARD_PACKET_LOSS_CONCEALMENT], [.ENHANCED_PACKET_LOSS_CONCEALMENT],
         * [.DISABLED_PACKET_LOSS_CONCEALMENT], and
         * [.UNSPECIFIED_PACKET_LOSS_CONCEALMENT].
         */
        var packetLossConcealment = UNSPECIFIED_PACKET_LOSS_CONCEALMENT
            set(packetLossConcealment) {
                field = when (packetLossConcealment) {
                    STANDARD_PACKET_LOSS_CONCEALMENT, ENHANCED_PACKET_LOSS_CONCEALMENT, DISABLED_PACKET_LOSS_CONCEALMENT, UNSPECIFIED_PACKET_LOSS_CONCEALMENT -> packetLossConcealment
                    else -> throw IllegalArgumentException("packetLossConcealment")
                }
            }
        var residualEchoReturnLoss: Byte = 127
        var rFactor: Byte = 127
        var roundTripDelay = 0
        var signalLevel: Byte = 127
        /**
         * Gets the synchronization source identifier (SSRC) of the RTP data packet source being
         * reported upon by this report block.
         *
         * @return the synchronization source identifier (SSRC) of the RTP data packet source being
         * reported upon by this report block
         */
        /**
         * Sets the synchronization source identifier (SSRC) of the RTP data packet source being
         * reported upon by this report block.
         *
         * @param sourceSSRC
         * the synchronization source identifier (SSRC) of the RTP data packet source being
         * reported upon by this report block
         */
        /**
         * The synchronization source identifier (SSRC) of the RTP data packet source being reported
         * upon by this report block.
         */
        var sourceSSRC = 0

        /**
         * Initializes a new `VoIPMetricsReportBlock` instance by deserializing/reading a
         * binary representation from a `DataInputStream`.
         *
         * @param blockLength
         * the length of the extended report block to read, including the header, in 32-bit
         * words minus one
         * @param datainputstream
         * the binary representation from which the new instance is to be initialized. The
         * `datainputstream` is asumed to contain type-specific block contents without
         * extended report block header i.e. no block type (BT), type-specific, and block
         * length fields will be read from `datainputstream`.
         * @throws IOException
         * if an input/output error occurs while deserializing/reading the new instance from
         * `datainputstream` or the binary representation does not parse into an
         * `VoIPMetricsReportBlock` instance
         */
        constructor(blockLength: Int, datainputstream: DataInputStream) : this() {

            // block length (RFC 3611, Section 4.7)
            if (blockLength != 8 * 4) throw IOException("Invalid RTCP XR VoIP Metrics block length.")

            // SSRC of source
            sourceSSRC = datainputstream.readInt()
            // lost rate
            lossRate = datainputstream.readUnsignedByte().toShort()
            // discard rate
            discardRate = datainputstream.readUnsignedByte().toShort()
            // burst density
            burstDensity = datainputstream.readUnsignedByte().toShort()
            // gap density
            gapDensity = datainputstream.readUnsignedByte().toShort()
            // burst duration
            burstDuration = datainputstream.readUnsignedShort()
            // gap duration
            gapDuration = datainputstream.readUnsignedShort()
            // round trip delay
            roundTripDelay = datainputstream.readUnsignedShort()
            // end system delay
            endSystemDelay = datainputstream.readUnsignedShort()
            // signal level
            signalLevel = datainputstream.readByte()
            // noise level
            noiseLevel = datainputstream.readByte()
            // residual echo return loss (RERL)
            residualEchoReturnLoss = datainputstream.readByte()
            // Gmin
            gMin = datainputstream.readUnsignedByte().toShort()
            // R factor
            rFactor = datainputstream.readByte()
            // ext. R factor
            extRFactor = datainputstream.readByte()
            // MOS-LQ
            mosLq = datainputstream.readByte()
            // MOS-CQ
            mosCq = datainputstream.readByte()

            // receiver configuration byte (RX config)
            val rxConfig = datainputstream.readUnsignedByte()
            packetLossConcealment = (rxConfig and 0xC0 ushr 6).toByte()
            jitterBufferAdaptive = (rxConfig and 0x30 ushr 4).toByte()
            jitterBufferRate = (rxConfig and 0x0F).toByte()
            // reserved
            datainputstream.readByte()
            // jitter buffer nominal delay (JB nominal)
            jitterBufferNominalDelay = datainputstream.readUnsignedShort()
            // jitter buffer maximum delay (JB maximum)
            jitterBufferMaximumDelay = datainputstream.readUnsignedShort()
            // jitter buffer absolute maximum delay (JB abs max)
            jitterBufferAbsoluteMaximumDelay = datainputstream.readUnsignedShort()
        }

        /**
         * {@inheritDoc}
         */
        @Throws(IOException::class)
        override fun assemble(dataoutputstream: DataOutputStream) {
            // BT=7
            dataoutputstream.writeByte(VOIP_METRICS_REPORT_BLOCK_TYPE.toInt())
            // reserved
            dataoutputstream.writeByte(0)
            // block length = 8
            dataoutputstream.writeShort(8)
            // SSRC of source
            dataoutputstream.writeInt(sourceSSRC)
            // loss rate
            dataoutputstream.writeByte(lossRate.toInt())
            // discard rate
            dataoutputstream.writeByte(discardRate.toInt())
            // burst density
            dataoutputstream.writeByte(burstDensity.toInt())
            // gap density
            dataoutputstream.writeByte(gapDensity.toInt())
            // burst duration
            dataoutputstream.writeShort(burstDuration)
            // gap duration
            dataoutputstream.writeShort(gapDuration)
            // round trip delay
            dataoutputstream.writeShort(roundTripDelay)
            // end system delay
            dataoutputstream.writeShort(endSystemDelay)
            // signal level
            dataoutputstream.writeByte(signalLevel.toInt())
            // noise level
            dataoutputstream.writeByte(noiseLevel.toInt())
            // residual echo return loss (RERL)
            dataoutputstream.writeByte(residualEchoReturnLoss.toInt())
            // Gmin
            dataoutputstream.writeByte(gMin.toInt())
            // R factor
            dataoutputstream.writeByte(rFactor.toInt())
            // ext. R factor
            dataoutputstream.writeByte(extRFactor.toInt())
            // MOS-LQ
            dataoutputstream.writeByte(mosLq.toInt())
            // MOS-CQ
            dataoutputstream.writeByte(mosCq.toInt())
            // receiver configuration byte (RX config)
            dataoutputstream.writeByte(packetLossConcealment.toInt() and 0x03 shl 6
                    or (jitterBufferAdaptive.toInt() and 0x03 shl 4)
                    or (jitterBufferRate.toInt() and 0x0F))
            // reserved
            dataoutputstream.writeByte(0)
            // jitter buffer nominal delay (JB nominal)
            dataoutputstream.writeShort(jitterBufferNominalDelay)
            // jitter buffer maximum delay (JB maximum)
            dataoutputstream.writeShort(jitterBufferMaximumDelay)
            // jitter buffer absolute maximum delay (JB abs max)
            dataoutputstream.writeShort(jitterBufferAbsoluteMaximumDelay)
        }

        /**
         * {@inheritDoc}
         *
         * As defined by RFC 3611, a VoIP Metrics Report Block has a length in `byte`s equal
         * to `36`, including the extended report block header.
         */
        override fun calcLength(): Int {
            return (8 /* block length */ + 1) * 4
        }

        /**
         * {@inheritDoc}
         */
        override fun toString(): String {
            val s = StringBuilder("VoIP Metrics")
            s.append(", SSRC of source ").append(sourceSSRC.toLong() and 0xFFFFFFFFL)
            s.append(", loss rate ").append(lossRate.toInt())
            s.append(", discard rate ").append(discardRate.toInt())
            s.append(", burst density ").append(burstDensity.toInt())
            s.append(", gap density ").append(gapDensity.toInt())
            s.append(", burst duration ").append(burstDuration)
            s.append(", gap duration ").append(gapDuration)
            s.append(", round trip delay ").append(roundTripDelay)
            // TODO Auto-generated method stub
            return s.toString()
        }

        companion object {
            /**
             * The jitter buffer size is being dynamically adjusted to deal with varying levels of
             * jitter.
             */
            const val ADAPTIVE_JITTER_BUFFER_ADAPTIVE: Byte = 3

            /**
             * Silence is being inserted in place of lost packets.
             */
            const val DISABLED_PACKET_LOSS_CONCEALMENT: Byte = 1

            /**
             * An enhanced interpolation algorithm is being used; algorithms of this type are able to
             * conceal high packet loss rates effectively.
             */
            const val ENHANCED_PACKET_LOSS_CONCEALMENT: Byte = 2

            /**
             * The jitter buffer size is maintained at a fixed level.
             */
            const val NON_ADAPTIVE_JITTER_BUFFER_ADAPTIVE: Byte = 2
            const val RESERVED_JITTER_BUFFER_ADAPTIVE: Byte = 1
            const val SDP_PARAMETER = "voip-metrics"

            /**
             * A simple replay or interpolation algorithm is being used to fill-in the missing packet;
             * this approach is typically able to conceal isolated lost packets at low packet loss
             * rates.
             */
            const val STANDARD_PACKET_LOSS_CONCEALMENT: Byte = 3
            const val UNKNOWN_JITTER_BUFFER_ADAPTIVE: Byte = 0

            /**
             * No information is available concerning the use of packet loss concealment (PLC); however,
             * for some codecs this may be inferred.
             */
            const val UNSPECIFIED_PACKET_LOSS_CONCEALMENT: Byte = 0
            const val VOIP_METRICS_REPORT_BLOCK_TYPE: Short = 7
        }
    }

    /**
     * The list of zero or more extended report blocks carried by this `RTCPExtendedReport`.
     */
    val reportBlocks = LinkedList<ReportBlock>()
        /**
         * Gets a list of the extended report blocks carried by this `RTCPExtendedReport`.
         */
        get() {
            return field // Collections.unmodifiableList(reportBlocks);
        }

    /**
     * Gets the synchronization source identifier (SSRC) of the originator of this XR packet.
     *
     * @return the synchronization source identifier (SSRC) of the originator of this XR packet
     */
    /**
     * Sets the synchronization source identifier (SSRC) of the originator of this XR packet.
     *
     * @param ssrc
     * the synchronization source identifier (SSRC) of the originator of this XR packet
     */
    /**
     * The synchronization source identifier (SSRC) of the originator of this XR packet.
     */
    var ssrc = 0

    /**
     * Gets the `System` time in milliseconds at which this `RTCPExtendedReport` has
     * been received or sent by the local endpoint.
     *
     * @return the `System` time in milliseconds at which this `RTCPExtendedReport`
     * has been received or sent by the local endpoint
     */
    /**
     * Sets the `System` time in milliseconds at which this `RTCPExtendedReport` has
     * been received or sent by the local endpoint.
     *
     * @param systemTimeStamp
     * the `System` time in milliseconds at which this `RTCPExtendedReport` has
     * been received or sent by the local endpoint
     */
    /**
     * The `System` time in milliseconds at which this `RTCPExtendedReport` has been
     * received or sent by the local endpoint.
     */
    var systemTimeStamp: Long = 0

    /**
     * Initializes a new `RTCPExtendedReport` instance.
     */
    init {
        type = XR
    }

    /**
     * Initializes a new `RTCPExtendedReport instance by deserializing/reading a binary
     * representation from a `byte` array.
     *
     * @param buf the binary representation from which the new instance is to be initialized
     * @param off the offset in `buf` at which the binary representation starts
     * @param len the number of `byte`s in `buf` starting at `off` which comprise
     * the binary representation
     * @throws IOException if an input/output error occurs while deserializing/reading
     * the new instance from `buf` or the binary representation does not parse into an
     * `RTCPExtendedReport` instance
    ` */
    constructor(buf: ByteArray?, off: Int, len: Int) : this(DataInputStream(ByteArrayInputStream(buf, off, len)))

    /**
     * Initializes a new `RTCPExtendedReport` instance by deserializing/reading a binary
     * representation from a `DataInputStream`.
     *
     * @param datainputstream
     * the binary representation from which the new instance is to be initialized.
     * @throws IOException
     * if an input/output error occurs while deserializing/reading the new instance from
     * `datainputstream` or the binary representation does not parse into an
     * `RTCPExtendedReport` instance.
     */
    constructor(datainputstream: DataInputStream) : this() {

        // V=2, P, reserved
        val b0 = datainputstream.readUnsignedByte()

        // PT=XR=207
        val pt = datainputstream.readUnsignedByte()

        // length
        val length = datainputstream.readUnsignedShort()
        if (length < 1) throw IOException("Invalid RTCP length.")
        parse(b0, pt, length, datainputstream)
    }

    /**
     * Initializes a new `RTCPExtendedReport` instance by deserializing/reading a binary
     * representation of part of the packet from a `DataInputStream`, and taking the values
     * found in the first 4 bytes of the binary representation as arguments.
     *
     * @param b0
     * the first byte of the binary representation.
     * @param pt
     * the value of the `packet type` field.
     * @param length
     * the value of the `length` field.
     * @param datainputstream
     * the binary representation from which the new instance is to be initialized, excluding
     * the first 4 bytes.
     * @throws IOException
     * if an input/output error occurs while deserializing/reading the new instance from
     * `datainputstream` or the binary representation does not parse into an
     * `RTCPExtendedReport` instance.
     */
    constructor(b0: Int, pt: Int, length: Int, datainputstream: DataInputStream) : this() {
        parse(b0, pt, length, datainputstream)
    }

    /**
     * Initializes a new `RTCPExtendedReport` instance by deserializing/reading a binary
     * representation of part of the packet from a `DataInputStream`, and taking the values
     * normally found in the first 4 bytes of the binary representation as arguments.
     *
     * @param b0
     * the first byte of the binary representation.
     * @param pt
     * the value of the `packet type` field.
     * @param len
     * the value of the `length` field.
     * @param datainputstream
     * the binary representation from which the new instance is to be initialized, excluding
     * the first 4 bytes.
     * @throws IOException
     * if an input/output error occurs while deserializing/reading the new instance from
     * `datainputstream` or the binary representation does not parse into an
     * `RTCPExtendedReport` instance.
     */
    @Throws(IOException::class)
    private fun parse(b0: Int, pt: Int, len: Int, datainputstream: DataInputStream) {
        // The first 4 bytes have already been read.
        var length = len
        length -= 4

        // V=2
        if (b0 and 0xc0 != 128) throw IOException("Invalid RTCP version (V).")
        if (pt != XR) throw IOException("Invalid RTCP packet type (PT).")

        // SSRC
        ssrc = datainputstream.readInt()
        length -= 4

        // report blocks. A block is at least 4 bytes long.
        while (length >= 4) {
            // block type (BT)
            val bt = datainputstream.readUnsignedByte()

            // type-specific
            datainputstream.readByte()

            // block length in bytes, including the block header
            val blockLength = datainputstream.readUnsignedShort() + 1 shl 2
            if (length < blockLength) {
                throw IOException("Invalid extended block")
            }
            if (bt == VoIPMetricsReportBlock.VOIP_METRICS_REPORT_BLOCK_TYPE.toInt()) {
                addReportBlock(VoIPMetricsReportBlock(blockLength - 4, datainputstream))
            } else {
                // The implementation reads and ignores any extended report
                // blocks other than VoIP Metrics Report Block.

                // Already read 4 bytes
                datainputstream.skip((blockLength - 4).toLong())
            }
            length -= blockLength
        }

        // If we didn't read all bytes of the packet, the stream is probably in
        // an inconsistent state.
        if (length != 0) {
            throw IOException("Invalid XR packet, unread bytes")
        }
    }

    /**
     * Adds an extended report block to this extended report.
     *
     * @param reportBlock
     * the extended report block to add to this extended report
     * @return `true` if the list of extended report blocks carried by this extended report
     * changed because of the method invocation; otherwise, `false`
     * @throws NullPointerException
     * if `reportBlock` is `null`
     */
    fun addReportBlock(reportBlock: ReportBlock?): Boolean {
        return if (reportBlock == null) throw NullPointerException("reportBlock") else reportBlocks.add(reportBlock)
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun assemble(dataoutputstream: DataOutputStream) {
        // V=2, P, reserved
        dataoutputstream.writeByte(128)
        // PT=XR=207
        dataoutputstream.writeByte(XR)
        // length
        dataoutputstream.writeShort(calcLength() / 4 - 1)
        // SSRC
        dataoutputstream.writeInt(ssrc)
        // report blocks
        for (reportBlock in reportBlocks) reportBlock.assemble(dataoutputstream)
    }

    /**
     * {@inheritDoc}
     */
    override fun calcLength(): Int {
        var length = (1 /* V=2, P, reserved */
                + 1 /* PT */
                + 2 /* length */
                + 4) /* SSRC */

        // report blocks
        for (reportBlock in reportBlocks) length += reportBlock.calcLength()
        return length
    }

    /**
     * Gets the number of the extended report blocks carried by this `RTCPExtendedReport`.
     *
     * @return the number of the extended report blocks carried by this `RTCPExtendedReport`
     */
    val reportBlockCount: Int
        get() = reportBlocks.size

    /**
     * Removes an extended report block from this extended report.
     *
     * @param reportBlock the extended report block to remove from this extended report
     * @return `true` if the list of extended report blocks carried by this extended report
     * changed because of the method invocation; otherwise, `false`
     */
    fun removeReportBlock(reportBlock: ReportBlock?): Boolean {
        return if (reportBlock == null) false else reportBlocks.remove(reportBlock)
    }

    /**
     * {@inheritDoc}
     */
    override fun toString(): String {
        val s = StringBuilder("RTCP XR")

        // SSRC
        s.append(", SSRC ").append(ssrc.toLong() and 0xFFFFFFFFL)
        var b = false

        // report blocks
        s.append(", report blocks [")
        for (reportBlock in reportBlocks) {
            if (b) s.append("; ") else b = true
            s.append(reportBlock)
        }
        s.append("]")
        return s.toString()
    }

    companion object {
        const val SDP_ATTRIBUTE = "rtcp-xr"

        /**
         * The packet type (PT) constant `207` which identifies RTCP XR packets.
         */
        const val XR = 207
    }
}