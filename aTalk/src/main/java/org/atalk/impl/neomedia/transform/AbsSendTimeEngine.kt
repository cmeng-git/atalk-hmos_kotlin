/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atalk.impl.neomedia.transform

import org.atalk.impl.neomedia.RTPPacketPredicate
import org.atalk.service.neomedia.RawPacket
import org.atalk.util.RTPUtils

/**
 * Implements a `TransformEngine` which replaces the timestamps in
 * abs-send-time RTP extensions with timestamps generated locally.
 *
 * See https://www.webrtc.org/experiments/rtp-hdrext/abs-send-time
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class AbsSendTimeEngine
/**
 * Initializes a new [AbsSendTimeEngine] instance.
 */
    : SinglePacketTransformerAdapter(RTPPacketPredicate.INSTANCE), TransformEngine {
    /**
     * The ID of the abs-send-time RTP header extension.
     */
    private var extensionID = -1

    /**
     * Implements [SinglePacketTransformer.reverseTransform].
     */
    override fun transform(pkt: RawPacket): RawPacket {
        if (extensionID != -1) {
            var ext = pkt.getHeaderExtension(extensionID.toByte())
            if (ext == null) {
                ext = pkt.addExtension(extensionID.toByte(), EXT_LENGTH)
            }
            setTimestamp(ext.buffer, ext.offset + 1)
        }
        return pkt
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
     *
     */
    override val rtcpTransformer: PacketTransformer?
        get() = null

    /**
     * Sets the 3 bytes at offset `off` in `buf` to the value of
     * [System.nanoTime] converted to the fixed point (6.18) format specified in
     * []//www.webrtc.org/experiments/rtp-hdrext/abs-send-time"">&quot;https://www.webrtc.org/experiments/rtp-hdrext/abs-send-time&quot;.
     *
     * @param buf the buffer where to write the timestamp.
     * @param off the offset at which to write the timestamp.
     */
    private fun setTimestamp(buf: ByteArray, off: Int) {
        val ns = System.nanoTime()
        val fraction = (ns % b * (1 shl 18) / b).toInt()
        val seconds = (ns / b % 64).toInt() //6 bits only
        val timestamp = seconds shl 18 or fraction and 0x00FFFFFF
        buf[off] = (timestamp shr 16).toByte()
        buf[off + 1] = (timestamp shr 8).toByte()
        buf[off + 2] = timestamp.toByte()
    }

    /**
     * Sets the ID of the abs-send-time RTP extension. Set to -1 to effectively disable this
     * transformer.
     *
     * @param id
     * the ID to set.
     */
    fun setExtensionID(id: Int) {
        extensionID = id
    }

    companion object {
        /**
         * One billion.
         */
        private const val b = 1000000000

        /**
         * The length of the data in the abs-send-time extension (see the draft).
         */
        private const val EXT_LENGTH = 3

        /**
         * 1 2 3 4 5 6 7 8 9 10 11 12 .... 28 29 30 31 32
         * +-+-+-+-+-+-+-+-+-+-+--+--+-....+--+--+--+--+--+
         * |  ID   |  LEN  |   AbsSendTime Value          |
         * +-+-+-+-+-+-+-+-+-+-+--+--+-....+--+--+--+--+--+
         * getAbsSendTime returns the AbsSendTime as a 24bit value
         *
         * @param pkt is a RawPacket
         * @return
         */
        @JvmStatic
        fun getAbsSendTime(pkt: RawPacket, extensionID: Byte): Long {
            var absSendTime = -1L
            val header = pkt.getHeaderExtension(extensionID)
            if (header != null) {
                //offSet is the byte index to read from
                val offSet = header.offset + 1
                if (header.extLength == EXT_LENGTH) {
                    absSendTime = RTPUtils.readUint24AsInt(header.buffer, offSet).toLong()
                }
            }
            return absSendTime
        }
    }
}