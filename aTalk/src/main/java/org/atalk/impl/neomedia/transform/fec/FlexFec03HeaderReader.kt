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

import org.atalk.impl.neomedia.transform.fec.FlexFec03Mask.MalformedMaskException
import org.atalk.util.RTPUtils

/**
 * Parse a FlexFec header
 *
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
object FlexFec03HeaderReader {
    /**
     * The minimum size of the FlexFec header, in bytes.
     * We include here one instance of SSRC_i and SN base_i, as well as
     * the smallest possible packet mask.
     */
    private const val HEADER_MIN_SIZE_BYTES = 20

    /**
     * The offset of the mask in the flexfec header, relative to the start
     * of the flexfec header
     */
    private const val MASK_START_OFFSET_BYTES = 18

    /**
     * Parse a buffer pointing to FlexFec data.
     *
     * @param buffer
     * buffer which contains the flexfec data
     * @param flexFecOffset
     * the flexFecOffset in buffer at which the flexfec header starts
     * @param length
     * length of the buffer
     * @return true if parsing succeeded, false otherwise
     */
    fun readFlexFecHeader(buffer: ByteArray, flexFecOffset: Int, length: Int): FlexFec03Header? {
        if (length < HEADER_MIN_SIZE_BYTES) {
            return null
        }
        val retransmissionBit = buffer[flexFecOffset].toInt() and 0x80 shr 7 == 1
        if (retransmissionBit) {
            // We don't support flexfec retransmissions
            return null
        }
        val maskType = buffer[flexFecOffset].toInt() and 0x40 shr 6
        if (maskType != 0) {
            // We only support flexible (f == 0) mask type
            return null
        }
        val ssrcCount = buffer[flexFecOffset + 8].toInt() and 0xFF
        if (ssrcCount > 1) {
            // We only support a single protected ssrc
            return null
        }
        val protectedSsrc = RTPUtils.readUint32AsLong(buffer, flexFecOffset + 12)
        val seqNumBase = RTPUtils.readUint16AsInt(buffer, flexFecOffset + 16)
        val mask: FlexFec03Mask
        mask = try {
            FlexFec03Mask(buffer, flexFecOffset + MASK_START_OFFSET_BYTES, seqNumBase)
        } catch (e: MalformedMaskException) {
            return null
        }

        // HEADER_MIN_SIZES_BYTES already includes the the size of the smallest
        // possible packet mask, but maskSizeBytes will include that as well,
        // so subtract the size of the smallest possible packet mask (2)
        // here when we want to calculate the total.
        val flexFecHeaderSize = HEADER_MIN_SIZE_BYTES - 2 + mask.lengthBytes()
        return FlexFec03Header(protectedSsrc, seqNumBase, mask.protectedSeqNums, flexFecHeaderSize)
    }
}