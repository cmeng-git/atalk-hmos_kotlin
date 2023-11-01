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

import org.atalk.util.RTPUtils

/**
 * Models a FlexFec-03 mask field
 */
class FlexFec03Mask {
    class MalformedMaskException : Exception()

    private var sizeBytes: Int

    /**
     * The mask field (including k bits)
     */
    var maskWithKBits: FlexFec03BitSet
        private set
    private var baseSeqNum: Int

    /**
     * Initialize this maskWithoutKBits with a received buffer.
     * buffer + maskOffset should point to the location of the start
     * of the mask
     *
     * @param buffer
     * the flexfec packet buffer
     * @param maskOffset
     * maskOffset to the location of the start of the mask
     * @param baseSeqNum
     * the base sequence number from the flexfec packet
     */
    internal constructor(buffer: ByteArray, maskOffset: Int, baseSeqNum: Int) {
        sizeBytes = getMaskSizeInBytes(buffer, maskOffset)
        maskWithKBits = FlexFec03BitSet.Companion.valueOf(buffer, maskOffset, sizeBytes)
        this.baseSeqNum = baseSeqNum
    }

    /**
     * Create a mask from a base sequence number and a list of protected
     * sequence numbers
     *
     * @param baseSeqNum
     * the base sequence number to use for the mask
     * @param protectedSeqNums
     * the sequence numbers this mask should mark
     * as protected
     */
    constructor(baseSeqNum: Int, protectedSeqNums: List<Int>) {
        sizeBytes = getMaskSizeInBytes(baseSeqNum, protectedSeqNums)
        this.baseSeqNum = baseSeqNum
        maskWithKBits = createMaskWithKBits(sizeBytes, this.baseSeqNum, protectedSeqNums)
    }

    /**
     * Get the length of the mask, in bytes
     *
     * @return the length of the mask, in bytes
     */
    fun lengthBytes(): Int {
        return sizeBytes
    }

    /**
     * Get the list of media packet sequence numbers which are marked
     * as protected in this mask
     *
     * @return a list of sequence numbers of the media packets marked as
     * protected by this mask
     */
    val protectedSeqNums: List<Int>
        get() {
            val maskWithoutKBits = getMaskWithoutKBits(maskWithKBits)
            val protectedSeqNums: MutableList<Int> = ArrayList()
            for (i in 0 until maskWithoutKBits.sizeBits()) {
                if (maskWithoutKBits[i]) {
                    protectedSeqNums.add(RTPUtils.applySequenceNumberDelta(baseSeqNum, i))
                }
            }
            return protectedSeqNums
        }

