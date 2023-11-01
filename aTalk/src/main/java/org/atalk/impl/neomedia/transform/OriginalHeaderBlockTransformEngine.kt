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

import org.atalk.impl.neomedia.RTPPacketPredicate
import org.atalk.service.neomedia.RawPacket
import org.atalk.util.RTPUtils
import timber.log.Timber

/**
 * Appends an Original Header Block packet extension to incoming packets.
 * Note that we currently do NOT follow the PERC format, but rather an extended
 * backward compatible format.
 * {@see "https://tools.ietf.org/html/draft-ietf-perc-double-02"}
 *
 * Specifically the format the we currently append is
 * <pre>`0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-------------+---------------+-------------------------------+
 * |  id  | len  |R|     PT      |        Sequence Number        |
 * +-------------+---------------+-------------------------------+
 * |                         Timestamp                           |
 * +-------------------------------------------------------------+
 * |                            SSRC                             |
 * +-------------------------------------------------------------+
 *
`</pre> *
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class OriginalHeaderBlockTransformEngine
/**
 * Initializes a new [OriginalHeaderBlockTransformEngine] instance.
 */
    : SinglePacketTransformerAdapter(RTPPacketPredicate.INSTANCE), TransformEngine {
    /**
     * The ID of the OHB RTP header extension, or -1 if it is not enabled.
     */
    private var extensionID = -1

    /**
     * Implements [SinglePacketTransformer.reverseTransform].
     */
    override fun reverseTransform(pkt: RawPacket): RawPacket {
        if (extensionID != -1) {
            // TODO: check if an OHB ext already exists.
            addExtension(pkt)
        }
        return pkt
    }

    /**
     * Here we would re-form or remove the OHB extension to only include fields
     * which we modified, in order to reduce the overhead.
     */
    override fun transform(pkt: RawPacket): RawPacket {
        return pkt

        // We would want to do something like the below if we wanted to optimize
        // the packet size (by only including the modified fields in the OHB).
        /*
        if (extensionID != -1)
        {
            RawPacket.HeaderExtension ohb = pkt.getHeaderExtension(extensionID);
            if (ohb != null)
            {
                rebuildOhb(pkt, ohb);
            }
        }
        return pkt;
        */
    }

    /**
     * Removes any unmodified fields from the OHB header extension of a [RawPacket].
     *
     * @param pkt the packet.
     * @param ohb the OHB header extension.
     */
    private fun rebuildOhb(pkt: RawPacket, ohb: RawPacket.HeaderExtension) {
        val buf = ohb.buffer
        val off = ohb.offset

        // Make sure it was us who added the OHB (in reverseTransform). If it
        // came from elsewhere (i.e. the sender), we should handle it in another way.
        val len = ohb.extLength
        if (len != 11) {
            Timber.w("Unexpected OHB length.")
            return
        }

        // The new, potentially modified values.
        val pt = pkt.payloadType
        val seq = pkt.sequenceNumber
        val ts = pkt.timestamp
        val ssrc = pkt.getSSRCAsLong()

        // The original values.
        val origPt = buf[off + 1]
        val origSeq = RTPUtils.readUint16AsInt(buf, off + 2)
        val origTs = RTPUtils.readUint32AsLong(buf, off + 4)
        val origSsrc = RTPUtils.readUint32AsLong(buf, off + 8)

        val newLen = getLength(pt != origPt, seq != origSeq, ts != origTs, ssrc != origSsrc)

        // If the lengths match, we don't have anything to change.
        if (newLen != len) {
            // TODO:
            // 1. remove the old extension
            // 2. maybe add a new one
        }
    }

    /**
     * @param pt whether the PR was modified.
     * @param seq whether the sequence number was modified.
     * @param ts whether the timestamp was modified.
     * @param ssrc whether the SSRC was modified.
     * @return the length of the OHB extension, given the fields which differ from the original packet.
     */
    private fun getLength(pt: Boolean, seq: Boolean, ts: Boolean, ssrc: Boolean): Int {
        when {
            !pt && !seq && !ts && !ssrc -> return 0
            pt && !seq && !ts && !ssrc -> return 1
            !pt && seq && !ts && !ssrc -> return 2
            !pt && !seq && ts && !ssrc -> return 4
            !pt && !seq && !ts && ssrc -> return 5
            pt && seq && !ts && !ssrc -> return 3
            pt && !seq && ts && !ssrc -> return 5
            pt && !seq && !ts && ssrc -> return 5
            !pt && seq && ts && !ssrc -> return 6
            !pt && seq && !ts && ssrc -> return 7
            !pt && !seq && ts && ssrc -> return 8
            pt && seq && ts && !ssrc -> return 7
            pt && seq && !ts && ssrc -> return 7
            pt && !seq && ts && ssrc -> return 9
            !pt && seq && ts && ssrc -> return 10
            pt && seq && ts && ssrc -> return 11
            else -> throw IllegalStateException()
        }
    }

    /**
     * Implements [TransformEngine.rtpTransformer].
     */
    override val rtpTransformer: PacketTransformer
        get() = this

    /**
     * Implements [TransformEngine.rtcpTransformer].
     *
     * This `TransformEngine` does not transform RTCP packets.
     */
    override val rtcpTransformer: PacketTransformer?
        get() = null

    /**
     * Adds an abs-send-time RTP header extension with an ID of [ ][.extensionID] and value derived from the current system time to the
     * packet `pkt`.
     *
     * @param pkt the packet to add an extension to.
     */
    private fun addExtension(pkt: RawPacket?) {
        val he = pkt!!.addExtension(extensionID.toByte(), 11)
        val buf = he.buffer
        val off = he.offset

        // skip the first ID/len byte, which has been already set.
        buf[off + 1] = pkt.payloadType
        RTPUtils.writeShort(buf, off + 2, pkt.sequenceNumber.toShort())
        RTPUtils.writeInt(buf, off + 4, pkt.timestamp.toInt())
        RTPUtils.writeInt(buf, off + 8, pkt.getSSRC())
    }

    /**
     * Sets the ID of the abs-send-time RTP extension. Set to -1 to effectively
     * disable this transformer.
     *
     * @param id the ID to set.
     */
    fun setExtensionID(id: Int) {
        extensionID = id
    }
}