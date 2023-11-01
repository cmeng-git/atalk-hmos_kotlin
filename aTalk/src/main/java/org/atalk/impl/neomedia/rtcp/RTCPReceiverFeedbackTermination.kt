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
import net.sf.fmj.media.rtp.RTCPRRPacket
import net.sf.fmj.media.rtp.RTCPReportBlock
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.MediaStreamImpl
import org.atalk.impl.neomedia.RTCPPacketPredicate
import org.atalk.impl.neomedia.rtp.translator.RTPTranslatorImpl
import org.atalk.impl.neomedia.transform.PacketTransformer
import org.atalk.impl.neomedia.transform.SinglePacketTransformerAdapter
import org.atalk.impl.neomedia.transform.TransformEngine
import org.atalk.service.neomedia.RawPacket
import org.atalk.service.neomedia.TransmissionFailedException
import org.atalk.service.neomedia.event.RTCPFeedbackMessageEvent
import org.atalk.util.ArrayUtils
import org.atalk.util.RTCPUtils
import org.atalk.util.concurrent.PeriodicRunnable
import org.atalk.util.function.RTCPGenerator
import timber.log.Timber
import kotlin.math.min

/**
 * Terminates RRs and REMBs.
 *
 * @author George Politis
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class RTCPReceiverFeedbackTermination(stream: MediaStreamImpl) : PeriodicRunnable(REPORT_PERIOD_MS), TransformEngine {
    /**
     * The generator that generates `RawPacket`s from `RTCPCompoundPacket`s.
     */
    private val generator = RTCPGenerator()

    /**
     * The [MediaStreamImpl] that owns this instance.
     */
    private val stream: MediaStreamImpl?

    /**
     * Ctor.
     *
     * stream the MediaStream that owns this instance.
     */
    init {
        this.stream = stream
    }

    /**
     * {@inheritDoc}
     */
    override fun run() {
        super.run()

        // cmeng - just return if stream or rtpTranslator is null i.e. nothing to report
        if (stream!!.getRTPTranslator() == null) return

        // Create and return the packet.
        // We use the stream's local source ID (SSRC) as the SSRC of packet sender.
        val senderSSRC = senderSSRC
        if (senderSSRC == -1L) {
            return
        }

        // RRs
        val rrs = makeRRs(senderSSRC)

        // Bail out (early) if we have nothing to report.
        if (ArrayUtils.isNullOrEmpty(rrs)) {
            return
        }

        // REMB
        val remb = makeREMB(senderSSRC)

        // Build the RTCP compound packet to return.
        val rtcpPackets = if (remb == null) {
            rrs
        } else {
            // NOTE the add method throws an exception if remb == null.
            ArrayUtils.add(rrs, RTCPRRPacket::class.java, remb as RTCPRRPacket)
        }
        val compound = RTCPCompoundPacket(rtcpPackets)

        // inject the packets into the MediaStream.
        val pkt = generator.apply(compound)
        try {
            stream.injectPacket(pkt, false, this)
        } catch (e: TransmissionFailedException) {
            Timber.e(e, "transmission of an RTCP packet failed.")
        }
    }

    /**
     * (attempts) to get the local SSRC that will be used in the media sender
     * SSRC field of the RTCP reports. TAG(cat4-local-ssrc-hurricane)
     *
     * @return the local sender SSRC ID
     */
    private val senderSSRC: Long
        get() {
            val streamRTPManager = stream!!.streamRTPManager ?: return -1
            return streamRTPManager.localSSRC
        }

    /**
     * Makes `RTCPRRPacket`s using information in FMJ.
     *
     * @return A `List` of `RTCPRRPacket`s to inject into the `MediaStream`.
     */
    private fun makeRRs(senderSSRC: Long): Array<RTCPRRPacket>? {
        val reportBlocks = makeReportBlocks()
        if (ArrayUtils.isNullOrEmpty(reportBlocks)) {
            return null
        }
        val mod = reportBlocks.size % MAX_RTCP_REPORT_BLOCKS
        val div = reportBlocks.size / MAX_RTCP_REPORT_BLOCKS
        val size = if (mod == 0) div else div + 1
        val rrs = ArrayList<RTCPRRPacket>(size)
        // val rrs = arrayOfNulls<RTCPRRPacket>(size)

        // Since a maximum of 31 reception report blocks will fit in an SR or RR packet,
        // additional RR packets SHOULD be stacked after the initial SR or RR packet as needed to
        // contain the reception reports for all sources heard during the interval since the last report.
        if (reportBlocks.size > MAX_RTCP_REPORT_BLOCKS) {
            var rrIdx = 0
            var off = 0
            while (off < reportBlocks.size) {
                val blockCount = min(reportBlocks.size - off, MAX_RTCP_REPORT_BLOCKS)
                val blocks = arrayOfNulls<RTCPReportBlock>(blockCount)
                System.arraycopy(reportBlocks, off, blocks, 0, blocks.size)
                rrs[rrIdx++] = RTCPRRPacket(senderSSRC.toInt(), blocks)
                off += MAX_RTCP_REPORT_BLOCKS
            }
        } else {
            rrs[0] = RTCPRRPacket(senderSSRC.toInt(), reportBlocks)
        }
        return rrs.toTypedArray()
    }

    /**
     * Iterate through all the `ReceiveStream`s that this `MediaStream` has and
     * make `RTCPReportBlock`s for all of them.
     *
     * cmeng: rptTranslator is currently disabled for Android or peer is not the conference focus.
     *
     * [net.java.sip.communicator.service.protocol.media.MediaAwareCallConference.getRTPTranslator]
     *
     * @return
     */
    private fun makeReportBlocks(): Array<RTCPReportBlock> {
        // State validation.
        if (stream == null) {
            Timber.w("stream is null.")
            return MIN_RTCP_REPORT_BLOCKS_ARRAY
        }
        val streamRTPManager = stream.streamRTPManager
        if (streamRTPManager == null) {
            Timber.w("streamRTPManager is null.")
            return MIN_RTCP_REPORT_BLOCKS_ARRAY
        }

        /*
         * XXX MediaStreamImpl's implementation of #getReceiveStreams() says that, unfortunately,
         * it has been observed that sometimes there are valid ReceiveStreams in MediaStreamImpl
         * which are not returned by FMJ's RTPManager. Since
         * (1) MediaStreamImpl#getReceiveStreams() will include the results of StreamRTPManager#getReceiveStreams() and
         * (2) we are going to check the results against SSRCCache, it should be relatively safe
         * to rely on MediaStreamImpl's implementation.
         */
        val receiveStreams = stream.getReceiveStreams()
        if (receiveStreams == null || receiveStreams.isEmpty()) {
            Timber.d("There are no receive streams to build report blocks for.")
            return MIN_RTCP_REPORT_BLOCKS_ARRAY
        }
        val rtpTranslator = stream.getRTPTranslator()!!
        val cache = rtpTranslator.sSRCCache
        // SSRCCache cache = stream.getRTPTranslator().getSSRCCache();
        if (cache == null) {
            Timber.i("cache is null.")
            return MIN_RTCP_REPORT_BLOCKS_ARRAY
        }
        // Create and populate the return object.
        val reportBlocks = ArrayList<RTCPReportBlock>()
        for (receiveStream in receiveStreams) {
            // Dig into the guts of FMJ and get the stats for the current receiveStream.
            val info = cache.cache.get(receiveStream!!.ssrc.toInt())
            if (info == null) {
                Timber.w("We have a ReceiveStream but not an SSRCInfo for that ReceiveStream.")
                continue
            }
            if (!info.ours && info.sender) {
                val reportBlock = info.makeReceiverReport(lastProcessTime)
                reportBlocks.add(reportBlock)
                Timber.log(TimberLog.FINER, "%s", stream.getDiagnosticContext()
                        .makeTimeSeriesPoint("created_report_block")
                        .addField("rtcp_termination", hashCode())
                        .addField("ssrc", reportBlock.ssrc)
                        .addField("num_lost", reportBlock.numLost)
                        .addField("fraction_lost", reportBlock.fractionLost / 256.0)
                        .addField("jitter", reportBlock.jitter)
                        .addField("xtnd_seqnum", reportBlock.xtndSeqNum))
            }
        }
        return reportBlocks.toTypedArray<RTCPReportBlock>()
    }

    /**
     * Makes an `RTCPREMBPacket` that provides receiver feedback to the
     * endpoint from which we receive.
     *
     * @return an `RTCPREMBPacket` that provides receiver feedback to the
     * endpoint from which we receive.
     */
    private fun makeREMB(senderSSRC: Long): RTCPREMBPacket? {
        // Destination
        val remoteBitrateEstimator = stream!!.remoteBitrateEstimator!!
        if (!remoteBitrateEstimator.receiveSideBweEnabled()) {
            return null
        }
        val ssrcs = remoteBitrateEstimator.ssrcs!!

        // TODO(gp) intersect with SSRCs from signaled simulcast layers
        // NOTE(gp) The Google Congestion Control algorithm (sender side)
        // doesn't seem to care about the SSRCs in the dest field.
        val dest = LongArray(ssrcs.size)
        var i = 0
        for (ssrc in ssrcs) dest[i++] = ssrc

        // Exp & mantissa
        val bitrate = remoteBitrateEstimator.latestEstimate
        Timber.d("Estimated bitrate (bps): %s, dest: %s, time (ms): %s",
                bitrate, dest.contentToString(), System.currentTimeMillis())
        return if (bitrate == -1L) {
            null
        } else {
            RTCPREMBPacket(senderSSRC, 0L, bitrate, dest)
        }
    }

    /**
     * {@inheritDoc}
     */
    override val rtpTransformer: PacketTransformer?
        get() = null

    /**
     * {@inheritDoc}
     */
    override val rtcpTransformer = RTCPTransformer()

    /**
     *
     */
    inner class RTCPTransformer
    /**
     * Ctor.
     */
        : SinglePacketTransformerAdapter(RTCPPacketPredicate) {
        /**
         * {@inheritDoc}
         */
        override fun transform(pkt: RawPacket): RawPacket? {
            // Kill the RRs that FMJ is sending.
            return doTransform(pkt, true)
        }

        /**
         * {@inheritDoc}
         */
        override fun reverseTransform(pkt: RawPacket): RawPacket? {
            return doTransform(pkt, false)
        }

        private fun doTransform(pkt: RawPacket, send: Boolean): RawPacket? {
            val it = RTCPIterator(pkt)
            while (it.hasNext()) {
                val baf = it.next()
                val pt = RTCPUtils.getPacketType(baf)
                if (pt == RTCPRRPacket.RR || RTCPREMBPacket.isREMBPacket(baf)
                        || RTCPTCCPacket.isTCCPacket(baf)) {
                    it.remove()
                    continue
                }
                if (!send && pt > -1) {
                    val fmt = RTCPUtils.getReportCount(baf)
                    if ((pt == RTCPFeedbackMessageEvent.PT_PS
                                    && fmt == RTCPFeedbackMessageEvent.FMT_PLI)
                            || (pt == RTCPFeedbackMessageEvent.PT_PS
                                    && fmt == RTCPFeedbackMessageEvent.FMT_FIR)) {
                        val source = RTCPFBPacket.getSourceSSRC(baf)
                        (stream!!.getRTPTranslator() as RTPTranslatorImpl).rtcpFeedbackMessageSender.requestKeyframe(source)
                        it.remove()
                    }
                }
            }
            return if (pkt.length == 0) null else pkt
        }
    }

    companion object {
        /**
         * The maximum number of RTCP report blocks that an RR can contain.
         */
        private const val MAX_RTCP_REPORT_BLOCKS = 31

        /**
         * The minimum number of RTCP report blocks that an RR can contain.
         */
        private const val MIN_RTCP_REPORT_BLOCKS = 0

        /**
         * The reporting period for RRs and REMBs.
         */
        private const val REPORT_PERIOD_MS = 500L

        /**
         * A reusable array that holds [.MIN_RTCP_REPORT_BLOCKS] `RTCPReportBlock`s.
         */
        private val MIN_RTCP_REPORT_BLOCKS_ARRAY = emptyArray<RTCPReportBlock>()
    }
}