    companion object {
        private const val MASK_0_K_BIT = 0
        private const val MASK_1_K_BIT = 16
        private const val MASK_2_K_BIT = 48
        private const val MASK_SIZE_SMALL = 2
        private const val MASK_SIZE_MED = 6
        private const val MASK_SIZE_LARGE = 14
        private fun getNumBitsExcludingKBits(maskSizeBytes: Int): Int {
            val numBits = maskSizeBytes * 8
            if (maskSizeBytes > MASK_SIZE_MED) {
                return numBits - 3
            }
            return if (maskSizeBytes > MASK_SIZE_SMALL) {
                numBits - 2
            } else numBits - 1
        }

        private fun createMaskWithKBits(sizeBytes: Int, baseSeqNum: Int, protectedSeqNums: List<Int>): FlexFec03BitSet {
            // The sizeBytes we are given will be the entire size of the mask (including
            // k bits).  We're going to insert the k bits later, so subtract
            // those bits from the size of the mask we'll create now
            val numBits = getNumBitsExcludingKBits(sizeBytes)
            val mask = FlexFec03BitSet(numBits)
            // First create a mask without the k bits
            for (protectedSeqNum in protectedSeqNums) {
                val delta = RTPUtils.getSequenceNumberDelta(protectedSeqNum, baseSeqNum)
                mask.set(delta)
            }

            // Now insert the appropriate k bits
            if (sizeBytes > MASK_SIZE_MED) {
                mask.addBit(MASK_0_K_BIT, false)
                mask.addBit(MASK_1_K_BIT, false)
                mask.addBit(MASK_2_K_BIT, true)
            } else if (sizeBytes > MASK_SIZE_SMALL) {
                mask.addBit(MASK_0_K_BIT, false)
                mask.addBit(MASK_1_K_BIT, true)
            } else {
                mask.addBit(MASK_0_K_BIT, true)
            }
            return mask
        }

        /**
         * The size of the packet mask in a flexfec packet is dynamic.  This
         * method determines the size (in bytes) of the mask in the given packet
         * buffer and returns it
         *
         * @param buffer
         * the buffer containing the mask
         * @param maskOffset
         * the offset in the buffer to the start of the mask
         * @return the size of the mask, in bytes.
         */
        @Throws(MalformedMaskException::class)
        private fun getMaskSizeInBytes(buffer: ByteArray, maskOffset: Int): Int {
            if (buffer.size - maskOffset < MASK_SIZE_SMALL) {
                throw MalformedMaskException()
            }
            // The mask is always at least MASK_SIZE_SMALL bytes
            var maskSizeBytes = MASK_SIZE_SMALL
            val kbit0 = buffer[maskOffset].toInt() and 0x80 shr 7
            if (kbit0 == 0) {
                maskSizeBytes = MASK_SIZE_MED
                if (buffer.size - maskOffset < MASK_SIZE_MED) {
                    throw MalformedMaskException()
                }
                val kbit1 = buffer[maskOffset + 2].toInt() and 0x80 shr 7
                if (kbit1 == 0) {
                    if (buffer.size - maskOffset < MASK_SIZE_LARGE) {
                        throw MalformedMaskException()
                    }
                    maskSizeBytes = MASK_SIZE_LARGE
                }
            }
            return maskSizeBytes
        }

        /**
         * Determine how big the mask needs to be based on the given base sequence
         * number and the list of protected sequence numbers
         *
         * @param baseSeqNum
         * the base sequence number to use for the mask
         * @param sortedProtectedSeqNums
         * the sequence numbers this mask should mark
         * as protected. NOTE: this list MUST be in sorted order
         * @return the size, in bytes, of the mask that is needed to convey
         * the given protected sequence numbers
         */
        @Throws(MalformedMaskException::class)
        private fun getMaskSizeInBytes(baseSeqNum: Int, sortedProtectedSeqNums: List<Int>): Int {
            var largestDelta = -1
            for (protectedSeqNum in sortedProtectedSeqNums) {
                val delta = RTPUtils.getSequenceNumberDelta(protectedSeqNum, baseSeqNum)
                if (delta > largestDelta) {
                    largestDelta = delta
                }
            }
            if (largestDelta > 108) {
                throw MalformedMaskException()
            }
            if (largestDelta <= 14) {
                return MASK_SIZE_SMALL
            } else if (largestDelta <= 45) {
                return MASK_SIZE_MED
            }
            return MASK_SIZE_LARGE
        }

        /**
         * Extract the mask containing just the protected sequence number
         * bits (not the k bits) from the given buffer
         *
         * @param maskWithKBits
         * the mask with the k bits included
         * @return a [FlexFec03BitSet] which contains the bits of
         * the packet mask WITHOUT the k bits (the packet location bits are
         * 'collapsed' so that their bit index correctly represents the delta
         * from baseSeqNum)
         */
        private fun getMaskWithoutKBits(maskWithKBits: FlexFec03BitSet): FlexFec03BitSet {
            // Copy the given mask
            val maskWithoutKBits = FlexFec03BitSet.valueOf(maskWithKBits.toByteArray())
            val maskSizeBytes = maskWithKBits.sizeBytes()
            // We now have a FlexFec03BitSet of the entire mask, including the k
            // bits.  Now shift away the k bits

            // Note that it's important we remove the k bits in this order
            // (otherwise the bit we remove in the k bit position won't be the right
            // one).
            if (maskSizeBytes > MASK_SIZE_MED) {
                maskWithoutKBits.removeBit(MASK_2_K_BIT)
            }
            if (maskSizeBytes > MASK_SIZE_SMALL) {
                maskWithoutKBits.removeBit(MASK_1_K_BIT)
            }
            maskWithoutKBits.removeBit(MASK_0_K_BIT)
            return maskWithoutKBits
        }
    }
}