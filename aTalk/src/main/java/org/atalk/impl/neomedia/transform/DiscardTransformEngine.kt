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
package org.atalk.impl.neomedia.transform

import net.sf.fmj.media.rtp.RTCPPacket
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.rtp.ResumableStreamRewriter
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.RawPacket
import org.atalk.util.RTCPUtils
import timber.log.Timber
import javax.media.Buffer

/**
 * As the name suggests, the DiscardTransformEngine discards packets that are
 * flagged for discard. The packets that are passed on in the chain have their
 * sequence numbers rewritten hiding the gaps created by the dropped packets.
 *
 *
 * Instances of this class are not thread-safe. If multiple threads access an
 * instance concurrently, it must be synchronized externally.
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
class DiscardTransformEngine
/**
 * Ctor.
 *
 * @param stream the [MediaStream] that owns this instance.
 */
    (
        /**
         * The [MediaStream] that owns this instance.
         */
        private val stream: MediaStream,
) : TransformEngine {
    /**
     * A map of source ssrc to [ResumableStreamRewriter].
     */
    private val ssrcToRewriter = HashMap<Long, ResumableStreamRewriter>()

    /**
     * The [PacketTransformer] for RTCP packets.
     */
    override val rtpTransformer = object : SinglePacketTransformerAdapter() {
        /**
         * {@inheritDoc}
         */
        override fun reverseTransform(pkt: RawPacket): RawPacket? {
            if (pkt == null) {
                return null
            }

            val dropPkt = (pkt.flags and Buffer.FLAG_DISCARD) == Buffer.FLAG_DISCARD
            val ssrc = pkt.getSSRCAsLong()
            var rewriter: ResumableStreamRewriter?
            synchronized(ssrcToRewriter) {
                rewriter = ssrcToRewriter[ssrc]
                if (rewriter == null) {
                    rewriter = ResumableStreamRewriter()
                    ssrcToRewriter[ssrc] = rewriter!!
                }
            }
            rewriter!!.rewriteRTP(!dropPkt, pkt.buffer, pkt.offset, pkt.length)

            Timber.log(TimberLog.FINER, "%s RTP ssrc = %s, seqnum = %s, ts = %s, streamHashCode = %s",
                if (dropPkt) "discarding " else "passing through ", pkt.getSSRCAsLong(),
                pkt.sequenceNumber, pkt.timestamp, stream.hashCode())
            return if (dropPkt) null else pkt
        }
    }

    /**
     * The [PacketTransformer] for RTP packets.
     */
    override val rtcpTransformer = object : SinglePacketTransformerAdapter() {
        /**
         * {@inheritDoc}
         */
        override fun reverseTransform(pkt: RawPacket): RawPacket {
            if (pkt == null) {
                return pkt
            }

            val buf = pkt.buffer
            val offset = pkt.offset
            val length = pkt.length

            // The correct thing to do here is a loop because the RTCP packet
            // can be compound. However, in practice we haven't seen multiple
            // SRs being bundled in the same compound packet, and we're only
            // interested in SRs.

            // Check RTCP packet validity. This makes sure that pktLen > 0
            // so this loop will eventually terminate.
            if (!RawPacket.isRtpRtcp(buf, offset, length)) {
                return pkt
            }

            val pktLen = RTCPUtils.getLength(buf, offset, length)
            val pt = RTCPUtils.getPacketType(buf, offset, pktLen)
            if (pt == RTCPPacket.SR) {
                val ssrc = RawPacket.getRTCPSSRC(buf, offset, pktLen)
                var rewriter: ResumableStreamRewriter?
                synchronized(ssrcToRewriter) {
                    rewriter = ssrcToRewriter[ssrc]
                }
                if (rewriter != null) {
                    rewriter!!.processRTCP(true /* rewrite */, buf, offset, pktLen)
                }
            }
            return pkt
        }
    }
}