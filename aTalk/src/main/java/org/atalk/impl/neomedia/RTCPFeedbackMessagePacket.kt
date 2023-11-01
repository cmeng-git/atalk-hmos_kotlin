/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import org.atalk.impl.neomedia.rtp.translator.Payload
import org.atalk.service.neomedia.event.RTCPFeedbackMessageEvent
import javax.media.rtp.OutputDataStream

/**
 * Represents an RTCP feedback message packet as described by RFC 4585 &quot;Extended RTP Profile
 * for Real-time Transport Control Protocol (RTCP)-Based Feedback (RTP/AVPF)&quot;.
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class RTCPFeedbackMessagePacket(fmt: Int, pt: Int, senderSSRC: Long, sourceSSRC: Long) : Payload {
    /**
     * Feedback message type (FMT).
     */
    private var fmt = 0

    /**
     * Packet type (PT).
     */
    private var pt = 0

    /**
     * SSRC of packet sender.
     */
    private var senderSSRC = 0L

    /**
     * SSRC of media source.
     */
    private var sourceSSRC = 0L

    /**
     * The (command) sequence number of this Full Intra Request (FIR) RTCP feedback message as
     * defined by RFC 5104 &quot;Codec Control Messages in the RTP Audio-Visual Profile with
     * Feedback (AVPF)&quot. The sequence number space is unique for each pairing of the SSRC of
     * command source and the SSRC of the command target. The sequence number SHALL be increased by
     * 1 modulo 256 for each new command. A repetition SHALL NOT increase the sequence number. The
     * initial value is arbitrary.
     */
    private var seqNr = 0

    /**
     * Constructor.
     *
     * fmt feedback message type
     * pt payload type
     * senderSSRC SSRC of packet sender
     * sourceSSRC SSRC of media source
     */
    init {
        setFeedbackMessageType(fmt)
        setPayloadType(pt)
        setSenderSSRC(senderSSRC)
        setSourceSSRC(sourceSSRC)
    }

    /**
     * Gets the feedback message type (FMT) of this `RTCPFeedbackMessagePacket`.
     *
     * @return the feedback message type (FMT) of this `RTCPFeedbackMessagePacket`
     */
    private fun getFeedbackMessageType(): Int {
        return fmt
    }

    /**
     * Gets the packet type (PT) of this `RTCPFeedbackMessagePacket`.
     *
     * @return the packet type (PT) of this `RTCPFeedbackMessagePacket`
     */
    private fun getPayloadType(): Int {
        return pt
    }

    /**
     * Gets the synchronization source identifier (SSRC) of the originator of this packet.
     *
     * @return the synchronization source identifier (SSRC) of the originator of this packet
     */
    private fun getSenderSSRC(): Long {
        return senderSSRC
    }

    /**
     * Gets the (command) sequence number of this Full Intra Request (FIR) RTCP feedback message as
     * defined by RFC 5104 &quot;Codec Control Messages in the RTP Audio-Visual Profile with
     * Feedback (AVPF)&quot. The sequence number space is unique for each pairing of the SSRC of
     * command source and the SSRC of the command target. The sequence number SHALL be increased by
     * 1 modulo 256 for each new command. A repetition SHALL NOT increase the sequence number. The
     * initial value is arbitrary.
     *
     * @return the (command) sequence number of this Full Intra Request (FIR) RTCP feedback message
     */
    private fun getSequenceNumber(): Int {
        return seqNr
    }

    /**
     * Gets the synchronization source identifier (SSRC) of the media source that this piece of
     * feedback information is related to.
     *
     * @return the synchronization source identifier (SSRC) of the media source that this piece of
     * feedback information is related to
     */
    private fun getSourceSSRC(): Long {
        return sourceSSRC
    }

    /**
     * Sets the feedback message type (FMT) of this `RTCPFeedbackMessagePacket`.
     *
     * @param fmt
     * the feedback message type (FMT) to set on this `RTCPFeedbackMessagePacket`
     */
    private fun setFeedbackMessageType(fmt: Int) {
        this.fmt = fmt
    }

    /**
     * Sets the packet type (PT) of this `RTCPFeedbackMessagePacket`.
     *
     * @param pt
     * the packet type (PT) to set on this `RTCPFeedbackMessagePacket`
     */
    private fun setPayloadType(pt: Int) {
        this.pt = pt
    }

    /**
     * Sets the synchronization source identifier (SSRC) of the originator of this packet.
     *
     * @param senderSSRC
     * the synchronization source identifier (SSRC) of the originator of this packet
     */
    private fun setSenderSSRC(senderSSRC: Long) {
        this.senderSSRC = senderSSRC
    }

    /**
     * Sets the (command) sequence number of this Full Intra Request (FIR) RTCP feedback message as
     * defined by RFC 5104 &quot;Codec Control Messages in the RTP Audio-Visual Profile with
     * Feedback (AVPF)&quot. The sequence number space is unique for each pairing of the SSRC of
     * command source and the SSRC of the command target. The sequence number SHALL be increased by
     * 1 modulo 256 for each new command.
     *
     * @param seqNr
     * the (command) sequence number to set on this Full Intra Request (FIR) RTCP feedback
     * message
     */
    fun setSequenceNumber(seqNr: Int) {
        this.seqNr = seqNr
    }

    /**
     * Sets the synchronization source identifier (SSRC) of the media source that this piece of
     * feedback information is related to.
     *
     * @param sourceSSRC
     * the synchronization source identifier (SSRC) of the media source that this piece of
     * feedback information is related to
     */
    private fun setSourceSSRC(sourceSSRC: Long) {
        this.sourceSSRC = sourceSSRC
    }

    /**
     * Write the RTCP packet representation of this instance into a specific
     * `OutputDataStream`.
     *
     * @param out
     * the `OutputDataStream` into which the RTCP packet representation of this
     * instance is to be written
     */
    override fun writeTo(stream: OutputDataStream) {
        /*
		 * The length of this RTCP packet in 32-bit words minus one, including the header and any
		 * padding.
		 */
        val fmt = getFeedbackMessageType()
        val pt = getPayloadType()
        val fir = (pt == RTCPFeedbackMessageEvent.PT_PS
                && fmt == RTCPFeedbackMessageEvent.FMT_FIR)
        var rtcpPacketLength = 2

        if (fir) {
            /*
			 * RFC 5104
			 * "Codec Control Messages in the RTP Audio-Visual Profile with Feedback (AVPF)" defines
			 * that The length of the FIR feedback message MUST be set to 2 + 2 * N, where N is the
			 * number of FCI entries.
			 */
            rtcpPacketLength += 2 *  /* N */1
        }
        val len = (rtcpPacketLength + 1) * 4
        val buf = ByteArray(len)
        var off = 0

        /*
		 * version (V): 2 bits, padding (P): 1 bit, feedback message type (FMT): 5 bits.
		 */
        buf[off++] = (0x80 /* RTP version */ or (fmt and 0x1F)).toByte()
        // packet type (PT): 8 bits
        buf[off++] = pt.toByte()

        // length: 16 bits
        buf[off++] = (rtcpPacketLength and 0xFF00 shr 8).toByte()
        buf[off++] = (rtcpPacketLength and 0x00FF).toByte()

        // SSRC of packet sender: 32 bits
        writeSSRC(getSenderSSRC(), buf, off)
        off += 4

        // SSRC of media source: 32 bits
        val sourceSSRC = getSourceSSRC()

        /*
		 * RFC 5104 "Codec Control Messages in the RTP Audio-Visual Profile with Feedback (AVPF)"
		 * defines that the "SSRC of media source" is not used by the FIR feedback message and SHALL
		 * be set to 0.
		 */
        writeSSRC(
                if (fir) 0 else sourceSSRC,
                buf,
                off)
        off += 4

        // FCI entries
        if (fir) {
            /*
             * SSRC: 32 bits. The SSRC value of the media sender that is
             * requested to send a decoder refresh point.
             */
            writeSSRC(sourceSSRC, buf, off)
            off += 4
            // Seq nr.: 8 bits
            buf[off++] = (getSequenceNumber() % 256).toByte()
            /*
			 * Reserved: 24 bits. All bits SHALL be set to 0 by the sender and SHALL be ignored on
			 * reception.
			 */
            buf[off++] = 0
            buf[off++] = 0
            buf[off++] = 0
        }
        stream.write(buf, 0, len)
    }

    companion object {
        /**
         * Writes a specific synchronization source identifier (SSRC) into a specific `byte`
         * array starting at a specific offset.
         *
         * @param ssrc
         * the synchronization source identifier (SSRC) to write into `buf` starting at `off`
         * @param buf the `byte` array to write the specified `ssrc` into starting at `off`
         * @param offset the offset in `buf` at which the writing of `ssrc` is to start
         */
        fun writeSSRC(ssrc: Long, buf: ByteArray, offset: Int) {
            var off = offset
            buf[off++] = (ssrc shr 24).toByte()
            buf[off++] = (ssrc shr 16 and 0xFFL).toByte()
            buf[off++] = (ssrc shr 8 and 0xFFL).toByte()
            buf[off] = (ssrc and 0xFFL).toByte()
        }
    }
}