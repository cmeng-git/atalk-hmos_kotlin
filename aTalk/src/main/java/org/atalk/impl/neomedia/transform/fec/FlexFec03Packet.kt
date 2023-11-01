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

/**
 * @author bbaldino
 * Based on FlexFec draft -03
 * https://tools.ietf.org/html/draft-ietf-payload-flexible-fec-scheme-03
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |R|F| P|X|  CC   |M| PT recovery |         length recovery      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                          TS recovery                          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |   SSRCCount   |                    reserved                   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                             SSRC_i                            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           SN base_i           |k|          Mask [0-14]        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |k|                   Mask [15-45] (optional)                   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |k|                                                             |
 * +-+                   Mask [46-108] (optional)                  |
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                     ... next in SSRC_i ...                    |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
open class FlexFec03Packet
/**
 * Ctor
 *
 * @param buffer
 * rtp packet buffer
 * @param offset
 * offset at which the rtp packet starts in the given
 * buffer
 * @param length
 * length of the packet
 */
private constructor(buffer: ByteArray, offset: Int, length: Int) : RawPacket(buffer, offset, length) {
    /**
     * The FlexFEC03 header
     */
    protected var header: FlexFec03Header? = null

    /**
     * Get the list of media packet sequence numbers protected by this
     * FlexFec03Packet
     *
     * @return the list of media packet sequence numbers protected by this
     * FlexFec03Packet
     */
    val protectedSequenceNumbers: List<Int>
        get() = header!!.protectedSeqNums

    /**
     * Get the size of the flexfec header for this packet
     *
     * @return the size of the flexfec header for this packet
     */
    val flexFecHeaderSize: Int
        get() = header!!.size

    /**
     * Get the media ssrc protected by this flexfec packet
     *
     * @return the media ssrc protected by this flexfec packet
     */
    val protectedSsrc: Long
        get() = header!!.protectedSsrc

    /**
     * Returns the size of the FlexFEC payload, in bytes
     *
     * @return the size of the FlexFEC packet payload, in bytes
     */
    val flexFecPayloadLength: Int
        get() = length - this.headerLength - header!!.size

    /**
     * Get the offset at which the FlexFEC header starts
     *
     * @return the offset at which the FlexFEC header starts
     */
    val flexFecHeaderOffset: Int
        get() = offset + this.headerLength

    companion object {
        /**
         * Create a [FlexFec03Packet]
         *
         * @param p
         * the RawPacket to attempt parsing as a FlexFEC packet
         * @return a [FlexFec03Packet] if 'p' is successfully parsed
         * as a [FlexFec03Packet], null otherwise
         */
        fun create(p: RawPacket): FlexFec03Packet? {
            return create(p.buffer, p.offset, p.length)
        }

        /**
         * Create a [FlexFec03Packet]
         *
         * @param buffer
         * @param offset
         * @param length
         * @return a [FlexFec03Packet] if 'p' is successfully parsed
         * as a [FlexFec03Packet], null otherwise
         */
        fun create(buffer: ByteArray, offset: Int, length: Int): FlexFec03Packet? {
            val flexFecPacket = FlexFec03Packet(buffer, offset, length)
            val header = FlexFec03HeaderReader.readFlexFecHeader(
                    flexFecPacket.buffer,
                    flexFecPacket.flexFecHeaderOffset,
                    flexFecPacket.length - flexFecPacket.headerLength) ?: return null
            flexFecPacket.header = header
            return flexFecPacket
        }
    }
}